package fr.orbit.vol;

import org.bukkit.Color;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** Les Orbitos, leurs raretés et leurs mutations. Tout le contenu du mode est ici. */
public final class Catalogue {

    public enum Rarete {
        COMMUN("Commun", "<#B8C2CC>", 600),
        RARE("Rare", "<#3A7FFF>", 250),
        EPIQUE("Épique", "<#A64DFF>", 100),
        LEGENDAIRE("Légendaire", "<#E0B864>", 35),
        MYTHIQUE("Mythique", "<#FF4D6D>", 10),
        COSMIQUE("Cosmique", "<#62CFF4>", 3),
        SECRET("Secret", "<gradient:#FF4D6D:#E0B864:#62CFF4>", 0.6);

        public final String nom;
        public final String couleur;
        public final double poids;

        Rarete(String nom, String couleur, double poids) {
            this.nom = nom;
            this.couleur = couleur;
            this.poids = poids;
        }

        public String etiquette() {
            return couleur + nom + "<reset>";
        }
    }

    public enum Mutation {
        AUCUNE("", 1, null),
        OR("<#FFD447>Or ", 2, Color.fromRGB(0xFFD447)),
        DIAMANT("<#7FF6FF>Diamant ", 3, Color.fromRGB(0x7FF6FF)),
        ARCENCIEL("<rainbow>Arc-en-ciel</rainbow> ", 5, null),
        METEORE("<#FF7A1A>Météore ", 8, Color.fromRGB(0xFF7A1A));

        public final String prefixe;
        public final int mult;
        public final Color couleur;

        Mutation(String prefixe, int mult, Color couleur) {
            this.prefixe = prefixe;
            this.mult = mult;
            this.couleur = couleur;
        }
    }

    public record Orbito(String id, String nom, Rarete rarete, EntityType type, double taille, long prix, long revenu) {
        public String nomComplet(Mutation m) {
            return m.prefixe + rarete.couleur + nom + "<reset>";
        }
    }

    public static final Map<String, Orbito> TOUS = new LinkedHashMap<>();

    private static void o(String id, String nom, Rarete r, EntityType t, double taille, long prix, long revenu) {
        TOUS.put(id, new Orbito(id, nom, r, t, taille, prix, revenu));
    }

    static {
        // Communs : on démarre avec 100 crédits.
        o("dodo_turbo", "Dodo Turbo", Rarete.COMMUN, EntityType.CHICKEN, 1.6, 25, 1);
        o("lapin_satellite", "Lapin Satellite", Rarete.COMMUN, EntityType.RABBIT, 1.6, 45, 2);
        o("grenouille_wifi", "Grenouille Wifi", Rarete.COMMUN, EntityType.FROG, 1.6, 70, 3);
        o("tatou_patate", "Tatou Patate", Rarete.COMMUN, EntityType.ARMADILLO, 1.3, 100, 4);
        o("cochon_fusee", "Cochon Fusée", Rarete.COMMUN, EntityType.PIG, 1.0, 150, 5);
        o("mouton_nebuleuse", "Mouton Nébuleuse", Rarete.COMMUN, EntityType.SHEEP, 1.0, 200, 6);
        // Rares
        o("axolotl_astronaute", "Axolotl Astronaute", Rarete.RARE, EntityType.AXOLOTL, 1.6, 500, 10);
        o("perroquet_radar", "Perroquet Radar", Rarete.RARE, EntityType.PARROT, 1.8, 750, 14);
        o("renard_orbital", "Renard Orbital", Rarete.RARE, EntityType.FOX, 1.3, 1_000, 18);
        o("tortue_hyperdrive", "Tortue Hyperdrive", Rarete.RARE, EntityType.TURTLE, 1.0, 1_250, 22);
        o("chat_quantique", "Chat Quantique", Rarete.RARE, EntityType.CAT, 1.5, 1_500, 25);
        // Épiques
        o("bonhomme_cryo", "Bonhomme Cryo", Rarete.EPIQUE, EntityType.SNOW_GOLEM, 1.2, 4_000, 50);
        o("abeille_reacteur", "Abeille Réacteur", Rarete.EPIQUE, EntityType.BEE, 2.5, 5_500, 65);
        o("loup_comete", "Loup Comète", Rarete.EPIQUE, EntityType.WOLF, 1.4, 7_000, 80);
        o("vache_lunaire", "Vache Lunaire", Rarete.EPIQUE, EntityType.MOOSHROOM, 1.1, 8_500, 95);
        o("panda_gravite", "Panda Gravité", Rarete.EPIQUE, EntityType.PANDA, 1.0, 10_000, 110);
        // Légendaires
        o("fee_antenne", "Fée Antenne", Rarete.LEGENDAIRE, EntityType.ALLAY, 2.0, 30_000, 250);
        o("ours_polaris", "Ours Polaris", Rarete.LEGENDAIRE, EntityType.POLAR_BEAR, 1.0, 45_000, 330);
        o("chameau_stellaire", "Chameau Stellaire", Rarete.LEGENDAIRE, EntityType.CAMEL, 1.0, 60_000, 420);
        o("renifleur_etoiles", "Renifleur d'Étoiles", Rarete.LEGENDAIRE, EntityType.SNIFFER, 0.8, 80_000, 500);
        // Mythiques
        o("dodo_roi", "Dodo Roi", Rarete.MYTHIQUE, EntityType.CHICKEN, 3.2, 250_000, 1_500);
        o("grenouille_supernova", "Grenouille Supernova", Rarete.MYTHIQUE, EntityType.FROG, 3.2, 400_000, 2_400);
        o("titan_boulon", "Titan Boulon", Rarete.MYTHIQUE, EntityType.IRON_GOLEM, 1.1, 600_000, 3_500);
        // Cosmiques
        o("meduse_wifi", "Méduse Wifi", Rarete.COSMIQUE, EntityType.GLOW_SQUID, 1.6, 2_500_000, 12_000);
        o("rex_rikiki", "Rex Rikiki", Rarete.COSMIQUE, EntityType.RAVAGER, 0.6, 4_000_000, 18_000);
        o("gardien_neant", "Gardien du Néant", Rarete.COSMIQUE, EntityType.WARDEN, 0.7, 6_000_000, 25_000);
        // Secrets
        o("astraeos_poche", "Astraeos de Poche", Rarete.SECRET, EntityType.BREEZE, 1.6, 25_000_000, 120_000);
        o("grand_bidule", "Le Grand Bidule Galactique", Rarete.SECRET, EntityType.GHAST, 0.5, 60_000_000, 250_000);
    }

    private Catalogue() {
    }

    public static Orbito get(String id) {
        return TOUS.get(id);
    }

    public static Rarete tirerRarete() {
        double total = 0;
        for (Rarete r : Rarete.values()) total += r.poids;
        double x = ThreadLocalRandom.current().nextDouble(total);
        for (Rarete r : Rarete.values()) {
            x -= r.poids;
            if (x < 0) return r;
        }
        return Rarete.COMMUN;
    }

    public static Orbito tirer() {
        Rarete r = tirerRarete();
        List<Orbito> l = new ArrayList<>();
        for (Orbito o : TOUS.values()) if (o.rarete() == r) l.add(o);
        return l.get(ThreadLocalRandom.current().nextInt(l.size()));
    }

    public static Mutation tirerMutation(boolean meteores) {
        double x = ThreadLocalRandom.current().nextDouble();
        double f = meteores ? 2 : 1;
        if (meteores && x < 0.05) return Mutation.METEORE;
        if (meteores) x -= 0.05;
        if (x < 0.005 * f) return Mutation.ARCENCIEL;
        if (x < 0.030 * f) return Mutation.DIAMANT;
        if (x < 0.100 * f) return Mutation.OR;
        return Mutation.AUCUNE;
    }
}
