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

    // Cases : les mêmes que le tapis dessiné par tools/CasinoPackGen.java (rouletteBackground).
    // 0-35 = numéros 1 à 36, 36 = zéro, 37-48 ci-dessous, 49-53 = jetons.
    private static final List<Outside> OUTSIDE = List.of(
            new Outside("d1", "1re douzaine (1-12)", Material.LIME_WOOL, 37, 3),
            new Outside("d2", "2e douzaine (13-24)", Material.LIME_WOOL, 38, 3),
            new Outside("d3", "3e douzaine (25-36)", Material.LIME_WOOL, 39, 3),
            new Outside("c1", "Colonne 1 (1, 4, 7… 34)", Material.ORANGE_WOOL, 40, 3),
            new Outside("c2", "Colonne 2 (2, 5, 8… 35)", Material.ORANGE_WOOL, 41, 3),
            new Outside("c3", "Colonne 3 (3, 6, 9… 36)", Material.ORANGE_WOOL, 42, 3),
            new Outside("manque", "Manque (1-18)", Material.LIGHT_BLUE_WOOL, 43, 2),
            new Outside("passe", "Passe (19-36)", Material.BLUE_WOOL, 44, 2),
            new Outside("rouge", "Rouge", Material.RED_WOOL, 45, 2),
            new Outside("noir", "Noir", Material.BLACK_WOOL, 46, 2),
            new Outside("pair", "Pair", Material.WHITE_WOOL, 47, 2),
            new Outside("impair", "Impair", Material.GRAY_WOOL, 48, 2));

    private static int numberSlot(int n) {
        return n == 0 ? 36 : n - 1;
    }

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

    /** Ouvre la roulette : l'écran HD si le joueur a le pack du casino, sinon le menu coffre. */
    public void open(Player p) {
        if (plugin.screens() != null && plugin.screens().available(p)) {
            viewers.add(p.getUniqueId());
            screen(p, true);
            return;
        }
        Menu m = new Menu(6, "<#E0B864><b>Roulette</b></#E0B864> <#5A6270>— mises : " + Txt.n(totalOf(p.getUniqueId()))
                + " OC · gauche : miser · droite : retirer");
        UUID u = p.getUniqueId();
        long c = chip.getOrDefault(u, chips.get(0));
        Map<String, Long> mine = bets.getOrDefault(u, Map.of());
        for (int n = 0; n <= 36; n++) {
            int number = n;
            Material mat = n == 0 ? Material.LIME_CONCRETE : RED.contains(n) ? Material.RED_CONCRETE : Material.BLACK_CONCRETE;
            ItemStack it = Menu.item(mat, num(n), "<#9AA4B2>Plein : payé 35 contre 1", betLine(mine.get("n" + n)));
            it.setAmount(Math.max(1, n));
            m.set(numberSlot(n), it, e -> place(p, "n" + number, e.getClick().isRightClick()));
        }
        for (Outside o : OUTSIDE) {
            m.set(o.slot(), Menu.item(o.icon(), "<#E6EDF3><b>" + o.label(), "<#9AA4B2>Payé " + (o.pays() - 1) + " contre 1",
                    betLine(mine.get(o.key()))), e -> place(p, o.key(), e.getClick().isRightClick()));
        }
        for (int k = 0; k < chips.size() && k < 5; k++) {
            long v = chips.get(k);
            m.set(49 + k, Menu.item(v == c ? Material.GOLD_BLOCK : Material.GOLD_NUGGET, "<#E0B864><b>Jeton " + Txt.n(v) + " OC",
                    v == c ? "<#7BD88F>✔ Jeton choisi" : "<#9AA4B2>Clique pour le choisir"), e -> {
                chip.put(u, v);
                open(p);
            });
        }
        m.onClose(() -> open.remove(u, m));
        open.put(u, m);
        m.open(p);
    }

    private long totalOf(UUID u) {
        Map<String, Long> mine = bets.get(u);
        return mine == null ? 0 : mine.values().stream().mapToLong(Long::longValue).sum();
    }

    /** Rafraîchit ce que le joueur voit (écran HD ou menu), s'il l'a encore ouvert. */
    private void refresh(Player p) {
        UUID u = p.getUniqueId();
        if (viewers.contains(u)) {
            if (plugin.screens() != null && plugin.screens().isOpen(p, "roulette")) {
                screen(p, false);
            } else {
                viewers.remove(u);
            }
        } else if (open.containsKey(u) && p.isOnline()) {
            open(p);
        }
    }

    private void refreshViewers() {
        for (UUID u : new ArrayList<>(viewers)) {
            Player p = Bukkit.getPlayer(u);
            if (p == null) {
                viewers.remove(u);
            } else {
                refresh(p);
            }
        }
    }

    // ------------------------------------------------------------------ écran HD

    private static final int GOLD = 0xE0B864, LIGHT = 0xE6EDF3, GREY = 0x9AA4B2, RED_TXT = 0xE0484A, GREEN = 0x7BD88F, ICE = 0x62CFF4;
    private final Set<UUID> viewers = new java.util.HashSet<>();
    private int ball = -1;          // numéro sous la bille (pendant et juste après le tour)
    private int landed = -1;        // numéro sorti, affiché jusqu'au tour suivant
    private final Map<UUID, Long> lastNet = new HashMap<>();

    private static String chipModel(long amount) {
        long[] values = {1000, 500, 250, 100, 50, 10};
        for (long v : values) {
            if (amount >= v) {
                return "chip_" + v;
            }
        }
        return "chip_10";
    }

    private static String describe(int n) {
        return n == 0 ? "Zéro" : (RED.contains(n) ? "Rouge" : "Noir") + ", " + (n % 2 == 0 ? "pair" : "impair") + ", "
                + (n <= 18 ? "manque" : "passe") + ", " + (n <= 12 ? "1re" : n <= 24 ? "2e" : "3e") + " douzaine, colonne "
                + (n % 3 == 0 ? 3 : n % 3);
    }

    private void screen(Player p, boolean openIt) {
        fr.orbit.casino.hud.Screens screens = plugin.screens();
        if (screens == null || !screens.available(p)) {
            return;
        }
        UUID u = p.getUniqueId();
        long c = chip.getOrDefault(u, chips.get(0));
        Map<String, Long> mine = bets.getOrDefault(u, Map.of());
        long total = totalOf(u);
        fr.orbit.casino.hud.Screens.Frame f = new fr.orbit.casino.hud.Screens.Frame("roulette");

        // titre : état de la table à gauche, mises ou gain à droite
        String left;
        int leftColor = GOLD;
        String right;
        int rightColor = LIGHT;
        if (state == State.SPINNING && landed < 0) {
            left = "RIEN NE VA PLUS";
            leftColor = RED_TXT;
            right = total > 0 ? "MISE " + Txt.n(total) : "";
        } else if (landed >= 0) {
            left = "SORTI " + landed + " " + (landed == 0 ? "ZÉRO" : RED.contains(landed) ? "ROUGE" : "NOIR");
            leftColor = landed == 0 ? GREEN : RED.contains(landed) ? RED_TXT : LIGHT;
            Long net = lastNet.get(u);
            if (net == null) {
                right = Txt.n(Math.max(0, plugin.wallet().balance(p))) + " OC";
            } else {
                right = net > 0 ? "+" + Txt.n(net) + " OC" : net == 0 ? "0 OC" : Txt.n(net) + " OC";
                rightColor = net > 0 ? GREEN : net == 0 ? GREY : RED_TXT;
            }
        } else if (state == State.BETTING) {
            left = "ROULETTE · " + countdown + " S";
            leftColor = countdown <= 5 ? RED_TXT : ICE;
            right = total > 0 ? "MISE " + Txt.n(total) : Txt.n(Math.max(0, plugin.wallet().balance(p))) + " OC";
        } else {
            left = "ROULETTE · FAITES VOS JEUX";
            right = Txt.n(Math.max(0, plugin.wallet().balance(p))) + " OC";
        }
        f.title(fr.orbit.casino.hud.Hud.title().background(fr.orbit.casino.hud.Hud.BG_ROULETTE)
                .text(0, 8, fr.orbit.casino.hud.Hud.Align.LEFT, fr.orbit.casino.hud.Hud.fit(left, 100), leftColor)
                .text(0, 168, fr.orbit.casino.hud.Hud.Align.RIGHT, fr.orbit.casino.hud.Hud.fit(right, 62), rightColor)
                .build());

        String help = state == State.SPINNING ? "<#E0484A>Rien ne va plus"
                : "<#5A6270>Gauche : +" + Txt.n(c) + " OC · droite : retirer";
        for (int n = 0; n <= 36; n++) {
            String key = "n" + n;
            Long bet = mine.get(key);
            String model = n == ball ? "rl_ball" : bet != null ? "rl_" + chipModel(bet) : "invisible";
            String name = num(n) + " <#9AA4B2>· " + describe(n);
            String[] lore = {"<#9AA4B2>Plein : payé 35 contre 1", betLine(bet), help};
            cellItem(f, p, numberSlot(n), key, model, name, lore);
        }
        for (Outside o : OUTSIDE) {
            Long bet = mine.get(o.key());
            String model = bet != null ? "rl_" + chipModel(bet) : "invisible";
            String[] lore = {"<#9AA4B2>Payé " + (o.pays() - 1) + " contre 1 · le zéro fait perdre", betLine(bet), help};
            cellItem(f, p, o.slot(), o.key(), model, "<#E6EDF3><b>" + o.label(), lore);
        }

        StringBuilder last = new StringBuilder();
        for (int n : history) {
            last.append(num(n)).append(' ');
        }
        for (int k = 0; k < chips.size() && k < 5; k++) {
            long v = chips.get(k);
            boolean chosen = v == c;
            String model = "chip_" + v + (chosen ? "" : "_g");
            String name = "<#E0B864><b>Jeton " + Txt.n(v) + " OC";
            String[] lore = {chosen ? "<#7BD88F>✔ Jeton choisi" : "<#9AA4B2>Clique pour le choisir",
                    "<#9AA4B2>Tes mises : " + Txt.n(total) + " OC · max " + Txt.n(plugin.getConfig().getLong("roulette.mise-max", 5000)),
                    last.isEmpty() ? "<#5A6270>Aucun numéro sorti pour l'instant" : "<#9AA4B2>Derniers : " + last.toString().trim()};
            f.item(49 + k, fr.orbit.casino.hud.Screens.model(model, name, lore), model + "|" + String.join("|", lore), () -> {
                chip.put(u, v);
                refresh(p);
            });
        }
        screens.show(p, f, openIt);
    }

    private void cellItem(fr.orbit.casino.hud.Screens.Frame f, Player p, int slot, String key, String model, String name, String[] lore) {
        f.item(slot, fr.orbit.casino.hud.Screens.model(model, name, lore), model + "|" + name + "|" + String.join("|", lore),
                () -> place(p, key, false), () -> place(p, key, true));
    }

    private static String betLine(Long amount) {
        return amount == null ? "<#5A6270>Pas de mise" : "<#E0B864>Ta mise : " + Txt.n(amount) + " OC";
    }

    private void place(Player p, String key, boolean remove) {
        UUID u = p.getUniqueId();
        if (state == State.SPINNING) {
            Txt.bar(p, "<#E0484A>Rien ne va plus : attends le prochain tour.");
            return;
        }
        if (landed >= 0) { // nouvelle mise après un tour : on efface l'affichage du résultat
            landed = -1;
            ball = -1;
            lastNet.clear();
        }
        Map<String, Long> mine = bets.computeIfAbsent(u, k -> new LinkedHashMap<>());
        if (remove) {
            Long had = mine.remove(key);
            if (had != null) {
                plugin.wallet().credit(u, had, "roulette", "mise retirée", false);
                Txt.sound(p, Sound.BLOCK_CHAIN_BREAK, 1.3f);
            }
            if (mine.isEmpty()) {
                bets.remove(u);
            }
            refresh(p);
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
            if (p.isOnline()) {
                refresh(p);
            }
            refreshViewers();
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
            refreshViewers();
        }
    }

    private void spin() {
        state = State.SPINNING;
        landed = -1;
        lastNet.clear();
        // l'ancien menu coffre se ferme ; l'écran HD reste ouvert pour voir la bille
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
                ball = shown;
                refreshViewers();
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
            lastNet.put(u, won - staked);
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
        ball = n;
        landed = n;
        refreshViewers();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            state = State.IDLE;
            render(null);
            refreshViewers(); // le résultat reste affiché jusqu'à la prochaine mise
        }, 120L);
    }

    /** Multiplicateur (mise comprise) de la mise {@code key} quand {@code n} sort. */
    static long pays(String key, int n) {
        if (key.matches("n\\d+")) { // « noir » commence aussi par n
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
