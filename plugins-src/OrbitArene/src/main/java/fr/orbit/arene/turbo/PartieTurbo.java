package fr.orbit.arene.turbo;

import fr.orbit.arene.Jeu;
import fr.orbit.arene.OrbitArene;
import fr.orbit.arene.Partie;
import fr.orbit.arene.Txt;
import io.papermc.paper.entity.TeleportFlag;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Color;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Turbo-Sphère (mécanique « foot en voitures ») : 1 contre 1 ou 2 contre 2, bots pour compléter. Chaque joueur
 * est assis dans un bolide (support ItemDisplay + carrosserie en BlockDisplay) entièrement simulé par le serveur :
 * Z accélérer, S freiner / reculer, la souris donne la direction, Espace sauter (deuxième Espace en l'air =
 * esquive), Ctrl (sprint) = boost, aussi en l'air dans la direction du regard. Gros ballon simulé (gravité,
 * rebonds), buts dans les cages, chrono puis but en or.
 */
public final class PartieTurbo extends Partie {

    // terrain (demi-largeur x, demi-longueur z, hauteur), cages
    static final double HW = 18, HL = 30, HT = 14, GW = 5, GH = 5, GD = 4;
    static final double RB = 1.5, RC = 1.05;
    static final double G_CAR = 0.045, G_BALL = 0.03;
    static final double ACC = 0.028, VMAX = 0.62, ACC_BOOST = 0.05, VMAX_BOOST = 0.98;

    final class Voiture {
        final int equipe;          // 0 = bleus (défendent z négatif), 1 = or
        final Player p;
        Mannequin bot;
        final String nom;
        double x, y, z, vx, vy, vz, yaw, boost = 34;
        boolean auSol = true, esquive, parti;
        int airTicks, buts, touches, arrets;
        Input in;
        ItemDisplay siege;
        final List<BlockDisplay> pieces = new ArrayList<>();
        // bot
        boolean attaquant;

        Voiture(int equipe, Player p, String nom) {
            this.equipe = equipe;
            this.p = p;
            this.nom = nom;
        }

        int sensAttaque() {
            return equipe == 0 ? 1 : -1;
        }

        double vitesseSol() {
            return Math.hypot(vx, vz);
        }

        String hex() {
            return equipe == 0 ? "#3A7FFF" : "#E0B864";
        }
    }

    static final class Pastille {
        final double x, z;
        int recharge;
        BlockDisplay d;

        Pastille(double x, double z) {
            this.x = x;
            this.z = z;
        }
    }

    private final SplittableRandom rnd = new SplittableRandom();
    private final List<Voiture> voitures = new ArrayList<>();
    private final List<Pastille> pastilles = new ArrayList<>();
    private double bx, by, bz, bvx, bvy, bvz;
    private final Quaternionf spin = new Quaternionf();
    private List<BlockDisplay> ballon;
    private Voiture dernierToucheur, avantDernier;
    private final int[] score = new int[2];
    private final double fcx, fcz;
    private int pause = 80, reste, prolongation = -1, celebration;
    private boolean fini;
    private BossBar barre;

    public PartieTurbo(OrbitArene pl, Jeu.Mode mode, int slot) {
        super(pl, mode, slot);
        fcx = ox + HW + 6;
        fcz = oz + HL + GD + 6;
        reste = pl.getConfig().getInt("turbo.duree", 180) * 20;
    }

    @Override
    protected int[] taille() {
        return new int[]{(int) (2 * HW + 12), (int) (2 * (HL + GD) + 12)};
    }

    @Override
    public boolean assis(Player p) {
        for (Voiture v : voitures) {
            if (v.p == p && !v.parti) {
                return true;
            }
        }
        return false;
    }

    private Location monde(double x, double y, double z) {
        return new Location(w, fcx + x, Y0 + y, fcz + z);
    }

    // ------------------------------------------------------------------ terrain

    private void construire() {
        int x0 = (int) Math.floor(fcx - HW), x1 = (int) Math.ceil(fcx + HW) - 1;
        int z0 = (int) Math.floor(fcz - HL), z1 = (int) Math.ceil(fcz + HL) - 1;
        int gx0 = (int) Math.floor(fcx - GW), gx1 = (int) Math.ceil(fcx + GW) - 1;
        int top = (int) (Y0 + HT);
        for (int x = x0 - 1; x <= x1 + 1; x++) {
            for (int z = z0 - (int) GD - 1; z <= z1 + (int) GD + 1; z++) {
                boolean dansTerrain = x >= x0 && x <= x1 && z >= z0 && z <= z1;
                boolean dansCage = x >= gx0 && x <= gx1 && (z < z0 || z > z1) && z >= z0 - GD && z <= z1 + GD;
                boolean murX = (x == x0 - 1 || x == x1 + 1) && z >= z0 && z <= z1;
                boolean murZ = (z == z0 - 1 || z == z1 + 1) && x >= x0 && x <= x1;
                boolean cageBord = !dansCage && (x == gx0 - 1 || x == gx1 + 1) && x >= x0 && x <= x1 && (z < z0 || z > z1)
                        && z >= z0 - GD - 1 && z <= z1 + GD + 1;
                boolean cageFond = (z == z0 - GD - 1 || z == z1 + GD + 1) && x >= gx0 - 1 && x <= gx1 + 1;
                for (int y = Y0 - 1; y <= top; y++) {
                    w.getBlockAt(x, y, z).setType(Material.AIR, false);
                }
                if (dansTerrain || dansCage) {
                    int rx = x - (int) fcx, rz = z - (int) fcz;
                    Material sol;
                    if (rz == 0 || rz == -1 || (Math.abs(Math.hypot(rx + 0.5, rz + 0.5) - 7) < 0.6)) {
                        sol = Material.LIGHT_BLUE_STAINED_GLASS;
                    } else if (dansCage) {
                        sol = z < z0 ? Material.BLUE_CONCRETE : Material.YELLOW_CONCRETE;
                    } else if (Math.abs(rz) > HL - 6 && Math.abs(rx) < GW + 3) {
                        sol = rz < 0 ? Material.BLUE_TERRACOTTA : Material.YELLOW_TERRACOTTA;
                    } else {
                        sol = ((x >> 2) + (z >> 2)) % 2 == 0 ? Material.BLACK_CONCRETE : Material.GRAY_CONCRETE;
                    }
                    w.getBlockAt(x, Y0 - 1, z).setType(sol, false);
                    w.getBlockAt(x, top, z).setType(Material.BARRIER, false);
                    if (dansCage) {
                        for (int y = Y0 + (int) GH; y < top; y++) {
                            w.getBlockAt(x, y, z).setType(Material.BARRIER, false);
                        }
                        w.getBlockAt(x, Y0 + (int) GH, z).setType(z < z0 ? Material.BLUE_STAINED_GLASS : Material.YELLOW_STAINED_GLASS, false);
                    }
                } else if (murX || murZ || cageBord || cageFond) {
                    boolean ouverture = murZ && x >= gx0 && x <= gx1;
                    w.getBlockAt(x, Y0 - 1, z).setType(Material.SEA_LANTERN, false);
                    for (int y = Y0; y <= top; y++) {
                        if (ouverture && y < Y0 + GH) {
                            continue;
                        }
                        Material m = y - Y0 < 3 ? ((murZ || cageFond || cageBord) && (z < fcz) ? Material.BLUE_STAINED_GLASS
                                : (murZ || cageFond || cageBord) ? Material.YELLOW_STAINED_GLASS : Material.LIGHT_BLUE_STAINED_GLASS)
                                : Material.BARRIER;
                        w.getBlockAt(x, y, z).setType(m, false);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ départ

    @Override
    protected void demarrer(List<Player> joueurs) {
        construire();
        boolean deux = mode.id().equals("2v2");
        int parEquipe = deux ? 2 : 1;
        int[] compte = new int[2];
        int i = 0;
        for (Player p : joueurs) {
            int eq = i % 2;
            voitures.add(new Voiture(eq, p, p.getName()));
            compte[eq]++;
            i++;
        }
        int k = 0;
        for (int eq = 0; eq < 2; eq++) {
            while (compte[eq] < parEquipe) {
                Voiture v = new Voiture(eq, null, BOTS[(slot * 4 + k++ + 2) % BOTS.length]);
                voitures.add(v);
                compte[eq]++;
            }
        }
        for (Voiture v : voitures) {
            carrosserie(v);
            Location seat = monde(v.x, v.y + 0.55 + pl.getConfig().getDouble("turbo.decalage-siege", 0.35), v.z);
            v.siege = siege(seat);
            if (v.p != null) {
                teleporter(v.p, seat.clone().add(0, 0.3, 0));
                asseoir(v.siege, v.p);
                boutonQuitter(v.p);
            } else {
                Color c = Color.fromRGB(v.equipe == 0 ? 0x3A7FFF : 0xE0B864);
                v.bot = pantin(seat, v.nom, c, null);
                asseoir(v.siege, v.bot);
            }
        }
        ballon = boule(monde(0, RB, 0), Material.WHITE_CONCRETE, Material.LIGHT_GRAY_CONCRETE, (float) (2 * RB), 2);
        for (BlockDisplay d : ballon) {
            d.setGlowing(true);
            d.setGlowColorOverride(Color.fromRGB(0x62CFF4));
        }
        double[][] pos = {{-(HW - 4), 0}, {HW - 4, 0}, {-(HW - 4), -(HL - 8)}, {HW - 4, -(HL - 8)}, {-(HW - 4), HL - 8}, {HW - 4, HL - 8},
                {0, -(HL - 14)}, {0, HL - 14}};
        for (double[] p : pos) {
            Pastille pa = new Pastille(p[0], p[1]);
            pa.d = bloc(monde(p[0], 0.1, p[1]), Material.ORANGE_STAINED_GLASS.createBlockData(), 1.4f, 0.2f, 1.4f,
                    new Quaternionf().rotateY((float) Math.PI / 4), 0);
            pa.d.setGlowing(true);
            pa.d.setGlowColorOverride(Color.fromRGB(0xFF8A3D));
            pastilles.add(pa);
        }
        barre = BossBar.bossBar(Txt.mm(""), 1f, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
        for (Player p : joueurs) {
            p.showBossBar(barre);
            spectateurs.add(p);
        }
        engagement();
        for (Player p : joueurs) {
            Txt.send(p, mode.jeu().grad("<b>TURBO-SPHÈRE</b>") + " · <#E6EDF3>Z</#E6EDF3> accélérer, <#E6EDF3>S</#E6EDF3> freiner/reculer, "
                    + "<#E6EDF3>souris</#E6EDF3> pour tourner, <#E6EDF3>Espace</#E6EDF3> sauter (deux fois : esquive), "
                    + "<#E6EDF3>Ctrl</#E6EDF3> boost. Les pastilles orange rechargent le boost.");
        }
    }

    private void carrosserie(Voiture v) {
        Location at = monde(0, 0, 0);
        Material coque = v.equipe == 0 ? Material.BLUE_CONCRETE : Material.YELLOW_CONCRETE;
        piece(v, at, coque.createBlockData(), 1.5f, 0.5f, 2.4f, 0, 0.55f, 0);
        piece(v, at, Material.TINTED_GLASS.createBlockData(), 1.2f, 0.45f, 1.0f, 0, 1.02f, -0.25f);
        piece(v, at, (v.equipe == 0 ? Material.LIGHT_BLUE_CONCRETE : Material.ORANGE_CONCRETE).createBlockData(), 1.6f, 0.12f, 0.35f, 0, 1.05f, -1.15f);
        piece(v, at, Material.SEA_LANTERN.createBlockData(), 1.2f, 0.15f, 0.08f, 0, 0.62f, 1.22f);
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                piece(v, at, Material.BLACK_CONCRETE.createBlockData(), 0.35f, 0.55f, 0.55f, sx * 0.78f, 0.3f, sz * 0.78f);
            }
        }
    }

    /** Pièce de carrosserie : décalage dans le repère de la voiture (+z = avant), tournée avec l'entité (yaw). */
    private void piece(Voiture v, Location at, BlockData data, float sx, float sy, float sz, float lx, float ly, float lz) {
        BlockDisplay d = bloc(at, data, sx, sy, sz, new Quaternionf(), pl.lissage());
        d.setTransformation(new Transformation(new Vector3f(lx - sx / 2f, ly - sy / 2f, lz - sz / 2f), new Quaternionf(),
                new Vector3f(sx, sy, sz), new Quaternionf()));
        v.pieces.add(d);
    }

    /** Coup d'envoi : ballon au centre, bolides dans leur camp, compte à rebours. */
    private void engagement() {
        bx = 0;
        by = RB;
        bz = 0;
        bvx = bvy = bvz = 0;
        dernierToucheur = avantDernier = null;
        int[] n = new int[2];
        int[] total = new int[2];
        for (Voiture v : voitures) {
            total[v.equipe]++;
        }
        for (Voiture v : voitures) {
            int idx = n[v.equipe]++;
            double side = total[v.equipe] == 1 ? 0 : (idx == 0 ? -6 : 6);
            v.x = side;
            v.z = -v.sensAttaque() * HL * (total[v.equipe] == 1 || idx == 0 ? 0.55 : 0.8);
            v.y = 0;
            v.vx = v.vy = v.vz = 0;
            v.yaw = v.sensAttaque() > 0 ? 0 : 180;
            v.auSol = true;
            v.esquive = false;
            v.boost = Math.max(v.boost, 34);
            placer(v);
            if (v.p != null && !v.parti) {
                v.p.lookAt(fcx, Y0 + 1, fcz, io.papermc.paper.entity.LookAnchor.EYES);
            }
        }
        pause = 60;
        dessinerBallon();
    }

    // ------------------------------------------------------------------ commandes

    @Override
    public void touche(Player p, Input in) {
        for (Voiture v : voitures) {
            if (v.p == p) {
                Input prev = v.in;
                v.in = in;
                if (in.isJump() && (prev == null || !prev.isJump()) && pause == 0 && celebration == 0) {
                    sauter(v);
                }
            }
        }
    }

    private void sauter(Voiture v) {
        if (v.auSol) {
            v.vy = 0.5;
            v.auSol = false;
            v.esquive = true;
            v.airTicks = 0;
            w.playSound(monde(v.x, v.y, v.z), Sound.ENTITY_BREEZE_JUMP, 0.7f, 1.2f);
        } else if (v.esquive && v.airTicks < 30) {
            double rad = Math.toRadians(v.yaw);
            v.vx += -Math.sin(rad) * 0.55;
            v.vz += Math.cos(rad) * 0.55;
            v.vy = Math.max(v.vy, 0.15);
            v.esquive = false;
            w.playSound(monde(v.x, v.y, v.z), Sound.ENTITY_BREEZE_WIND_BURST, 0.8f, 1.4f);
        }
    }

    // ------------------------------------------------------------------ tick

    @Override
    public void tick() {
        t++;
        for (Voiture v : voitures) {
            if (v.p != null && !v.parti) {
                garderAssis(v.siege, v.p);
            }
        }
        if (fini) {
            return;
        }
        if (celebration > 0) {
            celebration--;
            physiqueBallon();
            dessinerBallon();
            if (celebration == 0) {
                engagement();
            }
            return;
        }
        if (pause > 0) {
            pause--;
            if (pause % 20 == 0 && pause > 0) {
                for (Player p : joueursEnLigne()) {
                    Txt.title(p, "<#62CFF4><b>" + pause / 20 + "</b>", prolongation >= 0 ? "<#E0B864>But en or !" : "", 900);
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.2f);
                }
            }
            if (pause == 0) {
                sonTous(Sound.BLOCK_NOTE_BLOCK_PLING, 0.9f, 2f);
            }
            barre();
            return;
        }
        if (prolongation >= 0) {
            if (--prolongation <= 0) {
                terminer();
                return;
            }
        } else if (--reste <= 0) {
            if (score[0] == score[1]) {
                prolongation = pl.getConfig().getInt("turbo.prolongation", 90) * 20;
                for (Player p : joueursEnLigne()) {
                    Txt.title(p, "<#E0B864><b>PROLONGATION</b>", "<#9AA4B2>Le prochain but gagne", 1800);
                }
                engagement();
                return;
            }
            terminer();
            return;
        }
        for (Voiture v : voitures) {
            conduire(v);
        }
        collisionsVoitures();
        physiqueBallon();
        for (Voiture v : voitures) {
            frapper(v);
            placer(v);
            pastilles(v);
        }
        if (verifierBut()) {
            return;
        }
        dessinerBallon();
        for (Pastille pa : pastilles) {
            if (pa.recharge > 0 && --pa.recharge == 0) {
                pa.d.setTransformation(centre(1.4f, 0.2f, 1.4f, new Quaternionf().rotateY((float) Math.PI / 4)));
            }
        }
        if (t % 4 == 0) {
            hud();
            barre();
        }
    }

    private void conduire(Voiture v) {
        boolean gaz, frein, boost;
        double viseYaw;
        float pitch = 0;
        if (v.p != null) {
            Input in = v.parti ? null : v.in;
            gaz = in != null && in.isForward();
            frein = in != null && in.isBackward();
            boost = in != null && in.isSprint() && v.boost > 0;
            viseYaw = v.p.getLocation().getYaw();
            pitch = v.p.getLocation().getPitch();
        } else {
            double[] c = ia(v);
            viseYaw = c[0];
            gaz = c[1] > 0;
            frein = c[1] < 0;
            boost = c[2] > 0 && v.boost > 0;
            if (c[3] > 0) {
                sauter(v);
            }
        }
        // direction : la voiture tourne vers le regard (plus vite quand elle roule)
        double diff = ((viseYaw - v.yaw) % 360 + 540) % 360 - 180;
        double taux = v.auSol ? 4 + 5 * Math.min(1, v.vitesseSol() / 0.3) : 3.5;
        v.yaw += Math.max(-taux, Math.min(taux, diff));
        double rad = Math.toRadians(v.yaw);
        double fx = -Math.sin(rad), fz = Math.cos(rad);
        if (v.auSol) {
            double avant = v.vx * fx + v.vz * fz;
            double lat = -v.vx * fz + v.vz * fx;
            lat *= 0.72;                         // adhérence
            double max = boost ? VMAX_BOOST : VMAX;
            if (boost) {
                avant = Math.min(max, avant + ACC_BOOST);
            } else if (gaz) {
                avant = avant < max ? Math.min(max, avant + ACC) : avant * 0.985;
            } else if (frein) {
                avant = avant > 0 ? Math.max(0, avant - 0.05) : Math.max(-0.3, avant - 0.02);
            } else {
                avant *= 0.975;
            }
            // v = avant·f + lat·g, avec f = (fx, fz) l'avant et g = (-fz, fx) le côté
            v.vx = fx * avant - fz * lat;
            v.vz = fz * avant + fx * lat;
        } else {
            v.airTicks++;
            if (boost) {
                double pr = Math.toRadians(pitch);
                v.vx += fx * Math.cos(pr) * 0.045;
                v.vz += fz * Math.cos(pr) * 0.045;
                v.vy += -Math.sin(pr) * 0.045 + 0.02;
            }
            v.vx *= 0.995;
            v.vz *= 0.995;
        }
        if (boost) {
            v.boost = Math.max(0, v.boost - 1.0);
            if (t % 2 == 0) {
                Location l = monde(v.x - fx * 1.4, v.y + 0.6, v.z - fz * 1.4);
                w.spawnParticle(v.equipe == 0 ? Particle.SOUL_FIRE_FLAME : Particle.FLAME, l, 3, 0.1, 0.1, 0.1, 0.01);
            }
        } else {
            v.boost = Math.min(100, v.boost + 0.1);
        }
        v.vy -= G_CAR;
        double sp = Math.sqrt(v.vx * v.vx + v.vz * v.vz + v.vy * v.vy);
        if (sp > 1.3) {
            v.vx *= 1.3 / sp;
            v.vy *= 1.3 / sp;
            v.vz *= 1.3 / sp;
        }
        v.x += v.vx;
        v.y += v.vy;
        v.z += v.vz;
        if (v.y <= 0) {
            if (!v.auSol && v.vy < -0.4) {
                w.playSound(monde(v.x, 0, v.z), Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 0.5f, 0.8f);
            }
            v.y = 0;
            v.vy = 0;
            v.auSol = true;
            v.esquive = false;
        } else {
            v.auSol = false;
        }
        if (v.y > HT - 2.2) {
            v.y = HT - 2.2;
            v.vy = Math.min(0, v.vy);
        }
        // murs (une voiture peut entrer dans les cages)
        double mx = HW - 0.9;
        if (Math.abs(v.x) > mx) {
            v.x = Math.signum(v.x) * mx;
            v.vx = -v.vx * 0.3;
        }
        boolean cage = Math.abs(v.x) < GW - 0.9 && v.y < GH - 1.6;
        double mz = cage ? HL + GD - 0.9 : HL - 0.9;
        if (Math.abs(v.z) > mz) {
            v.z = Math.signum(v.z) * mz;
            v.vz = -v.vz * 0.3;
        }
        if (Math.abs(v.z) > HL - 0.9 && Math.abs(v.x) > GW - 0.9) {
            v.x = Math.signum(v.x) * (GW - 0.9);
        }
    }

    private void collisionsVoitures() {
        for (int a = 0; a < voitures.size(); a++) {
            for (int b = a + 1; b < voitures.size(); b++) {
                Voiture u = voitures.get(a), v = voitures.get(b);
                double dx = v.x - u.x, dy = v.y - u.y, dz = v.z - u.z, d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (d < 2 * RC && d > 1e-6) {
                    double nx = dx / d, ny = dy / d, nz = dz / d, push = (2 * RC - d) / 2;
                    u.x -= nx * push;
                    u.z -= nz * push;
                    v.x += nx * push;
                    v.z += nz * push;
                    double rv = (v.vx - u.vx) * nx + (v.vy - u.vy) * ny + (v.vz - u.vz) * nz;
                    if (rv < 0) {
                        double j = -rv * 0.8;
                        u.vx -= nx * j;
                        u.vz -= nz * j;
                        v.vx += nx * j;
                        v.vz += nz * j;
                        w.playSound(monde(u.x, u.y, u.z), Sound.ENTITY_IRON_GOLEM_HURT, 0.4f, 1.6f);
                    }
                }
            }
        }
    }

    private void physiqueBallon() {
        bvy -= G_BALL;
        bvx *= 0.997;
        bvy *= 0.997;
        bvz *= 0.997;
        double sp = Math.sqrt(bvx * bvx + bvy * bvy + bvz * bvz);
        if (sp > 1.7) {
            bvx *= 1.7 / sp;
            bvy *= 1.7 / sp;
            bvz *= 1.7 / sp;
        }
        bx += bvx;
        by += bvy;
        bz += bvz;
        double e = 0.65;
        if (by < RB) {
            by = RB;
            if (bvy < -0.12) {
                bvy = -bvy * e;
                rebond(Math.abs(bvy));
            } else {
                bvy = 0;
                bvx *= 0.99;
                bvz *= 0.99;
            }
        }
        if (by > HT - RB) {
            by = HT - RB;
            bvy = -Math.abs(bvy) * e;
            rebond(Math.abs(bvy));
        }
        boolean dansCage = Math.abs(bz) > HL - RB * 0.2;
        double mx = dansCage ? GW - RB : HW - RB;
        if (Math.abs(bx) > mx) {
            bx = Math.signum(bx) * mx;
            bvx = -bvx * e;
            rebond(Math.abs(bvx));
        }
        boolean bouche = Math.abs(bx) <= GW - RB * 0.8 && by <= GH - RB * 0.8;
        if (Math.abs(bz) > HL - RB && !bouche && !dansCage) {
            bz = Math.signum(bz) * (HL - RB);
            bvz = -bvz * e;
            rebond(Math.abs(bvz));
        }
        if (dansCage) {
            if (by > GH - RB) {
                by = GH - RB;
                bvy = -Math.abs(bvy) * e;
            }
            if (Math.abs(bz) > HL + GD - RB) {
                bz = Math.signum(bz) * (HL + GD - RB);
                bvz = -bvz * 0.4;
            }
        }
    }

    private void rebond(double force) {
        if (force > 0.1 && t % 2 == 0) {
            w.playSound(monde(bx, by, bz), Sound.BLOCK_SLIME_BLOCK_FALL, (float) Math.min(1, force + 0.2), 0.9f);
        }
    }

    private void frapper(Voiture v) {
        double cx = v.x, cy = v.y + 0.6, cz = v.z;
        double dx = bx - cx, dy = by - cy, dz = bz - cz, d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d >= RB + RC || d < 1e-6) {
            return;
        }
        double nx = dx / d, ny = dy / d, nz = dz / d;
        bx = cx + nx * (RB + RC);
        by = Math.max(RB, cy + ny * (RB + RC));
        bz = cz + nz * (RB + RC);
        double rvx = bvx - v.vx, rvy = bvy - v.vy, rvz = bvz - v.vz;
        double vn = rvx * nx + rvy * ny + rvz * nz;
        if (vn < 0) {
            bvx -= 1.55 * vn * nx;
            bvy -= 1.55 * vn * ny;
            bvz -= 1.55 * vn * nz;
            double vc = Math.sqrt(v.vx * v.vx + v.vy * v.vy + v.vz * v.vz);
            bvx += nx * 0.12 * vc;
            bvz += nz * 0.12 * vc;
            bvy += (v.auSol ? 0.06 : 0) + Math.max(0, ny) * 0.1 * vc;
            if (dernierToucheur != v) {
                avantDernier = dernierToucheur;
                dernierToucheur = v;
            }
            v.touches++;
            float f = (float) Math.min(1, -vn + 0.2);
            w.playSound(monde(bx, by, bz), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, f, 0.7f);
            if (-vn > 0.5) {
                w.spawnParticle(Particle.CRIT, monde(bx, by, bz), 12, 0.6, 0.6, 0.6, 0.2);
            }
            // arrêt : ballon renvoyé alors qu'il filait vers sa cage
            if (Math.signum(rvz + v.vz) == -v.sensAttaque() && Math.abs(bz + v.sensAttaque() * HL) < 12 && bvz * v.sensAttaque() > 0) {
                v.arrets++;
            }
        }
    }

    private boolean verifierBut() {
        if (Math.abs(bz) < HL + RB * 0.9) {
            return false;
        }
        int equipeQuiMarque = bz > 0 ? 0 : 1;
        score[equipeQuiMarque]++;
        Voiture buteur = dernierToucheur != null && dernierToucheur.equipe == equipeQuiMarque ? dernierToucheur
                : avantDernier != null && avantDernier.equipe == equipeQuiMarque ? avantDernier : null;
        if (buteur != null) {
            buteur.buts++;
        }
        String hex = equipeQuiMarque == 0 ? "#3A7FFF" : "#E0B864";
        Location l = monde(bx, by, bz);
        w.spawnParticle(Particle.FIREWORK, l, 80, 1.5, 1.5, 1.5, 0.15);
        w.spawnParticle(Particle.EXPLOSION, l, 3, 1, 1, 1, 0);
        sonTous(Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 1f, 1f);
        for (Player p : joueursEnLigne()) {
            Txt.title(p, "<" + hex + "><b>BUT !</b>", (buteur != null ? "<" + hex + ">" + buteur.nom + "</" + hex + "> · " : "")
                    + "<#3A7FFF>" + score[0] + "</#3A7FFF> <#5A6270>—</#5A6270> <#E0B864>" + score[1], 2200);
        }
        if (prolongation >= 0) {
            dessinerBallon();
            terminer();
            return true;
        }
        celebration = 60;
        bvx *= 0.3;
        bvz *= 0.3;
        bvy = 0.4;
        return true;
    }

    private void pastilles(Voiture v) {
        if (!v.auSol || v.boost >= 100) {
            return;
        }
        for (Pastille pa : pastilles) {
            if (pa.recharge == 0 && Math.abs(v.x - pa.x) < 1.6 && Math.abs(v.z - pa.z) < 1.6) {
                v.boost = Math.min(100, v.boost + 40);
                pa.recharge = 200;
                pa.d.setTransformation(centre(0.3f, 0.05f, 0.3f, new Quaternionf()));
                if (v.p != null) {
                    v.p.playSound(v.p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.6f, 1.8f);
                }
            }
        }
    }

    // ------------------------------------------------------------------ bots

    /** {yaw visé, gaz (1 / 0 / -1), boost (0/1), saut (0/1)}. */
    private double[] ia(Voiture v) {
        int s = v.sensAttaque();
        // rôles : en 2 contre 2, le plus proche du ballon attaque, l'autre garde la cage
        Voiture coequipier = null;
        for (Voiture o : voitures) {
            if (o != v && o.equipe == v.equipe) {
                coequipier = o;
            }
        }
        boolean attaque = true;
        if (coequipier != null) {
            double dv = Math.hypot(bx - v.x, bz - v.z), dc = Math.hypot(bx - coequipier.x, bz - coequipier.z);
            attaque = dv <= dc;
        }
        double butAdvZ = s * (HL + 1), butMienZ = -s * HL;
        double tx, tz;
        boolean fonce = false;
        if (!attaque && Math.abs(bz - butMienZ) > 16) {
            tx = bx * 0.35;
            tz = butMienZ + s * 5;
        } else {
            // viser derrière le ballon, dans l'axe de la cage adverse
            double ux = 0 - bx, uz = butAdvZ - bz, un = Math.hypot(ux, uz);
            ux /= un;
            uz /= un;
            double px = bx + bvx * 8, pz = bz + bvz * 8;
            double ax = px - ux * 3.2, az = pz - uz * 3.2;
            double rx = bx - v.x, rz = bz - v.z;
            double devant = rx * ux + rz * uz;
            double lat = Math.abs(-rx * uz + rz * ux);
            if (devant > 0.5 && lat < 2.2 + devant * 0.25) {
                tx = px;
                tz = pz;
                fonce = true;
            } else {
                tx = ax;
                tz = az;
                // ne pas pousser le ballon vers sa propre cage : contourner par le côté
                if (devant < 0 && Math.hypot(rx, rz) < 5) {
                    tx += (v.x < bx ? -4 : 4);
                }
            }
        }
        tx = Math.max(-HW + 1.5, Math.min(HW - 1.5, tx));
        tz = Math.max(-HL - GD + 2, Math.min(HL + GD - 2, tz));
        double dx = tx - v.x, dz = tz - v.z, d = Math.hypot(dx, dz);
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        double diff = Math.abs(((yaw - v.yaw) % 360 + 540) % 360 - 180);
        double gaz = diff > 120 && d < 6 ? -1 : 1;
        double boost = (fonce && d > 6 && diff < 25) || (d > 25 && diff < 15) ? 1 : 0;
        double hb = Math.hypot(bx - v.x, bz - v.z);
        double saut = v.auSol && fonce && hb < 4 && by > 2.6 && by < 6 && rnd.nextInt(3) == 0 ? 1 : 0;
        return new double[]{yaw, gaz * (d < 1.5 && !fonce ? 0 : 1), boost, saut};
    }

    // ------------------------------------------------------------------ affichage

    private void placer(Voiture v) {
        Location l = monde(v.x, v.y, v.z);
        l.setYaw((float) v.yaw);
        for (BlockDisplay d : v.pieces) {
            d.teleport(l);
        }
        Location s = monde(v.x, v.y + 0.55 + pl.getConfig().getDouble("turbo.decalage-siege", 0.35), v.z);
        s.setYaw((float) v.yaw);
        if (v.siege != null && v.siege.isValid()) {
            v.siege.teleport(s, TeleportFlag.EntityState.RETAIN_PASSENGERS);
        }
    }

    private void dessinerBallon() {
        Location l = monde(bx, by, bz);
        for (BlockDisplay d : ballon) {
            d.teleport(l);
        }
        double sp = Math.hypot(bvx, bvz);
        if (sp > 0.02 && t % 2 == 0) {
            // roule autour de l'axe horizontal perpendiculaire au déplacement
            spin.premul(new Quaternionf().fromAxisAngleRad((float) (bvz / sp), 0f, (float) (-bvx / sp), (float) (sp * 2 / RB)));
            tourner(ballon, spin, (float) (2 * RB));
        }
    }

    private void hud() {
        for (Voiture v : voitures) {
            if (v.p == null || v.parti) {
                continue;
            }
            int n = (int) Math.round(v.boost / 10);
            String jauge = "<#FF8A3D>" + "▮".repeat(n) + "</#FF8A3D><#3A3F4E>" + "▮".repeat(10 - n) + "</#3A3F4E>";
            Txt.bar(v.p, "<" + v.hex() + "><b>" + (v.equipe == 0 ? "BLEUS" : "OR") + "</b></" + v.hex() + "> <#5A6270>·</#5A6270> BOOST "
                    + jauge + " <#E6EDF3>" + (int) v.boost + "</#E6EDF3> <#5A6270>·</#5A6270> <#9AA4B2>"
                    + Math.round(v.vitesseSol() * 20 * 3.6) + " km/h");
        }
    }

    private void barre() {
        if (barre == null) {
            return;
        }
        String temps = prolongation >= 0 ? "<#E0B864>BUT EN OR " + Txt.chrono(prolongation) : Txt.chrono(reste);
        barre.name(Txt.mm("<#3A7FFF><b>BLEUS " + score[0] + "</b></#3A7FFF> <#5A6270>—</#5A6270> <#E0B864><b>" + score[1]
                + " OR</b></#E0B864>   <#E6EDF3>" + temps));
        float total = pl.getConfig().getInt("turbo.duree", 180) * 20f;
        barre.progress(prolongation >= 0 ? 1f : Math.max(0f, Math.min(1f, reste / total)));
    }

    // ------------------------------------------------------------------ fin

    private void terminer() {
        if (fini) {
            return;
        }
        fini = true;
        for (Voiture v : voitures) {
            if (v.p == null || v.parti || !v.p.isOnline()) {
                continue;
            }
            v.p.hideBossBar(barre);
            int mien = score[v.equipe], autre = score[1 - v.equipe];
            boolean gagne = mien > autre, nul = mien == autre;
            int pts = v.buts * 100 + Math.min(40, v.touches) * 5 + v.arrets * 40 + (gagne ? 200 : nul ? 60 : 0);
            int oc = v.buts * 20 + Math.min(40, v.touches) + v.arrets * 8 + (gagne ? 80 : nul ? 30 : 0);
            resultat(v.p, pts, oc, gagne ? "<#7CFF8A><b>Victoire !</b>" : nul ? "<#E6EDF3><b>Match nul</b>" : "<#E0484A><b>Défaite</b>",
                    mien + " — " + autre + " · " + v.buts + " but(s), " + v.touches + " touche(s)");
        }
        finir();
    }

    @Override
    protected void retirer(Player p) {
        for (Voiture v : voitures) {
            if (v.p == p) {
                v.parti = true;
                v.in = null;
                p.hideBossBar(barre);
                if (v.siege != null) {
                    v.siege.eject();
                }
            }
        }
    }

    @Override
    protected void nettoyer() {
        fini = true;
        if (barre != null) {
            for (Player p : spectateurs) {
                p.hideBossBar(barre);
            }
        }
        spectateurs.clear();
    }

    private final List<Player> spectateurs = new ArrayList<>();
}
