const yaml=require('C:/Users/Zazou/oa-site/node_modules/js-yaml');const fs=require('fs');
const D='C:/Users/Zazou/MinecraftDev/plugins-src/OrbitMMO/src/main/resources/quests/';
const f=process.argv[2];const y=yaml.load(fs.readFileSync(D+f,'utf8'));
const q=y.quetes||{};const p=y.pnj||{};
console.log('## '+f+' pnj:'+Object.keys(p).length+' quetes:'+Object.keys(q).length);
console.log('PNJ: '+Object.entries(p).map(([k,v])=>k+'='+v.nom+(v.titre?' ('+v.titre+')':'')).join('; '));
for(const [id,v] of Object.entries(q)){ if(process.argv[3]&&!new RegExp(process.argv[3]).test(id))continue;
 console.log(id+' |'+(v.faction||'-')+'|'+v.niveau+'| '+v.nom+' — '+(v.resume||[]).join(' '));}
