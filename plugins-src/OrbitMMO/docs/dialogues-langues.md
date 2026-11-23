# Dialogues des PNJ : boîte, carte, portraits et langues (23/11/2026)

Demandes de Zazou :

1. une interface pour les dialogues des PNJ, sous-titrée, et une option qui traduit les dialogues
   pour les joueurs qui ne sont pas français (choix : boîte **en haut, centrée** ; **anglais
   seulement** en plus du français) ;
2. après essai : la phrase des PNJ de ville partait dans le chat, cachée derrière leur menu. Elle
   doit s'écrire dans l'interface, avec « un modèle visuel » du PNJ à droite quand on lui parle.

## Ce que voit le joueur (avec le pack)

- **PNJ de quête** : boîte de dialogue en haut de l'écran. Nom dans le bandeau, 3 lignes qui
  s'écrivent lettre par lettre, aide en bas à droite, **portrait de celui qui parle dans une case
  à droite**. Clic gauche (ou `/quetes passer`) : finit la page, puis passe à la suivante.
- **PNJ de ville** (forgeron, alchimiste, prêtre, runiste, hôtel des ventes, gardiens du Pilier) :
  leur menu s'ouvre avec, **à sa droite, la carte du PNJ** : portrait, nom, et la phrase qu'il
  dit. La carte reste tant que le joueur navigue dans les menus du PNJ, et disparaît quand il les
  ferme. Une nouvelle phrase à chaque clic sur le PNJ.
- Sans le pack : tout passe dans le chat, comme avant.

## Portraits

Un portrait est le visage du modèle du jeu, agrandi (48 × 60), dessiné par `tools\gen-hud.ps1` à
partir des textures du jeu : villageois selon son métier, sorcière, golem, squelette noir, et les
neuf apparences de joueur fournies avec le jeu.

Dans les fichiers de `quests\`, pour un PNJ :

- `visage: kai` (mannequins) : alex, ari, efe, kai, makena, noor, steve, sunny ou zuri. Le corps
  du PNJ dans le monde **et** son portrait prennent cette apparence. Sans `visage` : alex.
- `portrait: <clé>` force le portrait ; `portrait: aucun` le retire.
- Sans rien, le portrait vient du type de l'entité (villageois et son `metier`, sorcière, golem…).
- Avec `skin: <pseudo>` (apparence d'un vrai joueur), pas de portrait : on ne peut pas le dessiner.

Visages donnés le 23/11 (mon choix, à changer librement) : Maëlis = kai, Téo = ari, Brune = noor,
Marqué inconnu = zuri, Varenne = steve, Orsan = sunny. Avant, les six étaient tous « alex ».

Ajouter un portrait : une ligne de dessin dans `gen-hud.ps1` et sa clé à la fin de `$portraits`,
puis la même clé à la fin de `KEYS` dans `hud\Portraits.java` (même ordre des deux côtés).

## Comment c'est fait

- `quest\Dialogue.java` : pages, machine à écrire, temps de lecture, case du portrait.
- `hud\DialogueBox.java` : composition de la boîte (titre d'une barre de boss blanche, rendue
  invisible par le pack).
- `hud\NpcCard.java` : la carte, dessinée dans le titre du coffre par `hud\MenuSkin.java` ;
  `ui\Menu.java` la demande à `Hud.npcCard(joueur)` ; `ui\MenuListener.java` la retire à la
  fermeture ; `floor\FloorService.java` la pose au clic sur le PNJ (phrases FR et EN).
- `hud\Portraits.java` : clés des portraits et leurs glyphes.
- `hud\FontWidths.java` : largeur des caractères, coupe des lignes.
- Pack : `dialog_frame.png`, `dialog_portrait.png`, `npc_card.png`, `portrait_*.png`, polices
  `orbitmmo:txt<n>` (police du jeu abaissée de n pixels : le jeu refuse de remonter un texte, on
  ne peut que l'abaisser, d'où une police par ligne) et `src\main\resources\font-widths.txt`.

Réglages dans le code : `DialogueBox.LINES` (3), `TEXT_WIDTH` (220), `Dialogue.TYPE_SPEED`
(2 caractères par tick), `NpcCard.LINES` (7), `NpcCard.TEXT_WIDTH` (94).

La carte fait 112 px de large à droite du menu : il faut un écran d'au moins 416 px d'interface,
ce qui est le cas dès 1280 × 720 avec l'échelle d'interface automatique.

## Les langues

- Français et anglais. Par défaut la langue suit celle du jeu du joueur : français si le jeu est
  en français, anglais pour toutes les autres.
- Le joueur change par le bouton « Langue / Language » (case 24 du menu F, case 33 du journal de
  quêtes) ou par `/langue [fr | en | auto]` (alias `/language`, `/lang`).
- Traduit : noms et titres des PNJ, répliques, dialogues et narration des quêtes, noms, résumés
  et objectifs des quêtes, objets de quête, suivi à l'écran, journal, phrases des PNJ de ville.
  Les autres menus du jeu restent en français.

### Écrire une traduction

Dans les fichiers de `quests\`, chaque texte peut avoir sa clé jumelle `-en` : `nom-en`,
`titre-en`, `repliques-en`, `resume-en`, `intro-en`, `offre-en`, `fin-en`, `texte-en`,
`dialogue-en`, `apres-en`, `objet-en`, `description-en`.

- Une liste `-en` doit avoir autant de lignes que la liste française, dans le même ordre ; sinon
  elle est ignorée et le journal du serveur écrit « Traduction ignorée ».
- Les répliques gardent leur préfixe (`maelis: …`, `joueur: …`).
- Un texte sans jumelle reste en français pour tout le monde.
- `/quetes admin recharger` relit les fichiers ; le journal donne le nombre de textes traduits.

Les phrases des PNJ de ville sont dans `floor\FloorService.java` (`LINES` et `LINES_EN`, même
ordre).

Le fichier installé (`instances\reborn\plugins\OrbitMMO\quests\lumiere-depart.yml`) n'est jamais
écrasé par le jar : une traduction ou un visage ajouté dans la source doit aussi y être recopié.

## À savoir

- Tout changement du pack force les joueurs à le retélécharger à la connexion. Si le tunnel
  Cloudflare du pack est arrêté, ils n'ont plus ni HUD, ni boîte, ni carte.
- Sauvegarde d'avant : `MinecraftDev\backups\OrbitMMO-avant-dialogues-langues`.
