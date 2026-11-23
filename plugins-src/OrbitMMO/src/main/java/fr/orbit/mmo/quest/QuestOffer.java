package fr.orbit.mmo.quest;

import fr.orbit.mmo.OrbitMMO;
import fr.orbit.mmo.hud.DialogueBox;
import fr.orbit.mmo.hud.FontWidths;
import fr.orbit.mmo.quest.QuestDefs.ItemReward;
import fr.orbit.mmo.quest.QuestDefs.QuestDef;
import fr.orbit.mmo.quest.QuestDefs.RewardDef;
import fr.orbit.mmo.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fenêtre d'offre de quête dans le HUD (pack chargé) : à la fin du dialogue du PNJ, la boîte de dialogue devient une
 * fiche de quête (titre, résumé, récompenses, portrait du PNJ). Maj = accepter, clic gauche = plus tard. Elle se ferme
 * seule si l'on s'éloigne, après 40 s, ou si un autre dialogue prend la place. Sans le pack : boutons dans le chat
 * (QuestService.propose).
 */
final class QuestOffer implements Listener {

    private static final TextColor TITLE = TextColor.color(0xFFD66E);
    private static final TextColor SUMMARY = TextColor.color(0xEDE6D6);
    private static final TextColor REWARD = TextColor.color(0x7FD4FF);
    /** Délai avant qu'un clic ou Maj compte (le clic qui a fini le dialogue ne doit pas fermer la fiche). */
    private static final long GUARD_MS = 700;
    private static final long LIFETIME_MS = 40_000;
    private static final double MAX_DISTANCE = 8;

    private record Open(QuestDef quest, Runnable accept, Location at, long since, Component box) {
    }

    private final OrbitMMO plugin;
    private final QuestService svc;
    private final Map<UUID, Open> open = new HashMap<>();

    QuestOffer(OrbitMMO plugin, QuestService svc) {
        this.plugin = plugin;
        this.svc = svc;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::check, 10L, 10L);
    }

    /** Montre la fiche ; false si le joueur n'a pas le pack (l'appelant passe alors par le chat). */
    boolean show(Player p, QuestDef q, Runnable accept) {
        if (!plugin.hud().dialogueBox(p)) {
            return false;
        }
        boolean en = "en".equals(svc.lang(p));
        List<String> lines = new ArrayList<>(summary(p, q));
        while (lines.size() < DialogueBox.LINES - 1) {
            lines.add("");
        }
        lines.add(rewards(q.reward(), en));
        TextColor[] colors = {SUMMARY, SUMMARY, REWARD};
        String portrait = q.giver() == null ? null : svc.registry().portrait(q.giver());
        String title = svc.tr(p, q.name());
        String hint = en ? "SHIFT > ACCEPT   LEFT CLICK > LATER" : "MAJ > ACCEPTER   CLIC GAUCHE > PLUS TARD";
        int shown = 0;
        for (String l : lines) {
            shown += l.length();
        }
        Component box = DialogueBox.build(title, TITLE, lines, shown, colors, false, hint, portrait != null, portrait);
        plugin.hud().dialogue(p, box);
        open.put(p.getUniqueId(), new Open(q, accept, p.getLocation(), System.currentTimeMillis(), box));
        p.playSound(p.getLocation(), Sound.UI_TOAST_IN, 0.9f, 1.1f);
        return true;
    }

    /** Résumé de la quête sur deux lignes au plus (coupé avec « … »). */
    private List<String> summary(Player p, QuestDef q) {
        StringBuilder sb = new StringBuilder();
        for (String s : q.summary()) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(svc.tr(p, s));
        }
        if (sb.isEmpty()) {
            sb.append("en".equals(svc.lang(p)) ? "Recommended level " : "Niveau conseillé ").append(q.level());
        }
        List<String> wrapped = FontWidths.wrap(sb.toString(), DialogueBox.TEXT_WIDTH);
        int max = DialogueBox.LINES - 1;
        if (wrapped.size() <= max) {
            return wrapped;
        }
        List<String> out = new ArrayList<>(wrapped.subList(0, max));
        String last = out.get(max - 1);
        while (!last.isEmpty() && FontWidths.width(last + "…", false) > DialogueBox.TEXT_WIDTH) {
            last = last.substring(0, last.length() - 1);
        }
        out.set(max - 1, last.stripTrailing() + "…");
        return out;
    }

    private static String rewards(RewardDef r, boolean en) {
        List<String> parts = new ArrayList<>();
        if (r != null) {
            if (r.xp() > 0) {
                parts.add(Text.num(r.xp()) + " XP");
            }
            if (r.gold() > 0) {
                parts.add(Text.num(r.gold()) + (en ? " gold" : " or"));
            }
            int items = 0;
            for (ItemReward it : r.items()) {
                items += Math.max(1, it.amount());
            }
            if (items > 0) {
                parts.add(items + (en ? (items > 1 ? " items" : " item") : (items > 1 ? " objets" : " objet")));
            }
        }
        String head = en ? "Rewards: " : "Récompenses : ";
        return parts.isEmpty() ? head + "—" : head + String.join(" · ", parts);
    }

    private void close(Player p, Open o) {
        open.remove(p.getUniqueId());
        if (plugin.hud().dialogueShowing(p, o.box())) {
            plugin.hud().dialogueEnd(p);
        }
    }

    /** Fiche périmée, joueur parti trop loin, ou boîte reprise par un autre dialogue. */
    private void check() {
        if (open.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Open> en : new ArrayList<>(open.entrySet())) {
            Player p = Bukkit.getPlayer(en.getKey());
            Open o = en.getValue();
            if (p == null) {
                open.remove(en.getKey());
                continue;
            }
            if (!plugin.hud().dialogueShowing(p, o.box())) {
                open.remove(en.getKey());
                continue;
            }
            if (now - o.since() > LIFETIME_MS || p.getWorld() != o.at().getWorld()
                    || p.getLocation().distanceSquared(o.at()) > MAX_DISTANCE * MAX_DISTANCE) {
                close(p, o);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSneak(PlayerToggleSneakEvent e) {
        Player p = e.getPlayer();
        Open o = open.get(p.getUniqueId());
        if (!e.isSneaking() || o == null || System.currentTimeMillis() - o.since() < GUARD_MS) {
            return;
        }
        if (!plugin.hud().dialogueShowing(p, o.box())) {
            open.remove(p.getUniqueId());
            return;
        }
        close(p, o);
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.5f, 1.3f);
        o.accept().run();
    }

    @EventHandler
    public void onSwing(PlayerAnimationEvent e) {
        if (e.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }
        Player p = e.getPlayer();
        Open o = open.get(p.getUniqueId());
        if (o == null || System.currentTimeMillis() - o.since() < GUARD_MS) {
            return;
        }
        if (!plugin.hud().dialogueShowing(p, o.box())) {
            open.remove(p.getUniqueId());
            return;
        }
        close(p, o);
        p.playSound(p.getLocation(), Sound.UI_TOAST_OUT, 0.8f, 1f);
        plugin.hud().flash(p, "<#8C96A3>" + ("en".equals(svc.lang(p)) ? "Come back when you are ready." : "Revenez quand vous serez prêt."), 40);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        open.remove(e.getPlayer().getUniqueId());
    }
}
