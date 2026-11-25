---
titre: OrbitCasino : casino et Orbit Coins
emoji: 🎰
resume: Salle cachée (machines, blackjack, poker, roulette), Orbit Coins, boutique de cosmétiques
mots: casino, orbitcasino, oc, orbitcoins, coins, boutique, titres, top, ration, regles, table, blackjack, poker, roulette, machines, donner, retirer, solde, bouton, entree secrete
maj: 2026-11-25
---
**Où** : lobby, monde `casino`. Permission admin : `orbitcasino.admin`.

**▸ Le casino**
Salle cachée : **entrée secrète** = bouton derrière le comptoir de La Station. 13 machines à sous (gros lot commun), 3 tables de blackjack, roulette européenne (écran HD), 2 tables de poker Hold'em entre joueurs (Nébuleuse 10/20, Supernova 50/100), sans commission. Écrans HD avec le pack du casino (port 8793) ; sans le pack, les boutons du chat suffisent. Hôtesse des règles près de l'arrivée.

**▸ Orbit Coins**
2 000 OC au départ, **ration** de 1 000 OC par jour, 100 OC par 30 min de jeu. **Jamais achetés**, jamais échangés contre de l'argent ni contre la monnaie d'ARK. La boutique vend les cosmétiques du lobby et 8 titres de chat.

**▸ Commandes joueurs**
• `/oc` (`/orbitcoins`, `/coins`) — solde · `/oc boutique` · `/oc titres` · `/oc top` · `/oc ration`
• `/oc regles [blackjack|poker|mains|billard|…]` — le livre des règles.
• `/table <action> [montant]` — actions de table (utilisé par les boutons du chat) ; `/table ecran` rouvre l'écran HD.

**▸ Commandes admin** — `/casino`
• `donner <pseudo> <montant>` · `retirer <pseudo> <montant>` · `solde <pseudo>`
• `generer` — reconstruit la salle (tables et PNJ remis, arcade comprise).
• `tp` — t'emmène au casino.
• `bouton` — l'entrée secrète sur le bloc visé · `bouton poser` (console) — la repose.
• `recharger` — réglages et catalogue (les jeux gardent leurs réglages jusqu'au redémarrage).

**▸ Base**
Tables `casino_comptes`, `casino_cosmetiques`, `casino_jackpot`, `casino_journal`. La base fait foi ; une mise en cours est rendue au redémarrage.
