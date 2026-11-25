package fr.orbit.staff;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.sql.PreparedStatement;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mesures de combat en direct (style Pulsar) : clics par seconde, allonge des coups. Sert le livre et
 * alerte le staff au-delà des seuils. Ne sanctionne jamais tout seul.
 */
public final class Combat implements Listener {

    /** Mesures de la session d'un joueur. */
    public static final class Mesure {
        int clicsSeconde;
        public int cps;
        public int cpsMax;
        public double allongeMax;
        public double allongeDerniere;
        public int coups;
        final long[] derniereAlerte = new long[2];
    }

    private final OrbitStaff plugin;
    private final Map<UUID, Mesure> mesures = new ConcurrentHashMap<>();

    public Combat(OrbitStaff plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::seconde, 20L, 20L);
    }

    public Mesure mesure(UUID id) {
        return mesures.get(id);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwing(PlayerAnimationEvent e) {
        if (e.getAnimationType() == PlayerAnimationType.ARM_SWING) {
            mesures.computeIfAbsent(e.getPlayer().getUniqueId(), k -> new Mesure()).clicsSeconde++;
        }
    }

    private void seconde() {
        int seuil = plugin.getConfig().getInt("combat.cps-alerte", 18);
        for (Map.Entry<UUID, Mesure> e : mesures.entrySet()) {
            Mesure m = e.getValue();
            m.cps = m.clicsSeconde;
            m.clicsSeconde = 0;
            m.cpsMax = Math.max(m.cpsMax, m.cps);
            if (m.cps >= seuil) {
                Player p = Bukkit.getPlayer(e.getKey());
                if (p != null && !exempte(p)) {
                    alerte(p, m, 0, "cps", m.cps + " CPS");
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p) || !(e.getEntity() instanceof LivingEntity cible)
                || e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            return;
        }
        Mesure m = mesures.computeIfAbsent(p.getUniqueId(), k -> new Mesure());
        // Distance de l'œil au point le plus proche de la boîte de la cible.
        Vector oeil = p.getEyeLocation().toVector();
        BoundingBox b = cible.getBoundingBox();
        double x = Math.max(b.getMinX(), Math.min(oeil.getX(), b.getMaxX()));
        double y = Math.max(b.getMinY(), Math.min(oeil.getY(), b.getMaxY()));
        double z = Math.max(b.getMinZ(), Math.min(oeil.getZ(), b.getMaxZ()));
        double d = oeil.distance(new Vector(x, y, z));
        m.allongeDerniere = d;
        m.coups++;
        if (!(cible instanceof Player) || exempte(p)) {
            return;
        }
        m.allongeMax = Math.max(m.allongeMax, d);
        // Tolérance de latence : 2 blocs par seconde de ping, au plus +0,6.
        double seuil = plugin.getConfig().getDouble("combat.allonge-alerte", 3.6) + Math.min(0.6, p.getPing() / 1000.0 * 2);
        if (d > seuil) {
            alerte(p, m, 1, "allonge", String.format("allonge %.2f blocs (ping %d)", d, p.getPing()));
        }
    }

    private static boolean exempte(Player p) {
        return p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR;
    }

    private void alerte(Player p, Mesure m, int i, String type, String detail) {
        long now = System.currentTimeMillis();
        if (now - m.derniereAlerte[i] < plugin.getConfig().getLong("combat.alerte-delai-secondes", 10) * 1000) {
            return;
        }
        m.derniereAlerte[i] = now;
        String nom = p.getName();
        String uuid = p.getUniqueId().toString();
        String serveur = plugin.serveur();
        plugin.db().fire(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO staff_alertes (uuid, type, detail, serveur) VALUES (?,?,?,?)")) {
                ps.setString(1, uuid);
                ps.setString(2, type);
                ps.setString(3, detail);
                ps.setString(4, serveur);
                ps.executeUpdate();
            }
            return null;
        });
        plugin.flux().poster(Flux.ALERTE, p.getUniqueId(), "<white>" + Txt.esc(nom) + " <gray>— " + detail + " "
                + "<#4aa3ff><click:run_command:'/panel " + nom + "'>[Panel]</click> <#f5c542><click:run_command:'/stats " + nom
                + "'>[Livre]</click>");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        mesures.remove(e.getPlayer().getUniqueId());
    }
}
