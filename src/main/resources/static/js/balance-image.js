// Draws the shareable PNGs for the WhatsApp group: the team's balances and a match's Playing XI.
// Input rows are already calculated by the app; this file only does layout and drawing.
(function (root) {
  const W = 1080;
  const PAD = 64;
  const C = {
    bgTop: '#1c1c21', bgBottom: '#0e0e11', card: 'rgba(255,255,255,0.035)', line: 'rgba(255,255,255,0.08)',
    text: '#f5eee2', muted: '#a49d92', red: '#d0222c', due: '#ff6b6b', gold: '#e2b65c', goldLight: '#f7dd99',
    goldDeep: '#a57c2c', settled: '#8d8a85'
  };
  const HEAD = '"Alfa Slab One", Georgia, serif';
  const BODY = 'Figtree, system-ui, sans-serif';

  function loadImage(src) {
    return new Promise(resolve => {
      const img = new Image();
      img.onload = () => resolve(img);
      img.onerror = () => resolve(null);
      img.src = src;
    });
  }

  async function ensureFonts() {
    if (!document.fonts) return;
    await Promise.all([
      document.fonts.load(`64px ${HEAD}`), document.fonts.load(`400 24px ${BODY}`),
      document.fonts.load(`700 30px ${BODY}`), document.fonts.load(`800 30px ${BODY}`)
    ]).catch(() => {});
  }

  function fit(ctx, text, maxWidth) {
    if (ctx.measureText(text).width <= maxWidth) return text;
    let t = text;
    while (t.length > 1 && ctx.measureText(t + '…').width > maxWidth) t = t.slice(0, -1);
    return t + '…';
  }

  function roundRect(ctx, x, y, w, h, r) {
    ctx.beginPath();
    ctx.moveTo(x + r, y);
    ctx.arcTo(x + w, y, x + w, y + h, r);
    ctx.arcTo(x + w, y + h, x, y + h, r);
    ctx.arcTo(x, y + h, x, y, r);
    ctx.arcTo(x, y, x + w, y, r);
    ctx.closePath();
  }

  function spaced(ctx, text, x, y, spacing, align) {
    // Canvas letterSpacing isn't everywhere yet, so space characters manually.
    const chars = [...text];
    const width = chars.reduce((s, ch) => s + ctx.measureText(ch).width, 0) + spacing * (chars.length - 1);
    let cx = align === 'right' ? x - width : align === 'center' ? x - width / 2 : x;
    const prev = ctx.textAlign;
    ctx.textAlign = 'left';
    chars.forEach(ch => { ctx.fillText(ch, cx, y); cx += ctx.measureText(ch).width + spacing; });
    ctx.textAlign = prev;
  }

  // Background, crest, club name, subtitle and a red–gold rule ending at headerH.
  function drawHeader(ctx, H, logo, subtitle, dateText, headerH) {
    // Background with a red glow behind the crest.
    const bg = ctx.createLinearGradient(0, 0, 0, H);
    bg.addColorStop(0, C.bgTop);
    bg.addColorStop(1, C.bgBottom);
    ctx.fillStyle = bg;
    ctx.fillRect(0, 0, W, H);
    const glow = ctx.createRadialGradient(170, 150, 10, 170, 150, 520);
    glow.addColorStop(0, 'rgba(208,34,44,0.35)');
    glow.addColorStop(1, 'rgba(208,34,44,0)');
    ctx.fillStyle = glow;
    ctx.fillRect(0, 0, W, H);

    // Header: the whole crest, faded softly into the background (like the start screen).
    const LOGO = 220;
    if (logo) {
      const crest = document.createElement('canvas');
      crest.width = crest.height = LOGO;
      const cc = crest.getContext('2d');
      cc.drawImage(logo, 0, 0, LOGO, LOGO);
      const fade = cc.createRadialGradient(LOGO / 2, LOGO / 2, LOGO * 0.36, LOGO / 2, LOGO / 2, LOGO * 0.5);
      fade.addColorStop(0, 'rgba(0,0,0,1)');
      fade.addColorStop(1, 'rgba(0,0,0,0)');
      cc.globalCompositeOperation = 'destination-in';
      cc.fillStyle = fade;
      cc.fillRect(0, 0, LOGO, LOGO);
      ctx.drawImage(crest, PAD - 24, 150 - LOGO / 2);
    }
    const tx = PAD + LOGO + 8;
    const nameGrad = ctx.createLinearGradient(0, 90, 0, 160);
    nameGrad.addColorStop(0, C.goldLight);
    nameGrad.addColorStop(0.5, C.gold);
    nameGrad.addColorStop(1, C.goldDeep);
    ctx.fillStyle = nameGrad;
    ctx.font = `76px ${HEAD}`;
    ctx.fillText('RED FALCONS', tx, subtitle || dateText ? 122 : 150);
    if (subtitle) {
      ctx.fillStyle = C.text;
      ctx.font = `700 24px ${BODY}`;
      spaced(ctx, subtitle, tx + 4, 182, 5);
    }
    if (dateText) {
      ctx.fillStyle = C.muted;
      ctx.font = `500 24px ${BODY}`;
      ctx.fillText(dateText, tx + 4, 226);
    }

    // Red–gold rule under the header.
    const rule = ctx.createLinearGradient(PAD, 0, W - PAD, 0);
    rule.addColorStop(0, 'rgba(208,34,44,0)');
    rule.addColorStop(0.2, C.red);
    rule.addColorStop(0.5, C.gold);
    rule.addColorStop(0.8, C.red);
    rule.addColorStop(1, 'rgba(208,34,44,0)');
    ctx.fillStyle = rule;
    ctx.fillRect(PAD, headerH - 24, W - PAD * 2, 3);
  }

  function drawFooter(ctx, fy, title, link) {
    ctx.fillStyle = C.line;
    ctx.fillRect(PAD, fy, W - PAD * 2, 2);
    ctx.textAlign = 'center';
    ctx.fillStyle = C.text;
    ctx.font = `700 26px ${BODY}`;
    ctx.fillText(title, W / 2, fy + 44);
    ctx.fillStyle = C.gold;
    ctx.font = `800 28px ${BODY}`;
    ctx.fillText(link, W / 2, fy + 86);
    ctx.textAlign = 'left';
  }

  /**
   * @param {object} o
   * @param {{name: string, net: number, cells?: {text: string, color: string}[]}[]} o.rows
   *        players in Sr. no. order; cells fill the extra columns
   * @param {{label: string, width: number}[]} [o.columns]  extra columns drawn left of the total
   * @param {{label: string, value: string}[]} [o.payTo]   account details for paying, shown above the footer
   * @param {(n: number) => string} o.money           formats a positive amount, e.g. "Rs 3,129.55"
   * @param {string[]} o.chips                        short summary lines shown under the header
   * @param {{name: string, figures: {label: string, value: string, color: string}[]}} [o.league]
   *        league money (fee, paid to management, still owed, in account) drawn as a row of boxes
   * @param {string} o.dateText, o.link, o.logoSrc
   * @returns {Promise<Blob>}
   */
  async function render(o) {
    await ensureFonts();
    const logo = await loadImage(o.logoSrc);

    // One column keeps names readable; switch to two only for big squads so the picture stays a sane height.
    // Extra columns need the full width, so they keep a single column.
    const cols = o.columns || [];
    const twoCols = !cols.length && o.rows.length > 24;
    const perCol = twoCols ? Math.ceil(o.rows.length / 2) : o.rows.length;
    const ROW_H = twoCols ? 76 : 84;
    const GAP = 10;
    const headerH = 300;
    const chipsH = o.chips.length ? 96 : 24;
    const LEAGUE_H = 190;
    const leagueTop = headerH + chipsH;
    const COLHEAD_H = cols.length ? 48 : 0;
    const colHeadTop = leagueTop + (o.league ? LEAGUE_H : 0);
    const listTop = colHeadTop + COLHEAD_H;
    const listH = Math.max(perCol, 1) * (ROW_H + GAP);
    const PAY_H = o.payTo && o.payTo.length ? 200 : 0;
    const footerH = 150;
    const H = listTop + listH + PAY_H + footerH;

    const canvas = document.createElement('canvas');
    canvas.width = W;
    canvas.height = H;
    const ctx = canvas.getContext('2d');
    ctx.textBaseline = 'middle';

    drawHeader(ctx, H, logo, 'CRICKET CLUB · BALANCES', o.dateText, headerH);

    // Summary chips.
    if (o.chips.length) {
      ctx.font = `700 24px ${BODY}`;
      let cx = PAD;
      const cy = headerH + 22;
      o.chips.forEach(chip => {
        const w = ctx.measureText(chip.text).width + 44;
        roundRect(ctx, cx, cy, w, 52, 26);
        ctx.fillStyle = chip.bg;
        ctx.fill();
        ctx.fillStyle = chip.color;
        ctx.fillText(chip.text, cx + 22, cy + 27);
        cx += w + 14;
      });
    }

    // League money: fee, paid to management, still owed to management, in the admin's account.
    if (o.league) {
      ctx.fillStyle = C.gold;
      ctx.font = `800 20px ${BODY}`;
      spaced(ctx, o.league.name.toUpperCase(), PAD, leagueTop + 14, 3);
      const n = o.league.figures.length;
      const gap = 14;
      const boxW = (W - PAD * 2 - gap * (n - 1)) / n;
      const boxY = leagueTop + 40;
      o.league.figures.forEach((f, i) => {
        const bx = PAD + i * (boxW + gap);
        roundRect(ctx, bx, boxY, boxW, 112, 16);
        ctx.fillStyle = 'rgba(226,182,92,0.07)';
        ctx.fill();
        ctx.strokeStyle = 'rgba(226,182,92,0.25)';
        ctx.lineWidth = 2;
        ctx.stroke();
        ctx.fillStyle = C.muted;
        ctx.font = `800 15px ${BODY}`;
        spaced(ctx, fit(ctx, f.label.toUpperCase(), boxW - 40), bx + 20, boxY + 32, 1.5);
        ctx.fillStyle = f.color;
        ctx.font = `800 30px ${BODY}`;
        ctx.fillText(fit(ctx, f.value, boxW - 40), bx + 20, boxY + 76);
      });
    }

    // Player rows.
    const colW = twoCols ? (W - PAD * 2 - 24) / 2 : W - PAD * 2;
    // Right edges of the extra columns, laid out leftwards from the total.
    const TOTAL_W = 210;
    const totalRight = PAD + colW - 24;
    const colRights = [];
    cols.reduceRight((edge, c, i) => { colRights[i] = edge; return edge - c.width; }, totalRight - TOTAL_W);
    const namesEnd = cols.length ? colRights[0] - cols[0].width : null;
    if (cols.length) {
      ctx.fillStyle = C.muted;
      ctx.font = `800 16px ${BODY}`;
      const hy = colHeadTop + 18;
      spaced(ctx, 'PLAYER', PAD + 86, hy, 2);
      cols.forEach((c, i) => spaced(ctx, c.label.toUpperCase(), colRights[i], hy, 2, 'right'));
      spaced(ctx, 'TOTAL DUE', totalRight, hy, 2, 'right');
    }
    o.rows.forEach((r, i) => {
      const col = twoCols && i >= perCol ? 1 : 0;
      const idx = col ? i - perCol : i;
      const x = PAD + col * (colW + 24);
      const y = listTop + idx * (ROW_H + GAP);
      const kind = r.net < 0 ? 'due' : r.net > 0 ? 'back' : 'settled';
      const accent = kind === 'due' ? C.due : kind === 'back' ? C.gold : C.settled;

      roundRect(ctx, x, y, colW, ROW_H, 18);
      ctx.fillStyle = C.card;
      ctx.fill();
      ctx.fillStyle = accent;
      roundRect(ctx, x, y, 6, ROW_H, 3);
      ctx.fill();

      // Sr. no. badge
      const bx = x + 46, by = y + ROW_H / 2;
      ctx.beginPath();
      ctx.arc(bx, by, 22, 0, Math.PI * 2);
      ctx.fillStyle = 'rgba(208,34,44,0.18)';
      ctx.fill();
      ctx.fillStyle = C.due;
      ctx.font = `800 20px ${BODY}`;
      ctx.textAlign = 'center';
      ctx.fillText(String(i + 1), bx, by + 1);
      ctx.textAlign = 'left';

      // Amount + label on the right
      const amount = kind === 'due' ? o.money(-r.net) : kind === 'back' ? '+' + o.money(r.net) : 'Settled';
      const label = kind === 'due' ? 'DUE' : kind === 'back' ? 'PAY BACK' : '✓';
      const right = x + colW - 24;
      ctx.textAlign = 'right';
      ctx.fillStyle = accent;
      ctx.font = `800 ${twoCols ? 24 : 32}px ${BODY}`;
      const amountY = kind === 'settled' ? by : by - 11;
      ctx.fillText(amount, right, amountY);
      const amountW = ctx.measureText(amount).width;
      if (kind !== 'settled') {
        ctx.font = `800 16px ${BODY}`;
        spaced(ctx, label, right, by + 21, 2, 'right');
      }
      ctx.font = `800 26px ${BODY}`;
      (r.cells || []).forEach((cell, ci) => {
        ctx.fillStyle = cell.color;
        ctx.fillText(cell.text, colRights[ci], by + 1);
      });
      ctx.textAlign = 'left';

      // Name, truncated to the space left
      ctx.fillStyle = C.text;
      ctx.font = `700 ${twoCols ? 23 : 30}px ${BODY}`;
      const nameX = x + 86;
      const nameEnd = namesEnd !== null ? namesEnd - 20 : right - amountW - 28;
      ctx.fillText(fit(ctx, r.name || 'Unnamed', nameEnd - nameX), nameX, by + 1);
    });

    // Where to send the money.
    if (PAY_H) {
      const py = listTop + listH + 30;
      roundRect(ctx, PAD, py, W - PAD * 2, PAY_H - 30, 18);
      ctx.fillStyle = 'rgba(226,182,92,0.07)';
      ctx.fill();
      ctx.strokeStyle = 'rgba(226,182,92,0.25)';
      ctx.lineWidth = 2;
      ctx.stroke();
      ctx.fillStyle = C.gold;
      ctx.font = `800 20px ${BODY}`;
      spaced(ctx, 'PAY TO', PAD + 28, py + 34, 3);
      const n = o.payTo.length;
      const cw = (W - PAD * 2 - 56) / n;
      o.payTo.forEach((f, i) => {
        const cx = PAD + 28 + i * cw;
        ctx.fillStyle = C.muted;
        ctx.font = `800 15px ${BODY}`;
        spaced(ctx, f.label.toUpperCase(), cx, py + 78, 1.5);
        ctx.fillStyle = C.text;
        ctx.font = `800 30px ${BODY}`;
        ctx.fillText(fit(ctx, f.value, cw - 20), cx, py + 120);
      });
    }

    drawFooter(ctx, listTop + listH + PAY_H + 30, 'See your full breakdown', o.link);

    return new Promise(resolve => canvas.toBlob(resolve, 'image/png'));
  }

  /**
   * A match's Playing XI.
   * @param {object} o
   * @param {string} o.matchLabel                     e.g. "Match 1"
   * @param {string} o.leagueName                     shown under the club name
   * @param {string} [o.opponent], [o.when], [o.venue] optional details typed in before sharing
   * @param {{name: string, role: string, captain: boolean, keeper: boolean, opener?: boolean}[]} o.players  in batting order
   * @param {{name: string, role: string}} [o.twelfth]  the 12th man, drawn below the XI
   * @param {string} o.link, o.logoSrc
   * @returns {Promise<Blob>}
   */
  async function renderXI(o) {
    await ensureFonts();
    const logo = await loadImage(o.logoSrc);

    const opponent = (o.opponent || '').trim();
    const info = [o.when, o.venue].map(x => (x || '').trim()).filter(Boolean).join('  ·  ');
    const headerH = 300;
    const bannerTop = headerH + 10;
    const report = (o.report || '').trim();
    const bannerH = 120 + (opponent ? 56 : 0) + (info ? 44 : 0) + (report ? 70 : 0);
    const listTop = bannerTop + bannerH + 64;
    const ROW_H = 84;
    const GAP = 10;
    const listH = Math.max(o.players.length, 1) * (ROW_H + GAP);
    const TWELFTH_H = o.twelfth ? 56 + ROW_H + GAP : 0;
    const H = listTop + listH + TWELFTH_H + 150;

    const canvas = document.createElement('canvas');
    canvas.width = W;
    canvas.height = H;
    const ctx = canvas.getContext('2d');
    ctx.textBaseline = 'middle';
    drawHeader(ctx, H, logo, 'CRICKET CLUB · PLAYING XI', o.leagueName, headerH);

    // Match banner: match name, opponent, then date and venue.
    roundRect(ctx, PAD, bannerTop, W - PAD * 2, bannerH, 22);
    ctx.fillStyle = 'rgba(226,182,92,0.07)';
    ctx.fill();
    ctx.strokeStyle = 'rgba(226,182,92,0.3)';
    ctx.lineWidth = 2;
    ctx.stroke();
    ctx.textAlign = 'center';
    const titleGrad = ctx.createLinearGradient(0, bannerTop + 30, 0, bannerTop + 90);
    titleGrad.addColorStop(0, C.goldLight);
    titleGrad.addColorStop(1, C.gold);
    ctx.fillStyle = titleGrad;
    ctx.font = `60px ${HEAD}`;
    ctx.fillText(o.matchLabel.toUpperCase(), W / 2, bannerTop + 66);
    let by = bannerTop + 120;
    if (opponent) {
      ctx.fillStyle = C.text;
      ctx.font = `800 36px ${BODY}`;
      ctx.fillText(fit(ctx, 'vs ' + opponent, W - PAD * 2 - 60), W / 2, by + 6);
      by += 56;
    }
    if (info) {
      ctx.fillStyle = C.muted;
      ctx.font = `600 24px ${BODY}`;
      ctx.fillText(fit(ctx, info, W - PAD * 2 - 60), W / 2, by);
      by += 44;
    }
    // Reporting time: when the players should be at the ground.
    if (report) {
      const label = 'REPORT BY';
      ctx.font = `800 18px ${BODY}`;
      const lw = [...label].reduce((w, ch) => w + ctx.measureText(ch).width, 0) + 3 * (label.length - 1);
      ctx.font = `800 32px ${BODY}`;
      const tw = ctx.measureText(report).width;
      const pw = lw + tw + 74;
      const px = (W - pw) / 2;
      const py = by - 12;
      roundRect(ctx, px, py, pw, 54, 27);
      ctx.fillStyle = 'rgba(208,34,44,0.22)';
      ctx.fill();
      ctx.strokeStyle = 'rgba(208,34,44,0.7)';
      ctx.lineWidth = 2;
      ctx.stroke();
      ctx.textAlign = 'left';
      ctx.fillStyle = C.muted;
      ctx.font = `800 18px ${BODY}`;
      spaced(ctx, label, px + 28, py + 28, 3);
      ctx.fillStyle = C.goldLight;
      ctx.font = `800 32px ${BODY}`;
      ctx.fillText(report, px + 28 + lw + 18, py + 29);
    }
    ctx.textAlign = 'left';

    // Players in batting order, with captain and keeper badges; openers get gold numbers.
    const colW = W - PAD * 2;
    ctx.fillStyle = C.gold;
    ctx.font = `800 18px ${BODY}`;
    spaced(ctx, 'BATTING ORDER', PAD + 4, listTop - 24, 3);
    // The order can still change, so say so next to the heading.
    const headW = [...'BATTING ORDER'].reduce((w, ch) => w + ctx.measureText(ch).width, 0) + 3 * 12;
    ctx.fillStyle = C.muted;
    ctx.font = `600 18px ${BODY}`;
    ctx.fillText('(not finalized)', PAD + 4 + headW + 14, listTop - 24);
    o.players.forEach((p, i) => {
      const x = PAD;
      const y = listTop + i * (ROW_H + GAP);
      const cy = y + ROW_H / 2;
      roundRect(ctx, x, y, colW, ROW_H, 18);
      ctx.fillStyle = C.card;
      ctx.fill();
      ctx.fillStyle = p.captain ? C.gold : C.red;
      roundRect(ctx, x, y, 6, ROW_H, 3);
      ctx.fill();

      const bx = x + 46;
      ctx.beginPath();
      ctx.arc(bx, cy, 22, 0, Math.PI * 2);
      ctx.fillStyle = p.opener ? 'rgba(226,182,92,0.2)' : 'rgba(208,34,44,0.18)';
      ctx.fill();
      ctx.fillStyle = p.opener ? C.gold : C.due;
      ctx.font = `800 20px ${BODY}`;
      ctx.textAlign = 'center';
      ctx.fillText(String(i + 1), bx, cy + 1);

      const right = x + colW - 24;
      ctx.textAlign = 'right';
      ctx.fillStyle = p.opener ? C.gold : C.muted;
      ctx.font = `600 22px ${BODY}`;
      const role = p.opener ? ['Opener', p.role].filter(Boolean).join(' · ') : (p.role || '');
      ctx.fillText(role, right, cy + 1);
      const roleW = role ? ctx.measureText(role).width + 24 : 0;
      ctx.textAlign = 'left';

      const badges = [p.captain && { text: 'C', bg: C.gold, color: '#1c1c21' }, p.keeper && { text: 'WK', bg: 'rgba(208,34,44,0.85)', color: C.text }].filter(Boolean);
      ctx.font = `800 18px ${BODY}`;
      const badgesW = badges.reduce((w, b) => w + ctx.measureText(b.text).width + 22 + 8, 0);
      const nameX = x + 86;
      ctx.fillStyle = C.text;
      ctx.font = `700 30px ${BODY}`;
      const name = fit(ctx, p.name || 'Unnamed', right - roleW - badgesW - 12 - nameX);
      ctx.fillText(name, nameX, cy + 1);
      let tx = nameX + ctx.measureText(name).width + 14;
      ctx.font = `800 18px ${BODY}`;
      badges.forEach(b => {
        const w = ctx.measureText(b.text).width + 22;
        roundRect(ctx, tx, cy - 16, w, 32, 16);
        ctx.fillStyle = b.bg;
        ctx.fill();
        ctx.fillStyle = b.color;
        ctx.textAlign = 'center';
        ctx.fillText(b.text, tx + w / 2, cy + 1);
        ctx.textAlign = 'left';
        tx += w + 8;
      });
    });

    if (o.twelfth) {
      const ly = listTop + listH + 22;
      ctx.fillStyle = C.gold;
      ctx.font = `800 18px ${BODY}`;
      spaced(ctx, '12TH MAN', PAD + 4, ly, 3);
      const y = ly + 34;
      const cy = y + ROW_H / 2;
      roundRect(ctx, PAD, y, colW, ROW_H, 18);
      ctx.fillStyle = 'rgba(226,182,92,0.06)';
      ctx.fill();
      ctx.strokeStyle = 'rgba(226,182,92,0.35)';
      ctx.setLineDash([8, 6]);
      ctx.lineWidth = 2;
      ctx.stroke();
      ctx.setLineDash([]);
      ctx.fillStyle = C.gold;
      ctx.font = `800 18px ${BODY}`;
      ctx.textAlign = 'center';
      ctx.fillText('12', PAD + 46, cy + 1);
      const right = PAD + colW - 24;
      ctx.textAlign = 'right';
      ctx.fillStyle = C.muted;
      ctx.font = `600 22px ${BODY}`;
      ctx.fillText(o.twelfth.role || '', right, cy + 1);
      const roleW = o.twelfth.role ? ctx.measureText(o.twelfth.role).width + 24 : 0;
      ctx.textAlign = 'left';
      ctx.fillStyle = C.text;
      ctx.font = `700 30px ${BODY}`;
      ctx.fillText(fit(ctx, o.twelfth.name || 'Unnamed', right - roleW - PAD - 86), PAD + 86, cy + 1);
    }

    drawFooter(ctx, listTop + listH + TWELFTH_H + 30, 'Follow the team', o.link);
    return new Promise(resolve => canvas.toBlob(resolve, 'image/png'));
  }

  /**
   * The squad list for management: the club name and crest on top, then each player's name, role and jersey number.
   * @param {{name: string, role: string, jersey: string, captain?: boolean, viceCaptain?: boolean, keeper?: boolean}[]} o.players
   * @param {string} o.dateText, o.logoSrc
   * @returns {Promise<Blob>}
   */
  async function renderSquad(o) {
    await ensureFonts();
    const logo = await loadImage(o.logoSrc);
    const n = o.players.length;
    const ROW_H = 72;
    const GAP = 10;
    const headerH = 290;
    const titleY = headerH + 20;
    const colHeadY = headerH + 84;
    const listTop = headerH + 112;
    const H = listTop + Math.max(n, 1) * (ROW_H + GAP) + 120;

    const canvas = document.createElement('canvas');
    canvas.width = W;
    canvas.height = H;
    const ctx = canvas.getContext('2d');
    ctx.textBaseline = 'middle';
    drawHeader(ctx, H, logo, '', '', headerH);

    ctx.fillStyle = C.gold;
    ctx.font = `800 24px ${BODY}`;
    spaced(ctx, 'SQUAD LIST', PAD + 4, titleY, 4);
    ctx.textAlign = 'right';
    ctx.fillStyle = C.muted;
    ctx.font = `700 22px ${BODY}`;
    ctx.fillText(`${n} player${n === 1 ? '' : 's'}`, W - PAD - 4, titleY);
    ctx.textAlign = 'left';

    const colW = W - PAD * 2;
    const nameX = PAD + 92;
    const roleX = PAD + 520;
    const right = PAD + colW - 28;
    ctx.fillStyle = C.muted;
    ctx.font = `800 17px ${BODY}`;
    spaced(ctx, 'NAME', nameX, colHeadY, 3);
    spaced(ctx, 'ROLE', roleX, colHeadY, 3);
    spaced(ctx, 'JERSEY', right, colHeadY, 3, 'right');

    o.players.forEach((p, i) => {
      const y = listTop + i * (ROW_H + GAP);
      const cy = y + ROW_H / 2;
      roundRect(ctx, PAD, y, colW, ROW_H, 18);
      ctx.fillStyle = C.card;
      ctx.fill();
      ctx.fillStyle = C.red;
      roundRect(ctx, PAD, y, 6, ROW_H, 3);
      ctx.fill();

      const bx = PAD + 46;
      ctx.beginPath();
      ctx.arc(bx, cy, 21, 0, Math.PI * 2);
      ctx.fillStyle = 'rgba(208,34,44,0.18)';
      ctx.fill();
      ctx.fillStyle = C.due;
      ctx.font = `800 19px ${BODY}`;
      ctx.textAlign = 'center';
      ctx.fillText(String(i + 1), bx, cy + 1);
      ctx.textAlign = 'left';

      // C, VC and WK badges follow the name.
      const badges = [p.captain && { text: 'C', bg: C.gold, color: '#1c1c21' },
        p.viceCaptain && { text: 'VC', bg: 'rgba(226,182,92,0.22)', color: C.gold, ring: C.gold },
        p.keeper && { text: 'WK', bg: 'rgba(208,34,44,0.85)', color: C.text }].filter(Boolean);
      ctx.font = `800 18px ${BODY}`;
      const badgesW = badges.reduce((w, b) => w + ctx.measureText(b.text).width + 22 + 8, 0);
      ctx.fillStyle = C.text;
      ctx.font = `700 28px ${BODY}`;
      const nm = fit(ctx, p.name || 'Unnamed', roleX - nameX - 16 - badgesW);
      ctx.fillText(nm, nameX, cy + 1);
      let bxx = nameX + ctx.measureText(nm).width + 14;
      ctx.font = `800 18px ${BODY}`;
      badges.forEach(b => {
        const w = ctx.measureText(b.text).width + 22;
        roundRect(ctx, bxx, cy - 16, w, 32, 16);
        ctx.fillStyle = b.bg;
        ctx.fill();
        if (b.ring) { ctx.strokeStyle = b.ring; ctx.lineWidth = 2; ctx.stroke(); }
        ctx.fillStyle = b.color;
        ctx.textAlign = 'center';
        ctx.fillText(b.text, bxx + w / 2, cy + 1);
        ctx.textAlign = 'left';
        bxx += w + 8;
      });
      ctx.fillStyle = C.muted;
      ctx.font = `600 22px ${BODY}`;
      ctx.fillText(fit(ctx, p.role || '—', right - 110 - roleX), roleX, cy + 1);

      const label = p.jersey ? '#' + p.jersey : '—';
      ctx.font = `800 26px ${BODY}`;
      const w = Math.max(70, ctx.measureText(label).width + 36);
      roundRect(ctx, right - w, cy - 22, w, 44, 22);
      ctx.fillStyle = p.jersey ? 'rgba(226,182,92,0.16)' : 'rgba(255,255,255,0.05)';
      ctx.fill();
      ctx.fillStyle = p.jersey ? C.gold : C.muted;
      ctx.textAlign = 'center';
      ctx.fillText(label, right - w / 2, cy + 1);
      ctx.textAlign = 'left';
    });

    const fy = listTop + Math.max(n, 1) * (ROW_H + GAP) + 18;
    ctx.fillStyle = C.line;
    ctx.fillRect(PAD, fy, W - PAD * 2, 2);
    ctx.textAlign = 'center';
    ctx.fillStyle = C.muted;
    ctx.font = `600 24px ${BODY}`;
    ctx.fillText(o.dateText, W / 2, fy + 50);
    ctx.textAlign = 'left';
    return new Promise(resolve => canvas.toBlob(resolve, 'image/png'));
  }

  root.RFBalanceImage = { render, renderXI, renderSquad };
})(this);
