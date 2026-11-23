package fr.orbit.practice;

import fr.orbit.practice.match.MatchManager;
import fr.orbit.practice.match.QueueManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/** Menu « Combattre » : une ligne 1v1, une ligne 2v2, un bouton par type de combat. */
public final class QueueMenu {

    public static final class Holder implements InventoryHolder {
        private Inventory inventory;
        private boolean ranked;

        public boolean ranked() {
            return ranked;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final NamespacedKey key;
    private final QueueManager queue;
    private final MatchManager matches;
    private final EloStore elo;

    public QueueMenu(Plugin plugin, QueueManager queue, MatchManager matches, EloStore elo) {
        this.key = new NamespacedKey(plugin, "file");
        this.queue = queue;
        this.matches = matches;
        this.elo = elo;
    }

    /** Cases des kits : deux rangées centrées. */
    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 20, 21, 22, 23, 24, 29, 30, 31, 32, 33};

    /** Ouvre le menu des combats classés (Elo) ou casual (sans Elo). */
    public void open(Player p, boolean ranked) {
        Holder holder = new Holder();
        holder.ranked = ranked;
        Inventory inv = Bukkit.createInventory(holder, 45, Msg.raw(ranked
                ? "<dark_gray>Combats classés <gray>(Elo)" : "<dark_gray>Combats casual <gray>(sans Elo)"));
        holder.inventory = inv;
        Ladder[] ladders = Ladder.values();
        for (int i = 0; i < ladders.length && i < SLOTS.length; i++) {
            inv.setItem(SLOTS[i], button(p, ladders[i], ranked));
        }
        p.openInventory(inv);
    }

    private ItemStack button(Player p, Ladder ladder, boolean ranked) {
        int waiting = queue.waiting(ladder, Mode.SOLO, ranked) + queue.waiting(ladder, Mode.DUO, ranked);
        ItemStack item = new ItemStack(ladder.icon, Math.max(1, Math.min(64, waiting)));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Msg.item(Msg.OR + "<bold>" + ladder.label));
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(Msg.item("<gray>" + ladder.description));
        lore.add(Msg.item(""));
        if (ranked) {
            lore.add(Msg.item("<gray>Ton Elo : <white>" + elo.elo(p.getUniqueId(), ladder)));
        }
        lore.add(Msg.item("<gray>En file : <white>" + queue.waiting(ladder, Mode.SOLO, ranked) + " <gray>en 1v1, <white>"
                + queue.waiting(ladder, Mode.DUO, ranked) + " <gray>en 2v2"));
        lore.add(Msg.item("<gray>En combat : <white>" + (matches.fighting(ladder, Mode.SOLO) + matches.fighting(ladder, Mode.DUO))));
        lore.add(Msg.item(""));
        lore.add(Msg.item(Msg.BLEU + "Clic gauche : 1v1"));
        lore.add(Msg.item(queue.mateOf(p.getUniqueId()) == null
                ? "<dark_gray>Clic droit : 2v2 (il faut une équipe : /party inviter <pseudo>)"
                : Msg.BLEU + "Clic droit : 2v2 <green>(équipe prête)"));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.values());
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, ladder.id);
        item.setItemMeta(meta);
        return item;
    }

    /** Clic dans le menu : rejoint la file du bouton. */
    public void click(Player p, ItemStack item, boolean ranked, boolean rightClick) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return;
        }
        String value = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (value == null) {
            return;
        }
        Ladder ladder = Ladder.byId(value);
        p.closeInventory();
        if (ladder != null) {
            queue.join(p, ladder, rightClick ? Mode.DUO : Mode.SOLO, ranked);
        }
    }
}
