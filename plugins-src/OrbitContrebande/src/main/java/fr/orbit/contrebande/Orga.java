package fr.orbit.contrebande;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Une organisation de contrebandiers (1 à 4 joueurs) : l'empire qui persiste d'une nuit à l'autre.
 * Les colonnes simples sont dans la table {@code contrebande_orgas}, le reste dans {@link Data} (JSON).
 */
final class Orga {

    static final int MAX_MEMBRES = 4;

    /** Paliers de réputation : ★ à ★★★★★. */
    static final int[] PALIERS = {0, 5, 15, 30, 60, 100};

    /** Titres selon la fortune gagnée depuis la création. */
    static final long[] SEUILS = {0, 1_000, 5_000, 15_000, 40_000, 100_000, 250_000};
    static final String[] TITRES = {"Petite main", "Passeur", "Contrebandier", "Caïd des Docks", "Baron du Marché",
            "Parrain d'Orbite", "Légende d'Orbite"};

    int id;
    String nom;
    UUID chef;
    final Map<UUID, String> membres = new LinkedHashMap<>();
    long credits;
    int rep;
    long fortune;
    int nuits;
    int chaleur;
    Data data = new Data();

    static final class Data {
        /** Améliorations : bacs, engrais, conds, synth, bottes, brouilleur, marche, hv, jardinier, revendeur. */
        Map<String, Integer> niveaux = new HashMap<>();
        /** Coffre de la planque : clé d'objet -> quantité. */
        Map<String, Integer> coffre = new LinkedHashMap<>();
        /** Carnet : signature d'un produit -> suite d'additifs depuis la base brute (« COMETE,LUNE »). */
        Map<String, String> recettes = new LinkedHashMap<>();
        /** Avancement (ticks de nuit) des postes, -1 = vide. */
        List<Integer> bacs = new ArrayList<>();
        List<Integer> conds = new ArrayList<>();
        int synth = -1;
        int titre;
        long meilleureNuit;
        int ventes, saisies;
    }

    int niveau(String k) {
        Integer v = data.niveaux.get(k);
        return v == null ? 0 : v;
    }

    void niveau(String k, int v) {
        data.niveaux.put(k, v);
    }

    int etoiles() {
        int lvl = 0;
        for (int i = 0; i < PALIERS.length; i++) {
            if (rep >= PALIERS[i]) {
                lvl = i;
            }
        }
        return lvl;
    }

    int titreIndex() {
        int t = 0;
        for (int i = 0; i < SEUILS.length; i++) {
            if (fortune >= SEUILS[i]) {
                t = i;
            }
        }
        return t;
    }

    String titre() {
        return TITRES[titreIndex()];
    }

    int bacs() {
        return 2 + niveau("bacs");
    }

    int conds() {
        return niveau("conds");
    }

    boolean synth() {
        return niveau("synth") > 0;
    }

    boolean marche() {
        return niveau("marche") > 0;
    }

    boolean hauteVille() {
        return niveau("hv") > 0;
    }

    void ajouter(String cle, int n) {
        if (n <= 0) {
            return;
        }
        data.coffre.merge(cle, n, Integer::sum);
    }

    /** Retire jusqu'à n ; renvoie ce qui a été retiré. */
    int retirer(String cle, int n) {
        int have = data.coffre.getOrDefault(cle, 0);
        int take = Math.min(have, Math.max(0, n));
        if (have - take <= 0) {
            data.coffre.remove(cle);
        } else {
            data.coffre.put(cle, have - take);
        }
        return take;
    }

    int stock(String cle) {
        return data.coffre.getOrDefault(cle, 0);
    }

    /** Avancement des postes, à la bonne taille. */
    void normaliser() {
        if (data.niveaux == null) {
            data.niveaux = new HashMap<>();
        }
        if (data.coffre == null) {
            data.coffre = new LinkedHashMap<>();
        }
        if (data.recettes == null) {
            data.recettes = new LinkedHashMap<>();
        }
        if (data.bacs == null) {
            data.bacs = new ArrayList<>();
        }
        if (data.conds == null) {
            data.conds = new ArrayList<>();
        }
        while (data.bacs.size() < Ville.BACS.length) {
            data.bacs.add(-1);
        }
        while (data.conds.size() < Ville.CONDS.length) {
            data.conds.add(-1);
        }
    }
}
