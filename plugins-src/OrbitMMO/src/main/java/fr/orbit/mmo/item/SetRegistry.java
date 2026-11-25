package fr.orbit.mmo.item;

import fr.orbit.mmo.Keys;
import fr.orbit.mmo.OrbitMMO;
import org.bukkit.Color;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Sets d'équipement, lus dans plugins/OrbitMMO/sets.yml s'il existe, sinon dans celui du plugin
 * (« /mmoadmin sets exporter » écrit le fichier pour le modifier, « /mmoadmin reload » le relit).
 */
public final class SetRegistry {

    private final OrbitMMO plugin;
    private final Map<String, GearSet> sets = new LinkedHashMap<>();

    public SetRegistry(OrbitMMO plugin) {
        this.plugin = plugin;
    }

    public void load() {
        sets.clear();
        YamlConfiguration y = read();
        ConfigurationSection root = y == null ? null : y.getConfigurationSection("sets");
        if (root != null) {
            for (String id : root.getKeys(false)) {
                try {
                    sets.put(id, parse(id, root.getConfigurationSection(id)));
                } catch (RuntimeException e) {
                    plugin.getLogger().severe("Set " + id + " illisible : " + e.getMessage());
                }
            }
        }
        plugin.getLogger().info(sets.size() + " sets d'équipement chargés.");
    }

    private YamlConfiguration read() {
        File f = new File(plugin.getDataFolder(), "sets.yml");
        if (f.isFile()) {
            return YamlConfiguration.loadConfiguration(f);
        }
        InputStream in = plugin.getResource("sets.yml");
        return in == null ? null : YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    /** Écrit sets.yml dans plugins/OrbitMMO (sans écraser). */
    public boolean export() {
        File f = new File(plugin.getDataFolder(), "sets.yml");
        if (f.exists()) {
            return false;
        }
        plugin.saveResource("sets.yml", false);
        return true;
    }

    private GearSet parse(String id, ConfigurationSection y) {
        GearSet s = new GearSet(id);
        s.name = y.getString("nom", id);
        ArmorType t = ArmorType.byId(y.getString("type"));
        if (t != null) {
            s.type = t;
        }
        s.tier = Math.max(0, Math.min(Loot.TIERS - 1, y.getInt("palier", 0)));
        s.rarity = Rarity.of(y.getString("rarete", "RARE").replace("é", "e").replace("É", "E"));
        s.origin = y.getString("origine", "donjon").toLowerCase(Locale.ROOT);
        String bind = y.getString("liaison");
        s.bindOnPickup = bind == null ? "boss".equals(s.origin) || "raid".equals(s.origin) : bind.toLowerCase(Locale.ROOT).startsWith("ramass");
        s.look = y.getString("apparence");
        if (s.look != null) {
            // Convention partagée avec le paquet outfit : une apparence de set commence toujours par « set_ ».
            s.look = s.look.trim().toLowerCase(Locale.ROOT);
            s.look = s.look.isEmpty() ? null : s.look.startsWith("set_") ? s.look : "set_" + s.look;
        }
        s.itemLevelBonus = y.getInt("bonus-niveau-objet", 0);
        s.lore = y.getStringList("description");
        Loot.Base b = Loot.Base.byId(y.getString("modele-arme", "epee"));
        if (b != null) {
            s.weapon = b;
        }
        ConfigurationSection pieces = y.getConfigurationSection("pieces");
        if (pieces != null) {
            for (String k : pieces.getKeys(false)) {
                String key = k.toLowerCase(Locale.ROOT).replace("è", "e");
                if (GearSet.PIECES.contains(key)) {
                    s.pieces.put(key, pieces.getString(k));
                } else {
                    plugin.getLogger().warning("Set " + id + " : pièce inconnue « " + k + " » (casque, plastron, jambieres, bottes, arme, bouclier).");
                }
            }
        }
        if (s.pieces.containsKey("arme") && s.pieces.containsKey("bouclier")) {
            plugin.getLogger().warning("Set " + id + " : une arme ET un bouclier, le set compte quand même 5 pièces au plus.");
        }
        ConfigurationSection bonus = y.getConfigurationSection("bonus");
        if (bonus != null) {
            for (String k : bonus.getKeys(false)) {
                int n;
                try {
                    n = Integer.parseInt(k);
                } catch (NumberFormatException e) {
                    continue;
                }
                ConfigurationSection by = bonus.getConfigurationSection(k);
                if (by == null) {
                    continue;
                }
                Map<String, Double> values = new LinkedHashMap<>();
                for (String key : by.getKeys(true)) {
                    Object v = by.get(key);
                    if (v instanceof Number num) {
                        values.put(key, num.doubleValue());
                    }
                }
                s.bonuses.put(n, values);
            }
        }
        ConfigurationSection texts = y.getConfigurationSection("textes");
        if (texts != null) {
            for (String k : texts.getKeys(false)) {
                try {
                    s.texts.put(Integer.parseInt(k), texts.getString(k));
                } catch (NumberFormatException ignored) {
                    // clé non numérique
                }
            }
        }
        for (Map<?, ?> m : y.getMapList("butin")) {
            double chance = m.get("chance") instanceof Number n ? n.doubleValue() : 0;
            Object boss = m.get("boss");
            if (boss != null) {
                List<String> ids = new ArrayList<>();
                if (boss instanceof List<?> l) {
                    for (Object o : l) {
                        ids.add(String.valueOf(o));
                    }
                } else {
                    ids.add(String.valueOf(boss));
                }
                s.drops.add(new GearSet.Drop(ids, 0, 0, chance));
            } else if (m.get("etages") != null) {
                String r = String.valueOf(m.get("etages"));
                int min;
                int max;
                int dash = r.indexOf('-');
                if (dash > 0) {
                    min = Integer.parseInt(r.substring(0, dash).trim());
                    max = Integer.parseInt(r.substring(dash + 1).trim());
                } else {
                    min = max = Integer.parseInt(r.trim());
                }
                s.drops.add(new GearSet.Drop(null, min, max, chance));
            }
        }
        return s;
    }

    // ------------------------------------------------------------------ Accès

    public GearSet get(String id) {
        return id == null ? null : sets.get(id);
    }

    public Collection<GearSet> all() {
        return Collections.unmodifiableCollection(sets.values());
    }

    /** Set d'un objet (null : aucun). */
    public GearSet of(ItemStack it) {
        if (it == null || !it.hasItemMeta()) {
            return null;
        }
        return get(it.getItemMeta().getPersistentDataContainer().get(Keys.SET, PersistentDataType.STRING));
    }

    // ------------------------------------------------------------------ Création

    /** Crée une pièce du set (clé : casque, plastron, jambieres, bottes, arme, bouclier). */
    public ItemStack create(GearSet s, String piece, Color dye, Random rnd) {
        int t = s.tier;
        int statLevel = Loot.TIER_LEVEL[t] + 6;
        int req = Loot.TIER_LEVEL[t];
        ItemStack it;
        switch (piece) {
            case "arme" -> it = Loot.weapon(s.weapon, t, s.rarity, statLevel, req, rnd);
            case "bouclier" -> it = Loot.shield(t, s.rarity, statLevel, req, rnd);
            default -> {
                Loot.Slot slot = switch (piece) {
                    case "casque" -> Loot.Slot.HEAD;
                    case "jambieres" -> Loot.Slot.LEGS;
                    case "bottes" -> Loot.Slot.FEET;
                    default -> Loot.Slot.CHEST;
                };
                it = Loot.armor(s.type, slot, t, s.rarity, statLevel, req, dye, rnd);
            }
        }
        ItemMeta meta = it.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(Keys.SET, PersistentDataType.STRING, s.id);
        String title = s.pieces.get(piece);
        if (title != null) {
            pdc.set(Keys.TITLE, PersistentDataType.STRING, title);
        }
        pdc.set(Keys.BIND, PersistentDataType.STRING, s.bindOnPickup ? Binding.PICKUP : Binding.EQUIP);
        if (s.itemLevelBonus != 0) {
            pdc.set(Keys.ILVL_BONUS, PersistentDataType.INTEGER, s.itemLevelBonus);
        }
        if (s.look != null && pdc.has(Keys.OUTFIT)) {
            pdc.set(Keys.OUTFIT, PersistentDataType.STRING, s.look);
            pdc.set(Keys.OUTFIT_BASE, PersistentDataType.STRING, s.look);
        }
        pdc.set(Keys.FLAVOR, PersistentDataType.STRING, "<#FFD66E>Set · " + fr.orbit.mmo.util.Text.esc(s.name)
                + " <#5A6270>(" + s.originLabel() + ")");
        it.setItemMeta(meta);
        Loot.refresh(it);
        return it;
    }

    /** Pièce au hasard parmi celles du set. */
    public ItemStack createRandom(GearSet s, Color dye, Random rnd) {
        List<String> keys = new ArrayList<>(s.pieces.keySet());
        if (keys.isEmpty()) {
            return null;
        }
        return create(s, keys.get(rnd.nextInt(keys.size())), dye, rnd);
    }

    /** Sets qui peuvent tomber de ce boss (tirage fait à part pour chacun). */
    public List<GearSet> forBoss(String bossId) {
        List<GearSet> out = new ArrayList<>();
        for (GearSet s : sets.values()) {
            for (GearSet.Drop d : s.drops) {
                if (d.matchesBoss(bossId)) {
                    out.add(s);
                    break;
                }
            }
        }
        return out;
    }

    /** Tirage d'une pièce de set pour un monstre de l'étage (null : rien). */
    public ItemStack rollFloor(int floor, Color dye, Random rnd) {
        if (floor <= 0) {
            return null;
        }
        for (GearSet s : sets.values()) {
            for (GearSet.Drop d : s.drops) {
                if (d.matchesFloor(floor) && rnd.nextDouble() < d.chance()) {
                    return createRandom(s, dye, rnd);
                }
            }
        }
        return null;
    }

    /** Tirage d'une pièce de set pour un boss (null : rien). */
    public ItemStack rollBoss(String bossId, Color dye, Random rnd) {
        for (GearSet s : sets.values()) {
            for (GearSet.Drop d : s.drops) {
                if (d.matchesBoss(bossId) && rnd.nextDouble() < d.chance()) {
                    return createRandom(s, dye, rnd);
                }
            }
        }
        return null;
    }
}
