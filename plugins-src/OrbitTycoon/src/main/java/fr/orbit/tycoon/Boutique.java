package fr.orbit.tycoon;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Boutique du spawn : un PNJ astronaute (mannequin au skin du pack) sous une arche dorée, sur chacune des
 * 5 avenues qui mènent aux anneaux-portails. Clic droit sur un PNJ ou /boutique : ouvre le menu.
 * Pour l'instant les rayons affichent « Bientôt » (boutique future 100 % cosmétique, payée en Plasma : aucun pay-to-win).
 * Blocs posés une fois par « /tycoon boutique poser » ; PNJ et titres recréés au chargement de leur chunk.
 */
final class Boutique implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int Y = Zone.Y;
    private static final String TAG = "orbit_boutique";
    /** Distance au centre du spawn, et décalage du PNJ sur le côté de l'avenue. */
    private static final double R = 34, COTE = 7.5, ARCHE = 9.5;

    private final OrbitTycoon plugin;
    private final int n = Zone.farms().size();
    private final Entity[] pnjs = new Entity[n];
    private final TextDisplay[] titres = new TextDisplay[n];

    Boutique(OrbitTycoon plugin) { this.plugin = plugin; }

    // ------------------------------------------------------------------ positions

    /** Direction de l'avenue i (vers l'anneau-portail de la zone i). */
    private static double angle(int i) { return Math.toRadians(Zone.farms().get(i).angle); }

    /** Point (x, z) de l'avenue i, à la distance r du centre, décalé de q sur le côté (tangente). */
    private static double[] point(int i, double r, double q) {
        double a = angle(i);
        return new double[]{Math.cos(a) * r - Math.sin(a) * q, Math.sin(a) * r + Math.cos(a) * q};
    }

    private Location posPnj(int i) {
        double[] p = point(i, R, COTE);
        return new Location(plugin.monde(), Math.floor(p[0]) + 0.5, Y + 1, Math.floor(p[1]) + 0.5,
                Zone.yaw(-Math.cos(angle(i)), -Math.sin(angle(i))), 0f);
    }

    /** Point de téléportation : au milieu de la 1re avenue, tourné vers le PNJ Boutique. */
    Location pointTp() {
        double[] p = point(0, R, 2);
        return new Location(plugin.monde(), Math.floor(p[0]) + 0.5, Y + 1, Math.floor(p[1]) + 0.5,
                Zone.yaw(-Math.sin(angle(0)), Math.cos(angle(0))), 0f);
    }

    private Location posTitre(int i) {
        double[] p = point(i, R, 0);
        return new Location(plugin.monde(), Math.floor(p[0]) + 0.5, Y + 12, Math.floor(p[1]) + 0.5);
    }

    // ------------------------------------------------------------------ cycle de vie

    void demarrer() {
        for (Entity e : plugin.monde().getEntities()) if (e.getScoreboardTags().contains(TAG)) e.remove();
        rafraichir();
    }

    void arreter() {
        for (int i = 0; i < n; i++) {
            if (pnjs[i] != null && pnjs[i].isValid()) pnjs[i].remove();
            if (titres[i] != null && titres[i].isValid()) titres[i].remove();
        }
    }

    /** Vrai si l'avenue i a une boutique (config boutique.avenues = identifiants des zones). */
    private boolean active(int i) {
        List<String> l = plugin.getConfig().getStringList("boutique.avenues");
        return l.isEmpty() ? Zone.farms().get(i) == Zone.MOBZONE : l.contains(Zone.farms().get(i).id);
    }

    private void rafraichir() {
        for (int i = 0; i < n; i++) {
            if (active(i)) rafraichirAvenue(i);
            else {
                if (pnjs[i] != null && pnjs[i].isValid()) pnjs[i].remove();
                if (titres[i] != null && titres[i].isValid()) titres[i].remove();
            }
        }
    }

    /** Après /tycoon reload : PNJ des avenues ajoutées ou retirées. */
    void recharger() { rafraichir(); }

    private void rafraichirAvenue(int i) {
        Location l = posPnj(i);
        if (l.isChunkLoaded() && (pnjs[i] == null || !pnjs[i].isValid())) pnjs[i] = l.getWorld().spawn(l, Mannequin.class, m -> {
            Pnjs.preparer(m, l, "astronaute", "<gradient:#62CFF4:#E0B864><bold>Boutique</bold></gradient>");
            m.addScoreboardTag(TAG);
        });
        Location t = posTitre(i);
        if (t.isChunkLoaded() && (titres[i] == null || !titres[i].isValid())) titres[i] = t.getWorld().spawn(t, TextDisplay.class, d -> {
            d.text(MM.deserialize("<gradient:#62CFF4:#E0B864><bold>BOUTIQUE ORBIT</bold></gradient>\n<gray>Cosmétiques de la station"));
            d.setPersistent(false);
            d.setBillboard(Display.Billboard.VERTICAL);
            d.setShadowed(true);
            d.setViewRange(3f);
            d.setBackgroundColor(Color.fromARGB(130, 8, 16, 32));
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(2.2f, 2.2f, 2.2f), new AxisAngle4f()));
            d.addScoreboardTag(TAG);
        });
    }

    @EventHandler
    public void chunk(ChunkLoadEvent e) {
        if (!e.getWorld().equals(plugin.monde())) return;
        for (int i = 0; i < n; i++)
            for (Location l : new Location[]{posPnj(i), posTitre(i)})
                if (l.getBlockX() >> 4 == e.getChunk().getX() && l.getBlockZ() >> 4 == e.getChunk().getZ()) {
                    Bukkit.getScheduler().runTask(plugin, this::rafraichir);
                    return;
                }
    }

    @EventHandler
    public void degats(EntityDamageEvent e) {
        if (e.getEntity().getScoreboardTags().contains(TAG)) e.setCancelled(true);
    }

    @EventHandler
    public void clicPnj(PlayerInteractEntityEvent e) {
        if (!e.getRightClicked().getScoreboardTags().contains(TAG)) return;
        e.setCancelled(true);
        if (e.getHand() == EquipmentSlot.HAND) ouvrir(e.getPlayer());
    }

    @EventHandler
    public void clicPnjPrecis(PlayerInteractAtEntityEvent e) {
        if (e.getRightClicked().getScoreboardTags().contains(TAG)) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ menu

    private static final class Menu implements InventoryHolder {
        private Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    void ouvrir(Player p) {
        Menu h = new Menu();
        Inventory inv = Bukkit.createInventory(h, 27, Habillage.titre(plugin, p, 3, "Boutique Orbit"));
        h.inv = inv;
        long plasma = plugin.monnaies().solde(p.getUniqueId(), Monnaies.Monnaie.PLASMA);
        inv.setItem(11, objet(Material.AMETHYST_SHARD, "<#C77DFF><bold>Cosmétiques",
                "<gray>Titres, particules, compagnons…", "<gray>Payés en <#C77DFF>Plasma</#C77DFF>.", "", "<#E0B864>Bientôt disponible"));
        inv.setItem(13, objet(Material.NETHER_STAR, "<#C77DFF><bold>Ton Plasma",
                "<white>" + Monnaies.Monnaie.PLASMA.format(plasma), "", "<dark_gray>100 % cosmétique : aucun pay-to-win."));
        inv.setItem(15, objet(Material.EMERALD, "<#E0B864><bold>Hôtel des ventes",
                "<gray>Achète et vends entre joueurs", "<gray>en <#E0B864>Crédits orbitaux</#E0B864>.", "", "<#E0B864>➜ Clic pour ouvrir <dark_gray>(/hdv)"));
        Habillage.fond(plugin, p, inv);
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.2f);
    }

    private static ItemStack objet(Material m, String nom, String... lignes) {
        ItemStack it = new ItemStack(m);
        it.editMeta(meta -> {
            meta.displayName(MM.deserialize(nom).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            for (String l : lignes) lore.add(MM.deserialize(l).decoration(TextDecoration.ITALIC, false));
            if (!lore.isEmpty()) meta.lore(lore);
        });
        return it;
    }

    @EventHandler
    public void clicMenu(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Menu)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() != e.getView().getTopInventory() || !(e.getWhoClicked() instanceof Player p)) return;
        if (e.getSlot() == 15) {
            plugin.hdv().ouvrir(p, 0);
        } else if (e.getSlot() == 11) {
            OrbitTycoon.msg(p, "<gray>Ce rayon de la boutique ouvrira bientôt !");
            p.playSound(p.getLocation(), Sound.BLOCK_CHEST_LOCKED, 0.6f, 1.3f);
        }
    }

    @EventHandler
    public void glisser(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ construction (une seule fois)

    /** Arche dorée au-dessus de l'avenue, petite estrade sous le PNJ. Ne remplace que de l'air (et le sol de l'estrade). */
    int poser() {
        int poses = 0;
        for (int i = 0; i < n; i++) if (active(i)) poses += poser(i);
        return poses;
    }

    private static final java.util.Set<Material> BLOCS_ARCHE = java.util.EnumSet.of(Material.GOLD_BLOCK,
            Material.LIGHT_BLUE_CONCRETE, Material.SEA_LANTERN, Material.END_ROD, Material.LIGHT_BLUE_STAINED_GLASS);

    /**
     * Retire l'arche et l'estrade des avenues sans boutique (seulement les blocs que poser() met à ces endroits)
     * et remet le dallage d'origine du spawn sous l'estrade (même motif que Plans.base). Renvoie le nombre de blocs changés.
     */
    int retirer() {
        World w = plugin.monde();
        int changes = 0;
        for (int i = 0; i < n; i++) {
            if (active(i)) continue;
            for (int s = -1; s <= 1; s += 2) {
                double[] p = point(i, R, ARCHE * s);
                int x = (int) Math.floor(p[0]), z = (int) Math.floor(p[1]);
                for (int y = Y + 1; y <= Y + 11; y++) changes += vider(w, x, y, z);
            }
            for (double q = -ARCHE; q <= ARCHE; q += 0.5) {
                double[] p = point(i, R, q);
                int x = (int) Math.floor(p[0]), z = (int) Math.floor(p[1]);
                changes += vider(w, x, Y + 9, z) + vider(w, x, Y + 8, z);
            }
            Location l = posPnj(i);
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                int x = l.getBlockX() + dx, z = l.getBlockZ() + dz;
                Block b = w.getBlockAt(x, Y, z);
                if (!BLOCS_ARCHE.contains(b.getType())) continue;
                b.setType((((x >> 2) + (z >> 2)) & 1) == 0 ? Material.SMOOTH_QUARTZ : Material.CALCITE, false);
                changes++;
            }
        }
        return changes;
    }

    private static int vider(World w, int x, int y, int z) {
        Block b = w.getBlockAt(x, y, z);
        if (!BLOCS_ARCHE.contains(b.getType())) return 0;
        b.setType(Material.AIR, false);
        return 1;
    }

    private int poser(int i) {
        World w = plugin.monde();
        int poses = 0;
        // Piliers de l'arche, de part et d'autre de l'avenue.
        for (int s = -1; s <= 1; s += 2) {
            double[] p = point(i, R, ARCHE * s);
            int x = (int) Math.floor(p[0]), z = (int) Math.floor(p[1]);
            for (int y = Y + 1; y <= Y + 9; y++) poses += air(w, x, y, z, y % 2 == 0 ? Material.GOLD_BLOCK : Material.LIGHT_BLUE_CONCRETE);
            poses += air(w, x, Y + 10, z, Material.SEA_LANTERN);
            poses += air(w, x, Y + 11, z, Material.END_ROD);
        }
        // Poutre de l'arche.
        for (double q = -ARCHE; q <= ARCHE; q += 0.5) {
            double[] p = point(i, R, q);
            int x = (int) Math.floor(p[0]), z = (int) Math.floor(p[1]);
            poses += air(w, x, Y + 9, z, Math.abs(q) < 0.6 ? Material.SEA_LANTERN : Material.GOLD_BLOCK);
            poses += air(w, x, Y + 8, z, Material.LIGHT_BLUE_STAINED_GLASS);
        }
        // Estrade du PNJ (3 × 3), lanterne au centre.
        Location l = posPnj(i);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            Block b = w.getBlockAt(l.getBlockX() + dx, Y, l.getBlockZ() + dz);
            if (!w.getBlockAt(b.getX(), Y + 1, b.getZ()).getType().isAir()) continue;
            b.setType(dx == 0 && dz == 0 ? Material.SEA_LANTERN : (dx == 0 || dz == 0) ? Material.GOLD_BLOCK : Material.LIGHT_BLUE_CONCRETE, false);
            poses++;
        }
        return poses;
    }

    private static int air(World w, int x, int y, int z, Material m) {
        Block b = w.getBlockAt(x, y, z);
        if (!b.getType().isAir()) return 0;
        b.setType(m, false);
        return 1;
    }
}
