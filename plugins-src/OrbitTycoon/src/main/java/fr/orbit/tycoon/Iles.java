package fr.orbit.tycoon;

import org.bukkit.Material;
import org.bukkit.TreeType;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.type.Farmland;

import java.util.Random;

import static fr.orbit.tycoon.Plans.Y;
import static fr.orbit.tycoon.Plans.bruit;
import static fr.orbit.tycoon.Plans.hash;

/** Les 5 îles de farm (≈ 440 blocs de large), toujours dans la DA Orbit : barrière vitrée, or, lumière bleue. */
final class Iles {
    private final Plans p;

    Iles(Plans p) { this.p = p; }

    void dessiner(Chantier c, Zone z) {
        switch (z) {
            case PECHE -> peche(c, z);
            case BUCHERON -> bucheron(c, z);
            case AGRICULTURE -> agriculture(c, z);
            case MOBZONE -> mobzone(c, z);
            case MINE -> mine(c, z);
            default -> { }
        }
        arrivee(c, z);
    }

    // ------------------------------------------------------------------ commun

    /** Angle (radians) de la direction île -> spawn. */
    private static double versSpawn(Zone z) { return Math.atan2(-z.uz(), -z.ux()); }

    /** Écart angulaire (degrés, 0..180) entre deux angles en radians. */
    private static double ecart(double a, double b) {
        double d = Math.toDegrees(a - b) % 360;
        if (d < 0) d += 360;
        return d > 180 ? 360 - d : d;
    }

    /** Vrai si (dx, dz) est près du couloir arrivée/portail (à ne pas encombrer). */
    private static boolean couloir(Zone z, int dx, int dz, double marge) {
        double a = versSpawn(z), ux = Math.cos(a), uz = Math.sin(a);
        double along = dx * ux + dz * uz, perp = Math.abs(-dx * uz + dz * ux);
        return along > z.rayon - 60 && perp < marge;
    }

    private void arrivee(Chantier c, Zone z) {
        double[] pr = z.portailRetour();
        int ox = (int) Math.floor(pr[0]), oz = (int) Math.floor(pr[2]);
        p.disque(c, ox, oz, 9, (dx, dz, d) -> {
            Material m = d > 8.2 ? Material.GOLD_BLOCK : d > 7.2 ? Material.LIGHT_BLUE_CONCRETE : Material.POLISHED_DEEPSLATE;
            c.set(ox + dx, Y, oz + dz, m);
            for (int y = Y + 1; y <= Y + 14; y++) c.vider(ox + dx, y, oz + dz);
        });
        p.anneauPortail(c, pr, z.id, "<gradient:#62CFF4:#E0B864><bold>Base Orbit</bold></gradient>\n<gray>Retour au spawn");

        var a = z.arrivee(c.world());
        int ax = a.getBlockX(), az = a.getBlockZ();
        p.disque(c, ax, az, 4, (dx, dz, d) -> {
            Material m = d > 3.3 ? Material.GOLD_BLOCK : d < 1 ? Material.SEA_LANTERN : Material.LIGHT_BLUE_CONCRETE;
            c.set(ax + dx, Y, az + dz, m);
            for (int y = Y + 1; y <= Y + 4; y++) c.vider(ax + dx, y, az + dz);
        });
        StringBuilder t = new StringBuilder(z.nom);
        for (String l : z.description) t.append("\n").append(l);
        double ux = z.ux(), uz = z.uz();
        p.holo(c, z.id, ax + 0.5 + ux * 6, Y + 4.5, az + 0.5 + uz * 6, t.toString(), 1.8f);
    }

    // ------------------------------------------------------------------ Pêche : le lagon orbital

    private void peche(Chantier c, Zone z) {
        int R = z.rayon;
        p.disque(c, z.cx, z.cz, R, (dx, dz, d) -> {
            int x = z.cx + dx, zz = z.cz + dz;
            double ang = Math.atan2(dz, dx);
            int haut;
            if (d >= R - 1) {
                p.barriere(c, x, zz, ang, R);
                for (int k = 1; k <= 3; k++) c.set(x, Y - k, zz, Material.DIRT);
                haut = 3;
            } else if (d <= 120) {
                int fond = Math.max(5, Math.min(9, 7 + (int) Math.round(bruit(x, zz) * 0.5)));
                if (d > 110) fond = Math.min(fond, 3 + (int) ((120 - d) * 0.6));
                int h = hash(x, 1, zz);
                c.set(x, Y - fond, zz, h < 25 ? Material.SEA_LANTERN : h < 200 ? Material.GRAVEL : h < 260 ? Material.CLAY : Material.SAND);
                for (int y = Y - fond + 1; y <= Y; y++)
                    c.set(x, y, zz, (y == Y - fond + 1 && h > 900) ? Material.SEAGRASS : Material.WATER);
                for (int k = fond + 1; k <= fond + 2; k++) c.set(x, Y - k, zz, Material.SANDSTONE);
                haut = fond + 2;
            } else if (d <= 136) {
                c.set(x, Y, zz, Material.SAND);
                for (int k = 1; k <= 11; k++) c.set(x, Y - k, zz, Material.SANDSTONE);
                haut = 11;
            } else {
                c.set(x, Y, zz, Material.GRASS_BLOCK);
                for (int k = 1; k <= 3; k++) c.set(x, Y - k, zz, Material.DIRT);
                int h = hash(x, 2, zz);
                if (h < 50) c.set(x, Y + 1, zz, Material.SHORT_GRASS);
                else if (h < 58) c.set(x, Y + 1, zz, h % 2 == 0 ? Material.CORNFLOWER : Material.BLUE_ORCHID);
                haut = 3;
            }
            p.dessous(c, x, zz, d, R, haut, 62, Material.STONE, Material.DIORITE);
        });

        // Station orbitale sur pylônes au centre du lac, avec son télescope, et passerelle depuis l'arrivée.
        stationTelescope(c, z);
        passerelle(c, z);

        // Îlots satellites avec balises.
        Random rnd = new Random(11);
        for (int i = 0; i < 6; i++) {
            double a = Math.toRadians(i * 60 + 30);
            int x = z.cx + (int) Math.round(Math.cos(a) * 72), zz = z.cz + (int) Math.round(Math.sin(a) * 72);
            ilot(c, x, zz, 9, 4);
            balise(c, x + 3, Y, zz, 8);
            p.arbre(c, x - 2, Y, zz - 1, TreeType.TREE, rnd.nextLong(), Material.GRASS_BLOCK);
        }

        // Pontons.
        double aSpawn = versSpawn(z);
        for (int i = 0; i < 12; i++) {
            double a = Math.toRadians(i * 30 + 15);
            if (ecart(a, aSpawn) < 25) continue;
            ponton(c, z, a);
        }

        // Bouées lumineuses.
        c.etape(() -> {
            Random r = new Random(12);
            for (int i = 0; i < 60; i++) {
                double a = r.nextDouble() * Math.PI * 2, d = 28 + r.nextDouble() * 85;
                int x = z.cx + (int) Math.round(Math.cos(a) * d), zz = z.cz + (int) Math.round(Math.sin(a) * d);
                if (c.world().getBlockAt(x, Y, zz).getType() != Material.WATER) continue;
                c.set(x, Y, zz, Material.SEA_LANTERN);
                c.set(x, Y + 1, zz, i % 3 == 0 ? Material.GOLD_BLOCK : Material.LIGHT_BLUE_STAINED_GLASS);
            }
        });

        // Arbres et lampadaires sur la bande d'herbe.
        Random rt = new Random(13);
        TreeType[] types = {TreeType.TREE, TreeType.BIRCH, TreeType.TREE, TreeType.CHERRY};
        for (int r : new int[]{152, 174, 198}) {
            int n = (int) (2 * Math.PI * r / 15);
            for (int i = 0; i < n; i++) {
                double a = 2 * Math.PI * i / n + rt.nextDouble() * 0.04;
                if (ecart(a, aSpawn) < 9) continue;
                int dx = (int) Math.round(Math.cos(a) * (r + rt.nextInt(5) - 2)), dz = (int) Math.round(Math.sin(a) * (r + rt.nextInt(5) - 2));
                p.arbre(c, z.cx + dx, Y, z.cz + dz, types[rt.nextInt(types.length)], rt.nextLong(), Material.GRASS_BLOCK);
            }
        }
        c.etape(() -> {
            int n = (int) (2 * Math.PI * 140 / 18);
            for (int i = 0; i < n; i++) {
                double a = 2 * Math.PI * i / n;
                p.lampadaire(c, z.cx + (int) Math.round(Math.cos(a) * 140), Y, z.cz + (int) Math.round(Math.sin(a) * 140));
            }
        });
    }

    private void ilot(Chantier c, int cx, int cz, int r, int herbe) {
        p.disque(c, cx, cz, r, (dx, dz, d) -> {
            int x = cx + dx, z = cz + dz;
            for (int k = 1; k <= 11; k++) c.set(x, Y - k, z, Material.SANDSTONE);
            c.set(x, Y, z, d <= herbe ? Material.GRASS_BLOCK : Material.SAND);
        });
    }

    /** Rayon de la plateforme de la station orbitale (centre du lac). */
    static final int RAYON_STATION = 40;

    private void stationTelescope(Chantier c, Zone z) {
        int cx = z.cx, cz = z.cz, R = RAYON_STATION;
        double aS = versSpawn(z);
        // Plateforme posée sur des pylônes : l'eau reste visible dessous.
        p.disque(c, cx, cz, R, (dx, dz, d) -> {
            int x = cx + dx, zz = cz + dz;
            Material sol;
            if (d > R - 0.8) sol = Material.GOLD_BLOCK;
            else if (d > R - 4) sol = Material.LIGHT_BLUE_CONCRETE;
            else if (Math.abs(d - 30) < 0.6 || Math.abs(d - 21) < 0.6) sol = Material.GOLD_BLOCK;
            else if ((Math.abs(d - 29) < 0.6 || Math.abs(d - 22) < 0.6) && hash(x, 11, zz) < 450) sol = Material.SEA_LANTERN;
            else if (d > 22 && d < 29) sol = (((dx >> 3) + (dz >> 3)) & 1) == 0 ? Material.SMOOTH_QUARTZ : Material.CALCITE;
            else sol = (((dx >> 2) + (dz >> 2)) & 1) == 0 ? Material.POLISHED_DEEPSLATE : Material.DEEPSLATE_TILES;
            c.set(x, Y, zz, sol);
            c.set(x, Y - 1, zz, Material.POLISHED_DEEPSLATE);
            for (int y = Y + 1; y <= Y + 24; y++) c.vider(x, y, zz);
            // Coque inférieure en anneau (on voit l'eau au centre, sous les pylônes).
            if (d > R - 3) for (int k = 2; k <= 4; k++) c.set(x, Y - k, zz, k == 3 ? Material.SEA_LANTERN : Material.POLISHED_DEEPSLATE);
            if (d > R - 0.6) {
                double ang = Math.atan2(dz, dx);
                double arc = (ang + Math.PI) * R;
                if (ecart(ang, aS) > 5) {
                    if (arc % 10 < 1.1) { c.set(x, Y + 1, zz, Material.GOLD_BLOCK); c.set(x, Y + 2, zz, Material.GOLD_BLOCK); c.set(x, Y + 3, zz, Material.SEA_LANTERN); }
                    else { c.set(x, Y + 1, zz, Material.LIGHT_BLUE_STAINED_GLASS); c.set(x, Y + 2, zz, Material.LIGHT_BLUE_STAINED_GLASS); }
                }
            }
        });
        // Pylônes plongés dans le lac.
        c.etape(() -> {
            for (int ring = 0; ring < 2; ring++) {
                int n = ring == 0 ? 8 : 16, r = ring == 0 ? 14 : 34;
                for (int i = 0; i < n; i++) {
                    double a = Math.toRadians(i * 360.0 / n + 11.25);
                    int x = cx + (int) Math.round(Math.cos(a) * r), zz = cz + (int) Math.round(Math.sin(a) * r);
                    for (int dx = 0; dx <= 1; dx++) for (int dz = 0; dz <= 1; dz++)
                        for (int y = Y - 12; y <= Y - 2; y++) c.set(x + dx, y, zz + dz, (y - Y) % 4 == 0 ? Material.GOLD_BLOCK : Material.POLISHED_DEEPSLATE);
                }
            }
            for (int y = Y - 12; y <= Y - 2; y++)
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) c.set(cx + dx, y, cz + dz, Material.SEA_LANTERN);
        });
        // Observatoire principal (ouvert vers la passerelle) et 4 dômes annexes.
        p.dome(c, cx, Y + 1, cz, 18, Math.cos(aS), Math.sin(aS));
        for (int i = 0; i < 4; i++) {
            double a = aS + Math.toRadians(45 + i * 90);
            int x = cx + (int) Math.round(Math.cos(a) * 26), zz = cz + (int) Math.round(Math.sin(a) * 26);
            p.dome(c, x, Y + 1, zz, 5, Math.cos(aS), Math.sin(aS));
            c.etape(() -> { c.set(x, Y + 1, zz, Material.GOLD_BLOCK); c.set(x, Y + 2, zz, Material.SEA_LANTERN); });
        }
        // Balises et antennes sur l'anneau extérieur.
        for (int i = 0; i < 8; i++) {
            double a = aS + Math.toRadians(22.5 + i * 45);
            balise(c, cx + (int) Math.round(Math.cos(a) * 35), Y, cz + (int) Math.round(Math.sin(a) * 35), i % 2 == 0 ? 14 : 9);
        }
        // Anneau en orbite autour de la station.
        p.anneauIncline(c, cx, Y + 30, cz, 34, Math.toRadians(14), aS, Material.GOLD_BLOCK, Material.LIGHT_BLUE_STAINED_GLASS);
        // Télescope géant : tube incliné vers le ciel (à l'opposé du spawn), monté sur une fourche dorée.
        c.etape(() -> {
            double el = Math.toRadians(45), az = aS + Math.PI;
            double ax = Math.cos(el) * Math.cos(az), ay = Math.sin(el), azz = Math.cos(el) * Math.sin(az);
            double px = cx + 0.5, py = Y + 25.5, pz = cz + 0.5;
            for (int y = Y + 17; y <= Y + 22; y++)
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
                    c.set(cx + dx, y, cz + dz, (dx == 0 && dz == 0) ? Material.SEA_LANTERN : Material.GOLD_BLOCK);
            for (int ix = -30; ix <= 30; ix++)
                for (int iy = -14; iy <= 32; iy++)
                    for (int iz = -30; iz <= 30; iz++) {
                        int bx = cx + ix, by = (int) py + iy, bz = cz + iz;
                        double vx = bx + 0.5 - px, vy = by + 0.5 - py, vz = bz + 0.5 - pz;
                        double t = vx * ax + vy * ay + vz * azz;
                        if (t < -12 || t > 34) continue;
                        double qx = vx - ax * t, qy = vy - ay * t, qz = vz - azz * t;
                        double rad = Math.sqrt(qx * qx + qy * qy + qz * qz);
                        double rMax = t > 27 ? 5.8 : t < -9 ? 3.2 : 4.6;
                        if (rad > rMax) continue;
                        Material m;
                        if (t > 33) m = Material.LIGHT_BLUE_STAINED_GLASS;                         // lentille
                        else if (t < -11) m = Material.GOLD_BLOCK;                                  // oculaire
                        else if (Math.abs(t % 8) < 0.7 || t > 27 && rad > 4.8) m = Material.GOLD_BLOCK;
                        else if (rad < rMax - 2) m = Material.SEA_LANTERN;                          // cœur lumineux
                        else m = Material.LIGHT_BLUE_CONCRETE;
                        c.set(bx, by, bz, m);
                    }
            c.compter(15000);
        });
        p.holo(c, z.id, cx + 0.5, Y + 62, cz + 0.5, "<#3FA9F5><bold>Station orbitale</bold>\n<gray>Télescope d'observation", 4f);
    }

    /** Passerelle Orbit de la plage (côté arrivée) jusqu'à la station, sur pylônes. */
    private void passerelle(Chantier c, Zone z) {
        c.etape(() -> {
            double a = versSpawn(z), ux = Math.cos(a), uz = Math.sin(a), px = -uz, pz = ux;
            int debut = RAYON_STATION - 1;
            for (double r = debut; r <= 138; r += 0.5)
                for (double w = -2; w <= 2; w += 0.5) {
                    int x = z.cx + (int) Math.round(ux * r + px * w), zz = z.cz + (int) Math.round(uz * r + pz * w);
                    boolean bord = Math.abs(w) > 1.6;
                    c.set(x, Y, zz, bord ? Material.GOLD_BLOCK : (Math.abs(w) < 0.3 && ((int) r) % 6 == 0 ? Material.SEA_LANTERN : Material.SMOOTH_QUARTZ));
                    if (bord && r > debut + 2 && r < 134) c.set(x, Y + 1, zz, Material.LIGHT_BLUE_STAINED_GLASS);
                }
            for (int r = debut + 7; r <= 130; r += 12)
                for (int s = -1; s <= 1; s += 2) {
                    int x = z.cx + (int) Math.round(ux * r + px * 2 * s), zz = z.cz + (int) Math.round(uz * r + pz * 2 * s);
                    for (int y = Y - 10; y < Y; y++) c.set(x, y, zz, Material.POLISHED_DEEPSLATE);
                    c.set(x, Y + 2, zz, Material.SEA_LANTERN);
                }
        });
    }

    private void phare(Chantier c, int cx, int cz, int h, int r) {
        c.etape(() -> {
            for (int y = Y + 1; y <= Y + h; y++)
                for (int dx = -r - 1; dx <= r + 1; dx++)
                    for (int dz = -r - 1; dz <= r + 1; dz++) {
                        double d = Math.sqrt(dx * dx + dz * dz);
                        if (d < r - 1 || d > r + 0.3) continue;
                        Material m = ((y - Y - 1) / 4) % 2 == 0 ? Material.WHITE_CONCRETE : Material.LIGHT_BLUE_CONCRETE;
                        if ((y - Y) % 8 == 6 && (dx == 0 || dz == 0)) m = Material.LIGHT_BLUE_STAINED_GLASS;
                        c.set(cx + dx, y, cz + dz, m);
                    }
            int top = Y + h + 1;
            for (int dx = -r - 2; dx <= r + 2; dx++)
                for (int dz = -r - 2; dz <= r + 2; dz++) {
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if (d <= r + 2.3) c.set(cx + dx, top, cz + dz, Material.GOLD_BLOCK);
                    if (d > r + 1.3 && d <= r + 2.3) c.set(cx + dx, top + 1, cz + dz, Material.LIGHT_BLUE_STAINED_GLASS);
                    if (d <= r - 1.2) {
                        for (int y = top + 1; y <= top + 4; y++)
                            c.set(cx + dx, y, cz + dz, d < 1.5 ? Material.SEA_LANTERN : Material.LIGHT_BLUE_STAINED_GLASS);
                    }
                    if (d <= r - 0.5) c.set(cx + dx, top + 5, cz + dz, Material.GOLD_BLOCK);
                }
            for (int y = top + 6; y <= top + 8; y++) c.set(cx, y, cz, y == top + 8 ? Material.END_ROD : Material.GOLD_BLOCK);
        });
    }

    private void balise(Chantier c, int x, int ySol, int z, int h) {
        c.etape(() -> {
            for (int y = ySol + 1; y <= ySol + h; y++) c.set(x, y, z, y % 2 == 0 ? Material.GOLD_BLOCK : Material.LIGHT_BLUE_CONCRETE);
            c.set(x, ySol + h + 1, z, Material.SEA_LANTERN);
            c.set(x, ySol + h + 2, z, Material.END_ROD);
        });
    }

    private void ponton(Chantier c, Zone z, double a) {
        c.etape(() -> {
            double ux = Math.cos(a), uz = Math.sin(a), px = -uz, pz = ux;
            for (double r = 99; r <= 138; r += 0.5)
                for (double w = -1.5; w <= 1.5; w += 0.5) {
                    int x = z.cx + (int) Math.round(ux * r + px * w), zz = z.cz + (int) Math.round(uz * r + pz * w);
                    c.set(x, Y, zz, Material.SPRUCE_PLANKS);
                }
            for (int r = 100; r <= 136; r += 6)
                for (int s = -1; s <= 1; s += 2) {
                    int x = z.cx + (int) Math.round(ux * r + px * 2 * s), zz = z.cz + (int) Math.round(uz * r + pz * 2 * s);
                    for (int y = Y - 9; y <= Y; y++) c.set(x, y, zz, Material.SPRUCE_LOG);
                    c.set(x, Y + 1, zz, Material.SPRUCE_FENCE);
                    if (r % 12 == 4) c.set(x, Y + 2, zz, Material.LANTERN);
                }
            // Plateforme au bout.
            int ex = z.cx + (int) Math.round(ux * 95), ez = z.cz + (int) Math.round(uz * 95);
            for (int dx = -5; dx <= 5; dx++)
                for (int dz = -5; dz <= 5; dz++) {
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if (d > 5.2) continue;
                    c.set(ex + dx, Y, ez + dz, d > 4.3 ? Material.GOLD_BLOCK : Material.SPRUCE_PLANKS);
                    if (d > 4.3 && hash(ex + dx, 3, ez + dz) < 300) c.set(ex + dx, Y + 1, ez + dz, Material.SPRUCE_FENCE);
                }
            p.lampadaire(c, ex, Y, ez);
        });
    }

    // ------------------------------------------------------------------ Bûcheron : la forêt suspendue

    private boolean allee(Zone z, int dx, int dz, double d, double marge) {
        if (d < 26) return false;
        if (Math.abs(d - 60) < marge || Math.abs(d - 150) < marge) return true;
        double a0 = versSpawn(z);
        for (int k = 0; k < 6; k++) {
            double a = a0 + Math.toRadians(k * 60);
            double along = dx * Math.cos(a) + dz * Math.sin(a), perp = Math.abs(-dx * Math.sin(a) + dz * Math.cos(a));
            if (along > 0 && perp < marge) return true;
        }
        return false;
    }

    /** Tapis de fleurs (pétales, fleurs sauvages, litière) avec 2 à 4 éléments par bloc. */
    private static final org.bukkit.block.data.BlockData[] TAPIS = {
            org.bukkit.Bukkit.createBlockData("minecraft:pink_petals[flower_amount=3]"),
            org.bukkit.Bukkit.createBlockData("minecraft:pink_petals[flower_amount=4]"),
            org.bukkit.Bukkit.createBlockData("minecraft:wildflowers[flower_amount=3]"),
            org.bukkit.Bukkit.createBlockData("minecraft:wildflowers[flower_amount=4]"),
            org.bukkit.Bukkit.createBlockData("minecraft:leaf_litter[segment_amount=3]"),
            org.bukkit.Bukkit.createBlockData("minecraft:leaf_litter[segment_amount=4]")};

    /**
     * Sol du Bûcheron en grandes taches de couleur (≈ 40 blocs) : prairie bleue, prairie rouge et jaune,
     * tapis rose, mousse, sous-bois d'automne, fleurs sauvages.
     */
    private void solColore(Chantier c, int x, int z) {
        int qx = Math.floorDiv(x + (int) Math.round(bruit(z, x) * 3), 40), qz = Math.floorDiv(z + (int) Math.round(bruit(x, z) * 3), 40);
        int palette = hash(qx, 99, qz) % 6;
        int h = hash(x, 6, z);
        Material sol = Material.GRASS_BLOCK;
        org.bukkit.block.data.BlockData deco = null;
        switch (palette) {
            case 0 -> { if (h < 330) deco = new Material[]{Material.CORNFLOWER, Material.BLUE_ORCHID, Material.AZURE_BLUET}[h % 3].createBlockData(); }
            case 1 -> { if (h < 330) deco = new Material[]{Material.POPPY, Material.DANDELION, Material.RED_TULIP, Material.ORANGE_TULIP}[h % 4].createBlockData(); }
            case 2 -> { if (h < 450) deco = TAPIS[h % 2]; else if (h < 500) deco = Material.ALLIUM.createBlockData(); }
            case 3 -> { sol = h < 700 ? Material.MOSS_BLOCK : Material.GRASS_BLOCK; if (h < 250) deco = Material.MOSS_CARPET.createBlockData(); }
            case 4 -> { sol = h < 600 ? Material.PODZOL : Material.COARSE_DIRT; if (h < 350) deco = TAPIS[4 + h % 2]; else if (h < 420) deco = Material.FERN.createBlockData(); }
            default -> { if (h < 400) deco = TAPIS[2 + h % 2]; else if (h < 450) deco = Material.OXEYE_DAISY.createBlockData(); }
        }
        c.set(x, Y, z, sol);
        if (deco != null) c.set(x, Y + 1, z, deco);
    }

    private void bucheron(Chantier c, Zone z) {
        int R = z.rayon;
        p.disque(c, z.cx, z.cz, R, (dx, dz, d) -> {
            int x = z.cx + dx, zz = z.cz + dz;
            if (d >= R - 1) p.barriere(c, x, zz, Math.atan2(dz, dx), R);
            else if (d < 26) c.set(x, Y, zz, hash(x, 4, zz) < 500 ? Material.COARSE_DIRT : Material.PODZOL);
            else if (allee(z, dx, dz, d, 2.2)) c.set(x, Y, zz, Material.DIRT_PATH);
            else solColore(c, x, zz);
            for (int k = 1; k <= 3; k++) c.set(x, Y - k, zz, Material.DIRT);
            p.dessous(c, x, zz, d, R, 3, 62, Material.STONE, Material.ANDESITE);
        });

        // Scierie orbitale au centre.
        double a = versSpawn(z);
        p.dome(c, z.cx, Y + 1, z.cz, 14, Math.cos(a), Math.sin(a));
        c.etape(() -> {
            Material[] buches = {Material.OAK_LOG, Material.OAK_LOG, Material.STRIPPED_OAK_LOG, Material.OAK_WOOD};
            for (int i = 0; i < 4; i++) {
                double b = Math.toRadians(i * 90 + 45);
                int x = z.cx + (int) Math.round(Math.cos(b) * 8), zz = z.cz + (int) Math.round(Math.sin(b) * 8);
                for (int dx = -1; dx <= 1; dx++)
                    for (int dz = -1; dz <= 1; dz++)
                        for (int y = Y + 1; y <= Y + 2; y++) c.set(x + dx, y, zz + dz, buches[i]);
            }
            c.set(z.cx, Y + 1, z.cz, Material.CAMPFIRE);
            c.set(z.cx + 3, Y + 1, z.cz, Material.STONECUTTER);
            c.set(z.cx - 3, Y + 1, z.cz, Material.STONECUTTER);
        });
        p.holo(c, z.id, z.cx + 0.5, Y + 18, z.cz + 0.5, "<#C8894B><bold>Scierie orbitale</bold>", 2.5f);

        // Forêt : grille irrégulière, essences par secteur.
        // Que des chênes (petits et grands), décision du 23/11/2026.
        TreeType[][] secteurs = {{TreeType.TREE, TreeType.BIG_TREE}};
        Random rnd = new Random(21);
        for (int gx = -R; gx <= R; gx += 8)
            for (int gz = -R; gz <= R; gz += 8) {
                int dx = gx + rnd.nextInt(5) - 2, dz = gz + rnd.nextInt(5) - 2;
                long graine = rnd.nextLong();
                int choix = rnd.nextInt(100);
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d < 30 || d > R - 10 || allee(z, dx, dz, d, 4.5) || couloir(z, dx, dz, 14)) continue;
                double ang = Math.toDegrees(Math.atan2(dz, dx) - a) % 360;
                if (ang < 0) ang += 360;
                TreeType[] s = secteurs[0];
                p.arbre(c, z.cx + dx, Y, z.cz + dz, choix < 18 ? s[1] : s[0], graine, Material.GRASS_BLOCK);
            }
        c.etape(() -> {
            for (int r : new int[]{57, 147}) {
                int n = (int) (2 * Math.PI * r / 20);
                for (int i = 0; i < n; i++) {
                    double b = 2 * Math.PI * i / n;
                    p.lampadaire(c, z.cx + (int) Math.round(Math.cos(b) * r), Y, z.cz + (int) Math.round(Math.sin(b) * r));
                }
            }
        });
    }

    // ------------------------------------------------------------------ Agriculture : la serre orbitale

    /** Que du blé (décision du 23/11/2026). Ajouter d'autres cultures ici pour varier les parcelles. */
    private static final Material[] PARCELLES = {Material.WHEAT};

    private void agriculture(Chantier c, Zone z) {
        int R = z.rayon;
        Farmland terre = (Farmland) Material.FARMLAND.createBlockData();
        terre.setMoisture(terre.getMaximumMoisture());
        p.disque(c, z.cx, z.cz, R, (dx, dz, d) -> {
            int x = z.cx + dx, zz = z.cz + dz;
            for (int k = 1; k <= 3; k++) c.set(x, Y - k, zz, Material.DIRT);
            p.dessous(c, x, zz, d, R, 3, 62, Material.STONE, Material.GRANITE);
            if (d >= R - 1) { p.barriere(c, x, zz, Math.atan2(dz, dx), R); return; }
            if (d < 34 || d > R - 14) {
                c.set(x, Y, zz, Material.GRASS_BLOCK);
                int h = hash(x, 8, zz);
                if (d < 30 && d > 6 && h < 200) c.set(x, Y + 1, zz, new Material[]{Material.POPPY, Material.DANDELION, Material.CORNFLOWER, Material.ALLIUM}[h % 4]);
                return;
            }
            if (couloir(z, dx, dz, 3)) { c.set(x, Y, zz, Material.DIRT_PATH); return; }
            int pu = Math.floorMod(dx, 23), pv = Math.floorMod(dz, 23);
            if (pu < 2 || pv < 2) {
                c.set(x, Y, zz, Material.DIRT_PATH);
                if (pu == 0 && pv == 0) { c.set(x, Y + 1, zz, Material.SPRUCE_FENCE); c.set(x, Y + 2, zz, Material.LANTERN); }
                return;
            }
            int u = pu - 2, v = pv - 2;
            if (u % 7 == 3) { c.set(x, Y, zz, Material.WATER); return; }
            Material type = PARCELLES[hash(Math.floorDiv(dx, 23), 0, Math.floorDiv(dz, 23)) % PARCELLES.length];
            switch (type) {
                case MELON, PUMPKIN -> {
                    c.set(x, Y, zz, Material.DIRT);
                    if (v % 2 == 0) c.set(x, Y + 1, zz, type);
                }
                case SUGAR_CANE -> {
                    boolean bordEau = u % 7 == 2 || u % 7 == 4;
                    c.set(x, Y, zz, bordEau ? Material.DIRT : Material.GRASS_BLOCK);
                    if (bordEau) for (int y = Y + 1; y <= Y + 3; y++) c.set(x, y, zz, Material.SUGAR_CANE);
                }
                default -> {
                    c.set(x, Y, zz, terre);
                    Ageable mur = (Ageable) type.createBlockData();
                    mur.setAge(mur.getMaximumAge());
                    c.set(x, Y + 1, zz, mur);
                }
            }
        });

        // Grande serre au centre : verger sous dôme, fontaine.
        double a = versSpawn(z);
        p.dome(c, z.cx, Y + 1, z.cz, 30, Math.cos(a), Math.sin(a));
        p.disque(c, z.cx, z.cz, 5, (dx, dz, d) -> {
            int x = z.cx + dx, zz = z.cz + dz;
            if (d > 4.2) { c.set(x, Y, zz, Material.GOLD_BLOCK); c.set(x, Y + 1, zz, Material.SMOOTH_QUARTZ_SLAB); }
            else { c.set(x, Y, zz, Material.WATER); c.set(x, Y - 1, zz, Material.SEA_LANTERN); }
        });
        Random rnd = new Random(31);
        for (int i = 0; i < 8; i++) {
            double b = Math.toRadians(i * 45 + 22.5);
            p.arbre(c, z.cx + (int) Math.round(Math.cos(b) * 17), Y, z.cz + (int) Math.round(Math.sin(b) * 17),
                    i % 2 == 0 ? TreeType.AZALEA : TreeType.CHERRY, rnd.nextLong(), Material.GRASS_BLOCK);
        }
        p.holo(c, z.id, z.cx + 0.5, Y + 34, z.cz + 0.5, "<#F5D442><bold>Serre orbitale</bold>", 3f);

        // Éoliennes au bord.
        for (int i = 1; i < 6; i++) {
            double b = a + Math.toRadians(i * 60);
            eolienne(c, z.cx + (int) Math.round(Math.cos(b) * (R - 8)), z.cz + (int) Math.round(Math.sin(b) * (R - 8)), Math.cos(b), Math.sin(b));
        }
        // Bottes de foin près de l'arrivée.
        c.etape(() -> {
            var ar = z.arrivee(c.world());
            Random r = new Random(32);
            for (int i = 0; i < 14; i++) {
                int x = ar.getBlockX() + r.nextInt(17) - 8, zz = ar.getBlockZ() + r.nextInt(17) - 8;
                if (Math.abs(x - ar.getBlockX()) < 5 && Math.abs(zz - ar.getBlockZ()) < 5) continue;
                c.set(x, Y + 1, zz, Material.HAY_BLOCK);
                if (r.nextBoolean()) c.set(x, Y + 2, zz, Material.HAY_BLOCK);
            }
        });
    }

    private void eolienne(Chantier c, int x, int z, double nx, double nz) {
        c.etape(() -> {
            int h = 26;
            for (int y = Y + 1; y <= Y + h; y++) c.set(x, y, z, (y - Y) % 5 == 0 ? Material.LIGHT_BLUE_CONCRETE : Material.WHITE_CONCRETE);
            c.set(x, Y + h + 1, z, Material.GOLD_BLOCK);
            double tx = -nz, tz = nx;
            int hx = x - (int) Math.round(nx), hz = z - (int) Math.round(nz);
            int hy = Y + h + 1;
            c.set(hx, hy, hz, Material.SEA_LANTERN);
            for (int k = 0; k < 3; k++) {
                double th = Math.toRadians(90 + k * 120);
                for (double l = 1; l <= 13; l += 0.4) {
                    double u = Math.cos(th) * l, v = Math.sin(th) * l;
                    c.set(hx + (int) Math.round(tx * u), hy + (int) Math.round(v), hz + (int) Math.round(tz * u),
                            l > 11 ? Material.LIGHT_BLUE_CONCRETE : Material.WHITE_CONCRETE);
                }
            }
        });
    }

    // ------------------------------------------------------------------ Mobzone : la ceinture des ombres

    // ------------------------------------------------------------------ Mobzone : 11 parts (une par mob de l'épée)

    /** Rayon de la place centrale (statue + mur) : pas de part, pas de spawner. */
    static final double PLACE = 52;
    /** Écart entre deux spawners et nombre de mobs par apparition (spawner « pack » x10). */
    static final int ECART_SPAWNERS = 7, PACK_SPAWNER = 10;

    /** Nombre de parts = nombre de ressources de l'épée (une part par mob). */
    private int nbParts() { return Math.max(1, p.plugin.ressources().liste(Outil.Mode.EPEE).size()); }

    /** Part (0..n-1) d'un point de la Mobzone ; la part 0 est centrée vers le spawn (là où on arrive). -1 : place centrale. */
    static int partMobzone(Zone z, double x, double zc, int n) {
        double dx = x - z.cx, dz = zc - z.cz;
        if (dx * dx + dz * dz < PLACE * PLACE) return -1;
        double t = (Math.atan2(dz, dx) - versSpawn(z)) / (2 * Math.PI) * n + 0.5;
        return Math.floorMod((int) Math.floor(t), n);
    }

    /** Angle du milieu de la part 0 (vers le spawn) ; les parts suivantes tournent de 2π/n. */
    static double angleParts(Zone z) { return versSpawn(z); }

    /** Distance (blocs) de (dx, dz) à la limite entre deux parts la plus proche. */
    static double distanceLimite(Zone z, double dx, double dz, int n) {
        double best = Double.MAX_VALUE;
        for (int k = 0; k < n; k++) {
            double a = versSpawn(z) + (k + 0.5) * 2 * Math.PI / n;
            double along = dx * Math.cos(a) + dz * Math.sin(a);
            if (along > 0) best = Math.min(best, Math.abs(-dx * Math.sin(a) + dz * Math.cos(a)));
        }
        return best;
    }

    /** Ouverture du mur de la place, face au milieu de chaque part. */
    private static boolean ouverture(Zone z, int dx, int dz, int n) {
        for (int k = 0; k < n; k++) {
            double a = versSpawn(z) + k * 2 * Math.PI / n;
            double along = dx * Math.cos(a) + dz * Math.sin(a), perp = Math.abs(-dx * Math.sin(a) + dz * Math.cos(a));
            if (along > 0 && perp < 3.5) return true;
        }
        return false;
    }

    /** Sol de la Mobzone (hors bordure) : dalles sombres, ligne d'or entre deux parts. */
    private static Material solMobzone(Zone z, int dx, int dz, double d, int n) {
        int x = z.cx + dx, zz = z.cz + dz;
        if (d >= PLACE && d < z.rayon - 3 && distanceLimite(z, dx, dz, n) < 0.75) return Material.GOLD_BLOCK;
        int h = hash(x, 9, zz);
        if (d < 2.5) return Material.CRYING_OBSIDIAN;
        if (d < 6) return Material.CHISELED_POLISHED_BLACKSTONE;
        if (Math.abs(d - 20) < 0.6 || Math.abs(d - 120) < 0.6) return Material.GILDED_BLACKSTONE;
        if (Math.abs(d - 121.2) < 0.6) return Material.RED_NETHER_BRICKS;
        if (h < 15) return Material.SHROOMLIGHT;
        if (h < 165) return Material.CRACKED_POLISHED_BLACKSTONE_BRICKS;
        if (h < 315) return Material.BLACKSTONE;
        if (h < 415) return Material.POLISHED_BASALT;
        if (h < 515) return Material.DEEPSLATE_TILES;
        return Material.POLISHED_BLACKSTONE_BRICKS;
    }

    /** Mur de la place centrale (anneau 50..51.6), avec une ouverture par part. */
    private static boolean mur(double d) { return d >= 50 && d <= 51.6; }

    private void poserMur(Chantier c, Zone z, int dx, int dz, int n) {
        int x = z.cx + dx, zz = z.cz + dz;
        if (ouverture(z, dx, dz, n)) return;
        for (int y = Y + 1; y <= Y + 5; y++) c.set(x, y, zz, Material.POLISHED_BLACKSTONE_BRICKS);
        c.set(x, Y + 6, zz, Material.GILDED_BLACKSTONE);
    }

    private void mobzone(Chantier c, Zone z) {
        int R = z.rayon, n = nbParts();
        double a0 = versSpawn(z);
        p.disque(c, z.cx, z.cz, R, (dx, dz, d) -> {
            int x = z.cx + dx, zz = z.cz + dz;
            for (int k = 1; k <= 3; k++) c.set(x, Y - k, zz, Material.BLACKSTONE);
            p.dessous(c, x, zz, d, R, 3, 62, Material.DEEPSLATE, Material.BLACKSTONE);
            double ang = Math.atan2(dz, dx);
            if (d >= R - 2) {
                double arc = (ang + Math.PI) * R;
                for (int y = Y; y <= Y + 4; y++) c.set(x, y, zz, (y == Y + 3 && arc % 16 < 1.2) ? Material.GOLD_BLOCK : Material.POLISHED_BLACKSTONE_BRICKS);
                if (((int) arc) % 2 == 0) c.set(x, Y + 5, zz, Material.POLISHED_BLACKSTONE_BRICKS);
                if (arc % 16 < 1.2) c.set(x, Y + 6, zz, Material.SOUL_LANTERN);
                return;
            }
            c.set(x, Y, zz, solMobzone(z, dx, dz, d, n));
            if (mur(d)) poserMur(c, z, dx, dz, n);
        });
        // Statue de l'astronaute au centre de la place, tournée vers l'arrivée.
        new Statue().dessiner(c, z.cx, z.cz, Y, versSpawn(z));

        // Ruines : piliers brisés.
        Random rnd = new Random(41);
        var arrivee = z.arrivee(c.world());
        for (int i = 0; i < 70; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, d = 60 + rnd.nextDouble() * (R - 75);
            int x = z.cx + (int) Math.round(Math.cos(a) * d), zz = z.cz + (int) Math.round(Math.sin(a) * d);
            int h = 3 + rnd.nextInt(9);
            boolean lanterne = rnd.nextInt(10) < 4;
            if (Math.hypot(x - arrivee.getX(), zz - arrivee.getZ()) < 28) continue;
            c.etape(() -> {
                for (int y = Y + 1; y <= Y + h; y++)
                    c.set(x, y, zz, y == Y + 3 ? Material.GILDED_BLACKSTONE : (hash(x, y, zz) < 400 ? Material.CRACKED_POLISHED_BLACKSTONE_BRICKS : Material.POLISHED_BLACKSTONE));
                if (lanterne) c.set(x, Y + h + 1, zz, Material.SOUL_LANTERN);
            });
        }
        // Pylônes corrompus.
        for (int k = 1; k < 6; k++) {
            double a = a0 + Math.toRadians(k * 60);
            int x = z.cx + (int) Math.round(Math.cos(a) * 160), zz = z.cz + (int) Math.round(Math.sin(a) * 160);
            c.etape(() -> {
                for (int y = Y + 1; y <= Y + 20; y++) {
                    c.set(x, y, zz, Material.CRYING_OBSIDIAN);
                    for (int[] o : new int[][]{{1, 1}, {1, -1}, {-1, 1}, {-1, -1}})
                        c.set(x + o[0], y, zz + o[1], (y - Y) % 5 == 0 ? Material.GOLD_BLOCK : Material.POLISHED_BLACKSTONE_BRICKS);
                }
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) c.set(x + dx, Y + 21, zz + dz, Material.GOLD_BLOCK);
                c.set(x, Y + 22, zz, Material.SOUL_CAMPFIRE);
            });
        }
        // Une part par mob de l'épée : panneaux + spawners x10 tous les 7 blocs (après les ruines et pylônes).
        parts(c, z);
    }

    /** Panneaux de chaque part (près du mur et vers le bord) puis spawners. Renvoie le nombre de spawners posés (rempli pendant le chantier). */
    int[] parts(Chantier c, Zone z) {
        int R = z.rayon, n = nbParts();
        java.util.List<org.bukkit.entity.EntityType> mobs = p.plugin.ressources().mobsDesParts();
        java.util.List<Ressources.Palier> liste = p.plugin.ressources().liste(Outil.Mode.EPEE);
        String tag = z.id + "-parts";
        c.etape(() -> {
            for (int k = 0; k < n; k++) {
                double a = versSpawn(z) + k * 2 * Math.PI / n;
                int q = p.plugin.ressources().quete(k);
                String texte = (k == 0 ? "<#7CFC9A>" : "<#FF4D4D>") + "<bold>Part " + (k + 1) + " · " + liste.get(k).nom() + "</bold>\n"
                        + (k == 0 ? "<gray>Libre" : q > 0 ? "<gray>Épée · <#E0B864>Prestige " + q : "<gray>Pas encore disponible");
                for (double r : new double[]{57, R - 30})
                    p.holoImmediat(c.world(), tag, z.cx + Math.cos(a) * r + 0.5, Y + 4.2, z.cz + Math.sin(a) * r + 0.5,
                            Navigateur.texte(texte), 1.6f, 1.5f);
            }
        });
        org.bukkit.block.spawner.SpawnRule regle = new org.bukkit.block.spawner.SpawnRule(0, 15, 0, 15);   // de jour comme de nuit
        org.bukkit.NamespacedKey clePile = Piles.clePile(p.plugin);
        var arrivee = z.arrivee(c.world());
        int[] poses = {0};
        int debut = -(R / ECART_SPAWNERS) * ECART_SPAWNERS;
        for (int gx = debut; gx <= R; gx += ECART_SPAWNERS) {
            final int dx = gx;
            c.etape(() -> {
                for (int dz = debut; dz <= R; dz += ECART_SPAWNERS) {
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if (d < PLACE + 4 || d > R - 8) continue;
                    if (distanceLimite(z, dx, dz, n) < 3) continue;          // pas sur la ligne entre deux parts
                    int x = z.cx + dx, zz = z.cz + dz;
                    if (Math.hypot(x - arrivee.getX(), zz - arrivee.getZ()) < 25) continue;
                    int part = partMobzone(z, x, zz, n);
                    org.bukkit.entity.EntityType type = part < 0 || part >= mobs.size() ? null : mobs.get(part);
                    if (type == null) continue;
                    org.bukkit.block.Block b = c.world().getBlockAt(x, Y, zz);
                    if (b.getType().isAir() || !b.getRelative(0, 1, 0).getType().isAir()) continue;   // pas sous une ruine
                    org.bukkit.block.spawner.SpawnerEntry entree;
                    try {
                        entree = new org.bukkit.block.spawner.SpawnerEntry(
                                org.bukkit.Bukkit.getEntityFactory().createEntitySnapshot("{id:\"" + type.getKey() + "\"}"), 1, regle);
                    } catch (Exception ex) {
                        p.plugin.getLogger().warning("Spawner : " + type + " ignoré (" + ex.getMessage() + ").");
                        continue;
                    }
                    c.set(x, Y, zz, Material.SPAWNER);
                    if (!(b.getState() instanceof org.bukkit.block.CreatureSpawner s)) continue;
                    s.setPotentialSpawns(java.util.List.of(entree));
                    s.setSpawnRange(3);
                    s.setRequiredPlayerRange(20);
                    s.setMaxNearbyEntities(4);
                    s.setSpawnCount(1);
                    s.setMinSpawnDelay(160);
                    s.setMaxSpawnDelay(360);
                    s.setDelay(40 + hash(x, 14, zz) % 200);
                    s.getPersistentDataContainer().set(clePile, org.bukkit.persistence.PersistentDataType.INTEGER, PACK_SPAWNER);
                    s.update(true, false);
                    p.holoImmediat(c.world(), tag, x + 0.5, Y + 1.6, zz + 0.5, Piles.etiquetteSpawner(type, PACK_SPAWNER), 0.9f, 0.4f);
                    c.compter(120);
                    poses[0]++;
                }
            });
        }
        return poses;
    }

    /**
     * Mobzone déjà construite → nouvelle version (parts), sans toucher aux ruines ni au dessous de l'île :
     * anciens spawners remis en sol, lignes d'or entre les parts, place centrale vidée, mur à une ouverture par part,
     * statue, panneaux et nouveaux spawners. Les anciennes étiquettes et les mobs sont retirés à part (entités).
     */
    int[] refaireMobzone(Chantier c, Zone z) {
        int R = z.rayon, n = nbParts();
        p.disque(c, z.cx, z.cz, R - 3, (dx, dz, d) -> {
            int x = z.cx + dx, zz = z.cz + dz;
            org.bukkit.block.Block sol = c.world().getBlockAt(x, Y, zz);
            if (d < 48.6) {                                   // place centrale : on repart de zéro
                for (int y = Y + 1; y <= Y + Statue.HAUT; y++) c.vider(x, y, zz);
                c.set(x, Y, zz, solMobzone(z, dx, dz, d, n));
                return;
            }
            if (d < 53) {                                     // ancien mur à 4 entrées → nouveau mur
                for (int y = Y + 1; y <= Y + 6; y++) c.vider(x, y, zz);
                c.set(x, Y, zz, solMobzone(z, dx, dz, d, n));
                if (mur(d)) poserMur(c, z, dx, dz, n);
                return;
            }
            boolean libre = sol.getRelative(0, 1, 0).getType().isAir();
            if (sol.getType() == Material.SPAWNER || (libre && sol.getType() != Material.GOLD_BLOCK
                    && distanceLimite(z, dx, dz, n) < 0.75))
                c.set(x, Y, zz, solMobzone(z, dx, dz, d, n));
        });
        new Statue().dessiner(c, z.cx, z.cz, Y, versSpawn(z));
        return parts(c, z);
    }

    // ------------------------------------------------------------------ Mine : l'astéroïde minier

    static final int FOSSE = 160;

    /** Un étage = un minerai. Index 0 = gradin du haut, 10 = fond de la fosse. */
    static final Material[] MINERAI_ETAGE = {
            Material.COAL_ORE, Material.COPPER_ORE, Material.IRON_ORE, Material.LAPIS_ORE, Material.REDSTONE_ORE,
            Material.DEEPSLATE_GOLD_ORE, Material.NETHER_QUARTZ_ORE, Material.AMETHYST_BLOCK,
            Material.DEEPSLATE_DIAMOND_ORE, Material.DEEPSLATE_EMERALD_ORE, Material.ANCIENT_DEBRIS};
    static final String[] NOM_ETAGE = {"Charbon", "Cuivre", "Fer", "Lapis-lazuli", "Redstone", "Or", "Quartz",
            "Améthyste", "Diamant", "Émeraude", "Débris antiques"};

    /** Étage (0..10) d'une distance au centre de la fosse. */
    static int etage(double d) { return Math.max(0, Math.min(10, (int) ((FOSSE - d) / 12))); }

    /** Filons : bandes obliques continues (≈ 22 % du volume), pas des points isolés. */
    private static boolean filon(int x, int y, int z) {
        double v = Math.sin(x * 0.29 + y * 0.41 + z * 0.17) * 0.6 + Math.sin(z * 0.33 - x * 0.19 + y * 0.23) * 0.4;
        return Math.abs(v) < 0.2 || hash(x, y, z) < 25;
    }

    static Material roche(int etage, int x, int y, int z) {
        int h = hash(x, y + 7, z);
        if (etage <= 4) return h < 180 ? Material.ANDESITE : Material.STONE;
        return h < 150 ? Material.TUFF : Material.DEEPSLATE;
    }

    private void mine(Chantier c, Zone z) {
        int R = z.rayon;
        double a0 = versSpawn(z);
        // 4 escaliers alignés sur les axes (vers +x, -x, +z, -z) : vrais blocs d'escalier.
        int[][] axes = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        org.bukkit.block.BlockFace[] montee = {org.bukkit.block.BlockFace.EAST, org.bukkit.block.BlockFace.WEST,
                org.bukkit.block.BlockFace.SOUTH, org.bukkit.block.BlockFace.NORTH};
        org.bukkit.block.data.type.Stairs[] marches = new org.bukkit.block.data.type.Stairs[4];
        for (int i = 0; i < 4; i++) {
            marches[i] = (org.bukkit.block.data.type.Stairs) Material.POLISHED_DEEPSLATE_STAIRS.createBlockData();
            marches[i].setFacing(montee[i]);
        }

        p.disque(c, z.cx, z.cz, R, (dx, dz, d) -> {
            int x = z.cx + dx, zz = z.cz + dz;
            if (d >= FOSSE) {
                if (d >= R - 1) p.barriere(c, x, zz, Math.atan2(dz, dx), R);
                else {
                    int h = hash(x, 10, zz);
                    c.set(x, Y, zz, h < 500 ? Material.STONE : h < 700 ? Material.ANDESITE : h < 850 ? Material.TUFF : h < 900 ? Material.GRAVEL : Material.COBBLESTONE);
                }
                for (int k = 1; k <= 3; k++) c.set(x, Y - k, zz, Material.STONE);
                p.dessous(c, x, zz, d, R, 3, 66, Material.STONE, Material.DEEPSLATE);
                return;
            }
            int L = etage(d);
            int prof = L * 5;
            int marche = -1;
            // Couloir d'escalier : 3 de large ; descente d'un bloc par bloc sur les 5 derniers blocs de chaque gradin.
            for (int i = 0; i < 4; i++) {
                int along = dx * axes[i][0] + dz * axes[i][1], perp = Math.abs(dx * axes[i][1] - dz * axes[i][0]);
                if (along <= 0 || perp > 1) continue;
                int reste = FOSSE - along;
                int Le = Math.min(10, reste / 12), s = reste - Le * 12;
                if (Le >= 10) { prof = 50; break; }
                int descente = Math.max(0, Math.min(5, s - 7));
                prof = Le * 5 + descente;
                if (s >= 7) marche = i;
                L = Le;
                break;
            }
            int fond = Math.max(Plans.profondeur(x, zz, d, R, 66), prof + 16);
            Material ore = MINERAI_ETAGE[L];
            for (int k = prof; k <= fond; k++) {
                int y = Y - k;
                Material m;
                if (k < prof + 12) m = filon(x, y, zz) ? ore : roche(L, x, y, zz);
                else m = k > fond - 3 ? Material.DEEPSLATE : Material.STONE;
                c.set(x, y, zz, m);
            }
            if (marche >= 0) c.set(x, Y - prof, zz, marches[marche]);
        });

        // Lampadaires sur chaque gradin (pas dans les escaliers) et panneau de l'étage près de l'escalier est.
        c.etape(() -> {
            for (int L = 0; L < 10; L++) {
                int r = FOSSE - L * 12 - 3, ySol = Y - L * 5;
                int n = (int) (2 * Math.PI * r / 22);
                for (int i = 0; i < n; i++) {
                    double b = 2 * Math.PI * i / n;
                    boolean escalier = false;
                    for (int k = 0; k < 4; k++) if (ecart(b, Math.toRadians(k * 90)) < Math.toDegrees(5.0 / r)) escalier = true;
                    if (escalier) continue;
                    p.lampadaire(c, z.cx + (int) Math.round(Math.cos(b) * r), ySol, z.cz + (int) Math.round(Math.sin(b) * r));
                }
            }
        });
        for (int L = 0; L <= 10; L++) {
            int r = L < 10 ? FOSSE - L * 12 - 4 : 6;
            p.holo(c, z.id, z.cx + r + 0.5, Y - L * 5 + 3.2, z.cz + 4.5,
                    "<#B0BEC5><bold>Étage " + (L + 1) + "</bold>\n<#E0B864>" + NOM_ETAGE[L], 1.4f);
        }

        // Fond de la fosse : monument doré.
        c.etape(() -> {
            int sol = Y - 50;
            for (int dx = -3; dx <= 3; dx++)
                for (int dz = -3; dz <= 3; dz++) {
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if (d <= 3.3) c.set(z.cx + dx, sol, z.cz + dz, d > 2.4 ? Material.GOLD_BLOCK : Material.LIGHT_BLUE_CONCRETE);
                }
            for (int y = sol + 1; y <= sol + 7; y++) c.set(z.cx, y, z.cz, y % 2 == 0 ? Material.GOLD_BLOCK : Material.SEA_LANTERN);
            c.set(z.cx, sol + 8, z.cz, Material.END_ROD);
        });

        // Grues minières au bord de la fosse.
        for (int k = 0; k < 4; k++) grue(c, z, a0 + Math.toRadians(45 + k * 90));
    }


    private void grue(Chantier c, Zone z, double b) {
        c.etape(() -> {
            double ux = Math.cos(b), uz = Math.sin(b);
            int x = z.cx + (int) Math.round(ux * (FOSSE + 10)), zz = z.cz + (int) Math.round(uz * (FOSSE + 10));
            int h = 32;
            for (int y = Y + 1; y <= Y + h; y++)
                for (int dx = 0; dx <= 1; dx++) for (int dz = 0; dz <= 1; dz++)
                    c.set(x + dx, y, zz + dz, (y - Y) % 4 == 0 ? Material.GOLD_BLOCK : Material.POLISHED_DEEPSLATE);
            int top = Y + h + 1;
            for (double l = -8; l <= 30; l += 0.5) {
                int bx = x + (int) Math.round(-ux * l), bz = zz + (int) Math.round(-uz * l);
                c.set(bx, top, bz, ((int) l) % 4 == 0 ? Material.GOLD_BLOCK : Material.POLISHED_DEEPSLATE);
            }
            int ex = x + (int) Math.round(-ux * 30), ez = zz + (int) Math.round(-uz * 30);
            for (int y = top - 16; y < top; y++) c.set(ex, y, ez, Material.IRON_CHAIN);
            c.set(ex, top - 17, ez, Material.GOLD_BLOCK);
            int cx2 = x + (int) Math.round(ux * 8), cz2 = zz + (int) Math.round(uz * 8);
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
                for (int y = top - 2; y < top; y++) c.set(cx2 + dx, y, cz2 + dz, Material.IRON_BLOCK);
            c.set(x, top + 1, zz, Material.SEA_LANTERN);
        });
    }
}
