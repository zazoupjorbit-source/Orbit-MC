---
titre: OrbitSurvie (Survie)
emoji: 🏝️
resume: Île du spawn, crédits, marché, HDV, kits par grade, armoires de base, portail aléatoire
mots: survie, orbitsurvie, ile, argent, credits, payer, baltop, kit, base, armoire, tc, shop, marche, vendre, prix, hdv, portail, rtp
maj: 2026-11-25
---
**Où** : serveur `survie` (25569). Permission admin : `survie.admin`.

**▸ Le mode**
Survie vanilla dans le monde `world`, **sans PvP** sauf dans l'arène. Le spawn est une **île tropicale** générée par code (monde `ile`) : place et fontaine, **portail** nord (téléportation aléatoire à chaque passage, 300 à 3 000 blocs), **marché** (mineur, bûcheron, fermier, chasseur), **tour des enchantements**, **hôtel des ventes** (commissaire, banquier, coffres de l'Ender), **arène** (fosse PvP, inventaire gardé), port et phare.

**▸ Économie**
Crédits en base (100 au départ). Marché aux prix qui suivent l'offre et la demande ; HDV entre joueurs (taxe 5 %, 48 h, 10 ventes max).

**▸ Kits** — `/kit`
Départ (1 fois, avec une armoire de base) · Joueur (24 h) · VIP (24 h) · VIP+ (48 h) · ORBIT (72 h). Les grades VIP / VIP+ / ORBIT s'obtiennent **uniquement avec la monnaie du jeu**.

**▸ Armoires de base** — `/base`
Comme le TC de Rust : protège un carré autour d'elle, sur toute la hauteur (surface et Nether). Rayon 15 / 25 / 40 selon le niveau, payé en crédits **par jour** (3 jours offerts, 30 jours d'avance max). Impayée → la protection saute (avertissement 2 jours avant). 1 à 4 armoires selon le grade, 8 membres.

**▸ Commandes joueurs**
`/argent [pseudo]` (`/money`, `/bal`) · `/payer <pseudo> <montant>` · `/richesses` (`/baltop`) · `/kit [nom]` · `/base [acheter|ajouter <pseudo>|retirer <pseudo>|zone]` · `/shop [catégorie|recherche]` · `/vendre [tout]` · `/prix` · `/hdv [vendre <prix>|ventes]` · `/spawn` (5 s sans bouger).

**▸ Commandes admin**
• `/ile ecarts` — compare l'île et le plan, sans rien toucher. **Toujours avant generer.**
• `/ile generer` — remet l'île comme sur le plan (écrase les retouches).
• `/ile tp [point]` · `/ile points` · `/ile pnj` (repose les PNJ) · `/ile reload`
• `/ile argent <pseudo> <montant>` — fixe le solde d'un joueur.
• `/base donner <pseudo> [nombre]` — donne des armoires (64 max).
• `/base bypass` — active / coupe le contournement : tu peux tout casser et ouvrir dans les bases protégées.
