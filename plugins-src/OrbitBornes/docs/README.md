# OrbitBornes : les bornes solo de l'arcade cachée

Famille **B** du contrat `docs/arcade-jeux.md`. Plugin du **lobby seulement** (`server/plugins`), `softdepend: [OrbitCasino]`.

| Jeu | Id (`arcade_jeux`) | PNJ (monde `casino`, y 64, z 411.5, regard nord) | Où on joue | Doc |
|---|---|---|---|---|
| ASTROBLOCS (blocs qui tombent, solo + duel) | `astroblocs` | x = -17.5 | monde `bornes`, bande z = 0 | [astroblocs.md](astroblocs.md) |
| CRISTAUX D'ORBIT (alignements, écran HD) | `cristaux` | x = -13.5 | écran (coffre), le joueur reste dans la salle | [cristaux.md](cristaux.md) |
| DÉFENSE ORBITALE (tir spatial) | `defense` | x = -9.5 | monde `bornes`, bande z = 1000 | [defense.md](defense.md) |
| PULSAR (rythme, blocs musicaux) | `pulsar` | x = -5.5 | monde `bornes`, bande z = 2000 | [pulsar.md](pulsar.md) |

Noms et dessins originaux ORBIT : on reprend des mécaniques connues, jamais un nom, un logo, une musique ou un look.

## Fonctionnement commun
- **Monde `bornes`** : vide, créé par le plugin (nuit fixe, pas de mobs). Chaque jeu assis a sa bande en z, chaque place
  sa colonne en x = 96 × place (12 places par jeu, réglage `places`). Tout ce qu'une partie pose (blocs, entités) est retiré à la fin.
- **Jeux assis** (`SeatedGame`) : comme ORBIT RUSH, le joueur est assis sur un `ItemDisplay` face au mur, ses touches
  arrivent par `PlayerInputEvent` ; descendre est bloqué (`EntityDismountEvent` annulé), pas de dégâts pendant la partie.
  Vol et taille sont rendus à la fin ; l'inventaire n'est jamais touché.
- **Fin de partie** : retour devant le PNJ, titre, ligne de bilan dans le chat avec **[Rejouer]** et **[Classement]**.
  Déconnexion ou téléportation ailleurs (boussole, /spawn…) = partie arrêtée proprement. `/bornes quitter` abandonne.
- **Scores** : `arcade_scores` (meilleur score par joueur, relu chaque minute par le tableau des records de la salle).
  Les 4 jeux se déclarent dans `arcade_jeux` au démarrage (ordre 20 à 23).
- **Orbit Coins** : versés par `OrbitCasino#wallet().credit(...)`, jamais achetables. Plafond **par jour, par joueur et par jeu**
  dans la table `bornes_gains` (réglages `<jeu>.plafond-oc-jour` dans `config.yml`).
- **Pack** (port **8796**) : cristaux, icônes, boutons, drones, vaisseaux, fond d'écran HD et police, générés par
  `tools/BornesPackGen.java` à chaque build (aperçus : `maquettes/bornes-cristaux.png`, `maquettes/bornes-sprites.png`).
  Ajouté par-dessus le pack du lobby (`replace = false`), facultatif : sans lui, CRISTAUX passe en objets ordinaires
  et les drones en blocs colorés.

## Commandes
- `/bornes rejouer` · `/bornes top <jeu>` · `/bornes quitter` · `/bornes reprendre` (CRISTAUX en pause)
- `/bornes jouer <jeu> [mode]` (utilisé par les boutons du chat ; seulement dans la salle d'arcade)
- Staff (`orbitbornes.admin`) : `/bornes etat` (parties en cours, pack, base), `/bornes pnj` (reposer les PNJ), `/bornes tp`.

## Compiler / installer
```
powershell -ExecutionPolicy Bypass -File plugins-src\OrbitBornes\build.ps1 [-NoDeploy]
```
Compile contre `server/plugins/OrbitCasino.jar` et le jar du serveur Paper (titre de l'écran changé sans le rouvrir).
Ligne `OrbitBornes` dans `$Plugins` de `scripts/commun.ps1` (`Vers = @('server')`).

## Tables (base `orbit_mc`)
- `arcade_jeux`, `arcade_scores` : communes à l'arcade (créées aussi par OrbitCasino).
- `bornes_gains (uuid, jeu, jour, gain)` : OC versés aujourd'hui.
- `bornes_cristaux (uuid, nom, etoiles, total)` : étoiles des 15 niveaux de CRISTAUX (`etoiles` = « 3210… »).
