// Fiche du Trieur ORBIT dans le forum portfolio ⛏️・minecraft (même contenu que le post #devblog).
const trieur = require('./trieur');

module.exports = {
  key: 'trieur_portfolio',
  channel: 'p_orbitmc', // forum ⛏️・minecraft (ex-orbit-mc)
  threadName: '🧩 Trieur ORBIT · mod Fabric 26.3',
  build: trieur.build,
};
