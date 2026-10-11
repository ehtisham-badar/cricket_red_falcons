(() => {
  'use strict';

  const C = window.RFCalc;
  const API = (window.RF_API_BASE || '').replace(/\/$/, '');
  const ROLES = ['', 'Batsman', 'Batsman + Captain', 'Bowler', 'All-rounder', 'Wicket-keeper'];
  const CURRENCIES = ['Rs ', 'PKR ', '₹', '$', '£'];
  const PAYMENT_METHODS = ['Cash', 'JazzCash', 'Easypaisa', 'Bank transfer', 'Other'];
  const SESSION_KEY = 'rf.session';
  const TAB_KEY = 'rf.tab';
  const ME_KEY = 'rf.me';                     // viewer's chosen player id, or NOT_ME to show the picker
  const NOT_ME = '__none';
  const POLL_MS = 15000;

  const TABS = [
    { id: 'summary', label: 'Summary', icon: '<path d="M3 3v18h18"/><path d="M7 15l4-4 3 3 5-6"/>' },
    { id: 'days', label: 'Days', icon: '<rect x="3" y="4" width="18" height="18" rx="2"/><path d="M16 2v4M8 2v4M3 10h18"/>' },
    { id: 'players', label: 'Players', icon: '<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75"/>' },
    { id: 'settings', label: 'Settings', icon: '<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/>' }
  ];

  let session = readStore(SESSION_KEY);      // { name, admin, token }
  let tab = readStore(TAB_KEY) || 'summary';
  let state = null;                           // { players, trainings, expenses, tournament, settings }
  let sheet = null;                           // { type: 'day', dayId } | { type: 'player', id }
  let saveTimer = null;
  let pollTimer = null;
  let draftNames = '';                        // unsent text in the Add players box

  const $ = sel => document.querySelector(sel);

  // ---------- storage ----------
  function readStore(k) {
    try { return JSON.parse(localStorage.getItem(k)); } catch { return null; }
  }
  function writeStore(k, v) {
    try { v == null ? localStorage.removeItem(k) : localStorage.setItem(k, JSON.stringify(v)); } catch { /* ignore */ }
  }

  // ---------- helpers ----------
  function esc(s) {
    return String(s == null ? '' : s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  }
  const isAdmin = () => !!(session && session.admin && session.token);
  const cur = () => state.settings.currency;
  const fmt = n => C.money(n, cur());
  const fmtNet = n => C.signedMoney(n, cur());
  const netClass = n => n > 0 ? 'pos' : n < 0 ? 'neg' : 'zero';
  const rid = () => Math.random().toString(36).slice(2, 9);
  const cleanNumber = v => v.replace(/[^\d.]/g, '').replace(/(\..*)\./g, '$1');
  const icon = (paths, size = 20) =>
    `<svg viewBox="0 0 24 24" width="${size}" height="${size}" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${paths}</svg>`;
  const X_ICON = icon('<path d="M18 6 6 18M6 6l12 12"/>', 18);
  const IMAGE_ICON = icon('<rect x="3" y="3" width="18" height="18" rx="2"/><circle cx="8.5" cy="8.5" r="1.5"/><path d="m21 15-5-5L5 21"/>', 18);
  const COPY_ICON = icon('<rect x="9" y="9" width="13" height="13" rx="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>', 18);
  const WA_ICON = icon('<path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z"/>', 18);

  function emptyState() {
    return {
      players: [], trainings: [{ id: 't1', desc: '' }], expenses: {}, tournament: { name: '', fee: '', payments: [] },
      settings: { currency: 'Rs ', maxPerMatch: 11, roundUp: false }
    };
  }

  // Version 2 data has one amount per day; turn each into a single fund-paid expense.
  function expensesFrom(d) {
    if (d.expenses) {
      const out = {};
      Object.keys(d.expenses).forEach(day => {
        out[day] = (d.expenses[day] || []).map(e => ({
          id: e.id || rid(), item: e.item || '', amount: e.amount == null ? '' : String(e.amount), paidBy: e.paidBy || ''
        }));
      });
      return out;
    }
    const out = {};
    Object.keys(d.amounts || {}).forEach(day => {
      const amt = d.amounts[day];
      if (amt !== '' && amt != null) out[day] = [{ id: 'legacy-' + day, item: 'Amount spent', amount: String(amt), paidBy: '' }];
    });
    return out;
  }

  // Older data has a single "paid" total; it becomes one undated payment.
  function paymentsFrom(playerId, p) {
    if (Array.isArray(p.payments)) {
      return p.payments.map(x => ({
        id: x.id || rid(), date: x.date || '', amount: x.amount == null ? '' : String(x.amount),
        method: x.method || '', note: x.note || ''
      }));
    }
    const paid = p.paid == null ? '' : String(p.paid);
    return paid ? [{ id: 'legacy-pay-' + playerId, date: '', amount: paid, method: '', note: 'Paid before payment history' }] : [];
  }

  function normalize(d) {
    const s = d.settings || {};
    const players = (d.players || []).map(p => {
      const id = p.id || rid();
      return {
        id, name: p.name || '', role: p.role || '', tournament: p.tournament !== false, att: p.att || {},
        payments: paymentsFrom(id, p)
      };
    });
    const ids = new Set(players.map(p => p.id));
    const expenses = expensesFrom(d);
    Object.values(expenses).forEach(list => list.forEach(e => { if (!ids.has(e.paidBy)) e.paidBy = ''; }));
    return {
      players,
      trainings: d.trainings && d.trainings.length ? d.trainings.map(t => ({ id: t.id, desc: t.desc || '' })) : [{ id: 't1', desc: '' }],
      expenses,
      tournament: {
        name: (d.tournament && d.tournament.name) || '',
        fee: d.tournament && d.tournament.fee != null ? String(d.tournament.fee) : '',
        payments: paymentsFrom(null, { payments: (d.tournament && d.tournament.payments) || [] })
      },
      settings: {
        currency: CURRENCIES.includes(s.currency) ? s.currency : 'Rs ',
        maxPerMatch: s.maxPerMatch > 0 ? s.maxPerMatch : 11,
        roundUp: !!s.roundUp
      }
    };
  }

  function comparable(d) {
    const { players, trainings, expenses, tournament, settings } = d;
    return JSON.stringify({ players, trainings, expenses, tournament, settings });
  }

  // ---------- API ----------
  async function api(method, path, body, opts = {}) {
    const headers = { 'Content-Type': 'application/json' };
    if (session && session.token) headers['X-Admin-Token'] = session.token;
    const res = await fetch(API + path, { method, headers, body: body ? JSON.stringify(body) : undefined, ...opts });
    const data = await res.json().catch(() => ({}));
    if (!res.ok) {
      const e = new Error(data.error || `Request failed (${res.status})`);
      e.status = res.status;
      throw e;
    }
    return data;
  }

  async function fetchLedger() {
    return normalize(await api('GET', '/api/ledger'));
  }

  // ---------- saving (admin) ----------
  function commit() {
    render();
    if (!isAdmin()) return;
    setSaveStatus('pending');
    clearTimeout(saveTimer);
    saveTimer = setTimeout(flushSave, 450);
  }

  async function flushSave() {
    saveTimer = null;
    setSaveStatus('saving');
    try {
      await api('PUT', '/api/ledger', state);
      if (!saveTimer) setSaveStatus('saved');
    } catch (e) {
      if (e.status === 403) {
        session = { name: session.name, admin: false, token: null };
        writeStore(SESSION_KEY, null);
        setSaveStatus('');
        toast('Admin access is no longer valid. You are now view-only.');
        render();
        return;
      }
      setSaveStatus('error');
      saveTimer = setTimeout(flushSave, 4000);
    }
  }

  function setSaveStatus(s) {
    const el = $('#save-status');
    if (!el) return;
    const text = { pending: 'Unsaved…', saving: 'Saving…', saved: 'All changes saved', error: 'Offline — retrying' }[s] || '';
    el.textContent = text;
    el.dataset.state = s;
  }

  window.addEventListener('beforeunload', () => {
    if (saveTimer && isAdmin()) {
      clearTimeout(saveTimer);
      api('PUT', '/api/ledger', state, { keepalive: true }).catch(() => {});
    }
  });

  // ---------- polling (viewers) ----------
  function startPolling() {
    clearInterval(pollTimer);
    pollTimer = setInterval(async () => {
      if (isAdmin() || document.visibilityState !== 'visible') return;
      try {
        const d = await fetchLedger();
        if (comparable(d) !== comparable(state)) { state = d; render(); }
      } catch { /* try again next tick */ }
    }, POLL_MS);
  }

  // ---------- splash / login ----------
  function showSplash() {
    clearInterval(pollTimer);
    $('#app').hidden = true;
    $('#splash').hidden = false;
    $('#sheet-root').innerHTML = '';
    const cont = $('#continue-btn');
    if (session) {
      cont.hidden = false;
      cont.textContent = `Continue as ${session.admin ? 'Admin' : session.name}`;
    } else {
      cont.hidden = true;
    }
    $('#login-error').textContent = '';
    $('#login-name').value = '';
    setTimeout(() => $('#login-name').focus(), 50);
  }

  async function login(name) {
    const err = $('#login-error');
    err.textContent = '';
    try {
      const r = await api('POST', '/api/login', { name });
      session = { name: r.name, admin: r.admin, token: r.token };
      writeStore(SESSION_KEY, session);
      writeStore(ME_KEY, null); // a new name may be a different person on this device
      await enterApp();
    } catch (e) {
      err.textContent = 'Could not reach the server. Is it running?';
    }
  }

  async function enterApp() {
    try {
      if (session.token) {
        const s = await api('GET', '/api/session');
        if (!s.admin) { session = { ...session, admin: false, token: null }; writeStore(SESSION_KEY, session); }
      }
      state = await fetchLedger();
    } catch (e) {
      showSplash();
      $('#login-error').textContent = 'Could not load the ledger. Is the server running?';
      return;
    }
    $('#splash').hidden = true;
    $('#app').hidden = false;
    setSaveStatus('');
    render();
    startPolling();
  }

  // ---------- rendering ----------
  function render() {
    if (!state) return;
    const active = document.activeElement;
    const focusKey = active && active.dataset ? active.dataset.focus : null;
    let sel = null;
    if (focusKey && typeof active.selectionStart === 'number') sel = [active.selectionStart, active.selectionEnd];

    document.body.classList.toggle('is-admin', isAdmin());
    $('#tabs').innerHTML = TABS.map(t =>
      `<button class="tab ${t.id === tab ? 'active' : ''}" data-action="tab" data-tab="${t.id}" ${t.id === tab ? 'aria-current="page"' : ''}>
        ${icon(t.icon)}<span>${t.label}</span></button>`).join('');
    $('#who-badge').innerHTML = isAdmin()
      ? `<span class="badge badge-admin">${icon('<path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>', 14)} Admin</span>`
      : `<span class="badge badge-view">${icon('<path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/>', 14)} ${esc(session.name)} · view only</span>`;

    const views = { summary: viewSummary, days: viewDays, players: viewPlayers, settings: viewSettings };
    $('#view').innerHTML = (views[tab] || viewSummary)();
    $('#sheet-root').innerHTML = sheet ? renderSheet() : '';
    document.body.classList.toggle('sheet-open', !!sheet);

    if (focusKey) {
      const el = document.querySelector(`[data-focus="${CSS.escape(focusKey)}"]`);
      if (el) {
        el.focus({ preventScroll: true });
        if (sel && el.setSelectionRange) { try { el.setSelectionRange(sel[0], sel[1]); } catch { /* ignore */ } }
      }
    }
  }

  function pageHead(title, sub, actions = '') {
    return `<div class="page-head"><div><h2>${title}</h2>${sub ? `<p class="muted">${sub}</p>` : ''}</div>${actions}</div>`;
  }

  function warningBanner() {
    const w = C.warnings(state);
    if (!w.length) return '';
    return `<div class="banner">${icon('<path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><path d="M12 9v4M12 17h.01"/>')}
      <span><strong>${esc(w.join(', '))}</strong>: amount entered but no players marked — that cost isn't assigned to anyone yet.</span></div>`;
  }

  // Admins see the ledger from the manager's side; viewers from the player's side.
  function netText(n) {
    if (n > 0) return 'Pay back';
    if (n < 0) return isAdmin() ? 'Owes you' : 'Balance due';
    return 'Settled';
  }

  // ---------- WhatsApp messages (WhatsApp shows *text* as bold) ----------
  function playerMessage(p) {
    const c = C.playerCalc(state, p, null, fmt);
    const out = [`*Red Falcons CC* — ${p.name || 'Player'}`, ''];
    out.push('*Cost*');
    if (c.lines.length) c.lines.forEach(l => out.push('• ' + l.text)); else out.push('• No cost yet');
    out.push(`Total: ${fmt(c.cost)}`, '');
    out.push('*Paid*');
    if (p.payments.length) {
      p.payments.forEach(x => out.push(`• ${formatDate(x.date) || 'Earlier'}${x.method ? ' (' + x.method + ')' : ''}: ${fmt(C.num(x.amount))}`));
    } else {
      out.push('• Nothing yet');
    }
    out.push(`Total: ${fmt(c.paid)}`);
    if (c.creditLines.length) {
      out.push('', '*Bought for the team*');
      c.creditLines.forEach(l => out.push('• ' + l.text));
    }
    out.push('');
    if (c.net < 0) out.push(`*Balance due: ${fmt(-c.net)}*`);
    else if (c.net > 0) out.push(`*To be paid back to you: ${fmt(c.net)}*`);
    else out.push('*All settled ✅*');
    out.push('', `Full ledger: ${location.origin}`);
    return out.join('\n');
  }

  function teamMessage() {
    const stats = C.allStats(state);
    const tour = C.tournamentStats(state);
    const rows = state.players.map(p => ({ p, net: C.playerCalc(state, p, stats, fmt, tour).net }));
    const due = rows.filter(r => r.net < 0).sort((a, b) => a.net - b.net);
    const back = rows.filter(r => r.net > 0);
    const settled = rows.filter(r => r.net === 0);
    const out = ['*Red Falcons CC — balances*'];
    if (tour.fee > 0) out.push(`Tournament fee ${fmt(tour.fee)} (${fmt(tour.share)} each)`);
    if (due.length) {
      out.push('', '*Still to pay*');
      due.forEach((r, i) => out.push(`${i + 1}. ${r.p.name} — ${fmt(-r.net)}`));
    }
    if (back.length) {
      out.push('', '*To be paid back*');
      back.forEach(r => out.push(`• ${r.p.name} — ${fmt(r.net)}`));
    }
    if (settled.length) out.push('', `Settled ✅ ${settled.map(r => r.p.name).join(', ')}`);
    out.push('', `Your full breakdown: ${location.origin}`);
    return out.join('\n');
  }

  // ---------- balances picture ----------
  const MISC_PER_PLAYER = 500;
  const PAY_TO = [
    { label: 'Bank', value: 'NayaPay' },
    { label: 'Account title', value: 'Ehtisham Badar' },
    { label: 'Account #', value: '03084250469' }
  ];

  async function balanceImage() {
    const t = C.totals(state, fmt);
    const tour = C.tournamentStats(state);
    const stats = C.allStats(state);
    const advance = C.advanceShare(state);
    // Total due adds the misc collection on top of the ledger balance; the advance is the part
    // asked now from players who haven't paid anything yet.
    const rows = state.players.map(p => {
      const net = C.round2(C.playerCalc(state, p, stats, fmt, tour).net - MISC_PER_PLAYER);
      const adv = advance > 0 && C.inTournament(p) && C.paidTotal(p) === 0 ? Math.min(advance, -net) : 0;
      return {
        name: p.name,
        net,
        cells: [
          adv > 0 ? { text: fmt(adv), color: '#ff6b6b' } : { text: '—', color: '#8d8a85' },
          { text: fmt(MISC_PER_PLAYER), color: '#f5eee2' }
        ]
      };
    });
    const owed = rows.reduce((s, r) => s + Math.max(-r.net, 0), 0);
    const payBack = rows.reduce((s, r) => s + Math.max(r.net, 0), 0);
    const chips = [];
    if (owed > 0) chips.push({ text: `Still to pay ${fmt(owed)}`, color: '#ff6b6b', bg: 'rgba(208,34,44,0.18)' });
    if (payBack > 0) chips.push({ text: `To pay back ${fmt(payBack)}`, color: '#e2b65c', bg: 'rgba(226,182,92,0.14)' });
    if (tour.fee > 0 && tour.n) chips.push({ text: `Tournament ${fmt(tour.share)} each`, color: '#f5eee2', bg: 'rgba(255,255,255,0.07)' });
    const league = t.tournamentFee > 0 || state.tournament.payments.length ? {
      name: leagueName(),
      figures: [
        { label: 'League fee', value: t.tournamentFee > 0 ? fmt(t.tournamentFee) : '—', color: '#f5eee2' },
        { label: 'Paid to management', value: fmt(t.sentToManagement), color: '#e2b65c' },
        { label: 'Still owed to mgmt', value: fmt(t.managementDue), color: t.managementDue > 0 ? '#ff6b6b' : '#f5eee2' },
        { label: 'In my account', value: fmtNet(t.cashInHand).replace(/^\+/, ''), color: t.cashInHand < 0 ? '#ff6b6b' : '#f5eee2' }
      ]
    } : null;
    return window.RFBalanceImage.render({
      league,
      columns: [{ label: 'Advance', width: 170 }, { label: 'Misc', width: 130 }],
      rows,
      payTo: PAY_TO,
      money: fmt,
      chips,
      dateText: 'Updated ' + new Date().toLocaleDateString('en-GB', { day: 'numeric', month: 'long', year: 'numeric' }),
      link: location.host,
      logoSrc: 'img/logo.jpeg'
    });
  }

  function imageFileName() {
    return `red-falcons-balances-${today()}.png`;
  }

  // Preview with Share (phones: straight into WhatsApp), Download and Copy.
  function showImageDialog(blob) {
    const file = new File([blob], imageFileName(), { type: 'image/png' });
    const canShare = !!(navigator.canShare && navigator.canShare({ files: [file] }));
    const canCopy = !!(navigator.clipboard && window.ClipboardItem);
    const url = URL.createObjectURL(blob);
    const root = $('#dialog-root');
    root.innerHTML = `<div class="dialog-backdrop"></div>
      <div class="dialog image-dialog" role="dialog" aria-modal="true" aria-labelledby="img-title">
        <h3 id="img-title">Balances picture</h3>
        <div class="image-preview"><img src="${url}" alt="Team balances"></div>
        <div class="dialog-actions">
          <button class="btn btn-ghost" data-img="close">Close</button>
          ${canCopy ? `<button class="btn btn-ghost" data-img="copy">${COPY_ICON} Copy image</button>` : ''}
          <button class="btn ${canShare ? 'btn-ghost' : 'btn-primary'}" data-img="download">Download</button>
          ${canShare ? `<button class="btn btn-whatsapp" data-img="share">${WA_ICON} Share</button>` : ''}
        </div>
      </div>`;
    const close = () => { root.innerHTML = ''; URL.revokeObjectURL(url); document.removeEventListener('keydown', onKey); };
    const onKey = e => { if (e.key === 'Escape') close(); };
    document.addEventListener('keydown', onKey);
    root.querySelector('.dialog-backdrop').addEventListener('click', close);
    root.querySelector('[data-img="close"]').addEventListener('click', close);
    root.querySelector('[data-img="download"]').addEventListener('click', () => {
      const a = document.createElement('a');
      a.href = url;
      a.download = imageFileName();
      document.body.appendChild(a);
      a.click();
      a.remove();
    });
    const copyBtn = root.querySelector('[data-img="copy"]');
    if (copyBtn) copyBtn.addEventListener('click', async () => {
      try {
        await navigator.clipboard.write([new ClipboardItem({ 'image/png': blob })]);
        toast('Image copied — paste it into WhatsApp.');
      } catch {
        toast('Copy isn’t allowed here — use Download instead.');
      }
    });
    const shareBtn = root.querySelector('[data-img="share"]');
    if (shareBtn) shareBtn.addEventListener('click', () => {
      navigator.share({ files: [file], title: 'Red Falcons balances' }).catch(() => { /* user cancelled */ });
    });
  }

  function openWhatsApp(text) {
    window.open('https://wa.me/?text=' + encodeURIComponent(text), '_blank', 'noopener');
  }

  async function copyText(text) {
    try {
      await navigator.clipboard.writeText(text);
    } catch {
      const ta = document.createElement('textarea');
      ta.value = text;
      document.body.appendChild(ta);
      ta.select();
      document.execCommand('copy');
      ta.remove();
    }
    toast('Copied — paste it into WhatsApp.');
  }

  // ---------- "My balance" for viewers ----------
  const norm = s => (s || '').trim().toLowerCase().replace(/\s+/g, ' ');

  // The player this viewer is: their own pick, else a unique match on full name or first name.
  function myPlayer() {
    if (isAdmin()) return null;
    const chosen = readStore(ME_KEY);
    if (chosen === NOT_ME) return null;
    if (chosen && findPlayer(chosen)) return findPlayer(chosen);
    const n = norm(session.name);
    const full = state.players.filter(p => norm(p.name) === n);
    if (full.length === 1) return full[0];
    const first = state.players.filter(p => norm(p.name).split(' ')[0] === n);
    return first.length === 1 ? first[0] : null;
  }

  function myBalance() {
    if (isAdmin() || !state.players.length) return '';
    const me = myPlayer();
    if (!me) {
      return `<section class="me-card me-pick">
        <div><span class="day-kind">Your balance</span><h3>Which player are you?</h3></div>
        <select class="field select" data-input="pick-me" aria-label="Choose your name">
          <option value="">Choose your name…</option>
          ${state.players.map(p => `<option value="${esc(p.id)}">${esc(p.name)}</option>`).join('')}
        </select>
      </section>`;
    }
    const c = C.playerCalc(state, me, null, fmt);
    return `<section class="me-card ${netClass(c.net)}">
      <div class="me-text">
        <span class="day-kind">Your balance · ${esc(me.name)}</span>
        <strong class="me-net">${c.net < 0 ? fmt(-c.net) : fmt(c.net)}</strong>
        <span class="me-label">${c.net < 0 ? 'Still to pay' : c.net > 0 ? 'To be paid back to you' : 'All settled'}</span>
        <small class="muted">Paid ${fmt(c.paid)}${c.credit > 0 ? ` + bought ${fmt(c.credit)}` : ''} · Cost ${fmt(c.cost)}</small>
      </div>
      <div class="me-actions">
        <button class="btn btn-gold" data-action="open-player" data-id="${esc(me.id)}">See breakdown</button>
        <button class="btn btn-ghost small" data-action="not-me">Not you?</button>
      </div>
    </section>`;
  }

  function leagueName() {
    return state.tournament.name.trim() || 'Tournament';
  }

  function leagueBlock() {
    const t = C.totals(state, fmt);
    const admin = isAdmin();
    if (!(t.tournamentFee > 0) && !state.tournament.payments.length && !admin) return '';
    const pct = t.tournamentFee > 0 ? Math.min(100, Math.round(t.sentToManagement / t.tournamentFee * 100)) : 0;
    return `<section class="panel league">
      <div class="league-head">
        <div><span class="day-kind">League</span><h3>${esc(leagueName())}</h3></div>
        ${t.tournamentFee > 0 ? `<span class="league-pct">${pct}% paid to management</span>` : ''}
      </div>
      ${t.tournamentFee > 0 ? `<div class="league-bar"><span style="width:${pct}%"></span></div>` : ''}
      <div class="league-figs">
        <div><span>League fee</span><strong>${t.tournamentFee > 0 ? fmt(t.tournamentFee) : '—'}</strong>
          ${t.tournamentFee > 0 ? '' : `<small>${admin ? 'Set it on the Days tab' : 'Not set'}</small>`}</div>
        <div><span>${admin ? 'I have paid management' : 'Paid to management'}</span><strong class="pos">${fmt(t.sentToManagement)}</strong></div>
        <div><span>Still owed to management</span><strong class="${t.managementDue > 0 ? 'neg' : ''}">${fmt(t.managementDue)}</strong></div>
        <div><span>${admin ? 'In my account' : 'With the manager'}</span><strong class="${t.cashInHand < 0 ? 'neg' : ''}">${fmtNet(t.cashInHand).replace(/^\+/, '')}</strong>
          <small>Collected ${fmt(t.collected)} − sent ${fmt(t.sentToManagement)}${t.daySpent - t.fronted > 0 ? ` − expenses ${fmt(t.daySpent - t.fronted)}` : ''}</small></div>
      </div>
      <h4 class="league-sub">Payments to management</h4>
      ${paymentEditor(MGMT, state.tournament.payments, '+ Add payment to management')}
    </section>`;
  }

  function viewSummary() {
    const t = C.totals(state, fmt);
    const stats = C.allStats(state);
    const tour = C.tournamentStats(state);
    const days = C.days(state);
    const admin = isAdmin();
    const cards = [
      { label: 'Collected', value: fmt(t.collected), cls: '', note: 'Paid in by players' },
      { label: 'League fee', value: t.tournamentFee > 0 ? fmt(t.tournamentFee) : '—', cls: '', note: tour.n ? `${fmt(tour.share)} each · ${tour.n} players` : 'Not set' },
      { label: 'Day expenses', value: fmt(t.daySpent), cls: '', note: t.fronted > 0 ? `${fmt(t.fronted)} bought by players` : 'Trainings & matches' },
      { label: admin ? 'In my account' : 'With the manager', value: fmtNet(t.cashInHand).replace(/^\+/, ''), cls: t.cashInHand < 0 ? 'neg' : '', note: 'Collected − sent to management − expenses' },
      { label: admin ? 'You pay back' : 'To be paid back', value: fmt(t.payBack), cls: 'pos', note: 'Players in credit' },
      { label: admin ? 'Still owed to you' : 'Balance due', value: fmt(t.owed), cls: 'neg', note: 'Players still to pay' }
    ];

    const rows = state.players.map((p, i) => {
      const c = C.playerCalc(state, p, stats, fmt, tour);
      return `<button class="prow" data-action="open-player" data-id="${esc(p.id)}">
        <span class="sr">${i + 1}</span>
        <span class="pname"><strong>${esc(p.name) || '<em>Unnamed</em>'}</strong><small>${esc(p.role) || '—'}${C.inTournament(p) ? '' : ' · not in tournament'}</small></span>
        <span class="pnet ${netClass(c.net)}"><strong>${fmtNet(c.net)}</strong><small>${netText(c.net)}</small></span>
      </button>`;
    }).join('');

    const table = state.players.length ? `
      <section class="panel">
        <div class="panel-head"><h3>Season sheet</h3><p class="muted">Who attended what, and where everyone stands.</p></div>
        <div class="table-wrap">
          <table class="sheet-table">
            <thead>
              <tr><th class="sticky">#</th><th class="sticky sticky-2">Player</th>
                ${days.map(d => `<th class="day-col ${d.type}" title="${esc(d.label)}${d.desc ? ' — ' + esc(d.desc) : ''}">${d.short}<small>${stats[d.id].n > 0 && stats[d.id].amount > 0 ? fmt(stats[d.id].share) : '—'}</small></th>`).join('')}
                <th class="day-col tour" title="Tournament fee share">Tour.<small>${tour.fee > 0 && tour.n ? fmt(tour.share) : '—'}</small></th>
                <th>Cost</th><th>Paid</th><th title="Items bought for the team">Bought</th><th>Balance</th></tr>
            </thead>
            <tbody>
              ${state.players.map((p, i) => {
                const c = C.playerCalc(state, p, stats, fmt, tour);
                return `<tr data-action="open-player" data-id="${esc(p.id)}">
                  <td class="sticky">${i + 1}</td><td class="sticky sticky-2 name-cell">${esc(p.name)}</td>
                  ${days.map(d => `<td class="dot-cell">${p.att[d.id] ? `<span class="dot ${d.type}" title="${d.type === 'match' ? 'Played' : 'Arrived'}"></span>` : '<span class="dot-empty"></span>'}</td>`).join('')}
                  <td class="dot-cell">${C.inTournament(p) ? '<span class="dot tour" title="In tournament"></span>' : '<span class="dot-empty"></span>'}</td>
                  <td>${fmt(c.cost)}</td><td>${fmt(c.paid)}</td><td>${c.credit > 0 ? fmt(c.credit) : '—'}</td>
                  <td class="${netClass(c.net)} strong">${fmtNet(c.net)}</td></tr>`;
              }).join('')}
            </tbody>
            <tfoot><tr><td class="sticky"></td><td class="sticky sticky-2">Attended</td>
              ${days.map(d => `<td>${stats[d.id].n}</td>`).join('')}<td>${tour.n}</td>
              <td>${fmt(t.spent)}</td><td>${fmt(t.collected)}</td><td>${t.fronted > 0 ? fmt(t.fronted) : '—'}</td><td></td></tr></tfoot>
          </table>
        </div>
      </section>` : '';

    return `
      ${pageHead('Summary', `${state.players.length} player${state.players.length === 1 ? '' : 's'} · ${state.trainings.length} training${state.trainings.length === 1 ? '' : 's'} · 5 matches`,
        admin && state.players.length ? `<div class="share-row">
          <button class="btn btn-gold" data-action="share-image">${IMAGE_ICON} Share as image</button>
          <button class="btn btn-whatsapp" data-action="share-team">${WA_ICON} Send as text</button>
          <button class="btn btn-ghost" data-action="copy-team">${COPY_ICON} Copy</button></div>` : '')}
      ${myBalance()}
      ${leagueBlock()}
      <div class="stats">${cards.map(c => `<div class="stat ${c.cls}"><span>${c.label}</span><strong>${c.value}</strong><small>${esc(c.note)}</small></div>`).join('')}</div>
      ${warningBanner()}
      <div class="summary-grid">
        <section class="panel">
          <div class="panel-head"><h3>Players</h3><p class="muted">Tap a player for the full breakdown.</p></div>
          ${rows ? `<div class="plist">${rows}</div>` : emptyPlayers()}
        </section>
        ${table}
      </div>`;
  }

  function emptyPlayers() {
    return `<div class="empty">${icon('<circle cx="12" cy="12" r="10"/><path d="M8 14s1.5 2 4 2 4-2 4-2M9 9h.01M15 9h.01"/>', 32)}
      <p>No players yet.</p>${isAdmin() ? '<button class="btn btn-primary" data-action="tab" data-tab="players">Add players</button>' : ''}</div>`;
  }

  function playerName(id) {
    const p = id && state.players.find(x => x.id === id);
    return p ? (p.name || 'Unnamed') : 'Team fund';
  }

  function tournamentPanel() {
    const tour = C.tournamentStats(state);
    const out = state.players.length - tour.n;
    return `<section class="tour-panel">
      <div class="tour-text">
        <span class="day-kind">Whole series · league fee</span>
        ${isAdmin()
          ? `<input class="field league-name" type="text" placeholder="League name (e.g. Hundred Premier League)" value="${esc(state.tournament.name)}" maxlength="80" data-input="tournament-name" data-focus="tour-name" aria-label="League name">`
          : `<h3>${esc(leagueName())}</h3>`}
        <p class="muted">Split equally among players taking part${out ? ` (${out} left out — change this on the Players page)` : '. Leave someone out on the Players page'}.</p>
      </div>
      <div class="tour-figs">
        ${isAdmin()
          ? `<span class="money-input big"><em>${esc(cur().trim())}</em><input type="text" inputmode="decimal" placeholder="e.g. 65000" value="${esc(state.tournament.fee)}" data-input="tournament-fee" data-focus="tour-fee" aria-label="Tournament fee"></span>`
          : `<strong class="tour-fee">${tour.fee > 0 ? fmt(tour.fee) : '—'}</strong>`}
        <span class="tour-share">${tour.fee > 0 && tour.n ? `<strong>${fmt(tour.share)}</strong> each · ${tour.n} player${tour.n === 1 ? '' : 's'}` : `${tour.n} player${tour.n === 1 ? '' : 's'} taking part`}</span>
      </div>
    </section>`;
  }

  function viewDays() {
    const admin = isAdmin();
    const stats = C.allStats(state);
    const max = state.settings.maxPerMatch;
    const cards = C.days(state).map(d => {
      const s = stats[d.id];
      const items = C.dayExpenses(state, d.id).filter(e => e.item || C.num(e.amount) > 0);
      const count = d.type === 'match' ? `${s.n} / ${max} played` : `${s.n} arrived`;
      const share = s.n > 0 && s.amount > 0 ? `${fmt(s.share)} each` : '—';
      const removable = admin && d.type === 'training' && state.trainings.length > 1;
      return `<article class="day-card ${d.type}">
        <header>
          <span class="day-kind">${d.type === 'match' ? 'Match' : 'Training'}</span>
          <h3>${d.label}</h3>
          ${removable ? `<button class="icon-btn danger" data-action="remove-training" data-id="${esc(d.id)}" aria-label="Remove ${d.label}">${X_ICON}</button>` : ''}
        </header>
        ${d.type === 'training' ? (admin
          ? `<input class="field" type="text" placeholder="Description (e.g. Nets at club)" value="${esc(d.desc)}" data-input="training-desc" data-id="${esc(d.id)}" data-focus="desc-${esc(d.id)}" maxlength="200">`
          : `<p class="desc">${esc(d.desc) || '<span class="muted">No description</span>'}</p>`) : ''}
        <div class="amount"><span>Total spent</span><strong>${s.amount > 0 ? fmt(s.amount) : '—'}</strong></div>
        ${items.length ? `<ul class="mini-items">${items.slice(0, 3).map(e => `<li><span>${esc(e.item) || 'Item'}<small>${esc(playerName(e.paidBy))}</small></span><span>${fmt(C.num(e.amount))}</span></li>`).join('')}
          ${items.length > 3 ? `<li class="more">+${items.length - 3} more</li>` : ''}</ul>` : '<p class="muted mini-empty">No expenses yet</p>'}
        <div class="day-meta"><span>${count}</span><span class="share">${share}</span></div>
        <button class="btn btn-block ${d.type === 'match' ? 'btn-gold' : 'btn-primary'}" data-action="open-day" data-id="${esc(d.id)}">
          ${admin ? 'Expenses & attendance' : 'See details'}</button>
      </article>`;
    }).join('');

    return `
      ${pageHead('Days', 'Each day’s expenses are split among the players who attended. Whoever paid for an item gets it credited.',
        admin ? `<button class="btn btn-primary" data-action="add-training">${icon('<path d="M12 5v14M5 12h14"/>', 18)} Add training</button>` : '')}
      ${warningBanner()}
      ${tournamentPanel()}
      <div class="day-grid">${cards}</div>`;
  }

  function tourToggle(p) {
    const on = C.inTournament(p);
    return `<button class="tog tour-tog ${on ? 'on' : ''} ${isAdmin() ? '' : 'ro'}" data-action="toggle-tournament" data-id="${esc(p.id)}"
      ${isAdmin() ? '' : 'disabled'} aria-pressed="${on}" title="Shares the tournament fee">${on ? 'In' : 'Out'}</button>`;
  }

  function viewPlayers() {
    const admin = isAdmin();
    const list = state.players.map((p, i) => `
      <div class="player-line">
        <span class="sr">${i + 1}</span>
        <button class="pl-name" data-action="open-player" data-id="${esc(p.id)}">${esc(p.name) || '<em>Unnamed</em>'}</button>
        ${admin
          ? `<select class="field select" data-input="role" data-id="${esc(p.id)}" aria-label="Role for ${esc(p.name)}">${ROLES.map(r => `<option value="${esc(r)}" ${r === p.role ? 'selected' : ''}>${r || 'No role'}</option>`).join('')}</select>
             ${tourToggle(p)}
             <button class="icon-btn danger" data-action="remove-player" data-id="${esc(p.id)}" aria-label="Remove ${esc(p.name)}">${X_ICON}</button>`
          : `<span class="muted role">${esc(p.role) || '—'}</span>${tourToggle(p)}`}
      </div>`).join('');

    return `
      ${pageHead('Players', `${state.players.length} in the squad`)}
      <div class="players-layout">
        <section class="panel">
          <div class="player-head"><span>Sr.</span><span>Name</span><span>Role</span><span>Tournament</span></div>
          ${list || emptyPlayers()}
        </section>
        ${admin ? `<section class="panel add-panel">
          <div class="panel-head"><h3>Add players</h3><p class="muted">Paste names separated by new lines or commas.</p></div>
          <textarea id="add-names" class="field textarea" rows="7" placeholder="Babar&#10;Rizwan&#10;Shaheen, Naseem" data-focus="add-names">${esc(draftNames)}</textarea>
          <button class="btn btn-primary btn-block" data-action="add-players">Add to squad</button>
        </section>` : ''}
      </div>`;
  }

  function viewSettings() {
    const admin = isAdmin();
    const s = state.settings;
    const dataPanel = `
      <section class="panel">
        <div class="panel-head"><h3>Data</h3><p class="muted">${admin ? 'Backups use the same file format as the original HTML ledger.' : 'Download a copy of the current ledger.'}</p></div>
        <div class="settings-actions">
          <button class="btn btn-ghost" data-action="export">${icon('<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M7 10l5 5 5-5M12 15V3"/>', 18)} Save backup</button>
          ${admin ? `
          <button class="btn btn-ghost" data-action="import">${icon('<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M17 8l-5-5-5 5M12 3v12"/>', 18)} Open backup</button>
          <button class="btn btn-ghost" data-action="new-series">${icon('<path d="M23 4v6h-6M1 20v-6h6"/><path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/>', 18)} New series</button>
          <button class="btn btn-danger" data-action="clear-all">${icon('<path d="M3 6h18M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6M10 11v6M14 11v6M9 6V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2"/>', 18)} Clear all</button>` : ''}
        </div>
      </section>`;

    const prefs = admin ? `
      <section class="panel">
        <div class="panel-head"><h3>Preferences</h3></div>
        <div class="pref">
          <label for="pref-currency">Currency</label>
          <select id="pref-currency" class="field select" data-input="currency">
            ${CURRENCIES.map(c => `<option value="${esc(c)}" ${c === s.currency ? 'selected' : ''}>${esc(c.trim())}</option>`).join('')}
          </select>
        </div>
        <div class="pref">
          <label for="pref-max">Max players per match</label>
          <input id="pref-max" class="field small" type="text" inputmode="numeric" value="${s.maxPerMatch}" data-input="max" data-focus="pref-max">
        </div>
        <div class="pref">
          <span>Round shares up<small class="muted">Whole numbers, rounded up</small></span>
          <button class="switch ${s.roundUp ? 'on' : ''}" role="switch" aria-checked="${s.roundUp}" data-action="toggle-roundup" aria-label="Round shares up"><span></span></button>
        </div>
      </section>` : `
      <section class="panel">
        <div class="panel-head"><h3>View-only access</h3>
        <p class="muted">You can see everything, but only the team admin can make changes. This page refreshes automatically.</p></div>
        <dl class="kv"><dt>Currency</dt><dd>${esc(s.currency.trim())}</dd><dt>Max players per match</dt><dd>${s.maxPerMatch}</dd><dt>Round shares up</dt><dd>${s.roundUp ? 'On' : 'Off'}</dd></dl>
      </section>`;

    return `
      ${pageHead('Settings', '')}
      <div class="settings-grid">${dataPanel}${prefs}
        <section class="panel">
          <div class="panel-head"><h3>Signed in as ${isAdmin() ? 'Admin' : esc(session.name)}</h3></div>
          <button class="btn btn-ghost" data-action="logout">Switch user</button>
        </section>
      </div>`;
  }

  // ---------- sheets ----------
  function renderSheet() {
    let body = '';
    if (sheet.type === 'day') body = sheetDay();
    if (sheet.type === 'player') body = sheetPlayer();
    if (!body) { sheet = null; return ''; }
    return `<div class="sheet-backdrop" data-action="close-sheet"></div>
      <div class="sheet" role="dialog" aria-modal="true">${body}</div>`;
  }

  function toggleButton(p, d, stats) {
    const on = !!p.att[d.id];
    const isMatch = d.type === 'match';
    const max = state.settings.maxPerMatch;
    const full = isMatch && !on && stats[d.id].n >= max;
    const disabled = !isAdmin() || full;
    const label = isMatch ? (on ? 'Played' : 'Not played') : (on ? 'Arrived' : 'Not arrived');
    return `<button class="tog ${d.type} ${on ? 'on' : ''} ${!isAdmin() ? 'ro' : ''}" data-action="toggle-att" data-player="${esc(p.id)}" data-day="${esc(d.id)}"
      ${disabled ? 'disabled' : ''} ${full ? `title="${max} players already played ${d.label}"` : ''} aria-pressed="${on}">${label}</button>`;
  }

  const QUICK_ITEMS = ['Ground fee', 'Balls', 'Water', 'Other'];

  function expenseRows(d) {
    const list = C.dayExpenses(state, d.id);
    if (!isAdmin()) {
      if (!list.length) return '<p class="muted">No expenses recorded.</p>';
      return `<ul class="exp-list">${list.map(e => `<li><span><strong>${esc(e.item) || 'Item'}</strong><small>Paid by ${esc(playerName(e.paidBy))}</small></span>
        <span class="amt">${fmt(C.num(e.amount))}</span></li>`).join('')}</ul>`;
    }
    const payers = state.players.map(p => ({ id: p.id, name: p.name || 'Unnamed' }));
    const rows = list.map(e => `
      <div class="exp-row">
        <input class="field" type="text" placeholder="Item (e.g. Water)" value="${esc(e.item)}" maxlength="80"
          data-input="exp-item" data-day="${esc(d.id)}" data-id="${esc(e.id)}" data-focus="exi-${esc(e.id)}" aria-label="Item">
        <span class="money-input"><em>${esc(cur().trim())}</em><input type="text" inputmode="decimal" placeholder="0" value="${esc(e.amount)}"
          data-input="exp-amount" data-day="${esc(d.id)}" data-id="${esc(e.id)}" data-focus="exa-${esc(e.id)}" aria-label="Amount"></span>
        <select class="field select" data-input="exp-paidby" data-day="${esc(d.id)}" data-id="${esc(e.id)}" aria-label="Paid by">
          <option value="">Team fund</option>
          ${payers.map(p => `<option value="${esc(p.id)}" ${p.id === e.paidBy ? 'selected' : ''}>${esc(p.name)}</option>`).join('')}
        </select>
        <button class="icon-btn danger" data-action="remove-expense" data-day="${esc(d.id)}" data-id="${esc(e.id)}" aria-label="Remove ${esc(e.item || 'item')}">${X_ICON}</button>
      </div>`).join('');
    return `${rows ? `<div class="exp-rows"><div class="exp-head"><span>Item</span><span>Amount</span><span>Paid by</span></div>${rows}</div>` : ''}
      <div class="quick-add"><span class="muted">Add:</span>
        ${QUICK_ITEMS.map(item => `<button class="chip" data-action="add-expense" data-day="${esc(d.id)}" data-item="${item === 'Other' ? '' : esc(item)}">+ ${item}</button>`).join('')}
      </div>`;
  }

  function sheetDay() {
    const d = C.days(state).find(x => x.id === sheet.dayId);
    if (!d) return '';
    const stats = C.allStats(state);
    const s = stats[d.id];
    const isMatch = d.type === 'match';
    const max = state.settings.maxPerMatch;
    const full = isMatch && s.n >= max;
    const rows = state.players.map((p, i) => `
      <div class="att-row ${p.att[d.id] ? 'is-on' : ''}">
        <span class="sr">${i + 1}</span>
        <span class="pname"><strong>${esc(p.name)}</strong><small>${esc(p.role) || '—'}</small></span>
        ${toggleButton(p, d, stats)}
      </div>`).join('');
    return `
      <header class="sheet-head ${d.type}">
        <div>
          <span class="day-kind">${isMatch ? 'Match' : 'Training'}</span>
          <h2>${d.label}</h2>
          ${d.desc ? `<p class="muted">${esc(d.desc)}</p>` : ''}
        </div>
        <button class="icon-btn" data-action="close-sheet" aria-label="Close">${X_ICON}</button>
      </header>
      <div class="counter">
        <div><strong>${isMatch ? `${s.n} / ${max}` : s.n}</strong><span>${isMatch ? 'played' : 'arrived'}</span></div>
        <div><strong>${s.amount > 0 ? fmt(s.amount) : '—'}</strong><span>spent</span></div>
        <div><strong>${s.n > 0 && s.amount > 0 ? fmt(s.share) : '—'}</strong><span>each</span></div>
      </div>
      <div class="sheet-body">
        <h3 class="sub first">Expenses <span class="muted">${fmt(s.amount)}</span></h3>
        ${expenseRows(d)}
        <h3 class="sub">Attendance <span class="muted">${s.n}${isMatch ? ` / ${max}` : ''}</span></h3>
        ${full && isAdmin() ? `<p class="hint">${max} players already played ${d.label}</p>` : ''}
        ${rows || '<p class="muted center">No players yet.</p>'}
      </div>`;
  }

  function formatDate(iso) {
    const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso || '');
    if (!m) return '';
    return new Date(+m[1], m[2] - 1, +m[3]).toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' });
  }

  const today = () => new Date().toLocaleDateString('en-CA'); // YYYY-MM-DD in local time

  // Payments made by a player, or (owner MGMT) sent by the admin to the tournament management.
  const MGMT = '__mgmt';
  function paymentsOf(ownerId) {
    if (ownerId === MGMT) return state.tournament.payments;
    const p = findPlayer(ownerId);
    return p ? p.payments : null;
  }

  function paymentRows(p) {
    return paymentEditor(p.id, p.payments, '+ Add payment');
  }

  function paymentEditor(ownerId, list, addLabel) {
    const p = { id: ownerId, payments: list };
    if (!isAdmin()) {
      if (!p.payments.length) return '<p class="muted">No payments yet.</p>';
      return `<ul class="exp-list">${p.payments.map(x => `<li><span><strong>${formatDate(x.date) || 'Earlier'}</strong>
        <small>${[x.method, x.note].filter(Boolean).map(esc).join(' · ') || '—'}</small></span>
        <span class="amt">${fmt(C.num(x.amount))}</span></li>`).join('')}</ul>`;
    }
    const attrs = x => `data-player="${esc(p.id)}" data-id="${esc(x.id)}"`;
    const rows = p.payments.map(x => `
      <div class="pay-row">
        <input class="field" type="date" value="${esc(x.date)}" max="${today()}" data-input="pay-date" ${attrs(x)} aria-label="Date">
        <span class="money-input"><em>${esc(cur().trim())}</em><input type="text" inputmode="decimal" placeholder="0" value="${esc(x.amount)}"
          data-input="pay-amount" ${attrs(x)} data-focus="pya-${esc(x.id)}" aria-label="Amount"></span>
        <select class="field select" data-input="pay-method" ${attrs(x)} aria-label="Method">
          ${x.method ? '' : '<option value="" selected>Method</option>'}
          ${PAYMENT_METHODS.map(m => `<option ${m === x.method ? 'selected' : ''}>${m}</option>`).join('')}
        </select>
        <input class="field note" type="text" placeholder="Note (optional)" value="${esc(x.note)}" maxlength="200"
          data-input="pay-note" ${attrs(x)} data-focus="pyn-${esc(x.id)}" aria-label="Note">
        <button class="icon-btn danger" data-action="remove-payment" ${attrs(x)} aria-label="Remove payment">${X_ICON}</button>
      </div>`).join('');
    return `${rows ? `<div class="exp-rows">${rows}</div>` : ''}
      <button class="chip" data-action="add-payment" data-id="${esc(p.id)}">${addLabel}</button>`;
  }

  function sheetPlayer() {
    const idx = state.players.findIndex(x => x.id === sheet.id);
    const p = state.players[idx];
    if (!p) return '';
    const admin = isAdmin();
    const stats = C.allStats(state);
    const c = C.playerCalc(state, p, stats, fmt);
    const days = C.days(state);
    const inTour = C.inTournament(p);

    const fields = admin ? `
      <div class="form-grid">
        <label><span>Name</span><input class="field" type="text" value="${esc(p.name)}" data-input="name" data-id="${esc(p.id)}" data-focus="pname-${esc(p.id)}" maxlength="60"></label>
        <label><span>Role</span><select class="field select" data-input="role" data-id="${esc(p.id)}">${ROLES.map(r => `<option value="${esc(r)}" ${r === p.role ? 'selected' : ''}>${r || 'No role'}</option>`).join('')}</select></label>
        <div class="form-row"><span>Tournament</span>
          <span class="tour-row">${tourToggle(p)}<small class="muted">${inTour ? 'Shares the tournament fee' : 'Not charged the tournament fee'}</small></span></div>
      </div>` : `
      <dl class="kv"><dt>Role</dt><dd>${esc(p.role) || '—'}</dd>
        <dt>Tournament</dt><dd>${inTour ? 'Taking part' : 'Not taking part'}</dd></dl>`;

    const sumLine = `Paid ${fmt(c.paid)}${c.credit > 0 ? ` + Bought ${fmt(c.credit)}` : ''} − Cost ${fmt(c.cost)}`;

    return `
      <header class="sheet-head">
        <div><span class="day-kind">Player #${idx + 1}</span><h2>${esc(p.name) || 'Unnamed'}</h2></div>
        <button class="icon-btn" data-action="close-sheet" aria-label="Close">${X_ICON}</button>
      </header>
      <div class="sheet-body">
        <div class="net-hero ${netClass(c.net)}">
          <span>${netText(c.net)}</span>
          <strong>${fmtNet(c.net)}</strong>
          <small>${sumLine}</small>
        </div>
        ${admin ? `<div class="share-row">
          <button class="btn btn-whatsapp" data-action="share-player" data-id="${esc(p.id)}">${WA_ICON} Send on WhatsApp</button>
          <button class="btn btn-ghost" data-action="copy-player" data-id="${esc(p.id)}">${COPY_ICON} Copy</button>
        </div>` : ''}
        ${fields}
        <h3 class="sub">Payments <span class="muted">${fmt(c.paid)}</span></h3>
        ${paymentRows(p)}
        <h3 class="sub">Attendance</h3>
        <div class="att-grid">
          ${days.map(d => `<div class="att-cell ${d.type}"><span>${d.label}</span>${toggleButton(p, d, stats)}</div>`).join('')}
        </div>
        <h3 class="sub">Cost <span class="muted">${fmt(c.cost)}</span></h3>
        <ul class="breakdown">${c.lines.length ? c.lines.map(l => `<li class="${l.type}">${esc(l.text)}</li>`).join('') : '<li class="none">No cost</li>'}</ul>
        ${c.creditLines.length ? `<h3 class="sub">Bought for the team <span class="muted">${fmt(c.credit)}</span></h3>
        <ul class="breakdown credit">${c.creditLines.map(l => `<li class="${l.type}">${esc(l.text)}</li>`).join('')}</ul>` : ''}
      </div>`;
  }

  // ---------- dialogs & toast ----------
  function confirmDialog(title, message, okLabel = 'Confirm', danger = false) {
    return new Promise(resolve => {
      const root = $('#dialog-root');
      root.innerHTML = `<div class="dialog-backdrop"></div>
        <div class="dialog" role="alertdialog" aria-modal="true" aria-labelledby="dlg-title">
          <h3 id="dlg-title">${esc(title)}</h3><p>${esc(message)}</p>
          <div class="dialog-actions"><button class="btn btn-ghost" data-dlg="0">Cancel</button>
          <button class="btn ${danger ? 'btn-danger' : 'btn-primary'}" data-dlg="1">${esc(okLabel)}</button></div></div>`;
      const done = v => { root.innerHTML = ''; document.removeEventListener('keydown', onKey); resolve(v); };
      const onKey = e => { if (e.key === 'Escape') done(false); };
      document.addEventListener('keydown', onKey);
      root.querySelectorAll('[data-dlg]').forEach(b => b.addEventListener('click', () => done(b.dataset.dlg === '1')));
      root.querySelector('.dialog-backdrop').addEventListener('click', () => done(false));
      root.querySelector('[data-dlg="1"]').focus();
    });
  }

  let toastTimer;
  function toast(msg) {
    const t = $('#toast');
    t.textContent = msg;
    t.classList.add('show');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => t.classList.remove('show'), 3200);
  }

  // ---------- backup ----------
  function exportBackup() {
    // Version 3 adds expenses and tournament; amounts (per-day totals) keep older readers working.
    const amounts = {};
    C.days(state).forEach(d => {
      if (C.dayExpenses(state, d.id).length) amounts[d.id] = String(C.dayAmount(state, d.id));
    });
    const data = {
      app: 'cricket-ledger',
      version: 3,
      savedAt: new Date().toISOString(),
      players: state.players.map(p => ({
        id: p.id, name: p.name, role: p.role, paid: String(C.paidTotal(p) || ''), tournament: p.tournament, att: p.att, payments: p.payments
      })),
      amounts,
      trainings: state.trainings,
      expenses: state.expenses,
      tournament: state.tournament
    };
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = `cricket-ledger-${new Date().toISOString().slice(0, 10)}.json`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(a.href), 1000);
  }

  async function importBackup(file) {
    let data;
    try {
      data = JSON.parse(await file.text());
      if (!data || !Array.isArray(data.players)) throw new Error();
    } catch {
      toast('This file is not a valid ledger save file.');
      return;
    }
    const ok = await confirmDialog('Open backup?', `This replaces all current data with “${file.name}” (${data.players.length} players).`, 'Replace data', true);
    if (!ok) return;
    state = normalize({ ...data, settings: state.settings });
    sheet = null;
    commit();
    toast('Backup restored.');
  }

  // ---------- events ----------
  function findPlayer(id) { return state.players.find(p => p.id === id); }
  function findPayment(el) {
    const list = paymentsOf(el.dataset.player);
    return list && list.find(x => x.id === el.dataset.id);
  }
  function findExpense(el) { return (state.expenses[el.dataset.day] || []).find(e => e.id === el.dataset.id); }

  const actions = {
    tab(el) { tab = el.dataset.tab; writeStore(TAB_KEY, tab); sheet = null; render(); window.scrollTo({ top: 0 }); },
    'open-player'(el) { sheet = { type: 'player', id: el.dataset.id }; render(); },
    'open-day'(el) { sheet = { type: 'day', dayId: el.dataset.id }; render(); },
    'close-sheet'() { sheet = null; render(); },
    async logout() {
      const ok = await confirmDialog('Switch user?', 'You will go back to the start screen.', 'Switch user');
      if (!ok) return;
      if (saveTimer) { clearTimeout(saveTimer); await flushSave(); }
      session = null;
      writeStore(SESSION_KEY, null);
      writeStore(ME_KEY, null);
      sheet = null;
      showSplash();
    },
    'toggle-att'(el) {
      if (!isAdmin()) return;
      const p = findPlayer(el.dataset.player);
      const day = C.days(state).find(d => d.id === el.dataset.day);
      if (!p || !day) return;
      if (p.att[day.id]) {
        delete p.att[day.id];
      } else {
        const n = C.dayStats(state, day).n;
        if (day.type === 'match' && n >= state.settings.maxPerMatch) {
          toast(`${state.settings.maxPerMatch} players already played ${day.label}`);
          return;
        }
        p.att[day.id] = true;
      }
      commit();
    },
    'add-training'() {
      if (!isAdmin()) return;
      state.trainings.push({ id: 't' + rid(), desc: '' });
      commit();
      toast(`Training ${state.trainings.length} added.`);
    },
    async 'remove-training'(el) {
      const idx = state.trainings.findIndex(t => t.id === el.dataset.id);
      if (idx < 0 || state.trainings.length <= 1) return;
      const ok = await confirmDialog(`Remove Training ${idx + 1}?`, 'Its expenses and attendance will be removed.', 'Remove', true);
      if (!ok) return;
      const id = state.trainings[idx].id;
      state.trainings.splice(idx, 1);
      delete state.expenses[id];
      state.players.forEach(p => { delete p.att[id]; });
      if (sheet && sheet.dayId === id) sheet = null;
      commit();
    },
    'add-players'() {
      const ta = $('#add-names');
      const names = ta.value.split(/[\n,]+/).map(s => s.trim()).filter(Boolean);
      if (!names.length) { toast('Type or paste at least one name.'); return; }
      names.forEach(name => state.players.push({ id: rid(), name, role: '', tournament: true, att: {}, payments: [] }));
      draftNames = '';
      commit();
      toast(`${names.length} player${names.length === 1 ? '' : 's'} added.`);
    },
    async 'remove-player'(el) {
      const p = findPlayer(el.dataset.id);
      if (!p) return;
      const bought = Object.values(state.expenses).flat().filter(e => e.paidBy === p.id).length;
      const ok = await confirmDialog(`Remove ${p.name || 'this player'}?`,
        'Their attendance and payments will be removed.' + (bought ? ` ${bought} item${bought === 1 ? '' : 's'} they paid for will be marked as paid by the team fund.` : ''),
        'Remove', true);
      if (!ok) return;
      state.players = state.players.filter(x => x.id !== p.id);
      Object.values(state.expenses).forEach(list => list.forEach(e => { if (e.paidBy === p.id) e.paidBy = ''; }));
      commit();
    },
    export() { exportBackup(); },
    import() { $('#import-file').click(); },
    async 'new-series'() {
      const ok = await confirmDialog('Start a new series?', 'Keeps players, names and roles. Clears attendance, payments, expenses, the league fee and payments to management, and resets trainings to just Training 1.', 'Start new series', true);
      if (!ok) return;
      state.players.forEach(p => { p.att = {}; p.payments = []; });
      state.expenses = {};
      state.tournament = { name: '', fee: '', payments: [] };
      state.trainings = [{ id: 't1', desc: '' }];
      commit();
      toast('New series started.');
    },
    async 'clear-all'() {
      const ok = await confirmDialog('Clear everything?', 'This removes all players, trainings, expenses, the tournament fee and payments. Save a backup first if you might need it.', 'Clear all', true);
      if (!ok) return;
      const settings = state.settings;
      state = { ...emptyState(), settings };
      sheet = null;
      commit();
      toast('All data cleared.');
    },
    'toggle-roundup'() { state.settings.roundUp = !state.settings.roundUp; commit(); },
    'toggle-tournament'(el) {
      const p = findPlayer(el.dataset.id);
      if (!p || !isAdmin()) return;
      p.tournament = !C.inTournament(p);
      commit();
    },
    'add-payment'(el) {
      const list = paymentsOf(el.dataset.id);
      if (!list || !isAdmin()) return;
      const x = { id: rid(), date: today(), amount: '', method: el.dataset.id === MGMT ? 'Bank transfer' : 'Cash', note: '' };
      list.push(x);
      commit();
      const target = document.querySelector(`[data-focus="pya-${x.id}"]`);
      if (target) target.focus();
    },
    async 'remove-payment'(el) {
      const owner = el.dataset.player;
      const list = paymentsOf(owner);
      const x = list && list.find(y => y.id === el.dataset.id);
      if (!x) return;
      if (C.num(x.amount) > 0) {
        const whose = owner === MGMT ? 'the payments to management' : `${findPlayer(owner).name}'s payments`;
        const ok = await confirmDialog('Remove this payment?', `${fmt(C.num(x.amount))}${x.date ? ' on ' + formatDate(x.date) : ''} will be removed from ${whose}.`, 'Remove', true);
        if (!ok) return;
      }
      list.splice(list.indexOf(x), 1);
      commit();
    },
    'share-player'(el) { const p = findPlayer(el.dataset.id); if (p) openWhatsApp(playerMessage(p)); },
    'copy-player'(el) { const p = findPlayer(el.dataset.id); if (p) copyText(playerMessage(p)); },
    'share-team'() { openWhatsApp(teamMessage()); },
    async 'share-image'(el) {
      const label = el.innerHTML;
      el.disabled = true;
      el.textContent = 'Creating…';
      try {
        showImageDialog(await balanceImage());
      } catch (e) {
        toast('Could not create the picture.');
      } finally {
        el.disabled = false;
        el.innerHTML = label;
      }
    },
    'copy-team'() { copyText(teamMessage()); },
    'not-me'() { writeStore(ME_KEY, NOT_ME); render(); },
    'add-expense'(el) {
      if (!isAdmin()) return;
      const day = el.dataset.day;
      const e = { id: rid(), item: el.dataset.item || '', amount: '', paidBy: '' };
      (state.expenses[day] = state.expenses[day] || []).push(e);
      commit();
      // Jump to the field that still needs filling in.
      const target = document.querySelector(`[data-focus="${e.item ? 'exa' : 'exi'}-${e.id}"]`);
      if (target) target.focus();
    },
    'remove-expense'(el) {
      const list = state.expenses[el.dataset.day];
      if (!list) return;
      state.expenses[el.dataset.day] = list.filter(e => e.id !== el.dataset.id);
      if (!state.expenses[el.dataset.day].length) delete state.expenses[el.dataset.day];
      commit();
    }
  };

  const inputs = {
    'training-desc'(el) { const t = state.trainings.find(x => x.id === el.dataset.id); if (t) t.desc = el.value; },
    'exp-item'(el) { const e = findExpense(el); if (e) e.item = el.value; },
    'exp-amount'(el) { el.value = cleanNumber(el.value); const e = findExpense(el); if (e) e.amount = el.value; },
    'exp-paidby'(el) { const e = findExpense(el); if (e) e.paidBy = el.value; },
    'tournament-fee'(el) { el.value = cleanNumber(el.value); state.tournament.fee = el.value; },
    'tournament-name'(el) { state.tournament.name = el.value; },
    name(el) { const p = findPlayer(el.dataset.id); if (p) p.name = el.value; },
    'pay-date'(el) { const x = findPayment(el); if (x) x.date = el.value; },
    'pay-amount'(el) { el.value = cleanNumber(el.value); const x = findPayment(el); if (x) x.amount = el.value; },
    'pay-method'(el) { const x = findPayment(el); if (x) x.method = el.value; },
    'pay-note'(el) { const x = findPayment(el); if (x) x.note = el.value; },
    role(el) { const p = findPlayer(el.dataset.id); if (p) p.role = el.value; },
    currency(el) { state.settings.currency = el.value; },
    max(el) {
      el.value = el.value.replace(/\D/g, '').slice(0, 2);
      const n = parseInt(el.value, 10);
      if (n > 0) state.settings.maxPerMatch = n;
    }
  };

  function wireEvents() {
    document.addEventListener('click', e => {
      const el = e.target.closest('[data-action]');
      if (!el || el.disabled) return;
      const fn = actions[el.dataset.action];
      if (fn) { e.preventDefault(); fn(el); }
    });

    const onInput = e => {
      const el = e.target;
      const fn = el.dataset && inputs[el.dataset.input];
      if (el.id === 'add-names') { draftNames = el.value; return; }
      if (el.dataset && el.dataset.input === 'pick-me') { writeStore(ME_KEY, el.value || NOT_ME); render(); return; }
      if (!fn || !isAdmin()) return;
      fn(el);
      commit();
    };
    document.addEventListener('input', onInput);

    document.addEventListener('keydown', e => {
      if (e.key === 'Escape' && sheet && !$('#dialog-root').innerHTML) { sheet = null; render(); }
    });

    $('#import-file').addEventListener('change', e => {
      const f = e.target.files[0];
      e.target.value = '';
      if (f) importBackup(f);
    });

    $('#login-form').addEventListener('submit', e => {
      e.preventDefault();
      const name = $('#login-name').value.trim();
      if (!name) { $('#login-error').textContent = 'Please enter your name.'; return; }
      login(name);
    });
    $('#continue-btn').addEventListener('click', () => enterApp());
  }

  wireEvents();
  // Returning visitors go straight back in; the name screen is only for first visits and "Switch user".
  if (session) enterApp(); else showSplash();
})();
