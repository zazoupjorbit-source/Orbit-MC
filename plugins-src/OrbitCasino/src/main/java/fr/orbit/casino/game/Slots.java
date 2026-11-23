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
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Machines à sous : 3 rouleaux, tirage pondéré, gros lot progressif commun à toutes les machines (table
 * casino_jackpot). Clic sur la machine = lancer avec sa mise ; accroupi + clic = choisir la mise.
 */
public final class Slots {

    record Sym(String id, String glyph, String color, int weight, int mult) {
        String mini() {
            return "<" + color + "><b>" + glyph + "</b></" + color + ">";
        }
    }

    private static final Map<String, String[]> LOOK = Map.of(
            "coeur", new String[]{"❤", "#E0484A"},
            "trefle", new String[]{"♣", "#7BD88F"},
            "carreau", new String[]{"♦", "#62CFF4"},
            "soleil", new String[]{"☀", "#FFD966"},
            "etoile", new String[]{"★", "#E0B864"},
            "sept", new String[]{"7", "#FF3B3B"},
            "orbit", new String[]{"✦", "#C79BFF"});

    private final OrbitCasino plugin;
    private final World world;
    private final List<Sym> symbols = new ArrayList<>();
    private int totalWeight;
    private final List<Long> bets = new ArrayList<>();
    private final Map<UUID, Long> chosenBet = new HashMap<>();
    private final Map<Block, Integer> blocks = new HashMap<>();
    private final TextDisplay[] screens = new TextDisplay[Room.SLOT_X.length];
    private final boolean[] spinning = new boolean[Room.SLOT_X.length];
    private TextDisplay jackpotSign;
    private volatile long jackpot;

    public Slots(OrbitCasino plugin, World world) {
        this.plugin = plugin;
        this.world = world;
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("machines.symboles");
        if (s != null) {
            for (String id : s.getKeys(false)) {
                String[] look = LOOK.getOrDefault(id, new String[]{"?", "#FFFFFF"});
                Sym sym = new Sym(id, look[0], look[1], Math.max(1, s.getInt(id + ".poids", 10)), s.getInt(id + ".gain", 2));
                symbols.add(sym);
                totalWeight += sym.weight();
            }
        }
        for (Object o : plugin.getConfig().getList("machines.mises", List.of(10, 50, 100, 250, 500))) {
            bets.add(Long.parseLong(String.valueOf(o)));
        }
    }

    public void spawn() {
        for (int i = 0; i < Room.SLOT_X.length; i++) {
            for (Block b : Room.slotBlocks(world, i)) {
                blocks.put(b, i);
            }
            Location screen = new Location(world, Room.SLOT_X[i] + 0.5, Room.Y + 1.45, Room.SLOT_Z + 1.02, 0f, 0f);
            screens[i] = Holo.text(screen, idle(i), 0.55f, true);
        }
        jackpotSign = Holo.text(new Location(world, 0.5, Room.Y + 6.6, Room.SLOT_Z + 0.6, 0f, 0f), jackpotText(), 2.2f, true);
        Holo.text(new Location(world, 0.5, Room.Y + 9.3, Room.SLOT_Z + 0.6, 0f, 0f),
                "<gradient:#E0B864:#FFF3C4><b>ORBIT CASINO</b></gradient>", 3.2f, true);
        loadJackpot();
    }

    private String jackpotText() {
        return "<#C79BFF>✦</#C79BFF> <#E0B864><b>JACKPOT</b></#E0B864> <#C79BFF>✦</#C79BFF>\n" + Txt.oc(jackpot);
    }

    private String idle(int i) {
        return "<#9AA4B2>MACHINE " + (i + 1) + "</#9AA4B2>\n<#5A6270><b>? ? ?</b></#5A6270>\n<#9AA4B2>Clic : jouer\nAccroupi + clic : mise";
    }

    private void loadJackpot() {
        long start = plugin.getConfig().getLong("machines.jackpot-depart", 10000);
        plugin.db().then(plugin.db().run(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT IGNORE INTO casino_jackpot (id, montant) VALUES (1, ?)")) {
                ps.setLong(1, start);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT montant FROM casino_jackpot WHERE id = 1");
                 ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : start;
            }
        }), v -> {
            jackpot = v;
            Holo.set(jackpotSign, jackpotText());
        }, null);
    }

    public Integer machineAt(Block b) {
        return blocks.get(b);
    }

    private long betOf(Player p) {
        return chosenBet.getOrDefault(p.getUniqueId(), bets.size() > 1 ? bets.get(1) : bets.get(0));
    }

    public void click(Player p, int i, boolean sneaking) {
        if (sneaking) {
            chooseBet(p);
            return;
        }
        if (spinning[i]) {
            Txt.bar(p, "<#E0484A>Cette machine tourne déjà.");
            return;
        }
        long bet = betOf(p);
        spinning[i] = true;
        plugin.wallet().debit(p, bet, "machine", "machine " + (i + 1), ok -> {
            if (!ok) {
                spinning[i] = false;
                Txt.bar(p, "<#E0484A>Pas assez d'Orbit Coins pour miser " + Txt.n(bet) + " OC.");
                Txt.sound(p, Sound.ENTITY_VILLAGER_NO, 1f);
                return;
            }
            feedJackpot(bet);
            spin(p, i, bet);
        });
    }

    private void chooseBet(Player p) {
        Menu m = new Menu(1, "<#E0B864><b>Mise de la machine");
        long current = betOf(p);
        for (int k = 0; k < bets.size() && k < 9; k++) {
            long b = bets.get(k);
            m.set(k, Menu.item(b == current ? Material.GOLD_BLOCK : Material.GOLD_NUGGET,
                    Txt.oc(b), b == current ? "<#7BD88F>✔ Mise actuelle" : "<#9AA4B2>Clique pour choisir"), e -> {
                chosenBet.put(p.getUniqueId(), b);
                p.closeInventory();
                Txt.bar(p, "<#9AA4B2>Mise des machines : " + Txt.oc(b));
                Txt.sound(p, Sound.BLOCK_NOTE_BLOCK_HAT, 1.5f);
            });
        }
        m.open(p);
    }

    private void feedJackpot(long bet) {
        long part = bet * plugin.getConfig().getLong("machines.part-jackpot", 3) / 100;
        if (part <= 0) {
            return;
        }
        jackpot += part;
        Holo.set(jackpotSign, jackpotText());
        plugin.db().run(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE casino_jackpot SET montant = montant + ? WHERE id = 1")) {
                ps.setLong(1, part);
                return ps.executeUpdate();
            }
        });
    }

    private Sym roll() {
        int r = Cards.RANDOM.nextInt(totalWeight);
        for (Sym s : symbols) {
            r -= s.weight();
            if (r < 0) {
                return s;
            }
        }
        return symbols.get(0);
    }

    private void spin(Player p, int i, long bet) {
        Sym[] result = {roll(), roll(), roll()};
        Location sound = new Location(world, Room.SLOT_X[i] + 0.5, Room.Y + 1.5, Room.SLOT_Z + 1.5);
        world.playSound(sound, Sound.BLOCK_LEVER_CLICK, 1f, 0.8f);
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                t += 2;
                Sym[] shown = new Sym[3];
                for (int r = 0; r < 3; r++) {
                    shown[r] = t >= 16 + r * 10 ? result[r] : roll();
                }
                Holo.set(screens[i], "<#E0B864>" + p.getName() + "</#E0B864>\n" + shown[0].mini() + "  " + shown[1].mini() + "  "
                        + shown[2].mini() + "\n<#9AA4B2>Mise " + Txt.n(bet) + " OC");
                if (t % 4 == 0 && t < 36) {
                    world.playSound(sound, Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, 1.2f + (t / 40f));
                }
                if (t == 16 || t == 26 || t == 36) {
                    world.playSound(sound, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 0.9f, 1f);
                }
                if (t >= 38) {
                    cancel();
                    finish(p, i, bet, result, sound);
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void finish(Player p, int i, long bet, Sym[] r, Location at) {
        String line = r[0].mini() + "  " + r[1].mini() + "  " + r[2].mini();
        long win = 0;
        boolean grand = false;
        if (r[0] == r[1] && r[1] == r[2]) {
            win = bet * r[0].mult();
            grand = r[0].id().equals("orbit");
        } else {
            Sym pair = r[0] == r[1] || r[0] == r[2] ? r[0] : r[1] == r[2] ? r[1] : null;
            if (pair != null) {
                boolean rich = pair.id().equals("sept") || pair.id().equals("etoile") || pair.id().equals("orbit");
                win = bet * (rich ? 3 : 1);
            }
        }
        final long gain = win;
        if (grand) {
            grabJackpot(p, bet, gain, i, line, at);
            return;
        }
        if (gain > 0) {
            plugin.wallet().credit(p.getUniqueId(), gain, "machine", "machine " + (i + 1), gain > bet);
        }
        String verdict = gain > bet ? "<#7BD88F><b>+" + Txt.n(gain) + " OC</b>" : gain == bet ? "<#9AA4B2>Remboursé" : "<#5A6270>Perdu";
        Holo.set(screens[i], "<#E0B864>" + p.getName() + "</#E0B864>\n" + line + "\n" + verdict);
        if (p.isOnline()) {
            Txt.bar(p, line + "  " + verdict);
            if (gain > bet) {
                Txt.sound(p, gain >= bet * 15 ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_PLAYER_LEVELUP, 1.3f);
                world.spawnParticle(Particle.HAPPY_VILLAGER, at.clone().add(0, 1, 0), 12, 0.4, 0.4, 0.2);
            }
        }
        if (gain >= bet * 30) {
            broadcast("<#E0B864>" + p.getName() + "</#E0B864> remporte " + Txt.oc(gain) + " à la machine " + (i + 1) + " ! " + line);
        }
        release(i, 60L);
    }

    private void grabJackpot(Player p, long bet, long base, int i, String line, Location at) {
        long start = plugin.getConfig().getLong("machines.jackpot-depart", 10000);
        plugin.db().then(plugin.db().transaction(c -> {
            long pot;
            try (PreparedStatement ps = c.prepareStatement("SELECT montant FROM casino_jackpot WHERE id = 1 FOR UPDATE");
                 ResultSet rs = ps.executeQuery()) {
                pot = rs.next() ? rs.getLong(1) : start;
            }
            try (PreparedStatement ps = c.prepareStatement("UPDATE casino_jackpot SET montant = ? WHERE id = 1")) {
                ps.setLong(1, start);
                ps.executeUpdate();
            }
            return pot;
        }), pot -> {
            long total = pot + base;
            jackpot = start;
            Holo.set(jackpotSign, jackpotText());
            plugin.wallet().credit(p.getUniqueId(), total, "jackpot", "machine " + (i + 1), true);
            Holo.set(screens[i], "<#C79BFF><b>✦ JACKPOT ✦</b></#C79BFF>\n" + line + "\n<#E0B864><b>+" + Txt.n(total) + " OC");
            broadcast("<#C79BFF><b>✦ JACKPOT ✦</b></#C79BFF> <#E0B864>" + p.getName() + "</#E0B864> décroche "
                    + Txt.oc(total) + " à la machine " + (i + 1) + " !");
            for (Player o : world.getPlayers()) {
                o.playSound(o.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            }
            world.spawnParticle(Particle.TOTEM_OF_UNDYING, at.clone().add(0, 1.5, 0), 120, 0.6, 1, 0.6, 0.4);
            release(i, 140L);
        }, () -> release(i, 20L));
    }

    private void release(int i, long delay) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            spinning[i] = false;
            Holo.set(screens[i], idle(i));
        }, delay);
    }

    private void broadcast(String msg) {
        for (Player o : world.getPlayers()) {
            Txt.send(o, msg);
        }
    }

    /** Pour /casino : RTP théorique du réglage actuel (somme des gains espérés pour 1 OC misé). */
    public double rtp() {
        double total = 0;
        for (Sym a : symbols) {
            for (Sym b : symbols) {
                for (Sym c : symbols) {
                    double p = (double) a.weight() * b.weight() * c.weight() / ((double) totalWeight * totalWeight * totalWeight);
                    double win;
                    if (a == b && b == c) {
                        win = a.mult();
                    } else {
                        Sym pair = a == b || a == c ? a : b == c ? b : null;
                        win = pair == null ? 0 : pair.id().equals("sept") || pair.id().equals("etoile") || pair.id().equals("orbit") ? 3 : 1;
                    }
                    total += p * win;
                }
            }
        }
        return total;
    }
}
