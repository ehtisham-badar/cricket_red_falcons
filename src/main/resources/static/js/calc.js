// Pure ledger calculations (spec §3, §7). No DOM access, so it can be tested in Node.
//
// Model on top of the original spec:
//  - Each day has a list of expenses ({ id, item, amount, paidBy }). A day's amount is their sum.
//    paidBy '' means the team fund paid it; otherwise it is the id of the player who paid out of pocket.
//  - An expense can be narrowed to chosen players (sharedBy: [ids], e.g. a breakfast only some had); only they
//    split it, whether or not they played. Without sharedBy it's split among everyone who played.
//  - A player who paid for an expense gets that amount credited to their balance.
//  - The series-wide league (tournament) fee is a fee per match (fee ÷ the league's match count, 5 unless
//    set). Each match's part is split among the players who played it (the 12th man isn't marked as playing,
//    so pays nothing). Matches not added yet, or with no XI marked, are split across everyone taking part
//    (player.tournament !== false) until they are.
//    With roundUp each player's league total is rounded once, to whole rupees that add up to the fee.
//  - A player's payments ({ id, date, amount, method, note }) add up to what they have paid.
//  - charges ({ id, title, amount, players: [ids] }) are season-wide costs on chosen players only, such as a
//    shirt order: each of them is charged the amount. The team fund pays the supplier.
//  - tournament.payments are transfers from the admin to the tournament management; only money
//    actually sent leaves the admin's account (cash in hand), the rest is still owed to management.
(function (root) {
  const DEFAULT_MATCH_COUNT = 5;

  // Matches are added as they come ({ id, date, opponent, venue }), numbered in order.
  // Generated test matches are numbered on their own ("Test 1") so they never shift the real match numbers.
  function matches(state) {
    let real = 0, test = 0;
    return (state.matches || []).map(m => {
      const isTest = m.test === true;
      const n = isTest ? ++test : ++real;
      return {
        id: m.id, label: (isTest ? 'Test ' : 'Match ') + n, short: (isTest ? 'TM' : 'M') + n, type: 'match', test: isTest,
        date: m.date || '', time: m.time || '', opponent: m.opponent || '', venue: m.venue || '', finished: !isTest && m.finished === true, announced: m.announced === true, extras: m.extras || '', twelfthShares: m.twelfthShares === true, twelfthPlayed: m.twelfthPlayed === true, result: m.result || ''
      };
    });
  }

  // How many matches the league fee covers: the setting, or more if more were added.
  function leagueMatchCount(state) {
    const set = parseInt(state.tournament && state.tournament.matchCount, 10);
    return Math.max(set > 0 ? set : DEFAULT_MATCH_COUNT, (state.matches || []).filter(m => m.test !== true).length);
  }

  function days(state) {
    const trainings = state.trainings.map((t, i) => ({
      id: t.id, label: 'Training ' + (i + 1), short: 'T' + (i + 1), type: 'training', desc: t.desc || ''
    }));
    return trainings.concat(matches(state));
  }

  // A match only charges its XI (league share and expenses) once the admin has finished it.
  const isCharged = d => d.type !== 'match' || d.finished === true;

  function num(v) {
    const n = parseFloat(v);
    return Number.isFinite(n) ? n : 0;
  }

  function round2(n) {
    return Math.round((n + Number.EPSILON) * 100) / 100;
  }

  function split(amount, n, roundUp) {
    if (n <= 0) return 0;
    return roundUp ? Math.ceil(amount / n) : round2(amount / n);
  }

  const inTournament = p => p.tournament !== false;

  function paidTotal(player) {
    if (!player.payments) return num(player.paid);
    return round2(player.payments.reduce((s, x) => s + num(x.amount), 0));
  }

  function dayExpenses(state, dayId) {
    return (state.expenses && state.expenses[dayId]) || [];
  }

  function dayAmount(state, dayId) {
    return round2(dayExpenses(state, dayId).reduce((s, e) => s + num(e.amount), 0));
  }

  // The players an expense is narrowed to, or null when everyone who played shares it.
  function sharedOf(state, e) {
    if (!e.sharedBy || !e.sharedBy.length) return null;
    const ids = e.sharedBy.filter(id => state.players.some(p => p.id === id));
    return ids.length ? ids : null;
  }

  // Season-wide charges (a shirt order), each on its chosen players only.
  function charges(state) {
    return state.charges || [];
  }

  // What a player is charged for one charge: its amount if they are on it, else 0.
  function chargeOf(c, playerId) {
    return (c.players || []).includes(playerId) ? num(c.amount) : 0;
  }

  // The whole charge: its amount for every player on it.
  function chargeTotal(state, c) {
    return round2(num(c.amount) * (c.players || []).filter(id => state.players.some(p => p.id === id)).length);
  }

  // The 12th man shares a match's costs once he bats or bowls in it; fielding as a substitute doesn't count.
  // Without his stats (they may be hidden from viewers) the server's twelfthPlayed flag on the match says so.
  function twelfthPlays(state, dayId) {
    const id = state.twelfth && state.twelfth[dayId];
    if (!id) return false;
    if (state.stats && state.stats[dayId] && state.stats[dayId][id]) {
      const e = statOf(state, dayId, id);
      return batted(e) || bowled(e);
    }
    const m = (state.matches || []).find(x => x.id === dayId);
    return !!(m && m.twelfthPlayed);
  }

  // Whether a player pays for a day: they attended / played, or they're a 12th man who batted or bowled.
  function pays(state, player, dayId) {
    if (player.att && player.att[dayId]) return true;
    return !!(state.twelfth && state.twelfth[dayId] === player.id && twelfthPlays(state, dayId));
  }

  // Who shares a day's expenses: whoever pays, plus the 12th man when the admin chose to include him for that match.
  function expPays(state, player, dayId) {
    if (pays(state, player, dayId)) return true;
    const m = (state.matches || []).find(x => x.id === dayId);
    return !!(m && m.twelfthShares && state.twelfth && state.twelfth[dayId] === player.id);
  }

  // n is who attended (the XI for a match); payers is who shares the league fee (the XI, plus a 12th man who played);
  // expPayers is who shares the expenses (the same, plus the 12th man when the admin includes him).
  
  function dayStats(state, day) {
    const n = state.players.filter(p => p.att && p.att[day.id]).length;
    const payers = state.players.filter(p => pays(state, p, day.id)).length;
    const expPayers = state.players.filter(p => expPays(state, p, day.id)).length;
    const amount = dayAmount(state, day.id);
    // common: the expenses everyone who played shares; the rest go only to the players chosen for them.
    const common = round2(dayExpenses(state, day.id).filter(e => !sharedOf(state, e)).reduce((t, e) => t + num(e.amount), 0));
    return { n, payers, expPayers, amount, common, share: split(common, expPayers, state.settings.roundUp) };
  }

  function allStats(state) {
    const map = {};
    days(state).forEach(d => { map[d.id] = dayStats(state, d); });
    return map;
  }

  // League fee per player, from the matches each one played. charges[id] = { amount, exact, parts }.
  function leagueCharges(state, stats, fee) {
    const all = matches(state);
    const perMatch = fee / leagueMatchCount(state);
    const squad = state.players.filter(inTournament);
    const charges = {};
    // played: the part from matches already played (the rest is an advance share of matches still to come).
    state.players.forEach(p => { charges[p.id] = { amount: 0, exact: 0, played: 0, parts: [] }; });
    const played = all.filter(m => m.finished && stats[m.id] && stats[m.id].payers > 0);
    const unplayed = leagueMatchCount(state) - played.length;
    if (!(fee > 0)) return { perMatch, unplayed, charges };

    played.forEach(m => {
      const n = stats[m.id] ? stats[m.id].payers : 0;
      if (!n) return;
      state.players.filter(p => pays(state, p, m.id)).forEach(p => {
        charges[p.id].exact += perMatch / n;
        charges[p.id].played += perMatch / n;
        charges[p.id].parts.push({ label: m.label, amount: perMatch, n, share: perMatch / n });
      });
    });
    if (unplayed > 0 && squad.length) {
      const amount = perMatch * unplayed;
      const label = unplayed === 1 ? '1 match not played yet' : unplayed + ' matches not played yet';
      squad.forEach(p => {
        charges[p.id].exact += amount / squad.length;
        charges[p.id].parts.push({ label, amount, n: squad.length, share: amount / squad.length });
      });
    }

    const list = state.players.map(p => charges[p.id]);
    if (state.settings.roundUp) {
      // Largest remainder: whole rupees whose sum is the fee (to the rupee), fairest to the biggest fractions.
      const target = Math.round(list.reduce((s, c) => s + c.exact, 0));
      list.forEach(c => { c.amount = Math.floor(c.exact + 1e-9); });
      let left = target - list.reduce((s, c) => s + c.amount, 0);
      list.map((c, i) => ({ c, i, frac: c.exact - c.amount }))
        .filter(x => x.frac > 1e-9)
        .sort((a, b) => b.frac - a.frac || a.i - b.i)
        .forEach(x => { if (left > 0) { x.c.amount += 1; left -= 1; } });
    } else {
      list.forEach(c => { c.amount = round2(c.exact); });
    }
    return { perMatch, unplayed, charges };
  }

  function tournamentStats(state, stats) {
    const fee = num(state.tournament && state.tournament.fee);
    const n = state.players.filter(inTournament).length;
    const league = leagueCharges(state, stats || allStats(state), fee);
    return { fee, n, share: split(fee, n, state.settings.roundUp), perMatch: league.perMatch, unplayed: league.unplayed, charges: league.charges };
  }

  // Money already sent to management, split over the players taking part: the suggested advance.
  function advanceShare(state) {
    const sent = ((state.tournament && state.tournament.payments) || []).reduce((s, x) => s + num(x.amount), 0);
    return split(sent, state.players.filter(inTournament).length, state.settings.roundUp);
  }

  // The advance each player pays up front: the admin's amount, else the suggested share.
  function advancePerPlayer(state) {
    const set = state.tournament && state.tournament.advance;
    return set !== '' && set != null ? num(set) : advanceShare(state);
  }

  // How much of the advance a player still has to pay; their payments count towards it first.
  function advanceDue(state, player) {
    if (!inTournament(player)) return 0;
    return round2(Math.max(advancePerPlayer(state) - paidTotal(player), 0));
  }

  function playerCalc(state, player, stats, fmt, tour) {
    stats = stats || allStats(state);
    tour = tour || tournamentStats(state, stats);
    let cost = 0;
    let credit = 0;
    const lines = [];
    const creditLines = [];

    const league = tour.charges[player.id];
    if (league && league.amount > 0) {
      cost += league.amount;
      league.parts.forEach(x => lines.push({ type: 'tournament', text: 'League · ' + x.label + ': ' + fmt(x.amount) + ' ÷ ' + x.n + ' = ' + fmt(round2(x.share)) }));
      if (Math.abs(league.amount - round2(league.exact)) >= 0.005) {
        lines.push({ type: 'tournament', text: 'League total ' + fmt(round2(league.exact)) + ' → ' + fmt(league.amount) + ' in whole rupees' });
      }
    }
    days(state).forEach(d => {
      if (!isCharged(d)) return;
      const s = stats[d.id];
      if (expPays(state, player, d.id) && s.common > 0) {
        cost += s.share;
        lines.push({ type: d.type, day: d, text: d.label + ': ' + fmt(s.common) + ' ÷ ' + s.expPayers + ' = ' + fmt(s.share) });
      }
      dayExpenses(state, d.id).forEach(e => {
        const amt = num(e.amount);
        const only = sharedOf(state, e);
        if (only && only.includes(player.id) && amt > 0) {
          const each = split(amt, only.length, state.settings.roundUp);
          cost += each;
          lines.push({ type: d.type, day: d, text: (e.item || 'Expense') + ' (' + d.label + '): ' + fmt(amt) + ' ÷ ' + only.length + ' = ' + fmt(each) });
        }
        if (e.paidBy === player.id && amt > 0) {
          credit += amt;
          creditLines.push({ type: d.type, day: d, text: (e.item || 'Expense') + ' (' + d.label + '): ' + fmt(amt) });
        }
      });
    });

    charges(state).forEach(c => {
      const amt = chargeOf(c, player.id);
      if (amt > 0) {
        cost += amt;
        lines.push({ type: 'charge', text: (c.title || 'Charge') + ': ' + fmt(amt) });
      }
    });

    cost = round2(cost);
    credit = round2(credit);
    const paid = paidTotal(player);
    const net = round2(paid + credit - cost);
    // usedSoFar: only what has happened (matches played, trainings, expenses), without the advance share of
    // league matches still to come; left is what remains of their money after that (paid back if they leave).
    const playedPart = league ? (state.settings.roundUp ? Math.round(league.played) : round2(league.played)) : 0;
    const upcoming = league ? Math.max(league.amount - playedPart, 0) : 0;
    const usedSoFar = round2(Math.max(cost - upcoming, 0));
    const left = round2(paid + credit - usedSoFar);
    return { cost, paid, credit, net, usedSoFar, left, lines, creditLines };
  }

  function totals(state, fmt) {
    const stats = allStats(state);
    const tour = tournamentStats(state, stats);
    const sent = round2(((state.tournament && state.tournament.payments) || []).reduce((s, x) => s + num(x.amount), 0));
    let collected = 0, payBack = 0, owed = 0, fronted = 0, daySpent = 0, fundSpent = sent;
    state.players.forEach(p => {
      const c = playerCalc(state, p, stats, fmt, tour);
      collected += c.paid;
      fronted += c.credit;
      if (c.net > 0) payBack += c.net;
      if (c.net < 0) owed += -c.net;
    });
    const chargesSpent = round2(charges(state).reduce((s, c) => s + chargeTotal(state, c), 0));
    fundSpent += chargesSpent;
    days(state).forEach(d => {
      daySpent += stats[d.id].amount;
      dayExpenses(state, d.id).forEach(e => { if (!e.paidBy) fundSpent += num(e.amount); });
    });
    return {
      collected: round2(collected),
      tournamentFee: tour.fee,
      daySpent: round2(daySpent),
      chargesSpent,
      spent: round2(daySpent + tour.fee + chargesSpent),
      fronted: round2(fronted),
      sentToManagement: sent,
      managementDue: round2(Math.max(tour.fee - sent, 0)),
      // teamSpent: what the team fund paid for the team itself (day expenses it paid, and orders), apart from management.
      teamSpent: round2(fundSpent - sent),
      cashInHand: round2(collected - fundSpent),
      payBack: round2(payBack),
      owed: round2(owed)
    };
  }

  // ---------- match stats (The Hundred: 5-ball overs) ----------
  const BALLS_PER_OVER = 5;
  const STAT_KEYS = ['balls', 'ones', 'twos', 'threes', 'fours', 'sixes', 'bowlBalls', 'bowlRuns', 'wickets',
    'catches', 'drops', 'misfields', 'goodFielding', 'wides', 'noBalls'];

  function statOf(state, matchId, playerId) {
    const e = (state.stats && state.stats[matchId] && state.stats[matchId][playerId]) || {};
    const out = { out: !!e.out };
    STAT_KEYS.forEach(k => { out[k] = Math.max(0, parseInt(e[k], 10) || 0); });
    // How a batter got out: the kind, the bowler and fielder (free text), and the team score and over at that wicket.
    out.dismissal = typeof e.dismissal === 'string' ? e.dismissal : '';
    out.bowler = typeof e.bowler === 'string' ? e.bowler : '';
    out.fielder = typeof e.fielder === 'string' ? e.fielder : '';
    const fow = parseInt(e.fowScore, 10);
    out.fowScore = Number.isFinite(fow) && fow >= 0 ? fow : null;
    out.fowOver = typeof e.fowOver === 'string' ? e.fowOver : '';
    return out;
  }

  const runsOf = e => e.ones + 2 * e.twos + 3 * e.threes + 4 * e.fours + 6 * e.sixes;
  const shotsOf = e => e.ones + e.twos + e.threes + e.fours + e.sixes;
  const strikeRate = (runs, balls) => balls > 0 ? round2(runs / balls * 100) : null;
  // 13 balls -> "2.3" (2 overs and 3 balls of 5)
  const oversOf = balls => Math.floor(balls / BALLS_PER_OVER) + '.' + (balls % BALLS_PER_OVER);
  const economy = (runs, balls) => balls > 0 ? round2(runs / (balls / BALLS_PER_OVER)) : null;
  const batted = e => e.balls > 0 || e.out || shotsOf(e) > 0;
  // A bowler's runs conceded: off the bat plus the wides and no-balls they gave away.
  const extrasOf = e => e.wides + e.noBalls;
  const concededOf = e => e.bowlRuns + extrasOf(e);
  const bowled = e => e.bowlBalls > 0 || extrasOf(e) > 0;

  // Season totals per player, from every match that has stats.
  function leagueStats(state, visible) {
    const ms = matches(state).filter(m => !visible || visible(m));
    return state.players.map(p => {
      const t = { player: p, matches: 0, innings: 0, outs: 0, runs: 0, balls: 0, ones: 0, twos: 0, threes: 0, fours: 0, sixes: 0,
        high: null, bowlBalls: 0, bowlRuns: 0, conceded: 0, wides: 0, noBalls: 0, wickets: 0, best: null,
        catches: 0, drops: 0, misfields: 0, goodFielding: 0 };
      ms.forEach(m => {
        if (!(state.stats && state.stats[m.id] && state.stats[m.id][p.id])) return;
        const e = statOf(state, m.id, p.id);
        t.matches += 1;
        if (batted(e)) {
          const r = runsOf(e);
          t.innings += 1;
          if (e.out) t.outs += 1;
          t.runs += r;
          if (!t.high || r > t.high.runs || (r === t.high.runs && !e.out && t.high.out)) t.high = { runs: r, out: e.out };
        }
        t.balls += e.balls;
        ['ones', 'twos', 'threes', 'fours', 'sixes', 'catches', 'drops', 'misfields', 'goodFielding'].forEach(k => { t[k] += e[k]; });
        if (bowled(e)) {
          const conceded = concededOf(e);
          t.bowlBalls += e.bowlBalls; t.bowlRuns += e.bowlRuns; t.conceded += conceded; t.wickets += e.wickets;
          t.wides += e.wides; t.noBalls += e.noBalls;
          if (!t.best || e.wickets > t.best.wickets || (e.wickets === t.best.wickets && conceded < t.best.runs)) {
            t.best = { wickets: e.wickets, runs: conceded };
          }
        }
      });
      t.strikeRate = strikeRate(t.runs, t.balls);
      t.average = t.outs > 0 ? round2(t.runs / t.outs) : null;
      t.economy = economy(t.conceded, t.bowlBalls);
      return t;
    });
  }

  function warnings(state) {
    const stats = allStats(state);
    const out = days(state).filter(d => isCharged(d) && stats[d.id].amount > 0 && stats[d.id].expPayers === 0).map(d => d.label);
    const tour = tournamentStats(state, stats);
    if (tour.fee > 0 && tour.n === 0 && tour.unplayed > 0) out.push('Tournament fee');
    return out;
  }

  function money(n, currency) {
    const v = round2(n);
    const whole = Number.isInteger(v);
    return currency + v.toLocaleString('en-PK', {
      minimumFractionDigits: whole ? 0 : 2, maximumFractionDigits: whole ? 0 : 2
    });
  }

  function signedMoney(n, currency) {
    const v = round2(n);
    if (v > 0) return '+' + money(v, currency);
    if (v < 0) return '−' + money(-v, currency);
    return money(0, currency);
  }

  function netLabel(net) {
    return net > 0 ? 'Pay back' : net < 0 ? 'Owes you' : 'Settled';
  }

  const api = {
    matches, leagueMatchCount, days, num, isCharged, pays, expPays, twelfthPlays,
    BALLS_PER_OVER, STAT_KEYS, statOf, runsOf, shotsOf, strikeRate, oversOf, economy, batted, extrasOf, concededOf, bowled, leagueStats, round2, inTournament, paidTotal, dayExpenses, dayAmount, sharedOf, charges, chargeOf, chargeTotal, dayStats, allStats,
    tournamentStats, advanceShare, advancePerPlayer, advanceDue, playerCalc, totals, warnings, money, signedMoney, netLabel
  };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.RFCalc = api;
})(this);
