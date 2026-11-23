package fr.orbit.mmo.mount;

import fr.orbit.mmo.Keys;
import fr.orbit.mmo.OrbitMMO;
import fr.orbit.mmo.continent.ContinentData;
import fr.orbit.mmo.item.Items;
import fr.orbit.mmo.mob.MobKilledEvent;
import fr.orbit.mmo.mob.MobState;
import fr.orbit.mmo.player.PlayerData;
import fr.orbit.mmo.ui.Menu;
import fr.orbit.mmo.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Montures : collection par personnage (PlayerData.mounts), appel avec /monture, écurie (menu),
 * butin sur le continent (mounts.yml → sources), objet « monture » à apprendre au clic droit.
 * Montée seulement sur le continent, hors combat ; un coup reçu ou porté fait descendre.
 */
public final class MountService implements Listener {

    public static final String ITEM_TYPE = "mount";

    private final OrbitMMO plugin;
    private final NamespacedKey key;
    private final Map<String, MountDef> defs = new LinkedHashMap<>();
    private final Map<UUID, Ride> rides = new HashMap<>();
    private final Map<UUID, Long> lastCall = new HashMap<>();
    private final Map<UUID, Long> lastNotice = new HashMap<>();
    private int combatSeconds = 6;
    private boolean dismountOnHit = true;
    private boolean flySafe = true;
    private double maxHeight = 300;
    private long cooldownMs = 3000;

    public MountService(OrbitMMO plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "mount");
    }

    public void enable() {
        load();
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void shutdown() {
        for (Ride r : new ArrayList<>(rides.values())) {
            r.remove();
        }
        rides.clear();
    }

    /** Lit mounts.yml : celui de plugins/OrbitMMO s'il existe, sinon celui du jar. */
    public int load() {
        defs.clear();
        YamlConfiguration y;
        File f = new File(plugin.getDataFolder(), "mounts.yml");
        if (f.exists()) {
            y = YamlConfiguration.loadConfiguration(f);
        } else {
            y = new YamlConfiguration();
            try (InputStreamReader r = new InputStreamReader(plugin.getResource("mounts.yml"), StandardCharsets.UTF_8)) {
                y.load(r);
            } catch (Exception e) {
                plugin.getLogger().warning("mounts.yml illisible : " + e.getMessage());
            }
        }
        ConfigurationSection s = y.getConfigurationSection("reglages");
        if (s != null) {
            combatSeconds = s.getInt("combat-secondes", combatSeconds);
            dismountOnHit = s.getBoolean("descendre-si-touche", dismountOnHit);
            flySafe = s.getBoolean("vol-zones-sures", flySafe);
            maxHeight = s.getDouble("hauteur-max", maxHeight);
            cooldownMs = (long) (s.getDouble("recharge-secondes", cooldownMs / 1000.0) * 1000);
        }
        ConfigurationSection m = y.getConfigurationSection("montures");
        if (m != null) {
            for (String id : m.getKeys(false)) {
                ConfigurationSection c = m.getConfigurationSection(id);
                if (c != null) {
                    defs.put(id, MountDef.read(id, c));
                }
            }
        }
        plugin.getLogger().info(defs.size() + " monture(s) chargée(s).");
        return defs.size();
    }

    public Map<String, MountDef> defs() {
        return defs;
    }

    public MountDef def(String id) {
        return id == null ? null : defs.get(id);
    }

    double maxHeight() {
        return maxHeight;
    }

    boolean flightAllowed(Location l) {
        return flySafe || !plugin.floors().isSafe(l);
    }

    void notice(Player p, String mini) {
        long now = System.currentTimeMillis();
        Long last = lastNotice.get(p.getUniqueId());
        if (last == null || now - last > 3000) {
            lastNotice.put(p.getUniqueId(), now);
            Text.msg(p, mini);
        }
    }

    public boolean riding(Player p) {
        return rides.containsKey(p.getUniqueId());
    }

    // ------------------------------------------------------------------ Appel / descente

    /** Appelle la monture choisie (ou la seule connue) ; ouvre l'écurie si rien n'est choisi. */
    public void call(Player p) {
        PlayerData d = plugin.players().get(p);
        if (riding(p)) {
            dismiss(p, true);
            return;
        }
        String id = d.mountPick != null && d.mounts.contains(d.mountPick) ? d.mountPick
                : d.mounts.size() == 1 ? d.mounts.iterator().next() : null;
        if (id == null) {
            openStable(p);
            return;
        }
        summon(p, id);
    }

    public void summon(Player p, String id) {
        PlayerData d = plugin.players().get(p);
        MountDef def = def(id);
        if (def == null || !d.mounts.contains(id)) {
            Text.msg(p, "<#FF5C3B>Tu ne possèdes pas cette monture.");
            return;
        }
        if (!d.hasCharacter()) {
            Text.msg(p, "<#FF5C3B>Crée d'abord ton personnage.");
            return;
        }
        if (def.faction != null && def.faction != d.faction) {
            Text.msg(p, "<#FF5C3B>" + def.name + " n'accepte que les cavaliers de la faction " + def.faction.tag() + "<#FF5C3B>.");
            return;
        }
        if (plugin.continent() == null || !plugin.continent().isContinent(p.getWorld())) {
            Text.msg(p, "<#FF5C3B>Les montures ne s'appellent que sur le continent.");
            return;
        }
        if (p.isInsideVehicle() || p.getGameMode() == GameMode.SPECTATOR || p.isDead() || p.isGliding()) {
            Text.msg(p, "<#FF5C3B>Impossible d'appeler ta monture maintenant.");
            return;
        }
        long now = System.currentTimeMillis();
        if (now - d.lastCombat < combatSeconds * 1000L) {
            Text.msg(p, "<#FF5C3B>Impossible en combat.");
            return;
        }
        Long last = lastCall.get(p.getUniqueId());
        if (last != null && now - last < cooldownMs) {
            Text.msg(p, "<#FF5C3B>Patiente un instant…");
            return;
        }
        if (!((org.bukkit.entity.Entity) p).isOnGround() && !p.getLocation().getBlock().isLiquid()) {
            Text.msg(p, "<#FF5C3B>Pose-toi au sol pour appeler ta monture.");
            return;
        }
        lastCall.put(p.getUniqueId(), now);
        d.mountPick = id;
        Location at = p.getLocation();
        Ride r = new Ride(p, def, at);
        rides.put(p.getUniqueId(), r);
        p.getWorld().playSound(at, def.flying ? Sound.ENTITY_PHANTOM_FLAP : Sound.ENTITY_HORSE_SADDLE, 1f, 1f);
        p.getWorld().spawnParticle(Particle.CLOUD, at.clone().add(0, 0.8, 0), 20, 0.6, 0.4, 0.6, 0.02);
        p.sendActionBar(Text.mm(def.tag() + (def.aquatic ? " <#B8C0CA>· dans l'eau : avancer = nager où tu regardes, [Espace] remonter, [Ctrl] plonger" : def.flying ? " <#B8C0CA>· [Espace] ou regarder en l'air pour s'envoler, [Ctrl] descendre, [Maj] mettre pied à terre"
                : " <#B8C0CA>· [Maj] mettre pied à terre")));
    }

    public void dismiss(Player p, boolean message) {
        Ride r = rides.remove(p.getUniqueId());
        if (r == null) {
            return;
        }
        boolean air = r.airborne();
        Location at = r.root.getLocation();
        r.remove();
        if (air && p.isOnline()) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 20 * 8, 0, false, false, true));
        }
        p.setFallDistance(0);
        at.getWorld().spawnParticle(Particle.CLOUD, at, 15, 0.5, 0.4, 0.5, 0.02);
        if (message) {
            p.sendActionBar(Text.mm("<#B8C0CA>Tu mets pied à terre."));
        }
    }

    private void tick() {
        if (rides.isEmpty()) {
            return;
        }
        for (Ride r : new ArrayList<>(rides.values())) {
            if (!r.valid()) {
                rides.remove(r.player.getUniqueId());
                r.remove();
                continue;
            }
            try {
                r.tick(this);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Monture de " + r.player.getName() + " : " + e);
                dismiss(r.player, false);
            }
        }
    }

    // ------------------------------------------------------------------ Objets et collection

    public ItemStack item(MountDef def) {
        List<String> lore = new ArrayList<>();
        lore.add(def.rarityTag() + " <#5A6270>·</#5A6270> " + "<#B8C0CA>Monture " + def.typeTag());
        if (def.faction != null) {
            lore.add("<#B8C0CA>Réservée à la faction " + def.faction.tag());
        }
        lore.add("");
        for (String l : def.lore) {
            lore.add("<#8A93A0><i>" + l);
        }
        lore.add("");
        lore.add("<#FFD66E>[Clic droit] <#B8C0CA>l'ajouter à ton écurie");
        ItemStack it = Items.icon(def.icon, def.tag(), lore);
        ItemMeta meta = it.getItemMeta();
        meta.getPersistentDataContainer().set(Keys.ITEM, PersistentDataType.STRING, ITEM_TYPE);
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, def.id);
        meta.setEnchantmentGlintOverride(true);
        it.setItemMeta(meta);
        return it;
    }

    public String idOf(ItemStack it) {
        if (it == null || it.isEmpty() || !it.hasItemMeta()) {
            return null;
        }
        return it.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    public void give(Player p, MountDef def) {
        for (ItemStack left : p.getInventory().addItem(item(def)).values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), left);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        ItemStack it = e.getItem();
        String id = idOf(it);
        if (id == null) {
            return;
        }
        e.setCancelled(true);
        Player p = e.getPlayer();
        MountDef def = def(id);
        PlayerData d = plugin.players().get(p);
        if (def == null) {
            Text.msg(p, "<#FF5C3B>Cette monture n'existe plus.");
            return;
        }
        if (!d.hasCharacter()) {
            Text.msg(p, "<#FF5C3B>Crée d'abord ton personnage.");
            return;
        }
        if (d.mounts.contains(id)) {
            Text.msg(p, "<#FF8A5C>Tu as déjà " + def.tag() + "<#FF8A5C> dans ton écurie. Tu peux la vendre à l'hôtel des ventes.");
            return;
        }
        it.setAmount(it.getAmount() - 1);
        d.mounts.add(id);
        if (d.mountPick == null) {
            d.mountPick = id;
        }
        Text.title(p, def.tag(), "<#B8C0CA>rejoint ton écurie · <#FFD66E>/monture", 5, 50, 15);
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
    }

    // ------------------------------------------------------------------ Butin

    @EventHandler
    public void onKill(MobKilledEvent e) {
        Player killer = e.killer();
        MobState mob = e.mob();
        if (killer == null || mob.entity == null || plugin.continent() == null) {
            return;
        }
        Location loc = mob.entity.getLocation();
        if (!plugin.continent().isContinent(loc.getWorld())) {
            return;
        }
        String region = null;
        ContinentData data = plugin.continent().data();
        if (data != null) {
            ContinentData.Region r = data.region(loc.getBlockX(), loc.getBlockZ());
            region = r == null ? null : r.id();
        }
        String mobId = mob.def == null ? null : mob.def.id();
        boolean boss = mob.boss != null;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (MountDef def : defs.values()) {
            for (MountDef.Source s : def.sources) {
                if (s.boss() && !boss) {
                    continue;
                }
                if (s.monster() != null && !s.monster().equalsIgnoreCase(mobId)) {
                    continue;
                }
                if (s.region() != null && !s.region().equalsIgnoreCase(region)) {
                    continue;
                }
                if (rnd.nextDouble() < s.chance()) {
                    give(killer, def);
                    Bukkit.broadcast(Text.mm("<#FFD66E>✦ <white>" + Text.esc(killer.getName()) + "</white> <#B8C0CA>a obtenu la monture "
                            + def.tag() + "<#B8C0CA> !"));
                    killer.playSound(killer.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                    return; // une seule monture par monstre
                }
            }
        }
    }

    // ------------------------------------------------------------------ Descentes forcées

    @EventHandler
    public void onDismount(EntityDismountEvent e) {
        if (!(e.getEntity() instanceof Player p)) {
            return;
        }
        Ride r = rides.get(p.getUniqueId());
        if (r != null && !r.ending && e.getDismounted().equals(r.root)) {
            Bukkit.getScheduler().runTask(plugin, () -> dismiss(p, true));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!dismountOnHit) {
            return;
        }
        if (e.getEntity() instanceof Player victim && riding(victim)) {
            dismiss(victim, false);
            notice(victim, "<#FF8A5C>Touché : tu es désarçonné !");
        }
        Entity dmg = e.getDamager();
        if (dmg instanceof Projectile pr && pr.getShooter() instanceof Entity shooter) {
            dmg = shooter;
        }
        if (dmg instanceof Player attacker && riding(attacker)) {
            dismiss(attacker, false);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFall(EntityDamageEvent e) {
        if (e.getCause() == EntityDamageEvent.DamageCause.FALL && e.getEntity() instanceof Player p && riding(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        dismiss(e.getPlayer(), false);
        lastCall.remove(e.getPlayer().getUniqueId());
        lastNotice.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        dismiss(e.getPlayer(), false);
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent e) {
        dismiss(e.getPlayer(), false);
    }

    // ------------------------------------------------------------------ Écurie

    /** Bouton « Montures » des menus F et Maj+F. */
    public ItemStack menuIcon(PlayerData d, boolean self) {
        List<String> lore = new ArrayList<>();
        MountDef pick = def(d.mountPick);
        lore.add("<#8C96A3>Écurie : <white>" + d.mounts.size() + " / " + defs.size() + "</white> montures");
        if (pick != null && d.mounts.contains(pick.id)) {
            lore.add("<#8C96A3>Choisie : " + pick.tag());
        }
        if (self) {
            lore.add("");
            lore.add("<#5A6270>Appeler / renvoyer : /monture");
            lore.add("<#5A6270>Tombent des monstres du continent.");
            lore.add("<#FFD66E>[Clic] <#B8C0CA>ouvrir l'écurie");
        }
        return Items.icon(pick != null && d.mounts.contains(pick.id) ? pick.icon : org.bukkit.Material.SADDLE,
                "<#C9A26B><b>Montures</b>", lore);
    }

    public void openStable(Player p) {
        PlayerData d = plugin.players().get(p);
        int rows = Math.max(3, Math.min(6, (defs.size() + 8) / 9 + 2));
        Menu m = new Menu(rows, Text.mm("Écurie"));
        m.skin("Écurie", d.mounts.size() + " / " + defs.size() + " montures", null);
        int slot = 10;
        for (MountDef def : defs.values()) {
            if (slot % 9 == 8) {
                slot += 2;
            }
            if (slot >= rows * 9 - 9) {
                break;
            }
            boolean owned = d.mounts.contains(def.id);
            List<String> lore = new ArrayList<>();
            lore.add(def.rarityTag() + " <#5A6270>·</#5A6270> " + def.typeTag());
            if (def.faction != null) {
                lore.add("<#B8C0CA>Faction " + def.faction.tag());
            }
            for (String l : def.lore) {
                lore.add("<#8A93A0><i>" + l);
            }
            lore.add("");
            if (owned) {
                lore.add(def.id.equals(d.mountPick) ? "<#5EE89A>✔ Monture choisie" : "<#FFD66E>[Clic] <#B8C0CA>la choisir et l'appeler");
            } else {
                lore.add("<#FF8A5C>Pas encore obtenue. Où la trouver :");
                for (MountDef.Source s : def.sources) {
                    lore.add("<#8A93A0> • " + s.describe());
                }
            }
            ItemStack icon = Items.icon(owned ? def.icon : org.bukkit.Material.GRAY_DYE, owned ? def.tag() : "<#5A6270>" + def.name, lore);
            if (owned && def.id.equals(d.mountPick)) {
                Items.glow(icon);
            }
            String id = def.id;
            m.set(slot, icon, click -> {
                if (!d.mounts.contains(id)) {
                    return;
                }
                d.mountPick = id;
                p.closeInventory();
                if (riding(p)) {
                    dismiss(p, false);
                }
                summon(p, id);
            });
            slot++;
        }
        m.open(p);
    }
}
