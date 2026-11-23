package fr.orbit.tycoon;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
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
 * Accroupi (Shift) + clic droit = outil suivant.
 */
final class Outil implements Listener {

    enum Mode {
        PIOCHE("Pioche", Material.IRON_PICKAXE, "#B0BEC5", "Mine"),
        HACHE("Hache", Material.IRON_AXE, "#C8894B", "Bûcheron"),
        HOUE("Houe", Material.IRON_HOE, "#F5D442", "Agriculture"),
        CANNE("Canne à pêche", Material.FISHING_ROD, "#3FA9F5", "Pêche"),
        EPEE("Épée", Material.IRON_SWORD, "#FF4D4D", "Mobzone");

        final String nom, couleur, zone;
        final Material type;

        Mode(String nom, Material type, String couleur, String zone) {
            this.nom = nom; this.type = type; this.couleur = couleur; this.zone = zone;
        }

        Mode suivant() { return values()[(ordinal() + 1) % values().length]; }
    }

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
        lore.add(texte("<#E0B864>Shift + clic droit <gray>: outil suivant"));
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

    @EventHandler(priority = EventPriority.LOWEST)
    public void changer(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        if (!p.isSneaking() || !est(e.getItem())) return;
        e.setCancelled(true);
        long now = System.currentTimeMillis();
        Long avant = dernierChangement.get(p.getUniqueId());
        if (avant != null && now - avant < 200) return;
        dernierChangement.put(p.getUniqueId(), now);
        Mode m = mode(e.getItem()).suivant();
        p.getInventory().setItemInMainHand(creer(m));
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6f, 1.4f + m.ordinal() * 0.1f);
        p.sendActionBar(texte("<gray>Outil orbital <dark_gray>» <" + m.couleur + "><bold>" + m.nom + "</bold> <dark_gray>(" + m.zone + ")"));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void lancer(PlayerFishEvent e) {
        // Shift + clic droit avec la canne : on change d'outil au lieu de lancer.
        if (e.getState() == PlayerFishEvent.State.FISHING && e.getPlayer().isSneaking()
                && est(e.getPlayer().getInventory().getItemInMainHand())) e.setCancelled(true);
    }
}
