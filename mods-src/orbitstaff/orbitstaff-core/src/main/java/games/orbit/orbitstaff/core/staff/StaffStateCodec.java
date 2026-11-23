package games.orbit.orbitstaff.core.staff;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Conversion {@link StaffModeState} &lt;-&gt; Map, pour la persistance YAML (Bukkit).
 * Logique pure et testable, isolee de l'API de configuration Bukkit.
 */
public final class StaffStateCodec {

    private StaffStateCodec() {
    }

    public static Map<String, Object> toMap(StaffModeState state) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("active", state.active());
        map.put("vanished", state.vanished());
        map.put("previousGameMode", state.previousGameMode());
        map.put("inventoryData", state.inventoryData());
        map.put("world", state.world());
        map.put("x", state.x());
        map.put("y", state.y());
        map.put("z", state.z());
        map.put("yaw", (double) state.yaw());
        map.put("pitch", (double) state.pitch());
        return map;
    }

    public static StaffModeState fromMap(Map<String, Object> map) {
        return new StaffModeState(
                asBool(map.get("active")),
                asBool(map.get("vanished")),
                asString(map.get("previousGameMode"), "SURVIVAL"),
                asString(map.get("inventoryData"), null),
                asString(map.get("world"), null),
                asDouble(map.get("x")),
                asDouble(map.get("y")),
                asDouble(map.get("z")),
                (float) asDouble(map.get("yaw")),
                (float) asDouble(map.get("pitch")));
    }

    private static boolean asBool(Object o) {
        return o instanceof Boolean b && b;
    }

    private static String asString(Object o, String fallback) {
        return o instanceof String s ? s : fallback;
    }

    private static double asDouble(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0.0;
    }
}
