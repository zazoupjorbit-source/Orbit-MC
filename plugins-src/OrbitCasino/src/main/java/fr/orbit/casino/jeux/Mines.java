package fr.orbit.casino.jeux;

import fr.orbit.casino.Holo;
import fr.orbit.casino.OrbitCasino;
import fr.orbit.casino.Txt;
import fr.orbit.casino.game.Cards;
import fr.orbit.casino.hud.Hud;
import fr.orbit.casino.hud.Screens;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mines : grille de 5 x 5, de 1 à 24 mines cachées (placées au départ, SecureRandom). Chaque case sûre fait monter
 * le multiplicateur ; on encaisse quand on veut, une mine fait tout perdre.
 * Multiplicateur après k cases sûres = 0,97 × C(25, k) / C(25 − mines, k) : 97 % redistribués quelle que soit la façon de jouer.
 */
public final class Mines {

    private static final long[] STEPS = {10, 50, 100, 250, 500, 1000};
    private static final int[] COUNTS = {1, 2, 3, 5, 8, 10, 15, 20, 24};
    private static final int GOLD = 0xE0B864, LIGHT = 0xE6EDF3, GREY = 0x9AA4B2, RED = 0xE0484A, GREEN = 0x7BD88F, ICE = 0x62CFF4;

    private static final class Game {
        final long bet;
        final int mines;
        final boolean[] mine = new boolean[25];
        final boolean[] open = new boolean[25];
        int found;
        boolean over;
        boolean lost;
        int boom = -1;
        long paid;

        Game(long bet, int mines) {
            this.bet = bet;
            this.mines = mines;
            int placed = 0;
            while (placed < mines) {
                int i = Cards.RANDOM.nextInt(25);
                if (!mine[i]) {
                    mine[i] = true;
                    placed++;
                }
            }
        }
    }

    private final OrbitCasino plugin;
    private final World world;
    private final double edge;
    private final long maxWin;
    private final Map<UUID, Game> games = new HashMap<>();
    private final Map<UUID, Integer> step = new HashMap<>();
    private final Map<UUID, Integer> count = new HashMap<>();
    private final java.util.Set<UUID> pending = new java.util.HashSet<>();

    public Mines(OrbitCasino plugin, World world) {
        this.plugin = plugin;
        this.world = world;
        this.edge = plugin.getConfig().getDouble("mines.redistribution", 0.97);
        this.maxWin = plugin.getConfig().getLong("mines.gain-max", 100000);
    }

    public double rtp() {
        return edge;
    }

    public void spawn() {
        Holo.text(new Location(world, Salon.MINES_X + 0.5, Salon.Y + 3.4, Salon.BORNE_Z + 0.4, 180f, 0f),
                "<gradient:#7BD88F:#62CFF4><b>MINES</b></gradient> <#5A6270>· " + Math.round(edge * 100) + " % redistribués\n"
                        + "<#E6EDF3>Trouve les cristaux, évite les mines\n<#9AA4B2>Clic droit pour jouer", 1.0f, true);
    }

    /** Multiplicateur après {@code k} cases sûres avec {@code m} mines. */
    double mult(int m, int k) {
        double r = edge;
        for (int i = 0; i < k; i++) {
            r *= (25.0 - i) / (25.0 - m - i);
        }
        return r;
    }

    private long value(Game g) {
        return g.found == 0 ? g.bet : Math.min(maxWin, (long) Math.floor(g.bet * mult(g.mines, g.found)));
    }

    private static int slot(int cell) {
        return (cell / 5) * 9 + 2 + cell % 5;
    }

    // ------------------------------------------------------------------ joueur

    public void open(Player p) {
        if (plugin.screens() == null || !plugin.screens().available(p)) {
            Txt.send(p, "<#E0484A>Mines se joue sur l'écran du casino : accepte le pack de ressources du serveur (reconnecte-toi).");
            return;
        }
        screen(p, true);
    }

    private void start(Player p) {
        UUID u = p.getUniqueId();
        Game g = games.get(u);
        if (g != null && !g.over || pending.contains(u)) {
            return;
        }
        long bet = STEPS[step.getOrDefault(u, 0)];
        int m = COUNTS[count.getOrDefault(u, 2)];
        pending.add(u);
        plugin.wallet().debit(p, bet, "mines", m + " mines", ok -> {
            pending.remove(u);
            if (!ok) {
                Txt.bar(p, "<#E0484A>Pas assez d'Orbit Coins.");
                return;
            }
            games.put(u, new Game(bet, m));
            Txt.sound(p, Sound.BLOCK_CHAIN_PLACE, 1.4f);
            if (p.isOnline()) {
                screen(p, false);
            }
        });
    }

    private void reveal(Player p, int cell) {
        Game g = games.get(p.getUniqueId());
        if (g == null || g.over || g.open[cell]) {
            return;
        }
        g.open[cell] = true;
        if (g.mine[cell]) {
            g.over = true;
            g.lost = true;
            g.boom = cell;
            p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.3f);
            Txt.send(p, "Mines : <#E0484A>boum !</#E0484A> " + Txt.n(-g.bet) + " OC <#9AA4B2>(" + g.found + " cristal"
                    + (g.found > 1 ? "x" : "") + " trouvé" + (g.found > 1 ? "s" : "") + ")");
        } else {
            g.found++;
            p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.9f + g.found * 0.05f);
            if (g.found == 25 - g.mines || value(g) >= maxWin) {
                cashOut(p); // plus rien à découvrir (ou gain maximum)
                return;
            }
        }
        screen(p, false);
    }

    private void cashOut(Player p) {
        Game g = games.get(p.getUniqueId());
        if (g == null || g.over || g.found == 0) {
            return;
        }
        g.over = true;
        g.paid = value(g);
        plugin.wallet().credit(p.getUniqueId(), g.paid, "mines", g.found + " cristaux, " + g.mines + " mines", g.paid > g.bet);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.4f);
        Txt.send(p, "Mines : encaissé à " + Crash.x(mult(g.mines, g.found)) + " · <#7BD88F><b>+" + Txt.n(g.paid - g.bet) + " OC</b>");
        if (g.paid >= 5000) {
            for (Player o : world.getPlayers()) {
                Txt.send(o, "<#E0B864>" + p.getName() + "</#E0B864> gagne " + Txt.oc(g.paid) + " aux Mines !");
            }
        }
        screen(p, false);
    }

    private void screen(Player p, boolean open) {
        Screens screens = plugin.screens();
        if (screens == null || !screens.available(p)) {
            return;
        }
        UUID u = p.getUniqueId();
        Game g = games.get(u);
        boolean active = g != null && !g.over;
        int m = active ? g.mines : COUNTS[count.getOrDefault(u, 2)];
        long bet = STEPS[step.getOrDefault(u, 0)];
        Screens.Frame f = new Screens.Frame("mines");
        Hud.Builder t = Hud.title().background(Hud.BG_MINES)
                .text(0, 8, Hud.Align.LEFT, "MINES · " + m + " MINE" + (m > 1 ? "S" : ""), GOLD)
                .text(0, 168, Hud.Align.RIGHT, Hud.fit(Txt.n(Math.max(0, plugin.wallet().balance(p))) + " OC", 60), LIGHT);
        if (g != null) {
            int k = g.found;
            t.text(1, 22, Hud.Align.CENTER, Hud.fit(Crash.x(k == 0 ? 1 : mult(g.mines, k)), 32), g.lost ? RED : GOLD);
            String cash = g.lost ? "0" : Txt.n(g.over ? g.paid : value(g));
            t.text(2, 22, Hud.Align.CENTER, Hud.fit(cash, 32), g.lost ? RED : g.over ? GREEN : LIGHT);
            if (!g.over) {
                t.text(1, 155, Hud.Align.CENTER, Hud.fit(Crash.x(mult(g.mines, k + 1)), 32), ICE);
            }
        } else {
            t.text(1, 22, Hud.Align.CENTER, "×1.00", GREY);
            t.text(1, 155, Hud.Align.CENTER, Hud.fit(Crash.x(mult(m, 1)), 32), ICE);
        }
        t.text(2, 155, Hud.Align.CENTER, String.valueOf(m), RED);
        String status;
        int sc = GREY;
        if (g == null) {
            status = "MISE, NOMBRE DE MINES, PUIS JOUER";
        } else if (!g.over) {
            status = g.found == 0 ? "CLIQUE SUR UNE CASE" : "ENCORE UNE ? OU ENCAISSE";
            sc = LIGHT;
        } else if (g.lost) {
            status = "BOUM · " + Txt.n(-g.bet) + " OC";
            sc = RED;
        } else {
            status = "ENCAISSÉ · +" + Txt.n(g.paid - g.bet) + " OC";
            sc = GREEN;
        }
        t.text(3, 88, Hud.Align.CENTER, Hud.fit(status, 88), sc);
        f.title(t.build());

        for (int c = 0; c < 25; c++) {
            int cell = c;
            String model;
            String name;
            if (g == null) {
                model = "mn_cell";
                name = "<#9AA4B2>Lance une partie d'abord";
            } else if (g.open[c] && !g.mine[c]) {
                model = "mn_gem";
                name = "<#62CFF4>Cristal";
            } else if (g.over && g.mine[c]) {
                model = c == g.boom ? "mn_boom" : "mn_mine";
                name = "<#E0484A>Mine";
            } else {
                model = "mn_cell";
                name = g.over ? "<#5A6270>Case sûre" : "<#E6EDF3>Découvrir";
            }
            f.item(slot(c), Screens.model(model, name), model + "|" + name, g != null && !g.over ? () -> reveal(p, cell) : null);
        }

        f.button(45, "btn_moins", false, () -> {
            if (!activeGame(u)) {
                step.put(u, Math.max(0, step.getOrDefault(u, 0) - 1));
                screen(p, false);
            }
        }, "<#62CFF4>Mise −");
        f.item(46, Screens.model("chip_" + bet, "<#E0B864><b>Mise : " + Txt.n(bet) + " OC", "<#9AA4B2>De 10 à 1 000 OC"), "chip|" + bet, null);
        f.button(47, "btn_plus", false, () -> {
            if (!activeGame(u)) {
                step.put(u, Math.min(STEPS.length - 1, step.getOrDefault(u, 0) + 1));
                screen(p, false);
            }
        }, "<#62CFF4>Mise +");
        if (active) {
            if (g.found > 0) {
                f.button(48, "btn_encaisser", true, () -> cashOut(p), "<#E0B864><b>Encaisser " + Txt.n(value(g)) + " OC",
                        "<#9AA4B2>" + Crash.x(mult(g.mines, g.found)) + " · " + g.found + " cristal" + (g.found > 1 ? "x" : ""));
            } else {
                f.button(48, "btn_encaisser_off", true, null, "<#9AA4B2>Découvre au moins une case");
            }
        } else {
            f.button(48, "btn_miser", true, () -> start(p), "<#7BD88F><b>Jouer " + Txt.n(bet) + " OC",
                    "<#9AA4B2>" + m + " mine" + (m > 1 ? "s" : "") + " · 1re case " + Crash.x(mult(m, 1)));
        }
        ItemStack mineItem = Screens.model("mn_mine", "<#E0484A><b>Mines : " + m, "<#9AA4B2>Gauche : plus · droite : moins",
                "<#5A6270>Plus de mines = plus de gain par case");
        mineItem.setAmount(Math.max(1, Math.min(64, m)));
        f.item(51, mineItem, "mines|" + m, () -> cycleCount(p, 1), () -> cycleCount(p, -1));
        f.button(53, "btn_quitter", false, () -> plugin.screens().close(p), "<#9AA4B2>Fermer",
                active ? "<#5A6270>La partie t'attend à la borne" : "<#5A6270>À bientôt",
                "<#5A6270>" + Math.round(edge * 100) + " % redistribués · gain max " + Txt.n(maxWin) + " OC");
        screens.show(p, f, open);
    }

    private boolean activeGame(UUID u) {
        Game g = games.get(u);
        return g != null && !g.over;
    }

    private void cycleCount(Player p, int d) {
        UUID u = p.getUniqueId();
        if (activeGame(u)) {
            return;
        }
        count.put(u, Math.floorMod(count.getOrDefault(u, 2) + d, COUNTS.length));
        screen(p, false);
    }

    /** Déconnexion : la partie en cours est encaissée (ou la mise rendue si rien n'est découvert). */
    public void quit(UUID u) {
        Game g = games.remove(u);
        if (g != null && !g.over) {
            plugin.wallet().credit(u, value(g), "mines", "déconnexion", g.found > 0 && value(g) > g.bet);
        }
    }

    public void shutdown() {
        for (Map.Entry<UUID, Game> e : games.entrySet()) {
            Game g = e.getValue();
            if (!g.over) {
                plugin.wallet().cashOutNow(e.getKey(), value(g), "mines");
            }
        }
        games.clear();
    }
}
