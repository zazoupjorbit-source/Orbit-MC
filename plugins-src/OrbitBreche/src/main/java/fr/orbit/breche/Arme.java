package fr.orbit.breche;

import org.bukkit.Sound;

/**
 * Armes de BRÈCHE (noms et modèles ORBIT). Tir instantané (rayon), dégâts au corps, multiplicateur à la tête et
 * aux jambes, dispersion de base, pénalité en mouvement, recul (dispersion qui grimpe en rafale) et chute des
 * dégâts avec la distance. Les cadences sont en ticks (20 par seconde).
 */
enum Arme {
    //        id            nom           prix  place auto chargeur réserve cadence dégâts tête jambes dispersion mouvement recul reculMax portée chute(dès,×) plombs recharge son
    ETINCELLE("etincelle", "Étincelle", 0, 1, false, 12, 36, 5, 26, 3.0, 0.85, 0.010, 0.060, 0.012, 0.05, 50, 25, 0.85, 1, 30, Sound.BLOCK_NOTE_BLOCK_HAT, 1.6f,
            "Pistolet de service. Gratuit, précis au coup par coup."),
    FAUCON("faucon", "Faucon", 800, 1, false, 6, 24, 9, 55, 2.9, 0.85, 0.006, 0.080, 0.040, 0.08, 60, 30, 0.9, 1, 40, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.7f,
            "Revolver lourd : une balle dans la tête suffit."),
    ESSAIM("essaim", "Essaim", 1600, 0, true, 30, 90, 2, 26, 2.5, 0.8, 0.016, 0.030, 0.006, 0.07, 40, 15, 0.75, 1, 45, Sound.BLOCK_NOTE_BLOCK_SNARE, 1.9f,
            "Mitraillette : cadence folle, précise en courant."),
    BRISE_ROC("briseroc", "Brise-roc", 1800, 0, false, 6, 24, 18, 11, 2.0, 0.9, 0.085, 0.100, 0.0, 0.0, 18, 8, 0.5, 12, 50, Sound.ENTITY_GENERIC_EXPLODE, 1.9f,
            "Fusil à pompe : 12 plombs, dévastateur de près."),
    COMETE("comete", "Comète", 2900, 0, true, 25, 75, 3, 40, 4.0, 0.85, 0.004, 0.100, 0.012, 0.09, 80, 60, 0.95, 1, 50, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.35f,
            "Fusil d'assaut : 160 à la tête, à toute distance."),
    ZENITH("zenith", "Zénith", 4500, 0, false, 5, 15, 30, 150, 1.7, 0.85, 0.220, 0.250, 0.0, 0.0, 120, 120, 1.0, 1, 70, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.8f,
            "Fusil de précision : accroupi = lunette. Un tir au corps abat.");

    final String id, nom, desc;
    final int prix, place, chargeur, reserve, cadence, degats, portee, chuteDes, plombs, recharge;
    final boolean auto;
    final double tete, jambes, dispersion, mouvement, recul, reculMax, chute;
    final Sound son;
    final float hauteur;

    Arme(String id, String nom, int prix, int place, boolean auto, int chargeur, int reserve, int cadence, int degats, double tete, double jambes,
         double dispersion, double mouvement, double recul, double reculMax, int portee, int chuteDes, double chute, int plombs, int recharge,
         Sound son, float hauteur, String desc) {
        this.id = id;
        this.nom = nom;
        this.prix = prix;
        this.place = place;
        this.auto = auto;
        this.chargeur = chargeur;
        this.reserve = reserve;
        this.cadence = cadence;
        this.degats = degats;
        this.tete = tete;
        this.jambes = jambes;
        this.dispersion = dispersion;
        this.mouvement = mouvement;
        this.recul = recul;
        this.reculMax = reculMax;
        this.portee = portee;
        this.chuteDes = chuteDes;
        this.chute = chute;
        this.plombs = plombs;
        this.recharge = recharge;
        this.son = son;
        this.hauteur = hauteur;
        this.desc = desc;
    }

    boolean primaire() {
        return place == 0;
    }

    /** Dégâts d'une balle (ou d'un plomb) à une distance donnée. */
    double degats(double distance) {
        return distance <= chuteDes ? degats : degats * chute;
    }

    static Arme of(String id) {
        for (Arme a : values()) {
            if (a.id.equalsIgnoreCase(id)) {
                return a;
            }
        }
        return null;
    }

    /** Boucliers (absorbent les deux tiers des dégâts jusqu'à épuisement). */
    enum Bouclier {
        LEGER("leger", "Bouclier léger", 400, 25),
        LOURD("lourd", "Bouclier lourd", 1000, 50);

        final String id, nom;
        final int prix, points;

        Bouclier(String id, String nom, int prix, int points) {
            this.id = id;
            this.nom = nom;
            this.prix = prix;
            this.points = points;
        }
    }
}
