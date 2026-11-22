package fr.orbit.mmo.quest;

import fr.orbit.mmo.OrbitMMO;
import fr.orbit.mmo.quest.QuestDefs.Line;
import fr.orbit.mmo.quest.QuestDefs.NpcDef;
import fr.orbit.mmo.util.Text;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Dialogues dans le chat, réplique par réplique, au rythme de lecture.
 * Recliquer sur le PNJ (ou /quetes passer) affiche la réplique suivante tout de suite.
 */
final class Dialogue {

    private static final class Session {
        final List<Line> lines;
        final Runnable done;
        int index;
        BukkitTask task;

        Session(List<Line> lines, Runnable done) {
            this.lines = lines;
            this.done = done;
        }
    }

    private final OrbitMMO plugin;
    private final QuestRegistry registry;
    private final Map<UUID, Session> sessions = new HashMap<>();

    Dialogue(OrbitMMO plugin, QuestRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    boolean busy(Player p) {
        return sessions.containsKey(p.getUniqueId());
    }

    /** Joue les répliques puis appelle done (même s'il n'y a aucune réplique). Remplace un dialogue en cours. */
    void play(Player p, List<Line> lines, Runnable done) {
        cancel(p.getUniqueId());
        if (lines == null || lines.isEmpty()) {
            if (done != null) {
                done.run();
            }
            return;
        }
        Session s = new Session(lines, done);
        sessions.put(p.getUniqueId(), s);
        p.sendMessage(Text.mm(" "));
        next(p, s);
    }

    /** Affiche tout de suite la réplique suivante. */
    void skip(Player p) {
        Session s = sessions.get(p.getUniqueId());
        if (s != null) {
            if (s.task != null) {
                s.task.cancel();
            }
            next(p, s);
        }
    }

    void cancel(UUID id) {
        Session s = sessions.remove(id);
        if (s != null && s.task != null) {
            s.task.cancel();
        }
    }

    void cancelAll() {
        for (UUID id : List.copyOf(sessions.keySet())) {
            cancel(id);
        }
    }

    private void next(Player p, Session s) {
        if (!p.isOnline() || sessions.get(p.getUniqueId()) != s) {
            return;
        }
        if (s.index >= s.lines.size()) {
            sessions.remove(p.getUniqueId());
            if (s.done != null) {
                s.done.run();
            }
            return;
        }
        Line line = s.lines.get(s.index++);
        p.sendMessage(Text.mm(format(p, line)));
        sound(p, line);
        long delay = Math.max(30, Math.min(140, 26 + Math.round(line.text().length() * 0.9)));
        s.task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> next(p, s), delay);
    }

    private String format(Player p, Line line) {
        String text = Text.esc(line.text().replace("{joueur}", p.getName()));
        return switch (line.speaker()) {
            case "narrateur" -> "<#A9B1BC><i>" + text + "</i></#A9B1BC>";
            case "joueur" -> "<#7FD4FF>Vous</#7FD4FF> <#5A6270>»</#5A6270> <#D7DCE2>" + text;
            default -> {
                NpcDef n = registry.npc(line.speaker());
                String name = n == null ? line.speaker() : n.name();
                String hex = n == null ? "#FFE9A8" : n.hex();
                yield "<" + hex + "><b>" + Text.esc(name) + "</b></" + hex + "> <#5A6270>»</#5A6270> <#EDE6D6>" + text;
            }
        };
    }

    private static void sound(Player p, Line line) {
        if (line.speaker().equals("narrateur")) {
            p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 0.8f);
        } else if (!line.speaker().equals("joueur")) {
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HARP, 0.35f, 1.3f);
        }
    }
}
