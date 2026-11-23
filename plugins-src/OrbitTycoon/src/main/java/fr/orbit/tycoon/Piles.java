package fr.orbit.tycoon;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.WaterMob;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Empilement de la Mobzone.
 * - Spawner empilé : son niveau (x1..x5, dans le PDC du bloc) = nombre de mobs dans chaque apparition.
 * - Mob empilé : les mobs du même type proches fusionnent en un seul (« Zombie x12 ») ; à sa mort, le suivant prend sa place.
 */
final class Piles implements Listener {
    private static final TextColor OR = TextColor.color(0xE0B864), BLEU = TextColor.color(0x62CFF4);
    /** Boss et mobs sans intérêt hors de l'eau ou sans IA : jamais dans un spawner. */
    private static final Set<EntityType> EXCLUS = EnumSet.of(EntityType.ENDER_DRAGON, EntityType.WITHER,
            EntityType.WARDEN, EntityType.ELDER_GUARDIAN, EntityType.GIANT);

    private final OrbitTycoon plugin;
    private final NamespacedKey clePile, cleNombre;

    Piles(OrbitTycoon plugin) {
        this.plugin = plugin;
        this.clePile = clePile(plugin);
        this.cleNombre = new NamespacedKey(plugin, "nombre");
    }

    static NamespacedKey clePile(Plugin plugin) { return new NamespacedKey(plugin, "pile"); }

    /** Tous les mobs vivants qu'un spawner peut faire apparaître (hors boss et mobs aquatiques). */
    static List<EntityType> mobsDeSpawner() {
        List<EntityType> res = new ArrayList<>();
        for (EntityType t : EntityType.values()) {
            Class<? extends Entity> cls = t.getEntityClass();
            if (cls == null || !t.isSpawnable() || !t.isAlive() || EXCLUS.contains(t)) continue;
            if (!Mob.class.isAssignableFrom(cls) || WaterMob.class.isAssignableFrom(cls)) continue;
            res.add(t);
        }
        return res;
    }

    static Component nom(EntityType t) { return Component.translatable(t.translationKey()); }

    static Component etiquetteSpawner(EntityType t, int niveau) {
        return Component.text("Spawner ", BLEU).append(nom(t).color(TextColor.color(0xFFFFFF)))
                .append(Component.text(" x" + niveau, OR).decoration(TextDecoration.BOLD, true));
    }

    private int max() { return Math.max(1, plugin.getConfig().getInt("mobzone.pile-max", 50)); }

    private double rayon() { return plugin.getConfig().getDouble("mobzone.pile-rayon", 8); }

    int nombre(Entity e) { return e.getPersistentDataContainer().getOrDefault(cleNombre, PersistentDataType.INTEGER, 1); }

    private void fixer(LivingEntity e, int n) {
        e.getPersistentDataContainer().set(cleNombre, PersistentDataType.INTEGER, n);
        e.customName(nom(e.getType()).color(TextColor.color(0xFFFFFF)).append(Component.text(" x" + n, OR).decoration(TextDecoration.BOLD, true)));
        e.setCustomNameVisible(true);
        if (e instanceof Zombie z) z.setShouldBurnInDay(false);
        if (e instanceof AbstractSkeleton s) s.setShouldBurnInDay(false);
        if (e instanceof Phantom p) p.setShouldBurnInDay(false);
        figer(e);
    }

    /**
     * Mob de spawner : il reste planté là où il est apparu. Pas d'IA (ne marche pas, n'attaque pas, ne vole pas),
     * aucun recul quand on le frappe ; la gravité reste (un blaze retombe au sol).
     */
    private static void figer(LivingEntity e) {
        if (e instanceof Mob m) m.setAware(false);
        var recul = e.getAttribute(org.bukkit.attribute.Attribute.KNOCKBACK_RESISTANCE);
        if (recul != null) recul.setBaseValue(1.0);
    }

    /** Les mobs figés ne se téléportent pas non plus (endermen, shulkers frappés). */
    @EventHandler(ignoreCancelled = true)
    public void teleportation(org.bukkit.event.entity.EntityTeleportEvent e) {
        if (e.getEntity().getPersistentDataContainer().has(cleNombre, PersistentDataType.INTEGER)) e.setCancelled(true);
    }

    /** Mobs déjà là avant la mise à jour (chargés avec leur chunk) : on les fige aussi. */
    @EventHandler
    public void chargement(org.bukkit.event.world.EntitiesLoadEvent e) {
        for (Entity en : e.getEntities())
            if (en instanceof LivingEntity le && le.getPersistentDataContainer().has(cleNombre, PersistentDataType.INTEGER)) figer(le);
    }

    /** Un spawner empilé fait apparaître une pile de N mobs, qui rejoint la pile voisine du même type si possible. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void apparition(SpawnerSpawnEvent e) {
        if (!(e.getEntity() instanceof LivingEntity mob)) return;
        CreatureSpawner s = e.getSpawner();
        int niveau = s == null ? 1 : s.getPersistentDataContainer().getOrDefault(clePile, PersistentDataType.INTEGER, 1);
        fixer(mob, Math.min(niveau, max()));
        // On ne peut pas annuler l'événement (le spawner réessaierait à chaque tick) : on fusionne au tick suivant.
        Bukkit.getScheduler().runTask(plugin, () -> fusionner(mob));
    }

    private void fusionner(LivingEntity mob) {
        if (!mob.isValid()) return;
        int n = nombre(mob), max = max();
        double r = rayon();
        LivingEntity cible = null;
        double meilleure = Double.MAX_VALUE;
        for (LivingEntity o : mob.getLocation().getNearbyLivingEntities(r)) {
            if (o == mob || o.getType() != mob.getType() || !o.isValid() || o.isDead()) continue;
            if (!o.getPersistentDataContainer().has(cleNombre, PersistentDataType.INTEGER)) continue;
            if (nombre(o) + n > max) continue;
            double d = o.getLocation().distanceSquared(mob.getLocation());
            if (d < meilleure) { meilleure = d; cible = o; }
        }
        if (cible == null) return;
        fixer(cible, nombre(cible) + n);
        mob.remove();
    }

    /** À la mort d'une pile, une nouvelle prend sa place avec un mob de moins. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void mort(EntityDeathEvent e) {
        LivingEntity mort = e.getEntity();
        int n = nombre(mort);
        if (n <= 1 || !mort.getPersistentDataContainer().has(cleNombre, PersistentDataType.INTEGER)) return;
        Location l = mort.getLocation();
        Entity suite = l.getWorld().spawnEntity(l, mort.getType(), CreatureSpawnEvent.SpawnReason.CUSTOM);
        if (suite instanceof LivingEntity le) fixer(le, n - 1);
    }
}
