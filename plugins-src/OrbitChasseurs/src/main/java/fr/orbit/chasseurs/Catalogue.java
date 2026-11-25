package fr.orbit.chasseurs;

/** Vaisseaux, couleurs, modes et niveaux des bots. Tout se débloque en jouant : rien ne s'achète. */
final class Catalogue {

    private Catalogue() {
    }

    enum Mode {
        MELEE("melee", "chasseurs", "Mêlée générale", "Ceinture de Kesh", "#62CFF4"),
        DUO("duo", "chasseurs_duo", "Duo 2v2", "Épave Hélios", "#E0B864"),
        CONVOI("convoi", "chasseurs_convoi", "Convoi (coop)", "Route des Glaces", "#7CFF8A");

        final String id, jeu, label, arene, color;

        Mode(String id, String jeu, String label, String arene, String color) {
            this.id = id;
            this.jeu = jeu;
            this.label = label;
            this.arene = arene;
            this.color = color;
        }

        static Mode of(String s) {
            for (Mode m : values()) {
                if (m.id.equalsIgnoreCase(s) || m.name().equalsIgnoreCase(s)) {
                    return m;
                }
            }
            return switch (s.toLowerCase()) {
                case "mêlée", "ffa" -> MELEE;
                case "2v2", "equipes", "équipes" -> DUO;
                case "coop", "escorte", "vagues" -> CONVOI;
                default -> null;
            };
        }
    }

    /** Niveau des bots : vitesse de virage, précision, discipline de tir, multiplicateur de points. */
    enum Level {
        RECRUE("Recrue", "#7CFF8A", 0.55, 6.0, 0.55, 0.75),
        PILOTE("Pilote", "#62CFF4", 0.72, 3.8, 0.75, 1.0),
        AS("As", "#E0B864", 0.88, 2.4, 0.95, 1.3),
        LEGENDE("Légende", "#E0484A", 1.0, 1.4, 1.1, 1.6);

        final String label, color;
        final double turn, spread, damage, points;

        Level(String label, String color, double turn, double spread, double damage, double points) {
            this.label = label;
            this.color = color;
            this.turn = turn;
            this.spread = spread;
            this.damage = damage;
            this.points = points;
        }

        static Level of(int n) {
            return values()[Math.max(0, Math.min(values().length - 1, n - 1))];
        }

        int number() {
            return ordinal() + 1;
        }
    }

    enum ShipType {
        //        id         nom        coque bouc. vit.  virage dégâts cad. chaleur missiles niveau
        FLECHE("fleche", "Flèche", 100, 60, 1.00, 7.0, 9, 3, 7.0, 2, 1, "Chasseur équilibré, celui de tout pilote qui débute."),
        COMETE("comete", "Comète", 75, 50, 1.20, 8.0, 8, 3, 7.0, 2, 3, "Intercepteur : très rapide, coque fragile."),
        BASTION("bastion", "Bastion", 150, 90, 0.85, 5.5, 11, 4, 8.0, 4, 6, "Canonnière lourde : encaisse, frappe fort, vire lentement."),
        SPECTRE("spectre", "Spectre", 85, 70, 1.10, 9.0, 8, 3, 5.0, 2, 10, "Aile furtive : la plus agile, arme qui chauffe peu."),
        ECLIPSE("eclipse", "Éclipse", 110, 80, 1.10, 7.5, 10, 3, 6.5, 3, 15, "Double coque à anneau d'or : le vaisseau des légendes."),
        // hors hangar
        DRONE("drone", "Écumeur", 34, 0, 0.95, 6.0, 5, 6, 0, 0, 99, ""),
        FREGATE("drone", "Frégate pirate", 420, 120, 0.6, 3.2, 9, 4, 0, 6, 99, ""),
        CARGO("cargo", "ORBIT-Cargo", 900, 200, 0.2, 2.0, 0, 99, 0, 0, 99, "");

        final String id, label, text;
        final double hull, shield, speed, turn, damage, heat;
        final int cadence, missiles, level;

        ShipType(String id, String label, double hull, double shield, double speed, double turn, double damage, int cadence,
                 double heat, int missiles, int level, String text) {
            this.id = id;
            this.label = label;
            this.hull = hull;
            this.shield = shield;
            this.speed = speed;
            this.turn = turn;
            this.damage = damage;
            this.cadence = cadence;
            this.heat = heat;
            this.missiles = missiles;
            this.level = level;
            this.text = text;
        }

        boolean playable() {
            return level < 99;
        }

        static ShipType of(String id) {
            for (ShipType t : values()) {
                if (t.playable() && t.id.equalsIgnoreCase(id)) {
                    return t;
                }
            }
            return FLECHE;
        }

        static final ShipType[] HANGAR = {FLECHE, COMETE, BASTION, SPECTRE, ECLIPSE};
    }

    /** Condition de déblocage d'une couleur. */
    enum Need { NIVEAU, ELIMINATIONS, VICTOIRES, VAGUE }

    enum Paint {
        ORBIT("orbit", "Bleu ORBIT", 0x3A7FFF, Need.NIVEAU, 1),
        GLACE("glace", "Glace", 0x62CFF4, Need.NIVEAU, 1),
        OR("or", "Or", 0xE0B864, Need.NIVEAU, 2),
        NEON("neon", "Néon rose", 0xFF4FD8, Need.NIVEAU, 4),
        PLASMA("plasma", "Vert plasma", 0x7CFF8A, Need.NIVEAU, 5),
        NEBULEUSE("nebuleuse", "Violet nébuleuse", 0xC79BFF, Need.NIVEAU, 8),
        BRAISE("braise", "Rouge braise", 0xE0484A, Need.ELIMINATIONS, 50),
        ETOILE("etoile", "Blanc étoile", 0xF4F6FA, Need.VICTOIRES, 10),
        VIDE("vide", "Noir du vide", 0x30343C, Need.VAGUE, 8),
        SOLAIRE("solaire", "Orange solaire", 0xFF8A2A, Need.ELIMINATIONS, 200);

        final String id, label;
        final int rgb;
        final Need need;
        final int amount;

        Paint(String id, String label, int rgb, Need need, int amount) {
            this.id = id;
            this.label = label;
            this.rgb = rgb;
            this.need = need;
            this.amount = amount;
        }

        static Paint of(String id) {
            for (Paint p : values()) {
                if (p.id.equalsIgnoreCase(id)) {
                    return p;
                }
            }
            return ORBIT;
        }

        boolean unlocked(Profiles.Profile p) {
            return switch (need) {
                case NIVEAU -> p.level() >= amount;
                case ELIMINATIONS -> p.kills >= amount;
                case VICTOIRES -> p.wins >= amount;
                case VAGUE -> p.bestWave >= amount;
            };
        }

        String how() {
            return switch (need) {
                case NIVEAU -> "niveau de pilote " + amount;
                case ELIMINATIONS -> amount + " éliminations";
                case VICTOIRES -> amount + " victoires";
                case VAGUE -> "vague " + amount + " du Convoi";
            };
        }
    }

    static final String[] BOT_NAMES = {"Nova-7", "Kepler", "Véga", "Orion-K", "Lyra", "Pulsar", "Quasar", "Altaïr", "Sirius", "Halley",
            "Cassiopée", "Antarès", "Rigel", "Deneb", "Andromède", "Ganymède"};
    static final int[] BOT_COLORS = {0xFF4FD8, 0x7CFF8A, 0xC79BFF, 0xE0484A, 0xFF8A2A, 0xF4F6FA, 0xE0B864, 0x62CFF4};
    static final int TEAM_BLUE = 0x3A7FFF, TEAM_GOLD = 0xE0B864, PIRATE = 0xE0282A, CARGO_COLOR = 0x62CFF4;
}
