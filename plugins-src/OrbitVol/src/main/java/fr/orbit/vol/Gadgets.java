package fr.orbit.vol;

import fr.orbit.vol.Arene.Base;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Objets à effet (clic droit) : propulseur, comète, téléporteur, mur, bombe, grappin, cryo ; marteau au coup. */
final class Gadgets {

    private final Jeu jeu;
    private final Map<String, Long> recharges = new HashMap<>();
    final NamespacedKey BOMBE;

    Gadgets(Jeu jeu) {
        this.jeu = jeu;
        this.BOMBE = new NamespacedKey(jeu.plugin, "bombe");
    }

    /** Objets gardés (pas consommés) : on ne peut pas les acheter deux fois. */
    static boolean permanent(String id) {
        return id.equals("batte") || id.equals("grappin") || id.equals("cryo") || id.equals("marteau");
    }

    /** Nombre maximum qu'on peut avoir sur soi (équilibrage du 24/11). */
    static int stockMax(String id) {
        return switch (id) {
            case "filet", "propulseur", "comete" -> 3;
            case "mur", "bombe" -> 2;
            default -> 1;
        };
    }

    /** Recharge de chaque objet, en millisecondes. */
    static long recharge(String id) {
        return switch (id) {
            case "filet" -> 6000;
            case "propulseur" -> 12000;
            case "comete" -> 30000;
            case "teleporteur" -> 45000;
            case "mur" -> 20000;
            case "bombe" -> 20000;
            case "grappin" -> 8000;
            case "cryo" -> 15000;
            case "marteau" -> 6000;
            case "cape" -> 30000;
            default -> 0;
        };
    }

    boolean pret(Player p, String id) {
        return pret(p, id, recharge(id));
    }

    private boolean pret(Player p, String id, long ms) {
        String k = p.getUniqueId() + ":" + id;
        long now = System.currentTimeMillis();
        Long fin = recharges.get(k);
        if (fin != null && fin > now) {
            Txt.bar(p, "<#FF4D6D>Recharge : " + ((fin - now) / 1000 + 1) + " s");
            return false;
        }
        recharges.put(k, now + ms);
        return true;
    }

    /** Vrai (et message) si l'objet est encore en recharge, sans la relancer. */
    private boolean enRecharge(Player p, String id) {
        Long fin = recharges.get(p.getUniqueId() + ":" + id);
        long now = System.currentTimeMillis();
        if (fin == null || fin <= now) return false;
        Txt.bar(p, "<#FF4D6D>Recharge : " + ((fin - now) / 1000 + 1) + " s");
        return true;
    }

    private static void consommer(ItemStack it) {
        it.setAmount(it.getAmount() - 1);
    }

    /** Fait lâcher l'Orbito porté par ce joueur. */
    private void lacher(Player cible) {
        Jeu.Spec s = jeu.porte.get(cible.getUniqueId());
        if (s != null) jeu.rendre(s, true);
    }

    /** Clic droit avec un objet : vrai si l'objet a été utilisé. */
    boolean utiliser(Player p, ItemStack it) {
        if (it == null) return false;
        if (Objets.est(it, "propulseur")) { propulseur(p, it); return true; }
        if (Objets.est(it, "comete")) { comete(p, it); return true; }
        if (Objets.est(it, "teleporteur")) { teleporteur(p, it); return true; }
        if (Objets.est(it, "mur")) { mur(p, it); return true; }
        if (Objets.est(it, "bombe")) { bombe(p, it); return true; }
        if (Objets.est(it, "grappin")) { grappin(p); return true; }
        if (Objets.est(it, "cryo")) { cryo(p); return true; }
        return false;
    }

    private void propulseur(Player p, ItemStack it) {
        if (!pret(p, "propulseur")) return;
        consommer(it);
        Vector v = p.getLocation().getDirection().setY(0);
        if (v.lengthSquared() < 0.01) v = new Vector(0, 0, 1);
        p.setVelocity(v.normalize().multiply(1.5).setY(0.5));
        p.getWorld().spawnParticle(Particle.FIREWORK, p.getLocation(), 25, 0.2, 0.1, 0.2, 0.08);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 1.2f);
    }

    private void comete(Player p, ItemStack it) {
        if (!pret(p, "comete")) return;
        consommer(it);
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 0, false, true, true));
        p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 100, 0, false, true, true));
        p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 30, 0.3, 0.6, 0.3, 0.05);
        Txt.sound(p, Sound.BLOCK_BEACON_ACTIVATE, 1.8f);
        Txt.bar(p, "<#FFE08A><b>Comète !</b> <#E6EDF3>vitesse 5 s");
    }

    private void teleporteur(Player p, ItemStack it) {
        if (jeu.porte.containsKey(p.getUniqueId())) { Txt.bar(p, "<#FF4D6D>Impossible en portant un Orbito !"); return; }
        Base b = jeu.baseDe.get(p.getUniqueId());
        if (b == null) return;
        if (!pret(p, "teleporteur")) return;
        consommer(it);
        p.getWorld().spawnParticle(Particle.PORTAL, p.getLocation().add(0, 1, 0), 60, 0.3, 0.8, 0.3, 0.4);
        p.teleport(b.spawn());
        p.getWorld().spawnParticle(Particle.REVERSE_PORTAL, p.getLocation().add(0, 1, 0), 60, 0.3, 0.8, 0.3, 0.05);
        Txt.sound(p, Sound.ENTITY_ENDERMAN_TELEPORT, 1.2f);
    }

    private void mur(Player p, ItemStack it) {
        if (enRecharge(p, "mur")) return;
        Location l = p.getLocation();
        Vector f = l.getDirection().setY(0);
        if (f.lengthSquared() < 0.01) return;
        f.normalize();
        Vector cote = new Vector(-f.getZ(), 0, f.getX());
        Location c = l.clone().add(f.clone().multiply(2));
        List<Block> poses = new ArrayList<>();
        for (int i = -1; i <= 1; i++)
            for (int y = 0; y < 3; y++) {
                Block bl = c.clone().add(cote.clone().multiply(i)).add(0, y, 0).getBlock();
                if (bl.getY() <= Arene.Y || bl.getType() != Material.AIR) continue;
                boolean pris = false;
                for (Entity e : bl.getWorld().getNearbyEntities(bl.getLocation().add(0.5, 0.5, 0.5), 0.6, 0.6, 0.6))
                    if (e instanceof Player) pris = true;
                if (pris) continue;
                bl.setType(Material.LIGHT_BLUE_STAINED_GLASS, false);
                poses.add(bl);
            }
        if (poses.isEmpty()) { Txt.bar(p, "<#FF4D6D>Pas de place pour le mur."); return; }
        recharges.put(p.getUniqueId() + ":mur", System.currentTimeMillis() + recharge("mur"));
        consommer(it);
        p.getWorld().playSound(c, Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.6f);
        jeu.plugin.getServer().getScheduler().runTaskLater(jeu.plugin, () -> {
            for (Block bl : poses) if (bl.getType() == Material.LIGHT_BLUE_STAINED_GLASS) bl.setType(Material.AIR, false);
            p.getWorld().playSound(c, Sound.BLOCK_GLASS_BREAK, 0.7f, 1.4f);
        }, 100);
    }

    private void bombe(Player p, ItemStack it) {
        if (!pret(p, "bombe")) return;
        ItemStack vue = it.clone();
        vue.setAmount(1);
        consommer(it);
        Snowball s = p.launchProjectile(Snowball.class);
        s.setItem(vue);
        s.setVelocity(s.getVelocity().multiply(1.1));
        s.getPersistentDataContainer().set(BOMBE, PersistentDataType.BYTE, (byte) 1);
        Txt.sound(p, Sound.ENTITY_SNOWBALL_THROW, 0.6f);
    }

    /** Impact de la bombe à gravité : onde de choc qui projette et fait tout lâcher. */
    boolean impact(Snowball s, Location l) {
        if (!s.getPersistentDataContainer().has(BOMBE, PersistentDataType.BYTE)) return false;
        UUID lanceur = s.getShooter() instanceof Player p ? p.getUniqueId() : null;
        l.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, l, 1);
        l.getWorld().spawnParticle(Particle.DUST, l, 60, 2, 1, 2, 0, new Particle.DustOptions(Color.fromRGB(0xA64DFF), 2f));
        l.getWorld().playSound(l, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.3f);
        for (Player o : l.getWorld().getPlayers()) {
            if (o.getUniqueId().equals(lanceur) || o.getLocation().distanceSquared(l) > 9) continue;
            Vector v = o.getLocation().toVector().subtract(l.toVector()).setY(0);
            if (v.lengthSquared() < 0.01) v = new Vector(0.1, 0, 0);
            o.setVelocity(v.normalize().multiply(1.2).setY(0.5));
            lacher(o);
        }
        return true;
    }

    private void grappin(Player p) {
        RayTraceResult r = p.getWorld().rayTraceBlocks(p.getEyeLocation(), p.getEyeLocation().getDirection(), 28);
        if (r == null || r.getHitBlock() == null) { Txt.bar(p, "<#5A6270>Rien à agripper."); return; }
        if (!pret(p, "grappin")) return;
        Location cible = r.getHitPosition().toLocation(p.getWorld());
        Vector v = cible.toVector().subtract(p.getLocation().toVector());
        double d = v.length();
        Vector pas = v.clone().normalize().multiply(0.6);
        Location t = p.getEyeLocation().clone();
        for (double x = 0; x < d; x += 0.6) {
            p.getWorld().spawnParticle(Particle.DUST, t, 1, 0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(0xE0B864), 0.8f));
            t.add(pas);
        }
        p.setVelocity(v.normalize().multiply(Math.min(2.6, 0.9 + d * 0.09)).setY(Math.max(0.4, v.getY() * 0.12 + 0.45)));
        p.getWorld().playSound(p.getLocation(), Sound.ITEM_CROSSBOW_SHOOT, 1f, 1.4f);
    }

    private void cryo(Player p) {
        RayTraceResult r = p.getWorld().rayTraceEntities(p.getEyeLocation(), p.getEyeLocation().getDirection(), 22, 0.5,
                e -> e instanceof Player o && !o.equals(p));
        double dist = r != null ? r.getHitPosition().distance(p.getEyeLocation().toVector()) : 22;
        if (!pret(p, "cryo")) return;
        Vector dir = p.getEyeLocation().getDirection();
        Location t = p.getEyeLocation().clone();
        for (double x = 0; x < dist; x += 0.5) {
            p.getWorld().spawnParticle(Particle.DUST, t, 1, 0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(0x9FE8FF), 1.1f));
            t.add(dir.clone().multiply(0.5));
        }
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_HURT_FREEZE, 1f, 1.6f);
        if (r == null || !(r.getHitEntity() instanceof Player cible)) return;
        cible.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 3, false, true, true));
        cible.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 40, 200, false, false, false));
        cible.setFreezeTicks(140);
        cible.getWorld().spawnParticle(Particle.SNOWFLAKE, cible.getLocation().add(0, 1, 0), 40, 0.3, 0.6, 0.3, 0.02);
        lacher(cible);
        Txt.bar(cible, "<#9FE8FF><b>Gelé !</b>");
    }

    /** Coup de marteau cosmique : onde de choc autour de la victime (toutes les 3 s). */
    void marteau(Player attaquant, Player victime) {
        if (!pret(attaquant, "marteau")) return;
        Location l = victime.getLocation();
        l.getWorld().spawnParticle(Particle.SONIC_BOOM, l.clone().add(0, 0.5, 0), 1);
        l.getWorld().playSound(l, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1f, 1f);
        for (Player o : l.getWorld().getPlayers()) {
            if (o.equals(attaquant) || o.equals(victime) || o.getLocation().distanceSquared(l) > 12) continue;
            Vector v = o.getLocation().toVector().subtract(l.toVector()).setY(0);
            if (v.lengthSquared() < 0.01) v = new Vector(0.1, 0, 0);
            o.setVelocity(v.normalize().multiply(1.3).setY(0.5));
            lacher(o);
        }
    }

    void oublier(UUID id) {
        recharges.keySet().removeIf(k -> k.startsWith(id.toString()));
    }
}
