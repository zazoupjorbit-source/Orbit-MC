// Capture du son de la page de diffusion : tourne dans le fil audio du navigateur, qui n'est jamais ralenti,
// même onglet caché. Découpe le son en tranches de 20 ms (960 points à 48 kHz, 2 voies) et les passe au fil de
// travail par le port reçu au démarrage.
class Capture extends AudioWorkletProcessor {
  constructor() {
    super();
    this.sortie = null;
    this.n = 0;
    this.g = new Float32Array(960);
    this.d = new Float32Array(960);
    this.port.onmessage = (m) => { if (m.data.port) this.sortie = m.data.port; };
  }

  process(entrees) {
    const voies = entrees[0];
    const g = voies && voies[0], d = voies && (voies[1] || voies[0]);
    const taille = g ? g.length : 128;
    for (let i = 0; i < taille; i++) {
      this.g[this.n] = g ? g[i] : 0;
      this.d[this.n] = d ? d[i] : 0;
      if (++this.n === 960) {
        this.n = 0;
        if (this.sortie) {
          const t = new Float32Array(1920);
          t.set(this.g, 0);
          t.set(this.d, 960);
          this.sortie.postMessage(t, [t.buffer]);
        }
      }
    }
    return true;
  }
}
registerProcessor('capture', Capture);
