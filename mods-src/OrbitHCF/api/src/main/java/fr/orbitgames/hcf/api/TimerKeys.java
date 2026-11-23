package fr.orbitgames.hcf.api;

/**
 * Cles de timers standardisees, partagees entre les modules.
 * Les modules ecrivent leurs timers dans le {@link fr.orbitgames.hcf.api.service.TimerService}
 * avec ces cles ; OrbitHCF-Display les lit pour l'affichage.
 */
public final class TimerKeys {

    private TimerKeys() {
    }

    // Core
    public static final String HOME = "home";
    public static final String STUCK = "stuck";
    public static final String LOGOUT = "logout";

    // Combat
    public static final String COMBAT_TAG = "combat_tag";
    public static final String PVP_TIMER = "pvp_timer";
    public static final String ENDER_PEARL = "ender_pearl";
    public static final String GOLDEN_APPLE = "golden_apple";
    public static final String ENCHANTED_GOLDEN_APPLE = "enchanted_golden_apple";

    // Classes
    public static final String ARCHER_TAG = "archer_tag";
    public static final String BARD_ABILITY = "bard_ability";
    public static final String ROGUE_ABILITY = "rogue_ability";
    public static final String MINER_ABILITY = "miner_ability";

    // Globaux (evenements)
    public static final String GLOBAL_SOTW = "sotw";
    public static final String GLOBAL_EOTW = "eotw";
}
