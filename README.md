# Red Falcons — Cricket Ledger

Web app that tracks training and match attendance, what each player paid, and who owes what.
Built from [`Cricket Ledger App Spec.md`](Cricket%20Ledger%20App%20Spec.md).

- **Backend:** Spring Boot 4 (Maven), H2 file database in `./data/`
- **Frontend:** plain HTML/CSS/JS in `src/main/resources/static/`

## Run locally

```bash
./mvnw spring-boot:run
```

Open http://localhost:8080. No Maven install needed; `mvnw` downloads it.

## Who can edit

The start screen asks for a name.

- Typing the **admin key** gives full access (edit, delete, import, clear).
- Typing any other name gives **view-only** access. The page refreshes every 15 seconds.

The key is checked on the server, so viewers can't unlock editing from the browser.
The default key is in `src/main/resources/application.properties`
(`ledger.admin-key`). Change it there, or override it without editing the file:

```bash
LEDGER_ADMIN_KEY=my-secret ./mvnw spring-boot:run
```

Changing the key logs out any existing admin sessions.

## Data

- Saved automatically to `./data/redfalcons.mv.db` (git-ignored).
- **Settings → Save backup** downloads `cricket-ledger-YYYY-MM-DD.json`, which uses the same format as the original HTML version.
- **Open backup** restores one.

## Deploying later

Netlify only hosts static files and JS/Go functions, so it **cannot run this Java backend**.
Options:

1. **Everything on one Java host** (simplest): deploy the jar (`./mvnw package` → `target/cricket-ledger-1.0.0.jar`)
   to Render, Railway or Fly.io. Point the database at a hosted Postgres, because their disks reset on redeploy.
2. **UI on Netlify, API elsewhere:** deploy `src/main/resources/static/` to Netlify. In `js/config.js`, set
   `window.RF_API_BASE` to the backend URL. On the backend, set `LEDGER_ALLOWED_ORIGINS=https://your-site.netlify.app`.

## Tests

```bash
./mvnw test
```
