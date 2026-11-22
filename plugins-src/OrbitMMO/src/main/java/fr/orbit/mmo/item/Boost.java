package fr.orbit.mmo.item;

import fr.orbit.mmo.Keys;
import fr.orbit.mmo.util.Text;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Effets temporaires achetés en ville (alchimiste, prêtre). Un effet actif s'ajoute aux bonus du personnage
 * (clé de Mods) jusqu'à sa fin ; reprendre le même effet remet seulement sa durée à zéro.
 */
public enum Boost {

    VITESSE("vitesse", "Potion de vitesse", "#7FFFD4", Material.POTION, 0x5FE3C8, "vitesse", 20, 180, 60,
            Seller.ALCHEMIST, "+20 % de vitesse de déplacement"),
    VITALITE("vitalite", "Élixir de vitalité", "#FF5C8A", Material.POTION, 0xE0245E, "pv", 15, 600, 120,
            Seller.ALCHEMIST, "+15 % de points de vie max"),
    PROTECTION("protection", "Parchemin de protection", "#E6C35C", Material.PAPER, 0, "armure", 10, 600, 120,
            Seller.PRIEST, "−10 % de dégâts subis"),
    PUISSANCE("puissance", "Essence de puissance", "#FF8A3B", Material.BLAZE_POWDER, 0, "degats", 10, 600, 150,
            Seller.PRIEST, "+10 % de dégâts (coups et sorts)"),
    PRECISION("precision", "Essence de précision", "#FFD66E", Material.GLOWSTONE_DUST, 0, "crit", 5, 600, 120,
            Seller.PRIEST, "+5 % de chances de coup critique"),
    FEROCITE("ferocite", "Élixir de férocité", "#FF8A3B", Material.POTION, 0xFF6A1A, "critdeg", 15, 600, 180,
            Seller.ALCHEMIST, "+15 % de dégâts critiques"),
    SANG("sang", "Élixir de sang", "#E0245E", Material.POTION, 0x8B0A1A, "vol-de-vie", 2, 600, 220,
            Seller.ALCHEMIST, "+2 % de vol de vie (coups et sorts)"),
    GRACE("grace", "Parchemin de grâce", "#FFF3B0", Material.PAPER, 0, "soins", 10, 600, 120,
            Seller.PRIEST, "+10 % de soins prodigués"),
    EPINES("epines", "Parchemin d'épines", "#7CFF8A", Material.PAPER, 0, "epines", 20, 600, 120,
            Seller.PRIEST, "renvoie 20 % des dégâts de mêlée subis"),
    /** Donnée gratuitement par le prêtre (pas d'objet). */
    BENEDICTION("benediction", "Bénédiction", "#FFF3B0", Material.NETHER_STAR, 0, "pv", 5, 900, 0,
            Seller.PRIEST, "+5 % de points de vie max");

    public enum Seller { ALCHEMIST, PRIEST }

    public final String id;
    public final String name;
    public final String hex;
    public final Material material;
    public final int potionColor;
    /** Clé de bonus (Mods) : vitesse, pv, armure, degats, crit. */
    public final String mod;
    public final double value;
    public final int seconds;
    public final long price;
    public final Seller seller;
    public final String effect;

    Boost(String id, String name, String hex, Material material, int potionColor, String mod, double value, int seconds,
          long price, Seller seller, String effect) {
        this.id = id;
        this.name = name;
        this.hex = hex;
        this.material = material;
        this.potionColor = potionColor;
        this.mod = mod;
        this.value = value;
        this.seconds = seconds;
        this.price = price;
        this.seller = seller;
        this.effect = effect;
    }

    public String itemType() {
        return "boost_" + id;
    }

    public String tag() {
        return "<" + hex + ">" + name + "</" + hex + ">";
    }

    public String duration() {
        return seconds % 60 == 0 ? seconds / 60 + " min" : seconds + " s";
    }

    public static Boost byId(String id) {
        for (Boost b : values()) {
            if (b.id.equals(id)) {
                return b;
            }
        }
        return null;
    }

    public static Boost byItemType(String type) {
        if (type == null || !type.startsWith("boost_")) {
            return null;
        }
        return byId(type.substring("boost_".length()));
    }

    /** Objet à consommer au clic droit. */
    public ItemStack item(int amount) {
        ItemStack it = new ItemStack(material);
        ItemMeta meta = it.getItemMeta();
        if (meta instanceof PotionMeta pm && potionColor != 0) {
            pm.setColor(Color.fromRGB(potionColor));
        }
        meta.displayName(Text.item("<" + hex + ">" + name));
        String use = material == Material.POTION ? "boire" : material == Material.PAPER ? "lire" : "absorber";
        meta.lore(Text.lore(List.of("<#D7DCE2>" + effect, "<#8C96A3>pendant " + duration() + ".",
                "<#5A6270>Reprendre le même effet remet sa durée à zéro.", "<#5A6270>[Clic droit] " + use)));
        meta.setMaxStackSize(16);
        meta.setEnchantmentGlintOverride(material != Material.POTION);
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        meta.getPersistentDataContainer().set(Keys.ITEM, PersistentDataType.STRING, itemType());
        it.setItemMeta(meta);
        it.setAmount(Math.max(1, Math.min(16, amount)));
        return it;
    }
}
