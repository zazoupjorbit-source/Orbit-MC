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
// Types du 23/11 (détail et exemples : docs\quetes-types.md). o = objet de réglages { clé: valeur } :
//   ['E', pnj, [chemin], texte, o, [après]]          escorte    o : depart, pv, monstres, nombre, vagues, vitesse, degats, niveau
//   ['D', point, texte, o, [après]]                  défense    o : cible [fr,en], pv, monstres, nombre, vagues | duree,
//                                                                    intervalle, apparition, rayon, degats, niveau
//   ['Q', pnj, choix, texte, [dialogue], question, [[id, texte, [réponse], {xp, or}], …]]   dialogue à choix
//   ['L', pnj, objet, materiau, texte, [dialogue]]   livraison (objet remis au début de l'étape)
//   ['X', [[point, texte, [après]], …], pnj|null, texte, [dialogue de conclusion]]          enquête
//   ['F', point, depart|null, [gardes], texte, o, [après]]   infiltration   o : vue, rayon
//   ['T', [traces], texte, [après]]                  pistage (la dernière trace = la cible)
//   ['G', point, [monstres], tenue, materiau, couleur|null, texte, [après]]   déguisement
//   ['V', point, rayon, duree, texte, o, [après]]    survie     o : mode ('resserre'|'brule'), rayonFin, degats,
//                                                                    monstres, nombre, intervalle
//   ['M', point, objet, depart|null, texte, o, [après]]       transport  o : materiau, rayon
//   ['C', secondes, étape, [échec], reprise]         contre la montre autour de n'importe quelle étape
//                                                    (reprise : n° d'étape où l'on revient, sinon la même)
// Toute étape peut finir par un objet { si: 'choix=réponse', chrono, echec: [répliques], reprise } (branches, chrono).
// Une quête peut demander une réponse : req: ['quete-avant', 'choix:pacte=accepter'] ('!choix:…' = toute autre).
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

const TYPES = {
  P: 'PARLER', A: 'ATTEINDRE', I: 'INTERAGIR', K: 'TUER', R: 'RAMASSER', S: 'SORT', N: 'ANCRAGE',
  E: 'ESCORTE', D: 'DEFENSE', Q: 'CHOIX', L: 'LIVRER', X: 'ENQUETE', F: 'INFILTRER', T: 'PISTER',
  G: 'DEGUISEMENT', V: 'SURVIE', M: 'TRANSPORT',
};
const isObj = v => v != null && typeof v === 'object' && !Array.isArray(v);
const ids = a => `[${a.join(', ')}]`;

// Une étape. extra : réglages venus d'un 'C' (contre la montre) qui l'entoure.
function writeStep(f, Q, s0, extra) {
  if (s0[0] === 'C') {
    const [, sec, inner, echec, reprise] = s0;
    return writeStep(f, Q, inner, Object.assign({}, extra, { chrono: sec, echec, reprise }));
  }
  const s = s0.slice();
  // Objet de réglages communs en dernier (si, chrono, echec, reprise).
  const tail = s.length > 1 && isObj(s[s.length - 1]) && !['E', 'D', 'F', 'V', 'M'].includes(s[0]) ? s.pop() : {};
  const T = TYPES[s[0]];
  if (!T) throw new Error(`${f} ${Q.id} : étape inconnue ${s[0]}`);
  out(6, '- type: ' + T);
  const e = (k, v) => { if (v !== undefined && v !== null) out(8, `${k}: ${v}`); };
  const nb = (k, v) => { if (typeof v === 'number') e(k, v); };
  let texte, apres, dial, o = {};
  switch (s[0]) {
    case 'P': e('pnj', s[1]); texte = s[2]; dial = s[3]; break;
    case 'A': case 'I': e('point', s[1]); e('rayon', s[2]); texte = s[3]; apres = s[4]; break;
    case 'K': e('monstres', ids(s[1])); if (s[2] > 1) e('nombre', s[2]); if (s[3]) e('point', s[3]); texte = s[4]; apres = s[5]; break;
    case 'R': e('monstres', ids(s[1])); e('nombre', s[2]); e('chance', s[3]); if (s[4]) e('point', s[4]); texte = s[5]; break;
    case 'S': e('monstres', ids(s[1])); texte = s[2]; apres = s[3]; break;
    case 'N': e('point', s[1]); e('rayon', 4); texte = s[2]; apres = s[3]; break;
    case 'E':
      o = s[4] || {}; e('pnj', s[1]); e('chemin', ids(s[2])); e('depart', o.depart);
      if (o.monstres) e('monstres', ids(o.monstres));
      nb('nombre', o.nombre); nb('vagues', o.vagues); nb('pv', o.pv); nb('vitesse', o.vitesse); nb('degats', o.degats); nb('niveau', o.niveau);
      texte = s[3]; apres = s[5]; break;
    case 'D':
      o = s[3] || {}; e('point', s[1]); nb('rayon', o.rayon);
      if (o.monstres) e('monstres', ids(o.monstres));
      nb('nombre', o.nombre); nb('vagues', o.vagues); nb('duree', o.duree); nb('intervalle', o.intervalle);
      e('apparition', o.apparition); nb('pv', o.pv); nb('degats', o.degats); nb('niveau', o.niveau);
      pair(8, 'cible', o.cible); texte = s[2]; apres = s[4]; break;
    case 'Q':
      e('pnj', s[1]); e('choix', s[2]); texte = s[3]; dial = s[4]; pair(8, 'question', s[5]);
      out(8, 'reponses:');
      for (const r of s[6]) {
        out(10, '- id: ' + r[0]);
        pair(12, 'texte', r[1]);
        talk(12, 'reponse', r[2]);
        if (r[3] && r[3].xp) out(12, 'xp: ' + r[3].xp);
        if (r[3] && r[3].or) out(12, 'or: ' + r[3].or);
      }
      break;
    case 'L': e('pnj', s[1]); texte = s[4]; dial = s[5]; e('materiau', s[3]); break;
    case 'X':
      if (s[2]) e('pnj', s[2]);
      out(8, 'indices:');
      for (const c of s[1]) {
        out(10, '- point: ' + c[0]);
        pair(12, 'texte', c[1]);
        talk(12, 'apres', c[2]);
      }
      texte = s[3]; dial = s[4]; break;
    case 'F':
      o = s[5] || {}; e('point', s[1]); e('depart', s[2]); e('monstres', ids(s[3])); nb('vue', o.vue); nb('rayon', o.rayon);
      texte = s[4]; apres = s[6]; break;
    case 'T': e('traces', ids(s[1])); texte = s[2]; apres = s[3]; break;
    case 'G':
      e('point', s[1]); e('monstres', ids(s[2])); e('materiau', s[4]); if (s[5]) e('couleur', q(s[5]));
      texte = s[6]; apres = s[7]; break;
    case 'V':
      o = s[5] || {}; e('point', s[1]); e('rayon', s[2]); e('duree', s[3]); e('mode', o.mode);
      nb('rayon-fin', o.rayonFin); nb('degats', o.degats);
      if (o.monstres) e('monstres', ids(o.monstres));
      nb('nombre', o.nombre); nb('intervalle', o.intervalle); nb('niveau', o.niveau);
      texte = s[4]; apres = s[6]; break;
    case 'M':
      o = s[5] || {}; e('point', s[1]); e('depart', s[3]); e('materiau', o.materiau); nb('rayon', o.rayon);
      texte = s[4]; apres = s[6]; break;
  }
  // texte en premier pour la lisibilité : on le remonte juste après « type »
  const typeLine = lines.lastIndexOf('      - type: ' + T);
  const tl = [`        texte: ${q(texte[0])}`]; if (texte[1]) tl.push(`        texte-en: ${q(texte[1])}`);
  lines.splice(typeLine + 1, 0, ...tl);
  if (s[0] === 'R') { pair(8, 'objet', s[6]); }
  if (s[0] === 'L') { pair(8, 'objet', s[2]); }
  if (s[0] === 'G') { pair(8, 'objet', s[3]); }
  if (s[0] === 'M') { pair(8, 'objet', s[2]); }
  talk(8, 'dialogue', dial);
  talk(8, 'apres', apres);
  // Réglages communs : ceux de l'objet de la lettre, puis ceux de fin d'étape, puis ceux d'un 'C' autour.
  const g = Object.assign({}, o, tail, extra);
  if (g.si) e('si', q(g.si));
  if (g.chrono) e('chrono', g.chrono);
  if (g.reprise) e('reprise', g.reprise);
  talk(8, 'echec', g.echec);
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
  if (z.pointsRelatifs && Object.keys(z.pointsRelatifs).length) {
    // Posés d'un coup autour de l'admin par /quetes admin poser <fichier> (jamais par --installer).
    out(0, 'points-relatifs:');
    for (const [n, p] of Object.entries(z.pointsRelatifs)) out(2, `${n}: [${p[0]}, ${p[1]}, ${p[2]}]`);
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
    if (Q.admin || z.admin) out(4, 'admin: true');
    out(4, 'niveau: ' + Q.lv);
    if (Q.req && Q.req.length) out(4, `requiert: [${Q.req.map(r => /^[A-Za-z0-9_\-]+$/.test(r) ? r : q(r)).join(', ')}]`);
    if (Q.g) out(4, 'donneur: ' + Q.g);
    if (Q.dep) out(4, 'depart: ' + Q.dep);
    list(4, 'resume', Q.res);
    talk(4, 'offre', Q.off);
    talk(4, 'intro', Q.intro);
    out(4, 'etapes:');
    for (const s of Q.s) writeStep(f, Q, s, {});
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
