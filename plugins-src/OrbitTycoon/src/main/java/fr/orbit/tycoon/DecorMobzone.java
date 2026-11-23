package fr.orbit.tycoon;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.structure.StructureRotation;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static fr.orbit.tycoon.Plans.hash;

/**
 * Décor de la Mobzone (« /tycoon mobzone deco poser|retirer ») :
 *  - dans chaque part, un décor au thème de son mob (tombes, os, toiles, obsidienne, chaudrons, braseros…)
 *    mêlé à du mobilier spatial (capsules cryo, cristaux, météorites, bornes, débris de satellite),
 *    posé au milieu des carrés formés par les spawners (jamais à moins de 2 blocs d'un spawner) ;
 *  - sur la place de la statue : lampadaires, bancs tournés vers la statue, jardinières de cristaux ;
 *  - de part et d'autre de chaque ouverture du mur : un lampadaire.
 * Ne pose QUE dans l'air, sur du sol plein ; un motif est sauté s'il toucherait un bloc existant.
 * Chaque bloc posé est noté dans deco-mobzone.txt : « retirer » enlève exactement ces blocs.
 */
final class DecorMobzone {
    private static final int Y = Zone.Y;
    private static final StructureRotation[] ROT = {StructureRotation.NONE, StructureRotation.CLOCKWISE_90,
            StructureRotation.CLOCKWISE_180, StructureRotation.COUNTERCLOCKWISE_90};

    private final OrbitTycoon plugin;
    private World w;
    private final List<String> journal = new ArrayList<>();
    private int poses, sautes;

    DecorMobzone(OrbitTycoon plugin) { this.plugin = plugin; }

    private File fichier() { return new File(plugin.getDataFolder(), "deco-mobzone.txt"); }

    // ================================================================== motifs (h = 0 : juste au-dessus du sol)

    private static Decors.Motif m() { return new Decors.Motif(); }

    /** Motifs créés au premier « poser » (pas au démarrage du plugin). */
    private static final class M {
    /** Mobilier spatial, dans toutes les parts. */
    private static final Decors.Motif[] SPATIAL = {
            m().b(0, 0, 0, "amethyst_block").b(0, 1, 0, "amethyst_cluster[facing=up]")
                    .b(1, 0, 0, "large_amethyst_bud[facing=up]").b(-1, 0, 1, "medium_amethyst_bud[facing=up]"),   // cristaux
            m().b(0, 0, 0, "polished_deepslate").b(0, 1, 0, "light_blue_stained_glass").b(0, 2, 0, "light_blue_stained_glass")
                    .b(0, 3, 0, "sea_lantern"),                                                                   // capsule cryo
            m().b(0, 0, 0, "magma_block").b(1, 0, 0, "blackstone").b(0, 0, 1, "basalt[axis=y]").b(-1, 0, 0, "gilded_blackstone")
                    .b(0, 1, 0, "blackstone").b(0, 0, -1, "cobbled_deepslate"),                                   // météorite
            m().col(0, 0, 0, 1, "polished_blackstone_wall").b(0, 2, 0, "gold_block").b(0, 3, 0, "soul_lantern"),  // borne
            m().b(0, 0, 0, "iron_block").b(-1, 0, 0, "daylight_detector").b(1, 0, 0, "daylight_detector")
                    .b(0, 1, 0, "lightning_rod[facing=up]").b(0, 0, 1, "light_blue_glazed_terracotta"),          // satellite écrasé
    };

    /** Décor au thème du mob de chaque part (même ordre que ressources.epee). */
    private static final Decors.Motif[][] THEMES = {
            {   // Zombies : tombes et caisses éventrées
                    m().b(0, 0, 0, "mossy_cobblestone").b(0, 1, 0, "mossy_cobblestone_wall").b(0, 0, 1, "coarse_dirt")
                            .b(0, 0, -1, "cobblestone_slab"),
                    m().b(0, 0, 0, "barrel[facing=up]").b(1, 0, 0, "zombie_head[rotation=4]").b(-1, 0, 0, "rooted_dirt"),
            },
            {   // Squelettes : os et arches d'os
                    m().b(0, 0, 0, "bone_block[axis=y]").b(0, 1, 0, "bone_block[axis=x]").b(1, 0, 0, "skeleton_skull[rotation=4]"),
                    m().col(-1, 0, 0, 1, "bone_block[axis=y]").col(1, 0, 0, 1, "bone_block[axis=y]").b(-1, 2, 0, "bone_block[axis=x]")
                            .b(0, 2, 0, "bone_block[axis=x]").b(1, 2, 0, "bone_block[axis=x]"),
            },
            {   // Araignées : troncs morts et toiles en hauteur (jamais au sol, pour ne pas ralentir)
                    m().col(0, 0, 0, 1, "stripped_dark_oak_log[axis=y]").b(0, 2, 0, "cobweb").b(1, 0, 0, "brown_mushroom"),
                    m().b(0, 0, 0, "mushroom_stem").b(-1, 0, 0, "white_terracotta").b(0, 1, 0, "cobweb"),
            },
            {   // Creepers : mousse, TNT inerte, têtes
                    m().b(0, 0, 0, "moss_block").b(0, 1, 0, "creeper_head[rotation=4]").b(1, 0, 0, "tnt").b(-1, 0, 0, "lime_glazed_terracotta"),
                    m().b(0, 0, 0, "tnt").b(1, 0, 0, "tnt").b(0, 1, 0, "tnt").b(0, 0, 1, "moss_carpet"),
            },
            {   // Endermen : obélisques d'obsidienne
                    m().col(0, 0, 0, 2, "obsidian").b(0, 3, 0, "end_rod[facing=up]").b(1, 0, 0, "end_stone_bricks"),
                    m().b(0, 0, 0, "end_stone").b(1, 0, 0, "end_stone_brick_slab").b(0, 1, 0, "crying_obsidian"),
            },
            {   // Sorcières : chaudrons et alambics
                    m().b(0, 0, 0, "cauldron").b(1, 0, 0, "brewing_stand").b(-1, 0, 0, "potted_red_mushroom"),
                    m().b(0, 0, 0, "spruce_planks").b(0, 1, 0, "potted_brown_mushroom").b(1, 0, 0, "cauldron"),
            },
            {   // Blazes : braseros du Nether
                    m().b(0, 0, 0, "nether_bricks").b(0, 1, 0, "nether_brick_fence").b(0, 2, 0, "magma_block").b(1, 0, 0, "magma_block"),
                    m().b(0, 0, 0, "soul_campfire").b(1, 0, 0, "nether_bricks").b(-1, 0, 0, "nether_bricks"),
            },
            {   // Wither squelettes : sable des âmes, crânes, lanternes bleues
                    m().b(0, 0, 0, "soul_soil").b(0, 1, 0, "wither_skeleton_skull[rotation=4]").b(1, 0, 0, "soul_lantern"),
                    m().b(0, 0, 0, "nether_brick_fence").b(0, 1, 0, "nether_brick_fence").b(0, 2, 0, "soul_lantern").b(1, 0, 0, "soul_soil"),
            },
            {   // Évocateurs : totems de chêne noir et d'émeraude
                    m().col(0, 0, 0, 1, "dark_oak_log[axis=y]").b(0, 2, 0, "emerald_block").b(1, 0, 0, "dark_oak_fence"),
                    m().b(0, 0, 0, "dark_oak_planks").b(0, 1, 0, "lantern").b(-1, 0, 0, "dark_oak_slab"),
            },
            {   // Ravageurs : enclos de foin
                    m().b(0, 0, 0, "hay_block").b(0, 1, 0, "hay_block").b(1, 0, 0, "iron_bars").b(-1, 0, 0, "iron_bars"),
                    m().b(0, 0, 0, "hay_block").b(1, 0, 0, "hay_block").b(0, 0, 1, "hay_block").b(0, 1, 0, "lantern"),
            },
            {   // Shulkers (gardiens du vide) : piliers de purpur
                    m().col(0, 0, 0, 2, "purpur_pillar[axis=y]").b(0, 3, 0, "end_rod[facing=up]").b(1, 0, 0, "purple_shulker_box[facing=up]"),
                    m().b(0, 0, 0, "purpur_block").b(0, 1, 0, "purpur_slab").b(-1, 0, 0, "end_stone_bricks"),
            },
    };

    private static final Decors.Motif LAMPADAIRE = m().col(0, 0, 0, 3, "polished_blackstone_wall").b(0, 4, 0, "gold_block")
            .b(0, 5, 0, "soul_lantern");
    private static final Decors.Motif BANC = m().b(-1, 0, 0, "dark_oak_slab").b(0, 0, 0, "dark_oak_slab").b(1, 0, 0, "dark_oak_slab")
            .b(-2, 0, 0, "polished_blackstone_wall").b(2, 0, 0, "polished_blackstone_wall");
    private static final Decors.Motif JARDINIERE = m().b(0, 0, 0, "gold_block").b(1, 0, 0, "light_blue_concrete")
            .b(-1, 0, 0, "light_blue_concrete").b(0, 1, 0, "amethyst_cluster[facing=up]");
    }

    // ================================================================== pose

    /** Avant du motif (-z) tourné vers (dx, dz), en quarts de tour. */
    private static int quarts(double dx, double dz) {
        if (Math.abs(dx) > Math.abs(dz)) return dx > 0 ? 1 : 3;
        return dz > 0 ? 2 : 0;
    }

    private static long cle(int x, int y, int z) {
        return ((long) (x + 4_000_000) << 30) ^ ((long) (y + 512) << 20) ^ (z + 4_000_000L);
    }

    /** Un spawner à moins de 2 blocs (horizontalement) de cette colonne ? */
    private boolean presSpawner(int x, int z) {
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++)
                if (w.getBlockAt(x + dx, Y, z + dz).getType() == Material.SPAWNER) return true;
        return false;
    }

    private boolean poser(int ox, int oz, int k, Decors.Motif m) {
        List<int[]> monde = new ArrayList<>();
        Set<Long> occupe = new HashSet<>();
        for (int[] p : m.pos) {
            int x = p[0], z = p[2];
            for (int i = 0; i < k; i++) { int t = x; x = -z; z = t; }
            int[] q = {ox + x, Y + 1 + p[1], oz + z};
            monde.add(q);
            occupe.add(cle(q[0], q[1], q[2]));
        }
        for (int[] q : monde) {
            if (!w.getBlockAt(q[0], q[1], q[2]).getType().isAir()) return saute();
            if (q[1] == Y + 1) {
                Material sol = w.getBlockAt(q[0], Y, q[2]).getType();
                if (!sol.isSolid() || sol == Material.SPAWNER || sol == Material.GOLD_BLOCK) return saute();
                if (presSpawner(q[0], q[2])) return saute();
            }
            if (q[1] <= Y + 3)
                for (int[] v : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int nx = q[0] + v[0], nz = q[2] + v[1];
                    if (!occupe.contains(cle(nx, q[1], nz)) && !w.getBlockAt(nx, q[1], nz).getType().isAir()) return saute();
                }
        }
        for (int i = 0; i < monde.size(); i++) {
            int[] q = monde.get(i);
            BlockData d = m.data.get(i).clone();
            if (k != 0) d.rotate(ROT[k]);
            w.getBlockAt(q[0], q[1], q[2]).setBlockData(d, false);
            journal.add(q[0] + " " + q[1] + " " + q[2] + " " + d.getMaterial().name());
        }
        poses++;
        return true;
    }

    private boolean saute() { sautes++; return false; }

    // ================================================================== plan

    /** Au milieu des carrés de spawners (tous les 7 blocs), environ 1 carré sur 4. */
    private void parts(Zone z) {
        int R = z.rayon, n = Math.max(1, plugin.ressources().liste(Outil.Mode.EPEE).size());
        int e = Iles.ECART_SPAWNERS, debut = -(R / e) * e - e;
        var arrivee = z.arrivee(w);
        for (int gx = debut; gx <= R; gx += e)
            for (int gz = debut; gz <= R; gz += e) {
                int dx = gx + e / 2, dz = gz + e / 2;               // milieu du carré (à 3 blocs des spawners)
                double d = Math.sqrt((double) dx * dx + (double) dz * dz);
                if (d < Iles.PLACE + 5 || d > R - 10) continue;
                if (Iles.distanceLimite(z, dx, dz, n) < 3.5) continue;
                int x = z.cx + dx, zz = z.cz + dz;
                if (Math.hypot(x - arrivee.getX(), zz - arrivee.getZ()) < 22) continue;
                int h = hash(x, 31, zz);
                if (h >= 260) continue;                            // ~1 carré sur 4
                int part = Iles.partMobzone(z, x, zz, n);
                Decors.Motif[] theme = part >= 0 && part < M.THEMES.length ? M.THEMES[part] : M.SPATIAL;
                Decors.Motif mo = h < 165 ? theme[h % theme.length] : M.SPATIAL[h % M.SPATIAL.length];
                poser(x, zz, h % 4, mo);
            }
    }

    /** Place de la statue : lampadaires contre le mur, bancs et jardinières tournés vers la statue ; lampadaires aux ouvertures. */
    private void place(Zone z) {
        int n = Math.max(1, plugin.ressources().liste(Outil.Mode.EPEE).size());
        for (int k = 0; k < n; k++) {                              // entre deux ouvertures : lampadaire contre le mur
            double a = Iles.angleParts(z) + (k + 0.5) * 2 * Math.PI / n;
            int x = z.cx + (int) Math.round(Math.cos(a) * 46), zz = z.cz + (int) Math.round(Math.sin(a) * 46);
            poser(x, zz, 0, M.LAMPADAIRE);
        }
        for (int k = 0; k < n; k++) {
            double a = Iles.angleParts(z) + k * 2 * Math.PI / n;
            double ca = Math.cos(a), sa = Math.sin(a), tx = -sa, tz = ca;
            // Lampadaires de chaque côté de l'ouverture, côté part.
            for (int s : new int[]{-1, 1})
                poser(z.cx + (int) Math.round(ca * 56 + tx * 5 * s), z.cz + (int) Math.round(sa * 56 + tz * 5 * s), 0, M.LAMPADAIRE);
            // Banc face à la statue, dans l'axe de l'ouverture ; jardinières de part et d'autre.
            double b = a + Math.PI / n;                            // entre deux allées
            int bx = z.cx + (int) Math.round(Math.cos(b) * 31), bz = z.cz + (int) Math.round(Math.sin(b) * 31);
            poser(bx, bz, quarts(-Math.cos(b), -Math.sin(b)), M.BANC);
            int jx = z.cx + (int) Math.round(Math.cos(b) * 39), jz = z.cz + (int) Math.round(Math.sin(b) * 39);
            poser(jx, jz, quarts(-Math.cos(b), -Math.sin(b)), M.JARDINIERE);
        }
    }

    // ================================================================== commandes

    /** /tycoon mobzone deco poser : renvoie un résumé. */
    String poser() {
        w = plugin.monde();
        journal.clear();
        poses = sautes = 0;
        Zone z = Zone.MOBZONE;
        place(z);
        parts(z);
        try {
            File f = fichier();
            f.getParentFile().mkdirs();
            Files.write(f.toPath(), journal, StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException e) {
            plugin.getLogger().warning("deco-mobzone.txt : " + e);
        }
        return poses + " décors posés (" + journal.size() + " blocs), " + sautes + " sautés (place déjà prise ou spawner trop près).";
    }

    /** /tycoon mobzone deco retirer : enlève les blocs notés dans deco-mobzone.txt s'ils sont toujours là. */
    String retirer() {
        w = plugin.monde();
        File f = fichier();
        if (!f.exists()) return "Aucun décor noté (deco-mobzone.txt absent).";
        int n = 0;
        try {
            for (String l : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
                String[] p = l.trim().split(" ");
                if (p.length != 4) continue;
                Block b = w.getBlockAt(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
                if (b.getType().name().equals(p[3])) { b.setType(Material.AIR, false); n++; }
            }
            Files.delete(f.toPath());
        } catch (IOException | NumberFormatException e) {
            return "Erreur en lisant deco-mobzone.txt : " + e.getMessage();
        }
        return n + " blocs de décor retirés.";
    }
}
