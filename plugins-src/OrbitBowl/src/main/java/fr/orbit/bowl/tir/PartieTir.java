package fr.orbit.bowl.tir;

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
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Tir aux Comètes : 60 secondes derrière le comptoir d'un stand. Trois rails de cibles défilent au fond :
 * météores (10), soucoupes (25), comètes dorées (60, rares) et satellites ORBIT (-30, à éviter).
 */
public final class PartieTir extends Activite {

    private static final int DUREE = 60 * 20, DEPART = 60, CADENCE = 7;
    private static final double RAYON = 0.36, DEMI = 4.6;

    private enum Type {
        METEORE("tir_meteore", 10), SOUCOUPE("tir_soucoupe", 25), COMETE("tir_comete", 60), SATELLITE("tir_satellite", -30);

        final String modele;
        final int points;

        Type(String modele, int points) {
            this.modele = modele;
            this.points = points;
        }
    }

    private static final class Cible {
        final int rail;
        Type type;
        double z;
        int cache, bascule;
        ItemDisplay d;

        Cible(int rail, Type type, double z) {
            this.rail = rail;
            this.type = type;
            this.z = z;
        }
    }

    private static final double[] RAIL_Y = {65.0, 66.35, 67.7};
    private static final double[] RAIL_V = {2.2, -3.6, 5.0};

    private final double zc;
    private final Random rnd = new Random();
    private final List<Cible> cibles = new ArrayList<>();
    private int score, combo, touches, tirs, recharge, reste;
    private TextDisplay panneau;

    public PartieTir(OrbitBowl pl, Poste poste, Mode mode, long mise) {
        super(pl, poste, mode, mise);
        zc = Salle.tirZ(poste.index);
    }

    @Override
    protected void equiper(Player p) {
        p.getInventory().setItem(0, objet(Material.STICK, "carabine", "<#FF8A3D><b>Carabine à comètes</b>",
                "Clic : tirer là où tu regardes", "Météore 10 · soucoupe 25 · comète 60", "Satellite ORBIT : -30, ne tire pas !"));
        p.getInventory().setHeldItemSlot(0);
    }

    private Location poste() {
        return new Location(w, Salle.TIR_X, Salle.Y, zc, -90f, 0f);
    }

    @Override
    protected void commencer() {
        Player p = joueurs.get(0);
        pl.teleporter(p, poste());
        for (int i = 0; i < 4; i++) {
            cibles.add(new Cible(0, Type.METEORE, zc - DEMI + i * 2.3));
        }
        for (int i = 0; i < 3; i++) {
            cibles.add(new Cible(1, i == 2 ? Type.SATELLITE : Type.SOUCOUPE, zc - DEMI + i * 3.1));
        }
        cibles.add(new Cible(2, Type.SATELLITE, zc - DEMI));
        cibles.add(new Cible(2, Type.COMETE, zc));
        for (Cible c : cibles) {
            c.d = sprite(lieu(c), Material.FIREWORK_STAR, c.type.modele, 0.85f, false);
            c.d.setRotation(90f, 0f);
            c.d.setInterpolationDuration(3);
        }
        panneau = texte(new Location(w, 26.2, Salle.Y + 5.6, zc, -90f, 0f), "", 0.7f, false);
        reste = DUREE + DEPART;
        Txt.title(p, Jeu.TIR.titre(), "<#E6EDF3>60 secondes · prêt ?", 1500);
    }

    private Location lieu(Cible c) {
        return new Location(w, Salle.CIBLES_X, RAIL_Y[c.rail], c.z, 90f, 0f);
    }

    @Override
    protected void tick() {
        Player p = joueurs.isEmpty() ? null : joueurs.get(0);
        if (p == null) {
            return;
        }
        reste--;
        Location at = p.getLocation();
        double x = clamp(at.getX(), 24.6, 25.95), z = clamp(at.getZ(), zc - 1.5, zc + 1.5);
        if (Math.abs(x - at.getX()) > 1e-3 || Math.abs(z - at.getZ()) > 1e-3) {
            Location to = at.clone();
            to.setX(x);
            to.setZ(z);
            pl.teleporter(p, to);
        }
        if (reste > DUREE) {
            int s = (reste - DUREE + 19) / 20;
            if ((reste - DUREE) % 20 == 0) {
                Txt.title(p, "<#FF8A3D><b>" + s + "</b>", "", 900);
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 1.2f);
            }
            return;
        }
        if (reste == DUREE) {
            Txt.title(p, "<gradient:#FF8A3D:#E0B864><b>FEU !</b></gradient>", "", 700);
            p.playSound(p.getLocation(), Sound.BLOCK_BELL_USE, 0.8f, 1.4f);
        }
        if (recharge > 0) {
            recharge--;
        }
        for (Cible c : cibles) {
            avancer(c);
        }
        if (t % 4 == 0) {
            Txt.bar(p, "<#FF8A3D><b>" + score + "</b> pts</#FF8A3D> <#5A6270>·</#5A6270> <#E6EDF3>" + Txt.chrono(reste)
                    + " <#5A6270>·</#5A6270> combo <#E0B864>×" + mult() + "</#E0B864> <#5A6270>·</#5A6270> <#9AA4B2>" + touches + "/" + tirs);
            if (panneau != null) {
                panneau.text(Txt.mm(Jeu.TIR.grad("<b>" + p.getName() + "</b>") + "\n<#E6EDF3><b>" + score + "</b> pts · " + Txt.chrono(reste)));
            }
        }
        if (reste <= 0) {
            finPartie();
        }
    }

    private void avancer(Cible c) {
        if (c.cache > 0) {
            c.cache--;
            if (c.cache == 0) {
                // réapparition : sur ce rail, une nouvelle cible (la comète reste rare)
                if (c.rail == 2) {
                    c.type = rnd.nextDouble() < 0.35 ? Type.COMETE : Type.SATELLITE;
                } else if (c.rail == 1) {
                    c.type = rnd.nextDouble() < 0.25 ? Type.SATELLITE : Type.SOUCOUPE;
                }
                c.d.setItemStack(objet(Material.FIREWORK_STAR, c.type.modele, ""));
                c.d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.85f, 0.85f, 0.85f), new Quaternionf()));
            }
            return;
        }
        double v = RAIL_V[c.rail] * (reste < 15 * 20 ? 1.35 : 1.0) / 20;
        c.z += v;
        if (c.z > zc + DEMI) {
            c.z -= 2 * DEMI;
            c.d.teleport(lieu(c));
        } else if (c.z < zc - DEMI) {
            c.z += 2 * DEMI;
            c.d.teleport(lieu(c));
        } else {
            c.d.teleport(lieu(c));
        }
    }

    private int mult() {
        return combo >= 6 ? 3 : combo >= 3 ? 2 : 1;
    }

    @Override
    public void clic(Player p, boolean gauche) {
        if (reste > DUREE || reste <= 0 || recharge > 0) {
            return;
        }
        recharge = CADENCE;
        tirs++;
        Location eye = p.getEyeLocation();
        Vector d = eye.getDirection();
        son(eye, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.5f, 1.7f);
        if (d.getX() < 0.05) {
            combo = 0;
            return;
        }
        double k = (Salle.CIBLES_X - eye.getX()) / d.getX();
        double y = eye.getY() + d.getY() * k, z = eye.getZ() + d.getZ() * k;
        Cible touchee = null;
        double mieux = RAYON;
        for (Cible c : cibles) {
            if (c.cache > 0) {
                continue;
            }
            double dist = Math.hypot(y - RAIL_Y[c.rail], z - c.z);
            if (dist < mieux) {
                mieux = dist;
                touchee = c;
            }
        }
        // traînée du tir
        Location fin = new Location(w, Salle.CIBLES_X, y, z);
        Vector pas = fin.toVector().subtract(eye.toVector()).multiply(1 / 14.0);
        Location l = eye.clone().add(d.clone().multiply(0.8));
        for (int i = 0; i < 13; i++) {
            l.add(pas);
            p.spawnParticle(Particle.DUST, l, 1, 0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(0xFF8A3D), 0.5f));
        }
        if (touchee == null) {
            combo = 0;
            w.spawnParticle(Particle.SMOKE, fin, 4, 0.05, 0.05, 0.05, 0.01);
            return;
        }
        int pts;
        if (touchee.type == Type.SATELLITE) {
            pts = touchee.type.points;
            combo = 0;
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1f);
            Txt.title(p, "", "<#E0484A>Satellite ORBIT touché ! " + pts, 700);
        } else {
            touches++;
            combo++;
            pts = touchee.type.points * mult();
            p.playSound(p.getLocation(), touchee.type == Type.COMETE ? Sound.ENTITY_PLAYER_LEVELUP : Sound.ENTITY_ARROW_HIT_PLAYER, 0.8f,
                    touchee.type == Type.COMETE ? 1.6f : 1.3f);
            w.spawnParticle(touchee.type == Type.COMETE ? Particle.FIREWORK : Particle.CRIT, fin, 12, 0.15, 0.15, 0.15, 0.05);
        }
        score = Math.max(0, score + pts);
        texte(fin.clone().add(-0.4, 0.5, 0), (pts > 0 ? "<#7CFF8A>+" : "<#E0484A>") + pts, 0.6f, true).setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        // la cible bascule en arrière puis disparaît un moment
        touchee.d.setInterpolationDelay(0);
        touchee.d.setTransformation(new Transformation(new Vector3f(0, -0.35f, 0), new Quaternionf().rotateX((float) Math.toRadians(80)),
                new Vector3f(0.85f, 0.85f, 0.85f), new Quaternionf()));
        Cible c = touchee;
        c.cache = 30 + rnd.nextInt(20);
        pl.getServer().getScheduler().runTaskLater(pl, () -> {
            if (c.d.isValid() && c.cache > 0) {
                c.d.setItemStack(new ItemStack(Material.AIR));
            }
        }, 6L);
    }

    @Override
    public String etat() {
        return (joueurs.isEmpty() ? "" : joueurs.get(0).getName() + " · ") + score + " pts";
    }

    private void finPartie() {
        Player p = joueurs.get(0);
        double precision = tirs == 0 ? 0 : touches * 100.0 / tirs;
        Txt.title(p, "<gradient:#FF8A3D:#E0B864><b>" + score + " PTS</b></gradient>", "<#E6EDF3>" + touches + " cibles · précision "
                + Math.round(precision) + " %", 2200);
        pl.scores().enregistrer(p, Jeu.TIR, score, score / 10, "tir " + score + " pts", true);
        terminer();
    }
}
