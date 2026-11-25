// Charge toutes les quêtes et PNJ d'OrbitMMO (textes français).
const yaml=require('C:/Users/Zazou/oa-site/node_modules/js-yaml');const fs=require('fs');
const D='C:/Users/Zazou/MinecraftDev/plugins-src/OrbitMMO/src/main/resources/quests/';
const FILES=['lumiere-depart','lumiere-val-ardence','ombre-balafre','lumiere-bois-aube','ombre-sylve-noire','lumiere-ponant','ombre-marais','la-marche','acte4-desert','acte5-cimes','acte6-cendres','acte7-fournaise','acte8-souffle','marque-sans-nom','contrats'];
const PNJ={},MON={},Q={},ORDER=[],FILEQ={},CHOIX={};
for(const f of FILES){const y=yaml.load(fs.readFileSync(D+f+'.yml','utf8'));
 for(const [k,v] of Object.entries(y.pnj||{})) if(!PNJ[k]) PNJ[k]={...v,fichier:f};
 for(const [k,v] of Object.entries(y.monstres||{})) MON[k]=v;
 FILEQ[f]=[];
 for(const [k,v] of Object.entries(y.quetes||{})){Q[k]={...v,id:k,fichier:f};ORDER.push(k);FILEQ[f].push(k);
  for(const s of v.etapes||[]) if(s.type==='CHOIX') CHOIX[s.choix]=CHOIX[s.choix]||{question:s.question,reponses:s.reponses,quetes:[]}, CHOIX[s.choix].quetes.push(k);}
}
module.exports={PNJ,MON,Q,ORDER,FILEQ,CHOIX,FILES};
