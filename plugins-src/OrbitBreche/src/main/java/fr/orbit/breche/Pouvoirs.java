package fr.orbit.breche;

import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Les pouvoirs des agents : projectiles (lancés en cloche ou tendus, avec rebond), zones au sol (feu, puits,
 * lenteur), fumées opaques, tourelle, fil de givre, mines, mur de cristal, révélations et aveuglements.
 * Le même code sert aux joueurs (regard) et aux bots (direction choisie par l'IA).
 */
final class Pouvoirs {

    private final Partie partie;
    private final OrbitBreche pl;
    private final World w;

    // ------------------------------------------------------------------ objets en jeu

    private final class Proj {
        Combattant src;
        Location pos;
        Vector vel;
        double grav;
        ItemDisplay vue;
        Particle trace;
        int age, vie = 200, meche = -1;
        boolean rebond, pose, frappeEnnemi;
        Consumer<Location> impact;
    }

    final class Zone {
        Combattant src;
        double x, z, r;
        int debut, fin;
        Consumer<Zone> chaque;
        Runnable apres;
    }

    final class Fumee {
        Combattant src;
        Location centre;
        double r;
        int fin;
        final List<TextDisplay> vues = new ArrayList<>();
    }

    final class Tourelle {
        Combattant src;
        Location pos;
        double hp = 120;
        ItemDisplay vue;
        Interaction boite;
        int prochain;
    }

    private final class Fil {
        Combattant src;
        double ax, az, bx, bz, y;
        int arme;
    }

    private final class Mine {
        Combattant src;
        Location pos;
        int arme;
        ItemDisplay vue;
    }

    private final class Mur {
        final List<int[]> cases = new ArrayList<>();
        int fin;
    }

    private record Differe(int quand, Runnable action) {
    }

    private final List<Proj> projs = new ArrayList<>();
    private final List<Zone> zones = new ArrayList<>();
    final List<Fumee> fumees = new ArrayList<>();
    final List<Tourelle> tourelles = new ArrayList<>();
    private final List<Fil> fils = new ArrayList<>();
    private final List<Mine> mines = new ArrayList<>();
    private final List<Mur> murs = new ArrayList<>();
    private final List<Differe> differes = new ArrayList<>();

    Pouvoirs(Partie partie) {
        this.partie = partie;
        this.pl = partie.pl;
        this.w = partie.w;
    }

    private int t() {
        return partie.t;
    }

    // ------------------------------------------------------------------ lancer

    /** Pouvoir lancé par un joueur depuis la barre (slot 0 à 3 = C, Q, E, X). Renvoie vrai s'il est parti. */
    boolean lancerJoueur(Combattant c, int slot) {
        Player p = c.p;
        Agent.Pouvoir pv = c.agent.pouvoirs[slot];
        if (!partie.tir.peutAgir(c)) {
            p.playSound(p.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 0.6f, 1.6f);
            Txt.bar(p, partie.phase == Partie.Phase.ACHAT ? "<#9AA4B2>Les pouvoirs s'utilisent une fois la manche lancée." : "<#9AA4B2>Impossible maintenant.");
            return false;
        }
        if (pv.ultime() ? c.ult < pv.max() : c.charges[slot] <= 0) {
            p.playSound(p.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 0.6f, 1.6f);
            Txt.bar(p, pv.ultime() ? "<#9AA4B2>Ultime : " + c.ult + "/" + pv.max() + " points" : "<#9AA4B2>Plus de charge : " + pv.nom()
                    + (pv.prix() > 0 ? " s'achète à la boutique." : "."));
            return false;
        }
        return lancer(c, slot, p.getEyeLocation(), p.getEyeLocation().getDirection());
    }

    /** Lance le pouvoir (joueur ou bot) depuis {@code oeil} dans la direction {@code dir} ; consomme la charge s'il part. */
    boolean lancer(Combattant c, int slot, Location oeil, Vector dir) {
        Agent.Pouvoir pv = c.agent.pouvoirs[slot];
        if (!partie.tir.peutAgir(c) || (pv.ultime() ? c.ult < pv.max() : c.charges[slot] <= 0)) {
            return false;
        }
        boolean ok = switch (pv.id()) {
            case "brasier" -> brasier(c, oeil, dir);
            case "eclat" -> eclat(c, oeil, dir);
            case "envol" -> envol(c, dir);
            case "supernova" -> supernova(c, oeil, dir);
            case "puits" -> puits(c, oeil, dir);
            case "pas" -> pas(c, oeil, dir);
            case "nebuleuse" -> nebuleuse(c, oeil, dir, 45);
            case "eclipse" -> eclipse(c);
            case "fil" -> fil(c, dir);
            case "mine" -> mine(c, oeil, dir);
            case "tourelle" -> tourelle(c, dir);
            case "glaciation" -> glaciation(c);
            case "onde" -> onde(c, oeil, dir);
            case "commotion" -> commotion(c, oeil, dir);
            case "sonar" -> sonar(c, oeil, dir);
            case "traque" -> traque(c);
            case "cristal" -> cristal(c, oeil, dir);
            case "lenteur" -> lenteur(c, oeil, dir);
            case "soin" -> soin(c, oeil, dir);
            case "renaissance" -> renaissance(c, oeil, dir);
            default -> false;
        };
        if (!ok) {
            return false;
        }
        if (pv.ultime()) {
            c.ult = 0;
            partie.annoncer(c.nomCouleur() + " <#5A6270>déclenche</#5A6270> <" + c.agent.couleur + "><b>" + pv.nom().toUpperCase() + "</b>");
        } else {
            c.charges[slot]--;
        }
        if (c.p != null) {
            partie.hotbar(c);
        }
        return true;
    }

    // ------------------------------------------------------------------ outils

    private ItemDisplay objet(Location at, Material m, float taille) {
        return w.spawn(at, ItemDisplay.class, e -> {
            pl.mark(e);
            e.setItemStack(new ItemStack(m));
            e.setBrightness(new Display.Brightness(15, 15));
            e.setTeleportDuration(1);
            e.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(taille, taille, taille), new AxisAngle4f()));
        });
    }

    private Proj projectile(Combattant c, Location oeil, Vector dir, double vitesse, double grav, Material m, Particle trace, boolean rebond,
                            Consumer<Location> impact) {
        Proj p = new Proj();
        p.src = c;
        p.pos = oeil.clone().add(dir.clone().multiply(0.5));
        p.vel = dir.clone().normalize().multiply(vitesse);
        if (grav > 0) {
            p.vel.setY(p.vel.getY() + 0.12);
        }
        p.grav = grav;
        p.trace = trace;
        p.rebond = rebond;
        p.impact = impact;
        p.vue = objet(p.pos, m, 0.45f);
        projs.add(p);
        w.playSound(oeil, Sound.ENTITY_SNOWBALL_THROW, 0.8f, 0.7f);
        return p;
    }

    private void tickProjs() {
        Iterator<Proj> it = projs.iterator();
        List<Runnable> impacts = new ArrayList<>();
        while (it.hasNext()) {
            Proj p = it.next();
            p.age++;
            if (p.pose) {
                if (--p.meche <= 0) {
                    it.remove();
                    finir(p, impacts);
                }
                continue;
            }
            boolean fini = p.age >= p.vie;
            Vector v = p.vel.clone();
            double len = v.length();
            if (!fini && len > 1e-4) {
                RayTraceResult hit = w.rayTraceBlocks(p.pos, v.clone().normalize(), len, FluidCollisionMode.NEVER, true);
                if (hit != null) {
                    Vector n = hit.getHitBlockFace() == null ? new Vector(0, 1, 0) : hit.getHitBlockFace().getDirection();
                    p.pos = hit.getHitPosition().toLocation(w).add(n.clone().multiply(0.08));
                    if (n.getY() > 0.5 || !p.rebond) {
                        if (p.meche > 0 && n.getY() > 0.5) {
                            p.pose = true;
                        } else {
                            fini = true;
                        }
                    } else {
                        // rebond sur un mur : composante normale inversée et amortie
                        double dot = p.vel.dot(n);
                        p.vel.subtract(n.clone().multiply(2 * dot)).multiply(0.45);
                    }
                } else {
                    p.pos.add(v);
                }
                p.vel.setY(p.vel.getY() - p.grav);
                p.vel.multiply(0.99);
                if (p.frappeEnnemi) {
                    for (Combattant o : partie.tous) {
                        if (o.vivant && o.equipe != p.src.equipe && o.oeil(w).add(0, -0.6, 0).distanceSquared(p.pos) < 1.2) {
                            fini = true;
                            break;
                        }
                    }
                }
                if (p.pos.getY() < Plan.SOL - 3) {
                    fini = true;
                }
            }
            if (p.vue != null && p.vue.isValid()) {
                p.vue.teleport(p.pos);
            }
            if (p.trace != null) {
                w.spawnParticle(p.trace, p.pos, 2, 0.04, 0.04, 0.04, 0.005);
            }
            if (fini) {
                it.remove();
                finir(p, impacts);
            }
        }
        for (Runnable r : impacts) {
            r.run();
        }
    }

    private void finir(Proj p, List<Runnable> impacts) {
        if (p.vue != null) {
            p.vue.remove();
        }
        Location at = p.pos.clone();
        impacts.add(() -> p.impact.accept(at));
    }

    private Zone zone(Combattant c, Location at, double r, int duree, Consumer<Zone> chaque, Runnable apres) {
        Zone z = new Zone();
        z.src = c;
        z.x = at.getX();
        z.z = at.getZ();
        z.r = r;
        z.debut = t();
        z.fin = t() + duree;
        z.chaque = chaque;
        z.apres = apres;
        zones.add(z);
        return z;
    }

    private void plus_tard(int ticks, Runnable r) {
        differes.add(new Differe(t() + ticks, r));
    }

    List<Combattant> ennemis(Combattant c) {
        List<Combattant> l = new ArrayList<>();
        for (Combattant o : partie.tous) {
            if (o.vivant && o.equipe != c.equipe) {
                l.add(o);
            }
        }
        return l;
    }

    boolean vue(Location a, Location b) {
        Vector d = b.toVector().subtract(a.toVector());
        double len = d.length();
        if (len < 0.1) {
            return true;
        }
        return w.rayTraceBlocks(a, d.multiply(1 / len), len, FluidCollisionMode.NEVER, true) == null;
    }

    /** Une fumée coupe-t-elle la ligne entre a et b ? (pour les bots et la tourelle) */
    boolean fumeeEntre(Location a, Location b) {
        for (Fumee f : fumees) {
            if (segmentSphere(a.toVector(), b.toVector(), f.centre.toVector(), f.r * 0.92)) {
                return true;
            }
        }
        return false;
    }

    private static boolean segmentSphere(Vector a, Vector b, Vector c, double r) {
        Vector ab = b.clone().subtract(a);
        double l2 = ab.lengthSquared();
        double tt = l2 < 1e-6 ? 0 : Math.max(0, Math.min(1, c.clone().subtract(a).dot(ab) / l2));
        Vector p = a.clone().add(ab.multiply(tt));
        return p.distanceSquared(c) <= r * r;
    }

    /** Point visé au sol, à {@code max} blocs au plus. */
    private Location pointVise(Location oeil, Vector dir, double max) {
        RayTraceResult hit = w.rayTraceBlocks(oeil, dir, max, FluidCollisionMode.NEVER, true);
        Location p = hit == null ? oeil.clone().add(dir.clone().multiply(max)) : hit.getHitPosition().toLocation(w).subtract(dir.clone().multiply(0.6));
        p.setY(Plan.Y);
        return p;
    }

    // ------------------------------------------------------------------ effets

    void aveugler(Combattant o, int ticks) {
        o.aveugle = Math.max(o.aveugle, t() + ticks);
        if (o.p != null) {
            o.p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, ticks + 10, 0, false, false, false));
            o.p.playSound(o.p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 1f, 1.8f);
            Txt.title(o.p, "", "<#FFFFFF>✸ aveuglé", ticks * 50);
        }
        o.cible = null;
    }

    void ralentir(Combattant o, int ticks, int niveau) {
        o.lent = Math.max(o.lent, t() + ticks);
        if (o.p != null) {
            o.p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, niveau, false, false, true));
        }
    }

    void reveler(Combattant o, int ticks) {
        o.revele = Math.max(o.revele, t() + ticks);
        if (o.p != null) {
            o.p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, ticks, 0, false, false, true));
        } else if (o.m != null) {
            o.m.setGlowing(true);
        }
    }

    void geler(Combattant o, int ticks) {
        o.gel = Math.max(o.gel, t() + ticks);
        o.chemin.clear();
        if (o.p != null) {
            o.p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 6, false, false, true));
            Etat.base(o.p, org.bukkit.attribute.Attribute.JUMP_STRENGTH, 0);
            Txt.title(o.p, "<#62CFF4><b>GELÉ</b>", "", ticks * 50);
        }
        o.cible = null;
    }

    void commotionner(Combattant o, int ticks) {
        o.commotion = Math.max(o.commotion, t() + ticks);
        if (o.p != null) {
            o.p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, ticks + 40, 0, false, false, false));
            o.p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 0, false, false, true));
        }
    }

    void myope(Combattant o, int ticks) {
        o.myope = Math.max(o.myope, t() + ticks);
        if (o.p != null) {
            o.p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, ticks, 0, false, false, false));
            o.p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, ticks, 0, false, false, false));
        }
        o.cible = null;
    }

    /** Explosion : dégâts dégressifs du centre au bord, murs protègent. */
    void explosion(Combattant src, Location at, double r, double max, double min, String nom) {
        w.spawnParticle(Particle.EXPLOSION, at, 3, r * 0.3, 0.3, r * 0.3, 0);
        w.spawnParticle(Particle.FLAME, at, 40, r * 0.4, 0.4, r * 0.4, 0.06);
        w.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.6f, 1.1f);
        for (Combattant o : ennemis(src)) {
            Location corps = o.oeil(w).add(0, -0.7, 0);
            double d = corps.distance(at);
            if (d <= r && vue(at.clone().add(0, 0.3, 0), corps)) {
                partie.blesser(src, o, max - (max - min) * (d / r), nom, false);
            }
        }
    }

    /** Éclair aveuglant en {@code at} : aveugle les ennemis qui le voient (plus fort s'ils le regardent). */
    private void flash(Combattant src, Location at, int duree) {
        w.spawnParticle(Particle.END_ROD, at, 60, 0.3, 0.3, 0.3, 0.25);
        w.spawnParticle(Particle.FIREWORK, at, 30, 0.2, 0.2, 0.2, 0.15);
        w.playSound(at, Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 1.5f, 1.6f);
        for (Combattant o : ennemis(src)) {
            Location oeil = o.oeil(w);
            double d = oeil.distance(at);
            if (d > 32 || !vue(at, oeil)) {
                continue;
            }
            Vector vers = at.toVector().subtract(oeil.toVector()).normalize();
            double face = vers.dot(o.regard(w));
            int ticks = face > 0.35 ? duree : face > -0.2 ? duree / 2 : 0;
            if (d < 4) {
                ticks = Math.max(ticks, duree / 2);
            }
            if (ticks > 0) {
                aveugler(o, ticks);
            }
        }
    }

    // ------------------------------------------------------------------ SOLÈNE

    private boolean brasier(Combattant c, Location oeil, Vector dir) {
        projectile(c, oeil, dir, 0.9, 0.045, Material.FIRE_CHARGE, Particle.FLAME, true, at -> {
            w.playSound(at, Sound.ITEM_FIRECHARGE_USE, 1.2f, 0.8f);
            zone(c, at, 3.2, 100, z -> {
                for (int i = 0; i < 6; i++) {
                    double a = Math.random() * Math.PI * 2, rr = Math.sqrt(Math.random()) * z.r;
                    w.spawnParticle(i % 3 == 0 ? Particle.SMALL_FLAME : Particle.FLAME, z.x + Math.cos(a) * rr, Plan.Y + 0.15, z.z + Math.sin(a) * rr, 1, 0, 0.05, 0, 0.01);
                }
                if ((t() - z.debut) % 5 == 0) {
                    for (Combattant o : ennemis(c)) {
                        if (o.dist2(z.x, z.z) <= z.r * z.r) {
                            partie.blesser(c, o, 12, "Brasier", false);
                        }
                    }
                }
                if ((t() - z.debut) % 20 == 0) {
                    w.playSound(new Location(w, z.x, Plan.Y, z.z), Sound.BLOCK_FIRE_AMBIENT, 1f, 1f);
                }
            }, null);
        });
        return true;
    }

    private boolean eclat(Combattant c, Location oeil, Vector dir) {
        Proj p = projectile(c, oeil, dir, 0.85, 0, Material.GLOWSTONE_DUST, Particle.END_ROD, false, at -> flash(c, at, 45));
        p.vie = 14;
        return true;
    }

    private boolean envol(Combattant c, Vector dir) {
        Vector h = dir.clone().setY(0);
        if (h.lengthSquared() < 1e-4) {
            return false;
        }
        h.normalize();
        Location l = c.pieds(w);
        if (c.p != null) {
            c.p.setVelocity(h.clone().multiply(1.65).setY(0.32));
        } else {
            double x = c.x, z = c.z;
            for (int i = 0; i < 12; i++) {
                double nx = x + h.getX() * 0.5, nz = z + h.getZ() * 0.5;
                int col = partie.plan.col(nx), rang = partie.plan.rang(nz);
                if (!partie.plan.libre(col, rang, partie.carte.bloque)) {
                    break;
                }
                x = nx;
                z = nz;
            }
            c.x = x;
            c.z = z;
            c.chemin.clear();
        }
        w.spawnParticle(Particle.CLOUD, l.clone().add(0, 0.3, 0), 12, 0.3, 0.1, 0.3, 0.05);
        w.spawnParticle(Particle.FLAME, l.clone().add(0, 0.5, 0), 10, 0.3, 0.3, 0.3, 0.02);
        w.playSound(l, Sound.ENTITY_BREEZE_JUMP, 1f, 1.2f);
        return true;
    }

    private boolean supernova(Combattant c, Location oeil, Vector dir) {
        Proj p = projectile(c, oeil, dir, 1.3, 0, Material.MAGMA_CREAM, Particle.FLAME, false, at -> explosion(c, at, 5.5, 160, 50, "Supernova"));
        p.frappeEnnemi = true;
        p.vue.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.9f, 0.9f, 0.9f), new AxisAngle4f()));
        w.playSound(oeil, Sound.ENTITY_BLAZE_SHOOT, 1.4f, 0.6f);
        return true;
    }

    // ------------------------------------------------------------------ ORYX

    private boolean puits(Combattant c, Location oeil, Vector dir) {
        projectile(c, oeil, dir, 0.85, 0.045, Material.ENDER_EYE, Particle.REVERSE_PORTAL, true, at -> {
            w.playSound(at, Sound.BLOCK_PORTAL_TRIGGER, 0.6f, 1.8f);
            zone(c, at, 5, 50, z -> {
                double ang = (t() - z.debut) * 0.5;
                for (int i = 0; i < 4; i++) {
                    double a = ang + i * Math.PI / 2, rr = z.r * (1 - ((t() - z.debut) % 10) / 10.0);
                    w.spawnParticle(Particle.PORTAL, z.x + Math.cos(a) * rr, Plan.Y + 0.6, z.z + Math.sin(a) * rr, 2, 0, 0, 0, 0);
                }
                for (Combattant o : ennemis(c)) {
                    double d2 = o.dist2(z.x, z.z);
                    if (d2 > z.r * z.r || d2 < 0.25) {
                        continue;
                    }
                    double d = Math.sqrt(d2), dx = (z.x - o.px()) / d, dz = (z.z - o.pz()) / d;
                    if (o.p != null) {
                        Vector v = o.p.getVelocity();
                        o.p.setVelocity(new Vector(v.getX() * 0.6 + dx * 0.14, v.getY(), v.getZ() * 0.6 + dz * 0.14));
                    } else if (partie.plan.libre(partie.plan.col(o.x + dx * 0.07), partie.plan.rang(o.z + dz * 0.07), partie.carte.bloque)) {
                        o.x += dx * 0.07;
                        o.z += dz * 0.07;
                    }
                    if ((t() - z.debut) % 10 == 0) {
                        ralentir(o, 14, 1);
                    }
                }
            }, () -> {
                Location l = new Location(w, at.getX(), Plan.Y + 0.6, at.getZ());
                w.spawnParticle(Particle.REVERSE_PORTAL, l, 60, 1.5, 0.4, 1.5, 0.1);
                w.playSound(l, Sound.ENTITY_ENDERMAN_TELEPORT, 1.2f, 0.6f);
                for (Combattant o : ennemis(c)) {
                    if (o.dist2(at.getX(), at.getZ()) <= 3.5 * 3.5) {
                        partie.blesser(c, o, 35, "Puits d'ombre", false);
                    }
                }
            });
        });
        return true;
    }

    private boolean pas(Combattant c, Location oeil, Vector dir) {
        Location cible = pointVise(oeil, dir, 14);
        int[] cell = partie.plan.proche(partie.plan.col(cible.getX()), partie.plan.rang(cible.getZ()), partie.carte.bloque);
        if (cell == null) {
            return false;
        }
        Location dest = new Location(w, partie.plan.wx(cell[0]), Plan.Y, partie.plan.wz(cell[1]));
        Location dep = c.pieds(w);
        w.spawnParticle(Particle.PORTAL, dep.clone().add(0, 1, 0), 40, 0.3, 0.8, 0.3, 0.5);
        w.spawnParticle(Particle.REVERSE_PORTAL, dest.clone().add(0, 1, 0), 40, 0.3, 0.8, 0.3, 0.05);
        w.playSound(dep, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.8f, 1.6f);
        plus_tard(12, () -> {
            if (!c.vivant || c.gel > t()) {
                return;
            }
            if (c.p != null) {
                Location l = c.p.getLocation();
                dest.setYaw(l.getYaw());
                dest.setPitch(l.getPitch());
                pl.teleporter(c.p, dest);
            } else {
                c.x = dest.getX();
                c.z = dest.getZ();
                c.chemin.clear();
            }
            w.playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.2f);
        });
        return true;
    }

    /** Fumée opaque centrée au sol au point visé (bots : point choisi par l'IA). */
    boolean nebuleuse(Combattant c, Location oeil, Vector dir, double max) {
        Location p = pointVise(oeil, dir, max);
        p.setY(Plan.Y + 1.7);
        Fumee f = new Fumee();
        f.src = c;
        f.centre = p;
        f.r = 3.3;
        f.fin = t() + 300;
        float s = (float) (f.r * 2 / 0.5);
        for (int i = 0; i < 2; i++) {
            float ang = i == 0 ? 0f : (float) Math.toRadians(45);
            Quaternionf rot = new Quaternionf().rotateZ(ang);
            Vector3f haut = rot.transform(new Vector3f(0, (float) f.r, 0));
            Vector3f tr = new Vector3f(-haut.x, -haut.y, i * 0.03f);
            int argb = i == 0 ? 0xF0_3A2E55 : 0xF0_4A3C68;
            f.vues.add(w.spawn(p, TextDisplay.class, e -> {
                pl.mark(e);
                e.text(Txt.mm("     \n     "));
                e.setBillboard(Display.Billboard.CENTER);
                e.setDefaultBackground(false);
                e.setBackgroundColor(Color.fromARGB(argb));
                e.setShadowed(false);
                e.setSeeThrough(false);
                e.setBrightness(new Display.Brightness(12, 12));
                e.setTransformation(new Transformation(new Vector3f(), rot, new Vector3f(0.05f, 0.05f, 0.05f), new Quaternionf()));
            }));
            TextDisplay td = f.vues.get(i);
            plus_tard(1, () -> {
                if (td.isValid()) {
                    td.setInterpolationDelay(0);
                    td.setInterpolationDuration(8);
                    td.setTransformation(new Transformation(tr, rot, new Vector3f(s, s, s), new Quaternionf()));
                }
            });
        }
        fumees.add(f);
        w.playSound(p, Sound.BLOCK_FIRE_EXTINGUISH, 1.2f, 0.6f);
        w.spawnParticle(Particle.LARGE_SMOKE, p, 30, 1.5, 1, 1.5, 0.02);
        return true;
    }

    private boolean eclipse(Combattant c) {
        for (Combattant o : ennemis(c)) {
            myope(o, 100);
            if (o.p != null) {
                Txt.title(o.p, "<#C79BFF><b>ÉCLIPSE</b>", "<#9AA4B2>tu n'y vois presque plus", 2000);
                o.p.playSound(o.p.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 0.6f, 0.5f);
            }
        }
        if (c.p != null) {
            c.p.playSound(c.p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1f, 0.5f);
        }
        return true;
    }

    // ------------------------------------------------------------------ KAEL

    private boolean fil(Combattant c, Vector dir) {
        Vector h = dir.clone().setY(0);
        if (h.lengthSquared() < 1e-4) {
            return false;
        }
        h.normalize();
        Location a = c.pieds(w).add(0, 0.35, 0);
        RayTraceResult hit = w.rayTraceBlocks(a, h, 10, FluidCollisionMode.NEVER, true);
        Location b = hit == null ? a.clone().add(h.clone().multiply(10)) : hit.getHitPosition().toLocation(w);
        if (a.distance(b) < 1.5) {
            return false;
        }
        Fil f = new Fil();
        f.src = c;
        f.ax = a.getX();
        f.az = a.getZ();
        f.bx = b.getX();
        f.bz = b.getZ();
        f.y = a.getY();
        f.arme = t() + 20;
        fils.add(f);
        w.playSound(a, Sound.BLOCK_TRIPWIRE_ATTACH, 1f, 1.4f);
        return true;
    }

    private boolean mine(Combattant c, Location oeil, Vector dir) {
        projectile(c, oeil, dir, 0.7, 0.05, Material.PRISMARINE_CRYSTALS, Particle.SNOWFLAKE, true, at -> {
            Mine m = new Mine();
            m.src = c;
            m.pos = at;
            m.arme = t() + 15;
            m.vue = objet(at.clone().add(0, 0.15, 0), Material.PRISMARINE_CRYSTALS, 0.4f);
            mines.add(m);
            w.playSound(at, Sound.BLOCK_GLASS_PLACE, 0.8f, 1.6f);
        });
        return true;
    }

    private boolean tourelle(Combattant c, Vector dir) {
        Vector h = dir.clone().setY(0);
        if (h.lengthSquared() < 1e-4) {
            h = new Vector(0, 0, 1);
        }
        h.normalize();
        Location base = c.pieds(w);
        double x = base.getX() + h.getX() * 1.6, z = base.getZ() + h.getZ() * 1.6;
        if (!partie.plan.libre(partie.plan.col(x), partie.plan.rang(z), partie.carte.bloque)) {
            x = base.getX();
            z = base.getZ();
        }
        for (Tourelle old : new ArrayList<>(tourelles)) {
            if (old.src == c) {
                detruire(old, false);
            }
        }
        Tourelle tr = new Tourelle();
        tr.src = c;
        tr.pos = new Location(w, x, Plan.Y, z, (float) Math.toDegrees(Math.atan2(-h.getX(), h.getZ())), 0);
        tr.prochain = t() + 20;
        tr.vue = w.spawn(tr.pos.clone().add(0, 0.5, 0), ItemDisplay.class, e -> {
            pl.mark(e);
            e.setItemStack(pl.objets().modele(Material.PAPER, "tourelle"));
            e.setBrightness(new Display.Brightness(15, 15));
            e.setTeleportDuration(2);
            e.setRotation(tr.pos.getYaw(), 0);
            e.setGlowing(true);
            e.setGlowColorOverride(Color.fromRGB(c.equipe == 0 ? 0x3A7FFF : 0xE0B864));
        });
        tr.boite = w.spawn(tr.pos, Interaction.class, e -> {
            pl.mark(e);
            e.setInteractionWidth(0.8f);
            e.setInteractionHeight(1.0f);
            e.setResponsive(false);
        });
        tourelles.add(tr);
        w.playSound(tr.pos, Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 1f, 0.7f);
        return true;
    }

    private boolean glaciation(Combattant c) {
        Location centre = c.pieds(w);
        double r = 10;
        for (int k = 0; k < 6; k++) {
            int kk = k;
            plus_tard(k * 5, () -> {
                double rr = r * (kk + 1) / 6.0;
                for (int i = 0; i < 48; i++) {
                    double a = i * Math.PI * 2 / 48;
                    w.spawnParticle(Particle.SNOWFLAKE, centre.getX() + Math.cos(a) * rr, Plan.Y + 0.3, centre.getZ() + Math.sin(a) * rr, 1, 0, 0.1, 0, 0);
                }
                w.playSound(centre, Sound.BLOCK_POWDER_SNOW_STEP, 1.5f, 0.6f + kk * 0.1f);
            });
        }
        plus_tard(30, () -> {
            w.playSound(centre, Sound.BLOCK_GLASS_BREAK, 2f, 0.6f);
            w.spawnParticle(Particle.SNOWFLAKE, centre.clone().add(0, 1, 0), 300, r * 0.5, 1, r * 0.5, 0.05);
            for (Combattant o : ennemis(c)) {
                if (o.dist2(centre.getX(), centre.getZ()) <= r * r) {
                    geler(o, 80);
                    reveler(o, 80);
                    partie.blesser(c, o, 20, "Glaciation", false);
                }
            }
        });
        return true;
    }

    // ------------------------------------------------------------------ ÉCHO

    private boolean onde(Combattant c, Location oeil, Vector dir) {
        Proj p = projectile(c, oeil, dir, 0.85, 0.045, Material.FIREWORK_STAR, Particle.SMOKE, true,
                at -> explosion(c, at.clone().add(0, 0.3, 0), 3.8, 75, 15, "Onde de choc"));
        p.meche = 16;
        return true;
    }

    private boolean commotion(Combattant c, Location oeil, Vector dir) {
        projectile(c, oeil, dir, 0.85, 0.045, Material.ECHO_SHARD, Particle.SCULK_CHARGE_POP, true, at -> {
            Location l = at.clone().add(0, 0.6, 0);
            w.spawnParticle(Particle.SONIC_BOOM, l, 1);
            w.playSound(l, Sound.ENTITY_WARDEN_ATTACK_IMPACT, 1.4f, 0.8f);
            for (Combattant o : ennemis(c)) {
                Location oo = o.oeil(w);
                if (oo.distance(l) <= 4.5 && vue(l, oo)) {
                    commotionner(o, 70);
                }
            }
        });
        return true;
    }

    private boolean sonar(Combattant c, Location oeil, Vector dir) {
        projectile(c, oeil, dir, 1.6, 0.02, Material.SPECTRAL_ARROW, Particle.END_ROD, false, at -> {
            ItemDisplay fleche = objet(at, Material.SPECTRAL_ARROW, 0.6f);
            w.playSound(at, Sound.ENTITY_ARROW_HIT, 1f, 0.6f);
            for (int k : new int[]{5, 35}) {
                plus_tard(k, () -> {
                    w.playSound(at, Sound.BLOCK_BEACON_AMBIENT, 1.6f, 1.8f);
                    for (int s = 0; s < 4; s++) {
                        int ss = s;
                        plus_tard(s * 2, () -> {
                            double rr = 4 * (ss + 1);
                            for (int i = 0; i < 40; i++) {
                                double a = i * Math.PI * 2 / 40;
                                w.spawnParticle(Particle.END_ROD, at.getX() + Math.cos(a) * rr, at.getY(), at.getZ() + Math.sin(a) * rr, 1, 0, 0, 0, 0);
                            }
                        });
                    }
                    for (Combattant o : ennemis(c)) {
                        Location oo = o.oeil(w);
                        if (oo.distance(at) <= 16 && vue(at, oo)) {
                            reveler(o, 50);
                        }
                    }
                });
            }
            plus_tard(45, fleche::remove);
        });
        return true;
    }

    private boolean traque(Combattant c) {
        for (int k = 0; k < 3; k++) {
            plus_tard(k * 40, () -> {
                for (Combattant o : ennemis(c)) {
                    reveler(o, 35);
                    w.spawnParticle(Particle.END_ROD, o.pieds(w).add(0, 1, 0), 20, 0.2, 1.2, 0.2, 0.01);
                }
                for (Combattant a : partie.tous) {
                    if (a.p != null && a.equipe == c.equipe) {
                        a.p.playSound(a.p.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1f, 1.4f);
                    }
                }
            });
        }
        return true;
    }

    // ------------------------------------------------------------------ NAÏA

    private boolean cristal(Combattant c, Location oeil, Vector dir) {
        Vector h = dir.clone().setY(0);
        if (h.lengthSquared() < 1e-4) {
            return false;
        }
        h.normalize();
        Location p = pointVise(oeil, dir, 10);
        Plan plan = partie.plan;
        int col = plan.col(p.getX()), rang = plan.rang(p.getZ());
        boolean leLongDeZ = Math.abs(h.getX()) > Math.abs(h.getZ());
        Mur m = new Mur();
        m.fin = t() + 500;
        for (int k = -3; k <= 3; k++) {
            int cx = leLongDeZ ? col : col + k, cz = leLongDeZ ? rang + k : rang;
            if (!plan.libre(cx, cz, partie.carte.bloque)) {
                continue;
            }
            double wx = plan.wx(cx), wz = plan.wz(cz);
            boolean occupe = false;
            for (Combattant o : partie.tous) {
                if (o.vivant && o.dist2(wx, wz) < 0.8 * 0.8) {
                    occupe = true;
                    break;
                }
            }
            if (occupe) {
                continue;
            }
            for (int y = Plan.Y; y < Plan.Y + 3; y++) {
                Block b = w.getBlockAt(plan.x0 + cx, y, plan.z0 + cz);
                if (b.getType().isAir()) {
                    b.setType(Material.LIME_STAINED_GLASS, false);
                }
            }
            partie.carte.bloque[cz][cx] = true;
            m.cases.add(new int[]{cx, cz});
        }
        if (m.cases.isEmpty()) {
            return false;
        }
        murs.add(m);
        w.playSound(p, Sound.BLOCK_AMETHYST_BLOCK_PLACE, 1.4f, 0.8f);
        w.spawnParticle(Particle.HAPPY_VILLAGER, p.clone().add(0, 1.5, 0), 30, 2, 1, 2, 0);
        return true;
    }

    private void retirerMur(Mur m) {
        Plan plan = partie.plan;
        for (int[] cell : m.cases) {
            for (int y = Plan.Y; y < Plan.Y + 3; y++) {
                Block b = w.getBlockAt(plan.x0 + cell[0], y, plan.z0 + cell[1]);
                if (b.getType() == Material.LIME_STAINED_GLASS) {
                    b.setType(Material.AIR, false);
                }
            }
            partie.carte.bloque[cell[1]][cell[0]] = false;
        }
    }

    private boolean lenteur(Combattant c, Location oeil, Vector dir) {
        projectile(c, oeil, dir, 0.8, 0.05, Material.SLIME_BALL, Particle.ITEM_SLIME, true, at -> {
            w.playSound(at, Sound.BLOCK_SLIME_BLOCK_PLACE, 1.2f, 0.7f);
            Particle.DustOptions vert = new Particle.DustOptions(Color.fromRGB(0x7CFF8A), 1.2f);
            zone(c, at, 4.5, 120, z -> {
                for (int i = 0; i < 8; i++) {
                    double a = Math.random() * Math.PI * 2, rr = Math.sqrt(Math.random()) * z.r;
                    w.spawnParticle(Particle.DUST, z.x + Math.cos(a) * rr, Plan.Y + 0.1 + Math.random() * 0.4, z.z + Math.sin(a) * rr, 1, 0, 0, 0, 0, vert);
                }
                if ((t() - z.debut) % 5 == 0) {
                    for (Combattant o : ennemis(c)) {
                        if (o.dist2(z.x, z.z) <= z.r * z.r) {
                            ralentir(o, 12, 2);
                        }
                    }
                }
            }, null);
        });
        return true;
    }

    private boolean soin(Combattant c, Location oeil, Vector dir) {
        Combattant cible = c;
        RayTraceResult res = w.rayTraceEntities(oeil, dir, 12, 0.3, e -> {
            Combattant o = partie.parEntite.get(e.getUniqueId());
            return o != null && o != c && o.vivant && o.equipe == c.equipe;
        });
        if (res != null && res.getHitEntity() != null) {
            Combattant o = partie.parEntite.get(res.getHitEntity().getUniqueId());
            if (o != null && partie.pouvoirs.vue(oeil, o.oeil(w))) {
                cible = o;
            }
        }
        if (cible.hp >= 100) {
            if (c.p != null) {
                Txt.bar(c.p, "<#9AA4B2>" + (cible == c ? "Tu as" : cible.nom + " a") + " déjà toute sa vie.");
            }
            return false;
        }
        cible.soinFin = t() + 80;
        cible.soinParTick = 60.0 / 80;
        Location l = cible.pieds(w);
        w.playSound(l, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.2f, 1.4f);
        w.spawnParticle(Particle.HEART, l.clone().add(0, 2, 0), 4, 0.3, 0.2, 0.3, 0);
        if (cible != c && cible.p != null) {
            Txt.bar(cible.p, "<#7CFF8A>" + c.nom + " te soigne.");
        }
        return true;
    }

    private boolean renaissance(Combattant c, Location oeil, Vector dir) {
        Location vise = pointVise(oeil, dir, 30);
        Combattant mieux = null;
        double best = Double.MAX_VALUE;
        for (Combattant o : partie.tous) {
            if (o.vivant || o.equipe != c.equipe || o == c) {
                continue;
            }
            double dc = c.dist2(o.mortX, o.mortZ);
            if (dc > 30 * 30) {
                continue;
            }
            double dx = o.mortX - vise.getX(), dz = o.mortZ - vise.getZ();
            double d = dx * dx + dz * dz;
            if (d < best) {
                best = d;
                mieux = o;
            }
        }
        if (mieux == null) {
            if (c.p != null) {
                Txt.bar(c.p, "<#9AA4B2>Aucun allié tombé à moins de 30 blocs.");
            }
            return false;
        }
        partie.ranimer(mieux);
        return true;
    }

    // ------------------------------------------------------------------ tourelles et objets touchables

    boolean objetEnnemi(Combattant c, Entity e) {
        for (Tourelle tr : tourelles) {
            if (tr.boite != null && tr.boite.getUniqueId().equals(e.getUniqueId())) {
                return tr.src.equipe != c.equipe;
            }
        }
        return false;
    }

    void toucherObjet(Combattant src, Entity e, double dmg) {
        for (Tourelle tr : new ArrayList<>(tourelles)) {
            if (tr.boite != null && tr.boite.getUniqueId().equals(e.getUniqueId()) && tr.src.equipe != src.equipe) {
                tr.hp -= dmg;
                w.spawnParticle(Particle.ELECTRIC_SPARK, tr.pos.clone().add(0, 0.6, 0), 6, 0.2, 0.2, 0.2, 0.05);
                if (tr.hp <= 0) {
                    detruire(tr, true);
                    if (src.p != null) {
                        Txt.bar(src.p, "<#7CFF8A>Tourelle détruite");
                    }
                }
                return;
            }
        }
    }

    private void detruire(Tourelle tr, boolean bruit) {
        tourelles.remove(tr);
        if (tr.vue != null) {
            tr.vue.remove();
        }
        if (tr.boite != null) {
            tr.boite.remove();
        }
        if (bruit) {
            w.spawnParticle(Particle.EXPLOSION, tr.pos.clone().add(0, 0.5, 0), 1);
            w.playSound(tr.pos, Sound.ENTITY_IRON_GOLEM_DEATH, 0.8f, 1.6f);
            if (tr.src.p != null) {
                Txt.bar(tr.src.p, "<#E0484A>Ta tourelle a été détruite");
            }
        }
    }

    private void tickTourelle(Tourelle tr) {
        if (t() < tr.prochain) {
            return;
        }
        tr.prochain = t() + 8;
        Location oeil = tr.pos.clone().add(0, 0.9, 0);
        Combattant mieux = null;
        double best = 28 * 28;
        for (Combattant o : ennemis(tr.src)) {
            Location oo = o.oeil(w).add(0, -0.4, 0);
            double d = oo.distanceSquared(oeil);
            if (d < best && vue(oeil, oo) && !fumeeEntre(oeil, oo)) {
                best = d;
                mieux = o;
            }
        }
        if (mieux == null) {
            return;
        }
        Location cible = mieux.oeil(w).add(0, -0.4, 0);
        Vector d = cible.toVector().subtract(oeil.toVector());
        float yaw = (float) Math.toDegrees(Math.atan2(-d.getX(), d.getZ()));
        tr.vue.setRotation(yaw, 0);
        double chance = mieux.bouge() ? 0.42 : 0.6;
        boolean touche = Math.random() < chance;
        Location fin = cible.clone();
        if (!touche) {
            fin.add((Math.random() - 0.5) * 1.4, (Math.random() - 0.4) * 0.8, (Math.random() - 0.5) * 1.4);
        }
        partie.tir.trace(oeil.clone().add(d.clone().normalize().multiply(0.4)), fin, tr.src.equipe);
        w.playSound(oeil, Sound.BLOCK_NOTE_BLOCK_HAT, 0.9f, 2f);
        if (touche) {
            partie.blesser(tr.src, mieux, 8, "Tourelle", false);
        }
    }

    // ------------------------------------------------------------------ boucle

    void tick() {
        int now = t();
        if (!differes.isEmpty()) {
            List<Differe> dus = new ArrayList<>();
            differes.removeIf(d -> {
                if (d.quand <= now) {
                    dus.add(d);
                    return true;
                }
                return false;
            });
            for (Differe d : dus) {
                d.action.run();
            }
        }
        tickProjs();
        for (Zone z : new ArrayList<>(zones)) {
            if (now >= z.fin) {
                zones.remove(z);
                if (z.apres != null) {
                    z.apres.run();
                }
            } else if (z.chaque != null) {
                z.chaque.accept(z);
            }
        }
        for (Fumee f : new ArrayList<>(fumees)) {
            if (now >= f.fin) {
                fumees.remove(f);
                for (TextDisplay td : f.vues) {
                    td.remove();
                }
                continue;
            }
            if (now % 10 == 0) {
                for (Combattant o : partie.tous) {
                    if (o.p != null && o.vivant && o.oeil(w).distanceSquared(f.centre) < f.r * f.r) {
                        o.p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 25, 0, false, false, false));
                    }
                }
                w.spawnParticle(Particle.LARGE_SMOKE, f.centre, 6, f.r * 0.5, 0.8, f.r * 0.5, 0.005);
            }
        }
        for (Tourelle tr : new ArrayList<>(tourelles)) {
            tickTourelle(tr);
        }
        if (now % 2 == 0) {
            for (Fil f : new ArrayList<>(fils)) {
                if (now % 10 == 0) {
                    Particle.DustOptions glace = new Particle.DustOptions(Color.fromRGB(0x62CFF4), 0.5f);
                    double len = Math.hypot(f.bx - f.ax, f.bz - f.az);
                    int n = (int) Math.max(2, len * 1.5);
                    for (int i = 0; i <= n; i++) {
                        double k = i / (double) n;
                        w.spawnParticle(Particle.DUST, f.ax + (f.bx - f.ax) * k, f.y, f.az + (f.bz - f.az) * k, 1, 0, 0, 0, 0, glace);
                    }
                }
                if (now < f.arme) {
                    continue;
                }
                for (Combattant o : ennemis(f.src)) {
                    if (distSegment(o.px(), o.pz(), f) < 0.55) {
                        fils.remove(f);
                        Location l = o.pieds(w).add(0, 0.5, 0);
                        w.playSound(l, Sound.BLOCK_TRIPWIRE_CLICK_ON, 1.5f, 0.8f);
                        w.spawnParticle(Particle.SNOWFLAKE, l, 40, 0.5, 0.5, 0.5, 0.05);
                        reveler(o, 60);
                        ralentir(o, 60, 2);
                        partie.blesser(f.src, o, 25, "Fil de givre", false);
                        if (f.src.p != null) {
                            Txt.bar(f.src.p, "<#62CFF4>Fil de givre déclenché par " + o.nom);
                        }
                        break;
                    }
                }
            }
            for (Mine m : new ArrayList<>(mines)) {
                if (now < m.arme) {
                    continue;
                }
                for (Combattant o : ennemis(m.src)) {
                    if (o.dist2(m.pos.getX(), m.pos.getZ()) <= 2.5 * 2.5) {
                        mines.remove(m);
                        if (m.vue != null) {
                            m.vue.remove();
                        }
                        w.playSound(m.pos, Sound.BLOCK_GLASS_BREAK, 1.5f, 1.2f);
                        w.spawnParticle(Particle.SNOWFLAKE, m.pos.clone().add(0, 0.5, 0), 80, 1.5, 0.6, 1.5, 0.05);
                        for (Combattant x : ennemis(m.src)) {
                            if (x.dist2(m.pos.getX(), m.pos.getZ()) <= 3.5 * 3.5) {
                                ralentir(x, 60, 2);
                                partie.blesser(m.src, x, 30, "Mine cryo", false);
                            }
                        }
                        break;
                    }
                }
            }
        }
        for (Mur m : new ArrayList<>(murs)) {
            if (now >= m.fin) {
                murs.remove(m);
                retirerMur(m);
            }
        }
    }

    private static double distSegment(double px, double pz, Fil f) {
        double vx = f.bx - f.ax, vz = f.bz - f.az, l2 = vx * vx + vz * vz;
        double k = l2 < 1e-6 ? 0 : Math.max(0, Math.min(1, ((px - f.ax) * vx + (pz - f.az) * vz) / l2));
        double dx = f.ax + vx * k - px, dz = f.az + vz * k - pz;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Un combattant meurt : rien ne disparaît (comme ses fils et sa tourelle, ses pouvoirs restent en place). */
    void mort(Combattant c) {
    }

    /** Un joueur quitte la partie : ses objets disparaissent avec lui. */
    void oublier(Combattant c) {
        for (Tourelle tr : new ArrayList<>(tourelles)) {
            if (tr.src == c) {
                detruire(tr, false);
            }
        }
        fils.removeIf(f -> f.src == c);
        for (Mine m : new ArrayList<>(mines)) {
            if (m.src == c) {
                mines.remove(m);
                if (m.vue != null) {
                    m.vue.remove();
                }
            }
        }
    }

    /** Fin de manche : tout disparaît. */
    void nettoyer() {
        for (Proj p : projs) {
            if (p.vue != null) {
                p.vue.remove();
            }
        }
        projs.clear();
        zones.clear();
        for (Fumee f : fumees) {
            for (TextDisplay td : f.vues) {
                td.remove();
            }
        }
        fumees.clear();
        for (Tourelle tr : new ArrayList<>(tourelles)) {
            detruire(tr, false);
        }
        fils.clear();
        for (Mine m : mines) {
            if (m.vue != null) {
                m.vue.remove();
            }
        }
        mines.clear();
        for (Mur m : murs) {
            retirerMur(m);
        }
        murs.clear();
        differes.clear();
    }
}
