package fr.orbit.bowl.bowling;

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
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Une partie de bowling sur une piste : 1 à 4 joueurs (et le Bot ORBIT), 10 frames chacun leur tour.
 * Viser en regardant la piste, choisir l'effet dans la barre, charger la force accroupi puis relâcher.
 */
public final class PartieBowling extends Activite {

    static final String[] BOULES = {"boule_bleue", "boule_or", "boule_nebuleuse", "boule_glace", "boule_rubis"};
    static final double[] EFFETS = {-1, -0.5, 0, 0.5, 1};
    static final String[] NOMS_EFFETS = {"⟲⟲ crochet fort à gauche", "⟲ crochet à gauche", "● tout droit", "⟳ crochet à droite", "⟳⟳ crochet fort à droite"};
    private static final double PIN_TAILLE = 1.1, BOULE_TAILLE = (float) (2 * Physique.RB);
    private static final int PERIODE = 34, DELAI_MAX = 45 * 20;

    private enum Etape { VISEE, ROULE, MACHINE }

    private static final class Joueur {
        final Player p;
        final String nom, boule;
        final Feuille feuille = new Feuille();
        boolean parti;

        Joueur(Player p, String nom, String boule) {
            this.p = p;
            this.nom = nom;
            this.boule = boule;
        }

        boolean bot() {
            return p == null;
        }
    }

    private final double cx;
    private final Physique ph;
    private final Random rnd = new Random();
    private final List<Joueur> ordre = new ArrayList<>();
    private int courant, chrono, chargeDebut = -1, deboutAvant, machine;
    private Etape etape = Etape.VISEE;
    private ItemDisplay boule;
    private final ItemDisplay[] quilles = new ItemDisplay[10];
    private BlockDisplay rateau;
    private TextDisplay tableau;
    private Mannequin bot;
    private boolean remettre, auto;
    private int lancesBot;

    public PartieBowling(OrbitBowl pl, Poste poste, Mode mode, long mise) {
        super(pl, poste, mode, mise);
        cx = Salle.pisteX(poste.index);
        ph = new Physique(cx, System.nanoTime());
    }

    // ------------------------------------------------------------------ arrivée, départ

    @Override
    protected void equiper(Player p) {
        for (int i = 0; i < EFFETS.length; i++) {
            p.getInventory().setItem(i, objet(Material.HEART_OF_THE_SEA, BOULES[Math.min(joueurs.size(), BOULES.length - 1)],
                    "<#E0B864><b>Boule</b></#E0B864> <#E6EDF3>· " + NOMS_EFFETS[i],
                    "Accroupi : charger la force (la jauge va et vient)", "Relâcher : lancer", "Regarde la piste pour viser"));
        }
        p.getInventory().setItem(7, objet(Material.BOOK, "regles", "<#62CFF4><b>Règles</b>", "Clic droit : les règles du bowling"));
        p.getInventory().setHeldItemSlot(2);
    }

    @Override
    protected void accueillir(Player p) {
        pl.teleporter(p, banc(joueurs.size() - 1));
        if (phase() == Phase.ATTENTE && joueurs.size() > 1) {
            Txt.send(p, "Tu es sur la " + poste.nom + " : la partie démarre quand le créateur la lance.");
        }
    }

    private Location banc(int i) {
        return new Location(w, cx - 1.5 + (i % 4), Salle.Y, 9.5, 180f, 0f);
    }

    private Location elan() {
        return new Location(w, cx, Salle.Y, 3.2, 180f, 0f);
    }

    @Override
    protected void commencer() {
        for (int i = 0; i < joueurs.size(); i++) {
            Player p = joueurs.get(i);
            ordre.add(new Joueur(p, p.getName(), BOULES[i % BOULES.length]));
        }
        if (mode.bot()) {
            ordre.add(new Joueur(null, "Bot ORBIT", "boule_rubis"));
            bot = garder(w.spawn(banc(3), Mannequin.class, m -> {
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
        for (int k = 0; k < 10; k++) {
            int kk = k;
            quilles[k] = garder(w.spawn(new Location(w, ph.px[k], Salle.Y + PIN_TAILLE / 2, ph.pz[k]), ItemDisplay.class, d -> {
                OrbitBowl.marquer(d);
                d.setItemStack(objet(Material.BONE, "quille", ""));
                d.setBrightness(new Display.Brightness(15, 15));
                d.setTeleportDuration(1);
                d.setInterpolationDuration(1);
                d.setViewRange(2f);
                d.setTransformation(pose(kk));
            }));
        }
        rateau = garder(w.spawn(new Location(w, cx, Salle.Y + 2.2, Physique.TETE + 1.2), BlockDisplay.class, d -> {
            OrbitBowl.marquer(d);
            BlockData bd = Material.POLISHED_BLACKSTONE.createBlockData();
            d.setBlock(bd);
            d.setBrightness(new Display.Brightness(12, 12));
            d.setTeleportDuration(4);
            d.setTransformation(new Transformation(new Vector3f(-1.6f, 0f, -0.08f), new AxisAngle4f(),
                    new Vector3f(3.2f, 0.32f, 0.16f), new AxisAngle4f()));
        }));
        tableau = texte(new Location(w, cx, Salle.Y + 3.6, 1.0, 0f, 0f), "", 0.42f, false);
        tableau.setLineWidth(700);
        courant = 0;
        debutTour(true);
        titre(poste.jeu.titre(), "<#E6EDF3>" + poste.nom + " · " + mode.nom(), 1800);
    }

    @Override
    protected void depart(Player p) {
        super.depart(p);
        if (finie()) {
            return;
        }
        for (int i = 0; i < ordre.size(); i++) {
            Joueur j = ordre.get(i);
            if (p.equals(j.p)) {
                j.parti = true;
                annoncer("<#9AA4B2>" + p.getName() + " quitte la piste.");
                if (i == courant && etape == Etape.VISEE) {
                    chargeDebut = -1;
                    passerAuSuivant();
                }
            }
        }
    }

    // ------------------------------------------------------------------ tours

    private Joueur lanceur() {
        return ordre.get(courant);
    }

    private void debutTour(boolean nouveauFrame) {
        Joueur j = lanceur();
        etape = Etape.VISEE;
        chrono = 0;
        chargeDebut = -1;
        auto = false;
        deboutAvant = ph.debout();
        if (j.bot()) {
            bot.teleport(elan());
            lancesBot = 25 + rnd.nextInt(20);
        } else if (j.p.isOnline()) {
            if (nouveauFrame) {
                pl.teleporter(j.p, elan());
            }
            int f = j.feuille.frame() + 1;
            Txt.title(j.p, "<gradient:#3A7FFF:#E0B864><b>À TOI !</b></gradient>", "<#E6EDF3>Frame " + f + (j.feuille.lancerDansFrame() > 0
                    ? " · 2e boule" : "") + " <#5A6270>·</#5A6270> <#9AA4B2>accroupi pour charger", 1400);
            j.p.playSound(j.p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.4f);
        }
        if (bot != null && !j.bot()) {
            bot.teleport(banc(3));
        }
        afficherTableau();
    }

    private void passerAuSuivant() {
        if (toutFini()) {
            finPartie();
            return;
        }
        int essais = 0;
        do {
            courant = (courant + 1) % ordre.size();
            essais++;
        } while ((lanceur().parti || lanceur().feuille.termine()) && essais <= ordre.size());
        if (lanceur().parti || lanceur().feuille.termine()) {
            finPartie();
            return;
        }
        if (ph.debout() < 10) {
            ph.rack();
            montrerQuilles(true);
        }
        debutTour(true);
    }

    private boolean toutFini() {
        for (Joueur j : ordre) {
            if (!j.parti && !j.feuille.termine()) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ boucle

    @Override
    protected void tick() {
        garderLesJoueursHorsPiste();
        switch (etape) {
            case VISEE -> visee();
            case ROULE -> roule();
            case MACHINE -> machine();
        }
    }

    private void garderLesJoueursHorsPiste() {
        Joueur l = lanceur();
        for (Player p : joueurs) {
            Location at = p.getLocation();
            boolean surPiste = at.getZ() < 4.9 && Math.abs(at.getX() - cx) < 2.6;
            if (p.equals(l.p) && etape == Etape.VISEE) {
                double x = clamp(at.getX(), cx - 2.3, cx + 2.3), z = clamp(at.getZ(), 0.4, 4.9);
                if (Math.abs(x - at.getX()) > 1e-3 || Math.abs(z - at.getZ()) > 1e-3) {
                    Location to = at.clone();
                    to.setX(x);
                    to.setZ(z);
                    pl.teleporter(p, to);
                }
            } else if (surPiste) {
                pl.teleporter(p, banc(joueurs.indexOf(p)));
            }
        }
    }

    private void visee() {
        Joueur j = lanceur();
        chrono++;
        if (j.bot()) {
            if (chrono >= lancesBot) {
                lancerBot();
            }
            return;
        }
        Player p = j.p;
        if (!p.isOnline()) {
            return;
        }
        double x0 = clamp(p.getLocation().getX(), cx - Physique.DEMI + 0.15, cx + Physique.DEMI - 0.15);
        double ang = angleVise(p);
        if (chrono % 2 == 0) {
            // ligne de visée dorée sur la piste (sans l'effet : à toi de l'anticiper)
            for (double d = 1; d <= 9; d += 0.7) {
                double x = x0 + Math.sin(ang) * d, z = -0.3 - Math.cos(ang) * d;
                if (Math.abs(x - cx) > Physique.DEMI) {
                    break;
                }
                p.spawnParticle(Particle.DUST, x, Salle.Y + 0.06, z, 1, 0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(0xE0B864), 0.7f));
            }
        }
        int slot = Math.max(0, Math.min(4, p.getInventory().getHeldItemSlot()));
        if (chargeDebut >= 0) {
            double f = vaEtVient(t - chargeDebut, PERIODE);
            Txt.bar(p, "<#9AA4B2>Force</#9AA4B2> " + jauge(f, f > 0.85 ? "#E0484A" : "#E0B864") + " <#E6EDF3>" + Math.round(f * 100)
                    + " %  <#5A6270>·</#5A6270> <#9AA4B2>" + NOMS_EFFETS[slot]);
        } else if (chrono % 10 == 0) {
            Txt.bar(p, "<#9AA4B2>Effet :</#9AA4B2> <#E6EDF3>" + NOMS_EFFETS[slot] + "</#E6EDF3> <#5A6270>·</#5A6270> <#9AA4B2>molette pour changer · "
                    + "accroupi pour charger · " + Txt.chrono(DELAI_MAX - chrono));
        }
        if (chrono >= DELAI_MAX) {
            auto = true;
            Txt.send(p, "<#9AA4B2>Temps écoulé : boule lancée doucement à ta place.");
            lancer(x0, 0, 0.45, 0);
        }
    }

    /** Angle de visée (radians, 0 = tout droit, positif vers l'est), borné à ±10°. */
    private static double angleVise(Player p) {
        double yaw = Math.toRadians(p.getLocation().getYaw());
        double dx = -Math.sin(yaw), dz = Math.cos(yaw);
        double a = Math.atan2(dx, -dz);
        return clamp(a, Math.toRadians(-10), Math.toRadians(10));
    }

    @Override
    public void accroupi(Player p, boolean bas) {
        if (etape != Etape.VISEE || !p.equals(lanceur().p)) {
            return;
        }
        if (bas) {
            chargeDebut = t;
            p.playSound(p.getLocation(), Sound.ITEM_CROSSBOW_LOADING_START, 0.5f, 1.2f);
        } else if (chargeDebut >= 0) {
            int duree = t - chargeDebut;
            double force = vaEtVient(duree, PERIODE);
            chargeDebut = -1;
            if (duree < 3) {
                return;
            }
            int slot = Math.max(0, Math.min(4, p.getInventory().getHeldItemSlot()));
            double x0 = clamp(p.getLocation().getX(), cx - Physique.DEMI + 0.15, cx + Physique.DEMI - 0.15);
            lancer(x0, angleVise(p), Math.max(0.12, force), EFFETS[slot]);
        }
    }

    @Override
    public void clic(Player p, boolean gauche) {
        if (!gauche && p.getInventory().getHeldItemSlot() == 7) {
            p.performCommand("bowl regles bowling");
        }
    }

    private void lancer(double x0, double angle, double force, double effet) {
        // trop fort = moins précis
        double bruit = Math.toRadians(0.35 + (force > 0.85 ? (force - 0.85) * 14 : 0));
        double a = angle + rnd.nextGaussian() * bruit;
        double v = 7 + 8.5 * force;
        ph.lancer(x0, a, v, effet);
        etape = Etape.ROULE;
        chrono = 0;
        Joueur j = lanceur();
        if (boule != null) {
            lacher(boule);
        }
        boule = sprite(new Location(w, ph.bx, Salle.Y + Physique.RB, ph.bz), Material.HEART_OF_THE_SEA, j.boule, (float) BOULE_TAILLE, true);
        son(new Location(w, ph.bx, Salle.Y, 0), Sound.ENTITY_MINECART_RIDING, 0.35f, 0.7f);
        son(new Location(w, ph.bx, Salle.Y, 0), Sound.BLOCK_WOOD_PLACE, 0.8f, 0.6f);
    }

    private void lancerBot() {
        // le bot vise la poche 1-3 avec un léger crochet, ou le centre des quilles restantes pour le spare
        double eff, cibleX, cibleZ;
        if (ph.debout() == 10) {
            eff = -0.5;
            cibleX = cx + 0.2;
            cibleZ = Physique.TETE;
        } else {
            eff = 0;
            double sx = 0, n = 0, zmax = -99;
            for (int k = 0; k < 10; k++) {
                if (ph.etat[k] == Physique.DEBOUT) {
                    sx += ph.px[k];
                    n++;
                    zmax = Math.max(zmax, ph.pz[k]);
                }
            }
            cibleX = n == 0 ? cx : sx / n;
            cibleZ = n == 0 ? Physique.TETE : zmax;
        }
        double v = 11, mieux = cx, ecart = 99;
        for (double x = cx - 1.35; x <= cx + 1.35; x += 0.02) {
            double tx = Physique.trajectoire(cx, x, 0, v, eff, cibleZ);
            if (!Double.isNaN(tx) && Math.abs(tx - cibleX) < ecart) {
                ecart = Math.abs(tx - cibleX);
                mieux = x;
            }
        }
        double force = (v - 7) / 8.5;
        bot.teleport(new Location(w, mieux, Salle.Y, 1.0, 180f, 0f));
        lancer(mieux + rnd.nextGaussian() * 0.09, 0, force, eff);
    }

    private void roule() {
        ph.tick();
        chrono++;
        if (boule != null) {
            double y = ph.dansFosse ? 62.0 + Physique.RB : ph.gouttiere ? 63.0 + Physique.RB : Salle.Y + Physique.RB;
            boule.teleport(new Location(w, ph.bx, y, ph.bz));
            if (chrono % 3 == 0 && !ph.dansFosse) {
                w.spawnParticle(Particle.DUST, ph.bx, y - 0.2, ph.bz + 0.3, 1, 0.05, 0, 0.05, 0,
                        new Particle.DustOptions(Color.fromRGB(0x62CFF4), 0.6f));
            }
        }
        for (int k = 0; k < 10; k++) {
            synchroQuille(k);
        }
        Location deck = new Location(w, cx, Salle.Y + 0.5, Physique.TETE - 1);
        if (ph.chocBoule == 2) {
            son(deck, Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 0.9f, 1.5f + rnd.nextFloat() * 0.3f);
        } else if (ph.chocQuilles == 2) {
            son(deck, Sound.BLOCK_WOOD_HIT, 1f, 1.3f + rnd.nextFloat() * 0.4f);
        } else if (ph.chocQuilles == 1 || ph.chocBoule == 1) {
            son(deck, Sound.BLOCK_BAMBOO_WOOD_HIT, 0.8f, 1.4f);
        }
        if (ph.gouttiere && chrono % 6 == 0 && !ph.dansFosse) {
            son(new Location(w, ph.bx, 63, ph.bz), Sound.BLOCK_WOOL_STEP, 0.6f, 0.6f);
        }
        if (ph.fini()) {
            resultat();
        }
    }

    private void resultat() {
        Joueur j = lanceur();
        int tombees = deboutAvant - ph.debout();
        boolean premiere = j.feuille.lancerDansFrame() == 0 || deboutAvant == 10;
        j.feuille.ajouter(tombees);
        remettre = j.feuille.remettreTout();
        String haut, bas;
        Location deck = new Location(w, cx, Salle.Y + 1, Physique.TETE - 1);
        if (tombees == 10 && premiere) {
            haut = "<gradient:#E0B864:#FFF2C2:#E0B864><b>STRIKE !</b></gradient>";
            bas = "<#E6EDF3>" + j.nom + " fait tomber les 10 quilles";
            w.spawnParticle(Particle.FIREWORK, deck, 70, 1.0, 0.8, 1.0, 0.08);
            son(deck, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 1f, 1f);
            son(deck, Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.3f);
        } else if (ph.debout() == 0) {
            haut = "<gradient:#62CFF4:#3A7FFF><b>SPARE !</b></gradient>";
            bas = "<#E6EDF3>" + j.nom + " nettoie la piste";
            w.spawnParticle(Particle.END_ROD, deck, 40, 1.0, 0.6, 1.0, 0.05);
            son(deck, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 0.8f);
        } else if (tombees == 0) {
            haut = ph.gouttiere ? "<#9AA4B2><b>GOUTTIÈRE</b>" : "<#9AA4B2><b>RATÉ</b>";
            bas = "<#9AA4B2>aucune quille";
            son(deck, Sound.ENTITY_VILLAGER_NO, 0.6f, 1f);
        } else {
            haut = "<#E6EDF3><b>" + tombees + "</b> <#9AA4B2>quille" + (tombees > 1 ? "s" : "");
            bas = auto ? "<#9AA4B2>(lancer automatique)" : ph.debout() > 0 && premiere ? "<#9AA4B2>" + ph.debout() + " encore debout" : "";
        }
        titre(haut, bas, 1300);
        if (j.p != null && j.p.isOnline() && j.feuille.termine()) {
            Txt.send(j.p, "Ta partie est finie : <b>" + j.feuille.total() + "</b> points.");
        }
        etape = Etape.MACHINE;
        machine = 0;
        afficherTableau();
    }

    /** La machine balaie les quilles tombées (et remet les 10 si besoin), puis tour suivant. */
    private void machine() {
        machine++;
        if (machine == 18) {
            rateau.teleport(new Location(w, cx, Salle.Y + 0.05, Physique.TETE + 1.2));
            son(new Location(w, cx, Salle.Y + 1, Physique.TETE), Sound.BLOCK_PISTON_EXTEND, 0.6f, 0.8f);
        }
        if (machine == 24) {
            rateau.teleport(new Location(w, cx, Salle.Y + 0.05, Physique.DECK - 0.6));
        }
        if (machine == 30) {
            ph.balayer();
            montrerQuilles(false);
            if (boule != null) {
                lacher(boule);
                boule = null;
            }
            rateau.teleport(new Location(w, cx, Salle.Y + 2.2, Physique.TETE + 1.2));
        }
        if (machine == 38 && remettre) {
            ph.rack();
            montrerQuilles(true);
            son(new Location(w, cx, Salle.Y + 1, Physique.TETE), Sound.BLOCK_PISTON_CONTRACT, 0.6f, 1.1f);
        }
        if (machine >= 48) {
            Joueur j = lanceur();
            if (j.feuille.termine() || j.feuille.lancerDansFrame() == 0 || j.parti) {
                passerAuSuivant();
            } else {
                debutTour(false);
            }
        }
    }

    // ------------------------------------------------------------------ affichage des quilles

    private Transformation pose(int k) {
        float s = (float) PIN_TAILLE;
        Quaternionf q;
        if (ph.etat[k] == Physique.TOMBE || ph.etat[k] == Physique.COUCHEE) {
            q = bascule(ph.dirX[k], ph.dirZ[k], ph.angle[k]);
        } else if (ph.tremble[k] > 0) {
            q = bascule(Math.cos(k * 1.7), Math.sin(k * 1.7), Math.sin(t * 1.9) * 0.12 * ph.tremble[k]);
        } else {
            q = new Quaternionf();
        }
        // pivot à la base de la quille (le modèle est centré sur l'entité)
        Vector3f base = new Vector3f(0, -s / 2, 0);
        Vector3f tourne = q.transform(new Vector3f(base));
        Vector3f tr = new Vector3f(base).sub(tourne);
        return new Transformation(tr, q, new Vector3f(s, s, s), new Quaternionf());
    }

    private void synchroQuille(int k) {
        ItemDisplay d = quilles[k];
        if (d == null || !d.isValid()) {
            return;
        }
        int e = ph.etat[k];
        if (e == Physique.FOSSE_Q || e == Physique.RETIREE) {
            if (e == Physique.FOSSE_Q && d.getLocation().getY() > 62.5) {
                d.teleport(new Location(w, ph.px[k], 62.0 + PIN_TAILLE / 2 - 0.4, Math.max(ph.pz[k], -22.8)));
            }
            return;
        }
        double y = Salle.Y + PIN_TAILLE / 2;
        if (Math.abs(ph.px[k] - cx) > Physique.DEMI + 0.2) {
            y -= 0.8; // tombée dans la gouttière
        }
        Location at = d.getLocation();
        if (Math.abs(at.getX() - ph.px[k]) > 1e-3 || Math.abs(at.getZ() - ph.pz[k]) > 1e-3 || Math.abs(at.getY() - y) > 1e-3) {
            d.teleport(new Location(w, ph.px[k], y, ph.pz[k]));
        }
        if (e != Physique.DEBOUT || ph.tremble[k] > 0 || ph.angle[k] > 0) {
            d.setInterpolationDelay(0);
            d.setTransformation(pose(k));
        }
    }

    private void montrerQuilles(boolean poser) {
        ItemStack quille = objet(Material.BONE, "quille", "");
        for (int k = 0; k < 10; k++) {
            ItemDisplay d = quilles[k];
            if (d == null || !d.isValid()) {
                continue;
            }
            boolean visible = ph.etat[k] == Physique.DEBOUT;
            d.setItemStack(visible ? quille : new ItemStack(Material.AIR));
            if (visible && poser) {
                // les quilles redescendent de la machine
                d.setTeleportDuration(0);
                d.teleport(new Location(w, ph.px[k], Salle.Y + PIN_TAILLE / 2 + 1.2, ph.pz[k]));
                d.setTransformation(pose(k));
                int kk = k;
                pl.getServer().getScheduler().runTaskLater(pl, () -> {
                    if (d.isValid() && ph.etat[kk] == Physique.DEBOUT) {
                        d.setTeleportDuration(8);
                        d.teleport(new Location(w, ph.px[kk], Salle.Y + PIN_TAILLE / 2, ph.pz[kk]));
                        pl.getServer().getScheduler().runTaskLater(pl, () -> d.setTeleportDuration(1), 9L);
                    }
                }, 2L);
            }
        }
    }

    // ------------------------------------------------------------------ tableau des scores

    private void afficherTableau() {
        if (tableau == null || !tableau.isValid()) {
            return;
        }
        StringBuilder sb = new StringBuilder("<gradient:#3A7FFF:#E0B864><b>" + poste.nom.toUpperCase(java.util.Locale.ROOT) + "</b></gradient>\n<#5A6270>");
        for (int f = 1; f <= 10; f++) {
            sb.append(f).append(f == 10 ? "" : "    ");
        }
        sb.append("</#5A6270>");
        for (int i = 0; i < ordre.size(); i++) {
            Joueur j = ordre.get(i);
            boolean actif = i == courant && !finie();
            String c = j.parti ? "#5A6270" : actif ? "#E0B864" : "#E6EDF3";
            sb.append("\n<").append(c).append(">").append(actif ? "▶ " : "").append("<b>").append(j.nom).append("</b></").append(c).append(">  ");
            Integer[] cu = j.feuille.cumuls();
            for (int f = 0; f < 10; f++) {
                String m = j.feuille.marques(f);
                sb.append("<#62CFF4>").append(m.isEmpty() ? "·" : m.replace("X", "<#E0B864>X</#E0B864>")).append("</#62CFF4>");
                sb.append("<#5A6270>").append(cu[f] == null ? "" : "(" + cu[f] + ")").append("</#5A6270> ");
            }
            sb.append(" <#E0B864><b>").append(j.feuille.total()).append("</b>");
        }
        tableau.text(Txt.mm(sb.toString()));
    }

    @Override
    public String etat() {
        StringBuilder sb = new StringBuilder();
        for (Joueur j : ordre) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(j.nom).append(" ").append(j.feuille.total());
        }
        return sb.length() == 0 ? joueurs.size() + " joueur(s)" : sb.toString();
    }

    // ------------------------------------------------------------------ fin

    private void finPartie() {
        List<Joueur> classement = new ArrayList<>();
        for (Joueur j : ordre) {
            if (!j.parti) {
                classement.add(j);
            }
        }
        classement.sort((a, b) -> Integer.compare(b.feuille.total(), a.feuille.total()));
        afficherTableau();
        StringBuilder sb = new StringBuilder("Fin de partie sur la " + poste.nom + " :");
        int r = 1;
        for (Joueur j : classement) {
            sb.append("\n  ").append(r == 1 ? "<#E0B864>🏆 " : "<#9AA4B2>" + r + ". ").append(j.nom).append(" · <b>").append(j.feuille.total()).append("</b>");
            r++;
        }
        annoncer(sb.toString());
        if (mise > 0) {
            Joueur g = classement.size() > 0 ? classement.get(0) : null;
            boolean egalite = classement.size() > 1 && classement.get(0).feuille.total() == classement.get(1).feuille.total();
            if (g != null && !egalite && g.p != null) {
                regler(g.p.getUniqueId(), "victoire au bowling");
                annoncer("<#E0B864>" + g.nom + " remporte le défi : " + Txt.oc(2 * mise) + " !");
            } else {
                regler(null, "égalité");
                annoncer("<#9AA4B2>Égalité : chacun récupère sa mise.");
            }
        }
        int scoreBot = -1;
        for (Joueur j : ordre) {
            if (j.bot()) {
                scoreBot = j.feuille.total();
            }
        }
        for (Joueur j : classement) {
            if (j.bot() || j.p == null || !j.p.isOnline()) {
                continue;
            }
            int total = j.feuille.total();
            int oc = mise > 0 ? 0 : total / 3 + (scoreBot >= 0 && total > scoreBot ? 25 : 0);
            pl.scores().enregistrer(j.p, Jeu.BOWLING, total, oc, poste.nom + " · " + total + " pts", true);
            if (total == 300) {
                titre("<gradient:#E0B864:#FFF2C2:#E0B864><b>PARTIE PARFAITE</b></gradient>", "<#E6EDF3>" + j.nom + " : 300 !", 3000);
            }
        }
        terminer();
    }
}
