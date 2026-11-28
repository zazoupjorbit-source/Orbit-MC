package fr.orbit.bowl;

import org.bukkit.command.CommandSender;

/** Règles de chaque jeu, affichées par /bowl regles &lt;jeu&gt; (et par l'hôtesse). */
final class Regles {

    private Regles() {
    }

    static void montrer(CommandSender to, Jeu j) {
        Txt.send(to, j.titre() + " <#5A6270>· règles");
        for (String l : lignes(j)) {
            to.sendMessage(Txt.mm("  <#E6EDF3>" + l));
        }
    }

    private static String[] lignes(Jeu j) {
        return switch (j) {
            case BOWLING -> new String[]{
                    "<#E0B864>Viser :</#E0B864> place-toi sur l'élan et regarde où envoyer la boule (ligne dorée sur la piste).",
                    "<#E0B864>Effet :</#E0B864> choisis la boule dans ta barre (effet à gauche ⟲, droite, ou à droite ⟳). La boule tourne en fin de piste.",
                    "<#E0B864>Lancer :</#E0B864> reste accroupi, la jauge monte et redescend, relâche au bon moment. Trop fort = moins précis.",
                    "10 frames, 2 lancers par frame (3 à la 10e si strike ou spare). Strike = 10 + les 2 lancers suivants, spare = 10 + le suivant.",
                    "Ne dépasse pas la ligne de faute néon. Score parfait : 300.",
                    "Défi misé : chacun mise la même somme, le meilleur score prend tout (égalité = mises rendues). Aucune commission."};
            case FLECHETTES -> new String[]{
                    "Reste derrière la ligne néon et vise la cible : le viseur bleu tremble un peu.",
                    "<#E0B864>Accroupi</#E0B864> = tu retiens ton souffle : le viseur se calme 2 secondes, puis tremble plus fort.",
                    "<#E0B864>Clic</#E0B864> = lancer. 3 fléchettes par volée. Anneau extérieur = double, anneau du milieu = triple, centre = 50, anneau vert = 25.",
                    "Score max : 8 volées, le plus gros total. Horloge : touche 1 à 20 dans l'ordre puis le centre.",
                    "301 : descends à 0 pile ; si tu passes en dessous, la volée est annulée."};
            case TIR -> new String[]{
                    "60 secondes derrière le comptoir. <#E0B864>Clic</#E0B864> = tir là où tu regardes.",
                    "Météore 10 pts, soucoupe 25 pts, comète dorée 60 pts. Satellite ORBIT = -30 pts : ne tire pas dessus !",
                    "Touches à la suite = combo jusqu'à ×3. Un tir raté remet le combo à ×1."};
            case BASKET -> new String[]{
                    "45 secondes. Regarde le panier, reste <#E0B864>accroupi</#E0B864> et relâche quand la jauge est dans la <#E0B864>zone or</#E0B864> (visée aidée).",
                    "Panier = 2 pts, 3 pts dans les 15 dernières secondes. Après 20 s, le panier se met à bouger."};
            case GOLF -> new String[]{
                    "9 trous, par 24. Approche-toi de ta balle, regarde la direction, reste <#E0B864>accroupi</#E0B864> pour la force, relâche.",
                    "Astéroïdes = rebonds, champs bleus = la balle glisse, trou de ver violet = la balle ressort plus loin.",
                    "6 coups max par trou. Points : trou en un 10, sous le par 6 à 8, par 4, +1 : 2, +2 : 1."};
            case BABY -> new String[]{
                    "Tu es assis côté sud. <#E0B864>Avancer / reculer</#E0B864> (Z/S ou W/S) fait glisser toutes tes barres.",
                    "<#E0B864>Saut ou clic</#E0B864> = frappe : tes joueurs proches de la balle tirent vers le but adverse.",
                    "Premier à 5 buts. Duel misé : le gagnant prend les deux mises."};
            case LASER -> new String[]{
                    "Équipes Bleu et Or, 5 minutes. Les places vides sont prises par des bots.",
                    "<#E0B864>Clic</#E0B864> = tir laser. Touché = désactivé 3 secondes (ta veste s'éteint, tu ne tires plus).",
                    "+10 pts par adversaire touché. L'équipe qui a le plus de points gagne (+bonus pour chacun)."};
        };
    }
}
