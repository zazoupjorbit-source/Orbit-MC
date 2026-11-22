package fr.orbit.mmo.item;

import fr.orbit.mmo.Keys;
import fr.orbit.mmo.classes.TalentDef;
import fr.orbit.mmo.util.Text;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Runes serties dans les châsses d'une arme (chez Ysolde, la runiste). Trois rangs ; trois runes identiques
 * se fusionnent en une rune du rang supérieur. Runes de statistique : un bonus permanent tant que l'arme est
 * tenue. Runes élémentaires : une chance, à chaque coup porté, de déclencher un effet (voir Combat.runeProcs).
 */
public enum Rune {
    PUISSANCE("puissance", "Rune de puissance", Material.BLADE_POTTERY_SHERD, "#FF5C5C", Stat.PUISSANCE, 0, 3, 6, 10),
    PRECISION("precision", "Rune de précision", Material.ARCHER_POTTERY_SHERD, "#FFD66E", Stat.PRECISION, 0, 2, 4, 6),
    FEROCITE("ferocite", "Rune de férocité", Material.DANGER_POTTERY_SHERD, "#FF8A3B", Stat.FEROCITE, 0, 8, 16, 25),
    VITALITE("vitalite", "Rune de vitalité", Material.HEART_POTTERY_SHERD, "#7CFF8A", Stat.VITALITE, 0, 4, 8, 12),
    GARDE("garde", "Rune de garde", Material.SHELTER_POTTERY_SHERD, "#9AD8FF", Stat.ROBUSTESSE, 0, 2, 4, 6),
    VAMPIRE("vampire", "Rune du vampire", Material.HEARTBREAK_POTTERY_SHERD, "#E0245E", Stat.VAMPIRISME, 0, 1.5, 3, 5),
    VENT("vent", "Rune du vent", Material.FLOW_POTTERY_SHERD, "#D7F5FF", Stat.CELERITE, 0, 3, 6, 9),
    SAGESSE("sagesse", "Rune de sagesse", Material.PRIZE_POTTERY_SHERD, "#4FA3FF", Stat.FOCALISATION, 0, 5, 10, 15),
    FEU("feu", "Rune de feu", Material.BURN_POTTERY_SHERD, "#FF7A1A", null, 10, 40, 60, 85),
    GIVRE("givre", "Rune de givre", Material.HOWL_POTTERY_SHERD, "#8FE3FF", null, 14, 20, 30, 45),
    FOUDRE("foudre", "Rune de foudre", Material.GUSTER_POTTERY_SHERD, "#F2F27A", null, 9, 45, 65, 90),
    NEANT("neant", "Rune du néant", Material.SKULL_POTTERY_SHERD, "#9B59FF", null, 6, 80, 120, 170);

    public static final int MAX_RANK = 3;
    private static final String[] RANK = {"I", "II", "III"};
    private static final String[] RANK_HEX = {"#5CE65C", "#4FA3FF", "#B36BFF"};
    /** Prix d'une rune de rang I chez la runiste (statistique / élémentaire). */
    public static final long STAT_PRICE = 180;
    public static final long ELEMENT_PRICE = 900;
    public static final int ELEMENT_LEVEL = 15;
    /** Fusion de 3 runes : prix pour obtenir le rang 2, le rang 3. */
    public static final long[] FUSE_PRICE = {0, 0, 250, 1200};

    public final String id;
    public final String name;
    public final Material material;
    public final String hex;
    /** Bonus permanent (null : rune élémentaire). */
    public final Stat stat;
    /** Chance de déclenchement par coup (%), runes élémentaires. */
    public final double chance;
    private final double[] values;

    Rune(String id, String name, Material material, String hex, Stat stat, double chance, double... values) {
        this.id = id;
        this.name = name;
        this.material = material;
        this.hex = hex;
        this.stat = stat;
        this.chance = chance;
        this.values = values;
    }

    /** Une rune sertie ou dans le sac : rune + rang (1 à 3). */
    public record Ref(Rune rune, int rank) {
        public String key() {
            return rune.id + ":" + rank;
        }
    }

    public boolean elemental() {
        return stat == null;
    }

    public double value(int rank) {
        return values[Math.max(1, Math.min(MAX_RANK, rank)) - 1];
    }

    public static String rank(int rank) {
        return RANK[Math.max(1, Math.min(MAX_RANK, rank)) - 1];
    }

    public static String rankHex(int rank) {
        return RANK_HEX[Math.max(1, Math.min(MAX_RANK, rank)) - 1];
    }

    public String tag(int rank) {
        return "<" + hex + ">" + name + " <" + rankHex(rank) + ">" + rank(rank);
    }

    /** Effet en une ligne (lore de la rune et de l'arme). */
    public String effect(int rank) {
        String v = TalentDef.format(value(rank));
        if (stat != null) {
            return stat.line(value(rank));
        }
        String c = TalentDef.format(chance);
        return switch (this) {
            case FEU -> c + " % par coup : embrase la cible (" + v + " % du coup en 4 s)";
            case GIVRE -> c + " % par coup : +" + v + " % de dégâts de givre, cible ralentie";
            case FOUDRE -> c + " % par coup : éclair (" + v + " % du coup) sur la cible et 2 ennemis proches";
            default -> c + " % par coup : frappe du néant, +" + v + " % de dégâts";
        };
    }

    public long buyPrice() {
        return elemental() ? ELEMENT_PRICE : STAT_PRICE;
    }

    /** Rachat par le forgeron. */
    public long sellPrice(int rank) {
        double mult = rank >= 3 ? 12 : rank == 2 ? 3.5 : 1;
        return Math.round(buyPrice() / 4.0 * mult);
    }

    public ItemStack item(int rank) {
        int r = Math.max(1, Math.min(MAX_RANK, rank));
        ItemStack it = new ItemStack(material);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Text.item("<b>" + tag(r)));
        List<String> lore = new ArrayList<>();
        lore.add("<#8C96A3>Rune " + (elemental() ? "élémentaire" : "de statistique") + " · rang " + rank(r));
        lore.add("<#7CFF8A>" + effect(r));
        lore.add("");
        lore.add("<#5A6270>Se sertit dans une arme chez Ysolde, la runiste.");
        if (r < MAX_RANK) {
            lore.add("<#5A6270>3 runes identiques = 1 rune du rang " + rank(r + 1) + ".");
        }
        meta.lore(Text.lore(lore));
        meta.setEnchantmentGlintOverride(r >= 2);
        meta.addItemFlags(ItemFlag.values());
        meta.getPersistentDataContainer().set(Keys.ITEM, PersistentDataType.STRING, "rune");
        meta.getPersistentDataContainer().set(Keys.RUNE, PersistentDataType.STRING, id + ":" + r);
        it.setItemMeta(meta);
        return it;
    }

    public static Rune byId(String id) {
        for (Rune r : values()) {
            if (r.id.equals(id)) {
                return r;
            }
        }
        return null;
    }

    /** « feu:2 » → rune + rang (null si invalide). */
    public static Ref parse(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        int i = s.indexOf(':');
        Rune r = byId(i < 0 ? s : s.substring(0, i));
        if (r == null) {
            return null;
        }
        int rank = 1;
        if (i >= 0) {
            try {
                rank = Integer.parseInt(s.substring(i + 1));
            } catch (NumberFormatException ignored) {
                // rang 1
            }
        }
        return new Ref(r, Math.max(1, Math.min(MAX_RANK, rank)));
    }

    /** Rune d'un objet du sac (null si ce n'est pas une rune). */
    public static Ref of(ItemStack it) {
        if (it == null || !it.hasItemMeta()) {
            return null;
        }
        return parse(it.getItemMeta().getPersistentDataContainer().get(Keys.RUNE, PersistentDataType.STRING));
    }
}
