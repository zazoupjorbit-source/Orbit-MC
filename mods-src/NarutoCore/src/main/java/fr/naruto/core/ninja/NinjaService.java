package fr.naruto.core.ninja;

import fr.naruto.core.db.AsyncExecutor;
import fr.naruto.core.db.Storage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Gere les profils ninja en cache, le chakra (regen + barre) et la persistance. */
public final class NinjaService {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final Plugin plugin;
    private final AsyncExecutor async;
    private final Storage storage;

    private final Map<UUID, Ninja> cache = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> bars = new ConcurrentHashMap<>();

    public NinjaService(Plugin plugin, AsyncExecutor async, Storage storage) {
        this.plugin = plugin;
        this.async = async;
        this.storage = storage;
    }

    public Ninja get(UUID uuid) { return cache.get(uuid); }

    public void load(Player player) {
        UUID id = player.getUniqueId();
        async.runAsync(() -> {
            Ninja loaded = storage.load(id);
            Ninja ninja = loaded != null ? loaded : new Ninja(id, Element.NONE, Clan.NONE, Rank.GENIN, 0);
            async.sync(() -> {
                cache.put(id, ninja);
                showBar(player, ninja);
                if (loaded == null) save(ninja);
            });
        });
    }

    public void unload(Player player) {
        UUID id = player.getUniqueId();
        Ninja n = cache.remove(id);
        BossBar bar = bars.remove(id);
        if (bar != null) bar.removeAll();
        if (n != null) save(n);
    }

    public void save(Ninja ninja) {
        async.runAsync(() -> storage.save(ninja));
    }

    private void showBar(Player player, Ninja ninja) {
        BossBar bar = Bukkit.createBossBar("Chakra", BarColor.BLUE, BarStyle.SEGMENTED_10);
        bar.addPlayer(player);
        bars.put(player.getUniqueId(), bar);
        updateBar(player, ninja);
    }

    public void updateBar(Player player, Ninja ninja) {
        BossBar bar = bars.get(player.getUniqueId());
        if (bar == null) return;
        int max = ninja.maxChakra();
        double ratio = max <= 0 ? 0 : ninja.chakra() / max;
        bar.setProgress(Math.max(0, Math.min(1, ratio)));
        bar.setColor(ninja.element() == Element.KATON ? BarColor.RED
                : ninja.element() == Element.RAITON ? BarColor.YELLOW
                : ninja.element() == Element.DOTON ? BarColor.GREEN
                : BarColor.BLUE);
        bar.setTitle(net.md_5.bungee.api.ChatColor.AQUA + "Chakra "
                + (int) ninja.chakra() + "/" + max
                + net.md_5.bungee.api.ChatColor.GRAY + "  |  " + ninja.rank().displayName()
                + "  " + ninja.element().displayName());
    }

    public void regenTick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Ninja n = cache.get(player.getUniqueId());
            if (n == null) continue;
            double regen = 3 + (n.clan() != null ? n.clan().regenBonus() * 2.0 : 0);
            if (n.chakra() < n.maxChakra()) {
                n.restore(regen);
            }
            updateBar(player, n);
        }
    }

    public Component name(Ninja n) {
        return MM.deserialize("<" + n.element().color() + ">" + n.element().displayName());
    }
}
