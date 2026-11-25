// Site Orbit MC : rien n'est branché (serveur, paiement, Discord). Les chiffres et prix sont des exemples.
const $ = (s, el = document) => el.querySelector(s);
const $$ = (s, el = document) => [...el.querySelectorAll(s)];

// ---------- Ciel étoilé ----------
const ciel = $('#om-ciel'), cx = ciel.getContext('2d');
let etoiles = [];
function tailleCiel() {
  ciel.width = innerWidth; ciel.height = innerHeight;
  etoiles = Array.from({ length: Math.round(innerWidth * innerHeight / 5000) }, () => ({
    x: Math.random() * ciel.width, y: Math.random() * ciel.height,
    t: Math.random() < .15 ? 2 : 1, v: .05 + Math.random() * .2, p: Math.random() * 6.28,
    or: Math.random() < .08
  }));
}
function dessinerCiel(ms) {
  cx.fillStyle = '#070B16'; cx.fillRect(0, 0, ciel.width, ciel.height);
  const g = cx.createRadialGradient(ciel.width * .8, ciel.height * .1, 0, ciel.width * .8, ciel.height * .1, ciel.width * .7);
  g.addColorStop(0, 'rgba(46,141,181,.18)'); g.addColorStop(1, 'rgba(7,11,22,0)');
  cx.fillStyle = g; cx.fillRect(0, 0, ciel.width, ciel.height);
  for (const e of etoiles) {
    e.x -= e.v; if (e.x < 0) e.x = ciel.width;
    cx.globalAlpha = .45 + .55 * Math.abs(Math.sin(ms / 900 + e.p));
    cx.fillStyle = e.or ? '#E0B864' : '#A8E6FA';
    cx.fillRect(Math.round(e.x), Math.round(e.y), e.t, e.t);
  }
  cx.globalAlpha = 1;
  if (!matchMedia('(prefers-reduced-motion: reduce)').matches) requestAnimationFrame(dessinerCiel);
}
addEventListener('resize', tailleCiel);
tailleCiel(); requestAnimationFrame(dessinerCiel);

// ---------- Petit message ----------
let minuteur;
function toast(txt) {
  const t = $('#om-toast'); t.textContent = txt; t.classList.add('on');
  clearTimeout(minuteur); minuteur = setTimeout(() => t.classList.remove('on'), 2200);
}

// ---------- Menu téléphone + lien actif ----------
const burger = $('.om-burger'), nav = $('.om-nav');
burger.addEventListener('click', () => {
  const o = nav.classList.toggle('ouvert'); burger.setAttribute('aria-expanded', o);
});
$$('.om-nav a').forEach(a => a.addEventListener('click', () => { nav.classList.remove('ouvert'); burger.setAttribute('aria-expanded', false); }));
const liens = $$('.om-nav a:not(.om-btn)');
const vigie = new IntersectionObserver(entrees => entrees.forEach(e => {
  if (e.isIntersecting) liens.forEach(a => a.classList.toggle('on', a.hash === '#' + e.target.id));
}), { rootMargin: '-45% 0px -50% 0px' });
$$('main > section').forEach(s => vigie.observe(s));

// ---------- Copier l'adresse ----------
$('#om-copier').addEventListener('click', async () => {
  try { await navigator.clipboard.writeText($('#om-ip').textContent); toast('Adresse copiée !'); }
  catch { toast('Copie impossible : sélectionne l\'adresse à la main'); }
});

// ---------- Compteurs ----------
$$('[data-compte]').forEach(el => {
  const fin = +el.dataset.compte, debut = performance.now();
  const pas = ms => { const k = Math.min(1, (ms - debut) / 1200); el.textContent = Math.round(fin * (1 - (1 - k) ** 3)); if (k < 1) requestAnimationFrame(pas); };
  requestAnimationFrame(pas);
});

// ---------- Modes ----------
const MODES = {
  survie: {
    c: '#7BD84E', nom: 'Survie',
    txt: 'La survie comme on l\'aime, avec un vrai monde à partager. Construis ta base, échange avec les autres et protège ce que tu as bâti.',
    pts: ['Terrains protégés', 'Économie entre joueurs', 'Événements de saison'],
    infos: [['Joueurs', 'solo / équipe'], ['Rythme', 'tranquille'], ['État', 'en préparation']]
  },
  extraction: {
    c: '#FF6A2B', nom: 'Extraction',
    txt: 'Entre dans la zone, récupère le butin, et sors avant qu\'il ne soit trop tard. Si tu tombes, tu perds ce que tu portais.',
    pts: ['Armes et grenades', 'Points d\'extraction', 'Coffre personnel entre les raids'],
    infos: [['Joueurs', 'solo / escouade'], ['Rythme', 'intense'], ['État', 'en préparation']]
  },
  practice: {
    c: '#F0475A', nom: 'Practice HCF',
    txt: 'Entraîne-toi au combat avec des kits et des classes. Grimpe au classement en duel et prouve ta valeur.',
    pts: ['Duels classés (Elo)', 'Kits et classes HCF', 'Files 1v1 et 2v2'],
    infos: [['Joueurs', '1v1 / 2v2'], ['Rythme', 'rapide'], ['État', 'en préparation']]
  },
  mmo: {
    c: '#A99BFF', nom: 'MMO',
    txt: 'Depuis la Nuit des Étoiles Filantes, les Marqués doivent choisir : la Lumière d\'Auréval ou l\'Ombre de Morneval. Ton histoire commence ici.',
    pts: ['Deux factions : Lumière et Ombre', 'Classes, sorts et talents', 'Un continent à explorer'],
    infos: [['Joueurs', 'solo / groupe'], ['Rythme', 'aventure'], ['État', 'en développement']]
  }
};
const fiche = $('#om-fiche');
function montrerMode(id) {
  const m = MODES[id];
  $$('.om-mode').forEach(b => b.setAttribute('aria-selected', b.dataset.mode === id));
  fiche.className = `om-fiche gui om-fiche--${id}`;
  fiche.style.setProperty('--c', m.c);
  fiche.innerHTML = `
    <div>
      <h3>${m.nom}</h3>
      <p>${m.txt}</p>
      <ul>${m.pts.map(p => `<li>${p}</li>`).join('')}</ul>
    </div>
    <div class="om-fiche__cote">
      ${m.infos.map(([k, v]) => `<div class="om-fiche__ligne"><span>${k}</span><b>${v}</b></div>`).join('')}
      <button class="om-btn" data-bientot>Entrer par la porte</button>
    </div>`;
  void fiche.offsetWidth; fiche.style.animation = 'none'; fiche.offsetHeight; fiche.style.animation = '';
}
$$('.om-mode').forEach(b => b.addEventListener('click', () => montrerMode(b.dataset.mode)));
fiche.addEventListener('click', e => { if (e.target.closest('[data-bientot]')) toast('Bientôt ouvert !'); });
montrerMode('survie');

// ---------- Le Comptoir ----------
const ARTICLES = [
  { t: 'grade', ico: '★', nom: 'Grade VIP', desc: 'Couleur de pseudo et accès aux salons VIP.', prix: '4,99 €', grade: ['VIP', '#62CFF4'] },
  { t: 'grade', ico: '✦', nom: 'Grade VIP+', desc: 'Tout le VIP, plus des emotes exclusives.', prix: '9,99 €', grade: ['VIP+', '#E0B864'] },
  { t: 'grade', ico: '◉', nom: 'Grade ORBIT', desc: 'Le grade soutien : badge animé et titre.', prix: '19,99 €', grade: ['ORBIT', '#F3D894'] },
  { t: 'cosmetique', ico: '🪐', nom: 'Anneau orbital', desc: 'Un anneau de particules qui tourne autour de toi.', prix: '3,49 €' },
  { t: 'cosmetique', ico: '☄', nom: 'Traînée de comète', desc: 'Une traînée dorée derrière tes pas.', prix: '2,99 €' },
  { t: 'cosmetique', ico: '🎩', nom: 'Casque de pilote', desc: 'Chapeau cosmétique, ne cache pas ta classe.', prix: '2,49 €' },
  { t: 'pet', ico: '🛰', nom: 'Mini-satellite', desc: 'Un petit compagnon qui te suit. Aucun bonus.', prix: '3,99 €' },
  { t: 'pet', ico: '🐙', nom: 'Poulpe stellaire', desc: 'Il flotte à tes côtés et fait des bulles.', prix: '3,99 €' }
];
const grille = $('#om-articles');
function montrerArticles(f) {
  grille.innerHTML = ARTICLES.filter(a => f === 'tout' || a.t === f).map(a => `
    <article class="om-article gui">
      <div class="om-article__vis" aria-hidden="true">${a.ico}</div>
      ${a.grade ? `<span class="om-grade" style="color:${a.grade[1]}">${a.grade[0]}</span>` : ''}
      <h3>${a.nom}</h3>
      <p>${a.desc}</p>
      <div class="om-article__bas"><span class="om-prix">${a.prix}</span><button class="om-btn om-btn--s" data-nom="${a.nom}">Voir</button></div>
    </article>`).join('');
}
$$('.om-chip').forEach(c => c.addEventListener('click', () => {
  $$('.om-chip').forEach(x => x.classList.toggle('on', x === c)); montrerArticles(c.dataset.filtre);
}));
grille.addEventListener('click', e => { const b = e.target.closest('[data-nom]'); if (b) toast(`${b.dataset.nom} : boutique pas encore ouverte`); });
montrerArticles('tout');

// ---------- Formulaire (rien n'est envoyé) ----------
$('#om-form').addEventListener('submit', e => {
  e.preventDefault();
  const p = $('#om-pseudo').value.trim(), msg = $('#om-form-msg');
  if (!/^[A-Za-z0-9_]{3,16}$/.test(p)) { msg.style.color = '#F0475A'; msg.textContent = 'Pseudo invalide : 3 à 16 lettres, chiffres ou _.'; return; }
  msg.style.color = ''; msg.textContent = `Merci ${p} ! (démo : rien n'est envoyé pour l'instant)`;
  e.target.reset();
});
