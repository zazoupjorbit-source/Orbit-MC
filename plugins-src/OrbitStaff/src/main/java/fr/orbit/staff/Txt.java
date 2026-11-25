package fr.orbit.staff;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Textes (MiniMessage, charte Orbit MC bleu + or) et fabrication d'icônes. */
public final class Txt {

    public static final MiniMessage MM = MiniMessage.miniMessage();
    public static final String BLEU = "<#4aa3ff>";
    public static final String OR = "<#f5c542>";
    public static final String PREFIXE = "<#4aa3ff><bold>ORBIT</bold> <#f5c542><bold>STAFF</bold> <dark_gray>» <gray>";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yy HH:mm").withZone(ZoneId.of("Europe/Paris"));

    private Txt() {
    }

    public static Component mm(String s) {
        return MM.deserialize(s);
    }

    /** Texte d'objet : pas d'italique par défaut. */
    public static Component item(String s) {
        return MM.deserialize(s).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static void send(CommandSender to, String s) {
        to.sendMessage(mm(PREFIXE + s));
    }

    public static void err(CommandSender to, String s) {
        to.sendMessage(mm(PREFIXE + "<#ff5555>" + s));
    }

    /** Échappe un texte libre (pseudo, motif) pour MiniMessage. */
    public static String esc(String s) {
        return s == null ? "" : MM.escapeTags(s);
    }

    public static ItemStack icon(Material m, String name, List<String> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(item(name));
            List<Component> l = new ArrayList<>();
            for (String line : lore) {
                l.add(item("<gray>" + line));
            }
            meta.lore(l);
            meta.addItemFlags(ItemFlag.values());
            it.setItemMeta(meta);
        }
        return it;
    }

    public static ItemStack icon(Material m, String name, String... lore) {
        return icon(m, name, List.of(lore));
    }

    public static ItemStack head(OfflinePlayer p, String name, List<String> lore) {
        ItemStack it = icon(Material.PLAYER_HEAD, name, lore);
        if (it.getItemMeta() instanceof SkullMeta sm) {
            sm.setOwningPlayer(p);
            it.setItemMeta(sm);
        }
        return it;
    }

    public static String date(Instant i) {
        return i == null ? "—" : DATE.format(i);
    }

    /** 1j 4h, 12min, 40s... */
    public static String duree(Duration d) {
        long s = Math.max(0, d.getSeconds());
        long j = s / 86400, h = (s % 86400) / 3600, m = (s % 3600) / 60;
        if (j > 0) {
            return j + "j " + h + "h";
        }
        if (h > 0) {
            return h + "h " + m + "min";
        }
        if (m > 0) {
            return m + "min";
        }
        return s + "s";
    }

    /** Parse 30m, 2h, 3j/3d, 1s/1w (semaine), 1mo, 1a/1y. Null si perm ; exception si invalide. */
    public static Duration parseDuree(String s) {
        String v = s.toLowerCase().trim();
        if (v.equals("perm") || v.equals("permanent") || v.equals("def")) {
            return null;
        }
        int i = 0;
        while (i < v.length() && Character.isDigit(v.charAt(i))) {
            i++;
        }
        if (i == 0 || i == v.length()) {
            throw new IllegalArgumentException(s);
        }
        long n = Long.parseLong(v.substring(0, i));
        return switch (v.substring(i)) {
            case "s", "sec" -> Duration.ofSeconds(n);
            case "m", "min" -> Duration.ofMinutes(n);
            case "h" -> Duration.ofHours(n);
            case "j", "d" -> Duration.ofDays(n);
            case "w", "sem" -> Duration.ofDays(7 * n);
            case "mo", "mois" -> Duration.ofDays(30 * n);
            case "a", "y", "an" -> Duration.ofDays(365 * n);
            default -> throw new IllegalArgumentException(s);
        };
    }

    public static void alerterStaff(String perm, String s) {
        Component c = mm(PREFIXE + s);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission(perm)) {
                p.sendMessage(c);
            }
        }
        Bukkit.getConsoleSender().sendMessage(c);
    }
}
