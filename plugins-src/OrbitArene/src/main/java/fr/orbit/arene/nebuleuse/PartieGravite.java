package fr.orbit.arene.nebuleuse;

import fr.orbit.arene.Etat;
import fr.orbit.arene.Jeu;
import fr.orbit.arene.OrbitArene;
import fr.orbit.arene.Partie;
import fr.orbit.arene.Txt;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Nébuleuse · Gravité (mécanique « .io » : on grossit en avalant). Arène plate et carrée ; on avale les orbes et
 * les joueurs nettement plus petits que soi. La masse fait vraiment grandir le joueur (attribut SCALE) et le
 * ralentit. Avalé = on repart petit ailleurs. Score = plus grosse masse atteinte.
 */
public final class PartieGravite extends Partie {

    static final int R = 40;          // demi-côté de l'arène (blocs)
    static final double MASSE0 = 10;

    final class Blob {
        final Player p;
        final Mannequin bot;
        final String nom;
        final Color couleur;
        double x, z, masse = MASSE0, pic = MASSE0, cible = 0;
        double tx, tz;
        int protege = 60, avales, absorbe;
        double echelleFaite = -1;

        Blob(Player p, Mannequin bot, String nom, Color couleur, double x, double z) {
            this.p = p;
            this.bot = bot;
            this.nom = nom;
            this.couleur = couleur;
            this.x = x;
            this.z = z;
            this.tx = x;
            this.tz = z;
        }

        double echelle() {
            return Math.max(0.7, Math.min(7.0, 0.7 * Math.pow(masse / MASSE0, 0.42)));
        }

        double rayon() {
            return 0.42 * echelle() + 0.25;
        }

        /** Vitesse de marche (attribut) : plus gros = plus lent. */
        double vitesse() {
            return Math.max(0.045, 0.12 * Math.pow(echelle() / 0.7, -0.38));
        }
    }

    static final class Orbe {
        final double x, z;
        final int valeur;
        final BlockDisplay d;

        Orbe(double x, double z, int valeur, BlockDisplay d) {
            this.x = x;
            this.z = z;
            this.valeur = valeur;
            this.d = d;
        }
    }

    private static final Material[] ORBES = {Material.LIGHT_BLUE_STAINED_GLASS, Material.MAGENTA_STAINED_GLASS,
            Material.LIME_STAINED_GLASS, Material.YELLOW_STAINED_GLASS, Material.PINK_STAINED_GLASS, Material.CYAN_STAINED_GLASS};

    private final SplittableRandom rnd = new SplittableRandom();
    private final List<Blob> blobs = new ArrayList<>();
    private final List<Orbe> orbes = new ArrayList<>();
    private int depart = 80, reste;
    private final int nbOrbes;
    private final double cx, cz;

    public PartieGravite(OrbitArene pl, Jeu.Mode mode, int slot) {
        super(pl, mode, slot);
        nbOrbes = pl.getConfig().getInt("nebuleuse.gravite.orbes", 150);
        reste = pl.getConfig().getInt("nebuleuse.gravite.duree", 180) * 20;
        cx = ox + R + 4;
        cz = oz + R + 4;
    }

    @Override
    protected int[] taille() {
        return new int[]{2 * R + 8, 2 * R + 8};
    }

    // ------------------------------------------------------------------ arène

    private void construire() {
        int y = Y0 - 1;
        for (int dx = -R - 1; dx <= R; dx++) {
            for (int dz = -R - 1; dz <= R; dz++) {
                int bx = (int) cx + dx, bz = (int) cz + dz;
                boolean bord = dx == -R - 1 || dx == R || dz == -R - 1 || dz == R;
                Material sol = bord ? Material.SEA_LANTERN
                        : (Math.floorMod(dx, 8) == 0 || Math.floorMod(dz, 8) == 0) ? Material.BLUE_STAINED_GLASS : Material.BLACK_CONCRETE;
                w.getBlockAt(bx, y, bz).setType(sol, false);
                if (bord) {
                    w.getBlockAt(bx, y + 1, bz).setType(Material.LIGHT_BLUE_STAINED_GLASS, false);
                    for (int h = 2; h <= 16; h++) {
                        w.getBlockAt(bx, y + h, bz).setType(Material.BARRIER, false);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ départ

    @Override
    protected void demarrer(List<Player> joueurs) {
        construire();
        int total = Math.max(joueurs.size() + 3, pl.getConfig().getInt("nebuleuse.gravite.joueurs-max", 8));
        int i = 0;
        for (Player p : joueurs) {
            double[] at = place();
            Blob b = new Blob(p, null, p.getName(), COULEURS[i % COULEURS.length], at[0], at[1]);
            blobs.add(b);
            teleporter(p, new Location(w, at[0], Y0, at[1], rnd.nextFloat() * 360f, 0f));
            Etat.base(p, Attribute.JUMP_STRENGTH, 0.0);
            Etat.base(p, Attribute.MOVEMENT_SPEED, 0.0);
            boutonQuitter(p);
            appliquer(b);
            i++;
        }
        for (int k = 0; i < total; i++, k++) {
            double[] at = place();
            Color c = COULEURS[i % COULEURS.length];
            String nom = BOTS[(slot * 5 + k) % BOTS.length];
            Mannequin m = pantin(new Location(w, at[0], Y0, at[1]), nom, c, null);
            Blob b = new Blob(null, m, nom, c, at[0], at[1]);
            blobs.add(b);
            appliquer(b);
        }
        while (orbes.size() < nbOrbes) {
            orbe();
        }
        for (Player p : joueurs) {
            Txt.send(p, mode.jeu().grad("<b>NÉBULEUSE · GRAVITÉ</b>") + " · avale les orbes et les joueurs <b>nettement plus petits</b> que toi. "
                    + "Plus tu es gros, plus tu es lent. <#9AA4B2>" + Txt.chrono(reste) + " de partie.");
        }
    }

    private double[] place() {
        for (int essai = 0; essai < 30; essai++) {
            double x = cx + rnd.nextDouble(-R + 3, R - 3), z = cz + rnd.nextDouble(-R + 3, R - 3);
            boolean ok = true;
            for (Blob b : blobs) {
                if (Math.hypot(b.x - x, b.z - z) < 8 + b.rayon() * 2) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                return new double[]{x, z};
            }
        }
        return new double[]{cx + rnd.nextDouble(-R + 3, R - 3), cz + rnd.nextDouble(-R + 3, R - 3)};
    }

    private void orbe() {
        double x = cx + rnd.nextDouble(-R + 1, R - 1), z = cz + rnd.nextDouble(-R + 1, R - 1);
        boolean gros = rnd.nextInt(14) == 0;
        float s = gros ? 0.55f : 0.32f;
        Material m = gros ? Material.YELLOW_STAINED_GLASS : ORBES[rnd.nextInt(ORBES.length)];
        BlockDisplay d = bloc(new Location(w, x, Y0 + 0.35, z), m.createBlockData(), s, s, s,
                new Quaternionf().rotateY((float) Math.PI / 4).rotateX((float) Math.PI / 4), 0);
        d.setGlowing(gros);
        if (gros) {
            d.setGlowColorOverride(Color.fromRGB(0xE0B864));
        }
        orbes.add(new Orbe(x, z, gros ? 6 : 1, d));
    }

    // ------------------------------------------------------------------ tick

    @Override
    public void tick() {
        t++;
        if (depart > 0) {
            depart--;
            if (depart % 20 == 0) {
                int s = depart / 20;
                for (Player p : joueursEnLigne()) {
                    Txt.title(p, s > 0 ? "<#62CFF4><b>" + s + "</b>" : "<#7CFF8A><b>GO !</b>", s > 0 ? "<#9AA4B2>Avale tout ce qui est plus petit que toi" : "", 900);
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, s > 0 ? 1f : 2f);
                }
                if (s == 0) {
                    for (Blob b : blobs) {
                        b.echelleFaite = -1;
                        appliquer(b);
                    }
                }
            }
            return;
        }
        reste--;
        for (Blob b : blobs) {
            if (b.protege > 0) {
                b.protege--;
            }
            if (b.p != null) {
                Location l = b.p.getLocation();
                b.x = l.getX();
                b.z = l.getZ();
                if (l.getY() < Y0 - 3 || Math.abs(b.x - cx) > R + 2 || Math.abs(b.z - cz) > R + 2) {
                    teleporter(b.p, new Location(w, cx, Y0, cz, l.getYaw(), l.getPitch()));
                }
                if (b.p.getAllowFlight()) {
                    b.p.setAllowFlight(false);
                }
            } else {
                ia(b);
            }
        }
        manger();
        if (t % 20 == 0) {
            for (Blob b : blobs) {
                if (b.masse > 60) {
                    b.masse -= b.masse * 0.004;
                    appliquer(b);
                }
            }
        }
        while (orbes.size() < nbOrbes) {
            orbe();
        }
        if (t % 4 == 0) {
            hud();
        }
        if (reste <= 0) {
            fin();
        }
    }

    /** Bot : fuit les plus gros proches, chasse les plus petits, sinon va vers l'orbe la plus proche. */
    private void ia(Blob b) {
        if (t % 5 == 0) {
            double fx = 0, fz = 0;
            boolean fuite = false;
            Blob proie = null;
            double best = 1e9;
            for (Blob o : blobs) {
                if (o == b) {
                    continue;
                }
                double d = Math.hypot(o.x - b.x, o.z - b.z);
                if (o.masse > b.masse * 1.2 && d < 7 + o.rayon() * 2.5 && o.protege == 0) {
                    fx += (b.x - o.x) / Math.max(0.5, d);
                    fz += (b.z - o.z) / Math.max(0.5, d);
                    fuite = true;
                } else if (b.masse > o.masse * 1.25 && o.protege == 0 && d < 16 && d < best) {
                    best = d;
                    proie = o;
                }
            }
            if (fuite) {
                // fuite, en évitant les murs
                fx += (cx - b.x) / R * 0.6;
                fz += (cz - b.z) / R * 0.6;
                b.tx = b.x + fx * 8;
                b.tz = b.z + fz * 8;
                b.cible = 2;
            } else if (proie != null) {
                b.tx = proie.x;
                b.tz = proie.z;
                b.cible = 1;
            } else if (b.cible != 3 || Math.hypot(b.tx - b.x, b.tz - b.z) < 0.6 || t % 40 == 0) {
                Orbe o = null;
                double bd = 1e9;
                for (Orbe or : orbes) {
                    double d = Math.hypot(or.x - b.x, or.z - b.z) - or.valeur * 0.8 + rnd.nextDouble(0, 2);
                    if (d < bd) {
                        bd = d;
                        o = or;
                    }
                }
                if (o != null) {
                    b.tx = o.x;
                    b.tz = o.z;
                    b.cible = 3;
                }
            }
        }
        double dx = b.tx - b.x, dz = b.tz - b.z, d = Math.hypot(dx, dz);
        double v = b.vitesse() * 2.16 * (b.cible == 1 ? 1.12 : b.cible == 2 ? 1.08 : 0.95);
        if (d > 0.05) {
            double k = Math.min(v, d) / d;
            b.x += dx * k;
            b.z += dz * k;
        }
        b.x = Math.max(cx - R + 0.5, Math.min(cx + R - 0.5, b.x));
        b.z = Math.max(cz - R + 0.5, Math.min(cz + R - 0.5, b.z));
        float yaw = d > 0.05 ? (float) Math.toDegrees(Math.atan2(-dx, dz)) : b.bot.getLocation().getYaw();
        b.bot.teleport(new Location(w, b.x, Y0, b.z, yaw, 0f));
    }

    private void manger() {
        // orbes
        for (Blob b : blobs) {
            double r = b.rayon() + 0.35;
            boolean change = false;
            for (int i = orbes.size() - 1; i >= 0; i--) {
                Orbe o = orbes.get(i);
                if (Math.abs(o.x - b.x) < r && Math.abs(o.z - b.z) < r && Math.hypot(o.x - b.x, o.z - b.z) < r) {
                    oublier(o.d);
                    orbes.remove(i);
                    b.masse += o.valeur;
                    change = true;
                    if (b.p != null) {
                        b.p.playSound(b.p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4f, 1.4f + (float) rnd.nextDouble(0.5));
                    }
                }
            }
            if (change) {
                appliquer(b);
            }
        }
        // joueurs : le gros avale le petit si son centre est dans son rayon
        for (Blob a : blobs) {
            for (Blob b : blobs) {
                if (a == b || a.protege > 0 || b.protege > 0 || a.masse < b.masse * 1.25) {
                    continue;
                }
                if (Math.hypot(a.x - b.x, a.z - b.z) < a.rayon()) {
                    avale(a, b);
                }
            }
        }
    }

    private void avale(Blob a, Blob b) {
        a.masse += b.masse * 0.8;
        a.avales++;
        b.absorbe++;
        w.spawnParticle(Particle.END_ROD, b.x, Y0 + 0.8, b.z, 20, 0.4, 0.5, 0.4, 0.05);
        if (a.p != null) {
            a.p.playSound(a.p.getLocation(), Sound.ENTITY_GENERIC_EAT, 1f, 0.8f);
            Txt.bar(a.p, "<#7CFF8A>Tu as avalé <b>" + b.nom + "</b> (+" + Math.round(b.masse * 0.8) + ")");
        }
        if (b.p != null) {
            b.p.playSound(b.p.getLocation(), Sound.ENTITY_PLAYER_HURT_SWEET_BERRY_BUSH, 1f, 0.7f);
            Txt.title(b.p, "<#E0484A><b>Avalé !</b>", "<#9AA4B2>par " + a.nom + " · tu repars petit", 1600);
        }
        b.masse = MASSE0;
        b.protege = 60;
        double[] at = place();
        b.x = at[0];
        b.z = at[1];
        b.tx = b.x;
        b.tz = b.z;
        if (b.p != null) {
            teleporter(b.p, new Location(w, b.x, Y0, b.z, b.p.getLocation().getYaw(), 0f));
        } else {
            b.bot.teleport(new Location(w, b.x, Y0, b.z));
        }
        appliquer(a);
        appliquer(b);
    }

    /** Taille et vitesse suivent la masse. */
    private void appliquer(Blob b) {
        b.pic = Math.max(b.pic, b.masse);
        double s = b.echelle();
        if (Math.abs(s - b.echelleFaite) < 0.01) {
            return;
        }
        b.echelleFaite = s;
        if (b.p != null) {
            Etat.base(b.p, Attribute.SCALE, s);
            Etat.base(b.p, Attribute.MOVEMENT_SPEED, depart > 0 ? 0.0 : b.vitesse());
            Etat.base(b.p, Attribute.STEP_HEIGHT, 0.6);
        } else if (b.bot != null) {
            var a = b.bot.getAttribute(Attribute.SCALE);
            if (a != null) {
                a.setBaseValue(s);
            }
        }
    }

    private List<Blob> classement() {
        List<Blob> l = new ArrayList<>(blobs);
        l.sort(Comparator.comparingDouble((Blob b) -> -b.masse));
        return l;
    }

    private void hud() {
        List<Blob> l = classement();
        StringBuilder top = new StringBuilder();
        for (int i = 0; i < Math.min(3, l.size()); i++) {
            Blob b = l.get(i);
            top.append(i == 0 ? "<#E0B864>" : "<#C9D4E0>").append(i + 1).append(". ").append(b.nom).append(" ")
                    .append(Math.round(b.masse)).append(i == 0 ? "</#E0B864>" : "</#C9D4E0>").append("  ");
        }
        for (Blob b : blobs) {
            if (b.p == null) {
                continue;
            }
            int rang = l.indexOf(b) + 1;
            Txt.bar(b.p, "<#62CFF4>◉ " + Math.round(b.masse) + "</#62CFF4> <#5A6270>·</#5A6270> <#E6EDF3>" + rang + "/" + l.size()
                    + "</#E6EDF3> <#5A6270>·</#5A6270> <#9AA4B2>" + Txt.chrono(reste) + "</#9AA4B2>   " + top
                    + (b.protege > 0 ? " <#7CFF8A>(protégé)" : ""));
        }
    }

    private void fin() {
        List<Blob> l = classement();
        for (int i = 0; i < l.size(); i++) {
            Blob b = l.get(i);
            if (b.p == null || !b.p.isOnline()) {
                continue;
            }
            int rang = i + 1;
            int oc = (int) Math.round(b.pic / 4) + b.avales * 15 + (rang == 1 ? 60 : rang <= 3 ? 25 : 0);
            resultat(b.p, Math.round(b.pic), oc, rang == 1 ? "<#E0B864><b>1er !</b>" : "<#62CFF4><b>" + rang + "e</b>",
                    "Masse max " + Math.round(b.pic) + " · " + b.avales + " avalé(s)");
        }
        finir();
    }

    @Override
    protected void retirer(Player p) {
        blobs.removeIf(b -> b.p == p);
    }

    @Override
    protected void nettoyer() {
        blobs.clear();
        orbes.clear();
    }
}
