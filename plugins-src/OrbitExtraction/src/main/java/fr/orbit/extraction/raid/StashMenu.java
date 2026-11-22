package fr.orbit.extraction.raid;

import fr.orbit.extraction.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.Base64;
import java.util.UUID;
import java.util.logging.Logger;

/** Coffre de la planque (54 cases), enregistré dans le profil à la fermeture. */
public final class StashMenu implements Listener {

    private static final class Holder implements InventoryHolder {
        final UUID owner;
        Inventory inv;

        Holder(UUID owner) {
            this.owner = owner;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private final ProfileStore profiles;
    private final Logger log;

    public StashMenu(ProfileStore profiles, Logger log) {
        this.profiles = profiles;
        this.log = log;
    }

    public void open(Player p) {
        Profile pr = profiles.get(p);
        Holder h = new Holder(p.getUniqueId());
        Inventory inv = Bukkit.createInventory(h, Profile.STASH_SIZE, Text.mm("<#8E2A20>☣</#8E2A20> <#2B2F36>Planque — coffre"));
        h.inv = inv;
        ItemStack[] items = new ItemStack[Profile.STASH_SIZE];
        for (int i = 0; i < Profile.STASH_SIZE; i++) {
            items[i] = decode(pr.stash[i]);
        }
        inv.setContents(items);
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.BLOCK_BARREL_OPEN, 0.7f, 1f);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) {
            return;
        }
        Profile pr = profiles.loaded(h.owner);
        if (pr == null) {
            log.warning("Coffre de " + h.owner + " fermé sans profil chargé : contenu non enregistré.");
            return;
        }
        ItemStack[] items = e.getInventory().getContents();
        for (int i = 0; i < Profile.STASH_SIZE; i++) {
            pr.stash[i] = encode(i < items.length ? items[i] : null);
        }
        profiles.save(pr);
        if (e.getPlayer() instanceof Player p) {
            p.playSound(p.getLocation(), Sound.BLOCK_BARREL_CLOSE, 0.7f, 1f);
        }
    }

    private static String encode(ItemStack it) {
        if (it == null || it.getType().isAir() || it.getAmount() <= 0) {
            return "";
        }
        return Base64.getEncoder().encodeToString(it.serializeAsBytes());
    }

    private ItemStack decode(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(s));
        } catch (RuntimeException ex) {
            log.warning("Objet de planque illisible ignoré : " + ex.getMessage());
            return null;
        }
    }
}
