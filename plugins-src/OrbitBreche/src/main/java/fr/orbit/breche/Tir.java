package fr.orbit.breche;

import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Random;

/**
 * Tir : rayon instantané pour les joueurs (tête, corps, jambes selon la hauteur de l'impact), tir « au jet de dé »
 * pour les bots (précision selon le niveau, la distance et le mouvement de la cible). Rechargement par la touche F
 * ou automatiquement quand le chargeur est vide. Lame en clic gauche.
 */
final class Tir {

    private final Partie partie;
    private final World w;
    private final Random rnd = new Random();

    Tir(Partie partie) {
        this.partie = partie;
        this.w = partie.w;
    }

    boolean peutAgir(Combattant c) {
        Partie.Phase ph = partie.phase;
        return c.vivant && c.gel <= partie.t && (ph == Partie.Phase.COMBAT || ph == Partie.Phase.NOYAU);
    }

    /** À chaque tick : rechargement en cours, tir automatique tant que la gâchette est tenue, lunette du Zénith. */
    void tick(Combattant c) {
        int t = partie.t;
        if (c.rechargeFin >= 0) {
            Arme a = c.arme();
            if (c.p != null && (a == null || a.place != c.rechargePlace)) {
                c.rechargeFin = -1;
            } else if (t >= c.rechargeFin) {
                finRecharge(c);
            }
        }
        if (c.p != null) {
            Arme a = c.arme();
            if (a != null && a.auto && t <= c.gachette) {
                essayer(c, a);
            }
            if (a == Arme.ZENITH && c.p.isSneaking()) {
                c.p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 4, 3, false, false, false));
            }
        }
    }

    /** Clic droit d'un joueur, arme en main. */
    void gachette(Combattant c) {
        Arme a = c.arme();
        if (a == null) {
            return;
        }
        if (c.dernierClic == partie.t) {
            return;
        }
        c.dernierClic = partie.t;
        if (a.auto) {
            c.gachette = partie.t + 5;
        }
        essayer(c, a);
    }

    void recharger(Combattant c) {
        Arme a = c.arme();
        if (a == null || c.rechargeFin >= 0) {
            return;
        }
        int k = a.place;
        if (c.chargeur[k] >= a.chargeur || c.reserve[k] <= 0) {
            return;
        }
        c.rechargeFin = partie.t + a.recharge;
        c.rechargePlace = k;
        Location l = c.pieds(w);
        w.playSound(l, Sound.BLOCK_IRON_TRAPDOOR_OPEN, 0.6f, 1.6f);
    }

    private void finRecharge(Combattant c) {
        int k = c.rechargePlace;
        Arme a = k == 0 ? c.primaire : c.secondaire;
        c.rechargeFin = -1;
        if (a == null) {
            return;
        }
        int besoin = Math.min(a.chargeur - c.chargeur[k], c.reserve[k]);
        c.chargeur[k] += besoin;
        c.reserve[k] -= besoin;
        w.playSound(c.pieds(w), Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 0.6f, 1.8f);
    }

    private void essayer(Combattant c, Arme a) {
        int t = partie.t;
        if (!peutAgir(c) || c.rechargeFin >= 0 || t < c.prochainTir) {
            return;
        }
        int k = a.place;
        if (c.chargeur[k] <= 0) {
            c.gachette = -1;
            if (c.reserve[k] > 0) {
                recharger(c);
            } else if (c.p != null) {
                c.p.playSound(c.p.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 0.7f, 1.7f);
            }
            return;
        }
        c.chargeur[k]--;
        c.prochainTir = t + a.cadence;
        double recul = Math.max(0, c.recul - (t - c.dernierTir) * 0.012);
        c.dernierTir = t;
        double disp = dispersion(c, a) + recul;
        c.recul = Math.min(a.reculMax, recul + a.recul);
        tirerJoueur(c, a, disp);
        if (c.chargeur[k] == 0 && c.reserve[k] > 0) {
            recharger(c);
        }
    }

    double dispersion(Combattant c, Arme a) {
        double d = a.dispersion;
        boolean accroupi = c.accroupi();
        if (a == Arme.ZENITH) {
            d = accroupi && !c.bouge() ? 0.0008 : accroupi ? 0.03 : a.dispersion;
        } else if (accroupi) {
            d *= 0.7;
        }
        if (c.bouge()) {
            d += a.mouvement;
        }
        if (c.p != null && !c.p.isOnGround()) {
            d = d * 2.5 + 0.05;
        }
        if (c.commotion > partie.t) {
            d = d * 3 + 0.04;
        }
        return d;
    }

    private static Vector devier(Vector dir, double disp, Random rnd) {
        if (disp <= 0) {
            return dir.clone();
        }
        Vector a = Math.abs(dir.getY()) < 0.95 ? new Vector(0, 1, 0) : new Vector(1, 0, 0);
        Vector u = dir.getCrossProduct(a).normalize();
        Vector v = dir.getCrossProduct(u).normalize();
        double r = disp * Math.abs(rnd.nextGaussian()) * 0.8, ang = rnd.nextDouble() * Math.PI * 2;
        return dir.clone().add(u.multiply(Math.cos(ang) * r)).add(v.multiply(Math.sin(ang) * r)).normalize();
    }

    private void tirerJoueur(Combattant c, Arme a, double disp) {
        Player p = c.p;
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection();
        w.playSound(eye, a.son, a == Arme.ZENITH ? 1.6f : 1.1f, a.hauteur);
        Location bout = eye.clone().add(dir.clone().multiply(0.6));
        w.spawnParticle(Particle.SMOKE, bout.clone().add(dir.clone().multiply(0.4)), 1, 0.02, 0.02, 0.02, 0.005);
        for (int i = 0; i < a.plombs; i++) {
            Vector d = devier(dir, disp, rnd);
            RayTraceResult res = w.rayTrace(eye, d, a.portee, FluidCollisionMode.NEVER, true, 0.0, e -> cibleValide(c, e));
            Location fin = res == null ? eye.clone().add(d.clone().multiply(a.portee)) : res.getHitPosition().toLocation(w);
            if (i < 3) {
                trace(bout, fin, c.equipe);
            }
            if (res == null) {
                continue;
            }
            if (res.getHitEntity() != null) {
                Entity e = res.getHitEntity();
                double dist = eye.distance(fin);
                Combattant o = partie.parEntite.get(e.getUniqueId());
                if (o != null) {
                    double dy = fin.getY() - (o.p != null ? o.p.getLocation().getY() : Plan.Y);
                    boolean tete = dy >= (o.accroupi() ? 1.05 : 1.42);
                    double mult = tete ? a.tete : dy < 0.7 ? a.jambes : 1.0;
                    w.spawnParticle(tete ? Particle.CRIT : Particle.DAMAGE_INDICATOR, fin, tete ? 6 : 2, 0.05, 0.05, 0.05, 0.1);
                    partie.blesser(c, o, a.degats(dist) * mult, a.nom, tete);
                } else {
                    partie.pouvoirs.toucherObjet(c, e, a.degats(dist));
                }
            } else if (res.getHitBlock() != null) {
                Block b = res.getHitBlock();
                w.spawnParticle(Particle.BLOCK, fin, 4, 0.05, 0.05, 0.05, 0, b.getBlockData());
            }
        }
    }

    /** Ce qu'un tir de {@code c} peut toucher : ennemis vivants et objets ennemis (tourelle). */
    boolean cibleValide(Combattant c, Entity e) {
        Combattant o = partie.parEntite.get(e.getUniqueId());
        if (o != null) {
            return o.vivant && o.equipe != c.equipe;
        }
        return partie.pouvoirs.objetEnnemi(c, e);
    }

    void trace(Location de, Location a, int equipe) {
        Vector d = a.toVector().subtract(de.toVector());
        double len = d.length();
        if (len < 0.3) {
            return;
        }
        int n = (int) Math.min(48, len * 1.6);
        Vector pas = d.multiply(1.0 / n);
        Location l = de.clone();
        Particle.DustOptions opt = new Particle.DustOptions(equipe == 0 ? Color.fromRGB(0x9CC8FF) : Color.fromRGB(0xFFE0A0), 0.35f);
        for (int i = 0; i < n; i++) {
            w.spawnParticle(Particle.DUST, l, 1, 0, 0, 0, 0, opt);
            l.add(pas);
        }
    }

    // ------------------------------------------------------------------ lame

    void lame(Combattant c) {
        if (!peutAgir(c) || partie.t < c.prochainTir) {
            return;
        }
        c.prochainTir = partie.t + 10;
        Location eye = c.oeil(w);
        Vector dir = eye.getDirection();
        w.playSound(eye, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 1.3f);
        RayTraceResult res = w.rayTrace(eye, dir, 2.8, FluidCollisionMode.NEVER, true, 0.1, e -> cibleValide(c, e));
        if (res == null || res.getHitEntity() == null) {
            return;
        }
        Combattant o = partie.parEntite.get(res.getHitEntity().getUniqueId());
        if (o == null) {
            partie.pouvoirs.toucherObjet(c, res.getHitEntity(), 50);
            return;
        }
        Vector vers = o.pieds(w).toVector().subtract(c.pieds(w).toVector()).setY(0);
        Vector regard = o.regard(w).setY(0);
        boolean dos = vers.lengthSquared() > 0 && regard.lengthSquared() > 0 && vers.normalize().dot(regard.normalize()) > 0.5;
        w.spawnParticle(Particle.SWEEP_ATTACK, res.getHitPosition().toLocation(w), 1);
        partie.blesser(c, o, dos ? 150 : 50, "Lame", false);
    }

    // ------------------------------------------------------------------ bots

    /** Tir d'un bot sur un combattant : touché ou raté selon son niveau, la distance et l'état de la cible. */
    void tirBot(Combattant b, Combattant v) {
        Arme a = b.arme();
        int k = a.place;
        if (b.rechargeFin >= 0 || partie.t < b.prochainTir) {
            return;
        }
        if (b.chargeur[k] <= 0) {
            if (b.reserve[k] > 0) {
                b.rechargeFin = partie.t + a.recharge;
                b.rechargePlace = k;
            } else if (k == 0) {
                b.primaire = null;
            }
            return;
        }
        b.chargeur[k]--;
        int cad = (int) Math.round(a.cadence * (a.auto ? 1.4 : 1.6)) + rnd.nextInt(a.auto ? 2 : 6);
        b.prochainTir = partie.t + cad;
        Location de = b.oeil(w);
        Location vise = v.oeil(w).add(0, -0.35, 0);
        double dist = de.distance(vise);
        w.playSound(de, a.son, 1.0f, a.hauteur);
        double base = b.niveau == 1 ? 0.30 : b.niveau == 2 ? 0.45 : 0.6;
        double chance = base * Math.max(0.25, 1 - dist / (a.portee * 1.3));
        if (v.bouge()) {
            chance *= 0.75;
        }
        if (v.accroupi()) {
            chance *= 0.9;
        }
        if (b.commotion > partie.t) {
            chance *= 0.4;
        }
        if (b.lent > partie.t) {
            chance *= 0.85;
        }
        if (a == Arme.ZENITH) {
            chance *= 0.8;
        }
        double teteP = b.niveau == 1 ? 0.06 : b.niveau == 2 ? 0.12 : 0.20;
        double total = 0;
        boolean tete = false;
        for (int i = 0; i < a.plombs; i++) {
            if (rnd.nextDouble() < chance) {
                boolean h = rnd.nextDouble() < teteP;
                tete |= h;
                total += a.degats(dist) * (h ? a.tete : rnd.nextDouble() < 0.2 ? a.jambes : 1.0);
            }
        }
        Location fin = vise.clone();
        if (total <= 0) {
            fin.add((rnd.nextDouble() - 0.5) * 1.6, (rnd.nextDouble() - 0.3) * 1.0, (rnd.nextDouble() - 0.5) * 1.6);
        }
        Vector d = fin.toVector().subtract(de.toVector());
        if (d.lengthSquared() > 0.01) {
            trace(de.clone().add(d.clone().normalize().multiply(0.6)), fin, b.equipe);
        }
        if (total > 0) {
            partie.blesser(b, v, total, a.nom, tete);
        }
    }

    /** Tir d'un bot ou d'une tourelle sur un objet (tourelle adverse). */
    void tirBotObjet(Combattant b, Location cible, Entity e) {
        Arme a = b.arme();
        int k = a.place;
        if (b.rechargeFin >= 0 || partie.t < b.prochainTir || b.chargeur[k] <= 0) {
            return;
        }
        b.chargeur[k]--;
        b.prochainTir = partie.t + a.cadence * 2 + rnd.nextInt(4);
        Location de = b.oeil(w);
        w.playSound(de, a.son, 1.0f, a.hauteur);
        trace(de, cible, b.equipe);
        if (rnd.nextDouble() < 0.6) {
            partie.pouvoirs.toucherObjet(b, e, a.degats(de.distance(cible)) * a.plombs * 0.5);
        }
    }
}
