package fr.orbit.breche;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Une carte posée dans le monde de BRÈCHE : construction par le code, barrières d'achat, cases bloquées (murs de cristal). */
final class Carte {

    final OrbitBreche pl;
    final Plan plan;
    final World w;
    final boolean[][] bloque;
    private final List<TextDisplay> lettres = new ArrayList<>();
    private final Map<String, BlockData> cache = new HashMap<>();

    Carte(OrbitBreche pl, Plan plan, World w) {
        this.pl = pl;
        this.plan = plan;
        this.w = w;
        this.bloque = new boolean[plan.h][plan.w];
    }

    private BlockData data(String s) {
        return cache.computeIfAbsent(s, Bukkit::createBlockData);
    }

    private void set(int x, int y, int z, String s) {
        w.getBlockAt(plan.x0 + x, y, plan.z0 + z).setBlockData(data(s), false);
    }

    /** Empreinte du plan : la carte est reconstruite au démarrage dès qu'elle change. */
    private String empreinte() {
        StringBuilder sb = new StringBuilder(plan.theme.toString());
        for (char[] row : plan.g) {
            sb.append(row);
        }
        return Integer.toHexString(sb.toString().hashCode()) + "-v1";
    }

    /** Construit la carte si elle n'existe pas encore (ou si son plan a changé), ou toujours si {@code forcer}. */
    boolean construire(boolean forcer) {
        File marque = new File(pl.getDataFolder(), "construit-" + plan.id + ".txt");
        String emp = empreinte();
        try {
            if (!forcer && marque.isFile() && Files.readString(marque.toPath(), StandardCharsets.UTF_8).trim().equals(emp)) {
                return false;
            }
        } catch (Exception ignored) {
        }
        long t0 = System.currentTimeMillis();
        for (int cx = (plan.x0 - 4) >> 4; cx <= (plan.x0 + plan.w + 4) >> 4; cx++) {
            for (int cz = (plan.z0 - 4) >> 4; cz <= (plan.z0 + plan.h + 4) >> 4; cz++) {
                w.getChunkAt(cx, cz);
            }
        }
        Plan.Theme t = plan.theme;
        Random rnd = new Random(plan.id.hashCode());
        int n = 0;
        for (int z = 0; z < plan.h; z++) {
            for (int x = 0; x < plan.w; x++) {
                char c = plan.g[z][x];
                set(x, Plan.SOL - 2, z, t.bord());
                set(x, Plan.SOL - 1, z, t.sol2());
                String sol = switch (c) {
                    case ',', '=' -> t.sol2();
                    case 'a' -> bordSite(x, z, 'a') ? t.bord() : t.siteA();
                    case 'b' -> bordSite(x, z, 'b') ? t.bord() : t.siteB();
                    case 't' -> t.baseT();
                    case 'd' -> t.baseD();
                    case '#', 'X' -> t.mur();
                    default -> (x % 6 == 3 && z % 6 == 3 && c == '.') ? t.lumiere() : t.sol();
                };
                set(x, Plan.SOL, z, sol);
                for (int y = Plan.Y; y < Plan.Y + Plan.MUR + 4; y++) {
                    String b = "minecraft:air";
                    int k = y - Plan.Y;
                    if (c == '#') {
                        if (k < Plan.MUR) {
                            b = k == Plan.MUR - 1 ? t.murHaut() : (k == 2 && lumiereMur(x, z)) ? t.lumiere() : t.mur();
                        }
                    } else if (c == 'X') {
                        if (k < Plan.MUR) {
                            b = k == Plan.MUR - 1 ? t.murHaut() : t.pilier();
                        }
                    } else if (c == 'c') {
                        if (k == 0) {
                            b = t.caisse();
                        }
                    } else if (c == 'C') {
                        if (k == 0) {
                            b = t.caisse();
                        } else if (k == 1) {
                            b = rnd.nextInt(3) == 0 ? t.caisse() : t.caisseHaut();
                        }
                    }
                    set(x, y, z, b);
                    n++;
                }
            }
        }
        barrieres(false);
        poserLettres();
        try {
            pl.getDataFolder().mkdirs();
            Files.writeString(marque.toPath(), emp, StandardCharsets.UTF_8);
        } catch (Exception e) {
            pl.getLogger().warning("Marque de construction non écrite : " + e.getMessage());
        }
        pl.getLogger().info("Carte « " + plan.nom + " » construite (" + n + " blocs, " + (System.currentTimeMillis() - t0) + " ms).");
        return true;
    }

    private boolean bordSite(int x, int z, char s) {
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            char o = plan.at(x + d[0], z + d[1]);
            if (o != s && Plan.marchable(o)) {
                return true;
            }
        }
        return false;
    }

    private boolean lumiereMur(int x, int z) {
        if ((x + z) % 5 != 0) {
            return false;
        }
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            if (Plan.marchable(plan.at(x + d[0], z + d[1]))) {
                return true;
            }
        }
        return false;
    }

    /** Vitres des sorties de base pendant la phase d'achat. */
    void barrieres(boolean on) {
        for (int z = 0; z < plan.h; z++) {
            for (int x = 0; x < plan.w; x++) {
                if (plan.g[z][x] == '=') {
                    for (int y = Plan.Y; y < Plan.Y + Plan.MUR; y++) {
                        set(x, y, z, on ? plan.theme.vitre() : "minecraft:air");
                    }
                }
            }
        }
    }

    /** Grandes lettres A et B au-dessus des sites. */
    void poserLettres() {
        for (TextDisplay t : lettres) {
            t.remove();
        }
        lettres.clear();
        for (char s : new char[]{'a', 'b'}) {
            List<int[]> cells = plan.site(s);
            if (cells.isEmpty()) {
                continue;
            }
            double sx = 0, sz = 0;
            for (int[] c : cells) {
                sx += c[0];
                sz += c[1];
            }
            Location at = new Location(w, plan.wx((int) Math.round(sx / cells.size())), Plan.Y + 6.5, plan.wz((int) Math.round(sz / cells.size())));
            String col = s == 'a' ? "#62CFF4" : "#E0B864";
            lettres.add(w.spawn(at, TextDisplay.class, e -> {
                pl.mark(e);
                e.text(Txt.mm("<" + col + "><b>" + Character.toUpperCase(s) + "</b>"));
                e.setBillboard(Display.Billboard.CENTER);
                e.setShadowed(true);
                e.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                e.setBrightness(new Display.Brightness(15, 15));
                e.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(7f, 7f, 7f), new AxisAngle4f()));
            }));
        }
    }

    void retirerLettres() {
        for (TextDisplay t : lettres) {
            t.remove();
        }
        lettres.clear();
    }

    void debloquer() {
        for (boolean[] row : bloque) {
            Arrays.fill(row, false);
        }
    }

    /** Point d'apparition n° i d'une base ('t' ou 'd'), regard vers le centre de la carte. */
    Location base(char cote, int i, Random rnd) {
        List<int[]> cells = cote == 't' ? plan.baseT : plan.baseD;
        int[] c = cells.get(Math.floorMod(i * 7 + rnd.nextInt(3), cells.size()));
        double x = plan.wx(c[0]), z = plan.wz(c[1]);
        float yaw = (float) Math.toDegrees(Math.atan2(-(plan.cx() - x), plan.cz() - z));
        return new Location(w, x, Plan.Y, z, yaw, 0f);
    }

    /** Garde les tronçons de la carte chargés sans joueur (partie d'essai entre bots). */
    void garderCharge(boolean on) {
        for (int cx = (plan.x0 - 2) >> 4; cx <= (plan.x0 + plan.w + 2) >> 4; cx++) {
            for (int cz = (plan.z0 - 2) >> 4; cz <= (plan.z0 + plan.h + 2) >> 4; cz++) {
                if (on) {
                    w.addPluginChunkTicket(cx, cz, pl);
                } else {
                    w.removePluginChunkTicket(cx, cz, pl);
                }
            }
        }
    }
}
