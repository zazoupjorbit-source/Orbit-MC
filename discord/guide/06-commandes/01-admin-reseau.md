---
titre: Commandes admin : staff et réseau
emoji: 🛡️
resume: OrbitStaff (sanctions, mode staff, enquête) et OrbitDevCore (spawn, warps, PNJ, station, mondes)
mots: liste, commandes admin, staff, ban, mute, kick, warn, freeze, panel, stats, orbitdev, station, servernpc, anneau, devworld, setwarp, setspawn
maj: 2026-11-25
---
**▸ OrbitStaff** — tous les serveurs
```
/staff                       mode staff (outils)
/vanish                      invisible / visible
/panel <joueur>              actions sur le joueur
/stats <joueur>              livre de stats
/historique <joueur>         sanctions passées et en cours
/note <joueur> <texte>       note staff
/freeze <joueur> [motif]     geler / dégeler
/invsee <joueur>             inventaire
/endersee <joueur>           coffre de l'End
/catalogue                   objets du mode (admin)
/sanction <joueur>           menu des sanctions prédéfinies
/warn <joueur> <motif>
/mute <joueur> <durée|perm> <motif>
/unmute <joueur> [motif]
/kick <joueur> <motif>
/tempban <joueur> <durée> <motif>
/ban <joueur> <motif>
/unban <joueur> [motif]
/reports                     signalements en attente
/sc [message]                chat staff réseau
/alertes                     couper / remettre ses alertes
/orbitstaff statut|reload
```
<!-- suite -->
**▸ OrbitDevCore** — lobby et tous les serveurs
```
/spawn <joueur>              envoie au spawn
/setspawn                    spawn à ta position
/setwarp <nom> [icône]       crée / déplace un warp
/delwarp <nom>
/orbitdev info|reload        état, recharger
/orbitdev pack               renvoie le pack du lobby à tous
/orbitdev clearlag           retire les entités en trop
/orbitdev feu <…>            tests du feu d'artifice
/devworld create <nom> [flat|normal|void]
/devworld list | tp <nom>
/servernpc create <id> <serveur|monde:nom> [type]
/servernpc remove|tp|tphere <id>
/servernpc server|world|name|profession <id> <valeur>
/servernpc list
/anneau creer <serveur> [rayon] [titre]
/anneau retour <serveur> [titre]
/anneau retirer [monde x y z] | liste
```
**▸ La Station** — lobby
```
/station generer [zone]      met à jour (garde les retouches)
/station ecarts              retouches et plan pas posé
/station remettre <rayon|tout>
/station etancheite          ouvertures sur le vide ?
/station apercu [hauteur]    images PNG
/station regle <clé> <valeur>
/station tp [point] | points | recharger
/station effacer oui         DANGEREUX
```
