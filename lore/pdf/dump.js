const {PNJ,Q}=require('./data');
const who=l=>{const m=/^([a-z0-9_]+): (.*)$/.exec(l);if(!m)return '  '+l;const n=m[1]==='joueur'?'VOUS':(PNJ[m[1]]?PNJ[m[1]].nom:m[1]);return '  '+n+' : '+m[2];};
for(const id of process.argv.slice(2)){const q=Q[id];if(!q){console.log('?',id);continue;}
 console.log('### '+q.nom+' ('+id+')');for(const l of [...(q.offre||[]),...(q.intro||[])])console.log(who(l));
 for(const s of q.etapes||[]){console.log(' ['+s.type+'] '+s.texte+(s.si?' {si '+s.si+'}':''));
  for(const l of s.dialogue||[])console.log(who(l));
  if(s.reponses){console.log('  ? '+s.question);for(const r of s.reponses){console.log('  -> '+r.id+': '+r.texte);for(const l of r.reponse||[])console.log('  '+who(l));}}
  for(const i of s.indices||[]){console.log('  * '+i.texte);for(const l of i.apres||[])console.log('  '+who(l));}
  for(const l of s.apres||[])console.log(who(l));}}
