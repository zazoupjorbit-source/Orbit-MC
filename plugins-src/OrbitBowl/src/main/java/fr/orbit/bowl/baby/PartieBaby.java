package fr.orbit.bowl.baby;

import fr.orbit.bowl.Activite;
import fr.orbit.bowl.Jeu;
import fr.orbit.bowl.Mode;
import fr.orbit.bowl.OrbitBowl;
import fr.orbit.bowl.Poste;
import fr.orbit.bowl.Salle;
import fr.orbit.bowl.Txt;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Random;

/**
 * Baby-Orbit : baby-foot 1 contre 1. Le terrain fait 7 × 4 blocs (x vers l'est, z vers le sud). Bleu est assis au sud
 * et défend le but ouest ; Or (bot ou 2e joueur) est assis au nord et défend le but est. Chaque joueur fait glisser
 * toutes ses barres ensemble (avancer / reculer) et frappe (saut ou clic).
 */
public final class PartieBaby extends Activite {

    private static final int BUTS = 5;
    private static final double RBALLE = 0.09, RFIG = 0.11, BUT = 0.8, V_MAX = 9;
    /** Barres d'ouest en est : équipe (0 bleu, 1 or), nombre de joueurs, écart entre eux. */
    private static final int[] EQUIPE = {0, 0, 1, 0, 1, 0, 1, 1};
    private static final int[] NOMBRE = {1, 2, 3, 5, 5, 3, 2, 1};
    private static final double[] ECART = {0, 1.6, 1.25, 0.75, 0.75, 1.25, 1.6, 0};

    private final double x0 = Salle.BABY_X0, x1 = Salle.BABY_X1, z0, zm, y = Salle.BABY_Y;
    private final double[] barreX = new double[8];
    private final double[] cmd = new double[2], cmdAvant = new double[2];
    private final int[] frappe = new int[2], buts = new int[2], recharge = new int[2];
    private final Input[] touches = new Input[2];
    private final Player[] cote = new Player[2];
    private final Random rnd = new Random();
    private double bx, bz, bvx, bvz;
    private int pause, service = 0;
    private ItemDisplay balle;
    private final ItemDisplay[][] figs = new ItemDisplay[8][];
    private final BlockDisplay[] barres = new BlockDisplay[8];
    private final ItemDisplay[] sieges = new ItemDisplay[2];
    private TextDisplay tableau;
    private boolean fini;

    public PartieBaby(OrbitBowl pl, Poste poste, Mode mode, long mise) {
        super(pl, poste, mode, mise);
        z0 = Salle.babyZ0(poste.index);
        zm = z0 + Salle.BABY_H / 2;
        for (int i = 0; i < 8; i++) {
            barreX[i] = x0 + (x1 - x0) / 16 + i * (x1 - x0) / 8;
        }
    }

    @Override
    protected void equiper(Player p) {
        p.getInventory().setItem(0, objet(Material.STICK, "barre", "<#3A7FFF><b>Barres</b>", "Avancer / reculer : faire glisser tes barres",
                "Saut ou clic : frapper"));
        p.getInventory().setHeldItemSlot(0);
    }

    private double omax(int i) {
        return i == 0 || i == 7 ? BUT + 0.1 : 1.85 - (NOMBRE[i] - 1) / 2.0 * ECART[i];
    }

    private double figZ(int i, int j, int equipe) {
        return zm + cmd[equipe] * omax(i) + (j - (NOMBRE[i] - 1) / 2.0) * ECART[i];
    }

    @Override
    protected void commencer() {
        cote[0] = joueurs.get(0);
        cote[1] = joueurs.size() > 1 ? joueurs.get(1) : null;
        for (int e = 0; e < 2; e++) {
            if (cote[e] == null) {
                continue;
            }
            // siège en l'air, 1,5 bloc derrière le rebord : assis, le joueur descend de 0,6 sous son siège et ses yeux
            // arrivent ~1,6 bloc au-dessus du terrain (avant : siège au ras du sol, yeux dans le rebord en bois)
            Location s = new Location(w, (x0 + x1) / 2, Salle.Y + 1.6, e == 0 ? z0 + Salle.BABY_H + 1.5 : z0 - 1.5, e == 0 ? 180f : 0f, 30f);
            Player p = cote[e];
            pl.teleporter(p, s);
            int ee = e;
            sieges[e] = garder(w.spawn(s, ItemDisplay.class, d -> {
                OrbitBowl.marquer(d);
                d.setItemStack(new ItemStack(Material.AIR));
            }));
            pl.getServer().getScheduler().runTaskLater(pl, () -> {
                if (sieges[ee].isValid() && p.isOnline() && contient(p)) {
                    sieges[ee].addPassenger(p);
                }
            }, 2L);
        }
        for (int i = 0; i < 8; i++) {
            int e = EQUIPE[i];
            figs[i] = new ItemDisplay[NOMBRE[i]];
            for (int j = 0; j < NOMBRE[i]; j++) {
                figs[i][j] = sprite(new Location(w, barreX[i], y + 0.22, figZ(i, j, e), e == 0 ? 90f : -90f, 0f),
                        Material.LEATHER, e == 0 ? "fig_bleu" : "fig_or", 0.46f, false);
                figs[i][j].setRotation(e == 0 ? -90f : 90f, 0f);
                figs[i][j].setInterpolationDuration(2);
            }
            double longueur = Salle.BABY_H + 1.4;
            double zDebut = e == 0 ? z0 : z0 - 1.4;
            barres[i] = garder(w.spawn(new Location(w, barreX[i], y + 0.34, zDebut + cmd[e] * omax(i)), BlockDisplay.class, d -> {
                OrbitBowl.marquer(d);
                d.setBlock(Material.IRON_BLOCK.createBlockData());
                d.setBrightness(new Display.Brightness(14, 14));
                d.setTeleportDuration(1);
                d.setTransformation(new Transformation(new Vector3f(-0.03f, -0.03f, 0f), new AxisAngle4f(),
                        new Vector3f(0.06f, 0.06f, (float) longueur), new AxisAngle4f()));
            }));
        }
        balle = sprite(new Location(w, (x0 + x1) / 2, y + RBALLE, zm), Material.SNOWBALL, "balle_baby", (float) (RBALLE * 2.4), true);
        tableau = texte(new Location(w, (x0 + x1) / 2, Salle.Y + 3.0, zm, 0f, 0f), "", 0.6f, true);
        engager(rnd.nextInt(2));
        afficher();
        titre(Jeu.BABY.titre(), "<#E6EDF3>premier à " + BUTS + " buts · avancer/reculer pour bouger, saut pour frapper", 2200);
    }

    @Override
    public boolean assis(Player p) {
        return !fini;
    }

    private void engager(int versEquipe) {
        bx = (x0 + x1) / 2;
        bz = zm + (rnd.nextDouble() - 0.5) * 0.6;
        double a = (versEquipe == 0 ? Math.PI : 0) + (rnd.nextDouble() - 0.5) * 0.9;
        bvx = Math.cos(a) * 1.6;
        bvz = Math.sin(a) * 1.6;
        pause = 30;
    }

    // ------------------------------------------------------------------ commandes

    @Override
    public void touche(Player p, Input in) {
        for (int e = 0; e < 2; e++) {
            if (p.equals(cote[e])) {
                if (in.isJump() && (touches[e] == null || !touches[e].isJump())) {
                    frapper(e);
                }
                touches[e] = in;
            }
        }
    }

    @Override
    public void clic(Player p, boolean gauche) {
        for (int e = 0; e < 2; e++) {
            if (p.equals(cote[e])) {
                frapper(e);
            }
        }
    }

    private void frapper(int e) {
        if (recharge[e] > 0 || fini) {
            return;
        }
        frappe[e] = 5;
        recharge[e] = 8;
        son(new Location(w, (x0 + x1) / 2, y, zm), Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, 0.5f, 1.4f);
    }

    // ------------------------------------------------------------------ boucle

    @Override
    protected void tick() {
        if (fini) {
            return;
        }
        for (int e = 0; e < 2; e++) {
            cmdAvant[e] = cmd[e];
            if (recharge[e] > 0) {
                recharge[e]--;
            }
            if (cote[e] != null) {
                Input in = touches[e];
                if (in != null) {
                    // assis au sud et tourné vers le nord, « avancer » pousse les barres vers le nord (z décroissant)
                    double sens = e == 0 ? -1 : 1;
                    if (in.isForward()) {
                        cmd[e] = clamp(cmd[e] + sens * 0.075, -1, 1);
                    }
                    if (in.isBackward()) {
                        cmd[e] = clamp(cmd[e] - sens * 0.075, -1, 1);
                    }
                }
            } else {
                bot(e);
            }
        }
        if (pause > 0) {
            pause--;
        } else {
            for (int s = 0; s < 4; s++) {
                pas();
                if (fini || pause > 0) {
                    break;
                }
            }
        }
        dessiner();
        for (int e = 0; e < 2; e++) {
            if (frappe[e] > 0) {
                frappe[e]--;
            }
        }
    }

    private void bot(int e) {
        // le bot place la figurine la plus proche de la balle (sur la barre juste devant elle) dans l'axe
        int dir = e == 0 ? 1 : -1;
        double meilleure = Double.MAX_VALUE, cible = cmd[e];
        for (int i = 0; i < 8; i++) {
            if (EQUIPE[i] != e) {
                continue;
            }
            double dxb = (barreX[i] - bx) * dir;
            if (dxb < -0.3 || dxb > 2.6) {
                continue;
            }
            for (int j = 0; j < NOMBRE[i]; j++) {
                double base = (j - (NOMBRE[i] - 1) / 2.0) * ECART[i];
                double c = clamp((bz + bvz * 0.15 - zm - base) / omax(i), -1, 1);
                double cout = Math.abs(c - cmd[e]) + Math.abs(dxb) * 0.2;
                if (cout < meilleure) {
                    meilleure = cout;
                    cible = c;
                }
            }
        }
        double v = 0.055;
        cmd[e] += clamp(cible - cmd[e], -v, v);
        if (recharge[e] == 0) {
            for (int i = 0; i < 8; i++) {
                if (EQUIPE[i] != e) {
                    continue;
                }
                for (int j = 0; j < NOMBRE[i]; j++) {
                    if (Math.hypot(bx - barreX[i], bz - figZ(i, j, e)) < 0.3 && rnd.nextDouble() < 0.5) {
                        frapper(e);
                        return;
                    }
                }
            }
        }
    }

    private void pas() {
        double dt = 0.05 / 4;
        bx += bvx * dt;
        bz += bvz * dt;
        // bandes latérales
        if (bz < z0 + RBALLE) {
            bz = z0 + RBALLE;
            bvz = Math.abs(bvz) * 0.8;
        } else if (bz > z0 + Salle.BABY_H - RBALLE) {
            bz = z0 + Salle.BABY_H - RBALLE;
            bvz = -Math.abs(bvz) * 0.8;
        }
        // buts aux deux bouts
        if (bx < x0 + RBALLE || bx > x1 - RBALLE) {
            boolean ouest = bx < x0 + RBALLE;
            if (Math.abs(bz - zm) < BUT - RBALLE * 0.5) {
                if (bx < x0 - 0.15 || bx > x1 + 0.15) {
                    but(ouest ? 1 : 0);
                }
                return;
            }
            if (ouest) {
                bx = x0 + RBALLE;
                bvx = Math.abs(bvx) * 0.8;
            } else {
                bx = x1 - RBALLE;
                bvx = -Math.abs(bvx) * 0.8;
            }
            son(new Location(w, bx, y, bz), Sound.BLOCK_WOOD_HIT, 0.4f, 1.6f);
        }
        // figurines
        for (int i = 0; i < 8; i++) {
            int e = EQUIPE[i];
            double vRod = (cmd[e] - cmdAvant[e]) * omax(i) * 20;
            for (int j = 0; j < NOMBRE[i]; j++) {
                double fx = barreX[i], fz = figZ(i, j, e);
                double dx = bx - fx, dz = bz - fz, d = Math.hypot(dx, dz);
                if (frappe[e] > 0 && d < RFIG + RBALLE + 0.12) {
                    // frappe : la balle part vers le but adverse, angle selon le point de contact
                    double sens = e == 0 ? 1 : -1;
                    double v = 6.5 + rnd.nextDouble() * 1.5;
                    bvx = sens * v;
                    bvz = clamp(dz * 9 + vRod * 0.4, -4.5, 4.5);
                    bx = fx + sens * (RFIG + RBALLE + 0.02);
                    frappe[e] = 0;
                    son(new Location(w, bx, y, bz), Sound.BLOCK_BAMBOO_WOOD_HIT, 0.8f, 1.2f);
                    continue;
                }
                if (d < RFIG + RBALLE && d > 1e-6) {
                    double nx = dx / d, nz = dz / d;
                    double vn = bvx * nx + (bvz - vRod) * nz;
                    if (vn < 0) {
                        bvx -= 1.6 * vn * nx;
                        bvz -= 1.6 * vn * nz;
                    }
                    bx = fx + nx * (RFIG + RBALLE);
                    bz = fz + nz * (RFIG + RBALLE);
                }
            }
        }
        double v = Math.hypot(bvx, bvz);
        if (v > V_MAX) {
            bvx *= V_MAX / v;
            bvz *= V_MAX / v;
        }
        bvx *= 0.997;
        bvz *= 0.997;
        // balle presque arrêtée hors de portée : petit coup de pouce (comme une table légèrement inclinée)
        if (v < 0.25) {
            bvz += (zm - bz) * 0.02;
            bvx += (rnd.nextDouble() - 0.5) * 0.05;
        }
    }

    private void but(int equipe) {
        buts[equipe]++;
        Location at = new Location(w, bx, y + 0.3, bz);
        w.spawnParticle(Particle.FIREWORK, at, 25, 0.3, 0.3, 0.3, 0.05);
        son(at, Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 0.8f, 1.2f);
        String qui = nom(equipe);
        titre((equipe == 0 ? "<#3A7FFF>" : "<#E0B864>") + "<b>BUT !</b>", "<#E6EDF3>" + qui + " · " + buts[0] + " - " + buts[1], 1300);
        afficher();
        if (buts[equipe] >= BUTS) {
            finMatch(equipe);
            return;
        }
        engager(1 - equipe);
    }

    private String nom(int e) {
        return cote[e] != null ? cote[e].getName() : "Bot ORBIT";
    }

    private void dessiner() {
        balle.teleport(new Location(w, bx, y + RBALLE, bz));
        for (int i = 0; i < 8; i++) {
            int e = EQUIPE[i];
            boolean bouge = Math.abs(cmd[e] - cmdAvant[e]) > 1e-4;
            boolean frap = frappe[e] > 0;
            if (bouge) {
                double zDebut = (e == 0 ? z0 : z0 - 1.4) + cmd[e] * omax(i);
                barres[i].teleport(new Location(w, barreX[i], y + 0.34, zDebut));
            }
            for (int j = 0; j < NOMBRE[i]; j++) {
                ItemDisplay d = figs[i][j];
                if (bouge) {
                    d.teleport(new Location(w, barreX[i], y + 0.22, figZ(i, j, e), e == 0 ? -90f : 90f, 0f));
                }
                if (frappe[e] == 5 || frappe[e] == 1) {
                    // la figurine fouette : bascule vers le but adverse puis revient
                    float a = frap && frappe[e] == 5 ? (float) Math.toRadians(-60) : 0f;
                    d.setInterpolationDelay(0);
                    d.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotateZ(a),
                            new Vector3f(0.46f, 0.46f, 0.46f), new Quaternionf()));
                }
            }
        }
        for (int e = 0; e < 2; e++) {
            if (cote[e] != null && sieges[e] != null && sieges[e].isValid() && !sieges[e].getPassengers().contains(cote[e])
                    && cote[e].isOnline() && contient(cote[e])) {
                sieges[e].addPassenger(cote[e]);
            }
        }
    }

    private void afficher() {
        if (tableau != null && tableau.isValid()) {
            tableau.text(Txt.mm("<#3A7FFF><b>" + nom(0) + "</b></#3A7FFF> <#E6EDF3><b>" + buts[0] + " - " + buts[1]
                    + "</b></#E6EDF3> <#E0B864><b>" + nom(1) + "</b>"));
        }
    }

    @Override
    protected void depart(Player p) {
        super.depart(p);
        if (finie() || fini) {
            return;
        }
        for (int e = 0; e < 2; e++) {
            if (p.equals(cote[e])) {
                cote[e] = null;
                if (joueurs.size() > 1) {
                    annoncer("<#9AA4B2>" + p.getName() + " quitte la table : le bot prend sa place.");
                }
            }
        }
    }

    @Override
    public String etat() {
        return nom(0) + " " + buts[0] + " - " + buts[1] + " " + nom(1);
    }

    private void finMatch(int gagnant) {
        fini = true;
        String g = nom(gagnant);
        titre("<gradient:#3A7FFF:#E0B864><b>" + g.toUpperCase(java.util.Locale.ROOT) + " GAGNE</b></gradient>",
                "<#E6EDF3>" + buts[0] + " - " + buts[1], 2400);
        annoncer("Fin du match : " + nom(0) + " " + buts[0] + " - " + buts[1] + " " + nom(1) + ".");
        if (mise > 0) {
            if (cote[gagnant] != null) {
                regler(cote[gagnant].getUniqueId(), "victoire au baby-foot");
                annoncer("<#E0B864>" + g + " remporte le duel : " + Txt.oc(2 * mise) + " !");
            } else {
                regler(null, "match sans gagnant");
            }
        }
        for (int e = 0; e < 2; e++) {
            Player p = cote[e];
            if (p == null || !p.isOnline()) {
                continue;
            }
            boolean gagne = e == gagnant;
            int oc = mise > 0 ? 0 : gagne ? (mode.bot() ? 50 : 30) : 10;
            pl.scores().enregistrer(p, Jeu.BABY, buts[e], oc, "baby-foot " + buts[0] + "-" + buts[1], false);
        }
        pl.getServer().getScheduler().runTaskLater(pl, this::terminer, 30L);
    }
}
