package fr.orbit.mmo.quest;

import fr.orbit.mmo.item.Items;
import fr.orbit.mmo.quest.QuestDefs.QuestDef;
import fr.orbit.mmo.quest.QuestDefs.StepDef;
import fr.orbit.mmo.quest.QuestProgress.Active;
import fr.orbit.mmo.ui.Menu;
import fr.orbit.mmo.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Journal de quêtes : quêtes en cours (résumé, étapes) et quêtes terminées. */
final class QuestJournal {

    private QuestJournal() {
    }

    static void open(QuestService svc, Player p) {
        QuestProgress pr = svc.progress(p);
        Menu m = new Menu(4, Text.mm("<#5A4A2A>Journal de quêtes")).skin("Journal de quêtes");
        int slot = 10;
        for (Map.Entry<String, Active> e : pr.active.entrySet()) {
            QuestDef q = svc.registry().quest(e.getKey());
            if (q == null || slot > 16) {
                continue;
            }
            List<String> lore = new ArrayList<>();
            for (String s : q.summary()) {
                lore.add("<#A9B1BC><i>" + Text.esc(s));
            }
            if (!q.summary().isEmpty()) {
                lore.add(" ");
            }
            Active a = e.getValue();
            for (StepDef s : q.steps()) {
                if (s.index() < a.step) {
                    lore.add("<#5EE89A>✔ <#8C96A3><st>" + Text.esc(s.text()) + "</st>");
                } else if (s.index() == a.step) {
                    lore.add("<#FFD66E>▸ <white>" + Text.esc(svc.objective(s, a)));
                } else {
                    lore.add("<#5A6270>• ???");
                }
            }
            m.set(slot++, Items.glow(Items.icon(Material.WRITABLE_BOOK, "<#FFD66E><b>" + Text.esc(q.name()), lore)), null);
        }
        if (slot == 10) {
            m.set(13, Items.icon(Material.BOOK, "<#8C96A3>Aucune quête en cours",
                    "<#A9B1BC>Cherchez les <#FFD66E><b>!</b></#FFD66E> au-dessus", "<#A9B1BC>de la tête des personnages."), null);
        }
        List<String> done = new ArrayList<>();
        for (String id : pr.done) {
            QuestDef q = svc.registry().quest(id);
            done.add("<#5EE89A>✔ <#D7DCE2>" + Text.esc(q == null ? id : q.name()));
        }
        if (done.isEmpty()) {
            done.add("<#8C96A3>Aucune pour l'instant.");
        }
        m.set(31, Items.icon(Material.ENCHANTED_BOOK, "<#B8C0CA>Quêtes terminées <#8C96A3>(" + pr.done.size() + ")", done), null);
        m.open(p);
    }
}
