package fr.orbit.reborn.hud;

import fr.orbit.reborn.RebornMMO;
import fr.orbit.reborn.party.PartyService.Party;
import fr.orbit.reborn.player.PlayerData;
import fr.orbit.reborn.player.Stats;
import fr.orbit.reborn.skill.Skill;
import fr.orbit.reborn.skill.Skills;
import fr.orbit.reborn.util.Text;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * HUD : barre de vie du joueur (nom, niveau, PV) puis celles des équipiers, en haut de l'écran ;
 * barre d'action avec les compétences équipées, les Col et la zone.
 */
public final class Hud {

    private final RebornMMO plugin;
    private final Map<UUID, BossBar> own = new HashMap<>();
    private final Map<UUID, Map<UUID, BossBar>> partyBars = new HashMap<>();
    private final Map<UUID, Long> holdUntil = new HashMap<>();

    public Hud(RebornMMO plugin) {
        this.plugin = plugin;
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::barsTick, 10L, 4L);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::actionTick, 20L, 10L);
    }

    private void barsTick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            PlayerData d = plugin.players().loaded(id);
            if (d == null || d.classId == null) {
                hideAll(p);
                continue;
            }
            BossBar self = own.computeIfAbsent(id, k -> {
                BossBar b = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS);
                p.showBossBar(b);
                return b;
            });
            boolean drawn = plugin.pack().isLoaded(id);
            update(self, p.getName(), d, true, drawn);

            Map<UUID, BossBar> bars = partyBars.computeIfAbsent(id, k -> new HashMap<>());
            Set<UUID> keep = new HashSet<>();
            Party party = plugin.parties().party(id);
            if (party != null) {
                for (UUID m : party.members()) {
                    if (m.equals(id)) {
                        continue;
                    }
                    Player mp = Bukkit.getPlayer(m);
                    PlayerData md = mp == null ? null : plugin.players().loaded(m);
                    if (md == null || md.classId == null) {
                        continue;
                    }
                    keep.add(m);
                    BossBar mb = bars.computeIfAbsent(m, k -> {
                        BossBar b = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.GREEN, BossBar.Overlay.NOTCHED_10);
                        p.showBossBar(b);
                        return b;
                    });
                    update(mb, mp.getName(), md, false, drawn);
                }
            }
            for (Iterator<Map.Entry<UUID, BossBar>> it = bars.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<UUID, BossBar> e = it.next();
                if (!keep.contains(e.getKey())) {
                    p.hideBossBar(e.getValue());
                    it.remove();
                }
            }
        }
    }

    private void update(BossBar bar, String name, PlayerData d, boolean self, boolean drawn) {
        double max = Stats.maxHp(d);
        double hp = Math.max(0, d.hp);
        double f = max <= 0 ? 0 : Math.max(0, Math.min(1, hp / max));
        if (drawn) {
            // Pack chargé : barre dessinée par la police du pack ; la barre de boss blanche est invisible.
            bar.name(HpGlyphs.bar(name, d.level, hp, max, self, plugin.getConfig().getInt("hud.offset-x", 0)));
            bar.progress(1f);
            bar.color(BossBar.Color.WHITE);
            bar.overlay(BossBar.Overlay.PROGRESS);
            return;
        }
        bar.overlay(self ? BossBar.Overlay.PROGRESS : BossBar.Overlay.NOTCHED_10);
        String label = self ? "<white><b>" + Text.esc(name) + "</b></white>" : "<#B8C0CA>◆ " + Text.esc(name) + "</#B8C0CA>";
        bar.name(Text.mm(label + "  <#8C96A3>Lv.</#8C96A3><white>" + d.level + "</white>   <" + Text.hpColor(f) + ">"
                + Text.num((long) Math.ceil(hp)) + "</" + Text.hpColor(f) + "> <#5A6270>/</#5A6270> <#D7DCE2>" + Text.num((long) max)));
        bar.progress((float) f);
        bar.color(f > 0.5 ? BossBar.Color.GREEN : f > 0.2 ? BossBar.Color.YELLOW : BossBar.Color.RED);
    }

    private void actionTick() {
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData d = plugin.players().loaded(p.getUniqueId());
            if (d == null || d.classId == null) {
                continue;
            }
            plugin.cursor().update(p, d);
            Long hold = holdUntil.get(p.getUniqueId());
            if (hold != null && hold > now) {
                continue;
            }
            p.sendActionBar(Text.mm(line(p, d, now)));
        }
    }

    private String line(Player p, PlayerData d, long now) {
        StringBuilder sb = new StringBuilder();
        if (now < d.postMotionUntil) {
            sb.append("<#FF5C5C>Délai post-mouvement</#FF5C5C> <#3A3F47>│</#3A3F47> ");
        }
        List<String> eq = d.equipped;
        if (eq.isEmpty()) {
            sb.append("<#8C96A3>Aucune compétence — /mmo</#8C96A3>");
        } else {
            int sel = Math.floorMod(d.selected, eq.size());
            for (int i = 0; i < eq.size(); i++) {
                Skill s = Skills.get(eq.get(i));
                if (s == null) {
                    continue;
                }
                if (i > 0) {
                    sb.append(" <#3A3F47>·</#3A3F47> ");
                }
                long ready = d.cooldowns.getOrDefault(s.id, 0L);
                if (i == sel) {
                    sb.append("<").append(s.hex).append("><b>").append(s.name).append("</b></").append(s.hex).append(">");
                    sb.append(ready > now ? " <#8C96A3>" + (long) Math.ceil((ready - now) / 1000.0) + "s</#8C96A3>" : " <#5EE89A>✔</#5EE89A>");
                } else {
                    sb.append(ready > now ? "<#3A3F47>" : "<#8C96A3>").append(s.name).append(ready > now ? "</#3A3F47>" : "</#8C96A3>");
                }
            }
        }
        sb.append(" <#3A3F47>│</#3A3F47> <#FFD66E>◆ ").append(Text.num(d.col)).append(" Col</#FFD66E>");
        if (plugin.floors().isSafe(p.getLocation())) {
            sb.append(" <#3A3F47>│</#3A3F47> <#5EE89A>Zone sûre</#5EE89A>");
        } else if (plugin.floors().inArena(p.getLocation())) {
            sb.append(" <#3A3F47>│</#3A3F47> <#FF3B3B>Salle du boss</#FF3B3B>");
        }
        return sb.toString();
    }

    /** Message temporaire dans la barre d'action (prioritaire sur la ligne normale). */
    public void flash(Player p, String mini, int ticks) {
        holdUntil.put(p.getUniqueId(), System.currentTimeMillis() + ticks * 50L);
        p.sendActionBar(Text.mm(mini));
    }

    public void refresh(Player p) {
        PlayerData d = plugin.players().loaded(p.getUniqueId());
        if (d == null || d.classId == null) {
            return;
        }
        holdUntil.remove(p.getUniqueId());
        p.sendActionBar(Text.mm(line(p, d, System.currentTimeMillis())));
    }

    public void quit(Player p) {
        hideAll(p);
        holdUntil.remove(p.getUniqueId());
    }

    private void hideAll(Player p) {
        BossBar b = own.remove(p.getUniqueId());
        if (b != null) {
            p.hideBossBar(b);
        }
        Map<UUID, BossBar> bars = partyBars.remove(p.getUniqueId());
        if (bars != null) {
            for (BossBar mb : bars.values()) {
                p.hideBossBar(mb);
            }
        }
    }

    public void shutdown() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            hideAll(p);
        }
    }
}
