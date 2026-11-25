package fr.orbit.casino.game;

import fr.orbit.casino.Holo;
import fr.orbit.casino.Menu;
import fr.orbit.casino.OrbitCasino;
import fr.orbit.casino.Room;
import fr.orbit.casino.Txt;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Texas Hold'em sans limite entre joueurs, 6 places. Le croupier distribue et ne prend rien : tout ce qui est
 * misé revient aux joueurs. Cave entre 20 et 100 grosses blindes ; le tapis est noté en base (en_jeu) et rendu
 * si le serveur s'arrête.
 */
public final class Poker implements Seated {

    private enum State { WAITING, STARTING, BETTING, SHOWDOWN }

    private static final class Seat {
        UUID uuid;
        String name;
        String ip;
        long stack;
        final List<Cards.Card> hole = new ArrayList<>();
        boolean inHand, folded, allIn, acted, gone, pending;
        long round;      // misé sur ce tour d'enchères
        long committed;  // misé sur toute la main
        String last = "";
        int timeouts;
    }

    private final OrbitCasino plugin;
    private final World world;
    private final String id;
    private final String name;
    private final long sb, bb;
    private final int cz;
    private final Seat[] seats = new Seat[6];
    private final TextDisplay[] seatText = new TextDisplay[6];
    private TextDisplay center;
    private TextDisplay label;
    private final List<Cards.Card> board = new ArrayList<>();
    private Cards.Shoe deck = new Cards.Shoe(1);
    private State state = State.WAITING;
    private int countdown;
    private int dealer = -1, turn = -1, street;
    private long currentBet, minRaise;
    private int turnLeft;
    private String result = "";

    public Poker(OrbitCasino plugin, World world, String id, String name, long sb, long bb, int cz) {
        this.plugin = plugin;
        this.world = world;
        this.id = id;
        this.name = name;
        this.sb = sb;
        this.bb = bb;
        this.cz = cz;
    }

    public int cz() {
        return cz;
    }

    /** Places : 0-2 au nord (gauche → droite vu du croupier), 3-5 au sud. Ordre du jeu : sens des aiguilles. */
    /** Place où le joueur est tenu tant qu'il est assis (null s'il n'est pas à cette table). */
    public Location heldAt(UUID uuid) {
        int i = seatOf(uuid);
        return i < 0 || seats[i].gone || seats[i].pending ? null : seatPos(i);
    }

    private Location seatPos(int i) {
        int[] dx = {-2, 0, 2, 2, 0, -2};
        boolean north = i < 3;
        return new Location(world, Room.POKER_X + dx[i] + 0.5, Room.Y, cz + (north ? -1.5 : 2.5), north ? 0f : 180f, 0f);
    }

    public void spawn() {
        Holo.npc(new Location(world, Room.POKER_X - 3.5, Room.Y, cz + 0.5, -90f, 0f), "<#C79BFF>Croupier · " + name,
                Villager.Profession.CARTOGRAPHER);
        label = Holo.text(new Location(world, Room.POKER_X + 0.5, Room.Y + 3.4, cz + 0.5), "", 1.0f, false);
        center = Holo.text(new Location(world, Room.POKER_X + 0.5, Room.Y + 1.5, cz + 0.5), "", 0.7f, false);
        for (int i = 0; i < 6; i++) {
            Location s = seatPos(i);
            double z = i < 3 ? cz - 0.75 : cz + 1.75;
            seatText[i] = Holo.text(new Location(world, s.getX(), Room.Y + 1.15, z), "", 0.42f, false);
        }
        render();
    }

    // ------------------------------------------------------------------ places

    private int seatOf(UUID uuid) {
        for (int i = 0; i < 6; i++) {
            if (seats[i] != null && !seats[i].gone && !seats[i].pending && seats[i].uuid.equals(uuid)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean has(UUID player) {
        return seatOf(player) >= 0;
    }

    private static String ip(Player p) {
        InetSocketAddress a = p.getAddress();
        return a == null ? "" : a.getAddress().getHostAddress();
    }

    /** Clic sur la table : choisir sa cave puis s'asseoir à la place libre la plus proche. */
    public void open(Player p, Location clicked) {
        if (has(p.getUniqueId())) {
            prompt(p);
            return;
        }
        if (plugin.casino().seatedAnywhere(p.getUniqueId())) {
            Txt.send(p, "<#E0484A>Tu es déjà assis à une autre table. <#9AA4B2>/table quitter");
            return;
        }
        int seat = freeSeat(clicked);
        if (seat < 0) {
            Txt.send(p, "<#E0484A>La " + name + " est complète.");
            return;
        }
        if (plugin.getConfig().getBoolean("poker.meme-ip-interdite", true) && !p.hasPermission("orbitcasino.admin")) {
            String ip = ip(p);
            for (Seat s : seats) {
                if (s != null && !s.gone && !ip.isEmpty() && ip.equals(s.ip)) {
                    Txt.send(p, "<#E0484A>Un compte de la même connexion est déjà assis à cette table.");
                    return;
                }
            }
        }
        long[] caves = {bb * 20, bb * 50, bb * 100};
        Menu m = new Menu(3, "<#C79BFF><b>" + name + "</b></#C79BFF> <#5A6270>— blindes " + Txt.n(sb) + "/" + Txt.n(bb));
        String[] labels = {"Petite cave", "Cave moyenne", "Grosse cave"};
        Material[] icons = {Material.IRON_NUGGET, Material.GOLD_NUGGET, Material.GOLD_INGOT};
        for (int k = 0; k < 3; k++) {
            long cave = caves[k];
            m.set(11 + k * 2, Menu.item(icons[k], "<#E0B864><b>" + labels[k] + " : " + Txt.n(cave) + " OC",
                    "<#9AA4B2>" + (cave / bb) + " grosses blindes", "<#9AA4B2>Ton solde : " + Txt.oc(Math.max(0, plugin.wallet().balance(p))),
                    "", "<#5A6270>Tu récupères ton tapis en quittant la table."), e -> {
                p.closeInventory();
                buyIn(p, cave, clicked);
            });
        }
        m.set(22, Menu.item(Material.BOOK, "<#62CFF4><b>Règles</b>", "<#9AA4B2>Texas Hold'em sans limite, entre joueurs.",
                "<#9AA4B2>2 cartes chacun, 5 au milieu : la meilleure", "<#9AA4B2>main de 5 cartes gagne le pot.",
                "<#9AA4B2>Le casino ne prend aucune commission."));
        m.fill(Material.BLACK_STAINED_GLASS_PANE);
        m.open(p);
    }

    private int freeSeat(Location clicked) {
        int best = -1;
        double dist = Double.MAX_VALUE;
        for (int i = 0; i < 6; i++) {
            if (seats[i] == null) {
                double d = clicked == null ? i : seatPos(i).distanceSquared(clicked);
                if (d < dist) {
                    dist = d;
                    best = i;
                }
            }
        }
        return best;
    }

    private void buyIn(Player p, long cave, Location clicked) {
        if (has(p.getUniqueId()) || plugin.casino().seatedAnywhere(p.getUniqueId())) {
            return;
        }
        int seat = freeSeat(clicked);
        if (seat < 0) {
            Txt.send(p, "<#E0484A>Plus de place libre.");
            return;
        }
        Seat s = new Seat();
        s.uuid = p.getUniqueId();
        s.name = p.getName();
        s.ip = ip(p);
        seats[seat] = s; // place réservée pendant le retrait
        s.pending = true;
        plugin.wallet().debit(p, cave, "poker", name + " cave", ok -> {
            if (!ok || !p.isOnline() || seats[seat] != s) {
                if (seats[seat] == s) {
                    seats[seat] = null;
                }
                if (ok) {
                    plugin.wallet().credit(p.getUniqueId(), cave, "poker", "cave rendue", false);
                }
                Txt.send(p, "<#E0484A>Pas assez d'Orbit Coins pour cette cave.");
                return;
            }
            s.pending = false;
            s.stack = cave;
            plugin.wallet().setStaked(s.uuid, cave);
            Location to = seatPos(seat);
            p.teleport(to);
            Txt.send(p, "Tu t'assois à la <b>" + name + "</b> avec " + Txt.oc(cave) + ". <#9AA4B2>/table quitter pour partir avec ton tapis.");
            p.sendMessage(Txt.mm("  ").append(Txt.button("📖 Règles", "#C79BFF", "/oc regles poker", "Comment jouer au poker"))
                    .append(Txt.mm(" ")).append(Txt.button("Les mains", "#C79BFF", "/oc regles mains", "Quelle main bat quelle main")));
            Txt.sound(p, Sound.BLOCK_CHAIN_PLACE, 1.2f);
            say(p.getName() + " s'assoit à la table.", p.getUniqueId());
            if (state == State.WAITING && ready() >= 2) {
                state = State.STARTING;
                countdown = 10;
            }
            render();
            if (plugin.screens() != null && plugin.screens().available(p)) {
                screen(p, seat, true);
            }
        });
    }

    private int ready() {
        int n = 0;
        for (Seat s : seats) {
            if (s != null && !s.gone && !s.pending && s.stack > 0) {
                n++;
            }
        }
        return n;
    }

    @Override
    public void leave(Player p, String reason) {
        int i = seatOf(p.getUniqueId());
        if (i < 0) {
            return;
        }
        Seat s = seats[i];
        boolean playing = state == State.BETTING && s.inHand && !s.folded;
        if (playing) {
            s.folded = true;
            s.last = "couché";
        }
        long back = s.stack;
        s.stack = 0;
        s.gone = true;
        raiseTarget.remove(s.uuid);
        if (plugin.screens() != null) {
            plugin.screens().close(p);
        }
        plugin.wallet().cashOut(s.uuid, back, "poker");
        if (p.isOnline() && reason != null) {
            Txt.send(p, "Tu quittes la " + name + " avec " + Txt.oc(back) + (reason.isEmpty() ? "." : " (" + reason + ")."));
        }
        say(s.name + " quitte la table.", s.uuid);
        if (!(state == State.BETTING && s.inHand) && state != State.SHOWDOWN) {
            seats[i] = null; // pas engagé dans la main : place libérée tout de suite
        }
        if (playing) {
            if (turn == i) {
                advance();
            } else if (alive() <= 1) {
                endHand();
            }
        }
        if (state == State.STARTING && ready() < 2) {
            state = State.WAITING;
        }
        render();
    }

    // ------------------------------------------------------------------ main

    private void startHand() {
        if (ready() < 2) {
            state = State.WAITING;
            render();
            return;
        }
        handNo++;
        deck = new Cards.Shoe(1);
        board.clear();
        result = "";
        street = 0;
        for (Seat s : seats) {
            if (s == null) {
                continue;
            }
            s.hole.clear();
            s.round = 0;
            s.committed = 0;
            s.allIn = false;
            s.acted = false;
            s.folded = false;
            s.last = "";
            s.inHand = !s.gone && !s.pending && s.stack > 0;
        }
        dealer = next(dealer);
        int players = alive();
        int sbSeat = players == 2 ? dealer : next(dealer);
        int bbSeat = next(sbSeat);
        post(sbSeat, sb, "petite blinde");
        post(bbSeat, bb, "grosse blinde");
        currentBet = bb;
        minRaise = bb;
        for (int r = 0; r < 2; r++) {
            for (Seat s : seats) {
                if (s != null && s.inHand) {
                    s.hole.add(deck.draw());
                }
            }
        }
        state = State.BETTING;
        for (Seat s : seats) {
            if (s != null && s.inHand) {
                Player p = Bukkit.getPlayer(s.uuid);
                if (p != null) {
                    Txt.send(p, "Nouvelle main · tes cartes : " + Cards.row(s.hole));
                    Txt.sound(p, Sound.ITEM_BOOK_PAGE_TURN, 1.2f);
                }
            }
        }
        turn = bbSeat;
        openScreens();
        advance();
    }

    private void post(int i, long amount, String what) {
        Seat s = seats[i];
        long a = Math.min(amount, s.stack);
        s.stack -= a;
        s.round += a;
        s.committed += a;
        s.last = what;
        if (s.stack == 0) {
            s.allIn = true;
        }
    }

    /** Siège suivant encore dans la main (sens du jeu). */
    private int next(int from) {
        for (int k = 1; k <= 6; k++) {
            int i = Math.floorMod(from + k, 6);
            Seat s = seats[i];
            if (s != null && s.inHand && !s.folded) {
                return i;
            }
        }
        return from;
    }

    private int alive() {
        int n = 0;
        for (Seat s : seats) {
            if (s != null && s.inHand && !s.folded) {
                n++;
            }
        }
        return n;
    }

    private int canAct() {
        int n = 0;
        for (Seat s : seats) {
            if (s != null && s.inHand && !s.folded && !s.allIn) {
                n++;
            }
        }
        return n;
    }

    private boolean roundDone() {
        for (Seat s : seats) {
            if (s != null && s.inHand && !s.folded && !s.allIn && (!s.acted || s.round < currentBet)) {
                return false;
            }
        }
        return true;
    }

    /** Après chaque action : joueur suivant, tour suivant, ou fin de la main. */
    private void advance() {
        if (state != State.BETTING) {
            return;
        }
        if (alive() <= 1) {
            endHand();
            return;
        }
        if (roundDone()) {
            if (canAct() <= 1 && everyoneMatched()) {
                // plus personne ne peut relancer : on retourne toutes les cartes
                while (board.size() < 5) {
                    board.add(deck.draw());
                }
                street = 3;
                for (Seat s : seats) {
                    if (s != null && s.inHand && !s.folded) {
                        s.last = "tapis";
                    }
                }
                endHand();
                return;
            }
            nextStreet();
            return;
        }
        int i = turn;
        for (int k = 0; k < 6; k++) {
            i = Math.floorMod(i + 1, 6);
            Seat s = seats[i];
            if (s != null && s.inHand && !s.folded && !s.allIn && (!s.acted || s.round < currentBet)) {
                turn = i;
                raiseTarget.remove(s.uuid);
                turnLeft = plugin.getConfig().getInt("poker.temps-de-jeu", 30);
                render();
                Player p = Bukkit.getPlayer(s.uuid);
                if (p != null) {
                    prompt(p);
                    Txt.sound(p, Sound.BLOCK_NOTE_BLOCK_PLING, 1.6f);
                }
                return;
            }
        }
        nextStreet();
    }

    private boolean everyoneMatched() {
        long max = 0;
        for (Seat s : seats) {
            if (s != null && s.inHand && !s.folded) {
                max = Math.max(max, s.round);
            }
        }
        for (Seat s : seats) {
            if (s != null && s.inHand && !s.folded && !s.allIn && s.round < max) {
                return false;
            }
        }
        return true;
    }

    private void nextStreet() {
        if (street >= 3) {
            endHand();
            return;
        }
        street++;
        int cards = street == 1 ? 3 : 1;
        for (int k = 0; k < cards; k++) {
            board.add(deck.draw());
        }
        currentBet = 0;
        minRaise = bb;
        for (Seat s : seats) {
            if (s != null && s.inHand) {
                s.round = 0;
                s.acted = false;
                if (!s.folded && !s.allIn) {
                    s.last = "";
                }
            }
        }
        world.playSound(new Location(world, Room.POKER_X + 0.5, Room.Y + 1, cz + 0.5), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1f);
        String[] streets = {"", "Flop", "Turn", "River"};
        say(streets[street] + " : " + Cards.row(board), null);
        turn = dealer; // le premier à parler est à gauche du donneur
        advance();
    }

    @Override
    public void action(Player p, String action, String arg) {
        int i = seatOf(p.getUniqueId());
        if (i < 0) {
            return;
        }
        if (action.equals("quitter") || action.equals("quitter-ok")) {
            Seat me = seats[i];
            boolean inPlay = state == State.BETTING && me.inHand && !me.folded;
            if (inPlay && action.equals("quitter")) {
                // se lever en pleine main = abandonner : on le fait confirmer
                if (plugin.screens() != null) {
                    plugin.screens().close(p);
                }
                Txt.send(p, "<#E0484A>Tu es dans une main.</#E0484A> Te lever, c'est <b>te coucher</b> : tu perds les "
                        + Txt.oc(me.committed) + " déjà misés et tu repars avec ton tapis (" + Txt.oc(me.stack) + ").");
                p.sendMessage(Txt.mm("  ").append(Txt.button("Abandonner et partir", "#E0484A", "/table quitter-ok", "Te coucher et quitter la table"))
                        .append(Txt.mm(" ")).append(Txt.button("Rester", "#7BD88F", "/table ecran", "Reprendre la partie")));
                return;
            }
            leave(p, inPlay ? "abandon" : "");
            return;
        }
        if (action.equals("ecran")) {
            prompt(p);
            return;
        }
        if (state != State.BETTING || turn != i) {
            Txt.bar(p, "<#E0484A>Ce n'est pas ton tour.");
            return;
        }
        Seat s = seats[i];
        long toCall = currentBet - s.round;
        switch (action) {
            case "coucher" -> {
                s.folded = true;
                s.last = "couché";
            }
            case "parole", "suivre" -> {
                if (toCall <= 0) {
                    s.last = "parole";
                } else {
                    long a = Math.min(toCall, s.stack);
                    pay(s, a);
                    s.last = s.allIn ? "tapis" : "suit " + Txt.n(a);
                }
            }
            case "tapis" -> raiseTo(s, s.round + s.stack);
            case "relancer" -> {
                long to;
                try {
                    to = Long.parseLong(arg == null ? "" : arg.trim());
                } catch (NumberFormatException e) {
                    Txt.bar(p, "<#E0484A>/table relancer <montant total>");
                    return;
                }
                long min = currentBet + minRaise;
                long max = s.round + s.stack;
                if (to >= max) {
                    to = max;
                } else if (to < min) {
                    Txt.bar(p, "<#E0484A>Relance minimum à " + Txt.n(min) + " OC.");
                    return;
                }
                raiseTo(s, to);
            }
            default -> {
                prompt(p);
                return;
            }
        }
        s.acted = true;
        s.timeouts = 0;
        world.playSound(seatPos(i), Sound.BLOCK_CHAIN_PLACE, 0.6f, 1.3f);
        advance();
    }

    private void pay(Seat s, long a) {
        s.stack -= a;
        s.round += a;
        s.committed += a;
        if (s.stack == 0) {
            s.allIn = true;
        }
    }

    /** Monte la mise du tour à {@code to} (tapis si c'est tout le tapis). */
    private void raiseTo(Seat s, long to) {
        long add = Math.min(to - s.round, s.stack);
        if (add <= 0) {
            return;
        }
        pay(s, add);
        long raise = s.round - currentBet;
        if (s.round > currentBet) {
            if (raise >= minRaise) {
                minRaise = raise;
                // relance complète : tout le monde doit reparler
                for (Seat o : seats) {
                    if (o != null && o != s && o.inHand && !o.folded && !o.allIn) {
                        o.acted = false;
                    }
                }
            }
            currentBet = s.round;
        }
        s.last = s.allIn ? "TAPIS " + Txt.n(s.round) : "relance à " + Txt.n(s.round);
    }

    private void endHand() {
        state = State.SHOWDOWN;
        turn = -1;
        List<Seat> contenders = new ArrayList<>();
        for (Seat s : seats) {
            if (s != null && s.inHand && !s.folded) {
                contenders.add(s);
            }
        }
        StringBuilder summary = new StringBuilder();
        if (contenders.size() == 1) {
            Seat w = contenders.get(0);
            long pot = 0;
            for (Seat s : seats) {
                if (s != null) {
                    pot += s.committed;
                }
            }
            w.stack += pot;
            w.last = "<#7BD88F>gagne " + Txt.n(pot);
            summary.append(w.name).append(" remporte ").append(Txt.oc(pot)).append(" (tout le monde s'est couché).");
            result = "<#7BD88F>" + w.name + " remporte " + Txt.n(pot) + " OC";
        } else {
            while (board.size() < 5) {
                board.add(deck.draw());
            }
            // pots successifs : un niveau par montant engagé par un joueur encore en lice
            TreeSet<Long> levels = new TreeSet<>();
            for (Seat s : contenders) {
                levels.add(s.committed);
            }
            long prev = 0;
            List<String> results = new ArrayList<>();
            List<Seat> lastWinners = List.of();
            for (long level : levels) {
                long pot = 0;
                for (Seat s : seats) {
                    if (s != null) {
                        pot += Math.min(s.committed, level) - Math.min(s.committed, prev);
                    }
                }
                List<Seat> eligible = contenders.stream().filter(s -> s.committed >= level).toList();
                long bestScore = -1;
                List<Seat> winners = new ArrayList<>();
                for (Seat s : eligible) {
                    List<Cards.Card> seven = new ArrayList<>(board);
                    seven.addAll(s.hole);
                    long score = PokerHand.best(seven).score();
                    if (score > bestScore) {
                        bestScore = score;
                        winners.clear();
                        winners.add(s);
                    } else if (score == bestScore) {
                        winners.add(s);
                    }
                }
                if (pot > 0 && !winners.isEmpty()) {
                    long share = pot / winners.size();
                    long rest = pot - share * winners.size();
                    for (Seat w : winners) {
                        w.stack += share + (rest-- > 0 ? 1 : 0);
                    }
                    String names = String.join(", ", winners.stream().map(w -> w.name).toList());
                    results.add(names + " " + Txt.oc(pot) + (levels.size() > 1 ? " <#5A6270>(" + (results.isEmpty() ? "pot principal" : "pot annexe") + ")" : ""));
                }
                lastWinners = winners;
                prev = level;
            }
            // reste éventuel (joueurs couchés au-delà du dernier niveau) : aux derniers gagnants
            long leftover = 0;
            for (Seat s : seats) {
                if (s != null) {
                    leftover += Math.max(0, s.committed - prev);
                }
            }
            if (leftover > 0 && !lastWinners.isEmpty()) {
                lastWinners.get(0).stack += leftover;
            }
            for (Seat s : contenders) {
                List<Cards.Card> seven = new ArrayList<>(board);
                seven.addAll(s.hole);
                s.last = PokerHand.best(seven).name();
            }
            summary.append("Abattage : ").append(Cards.row(board)).append(" · ");
            for (Seat s : contenders) {
                summary.append(s.name).append(" ").append(Cards.row(s.hole)).append(" <#9AA4B2>(").append(s.last).append(")</#9AA4B2>  ");
            }
            summary.append("\n<#E0B864>Gagnant : </#E0B864>").append(String.join(" · ", results));
            result = "<#7BD88F>" + String.join(" · ", results);
        }
        say(summary.toString(), null);
        for (Seat s : seats) {
            if (s != null && !s.gone) {
                plugin.wallet().setStaked(s.uuid, s.stack);
            }
        }
        world.playSound(new Location(world, Room.POKER_X + 0.5, Room.Y + 1, cz + 0.5), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
        render();
        Bukkit.getScheduler().runTaskLater(plugin, this::afterHand, 140L);
    }

    private void afterHand() {
        for (int i = 0; i < 6; i++) {
            Seat s = seats[i];
            if (s == null) {
                continue;
            }
            if (s.gone) {
                seats[i] = null; // parti pendant la main (son tapis lui a déjà été rendu)
                continue;
            }
            if (s.pending) {
                continue;
            }
            s.inHand = false;
            s.hole.clear();
            s.committed = 0;
            s.round = 0;
            if (!s.gone && s.stack <= 0) {
                Player p = Bukkit.getPlayer(s.uuid);
                plugin.wallet().cashOut(s.uuid, 0, "poker");
                seats[i] = null;
                if (p != null) {
                    Txt.send(p, "Tu n'as plus de jetons : tu quittes la " + name + ".");
                }
            }
        }
        board.clear();
        result = "";
        if (ready() >= 2) {
            state = State.STARTING;
            countdown = 5;
        } else {
            state = State.WAITING;
        }
        render();
    }

    // ------------------------------------------------------------------ chaque seconde

    @Override
    public void tick() {
        for (int i = 0; i < 6; i++) {
            Seat s = seats[i];
            if (s == null || s.gone || s.pending) {
                continue;
            }
            Player p = Bukkit.getPlayer(s.uuid);
            if (p != null && !p.getWorld().equals(world)) {
                leave(p, "tu as quitté la salle");
            } else if (p != null && p.getLocation().distanceSquared(seatPos(i)) > 1) {
                // assis = on ne bouge plus (téléporté, poussé…) : retour à sa place
                Location back = seatPos(i);
                back.setYaw(p.getLocation().getYaw());
                back.setPitch(p.getLocation().getPitch());
                p.teleport(back);
            }
        }
        switch (state) {
            case STARTING -> {
                if (ready() < 2) {
                    state = State.WAITING;
                } else if (--countdown <= 0) {
                    startHand();
                    return;
                }
                render();
            }
            case BETTING -> {
                Seat s = turn >= 0 ? seats[turn] : null;
                if (s == null) {
                    advance();
                    return;
                }
                Player p = Bukkit.getPlayer(s.uuid);
                if (--turnLeft <= 0) {
                    boolean check = currentBet - s.round <= 0;
                    int absences = s.timeouts + 1;
                    if (p != null) {
                        Txt.bar(p, "<#E0484A>Temps écoulé : " + (check ? "parole." : "tu te couches."));
                        action(p, check ? "parole" : "coucher", null);
                        s.timeouts = absences;
                        if (absences >= 2 && has(s.uuid)) {
                            leave(p, "absent deux fois de suite");
                        }
                    } else {
                        s.folded = true;
                        advance();
                    }
                    return;
                }
                render();
            }
            default -> {
            }
        }
        // cartes privées en barre d'action
        for (Seat s : seats) {
            if (s != null && !s.gone && s.inHand && state != State.SHOWDOWN) {
                Player p = Bukkit.getPlayer(s.uuid);
                if (p != null && !(plugin.screens() != null && plugin.screens().isOpen(p))) {
                    boolean mine = turn >= 0 && seats[turn] == s;
                    Txt.bar(p, "<#9AA4B2>Tes cartes</#9AA4B2> " + Cards.row(s.hole) + (s.folded ? " <#5A6270>(couché)" : "")
                            + " <#5A6270>·</#5A6270> <#9AA4B2>pot " + Txt.n(pot()) + " · tapis " + Txt.n(s.stack)
                            + (mine ? " <#5A6270>·</#5A6270> <#E0B864><b>À toi</b> " + turnLeft + " s" : ""));
                }
            }
        }
    }

    private long pot() {
        long pot = 0;
        for (Seat s : seats) {
            if (s != null) {
                pot += s.committed;
            }
        }
        return pot;
    }

    @Override
    public void shutdown() {
        for (Seat s : seats) {
            if (s == null || s.pending) {
                continue;
            }
            // main interrompue : chacun récupère son tapis et ce qu'il avait engagé dans la main
            long back = (s.gone ? 0 : s.stack) + (state == State.BETTING ? s.committed : 0);
            if (back > 0 || !s.gone) {
                plugin.wallet().cashOutNow(s.uuid, back, "poker");
            }
        }
    }
    // ------------------------------------------------------------------ affichage

    private void render() {
        Holo.set(label, "<#C79BFF><b>POKER</b></#C79BFF> <#E6EDF3>" + name + "\n<#9AA4B2>Blindes " + Txt.n(sb) + " / " + Txt.n(bb) + "\n"
                + switch (state) {
            case WAITING -> ready() == 0 ? "<#9AA4B2>Clic sur la table pour t'asseoir" : "<#9AA4B2>En attente d'un 2e joueur";
            case STARTING -> "<#62CFF4>Prochaine main dans " + countdown + " s";
            case BETTING -> turn >= 0 && seats[turn] != null ? "<#9AA4B2>Au tour de <#E6EDF3>" + seats[turn].name + " <#5A6270>(" + turnLeft + " s)" : "";
            case SHOWDOWN -> "<#E0B864>Abattage";
        });
        if (state == State.BETTING || state == State.SHOWDOWN) {
            StringBuilder b = new StringBuilder();
            for (int k = 0; k < 5; k++) {
                b.append(k < board.size() ? board.get(k).mini() : "<#2A3140>▒▒</#2A3140>").append(' ');
            }
            Holo.set(center, b.toString().trim() + "\n<#E0B864>Pot : " + Txt.n(pot()) + " OC"
                    + (state == State.SHOWDOWN && !result.isEmpty() ? "\n" + result : ""));
        } else {
            Holo.set(center, "");
        }
        for (int i = 0; i < 6; i++) {
            Seat s = seats[i];
            if (s == null || s.pending || (s.gone && !s.inHand)) {
                Holo.set(seatText[i], s == null ? "<#5A6270>libre" : "<#5A6270>…");
                continue;
            }
            StringBuilder sb = new StringBuilder();
            sb.append(turn == i && state == State.BETTING ? "<#E0B864><b>▶ " : "<#E6EDF3>").append(s.name).append("</b>");
            if (i == dealer && state != State.WAITING) {
                sb.append(" <#F2F4F7>Ⓓ");
            }
            sb.append("\n").append(Txt.oc(s.stack));
            if (s.inHand) {
                if (state == State.SHOWDOWN && !s.folded) {
                    sb.append("\n").append(Cards.row(s.hole));
                } else if (!s.folded) {
                    sb.append("\n").append(Cards.hidden()).append(' ').append(Cards.hidden());
                }
                if (s.round > 0 && state == State.BETTING) {
                    sb.append("\n<#62CFF4>mise ").append(Txt.n(s.round));
                }
            }
            if (!s.last.isEmpty()) {
                sb.append("\n<#9AA4B2>").append(s.last);
            }
            Holo.set(seatText[i], sb.toString());
        }
        screens();
    }

    // ------------------------------------------------------------------ écran HD

    private static final int GOLD = 0xE0B864, LIGHT = 0xE6EDF3, GREY = 0x9AA4B2, RED = 0xE0484A, GREEN = 0x7BD88F, VIOLET = 0xC79BFF;
    private static final int[] HEAD_SLOTS = {9, 18, 27, 17, 26, 35};
    private final java.util.Map<UUID, Long> raiseTarget = new java.util.HashMap<>();

    private int handNo;
    /** Dernier moment où l'écran a été rouvert d'office, par joueur : une fois par main, puis une fois à chacun de ses tours. */
    private final java.util.Map<UUID, String> autoOpened = new java.util.HashMap<>();

    private void screens() {
        for (int i = 0; i < 6; i++) {
            Seat s = seats[i];
            if (s != null && !s.gone && !s.pending) {
                Player p = Bukkit.getPlayer(s.uuid);
                if (p != null) {
                    boolean open = false;
                    if (state == State.BETTING && s.inHand && !s.folded) {
                        String prev = autoOpened.get(s.uuid);
                        boolean newHand = prev == null || !prev.startsWith(handNo + "|");
                        if (turn == i) {
                            String key = handNo + "|" + street + "/" + currentBet;
                            open = !key.equals(prev);
                            autoOpened.put(s.uuid, key);
                        } else if (newHand) {
                            open = true;
                            autoOpened.put(s.uuid, handNo + "|");
                        }
                    }
                    screen(p, i, open);
                }
            }
        }
    }

    private void openScreens() {
        for (int i = 0; i < 6; i++) {
            Seat s = seats[i];
            if (s != null && s.inHand && !s.gone) {
                Player p = Bukkit.getPlayer(s.uuid);
                if (p != null) {
                    screen(p, i, true);
                }
            }
        }
    }

    private long minRaiseTo(Seat s) {
        return Math.min(currentBet + minRaise, s.round + s.stack);
    }

    private long target(Seat s) {
        long min = minRaiseTo(s), max = s.round + s.stack;
        long t = raiseTarget.getOrDefault(s.uuid, min);
        return Math.max(min, Math.min(max, t));
    }

    private void nudge(Player p, int dir) {
        int i = seatOf(p.getUniqueId());
        if (i < 0) {
            return;
        }
        Seat s = seats[i];
        raiseTarget.put(s.uuid, target(s) + dir * bb);
        screen(p, i, false);
    }

    private static String plain(String mini) {
        return Txt.MM.stripTags(mini);
    }

    private void screen(Player p, int i, boolean open) {
        fr.orbit.casino.hud.Screens screens = plugin.screens();
        Seat me = seats[i];
        if (screens == null || me == null || !screens.available(p)) {
            return;
        }
        boolean live = state == State.BETTING || state == State.SHOWDOWN;
        boolean myTurn = state == State.BETTING && turn == i;
        long toCall = Math.max(0, currentBet - me.round);
        fr.orbit.casino.hud.Screens.Frame f = new fr.orbit.casino.hud.Screens.Frame("poker-" + id);
        fr.orbit.casino.hud.Hud.Builder t = fr.orbit.casino.hud.Hud.title().background(fr.orbit.casino.hud.Hud.BG_POKER)
                .text(0, 8, fr.orbit.casino.hud.Hud.Align.LEFT, "POKER · " + name.toUpperCase(java.util.Locale.ROOT), VIOLET)
                .text(0, 168, fr.orbit.casino.hud.Hud.Align.RIGHT, "BLINDES " + Txt.n(sb) + "/" + Txt.n(bb), GREY);
        if (live) {
            t.text(1, 88, fr.orbit.casino.hud.Hud.Align.CENTER, "POT " + Txt.n(pot()) + " OC", GOLD);
        }

        // tableau : 5 emplacements, les cartes arrivent au flop, au turn, à la river
        if (live) {
            for (int k = 0; k < 5; k++) {
                if (k < board.size()) {
                    f.card(11 + k, board.get(k));
                } else {
                    f.placeholder(11 + k);
                }
            }
        }
        // ses deux cartes
        if (me.inHand && me.hole.size() == 2) {
            f.card(30, me.hole.get(0));
            f.card(32, me.hole.get(1));
        }

        // les joueurs autour de la table
        for (int k = 0; k < 6; k++) {
            Seat s = seats[k];
            if (s == null || s.pending || (s.gone && !s.inHand)) {
                continue;
            }
            boolean turnK = state == State.BETTING && turn == k;
            List<String> lore = new ArrayList<>();
            lore.add("<#9AA4B2>Tapis : <#E0B864>" + Txt.n(s.stack) + " OC");
            if (k == dealer && state != State.WAITING) {
                lore.add("<#F2F4F7>Donneur");
            }
            if (s.inHand && s.round > 0 && state == State.BETTING) {
                lore.add("<#62CFF4>Mise : " + Txt.n(s.round));
            }
            if (!s.last.isEmpty()) {
                lore.add("<#9AA4B2>" + s.last);
            }
            if (state == State.SHOWDOWN && s.inHand && !s.folded && s.hole.size() == 2) {
                lore.add(Cards.row(s.hole));
            }
            String color = s.folded || s.gone ? "<#5A6270>" : turnK ? "<#E0B864>" : "<#E6EDF3>";
            String title = color + "<b>" + (turnK ? "▶ " : "") + s.name + (k == i ? " (toi)" : "");
            org.bukkit.inventory.ItemStack head = new org.bukkit.inventory.ItemStack(Material.PLAYER_HEAD);
            org.bukkit.inventory.meta.SkullMeta meta = (org.bukkit.inventory.meta.SkullMeta) head.getItemMeta();
            Player owner = Bukkit.getPlayer(s.uuid);
            if (owner != null) {
                meta.setPlayerProfile(owner.getPlayerProfile());
            }
            meta.setEnchantmentGlintOverride(turnK);
            head.setItemMeta(meta);
            Menu.named(head, title, lore.toArray(new String[0]));
            f.item(HEAD_SLOTS[k], head, "head|" + s.uuid + "|" + title + "|" + String.join("|", lore), null);
        }

        // statut
        String status;
        int color = LIGHT;
        switch (state) {
            case WAITING -> {
                status = "EN ATTENTE D'UN 2E JOUEUR";
                color = GREY;
            }
            case STARTING -> {
                status = "PROCHAINE MAIN DANS " + countdown + " S";
                color = GREY;
            }
            case BETTING -> {
                if (!me.inHand) {
                    status = "MAIN EN COURS · ATTENDS LA SUIVANTE";
                    color = GREY;
                } else if (me.folded) {
                    status = "TU T'ES COUCHÉ";
                    color = GREY;
                } else if (myTurn) {
                    String call = toCall <= 0 ? "PAROLE POSSIBLE" : "SUIVRE " + Txt.n(Math.min(toCall, me.stack));
                    status = "À TOI · " + call + (me.stack > toCall ? " · RELANCE " + Txt.n(target(me)) : "");
                    color = GOLD;
                } else if (me.allIn) {
                    status = "TAPIS · ON ATTEND LES AUTRES";
                    color = VIOLET;
                } else {
                    status = "AU TOUR DE " + (turn >= 0 && seats[turn] != null ? seats[turn].name : "...");
                    color = GREY;
                }
            }
            default -> {
                status = result.isEmpty() ? "ABATTAGE" : plain(result);
                color = GREEN;
            }
        }
        t.text(2, 88, fr.orbit.casino.hud.Hud.Align.CENTER, fr.orbit.casino.hud.Hud.fit(status, 168), color);

        String mine = "TAPIS " + Txt.n(me.stack) + " OC";
        if (me.inHand && !me.folded && me.hole.size() == 2 && board.size() >= 3) {
            List<Cards.Card> seven = new ArrayList<>(board);
            seven.addAll(me.hole);
            mine = PokerHand.best(seven).name().toUpperCase(java.util.Locale.ROOT) + " · " + mine;
        }
        t.text(3, 8, fr.orbit.casino.hud.Hud.Align.LEFT, fr.orbit.casino.hud.Hud.fit(mine, 120), LIGHT);
        f.title(t.build());

        // boutons
        boolean canRaise = myTurn && me.stack > toCall;
        if (state == State.BETTING && me.inHand && !me.folded) {
            f.button(45, myTurn ? "btn_coucher" : "btn_coucher_off", true, () -> action(p, "coucher", null),
                    "<#E0484A><b>Se coucher", "<#9AA4B2>Abandonner la main");
            if (toCall <= 0) {
                f.button(47, myTurn ? "btn_parole" : "btn_parole_off", true, () -> action(p, "parole", null),
                        "<#9AA4B2><b>Parole", "<#9AA4B2>Ne rien miser de plus");
            } else {
                f.button(47, myTurn ? "btn_suivre" : "btn_suivre_off", true, () -> action(p, "suivre", null),
                        "<#7BD88F><b>Suivre " + Txt.n(Math.min(toCall, me.stack)), "<#9AA4B2>Égaler la mise");
            }
            long to = target(me);
            f.button(49, canRaise ? "btn_relancer" : "btn_relancer_off", true, () -> action(p, "relancer", String.valueOf(target(me))),
                    "<#E0B864><b>Relancer à " + Txt.n(to), "<#9AA4B2>Montant total de ta mise sur ce tour", "<#9AA4B2>Règle-le avec + et −");
            f.button(51, myTurn ? "btn_tapis" : "btn_tapis_off", true, () -> action(p, "tapis", null),
                    "<#C79BFF><b>Tapis " + Txt.n(me.stack + me.round), "<#9AA4B2>Tout miser");
            if (canRaise) {
                f.button(43, "btn_moins", false, () -> nudge(p, -1), "<#62CFF4><b>− " + Txt.n(bb), "<#9AA4B2>Relance : " + Txt.n(to));
                f.button(44, "btn_plus", false, () -> nudge(p, 1), "<#62CFF4><b>+ " + Txt.n(bb), "<#9AA4B2>Relance : " + Txt.n(to));
            }
        }
        f.button(53, "btn_quitter", false, () -> action(p, "quitter", null), "<#E0484A><b>Quitter la table",
                "<#9AA4B2>Tu repars avec ton tapis");
        if (myTurn) {
            f.timer(8, turnLeft);
        } else if (state == State.STARTING) {
            f.timer(8, countdown);
        }
        screens.show(p, f, open);
    }

    private void prompt(Player p) {
        int i = seatOf(p.getUniqueId());
        if (i < 0) {
            return;
        }
        Seat s = seats[i];
        if (plugin.screens() != null && plugin.screens().available(p)) {
            screen(p, i, true);
            return;
        }
        if (state != State.BETTING || turn != i) {
            p.sendMessage(Txt.mm(Txt.PREFIX + "<#9AA4B2>Tu es assis à la " + name + " avec " + Txt.oc(s.stack) + ". ")
                    .append(Txt.button("Quitter", "#5A6270", "/table quitter", "Partir avec ton tapis")));
            return;
        }
        long toCall = currentBet - s.round;
        long min = Math.min(currentBet + minRaise, s.round + s.stack);
        long potSize = pot() + Math.max(0, toCall);
        long potRaise = Math.min(currentBet + potSize, s.round + s.stack);
        Component line = Txt.mm(Txt.PREFIX + Cards.row(s.hole) + " <#9AA4B2>· à suivre " + Txt.n(Math.max(0, toCall)) + "  ")
                .append(Txt.button("Se coucher", "#E0484A", "/table coucher", "Abandonner la main"))
                .append(Component.space());
        if (toCall <= 0) {
            line = line.append(Txt.button("Parole", "#9AA4B2", "/table parole", "Ne rien miser de plus"));
        } else {
            line = line.append(Txt.button("Suivre " + Txt.n(Math.min(toCall, s.stack)), "#7BD88F", "/table suivre",
                    "Égaler la mise"));
        }
        if (s.stack > toCall) {
            line = line.append(Component.space()).append(Txt.button("Relancer à " + Txt.n(min), "#E0B864", "/table relancer " + min,
                    "Relance minimum · tape /table relancer <montant> pour choisir"));
            if (potRaise > min) {
                line = line.append(Component.space()).append(Txt.button("Pot " + Txt.n(potRaise), "#E0B864", "/table relancer " + potRaise,
                        "Relancer de la taille du pot"));
            }
        }
        line = line.append(Component.space()).append(Txt.button("Tapis " + Txt.n(s.stack + s.round), "#C79BFF", "/table tapis",
                "Tout miser"));
        p.sendMessage(line);
    }

    private void say(String msg, UUID except) {
        for (Seat s : seats) {
            if (s != null && !s.gone && !Objects.equals(s.uuid, except)) {
                Player p = Bukkit.getPlayer(s.uuid);
                if (p != null) {
                    Txt.send(p, msg);
                }
            }
        }
    }

    public String id() {
        return id;
    }
}
