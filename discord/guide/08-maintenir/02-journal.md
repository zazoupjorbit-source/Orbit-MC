---
titre: Journal des changements du guide
emoji: 🗒️
resume: Ce qui a été ajouté ou corrigé dans le guide, du plus récent au plus ancien
mots: journal, historique, changements, changelog, nouveautes
maj: 2026-11-26
---
Ajouter une ligne en haut à chaque changement : date, fiche(s), ce qui a changé.

**26/11/2026**
• **Survie : le port orbital remplace l'île.** Fiche « OrbitSurvie (Survie) » : nouveau spawn dans le monde `orbital` (station sous les étoiles au-dessus d'une planète, 8 modules autour de l'atrium), même PNJ et mêmes commandes ; le portail devient une capsule de descente (chute guidée, sans dégâts) ; `/ile apercu` ajouté à la liste. En place sur la survie le 26/11 à 00:24.

**25/11/2026**
• **HCF : le staff est deathban comme tout le monde.** Fiche « Orbit HCF (OrbitHCF) » : la permission `*` des grades staff ne protège plus du deathban ni des règles du spawn ; il faut passer en mode staff (`/f bypass`). 25/11 17:01.
• **HCF : PNJ de combat logger.** Fiche « Orbit HCF (OrbitHCF) » : une déconnexion hors de la Safezone laisse un villageois au nom du joueur ; tué = mort du joueur (objets, DTR, deathban). `/logout` reste la sortie sûre. Chargé sur practice le 25/11 à 10:04.

• **Survie : économie revue et carte web.** Fiche « OrbitSurvie (Survie) » : grades à 1 000 000 / 2 500 000 / 5 000 000 crédits, métiers jusqu'au niveau 200 avec une XP plus dure et une prime tous les 10 niveaux, prix du marché ×4 (×6 pour les produits de farm), roulement des prix toutes les heures avec bornes sur les objets précieux (`/shop admin roulement`), primes et lots relevés, carte web squaremap sur http://127.0.0.1:8080 (PC du serveur seulement) et ses commandes `/squaremap`. En place sur la survie le 25/11 à 10:00.
• **HCF : claim à la baguette.** Fiche « Orbit HCF (OrbitHCF) » : `/f claim` donne une baguette (deux clics pour les coins, achat accroupi + clic gauche dans le vide ou `/f claim valider`), terrains rectangulaires payés par la banque de faction, `/f unclaim` rend la moitié du prix, annonce à l'écran quand on change de territoire. Les claims par chunk ne servent plus qu'aux zones d'événement. En place sur practice le 25/11 à 09:53.
• **Le serveur practice devient un vrai serveur HCF.** Fiche « Orbit HCF (OrbitHCF) » (remplace « OrbitPractice et mode HCF ») + listes des commandes admin et joueurs : serveur 100 % HCF sur un monde neuf avec Nether et End, spawn, routes et KOTH construits par `/hcf generer`, bordure, argent (`/balance`, `/pay`, `/eco`), banque et chat de faction (`/f deposit`, `/f withdraw`, `/f chat`), boutique en panneaux au spawn, ascenseurs, annonce [FD], combat 1.8 repris dans OrbitHCF-Combat. OrbitPractice mis de côté (`plugins\_desactives`). En place sur practice le 25/11 à 09:46.
• **Survie : grosse mise à jour.** Fiche « OrbitSurvie (Survie) » + listes des commandes admin et joueurs : grades achetés en crédits (`/grade`), contrats du jour (`/contrats`), métiers (`/metiers`), classements du mois avec lots (`/classement`), événements automatiques (`/evenement` : pêche, largage, boss), duels avec mise, paris et tournoi du samedi (`/duel`, `/pari`, `/tournoi`), échoppes de joueurs (`/echoppe`), points de retour et `/tpa`, monde ressources remis à zéro chaque mois (`/ressources`), armoire de rattrapage pour les anciens joueurs. Chargé sur la survie le 25/11 à 08:52.
• **ORBIT TV : tunnel du son.** Fiche « ORBIT TV (OrbitTele) » : les joueurs hors du PC du serveur entendent aussi les télés (tunnel https ouvert par le plugin, lien donné par `/tele son`) ; le partage d'écran reste réservé au PC du serveur sauf réglage `web.diffusion-a-distance`.
• **ORBIT TV : son du partage d'écran.** Fiche « ORBIT TV (OrbitTele) » + listes des commandes admin du lobby et des joueurs : nouvelle commande `/tele son` (page d'écoute dans le navigateur, volume selon la distance à la télé), réglage « Son » de la page de diffusion, pages web habillées à la DA Orbit MC. Chargé sur le lobby le 25/11 à 06:49.
• **Création du guide staff ORBIT MC** (demande de Zazou, même format que le guide d'ORBIT ARK). 8 rubriques : Démarrage, Outils du staff (OrbitStaff), Plugins du réseau, Modes de jeu, Casino et arcade, Commandes admin et joueurs, Procédures, Maintenir ce guide. Une fiche par plugin ORBIT (OrbitDevCore, OrbitStaff, OrbitPackRelay, OrbitSurvie, OrbitExtraction, OrbitPractice, OrbitMMO, OrbitTycoon, OrbitVol, OrbitCasino, OrbitBornes, OrbitArene, OrbitChasseurs, OrbitRoyale, OrbitTele), les listes complètes des commandes admin, et les procédures courantes. Commandes relevées dans le code des plugins le 25/11.
