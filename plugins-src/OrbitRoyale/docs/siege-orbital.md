# SIÈGE ORBITAL (plugin OrbitRoyale)

Bataille de tours 1v1 à cartes et plasma, dans l'arcade cachée de La Station (famille **D. Cartes** de
`MinecraftDev/docs/arcade-jeux.md`). Mécanique de jeu de cartes en temps réel, mais tout est ORBIT : nom, unités,
sprites dessinés par `tools/RoyalePackGen.java`, aucun personnage ni visuel repris d'un autre jeu.

## Où et comment
- **Lobby seulement** (`server/plugins`). `softdepend: [OrbitCasino]`.
- PNJ « Commandante · Siège Orbital » dans la salle d'arcade : monde `casino`, `12.5 64 389.5`, yaw 0
  (réglable : `pnj.position` dans `config.yml`). Clic droit = jouer ; **accroupi + clic droit** = deck.
- Arènes générées par code dans le monde vide `royale` (créé par le plugin), une par place : x = 160 × place, y = 100.
  8 places par défaut (`arenes`).
- Pack de ressources (sprites des unités bleues/rouges + icônes de cartes) servi sur le port **8798**, ajouté par-dessus
  celui du lobby à la connexion (`replace = false`, pas obligatoire). Sans le pack, les unités s'affichent en
  œufs d'apparition / objets du jeu de base, avec un contour lumineux bleu ou rouge.

## Déroulé d'une partie
1. Clic sur le PNJ → file d'attente. Si un autre joueur attend déjà → **duel** (camp bleu contre camp rouge).
   Sinon, au bout de `partie.attente-bot` s (8 s) → **bot** (bouton « Bot tout de suite » = `/siege bot`).
2. Le joueur marche sur un **plafond de barrières invisible** à 9 blocs au-dessus de toute l'arène (mode Aventure,
   rebord invisible autour ; départ au-dessus de son camp),
   son inventaire est mis de côté (copie aussi dans `plugins/OrbitRoyale/etats/<uuid>.yml` en cas d'arrêt brutal).
3. Barre d'objets : cases 1-4 = la main (la quantité de l'objet = son coût en plasma), case 6 = carte suivante,
   case 9 = abandonner (deux clics). On choisit une carte puis on **clique en visant le sol** : un cercle de
   particules et un **fantôme de l'unité** (visible seulement par lui) montrent où elle arrive (vert = prêt, violet =
   pas assez de plasma). Un clic trop loin (camp adverse, faille, tour) recale la carte sur l'endroit permis le plus
   proche. Ligne de pose bleue/rouge juste avant la faille ; « ✔ … posée » dans la barre d'action.
4. Barres du haut : temps + étoiles, et jauge de **plasma** (0 à 10, +1 toutes les 2,8 s, ×2 la dernière minute
   et en prolongation). Départ à 5.
5. 3 minutes (`partie.duree`). Chaque Tourelle détruite = 1 étoile, le Bastion = 3 étoiles et fin immédiate.
   Égalité d'étoiles → prolongation d'1 min : la prochaine étoile gagne ; sinon les tours les moins abîmées
   gagnent ; égalité parfaite = nul.
6. Fin : titre, trophées, Orbit Coins, retour devant le PNJ, état rendu, boutons [Rejouer] [Classement] [Deck].
   Abandon, déconnexion ou téléportation hors de l'arène = défaite (avant le « 3-2-1 » : partie annulée).

## L'arène
19 × 37 cases. Faille du Néant au milieu (z = -1..1), 2 ponts en x = ±6. Par camp : **Bastion** (5×5, balise,
2 400 PV, 55 dégâts, dort tant qu'il n'est pas touché ou qu'une Tourelle n'est pas tombée) et 2 **Tourelles** (3×3, 1 400 PV, 45 dégâts).
Les tours tirent sur tout, volants compris. Une tour détruite devient un tas de gravats.
Pose : sur son demi-terrain ; si la Tourelle adverse d'une voie est tombée, on peut aussi poser des unités dans
la « poche » de cette voie (5 cases au-delà de la faille). Les sorts se lancent partout.

## Les 12 cartes (coût en plasma)
| Carte | Coût | Type | Déblocage |
|---|---|---|---|
| Sentinelle | 3 | mêlée, robot de garde | d'office |
| Canonnier | 3 | distance, anti-volants | d'office |
| Essaim d'Orbitos | 2 | 4 petits Orbitos rapides | d'office |
| Chasseur stellaire | 4 | vaisseau volant, anti-volants | d'office |
| Frappe orbitale | 4 | sort de zone (35 % sur les tours) | d'office |
| Tourelle à plasma | 4 | bâtiment défensif 30 s | d'office |
| Onde de givre | 2 | sort : gel 2,5 s | 30 trophées de record |
| Drones de garde | 3 | 3 volants | 60 |
| Colosse du Néant | 5 | tank, ne vise que les tours/bâtiments | 100 |
| Chevalier de l'Aube | 4 | mêlée rapide et forte | 150 |
| Mage de l'Ombre | 5 | distance, dégâts de zone | 220 |
| Portail d'Orbitos | 5 | bâtiment : un Orbito toutes les 4 s | 300 |

Stats dans `Card.java` (`/siege cartes` les affiche). Deck de 8 (ou toutes les cartes débloquées s'il y en a
moins : 6 au départ). Le **record** de trophées débloque : on ne reperd jamais une carte. **Jamais en payant.**

IA des unités : chacune vise l'ennemi le plus proche à portée de vue (5,5 cases), sinon la Tourelle de sa voie
puis le Bastion ; les unités au sol passent par les ponts, les volantes au-dessus de la faille ; elles se poussent
pour ne pas s'empiler.

## Le bot
`Bot.java`. Il laisse la menace passer la faille pour que ses tours aident, puis défend (sort sur un groupe de 3+, sinon une unité qui peut toucher la
menace, posée à portée de ses tours), sans en rajouter si ses défenseurs suffisent ; il accompagne ses unités qui passent la faille (contre-attaque), attaque quand il a 8-9,5 de plasma (gros tank derrière son Bastion, unités
rapides au pont, sur la voie où la Tourelle adverse est la plus faible) et finit une tour à la Frappe.
Niveau 1 à 4 selon les trophées du joueur (< 60, < 150, < 300, au-delà) : temps de réaction de ~2 s à ~0,5 s,
erreurs de placement au niveau 1. Son deck : cartes débloquées autour des trophées du joueur + 40.

## Récompenses (config `recompenses`)
- Trophées : +30 victoire, −15 défaite (jamais sous 0), 0 nul.
- Orbit Coins via `OrbitCasino#wallet().credit(uuid, n, "siege", détail, false)` : 60 victoire / 20 défaite / 30 nul
  + 10 par étoile, **plafond 600 OC par jour et par joueur** (`gain_jour` dans `royale_profils`).
- Tableau des records de l'arcade : déclaré dans `arcade_jeux` (`jeu = 'siege'`, unité « trophées », ordre 40) ;
  `arcade_scores.meilleur` = meilleur nombre de trophées atteint.

## Base (orbit_mc)
`royale_profils` (uuid, nom, trophees, record, victoires, defaites, nuls, deck, jour, gain_jour). Créée au démarrage,
comme `arcade_jeux` / `arcade_scores` (`CREATE TABLE IF NOT EXISTS`).

## Commandes
- `/siege jouer` (depuis l'arcade), `/siege bot`, `/siege deck`, `/siege cartes`, `/siege top`, `/siege quitter`
  (abandon ou sortie de file). Alias `/royale`.
- Admin (`orbitroyale.admin`) : `/siege tp` (devant le PNJ), `/siege pnj` (reposer le PNJ),
  `/siege arene <n> [voir]` (reconstruire une arène), `/siege demo [trophées]` (deux bots s'affrontent,
  sert à vérifier la simulation sans joueur). Chaque fin de partie écrit une ligne « Partie arène N : … » dans le journal.

## Compiler / installer
`powershell -ExecutionPolicy Bypass -File build.ps1 [-NoDeploy]` (compile contre `server/plugins/OrbitCasino.jar`,
génère le pack et l'aperçu `MinecraftDev/maquettes/siege/siege-sprites.png`). Ligne `OrbitRoyale` dans `$Plugins`
de `scripts/commun.ps1` (`Vers = @('server')`).

## Pistes pour enrichir
Niveaux de cartes, émotes, coffres de fin gratuits, replays, mode 2v2, saisons de trophées, animation des sprites
(2 images), tours en modèles du pack au lieu de blocs.

## Vérifié / pas vérifié (24/11/2026)
- Vérifié par la console : compilation, démarrage sans exception, pack servi sur 8798 (HTTP 200, 139 Ko), monde
  `royale` créé, PNJ posé, déclaration `arcade_jeux`. Démos bot contre bot : niveau 4 = 31 et 25 cartes jouées, une
  Tourelle à 47 %, victoire 1-0 au temps ; niveau 1 = 40 cartes chacun mais aucune tour touchée (égalité : les deux bots
  se neutralisent au pont). Une vraie partie de Zazou contre le bot (09:13) : défaite enregistrée, 20 OC versés,
  `arcade_scores` écrit.
- Pas vu par Claude : le rendu en jeu (sprites, lisibilité depuis la plateforme, cercle de visée), le duel entre deux
  joueurs, le menu du deck, la reprise après un arrêt brutal.

### Réglages du 24/11, 09:34
Bot : défense dans la portée des tours, pas de surdéfense, contre-attaque. Après ce changement (démos de 09:30) :
niveau 4 = victoire 1-0 en prolongation ; niveaux 1-2 = départagés aux dégâts des tours dans 2 cas sur 3, mais aucune
tour détruite. Ensuite : tours allégées (1 400 / 2 400 PV, 45 / 55 dégâts), Sentinelle, Canonnier, Essaim, Chevalier
et Colosse un peu renforcés. **Cette dernière version est dans `server/plugins/update/`** (Zazou connecté) : elle
n'a pas encore tourné, ni en démo ni en jeu.

### Retour de Zazou (24/11, ~09:40) et corrections
« Trop loin du terrain, je ne voyais pas bien ; je ne peux pas placer de bloc. » → vue au plafond invisible (9 blocs au
lieu de 21 de haut et 24 en arrière), fantôme de la carte visée, recalage du point de pose, confirmation « ✔ posée »,
aide dans le décompte. Les arènes déjà construites sont refaites automatiquement (test : barrière au centre du plafond).
Version posée dans `server/plugins/update/` (Zazou connecté) : **pas encore chargée ni vue en jeu.**
