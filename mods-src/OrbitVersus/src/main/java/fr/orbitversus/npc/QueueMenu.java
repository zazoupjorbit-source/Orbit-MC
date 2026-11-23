package fr.orbitversus.npc;

import fr.orbitversus.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/** Menu ouvert par le PNJ Versus : choisir sa file. */
public final class QueueMenu {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    public void open(Player player) {
        QueueHolder holder = new QueueHolder();
        Inventory inv = Bukkit.createInventory(holder, 27,
                MM.deserialize(Msg.GRAD + "<bold>⚔ Mode Versus</bold></gradient>"));
        holder.setInventory(inv);

        ItemStack pane = pane();
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, pane);
        }
        inv.setItem(QueueHolder.SLOT_1V1, icon(Material.DIAMOND_SWORD,
                "<gradient:#E8650A:#FF3B3B><bold>1 vs 1</bold></gradient>",
                List.of("<gray>Cherche un adversaire en solo.", "<dark_gray>▸ Clic pour rejoindre la file")));
        inv.setItem(QueueHolder.SLOT_2V2, icon(Material.IRON_SWORD,
                "<gradient:#E8650A:#FF3B3B><bold>2 vs 2</bold></gradient>",
                List.of("<gray>Combat en équipe de 2.", "<dark_gray>▸ /2v2 <nom d'équipe>")));
        inv.setItem(QueueHolder.SLOT_LEAVE, icon(Material.BARRIER,
                "<red><bold>Quitter la file</bold></red>", List.of("<gray>Annule ta recherche.")));

        player.openInventory(inv);
    }

    private ItemStack pane() {
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.empty());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack icon(Material material, String nameMini, List<String> loreMini) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize(nameMini).decoration(TextDecoration.ITALIC, false));
        meta.lore(loreMini.stream().map(s -> MM.deserialize(s).decoration(TextDecoration.ITALIC, false)).toList());
        item.setItemMeta(meta);
        return item;
    }
}
