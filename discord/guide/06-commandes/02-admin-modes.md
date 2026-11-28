---
titre: Commandes admin : modes de jeu
emoji: ⚔️
resume: Survie, Extraction, Practice, MMO, Tycoon, Vole mon Orbito : toutes les commandes admin
mots: liste, commandes admin, ile, extraction, practice, mmoadmin, continent, quetes admin, guilde admin, tycoon, donner, vol
maj: 2026-11-25
---
**▸ Survie** (`survie.admin`)
```
/ile ecarts                  comparer au plan (AVANT generer)
/ile generer                 remettre l'île comme le plan
/ile tp [point] | points | pnj | reload
/ile argent <pseudo> <montant>   fixer un solde
/base donner <pseudo> [nombre]
/base bypass                 casser dans les bases
/evenement lancer <peche|largage|boss>   lancer un événement
/evenement stop              arrêter l'événement en cours
/tournoi ouvrir | annuler    tournoi de l'arène
/ressources reset oui        recréer le monde ressources
/echoppe supprimer           fermer l'échoppe regardée
```
**▸ Extraction** (`extraction.admin`) — `/extraction` ou `/ext`
```
planque [rayon]              zone sûre ici
spawn add|remove|list        apparitions en raid
extract add <nom> [rayon] [s] | remove <nom> | list
give <spec> [joueur]         arme:/munition:/grenade:/objet:
armes                        arsenal
infecte <type> [nombre] | purge
kit [joueur] [kit] | rang [joueur]
caisses [reset] | carte [generer]
vehicule [type|liste|reset]
evenement <crash|horde|largage> [joueur]
fin <joueur>                 termine son raid
stats [joueur] | pack [joueur] | reload
credits <joueur> <montant>
```
**▸ HCF** (`orbithcf.admin`)
```
/hcf spawn [definir] | bordure | reload
/hcf generer spawn [forcer] | routes | koth <nom> [x z]
/eco give|take|set <joueur> <montant>
/f setdtr|freeze|unfreeze|points <faction> ... | /f bypass
/koth start|stop|list | /sotw start <duree> | /eotw start
/lives give <joueur> <n> | /revive <joueur>
```
<!-- suite -->
**▸ Orbit MMO** (`orbitmmo.admin`)
```
/mmoadmin niveau <joueur> <niveau>
/mmoadmin or <joueur> <+/-montant>
/mmoadmin spe <joueur> <spé>
/mmoadmin ressource | reset <joueur>
/mmoadmin etage <n> | ouvrir <n> | boss
/mmoadmin spawn <monstre> [niveau] | construction
/mmoadmin sets [exporter] | set <id> [pièce|tout] [joueur]
/mmoadmin tenue <id> [joueur] | retirer | soi | info | liste
/mmoadmin classes exporter | reload
/continent info | lieux | tp <lieu|x z>
/continent pregen [stop|etat|refaire] | apercu <…>
/continent capitale <nom> plan|ecarts|generer [confirmer]|pnj|tp|points
/continent site <id> plan|ecarts|generer [confirmer]|tp|points
/continent site liste <type> | proche | tout ecarts|generer [type]
/continent quetes [poser [confirmer]|retirer]
/continent ambiance etat|essai|route|recharger
/continent aubergistes poser|retirer
/continent paysage etat|compter|essai|lancer|refaire|stop
/quetes admin poser <fichier> | point <nom> | supprimer <nom>
/quetes admin points | tp <point> | manquants | liste | recharger
/quetes admin demarrer|terminer <joueur> <quête>
/quetes admin reset <joueur> [quête] | etape <joueur> <quête> <n°>
/quetes admin trainee <joueur> [point|x y z|fin]
/guilde admin dissoudre <guilde> | etage <n> <guilde>
/guilde admin banque <+/-montant> <guilde> | recharger
```
<!-- suite -->
**▸ Tycoon** (`orbittycoon.admin`)
```
/donner <joueur|*> <quoi> [quantité]
/tycoon monnaie <donner|retirer|definir> <joueur> <credit|flux|plasma> <montant>
/tycoon prestige <joueur> <niveau> [outil]
/tycoon test <prestige> [outil|tout] [joueur]
/tycoon equiper [joueur] | equipement donner <outil|tout> [joueur]
/tycoon cle <1-6> [joueur] [nombre]
/tycoon pet donner <espece_rarete> [joueur] | pet niveau <0-100> [joueur]
/tycoon gen donner <joueur> <type> [nombre] | gen liste
/tycoon ile <joueur> <nom|aucune>
/tycoon tuto reset <joueur>
/tycoon vote <joueur> [site] | voteparty [ajouter|definir <n>]
/tycoon generer [zone|tout] [confirmer] | tp <zone>
/tycoon deco | classement | coffres | boutique  [poser|retirer]
/tycoon dome generer [confirmer] | mobzone deco|refaire | iles deco
/tycoon nettoyage | reload
```
**▸ Vole mon Orbito** (`orbitvol.admin`)
```
/vol generer | donner <joueur> <montant> | meteores
/vol evenement <nuit_doree|marchand_noir|geant|stop>
/vol orbito <id> [OR|DIAMANT|ARCENCIEL|METEORE] | liste
/vol pack <joueur>
```
