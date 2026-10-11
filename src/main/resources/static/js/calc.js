// Pure ledger calculations (spec §3, §7). No DOM access, so it can be tested in Node.
//
// Model on top of the original spec:
//  - Each day has a list of expenses ({ id, item, amount, paidBy }). A day's amount is their sum.
//    paidBy '' means the team fund paid it; otherwise it is the id of the player who paid out of pocket.
//  - A player who paid for an expense gets that amount credited to their balance.
//  - A series-wide tournament fee is split equally among players taking part (player.tournament !== false).
//  - A player's payments ({ id, date, amount, method, note }) add up to what they have paid.
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

  function tournamentStats(state) {
    const fee = num(state.tournament && state.tournament.fee);
    const n = state.players.filter(inTournament).length;
    return { fee, n, share: split(fee, n, state.settings.roundUp) };
  }

  function playerCalc(state, player, stats, fmt, tour) {
    stats = stats || allStats(state);
    tour = tour || tournamentStats(state);
    let cost = 0;
    let credit = 0;
    const lines = [];
    const creditLines = [];

    if (tour.fee > 0 && inTournament(player)) {
      cost += tour.share;
      lines.push({ type: 'tournament', text: 'Tournament fee: ' + fmt(tour.fee) + ' ÷ ' + tour.n + ' = ' + fmt(tour.share) });
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
    const tour = tournamentStats(state);
    let collected = 0, payBack = 0, owed = 0, fronted = 0, daySpent = 0, fundSpent = tour.fee;
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
      cashInHand: round2(collected - fundSpent),
      payBack: round2(payBack),
      owed: round2(owed)
    };
  }

  function warnings(state) {
    const stats = allStats(state);
    const out = days(state).filter(d => stats[d.id].amount > 0 && stats[d.id].n === 0).map(d => d.label);
    const tour = tournamentStats(state);
    if (tour.fee > 0 && tour.n === 0) out.push('Tournament fee');
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
    tournamentStats, playerCalc, totals, warnings, money, signedMoney, netLabel
  };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.RFCalc = api;
})(this);
