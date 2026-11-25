package fr.orbit.chasseurs;

import com.destroystokyo.paper.ParticleBuilder;
import fr.orbit.chasseurs.Catalogue.Level;
import fr.orbit.chasseurs.Catalogue.Mode;
import fr.orbit.chasseurs.Catalogue.ShipType;
import io.papermc.paper.entity.TeleportFlag;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Color;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Une partie : mêlée générale, duo 2v2 ou convoi coopératif. Les places libres sont tenues par des bots ; un joueur
 * qui arrive en cours de partie prend la place d'un bot, un joueur qui part est remplacé par un bot.
 */
final class Match {

    enum State { COUNTDOWN, RUNNING, ENDING, DONE }

    static final class Missile {
        final Ship owner;
        final Ship target;
        final Vector3d pos, dir;
        final ItemDisplay entity;
        int life = 110;

        Missile(Ship owner, Ship target, Vector3d pos, Vector3d dir, ItemDisplay entity) {
            this.owner = owner;
            this.target = target;
            this.pos = pos;
            this.dir = dir;
            this.entity = entity;
        }
    }

    enum Bonus {
        REPARATION("bonus_reparation", "<#7CFF8A>✚ Coque réparée"),
        MISSILES("bonus_missiles", "<#FF4FD8>➹ +2 missiles"),
        SURVOLTEUR("bonus_survolteur", "<#E0B864>⚡ Survolteur : arme refroidie, plein de boost");

        final String model, text;

        Bonus(String model, String text) {
            this.model = model;
            this.text = text;
        }
    }

    static final class Pickup {
        final Bonus bonus;
        final Vector3d pos;
        final ItemDisplay entity;
        int backAt;
        float spin;

        Pickup(Bonus bonus, Vector3d pos, ItemDisplay entity) {
            this.bonus = bonus;
            this.pos = pos;
            this.entity = entity;
        }
    }

    private record Fx(Entity entity, int until) {
    }

    final OrbitChasseurs plugin;
    final Games games;
    final Mode mode;
    final Arena arena;
    final Level level;
    final Ship.Tuning tuning;
    final Random rnd = new Random();
    final List<Ship> ships = new ArrayList<>();
    final Map<UUID, Ship> byPlayer = new HashMap<>();
    final List<Missile> missiles = new ArrayList<>();
    private final List<Pickup> pickups = new ArrayList<>();
    private final List<Fx> fx = new ArrayList<>();
    private final List<Ship> gone = new ArrayList<>();
    private final Map<UUID, BossBar[]> bars = new HashMap<>();
    private final int[] teamKills = new int[2];

    State state = State.COUNTDOWN;
    int ticks, countdown, timeLeft, endTicks;
    private int nextTeam = 10, botNames;
    // convoi
    Ship cargo;
    int wave;
    private double cargoT;
    private int toSpawn, spawnCd, waveBreak, fregates;
    private String endTitle = "", endSub = "";
    private Ship winner;
    private int winningTeam = -1;

    Match(OrbitChasseurs plugin, Games games, Mode mode, Arena arena, Level level) {
        this.plugin = plugin;
        this.games = games;
        this.mode = mode;
        this.arena = arena;
        this.level = level;
        this.tuning = plugin.tuning();
        this.countdown = plugin.getConfig().getInt("partie.decompte-secondes", 8) * 20;
        this.timeLeft = plugin.getConfig().getInt("partie.duree-secondes", 240) * 20;
        this.botNames = rnd.nextInt(Catalogue.BOT_NAMES.length);
        arena.hold(plugin, true);
        setup();
    }

    // ------------------------------------------------------------------ mise en place

    private void setup() {
        switch (mode) {
            case MELEE -> {
                int n = Math.max(2, plugin.getConfig().getInt("partie.melee-vaisseaux", 6));
                for (int i = 0; i < n; i++) {
                    addBot(nextTeam++, null);
                }
            }
            case DUO -> {
                addBot(0, null);
                addBot(0, null);
                addBot(1, null);
                addBot(1, null);
            }
            case CONVOI -> {
                cargo = new Ship(this, ShipType.CARGO, "ORBIT-Cargo", Catalogue.CARGO_COLOR, 0, null, null);
                cargo.spawn(arena.path(0), 0);
                ships.add(cargo);
                moveCargo();
                addBot(0, null); // ailier, retiré dès qu'un deuxième joueur arrive
                waveBreak = 100;
            }
        }
        for (int i = 0; i < arena.pickups.size(); i++) {
            Bonus b = Bonus.values()[i % Bonus.values().length];
            Vector3d p = arena.pickups.get(i);
            ItemDisplay e = arena.world.spawn(new Location(arena.world, p.x, p.y, p.z), ItemDisplay.class, d -> {
                plugin.mark(d);
                d.setItemStack(plugin.model(b.model, 0xFFFFFF));
                d.setBrightness(new Display.Brightness(15, 15));
                d.setViewRange(4f);
                d.setInterpolationDuration(20);
                d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(2.2f, 2.2f, 2.2f), new Quaternionf()));
            });
            pickups.add(new Pickup(b, p, e));
        }
    }

    private int color(int team) {
        return switch (mode) {
            case DUO -> team == 0 ? Catalogue.TEAM_BLUE : Catalogue.TEAM_GOLD;
            case CONVOI -> team == 0 ? Catalogue.BOT_COLORS[1] : Catalogue.PIRATE;
            default -> Catalogue.BOT_COLORS[Math.floorMod(team, Catalogue.BOT_COLORS.length)];
        };
    }

    private Ship addBot(int team, ShipType forced) {
        ShipType type = forced != null ? forced : ShipType.HANGAR[rnd.nextInt(Math.min(ShipType.HANGAR.length, 2 + level.ordinal()))];
        boolean pirate = mode == Mode.CONVOI && team == 1;
        Level lv = pirate ? Level.of(level.number() - 1 + (wave >= 5 ? 1 : 0) + (wave >= 10 ? 1 : 0)) : level;
        String name = pirate ? type.label : Catalogue.BOT_NAMES[botNames++ % Catalogue.BOT_NAMES.length];
        Ship s = new Ship(this, type, name, color(team), team, null, new Bot(lv, pirate));
        place(s);
        ships.add(s);
        return s;
    }

    /** Pose (ou repose) un vaisseau à un point de départ loin des ennemis. */
    private void place(Ship s) {
        Vector3d at;
        float yaw;
        if (mode == Mode.CONVOI && s.team == 0 && cargo != null) {
            int k = 1 + (int) ships.stream().filter(o -> o != s && o.team == 0 && o != cargo).count();
            at = arena.path(cargoT - 0.16 - 0.07 * (k % 5)).add(0, (k % 2 == 0 ? 3 : -3), 0);
            Vector3d ahead = arena.path(cargoT).sub(at);
            yaw = (float) Math.toDegrees(Math.atan2(-ahead.x, ahead.z));
        } else if (mode == Mode.CONVOI) {
            at = arena.freePoint(rnd, 56, 68, 4);
            Vector3d in = arena.centre().sub(at);
            yaw = (float) Math.toDegrees(Math.atan2(-in.x, in.z));
        } else {
            Vector3d best = arena.spawns.get(0);
            double bestD = -1;
            for (Vector3d sp : arena.spawns) {
                double near = 999;
                for (Ship o : ships) {
                    if (o != s && o.alive) {
                        near = Math.min(near, o.pos.distance(sp) * (o.team == s.team ? 2.5 : 1));
                    }
                }
                near += rnd.nextDouble() * 4;
                if (near > bestD) {
                    bestD = near;
                    best = sp;
                }
            }
            at = new Vector3d(best);
            Vector3d in = arena.centre().sub(at);
            yaw = (float) Math.toDegrees(Math.atan2(-in.x, in.z));
        }
        if (s.display == null) {
            s.spawn(at, yaw);
        } else {
            s.pos.set(at);
            s.prev.set(at);
            s.vel.zero();
            s.yaw = s.wishYaw = yaw;
            s.pitch = s.wishPitch = 0;
            s.reset();
            s.show(true);
            s.push();
        }
        s.protect = 50;
    }

    List<Ship> humans() {
        List<Ship> list = new ArrayList<>();
        for (Ship s : ships) {
            if (s.human()) {
                list.add(s);
            }
        }
        return list;
    }

    private List<Player> players() {
        List<Player> list = new ArrayList<>();
        for (Ship s : ships) {
            if (s.human() && s.player.isOnline()) {
                list.add(s.player);
            }
        }
        return list;
    }

    boolean joinable() {
        if (state != State.COUNTDOWN && state != State.RUNNING) {
            return false;
        }
        int h = byPlayer.size();
        return switch (mode) {
            case MELEE -> h < plugin.getConfig().getInt("partie.melee-vaisseaux", 6) && (state == State.COUNTDOWN || timeLeft > 600);
            case DUO -> h < 4 && (state == State.COUNTDOWN || timeLeft > 600);
            case CONVOI -> h < plugin.getConfig().getInt("partie.convoi-joueurs-max", 4);
        };
    }

    /** Le joueur est déjà dans le monde de l'arène, son état sauvegardé par {@link Games}. Il prend la place d'un bot. */
    void addPlayer(Player p, Profiles.Profile pr) {
        int team;
        Ship replaced = null;
        if (mode == Mode.CONVOI) {
            team = 0;
            if (byPlayer.size() >= 1) {
                for (Ship s : ships) {
                    if (s.bot != null && s.team == 0) {
                        replaced = s;
                    }
                }
            }
        } else {
            // en duo : l'équipe bleue d'abord (deux amis jouent ensemble), puis l'or
            for (Ship s : ships) {
                if (s.bot != null && (replaced == null || s.team < replaced.team || (s.team == replaced.team && s.kills < replaced.kills))) {
                    replaced = s;
                }
            }
            team = replaced != null ? replaced.team : nextTeam++;
        }
        if (replaced != null) {
            replaced.remove();
            ships.remove(replaced);
        }
        int rgb = mode == Mode.DUO ? color(team) : pr.paint.rgb;
        Ship s = new Ship(this, pr.ship, p.getName(), rgb, team, p, null);
        ships.add(s);
        byPlayer.put(p.getUniqueId(), s);
        place(s);
        games.teleport(p, new Location(arena.world, s.pos.x, s.pos.y - 0.5, s.pos.z, s.yaw, 0f));
        BossBar top = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
        BossBar radar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.GREEN, BossBar.Overlay.NOTCHED_10);
        bars.put(p.getUniqueId(), new BossBar[]{top, radar});
        p.showBossBar(top);
        p.showBossBar(radar);
        Txt.send(p, "<" + mode.color + "><b>" + mode.label + "</b></" + mode.color + "> · " + arena(mode) + " · bots <" + level.color + ">" + level.label
                + "</" + level.color + "> · vaisseau <b>" + pr.ship.label + "</b>");
        p.sendMessage(Txt.mm("  <#9AA4B2>Regarde où tu veux aller · <#E6EDF3>Z</#E6EDF3> poussée · <#E6EDF3>S</#E6EDF3> frein · <#E6EDF3>Q/D</#E6EDF3> glisser de côté · "
                + "<#E6EDF3>Espace</#E6EDF3> boost · <#E6EDF3>Maj</#E6EDF3> dérapage"));
        p.sendMessage(Txt.mm("  <#9AA4B2><#E6EDF3>Clic</#E6EDF3> laser (clic droit maintenu = rafale) · <#E6EDF3>F</#E6EDF3> ou <#E6EDF3>2</#E6EDF3> missile · "
                + "<#E6EDF3>3</#E6EDF3> réparation · <#E6EDF3>/chasseurs quitter</#E6EDF3> pour abandonner"));
        if (state == State.RUNNING) {
            announce("<#7CFF8A>➕ <b>" + p.getName() + "</b> rejoint la partie.");
        }
        updateInventory(s);
    }

    private static String arena(Mode m) {
        return "<#9AA4B2>" + m.arene + "</#9AA4B2>";
    }

    /** Départ d'un joueur (abandon, déconnexion, téléportation). Sa place revient à un bot ; sans joueur, la partie s'arrête. */
    void removePlayer(Player p, boolean record) {
        Ship s = byPlayer.remove(p.getUniqueId());
        if (s == null) {
            return;
        }
        BossBar[] b = bars.remove(p.getUniqueId());
        if (b != null) {
            p.hideBossBar(b[0]);
            p.hideBossBar(b[1]);
        }
        if (record && state == State.RUNNING && score(s, false) > 0) {
            games.reward(this, s, false, false);
        }
        s.remove();
        ships.remove(s);
        if (byPlayer.isEmpty()) {
            if (state != State.DONE) {
                dispose();
            }
            return;
        }
        if (state == State.RUNNING || state == State.COUNTDOWN) {
            if (mode != Mode.CONVOI) {
                addBot(s.team, null);
            }
            announce("<#9AA4B2>➖ " + p.getName() + " quitte la partie.");
        }
    }

    // ------------------------------------------------------------------ commandes des joueurs

    void input(Player p, Input in) {
        Ship s = byPlayer.get(p.getUniqueId());
        if (s == null) {
            return;
        }
        s.inFwd = in.isForward();
        s.inBack = in.isBackward();
        s.inLeft = in.isLeft();
        s.inRight = in.isRight();
        s.inBoost = in.isJump();
        s.inDrift = in.isSneak();
    }

    void trigger(Player p, boolean held) {
        Ship s = byPlayer.get(p.getUniqueId());
        if (s != null) {
            s.trigger = Math.max(s.trigger, held ? 6 : 2);
        }
    }

    void missileKey(Player p) {
        Ship s = byPlayer.get(p.getUniqueId());
        if (s != null && s.alive && state == State.RUNNING) {
            if (s.missiles <= 0) {
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 0.6f, 0.5f);
                p.sendActionBar(Txt.mm("<#E0484A>Plus de missile</#E0484A> <#9AA4B2>· ramasse un bonus rose"));
            } else if (s.missileCd == 0) {
                launch(s, lock(s, 12, 85));
            }
        }
    }

    void repairKey(Player p) {
        Ship s = byPlayer.get(p.getUniqueId());
        if (s != null && s.alive && state == State.RUNNING) {
            if (s.repairCd > 0) {
                p.sendActionBar(Txt.mm("<#9AA4B2>Nanoréparation prête dans <#E6EDF3>" + (s.repairCd / 20 + 1) + " s"));
            } else if (s.hull >= s.hullMax) {
                p.sendActionBar(Txt.mm("<#9AA4B2>Coque intacte"));
            } else {
                repair(s);
            }
        }
    }

    void repair(Ship s) {
        s.repairCd = 900;
        s.repairing = 60;
        if (s.human()) {
            s.player.playSound(s.player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.7f, 1.6f);
            updateInventory(s);
        }
    }

    private void updateInventory(Ship s) {
        games.hotbar(s.player, s.missiles, s.repairCd == 0);
    }

    // ------------------------------------------------------------------ boucle

    void tick() {
        ticks++;
        if (state == State.DONE) {
            return;
        }
        for (Ship s : new ArrayList<>(ships)) {
            if (s.human() && (!s.player.isOnline() || s.display == null || !s.display.isValid())) {
                games.leave(s.player, true);
            }
        }
        if (state == State.DONE) {
            return;
        }
        cleanFx();
        if (state == State.COUNTDOWN) {
            for (Ship s : ships) {
                if (s == cargo) {
                    continue;
                }
                s.tick(true);
                if (s.human() && s.age > 3) {
                    s.seat();
                }
            }
            countdownTick();
            if (ticks % 4 == 0) {
                hud();
            }
            return;
        }
        if (state == State.ENDING) {
            for (Ship s : ships) {
                if (s.human() && s.age > 3) {
                    s.seat();
                }
            }
            if (--endTicks <= 0) {
                finish();
            }
            return;
        }
        // en jeu
        if (mode == Mode.CONVOI) {
            convoi();
        } else {
            timeLeft--;
        }
        for (Ship s : new ArrayList<>(ships)) {
            if (s == cargo) {
                continue;
            }
            if (!s.alive) {
                if (s.respawnIn > 0 && --s.respawnIn == 0) {
                    place(s);
                    if (s.human()) {
                        s.player.playSound(s.player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 1.5f);
                        updateInventory(s);
                    }
                }
                if (s.human()) {
                    s.seat();
                }
                continue;
            }
            if (s.bot != null) {
                s.bot.think(s, this);
            }
            s.tick(false);
            if (state != State.RUNNING) {
                return;
            }
            if (!s.alive) {
                continue;
            }
            if (s.human()) {
                if (s.age > 3) {
                    s.seat();
                }
                if (s.trigger > 0 && s.fireCd == 0 && !s.overheated) {
                    fire(s);
                }
            } else {
                if (s.bot.wantFire && s.fireCd == 0 && !s.overheated) {
                    fire(s);
                }
                if (s.bot.wantMissile && s.missileCd == 0 && s.missiles > 0) {
                    launch(s, s.bot.target);
                }
            }
            if (s.fighter()) {
                ringsAndBonus(s);
            }
            if (ticks % 2 == 0) {
                trail(s);
            }
        }
        if (state != State.RUNNING) {
            return;
        }
        missilesTick();
        pickupsTick();
        for (Ship s : gone) {
            s.remove();
            ships.remove(s);
        }
        gone.clear();
        if (ticks % 2 == 0) {
            hud();
        }
        checkEnd();
        if (testEnd > 0 && state == State.RUNNING && ticks >= testEnd) {
            testReport("durée écoulée");
            dispose();
        }
    }

    private void countdownTick() {
        if (countdown % 20 == 0) {
            int s = countdown / 20;
            for (Player p : players()) {
                if (s > 3) {
                    p.showTitle(Title.title(Txt.mm(Txt.TITLE), Txt.mm("<#9AA4B2>" + mode.label + " · départ dans <#E6EDF3>" + s + " s</#E6EDF3> · d'autres pilotes peuvent rejoindre"),
                            Title.Times.times(Duration.ZERO, Duration.ofMillis(1100), Duration.ZERO)));
                } else if (s > 0) {
                    p.showTitle(Title.title(Txt.mm("<gradient:#3A7FFF:#62CFF4><b>" + s + "</b></gradient>"), Txt.mm("<#9AA4B2>Regarde où tu veux aller · clic pour tirer"),
                            Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(100))));
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.9f, 1f);
                }
            }
        }
        if (--countdown <= 0) {
            state = State.RUNNING;
            for (Player p : players()) {
                p.showTitle(Title.title(Txt.mm("<#7CFF8A><b>DÉCOLLAGE !</b>"), Component.empty(),
                        Title.Times.times(Duration.ZERO, Duration.ofMillis(700), Duration.ofMillis(200))));
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
                p.playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.8f, 0.7f);
            }
        }
    }

    private int testEnd;
    private int testShots, testHits, testMissiles, testCrashes;

    /** Partie de bots seuls (essai par la console) : départ immédiat, durée imposée, résumé dans le journal. */
    void testRun(int seconds) {
        countdown = 20;
        timeLeft = seconds * 20;
        testEnd = seconds * 20 + 20;
    }

    private void testReport(String why) {
        StringBuilder sb = new StringBuilder("Essai " + mode.id + " terminé (" + why + ") après " + ticks / 20 + " s : " + testShots + " tirs, " + testHits
                + " touches, " + testMissiles + " missiles, " + testCrashes + " chocs contre le décor"
                + (mode == Mode.CONVOI ? ", vague " + wave + ", cargo " + (int) Math.max(0, cargo.hull) + "/" + (int) cargo.hullMax : "")
                + (mode == Mode.DUO ? ", Bleus " + teamKills[0] + " – " + teamKills[1] + " Ors" : "") + ".");
        for (Ship s : ranking()) {
            sb.append("\n    ").append(s.name).append(" (").append(s.type.label).append(") : ").append(s.kills).append(" élim., ").append(s.assists)
                    .append(" aides, ").append(s.deaths).append(" pertes, ").append(s.points).append(" pts");
        }
        plugin.getLogger().info(sb.toString());
    }

    /** Lancement immédiat demandé par un joueur pendant le décompte. */
    void hurry() {
        if (state == State.COUNTDOWN && countdown > 60) {
            countdown = 60;
        }
    }

    // ------------------------------------------------------------------ armes

    private static double raySphere(Vector3d o, Vector3d d, Vector3d c, double r) {
        double ox = c.x - o.x, oy = c.y - o.y, oz = c.z - o.z;
        double tca = ox * d.x + oy * d.y + oz * d.z;
        double d2 = ox * ox + oy * oy + oz * oz - tca * tca;
        if (d2 > r * r) {
            return -1;
        }
        double t = tca - Math.sqrt(r * r - d2);
        return t < 0 ? (tca + Math.sqrt(r * r - d2) >= 0 ? 0 : -1) : t;
    }

    private Vector3d aim(Ship s) {
        Vector3d f = s.fwd();
        if (s.human()) {
            Vector3d look = Ship.dir(s.wishYaw, s.wishPitch);
            return look.angle(f) < Math.toRadians(28) ? look : f;
        }
        Ship t = s.bot.target;
        if (t != null && t.alive) {
            Vector3d to = new Vector3d(t.center).sub(s.center);
            double dist = to.length();
            to.normalize();
            if (to.angle(f) < Math.toRadians(15)) {
                double spread = Math.tan(Math.toRadians(s.bot.level.spread * (s.bot.pirate ? 1.5 : 1.0))) * (t.fighter() ? 1 : 0.5);
                // une cible qui vire ou qui file est plus dure à toucher
                spread *= 1 + Math.min(1.5, t.vel.length());
                return to.add(rnd.nextGaussian() * spread, rnd.nextGaussian() * spread, rnd.nextGaussian() * spread).normalize();
            }
        }
        return f;
    }

    private void fire(Ship s) {
        s.fireCd = s.type.cadence;
        s.sinceFire = 0;
        if (s.type.heat > 0) {
            s.heat += s.type.heat;
            if (s.heat >= 100) {
                s.heat = 100;
                s.overheated = true;
                if (s.human()) {
                    s.player.playSound(s.player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.8f, 1.2f);
                    s.player.showTitle(Title.title(Component.empty(), Txt.mm("<#E0484A><b>♨ SURCHAUFFE</b>"),
                            Title.Times.times(Duration.ZERO, Duration.ofMillis(700), Duration.ofMillis(200))));
                }
            }
        }
        Vector3d d = aim(s);
        Vector3d o = new Vector3d(s.human() ? s.pos : s.center);
        double max = tuning.laserRange;
        double wall = arena.ray(o, d, max);
        double best = wall < 0 ? max : wall;
        Ship hit = null;
        for (Ship x : ships) {
            if (x == s || !x.alive || x.team == s.team) {
                continue;
            }
            double r = x.radius + (s.human() ? Math.min(1.8, x.center.distance(o) * tuning.assist) : 0);
            double t = raySphere(o, d, x.center, r);
            if (t >= 0 && t < best) {
                best = t;
                hit = x;
            }
        }
        Vector3d end = new Vector3d(d).mul(best).add(o);
        Vector3d muzzle = new Vector3d(s.fwd()).mul(s.radius * 0.8).add(s.center);
        beam(muzzle, end, s.rgb);
        Location at = new Location(arena.world, muzzle.x, muzzle.y, muzzle.z);
        for (Player p : players()) {
            if (p == s.player) {
                p.playSound(p.getLocation(), Sound.ENTITY_SHULKER_SHOOT, 0.22f, 1.9f);
            } else {
                p.playSound(at, Sound.ENTITY_SHULKER_SHOOT, 2.2f, s.fighter() ? 1.6f : 1.2f);
            }
        }
        testShots++;
        if (hit != null) {
            testHits++;
            particles(Particle.ELECTRIC_SPARK, end, 6, 0.3, 0.2, null);
            damage(hit, s, s.type.damage * (s.bot != null ? s.bot.level.damage : 1.0), "laser");
        } else if (wall >= 0) {
            particles(Particle.CRIT, end, 4, 0.2, 0.15, null);
        }
    }

    private void beam(Vector3d a, Vector3d b, int rgb) {
        Vector3d d = new Vector3d(b).sub(a);
        double len = d.length();
        if (len < 0.5) {
            return;
        }
        d.div(len);
        Vector3d mid = new Vector3d(a).add(b).mul(0.5);
        Quaternionf q = new Quaternionf().rotationTo(0f, 0f, 1f, (float) d.x, (float) d.y, (float) d.z);
        ItemDisplay e = arena.world.spawn(new Location(arena.world, mid.x, mid.y, mid.z), ItemDisplay.class, x -> {
            plugin.mark(x);
            x.setItemStack(plugin.model("laser", rgb));
            x.setBrightness(new Display.Brightness(15, 15));
            x.setViewRange(6f);
            x.setTransformation(new Transformation(new Vector3f(), q, new Vector3f(3f, 3f, (float) len), new Quaternionf()));
        });
        fx.add(new Fx(e, ticks + 3));
    }

    /** Cible dans le collimateur du vaisseau (angle en degrés, portée), en vue directe. */
    Ship lock(Ship s, double degrees, double range) {
        Vector3d look = s.human() ? Ship.dir(s.wishYaw, s.wishPitch) : s.fwd();
        Ship best = null;
        double bestAngle = Math.toRadians(degrees);
        for (Ship x : ships) {
            if (x == s || !x.alive || x.team == s.team) {
                continue;
            }
            Vector3d to = new Vector3d(x.center).sub(s.pos);
            double dist = to.length();
            if (dist > range || dist < 0.5) {
                continue;
            }
            double a = look.angle(to);
            if (a < bestAngle && arena.ray(s.pos, to.div(dist), dist) < 0) {
                bestAngle = a;
                best = x;
            }
        }
        return best;
    }

    private void launch(Ship s, Ship target) {
        s.missiles--;
        s.missileCd = 30;
        testMissiles++;
        Vector3d dir = s.human() ? Ship.dir(s.wishYaw, s.wishPitch) : s.fwd();
        Vector3d pos = new Vector3d(s.fwd()).mul(s.radius + 0.6).add(s.center);
        ItemDisplay e = arena.world.spawn(new Location(arena.world, pos.x, pos.y, pos.z), ItemDisplay.class, x -> {
            plugin.mark(x);
            x.setItemStack(plugin.model("missile", s.rgb));
            x.setBrightness(new Display.Brightness(15, 15));
            x.setViewRange(5f);
            x.setTeleportDuration(1);
            x.setInterpolationDuration(1);
            x.setTransformation(missileTransform(dir));
        });
        missiles.add(new Missile(s, target, pos, dir, e));
        Location at = new Location(arena.world, pos.x, pos.y, pos.z);
        for (Player p : players()) {
            p.playSound(p == s.player ? p.getLocation() : at, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, p == s.player ? 0.8f : 3f, 0.6f);
        }
        if (s.human()) {
            s.player.sendActionBar(Txt.mm(target != null ? "<#FF4FD8><b>➹ MISSILE</b></#FF4FD8> <#E6EDF3>verrouillé sur " + target.name
                    : "<#FF4FD8><b>➹ MISSILE</b></#FF4FD8> <#9AA4B2>tiré droit devant (aucune cible dans le viseur)"));
            updateInventory(s);
        }
        if (target != null && target.human()) {
            target.player.playSound(target.player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1.8f);
        }
    }

    private static Transformation missileTransform(Vector3d dir) {
        Quaternionf q = new Quaternionf().rotationTo(0f, 0f, 1f, (float) dir.x, (float) dir.y, (float) dir.z);
        return new Transformation(new Vector3f(), q, new Vector3f(2.4f, 2.4f, 2.4f), new Quaternionf());
    }

    /** Missile qui poursuit ce vaisseau, s'il y en a un. */
    Missile threat(Ship s) {
        for (Missile m : missiles) {
            if (m.target == s && m.pos.distance(s.center) < 45) {
                return m;
            }
        }
        return null;
    }

    private void missilesTick() {
        Iterator<Missile> it = missiles.iterator();
        while (it.hasNext()) {
            Missile m = it.next();
            boolean boom = --m.life <= 0;
            Ship struck = null;
            if (m.target != null && m.target.alive) {
                Vector3d want = new Vector3d(m.target.center).sub(m.pos).normalize();
                double ang = m.dir.angle(want), max = Math.toRadians(5.5);
                if (ang <= max || ang < 1e-4) {
                    m.dir.set(want);
                } else {
                    double k = max / ang;
                    m.dir.lerp(want, k).normalize();
                }
                if (m.target.human() && m.life % 5 == 0) {
                    double dist = m.pos.distance(m.target.center);
                    m.target.player.playSound(m.target.player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 0.9f, (float) Math.max(0.8, 2.0 - dist / 50));
                }
            }
            for (int step = 0; step < 3 && !boom; step++) {
                m.pos.add(m.dir.x * 0.5, m.dir.y * 0.5, m.dir.z * 0.5);
                if (arena.solid(m.pos.x, m.pos.y, m.pos.z) || arena.fromCentre(m.pos) > Arena.R + 2) {
                    boom = true;
                }
                for (Ship x : ships) {
                    if (x != m.owner && x.alive && x.team != m.owner.team && x.center.distance(m.pos) < x.radius + 0.9) {
                        struck = x;
                        boom = true;
                        break;
                    }
                }
            }
            if (boom) {
                particles(Particle.EXPLOSION, m.pos, 3, 0.6, 0, null);
                particles(Particle.FLAME, m.pos, 20, 0.3, 0.2, null);
                sound(m.pos, Sound.ENTITY_GENERIC_EXPLODE, 3f, 1.5f);
                for (Ship x : new ArrayList<>(ships)) {
                    if (x.alive && x.team != m.owner.team) {
                        double dist = x.center.distance(m.pos) - x.radius;
                        if (x == struck) {
                            damage(x, m.owner, tuning.missileDamage, "missile");
                        } else if (dist < 4) {
                            damage(x, m.owner, tuning.missileDamage * 0.4 * (1 - Math.max(0, dist) / 4), "missile");
                        }
                    }
                }
                m.entity.remove();
                it.remove();
                if (state != State.RUNNING) {
                    return;
                }
                continue;
            }
            m.entity.teleport(new Location(arena.world, m.pos.x, m.pos.y, m.pos.z), TeleportFlag.EntityState.RETAIN_PASSENGERS);
            m.entity.setInterpolationDelay(0);
            m.entity.setTransformation(missileTransform(m.dir));
            particles(Particle.FLAME, new Vector3d(m.dir).mul(-0.8).add(m.pos), 2, 0.05, 0.01, null);
            if (m.life % 2 == 0) {
                particles(Particle.CLOUD, new Vector3d(m.dir).mul(-1.2).add(m.pos), 1, 0.05, 0.01, null);
            }
        }
    }

    // ------------------------------------------------------------------ dégâts

    void crash(Ship s, double amount) {
        testCrashes++;
        if (s.human()) {
            s.player.playSound(s.player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.5f, 0.7f);
        }
        particles(Particle.CRIT, s.center, 10, 0.6, 0.3, null);
        damage(s, null, amount, "un astéroïde");
    }

    void damage(Ship victim, Ship attacker, double amount, String cause) {
        if (!victim.alive || state != State.RUNNING || victim.protect > 0 || amount <= 0) {
            return;
        }
        victim.sinceHit = 0;
        if (attacker != null) {
            victim.lastAttacker = attacker;
            victim.lastAttackTick = ticks;
            victim.damagers.put(attacker, ticks);
            if (attacker.human()) {
                attacker.player.playSound(attacker.player.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 0.35f, victim.shield > 0 ? 1.7f : 1.1f);
            }
        }
        double absorbed = Math.min(victim.shield, amount);
        victim.shield -= absorbed;
        victim.hull -= amount - absorbed;
        if (victim.human()) {
            victim.player.playSound(victim.player.getLocation(), absorbed >= amount ? Sound.ITEM_SHIELD_BLOCK : Sound.ENTITY_IRON_GOLEM_HURT, 0.6f, absorbed >= amount ? 1.5f : 0.8f);
        }
        if (victim == cargo && attacker != null && ticks % 40 == 0 && victim.hull < victim.hullMax * 0.5) {
            for (Player p : players()) {
                p.sendActionBar(Txt.mm("<#E0484A><b>⚠ LE CARGO EST ATTAQUÉ</b></#E0484A> <#E6EDF3>coque " + (int) (100 * victim.hull / victim.hullMax) + " %"));
            }
        }
        if (victim.hull <= 0) {
            Ship killer = attacker != null ? attacker : (victim.lastAttacker != null && ticks - victim.lastAttackTick < 120 ? victim.lastAttacker : null);
            kill(victim, killer, cause);
        }
    }

    private void kill(Ship victim, Ship killer, String cause) {
        victim.alive = false;
        victim.hull = 0;
        victim.deaths++;
        victim.vel.zero();
        Vector3d c = new Vector3d(victim.center);
        particles(Particle.EXPLOSION_EMITTER, c, victim.fighter() ? 1 : 3, victim.radius * 0.4, 0, null);
        particles(Particle.FLAME, c, 40, 0.5, 0.28, null);
        particles(Particle.FIREWORK, c, 30, 0.4, 0.3, null);
        particles(Particle.DUST, c, 30, victim.radius * 0.7, 0, new Particle.DustOptions(Color.fromRGB(victim.rgb), 2f));
        sound(c, Sound.ENTITY_GENERIC_EXPLODE, 5f, victim.fighter() ? 0.9f : 0.6f);
        victim.show(false);
        boolean pirate = mode == Mode.CONVOI && victim.team == 1;
        if (killer != null && killer != victim) {
            killer.kills++;
            killer.points += victim.type == ShipType.DRONE ? 25 : victim.type == ShipType.FREGATE ? 250 : 100;
            if (mode == Mode.DUO && killer.team >= 0 && killer.team < 2) {
                teamKills[killer.team]++;
            }
            if (killer.human()) {
                killer.player.playSound(killer.player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f, 0.7f);
                killer.player.showTitle(Title.title(Component.empty(), Txt.mm("<#E0B864><b>✦ " + victim.name + " éliminé</b>"),
                        Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(300))));
            }
        }
        for (Map.Entry<Ship, Integer> e : victim.damagers.entrySet()) {
            Ship a = e.getKey();
            if (a != killer && a != victim && ticks - e.getValue() < 200) {
                a.assists++;
                a.points += pirate ? 10 : 40;
            }
        }
        if (!pirate && victim != cargo) {
            String kc = killer != null ? "<" + Txt.hex(killer.rgb) + ">" + killer.name + "</" + Txt.hex(killer.rgb) + ">" : null;
            String vc = "<" + Txt.hex(victim.rgb) + ">" + victim.name + "</" + Txt.hex(victim.rgb) + ">";
            announce("<#5A6270>☠</#5A6270> " + (kc != null ? kc + " <#9AA4B2>a détruit</#9AA4B2> " + vc + " <#5A6270>(" + cause + ")"
                    : vc + " <#9AA4B2>s'est écrasé contre</#9AA4B2> <#5A6270>" + cause));
        } else if (victim.type == ShipType.FREGATE) {
            announce("<#E0B864><b>✦ Frégate pirate détruite !</b></#E0B864>" + (killer != null ? " <#9AA4B2>par " + killer.name : ""));
        }
        if (pirate) {
            gone.add(victim);
        } else if (victim != cargo) {
            victim.respawnIn = plugin.getConfig().getInt("partie.reapparition-secondes", 3) * 20 + (mode == Mode.CONVOI ? 40 : 0);
            if (victim.human()) {
                victim.player.showTitle(Title.title(Txt.mm("<#E0484A><b>✖ Vaisseau détruit</b>"),
                        Txt.mm("<#9AA4B2>" + (killer != null ? "par " + killer.name : cause) + " · retour dans " + victim.respawnIn / 20 + " s"),
                        Title.Times.times(Duration.ZERO, Duration.ofMillis(1600), Duration.ofMillis(300))));
            }
        }
        if (victim == cargo) {
            end();
        }
    }

    // ------------------------------------------------------------------ anneaux, bonus, traînées

    private void ringsAndBonus(Ship s) {
        if (s.ringCd == 0) {
            for (Arena.Ring r : arena.rings) {
                double d0 = (s.prev.x - r.c().x) * r.n().x + (s.prev.y - r.c().y) * r.n().y + (s.prev.z - r.c().z) * r.n().z;
                double d1 = (s.pos.x - r.c().x) * r.n().x + (s.pos.y - r.c().y) * r.n().y + (s.pos.z - r.c().z) * r.n().z;
                if (d0 * d1 < 0 && s.pos.distance(r.c()) < r.r() - 0.6) {
                    s.ringCd = 120;
                    s.boost = 100;
                    s.boostLock = false;
                    s.points += 10;
                    if (s.human()) {
                        s.player.playSound(s.player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 2f);
                        s.player.sendActionBar(Txt.mm("<#62CFF4><b>◯ Anneau traversé</b></#62CFF4> <#E6EDF3>boost rechargé · +10 pts"));
                    }
                    particles(Particle.END_ROD, s.pos, 20, 1.2, 0.1, null);
                    break;
                }
            }
        }
        for (Pickup p : pickups) {
            if (p.backAt <= ticks && p.pos.distance(s.center) < 3.0) {
                boolean used = switch (p.bonus) {
                    case REPARATION -> s.hull < s.hullMax;
                    case MISSILES -> s.missiles < s.type.missiles + 4;
                    case SURVOLTEUR -> true;
                };
                if (!used) {
                    continue;
                }
                switch (p.bonus) {
                    case REPARATION -> s.hull = Math.min(s.hullMax, s.hull + s.hullMax * 0.45);
                    case MISSILES -> s.missiles = Math.min(s.type.missiles + 4, s.missiles + 2);
                    case SURVOLTEUR -> {
                        s.surge = 200;
                        s.heat = 0;
                        s.overheated = false;
                        s.boost = 100;
                        s.boostLock = false;
                    }
                }
                p.backAt = ticks + 400;
                p.entity.setItemStack(null);
                if (s.human()) {
                    s.player.playSound(s.player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f, 1.4f);
                    s.player.sendActionBar(Txt.mm(p.bonus.text));
                    updateInventory(s);
                }
            }
        }
    }

    private void pickupsTick() {
        if (ticks % 20 != 0) {
            return;
        }
        for (Pickup p : pickups) {
            if (p.backAt != 0 && p.backAt <= ticks) {
                p.backAt = 0;
                p.entity.setItemStack(plugin.model(p.bonus.model, 0xFFFFFF));
            }
            p.spin += 2.0f;
            p.entity.setInterpolationDelay(0);
            p.entity.setInterpolationDuration(20);
            p.entity.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotationY(p.spin), new Vector3f(2.2f, 2.2f, 2.2f), new Quaternionf()));
        }
    }

    private void trail(Ship s) {
        Vector3d tail = new Vector3d(s.fwd()).mul(-s.radius * 0.9).add(s.center);
        if (s.boosting) {
            particles(Particle.END_ROD, tail, 2, 0.1, 0.02, null);
        } else {
            particles(Particle.DUST, tail, 1, 0.08, 0, new Particle.DustOptions(Color.fromRGB(s.rgb), s.fighter() ? 1.1f : 1.8f));
        }
        if (s.hull < s.hullMax * 0.3) {
            particles(Particle.LARGE_SMOKE, s.center, 1, 0.3, 0.01, null);
        }
    }

    private <T> void particles(Particle particle, Vector3d at, int count, double spread, double extra, T data) {
        List<Player> to = players();
        if (to.isEmpty()) {
            return;
        }
        new ParticleBuilder(particle).location(arena.world, at.x, at.y, at.z).count(count).offset(spread, spread, spread).extra(extra)
                .data(data).receivers(to).force(true).spawn();
    }

    private void sound(Vector3d at, Sound sound, float volume, float pitch) {
        Location l = new Location(arena.world, at.x, at.y, at.z);
        for (Player p : players()) {
            p.playSound(l, sound, volume, pitch);
        }
    }

    void announce(String message) {
        Component c = Txt.mm(message);
        for (Player p : players()) {
            p.sendMessage(c);
        }
    }

    private void cleanFx() {
        Iterator<Fx> it = fx.iterator();
        while (it.hasNext()) {
            Fx f = it.next();
            if (f.until <= ticks) {
                f.entity.remove();
                it.remove();
            }
        }
    }

    // ------------------------------------------------------------------ convoi

    private void moveCargo() {
        Vector3d p = arena.path(cargoT), q = arena.path(cargoT + 0.01);
        Vector3d d = q.sub(p).normalize();
        cargo.prev.set(cargo.pos);
        cargo.pos.set(p);
        cargo.center.set(p);
        cargo.vel.set(d).mul(0.0022 * 48);
        cargo.yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        cargo.pitch = (float) Math.toDegrees(-Math.asin(d.y));
        cargo.push();
    }

    private void convoi() {
        if (cargo.alive) {
            cargoT += 0.0022;
            cargo.sinceHit++;
            cargo.age++;
            if (cargo.protect > 0) {
                cargo.protect--;
            }
            if (cargo.sinceHit > 160 && cargo.shield < cargo.type.shield) {
                cargo.shield = Math.min(cargo.type.shield, cargo.shield + 1);
            }
            moveCargo();
            if (ticks % 5 == 0) {
                cargo.refreshTag();
            }
            if (ticks % 3 == 0) {
                particles(Particle.DUST, new Vector3d(cargo.fwd()).mul(-5).add(cargo.pos), 2, 0.5, 0, new Particle.DustOptions(Color.fromRGB(0x62CFF4), 2.2f));
            }
        }
        int pirates = 0;
        for (Ship s : ships) {
            if (s.team == 1 && s.alive) {
                pirates++;
            }
        }
        if (waveBreak > 0) {
            if (--waveBreak == 0) {
                wave++;
                toSpawn = Math.min(16, 2 + wave + byPlayer.size());
                fregates = wave % 4 == 0 ? 1 + wave / 12 : 0;
                spawnCd = 0;
                for (Player p : players()) {
                    p.showTitle(Title.title(Txt.mm("<#E0484A><b>VAGUE " + wave + "</b>"),
                            Txt.mm(fregates > 0 ? "<#E0B864>Une frégate pirate approche !" : "<#9AA4B2>" + toSpawn + " écumeurs en approche · protège le cargo"),
                            Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1600), Duration.ofMillis(300))));
                    p.playSound(p.getLocation(), fregates > 0 ? Sound.ENTITY_WITHER_SPAWN : Sound.BLOCK_BEACON_DEACTIVATE, 0.6f, fregates > 0 ? 1.3f : 0.7f);
                }
            }
            return;
        }
        if ((toSpawn > 0 || fregates > 0) && --spawnCd <= 0 && pirates < 5 + byPlayer.size()) {
            spawnCd = Math.max(14, 34 - wave);
            if (fregates > 0) {
                fregates--;
                addBot(1, ShipType.FREGATE);
            } else {
                toSpawn--;
                addBot(1, ShipType.DRONE);
            }
        } else if (toSpawn == 0 && fregates == 0 && pirates == 0) {
            int bonus = 50 + 25 * wave;
            for (Ship s : humans()) {
                s.points += bonus;
                s.missiles = Math.min(s.type.missiles + 4, s.missiles + 1);
                s.player.playSound(s.player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.3f);
                updateInventory(s);
            }
            cargo.shield = cargo.type.shield;
            cargo.hull = Math.min(cargo.hullMax, cargo.hull + cargo.hullMax * 0.06);
            announce("<#7CFF8A><b>✔ Vague " + wave + " repoussée</b></#7CFF8A> <#9AA4B2>· +" + bonus + " pts · +1 missile · bouclier du cargo rechargé");
            waveBreak = 120;
        }
    }

    // ------------------------------------------------------------------ affichage

    private void hud() {
        boolean slow = ticks % 4 == 0;
        for (Ship s : ships) {
            if (!s.human() || !s.player.isOnline()) {
                continue;
            }
            Player p = s.player;
            if (state == State.RUNNING && s.alive && s.outside == 0) {
                Ship lock = lock(s, 6, 90);
                double h = s.hull / s.hullMax;
                StringBuilder sb = new StringBuilder();
                sb.append("<#62CFF4>⛨</#62CFF4> ").append(Txt.bar(s.shield / s.type.shield, 6, "#62CFF4"))
                        .append("  <#7CFF8A>✚</#7CFF8A> ").append(Txt.bar(h, 8, h > 0.5 ? "#7CFF8A" : h > 0.25 ? "#FFB000" : "#E0484A"))
                        .append("  <#FFB000>♨</#FFB000> ").append(s.overheated ? "<#E0484A><b>SURCHAUFFE</b></#E0484A>" : Txt.bar(s.heat / 100, 6, s.heat > 70 ? "#E0484A" : "#FFB000"))
                        .append("  <#E0B864>⚡</#E0B864> ").append(Txt.bar(s.boost / 100, 5, s.boostLock ? "#5A6270" : "#E0B864"))
                        .append("  <#E6EDF3>➤ ").append((int) Math.round(s.vel.length() * 20)).append(" m/s</#E6EDF3>")
                        .append("  <#FF4FD8>➹ ").append(s.missiles).append("</#FF4FD8>");
                if (lock != null) {
                    sb.append("  <#E0484A><b>◈ ").append(lock.name).append("</b> ").append((int) lock.center.distance(s.pos)).append(" m</#E0484A>");
                }
                if (s.protect > 0) {
                    sb.append("  <#7CFF8A>invulnérable</#7CFF8A>");
                }
                p.sendActionBar(Txt.mm(sb.toString()));
            }
            if (!slow) {
                continue;
            }
            BossBar[] b = bars.get(p.getUniqueId());
            if (b == null) {
                continue;
            }
            b[0].name(Txt.mm(status(s)));
            b[0].progress(mode == Mode.CONVOI ? (float) Math.max(0, Math.min(1, cargo.hull / cargo.hullMax))
                    : (float) Math.max(0, Math.min(1, timeLeft / (double) (plugin.getConfig().getInt("partie.duree-secondes", 240) * 20))));
            b[0].color(mode == Mode.CONVOI ? (cargo.hull < cargo.hullMax * 0.3 ? BossBar.Color.RED : BossBar.Color.YELLOW) : BossBar.Color.BLUE);
            b[1].name(Txt.mm(radar(s)));
            double h = s.alive ? s.hull / s.hullMax : 0;
            b[1].progress((float) Math.max(0, Math.min(1, h)));
            b[1].color(h > 0.5 ? BossBar.Color.GREEN : h > 0.25 ? BossBar.Color.YELLOW : BossBar.Color.RED);
        }
    }

    private String status(Ship s) {
        String head = "<" + mode.color + "><b>" + mode.label.toUpperCase() + "</b></" + mode.color + "> <#5A6270>·</#5A6270> ";
        if (state == State.COUNTDOWN) {
            return head + "<#E6EDF3>départ dans " + (countdown / 20 + 1) + " s";
        }
        switch (mode) {
            case MELEE: {
                Ship first = ranking().get(0);
                return head + "<#E6EDF3>" + Txt.time(timeLeft) + "</#E6EDF3> <#5A6270>·</#5A6270> <#E0B864>toi " + s.kills + " ✦</#E0B864> <#5A6270>·</#5A6270> <#9AA4B2>1er "
                        + first.name + " " + first.kills + "/" + plugin.getConfig().getInt("partie.melee-eliminations", 15);
            }
            case DUO:
                return head + "<#E6EDF3>" + Txt.time(timeLeft) + "</#E6EDF3> <#5A6270>·</#5A6270> <#3A7FFF><b>Bleus " + teamKills[0] + "</b></#3A7FFF> <#5A6270>–</#5A6270> <#E0B864><b>"
                        + teamKills[1] + " Ors</b></#E0B864> <#9AA4B2>(" + plugin.getConfig().getInt("partie.duo-eliminations", 12) + ")";
            default:
                return head + "<#E0484A>vague " + Math.max(1, wave) + "</#E0484A> <#5A6270>·</#5A6270> <#E0B864>cargo " + (int) Math.ceil(100 * cargo.hull / cargo.hullMax)
                        + " %</#E0B864> <#5A6270>·</#5A6270> <#E6EDF3>" + Txt.n(s.points) + " pts";
        }
    }

    /** Radar : bande de 33 cases autour du regard (case du milieu = droit devant) ; ▲ ▼ = plus haut / plus bas. */
    private String radar(Ship s) {
        int cells = 33;
        char[] ch = new char[cells];
        String[] col = new String[cells];
        double[] near = new double[cells];
        java.util.Arrays.fill(ch, '·');
        java.util.Arrays.fill(near, 1e9);
        float viewYaw = s.wishYaw, viewPitch = s.wishPitch;
        for (Ship o : ships) {
            if (o == s || !o.alive) {
                continue;
            }
            plot(s, o.center, viewYaw, viewPitch, o.team == s.team ? (o == cargo ? "#E0B864" : "#7CFF8A") : null, o == cargo ? '■' : 0, ch, col, near);
        }
        for (Missile m : missiles) {
            if (m.target == s) {
                plot(s, m.pos, viewYaw, viewPitch, "#FF4FD8", '!', ch, col, near);
            }
        }
        StringBuilder sb = new StringBuilder("<#5A6270>◀ </#5A6270>");
        for (int i = 0; i < cells; i++) {
            if (i == cells / 2 - 1) {
                sb.append("<#E6EDF3>[</#E6EDF3>");
            }
            String c = col[i] == null ? "#3A4250" : col[i];
            sb.append('<').append(c).append('>').append(ch[i]).append("</").append(c).append('>');
            if (i == cells / 2 + 1) {
                sb.append("<#E6EDF3>]</#E6EDF3>");
            }
        }
        return sb.append("<#5A6270> ▶</#5A6270>").toString();
    }

    private void plot(Ship s, Vector3d at, float viewYaw, float viewPitch, String friendColor, char forced, char[] ch, String[] col, double[] near) {
        double dx = at.x - s.pos.x, dy = at.y - s.pos.y, dz = at.z - s.pos.z;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist < 0.5 || dist > 170) {
            return;
        }
        float bearing = Ship.wrap((float) Math.toDegrees(Math.atan2(-dx, dz)) - viewYaw);
        int cell = Math.max(0, Math.min(ch.length - 1, Math.round(bearing / (360f / ch.length)) + ch.length / 2));
        if (dist >= near[cell]) {
            return;
        }
        near[cell] = dist;
        double elev = Math.toDegrees(Math.asin(dy / dist)) + viewPitch;
        ch[cell] = forced != 0 ? forced : elev > 12 ? '▲' : elev < -12 ? '▼' : '◆';
        col[cell] = friendColor != null ? friendColor : dist < 30 ? "#FF5A5C" : dist < 70 ? "#E0484A" : "#8A3A3C";
    }

    // ------------------------------------------------------------------ fin de partie

    List<Ship> ranking() {
        List<Ship> list = new ArrayList<>();
        for (Ship s : ships) {
            if (s != cargo && !(mode == Mode.CONVOI && s.team == 1)) {
                list.add(s);
            }
        }
        list.sort(Comparator.<Ship>comparingInt(s -> mode == Mode.CONVOI ? -s.points : -s.kills).thenComparingInt(s -> s.deaths).thenComparingInt(s -> -s.points));
        return list;
    }

    private void checkEnd() {
        switch (mode) {
            case MELEE -> {
                int goal = plugin.getConfig().getInt("partie.melee-eliminations", 15);
                for (Ship s : ships) {
                    if (s.kills >= goal) {
                        end();
                        return;
                    }
                }
                if (timeLeft <= 0) {
                    end();
                }
            }
            case DUO -> {
                int goal = plugin.getConfig().getInt("partie.duo-eliminations", 12);
                if (teamKills[0] >= goal || teamKills[1] >= goal || timeLeft <= 0) {
                    end();
                }
            }
            default -> {
            }
        }
    }

    boolean won(Ship s) {
        return switch (mode) {
            case MELEE -> winner == s;
            case DUO -> winningTeam == s.team;
            case CONVOI -> false;
        };
    }

    /** Points d'un joueur : ce qu'il a gagné en vol, la prime de victoire, le tout pondéré par le niveau des bots. */
    int score(Ship s, boolean win) {
        return (int) Math.round((s.points + (win ? 300 : 0)) * level.points);
    }

    private void end() {
        if (state != State.RUNNING) {
            return;
        }
        state = State.ENDING;
        endTicks = 130;
        if (testEnd > 0) {
            testReport(mode == Mode.CONVOI ? "cargo détruit" : "objectif atteint ou temps écoulé");
            testEnd = 0;
        }
        for (Missile m : missiles) {
            m.entity.remove();
        }
        missiles.clear();
        List<Ship> rank = ranking();
        switch (mode) {
            case MELEE -> winner = rank.isEmpty() ? null : rank.get(0);
            case DUO -> winningTeam = teamKills[0] == teamKills[1] ? -1 : teamKills[0] > teamKills[1] ? 0 : 1;
            default -> {
            }
        }
        for (Ship s : humans()) {
            Player p = s.player;
            boolean win = won(s);
            String title, sub;
            if (mode == Mode.CONVOI) {
                title = "<#E0484A><b>CARGO DÉTRUIT</b>";
                sub = "<#E6EDF3>vague " + wave + " · " + Txt.n(score(s, false)) + " pts";
            } else if (win) {
                title = "<gradient:#E0B864:#FFE08A><b>VICTOIRE</b></gradient>";
                sub = "<#E6EDF3>" + s.kills + " éliminations · " + Txt.n(score(s, true)) + " pts";
            } else if (mode == Mode.DUO && winningTeam < 0) {
                title = "<#9AA4B2><b>ÉGALITÉ</b>";
                sub = "<#E6EDF3>" + teamKills[0] + " – " + teamKills[1];
            } else {
                title = "<#E0484A><b>DÉFAITE</b>";
                sub = "<#E6EDF3>" + (mode == Mode.MELEE && winner != null ? "vainqueur : " + winner.name + " · " : "") + Txt.n(score(s, false)) + " pts";
            }
            p.showTitle(Title.title(Txt.mm(title), Txt.mm(sub), Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(4500), Duration.ofMillis(800))));
            p.playSound(p.getLocation(), win ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 1f);
            p.sendMessage(Component.empty());
            p.sendMessage(Txt.mm(Txt.TITLE + " <#5A6270>·</#5A6270> <" + mode.color + ">" + mode.label + "</" + mode.color + "> <#5A6270>·</#5A6270> " + arena(mode)
                    + (mode == Mode.CONVOI ? " <#5A6270>·</#5A6270> <#E0484A>vague " + wave : "")));
            int i = 1;
            for (Ship r : rank) {
                String color = r == s ? "#E6EDF3" : "#9AA4B2";
                String team = mode == Mode.DUO ? (r.team == 0 ? "<#3A7FFF>■</#3A7FFF> " : "<#E0B864>■</#E0B864> ") : "";
                p.sendMessage(Txt.mm("  <#5A6270>" + i++ + ".</#5A6270> " + team + "<" + color + ">" + (r == s ? "<b>" : "") + r.name + (r == s ? "</b>" : "")
                        + (r.human() ? "" : " <#5A6270>(bot)</#5A6270>") + "</" + color + "> <#E0B864>" + r.kills + " ✦</#E0B864> <#5A6270>·</#5A6270> <#9AA4B2>"
                        + r.assists + " aides · " + r.deaths + " pertes</#9AA4B2> <#5A6270>·</#5A6270> <#E6EDF3>" + Txt.n(score(r, won(r))) + " pts"));
            }
            games.reward(this, s, win, true);
        }
    }

    /** Fin du temps des résultats : tout le monde rentre à la salle. */
    private void finish() {
        for (Ship s : humans()) {
            games.leave(s.player, false);
        }
        if (state != State.DONE) {
            dispose();
        }
    }

    /** Retire tout ce que la partie a posé. Les joueurs encore là sont renvoyés à la salle. */
    void dispose() {
        if (state == State.DONE) {
            return;
        }
        state = State.DONE;
        for (Ship s : new ArrayList<>(humans())) {
            games.leave(s.player, false);
        }
        for (Ship s : ships) {
            s.remove();
        }
        ships.clear();
        for (Missile m : missiles) {
            m.entity.remove();
        }
        missiles.clear();
        for (Pickup p : pickups) {
            p.entity.remove();
        }
        pickups.clear();
        for (Fx f : fx) {
            f.entity.remove();
        }
        fx.clear();
        arena.hold(plugin, false);
        games.closed(this);
    }
}
