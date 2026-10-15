// Makes the app installable. Always tries the network first so a new deploy shows up straight away;
// the cached copy of the app's own files is only used when offline. Ledger data (another origin) isn't cached.
const CACHE = 'rf-shell-v2';

self.addEventListener('install', () => self.skipWaiting());
self.addEventListener('activate', e => {
  e.waitUntil(caches.keys()
    .then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k))))
    .then(() => self.clients.claim()));
});

self.addEventListener('fetch', e => {
  const req = e.request;
  if (req.method !== 'GET' || new URL(req.url).origin !== self.location.origin) return;
  e.respondWith(fetch(req)
    .then(res => {
      if (res.ok) { const copy = res.clone(); caches.open(CACHE).then(c => c.put(req, copy)); }
      return res;
    })
    .catch(() => caches.match(req).then(hit => hit || caches.match('/'))));
});

// ---------- notifications ----------
// Pushes arrive empty; ask the ledger API what's waiting for this device and show it.
const API = new URL(self.location).searchParams.get('api') || '';
const ICON = 'img/icon-192.png';

self.addEventListener('push', e => {
  e.waitUntil((async () => {
    let list = [];
    try {
      const sub = await self.registration.pushManager.getSubscription();
      if (sub) {
        const res = await fetch(`${API}/api/push/pending?endpoint=${encodeURIComponent(sub.endpoint)}`, { cache: 'no-store' });
        if (res.ok) list = await res.json();
      }
    } catch { /* show the fallback below */ }
    if (!list.length) list = [{ title: 'Red Falcons', body: 'There’s something new — open the app.' }];
    await Promise.all(list.map((m, i) => self.registration.showNotification(m.title || 'Red Falcons', {
      body: m.body || '', icon: ICON, badge: ICON, tag: 'rf-' + Date.now() + '-' + i, data: { url: '/' }
    })));
  })());
});

self.addEventListener('notificationclick', e => {
  e.notification.close();
  e.waitUntil((async () => {
    const wins = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
    const open = wins.find(w => new URL(w.url).origin === self.location.origin);
    if (open) return open.focus();
    return self.clients.openWindow((e.notification.data && e.notification.data.url) || '/');
  })());
});
