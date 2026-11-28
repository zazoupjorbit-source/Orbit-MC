package fr.orbit.bowl.laser;

import fr.orbit.bowl.Activite;
import fr.orbit.bowl.Jeu;
import fr.orbit.bowl.Mode;
import fr.orbit.bowl.OrbitBowl;
import fr.orbit.bowl.Poste;
import fr.orbit.bowl.Salle;
import fr.orbit.bowl.Txt;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Laser Nova : Bleu contre Or dans l'arène néon. Les places vides sont tenues par des bots (déplacement sur la grille
 * de l'arène, tir avec un temps de réaction et une précision limitée). Touché = désactivé 3 secondes, +10 pts au tireur.
 */
public final class PartieLaser extends Activite {

    private static final int DEPART = 100, HORS_JEU = 60, PROTECTION = 30, CADENCE = 8, CADENCE_BOT = 26;
    private static final double PORTEE = 40, VITESSE_BOT = 0.17;
    private static final String[] NOMS = {"Nova", "Pulsar", "Quasar", "Zénith", "Vega", "Orion", "Lyra", "Sirius", "Altaïr", "Rigel"};
    private static final String[] COUL = {"#3A7FFF", "#E0B864"};
    private static final String[] EQ = {"Bleu", "Or"};

    private final class Combattant {
        final Player p;
        Mannequin m;
        final String nom;
        int equipe, points, touches, subis, horsJeu, protege, recharge;
        // bot
        double x, z;
        float yaw;
        LinkedList<int[]> chemin = new LinkedList<>();
        Combattant visee;
        int reaction, reflexion;

        Combattant(Player p, String nom) {
            this.p = p;
            this.nom = nom;
        }

        Entity corps() {
            return p != null ? p : m;
        }

        boolean actif() {
            return horsJeu <= 0;
        }

        Location oeil() {
            return p != null ? p.getEyeLocation() : new Location(w, x, Salle.Y + 1.55, z, yaw, 0f);
        }

        Location pieds() {
            return p != null ? p.getLocation() : new Location(w, x, Salle.Y, z, yaw, 0f);
        }

        String nomCouleur() {
            return "<" + COUL[equipe] + ">" + nom + "</" + COUL[equipe] + ">";
        }
    }

    private final List<Combattant> tous = new ArrayList<>();
    private final Map<UUID, Combattant> parEntite = new HashMap<>();
    private final int[] score = new int[2];
    private final Random rnd = new Random();
    private int reste, duree;
    private TextDisplay tableauSalle, tableauNord;

    public PartieLaser(OrbitBowl pl, Poste poste, Mode mode, long mise) {
        super(pl, poste, mode, mise);
    }

    @Override
    protected int attenteTicks() {
        return pl.getConfig().getInt("laser.attente", 20) * 20;
    }

    @Override
    protected boolean lancementAuto() {
        return !joueurs.isEmpty();
    }

    @Override
    protected void equiper(Player p) {
        p.getInventory().setItem(0, objet(Material.BLAZE_ROD, "pistolet_laser", "<#FF4FD8><b>Pistolet Nova</b>",
                "Clic : tir laser", "Touché = 3 s hors jeu", "+10 pts par adversaire touché"));
        p.getInventory().setHeldItemSlot(0);
    }

    @Override
    protected void accueillir(Player p) {
        if (phase == Phase.ATTENTE && mode.max() > 1) {
            Txt.send(p, "<#9AA4B2>Laser Nova : départ dans " + Txt.chrono(attenteTicks()) + " max (bots en renfort). Le créateur peut lancer tout de suite.");
        }
    }

    // ------------------------------------------------------------------ départ

    @Override
    protected void commencer() {
        int parEquipe = Math.max(1, Math.min(6, pl.getConfig().getInt("laser.equipe", 4)));
        parEquipe = Math.max(parEquipe, (joueurs.size() + 1) / 2);
        duree = Math.max(60, pl.getConfig().getInt("laser.duree", 300)) * 20;
        int i = 0;
        for (Player p : joueurs) {
            Combattant c = new Combattant(p, p.getName());
            c.equipe = i++ % 2;
            tous.add(c);
            parEntite.put(p.getUniqueId(), c);
        }
        int[] compte = new int[2];
        for (Combattant c : tous) {
            compte[c.equipe]++;
        }
        List<String> noms = new ArrayList<>(List.of(NOMS));
        java.util.Collections.shuffle(noms, rnd);
        int k = 0;
        for (int e = 0; e < 2; e++) {
            while (compte[e] < parEquipe) {
                Combattant b = new Combattant(null, "Bot " + noms.get(k++ % noms.size()));
                b.equipe = e;
                tous.add(b);
                compte[e]++;
            }
        }
        int[] n = new int[2];
        for (Combattant c : tous) {
            Location l = LaserNova.depart(w, c.equipe, n[c.equipe]++);
            if (c.p != null) {
                pl.teleporter(c.p, l);
                habiller(c);
                Txt.title(c.p, Jeu.LASER.titre(), "<#E6EDF3>Équipe <" + COUL[c.equipe] + "><b>" + EQ[c.equipe] + "</b></" + COUL[c.equipe] + ">", 2200);
            } else {
                c.x = l.getX();
                c.z = l.getZ();
                c.yaw = l.getYaw();
                c.m = garder(w.spawn(l, Mannequin.class, m -> {
                    OrbitBowl.marquer(m);
                    m.setAI(false);
                    m.setGravity(false);
                    m.setInvulnerable(true);
                    m.setSilent(true);
                    m.setCollidable(false);
                    m.setImmovable(true);
                    m.customName(Txt.mm("<#9AA4B2>[bot]</#9AA4B2> <" + COUL[c.equipe] + ">" + c.nom));
                    m.setCustomNameVisible(true);
                    try {
                        m.setDescription(null);
                    } catch (RuntimeException ignored) {
                        m.setDescription(net.kyori.adventure.text.Component.empty());
                    }
                }));
                parEntite.put(c.m.getUniqueId(), c);
                habiller(c);
                c.m.getEquipment().setItemInMainHand(objet(Material.BLAZE_ROD, "pistolet_laser", ""));
                c.reflexion = rnd.nextInt(20);
            }
        }
        tableauSalle = texte(new Location(w, 18.5, Salle.Y + 7.4, Salle.Z2 - 0.45, 180f, 0f), "", 1.2f, false);
        tableauNord = texte(new Location(w, 18.5, Salle.Y + 4.3, LaserNova.Z0 + LaserNova.N - 0.45, 180f, 0f), "", 1.1f, false);
        reste = duree + DEPART;
        annoncer("<#FF4FD8>Laser Nova</#FF4FD8> <#9AA4B2>· " + parEquipe + " contre " + parEquipe + " · " + Txt.chrono(duree) + ". Touche les "
                + "adversaires, évite leurs tirs !");
    }

    private ItemStack veste(Color c) {
        ItemStack it = new ItemStack(Material.LEATHER_CHESTPLATE);
        LeatherArmorMeta meta = (LeatherArmorMeta) it.getItemMeta();
        meta.setColor(c);
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack casque(Color c) {
        ItemStack it = new ItemStack(Material.LEATHER_HELMET);
        LeatherArmorMeta meta = (LeatherArmorMeta) it.getItemMeta();
        meta.setColor(c);
        it.setItemMeta(meta);
        return it;
    }

    private void habiller(Combattant c) {
        Color col = !c.actif() ? Color.fromRGB(0x3A3F4E) : c.equipe == 0 ? Color.fromRGB(0x3A7FFF) : Color.fromRGB(0xE0B864);
        if (c.p != null) {
            c.p.getInventory().setChestplate(veste(col));
            c.p.getInventory().setHelmet(casque(col));
        } else if (c.m != null && c.m.isValid()) {
            c.m.getEquipment().setChestplate(veste(col));
            c.m.getEquipment().setHelmet(casque(col));
        }
    }

    // ------------------------------------------------------------------ boucle

    @Override
    protected void tick() {
        reste--;
        if (reste > duree) {
            int s = (reste - duree + 19) / 20;
            if ((reste - duree) % 20 == 0) {
                titre("<#FF4FD8><b>" + s + "</b>", "", 900);
                for (Combattant c : tous) {
                    if (c.p != null) {
                        c.p.playSound(c.p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 1.2f);
                    }
                }
            }
            garderEnBase();
            return;
        }
        if (reste == duree) {
            titre("<gradient:#FF4FD8:#62CFF4><b>GO !</b></gradient>", "", 800);
            for (Combattant c : tous) {
                if (c.p != null) {
                    c.p.playSound(c.p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.6f);
                }
            }
        }
        for (Combattant c : tous) {
            if (c.recharge > 0) {
                c.recharge--;
            }
            if (c.protege > 0) {
                c.protege--;
            }
            if (c.horsJeu > 0 && --c.horsJeu == 0) {
                c.protege = PROTECTION;
                habiller(c);
                if (c.p != null) {
                    c.p.playSound(c.p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.7f, 1.6f);
                    Txt.bar(c.p, "<#7CFF8A>Réactivé !");
                }
            }
            if (c.p == null) {
                bot(c);
            }
        }
        if (t % 5 == 0) {
            affichage();
        }
        if (reste <= 0) {
            finPartie();
        }
    }

    /** Pendant le compte à rebours, les joueurs restent dans leur base. */
    private void garderEnBase() {
        for (Combattant c : tous) {
            if (c.p == null) {
                continue;
            }
            Location l = c.p.getLocation();
            int r = LaserNova.rang(l.getZ());
            boolean dehors = c.equipe == 0 ? r > 2 : r < LaserNova.N - 3;
            if (dehors) {
                Location to = l.clone();
                to.setZ(LaserNova.z(c.equipe == 0 ? 1 : LaserNova.N - 2));
                pl.teleporter(c.p, to);
            }
        }
    }

    private void affichage() {
        String tab = "<gradient:#3A7FFF:#62CFF4><b>BLEU " + score[0] + "</b></gradient> <#5A6270>—</#5A6270> <gradient:#E0B864:#FF8A3D><b>"
                + score[1] + " OR</b></gradient>\n<#E6EDF3>" + Txt.chrono(Math.min(reste, duree));
        if (tableauSalle != null) {
            tableauSalle.text(Txt.mm(Jeu.LASER.grad("<b>LASER NOVA</b>") + "\n" + tab));
        }
        if (tableauNord != null) {
            tableauNord.text(Txt.mm(tab));
        }
        for (Combattant c : tous) {
            if (c.p == null) {
                continue;
            }
            String etat = !c.actif() ? "<#E0484A>hors jeu " + ((c.horsJeu + 19) / 20) + " s" : c.recharge > 0 ? "<#9AA4B2>recharge" : "<#7CFF8A>prêt";
            Txt.bar(c.p, "<#3A7FFF>Bleu " + score[0] + "</#3A7FFF> <#5A6270>·</#5A6270> <#E0B864>Or " + score[1] + "</#E0B864> <#5A6270>·</#5A6270> <#E6EDF3>"
                    + Txt.chrono(Math.min(reste, duree)) + "</#E6EDF3> <#5A6270>·</#5A6270> toi <#FF4FD8>" + c.points + "</#FF4FD8> <#5A6270>·</#5A6270> " + etat);
        }
    }

    // ------------------------------------------------------------------ tirs

    @Override
    public void clic(Player p, boolean gauche) {
        Combattant c = parEntite.get(p.getUniqueId());
        if (c == null || reste > duree || reste <= 0) {
            return;
        }
        if (!c.actif()) {
            p.playSound(p.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 0.6f, 1.6f);
            return;
        }
        if (c.recharge > 0) {
            return;
        }
        c.recharge = CADENCE;
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection();
        RayTraceResult res = w.rayTrace(eye, dir, PORTEE, FluidCollisionMode.NEVER, true, 0.25, e -> {
            Combattant o = parEntite.get(e.getUniqueId());
            return o != null && o.equipe != c.equipe && !e.equals(p);
        });
        Location fin = res == null ? eye.clone().add(dir.clone().multiply(PORTEE)) : res.getHitPosition().toLocation(w);
        rayon(eye.clone().add(dir.clone().multiply(0.6)), fin, c.equipe);
        son(eye, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.7f, 2f);
        if (res != null && res.getHitEntity() != null) {
            Combattant cible = parEntite.get(res.getHitEntity().getUniqueId());
            if (cible != null) {
                toucher(c, cible);
            }
        }
    }

    private void rayon(Location de, Location a, int equipe) {
        Vector d = a.toVector().subtract(de.toVector());
        double len = d.length();
        if (len < 0.1) {
            return;
        }
        int n = (int) Math.min(80, len * 2.5);
        Vector pas = d.multiply(1.0 / n);
        Location l = de.clone();
        Particle.DustOptions opt = new Particle.DustOptions(equipe == 0 ? Color.fromRGB(0x62CFF4) : Color.fromRGB(0xFFB84D), 0.55f);
        for (int i = 0; i < n; i++) {
            w.spawnParticle(Particle.DUST, l, 1, 0, 0, 0, 0, opt);
            l.add(pas);
        }
        w.spawnParticle(Particle.ELECTRIC_SPARK, a, 4, 0.05, 0.05, 0.05, 0.02);
    }

    private void toucher(Combattant tireur, Combattant cible) {
        if (!cible.actif() || cible.protege > 0) {
            if (tireur.p != null) {
                Txt.bar(tireur.p, "<#9AA4B2>" + cible.nom + " est déjà hors jeu");
            }
            return;
        }
        cible.horsJeu = HORS_JEU;
        cible.subis++;
        cible.visee = null;
        cible.chemin.clear();
        habiller(cible);
        tireur.points += 10;
        tireur.touches++;
        score[tireur.equipe] += 10;
        Location at = cible.oeil();
        w.spawnParticle(Particle.FIREWORK, at, 14, 0.2, 0.3, 0.2, 0.05);
        son(at, Sound.ENTITY_BLAZE_HURT, 0.6f, 1.8f);
        if (tireur.p != null) {
            tireur.p.playSound(tireur.p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f, 1.4f);
            Txt.title(tireur.p, "", "<#7CFF8A>Touché : " + cible.nomCouleur() + " <#E0B864>+10", 700);
        }
        if (cible.p != null) {
            cible.p.playSound(cible.p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.9f, 1.4f);
            Txt.title(cible.p, "<#E0484A><b>TOUCHÉ</b>", "<#E6EDF3>par " + tireur.nomCouleur() + " · 3 s hors jeu", 1200);
        }
    }

    // ------------------------------------------------------------------ bots

    private void bot(Combattant b) {
        if (b.m == null || !b.m.isValid()) {
            return;
        }
        if (!b.actif()) {
            return;
        }
        // repérer une cible de temps en temps
        if (--b.reflexion <= 0) {
            b.reflexion = 5;
            Combattant vue = chercher(b);
            if (vue != b.visee) {
                b.visee = vue;
                b.reaction = 10 + rnd.nextInt(14);
            }
        }
        if (b.visee != null) {
            Combattant v = b.visee;
            if (!v.actif() || !voit(b, v)) {
                b.visee = null;
            } else {
                Location o = v.pieds();
                b.yaw = (float) Math.toDegrees(Math.atan2(-(o.getX() - b.x), o.getZ() - b.z));
                b.m.teleport(new Location(w, b.x, Salle.Y, b.z, b.yaw, 0f));
                if (--b.reaction <= 0 && b.recharge <= 0) {
                    tirBot(b, v);
                }
                return;
            }
        }
        avancer(b);
    }

    private Combattant chercher(Combattant b) {
        Combattant mieux = null;
        double best = 24 * 24;
        for (Combattant o : tous) {
            if (o.equipe == b.equipe || !o.actif() || o.protege > 0) {
                continue;
            }
            Location l = o.pieds();
            double d = (l.getX() - b.x) * (l.getX() - b.x) + (l.getZ() - b.z) * (l.getZ() - b.z);
            if (d < best && voit(b, o)) {
                best = d;
                mieux = o;
            }
        }
        return mieux;
    }

    private boolean voit(Combattant b, Combattant o) {
        Location de = b.oeil(), a = o.oeil().add(0, -0.3, 0);
        Vector d = a.toVector().subtract(de.toVector());
        double len = d.length();
        if (len > 26 || len < 0.1) {
            return len < 0.1;
        }
        return w.rayTraceBlocks(de, d.multiply(1 / len), len, FluidCollisionMode.NEVER, true) == null;
    }

    private void tirBot(Combattant b, Combattant v) {
        b.recharge = CADENCE_BOT + rnd.nextInt(14);
        b.reaction = 4 + rnd.nextInt(8);
        Location de = b.oeil();
        Location a = v.oeil().add(0, -0.35, 0);
        double dist = de.distance(a);
        double chance = clamp(0.62 - dist * 0.014, 0.22, 0.6);
        // un joueur qui bouge est plus dur à toucher
        if (v.p != null && v.p.getVelocity().setY(0).lengthSquared() > 0.004) {
            chance *= 0.7;
        }
        boolean touche = rnd.nextDouble() < chance;
        if (!touche) {
            a.add((rnd.nextDouble() - 0.5) * 1.6, (rnd.nextDouble() - 0.3) * 0.9, (rnd.nextDouble() - 0.5) * 1.6);
        }
        rayon(de.clone().add(a.toVector().subtract(de.toVector()).normalize().multiply(0.5)), a, b.equipe);
        son(de, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.6f, 1.8f);
        if (touche) {
            toucher(b, v);
        }
    }

    private void avancer(Combattant b) {
        if (b.chemin.isEmpty()) {
            if (rnd.nextInt(6) != 0) {
                return; // petite pause entre deux trajets
            }
            int[] but = destination(b);
            b.chemin = chemin(LaserNova.col(b.x), LaserNova.rang(b.z), but[0], but[1]);
            if (b.chemin.isEmpty()) {
                return;
            }
        }
        int[] n = b.chemin.getFirst();
        double tx = LaserNova.x(n[0]), tz = LaserNova.z(n[1]);
        double dx = tx - b.x, dz = tz - b.z, d = Math.hypot(dx, dz);
        if (d <= VITESSE_BOT) {
            b.x = tx;
            b.z = tz;
            b.chemin.removeFirst();
        } else {
            b.x += dx / d * VITESSE_BOT;
            b.z += dz / d * VITESSE_BOT;
            b.yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        }
        b.m.teleport(new Location(w, b.x, Salle.Y, b.z, b.yaw, 0f));
    }

    /** Une case libre au hasard, plutôt vers le camp adverse. */
    private int[] destination(Combattant b) {
        for (int essai = 0; essai < 40; essai++) {
            int c = rnd.nextInt(LaserNova.N);
            int r = b.equipe == 0 ? 6 + rnd.nextInt(LaserNova.N - 9) : 3 + rnd.nextInt(LaserNova.N - 9);
            if (LaserNova.libre(c, r)) {
                return new int[]{c, r};
            }
        }
        return new int[]{LaserNova.N / 2, LaserNova.N / 2 - 2};
    }

    /** Plus court chemin (4 directions) sur la grille de l'arène. */
    private static LinkedList<int[]> chemin(int c0, int r0, int c1, int r1) {
        LinkedList<int[]> res = new LinkedList<>();
        int n = LaserNova.N;
        if (!LaserNova.libre(c0, r0) || !LaserNova.libre(c1, r1)) {
            return res;
        }
        int[] prec = new int[n * n];
        java.util.Arrays.fill(prec, -1);
        ArrayDeque<Integer> file = new ArrayDeque<>();
        int dep = c0 * n + r0, arr = c1 * n + r1;
        prec[dep] = dep;
        file.add(dep);
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!file.isEmpty()) {
            int cur = file.poll();
            if (cur == arr) {
                break;
            }
            int c = cur / n, r = cur % n;
            for (int[] d : dirs) {
                int nc = c + d[0], nr = r + d[1];
                if (LaserNova.libre(nc, nr) && prec[nc * n + nr] < 0) {
                    prec[nc * n + nr] = cur;
                    file.add(nc * n + nr);
                }
            }
        }
        if (prec[arr] < 0) {
            return res;
        }
        for (int cur = arr; cur != dep; cur = prec[cur]) {
            res.addFirst(new int[]{cur / n, cur % n});
        }
        return res;
    }

    // ------------------------------------------------------------------ fin

    private void finPartie() {
        int gagnant = score[0] == score[1] ? -1 : score[0] > score[1] ? 0 : 1;
        titre(gagnant < 0 ? "<#E6EDF3><b>ÉGALITÉ</b>" : "<" + COUL[gagnant] + "><b>VICTOIRE " + EQ[gagnant].toUpperCase() + "</b>",
                "<#3A7FFF>Bleu " + score[0] + "</#3A7FFF> <#5A6270>—</#5A6270> <#E0B864>" + score[1] + " Or", 2600);
        List<Combattant> classement = new ArrayList<>(tous);
        classement.sort(Comparator.comparingInt((Combattant c) -> -c.points).thenComparingInt(c -> c.subis));
        annoncer("<#FF4FD8><b>Laser Nova</b></#FF4FD8> <#9AA4B2>· fin de partie :");
        int rang = 1;
        for (Combattant c : classement) {
            annoncer("<#E6EDF3>" + rang++ + ". " + c.nomCouleur() + " <#5A6270>·</#5A6270> <#E0B864>" + c.points + " pts</#E0B864> <#9AA4B2>(" + c.touches
                    + " touché(s), " + c.subis + " fois hors jeu)");
        }
        for (Combattant c : tous) {
            if (c.p == null || !c.p.isOnline()) {
                continue;
            }
            boolean gagne = gagnant == c.equipe;
            pl.scores().enregistrer(c.p, Jeu.LASER, c.points, c.points / 5 + (gagne ? 25 : 5),
                    "laser " + c.points + " pts · " + (gagnant < 0 ? "égalité" : gagne ? "victoire" : "défaite"), true);
        }
        terminer();
    }

    /** En fin de partie, tout le monde ressort de l'arène (la console est de l'autre côté du mur). */
    @Override
    protected boolean loin(Player p) {
        return true;
    }

    @Override
    protected void quitte(Player p) {
        Combattant c = parEntite.remove(p.getUniqueId());
        if (c != null) {
            tous.remove(c);
            for (Combattant o : tous) {
                if (o.visee == c) {
                    o.visee = null;
                }
            }
        }
    }

    @Override
    public String etat() {
        if (phase == Phase.ATTENTE) {
            return joueurs.size() + " inscrit(s), départ bientôt";
        }
        return "Bleu " + score[0] + " — " + score[1] + " Or · " + Txt.chrono(Math.max(0, Math.min(reste, duree)));
    }
}
