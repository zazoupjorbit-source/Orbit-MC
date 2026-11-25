package fr.orbit.casino.jeux;

import fr.orbit.casino.Holo;
import fr.orbit.casino.Menu;
import fr.orbit.casino.OrbitCasino;
import fr.orbit.casino.Txt;
import fr.orbit.casino.game.Cards;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Billard américain (8-ball simplifié) sur la table du salon. Les 16 boules sont des ItemDisplay déplacés chaque tick ;
 * la physique 2D (frottement, bandes, chocs entre boules, 6 poches) est calculée par le serveur.
 * Viser : regarder le point du tapis où envoyer la blanche. Tirer : rester accroupi (plus longtemps = plus fort) puis relâcher.
 * Modes : entraînement solo, contre le bot ORBIT, ou défi à 2 avec mise en OC entre joueurs (le casino ne prend rien).
 */
public final class Billard {

    private static final double R = 0.1, X0 = Salon.BIL_X1, X1 = Salon.BIL_X2 + 1.0, Z0 = Salon.BIL_Z1, Z1 = Salon.BIL_Z2 + 1.0;
    private static final double SURFACE = Salon.Y + 1.0, BALL_Y = SURFACE + R;
    private static final double[][] POCKETS = {{X0, Z0}, {(X0 + X1) / 2, Z0}, {X1, Z0}, {X0, Z1}, {(X0 + X1) / 2, Z1}, {X1, Z1}};
    private static final double POCKET_CORNER = 0.22, POCKET_SIDE = 0.19;
    private static final int SUB = 10, CHARGE_MAX = 36, TURN_SECONDS = 45;
    private static final double VMAX = 0.6, CUSHION = 0.78, BALL_E = 0.95, FRICTION = 0.975, ROLL = 0.003, STOP = 0.003;
    private static final double HEAD_X = X0 + (X1 - X0) * 0.25, FOOT_X = X0 + (X1 - X0) * 0.72, MID_Z = (Z0 + Z1) / 2;
    private static final long[] STAKES = {0, 100, 500, 1000, 5000};
    private static final String BOT = "Bot ORBIT";

    private enum Mode { NONE, WAITING, SOLO, BOT, DUEL }

    private final class Ball {
        final int n;
        double x, z, vx, vz;
        boolean in;
        ItemDisplay e;

        Ball(int n, double x, double z) {
            this.n = n;
            this.x = x;
            this.z = z;
        }

        boolean moving() {
            return !in && (vx != 0 || vz != 0);
        }
    }

    private final OrbitCasino plugin;
    private final World world;
    private final Ball[] balls = new Ball[16];
    private Mode mode = Mode.NONE;
    private final UUID[] players = new UUID[2];
    private final String[] names = new String[2];
    private final int[] group = new int[2]; // 0 : pas encore, 1 : pleines (1-7), 2 : rayées (9-15)
    private long stake;
    private int turn;
    private boolean moving;
    private boolean charging;
    private int charge;
    private int turnLeft;
    private int shots;
    private int botWait;
    private Integer firstHit;
    private final List<Integer> pocketed = new ArrayList<>();
    private boolean scratch;
    private TextDisplay sign;
    private int ending = -1;

    public Billard(OrbitCasino plugin, World world) {
        this.plugin = plugin;
        this.world = world;
    }

    public void spawn() {
        sign = Holo.text(new Location(world, Salon.CX, Salon.Y + 3.2, Salon.CZ), "", 0.9f, false);
        render();
        if (mode != Mode.NONE && mode != Mode.WAITING) {
            for (Ball b : balls) {
                if (b != null && !b.in) {
                    b.e = display(b);
                }
            }
        }
    }

    private void render() {
        String status = switch (mode) {
            case NONE -> "<#9AA4B2>Clic droit sur la table pour jouer";
            case WAITING -> "<#62CFF4>" + names[0] + " attend un adversaire" + (stake > 0 ? " · mise " + Txt.n(stake) + " OC" : "")
                    + "\n<#9AA4B2>Clic droit sur la table pour accepter";
            case SOLO -> "<#9AA4B2>Entraînement de " + names[0] + " · " + shots + " coup" + (shots > 1 ? "s" : "");
            case BOT, DUEL -> "<#E6EDF3>" + names[0] + " " + groupTag(0) + " <#5A6270>contre</#5A6270> " + names[1] + " " + groupTag(1)
                    + "\n<#E0B864>À " + names[turn] + " de jouer" + (stake > 0 ? " <#5A6270>· pot " + Txt.n(stake * 2) + " OC" : "");
        };
        Holo.set(sign, "<gradient:#7BD88F:#62CFF4><b>BILLARD</b></gradient>\n" + status);
    }

    private String groupTag(int i) {
        return group[i] == 1 ? "<#E0B864>(pleines)</#E0B864>" : group[i] == 2 ? "<#62CFF4>(rayées)</#62CFF4>" : "";
    }

    // ------------------------------------------------------------------ boules

    private ItemDisplay display(Ball b) {
        ItemStack it = new ItemStack(Material.SNOWBALL);
        ItemMeta meta = it.getItemMeta();
        meta.setItemModel(new NamespacedKey("orbitcasino", "pool_" + b.n));
        it.setItemMeta(meta);
        return world.spawn(new Location(world, b.x, BALL_Y, b.z), ItemDisplay.class, d -> {
            Holo.tag(d);
            d.setItemStack(it);
            d.setBillboard(Display.Billboard.CENTER);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTeleportDuration(1);
            float s = (float) (2 * R / (60 / 64.0));
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(s, s, s), new AxisAngle4f()));
        });
    }

    private void rack() {
        clearBalls();
        balls[0] = new Ball(0, HEAD_X, MID_Z);
        // triangle : 8 au centre de la 3e rangée, une pleine et une rayée aux coins du fond
        List<Integer> rest = new ArrayList<>();
        for (int n = 1; n <= 15; n++) {
            if (n != 8 && n != 1 && n != 9) {
                rest.add(n);
            }
        }
        java.util.Collections.shuffle(rest, Cards.RANDOM);
        boolean flip = Cards.RANDOM.nextBoolean();
        double dx = 2 * R * Math.cos(Math.PI / 6) + 0.004;
        int idx = 0;
        for (int row = 0; row < 5; row++) {
            for (int j = 0; j <= row; j++) {
                double x = FOOT_X + row * dx, z = MID_Z + (j - row / 2.0) * (2 * R + 0.004);
                int n;
                if (row == 2 && j == 1) {
                    n = 8;
                } else if (row == 4 && j == 0) {
                    n = flip ? 1 : 9;
                } else if (row == 4 && j == 4) {
                    n = flip ? 9 : 1;
                } else {
                    n = rest.get(idx++);
                }
                balls[n] = new Ball(n, x, z);
            }
        }
        for (Ball b : balls) {
            b.e = display(b);
        }
    }

    private void clearBalls() {
        for (int i = 0; i < balls.length; i++) {
            if (balls[i] != null && balls[i].e != null) {
                balls[i].e.remove();
            }
            balls[i] = null;
        }
    }

    // ------------------------------------------------------------------ s'installer

    public boolean isPlayer(UUID u) {
        return u.equals(players[0]) || u.equals(players[1]);
    }

    public void click(Player p) {
        UUID u = p.getUniqueId();
        switch (mode) {
            case NONE -> menu(p);
            case WAITING -> {
                if (u.equals(players[0])) {
                    Menu m = new Menu(3, "<#7BD88F><b>Billard</b></#7BD88F> <#5A6270>— tu attends un adversaire");
                    m.set(13, Menu.item(Material.BARRIER, "<#E0484A><b>Annuler le défi", stake > 0 ? "<#9AA4B2>Ta mise de " + Txt.n(stake) + " OC est rendue" : ""),
                            e -> {
                                p.closeInventory();
                                cancelWaiting("défi annulé");
                            });
                    m.fill(Material.BLACK_STAINED_GLASS_PANE);
                    m.open(p);
                } else {
                    Menu m = new Menu(3, "<#7BD88F><b>Billard</b></#7BD88F> <#5A6270>— défi de " + names[0]);
                    m.set(13, Menu.item(Material.EMERALD, "<#7BD88F><b>Accepter le défi de " + names[0],
                            stake > 0 ? "<#E0B864>Mise : " + Txt.n(stake) + " OC chacun · le gagnant prend " + Txt.n(stake * 2) + " OC" : "<#9AA4B2>Sans mise",
                            "<#5A6270>Le casino ne prend rien"), e -> {
                        p.closeInventory();
                        join(p);
                    });
                    m.fill(Material.BLACK_STAINED_GLASS_PANE);
                    m.open(p);
                }
            }
            default -> {
                if (isPlayer(u)) {
                    help(p);
                } else {
                    Txt.send(p, "Table occupée : " + names[0] + (mode == Mode.SOLO ? " s'entraîne." : " contre " + names[1] + "."));
                }
            }
        }
    }

    private void menu(Player p) {
        Menu m = new Menu(3, "<#7BD88F><b>Billard</b></#7BD88F> <#5A6270>— choisis ta partie");
        m.set(10, Menu.item(Material.SNOWBALL, "<#62CFF4><b>Entraînement solo", "<#9AA4B2>Rentre les 15 boules, en le moins de coups possible"),
                e -> {
                    p.closeInventory();
                    startSolo(p);
                });
        m.set(11, Menu.item(Material.COMPARATOR, "<#C79BFF><b>Contre le bot ORBIT", "<#9AA4B2>Sans mise, pour apprendre les règles"),
                e -> {
                    p.closeInventory();
                    startBot(p);
                });
        for (int k = 0; k < STAKES.length; k++) {
            long s = STAKES[k];
            m.set(13 + k, Menu.item(s == 0 ? Material.IRON_NUGGET : s >= 1000 ? Material.GOLD_BLOCK : Material.GOLD_INGOT,
                    "<#E0B864><b>Défi à 2 · " + (s == 0 ? "sans mise" : Txt.n(s) + " OC"),
                    s == 0 ? "<#9AA4B2>Pour le plaisir" : "<#9AA4B2>Chacun mise " + Txt.n(s) + " OC, le gagnant prend tout",
                    "<#5A6270>Le casino ne prend rien"), e -> {
                p.closeInventory();
                challenge(p, s);
            });
        }
        m.fill(Material.BLACK_STAINED_GLASS_PANE);
        m.open(p);
    }

    private boolean busy(Player p) {
        if (plugin.casino() != null && plugin.casino().seatedAnywhere(p.getUniqueId())) {
            Txt.send(p, "<#E0484A>Quitte d'abord ta table.");
            return true;
        }
        if (mode != Mode.NONE && !(mode == Mode.WAITING && !isPlayer(p.getUniqueId()))) {
            Txt.send(p, "<#E0484A>La table vient d'être prise.");
            return true;
        }
        return false;
    }

    private void startSolo(Player p) {
        if (busy(p)) {
            return;
        }
        mode = Mode.SOLO;
        setPlayers(p, null);
        begin();
    }

    private void startBot(Player p) {
        if (busy(p)) {
            return;
        }
        mode = Mode.BOT;
        setPlayers(p, null);
        names[1] = BOT;
        begin();
    }

    private void setPlayers(Player a, Player b) {
        players[0] = a.getUniqueId();
        names[0] = a.getName();
        players[1] = b == null ? null : b.getUniqueId();
        names[1] = b == null ? null : b.getName();
        group[0] = group[1] = 0;
        stake = 0;
        shots = 0;
        turn = 0;
    }

    private void challenge(Player p, long s) {
        if (busy(p)) {
            return;
        }
        Runnable open = () -> {
            mode = Mode.WAITING;
            setPlayers(p, null);
            stake = s;
            render();
            for (Player o : world.getPlayers()) {
                o.sendMessage(Txt.mm(Txt.PREFIX + "<#7BD88F>" + p.getName() + "</#7BD88F> lance un défi au billard"
                                + (s > 0 ? " pour " + Txt.oc(s) : "") + ". ")
                        .append(Txt.button("Accepter", "#7BD88F", "/cj billard rejoindre", "Jouer contre " + p.getName())));
            }
        };
        if (s == 0) {
            open.run();
            return;
        }
        plugin.wallet().debit(p, s, "billard", "mise du défi", ok -> {
            if (!ok) {
                Txt.send(p, "<#E0484A>Pas assez d'Orbit Coins.");
            } else if (mode != Mode.NONE) {
                plugin.wallet().credit(p.getUniqueId(), s, "billard", "table prise", false);
                Txt.send(p, "<#E0484A>La table vient d'être prise. Mise rendue.");
            } else {
                open.run();
            }
        });
    }

    private static String ip(Player p) {
        InetSocketAddress a = p.getAddress();
        return a == null ? "" : a.getAddress().getHostAddress();
    }

    public void join(Player p) {
        if (mode != Mode.WAITING || isPlayer(p.getUniqueId())) {
            Txt.send(p, "<#9AA4B2>Pas de défi en attente.");
            return;
        }
        if (plugin.casino() != null && plugin.casino().seatedAnywhere(p.getUniqueId())) {
            Txt.send(p, "<#E0484A>Quitte d'abord ta table.");
            return;
        }
        Player a = Bukkit.getPlayer(players[0]);
        if (a == null) {
            cancelWaiting("joueur parti");
            return;
        }
        if (stake > 0 && plugin.getConfig().getBoolean("poker.meme-ip-interdite", true) && !p.hasPermission("orbitcasino.admin")
                && ip(a).equals(ip(p))) {
            Txt.send(p, "<#E0484A>Deux comptes de la même connexion ne peuvent pas jouer l'un contre l'autre avec une mise.");
            return;
        }
        Runnable go = () -> {
            mode = Mode.DUEL;
            long s = stake;
            setPlayers(a, p);
            stake = s;
            turn = Cards.RANDOM.nextInt(2);
            begin();
        };
        if (stake == 0) {
            go.run();
            return;
        }
        long s = stake;
        plugin.wallet().debit(p, s, "billard", "mise du défi", ok -> {
            if (!ok) {
                Txt.send(p, "<#E0484A>Pas assez d'Orbit Coins.");
            } else if (mode != Mode.WAITING || stake != s) {
                plugin.wallet().credit(p.getUniqueId(), s, "billard", "défi déjà pris", false);
                Txt.send(p, "<#E0484A>Trop tard, le défi n'est plus là. Mise rendue.");
            } else {
                go.run();
            }
        });
    }

    private void cancelWaiting(String why) {
        if (mode != Mode.WAITING) {
            return;
        }
        if (stake > 0) {
            plugin.wallet().credit(players[0], stake, "billard", why, false);
        }
        Player a = Bukkit.getPlayer(players[0]);
        if (a != null) {
            Txt.send(a, "Billard : " + why + (stake > 0 ? ", mise rendue." : "."));
        }
        reset();
    }

    private void begin() {
        ending = -1;
        Salon.repairBillard(world);
        rack();
        render();
        startTurn();
        for (UUID u : players) {
            Player p = u == null ? null : Bukkit.getPlayer(u);
            if (p != null) {
                help(p);
            }
        }
    }

    private void help(Player p) {
        Txt.send(p, "<#7BD88F><b>Billard</b></#7BD88F> · <#E6EDF3>regarde</#E6EDF3> le point du tapis où envoyer la blanche, "
                + "<#E6EDF3>reste accroupi</#E6EDF3> pour charger (plus longtemps = plus fort), <#E6EDF3>relâche</#E6EDF3> pour tirer.");
        p.sendMessage(Txt.mm("  ").append(Txt.button("Quitter la partie", "#E0484A", "/cj billard quitter",
                mode == Mode.DUEL ? "Abandonner : l'adversaire gagne" : "Ranger les boules")).append(Txt.mm(" "))
                .append(Txt.button("📖 Règles", "#62CFF4", "/oc regles billard", "Les règles du billard")));
    }

    private void reset() {
        clearBalls();
        mode = Mode.NONE;
        players[0] = players[1] = null;
        names[0] = names[1] = null;
        stake = 0;
        charging = false;
        moving = false;
        ending = -1;
        render();
    }

    // ------------------------------------------------------------------ tours

    private Player current() {
        UUID u = players[turn];
        return u == null ? null : Bukkit.getPlayer(u);
    }

    private boolean botTurn() {
        return mode == Mode.BOT && turn == 1;
    }

    private void startTurn() {
        turnLeft = TURN_SECONDS;
        charging = false;
        charge = 0;
        botWait = 30;
        render();
        Player p = current();
        if (p != null && mode != Mode.SOLO) {
            Txt.bar(p, "<#7BD88F>À toi de jouer " + (group[turn] == 1 ? "(pleines)" : group[turn] == 2 ? "(rayées)" : ""));
            Txt.sound(p, Sound.BLOCK_NOTE_BLOCK_PLING, 1.5f);
        }
    }

    /** Toutes les secondes. */
    public void tick() {
        if (mode == Mode.NONE) {
            return;
        }
        // joueur parti trop loin ou déconnecté
        for (int i = 0; i < 2; i++) {
            if (players[i] == null) {
                continue;
            }
            Player p = Bukkit.getPlayer(players[i]);
            if (p == null || !p.getWorld().equals(world) || p.getLocation().distanceSquared(new Location(world, Salon.CX, Salon.Y, Salon.CZ)) > 20 * 20) {
                leave(players[i], p == null ? "déconnecté" : "parti trop loin");
                return;
            }
        }
        if (mode == Mode.WAITING || moving || ending >= 0 || botTurn()) {
            return;
        }
        if (--turnLeft <= 0) {
            Player p = current();
            if (mode == Mode.SOLO) {
                turnLeft = TURN_SECONDS;
                return;
            }
            if (p != null) {
                Txt.send(p, "Billard : temps écoulé, la main passe.");
            }
            turn = 1 - turn;
            startTurn();
        } else if (turnLeft <= 10) {
            Player p = current();
            if (p != null) {
                Txt.bar(p, "<#E0484A>Encore " + turnLeft + " s pour tirer");
            }
        }
    }

    /** Accroupi : charge ; relâché : tir. */
    public void sneak(Player p, boolean down) {
        if (mode == Mode.NONE || mode == Mode.WAITING || moving || ending >= 0 || !p.getUniqueId().equals(players[turn])) {
            return;
        }
        if (p.getLocation().distanceSquared(new Location(world, Salon.CX, Salon.Y, Salon.CZ)) > 7 * 7) {
            return;
        }
        if (down) {
            charging = true;
            charge = 0;
        } else if (charging) {
            charging = false;
            double power = VMAX * (0.06 + 0.94 * Math.min(1, charge / (double) CHARGE_MAX));
            Vector dir = aim(p);
            if (dir == null) {
                Txt.bar(p, "<#E0484A>Regarde le tapis pour viser.");
                return;
            }
            shoot(dir.getX() * power, dir.getZ() * power);
        }
    }

    /** Direction du tir : de la blanche vers le point du tapis regardé (sinon, droit devant). */
    private Vector aim(Player p) {
        Ball cue = balls[0];
        if (cue == null || cue.in) {
            return null;
        }
        Location eye = p.getEyeLocation();
        Vector d = eye.getDirection();
        double tx, tz;
        if (d.getY() < -0.05) {
            double t = (BALL_Y - eye.getY()) / d.getY();
            tx = eye.getX() + d.getX() * t - cue.x;
            tz = eye.getZ() + d.getZ() * t - cue.z;
        } else {
            tx = d.getX();
            tz = d.getZ();
        }
        double len = Math.hypot(tx, tz);
        if (len < 0.03) {
            tx = d.getX();
            tz = d.getZ();
            len = Math.hypot(tx, tz);
        }
        return len < 1e-6 ? null : new Vector(tx / len, 0, tz / len);
    }

    private void shoot(double vx, double vz) {
        Ball cue = balls[0];
        cue.vx = vx;
        cue.vz = vz;
        moving = true;
        firstHit = null;
        pocketed.clear();
        scratch = false;
        if (turn == 0 || mode != Mode.BOT) {
            shots++;
        }
        double speed = Math.hypot(vx, vz);
        world.playSound(new Location(world, cue.x, BALL_Y, cue.z), Sound.BLOCK_BAMBOO_WOOD_HIT, 1f, (float) (0.8 + speed));
        render();
    }

    // ------------------------------------------------------------------ physique (chaque tick)

    public void fastTick() {
        if (ending >= 0) {
            if (--ending <= 0) {
                reset();
            }
            return;
        }
        if (mode == Mode.NONE || mode == Mode.WAITING) {
            return;
        }
        if (!moving) {
            if (botTurn()) {
                if (--botWait <= 0) {
                    botShot();
                }
                return;
            }
            Player p = current();
            if (p == null) {
                return;
            }
            if (charging) {
                charge = Math.min(CHARGE_MAX, charge + 1);
                int bars = (int) Math.round(20.0 * charge / CHARGE_MAX);
                Txt.bar(p, "<#9AA4B2>Force <#7BD88F>" + "|".repeat(bars) + "<#5A6270>" + "|".repeat(20 - bars) + " <#9AA4B2>relâche pour tirer");
            }
            if (Bukkit.getCurrentTick() % 2 == 0) {
                guide(p);
            }
            return;
        }
        step();
    }

    private void guide(Player p) {
        Vector d = aim(p);
        Ball cue = balls[0];
        if (d == null || cue == null) {
            return;
        }
        Particle.DustOptions dust = new Particle.DustOptions(Color.WHITE, 0.45f);
        for (double s = 0.15; s <= 1.4; s += 0.1) {
            double x = cue.x + d.getX() * s, z = cue.z + d.getZ() * s;
            if (x < X0 || x > X1 || z < Z0 || z > Z1) {
                break;
            }
            p.spawnParticle(Particle.DUST, x, BALL_Y, z, 1, 0, 0, 0, 0, dust);
        }
    }

    private void step() {
        Location sound = null;
        float vol = 0;
        for (int s = 0; s < SUB; s++) {
            for (Ball b : balls) {
                if (b == null || b.in) {
                    continue;
                }
                b.x += b.vx / SUB;
                b.z += b.vz / SUB;
                if (inPocket(b)) {
                    pocket(b);
                    continue;
                }
                boolean hit = false;
                if (b.x < X0 + R) {
                    b.x = X0 + R;
                    b.vx = -b.vx * CUSHION;
                    hit = true;
                } else if (b.x > X1 - R) {
                    b.x = X1 - R;
                    b.vx = -b.vx * CUSHION;
                    hit = true;
                }
                if (b.z < Z0 + R) {
                    b.z = Z0 + R;
                    b.vz = -b.vz * CUSHION;
                    hit = true;
                } else if (b.z > Z1 - R) {
                    b.z = Z1 - R;
                    b.vz = -b.vz * CUSHION;
                    hit = true;
                }
                if (hit && Math.hypot(b.vx, b.vz) > 0.03) {
                    world.playSound(new Location(world, b.x, BALL_Y, b.z), Sound.BLOCK_WOOL_HIT, 0.6f, 1.2f);
                }
            }
            for (int i = 0; i < balls.length; i++) {
                Ball a = balls[i];
                if (a == null || a.in) {
                    continue;
                }
                for (int j = i + 1; j < balls.length; j++) {
                    Ball b = balls[j];
                    if (b == null || b.in) {
                        continue;
                    }
                    double dx = b.x - a.x, dz = b.z - a.z, d2 = dx * dx + dz * dz;
                    if (d2 >= 4 * R * R || d2 == 0) {
                        continue;
                    }
                    double d = Math.sqrt(d2), nx = dx / d, nz = dz / d;
                    double overlap = 2 * R - d;
                    a.x -= nx * overlap / 2;
                    a.z -= nz * overlap / 2;
                    b.x += nx * overlap / 2;
                    b.z += nz * overlap / 2;
                    double rel = (a.vx - b.vx) * nx + (a.vz - b.vz) * nz;
                    if (rel > 0) {
                        double jmp = rel * (1 + BALL_E) / 2;
                        a.vx -= jmp * nx;
                        a.vz -= jmp * nz;
                        b.vx += jmp * nx;
                        b.vz += jmp * nz;
                        if (firstHit == null && (a.n == 0 || b.n == 0)) {
                            firstHit = a.n == 0 ? b.n : a.n;
                        }
                        if (rel > vol) {
                            vol = (float) rel;
                            sound = new Location(world, (a.x + b.x) / 2, BALL_Y, (a.z + b.z) / 2);
                        }
                    }
                }
            }
        }
        if (sound != null) {
            world.playSound(sound, Sound.BLOCK_NOTE_BLOCK_HAT, Math.min(1f, 0.3f + vol * 2), 1.9f);
        }
        boolean any = false;
        for (Ball b : balls) {
            if (b == null || b.in) {
                continue;
            }
            double sp = Math.hypot(b.vx, b.vz);
            if (sp > 0) {
                double ns = sp * FRICTION - ROLL;
                if (ns <= STOP) {
                    b.vx = b.vz = 0;
                } else {
                    b.vx *= ns / sp;
                    b.vz *= ns / sp;
                    any = true;
                }
            }
            if (b.e != null && b.e.isValid()) {
                b.e.teleport(new Location(world, b.x, BALL_Y, b.z));
            }
        }
        if (!any) {
            moving = false;
            settle();
        }
    }

    private boolean inPocket(Ball b) {
        for (int k = 0; k < POCKETS.length; k++) {
            double r = k == 1 || k == 4 ? POCKET_SIDE : POCKET_CORNER;
            double dx = b.x - POCKETS[k][0], dz = b.z - POCKETS[k][1];
            if (dx * dx + dz * dz < r * r) {
                return true;
            }
        }
        return false;
    }

    private void pocket(Ball b) {
        b.in = true;
        b.vx = b.vz = 0;
        Location at = new Location(world, b.x, BALL_Y, b.z);
        world.playSound(at, Sound.ENTITY_ITEM_PICKUP, 0.8f, 0.6f);
        world.spawnParticle(Particle.CRIT, at, 6, 0.05, 0.05, 0.05, 0.05);
        if (b.e != null) {
            b.e.remove();
            b.e = null;
        }
        if (b.n == 0) {
            scratch = true;
        } else {
            pocketed.add(b.n);
        }
    }

    // ------------------------------------------------------------------ règles (après chaque coup)

    private static int kind(int n) {
        return n == 8 ? 3 : n < 8 ? 1 : 2;
    }

    private boolean cleared(int g) {
        for (int n = 1; n <= 15; n++) {
            if (n != 8 && kind(n) == g && !balls[n].in) {
                return false;
            }
        }
        return true;
    }

    private void respotCue() {
        Ball cue = balls[0];
        cue.in = false;
        cue.vx = cue.vz = 0;
        double x = HEAD_X;
        boolean free;
        do {
            free = true;
            for (int n = 1; n <= 15; n++) {
                Ball o = balls[n];
                if (!o.in && Math.hypot(o.x - x, o.z - MID_Z) < 2.2 * R) {
                    free = false;
                    x -= 2.5 * R;
                    break;
                }
            }
        } while (!free && x > X0 + R);
        cue.x = Math.max(X0 + R, x);
        cue.z = MID_Z;
        cue.e = display(cue);
    }

    private void settle() {
        if (mode == Mode.SOLO) {
            if (scratch) {
                respotCue();
                tell(players[0], "<#E0484A>Blanche rentrée : replacée.");
            }
            boolean done = true;
            for (int n = 1; n <= 15; n++) {
                done &= balls[n].in;
            }
            if (done) {
                tell(players[0], "<#7BD88F><b>Table nettoyée en " + shots + " coups !</b>");
                ending = 80;
            }
            render();
            return;
        }
        int me = turn, mine = group[me];
        boolean foul = scratch || firstHit == null || mine == 0 && firstHit == 8;
        if (!foul && mine != 0 && firstHit != null) {
            int need = cleared(mine) ? 3 : mine;
            foul = kind(firstHit) != need;
        }
        // la 8
        if (pocketed.contains(8)) {
            boolean ok = mine != 0 && !foul && clearedBefore8(mine);
            finish(ok ? me : 1 - me, ok ? names[me] + " rentre la 8" : "la 8 rentrée trop tôt par " + names[me]);
            return;
        }
        boolean own = false;
        if (!foul && mine == 0 && !pocketed.isEmpty()) {
            int g = kind(pocketed.get(0));
            group[me] = g;
            group[1 - me] = 3 - g;
            mine = g;
            say(names[me] + " prend les " + (g == 1 ? "pleines" : "rayées") + ".");
        }
        for (int n : pocketed) {
            if (kind(n) == mine) {
                own = true;
            }
        }
        if (scratch) {
            respotCue();
        }
        if (foul) {
            say("<#E0484A>Faute de " + names[me] + "</#E0484A> (" + (scratch ? "blanche rentrée" : firstHit == null ? "aucune boule touchée"
                    : "mauvaise boule touchée en premier") + ") : la main passe.");
        }
        if (foul || !own) {
            turn = 1 - turn;
        }
        startTurn();
    }

    private boolean clearedBefore8(int g) {
        return cleared(g); // la 8 compte si toutes les boules du groupe étaient rentrées (y compris pendant ce coup)
    }

    private void finish(int winner, String why) {
        String w = names[winner], l = names[1 - winner];
        say("<#E0B864><b>" + w + " gagne la partie</b></#E0B864> : " + why + ".");
        if (mode == Mode.DUEL && stake > 0) {
            plugin.wallet().credit(players[winner], stake * 2, "billard", "victoire contre " + l, true);
            tell(players[winner], "Billard : tu remportes le pot de " + Txt.oc(stake * 2) + ".");
        }
        Player wp = players[winner] == null ? null : Bukkit.getPlayer(players[winner]);
        if (wp != null) {
            Txt.sound(wp, Sound.ENTITY_PLAYER_LEVELUP, 1.2f);
        }
        stake = 0;
        ending = 80;
        render();
    }

    /** Un joueur s'en va (commande, déconnexion, trop loin) : en défi, l'autre gagne. */
    public void leave(UUID u, String why) {
        if (!isPlayer(u)) {
            return;
        }
        if (mode == Mode.WAITING) {
            cancelWaiting(why);
            return;
        }
        if (ending >= 0) {
            reset();
            return;
        }
        int i = u.equals(players[0]) ? 0 : 1;
        if (mode == Mode.DUEL) {
            say(names[i] + " abandonne (" + why + ").");
            finish(1 - i, names[i] + " abandonne");
            reset();
            return;
        }
        tell(u, "Billard : partie terminée.");
        reset();
    }

    private void say(String msg) {
        for (UUID u : players) {
            tell(u, msg);
        }
        for (Player o : world.getPlayers()) {
            if (!isPlayer(o.getUniqueId()) && o.getLocation().distanceSquared(new Location(world, Salon.CX, Salon.Y, Salon.CZ)) < 100) {
                Txt.send(o, "Billard : " + msg);
            }
        }
    }

    private void tell(UUID u, String msg) {
        Player p = u == null ? null : Bukkit.getPlayer(u);
        if (p != null) {
            Txt.send(p, "Billard : " + msg);
        }
    }

    // ------------------------------------------------------------------ bot

    private void botShot() {
        Ball cue = balls[0];
        int mine = group[1];
        int need = mine == 0 ? 0 : cleared(mine) ? 3 : mine;
        double best = -1, bx = 0, bz = 0, bp = 0.35;
        for (int n = 1; n <= 15; n++) {
            Ball b = balls[n];
            if (b.in || (need == 0 ? n == 8 : kind(n) != need)) {
                continue;
            }
            for (double[] pk : POCKETS) {
                double px = pk[0] - b.x, pz = pk[1] - b.z, pl = Math.hypot(px, pz);
                px /= pl;
                pz /= pl;
                double gx = b.x - px * 2 * R, gz = b.z - pz * 2 * R;
                double ax = gx - cue.x, az = gz - cue.z, al = Math.hypot(ax, az);
                if (al < 1e-3) {
                    continue;
                }
                double cos = (ax * px + az * pz) / al;
                if (cos < 0.25 || blocked(cue.x, cue.z, gx, gz, n) || blocked(b.x, b.z, pk[0], pk[1], n)) {
                    continue;
                }
                double score = cos / (al + pl);
                if (score > best) {
                    best = score;
                    bx = ax / al;
                    bz = az / al;
                    bp = Math.min(VMAX, 0.18 + 0.1 * (al + pl) / cos);
                }
            }
        }
        if (best < 0) { // rien de propre : on vise la boule la plus proche
            double dmin = 1e9;
            for (int n = 1; n <= 15; n++) {
                Ball b = balls[n];
                if (b.in || (need == 0 ? n == 8 : kind(n) != need)) {
                    continue;
                }
                double dx = b.x - cue.x, dz = b.z - cue.z, d = Math.hypot(dx, dz);
                if (d < dmin) {
                    dmin = d;
                    bx = dx / d;
                    bz = dz / d;
                }
            }
            bp = 0.4;
        }
        double err = Math.toRadians((Cards.RANDOM.nextDouble() - 0.5) * 3.0);
        double c = Math.cos(err), s = Math.sin(err);
        shoot((bx * c - bz * s) * bp, (bx * s + bz * c) * bp);
    }

    private boolean blocked(double x1, double z1, double x2, double z2, int except) {
        double dx = x2 - x1, dz = z2 - z1, l2 = dx * dx + dz * dz;
        for (int n = 1; n <= 15; n++) {
            Ball o = balls[n];
            if (n == except || o.in) {
                continue;
            }
            double t = l2 == 0 ? 0 : Math.max(0, Math.min(1, ((o.x - x1) * dx + (o.z - z1) * dz) / l2));
            double cx = x1 + dx * t - o.x, cz = z1 + dz * t - o.z;
            if (cx * cx + cz * cz < 4 * R * R) {
                return true;
            }
        }
        return false;
    }

    public void shutdown() {
        if (mode == Mode.WAITING && stake > 0) {
            plugin.wallet().cashOutNow(players[0], stake, "billard");
        } else if (mode == Mode.DUEL && stake > 0 && ending < 0) {
            plugin.wallet().cashOutNow(players[0], stake, "billard");
            plugin.wallet().cashOutNow(players[1], stake, "billard");
        }
        stake = 0;
        reset();
    }
}
