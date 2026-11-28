package fr.orbit.bowl.fleches;

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
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Fléchettes sur une cible du mur ouest. La cible regarde l'est : pour le joueur, la droite est le nord (z décroissant).
 * Coordonnées de cible : u vers la droite, v vers le haut, en blocs depuis le centre.
 */
public final class PartieFleches extends Activite {

    /** Rayons (blocs) : centre 50, anneau 25, triple, double, bord de la zone qui compte. */
    public static final double R_BULL = 0.045, R_25 = 0.10, R_T1 = 0.38, R_T2 = 0.45, R_D1 = 0.65, R_D2 = 0.72, R_AFF = 0.9;
    public static final int[] SECTEURS = {20, 1, 18, 4, 13, 6, 10, 15, 2, 17, 3, 19, 7, 16, 8, 11, 14, 9, 12, 5};
    private static final int VOLEES_MAX = 8, VOLEES_LIMITE = 20;

    /** Résultat d'une fléchette : points, multiplicateur, numéro (25 = anneau, 50 = centre, 0 = hors cible). */
    public record Coup(int points, int mult, int numero) {
        public String nom() {
            if (numero == 0) {
                return "raté";
            }
            if (numero == 50) {
                return "CENTRE (50)";
            }
            if (numero == 25) {
                return "25";
            }
            return (mult == 3 ? "triple " : mult == 2 ? "double " : "") + numero + (mult > 1 ? " (" + points + ")" : "");
        }
    }

    public static Coup score(double u, double v) {
        double r = Math.hypot(u, v);
        if (r > R_D2) {
            return new Coup(0, 0, 0);
        }
        if (r < R_BULL) {
            return new Coup(50, 1, 50);
        }
        if (r < R_25) {
            return new Coup(25, 1, 25);
        }
        double deg = Math.toDegrees(Math.atan2(u, v));
        int idx = Math.floorMod((int) Math.floor((deg + 9) / 18), 20);
        int n = SECTEURS[idx];
        int m = r >= R_T1 && r <= R_T2 ? 3 : r >= R_D1 ? 2 : 1;
        return new Coup(n * m, m, n);
    }

    private static final class Joueur {
        final Player p;
        final String nom;
        int score, cible = 1, flechettes, volees;
        final List<String> derniere = new ArrayList<>();
        boolean parti;

        Joueur(Player p, String nom, int depart) {
            this.p = p;
            this.nom = nom;
            this.score = depart;
        }
    }

    private final double zc, yc = Salle.CIBLE_Y;
    private final Random rnd = new Random();
    private final List<Joueur> ordre = new ArrayList<>();
    private final List<Entity> plantees = new ArrayList<>();
    private int courant, lancees, scoreDebutVolee, attente, souffleDebut = -1, recup;
    private boolean volleeFinie;
    private double ph1, ph2;
    private TextDisplay tableau;
    private Mannequin bot;
    private boolean fini;

    public PartieFleches(OrbitBowl pl, Poste poste, Mode mode, long mise) {
        super(pl, poste, mode, mise);
        zc = Salle.cibleZ(poste.index);
    }

    private boolean en301() {
        return mode.id().startsWith("301") || mode.id().equals("defi");
    }

    private boolean horloge() {
        return mode.id().equals("horloge");
    }

    @Override
    protected void equiper(Player p) {
        p.getInventory().setItem(0, objet(Material.ARROW, "flechette", "<#62CFF4><b>Fléchettes</b>",
                "Vise la cible : le viseur bleu tremble", "Accroupi : retenir son souffle (2 s)", "Clic : lancer"));
        p.getInventory().setItem(7, objet(Material.BOOK, "regles", "<#62CFF4><b>Règles</b>", "Clic droit : les règles"));
        p.getInventory().setHeldItemSlot(0);
    }

    @Override
    protected void accueillir(Player p) {
        pl.teleporter(p, attenteLieu(joueurs.size() - 1));
    }

    private Location oche() {
        return new Location(w, Salle.OCHE_X + 1.3, Salle.Y, zc, 90f, 0f);
    }

    private Location attenteLieu(int i) {
        return new Location(w, -28.6, Salle.Y, zc - 1.2 + (i % 4) * 0.8, 90f, 0f);
    }

    @Override
    protected void commencer() {
        int depart = en301() ? 301 : 0;
        for (Player p : joueurs) {
            ordre.add(new Joueur(p, p.getName(), depart));
        }
        if (mode.bot()) {
            ordre.add(new Joueur(null, "Bot ORBIT", depart));
            bot = garder(w.spawn(attenteLieu(3), Mannequin.class, m -> {
                OrbitBowl.marquer(m);
                m.setAI(false);
                m.setGravity(false);
                m.setInvulnerable(true);
                m.setSilent(true);
                m.setCollidable(false);
                m.setImmovable(true);
                m.customName(Txt.mm("<#9AA4B2>[bot]</#9AA4B2> <gradient:#E0484A:#E0B864>Bot ORBIT</gradient>"));
                m.setCustomNameVisible(true);
                try {
                    m.setDescription(null);
                } catch (RuntimeException ignored) {
                    m.setDescription(net.kyori.adventure.text.Component.empty());
                }
            }));
        }
        tableau = texte(new Location(w, Salle.CIBLE_X + 0.1, yc + 1.25, zc, -90f, 0f), "", 0.45f, false);
        ph1 = rnd.nextDouble() * 6;
        ph2 = rnd.nextDouble() * 6;
        courant = 0;
        debutVolee();
        titre(poste.jeu.titre(), "<#E6EDF3>" + mode.nom(), 1600);
    }

    // ------------------------------------------------------------------ tours

    private Joueur lanceur() {
        return ordre.get(courant);
    }

    private void debutVolee() {
        Joueur j = lanceur();
        lancees = 0;
        volleeFinie = false;
        scoreDebutVolee = j.score;
        j.derniere.clear();
        souffleDebut = -1;
        attente = 0;
        for (Entity e : plantees) {
            lacher(e);
        }
        plantees.clear();
        if (j.p == null) {
            bot.teleport(oche());
            attente = 30;
        } else if (j.p.isOnline()) {
            pl.teleporter(j.p, oche());
            Txt.title(j.p, "<gradient:#62CFF4:#E0B864><b>À TOI !</b></gradient>", "<#E6EDF3>" + objectif(j), 1200);
        }
        if (bot != null && j.p != null) {
            bot.teleport(attenteLieu(3));
        }
        for (Player o : joueurs) {
            if (!o.equals(j.p) && o.getLocation().getX() < -29.3 && Math.abs(o.getLocation().getZ() - zc) < 3) {
                pl.teleporter(o, attenteLieu(joueurs.indexOf(o)));
            }
        }
        afficher();
    }

    private String objectif(Joueur j) {
        if (horloge()) {
            return "vise le " + (j.cible > 20 ? "centre" : String.valueOf(j.cible));
        }
        if (en301()) {
            return "reste " + j.score;
        }
        return "volée " + (j.volees + 1) + "/" + VOLEES_MAX;
    }

    private void finVolee() {
        Joueur j = lanceur();
        j.volees++;
        if (termineePour(j)) {
            finPartie();
            return;
        }
        int essais = 0;
        do {
            courant = (courant + 1) % ordre.size();
            essais++;
        } while (lanceur().parti && essais <= ordre.size());
        if (lanceur().parti || tousLimite()) {
            finPartie();
            return;
        }
        debutVolee();
    }

    private boolean termineePour(Joueur j) {
        if (en301()) {
            return j.score == 0;
        }
        if (horloge()) {
            return j.cible > 21;
        }
        // score max : fini quand tout le monde a ses 8 volées
        for (Joueur o : ordre) {
            if (!o.parti && o.volees < VOLEES_MAX) {
                return false;
            }
        }
        return true;
    }

    private boolean tousLimite() {
        for (Joueur o : ordre) {
            if (!o.parti && o.volees < VOLEES_LIMITE) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ boucle

    @Override
    protected void tick() {
        if (fini) {
            return;
        }
        Joueur j = lanceur();
        if (attente > 0) {
            attente--;
            if (attente == 0) {
                if (volleeFinie) {
                    finVolee();
                } else if (j.p == null) {
                    lancerBot();
                }
            }
            return;
        }
        if (j.p == null || !j.p.isOnline()) {
            return;
        }
        Player p = j.p;
        Location at = p.getLocation();
        double x = clamp(at.getX(), Salle.OCHE_X + 0.3, -28.8), z = clamp(at.getZ(), zc - 1.4, zc + 1.4);
        if (Math.abs(x - at.getX()) > 1e-3 || Math.abs(z - at.getZ()) > 1e-3) {
            Location to = at.clone();
            to.setX(x);
            to.setZ(z);
            pl.teleporter(p, to);
        }
        double[] vise = viseur(p);
        if (vise != null) {
            p.spawnParticle(Particle.DUST, Salle.CIBLE_X + 0.06, yc + vise[1], zc - vise[0], 1, 0, 0, 0, 0,
                    new Particle.DustOptions(Color.fromRGB(0x62CFF4), 0.55f));
        }
        if (recup > 0) {
            recup--;
        }
        if (souffleDebut >= 0) {
            int d = t - souffleDebut;
            Txt.bar(p, d < 40 ? "<#62CFF4>Souffle retenu… " + jauge(1 - d / 40.0, "#62CFF4") : "<#E0484A>Tu trembles ! Relâche ton souffle.");
        } else if (t % 10 == 0) {
            Txt.bar(p, "<#9AA4B2>Fléchette " + (lancees + 1) + "/3 · " + objectif(j) + " · accroupi = retenir son souffle · clic = lancer");
        }
    }

    /** Point visé (u, v) avec le tremblement, ou null si le joueur ne regarde pas la cible. */
    private double[] viseur(Player p) {
        Location eye = p.getEyeLocation();
        Vector d = eye.getDirection();
        if (d.getX() > -0.05) {
            return null;
        }
        double k = (Salle.CIBLE_X - eye.getX()) / d.getX();
        double y = eye.getY() + d.getY() * k, z = eye.getZ() + d.getZ() * k;
        double u = -(z - zc), v = y - yc;
        double amp = amplitude();
        double s = t * 0.05;
        u += amp * (Math.sin(1.3 * s * 6 + ph1) + 0.6 * Math.sin(2.9 * s * 6 + ph2)) / 1.6;
        v += amp * (Math.cos(1.1 * s * 6 + ph2) + 0.6 * Math.sin(2.3 * s * 6 + ph1)) / 1.6;
        return new double[]{u, v};
    }

    private double amplitude() {
        if (souffleDebut < 0) {
            return 0.075;
        }
        int d = t - souffleDebut;
        return d < 40 ? 0.022 : Math.min(0.17, 0.022 + (d - 40) * 0.004);
    }

    @Override
    public void accroupi(Player p, boolean bas) {
        if (!p.equals(lanceur().p) || attente > 0) {
            return;
        }
        if (bas && recup == 0) {
            souffleDebut = t;
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_BREATH, 0.6f, 0.8f);
        } else if (!bas && souffleDebut >= 0) {
            souffleDebut = -1;
            recup = 20;
        }
    }

    @Override
    public void clic(Player p, boolean gauche) {
        if (!gauche && p.getInventory().getHeldItemSlot() == 7) {
            p.performCommand("bowl regles flechettes");
            return;
        }
        if (!p.equals(lanceur().p) || attente > 0 || volleeFinie || lancees >= 3) {
            return;
        }
        double[] vise = viseur(p);
        double u = vise == null ? 2 : vise[0] + rnd.nextGaussian() * 0.012, v = vise == null ? 0 : vise[1] + rnd.nextGaussian() * 0.012;
        lancer(p.getEyeLocation().add(p.getLocation().getDirection().multiply(0.5)).add(0, -0.2, 0), u, v);
    }

    private void lancerBot() {
        Joueur j = lanceur();
        double[] cible = cibleBot(j);
        double s = 0.085;
        lancer(bot.getLocation().add(0, 1.4, 0), cible[0] + rnd.nextGaussian() * s, cible[1] + rnd.nextGaussian() * s);
    }

    /** Où le bot vise, selon le jeu. */
    private double[] cibleBot(Joueur j) {
        if (en301()) {
            int r = j.score;
            if (r == 50 || r == 25) {
                return new double[]{0, 0};
            }
            if (r > 60) {
                return secteur(20, (R_T1 + R_T2) / 2);
            }
            if (r > 40) {
                return secteur(20, 0.25);
            }
            return secteur(r > 20 ? r - 20 : r, 0.25);
        }
        if (horloge()) {
            return j.cible > 20 ? new double[]{0, 0} : secteur(j.cible, 0.25);
        }
        return secteur(20, (R_T1 + R_T2) / 2);
    }

    private static double[] secteur(int n, double r) {
        for (int i = 0; i < 20; i++) {
            if (SECTEURS[i] == n) {
                double a = Math.toRadians(i * 18);
                return new double[]{Math.sin(a) * r, Math.cos(a) * r};
            }
        }
        return new double[]{0, 0};
    }

    /** La fléchette vole 6 ticks puis se plante ; le score est compté à l'impact. */
    private void lancer(Location depart, double u, double v) {
        lancees++;
        souffleDebut = -1;
        attente = 8;
        Location impact = new Location(w, Salle.CIBLE_X + 0.02, yc + v, zc - u);
        ItemDisplay vol = sprite(depart, Material.ARROW, "flechette_vol", 0.35f, true);
        vol.setTeleportDuration(6);
        son(depart, Sound.ENTITY_ARROW_SHOOT, 0.6f, 1.6f);
        pl.getServer().getScheduler().runTaskLater(pl, () -> {
            if (vol.isValid()) {
                vol.teleport(impact);
            }
        }, 1L);
        pl.getServer().getScheduler().runTaskLater(pl, () -> {
            lacher(vol);
            if (!finie()) {
                impact(u, v, impact);
            }
        }, 7L);
    }

    private void impact(double u, double v, Location at) {
        Joueur j = lanceur();
        Coup c = score(u, v);
        j.flechettes++;
        boolean dansCible = Math.hypot(u, v) <= R_AFF;
        if (dansCible) {
            planter(at);
            son(at, Sound.BLOCK_BAMBOO_WOOD_HIT, 1f, 1.5f);
        } else {
            son(at, Sound.BLOCK_STONE_HIT, 0.8f, 1.2f);
        }
        String msg;
        if (en301()) {
            int reste = j.score - c.points();
            if (reste < 0) {
                j.score = scoreDebutVolee;
                msg = "<#E0484A>" + c.nom() + " · trop ! volée annulée (reste " + j.score + ")";
                j.derniere.add("✗");
                volleeFinie = true;
            } else {
                j.score = reste;
                j.derniere.add(c.numero() == 0 ? "-" : String.valueOf(c.points()));
                msg = c.nom() + " · reste <b>" + j.score + "</b>";
                if (reste == 0) {
                    volleeFinie = true;
                }
            }
        } else if (horloge()) {
            boolean bon = j.cible <= 20 ? c.numero() == j.cible : c.numero() == 25 || c.numero() == 50;
            j.derniere.add(bon ? "✔" : c.numero() == 0 ? "-" : String.valueOf(c.numero()));
            if (bon) {
                j.cible++;
                msg = "<#7CFF8A>" + c.nom() + " ✔</#7CFF8A> · " + (j.cible > 21 ? "<b>tour fini !</b>" : "suivant : " + (j.cible > 20 ? "le centre" : j.cible));
                if (j.cible > 21) {
                    volleeFinie = true;
                }
            } else {
                msg = c.nom() + " · il fallait le " + (j.cible > 20 ? "centre" : j.cible);
            }
        } else {
            j.score += c.points();
            j.derniere.add(c.numero() == 0 ? "-" : String.valueOf(c.points()));
            msg = c.nom() + " · total <b>" + j.score + "</b>";
        }
        if (c.numero() == 50 || c.mult() == 3 && c.numero() >= 18) {
            w.spawnParticle(Particle.FIREWORK, at, 18, 0.1, 0.2, 0.2, 0.04);
            son(at, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.6f);
        }
        for (Player p : joueurs) {
            Txt.bar(p, "<#E6EDF3>" + j.nom + " : " + msg);
        }
        if (lancees >= 3) {
            volleeFinie = true;
        }
        if (volleeFinie) {
            attente = 30;
        }
        afficher();
    }

    /** Fléchette plantée : pointe, fût doré, ailettes bleues (BlockDisplay, orientation sûre). */
    private void planter(Location at) {
        double x = at.getX();
        plantees.add(garder(part(new Location(w, x, at.getY(), at.getZ()), Material.IRON_BLOCK, 0.0f, 0.10f, 0.03f)));
        plantees.add(garder(part(new Location(w, x, at.getY(), at.getZ()), Material.GOLD_BLOCK, 0.10f, 0.16f, 0.05f)));
        plantees.add(garder(part(new Location(w, x, at.getY(), at.getZ()), Material.BLUE_STAINED_GLASS, 0.26f, 0.12f, 0.13f)));
    }

    private BlockDisplay part(Location at, Material m, float debut, float longueur, float epaisseur) {
        return w.spawn(at, BlockDisplay.class, d -> {
            OrbitBowl.marquer(d);
            d.setBlock(m.createBlockData());
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTransformation(new Transformation(new Vector3f(debut, -epaisseur / 2, -epaisseur / 2), new AxisAngle4f(),
                    new Vector3f(longueur, epaisseur, epaisseur), new AxisAngle4f()));
        });
    }

    // ------------------------------------------------------------------ tableau, fin

    private void afficher() {
        if (tableau == null || !tableau.isValid()) {
            return;
        }
        StringBuilder sb = new StringBuilder(poste.jeu.grad("<b>" + poste.nom.toUpperCase(java.util.Locale.ROOT) + " · " + mode.nom().toUpperCase(java.util.Locale.ROOT) + "</b>"));
        for (int i = 0; i < ordre.size(); i++) {
            Joueur j = ordre.get(i);
            boolean actif = i == courant;
            String c = j.parti ? "#5A6270" : actif ? "#E0B864" : "#E6EDF3";
            sb.append("\n<").append(c).append(">").append(actif ? "▶ " : "").append(j.nom).append("</").append(c).append("> <#62CFF4><b>");
            if (horloge()) {
                sb.append(j.cible > 21 ? "fini" : j.cible > 20 ? "centre" : "→ " + j.cible);
            } else {
                sb.append(j.score);
            }
            sb.append("</b></#62CFF4>");
            if (actif && !j.derniere.isEmpty()) {
                sb.append(" <#9AA4B2>[").append(String.join(" ", j.derniere)).append("]");
            }
        }
        tableau.text(Txt.mm(sb.toString()));
    }

    @Override
    protected void depart(Player p) {
        super.depart(p);
        if (finie()) {
            return;
        }
        for (int i = 0; i < ordre.size(); i++) {
            if (p.equals(ordre.get(i).p)) {
                ordre.get(i).parti = true;
                if (i == courant && !fini) {
                    attente = 0;
                    volleeFinie = false;
                    finVolee();
                }
            }
        }
    }

    @Override
    public String etat() {
        StringBuilder sb = new StringBuilder(mode.nom());
        for (Joueur j : ordre) {
            sb.append(" · ").append(j.nom).append(" ").append(horloge() ? "→" + j.cible : String.valueOf(j.score));
        }
        return sb.toString();
    }

    private void finPartie() {
        if (fini) {
            return;
        }
        fini = true;
        List<Joueur> cl = new ArrayList<>();
        for (Joueur j : ordre) {
            if (!j.parti) {
                cl.add(j);
            }
        }
        if (en301()) {
            cl.sort((a, b) -> Integer.compare(a.score, b.score));
        } else if (horloge()) {
            cl.sort((a, b) -> a.cible != b.cible ? Integer.compare(b.cible, a.cible) : Integer.compare(a.flechettes, b.flechettes));
        } else {
            cl.sort((a, b) -> Integer.compare(b.score, a.score));
        }
        StringBuilder sb = new StringBuilder("Fin de partie (" + mode.nom() + ") :");
        int r = 1;
        for (Joueur j : cl) {
            sb.append("\n  ").append(r == 1 ? "<#E0B864>🏆 " : "<#9AA4B2>" + r + ". ").append(j.nom).append(" · ")
                    .append(horloge() ? (j.cible > 21 ? "tour fini en " + j.flechettes + " fléchettes" : "arrivé au " + Math.min(j.cible, 20))
                            : en301() ? "reste " + j.score : j.score + " pts");
            r++;
        }
        annoncer(sb.toString());
        Joueur g = cl.isEmpty() ? null : cl.get(0);
        boolean egalite = cl.size() > 1 && (en301() ? cl.get(0).score == cl.get(1).score
                : horloge() ? cl.get(0).cible == cl.get(1).cible && cl.get(0).flechettes == cl.get(1).flechettes
                : cl.get(0).score == cl.get(1).score);
        if (mise > 0) {
            if (g != null && g.p != null && !egalite) {
                regler(g.p.getUniqueId(), "victoire aux fléchettes");
                annoncer("<#E0B864>" + g.nom + " remporte le défi : " + Txt.oc(2 * mise) + " !");
            } else {
                regler(null, "égalité");
                annoncer("<#9AA4B2>Égalité : chacun récupère sa mise.");
            }
        }
        for (Joueur j : cl) {
            if (j.p == null || !j.p.isOnline()) {
                continue;
            }
            boolean gagne = j == g && !egalite;
            if (mode.id().equals("max")) {
                pl.scores().enregistrer(j.p, Jeu.FLECHETTES, j.score, j.score / 6, "score max " + j.score, true);
            } else if (horloge()) {
                int oc = j.cible > 21 ? Math.max(25, 90 - j.flechettes) : 10;
                pl.scores().enregistrer(j.p, Jeu.FLECHETTES, 0, oc, "tour de l'horloge", false);
            } else {
                int oc = mise > 0 ? 0 : gagne ? 60 : 15;
                pl.scores().enregistrer(j.p, Jeu.FLECHETTES, 0, oc, "301 " + (gagne ? "gagné" : "perdu"), false);
            }
        }
        pl.getServer().getScheduler().runTaskLater(pl, this::terminer, 40L);
    }
}
