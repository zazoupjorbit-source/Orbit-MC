package fr.orbit.guns.grenade;

import fr.orbit.guns.gun.GunService;
import fr.orbit.guns.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Grenades : lancer au clic droit, explosion a retardement, degats de zone custom
 *  (les explosions vanilla sont bloquees par les protections du serveur). */
public final class Grenades implements Listener {

    public record Def(String id, String display, Material baseItem, String itemModel,
                      double damage, double radius, int fuseTicks, double throwSpeed,
                      String soundThrow, String soundExplode) {}

    private final Plugin plugin;
    private final GunService service;
    private final NamespacedKey idKey;
    private final Map<String, Def> defs = new LinkedHashMap<>();
    private final Map<UUID, Long> lastThrow = new ConcurrentHashMap<>();

    public Grenades(Plugin plugin, GunService service, NamespacedKey idKey) {
        this.plugin = plugin;
        this.service = service;
        this.idKey = idKey;
    }

    public void load(FileConfiguration c) {
        defs.clear();
        ConfigurationSection root = c.getConfigurationSection("grenades");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            defs.put(id.toLowerCase(), new Def(
                    id.toLowerCase(),
                    s.getString("display", "&a&lGrenade"),
                    Material.matchMaterial(s.getString("base-item", "FIREWORK_STAR")) == null
                            ? Material.FIREWORK_STAR
                            : Material.matchMaterial(s.getString("base-item", "FIREWORK_STAR")),
                    s.getString("item-model", ""),
                    s.getDouble("damage", 30.0),
                    s.getDouble("radius", 5.0),
                    Math.max(0, s.getInt("fuse-ticks", 20)),
                    s.getDouble("throw-speed", 1.4),
                    s.getString("sound-throw", "entity.snowball.throw"),
                    s.getString("sound-explode", "entity.generic.explode")));
        }
    }

    public Def def(String id) { return id == null ? null : defs.get(id.toLowerCase()); }
    public java.util.Set<String> ids() { return defs.keySet(); }
    public int size() { return defs.size(); }

    public ItemStack build(Def def, int amount) {
        ItemStack is = new ItemStack(def.baseItem(), Math.max(1, amount));
        ItemMeta meta = is.getItemMeta();
        meta.displayName(Text.of(def.display()));
        if (!def.itemModel().isBlank()) {
            NamespacedKey model = NamespacedKey.fromString(def.itemModel());
            if (model != null) meta.setItemModel(model);
        }
        meta.lore(List.of(
                Text.of("&7Degats : &c" + def.damage() + " &7(zone " + (int) def.radius() + " blocs)"),
                Text.of(""),
                Text.of("&7Clic droit &8» &flancer")));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, def.id());
        is.setItemMeta(meta);
        return is;
    }

    public boolean isGrenade(ItemStack is) {
        return is != null && is.hasItemMeta()
                && is.getItemMeta().getPersistentDataContainer().has(idKey, PersistentDataType.STRING);
    }

    private String grenadeId(ItemStack is) {
        if (!isGrenade(is)) return null;
        return is.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
    }

    /** Clic droit avec une grenade en main = lancer. */
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!event.getAction().isRightClick()) return;
        ItemStack item = event.getItem();
        Def def = def(grenadeId(item));
        if (def == null) return;
        event.setCancelled(true);

        Player p = event.getPlayer();
        long now = System.currentTimeMillis();
        Long last = lastThrow.get(p.getUniqueId());
        if (last != null && now - last < 700) return;   // anti spam-clic
        lastThrow.put(p.getUniqueId(), now);

        item.setAmount(item.getAmount() - 1);
        Snowball ball = p.launchProjectile(Snowball.class,
                p.getEyeLocation().getDirection().multiply(def.throwSpeed()));
        ball.setItem(build(def, 1));   // la grenade 3D visible en vol
        ball.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, def.id());
        p.getWorld().playSound(p.getLocation(), def.soundThrow(), 1.0f, 0.9f);
    }

    /** Impact : la grenade se pose puis explose apres la meche. */
    @EventHandler
    public void onHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball ball)) return;
        String id = ball.getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
        Def def = def(id);
        if (def == null) return;

        Location loc = ball.getLocation();
        if (event.getHitBlockFace() != null) loc.add(event.getHitBlockFace().getDirection().multiply(0.3));
        ProjectileSource src = ball.getShooter();
        Player thrower = src instanceof Player pl ? pl : null;

        if (def.fuseTicks() <= 0) { explode(loc, def, thrower); return; }
        // pose un item "grenade au sol" le temps de la meche
        Item rest = loc.getWorld().dropItem(loc, build(def, 1));
        rest.setPickupDelay(32767);
        rest.setCanMobPickup(false);
        rest.setVelocity(new Vector(0, 0.05, 0));
        rest.setWillAge(false);
        loc.getWorld().playSound(loc, "block.note_block.hat", 0.8f, 1.6f);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Location at = rest.isValid() ? rest.getLocation() : loc;
            rest.remove();
            explode(at, def, thrower);
        }, def.fuseTicks());
    }

    private void explode(Location loc, Def def, Player thrower) {
        var world = loc.getWorld();
        world.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 2, 0.4, 0.4, 0.4, 0);
        world.spawnParticle(Particle.EXPLOSION, loc, 18, def.radius() * 0.4, 0.8, def.radius() * 0.4, 0);
        world.spawnParticle(Particle.LARGE_SMOKE, loc, 24, def.radius() * 0.3, 0.6, def.radius() * 0.3, 0.02);
        world.playSound(loc, def.soundExplode(), 1.6f, 1.0f);

        double r = def.radius();
        for (LivingEntity le : world.getNearbyLivingEntities(loc, r, r, r)) {
            if (le instanceof Player) continue;          // pas de degats aux joueurs (coop)
            double dist = le.getLocation().distance(loc);
            if (dist > r) continue;
            double dmg = def.damage() * (1.0 - 0.6 * (dist / r));   // degressif vers le bord
            if (thrower != null && thrower.isOnline()) service.damageAs(thrower, le, dmg);
            else le.damage(dmg);
            Vector push = le.getLocation().toVector().subtract(loc.toVector()).normalize()
                    .multiply(0.5).setY(0.35);
            le.setVelocity(le.getVelocity().add(push));
        }
    }
}
