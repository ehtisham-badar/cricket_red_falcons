// Draws the team's balances as one shareable PNG (a reminder picture for the WhatsApp group).
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

  /**
   * @param {object} o
   * @param {{name: string, net: number}[]} o.rows   players in Sr. no. order
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
    const twoCols = o.rows.length > 24;
    const perCol = twoCols ? Math.ceil(o.rows.length / 2) : o.rows.length;
    const ROW_H = twoCols ? 76 : 84;
    const GAP = 10;
    const headerH = 300;
    const chipsH = o.chips.length ? 96 : 24;
    const LEAGUE_H = 190;
    const leagueTop = headerH + chipsH;
    const listTop = leagueTop + (o.league ? LEAGUE_H : 0);
    const listH = Math.max(perCol, 1) * (ROW_H + GAP);
    const footerH = 150;
    const H = listTop + listH + footerH;

    const canvas = document.createElement('canvas');
    canvas.width = W;
    canvas.height = H;
    const ctx = canvas.getContext('2d');
    ctx.textBaseline = 'middle';

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
    ctx.fillText('RED FALCONS', tx, 122);
    ctx.fillStyle = C.text;
    ctx.font = `700 24px ${BODY}`;
    spaced(ctx, 'CRICKET CLUB · BALANCES', tx + 4, 182, 5);
    ctx.fillStyle = C.muted;
    ctx.font = `500 24px ${BODY}`;
    ctx.fillText(o.dateText, tx + 4, 226);

    // Red–gold rule under the header.
    const rule = ctx.createLinearGradient(PAD, 0, W - PAD, 0);
    rule.addColorStop(0, 'rgba(208,34,44,0)');
    rule.addColorStop(0.2, C.red);
    rule.addColorStop(0.5, C.gold);
    rule.addColorStop(0.8, C.red);
    rule.addColorStop(1, 'rgba(208,34,44,0)');
    ctx.fillStyle = rule;
    ctx.fillRect(PAD, headerH - 24, W - PAD * 2, 3);

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
      ctx.textAlign = 'left';

      // Name, truncated to the space left
      ctx.fillStyle = C.text;
      ctx.font = `700 ${twoCols ? 23 : 30}px ${BODY}`;
      const nameX = x + 86;
      ctx.fillText(fit(ctx, r.name || 'Unnamed', right - amountW - 28 - nameX), nameX, by + 1);
    });

    // Footer
    const fy = listTop + listH + 30;
    ctx.fillStyle = C.line;
    ctx.fillRect(PAD, fy, W - PAD * 2, 2);
    ctx.textAlign = 'center';
    ctx.fillStyle = C.text;
    ctx.font = `700 26px ${BODY}`;
    ctx.fillText('See your full breakdown', W / 2, fy + 44);
    ctx.fillStyle = C.gold;
    ctx.font = `800 28px ${BODY}`;
    ctx.fillText(o.link, W / 2, fy + 86);

    return new Promise(resolve => canvas.toBlob(resolve, 'image/png'));
  }

  root.RFBalanceImage = { render };
})(this);
