package fr.orbitgames.hcf.display.bar;

import fr.orbitgames.hcf.api.OrbitHCF;
import fr.orbitgames.hcf.api.model.KothInfo;
import fr.orbitgames.hcf.api.service.EventsService;
import fr.orbitgames.hcf.api.util.DurationFormat;
import fr.orbitgames.hcf.api.util.Text;
import fr.orbitgames.hcf.display.OrbitHCFDisplayPlugin;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

/**
 * Bossbars du KOTH et du SOTW (lecture seule via OrbitHCF-API).
 */
public class BossBarManager {

    private final OrbitHCFDisplayPlugin plugin;
    private BossBar kothBar;
    private BossBar sotwBar;

    public BossBarManager(OrbitHCFDisplayPlugin plugin) {
        this.plugin = plugin;
    }

    public void tick() {
        EventsService events = OrbitHCF.events();
        if (events == null) {
            return;
        }
        tickKoth(events);
        tickSotw(events);
    }

    private void tickKoth(EventsService events) {
        if (!plugin.getScoreboardConfig().getBoolean("bossbar.koth.enabled", true)) {
            return;
        }
        Optional<KothInfo> active = events.getActiveKoth();
        if (active.isEmpty()) {
            if (kothBar != null) {
                kothBar.removeAll();
                kothBar = null;
            }
            return;
        }
        KothInfo info = active.get();
        if (kothBar == null) {
            kothBar = Bukkit.createBossBar("", parseColor(
                    plugin.getScoreboardConfig().getString("bossbar.koth.color", "YELLOW")), BarStyle.SOLID);
        }
        kothBar.setTitle(Text.color(Text.replace(
                plugin.getScoreboardConfig().getString("bossbar.koth.title", "&6KOTH {koth} {temps}"),
                "koth", info.getName(),
                "temps", DurationFormat.compact(info.getRemainingCapture()))));
        double total = Math.max(1L, info.getTotalCapture().getSeconds());
        kothBar.setProgress(Math.max(0.0D, Math.min(1.0D, info.getRemainingCapture().getSeconds() / total)));
        for (Player player : Bukkit.getOnlinePlayers()) {
            kothBar.addPlayer(player);
        }
    }

    private void tickSotw(EventsService events) {
        if (!plugin.getScoreboardConfig().getBoolean("bossbar.sotw.enabled", true)) {
            return;
        }
        if (!events.isSotwActive()) {
            if (sotwBar != null) {
                sotwBar.removeAll();
                sotwBar = null;
            }
            return;
        }
        Duration remaining = events.getSotwRemaining();
        if (sotwBar == null) {
            sotwBar = Bukkit.createBossBar("", parseColor(
                    plugin.getScoreboardConfig().getString("bossbar.sotw.color", "GREEN")), BarStyle.SOLID);
        }
        sotwBar.setTitle(Text.color(Text.replace(
                plugin.getScoreboardConfig().getString("bossbar.sotw.title", "&aSOTW {temps}"),
                "temps", DurationFormat.compact(remaining))));
        sotwBar.setProgress(1.0D);
        for (Player player : Bukkit.getOnlinePlayers()) {
            sotwBar.addPlayer(player);
        }
    }

    private BarColor parseColor(String name) {
        try {
            return BarColor.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return BarColor.WHITE;
        }
    }

    public void shutdown() {
        if (kothBar != null) {
            kothBar.removeAll();
        }
        if (sotwBar != null) {
            sotwBar.removeAll();
        }
    }
}
