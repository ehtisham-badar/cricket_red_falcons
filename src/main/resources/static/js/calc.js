// Pure ledger calculations (spec §3, §7). No DOM access, so it can be tested in Node.
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

  function dayStats(state, day) {
    const n = state.players.filter(p => p.att && p.att[day.id]).length;
    const amount = num(state.amounts[day.id]);
    let share = 0;
    if (n > 0) share = state.settings.roundUp ? Math.ceil(amount / n) : round2(amount / n);
    return { n, amount, share };
  }

  function allStats(state) {
    const map = {};
    days(state).forEach(d => { map[d.id] = dayStats(state, d); });
    return map;
  }

  function playerCalc(state, player, stats, fmt) {
    stats = stats || allStats(state);
    let cost = 0;
    const lines = [];
    days(state).forEach(d => {
      const s = stats[d.id];
      if (player.att && player.att[d.id] && s.amount > 0) {
        cost += s.share;
        lines.push({ day: d, text: d.label + ': ' + fmt(s.amount) + ' ÷ ' + s.n + ' = ' + fmt(s.share), share: s.share });
      }
    });
    cost = round2(cost);
    const paid = num(player.paid);
    const net = round2(paid - cost);
    return { cost, paid, net, lines };
  }

  function totals(state, fmt) {
    const stats = allStats(state);
    let collected = 0, spent = 0, payBack = 0, owed = 0;
    state.players.forEach(p => {
      const c = playerCalc(state, p, stats, fmt);
      collected += c.paid;
      if (c.net > 0) payBack += c.net;
      if (c.net < 0) owed += -c.net;
    });
    days(state).forEach(d => { spent += stats[d.id].amount; });
    return { collected: round2(collected), spent: round2(spent), payBack: round2(payBack), owed: round2(owed) };
  }

  function warnings(state) {
    const stats = allStats(state);
    return days(state).filter(d => stats[d.id].amount > 0 && stats[d.id].n === 0).map(d => d.label);
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

  const api = { MATCHES, days, num, round2, dayStats, allStats, playerCalc, totals, warnings, money, signedMoney, netLabel };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.RFCalc = api;
})(this);
