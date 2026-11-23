package fr.orbit.casino.game;

import fr.orbit.casino.Holo;
import fr.orbit.casino.OrbitCasino;
import fr.orbit.casino.Room;
import fr.orbit.casino.Txt;
import fr.orbit.casino.hud.Hud;
import fr.orbit.casino.hud.Screens;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Une table de blackjack : 5 places contre le croupier. Sabot de 6 jeux, le croupier reste à 17,
 * blackjack payé 3 pour 2, doubler sur deux cartes, séparer une fois.
 */
public final class Blackjack implements Seated {

    private enum State { IDLE, BETTING, PLAYING, DEALER, RESULT }

    private static final class Hand {
        final List<Cards.Card> cards = new ArrayList<>();
        long bet;
        boolean done, doubled, split, natural;
    }

    private static final class Seat {
        UUID uuid;
        String name;
        long bet;
        final List<Hand> hands = new ArrayList<>();
        long lastNet;
        String lastVerdict;
    }

    private final OrbitCasino plugin;
    private final World world;
    private final int number;
    private final int cz;
    private final Seat[] seats = new Seat[5];
    private final TextDisplay[] seatText = new TextDisplay[5];
    private TextDisplay dealerText;
    private TextDisplay label;
    private final Cards.Shoe shoe = new Cards.Shoe(6);
    private final List<Cards.Card> dealer = new ArrayList<>();
    private final List<Long> betChoices = new ArrayList<>();
    private State state = State.IDLE;
    private int countdown;
    private int turnSeat = -1, turnHand;
    private int turnLeft;

    public Blackjack(OrbitCasino plugin, World world, int number, int cz) {
        this.plugin = plugin;
        this.world = world;
        this.number = number;
        this.cz = cz;
        for (Object o : plugin.getConfig().getList("blackjack.mises", List.of(10, 50, 100, 250, 500, 1000))) {
            betChoices.add(Long.parseLong(String.valueOf(o)));
        }
    }

    public int cz() {
        return cz;
    }

    private Location seatPos(int i) {
        return new Location(world, Room.BJ_X - 2 + i + 0.5, Room.Y, cz + 2.5, 180f, 0f);
    }

    public void spawn() {
        Holo.npc(new Location(world, Room.BJ_X + 0.5, Room.Y, cz - 0.5, 0f, 0f), "<#E0B864>Croupier · Blackjack " + number,
                Villager.Profession.CLERIC);
        label = Holo.text(new Location(world, Room.BJ_X + 0.5, Room.Y + 3.1, cz - 0.5), "", 1.0f, false);
        dealerText = Holo.text(new Location(world, Room.BJ_X + 0.5, Room.Y + 1.25, cz + 0.6), "", 0.55f, false);
        for (int i = 0; i < 5; i++) {
            seatText[i] = Holo.text(new Location(world, Room.BJ_X - 2 + i + 0.5, Room.Y + 1.15, cz + 1.55), "", 0.42f, false);
        }
        render();
    }

    // ------------------------------------------------------------------ places

    @Override
    public boolean has(UUID player) {
        return seatOf(player) >= 0;
    }

    private int seatOf(UUID uuid) {
        for (int i = 0; i < 5; i++) {
            if (seats[i] != null && seats[i].uuid.equals(uuid)) {
                return i;
            }
        }
        return -1;
    }

    /** S'asseoir à la place libre la plus proche de l'endroit cliqué. */
    public void sit(Player p, double clickedX) {
        if (has(p.getUniqueId())) {
            prompt(p);
            return;
        }
        int best = -1;
        double dist = Double.MAX_VALUE;
        for (int i = 0; i < 5; i++) {
            double d = Math.abs(seatPos(i).getX() - clickedX);
            if (seats[i] == null && d < dist) {
                best = i;
                dist = d;
            }
        }
        if (best < 0) {
            Txt.send(p, "<#E0484A>La table " + number + " est complète.");
            return;
        }
        if (plugin.casino().seatedAnywhere(p.getUniqueId())) {
            Txt.send(p, "<#E0484A>Tu es déjà assis à une autre table. <#9AA4B2>/table quitter");
            return;
        }
        Seat s = new Seat();
        s.uuid = p.getUniqueId();
        s.name = p.getName();
        seats[best] = s;
        Location to = seatPos(best);
        to.setYaw(0f);
        p.teleport(to);
        Txt.send(p, "Bienvenue à la table de <b>blackjack " + number + "</b>. Le croupier tire jusqu'à 17, le blackjack paie 3 pour 2.");
        Txt.sound(p, Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, 1f);
        prompt(p);
        render();
    }

    @Override
    public void leave(Player p, String reason) {
        int i = seatOf(p.getUniqueId());
        if (i < 0) {
            return;
        }
        Seat s = seats[i];
        boolean inPlay = state == State.PLAYING || state == State.DEALER;
        if (!inPlay && s.bet > 0 && s.hands.isEmpty()) {
            plugin.wallet().credit(s.uuid, s.bet, "blackjack", "mise rendue", false);
        }
        seats[i] = null;
        if (plugin.screens() != null) {
            plugin.screens().close(p);
        }
        if (p.isOnline() && reason != null) {
            Txt.send(p, "Tu quittes la table " + number + (reason.isEmpty() ? "." : " (" + reason + ")."));
        }
        if (state == State.PLAYING && turnSeat == i) {
            nextTurn();
        }
        if (state == State.BETTING && noBets()) {
            state = State.IDLE;
        }
        render();
    }

    private boolean noBets() {
        for (Seat s : seats) {
            if (s != null && s.bet > 0) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ actions

    @Override
    public void action(Player p, String action, String arg) {
        int i = seatOf(p.getUniqueId());
        if (i < 0) {
            return;
        }
        switch (action) {
            case "miser" -> bet(p, i, arg);
            case "tirer" -> play(p, i, "hit");
            case "rester" -> play(p, i, "stand");
            case "doubler" -> play(p, i, "double");
            case "separer" -> play(p, i, "split");
            case "quitter" -> leave(p, "");
            default -> prompt(p);
        }
    }

    private void bet(Player p, int i, String arg) {
        Seat s = seats[i];
        if (state != State.IDLE && state != State.BETTING) {
            Txt.bar(p, "<#E0484A>Attends la fin de la main pour miser.");
            return;
        }
        if (s.bet > 0) {
            Txt.bar(p, "<#9AA4B2>Ta mise est déjà posée : " + Txt.oc(s.bet));
            return;
        }
        long amount;
        try {
            amount = Long.parseLong(arg == null ? "" : arg.trim());
        } catch (NumberFormatException e) {
            prompt(p);
            return;
        }
        long max = plugin.getConfig().getLong("blackjack.mise-max", 1000);
        if (amount < 1 || amount > max) {
            Txt.send(p, "<#E0484A>Mise entre 1 et " + Txt.n(max) + " OC.");
            return;
        }
        s.bet = -1; // réservé pendant le retrait
        plugin.wallet().debit(p, amount, "blackjack", "table " + number, ok -> {
            if (seats[i] != s) {
                if (ok) {
                    plugin.wallet().credit(p.getUniqueId(), amount, "blackjack", "mise rendue", false);
                }
                return;
            }
            if (!ok || (state != State.IDLE && state != State.BETTING)) {
                s.bet = 0;
                if (ok) {
                    plugin.wallet().credit(p.getUniqueId(), amount, "blackjack", "mise rendue", false);
                }
                Txt.bar(p, "<#E0484A>" + (ok ? "Trop tard pour cette main." : "Pas assez d'Orbit Coins."));
                return;
            }
            s.bet = amount;
            Txt.sound(p, Sound.BLOCK_CHAIN_PLACE, 1.4f);
            if (state == State.IDLE) {
                state = State.BETTING;
                countdown = plugin.getConfig().getInt("blackjack.attente-mises", 15);
                for (Seat o : seats) {
                    if (o != null && o.bet == 0) {
                        Player op = Bukkit.getPlayer(o.uuid);
                        if (op != null) {
                            Txt.send(op, "La main commence dans " + countdown + " s : mise maintenant !");
                        }
                    }
                }
            }
            if (allBet()) {
                countdown = Math.min(countdown, 2);
            }
            render();
        });
    }

    private boolean allBet() {
        for (Seat s : seats) {
            if (s != null && s.bet <= 0) {
                return false;
            }
        }
        return true;
    }

    private void play(Player p, int i, String move) {
        if (state != State.PLAYING || turnSeat != i) {
            Txt.bar(p, "<#E0484A>Ce n'est pas ton tour.");
            return;
        }
        Seat s = seats[i];
        Hand h = s.hands.get(turnHand);
        switch (move) {
            case "hit" -> {
                h.cards.add(shoe.draw());
                sound(Sound.ITEM_BOOK_PAGE_TURN);
                int v = value(h.cards);
                if (v >= 21) {
                    h.done = true;
                    nextTurn();
                } else {
                    turnLeft = time();
                    render();
                    prompt(p);
                }
            }
            case "stand" -> {
                h.done = true;
                nextTurn();
            }
            case "double" -> {
                if (h.cards.size() != 2) {
                    return;
                }
                long extra = h.bet;
                turnLeft = time();
                plugin.wallet().debit(p, extra, "blackjack", "doubler", ok -> {
                    if (!ok) {
                        Txt.bar(p, "<#E0484A>Pas assez d'Orbit Coins pour doubler.");
                        return;
                    }
                    if (state != State.PLAYING || turnSeat != i || seats[i] != s || h.done) {
                        plugin.wallet().credit(p.getUniqueId(), extra, "blackjack", "doubler annulé", false);
                        return;
                    }
                    h.bet += extra;
                    h.doubled = true;
                    h.cards.add(shoe.draw());
                    h.done = true;
                    sound(Sound.ITEM_BOOK_PAGE_TURN);
                    nextTurn();
                });
            }
            case "split" -> {
                if (!canSplit(s, h)) {
                    return;
                }
                long extra = h.bet;
                turnLeft = time();
                plugin.wallet().debit(p, extra, "blackjack", "séparer", ok -> {
                    if (!ok) {
                        Txt.bar(p, "<#E0484A>Pas assez d'Orbit Coins pour séparer.");
                        return;
                    }
                    if (state != State.PLAYING || turnSeat != i || seats[i] != s || h.done || !canSplit(s, h)) {
                        plugin.wallet().credit(p.getUniqueId(), extra, "blackjack", "séparer annulé", false);
                        return;
                    }
                    Hand second = new Hand();
                    second.bet = extra;
                    second.split = true;
                    h.split = true;
                    second.cards.add(h.cards.remove(1));
                    h.cards.add(shoe.draw());
                    second.cards.add(shoe.draw());
                    s.hands.add(turnHand + 1, second);
                    if (h.cards.get(0).rank() == 14) { // as séparés : une carte chacun
                        h.done = true;
                        second.done = true;
                        nextTurn();
                        return;
                    }
                    if (value(h.cards) == 21) {
                        h.done = true;
                        nextTurn();
                        return;
                    }
                    render();
                    prompt(p);
                });
            }
            default -> {
            }
        }
    }

    private static boolean canSplit(Seat s, Hand h) {
        return s.hands.size() == 1 && h.cards.size() == 2 && cardValue(h.cards.get(0)) == cardValue(h.cards.get(1));
    }

    private int time() {
        return plugin.getConfig().getInt("blackjack.temps-de-jeu", 30);
    }

    // ------------------------------------------------------------------ déroulé

    @Override
    public void tick() {
        // joueur parti trop loin
        for (int i = 0; i < 5; i++) {
            Seat s = seats[i];
            if (s == null) {
                continue;
            }
            Player p = Bukkit.getPlayer(s.uuid);
            if (p == null) {
                continue;
            }
            if (!p.getWorld().equals(world) || p.getLocation().distanceSquared(seatPos(i)) > 64) {
                leave(p, "tu t'es éloigné");
            }
        }
        switch (state) {
            case BETTING -> {
                if (noBets()) {
                    state = State.IDLE;
                } else if (--countdown <= 0) {
                    deal();
                }
                render();
            }
            case PLAYING -> {
                if (--turnLeft <= 0 && turnSeat >= 0 && seats[turnSeat] != null) {
                    Player p = Bukkit.getPlayer(seats[turnSeat].uuid);
                    if (p != null) {
                        Txt.bar(p, "<#E0484A>Temps écoulé : tu restes.");
                    }
                    seats[turnSeat].hands.get(turnHand).done = true;
                    nextTurn();
                } else if (turnSeat >= 0 && seats[turnSeat] != null) {
                    Player p = Bukkit.getPlayer(seats[turnSeat].uuid);
                    if (p != null) {
                        Hand h = seats[turnSeat].hands.get(turnHand);
                        if (plugin.screens() != null && plugin.screens().isOpen(p)) {
                            screen(p, turnSeat, false);
                        } else {
                            Txt.bar(p, "<#E0B864>À toi</#E0B864> <#5A6270>·</#5A6270> " + Cards.row(h.cards) + " <#9AA4B2>= " + show(h.cards)
                                    + " <#5A6270>·</#5A6270> <#9AA4B2>" + turnLeft + " s");
                        }
                    }
                }
            }
            default -> {
            }
        }
    }

    private void deal() {
        if (shoe.low()) {
            shoe.shuffle();
            say("Le croupier mélange le sabot.");
        }
        dealer.clear();
        boolean any = false;
        for (Seat s : seats) {
            if (s != null) {
                s.hands.clear();
                if (s.bet > 0) {
                    Hand h = new Hand();
                    h.bet = s.bet;
                    s.hands.add(h);
                    any = true;
                } else if (s.bet < 0) {
                    s.bet = 0; // retrait encore en cours : la place passe son tour
                }
            }
        }
        if (!any) {
            state = State.IDLE;
            return;
        }
        for (int round = 0; round < 2; round++) {
            for (Seat s : seats) {
                if (s != null && !s.hands.isEmpty()) {
                    s.hands.get(0).cards.add(shoe.draw());
                }
            }
            dealer.add(shoe.draw());
        }
        sound(Sound.ITEM_BOOK_PAGE_TURN);
        // blackjacks naturels
        boolean dealerBj = value(dealer) == 21;
        for (Seat s : seats) {
            if (s != null && !s.hands.isEmpty()) {
                Hand h = s.hands.get(0);
                if (value(h.cards) == 21) {
                    h.natural = true;
                    h.done = true;
                }
            }
        }
        if (dealerBj) {
            state = State.RESULT;
            openScreens();
            say("Le croupier a un <b>blackjack</b> !");
            finishRound();
            return;
        }
        state = State.PLAYING;
        turnSeat = -1;
        turnHand = 0;
        openScreens();
        nextTurn();
    }

    /** Passe à la prochaine main à jouer ; s'il n'y en a plus, le croupier joue. */
    private void nextTurn() {
        if (state != State.PLAYING) {
            return;
        }
        int start = Math.max(turnSeat, 0);
        for (int i = start; i < 5; i++) {
            Seat s = seats[i];
            if (s == null) {
                continue;
            }
            for (int h = 0; h < s.hands.size(); h++) {
                if (!s.hands.get(h).done) {
                    turnSeat = i;
                    turnHand = h;
                    turnLeft = time();
                    render();
                    Player p = Bukkit.getPlayer(s.uuid);
                    if (p != null) {
                        prompt(p);
                        Txt.sound(p, Sound.BLOCK_NOTE_BLOCK_PLING, 1.6f);
                    } else {
                        s.hands.get(h).done = true;
                        continue;
                    }
                    return;
                }
            }
        }
        turnSeat = -1;
        dealerPlays();
    }

    private void dealerPlays() {
        state = State.DEALER;
        render();
        boolean anyAlive = false;
        for (Seat s : seats) {
            if (s != null) {
                for (Hand h : s.hands) {
                    if (value(h.cards) <= 21 && !h.natural) {
                        anyAlive = true;
                    }
                }
            }
        }
        if (!anyAlive) {
            Bukkit.getScheduler().runTaskLater(plugin, this::finishRound, 20L);
            return;
        }
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (value(dealer) < 17) {
                dealer.add(shoe.draw());
                sound(Sound.ITEM_BOOK_PAGE_TURN);
                render();
            } else {
                task.cancel();
                finishRound();
            }
        }, 20L, 20L);
    }

    private void finishRound() {
        state = State.RESULT;
        int d = value(dealer);
        boolean dealerBj = d == 21 && dealer.size() == 2;
        for (Seat s : seats) {
            if (s == null) {
                continue;
            }
            Player p = Bukkit.getPlayer(s.uuid);
            long total = 0, staked = 0;
            StringBuilder lines = new StringBuilder();
            for (Hand h : s.hands) {
                int v = value(h.cards);
                long pay;
                String verdict;
                staked += h.bet;
                if (h.natural && !h.split) {
                    pay = dealerBj ? h.bet : h.bet + h.bet * 3 / 2;
                    verdict = dealerBj ? "<#9AA4B2>égalité" : "<#E0B864><b>BLACKJACK</b>";
                } else if (v > 21) {
                    pay = 0;
                    verdict = "<#E0484A>sauté";
                } else if (dealerBj) {
                    pay = 0;
                    verdict = "<#E0484A>perdu";
                } else if (d > 21 || v > d) {
                    pay = h.bet * 2;
                    verdict = "<#7BD88F>gagné";
                } else if (v == d) {
                    pay = h.bet;
                    verdict = "<#9AA4B2>égalité";
                } else {
                    pay = 0;
                    verdict = "<#E0484A>perdu";
                }
                total += pay;
                lines.append(lines.isEmpty() ? "" : "  ").append(Cards.row(h.cards)).append(" ").append(verdict);
            }
            if (total > 0) {
                plugin.wallet().credit(s.uuid, total, "blackjack", "table " + number, total > staked);
            }
            s.lastNet = total - staked;
            s.lastVerdict = s.hands.isEmpty() ? null
                    : s.hands.size() == 1 && s.hands.get(0).natural && !s.hands.get(0).split && !dealerBj ? "bj"
                    : s.lastNet > 0 ? "win" : s.lastNet == 0 ? "push" : "lose";
            if (p != null && !s.hands.isEmpty()) {
                long net = total - staked;
                Txt.send(p, "Croupier : " + Cards.row(dealer) + " <#9AA4B2>= " + (d > 21 ? "sauté" : d) + "</#9AA4B2> · " + lines
                        + " · " + (net > 0 ? "<#7BD88F><b>+" + Txt.n(net) + " OC</b>" : net == 0 ? "<#9AA4B2>±0" : "<#E0484A>" + Txt.n(net) + " OC"));
                Txt.sound(p, net > 0 ? Sound.ENTITY_PLAYER_LEVELUP : net == 0 ? Sound.BLOCK_NOTE_BLOCK_HAT : Sound.ENTITY_VILLAGER_NO,
                        net > 0 ? 1.4f : 1f);
            }
            s.bet = 0;
        }
        render();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Seat s : seats) {
                if (s != null) {
                    s.hands.clear();
                    s.lastVerdict = null;
                    Player p = Bukkit.getPlayer(s.uuid);
                    if (p != null) {
                        prompt(p);
                    }
                }
            }
            dealer.clear();
            state = State.IDLE;
            render();
        }, 100L);
    }

    @Override
    public void shutdown() {
        for (Seat s : seats) {
            if (s == null) {
                continue;
            }
            long back = state == State.BETTING || state == State.IDLE ? Math.max(0, s.bet) : 0;
            if (state == State.PLAYING || state == State.DEALER) {
                back = 0;
                for (Hand h : s.hands) {
                    back += h.bet; // main interrompue : mise rendue
                }
            }
            if (back > 0) {
                plugin.wallet().cashOutNow(s.uuid, back, "blackjack");
            }
        }
    }

    // ------------------------------------------------------------------ calcul et affichage

    static int cardValue(Cards.Card c) {
        return c.rank() == 14 ? 11 : Math.min(10, c.rank());
    }

    static int value(List<Cards.Card> cards) {
        int total = 0, aces = 0;
        for (Cards.Card c : cards) {
            total += cardValue(c);
            if (c.rank() == 14) {
                aces++;
            }
        }
        while (total > 21 && aces-- > 0) {
            total -= 10;
        }
        return total;
    }

    private static String show(List<Cards.Card> cards) {
        int v = value(cards);
        return v > 21 ? "<#E0484A>" + v + "</#E0484A>" : String.valueOf(v);
    }

    private void render() {
        String head = "<#E0B864><b>BLACKJACK " + number + "</b></#E0B864>\n";
        Holo.set(label, head + switch (state) {
            case IDLE -> "<#9AA4B2>Clic sur la table pour t'asseoir";
            case BETTING -> "<#62CFF4>Mises : " + countdown + " s";
            case PLAYING -> turnSeat >= 0 && seats[turnSeat] != null ? "<#9AA4B2>Au tour de <#E6EDF3>" + seats[turnSeat].name : "";
            case DEALER -> "<#9AA4B2>Le croupier joue";
            case RESULT -> "<#9AA4B2>Résultats";
        });
        if (dealer.isEmpty()) {
            Holo.set(dealerText, "");
        } else if (state == State.PLAYING) {
            Holo.set(dealerText, dealer.get(0).mini() + " " + Cards.hidden());
        } else {
            Holo.set(dealerText, Cards.row(dealer) + " <#9AA4B2>= " + show(dealer));
        }
        for (int i = 0; i < 5; i++) {
            Seat s = seats[i];
            if (s == null) {
                Holo.set(seatText[i], "<#5A6270>libre");
                continue;
            }
            StringBuilder sb = new StringBuilder();
            sb.append(state == State.PLAYING && turnSeat == i ? "<#E0B864><b>▶ " : "<#E6EDF3>").append(s.name).append("</b>");
            if (s.hands.isEmpty()) {
                sb.append("\n").append(s.bet > 0 ? Txt.oc(s.bet) : "<#5A6270>pas de mise");
            }
            for (int h = 0; h < s.hands.size(); h++) {
                Hand hand = s.hands.get(h);
                sb.append("\n").append(state == State.PLAYING && turnSeat == i && turnHand == h ? "<#E0B864>›</#E0B864> " : "")
                        .append(Cards.row(hand.cards)).append(" <#9AA4B2>= ").append(show(hand.cards))
                        .append("\n").append(Txt.oc(hand.bet)).append(hand.doubled ? " <#62CFF4>x2" : "");
            }
            Holo.set(seatText[i], sb.toString());
        }
        for (int i = 0; i < 5; i++) {
            if (seats[i] != null) {
                Player p = Bukkit.getPlayer(seats[i].uuid);
                if (p != null) {
                    screen(p, i, false);
                }
            }
        }
    }

    /** Ouvre l'écran de tous ceux qui jouent la main (début de main). */
    private void openScreens() {
        for (int i = 0; i < 5; i++) {
            if (seats[i] != null && !seats[i].hands.isEmpty()) {
                Player p = Bukkit.getPlayer(seats[i].uuid);
                if (p != null) {
                    screen(p, i, true);
                }
            }
        }
    }

    // ------------------------------------------------------------------ écran HD

    private static final int GOLD = 0xE0B864, LIGHT = 0xE6EDF3, GREY = 0x9AA4B2, RED = 0xE0484A, GREEN = 0x7BD88F, ICE = 0x62CFF4;
    private static final long[] CHIPS = {1000, 500, 250, 100, 50, 10};

    static String chipModel(long amount) {
        for (long c : CHIPS) {
            if (amount >= c) {
                return "chip_" + c;
            }
        }
        return "chip_10";
    }

    private void screen(Player p, int i, boolean open) {
        Screens screens = plugin.screens();
        Seat s = seats[i];
        if (screens == null || s == null || !screens.available(p)) {
            return;
        }
        Screens.Frame f = new Screens.Frame("blackjack-" + number);
        Hud.Builder t = Hud.title().background(Hud.BG_BLACKJACK)
                .text(0, 8, Hud.Align.LEFT, "BLACKJACK " + number, GOLD)
                .text(0, 168, Hud.Align.RIGHT, Txt.n(plugin.wallet().balance(p)) + " OC", LIGHT);

        // croupier : la 2e carte reste face cachée tant que les joueurs jouent
        String dl = "CROUPIER";
        if (!dealer.isEmpty()) {
            if (state == State.PLAYING) {
                dl = "CROUPIER  " + cardValue(dealer.get(0)) + " + ?";
            } else {
                int d = value(dealer);
                dl = "CROUPIER  " + (d > 21 ? d + " · SAUTÉ" : d == 21 && dealer.size() == 2 ? "BLACKJACK" : String.valueOf(d));
            }
        }
        t.text(1, 88, Hud.Align.CENTER, dl, GREY);
        for (int k = 0; k < dealer.size() && k < 7; k++) {
            if (k == 1 && state == State.PLAYING) {
                f.back(11 + k);
            } else {
                f.card(11 + k, dealer.get(k));
            }
        }

        // ses mains
        boolean myTurn = state == State.PLAYING && turnSeat == i;
        if (s.hands.size() == 1) {
            Hand h = s.hands.get(0);
            for (int k = 0; k < h.cards.size() && k < 7; k++) {
                f.card(29 + k, h.cards.get(k));
            }
        } else if (s.hands.size() == 2) {
            for (int hi = 0; hi < 2; hi++) {
                Hand h = s.hands.get(hi);
                for (int k = 0; k < h.cards.size() && k < 4; k++) {
                    f.card(27 + hi * 5 + k, h.cards.get(k));
                }
            }
        } else if (s.bet > 0) {
            f.item(31, Screens.model(chipModel(s.bet), "<#E0B864>Ta mise : " + Txt.n(s.bet) + " OC"), "mise|" + s.bet, null);
        }

        // statut
        String status;
        int color = LIGHT;
        switch (state) {
            case IDLE, BETTING -> {
                if (s.bet > 0) {
                    status = "MISE POSÉE · " + (state == State.BETTING ? "DÉPART DANS " + countdown + " S" : "ON ATTEND");
                    color = GREY;
                } else {
                    status = "FAITES VOS JEUX";
                    color = GOLD;
                }
            }
            case PLAYING -> {
                if (s.hands.isEmpty()) {
                    status = "MAIN EN COURS · ATTENDS LA SUIVANTE";
                    color = GREY;
                } else if (myTurn) {
                    status = s.hands.size() == 2 ? "À TOI · MAIN " + (turnHand + 1) : "À TOI DE JOUER";
                    color = GOLD;
                } else {
                    status = "AU TOUR DE " + (turnSeat >= 0 && seats[turnSeat] != null ? seats[turnSeat].name : "...");
                    color = GREY;
                }
            }
            case DEALER -> {
                status = "LE CROUPIER JOUE";
                color = ICE;
            }
            default -> {
                if (s.lastVerdict == null) {
                    status = "FIN DE LA MAIN";
                    color = GREY;
                } else {
                    switch (s.lastVerdict) {
                        case "bj" -> {
                            status = "BLACKJACK !  +" + Txt.n(s.lastNet) + " OC";
                            color = GOLD;
                        }
                        case "win" -> {
                            status = "GAGNÉ  +" + Txt.n(s.lastNet) + " OC";
                            color = GREEN;
                        }
                        case "push" -> {
                            status = "ÉGALITÉ · MISE RENDUE";
                            color = GREY;
                        }
                        default -> {
                            status = "PERDU  " + Txt.n(s.lastNet) + " OC";
                            color = RED;
                        }
                    }
                }
            }
        }
        t.text(2, 88, Hud.Align.CENTER, Hud.fit(status, 168), color);

        // sa main en chiffres
        String mine;
        if (s.hands.isEmpty()) {
            mine = s.bet > 0 ? "MISE " + Txt.n(s.bet) + " OC" : "CHOISIS TA MISE";
        } else if (s.hands.size() == 1) {
            Hand h = s.hands.get(0);
            int v = value(h.cards);
            mine = "TA MAIN " + v + (v > 21 ? " SAUTÉ" : "") + " · MISE " + Txt.n(h.bet) + " OC" + (h.doubled ? " ×2" : "");
        } else {
            long total = s.hands.get(0).bet + s.hands.get(1).bet;
            mine = "MAINS " + value(s.hands.get(0).cards) + " ET " + value(s.hands.get(1).cards) + " · MISE " + Txt.n(total) + " OC";
        }
        t.text(3, 8, Hud.Align.LEFT, Hud.fit(mine, 160), LIGHT);
        f.title(t.build());

        // boutons
        if ((state == State.IDLE || state == State.BETTING) && s.bet == 0) {
            int slot = 45;
            for (long b : betChoices) {
                if (slot > 51) {
                    break;
                }
                long amount = b;
                f.button(slot++, chipModel(amount), false, () -> action(p, "miser", String.valueOf(amount)),
                        "<#E0B864><b>Miser " + Txt.n(amount) + " OC", "<#9AA4B2>Solde : " + Txt.n(plugin.wallet().balance(p)) + " OC");
            }
        } else if (state == State.PLAYING && !s.hands.isEmpty()) {
            Hand h = myTurn ? s.hands.get(turnHand) : null;
            boolean dbl = h != null && h.cards.size() == 2;
            boolean spl = h != null && canSplit(s, h);
            f.button(45, myTurn ? "btn_tirer" : "btn_tirer_off", true, () -> action(p, "tirer", ""),
                    "<#7BD88F><b>Tirer", "<#9AA4B2>Une carte de plus");
            f.button(47, myTurn ? "btn_rester" : "btn_rester_off", true, () -> action(p, "rester", ""),
                    "<#E0484A><b>Rester", "<#9AA4B2>Garder cette main");
            f.button(49, dbl ? "btn_doubler" : "btn_doubler_off", true, () -> action(p, "doubler", ""),
                    "<#E0B864><b>Doubler", dbl ? "<#9AA4B2>Mise ×2 (" + Txt.n(h.bet) + " OC de plus), une seule carte" : "<#5A6270>Seulement sur les 2 premières cartes");
            f.button(51, spl ? "btn_separer" : "btn_separer_off", true, () -> action(p, "separer", ""),
                    "<#62CFF4><b>Séparer", spl ? "<#9AA4B2>Deux mains de " + Txt.n(h.bet) + " OC" : "<#5A6270>Seulement avec une paire");
        }
        f.button(53, "btn_quitter", false, () -> action(p, "quitter", ""), "<#E0484A><b>Quitter la table",
                "<#9AA4B2>Ta mise est rendue si la main n'a pas commencé");

        if (state == State.BETTING) {
            f.timer(8, countdown);
        } else if (myTurn) {
            f.timer(8, turnLeft);
        }
        screens.show(p, f, open);
    }

    /** Boutons d'action dans le chat selon le moment. */
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
        Component line = Txt.mm(Txt.PREFIX);
        if (state == State.PLAYING && turnSeat == i) {
            Hand h = s.hands.get(turnHand);
            line = line.append(Txt.mm(Cards.row(h.cards) + " <#9AA4B2>= " + show(h.cards) + "  </#9AA4B2>"))
                    .append(Txt.button("Tirer", "#7BD88F", "/table tirer", "Une carte de plus"))
                    .append(Component.space())
                    .append(Txt.button("Rester", "#E0484A", "/table rester", "Garder cette main"));
            if (h.cards.size() == 2) {
                line = line.append(Component.space()).append(Txt.button("Doubler", "#E0B864", "/table doubler",
                        "Double la mise (" + Txt.n(h.bet) + " OC) et prends une seule carte"));
            }
            if (canSplit(s, h)) {
                line = line.append(Component.space()).append(Txt.button("Séparer", "#62CFF4", "/table separer",
                        "Deux mains de " + Txt.n(h.bet) + " OC"));
            }
        } else if ((state == State.IDLE || state == State.BETTING) && s.bet == 0) {
            line = line.append(Txt.mm("Ta mise : "));
            for (long b : betChoices) {
                line = line.append(Txt.button(Txt.n(b), "#E0B864", "/table miser " + b, "Miser " + Txt.n(b) + " OC"))
                        .append(Component.space());
            }
            line = line.append(Txt.button("Quitter", "#5A6270", "/table quitter", "Quitter la table"));
        } else {
            return;
        }
        p.sendMessage(line);
    }

    private void say(String msg) {
        for (Seat s : seats) {
            if (s != null) {
                Player p = Bukkit.getPlayer(s.uuid);
                if (p != null) {
                    Txt.send(p, msg);
                }
            }
        }
    }

    private void sound(Sound sound) {
        world.playSound(new Location(world, Room.BJ_X + 0.5, Room.Y + 1, cz + 1), sound, 0.8f, 1.2f);
    }
}
