---
titre: OrbitSurvie (Survie)
emoji: 🏝️
resume: Île du spawn, crédits, marché, grades, contrats, métiers, événements, arène, échoppes, bases
mots: survie, carte, squaremap, map, roulement, cours, orbitsurvie, ile, argent, credits, payer, baltop, kit, base, armoire, tc, shop, marche, vendre, prix, hdv, portail, rtp, grade, vip, contrats, metiers, jobs, classement, evenement, largage, boss, peche, duel, pari, tournoi, echoppe, home, sethome, tpa, ressources
maj: 2026-11-26
---
**Où** : serveur `survie` (25569). Permission admin : `survie.admin`.

**▸ Le mode**
Survie vanilla dans le monde `world`, **sans PvP** sauf dans l'arène. Le spawn est le **port orbital** (depuis le 26/11), une station générée par code dans le monde vide `orbital`, de nuit sous les étoiles, au-dessus d'une planète qu'on voit par les planchers de verre. Atrium central (spawn, Guide, classements) relié par des coursives à 8 modules : **baie de lancement** au nord (le sas : capsule de descente vers un point aléatoire de la planète, 300 à 3 000 blocs ; Prospecteur), **galerie marchande** à l'est (mineur, bûcheron, fermier, chasseur, maître des métiers), **comptoir d'échange** à l'ouest (hôtel des ventes, banquier, coffres de l'Ender), **arène** au sud (fosse PvP, inventaire gardé), **labo d'enchantement** au nord-ouest, **kits et grades** au nord-est, **quais** au sud-est (pêcheur, capitaine, navette amarrée), **serre** au sud-ouest. Qui tombe de la station revient au spawn. L'ancienne île tropicale (monde `ile`) est gardée sur le disque : `ile.style: tropical` + `ile.monde: ile` dans la config puis redémarrage pour y revenir.

**▸ Économie**
Crédits en base (400 au départ). Depuis le 25/11, prix du marché ×4 (×6 pour les produits de farm : cultures, élevage, butin de monstres, poissons, bois). Les prix suivent l'offre et la demande, et **toutes les heures un roulement** fait monter ou baisser 12 objets tirés au hasard (+40 % / −35 % max), annoncé dans le chat et visible dans `/shop` → Tendances. Bornes fixes pour les objets précieux : diamant et netherite 0,85 à 1,20, émeraude 0,80 à 1,25, fer et or 0,70 à 1,30 (dans `marche.yml`). HDV entre joueurs (taxe 5 %, 48 h, 10 ventes max).

**▸ Kits** — `/kit`
Départ (1 fois, avec une armoire de base) · Joueur (24 h) · VIP (24 h) · VIP+ (48 h) · ORBIT (72 h).

**▸ Grades** — `/grade` (PNJ « Grades » au pavillon des kits)
VIP 1 000 000, VIP+ 2 500 000, ORBIT 5 000 000 crédits pour **30 jours** ; racheter prolonge. Payés **uniquement en crédits du jeu**. Posés par LuckPerms dans le contexte `server=survie` (le grade ne vaut que sur la survie). Ils ouvrent les kits et montent les limites (armoires, points de retour, échoppes).

**▸ Armoires de base** — `/base`
Comme le TC de Rust : protège un carré autour d'elle, sur toute la hauteur (surface et Nether). Rayon 15 / 25 / 40 selon le niveau, payé en crédits **par jour** (3 jours offerts, 30 jours d'avance max). Impayée → la protection saute (avertissement 2 jours avant). 1 à 4 armoires selon le grade, 8 membres. Les joueurs arrivés avant le 23/11 (kit Départ sans armoire) en reçoivent une à leur connexion.

<!-- suite -->
**▸ Contrats du jour** — `/contrats` (Capitaine, au port)
Trois commandes par jour (facile, moyen, difficile), les mêmes pour tous : on apporte les objets, on est payé en crédits ; les trois livrés = prime de 1 000. Une fois par jour et par joueur.

**▸ Métiers** — `/metiers` (Maître des métiers, au marché)
Mineur, Bûcheron, Fermier, Pêcheur, Chasseur : tout le monde a les cinq, ils montent en jouant dans la survie (niveau **200** max, XP plus dure depuis le 25/11 : ~1,15 M d'XP pour le niveau 50, ~44 M pour le 200). Chaque action paie quelques crédits (versés toutes les 5 min, +1 % par niveau) ; prime tous les 10 niveaux, de 2 000 au niveau 10 à 1 000 000 au niveau 200. Rien pour un bloc qu'on vient de poser, un minerai pris au toucher de soie ou un monstre de générateur.

**▸ Classements** — `/classement [catégorie]`
Du mois : contrats, xp, poissons, monstres, ventes, duels ; fortunes : `/richesses`. Deux panneaux sur la place. Au changement de mois, les trois premiers de chaque catégorie reçoivent 25 000 / 12 000 / 6 000 crédits, automatiquement.

**▸ Événements** — `/evenement`
Un par heure s'il y a au moins 2 joueurs, tiré au hasard : **concours de pêche** (10 min, lots aux 3 premiers), **largage** (un tonneau de butin tombe à des coordonnées annoncées, le premier qui l'ouvre prend tout ; retiré au bout de 15 min) et **boss** (monstre renforcé, prime de 12 000 partagée selon les dégâts).

**▸ Arène** — `/duel`, `/pari`, `/tournoi`
`/duel <pseudo> [mise]` : le gagnant prend les deux mises (taxe 5 %). Pendant le compte à rebours de 20 s, les spectateurs parient (`/pari <pseudo> <montant>`, pot partagé entre les gagnants). Pendant un duel, seuls les deux duellistes se frappent. Tournoi à élimination directe chaque **samedi à 21:00** (5 min d'inscription, 4 joueurs minimum, 20 000 / 8 000 crédits). Mise max 500 000, pari max 20 000.

**▸ Échoppes** — `/echoppe`, `/echoppes`
Un coffre ou un tonneau de sa base devient une boutique : `/echoppe creer <prix> [quantité]`, objet en main, en regardant le coffre. Les visiteurs cliquent le coffre pour acheter, le vendeur est payé même absent. 3 à 15 échoppes selon le grade.

**▸ Carte web** (squaremap 1.4.4, depuis le 25/11)
Carte vue du dessus du monde de survie, avec les joueurs en direct : http://127.0.0.1:8080 , **sur le PC du serveur seulement** (pas ouverte sur Internet). Le port, l'ancienne île et le monde ressources ne sont pas affichés. Elle se met à jour toute seule quand des chunks changent.

**▸ Points de retour et monde ressources**
`/sethome [nom]`, `/home [nom]`, `/delhome`, `/homes` (2 à 8 selon le grade, pas dans la base d'un autre) · `/tpa <pseudo>`, `/tpaccept`, `/tprefuse`. Téléportation après 5 s sans bouger.
`/ressources` (depuis le port, ou le Prospecteur de la baie de lancement) : monde normal à part pour miner, **remis à zéro chaque début de mois** ; ni base ni point de retour là-bas.

<!-- suite -->
**▸ Commandes joueurs**
`/argent [pseudo]` (`/money`, `/bal`) · `/payer <pseudo> <montant>` · `/richesses` (`/baltop`) · `/kit [nom]` · `/grade` · `/base [acheter|ajouter <pseudo>|retirer <pseudo>|zone]` · `/shop [catégorie|recherche]` · `/vendre [tout]` · `/prix` · `/hdv [vendre <prix>|ventes]` · `/contrats` · `/metiers` · `/classement [catégorie]` · `/evenement` · `/duel` · `/pari` · `/tournoi [rejoindre|quitter]` · `/echoppe` · `/echoppes` · `/home` · `/sethome` · `/delhome` · `/homes` · `/tpa` · `/ressources` · `/spawn` (5 s sans bouger).

**▸ Commandes admin**
• `/ile ecarts` — compare le spawn et le plan, sans rien toucher. **Toujours avant generer.**
• `/ile generer` — remet le spawn comme sur le plan (écrase les retouches).
• `/ile tp [point]` · `/ile points` · `/ile pnj` (repose les PNJ) · `/ile reload` (relit toute la config)
• `/ile apercu` — vue de dessus du plan dans `plugins\OrbitSurvie\ile-apercu.png`.
• `/ile argent <pseudo> <montant>` — fixe le solde d'un joueur.
• `/base donner <pseudo> [nombre]` — donne des armoires (64 max).
• `/base bypass` — active / coupe le contournement : tu peux tout casser et ouvrir dans les bases protégées.
• `/evenement lancer <peche|largage|boss>` — lance un événement tout de suite · `/evenement stop` — l'arrête (tonneau et boss retirés).
• `/tournoi ouvrir` — ouvre les inscriptions d'un tournoi · `/tournoi annuler`.
• `/ressources reset oui` — efface et recrée le monde ressources (les joueurs qui y sont rentrent au port).
• `/shop admin roulement` — relance tout de suite un roulement des prix · `/shop admin prix <objet…>` — prix d'achat, de vente et de référence · `/shop admin reset [objet|tout]` — remet les prix à la normale.
• `/squaremap fullrender minecraft:overworld` — redessine toute la carte · `/squaremap radiusrender minecraft:overworld <rayon>` — autour de toi · `/squaremap cancelrender` · `/squaremap pauserender` · `/squaremap hide|show [pseudo]` — cacher un joueur de la carte · `/squaremap reload` (après une modif de `plugins\squaremap\config.yml`).
• `/echoppe supprimer` — en regardant une échoppe : la ferme, même si elle n'est pas à toi.

**▸ Réglages**
Tout est dans `instances\survie\plugins\OrbitSurvie\config.yml` (et `marche.yml` pour l'inflation, le roulement et les bornes de prix) : prix des grades, liste des contrats, barèmes d'XP des métiers, lots, événements, mises, limites par grade. `/ile reload` après une modification.
