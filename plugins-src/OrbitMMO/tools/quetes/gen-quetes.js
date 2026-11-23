// Générateur des fichiers de quêtes d'OrbitMMO à partir de descriptions compactes (tools\quetes\zones\*.js).
// Usage : node tools\quetes\gen-quetes.js [--installer]
//   écrit src\main\resources\quests\<fichier>.yml pour chaque zone ;
//   --installer : copie aussi les .yml dans instances\reborn\plugins\OrbitMMO\quests et ajoute les points
//                 manquants dans quest-points.yml (un point déjà posé n'est jamais déplacé).
//
// Format d'une zone : voir zones\ombre-balafre.js. Textes en paires [français, anglais].
// Répliques : [orateur, français, anglais] ; orateur = id de PNJ, 'j' (joueur) ou '-' (narration).
// Étapes :
//   ['P', pnj, texte, [répliques]]                 parler
//   ['A', point, rayon, texte, [répliques après]]  atteindre
//   ['I', point, rayon, texte, [répliques après]]  interagir
//   ['K', [monstres], nombre, point|null, texte, [après]]               tuer
//   ['R', [monstres], nombre, chance, point|null, texte, objet]         ramasser
//   ['S', [monstres], texte, [après]]               lancer un sort
//   ['N', point, texte, [après]]                    Éclat d'ancrage
const fs = require('fs'), path = require('path');
const ROOT = path.resolve(__dirname, '..', '..');
const OUT = path.join(ROOT, 'src', 'main', 'resources', 'quests');
const INST = path.resolve(ROOT, '..', '..', 'instances', 'reborn', 'plugins', 'OrbitMMO');
const install = process.argv.includes('--installer');

const q = s => JSON.stringify(String(s));
const lines = [];
const out = (ind, s) => lines.push(' '.repeat(ind) + s);
function pair(ind, key, p) {
  if (p == null) return;
  if (!Array.isArray(p)) { out(ind, `${key}: ${q(p)}`); return; }
  out(ind, `${key}: ${q(p[0])}`);
  if (p[1] != null && p[1] !== p[0]) out(ind, `${key}-en: ${q(p[1])}`);
}
function list(ind, key, arr) { // arr de paires [fr, en]
  if (!arr || !arr.length) return;
  out(ind, key + ':'); for (const a of arr) out(ind + 2, '- ' + q(a[0]));
  if (arr.every(a => a[1] != null)) { out(ind, key + '-en:'); for (const a of arr) out(ind + 2, '- ' + q(a[1])); }
}
function talk(ind, key, arr) { // répliques [orateur, fr, en]
  if (arr) for (const r of arr) if (r[2] == null) console.warn("  ANGLAIS MANQUANT (" + key + ") : " + r[1].slice(0, 60));
  if (!arr || !arr.length) return;
  const fmt = (who, t) => who === '-' ? t : `${who === 'j' ? 'joueur' : who}: ${t}`;
  list(ind, key, arr.map(r => [fmt(r[0], r[1]), r[2] == null ? null : fmt(r[0], r[2])]));
}

const points = {};
const zonesDir = path.join(__dirname, 'zones');
for (const f of fs.readdirSync(zonesDir).filter(n => n.endsWith('.js')).sort()) {
  const z = require(path.join(zonesDir, f));
  lines.length = 0;
  out(0, '# ------------------------------------------------------------------');
  for (const h of z.entete) out(0, '#  ' + h);
  out(0, '#  FICHIER GÉNÉRÉ par tools\\quetes\\gen-quetes.js depuis tools\\quetes\\zones\\' + f + ' : modifier la source, pas ce fichier.');
  out(0, '# ------------------------------------------------------------------');
  out(0, '');
  if (z.pnj && Object.keys(z.pnj).length) {
    out(0, 'pnj:');
    for (const [id, n] of Object.entries(z.pnj)) {
      out(2, id + ':');
      pair(4, 'nom', n.n); pair(4, 'titre', n.t);
      out(4, `couleur: ${q(n.c || '#D7DCE2')}`);
      if (!n.sansCorps) out(4, 'type: MANNEQUIN');
      if (n.v) out(4, 'visage: ' + n.v);
      out(4, 'point: ' + (n.p || id));
      if (n.m) out(4, 'main: ' + n.m);
      list(4, 'repliques', n.r);
    }
    out(0, '');
  }
  if (z.monstres && Object.keys(z.monstres).length) {
    out(0, 'monstres:');
    for (const [id, m] of Object.entries(z.monstres)) {
      out(2, id + ':');
      for (const [k, v] of Object.entries(m)) out(4, `${k}: ${typeof v === 'string' && k === 'nom' ? q(v) : v}`);
    }
    out(0, '');
  }
  if (z.apparitions && Object.keys(z.apparitions).length) {
    out(0, 'apparitions:');
    for (const [id, a] of Object.entries(z.apparitions)) {
      const [pt, mob, lv, nb, r] = a;
      out(2, id + ':'); out(4, 'point: ' + pt); out(4, 'monstre: ' + mob);
      out(4, 'niveau: ' + (Array.isArray(lv) ? `[${lv.join(', ')}]` : lv)); out(4, 'nombre: ' + nb); out(4, 'rayon: ' + r);
    }
    out(0, '');
  }
  out(0, 'quetes:');
  for (const Q of z.quetes) {
    if (Q.titre) { out(0, ''); out(2, '# ================================================================ ' + Q.titre); continue; }
    out(0, '');
    out(2, Q.id + ':');
    pair(4, 'nom', Q.n);
    const fac = Q.f !== undefined ? Q.f : z.faction;
    if (fac) out(4, 'faction: ' + fac);
    out(4, 'niveau: ' + Q.lv);
    if (Q.req && Q.req.length) out(4, `requiert: [${Q.req.join(', ')}]`);
    if (Q.g) out(4, 'donneur: ' + Q.g);
    if (Q.dep) out(4, 'depart: ' + Q.dep);
    list(4, 'resume', Q.res);
    talk(4, 'offre', Q.off);
    talk(4, 'intro', Q.intro);
    out(4, 'etapes:');
    for (const s of Q.s) {
      const T = { P: 'PARLER', A: 'ATTEINDRE', I: 'INTERAGIR', K: 'TUER', R: 'RAMASSER', S: 'SORT', N: 'ANCRAGE' }[s[0]];
      if (!T) throw new Error(`${f} ${Q.id} : étape inconnue ${s[0]}`);
      out(6, '- type: ' + T);
      const e = (k, v) => out(8, `${k}: ${v}`);
      let texte, apres, dial;
      switch (s[0]) {
        case 'P': e('pnj', s[1]); texte = s[2]; dial = s[3]; break;
        case 'A': case 'I': e('point', s[1]); e('rayon', s[2]); texte = s[3]; apres = s[4]; break;
        case 'K': e('monstres', `[${s[1].join(', ')}]`); if (s[2] > 1) e('nombre', s[2]); if (s[3]) e('point', s[3]); texte = s[4]; apres = s[5]; break;
        case 'R': e('monstres', `[${s[1].join(', ')}]`); e('nombre', s[2]); e('chance', s[3]); if (s[4]) e('point', s[4]); texte = s[5]; break;
        case 'S': e('monstres', `[${s[1].join(', ')}]`); texte = s[2]; apres = s[3]; break;
        case 'N': e('point', s[1]); e('rayon', 4); texte = s[2]; apres = s[3]; break;
      }
      // texte en premier pour la lisibilité : on le remonte juste après « type »
      const typeLine = lines.lastIndexOf('      - type: ' + T);
      const tl = [`        texte: ${q(texte[0])}`]; if (texte[1]) tl.push(`        texte-en: ${q(texte[1])}`);
      lines.splice(typeLine + 1, 0, ...tl);
      if (s[0] === 'R') { pair(8, 'objet', s[6]); }
      talk(8, 'dialogue', dial);
      talk(8, 'apres', apres);
    }
    out(4, 'recompenses:');
    out(6, 'xp: ' + Q.xp); out(6, 'or: ' + Q.or);
    if (Q.items) {
      out(6, 'objets:');
      for (const it of Q.items) {
        out(8, '- materiau: ' + it.m);
        pair(10, 'nom', it.n);
        list(10, 'description', it.d);
      }
    }
  }
  const file = z.fichier + '.yml';
  fs.writeFileSync(path.join(OUT, file), lines.join('\n') + '\n', 'utf8');
  console.log('écrit', file, '—', z.quetes.filter(x => !x.titre).length, 'quêtes');
  Object.assign(points, z.points || {});
  if (install) fs.copyFileSync(path.join(OUT, file), path.join(INST, 'quests', file));
}

if (install) {
  const pf = path.join(INST, 'quest-points.yml');
  const txt = fs.readFileSync(pf, 'utf8');
  const have = new Set(txt.split(/\r?\n/).map(l => (l.match(/^([A-Za-z0-9_\-]+):/) || [])[1]).filter(Boolean));
  const add = [];
  for (const [n, p] of Object.entries(points)) {
    if (have.has(n)) continue;
    add.push(`${n}: {monde: orbit_continent, x: ${p[0]}, y: ${p[1]}, z: ${p[2]}, yaw: ${p[3] || 0}, pitch: 0.0}`);
  }
  if (add.length) fs.writeFileSync(pf, txt.replace(/\s*$/, '\n') + add.join('\n') + '\n', 'utf8');
  console.log(add.length, 'point(s) ajouté(s) à quest-points.yml');
}
