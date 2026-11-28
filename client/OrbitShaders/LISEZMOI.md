# Orbit Shaders (v1.0, 26/11/2026)

Pack de shaders d'ORBIT pour **Iris** (Minecraft 26.3), pensé pour le MMO : code original, dans l'esprit des grands
packs « réalistes doux », avec la DA d'Orbit MC (bleu + or).

Un pack de shaders est **côté joueur** : le serveur ne peut pas l'imposer comme le pack de textures. Chaque joueur
installe Fabric + Sodium + Iris, puis met `OrbitShaders-v1.0.zip` dans son dossier `shaderpacks`.

## Ce qu'il fait

- Ombres douces du soleil et de la lune (carte d'ombre déformée, 8 échantillons), feuillage traversé par la lumière.
- Lumière dorée le jour, ambiance bleue la nuit et dans les ombres ; torches chaudes ; torche tenue en main qui éclaire.
- Rayons de lumière (soleil à travers les arbres et les fenêtres), plus forts au lever et au coucher.
- Ciel dessiné : dégradé bleu, horizon doré au lever et au coucher, soleil rond, étoiles qui scintillent et tournent,
  **nébuleuse ORBIT** bleu et violet avec des filaments dorés la nuit.
- Eau : vagues, reflet du ciel selon l'angle, reflet doré du soleil, eau bleu-vert transparente.
- Herbes, fleurs, cultures, feuilles et lianes qui bougent au vent (ombres comprises).
- Blocs lumineux qui brillent (lave, pierre lumineuse, flammes des torches…), halo (bloom).
- Brume du ciel, brouillard de bord de carte, sous l'eau, dans la lave et la neige poudreuse.
- Image : courbe filmique, teinte ORBIT (ombres légèrement bleues, lumières légèrement dorées), vignette.
- Menu Iris en français, profils Bas / Moyen / Haut / Ultra. Nether et End gérés (sans soleil).

## Fichiers

- `shaders/programs/` : tout le code ; `shaders/lib/` : réglages, éclairage, ciel, ombres, vent.
- `shaders/*.vsh|fsh`, `world-1/`, `world1/` : fichiers d'appel **générés** par `outils/generer-programmes.ps1`
  (ne pas les modifier à la main).
- `shaders/block.properties` : quels blocs bougent, brillent, sont de l'eau.
- `outils/TestShaders.java` : compile tous les programmes sur la carte graphique, sans lancer le jeu, et dessine
  `apercu/moments.png` (ciel et sol à 6 moments de la journée, passés par la vraie passe finale).

## Construire, tester, installer

```powershell
powershell -ExecutionPolicy Bypass -File client\construire-shaders.ps1 -Installer
```
(`-Installer` copie le zip dans `%APPDATA%\.minecraft\shaderpacks` ; le zip est aussi dans `client\dist\`, hors git.)

Test sans le jeu (bibliothèques LWJGL 3.3.3 du Minecraft installé) :
```powershell
$L = "$env:APPDATA\.minecraft\libraries\org\lwjgl"
$cp = (@('lwjgl','lwjgl-glfw','lwjgl-opengl') | ForEach-Object { "$L\$_\3.3.3\$_-3.3.3.jar;$L\$_\3.3.3\$_-3.3.3-natives-windows.jar" }) -join ';'
java -cp $cp client\OrbitShaders\outils\TestShaders.java client\OrbitShaders\shaders -apercu client\OrbitShaders\apercu\moments.png
```

## État

- 26/11/2026 : 67 programmes × 2 (options par défaut, options oui/non coupées) compilent et se lient sur une
  RTX 4060 (pilote NVIDIA 621). Aperçu du ciel validé à l'œil.
- **Pas encore vu en jeu** : Iris n'est pas installé sur le PC de Zazou. À vérifier en premier : ombres (acné,
  décalage), eau, nuages (dessinés depuis la couleur des sommets), contour du bloc visé, lumière des grottes.
