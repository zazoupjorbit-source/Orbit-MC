package fr.orbit.mmo.quest;

import fr.orbit.mmo.OrbitMMO;
import fr.orbit.mmo.hud.DialogueBox;
import fr.orbit.mmo.hud.FontWidths;
import fr.orbit.mmo.quest.QuestDefs.Line;
import fr.orbit.mmo.quest.QuestDefs.NpcDef;
import fr.orbit.mmo.util.Text;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Dialogues, réplique par réplique, au rythme de lecture, dans la langue du joueur.
 * Avec le pack : boîte de dialogue en haut de l'écran (nom de celui qui parle, texte qui s'écrit, trois lignes par
 * page, portrait de celui qui parle à droite). Sans le pack : dans le chat. Clic gauche, clic sur le PNJ ou /quetes passer : fin de l'écriture, puis suite.
 */
final class Dialogue {

    private static final TextColor SPEECH = TextColor.color(0xEDE6D6);
    private static final TextColor NARRATION = TextColor.color(0xC9CFD8);
    private static final TextColor PLAYER = TextColor.color(0x7FD4FF);
    /** Caractères « écrits » par tick dans la boîte. */
    private static final int TYPE_SPEED = 2;

    private static final class Session {
        final List<Line> lines;
        final Runnable done;
        int index;
        BukkitTask task;

        // Boîte de dialogue (pack chargé).
        boolean box;
        String name;
        TextColor nameColor = SPEECH;
        TextColor textColor = SPEECH;
        boolean italic;
        /** La case du portrait est dessinée pendant tout le dialogue (un de ceux qui parlent a un portrait). */
        boolean slot;
        /** Portrait de celui qui parle (clé de hud/Portraits, null : case vide). */
        String portrait;
        List<String> wrapped = List.of();
        int page;
        int shown;
        int total;
        int wait;
        long pageAt;
        long skippedAt;

        Session(List<Line> lines, Runnable done) {
            this.lines = lines;
            this.done = done;
        }
    }

    private final OrbitMMO plugin;
    private final QuestRegistry registry;
    private final Function<Player, String> lang;
    private final Map<UUID, Session> sessions = new HashMap<>();

    Dialogue(OrbitMMO plugin, QuestRegistry registry, Function<Player, String> lang) {
        this.plugin = plugin;
        this.registry = registry;
        this.lang = lang;
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
        for (Line l : lines) {
            if (registry.portrait(l.speaker()) != null) {
                s.slot = true;
                break;
            }
        }
        sessions.put(p.getUniqueId(), s);
        next(p, s);
    }

    /** Clic du joueur : termine l'écriture de la page, ou passe à la suite. */
    void skip(Player p) {
        Session s = sessions.get(p.getUniqueId());
        if (s == null) {
            return;
        }
        if (!s.box) {
            next(p, s);
            return;
        }
        long now = System.currentTimeMillis();
        // Le clic qui a ouvert le dialogue, ou un double signal du même clic, ne compte pas.
        if (now - s.pageAt < 350 || now - s.skippedAt < 250) {
            return;
        }
        s.skippedAt = now;
        if (s.shown < s.total) {
            s.shown = s.total;
            s.wait = readTime(s.total);
            render(p, s);
        } else {
            advance(p, s);
        }
    }

    void cancel(UUID id) {
        Session s = sessions.remove(id);
        if (s == null) {
            return;
        }
        if (s.task != null) {
            s.task.cancel();
        }
        Player p = Bukkit.getPlayer(id);
        if (p != null) {
            plugin.hud().dialogueEnd(p);
        }
    }

    void cancelAll() {
        for (UUID id : List.copyOf(sessions.keySet())) {
            cancel(id);
        }
    }

    // ------------------------------------------------------------------ Déroulé

    private void next(Player p, Session s) {
        if (s.task != null) {
            s.task.cancel();
            s.task = null;
        }
        if (!p.isOnline() || sessions.get(p.getUniqueId()) != s) {
            return;
        }
        if (s.index >= s.lines.size()) {
            sessions.remove(p.getUniqueId());
            plugin.hud().dialogueEnd(p);
            if (s.done != null) {
                s.done.run();
            }
            return;
        }
        boolean first = s.index == 0;
        Line line = s.lines.get(s.index++);
        String language = lang.apply(p);
        String text = registry.tr(line.text(), language).replace("{joueur}", p.getName());
        sound(p, line);
        if (plugin.hud().dialogueBox(p)) {
            s.box = true;
            speaker(s, line, language);
            s.wrapped = FontWidths.wrap(text, DialogueBox.TEXT_WIDTH);
            s.page = 0;
            startPage(p, s);
            s.task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> tick(p, s), 1L, 1L);
            return;
        }
        // Sans le pack : dans le chat.
        s.box = false;
        plugin.hud().dialogueEnd(p);
        if (first) {
            p.sendMessage(Text.mm(" "));
        }
        p.sendMessage(Text.mm(chat(line, text, language)));
        long delay = Math.max(30, Math.min(140, 26 + Math.round(text.length() * 0.9)));
        s.task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> next(p, s), delay);
    }

    private void tick(Player p, Session s) {
        if (!p.isOnline() || sessions.get(p.getUniqueId()) != s) {
            if (s.task != null) {
                s.task.cancel();
            }
            return;
        }
        if (s.shown < s.total) {
            s.shown = Math.min(s.total, s.shown + TYPE_SPEED);
            if (s.shown >= s.total) {
                s.wait = readTime(s.total);
            }
            render(p, s);
            return;
        }
        if (--s.wait <= 0) {
            advance(p, s);
        }
    }

    /** Page suivante de la réplique, ou réplique suivante. */
    private void advance(Player p, Session s) {
        if ((s.page + 1) * DialogueBox.LINES < s.wrapped.size()) {
            s.page++;
            startPage(p, s);
        } else {
            next(p, s);
        }
    }

    private void startPage(Player p, Session s) {
        s.shown = 0;
        s.total = 0;
        for (String l : page(s)) {
            s.total += l.length();
        }
        s.wait = readTime(s.total);
        s.pageAt = System.currentTimeMillis();
        render(p, s);
    }

    private static List<String> page(Session s) {
        int from = s.page * DialogueBox.LINES;
        return s.wrapped.subList(Math.min(from, s.wrapped.size()), Math.min(s.wrapped.size(), from + DialogueBox.LINES));
    }

    /** Temps laissé pour lire une page, en ticks. */
    private static int readTime(int chars) {
        return Math.max(40, Math.min(170, 30 + chars));
    }

    private void render(Player p, Session s) {
        String hint = "en".equals(lang.apply(p)) ? "LEFT CLICK > NEXT" : "CLIC GAUCHE > SUITE";
        plugin.hud().dialogue(p, DialogueBox.build(s.name, s.nameColor, page(s), s.shown, s.textColor, s.italic, hint,
                s.slot, s.portrait));
    }

    // ------------------------------------------------------------------ Qui parle

    private void speaker(Session s, Line line, String language) {
        s.portrait = registry.portrait(line.speaker());
        switch (line.speaker()) {
            case "narrateur" -> {
                s.name = null;
                s.textColor = NARRATION;
                s.italic = true;
            }
            case "joueur" -> {
                s.name = "en".equals(language) ? "You" : "Vous";
                s.nameColor = PLAYER;
                s.textColor = SPEECH;
                s.italic = false;
            }
            default -> {
                NpcDef n = registry.npc(line.speaker());
                s.name = n == null ? line.speaker() : registry.tr(n.name(), language);
                TextColor c = n == null ? null : TextColor.fromHexString(n.hex());
                s.nameColor = c == null ? TextColor.color(0xFFE9A8) : c;
                s.textColor = SPEECH;
                s.italic = false;
            }
        }
    }

    private String chat(Line line, String raw, String language) {
        String text = Text.esc(raw);
        return switch (line.speaker()) {
            case "narrateur" -> "<#A9B1BC><i>" + text + "</i></#A9B1BC>";
            case "joueur" -> "<#7FD4FF>" + ("en".equals(language) ? "You" : "Vous") + "</#7FD4FF> <#5A6270>»</#5A6270> <#D7DCE2>" + text;
            default -> {
                NpcDef n = registry.npc(line.speaker());
                String name = n == null ? line.speaker() : registry.tr(n.name(), language);
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
