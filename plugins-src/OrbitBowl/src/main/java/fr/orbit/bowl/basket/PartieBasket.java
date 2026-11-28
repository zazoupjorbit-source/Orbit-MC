package fr.orbit.bowl.basket;

import fr.orbit.bowl.Activite;
import fr.orbit.bowl.Jeu;
import fr.orbit.bowl.Mode;
import fr.orbit.bowl.OrbitBowl;
import fr.orbit.bowl.Poste;
import fr.orbit.bowl.Salle;
import fr.orbit.bowl.Txt;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Panier Orbital : borne de basket façon fête foraine. 45 secondes, tirs en cloche calculés par le serveur
 * (gravité, arceau, panneau, parois de la cage). Panier = 2 points, 3 dans les 15 dernières secondes ;
 * après 20 secondes, le panier glisse de gauche à droite.
 */
public final class PartieBasket extends Activite {

    private static final int DUREE = 45 * 20, DEPART = 60, PERIODE = 50, SOUS = 5;
    /** Zone idéale de la jauge (centre, demi-largeur) et angle du tir en cloche. */
    private static final double IDEAL = 0.72, ZONE = 0.07, ANGLE = Math.toRadians(55);
    private static final double RB = 0.2, RH = 0.45, G = 16, DT = 0.05 / SOUS;

    private static final class Ballon {
        double x, y, z, vx, vy, vz;
        int age;
        boolean marque, touche;
        ItemDisplay d;
    }

    private final double cx;
    private final List<Ballon> ballons = new ArrayList<>();
    private int reste, charge = -1, score, paniers, tirs, serie;
    private double hx;
    private ItemDisplay arceau, filet;

    public PartieBasket(OrbitBowl pl, Poste poste, Mode mode, long mise) {
        super(pl, poste, mode, mise);
        cx = Salle.basketX(poste.index);
        hx = cx;
    }

    /** Arceaux et filets des 3 bornes (décor permanent, déplacés pendant les parties). */
    public static final ItemDisplay[] ARCEAUX = new ItemDisplay[3], FILETS = new ItemDisplay[3];

    public static void decorer(org.bukkit.World w) {
        for (int k = 0; k < 3; k++) {
            double x = Salle.basketX(k);
            ARCEAUX[k] = Salle.image(w, x, Salle.PANIER_Y, Salle.PANIER_Z, 0f, "panier", 1.05f, 1.05f);
            ARCEAUX[k].setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotateX((float) Math.toRadians(-90)),
                    new Vector3f(1.05f, 1.05f, 1.05f), new Quaternionf()));
            ARCEAUX[k].setTeleportDuration(1);
            FILETS[k] = Salle.image(w, x, Salle.PANIER_Y - 0.32, Salle.PANIER_Z, 180f, "filet", 0.95f, 0.95f);
            FILETS[k].setTeleportDuration(1);
        }
    }

    private static void placer(int k, double x) {
        if (ARCEAUX[k] != null && ARCEAUX[k].isValid()) {
            ARCEAUX[k].teleport(new Location(ARCEAUX[k].getWorld(), x, Salle.PANIER_Y, Salle.PANIER_Z));
        }
        if (FILETS[k] != null && FILETS[k].isValid()) {
            FILETS[k].teleport(new Location(FILETS[k].getWorld(), x, Salle.PANIER_Y - 0.32, Salle.PANIER_Z, 180f, 0f));
        }
    }

    @Override
    protected void fin() {
        placer(poste.index, cx);
    }

    @Override
    protected void equiper(Player p) {
        p.getInventory().setItem(0, objet(Material.SLIME_BALL, "ballon", "<#FF8A3D><b>Ballon</b>",
                "Regarde le panier", "Accroupi : charger (la jauge va et vient)", "Relâcher dans la zone or : panier !"));
        p.getInventory().setHeldItemSlot(0);
    }

    @Override
    protected void commencer() {
        Player p = joueurs.get(0);
        pl.teleporter(p, new Location(w, cx, Salle.Y, Salle.BASKET_TIR_Z, 0f, -18f));
        arceau = ARCEAUX[poste.index];
        filet = FILETS[poste.index];
        reste = DUREE + DEPART;
        Txt.title(p, Jeu.BASKET.titre(), "<#E6EDF3>45 secondes · prêt ?", 1500);
    }

    @Override
    protected void tick() {
        if (joueurs.isEmpty()) {
            return;
        }
        Player p = joueurs.get(0);
        reste--;
        Location at = p.getLocation();
        double x = clamp(at.getX(), cx - 1.3, cx + 1.3), z = clamp(at.getZ(), 36.6, 38.3);
        if (Math.abs(x - at.getX()) > 1e-3 || Math.abs(z - at.getZ()) > 1e-3) {
            Location to = at.clone();
            to.setX(x);
            to.setZ(z);
            pl.teleporter(p, to);
        }
        if (reste > DUREE) {
            if ((reste - DUREE) % 20 == 0) {
                Txt.title(p, "<#FF8A3D><b>" + (reste - DUREE) / 20 + "</b>", "", 900);
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 1.2f);
            }
            return;
        }
        if (reste == DUREE) {
            Txt.title(p, "<gradient:#FF8A3D:#62CFF4><b>GO !</b></gradient>", "", 700);
            p.playSound(p.getLocation(), Sound.BLOCK_BELL_USE, 0.8f, 1.4f);
        }
        // panier mobile après 20 s
        int ecoule = DUREE - reste;
        if (ecoule > 20 * 20) {
            hx = cx + Math.sin((ecoule - 400) * 0.045) * 0.85;
            placer(poste.index, hx);
        }
        for (Iterator<Ballon> it = ballons.iterator(); it.hasNext(); ) {
            Ballon b = it.next();
            for (int s = 0; s < SOUS; s++) {
                pas(b, p);
            }
            b.age++;
            b.d.teleport(new Location(w, b.x, b.y, b.z));
            if (b.age > 70 || b.y < Salle.Y + RB + 0.01 && Math.abs(b.vy) < 0.5 && b.age > 20) {
                lacher(b.d);
                it.remove();
            }
        }
        if (charge >= 0) {
            double f = vaEtVient(t - charge, PERIODE);
            Txt.bar(p, "<#9AA4B2>Force</#9AA4B2> " + jaugeZone(f) + (Math.abs(f - IDEAL) <= ZONE ? " <#E0B864><b>PARFAIT</b>" : ""));
        } else if (t % 4 == 0) {
            Txt.bar(p, "<#FF8A3D><b>" + score + "</b> pts</#FF8A3D> <#5A6270>·</#5A6270> <#E6EDF3>" + Txt.chrono(reste)
                    + (reste < 15 * 20 ? " <#E0B864><b>· PANIERS À 3 POINTS !</b>" : "") + " <#5A6270>·</#5A6270> <#9AA4B2>" + paniers + "/" + tirs);
        }
        if (reste <= 0) {
            fin(p);
        }
    }

    @Override
    public void accroupi(Player p, boolean bas) {
        if (reste > DUREE || reste <= 0) {
            return;
        }
        if (bas) {
            charge = t;
        } else if (charge >= 0) {
            int duree = t - charge;
            double f = vaEtVient(duree, PERIODE);
            charge = -1;
            if (duree >= 3 && ballons.size() < 3) {
                tirer(p, f);
            }
        }
    }

    /** Jauge de 20 traits avec la zone idéale en or. */
    private static String jaugeZone(double f) {
        int n = (int) Math.round(clamp(f, 0, 1) * 20);
        boolean dedans = Math.abs(f - IDEAL) <= ZONE;
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 20; i++) {
            boolean zone = Math.abs(i / 20.0 - IDEAL) <= ZONE + 1e-9;
            String c = i <= n ? (dedans ? "#E0B864" : "#FF8A3D") : zone ? "#6B5A2E" : "#3A3F4E";
            sb.append('<').append(c).append(">|</").append(c).append('>');
        }
        return sb.toString();
    }

    /**
     * Tir assisté façon borne de fête foraine : le serveur calcule le tir en cloche parfait vers l'arceau, puis
     * l'abîme selon la jauge (trop fort = trop long, trop faible = trop court) et la visée (un sixième de l'écart
     * de direction est gardé). Relâcher dans la zone or de la jauge = swish.
     */
    private void tirer(Player p, double f) {
        Location eye = p.getEyeLocation();
        Vector d = eye.getDirection().normalize();
        double sx = eye.getX(), sy = eye.getY() - 0.2, sz = eye.getZ() + 0.4;
        // cible : centre de l'arceau, en rattrapant la moitié de son déplacement pendant le vol (~1 s)
        double tx = hx;
        int ecoule = DUREE - reste;
        if (ecoule > 20 * 20) {
            tx = hx + (cx + Math.sin((ecoule + 20 - 400) * 0.045) * 0.85 - hx) * 0.5;
        }
        double dx = tx - sx, dz = Salle.PANIER_Z - sz, dist = Math.hypot(dx, dz), h = Salle.PANIER_Y - sy;
        double ideal = Math.atan2(dz, dx), vise = Math.atan2(d.getZ(), d.getX());
        double ecart = Math.atan2(Math.sin(vise - ideal), Math.cos(vise - ideal));
        double dir = ideal + clamp(ecart, -0.6, 0.6) / 6;
        double cos = Math.cos(ANGLE), den = 2 * cos * cos * (dist * Math.tan(ANGLE) - h);
        double v = den > 0 ? Math.sqrt(G * dist * dist / den) : 10;
        // +1 % : compense le pas de calcul (sinon le tir parfait tombe un peu court) ; simulé : zone or = panier
        v *= 1.01 * (1 + (f - IDEAL) * 0.25);
        double vh = v * cos;
        Ballon b = new Ballon();
        b.x = sx;
        b.y = sy;
        b.z = sz;
        b.vx = Math.cos(dir) * vh;
        b.vz = Math.sin(dir) * vh;
        b.vy = v * Math.sin(ANGLE);
        b.d = sprite(new Location(w, b.x, b.y, b.z), Material.SLIME_BALL, "ballon", (float) (2 * RB) * 1.05f, true);
        ballons.add(b);
        tirs++;
        son(eye, Sound.ENTITY_WIND_CHARGE_THROW, 0.6f, 1.2f);
    }

    private void pas(Ballon b, Player p) {
        double py = b.y;
        b.vy -= G * DT;
        b.x += b.vx * DT;
        b.y += b.vy * DT;
        b.z += b.vz * DT;
        // arceau : 16 points sur le cercle
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI / 8;
            double rx = hx + Math.cos(a) * RH, rz = Salle.PANIER_Z + Math.sin(a) * RH, ry = Salle.PANIER_Y;
            double dx = b.x - rx, dy = b.y - ry, dz = b.z - rz, dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist < RB + 0.03 && dist > 1e-6) {
                double nx = dx / dist, ny = dy / dist, nz = dz / dist, vn = b.vx * nx + b.vy * ny + b.vz * nz;
                if (vn < 0) {
                    b.vx -= 1.55 * vn * nx;
                    b.vy -= 1.55 * vn * ny;
                    b.vz -= 1.55 * vn * nz;
                    b.x = rx + nx * (RB + 0.03);
                    b.y = ry + ny * (RB + 0.03);
                    b.z = rz + nz * (RB + 0.03);
                    if (!b.touche) {
                        son(new Location(w, rx, ry, rz), Sound.BLOCK_CHAIN_HIT, 0.9f, 1.2f);
                    }
                    b.touche = true;
                }
            }
        }
        // panneau (mur du fond), parois de la cage, sol
        if (b.z > Salle.PANNEAU_Z - RB) {
            b.z = Salle.PANNEAU_Z - RB;
            if (b.vz > 0) {
                b.vz = -b.vz * 0.6;
                son(new Location(w, b.x, b.y, b.z), Sound.BLOCK_WOOD_HIT, 0.5f, 1.4f);
            }
        }
        if (b.x < cx - 1.5 + RB) {
            b.x = cx - 1.5 + RB;
            b.vx = Math.abs(b.vx) * 0.5;
        } else if (b.x > cx + 1.5 - RB) {
            b.x = cx + 1.5 - RB;
            b.vx = -Math.abs(b.vx) * 0.5;
        }
        if (b.y < Salle.Y + RB) {
            b.y = Salle.Y + RB;
            if (b.vy < 0) {
                b.vy = -b.vy * 0.55;
                b.vx *= 0.8;
                b.vz *= 0.8;
                if (Math.abs(b.vy) > 1) {
                    son(new Location(w, b.x, b.y, b.z), Sound.BLOCK_WOOL_FALL, 0.7f, 0.8f);
                }
            }
        }
        // panier : la balle traverse le cercle de haut en bas
        if (!b.marque && py > Salle.PANIER_Y && b.y <= Salle.PANIER_Y && b.vy < 0
                && Math.hypot(b.x - hx, b.z - Salle.PANIER_Z) < RH - RB * 0.55) {
            b.marque = true;
            marque(b, p);
        }
    }

    private void marque(Ballon b, Player p) {
        int pts = reste < 15 * 20 ? 3 : 2;
        score += pts;
        paniers++;
        serie = b.touche ? 0 : serie + 1;
        Location at = new Location(w, hx, Salle.PANIER_Y - 0.3, Salle.PANIER_Z);
        w.spawnParticle(Particle.FIREWORK, at, 16, 0.2, 0.2, 0.2, 0.05);
        p.playSound(p.getLocation(), b.touche ? Sound.ENTITY_EXPERIENCE_ORB_PICKUP : Sound.ENTITY_PLAYER_LEVELUP, 0.8f, b.touche ? 1.2f : 1.8f);
        Txt.title(p, "", (b.touche ? "<#FF8A3D>PANIER" : "<#E0B864><b>SWISH !</b>") + " +" + pts, 600);
        b.vx *= 0.2;
        b.vz *= 0.2;
    }

    @Override
    public String etat() {
        return (joueurs.isEmpty() ? "" : joueurs.get(0).getName() + " · ") + score + " pts";
    }

    private void fin(Player p) {
        placer(poste.index, cx);
        Txt.title(p, "<gradient:#FF8A3D:#62CFF4><b>" + score + " PTS</b></gradient>", "<#E6EDF3>" + paniers + " paniers sur " + tirs + " tirs", 2200);
        pl.scores().enregistrer(p, Jeu.BASKET, score, score, "basket " + score + " pts", true);
        terminer();
    }
}
