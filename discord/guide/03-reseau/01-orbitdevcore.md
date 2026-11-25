---
titre: OrbitDevCore (lobby et commun)
emoji: 🧭
resume: Spawn, warps, boussole, cosmétiques, montures du lobby, vote, boutique, mondes de test
mots: orbitdevcore, orbitdev, spawn, setspawn, warp, setwarp, delwarp, boussole, compass, lobby, hub, cosmetiques, monture, vote, boutique, clearlag, devworld
maj: 2026-11-25
---
**Où** : tous les serveurs (le cœur du lobby ; sur les modes, une partie est coupée par la config).

**▸ Ce qu'il fait**
Spawn et warps, la **boussole** de navigation (rangées Modes, Station, et Serveurs de dev pour les admins), les PNJ et anneaux qui envoient vers les autres serveurs, les **cosmétiques** (traînées, auras, chapeaux, dos, ballons, familiers, montures), les préfixes de grade dans le chat (LuckPerms), le français / anglais, le pack du lobby, un clear lag et le mini-jeu feu d'artifice.

**▸ Commandes joueurs**
`/spawn` · `/warp [nom]` (`/warps`) · `/boussole` (`/compass`) · `/lobby` (`/hub`) · `/cosmetiques [catégorie|retirer]` · `/monture [choisir|renvoyer]` · `/boutique` (`/shop`) · `/vote`

**▸ Commandes admin** (`orbitdev.admin`)
• `/spawn <joueur>` — envoie un joueur au spawn.
• `/setspawn` — le spawn à ta position.
• `/setwarp <nom> [icône]` · `/delwarp <nom>`
• `/orbitdev info` — version, nombre de warps et de PNJ, proxy on/off.
• `/orbitdev reload` — relit toute la config.
• `/orbitdev pack` — renvoie le pack du lobby à tous les connectés (après une mise à jour).
• `/orbitdev clearlag` — retire les entités en trop, dit combien.
• `/orbitdev feu <cible|riposte|retirer|serie [n]>` — outils de test du mini-jeu feu d'artifice.
• `/devworld create <nom> [flat|normal|void]` · `/devworld list` · `/devworld tp <nom>` — mondes de test.

**▸ Cosmétiques et casino**
Au lobby, les cosmétiques s'achètent en **Orbit Coins** (boutique du casino). Sur les modes, les cosmétiques sont coupés (`cosmetics.enabled: false`).

**▸ ⚠️ Conflits de commandes**
Sur reborn, `/monture` et `/lobby` sont ceux d'**OrbitMMO** (il s'allume avant OrbitDevCore). Sur la survie, `/spawn` est celui de l'île. Sur le Tycoon, `/boutique` est celle du Tycoon. Forcer l'un ou l'autre : `/orbitdevcore:spawn`, `/orbitmmo:monture`…
