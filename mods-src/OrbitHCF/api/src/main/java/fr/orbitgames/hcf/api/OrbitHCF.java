package fr.orbitgames.hcf.api;

import fr.orbitgames.hcf.api.service.ClaimService;
import fr.orbitgames.hcf.api.service.ClassesService;
import fr.orbitgames.hcf.api.service.CombatService;
import fr.orbitgames.hcf.api.service.DatabaseService;
import fr.orbitgames.hcf.api.service.DtrService;
import fr.orbitgames.hcf.api.service.EventsService;
import fr.orbitgames.hcf.api.service.FactionService;
import fr.orbitgames.hcf.api.service.ProfileService;
import fr.orbitgames.hcf.api.service.TimerService;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Point d'entree statique de l'API OrbitHCF.
 * <p>
 * Chaque module enregistre ses services ici a l'activation et les retire
 * a la desactivation. Les autres modules ne doivent JAMAIS acceder aux
 * classes internes d'un autre module : uniquement a ces interfaces.
 */
public final class OrbitHCF {

    private static final Map<Class<?>, Object> SERVICES = new ConcurrentHashMap<>();

    private OrbitHCF() {
    }

    /** Enregistre l'implementation d'un service API. */
    public static <T> void register(Class<T> type, T implementation) {
        SERVICES.put(type, implementation);
    }

    /** Retire un service du registre (desactivation du module). */
    public static <T> void unregister(Class<T> type) {
        SERVICES.remove(type);
    }

    /** Vide entierement le registre (arret du serveur). */
    public static void clear() {
        SERVICES.clear();
    }

    /** Recupere un service, ou {@code null} si le module correspondant est absent. */
    @SuppressWarnings("unchecked")
    public static <T> T get(Class<T> type) {
        return (T) SERVICES.get(type);
    }

    /** Recupere un service sous forme d'{@link Optional}. */
    public static <T> Optional<T> optional(Class<T> type) {
        return Optional.ofNullable(get(type));
    }

    /** Indique si un service est disponible (module installe et actif). */
    public static boolean isPresent(Class<?> type) {
        return SERVICES.containsKey(type);
    }

    // ------------------------------------------------------------------
    // Raccourcis types (retournent null si le module est absent)
    // ------------------------------------------------------------------

    public static FactionService factions() {
        return get(FactionService.class);
    }

    public static ClaimService claims() {
        return get(ClaimService.class);
    }

    public static DtrService dtr() {
        return get(DtrService.class);
    }

    public static fr.orbitgames.hcf.api.service.EconomyService economy() {
        return get(fr.orbitgames.hcf.api.service.EconomyService.class);
    }

    public static TimerService timers() {
        return get(TimerService.class);
    }

    public static ProfileService profiles() {
        return get(ProfileService.class);
    }

    public static DatabaseService database() {
        return get(DatabaseService.class);
    }

    public static ClassesService classes() {
        return get(ClassesService.class);
    }

    public static CombatService combat() {
        return get(CombatService.class);
    }

    public static EventsService events() {
        return get(EventsService.class);
    }
}
