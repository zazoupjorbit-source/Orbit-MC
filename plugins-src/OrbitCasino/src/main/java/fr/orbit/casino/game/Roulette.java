package fr.orbit.casino.game;

import fr.orbit.casino.Holo;
import fr.orbit.casino.Menu;
import fr.orbit.casino.OrbitCasino;
import fr.orbit.casino.Room;
import fr.orbit.casino.Txt;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Roulette européenne (un seul zéro), ouverte à tous en même temps : la première mise lance le compte à
 * rebours, puis « rien ne va plus », la roue tourne et chacun est payé. Les mises se posent dans un menu.
 */
public final class Roulette {

    private enum State { IDLE, BETTING, SPINNING }

    private static final Set<Integer> RED = Set.of(1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36);

    /** Mises extérieures : clé, nom, case du menu, multiplicateur (mise comprise). */
    private record Outside(String key, String label, Material icon, int slot, int pays) {
    }

    private static final List<Outside> OUTSIDE = List.of(
            new Outside("rouge", "Rouge", Material.RED_WOOL, 37, 2),
            new Outside("noir", "Noir", Material.BLACK_WOOL, 38, 2),
            new Outside("pair", "Pair", Material.WHITE_WOOL, 39, 2),
            new Outside("impair", "Impair", Material.GRAY_WOOL, 40, 2),
            new Outside("manque", "Manque (1-18)", Material.LIGHT_BLUE_WOOL, 41, 2),
            new Outside("passe", "Passe (19-36)", Material.BLUE_WOOL, 42, 2),
            new Outside("d1", "1re douzaine (1-12)", Material.LIME_WOOL, 43, 3),
            new Outside("d2", "2e douzaine (13-24)", Material.LIME_WOOL, 44, 3),
            new Outside("d3", "3e douzaine (25-36)", Material.LIME_WOOL, 45, 3),
            new Outside("c1", "Colonne 1 (1, 4, 7…)", Material.ORANGE_WOOL, 46, 3),
            new Outside("c2", "Colonne 2 (2, 5, 8…)", Material.ORANGE_WOOL, 47, 3),
            new Outside("c3", "Colonne 3 (3, 6, 9…)", Material.ORANGE_WOOL, 48, 3));

    private final OrbitCasino plugin;
    private final World world;
    private final Map<UUID, Map<String, Long>> bets = new LinkedHashMap<>();
    private final Map<UUID, String> names = new HashMap<>();
    private final Map<UUID, Long> chip = new HashMap<>();
    private final Map<UUID, Menu> open = new HashMap<>();
    private final List<Long> chips = new ArrayList<>();
    private final Deque<Integer> history = new ArrayDeque<>();
    private State state = State.IDLE;
    private int countdown;
    private TextDisplay wheel;

    public Roulette(OrbitCasino plugin, World world) {
        this.plugin = plugin;
        this.world = world;
        for (Object o : plugin.getConfig().getList("roulette.jetons", List.of(10, 50, 100, 500, 1000))) {
            chips.add(Long.parseLong(String.valueOf(o)));
        }
    }

    public void spawn() {
        Holo.npc(new Location(world, 0.5, Room.Y, Room.RL_Z1 - 0.5, 0f, 0f), "<#E0B864>Croupier · Roulette", Villager.Profession.LIBRARIAN);
        wheel = Holo.text(new Location(world, 0.5, Room.Y + 2.4, -0.5), "", 1.1f, false);
        render(null);
    }

    private static String color(int n) {
        return n == 0 ? "#7BD88F" : RED.contains(n) ? "#E0484A" : "#F2F4F7";
    }

    private static String num(int n) {
        return "<" + color(n) + "><b>" + n + "</b></" + color(n) + ">";
    }

    private void render(String middle) {
        StringBuilder h = new StringBuilder();
        for (int n : history) {
            h.append(num(n)).append(' ');
        }
        String status = middle != null ? middle : switch (state) {
            case IDLE -> "<#9AA4B2>Clic sur la table pour miser";
            case BETTING -> "<#62CFF4>Faites vos jeux : " + countdown + " s";
            case SPINNING -> "<#E0484A>Rien ne va plus";
        };
        Holo.set(wheel, "<#E0B864><b>ROULETTE</b></#E0B864>\n" + status + (h.isEmpty() ? "" : "\n<#5A6270>Derniers : " + h.toString().trim()));
    }

    // ------------------------------------------------------------------ menu des mises

    public void open(Player p) {
        Menu m = new Menu(6, "<#E0B864><b>Roulette</b></#E0B864> <#5A6270>— gauche : miser · droite : retirer");
        UUID u = p.getUniqueId();
        long c = chip.getOrDefault(u, chips.get(0));
        Map<String, Long> mine = bets.getOrDefault(u, Map.of());
        for (int n = 0; n <= 36; n++) {
            int number = n;
            Material mat = n == 0 ? Material.LIME_CONCRETE : RED.contains(n) ? Material.RED_CONCRETE : Material.BLACK_CONCRETE;
            ItemStack it = Menu.item(mat, num(n), "<#9AA4B2>Plein : payé 35 contre 1", betLine(mine.get("n" + n)));
            it.setAmount(Math.max(1, n));
            m.set(n, it, e -> place(p, "n" + number, e.getClick()));
        }
        for (Outside o : OUTSIDE) {
            m.set(o.slot(), Menu.item(o.icon(), "<#E6EDF3><b>" + o.label(), "<#9AA4B2>Payé " + (o.pays() - 1) + " contre 1",
                    betLine(mine.get(o.key()))), e -> place(p, o.key(), e.getClick()));
        }
        for (int k = 0; k < chips.size() && k < 5; k++) {
            long v = chips.get(k);
            m.set(49 + k, Menu.item(v == c ? Material.GOLD_BLOCK : Material.GOLD_NUGGET, "<#E0B864><b>Jeton " + Txt.n(v) + " OC",
                    v == c ? "<#7BD88F>✔ Jeton choisi" : "<#9AA4B2>Clique pour le choisir"), e -> {
                chip.put(u, v);
                open(p);
            });
        }
        long total = mine.values().stream().mapToLong(Long::longValue).sum();
        m.set(36, Menu.item(Material.SUNFLOWER, "<#E0B864><b>Tes mises : " + Txt.n(total) + " OC",
                "<#9AA4B2>Solde : " + Txt.oc(Math.max(0, plugin.wallet().balance(p))),
                state == State.BETTING ? "<#62CFF4>La roue tourne dans " + countdown + " s" : state == State.SPINNING
                        ? "<#E0484A>Rien ne va plus" : "<#9AA4B2>Ta première mise lance le compte à rebours"));
        m.onClose(() -> open.remove(u, m));
        open.put(u, m);
        m.open(p);
    }

    private static String betLine(Long amount) {
        return amount == null ? "<#5A6270>Pas de mise" : "<#E0B864>Ta mise : " + Txt.n(amount) + " OC";
    }

    private void place(Player p, String key, ClickType click) {
        UUID u = p.getUniqueId();
        if (state == State.SPINNING) {
            Txt.bar(p, "<#E0484A>Rien ne va plus : attends le prochain tour.");
            return;
        }
        Map<String, Long> mine = bets.computeIfAbsent(u, k -> new LinkedHashMap<>());
        if (click.isRightClick()) {
            Long had = mine.remove(key);
            if (had != null) {
                plugin.wallet().credit(u, had, "roulette", "mise retirée", false);
                Txt.sound(p, Sound.BLOCK_CHAIN_BREAK, 1.3f);
            }
            if (mine.isEmpty()) {
                bets.remove(u);
            }
            open(p);
            return;
        }
        long c = chip.getOrDefault(u, chips.get(0));
        long total = mine.values().stream().mapToLong(Long::longValue).sum();
        long max = plugin.getConfig().getLong("roulette.mise-max", 5000);
        if (total + c > max) {
            Txt.bar(p, "<#E0484A>Mises limitées à " + Txt.n(max) + " OC par tour.");
            return;
        }
        names.put(u, p.getName());
        mine.merge(key, c, Long::sum); // réservé tout de suite, annulé si le retrait échoue
        plugin.wallet().debit(p, c, "roulette", key, ok -> {
            if (!ok || state == State.SPINNING) {
                Map<String, Long> now = bets.get(u);
                if (now != null) {
                    now.computeIfPresent(key, (k, v) -> v - c <= 0 ? null : v - c);
                    if (now.isEmpty()) {
                        bets.remove(u);
                    }
                }
                if (ok) {
                    plugin.wallet().credit(u, c, "roulette", "trop tard", false);
                }
                Txt.bar(p, "<#E0484A>" + (ok ? "Trop tard pour ce tour." : "Pas assez d'Orbit Coins."));
            } else {
                Txt.sound(p, Sound.BLOCK_CHAIN_PLACE, 1.5f);
                if (state == State.IDLE) {
                    state = State.BETTING;
                    countdown = plugin.getConfig().getInt("roulette.attente-mises", 30);
                    for (Player o : world.getPlayers()) {
                        if (o.getLocation().distanceSquared(new Location(world, 0, Room.Y, -1)) < 400) {
                            Txt.send(o, "Roulette : <b>faites vos jeux</b>, la roue tourne dans " + countdown + " s.");
                        }
                    }
                }
                render(null);
            }
            if (open.containsKey(u) && p.isOnline()) {
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
        countdown--;
        if (countdown == 5) {
            for (UUID u : bets.keySet()) {
                Player p = Bukkit.getPlayer(u);
                if (p != null) {
                    Txt.bar(p, "<#E0B864>La roue tourne dans 5 s !");
                }
            }
        }
        if (countdown <= 0) {
            spin();
        } else {
            render(null);
        }
    }

    private void spin() {
        state = State.SPINNING;
        for (UUID u : new ArrayList<>(open.keySet())) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) {
                p.closeInventory();
            }
        }
        int result = Cards.RANDOM.nextInt(37);
        Location at = new Location(world, 0.5, Room.Y + 1, -0.5);
        world.playSound(at, Sound.BLOCK_GRINDSTONE_USE, 1f, 1.4f);
        new BukkitRunnable() {
            int step = 0, wait = 0, delay = 1;

            @Override
            public void run() {
                if (++wait < delay) {
                    return;
                }
                wait = 0;
                step++;
                if (step % 4 == 0) {
                    delay++;
                }
                if (delay >= 7) {
                    cancel();
                    land(result, at);
                    return;
                }
                int shown = Cards.RANDOM.nextInt(37);
                render("<#E0484A>Rien ne va plus\n<#5A6270>» " + num(shown) + " <#5A6270>«");
                world.playSound(at, Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 2f - delay * 0.15f);
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void land(int n, Location at) {
        history.addFirst(n);
        while (history.size() > 8) {
            history.removeLast();
        }
        world.playSound(at, Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1f);
        world.spawnParticle(Particle.FIREWORK, at.clone().add(0, 1.5, 0), 30, 0.3, 0.3, 0.3, 0.05);
        String label = n == 0 ? "ZÉRO" : (RED.contains(n) ? "Rouge" : "Noir") + ", " + (n % 2 == 0 ? "pair" : "impair")
                + ", " + (n <= 18 ? "manque" : "passe");
        render("<#9AA4B2>Sorti : " + num(n) + " <#9AA4B2>" + label);
        for (Map.Entry<UUID, Map<String, Long>> e : bets.entrySet()) {
            UUID u = e.getKey();
            long staked = 0, won = 0;
            for (Map.Entry<String, Long> b : e.getValue().entrySet()) {
                staked += b.getValue();
                won += b.getValue() * pays(b.getKey(), n);
            }
            if (won > 0) {
                plugin.wallet().credit(u, won, "roulette", "sorti " + n, won > staked);
            }
            Player p = Bukkit.getPlayer(u);
            if (p != null) {
                long net = won - staked;
                Txt.send(p, "Roulette : " + num(n) + " <#9AA4B2>(" + label + ")</#9AA4B2> · misé " + Txt.n(staked) + " OC · "
                        + (net > 0 ? "<#7BD88F><b>+" + Txt.n(net) + " OC</b>" : net == 0 ? "<#9AA4B2>±0" : "<#E0484A>" + Txt.n(net) + " OC"));
                Txt.sound(p, net > 0 ? Sound.ENTITY_PLAYER_LEVELUP : Sound.BLOCK_NOTE_BLOCK_BASS, net > 0 ? 1.4f : 0.8f);
                if (won >= 5000) {
                    for (Player o : world.getPlayers()) {
                        Txt.send(o, "<#E0B864>" + names.getOrDefault(u, "?") + "</#E0B864> gagne " + Txt.oc(won) + " à la roulette !");
                    }
                }
            }
        }
        bets.clear();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            state = State.IDLE;
            render(null);
        }, 120L);
    }

    /** Multiplicateur (mise comprise) de la mise {@code key} quand {@code n} sort. */
    static long pays(String key, int n) {
        if (key.startsWith("n")) {
            return Integer.parseInt(key.substring(1)) == n ? 36 : 0;
        }
        if (n == 0) {
            return 0;
        }
        boolean win = switch (key) {
            case "rouge" -> RED.contains(n);
            case "noir" -> !RED.contains(n);
            case "pair" -> n % 2 == 0;
            case "impair" -> n % 2 == 1;
            case "manque" -> n <= 18;
            case "passe" -> n >= 19;
            case "d1" -> n <= 12;
            case "d2" -> n >= 13 && n <= 24;
            case "d3" -> n >= 25;
            case "c1" -> n % 3 == 1;
            case "c2" -> n % 3 == 2;
            case "c3" -> n % 3 == 0;
            default -> false;
        };
        if (!win) {
            return 0;
        }
        return key.startsWith("d") || key.startsWith("c") ? 3 : 2;
    }

    /** Arrêt du serveur : mises du tour en cours rendues. */
    public void shutdown() {
        for (Map.Entry<UUID, Map<String, Long>> e : bets.entrySet()) {
            long total = e.getValue().values().stream().mapToLong(Long::longValue).sum();
            if (total > 0) {
                plugin.wallet().cashOutNow(e.getKey(), total, "roulette");
            }
        }
        bets.clear();
    }
}
