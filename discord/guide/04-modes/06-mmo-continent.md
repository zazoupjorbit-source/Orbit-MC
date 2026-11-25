---
titre: Orbit MMO : /continent
emoji: 🗺️
resume: Le monde ouvert : lieux, téléportation, pré-génération, capitales, sites, ambiance, paysage
mots: continent, lieux, tp, pregen, apercu, capitale, capitales, site, sites, quetes, ambiance, aubergistes, paysage, generer, ecarts, plan, aureval, morneval
maj: 2026-11-25
---
Permission `orbitmmo.admin`, serveur `reborn`. Doc complète : `plugins-src\OrbitMMO\docs\continent.md`.
⚠️ **Règle** : `ecarts` avant tout `generer`, et une sauvegarde. `generer confirmer` **écrase** les retouches faites à la main.

**▸ Se repérer**
• `/continent info` — région, biome, lieu le plus proche.
• `/continent lieux` — capitales, villes, villages, donjons.
• `/continent tp <lieu | x z>`
• `/continent apercu <lieu | x z> [taille] [pas] [oblique]` — image PNG du terrain.
• `/continent pregen [stop|etat|refaire]` — génère tous les chunks à l'avance.

**▸ Capitales** (Auréval, Morneval ; réglages `capitales.yml`)
• `/continent capitale` — liste et état.
• `… capitale <nom> plan` — image du plan · `ecarts` — comparaison au monde (rien n'est modifié).
• `… capitale <nom> generer [confirmer]` — construit.
• `… capitale <nom> pnj` — repose PNJ et points, sans toucher un bloc.
• `… capitale <nom> tp [point]` · `points` · `capitale recharger` · `capitale stop`

**▸ Sites** (villages, donjons, camps, ruines, cratères, lieux-dits ; `sites.yml`)
• `/continent site liste <type>` · `site proche`
• `… site <id> plan|ecarts|generer [confirmer]|tp [point]|points`
• `… site tout ecarts [type]` · `site tout generer [type]` — tous à la suite (les sites retouchés sont laissés).
• `… site recharger` · `site stop`

**▸ Vie du monde**
• `/continent quetes [poser [confirmer]|retirer]` — points de quête dessinés par les sites.
• `/continent ambiance etat [lieu]` · `essai <lieu> [point] [secondes]` · `route` · `recharger` — gardes, villageois, bétail, voyageurs (`ambiance.yml`).
• `/continent aubergistes poser|retirer` — un aubergiste (cristal de téléportation) par capitale et village.
• `/continent paysage etat|compter|essai [lieu|x z] [rayon]|lancer|refaire|stop` — bosquets, étangs, ruines, champs entre les sites.
