// Pure ledger calculations (spec §3, §7). No DOM access, so it can be tested in Node.
//
// Model on top of the original spec:
//  - Each day has a list of expenses ({ id, item, amount, paidBy }). A day's amount is their sum.
//    paidBy '' means the team fund paid it; otherwise it is the id of the player who paid out of pocket.
//  - A player who paid for an expense gets that amount credited to their balance.
//  - The series-wide league (tournament) fee is a fee per match (fee ÷ 5). Each match's part is split among
//    the players who played it (the 12th man isn't marked as playing, so pays nothing). Matches with no XI
//    marked yet are split across everyone taking part (player.tournament !== false) until they are.
//    With roundUp each player's league total is rounded once, to whole rupees that add up to the fee.
//  - A player's payments ({ id, date, amount, method, note }) add up to what they have paid.
//  - tournament.payments are transfers from the admin to the tournament management; only money
//    actually sent leaves the admin's account (cash in hand), the rest is still owed to management.
(function (root) {
  const MATCHES = [1, 2, 3, 4, 5].map(i => ({ id: 'm' + i, label: 'Match ' + i, short: 'M' + i, type: 'match' }));

  function days(state) {
    const trainings = state.trainings.map((t, i) => ({
      id: t.id, label: 'Training ' + (i + 1), short: 'T' + (i + 1), type: 'training', desc: t.desc || ''
    }));
    return trainings.concat(MATCHES);
  }

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

  function dayStats(state, day) {
    const n = state.players.filter(p => p.att && p.att[day.id]).length;
    const amount = dayAmount(state, day.id);
    return { n, amount, share: split(amount, n, state.settings.roundUp) };
  }

  function allStats(state) {
    const map = {};
    days(state).forEach(d => { map[d.id] = dayStats(state, d); });
    return map;
  }

  // League fee per player, from the matches each one played. charges[id] = { amount, exact, parts }.
  function leagueCharges(state, stats, fee) {
    const perMatch = fee / MATCHES.length;
    const squad = state.players.filter(inTournament);
    const charges = {};
    state.players.forEach(p => { charges[p.id] = { amount: 0, exact: 0, parts: [] }; });
    if (!(fee > 0)) return { perMatch, unplayed: [], charges };

    const unplayed = MATCHES.filter(m => !stats[m.id] || stats[m.id].n === 0);
    MATCHES.forEach(m => {
      const n = stats[m.id] ? stats[m.id].n : 0;
      if (!n) return;
      state.players.filter(p => p.att && p.att[m.id]).forEach(p => {
        charges[p.id].exact += perMatch / n;
        charges[p.id].parts.push({ label: m.label, amount: perMatch, n, share: perMatch / n });
      });
    });
    if (unplayed.length && squad.length) {
      const amount = perMatch * unplayed.length;
      const label = unplayed.length === 1 ? unplayed[0].label + ' (no XI yet)' : unplayed.length + ' matches with no XI yet';
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
      const s = stats[d.id];
      if (player.att && player.att[d.id] && s.amount > 0) {
        cost += s.share;
        lines.push({ type: d.type, day: d, text: d.label + ': ' + fmt(s.amount) + ' ÷ ' + s.n + ' = ' + fmt(s.share) });
      }
      dayExpenses(state, d.id).forEach(e => {
        const amt = num(e.amount);
        if (e.paidBy === player.id && amt > 0) {
          credit += amt;
          creditLines.push({ type: d.type, day: d, text: (e.item || 'Expense') + ' (' + d.label + '): ' + fmt(amt) });
        }
      });
    });

    cost = round2(cost);
    credit = round2(credit);
    const paid = paidTotal(player);
    const net = round2(paid + credit - cost);
    return { cost, paid, credit, net, lines, creditLines };
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
    days(state).forEach(d => {
      daySpent += stats[d.id].amount;
      dayExpenses(state, d.id).forEach(e => { if (!e.paidBy) fundSpent += num(e.amount); });
    });
    return {
      collected: round2(collected),
      tournamentFee: tour.fee,
      daySpent: round2(daySpent),
      spent: round2(daySpent + tour.fee),
      fronted: round2(fronted),
      sentToManagement: sent,
      managementDue: round2(Math.max(tour.fee - sent, 0)),
      cashInHand: round2(collected - fundSpent),
      payBack: round2(payBack),
      owed: round2(owed)
    };
  }

  function warnings(state) {
    const stats = allStats(state);
    const out = days(state).filter(d => stats[d.id].amount > 0 && stats[d.id].n === 0).map(d => d.label);
    const tour = tournamentStats(state, stats);
    if (tour.fee > 0 && tour.n === 0 && tour.unplayed.length) out.push('Tournament fee');
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
    MATCHES, days, num, round2, inTournament, paidTotal, dayExpenses, dayAmount, dayStats, allStats,
    tournamentStats, advanceShare, advancePerPlayer, advanceDue, playerCalc, totals, warnings, money, signedMoney, netLabel
  };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.RFCalc = api;
})(this);
