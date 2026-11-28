// Génère les textures 16x16 du Trieur ORBIT (bleu + or), sans dépendance.
// Usage : node outils/textures.mjs   (depuis mods-src/OrbitTrieur)
import { deflateSync } from 'node:zlib';
import { writeFileSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const racine = join(dirname(fileURLToPath(import.meta.url)), '..');
const sortie = join(racine, 'src/main/resources/assets/orbittrieur/textures/block');
mkdirSync(sortie, { recursive: true });

const C = {
  nuit: [11, 26, 51], bleu: [24, 52, 98], bleuC: [46, 107, 214], acier: [33, 66, 120],
  ombre: [7, 16, 33], or: [242, 193, 78], orS: [176, 128, 36], orC: [255, 226, 140],
  cyan: [120, 220, 255], trou: [4, 9, 18],
};

// Petit bruit déterministe pour donner du grain au métal.
function bruit(x, y, graine) {
  let h = (x * 374761393 + y * 668265263 + graine * 982451653) | 0;
  h = Math.imul(h ^ (h >>> 13), 1274126177);
  return ((h ^ (h >>> 16)) & 255) / 255;
}
const teinte = (c, k) => c.map(v => Math.max(0, Math.min(255, Math.round(v * k))));

function image(dessin, graine) {
  const px = [];
  for (let y = 0; y < 16; y++) {
    px.push([]);
    for (let x = 0; x < 16; x++) {
      px[y].push(teinte(C.bleu, 0.9 + bruit(x, y, graine) * 0.2));
    }
  }
  const set = (x, y, c) => { if (x >= 0 && x < 16 && y >= 0 && y < 16) px[y][x] = c; };
  dessin(set, px);
  return px;
}

// Cadre commun : liseré or en haut et en bas, bords acier, rivets aux coins.
function cadre(set) {
  for (let i = 0; i < 16; i++) {
    set(i, 0, C.or); set(i, 15, C.orS);
    set(0, i, C.acier); set(15, i, C.ombre);
  }
  set(0, 0, C.orC); set(15, 0, C.or); set(0, 15, C.orS); set(15, 15, C.ombre);
  for (const [x, y] of [[2, 2], [13, 2], [2, 13], [13, 13]]) { set(x, y, C.orC); set(x + 1, y + 1, C.orS); }
}

const textures = {
  // Côté : panneau avec trois fentes d'aération et une bande bleu clair.
  trieur_cote: image(set => {
    cadre(set);
    for (const y of [5, 8, 11]) for (let x = 4; x <= 11; x++) { set(x, y, C.trou); set(x, y + 1, C.acier); }
    for (let y = 1; y < 15; y++) set(1, y, C.bleuC);
  }, 1),

  // Face : une flèche d'or qui se sépare en trois (le tri), lueur cyan au centre.
  trieur_face: image(set => {
    cadre(set);
    for (let y = 3; y <= 12; y++) for (let x = 3; x <= 12; x++) set(x, y, C.nuit);
    for (let x = 3; x <= 7; x++) set(x, 8, C.or);            // tronc
    set(7, 7, C.orC); set(7, 9, C.orC);
    for (let i = 0; i <= 4; i++) { set(8 + i, 7 - i, C.or); set(8 + i, 9 + i, C.or); } // branches
    for (let x = 8; x <= 12; x++) set(x, 8, C.or);           // branche du milieu
    for (const [x, y] of [[12, 3], [12, 8], [12, 13]]) { set(x, y, C.orC); }
    set(6, 8, C.cyan); set(5, 8, C.orC);
  }, 2),

  // Dessus : ouverture d'entrée (grille sombre), cadre or.
  trieur_dessus: image(set => {
    cadre(set);
    for (let y = 3; y <= 12; y++) for (let x = 3; x <= 12; x++) set(x, y, (x + y) % 3 === 0 ? C.acier : C.trou);
    for (let i = 3; i <= 12; i++) { set(i, 3, C.orS); set(i, 12, C.orS); set(3, i, C.orS); set(12, i, C.orS); }
    set(7, 7, C.cyan); set(8, 8, C.cyan);
  }, 3),

  // Dessous : plaque pleine.
  trieur_dessous: image(set => {
    cadre(set);
    for (let x = 3; x <= 12; x++) { set(x, 7, C.acier); set(x, 8, C.ombre); }
  }, 4),
};

// Encodeur PNG minimal (RGBA 8 bits).
const crcTable = new Uint32Array(256).map((_, n) => {
  let c = n;
  for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
  return c >>> 0;
});
function crc32(buf) {
  let c = 0xffffffff;
  for (const b of buf) c = crcTable[(c ^ b) & 255] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}
function bloc(type, data) {
  const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
  const td = Buffer.concat([Buffer.from(type, 'ascii'), data]);
  const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(td));
  return Buffer.concat([len, td, crc]);
}
function png(px) {
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(16, 0); ihdr.writeUInt32BE(16, 4);
  ihdr[8] = 8; ihdr[9] = 6; ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0;
  const brut = Buffer.alloc(16 * (1 + 16 * 4));
  let o = 0;
  for (let y = 0; y < 16; y++) {
    brut[o++] = 0;
    for (let x = 0; x < 16; x++) { const [r, g, b] = px[y][x]; brut[o++] = r; brut[o++] = g; brut[o++] = b; brut[o++] = 255; }
  }
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), bloc('IHDR', ihdr),
    bloc('IDAT', deflateSync(brut)), bloc('IEND', Buffer.alloc(0))]);
}

for (const [nom, px] of Object.entries(textures)) {
  writeFileSync(join(sortie, nom + '.png'), png(px));
  console.log('écrit', nom + '.png');
}
