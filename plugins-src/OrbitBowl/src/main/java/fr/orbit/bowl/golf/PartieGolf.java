package fr.orbit.bowl.golf;

import fr.orbit.bowl.Activite;
import fr.orbit.bowl.Jeu;
import fr.orbit.bowl.Mode;
import fr.orbit.bowl.OrbitBowl;
import fr.orbit.bowl.Poste;
import fr.orbit.bowl.Salle;
import fr.orbit.bowl.Txt;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Golf des Astéroïdes : 9 trous, chacun joue sa balle à son rythme (accroupi = charger, relâcher = frapper dans la
 * direction du regard). Points par trou : trou en un 10, deux sous le par 8, un sous le par 6, par 4, +1 : 2, +2 : 1.
 * Six coups maximum par trou.
 */
public final class PartieGolf extends Activite {

    private static final double R = 0.12, R_AST = 0.42, DEMI_SAT = 1.3, W_SAT = 1.1, CHAMP = 7.0;
    private static final int SOUS_PAS = 5, MAX_COUPS = 6, PAUSE = 70;
    private static final String[] BALLES = {"golf_bleu", "golf_or", "golf_rose", "golf_vert"};
    private static final String[] COULEURS = {"#3A7FFF", "#E0B864", "#FF4FD8", "#7CFF8A"};

    private static final class Balle {
        final Player p;
        final int n;
        double x, z, vx, vz;
        boolean bouge, rentre, coupe;
        int coups, charge = -1, roule, total, coupsTotal, enUn;
        ItemDisplay d;

        Balle(Player p, int n) {
            this.p = p;
            this.n = n;
        }
    }

    private final Map<UUID, Balle> balles = new LinkedHashMap<>();
    private final Random rnd = new Random();
    private final List<BlockDisplay> satellite = new ArrayList<>();
    private final List<Object[]> pieces = new ArrayList<>();
    private int trou, pause;
    private final List<double[]> asteroides = new ArrayList<>();

    public PartieGolf(OrbitBowl pl, Poste poste, Mode mode, long mise) {
        super(pl, poste, mode, mise);
    }

    @Override
    protected void equiper(Player p) {
        p.getInventory().setItem(0, objet(Material.STICK, "putter", "<#7CFF8A><b>Putter stellaire</b>",
                "Regarde où tu veux envoyer la balle", "Accroupi : charger · relâcher : frapper", "Six coups maximum par trou"));
        p.getInventory().setHeldItemSlot(0);
    }

    @Override
    protected void accueillir(Player p) {
        Txt.send(p, "<#9AA4B2>Golf des Astéroïdes : 9 trous. Accroupis-toi près de ta balle pour charger, relâche pour frapper.");
    }

    @Override
    protected void commencer() {
        int n = 0;
        for (Player p : joueurs) {
            Balle b = new Balle(p, n++);
            b.d = sprite(new Location(w, 0, Salle.Y, 0), Material.SNOWBALL, BALLES[b.n % BALLES.length], 0.32f, true);
            balles.put(p.getUniqueId(), b);
        }
        installer(0);
    }

    // ------------------------------------------------------------------ trous

    private void installer(int k) {
        trou = k;
        asteroides.clear();
        for (int r = 0; r < Golf.H; r++) {
            for (int c = 0; c < Golf.L; c++) {
                if (Golf.cell(k, c, r) == 'A') {
                    asteroides.add(new double[]{Golf.x0(k) + c + 0.5, Golf.z0(k) + r + 0.5});
                }
            }
        }
        for (BlockDisplay d : satellite) {
            lacher(d);
        }
        satellite.clear();
        pieces.clear();
        double[] s = Golf.trouve(k, 'S');
        if (s != null) {
            poserSatellite(s);
        }
        double[] tee = Golf.trouve(k, 'T');
        int i = 0;
        for (Balle b : balles.values()) {
            // départ légèrement décalé pour que les balles ne se cachent pas l'une l'autre
            b.x = tee[0] + (i % 2 == 0 ? -0.22 : 0.22) * (i / 2 == 0 ? 1 : 0.5);
            b.z = tee[1] + (i / 2) * 0.25 - 0.12;
            b.vx = b.vz = 0;
            b.bouge = b.rentre = false;
            b.coups = 0;
            b.charge = -1;
            b.d.setItemStack(objet(Material.SNOWBALL, BALLES[b.n % BALLES.length], ""));
            b.d.teleport(lieu(b));
            pl.teleporter(b.p, new Location(w, tee[0], Salle.Y, tee[1] + 1.1, 180f, 25f));
            i++;
        }
        titre("<gradient:#7CFF8A:#62CFF4><b>TROU " + (k + 1) + "</b></gradient>", "<#E6EDF3>" + Golf.NOMS[k] + " <#5A6270>·</#5A6270> <#E0B864>par "
                + Golf.PARS[k], 1800);
        for (Player p : joueurs) {
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, 1.2f);
        }
    }

    private void poserSatellite(double[] s) {
        Location at = new Location(w, s[0], Salle.Y + 0.04, s[1]);
        Object[][] modeles = {
                {Material.IRON_BLOCK, new Vector3f(-1.3f, 0f, -0.08f), new Vector3f(2.6f, 0.16f, 0.16f)},
                {Material.LIGHT_BLUE_STAINED_GLASS, new Vector3f(-1.35f, 0.02f, -0.32f), new Vector3f(0.55f, 0.06f, 0.64f)},
                {Material.LIGHT_BLUE_STAINED_GLASS, new Vector3f(0.8f, 0.02f, -0.32f), new Vector3f(0.55f, 0.06f, 0.64f)},
                {Material.GOLD_BLOCK, new Vector3f(-0.2f, 0f, -0.2f), new Vector3f(0.4f, 0.3f, 0.4f)}};
        for (Object[] pc : modeles) {
            BlockDisplay d = garder(w.spawn(at, BlockDisplay.class, e -> {
                OrbitBowl.marquer(e);
                e.setBlock(((Material) pc[0]).createBlockData());
                e.setBrightness(new Display.Brightness(15, 15));
                e.setTransformation(new Transformation((Vector3f) pc[1], new Quaternionf(), (Vector3f) pc[2], new Quaternionf()));
            }));
            satellite.add(d);
            pieces.add(pc);
        }
    }

    private double angleSatellite() {
        return W_SAT * t / 20.0;
    }

    private void tournerSatellite() {
        float a = (float) angleSatellite();
        Quaternionf q = new Quaternionf().rotateY(-a);
        for (int i = 0; i < satellite.size(); i++) {
            BlockDisplay d = satellite.get(i);
            Object[] pc = pieces.get(i);
            Vector3f tr = q.transform(new Vector3f((Vector3f) pc[1]));
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(1);
            d.setTransformation(new Transformation(tr, new Quaternionf(q), (Vector3f) pc[2], new Quaternionf()));
        }
    }

    private Location lieu(Balle b) {
        return new Location(w, b.x, Salle.Y + R + 0.02, b.z);
    }

    // ------------------------------------------------------------------ boucle

    @Override
    protected void tick() {
        if (!satellite.isEmpty()) {
            tournerSatellite();
        }
        if (pause > 0) {
            if (--pause == 0) {
                if (trou + 1 >= 9) {
                    finPartie();
                } else {
                    installer(trou + 1);
                }
            }
            return;
        }
        boolean tous = true;
        for (Balle b : balles.values()) {
            if (b.bouge) {
                rouler(b);
            } else if (!b.rentre) {
                viser(b);
            }
            tous &= b.rentre;
        }
        if (tous && !balles.isEmpty()) {
            bilanTrou();
        }
    }

    private boolean proche(Balle b) {
        Location l = b.p.getLocation();
        return Math.hypot(l.getX() - b.x, l.getZ() - b.z) < 3.2;
    }

    private void viser(Balle b) {
        Player p = b.p;
        if (b.charge >= 0) {
            double f = vaEtVient(t - b.charge, 40);
            Txt.bar(p, "<#7CFF8A>Puissance</#7CFF8A> " + jauge(f, f > 0.85 ? "#E0484A" : "#7CFF8A") + " <#9AA4B2>relâche pour frapper");
        } else if (t % 10 == 0) {
            Txt.bar(p, "<#7CFF8A>Trou " + (trou + 1) + "/9</#7CFF8A> <#5A6270>·</#5A6270> <#E6EDF3>coup " + (b.coups + 1) + "</#E6EDF3> <#5A6270>·</#5A6270> par "
                    + Golf.PARS[trou] + " <#5A6270>·</#5A6270> <#E0B864>" + b.total + " pts" + (proche(b) ? "" : " <#E0484A>· approche-toi de ta balle"));
        }
        if (t % 3 == 0 && proche(b)) {
            double[] d = direction(p);
            for (int i = 1; i <= 6; i++) {
                p.spawnParticle(Particle.DUST, new Location(w, b.x + d[0] * i * 0.45, Salle.Y + 0.08, b.z + d[1] * i * 0.45), 1, 0, 0, 0, 0,
                        new Particle.DustOptions(Color.fromRGB(0xE0B864), 0.45f));
            }
        }
    }

    private static double[] direction(Player p) {
        Vector v = p.getLocation().getDirection();
        double n = Math.hypot(v.getX(), v.getZ());
        if (n < 1e-3) {
            double yaw = Math.toRadians(p.getLocation().getYaw());
            return new double[]{-Math.sin(yaw), Math.cos(yaw)};
        }
        return new double[]{v.getX() / n, v.getZ() / n};
    }

    @Override
    public void accroupi(Player p, boolean bas) {
        Balle b = balles.get(p.getUniqueId());
        if (b == null || pause > 0 || b.bouge || b.rentre) {
            return;
        }
        if (bas) {
            if (proche(b)) {
                b.charge = t;
            }
            return;
        }
        if (b.charge < 0) {
            return;
        }
        double f = vaEtVient(t - b.charge, 40);
        b.charge = -1;
        double[] d = direction(p);
        double v = 1.2 + f * 11.8;
        if (f > 0.85) {
            // trop fort : la frappe part un peu de travers
            double ecart = rnd.nextGaussian() * 0.07 * (f - 0.85) / 0.15;
            double c = Math.cos(ecart), s = Math.sin(ecart);
            d = new double[]{d[0] * c - d[1] * s, d[0] * s + d[1] * c};
        }
        b.vx = d[0] * v;
        b.vz = d[1] * v;
        b.bouge = true;
        b.roule = 0;
        b.coups++;
        b.coupsTotal++;
        son(lieu(b), Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, 1f, 0.8f + (float) f * 0.6f);
        son(lieu(b), Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, 1.6f);
    }

    // ------------------------------------------------------------------ physique

    private boolean solide(double x, double z) {
        int c = (int) Math.floor(x - Golf.x0(trou)), r = (int) Math.floor(z - Golf.z0(trou));
        char ch = Golf.cell(trou, c, r);
        return ch == '#' || ch == ' ';
    }

    private char sous(double x, double z) {
        return Golf.cell(trou, (int) Math.floor(x - Golf.x0(trou)), (int) Math.floor(z - Golf.z0(trou)));
    }

    private void rouler(Balle b) {
        b.roule++;
        double h = 0.05 / SOUS_PAS;
        boolean choc = false;
        for (int s = 0; s < SOUS_PAS && b.bouge; s++) {
            char ch = sous(b.x, b.z);
            switch (ch) {
                case '>' -> b.vx += CHAMP * h;
                case '<' -> b.vx -= CHAMP * h;
                case '^' -> b.vz -= CHAMP * h;
                case 'v' -> b.vz += CHAMP * h;
                default -> {
                }
            }
            double v = Math.hypot(b.vx, b.vz);
            if (v > 1e-6) {
                double nv = Math.max(0, v - (1.6 + 0.6 * v) * h);
                b.vx *= nv / v;
                b.vz *= nv / v;
            }
            // murs, axe par axe
            double nx = b.x + b.vx * h;
            if (solide(nx + Math.signum(b.vx) * R, b.z)) {
                b.vx = -b.vx * 0.78;
                choc |= Math.abs(b.vx) > 0.8;
            } else {
                b.x = nx;
            }
            double nz = b.z + b.vz * h;
            if (solide(b.x, nz + Math.signum(b.vz) * R)) {
                b.vz = -b.vz * 0.78;
                choc |= Math.abs(b.vz) > 0.8;
            } else {
                b.z = nz;
            }
            // astéroïdes
            for (double[] a : asteroides) {
                double dx = b.x - a[0], dz = b.z - a[1], d = Math.hypot(dx, dz);
                if (d < R_AST + R && d > 1e-6) {
                    double nxa = dx / d, nza = dz / d, vn = b.vx * nxa + b.vz * nza;
                    if (vn < 0) {
                        b.vx -= 1.85 * vn * nxa;
                        b.vz -= 1.85 * vn * nza;
                        choc |= -vn > 0.8;
                    }
                    b.x = a[0] + nxa * (R_AST + R);
                    b.z = a[1] + nza * (R_AST + R);
                }
            }
            if (!satellite.isEmpty()) {
                choc |= heurterSatellite(b);
            }
            // trou de ver
            if (ch == 'W') {
                double[] e = Golf.trouve(trou, 'W'), o = Golf.trouve(trou, 'w');
                if (o != null && Math.hypot(b.x - e[0], b.z - e[1]) < 0.4) {
                    w.spawnParticle(Particle.PORTAL, lieu(b), 30, 0.2, 0.2, 0.2, 0.4);
                    son(lieu(b), Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.4f);
                    double sv = Math.max(2.5, Math.hypot(b.vx, b.vz) * 0.7);
                    b.x = o[0];
                    b.z = o[1];
                    double ang = rnd.nextDouble() * Math.PI * 2;
                    b.vx = Math.cos(ang) * sv;
                    b.vz = Math.sin(ang) * sv;
                    w.spawnParticle(Particle.REVERSE_PORTAL, lieu(b), 30, 0.2, 0.2, 0.2, 0.1);
                }
            }
            // trou
            double[] o = Golf.trouve(trou, 'O');
            double dt = Math.hypot(b.x - o[0], b.z - o[1]);
            if (dt < 0.2) {
                double vv = Math.hypot(b.vx, b.vz);
                if (vv < 5.0) {
                    rentrer(b);
                    return;
                }
                if (dt < 0.12) {
                    // trop vite : la balle lèche le trou et repart déviée
                    double ang = (rnd.nextBoolean() ? 1 : -1) * (0.25 + rnd.nextDouble() * 0.3);
                    double c = Math.cos(ang), sn = Math.sin(ang);
                    double vx = (b.vx * c - b.vz * sn) * 0.75, vz = (b.vx * sn + b.vz * c) * 0.75;
                    b.vx = vx;
                    b.vz = vz;
                    son(lieu(b), Sound.BLOCK_METAL_HIT, 0.6f, 1.8f);
                }
            }
        }
        if (choc) {
            son(lieu(b), Sound.BLOCK_STONE_HIT, 0.6f, 1.5f);
        }
        b.d.teleport(lieu(b));
        if (t % 2 == 0) {
            w.spawnParticle(Particle.DUST, lieu(b).add(0, -0.05, 0), 1, 0, 0, 0, 0,
                    new Particle.DustOptions(Color.fromRGB(Integer.parseInt(COULEURS[b.n % 4].substring(1), 16)), 0.4f));
        }
        double v = Math.hypot(b.vx, b.vz);
        boolean champ = "<>^v".indexOf(sous(b.x, b.z)) >= 0;
        if ((v < 0.12 && !champ) || (champ && v < 0.25 && b.roule > 120) || b.roule > 20 * 25) {
            b.vx = b.vz = 0;
            b.bouge = false;
            arret(b);
        }
    }

    private boolean heurterSatellite(Balle b) {
        double[] s = Golf.trouve(trou, 'S');
        double a = angleSatellite(), ux = Math.cos(a), uz = Math.sin(a);
        double rx = b.x - s[0], rz = b.z - s[1];
        double proj = clamp(rx * ux + rz * uz, -DEMI_SAT, DEMI_SAT);
        double px = s[0] + ux * proj, pz = s[1] + uz * proj;
        double dx = b.x - px, dz = b.z - pz, d = Math.hypot(dx, dz);
        double lim = R + 0.1;
        if (d >= lim || d < 1e-6) {
            return false;
        }
        double nx = dx / d, nz = dz / d;
        // vitesse du bras au point de contact (rotation autour de y)
        double bx = -W_SAT * (pz - s[1]), bz = W_SAT * (px - s[0]);
        double rel = (b.vx - bx) * nx + (b.vz - bz) * nz;
        if (rel < 0) {
            b.vx -= 1.8 * rel * nx;
            b.vz -= 1.8 * rel * nz;
        }
        b.x = px + nx * lim;
        b.z = pz + nz * lim;
        return -rel > 0.8;
    }

    private void arret(Balle b) {
        if (b.coups >= MAX_COUPS) {
            b.rentre = true;
            b.coupe = true;
            Txt.title(b.p, "", "<#E0484A>Six coups : trou terminé (0 pt)", 1400);
            annoncerTrou(b, 0, MAX_COUPS + 1);
            return;
        }
        if (!proche(b)) {
            double[] o = Golf.trouve(trou, 'O');
            double dx = b.x - o[0], dz = b.z - o[1], n = Math.max(1e-3, Math.hypot(dx, dz));
            double px = b.x + dx / n * 1.0, pz = b.z + dz / n * 1.0;
            if (solide(px, pz)) {
                px = b.x;
                pz = b.z;
            }
            float yaw = (float) Math.toDegrees(Math.atan2(-(o[0] - px), o[1] - pz));
            pl.teleporter(b.p, new Location(w, px, Salle.Y, pz, yaw, 25f));
        }
    }

    private int points(int coups) {
        int par = Golf.PARS[trou];
        if (coups == 1) {
            return 10;
        }
        int e = coups - par;
        return e <= -2 ? 8 : e == -1 ? 6 : e == 0 ? 4 : e == 1 ? 2 : e == 2 ? 1 : 0;
    }

    private static String nomScore(int coups, int par) {
        if (coups == 1) {
            return "TROU EN UN";
        }
        return switch (coups - par) {
            case -3 -> "ALBATROS";
            case -2 -> "EAGLE";
            case -1 -> "BIRDIE";
            case 0 -> "PAR";
            case 1 -> "BOGEY";
            case 2 -> "DOUBLE BOGEY";
            default -> coups - par < 0 ? "EXPLOIT" : "+" + (coups - par);
        };
    }

    private void rentrer(Balle b) {
        double[] o = Golf.trouve(trou, 'O');
        b.x = o[0];
        b.z = o[1];
        b.vx = b.vz = 0;
        b.bouge = false;
        b.rentre = true;
        b.d.teleport(new Location(w, o[0], Salle.Y - 0.05, o[1]));
        b.d.setItemStack(new org.bukkit.inventory.ItemStack(Material.AIR));
        int pts = points(b.coups);
        b.total += pts;
        String nom = nomScore(b.coups, Golf.PARS[trou]);
        Location at = new Location(w, o[0], Salle.Y + 0.3, o[1]);
        son(at, Sound.BLOCK_NOTE_BLOCK_BELL, 0.9f, 1.4f);
        if (b.coups == 1) {
            b.enUn++;
            w.spawnParticle(Particle.FIREWORK, at.clone().add(0, 0.6, 0), 60, 0.4, 0.6, 0.4, 0.08);
            son(at, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
            titre("<gradient:#E0B864:#7CFF8A><b>TROU EN UN !</b></gradient>", "<#E6EDF3>" + b.p.getName() + " · +10 pts", 2000);
        } else {
            w.spawnParticle(Particle.HAPPY_VILLAGER, at, 12, 0.25, 0.2, 0.25, 0);
            Txt.title(b.p, "<#7CFF8A><b>" + nom + "</b>", "<#E6EDF3>" + b.coups + " coup" + (b.coups > 1 ? "s" : "") + " · <#E0B864>+" + pts
                    + " pts", 1600);
        }
        annoncerTrou(b, pts, b.coups);
    }

    private void annoncerTrou(Balle b, int pts, int coups) {
        if (joueurs.size() > 1) {
            annoncer("<" + COULEURS[b.n % 4] + ">" + b.p.getName() + "</" + COULEURS[b.n % 4] + "> <#9AA4B2>termine le trou " + (trou + 1) + " en "
                    + coups + " coup" + (coups > 1 ? "s" : "") + " : <#E0B864>+" + pts + " pts");
        }
    }

    private void bilanTrou() {
        pause = PAUSE;
        if (trou + 1 < 9) {
            StringBuilder sb = new StringBuilder("<#7CFF8A>Trou " + (trou + 1) + " terminé.</#7CFF8A> ");
            for (Balle b : balles.values()) {
                sb.append("<").append(COULEURS[b.n % 4]).append(">").append(b.p.getName()).append("</").append(COULEURS[b.n % 4])
                        .append("> <#E0B864>").append(b.total).append("</#E0B864>  ");
            }
            annoncer(sb.toString().trim());
        }
    }

    private void finPartie() {
        List<Balle> classement = new ArrayList<>(balles.values());
        classement.sort(Comparator.comparingInt((Balle b) -> -b.total).thenComparingInt(b -> b.coupsTotal));
        int rang = 1;
        for (Balle b : classement) {
            String ligne = "<#E6EDF3>" + rang + ". " + b.p.getName() + " <#5A6270>·</#5A6270> <#E0B864>" + b.total + " pts</#E0B864> <#9AA4B2>("
                    + b.coupsTotal + " coups" + (b.enUn > 0 ? ", " + b.enUn + " trou(s) en un" : "") + ")";
            annoncer(ligne);
            rang++;
        }
        for (Balle b : classement) {
            if (!b.p.isOnline()) {
                continue;
            }
            int bonus = classement.size() > 1 && b == classement.get(0) ? 20 : 0;
            Txt.title(b.p, "<gradient:#7CFF8A:#62CFF4><b>" + b.total + " PTS</b></gradient>", "<#E6EDF3>" + b.coupsTotal + " coups sur 9 trous", 2400);
            pl.scores().enregistrer(b.p, Jeu.GOLF, b.total, b.total * 2 + bonus, "golf " + b.total + " pts · " + b.coupsTotal + " coups", true);
        }
        terminer();
    }

    @Override
    protected void quitte(Player p) {
        Balle b = balles.remove(p.getUniqueId());
        if (b != null) {
            lacher(b.d);
        }
    }

    @Override
    public String etat() {
        return "trou " + (trou + 1) + "/9 · " + joueurs.size() + " joueur(s)";
    }
}
