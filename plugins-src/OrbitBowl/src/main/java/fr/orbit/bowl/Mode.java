package fr.orbit.bowl;

import java.util.List;

/**
 * Une façon de jouer à une activité. {@code min}/{@code max} : joueurs humains ; {@code bot} : un bot complète
 * la partie ; {@code mise} : chacun mise la même somme, le gagnant prend tout (aucune commission).
 */
public record Mode(Jeu jeu, String id, String nom, String aide, int min, int max, boolean bot, boolean mise) {

    public static final long[] MISES = {100, 500, 1000, 5000};

    public static List<Mode> de(Jeu j) {
        return switch (j) {
            case BOWLING -> List.of(
                    new Mode(j, "solo", "Partie solo", "10 frames, seul sur la piste", 1, 1, false, false),
                    new Mode(j, "bot", "Contre le bot", "Toi contre le Bot ORBIT", 1, 1, true, false),
                    new Mode(j, "amis", "Entre amis", "2 à 4 joueurs sur la même piste", 2, 4, false, false),
                    new Mode(j, "defi", "Défi misé", "1 contre 1 : le meilleur score prend les deux mises", 2, 2, false, true));
            case FLECHETTES -> List.of(
                    new Mode(j, "max", "Score max", "8 volées de 3 fléchettes : fais le plus gros score", 1, 1, false, false),
                    new Mode(j, "horloge", "Tour de l'horloge", "Touche 1, 2, 3… 20 puis le centre, en un minimum de fléchettes", 1, 4, false, false),
                    new Mode(j, "301bot", "301 contre le bot", "Descends de 301 à 0 pile avant le bot", 1, 1, true, false),
                    new Mode(j, "301", "301 entre amis", "2 à 4 joueurs, premier à 0 pile", 2, 4, false, false),
                    new Mode(j, "defi", "Défi 301 misé", "1 contre 1 en 301 : le gagnant prend les deux mises", 2, 2, false, true));
            case TIR -> List.of(new Mode(j, "solo", "60 secondes", "Touche un max de cibles, évite les satellites ORBIT", 1, 1, false, false));
            case BASKET -> List.of(new Mode(j, "solo", "45 secondes", "Un max de paniers, le panier bouge à la fin", 1, 1, false, false));
            case GOLF -> List.of(
                    new Mode(j, "solo", "Parcours solo", "9 trous, par 24", 1, 1, false, false),
                    new Mode(j, "amis", "Entre amis", "2 à 4 joueurs, chacun son tour", 2, 4, false, false));
            case BABY -> List.of(
                    new Mode(j, "bot", "Contre le bot", "Premier à 5 buts", 1, 1, true, false),
                    new Mode(j, "duel", "Duel", "1 contre 1, premier à 5 buts", 2, 2, false, false),
                    new Mode(j, "defi", "Duel misé", "1 contre 1 : le gagnant prend les deux mises", 2, 2, false, true));
            case LASER -> List.of(new Mode(j, "partie", "Partie", "Bleu contre Or, 5 minutes, bots en renfort", 1, 8, true, false));
        };
    }

    public static Mode parse(Jeu j, String id) {
        for (Mode m : de(j)) {
            if (m.id.equalsIgnoreCase(id)) {
                return m;
            }
        }
        return null;
    }
}
