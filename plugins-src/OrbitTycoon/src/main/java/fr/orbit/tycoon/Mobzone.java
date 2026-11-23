package fr.orbit.tycoon;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** Fait apparaître des monstres dans l'arène tant qu'au moins un joueur s'y trouve. */
final class Mobzone {
    private final OrbitTycoon plugin;
    private final NamespacedKey cle;
    private final Set<UUID> suivis = new HashSet<>();
    private final Random rnd = new Random();
    private BukkitTask tache;

    Mobzone(OrbitTycoon plugin) {
        this.plugin = plugin;
        this.cle = new NamespacedKey(plugin, "mobzone");
    }

    void demarrer() {
        arreter();
        long t = Math.max(1, plugin.getConfig().getInt("mobzone.intervalle", 5)) * 20L;
        tache = plugin.getServer().getScheduler().runTaskTimer(plugin, this::vague, t, t);
    }

    void arreter() {
        if (tache != null) { tache.cancel(); tache = null; }
    }

    /** Retire tous les monstres de l'arène (arrêt du plugin). */
    void vider(World w) {
        for (UUID id : suivis) { Entity e = w.getEntity(id); if (e != null) e.remove(); }
        suivis.clear();
    }

    private void vague() {
        World w = plugin.monde();
        Zone z = Zone.MOBZONE;
        boolean joueur = false;
        for (Player p : w.getPlayers()) if (Zone.at(p.getLocation()) == z) { joueur = true; break; }
        suivis.removeIf(id -> { Entity e = w.getEntity(id); return e == null || !e.isValid() || e.isDead(); });
        if (!joueur) return;

        int max = plugin.getConfig().getInt("mobzone.max", 25);
        int n = Math.min(plugin.getConfig().getInt("mobzone.par-vague", 4), max - suivis.size());
        List<EntityType> types = new ArrayList<>();
        for (String s : plugin.getConfig().getStringList("mobzone.types")) {
            try { EntityType t = EntityType.valueOf(s.toUpperCase()); if (t.isAlive() && t.isSpawnable()) types.add(t); }
            catch (IllegalArgumentException ex) { plugin.getLogger().warning("mobzone.types : type inconnu « " + s + " »."); }
        }
        if (types.isEmpty()) return;

        Location arrivee = z.arrivee(w);
        for (int i = 0; i < n; i++) {
            Location loc = point(w, z, arrivee);
            if (loc == null) continue;
            EntityType type = types.get(rnd.nextInt(types.size()));
            Entity e = w.spawnEntity(loc, type, CreatureSpawnEvent.SpawnReason.CUSTOM, ent -> {
                if (ent instanceof Zombie zo) { zo.setShouldBurnInDay(false); zo.setAdult(); }
                if (ent instanceof AbstractSkeleton sk) sk.setShouldBurnInDay(false);
                if (ent instanceof Mob m) m.setRemoveWhenFarAway(false);
                if (ent instanceof LivingEntity le) le.setPersistent(false);
                ent.getPersistentDataContainer().set(cle, PersistentDataType.BYTE, (byte) 1);
            });
            suivis.add(e.getUniqueId());
        }
    }

    /** Point libre au hasard dans l'arène, loin du pad d'arrivée. */
    /** Point libre entre 10 et 28 blocs d'un joueur de la zone, sur l'île, loin de l'arrivée. */
    private Location point(World w, Zone z, Location arrivee) {
        List<Player> ici = new ArrayList<>();
        for (Player p : w.getPlayers()) if (Zone.at(p.getLocation()) == z) ici.add(p);
        if (ici.isEmpty()) return null;
        Location centre = ici.get(rnd.nextInt(ici.size())).getLocation();
        for (int essai = 0; essai < 10; essai++) {
            double a = rnd.nextDouble() * Math.PI * 2, r = 10 + rnd.nextDouble() * 18;
            int x = centre.getBlockX() + (int) Math.round(Math.cos(a) * r), zz = centre.getBlockZ() + (int) Math.round(Math.sin(a) * r);
            if (z.distance(x, zz) > z.rayon - 6) continue;
            Location loc = new Location(w, x + 0.5, Zone.Y + 1, zz + 0.5);
            if (loc.distanceSquared(arrivee) < 30 * 30) continue;
            Block pied = w.getBlockAt(x, Zone.Y + 1, zz), tete = w.getBlockAt(x, Zone.Y + 2, zz), sol = w.getBlockAt(x, Zone.Y, zz);
            if (pied.isPassable() && tete.isPassable() && sol.isSolid()) return loc;
        }
        return null;
    }
}
