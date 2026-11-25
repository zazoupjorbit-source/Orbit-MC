package fr.orbit.mmo.quest;

import fr.orbit.mmo.hud.QuestPanel;
import fr.orbit.mmo.hud.QuestPanel.Row;
import fr.orbit.mmo.util.Text;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Suivi des quêtes à droite de l'écran (tableau latéral propre à chaque joueur). Avec le pack : cadre du HUD
 * ({@link QuestPanel}) ; sans : tableau du jeu en texte coloré.
 */
final class QuestTracker {

    private static final int MAX_LINES = QuestPanel.MAX_ROWS;
    private static final String ARROWS = "↑↗→↘↓↙←↖";

    private record Shown(List<Row> rows, boolean skinned, String title) {
    }

    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private final Map<UUID, Shown> last = new HashMap<>();

    /** Liste vide = tableau retiré. */
    void show(Player p, List<Row> rows, boolean skinned, String title) {
        UUID id = p.getUniqueId();
        if (rows.isEmpty()) {
            hide(p);
            return;
        }
        List<Row> lines = rows.size() > MAX_LINES ? new ArrayList<>(rows.subList(0, MAX_LINES)) : rows;
        // Coupé au plafond : jamais de trait de séparation tout en bas.
        while (lines.size() > 1 && lines.get(lines.size() - 1).kind() == QuestPanel.Kind.SEPARATOR) {
            lines = new ArrayList<>(lines.subList(0, lines.size() - 1));
        }
        Shown now = new Shown(List.copyOf(lines), skinned, title);
        if (now.equals(last.get(id)) && p.getScoreboard() == boards.get(id)) {
            return;
        }
        last.put(id, now);
        Scoreboard sb = boards.get(id);
        if (sb == null) {
            sb = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective o = sb.registerNewObjective("quetes", Criteria.DUMMY, Component.text(title));
            o.setDisplaySlot(DisplaySlot.SIDEBAR);
            o.numberFormat(NumberFormat.blank());
            boards.put(id, sb);
        }
        if (p.getScoreboard() != sb) {
            p.setScoreboard(sb);
        }
        Objective o = sb.getObjective("quetes");
        int n = lines.size();
        o.displayName(skinned ? QuestPanel.title(n, title) : Text.mm("<#FFD66E><b>✦ " + Text.esc(title) + " ✦</b>"));
        for (int i = 0; i < MAX_LINES; i++) {
            String entry = entry(i);
            if (i < n) {
                Score s = o.getScore(entry);
                s.setScore(MAX_LINES - i);
                s.customName(skinned ? QuestPanel.row(lines.get(i)) : Text.mm(plain(lines.get(i))));
            } else {
                sb.resetScores(entry);
            }
        }
    }

    /** Ligne du tableau sans le pack (comme avant le cadre). */
    private static String plain(Row r) {
        String t = Text.esc(r.text());
        return switch (r.kind()) {
            case QUEST_MAIN -> "<#FFD66E>✦ <b>" + t + "</b>";
            case QUEST -> "<#FFD66E><b>" + t + "</b>";
            case OBJECTIVE -> "<#D7DCE2>▸ " + t;
            case OBJECTIVE_MORE -> "<#D7DCE2>  " + t;
            case DISTANCE -> "<#8C96A3>  " + ARROWS.charAt(Math.floorMod(r.arrow(), 8)) + " " + t;
            case HERE -> "<#5EE89A>  " + t;
            case ELSEWHERE, MORE -> "<#8C96A3>  " + t;
            case STATUS -> "<#E8C77A>  " + t;
            case ALERT -> "<#FF8A7A>  " + t;
            case SEPARATOR -> " ";
        };
    }

    void hide(Player p) {
        UUID id = p.getUniqueId();
        last.remove(id);
        Scoreboard sb = boards.remove(id);
        if (sb != null && p.getScoreboard() == sb) {
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    void quit(UUID id) {
        boards.remove(id);
        last.remove(id);
    }

    void hideAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            hide(p);
        }
    }

    /** Entrée invisible et unique par ligne (le texte affiché est le nom personnalisé du score). */
    private static String entry(int i) {
        return "§" + Integer.toHexString(i) + "§r";
    }
}
