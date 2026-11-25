---
titre: Signalements, alertes et chat staff
emoji: 🚨
resume: /report des joueurs, /reports, alertes de triche (CPS, allonge) et chat staff réseau
mots: report, reports, signaler, signalement, alertes, alerts, cps, allonge, reach, triche, sc, staffchat, chat staff
maj: 2026-11-25
---
**▸ Signalements**
• Les joueurs : `/report <joueur> <motif>` (`/signaler`).
• Le staff : `/reports` liste les signalements en attente, sur **tous** les serveurs.

**▸ Alertes de triche**
OrbitStaff surveille le combat et **alerte** le staff, il ne sanctionne jamais seul :
• **CPS** au-dessus de 18 clics / seconde ;
• **allonge** au-dessus de 3,6 blocs.
Une alerte du même type pour le même joueur au plus toutes les 10 s. Réglages dans `plugins\OrbitStaff\config.yml` (`combat:`).
Couper / remettre les alertes pour soi : `/alertes` (`/alerts`).

**▸ Chat staff (tous les serveurs)**
• `/sc <message>` (`/staffchat`) : message au staff connecté, où qu'il soit.
• `/sc` seul : bascule ton chat sur le canal staff (re-taper pour revenir).

**▸ Comment ça circule**
Chat staff, signalements, alertes et sanctions passent par la base (« fil réseau », relu toutes les 2 s). Pas besoin de plugin sur le proxy.

**▸ Administration**
`/orbitstaff statut` (version, nom du serveur, base OK ou coupée, catalogues branchés) · `/orbitstaff reload` (relit `config.yml` et `sanctions.yml`).
