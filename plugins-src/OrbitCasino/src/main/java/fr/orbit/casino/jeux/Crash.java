package fr.orbit.casino.jeux;

import fr.orbit.casino.Holo;
import fr.orbit.casino.OrbitCasino;
import fr.orbit.casino.Txt;
import fr.orbit.casino.game.Cards;
import fr.orbit.casino.hud.Hud;
import fr.orbit.casino.hud.Screens;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Crash : une fusée ORBIT décolle, le multiplicateur monte (×e^(0,08 t)) et on encaisse avant l'explosion.
 * Tour commun à tous : la première mise lance le compte à rebours. Le point d'explosion est tiré au décollage
 * (SecureRandom) : P(atteindre ×m) = 0,97 / m, soit 97 % redistribués quelle que soit la façon de jouer.
 */
public final class Crash {

    private enum State { IDLE, BETTING, FLYING }

    private static final long[] STEPS = {10, 50, 100, 250, 500, 1000};
    private static final double[] AUTOS = {0, 1.5, 2, 3, 5, 10, 25};
    private static final double GROWTH = 0.08;
    private static final double[] ROWS = {6.5, 4, 2.6, 1.6};
    private static final int GOLD = 0xE0B864, LIGHT = 0xE6EDF3, GREY = 0x9AA4B2, RED = 0xE0484A, GREEN = 0x7BD88F, ICE = 0x62CFF4;

    private static final class Bet {
        final String name;
        final long amount;
        final double auto;
        double cashed; // 0 = encore en vol
        long paid;

        Bet(String name, long amount, double auto) {
            this.name = name;
            this.amount = amount;
            this.auto = auto;
        }
    }

    private final OrbitCasino plugin;
    private final World world;
    private final double edge;
    private final double cap;
    private final int waitSeconds;
    private final Map<UUID, Bet> bets = new LinkedHashMap<>();
    private final Map<UUID, Bet> last = new HashMap<>();
    private final Map<UUID, Integer> step = new HashMap<>();
    private final Map<UUID, Integer> auto = new HashMap<>();
    private final Set<UUID> viewers = new HashSet<>();
    private final Deque<Double> history = new ArrayDeque<>();
    private final List<Integer> trail = new ArrayList<>();
    private State state = State.IDLE;
    private int countdown;
    private int ticks;
    private double mult = 1;
    private double crashAt;
    private double lastCrash = -1;
    private int rocket = 36;
    private TextDisplay sign;

    public Crash(OrbitCasino plugin, World world) {
        this.plugin = plugin;
        this.world = world;
        this.edge = plugin.getConfig().getDouble("crash.redistribution", 0.97);
        this.cap = plugin.getConfig().getDouble("crash.plafond", 100);
        this.waitSeconds = plugin.getConfig().getInt("crash.attente-mises", 10);
    }

    public double rtp() {
        return edge;
    }

    private Location borne() {
        return new Location(world, Salon.CRASH_X + 0.5, Salon.Y, Salon.BORNE_Z - 0.5);
    }

    public void spawn() {
        sign = Holo.text(new Location(world, Salon.CRASH_X + 0.5, Salon.Y + 3.4, Salon.BORNE_Z + 0.4, 180f, 0f), "", 1.0f, true);
        Holo.text(new Location(world, Salon.CRASH_X + 0.5, Salon.Y + 1.5, Salon.BORNE_Z - 0.52, 180f, 0f),
                "<#62CFF4><b>▲</b>", 1.6f, true);
        render();
    }

    private void render() {
        StringBuilder h = new StringBuilder();
        for (double d : history) {
            h.append(d >= 2 ? "<#7BD88F>" : "<#E0484A>").append(x(d)).append("</#").append(d >= 2 ? "7BD88F>" : "E0484A>").append(' ');
        }
        String status = switch (state) {
            case IDLE -> lastCrash > 0 ? "<#E0484A>Boum à " + x(lastCrash) + "</#E0484A>\n<#9AA4B2>Clic droit pour miser" : "<#9AA4B2>Clic droit pour miser";
            case BETTING -> "<#62CFF4>Décollage dans " + countdown + " s";
            case FLYING -> "<#E0B864><b>" + x(mult) + "</b>";
        };
        Holo.set(sign, "<gradient:#62CFF4:#3A7FFF><b>CRASH</b></gradient> <#5A6270>· " + Math.round(edge * 100) + " % redistribués\n" + status
                + (h.isEmpty() ? "" : "\n<#5A6270>Derniers : " + h.toString().trim()));
    }

    static String x(double m) {
        return String.format(Locale.ROOT, "×%.2f", m);
    }

    private long betOf(UUID u) {
        return STEPS[step.getOrDefault(u, 0)];
    }

    private double autoOf(UUID u) {
        return AUTOS[auto.getOrDefault(u, 0)];
    }

    // ------------------------------------------------------------------ joueur

    public void open(Player p) {
        if (plugin.screens() != null && plugin.screens().available(p)) {
            viewers.add(p.getUniqueId());
            screen(p, true);
            return;
        }
        // sans le pack du casino : boutons du chat
        Txt.send(p, "<gradient:#62CFF4:#3A7FFF><b>CRASH</b></gradient> · mise " + Txt.oc(betOf(p.getUniqueId())) + " · auto "
                + (autoOf(p.getUniqueId()) == 0 ? "non" : x(autoOf(p.getUniqueId()))));
        p.sendMessage(Txt.mm("  ").append(Txt.button("Miser", "#7BD88F", "/cj crash miser", "Miser pour le prochain décollage"))
                .append(Txt.mm(" ")).append(Txt.button("Encaisser", "#E0B864", "/cj crash encaisser", "Encaisser pendant le vol"))
                .append(Txt.mm(" ")).append(Txt.button("Mise +", "#62CFF4", "/cj crash plus", "Mise suivante"))
                .append(Txt.mm(" ")).append(Txt.button("Auto", "#3A7FFF", "/cj crash auto", "Encaissement automatique")));
    }

    public void command(Player p, String action) {
        UUID u = p.getUniqueId();
        switch (action) {
            case "miser" -> bet(p);
            case "encaisser" -> cashOut(p);
            case "plus" -> {
                step.put(u, (step.getOrDefault(u, 0) + 1) % STEPS.length);
                Txt.bar(p, "<#9AA4B2>Mise Crash : " + Txt.oc(betOf(u)));
            }
            case "auto" -> {
                auto.put(u, (auto.getOrDefault(u, 0) + 1) % AUTOS.length);
                Txt.bar(p, "<#9AA4B2>Encaissement auto : " + (autoOf(u) == 0 ? "non" : x(autoOf(u))));
            }
            default -> open(p);
        }
    }

    private void bet(Player p) {
        UUID u = p.getUniqueId();
        if (state == State.FLYING) {
            Txt.bar(p, "<#E0484A>La fusée est en vol : mise pour le prochain décollage.");
            return;
        }
        if (bets.containsKey(u)) {
            Txt.bar(p, "<#9AA4B2>Ta mise est déjà posée.");
            return;
        }
        long amount = betOf(u);
        double a = autoOf(u);
        bets.put(u, new Bet(p.getName(), amount, a)); // réservée, retirée si le retrait échoue
        plugin.wallet().debit(p, amount, "crash", "mise", ok -> {
            if (!ok || state == State.FLYING) {
                bets.remove(u);
                if (ok) {
                    plugin.wallet().credit(u, amount, "crash", "trop tard", false);
                }
                Txt.bar(p, "<#E0484A>" + (ok ? "Trop tard pour ce décollage." : "Pas assez d'Orbit Coins."));
            } else {
                last.remove(u);
                Txt.sound(p, Sound.BLOCK_CHAIN_PLACE, 1.5f);
                if (state == State.IDLE) {
                    state = State.BETTING;
                    countdown = waitSeconds;
                    trail.clear();
                    rocket = 36;
                    for (Player o : world.getPlayers()) {
                        if (o.getLocation().distanceSquared(borne()) < 400) {
                            Txt.send(o, "Crash : la fusée décolle dans " + countdown + " s. <#9AA4B2>Clic droit sur la borne pour miser.");
                        }
                    }
                }
            }
            render();
            refreshViewers();
        });
    }

    private void cashOut(Player p) {
        Bet b = bets.get(p.getUniqueId());
        if (state != State.FLYING || b == null || b.cashed > 0) {
            Txt.bar(p, "<#9AA4B2>Rien à encaisser.");
            return;
        }
        pay(p.getUniqueId(), b, mult);
        refreshViewers();
    }

    private void pay(UUID u, Bet b, double at) {
        b.cashed = at;
        b.paid = (long) Math.floor(b.amount * at);
        plugin.wallet().credit(u, b.paid, "crash", "encaissé " + x(at), b.paid > b.amount);
        Player p = Bukkit.getPlayer(u);
        if (p != null) {
            Txt.sound(p, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.2f);
            Txt.bar(p, "<#7BD88F>Encaissé à " + x(at) + " : +" + Txt.n(b.paid - b.amount) + " OC");
        }
        if (b.paid >= 5000) {
            for (Player o : world.getPlayers()) {
                Txt.send(o, "<#E0B864>" + b.name + "</#E0B864> encaisse " + Txt.oc(b.paid) + " au Crash (" + x(at) + ") !");
            }
        }
    }

    // ------------------------------------------------------------------ déroulé

    /** Toutes les secondes. */
    public void tick() {
        if (state != State.BETTING) {
            return;
        }
        if (bets.isEmpty()) {
            state = State.IDLE;
            render();
            return;
        }
        if (--countdown <= 0) {
            launch();
        } else {
            render();
            refreshViewers();
        }
    }

    private void launch() {
        state = State.FLYING;
        ticks = 0;
        mult = 1;
        trail.clear();
        rocket = 36;
        double u = Cards.RANDOM.nextDouble();
        crashAt = Math.floor(100 * edge / (1 - u)) / 100.0;
        if (crashAt < 1) {
            crashAt = 1;
        }
        world.playSound(borne(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.2f, 0.8f);
        for (UUID v : viewers) {
            Player p = Bukkit.getPlayer(v);
            if (p != null) {
                p.playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.8f, 0.9f);
            }
        }
        if (crashAt <= 1.0) {
            explode();
            return;
        }
        render();
        refreshViewers();
    }

    /** Chaque tick. */
    public void fastTick() {
        if (state != State.FLYING) {
            return;
        }
        ticks++;
        if (ticks % 2 != 0) {
            return;
        }
        double next = Math.exp(GROWTH * ticks / 20.0);
        double limit = Math.min(Math.min(next, crashAt), cap);
        for (Map.Entry<UUID, Bet> e : bets.entrySet()) {
            Bet b = e.getValue();
            if (b.cashed == 0 && b.auto > 0 && b.auto <= limit) {
                pay(e.getKey(), b, b.auto);
            }
        }
        if (next >= cap && crashAt >= cap) {
            mult = cap;
            for (Map.Entry<UUID, Bet> e : bets.entrySet()) {
                if (e.getValue().cashed == 0) {
                    pay(e.getKey(), e.getValue(), cap); // plafond atteint : encaissé d'office
                }
            }
            explode();
            return;
        }
        if (next >= crashAt) {
            mult = crashAt;
            explode();
            return;
        }
        mult = next;
        int col = Math.min(8, ticks / 50);
        int row = 4;
        for (int r = 0; r < ROWS.length; r++) {
            if (mult >= ROWS[r]) {
                row = r;
                break;
            }
        }
        int slot = row * 9 + col;
        if (slot != rocket) {
            if (!trail.contains(rocket)) {
                trail.add(rocket);
            }
            rocket = slot;
        }
        if (ticks % 4 == 0) {
            render();
            refreshViewers();
            if (ticks % 20 == 0) {
                for (UUID v : viewers) {
                    Player p = Bukkit.getPlayer(v);
                    if (p != null) {
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.4f, (float) Math.min(2, 0.8 + mult / 5));
                    }
                }
            }
        }
    }

    private void explode() {
        state = State.IDLE;
        lastCrash = mult = crashAt >= cap ? cap : crashAt;
        history.addFirst(lastCrash);
        while (history.size() > 6) {
            history.removeLast();
        }
        Location at = borne().add(0, 2.5, 0);
        world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.2f);
        world.spawnParticle(Particle.FIREWORK, at, 40, 0.4, 0.4, 0.4, 0.08);
        for (Map.Entry<UUID, Bet> e : bets.entrySet()) {
            Bet b = e.getValue();
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) {
                continue;
            }
            if (b.cashed > 0) {
                Txt.send(p, "Crash : boum à " + x(lastCrash) + ". Tu avais encaissé à " + x(b.cashed) + " : <#7BD88F><b>+"
                        + Txt.n(b.paid - b.amount) + " OC</b>");
            } else {
                Txt.send(p, "Crash : boum à " + x(lastCrash) + " ! <#E0484A>" + Txt.n(-b.amount) + " OC");
                Txt.sound(p, Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f);
            }
        }
        last.clear();
        last.putAll(bets);
        bets.clear();
        render();
        refreshViewers();
    }

    // ------------------------------------------------------------------ écran HD

    private void refreshViewers() {
        if (plugin.screens() == null) {
            return;
        }
        for (UUID v : new ArrayList<>(viewers)) {
            Player p = Bukkit.getPlayer(v);
            if (p == null || !plugin.screens().isOpen(p, "crash")) {
                viewers.remove(v);
            } else {
                screen(p, false);
            }
        }
        // sans écran : la barre d'action des joueurs en vol
        if (state == State.FLYING) {
            for (Map.Entry<UUID, Bet> e : bets.entrySet()) {
                Player p = Bukkit.getPlayer(e.getKey());
                if (p != null && !viewers.contains(e.getKey()) && e.getValue().cashed == 0) {
                    Txt.bar(p, "<#E0B864><b>" + x(mult) + "</b> <#9AA4B2>· mise " + Txt.n(e.getValue().amount) + " OC · /cj crash encaisser");
                }
            }
        }
    }

    private void screen(Player p, boolean open) {
        Screens screens = plugin.screens();
        if (screens == null || !screens.available(p)) {
            return;
        }
        UUID u = p.getUniqueId();
        Bet mine = bets.get(u);
        Bet before = mine == null ? last.get(u) : null;
        Screens.Frame f = new Screens.Frame("crash");
        String left;
        int leftColor = GOLD;
        switch (state) {
            case BETTING -> {
                left = "CRASH · DÉCOLLAGE DANS " + countdown + " S";
                leftColor = countdown <= 3 ? RED : ICE;
            }
            case FLYING -> left = "CRASH · EN VOL";
            default -> {
                left = lastCrash > 0 ? "CRASH · BOUM À " + x(lastCrash) : "CRASH · MISE POUR DÉCOLLER";
                leftColor = lastCrash > 0 ? RED : GOLD;
            }
        }
        String right = Txt.n(Math.max(0, plugin.wallet().balance(p))) + " OC";
        Hud.Builder t = Hud.title().background(Hud.BG_CRASH)
                .text(0, 8, Hud.Align.LEFT, Hud.fit(left, 104), leftColor)
                .text(0, 168, Hud.Align.RIGHT, Hud.fit(right, 52), LIGHT);
        if (state == State.FLYING) {
            t.text(1, 80, Hud.Align.CENTER, x(mult), mine != null && mine.cashed > 0 ? GREEN : GOLD);
        } else if (lastCrash > 0) {
            t.text(1, 80, Hud.Align.CENTER, x(lastCrash), RED);
        }
        String status;
        int sc = LIGHT;
        if (mine != null && mine.cashed > 0) {
            status = "ENCAISSÉ " + x(mine.cashed) + " → +" + Txt.n(mine.paid - mine.amount) + " OC";
            sc = GREEN;
        } else if (mine != null) {
            status = "MISE " + Txt.n(mine.amount) + " OC" + (mine.auto > 0 ? " · AUTO " + x(mine.auto) : "")
                    + (state == State.FLYING ? " · VAUT " + Txt.n((long) Math.floor(mine.amount * mult)) : "");
        } else if (before != null) {
            status = before.cashed > 0 ? "DERNIER VOL : +" + Txt.n(before.paid - before.amount) + " OC" : "DERNIER VOL : " + Txt.n(-before.amount) + " OC";
            sc = before.cashed > 0 ? GREEN : RED;
        } else {
            status = state == State.FLYING ? "MISE POUR LE PROCHAIN VOL" : "CHOISIS TA MISE PUIS MISER";
            sc = GREY;
        }
        t.text(3, 8, Hud.Align.LEFT, Hud.fit(status, 150), sc);
        f.title(t.build());

        boolean showFlight = state == State.FLYING || lastCrash > 0 && state == State.IDLE;
        if (showFlight) {
            for (int s : trail) {
                f.item(s, Screens.model("crash_trail", null), "crash_trail", null);
            }
            boolean boom = state != State.FLYING;
            f.item(rocket, Screens.model(boom ? "crash_boom" : "crash_rocket", boom ? "<#E0484A>Boum à " + x(lastCrash) : "<#E0B864>" + x(mult)),
                    (boom ? "crash_boom" : "crash_rocket") + "|" + rocket, null);
        } else {
            f.item(36, Screens.model("crash_rocket", "<#62CFF4>Prête au décollage"), "crash_rocket|36", null);
        }

        long bet = betOf(u);
        boolean canChange = mine == null && state != State.FLYING;
        f.button(45, "btn_moins", false, () -> {
            if (canChange) {
                step.put(u, Math.max(0, step.getOrDefault(u, 0) - 1));
                screen(p, false);
            }
        }, "<#62CFF4>Mise −");
        f.item(46, Screens.model("chip_" + bet, "<#E0B864><b>Mise : " + Txt.n(bet) + " OC", "<#9AA4B2>De 10 à 1 000 OC par vol"),
                "chip|" + bet, null);
        f.button(47, "btn_plus", false, () -> {
            if (canChange) {
                step.put(u, Math.min(STEPS.length - 1, step.getOrDefault(u, 0) + 1));
                screen(p, false);
            }
        }, "<#62CFF4>Mise +");
        if (state == State.FLYING && mine != null && mine.cashed == 0) {
            f.button(48, "btn_encaisser", true, () -> cashOut(p), "<#E0B864><b>Encaisser à " + x(mult),
                    "<#7BD88F>" + Txt.n((long) Math.floor(mine.amount * mult)) + " OC");
        } else if (mine == null && state != State.FLYING) {
            f.button(48, "btn_miser", true, () -> bet(p), "<#7BD88F><b>Miser " + Txt.n(bet) + " OC",
                    "<#9AA4B2>Le décollage part " + waitSeconds + " s après la première mise");
        } else {
            f.button(48, mine == null ? "btn_miser_off" : "btn_encaisser_off", true, null,
                    mine == null ? "<#9AA4B2>En vol : attends le prochain décollage" : "<#9AA4B2>Déjà encaissé");
        }
        if (state == State.BETTING) {
            f.timer(50, countdown);
        }
        double a = autoOf(u);
        f.item(51, Screens.model("btn_auto", "<#62CFF4><b>Encaissement auto : " + (a == 0 ? "non" : x(a)),
                        "<#9AA4B2>Gauche : suivant · droite : précédent", "<#5A6270>Compte pour la prochaine mise"),
                "auto|" + a, () -> cycleAuto(p, 1), () -> cycleAuto(p, -1));
        f.item(52, Screens.model("invisible", "<#62CFF4><b>Encaissement auto : " + (a == 0 ? "non" : x(a))), "auto2|" + a,
                () -> cycleAuto(p, 1), () -> cycleAuto(p, -1));
        StringBuilder h = new StringBuilder();
        for (double d : history) {
            h.append(x(d)).append("  ");
        }
        f.button(53, "btn_quitter", false, () -> plugin.screens().close(p), "<#9AA4B2>Fermer",
                "<#5A6270>Ta mise reste en jeu", h.isEmpty() ? "<#5A6270>Pas encore de vol" : "<#9AA4B2>Derniers : " + h.toString().trim(),
                "<#5A6270>" + Math.round(edge * 100) + " % redistribués · plafond " + x(cap));
        screens.show(p, f, open);
    }

    private void cycleAuto(Player p, int d) {
        UUID u = p.getUniqueId();
        auto.put(u, Math.floorMod(auto.getOrDefault(u, 0) + d, AUTOS.length));
        screen(p, false);
    }

    /** Arrêt du serveur : mises encore en vol rendues. */
    public void shutdown() {
        for (Map.Entry<UUID, Bet> e : bets.entrySet()) {
            if (e.getValue().cashed == 0) {
                plugin.wallet().cashOutNow(e.getKey(), e.getValue().amount, "crash");
            }
        }
        bets.clear();
        state = State.IDLE;
    }
}
