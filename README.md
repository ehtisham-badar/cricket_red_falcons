# Red Falcons — Cricket Ledger

Web app that tracks training and match attendance, what each player paid, and who owes what.
Built from [`Cricket Ledger App Spec.md`](Cricket%20Ledger%20App%20Spec.md).

- **Backend:** Spring Boot 4 (Maven), H2 file database in `./data/`
- **Frontend:** plain HTML/CSS/JS in `src/main/resources/static/`

## Run locally

Create `config/application.properties` (git-ignored) with your admin key:

```properties
ledger.admin-key=your-secret-key
```

Then:

```bash
./mvnw spring-boot:run
```

Open http://localhost:8080. No Maven install needed; `mvnw` downloads it.
The app refuses to start if no admin key is configured.

## Who can edit

The start screen asks for a name.

- Typing the **admin key** gives full access (edit, delete, import, clear).
- Typing any other name gives **view-only** access. The page refreshes every 15 seconds.

The key is checked on the server, so viewers can't unlock editing from the browser.
It is never stored in git: set it in `config/application.properties` locally, or with the
`LEDGER_ADMIN_KEY` environment variable on a server. Changing the key logs out any existing admin sessions.

## Data

- Saved automatically to `./data/redfalcons.mv.db` (git-ignored). Set `LEDGER_DATA_DIR` to store it elsewhere.
- **Settings → Save backup** downloads `cricket-ledger-YYYY-MM-DD.json`. Backups also store itemised expenses and the tournament fee, and still include per-day totals for older readers. Backups from the original HTML version open fine: each day's amount becomes one expense item.
- **Open backup** restores one.

## Deploying (Railway + optional Vercel)

**Railway** runs the server, and it also serves the pages, so this alone is a complete deployment.

1. Create a Railway project from this GitHub repo. It builds with Maven and uses Railway's `PORT`.
2. Add a **Volume** (e.g. mounted at `/data`). Without one, Railway wipes the database on every redeploy.
3. Set these variables:
   - `LEDGER_ADMIN_KEY`: your secret admin key
   - `LEDGER_DATA_DIR`: the Volume mount path, e.g. `/data`
4. If the build picks the wrong Java version, pin Java 21 in the Railway service settings.

**Vercel** (optional, if you want the pages served from Vercel):

1. Deploy `src/main/resources/static/` as a static site, with no build step.
2. In `js/config.js`, set `window.RF_API_BASE` to the Railway URL.
3. On Railway, set `LEDGER_ALLOWED_ORIGINS=https://<your-app>.vercel.app`.

## Tests

```bash
./mvnw test
```
