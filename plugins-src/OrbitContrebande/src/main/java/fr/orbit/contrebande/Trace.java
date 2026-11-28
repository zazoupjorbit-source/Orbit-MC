package fr.orbit.contrebande;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Tracé de la ville de CONTREBANDE : rues, places, lots des immeubles, portes et rondes de la douane.
 * Aucune dépendance au serveur : {@code java -cp target/classes fr.orbit.contrebande.Trace plan.png} dessine le plan hors jeu.
 * <pre>
 *  HAUTE-VILLE (z 0-65)   : un croissant autour d'un jardin rond, une avenue en biais, un square, des impasses
 *  LE MARCHÉ   (z 67-137) : une place ronde, quatre rues en étoile, des venelles
 *  LES DOCKS   (z 139-207): la planque, une avenue en biais vers le quai, le dépôt de conteneurs, le phare
 * </pre>
 * Les portes des deux murs ne sont pas en face l'une de l'autre : on ne traverse jamais la ville en ligne droite.
 */
final class Trace {

    /** 256 x 224 depuis la v0.5.0 : le double de la première ville dans chaque sens, soit quatre fois sa surface. */
    static final int W = 256, D = 224;
    static final int MUR_HV = 66, MUR_MARCHE = 138, QUAI = 208;
    static final int[][] PORTES_HV = {{62, 66}, {155, 159}, {239, 243}};
    static final int[][] PORTES_MARCHE = {{30, 34}, {122, 126}, {215, 219}};
    /** Première colonne de chacun des trois pontons (3 de large), coin nord-ouest de la planque (12 x 12). */
    static final int[] PONTONS = {41, 121, 201};
    static final int PLANQUE_X = 4, PLANQUE_Z = 158;
    /** Colonnes où l'on peut bâtir. */
    static final int XMIN = 3, XMAX = W - 4;

    static final byte SOL = 0, ROUTE = 1, ALLEE = 2, PLACE = 3, RUELLE = 4, COUR = 5, BATI = 6, PARC = 7, BASSIN = 8, EAU = 9,
            PONTON = 10, BORD = 11, MUR = 12, PLANQUE = 13, DEPOT = 14, PASSAGE = 15, PHARE = 16, TROTTOIR = 17;

    /** Quartiers : mêmes numéros que {@code Ville.Quartier.ordinal()}. */
    static final int DOCKS = 0, MARCHE = 1, HV = 2;
    static final int SUD = 0, NORD = 1, EST = 2, OUEST = 3;

    static final byte[][] T = new byte[W][D];
    /** Numéro du lot + 1 (0 = aucun). */
    static final int[][] LOT = new int[W][D];
    /** Cases à garder dégagées (devant une porte, bouche d'un passage). */
    static final boolean[][] SEUIL = new boolean[W][D];

    /**
     * Un immeuble. {@code face} : côté de la porte (-1 = aucune), {@code px/pz} : bloc de la porte.
     * {@code passage} : 0 = aucun, x > 0 = passage nord-sud sur les colonnes x et x+1, z < 0 = passage est-ouest sur -z et -z+1.
     */
    record Lot(int id, int x1, int z1, int x2, int z2, int q, int h, int face, int px, int pz, String nom, int passage) {
        int w() {
            return x2 - x1 + 1;
        }

        int d() {
            return z2 - z1 + 1;
        }
    }

    static final List<Lot> LOTS = new ArrayList<>();

    private static final String[][] NOMS = {
            {"Entrepôt 7", "Garage Rouille", "Cantine du Quai", "Hangar 12", "Atelier Soudure", "Ferraille & Fils", "Bar du Phare",
                    "Capitainerie", "Entrepôt 9", "Poissonnerie Kraken", "Dortoir des Dockers", "Hangar 21", "Chantier Naval",
                    "Glacière du Port", "Voilerie Astrale", "Comptoir des Marées", "Hangar 33", "Douche des Dockers", "Atelier Dérive",
                    "Entrepôt 14", "Corderie", "Dépôt Bitume", "Cale Sèche", "Relais des Grues"},
            {"Bar L'Éclipse", "Épicerie Comète", "Chez Nébula", "Atelier Quasar", "Bazar Galaxie", "Club Pulsar", "Café Météore",
                    "Pharmacie Lune", "Fripes Saturne", "Noodles 88", "Arcade Vortex", "Tatouage Cosmos", "Disques Supernova",
                    "Herboriste Io", "Hôtel Petite Ourse", "Thés d'Europe", "Lampes Sélène", "Barbier Apogée", "Épices Mercure",
                    "Tapis Cassiopée", "Raviolis Callisto", "Kiosque Écho", "Brocante Orbite", "Laverie Céleste", "Bouquins Antarès",
                    "Glacier Borée", "Luthier Véga", "Poterie Ganymède", "Traiteur Phobos", "Horloger Deimos"},
            {"Hôtel Orion", "Banque Stellaire", "Galerie Céleste", "Résidence Aurore", "Villa Zénith", "Opéra des Astres", "Maison Vesper",
                    "Club Héliopause", "Résidence Halo", "Ambassade de Titan", "Bijouterie Lyra", "Spa Andromède", "Cercle Polaris",
                    "Musée Kepler", "Tour Sirius", "Palais Équinoxe", "Maison Solstice", "Salon Périhélie", "Hôtel Méridien d'Or",
                    "Joaillerie Altaïr", "Loge Séléné", "Résidence Nadir", "Cercle Aphélie", "Villa Rigel"}};

    static {
        tracer();
        lotir();
        trottoirs();
        portes();
    }

    // ------------------------------------------------------------------ pinceaux

    static boolean dans(int x, int z) {
        return x >= 0 && z >= 0 && x < W && z < D;
    }

    private static void poser(int x, int z, byte k) {
        if (!dans(x, z)) {
            return;
        }
        byte t = T[x][z];
        if (t == PLANQUE || t == MUR || t == EAU || t == PONTON || t == BORD) {
            return;
        }
        T[x][z] = k;
    }

    private static void rect(int x1, int z1, int x2, int z2, byte k) {
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                poser(x, z, k);
            }
        }
    }

    private static void disque(double cx, double cz, double r, byte k) {
        for (int x = (int) (cx - r - 1); x <= cx + r + 1; x++) {
            for (int z = (int) (cz - r - 1); z <= cz + r + 1; z++) {
                if (Math.hypot(x + 0.5 - cx, z + 0.5 - cz) <= r) {
                    poser(x, z, k);
                }
            }
        }
    }

    private static void anneau(double cx, double cz, double r1, double r2, int zMin, int zMax, byte k) {
        for (int x = 0; x < W; x++) {
            for (int z = zMin; z <= zMax; z++) {
                double d = Math.hypot(x + 0.5 - cx, z + 0.5 - cz);
                if (d >= r1 && d <= r2) {
                    poser(x, z, k);
                }
            }
        }
    }

    /** Rue en biais : toutes les cases à moins d'une demi-largeur du segment. */
    private static void ligne(double x1, double z1, double x2, double z2, double largeur, int zMin, int zMax, byte k) {
        double dx = x2 - x1, dz = z2 - z1, l2 = dx * dx + dz * dz;
        for (int x = 0; x < W; x++) {
            for (int z = zMin; z <= zMax; z++) {
                double t = Math.max(0, Math.min(1, ((x + 0.5 - x1) * dx + (z + 0.5 - z1) * dz) / l2));
                if (Math.hypot(x + 0.5 - x1 - t * dx, z + 0.5 - z1 - t * dz) <= largeur / 2) {
                    poser(x, z, k);
                }
            }
        }
    }

    // ------------------------------------------------------------------ rues

    private static void tracer() {
        for (int x = 0; x < W; x++) {
            for (int z = 0; z < D; z++) {
                T[x][z] = z > QUAI ? EAU : z == QUAI ? BORD : z == MUR_HV || z == MUR_MARCHE ? MUR : SOL;
            }
        }
        for (int px : PONTONS) {
            for (int x = px; x <= px + 2; x++) {
                for (int z = QUAI + 1; z < D; z++) {
                    T[x][z] = PONTON;
                }
            }
        }
        for (int x = PLANQUE_X; x <= PLANQUE_X + 11; x++) {
            for (int z = PLANQUE_Z; z <= PLANQUE_Z + 11; z++) {
                T[x][z] = PLANQUE;
            }
        }
        // boulevards de ceinture, de part et d'autre des deux murs (7 de large : trottoir, cinq de chaussée, trottoir), route du quai
        rect(0, 59, W - 1, 65, ROUTE);
        rect(0, 67, W - 1, 73, ROUTE);
        rect(0, 131, W - 1, 137, ROUTE);
        rect(0, 139, W - 1, 145, ROUTE);
        rect(0, 203, W - 1, 207, ROUTE);

        // --- Haute-Ville : le croissant, son jardin rond, l'avenue en biais, le square
        anneau(128.5, 66, 40, 49, 0, 58, ROUTE);
        disque(128.5, 44.5, 13, PLACE);
        disque(128.5, 44.5, 7.5, PARC);
        rect(127, 26, 130, 31, ALLEE);
        rect(127, 57, 130, 58, ALLEE);
        rect(60, 28, 68, 58, ROUTE);
        rect(12, 28, 97, 34, ROUTE);
        rect(16, 8, 27, 19, PLACE);
        rect(20, 20, 22, 27, ALLEE);
        rect(56, 4, 58, 27, ALLEE);
        rect(30, 35, 32, 58, ALLEE);
        ligne(240, 60, 146, 22, 9, 0, 58, ROUTE);
        rect(172, 16, 174, 31, ALLEE);
        rect(172, 16, 251, 18, ALLEE);
        rect(200, 19, 202, 41, ALLEE);
        rect(224, 19, 226, 51, ALLEE);

        // --- Marché : quatre rues en étoile, venelles, puis la place ronde et son bassin par-dessus
        ligne(128.5, 102.5, 38, 76, 5, 74, 130, ALLEE);
        ligne(128.5, 102.5, 218, 76, 5, 74, 130, ALLEE);
        ligne(128.5, 102.5, 38, 128, 5, 74, 130, ALLEE);
        ligne(128.5, 102.5, 218, 128, 5, 74, 130, ALLEE);
        rect(28, 74, 34, 130, ROUTE);
        rect(220, 74, 226, 130, ROUTE);
        rect(126, 74, 130, 86, ALLEE);
        rect(126, 119, 130, 130, ALLEE);
        rect(35, 101, 110, 104, ALLEE);
        rect(176, 88, 178, 117, ALLEE);
        rect(76, 74, 78, 88, ALLEE);
        rect(184, 118, 186, 130, ALLEE);
        rect(80, 116, 82, 130, ALLEE);
        rect(168, 74, 170, 90, ALLEE);
        disque(128.5, 102.5, 19, PLACE);
        disque(128.5, 102.5, 4.5, BASSIN);

        // --- Docks : rue des Grues, rue de la Planque, avenue du Phare en biais, dépôt de conteneurs, place du Phare
        rect(28, 146, 36, 202, ROUTE);
        rect(0, 170, 87, 176, ROUTE);
        ligne(84, 173.5, 117, 203, 9, 146, 202, ROUTE);
        rect(120, 146, 128, 167, ROUTE);
        rect(120, 160, 182, 166, ROUTE);
        rect(174, 146, 182, 202, ROUTE);
        rect(185, 149, 237, 199, DEPOT);
        rect(49, 146, 51, 169, ALLEE);
        rect(73, 146, 75, 169, ALLEE);
        rect(101, 146, 103, 186, ALLEE);
        rect(61, 177, 63, 202, ALLEE);
        rect(145, 167, 147, 202, ALLEE);
        disque(245.5, 195.5, 7.4, PLACE);
        disque(245.5, 195.5, 4.7, PHARE);
    }

    private static boolean grille(int x, int z) {
        for (int[] s : z == MUR_HV ? PORTES_HV : PORTES_MARCHE) {
            if (x >= s[0] && x <= s[1]) {
                return true;
            }
        }
        return false;
    }

    /** Ce qui borde une chaussée : un immeuble, une cour, un jardin, un mur (hors grilles) ou le bord de la carte. */
    private static boolean rive(int x, int z, boolean enFace) {
        if (!dans(x, z)) {
            return true;
        }
        return switch (T[x][z]) {
            case BATI, SOL, COUR, PARC, PLANQUE, PHARE -> true;
            case MUR -> enFace && !grille(x, z);
            default -> false;
        };
    }

    /** Trottoirs : la rangée de chaque route qui longe autre chose qu'une rue. On y marche comme sur la chaussée. */
    private static void trottoirs() {
        boolean[][] bord = new boolean[W][D];
        for (int x = 0; x < W; x++) {
            for (int z = 0; z < QUAI; z++) {
                if (T[x][z] != ROUTE) {
                    continue;
                }
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if ((dx != 0 || dz != 0) && rive(x + dx, z + dz, dx == 0)) {
                            bord[x][z] = true;
                        }
                    }
                }
            }
        }
        for (int x = 0; x < W; x++) {
            for (int z = 0; z < QUAI; z++) {
                if (bord[x][z]) {
                    T[x][z] = TROTTOIR;
                }
            }
        }
    }

    // ------------------------------------------------------------------ lots

    private static boolean libre(int x, int w, int z) {
        for (int i = x; i < x + w; i++) {
            if (i > XMAX || T[i][z] != SOL) {
                return false;
            }
        }
        return true;
    }

    /** Longueur à bâtir sur {@code dispo} cases : assez pour ne pas laisser de bande trop fine, coupée en deux si c'est long. */
    private static int taille(Random r, int dispo, int min, int max, int m) {
        int t = Math.min(dispo, min + r.nextInt(max - min + 1));
        if (dispo - t - 1 < m) {
            t = dispo >= 2 * m + 1 && dispo > max ? (dispo - 1) / 2 : Math.min(dispo, max + 3);
            if (dispo - t - 1 < m && dispo - t > 0 && dispo > max + 3) {
                t = dispo - m - 1;
            }
        }
        return t;
    }

    private static void lotir() {
        Random r = new Random(2026L);
        int[][] zones = {{147, 201, DOCKS, 10, 18, 9, 16, 6}, {75, 129, MARCHE, 7, 14, 7, 14, 5}, {2, 57, HV, 10, 18, 9, 16, 6}};
        int[] nom = new int[3];
        for (int[] zo : zones) {
            int q = zo[2];
            for (int z = zo[0]; z <= zo[1]; z++) {
                for (int x = XMIN; x <= XMAX; x++) {
                    if (T[x][z] != SOL) {
                        continue;
                    }
                    int run = 0;
                    while (x + run <= XMAX && T[x + run][z] == SOL && run < 40) {
                        run++;
                    }
                    int m = zo[7], w = taille(r, run, zo[3], zo[4], m), d = 0;
                    for (; w >= m; w--) {
                        int dispo = 0;
                        while (z + dispo <= zo[1] && dispo < 40 && libre(x, w, z + dispo)) {
                            dispo++;
                        }
                        d = taille(r, dispo, zo[5], zo[6], m);
                        if (d >= m) {
                            break;
                        }
                    }
                    if (w < m || d < m) {
                        T[x][z] = COUR;
                        continue;
                    }
                    int id = LOTS.size();
                    for (int i = x; i < x + w; i++) {
                        for (int j = z; j < z + d; j++) {
                            T[i][j] = BATI;
                            LOT[i][j] = id + 1;
                        }
                    }
                    // ruelle d'un bloc à l'est et au sud : de quoi semer une patrouille
                    for (int j = z; j <= z + d && j <= zo[1]; j++) {
                        if (x + w <= XMAX && T[x + w][j] == SOL) {
                            T[x + w][j] = RUELLE;
                        }
                    }
                    for (int i = x; i < x + w; i++) {
                        if (z + d <= zo[1] && T[i][z + d] == SOL) {
                            T[i][z + d] = RUELLE;
                        }
                    }
                    int h = switch (q) {
                        case HV -> 9 + r.nextInt(10);
                        case MARCHE -> 5 + r.nextInt(5);
                        default -> 5 + r.nextInt(5);
                    };
                    LOTS.add(new Lot(id, x, z, x + w - 1, z + d - 1, q, h, -1, 0, 0, "", r.nextInt(100)));
                }
            }
        }
        // noms, dans un ordre mélangé
        Random rn = new Random(41L);
        List<Integer> ordre = new ArrayList<>();
        for (int i = 0; i < LOTS.size(); i++) {
            ordre.add(i);
        }
        java.util.Collections.shuffle(ordre, rn);
        String[] noms = new String[LOTS.size()];
        for (int i : ordre) {
            int q = LOTS.get(i).q;
            noms[i] = nom[q] < NOMS[q].length ? NOMS[q][nom[q]++] : "";
        }
        for (int i = 0; i < LOTS.size(); i++) {
            Lot l = LOTS.get(i);
            LOTS.set(i, new Lot(l.id, l.x1, l.z1, l.x2, l.z2, l.q, l.h, -1, 0, 0, noms[i], l.passage));
        }
    }

    static boolean marche(int x, int z) {
        if (!dans(x, z)) {
            return false;
        }
        byte t = T[x][z];
        return t == SOL || t == ROUTE || t == TROTTOIR || t == ALLEE || t == PLACE || t == RUELLE || t == COUR || t == DEPOT || t == PASSAGE;
    }

    private static int poids(int x, int z) {
        if (!dans(x, z)) {
            return 0;
        }
        return switch (T[x][z]) {
            case ROUTE, TROTTOIR, ALLEE, PLACE -> 4;
            case SOL -> 3;
            case DEPOT, RUELLE -> 1;
            default -> 0;
        };
    }

    private static void seuil(int x, int z) {
        if (dans(x, z)) {
            SEUIL[x][z] = true;
        }
    }

    private static void portes() {
        for (int n = 0; n < LOTS.size(); n++) {
            Lot l = LOTS.get(n);
            // passage couvert à travers les grands immeubles du Marché et des Docks
            int passage = 0;
            if (l.q != HV && l.passage < 50) {
                int px = l.x1 + l.w() / 2 - 1, pz = l.z1 + l.d() / 2 - 1;
                if (l.w() >= 10 && marche(px, l.z1 - 1) && marche(px + 1, l.z1 - 1) && marche(px, l.z2 + 1) && marche(px + 1, l.z2 + 1)) {
                    passage = px;
                } else if (l.d() >= 10 && marche(l.x1 - 1, pz) && marche(l.x1 - 1, pz + 1) && marche(l.x2 + 1, pz) && marche(l.x2 + 1, pz + 1)) {
                    passage = -pz;
                }
            }
            if (passage > 0) {
                for (int z = l.z1; z <= l.z2; z++) {
                    T[passage][z] = PASSAGE;
                    T[passage + 1][z] = PASSAGE;
                }
                for (int x = passage; x <= passage + 1; x++) {
                    seuil(x, l.z1 - 1);
                    seuil(x, l.z2 + 1);
                }
            } else if (passage < 0) {
                for (int x = l.x1; x <= l.x2; x++) {
                    T[x][-passage] = PASSAGE;
                    T[x][-passage + 1] = PASSAGE;
                }
                for (int z = -passage; z <= -passage + 1; z++) {
                    seuil(l.x1 - 1, z);
                    seuil(l.x2 + 1, z);
                }
            }
            // porte : la façade la mieux exposée à la rue, au milieu de sa plus longue partie libre
            int face = -1, score = 0, bx = 0, bz = 0;
            for (int f = 0; f < 4; f++) {
                boolean ns = f == SUD || f == NORD;
                int from = ns ? l.x1 + 1 : l.z1 + 1, to = ns ? l.x2 - 1 : l.z2 - 1;
                int s = 0, debut = -1, long1 = 0, milieu = -1;
                for (int i = from; i <= to + 1; i++) {
                    int p = 0;
                    if (i <= to) {
                        boolean mur = true;
                        for (int k = i - 1; k <= i + 1; k++) {
                            mur &= T[ns ? k : (f == EST ? l.x2 : l.x1)][ns ? (f == SUD ? l.z2 : l.z1) : k] == BATI;
                        }
                        p = mur ? poids(ns ? i : (f == EST ? l.x2 + 1 : l.x1 - 1), ns ? (f == SUD ? l.z2 + 1 : l.z1 - 1) : i) : 0;
                    }
                    if (p > 0) {
                        s += p * p;
                        if (debut < 0) {
                            debut = i;
                        }
                    } else if (debut >= 0) {
                        if (i - debut > long1) {
                            long1 = i - debut;
                            milieu = (debut + i - 1) / 2;
                        }
                        debut = -1;
                    }
                }
                if (s > score && milieu >= 0) {
                    score = s;
                    face = f;
                    bx = f == SUD || f == NORD ? milieu : (f == EST ? l.x2 : l.x1);
                    bz = f == SUD ? l.z2 : f == NORD ? l.z1 : milieu;
                }
            }
            if (face >= 0) {
                seuil(bx + (face == EST ? 1 : face == OUEST ? -1 : 0), bz + (face == SUD ? 1 : face == NORD ? -1 : 0));
            }
            LOTS.set(n, new Lot(l.id, l.x1, l.z1, l.x2, l.z2, l.q, l.h, face, bx, bz, "", passage));
        }
        // noms : seulement les immeubles qui ont une porte, dans un ordre mélangé
        List<Integer> ordre = new ArrayList<>();
        for (int i = 0; i < LOTS.size(); i++) {
            if (LOTS.get(i).face >= 0) {
                ordre.add(i);
            }
        }
        java.util.Collections.shuffle(ordre, new Random(41L));
        int[] nom = new int[3];
        for (int i : ordre) {
            Lot l = LOTS.get(i);
            int rang = nom[l.q]++;
            String s = rang < NOMS[l.q].length ? NOMS[l.q][rang] : autre(l.q, rang - NOMS[l.q].length);
            LOTS.set(i, new Lot(l.id, l.x1, l.z1, l.x2, l.z2, l.q, l.h, l.face, l.px, l.pz, s, l.passage));
        }
    }

    // ------------------------------------------------------------------ rondes de la douane (x, z), toujours dans les rues

    /** Noms de rechange quand la liste d'un quartier est épuisée : 80 par quartier, tous différents. */
    private static String autre(int q, int i) {
        if (i >= 80) {
            return "";
        }
        // listes locales : cette méthode sert pendant l'initialisation de la classe, avant les constantes déclarées plus bas
        String[][] genres = {{"Entrepôt", "Hangar", "Dépôt", "Atelier", "Cale"},
                {"Échoppe", "Comptoir", "Bazar", "Atelier", "Cantine"}, {"Résidence", "Villa", "Maison", "Cercle", "Galerie"}};
        String[] astres = {"Mira", "Deneb", "Castor", "Pollux", "Electra", "Maïa", "Thébé", "Obéron", "Titania",
                "Ariel", "Triton", "Charon", "Cérès", "Vesta", "Pallas", "Hygie"};
        return genres[q][i % 5] + " " + (q == DOCKS ? String.valueOf(40 + i * 3) : astres[(3 * (i / 5) + i % 5) % astres.length]);
    }

    /** Points du croissant de la Haute-Ville, de l'angle a1 à a2 (0 = bout ouest, 90 = sommet nord, 180 = bout est). */
    private static double[][] arc(double a1, double a2, int n) {
        double[][] p = new double[n][];
        for (int i = 0; i < n; i++) {
            double a = Math.toRadians(a1 + (a2 - a1) * i / (n - 1));
            p[i] = new double[]{128.5 - 44.5 * Math.cos(a), 66 - 44.5 * Math.sin(a)};
        }
        return p;
    }

    private static double[][] suite(double[][]... parts) {
        List<double[]> l = new ArrayList<>();
        for (double[][] p : parts) {
            l.addAll(List.of(p));
        }
        return l.toArray(new double[0][]);
    }

    /** Une ronde fermée, avec des points intermédiaires : jamais plus de 20 blocs entre deux points (les agents s'y retrouvent). */
    private static double[][] ronde(double[][] p) {
        List<double[]> l = new ArrayList<>();
        for (int i = 0; i < p.length; i++) {
            double[] u = p[i], v = p[(i + 1) % p.length];
            l.add(u);
            int n = (int) Math.ceil(Math.hypot(v[0] - u[0], v[1] - u[1]) / 20);
            for (int k = 1; k < n; k++) {
                l.add(new double[]{u[0] + (v[0] - u[0]) * k / n, u[1] + (v[1] - u[1]) * k / n});
            }
        }
        return l.toArray(new double[0][]);
    }

    /** Rondes à pied (et couloirs des drones) : des boucles, on revient du dernier point au premier. */
    static final double[][] R_D1 = ronde(new double[][]{{32.5, 142.5}, {32.5, 173.5}, {84, 173.5}, {114, 200}, {116, 205.5}, {178.5, 205.5},
            {178.5, 163.5}, {124.5, 163.5}, {124.5, 142.5}});
    static final double[][] R_D2 = ronde(new double[][]{{12, 205.5}, {244, 205.5}});
    static final double[][] R_D3 = ronde(new double[][]{{178.5, 142.5}, {178.5, 205.5}, {117, 205.5}, {86, 174.5}, {32.5, 173.5}, {32.5, 142.5}});
    static final double[][] R_M1 = ronde(new double[][]{{31.5, 70.5}, {128.5, 70.5}, {128.5, 88}, {112, 102.5}, {128.5, 117}, {128.5, 134.5},
            {31.5, 134.5}});
    static final double[][] R_M2 = ronde(new double[][]{{223.5, 70.5}, {216, 77}, {145, 97.5}, {145, 107.5}, {216, 127.5}, {223.5, 134.5}});
    static final double[][] R_H1 = ronde(suite(new double[][]{{84.5, 62.5}}, arc(12, 168, 17), new double[][]{{172.5, 62.5}}));
    static final double[][] R_H2 = ronde(new double[][]{{64.5, 57}, {64.5, 31.5}, {16, 31.5}, {64.5, 31.5}, {92, 31.5}, {64.5, 31.5}});
    /** Tournées des voitures : au milieu des routes larges seulement. */
    static final double[][] V_DOCKS = {{32.5, 142.5}, {178.5, 142.5}, {178.5, 205.5}, {32.5, 205.5}};
    static final double[][] V_MARCHE = {{31.5, 70.5}, {223.5, 70.5}, {223.5, 134.5}, {31.5, 134.5}};
    static final double[][] V_HV = suite(new double[][]{{84.5, 62.5}, {240, 62.5}, {236, 58.4}, {148.5, 23.2}}, arc(108, 12, 13));

    // ------------------------------------------------------------------ aperçu hors jeu

    public static void main(String[] a) throws Exception {
        int k = 6;
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(W * k, D * k, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        int[] col = {0x8A8F99, 0x3A3F4A, 0x6B5E52, 0xC9B38A, 0x565B66, 0x7A9A6A, 0, 0x4FA35A, 0x4FB6E8, 0x2A5F9E, 0x8A6A44, 0xB0B4BC,
                0x141018, 0xE0B864, 0x70757F, 0x9C8F80, 0xF2F2F2, 0x5E6470};
        int[][] teintes = {{0x4E6E78, 0x5C5348, 0x6A7A84, 0x7A5A44}, {0xC8743C, 0xD6A63C, 0xB8483C, 0x7FA648, 0xC47A8C, 0x4C9C9C},
                {0xE8E4DC, 0xD8DCE8, 0xC8D8E4, 0xEADFCB}};
        int[] nb = new int[3], portes = new int[3], passages = new int[3], cour = new int[1];
        for (int x = 0; x < W; x++) {
            for (int z = 0; z < D; z++) {
                int c = col[T[x][z]];
                if (LOT[x][z] > 0) {
                    Lot l = LOTS.get(LOT[x][z] - 1);
                    c = T[x][z] == PASSAGE ? col[PASSAGE] : teintes[l.q][l.id % teintes[l.q].length];
                }
                if (T[x][z] == COUR) {
                    cour[0]++;
                }
                g.setColor(new java.awt.Color(c));
                g.fillRect(x * k, z * k, k, k);
                if (SEUIL[x][z]) {
                    g.setColor(new java.awt.Color(0xFFFFFF));
                    g.fillRect(x * k + 2, z * k + 2, k - 4, k - 4);
                }
            }
        }
        g.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 9));
        for (Lot l : LOTS) {
            nb[l.q]++;
            g.setColor(new java.awt.Color(0x10131A));
            g.drawRect(l.x1 * k, l.z1 * k, l.w() * k - 1, l.d() * k - 1);
            g.drawString(String.valueOf(l.h), l.x1 * k + 3, l.z1 * k + 10);
            if (l.face >= 0) {
                portes[l.q]++;
                g.setColor(new java.awt.Color(0xFFE23A));
                g.fillRect(l.px * k + 1, l.pz * k + 1, k - 2, k - 2);
            }
            if (l.passage != 0) {
                passages[l.q]++;
            }
        }
        Object[][] routes = {{R_D1, 0x62CFF4}, {R_D2, 0x62CFF4}, {R_D3, 0x62CFF4}, {R_M1, 0x62CFF4}, {R_M2, 0x62CFF4}, {R_H1, 0xFF9BE8},
                {R_H2, 0xFF9BE8}, {V_DOCKS, 0xE0484A}, {V_MARCHE, 0xE0484A}, {V_HV, 0xE0484A}};
        for (Object[] r : routes) {
            double[][] p = (double[][]) r[0];
            boolean voiture = (Integer) r[1] == 0xE0484A;
            g.setStroke(new java.awt.BasicStroke(voiture ? 3f : 1.6f));
            g.setColor(new java.awt.Color((Integer) r[1]));
            for (int i = 0; i < p.length; i++) {
                double[] u = p[i], v = p[(i + 1) % p.length];
                g.draw(new java.awt.geom.Line2D.Double(u[0] * k, u[1] * k, v[0] * k, v[1] * k));
                if (!marche((int) u[0], (int) u[1])) {
                    System.out.println("ATTENTION : point de ronde hors rue " + u[0] + ", " + u[1]);
                }
            }
        }
        g.dispose();
        javax.imageio.ImageIO.write(img, "png", new java.io.File(a[0]));
        String[] q = {"Docks", "Marché", "Haute-Ville"};
        for (int i = 0; i < 3; i++) {
            System.out.println(q[i] + " : " + nb[i] + " immeubles, " + portes[i] + " portes, " + passages[i] + " passages");
        }
        System.out.println("Cours : " + cour[0] + " cases");
        int sansNom = 0;
        for (Lot l : LOTS) {
            if (l.face >= 0 && l.nom.isEmpty()) {
                sansNom++;
            }
        }
        System.out.println("Immeubles à porte sans nom : " + sansNom);
    }
}
