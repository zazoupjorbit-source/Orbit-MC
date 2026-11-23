package fr.naruto.core.gui;

import fr.naruto.core.jutsu.Jutsu;
import fr.naruto.core.jutsu.JutsuService;
import fr.naruto.core.ninja.Clan;
import fr.naruto.core.ninja.Element;
import fr.naruto.core.ninja.Ninja;
import fr.naruto.core.text.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Construit les inventaires : selection d'element, de clan, et menu des jutsu. */
public final class Menus {

    private Menus() {}

    public static ItemStack item(Material mat, String nameMini, List<String> loreMini) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Text.MM.deserialize(nameMini).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        for (String l : loreMini) lore.add(Text.MM.deserialize(l).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        it.setItemMeta(meta);
        return it;
    }

    public static void openElement(Player player) {
        NarutoHolder holder = new NarutoHolder(NarutoHolder.Type.ELEMENT);
        Inventory inv = Bukkit.createInventory(holder, 27, Text.MM.deserialize("<dark_gray>Choisis ton <#E8650A>affinite"));
        holder.setInventory(inv);
        int[] slots = {10, 11, 12, 13, 14};
        Element[] els = {Element.KATON, Element.SUITON, Element.FUTON, Element.DOTON, Element.RAITON};
        for (int i = 0; i < els.length; i++) {
            Element e = els[i];
            inv.setItem(slots[i], item(e.icon(), "<" + e.color() + "><bold>" + e.displayName(),
                    List.of("<gray>Nature : <white>" + e.nature(), "<dark_gray>", "<yellow>Clic pour choisir")));
            holder.actions().put(slots[i], e.name());
        }
        player.openInventory(inv);
    }

    public static void openClan(Player player) {
        NarutoHolder holder = new NarutoHolder(NarutoHolder.Type.CLAN);
        Inventory inv = Bukkit.createInventory(holder, 27, Text.MM.deserialize("<dark_gray>Choisis ton <#E8650A>clan"));
        holder.setInventory(inv);
        Clan[] clans = {Clan.UCHIHA, Clan.HYUGA, Clan.UZUMAKI, Clan.NARA, Clan.SENJU, Clan.ABURAME};
        int[] slots = {10, 11, 12, 14, 15, 16};
        for (int i = 0; i < clans.length; i++) {
            Clan c = clans[i];
            inv.setItem(slots[i], item(c.icon(), "<" + c.color() + "><bold>" + c.displayName(),
                    List.of("<gray>Kekkei Genkai : <white>" + c.kekkeiGenkai(),
                            "<gray>" + c.description(), "<dark_gray>", "<yellow>Clic pour choisir")));
            holder.actions().put(slots[i], c.name());
        }
        player.openInventory(inv);
    }

    public static void openJutsu(Player player, Ninja ninja, JutsuService jutsus) {
        List<Jutsu> avail = jutsus.availableFor(ninja);
        int rows = Math.max(1, (int) Math.ceil(avail.size() / 9.0));
        int size = Math.min(54, rows * 9);
        NarutoHolder holder = new NarutoHolder(NarutoHolder.Type.JUTSU);
        Inventory inv = Bukkit.createInventory(holder, size, Text.MM.deserialize("<#E8650A><bold>Jutsu <dark_gray>(" + avail.size() + ")"));
        holder.setInventory(inv);
        int slot = 0;
        for (Jutsu j : avail) {
            if (slot >= size) break;
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Element : <" + j.element().color() + ">" + j.element().displayName());
            lore.add("<gray>Chakra : <aqua>" + j.chakraCost() + " <dark_gray>| Recharge : " + (j.cooldownMs() / 1000) + "s");
            for (String l : j.lore()) lore.add("<dark_gray>" + l);
            lore.add("<dark_gray>");
            lore.add("<green>Clic pour recevoir le parchemin");
            inv.setItem(slot, item(j.icon(), "<#FFB347>" + j.displayName(), lore));
            holder.actions().put(slot, j.id());
            slot++;
        }
        player.openInventory(inv);
    }
}
