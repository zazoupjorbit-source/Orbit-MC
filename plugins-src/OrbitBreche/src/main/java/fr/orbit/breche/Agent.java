package fr.orbit.breche;

/**
 * Agents de BRÈCHE : cinq personnages de l'univers ORBIT, quatre pouvoirs chacun (C, Q, E et l'ultime X).
 * C et Q s'achètent pendant la phase d'achat ; E est gratuit à chaque manche (certains se rechargent avec le
 * temps) ; X se charge avec les points d'ultime (élimination, mort, pose et désamorçage du Noyau).
 */
enum Agent {
    SOLENE("solene", "Solène", "Duelliste", "#FFB547", "Fille d'un soleil mourant, elle entre la première et ne recule jamais.",
            new Pouvoir("brasier", "Brasier", "4", 200, 1, 0, 0, "Lance une braise : le sol brûle 5 s (dégâts aux ennemis)."),
            new Pouvoir("eclat", "Éclat", "5", 250, 2, 0, 0, "Orbe solaire qui éclate après 0,7 s : aveugle ceux qui la regardent."),
            new Pouvoir("envol", "Envol", "6", 0, 1, 1, 0, "Bond fulgurant vers l'avant. Gratuit à chaque manche."),
            new Pouvoir("supernova", "Supernova", "7", -1, 7, 0, 0, "Missile solaire : énorme explosion à l'impact.")),
    ORYX("oryx", "Oryx", "Contrôleur", "#C79BFF", "Ancien veilleur de l'Ombre : il éteint les lignes de vue et choisit où l'on se bat.",
            new Pouvoir("puits", "Puits d'ombre", "4", 250, 1, 0, 0, "Orbe qui aspire et ralentit les ennemis proches, puis les blesse."),
            new Pouvoir("pas", "Pas de l'ombre", "5", 150, 1, 0, 0, "Se téléporte au sol visé (14 blocs max)."),
            new Pouvoir("nebuleuse", "Nébuleuse", "6", 100, 3, 1, 0, "Fumée opaque au point visé (15 s). Une gratuite par manche."),
            new Pouvoir("eclipse", "Éclipse", "7", -1, 7, 0, 0, "Tous les ennemis voient à peine à quelques blocs pendant 5 s.")),
    KAEL("kael", "Kael", "Sentinelle", "#62CFF4", "Ingénieur des glaces de la station : rien ne passe son couloir sans qu'il le sache.",
            new Pouvoir("fil", "Fil de givre", "4", 200, 1, 0, 0, "Fil tendu devant soi : l'ennemi qui le franchit est révélé, ralenti et blessé."),
            new Pouvoir("mine", "Mine cryo", "5", 150, 2, 0, 0, "Mine lancée : gèle à moitié et blesse l'ennemi qui s'approche."),
            new Pouvoir("tourelle", "Tourelle", "6", 0, 1, 1, 0, "Pose une tourelle qui tire sur les ennemis en vue. Gratuite."),
            new Pouvoir("glaciation", "Glaciation", "7", -1, 7, 0, 0, "Après 1,5 s, gèle et révèle tous les ennemis à 10 blocs.")),
    ECHO("echo", "Écho", "Initiateur", "#3A7FFF", "Pisteur des sondes profondes : il voit à travers les murs et ouvre la voie.",
            new Pouvoir("onde", "Onde de choc", "4", 200, 2, 0, 0, "Grenade qui explose 0,8 s après l'impact."),
            new Pouvoir("commotion", "Commotion", "5", 200, 1, 0, 0, "Orbe qui sonne les ennemis : vision troublée, tir imprécis."),
            new Pouvoir("sonar", "Flèche sonar", "6", 0, 1, 1, 40, "Flèche qui révèle deux fois les ennemis autour d'elle. Recharge 40 s."),
            new Pouvoir("traque", "Traque", "7", -1, 7, 0, 0, "Trois ondes qui révèlent tous les ennemis de la carte.")),
    NAIA("naia", "Naïa", "Soutien", "#7CFF8A", "Gardienne de la Lumière : elle soigne, bloque et ramène les siens.",
            new Pouvoir("cristal", "Mur de cristal", "4", 300, 1, 0, 0, "Mur de cristal de 7 blocs au point visé (25 s)."),
            new Pouvoir("lenteur", "Orbe de lenteur", "5", 100, 2, 0, 0, "Champ qui ralentit fortement les ennemis (6 s)."),
            new Pouvoir("soin", "Soin", "6", 0, 1, 1, 35, "Soigne 60 PV à l'allié visé (ou à soi). Recharge 35 s."),
            new Pouvoir("renaissance", "Renaissance", "7", -1, 8, 0, 0, "Ramène à la vie l'allié tombé le plus proche du point visé."));

    /** Un pouvoir : prix -1 = ultime (max = points nécessaires) ; gratuit = charges offertes à chaque manche ; recharge en secondes. */
    record Pouvoir(String id, String nom, String touche, int prix, int max, int gratuit, int recharge, String desc) {
        boolean ultime() {
            return prix < 0;
        }
    }

    final String id, nom, role, couleur, bio;
    final Pouvoir[] pouvoirs;

    Agent(String id, String nom, String role, String couleur, String bio, Pouvoir... pouvoirs) {
        this.id = id;
        this.nom = nom;
        this.role = role;
        this.couleur = couleur;
        this.bio = bio;
        this.pouvoirs = pouvoirs;
    }

    int rgb() {
        return Integer.parseInt(couleur.substring(1), 16);
    }

    String nomCouleur() {
        return "<" + couleur + ">" + nom + "</" + couleur + ">";
    }

    static Agent of(String id) {
        for (Agent a : values()) {
            if (a.id.equalsIgnoreCase(id)) {
                return a;
            }
        }
        return null;
    }
}
