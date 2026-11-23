package fr.orbit.tycoon;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Anneaux-portails : un joueur qui traverse le disque d'un anneau est téléporté.
 * Sur le spawn : un anneau par zone. Sur chaque île : un anneau de retour vers le spawn.
 */
final class Portails implements Listener {

    private record Portail(double x, double y, double z, double nx, double nz, Zone vers, Zone retourDe) {}

    private final OrbitTycoon plugin;
    private final Navigateur navigateur;
    private final List<Portail> portails = new ArrayList<>();
    private final Map<UUID, Long> attente = new HashMap<>();
    private BukkitTask effets;

    Portails(OrbitTycoon plugin, Navigateur navigateur) {
        this.plugin = plugin;
        this.navigateur = navigateur;
        for (Zone z : Zone.farms()) {
            double[] b = z.portailBase(), r = z.portailRetour();
            portails.add(new Portail(b[0], b[1], b[2], b[3], b[4], z, null));
            portails.add(new Portail(r[0], r[1], r[2], r[3], r[4], Zone.BASE, z));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void bouger(PlayerMoveEvent e) {
        Location to = e.getTo();
        if (to.getBlockX() == e.getFrom().getBlockX() && to.getBlockY() == e.getFrom().getBlockY() && to.getBlockZ() == e.getFrom().getBlockZ()) return;
        Player p = e.getPlayer();
        Long t = attente.get(p.getUniqueId());
        if (t != null && System.currentTimeMillis() < t) return;
        double cx = to.getX(), cy = to.getY() + 0.9, cz = to.getZ();
        for (Portail po : portails) {
            double vx = cx - po.x, vy = cy - po.y, vz = cz - po.z;
            if (vx * vx + vz * vz > 100) continue;
            double dn = vx * po.nx + vz * po.nz, u = -vx * po.nz + vz * po.nx;
            if (Math.abs(dn) > 0.7 || u * u + vy * vy > Zone.RAYON_PORTAIL * Zone.RAYON_PORTAIL) continue;
            attente.put(p.getUniqueId(), System.currentTimeMillis() + 3000);
            Location dest = po.retourDe != null ? po.retourDe.retourBase(p.getWorld()) : po.vers.arrivee(p.getWorld());
            navigateur.teleporter(p, po.vers, dest);
            return;
        }
    }

    /** Particules et son autour des anneaux quand un joueur est proche. */
    void demarrer() {
        arreter();
        Particle.DustOptions bleu = new Particle.DustOptions(Color.fromRGB(0x62, 0xCF, 0xF4), 1.3f);
        Particle.DustOptions or = new Particle.DustOptions(Color.fromRGB(0xE0, 0xB8, 0x64), 1.0f);
        effets = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            World w = plugin.monde();
            if (w.getPlayers().isEmpty()) return;
            long tick = w.getGameTime();
            for (Portail po : portails) {
                Location c = new Location(w, po.x, po.y, po.z);
                boolean proche = false;
                for (Player p : w.getPlayers()) if (p.getLocation().distanceSquared(c) < 48 * 48) { proche = true; break; }
                if (!proche) continue;
                double tx = -po.nz, tz = po.nx;
                for (int i = 0; i < 10; i++) {
                    double a = (tick * 0.12) + i * (Math.PI * 2 / 10);
                    double r = Zone.RAYON_PORTAIL * (0.35 + 0.6 * ((i % 3) / 2.0));
                    double u = Math.cos(a) * r, v = Math.sin(a) * r;
                    w.spawnParticle(Particle.DUST, po.x + tx * u, po.y + v, po.z + tz * u, 1, 0, 0, 0, 0, i % 4 == 0 ? or : bleu);
                }
                if (tick % 80 == 0) w.playSound(c, Sound.BLOCK_PORTAL_AMBIENT, 0.35f, 1.4f);
            }
        }, 20L, 4L);
    }

    void arreter() {
        if (effets != null) { effets.cancel(); effets = null; }
    }
}
