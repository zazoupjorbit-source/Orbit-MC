package fr.devpanel.bridge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exporte l'etat du serveur dans plugins/PanelBridge/stats.json toutes les 2 secondes
 * pour le panel de dev local. Collecte sur le thread principal (API Bukkit non thread-safe),
 * ecriture disque en asynchrone.
 */
public final class PanelBridge extends JavaPlugin {

    private final Gson gson = new GsonBuilder().create();
    private File out;
    private File tmp;

    @Override
    public void onEnable() {
        getDataFolder().mkdirs();
        out = new File(getDataFolder(), "stats.json");
        tmp = new File(getDataFolder(), "stats.json.tmp");
        Bukkit.getScheduler().runTaskTimer(this, this::snapshot, 20L, 40L);
    }

    @Override
    public void onDisable() {
        if (out != null) out.delete();
    }

    private void snapshot() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("time", System.currentTimeMillis());
        root.put("version", Bukkit.getVersion());
        root.put("mcVersion", Bukkit.getMinecraftVersion());
        root.put("tps", Bukkit.getTPS());
        root.put("mspt", Bukkit.getAverageTickTime());
        root.put("maxPlayers", Bukkit.getMaxPlayers());

        List<Map<String, Object>> players = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Map<String, Object> m = new LinkedHashMap<>();
            Location l = p.getLocation();
            m.put("name", p.getName());
            m.put("uuid", p.getUniqueId().toString());
            m.put("gamemode", p.getGameMode().name());
            m.put("health", Math.round(p.getHealth() * 10) / 10.0);
            m.put("food", p.getFoodLevel());
            m.put("level", p.getLevel());
            m.put("ping", p.getPing());
            m.put("op", p.isOp());
            m.put("fly", p.isFlying());
            m.put("world", l.getWorld() == null ? "?" : l.getWorld().getName());
            m.put("x", l.getBlockX());
            m.put("y", l.getBlockY());
            m.put("z", l.getBlockZ());
            players.add(m);
        }
        root.put("players", players);

        List<Map<String, Object>> worlds = new ArrayList<>();
        for (World w : Bukkit.getWorlds()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", w.getName());
            m.put("env", w.getEnvironment().name());
            m.put("entities", w.getEntityCount());
            m.put("tileEntities", w.getTileEntityCount());
            m.put("chunks", w.getChunkCount());
            m.put("players", w.getPlayerCount());
            m.put("time", w.getTime());
            m.put("storm", w.hasStorm());
            worlds.add(m);
        }
        root.put("worlds", worlds);

        List<Map<String, Object>> plugins = new ArrayList<>();
        for (Plugin pl : Bukkit.getPluginManager().getPlugins()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", pl.getName());
            m.put("version", pl.getPluginMeta().getVersion());
            m.put("enabled", pl.isEnabled());
            m.put("authors", pl.getPluginMeta().getAuthors());
            m.put("description", pl.getPluginMeta().getDescription());
            plugins.add(m);
        }
        root.put("plugins", plugins);

        final String json = gson.toJson(root);
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> write(json));
    }

    private synchronized void write(String json) {
        try {
            Files.write(tmp.toPath(), json.getBytes(StandardCharsets.UTF_8));
            Files.move(tmp.toPath(), out.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            getLogger().warning("Ecriture stats impossible : " + e.getMessage());
        }
    }
}
