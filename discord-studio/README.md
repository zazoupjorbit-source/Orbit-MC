# Discord ORBIT STUDIO

Kit qui monte tout le serveur Discord du studio d'un coup (rôles, salons, permissions, messages), plus un bot pour le règlement, les notifications et les **demandes de projet**.
Même principe que le kit d'ORBIT MC (`discord/`), mais c'est un bot séparé, sur un serveur séparé.

## Ce qui est créé

**10 rôles** : 👑 Fondateur, ⚙️ Développeur, 🚀 Porteur de projet (donné quand une demande est acceptée), 🤝 Partenaire, 🧪 Bêta-testeur, ✦ Membre (règlement accepté), 🔔 Annonces / Devblog / Sorties / Sessions de test.

**9 catégories, 31 salons**

| Catégorie | Qui la voit | Contenu |
|---|---|---|
| 📌 ACCUEIL | Tout le monde | bienvenue, règlement (bouton **J'accepte**), annonces, rôles, liens |
| 🛰️ ACTUALITÉ | Membres, lecture seule | devblog, changelog, vitrine, suivi-des-projets (tenu par le bot) |
| 🧩 NOS PROJETS | Membres | orbit-mc, mods-et-plugins, garrys-mod, ark, autres-projets |
| 📨 DEMANDES DE PROJET | Membres | demander-un-projet (bouton), idées-de-projets (forum) |
| 💬 COMMUNAUTÉ | Membres | général, médias, entraide-dev (forum), suggestions (forum), hors-sujet |
| 🔊 VOCAUX | Membres | Salon, Dev en direct, Session de jeu, AFK |
| 🧪 BÊTA | Bêta-testeurs, porteurs de projet, studio | bêta-infos, bêta-retours |
| 🔒 STUDIO | Fondateurs + devs | studio, idées, logs, Réunion |
| 📂 DEMANDES OUVERTES | Studio (+ chaque demandeur pour son salon) | un salon privé par demande |

## Les demandes de projet

1. Le membre clique sur **Demander un projet** dans #demander-un-projet et remplit le formulaire : nom, type (mode, plugin, mod, map, addon GMod, bot Discord, site, autre), plateforme et version, description, infos utiles.
2. Le bot ouvre un salon privé `📨・<numéro>-<nom>` entre lui et le studio, et le note dans #logs.
3. Le studio fait avancer la demande avec les boutons : **Accepter → En cours → En test → Livrée**, ou **Refuser** (avec une raison que le demandeur voit).
   Le demandeur est prévenu à chaque étape. À l'acceptation, il reçoit le rôle 🚀 Porteur de projet (accès à la bêta).
4. Dès qu'une demande est acceptée, une carte apparaît dans #suivi-des-projets et se met à jour toute seule. Les demandes refusées ou en attente n'y sont jamais affichées.
5. **Fermer le salon** (demandeur ou studio) supprime le salon, mais la demande reste enregistrée. Le bouton **Mes demandes** liste les siennes.

Réglages dans `config.json` → `demandes` : `maxOuvertesParMembre` (2 par défaut) et `suiviPublic` (mettre `false` pour cacher #suivi-des-projets).
Les demandes sont enregistrées dans `data/demandes.json` (hors git).

---

## Installation (environ 10 minutes)

### 1. Créer le serveur
Dans Discord : **+** → **Créer le mien** → **Pour un club ou une communauté**. Nomme-le *ORBIT STUDIO*, mets l'icône.
Utilise un **serveur vide** : le script modifie les permissions de @everyone.

*Recommandé :* **Paramètres du serveur → Activer la Communauté**, pour avoir les forums et les salons d'annonces. Sinon ils sont créés en salons texte.

### 2. Créer le bot (un bot à part, pas celui d'ORBIT MC)
1. <https://discord.com/developers/applications> → **New Application** (nom : *ORBIT STUDIO*).
2. Onglet **Bot** → **Reset Token**, copie le token. C'est un mot de passe : ne le montre à personne.
3. Onglet **OAuth2 → URL Generator** : coche `bot` et `applications.commands`, puis **Administrator** dans *Bot Permissions*.
4. Ouvre l'URL générée et ajoute le bot au serveur.

### 3. Configurer
1. Copie `.env.example` en `.env`, mets le token dans `DISCORD_TOKEN` et l'ID du serveur dans `GUILD_ID`
   (Paramètres Discord → Avancés → **Mode développeur**, puis clic droit sur le serveur → **Copier l'identifiant**).
2. Dans `config.json`, renseigne tes liens (site, GitHub, YouTube, invitation du Discord ORBIT MC…). Les champs vides sont masqués.

### 4. Lancer
```powershell
npm install
npm run setup
npm start
```
`npm run setup` crée tout le serveur ; tu peux le relancer après chaque modification de `src/structure.js` ou `src/messages.js`, rien n'est dupliqué.
`npm start` lance le bot : il doit tourner pour que les boutons et le formulaire marchent.

Après l'installation, donne-toi le rôle 👑 Fondateur (et à Wane), et ⚙️ Développeur aux devs.
