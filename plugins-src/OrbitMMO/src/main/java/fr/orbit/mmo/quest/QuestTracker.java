package fr.orbit.mmo.quest;

import fr.orbit.mmo.util.Text;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Suivi des quêtes à droite de l'écran (tableau latéral propre à chaque joueur). */
final class QuestTracker {

    private static final int MAX_LINES = 14;

    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private final Map<UUID, List<String>> last = new HashMap<>();

    /** Lignes au format MiniMessage ; liste vide = tableau retiré. */
    void show(Player p, List<String> lines) {
        UUID id = p.getUniqueId();
        if (lines.isEmpty()) {
            hide(p);
            return;
        }
        if (lines.equals(last.get(id)) && p.getScoreboard() == boards.get(id)) {
            return;
        }
        last.put(id, List.copyOf(lines));
        Scoreboard sb = boards.get(id);
        if (sb == null) {
            sb = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective o = sb.registerNewObjective("quetes", Criteria.DUMMY, Text.mm("<#FFD66E><b>✦ Quêtes ✦</b>"));
            o.setDisplaySlot(DisplaySlot.SIDEBAR);
            o.numberFormat(NumberFormat.blank());
            boards.put(id, sb);
        }
        if (p.getScoreboard() != sb) {
            p.setScoreboard(sb);
        }
        Objective o = sb.getObjective("quetes");
        int n = Math.min(MAX_LINES, lines.size());
        for (int i = 0; i < MAX_LINES; i++) {
            String entry = entry(i);
            if (i < n) {
                Score s = o.getScore(entry);
                s.setScore(MAX_LINES - i);
                s.customName(Text.mm(lines.get(i)));
            } else {
                sb.resetScores(entry);
            }
        }
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
