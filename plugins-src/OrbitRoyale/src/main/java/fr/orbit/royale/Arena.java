package fr.orbit.royale;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Arène du Siège Orbital, générée par code dans le monde vide « royale » (une par place, x = 160 × place).
 * Repère de l'arène : la case (i, j) est le bloc (OX + i, Y, OZ + j) et son centre est en (i, j) ;
 * x de -9 à 9 (19 de large), z de -18 à 18 (37 de long). Faille du Néant en z = -1..1, ponts en x = ±6 (3 de large).
 * Camp 0 (bleu) en bas (z négatif), camp 1 (rouge) en haut. Les joueurs marchent sur un plafond de barrières
 * invisible à 9 blocs au-dessus du sol.
 */
final class Arena {

    static final int Y = 100, STEP = 160;
    static final int HX = 9, HZ = 18;
    static final double BRIDGE_X = 6, RIVER = 1.5;
    // plafond de barrières invisible : on marche à CAM_H blocs au-dessus du sol (retour de Zazou : la vue de loin était illisible)
    static final int CAM_H = 9, CAM_Z = 14, CLEAR_Z = 27, CLEAR_H = 26;

    final World world;
    final int slot, ox, oz;

    Arena(World world, int slot) {
        this.world = world;
        this.slot = slot;
        this.ox = slot * STEP;
        this.oz = 0;
    }

    /** Point du monde au-dessus de la case (x, z) de l'arène, à h blocs du dessus du sol. */
    Location at(double x, double h, double z) {
        return new Location(world, ox + x + 0.5, Y + 1 + h, oz + z + 0.5);
    }

    double localX(double wx) {
        return wx - ox - 0.5;
    }

    double localZ(double wz) {
        return wz - oz - 0.5;
    }

    /** Point de départ sur le plafond invisible, au-dessus de son camp, tourné vers l'adversaire. */
    Location camera(int side) {
        int s = side == 0 ? -1 : 1;
        Location l = at(0, CAM_H, s * CAM_Z);
        l.setYaw(side == 0 ? 0f : 180f);
        l.setPitch(48f);
        return l;
    }

    static double towerX(int lane) {
        return lane * BRIDGE_X;
    }

    static double sideTowerZ(int side) {
        return (side == 0 ? -1 : 1) * 11;
    }

    static double mainTowerZ(int side) {
        return (side == 0 ? -1 : 1) * 15;
    }

    boolean built() {
        return world.getBlockAt(ox, Y, oz + 10).getType() != Material.AIR && world.getBlockAt(ox, Y + CAM_H, oz).getType() == Material.BARRIER;
    }

    void loadChunks(org.bukkit.plugin.Plugin plugin, boolean add) {
        for (int cx = Math.floorDiv(ox - 12, 16); cx <= Math.floorDiv(ox + 12, 16); cx++) {
            for (int cz = Math.floorDiv(oz - CLEAR_Z, 16); cz <= Math.floorDiv(oz + CLEAR_Z, 16); cz++) {
                if (add) {
                    world.getChunkAt(cx, cz).addPluginChunkTicket(plugin);
                } else {
                    world.removePluginChunkTicket(cx, cz, plugin);
                }
            }
        }
    }

    private void set(int i, int y, int j, Material m) {
        Block b = world.getBlockAt(ox + i, y, oz + j);
        if (b.getType() != m) {
            b.setType(m, false);
        }
    }

    /** Construit (ou répare) toute l'arène : sol, faille, ponts, bordure, plafond invisible et les 6 tours. */
    int build() {
        int n = 0;
        // on vide la zone
        for (int i = -HX - 3; i <= HX + 3; i++) {
            for (int j = -CLEAR_Z; j <= CLEAR_Z; j++) {
                for (int y = Y - 4; y <= Y + CLEAR_H; y++) {
                    if (world.getBlockAt(ox + i, y, oz + j).getType() != Material.AIR) {
                        world.getBlockAt(ox + i, y, oz + j).setType(Material.AIR, false);
                    }
                }
            }
        }
        for (int i = -HX; i <= HX; i++) {
            for (int j = -HZ; j <= HZ; j++) {
                boolean river = Math.abs(j) <= 1;
                boolean bridge = river && Math.abs(Math.abs(i) - BRIDGE_X) <= 1;
                Material m;
                if (river && !bridge) {
                    set(i, Y - 3, j, (i + j) % 3 == 0 ? Material.CRYING_OBSIDIAN : Material.OBSIDIAN);
                    n++;
                    continue;
                } else if (bridge) {
                    m = Math.abs(i) == BRIDGE_X ? Material.WAXED_OXIDIZED_CUT_COPPER : Material.WAXED_CUT_COPPER;
                } else if (j < 0) {
                    m = j == -2 ? Material.LIGHT_BLUE_CONCRETE
                            : ((Math.floorDiv(i, 2) + Math.floorDiv(j, 2)) & 1) == 0 ? Material.POLISHED_DEEPSLATE : Material.DEEPSLATE_TILES;
                } else {
                    m = j == 2 ? Material.RED_CONCRETE
                            : ((Math.floorDiv(i, 2) + Math.floorDiv(j, 2)) & 1) == 0 ? Material.POLISHED_BLACKSTONE : Material.POLISHED_BLACKSTONE_BRICKS;
                }
                set(i, Y, j, m);
                n++;
            }
        }
        // bordure lumineuse
        for (int i = -HX - 1; i <= HX + 1; i++) {
            for (int j = -HZ - 1; j <= HZ + 1; j++) {
                if (Math.abs(i) <= HX && Math.abs(j) <= HZ) {
                    continue;
                }
                if (Math.abs(j) <= 1) {
                    continue;
                }
                boolean lamp = (i + j) % 4 == 0;
                set(i, Y, j, lamp ? (j < 0 ? Material.SEA_LANTERN : Material.SHROOMLIGHT) : Material.SMOOTH_QUARTZ);
                set(i, Y + 1, j, j < 0 ? Material.LIGHT_BLUE_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE);
                n += 2;
            }
        }
        // plafond de barrières (invisible) sur toute l'arène + rebord de 3 blocs : on marche au-dessus du terrain
        for (int i = -HX - 2; i <= HX + 2; i++) {
            for (int j = -HZ - 2; j <= HZ + 2; j++) {
                set(i, Y + CAM_H, j, Material.BARRIER);
                n++;
                if (Math.abs(i) == HX + 2 || Math.abs(j) == HZ + 2) {
                    for (int h = 1; h <= 3; h++) {
                        set(i, Y + CAM_H + h, j, Material.BARRIER);
                        n++;
                    }
                }
            }
        }
        n += buildTowers();
        return n;
    }

    int buildTowers() {
        int n = 0;
        for (int side = 0; side < 2; side++) {
            n += tower(side, 0, (int) mainTowerZ(side), true);
            n += tower(side, -6, (int) sideTowerZ(side), false);
            n += tower(side, 6, (int) sideTowerZ(side), false);
        }
        return n;
    }

    private int tower(int side, int cx, int cz, boolean main) {
        int half = main ? 2 : 1, height = main ? 5 : 4;
        Material body = side == 0 ? Material.QUARTZ_BRICKS : Material.POLISHED_BLACKSTONE_BRICKS;
        Material pillar = side == 0 ? Material.LAPIS_BLOCK : Material.RED_NETHER_BRICKS;
        Material light = side == 0 ? Material.SEA_LANTERN : Material.SHROOMLIGHT;
        int n = 0;
        for (int i = -half; i <= half; i++) {
            for (int j = -half; j <= half; j++) {
                boolean corner = Math.abs(i) == half && Math.abs(j) == half;
                for (int h = 1; h <= height + 1; h++) {
                    Material m;
                    if (h <= height) {
                        m = corner ? pillar : (h == height && i == 0 && j == 0 && !main) ? light : body;
                        if (main && h == height && Math.abs(i) <= 1 && Math.abs(j) <= 1) {
                            m = Material.IRON_BLOCK; // socle de la balise
                        }
                    } else {
                        m = corner ? light : Material.AIR;
                        if (main && i == 0 && j == 0) {
                            m = Material.BEACON;
                        }
                    }
                    set(cx + i, Y + h, cz + j, m);
                    n++;
                }
                if (main && i == 0 && j == 0) {
                    set(cx, Y + height + 2, cz, side == 0 ? Material.LIGHT_BLUE_STAINED_GLASS : Material.RED_STAINED_GLASS);
                }
            }
        }
        return n;
    }

    /** Tour détruite : un tas de gravats fumants à la place. */
    void rubble(Body t) {
        int half = t.main ? 2 : 1, height = t.main ? 7 : 5;
        int cx = (int) Math.round(t.x), cz = (int) Math.round(t.z);
        for (int i = -half; i <= half; i++) {
            for (int j = -half; j <= half; j++) {
                for (int h = 1; h <= height + 1; h++) {
                    Material m = Material.AIR;
                    if (h == 1) {
                        m = (i * 7 + j * 3) % 4 == 0 ? Material.MAGMA_BLOCK : Material.COBBLED_DEEPSLATE;
                    } else if (h == 2 && (i + j) % 2 == 0) {
                        m = Material.BLACKSTONE;
                    }
                    set(cx + i, Y + h, cz + j, m);
                }
            }
        }
    }
}
