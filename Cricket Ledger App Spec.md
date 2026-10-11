# Cricket Team Ledger — Mobile App Spec

A personal, offline-only phone app for a cricket team manager to track who attended trainings and matches, what each player paid, and how much to pay back or collect from each player.

Single user. No login, no server, no internet required. All data stays on the phone.

---

## 1. Core concept

- There is a **pool of players**.
- There are **days**: one or more **Trainings** and exactly **5 Matches** (Match 1 … Match 5).
- Each day has an **amount spent** (ground fee, balls, etc.).
- Each day's amount is **split equally only among players who attended that day**.
- Each player has an amount **Paid**.
- **Net = Paid − Cost**
  - Net > 0 → manager **pays back** that amount
  - Net < 0 → player **owes** the manager
  - Net = 0 → **Settled**

---

## 2. Data model

```ts
type Role = '' | 'Batsman' | 'Batsman + Captain' | 'Bowler' | 'All-rounder' | 'Wicket-keeper';

interface Player {
  id: string;             // unique
  name: string;
  role: Role;
  paid: string;           // numeric string, '' = 0
  att: Record<string, boolean>; // key = day id ('t1', 'tabc12', 'm1'…'m5'), true = attended
}

interface Training {
  id: string;             // 't1' for the first, then 't' + random id
  desc: string;           // free-text description
}

interface LedgerState {
  players: Player[];
  trainings: Training[];  // at least 1 always
  amounts: Record<string, string>; // day id -> amount spent (numeric string)
}
```

Fixed matches (not editable, not removable):

| id | label   |
|----|---------|
| m1 | Match 1 |
| m2 | Match 2 |
| m3 | Match 3 |
| m4 | Match 4 |
| m5 | Match 5 |

Trainings are labelled by position: `Training 1`, `Training 2`, … (renumber when one is removed).

Day order everywhere: all trainings first, then Match 1–5.

---

## 3. Calculations

For each day `d`:

```
attendees(d) = players where att[d.id] == true
n            = count(attendees(d))
amount       = number(amounts[d.id]) or 0
share(d)     = n > 0 ? amount / n : 0
```

- Round each share to 2 decimals.
- Optional setting **Round shares up**: `share = ceil(amount / n)` (whole numbers).

For each player:

```
cost  = sum of share(d) for every day d where att[d.id] == true and amount > 0
paid  = number(player.paid) or 0
net   = round2(paid - cost)
```

Totals (summary):

```
Collected        = sum(paid) over all players
Total spent      = sum(amount) over all days
You pay back     = sum(net) where net > 0
Still owed to you = sum(-net) where net < 0
```

Cost breakdown text per player, one line per attended day with an amount:

```
Match 1: Rs 3,900 ÷ 11 = Rs 354.55
Training 1: Rs 1,500 ÷ 13 = Rs 115.38
```

If none → `No cost`.

---

## 4. Rules

- **Max 11 players per match** (configurable setting, default 11). When 11 are marked Played for a match, the remaining players' "Played" toggle for that match is disabled with the hint "11 players already played Match N".
- **Trainings have no limit.**
- Training attendance labels: `Arrived` / `Not arrived`.
- Match attendance labels: `Played` / `Not played`.
- At least one training must always exist. Remove button hidden when only one remains.
- Removing a training deletes its amount; attendance entries for that id become irrelevant (can be cleaned up).
- **Warning banner**: if any day has amount > 0 but 0 attendees →
  `"Match 2, Training 3: amount entered but no players marked — that cost isn't assigned to anyone yet."`

---

## 5. Screens (mobile)

Recommended bottom tab navigation with 3 tabs.

### 5.1 Summary (home)

- 4 stat cards: **Collected**, **Total spent**, **You pay back**, **Still owed to you**
- Warning banner (if any)
- List of players sorted by Sr. no.: name, role, net amount (colored) + label (Pay back / Owes you / Settled)
- Tap a player → Player detail

### 5.2 Days

- List of day cards (trainings first, then Match 1–5). Each card:
  - Label (Training N / Match N)
  - Description text field (**trainings only**)
  - Amount spent input (numeric keypad)
  - `X / 11 played` (match) or `X arrived` (training)
  - `Rs Y each` share, or `—`
  - Remove (✕) for trainings when more than one exists (with confirm)
- **Add training** button
- Tap a day → **Attendance screen** for that day: full player list with a toggle per player (Arrived/Not arrived or Played/Not played), counter at top, match limit enforced.

> Marking attendance day-by-day is much faster on a phone than a wide table.

### 5.3 Players

- List with **Sr. no.** (auto, 1-based position), **Name**, **Role**
- **Add players**: textarea, paste names separated by new lines or commas → appended with empty role, no attendance, paid ''.
- Swipe / button to remove a player (with confirm).

### 5.4 Player detail

- Name (editable), Role (picker), Paid (numeric input)
- Attendance toggles for every day
- Cost with breakdown lines
- Net amount, large and colored

### 5.5 Settings / menu

- **Save backup** → export JSON file (share sheet)
- **Open backup** → import JSON file (confirm before replacing)
- **New series** → keeps players & names/roles; clears attendance, paid, amounts; resets trainings to just Training 1 (confirm)
- **Clear all** → removes everything (confirm)
- Currency: `Rs ` (default), `PKR `, `₹`, `$`, `£`
- Max players per match: number (default 11)
- Round shares up: toggle (default off)

---

## 6. Persistence

- Auto-save full `LedgerState` locally on every change (AsyncStorage / SharedPreferences / localStorage / SQLite — any local store).
- Load on app start. If nothing stored → empty players, `trainings: [{id:'t1', desc:''}]`, `amounts: {}`.

### Backup file format (must stay compatible with the existing HTML version)

```json
{
  "app": "cricket-ledger",
  "version": 2,
  "savedAt": "2026-10-08T12:00:00.000Z",
  "players": [
    { "id": "a1b2c3d", "name": "Babar", "role": "Batsman + Captain", "paid": "500",
      "att": { "t1": true, "m1": true } }
  ],
  "amounts": { "t1": "1500", "m1": "3900", "m2": "", "m3": "", "m4": "", "m5": "" },
  "trainings": [ { "id": "t1", "desc": "Nets at club" } ]
}
```

Filename: `cricket-ledger-YYYY-MM-DD.json`.

Import validation: must be valid JSON with a `players` array; otherwise show "This file is not a valid ledger save file." Missing `amounts` → `{}`; missing/empty `trainings` → `[{id:'t1', desc:''}]`; missing `role` → `''`.

---

## 7. Formatting

- Money: `currency + number` with thousands separators (`en-PK` locale); show 2 decimals only when not a whole number. Examples: `Rs 3,900`, `Rs 354.55`.
- Net sign: `+Rs 145.45` (pay back), `−Rs 54.55` (owes, uses the real minus sign `−`), `Rs 0` (settled).

---

## 8. Visual style

Warm, rounded, friendly.

| Token | Value |
|---|---|
| Background | `#f5ead8` (cream) |
| Text | `#201e1d` |
| Accent (terracotta) | `#c67139` — primary buttons, "Arrived", "Owes you" |
| Accent 2 (sage) | `#7a8a5e` — "Played", "Pay back", training cards tint |
| Headings font | Caprasimo |
| Body font | Figtree |
| Corner radius | 16px cards, fully rounded (pill) buttons, inputs and toggles |

- Active toggle: solid fill (sage for Played, terracotta for Arrived), white text.
- Inactive toggle: dashed outline, muted text.
- Disabled toggle: 45% opacity.
- Pay back amounts in dark sage, owes amounts in dark terracotta, settled in grey.
- Minimum tap target 44×44px.

---

## 9. Suggested tech (pick one)

- **Easiest, no app store:** turn the existing HTML into a PWA (add `manifest.json` + service worker), open it in Chrome/Safari on the phone → "Add to Home Screen". Works offline.
- **Native-feel:** React Native + Expo, `@react-native-async-storage/async-storage` for storage, `expo-document-picker` + `expo-sharing` + `expo-file-system` for backup import/export. Build an APK with `eas build -p android --profile preview` and install directly (no Play Store needed).
- **Android only:** Flutter with `shared_preferences` + `file_picker` + `share_plus`.

---

## 10. Acceptance checks

1. Match 1 amount 3,900, 11 players Played → each shows `Rs 354.55` cost for Match 1.
2. 12th player's Played button for Match 1 is disabled.
3. Training 1 amount 1,500, 13 Arrived → each share `Rs 115.38`.
4. Player who paid 500 and attended both → cost `Rs 469.93`, net `+Rs 30.07`, "Pay back".
5. Player who attended nothing and paid 200 → net `+Rs 200`.
6. Add Training 2 with description, amount 600, 4 arrive → each `Rs 150`; removing it removes those costs.
7. Save backup → Clear all → Open backup → all data restored exactly.
8. Backup saved from the HTML version opens in the mobile app and vice versa.
