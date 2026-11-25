// npm run check-guide : relit toutes les fiches du dossier guide/ sans Discord
// et liste les erreurs (en-tête, emoji, longueurs, dates). Code de sortie 1 s'il y en a.
const lib = require('../src/guideLib');

const guide = lib.loadGuide();
for (const c of guide.categories) {
  console.log(`${c.emoji} ${c.titre} (${c.pages.length} fiche${c.pages.length > 1 ? 's' : ''})`);
  for (const p of c.pages) {
    console.log(`   ${p.emoji} ${p.key}  ·  ${p.chunks.length} partie${p.chunks.length > 1 ? 's' : ''}  ·  maj ${p.maj || '?'}`);
  }
}
console.log(`\n${guide.pages.length} fiches, ${guide.categories.length} rubriques.`);
if (guide.errors.length) {
  console.error(`\n✖ ${guide.errors.length} erreur(s) :\n- ${guide.errors.join('\n- ')}`);
  process.exit(1);
}
console.log('✔ Aucune erreur.');
