package fr.orbitgames.hcf.events.zone;

import fr.orbitgames.hcf.events.OrbitHCFEventsPlugin;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registre des zones d'evenement, persistees dans events.yml.
 */
public class ZoneRegistry {

    private final OrbitHCFEventsPlugin plugin;
    private final Map<String, CaptureZone> zones = new ConcurrentHashMap<>();

    public ZoneRegistry(OrbitHCFEventsPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        zones.clear();
        ConfigurationSection section = plugin.getEventsConfig().getConfigurationSection("zones");
        if (section == null) {
            return;
        }
        for (String name : section.getKeys(false)) {
            CaptureZone.ZoneType type;
            try {
                type = CaptureZone.ZoneType.valueOf(
                        section.getString(name + ".type", "KOTH").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                type = CaptureZone.ZoneType.KOTH;
            }
            CaptureZone zone = new CaptureZone(name, type,
                    section.getInt(name + ".capture-seconds",
                            plugin.getEventsConfig().getInt("koth.default-capture-seconds", 300)));
            zone.setWorldName(section.getString(name + ".world"));
            List<Integer> pos1 = section.getIntegerList(name + ".pos1");
            if (pos1.size() == 3) {
                zone.setPos1Raw(new int[]{pos1.get(0), pos1.get(1), pos1.get(2)});
            }
            List<Integer> pos2 = section.getIntegerList(name + ".pos2");
            if (pos2.size() == 3) {
                zone.setPos2Raw(new int[]{pos2.get(0), pos2.get(1), pos2.get(2)});
            }
            zones.put(name.toLowerCase(Locale.ROOT), zone);
        }
        plugin.getLogger().info(zones.size() + " zones d'evenement chargees.");
    }

    public void save() {
        plugin.getEventsConfig().set("zones", null);
        for (CaptureZone zone : zones.values()) {
            String path = "zones." + zone.getName();
            plugin.getEventsConfig().set(path + ".type", zone.getType().name());
            plugin.getEventsConfig().set(path + ".capture-seconds", zone.getCaptureSeconds());
            plugin.getEventsConfig().set(path + ".world", zone.getWorldName());
            if (zone.getPos1() != null) {
                plugin.getEventsConfig().set(path + ".pos1",
                        List.of(zone.getPos1()[0], zone.getPos1()[1], zone.getPos1()[2]));
            }
            if (zone.getPos2() != null) {
                plugin.getEventsConfig().set(path + ".pos2",
                        List.of(zone.getPos2()[0], zone.getPos2()[1], zone.getPos2()[2]));
            }
        }
        plugin.saveEventsConfig();
    }

    public CaptureZone get(String name) {
        return name != null ? zones.get(name.toLowerCase(Locale.ROOT)) : null;
    }

    public void add(CaptureZone zone) {
        zones.put(zone.getName().toLowerCase(Locale.ROOT), zone);
        save();
    }

    public void remove(String name) {
        zones.remove(name.toLowerCase(Locale.ROOT));
        save();
    }

    public Collection<CaptureZone> all() {
        return zones.values();
    }

    public List<CaptureZone> byType(CaptureZone.ZoneType type) {
        List<CaptureZone> result = new ArrayList<>();
        for (CaptureZone zone : zones.values()) {
            if (zone.getType() == type) {
                result.add(zone);
            }
        }
        return result;
    }
}
