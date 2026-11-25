// Logo Discord d'Orbit MC en pixel art : la Station au centre, reliée à 5 portails (les modes).
// Tout est dessiné pixel par pixel par ce script, sans IA ni dépendance : `node logo-discord-pixel.cjs`
// Sorties (à côté du script) : icône 128 px native + agrandie x4 / x8, bannière 320x180 native + x3 / x6.
const zlib = require('zlib'), fs = require('fs'), path = require('path');
const OUT = __dirname;

// ---------------------------------------------------------------- palette
const S = ['#0B1020', '#1B2740', '#2E4163', '#486388', '#6C8CAE', '#9DBBD3', '#D8ECF5']; // acier
const G = ['#3A2410', '#6B4418', '#A8742A', '#D9A441', '#F4D06F', '#FFF3B8'];            // or
const I = ['#0E3A66', '#1C6FA8', '#35A7DE', '#62CFF4', '#B4ECFF', '#FFFFFF'];            // bleu glacé
const NOIR = '#03060D';
const ESPACE = ['#05070F', '#080D1C', '#0C152C', '#121F40'];

// ---------------------------------------------------------------- image
class Img {
  constructor(w, h) { this.w = w; this.h = h; this.d = new Array(w * h).fill(null); }
  get(x, y) { return x < 0 || y < 0 || x >= this.w || y >= this.h ? null : this.d[y * this.w + x]; }
  set(x, y, c) { if (x >= 0 && y >= 0 && x < this.w && y < this.h) this.d[y * this.w + x] = c; }
  blit(src, ox = 0, oy = 0) {
    for (let y = 0; y < src.h; y++) for (let x = 0; x < src.w; x++) {
      const c = src.d[y * src.w + x]; if (c) this.set(x + ox, y + oy, c);
    }
  }
  // contour d'un pixel autour de tout ce qui est dessiné
  outline(col, diag = false) {
    const o = new Img(this.w, this.h); o.d = this.d.slice();
    for (let y = 0; y < this.h; y++) for (let x = 0; x < this.w; x++) {
      if (this.get(x, y)) continue;
      let n = this.get(x - 1, y) || this.get(x + 1, y) || this.get(x, y - 1) || this.get(x, y + 1);
      if (!n && diag) n = this.get(x - 1, y - 1) || this.get(x + 1, y - 1) || this.get(x - 1, y + 1) || this.get(x + 1, y + 1);
      if (n) o.set(x, y, col);
    }
    return o;
  }
}

const CRC = (() => { const t = new Uint32Array(256); for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xEDB88320 ^ (c >>> 1) : c >>> 1; t[n] = c >>> 0; } return t; })();
function crc32(buf) { let c = 0xFFFFFFFF; for (const b of buf) c = CRC[(c ^ b) & 255] ^ (c >>> 8); return (c ^ 0xFFFFFFFF) >>> 0; }
function chunk(type, data) {
  const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
  const td = Buffer.concat([Buffer.from(type), data]);
  const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(td));
  return Buffer.concat([len, td, crc]);
}
function savePng(img, file, scale = 1) {
  const W = img.w * scale, H = img.h * scale;
  const raw = Buffer.alloc((W * 3 + 1) * H);
  for (let y = 0; y < H; y++) {
    const row = y * (W * 3 + 1); raw[row] = 0;
    for (let x = 0; x < W; x++) {
      const c = img.d[Math.floor(y / scale) * img.w + Math.floor(x / scale)] || '#000000';
      raw[row + 1 + x * 3] = parseInt(c.slice(1, 3), 16);
      raw[row + 2 + x * 3] = parseInt(c.slice(3, 5), 16);
      raw[row + 3 + x * 3] = parseInt(c.slice(5, 7), 16);
    }
  }
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(W, 0); ihdr.writeUInt32BE(H, 4); ihdr[8] = 8; ihdr[9] = 2;
  fs.writeFileSync(path.join(OUT, file), Buffer.concat([
    Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ihdr),
    chunk('IDAT', zlib.deflateSync(raw, { level: 9 })), chunk('IEND', Buffer.alloc(0))]));
}

function rng(a) { return () => { a |= 0; a = a + 0x6D2B79F5 | 0; let t = Math.imul(a ^ a >>> 15, 1 | a); t = t + Math.imul(t ^ t >>> 7, 61 | t) ^ t; return ((t ^ t >>> 14) >>> 0) / 4294967296; }; }
const BAYER = [[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]];
const bayer = (x, y) => (BAYER[y & 3][x & 3] + 0.5) / 16;
const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
const deg = r => r * 180 / Math.PI, mod = (a, m) => ((a % m) + m) % m;

// ---------------------------------------------------------------- fond : espace tramé + étoiles
function background(w, h, halos, seed) {
  const img = new Img(w, h), rnd = rng(seed), C = 26;
  const gw = Math.ceil(w / C) + 2, gh = Math.ceil(h / C) + 2;
  const grid = Array.from({ length: gw * gh }, () => rnd());
  const sm = t => t * t * (3 - 2 * t), lerp = (a, b, t) => a + (b - a) * t;
  const noise = (x, y) => {
    const gx = x / C, gy = y / C, x0 = Math.floor(gx), y0 = Math.floor(gy), tx = sm(gx - x0), ty = sm(gy - y0);
    const g = (i, j) => grid[j * gw + i];
    return lerp(lerp(g(x0, y0), g(x0 + 1, y0), tx), lerp(g(x0, y0 + 1), g(x0 + 1, y0 + 1), tx), ty);
  };
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    let halo = 0;
    for (const [hx, hy, hr, f] of halos) { const t = Math.max(0, 1 - Math.hypot(x - hx, y - hy) / hr); halo = Math.max(halo, Math.pow(t, 1.3) * f); }
    const v = noise(x, y) * 0.42 - 0.1 + halo;
    img.set(x, y, ESPACE[clamp(Math.floor(v * 3.5 + (bayer(x, y) - 0.5) * 0.6), 0, 3)]);
  }
  const n = Math.round(w * h / 210);
  for (let i = 0; i < n; i++) {
    const x = Math.floor(rnd() * w), y = Math.floor(rnd() * h), r = rnd();
    img.set(x, y, r < 0.5 ? '#2C3B62' : r < 0.82 ? '#6F8AC2' : r < 0.95 ? '#DCEBFF' : '#F4D06F');
  }
  return img;
}
function sparkle(img, x, y, big) {
  img.set(x, y, '#FFFFFF');
  for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
    img.set(x + dx, y + dy, '#B4ECFF');
    if (big) img.set(x + dx * 2, y + dy * 2, '#35A7DE');
  }
}

// ---------------------------------------------------------------- les 5 univers (19x19, dessinés à la main)
const UNIVERS = {
  survie: {
    glow: '#C8F99A', col: '#7BD84E', dark: '#2E7D32',
    pal: { a: '#2F8FCC', b: '#7CCBEA', c: '#F2FBFF', s: '#FFF3B8', L: '#2E7D32', l: '#5DBB3F', y: '#A6EE6A', t: '#6B4418', h: '#3E9B4F', g: '#7BD84E', G: '#3F9A3A', f: '#FFF3B8' },
    px: [
      'aaaaaaaaaaaaaaaaaaa',
      'aaaaaaaaaaaaaaaaaaa',
      'aaaaaaaaaaaaaaaaaaa',
      'aaaaaLLLLaaaassaaaa',
      'aaaLLllllLLaassaaaa',
      'bbLLllyllllLbbbbbbb',
      'bbLlllllLllLLbbccbb',
      'bbLLlLllllLLbbccccb',
      'bbbLLLlLLLLbbbbbbbb',
      'bbbbbLttLbbbbbbhhhb',
      'bbbbbbttbbbbbhhhhhh',
      'hhhbbbttbbbhhhhhhhh',
      'hhhhhhtthhhhhhhhhhh',
      'ggggggttgggggggGGgg',
      'gggGGgggggfggGGGGgg',
      'gGGGGGGggggGGGGGGGG',
      'GGGGGGGGGGGGGGGGGGG',
      'GGGGGGGGGGGGGGGGGGG',
      'GGGGGGGGGGGGGGGGGGG'],
  },
  extraction: {
    glow: '#FFC08A', col: '#FF6A2B', dark: '#8A2A0A',
    pal: { k: '#1E0808', r: '#5E120E', R: '#9A2412', o: '#FF6A2B', y: '#FFC24A', B: '#0D0606', w: '#FFE08A', m: '#3A1512' },
    px: [
      'kkkkkkkkkkkkkkkkkkk',
      'kkkkkkkkkkkkkkkkkkk',
      'kkkkkmmmkkkkkkkkkkk',
      'kkkkmmmmmkkkkkkkkkk',
      'kkkkkmmmkkkkkkmmkkk',
      'rrrrrrmmrrrrrmmmrrr',
      'rrrrrrrmrrrrrrmrrrr',
      'rrrrrrrrBBBrrrrrrrr',
      'RRRBBRRRBBBRRRRRRRR',
      'RRRBBRRRBwBRRRBBBBR',
      'RRRBwRRRBBBRRRBBBBR',
      'ooBBBBooBBBoooBBBBo',
      'ooBBwBooBwBoooBwBBo',
      'oyBBBByoBBByoyBBBBy',
      'yyBBBByyBBByyyBBBBy',
      'BBBBBBBBBBBBBBBBBBB',
      'BBBBBBBBBBBBBBBBBBB',
      'BBBBBBBBBBBBBBBBBBB',
      'BBBBBBBBBBBBBBBBBBB'],
  },
  practice: {
    glow: '#EBC4FF', col: '#C46BFF', dark: '#5A1FA0',
    pal: { p: '#F6C8FF', P: '#C455F0', c: '#B4F6FF', C: '#2FB8E0', W: '#FFFFFF', x: '#C8B0FF', g: '#F4D06F', G: '#A8742A' },
    fond: (dx, dy, r) => r < 3.6 ? '#4A2390' : r < 6.2 ? '#2E1560' : '#1A0B33',
    px: [
      '...................',
      '...................',
      '...................',
      '....p.........c....',
      '....Pp.......cC....',
      '.....Pp.....cC.....',
      '......Pp...cC......',
      '.......Pp.cC.......',
      '........PWC........',
      '....g...cxp...g....',
      '.....g.cC.Pp.g.....',
      '......g.....g......',
      '.....G.g...g.G.....',
      '....G...g.g...G....',
      '...g...........g...',
      '...................',
      '...................',
      '...................',
      '...................'],
  },
  mmo: {
    glow: '#FFF0B0', col: '#F4C24F', dark: '#8A5A14',
    pal: { n: '#231E66', N: '#3F3391', v: '#6B55B8', e: '#A886E0', m: '#FFF3B8', M: '#F4D06F', C: '#120F2E', w: '#FFC24A', s: '#FFFFFF', f: '#F0475A', H: '#120F2E' },
    px: [
      'nnnnnnnnnnnnnnnnnnn',
      'nnnnnnnsnnnnnnnnnnn',
      'nnnnnnnnnnmmmmnnnnn',
      'nnsnnnnnnmmmmmmnnnn',
      'nnnnfnnnmmmmmmmMnnn',
      'NNNNCNNNmmmmmmMMNsN',
      'NNNCCCNNmmmmmCMMNNN',
      'NNNCCCNNNmmmCCCMNNN',
      'vvvCCCvvvvmMCCCvvvv',
      'vvvCwCvvvvvvCCCvvvv',
      'eeeCCCCeCeCeCwCeeee',
      'eeeCCCCCCCCCCCCeeee',
      'eeeCCCCCCCCCCCCeeee',
      'eeeCCCCCwwCCCCCeeee',
      'eeHCCCCCwwCCCCCHHee',
      'HHHHHHHHHHHHHHHHHHH',
      'HHHHHHHHHHHHHHHHHHH',
      'HHHHHHHHHHHHHHHHHHH',
      'HHHHHHHHHHHHHHHHHHH'],
  },
  tycoon: {
    glow: '#B4FFEA', col: '#4FD8B4', dark: '#147A63',
    // engrenage : 8 dents, moyeu doré
    fond: (dx, dy, r) => {
      let dent = false;
      for (let k = 0; k < 8; k++) {
        const a = k * Math.PI / 4, ux = Math.cos(a), uy = Math.sin(a);
        const al = dx * ux + dy * uy, ac = Math.abs(dx * uy - dy * ux);
        if (al > 4 && al <= 7.7 && ac <= 1.7) dent = true;
      }
      if (r <= 1.5) return r < 0.5 ? '#FFF3B8' : '#D9A441';
      if (r <= 2.9) return '#06261F';
      if (r <= 6.1 || dent) return dx + dy < -3 ? '#C9FFF0' : dx + dy > 3 ? '#1F8C74' : '#4FD8B4';
      return r > 8.6 || (dx + dy > 0 && r > 7.9) ? '#06261F' : '#0B3B33';
    },
  },
};
for (const [k, u] of Object.entries(UNIVERS)) {
  if (u.px && (u.px.length !== 19 || u.px.some(r => r.length !== 19))) throw new Error('univers ' + k + ' : grille pas en 19x19');
}

function portal(img, px, py, u) {
  for (let dy = -15; dy <= 15; dy++) for (let dx = -15; dx <= 15; dx++) {
    const r = Math.hypot(dx, dy); if (r > 14.5) continue;
    const t = r ? (-dx * 0.7 - dy * 0.7) / r : 0; // lumière en haut à gauche
    let c;
    if (r <= 9.5) { c = u.px ? u.pal[u.px[dy + 9][dx + 9]] : null; if (!c) c = u.fond(dx, dy, r); }
    else if (r <= 10.5) c = t > -0.35 ? u.glow : u.col;
    else if (r <= 11.5) c = t > 0.35 ? G[4] : t > -0.35 ? G[3] : G[2];
    else if (r <= 12.5) c = t > 0.55 ? S[4] : t > 0 ? S[3] : t > -0.55 ? S[2] : S[1];
    else if (r <= 13.5) c = t > 0.55 ? S[6] : t > 0 ? S[5] : t > -0.55 ? S[3] : S[2];
    else c = NOIR;
    if (Math.abs(dx) === 9 && Math.abs(dy) === 9) c = t > 0 ? G[5] : G[3]; // rivets
    img.set(px + dx, py + dy, c);
  }
}

// ---------------------------------------------------------------- la Station : sphère + anneau incliné
const U = [0.8, -0.6], Wd = [0.6, 0.8], K = 0.3;      // grand axe, petit axe, écrasement de l'anneau
const V3 = [Wd[0] * K, Wd[1] * K, Math.sqrt(1 - K * K)];
const N3 = [U[1] * V3[2], -U[0] * V3[2], U[0] * V3[1] - U[1] * V3[0]]; // pôle nord (vers le haut à gauche)
const LUM = (() => { const v = [-0.42, -0.52, 0.74], l = Math.hypot(...v); return v.map(a => a / l); })();
const hash = n => { let t = Math.imul(n ^ 0x9E3779B9, 0x85EBCA6B); t ^= t >>> 13; t = Math.imul(t, 0xC2B2AE35); return (t ^ t >>> 16) >>> 0; };

function sphere(w, h, cx, cy, R) {
  const L = new Img(w, h);
  // latitude / longitude de chaque pixel, pour découper la coque en plaques nettes
  const info = (dx, dy) => {
    const r = Math.hypot(dx, dy); if (r > R + 0.3) return null;
    const rr = Math.min(r, R), P = [dx / R, dy / R, Math.sqrt(R * R - rr * rr) / R];
    const pl = Math.hypot(...P); P[0] /= pl; P[1] /= pl; P[2] /= pl;
    const lat = deg(Math.asin(clamp(P[0] * N3[0] + P[1] * N3[1] + P[2] * N3[2], -1, 1)));
    const lon = deg(Math.atan2(P[0] * V3[0] + P[1] * V3[1] + P[2] * V3[2], P[0] * U[0] + P[1] * U[1])) + 180;
    const bande = [-58, -30, -3, 10, 34, 60].filter(b => lat > b).length;
    const per = [0, 90, 60, 0, 60, 90, 0][bande], dec = [0, 20, 40, 0, 10, 55, 0][bande];
    const cell = per ? Math.floor(mod(lon + dec, 360) / per) : 0;
    return { r, P, lat, lon, bande, id: bande * 100 + cell };
  };
  for (let dy = -R - 1; dy <= R + 1; dy++) for (let dx = -R - 1; dx <= R + 1; dx++) {
    const p = info(dx, dy); if (!p) continue;
    const d = p.P[0] * LUM[0] + p.P[1] * LUM[1] + p.P[2] * LUM[2];
    let idx = d > 0.95 ? 6 : d > 0.76 ? 5 : d > 0.46 ? 4 : d > 0.16 ? 3 : d > -0.2 ? 2 : 1;
    let c = null;
    const a = info(dx + 1, dy), b = info(dx, dy + 1);
    const joint = (a && a.id !== p.id) || (b && b.id !== p.id);
    if (p.bande === 3) { // tranchée de l'équateur, avec ses hublots éclairés
      idx = Math.max(1, idx - 1);
      if (p.lat > 1.5 && p.lat < 6.5) c = mod(p.lon, 14) < 7 ? (idx >= 2 ? G[4] : G[2]) : S[0];
    } else if (joint) idx = Math.max(1, idx - 1);
    else if (p.r > R - 1.2 && d > 0.35) idx = Math.min(6, idx + 1); // liseré clair du bord éclairé
    if (!c) c = S[idx];
    if (!c.startsWith('#F') && !c.startsWith('#A') && idx <= 2 && p.r > R - 1.3) c = '#2B5C8F'; // reflet bleu du bord
    L.set(cx + dx, cy + dy, c);
  }
  return L.outline(NOIR);
}

function ring(w, h, cx, cy, RI, RO) {
  const L = new Img(w, h);
  for (let dy = -RO - 2; dy <= RO + 2; dy++) for (let dx = -RO - 2; dx <= RO + 2; dx++) {
    const a = dx * U[0] + dy * U[1], b = dx * Wd[0] + dy * Wd[1], rho = Math.hypot(a, b / K);
    if (rho < RI || rho > RO) continue;
    const th = Math.atan2(b / K, a), spec = Math.cos(th - 2.3), devant = b > 0;
    let c;
    if (rho > RO - 1.4) c = devant ? (spec > 0.3 ? G[5] : G[4]) : G[2];
    else if (rho > RO - 2.6) c = devant ? G[3] : G[1];
    else {
      const t = (rho - RI) / (RO - 2.6 - RI);
      let i = t < 0.2 ? 1 : t < 0.45 ? 3 : t < 0.8 ? 4 : 2;
      if (i === 4 && spec > 0.8) i = 5;
      if (i === 4 && spec < -0.6) i = 3;
      if (!devant) i = Math.max(0, i - 1);
      c = I[i];
    }
    L.set(cx + dx, cy + dy, c);
  }
  const O = L.outline(NOIR);
  const dos = new Img(w, h), face = new Img(w, h);
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    const c = O.get(x, y); if (!c) continue;
    ((x - cx) * Wd[0] + (y - cy) * Wd[1] > 0 ? face : dos).set(x, y, c);
  }
  return { dos, face };
}

function dock(w, h, cx, cy, R) {
  const L = new Img(w, h);
  for (let dy = -R - 4; dy <= R + 4; dy++) for (let dx = -R - 4; dx <= R + 4; dx++) {
    const r = Math.hypot(dx, dy), e = r - R; if (e < -2 || e >= 2) continue;
    const t = (-dx * 0.65 - dy * 0.76) / r, joint = mod(deg(Math.atan2(dy, dx)) + 3.75, 15) < 1.3;
    let c;
    if (e >= 1) c = t > 0.3 ? S[6] : t > -0.3 ? S[4] : S[3];
    else if (e >= 0) c = joint ? S[1] : t > 0.3 ? S[4] : t > -0.3 ? S[3] : S[2];
    else if (e >= -1) c = t > 0.3 ? G[4] : t > -0.3 ? G[3] : G[2];
    else c = joint ? S[0] : t > 0.3 ? S[2] : S[1];
    L.set(cx + dx, cy + dy, c);
  }
  return L.outline(NOIR);
}

function beam(img, cx, cy, tx, ty, u, r0, r1) {
  const len = Math.hypot(tx, ty), ux = tx / len, uy = ty / len;
  for (let dy = -r1 - 3; dy <= r1 + 3; dy++) for (let dx = -r1 - 3; dx <= r1 + 3; dx++) {
    const along = dx * ux + dy * uy; if (along < r0 || along > r1) continue;
    const d = Math.abs(dx * uy - dy * ux);
    if (d < 0.6) img.set(cx + dx, cy + dy, '#FFFFFF');
    else if (d < 1.6) img.set(cx + dx, cy + dy, u.glow);
    else if (d < 2.6) img.set(cx + dx, cy + dy, u.col);
    else if (d < 3.6) img.set(cx + dx, cy + dy, u.dark);
  }
}

// positions des portails : pentes propres (vertical, 3:1, 2:3) pour des rayons sans escalier irrégulier
const PORTS = [
  ['tycoon', 0, -47], ['survie', 45, -15], ['extraction', 26, 39], ['practice', -26, 39], ['mmo', -45, -15]];

function emblem(img, cx, cy) {
  const w = img.w, h = img.h;
  sparkle(img, cx + 22, cy - 32, true); sparkle(img, cx - 23, cy - 31, false);
  sparkle(img, cx, cy + 39, false); sparkle(img, cx - 37, cy + 11, true); sparkle(img, cx + 38, cy + 13, false);
  img.blit(dock(w, h, cx, cy, 47));
  const an = ring(w, h, cx, cy, 27, 35);
  img.blit(an.dos);
  for (const [k, x, y] of PORTS) beam(img, cx, cy, x, y, UNIVERS[k], 16, 38);
  img.blit(sphere(w, h, cx, cy, 21));
  img.blit(an.face);
  for (const [k, x, y] of PORTS) portal(img, cx + x, cy + y, UNIVERS[k]);
}

// ---------------------------------------------------------------- icône carrée
function icone() {
  const img = background(128, 128, [[64, 64, 72, 1.0]], 4242);
  emblem(img, 64, 64);
  return img;
}

// ---------------------------------------------------------------- lettrage « ORBIT » + plaque « MC »
const oct = (x0, y0, x1, y1, c) => {
  const [tl, tr, br, bl] = Array.isArray(c) ? c : [c, c, c, c];
  return [[x0 + tl, y0], [x1 - tr, y0], [x1, y0 + tr], [x1, y1 - br], [x1 - br, y1], [x0 + bl, y1], [x0, y1 - bl], [x0, y0 + tl]];
};
const LETTRES = {
  O: { w: 30, ops: [['+', oct(0, 0, 30, 44, 6)], ['-', oct(10, 10, 20, 34, 2)]] },
  R: { w: 30, ops: [['+', oct(0, 0, 10, 44, [3, 0, 0, 2])], ['+', oct(0, 0, 30, 26, [3, 6, 6, 0])], ['-', oct(10, 10, 20, 16, [1, 2, 2, 1])], ['+', [[10, 24], [20, 24], [30, 44], [19, 44], [10, 26]]]] },
  B: { w: 30, ops: [['+', oct(0, 0, 10, 44, [3, 0, 0, 3])], ['+', oct(0, 0, 28, 24, [3, 6, 4, 0])], ['+', oct(0, 20, 30, 44, [0, 4, 6, 3])], ['-', oct(10, 10, 18, 17, [1, 2, 2, 1])], ['-', oct(10, 27, 20, 34, [1, 2, 2, 1])]] },
  I: { w: 12, ops: [['+', oct(0, 0, 12, 44, 2)]] },
  T: { w: 30, ops: [['+', oct(0, 0, 30, 11, [3, 3, 2, 2])], ['+', oct(10, 0, 20, 44, [0, 0, 2, 2])]] },
};
function fillPoly(m, w, pts, ox, oy, v) {
  const ys = pts.map(p => p[1]), xs = pts.map(p => p[0]);
  for (let y = Math.floor(Math.min(...ys)); y < Math.ceil(Math.max(...ys)); y++)
    for (let x = Math.floor(Math.min(...xs)); x < Math.ceil(Math.max(...xs)); x++) {
      const px = x + 0.5, py = y + 0.5; let inside = false;
      for (let i = 0, j = pts.length - 1; i < pts.length; j = i++) {
        const [xi, yi] = pts[i], [xj, yj] = pts[j];
        if ((yi > py) !== (yj > py) && px < (xj - xi) * (py - yi) / (yj - yi) + xi) inside = !inside;
      }
      if (inside) m[(y + oy) * w + x + ox] = v;
    }
}
// forme en relief : liseré or biseauté de 2 px, face au choix, épaisseur vers le bas, contour noir
function relief(img, m, face, epais) {
  const w = img.w, h = img.h, L = new Img(w, h), inM = (x, y) => x >= 0 && y >= 0 && x < w && y < h && m[y * w + x];
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    if (!inM(x, y)) continue;
    for (let k = 1; k <= epais; k++) if (!inM(x, y + k)) L.set(x, y + k, k <= epais / 2 ? '#22314F' : '#131C33');
  }
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    if (!inM(x, y)) continue;
    let d = 9, nx = 0, ny = 0;
    for (let oy = -3; oy <= 3; oy++) for (let ox = -3; ox <= 3; ox++) {
      if (inM(x + ox, y + oy)) continue;
      d = Math.min(d, Math.max(Math.abs(ox), Math.abs(oy))); nx += ox; ny += oy;
    }
    const l = Math.hypot(nx, ny) || 1, lit = (-0.6 * nx - 0.8 * ny) / l;
    let c;
    if (d === 1) c = lit > 0.3 ? G[4] : lit < -0.3 ? G[1] : G[3];
    else if (d === 2) c = lit > 0.3 ? G[5] : lit < -0.3 ? G[2] : G[3];
    else c = face(x, y, d === 3 ? (lit > 0.3 ? -1 : lit < -0.3 ? 1 : 0) : 0);
    L.set(x, y, c);
  }
  img.blit(L.outline(NOIR, true));
}
function chrome(y0) { // acier chromé : ciel clair en haut, ligne d'horizon nette, sol sombre qui remonte
  return (x, y, bord) => {
    const yl = y - y0, dm = (x + y) & 1;
    let i;
    if (yl < 8) i = 4; else if (yl === 8) i = dm ? 5 : 4;
    else if (yl < 14) i = 5; else if (yl === 14) i = dm ? 6 : 5;
    else if (yl < 21) i = 6;
    else if (yl < 27) i = 2; else if (yl === 27) i = dm ? 3 : 2;
    else if (yl < 34) i = 3; else if (yl === 34) i = dm ? 4 : 3;
    else i = 4;
    return S[clamp(i + bord, 1, 6)];
  };
}
const GLYPHES = {
  M: ['##.......##', '###.....###', '####...####', '##.##.##.##', '##..###..##', '##...#...##', '##.......##', '##.......##', '##.......##', '##.......##'],
  C: ['.#######.', '#########', '##.....##', '##.......', '##.......', '##.......', '##.......', '##.....##', '#########', '.#######.'],
};
function texte(img, str, x, y) {
  let cx = x;
  for (const ch of str) {
    const g = GLYPHES[ch];
    for (let pass = 0; pass < 2; pass++) g.forEach((row, j) => [...row].forEach((p, i) => {
      if (p !== '#') return;
      if (pass === 0) img.set(cx + i + 1, y + j + 1, '#060912');
      else img.set(cx + i, y + j, j < 3 ? '#FFFFFF' : j < 6 ? I[4] : I[3]);
    }));
    cx += g[0].length + 3;
  }
}
function gemme(img, x, y, u) {
  for (let dy = -3; dy <= 3; dy++) for (let dx = -3; dx <= 3; dx++) {
    const m = Math.abs(dx) + Math.abs(dy); if (m > 3) continue;
    img.set(x + dx, y + dy, m === 3 ? NOIR : dx + dy < 0 ? u.glow : dx + dy === 0 ? u.col : u.dark);
  }
}

function banniere() {
  const W = 320, H = 180;
  const img = background(W, H, [[74, 90, 98, 1.0], [226, 84, 130, 0.5]], 9191);
  sparkle(img, 160, 22, true); sparkle(img, 289, 150, true); sparkle(img, 247, 28, false); sparkle(img, 178, 152, false); sparkle(img, 304, 40, false);
  emblem(img, 74, 90);
  // ORBIT
  const m = new Uint8Array(W * H), ox = 150, oy = 44; let x = ox;
  for (const ch of 'ORBIT') {
    const L = LETTRES[ch];
    for (const [op, pts] of L.ops) fillPoly(m, W, pts, x, oy, op === '+' ? 1 : 0);
    x += L.w + 5;
  }
  relief(img, m, chrome(oy), 4);
  // filets or de part et d'autre de la plaque
  for (const [x0, x1] of [[150, 198], [254, 302]]) for (let px = x0; px < x1; px++) {
    img.set(px, 108, G[4]); img.set(px, 109, G[2]); img.set(px, 107, NOIR); img.set(px, 110, NOIR);
  }
  // plaque MC
  const p = new Uint8Array(W * H);
  fillPoly(p, W, oct(202, 99, 250, 119, 4), 0, 0, 1);
  relief(img, p, (px, py) => py < 109 ? '#22386A' : '#182850', 2);
  texte(img, 'MC', 214, 104);
  // les 5 modes, en gemmes
  ['survie', 'extraction', 'practice', 'mmo', 'tycoon'].forEach((k, i) => gemme(img, 226 + (i - 2) * 14, 134, UNIVERS[k]));
  return img;
}

if (require.main === module) {
  const ic = icone();
  savePng(ic, 'discord-icone-pixel-128.png', 1);
  savePng(ic, 'discord-icone-pixel-512.png', 4);
  savePng(ic, 'discord-icone-pixel-1024.png', 8);
  const ba = banniere();
  savePng(ba, 'discord-banniere-pixel-320.png', 1);
  savePng(ba, 'discord-banniere-pixel-960.png', 3);
  savePng(ba, 'discord-banniere-pixel-1920.png', 6);
  console.log('ok');
}
