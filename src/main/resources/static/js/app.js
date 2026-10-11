(() => {
  'use strict';

  const C = window.RFCalc;
  const API = (window.RF_API_BASE || '').replace(/\/$/, '');
  const ROLES = ['', 'Batsman', 'Batsman + Captain', 'Bowler', 'All-rounder', 'Wicket-keeper'];
  const CURRENCIES = ['Rs ', 'PKR ', '₹', '$', '£'];
  const SESSION_KEY = 'rf.session';
  const TAB_KEY = 'rf.tab';
  const POLL_MS = 15000;

  const TABS = [
    { id: 'summary', label: 'Summary', icon: '<path d="M3 3v18h18"/><path d="M7 15l4-4 3 3 5-6"/>' },
    { id: 'days', label: 'Days', icon: '<rect x="3" y="4" width="18" height="18" rx="2"/><path d="M16 2v4M8 2v4M3 10h18"/>' },
    { id: 'players', label: 'Players', icon: '<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75"/>' },
    { id: 'settings', label: 'Settings', icon: '<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/>' }
  ];

  let session = readStore(SESSION_KEY);      // { name, admin, token }
  let tab = readStore(TAB_KEY) || 'summary';
  let state = null;                           // { players, trainings, amounts, settings }
  let sheet = null;                           // { type: 'attendance', dayId } | { type: 'player', id }
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

  function emptyState() {
    return { players: [], trainings: [{ id: 't1', desc: '' }], amounts: {}, settings: { currency: 'Rs ', maxPerMatch: 11, roundUp: false } };
  }

  function normalize(d) {
    const s = d.settings || {};
    return {
      players: (d.players || []).map(p => ({ id: p.id, name: p.name || '', role: p.role || '', paid: p.paid == null ? '' : String(p.paid), att: p.att || {} })),
      trainings: d.trainings && d.trainings.length ? d.trainings.map(t => ({ id: t.id, desc: t.desc || '' })) : [{ id: 't1', desc: '' }],
      amounts: d.amounts || {},
      settings: {
        currency: CURRENCIES.includes(s.currency) ? s.currency : 'Rs ',
        maxPerMatch: s.maxPerMatch > 0 ? s.maxPerMatch : 11,
        roundUp: !!s.roundUp
      }
    };
  }

  function comparable(d) {
    const { players, trainings, amounts, settings } = d;
    return JSON.stringify({ players, trainings, amounts, settings });
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

  function viewSummary() {
    const t = C.totals(state, fmt);
    const stats = C.allStats(state);
    const days = C.days(state);
    const admin = isAdmin();
    const cards = [
      { label: 'Collected', value: fmt(t.collected), cls: '' },
      { label: 'Total spent', value: fmt(t.spent), cls: '' },
      { label: admin ? 'You pay back' : 'To be paid back', value: fmt(t.payBack), cls: 'pos' },
      { label: admin ? 'Still owed to you' : 'Still owed', value: fmt(t.owed), cls: 'neg' }
    ];

    const rows = state.players.map((p, i) => {
      const c = C.playerCalc(state, p, stats, fmt);
      return `<button class="prow" data-action="open-player" data-id="${esc(p.id)}">
        <span class="sr">${i + 1}</span>
        <span class="pname"><strong>${esc(p.name) || '<em>Unnamed</em>'}</strong><small>${esc(p.role) || '—'}</small></span>
        <span class="pnet ${netClass(c.net)}"><strong>${fmtNet(c.net)}</strong><small>${C.netLabel(c.net)}</small></span>
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
                <th>Cost</th><th>Paid</th><th>Net</th></tr>
            </thead>
            <tbody>
              ${state.players.map((p, i) => {
                const c = C.playerCalc(state, p, stats, fmt);
                return `<tr data-action="open-player" data-id="${esc(p.id)}">
                  <td class="sticky">${i + 1}</td><td class="sticky sticky-2 name-cell">${esc(p.name)}</td>
                  ${days.map(d => `<td class="dot-cell">${p.att[d.id] ? `<span class="dot ${d.type}" title="${d.type === 'match' ? 'Played' : 'Arrived'}"></span>` : '<span class="dot-empty"></span>'}</td>`).join('')}
                  <td>${fmt(c.cost)}</td><td>${fmt(c.paid)}</td><td class="${netClass(c.net)} strong">${fmtNet(c.net)}</td></tr>`;
              }).join('')}
            </tbody>
            <tfoot><tr><td class="sticky"></td><td class="sticky sticky-2">Attended</td>
              ${days.map(d => `<td>${stats[d.id].n}</td>`).join('')}<td>${fmt(t.spent)}</td><td>${fmt(t.collected)}</td><td></td></tr></tfoot>
          </table>
        </div>
      </section>` : '';

    return `
      ${pageHead('Summary', `${state.players.length} player${state.players.length === 1 ? '' : 's'} · ${state.trainings.length} training${state.trainings.length === 1 ? '' : 's'} · 5 matches`)}
      <div class="stats">${cards.map(c => `<div class="stat ${c.cls}"><span>${c.label}</span><strong>${c.value}</strong></div>`).join('')}</div>
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

  function viewDays() {
    const admin = isAdmin();
    const stats = C.allStats(state);
    const max = state.settings.maxPerMatch;
    const cards = C.days(state).map(d => {
      const s = stats[d.id];
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
        <label class="amount">
          <span>Amount spent</span>
          ${admin
            ? `<span class="money-input"><em>${esc(cur().trim())}</em><input type="text" inputmode="decimal" placeholder="0" value="${esc(state.amounts[d.id] || '')}" data-input="amount" data-id="${esc(d.id)}" data-focus="amt-${esc(d.id)}"></span>`
            : `<strong>${s.amount > 0 ? fmt(s.amount) : '—'}</strong>`}
        </label>
        <div class="day-meta"><span>${count}</span><span class="share">${share}</span></div>
        <button class="btn btn-block ${d.type === 'match' ? 'btn-gold' : 'btn-primary'}" data-action="open-day" data-id="${esc(d.id)}">
          ${admin ? 'Mark attendance' : 'See attendance'}</button>
      </article>`;
    }).join('');

    return `
      ${pageHead('Days', 'Trainings first, then Match 1–5. Each day’s cost is split among those who attended.',
        admin ? `<button class="btn btn-primary" data-action="add-training">${icon('<path d="M12 5v14M5 12h14"/>', 18)} Add training</button>` : '')}
      ${warningBanner()}
      <div class="day-grid">${cards}</div>`;
  }

  function viewPlayers() {
    const admin = isAdmin();
    const list = state.players.map((p, i) => `
      <div class="player-line">
        <span class="sr">${i + 1}</span>
        <button class="pl-name" data-action="open-player" data-id="${esc(p.id)}">${esc(p.name) || '<em>Unnamed</em>'}</button>
        ${admin
          ? `<select class="field select" data-input="role" data-id="${esc(p.id)}" aria-label="Role for ${esc(p.name)}">${ROLES.map(r => `<option value="${esc(r)}" ${r === p.role ? 'selected' : ''}>${r || 'No role'}</option>`).join('')}</select>
             <button class="icon-btn danger" data-action="remove-player" data-id="${esc(p.id)}" aria-label="Remove ${esc(p.name)}">${X_ICON}</button>`
          : `<span class="muted role">${esc(p.role) || '—'}</span>`}
      </div>`).join('');

    return `
      ${pageHead('Players', `${state.players.length} in the squad`)}
      <div class="players-layout">
        <section class="panel">
          <div class="player-head"><span>Sr.</span><span>Name</span><span>Role</span></div>
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
    if (sheet.type === 'attendance') body = sheetAttendance();
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

  function sheetAttendance() {
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
      ${full && isAdmin() ? `<p class="hint">${max} players already played ${d.label}</p>` : ''}
      <div class="sheet-body">${rows || '<p class="muted center">No players yet.</p>'}</div>`;
  }

  function sheetPlayer() {
    const idx = state.players.findIndex(x => x.id === sheet.id);
    const p = state.players[idx];
    if (!p) return '';
    const admin = isAdmin();
    const stats = C.allStats(state);
    const c = C.playerCalc(state, p, stats, fmt);
    const days = C.days(state);

    const fields = admin ? `
      <div class="form-grid">
        <label><span>Name</span><input class="field" type="text" value="${esc(p.name)}" data-input="name" data-id="${esc(p.id)}" data-focus="pname-${esc(p.id)}" maxlength="60"></label>
        <label><span>Role</span><select class="field select" data-input="role" data-id="${esc(p.id)}">${ROLES.map(r => `<option value="${esc(r)}" ${r === p.role ? 'selected' : ''}>${r || 'No role'}</option>`).join('')}</select></label>
        <label><span>Paid</span><span class="money-input"><em>${esc(cur().trim())}</em><input type="text" inputmode="decimal" placeholder="0" value="${esc(p.paid)}" data-input="paid" data-id="${esc(p.id)}" data-focus="paid-${esc(p.id)}"></span></label>
      </div>` : `
      <dl class="kv"><dt>Role</dt><dd>${esc(p.role) || '—'}</dd><dt>Paid</dt><dd>${fmt(c.paid)}</dd></dl>`;

    return `
      <header class="sheet-head">
        <div><span class="day-kind">Player #${idx + 1}</span><h2>${esc(p.name) || 'Unnamed'}</h2></div>
        <button class="icon-btn" data-action="close-sheet" aria-label="Close">${X_ICON}</button>
      </header>
      <div class="sheet-body">
        <div class="net-hero ${netClass(c.net)}">
          <span>${C.netLabel(c.net)}</span>
          <strong>${fmtNet(c.net)}</strong>
          <small>Paid ${fmt(c.paid)} − Cost ${fmt(c.cost)}</small>
        </div>
        ${fields}
        <h3 class="sub">Attendance</h3>
        <div class="att-grid">
          ${days.map(d => `<div class="att-cell ${d.type}"><span>${d.label}</span>${toggleButton(p, d, stats)}</div>`).join('')}
        </div>
        <h3 class="sub">Cost <span class="muted">${fmt(c.cost)}</span></h3>
        <ul class="breakdown">${c.lines.length ? c.lines.map(l => `<li class="${l.day.type}">${esc(l.text)}</li>`).join('') : '<li class="none">No cost</li>'}</ul>
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
    const data = {
      app: 'cricket-ledger',
      version: 2,
      savedAt: new Date().toISOString(),
      players: state.players.map(p => ({ id: p.id, name: p.name, role: p.role, paid: p.paid, att: p.att })),
      amounts: state.amounts,
      trainings: state.trainings
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
    const next = normalize({ ...data, settings: state.settings });
    next.players.forEach(p => { if (!p.id) p.id = rid(); });
    state = next;
    sheet = null;
    commit();
    toast('Backup restored.');
  }

  // ---------- events ----------
  function findPlayer(id) { return state.players.find(p => p.id === id); }

  const actions = {
    tab(el) { tab = el.dataset.tab; writeStore(TAB_KEY, tab); sheet = null; render(); window.scrollTo({ top: 0 }); },
    'open-player'(el) { sheet = { type: 'player', id: el.dataset.id }; render(); },
    'open-day'(el) { sheet = { type: 'attendance', dayId: el.dataset.id }; render(); },
    'close-sheet'() { sheet = null; render(); },
    async logout() {
      const ok = await confirmDialog('Switch user?', 'You will go back to the start screen.', 'Switch user');
      if (!ok) return;
      if (saveTimer) { clearTimeout(saveTimer); await flushSave(); }
      session = null;
      writeStore(SESSION_KEY, null);
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
      const ok = await confirmDialog(`Remove Training ${idx + 1}?`, 'Its amount and attendance will be removed.', 'Remove', true);
      if (!ok) return;
      const id = state.trainings[idx].id;
      state.trainings.splice(idx, 1);
      delete state.amounts[id];
      state.players.forEach(p => { delete p.att[id]; });
      if (sheet && sheet.dayId === id) sheet = null;
      commit();
    },
    'add-players'() {
      const ta = $('#add-names');
      const names = ta.value.split(/[\n,]+/).map(s => s.trim()).filter(Boolean);
      if (!names.length) { toast('Type or paste at least one name.'); return; }
      names.forEach(name => state.players.push({ id: rid(), name, role: '', paid: '', att: {} }));
      draftNames = '';
      commit();
      toast(`${names.length} player${names.length === 1 ? '' : 's'} added.`);
    },
    async 'remove-player'(el) {
      const p = findPlayer(el.dataset.id);
      if (!p) return;
      const ok = await confirmDialog(`Remove ${p.name || 'this player'}?`, 'Their attendance and payments will be removed.', 'Remove', true);
      if (!ok) return;
      state.players = state.players.filter(x => x.id !== p.id);
      commit();
    },
    export() { exportBackup(); },
    import() { $('#import-file').click(); },
    async 'new-series'() {
      const ok = await confirmDialog('Start a new series?', 'Keeps players, names and roles. Clears attendance, payments and amounts, and resets trainings to just Training 1.', 'Start new series', true);
      if (!ok) return;
      state.players.forEach(p => { p.att = {}; p.paid = ''; });
      state.amounts = {};
      state.trainings = [{ id: 't1', desc: '' }];
      commit();
      toast('New series started.');
    },
    async 'clear-all'() {
      const ok = await confirmDialog('Clear everything?', 'This removes all players, trainings, amounts and payments. Save a backup first if you might need it.', 'Clear all', true);
      if (!ok) return;
      const settings = state.settings;
      state = { ...emptyState(), settings };
      sheet = null;
      commit();
      toast('All data cleared.');
    },
    'toggle-roundup'() { state.settings.roundUp = !state.settings.roundUp; commit(); }
  };

  const inputs = {
    'training-desc'(el) { const t = state.trainings.find(x => x.id === el.dataset.id); if (t) t.desc = el.value; },
    amount(el) { el.value = cleanNumber(el.value); state.amounts[el.dataset.id] = el.value; },
    name(el) { const p = findPlayer(el.dataset.id); if (p) p.name = el.value; },
    paid(el) { el.value = cleanNumber(el.value); const p = findPlayer(el.dataset.id); if (p) p.paid = el.value; },
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
  showSplash();
})();
