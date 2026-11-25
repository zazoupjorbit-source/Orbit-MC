package fr.orbit.casino.jeux;

import fr.orbit.casino.Holo;
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
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Plinko : planche de 8 rangées de picots contre le mur nord du salon. Chaque bille (ItemDisplay) rebondit de picot
 * en picot, à gauche ou à droite à pile ou face (SecureRandom), et tombe dans une des 9 cases du bas.
 * Gains (mise comprise) : ×11, ×3, ×1,3, ×0,7, ×0,4, ×0,7, ×1,3, ×3, ×11 → 97,3 % redistribués.
 */
public final class Plinko {

    static final double[] MULTS = {11, 3, 1.3, 0.7, 0.4, 0.7, 1.3, 3, 11};
    private static final int ROWS = 8;
    private static final double S = 0.9, DY = 0.75, TOP = 74.3, PEG_Z = Salon.Z1 + 1.0, BALL_Z = PEG_Z + 0.27, CONTACT = 0.21;
    private static final long[] STEPS = {10, 50, 100, 250, 500};
    private static final int SEG = 5;

    private final class Ball {
        final UUID owner;
        final String name;
        final long bet;
        final boolean[] right = new boolean[ROWS];
        final ItemDisplay e;
        int seg;
        int t;
        int k;
        int linger = -1;

        Ball(Player p, long bet) {
            this.owner = p.getUniqueId();
            this.name = p.getName();
            this.bet = bet;
            for (int i = 0; i < ROWS; i++) {
                right[i] = Cards.RANDOM.nextBoolean();
            }
            ItemStack it = new ItemStack(Material.SNOWBALL);
            ItemMeta meta = it.getItemMeta();
            meta.setItemModel(new NamespacedKey("orbitcasino", "pk_ball"));
            it.setItemMeta(meta);
            e = world.spawn(new Location(world, Salon.CX, TOP + 1.0, BALL_Z, 0f, 0f), ItemDisplay.class, d -> {
                Holo.tag(d);
                d.setItemStack(it);
                d.setBillboard(Display.Billboard.CENTER);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setTeleportDuration(1);
                d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.32f, 0.32f, 0.32f), new AxisAngle4f()));
            });
        }
    }

    private final OrbitCasino plugin;
    private final World world;
    private final List<Ball> balls = new ArrayList<>();
    private final Map<UUID, Integer> step = new HashMap<>();
    private final List<TextDisplay> bins = new ArrayList<>();

    public Plinko(OrbitCasino plugin, World world) {
        this.plugin = plugin;
        this.world = world;
    }

    public static double rtp() {
        double ev = 0;
        for (int k = 0; k <= ROWS; k++) {
            ev += binom(ROWS, k) / Math.pow(2, ROWS) * MULTS[k];
        }
        return ev;
    }

    private static double binom(int n, int k) {
        double r = 1;
        for (int i = 1; i <= k; i++) {
            r = r * (n - k + i) / i;
        }
        return r;
    }

    private static double pegX(int row, double k) {
        return Salon.CX + (k - row / 2.0) * S;
    }

    private static double pegY(int row) {
        return TOP - row * DY;
    }

    public void spawn() {
        balls.clear();
        bins.clear();
        for (int i = 0; i < ROWS; i++) {
            for (int j = 0; j <= i + 2; j++) {
                double px = Salon.CX + (j - (i + 2) / 2.0) * S, py = pegY(i);
                world.spawn(new Location(world, px, py, PEG_Z), BlockDisplay.class, d -> {
                    Holo.tag(d);
                    d.setBlock(Bukkit.createBlockData("minecraft:end_rod[facing=south]"));
                    d.setBrightness(new Display.Brightness(15, 15));
                    d.setTransformation(new Transformation(new Vector3f(-0.5f, -0.5f, 0f), new AxisAngle4f(),
                            new Vector3f(1f, 1f, 0.55f), new AxisAngle4f()));
                });
            }
        }
        double binY = pegY(ROWS - 1) - DY;
        for (int k = 0; k <= ROWS; k++) {
            double m = MULTS[k];
            String c = m >= 10 ? "#E0B864" : m >= 3 ? "#7BD88F" : m >= 1 ? "#62CFF4" : "#E0484A";
            bins.add(Holo.text(new Location(world, Salon.CX + (k - ROWS / 2.0) * S, binY - 0.75, PEG_Z + 0.05, 0f, 0f),
                    "<" + c + "><b>×" + fmt(m) + "</b>", 0.55f, true));
        }
        Holo.text(new Location(world, Salon.CX, Salon.PK_Y2 + 0.15, PEG_Z + 0.05, 0f, 0f),
                "<gradient:#62CFF4:#E0B864><b>PLINKO</b></gradient> <#5A6270>· " + String.format(Locale.ROOT, "%.1f", rtp() * 100)
                        .replace('.', ',') + " % redistribués", 0.9f, true);
        Holo.text(new Location(world, Salon.CX + 0.5, Salon.Y + 1.6, Salon.PK_COUNTER_Z + 0.5, 180f, 0f),
                "<#E6EDF3>Clic droit : lâcher une bille\n<#9AA4B2>Accroupi + clic : changer la mise", 0.7f, false);
    }

    private static String fmt(double m) {
        return m == Math.floor(m) ? String.valueOf((long) m) : String.valueOf(m).replace('.', ',');
    }

    public void click(Player p) {
        UUID u = p.getUniqueId();
        if (p.isSneaking()) {
            step.put(u, (step.getOrDefault(u, 0) + 1) % STEPS.length);
            Txt.bar(p, "<#9AA4B2>Mise Plinko : " + Txt.oc(STEPS[step.get(u)]) + " par bille");
            Txt.sound(p, Sound.UI_BUTTON_CLICK, 1.6f);
            return;
        }
        long mine = balls.stream().filter(b -> b.owner.equals(u) && b.linger < 0).count();
        if (mine >= 3 || balls.size() >= 24) {
            Txt.bar(p, "<#E0484A>Attends que tes billes arrivent (3 à la fois).");
            return;
        }
        long bet = STEPS[step.getOrDefault(u, 0)];
        plugin.wallet().debit(p, bet, "plinko", "bille", ok -> {
            if (!ok) {
                Txt.bar(p, "<#E0484A>Pas assez d'Orbit Coins.");
                return;
            }
            balls.add(new Ball(p, bet));
            world.playSound(new Location(world, Salon.CX, TOP, BALL_Z), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.6f);
            Txt.bar(p, "<#9AA4B2>Bille lâchée · " + Txt.oc(bet) + " <#5A6270>(accroupi + clic : changer la mise)");
        });
    }

    /** Chaque tick : chaque bille avance d'une étape de son rebond. */
    public void fastTick() {
        for (Iterator<Ball> it = balls.iterator(); it.hasNext(); ) {
            Ball b = it.next();
            if (!b.e.isValid()) {
                it.remove();
                continue;
            }
            if (b.linger >= 0) {
                if (--b.linger <= 0) {
                    b.e.remove();
                    it.remove();
                }
                continue;
            }
            b.t++;
            double u = Math.min(1, b.t / (double) SEG);
            double x0, y0, x1, y1, hop;
            if (b.seg == 0) {
                x0 = Salon.CX;
                y0 = TOP + 1.0;
                x1 = Salon.CX;
                y1 = pegY(0) + CONTACT;
                hop = 0;
                u = u * u;
            } else {
                int row = b.seg - 1; // on part du picot de cette rangée
                double kNow = b.k;
                double kNext = b.k + (b.right[row] ? 1 : 0);
                x0 = pegX(row, kNow);
                y0 = pegY(row) + CONTACT;
                if (row + 1 < ROWS) {
                    x1 = pegX(row + 1, kNext);
                    y1 = pegY(row + 1) + CONTACT;
                } else {
                    x1 = Salon.CX + (kNext - ROWS / 2.0) * S;
                    y1 = pegY(ROWS - 1) - DY + 0.05;
                }
                hop = 0.28;
            }
            double x = x0 + (x1 - x0) * u;
            double y = y0 + (y1 - y0) * u + hop * 4 * u * (1 - u);
            b.e.teleport(new Location(world, x, y, BALL_Z));
            if (b.t >= SEG) {
                if (b.seg > 0) {
                    b.k += b.right[b.seg - 1] ? 1 : 0;
                }
                b.seg++;
                b.t = 0;
                Location at = new Location(world, x, y, BALL_Z);
                if (b.seg <= ROWS) {
                    world.playSound(at, Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.0f + b.seg * 0.1f);
                } else {
                    land(b, at);
                }
            }
        }
    }

    private void land(Ball b, Location at) {
        double m = MULTS[b.k];
        long won = (long) Math.floor(b.bet * m);
        if (won > 0) {
            plugin.wallet().credit(b.owner, won, "plinko", "×" + fmt(m), won > b.bet);
        }
        b.linger = 30;
        b.e.setGlowing(true);
        b.e.setGlowColorOverride(m >= 3 ? Color.fromRGB(0xE0B864) : m >= 1 ? Color.fromRGB(0x62CFF4) : Color.fromRGB(0xE0484A));
        world.spawnParticle(m >= 1 ? Particle.HAPPY_VILLAGER : Particle.SMOKE, at, 8, 0.15, 0.1, 0.05, 0.01);
        world.playSound(at, m >= 3 ? Sound.ENTITY_PLAYER_LEVELUP : m >= 1 ? Sound.BLOCK_NOTE_BLOCK_BELL : Sound.BLOCK_NOTE_BLOCK_BASS,
                0.7f, m >= 3 ? 1.4f : 1f);
        Player p = Bukkit.getPlayer(b.owner);
        if (p != null) {
            Txt.bar(p, (won >= b.bet ? "<#7BD88F>" : "<#E0484A>") + "Plinko ×" + fmt(m) + " : " + Txt.n(won) + " OC <#5A6270>(mise " + Txt.n(b.bet) + ")");
        }
        if (m >= 11 && b.bet >= 100) {
            for (Player o : world.getPlayers()) {
                Txt.send(o, "<#E0B864>" + b.name + "</#E0B864> touche le ×11 au Plinko : " + Txt.oc(won) + " !");
            }
        }
    }

    /** Arrêt du serveur : billes en route payées comme si elles tombaient au milieu (mise rendue). */
    public void shutdown() {
        for (Ball b : balls) {
            if (b.linger < 0) {
                plugin.wallet().cashOutNow(b.owner, b.bet, "plinko");
            }
            b.e.remove();
        }
        balls.clear();
    }
}
