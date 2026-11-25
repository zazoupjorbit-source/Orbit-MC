package fr.orbit.bornes.pulsar;

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
import org.bukkit.SoundCategory;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;

import java.util.ArrayList;
import java.util.List;

/**
 * PULSAR : jeu de rythme. Les notes descendent dans 4 couloirs sur un mur du monde des bornes ; il faut frapper la
 * touche du couloir quand la note passe la ligne : Q (A) · S · Z (W) · D. La musique est jouée aux blocs musicaux
 * (morceaux du domaine public ou originaux, voir {@link Songs}). Normal : notes principales, défilement calme ;
 * Expert : toutes les notes, défilement rapide.
 * <p>Chaque note est un BlockDisplay posé en haut puis envoyé d'un seul coup jusque sous la ligne avec une durée de
 * téléportation égale au trajet : le client la fait glisser seul, sans paquet à chaque tick.</p>
 */
public final class PulsarGame extends SeatedGame {

    static final String[] KEYS = {"Q", "S", "Z", "D"};
    static final String[] KEYS_ALT = {"A", "", "W", ""};
    static final Material[] LANE = {Material.MAGENTA_CONCRETE, Material.LIGHT_BLUE_CONCRETE, Material.LIME_CONCRETE, Material.ORANGE_CONCRETE};
    static final Material[] LANE_GLASS = {Material.MAGENTA_STAINED_GLASS, Material.LIGHT_BLUE_STAINED_GLASS, Material.LIME_STAINED_GLASS,
            Material.ORANGE_STAINED_GLASS};
    static final String[] LANE_COLOR = {"#FF4FD8", "#62CFF4", "#7CFF8A", "#FFB000"};
    static final int LINE = 2, TOP = 19, PERFECT = 1, GOOD = 3, OK = 5, OVERSHOOT = 6;

    private static final class Chip {
        final int lane, hit; // tick de la frappe idéale (depuis le départ du morceau)
        final int pitch;
        boolean judged, spawned;
        BlockDisplay e;

        Chip(int lane, int hit, int pitch) {
            this.lane = lane;
            this.hit = hit;
            this.pitch = pitch;
        }
    }

    private final Songs.Song song;
    private final boolean expert;
    private final int travel;
    private final double speed;
    private final List<Chip> chips = new ArrayList<>();
    private final List<int[]> music = new ArrayList<>(); // {tick, kind (0 mélodie, 1 basse, 2 grosse caisse, 3 caisse claire, 4 charleston), pitch}
    private int countdown = 60, t = -1, musicIdx, nextChip, end;
    private boolean started, over;
    private Input last;
    private int score, combo, maxCombo, perfect, good, ok, miss;
    private final int[] flash = new int[4];
    private TextDisplay judge, stats;
    private int judgeTimer;

    public PulsarGame(OrbitBornes plugin, Player player, int slot, Songs.Song song, boolean expert) {
        super(plugin, player, GameType.PULSAR, slot, Sessions.Z_PULSAR);
        this.song = song;
        this.expert = expert;
        this.travel = expert ? 30 : 42;
        this.speed = (TOP - LINE) / (double) (travel - 1);
        prepare();
    }

    /** Partition → notes à frapper (couloir selon le contour de la mélodie) et événements sonores. */
    private void prepare() {
        int lead = travel + 20;
        int lane = song.melody().isEmpty() ? 0 : song.melody().get(0).pitch() % 4, prevPitch = -1, lastKept = -100;
        for (Songs.Note n : song.melody()) {
            int tick = lead + n.at() * song.tick8();
            music.add(new int[]{tick, 0, n.pitch()});
            if (prevPitch >= 0) {
                if (n.pitch() > prevPitch) {
                    lane = lane == 3 ? 2 : lane + 1;
                } else if (n.pitch() < prevPitch) {
                    lane = lane == 0 ? 1 : lane - 1;
                }
            }
            prevPitch = n.pitch();
            int gap = expert ? 0 : 8;
            if (tick - lastKept >= gap) {
                chips.add(new Chip(lane, tick, n.pitch()));
                lastKept = tick;
            }
        }
        for (Songs.Note n : song.bass()) {
            music.add(new int[]{lead + n.at() * song.tick8(), 1, n.pitch()});
        }
        if (song.drums() > 0) {
            for (int e = 0; e < song.length(); e++) {
                int tick = lead + e * song.tick8();
                if (song.drums() == 2) {
                    if (e % 8 == 0 || e % 8 == 4) {
                        music.add(new int[]{tick, 2, 0});
                    }
                    if (e % 8 == 2 || e % 8 == 6) {
                        music.add(new int[]{tick, 3, 0});
                    }
                    music.add(new int[]{tick, 4, 0});
                } else if (e % 2 == 0) {
                    music.add(new int[]{tick, 4, 0});
                }
            }
        }
        music.sort((a, b) -> Integer.compare(a[0], b[0]));
        end = lead + song.length() * song.tick8() + 40;
    }

    private double laneX(int lane) {
        return x0 + lane * 3 + 1.0;
    }

    // ------------------------------------------------------------------ décor

    @Override
    protected Location seatLocation() {
        return new Location(world, x0 + 5.5, Y + 8.2, z0 + 17.5, 180f, 0f);
    }

    @Override
    protected void build() {
        for (int x = -3; x <= 14; x++) {
            for (int y = -1; y <= 21; y++) {
                boolean frameCell = x == -3 || x == 14 || y == -1 || y == 21;
                Material m;
                if (frameCell) {
                    m = Math.floorMod(x + y, 3) == 0 ? Material.PEARLESCENT_FROGLIGHT : Material.PURPLE_CONCRETE;
                } else if (x >= 0 && x <= 10 && x % 3 == 2) {
                    m = Material.GRAY_STAINED_GLASS; // séparation des couloirs
                } else if (y == LINE && x >= 0 && x <= 10) {
                    m = LANE_GLASS[x / 3];
                } else {
                    m = Material.BLACK_CONCRETE;
                }
                set(x0 + x, Y + y, z0 - 1, m);
            }
        }
        for (int i = 0; i < 4; i++) {
            String alt = KEYS_ALT[i].isEmpty() ? "" : " <#5A6270>(" + KEYS_ALT[i] + ")";
            text(new Location(world, laneX(i), Y + 0.15, z0 + 0.2), "<" + LANE_COLOR[i] + "><b>" + KEYS[i] + "</b>" + alt, 1.6f);
        }
        judge = text(new Location(world, x0 + 5.5, Y + 4.2, z0 + 0.4), "", 2f);
        stats = text(new Location(world, x0 + 5.5, Y + 22.3, z0 + 0.2), "", 1.4f);
        text(new Location(world, x0 + 5.5, Y + 24.0, z0 + 0.2), GameType.PULSAR.title() + " <#9AA4B2>· " + song.title()
                + (expert ? " <#FF4FD8>(expert)" : ""), 2.2f);
    }

    @Override
    protected void begin() {
        Txt.send(player, GameType.PULSAR.title() + " <#9AA4B2>· " + song.title() + " <#5A6270>(" + song.credit() + ")</#5A6270> · frappe "
                + "<#FF4FD8>Q</#FF4FD8> <#62CFF4>S</#62CFF4> <#7CFF8A>Z</#7CFF8A> <#FFB000>D</#FFB000> (A S W D en QWERTY) quand la note touche la ligne. "
                + "<#9AA4B2>/bornes quitter pour abandonner.");
    }

    // ------------------------------------------------------------------ commandes

    @Override
    protected void input(Input in) {
        Input prev = last;
        last = in;
        if (!started || over) {
            return;
        }
        if (in.isLeft() && (prev == null || !prev.isLeft())) {
            press(0);
        }
        if (in.isBackward() && (prev == null || !prev.isBackward())) {
            press(1);
        }
        if (in.isForward() && (prev == null || !prev.isForward())) {
            press(2);
        }
        if (in.isRight() && (prev == null || !prev.isRight())) {
            press(3);
        }
    }

    private void press(int lane) {
        flash[lane] = 3;
        set(x0 + lane * 3, Y + LINE, z0 - 1, Material.SEA_LANTERN);
        set(x0 + lane * 3 + 1, Y + LINE, z0 - 1, Material.SEA_LANTERN);
        Chip best = null;
        int bestDt = 99;
        for (Chip c : chips) {
            if (c.judged || c.lane != lane) {
                continue;
            }
            int dt = Math.abs(t - c.hit);
            if (dt < bestDt) {
                bestDt = dt;
                best = c;
            }
            if (c.hit > t + OK) {
                break;
            }
        }
        if (best == null || bestDt > OK) {
            return; // frappe dans le vide : pas de pénalité
        }
        best.judged = true;
        if (best.e != null) {
            forget(best.e);
            best.e = null;
        }
        combo++;
        maxCombo = Math.max(maxCombo, combo);
        int base;
        String label;
        if (bestDt <= PERFECT) {
            perfect++;
            base = 300;
            label = "<gradient:#FFE08A:#E0B864><b>PARFAIT</b></gradient>";
        } else if (bestDt <= GOOD) {
            good++;
            base = 150;
            label = "<#62CFF4><b>BIEN</b>";
        } else {
            ok++;
            base = 50;
            label = "<#9AA4B2><b>OK</b>";
        }
        score += base * (10 + Math.min(30, combo)) / 10;
        showJudge(label);
        world.spawnParticle(Particle.END_ROD, laneX(lane), Y + LINE + 0.5, z0 + 0.4, bestDt <= PERFECT ? 10 : 4, 0.4, 0.2, 0.05, 0.03);
    }

    private void showJudge(String label) {
        if (judge != null) {
            judge.text(Txt.mm(label + (combo >= 5 ? "\n<#E6EDF3>combo ×" + combo : "")));
            judgeTimer = 14;
        }
    }

    // ------------------------------------------------------------------ tick

    @Override
    protected void update() {
        if (over) {
            return;
        }
        if (!started) {
            if (countdown % 20 == 0 && countdown > 0) {
                Txt.title(player, "<gradient:#C79BFF:#FF4FD8><b>" + countdown / 20 + "</b></gradient>",
                        countdown == 60 ? "<#9AA4B2>" + song.title() + " · Q S Z D" : "", 900);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1.0f);
            }
            if (--countdown <= 0) {
                started = true;
                t = 0;
            }
            return;
        }
        t++;
        // notes qui apparaissent en haut, puis glissent seules jusque sous la ligne
        while (nextChip < chips.size() && chips.get(nextChip).hit - travel <= t) {
            Chip c = chips.get(nextChip++);
            double y0 = Y + LINE + 0.5 + (travel - 1) * speed;
            c.e = blockDisplay(laneX(c.lane), y0, z0 + 0.15, LANE[c.lane].createBlockData(), 1.8f, 0.5f, 0.2f);
            c.e.setGlowing(true);
            c.spawned = true;
        }
        for (Chip c : chips) {
            if (c.spawned && c.e != null && c.hit - travel + 1 == t) {
                c.e.setTeleportDuration(Math.min(59, travel - 1 + OVERSHOOT));
                move(c.e, laneX(c.lane), Y + LINE + 0.5 - OVERSHOOT * speed, z0 + 0.15);
            }
            if (!c.judged && t > c.hit + OK) {
                c.judged = true;
                miss++;
                combo = 0;
                showJudge("<#E0484A><b>RATÉ</b>");
            }
            if (c.e != null && t >= c.hit + OVERSHOOT) {
                forget(c.e);
                c.e = null;
            }
        }
        // musique
        while (musicIdx < music.size() && music.get(musicIdx)[0] <= t) {
            play(music.get(musicIdx++));
        }
        for (int i = 0; i < 4; i++) {
            if (flash[i] > 0 && --flash[i] == 0) {
                set(x0 + i * 3, Y + LINE, z0 - 1, LANE_GLASS[i]);
                set(x0 + i * 3 + 1, Y + LINE, z0 - 1, LANE_GLASS[i]);
            }
        }
        if (judgeTimer > 0 && --judgeTimer == 0 && judge != null) {
            judge.text(Txt.mm(""));
        }
        if (t % 10 == 0) {
            hud();
        }
        if (t >= end) {
            over = true;
            plugin.sessions().finish(this, "<gradient:#C79BFF:#FF4FD8><b>" + grade() + "</b></gradient> <#E6EDF3>· " + Math.round(accuracy() * 100) + " %");
        }
    }

    private void play(int[] ev) {
        Location at = player.getLocation();
        float pitch = (float) Math.pow(2, (ev[2] - 12) / 12.0);
        switch (ev[1]) {
            case 0 -> player.playSound(at, song.id().equals("pulsar") ? Sound.BLOCK_NOTE_BLOCK_BIT : Sound.BLOCK_NOTE_BLOCK_BELL,
                    SoundCategory.RECORDS, 0.9f, pitch);
            case 1 -> player.playSound(at, Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.RECORDS, 0.9f, pitch);
            case 2 -> player.playSound(at, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, SoundCategory.RECORDS, 0.8f, 0.8f);
            case 3 -> player.playSound(at, Sound.BLOCK_NOTE_BLOCK_SNARE, SoundCategory.RECORDS, 0.6f, 1.0f);
            default -> player.playSound(at, Sound.BLOCK_NOTE_BLOCK_HAT, SoundCategory.RECORDS, 0.35f, 1.4f);
        }
    }

    private double accuracy() {
        int total = perfect + good + ok + miss;
        return total == 0 ? 0 : (perfect + good * 0.6 + ok * 0.25) / total;
    }

    private String grade() {
        double a = accuracy();
        return a >= 0.95 ? "S" : a >= 0.85 ? "A" : a >= 0.7 ? "B" : a >= 0.5 ? "C" : "D";
    }

    private void hud() {
        Txt.bar(player, "<#E6EDF3>" + Txt.n(score) + " pts</#E6EDF3>  <#5A6270>·</#5A6270>  <#FF4FD8>combo " + combo
                + "</#FF4FD8>  <#5A6270>·</#5A6270>  <#62CFF4>" + Math.round(accuracy() * 100) + " %");
        if (stats != null) {
            stats.text(Txt.mm("<#9AA4B2>SCORE <#E6EDF3><b>" + Txt.n(score) + "</b>   <#9AA4B2>COMBO <#FF4FD8>" + combo));
        }
    }

    // ------------------------------------------------------------------ fin

    @Override
    protected void end() {
        over = true;
    }

    @Override
    protected double score() {
        return score;
    }

    @Override
    protected int ocWanted() {
        return score / Math.max(1, plugin.getConfig().getInt("pulsar.oc-diviseur", 500));
    }

    @Override
    protected String summary() {
        return song.title() + (expert ? " (expert)" : "") + " · <b>" + Txt.n(score) + "</b> pts · note " + grade() + " · "
                + Math.round(accuracy() * 100) + " % · " + perfect + " parfaits · combo max " + maxCombo;
    }

    @Override
    protected boolean played() {
        return started && t > 0;
    }
}
