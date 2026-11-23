package fr.orbit.tycoon;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static fr.orbit.tycoon.Navigateur.texte;

/**
 * Outil orbital : un seul objet lié qui regroupe pioche, hache, houe, canne à pêche et épée.
 * Accroupi (Shift) + clic droit = interface de l'outil (choix de l'outil + fiche) ;
 * accroupi (Shift) + F = outil suivant.
 */
final class Outil implements Listener {

    enum Mode {
        PIOCHE("pioche", "Pioche", Material.IRON_PICKAXE, "#B0BEC5", "Mine", "Minerais en filons, du charbon aux débris antiques"),
        HACHE("hache", "Hache", Material.IRON_AXE, "#C8894B", "Bûcheron", "Chênes de l'île du bûcheron"),
        HOUE("houe", "Houe", Material.IRON_HOE, "#F5D442", "Agriculture", "Blé des champs"),
        CANNE("peche", "Canne à pêche", Material.FISHING_ROD, "#3FA9F5", "Pêche", "Poissons du lac"),
        EPEE("epee", "Épée", Material.IRON_SWORD, "#FF4D4D", "Mobzone", "Monstres des spawners");

        /** id = clé courte sans accent (fichiers joueurs, commandes). */
        final String id, nom, couleur, zone, recolte;
        final Material type;

        Mode(String id, String nom, Material type, String couleur, String zone, String recolte) {
            this.id = id; this.nom = nom; this.type = type; this.couleur = couleur; this.zone = zone; this.recolte = recolte;
        }

        Mode suivant() { return values()[(ordinal() + 1) % values().length]; }

        /** « houe », « pioche », « peche »/« pêche »/« canne », « epee »/« épée », « hache » → Mode (null sinon). */
        static Mode parse(String s) {
            if (s == null) return null;
            String n = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase();
            if (n.equals("canne")) return CANNE;
            for (Mode m : values()) if (m.id.equals(n) || m.name().equalsIgnoreCase(n)) return m;
            return null;
        }
    }

    /** Emplacements du menu (3 lignes) : fiche en haut, les 5 outils au milieu, améliorations en bas. */
    private static final int SLOT_FICHE = 4, SLOT_AMELIORATIONS = 22;
    private static final int[] SLOTS_OUTILS = {11, 12, 13, 14, 15};

    private final OrbitTycoon plugin;
    private final Liens liens;
    private final NamespacedKey cleMode;
    private final Map<UUID, Long> dernierChangement = new ConcurrentHashMap<>();

    Outil(OrbitTycoon plugin, Liens liens) {
        this.plugin = plugin;
        this.liens = liens;
        this.cleMode = new NamespacedKey(plugin, "outil");
    }

    boolean est(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(cleMode, PersistentDataType.STRING);
    }

    Mode mode(ItemStack item) {
        try { return Mode.valueOf(item.getItemMeta().getPersistentDataContainer().get(cleMode, PersistentDataType.STRING)); }
        catch (Exception e) { return Mode.PIOCHE; }
    }

    ItemStack creer(Mode m) {
        ItemStack item = new ItemStack(m.type);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(texte("<gradient:#62CFF4:#E0B864><bold>Outil orbital</bold></gradient> <dark_gray>· <" + m.couleur + "><bold>" + m.nom));
        List<Component> lore = new ArrayList<>();
        lore.add(texte("<gray>Un seul outil pour toute la station."));
        lore.add(Component.empty());
        for (Mode x : Mode.values())
            lore.add(texte((x == m ? "<" + x.couleur + ">▶ <bold>" : "<dark_gray>  ") + x.nom + (x == m ? "</bold>" : "") + " <dark_gray>(" + x.zone + ")"));
        lore.add(Component.empty());
        lore.add(texte("<#E0B864>Shift + clic droit <gray>: interface de l'outil"));
        lore.add(texte("<#E0B864>Shift + F <gray>: outil suivant"));
        lore.add(texte("<#E0B864>✦ Lié à toi : impossible à jeter ou échanger."));
        meta.lore(lore);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(cleMode, PersistentDataType.STRING, m.name());
        liens.marquer(meta);
        item.setItemMeta(meta);
        return item;
    }

    /** Garantit exactement un Outil orbital (garde son mode actuel s'il en avait un). */
    void donner(Player p) {
        PlayerInventory inv = p.getInventory();
        Mode m = Mode.PIOCHE;
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (est(it)) { m = mode(it); inv.setItem(i, null); }
        }
        int slot = Math.max(0, Math.min(8, plugin.getConfig().getInt("outil.slot", 0)));
        ItemStack occupant = inv.getItem(slot);
        if (occupant != null && !occupant.getType().isAir()) {
            inv.setItem(slot, null);
            for (ItemStack reste : inv.addItem(occupant).values()) p.getWorld().dropItemNaturally(p.getLocation(), reste);
        }
        inv.setItem(slot, creer(m));
    }

    /** Mode de l'Outil orbital du joueur (où qu'il soit dans l'inventaire), null s'il n'en a pas. */
    Mode modeActuel(Player p) {
        for (ItemStack it : p.getInventory().getContents()) if (est(it)) return mode(it);
        return null;
    }

    /** Passe l'Outil orbital du joueur sur le mode m, à son emplacement actuel. */
    void equiper(Player p, Mode m) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) if (est(inv.getItem(i))) { inv.setItem(i, creer(m)); break; }
        plugin.tuto().fait(p, Tuto.Etape.OUTIL);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6f, 1.4f + m.ordinal() * 0.1f);
        p.sendActionBar(texte("<gray>Outil orbital <dark_gray>» <" + m.couleur + "><bold>" + m.nom + "</bold> <dark_gray>(" + m.zone + ")"));
    }

    /** Anti double déclenchement (le client envoie parfois deux paquets pour un seul clic). */
    private boolean tropRapide(Player p) {
        long now = System.currentTimeMillis();
        Long avant = dernierChangement.put(p.getUniqueId(), now);
        return avant != null && now - avant < 200;
    }

    // ------------------------------------------------------------------ interface

    private static final class Menu implements InventoryHolder {
        private Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    private Pack pack;

    void pack(Pack pack) { this.pack = pack; }

    /**
     * Titre habillé (pack chargé) : recul de 18 px, fond Orbit de l'interface (tools/gen-hud.ps1, glyphe , avance 197),
     * recul de 134 px, puis « Outil orbital » en or centré sur le bandeau (71 px en gras, centre à 80 px du début).
     */
    private static final Component TITRE_HABILLE = Component.text()
            .append(Component.text("").font(Key.key("orbittycoon", "menu")).color(NamedTextColor.WHITE))
            .append(Component.text("Outil orbital").color(TextColor.color(0xE0B864)).decoration(TextDecoration.BOLD, true))
            .build();

    void ouvrir(Player p) {
        Mode actuel = modeActuel(p);
        if (actuel == null) return;
        plugin.tuto().fait(p, Tuto.Etape.OUTIL);
        Menu holder = new Menu();
        boolean habille = pack != null && pack.charge(p);
        Inventory inv = Bukkit.createInventory(holder, 27, habille ? TITRE_HABILLE
                : texte("<gradient:#62CFF4:#E0B864><bold>Outil orbital</bold></gradient> <dark_gray>· <" + actuel.couleur + ">" + actuel.nom));
        holder.inv = inv;
        if (!habille) {
            ItemStack fondA = vitre(Material.BLUE_STAINED_GLASS_PANE), fondB = vitre(Material.BLACK_STAINED_GLASS_PANE);
            for (int i = 0; i < 27; i++) inv.setItem(i, (i % 2 == 0) ? fondA : fondB);
        }
        inv.setItem(SLOT_FICHE, fiche(p, actuel));
        Mode[] modes = Mode.values();
        for (int i = 0; i < modes.length; i++) inv.setItem(SLOTS_OUTILS[i], iconeOutil(p, modes[i], modes[i] == actuel));
        inv.setItem(SLOT_AMELIORATIONS, iconeAmeliorations());
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.5f, 1.6f);
    }

    private ItemStack vitre(Material m) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setHideTooltip(true);
        it.setItemMeta(meta);
        return it;
    }

    private static String nombre(long n) {
        return String.format("%,d", n).replace(',', ' ').replace(' ', ' ').replace(' ', ' ');
    }

    /** Fiche de l'outil en main : zone, récolte, prestige, soldes. */
    private ItemStack fiche(Player p, Mode m) {
        Monnaies mon = plugin.monnaies();
        UUID id = p.getUniqueId();
        ItemStack it = new ItemStack(m.type);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(texte("<" + m.couleur + "><bold>" + m.nom + "</bold> <dark_gray>· <gray>fiche de l'outil"));
        List<Component> lore = new ArrayList<>();
        lore.add(texte("<gray>Zone : <" + m.couleur + ">" + m.zone));
        lore.add(texte("<gray>Récolte : <white>" + m.recolte));
        lore.add(Component.empty());
        lore.add(texte("<gray>Niveau de l'outil : <#3DDCB0>" + mon.niveau(id, m)));
        lore.add(texte("<gray>Prestige de l'outil : <#E0B864>✪ " + mon.prestige(id, m)));
        lore.add(texte("<gray>Prestige total : <#E0B864>✪ " + mon.prestige(id)));
        lore.add(Component.empty());
        for (Monnaies.Monnaie x : new Monnaies.Monnaie[]{Monnaies.Monnaie.CREDIT, Monnaies.Monnaie.FLUX})
            lore.add(texte("<" + x.couleur + ">" + x.symbole + " " + nombre(mon.solde(id, x)) + " <gray>" + x.pluriel));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.setEnchantmentGlintOverride(true);
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack iconeOutil(Player p, Mode m, boolean enMain) {
        ItemStack it = new ItemStack(m.type);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(texte("<" + m.couleur + "><bold>" + m.nom));
        List<Component> lore = new ArrayList<>();
        lore.add(texte("<gray>Zone : <" + m.couleur + ">" + m.zone));
        lore.add(texte("<gray>" + m.recolte));
        lore.add(texte("<gray>Prestige : <#E0B864>✪ " + plugin.monnaies().prestige(p.getUniqueId(), m)
                + " <dark_gray>· <gray>Niveau <#3DDCB0>" + plugin.monnaies().niveau(p.getUniqueId(), m)));
        lore.add(Component.empty());
        lore.add(texte(enMain ? "<#E0B864>● En main" : "<#62CFF4>➜ Clic gauche : l'équiper"));
        lore.add(texte("<#E0B864>➜ Clic droit : fiche, XP et prestige"));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        if (enMain) meta.setEnchantmentGlintOverride(true);
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack iconeAmeliorations() {
        ItemStack it = new ItemStack(Material.ANVIL);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(texte("<#E0B864><bold>Fiche de l'outil en main"));
        meta.lore(List.of(
                texte("<gray>Niveau, XP, prestige et améliorations."),
                Component.empty(),
                texte("<#E0B864>➜ Clic pour ouvrir")));
        it.setItemMeta(meta);
        return it;
    }

    // ------------------------------------------------------------------ événements

    @EventHandler(priority = EventPriority.LOWEST)
    public void clicDroit(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        if (!p.isSneaking() || !est(e.getItem())) return;
        e.setCancelled(true);
        if (tropRapide(p)) return;
        ouvrir(p);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void suivant(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        ItemStack main = p.getInventory().getItemInMainHand();
        if (!p.isSneaking() || !est(main)) return;
        e.setCancelled(true);
        if (tropRapide(p)) return;
        equiper(p, mode(main).suivant());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void menu(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Menu)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        int slot = e.getRawSlot();
        Mode choisi = null;
        for (int i = 0; i < SLOTS_OUTILS.length; i++) if (slot == SLOTS_OUTILS[i]) choisi = Mode.values()[i];
        if (choisi == null) {
            Mode enMain = modeActuel(p);
            if (slot == SLOT_AMELIORATIONS && enMain != null) plugin.progression().ouvrir(p, enMain);
            return;
        }
        if (e.isRightClick()) { plugin.progression().ouvrir(p, choisi); return; }
        Mode avant = modeActuel(p);
        p.closeInventory();
        if (choisi != avant) equiper(p, choisi);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void glisser(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void lancer(PlayerFishEvent e) {
        // Shift + clic droit avec la canne : on ouvre l'interface au lieu de lancer.
        if (e.getState() == PlayerFishEvent.State.FISHING && e.getPlayer().isSneaking()
                && est(e.getPlayer().getInventory().getItemInMainHand())) e.setCancelled(true);
    }
}
