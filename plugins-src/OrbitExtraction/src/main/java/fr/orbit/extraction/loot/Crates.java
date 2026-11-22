package fr.orbit.extraction.loot;

import fr.orbit.extraction.Keys;
import fr.orbit.extraction.OrbitExtraction;
import fr.orbit.extraction.raid.AutoSpawns;
import fr.orbit.extraction.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Caisses de butin du raid, façon largages : un nombre fixe de caisses est réparti au hasard sur l'île, chacune
 * d'un niveau (blanche → rouge) qui fixe son contenu. Une colonne de lumière de sa couleur la signale de loin et
 * elle apparaît sur la carte tactique. Une caisse vidée ou trop vieille est remplacée ailleurs.
 *
 * Les caisses sont d'abord logiques (position + contenu) : leurs entités n'existent que lorsqu'un joueur est
 * assez près, ce qui évite de garder des chunks chargés partout sur l'île.
 */
public final class Crates implements Listener {

    public record Entry(String spec, int weight) {
    }

    public record Tier(String id, String name, String color, int rgb, Material block, Material glass, String banner, int weight,
                       int minRolls, int maxRolls, boolean announce, List<Entry> loot) {
    }

    /** Ce que la carte tactique a besoin de savoir d'une caisse. */
    public record View(int x, int z, Tier tier) {
    }

    private static final class Crate implements InventoryHolder {
        final String id = UUID.randomUUID().toString();
        final Tier tier;
        final int x, z;
        int y;
        Inventory inv;
        long expires;
        boolean opened;
        /** Caisse d'événement (crash, largage) : hors du quota des caisses ordinaires. */
        boolean event;
        Location at;
        BlockDisplay box;
        BlockDisplay beam;
        Interaction hit;

        Crate(Tier tier, int x, int y, int z) {
            this.tier = tier;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private static final float BEAM_HEIGHT = 96f;

    private final OrbitExtraction plugin;
    private final Map<String, Crate> crates = new LinkedHashMap<>();
    private final List<Tier> tiers = new ArrayList<>();
    private final Random random = new Random();
    private boolean enabled;
    private int target;
    private long lifetimeMs;
    private long respawnMs;
    private double minDistance;
    private double showRange;
    private double noise;
    private long nextSpawn;
    private int ticks;

    public Crates(OrbitExtraction plugin) {
        this.plugin = plugin;
    }

    public void load(ConfigurationSection c) {
        clear();
        tiers.clear();
        enabled = c != null && c.getBoolean("actives-en-jeu", true);
        target = c == null ? 0 : Math.max(0, c.getInt("nombre", 14));
        lifetimeMs = (long) ((c == null ? 12 : Math.max(1, c.getDouble("duree-minutes", 12))) * 60_000);
        respawnMs = (long) ((c == null ? 45 : Math.max(0, c.getDouble("reapparition-secondes", 45))) * 1000);
        minDistance = c == null ? 90 : c.getDouble("distance-min", 90);
        showRange = c == null ? 150 : Math.max(48, c.getDouble("distance-affichage", 150));
        noise = c == null ? 30 : c.getDouble("bruit", 30);
        ConfigurationSection ts = c == null ? null : c.getConfigurationSection("niveaux");
        if (ts != null) {
            for (String id : ts.getKeys(false)) {
                ConfigurationSection s = ts.getConfigurationSection(id);
                if (s == null) {
                    continue;
                }
                List<Entry> loot = new ArrayList<>();
                for (Map<?, ?> m : s.getMapList("butin")) {
                    Object spec = m.get("objet");
                    if (spec != null) {
                        loot.add(new Entry(spec.toString(), m.get("poids") instanceof Number n ? Math.max(1, n.intValue()) : 10));
                    }
                }
                if (loot.isEmpty()) {
                    continue;
                }
                String color = s.getString("couleur", "#E6EDF3");
                int rgb;
                try {
                    rgb = Integer.parseInt(color.substring(1), 16);
                } catch (RuntimeException e) {
                    color = "#E6EDF3";
                    rgb = 0xE6EDF3;
                }
                String[] rolls = s.getString("tirages", "3-4").split("-");
                int a = parse(rolls[0], 3), b = rolls.length > 1 ? parse(rolls[1], a) : a;
                tiers.add(new Tier(id.toLowerCase(Locale.ROOT), s.getString("nom", id), color, rgb,
                        block(s.getString("bloc"), Material.BARREL), block(s.getString("faisceau"), Material.WHITE_STAINED_GLASS),
                        s.getString("banniere", "WHITE").toUpperCase(Locale.ROOT), Math.max(0, s.getInt("poids", 10)),
                        Math.max(1, a), Math.max(a, b), s.getBoolean("annonce", false), loot));
            }
        }
        nextSpawn = 0;
        plugin.getLogger().info("Caisses de butin : " + (enabled && !tiers.isEmpty() ? target + " sur l'île, " + tiers.size() + " niveaux." : "désactivées."));
    }

    private static int parse(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static Material block(String name, Material def) {
        Material m = name == null ? null : Material.matchMaterial(name);
        return m != null && m.isBlock() ? m : def;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 60L, 10L);
    }

    public Collection<View> views() {
        List<View> out = new ArrayList<>(crates.size());
        for (Crate c : crates.values()) {
            out.add(new View(c.x, c.z, c.tier));
        }
        return out;
    }

    public int count() {
        return crates.size();
    }

    private int regular() {
        int n = 0;
        for (Crate c : crates.values()) {
            if (!c.event) {
                n++;
            }
        }
        return n;
    }

    public Tier tier(String id) {
        for (Tier t : tiers) {
            if (t.id().equalsIgnoreCase(id)) {
                return t;
            }
        }
        return null;
    }

    /**
     * Pose une caisse d'événement (crash d'hélicoptère, largage) à un point déjà vérifié ; elle ne compte pas dans
     * le quota des caisses ordinaires. Faux si le niveau n'existe pas.
     */
    public boolean drop(String tierId, int x, int y, int z, double minutes) {
        Tier t = tier(tierId);
        if (t == null) {
            return false;
        }
        Crate c = new Crate(t, x, y, z);
        c.event = true;
        c.expires = System.currentTimeMillis() + (long) (minutes * 60_000);
        c.inv = Bukkit.createInventory(c, 27, Text.mm("<" + t.color() + ">▼</" + t.color() + "> <#2B2F36>" + Text.esc(t.name())));
        fill(c);
        crates.put(c.id, c);
        return true;
    }

    /** Résumé pour /extraction caisses. */
    public String summary() {
        Map<String, Integer> n = new LinkedHashMap<>();
        for (Tier t : tiers) {
            n.put(t.id(), 0);
        }
        for (Crate c : crates.values()) {
            n.merge(c.tier.id(), 1, Integer::sum);
        }
        StringBuilder sb = new StringBuilder();
        for (Tier t : tiers) {
            sb.append(sb.isEmpty() ? "" : "<#5A6270>, </#5A6270>").append("<").append(t.color()).append(">").append(Text.esc(t.name()))
                    .append(" ×").append(n.get(t.id())).append("</").append(t.color()).append(">");
        }
        return sb.toString();
    }

    /** Retire toutes les caisses : elles sont redistribuées au prochain passage. */
    public void clear() {
        for (Crate c : crates.values()) {
            hide(c);
            closeViewers(c);
        }
        crates.clear();
        nextSpawn = 0;
    }

    public void shutdown() {
        clear();
    }

    // ------------------------------------------------------------------ boucle

    private void tick() {
        ticks += 10;
        if (!enabled || tiers.isEmpty() || target <= 0) {
            return;
        }
        World w = Bukkit.getWorld(plugin.raid().worldName());
        AutoSpawns auto = plugin.autoSpawns();
        if (w == null || auto.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        Iterator<Crate> it = crates.values().iterator();
        while (it.hasNext()) {
            Crate c = it.next();
            boolean viewers = !c.inv.getViewers().isEmpty();
            boolean empty = c.opened && c.inv.isEmpty();
            if (!viewers && (empty || now >= c.expires)) {
                if (c.box != null && c.box.isValid()) {
                    w.spawnParticle(Particle.CLOUD, c.at.clone().add(0, 0.5, 0), 14, 0.3, 0.3, 0.3, 0.02);
                }
                hide(c);
                it.remove();
                if (!c.event) {
                    nextSpawn = Math.max(nextSpawn, now + respawnMs);
                }
            }
        }
        if (ticks % 20 == 0 && now >= nextSpawn) {
            int have = regular();
            for (int i = 0; i < 3 && have + i < target; i++) {
                create(w, auto, now);
            }
        }
        List<Player> players = w.getPlayers();
        for (Crate c : new ArrayList<>(crates.values())) {
            boolean near = false;
            for (Player p : players) {
                double dx = p.getX() - c.x, dz = p.getZ() - c.z;
                if (dx * dx + dz * dz <= showRange * showRange) {
                    near = true;
                    break;
                }
            }
            boolean shown = c.box != null && c.box.isValid();
            if (near && !shown && w.isChunkLoaded(c.x >> 4, c.z >> 4)) {
                hide(c);    // entités orphelines d'un chunk déchargé
                if (!show(w, c)) {
                    crates.remove(c.id);    // le terrain a changé à cet endroit : la caisse sera retirée ailleurs
                    continue;
                }
                shown = true;
            } else if (!near && c.box != null) {
                hide(c);
                shown = false;
            }
            if (shown) {
                w.spawnParticle(Particle.DUST, c.at.clone().add(0, 1.5, 0), 5, 0.25, 0.5, 0.25, 0,
                        new Particle.DustOptions(Color.fromRGB(c.tier.rgb()), 1.5f), true);
            }
        }
    }

    private Tier pickTier() {
        int total = 0;
        for (Tier t : tiers) {
            total += t.weight();
        }
        if (total <= 0) {
            return null;
        }
        int r = random.nextInt(total);
        for (Tier t : tiers) {
            r -= t.weight();
            if (r < 0) {
                return t;
            }
        }
        return null;
    }

    private void create(World w, AutoSpawns auto, long now) {
        int[] pt = null;
        for (int attempt = 0; attempt < 24 && pt == null; attempt++) {
            int[] cand = auto.randomPoint();
            if (cand == null) {
                return;
            }
            boolean ok = true;
            for (Crate o : crates.values()) {
                double dx = o.x - cand[0], dz = o.z - cand[2];
                if (dx * dx + dz * dz < minDistance * minDistance) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                pt = cand;
            }
        }
        if (pt == null) {
            return;
        }
        Tier t = pickTier();
        if (t == null) {
            return;
        }
        Crate c = new Crate(t, pt[0], pt[1], pt[2]);
        c.expires = now + lifetimeMs + random.nextInt(60_000);
        c.inv = Bukkit.createInventory(c, 27, Text.mm("<" + t.color() + ">▼</" + t.color() + "> <#2B2F36>" + Text.esc(t.name())));
        fill(c);
        crates.put(c.id, c);
        if (t.announce()) {
            for (Player p : w.getPlayers()) {
                if (plugin.raid().inRaid(p)) {
                    int d = (int) Math.round(Math.hypot(p.getX() - c.x, p.getZ() - c.z));
                    Text.msg(p, "<" + t.color() + "><b>" + Text.esc(t.name()) + "</b></" + t.color() + "> repérée à <#D7DCE2>" + d
                            + " m</#D7DCE2> " + arrow(p, c.x, c.z) + " <#8A919B>— elle est sur ta carte.");
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.7f, 0.7f);
                }
            }
        }
    }

    private static String arrow(Player p, double x, double z) {
        double target = Math.toDegrees(Math.atan2(-(x - p.getX()), z - p.getZ()));
        double rel = ((target - p.getLocation().getYaw()) % 360 + 540) % 360 - 180;
        String[] arrows = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
        return arrows[(((int) Math.round(rel / 45.0) % 8) + 8) % 8];
    }

    private void fill(Crate c) {
        Tier t = c.tier;
        int total = 0;
        for (Entry e : t.loot()) {
            total += e.weight();
        }
        int rolls = t.minRolls() + random.nextInt(t.maxRolls() - t.minRolls() + 1);
        List<Integer> free = new ArrayList<>();
        for (int i = 0; i < 27; i++) {
            free.add(i);
        }
        for (int i = 0; i < rolls; i++) {
            int r = random.nextInt(total);
            Entry pick = t.loot().get(0);
            for (Entry e : t.loot()) {
                r -= e.weight();
                if (r < 0) {
                    pick = e;
                    break;
                }
            }
            for (ItemStack it : plugin.specs().create(pick.spec(), true)) {
                if (free.isEmpty()) {
                    return;
                }
                c.inv.setItem(free.remove(random.nextInt(free.size())), it);
            }
        }
    }

    // ------------------------------------------------------------------ entités

    private boolean show(World w, Crate c) {
        Location l = AutoSpawns.check(w, c.x, c.y, c.z);
        if (l == null) {
            return false;
        }
        c.y = l.getBlockY();
        c.at = l;
        Tier t = c.tier;
        c.box = w.spawn(l, BlockDisplay.class, d -> {
            d.setBlock(t.block().createBlockData());
            d.setPersistent(false);
            d.setTransformation(new Transformation(new Vector3f(-0.45f, 0f, -0.45f), new AxisAngle4f(),
                    new Vector3f(0.9f, 0.9f, 0.9f), new AxisAngle4f()));
            d.setViewRange(4f);
            d.setGlowing(true);
            d.setGlowColorOverride(Color.fromRGB(t.rgb()));
        });
        c.beam = w.spawn(l.clone().add(0, 1.0, 0), BlockDisplay.class, d -> {
            d.setBlock(t.glass().createBlockData());
            d.setPersistent(false);
            d.setTransformation(new Transformation(new Vector3f(-0.14f, 0f, -0.14f), new AxisAngle4f(),
                    new Vector3f(0.28f, BEAM_HEIGHT, 0.28f), new AxisAngle4f()));
            d.setBrightness(new Display.Brightness(15, 15));
            d.setViewRange(8f);
            d.setDisplayWidth(2f);
            d.setDisplayHeight(BEAM_HEIGHT * 2);
        });
        c.hit = w.spawn(l, Interaction.class, i -> {
            i.setInteractionWidth(1.2f);
            i.setInteractionHeight(1.05f);
            i.setResponsive(true);
            i.setPersistent(false);
            i.getPersistentDataContainer().set(Keys.CRATE, PersistentDataType.STRING, c.id);
        });
        return true;
    }

    private static void hide(Crate c) {
        if (c.box != null) {
            c.box.remove();
        }
        if (c.beam != null) {
            c.beam.remove();
        }
        if (c.hit != null) {
            c.hit.remove();
        }
        c.box = null;
        c.beam = null;
        c.hit = null;
    }

    private static void closeViewers(Crate c) {
        if (c.inv != null) {
            for (org.bukkit.entity.HumanEntity v : new ArrayList<>(c.inv.getViewers())) {
                v.closeInventory();
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof Interaction i)) {
            return;
        }
        String id = i.getPersistentDataContainer().get(Keys.CRATE, PersistentDataType.STRING);
        if (id == null) {
            return;
        }
        e.setCancelled(true);
        Crate c = crates.get(id);
        Player p = e.getPlayer();
        if (c == null) {
            i.remove();     // caisse d'une session précédente
            return;
        }
        if (!plugin.raid().inRaid(p)) {
            return;
        }
        if (!c.opened) {
            c.opened = true;
            plugin.infected().noise(c.at, noise, p);
            p.getWorld().playSound(c.at, Sound.BLOCK_ENDER_CHEST_OPEN, 1.2f, 0.8f);
        } else {
            p.playSound(c.at, Sound.BLOCK_BARREL_OPEN, 0.8f, 1f);
        }
        p.openInventory(c.inv);
    }
}
