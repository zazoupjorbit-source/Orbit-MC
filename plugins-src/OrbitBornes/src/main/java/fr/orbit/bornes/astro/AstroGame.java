package fr.orbit.bornes.astro;

import fr.orbit.bornes.GameType;
import fr.orbit.bornes.OrbitBornes;
import fr.orbit.bornes.SeatedGame;
import fr.orbit.bornes.Sessions;
import fr.orbit.bornes.Txt;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;

/**
 * ASTROBLOCS : les blocs qui tombent, sur un grand mur de 10 × 20 blocs du monde des bornes. Le joueur est assis face
 * au mur (regard vers le nord) et pilote au clavier : Q/D (A/D) déplacer, Z (W) tourner, Ctrl tourner dans l'autre sens,
 * S descendre plus vite, Espace poser d'un coup, Maj mettre en réserve.
 * En duel, la grille de l'adversaire (joueur ou bot ORBI-7) est affichée à droite et les lignes faites montent chez lui.
 * <pre>
 *   x0-7 .. x0-2 : SUIVANT / RÉSERVE / score      x0 .. x0+9 : grille      x0+14 .. x0+23 : grille adverse (duel)
 * </pre>
 */
public final class AstroGame extends SeatedGame {

    enum State { COUNTDOWN, PLAY, CLEAR, OVER }

    static final int[] GRAVITY = {16, 14, 12, 10, 9, 8, 7, 6, 5, 4, 4, 3, 3, 2, 2, 2, 1};
    static final int LOCK_DELAY = 10, LOCK_RESETS = 15, OPP = 14;

    final Board board;
    private final Duel duel;
    private State state = State.COUNTDOWN;
    private int countdown = 60, fall, lockTimer, lockResets, clearTimer;
    private int[] clearing = new int[0];
    private Input last;
    private int leftHeld, rightHeld;
    private boolean softDrop;
    private boolean won, started;
    private final Material[][] shown = new Material[Board.VISIBLE][Board.W];
    private final Material[][] shownOpp = new Material[Board.VISIBLE][Board.W];
    private final Material[][] shownNext = new Material[2][4], shownHold = new Material[2][4];
    private TextDisplay stats, oppLabel;
    private int lastPendingShown = -1, lastOppPending = -1;

    public AstroGame(OrbitBornes plugin, Player player, int slot, Duel duel) {
        super(plugin, player, GameType.ASTROBLOCS, slot, Sessions.Z_ASTRO);
        this.duel = duel;
        this.board = new Board(System.nanoTime() ^ player.getUniqueId().getLeastSignificantBits());
    }

    // ------------------------------------------------------------------ décor

    @Override
    protected Location seatLocation() {
        double cx = duel == null ? x0 + 1.5 : x0 + 9.0;
        double dz = duel == null ? 19.5 : 23.5;
        return new Location(world, cx, Y + 8.2, z0 + dz, 180f, 0f);
    }

    @Override
    protected void build() {
        int right = duel == null ? x0 + 11 : x0 + OPP + 11;
        for (int x = x0 - 8; x <= right; x++) {
            for (int y = Y - 2; y <= Y + 21; y++) {
                set(x, y, z0 - 1, (x + y) % 7 == 0 && (x < x0 - 1 || x > x0 + 10) ? Material.GRAY_CONCRETE : Material.BLACK_CONCRETE);
            }
        }
        frame(x0);
        if (duel != null) {
            frame(x0 + OPP);
        }
        // cadres des aperçus (SUIVANT en haut, RÉSERVE au milieu)
        box(x0 - 7, Y + 14);
        box(x0 - 7, Y + 8);
        text(new Location(world, x0 - 4.5, Y + 18.2, z0 + 0.2), "<#62CFF4><b>SUIVANT", 1.6f);
        text(new Location(world, x0 - 4.5, Y + 12.2, z0 + 0.2), "<#C79BFF><b>RÉSERVE", 1.6f);
        text(new Location(world, x0 + 5, Y + 20.4, z0 + 1.1), "<#E6EDF3><b>" + player.getName(), 1.6f);
        stats = text(new Location(world, x0 - 4.5, Y + 0.5, z0 + 0.2), "", 1.4f);
        if (duel != null) {
            oppLabel = text(new Location(world, x0 + OPP + 5, Y + 20.4, z0 + 1.1), "<#FF4FD8><b>" + duel.otherName(this), 1.6f);
        }
        text(new Location(world, x0 + (duel == null ? 5 : 12), Y + 23.2, z0 + 0.2), GameType.ASTROBLOCS.title(), 3f);
    }

    private void frame(int bx) {
        for (int y = Y - 1; y <= Y + Board.VISIBLE; y++) {
            set(bx - 1, y, z0, Material.LIGHT_BLUE_CONCRETE);
            set(bx + Board.W, y, z0, Material.LIGHT_BLUE_CONCRETE);
        }
        for (int x = bx - 1; x <= bx + Board.W; x++) {
            set(x, Y - 1, z0, x == bx - 1 || x == bx + Board.W ? Material.SEA_LANTERN : Material.LIGHT_BLUE_CONCRETE);
            set(x, Y + Board.VISIBLE, z0, x == bx - 1 || x == bx + Board.W ? Material.SEA_LANTERN : Material.LIGHT_BLUE_CONCRETE);
        }
    }

    /** Cadre d'un aperçu : 6 × 4 autour d'une zone 4 × 2 en (x + 1, y + 1). */
    private void box(int x, int y) {
        for (int dx = 0; dx <= 5; dx++) {
            set(x + dx, y, z0, Material.PURPLE_CONCRETE);
            set(x + dx, y + 3, z0, Material.PURPLE_CONCRETE);
        }
        set(x, y + 1, z0, Material.PURPLE_CONCRETE);
        set(x, y + 2, z0, Material.PURPLE_CONCRETE);
        set(x + 5, y + 1, z0, Material.PURPLE_CONCRETE);
        set(x + 5, y + 2, z0, Material.PURPLE_CONCRETE);
    }

    @Override
    protected void begin() {
        Txt.send(player, GameType.ASTROBLOCS.title() + (duel != null ? " <#FF4FD8>· duel contre " + duel.otherName(this) : "")
                + " <#9AA4B2>· <#E6EDF3>Q/D</#E6EDF3> déplacer · <#E6EDF3>Z</#E6EDF3> tourner (Ctrl : l'autre sens) · <#E6EDF3>S</#E6EDF3> descendre · "
                + "<#E6EDF3>Espace</#E6EDF3> poser · <#E6EDF3>Maj</#E6EDF3> réserve. <#9AA4B2>/bornes quitter pour abandonner.");
        render();
    }

    // ------------------------------------------------------------------ commandes

    @Override
    protected void input(Input in) {
        Input prev = last;
        last = in;
        softDrop = in.isBackward();
        if (state != State.PLAY) {
            return;
        }
        if (in.isLeft() && (prev == null || !prev.isLeft())) {
            leftHeld = 1;
            shift(-1);
        } else if (!in.isLeft()) {
            leftHeld = 0;
        }
        if (in.isRight() && (prev == null || !prev.isRight())) {
            rightHeld = 1;
            shift(1);
        } else if (!in.isRight()) {
            rightHeld = 0;
        }
        if (in.isForward() && (prev == null || !prev.isForward())) {
            turn(1);
        }
        if (in.isSprint() && (prev == null || !prev.isSprint())) {
            turn(-1);
        }
        if (in.isJump() && (prev == null || !prev.isJump())) {
            int d = board.hardDrop();
            board.score += 2 * d;
            player.playSound(player.getLocation(), Sound.BLOCK_STONE_PLACE, 0.8f, 0.8f);
            lockNow();
        }
        if (in.isSneak() && (prev == null || !prev.isSneak())) {
            if (board.swapHold()) {
                player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.3f);
                fall = 0;
                lockResets = 0;
                lockTimer = 0;
                if (board.over) {
                    lose("Plus de place");
                }
            }
        }
        render();
    }

    private void shift(int dx) {
        if (board.move(dx)) {
            player.playSound(player.getLocation(), Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, 0.4f, 1.6f);
            resetLock();
        }
    }

    private void turn(int dir) {
        if (board.rotate(dir)) {
            player.playSound(player.getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_ON, 0.5f, 1.4f);
            resetLock();
        }
    }

    private void resetLock() {
        if (lockTimer > 0 && lockResets < LOCK_RESETS) {
            lockTimer = 0;
            lockResets++;
        }
    }

    // ------------------------------------------------------------------ tick

    @Override
    protected void update() {
        if (state == State.OVER) {
            return;
        }
        if (state == State.COUNTDOWN) {
            if (countdown % 20 == 0 && countdown > 0) {
                Txt.title(player, "<gradient:#62CFF4:#3A7FFF><b>" + countdown / 20 + "</b></gradient>",
                        countdown == 60 ? "<#9AA4B2>Q/D · Z tourner · S · Espace · Maj réserve" : "", 900);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.9f, 1.0f);
            }
            if (--countdown <= 0) {
                state = State.PLAY;
                board.spawn();
                started = true;
                Txt.title(player, "<#7CFF8A><b>GO !</b>", "", 600);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
                render();
            }
            return;
        }
        // duel : l'autre a perdu ?
        if (duel != null) {
            Board other = duel.other(this);
            if (duel.bot != null && this == duel.a && state != State.COUNTDOWN) {
                duel.bot.tick(board);
            }
            if (other.over && !board.over) {
                won = true;
                state = State.OVER;
                plugin.sessions().finish(this, "<#7CFF8A><b>VICTOIRE !</b>");
                return;
            }
        }
        if (state == State.CLEAR) {
            if (--clearTimer <= 0) {
                int sentNow = board.clear(clearing);
                if (duel != null && sentNow > 0) {
                    duel.other(this).pending += sentNow;
                    Txt.bar(player, "<#FF4FD8>⇪ " + sentNow + " ligne" + (sentNow > 1 ? "s" : "") + " envoyée" + (sentNow > 1 ? "s" : "")
                            + " à " + duel.otherName(this));
                }
                clearing = new int[0];
                state = State.PLAY;
                spawnNext();
            }
            render();
            return;
        }
        // déplacement continu (touche maintenue)
        if (leftHeld > 0 && ++leftHeld > 8 && leftHeld % 2 == 0) {
            shift(-1);
        }
        if (rightHeld > 0 && ++rightHeld > 8 && rightHeld % 2 == 0) {
            shift(1);
        }
        int g = GRAVITY[Math.min(GRAVITY.length - 1, board.level - 1)];
        if (softDrop) {
            if (board.down()) {
                board.score += 1;
                fall = 0;
            }
        } else if (++fall >= g) {
            fall = 0;
            board.down();
        }
        if (board.landed()) {
            if (++lockTimer >= LOCK_DELAY) {
                lockNow();
            }
        } else {
            lockTimer = 0;
        }
        render();
        if (ticks % 10 == 0) {
            hud();
        }
    }

    private void lockNow() {
        if (state != State.PLAY || board.cur == null) {
            return;
        }
        int[] rows = board.lock();
        lockTimer = 0;
        lockResets = 0;
        fall = 0;
        if (board.over) {
            lose("Pile trop haute");
            return;
        }
        if (rows.length > 0) {
            clearing = rows;
            clearTimer = 8;
            state = State.CLEAR;
            player.playSound(player.getLocation(), rows.length >= 4 ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_EXPERIENCE_ORB_PICKUP,
                    0.7f, rows.length >= 4 ? 1.4f : 0.9f + rows.length * 0.2f);
            if (rows.length >= 4) {
                Txt.title(player, "", "<gradient:#E0B864:#FF4FD8><b>ASTRO-QUADRUPLE !</b></gradient>", 900);
                world.spawnParticle(Particle.FIREWORK, x0 + 5, Y + rows[0] + 1, z0 + 1.5, 40, 3, 1, 0.2, 0.05);
            }
        } else {
            board.noClear();
            if (board.over) {
                lose("Submergé par les lignes adverses");
                return;
            }
            spawnNext();
        }
    }

    private void spawnNext() {
        int before = board.level;
        board.spawn();
        if (board.level > before) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f);
        }
        if (board.over) {
            lose("Plus de place");
        }
    }

    private void lose(String why) {
        if (state == State.OVER) {
            return;
        }
        state = State.OVER;
        board.over = true;
        player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.1f);
        plugin.sessions().finish(this, duel == null ? "<#E0484A><b>" + why + "</b>" : "<#E0484A><b>DÉFAITE</b>");
    }

    private void hud() {
        StringBuilder sb = new StringBuilder("<#E6EDF3>").append(Txt.n(board.score)).append(" pts</#E6EDF3>  <#5A6270>·</#5A6270>  <#62CFF4>")
                .append(board.lines).append(" lignes</#62CFF4>  <#5A6270>·</#5A6270>  <#E0B864>niveau ").append(board.level).append("</#E0B864>");
        if (board.pending > 0) {
            sb.append("  <#E0484A>⚠ ").append(board.pending).append(" ligne").append(board.pending > 1 ? "s" : "").append(" en approche");
        }
        Txt.bar(player, sb.toString());
        if (stats != null) {
            stats.text(Txt.mm("<#9AA4B2>SCORE\n<#E6EDF3><b>" + Txt.n(board.score) + "</b>\n<#9AA4B2>LIGNES <#62CFF4>" + board.lines
                    + "\n<#9AA4B2>NIVEAU <#E0B864>" + board.level));
        }
    }

    // ------------------------------------------------------------------ dessin (seuls les blocs qui changent sont posés)

    private void render() {
        draw(x0, board, shown, true, clearing);
        if (duel != null) {
            Board other = duel.other(this);
            draw(x0 + OPP, other, shownOpp, true, null);
            if (other.pending != lastOppPending) {
                lastOppPending = other.pending;
                gauge(x0 + OPP, other.pending);
            }
        }
        if (board.pending != lastPendingShown) {
            lastPendingShown = board.pending;
            gauge(x0, board.pending);
        }
        preview(x0 - 6, Y + 15, board.next(), shownNext);
        preview(x0 - 6, Y + 9, board.hold, shownHold);
    }

    /** Lignes en approche : la colonne gauche du cadre rougit sur autant de lignes. */
    private void gauge(int bx, int n) {
        for (int y = 0; y < Board.VISIBLE; y++) {
            set(bx - 1, Y + y, z0, y < n ? Material.RED_CONCRETE : Material.LIGHT_BLUE_CONCRETE);
        }
    }

    private void draw(int bx, Board b, Material[][] cache, boolean active, int[] flash) {
        Material[][] want = new Material[Board.VISIBLE][Board.W];
        for (int y = 0; y < Board.VISIBLE; y++) {
            for (int x = 0; x < Board.W; x++) {
                want[y][x] = Piece.blockOf(b.cells[y][x]);
            }
        }
        if (active && b.cur != null) {
            int gy = b.ghostY();
            for (int[] c : b.cur.cells[b.rot]) {
                int x = b.px + c[0], y = gy - c[1];
                if (y >= 0 && y < Board.VISIBLE && want[y][x] == Material.AIR) {
                    want[y][x] = b.cur.ghost;
                }
            }
            for (int[] c : b.cur.cells[b.rot]) {
                int x = b.px + c[0], y = b.py - c[1];
                if (y >= 0 && y < Board.VISIBLE) {
                    want[y][x] = b.cur.block;
                }
            }
        }
        if (flash != null) {
            for (int y : flash) {
                if (y < Board.VISIBLE) {
                    for (int x = 0; x < Board.W; x++) {
                        want[y][x] = (clearTimer / 2) % 2 == 0 ? Material.SEA_LANTERN : Material.WHITE_CONCRETE;
                    }
                }
            }
        }
        for (int y = 0; y < Board.VISIBLE; y++) {
            for (int x = 0; x < Board.W; x++) {
                if (cache[y][x] != want[y][x]) {
                    cache[y][x] = want[y][x];
                    set(bx + x, Y + y, z0, want[y][x]);
                }
            }
        }
    }

    /** Aperçu 4 × 2 d'une pièce (bas-gauche en x, y). */
    private void preview(int x, int y, Piece p, Material[][] cache) {
        Material[][] want = new Material[2][4];
        for (Material[] row : want) {
            java.util.Arrays.fill(row, Material.AIR);
        }
        if (p != null) {
            for (int[] c : p.cells[0]) {
                int cy = p == Piece.I ? 0 : 1 - c[1];
                if (cy >= 0 && cy < 2 && c[0] < 4) {
                    want[cy][c[0] + (p == Piece.O ? 1 : 0)] = p.block;
                }
            }
        }
        for (int dy = 0; dy < 2; dy++) {
            for (int dx = 0; dx < 4; dx++) {
                if (cache[dy][dx] != want[dy][dx]) {
                    cache[dy][dx] = want[dy][dx];
                    set(x + dx, y + dy, z0, want[dy][dx]);
                }
            }
        }
    }

    // ------------------------------------------------------------------ fin

    @Override
    protected void end() {
        board.over = true; // l'adversaire d'un duel gagne si l'on part
        state = State.OVER;
    }

    @Override
    protected double score() {
        return board.score;
    }

    @Override
    protected int ocWanted() {
        int oc = board.score / Math.max(1, plugin.getConfig().getInt("astroblocs.oc-diviseur", 100));
        return oc + (won ? plugin.getConfig().getInt("astroblocs.bonus-victoire", 40) : 0);
    }

    @Override
    protected String summary() {
        String s = "<b>" + Txt.n(board.score) + "</b> pts · " + board.lines + " lignes · niveau " + board.level;
        if (duel != null) {
            s += won ? " · <#7CFF8A>victoire contre " + duel.otherName(this) + "</#7CFF8A>" : " · <#E0484A>défaite contre " + duel.otherName(this) + "</#E0484A>";
        }
        return s;
    }

    @Override
    protected boolean played() {
        return started;
    }
}
