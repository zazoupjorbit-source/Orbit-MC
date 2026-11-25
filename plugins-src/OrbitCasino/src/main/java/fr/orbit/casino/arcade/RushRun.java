package fr.orbit.casino.arcade;

import io.papermc.paper.entity.TeleportFlag;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Une course d'ORBIT RUSH. Tout est simulé par le serveur : le joueur est assis sur un support invisible
 * (ItemDisplay) que l'on déplace à chaque tick, et les collisions se calculent sur le modèle des obstacles
 * (pas sur les blocs, qui ne servent qu'au décor). Commandes : Q/D (A/D) = changer de voie, Espace ou Z = sauter,
 * Maj ou S = glisser.
 * <p>Repère : la piste avance vers +z ; les 3 voies sont à x = X0-3, X0, X0+3 ; on court à y = Y0 (sol en Y0-1).</p>
 */
final class RushRun {

    enum State { COUNTDOWN, RUNNING, DONE }

    enum Kind { BARRIER, BAR, SHUTTLE, RAMP }

    enum Power {
        AIMANT("🧲 Aimant", "#62CFF4", Material.LODESTONE, 200),
        BOUCLIER("🛡 Bouclier", "#7CFF8A", Material.SHIELD, 400),
        X2("✦ Pièces ×2", "#E0B864", Material.NETHER_STAR, 300),
        PROPULSEUR("🚀 Propulseur", "#FF4FD8", Material.FIREWORK_ROCKET, 120),
        SUPER_SAUT("⇮ Super saut", "#C79BFF", Material.SLIME_BALL, 240);

        final String label, color;
        final Material item;
        final int ticks;

        Power(String label, String color, Material item, int ticks) {
            this.label = label;
            this.color = color;
            this.item = item;
            this.ticks = ticks;
        }
    }

    /** Obstacle : une voie, une tranche de z, une hauteur. La rampe monte de 0 à top sur sa longueur. */
    static final class Box {
        final Kind kind;
        final int lane;
        final double z1, z2, bottom, top;
        boolean broken;

        Box(Kind kind, int lane, double z1, double z2, double bottom, double top) {
            this.kind = kind;
            this.lane = lane;
            this.z1 = z1;
            this.z2 = z2;
            this.bottom = bottom;
            this.top = top;
        }

        boolean standable() {
            return kind != Kind.BAR;
        }

        double topAt(double z) {
            return kind == Kind.RAMP ? top * Math.max(0, Math.min(1, (z - z1) / (z2 - z1))) : top;
        }
    }

    static final class Pickup {
        final double x, y, z;
        final Power power; // null = pièce
        final ItemDisplay entity;

        Pickup(double x, double y, double z, Power power, ItemDisplay entity) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.power = power;
            this.entity = entity;
        }
    }

    static final double GRAVITY = 0.09, JUMP = 0.5, SUPER_JUMP = 0.8, STAND_H = 1.8, SLIDE_H = 0.9, STEP = 0.6;
    static final int AHEAD = 90, SLICE_W = 7, SLICE_TOP = 8;

    private final Runner runner;
    final Player player;
    final int slot;
    final int x0, y0;
    private final World world;
    private final SplittableRandom rnd = new SplittableRandom();
    private final NamespacedKey key;

    State state = State.COUNTDOWN;
    private int countdown = 60;
    private ItemDisplay seat;

    // physique
    private int lane = 1;
    private double x, y, z, vy;
    private boolean onGround = true;
    private int slide, jumpBuffer, invulnerable, stumbleTicks;
    private Input last;

    // partie
    double distance;
    int coins;
    private int coinValue = 1;
    final Map<Power, Integer> active = new EnumMap<>(Power.class);
    private int ticks;

    // monde
    private final List<Box> boxes = new ArrayList<>();
    private final List<Pickup> pickups = new ArrayList<>();
    private final int[] busyUntil = new int[3];
    private int builtZ = -8, clearedZ = -8, genZ = 34, nextPower = 140;

    // état du joueur à rendre
    private final boolean hadFlight;
    private final double baseScale;

    RushRun(Runner runner, Player player, int slot, World world, NamespacedKey key) {
        this.runner = runner;
        this.player = player;
        this.slot = slot;
        this.world = world;
        this.key = key;
        this.x0 = Runner.X_BASE + slot * Runner.X_STEP;
        this.y0 = Runner.Y0;
        this.hadFlight = player.getAllowFlight();
        AttributeInstance sc = player.getAttribute(Attribute.SCALE);
        this.baseScale = sc == null ? 1.0 : sc.getBaseValue();
        this.x = laneX(1);
        this.z = 0.5;
    }

    double laneX(int l) {
        return x0 + (l - 1) * 3 + 0.5;
    }

    // ------------------------------------------------------------------ départ

    void begin() {
        generateAhead();
        while (builtZ < AHEAD) {
            buildSlice(builtZ++);
        }
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setFallDistance(0);
        Location start = new Location(world, x, y0, z, 0f, 8f);
        player.teleport(start);
        seat = world.spawn(start.clone().add(0, runner.seatOffset(), 0), ItemDisplay.class, e -> {
            e.setPersistent(false);
            e.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
            e.setTeleportDuration(runner.smoothing());
        });
        Bukkit.getScheduler().runTaskLater(runner.plugin(), () -> {
            if (state != State.DONE && seat.isValid()) {
                seat.addPassenger(player);
            }
        }, 2L);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.4f);
    }

    // ------------------------------------------------------------------ commandes

    void input(Input in) {
        Input prev = last;
        last = in;
        if (state != State.RUNNING) {
            return;
        }
        boolean left = in.isLeft() && (prev == null || !prev.isLeft());
        boolean right = in.isRight() && (prev == null || !prev.isRight());
        boolean jump = (in.isJump() && (prev == null || !prev.isJump())) || (in.isForward() && (prev == null || !prev.isForward()));
        boolean duck = (in.isSneak() && (prev == null || !prev.isSneak())) || (in.isBackward() && (prev == null || !prev.isBackward()));
        // la gauche du joueur dépend de là où il regarde : face à la piste (+z), sa gauche est +x
        int side = Math.cos(Math.toRadians(player.getLocation().getYaw())) >= 0 ? 1 : -1;
        if (left) {
            changeLane(side);
        }
        if (right) {
            changeLane(-side);
        }
        if (jump) {
            jumpBuffer = 5;
        }
        if (duck) {
            slide = 16;
            if (!onGround && !active.containsKey(Power.PROPULSEUR)) {
                vy = Math.min(vy, -0.7);
            }
            player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_ELYTRA, 0.6f, 1.6f);
        }
    }

    private void changeLane(int dir) {
        int target = lane + dir;
        if (target < 0 || target > 2) {
            bonk();
            return;
        }
        double h = slide > 0 ? SLIDE_H : STAND_H;
        for (Box b : boxes) {
            if (b.lane == target && !b.broken && b.kind != Kind.RAMP && z >= b.z1 - 0.3 && z < b.z2
                    && y < b.top - (b.standable() ? STEP : 0) && y + h > b.bottom) {
                bonk();
                return;
            }
        }
        lane = target;
        player.playSound(player.getLocation(), Sound.ENTITY_BREEZE_JUMP, 0.35f, 1.8f);
    }

    private void bonk() {
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 0.8f, 0.7f);
        if (stumbleTicks > 0 && invulnerable == 0 && !active.containsKey(Power.BOUCLIER)) {
            runner.finish(this, "Deux faux pas de suite");
            return;
        }
        stumbleTicks = 60;
        player.sendActionBar(fr.orbit.casino.Txt.mm("<#FFB000><b>Attention !</b> Un faux pas de plus et c'est la chute."));
    }

    // ------------------------------------------------------------------ tick

    double speed() {
        return Math.min(runner.maxSpeed(), runner.startSpeed() + distance * runner.acceleration());
    }

    void tick() {
        ticks++;
        if (state == State.COUNTDOWN) {
            if (countdown % 20 == 0) {
                int s = countdown / 20;
                if (s > 0) {
                    player.showTitle(Title.title(fr.orbit.casino.Txt.mm("<gradient:#FF4FD8:#62CFF4><b>" + s + "</b></gradient>"),
                            fr.orbit.casino.Txt.mm(s == 3 ? "<#9AA4B2>Q/D voies · Espace sauter · Maj glisser" : ""),
                            Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(100))));
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.9f, 1.0f);
                }
            }
            if (--countdown <= 0) {
                state = State.RUNNING;
                player.showTitle(Title.title(fr.orbit.casino.Txt.mm("<#7CFF8A><b>GO !</b>"), net.kyori.adventure.text.Component.empty(),
                        Title.Times.times(Duration.ZERO, Duration.ofMillis(600), Duration.ofMillis(200))));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
            }
            keepSeated();
            return;
        }
        if (state != State.RUNNING) {
            return;
        }
        keepSeated();
        if (seat == null || !seat.isValid() || !player.isOnline()) {
            runner.finish(this, null);
            return;
        }
        // minuteurs
        active.replaceAll((p, t) -> t - 1);
        active.entrySet().removeIf(e -> {
            if (e.getValue() <= 0) {
                player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.6f, 1.5f);
                return true;
            }
            return false;
        });
        coinValue = active.containsKey(Power.X2) ? 2 : 1;
        if (invulnerable > 0) {
            invulnerable--;
        }
        if (stumbleTicks > 0) {
            stumbleTicks--;
        }
        if (jumpBuffer > 0) {
            jumpBuffer--;
        }
        boolean wasSliding = slide > 0;
        if (slide > 0) {
            slide--;
        }
        setScale(slide > 0 ? 0.5 : baseScale);
        if (wasSliding && slide == 0) {
            setScale(baseScale);
        }

        double v = speed();
        double zPrev = z;
        z += v;
        distance += v;
        // latéral : glisse vers le centre de la voie
        double tx = laneX(lane);
        x += Math.max(-0.6, Math.min(0.6, tx - x));
        // vertical
        boolean jet = active.containsKey(Power.PROPULSEUR);
        if (jet) {
            y += (5.5 - y) * 0.15;
            vy = 0;
            onGround = false;
            if (ticks % 2 == 0) {
                world.spawnParticle(Particle.FLAME, x, y0 + y - 0.2, z - 0.6, 3, 0.15, 0.05, 0.15, 0.01);
            }
        } else {
            if (jumpBuffer > 0 && onGround) {
                vy = active.containsKey(Power.SUPER_SAUT) ? SUPER_JUMP : JUMP;
                onGround = false;
                jumpBuffer = 0;
                slide = 0;
                setScale(baseScale);
                player.playSound(player.getLocation(), Sound.ENTITY_GOAT_LONG_JUMP, 0.7f, 1.3f);
            }
            vy -= GRAVITY;
            y += vy;
        }
        double h = slide > 0 ? SLIDE_H : STAND_H;
        int cl = currentLane();
        // collisions (balayage entre zPrev et z pour ne rien sauter à grande vitesse)
        if (!jet) {
            for (Box b : boxes) {
                if (b.lane != cl || b.broken || b.kind == Kind.RAMP || b.z2 <= zPrev || b.z1 > z) {
                    continue;
                }
                double pass = b.standable() ? b.top - STEP : b.top;
                if (y < pass && y + h > b.bottom && !(b.standable() && vy <= 0 && y >= b.top - STEP)) {
                    if (invulnerable > 0) {
                        continue;
                    }
                    if (active.remove(Power.BOUCLIER) != null) {
                        smash(b);
                        invulnerable = 30;
                        player.playSound(player.getLocation(), Sound.ITEM_SHIELD_BREAK, 1f, 1f);
                        continue;
                    }
                    runner.finish(this, b.kind == Kind.BAR ? "Portique pris en pleine tête" : b.kind == Kind.SHUTTLE
                            ? "Percuté une navette" : "Trébuché sur une barrière");
                    return;
                }
            }
        }
        // sol : piste, dessus des navettes et barrières, rampes
        double ground = 0;
        for (Box b : boxes) {
            if (b.lane != cl || b.broken || b.kind == Kind.BAR || b.z1 > z || b.z2 <= z) {
                continue;
            }
            double t = b.topAt(z);
            // on ne se pose sur un dessus qu'en descendant (un saut qui frôle une barrière ne s'y colle pas)
            if (b.kind == Kind.RAMP || (y >= t - STEP && vy <= 0)) {
                ground = Math.max(ground, t);
            }
        }
        if (!jet && y <= ground) {
            if (!onGround && vy < -0.3) {
                player.playSound(player.getLocation(), Sound.BLOCK_WOOL_FALL, 0.6f, 1.2f);
            }
            y = ground;
            vy = 0;
            onGround = true;
        } else if (!jet && y > ground + 0.05) {
            onGround = false;
        }
        if (jet && y < ground) {
            y = ground;
        }
        // déplacement du support (le joueur suit, lissé côté client)
        seat.teleport(new Location(world, x, y0 + y + runner.seatOffset(), z, 0f, 0f), TeleportFlag.EntityState.RETAIN_PASSENGERS);
        collect(zPrev);
        stream();
        if (ticks % 2 == 0) {
            hud(v);
        }
    }

    private int currentLane() {
        double rel = (x - x0 - 0.5) / 3.0 + 1;
        return (int) Math.max(0, Math.min(2, Math.round(rel)));
    }

    private void keepSeated() {
        if (seat != null && seat.isValid() && player.isOnline() && player.getVehicle() == null && ticks > 4) {
            seat.addPassenger(player);
        }
    }

    private void setScale(double s) {
        AttributeInstance a = player.getAttribute(Attribute.SCALE);
        if (a != null && a.getBaseValue() != s) {
            a.setBaseValue(s);
        }
    }

    private void hud(double v) {
        StringBuilder sb = new StringBuilder("<#E0B864>⬢ ").append(coins).append("</#E0B864>  <#5A6270>·</#5A6270>  <#E6EDF3>")
                .append(score()).append(" pts</#E6EDF3>  <#5A6270>·</#5A6270>  <#62CFF4>").append(Math.round(v * 20)).append(" m/s</#62CFF4>");
        for (Map.Entry<Power, Integer> e : active.entrySet()) {
            sb.append("  <").append(e.getKey().color).append(">").append(e.getKey().label).append(" ")
                    .append((e.getValue() + 19) / 20).append("s</").append(e.getKey().color).append(">");
        }
        player.sendActionBar(fr.orbit.casino.Txt.mm(sb.toString()));
    }

    int score() {
        return (int) distance + coins * 5;
    }

    // ------------------------------------------------------------------ ramassage

    private void collect(double zPrev) {
        boolean magnet = active.containsKey(Power.AIMANT);
        double py = y0 + y + 0.9;
        Iterator<Pickup> it = pickups.iterator();
        while (it.hasNext()) {
            Pickup p = it.next();
            if (p.z < zPrev - 3) {
                p.entity.remove();
                it.remove();
                continue;
            }
            boolean hit;
            if (magnet && p.power == null) {
                hit = Math.abs(p.z - z) < 4.5 && Math.abs(p.x - x) < 7 && Math.abs(p.y - py) < 5;
            } else {
                hit = p.z >= zPrev - 0.6 && p.z <= z + 0.8 && Math.abs(p.x - x) < 1.4 && Math.abs(p.y - py) < 1.5;
            }
            if (!hit) {
                continue;
            }
            p.entity.remove();
            it.remove();
            if (p.power == null) {
                coins += coinValue;
                if (coins % 10 == 0 || coinValue > 1) {
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.4f, 1.6f + (float) rnd.nextDouble(0.3));
                } else {
                    player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 1.8f);
                }
            } else {
                active.put(p.power, p.power.ticks);
                player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.8f);
                player.showTitle(Title.title(net.kyori.adventure.text.Component.empty(),
                        fr.orbit.casino.Txt.mm("<" + p.power.color + "><b>" + p.power.label + "</b>"),
                        Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(300))));
                if (p.power == Power.PROPULSEUR) {
                    // ligne de pièces en altitude pendant le vol
                    double span = speed() * p.power.ticks;
                    for (double dz = 12; dz < span; dz += 3) {
                        coin(laneX((int) ((dz / 15) % 3)), 6.3, z + dz);
                    }
                }
            }
        }
    }

    private ItemDisplay display(double px, double py, double pz, ItemStack item, float scale, Color glow) {
        return world.spawn(new Location(world, px, y0 + py, pz), ItemDisplay.class, e -> {
            e.setPersistent(false);
            e.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
            e.setItemStack(item);
            e.setBillboard(Display.Billboard.VERTICAL);
            e.setBrightness(new Display.Brightness(15, 15));
            e.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(scale, scale, scale), new AxisAngle4f()));
            if (glow != null) {
                e.setGlowing(true);
                e.setGlowColorOverride(glow);
            }
        });
    }

    private void coin(double px, double py, double pz) {
        ItemStack item = new ItemStack(Material.GOLD_NUGGET);
        NamespacedKey model = runner.coinModel();
        if (model != null) {
            ItemMeta meta = item.getItemMeta();
            meta.setItemModel(model);
            item.setItemMeta(meta);
        }
        pickups.add(new Pickup(px, y0 + py, pz, null, display(px, py, pz, item, 0.6f, null)));
    }

    private void power(double px, double pz) {
        Power[] all = Power.values();
        Power pw = all[rnd.nextInt(all.length)];
        Color c = Color.fromRGB(Integer.parseInt(pw.color.substring(1), 16));
        pickups.add(new Pickup(px, y0 + 1.0, pz, pw, display(px, 1.0, pz, new ItemStack(pw.item), 0.9f, c)));
    }

    // ------------------------------------------------------------------ piste

    /** Génère des rangées d'obstacles d'avance, construit les tranches devant et efface celles de derrière. */
    private void generateAhead() {
        while (genZ < z + AHEAD + 40) {
            generateRow(genZ);
            int gap = 18 - (int) Math.min(6, distance / 600);
            genZ += gap + rnd.nextInt(5);
        }
    }

    private void stream() {
        generateAhead();
        int built = 0;
        while (builtZ < z + AHEAD && built++ < 4) {
            buildSlice(builtZ++);
        }
        while (clearedZ < z - 14) {
            clearSlice(clearedZ++);
        }
        boxes.removeIf(b -> b.z2 < z - 16);
    }

    private static final int EMPTY = 0, T_BARRIER = 1, T_BAR = 2, T_SHUTTLE = 3, T_RAMP = 4, T_BUSY = 5;

    private void generateRow(int gz) {
        int[] t = new int[3];
        int closed = 0;
        for (int l = 0; l < 3; l++) {
            if (busyUntil[l] > gz) {
                t[l] = T_BUSY;
                closed++;
                continue;
            }
            int r = rnd.nextInt(100);
            t[l] = r < 28 ? EMPTY : r < 52 ? T_BARRIER : r < 70 ? T_BAR : r < 86 ? T_SHUTTLE : T_RAMP;
        }
        // une seule navette sans rampe à la fois : il reste toujours une voie qu'on franchit de face
        // (vide, barrière à sauter, portique à glisser ou rampe à monter)
        for (int l = 0; l < 3; l++) {
            if (t[l] == T_SHUTTLE) {
                if (closed >= 1) {
                    t[l] = rnd.nextBoolean() ? EMPTY : T_BARRIER;
                } else {
                    closed++;
                }
            }
        }
        // une voie libre sur deux rangées en moyenne pour respirer
        if (rnd.nextInt(3) == 0) {
            int l = rnd.nextInt(3);
            if (t[l] != T_BUSY) {
                t[l] = EMPTY;
            }
        }
        boolean coinsDone = false;
        for (int l = 0; l < 3; l++) {
            double cx = laneX(l);
            switch (t[l]) {
                case T_BARRIER -> {
                    boxes.add(new Box(Kind.BARRIER, l, gz, gz + 1, 0, 1));
                    if (!coinsDone && rnd.nextInt(3) == 0) {
                        double[] arc = {0.9, 1.5, 2.0, 1.5, 0.9};
                        for (int i = 0; i < 5; i++) {
                            coin(cx, arc[i], gz - 2 + i + 0.5);
                        }
                        coinsDone = true;
                    }
                }
                case T_BAR -> boxes.add(new Box(Kind.BAR, l, gz, gz + 1, 1, 2));
                case T_SHUTTLE, T_RAMP -> {
                    int len = 8 + rnd.nextInt(11);
                    int s = gz;
                    if (t[l] == T_RAMP) {
                        boxes.add(new Box(Kind.RAMP, l, gz, gz + 4, 0, 3));
                        s = gz + 4;
                        for (int i = 0; i < len; i += 2) {
                            coin(cx, 3.9, s + i + 0.5);
                        }
                    }
                    boxes.add(new Box(Kind.SHUTTLE, l, s, s + len, 0, 3));
                    busyUntil[l] = s + len + 3;
                }
                case EMPTY -> {
                    if (!coinsDone && rnd.nextInt(2) == 0) {
                        int n = 5 + rnd.nextInt(4);
                        for (int i = 0; i < n; i++) {
                            coin(cx, 0.9, gz + 2 + i * 2 + 0.5);
                        }
                        coinsDone = true;
                    } else if (gz >= nextPower) {
                        power(cx, gz + 6.5);
                        nextPower = gz + 160 + rnd.nextInt(120);
                    }
                }
                default -> {
                }
            }
        }
    }

    private void set(int bx, int by, int bz, Material m) {
        world.getBlockAt(bx, by, bz).setType(m, false);
    }

    private void set(int bx, int by, int bz, BlockData d) {
        world.getBlockAt(bx, by, bz).setBlockData(d, false);
    }

    private static final BlockData SLAB = Bukkit.createBlockData("minecraft:smooth_stone_slab[type=bottom]");
    private static final BlockData ROD = Bukkit.createBlockData("minecraft:end_rod[facing=up]");

    private void clearSlice(int bz) {
        for (int bx = x0 - SLICE_W; bx <= x0 + SLICE_W; bx++) {
            for (int by = y0 - 1; by <= y0 + SLICE_TOP; by++) {
                if (!world.getBlockAt(bx, by, bz).getType().isAir()) {
                    set(bx, by, bz, Material.AIR);
                }
            }
        }
    }

    private void buildSlice(int bz) {
        clearSlice(bz);
        int f = y0 - 1;
        for (int dx = -4; dx <= 4; dx++) {
            Material m = Math.floorMod(bz, 8) == 0 ? Material.LIGHT_BLUE_CONCRETE
                    : Math.abs(dx) <= 1 ? Material.GRAY_CONCRETE : Material.BLACK_CONCRETE;
            set(x0 + dx, f, bz, m);
        }
        for (int s : new int[]{-5, 5}) {
            set(x0 + s, f, bz, Math.floorMod(bz, 4) == 0 ? Material.SEA_LANTERN : Material.CYAN_CONCRETE);
            set(x0 + s, y0, bz, Material.CYAN_STAINED_GLASS);
        }
        if (bz < 0) {
            for (int dx = -4; dx <= 4; dx++) {
                set(x0 + dx, f, bz, Material.POLISHED_BLACKSTONE);
            }
        }
        if (bz == -8) {
            for (int dx = -5; dx <= 5; dx++) {
                for (int dy = 0; dy <= 4; dy++) {
                    set(x0 + dx, y0 + dy, bz, Material.PURPLE_STAINED_GLASS);
                }
            }
        }
        // arches du couloir tous les 24 blocs
        if (Math.floorMod(bz, 24) == 12) {
            for (int s : new int[]{-6, 6}) {
                for (int dy = -1; dy <= 6; dy++) {
                    set(x0 + s, y0 + dy, bz, dy == 3 ? Material.PEARLESCENT_FROGLIGHT : Material.POLISHED_BLACKSTONE_BRICKS);
                }
            }
            for (int dx = -6; dx <= 6; dx++) {
                set(x0 + dx, y0 + 7, bz, Math.floorMod(dx, 3) == 0 ? Material.VERDANT_FROGLIGHT : Material.POLISHED_BLACKSTONE_BRICKS);
            }
        }
        for (Box b : boxes) {
            if (bz >= b.z1 && bz < b.z2 && !b.broken) {
                drawBox(b, bz);
            }
        }
    }

    private void drawBox(Box b, int bz) {
        int cx = x0 + (b.lane - 1) * 3;
        int rel = (int) (bz - b.z1);
        switch (b.kind) {
            case BARRIER -> {
                for (int dx = -1; dx <= 1; dx++) {
                    set(cx + dx, y0, bz, dx == 0 ? Material.WHITE_CONCRETE : Material.RED_CONCRETE);
                }
            }
            case BAR -> {
                set(cx - 1, y0, bz, ROD);
                set(cx + 1, y0, bz, ROD);
                for (int dx = -1; dx <= 1; dx++) {
                    set(cx + dx, y0 + 1, bz, Math.floorMod(cx + dx, 2) == 0 ? Material.YELLOW_CONCRETE : Material.BLACK_CONCRETE);
                }
            }
            case RAMP -> {
                double h = (rel + 1) * 0.75;
                int full = (int) Math.floor(h);
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = 0; dy < full; dy++) {
                        set(cx + dx, y0 + dy, bz, Material.SMOOTH_STONE);
                    }
                    if (h - full >= 0.5) {
                        set(cx + dx, y0 + full, bz, SLAB);
                    }
                }
            }
            case SHUTTLE -> {
                int len = (int) (b.z2 - b.z1);
                for (int dx = -1; dx <= 1; dx++) {
                    boolean side = dx != 0;
                    if (rel == 0) {
                        set(cx + dx, y0, bz, side ? Material.SEA_LANTERN : Material.BLACK_CONCRETE);
                        set(cx + dx, y0 + 1, bz, Material.LIGHT_BLUE_STAINED_GLASS);
                        set(cx + dx, y0 + 2, bz, Material.WHITE_CONCRETE);
                    } else if (rel == len - 1) {
                        set(cx + dx, y0, bz, side ? Material.REDSTONE_LAMP : Material.GRAY_CONCRETE);
                        set(cx + dx, y0 + 1, bz, Material.RED_STAINED_GLASS);
                        set(cx + dx, y0 + 2, bz, Material.WHITE_CONCRETE);
                    } else {
                        set(cx + dx, y0, bz, Material.GRAY_CONCRETE);
                        set(cx + dx, y0 + 1, bz, side && rel % 3 == 1 ? Material.LIGHT_BLUE_STAINED_GLASS : Material.WHITE_CONCRETE);
                        set(cx + dx, y0 + 2, bz, side ? Material.ORANGE_CONCRETE : Material.WHITE_CONCRETE);
                    }
                }
            }
            default -> {
            }
        }
    }

    /** Bouclier : l'obstacle percuté vole en éclats. */
    private void smash(Box b) {
        b.broken = true;
        int cx = x0 + (b.lane - 1) * 3;
        for (int bz = (int) b.z1; bz < (int) b.z2; bz++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = 0; dy <= 2; dy++) {
                    set(cx + dx, y0 + dy, bz, Material.AIR);
                }
            }
        }
        world.spawnParticle(Particle.EXPLOSION, cx + 0.5, y0 + 1, z + 1, 2, 0.6, 0.6, 0.6, 0);
    }

    // ------------------------------------------------------------------ fin

    /** Retire tout ce que la course a posé et rend au joueur son état. */
    void cleanup() {
        state = State.DONE;
        if (seat != null) {
            seat.eject();
            seat.remove();
        }
        for (Pickup p : pickups) {
            p.entity.remove();
        }
        pickups.clear();
        for (int bz = clearedZ; bz < builtZ; bz++) {
            clearSlice(bz);
        }
        for (Entity e : world.getNearbyEntities(new Location(world, x0, y0, z), 12, 12, AHEAD + 60)) {
            if (e.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
                e.remove();
            }
        }
        if (player.isOnline()) {
            setScale(baseScale);
            player.setAllowFlight(hadFlight);
            player.setFallDistance(0);
        }
    }
}
