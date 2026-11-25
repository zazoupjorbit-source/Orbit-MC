// Fil de travail de la page de diffusion : il tourne aussi quand l'onglet est caché (ce qui est le cas dès que
// l'on partage son écran), là où les minuteries d'une page sont ralenties à une par seconde.
let cfg = null, actif = false, toile = null, ctx = null;
let dernierBlob = null, dernierEnvoi = 0, occupe = false, lecteur = null, minuterie = null, veilleur = null;
let images = 0, octets = 0;

function preparer() {
  if (!toile || toile.width !== cfg.w || toile.height !== cfg.h) {
    toile = new OffscreenCanvas(cfg.w, cfg.h);
    ctx = toile.getContext('2d', { alpha: false });
  }
}

function dessiner(source, sw, sh) {
  const W = cfg.w, H = cfg.h;
  const e = cfg.cadrage === 'remplir' ? Math.max(W / sw, H / sh) : Math.min(W / sw, H / sh);
  const dw = sw * e, dh = sh * e;
  ctx.fillStyle = '#000';
  ctx.fillRect(0, 0, W, H);
  ctx.imageSmoothingEnabled = true;
  ctx.imageSmoothingQuality = 'high';
  ctx.drawImage(source, (W - dw) / 2, (H - dh) / 2, dw, dh);
}

async function envoyer(blob) {
  dernierBlob = blob;
  dernierEnvoi = performance.now();
  try {
    const r = await fetch('/image?ecran=' + encodeURIComponent(cfg.ecran), {
      method: 'POST', headers: { 'X-Jeton': cfg.jeton, 'Content-Type': 'image/jpeg' }, body: blob });
    const j = await r.json().catch(() => ({}));
    if (r.ok) { images++; octets += blob.size; }
    postMessage({ type: 'retour', ok: r.ok, statut: r.status, ...j });
  } catch (e) {
    postMessage({ type: 'retour', ok: false, statut: 0 });
  }
}

async function image(source, sw, sh) {
  if (occupe || !actif) return;
  occupe = true;
  try {
    preparer();
    dessiner(source, sw, sh);
    await envoyer(await toile.convertToBlob({ type: 'image/jpeg', quality: cfg.qualite }));
  } finally {
    occupe = false;
  }
}

async function lire(flux) {
  lecteur = flux.getReader();
  let dernier = 0;
  while (actif) {
    const { value, done } = await lecteur.read();
    if (done) break;
    const t = performance.now();
    if (!occupe && t - dernier >= 1000 / cfg.fps - 3) {
      dernier = t;
      image(value, value.displayWidth, value.displayHeight);
    }
    value.close();
  }
}

// Mire de test : barres, dégradés, texte et un point qui bouge, pour juger couleurs et fluidité sans rien partager.
function mire() {
  if (occupe || !actif) return;
  preparer();
  const W = cfg.w, H = cfg.h, t = performance.now() / 1000;
  const barres = ['#ffffff', '#ffe600', '#00e5ff', '#19d44b', '#ff38d1', '#ff2a2a', '#2a50ff', '#000000'];
  barres.forEach((c, i) => { ctx.fillStyle = c; ctx.fillRect(i * W / 8, 0, W / 8 + 1, H * 0.34); });
  const rampes = [[255, 0, 0], [0, 255, 0], [0, 0, 255], [255, 255, 255]];
  rampes.forEach((c, i) => {
    const g = ctx.createLinearGradient(0, 0, W, 0);
    g.addColorStop(0, '#000');
    g.addColorStop(1, `rgb(${c[0]},${c[1]},${c[2]})`);
    ctx.fillStyle = g;
    ctx.fillRect(0, H * (0.34 + i * 0.08), W, H * 0.08 + 1);
  });
  const fond = ctx.createLinearGradient(0, H * 0.66, W, H);
  fond.addColorStop(0, '#0b1020'); fond.addColorStop(0.5, '#3A7FFF'); fond.addColorStop(1, '#E0B864');
  ctx.fillStyle = fond;
  ctx.fillRect(0, H * 0.66, W, H * 0.34);
  ctx.fillStyle = '#fff';
  ctx.font = `bold ${Math.round(H * 0.075)}px Segoe UI, Arial, sans-serif`;  ctx.textBaseline = 'middle';
  ctx.fillText('ORBIT TV · mire de test', W * 0.03, H * 0.76);
  ctx.font = `${Math.round(H * 0.045)}px Segoe UI, Arial, sans-serif`;
  ctx.fillText('Texte fin : Voix ambiguë d\'un cœur qui préfère les kiwis. 0123456789', W * 0.03, H * 0.86);
  ctx.fillText(new Date().toLocaleTimeString('fr-FR') + ' · ' + W + ' × ' + H + ' points', W * 0.03, H * 0.94);
  ctx.fillStyle = '#fff';
  ctx.beginPath();
  ctx.arc(W * (0.5 + 0.45 * Math.sin(t * 1.6)), H * 0.17, H * 0.045, 0, 7);
  ctx.fill();
  occupe = true;
  toile.convertToBlob({ type: 'image/jpeg', quality: cfg.qualite }).then(envoyer).finally(() => { occupe = false; });
}

// ------------------------------------------------------------------ son
// Le son arrive ici par l'un de ces chemins, puis est recoupé en tranches de 20 ms (960 points à 48 kHz, 2 voies),
// encodé en Opus et déposé sur le serveur par lots de 5 (un dixième de seconde), un envoi à la fois pour garder l'ordre :
//  - 'piste' : la piste audio du partage, lue directement (Chrome, Edge). C'est l'horloge de la capture qui rythme le son,
//              pas celle de la carte son de celui qui diffuse.
//  - 'port'  : le fil audio de la page (capture.js), pour les navigateurs qui ne savent pas lire une piste ici.
//  - 'mire'  : une note qui bat, fabriquée ici, pour essayer sans rien partager.
let codeur = null, portSon = null, lecteurSon = null, sourceSon = null, rythmeSon = null;
let paquets = [], envoiSon = false, tranches = 0, crete = 0, octetsSon = 0, auditeurs = 0;
const tg = new Float32Array(960), td = new Float32Array(960);
let tn = 0, phase = 0, avantG = 0, avantD = 0, dernierPcm = 0, mireDebut = 0, mirePoints = 0;

function tranche() {
  for (let i = 0; i < 960; i += 4) { const v = tg[i] < 0 ? -tg[i] : tg[i]; if (v > crete) crete = v; }
  if (++tranches % 5 === 0) { postMessage({ type: 'niveau', crete }); crete = 0; }
  if (!actif || !codeur || codeur.state !== 'configured' || codeur.encodeQueueSize > 25) return;
  const t = new Float32Array(1920);
  t.set(tg, 0);
  t.set(td, 960);
  const a = new AudioData({ format: 'f32-planar', sampleRate: 48000, numberOfFrames: 960, numberOfChannels: 2, timestamp: tranches * 20000, data: t });
  codeur.encode(a);
  a.close();
}

// Ajoute n points (g, d) pris à « taux » points par seconde ; ramenés à 48 kHz si besoin (interpolation simple).
function nourrir(g, d, n, taux) {
  dernierPcm = performance.now();
  if (taux === 48000) {
    for (let i = 0; i < n; i++) { tg[tn] = g[i]; td[tn] = d[i]; if (++tn === 960) { tn = 0; tranche(); } }
    return;
  }
  const pas = taux / 48000;
  let pos = phase;   // position dans ce bloc ; −1 = dernier point du bloc d'avant
  while (pos < n - 1) {
    const i = Math.floor(pos), f = pos - i;
    const g0 = i < 0 ? avantG : g[i], d0 = i < 0 ? avantD : d[i];
    tg[tn] = g0 + (g[i + 1] - g0) * f;
    td[tn] = d0 + (d[i + 1] - d0) * f;
    if (++tn === 960) { tn = 0; tranche(); }
    pos += pas;
  }
  phase = pos - n;
  avantG = g[n - 1];
  avantD = d[n - 1];
}

async function lirePiste(flux) {
  const lecteur = lecteurSon = flux.getReader();
  let g = new Float32Array(4800), d = new Float32Array(4800);
  try {
    while (lecteurSon === lecteur) {
      const { value, done } = await lecteur.read();
      if (done) break;
      const n = value.numberOfFrames, voies = value.numberOfChannels;
      if (g.length < n) { g = new Float32Array(n); d = new Float32Array(n); }
      value.copyTo(g.subarray(0, n), { planeIndex: 0, format: 'f32-planar' });
      if (voies > 1) value.copyTo(d.subarray(0, n), { planeIndex: 1, format: 'f32-planar' });
      const taux = value.sampleRate;
      value.close();
      if (lecteurSon === lecteur) nourrir(g, voies > 1 ? d : g, n, taux);
    }
  } catch (e) {
    if (lecteurSon === lecteur) postMessage({ type: 'son', etat: 'erreur', detail: String(e && e.message || e) });
  }
}

// Dix fois par seconde : la mire fabrique le son qui manque pour suivre l'heure ; une piste qui se tait
// (rien ne joue sur l'écran partagé) est comblée par du silence, pour que les joueurs gardent le direct.
function rythme() {
  const t = performance.now();
  if (sourceSon === 'mire') {
    const du = Math.min(48000, Math.floor((t - mireDebut) * 48) - mirePoints);
    if (du <= 0) return;
    const g = new Float32Array(du);
    for (let i = 0; i < du; i++) {
      const s = (mirePoints + i) / 48000;
      g[i] = Math.sin(6.283185307 * 523.25 * s) * 0.09 * (1 + Math.sin(6.283185307 * s));
    }
    mirePoints += du;
    nourrir(g, g, du, 48000);
  } else if (sourceSon === 'piste' && t - dernierPcm > 300) {
    const vide = new Float32Array(4800);
    nourrir(vide, vide, 4800, 48000);
  }
}

function sonDemarrer(d) {
  sonArreter();
  if (typeof AudioEncoder === 'undefined') { postMessage({ type: 'son', etat: 'absent' }); return; }
  try {
    codeur = new AudioEncoder({
      output: (c) => {
        const b = new Uint8Array(c.byteLength);
        c.copyTo(b);
        paquets.push(b);
        // serveur injoignable : on ne garde pas plus de trois secondes de retard
        if (paquets.length > 150) paquets.splice(0, paquets.length - 50);
        if (paquets.length >= 5) envoyerSon();
      },
      error: (e) => { postMessage({ type: 'son', etat: 'erreur', detail: String(e && e.message || e) }); sonArreter(); },
    });
    codeur.configure({ codec: 'opus', sampleRate: 48000, numberOfChannels: 2, bitrate: d.debit });
  } catch (e) {
    codeur = null;
    postMessage({ type: 'son', etat: 'erreur', detail: String(e && e.message || e) });
    return;
  }
  sourceSon = d.source;
  tn = 0; phase = 0; avantG = avantD = 0;
  dernierPcm = mireDebut = performance.now();
  mirePoints = 0;
  if (d.source === 'piste') {
    lirePiste(d.flux);
  } else if (d.source === 'port') {
    portSon = d.port;
    portSon.onmessage = (m) => nourrir(m.data.subarray(0, 960), m.data.subarray(960), 960, 48000);
  }
  if (d.source !== 'port') rythmeSon = setInterval(rythme, 100);
  postMessage({ type: 'son', etat: 'pret' });
}

function sonArreter() {
  clearInterval(rythmeSon);
  rythmeSon = null;
  sourceSon = null;
  if (lecteurSon) { const l = lecteurSon; lecteurSon = null; l.cancel().catch(() => {}); }
  if (portSon) { portSon.onmessage = null; portSon.close(); portSon = null; }
  if (codeur) { try { codeur.close(); } catch (e) { /* déjà fermé */ } codeur = null; }
  paquets = [];
  auditeurs = 0;
}

async function envoyerSon() {
  if (envoiSon) return;
  envoiSon = true;
  try {
    while (paquets.length >= 5 && actif && codeur) {
      const lot = paquets.splice(0, 50);
      let taille = 0;
      for (const p of lot) taille += 2 + p.length;
      const corps = new Uint8Array(taille);
      let i = 0;
      for (const p of lot) { corps[i++] = p.length >> 8; corps[i++] = p.length & 255; corps.set(p, i); i += p.length; }
      try {
        const r = await fetch('/son?ecran=' + encodeURIComponent(cfg.ecran), {
          method: 'POST', headers: { 'X-Jeton': cfg.jeton, 'Content-Type': 'application/octet-stream' }, body: corps });
        // 409 = l'écran n'est pas encore (ou plus) à nous : ce lot est perdu, le suivant passera
        if (r.ok) { octetsSon += taille; auditeurs = (await r.json().catch(() => ({}))).auditeurs || 0; }
      } catch (e) { /* serveur injoignable : l'image le signale déjà */ }
    }
  } finally {
    envoiSon = false;
  }
}

function arreter() {
  actif = false;
  clearInterval(minuterie);
  clearInterval(veilleur);
  if (lecteur) { lecteur.cancel().catch(() => {}); lecteur = null; }
  dernierBlob = null;
  sonArreter();
}

onmessage = (m) => {
  const d = m.data;
  if (d.type === 'son') { if (actif) sonDemarrer(d); return; }
  if (d.type === 'son-stop') { sonArreter(); return; }
  if (d.type === 'regler') { if (cfg) Object.assign(cfg, d.cfg); if (actif && d.mire) { clearInterval(minuterie); minuterie = setInterval(mire, 1000 / cfg.fps); } return; }
  if (d.type === 'stop') { arreter(); return; }
  if (d.type === 'image') { image(d.image, d.image.width, d.image.height).finally(() => d.image.close()); return; }
  arreter();
  cfg = d.cfg;
  actif = true;
  images = 0; octets = 0;
  // Écran figé : le navigateur n'envoie plus d'image, on renvoie la dernière pour que le serveur garde le direct.
  veilleur = setInterval(() => {
    postMessage({ type: 'debit', images, octets, son: octetsSon, auditeurs, avecSon: !!codeur });
    images = 0; octets = 0; octetsSon = 0;
    if (actif && !occupe && dernierBlob && performance.now() - dernierEnvoi > 1500) envoyer(dernierBlob);
  }, 1000);
  if (d.type === 'flux') lire(d.flux);
  else if (d.type === 'mire') minuterie = setInterval(mire, 1000 / cfg.fps);
  else if (d.type === 'tics') minuterie = setInterval(() => postMessage({ type: 'tic' }), 1000 / cfg.fps);
};
