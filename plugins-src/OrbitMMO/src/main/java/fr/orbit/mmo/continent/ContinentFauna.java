package fr.orbit.mmo.continent;

import fr.orbit.mmo.OrbitMMO;
import fr.orbit.mmo.mob.MobState;
import fr.orbit.mmo.registry.Defs.MobDef;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Les monstres du continent. La faune sauvage dépend de la région (sites.yml) et son niveau monte à mesure qu'on
 * s'éloigne des lieux sûrs ; les camps et les ruines construits ont leurs propres gardiens, qui reviennent tant qu'un
 * joueur est dans les parages. Rien n'apparaît dans une zone sûre, sur une route ou devant un donjon, et un monstre qui
 * entre dans une zone sûre disparaît.
 */
public final class ContinentFauna {

    private final OrbitMMO plugin;
    private final ContinentService continent;
    private final Random random = new Random();
    /** Gardiens de chaque camp ou ruine. */
    private final Map<String, List<MobState>> guards = new HashMap<>();
    private final Map<String, Long> nextGuard = new HashMap<>();
    private final ContinentData.RoadHit hit = new ContinentData.RoadHit();

    ContinentFauna(OrbitMMO plugin, ContinentService continent) {
        this.plugin = plugin;
        this.continent = continent;
    }

    /** Niveau des monstres à cet endroit : du minimum au maximum de la région, selon la distance au lieu sûr le plus proche. */
    public int levelAt(int x, int z) {
        ContinentData data = continent.data();
        ContinentData.Region r = data.region(x, z);
        if (r == null) {
            return 1;
        }
        double nearest = Double.MAX_VALUE;
        for (ContinentData.Place p : data.places) {
            if (p.safe() > 0) {
                nearest = Math.min(nearest, Math.hypot(x - p.x(), z - p.z()) - p.safe());
            }
        }
        SiteService sites = continent.sites();
        if (sites != null) {
            for (SiteService.Built b : sites.built()) {
                SiteDesign.Site s = b.site();
                if (s.safe() > 0) {
                    nearest = Math.min(nearest, Math.hypot(x - s.x(), z - s.z()) - s.safe());
                }
            }
        }
        int span = sites == null || sites.catalog() == null ? 700 : sites.catalog().levelSpan;
        double t = Math.max(0, Math.min(1, nearest / span));
        return r.levelMin() + (int) Math.round(t * (r.levelMax() - r.levelMin()));
    }

    private int jitter(int level, ContinentData.Region r) {
        int l = level + random.nextInt(3) - 1;
        return r == null ? Math.max(1, l) : Math.max(r.levelMin(), Math.min(r.levelMax(), l));
    }

    private List<MobDef> defs(List<String> ids) {
        List<MobDef> out = new ArrayList<>();
        for (String id : ids) {
            MobDef def = plugin.registry().mob(id);
            if (def != null) {
                out.add(def);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ Apparition
    /** Appelé par MobService à chaque tic d'apparition. */
    public void spawnTick() {
        World w = continent.world();
        SiteService sites = continent.sites();
        if (w == null || sites == null || sites.catalog() == null || w.getPlayers().isEmpty()) {
            return;
        }
        SiteCatalog cat = sites.catalog();
        int inWorld = plugin.mobs().count(w);
        long now = System.currentTimeMillis();
        for (Player p : w.getPlayers()) {
            if (!plugin.mobs().eligible(p)) {
                continue;
            }
            Location l = p.getLocation();
            int px = l.getBlockX(), pz = l.getBlockZ();
            // Gardiens des camps et des ruines proches.
            for (SiteService.Built b : sites.built()) {
                SiteDesign.Site s = b.site();
                // En deçà de la distance où MobService retire un monstre sans joueur (72), sinon il le reprendrait aussitôt.
                if (s.mobs().isEmpty() || Math.hypot(s.x() - px, s.z() - pz) > 56) {
                    continue;
                }
                List<MobState> list = guards.computeIfAbsent(s.id(), k -> new ArrayList<>());
                boolean killed = false;
                for (MobState ms : list) {
                    killed |= ms.dead && !ms.despawned;
                }
                list.removeIf(ms -> ms.dead || !ms.entity.isValid());
                if (killed) {
                    // Un gardien tué n'est pas remplacé tout de suite : le camp peut être vidé.
                    nextGuard.merge(s.id(), now + cat.guardReturn * 1000L, Math::max);
                }
                int want = s.kind().equals("ruine") ? cat.perRuin : cat.perCamp;
                if (list.size() >= want || nextGuard.getOrDefault(s.id(), 0L) > now) {
                    continue;
                }
                List<MobDef> pool = defs(s.mobs());
                Location at = pool.isEmpty() ? null : spot(w, s.x(), s.z(), 2, s.radius() - 3, false);
                if (at == null) {
                    continue;
                }
                ContinentData.Region r = continent.data().region(s.x(), s.z());
                int level = jitter(levelAt(s.x(), s.z()) + 1, r);
                MobState ms = plugin.mobs().spawn(pool.get(random.nextInt(pool.size())), level, at, 0, null, 1);
                if (ms != null) {
                    list.add(ms);
                    inWorld++;
                    // Les gardiens arrivent l'un après l'autre.
                    nextGuard.put(s.id(), now + 1500);
                }
            }
            // Faune sauvage autour du joueur.
            if (continent.isSafe(l)) {
                continue;
            }
            int near = plugin.mobs().countNear(l, 36);
            for (int i = 0; i < 1 && near < cat.perPlayer && inWorld < cat.cap; i++) {
                Location at = spot(w, px, pz, 14, 30, true);
                if (at == null) {
                    continue;
                }
                ContinentData.Region r = continent.data().region(at.getBlockX(), at.getBlockZ());
                SiteCatalog.RegionRule rule = r == null ? null : cat.regions.get(r.id());
                List<MobDef> pool = rule == null ? List.of() : defs(rule.fauna());
                if (pool.isEmpty()) {
                    break;
                }
                plugin.mobs().spawn(pool.get(random.nextInt(pool.size())), jitter(levelAt(at.getBlockX(), at.getBlockZ()), r), at, 0, null, 1);
                near++;
                inWorld++;
            }
        }
    }

    /** Vrai là où la faune sauvage n'apparaît pas : zones sûres, routes, abords d'un site. */
    private boolean calm(int x, int z) {
        ContinentData data = continent.data();
        if (data.placeAt(x, z) != null || data.road(x, z, 3, hit)) {
            return true;
        }
        SiteService sites = continent.sites();
        if (sites.safeAt(x, z) != null) {
            return true;
        }
        for (SiteDesign.Site s : sites.catalog().sites.values()) {
            int margin = s.kind().equals("crateres") ? 18 : (s.kind().equals("donjon") ? 12 : 4);
            int reach = s.radius() + margin;
            if (Math.abs(s.x() - x) <= reach && Math.abs(s.z() - z) <= reach && Math.hypot(s.x() - x, s.z() - z) <= reach) {
                return true;
            }
        }
        return false;
    }

    private Location spot(World w, int cx, int cz, double from, double to, boolean wild) {
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2, dist = from + random.nextDouble() * Math.max(1, to - from);
            int x = (int) Math.floor(cx + Math.cos(angle) * dist), z = (int) Math.floor(cz + Math.sin(angle) * dist);
            if (!continent.data().inside(x, z) || !w.isChunkLoaded(x >> 4, z >> 4) || (wild && calm(x, z))) {
                continue;
            }
            int y = w.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            Block ground = w.getBlockAt(x, y, z);
            Block feet = ground.getRelative(BlockFace.UP);
            if (!ground.getType().isSolid() || ground.isLiquid() || !feet.isPassable() || feet.isLiquid() || !feet.getRelative(BlockFace.UP).isPassable()) {
                continue;
            }
            // Pas sur un toit ni au sommet d'un arbre : le sol doit être proche de celui du terrain.
            if (wild && Math.abs(y - continent.generator().standY(x, z)) > 3) {
                continue;
            }
            return new Location(w, x + 0.5, y + 1, z + 0.5, random.nextFloat() * 360f, 0f);
        }
        return null;
    }

    // ------------------------------------------------------------------ Suivi
    /**
     * Appelé par MobService pour chaque monstre du continent.
     *
     * @return true si le monstre doit disparaître (entré dans une zone sûre, ou gardien parti trop loin de son camp)
     */
    public boolean stray(MobState ms) {
        Location l = ms.entity.getLocation();
        if (continent.isSafe(l)) {
            return true;
        }
        for (Map.Entry<String, List<MobState>> e : guards.entrySet()) {
            if (e.getValue().contains(ms)) {
                SiteDesign.Site s = continent.sites().site(e.getKey());
                return s != null && Math.hypot(l.getX() - s.x(), l.getZ() - s.z()) > 48;
            }
        }
        return false;
    }
}
