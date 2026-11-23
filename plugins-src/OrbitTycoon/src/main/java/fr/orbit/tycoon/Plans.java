package fr.orbit.tycoon;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.TreeType;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * Dessin du Tycoon, entièrement par code. Ici : les outils communs (colonnes, dessous des îles,
 * barrière, anneaux-portails, dômes, hologrammes) et la Base Orbit. Les îles sont dans {@link Iles}.
 * DA Orbit : bleu glacé + or, lumière de lanternes marines ; chaque zone garde sa couleur d'accent.
 */
final class Plans {
    static final int Y = Zone.Y;
    static final NamespacedKey MODELE_PORTAIL = new NamespacedKey("orbittycoon", "portail");

    final OrbitTycoon plugin;
    final NamespacedKey cleDeco;
    private final Iles iles;

    Plans(OrbitTycoon plugin) {
        this.plugin = plugin;
        this.cleDeco = new NamespacedKey(plugin, "holo");
        this.iles = new Iles(this);
    }

    void dessiner(Chantier c, Zone z, boolean nettoyer) {
        retirerDeco(c, z.cx, z.cz, z.rayon + 10, z.id);
        if (nettoyer) nettoyer(c, z.cx, z.cz, z.rayon + 4, Y - 80, Y + 50);
        if (z == Zone.BASE) base(c); else iles.dessiner(c, z);
    }

    /** Efface l'ancienne version (îles à 400 blocs, petit spawn) : blocs + hologrammes. */
    void effacerAncienneVersion(Chantier c) {
        int[][] anciens = {{0, 0}, {400, 0}, {0, 400}, {-400, 0}, {0, -400}};
        String[] ids = {"base", "peche", "bucheron", "agriculture", "mobzone"};
        for (int i = 0; i < anciens.length; i++) {
            retirerDeco(c, anciens[i][0], anciens[i][1], 60, ids[i]);
            nettoyer(c, anciens[i][0], anciens[i][1], 52, Y - 45, Y + 35);
        }
    }

    // ================================================================== outils communs

    interface Colonne { void at(int dx, int dz, double d); }

    /** Une étape par rangée : appelle f pour chaque colonne du disque de rayon r autour de (cx, cz). */
    void disque(Chantier c, int cx, int cz, int r, Colonne f) {
        for (int dx = -r; dx <= r; dx++) {
            final int fx = dx;
            c.etape(() -> {
                for (int dz = -r; dz <= r; dz++) {
                    double d = Math.sqrt((double) fx * fx + (double) dz * dz);
                    if (d <= r) f.at(fx, dz, d);
                }
            });
        }
    }

    void nettoyer(Chantier c, int cx, int cz, int r, int y0, int y1) {
        disque(c, cx, cz, r, (dx, dz, d) -> {
            for (int y = y0; y <= y1; y++) c.vider(cx + dx, y, cz + dz);
            c.compter((y1 - y0) / 6);   // les lectures coûtent aussi : on les compte dans le budget du tick
        });
    }

    /** Retire hologrammes et disques de portail marqués avec cet id dans le rayon (charge les chunks). */
    void retirerDeco(Chantier c, int cx, int cz, int r, String id) {
        int rc = (r >> 4) + 1;
        for (int qx = (cx >> 4) - rc; qx <= (cx >> 4) + rc; qx++) {
            final int fqx = qx;
            c.etape(() -> {
                for (int qz = (cz >> 4) - rc; qz <= (cz >> 4) + rc; qz++)
                    if (c.world().isChunkGenerated(fqx, qz))
                    for (Entity e : c.world().getChunkAt(fqx, qz).getEntities()) {
                        if (!(e instanceof Display)) continue;
                        String tag = e.getPersistentDataContainer().get(cleDeco, PersistentDataType.STRING);
                        if (id.equals(tag)) e.remove();
                    }
                c.compter(8000);   // une ou deux colonnes de chunks par tick, pas plus
            });
        }
    }

    /** Bruit doux déterministe (pas de Random : même résultat à chaque génération). */
    static double bruit(double x, double z) {
        return Math.sin(x * 0.071 + z * 0.043) * 2.2 + Math.sin(x * 0.023 - z * 0.061) * 3.1 + Math.sin(x * 0.17 + z * 0.13) * 0.8;
    }

    /** Hachage déterministe 0..999 d'une position. */
    static int hash(int x, int y, int z) {
        long h = x * 73856093L ^ y * 19349663L ^ z * 83492791L;
        h ^= (h >>> 13); h *= 0x5bd1e995L; h ^= (h >>> 15);
        return (int) Math.floorMod(h, 1000L);
    }

    /**
     * Dessous d'une île : coque irrégulière en forme de cône. Les couches 1..haut sous la surface sont
     * remplies par l'appelant ; ici on pose la paroi visible (bord et fond), l'intérieur reste creux.
     */
    void dessous(Chantier c, int x, int z, double d, int R, int haut, int profMax, Material roche, Material roche2) {
        int prof = profondeur(x, z, d, R, profMax);
        int profExt = profondeur(x, z, d + 1.5, R, profMax);
        int debut = Math.max(haut + 1, Math.min(prof, profExt) - 2);
        for (int k = debut; k <= prof; k++)
            c.set(x, Y - k, z, hash(x, k, z) < 300 ? roche2 : roche);
    }

    static int profondeur(int x, int z, double d, int R, int profMax) {
        double t = Math.min(1, d / R);
        return 5 + (int) (Math.sqrt(Math.max(0, 1 - t * t)) * profMax + bruit(x, z) * 2.5 * (1 - t * 0.5));
    }

    /** Bord d'île : bande sombre + vitres bleues sur 2 blocs + pilier doré tous les 14 blocs. */
    void barriere(Chantier c, int x, int z, double angleRad, int R) {
        double arc = (angleRad + Math.PI) * R;
        c.set(x, Y, z, Material.POLISHED_DEEPSLATE);
        if (arc % 14 < 1.1) {
            for (int y = Y + 1; y <= Y + 4; y++) c.set(x, y, z, y == Y + 1 ? Material.POLISHED_DEEPSLATE : Material.GOLD_BLOCK);
            c.set(x, Y + 5, z, Material.SEA_LANTERN);
        } else {
            c.set(x, Y + 1, z, Material.LIGHT_BLUE_STAINED_GLASS);
            c.set(x, Y + 2, z, Material.LIGHT_BLUE_STAINED_GLASS);
        }
    }

    /** Lampadaire Orbit : socle doré, fût sombre, lanterne marine. */
    void lampadaire(Chantier c, int x, int ySol, int z) {
        c.set(x, ySol + 1, z, Material.GOLD_BLOCK);
        c.set(x, ySol + 2, z, Material.POLISHED_DEEPSLATE);
        c.set(x, ySol + 3, z, Material.POLISHED_DEEPSLATE);
        c.set(x, ySol + 4, z, Material.SEA_LANTERN);
        c.set(x, ySol + 5, z, Material.LIGHT_BLUE_STAINED_GLASS);
    }

    /** Dôme de verre (demi-sphère creuse) avec nervures dorées ; ouverture vers (ox, oz) si non nul. */
    void dome(Chantier c, int cx, int cy, int cz, int r, double ox, double oz) {
        for (int dx = -r; dx <= r; dx++) {
            final int fx = dx;
            c.etape(() -> {
                for (int dz = -r; dz <= r; dz++)
                    for (int dy = 0; dy <= r; dy++) {
                        double d = Math.sqrt(fx * fx + dy * dy + dz * dz);
                        if (d > r || d < r - 1.0) continue;
                        if ((ox != 0 || oz != 0) && dy < 5) {
                            double along = fx * ox + dz * oz, perp = Math.abs(-fx * oz + dz * ox);
                            if (along > 0 && perp < 2.5) continue;
                        }
                        double ang = Math.toDegrees(Math.atan2(dz, fx)) + 180;
                        double hr = Math.max(1, Math.sqrt(fx * fx + dz * dz));
                        boolean nervure = (ang % 45) < Math.toDegrees(1.0 / hr) || dy == 0;
                        c.set(cx + fx, cy + dy, cz + dz, nervure ? Material.GOLD_BLOCK : Material.LIGHT_BLUE_STAINED_GLASS);
                    }
            });
        }
    }

    /** Anneau incliné (orbites décoratives). */
    void anneauIncline(Chantier c, int cx, int cy, int cz, double r, double incl, double rot, Material a, Material b) {
        c.etape(() -> {
            Set<Long> vus = new HashSet<>();
            double ci = Math.cos(incl), si = Math.sin(incl), cr = Math.cos(rot), sr = Math.sin(rot);
            for (double t = 0; t < 360; t += 0.15) {
                double rad = Math.toRadians(t);
                double x = r * Math.cos(rad), zz = r * Math.sin(rad);
                double y = zz * si;
                zz = zz * ci;
                double x2 = x * cr - zz * sr, z2 = x * sr + zz * cr;
                int bx = (int) Math.round(x2), by = (int) Math.round(y), bz = (int) Math.round(z2);
                long cle = ((long) (bx + 1024) << 22) | ((long) (by + 1024) << 11) | (bz + 1024);
                if (!vus.add(cle)) continue;
                c.set(cx + bx, cy + by, cz + bz, ((int) (t / 12)) % 2 == 0 ? a : b);
            }
        });
    }

    /**
     * Anneau-portail vertical (style porte des étoiles) centré sur p = {x, y, z, nx, nz} :
     * cercle d'or et de bleu avec 9 chevrons lumineux, disque animé au centre (modèle du pack).
     */
    void anneauPortail(Chantier c, double[] p, String tagDeco, String couleurZone) {
        double px = p[0], py = p[1], pz = p[2], nx = p[3], nz = p[4];
        double tx = -nz, tz = nx;
        double rIn = Zone.RAYON_PORTAIL + 0.3, rOut = rIn + 1.7;
        c.etape(() -> {
            int R = (int) Math.ceil(rOut) + 1;
            for (int ix = -R; ix <= R; ix++)
                for (int iy = -R; iy <= R; iy++)
                    for (int iz = -R; iz <= R; iz++) {
                        int bx = (int) Math.floor(px) + ix, by = (int) Math.floor(py) + iy, bz = (int) Math.floor(pz) + iz;
                        double vx = bx + 0.5 - px, vy = by + 0.5 - py, vz = bz + 0.5 - pz;
                        double dn = vx * nx + vz * nz, u = vx * tx + vz * tz;
                        double rr = Math.sqrt(u * u + vy * vy);
                        if (Math.abs(dn) > 0.75 || rr < rIn || rr > rOut) continue;
                        double ang = Math.toDegrees(Math.atan2(vy, u)) + 360;
                        Material m;
                        if (ang % 40 < 7) m = Material.SEA_LANTERN;
                        else if (rr > rOut - 0.6) m = Material.GOLD_BLOCK;
                        else m = ((int) (ang / 20)) % 2 == 0 ? Material.LIGHT_BLUE_CONCRETE : Material.GOLD_BLOCK;
                        c.set(bx, by, bz, m);
                    }
            // Socle sous l'anneau.
            for (int ix = -2; ix <= 2; ix++)
                for (int k = -1; k <= 1; k++) {
                    int bx = (int) Math.floor(px + tx * ix + nx * k), bz = (int) Math.floor(pz + tz * ix + nz * k);
                    c.set(bx, Y, bz, Math.abs(ix) == 2 ? Material.GOLD_BLOCK : Material.SEA_LANTERN);
                }
            World w = c.world();
            float rotation = (float) Math.atan2(nx, nz);
            ItemStack modele = new ItemStack(Material.LIGHT_BLUE_STAINED_GLASS);
            modele.editMeta(m -> m.setItemModel(MODELE_PORTAIL));
            w.spawn(new Location(w, px, py, pz), ItemDisplay.class, d -> {
                d.setItemStack(modele);
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                float taille = (float) (Zone.RAYON_PORTAIL * 2 + 0.6);
                d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(rotation, 0, 1, 0),
                        new Vector3f(taille, taille, 0.15f), new AxisAngle4f()));
                d.setBrightness(new Display.Brightness(15, 15));
                d.setViewRange(4f);
                d.setPersistent(true);
                d.getPersistentDataContainer().set(cleDeco, PersistentDataType.STRING, tagDeco);
            });
        });
        holo(c, tagDeco, px, py + rOut + 2.2, pz, couleurZone, 2.2f);
    }

    void holo(Chantier c, String tag, double x, double y, double z, String texte, float echelle) {
        c.etape(() -> holoImmediat(c.world(), tag, x, y, z, MiniMessage.miniMessage().deserialize(texte), echelle, 3f));
    }

    /** Pose un hologramme tout de suite (à appeler depuis une étape). vue = portée en multiples de 64 blocs. */
    void holoImmediat(World w, String tag, double x, double y, double z, net.kyori.adventure.text.Component texte, float echelle, float vue) {
        w.spawn(new Location(w, x, y, z), TextDisplay.class, d -> {
            d.text(texte);
            d.setBillboard(Display.Billboard.CENTER);
            d.setShadowed(true);
            d.setPersistent(true);
            d.setViewRange(vue);
            d.setBackgroundColor(Color.fromARGB(110, 8, 16, 32));
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                    new Vector3f(echelle, echelle, echelle), new AxisAngle4f()));
            d.getPersistentDataContainer().set(cleDeco, PersistentDataType.STRING, tag);
        });
    }

    void arbre(Chantier c, int x, int ySol, int z, TreeType type, long graine, Material sol) {
        c.etape(() -> {
            c.set(x, ySol, z, sol);
            World w = c.world();
            w.generateTree(new Location(w, x, ySol + 1, z), new Random(graine), type, state -> {
                BlockData data = state.getBlockData();
                if (data instanceof Leaves l) { l.setPersistent(true); state.setBlockData(l); }
            });
            c.compter(80);
        });
    }

    // ================================================================== Base Orbit (spawn)

    private static final String[] PAVILLONS = {
            "<#E0B864><bold>Marché orbital</bold>\n<gray>Bientôt",
            "<#E0B864><bold>Classements</bold>\n<gray>Bientôt",
            "<#E0B864><bold>Atelier des outils</bold>\n<gray>Bientôt",
            "<#E0B864><bold>Quêtes de la station</bold>\n<gray>Bientôt",
            "<#E0B864><bold>Observatoire</bold>\n<gray>Bientôt"};

    private void base(Chantier c) {
        Zone b = Zone.BASE;
        int R = b.rayon;
        double[] angles = Zone.farms().stream().mapToDouble(z -> Math.toRadians(z.angle)).toArray();

        disque(c, 0, 0, R + 1, (dx, dz, d) -> {
            if (d > R + 0.5) return;
            double ang = Math.atan2(dz, dx);
            // Avenue la plus proche (vers une zone).
            double meilleurPerp = 99, along = 0;
            for (double a : angles) {
                double al = dx * Math.cos(a) + dz * Math.sin(a), pe = Math.abs(-dx * Math.sin(a) + dz * Math.cos(a));
                if (al > 0 && pe < meilleurPerp) { meilleurPerp = pe; along = al; }
            }
            boolean avenue = meilleurPerp <= 4.5 && along > 9 && along < Zone.PORTAIL_BASE - 6;

            Material sol;
            if (d < 4.5) sol = Material.GOLD_BLOCK;
            else if (d < 7.5) sol = Material.LIGHT_BLUE_STAINED_GLASS;
            else if (d >= R - 2) sol = Material.POLISHED_DEEPSLATE;
            else if (avenue) {
                if (meilleurPerp > 3.6) sol = Material.GOLD_BLOCK;
                else if (meilleurPerp < 0.6 && along % 6 < 1) sol = Material.SEA_LANTERN;
                else sol = Material.LIGHT_BLUE_CONCRETE;
            } else if (anneau(d, 20) || anneau(d, 45) || anneau(d, 82) || anneau(d, 113)) sol = Material.GOLD_BLOCK;
            else if (anneau(d, 18.8) || anneau(d, 21.2) || anneau(d, 43.8) || anneau(d, 46.2)
                    || anneau(d, 80.8) || anneau(d, 83.2) || anneau(d, 111.8) || anneau(d, 114.2)) sol = Material.LIGHT_BLUE_CONCRETE;
            else if (d < 45) sol = (((dx >> 2) + (dz >> 2)) & 1) == 0 ? Material.SMOOTH_QUARTZ : Material.CALCITE;
            else if (d < 82) sol = ((((dx + dz) >> 2) & 1) == 0) ? Material.QUARTZ_BRICKS : Material.SMOOTH_QUARTZ;
            else if (d < 113) sol = (((dx >> 3) + (dz >> 3)) & 1) == 0 ? Material.POLISHED_DEEPSLATE : Material.DEEPSLATE_TILES;
            else sol = Material.CALCITE;
            c.set(dx, Y, dz, sol);
            c.set(dx, Y - 1, dz, (d >= 4.5 && d < 7.5) ? Material.SEA_LANTERN : Material.POLISHED_DEEPSLATE);
            c.set(dx, Y - 2, dz, Material.POLISHED_DEEPSLATE);

            // Coque : cône de métal sombre, cerclé de lumière tous les 6 blocs.
            int prof = 3 + (int) (Math.sqrt(Math.max(0, 1 - (d / R) * (d / R))) * 55);
            int profExt = 3 + (int) (Math.sqrt(Math.max(0, 1 - Math.min(1, (d + 1.5) / R) * Math.min(1, (d + 1.5) / R))) * 55);
            for (int k = Math.max(3, Math.min(prof, profExt) - 2); k <= prof; k++)
                c.set(dx, Y - k, dz, k % 6 == 0 ? Material.SEA_LANTERN
                        : (((int) (Math.toDegrees(ang) + 180) / 10) % 2 == 0 ? Material.DEEPSLATE_TILES : Material.POLISHED_DEEPSLATE));

            // Garde-corps vitré, piliers dorés.
            if (d >= R - 0.9 && !Dome.couloir(dx, dz)) {
                double arc = (ang + Math.PI) * R;
                if (arc % 12 < 1.2) {
                    for (int y = Y + 1; y <= Y + 5; y++) c.set(dx, y, dz, Material.GOLD_BLOCK);
                    c.set(dx, Y + 6, dz, Material.SEA_LANTERN);
                    c.set(dx, Y + 7, dz, Material.END_ROD);
                } else for (int y = Y + 1; y <= Y + 3; y++) c.set(dx, y, dz, Material.LIGHT_BLUE_STAINED_GLASS);
            }
        });
        // Réacteur sous la coque.
        c.etape(() -> {
            for (int y = Y - 66; y <= Y - 58; y++)
                for (int dx = -2; dx <= 2; dx++)
                    for (int dz = -2; dz <= 2; dz++)
                        if (dx * dx + dz * dz <= 5) c.set(dx, y, dz, (y % 2 == 0) ? Material.LIGHT_BLUE_STAINED_GLASS : Material.SEA_LANTERN);
        });

        // Cœur : balise sur base d'or, colonnes autour.
        c.etape(() -> {
            c.set(0, Y + 1, 0, Material.BEACON);
            c.set(0, Y + 2, 0, Material.LIGHT_BLUE_STAINED_GLASS);
            for (int i = 0; i < 10; i++) {
                double a = Math.toRadians(i * 36 + 18);
                int x = (int) Math.round(Math.cos(a) * 13), z = (int) Math.round(Math.sin(a) * 13);
                for (int y = Y + 1; y <= Y + 8; y++) c.set(x, y, z, y % 2 == 0 ? Material.GOLD_BLOCK : Material.LIGHT_BLUE_CONCRETE);
                c.set(x, Y + 9, z, Material.SEA_LANTERN);
                c.set(x, Y + 10, z, Material.END_ROD);
            }
        });
        holo(c, b.id, 0.5, Y + 8, 0.5, "<gradient:#62CFF4:#E0B864><bold>ORBIT TYCOON</bold></gradient>\n"
                + "<gray>Traverse un anneau-portail\n<gray>ou utilise ton <#E0B864>Navigateur orbital</#E0B864>.", 2.2f);

        // Planète de verre et trois orbites, très haut au-dessus du cœur (le rayon de la balise la traverse).
        int py = Y + 40;
        c.etape(() -> {
            for (int dx = -8; dx <= 8; dx++)
                for (int dy = -8; dy <= 8; dy++)
                    for (int dz = -8; dz <= 8; dz++) {
                        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                        if (d > 8.3) continue;
                        Material m = (Math.abs(dy) <= 0 && d >= 6.5) ? Material.GOLD_BLOCK
                                : (d < 4 ? Material.CYAN_STAINED_GLASS : Material.LIGHT_BLUE_STAINED_GLASS);
                        c.set(dx, py + dy, dz, m);
                    }
        });
        anneauIncline(c, 0, py, 0, 22, Math.toRadians(20), 0, Material.GOLD_BLOCK, Material.LIGHT_BLUE_STAINED_GLASS);
        anneauIncline(c, 0, py, 0, 29, Math.toRadians(-15), Math.toRadians(60), Material.LIGHT_BLUE_STAINED_GLASS, Material.GOLD_BLOCK);
        anneauIncline(c, 0, py, 0, 36, Math.toRadians(8), Math.toRadians(120), Material.GOLD_BLOCK, Material.SEA_LANTERN);

        // Lampadaires le long des avenues.
        c.etape(() -> {
            for (double a : angles)
                for (int r = 16; r < Zone.PORTAIL_BASE - 10; r += 12)
                    for (int s = -1; s <= 1; s += 2) {
                        int x = (int) Math.round(Math.cos(a) * r - Math.sin(a) * 6.5 * s);
                        int z = (int) Math.round(Math.sin(a) * r + Math.cos(a) * 6.5 * s);
                        lampadaire(c, x, Y, z);
                    }
        });

        // Jardins (entre les avenues, anneau intérieur) et pavillons (anneau extérieur).
        for (int i = 0; i < 5; i++) {
            double a = Math.toRadians(Zone.farms().get(i).angle + 36);
            jardin(c, (int) Math.round(Math.cos(a) * 63), (int) Math.round(Math.sin(a) * 63), 13, 7000L + i);
            int px = (int) Math.round(Math.cos(a) * 97), pz = (int) Math.round(Math.sin(a) * 97);
            pavillon(c, px, pz, -Math.cos(a), -Math.sin(a), PAVILLONS[i]);
        }

        // Anneaux-portails vers les 5 zones.
        for (Zone z : Zone.farms()) {
            double[] p = z.portailBase();
            int ox = (int) Math.floor(p[0]), oz = (int) Math.floor(p[2]);
            disque(c, ox, oz, 9, (dx, dz, d) -> {
                Material m = d > 8.2 ? Material.GOLD_BLOCK : d > 7.2 ? Material.LIGHT_BLUE_CONCRETE : Material.POLISHED_DEEPSLATE;
                c.set(ox + dx, Y, oz + dz, m);
            });
            anneauPortail(c, p, b.id, z.nom + "\n<gray>Entre dans l'anneau");
        }
    }

    private static boolean anneau(double d, double r) { return Math.abs(d - r) < 0.6; }

    private void jardin(Chantier c, int cx, int cz, int r, long graine) {
        Material[] fleurs = {Material.CORNFLOWER, Material.BLUE_ORCHID, Material.AZURE_BLUET, Material.LILY_OF_THE_VALLEY,
                Material.OXEYE_DAISY, Material.DANDELION, Material.ALLIUM};
        disque(c, cx, cz, r, (dx, dz, d) -> {
            int x = cx + dx, z = cz + dz;
            if (d > r - 0.8) { c.set(x, Y, z, Material.GOLD_BLOCK); c.set(x, Y + 1, z, Material.LIGHT_BLUE_STAINED_GLASS); return; }
            if (d > 3 && d < 4.6) { c.set(x, Y, z, Material.WATER); c.set(x, Y - 1, z, Material.SEA_LANTERN); return; }
            c.set(x, Y, z, Material.GRASS_BLOCK);
            c.set(x, Y - 1, z, Material.DIRT);
            int h = hash(x, 7, z);
            if (d > 5 && h < 280) c.set(x, Y + 1, z, fleurs[h % fleurs.length]);
            else if (d > 5 && h < 330) c.set(x, Y + 1, z, Material.FLOWERING_AZALEA);
        });
        arbre(c, cx, Y, cz, TreeType.AZALEA, graine, Material.ROOTED_DIRT);
    }

    private void pavillon(Chantier c, int cx, int cz, double ox, double oz, String nom) {
        disque(c, cx, cz, 10, (dx, dz, d) -> c.set(cx + dx, Y, cz + dz, d > 9 ? Material.GOLD_BLOCK : Material.SMOOTH_QUARTZ));
        dome(c, cx, Y + 1, cz, 9, ox, oz);
        c.etape(() -> {
            c.set(cx, Y + 1, cz, Material.GOLD_BLOCK);
            c.set(cx, Y + 2, cz, Material.SEA_LANTERN);
        });
        holo(c, Zone.BASE.id, cx + 0.5, Y + 4.2, cz + 0.5, nom, 1.4f);
    }
}
