package fr.orbit.mmo.boss;

import fr.orbit.mmo.OrbitMMO;
import fr.orbit.mmo.item.Items;
import fr.orbit.mmo.mob.MobState;
import fr.orbit.mmo.player.PlayerData;
import fr.orbit.mmo.player.Stats;
import fr.orbit.mmo.registry.Defs.BossDef;
import fr.orbit.mmo.registry.Defs.FloorDef;
import fr.orbit.mmo.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** Boss d'étage : apparition quand un joueur entre dans la salle, récompenses, ouverture de l'étage suivant. */
public final class BossService {

    private final OrbitMMO plugin;
    private final Map<Integer, BossFight> fights = new HashMap<>();
    private final Random random = new Random();

    public BossService(OrbitMMO plugin) {
        this.plugin = plugin;
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 40L, 10L);
    }

    private void tick() {
        long now = System.currentTimeMillis();
        double arenaR = plugin.floors().arenaRadius() - 1;
        for (World w : Bukkit.getWorlds()) {
            int f = plugin.floors().floorOf(w);
            if (f <= 0) {
                continue;
            }
            BossFight fight = fights.get(f);
            if (fight != null) {
                if (!fight.tick()) {
                    fights.remove(f);
                }
                continue;
            }
            if (now < plugin.floors().bossReadyAt(f)) {
                continue;
            }
            Location c = plugin.floors().arenaCenter(w);
            List<Player> inside = new ArrayList<>();
            for (Player p : w.getPlayers()) {
                PlayerData d = plugin.players().loaded(p.getUniqueId());
                double dx = p.getLocation().getX() - c.getX();
                double dz = p.getLocation().getZ() - c.getZ();
                if (d != null && d.classId != null && !p.isDead() && dx * dx + dz * dz <= arenaR * arenaR
                        && (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE)) {
                    inside.add(p);
                }
            }
            if (!inside.isEmpty()) {
                begin(f, w, inside);
            }
        }
    }

    private void begin(int f, World w, List<Player> inside) {
        FloorDef fd = plugin.floors().def(f);
        BossDef bd = plugin.registry().boss(fd.boss());
        if (bd == null) {
            return;
        }
        Location c = plugin.floors().arenaCenter(w);
        c.setYaw(90f);
        int level = fd.maxLevel() + 2;
        double factor = 1 + 0.6 * (inside.size() - 1);
        MobState ms = plugin.mobs().spawn(bd.asMob(), level, c, f, bd, factor);
        if (ms == null) {
            return;
        }
        BossFight fight = new BossFight(plugin, f, bd, ms, c);
        fights.put(f, fight);
        fight.begin(inside);
    }

    public void onDamaged(MobState ms) {
        BossFight fight = fights.get(ms.floor);
        if (fight != null && fight.boss == ms) {
            fight.onDamaged();
        }
    }

    public void onBossDeath(MobState ms, Player killer) {
        BossFight fight = fights.remove(ms.floor);
        if (fight != null) {
            fight.cleanup();
        }
        BossDef def = ms.boss;
        int f = ms.floor;
        World w = ms.entity.getWorld();
        Location l = ms.entity.getLocation();

        long xp = Math.round(Stats.mobXp(ms.level) * def.xp());
        long gold = Math.round(Stats.mobGold(ms.level) * def.col());
        List<Player> victors = new ArrayList<>();
        for (UUID id : ms.damagers.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) {
                continue;
            }
            victors.add(p);
            PlayerData d = plugin.players().get(p);
            d.gold += gold;
            d.kills++;
            plugin.progression().addXp(p, d, xp);
            Text.msg(p, "<#D7DCE2>Boss vaincu : <#7FD4FF>+" + Text.num(xp) + " XP</#7FD4FF>  <#FFD66E>+" + Text.num(gold) + " or");
            // Butin personnel : un objet Rare ou mieux pour chaque participant, parfois une rune.
            fr.orbit.mmo.item.Loot.rollBossLoot(plugin, p, d, ms.level, def.id());
        }

        // Butin : au dernier coup, sinon au plus gros contributeur.
        Player lab = killer;
        if (lab == null) {
            UUID top = null;
            double best = -1;
            for (Map.Entry<UUID, Double> e : ms.damagers.entrySet()) {
                if (e.getValue() > best) {
                    best = e.getValue();
                    top = e.getKey();
                }
            }
            lab = top == null ? null : Bukkit.getPlayer(top);
        }
        if (lab != null) {
            PlayerData ld = plugin.players().get(lab);
            fr.orbit.mmo.classes.ClassDef lc = plugin.progression().cls(ld);
            ItemStack item = fr.orbit.mmo.item.Loot.bossLab(def, fr.orbit.mmo.clazz.WeaponType.of(plugin.bar().weapon(lab, ld)),
                    fr.orbit.mmo.item.ArmorType.of(lc), ms.level, lc == null ? null : fr.orbit.mmo.util.Fx.color(lc.hex));
            fr.orbit.mmo.item.Binding.onLoot(item, lab); // lié quand ramassé
            Items.give(lab, item);
            Bukkit.broadcast(Text.mm(Text.PREFIX + "<#FFD66E>✦ " + Text.esc(lab.getName())
                    + " remporte le butin du boss : </#FFD66E>" + "<gradient:#FFD66E:#FF8A3B>" + Text.esc(def.labName()) + "</gradient>"));
        }

        for (Player p : w.getPlayers()) {
            Text.title(p, "<gradient:#FFD66E:#FFF3B0><b>Félicitations !</b></gradient>",
                    "<#D7DCE2>Étage " + f + " franchi · " + Text.esc(def.name()) + " vaincu", 10, 80, 20);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }
        fireworks(l);

        // Record du serveur, puis l'étage est franchi pour chaque vainqueur et pour sa guilde (classement, prime de
        // premier passage) : les étages ne s'ouvrent plus pour tout le serveur d'un coup.
        if (f > plugin.floors().highestCleared()) {
            plugin.floors().setHighestCleared(f);
        }
        plugin.guilds().floorCleared(f, victors);
        long minutes = plugin.getConfig().getLong("bosses-respawn-minutes", 10);
        plugin.floors().setBossReadyAt(f, System.currentTimeMillis() + minutes * 60_000L);
    }

    private void fireworks(Location l) {
        for (int i = 0; i < 6; i++) {
            Location at = l.clone().add(random.nextDouble() * 8 - 4, 2, random.nextDouble() * 8 - 4);
            l.getWorld().spawn(at, Firework.class, fw -> {
                FireworkMeta meta = fw.getFireworkMeta();
                meta.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE)
                        .withColor(Color.fromRGB(0xFFD66E), Color.fromRGB(0x7FD4FF)).withFade(Color.WHITE)
                        .trail(true).flicker(true).build());
                meta.setPower(1);
                fw.setFireworkMeta(meta);
            });
        }
    }

    /** Admin : rend le boss de l'étage disponible immédiatement. */
    public void resetCooldown(int floor) {
        plugin.floors().setBossReadyAt(floor, 0);
    }

    public void shutdown() {
        for (BossFight fight : fights.values()) {
            plugin.mobs().remove(fight.boss);
            fight.cleanup();
        }
        fights.clear();
    }
}
