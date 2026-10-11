// Where the API lives. Locally the Spring Boot app serves both the page and the API, so use the
// same origin. Everywhere else (Railway itself, or the Vercel-hosted frontend) talk to Railway.
window.RF_API_BASE = ['localhost', '127.0.0.1'].includes(location.hostname)
  ? ''
  : 'https://ledger-production-7d2b.up.railway.app';
