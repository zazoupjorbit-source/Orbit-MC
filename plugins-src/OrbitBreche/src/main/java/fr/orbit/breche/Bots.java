package fr.orbit.breche;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * IA des bots de BRÈCHE. Ils marchent sur la grille de la carte (plus court chemin à 8 directions), voient
 * devant eux (champ de vision, murs et fumées), réagissent avec un temps qui dépend de leur niveau, achètent
 * selon leurs crédits et utilisent les pouvoirs de leur agent. Attaque : foncer sur un site, poser le Noyau,
 * le garder. Défense : tenir un site, puis désamorcer.
 */
final class Bots {

    private static final double VITESSE = 0.2;

    private final Partie partie;
    private final World w;
    private final Plan plan;
    private final Random rnd = new Random();
    private char siteAttaque = 'a';
    /** Premier contact des défenseurs : tick de la rotation (-1 = pas encore de contact) et site attaqué. */
    private int alerte = -1;
    private char alerteSite = 'a';

    Bots(Partie partie) {
        this.partie = partie;
        this.w = partie.w;
        this.plan = partie.plan;
    }

    private int t() {
        return partie.t;
    }

    private List<Combattant> bots() {
        List<Combattant> l = new ArrayList<>();
        for (Combattant c : partie.tous) {
            if (c.bot()) {
                l.add(c);
            }
        }
        return l;
    }

    // ------------------------------------------------------------------ événements de la partie

    void debutManche() {
        siteAttaque = rnd.nextBoolean() ? 'a' : 'b';
        alerte = -1;
        int i = 0;
        for (Combattant b : bots()) {
            b.cible = null;
            b.cibleTourelle = null;
            b.chemin.clear();
            b.but = null;
            b.accroupi = false;
            b.connuJusqu = 0;
            b.reaction = 0;
            b.prochainPouvoir = 0;
            b.attente = 20 + rnd.nextInt(40);
            if (b.equipe == partie.attaque) {
                b.siteBut = rnd.nextInt(5) == 0 ? (siteAttaque == 'a' ? 'b' : 'a') : siteAttaque;
            } else {
                b.siteBut = (i++ % 2 == 0) ? 'a' : 'b';
            }
            acheter(b);
            partie.pl.objets().habillerBot(b);
        }
    }

    void debutCombat() {
        for (Combattant b : bots()) {
            b.attente = b.equipe == partie.attaque ? rnd.nextInt(30) : rnd.nextInt(10);
        }
    }

    void noyauPose() {
        for (Combattant b : bots()) {
            b.chemin.clear();
            b.but = null;
            b.accroupi = false;
        }
    }

    void noyauLache(double x, double z) {
        Combattant mieux = null;
        double best = Double.MAX_VALUE;
        for (Combattant b : bots()) {
            if (b.vivant && b.equipe == partie.attaque) {
                double d = b.dist2(x, z);
                if (d < best) {
                    best = d;
                    mieux = b;
                }
            }
        }
        if (mieux != null) {
            mieux.but = new int[]{plan.col(x), plan.rang(z)};
            mieux.chemin.clear();
        }
    }

    void finManche() {
        for (Combattant b : bots()) {
            b.cible = null;
            b.accroupi = false;
            b.chemin.clear();
        }
    }

    /** Un bot est touché : il se retourne vers le tireur. */
    void touche(Combattant b, Combattant src) {
        Location l = src.pieds(w);
        b.connuX = l.getX();
        b.connuZ = l.getZ();
        b.connuJusqu = t() + 80;
        if (b.cible == null && b.aveugle <= t() && voit(b, src, true)) {
            b.cible = src;
            b.reaction = Math.max(b.reaction, reaction(b) / 2);
        }
        b.accroupi = false;
    }

    // ------------------------------------------------------------------ achats

    void acheter(Combattant b) {
        boolean pistolets = partie.manche == 1 || partie.manche == partie.victoire;
        if (b.primaire == null && !pistolets) {
            Arme choix = null;
            if (b.credits >= 5500 && rnd.nextInt(8) == 0) {
                choix = Arme.ZENITH;
            } else if (b.credits >= 2900) {
                choix = Arme.COMETE;
            } else if (b.credits >= 2000 && rnd.nextBoolean()) {
                choix = rnd.nextInt(3) == 0 ? Arme.BRISE_ROC : Arme.ESSAIM;
            } else if (b.credits >= 1600 && rnd.nextInt(3) == 0) {
                choix = Arme.ESSAIM;
            }
            if (choix != null) {
                b.credits -= choix.prix;
                b.primaire = choix;
            }
        }
        if (b.secondaire == Arme.ETINCELLE && b.primaire == null && b.credits >= 800 && !pistolets && rnd.nextInt(3) == 0) {
            b.credits -= Arme.FAUCON.prix;
            b.secondaire = Arme.FAUCON;
        }
        if (b.bouclier < 50 && b.credits >= 1000 && (b.primaire != null || b.credits >= 2400)) {
            b.credits -= 1000;
            b.bouclier = 50;
        } else if (b.bouclier < 25 && b.credits >= 400 && (pistolets || b.primaire != null)) {
            b.credits -= 400;
            b.bouclier = 25;
        }
        for (int i = 0; i < 3; i++) {
            Agent.Pouvoir pv = b.agent.pouvoirs[i];
            while (pv.prix() > 0 && b.charges[i] < pv.max() && b.credits >= pv.prix() + (pistolets ? 0 : 200)) {
                b.credits -= pv.prix();
                b.charges[i]++;
            }
        }
        b.pleinMunitions();
    }

    // ------------------------------------------------------------------ boucle

    /** Les défenseurs de l'autre site viennent en renfort (un seul reste en place). */
    private void rotation() {
        boolean ancre = false;
        for (Combattant b : bots()) {
            if (!b.vivant || b.equipe == partie.attaque || b.siteBut == alerteSite) {
                continue;
            }
            if (!ancre) {
                ancre = true;
                continue;
            }
            b.siteBut = alerteSite;
            if (b.cible == null) {
                b.but = null;
                b.chemin.clear();
            }
        }
    }

    private char sitePlusProche(Combattant o) {
        Location a = centreSite('a'), s = centreSite('b');
        if (a == null || s == null) {
            return a == null ? 'b' : 'a';
        }
        return o.dist2(a.getX(), a.getZ()) <= o.dist2(s.getX(), s.getZ()) ? 'a' : 'b';
    }

    void tick() {
        if (alerte >= 0 && t() >= alerte && partie.phase == Partie.Phase.COMBAT) {
            alerte = Integer.MAX_VALUE;
            rotation();
        }
        List<Combattant> l = bots();
        for (int i = 0; i < l.size(); i++) {
            Combattant b = l.get(i);
            if (!b.vivant || b.m == null || !b.m.isValid()) {
                continue;
            }
            if (b.bouge > 0) {
                b.bouge--;
            }
            if (partie.phase == Partie.Phase.ACHAT || partie.phase == Partie.Phase.FIN_MANCHE) {
                if ((t() + i * 7) % 40 == 0) {
                    b.yaw += (float) ((rnd.nextDouble() - 0.5) * 60);
                    placer(b);
                }
                continue;
            }
            if (b.gel > t()) {
                continue;
            }
            if ((t() + i) % 4 == 0) {
                percevoir(b);
            }
            if (b.cible != null) {
                combattre(b);
            } else if (b.attente > 0) {
                b.attente--;
            } else {
                objectif(b);
            }
            if (rnd.nextInt(20) == 0) {
                pouvoirsHorsCombat(b);
            }
            placer(b);
        }
    }

    private void placer(Combattant b) {
        b.m.teleport(new Location(w, b.x, Plan.Y, b.z, b.yaw, b.pitch));
        b.m.setSneaking(b.accroupi);
    }

    private int reaction(Combattant b) {
        return switch (b.niveau) {
            case 1 -> 16 + rnd.nextInt(12);
            case 3 -> 5 + rnd.nextInt(7);
            default -> 10 + rnd.nextInt(9);
        };
    }

    // ------------------------------------------------------------------ perception

    private boolean voit(Combattant b, Combattant o, boolean sansChamp) {
        if (b.aveugle > t()) {
            return false;
        }
        Location de = b.oeil(w), a = o.oeil(w).add(0, -0.3, 0);
        double d = de.distance(a);
        double portee = b.myope > t() ? 7 : 48;
        if (d > portee) {
            return false;
        }
        if (!sansChamp && d > 4 && o.revele <= t()) {
            Vector regard = new Vector(-Math.sin(Math.toRadians(b.yaw)), 0, Math.cos(Math.toRadians(b.yaw)));
            Vector vers = a.toVector().subtract(de.toVector()).setY(0);
            if (vers.lengthSquared() > 1e-4 && regard.dot(vers.normalize()) < 0.3) {
                return false;
            }
        }
        return partie.pouvoirs.vue(de, a) && !partie.pouvoirs.fumeeEntre(de, a);
    }

    private void percevoir(Combattant b) {
        Combattant mieux = null;
        double best = Double.MAX_VALUE;
        for (Combattant o : partie.tous) {
            if (!o.vivant || o.equipe == b.equipe) {
                continue;
            }
            if (o.revele > t()) {
                Location l = o.pieds(w);
                b.connuX = l.getX();
                b.connuZ = l.getZ();
                b.connuJusqu = t() + 40;
            }
            if (!voit(b, o, false)) {
                continue;
            }
            double d = b.dist2(o.px(), o.pz());
            if (d < best) {
                best = d;
                mieux = o;
            }
        }
        if (mieux != b.cible) {
            if (b.cible != null && mieux == null) {
                Location l = b.cible.pieds(w);
                b.connuX = l.getX();
                b.connuZ = l.getZ();
                b.connuJusqu = t() + 60;
            }
            if (mieux != null && b.cible == null) {
                if (alerte < 0 && b.equipe != partie.attaque) {
                    alerteSite = sitePlusProche(mieux);
                    alerte = t() + 50;
                }
                b.reaction = reaction(b);
                b.accroupi = false;
                pouvoirsEnVue(b, mieux);
            }
            b.cible = mieux;
        }
        b.cibleTourelle = null;
        if (mieux == null && b.aveugle <= t()) {
            for (Pouvoirs.Tourelle tr : partie.pouvoirs.tourelles) {
                if (tr.src.equipe != b.equipe && tr.pos.distanceSquared(b.oeil(w)) < 25 * 25
                        && partie.pouvoirs.vue(b.oeil(w), tr.pos.clone().add(0, 0.6, 0))) {
                    b.cibleTourelle = tr;
                    break;
                }
            }
        }
    }

    // ------------------------------------------------------------------ combat

    private void viser(Combattant b, Location a) {
        Location de = b.oeil(w);
        double dx = a.getX() - de.getX(), dy = a.getY() - de.getY(), dz = a.getZ() - de.getZ();
        b.yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        b.pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
    }

    private void combattre(Combattant b) {
        Combattant v = b.cible;
        if (!v.vivant || b.aveugle > t()) {
            b.cible = null;
            return;
        }
        viser(b, v.oeil(w).add(0, -0.35, 0));
        // petit pas de côté de temps en temps
        if (b.niveau >= 2 && rnd.nextInt(18) == 0) {
            b.bouge = 8;
            b.reflexion = rnd.nextBoolean() ? 1 : -1;
        }
        if (b.bouge > 0) {
            double a = Math.toRadians(b.yaw + 90 * b.reflexion);
            pas(b, -Math.sin(a) * 0.09, Math.cos(a) * 0.09);
        }
        if (b.reaction > 0) {
            b.reaction--;
            return;
        }
        if (b.arme() == null) {
            b.secondaire = Arme.ETINCELLE;
        }
        partie.tir.tirBot(b, v);
        if (b.agent == Agent.SOLENE && b.hp < 35 && b.charges[2] > 0 && rnd.nextInt(10) == 0) {
            Vector loin = b.pieds(w).toVector().subtract(v.pieds(w).toVector()).setY(0);
            if (loin.lengthSquared() > 0.01) {
                partie.pouvoirs.lancer(b, 2, b.oeil(w), loin.normalize());
            }
        }
    }

    private void pas(Combattant b, double dx, double dz) {
        double vit = b.lent > t() ? 0.5 : 1.0;
        double nx = b.x + dx * vit, nz = b.z + dz * vit;
        if (plan.libre(plan.col(nx), plan.rang(nz), partie.carte.bloque)) {
            b.x = nx;
            b.z = nz;
        }
    }

    // ------------------------------------------------------------------ objectifs

    private void objectif(Combattant b) {
        boolean att = b.equipe == partie.attaque;
        b.accroupi = false;
        if (b.cibleTourelle instanceof Pouvoirs.Tourelle tr) {
            viser(b, tr.pos.clone().add(0, 0.6, 0));
            partie.tir.tirBotObjet(b, tr.pos.clone().add(0, 0.6, 0), tr.boite);
            return;
        }
        if (partie.phase == Partie.Phase.NOYAU) {
            if (att) {
                garder(b, partie.noyauX, partie.noyauZ, 6);
            } else {
                if (b.dist2(partie.noyauX, partie.noyauZ) <= 1.6 * 1.6) {
                    b.accroupi = true;
                    b.chemin.clear();
                    regarderVers(b, partie.noyauX, partie.noyauZ);
                } else {
                    aller(b, partie.noyauX, partie.noyauZ);
                }
            }
            return;
        }
        // Noyau au sol : l'attaquant le plus proche va le chercher, avant toute autre chose
        if (att && doitRamasser(b)) {
            aller(b, partie.noyauX, partie.noyauZ);
            return;
        }
        // traque d'un ennemi entendu ou révélé
        if (b.connuJusqu > t() && !b.porteNoyau && b.dist2(b.connuX, b.connuZ) < 22 * 22 && (att || rnd.nextInt(3) > 0)) {
            if (b.dist2(b.connuX, b.connuZ) < 2) {
                b.connuJusqu = 0;
            } else {
                regarderVers(b, b.connuX, b.connuZ);
                if (att || b.dist2(b.connuX, b.connuZ) > 64) {
                    aller(b, b.connuX, b.connuZ);
                }
                return;
            }
        }
        if (att) {
            if (b.porteNoyau && plan.site(b.x, b.z) != 0) {
                b.accroupi = true;
                b.chemin.clear();
                return;
            }
            tenirSite(b, b.siteBut, !b.porteNoyau);
        } else {
            tenirSite(b, b.siteBut, true);
        }
    }

    /** Noyau au sol : vrai pour le bot attaquant vivant le plus proche du Noyau. */
    private boolean doitRamasser(Combattant b) {
        if (!partie.noyauAuSol || partie.phase != Partie.Phase.COMBAT) {
            return false;
        }
        double d = b.dist2(partie.noyauX, partie.noyauZ);
        for (Combattant o : bots()) {
            if (o != b && o.vivant && o.equipe == b.equipe && o.dist2(partie.noyauX, partie.noyauZ) < d) {
                return false;
            }
        }
        return true;
    }

    /** Va sur une case du site, puis y reste en se replaçant de temps en temps. */
    private void tenirSite(Combattant b, char s, boolean bouger) {
        List<int[]> cells = plan.site(s);
        if (cells.isEmpty()) {
            return;
        }
        // un but hérité d'autre chose (traque, Noyau ramassé) n'est pas sur le site : on en reprend un
        if (b.but == null || plan.site(plan.wx(b.but[0]), plan.wz(b.but[1])) != s || (b.chemin.isEmpty() && bouger && rnd.nextInt(220) == 0)) {
            b.but = cells.get(rnd.nextInt(cells.size()));
            b.chemin.clear();
        }
        if (!suivre(b, b.but)) {
            if (rnd.nextInt(60) == 0) {
                b.yaw += (float) ((rnd.nextDouble() - 0.5) * 120);
            }
        }
    }

    private void garder(Combattant b, double x, double z, double r) {
        if (b.but == null || (b.chemin.isEmpty() && rnd.nextInt(160) == 0)) {
            double a = rnd.nextDouble() * Math.PI * 2, rr = 2 + rnd.nextDouble() * r;
            int[] cell = plan.proche(plan.col(x + Math.cos(a) * rr), plan.rang(z + Math.sin(a) * rr), partie.carte.bloque);
            b.but = cell;
            b.chemin.clear();
        }
        if (b.but != null && !suivre(b, b.but) && rnd.nextInt(50) == 0) {
            b.yaw += (float) ((rnd.nextDouble() - 0.5) * 140);
        }
    }

    private void aller(Combattant b, double x, double z) {
        int[] cell = plan.proche(plan.col(x), plan.rang(z), partie.carte.bloque);
        if (cell == null) {
            return;
        }
        if (b.but == null || b.but[0] != cell[0] || b.but[1] != cell[1]) {
            b.but = cell;
            b.chemin.clear();
        }
        suivre(b, b.but);
    }

    /** Avance d'un pas vers la case but. Faux quand le bot y est (ou ne peut pas y aller). */
    private boolean suivre(Combattant b, int[] but) {
        int c0 = plan.col(b.x), r0 = plan.rang(b.z);
        if (c0 == but[0] && r0 == but[1] && b.chemin.isEmpty()) {
            return false;
        }
        if (b.chemin.isEmpty()) {
            if (!plan.libre(c0, r0, partie.carte.bloque)) {
                int[] p = plan.proche(c0, r0, partie.carte.bloque);
                if (p == null) {
                    return false;
                }
                c0 = p[0];
                r0 = p[1];
            }
            b.chemin = plan.chemin(c0, r0, but[0], but[1], partie.carte.bloque);
            if (b.chemin.isEmpty()) {
                b.but = null;
                return false;
            }
        }
        int[] n = b.chemin.getFirst();
        if (partie.carte.bloque[n[1]][n[0]]) {
            b.chemin.clear();
            return true;
        }
        double tx = plan.wx(n[0]), tz = plan.wz(n[1]);
        double dx = tx - b.x, dz = tz - b.z, d = Math.hypot(dx, dz);
        double v = VITESSE * (b.lent > t() ? 0.45 : 1.0) * (b.accroupi ? 0.5 : 1.0);
        if (d <= v) {
            b.x = tx;
            b.z = tz;
            b.chemin.removeFirst();
        } else {
            b.x += dx / d * v;
            b.z += dz / d * v;
            float cible = (float) Math.toDegrees(Math.atan2(-dx, dz));
            float diff = ((cible - b.yaw) % 360 + 540) % 360 - 180;
            b.yaw += Math.max(-25, Math.min(25, diff));
            b.pitch = 0;
        }
        b.bouge = 2;
        return true;
    }

    private void regarderVers(Combattant b, double x, double z) {
        b.yaw = (float) Math.toDegrees(Math.atan2(-(x - b.x), z - b.z));
        b.pitch = 10;
    }

    // ------------------------------------------------------------------ pouvoirs

    /** Direction pour lancer un objet en cloche vers un point (approximation : plus loin = plus haut). */
    private Vector cloche(Combattant b, Location cible) {
        Location de = b.oeil(w);
        Vector h = cible.toVector().subtract(de.toVector()).setY(0);
        double d = h.length();
        if (d < 0.1) {
            return new Vector(0, -1, 0);
        }
        h.normalize();
        double angle = Math.max(0.0, Math.min(0.75, d * 0.032 - 0.1));
        return h.multiply(Math.cos(angle)).setY(Math.sin(angle)).normalize();
    }

    private Vector droit(Combattant b, Location cible) {
        return cible.toVector().subtract(b.oeil(w).toVector()).normalize();
    }

    private boolean pret(Combattant b, int slot) {
        Agent.Pouvoir pv = b.agent.pouvoirs[slot];
        return pv.ultime() ? b.ult >= pv.max() : b.charges[slot] > 0;
    }

    private boolean essayer(Combattant b, int slot, Vector dir, double chance) {
        if (b.prochainPouvoir > t() || !pret(b, slot) || rnd.nextDouble() >= chance) {
            return false;
        }
        if (partie.pouvoirs.lancer(b, slot, b.oeil(w), dir)) {
            b.prochainPouvoir = t() + 40 + rnd.nextInt(40);
            return true;
        }
        return false;
    }

    /** Pouvoirs utilisés en voyant un ennemi. */
    private void pouvoirsEnVue(Combattant b, Combattant v) {
        Location vl = v.pieds(w);
        double d = Math.sqrt(b.dist2(vl.getX(), vl.getZ()));
        Location tete = v.oeil(w);
        switch (b.agent) {
            case SOLENE -> {
                if (essayer(b, 3, droit(b, tete), 0.5)) {
                    return;
                }
                if (d > 6 && d < 22 && essayer(b, 1, droit(b, tete), 0.35)) {
                    return;
                }
                if (d > 5 && d < 18) {
                    essayer(b, 0, cloche(b, vl), 0.3);
                }
            }
            case ORYX -> {
                if (partie.vivants(v.equipe) >= 2 && essayer(b, 3, droit(b, tete), 0.4)) {
                    return;
                }
                if (d > 12 && essayer(b, 2, droit(b, vl), 0.3)) {
                    return;
                }
                if (d > 5 && d < 18) {
                    essayer(b, 0, cloche(b, vl), 0.3);
                }
            }
            case KAEL -> {
                if (d < 10 && essayer(b, 3, droit(b, tete), 0.6)) {
                    return;
                }
                if (d > 4 && d < 15) {
                    essayer(b, 1, cloche(b, vl), 0.25);
                }
            }
            case ECHO -> {
                if (essayer(b, 3, droit(b, tete), 0.4)) {
                    return;
                }
                if (d > 5 && d < 18 && essayer(b, 0, cloche(b, vl), 0.35)) {
                    return;
                }
                if (d > 5 && d < 18) {
                    essayer(b, 1, cloche(b, vl), 0.3);
                }
            }
            case NAIA -> {
                if (d > 5 && d < 18 && essayer(b, 1, cloche(b, vl), 0.3)) {
                    return;
                }
                if (b.hp < 50 && d > 4 && d < 9) {
                    essayer(b, 0, droit(b, vl), 0.3);
                }
            }
        }
    }

    /** Pouvoirs de préparation (sans ennemi en vue). */
    private void pouvoirsHorsCombat(Combattant b) {
        if (b.cible != null || b.prochainPouvoir > t()) {
            return;
        }
        boolean att = b.equipe == partie.attaque;
        Location siteC = centreSite(b.siteBut);
        double dSite = siteC == null ? 999 : Math.sqrt(b.dist2(siteC.getX(), siteC.getZ()));
        Vector regard = new Vector(-Math.sin(Math.toRadians(b.yaw)), 0, Math.cos(Math.toRadians(b.yaw)));
        switch (b.agent) {
            case KAEL -> {
                if (b.chemin.isEmpty() && (!att || dSite < 8)) {
                    if (!essayer(b, 2, regard, 0.5) && !att) {
                        essayer(b, 0, regard, 0.3);
                    }
                }
            }
            case NAIA -> {
                if (b.hp < 60) {
                    essayer(b, 2, regard.clone().setY(-1), 0.8);
                }
                if (pret(b, 3)) {
                    for (Combattant o : partie.tous) {
                        if (!o.vivant && o.equipe == b.equipe && b.dist2(o.mortX, o.mortZ) < 20 * 20) {
                            Vector dir = new Location(w, o.mortX, Plan.Y, o.mortZ).toVector().subtract(b.oeil(w).toVector()).normalize();
                            essayer(b, 3, dir, 0.7);
                            break;
                        }
                    }
                }
            }
            case ECHO -> {
                if (att && siteC != null && dSite < 20 && dSite > 8) {
                    essayer(b, 2, droit(b, siteC.clone().add(0, 1, 0)), 0.4);
                }
            }
            case ORYX -> {
                if (att && siteC != null && dSite < 26 && dSite > 10) {
                    Location p = b.pieds(w).add(siteC.toVector().subtract(b.pieds(w).toVector()).multiply(0.55));
                    essayer(b, 2, droit(b, p), 0.35);
                }
            }
            default -> {
            }
        }
    }

    private Location centreSite(char s) {
        List<int[]> cells = plan.site(s);
        if (cells.isEmpty()) {
            return null;
        }
        double sx = 0, sz = 0;
        for (int[] c : cells) {
            sx += c[0];
            sz += c[1];
        }
        return new Location(w, plan.wx((int) Math.round(sx / cells.size())), Plan.Y, plan.wz((int) Math.round(sz / cells.size())));
    }
}
