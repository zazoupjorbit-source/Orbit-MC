package fr.orbit.bornes.defense;

import fr.orbit.bornes.GameType;
import fr.orbit.bornes.OrbitBornes;
import fr.orbit.bornes.SeatedGame;
import fr.orbit.bornes.Sessions;
import fr.orbit.bornes.Txt;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.SplittableRandom;

/**
 * DÉFENSE ORBITALE : des vagues de drones descendent vers la station en marchant de gauche à droite ; le joueur, assis
 * face au mur, pilote le vaisseau de défense (Q/D) et tire (Espace ou Z). Boucliers en blocs qui s'usent, vaisseau-mère
 * bonus, 3 vies, vagues de plus en plus rapides. Dessins originaux (pack) ; sans le pack, blocs colorés.
 * <p>Repère du terrain : fx de 0 à 26 (vers +x), fy de 0 à 20 (vers le haut) ; mur au fond en z0 - 1, boucliers en z0,
 * objets affichés en z0 + 1.3.</p>
 */
public final class DefenseGame extends SeatedGame {

    static final double FW = 26, FH = 20, SHIP_Y = 1.2, DZ = 1.3;
    static final int COLS = 9, ROWS = 5;
    static final int[] SHIELD_X = {3, 9, 15, 21};

    enum State { COUNTDOWN, PLAY, BETWEEN, OVER }

    private static final class Drone {
        final int row, col, kind; // kind 0 = sentinelle (10), 1 = méduse (20), 2 = croiseur (30)
        boolean alive = true;
        ItemDisplay e;

        Drone(int row, int col, int kind) {
            this.row = row;
            this.col = col;
            this.kind = kind;
        }
    }

    private static final class Bullet {
        double x, y;
        final double vy;
        final ItemDisplay e;

        Bullet(double x, double y, double vy, ItemDisplay e) {
            this.x = x;
            this.y = y;
            this.vy = vy;
            this.e = e;
        }
    }

    private final SplittableRandom rnd = new SplittableRandom();
    private State state = State.COUNTDOWN;
    private int countdown = 60, between, wave = 1, lives = 3, score, kills, invul, marchTimer, dir = 1, frame, motherTimer, fireCool;
    private double shipX = FW / 2, offX, offY, motherX;
    private int motherDir;
    private boolean started, left, right, fire;
    private final List<Drone> drones = new ArrayList<>();
    private final List<Bullet> shots = new ArrayList<>(), bolts = new ArrayList<>();
    private final List<Object[]> booms = new ArrayList<>();
    private final int[][] shield = new int[27][6];
    private ItemDisplay ship, mother;
    private TextDisplay stats;
    private int doubleShot;

    public DefenseGame(OrbitBornes plugin, Player player, int slot) {
        super(plugin, player, GameType.DEFENSE, slot, Sessions.Z_DEFENSE);
    }

    private double wx(double fx) {
        return x0 + fx;
    }

    private double wy(double fy) {
        return Y + fy;
    }

    // ------------------------------------------------------------------ décor

    @Override
    protected Location seatLocation() {
        return new Location(world, x0 + FW / 2, Y + 8.2, z0 + 22.5, 180f, 0f);
    }

    @Override
    protected void build() {
        for (int x = -2; x <= 28; x++) {
            for (int y = -2; y <= 22; y++) {
                boolean frameCell = x == -2 || x == 28 || y == -2 || y == 22;
                Material m = frameCell ? (Math.floorMod(x + y, 4) == 0 ? Material.PEARLESCENT_FROGLIGHT : Material.PURPLE_CONCRETE)
                        : starAt(x, y) ? (rnd.nextInt(3) == 0 ? Material.LIGHT_BLUE_STAINED_GLASS : Material.WHITE_STAINED_GLASS)
                        : Material.BLACK_CONCRETE;
                set(x0 + x, Y + y, z0 - 1, m);
            }
        }
        // sol de la station
        for (int x = -1; x <= 27; x++) {
            set(x0 + x, Y - 1, z0, Math.floorMod(x, 5) == 0 ? Material.SEA_LANTERN : Material.CYAN_TERRACOTTA);
        }
        stats = text(new Location(world, x0 + FW / 2, Y + FH + 2.6, z0 + 0.2), "", 1.5f);
        text(new Location(world, x0 + FW / 2, Y + FH + 4.4, z0 + 0.2), GameType.DEFENSE.title(), 3f);
        ship = itemDisplay(wx(shipX), wy(SHIP_Y), z0 + DZ, sprite("ship", Material.GOLD_BLOCK), hasPack ? 1.8f : 0.9f, 1);
    }

    private boolean starAt(int x, int y) {
        int h = (x * 73856093) ^ (y * 19349663) ^ (x0 * 83492791);
        return Math.floorMod(h, 23) == 0;
    }

    @Override
    protected void begin() {
        Txt.send(player, GameType.DEFENSE.title() + " <#9AA4B2>· <#E6EDF3>Q/D</#E6EDF3> (A/D) se déplacer · <#E6EDF3>Espace</#E6EDF3> ou <#E6EDF3>Z</#E6EDF3> tirer "
                + "(maintenu = tir continu). <#9AA4B2>/bornes quitter pour abandonner.");
        hud();
    }

    private void buildShields() {
        for (int[] col : shield) {
            java.util.Arrays.fill(col, 0);
        }
        for (int sx : SHIELD_X) {
            for (int dx = 0; dx < 3; dx++) {
                for (int dy = 0; dy < 2; dy++) {
                    shield[sx + dx][3 + dy] = 2;
                }
            }
        }
        for (int x = 0; x < 27; x++) {
            for (int y = 2; y < 6; y++) {
                drawShield(x, y);
            }
        }
    }

    private void drawShield(int x, int y) {
        int hp = shield[x][y];
        set(x0 + x, Y + y, z0, hp >= 2 ? Material.CYAN_CONCRETE : hp == 1 ? Material.CYAN_STAINED_GLASS : Material.AIR);
    }

    private void spawnWave() {
        for (Drone d : drones) {
            if (d.e != null) {
                forget(d.e);
            }
        }
        drones.clear();
        offX = 0;
        offY = Math.min(4.0, (wave - 1) * 0.8);
        dir = 1;
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                Drone d = new Drone(r, c, r == 0 ? 2 : r <= 2 ? 1 : 0);
                d.e = itemDisplay(wx(dx(d)), wy(dy(d)), z0 + DZ, droneItem(d.kind, 0), hasPack ? 1.5f : 0.7f, 2);
                drones.add(d);
            }
        }
        buildShields();
        motherTimer = 400 + rnd.nextInt(300);
    }

    private org.bukkit.inventory.ItemStack droneItem(int kind, int f) {
        String m = "drone_" + (char) ('a' + kind) + "_" + (f + 1);
        Material plain = kind == 0 ? Material.LIGHT_BLUE_CONCRETE : kind == 1 ? Material.MAGENTA_CONCRETE : Material.LIME_CONCRETE;
        return sprite(m, plain);
    }

    private double dx(Drone d) {
        return 3.5 + d.col * 2.3 + offX;
    }

    private double dy(Drone d) {
        return 17.5 - d.row * 1.7 - offY;
    }

    // ------------------------------------------------------------------ commandes

    @Override
    protected void input(Input in) {
        left = in.isLeft();
        right = in.isRight();
        fire = in.isJump() || in.isForward();
    }

    // ------------------------------------------------------------------ tick

    @Override
    protected void update() {
        if (state == State.OVER) {
            return;
        }
        if (state == State.COUNTDOWN) {
            if (countdown % 20 == 0 && countdown > 0) {
                Txt.title(player, "<gradient:#7CFF8A:#62CFF4><b>" + countdown / 20 + "</b></gradient>",
                        countdown == 60 ? "<#9AA4B2>Q/D se déplacer · Espace tirer" : "", 900);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.9f, 1.0f);
            }
            if (--countdown <= 0) {
                state = State.PLAY;
                started = true;
                spawnWave();
                Txt.title(player, "<#7CFF8A><b>VAGUE 1</b>", "<#9AA4B2>Défends la station !", 900);
            }
            return;
        }
        tickBooms();
        moveShip();
        if (state == State.BETWEEN) {
            if (--between <= 0) {
                wave++;
                state = State.PLAY;
                spawnWave();
                Txt.title(player, "<#7CFF8A><b>VAGUE " + wave + "</b>", "<#9AA4B2>Ils accélèrent…", 900);
            }
            moveShots();
            return;
        }
        march();
        if (state != State.PLAY) {
            return;
        }
        droneFire();
        moveShots();
        if (state != State.PLAY) {
            return;
        }
        moveBolts();
        motherShip();
        if (invul > 0) {
            invul--;
            if (ship != null) {
                ship.setGlowing(invul % 6 < 3);
            }
        }
        if (ticks % 10 == 0) {
            hud();
        }
    }

    private void moveShip() {
        double v = 0.42;
        if (left && !right) {
            shipX = Math.max(1, shipX - v);
        } else if (right && !left) {
            shipX = Math.min(FW - 1, shipX + v);
        }
        if (ship != null) {
            move(ship, wx(shipX), wy(SHIP_Y), z0 + DZ);
        }
        if (fireCool > 0) {
            fireCool--;
        }
        if (doubleShot > 0) {
            doubleShot--;
        }
        int maxShots = doubleShot > 0 ? 2 : 1;
        if (fire && fireCool == 0 && shots.size() < maxShots && state == State.PLAY) {
            shots.add(new Bullet(shipX, SHIP_Y + 1, 0.9, itemDisplay(wx(shipX), wy(SHIP_Y + 1), z0 + DZ, sprite("shot", Material.END_ROD),
                    hasPack ? 1.0f : 0.5f, 1)));
            fireCool = doubleShot > 0 ? 5 : 8;
            player.playSound(player.getLocation(), Sound.ENTITY_BREEZE_SHOOT, 0.35f, 1.8f);
        }
    }

    private int alive() {
        int n = 0;
        for (Drone d : drones) {
            if (d.alive) {
                n++;
            }
        }
        return n;
    }

    private void march() {
        int alive = alive();
        if (alive == 0) {
            score += 100 * wave;
            state = State.BETWEEN;
            between = 60;
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.4f);
            Txt.title(player, "", "<#E0B864>Vague " + wave + " repoussée · +" + 100 * wave, 1200);
            for (Bullet b : bolts) {
                forget(b.e);
            }
            bolts.clear();
            return;
        }
        int interval = Math.max(2, 2 + alive * 16 / (ROWS * COLS) - Math.min(4, wave - 1) / 2);
        if (++marchTimer < interval) {
            return;
        }
        marchTimer = 0;
        double minX = 99, maxX = -99, minY = 99;
        for (Drone d : drones) {
            if (d.alive) {
                minX = Math.min(minX, dx(d));
                maxX = Math.max(maxX, dx(d));
                minY = Math.min(minY, dy(d));
            }
        }
        if ((dir > 0 && maxX + 0.5 > FW - 0.6) || (dir < 0 && minX - 0.5 < 0.6)) {
            dir = -dir;
            offY += 0.8;
        } else {
            offX += 0.5 * dir;
        }
        frame ^= 1;
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.35f, frame == 0 ? 0.6f : 0.5f);
        for (Drone d : drones) {
            if (!d.alive) {
                continue;
            }
            move(d.e, wx(dx(d)), wy(dy(d)), z0 + DZ);
            if (hasPack) {
                d.e.setItemStack(droneItem(d.kind, frame));
            }
            // les drones rasent les boucliers
            int bx = (int) Math.floor(dx(d)), by = (int) Math.floor(dy(d));
            for (int x = bx - 1; x <= bx + 1; x++) {
                if (x >= 0 && x < 27 && by >= 0 && by < 6 && shield[x][by] > 0) {
                    shield[x][by] = 0;
                    drawShield(x, by);
                }
            }
        }
        if (minY - 0.8 < SHIP_Y + 1.3) {
            gameOver("Les drones ont atteint la station");
        }
    }

    private void droneFire() {
        int maxBolts = Math.min(6, 2 + wave);
        int chance = Math.max(8, 30 - wave * 3);
        if (bolts.size() >= maxBolts || rnd.nextInt(chance) != 0) {
            return;
        }
        // un drone du bas d'une colonne au hasard (de préférence au-dessus du vaisseau)
        Drone shooter = null;
        double best = 99;
        for (Drone d : drones) {
            if (!d.alive) {
                continue;
            }
            boolean bottom = true;
            for (Drone o : drones) {
                if (o.alive && o.col == d.col && o.row > d.row) {
                    bottom = false;
                    break;
                }
            }
            if (!bottom) {
                continue;
            }
            double score = Math.abs(dx(d) - shipX) + rnd.nextDouble() * 8;
            if (score < best) {
                best = score;
                shooter = d;
            }
        }
        if (shooter != null) {
            double x = dx(shooter), y = dy(shooter) - 0.8;
            bolts.add(new Bullet(x, y, -(0.28 + Math.min(0.2, wave * 0.03)),
                    itemDisplay(wx(x), wy(y), z0 + DZ, sprite("bolt", Material.BLAZE_ROD), hasPack ? 1.0f : 0.5f, 1)));
        }
    }

    private boolean hitShield(double fx, double fy) {
        int x = (int) Math.floor(fx), y = (int) Math.floor(fy);
        if (x >= 0 && x < 27 && y >= 0 && y < 6 && shield[x][y] > 0) {
            shield[x][y]--;
            drawShield(x, y);
            world.spawnParticle(Particle.BLOCK, wx(fx), wy(fy), z0 + 1.1, 6, 0.2, 0.2, 0.05, Material.CYAN_STAINED_GLASS.createBlockData());
            return true;
        }
        return false;
    }

    private void moveShots() {
        Iterator<Bullet> it = shots.iterator();
        while (it.hasNext()) {
            Bullet b = it.next();
            b.y += b.vy;
            boolean gone = b.y > FH + 0.5 || hitShield(b.x, b.y);
            if (!gone) {
                for (Drone d : drones) {
                    if (d.alive && Math.abs(dx(d) - b.x) < 0.85 && Math.abs(dy(d) - b.y) < 0.7) {
                        d.alive = false;
                        forget(d.e);
                        int pts = d.kind == 0 ? 10 : d.kind == 1 ? 20 : 30;
                        score += pts;
                        kills++;
                        boom(dx(d), dy(d));
                        player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.35f, 1.6f + d.kind * 0.1f);
                        gone = true;
                        break;
                    }
                }
            }
            if (!gone && mother != null && Math.abs(motherX - b.x) < 1.4 && Math.abs(18.8 - b.y) < 0.8) {
                int pts = 50 * (1 + rnd.nextInt(6));
                score += pts;
                boom(motherX, 18.8);
                forget(mother);
                mother = null;
                doubleShot = 200;
                gone = true;
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.8f);
                Txt.title(player, "", "<#E0B864>Vaisseau-mère abattu · +" + pts + " <#62CFF4>· tir double 10 s", 1200);
            }
            if (gone) {
                forget(b.e);
                it.remove();
            } else {
                move(b.e, wx(b.x), wy(b.y), z0 + DZ);
            }
        }
    }

    private void moveBolts() {
        Iterator<Bullet> it = bolts.iterator();
        while (it.hasNext()) {
            Bullet b = it.next();
            b.y += b.vy;
            boolean gone = b.y < -0.5 || hitShield(b.x, b.y);
            if (!gone && invul == 0 && Math.abs(b.x - shipX) < 0.9 && Math.abs(b.y - SHIP_Y) < 0.6) {
                gone = true;
                hit();
                if (state != State.PLAY) {
                    return;
                }
            }
            if (gone) {
                forget(b.e);
                it.remove();
            } else {
                move(b.e, wx(b.x), wy(b.y), z0 + DZ);
            }
        }
    }

    private void hit() {
        lives--;
        boom(shipX, SHIP_Y);
        player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 0.8f);
        if (lives <= 0) {
            gameOver("Vaisseau détruit");
            return;
        }
        invul = 50;
        Txt.title(player, "", "<#E0484A>Touché ! <#E6EDF3>" + "❤".repeat(lives) + " restant" + (lives > 1 ? "s" : ""), 900);
    }

    private void motherShip() {
        if (mother == null) {
            if (--motherTimer <= 0) {
                motherDir = rnd.nextBoolean() ? 1 : -1;
                motherX = motherDir > 0 ? -1 : FW + 1;
                mother = itemDisplay(wx(motherX), wy(18.8), z0 + DZ, sprite("mother", Material.PURPLE_CONCRETE), hasPack ? 2.2f : 1.0f, 1);
                player.playSound(player.getLocation(), Sound.BLOCK_BEACON_AMBIENT, 1f, 1.6f);
                motherTimer = 500 + rnd.nextInt(400);
            }
            return;
        }
        motherX += 0.18 * motherDir;
        if (motherX < -1.5 || motherX > FW + 1.5) {
            forget(mother);
            mother = null;
            return;
        }
        move(mother, wx(motherX), wy(18.8), z0 + DZ);
    }

    private void boom(double fx, double fy) {
        ItemDisplay e = itemDisplay(wx(fx), wy(fy), z0 + DZ + 0.1, sprite("boom", Material.FIRE_CHARGE), hasPack ? 1.6f : 0.6f, 1);
        booms.add(new Object[]{e, 6});
        world.spawnParticle(Particle.FIREWORK, wx(fx), wy(fy), z0 + DZ, 8, 0.3, 0.3, 0.05, 0.05);
    }

    private void tickBooms() {
        Iterator<Object[]> it = booms.iterator();
        while (it.hasNext()) {
            Object[] b = it.next();
            int t = (int) b[1] - 1;
            b[1] = t;
            if (t <= 0) {
                forget((ItemDisplay) b[0]);
                it.remove();
            }
        }
    }

    private void gameOver(String why) {
        if (state == State.OVER) {
            return;
        }
        state = State.OVER;
        plugin.sessions().finish(this, "<#E0484A><b>" + why + "</b>");
    }

    private void hud() {
        String s = "<#E6EDF3>" + Txt.n(score) + " pts</#E6EDF3>  <#5A6270>·</#5A6270>  <#7CFF8A>vague " + wave + "</#7CFF8A>  <#5A6270>·</#5A6270>  <#E0484A>"
                + "❤".repeat(Math.max(0, lives)) + "</#E0484A>" + (doubleShot > 0 ? "  <#62CFF4>tir double " + (doubleShot + 19) / 20 + "s" : "");
        Txt.bar(player, s);
        if (stats != null) {
            stats.text(Txt.mm("<#9AA4B2>SCORE <#E6EDF3><b>" + Txt.n(score) + "</b>   <#9AA4B2>VAGUE <#7CFF8A>" + wave + "   <#E0484A>"
                    + "❤".repeat(Math.max(0, lives))));
        }
    }

    // ------------------------------------------------------------------ fin

    @Override
    protected void end() {
        state = State.OVER;
    }

    @Override
    protected double score() {
        return score;
    }

    @Override
    protected int ocWanted() {
        return score / Math.max(1, plugin.getConfig().getInt("defense.oc-diviseur", 15));
    }

    @Override
    protected String summary() {
        return "<b>" + Txt.n(score) + "</b> pts · vague " + wave + " · " + kills + " drones abattus";
    }

    @Override
    protected boolean played() {
        return started;
    }
}
