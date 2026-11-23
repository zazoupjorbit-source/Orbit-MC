package fr.orbit.mmo.quest;

import fr.orbit.mmo.OrbitMMO;
import fr.orbit.mmo.item.Items;
import fr.orbit.mmo.quest.QuestDefs.QuestDef;
import fr.orbit.mmo.quest.QuestDefs.StepDef;
import fr.orbit.mmo.quest.QuestProgress.Active;
import fr.orbit.mmo.ui.Menu;
import fr.orbit.mmo.ui.Menus;
import fr.orbit.mmo.util.Text;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Journal de quêtes : quêtes en cours (résumé, étapes), quête suivie par la traînée de guidage, quêtes terminées.
 * Un clic sur une quête en cours la fait suivre ; le bouton du bas allume ou éteint la traînée.
 */
final class QuestJournal {

    private QuestJournal() {
    }

    static void open(QuestService svc, Player p) {
        QuestProgress pr = svc.progress(p);
        String followed = svc.trackedId(pr);
        boolean trail = svc.trailOn(pr);
        Menu m = new Menu(4, Text.mm("<#5A4A2A>Journal de quêtes")).skin("Journal de quêtes");
        int slot = 10;
        for (Map.Entry<String, Active> e : pr.active.entrySet()) {
            QuestDef q = svc.registry().quest(e.getKey());
            if (q == null || slot > 16) {
                continue;
            }
            List<String> lore = new ArrayList<>();
            for (String s : q.summary()) {
                lore.add("<#A9B1BC><i>" + Text.esc(svc.tr(p, s)));
            }
            if (!q.summary().isEmpty()) {
                lore.add(" ");
            }
            Active a = e.getValue();
            StepDef current = null;
            for (StepDef s : q.steps()) {
                if (s.index() < a.step) {
                    lore.add("<#5EE89A>✔ <#8C96A3><st>" + Text.esc(svc.tr(p, s.text())) + "</st>");
                } else if (s.index() == a.step) {
                    lore.add("<#FFD66E>▸ <white>" + Text.esc(svc.objective(p, s, a)));
                    current = s;
                } else {
                    lore.add("<#5A6270>• ???");
                }
            }
            Location at = current == null ? null : svc.target(current);
            if (at != null) {
                lore.add(at.getWorld().equals(p.getWorld())
                        ? "<#8C96A3>  à " + Math.round(at.distance(p.getLocation())) + " m"
                        : "<#8C96A3>  ailleurs (" + Text.esc(at.getWorld().getName()) + ")");
            }
            lore.add(" ");
            boolean isFollowed = q.id().equals(followed);
            if (isFollowed) {
                lore.add("<#FFD66E>✦ Quête suivie");
                lore.add(trail ? "<#8C96A3>La traînée dorée au sol mène à l'objectif."
                        : "<#8C96A3>Traînée éteinte : bouton du bas pour l'allumer.");
            } else {
                lore.add("<#5EE89A>Clic : suivre cette quête");
            }
            ItemStack icon = Items.icon(isFollowed ? Material.WRITABLE_BOOK : Material.BOOK,
                    (isFollowed ? "<#FFD66E><b>" : "<#D7DCE2><b>") + Text.esc(svc.tr(p, q.name())), lore);
            m.set(slot++, isFollowed ? Items.glow(icon) : icon, t -> {
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
                svc.track(p, q);
                open(svc, p);
            });
        }
        boolean nothing = slot == 10;
        boolean nowhere = nothing && svc.trailTarget(p) == null;
        if (nothing && !svc.anyQuestFor(p)) {
            m.set(13, Items.icon(Material.BOOK, "<#8C96A3>Pas encore de quêtes pour votre camp",
                    "<#A9B1BC>Les quêtes de votre camp ne sont", "<#A9B1BC>pas encore écrites.",
                    " ", "<#8C96A3>La traînée de guidage n'a donc", "<#8C96A3>rien à vous montrer pour l'instant."), null);
        } else if (nothing) {
            m.set(13, Items.icon(Material.BOOK, "<#8C96A3>Aucune quête en cours",
                    "<#A9B1BC>Cherchez les <#FFD66E><b>!</b></#FFD66E> au-dessus", "<#A9B1BC>de la tête des personnages.",
                    " ", nowhere ? "<#8C96A3>Aucune quête ne vous attend pour l'instant."
                            : "<#8C96A3>La traînée mène au personnage qui vous attend."), null);
        }
        m.set(27, Items.icon(Material.ARROW, "<#8C96A3>Menu"), t -> Menus.main(OrbitMMO.getPlugin(OrbitMMO.class), p));
        m.set(29, Items.icon(trail ? Material.GLOWSTONE_DUST : Material.GUNPOWDER,
                trail ? "<#FFD66E><b>Traînée de guidage : allumée</b>" : "<#8C96A3><b>Traînée de guidage : éteinte</b>",
                "<#A9B1BC>Un chemin de flammes dorées, au sol,", "<#A9B1BC>que vous êtes seul à voir : il part de",
                "<#A9B1BC>vos pieds et mène à l'objectif de la", "<#A9B1BC>quête suivie, en contournant les murs.",
                nowhere ? "<#FF8A7A>Rien à montrer : aucune quête en cours." : " ",
                " ", trail ? "<#FF8A7A>Clic : éteindre" : "<#5EE89A>Clic : allumer", "<#5A6270>Aussi : /quetes trainee"), t -> {
            svc.toggleTrail(p);
            open(svc, p);
        });
        m.set(33, langIcon(svc, p), t -> {
            svc.cycleLang(p);
            open(svc, p);
        });
        List<String> done = new ArrayList<>();
        for (String id : pr.done) {
            QuestDef q = svc.registry().quest(id);
            done.add("<#5EE89A>✔ <#D7DCE2>" + Text.esc(q == null ? id : svc.tr(p, q.name())));
        }
        if (done.isEmpty()) {
            done.add("<#8C96A3>Aucune pour l'instant.");
        }
        m.set(31, Items.icon(Material.ENCHANTED_BOOK, "<#B8C0CA>Quêtes terminées <#8C96A3>(" + pr.done.size() + ")", done), null);
        m.open(p);
    }

    /** Bouton « Langue / Language » (journal et menu principal) : bilingue, pour être compris sans parler français. */
    static ItemStack langIcon(QuestService svc, Player p) {
        boolean en = "en".equals(svc.lang(p));
        boolean chosen = svc.langChosen(p);
        return Items.icon(Material.GLOBE_BANNER_PATTERN, "<#7FB2FF><b>Langue / Language</b>",
                "<#8C96A3>Dialogues et quêtes · Dialogues and quests",
                " ",
                (en ? "<#5A6270>○ Français" : "<#5EE89A>● Français") + (!chosen && !en ? " <#5A6270>(langue du jeu)" : ""),
                (en ? "<#5EE89A>● English" : "<#5A6270>○ English") + (!chosen && en ? " <#5A6270>(game language)" : ""),
                " ",
                en ? "<#FFD66E>Click: Français" : "<#FFD66E>Clic : English",
                "<#5A6270>/langue fr · /langue en · /langue auto");
    }
}
