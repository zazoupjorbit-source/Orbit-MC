package fr.orbit.extraction.rank;

import fr.orbit.extraction.OrbitExtraction;
import fr.orbit.extraction.gun.GunItems;
import fr.orbit.extraction.raid.Profile;
import fr.orbit.extraction.rank.Ranks.Rank;
import fr.orbit.extraction.util.Text;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Kits des rangs : arme, munitions, armure, soins. Un rang ouvre son kit et ceux des rangs en dessous ; chaque kit
 * a son propre délai. Les kits se prennent à la planque (téléphone ou /kit) : l'armure s'équipe toute seule si
 * l'emplacement est libre.
 */
public final class Kits {

    public record Kit(String id, String name, Rank rank, Material icon, long cooldownMs, List<String> contents) {
    }

    private final OrbitExtraction plugin;
    private final List<Kit> kits = new ArrayList<>();

    public Kits(OrbitExtraction plugin) {
        this.plugin = plugin;
    }

    public void load(ConfigurationSection c) {
        kits.clear();
        if (c == null) {
            return;
        }
        for (String id : c.getKeys(false)) {
            ConfigurationSection s = c.getConfigurationSection(id);
            if (s == null) {
                continue;
            }
            Rank rank = plugin.ranks().byId(s.getString("rang", id));
            if (rank == null) {
                plugin.getLogger().warning("Kit « " + id + " » : rang inconnu « " + s.getString("rang", id) + " », kit ignoré.");
                continue;
            }
            Material icon = Material.matchMaterial(s.getString("icone", "CHEST"));
            List<String> contents = s.getStringList("contenu");
            for (String spec : contents) {
                String[] sp = spec.trim().split(":");
                // Créer une arme enregistre un chargeur : pour elles, on vérifie seulement que l'id existe.
                boolean gun = sp[0].equalsIgnoreCase("arme") || sp[0].equalsIgnoreCase("gun");
                if (gun ? sp.length < 2 || plugin.guns().gun(sp[1]) == null : plugin.specs().create(spec, true).isEmpty()) {
                    plugin.getLogger().warning("Kit « " + id + " » : objet inconnu « " + spec + " ».");
                }
            }
            kits.add(new Kit(id.toLowerCase(Locale.ROOT), s.getString("nom", id), rank, icon == null || !icon.isItem() ? Material.CHEST : icon,
                    (long) (Math.max(0, s.getDouble("recharge-minutes", 60)) * 60_000), contents));
        }
        kits.sort((a, b) -> Integer.compare(a.rank().level(), b.rank().level()));
    }

    public List<Kit> all() {
        return kits;
    }

    public Kit byId(String id) {
        for (Kit k : kits) {
            if (k.id().equalsIgnoreCase(id)) {
                return k;
            }
        }
        return null;
    }

    public boolean unlocked(Player p, Kit k) {
        return plugin.ranks().of(p).level() >= k.rank().level();
    }

    /** Millisecondes avant de pouvoir reprendre ce kit (0 = disponible). */
    public long remaining(Player p, Kit k) {
        Long next = plugin.profiles().get(p).kitNext.get(k.id());
        return next == null ? 0 : Math.max(0, next - System.currentTimeMillis());
    }

    /** « 1 h 05 », « 12 min », « 40 s ». */
    public static String duration(long ms) {
        long s = (ms + 999) / 1000;
        if (s >= 3600) {
            return (s / 3600) + " h " + String.format(Locale.ROOT, "%02d", (s % 3600) / 60);
        }
        return s >= 60 ? ((s + 59) / 60) + " min" : s + " s";
    }

    /** Vrai si le kit a été donné. */
    public boolean claim(Player p, Kit k) {
        if (!unlocked(p, k)) {
            deny(p, "Le kit <#D7DCE2>" + Text.esc(k.name()) + "</#D7DCE2> est réservé au rang " + k.rank().tag() + ".");
            return false;
        }
        if (plugin.raid().inRaid(p)) {
            deny(p, "<#C0392B>Les kits se prennent à la planque.</#C0392B> Extrais-toi d'abord.");
            return false;
        }
        long left = remaining(p, k);
        if (left > 0) {
            deny(p, "Kit <#D7DCE2>" + Text.esc(k.name()) + "</#D7DCE2> disponible dans <#D7DCE2>" + duration(left) + "</#D7DCE2>.");
            return false;
        }
        give(p, k);
        Profile pr = plugin.profiles().get(p);
        pr.kitNext.put(k.id(), System.currentTimeMillis() + k.cooldownMs());
        plugin.profiles().save(pr);
        p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 0.8f, 1f);
        Text.msg(p, "Kit <" + k.rank().color() + ">" + Text.esc(k.name()) + "</" + k.rank().color() + "> reçu. <#8A919B>Prochain dans "
                + duration(k.cooldownMs()) + ".");
        return true;
    }

    /** Donne le contenu sans toucher au délai (admin). */
    public void give(Player p, Kit k) {
        for (String spec : k.contents()) {
            for (ItemStack it : plugin.specs().create(spec, true)) {
                equipOrGive(p, it);
            }
        }
    }

    private static void equipOrGive(Player p, ItemStack it) {
        PlayerInventory inv = p.getInventory();
        EquipmentSlot slot = it.getType().getEquipmentSlot();
        if (it.getAmount() == 1 && (slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST || slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET)) {
            ItemStack worn = inv.getItem(slot);
            if (worn == null || worn.getType().isAir()) {
                inv.setItem(slot, it);
                return;
            }
        }
        GunItems.give(p, it);
    }

    private static void deny(Player p, String mini) {
        Text.msg(p, mini);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.7f);
    }
}
