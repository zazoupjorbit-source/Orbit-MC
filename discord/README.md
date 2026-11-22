# Discord ORBIT MC

Ce kit installe tout le serveur Discord d'un coup : rôles, salons, permissions et messages. Il fournit aussi un bot pour les boutons, les tickets et le statut des serveurs.

## Ce qui est créé

**17 rôles**
- **Staff :** 👑 Fondateur, 🛠️ Administrateur, ⚙️ Développeur, 🛡️ Modérateur, 🎧 Support
- **Équipe :** 🏗️ Builder, 📜 Scénariste
- **Communauté :** 🎥 Créateur de contenu, 🤝 Partenaire, 🧪 Bêta-testeur
- **Factions :** ☀️ Lumière, 🌑 Ombre
- **Membre vérifié :** ✦ Marqué
- **Notifications :** 🔔 Annonces / Devblog / Événements / Sessions de test

**11 catégories et 43 salons**

| Catégorie | Qui la voit | Contenu |
|---|---|---|
| 📌 BIENVENUE | Tout le monde | bienvenue, règlement (bouton **J'accepte**), annonces, rôles, liens |
| 🌌 ORBIT MC | Marqués, lecture seule | le-jeu (lore), devblog, changelog, statut-serveurs (auto), chroniques, événements |
| 💬 COMMUNAUTÉ | Marqués | général, médias, recherche-de-groupe, suggestions (forum), commandes, hors-sujet |
| ⚔️ MODES DE JEU | Marqués | survie, extraction, practice, reborn |
| ☀️ LUMIÈRE | Faction Lumière + modos | bastion-de-l-aube, salon vocal |
| 🌑 OMBRE | Faction Ombre + modos | citadelle-d-obsidienne, salon vocal |
| 🎫 SUPPORT | Marqués | aide (forum), bugs (forum), tickets (bouton) |
| 🔊 VOCAUX | Marqués | Taverne, Groupe I à III (5 places), Duel (2 places), AFK |
| 🧪 BÊTA | Bêta-testeurs + équipe | bêta-infos, bêta-retours, salon vocal |
| 🔒 STAFF | Équipe | staff, logs, dev, build, lore, Réunion |
| 📨 TICKETS OUVERTS | Support + modos | les tickets créés par le bot |

**Parcours d'un nouveau membre :** à son arrivée, il ne voit que BIENVENUE. Il accepte le règlement et reçoit le rôle ✦ Marqué, ce qui lui ouvre le reste du serveur. Ensuite il choisit sa faction, qui lui donne accès à son salon privé, et ses notifications.

**Réglages du serveur :** vérification moyenne, filtre de contenu pour tous, notifications « mentions seulement », salon AFK. Les messages d'arrivée vont dans #général.

---

## Installation (environ 10 minutes)

### 1. Créer le serveur
Dans Discord, clique sur **+**, puis **Créer le mien**, puis **Pour un club ou une communauté**. Nomme-le *ORBIT MC* et mets une icône.
Utilise un **serveur vide** : le script modifie les permissions de @everyone.

*Recommandé :* **Paramètres du serveur → Activer la Communauté**. Tu obtiens ainsi les salons forum (suggestions, aide, bugs) et les salons d'annonces. Sans la Communauté, ces salons sont créés en salons texte classiques.

### 2. Créer le bot
1. Va sur <https://discord.com/developers/applications>, puis **New Application** (nom : *ORBIT MC*).
2. Onglet **Bot** : clique **Reset Token** et copie le token. C'est un mot de passe : ne le montre à personne.
3. Onglet **OAuth2 → URL Generator** : coche `bot` et `applications.commands`, puis dans *Bot Permissions* coche **Administrator**.
4. Ouvre l'URL générée et ajoute le bot à ton serveur.

### 3. Configurer
1. Copie `.env.example` en `.env`. Mets le token dans `DISCORD_TOKEN` et l'ID du serveur dans `GUILD_ID`.
   Pour obtenir l'ID : Paramètres Discord → Avancés → **Mode développeur**, puis clic droit sur le serveur → **Copier l'identifiant**.
2. Dans `config.json`, renseigne l'adresse du serveur Minecraft et tes liens (site, YouTube, TikTok…). Les champs vides sont simplement masqués.

### 4. Lancer
```powershell
npm run setup
```
Cette commande crée tout le serveur. Tu peux la relancer à chaque modification : rien n'est dupliqué et les messages sont mis à jour sur place.

```powershell
npm start
```
Le bot doit **rester allumé** pour que les boutons, les tickets et le statut fonctionnent.

### 5. Finitions manuelles
- Donne-toi le rôle **👑 Fondateur**.
- Dans **Paramètres → Rôles**, vérifie que le rôle du bot (*ORBIT MC*) est placé **au-dessus** de ✦ Marqué, des factions et des notifications. Sinon, il ne peut pas les attribuer.
- Si la Communauté est activée, configure l'**Accueil (Onboarding)** de Discord pour qu'il pointe vers #règlement et #rôles.

---

## Statut des serveurs
Le bot interroge les serveurs listés dans `config.json → status.servers` toutes les 60 secondes. Il met à jour #statut-serveurs et affiche « X Marqués en jeu » dans son statut.
Les adresses sont réglées sur `127.0.0.1` (proxy et backends locaux) : le bot doit donc tourner **sur la même machine** que le réseau. Si le bot est hébergé ailleurs, remplace-les par l'adresse publique.

## Personnaliser
- **Salons, rôles et permissions :** `src/structure.js`, puis `npm run setup`.
- **Textes (bienvenue, règlement, lore…) :** `src/messages.js`, puis `npm run setup`.
- `data/state.json` conserve les identifiants des éléments créés. Si tu le supprimes, le script retrouve les éléments par leur nom.
- Le script ne **supprime jamais** rien. Un salon retiré de `structure.js` reste sur Discord : supprime-le à la main.

> ORBIT MC n'est pas un produit officiel Minecraft. Non approuvé par Mojang ou Microsoft, ni associé à eux.
