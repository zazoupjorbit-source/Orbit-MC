# Objets d'Orbit MMO — refonte du 24/11/2026

Types d'armure, niveau d'objet, sets, liaison et garde-robe. Code : `src\main\java\fr\orbit\mmo\item\`
(`ArmorType`, `Loot`, `GearSet`, `SetRegistry`, `Binding`, `Wardrobe`) et `ui\` (`ArmoryMenus`, `WardrobeMenus`,
`ProfileMenu`, `MarketMenus`). Icônes : `tools\gen-items.ps1` + `tools\hd\ArmorIcons.cs`.

**Installé sur reborn le 24/11/2026 à 11:05** (jar commun : objets + tenues 3D + paysage ; démarrage sans erreur, « 3 sets d'équipement chargés », `/mmoadmin sets` vérifié par la console). Rien n'est encore essayé en jeu (voir « À essayer » en bas).

---

## 1. Types d'armure

| Type | Classes | Armure (× plaques) | Bonus propres (casque · plastron · jambières · bottes) | Réserve de bonus aléatoires |
|---|---|---|---|---|
| **Tissu** | Mage, Prêtre, Astromancien | 40 % | ressource · soins · PV · ressource | ressource, soins, dégâts, critique, dégâts critiques, PV, vitesse |
| **Cuir** | Druide, Chasseur | 60 % | critique · esquive · esquive · vitesse | esquive, critique, dégâts critiques, vitesse, vol de vie, dégâts, PV |
| **Mailles** | Chaman, Lancier, Scalde | 80 % | critique · dégâts critiques · esquive · vitesse | critique, dégâts critiques, esquive, dégâts, PV, dégâts subis, vitesse |
| **Plaques** | Guerrier, Paladin | 100 % | critique · PV · dégâts subis · vitesse | comme avant la refonte (PV, dégâts subis, esquive…) |

- Moins d'armure = bonus propre plus fort (facteur 0,85 tissu, 0,70 cuir, 0,60 mailles, 0,50 plaques ; × part de la pièce).
- **Chasseur passé de mailles à cuir** (choix de Zazou du 24/11, `classes/chasseur.yml`). Les mailles sont portées par les trois classes ajoutées le 24/11 : Chaman, Lancier, Scalde (`docs/classes-mailles-talents.md`).
- Le type de la classe vient de la clé `armure:` de son YAML (`ClassDef.Armor`). **Une classe ne porte que son type** :
  une pièce d'un autre type mise sur soi revient dans le sac au plus tard 1 s après (`Binding.checkWorn`, appelé quand
  l'empreinte de l'équipement change, `Progression.tickBoosts`, et 2 s après la connexion).
- Noms par palier : tissu « de lin, laine, soie, soie d'électrum, tisse-cristal, soie d'ombre, tisse-astral, tisse-orbite » ;
  cuir « de peau, cuir tanné, clouté, d'électrum, cristallin, d'obsidienne, astral, d'orbitium » ; mailles et plaques
  « de recrue … d'orbitium ». Pièces : Capuche/Robe/Jupe/Chaussons, Coiffe/Veste/Jambières/Bottes, Camail/Haubert/Chausses/Solerets,
  Casque/Plastron/Jambières/Bottes.
- Matériau vanilla : tissu et cuir = cuir teint (couleur par palier), mailles = cotte de mailles, plaques = comme avant.
- Butin : les pièces d'armure qui tombent sont du type de la classe du joueur. L'armurerie de Torvald montre d'abord le
  rayon du type de la classe ; les 3 autres types s'achètent (boutons en bas à droite) mais ne se portent pas.

### Conversion à la connexion (`Loot.migrate` → `Loot.upgrade`)
Toute armure du nouveau format sans type devient **plaques** (tout était porté par des Guerriers), avec ses clés de tenue
et son icône HD. Le butin de boss (« Butin de boss » dans la description, ou ancien type `lab`) devient **lié quand ramassé**
et se lie tout de suite au joueur qui l'a, avec +5 de niveau d'objet. Le reste devient **lié quand équipé** (pas lié tant
qu'il n'est pas porté). Les anciens objets d'avant les raretés passent d'abord par `Loot.adopt` comme avant.

## 2. Clé de tenue (contrat avec le paquet `outfit\`, session « tenues 3D »)

Sur chaque pièce d'armure du MMO (PDC, namespace `orbitmmo`) :

| Clé | Type | Valeur |
|---|---|---|
| `orbitmmo:outfit` | STRING | apparence **affichée** : `<type>_<palier>`, type ∈ `tissu`, `cuir`, `mailles`, `plaques`, palier 0 à 7 (ex. `plaques_3`). Un set peut déclarer une apparence propre (`apparence:` dans `sets.yml`) ; aucun des 3 sets d'exemple ne le fait. |
| `orbitmmo:outfit_base` | STRING | apparence d'origine de la pièce (la garde-robe ne change que `outfit`) |
| `orbitmmo:armor_type` | STRING | type de la pièce (`plaques`…) |
| `orbitmmo:tier` | INTEGER | palier 0-7 (déjà là avant) |

Une apparence de set commence toujours par `set_` (`SetRegistry` ajoute le préfixe s'il manque) ; si le paquet `outfit` ne la
connaît pas, il retombe sur `armor_type` + `tier`, que toute pièce d'armure de set porte (les armes et boucliers n'ont ni
`outfit` ni `armor_type`). L'emplacement se lit sur la case
portée (casque, plastron, jambières, bottes). Le plugin ne touche pas au composant EQUIPPABLE.

## 3. Niveau d'objet

`Loot.itemLevel(objet)` = **20 + 20 × palier** (recrue 20 → orbitium 160)
\+ **rareté** (Commun 0, Inhabituel 4, Rare 8, Épique 13, Légendaire 20)
\+ **niveau dans le palier** (niveau requis − niveau du palier, 0 à 10)
\+ **3 par amélioration** (+1 à +10 chez Torvald, donc jusqu'à +30)
\+ **bonus de l'objet** (`orbitmmo:ilvl_bonus` : butin de boss +5, sets via `bonus-niveau-objet`).

Affiché sur chaque arme, armure et bouclier (« Niveau d'objet : 112 ») et sur la fiche [Maj + F] : ligne dans l'identité
(case 4) et nouvelle case 51 « Niveau d'objet » (moyenne, niveau de chaque pièce, sets portés).
Moyenne (`Loot.averageItemLevel`) : arme active + 4 pièces d'armure, + main secondaire pour une classe ou spé à bouclier ;
un emplacement vide compte 0.

**Minimum pour entrer** (`config.yml`) — réglable, **0 = pas de minimum (réglage actuel)** en attendant le choix de Zazou :
```yaml
niveau-objet:
  pilier: 0   # gardien du Pilier dans la capitale + portails de donjon du continent (pas entre deux étages)
  raid: 0     # pas encore de raid : clé prête (Loot.itemLevelGate(plugin, p, d, "raid"))
```
Les admins (`orbitmmo.admin`) passent toujours. Repères : ensemble Commun d'acier ≈ 60, Rare d'électrum +5 ≈ 103, Épique d'orbitium +10 ≈ 203.

## 4. Sets (`sets.yml`)

Fichier intégré au jar ; `/mmoadmin sets exporter` l'écrit dans `plugins\OrbitMMO\sets.yml` qui le remplace alors
(comme les classes) ; `/mmoadmin reload` le relit. Format complet en tête du fichier
(`src\main\resources\sets.yml`). Un set = 5 pièces parmi casque, plastron, jambieres, bottes, arme, bouclier ;
bonus à 2, 4 et 5 pièces (clés de bonus des talents : degats, crit, critdeg, pv, armure, esquive, blocage, vol-de-vie,
vitesse, ressource, epines, soins, degats.<sort>…), comptés une fois par emplacement porté, si le niveau requis est atteint
(`Loot.setCounts`, ajoutés par `Loot.addGearMods`). Sources : `butin: [{boss: "*"|id, chance}]` (chaque participant)
ou `[{etages: "2-6", chance}]` (chaque monstre tué dans ces étages du Pilier) ; sans butin = artisanat (métiers à venir).

Sets d'exemple (plaques) :

| Id | Nom | Palier · rareté | Origine · liaison | 5e pièce | Bonus 2 / 4 / 5 | Source |
|---|---|---|---|---|---|---|
| `garde_du_pilier` | Garde du Pilier | Cuivre · Rare | donjon · équipé | bouclier | +4 % PV / −3 % dégâts subis, +4 % blocage / +10 % épines, +4 % vitesse | monstres des étages 2-6, 0,4 % |
| `rempart_astral` | Rempart astral | Électrum · Épique, +5 niv. d'objet | boss · ramassé | bouclier | −3 % dégâts subis / +6 % PV, +6 % blocage / +6 % dégâts, +2 % vol de vie | tout boss du Pilier, 12 % par participant |
| `harnois_de_torvald` | Harnois de Torvald | Acier · Rare | artisan · équipé | espadon | +10 % dégâts crit. / +4 % dégâts / +3 % crit., +5 % vitesse | aucune (artisanat ; `/mmoadmin set`) |

La description d'une pièce liste le set et ses bonus ; la fiche [Maj + F] (case 51) les colore selon les pièces portées.

## 5. Liaison

- **Lié quand ramassé** : butin de boss (objet du dernier coup, objet Rare+ de chaque participant, pièces de set « boss »).
  Lié dès qu'il arrive dans le sac.
- **Lié quand équipé** : tout le reste (butin des monstres, armurerie, sets donjon/artisan). Lié quand il est porté
  (armure, bouclier en main secondaire, arme active).
- Lié = `orbitmmo:owner` (UUID) + `orbitmmo:bound_name` ; clé `orbitmmo:bind` = `ramasse` | `equipe`.
- Un objet lié : **refusé à l'hôtel des ventes** (`Market.cannotSell`), ne se jette pas, ne se ramasse pas par un autre,
  ne se pose pas dans un coffre / tonneau / entonnoir / cadre / support d'armure (`Binding`). Le coffre de l'Ender reste
  permis (personnel). **Torvald le rachète** comme avant. À la mort, rien ne tombe (inventaire gardé, inchangé).

## 6. Garde-robe

- Chaque pièce portée débloque son apparence d'origine pour son emplacement (`PlayerData.looks`, sauvé sous `looks`,
  « plaques_3:chest »).
- Menu : fiche [Maj + F] → case 52 « Garde-robe » (`ui\WardrobeMenus`) : les 4 pièces portées (clic = choisir parmi les
  apparences débloquées du même type), « Tenues complètes » (une apparence sur toutes les pièces qui l'ont débloquée),
  « Tout remettre d'origine ». Seule la clé `orbitmmo:outfit` et l'icône changent ; les statistiques, le nom et le
  niveau d'objet non. La description indique « Apparence : Plaques · Cristal (garde-robe) ».
- Gratuit, partout. Le fond de la fiche a deux nouvelles cases (51 or, 52 lavande) dans `tools\gen-hud.ps1`.

## 7. Icônes HD

`tools\gen-items.ps1` (appelé par `gen-pack.ps1`, juste avant le HUD ; peut tourner seul) compile `tools\hd\ArmorIcons.cs` :
64 textures **64 × 64** `pack\assets\orbitmmo\textures\item\armure\<type>_<piece>_<v>.png` (4 types × head/chest/legs/feet
× 4 variantes ; variante = palier ÷ 2, `Loot.PACK_TIER`), modèles `models\item\armure\`, et cas `custom_model_data`
« orbitmmo:armure/<type>_<piece>_<v> » ajoutés aux 28 définitions vanilla (cuir, mailles, cuivre, fer, or, diamant,
netherite × 4 pièces), avec l'apparence vanilla en repli (sans le pack, rien ne casse). Si un autre script a déjà écrit
une de ces définitions avec un `select` sur custom_model_data, les cas sont ajoutés à sa liste.
Matières : tissu (trame, plis, galon), cuir (grain, coutures), mailles (anneaux), plaques (métal poli, rivets, visière).
Le rendu **porté** (tenue 3D) est l'affaire du paquet `outfit\`.

## 8. Commandes admin

- `/mmoadmin sets` (liste) · `/mmoadmin sets exporter`
- `/mmoadmin set <id> [casque|plastron|jambieres|bottes|arme|bouclier|tout] [joueur]` — donne des pièces (liées si le set
  est « lié quand ramassé »).
- `/mmoadmin reload` relit aussi les sets.

## 9. À essayer en jeu (rien n'a été vu en jeu)

1. Connexion d'un Guerrier existant : armures converties en plaques (description « Armure de plaques », niveau d'objet,
   « Lié · pseudo » sur ce qui est porté ; butin de boss lié tout de suite), icônes HD des pièces.
2. Fiche [Maj + F] : ligne « Niveau d'objet » (case 4), case 51 (moyenne, détail, sets), case 52 « Garde-robe » ; fonds
   des cases 51-52 bien dessinés.
3. Torvald → Armurerie → Armures : rayon plaques, icônes HD, boutons tissu/cuir/mailles ; acheter une pièce en tissu puis
   l'enfiler → elle revient dans le sac avec un message.
4. Mettre une pièce neuve (« Lié quand équipé ») : message « Objet lié », puis tenter de la vendre à l'HDV (refus),
   de la jeter (refus), de la poser dans un coffre (refus) ; Torvald la rachète.
5. `/mmoadmin set garde_du_pilier tout` : 5 pièces, bonus actifs à 2/4/5 pièces dans la fiche (couleur verte) et dans
   « Bonus » ; `/mmoadmin set rempart_astral tout` : pièces déjà liées.
6. Garde-robe : porter deux ensembles de paliers différents, puis changer l'apparence d'une pièce et d'une tenue complète,
   vérifier que statistiques et niveau d'objet ne bougent pas ; « Tout remettre d'origine ».
7. Tuer un boss du Pilier : objet Rare+ lié, parfois une pièce du Rempart astral ; objet du dernier coup lié.
8. Mettre `niveau-objet.pilier: 100` dans `plugins\OrbitMMO\config.yml`, `/mmoadmin reload`, essayer d'entrer par le
   gardien avec un joueur non admin (refus), puis remettre 0.
9. Avec la session tenues 3D : vérifier que la tenue suit `orbitmmo:outfit` (et la garde-robe).

## Points ouverts (à décider par Zazou)

- ~~Quelle classe porte les mailles~~ : réglé le 24/11 (Chaman, Lancier, Scalde).
- Niveau d'objet minimum du Pilier (et des raids) : aujourd'hui 0.
- Garde-robe payante ou réservée aux villes ?
- Autres sets (tissu, cuir, mailles) quand d'autres classes seront jouables ; les sets « artisan » attendent les métiers.
