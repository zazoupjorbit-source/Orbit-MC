package games.orbit.orbitstaff.integrations;

/**
 * Integrations externes connues d'OrbitStaff et le nom du plugin Bukkit associe.
 */
public enum IntegrationType {

    LUCKPERMS("LuckPerms"),
    ESSENTIALSX("Essentials"),
    VAULT("Vault"),
    COREPROTECT("CoreProtect"),
    PLACEHOLDERAPI("PlaceholderAPI"),
    WORLDGUARD("WorldGuard");

    private final String pluginName;

    IntegrationType(String pluginName) {
        this.pluginName = pluginName;
    }

    /** @return le nom du plugin tel que declare a Bukkit. */
    public String pluginName() {
        return pluginName;
    }
}
