(() => {
  'use strict';

  const C = window.RFCalc;
  const API = (window.RF_API_BASE || '').replace(/\/$/, '');
  const ROLES = ['', 'Batsman', 'Batsman + Captain', 'Bowler', 'All-rounder', 'Wicket-keeper'];
  const CURRENCIES = ['Rs ', 'PKR ', '₹', '$', '£'];
  const PAYMENT_METHODS = ['Cash', 'JazzCash', 'Easypaisa', 'Bank transfer', 'Other'];
  const SESSION_KEY = 'rf.session';
  const TAB_KEY = 'rf.tab';
  const THEME_KEY = 'rf.theme';               // this device's theme id
  const THEMES = [
    { id: 'night', name: 'Falcon Night', note: 'Charcoal, falcon red and gold', bar: '#121215', colors: ['#111114', '#d0222c', '#e2b65c'] },
    { id: 'pitch', name: 'Pitch Green', note: 'Cricket-field green and gold', bar: '#0c1511', colors: ['#0c1511', '#2f9e5e', '#e2b65c'] },
    { id: 'day', name: 'Daylight', note: 'Light, for reading outdoors', bar: '#f5efe4', colors: ['#f5efe4', '#c81e28', '#b07f22'] },
    { id: 'navy', name: 'Midnight Blue', note: 'Deep navy, royal blue and gold', bar: '#0b1220', colors: ['#0b1220', '#2f6fd6', '#e2b65c'] },
    { id: 'royal', name: 'Royal Purple', note: 'Dark plum, violet and gold', bar: '#140d1c', colors: ['#140d1c', '#8b45d6', '#e2b65c'] },
    { id: 'sunset', name: 'Sunset', note: 'Warm dusk, sunset orange and gold', bar: '#170f0b', colors: ['#170f0b', '#e2662a', '#e2b65c'] }
  ];
  const COLLAPSED_KEY = 'rf.collapsed';      // Days sections this viewer has folded: { training, match }
  const DEVICE_KEY = 'rf.device';            // this browser's trusted-device token (skips the two-factor code)
  const FEED_SEEN_KEY = 'rf.feedSeen';       // newest notification this device has opened
  const MGR_KEY = 'rf.manager';              // this browser's pending manager request: { token, name, status }
  const ME_KEY = 'rf.me';                     // viewer's chosen player id, or NOT_ME to show the picker
  const NOT_ME = '__none';
  const POLL_MS = 15000;

  const TABS = [
    { id: 'summary', label: 'Home', icon: '<path d="M3 11l9-8 9 8"/><path d="M5 10v10a1 1 0 0 0 1 1h4v-6h4v6h4a1 1 0 0 0 1-1V10"/>' },
    { id: 'days', label: 'Days', daysTab: true, icon: '<rect x="3" y="4" width="18" height="18" rx="2"/><path d="M16 2v4M8 2v4M3 10h18"/>' },
    { id: 'money', label: 'Money', icon: '<rect x="2" y="6" width="20" height="13" rx="2"/><path d="M2 10h20M16 14.5h2"/>' },
    { id: 'stats', label: 'Stats', statsTab: true, icon: '<path d="M18 20V10M12 20V4M6 20v-6"/>' },
    { id: 'players', label: 'Team', icon: '<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75"/>' },
    { id: 'announce', label: 'Announce', staffTab: true, icon: '<path d="M3 11v2a1 1 0 0 0 1 1h2l5 4V6L6 10H4a1 1 0 0 0-1 1z"/><path d="M15.5 8.5a5 5 0 0 1 0 7"/><path d="M18.5 5.5a9 9 0 0 1 0 13"/>' },
    { id: 'settings', label: 'Settings', icon: '<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/>' }
  ];

  let session = readStore(SESSION_KEY);      // { name, admin, token }
  let tab = 'summary';                        // set from the remembered tab once we know who is signed in
  let state = null;                           // { players, trainings, expenses, tournament, settings }
  let sheet = null;                           // { type: 'day', dayId } | { type: 'player', id }
  let saveTimer = null;
  let pollTimer = null;
  let draftNames = '';
  let statsSel = 'league';
  let archive = null;                         // { id, name, archivedAt } while an archived season is open
  let liveState = null;                       // the live ledger, kept while an archive is open
  let seasons = null;
  let seasonDraft = null;                     // name typed for the next archive                         // archived seasons list (null until loaded)                    // Stats tab: 'league' or a match id                        // unsent text in the Add players box

  const $ = sel => document.querySelector(sel);

  // ---------- storage ----------
  function readStore(k) {
    try { return JSON.parse(localStorage.getItem(k)); } catch { return null; }
  }
  function writeStore(k, v) {
    try { v == null ? localStorage.removeItem(k) : localStorage.setItem(k, JSON.stringify(v)); } catch { /* ignore */ }
  }

  // ---------- theme ----------
  function currentTheme() {
    const id = readStore(THEME_KEY);
    return THEMES.some(t => t.id === id) ? id : 'night';
  }

  function applyTheme(id) {
    const theme = THEMES.find(t => t.id === id) || THEMES[0];
    if (theme.id === 'night') delete document.documentElement.dataset.theme;
    else document.documentElement.dataset.theme = theme.id;
    const meta = document.querySelector('meta[name="theme-color"]');
    if (meta) meta.setAttribute('content', theme.bar);
  }

  // ---------- install as an app ----------
  let installPrompt = null;                   // Chrome/Android's install prompt, kept until used
  const isInstalled = () => window.matchMedia('(display-mode: standalone)').matches || navigator.standalone === true;
  const isIOS = () => /iphone|ipad|ipod/i.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);

  window.addEventListener('beforeinstallprompt', e => {
    e.preventDefault();
    installPrompt = e;
    if (state && tab === 'settings') render();
  });
  window.addEventListener('appinstalled', () => {
    installPrompt = null;
    if (state) { render(); toast('Red Falcons is on your home screen.'); }
  });
  if ('serviceWorker' in navigator) {
    // The worker is told where the API lives so it can fetch notifications for this device.
    window.addEventListener('load', () => navigator.serviceWorker.register('sw.js?api=' + encodeURIComponent(API)).catch(() => {}));
  }

  // ---------- two-factor sign-in (admin) ----------
  let mgr = readStore(MGR_KEY);               // { token, name, status } once this browser asked to be a manager
  let mgrDraft = { phrase: '', edits: {} };   // the phrase being typed to ask, and phrases being edited by the admin
  let mgrList = null;                         // admin: manager requests and approved managers
  let editedAt = 0;                           // when the admin last changed something on this screen
  let announcements = null;                   // staff: every announcement written, newest first
  let annSeg = 'ann';                         // which part of the Announce tab is open: 'ann' or 'poll'
  let annDraft = { title: '', body: '', matchId: '', later: false, at: '', action: '' };   // the announcement being written
  let calMonth = null;                        // the month the fixtures calendar shows: { y, m }
  let feed = null;                            // viewers and managers: what the admin has announced, newest first
  let inbox = null;                           // admin: { access, lineups } waiting for a decision
  let declineDraft = {};                      // admin: notes being written while declining, by 'kind:id'
  let xiDirty = {};                           // manager: matches whose lineup was changed here but not sent yet
  let myLineups = [];                         // manager: their recent lineup requests and what became of them
  let tfa = null;                             // { enabled, recoveryLeft, devices } from the server
  let tfaSetup = null;                        // { secret, uri, qr } while setting up
  let tfaCodes = null;                        // recovery codes to show once after turning it on
  const tfaDraft = { code: '', off: '' };
  let qrLib = null;

  function loadQrLib() {
    if (window.qrcode) return Promise.resolve();
    if (!qrLib) {
      qrLib = new Promise((resolve, reject) => {
        const el = document.createElement('script');
        el.src = 'https://cdnjs.cloudflare.com/ajax/libs/qrcode-generator/1.4.4/qrcode.min.js';
        el.onload = resolve;
        el.onerror = () => { qrLib = null; reject(new Error('QR unavailable')); };
        document.head.appendChild(el);
      });
    }
    return qrLib;
  }

  async function loadTfa() {
    if (!realAdmin()) return;
    try { tfa = await api('GET', '/api/2fa'); } catch { tfa = tfa || { enabled: false, devices: [], recoveryLeft: 0, error: true }; }
    renderIfSettings();
  }

  function tfaPanel() {
    if (!isAdmin()) return '';
    const head = note => `<div class="panel-head"><h3>Two-factor sign-in</h3><p class="muted">${note}</p></div>`;
    if (tfaCodes) {
      return `<section class="panel tfa-panel">${head('Two-factor is on. Save these recovery codes somewhere safe — each one signs you in once if you lose your phone. They won’t be shown again.')}
        <ol class="tfa-codes">${tfaCodes.map(c => `<li><code>${esc(c)}</code></li>`).join('')}</ol>
        <div class="settings-actions">
          <button class="btn btn-ghost" data-action="tfa-copy">${COPY_ICON} Copy codes</button>
          <button class="btn btn-gold" data-action="tfa-done">I’ve saved them</button>
        </div>
      </section>`;
    }
    if (tfaSetup) {
      const grouped = tfaSetup.secret.replace(/(.{4})/g, '$1 ').trim();
      return `<section class="panel tfa-panel">${head('Scan the code with an authenticator app (Google Authenticator, Microsoft Authenticator, Authy…), then type the 6 digits it shows.')}
        <div class="tfa-setup">
          <div class="tfa-qr">${tfaSetup.qr || `<a class="btn btn-ghost small" href="${esc(tfaSetup.uri)}">Open in authenticator app</a>`}</div>
          <div class="tfa-steps">
            <p class="muted">Can’t scan? Add it by hand with this key:</p>
            <code class="tfa-secret">${esc(grouped)}</code>
            <input class="field" type="text" inputmode="numeric" autocomplete="one-time-code" maxlength="7" placeholder="6-digit code" value="${esc(tfaDraft.code)}" data-input="tfa-code" data-focus="tfa-code" aria-label="Code from the app">
            <div class="settings-actions">
              <button class="btn btn-primary" data-action="tfa-confirm">Turn on</button>
              <button class="btn btn-ghost" data-action="tfa-cancel">Cancel</button>
            </div>
          </div>
        </div>
      </section>`;
    }
    if (!tfa) return `<section class="panel tfa-panel">${head('Loading…')}</section>`;
    if (!tfa.enabled) {
      return `<section class="panel tfa-panel">${head('Off. Anyone who knows the admin key can sign in as admin. Turn it on so signing in also needs a code from your phone.')}
        <button class="btn btn-gold" data-action="tfa-start">${icon('<rect x="5" y="2" width="14" height="20" rx="2"/><path d="M12 18h.01"/>', 18)} Set up two-factor</button>
      </section>`;
    }
    const devices = tfa.devices || [];
    return `<section class="panel tfa-panel">${head(`On. Signing in as admin needs a code from your phone, except on trusted browsers. ${tfa.recoveryLeft} recovery code${tfa.recoveryLeft === 1 ? '' : 's'} left.`)}
      <p class="install-done">${icon('<path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/><path d="m9 12 2 2 4-4"/>', 18)} Two-factor is on</p>
      <h4 class="league-sub">Trusted browsers</h4>
      ${devices.length ? `<ul class="season-list">${devices.map(d => `<li>
          <span class="season-icon">${icon('<rect x="2" y="4" width="20" height="14" rx="2"/><path d="M8 21h8M12 18v3"/>', 18)}</span>
          <span class="season-text"><strong>${esc(d.label)}</strong><small>${d.current ? '<em class="tfa-this">This browser</em> · ' : ''}Last used ${esc(formatDate(localDay(d.lastUsed)))}</small></span>
          <span></span>
          <button class="icon-btn danger" data-action="tfa-revoke" data-id="${esc(d.id)}" data-current="${d.current}" aria-label="Stop trusting ${esc(d.label)}">${X_ICON}</button>
        </li>`).join('')}</ul>` : '<p class="muted">None. Tick “Trust this browser” when you sign in to skip the code there.</p>'}
      <div class="tfa-off">
        <input class="field" type="text" maxlength="9" placeholder="Code or recovery code" value="${esc(tfaDraft.off)}" data-input="tfa-off" data-focus="tfa-off" aria-label="Code to turn two-factor off">
        <button class="btn btn-danger" data-action="tfa-disable">Turn off</button>
      </div>
    </section>`;
  }

  // ---------- notifications ----------
  let push = { ready: false, supported: false, enabled: false, key: '', permission: 'default', subscribed: false };
  let pushDraft = { title: 'Red Falcons', body: '', audience: 'all' };
  const pushSupported = () => 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window;

  function keyBytes(b64) {
    const s = (b64 + '='.repeat((4 - b64.length % 4) % 4)).replace(/-/g, '+').replace(/_/g, '/');
    return Uint8Array.from(atob(s), c => c.charCodeAt(0));
  }

  // Who this device belongs to, so player-targeted messages reach it ('' for the admin or unknown).
  const pushPlayerId = () => (realAdmin() ? '' : (myPlayer() || {}).id || '');

  async function refreshPush() {
    push.supported = pushSupported();
    if (!push.supported) { push.ready = true; renderIfSettings(); return; }
    try {
      const k = await api('GET', '/api/push/key');
      push.enabled = !!k.enabled;
      push.key = k.publicKey || '';
      push.permission = Notification.permission;
      const reg = await navigator.serviceWorker.ready;
      const sub = await reg.pushManager.getSubscription();
      push.subscribed = !!sub;
      // Keep the server's record of this device current (who it is, admin or not).
      if (sub && push.enabled) await api('POST', '/api/push/subscribe', { endpoint: sub.endpoint, playerId: pushPlayerId() });
    } catch { /* leave as is */ }
    push.ready = true;
    renderIfSettings();
  }

  function renderIfSettings() { if (state && (tab === 'settings' || tab === 'summary')) render(); }

  const PUSH_PROMPT_KEY = 'rf.pushPromptOff';
  async function enablePush() {
    try {
      const perm = await Notification.requestPermission();
      push.permission = perm;
      if (perm !== 'granted') { toast('Notifications weren’t allowed.'); renderIfSettings(); return; }
      const reg = await navigator.serviceWorker.ready;
      const sub = await reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: keyBytes(push.key) });
      await api('POST', '/api/push/subscribe', { endpoint: sub.endpoint, playerId: pushPlayerId() });
      push.subscribed = true;
      toast('Notifications are on for this device.');
    } catch {
      toast('Could not turn notifications on here.');
    }
    renderIfSettings();
  }

  async function disablePush() {
    try {
      const reg = await navigator.serviceWorker.ready;
      const sub = await reg.pushManager.getSubscription();
      if (sub) {
        await api('POST', '/api/push/unsubscribe', { endpoint: sub.endpoint }).catch(() => {});
        await sub.unsubscribe();
      }
      push.subscribed = false;
      toast('Notifications are off for this device.');
    } catch {
      toast('Could not turn notifications off.');
    }
    renderIfSettings();
  }

  // Players a notification goes to: null means every device.
  function pushAudience() {
    const a = pushDraft.audience;
    if (a === 'all') return { ids: null, label: 'everyone with notifications on' };
    if (a === 'owe') {
      const stats = C.allStats(state);
      const tour = C.tournamentStats(state, stats);
      const ids = state.players.filter(p => C.playerCalc(state, p, stats, fmt, tour).net < 0).map(p => p.id);
      return { ids, label: `${ids.length} player${ids.length === 1 ? '' : 's'} who owe money` };
    }
    const m = findMatch(a.replace(/^xi:/, ''));
    if (!m) return { ids: [], label: 'nobody' };
    const ids = state.players.filter(p => p.att[m.id]).map(p => p.id).concat(state.twelfth[m.id] ? [state.twelfth[m.id]] : []);
    return { ids, label: `${m.label}’s XI and 12th man (${ids.length})` };
  }

  function notifyPanels() {
    const ios = isIOS() && !isInstalled();
    let status;
    if (!push.ready) status = '<p class="muted">Checking…</p>';
    else if (!push.supported) status = '<p class="muted">This browser can’t show notifications.</p>';
    else if (ios) status = '<p class="muted">On iPhone, install the app first (Appearance → Install on phone), then turn notifications on from the installed app.</p>';
    else if (!push.enabled) status = '<p class="muted">Notifications aren’t set up on the server yet.</p>';
    else if (push.permission === 'denied') status = '<p class="muted">Notifications are blocked for this site. Allow them in your browser’s site settings, then come back.</p>';
    else if (push.subscribed) {
      const me = !realAdmin() && myPlayer();
      status = `<p class="install-done">${icon('<path d="M20 6 9 17l-5-5"/>', 18)} On for this device${me ? ` · ${esc(me.name)}` : ''}</p>
        ${!realAdmin() && !me ? '<p class="muted notify-note">Choose your name in Settings so messages meant for you reach you.</p>' : ''}
        <button class="btn btn-ghost" data-action="push-off">Turn off</button>`;
    } else {
      status = `<button class="btn btn-gold btn-block" data-action="push-on">${BELL_ICON} Turn on notifications</button>`;
    }
    const device = `<section class="panel">
      <div class="panel-head"><h3>This device</h3><p class="muted">Get match updates and payment reminders as notifications.</p></div>
      ${status}
    </section>`;
    if (!isAdmin()) return { device, compose: '' };

    const aud = pushAudience();
    const options = [['all', 'Everyone'], ['owe', 'Players who owe money']]
      .concat(C.matches(state).map(m => ['xi:' + m.id, `${m.label} XI + 12th`]));
    const templates = [
      ['Payment reminder', 'Please clear your balance — your amount and the NayaPay details are in the app.'],
      ['Match day', 'Match today! Check the Playing XI and reporting time in the app.'],
      ['Training', 'Training today — see you there!']
    ];
    const compose = `<section class="panel">
      <div class="panel-head"><h3>Send a notification</h3><p class="muted">Goes to players’ phones that have notifications on.</p></div>
      <div class="notify-form">
        <div class="quick-add">${templates.map(([t, b]) => `<button class="chip" data-action="push-template" data-title="${esc(t)}" data-body="${esc(b)}">${esc(t)}</button>`).join('')}</div>
        <input class="field" type="text" maxlength="80" placeholder="Title" value="${esc(pushDraft.title)}" data-input="push-title" data-focus="push-title" aria-label="Title">
        <textarea class="field textarea notify-body" maxlength="300" placeholder="Message" data-input="push-body" data-focus="push-body" aria-label="Message">${esc(pushDraft.body)}</textarea>
        <select class="field select" data-input="push-audience" aria-label="Send to">
          ${options.map(([v, l]) => `<option value="${esc(v)}" ${v === pushDraft.audience ? 'selected' : ''}>${esc(l)}</option>`).join('')}
        </select>
        <p class="muted notify-note">To: ${esc(aud.label)}</p>
        <button class="btn btn-primary" data-action="push-send" ${push.enabled && pushDraft.body.trim() ? '' : 'disabled'}>${BELL_ICON} Send</button>
      </div>
    </section>`;
    return { device, compose };
  }

  function installPanel() {
    const SHARE = icon('<path d="M4 12v8a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-8"/><path d="m16 6-4-4-4 4"/><path d="M12 2v13"/>', 16);
    let body;
    if (isInstalled()) {
      body = `<p class="install-done">${icon('<path d="M20 6 9 17l-5-5"/>', 18)} Installed — you’re using the app.</p>`;
    } else if (installPrompt) {
      body = `<button class="btn btn-gold btn-block" data-action="install-app">${icon('<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M7 10l5 5 5-5M12 15V3"/>', 18)} Install app</button>`;
    } else if (isIOS()) {
      body = `<ol class="install-steps">
        <li>Tap the <strong>Share</strong> button ${SHARE} in Safari</li>
        <li>Scroll and tap <strong>Add to Home Screen</strong></li>
        <li>Tap <strong>Add</strong></li>
      </ol>`;
    } else {
      body = `<p class="muted install-hint">Open your browser menu (⋮) and choose <strong>Install app</strong> or <strong>Add to Home screen</strong>.</p>`;
    }
    return `<section class="panel install-panel">
      <div class="panel-head"><h3>Install on phone</h3><p class="muted">Adds Red Falcons to your home screen with the crest. It opens full-screen like a real app.</p></div>
      <div class="install-row"><img src="img/icon-192.png" alt="" class="install-icon">${body}</div>
    </section>`;
  }

  // ---------- helpers ----------
  function esc(s) {
    return String(s == null ? '' : s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  }
  // The admin edits the live ledger only; while an archived season is open everything is read-only.
  const realAdmin = () => !!(session && session.admin && session.token);
  const isAdmin = () => !archive && realAdmin();
  // Viewer access "own": viewers see only their own balance and payments.
  const ownOnly = () => !realAdmin() && state && state.settings.viewerAccess === 'own';
  // An approved manager (a viewer the admin trusts) may set the playing XI of matches, and nothing else.
  const isManager = () => !archive && !!(session && session.managerToken);
  const canPickXi = d => !!d && d.type === 'match' && (isAdmin() || (isManager() && !d.finished));
  // Plain viewers see a match only once the admin has finished it. A manager also sees open matches (they
  // set the XI there); test matches are the admin's alone.
  // The admin and managers write announcements; everyone else reads them.
  const isStaff = () => realAdmin() || !!(session && session.managerToken);
  const seesOpenMatches = () => realAdmin() || !!(session && session.managerToken);
  const matchVisible = m => m.test ? realAdmin() : (m.finished || seesOpenMatches());
  // Stats and counts follow the stricter rule: only the admin sees anything from a match before it is finished.
  const statsMatchVisible = m => realAdmin() || (!m.test && m.finished);
  const daysVisible = () => realAdmin() || !!(session && session.managerToken) || (!!state && state.settings.daysVisibility === 'all');
  const statsVisible = () => realAdmin() || (!!state && state.settings.statsVisibility === 'all');
  const seesMoneyOf = p => !ownOnly() || (myPlayer() || {}).id === p.id;
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
  const DICE_ICON = icon('<rect x="3" y="3" width="18" height="18" rx="3"/><circle cx="8" cy="8" r="1.2" fill="currentColor"/><circle cx="16" cy="16" r="1.2" fill="currentColor"/><circle cx="12" cy="12" r="1.2" fill="currentColor"/><circle cx="16" cy="8" r="1.2" fill="currentColor"/><circle cx="8" cy="16" r="1.2" fill="currentColor"/>', 18);
  const BELL_ICON = icon('<path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.73 21a2 2 0 0 1-3.46 0"/>', 18);
  const COPY_ICON = icon('<rect x="9" y="9" width="13" height="13" rx="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>', 18);
  const WALLET_ICON = icon('<rect x="2" y="5" width="20" height="14" rx="2"/><path d="M2 10h20"/><path d="M16 15h2"/>', 18);
  const PERSON_PATH = '<path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/>';
  const BOWL_STYLES = ['', 'Fast', 'Medium-fast', 'Medium', 'Off spin', 'Leg spin', 'Left-arm spin'];
  const WA_ICON = icon('<path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z"/>', 18);

  // Pre-fills the payment account for ledgers saved before it was a setting.
  const DEFAULT_PAY_TO = { payBank: 'NayaPay', payTitle: 'Ehtisham Badar', payAccount: '03084250469' };

  function emptyState() {
    return {
      players: [], trainings: [{ id: 't1', desc: '' }], expenses: {}, tournament: { name: '', fee: '', advance: '', matchCount: 5, payments: [] }, twelfth: {}, battingOrder: {}, matches: [], stats: {},
      settings: { currency: 'Rs ', maxPerMatch: 11, roundUp: false, ...DEFAULT_PAY_TO }
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
        payments: paymentsFrom(id, p), phone: p.phone == null ? '' : String(p.phone),
        battingHand: p.battingHand === 'left' || p.battingHand === 'right' ? p.battingHand : '',
        bowlingArm: p.bowlingArm === 'left' || p.bowlingArm === 'right' ? p.bowlingArm : '',
        bowlingStyle: p.bowlingStyle || '', keeper: p.keeper === true, captain: p.captain === true, viceCaptain: p.viceCaptain === true,
        jersey: p.jersey == null ? '' : String(p.jersey).replace(/\D/g, '').slice(0, 3), photoAt: p.photoAt || '', jerseyName: p.jerseyName || '', profileAt: p.profileAt || ''
      };
    });
    const ids = new Set(players.map(p => p.id));
    const expenses = expensesFrom(d);
    Object.values(expenses).forEach(list => list.forEach(e => { if (!ids.has(e.paidBy)) e.paidBy = ''; }));
    // Matches added so far. Files from before matches were added one by one had five fixed ones
    // (m1–m5); keep those that hold any attendance, expenses or 12th man.
    let matches;
    if (Array.isArray(d.matches)) {
      matches = d.matches.filter(m => m && m.id).map(m => ({ id: m.id, date: m.date || '', opponent: m.opponent || '', venue: m.venue || '', time: /^([01]\d|2[0-3]):[0-5]\d$/.test(m.time || '') ? m.time : '', test: m.test === true, finished: m.test !== true && m.finished === true, announced: m.announced === true, result: ['win', 'loss', 'tie'].includes(m.result) ? m.result : '' }));
    } else {
      const used = id => players.some(p => p.att[id]) || (expenses[id] || []).length || (d.twelfth || {})[id];
      // These were charged as soon as they were marked, so they count as finished.
      matches = ['m1', 'm2', 'm3', 'm4', 'm5'].filter(used).map(id => ({ id, date: '', opponent: '', venue: '', finished: true }));
    }
    const isMatch = id => matches.some(m => m.id === id);
    // match id -> the 12th man's player id; never someone also playing that match
    const twelfth = {};
    Object.entries(d.twelfth || {}).forEach(([match, id]) => {
      const p = players.find(x => x.id === id);
      if (isMatch(match) && p && !p.att[match]) twelfth[match] = id;
    });
    // match id -> player ids in batting order; only players playing that match, each once
    const battingOrder = {};
    Object.entries(d.battingOrder || {}).forEach(([match, list]) => {
      if (!isMatch(match) || !Array.isArray(list)) return;
      const kept = [...new Set(list)].filter(id => players.some(p => p.id === id && p.att[match]));
      if (kept.length) battingOrder[match] = kept;
    });
    // match id -> player id -> stats (admin only); keep known matches and players
    const stats = {};
    Object.entries(d.stats || {}).forEach(([match, byPlayer]) => {
      if (!isMatch(match) || !byPlayer) return;
      Object.entries(byPlayer).forEach(([pid, e]) => {
        if (!e || !players.some(p => p.id === pid)) return;
        (stats[match] = stats[match] || {})[pid] = { ...C.statOf({ stats: { m: { p: e } } }, 'm', 'p') };
      });
    });
    return {
      players,
      matches,
      stats,
      twelfth,
      battingOrder,
      trainings: d.trainings && d.trainings.length ? d.trainings.map(t => ({ id: t.id, desc: t.desc || '' })) : [{ id: 't1', desc: '' }],
      expenses,
      tournament: {
        name: (d.tournament && d.tournament.name) || '',
        fee: d.tournament && d.tournament.fee != null ? String(d.tournament.fee) : '',
        advance: d.tournament && d.tournament.advance != null ? String(d.tournament.advance) : '',
        matchCount: d.tournament && d.tournament.matchCount > 0 ? d.tournament.matchCount : 5,
        payments: paymentsFrom(null, { payments: (d.tournament && d.tournament.payments) || [] })
      },
      settings: {
        currency: CURRENCIES.includes(s.currency) ? s.currency : 'Rs ',
        maxPerMatch: s.maxPerMatch > 0 ? s.maxPerMatch : 11,
        roundUp: !!s.roundUp,
        payBank: s.payBank != null ? s.payBank : DEFAULT_PAY_TO.payBank,
        payTitle: s.payTitle != null ? s.payTitle : DEFAULT_PAY_TO.payTitle,
        payAccount: s.payAccount != null ? s.payAccount : DEFAULT_PAY_TO.payAccount,
        viewerAccess: s.viewerAccess === 'own' ? 'own' : 'all',
        statsVisibility: s.statsVisibility === 'all' ? 'all' : 'admin',
        daysVisibility: s.daysVisibility === 'all' ? 'all' : 'admin',
        showNextMatch: s.showNextMatch !== false,
        showFixtures: s.showFixtures === true,
        adminShowFixtures: s.adminShowFixtures !== false
      }
    };
  }

  function comparable(d) {
    const { players, trainings, matches, expenses, tournament, settings, twelfth, battingOrder, stats } = d;
    return JSON.stringify({ players, trainings, matches, expenses, tournament, settings, twelfth, battingOrder, stats });
  }

  // ---------- language ----------
  // The button shows the other language; switching reloads the page (see i18n.js).
  document.querySelectorAll('[data-lang-toggle]').forEach(b => {
    b.textContent = window.I18N && I18N.lang === 'ur' ? (b.classList.contains('lang-btn') ? 'EN' : 'English') : 'اردو';
    b.addEventListener('click', () => I18N.toggle());
  });

  // ---------- API ----------
  async function api(method, path, body, opts = {}) {
    const headers = { 'Content-Type': 'application/json' };
    if (session && session.token) headers['X-Admin-Token'] = session.token;
    if (session && session.managerToken) headers['X-Manager-Token'] = session.managerToken;
    if (mgr && mgr.token) headers['X-Request-Token'] = mgr.token;
    const device = readStore(DEVICE_KEY);
    if (device) headers['X-Device'] = device;
    else {
      // Which player this viewer is, so private ledgers send them their own payments.
      const me = readStore(ME_KEY);
      if (me && me !== NOT_ME) headers['X-Viewer'] = me;
      else if (!me && state && session) {
        // Not chosen on this device, but the name they signed in with matches one player: that's who they are.
        const guess = myPlayer();
        if (guess) headers['X-Viewer'] = guess.id;
      }
    }
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
    editedAt = Date.now();
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
        tab = 'summary';
        setSaveStatus('');
        toast('Admin access is no longer valid. You are now view-only.');
        render();
        return;
      }
      setSaveStatus('error');
      saveTimer = setTimeout(flushSave, 4000);
    }
  }

  // ---------- lineup requests (manager) ----------
  // A manager's edits stay on this device until sent; the ledger changes only when the admin approves.
  function touchXi(dayId) {
    if (isManager()) { xiDirty[dayId] = true; render(); } else commit();
  }

  // Puts one match's lineup back to what the ledger says (after sending, or discarding).
  async function revertMatch(matchId) {
    const live = await fetchLedger();
    state.players.forEach(p => {
      const lp = live.players.find(x => x.id === p.id);
      if (lp && lp.att[matchId]) p.att[matchId] = true; else delete p.att[matchId];
    });
    if (live.twelfth[matchId]) state.twelfth[matchId] = live.twelfth[matchId]; else delete state.twelfth[matchId];
    if (live.battingOrder[matchId]) state.battingOrder[matchId] = live.battingOrder[matchId]; else delete state.battingOrder[matchId];
    render();
  }

  async function loadMyLineups() {
    if (!isManager()) return;
    let next;
    try { next = await api('GET', '/api/manager/lineups'); } catch { return; }
    const before = new Map(myLineups.map(x => [x.id, x.status]));
    next.forEach(x => {
      if (before.get(x.id) === 'pending' && x.status !== 'pending') {
        const label = (C.matches(state).find(m => m.id === x.matchId) || {}).label || 'the match';
        toast(x.status === 'approved' ? `The admin approved your lineup for ${label}.` : `The admin declined your lineup for ${label}${x.note ? `: ${x.note}` : '.'}`);
      }
    });
    const changed = JSON.stringify(next) !== JSON.stringify(myLineups);
    myLineups = next;
    if (changed) { try { state = Object.keys(xiDirty).length ? state : await fetchLedger(); } catch { /* ignore */ } render(); }
  }

  const lastLineup = matchId => myLineups.find(x => x.matchId === matchId);

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

  // After a viewer says who they are, a private ledger has to be fetched again with their id.
  async function refetchForViewer() {
    render();
    if (!ownOnly()) return;
    try { state = await fetchLedger(); render(); } catch { /* the next poll will catch up */ }
  }

  // ---------- season archive ----------
  // An ISO instant as this device's local YYYY-MM-DD.
  const localDay = iso => new Date(iso).toLocaleDateString('en-CA');

  function seasonName() {
    const month = new Date().toLocaleDateString('en-GB', { month: 'short', year: 'numeric' });
    return `${state.tournament.name.trim() || 'Season'} · ${month}`;
  }

  async function loadSeasons() {
    try { seasons = await api('GET', '/api/seasons'); } catch { seasons = seasons || []; }
    if (tab === 'settings') render();
  }

  // Saves the live ledger as a season; saves pending edits first so the copy is up to date.
  async function archiveSeason(name) {
    if (saveTimer) { clearTimeout(saveTimer); await flushSave(); }
    const info = await api('POST', '/api/seasons', { name });
    await loadSeasons();
    return info;
  }

  async function openSeason(id) {
    const info = (seasons || []).find(x => x.id === id);
    if (!info) return;
    try {
      const data = normalize(await api('GET', '/api/seasons/' + encodeURIComponent(id)));
      if (!archive) liveState = state;
      archive = info;
      state = data;
      tab = 'summary';
      statsSel = 'league';
      sheet = null;
      render();
      animateView();
      window.scrollTo({ top: 0 });
    } catch {
      toast('Could not open that season.');
    }
  }

  async function leaveSeason() {
    archive = null;
    state = liveState || state;
    liveState = null;
    tab = 'settings';
    sheet = null;
    render();
    try { state = await fetchLedger(); render(); } catch { /* keep what we had */ }
  }

  function archiveBar() {
    if (!archive) return '';
    return `<div class="archive-bar">
      ${icon('<path d="M21 8v13H3V8"/><path d="M1 3h22v5H1z"/><path d="M10 12h4"/>', 20)}
      <span><strong>${esc(archive.name)}</strong><small>Archived season · ${esc(formatDate(localDay(archive.archivedAt)))} · read-only</small></span>
      <button class="btn btn-gold" data-action="leave-season">Back to current season</button>
    </div>`;
  }

  function seasonPanel() {
    const list = seasons || [];
    const admin = isAdmin();
    return `<section class="panel season-panel">
      <div class="panel-head"><h3>Season archive</h3><p class="muted">Past seasons, kept read-only. ${admin ? 'Starting a new series archives the current one first.' : ''}</p></div>
      ${seasons === null ? '<p class="muted">Loading…</p>' : list.length ? `<ul class="season-list">${list.map(x => `<li>
          <span class="season-icon">${icon('<path d="M21 8v13H3V8"/><path d="M1 3h22v5H1z"/><path d="M10 12h4"/>', 18)}</span>
          <span class="season-text"><strong>${esc(x.name)}</strong><small>Archived ${esc(formatDate(localDay(x.archivedAt)))}</small></span>
          <button class="btn btn-ghost small" data-action="open-season" data-id="${esc(x.id)}">View</button>
          ${admin ? `<button class="icon-btn danger" data-action="delete-season" data-id="${esc(x.id)}" aria-label="Delete ${esc(x.name)}">${X_ICON}</button>` : ''}
        </li>`).join('')}</ul>` : '<p class="muted season-empty">No archived seasons yet.</p>'}
      ${admin ? `<div class="season-new">
        <input class="field" type="text" maxlength="120" value="${esc(seasonDraft === null ? seasonName() : seasonDraft)}" data-input="season-name" data-focus="season-name" aria-label="Season name">
        <button class="btn btn-ghost" data-action="archive-season">Archive current season</button>
      </div>` : ''}
    </section>`;
  }

  // ---------- manager access ----------
  function setMgr(next) {
    mgr = next;
    writeStore(MGR_KEY, next);
  }

  // A signed-in manager whose access the admin ended (or whose phrase was changed) goes back to view-only.
  function dropManager() {
    session = { name: session.name, admin: false, token: null };
    writeStore(SESSION_KEY, session);
    tab = 'summary';
    saveTab();
    toast('Your manager access was ended. You are now view-only.');
    render();
  }

  // Manager: is the session still valid? Requester: was the request approved or declined?
  let myManagerId = '';
  async function refreshManager() {
    if (session && session.managerToken) {
      let ok = true;
      try { const r = await api('GET', '/api/manager/session'); ok = r.manager; myManagerId = r.id || ''; } catch { return; }
      if (!ok) dropManager();
      return;
    }
    if (!mgr || !mgr.token) return;
    let status, note = '';
    try { const r = await api('GET', '/api/manager/status'); status = r.status; note = r.note || ''; } catch { return; }
    if (status === mgr.status) return;
    if (status === 'none') {
      setMgr(null);
      toast('Your manager request was removed.');
    } else if (status === 'declined') {
      setMgr({ ...mgr, status, note });
      toast(`Your manager request was declined${note ? `: ${note}` : '.'}`);
    } else {
      setMgr({ ...mgr, status });
      toast('Approved! Switch user and enter your phrase on the start screen to sign in as manager.');
    }
    renderIfSettings();
  }

  async function loadInbox() {
    if (!realAdmin()) return;
    let next;
    try { next = await api('GET', '/api/manager/inbox'); } catch { return; }
    if (JSON.stringify(next) === JSON.stringify(inbox)) return;
    const was = inbox ? inboxCount() : null;
    inbox = next;
    mgrList = next.access;
    if (was !== null && inboxCount() > was) toast('New request waiting — tap the bell.');
    render();
  }

  // Things players did that the admin should know about (like saving their profile).
  let adminUpdates = null;
  const ADMIN_SEEN_KEY = 'rf.adminSeen';
  const adminUnread = () => {
    if (!adminUpdates) return 0;
    const seen = readStore(ADMIN_SEEN_KEY);
    return seen == null ? 0 : adminUpdates.filter(n => n.createdAt > seen).length;
  };
  const markAdminSeen = () => { if (adminUpdates) writeStore(ADMIN_SEEN_KEY, adminUpdates.length ? adminUpdates[0].createdAt : ''); };
  async function loadAdminUpdates() {
    if (!realAdmin()) return;
    let next;
    try { next = await api('GET', '/api/notifications/admin'); } catch { return; }
    if (JSON.stringify(next) === JSON.stringify(adminUpdates)) return;
    const first = adminUpdates === null;
    const before = adminUpdates && adminUpdates.length ? adminUpdates[0].createdAt : '';
    adminUpdates = next;
    if (readStore(ADMIN_SEEN_KEY) == null) markAdminSeen();
    if (sheet && sheet.type === 'inbox') markAdminSeen();
    else if (!first && next.length && next[0].createdAt > before) toast(`${next[0].title}: ${next[0].body}`);
    render();
  }

  const inboxCount = () => inbox ? inbox.access.filter(m => m.status === 'pending').length + inbox.lineups.length : 0;

  function managerPanel() {
    const waiting = (mgrList || []).filter(m => m.status === 'pending').length;
    const active = (mgrList || []).filter(m => m.status === 'approved');
    const row = m => {
      const draft = m.id in mgrDraft.edits ? mgrDraft.edits[m.id] : m.phrase;
      return `<li class="mgr-row">
        <span class="mgr-who"><strong>${esc(m.name)}</strong>
          <small>${esc(m.label)} · approved ${esc(formatDate(localDay(m.decidedAt || m.createdAt)))}</small></span>
        <span class="mgr-phrase">
          <input class="field" type="text" maxlength="60" value="${esc(draft)}" data-input="mgr-edit" data-id="${esc(m.id)}" data-focus="mgrp-${esc(m.id)}" aria-label="Phrase for ${esc(m.name)}" spellcheck="false" autocomplete="off">
          ${draft !== m.phrase ? `<button class="btn btn-gold small" data-action="mgr-save-phrase" data-id="${esc(m.id)}">Save phrase</button>` : ''}
        </span>
        <span class="mgr-btns"><button class="btn btn-ghost small" data-action="mgr-remove" data-id="${esc(m.id)}" data-kind="revoke">Revoke</button></span>
      </li>`;
    };
    return `<section class="panel">
      <div class="panel-head"><h3>App managers</h3><p class="muted">A viewer asks for manager access with a phrase of their own. Once you approve, typing that phrase on the start screen signs them in (no two-factor). A manager can propose the playing XI and 12th man of matches that aren’t finished; nothing changes until you approve it. Change a phrase to sign that manager out, or revoke them, whenever you like.</p></div>
      ${waiting ? `<p class="mgr-waiting">${icon('<path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.7 21a2 2 0 0 1-3.4 0"/>', 16)} ${waiting} request${waiting === 1 ? '' : 's'} waiting. <button class="chip" data-action="open-inbox">Open notifications</button></p>` : ''}
      ${mgrList === null ? '<p class="muted">Loading…</p>' : !active.length ? '<p class="muted season-empty">No managers yet.</p>' : `<ul class="mgr-list">${active.map(row).join('')}</ul>`}
    </section>`;
  }

  // ---------- announcements (admin and managers) ----------
  async function loadAnnouncements() {
    if (!isStaff()) return;
    let next;
    try { next = await api('GET', '/api/announcements'); } catch { return; }
    if (JSON.stringify(next) === JSON.stringify(announcements)) return;
    announcements = next;
    if (tab === 'announce') render();
  }

  // "Match 3 vs Lahore Lions on Sat, 24 Oct 2026 at Model Town"
  const matchLine = m => `${m.label}${m.opponent ? ` vs ${m.opponent}` : ''}${m.date ? ` on ${formatMatchDate(m.date)}${m.time ? ` at ${fmtClock(m.time)}` : ''}` : ''}${m.venue ? ` (${m.venue})` : ''}`;

  // A local date-time (for the picker) the given number of days before the match, or '' when that is already past.
  function suggestAt(m, daysBefore, hour) {
    if (!m || !m.date) return '';
    const d = new Date(`${m.date}T${String(hour).padStart(2, '0')}:00`);
    d.setDate(d.getDate() - daysBefore);
    if (d.getTime() <= Date.now()) return '';
    const pad = n => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
  }

  const ANN_TEMPLATES = [
    { id: 'reminder', label: 'Match reminder', title: 'Match reminder', make: m => m ? `Reminder: ${matchLine(m)}. Please be ready and on time.` : 'Reminder: we have a match coming up. Please be ready and on time.', at: m => suggestAt(m, 0, 8) },
    { id: 'before', label: 'Day before', title: 'Match tomorrow', make: m => m ? `${matchLine(m)} is tomorrow. Get some rest and bring your kit.` : 'We have a match tomorrow. Get some rest and bring your kit.', at: m => suggestAt(m, 1, 18) },
    { id: 'report', label: 'Report time', title: 'Report time', make: m => m ? `Please report for ${m.label}${m.venue ? ` at ${m.venue}` : ''} by __ (time).${m.time ? ` Kick-off is ${fmtClock(m.time)}.` : ''}` : 'Please report at __ (time).', at: () => '' },
    { id: 'xi', label: 'XI announced', title: 'Playing XI announced', make: m => m ? `The playing XI for ${m.label}${m.opponent ? ` vs ${m.opponent}` : ''} is out. Check the app.` : 'The playing XI is out. Check the app.', at: () => '' },
    { id: 'profile', label: 'Update profile', title: 'Update your profile', make: () => 'Please update your profile: add your photo, jersey name and number, and your batting and bowling details. Tap here to fill it in.', at: () => '', action: 'profile' },
    { id: 'update', label: 'Match update', title: 'Match update', make: m => m ? `Update for ${m.label}: ` : 'Match update: ', at: () => '' }
  ];

  function viewAnnounce() {
    const d = annDraft;
    const realMatches = C.matches(state).filter(m => !m.test);
    const when = a => a.status === 'scheduled' ? `for ${esc(new Date(a.sendAt).toLocaleString('en-GB', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }))}` : `${ago(a.sendAt || a.createdAt)}`;
    const item = a => {
      const m = a.matchId ? realMatches.find(x => x.id === a.matchId) : null;
      const mine = realAdmin() || a.author === session.name;
      return `<article class="note-card ${a.status === 'scheduled' ? 'is-scheduled' : ''}">
        <div class="note-head"><strong>${esc(a.title)}</strong>
          <small><span class="ann-status ${a.status}">${a.status === 'scheduled' ? 'Scheduled' : 'Sent'}</span> ${when(a)} · by ${esc(a.author || 'Admin')}</small></div>
        <p class="note-detail">${esc(a.body)}</p>
        ${a.action === 'profile' ? `<span class="ann-chip">Profile request · ${state.players.filter(pl => pl.profileAt && pl.profileAt >= a.createdAt).length} of ${state.players.length} done</span>` : ''}
        ${m ? `<span class="ann-chip">${m.label}${m.opponent ? ` · vs ${esc(m.opponent)}` : ''}</span>` : ''}
        <div class="note-actions">
          <button class="btn btn-ghost small" data-action="ann-duplicate" data-id="${esc(a.id)}">${COPY_ICON} Duplicate</button>
          ${mine ? `<button class="btn btn-ghost small" data-action="ann-delete" data-id="${esc(a.id)}">Delete</button>` : ''}
        </div></article>`;
    };
    const openPolls = engage ? engage.polls.filter(x => !x.closed).length : 0;
    const seg = `<div class="seg-tabs" role="tablist">${[['ann', 'Announcements', 0], ['poll', 'Polls', openPolls]].map(([k, l, n]) =>
      `<button class="seg-btn ${annSeg === k ? 'on' : ''}" role="tab" aria-selected="${annSeg === k}" data-action="ann-seg" data-seg="${k}">${l}${n ? ` <span>${n}</span>` : ''}</button>`).join('')}</div>`;
    return `
      ${pageHead(annSeg === 'poll' ? 'Polls' : 'Announcements', annSeg === 'poll' ? 'Ask the team a quick question and see who answered.' : 'Write a message for the team. It shows in everyone’s notifications, and goes to the phones that have notifications on.')}
      ${seg}
      ${annSeg === 'poll' ? pollsAdmin() : `<section class="panel ann-composer" id="ann-composer">
        <div class="panel-head"><h3>New announcement</h3><p class="muted">Start from a template, or write your own. Pick a match to fill in its details.</p></div>
        <label class="ann-field"><span>About match <small class="muted">(optional)</small></span>
          <select class="field select" data-input="ann-match" data-focus="ann-match">
            <option value="">No particular match</option>
            ${realMatches.map(m => `<option value="${esc(m.id)}" ${d.matchId === m.id ? 'selected' : ''}>${esc(matchLine(m))}</option>`).join('')}
          </select></label>
        <div class="ann-templates"><span class="muted">Templates</span>
          ${ANN_TEMPLATES.map(x => `<button class="chip" data-action="ann-template" data-t="${x.id}">${x.label}</button>`).join('')}</div>
        <label class="ann-field"><span>Title</span>
          <input class="field" type="text" maxlength="80" value="${esc(d.title)}" placeholder="e.g. Match reminder" data-input="ann-title" data-focus="ann-title"></label>
        <label class="ann-field"><span>Message <small class="muted" id="ann-count">${d.body.length}/300</small></span>
          <textarea class="field" rows="4" maxlength="300" placeholder="What should the team know?" data-input="ann-body" data-focus="ann-body">${esc(d.body)}</textarea></label>
        ${d.action === 'profile' ? `<p class="ann-action-note">${icon(PERSON_PATH, 16)} <span>Players who tap this open the profile editor (photo, jersey, batting and bowling). You get a notification as each one saves.</span>
          <button class="chip" data-action="ann-action-clear" title="Send as a plain message">Plain message</button></p>` : ''}
        <div class="ann-when">
          <button class="tog ${d.later ? '' : 'on'}" data-action="ann-mode" data-later="0" aria-pressed="${!d.later}">Send now</button>
          <button class="tog ${d.later ? 'on' : ''}" data-action="ann-mode" data-later="1" aria-pressed="${d.later}">Send later</button>
          ${d.later ? `<input class="field" type="datetime-local" value="${esc(d.at)}" data-input="ann-at" data-focus="ann-at" aria-label="Send at">` : ''}
        </div>
        <div class="ann-actions">
          <button class="btn btn-gold" data-action="ann-send">${icon('<path d="M22 2 11 13M22 2l-7 20-4-9-9-4z"/>', 18)} ${d.later ? 'Schedule' : 'Send'}</button>
          <button class="btn btn-ghost" data-action="ann-clear">Clear</button>
        </div>
      </section>
      <section class="panel">
        <div class="panel-head"><h3>Sent</h3><p class="muted">Duplicate one to reuse it, for example for the next match.</p></div>
        ${announcements === null ? '<p class="muted">Loading…</p>' : announcements.length ? announcements.map(item).join('') : '<p class="muted season-empty">Nothing sent yet.</p>'}
      </section>`}`;
  }

  const realMatchesById = id => id ? C.matches(state).find(m => m.id === id && !m.test) : null;

  // ---------- notifications (viewers and managers) ----------
  const newestFeed = () => (feed && feed.length ? feed[0].createdAt : '');
  const unreadCount = () => {
    if (!feed) return 0;
    const seen = readStore(FEED_SEEN_KEY);
    return seen == null ? 0 : feed.filter(n => n.createdAt > seen).length;
  };
  const markFeedSeen = () => { if (feed) writeStore(FEED_SEEN_KEY, newestFeed()); };

  // Fetches what the admin has announced; a new one pops up as a toast while the app is open.
  async function loadFeed() {
    if (realAdmin()) return;
    let next;
    try { next = await api('GET', '/api/notifications'); } catch { return; }
    if (JSON.stringify(next) === JSON.stringify(feed)) return;
    const first = feed === null;
    const before = feed && feed.length ? feed[0].createdAt : '';
    feed = next;
    // A device that has never looked starts from now, so old announcements don't pile up as unread.
    if (readStore(FEED_SEEN_KEY) == null) markFeedSeen();
    if (sheet && sheet.type === 'feed') markFeedSeen();
    else if (!first && next.length && next[0].createdAt > before) toast(`${next[0].title}: ${next[0].body}`);
    render();
  }

  function sheetFeed() {
    const items = feed || [];
    return `<header class="sheet-head">
        <div><span class="day-kind">Updates</span><h2>Notifications</h2>
          <p class="muted">Announcements from the team, the Playing XI, and expenses the admin records.</p></div>
        <button class="icon-btn" data-action="close-sheet" aria-label="Close">${X_ICON}</button>
      </header>
      <div class="sheet-body">${feed === null ? '<p class="muted center">Loading…</p>' : items.length
        ? items.map(n => {
          const m = n.matchId ? C.matches(state).find(x => x.id === n.matchId) : null;
          return `<article class="note-card ${n.kind === 'announcement' || n.kind === 'notice' ? 'is-ann' : ''}"><div class="note-head"><strong>${esc(n.title)}</strong>
            <small>${n.kind === 'announcement' && n.author ? `${esc(n.author)} · ` : ''}${ago(n.createdAt)}</small></div>
            <p class="note-detail">${esc(n.body)}</p>
            ${n.action === 'profile' ? `<button class="btn btn-gold small" data-action="open-profile-form">${icon(PERSON_PATH, 16)} Update my profile</button>` : ''}
            ${m && matchVisible(m) ? `<span class="ann-chip">${m.label}${m.opponent ? ` · vs ${esc(m.opponent)}` : ''}</span>` : ''}</article>`;
        }).join('')
        : '<p class="muted center">Nothing new yet. Announcements and new expenses will show up here.</p>'}</div>`;
  }

  // ---------- notifications (admin) ----------
  const ago = iso => {
    const s = Math.max(0, Math.round((Date.now() - new Date(iso).getTime()) / 1000));
    if (s < 60) return 'just now';
    if (s < 3600) return `${Math.floor(s / 60)} min ago`;
    if (s < 86400) return `${Math.floor(s / 3600)} h ago`;
    return `${Math.floor(s / 86400)} d ago`;
  };

  function sheetInbox() {
    const items = [
      ...(inbox ? inbox.access.filter(m => m.status === 'pending').map(m => ({ kind: 'access', at: m.createdAt, m })) : []),
      ...(inbox ? inbox.lineups.map(l => ({ kind: 'lineup', at: l.createdAt, l })) : [])
    ].sort((a, b) => a.at.localeCompare(b.at));
    const card = it => {
      const id = it.kind === 'access' ? it.m.id : it.l.id;
      const key = `${it.kind}:${id}`;
      let title, sub, detail = '';
      if (it.kind === 'access') {
        title = `${esc(it.m.name)} wants manager access`;
        sub = `${esc(it.m.label)} · ${ago(it.at)}`;
        detail = `<p class="note-detail">Their phrase: <strong>${esc(it.m.phrase)}</strong>. As manager they could propose the playing XI; you would still approve each change.</p>`;
      } else {
        const l = it.l;
        const match = C.matches(state).find(x => x.id === l.matchId);
        const now = state.players.filter(p => p.att[l.matchId]).map(p => p.id);
        const add = l.playing.filter(x => !now.includes(x)).map(playerName);
        const drop = now.filter(x => !l.playing.includes(x)).map(playerName);
        title = `${esc(l.managerName)} proposes the lineup for ${esc(match ? match.label : 'a match')}`;
        sub = ago(it.at);
        detail = `<p class="note-detail"><strong>Playing (${l.playing.length}):</strong> ${l.playing.map(x => esc(playerName(x))).join(', ') || '—'}</p>
          <p class="note-detail"><strong>12th man:</strong> ${l.twelfth ? esc(playerName(l.twelfth)) : '—'}</p>
          ${l.batting && l.batting.length ? `<p class="note-detail"><strong>Batting order:</strong> ${l.batting.map((x, i) => `${i + 1}. ${esc(playerName(x))}`).join(' · ')}</p>` : ''}
          <p class="note-detail muted">${add.length || drop.length ? [...add.map(n => `+ ${esc(n)}`), ...drop.map(n => `− ${esc(n)}`)].join(' · ') : 'Same XI as now.'}</p>`;
      }
      const declining = key in declineDraft;
      return `<article class="note-card">
        <div class="note-head"><strong>${title}</strong><small>${sub}</small></div>
        ${detail}
        ${declining ? `<label class="note-label">Note for ${esc(it.kind === 'access' ? it.m.name : it.l.managerName)} (optional)
            <textarea class="field" rows="2" maxlength="300" data-input="decline-note" data-key="${esc(key)}" data-focus="dn-${esc(key)}" placeholder="Why it was declined">${esc(declineDraft[key])}</textarea></label>
          <div class="note-actions">
            <button class="btn btn-danger small" data-action="inbox-decline-send" data-kind="${it.kind}" data-id="${esc(id)}">Send decline</button>
            <button class="btn btn-ghost small" data-action="inbox-decline-cancel" data-key="${esc(key)}">Cancel</button>
          </div>`
          : `<div class="note-actions">
            <button class="btn btn-gold small" data-action="inbox-approve" data-kind="${it.kind}" data-id="${esc(id)}">Approve</button>
            <button class="btn btn-ghost small" data-action="inbox-decline-open" data-key="${esc(key)}">Decline</button>
          </div>`}
      </article>`;
    };
    return `<header class="sheet-head">
        <div><span class="day-kind">Admin</span><h2>Notifications</h2>
          <p class="muted">Manager requests and lineup changes wait here for your decision, and you see when players update their profile.</p></div>
        <button class="icon-btn" data-action="close-sheet" aria-label="Close">${X_ICON}</button>
      </header>
      <div class="sheet-body">${inbox === null ? '<p class="muted center">Loading…</p>' : items.length ? items.map(card).join('') : (adminUpdates && adminUpdates.length ? '' : '<p class="muted center">Nothing waiting. You’re all caught up.</p>')}
        ${adminUpdates && adminUpdates.length ? `<h3 class="sub ${items.length ? '' : 'first'}">Player updates</h3>${adminUpdates.map(n => `<article class="note-card is-ann"><div class="note-head"><strong>${esc(n.title)}</strong><small>${ago(n.createdAt)}</small></div><p class="note-detail">${esc(n.body)}</p></article>`).join('')}` : ''}</div>`;
  }

  function managerRequestPanel() {
    let body;
    if (isManager()) {
      body = `<p class="muted">You’re signed in as an app manager. Open a match on the Days tab to set the playing XI and 12th man. Everything else stays view-only.</p>`;
    } else if (mgr && mgr.status === 'approved') {
      body = `<p class="muted">Approved! Switch user, then type your phrase on the start screen to sign in as manager.</p>`;
    } else if (mgr && mgr.status === 'declined') {
      body = `<p class="muted">The admin declined your request${mgr.note ? `: “${esc(mgr.note)}”` : '.'}</p>
        <button class="btn btn-ghost" data-action="mgr-cancel">Ask again</button>`;
    } else if (mgr && mgr.status === 'pending') {
      body = `<p class="muted">Request sent. The admin has to approve it — this page updates by itself. Your phrase is the one you chose.</p>
        <button class="btn btn-ghost" data-action="mgr-cancel">Cancel request</button>`;
    } else {
      body = `<p class="muted">Pick a phrase only you know (at least 8 characters). If the admin approves, typing it on the start screen signs you in as an app manager, who can set the playing XI. The admin can see it, change it, or end your access at any time.</p>
        <div class="mgr-ask">
          <input class="field" type="text" maxlength="60" value="${esc(mgrDraft.phrase)}" placeholder="Your phrase" data-input="mgr-phrase" data-focus="mgr-phrase" aria-label="Your phrase" spellcheck="false" autocomplete="off">
          <button class="btn btn-gold" data-action="mgr-request">Request manager access</button>
        </div>`;
    }
    return `<section class="panel">${body}</section>`;
  }

  // ---------- polling (viewers) ----------
  function startPolling() {
    clearInterval(pollTimer);
    pollTimer = setInterval(async () => {
      if (archive || document.visibilityState !== 'visible') return;
      loadGallery();
      loadEngage();
      if (isStaff() && tab === 'announce') loadAnnouncements();
      if (isAdmin()) { loadInbox(); loadAdminUpdates(); syncProfiles(); return; }
      await refreshManager();
      if (isManager()) await loadMyLineups();
      await loadFeed();
      if (Object.keys(xiDirty).length) return; // a manager's unsent lineup must not be overwritten
      try {
        const d = await fetchLedger();
        if (comparable(d) !== comparable(state)) { state = d; render(); }
      } catch { /* try again next tick */ }
    }, POLL_MS);
  }

  // ---------- loading screen ----------
  let loaderSlow = null;
  function showLoader() {
    const el = $('#loader');
    el.hidden = false;
    el.classList.remove('done');
    $('#loader-text').textContent = 'Loading the ledger…';
    clearTimeout(loaderSlow);
    // Railway can take a few seconds to wake after a quiet spell.
    loaderSlow = setTimeout(() => { $('#loader-text').textContent = 'Waking up the server…'; }, 4000);
  }
  function hideLoader() {
    clearTimeout(loaderSlow);
    const el = $('#loader');
    if (el.hidden) return;
    el.classList.add('done');
    setTimeout(() => { el.hidden = true; }, 450);
  }

  // ---------- splash / login ----------
  function showSplash() {
    resetView();
    hideLoader();
    hideCodeStep();
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

  // The last tab is remembered per signed-in person, so someone else signing in on this device never
  // lands on the previous person's tab.
  const whoKey = () => !session ? '' : session.admin ? 'admin' : (session.managerToken ? 'manager:' : 'viewer:') + (session.name || '');
  function saveTab() { writeStore(TAB_KEY, { who: whoKey(), tab }); }
  function restoreTab() {
    const saved = readStore(TAB_KEY);
    tab = saved && typeof saved === 'object' && saved.who === whoKey() && saved.tab ? saved.tab : 'summary';
  }

  // A new user starts on Summary with nothing open.
  function resetView() {
    tab = 'summary';
    saveTab();
    sheet = null;
    draftNames = '';
    $('#dialog-root').innerHTML = '';
    window.scrollTo({ top: 0 });
  }

  // A short label for this browser in the admin's list of signed-in / trusted browsers.
  function deviceLabel() {
    const ua = navigator.userAgent;
    const browser = /Edg\//.test(ua) ? 'Edge' : /CriOS|Chrome\//.test(ua) ? 'Chrome' : /FxiOS|Firefox\//.test(ua) ? 'Firefox' : /Safari\//.test(ua) ? 'Safari' : 'Browser';
    const os = /iPhone/.test(ua) ? 'iPhone' : /iPad/.test(ua) ? 'iPad' : /Android/.test(ua) ? 'Android' : /Mac OS X/.test(ua) ? 'Mac' : /Windows/.test(ua) ? 'Windows' : /Linux/.test(ua) ? 'Linux' : '';
    return os ? `${browser} on ${os}` : browser;
  }

  let codeChallenge = null;

  function showCodeStep(challenge) {
    codeChallenge = challenge;
    $('#login-form').hidden = true;
    $('#code-form').hidden = false;
    $('#code-error').textContent = '';
    $('#login-code').value = '';
    setTimeout(() => $('#login-code').focus(), 50);
  }

  function hideCodeStep() {
    codeChallenge = null;
    $('#code-form').hidden = true;
    $('#login-form').hidden = false;
  }

  async function verifyCode(code) {
    const err = $('#code-error');
    err.textContent = '';
    try {
      const r = await api('POST', '/api/login/verify', { challenge: codeChallenge, code, trust: $('#login-trust').checked, label: deviceLabel() });
      if (r.device) writeStore(DEVICE_KEY, r.device);
      session = { name: r.name, admin: r.admin, token: r.token };
      writeStore(SESSION_KEY, session);
      writeStore(ME_KEY, null);
      resetView();
      hideCodeStep();
      if (r.recoveryLeft != null && r.recoveryLeft <= 2) setTimeout(() => toast(`Only ${r.recoveryLeft} recovery code${r.recoveryLeft === 1 ? '' : 's'} left — make new ones in Settings.`), 1500);
      await enterApp();
    } catch (e) {
      err.textContent = e.message || 'That code didn’t work.';
      if (/Too many|expired/i.test(err.textContent)) setTimeout(() => { hideCodeStep(); $('#login-error').textContent = err.textContent; }, 1600);
    }
  }

  async function login(name) {
    const err = $('#login-error');
    err.textContent = '';
    try {
      const r = await api('POST', '/api/login', { name, device: readStore(DEVICE_KEY), label: deviceLabel() });
      if (r.needCode) { showCodeStep(r.challenge); return; }
      if (mgr && mgr.name !== r.name) setMgr(null);
      session = { name: r.name, admin: r.admin, token: r.token };
      if (r.manager && r.managerToken) session.managerToken = r.managerToken;
      writeStore(SESSION_KEY, session);
      writeStore(ME_KEY, null); // a new name may be a different person on this device
      resetView();
      await enterApp();
    } catch (e) {
      err.textContent = 'Could not reach the server. Is it running?';
    }
  }

  async function enterApp() {
    showLoader();
    restoreTab();
    try {
      if (session.token) {
        const s = await api('GET', '/api/session');
        if (!s.admin) {
          // The admin session ended (signed out elsewhere, or two-factor was turned on): sign in again.
          session = null;
          writeStore(SESSION_KEY, null);
          showSplash();
          $('#login-error').textContent = 'Your admin sign-in has ended. Enter the admin key again.';
          return;
        }
      }
      if (session.managerToken) {
        const s = await api('GET', '/api/manager/session');
        if (!s.manager) {
          session = null;
          writeStore(SESSION_KEY, null);
          showSplash();
          $('#login-error').textContent = 'Your manager access has ended. Ask the admin, or enter your name.';
          return;
        }
      }
      state = await fetchLedger();
      // A private ledger only sends a viewer their own payments: once we know who they are, ask again.
      const me = ownOnly() && myPlayer();
      if (me && readStore(ME_KEY) !== me.id) { writeStore(ME_KEY, me.id); state = await fetchLedger(); }
    } catch (e) {
      showSplash();
      $('#login-error').textContent = 'Could not load the ledger. Is the server running?';
      return;
    }
    $('#splash').hidden = true;
    $('#app').hidden = false;
    setSaveStatus('');
    render();
    hideLoader();
    animateView();
    loadSeasons();
    refreshPush();
    loadTfa();
    loadInbox();
    loadAdminUpdates();
    loadFeed();
    loadGallery();
    loadEngage();
    loadAnnouncements();
    refreshManager().then(loadMyLineups);
    startPolling();
  }

  // ---------- rendering ----------
  // Highlights the Settings section in view in the side menu.
  function markSettingsNav() {
    const btns = document.querySelectorAll('.set-nav-btn');
    if (!btns.length) return;
    const offset = window.innerWidth <= 960 ? 150 : 110;
    let current = null;
    document.querySelectorAll('.set-section').forEach(sec => { if (sec.getBoundingClientRect().top - offset <= 0) current = sec.id.replace(/^set-/, ''); });
    if (!current) current = (btns[0].dataset.id);
    btns.forEach(b => b.classList.toggle('on', b.dataset.id === current));
  }
  let navTick = false;
  window.addEventListener('scroll', () => {
    if (navTick) return;
    navTick = true;
    requestAnimationFrame(() => { navTick = false; markSettingsNav(); });
  }, { passive: true });

  // Scrollable areas whose position should survive a re-render (render() rebuilds the page on every change).
  const KEEP_SCROLL = '.sheet-body, .table-wrap, .seg, [data-keep-scroll]';
  function scrollState() {
    const list = {};
    document.querySelectorAll(KEEP_SCROLL).forEach((el, i) => { list[(el.closest('#sheet-root') ? 's' : 'v') + i + el.className] = [el.scrollTop, el.scrollLeft]; });
    return { y: window.scrollY, sheetKey: sheet ? JSON.stringify(sheet) : '', list };
  }
  function restoreScroll(before) {
    const sheetKey = sheet ? JSON.stringify(sheet) : '';
    document.querySelectorAll(KEEP_SCROLL).forEach((el, i) => {
      const inSheet = !!el.closest('#sheet-root');
      if (inSheet && sheetKey !== before.sheetKey) return;      // a different sheet opened: start at its top
      const pos = before.list[(inSheet ? 's' : 'v') + i + el.className];
      if (pos) { el.scrollTop = pos[0]; el.scrollLeft = pos[1]; }
    });
    if (Math.abs(window.scrollY - before.y) > 1) window.scrollTo(0, before.y);
  }

  function render() {
    if (!state) return;
    const scroll = scrollState();
    const active = document.activeElement;
    const focusKey = active && active.dataset ? active.dataset.focus : null;
    let sel = null;
    if (focusKey && typeof active.selectionStart === 'number') sel = [active.selectionStart, active.selectionEnd];

    document.body.classList.toggle('is-admin', isAdmin());
    const bell = $('#bell');
    const bellCount = realAdmin() ? inboxCount() + adminUnread() : unreadCount();
    bell.hidden = false;
    bell.dataset.count = bellCount;
    bell.setAttribute('aria-label', bellCount ? `Notifications, ${bellCount} new` : 'Notifications');
    const shown = t => (!t.staffTab || isStaff()) && (!t.statsTab || statsVisible()) && (!t.daysTab || daysVisible()) && !(archive && t.id === 'settings');
    if (TABS.some(t => t.id === tab && !shown(t))) tab = 'summary';
    $('#tabs').innerHTML = TABS.filter(shown).map(t =>
      `<button class="tab ${t.id === tab ? 'active' : ''}" data-action="tab" data-tab="${t.id}" title="${t.label}" aria-label="${t.label}" ${t.id === tab ? 'aria-current="page"' : ''}>
        ${icon(t.icon)}<span>${t.label}</span></button>`).join('');
    $('#who-badge').innerHTML = isAdmin()
      ? `<span class="badge badge-admin">${icon('<path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>', 14)} Admin</span>`
      : `<span class="badge badge-view">${icon('<path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/>', 14)} ${esc(session.name)} · ${isManager() ? 'manager' : 'view only'}</span>`;

    const views = { summary: viewHome, money: viewMoney, days: viewDays, stats: viewStats, players: viewPlayers, announce: viewAnnounce, settings: viewSettings };
    $('#view').innerHTML = archiveBar() + (views[tab] || viewHome)();
    $('#sheet-root').innerHTML = sheet ? renderSheet() : '';
    document.body.classList.toggle('sheet-open', !!sheet);
    markSettingsNav();

    restoreScroll(scroll);

    if (focusKey) {
      const el = document.querySelector(`[data-focus="${CSS.escape(focusKey)}"]`);
      if (el) {
        el.focus({ preventScroll: true });
        if (sel && el.setSelectionRange) { try { el.setSelectionRange(sel[0], sel[1]); } catch { /* ignore */ } }
      }
    }
  }

  // Fades the page in on tab changes only; render() also runs on every keystroke.
  function animateView() {
    const v = $('#view');
    v.classList.remove('enter');
    void v.offsetWidth;
    v.classList.add('enter');
    setTimeout(() => v.classList.remove('enter'), 700);
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
  // The account players send money to, as label/value pairs; empty when none is set.
  function payTo() {
    const s = state.settings;
    return [
      { label: 'Bank', value: s.payBank.trim() },
      { label: 'Account title', value: s.payTitle.trim() },
      { label: 'Account #', value: s.payAccount.trim() }
    ].filter(f => f.value);
  }

  function payToLines() {
    const f = payTo();
    return f.length ? ['', '*Pay to*', ...f.map(x => `${x.label}: ${x.value}`)] : [];
  }

  // The part of a player's balance due to ask for now: their unpaid advance, capped at what they owe.
  function advanceNow(p, net) {
    return Math.min(C.advanceDue(state, p), Math.max(-net, 0));
  }

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
    const adv = advanceNow(p, c.net);
    if (adv > 0) out.push(`*Advance to pay now: ${fmt(adv)}*`);
    if (c.net < 0) out.push(...payToLines());
    out.push('', `Full ledger: ${location.origin}`);
    return out.join('\n');
  }


  // ---------- balances picture ----------
  async function balanceImage() {
    const t = C.totals(state, fmt);
    const tour = C.tournamentStats(state);
    const stats = C.allStats(state);
    const showAdvance = C.advancePerPlayer(state) > 0;
    const rows = state.players.map(p => {
      const net = C.playerCalc(state, p, stats, fmt, tour).net;
      const adv = advanceNow(p, net);
      return {
        name: p.name,
        net,
        cells: showAdvance ? [adv > 0 ? { text: fmt(adv), color: '#ff6b6b' } : { text: '—', color: '#8d8a85' }] : []
      };
    });
    const chips = [];
    if (t.owed > 0) chips.push({ text: `Still to pay ${fmt(t.owed)}`, color: '#ff6b6b', bg: 'rgba(208,34,44,0.18)' });
    if (t.payBack > 0) chips.push({ text: `To pay back ${fmt(t.payBack)}`, color: '#e2b65c', bg: 'rgba(226,182,92,0.14)' });
    if (tour.fee > 0) chips.push({ text: `League ${fmt(tour.perMatch)} per match ÷ XI`, color: '#f5eee2', bg: 'rgba(255,255,255,0.07)' });
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
      columns: showAdvance ? [{ label: 'Advance', width: 170 }] : [],
      rows,
      payTo: payTo(),
      money: fmt,
      chips,
      dateText: 'Updated ' + new Date().toLocaleDateString('en-GB', { day: 'numeric', month: 'long', year: 'numeric' }),
      link: location.host,
      logoSrc: 'img/logo.jpeg'
    });
  }

  const BALANCES_IMAGE = { title: 'Balances picture', alt: 'Team balances', shareTitle: 'Red Falcons balances', fileName: () => `red-falcons-balances-${today()}.png` };

  // ---------- Squad list for management ----------
  // Name, role and jersey number of the players taking part in the league, in squad order.
  function squadImage() {
    return window.RFBalanceImage.renderSquad({
      players: state.players.filter(C.inTournament).map(p => ({
        name: p.name, jersey: p.jersey,
        // The C, VC and WK badges say it, so a "Captain" in the role text isn't repeated.
        role: (p.role || '').replace(/\s*\+?\s*captain\s*/i, '').trim(),
        captain: isCaptain(p), viceCaptain: !!p.viceCaptain, keeper: isKeeper(p)
      })),
      dateText: 'Updated ' + new Date().toLocaleDateString('en-GB', { day: 'numeric', month: 'long', year: 'numeric' }),
      logoSrc: 'img/logo.jpeg'
    });
  }

  // ---------- Playing XI picture ----------
  const XI_FIELDS = [
    { key: 'opponent', label: 'Opponent', placeholder: 'e.g. Lahore Lions' },
    { key: 'when', label: 'Date & time', placeholder: 'e.g. Sat 18 Oct · 3 pm' },
    { key: 'venue', label: 'Venue', placeholder: 'e.g. Model Town ground' }
  ];

  // The players marked as playing this match, in batting order, with captain and keeper from their role.
  function playingXI(dayId) {
    return battingOrderOf(dayId).map((p, i) => {
      const keeper = /keeper/i.test(p.role);
      return {
        opener: i < 2,
        name: p.name,
        role: keeper ? '' : p.role.replace(/\s*\+?\s*captain\s*/i, '').trim(),
        captain: /captain/i.test(p.role),
        keeper
      };
    });
  }

  function twelfthMan(dayId) {
    const p = findPlayer(state.twelfth[dayId]);
    return p ? { name: p.name, role: /keeper/i.test(p.role) ? 'Wicket-keeper' : p.role.replace(/\s*\+?\s*captain\s*/i, '').trim() } : null;
  }

  function xiImage(day, details) {
    return window.RFBalanceImage.renderXI({
      matchLabel: day.label,
      leagueName: leagueName(),
      ...details,
      players: playingXI(day.id),
      twelfth: twelfthMan(day.id),
      link: location.host,
      logoSrc: 'img/logo.jpeg'
    });
  }

  // Preview with Share (phones: straight into WhatsApp), Download and Copy. With opts.fields, the
  // typed details redraw the picture.
  function showImageDialog(blob, opts = BALANCES_IMAGE) {
    let file = new File([blob], opts.fileName(), { type: 'image/png' });
    const canShare = !!(navigator.canShare && navigator.canShare({ files: [file] }));
    const canCopy = !!(navigator.clipboard && window.ClipboardItem);
    let url = URL.createObjectURL(blob);
    const root = $('#dialog-root');
    const fields = opts.fields || [];
    root.innerHTML = `<div class="dialog-backdrop"></div>
      <div class="dialog image-dialog" role="dialog" aria-modal="true" aria-labelledby="img-title">
        <h3 id="img-title">${esc(opts.title)}</h3>
        ${fields.length ? `<div class="image-fields">${fields.map(f => `<label><span>${esc(f.label)}</span>
          <input class="field" type="text" maxlength="60" placeholder="${esc(f.placeholder)}" value="${esc(f.value || '')}" data-img-field="${f.key}"></label>`).join('')}</div>` : ''}
        <div class="image-preview"><img src="${url}" alt="${esc(opts.alt)}"></div>
        <div class="dialog-actions">
          <button class="btn btn-ghost" data-img="close">Close</button>
          ${canCopy ? `<button class="btn btn-ghost" data-img="copy">${COPY_ICON} Copy image</button>` : ''}
          <button class="btn ${canShare ? 'btn-ghost' : 'btn-primary'}" data-img="download">Download</button>
          ${canShare ? `<button class="btn btn-whatsapp" data-img="share">${WA_ICON} Share</button>` : ''}
        </div>
      </div>`;
    const close = () => { root.innerHTML = ''; URL.revokeObjectURL(url); document.removeEventListener('keydown', onKey); };
    let redrawTimer;
    root.querySelectorAll('[data-img-field]').forEach(input => input.addEventListener('input', () => {
      clearTimeout(redrawTimer);
      redrawTimer = setTimeout(async () => {
        const values = {};
        root.querySelectorAll('[data-img-field]').forEach(i => { values[i.dataset.imgField] = i.value; });
        blob = await opts.redraw(values);
        if (!root.querySelector('.image-preview img')) return; // closed meanwhile
        URL.revokeObjectURL(url);
        url = URL.createObjectURL(blob);
        file = new File([blob], opts.fileName(), { type: 'image/png' });
        root.querySelector('.image-preview img').src = url;
      }, 350);
    }));
    const onKey = e => { if (e.key === 'Escape') close(); };
    document.addEventListener('keydown', onKey);
    root.querySelector('.dialog-backdrop').addEventListener('click', close);
    root.querySelector('[data-img="close"]').addEventListener('click', close);
    root.querySelector('[data-img="download"]').addEventListener('click', () => {
      const a = document.createElement('a');
      a.href = url;
      a.download = opts.fileName();
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
      navigator.share({ files: [file], title: opts.shareTitle }).catch(() => { /* user cancelled */ });
    });
  }

  // A Pakistani mobile number (0300 1234567, +92 300…) in the international form wa.me expects.
  function waNumber(phone) {
    const d = String(phone || '').replace(/\D/g, '');
    if (/^03\d{9}$/.test(d)) return '92' + d.slice(1);
    if (/^923\d{9}$/.test(d)) return d;
    return '';
  }

  // Opens the player's own chat when they have a valid number, otherwise WhatsApp's chat picker.
  function openWhatsApp(text, phone) {
    window.open(`https://wa.me/${waNumber(phone)}?text=${encodeURIComponent(text)}`, '_blank', 'noopener');
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
    if (realAdmin()) return null;
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
    if (realAdmin() || !state.players.length) return '';
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
        ${advanceNow(me, c.net) > 0 ? `<small class="adv-due">Advance to pay now: ${fmt(advanceNow(me, c.net))}</small>` : ''}
        ${c.net < 0 && payTo().length ? `<small class="pay-to-line">Pay to ${payTo().map(f => esc(f.value)).join(' · ')}</small>` : ''}
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

  // Share of the league fee already paid to management, as a round progress around the club crest.
  function pctRing(pct) {
    const R = 52, C = 2 * Math.PI * R;
    return `<div class="pct-ring" role="img" aria-label="${pct}% of the league fee paid to management" title="${pct}% paid to management">
      <svg viewBox="0 0 120 120" aria-hidden="true">
        <defs><linearGradient id="pctGrad" x1="0" y1="0" x2="1" y2="1"><stop offset="0" style="stop-color:var(--gold-light)"/><stop offset="1" style="stop-color:var(--gold-deep)"/></linearGradient></defs>
        <circle class="track" cx="60" cy="60" r="${R}"/>
        ${pct > 0 ? `<circle class="fill" cx="60" cy="60" r="${R}" transform="rotate(-90 60 60)" stroke-dasharray="${(C * pct / 100).toFixed(2)} ${C.toFixed(2)}"/>` : ''}
      </svg>
      <img class="pct-logo" src="img/logo.jpeg" alt="">
      <span class="pct-badge">${pct}%</span>
    </div>`;
  }

  function leagueBlock() {
    const t = C.totals(state, fmt);
    const admin = isAdmin();
    if (!(t.tournamentFee > 0) && !state.tournament.payments.length && !admin) return '';
    const pct = t.tournamentFee > 0 ? Math.min(100, Math.round(t.sentToManagement / t.tournamentFee * 100)) : 0;
    return `<section class="panel league">
      <div class="league-head">
        <div><span class="day-kind">League</span><h3>${esc(leagueName())}</h3>
          ${t.tournamentFee > 0 ? `<span class="league-pct">${pct}% paid to management</span>` : ''}</div>
        ${t.tournamentFee > 0 ? pctRing(pct) : ''}
      </div>
      <div class="league-figs">
        <div><span>League fee</span><strong>${t.tournamentFee > 0 ? fmt(t.tournamentFee) : '—'}</strong>
          ${t.tournamentFee > 0 ? '' : `<small>${admin ? 'Set it on the Days tab' : 'Not set'}</small>`}</div>
        <div><span>${admin ? 'I have paid management' : 'Paid to management'}</span><strong class="pos">${fmt(t.sentToManagement)}</strong></div>
        <div><span>Still owed to management</span><strong class="${t.managementDue > 0 ? 'neg' : ''}">${fmt(t.managementDue)}</strong></div>
        <div ${ownOnly() ? 'hidden' : ''}><span>${admin ? 'In my account' : 'With the manager'}</span><strong class="${t.cashInHand < 0 ? 'neg' : ''}">${fmtNet(t.cashInHand).replace(/^\+/, '')}</strong>
          <small>Collected ${fmt(t.collected)} − sent ${fmt(t.sentToManagement)}${t.daySpent - t.fronted > 0 ? ` − expenses ${fmt(t.daySpent - t.fronted)}` : ''}</small></div>
      </div>
      <h4 class="league-sub">Payments to management</h4>
      ${paymentEditor(MGMT, state.tournament.payments, '+ Add payment to management')}
    </section>`;
  }

  // ---------- fixtures: next match, countdown, calendar ----------
  const fmtClock = hhmm => {
    const m = /^(\d{2}):(\d{2})$/.exec(hhmm || '');
    if (!m) return '';
    const h = +m[1];
    return `${h % 12 || 12}:${m[2]} ${h < 12 ? 'am' : 'pm'}`;
  };
  const dayDiff = iso => Math.round((new Date(`${iso}T00:00:00`) - new Date(`${today()}T00:00:00`)) / 86400000);

  // Real matches the admin hasn't finished, soonest first; ones with no date yet come last.
  function upcomingFixtures() {
    return C.matches(state).filter(m => !m.test && !m.finished)
      .sort((a, b) => (a.date || '9999').localeCompare(b.date || '9999') || (a.time || '99:99').localeCompare(b.time || '99:99'))
      .filter(m => !m.date || dayDiff(m.date) >= 0);
  }

  // How long to go, live to the second. Without a confirmed time it counts down to the start of match day.
  function countdownHtml(m) {
    const block = (n, label, pad) => `<div class="nm-block"><strong>${pad ? String(n).padStart(2, '0') : n}</strong><span>${label}</span></div>`;
    if (!m.date) return '<p class="nm-msg">Date to be confirmed</p>';
    const confirmed = !!m.time;
    const ms = new Date(`${m.date}T${m.time || '00:00'}:00`) - new Date();
    if (ms <= 0) {
      if (!confirmed) return '<div class="nm-blocks"><div class="nm-block wide"><strong>Today</strong><span>match day</span></div></div>';
      return `<p class="nm-msg">${-ms < 4 * 3600000 ? 'It’s match time' : 'Awaiting the result'}</p>`;
    }
    const s = Math.floor(ms / 1000);
    const sep = '<i class="nm-sep" aria-hidden="true">:</i>';
    return `<div class="nm-blocks">${block(Math.floor(s / 86400), 'days')}${sep}${block(Math.floor(s / 3600) % 24, 'hours', true)}${sep}${block(Math.floor(s / 60) % 60, 'mins', true)}${sep}${block(s % 60, 'secs', true).replace('nm-block', 'nm-block secs')}</div>
      ${confirmed ? '' : '<p class="nm-sub">until match day · time to be confirmed</p>'}`;
  }

  // The admin decides in Settings whether players see these two cards. The admin sees them too, except
  // the fixtures calendar, which they can also hide from themselves.
  const cardShown = key => realAdmin() ? (key !== 'showFixtures' || state.settings.adminShowFixtures) : !!state.settings[key];
  const hiddenNote = key => !state.settings[key] ? '<span class="card-hidden">Hidden from players</span>' : '';

  // Which stats to show for a match: its own, or (until real ones are added) the first test match's, as a sample.
  function scorecardSource(m) {
    const has = id => state.stats[id] && Object.keys(state.stats[id]).length > 0;
    if (has(m.id)) return { id: m.id, sample: null };
    const test = C.matches(state).find(x => x.test && has(x.id));
    return test ? { id: test.id, sample: test } : null;
  }

  // ---------- other league teams ----------
  // Their logos, matched on the opponent's name ignoring case, spaces and punctuation.
  const TEAMS = [
    { name: 'Pak 11', keys: ['pak11', 'pakistan11'], logo: 'img/teams/pak11.png' },
    { name: 'Anggler', keys: ['anggler', 'angler'], logo: 'img/teams/anggler.png' },
    { name: 'Rage Rovers', keys: ['ragerovers', 'rage'], logo: 'img/teams/rage-rovers.png' },
    { name: 'Raptors', keys: ['raptors', 'raptor'], logo: 'img/teams/raptors.png' },
    { name: 'Galacticos', keys: ['galacticos', 'galactico'], logo: 'img/teams/galacticos.png' }
  ];
  function teamLogo(name) {
    const n = String(name || '').toLowerCase().replace(/[^a-z0-9]/g, '');
    if (!n) return '';
    const t = TEAMS.find(x => x.keys.some(k => n === k || n.includes(k)));
    return t ? t.logo : '';
  }

  // ---------- Playing XI announcement ----------
  function setAnnounced(matchId, on) {
    const m = state.matches.find(x => x.id === matchId);
    if (m) m.announced = !!on;
  }
  const xiOf = m => state.players.filter(p => p.att[m.id]);
  // Whether this viewer may see a match's XI: once announced, or (as a preview) the admin.
  const xiPublic = m => !!m.announced && xiOf(m).length > 0;

  function announceButton(m, cls = 'btn-gold') {
    if (!isAdmin() || m.finished || !xiOf(m).length) return '';
    return m.announced
      ? `<button class="btn btn-ghost small" data-action="unannounce-xi" data-id="${esc(m.id)}">Hide Playing XI</button>`
      : `<button class="btn ${cls}" data-action="announce-xi" data-id="${esc(m.id)}">${icon('<path d="M3 11v2a1 1 0 0 0 1 1h2l5 4V6L6 10H4a1 1 0 0 0-1 1z"/><path d="M15.5 8.5a5 5 0 0 1 0 7M18.5 5.5a9 9 0 0 1 0 13"/>', 18)} Announce Playing XI</button>`;
  }

  // "See details" for a match: the scorecard once finished, else the announced XI, else the full squad.
  const opponentLogo = m => teamLogo(m.opponent) ? `<img class="sheet-opp-logo" src="${teamLogo(m.opponent)}" alt="${esc(m.opponent)} logo">` : '';

  function sheetMatchInfo() {
    const m = C.matches(state).find(x => x.id === sheet.id);
    if (!m) return '';
    const preview = !m.announced && isAdmin() && xiOf(m).length > 0;
    const html = m.finished ? sheetScorecard() : xiPublic(m) || preview ? sheetLineup(m, preview) : sheetSquad(m);
    return html.replace(/<\/div>\s*$/, `${matchExtras(m)}</div>`);
  }

  function lineupHead(m, kind) {
    return `<header class="sheet-head">
        <div>${opponentLogo(m)}<span class="day-kind">${kind}</span><h2>Red Falcons v ${m.opponent ? esc(m.opponent) : 'TBC'}</h2>
          <p class="muted">${esc(matchInfo(m)) || m.label}</p></div>
        <button class="icon-btn" data-action="close-sheet" aria-label="Close">${X_ICON}</button>
      </header>`;
  }

  const roleTags = p => [isCaptain(p) && 'c', p.viceCaptain && 'vc', isKeeper(p) && 'wk'].filter(Boolean)
    .map(t => `<span class="lu-tag ${t}">${t.toUpperCase()}</span>`).join('');

  function sheetLineup(m, preview) {
    const xi = battingOrderOf(m.id);
    const twelfth = findPlayer(state.twelfth[m.id]);
    const row = (p, n, extra = '') => `<li class="lu-row ${extra}">
        <span class="lu-n">${n}</span>${avatar(p, 40)}
        <span class="lu-name"><strong>${esc(p.name)}</strong><small>${esc(p.role) || '—'}</small></span>
        <span class="lu-tags">${roleTags(p)}</span>
      </li>`;
    return `${lineupHead(m, preview ? 'Playing XI · not announced' : 'Playing XI')}
      <div class="sheet-body lu">
        ${preview ? `<div class="finish-box"><span>${icon('<path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/>', 18)}<span><strong>Only you can see this</strong><small>Players see the full squad until you announce the XI.</small></span></span>${announceButton(m)}</div>` : ''}
        <h3 class="sub first">Batting order <span class="muted">${xi.length} players</span></h3>
        <ol class="lu-list">${xi.map((p, i) => row(p, i + 1, i < 2 ? 'opener' : '')).join('')}</ol>
        ${twelfth ? `<h3 class="sub">12th man</h3><ol class="lu-list">${row(twelfth, 12, 'twelfth')}</ol>` : ''}
        ${!preview && isAdmin() ? `<p class="lu-admin">${announceButton(m)}</p>` : ''}
      </div>`;
  }

  function sheetSquad(m) {
    const squad = state.players.filter(C.inTournament);
    return `${lineupHead(m, 'Squad')}
      <div class="sheet-body lu">
        <p class="lu-note">${icon('<path d="M3 11v2a1 1 0 0 0 1 1h2l5 4V6L6 10H4a1 1 0 0 0-1 1z"/>', 16)} The Playing XI will be announced before match day.</p>
        <h3 class="sub first">Red Falcons squad <span class="muted">${squad.length} players</span></h3>
        <ul class="lu-list squad">${squad.map(p => `<li class="lu-row">
            ${avatar(p, 40)}<span class="lu-name"><strong>${esc(p.name)}</strong><small>${esc(p.role) || '—'}</small></span>
            <span class="lu-tags">${roleTags(p)}</span></li>`).join('')}</ul>
      </div>`;
  }

  // A scorecard in the usual cricket layout: batting, bowling and fielding for the Red Falcons.
  function sheetScorecard() {
    const m = C.matches(state).find(x => x.id === sheet.id);
    if (!m) return '';
    const src = scorecardSource(m);
    const name = p => `${esc(p.name)}${isCaptain(p) ? ' (c)' : ''}${isKeeper(p) ? ' †' : ''}`;
    const head = `<header class="sheet-head">
        <div>${opponentLogo(m)}<span class="day-kind">Scorecard</span><h2>Red Falcons v ${m.opponent ? esc(m.opponent) : 'TBC'}</h2>
          <p class="muted">${esc(matchInfo(m)) || m.label} ${resultChip(m)}</p></div>
        <button class="icon-btn" data-action="close-sheet" aria-label="Close">${X_ICON}</button>
      </header>`;
    if (!src) {
      return `${head}<div class="sheet-body"><p class="muted center">The scorecard appears here once the match stats are added.</p></div>`;
    }
    const order = battingOrderOf(src.id);
    const rows = order.map(p => ({ p, e: C.statOf(state, src.id, p.id) }));
    const batters = rows.filter(r => C.batted(r.e));
    const dnb = order.filter(p => !batters.some(r => r.p.id === p.id));
    const runs = batters.reduce((s, r) => s + C.runsOf(r.e), 0);
    const balls = batters.reduce((s, r) => s + r.e.balls, 0);
    const outs = batters.filter(r => r.e.out).length;
    const bowlers = rows.filter(r => C.bowled(r.e));
    const catchers = rows.filter(r => r.e.catches > 0);
    const batRows = batters.map(r => `<tr><td class="sc-name"><strong>${name(r.p)}</strong><small>${r.e.out ? 'out' : 'not out'}</small></td>
      <td class="sc-main">${C.runsOf(r.e)}</td><td>${r.e.balls}</td><td>${r.e.fours}</td><td>${r.e.sixes}</td><td>${SR(C.runsOf(r.e), r.e.balls)}</td></tr>`).join('');
    const bowlRows = bowlers.map(r => `<tr><td class="sc-name"><strong>${name(r.p)}</strong></td>
      <td>${C.oversOf(r.e.bowlBalls)}</td><td>${C.concededOf(r.e)}</td><td class="sc-main">${r.e.wickets}</td><td>${ECON(C.concededOf(r.e), r.e.bowlBalls)}</td><td>${r.e.wides}</td><td>${r.e.noBalls}</td></tr>`).join('');
    return `${head}
      <div class="sheet-body sc">
        ${src.sample ? `<p class="sc-sample">Sample scorecard, taken from ${src.sample.label}. It is replaced by ${m.label}’s own stats once they are added.</p>` : ''}
        <div class="sc-title"><h3>Red Falcons innings</h3><strong>${runs}/${outs} <small>(${C.oversOf(balls)} ov)</small></strong></div>
        <div class="table-wrap"><table class="sc-table">
          <thead><tr><th class="sc-name">Batter</th><th>R</th><th>B</th><th>4s</th><th>6s</th><th>SR</th></tr></thead>
          <tbody>${batRows || '<tr><td colspan="6" class="muted">Nobody batted.</td></tr>'}
            <tr class="sc-extras"><td class="sc-name">Extras</td><td colspan="5">not recorded</td></tr>
            <tr class="sc-total"><td class="sc-name">Total</td><td class="sc-main">${runs}</td><td colspan="4">${outs} wkts · ${C.oversOf(balls)} overs</td></tr></tbody>
        </table></div>
        ${dnb.length ? `<p class="sc-dnb"><strong>Did not bat:</strong> ${dnb.map(name).join(', ')}</p>` : ''}
        <div class="sc-title"><h3>Red Falcons bowling</h3></div>
        <div class="table-wrap"><table class="sc-table">
          <thead><tr><th class="sc-name">Bowler</th><th>O</th><th>R</th><th>W</th><th>Econ</th><th>Wd</th><th>NB</th></tr></thead>
          <tbody>${bowlRows || '<tr><td colspan="7" class="muted">No bowling recorded.</td></tr>'}</tbody>
        </table></div>
        ${catchers.length ? `<p class="sc-dnb"><strong>Catches:</strong> ${catchers.map(r => `${name(r.p)} (${r.e.catches})`).join(', ')}</p>` : ''}
        <p class="muted sc-note">Totals are the batters’ runs. The opposition’s innings, extras and fall of wickets aren’t recorded.</p>
      </div>`;
  }

  // ---------- match-day weather ----------
  // Free forecast from Open-Meteo (no key): the venue name is looked up once, then the match day's forecast, which
  // only exists up to 16 days ahead. Anything that fails just leaves the weather line out.
  const wxCache = {};          // "venue|date" -> { ok, ... } | { ok: false }
  const wxBusy = {};
  const WX_CODES = [
    [[0], 'Clear sky', '☀️'], [[1], 'Mostly clear', '🌤️'], [[2], 'Partly cloudy', '⛅'], [[3], 'Overcast', '☁️'],
    [[45, 48], 'Foggy', '🌫️'], [[51, 53, 55, 56, 57], 'Drizzle', '🌦️'], [[61, 63, 65, 66, 67], 'Rain', '🌧️'],
    [[71, 73, 75, 77, 85, 86], 'Snow', '❄️'], [[80, 81, 82], 'Rain showers', '🌦️'], [[95, 96, 99], 'Thunderstorm', '⛈️']
  ];
  const wxInfo = code => WX_CODES.find(c => c[0].includes(code)) || [[], 'Weather', '🌡️'];

  async function geocodeVenue(venue) {
    const key = 'rf.geo.' + venue.toLowerCase();
    const hit = readStore(key);
    if (hit) return hit;
    const tries = [venue, ...venue.split(',').map(x => x.trim()).filter(Boolean).reverse()];
    for (const q of [...new Set(tries)]) {
      const r = await fetch(`https://geocoding-api.open-meteo.com/v1/search?name=${encodeURIComponent(q)}&count=1&language=en`).then(x => x.json());
      const g = r && r.results && r.results[0];
      if (g) { const loc = { lat: g.latitude, lon: g.longitude, place: g.name }; writeStore(key, loc); return loc; }
    }
    return null;
  }

  function ensureWeather(m) {
    if (!m || !m.date || !(m.venue || '').trim()) return;
    const n = dayDiff(m.date);
    if (n < 0 || n > 15) return;
    const key = `${m.venue.trim()}|${m.date}`;
    if (wxCache[key] || wxBusy[key]) return;
    wxBusy[key] = true;
    (async () => {
      try {
        const loc = await geocodeVenue(m.venue.trim());
        if (!loc) throw new Error('place');
        const u = `https://api.open-meteo.com/v1/forecast?latitude=${loc.lat}&longitude=${loc.lon}&timezone=auto&start_date=${m.date}&end_date=${m.date}`
          + '&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,wind_speed_10m_max';
        const d = (await fetch(u).then(x => x.json())).daily;
        if (!d || !d.time || !d.time.length) throw new Error('range');
        wxCache[key] = { ok: true, code: d.weather_code[0], hi: Math.round(d.temperature_2m_max[0]), lo: Math.round(d.temperature_2m_min[0]),
          rain: d.precipitation_probability_max[0], wind: Math.round(d.wind_speed_10m_max[0]), place: loc.place };
      } catch { wxCache[key] = { ok: false }; }
      delete wxBusy[key];
      render();
    })();
  }

  function weatherLine(m) {
    if (!m || !m.date || !(m.venue || '').trim()) return '';
    ensureWeather(m);
    const w = wxCache[`${m.venue.trim()}|${m.date}`];
    if (!w || !w.ok) return '';
    const [, label, emoji] = wxInfo(w.code);
    const wet = w.rain != null && w.rain >= 60;
    return `<div class="nm-weather ${wet ? 'wet' : ''}" title="Forecast for ${esc(w.place)}">
      <span class="wx-emoji" aria-hidden="true">${emoji}</span>
      <span class="wx-main"><strong>${label}</strong><small>${w.hi}° / ${w.lo}° · ${w.rain == null ? '—' : w.rain}% chance of rain · wind ${w.wind} km/h</small></span>
      ${wet ? '<em class="wx-warn">Rain likely</em>' : ''}
    </div>`;
  }

  // The Summary's match-day poster: crest vs opponent, a scoreboard countdown, details and the XI if announced.
  function nextMatchCard() {
    if (!cardShown('showNextMatch')) return '';
    const m = upcomingFixtures()[0];
    if (!m) return '';
    const ic = (d, n = 16) => icon(d, n);
    const days = m.date ? dayDiff(m.date) : null;
    const status = days == null ? 'Date to be confirmed' : days === 0 ? 'Match day!' : days === 1 ? 'Tomorrow' : `In ${days} days`;
    const opp = (m.opponent || '').trim();
    const initials = opp ? opp.split(/\s+/).filter(Boolean).slice(0, 2).map(w => w[0]).join('').toUpperCase() : '?';
    const xi = xiPublic(m) ? xiOf(m) : [];
    const pending = !m.announced && isAdmin() && xiOf(m).length > 0;
    const chips = [
      `<span class="nm-chip">${ic('<rect x="3" y="4" width="18" height="18" rx="2"/><path d="M16 2v4M8 2v4M3 10h18"/>')} ${esc(m.date ? formatMatchDate(m.date) : 'Date to be confirmed')}</span>`,
      `<span class="nm-chip ${m.time ? '' : 'tbc'}">${ic('<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>')} ${m.time ? esc(fmtClock(m.time)) : 'Time to be confirmed'}</span>`,
      m.venue ? `<span class="nm-chip">${ic('<path d="M12 22s7-6.5 7-12a7 7 0 0 0-14 0c0 5.5 7 12 7 12z"/><circle cx="12" cy="10" r="2.5"/>')} ${esc(m.venue)}</span>` : ''
    ].join('');
    const squad = xi.length
      ? `<div class="nm-xi"><span class="nm-xi-faces">${xi.slice(0, 11).map(p => `<span title="${esc(p.name)}">${avatar(p, 34)}</span>`).join('')}</span>
          <span class="nm-xi-text"><strong>Playing XI announced</strong><small>${xi.length} player${xi.length === 1 ? '' : 's'}${state.twelfth[m.id] ? ' + 12th man' : ''}</small></span></div>`
      : pending
        ? `<div class="nm-xi none">${ic('<path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/>', 18)}<span class="nm-xi-text"><strong>XI picked · not announced</strong><small>Players see “XI not announced yet”</small></span></div>`
        : `<div class="nm-xi none">${ic('<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/>', 18)}<span class="nm-xi-text"><strong>XI not announced yet</strong><small>Watch this space before match day</small></span></div>`;
    return `<section class="panel nm-card ${days === 0 ? 'today' : ''}" data-nm="${esc(m.id)}">
      <span class="nm-sweep" aria-hidden="true"></span>
      <div class="nm-top">
        <span class="nm-kicker">${ic('<path d="M12 2l2.4 4.9 5.4.8-3.9 3.8.9 5.4L12 14.4 7.2 16.9l.9-5.4L4.2 7.7l5.4-.8z"/>', 14)} Next match · ${m.label} ${hiddenNote('showNextMatch')}</span>
        <span class="nm-status">${esc(status)}</span>
      </div>
      <p class="nm-league">${esc(leagueName())}</p>
      <div class="nm-face">
        <div class="nm-team us"><span class="nm-badge crest"><img src="img/logo.jpeg" alt=""></span><strong>Red Falcons</strong></div>
        <div class="nm-vs"><span>VS</span></div>
        <div class="nm-team them">${teamLogo(opp)
          ? `<span class="nm-badge logo"><img src="${teamLogo(opp)}" alt="${esc(opp)} logo"></span>`
          : `<span class="nm-badge opp">${esc(initials)}</span>`}<strong>${opp ? esc(opp) : 'Opponent TBC'}</strong></div>
      </div>
      <div class="nm-count" data-id="${esc(m.id)}">${countdownHtml(m)}</div>
      <div class="nm-chips">${chips}</div>
      ${weatherLine(m)}
      ${predictBlock(m, true)}
      <div class="nm-foot">
        ${squad}
        <div class="nm-actions">
          ${m.venue ? `<a class="btn btn-ghost" href="https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(m.venue)}" target="_blank" rel="noopener">${ic('<path d="m3 11 19-9-9 19-2-8-8-2z"/>', 18)} Directions</a>` : ''}
          ${pending ? announceButton(m) : ''}
          <button class="btn ${pending ? 'btn-ghost' : 'btn-gold'} nm-details" data-action="open-scorecard" data-id="${esc(m.id)}">See details</button>
        </div>
      </div>
    </section>`;
  }

  // Every league fixture with its date: upcoming first, then the ones already played.
  function fixturesPanel() {
    if (!cardShown('showFixtures')) return '';
    const real = C.matches(state).filter(m => !m.test);
    if (!real.length) return '';
    const byDate = (a, b) => (a.date || '9999').localeCompare(b.date || '9999');
    const open = real.filter(m => !m.finished).sort(byDate);
    const done = real.filter(m => m.finished).sort(byDate).reverse();
    const badge = x => {
      const lg = teamLogo(x.opponent);
      const ini = x.opponent ? x.opponent.split(/\s+/).filter(Boolean).slice(0, 2).map(w => w[0]).join('').toUpperCase() : '?';
      return lg ? `<span class="fx-badge"><img src="${lg}" alt=""></span>` : `<span class="fx-badge ini">${esc(ini)}</span>`;
    };
    const tag = x => {
      if (x.finished) { const r = RESULTS.find(q => q[0] === x.result); return r ? `<em class="fx-tag res ${x.result}">${r[1]}</em>` : '<em class="fx-tag done">Played</em>'; }
      if (!x.date) return '<em class="fx-tag">Date TBC</em>';
      const n = dayDiff(x.date);
      return `<em class="fx-tag ${n === 0 ? 'now' : ''}">${n === 0 ? 'Today' : n === 1 ? 'Tomorrow' : n > 1 ? `In ${n} days` : 'Awaiting result'}</em>`;
    };
    const row = x => {
      const dt = x.date ? new Date(`${x.date}T00:00:00`) : null;
      return `<li><button type="button" class="fx-row ${x.finished ? 'played' : ''}" data-action="open-scorecard" data-id="${esc(x.id)}">
        <span class="fx-day">${dt ? `<b>${dt.getDate()}</b><small>${dt.toLocaleDateString('en-GB', { month: 'short' })}</small>` : '<b>—</b><small>TBC</small>'}</span>
        ${badge(x)}
        <span class="fx-what"><strong>${x.opponent ? `vs ${esc(x.opponent)}` : 'Opponent TBC'}</strong>
          <small>${esc(x.label)}${dt ? ` · ${dt.toLocaleDateString('en-GB', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })}` : ''}</small></span>
        ${tag(x)}
      </button></li>`;
    };
    const group = (title, list) => list.length ? `<h4 class="fx-group">${title} <span>${list.length}</span></h4><ul class="fx-list">${list.map(row).join('')}</ul>` : '';
    return `<section class="panel fx-panel">
      <div class="fx-top">
        <span class="fx-kicker">${icon('<rect x="3" y="4" width="18" height="18" rx="2"/><path d="M16 2v4M8 2v4M3 10h18"/>', 14)} Fixtures ${hiddenNote('showFixtures')}</span>
        <strong class="fx-heading">${esc(leagueName())}</strong>
      </div>
      ${group('Upcoming', open)}${group('Played', done)}
    </section>`;
  }

  // Keeps the countdown ticking while the Summary screen is open.
  setInterval(() => {
    document.querySelectorAll('.nm-count').forEach(el => {
      const m = C.matches(state || { matches: [], trainings: [] }).find(x => x.id === el.dataset.id);
      if (m) el.innerHTML = countdownHtml(m);
    });
  }, 1000);

  const realMatchCount = () => state.matches.filter(m => !m.test && matchVisible(m)).length;

  function viewMoney() {
    const t = C.totals(state, fmt);
    const stats = C.allStats(state);
    const tour = C.tournamentStats(state);
    const days = C.days(state);
    const admin = isAdmin();
    const cards = [
      { label: 'Collected', value: fmt(t.collected), cls: '', note: 'Paid in by players' },
      { label: 'League fee', value: t.tournamentFee > 0 ? fmt(t.tournamentFee) : '—', cls: '', note: tour.fee > 0 ? `${fmt(tour.perMatch)} per match · split among each XI` : 'Not set' },
      { label: 'Day expenses', value: fmt(t.daySpent), cls: '', note: t.fronted > 0 ? `${fmt(t.fronted)} bought by players` : 'Trainings & matches' },
      { label: admin ? 'In my account' : 'With the manager', value: fmtNet(t.cashInHand).replace(/^\+/, ''), cls: t.cashInHand < 0 ? 'neg' : '', note: 'Collected − sent to management − expenses' },
      { label: admin ? 'You pay back' : 'To be paid back', value: fmt(t.payBack), cls: 'pos', note: 'Players in credit' },
      { label: admin ? 'Still owed to you' : 'Balance due', value: fmt(t.owed), cls: 'neg', note: 'Players still to pay' }
    ];

    const rows = state.players.map((p, i) => {
      const c = C.playerCalc(state, p, stats, fmt, tour);
      const shown = seesMoneyOf(p);
      const adv = shown ? advanceNow(p, c.net) : 0;
      const row = `<button class="prow" data-net="${shown ? netClass(c.net) : 'zero'}" data-action="open-profile" data-id="${esc(p.id)}">
        <span class="sr">${i + 1}</span>${avatar(p, 34)}
        <span class="pname"><strong>${esc(p.name) || '<em>Unnamed</em>'}</strong><small>${esc(p.role) || '—'}${C.inTournament(p) ? '' : ' · not in tournament'}${adv > 0 ? ` · <span class="adv-due">advance ${fmt(adv)}</span>` : ''}</small></span>
        ${shown ? `<span class="pnet ${netClass(c.net)}"><strong>${fmtNet(c.net)}</strong><small>${netText(c.net)}</small></span>` : '<span class="pnet zero"><small>Private</small></span>'}
      </button>`;
      return row;
    }).join('');

    // Matches the admin hasn't finished yet stay off the sheet.
    const sheetDays = days.filter(C.isCharged);
    const table = state.players.length ? `
      <section class="panel">
        <div class="panel-head"><h3>Season sheet</h3><p class="muted">Who attended what, and where everyone stands.</p></div>
        <div class="table-wrap">
          <table class="sheet-table">
            <thead>
              <tr><th class="sticky">#</th><th class="sticky sticky-2">Player</th>
                ${sheetDays.map(d => `<th class="day-col ${d.type}" title="${esc(d.label)}${d.desc ? ' — ' + esc(d.desc) : ''}">${d.short}<small>${!C.isCharged(d) ? 'pending' : stats[d.id].n > 0 && stats[d.id].amount > 0 ? fmt(stats[d.id].share) : '—'}</small></th>`).join('')}
                <th class="day-col tour" title="League fee for the matches each player played">League<small>${tour.fee > 0 ? fmt(tour.perMatch) + '/match' : '—'}</small></th>
                <th>Cost</th><th>Paid</th><th title="Items bought for the team">Bought</th><th>Balance</th></tr>
            </thead>
            <tbody>
              ${state.players.map((p, i) => {
                const c = C.playerCalc(state, p, stats, fmt, tour);
                return `<tr data-action="open-player" data-id="${esc(p.id)}">
                  <td class="sticky">${i + 1}</td><td class="sticky sticky-2 name-cell"><button class="pl-name" data-action="open-profile" data-id="${esc(p.id)}">${esc(p.name)}</button><button class="icon-btn exp-btn mini" data-action="open-player" data-id="${esc(p.id)}" aria-label="Expense card for ${esc(p.name)}" title="Expenses and payments">${WALLET_ICON}</button></td>
                  ${sheetDays.map(d => `<td class="dot-cell">${p.att[d.id] ? `<span class="dot ${d.type}" title="${d.type === 'match' ? 'Played' : 'Arrived'}"></span>` : isTwelfth(p, d) ? '<span class="dot twelfth" title="12th man"></span>' : '<span class="dot-empty"></span>'}</td>`).join('')}
                  <td class="league-cell">${tour.charges[p.id].amount > 0 ? fmt(tour.charges[p.id].amount) : '—'}</td>
                  ${seesMoneyOf(p) ? `<td>${fmt(c.cost)}</td><td>${fmt(c.paid)}</td><td>${c.credit > 0 ? fmt(c.credit) : '—'}</td>
                  <td class="${netClass(c.net)} strong">${fmtNet(c.net)}</td>` : '<td>—</td><td>—</td><td>—</td><td class="zero">Private</td>'}</tr>`;
              }).join('')}
            </tbody>
            <tfoot><tr><td class="sticky"></td><td class="sticky sticky-2">Attended</td>
              ${sheetDays.map(d => `<td>${stats[d.id].n}</td>`).join('')}<td>${fmt(Object.values(tour.charges).reduce((x, c) => x + c.amount, 0))}</td>
              <td>${fmt(t.spent)}</td><td>${ownOnly() ? '—' : fmt(t.collected)}</td><td>${t.fronted > 0 ? fmt(t.fronted) : '—'}</td><td></td></tr></tfoot>
          </table>
        </div>
      </section>` : '';

    return `
      ${pageHead('Money', `${C.leagueMatchCount(state) ? leagueName() + ' · ' : ''}${state.players.length} player${state.players.length === 1 ? '' : 's'} · ${realMatchCount()} match${realMatchCount() === 1 ? '' : 'es'}`,
        admin && state.players.length ? `<div class="share-row">
          <button class="btn btn-gold" data-action="share-image">${IMAGE_ICON} Share as image</button></div>` : '')}
      ${myBalance()}
      ${leagueBlock()}
      <div class="stats" ${ownOnly() ? 'hidden' : ''}>${cards.map(c => `<div class="stat ${c.cls}"><span>${c.label}</span><strong>${c.value}</strong><small>${esc(c.note)}</small></div>`).join('')}</div>
      ${warningBanner()}
      <div class="summary-grid">
        <section class="panel">
          <div class="panel-head"><h3>Players</h3><p class="muted">Tap a player for the full breakdown.</p></div>
          ${rows ? `<div class="plist">${rows}</div>` : emptyPlayers()}
        </section>
        ${table}
      </div>`;
  }

  // ---------- Home: what's coming up, and anything that needs you ----------
  // Things waiting for this person, shown as one short card instead of separate banners.
  function todoCard() {
    if (archive) return '';
    const items = [];
    const item = (iconHtml, title, note, buttons) => `<li class="todo-item"><span class="pp-ico">${iconHtml}</span>
      <div class="pp-text"><strong>${title}</strong><small>${note}</small></div><div class="pp-actions">${buttons}</div></li>`;
    if (realAdmin()) {
      const n = inboxCount();
      if (n) items.push(item(BELL_ICON, `${n} request${n === 1 ? '' : 's'} waiting`, 'Manager access or lineup changes need your decision.', '<button class="btn btn-gold" data-action="open-inbox">Open</button>'));
    } else if (state.players.length && !myPlayer()) {
      items.push(item(icon(PERSON_PATH, 22), 'Which player are you?', 'Choose your squad name to vote, predict, answer polls and see your balance.', '<button class="btn btn-gold" data-action="go-settings">Choose name</button>'));
    }
    if (!realAdmin() && feed) {
      const me = myPlayer();
      const req = feed.find(n => n.action === 'profile');
      if (req && !(me && me.profileAt && me.profileAt >= req.createdAt)) {
        items.push(item(icon(PERSON_PATH, 22), 'Update your profile', 'Add a photo, jersey name and number, and batting and bowling details.', '<button class="btn btn-gold" data-action="open-profile-form">Update now</button>'));
      }
    }
    const canPush = push.ready && push.supported && push.enabled && !push.subscribed && push.permission !== 'denied' && !(isIOS() && !isInstalled()) && !readStore(PUSH_PROMPT_KEY);
    if (canPush) {
      items.push(item(BELL_ICON, 'Never miss the team news', 'Get a notification when the Playing XI is announced or the team posts an update.',
        '<button class="btn btn-gold" data-action="push-on">Turn on</button><button class="btn btn-ghost" data-action="push-dismiss">Not now</button>'));
    }
    return items.length ? `<section class="panel todo-card"><h3 class="todo-title">${icon('<path d="M9 11l3 3L22 4"/><path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/>', 18)} For you <span>${items.length}</span></h3><ul class="todo-list">${items.join('')}</ul></section>` : '';
  }

  // A small money line, with the full picture one tap away on the Money tab.
  function moneyGlance() {
    if (!state.players.length) return '';
    const open = '<button class="btn btn-ghost small" data-action="tab" data-tab="money">Open Money</button>';
    if (realAdmin()) {
      const t = C.totals(state, fmt);
      return `<section class="panel glance"><div class="glance-row">
          <div><small>Collected</small><strong>${fmt(t.collected)}</strong></div>
          <div><small>Still owed to you</small><strong class="neg">${fmt(t.owed)}</strong></div>
          <div><small>In my account</small><strong>${fmtNet(t.cashInHand).replace(/^\+/, '')}</strong></div>
        </div>${open}</section>`;
    }
    const me = myPlayer();
    if (!me) return '';
    const c = C.playerCalc(state, me, null, fmt);
    return `<section class="panel glance ${netClass(c.net)}"><div class="glance-row">
        <div><small>Your balance</small><strong class="${c.net < 0 ? 'neg' : ''}">${c.net < 0 ? fmt(-c.net) : fmt(c.net)}</strong></div>
        <div class="glance-note">${c.net < 0 ? 'Still to pay' : c.net > 0 ? 'To be paid back to you' : 'All settled'}</div>
      </div>${open}</section>`;
  }

  function viewHome() {
    const next = nextMatchCard();
    return `
      ${pageHead('Home', `${leagueName()} · ${state.players.length} player${state.players.length === 1 ? '' : 's'}`)}
      ${todoCard()}
      ${next || `<section class="panel empty-next"><span class="pp-ico">${icon('<rect x="3" y="4" width="18" height="18" rx="2"/><path d="M16 2v4M8 2v4M3 10h18"/>', 22)}</span>
        <div class="pp-text"><strong>No upcoming match yet</strong><small>${isAdmin() ? 'Add one on the Days tab and it shows here with a countdown.' : 'When the next match is added, it shows here with a countdown.'}</small></div></section>`}
      ${moneyGlance()}
      ${pollsPanel()}
      ${fixturesPanel()}
      ${galleryStrip()}`;
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
    const admin = isAdmin();
    const tour = C.tournamentStats(state);
    const count = C.leagueMatchCount(state);
    const played = count - tour.unplayed;
    const xi = state.settings.maxPerMatch;
    const out = state.players.length - tour.n;
    const unit = esc(cur().trim());

    const advance = C.advancePerPlayer(state);
    const pending = state.players.filter(p => advanceNow(p, C.playerCalc(state, p, null, fmt).net) > 0).length;
    const advStatus = advance > 0 ? (pending ? `${pending} still to pay it` : 'Everyone has paid it') : 'No advance asked';
    const suggested = C.advanceShare(state);

    const money = (key, value, placeholder, label) => `<span class="money-input"><em>${unit}</em>
      <input type="text" inputmode="decimal" placeholder="${placeholder}" value="${esc(value)}" data-input="${key}" data-focus="${key}" aria-label="${label}"></span>`;
    const tile = (label, body, note, cls = '') => `<div class="lg-tile ${cls}"><span class="lg-label">${label}</span>${body}<small>${note}</small></div>`;

    return `<section class="tour-panel">
      <div class="lg-head">
        <div class="lg-title">
          <span class="day-kind">League · whole series</span>
          ${admin
            ? `<input class="lg-name" type="text" placeholder="League name" value="${esc(state.tournament.name)}" maxlength="80" data-input="tournament-name" data-focus="tour-name" aria-label="League name">`
            : `<h3>${esc(leagueName())}</h3>`}
        </div>
        <div class="lg-progress" title="${played} of ${count} matches played">
          <span class="lg-pill">${played} of ${count} played</span>
          <span class="lg-segs">${Array.from({ length: count }, (_, i) => `<i class="${i < played ? 'on' : ''}"></i>`).join('')}</span>
        </div>
      </div>

      <div class="lg-tiles">
        ${tile('League fee',
          admin ? money('tournament-fee', state.tournament.fee, 'e.g. 65000', 'League fee') : `<strong>${tour.fee > 0 ? fmt(tour.fee) : '—'}</strong>`,
          'Total for the series')}
        ${tile('League matches',
          admin ? `<input class="field lg-count" type="text" inputmode="numeric" value="${esc(state.tournament.matchCount)}" data-input="tournament-matches" data-focus="tour-matches" aria-label="Matches the league fee covers">` : `<strong>${count}</strong>`,
          tour.fee > 0 ? `${fmt(tour.perMatch)} per match` : 'Set the league fee')}
        ${tile('Advance each',
          admin ? money('tournament-advance', state.tournament.advance, suggested > 0 ? suggested : '0', 'Advance per player') : `<strong>${advance > 0 ? fmt(advance) : '—'}</strong>`,
          advStatus)}
        ${tile('Each player per match',
          `<strong>${tour.fee > 0 ? fmt(C.round2(tour.perMatch / xi)) : '—'}</strong>`,
          tour.fee > 0 ? `${fmt(tour.perMatch)} ÷ ${xi} in the XI` : '—', 'lg-result')}
      </div>

      <p class="lg-note">${icon('<circle cx="12" cy="12" r="10"/><path d="M12 16v-4M12 8h.01"/>', 16)}
        <span>Each match is paid by the XI who played it, not the 12th man. Matches not played yet are shared by everyone taking part${out ? ` (${out} left out on the Players page)` : ''}.</span></p>
    </section>`;
  }

  function viewDays() {
    const admin = isAdmin();
    const stats = C.allStats(state);
    const max = state.settings.maxPerMatch;
    const card = d => {
      const s = stats[d.id];
      const items = C.dayExpenses(state, d.id).filter(e => e.item || C.num(e.amount) > 0);
      const count = d.type === 'match' ? `${s.n} / ${max} played${state.twelfth[d.id] ? ' + 12th' : ''}` : `${s.n} arrived`;
      const share = s.n > 0 && s.amount > 0 ? `${fmt(s.share)} each` : '—';
      const tour = d.type === 'match' ? C.tournamentStats(state, stats) : null;
      const leagueEach = tour && tour.fee > 0 && s.n > 0 ? `<div class="amount league-each"><span>League share</span><strong>${fmt(C.round2(tour.perMatch / s.n))} each</strong></div>` : '';
      const status = d.test ? `<span class="match-status test">${icon('<path d="M9 3h6M10 3v6l-5 9a2 2 0 0 0 2 3h10a2 2 0 0 0 2-3l-5-9V3"/>', 14)} Test match · never charged</span>` : d.type === 'match' ? (d.finished
        ? `<span class="match-status done">${icon('<path d="M20 6 9 17l-5-5"/>', 14)} Finished · charged</span>`
        : `<span class="match-status open">${icon('<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>', 14)} Not charged yet</span>`) : '';
      const removable = admin && !d.test && (d.type === 'match' || state.trainings.length > 1);
      return `<article class="day-card ${d.type}">
        <header>
          <span class="day-kind">${d.test ? 'Test match' : d.type === 'match' ? 'Match' : 'Training'}</span>
          <h3>${d.label}</h3>
          ${removable ? `<button class="icon-btn danger" data-action="remove-${d.type}" data-id="${esc(d.id)}" aria-label="Remove ${d.label}">${X_ICON}</button>` : ''}
          ${d.type === 'match' && (!admin || d.test) && matchInfo(d) ? `<p class="match-info">${esc(matchInfo(d))}</p>` : ''}
          ${status}
        </header>
        ${d.type === 'match' && admin && !d.test ? `<div class="match-fields">
          <input class="field" type="date" value="${esc(d.date)}" data-input="match-date" data-id="${esc(d.id)}" aria-label="Match date">
          <input class="field" type="time" value="${esc(d.time)}" data-input="match-time" data-id="${esc(d.id)}" aria-label="Kick-off time (leave empty until confirmed)" title="Kick-off time — leave empty until it is confirmed">
          <input class="field" type="text" placeholder="Opponent" value="${esc(d.opponent)}" maxlength="80" list="hpl-teams" data-input="match-opponent" data-id="${esc(d.id)}" data-focus="mopp-${esc(d.id)}" aria-label="Opponent">
          <input class="field" type="text" placeholder="Venue" value="${esc(d.venue)}" maxlength="80" data-input="match-venue" data-id="${esc(d.id)}" data-focus="mven-${esc(d.id)}" aria-label="Venue">
        </div>` : ''}
        ${d.type === 'training' ? (admin
          ? `<input class="field" type="text" placeholder="Description (e.g. Nets at club)" value="${esc(d.desc)}" data-input="training-desc" data-id="${esc(d.id)}" data-focus="desc-${esc(d.id)}" maxlength="200">`
          : `<p class="desc">${esc(d.desc) || '<span class="muted">No description</span>'}</p>`) : ''}
        <div class="amount"><span>Total spent</span><strong>${s.amount > 0 ? fmt(s.amount) : '—'}</strong></div>
        ${leagueEach}
        ${items.length ? `<ul class="mini-items">${items.slice(0, 3).map(e => `<li><span>${esc(e.item) || 'Item'}<small>${esc(playerName(e.paidBy))}</small></span><span>${fmt(C.num(e.amount))}</span></li>`).join('')}
          ${items.length > 3 ? `<li class="more">+${items.length - 3} more</li>` : ''}</ul>` : '<p class="muted mini-empty">No expenses yet</p>'}
        <div class="day-meta"><span>${count}</span><span class="share">${share}</span></div>
        <button class="btn btn-block ${d.type === 'match' ? 'btn-gold' : 'btn-primary'}" data-action="open-day" data-id="${esc(d.id)}">
          ${admin ? 'Expenses & attendance' : 'See details'}</button>
      </article>`;
    };
    const all = C.days(state);
    const folded = readStore(COLLAPSED_KEY) || {};
    const trainings = all.filter(d => d.type === 'training');
    const matches = all.filter(d => d.type === 'match' && !d.test && matchVisible(d));
    const tests = all.filter(d => d.test && matchVisible(d));

    return `
      ${pageHead('Days', 'Each day’s expenses are split among the players who attended. Whoever paid for an item gets it credited.',
        admin ? `<button class="btn btn-primary" data-action="add-training">${icon('<path d="M12 5v14M5 12h14"/>', 18)} Add training</button>` : '')}
      ${warningBanner()}
      ${tournamentPanel()}
      <section class="day-group ${folded.training ? 'folded' : ''}">
        <div class="group-row">${groupHead('training', 'Trainings', trainings.length)}</div>
        ${folded.training ? '' : `<div class="day-grid">${trainings.map(card).join('')}</div>`}
      </section>
      <section class="day-group ${folded.match ? 'folded' : ''}">
        <div class="group-row">
          ${groupHead('match', 'Matches', matches.length)}
          ${admin ? `<button class="btn btn-gold" data-action="add-match">${icon('<path d="M12 5v14M5 12h14"/>', 18)} Add match</button>
          <button class="btn btn-ghost" data-action="add-test-matches" title="Adds 5 matches with a random playing XI and random stats, for trying the app out">${DICE_ICON} Add 5 test matches</button>` : ''}
        </div>
        ${folded.match ? '' : matches.length ? `<div class="day-grid matches">${matches.map(card).join('')}</div>`
          : `<p class="muted empty-matches">No matches yet.${admin ? ' Add one on match day.' : ''}</p>`}
      </section>
      ${tests.length ? `<section class="day-group ${folded.test ? 'folded' : ''}">
        <div class="group-row">
          ${groupHead('test', 'Test matches', tests.length)}
          ${admin ? `<button class="btn btn-danger" data-action="remove-test-matches">${X_ICON} Delete test matches</button>` : ''}
        </div>
        ${folded.test ? '' : `<div class="day-grid matches">${tests.map(card).join('')}</div>`}
      </section>` : ''}`;
  }

  // A Days section heading that folds its cards away (remembered on this device).
  function groupHead(type, title, count) {
    const open = !(readStore(COLLAPSED_KEY) || {})[type];
    return `<button class="group-head ${type}" data-action="fold-group" data-type="${type}" aria-expanded="${open}"
      title="${open ? 'Minimise' : 'Expand'} ${title.toLowerCase()}">${title} <span>${count}</span>
      <i class="fold-icon">${icon(open ? '<path d="m18 15-6-6-6 6"/>' : '<path d="m6 9 6 6 6-6"/>', 18)}</i></button>`;
  }

  function formatMatchDate(iso) {
    const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso || '');
    if (!m) return '';
    return new Date(+m[1], m[2] - 1, +m[3]).toLocaleDateString('en-GB', { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' });
  }

  // "Sat, 24 Oct 2026 · vs Lahore Lions · Model Town" — whatever is set.
  function matchInfo(d) {
    return [formatMatchDate(d.date) + (d.date && d.time ? ', ' + fmtClock(d.time) : ''), d.opponent && 'vs ' + d.opponent, d.venue].filter(Boolean).join(' · ');
  }

  function tourToggle(p) {
    const on = C.inTournament(p);
    return `<button class="tog tour-tog ${on ? 'on' : ''} ${isAdmin() ? '' : 'ro'}" data-action="toggle-tournament" data-id="${esc(p.id)}"
      ${isAdmin() ? '' : 'disabled'} aria-pressed="${on}" title="Taking part in the league">${on ? 'In' : 'Out'}</button>`;
  }

  // ---------- stats (admin) ----------
  const SR = (runs, balls) => { const v = C.strikeRate(runs, balls); return v == null ? '—' : v.toFixed(2); };
  const ECON = (runs, balls) => { const v = C.economy(runs, balls); return v == null ? '—' : v.toFixed(2); };

  function viewStats() {
    const ms = C.matches(state).filter(statsMatchVisible);
    if (statsSel !== 'league' && !ms.some(m => m.id === statsSel)) statsSel = 'league';
    const seg = `<div class="seg" role="tablist" aria-label="Stats view">
      <button class="seg-btn ${statsSel === 'league' ? 'on' : ''}" data-action="stats-sel" data-id="league">League</button>
      ${ms.map(m => `<button class="seg-btn ${statsSel === m.id ? 'on' : ''}" data-action="stats-sel" data-id="${esc(m.id)}">${m.label}${m.date ? `<small>${esc(formatDate(m.date).replace(/ \d{4}$/, ''))}</small>` : ''}</button>`).join('')}
    </div>`;
    const body = statsSel === 'league' ? leagueStatsView() : matchStatsView(ms.find(m => m.id === statsSel));
    const sub = 'The Hundred: 5-ball overs, 20 overs an innings.' + (isAdmin() ? (state.settings.statsVisibility === 'all' ? ' Players can see these too.' : ' Only you can see these — change it in Settings.') : '');
    return `${pageHead('Stats', sub)}
      ${seg}${body}`;
  }

  function stepper(m, pid, key, label, value) {
    if (!isAdmin()) return `<div class="step"><span class="step-label">${label}</span><span class="step-ro">${value}</span></div>`;
    const attrs = `data-m="${esc(m)}" data-p="${esc(pid)}" data-k="${key}"`;
    return `<div class="step">
      <span class="step-label">${label}</span>
      <span class="step-ctl">
        <button type="button" data-action="stat-step" ${attrs} data-d="-1" aria-label="${label} minus one" ${value > 0 ? '' : 'disabled'}>−</button>
        <input type="text" inputmode="numeric" value="${value}" data-input="stat" ${attrs} data-focus="st-${esc(m)}-${esc(pid)}-${key}" aria-label="${label}">
        <button type="button" data-action="stat-step" ${attrs} data-d="1" aria-label="${label} plus one">+</button>
      </span>
    </div>`;
  }

  function statCard(d, p, pos, twelfthMan) {
    const e = C.statOf(state, d.id, p.id);
    const runs = C.runsOf(e);
    const st = (key, label) => stepper(d.id, p.id, key, label, e[key]);
    const bat = C.batted(e) ? `${runs}${e.out ? '' : '*'} (${e.balls}) · SR ${SR(runs, e.balls)}` : 'Did not bat';
    const bowl = C.bowled(e) ? `${e.wickets}/${C.concededOf(e)} (${C.oversOf(e.bowlBalls)})` : '';
    const field = [e.catches && `${e.catches} ct`, e.drops && `${e.drops} drop${e.drops > 1 ? 's' : ''}`].filter(Boolean).join(' · ');
    const tooFew = C.shotsOf(e) > e.balls;
    return `<article class="st-card ${twelfthMan ? 'twelfth' : ''}">
      <header class="st-head">
        <span class="bat-pos ${pos <= 2 && !twelfthMan ? 'opener' : ''}">${twelfthMan ? '12' : pos}</span>
        <span class="pname"><strong>${esc(p.name)}</strong><small>${twelfthMan ? '12th man · fielding only' : esc([bat, bowl, field].filter(Boolean).join('  ·  '))}</small></span>
      </header>
      ${twelfthMan ? '' : `<div class="st-group bat">
        <div class="st-title"><span>Batting</span><strong>${runs} runs · SR ${SR(runs, e.balls)}</strong>
          <button class="tog st-out ${e.out ? 'on' : ''} ${isAdmin() ? '' : 'ro'}" data-action="stat-out" data-m="${esc(d.id)}" data-p="${esc(p.id)}" aria-pressed="${e.out}" ${isAdmin() ? '' : 'disabled'}>${e.out ? 'Out' : 'Not out'}</button></div>
        <div class="st-steps">${st('balls', 'Balls')}${st('ones', '1s')}${st('twos', '2s')}${st('threes', '3s')}${st('fours', '4s')}${st('sixes', '6s')}</div>
        ${tooFew ? '<p class="st-warn">More scoring shots than balls faced.</p>' : ''}
      </div>
      <div class="st-group bowl">
        <div class="st-title"><span>Bowling</span><strong>${C.oversOf(e.bowlBalls)} ov · ${e.wickets}/${C.concededOf(e)} · Econ ${ECON(C.concededOf(e), e.bowlBalls)}</strong></div>
        <div class="st-steps">${st('bowlBalls', 'Balls')}${st('bowlRuns', 'Runs off bat')}${st('wickets', 'Wickets')}${st('wides', 'Wides')}${st('noBalls', 'No-balls')}</div>
        ${C.extrasOf(e) > 0 ? `<p class="st-extra">Extras ${C.extrasOf(e)} · conceded ${e.bowlRuns} + ${C.extrasOf(e)} = ${C.concededOf(e)}</p>` : ''}
      </div>`}
      <div class="st-group field">
        <div class="st-title"><span>Fielding</span></div>
        <div class="st-steps">${st('catches', 'Catches')}${st('drops', 'Drops')}${st('misfields', 'Misfields')}${st('goodFielding', 'Good fielding')}</div>
      </div>
    </article>`;
  }

  function matchStatsView(d) {
    const xi = battingOrderOf(d.id);
    const twelfth = findPlayer(state.twelfth[d.id]);
    const info = matchInfo(d);
    if (!xi.length) {
      return `<div class="empty st-empty"><p>Pick the XI for ${d.label} first, then add its stats here.</p>
        <button class="btn btn-gold" data-action="open-day" data-id="${esc(d.id)}">Pick the XI</button></div>`;
    }
    const all = xi.concat(twelfth ? [twelfth] : []).map(p => C.statOf(state, d.id, p.id));
    const sum = k => all.reduce((x, e) => x + e[k], 0);
    const runs = all.reduce((x, e) => x + C.runsOf(e), 0);
    const outs = all.filter(e => e.out).length;
    const conceded = all.reduce((x, e) => x + C.concededOf(e), 0);
    return `<section class="st-match">
      <div class="st-summary">
        <div><span>${d.label}${info ? ` · ${esc(info)}` : ''}</span>
          <strong>${runs}/${outs}</strong><small>${sum('balls')} balls (${C.oversOf(sum('balls'))} ov) · SR ${SR(runs, sum('balls'))}</small></div>
        <div><span>Bowling</span><strong>${sum('wickets')}/${conceded}</strong><small>${C.oversOf(sum('bowlBalls'))} of 20 ov · Econ ${ECON(conceded, sum('bowlBalls'))} · Extras ${sum('wides') + sum('noBalls')} (${sum('wides')} wd, ${sum('noBalls')} nb)</small></div>
        <div><span>Fielding</span><strong>${sum('catches')} ct</strong><small>${sum('drops')} dropped · ${sum('misfields')} misfields · ${sum('goodFielding')} good</small></div>
      </div>
      ${isAdmin() ? `<div class="st-tools"><p class="muted st-hint">Tapping + on a 1, 2, 3, 4 or 6 also adds a ball faced. A bowler’s balls are legal balls; wides and no-balls count as extras in runs conceded.</p>
        <button class="btn btn-ghost" data-action="random-stats" data-id="${esc(d.id)}">${DICE_ICON} Random stats</button></div>` : ''}
      <div class="st-list">
        ${xi.map((p, i) => statCard(d, p, i + 1, false)).join('')}
        ${twelfth ? statCard(d, twelfth, 12, true) : ''}
      </div>
    </section>`;
  }

  function leagueStatsView() {
    const all = C.leagueStats(state, statsMatchVisible).filter(t => t.matches > 0);
    if (!all.length) {
      return `<div class="empty st-empty"><p>No stats yet. Pick a match above and add them as you play — the season totals build up here.</p></div>`;
    }
    const top = (list, key, fmtFn) => { const t = list.slice().sort((a, b) => b[key] - a[key])[0]; return t && t[key] > 0 ? { t, v: fmtFn(t) } : null; };
    const srList = all.filter(t => t.balls >= 20 && t.strikeRate != null);
    const leaders = [
      { label: 'Most runs', x: top(all, 'runs', t => `${t.runs}`) },
      { label: 'Most wickets', x: top(all, 'wickets', t => `${t.wickets}`) },
      { label: 'Most catches', x: top(all, 'catches', t => `${t.catches}`) },
      { label: 'Best strike rate', note: 'min 20 balls', x: top(srList, 'strikeRate', t => SR(t.runs, t.balls)) }
    ];
    const name = t => `<td class="st-name"><button class="pl-name" data-action="open-profile" data-id="${esc(t.player.id)}">${esc(t.player.name)}</button></td>`;
    const batting = all.filter(t => t.innings > 0).sort((a, b) => b.runs - a.runs || (b.strikeRate || 0) - (a.strikeRate || 0));
    const bowling = all.filter(t => t.bowlBalls > 0 || t.conceded > 0).sort((a, b) => b.wickets - a.wickets || (a.economy || 99) - (b.economy || 99));
    const fielding = all.filter(t => t.catches + t.drops + t.misfields + t.goodFielding > 0).sort((a, b) => b.catches - a.catches || a.drops - b.drops);
    const table = (title, head, rows) => `<section class="panel st-table">
      <div class="panel-head"><h3>${title}</h3></div>
      ${rows.length ? `<div class="table-wrap"><table class="sheet-table"><thead><tr>${head.map((h, i) => `<th ${i === 0 ? 'class="st-name"' : ''}>${h}</th>`).join('')}</tr></thead>
        <tbody>${rows.join('')}</tbody></table></div>` : '<p class="muted">Nothing yet.</p>'}
    </section>`;
    const tests = state.matches.filter(m => m.test && statsMatchVisible(m)).length;
    return `${tests ? `<p class="test-note">These totals include ${tests} generated test match${tests === 1 ? '' : 'es'}. Delete them from the Days tab when you are done trying things out.</p>` : ''}
      <div class="st-leaders">${leaders.map(l => `<div class="stat ${l.x ? 'pos' : ''}"><span>${l.label}</span>
        <strong>${l.x ? l.x.v : '—'}</strong><small>${l.x ? esc(l.x.t.player.name) : (l.note || 'No one yet')}</small></div>`).join('')}</div>
      ${table('Batting', ['Player', 'M', 'Inns', 'Runs', 'Balls', 'SR', 'HS', 'Avg', '4s', '6s', '1s', '2s', '3s'], batting.map(t => `<tr>${name(t)}
        <td>${t.matches}</td><td>${t.innings}</td><td class="strong">${t.runs}</td><td>${t.balls}</td><td>${SR(t.runs, t.balls)}</td>
        <td>${t.high ? t.high.runs + (t.high.out ? '' : '*') : '—'}</td><td>${t.average == null ? '—' : t.average.toFixed(2)}</td>
        <td>${t.fours}</td><td>${t.sixes}</td><td>${t.ones}</td><td>${t.twos}</td><td>${t.threes}</td></tr>`))}
      ${table('Bowling', ['Player', 'Overs', 'Runs', 'Wkts', 'Econ', 'Wd', 'NB', 'Best'], bowling.map(t => `<tr>${name(t)}
        <td>${C.oversOf(t.bowlBalls)}</td><td>${t.conceded}</td><td class="strong">${t.wickets}</td><td>${ECON(t.conceded, t.bowlBalls)}</td>
        <td>${t.wides}</td><td>${t.noBalls}</td>
        <td>${t.best ? `${t.best.wickets}/${t.best.runs}` : '—'}</td></tr>`))}
      ${table('Fielding', ['Player', 'Catches', 'Drops', 'Misfields', 'Good fielding'], fielding.map(t => `<tr>${name(t)}
        <td class="strong">${t.catches}</td><td>${t.drops}</td><td>${t.misfields}</td><td>${t.goodFielding}</td></tr>`))}`;
  }

  // ---------- team activity: man of the match, polls, predictions, captain's notes ----------
  let engage = null;                 // GET /api/engage: { mom, polls, predictions, leaderboard, me }
  const pollDraft = { q: '', opts: ['Yes', 'No', 'Maybe'] };
  const notesCache = {};             // matchId -> { fieldPlan, battingPlan, opposition, updatedBy, updatedAt }
  const notesDraft = {};             // matchId -> edited copy
  const notesBusy = {};

  async function loadEngage() {
    let next;
    try { next = await api('GET', '/api/engage'); } catch { return; }
    if (JSON.stringify(next) === JSON.stringify(engage)) return;
    engage = next;
    render();
  }

  async function engageCall(method, path, body, done) {
    try {
      await api(method, path, body);
      await loadEngage();
      if (done) toast(done);
    } catch (e) { toast(e.message || 'That didn’t work.'); }
  }

  const RESULTS = [['win', 'Won'], ['loss', 'Lost'], ['tie', 'Tie']];
  const resultChip = m => {
    const r = RESULTS.find(x => x[0] === m.result);
    return r ? `<span class="res-chip ${m.result}">${r[1]}</span>` : '';
  };
  const momRow = id => engage && engage.mom.find(x => x.matchId === id);
  const predRow = id => engage && engage.predictions.find(x => x.matchId === id);
  const canAct = () => !!myPlayer();
  // Shown wherever someone has to be a real squad member to take part (votes, predictions, polls, photos).
  const needName = what => `<span class="need-name">${what} <button class="chip" data-action="go-settings">Choose your name in Settings</button></span>`;
  const pctBar = (n, total) => `<span class="eg-bar"><i style="width:${total ? Math.round(n * 100 / total) : 0}%"></i></span>`;

  // Man of the match: players who played vote once finished; the admin closes the voting and the winner is announced.
  function momSection(m) {
    if (!m.finished || m.test || !engage) return '';
    const row = momRow(m.id) || { closed: false, mine: '', counts: {}, winners: [] };
    const xi = xiOf(m);
    const total = Object.values(row.counts || {}).reduce((a, b) => a + b, 0);
    let body;
    if (row.closed && row.winners.length) {
      body = row.winners.map(id => {
        const p = findPlayer(id);
        return p ? `<div class="mom-winner">${avatar(p, 52)}<span><strong>${esc(p.name)}</strong><small>${row.counts[id] || 0} vote${(row.counts[id] || 0) === 1 ? '' : 's'}</small></span><span class="mom-star">⭐</span></div>` : '';
      }).join('');
    } else {
      const me = myPlayer();
      body = canAct()
        ? `<p class="muted eg-note">${row.mine ? 'Your vote is in — you can change it until the admin announces the winner.' : 'Tap the player who stood out. Results show after you vote.'}</p>
          <div class="mom-list">${xi.filter(p => p.id !== me.id).map(p => `<button type="button" class="mom-opt ${row.mine === p.id ? 'on' : ''}" data-action="mom-vote" data-match="${esc(m.id)}" data-player="${esc(p.id)}" aria-pressed="${row.mine === p.id}">
              ${avatar(p, 36)}<span class="mom-name">${esc(p.name)}</span>
              ${row.counts && row.counts[p.id] != null ? `<span class="mom-n">${row.counts[p.id]}</span>` : ''}</button>`).join('')}</div>`
        : `<p class="muted eg-note">${isStaff() ? 'Players vote here once the match is finished.' : needName('To vote,')}</p>`;
      if (isStaff() && total) {
        body += `<div class="mom-tally">${Object.entries(row.counts).sort((a, b) => b[1] - a[1]).map(([id, n]) => { const p = findPlayer(id); return p ? `<div class="tally-row"><span>${esc(p.name)}</span>${pctBar(n, total)}<b>${n}</b></div>` : ''; }).join('')}</div>`;
      }
    }
    const admin = isAdmin() ? (row.closed
      ? `<button class="btn btn-ghost small" data-action="mom-reopen" data-match="${esc(m.id)}">Reopen voting</button>`
      : `<button class="btn btn-gold small" data-action="mom-close" data-match="${esc(m.id)}" ${total ? '' : 'disabled'}>Close voting &amp; announce winner</button>`) : '';
    return `<h3 class="sub gal-title">Man of the match</h3>${body}${admin ? `<div class="eg-actions">${admin}</div>` : ''}`;
  }

  // Who will win? Open until the match starts.
  function predictBlock(m, compact) {
    if (m.finished || m.test || !engage) return '';
    const row = predRow(m.id) || { locked: false, mine: '', counts: {} };
    const opp = m.opponent || 'Opponent';
    const total = (row.counts.win || 0) + (row.counts.loss || 0);
    const btn = (pick, label) => `<button type="button" class="pred-opt ${row.mine === pick ? 'on' : ''}" data-action="predict" data-match="${esc(m.id)}" data-pick="${pick}" ${row.locked || !canAct() || row.mine ? 'disabled' : ''} aria-pressed="${row.mine === pick}">${label}</button>`;
    const staff = isStaff();
    const identified = canAct();
    let note;
    if (row.mine) note = 'Locked in. A prediction can’t be changed.';
    else if (row.locked) note = 'Predictions are closed — the match has started.';
    else if (!identified && !staff) note = needName('To predict,');
    else if (!identified) note = 'Players can guess the winner until the match starts.';
    else note = 'Guess the winner — once you pick, it’s locked. One point for every right guess.';
    // Players see how the team guessed once the match has started; the admin and managers open the full list instead.
    const tally = total && row.locked && !staff ? `<div class="mom-tally">
        <div class="tally-row"><span>Red Falcons</span>${pctBar(row.counts.win || 0, total)}<b>${row.counts.win || 0}</b></div>
        <div class="tally-row"><span>${esc(opp)}</span>${pctBar(row.counts.loss || 0, total)}<b>${row.counts.loss || 0}</b></div></div>` : '';
    const list = staff ? `<div class="eg-actions"><button class="btn btn-ghost small" data-action="open-predictions" data-match="${esc(m.id)}">${icon(PERSON_PATH, 16)} See who predicted${total ? ` (${total})` : ''}</button></div>` : '';
    return `<div class="pred ${compact ? 'compact' : ''}">
      <div class="pred-head"><strong>Who will win?</strong><small class="muted">${note}</small></div>
      <div class="pred-btns">${btn('win', 'Red Falcons')}${btn('loss', esc(opp))}</div>${tally}${list}</div>`;
  }

  // Admin and managers: who guessed what for one match (and who was right, once it is finished), on its own screen.
  function sheetPredictions() {
    const m = C.matches(state).find(x => x.id === sheet.id);
    if (!m || !isStaff()) return '';
    const row = predRow(m.id) || { picks: [], finished: false, result: '' };
    const picks = row.picks || [];
    const opp = m.opponent || 'Opponent';
    const names = pick => picks.filter(x => x.pick === pick).map(x => findPlayer(x.playerId)).filter(Boolean);
    const mark = pick => {
      if (!(row.finished && (row.result === 'win' || row.result === 'loss'))) return '';
      const right = pick === row.result;
      return `<b class="pr-mark ${right ? 'ok' : 'no'}">${right ? '✓ Right' : '✗ Wrong'}</b>`;
    };
    const group = (pick, label) => {
      const ps = names(pick);
      return `<div class="pick-group"><b>${label} · ${ps.length}</b>${ps.length
        ? `<ul class="pick-results">${ps.map(p => `<li>${avatar(p, 28)}<span class="pr-name">${esc(p.name)}</span>${mark(pick)}</li>`).join('')}</ul>`
        : '<p class="muted">Nobody.</p>'}</div>`;
    };
    const waiting = state.players.filter(p => C.inTournament(p) && !picks.some(x => x.playerId === p.id));
    const res = RESULTS.find(r => r[0] === m.result);
    return `<header class="sheet-head">
        <div><span class="day-kind">Predictions</span><h2>${esc(m.label)}${m.opponent ? ` · vs ${esc(m.opponent)}` : ''}</h2>
          <p class="muted">${picks.length} player${picks.length === 1 ? '' : 's'} predicted${res ? ` · result: ${res[1]}` : ''}. Only you and managers see this.</p></div>
        <button class="icon-btn" data-action="sheet-back" aria-label="Close">${X_ICON}</button>
      </header>
      <div class="sheet-body pick-sheet">
        ${group('win', 'Red Falcons win')}
        ${group('loss', `${esc(opp)} win`)}
        ${waiting.length ? `<div class="pick-group dim"><b>No prediction yet · ${waiting.length}</b><div>${waiting.map(p => `<span class="pick-chip off">${esc(p.name)}</span>`).join('')}</div></div>` : ''}
        ${m.finished && !m.result ? '<p class="muted eg-note">Set the match result (Won, Lost or Tie) to score these.</p>' : ''}
      </div>`;
  }

  // On a finished match the admin and managers get a button to the list of predictions.
  function predictResults(m) {
    if (!isStaff() || !m.finished || m.test || !engage) return '';
    const row = predRow(m.id);
    if (!row || !row.finished || !(row.picks || []).length) return '';
    return `<h3 class="sub gal-title">Predictions</h3>
      <div class="eg-actions"><button class="btn btn-ghost small" data-action="open-predictions" data-match="${esc(m.id)}">${icon(PERSON_PATH, 16)} See who predicted (${row.picks.length})</button></div>`;
  }
  const predictSection = m => (m.finished || m.test || !engage) ? '' : `<h3 class="sub gal-title">Prediction</h3>${predictBlock(m, false)}`;

  // The captain's plan for a match: the admin and managers only.
  function notesSection(m) {
    if (!isStaff() || m.test) return '';
    if (!notesCache[m.id] && !notesBusy[m.id]) {
      notesBusy[m.id] = true;
      api('GET', `/api/engage/notes/${encodeURIComponent(m.id)}`).then(n => { notesCache[m.id] = n; delete notesBusy[m.id]; render(); }).catch(() => { delete notesBusy[m.id]; });
    }
    const n = notesDraft[m.id] || notesCache[m.id];
    if (!n) return `<h3 class="sub gal-title">Captain’s notes</h3><p class="muted">Loading…</p>`;
    const field = (key, label, hint) => `<label class="ann-field"><span>${label}</span>
      <textarea class="field" rows="3" maxlength="1500" placeholder="${hint}" data-input="note-${key}" data-match="${esc(m.id)}" data-focus="note-${key}-${esc(m.id)}">${esc(n[key])}</textarea></label>`;
    return `<h3 class="sub gal-title">Captain’s notes <span class="note-lock">${icon('<rect x="4" y="11" width="16" height="10" rx="2"/><path d="M8 11V7a4 4 0 0 1 8 0v4"/>', 13)} Only admin and managers</span></h3>
      ${field('fieldPlan', 'Field plan', 'e.g. two slips, long-on back')}
      ${field('battingPlan', 'Batting plan', 'e.g. Babar and Rizwan open, hit out from over 15')}
      ${field('opposition', 'About the opposition', 'e.g. strong spinners, weak in the field')}
      <div class="eg-actions"><button class="btn btn-gold small" data-action="notes-save" data-match="${esc(m.id)}">Save notes</button>
        ${n.updatedAt ? `<small class="muted">Last saved by ${esc(n.updatedBy)} · ${ago(n.updatedAt)}</small>` : ''}</div>`;
  }

  const matchExtras = m => `${momSection(m)}${predictResults(m)}${predictSection(m)}${notesSection(m)}${matchGallery(m)}`;

  // Quick polls on Summary: answer once, see results after.
  function pollCard(p) {
    const can = canAct() || isStaff();
    const total = p.total;
    return `<article class="poll ${p.closed ? 'closed' : ''}">
      <div class="poll-head"><strong>${esc(p.question)}</strong><small class="muted">${esc(p.author)} · ${ago(p.createdAt)}${p.closed ? ' · Closed' : ''}</small></div>
      <div class="poll-opts">${p.options.map((o, i) => `<button type="button" class="poll-opt ${p.mine === i ? 'on' : ''}" data-action="poll-vote" data-id="${esc(p.id)}" data-i="${i}" ${p.closed || !can ? 'disabled' : ''} aria-pressed="${p.mine === i}">
          <span class="poll-text">${esc(o.text)}</span>${o.count != null ? `${pctBar(o.count, total)}<b>${o.count}</b>` : ''}</button>`).join('')}</div>
      ${!can ? `<small class="muted">${needName('To answer,')}</small>` : p.mine == null && !p.closed ? '<small class="muted">Answer to see how the team voted.</small>' : `<small class="muted">${total} answer${total === 1 ? '' : 's'}</small>`}
    </article>`;
  }

  function pollsPanel() {
    const list = (engage ? engage.polls : []).filter(p => !p.closed).concat((engage ? engage.polls : []).filter(p => p.closed).slice(0, 1)).slice(0, 4);
    if (!list.length) return '';
    return `<section class="panel polls-panel"><div class="panel-head"><h3>Quick polls</h3><p class="muted">Tap an answer. Results show once you have voted.</p></div>${list.map(pollCard).join('')}</section>`;
  }

  function leaguePanel() {
    const board = engage ? engage.leaderboard.filter(r => findPlayer(r.playerId)) : [];
    if (!board.length) return '';
    return `<section class="panel board-panel"><div class="panel-head"><h3>Prediction league</h3><p class="muted">One point for every match winner you guessed right.</p></div>
      <ol class="board">${board.slice(0, 5).map((r, i) => { const p = findPlayer(r.playerId); return `<li class="${i === 0 ? 'top' : ''}"><span class="board-n">${i + 1}</span>${avatar(p, 36)}<span class="board-name">${esc(p.name)}</span><span class="board-pts"><b>${r.points}</b><small>of ${r.played}</small></span></li>`; }).join('')}</ol></section>`;
  }

  // For the admin and managers on the Announce tab: ask a poll, then close or delete it.
  function pollsAdmin() {
    const mine = p => realAdmin() || p.authorId === myManagerId;
    const list = engage ? engage.polls : [];
    return `<section class="panel ann-composer" id="poll-composer">
        <div class="panel-head"><h3>Ask a quick poll</h3><p class="muted">Everyone gets a notification. Players answer once and see the result after they vote.</p></div>
        <label class="ann-field"><span>Question</span>
          <input class="field" type="text" maxlength="120" value="${esc(pollDraft.q)}" placeholder="e.g. Nets on Friday evening?" data-input="poll-q" data-focus="poll-q"></label>
        <div class="poll-draft">${pollDraft.opts.map((o, i) => `<input class="field" type="text" maxlength="40" value="${esc(o)}" placeholder="Answer ${i + 1}" data-input="poll-opt" data-i="${i}" data-focus="poll-opt-${i}" aria-label="Answer ${i + 1}">`).join('')}</div>
        <div class="ann-actions">
          <button class="btn btn-gold" data-action="poll-ask">${icon('<path d="M22 2 11 13M22 2l-7 20-4-9-9-4z"/>', 18)} Ask</button>
          ${pollDraft.opts.length < 5 ? '<button class="btn btn-ghost" data-action="poll-add-opt">Add an answer</button>' : ''}
        </div>
      </section>
      <section class="panel"><div class="panel-head"><h3>Polls</h3></div>
        ${list.length ? list.map(p => `<article class="note-card"><div class="note-head"><strong>${esc(p.question)}</strong>
            <small>${p.closed ? 'Closed' : 'Open'} · ${p.total} answer${p.total === 1 ? '' : 's'} · by ${esc(p.author)}</small></div>
          ${p.options.map(o => `<div class="tally-row"><span>${esc(o.text)}</span>${pctBar(o.count || 0, p.total)}<b>${o.count || 0}</b></div>`).join('')}
          ${p.voters.length ? `<p class="muted poll-voters">${p.voters.map(v => `${esc(v.name)}: ${esc(v.option)}`).join(' · ')}</p>` : ''}
          ${mine(p) ? `<div class="note-actions"><button class="btn btn-ghost small" data-action="poll-toggle" data-id="${esc(p.id)}">${p.closed ? 'Reopen' : 'Close'}</button>
            <button class="btn btn-ghost small" data-action="poll-delete" data-id="${esc(p.id)}">Delete</button></div>` : ''}</article>`).join('') : '<p class="muted season-empty">No polls yet.</p>'}
      </section>`;
  }

  // ---------- match photos ----------
  let gallery = null;               // [{ id, matchId, byId, by, createdAt }], newest first
  let galleryTarget = '';
  const matchPhotoSrc = id => `${API}/api/match-photos/${encodeURIComponent(id)}`;
  const photosOf = matchId => (gallery || []).filter(p => p.matchId === matchId);
  const canAddPhotos = () => isAdmin() || isManager() || !!myPlayer();
  const canRemovePhoto = p => isAdmin() || (isManager() ? p.byId === myManagerId : (myPlayer() || {}).id === p.byId);

  async function loadGallery() {
    let next;
    try { next = await api('GET', '/api/match-photos?limit=500'); } catch { return; }
    if (JSON.stringify(next) === JSON.stringify(gallery)) return;
    gallery = next;
    render();
  }

  // Shrinks to at most 1280 px on the long side, lowering the quality until it is small enough to send.
  async function shrinkMatchPhoto(file) {
    const bmp = await createImageBitmap(file);
    const k = Math.min(1, 1280 / Math.max(bmp.width, bmp.height));
    const canvas = document.createElement('canvas');
    canvas.width = Math.max(1, Math.round(bmp.width * k));
    canvas.height = Math.max(1, Math.round(bmp.height * k));
    canvas.getContext('2d').drawImage(bmp, 0, 0, canvas.width, canvas.height);
    for (const q of [0.82, 0.7, 0.55, 0.4]) {
      const blob = await new Promise(r => canvas.toBlob(r, 'image/jpeg', q));
      if (blob && blob.size <= 480000) return blob;
    }
    throw new Error('That picture is too big.');
  }

  async function uploadMatchPhotos(files) {
    const matchId = galleryTarget;
    if (!matchId || !files.length) return;
    let done = 0;
    for (const f of files) {
      try {
        toast(files.length > 1 ? `Uploading photo ${done + 1} of ${files.length}…` : 'Uploading photo…');
        const headers = { 'Content-Type': 'image/jpeg' };
        if (session && session.token) headers['X-Admin-Token'] = session.token;
        if (session && session.managerToken) headers['X-Manager-Token'] = session.managerToken;
        const me = myPlayer();
        if (me) headers['X-Viewer'] = me.id;
        const res = await fetch(`${API}/api/matches/${encodeURIComponent(matchId)}/photos`, { method: 'POST', headers, body: await shrinkMatchPhoto(f) });
        const data = await res.json().catch(() => ({}));
        if (!res.ok) throw new Error(data.error || `Request failed (${res.status})`);
        gallery = [data, ...(gallery || [])];
        done++;
        render();
      } catch (e) { toast(e.message || 'Could not upload that photo.'); }
    }
    if (done) toast(done === 1 ? 'Photo added.' : `${done} photos added.`);
  }

  // The photos of one match, shown at the foot of its details sheet.
  function matchGallery(m) {
    const list = photosOf(m.id);
    if (!list.length && !canAddPhotos()) return '';
    return `<h3 class="sub gal-title">Photos <span class="muted">${list.length || ''}</span></h3>
      ${list.length ? `<div class="gal-grid">${list.map(p => `<button type="button" class="gal-thumb" data-action="open-photo" data-id="${esc(p.id)}" aria-label="Photo by ${esc(p.by)}">
          <img src="${matchPhotoSrc(p.id)}" alt="" loading="lazy"></button>`).join('')}</div>` : '<p class="muted gal-empty">No photos yet. Be the first to add one.</p>'}
      ${canAddPhotos() ? `<button class="btn btn-ghost gal-add" data-action="gallery-add" data-match="${esc(m.id)}">${icon('<path d="M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z"/><circle cx="12" cy="13" r="4"/>', 18)} Add photos</button>`
        : `<p class="muted gal-empty">${needName('To add photos,')}</p>`}`;
  }

  // Latest photos across matches, on Summary.
  function galleryStrip() {
    const list = (gallery || []).slice(0, 10);
    if (!list.length) return '';
    return `<section class="panel gal-panel">
      <div class="panel-head"><h3>Match photos</h3><p class="muted">${gallery.length} photo${gallery.length === 1 ? '' : 's'} from the matches. Tap one to open it.</p></div>
      <div class="gal-strip" data-keep-scroll>${list.map(p => `<button type="button" class="gal-thumb" data-action="open-photo" data-id="${esc(p.id)}" aria-label="Photo by ${esc(p.by)}">
        <img src="${matchPhotoSrc(p.id)}" alt="" loading="lazy"></button>`).join('')}</div>
    </section>`;
  }

  function sheetPhoto() {
    const p = (gallery || []).find(x => x.id === sheet.id);
    if (!p) return '';
    const m = C.matches(state).find(x => x.id === p.matchId);
    const sibs = photosOf(p.matchId);
    const i = sibs.findIndex(x => x.id === p.id);
    return `<header class="sheet-head">
        <div><span class="day-kind">Photo${sibs.length > 1 ? ` · ${i + 1} of ${sibs.length}` : ''}</span>
          <h2>${m ? `${esc(m.label)}${m.opponent ? ` · vs ${esc(m.opponent)}` : ''}` : 'Match photo'}</h2>
          <p class="muted">Added by ${esc(p.by)} · ${ago(p.createdAt)}</p></div>
        <button class="icon-btn" data-action="photo-back" aria-label="Close">${X_ICON}</button>
      </header>
      <div class="sheet-body gal-view">
        <img class="gal-big" src="${matchPhotoSrc(p.id)}" alt="Match photo">
        <div class="gal-actions">
          ${sibs.length > 1 ? `<button class="btn btn-ghost" data-action="photo-nav" data-dir="-1">${icon('<path d="m15 18-6-6 6-6"/>', 18)} Previous</button>
            <button class="btn btn-ghost" data-action="photo-nav" data-dir="1">Next ${icon('<path d="m9 18 6-6-6-6"/>', 18)}</button>` : ''}
          ${canRemovePhoto(p) ? `<button class="btn btn-danger" data-action="photo-delete" data-id="${esc(p.id)}">Remove</button>` : ''}
        </div>
      </div>`;
  }

  // ---------- profile form (opened from the "Update your profile" announcement) ----------
  let pForm = null;   // the draft: { id, name, jerseyName, jersey, bat, arm, style }; id '' until they say who they are
  const formFor = p => ({ id: p ? p.id : '', name: p ? p.name : '', jerseyName: p ? p.jerseyName || '' : '', jersey: p ? p.jersey || '' : '',
    bat: p ? p.battingHand || '' : '', arm: p ? p.bowlingArm || '' : '', style: p ? p.bowlingStyle || '' : '' });
  function openProfileForm() {
    pForm = formFor(myPlayer());
    sheet = { type: 'profile-form' };
    render();
  }

  // They tell the app which squad player they are (also remembered for votes, photos and notifications).
  function chooseWho(id) {
    const p = findPlayer(id);
    if (!p) return;
    writeStore(ME_KEY, p.id);
    pForm = formFor(p);
    refetchForViewer();
    refreshPush();
    loadEngage();
    render();
  }

  function sheetProfileForm() {
    if (!pForm) return '';
    const me = findPlayer(pForm.id);
    const typed = norm(session && session.name);
    const words = typed.split(' ').filter(Boolean);
    const near = state.players.filter(p => words.some(w => norm(p.name).split(/[\s()]+/).includes(w)) || (typed && norm(p.name).includes(typed)));
    const opt = p => `<option value="${esc(p.id)}" ${me && me.id === p.id ? 'selected' : ''}>${esc(p.name)}</option>`;
    const rest = state.players.filter(p => !near.includes(p));
    const chooser = `<select class="field select" data-input="pf-who" aria-label="Choose your name">
      ${me ? '' : '<option value="">Choose your name…</option>'}
      ${near.length ? `<optgroup label="Matching “${esc(session.name)}”">${near.map(opt).join('')}</optgroup>` : ''}
      <optgroup label="${near.length ? 'Everyone in the squad' : 'The squad'}">${rest.map(opt).join('')}</optgroup></select>`;
    const head = `<header class="sheet-head">
        <div><span class="day-kind">Your profile</span><h2>Update your profile</h2>
          <p class="muted">Your teammates see this on your player card. Press Save when you’re done and the admin is told.</p></div>
        <button class="icon-btn" data-action="close-sheet" aria-label="Close">${X_ICON}</button>
      </header>`;
    if (!me) {
      return `${head}<div class="sheet-body pf"><div class="pf-who">
        <strong>Which player are you?</strong>
        <p class="muted">${typed ? `We couldn’t tell which squad player “${esc(session.name)}” is. ` : ''}Pick your name from the squad and you can edit your details.</p>
        ${chooser}</div></div>`;
    }
    const sel = (key, label, options) => `<label><span>${label}</span><select class="field select" data-input="pf-${key}">
      ${options.map(([v, l]) => `<option value="${esc(v)}" ${pForm[key] === v ? 'selected' : ''}>${l}</option>`).join('')}</select></label>`;
    return `${head}
      <div class="sheet-body pf">
        <div class="pf-who inline"><span>Updating <strong>${esc(me.name)}</strong></span>
          <label class="pf-notyou">Not you? Choose from the squad ${chooser}</label></div>
        <div class="pf-photo">
          <button class="avatar-btn" data-action="photo-pick" data-id="${esc(me.id)}" title="Change photo" aria-label="Change photo">${avatar(me, 112)}<span class="cam">${CAMERA_ICON}</span></button>
          <div><strong>${esc(me.name)}</strong><small class="muted">${me.photoAt ? 'Tap the photo to change it.' : 'Tap the camera to add a photo.'}</small></div>
        </div>
        <div class="form-grid">
          <label class="pf-wide"><span>Full name <small class="muted">(as shown in the squad, e.g. Ehtisham Badar (Roxy))</small></span>
            <input class="field" type="text" maxlength="60" value="${esc(pForm.name)}" placeholder="e.g. Ehtisham Badar (Roxy)" data-input="pf-name" data-focus="pf-name" autocomplete="off"></label>
          <label><span>Name on your jersey</span><input class="field" type="text" maxlength="24" value="${esc(pForm.jerseyName)}" placeholder="e.g. BABAR" data-input="pf-jerseyName" data-focus="pf-jname" autocomplete="off"></label>
          <label><span>Jersey number</span><input class="field" type="text" inputmode="numeric" maxlength="3" value="${esc(pForm.jersey)}" placeholder="e.g. 56" data-input="pf-jersey" data-focus="pf-jnum" autocomplete="off"></label>
          ${sel('bat', 'Batting hand', [['', 'Not set'], ['right', 'Right-hand'], ['left', 'Left-hand']])}
          ${sel('arm', 'Bowling arm', [['', 'Not set'], ['right', 'Right-arm'], ['left', 'Left-arm']])}
          ${sel('style', 'Bowling style', BOWL_STYLES.map(x => [x, x || 'Not set']))}
        </div>
        <button class="btn btn-gold btn-block" data-action="profile-form-save">${icon('<path d="M20 6 9 17l-5-5"/>', 18)} Save profile</button>
      </div>`;
  }

  // ---------- player photos ----------
  let photoTarget = null;

  // The photo calls speak raw bytes, not JSON. The admin may change anyone's; a player only their own.
  async function photoApi(method, id, blob) {
    const headers = { 'X-Viewer': id };
    if (blob) headers['Content-Type'] = 'image/jpeg';
    if (session && session.token) headers['X-Admin-Token'] = session.token;
    const res = await fetch(`${API}/api/players/${encodeURIComponent(id)}/photo`, { method, headers, body: blob });
    const data = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(data.error || `Request failed (${res.status})`);
    return data;
  }

  // Centre-crops to a square and shrinks to 320 px, so uploads stay small whatever the phone's camera gives.
  async function shrinkPhoto(file) {
    const bmp = await createImageBitmap(file);
    const size = Math.min(bmp.width, bmp.height);
    const canvas = document.createElement('canvas');
    canvas.width = canvas.height = 320;
    canvas.getContext('2d').drawImage(bmp, (bmp.width - size) / 2, (bmp.height - size) / 2, size, size, 0, 0, 320, 320);
    return new Promise((resolve, reject) => canvas.toBlob(b => b ? resolve(b) : reject(new Error('Could not read that picture.')), 'image/jpeg', 0.85));
  }

  // Reads the chosen picture into a canvas no bigger than 1600 px (phone cameras give huge files), upright as the camera meant.
  async function loadPicture(file) {
    let source;
    try {
      source = await createImageBitmap(file, { imageOrientation: 'from-image' });
    } catch {
      const url = URL.createObjectURL(file);
      try {
        source = await new Promise((resolve, reject) => {
          const img = new Image();
          img.onload = () => resolve(img);
          img.onerror = () => reject(new Error('decode'));
          img.src = url;
        });
      } finally { URL.revokeObjectURL(url); }
    }
    const w = source.width || source.naturalWidth, h = source.height || source.naturalHeight;
    if (!w || !h) throw new Error('decode');
    const k = Math.min(1, 1600 / Math.max(w, h));
    const c = document.createElement('canvas');
    c.width = Math.max(1, Math.round(w * k));
    c.height = Math.max(1, Math.round(h * k));
    c.getContext('2d').drawImage(source, 0, 0, c.width, c.height);
    if (source.close) source.close();
    return c;
  }

  // A crop box over the picture: drag to move, use the slider, wheel or a pinch to zoom, rotate if it is sideways.
  // Resolves with a 320 px square JPEG, or null if cancelled.
  async function cropPhoto(file) {
    let pic;
    try { pic = await loadPicture(file); } catch {
      toast('Could not open that picture. Try another one (JPEG or PNG).');
      return null;
    }
    const BOX = 280, OUT = 320;
    const st = { zoom: 1, x: 0, y: 0 };       // x, y: image centre relative to the box centre, in box pixels
    const minScale = () => Math.max(BOX / pic.width, BOX / pic.height);
    return new Promise(resolve => {
      const root = $('#dialog-root');
      root.innerHTML = `<div class="dialog-backdrop"></div>
        <div class="dialog crop-dialog" role="dialog" aria-modal="true" aria-labelledby="crop-title">
          <h3 id="crop-title">Crop your photo</h3>
          <p class="muted">Drag to move the picture, and zoom so your face fills the circle.</p>
          <div class="crop-box" id="crop-box"><canvas width="${BOX}" height="${BOX}"></canvas><span class="crop-guide"></span></div>
          <div class="crop-tools">
            <button class="icon-btn" type="button" data-crop-zoom="-0.25" aria-label="Zoom out">−</button>
            <input type="range" min="1" max="4" step="0.01" value="1" id="crop-zoom" aria-label="Zoom">
            <button class="icon-btn" type="button" data-crop-zoom="0.25" aria-label="Zoom in">+</button>
            <button class="icon-btn" type="button" id="crop-rotate" aria-label="Rotate" title="Rotate">${icon('<path d="M21 12a9 9 0 1 1-3-6.7L21 8"/><path d="M21 3v5h-5"/>', 18)}</button>
          </div>
          <div class="dialog-actions"><button class="btn btn-ghost" data-crop="0">Cancel</button><button class="btn btn-primary" data-crop="1">Use photo</button></div>
        </div>`;
      const box = $('#crop-box');
      const ctx = box.querySelector('canvas').getContext('2d');
      const setZoom = z => { st.zoom = Math.max(1, Math.min(4, z)); $('#crop-zoom').value = st.zoom; };
      const clamp = () => {
        const sc = minScale() * st.zoom;
        const lx = Math.max(0, (pic.width * sc - BOX) / 2), ly = Math.max(0, (pic.height * sc - BOX) / 2);
        st.x = Math.max(-lx, Math.min(lx, st.x));
        st.y = Math.max(-ly, Math.min(ly, st.y));
      };
      const draw = (c, size) => {
        const k = size / BOX, sc = minScale() * st.zoom;
        c.fillStyle = '#111';
        c.fillRect(0, 0, size, size);
        c.drawImage(pic, (BOX / 2 + st.x - pic.width * sc / 2) * k, (BOX / 2 + st.y - pic.height * sc / 2) * k, pic.width * sc * k, pic.height * sc * k);
      };
      const paint = () => { clamp(); draw(ctx, BOX); };
      paint();
      const pts = new Map();
      let pinch = 0;
      box.addEventListener('pointerdown', e => { e.preventDefault(); try { box.setPointerCapture(e.pointerId); } catch { /* ignore */ } pts.set(e.pointerId, { x: e.clientX, y: e.clientY }); pinch = 0; });
      box.addEventListener('pointermove', e => {
        const prev = pts.get(e.pointerId);
        if (!prev) return;
        e.preventDefault();
        pts.set(e.pointerId, { x: e.clientX, y: e.clientY });
        if (pts.size === 2) {
          const [a, b] = [...pts.values()];
          const d = Math.hypot(a.x - b.x, a.y - b.y);
          if (pinch) setZoom(st.zoom * d / pinch);
          pinch = d;
        } else {
          st.x += e.clientX - prev.x;
          st.y += e.clientY - prev.y;
        }
        paint();
      });
      const up = e => { pts.delete(e.pointerId); pinch = 0; };
      box.addEventListener('pointerup', up);
      box.addEventListener('pointercancel', up);
      box.addEventListener('wheel', e => { e.preventDefault(); setZoom(st.zoom * (e.deltaY < 0 ? 1.08 : 0.92)); paint(); }, { passive: false });
      $('#crop-zoom').addEventListener('input', e => { setZoom(Number(e.target.value)); paint(); });
      root.querySelectorAll('[data-crop-zoom]').forEach(b => b.addEventListener('click', () => { setZoom(st.zoom + Number(b.dataset.cropZoom)); paint(); }));
      $('#crop-rotate').addEventListener('click', () => {
        const c = document.createElement('canvas');
        c.width = pic.height;
        c.height = pic.width;
        const x = c.getContext('2d');
        x.translate(c.width, 0);
        x.rotate(Math.PI / 2);
        x.drawImage(pic, 0, 0);
        pic = c;
        st.x = st.y = 0;
        setZoom(1);
        paint();
      });
      const done = async ok => {
        document.removeEventListener('keydown', onKey);
        let out = null;
        if (ok) {
          const c = document.createElement('canvas');
          c.width = c.height = OUT;
          draw(c.getContext('2d'), OUT);
          out = await new Promise(r => c.toBlob(r, 'image/jpeg', 0.85));
        }
        root.innerHTML = '';
        resolve(out);
      };
      const onKey = e => { if (e.key === 'Escape') done(false); };
      document.addEventListener('keydown', onKey);
      root.querySelectorAll('[data-crop]').forEach(b => b.addEventListener('click', () => done(b.dataset.crop === '1')));
      root.querySelector('.dialog-backdrop').addEventListener('click', () => done(false));
    });
  }

  async function uploadPhoto(file) {
    const p = findPlayer(photoTarget);
    if (!p || !file) return;
    try {
      const cropped = await cropPhoto(file);
      if (!cropped) return;
      const r = await photoApi('PUT', p.id, cropped);
      p.photoAt = r.photoAt;
      render();
      toast('Photo updated.');
    } catch (e) { toast(e.message || 'Could not upload the photo.'); }
  }

  // A player's own batting hand, bowling arm and bowling type save as soon as they are picked.
  async function saveMyProfile(el) {
    const me = myPlayer();
    if (!me) return;
    const key = { 'me-bat': 'battingHand', 'me-arm': 'bowlingArm', 'me-style': 'bowlingStyle' }[el.dataset.input];
    if (!key) return;
    const prev = me[key];
    me[key] = el.value;
    try {
      const res = await fetch(`${API}/api/players/${encodeURIComponent(me.id)}/profile`, {
        method: 'PUT', headers: { 'Content-Type': 'application/json', 'X-Viewer': me.id },
        body: JSON.stringify({ battingHand: me.battingHand, bowlingArm: me.bowlingArm, bowlingStyle: me.bowlingStyle })
      });
      const data = await res.json().catch(() => ({}));
      if (!res.ok) throw new Error(data.error || 'Could not save that.');
      toast('Saved.');
    } catch (e) {
      me[key] = prev;
      toast(e.message || 'Could not save that.');
      render();
    }
  }

  // Players change their own photo and cricket details; the admin's open screen picks those up
  // without touching anything the admin is editing.
  async function syncProfiles() {
    if (!isAdmin() || saveTimer) return;
    const started = Date.now();
    let live;
    try { live = await fetchLedger(); } catch { return; }
    if (saveTimer || editedAt > started) return;
    let changed = false;
    live.players.forEach(lp => {
      const p = findPlayer(lp.id);
      if (!p) return;
      ['name', 'battingHand', 'bowlingArm', 'bowlingStyle', 'photoAt', 'jersey', 'jerseyName', 'profileAt'].forEach(k => { if (p[k] !== lp[k]) { p[k] = lp[k]; changed = true; } });
    });
    if (changed) render();
  }

  // ---------- player profile ----------
  const CAMERA_ICON = icon('<path d="M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z"/><circle cx="12" cy="13" r="4"/>', 16);
  const TROPHY_ICON = icon('<path d="M8 21h8M12 17v4M7 4h10v5a5 5 0 0 1-10 0z"/><path d="M17 5h3v2a3 3 0 0 1-3 3M7 5H4v2a3 3 0 0 0 3 3"/>', 14);
  // The admin may change anyone's picture; everyone else only their own.
  const canEditPhoto = p => !archive && (isAdmin() || (!!myPlayer() && myPlayer().id === p.id));
  const photoUrl = p => `${API}/api/players/${encodeURIComponent(p.id)}/photo?v=${encodeURIComponent(p.photoAt)}`;
  // A player's picture, or a red placeholder with a silhouette when none was uploaded.
  const avatar = (p, size = 44) => `<span class="avatar ${p.photoAt ? '' : 'ph'}" style="--s:${size}px">${p.photoAt
    ? `<img src="${esc(photoUrl(p))}" alt="" loading="lazy" decoding="async">` : icon(PERSON_PATH, Math.round(size * 0.56))}</span>`;
  const isCaptain = p => p.captain || /captain/i.test(p.role);
  const isKeeper = p => p.keeper || /keeper/i.test(p.role);
  const battingText = p => p.battingHand ? `${p.battingHand === 'left' ? 'Left' : 'Right'}-hand bat` : '—';
  const bowlingText = p => [p.bowlingArm ? `${p.bowlingArm === 'left' ? 'Left' : 'Right'}-arm` : '', p.bowlingStyle.toLowerCase()].filter(Boolean).join(' ') || '—';

  function profileStats(p, played) {
    const t = C.leagueStats(state, statsMatchVisible).find(x => x.player.id === p.id);
    if (!t || !t.matches) return '';
    const tile = (label, value) => `<div class="stat"><span>${label}</span><strong>${value}</strong></div>`;
    const bat = t.innings > 0 ? `<h3 class="sub">Batting</h3><div class="prof-tiles">
        ${tile('Matches', played)}${tile('Runs', t.runs)}${tile('Highest', t.high ? t.high.runs + (t.high.out ? '' : '*') : '—')}
        ${tile('Average', t.average == null ? '—' : t.average.toFixed(2))}${tile('Strike rate', SR(t.runs, t.balls))}
        ${tile('Fours', t.fours)}${tile('Sixes', t.sixes)}${tile('Innings', t.innings)}</div>` : '';
    const bowl = t.bowlBalls > 0 || t.conceded > 0 ? `<h3 class="sub">Bowling</h3><div class="prof-tiles">
        ${tile('Overs', C.oversOf(t.bowlBalls))}${tile('Wickets', t.wickets)}${tile('Runs', t.conceded)}
        ${tile('Economy', ECON(t.conceded, t.bowlBalls))}${tile('Best', t.best ? `${t.best.wickets}/${t.best.runs}` : '—')}</div>` : '';
    const field = t.catches + t.drops + t.misfields + t.goodFielding > 0 ? `<h3 class="sub">Fielding</h3><div class="prof-tiles">
        ${tile('Catches', t.catches)}${tile('Drops', t.drops)}${tile('Misfields', t.misfields)}${tile('Good fielding', t.goodFielding)}</div>` : '';
    return `<div class="prof-stats">${bat}${bowl}${field}</div>`;
  }

  // Every match the admin has finished, with this player's part in it (and their score when stats are shown).
  function matchHistory(p) {
    const rows = C.matches(state).filter(m => m.finished || (m.test && isAdmin()));
    if (!rows.length) return '';
    const item = m => {
      const took = p.att[m.id], twelfth = state.twelfth[m.id] === p.id;
      const status = took ? ['played', 'Played'] : twelfth ? ['twelfth', '12th man'] : ['out', 'Did not play'];
      const shown = statsMatchVisible(m) && state.stats[m.id] && state.stats[m.id][p.id];
      const e = shown ? C.statOf(state, m.id, p.id) : null;
      const line = e ? [C.batted(e) ? `${C.runsOf(e)}${e.out ? '' : '*'} (${e.balls})` : '', C.bowled(e) ? `${e.wickets}/${C.concededOf(e)}` : '',
        e.catches ? `${e.catches} ct` : ''].filter(Boolean).join(' · ') : '';
      const when = [m.opponent && `vs ${m.opponent}`, m.date && formatDate(m.date).replace(/ \d{4}$/, '')].filter(Boolean).join(' · ');
      return `<li class="mh-row ${status[0]}">
        <span class="mh-main"><strong>${m.label}${m.test ? ' <em class="mh-test">test</em>' : ''}</strong>${when ? `<small>${esc(when)}</small>` : ''}</span>
        <span class="mh-end"><span class="mh-status ${status[0]}">${status[1]}</span>${line ? `<small>${esc(line)}</small>` : ''}</span>
      </li>`;
    };
    return `<h3 class="sub">Matches</h3><ul class="mh-list">${rows.map(item).join('')}</ul>`;
  }

  function sheetProfile() {
    const idx = state.players.findIndex(x => x.id === sheet.id);
    const p = state.players[idx];
    if (!p) return '';
    const admin = isAdmin();
    // A real match only counts once the admin has finished it. Test matches count for the admin only.
    const playedIn = C.matches(state).filter(m => p.att[m.id] && (m.finished || (m.test && isAdmin())));
    const played = playedIn.length;
    const playedTests = playedIn.filter(m => m.test).length;
    const moms = engage ? engage.mom.filter(r => r.winners.includes(p.id)).length : 0;
    const badges = [isCaptain(p) ? 'Captain' : '', p.viceCaptain ? 'Vice-captain' : '', isKeeper(p) ? 'Wicket-keeper' : '', moms ? `⭐ Man of the match${moms > 1 ? ` ×${moms}` : ''}` : '']
      .filter(Boolean).map(b => `<span class="prof-badge">${b}</span>`).join('');
    const editor = admin ? `<div class="prof-edit">
        <h3 class="sub">Edit profile</h3>
        <div class="form-grid">
          <label><span>Batting hand</span><select class="field select" data-input="p-bat" data-id="${esc(p.id)}">
            ${[['', 'Not set'], ['right', 'Right-hand'], ['left', 'Left-hand']].map(([v, l]) => `<option value="${v}" ${p.battingHand === v ? 'selected' : ''}>${l}</option>`).join('')}</select></label>
          <label><span>Bowling arm</span><select class="field select" data-input="p-bowl-arm" data-id="${esc(p.id)}">
            ${[['', 'Not set'], ['right', 'Right-arm'], ['left', 'Left-arm']].map(([v, l]) => `<option value="${v}" ${p.bowlingArm === v ? 'selected' : ''}>${l}</option>`).join('')}</select></label>
          <label><span>Bowling style</span><select class="field select" data-input="p-bowl-style" data-id="${esc(p.id)}">
            ${BOWL_STYLES.map(s => `<option value="${esc(s)}" ${p.bowlingStyle === s ? 'selected' : ''}>${s || 'Not set'}</option>`).join('')}</select></label>
          <label><span>Jersey name</span><input class="field" type="text" maxlength="24" value="${esc(p.jerseyName)}" data-input="p-jersey-name" data-id="${esc(p.id)}" data-focus="pjname-${esc(p.id)}"></label>
          <label><span>Jersey number</span><input class="field" type="text" inputmode="numeric" maxlength="3" value="${esc(p.jersey)}" data-input="p-jersey" data-id="${esc(p.id)}" data-focus="pjersey-${esc(p.id)}"></label>
          <div class="form-row"><span>Roles</span><span class="prof-flags">
            ${[['captain', 'Captain'], ['viceCaptain', 'Vice-captain'], ['keeper', 'Wicket-keeper']].map(([k, l]) =>
              `<button class="tog ${p[k] ? 'on' : ''}" data-action="p-flag" data-id="${esc(p.id)}" data-flag="${k}" aria-pressed="${!!p[k]}">${l}</button>`).join('')}</span></div>
          <div class="form-row"><span>Photo</span><span class="prof-flags">
            <button class="btn btn-ghost small" data-action="photo-pick" data-id="${esc(p.id)}">${p.photoAt ? 'Change photo' : 'Upload photo'}</button>
            ${p.photoAt ? `<button class="btn btn-ghost small" data-action="photo-remove" data-id="${esc(p.id)}">Remove</button>` : ''}</span></div>
        </div></div>` : '';
    return `
      <header class="sheet-head">
        <div><span class="day-kind">Player #${idx + 1}</span></div>
        <button class="icon-btn" data-action="close-sheet" aria-label="Close">${X_ICON}</button>
      </header>
      <div class="sheet-body">
        <div class="prof-card">
          ${canEditPhoto(p)
            ? `<button class="avatar-btn" data-action="photo-pick" data-id="${esc(p.id)}" title="Change photo" aria-label="Change photo">${avatar(p, 112)}<span class="cam">${CAMERA_ICON}</span></button>`
            : avatar(p, 112)}
          <div class="prof-main">
            <div class="prof-namerow"><h2 class="prof-name">${esc(p.name) || 'Unnamed'}</h2>
              <button class="icon-btn exp-btn" data-action="open-player" data-id="${esc(p.id)}" title="Expenses and payments" aria-label="Expense card for ${esc(p.name)}">${WALLET_ICON}</button></div>
            ${p.jersey || p.jerseyName ? `<span class="prof-jersey">${p.jerseyName ? `${esc(p.jerseyName)} ` : ''}${p.jersey ? `#${esc(p.jersey)}` : ''}</span>` : ''}
            <div class="prof-badges">${badges}</div>
            <span class="muted">${esc(p.role) || '—'}</span>
            ${state.tournament.name ? `<span class="prof-tour">${TROPHY_ICON} ${esc(state.tournament.name)}${C.inTournament(p) ? '' : ' · not taking part'}</span>` : ''}
          </div>
        </div>
        <dl class="kv prof-kv">
          <dt>Batting</dt><dd>${battingText(p)}</dd>
          <dt>Bowling</dt><dd>${bowlingText(p)}</dd>
          <dt>Matches played</dt><dd>${played}${playedTests ? ` <small class="muted">(incl. ${playedTests} test)</small>` : ''}</dd>
          <dt>Tournament</dt><dd>${state.tournament.name ? `${esc(state.tournament.name)} · ` : ''}${C.inTournament(p) ? 'Taking part' : 'Not taking part'}</dd>
        </dl>
        ${profileStats(p, played)}
        ${matchHistory(p)}
        ${editor}
      </div>`;
  }

  // Who has saved their own profile yet. Players press "Update profile" here; the admin sees who is still missing.
  function profilesPanel() {
    if (archive || !realAdmin() || !state.players.length) return '';
    const done = state.players.filter(p => p.profileAt);
    const todo = state.players.filter(p => !p.profileAt);
    const pct = Math.round(done.length * 100 / state.players.length);
    const row = p => `<li class="pu-row">${avatar(p, 34)}
        <button class="pl-name" data-action="open-profile" data-id="${esc(p.id)}">${esc(p.name)}</button>
        <span class="prof-tag todo">Not updated</span></li>`;
    return `<section class="panel pu-panel">
      <div class="panel-head"><h3>Profiles</h3>
        <p class="muted">${done.length} of ${state.players.length} players have updated their profile. Tap a name to see it.</p></div>
      <span class="eg-bar pu-bar"><i style="width:${pct}%"></i></span>
      ${todo.length ? `<h4 class="pu-sub">Not updated yet <span>${todo.length}</span></h4><ul class="pu-list">${todo.map(row).join('')}</ul>` : '<p class="muted pu-all">Everyone has updated their profile. 🎉</p>'}
      ${realAdmin() && todo.length ? `<div class="eg-actions"><button class="btn btn-ghost small" data-action="ask-profile">${icon('<path d="M3 11v2a1 1 0 0 0 1 1h2l5 4V6L6 10H4a1 1 0 0 0-1 1z"/>', 16)} Ask everyone to update</button></div>` : ''}
      ${done.length ? `<h4 class="pu-sub">Updated <span>${done.length}</span></h4><ul class="pu-list done">${done.map(p => `<li class="pu-row">${avatar(p, 34)}<button class="pl-name" data-action="open-profile" data-id="${esc(p.id)}">${esc(p.name)}</button><span class="prof-tag ok">${icon('<path d="M20 6 9 17l-5-5"/>', 13)} Updated ${ago(p.profileAt)}</span></li>`).join('')}</ul>` : ''}
    </section>`;
  }

  function viewPlayers() {
    const admin = isAdmin();
    const tag = p => !realAdmin() ? '' : p.profileAt ? `<span class="pl-dot ok" title="Profile updated" aria-label="Profile updated">${icon('<path d="M20 6 9 17l-5-5"/>', 11)}</span>` : `<span class="pl-dot todo" title="Profile not updated yet" aria-label="Profile not updated yet"></span>`;
    const list = state.players.map((p, i) => `
      <div class="player-line">
        <span class="sr">${i + 1}</span>
        <span class="pl-namecell">${avatar(p, 30)}<button class="pl-name" data-action="open-profile" data-id="${esc(p.id)}">${esc(p.name) || '<em>Unnamed</em>'}</button>${tag(p)}<button class="icon-btn exp-btn mini" data-action="open-player" data-id="${esc(p.id)}" aria-label="Expense card for ${esc(p.name)}" title="Expenses and payments">${WALLET_ICON}</button></span>
        ${admin
          ? `<select class="field select" data-input="role" data-id="${esc(p.id)}" aria-label="Role for ${esc(p.name)}">${ROLES.map(r => `<option value="${esc(r)}" ${r === p.role ? 'selected' : ''}>${r || 'No role'}</option>`).join('')}</select>
             ${tourToggle(p)}
             <button class="icon-btn danger" data-action="remove-player" data-id="${esc(p.id)}" aria-label="Remove ${esc(p.name)}">${X_ICON}</button>`
          : `<span class="muted role">${esc(p.role) || '—'}</span>${tourToggle(p)}`}
      </div>`).join('');

    return `
      ${pageHead('Team', `${state.players.length} in the squad`, admin && state.players.length ? `<div class="share-row">
          <button class="btn btn-gold" data-action="share-squad">${IMAGE_ICON} Send squad list to management</button></div>` : '')}
      ${profilesPanel()}
      ${leaguePanel()}
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
    const I = {
      save: icon('<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M7 10l5 5 5-5M12 15V3"/>', 18),
      open: icon('<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M17 8l-5-5-5 5M12 3v12"/>', 18),
      xis: icon('<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="m17 8 5 5M22 8l-5 5"/>', 18),
      stats: icon('<path d="M18 20V10M12 20V4M6 20v-6"/><path d="m3 3 18 18"/>', 18),
      series: icon('<path d="M23 4v6h-6M1 20v-6h6"/><path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/>', 18),
      trash: icon('<path d="M3 6h18M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6M10 11v6M14 11v6M9 6V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2"/>', 18)
    };
    const backupPanel = `
      <section class="panel">
        <div class="panel-head"><h3>Backups</h3><p class="muted">${admin ? 'Save a copy of everything, or restore one. Same file format as the original HTML ledger.' : 'Download a copy of the current ledger.'}</p></div>
        <div class="settings-actions">
          <button class="btn btn-ghost" data-action="export">${I.save} Save backup</button>
          ${admin ? `<button class="btn btn-ghost" data-action="import">${I.open} Open backup</button>` : ''}
        </div>
      </section>`;
    const cleanupPanel = admin ? `
      <section class="panel">
        <div class="panel-head"><h3>Clean up</h3><p class="muted">Start match selections or stats again. Players and money stay as they are.</p></div>
        <div class="settings-actions">
          <button class="btn btn-ghost" data-action="clear-all-xis" ${xiCount() ? '' : 'disabled'}>${I.xis} Clear all playing XIs</button>
          <button class="btn btn-ghost" data-action="clear-all-stats" ${statCount() ? '' : 'disabled'}>${I.stats} Clear all stats</button>
        </div>
      </section>` : '';
    const dangerPanel = admin ? `
      <section class="panel danger-zone">
        <div class="panel-head"><h3>Danger zone</h3><p class="muted">These can’t be undone — save a backup first.</p></div>
        <div class="danger-grid">
          <div class="danger-row"><span><strong>New series</strong><small>Archives this season, keeps players, then clears attendance, payments, expenses, matches, stats and the league.</small></span>
            <button class="btn btn-ghost" data-action="new-series">${I.series} New series</button></div>
          <div class="danger-row"><span><strong>Clear everything</strong><small>Removes all players and data. No archive is made.</small></span>
            <button class="btn btn-danger" data-action="clear-all">${I.trash} Clear all</button></div>
        </div>
      </section>` : '';

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

    const field = (label, key, input, placeholder) => admin
      ? `<div class="pref"><label for="pay-${input}">${label}</label>
          <input id="pay-${input}" class="field" type="text" value="${esc(s[key])}" placeholder="${placeholder}" maxlength="60" data-input="pay-${input}" data-focus="pay-${input}"></div>`
      : `<dt>${label}</dt><dd>${esc(s[key]) || '—'}</dd>`;
    const fields = [['Bank / wallet', 'payBank', 'bank', 'e.g. NayaPay'], ['Account title', 'payTitle', 'title', 'Name on the account'],
      ['Account #', 'payAccount', 'account', 'Account or IBAN']].map(f => field(...f)).join('');
    const payPanel = `
      <section class="panel">
        <div class="panel-head"><h3>Payment account</h3><p class="muted">${admin ? 'Shown on the balances picture and in WhatsApp reminders.' : 'Where to send your payments.'}</p></div>
        ${admin ? fields : `<dl class="kv">${fields}</dl>`}
      </section>`;

    const current = currentTheme();
    const themePanel = `
      <section class="panel">
        <div class="panel-head"><h3>Theme</h3><p class="muted">Changes the look on this device only.</p></div>
        <div class="theme-list theme-grid" role="radiogroup" aria-label="Theme">
          ${THEMES.map(t => `<button class="theme-opt ${t.id === current ? 'on' : ''}" role="radio" aria-checked="${t.id === current}" data-action="set-theme" data-theme="${t.id}">
            <span class="theme-swatch" style="background:${t.colors[0]}"><i style="background:${t.colors[1]}"></i><i style="background:${t.colors[2]}"></i></span>
            <span class="theme-text"><strong>${t.name}</strong><small>${t.note}</small></span>
            <span class="theme-check">${icon('<path d="M20 6 9 17l-5-5"/>', 16)}</span>
          </button>`).join('')}
        </div>
      </section>`;

    const access = [
      { id: 'all', name: 'Everyone’s balances', note: 'Viewers see the whole team’s money' },
      { id: 'own', name: 'Only their own', note: 'Viewers see just their balance and payments' }
    ];
    const accessPanel = admin ? `
      <section class="panel">
        <div class="panel-head"><h3>Viewer access</h3><p class="muted">What players see when they sign in with their name. Attendance and expenses stay visible to all.</p></div>
        <div class="theme-list" role="radiogroup" aria-label="Viewer access">
          ${access.map(a => `<button class="theme-opt access-opt ${s.viewerAccess === a.id ? 'on' : ''}" role="radio" aria-checked="${s.viewerAccess === a.id}" data-action="set-access" data-access="${a.id}">
            <span class="access-icon">${icon(a.id === 'all' ? '<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75"/>' : '<rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/>', 20)}</span>
            <span class="theme-text"><strong>${a.name}</strong><small>${a.note}</small></span>
            <span class="theme-check">${icon('<path d="M20 6 9 17l-5-5"/>', 16)}</span>
          </button>`).join('')}
        </div>
        <p class="muted access-hint">Players pick their name to sign in, so this keeps balances out of casual view — it isn’t a password lock.</p>
      </section>` : '';

    const tabRows = [
      { key: 'daysVisibility', label: 'Days', path: '<rect x="3" y="4" width="18" height="18" rx="2"/><path d="M16 2v4M8 2v4M3 10h18"/>' },
      { key: 'statsVisibility', label: 'Stats', path: '<path d="M18 20V10M12 20V4M6 20v-6"/>' }
    ];
    const cardRows = [
      { key: 'showNextMatch', label: 'Next match card', note: 'The countdown and “See details” on the Home screen', path: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>' },
      { key: 'showFixtures', label: 'Fixtures calendar', note: 'Every fixture with its date, on the Home screen', path: '<rect x="3" y="4" width="18" height="18" rx="2"/><path d="M16 2v4M8 2v4M3 10h18"/>' }
    ];
    const cardsPanel = admin ? `
      <section class="panel">
        <div class="panel-head"><h3>Home cards</h3><p class="muted">Show or hide these for all players. When they’re hidden from players you still see them, marked “Hidden from players”.</p></div>
        ${cardRows.map(r => {
          const on = !!s[r.key];
          return `<div class="pref switch-row">
            <span class="sw-ico">${icon(r.path, 18)}</span>
            <span class="sw-text">${r.label}<small class="muted">${on ? 'Shown to players' : 'Hidden from players'}</small></span>
            <button class="switch ${on ? 'on' : ''}" role="switch" aria-checked="${on}" data-action="set-summary-card" data-key="${r.key}" aria-label="Show ${r.label} to players"><span></span></button>
          </div>`;
        }).join('')}
        <div class="pref switch-row">
          <span class="sw-ico">${icon('<path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>', 18)}</span>
          <span class="sw-text">Fixtures calendar for me<small class="muted">${s.adminShowFixtures ? 'You see it on Home' : 'Hidden on your Home too'}</small></span>
          <button class="switch ${s.adminShowFixtures ? 'on' : ''}" role="switch" aria-checked="${s.adminShowFixtures}" data-action="set-admin-fixtures" aria-label="Show the fixtures calendar to me"><span></span></button>
        </div>
      </section>` : '';

    const statsPanel = admin ? `
      <section class="panel">
        <div class="panel-head"><h3>Tabs for players</h3><p class="muted">Choose which tabs view-only players can open. You always see all of them.</p></div>
${tabRows.map(r => {
          const on = s[r.key] === 'all';
          return `<div class="pref switch-row">
            <span class="sw-ico">${icon(r.path, 18)}</span>
            <span class="sw-text">${r.label}<small class="muted">${on ? 'Players can open it' : 'Only you can open it'}</small></span>
            <button class="switch ${on ? 'on' : ''}" role="switch" aria-checked="${on}" data-action="set-tab-visibility" data-key="${r.key}" aria-label="Show ${r.label} to players"><span></span></button>
          </div>`;
        }).join('')}
      </section>` : '';

    const me = !realAdmin() ? myPlayer() : null;
    const pick = (input, label, prop, options) => `<label><span>${label}</span><select class="field select" data-input="${input}">
      ${options.map(([v, l]) => `<option value="${esc(v)}" ${me[prop] === v ? 'selected' : ''}>${l}</option>`).join('')}</select></label>`;
    const photoPanel = !realAdmin() ? `
      <section class="panel photo-panel">
        <div class="panel-head"><p class="muted">Shown on your player profile for everyone in the team. Changes save as soon as you pick them.</p></div>
        <label class="me-who ${me ? '' : 'needed'}"><span>${me ? 'You are' : 'Which player are you?'}${me ? '' : ' <small class="muted">Choose your squad name to vote, predict, answer polls and add photos.</small>'}</span>
          <select class="field select" data-input="pick-me" aria-label="Choose your name">
            ${me ? '' : '<option value="">Choose your name…</option>'}
            ${state.players.map(p => `<option value="${esc(p.id)}" ${me && me.id === p.id ? 'selected' : ''}>${esc(p.name)}</option>`).join('')}
          </select></label>
        ${me ? `<div class="photo-row">${avatar(me, 84)}<div class="photo-actions">
            <button class="btn btn-gold" data-action="photo-pick" data-id="${esc(me.id)}">${me.photoAt ? 'Change photo' : 'Upload photo'}</button>
            ${me.photoAt ? `<button class="btn btn-ghost" data-action="photo-remove" data-id="${esc(me.id)}">Remove</button>` : ''}</div></div>
          <div class="form-grid me-grid">
            ${pick('me-bat', 'Batting hand', 'battingHand', [['', 'Not set'], ['right', 'Right-hand'], ['left', 'Left-hand']])}
            ${pick('me-arm', 'Bowling arm', 'bowlingArm', [['', 'Not set'], ['right', 'Right-arm'], ['left', 'Left-arm']])}
            ${pick('me-style', 'Bowling type', 'bowlingStyle', BOWL_STYLES.map(s => [s, s || 'Not set']))}
          </div>
          <button class="btn btn-ghost" data-action="open-profile-form">Jersey name and number, and send to admin</button>`
          : ''}
      </section>` : '';

    const accountPanel = `
      <section class="panel account-card">
        <span class="account-avatar">${admin ? icon('<path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>', 22) : esc((session.name || '?').trim().charAt(0).toUpperCase())}</span>
        <span class="account-text"><strong>${admin ? 'Admin' : esc(session.name)}</strong><small>${admin ? 'Can change everything' : isManager() ? 'App manager · can set the playing XI' : 'View only · this page refreshes by itself'}</small></span>
        <button class="btn btn-ghost" data-action="logout">Switch user</button>
      </section>`;

    // Each section lays its cards out on a 12-column grid: [html, span]. A cell can hold a stack of cards.
    const SI = {
      look: icon('<circle cx="13.5" cy="6.5" r="1.5"/><circle cx="17.5" cy="10.5" r="1.5"/><circle cx="8.5" cy="7.5" r="1.5"/><circle cx="6.5" cy="12.5" r="1.5"/><path d="M12 2a10 10 0 0 0 0 20c1.1 0 2-.9 2-2 0-.5-.2-1-.5-1.3-.3-.3-.5-.8-.5-1.2 0-1.1.9-2 2-2h2.4c3 0 5.6-2.5 5.6-5.5C23 6 18 2 12 2z"/>', 18),
      team: icon('<path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75"/>', 18),
      access: icon('<path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/>', 18),
      manager: icon('<path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/><path d="m9 12 2 2 4-4"/>', 18),
      notify: icon('<path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.7 21a2 2 0 0 1-3.4 0"/>', 18),
      data: icon('<ellipse cx="12" cy="5" rx="9" ry="3"/><path d="M21 12c0 1.66-4 3-9 3s-9-1.34-9-3"/><path d="M3 5v14c0 1.66 4 3 9 3s9-1.34 9-3V5"/>', 18),
      account: icon('<rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/>', 18),
      profile: icon(PERSON_PATH, 18)
    };
    const meP = !realAdmin() ? myPlayer() : null;
    const heroName = realAdmin() ? 'Admin' : esc(session.name);
    const heroSub = realAdmin() ? 'Can change everything' : isManager() ? 'App manager · can set the playing XI' : 'View only · this page refreshes by itself';
    const hero = `<section class="set-hero">
      <span class="set-hero-av">${realAdmin() ? icon('<path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>', 28)
        : meP ? avatar(meP, 56) : esc((session.name || '?').trim().charAt(0).toUpperCase())}</span>
      <span class="set-hero-text"><strong>${heroName}</strong><small>${heroSub}</small></span>
      <span class="set-hero-chips">
        <span class="hero-chip"><b>${state.players.length}</b> player${state.players.length === 1 ? '' : 's'}</span>
        <span class="hero-chip"><b>${realMatchCount()}</b> match${realMatchCount() === 1 ? '' : 'es'}</span>
        <span class="hero-chip">${icon('<circle cx="12" cy="12" r="9"/><path d="M12 3v18"/>', 14)} ${esc(THEMES.find(x => x.id === currentTheme()).name)}</span>
      </span>
      <button class="btn btn-ghost" data-action="logout">Switch user</button>
    </section>`;
    const cell = (html, span) => html ? `<div class="set-cell span-${span}">${html}</div>` : '';
    const stack = (...cards) => cards.filter(Boolean).join('');
    const notify = notifyPanels();
    const sections = [
      { id: 'look', icon: SI.look, title: 'Appearance', note: 'How the app looks on this device.', top: true,
        cells: cell(themePanel, 7) + cell(installPanel(), 5) },
      { id: 'team', icon: SI.team, title: 'Team & payments', note: admin ? 'Where players pay and how shares are worked out.' : 'Where to send your payments.',
        cells: admin ? cell(payPanel, 6) + cell(prefs, 6) : cell(payPanel, 12) },
      admin && { id: 'access', icon: SI.access, title: 'Who can see what', note: 'What players see when they sign in with their name.',
        cells: cell(accessPanel, 6) + cell(statsPanel, 6) + cell(cardsPanel, 12) + cell(managerPanel(), 12) },
      !realAdmin() && !archive && { id: 'manager', icon: SI.manager, title: 'App manager', note: 'Help the admin pick the playing XI.', top: true,
        cells: cell(managerRequestPanel(), 12) },
      { id: 'notify', icon: SI.notify, title: 'Notifications', note: realAdmin() ? 'Turn them on for this device, and message the team.' : 'Turn them on for this device.', top: true,
        cells: isAdmin() ? cell(notify.device, 5) + cell(notify.compose, 7) : cell(notify.device, 12) },
      // Players and managers get no backups or admin preferences; past seasons appear once there are some.
      admin
        ? { id: 'data', icon: SI.data, title: 'Data', note: 'Backups, the season archive and resets.', top: true,
            cells: cell(seasonPanel(), 7) + cell(`<div class="set-stack">${stack(backupPanel, cleanupPanel)}</div>`, 5) + cell(dangerPanel, 12) }
        : (seasons || []).length > 0 && { id: 'data', icon: SI.data, title: 'Past seasons', note: 'Look back at earlier seasons, read-only.', top: true,
            cells: cell(seasonPanel(), 12) },
      admin
        ? { id: 'account', icon: SI.account, title: 'Security', note: 'Protect the admin sign-in.', top: true, cells: cell(tfaPanel(), 12) }
        : { id: 'account', icon: SI.profile, title: 'My profile', note: 'Your photo and cricket details.', top: true, cells: cell(photoPanel, 12) }
    ].filter(Boolean);
    // Players and managers see their own profile first.
    if (!admin) sections.unshift(...sections.splice(sections.findIndex(x => x.id === 'account'), 1));

    return `
      ${pageHead('Settings', admin ? 'Team, money, access and data — grouped by what they change.' : 'Your view of the Red Falcons ledger.')}
      ${hero}
      <div class="set-layout">
        <nav class="set-nav" aria-label="Settings sections">
          ${sections.map(x => `<button class="set-nav-btn" data-action="set-jump" data-id="${x.id}">${x.icon}<span>${x.title}</span></button>`).join('')}
        </nav>
        <div class="set-sections">
          ${sections.map(x => `<section class="set-section" id="set-${x.id}">
            <header class="set-head"><span class="set-ico">${x.icon}</span><div><h3>${x.title}</h3><p class="muted">${x.note}</p></div></header>
            <div class="set-cards ${x.top ? 'top' : ''}">${x.cells}</div>
          </section>`).join('')}
        </div>
      </div>`;
  }

  // ---------- sheets ----------
  function renderSheet() {
    let body = '';
    if (sheet.type === 'day') body = sheetDay();
    if (sheet.type === 'player') body = sheetPlayer();
    if (sheet.type === 'profile') body = sheetProfile();
    if (sheet.type === 'inbox') body = sheetInbox();
    if (sheet.type === 'scorecard') body = sheetMatchInfo();
    if (sheet.type === 'feed') body = sheetFeed();
    if (sheet.type === 'photo') body = sheetPhoto();
    if (sheet.type === 'profile-form') body = sheetProfileForm();
    if (sheet.type === 'predictions') body = sheetPredictions();
    if (!body) { sheet = null; return ''; }
    return `<div class="sheet-backdrop" data-action="close-sheet"></div>
      <div class="sheet" role="dialog" aria-modal="true">${body}</div>`;
  }

  const isTwelfth = (p, d) => d.type === 'match' && state.twelfth[d.id] === p.id;

  // plain: always Played / Not played (the match sheet shows the 12th man with its own button).
  function toggleButton(p, d, stats, plain = false) {
    const on = !!p.att[d.id];
    const isMatch = d.type === 'match';
    if (!on && !plain && isTwelfth(p, d)) {
      return `<button class="tog match twelfth ${!canPickXi(d) ? 'ro' : ''}" data-action="toggle-att" data-player="${esc(p.id)}" data-day="${esc(d.id)}"
        ${!canPickXi(d) && !isAdmin() ? 'disabled' : ''} title="12th man — not charged for ${d.label}">12th man</button>`;
    }
    const max = state.settings.maxPerMatch;
    const full = isMatch && !on && stats[d.id].n >= max;
    const editable = isMatch ? canPickXi(d) : isAdmin();
    const disabled = !editable || full;
    const label = isMatch ? (on ? 'Played' : 'Not played') : (on ? 'Arrived' : 'Not arrived');
    return `<button class="tog ${d.type} ${on ? 'on' : ''} ${!editable ? 'ro' : ''}" data-action="toggle-att" data-player="${esc(p.id)}" data-day="${esc(d.id)}"
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

  function battingSection(d) {
    const order = battingOrderOf(d.id);
    const edit = canPickXi(d);
    const UP = icon('<path d="m18 15-6-6-6 6"/>', 18);
    const DOWN = icon('<path d="m6 9 6 6 6-6"/>', 18);
    const GRIP = icon('<circle cx="9" cy="6" r="1.4"/><circle cx="15" cy="6" r="1.4"/><circle cx="9" cy="12" r="1.4"/><circle cx="15" cy="12" r="1.4"/><circle cx="9" cy="18" r="1.4"/><circle cx="15" cy="18" r="1.4"/>', 18);
    return `<section class="bat">
      <h3 class="sub first">Batting order
        ${edit && state.battingOrder[d.id] ? `<button class="chip small-chip" data-action="bat-reset" data-day="${esc(d.id)}">Reset</button>` : ''}</h3>
      ${edit ? '<p class="muted bat-hint">Drag a player by the handle to any position, or use the arrows.</p>' : ''}
      <ol class="bat-list" data-day="${esc(d.id)}">
        ${order.map((p, i) => `<li class="${i < 2 ? 'opener' : ''}" data-id="${esc(p.id)}">
          ${edit ? `<span class="bat-grip" title="Drag to reorder" aria-hidden="true">${GRIP}</span>` : ''}
          <span class="bat-pos">${i + 1}</span>
          <span class="pname"><strong>${esc(p.name)}</strong><small>${i < 2 ? 'Opener' : esc(p.role) || '—'}</small></span>
          ${edit ? `<span class="bat-moves">
            <button class="icon-btn" data-action="bat-move" data-day="${esc(d.id)}" data-id="${esc(p.id)}" data-dir="up" ${i === 0 ? 'disabled' : ''} aria-label="Move ${esc(p.name)} up">${UP}</button>
            <button class="icon-btn" data-action="bat-move" data-day="${esc(d.id)}" data-id="${esc(p.id)}" data-dir="down" ${i === order.length - 1 ? 'disabled' : ''} aria-label="Move ${esc(p.name)} down">${DOWN}</button>
          </span>` : ''}
        </li>`).join('')}
      </ol>
    </section>`;
  }

  // Drag a batter by the handle to any position (mouse or touch). Nothing re-renders until the drop.
  function startBatDrag(e) {
    const grip = e.target.closest('.bat-grip');
    if (!grip || (e.pointerType === 'mouse' && e.button !== 0)) return;
    const li = grip.closest('li'), ol = li.parentElement, day = ol.dataset.day;
    if (!canPickXi(findMatch(day))) return;
    e.preventDefault();
    const items = [...ol.children];
    const from = items.indexOf(li);
    const rects = items.map(x => x.getBoundingClientRect());
    const step = rects[from].height + 6;
    const startY = e.clientY;
    let to = from;
    grip.setPointerCapture(e.pointerId);
    li.classList.add('dragging');
    items.forEach(x => { if (x !== li) x.classList.add('shifting'); });
    const move = ev => {
      const dy = ev.clientY - startY;
      li.style.transform = `translateY(${dy}px)`;
      const centre = rects[from].top + rects[from].height / 2 + dy;
      to = items.filter((x, i) => i !== from && rects[i].top + rects[i].height / 2 < centre).length;
      items.forEach((x, i) => {
        if (i === from) return;
        const shift = from < to && i > from && i <= to ? -step : from > to && i >= to && i < from ? step : 0;
        x.style.transform = shift ? `translateY(${shift}px)` : '';
      });
    };
    const end = ev => {
      grip.removeEventListener('pointermove', move);
      grip.removeEventListener('pointerup', end);
      grip.removeEventListener('pointercancel', end);
      items.forEach(x => { x.style.transform = ''; x.classList.remove('dragging', 'shifting'); });
      if (ev.type === 'pointercancel' || to === from) return;
      const ids = battingOrderOf(day).map(p => p.id);
      ids.splice(to, 0, ids.splice(from, 1)[0]);
      state.battingOrder[day] = ids;
      touchXi(day);
    };
    grip.addEventListener('pointermove', move);
    grip.addEventListener('pointerup', end);
    grip.addEventListener('pointercancel', end);
  }

  function leagueLine(d, s) {
    const tour = C.tournamentStats(state);
    if (!(tour.fee > 0)) return '';
    const split = s.n ? `${fmt(tour.perMatch)} ÷ ${s.n} = ${fmt(C.round2(tour.perMatch / s.n))} each` : fmt(tour.perMatch);
    const when = d.finished ? '' : s.n ? ' · charged to the XI when you finish the match' : ' · split across everyone until the match is finished';
    return `<p class="league-line">League fee for ${d.label}: <strong>${split}</strong>${when}</p>`;
  }

  // Finish a match to charge its XI (league share and expenses); until then nothing in it touches balances.
  function finishBox(d, s) {
    const admin = isAdmin();
    if (d.finished) {
      return `<div class="finish-box done">
        <span>${icon('<path d="M20 6 9 17l-5-5"/>', 18)}<span><strong>Finished · charged</strong><small>${s.amount > 0 ? 'The XI’s league share and the expenses are' : 'The XI’s league share is'} in everyone’s balances.</small></span></span>
        ${admin ? `<button class="btn btn-ghost small" data-action="reopen-match" data-id="${esc(d.id)}">Reopen</button>` : ''}
      </div>${admin && d.type === 'match' ? `<div class="result-row"><span>Result</span>${RESULTS.map(([v, l]) => `<button class="tog ${d.result === v ? 'on' : ''}" data-action="set-result" data-id="${esc(d.id)}" data-result="${v}" aria-pressed="${d.result === v}">${l}</button>`).join('')}
        ${d.result ? `<button class="btn btn-ghost small" data-action="set-result" data-id="${esc(d.id)}" data-result="">Clear</button>` : ''}</div>` : ''}`;
    }
    return `<div class="finish-box">
      <span>${icon('<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>', 18)}<span><strong>Not charged yet</strong><small>${admin
        ? 'Pick the XI, add stats and expenses freely — balances only change when you finish the match.'
        : 'Balances update once the admin finishes this match.'}</small></span></span>
      ${admin ? `<button class="btn btn-gold" data-action="finish-match" data-id="${esc(d.id)}" ${s.n ? '' : 'disabled title="Pick the XI first"'}>${icon('<path d="M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z"/><path d="M4 22v-7"/>', 18)} Finish match</button>` : ''}
    </div>`;
  }

  // What a manager sees above the attendance list: Share, and where their proposal stands.
  function lineupBox(d, s) {
    const share = s.n > 0 ? `<div class="xi-actions"><button class="btn btn-gold" data-action="share-xi" data-id="${esc(d.id)}">${IMAGE_ICON} Share Playing XI</button></div>` : '';
    let status;
    const last = lastLineup(d.id);
    if (xiDirty[d.id]) {
      status = `<div class="lineup-note dirty"><span>Changes below haven’t been sent. The admin has to approve them.</span>
        <span class="lineup-btns"><button class="btn btn-gold small" data-action="xi-send" data-id="${esc(d.id)}">Send to admin</button>
        <button class="btn btn-ghost small" data-action="xi-discard" data-id="${esc(d.id)}">Discard</button></span></div>`;
    } else if (last && last.status === 'pending') {
      status = `<div class="lineup-note">Waiting for the admin to approve your lineup (${last.playing.length} playing). Change it below to send a new one.</div>`;
    } else if (last && last.status === 'declined') {
      status = `<div class="lineup-note declined">The admin declined your last lineup${last.note ? `: “${esc(last.note)}”` : '.'}</div>`;
    } else if (last && last.status === 'approved') {
      status = `<div class="lineup-note ok">The admin approved your last lineup.</div>`;
    } else {
      status = '<p class="muted xi-hint">Mark who’s playing below, then send it to the admin for approval.</p>';
    }
    return share + status;
  }

  function sheetDay() {
    const d = C.days(state).find(x => x.id === sheet.dayId);
    if (!d) return '';
    const stats = C.allStats(state);
    const s = stats[d.id];
    const isMatch = d.type === 'match';
    const max = state.settings.maxPerMatch;
    const full = isMatch && s.n >= max;
    const twelfthId = isMatch ? state.twelfth[d.id] : null;
    const rows = state.players.map((p, i) => `
      <div class="att-row ${p.att[d.id] ? 'is-on' : ''} ${twelfthId === p.id ? 'is-twelfth' : ''}">
        <span class="sr">${i + 1}</span>
        <span class="pname"><strong>${esc(p.name)}</strong><small>${esc(p.role) || '—'}</small></span>
        <span class="att-btns">
          ${canPickXi(d) ? `<button class="tog twelfth-tog ${twelfthId === p.id ? 'on' : ''}" data-action="toggle-twelfth" data-player="${esc(p.id)}" data-day="${esc(d.id)}"
            aria-pressed="${twelfthId === p.id}" title="${twelfthId === p.id ? 'Remove as 12th man' : 'Make 12th man (not charged)'}">12th</button>` : ''}
          ${toggleButton(p, d, stats, isMatch ? canPickXi(d) : isAdmin())}
        </span>
      </div>`).join('');
    return `
      <header class="sheet-head ${d.type}">
        <div>
          <span class="day-kind">${d.test ? 'Test match' : isMatch ? 'Match' : 'Training'}</span>
          <h2>${d.label}</h2>
          ${d.desc ? `<p class="muted">${esc(d.desc)}</p>` : ''}
          ${isMatch && matchInfo(d) ? `<p class="muted">${esc(matchInfo(d))}</p>` : ''}
        </div>
        <button class="icon-btn" data-action="close-sheet" aria-label="Close">${X_ICON}</button>
      </header>
      <div class="counter">
        <div><strong>${isMatch ? `${s.n} / ${max}` : s.n}</strong><span>${isMatch ? 'played' : 'arrived'}</span></div>
        <div><strong>${s.amount > 0 ? fmt(s.amount) : '—'}</strong><span>spent</span></div>
        <div><strong>${s.n > 0 && s.amount > 0 ? fmt(s.share) : '—'}</strong><span>each</span></div>
      </div>
      <div class="sheet-body">
 ${isMatch && !isAdmin() && canPickXi(d) ? lineupBox(d, s) : ''}
        ${isMatch && isAdmin() ? `<div class="xi-actions">
          <button class="btn btn-ghost" data-action="random-xi" data-id="${esc(d.id)}">${DICE_ICON} Random XI + 12th</button>
          ${s.n > 0 || twelfthId ? `<button class="btn btn-danger" data-action="clear-xi" data-id="${esc(d.id)}">${X_ICON} Clear XI</button>` : ''}
          ${s.n > 0 ? `<button class="btn btn-gold" data-action="share-xi" data-id="${esc(d.id)}">${IMAGE_ICON} Share Playing XI</button>` : ''}
          ${announceButton(d, 'btn-primary')}
        </div>
        ${s.n > 0 ? '' : '<p class="muted xi-hint">Mark who’s playing below, or pick at random, to share the Playing XI picture.</p>'}` : ''}
        ${isMatch && !d.test ? finishBox(d, s) : ''}
        ${d.test ? '<p class="test-note">A generated test match: it never changes anyone’s balance or the league fee.</p>' : ''}
        ${isMatch ? leagueLine(d, s) : ''}
        ${twelfthId ? `<p class="twelfth-line">12th man: <strong>${esc(playerName(twelfthId))}</strong> · not charged for this match</p>` : ''}
        ${isMatch && s.n > 0 ? battingSection(d) : ''}
        <h3 class="sub first">Expenses <span class="muted">${fmt(s.amount)}</span></h3>
        ${expenseRows(d)}
        <h3 class="sub">Attendance <span class="muted">${s.n}${isMatch ? ` / ${max}` : ''}</span></h3>
        ${full && (isMatch ? canPickXi(d) : isAdmin()) ? `<p class="hint">${max} players already played ${d.label}</p>` : ''}
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

  function advanceStatus(p, net) {
    const advance = C.advancePerPlayer(state);
    if (!(advance > 0) || !C.inTournament(p)) return '';
    const now = advanceNow(p, net);
    return now > 0
      ? `<p class="adv-status due">Advance: <strong>${fmt(now)}</strong> to pay now (of ${fmt(advance)})</p>`
      : `<p class="adv-status">Advance of ${fmt(advance)} paid ✓</p>`;
  }

  function sheetPlayer() {
    const idx = state.players.findIndex(x => x.id === sheet.id);
    const p = state.players[idx];
    if (!p) return '';
    const admin = isAdmin();
    const stats = C.allStats(state);
    const c = C.playerCalc(state, p, stats, fmt);
    // Test matches only exist for the stats; they never show in a player's attendance.
    const days = C.days(state).filter(d => d.type === 'training' || (!d.test && matchVisible(d)));
    const inTour = C.inTournament(p);

    const fields = admin ? `
      <div class="form-grid">
        <label><span>Name</span><input class="field" type="text" value="${esc(p.name)}" data-input="name" data-id="${esc(p.id)}" data-focus="pname-${esc(p.id)}" maxlength="60"></label>
        <label><span>Role</span><select class="field select" data-input="role" data-id="${esc(p.id)}">${ROLES.map(r => `<option value="${esc(r)}" ${r === p.role ? 'selected' : ''}>${r || 'No role'}</option>`).join('')}</select></label>
        <label><span>WhatsApp</span><input class="field ${p.phone && !waNumber(p.phone) ? 'invalid' : ''}" type="tel" inputmode="tel" placeholder="03xx xxxxxxx" value="${esc(p.phone)}" data-input="phone" data-id="${esc(p.id)}" data-focus="pphone-${esc(p.id)}" maxlength="20"></label>
        ${p.phone && !waNumber(p.phone) ? '<p class="field-hint">Not a valid mobile number, so reminders open the chat picker.</p>' : ''}
        <div class="form-row"><span>Tournament</span>
          <span class="tour-row">${tourToggle(p)}<small class="muted">${inTour ? 'Taking part · pays league fee for matches played' : 'Not taking part in the league'}</small></span></div>
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
        ${seesMoneyOf(p) ? `<div class="net-hero ${netClass(c.net)}">
          <span>${netText(c.net)}</span>
          <strong>${fmtNet(c.net)}</strong>
          <small>${sumLine}</small>
        </div>
        ${advanceStatus(p, c.net)}` : '<p class="private-note">Balances are private — you can only see your own.</p>'}
        ${admin ? `<div class="share-row">
          <button class="btn btn-whatsapp" data-action="share-player" data-id="${esc(p.id)}">${WA_ICON} ${c.net < 0 ? 'Send reminder' : 'Send on WhatsApp'}</button>
          <button class="btn btn-ghost" data-action="copy-player" data-id="${esc(p.id)}">${COPY_ICON} Copy</button>
        </div>` : ''}
        ${fields}
        ${seesMoneyOf(p) ? `<h3 class="sub">Payments <span class="muted">${fmt(c.paid)}</span></h3>
        ${paymentRows(p)}` : ''}
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
        id: p.id, name: p.name, role: p.role, paid: String(C.paidTotal(p) || ''), tournament: p.tournament, att: p.att, payments: p.payments,
        phone: p.phone
      })),
      amounts,
      trainings: state.trainings,
      matches: state.matches,
      expenses: state.expenses,
      tournament: state.tournament,
      twelfth: state.twelfth,
      battingOrder: state.battingOrder,
      stats: state.stats
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

  // The XI in batting order: the saved order first, then anyone playing who isn't in it, in squad order.
  function battingOrderOf(dayId) {
    const playing = state.players.filter(p => p.att[dayId]);
    const saved = (state.battingOrder[dayId] || []).map(findPlayer).filter(p => p && p.att[dayId]);
    return saved.concat(playing.filter(p => !saved.includes(p)));
  }

  // Fisher–Yates with crypto randomness, so every player has the same chance.
  function shuffle(list) {
    const a = list.slice();
    for (let i = a.length - 1; i > 0; i--) {
      const r = new Uint32Array(1);
      crypto.getRandomValues(r);
      const j = r[0] % (i + 1);
      [a[i], a[j]] = [a[j], a[i]];
    }
    return a;
  }

  // ---------- events ----------
  function findPlayer(id) { return state.players.find(p => p.id === id); }
  // Believable Hundred stats for a match's XI: 100 balls batted and bowled (5-ball sets, at most 20 balls
  // a bowler), wickets matched by outs and catches, a few extras and fielding moments. For trying things out.
  function randomStats(dayId) {
    const rnd = (a, b) => a + Math.floor(Math.random() * (b - a + 1));
    const pick = list => list[Math.floor(Math.random() * list.length)];
    const xi = battingOrderOf(dayId);
    const twelfth = findPlayer(state.twelfth[dayId]);
    const out = {};
    const entry = p => (out[p.id] = out[p.id] || C.statOf({}, '', ''));
    xi.forEach(entry);
    if (twelfth) entry(twelfth);

    // Batting: wickets lost decide how many batted; the top order faces more of the 100 balls.
    const lost = rnd(3, Math.min(9, xi.length - 2));
    const batters = xi.slice(0, Math.min(xi.length, lost + 2));
    const weights = batters.map((_, i) => (i < 2 ? 1.6 : i < 5 ? 1.25 : 0.6) * (0.5 + Math.random()));
    const total = weights.reduce((x, w) => x + w, 0);
    let left = 100;
    const ballsFaced = weights.map((w, i) => {
      const b = i === weights.length - 1 ? left : Math.max(1, Math.min(left - (weights.length - 1 - i), Math.round(100 * w / total)));
      left -= b;
      return b;
    });
    // The last batter in is always not out, plus one other; everyone else was dismissed.
    const notOut = new Set([batters.length - 1, rnd(0, batters.length - 2)]);
    batters.forEach((p, i) => {
      const e = entry(p);
      e.balls = ballsFaced[i];
      for (let b = 0; b < e.balls; b++) {
        const r = Math.random();
        if (r < 0.36) continue;                 // dot
        else if (r < 0.70) e.ones++;
        else if (r < 0.80) e.twos++;
        else if (r < 0.82) e.threes++;
        else if (r < 0.94) e.fours++;
        else e.sixes++;
      }
      e.out = !notOut.has(i);
    });

    // Bowling: 20 sets of 5 balls over 5–6 bowlers (bowlers and all-rounders first), max 4 sets each.
    const keen = xi.filter(p => /bowler|all-rounder/i.test(p.role));
    const others = xi.filter(p => !keen.includes(p));
    const bowlers = keen.concat(others).slice(0, Math.min(xi.length, rnd(5, 6)));
    const sets = bowlers.map(() => 1);
    for (let n = bowlers.length; n < 20; n++) {
      const open = sets.map((c, i) => c < 4 ? i : -1).filter(i => i >= 0);
      if (!open.length) break;
      sets[pick(open)]++;
    }
    const taken = rnd(3, 9);
    const credited = Math.max(0, taken - rnd(0, 1));   // the odd run-out isn't the bowler's
    bowlers.forEach((p, i) => {
      const e = entry(p);
      e.bowlBalls = sets[i] * 5;
      e.bowlRuns = Math.round(e.bowlBalls * (0.95 + Math.random() * 0.6));
      e.wides = Math.random() < 0.6 ? rnd(0, 3) : 0;
      e.noBalls = Math.random() < 0.25 ? 1 : 0;
    });
    for (let w = 0; w < credited; w++) {
      const byBalls = bowlers.flatMap((p, i) => Array(sets[i]).fill(p));
      entry(pick(byBalls)).wickets++;
    }

    // Fielding: about half the wickets are catches (keepers a little more often), plus drops and misfields.
    const keeper = xi.find(p => /keeper/i.test(p.role));
    const fielders = xi.concat(twelfth ? [twelfth] : []);
    for (let c = 0; c < Math.round(taken * (0.4 + Math.random() * 0.3)); c++) {
      entry(keeper && Math.random() < 0.3 ? keeper : pick(fielders)).catches++;
    }
    for (let i = rnd(0, 2); i > 0; i--) entry(pick(fielders)).drops++;
    for (let i = rnd(0, 3); i > 0; i--) entry(pick(fielders)).misfields++;
    for (let i = rnd(1, 4); i > 0; i--) entry(pick(fielders)).goodFielding++;
    return out;
  }

  function statEntry(m, pid) {
    state.stats[m] = state.stats[m] || {};
    if (!state.stats[m][pid]) state.stats[m][pid] = C.statOf(state, m, pid);
    return state.stats[m][pid];
  }
  // Matches that have an XI, 12th man or batting order; and how many stats entries exist.
  const xiCount = () => state.matches.filter(m => state.players.some(p => p.att[m.id]) || state.twelfth[m.id] || state.battingOrder[m.id]).length;
  const statCount = () => Object.values(state.stats).reduce((n, byPlayer) => n + Object.keys(byPlayer).length, 0);

  // Takes matches out of the ledger along with everything recorded against them.
  function dropMatches(ids) {
    const gone = new Set(ids);
    state.matches = state.matches.filter(m => !gone.has(m.id));
    ids.forEach(id => {
      delete state.expenses[id];
      delete state.twelfth[id];
      delete state.battingOrder[id];
      delete state.stats[id];
      state.players.forEach(p => { delete p.att[id]; });
    });
    if (sheet && gone.has(sheet.dayId)) sheet = null;
  }

  function findMatch(id) { return C.matches(state).find(m => m.id === id); }
  function findPayment(el) {
    const list = paymentsOf(el.dataset.player);
    return list && list.find(x => x.id === el.dataset.id);
  }
  function findExpense(el) { return (state.expenses[el.dataset.day] || []).find(e => e.id === el.dataset.id); }

  const actions = {
    tab(el) { tab = el.dataset.tab; saveTab(); sheet = null; render(); animateView(); window.scrollTo({ top: 0 }); if (tab === 'announce') loadAnnouncements(); },
    'ann-template'(el) {
      const x = ANN_TEMPLATES.find(y => y.id === el.dataset.t);
      if (!x) return;
      const m = realMatchesById(annDraft.matchId);
      annDraft.title = x.title;
      annDraft.body = x.make(m);
      annDraft.action = x.action || '';
      const at = x.at(m);
      if (at) { annDraft.later = true; annDraft.at = at; toast('Timed for the right moment — change it if you like.'); }
      render();
    },
    'ann-mode'(el) {
      annDraft.later = el.dataset.later === '1';
      if (annDraft.later && !annDraft.at) {
        const d = new Date(Date.now() + 3600000);
        d.setMinutes(0, 0, 0);
        const pad = n => String(n).padStart(2, '0');
        annDraft.at = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:00`;
      }
      render();
    },
    'ann-seg'(el) { annSeg = el.dataset.seg === 'poll' ? 'poll' : 'ann'; render(); },
    'ann-action-clear'() { annDraft.action = ''; render(); },
    'ann-clear'() { annDraft = { title: '', body: '', matchId: '', later: false, at: '', action: '' }; render(); },
    async 'ann-send'() {
      if (!isStaff()) return;
      const d = annDraft;
      if (!d.body.trim()) { toast('Write a message first.'); return; }
      let sendAt = '';
      if (d.later) {
        const when = new Date(d.at);
        if (!d.at || isNaN(when.getTime())) { toast('Pick when to send it.'); return; }
        sendAt = when.toISOString();
      }
      try {
        await api('POST', '/api/announcements', { title: d.title, body: d.body, matchId: d.matchId, sendAt, action: d.action });
        const later = d.later;
        annDraft = { title: '', body: '', matchId: '', later: false, at: '', action: '' };
        await loadAnnouncements();
        render();
        toast(later ? 'Scheduled. It will appear for everyone at that time.' : 'Sent. It now shows in everyone’s notifications.');
      } catch (e) { toast(e.message || 'Could not send it.'); }
    },
    'ann-duplicate'(el) {
      const a = (announcements || []).find(x => x.id === el.dataset.id);
      if (!a) return;
      annDraft = { title: a.title, body: a.body, matchId: a.matchId || '', later: false, at: '', action: a.action || '' };
      render();
      const box = $('#ann-composer');
      if (box) box.scrollIntoView({ behavior: 'smooth', block: 'start' });
      toast('Copied into the form — edit it and send.');
    },
    async 'ann-delete'(el) {
      const a = (announcements || []).find(x => x.id === el.dataset.id);
      if (!a) return;
      const ok = await confirmDialog('Delete this announcement?', a.status === 'scheduled' ? 'It will not be sent.' : 'It disappears from everyone’s notifications.', 'Delete', true);
      if (!ok) return;
      try { await api('DELETE', '/api/announcements/' + encodeURIComponent(a.id)); } catch (e) { toast(e.message || 'Could not delete it.'); }
      await loadAnnouncements();
    },
    'mom-vote'(el) { engageCall('PUT', `/api/engage/mom/${encodeURIComponent(el.dataset.match)}`, { candidateId: el.dataset.player }, 'Vote saved.'); },
    async 'mom-close'(el) {
      if (!await confirmDialog('Announce the winner?', 'Voting closes and everyone gets a notification with the man of the match.', 'Announce')) return;
      engageCall('POST', `/api/engage/mom/${encodeURIComponent(el.dataset.match)}/close`, null, 'Winner announced.');
    },
    'mom-reopen'(el) { engageCall('POST', `/api/engage/mom/${encodeURIComponent(el.dataset.match)}/reopen`, null, 'Voting reopened.'); },
    async 'predict'(el) {
      const m = C.matches(state).find(x => x.id === el.dataset.match);
      const label = el.dataset.pick === 'win' ? 'Red Falcons to win' : `${(m && m.opponent) || 'The opponent'} to win`;
      if (!await confirmDialog('Lock in your prediction?', `${label}. Once you lock it in, it can’t be changed.`, 'Lock it in')) return;
      engageCall('PUT', `/api/engage/predict/${encodeURIComponent(el.dataset.match)}`, { pick: el.dataset.pick }, 'Prediction locked in.');
    },
    'open-predictions'(el) { sheet = { type: 'predictions', id: el.dataset.match, back: sheet && sheet.type !== 'predictions' ? sheet : null }; render(); },
    'sheet-back'() { sheet = (sheet && sheet.back) || null; render(); },
    'go-settings'() {
      sheet = null;
      tab = 'settings';
      render();
      const t = document.getElementById('set-account');
      if (t) window.scrollTo({ top: t.getBoundingClientRect().top + window.scrollY - (window.innerWidth <= 960 ? 140 : 90), behavior: 'smooth' });
    },
    'poll-vote'(el) { engageCall('PUT', `/api/engage/polls/${encodeURIComponent(el.dataset.id)}/vote`, { option: Number(el.dataset.i) }); },
    'poll-add-opt'() { if (pollDraft.opts.length < 5) { pollDraft.opts.push(''); render(); } },
    async 'poll-ask'() {
      const opts = pollDraft.opts.map(o => o.trim()).filter(Boolean);
      if (!pollDraft.q.trim() || opts.length < 2) { toast('Write a question and at least two answers.'); return; }
      try {
        await api('POST', '/api/engage/polls', { question: pollDraft.q.trim(), options: opts });
        pollDraft.q = ''; pollDraft.opts = ['Yes', 'No', 'Maybe'];
        await loadEngage();
        render();
        toast('Poll sent to the team.');
      } catch (e) { toast(e.message || 'Could not send the poll.'); }
    },
    'poll-toggle'(el) { engageCall('POST', `/api/engage/polls/${encodeURIComponent(el.dataset.id)}/toggle`, null); },
    async 'poll-delete'(el) {
      if (!await confirmDialog('Delete this poll?', 'The answers go with it.', 'Delete', true)) return;
      engageCall('DELETE', `/api/engage/polls/${encodeURIComponent(el.dataset.id)}`, null, 'Poll deleted.');
    },
    'set-result'(el) {
      if (!isAdmin()) return;
      const m = state.matches.find(x => x.id === el.dataset.id);
      if (!m) return;
      m.result = el.dataset.result || '';
      commit();
      setTimeout(loadEngage, 900);
    },
    async 'notes-save'(el) {
      const id = el.dataset.match;
      const n = notesDraft[id] || notesCache[id];
      try {
        notesCache[id] = await api('PUT', `/api/engage/notes/${encodeURIComponent(id)}`, { fieldPlan: n.fieldPlan, battingPlan: n.battingPlan, opposition: n.opposition });
        delete notesDraft[id];
        render();
        toast('Notes saved.');
      } catch (e) { toast(e.message || 'Could not save the notes.'); }
    },
    'open-profile-form'() { openProfileForm(); },
    'ask-profile'() {
      const x = ANN_TEMPLATES.find(y => y.id === 'profile');
      annDraft = { title: x.title, body: x.make(), matchId: '', later: false, at: '', action: 'profile' };
      tab = 'announce';
      render();
      toast('Review the message, then press Send.');
    },
    async 'profile-form-save'() {
      const me = pForm && findPlayer(pForm.id);
      if (!me) return;
      try {
        await api('PUT', `/api/players/${encodeURIComponent(me.id)}/profile`, {
          battingHand: pForm.bat, bowlingArm: pForm.arm, bowlingStyle: pForm.style, jersey: pForm.jersey, jerseyName: pForm.jerseyName, name: pForm.name, submit: true
        });
        Object.assign(me, { name: pForm.name.trim().replace(/\s+/g, ' '), battingHand: pForm.bat, bowlingArm: pForm.arm, bowlingStyle: pForm.style, jersey: pForm.jersey.replace(/\D/g, '').slice(0, 3),
          jerseyName: pForm.jerseyName.trim().slice(0, 24), profileAt: new Date().toISOString() });
        pForm = null;
        sheet = null;
        render();
        toast('Thanks! Your profile is saved and the admin has been told.');
      } catch (e) { toast(e.message || 'Could not save your profile.'); }
    },
    'gallery-add'(el) { galleryTarget = el.dataset.match; $('#gallery-file').click(); },
    'open-photo'(el) { sheet = { type: 'photo', id: el.dataset.id, back: sheet && sheet.type !== 'photo' ? sheet : (sheet && sheet.back) || null }; render(); },
    'photo-back'() { sheet = sheet.back || null; render(); },
    'photo-nav'(el) {
      const p = (gallery || []).find(x => x.id === sheet.id);
      const sibs = p ? photosOf(p.matchId) : [];
      if (sibs.length < 2) return;
      const i = sibs.findIndex(x => x.id === sheet.id);
      sheet = { ...sheet, id: sibs[(i + Number(el.dataset.dir) + sibs.length) % sibs.length].id };
      render();
    },
    async 'photo-delete'(el) {
      if (!await confirmDialog('Remove this photo?', 'It will be taken down for everyone.', 'Remove', true)) return;
      try {
        await api('DELETE', `/api/match-photos/${encodeURIComponent(el.dataset.id)}`);
        gallery = (gallery || []).filter(x => x.id !== el.dataset.id);
        sheet = sheet.back || null;
        render();
        toast('Photo removed.');
      } catch (e) { toast(e.message || 'Could not remove it.'); }
    },
    'open-scorecard'(el) { sheet = { type: 'scorecard', id: el.dataset.id }; render(); },
    'cal-prev'() { calMonth = calMonth.m === 0 ? { y: calMonth.y - 1, m: 11 } : { y: calMonth.y, m: calMonth.m - 1 }; render(); },
    'cal-next'() { calMonth = calMonth.m === 11 ? { y: calMonth.y + 1, m: 0 } : { y: calMonth.y, m: calMonth.m + 1 }; render(); },
    'cal-today'() { calMonth = null; render(); },
    'p-flag'(el) {
      const p = findPlayer(el.dataset.id);
      const k = el.dataset.flag;
      if (!isAdmin() || !p || !['captain', 'viceCaptain', 'keeper'].includes(k)) return;
      p[k] = !p[k];
      commit();
    },
    'photo-pick'(el) { photoTarget = el.dataset.id; $('#photo-file').click(); },
    async 'photo-remove'(el) {
      const p = findPlayer(el.dataset.id);
      if (!p) return;
      try {
        await photoApi('DELETE', p.id);
        p.photoAt = '';
        render();
        toast('Photo removed.');
      } catch (e) { toast(e.message || 'Could not remove the photo.'); }
    },
    'open-profile'(el) { sheet = { type: 'profile', id: el.dataset.id }; render(); },
    'open-player'(el) { sheet = { type: 'player', id: el.dataset.id }; render(); },
    'open-day'(el) { sheet = { type: 'day', dayId: el.dataset.id }; render(); },
    'close-sheet'() { sheet = null; render(); },
    async logout() {
      const ok = await confirmDialog('Switch user?', 'You will go back to the start screen.', 'Switch user');
      if (!ok) return;
      if (saveTimer) { clearTimeout(saveTimer); await flushSave(); }
      if (session && session.token) await api('POST', '/api/logout').catch(() => {});
      if (session && session.managerToken) await api('POST', '/api/manager/logout').catch(() => {});
      session = null;
      writeStore(SESSION_KEY, null);
      writeStore(ME_KEY, null);
      resetView();
      showSplash();
    },
    'toggle-att'(el) {
      const p = findPlayer(el.dataset.player);
      const day = C.days(state).find(d => d.id === el.dataset.day);
      if (!p || !day) return;
      if (day.type === 'match' ? !canPickXi(day) : !isAdmin()) return;
      if (p.att[day.id]) {
        delete p.att[day.id];
      } else {
        const n = C.dayStats(state, day).n;
        if (day.type === 'match' && n >= state.settings.maxPerMatch) {
          toast(`${state.settings.maxPerMatch} players already played ${day.label}`);
          return;
        }
        p.att[day.id] = true;
        if (state.twelfth[day.id] === p.id) delete state.twelfth[day.id];
      }
      touchXi(day.id);
    },
    'toggle-twelfth'(el) {
      const p = findPlayer(el.dataset.player);
      const day = el.dataset.day;
      if (!p || !canPickXi(findMatch(day))) return;
      if (state.twelfth[day] === p.id) {
        delete state.twelfth[day];
      } else {
        delete p.att[day];
        state.twelfth[day] = p.id;
      }
      touchXi(day);
    },
    async 'mgr-request'() {
      const phrase = mgrDraft.phrase.trim();
      if (phrase.length < 8) { toast('The phrase needs at least 8 characters.'); return; }
      try {
        const r = await api('POST', '/api/manager/request', { name: session.name, phrase, label: deviceLabel() });
        setMgr({ token: r.token, name: session.name, status: 'pending' });
        mgrDraft.phrase = '';
        toast('Request sent to the admin.');
        render();
      } catch (e) { toast(e.message || 'Could not send the request.'); }
    },
    async 'mgr-cancel'() {
      if (!mgr) return;
      await api('DELETE', '/api/manager/request').catch(() => {});
      setMgr(null);
      render();
    },
    async 'mgr-save-phrase'(el) {
      if (!realAdmin()) return;
      const phrase = (mgrDraft.edits[el.dataset.id] || '').trim();
      try {
        await api('PUT', '/api/manager/requests/' + encodeURIComponent(el.dataset.id) + '/phrase', { phrase });
        delete mgrDraft.edits[el.dataset.id];
        mgrList = null; await loadInbox();
        toast('Phrase changed. That manager is signed out.');
      } catch (e) { toast(e.message || 'Could not change the phrase.'); }
    },
    async 'mgr-remove'(el) {
      if (!realAdmin()) return;
      const m = (mgrList || []).find(x => x.id === el.dataset.id);
      const ok = await confirmDialog(`Revoke ${m ? m.name : 'manager'}’s access?`, 'They go back to view-only straight away. They can ask again.', 'Revoke', true);
      if (!ok) return;
      try { await api('DELETE', '/api/manager/requests/' + encodeURIComponent(el.dataset.id)); } catch { toast('Could not do that.'); }
      await loadInbox();
      toast('Access revoked.');
    },
    'open-inbox'() {
      if (realAdmin()) { sheet = { type: 'inbox' }; markAdminSeen(); loadInbox(); loadAdminUpdates(); } else { sheet = { type: 'feed' }; markFeedSeen(); loadFeed(); }
      render();
    },
    async 'inbox-approve'(el) {
      if (!realAdmin()) return;
      // Anything I have changed but not saved goes first, so approving a lineup doesn't clobber it.
      if (saveTimer) { clearTimeout(saveTimer); await flushSave(); }
      const path = el.dataset.kind === 'access' ? '/api/manager/requests/' : '/api/manager/lineups/';
      try {
        await api('POST', path + encodeURIComponent(el.dataset.id) + '/approve');
        if (el.dataset.kind === 'lineup') state = await fetchLedger();
        toast(el.dataset.kind === 'access' ? 'Manager approved.' : 'Lineup approved and applied.');
      } catch (e) { toast(e.message || 'Could not approve it.'); }
      await loadInbox();
      render();
    },
    'inbox-decline-open'(el) { declineDraft[el.dataset.key] = ''; render(); },
    'inbox-decline-cancel'(el) { delete declineDraft[el.dataset.key]; render(); },
    async 'inbox-decline-send'(el) {
      if (!realAdmin()) return;
      const key = `${el.dataset.kind}:${el.dataset.id}`;
      const path = el.dataset.kind === 'access' ? '/api/manager/requests/' : '/api/manager/lineups/';
      try {
        await api('POST', path + encodeURIComponent(el.dataset.id) + '/decline', { note: (declineDraft[key] || '').trim() });
        toast('Declined.');
      } catch (e) { toast(e.message || 'Could not decline it.'); }
      delete declineDraft[key];
      await loadInbox();
      render();
    },
    async 'xi-send'(el) {
      if (!isManager()) return;
      const matchId = el.dataset.id;
      const playing = state.players.filter(p => p.att[matchId]).map(p => p.id);
      try {
        await api('POST', '/api/manager/lineups', { matchId, playing, twelfth: state.twelfth[matchId] || '',
          batting: state.battingOrder[matchId] ? battingOrderOf(matchId).map(p => p.id) : [] });
        delete xiDirty[matchId];
        await revertMatch(matchId);
        await loadMyLineups();
        toast('Sent to the admin for approval.');
      } catch (e) {
        if (e.status === 403) { await refreshManager(); return; }
        toast(e.message || 'Could not send it.');
      }
    },
    async 'xi-discard'(el) {
      delete xiDirty[el.dataset.id];
      try { await revertMatch(el.dataset.id); } catch { render(); }
    },
    'bat-move'(el) {
      const day = el.dataset.day;
      if (!canPickXi(findMatch(day))) return;
      const order = battingOrderOf(day).map(p => p.id);
      const i = order.indexOf(el.dataset.id);
      const j = i + (el.dataset.dir === 'up' ? -1 : 1);
      if (i < 0 || j < 0 || j >= order.length) return;
      [order[i], order[j]] = [order[j], order[i]];
      state.battingOrder[day] = order;
      touchXi(day);
    },
    'bat-reset'(el) {
      if (!canPickXi(findMatch(el.dataset.day))) return;
      delete state.battingOrder[el.dataset.day];
      touchXi(el.dataset.day);
    },
    async 'announce-xi'(el) {
      if (!isAdmin()) return;
      const m = findMatch(el.dataset.id);
      if (!m || !xiOf(m).length) return;
      const ok = await confirmDialog(`Announce the Playing XI for ${m.label}?`,
        `The ${xiOf(m).length} players${state.twelfth[m.id] ? ' and the 12th man' : ''} show on everyone’s home screen and in See details. Picking a new XI hides it again until you announce.`,
        'Announce');
      if (!ok) return;
      setAnnounced(m.id, true);
      commit();
      toast(`${m.label} Playing XI announced.`);
    },
    async 'unannounce-xi'(el) {
      if (!isAdmin()) return;
      const m = findMatch(el.dataset.id);
      if (!m) return;
      const ok = await confirmDialog(`Hide the Playing XI for ${m.label}?`, 'Players will see the full squad again until you announce.', 'Hide XI');
      if (!ok) return;
      setAnnounced(m.id, false);
      commit();
      toast(`${m.label} Playing XI hidden.`);
    },
    async 'finish-match'(el) {
      if (!isAdmin()) return;
      const d = findMatch(el.dataset.id);
      const m = d && state.matches.find(x => x.id === d.id);
      if (!m) return;
      const stats = C.allStats(state);
      const s = stats[d.id];
      if (!s.n) { toast('Pick the XI first.'); return; }
      const tour = C.tournamentStats(state, stats);
      const parts = [];
      if (tour.fee > 0) parts.push(`League ${fmt(tour.perMatch)} ÷ ${s.n} = ${fmt(C.round2(tour.perMatch / s.n))} each`);
      if (s.amount > 0) parts.push(`expenses ${fmt(s.amount)} ÷ ${s.n} = ${fmt(s.share)} each`);
      const ok = await confirmDialog(`Finish ${d.label}?`,
        `${parts.length ? parts.join(', and ') + ' — charged' : 'Charged'} to the ${s.n} who played${state.twelfth[d.id] ? ' (not the 12th man)' : ''}. Balances update straight away; you can reopen it if needed.`,
        'Finish & charge');
      if (!ok) return;
      m.finished = true;
      commit();
      toast(`${d.label} finished — balances updated.`);
    },
    async 'reopen-match'(el) {
      if (!isAdmin()) return;
      const d = findMatch(el.dataset.id);
      const m = d && state.matches.find(x => x.id === d.id);
      if (!m) return;
      const ok = await confirmDialog(`Reopen ${d.label}?`, 'Its league share and expenses come off the XI’s balances until you finish it again.', 'Reopen');
      if (!ok) return;
      m.finished = false;
      commit();
      toast(`${d.label} reopened — not charged until you finish it.`);
    },
    async 'clear-xi'(el) {
      const day = findMatch(el.dataset.id);
      if (!day || !isAdmin()) return;
      const n = state.players.filter(p => p.att[day.id]).length;
      const ok = await confirmDialog(`Clear the XI for ${day.label}?`,
        `Removes the ${n} playing${state.twelfth[day.id] ? ' and the 12th man' : ''}. Until a new XI is picked, this match's league fee${C.dayAmount(state, day.id) > 0 ? ' is split across everyone taking part, and its expenses aren\'t assigned to anyone' : ' is split across everyone taking part'}.`,
        'Clear XI', true);
      if (!ok) return;
      state.players.forEach(p => { delete p.att[day.id]; });
      delete state.twelfth[day.id];
      delete state.battingOrder[day.id];
      setAnnounced(day.id, false);
      commit();
      toast(`${day.label} XI cleared.`);
    },
    async 'random-xi'(el) {
      const day = findMatch(el.dataset.id);
      if (!day || !isAdmin()) return;
      const pool = state.players.filter(C.inTournament);
      if (!pool.length) { toast('No players in the tournament to pick from.'); return; }
      const picked = state.players.some(p => p.att[day.id]) || state.twelfth[day.id];
      if (picked) {
        const ok = await confirmDialog(`Pick a random XI for ${day.label}?`,
          'This replaces who is playing and the 12th man. Any expenses for this match are split among the new XI.', 'Pick at random');
        if (!ok) return;
      }
      const shuffled = shuffle(pool);
      const xi = shuffled.slice(0, state.settings.maxPerMatch);
      state.players.forEach(p => { delete p.att[day.id]; });
      delete state.battingOrder[day.id];
      setAnnounced(day.id, false);   // a new XI isn't public until it's announced again
      xi.forEach(p => { p.att[day.id] = true; });
      if (shuffled[xi.length]) state.twelfth[day.id] = shuffled[xi.length].id; else delete state.twelfth[day.id];
      commit();
      toast(`Picked ${xi.length} players${shuffled[xi.length] ? ` and ${shuffled[xi.length].name} as 12th man` : ''}.`);
    },
    async 'clear-all-xis'() {
      if (!isAdmin()) return;
      const n = xiCount();
      if (!n) return;
      const ok = await confirmDialog('Clear all playing XIs?',
        `Removes the XI, 12th man and batting order from ${n} match${n === 1 ? '' : 'es'}. League fees go back to being split across everyone until new XIs are picked, and match expenses aren’t charged to anyone. Stats stay.`,
        'Clear all XIs', true);
      if (!ok) return;
      state.matches.forEach(m => { state.players.forEach(p => { delete p.att[m.id]; }); });
      state.twelfth = {};
      state.battingOrder = {};
      commit();
      toast(`Playing XIs cleared from ${n} match${n === 1 ? '' : 'es'}.`);
    },
    async 'clear-all-stats'() {
      if (!isAdmin()) return;
      const n = statCount();
      if (!n) return;
      const matchesWithStats = Object.keys(state.stats).filter(m => Object.keys(state.stats[m]).length).length;
      const ok = await confirmDialog('Clear all stats?',
        `Removes ${n} player stat${n === 1 ? '' : 's'} across ${matchesWithStats} match${matchesWithStats === 1 ? '' : 'es'} — batting, bowling, extras and fielding. Players, matches and money aren’t affected.`,
        'Clear all stats', true);
      if (!ok) return;
      state.stats = {};
      commit();
      toast('All stats cleared.');
    },
    async 'tfa-start'() {
      try {
        const r = await api('POST', '/api/2fa/setup');
        let qr = '';
        try {
          await loadQrLib();
          const q = window.qrcode(0, 'M');
          q.addData(r.uri);
          q.make();
          qr = q.createSvgTag({ cellSize: 5, margin: 2, scalable: true });
        } catch { /* the manual key and link still work */ }
        tfaSetup = { ...r, qr };
        tfaDraft.code = '';
        render();
      } catch { toast('Could not start setup. Try again.'); }
    },
    'tfa-cancel'() { tfaSetup = null; render(); },
    async 'tfa-confirm'() {
      try {
        const r = await api('POST', '/api/2fa/confirm', { code: tfaDraft.code.replace(/\s/g, ''), label: deviceLabel() });
        writeStore(DEVICE_KEY, r.device);
        tfaCodes = r.recoveryCodes;
        tfaSetup = null;
        tfaDraft.code = '';
        render();
        loadTfa();
      } catch (e) { toast(e.message || 'That code didn’t match.'); }
    },
    'tfa-copy'() { copyText('Red Falcons admin recovery codes\n' + tfaCodes.join('\n')); },
    'tfa-done'() { tfaCodes = null; render(); },
    async 'tfa-revoke'(el) {
      const current = el.dataset.current === 'true';
      const ok = await confirmDialog('Stop trusting this browser?', current
        ? 'This browser will ask for a code next time you sign in as admin.'
        : 'That browser will need a code next time it signs in as admin.', 'Stop trusting', true);
      if (!ok) return;
      try {
        await api('DELETE', '/api/2fa/devices/' + encodeURIComponent(el.dataset.id));
        if (current) writeStore(DEVICE_KEY, null);
      } catch { toast('Could not do that.'); }
      loadTfa();
    },
    async 'tfa-disable'() {
      const ok = await confirmDialog('Turn two-factor off?', 'Signing in as admin will only need the admin key again. Trusted browsers and recovery codes are removed.', 'Turn off', true);
      if (!ok) return;
      try {
        await api('POST', '/api/2fa/disable', { code: tfaDraft.off.replace(/\s/g, '') });
        tfaDraft.off = '';
        writeStore(DEVICE_KEY, null);
        toast('Two-factor is off.');
      } catch (e) { toast(e.message || 'That code didn’t work.'); }
      loadTfa();
    },
    'push-on'() { enablePush(); },
    'push-dismiss'() { writeStore(PUSH_PROMPT_KEY, 1); render(); },
    'push-off'() { disablePush(); },
    'push-template'(el) { pushDraft.title = el.dataset.title; pushDraft.body = el.dataset.body; render(); },
    async 'push-send'(el) {
      if (!isAdmin() || !pushDraft.body.trim()) return;
      const aud = pushAudience();
      if (aud.ids && !aud.ids.length) { toast('Nobody to send to.'); return; }
      el.disabled = true;
      try {
        const r = await api('POST', '/api/push/send', { title: pushDraft.title, body: pushDraft.body, playerIds: aud.ids });
        toast(r.devices ? `Sent to ${r.devices} device${r.devices === 1 ? '' : 's'}.` : 'No one in that group has notifications on yet.');
        if (r.devices) { pushDraft.body = ''; render(); }
      } catch {
        toast('Could not send. Try again.');
      } finally {
        el.disabled = false;
      }
    },
    'open-season'(el) { openSeason(el.dataset.id); },
    'leave-season'() { leaveSeason(); },
    async 'archive-season'() {
      if (!isAdmin()) return;
      const name = (seasonDraft === null ? seasonName() : seasonDraft).trim() || seasonName();
      try {
        await archiveSeason(name);
        seasonDraft = null;
        render();
        toast(`“${name}” saved to the archive. The current season carries on.`);
      } catch {
        toast('Could not archive the season. Try again.');
      }
    },
    async 'delete-season'(el) {
      if (!isAdmin()) return;
      const x = (seasons || []).find(s => s.id === el.dataset.id);
      if (!x) return;
      const ok = await confirmDialog(`Delete “${x.name}”?`, 'This archived season is removed for good.', 'Delete', true);
      if (!ok) return;
      try { await api('DELETE', '/api/seasons/' + encodeURIComponent(x.id)); } catch { toast('Could not delete it.'); }
      await loadSeasons();
    },
    'set-jump'(el) {
      const target = document.getElementById('set-' + el.dataset.id);
      // On phones the section menu sticks under the header, so leave room for both.
      const offset = window.innerWidth <= 960 ? 140 : 90;
      if (target) window.scrollTo({ top: target.getBoundingClientRect().top + window.scrollY - offset, behavior: 'smooth' });
    },
    'set-admin-fixtures'() {
      if (!isAdmin()) return;
      state.settings.adminShowFixtures = !state.settings.adminShowFixtures;
      commit();
      toast(`Fixtures calendar is now ${state.settings.adminShowFixtures ? 'shown' : 'hidden'} on your Home.`);
    },
    'set-summary-card'(el) {
      const key = el.dataset.key;
      if (!isAdmin() || !['showNextMatch', 'showFixtures'].includes(key)) return;
      state.settings[key] = !state.settings[key];
      commit();
      toast(`${key === 'showNextMatch' ? 'Next match card' : 'Fixtures calendar'} is now ${state.settings[key] ? 'shown to players' : 'hidden from players'}.`);
    },
    'set-tab-visibility'(el) {
      if (!isAdmin()) return;
      const key = el.dataset.key;
      if (key !== 'statsVisibility' && key !== 'daysVisibility') return;
      state.settings[key] = state.settings[key] === 'all' ? 'admin' : 'all';
      commit();
      const name = key === 'daysVisibility' ? 'Days' : 'Stats';
      toast(state.settings[key] === 'all' ? `Players can now see the ${name} tab.` : `${name} is now admin only.`);
    },
    async 'random-stats'(el) {
      const d = findMatch(el.dataset.id);
      if (!d || !isAdmin()) return;
      const has = Object.keys(state.stats[d.id] || {}).length > 0;
      if (has) {
        const ok = await confirmDialog(`Random stats for ${d.label}?`, 'This replaces every stat already entered for this match.', 'Replace with random', true);
        if (!ok) return;
      }
      state.stats[d.id] = randomStats(d.id);
      commit();
      toast(`${d.label}: random stats added.`);
    },
    'set-access'(el) {
      if (!isAdmin()) return;
      state.settings.viewerAccess = el.dataset.access === 'own' ? 'own' : 'all';
      commit();
    },
    async 'install-app'() {
      if (!installPrompt) return;
      // The browser's prompt can only be shown once; it offers a fresh one later if dismissed.
      const prompt = installPrompt;
      installPrompt = null;
      prompt.prompt();
      await prompt.userChoice.catch(() => null);
      render();
    },
    'stats-sel'(el) { statsSel = el.dataset.id; render(); },
    'stat-step'(el) {
      if (!isAdmin()) return;
      const e = statEntry(el.dataset.m, el.dataset.p);
      const d = parseInt(el.dataset.d, 10);
      const k = el.dataset.k;
      if (e[k] + d < 0) return;
      e[k] += d;
      // A scoring shot is a ball faced, so live scoring only needs one tap.
      if (['ones', 'twos', 'threes', 'fours', 'sixes'].includes(k)) e.balls = Math.max(0, e.balls + d);
      commit();
    },
    'stat-out'(el) {
      if (!isAdmin()) return;
      const e = statEntry(el.dataset.m, el.dataset.p);
      e.out = !e.out;
      commit();
    },
    'set-theme'(el) {
      writeStore(THEME_KEY, el.dataset.theme);
      applyTheme(el.dataset.theme);
      render();
    },
    'fold-group'(el) {
      const folded = readStore(COLLAPSED_KEY) || {};
      folded[el.dataset.type] = !folded[el.dataset.type];
      writeStore(COLLAPSED_KEY, folded);
      render();
    },
    'add-match'() {
      if (!isAdmin()) return;
      const folded = readStore(COLLAPSED_KEY) || {};
      if (folded.match) { folded.match = false; writeStore(COLLAPSED_KEY, folded); }
      state.matches.push({ id: 'm' + rid(), date: today(), time: '', opponent: '', venue: '', finished: false, announced: false });
      commit();
      toast(`Match ${realMatchCount()} added — set its date and opponent.`);
    },
    // Five generated matches, each with a random playing XI, 12th man and stats, to try the app out.
    // They are flagged as test matches: never charged, kept apart, and removed in one go.
    async 'add-test-matches'() {
      if (!isAdmin()) return;
      const pool = state.players.filter(C.inTournament);
      if (pool.length < 5) { toast('Add at least 5 players taking part in the league first.'); return; }
      const old = state.matches.filter(m => m.test).length;
      const ok = await confirmDialog('Add 5 test matches?',
        `${old ? `The ${old} test matches already here are replaced. ` : ''}Each gets a random playing XI, 12th man and random stats. They never change anyone’s balance or the league fee, and you can delete them all with one button.`,
        'Add test matches');
      if (!ok) return;
      dropMatches(state.matches.filter(m => m.test).map(m => m.id));
      const folded = readStore(COLLAPSED_KEY) || {};
      if (folded.test) { folded.test = false; writeStore(COLLAPSED_KEY, folded); }
      for (let i = 0; i < 5; i++) {
        const id = 'x' + rid();
        const when = new Date(Date.now() - (5 - i) * 7 * 86400000).toLocaleDateString('en-CA');
        state.matches.push({ id, date: when, opponent: `Test XI ${i + 1}`, venue: 'Test ground', finished: false, test: true });
        const shuffled = shuffle(pool);
        const xi = shuffled.slice(0, state.settings.maxPerMatch);
        xi.forEach(p => { p.att[id] = true; });
        if (shuffled[xi.length]) state.twelfth[id] = shuffled[xi.length].id;
        state.stats[id] = randomStats(id);
      }
      commit();
      toast('5 test matches added, with random XIs and stats.');
    },
    async 'remove-test-matches'() {
      if (!isAdmin()) return;
      const ids = state.matches.filter(m => m.test).map(m => m.id);
      if (!ids.length) return;
      const ok = await confirmDialog(`Delete all ${ids.length} test matches?`, 'Their playing XIs, 12th men, batting orders and stats are removed. Real matches and money are not touched.', 'Delete test matches', true);
      if (!ok) return;
      dropMatches(ids);
      commit();
      toast('Test matches deleted.');
    },
    async 'remove-match'(el) {
      const day = findMatch(el.dataset.id);
      if (!day) return;
      const ok = await confirmDialog(`Remove ${day.label}?`, 'Its XI, 12th man, batting order and expenses will be removed. Later matches are renumbered.', 'Remove', true);
      if (!ok) return;
      state.matches = state.matches.filter(m => m.id !== day.id);
      delete state.expenses[day.id];
      delete state.twelfth[day.id];
      delete state.battingOrder[day.id];
      delete state.stats[day.id];
      state.players.forEach(p => { delete p.att[day.id]; });
      if (sheet && sheet.dayId === day.id) sheet = null;
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
      names.forEach(name => state.players.push({ id: rid(), name, role: '', tournament: true, att: {}, payments: [], phone: '' }));
      draftNames = '';
      commit();
      toast(`${names.length} player${names.length === 1 ? '' : 's'} added.`);
    },
    async 'remove-player'(el) {
      const p = findPlayer(el.dataset.id);
      if (!p) return;
      const bought = Object.values(state.expenses).flat().filter(e => e.paidBy === p.id).length;
      const ok = await confirmDialog(`Remove ${p.name || 'this player'}?`,
        'Their attendance, payments and stats will be removed.' + (bought ? ` ${bought} item${bought === 1 ? '' : 's'} they paid for will be marked as paid by the team fund.` : ''),
        'Remove', true);
      if (!ok) return;
      state.players = state.players.filter(x => x.id !== p.id);
      Object.keys(state.twelfth).forEach(m => { if (state.twelfth[m] === p.id) delete state.twelfth[m]; });
      Object.values(state.stats).forEach(byPlayer => { delete byPlayer[p.id]; });
      Object.values(state.expenses).forEach(list => list.forEach(e => { if (e.paidBy === p.id) e.paidBy = ''; }));
      commit();
    },
    export() { exportBackup(); },
    import() { $('#import-file').click(); },
    async 'new-series'() {
      const name = seasonName();
      const ok = await confirmDialog('Start a new series?', `The current season is saved to the archive first as “${name}”. Then players, names and roles stay; attendance, payments, expenses, matches, stats, the league fee, advance and payments to management are cleared, and trainings reset to Training 1.`, 'Archive & start new', true);
      if (!ok) return;
      try {
        await archiveSeason(name);
      } catch {
        toast('Could not archive the season, so nothing was cleared. Try again.');
        return;
      }
      state.players.forEach(p => { p.att = {}; p.payments = []; });
      state.expenses = {};
      state.tournament = { name: '', fee: '', advance: '', matchCount: 5, payments: [] };
      state.matches = [];
      state.stats = {};
      state.twelfth = {};
      state.battingOrder = {};
      state.trainings = [{ id: 't1', desc: '' }];
      commit();
      toast(`“${name}” archived. New series started.`);
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
    'share-player'(el) { const p = findPlayer(el.dataset.id); if (p) openWhatsApp(playerMessage(p), p.phone); },
    'copy-player'(el) { const p = findPlayer(el.dataset.id); if (p) copyText(playerMessage(p)); },
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
    async 'share-squad'(el) {
      if (!isAdmin()) return;
      if (!state.players.filter(C.inTournament).length) { toast('There are no players in the squad yet.'); return; }
      const label = el.innerHTML;
      el.disabled = true;
      el.textContent = 'Creating…';
      try {
        showImageDialog(await squadImage(), {
          title: 'Squad list for management', alt: 'Red Falcons squad list', shareTitle: 'Red Falcons squad list',
          fileName: () => `red-falcons-squad-${today()}.png`
        });
      } catch (e) {
        toast('Could not create the picture.');
      } finally {
        el.disabled = false;
        el.innerHTML = label;
      }
    },
    async 'share-xi'(el) {
      const day = C.days(state).find(d => d.id === el.dataset.id);
      if (!day) return;
      const label = el.innerHTML;
      el.disabled = true;
      el.textContent = 'Creating…';
      try {
        const details = { opponent: day.opponent, when: formatMatchDate(day.date), venue: day.venue };
        showImageDialog(await xiImage(day, details), {
          title: `${day.label} · Playing XI`, alt: `${day.label} Playing XI`, shareTitle: `Red Falcons · ${day.label} Playing XI`,
          fileName: () => `red-falcons-${day.short.toLowerCase()}-playing-xi.png`,
          fields: XI_FIELDS.map(f => ({ ...f, value: details[f.key] })), redraw: values => xiImage(day, values)
        });
      } catch (e) {
        toast('Could not create the picture.');
      } finally {
        el.disabled = false;
        el.innerHTML = label;
      }
    },
    'not-me'() { writeStore(ME_KEY, NOT_ME); refetchForViewer(); },
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
    'p-bat'(el) { const p = findPlayer(el.dataset.id); if (p) p.battingHand = el.value; },
    'p-bowl-arm'(el) { const p = findPlayer(el.dataset.id); if (p) p.bowlingArm = el.value; },
    'p-bowl-style'(el) { const p = findPlayer(el.dataset.id); if (p) p.bowlingStyle = el.value; },
    'p-jersey-name'(el) { const p = findPlayer(el.dataset.id); if (p) p.jerseyName = el.value.slice(0, 24); },
    'p-jersey'(el) { el.value = el.value.replace(/\D/g, '').slice(0, 3); const p = findPlayer(el.dataset.id); if (p) p.jersey = el.value; },
    'training-desc'(el) { const t = state.trainings.find(x => x.id === el.dataset.id); if (t) t.desc = el.value; },
    'exp-item'(el) { const e = findExpense(el); if (e) e.item = el.value; },
    'exp-amount'(el) { el.value = cleanNumber(el.value); const e = findExpense(el); if (e) e.amount = el.value; },
    'exp-paidby'(el) { const e = findExpense(el); if (e) e.paidBy = el.value; },
    'tournament-fee'(el) { el.value = cleanNumber(el.value); state.tournament.fee = el.value; },
    'tournament-name'(el) { state.tournament.name = el.value; },
    stat(el) {
      el.value = el.value.replace(/\D/g, '').slice(0, 3);
      statEntry(el.dataset.m, el.dataset.p)[el.dataset.k] = parseInt(el.value, 10) || 0;
    },
    'tournament-matches'(el) {
      el.value = el.value.replace(/\D/g, '').slice(0, 2);
      const n = parseInt(el.value, 10);
      if (n > 0) state.tournament.matchCount = n;
    },
    'match-time'(el) { const m = state.matches.find(x => x.id === el.dataset.id); if (m) m.time = el.value; },
    'match-date'(el) { const m = state.matches.find(x => x.id === el.dataset.id); if (m) m.date = el.value; },
    'match-opponent'(el) { const m = state.matches.find(x => x.id === el.dataset.id); if (m) m.opponent = el.value; },
    'match-venue'(el) { const m = state.matches.find(x => x.id === el.dataset.id); if (m) m.venue = el.value; },
    'tournament-advance'(el) { el.value = cleanNumber(el.value); state.tournament.advance = el.value; },
    'pay-bank'(el) { state.settings.payBank = el.value; },
    'pay-title'(el) { state.settings.payTitle = el.value; },
    'pay-account'(el) { state.settings.payAccount = el.value; },
    name(el) { const p = findPlayer(el.dataset.id); if (p) p.name = el.value; },
    phone(el) { el.value = el.value.replace(/[^\d+ -]/g, ''); const p = findPlayer(el.dataset.id); if (p) p.phone = el.value; },
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
    document.addEventListener('pointerdown', startBatDrag);
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
      if (el.dataset && el.dataset.input === 'season-name') { seasonDraft = el.value; return; }
      if (el.dataset && /^ann-/.test(el.dataset.input || '')) {
        const k = el.dataset.input;
        if (k === 'ann-title') annDraft.title = el.value;
        else if (k === 'ann-body') { annDraft.body = el.value; const c = $('#ann-count'); if (c) c.textContent = `${el.value.length}/300`; }
        else if (k === 'ann-at') annDraft.at = el.value;
        else if (k === 'ann-match') { annDraft.matchId = el.value; render(); }
        return;
      }
      if (el.dataset && /^pf-/.test(el.dataset.input || '')) {
        const k = el.dataset.input.slice(3);
        if (k === 'who') { chooseWho(el.value); return; }
        if (k === 'jersey') el.value = el.value.replace(/\D/g, '').slice(0, 3);
        if (pForm) pForm[k] = el.value;
        return;
      }
      if (el.dataset && /^poll-/.test(el.dataset.input || '')) {
        if (el.dataset.input === 'poll-q') pollDraft.q = el.value;
        else if (el.dataset.input === 'poll-opt') pollDraft.opts[Number(el.dataset.i)] = el.value;
        return;
      }
      if (el.dataset && /^note-/.test(el.dataset.input || '')) {
        const id = el.dataset.match;
        if (!notesDraft[id]) notesDraft[id] = { ...(notesCache[id] || { fieldPlan: '', battingPlan: '', opposition: '', updatedBy: '', updatedAt: '' }) };
        notesDraft[id][el.dataset.input.slice(5)] = el.value;
        return;
      }
      if (el.dataset && /^me-/.test(el.dataset.input || '')) { saveMyProfile(el); return; }
      if (el.dataset && el.dataset.input === 'decline-note') { declineDraft[el.dataset.key] = el.value; return; }
      if (el.dataset && el.dataset.input === 'mgr-phrase') { mgrDraft.phrase = el.value; return; }
      if (el.dataset && el.dataset.input === 'mgr-edit') { mgrDraft.edits[el.dataset.id] = el.value; render(); return; }
      if (el.dataset && /^tfa-/.test(el.dataset.input || '')) { tfaDraft[el.dataset.input.slice(4)] = el.value; return; }
      if (el.dataset && /^push-/.test(el.dataset.input || '')) {
        const key = el.dataset.input.slice(5);
        pushDraft[key] = el.value;
        if (key !== 'title') render();
        return;
      }
      if (el.dataset && el.dataset.input === 'pick-me') { writeStore(ME_KEY, el.value || NOT_ME); refetchForViewer(); refreshPush(); loadEngage(); return; }
      if (!fn || !isAdmin()) return;
      fn(el);
      commit();
    };
    document.addEventListener('input', onInput);

    document.addEventListener('keydown', e => {
      if (e.key === 'Escape' && sheet && !$('#dialog-root').innerHTML) { sheet = null; render(); }
    });

    $('#gallery-file').addEventListener('change', e => {
      const files = [...e.target.files];
      e.target.value = '';
      uploadMatchPhotos(files);
    });

    $('#photo-file').addEventListener('change', e => {
      const f = e.target.files[0];
      e.target.value = '';
      uploadPhoto(f);
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
    $('#code-form').addEventListener('submit', e => {
      e.preventDefault();
      const code = $('#login-code').value.trim();
      if (!code) { $('#code-error').textContent = 'Enter the 6-digit code.'; return; }
      verifyCode(code);
    });
    $('#code-back').addEventListener('click', () => { hideCodeStep(); $('#login-name').focus(); });
  }

  applyTheme(currentTheme());
  // The top bar is fixed to the screen; the page starts below it whatever its height.
  const topbarEl = document.querySelector('.topbar');
  if (topbarEl) {
    const setTopbarHeight = () => document.documentElement.style.setProperty('--topbar-h', topbarEl.offsetHeight + 'px');
    setTopbarHeight();
    if (window.ResizeObserver) new ResizeObserver(setTopbarHeight).observe(topbarEl);
    window.addEventListener('resize', setTopbarHeight);
  }

  wireEvents();
  // Returning visitors go straight back in; the name screen is only for first visits and "Switch user".
  if (session) enterApp(); else showSplash();
})();
