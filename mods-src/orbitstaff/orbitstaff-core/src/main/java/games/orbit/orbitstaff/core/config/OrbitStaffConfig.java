package games.orbit.orbitstaff.core.config;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Vue typee de config.yml. Resout la cle de service depuis une variable
 * d'environnement (jamais ecrite dans le fichier).
 */
public final class OrbitStaffConfig {

    private final String serverId;
    private final String language;
    private final String apiBaseUrl;
    private final String serviceKey;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    private final boolean allowLocalRead;
    private final boolean rejectCriticalActions;
    private final int freezeMaxMinutes;
    private final boolean inventoryReadOnly;

    private OrbitStaffConfig(String serverId, String language, String apiBaseUrl, String serviceKey,
                             int connectTimeoutMs, int readTimeoutMs, boolean allowLocalRead,
                             boolean rejectCriticalActions, int freezeMaxMinutes, boolean inventoryReadOnly) {
        this.serverId = serverId;
        this.language = language;
        this.apiBaseUrl = apiBaseUrl;
        this.serviceKey = serviceKey;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        this.allowLocalRead = allowLocalRead;
        this.rejectCriticalActions = rejectCriticalActions;
        this.freezeMaxMinutes = freezeMaxMinutes;
        this.inventoryReadOnly = inventoryReadOnly;
    }

    /** Construit la configuration a partir du fichier et de l'environnement. */
    public static OrbitStaffConfig from(FileConfiguration cfg) {
        String serverId = resolveEnvIfNeeded(cfg.getString("server.id", "srv-main"));
        String apiBaseUrl = resolveEnvIfNeeded(cfg.getString("api.base-url", "http://127.0.0.1:8085"));
        String keyEnv = cfg.getString("api.service-key-env", "ORBITSTAFF_CORE_SERVICE_KEY");
        String serviceKey = System.getenv(keyEnv);

        return new OrbitStaffConfig(
                serverId,
                cfg.getString("server.language", "fr"),
                apiBaseUrl,
                serviceKey == null ? "" : serviceKey,
                cfg.getInt("api.connect-timeout-ms", 3000),
                cfg.getInt("api.read-timeout-ms", 5000),
                cfg.getBoolean("api.degraded-mode.allow-local-read", true),
                cfg.getBoolean("api.degraded-mode.reject-critical-actions", true),
                cfg.getInt("staff-mode.freeze-max-minutes", 15),
                cfg.getBoolean("inspection.inventory-read-only", true));
    }

    /** Remplace une valeur du type ${VAR} par la variable d'environnement correspondante. */
    private static String resolveEnvIfNeeded(String value) {
        if (value != null && value.startsWith("${") && value.endsWith("}")) {
            String env = System.getenv(value.substring(2, value.length() - 1));
            return env == null ? "" : env;
        }
        return value;
    }

    public boolean hasServiceKey() {
        return serviceKey != null && !serviceKey.isBlank();
    }

    public String serverId() {
        return serverId;
    }

    public String language() {
        return language;
    }

    public String apiBaseUrl() {
        return apiBaseUrl;
    }

    public String serviceKey() {
        return serviceKey;
    }

    public int connectTimeoutMs() {
        return connectTimeoutMs;
    }

    public int readTimeoutMs() {
        return readTimeoutMs;
    }

    public boolean allowLocalRead() {
        return allowLocalRead;
    }

    public boolean rejectCriticalActions() {
        return rejectCriticalActions;
    }

    public int freezeMaxMinutes() {
        return freezeMaxMinutes;
    }

    public boolean inventoryReadOnly() {
        return inventoryReadOnly;
    }
}
