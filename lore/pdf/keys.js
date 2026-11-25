const yaml=require('C:/Users/Zazou/oa-site/node_modules/js-yaml');const fs=require('fs');
const D='C:/Users/Zazou/MinecraftDev/plugins-src/OrbitMMO/src/main/resources/quests/';
const K={q:{},s:{},t:{},top:{},p:{}};
for(const f of fs.readdirSync(D)){const y=yaml.load(fs.readFileSync(D+f,'utf8'));
for(const k in y)K.top[k]=1; for(const v of Object.values(y.pnj||{}))for(const k in v)K.p[k]=1;
for(const v of Object.values(y.quetes||{})){for(const k in v)K.q[k]=1;for(const s of v.etapes||[]){K.t[s.type]=1;for(const k in s)K.s[k]=(K.s[k]||0)+1;}}}
for(const k in K)console.log(k,Object.keys(K[k]).filter(x=>!x.endsWith('-en')).join(' '));
