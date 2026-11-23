package fr.orbit.extraction.loot;

import fr.orbit.extraction.OrbitExtraction;
import fr.orbit.extraction.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Coffres et tonneaux de la carte : pendant un raid, chacun est un conteneur de butin à fouiller (voir Search). Son
 * contenu est tiré à la première ouverture dans une table choisie selon le bloc (un coffre peut être un coffre
 * ordinaire, militaire ou médical), puis il se regarnit au bout de quelques minutes. Le contenu réel du bloc n'est
 * jamais touché : ce n'est qu'un inventaire virtuel par position.
 */
public final class Containers implements Listener {

    public record Table(String id, String name, String color, int emptyPct, int minRolls, int maxRolls, List<Crates.Entry> loot) {
    }

    private record Choice(Table table, int weight) {
    }

    private static final class Box implements InventoryHolder {
        Inventory inv;
        long refill;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private final OrbitExtraction plugin;
    private final Map<String, Table> tables = new HashMap<>();
    private final Map<Material, List<Choice>> byBlock = new EnumMap<>(Material.class);
    private final Map<Long, Box> boxes = new HashMap<>();
    private final Random random = new Random();
    private boolean enabled;
    private long refillMs;
    private double noise;
    private int opened;

    public Containers(OrbitExtraction plugin) {
        this.plugin = plugin;
    }

    public void load(ConfigurationSection c) {
        clear();
        tables.clear();
        byBlock.clear();
        enabled = c != null && c.getBoolean("conteneurs-actifs", true);
        refillMs = (long) ((c == null ? 15 : Math.max(1, c.getDouble("reapparition-minutes", 15))) * 60_000);
        noise = c == null ? 12 : c.getDouble("bruit", 12);
        ConfigurationSection ts = c == null ? null : c.getConfigurationSection("tables");
        if (ts != null) {
            for (String id : ts.getKeys(false)) {
                ConfigurationSection s = ts.getConfigurationSection(id);
                if (s == null) {
                    continue;
                }
                List<Crates.Entry> loot = new ArrayList<>();
                for (Map<?, ?> m : s.getMapList("butin")) {
                    Object spec = m.get("objet");
                    if (spec != null) {
                        loot.add(new Crates.Entry(spec.toString(), m.get("poids") instanceof Number n ? Math.max(1, n.intValue()) : 10));
                    }
                }
                if (loot.isEmpty()) {
                    continue;
                }
                String[] rolls = s.getString("tirages", "1-3").split("-");
                int a = parse(rolls[0], 1), b = rolls.length > 1 ? parse(rolls[1], a) : a;
                tables.put(id.toLowerCase(Locale.ROOT), new Table(id.toLowerCase(Locale.ROOT), s.getString("nom", id),
                        s.getString("couleur", "#C9A86A"), Math.max(0, Math.min(100, s.getInt("vide", 30))), Math.max(1, a), Math.max(a, b), loot));
            }
        }
        ConfigurationSection bs = c == null ? null : c.getConfigurationSection("blocs");
        if (bs != null) {
            for (String key : bs.getKeys(false)) {
                Material m = Material.matchMaterial(key);
                ConfigurationSection w = bs.getConfigurationSection(key);
                if (m == null || !m.isBlock() || w == null) {
                    continue;
                }
                List<Choice> list = new ArrayList<>();
                for (String t : w.getKeys(false)) {
                    Table table = tables.get(t.toLowerCase(Locale.ROOT));
                    int weight = w.getInt(t, 0);
                    if (table != null && weight > 0) {
                        list.add(new Choice(table, weight));
                    }
                }
                if (!list.isEmpty()) {
                    byBlock.put(m, list);
                }
            }
        }
        plugin.getLogger().info("Fouille de la carte : " + (enabled && !byBlock.isEmpty()
                ? byBlock.size() + " types de blocs, " + tables.size() + " tables de butin." : "désactivée."));
    }

    private static int parse(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::prune, 1200L, 1200L);
    }

    /** Conteneurs ouverts depuis le démarrage (pour /extraction caisses). */
    public String summary() {
        return boxes.size() + " conteneur" + (boxes.size() > 1 ? "s" : "") + " fouillé" + (boxes.size() > 1 ? "s" : "")
                + " en mémoire, " + opened + " ouverture" + (opened > 1 ? "s" : "") + " depuis le démarrage";
    }

    public void clear() {
        for (Box b : boxes.values()) {
            for (HumanEntity v : new ArrayList<>(b.inv.getViewers())) {
                v.closeInventory();
            }
            plugin.search().forget(b.inv);
        }
        boxes.clear();
    }

    public void shutdown() {
        clear();
    }

    /** Oublie les conteneurs à regarnir que personne ne regarde : ils seront retirés à la prochaine ouverture. */
    private void prune() {
        long now = System.currentTimeMillis();
        Iterator<Box> it = boxes.values().iterator();
        while (it.hasNext()) {
            Box b = it.next();
            if (now >= b.refill && b.inv.getViewers().isEmpty()) {
                plugin.search().forget(b.inv);
                it.remove();
            }
        }
    }

    private static long key(Block b) {
        return ((long) (b.getX() & 0x3FFFFFF) << 38) | ((long) (b.getZ() & 0x3FFFFFF) << 12) | (b.getY() & 0xFFF);
    }

    private Table pick(Material m) {
        List<Choice> list = byBlock.get(m);
        if (list == null) {
            return null;
        }
        int total = 0;
        for (Choice c : list) {
            total += c.weight();
        }
        int r = random.nextInt(total);
        for (Choice c : list) {
            r -= c.weight();
            if (r < 0) {
                return c.table();
            }
        }
        return list.get(0).table();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (!enabled || e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) {
            return;
        }
        Block block = e.getClickedBlock();
        if (!byBlock.containsKey(block.getType()) || !block.getWorld().getName().equals(plugin.raid().worldName())) {
            return;
        }
        Player p = e.getPlayer();
        if (!plugin.raid().inRaid(p)) {
            return;     // planque : coffres normaux
        }
        if (p.isSneaking() && plugin.items().gunOf(e.getItem()) != null) {
            return;     // accroupi arme en main : on tire (voir GunListener)
        }
        e.setUseInteractedBlock(Event.Result.DENY);
        e.setUseItemInHand(Event.Result.DENY);
        if (e.getHand() != EquipmentSlot.HAND) {
            return;
        }
        long k = key(block);
        Box box = boxes.get(k);
        long now = System.currentTimeMillis();
        Location at = block.getLocation().add(0.5, 0.5, 0.5);
        boolean fresh = box == null || (now >= box.refill && box.inv.getViewers().isEmpty());
        if (fresh) {
            if (box != null) {
                plugin.search().forget(box.inv);
            }
            Table t = pick(block.getType());
            if (t == null) {
                return;
            }
            box = new Box();
            box.refill = now + refillMs + random.nextInt(120_000);
            box.inv = Bukkit.createInventory(box, 27, Text.mm("<" + t.color() + ">▣</" + t.color() + "> <#2B2F36>" + Text.esc(t.name())));
            Search search = plugin.search();
            if (random.nextInt(100) < t.emptyPct()) {
                search.hide(box.inv, Map.of(), 1 + random.nextInt(2));      // on fouille quand même : rien au fond
            } else {
                int rolls = t.minRolls() + random.nextInt(t.maxRolls() - t.minRolls() + 1);
                search.hide(box.inv, search.fill(box.inv, t.loot(), rolls, random), random.nextInt(2));
            }
            boxes.put(k, box);
            opened++;
            plugin.infected().noise(at, noise, p);
        }
        boolean barrel = block.getType() == Material.BARREL;
        p.getWorld().playSound(at, barrel ? Sound.BLOCK_BARREL_OPEN : Sound.BLOCK_CHEST_OPEN, 0.8f, fresh ? 0.9f : 1.1f);
        p.openInventory(box.inv);
    }
}
