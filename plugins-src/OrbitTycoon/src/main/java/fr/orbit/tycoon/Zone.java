package fr.orbit.tycoon;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.List;

/**
 * Les lieux du Tycoon : la Base Orbit (spawn, au centre) et 5 îles de farm placées en étoile à 1100 blocs.
 * Tout le dessin (Plans), les portails (Portails) et les téléportations partent de ces valeurs.
 */
public enum Zone {
    BASE("base", "<gradient:#62CFF4:#E0B864><bold>Base Orbit</bold></gradient>", "#62CFF4", Material.BEACON, -1, 125,
            List.of("<gray>Le spawn de la station.", "<gray>Les anneaux-portails mènent aux zones.")),
    PECHE("peche", "<#3FA9F5><bold>Pêche</bold>", "#3FA9F5", Material.FISHING_ROD, 0, 220,
            List.of("<gray>Le lagon et sa station-télescope.", "<gray>Ici, on ne pêche que des poissons.")),
    BUCHERON("bucheron", "<#C8894B><bold>Bûcheron</bold>", "#C8894B", Material.IRON_AXE, 72, 220,
            List.of("<gray>La forêt suspendue.", "<gray>Les arbres repoussent tout seuls.")),
    AGRICULTURE("agriculture", "<#F5D442><bold>Agriculture</bold>", "#F5D442", Material.WHEAT, 144, 220,
            List.of("<gray>Les champs de blé de la serre.", "<gray>Le blé mûr repousse tout seul.")),
    MOBZONE("mobzone", "<#FF4D4D><bold>Mobzone</bold>", "#FF4D4D", Material.IRON_SWORD, 216, 220,
            List.of("<gray>La ceinture des ombres.", "<gray>Des spawners de tous les monstres.")),
    MINE("mine", "<#B0BEC5><bold>Mine</bold>", "#B0BEC5", Material.IRON_PICKAXE, 288, 220,
            List.of("<gray>L'astéroïde minier : 1 étage = 1 minerai.", "<gray>Plus tu descends, plus c'est rare."));

    public static final int Y = 100;
    /** Distance entre le centre du spawn et le centre de chaque île. */
    public static final int DISTANCE = 1100;
    /** Rayon de l'avenue du spawn où se trouve l'anneau-portail de chaque zone. */
    public static final int PORTAIL_BASE = 100;
    /** Rayon intérieur des anneaux-portails (le disque qui téléporte). */
    public static final double RAYON_PORTAIL = 5.5;

    public final String id, nom, couleur;
    public final Material icone;
    /** Angle (degrés) de la zone vu depuis le spawn ; -1 pour la base. */
    public final double angle;
    public final int rayon, cx, cz;
    public final List<String> description;

    Zone(String id, String nom, String couleur, Material icone, double angle, int rayon, List<String> description) {
        this.id = id; this.nom = nom; this.couleur = couleur; this.icone = icone;
        this.angle = angle; this.rayon = rayon; this.description = description;
        if (angle < 0) { cx = 0; cz = 0; }
        else {
            cx = (int) Math.round(Math.cos(Math.toRadians(angle)) * DISTANCE);
            cz = (int) Math.round(Math.sin(Math.toRadians(angle)) * DISTANCE);
        }
    }

    public boolean estBase() { return this == BASE; }

    /** Direction (unitaire) depuis le spawn vers la zone. */
    public double ux() { return Math.cos(Math.toRadians(angle)); }
    public double uz() { return Math.sin(Math.toRadians(angle)); }

    /** Yaw Minecraft pour regarder dans la direction (dx, dz). */
    public static float yaw(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    /** Point d'arrivée : sur l'île, côté spawn, en regardant vers le centre de l'île. */
    public Location arrivee(World w) {
        if (estBase()) return new Location(w, 0.5, Y + 1, 40.5, 180f, 0f);
        double r = rayon - 38;
        return new Location(w, cx - ux() * r + 0.5, Y + 1, cz - uz() * r + 0.5, yaw(ux(), uz()), 0f);
    }

    /** Point d'arrivée au spawn quand on revient de cette zone : devant son anneau, face au centre. */
    public Location retourBase(World w) {
        double r = PORTAIL_BASE - 12;
        return new Location(w, ux() * r + 0.5, Y + 1, uz() * r + 0.5, yaw(-ux(), -uz()), 0f);
    }

    /** Centre de l'anneau-portail de cette zone sur le spawn ; normale = direction radiale. */
    public double[] portailBase() {
        return new double[]{ux() * PORTAIL_BASE + 0.5, Y + 6.5, uz() * PORTAIL_BASE + 0.5, ux(), uz()};
    }

    /** Centre de l'anneau-portail de retour, sur l'île (côté spawn). */
    public double[] portailRetour() {
        double r = rayon - 22;
        return new double[]{cx - ux() * r + 0.5, Y + 6.5, cz - uz() * r + 0.5, ux(), uz()};
    }

    public double distance(double x, double z) {
        double dx = x - cx, dz = z - cz;
        return Math.sqrt(dx * dx + dz * dz);
    }

    public static Zone at(Location loc) {
        for (Zone z : values()) if (z.distance(loc.getX(), loc.getZ()) <= z.rayon + 15) return z;
        return null;
    }

    public static Zone parse(String s) {
        if (s == null) return null;
        String l = s.toLowerCase().replace("ê", "e").replace("û", "u");
        for (Zone z : values()) if (z.id.equals(l)) return z;
        if (l.equals("spawn")) return BASE;
        return null;
    }

    public static List<Zone> farms() { return List.of(PECHE, BUCHERON, AGRICULTURE, MOBZONE, MINE); }
}
