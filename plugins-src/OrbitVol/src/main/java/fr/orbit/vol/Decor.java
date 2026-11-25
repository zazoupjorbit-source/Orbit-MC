package fr.orbit.vol;

import fr.orbit.vol.Arene.Base;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Décor 3D des bases (modèles du pack) : tourelle dont la tête pivote, rayon laser, bouclier hexagonal,
 * borne du bouclier, plaque d'encaissement et socles des places. Les blocs restent dessous pour les collisions
 * (barrières invisibles) ; les entités ne sont jamais sauvegardées.
 */
final class Decor {

    private final Plugin plugin;
    private final World w;
    private final Map<Integer, List<Entity>> fixes = new HashMap<>();
    private final Map<Integer, List<Entity>> socles = new HashMap<>();
    private final Map<Integer, ItemDisplay[]> tourelles = new HashMap<>();
    private final Map<Integer, ItemDisplay> boucliers = new HashMap<>();

    Decor(Plugin plugin, World w) {
        this.plugin = plugin;
        this.w = w;
    }

    private static ItemStack modele(String id) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta();
        m.setItemModel(new NamespacedKey("orbitvol", id));
        it.setItemMeta(m);
        return it;
    }

    private ItemDisplay poser(Location l, String id, Vector3f echelle, Quaternionf rot, boolean lumineux) {
        return w.spawn(l, ItemDisplay.class, d -> {
            d.setItemStack(modele(id));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setPersistent(false);
            d.setViewRange(1.5f);
            if (lumineux) d.setBrightness(new Display.Brightness(15, 15));
            d.setTransformation(new Transformation(new Vector3f(), rot == null ? new Quaternionf() : rot, echelle, new Quaternionf()));
        });
    }

    private static Quaternionf yaw(double degres) {
        return new Quaternionf().rotationY((float) Math.toRadians(degres));
    }

    /** Base occupée : plaque, borne, socles et tourelle. */
    void installer(Base b, int places, boolean tourelle) {
        retirer(b);
        List<Entity> l = new ArrayList<>();
        int[] pl = b.plaque();
        w.getBlockAt(pl[0], pl[1], pl[2]).setType(Material.AIR, false);
        l.add(poser(new Location(w, pl[0] + 0.5, pl[1] + 0.03, pl[2] + 0.5), "pad", new Vector3f(1.6f, 1, 1.6f), null, false));
        int[] k = b.bouton();
        w.getBlockAt(k[0], k[1], k[2]).setType(Material.BARRIER, false);
        // L'écran regarde l'intérieur de la base.
        l.add(poser(new Location(w, k[0] + 0.5, k[1] + 0.5, k[2] + 0.5), "borne", new Vector3f(1, 1, 1), yaw(b.s > 0 ? 0 : 180), false));
        fixes.put(b.idx, l);
        socles(b, places);
        tourelle(b, tourelle);
    }

    void retirer(Base b) {
        List<Entity> l = fixes.remove(b.idx);
        if (l != null) l.forEach(Entity::remove);
        socles(b, 0);
        tourelle(b, false);
        porte(b, false);
        int[] pl = b.plaque();
        w.getBlockAt(pl[0], pl[1], pl[2]).setType(Material.AIR, false);
    }

    void socles(Base b, int debloquees) {
        b.socles(debloquees);
        List<Entity> l = socles.remove(b.idx);
        if (l != null) l.forEach(Entity::remove);
        if (debloquees <= 0) return;
        l = new ArrayList<>();
        for (int i = 0; i < debloquees && i < Profil.PLACES; i++) {
            Location p = b.place(i);
            l.add(poser(new Location(w, p.getBlockX() + 0.5, Arene.Y + 1.02, p.getBlockZ() + 0.5), "socle", new Vector3f(1.3f, 1, 1.3f), null, true));
        }
        socles.put(b.idx, l);
    }

    void tourelle(Base b, boolean presente) {
        ItemDisplay[] t = tourelles.remove(b.idx);
        if (t != null) for (ItemDisplay d : t) d.remove();
        int x = b.cx + 9, z = b.s * 32;
        Material m = presente ? Material.BARRIER : Material.AIR;
        w.getBlockAt(x, Arene.Y + 1, z).setType(m, false);
        w.getBlockAt(x, Arene.Y + 2, z).setType(m, false);
        if (!presente) return;
        ItemDisplay socle = poser(new Location(w, x + 0.5, Arene.Y + 1.5, z + 0.5), "tourelle_socle", new Vector3f(1, 1, 1), null, false);
        ItemDisplay tete = poser(tete(b), "tourelle_tete", new Vector3f(1, 1, 1), versPorte(b, 0), false);
        tete.setInterpolationDuration(4);
        tete.setTeleportDuration(0);
        tourelles.put(b.idx, new ItemDisplay[]{socle, tete});
    }

    private Location tete(Base b) {
        return new Location(w, b.cx + 9.5, Arene.Y + 2.45, b.s * 32 + 0.5);
    }

    /** Canon tourné vers la porte, décalé de « angle » degrés. */
    private static Quaternionf versPorte(Base b, double angle) {
        Vector3f dir = new Vector3f(0, 0, -b.s);
        return new Quaternionf().rotationTo(new Vector3f(0, 0, 1), dir).premul(yaw(angle));
    }

    /** Appelé chaque seconde : la tête balaie la base quand elle ne tire pas. */
    void balayer(long secondes) {
        for (Map.Entry<Integer, ItemDisplay[]> e : tourelles.entrySet()) {
            ItemDisplay tete = e.getValue()[1];
            if (!tete.isValid()) continue;
            Base b = Arene.bases(w)[e.getKey()];
            double a = Math.sin(secondes * 0.6 + e.getKey()) * 55;
            orienter(tete, versPorte(b, a), 18);
        }
    }

    private static void orienter(ItemDisplay d, Quaternionf q, int duree) {
        Transformation t = d.getTransformation();
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(duree);
        d.setTransformation(new Transformation(t.getTranslation(), q, t.getScale(), t.getRightRotation()));
    }

    /** Tir de la tourelle : la tête vise, un rayon laser part du canon puis s'éteint. */
    void tir(Base b, Location cible) {
        ItemDisplay[] t = tourelles.get(b.idx);
        Location depart = tete(b);
        Vector v = cible.toVector().subtract(depart.toVector());
        if (v.lengthSquared() < 0.01) return;
        Vector3f dir = new Vector3f((float) v.getX(), (float) v.getY(), (float) v.getZ()).normalize();
        Quaternionf q = new Quaternionf().rotationTo(new Vector3f(0, 0, 1), dir);
        if (t != null && t[1].isValid()) orienter(t[1], q, 2);
        Location bout = depart.clone().add(dir.x * 1.05, dir.y * 1.05, dir.z * 1.05);
        float len = (float) Math.max(0.2, v.length() - 1.05);
        ItemDisplay laser = poser(bout, "laser", new Vector3f(2.2f, 2.2f, len), q, true);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!laser.isValid()) return;
            laser.setInterpolationDelay(0);
            laser.setInterpolationDuration(6);
            laser.setTransformation(new Transformation(new Vector3f(), q, new Vector3f(0.1f, 0.1f, len), new Quaternionf()));
        }, 2);
        Bukkit.getScheduler().runTaskLater(plugin, laser::remove, 9);
    }

    /** Bouclier : barrières invisibles + écran hexagonal lumineux dans la porte. */
    void porte(Base b, boolean fermee) {
        b.porte(fermee);
        ItemDisplay d = boucliers.remove(b.idx);
        if (d != null) d.remove();
        if (!fermee) return;
        Location l = new Location(w, b.cx + 0.5, Arene.Y + 2.5, b.s * 12 + 0.5);
        boucliers.put(b.idx, poser(l, "bouclier", new Vector3f(7, 3, 1), null, true));
    }

    void toutRetirer() {
        fixes.values().forEach(l -> l.forEach(Entity::remove));
        socles.values().forEach(l -> l.forEach(Entity::remove));
        tourelles.values().forEach(t -> { for (ItemDisplay d : t) d.remove(); });
        boucliers.values().forEach(Entity::remove);
        fixes.clear(); socles.clear(); tourelles.clear(); boucliers.clear();
    }
}
