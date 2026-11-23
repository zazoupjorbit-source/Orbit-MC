package fr.naruto.core.jutsu;

import fr.naruto.core.ninja.Element;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.function.Consumer;

import static fr.naruto.core.jutsu.Effects.*;

/** Enregistre tout le roster de jutsu (10+ par element + techniques generales). */
public final class JutsuLibrary {

    private JutsuLibrary() {}

    private static void a(JutsuService s, String id, String name, Element el, int cost, int cdSec,
                          int tier, Material icon, String lore, Consumer<CastContext> fx) {
        s.register(new SimpleJutsu(id, name, el, cost, cdSec * 1000L, tier, icon, lore, fx));
    }

    public static void registerAll(JutsuService s) {
        // ===================== KATON (Feu) =====================
        a(s, "gokakyu", "Katon: Gokakyu no Jutsu", Element.KATON, 30, 4, 1, Material.FIRE_CHARGE, "Grande boule de feu.",
                combo(sound(Sound.ITEM_FIRECHARGE_USE, 1f), line(18, 8, Particle.FLAME, 80, 0.6, 0.3)));
        a(s, "endan", "Katon: Endan", Element.KATON, 18, 3, 1, Material.BLAZE_POWDER, "Balle de feu rapide.",
                combo(sound(Sound.ENTITY_BLAZE_SHOOT, 1f), line(20, 6, Particle.FLAME, 60, 0.4, 0.2)));
        a(s, "hosenka", "Katon: Hosenka no Jutsu", Element.KATON, 22, 4, 1, Material.MAGMA_CREAM, "Volee de flammes en eventail.",
                cone(10, 35, 4, 0.6, 0.2, Particle.FLAME));
        a(s, "ryuka", "Katon: Ryuka no Jutsu", Element.KATON, 30, 5, 2, Material.BLAZE_ROD, "Flamme du dragon.",
                line(20, 9, Particle.FLAME, 120, 0.6, 0.3));
        a(s, "karyu_endan", "Katon: Karyu Endan", Element.KATON, 55, 9, 3, Material.MAGMA_BLOCK, "Dragon de feu devastateur.",
                combo(line(24, 14, Particle.FLAME, 140, 0.8, 0.4), burst(24, 3, 6, Particle.LAVA, 100, 0.3)));
        a(s, "goka_messhitsu", "Katon: Goka Messhitsu", Element.KATON, 45, 8, 2, Material.CAMPFIRE, "Mer de flammes (zone).",
                burst(18, 4, 9, Particle.FLAME, 120, 0.2));
        a(s, "haisekisho", "Katon: Haisekisho", Element.KATON, 28, 5, 2, Material.GUNPOWDER, "Nuage de cendres aveuglant.",
                combo(cone(9, 45, 5, 0.3, 0.1, Particle.LARGE_SMOKE), debuffNearby(9, 80, 0, PotionEffectType.BLINDNESS)));
        a(s, "bakufu_ranbu", "Katon: Bakufu Ranbu", Element.KATON, 40, 7, 2, Material.FIRE_CHARGE, "Vague de feu qui repousse.",
                cone(10, 40, 7, 1.4, 0.4, Particle.FLAME));
        a(s, "goryuka", "Katon: Goryuka no Jutsu", Element.KATON, 35, 6, 2, Material.MAGMA_CREAM, "Missile enflamme explosif.",
                burst(20, 3, 10, Particle.LAVA, 120, 0.4));
        a(s, "hono_yoroi", "Katon: Armure de feu", Element.KATON, 25, 10, 1, Material.BLAZE_POWDER, "Aura de feu : force + resistance au feu.",
                combo(buff(160, 0, PotionEffectType.STRENGTH), buff(220, 0, PotionEffectType.FIRE_RESISTANCE), sound(Sound.ITEM_FIRECHARGE_USE, 0.8f)));

        // ===================== SUITON (Eau) =====================
        a(s, "suidan", "Suiton: Suidan no Jutsu", Element.SUITON, 25, 3, 1, Material.PRISMARINE_CRYSTALS, "Balle d'eau puissante.",
                combo(sound(Sound.ENTITY_GENERIC_SPLASH, 0.8f), line(20, 6, Particle.SPLASH, 0, 1.4, 0.35)));
        a(s, "teppodama", "Suiton: Teppodama", Element.SUITON, 16, 3, 1, Material.SNOWBALL, "Volee de balles d'eau.",
                line(20, 5, Particle.SPLASH, 0, 0.6, 0.2));
        a(s, "suiryudan", "Suiton: Suiryudan no Jutsu", Element.SUITON, 45, 7, 2, Material.HEART_OF_THE_SEA, "Dragon d'eau.",
                line(24, 12, Particle.SPLASH, 0, 1.8, 0.4));
        a(s, "daibakufu", "Suiton: Daibakufu no Jutsu", Element.SUITON, 55, 9, 3, Material.WATER_BUCKET, "Grande cascade (zone).",
                combo(burst(20, 4, 11, Particle.SPLASH, 0, 0.5), cone(12, 45, 4, 1.2, 0.3, Particle.SPLASH)));
        a(s, "bakusui_shoha", "Suiton: Bakusui Shoha", Element.SUITON, 40, 7, 2, Material.PRISMARINE_SHARD, "Torrent qui balaie tout.",
                cone(12, 50, 7, 1.6, 0.35, Particle.SPLASH));
        a(s, "suijinheki", "Suiton: Suijinheki", Element.SUITON, 30, 8, 2, Material.PRISMARINE, "Mur d'eau glacee (8s).",
                combo(sound(Sound.ENTITY_GENERIC_SPLASH, 0.7f), wall(Material.PACKED_ICE, 2, 4, 8)));
        a(s, "suiben", "Suiton: Suiben", Element.SUITON, 20, 4, 1, Material.KELP, "Fouet d'eau : attire la cible.",
                combo(pull(18, 1.3), line(18, 4, Particle.SPLASH, 0, 0, 0)));
        a(s, "mizu_bunshin", "Suiton: Mizu Bunshin", Element.SUITON, 25, 8, 2, Material.TURTLE_HELMET, "Clone d'eau : resistance.",
                combo(buff(120, 1, PotionEffectType.RESISTANCE), sound(Sound.ENTITY_GENERIC_SPLASH, 1f)));
        a(s, "kirigakure", "Suiton: Kirigakure no Jutsu", Element.SUITON, 22, 9, 2, Material.SNOW_BLOCK, "Brume : invisibilite + ennemis ralentis.",
                combo(buff(120, 0, PotionEffectType.INVISIBILITY), debuffNearby(10, 100, 1, PotionEffectType.SLOWNESS)));
        a(s, "suirei", "Suiton: Grace aquatique", Element.SUITON, 15, 8, 1, Material.TROPICAL_FISH, "Rapidite aquatique + apnee.",
                combo(buff(220, 0, PotionEffectType.DOLPHINS_GRACE), buff(220, 0, PotionEffectType.WATER_BREATHING)));

        // ===================== FUTON (Vent) =====================
        a(s, "reppusho", "Futon: Reppusho", Element.FUTON, 20, 4, 1, Material.FEATHER, "Paume de vent.",
                combo(sound(Sound.ENTITY_BREEZE_SHOOT, 1f), cone(8, 40, 3, 1.6, 0.45, Particle.CLOUD)));
        a(s, "daitoppa", "Futon: Daitoppa", Element.FUTON, 28, 5, 1, Material.WIND_CHARGE, "Grande percee : forte poussee.",
                cone(11, 45, 5, 2.0, 0.5, Particle.CLOUD));
        a(s, "kazekiri", "Futon: Kazekiri no Jutsu", Element.FUTON, 30, 5, 2, Material.SHEARS, "Lames de vent tranchantes.",
                line(20, 9, Particle.SWEEP_ATTACK, 0, 0.4, 0.1));
        a(s, "shinkuha", "Futon: Shinkuha", Element.FUTON, 26, 4, 2, Material.PHANTOM_MEMBRANE, "Onde de vide coupante.",
                line(22, 8, Particle.CLOUD, 0, 0.3, 0.1));
        a(s, "rasenshuriken", "Futon: Rasenshuriken", Element.FUTON, 60, 10, 3, Material.NETHER_STAR, "Shuriken tourbillonnant devastateur.",
                combo(line(22, 15, Particle.SWEEP_ATTACK, 0, 0.6, 0.2), burst(22, 3, 6, Particle.CLOUD, 0, 0.3)));
        a(s, "kamikaze", "Futon: Kamikaze", Element.FUTON, 18, 6, 1, Material.ELYTRA, "Bourrasque : dash + rapidite.",
                combo(dash(1.5, Particle.CLOUD), buff(80, 1, PotionEffectType.SPEED)));
        a(s, "tatsumaki", "Futon: Tatsumaki", Element.FUTON, 40, 8, 2, Material.WHITE_DYE, "Tornade : souleve les ennemis.",
                lift(6, 1.0, 4));
        a(s, "renkudan", "Futon: Renkudan", Element.FUTON, 24, 4, 1, Material.GHAST_TEAR, "Bullet d'air perforant (zone).",
                burst(18, 2.5, 7, Particle.CLOUD, 0, 0.4));
        a(s, "fujin", "Futon: Armure de vent", Element.FUTON, 22, 9, 2, Material.PHANTOM_MEMBRANE, "Legerete : saut + chute lente.",
                combo(buff(200, 1, PotionEffectType.JUMP_BOOST), buff(200, 0, PotionEffectType.SLOW_FALLING)));
        a(s, "shinku_taigyoku", "Futon: Shinku Taigyoku", Element.FUTON, 50, 9, 3, Material.WIND_CHARGE, "Sphere de vide (grosse zone).",
                burst(22, 5, 12, Particle.CLOUD, 0, 0.5));

        // ===================== DOTON (Terre) =====================
        a(s, "doryuheki", "Doton: Doryuheki", Element.DOTON, 35, 9, 2, Material.DIRT, "Mur de terre (10s).",
                combo(sound(Sound.BLOCK_GRAVEL_BREAK, 0.7f), wall(Material.COARSE_DIRT, 2, 4, 10)));
        a(s, "tsuchi_kabe", "Doton: Tsuchi Kabe", Element.DOTON, 25, 7, 1, Material.STONE, "Muraille de pierre (8s).",
                wall(Material.STONE, 2, 3, 8));
        a(s, "doryudan", "Doton: Doryudan", Element.DOTON, 40, 7, 2, Material.GRANITE, "Dragon de terre (projectile).",
                line(20, 10, Particle.CRIT, 0, 0.8, 0.3));
        a(s, "iwa_yari", "Doton: Iwa Yari", Element.DOTON, 22, 4, 1, Material.POINTED_DRIPSTONE, "Lances de pierre.",
                line(18, 7, Particle.CRIT, 0, 0.3, 0.1));
        a(s, "ganban_kyu", "Doton: Ganban Kyu", Element.DOTON, 45, 8, 2, Material.DEEPSLATE, "Ecrasement rocheux (zone).",
                burst(16, 3.5, 11, Particle.CRIT, 0, -0.2));
        a(s, "doryo_dango", "Doton: Doryo Dango", Element.DOTON, 55, 10, 3, Material.DRIPSTONE_BLOCK, "Boulet de terre colossal.",
                combo(burst(18, 4, 14, Particle.CRIT, 0, 0.3), sound(Sound.BLOCK_STONE_BREAK, 0.8f)));
        a(s, "yomi_numa", "Doton: Yomi Numa", Element.DOTON, 35, 8, 2, Material.MUD, "Marais : englue les ennemis proches.",
                combo(debuffNearby(6, 120, 3, PotionEffectType.SLOWNESS), debuffNearby(6, 120, 1, PotionEffectType.MINING_FATIGUE)));
        a(s, "retsudo_tensho", "Doton: Retsudo Tensho", Element.DOTON, 40, 8, 2, Material.MOSS_BLOCK, "Seisme : projette autour.",
                lift(6, 0.9, 6));
        a(s, "doton_yoroi", "Doton: Armure de terre", Element.DOTON, 22, 10, 1, Material.CLAY, "Peau de pierre : resistance.",
                buff(200, 1, PotionEffectType.RESISTANCE));
        a(s, "moguragakure", "Doton: Moguragakure", Element.DOTON, 20, 8, 2, Material.ROOTED_DIRT, "Taupe : disparais brievement.",
                combo(buff(80, 0, PotionEffectType.INVISIBILITY), buff(80, 0, PotionEffectType.SLOW_FALLING), sound(Sound.BLOCK_GRAVEL_BREAK, 1f)));

        // ===================== RAITON (Foudre) =====================
        a(s, "chidori", "Raiton: Chidori", Element.RAITON, 45, 8, 2, Material.GLOWSTONE_DUST, "Charge foudroyante (cible proche).",
                combo(sound(Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.6f), dash(1.3, Particle.ELECTRIC_SPARK), line(6, 12, Particle.ELECTRIC_SPARK, 0, 0, 0.1)));
        a(s, "gian", "Raiton: Gian", Element.RAITON, 26, 4, 1, Material.LIGHTNING_ROD, "Lance electrique.",
                line(20, 8, Particle.ELECTRIC_SPARK, 0, 0.3, 0.1));
        a(s, "raiko", "Raiton: Raiko", Element.RAITON, 35, 6, 2, Material.GLOW_INK_SAC, "Eclair sur la cible visee.",
                strike(24, 9));
        a(s, "jibashi", "Raiton: Jibashi", Element.RAITON, 30, 5, 2, Material.COPPER_INGOT, "Onde electrique paralysante.",
                combo(cone(10, 45, 5, 0.6, 0.2, Particle.ELECTRIC_SPARK), debuffNearby(10, 60, 2, PotionEffectType.SLOWNESS)));
        a(s, "raiju", "Raiton: Raiju Hashiri", Element.RAITON, 40, 7, 2, Material.GOLD_NUGGET, "Bete-foudre : dash + eclair.",
                combo(dash(1.6, Particle.ELECTRIC_SPARK), strike(8, 8)));
        a(s, "rairyu", "Raiton: Rairyudan", Element.RAITON, 50, 8, 3, Material.GOLD_INGOT, "Dragon de foudre.",
                line(24, 13, Particle.ELECTRIC_SPARK, 0, 0.6, 0.2));
        a(s, "kaminari", "Raiton: Kaminari", Element.RAITON, 32, 6, 2, Material.REDSTONE, "Fracas electrique (autour).",
                combo(lift(5, 0.4, 6), debuffNearby(5, 40, 3, PotionEffectType.SLOWNESS)));
        a(s, "byakurai", "Raiton: Byakurai", Element.RAITON, 30, 5, 2, Material.AMETHYST_SHARD, "Foudre blanche percante.",
                line(26, 11, Particle.ELECTRIC_SPARK, 0, 0.2, 0));
        a(s, "raiton_yoroi", "Raiton: Armure de foudre", Element.RAITON, 25, 10, 2, Material.LIGHTNING_ROD, "Manteau electrique : vitesse + force.",
                combo(buff(160, 1, PotionEffectType.SPEED), buff(160, 0, PotionEffectType.STRENGTH)));
        a(s, "kirin", "Raiton: Kirin", Element.RAITON, 70, 12, 3, Material.NETHER_STAR, "Foudre celeste devastatrice.",
                combo(strike(30, 20), burst(30, 3, 8, Particle.ELECTRIC_SPARK, 0, 0.3)));

        // ===================== GENERAL (tout element) =====================
        a(s, "kawarimi", "Kawarimi no Jutsu", Element.NONE, 15, 6, 1, Material.OAK_LOG, "Substitution : recule + resistance.", substitution());
        a(s, "shunshin", "Shunshin no Jutsu", Element.NONE, 12, 4, 1, Material.SUGAR, "Corps scintillant : dash rapide.",
                combo(dash(1.8, Particle.CLOUD), buff(40, 2, PotionEffectType.SPEED), sound(Sound.ENTITY_ENDERMAN_TELEPORT, 1.4f)));
        a(s, "kage_bunshin", "Kage Bunshin no Jutsu", Element.NONE, 30, 10, 1, Material.PAPER, "Clones de l'ombre : resistance + vitesse.",
                combo(buff(120, 1, PotionEffectType.RESISTANCE), buff(120, 0, PotionEffectType.SPEED), sound(Sound.ENTITY_ENDERMAN_TELEPORT, 1f)));
        a(s, "kai", "Kai (Liberation)", Element.NONE, 10, 8, 1, Material.GLASS, "Dissipe les effets negatifs + soin.", release());
    }

    /** Substitution : laisse une buche, recule de 5 blocs, resistance breve. */
    private static Consumer<CastContext> substitution() {
        return c -> {
            Player p = c.player;
            Location from = p.getLocation().clone();
            Block log = from.getBlock();
            BlockData prev = log.getBlockData();
            if (log.getType().isAir() || log.getType() == Material.SHORT_GRASS) {
                log.setType(Material.OAK_LOG, false);
                Bukkit.getScheduler().runTaskLater(c.plugin, () -> {
                    if (log.getType() == Material.OAK_LOG) log.setBlockData(prev, false);
                }, 40L);
            }
            Vector back = p.getEyeLocation().getDirection().setY(0).normalize().multiply(-5);
            Location to = from.clone().add(back);
            to.setY(from.getY());
            to.setYaw(from.getYaw());
            to.setPitch(from.getPitch());
            p.getWorld().spawnParticle(Particle.LARGE_SMOKE, from.clone().add(0, 1, 0), 25, 0.3, 0.6, 0.3, 0.02);
            p.teleport(to);
            p.getWorld().playSound(to, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.2f);
            p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20, 1));
        };
    }

    /** Kai : retire les effets negatifs et regenere un peu. */
    private static Consumer<CastContext> release() {
        return c -> {
            Player p = c.player;
            for (PotionEffectType t : new PotionEffectType[]{PotionEffectType.SLOWNESS, PotionEffectType.WEAKNESS,
                    PotionEffectType.BLINDNESS, PotionEffectType.MINING_FATIGUE, PotionEffectType.POISON,
                    PotionEffectType.NAUSEA, PotionEffectType.LEVITATION}) {
                p.removePotionEffect(t);
            }
            p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 60, 1));
            p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 15, 0.3, 0.6, 0.3, 0.03);
        };
    }
}
