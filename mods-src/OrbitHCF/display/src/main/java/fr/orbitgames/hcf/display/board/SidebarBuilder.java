package fr.orbitgames.hcf.display.board;

import fr.orbitgames.hcf.api.OrbitHCF;
import fr.orbitgames.hcf.api.model.Faction;
import fr.orbitgames.hcf.api.model.HcfClass;
import fr.orbitgames.hcf.api.model.KothInfo;
import fr.orbitgames.hcf.api.service.ClassesService;
import fr.orbitgames.hcf.api.service.DtrService;
import fr.orbitgames.hcf.api.service.EventsService;
import fr.orbitgames.hcf.api.service.FactionService;
import fr.orbitgames.hcf.api.service.TimerService;
import fr.orbitgames.hcf.api.util.DurationFormat;
import fr.orbitgames.hcf.api.util.Text;
import fr.orbitgames.hcf.display.OrbitHCFDisplayPlugin;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Construit les lignes du scoreboard d'un joueur a partir des services
 * OrbitHCF-API disponibles. Les lignes des modules absents sont masquees.
 */
public class SidebarBuilder {

    /** Cles de timers appartenant au module Combat. */
    private static final Set<String> COMBAT_KEYS = Set.of(
            "combat_tag", "ender_pearl", "golden_apple", "enchanted_golden_apple", "pvp_timer");
    /** Cles de timers appartenant au module Classes. */
    private static final Set<String> CLASSES_KEYS = Set.of(
            "archer_tag", "bard_ability", "rogue_ability", "miner_ability");

    private final OrbitHCFDisplayPlugin plugin;

    public SidebarBuilder(OrbitHCFDisplayPlugin plugin) {
        this.plugin = plugin;
    }

    public List<String> build(Player player) {
        List<String> lines = new ArrayList<>();
        for (String line : plugin.getScoreboardConfig().getStringList("lines.header")) {
            lines.add(line);
        }

        buildFactionBlock(player, lines);
        buildClassBlock(player, lines);
        buildTimersBlock(player, lines);
        buildEventsBlock(lines);

        for (String line : plugin.getScoreboardConfig().getStringList("lines.footer")) {
            lines.add(line);
        }

        // Limite du scoreboard vanilla
        List<String> colored = new ArrayList<>();
        for (int i = 0; i < Math.min(15, lines.size()); i++) {
            colored.add(Text.color(lines.get(i)));
        }
        return colored;
    }

    private void buildFactionBlock(Player player, List<String> lines) {
        FactionService factions = OrbitHCF.factions();
        DtrService dtr = OrbitHCF.dtr();
        if (factions == null) {
            return;
        }
        Faction faction = factions.getFactionByPlayer(player.getUniqueId());
        if (faction == null) {
            return;
        }
        lines.add(Text.replace(plugin.getScoreboardConfig().getString("faction.line-name", ""),
                "faction", faction.getName()));

        String color = faction.isRaidable() ? "&c"
                : faction.getDtr() >= faction.getMaxDtr() * 0.5D ? "&a" : "&e";
        String frozen = "";
        if (faction.isDtrFrozen() && dtr != null) {
            frozen = Text.replace(plugin.getScoreboardConfig().getString("faction.frozen-suffix", ""),
                    "temps", DurationFormat.compact(dtr.getFreezeRemaining(faction)));
        }
        lines.add(Text.replace(plugin.getScoreboardConfig().getString("faction.line-dtr", ""),
                "couleur", color,
                "dtr", String.format(Locale.US, "%.1f", faction.getDtr()),
                "max", String.format(Locale.US, "%.1f", faction.getMaxDtr()),
                "gel", frozen));

        if (faction.getFocusTarget() != null) {
            Faction focus = factions.getFaction(faction.getFocusTarget());
            if (focus != null) {
                lines.add(Text.replace(plugin.getScoreboardConfig().getString("faction.line-focus", ""),
                        "faction", focus.getName()));
            }
        }
    }

    private void buildClassBlock(Player player, List<String> lines) {
        ClassesService classes = OrbitHCF.classes();
        if (classes == null || !plugin.getScoreboardConfig().getBoolean("class.enabled", true)) {
            return;
        }
        Optional<HcfClass> active = classes.getActiveClass(player.getUniqueId());
        if (active.isEmpty()) {
            return;
        }
        lines.add(Text.replace(plugin.getScoreboardConfig().getString("class.line", ""),
                "classe", active.get().getDisplayName()));
        if (active.get() == HcfClass.BARD) {
            lines.add(Text.replace(plugin.getScoreboardConfig().getString("class.bard-energy", ""),
                    "energie", (int) classes.getBardEnergy(player.getUniqueId()),
                    "max", (int) classes.getMaxBardEnergy()));
        }
    }

    private void buildTimersBlock(Player player, List<String> lines) {
        TimerService timers = OrbitHCF.timers();
        if (timers == null) {
            return;
        }
        boolean combatPresent = OrbitHCF.combat() != null;
        boolean classesPresent = OrbitHCF.classes() != null;
        // Tri stable pour un affichage constant
        Map<String, Duration> active = new TreeMap<>(timers.getActiveTimers(player.getUniqueId()));
        for (Map.Entry<String, Duration> entry : active.entrySet()) {
            String key = entry.getKey();
            if (COMBAT_KEYS.contains(key) && !combatPresent) {
                continue;
            }
            if (CLASSES_KEYS.contains(key) && !classesPresent) {
                continue;
            }
            String format = plugin.getScoreboardConfig().getString("timers." + key);
            if (format == null || format.isBlank()) {
                continue;
            }
            lines.add(Text.replace(format, "temps", DurationFormat.compact(entry.getValue())));
        }
    }

    private void buildEventsBlock(List<String> lines) {
        EventsService events = OrbitHCF.events();
        if (events == null) {
            return;
        }
        Optional<KothInfo> koth = events.getActiveKoth();
        if (koth.isPresent()) {
            KothInfo info = koth.get();
            String format;
            if (info.isContested()) {
                format = plugin.getScoreboardConfig().getString("events.koth-contested", "");
            } else if (info.isCitadel()) {
                format = plugin.getScoreboardConfig().getString("events.citadel", "");
            } else {
                format = plugin.getScoreboardConfig().getString("events.koth", "");
            }
            lines.add(Text.replace(format, "koth", info.getName(),
                    "temps", DurationFormat.compact(info.getRemainingCapture())));
        }
        if (events.isSotwActive()) {
            lines.add(Text.replace(plugin.getScoreboardConfig().getString("events.sotw", ""),
                    "temps", DurationFormat.compact(events.getSotwRemaining())));
        }
        if (events.isEotwActive()) {
            lines.add(plugin.getScoreboardConfig().getString("events.eotw", ""));
        }
    }
}
