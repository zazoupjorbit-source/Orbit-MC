package fr.orbit.mmo.quest;

import fr.orbit.mmo.util.Fx;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.UUID;

/**
 * Traînée de guidage : un chemin de particules au ras du sol, comme les traînées cosmétiques du lobby, que le joueur
 * est seul à voir. Elle part de ses pieds et suit un vrai chemin praticable (portes, ponts, escaliers, contournement
 * des murs) vers l'objectif de la quête qu'il suit. Le chemin est cherché dans les {@link #REACH} blocs autour du
 * joueur et refait dès qu'il avance : de loin, la traînée donne donc le bon départ, puis se précise en route.
 */
final class QuestGuide {

    /** Rayon de recherche autour du joueur, en blocs. */
    private static final int REACH = 40;
    /** Cases explorées au plus par calcul : au-delà, on garde la case la plus proche de l'objectif. */
    private static final int NODE_LIMIT = 3500;
    /** Longueur de chemin dessinée, en cases. */
    private static final int DRAWN = 28;
    /** Calculs de chemin par passage (les autres joueurs attendent le suivant, 4 ticks plus tard). */
    private static final int COMPUTES_PER_TICK = 2;
    /** En deçà, on est arrivé : les particules de l'objectif prennent le relais. */
    private static final double ARRIVED = 4.5;

    private static final byte AIR = 0, SOLID = 1, WATER = 2, BLOCKED = 3;
    private static final int NONE = Integer.MIN_VALUE;
    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    private static final class Trail {
        List<double[]> path = List.of();
        UUID world;
        double ox, oy, oz;
        double tx, ty, tz;
        long computedAt;
        int phase;
    }

    private static final class Node {
        final int x, y, z;
        final Node parent;
        final double g, h;

        Node(int x, int y, int z, Node parent, double g, double h) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.parent = parent;
            this.g = g;
            this.h = h;
        }
    }

    private final QuestService svc;
    private final Map<UUID, Trail> trails = new HashMap<>();
    private final Particle.DustOptions dust = new Particle.DustOptions(Fx.color("#FFC93C"), 1.4f);
    private final Particle.DustOptions dustFar = new Particle.DustOptions(Fx.color("#FFE79A"), 1.0f);

    QuestGuide(QuestService svc) {
        this.svc = svc;
    }

    void quit(UUID id) {
        trails.remove(id);
    }

    /** Toutes les 4 ticks. */
    void tick() {
        int budget = COMPUTES_PER_TICK;
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Location target = svc.trailTarget(p);
            Location l = p.getLocation();
            if (target == null || !target.getWorld().equals(l.getWorld()) || p.isDead()
                    || target.distanceSquared(l) < ARRIVED * ARRIVED) {
                trails.remove(p.getUniqueId());
                continue;
            }
            Trail t = trails.computeIfAbsent(p.getUniqueId(), k -> new Trail());
            boolean elsewhere = !l.getWorld().getUID().equals(t.world);
            double moved = sq(l.getX() - t.ox) + sq(l.getY() - t.oy) + sq(l.getZ() - t.oz);
            boolean retarget = sq(target.getX() - t.tx) + sq(target.getY() - t.ty) + sq(target.getZ() - t.tz) > 1;
            long age = now - t.computedAt;
            boolean due = elsewhere || retarget || (moved > 2.5 * 2.5 && age > 350) || age > (t.path.isEmpty() ? 1500 : 4000);
            if (due && budget > 0) {
                budget--;
                t.world = l.getWorld().getUID();
                t.ox = l.getX();
                t.oy = l.getY();
                t.oz = l.getZ();
                t.tx = target.getX();
                t.ty = target.getY();
                t.tz = target.getZ();
                t.computedAt = now;
                t.path = path(l, target);
                t.phase = 0;
            }
            draw(p, t, l, target);
        }
    }

    private static double sq(double v) {
        return v * v;
    }

    // ------------------------------------------------------------------ Dessin

    /**
     * Les particules sont envoyées « forcées » : elles s'affichent même avec le réglage « Particules : minimales ».
     * Flammes et étincelles sont lumineuses (visibles de nuit et dans les rues sombres), la poussière dorée donne
     * la couleur de jour : ce sont les particules des traînées cosmétiques du lobby.
     */
    private void draw(Player p, Trail t, Location l, Location target) {
        t.phase++;
        if (t.path.size() < 2) {
            // Aucun chemin trouvé (joueur en l'air, dans l'eau profonde…) : une ligne droite vers l'objectif.
            double dx = target.getX() - l.getX();
            double dz = target.getZ() - l.getZ();
            double len = Math.max(0.001, Math.sqrt(dx * dx + dz * dz));
            for (int i = 2; i <= 9; i++) {
                double x = l.getX() + dx / len * i;
                double z = l.getZ() + dz / len * i;
                if (t.phase % 2 == 0) {
                    p.spawnParticle(Particle.DUST, x, l.getY() + 0.25, z, 2, 0.08, 0.03, 0.08, 0, dust, true);
                } else if (i % 2 == 0) {
                    p.spawnParticle(Particle.FLAME, x, l.getY() + 0.25, z, 1, 0, 0, 0, 0, null, true);
                }
            }
            return;
        }
        // Les cases déjà dépassées ne sont plus dessinées : on repart de la plus proche du joueur.
        int last = t.path.size() - 1;
        int from = 0;
        double best = Double.MAX_VALUE;
        int scan = Math.min(t.path.size(), 12);
        for (int i = 0; i < scan; i++) {
            double[] a = t.path.get(i);
            double d = sq(a[0] - l.getX()) + sq(a[2] - l.getZ()) + sq(a[1] - l.getY()) * 0.25;
            if (d < best) {
                best = d;
                from = i;
            }
        }
        from = Math.min(from, last - 1);
        int to = Math.min(last, from + DRAWN);
        boolean dustPass = t.phase % 2 == 0;
        for (int i = from + 1; i <= to; i++) {
            double[] a = t.path.get(i);
            if (dustPass) {
                Particle.DustOptions o = i - from > DRAWN * 3 / 4 ? dustFar : dust;
                p.spawnParticle(Particle.DUST, a[0], a[1] + 0.2, a[2], 2, 0.08, 0.03, 0.08, 0, o, true);
                if (i < to) {
                    double[] b = t.path.get(i + 1);
                    p.spawnParticle(Particle.DUST, (a[0] + b[0]) / 2, (a[1] + b[1]) / 2 + 0.2, (a[2] + b[2]) / 2, 1, 0.06, 0.03, 0.06, 0, o, true);
                }
            } else if ((i + t.phase / 2) % 2 == 0) {
                // Une flamme une case sur deux, en alternance : le chemin scintille sans faire un mur de feu.
                p.spawnParticle(Particle.FLAME, a[0], a[1] + 0.25, a[2], 1, 0, 0, 0, 0, null, true);
            }
        }
        // Trois étincelles courent le long du chemin, de soi vers l'objectif : elles donnent le sens de marche.
        int span = to - from;
        for (int k = 0; k < 3; k++) {
            double[] head = t.path.get(from + 1 + (t.phase * 2 + k * span / 3) % span);
            p.spawnParticle(Particle.END_ROD, head[0], head[1] + 0.55, head[2], 1, 0, 0, 0, 0, null, true);
        }
    }

    /** Pour le diagnostic : nombre de cases du chemin en cours, −1 si le joueur n'a pas de traînée. */
    int pathSize(UUID id) {
        Trail t = trails.get(id);
        return t == null ? -1 : t.path.size();
    }

    // ------------------------------------------------------------------ Recherche du chemin

    /** Chemin praticable du joueur vers l'objectif (ou vers la case atteignable la plus proche de lui), en points au sol. */
    private List<double[]> path(Location from, Location target) {
        World w = from.getWorld();
        Map<Long, Byte> kinds = new HashMap<>();
        int sx = from.getBlockX();
        int sz = from.getBlockZ();
        int sy = stand(w, kinds, sx, sz, (int) Math.floor(from.getY() + 0.5) + 1, 6);
        if (sy == NONE) {
            return List.of();
        }
        int tx = target.getBlockX();
        int tz = target.getBlockZ();
        int ty = target.getBlockY();

        PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Double.compare(a.g + a.h * 1.25, b.g + b.h * 1.25));
        Map<Long, Double> seen = new HashMap<>();
        Node start = new Node(sx, sy, sz, null, 0, h(sx, sz, tx, tz));
        open.add(start);
        seen.put(key(sx, sy, sz), 0.0);
        Node bestNode = start;
        Node goal = null;
        int explored = 0;
        while (!open.isEmpty() && explored < NODE_LIMIT) {
            Node n = open.poll();
            Double known = seen.get(key(n.x, n.y, n.z));
            if (known != null && known < n.g) {
                continue;
            }
            explored++;
            if (n.h < bestNode.h) {
                bestNode = n;
            }
            if (Math.abs(n.x - tx) <= 1 && Math.abs(n.z - tz) <= 1 && Math.abs(n.y - ty) <= 4) {
                goal = n;
                break;
            }
            // Bord de la zone de recherche : le premier chemin qui l'atteint est le bon départ.
            if (Math.max(Math.abs(n.x - sx), Math.abs(n.z - sz)) >= REACH) {
                goal = n;
                break;
            }
            for (int[] d : DIRS) {
                int nx = n.x + d[0];
                int nz = n.z + d[1];
                int ny = stand(w, kinds, nx, nz, n.y + 1, 4);
                if (ny == NONE) {
                    continue;
                }
                // Monter d'une case demande de la place au-dessus de sa tête.
                if (ny > n.y && kind(w, kinds, n.x, n.y + 2, n.z) != AIR) {
                    continue;
                }
                if (d[0] != 0 && d[1] != 0) {
                    // Pas de diagonale qui rase un coin de mur ou un trou.
                    int a = stand(w, kinds, n.x + d[0], n.z, n.y + 1, 2);
                    int b = stand(w, kinds, n.x, n.z + d[1], n.y + 1, 2);
                    if (a == NONE || b == NONE) {
                        continue;
                    }
                }
                double step = (d[0] != 0 && d[1] != 0 ? 1.414 : 1.0) + Math.abs(ny - n.y) * 0.6;
                if (kind(w, kinds, nx, ny - 1, nz) == WATER || kind(w, kinds, nx, ny, nz) == WATER) {
                    step += 2.5; // on préfère le pont à la nage
                }
                double g = n.g + step;
                long k = key(nx, ny, nz);
                Double old = seen.get(k);
                if (old != null && old <= g) {
                    continue;
                }
                seen.put(k, g);
                open.add(new Node(nx, ny, nz, n, g, h(nx, nz, tx, tz)));
            }
        }
        Node end = goal != null ? goal : bestNode;
        if (end == start) {
            return List.of();
        }
        List<double[]> out = new ArrayList<>();
        for (Node n = end; n != null; n = n.parent) {
            out.add(new double[]{n.x + 0.5, n.y, n.z + 0.5});
        }
        Collections.reverse(out);
        return out;
    }

    /** Distance à vol d'oiseau, en comptant les diagonales. */
    private static double h(int x, int z, int tx, int tz) {
        int dx = Math.abs(x - tx);
        int dz = Math.abs(z - tz);
        return Math.max(dx, dz) + 0.414 * Math.min(dx, dz);
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    /**
     * Hauteur des pieds dans la colonne, en cherchant de {@code top} vers le bas sur {@code depth} cases : un sol
     * (ou la surface de l'eau) et deux cases libres au-dessus. {@link #NONE} s'il n'y en a pas.
     */
    private static int stand(World w, Map<Long, Byte> kinds, int x, int z, int top, int depth) {
        if (!w.isChunkLoaded(x >> 4, z >> 4)) {
            return NONE;
        }
        for (int y = top; y > top - depth; y--) {
            byte floor = kind(w, kinds, x, y - 1, z);
            if (floor != SOLID && floor != WATER) {
                continue;
            }
            byte feet = kind(w, kinds, x, y, z);
            if (feet == BLOCKED || feet == SOLID || (feet == WATER && floor == WATER)) {
                continue;
            }
            if (kind(w, kinds, x, y + 1, z) == AIR) {
                return y;
            }
        }
        return NONE;
    }

    private static byte kind(World w, Map<Long, Byte> kinds, int x, int y, int z) {
        long k = key(x, y, z);
        Byte known = kinds.get(k);
        if (known != null) {
            return known;
        }
        byte v;
        if (y < w.getMinHeight() || y >= w.getMaxHeight()) {
            v = AIR;
        } else {
            Block b = w.getBlockAt(x, y, z);
            Material m = b.getType();
            if (m == Material.WATER) {
                v = WATER;
            } else if (m == Material.LAVA || m == Material.FIRE || m == Material.SOUL_FIRE || m == Material.CACTUS
                    || m == Material.MAGMA_BLOCK || m == Material.CAMPFIRE || m == Material.SOUL_CAMPFIRE
                    || m == Material.SWEET_BERRY_BUSH || m == Material.POWDER_SNOW || m == Material.WITHER_ROSE
                    || Tag.FENCES.isTagged(m) || Tag.WALLS.isTagged(m)) {
                // Dangers, et barrières ou murets : ni sol ni passage.
                v = BLOCKED;
            } else if (Tag.DOORS.isTagged(m) || Tag.FENCE_GATES.isTagged(m)) {
                v = AIR; // une porte s'ouvre
            } else {
                v = b.isPassable() ? AIR : SOLID;
            }
        }
        kinds.put(k, v);
        return v;
    }
}
