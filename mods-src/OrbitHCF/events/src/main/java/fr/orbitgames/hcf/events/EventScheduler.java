package fr.orbitgames.hcf.events;

import fr.orbitgames.hcf.events.zone.CaptureZone;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Calendrier des evenements : lance automatiquement les KOTH, Citadel
 * et Conquest planifies dans events.yml (heure locale du serveur).
 */
public class EventScheduler {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final OrbitHCFEventsPlugin plugin;
    /** Cle "jour|heure|nom" des entrees deja lancees cette minute. */
    private final Set<String> firedThisMinute = new HashSet<>();
    private String currentMinute = "";

    public EventScheduler(OrbitHCFEventsPlugin plugin) {
        this.plugin = plugin;
    }

    /** Appele toutes les 20 secondes. */
    public void tick() {
        LocalDateTime now = LocalDateTime.now();
        String minute = now.getDayOfWeek().name() + "|" + now.format(TIME);
        if (!minute.equals(currentMinute)) {
            currentMinute = minute;
            firedThisMinute.clear();
        }

        List<Map<?, ?>> schedule = plugin.getEventsConfig().getMapList("schedule");
        for (Map<?, ?> entry : schedule) {
            String day = String.valueOf(entry.get("day")).toUpperCase(Locale.ROOT);
            String time = String.valueOf(entry.get("time"));
            String type = String.valueOf(entry.get("type")).toLowerCase(Locale.ROOT);
            String name = String.valueOf(entry.get("name"));
            if (!day.equals(now.getDayOfWeek().name()) || !time.equals(now.format(TIME))) {
                continue;
            }
            String key = day + "|" + time + "|" + type + "|" + name;
            if (!firedThisMinute.add(key)) {
                continue;
            }
            launch(type, name);
        }
    }

    private void launch(String type, String name) {
        switch (type) {
            case "koth", "citadel" -> {
                CaptureZone zone = plugin.getZoneRegistry().get(name);
                if (zone != null && zone.isComplete() && plugin.getKothManager().getActive() == null) {
                    plugin.getKothManager().start(zone);
                } else {
                    plugin.getLogger().warning("Calendrier : impossible de lancer " + type + " '" + name + "'.");
                }
            }
            case "conquest" -> {
                if (!plugin.getConquestManager().isRunning()) {
                    plugin.getConquestManager().start();
                }
            }
            default -> plugin.getLogger().warning("Calendrier : type d'evenement inconnu '" + type + "'.");
        }
    }
}
