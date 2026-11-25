package fr.orbit.casino.jeux;

import fr.orbit.casino.Holo;
import fr.orbit.casino.Menu;
import fr.orbit.casino.OrbitCasino;
import fr.orbit.casino.Txt;
import fr.orbit.casino.game.Cards;
import org.bukkit.Bukkit;
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
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Roue des Étoiles : grande roue de 25 cases sur le mur est du salon. On mise sur une couleur (×2, ×4, ×8, ×12,
 * ORBIT, COMÈTE) ; la première mise lance un compte à rebours, puis la roue tourne et ralentit (SecureRandom).
 * Chaque mise rapporte en moyenne 96 % : 12 cases ×2, 6 ×4, 3 ×8, 2 ×12, 1 ORBIT ×24, 1 COMÈTE ×24.
 */
public final class Roue {

    /** Même ordre que la texture (tools/CasinoPackGen.java, WHEEL) : case 0 en haut, puis dans le sens des aiguilles. */
    static final String[] SEG = {"x2", "x4", "x2", "x8", "x2", "x4", "x2", "x12", "x2", "x4", "x2", "orbit", "x2", "x8", "x4",
            "x2", "x12", "x2", "x4", "x2", "x8", "x4", "x2", "comete", "x2"};

    private record Option(String key, String label, String color, Material icon, int pays, int slot) {
    }

    private static final List<Option> OPTIONS = List.of(
            new Option("x2", "×2", "#3A7FFF", Material.BLUE_WOOL, 2, 10),
            new Option("x4", "×4", "#62CFF4", Material.CYAN_WOOL, 4, 11),
            new Option("x8", "×8", "#C79BFF", Material.PURPLE_WOOL, 8, 12),
            new Option("x12", "×12", "#E0B864", Material.YELLOW_WOOL, 12, 14),
            new Option("orbit", "ORBIT ×24", "#E0484A", Material.RED_WOOL, 24, 15),
            new Option("comete", "COMÈTE ×24", "#7BD88F", Material.LIME_WOOL, 24, 16));

    private enum State { IDLE, BETTING, SPINNING }

    private final OrbitCasino plugin;
    private final World world;
    private final long[] chips = {10, 50, 100, 500, 1000};
    private final long maxBet;
    private final int waitSeconds;
    private final int sens;
    private final Map<UUID, Map<String, Long>> bets = new LinkedHashMap<>();
    private final Map<UUID, Long> chip = new HashMap<>();
    private final Map<UUID, Menu> open = new HashMap<>();
    private final Deque<String> history = new ArrayDeque<>();
    private State state = State.IDLE;
    private int countdown;
    private ItemDisplay wheel;
    private TextDisplay sign;
    private double angle;   // degrés, sens inverse des aiguilles vu de face
    private UUID npc;

    public Roue(OrbitCasino plugin, World world) {
        this.plugin = plugin;
        this.world = world;
        this.maxBet = plugin.getConfig().getLong("roue.mise-max", 2000);
        this.waitSeconds = plugin.getConfig().getInt("roue.attente-mises", 20);
        this.sens = plugin.getConfig().getInt("roue.sens", 1) >= 0 ? 1 : -1;
    }

    public static double rtp() {
        double total = 0;
        for (Option o : OPTIONS) {
            int n = 0;
            for (String s : SEG) {
                if (s.equals(o.key)) {
                    n++;
                }
            }
            total += (double) n / SEG.length * o.pays;
        }
        return total / OPTIONS.size();
    }

    public void spawn() {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta meta = it.getItemMeta();
        meta.setItemModel(new NamespacedKey("orbitcasino", "wheel"));
        it.setItemMeta(meta);
        // la roue regarde vers l'ouest (yaw 90), comme un panneau posé sur le mur est
        wheel = world.spawn(new Location(world, Salon.WHEEL_X, Salon.WHEEL_Y, Salon.WHEEL_Z, 90f, 0f), ItemDisplay.class, d -> {
            Holo.tag(d);
            d.setItemStack(it);
            d.setBillboard(Display.Billboard.FIXED);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setViewRange(2f);
            d.setTransformation(transform(angle));
        });
        Holo.text(new Location(world, Salon.WHEEL_X - 0.12, Salon.WHEEL_Y + 3.45, Salon.WHEEL_Z, 90f, 0f), "<#E0B864><b>▼</b>", 2.4f, true);
        sign = Holo.text(new Location(world, Salon.WHEEL_X - 0.15, Salon.WHEEL_Y + 4.5, Salon.WHEEL_Z, 90f, 0f), "", 1.1f, true);
        npc = Holo.npc(new Location(world, 57.5, Salon.Y, 4.5, 90f, 0f), "<#E0B864>Animatrice · Roue des Étoiles",
                Villager.Profession.CLERIC).getUniqueId();
        render(null);
    }

    public boolean isNpc(UUID id) {
        return id.equals(npc);
    }

    private Transformation transform(double deg) {
        return new Transformation(new Vector3f(), new AxisAngle4f((float) Math.toRadians(deg * sens), 0, 0, 1),
                new Vector3f(7f, 7f, 7f), new AxisAngle4f());
    }

    private static Option option(String key) {
        for (Option o : OPTIONS) {
            if (o.key.equals(key)) {
                return o;
            }
        }
        return OPTIONS.get(0);
    }

    private static String label(String key) {
        Option o = option(key);
        return "<" + o.color + "><b>" + o.label + "</b></" + o.color + ">";
    }

    private void render(String middle) {
        StringBuilder h = new StringBuilder();
        for (String s : history) {
            h.append(label(s)).append(' ');
        }
        String status = middle != null ? middle : switch (state) {
            case IDLE -> "<#9AA4B2>Parle à l'animatrice pour miser";
            case BETTING -> "<#62CFF4>Faites vos jeux : " + countdown + " s";
            case SPINNING -> "<#E0484A>La roue tourne…";
        };
        Holo.set(sign, "<gradient:#E0B864:#62CFF4><b>ROUE DES ÉTOILES</b></gradient> <#5A6270>· "
                + Math.round(rtp() * 100) + " % redistribués\n" + status + (h.isEmpty() ? "" : "\n<#5A6270>Derniers : " + h.toString().trim()));
    }

    // ------------------------------------------------------------------ mises

    private long totalOf(UUID u) {
        Map<String, Long> m = bets.get(u);
        return m == null ? 0 : m.values().stream().mapToLong(Long::longValue).sum();
    }

    public void open(Player p) {
        UUID u = p.getUniqueId();
        long c = chip.getOrDefault(u, chips[0]);
        Map<String, Long> mine = bets.getOrDefault(u, Map.of());
        Menu m = new Menu(4, "<#E0B864><b>Roue des Étoiles</b></#E0B864> <#5A6270>— mises : " + Txt.n(totalOf(u)) + " OC");
        for (Option o : OPTIONS) {
            int n = 0;
            for (String s : SEG) {
                if (s.equals(o.key)) {
                    n++;
                }
            }
            Long b = mine.get(o.key);
            ItemStack it = Menu.item(o.icon, "<" + o.color + "><b>" + o.label, "<#9AA4B2>" + n + " case" + (n > 1 ? "s" : "") + " sur 25 · paie "
                    + o.pays + " fois la mise", b == null ? "<#5A6270>Pas de mise" : "<#E0B864>Ta mise : " + Txt.n(b) + " OC",
                    state == State.SPINNING ? "<#E0484A>La roue tourne" : "<#5A6270>Gauche : +" + Txt.n(c) + " OC · droite : retirer");
            it.setAmount(Math.max(1, Math.min(64, o.pays)));
            m.set(o.slot, it, e -> place(p, o.key, e.getClick().isRightClick()));
        }
        for (int k = 0; k < chips.length; k++) {
            long v = chips[k];
            m.set(29 + k - 1, Menu.item(v == c ? Material.GOLD_BLOCK : Material.GOLD_NUGGET, "<#E0B864><b>Jeton " + Txt.n(v) + " OC",
                    v == c ? "<#7BD88F>✔ Jeton choisi" : "<#9AA4B2>Clique pour le choisir"), e -> {
                chip.put(u, v);
                open(p);
            });
        }
        StringBuilder h = new StringBuilder();
        for (String s : history) {
            h.append(label(s)).append(' ');
        }
        m.set(34, Menu.item(Material.CLOCK, "<#62CFF4><b>" + switch (state) {
                    case IDLE -> "La roue attend une mise";
                    case BETTING -> "Elle tourne dans " + countdown + " s";
                    case SPINNING -> "Elle tourne !";
                }, "<#9AA4B2>Mises jusqu'à " + Txt.n(maxBet) + " OC par tour", "<#9AA4B2>" + Math.round(rtp() * 100) + " % redistribués",
                h.isEmpty() ? "<#5A6270>Pas encore de tirage" : "<#9AA4B2>Derniers : " + h.toString().trim()));
        m.set(35, Menu.item(Material.BARRIER, "<#9AA4B2>Fermer"), e -> p.closeInventory());
        m.fill(Material.BLACK_STAINED_GLASS_PANE);
        m.onClose(() -> open.remove(u, m));
        open.put(u, m);
        m.open(p);
    }

    private void place(Player p, String key, boolean remove) {
        UUID u = p.getUniqueId();
        if (state == State.SPINNING) {
            Txt.bar(p, "<#E0484A>La roue tourne : attends le prochain tour.");
            return;
        }
        Map<String, Long> mine = bets.computeIfAbsent(u, k -> new LinkedHashMap<>());
        if (remove) {
            Long had = mine.remove(key);
            if (had != null) {
                plugin.wallet().credit(u, had, "roue", "mise retirée", false);
            }
            if (mine.isEmpty()) {
                bets.remove(u);
            }
            open(p);
            return;
        }
        long c = chip.getOrDefault(u, chips[0]);
        if (totalOf(u) + c > maxBet) {
            Txt.bar(p, "<#E0484A>Mises limitées à " + Txt.n(maxBet) + " OC par tour.");
            return;
        }
        mine.merge(key, c, Long::sum);
        plugin.wallet().debit(p, c, "roue", key, ok -> {
            if (!ok || state == State.SPINNING) {
                Map<String, Long> now = bets.get(u);
                if (now != null) {
                    now.computeIfPresent(key, (k, v) -> v - c <= 0 ? null : v - c);
                    if (now.isEmpty()) {
                        bets.remove(u);
                    }
                }
                if (ok) {
                    plugin.wallet().credit(u, c, "roue", "trop tard", false);
                }
                Txt.bar(p, "<#E0484A>" + (ok ? "Trop tard pour ce tour." : "Pas assez d'Orbit Coins."));
            } else {
                Txt.sound(p, Sound.BLOCK_CHAIN_PLACE, 1.5f);
                if (state == State.IDLE) {
                    state = State.BETTING;
                    countdown = waitSeconds;
                    for (Player o : world.getPlayers()) {
                        if (o.getLocation().distanceSquared(wheel.getLocation()) < 900) {
                            Txt.send(o, "Roue des Étoiles : <b>faites vos jeux</b>, elle tourne dans " + countdown + " s.");
                        }
                    }
                }
                render(null);
            }
            if (p.isOnline() && open.containsKey(u)) {
                open(p);
            }
        });
    }

    // ------------------------------------------------------------------ tour de roue

    public void tick() {
        if (state != State.BETTING) {
            return;
        }
        if (bets.isEmpty()) {
            state = State.IDLE;
            render(null);
            return;
        }
        if (--countdown <= 0) {
            spin();
        } else {
            render(null);
        }
    }

    private void spin() {
        state = State.SPINNING;
        render(null);
        for (UUID u : new ArrayList<>(open.keySet())) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) {
                p.closeInventory();
            }
        }
        int result = Cards.RANDOM.nextInt(SEG.length);
        double step = 360.0 / SEG.length;
        double target = result * step + (Cards.RANDOM.nextDouble() - 0.5) * step * 0.7;
        double start = angle;
        double delta = 360 * 5 + Math.floorMod((long) Math.round((target - start) * 1000), 360_000) / 1000.0;
        int duration = 140;
        Location at = new Location(world, Salon.WHEEL_X - 1, Salon.WHEEL_Y, Salon.WHEEL_Z);
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            int t = (int) Math.min(duration, (Bukkit.getCurrentTick() - startTick[0]));
            double u = t / (double) duration;
            double a = start + delta * (1 - Math.pow(1 - u, 3));
            if ((int) (a / step) != (int) (angle / step)) {
                world.playSound(at, Sound.BLOCK_NOTE_BLOCK_HAT, 0.7f, 1.6f);
            }
            angle = a;
            if (wheel != null && wheel.isValid()) {
                wheel.setInterpolationDelay(0);
                wheel.setInterpolationDuration(2);
                wheel.setTransformation(transform(angle));
            }
            if (t >= duration) {
                task.cancel();
                angle = Math.floorMod((long) Math.round(angle * 1000), 360_000) / 1000.0;
                land(result, at);
            }
        }, 1L, 2L);
        startTick[0] = Bukkit.getCurrentTick();
        world.playSound(at, Sound.BLOCK_GRINDSTONE_USE, 1f, 1.2f);
    }

    private final int[] startTick = new int[1];

    private void land(int index, Location at) {
        String key = SEG[index];
        Option o = option(key);
        history.addFirst(key);
        while (history.size() > 8) {
            history.removeLast();
        }
        world.playSound(at, Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1f);
        world.spawnParticle(Particle.FIREWORK, at.clone().add(-0.5, 3.5, 0), 40, 0.5, 0.5, 0.5, 0.06);
        render("<#9AA4B2>Sorti : " + label(key));
        for (Map.Entry<UUID, Map<String, Long>> e : bets.entrySet()) {
            UUID u = e.getKey();
            long staked = 0, won = 0;
            for (Map.Entry<String, Long> b : e.getValue().entrySet()) {
                staked += b.getValue();
                if (b.getKey().equals(key)) {
                    won += b.getValue() * o.pays;
                }
            }
            if (won > 0) {
                plugin.wallet().credit(u, won, "roue", "sorti " + key, won > staked);
            }
            Player p = Bukkit.getPlayer(u);
            if (p != null) {
                long net = won - staked;
                Txt.send(p, "Roue des Étoiles : " + label(key) + " · misé " + Txt.n(staked) + " OC · "
                        + (net > 0 ? "<#7BD88F><b>+" + Txt.n(net) + " OC</b>" : net == 0 ? "<#9AA4B2>±0" : "<#E0484A>" + Txt.n(net) + " OC"));
                Txt.sound(p, net > 0 ? Sound.ENTITY_PLAYER_LEVELUP : Sound.BLOCK_NOTE_BLOCK_BASS, net > 0 ? 1.4f : 0.8f);
                if (won >= 5000) {
                    for (Player other : world.getPlayers()) {
                        Txt.send(other, "<#E0B864>" + p.getName() + "</#E0B864> gagne " + Txt.oc(won) + " à la Roue des Étoiles !");
                    }
                }
            }
        }
        bets.clear();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            state = State.IDLE;
            render(null);
        }, 80L);
    }

    public void shutdown() {
        for (Map.Entry<UUID, Map<String, Long>> e : bets.entrySet()) {
            long total = e.getValue().values().stream().mapToLong(Long::longValue).sum();
            if (total > 0) {
                plugin.wallet().cashOutNow(e.getKey(), total, "roue");
            }
        }
        bets.clear();
    }
}
