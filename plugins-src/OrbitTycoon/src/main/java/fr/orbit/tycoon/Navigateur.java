package fr.orbit.tycoon;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Navigateur orbital : objet lié, clic droit = menu de téléportation vers la base et les 5 zones. */
final class Navigateur implements Listener {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int[] SLOTS_ZONES = {11, 12, 13, 14, 15};
    private static final int SLOT_BASE = 22;
    private static final int SLOT_ILE = 24;

    private final OrbitTycoon plugin;
    private final Liens liens;
    private final NamespacedKey cle;
    private Pack pack;
    private IlesJoueurs iles;

    void iles(IlesJoueurs iles) { this.iles = iles; }

    private ItemStack iconeIle(Player p) {
        IlesJoueurs.Ile ile = iles.ileDe(p.getUniqueId());
        boolean ici = ile != null && IlesJoueurs.estMonde(p.getWorld()) && iles.ileA(p.getX(), p.getZ()) == ile;
        ItemStack it = new ItemStack(Material.GRASS_BLOCK);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(texte("<#7CFC9A><bold>Mon île</bold>"));
        List<Component> lore = new ArrayList<>();
        if (ile == null) {
            lore.add(texte("<gray>Tu n'as pas encore d'île."));
            lore.add(Component.empty());
            lore.add(texte("<#62CFF4>➜ Clic pour créer ton île"));
        } else {
            int t = iles.taille(ile);
            lore.add(texte("<white>" + MM.escapeTags(ile.nom)));
            lore.add(texte("<gray>Niveau <#E0B864>" + (ile.palier + 1) + "<gray> — zone <#E0B864>" + t + " × " + t));
            lore.add(texte("<gray>Membres : <white>" + ile.membres.size()));
            lore.add(Component.empty());
            lore.add(texte(ici ? "<#E0B864>● Tu es ici" : "<#62CFF4>➜ Clic pour t'y téléporter"));
        }
        meta.lore(lore);
        if (ici) meta.setEnchantmentGlintOverride(true);
        it.setItemMeta(meta);
        return it;
    }

    Navigateur(OrbitTycoon plugin, Liens liens) {
        this.plugin = plugin;
        this.liens = liens;
        this.cle = new NamespacedKey(plugin, "navigateur");
    }

    static Component texte(String mm) {
        return MM.deserialize(mm).decoration(TextDecoration.ITALIC, false);
    }

    ItemStack creer() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(texte("<gradient:#62CFF4:#E0B864><bold>Navigateur orbital</bold></gradient> <gray>(clic droit)"));
        meta.lore(List.of(
                texte("<gray>Téléporte-toi vers les zones de la station :"),
                texte("<#3FA9F5>Pêche<gray>, <#C8894B>Bûcheron<gray>, <#F5D442>Agriculture<gray>,"),
                texte("<#FF4D4D>Mobzone<gray>, <#B0BEC5>Mine"),
                Component.empty(),
                texte("<#E0B864>✦ Lié à toi : impossible à jeter ou échanger.")));
        meta.setEnchantmentGlintOverride(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(cle, PersistentDataType.BYTE, (byte) 1);
        liens.marquer(meta);
        item.setItemMeta(meta);
        return item;
    }

    boolean est(ItemStack item) {
        if (item == null || item.getType() != Material.NETHER_STAR || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(cle, PersistentDataType.BYTE);
    }

    void donner(Player p) {
        PlayerInventory inv = p.getInventory();
        int slot = Math.max(0, Math.min(8, plugin.getConfig().getInt("navigateur.slot", 8)));
        for (int i = 0; i < inv.getSize(); i++) if (est(inv.getItem(i))) inv.setItem(i, null);
        if (est(p.getItemOnCursor())) p.setItemOnCursor(null);
        ItemStack occupant = inv.getItem(slot);
        if (occupant != null && !occupant.getType().isAir()) {
            inv.setItem(slot, null);
            for (ItemStack reste : inv.addItem(occupant).values()) p.getWorld().dropItemNaturally(p.getLocation(), reste);
        }
        inv.setItem(slot, creer());
    }

    // ------------------------------------------------------------------ menu

    private static final class Menu implements InventoryHolder {
        private Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    void pack(Pack pack) { this.pack = pack; }

    /**
     * Titre habillé (pack chargé) : recul de 18 px, fond du menu (tools/gen-hud.ps1, avance 197), recul de 153 px,
     * puis le nom en or centré sur le bandeau (« Navigateur orbital » en gras = 109 px, centre à 80 px du début).
     */
    private static final Component TITRE_HABILLE = Component.text()
            .append(Component.text("").font(Key.key("orbittycoon", "menu")).color(NamedTextColor.WHITE))
            .append(Component.text("Navigateur orbital").color(TextColor.color(0xE0B864)).decoration(TextDecoration.BOLD, true))
            .build();

    void ouvrir(Player p) {
        plugin.tuto().fait(p, Tuto.Etape.NAVIGATEUR);
        Menu holder = new Menu();
        boolean habille = pack != null && pack.charge(p);
        Inventory inv = Bukkit.createInventory(holder, 27, habille ? TITRE_HABILLE
                : texte("<gradient:#62CFF4:#E0B864><bold>Navigateur orbital</bold></gradient>"));
        holder.inv = inv;
        if (!habille) {
            ItemStack fondA = vitre(Material.BLUE_STAINED_GLASS_PANE), fondB = vitre(Material.BLACK_STAINED_GLASS_PANE);
            for (int i = 0; i < 27; i++) inv.setItem(i, (i % 2 == 0) ? fondA : fondB);
        }
        Zone ici = IlesJoueurs.estMonde(p.getWorld()) ? null : Zone.at(p.getLocation());
        List<Zone> zones = Zone.farms();
        for (int i = 0; i < zones.size(); i++) inv.setItem(SLOTS_ZONES[i], icone(zones.get(i), zones.get(i) == ici));
        inv.setItem(SLOT_BASE, icone(Zone.BASE, ici == Zone.BASE));
        if (iles != null) inv.setItem(SLOT_ILE, iconeIle(p));
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1.6f);
    }

    private ItemStack vitre(Material m) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setHideTooltip(true);
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack icone(Zone z, boolean ici) {
        ItemStack it = new ItemStack(z.icone);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(texte(z.nom));
        List<Component> lore = new ArrayList<>();
        for (String l : z.description) lore.add(texte(l));
        lore.add(Component.empty());
        lore.add(texte(ici ? "<#E0B864>● Tu es ici" : "<#62CFF4>➜ Clic pour t'y téléporter"));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        if (ici) meta.setEnchantmentGlintOverride(true);
        it.setItemMeta(meta);
        return it;
    }

    void teleporter(Player p, Zone z) { teleporter(p, z, z.arrivee(plugin.monde())); }

    void teleporter(Player p, Zone z, Location dest) {
        p.teleportAsync(dest).thenAccept(ok -> {
            if (!ok) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                p.playSound(p.getLocation(), Sound.BLOCK_PORTAL_TRAVEL, 0.25f, 1.6f);
                p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.2f);
                p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.05);
                p.showTitle(Title.title(MM.deserialize(z.nom), MM.deserialize(z.description.get(0)),
                        Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1500), Duration.ofMillis(400))));
            });
        });
    }

    // ------------------------------------------------------------------ événements

    @EventHandler(priority = EventPriority.HIGH)
    public void clicDroit(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!est(e.getItem())) return;
        e.setCancelled(true);
        ouvrir(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void menu(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getView().getTopInventory().getHolder() instanceof Menu) {
            e.setCancelled(true);
            int slot = e.getRawSlot();
            if (slot == SLOT_ILE && iles != null) {
                p.closeInventory();
                if (iles.ileDe(p.getUniqueId()) == null) p.performCommand("is create");
                else iles.home(p);
                return;
            }
            Zone z = slot == SLOT_BASE ? Zone.BASE : null;
            for (int i = 0; i < SLOTS_ZONES.length; i++) if (slot == SLOTS_ZONES[i]) z = Zone.farms().get(i);
            if (z == null) return;
            p.closeInventory();
            teleporter(p, z);
            return;
        }
        // Clic sur le navigateur dans son propre inventaire : ouvre le menu.
        if (est(e.getCurrentItem()) && e.getView().getTopInventory().getType() == InventoryType.CRAFTING)
            Bukkit.getScheduler().runTask(plugin, () -> ouvrir(p));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void glisser(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }
}
