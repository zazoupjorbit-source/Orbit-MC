const fs = require('fs');
const { PNJ, MON, Q, FILEQ, CHOIX } = require('./data');
const esc = s => String(s == null ? '' : s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/\{joueur\}/g, '[pseudo du joueur]');
const CH = [
  ['c1l', 'Acte I — Lumière', "Le Val d'Ardence", '1-9', ['lumiere-depart', 'lumiere-val-ardence'], 'lum'],
  ['c1o', 'Acte I — Ombre', 'La Balafre', '1-9', ['ombre-balafre'], 'omb'],
  ['c2l', 'Acte II — Lumière', "Le Bois de l'Aube", '9-16', ['lumiere-bois-aube'], 'lum'],
  ['c2o', 'Acte II — Ombre', 'La Sylve Noire', '9-16', ['ombre-sylve-noire'], 'omb'],
  ['c3l', 'Acte III — Lumière', 'Les Collines du Ponant', '16-25', ['lumiere-ponant'], 'lum'],
  ['c3o', 'Acte III — Ombre', 'Le Marais des Murmures', '16-25', ['ombre-marais'], 'omb'],
  ['c3m', "Fin de l'acte III", 'La Marche et les Quatre-Vents', '26-34', ['la-marche'], ''],
  ['c4', 'Acte IV', "Le Désert d'Ocre : « Le sable noir »", '34-48', ['acte4-desert'], ''],
  ['c5', 'Acte V', 'Les Cimes de Givre : « Ce que la neige a gardé »', '48-60', ['acte5-cimes'], ''],
  ['c6', 'Acte VI', 'Les Terres de Cendre : « La forge éteinte »', '60-72', ['acte6-cendres'], ''],
  ['c7', 'Acte VII', "La Fournaise : « Rassembler l'Astre »", '72-80', ['acte7-fournaise'], ''],
  ['c8', 'Acte VIII', 'Le Dernier Souffle : « Ce qui a froid »', '80-90', ['acte8-souffle'], ''],
  ['cm', 'Fil personnel', 'Le Marqué sans nom', '10-90', ['marque-sans-nom'], ''],
  ['cc', 'Contrats', 'Les quêtes répétables des hubs', '1-90', ['contrats'], ''],
];
const TYPES = { PARLER: 'Parler', ATTEINDRE: 'Aller', INTERAGIR: 'Interagir', TUER: 'Combattre', RAMASSER: 'Ramasser', SORT: 'Sort', ANCRAGE: "Éclat d'ancrage", ESCORTE: 'Escorte', DEFENSE: 'Défense', CHOIX: 'Choix', LIVRER: 'Livraison', ENQUETE: 'Enquête', INFILTRER: 'Infiltration', PISTER: 'Pistage', DEGUISEMENT: 'Déguisement', SURVIE: 'Survie', TRANSPORT: 'Transport' };
const FAC = { lumiere: ['Lumière', 'lum'], ombre: ['Ombre', 'omb'] };

function line(l) {
  const m = /^([a-z0-9_]+): ([\s\S]*)$/.exec(l);
  if (!m) return `<p class="nar">${esc(l)}</p>`;
  if (m[1] === 'joueur') return `<p class="rep j"><span class="who">Vous</span> ${esc(m[2])}</p>`;
  const p = PNJ[m[1]];
  return `<p class="rep"><span class="who">${esc(p ? p.nom : m[1])}</span> ${esc(m[2])}</p>`;
}
const lines = a => (a || []).map(line).join('');
function choixTexte(k, v) {
  const c = CHOIX[k]; const r = c && v && c.reponses.find(x => x.id === v);
  return r ? '« ' + r.texte + ' »' : (v || k);
}
function cond(si) {
  if (!si) return '';
  const neg = si.startsWith('!'); const [k, v] = si.replace(/^!/, '').split('=');
  return `<div class="si">${neg ? 'Sauf si' : 'Seulement si'} le joueur a répondu ${esc(choixTexte(k, v))}</div>`;
}
function req(q) {
  const r = (q.requiert || []).map(x => {
    if (/^!?choix:/.test(x)) {
      const neg = x[0] === '!'; const [k, v] = x.replace(/^!?choix:/, '').split('=');
      const c = CHOIX[k];
      return esc((neg ? 'pas la réponse ' : 'la réponse ') + (v ? choixTexte(k, v) : '') + ' à « ' + (c ? c.question : k) + ' »');
    }
    return Q[x] ? esc('« ' + Q[x].nom + ' »') : '<i>' + esc(x) + " (quête qui n'existe pas)</i>";
  });
  return r.length ? '<div class="req">Débloquée par : ' + r.join(', ') + '</div>' : '';
}
function etape(s) {
  let h = `<div class="et"><div class="et-t"><span class="ty">${TYPES[s.type] || s.type}</span> ${esc(s.texte)}`;
  if (s.monstres && ['TUER', 'RAMASSER', 'DEFENSE', 'ESCORTE', 'SURVIE'].includes(s.type))
    h += ` <span class="mon">(${s.monstres.map(m => esc(MON[m] ? MON[m].nom : m)).join(', ')}${s.nombre ? ' × ' + s.nombre : ''})</span>`;
  if (s.cible) h += ` <span class="mon">— à protéger : ${esc(Array.isArray(s.cible) ? s.cible[0] : s.cible)}</span>`;
  h += '</div>' + cond(s.si) + lines(s.dialogue);
  for (const ind of s.indices || []) h += `<div class="ind"><b>Indice : ${esc(ind.texte)}</b>${lines(ind.apres)}</div>`;
  if (s.reponses) {
    h += `<div class="ch"><div class="qst">${esc(s.question)}</div>`;
    for (const r of s.reponses) {
      const rw = [r.xp ? r.xp + ' XP' : '', r.or ? r.or + ' or' : ''].filter(Boolean).join(', ');
      h += `<div class="opt"><div class="opt-t">➤ ${esc(r.texte)}${rw ? ' <span class="rw">(' + rw + ')</span>' : ''}</div>${lines(r.reponse)}</div>`;
    }
    h += '</div>';
  }
  h += lines(s.apres);
  if (s.echec) h += "<div class=\"si\">En cas d'échec :</div>" + lines(s.echec);
  return h + '</div>';
}
function quete(q, num) {
  const f = FAC[q.faction];
  const don = q.donneur && PNJ[q.donneur] ? PNJ[q.donneur].nom : null;
  let h = `<div class="q ${f ? f[1] : 'neu'}" id="q-${q.id}"><div class="qh"><span class="qn">${num}.</span> ${esc(q.nom)}<span class="qm">${f ? f[0] : 'Les deux camps'} · niv. ${q.niveau}${q.repetable ? ' · répétable toutes les ' + q.repetable + ' h' : ''}${don ? ' · donneur : ' + esc(don) : ''}</span></div>`;
  h += req(q);
  if (q.resume) h += `<div class="res">${esc(q.resume.join(' '))}</div>`;
  h += lines(q.offre) + lines(q.intro);
  for (const s of q.etapes || []) h += etape(s);
  const r = q.recompenses || {};
  const rw = [r.xp ? r.xp + ' XP' : '', r.or ? r.or + ' or' : ''].filter(Boolean);
  if (rw.length) h += `<div class="rec">Récompense : ${rw.join(' · ')}</div>`;
  return h + '</div>';
}
const DEPART = ['l_eveil', 'ceux_qui_sont_tombes', 'ce_qui_rampe', 'poussiere_d_etoile', 'la_palissade', 'l_eclaireur_disparu', 'sous_les_murs_blancs', 'les_glaneurs', 'le_sanctuaire_effondre', 'le_serment_de_l_aube', 'la_route_de_l_aube', 'le_bois_qui_chante', 'les_feux_de_la_lisiere', 'la_liste', 'ce_que_gardent_les_morts', 'l_homme_de_la_colline', 'la_reine_tisseuse'];
function principal(id, f) {
  if (f === 'contrats' || f === 'marque-sans-nom') return true;
  if (DEPART.includes(id)) return true;
  if (/^a\d_[lofs]_/.test(id)) return true;
  if (/^(l|o)_(g|n|m|s|p)_/.test(id)) return false;
  return /^(l|o)_/.test(id);
}
let toc = '', body = '';
for (const [cid, t, sous, niv, files, cls] of CH) {
  const ids = files.flatMap(f => FILEQ[f].map(i => [i, f]));
  const pnj = Object.entries(PNJ).filter(([, v]) => files.includes(v.fichier));
  const main = ids.filter(([i, f]) => principal(i, f)).map(x => x[0]);
  const ann = ids.filter(([i, f]) => !principal(i, f)).map(x => x[0]);
  toc += `<li><a href="#${cid}">${t} — ${esc(sous)}</a> <span class="tn">niv. ${niv} · ${ids.length} quêtes</span></li>`;
  body += `<section class="chap ${cls}" id="${cid}"><div class="chap-k">${t} · niveaux ${niv}</div><h2 class="chap-t">${esc(sous)}</h2>`;
  body += cid === 'cc' ? `<p class="chap-c">${ids.length} contrats.</p>` : cid === 'cm' ? `<p class="chap-c">${ids.length} quêtes.</p>` : `<p class="chap-c">${main.length} quêtes d'histoire, ${ann.length} quêtes annexes.</p>`;
  if (pnj.length) {
    body += '<h3>Personnages</h3><table class="pnj">';
    for (const [, v] of pnj) body += `<tr><td><b>${esc(v.nom)}</b>${v.titre ? '<br><span class="tt">' + esc(v.titre) + '</span>' : ''}</td><td>${(v.repliques || []).map(r => '« ' + esc(r) + ' »').join('<br>')}</td></tr>`;
    body += '</table>';
  }
  let n = 0;
  if (main.length) { body += `<h3>${cid === 'cc' ? 'Contrats' : cid === 'cm' ? 'La chaîne' : "Quêtes d'histoire"}</h3>`; for (const id of main) body += quete(Q[id], ++n); }
  if (ann.length) { body += '<h3>Quêtes annexes</h3>'; for (const id of ann) body += quete(Q[id], ++n); }
  body += '</section>';
}
const syn = fs.readFileSync('synthese.html', 'utf8');
const css = fs.readFileSync('style.css', 'utf8');
const total = Object.keys(Q).length;
const html = `<!doctype html><html lang="fr"><head><meta charset="utf-8"><title>Orbit MMO — Le lore complet</title><style>${css}</style></head><body>
<div class="cover"><div class="cv-k">ORBIT MMO</div><div class="cv-t">Le lore complet</div><div class="cv-s">De la Chute de l'Astre au niveau 90<br>Résumé détaillé et chronique intégrale des quêtes et des dialogues</div><div class="cv-n">${total} quêtes · ${Object.keys(PNJ).length} personnages · 8 actes</div><div class="cv-d">Document de travail · état au 24 novembre 2026</div></div>
<div class="toc"><h1>Sommaire</h1><h3>Partie I — Le lore en entier</h3><ol class="t1"><li><a href="#s-intention">L'intention</a></li><li><a href="#s-cosmo">La cosmologie</a></li><li><a href="#s-marques">Les Marqués</a></li><li><a href="#s-factions">Les deux camps</a></li><li><a href="#s-classes">Les classes</a></li><li><a href="#s-histoire">L'histoire, acte par acte</a></li><li><a href="#s-msn">Le Marqué sans nom</a></li><li><a href="#s-choix">Les grands choix</a></li><li><a href="#s-perso">Les personnages</a></li><li><a href="#s-lieux">Les lieux</a></li><li><a href="#s-glossaire">Glossaire</a></li><li><a href="#s-etat">Où en est l'écriture</a></li></ol>
<h3>Partie II — Chronique intégrale des quêtes</h3><ol class="t2">${toc}</ol>
<p class="lire"><b>Comment lire la Partie II.</b> Chaque quête donne son camp, son niveau, ce qui la débloque, son résumé, puis chaque étape dans l'ordre avec tous ses dialogues. <span class="who">En couleur</span> : le personnage qui parle. <i>En italique</i> : la narration. « Vous » : le joueur. Pour un choix : la question, chaque réponse possible et ce qu'elle déclenche. Les étapes marquées « Seulement si » dépendent d'un choix fait plus tôt.</p></div>
<div class="syn">${syn}</div>
<section class="partie" id="p2"><div class="partie-num">Partie II</div><h1>Chronique intégrale des quêtes</h1><p class="chapo">Toutes les quêtes du jeu, dans l'ordre de l'histoire, avec tous les dialogues des personnages, tels qu'ils sont écrits dans le jeu.</p></section>
${body}
<section class="partie" id="fin"><div class="partie-num">À suivre</div><h1>L'aventure continue</h1><p class="chapo">L'Astre brille entier, mais le monde d'Orbit n'a pas fini de parler. Les Brèches se rouvrent chaque jour, le Pilier des Astres attend ses guildes, et bien des Marqués n'ont pas encore trouvé leur nom.<br><br>Les prochains chapitres arriveront plus tard, et ce document grandira avec eux.</p></section>
</body></html>`;
const cut = html.indexOf('</style>') + 8;
const typo = s => s.replace(/« /g, '«&nbsp;').replace(/ »/g, '&nbsp;»').replace(/ ([:;?!])(?=[\s<])/g, '&nbsp;$1');
fs.writeFileSync('Orbit-MMO-Lore-complet.html', html.slice(0, cut) + typo(html.slice(cut)));
console.log('ok', total, 'quetes', Math.round(html.length / 1024), 'Ko');
