package fr.orbitgames.hcf.api;

import fr.orbitgames.hcf.api.service.CombatService;
import fr.orbitgames.hcf.api.util.DurationFormat;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockEvent;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.event.inventory.InventoryEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldEvent;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * Limite le HCF a ses mondes quand le serveur accueille aussi d'autres modes de jeu (Practice).
 * <p>
 * Les modules enregistrent leurs ecouteurs avec {@link #register} au lieu de
 * {@code PluginManager.registerEvents} : un evenement n'arrive alors a l'ecouteur que s'il se passe
 * dans un monde HCF. Entrer dans un monde HCF compte comme une connexion (les ecouteurs de
 * {@link PlayerJoinEvent} sont appeles) et en sortir comme une deconnexion ({@link PlayerQuitEvent}) :
 * profils, timers, scoreboard et classes suivent sans que chaque module ait a le savoir.
 * <p>
 * Liste de mondes vide = HCF partout, comme a l'origine.
 */
public final class HcfWorlds {

    private record Handler(Plugin plugin, Listener listener, Method method, EventPriority priority) {
        void call(Event event) {
            try {
                method.invoke(listener, event);
            } catch (ReflectiveOperationException e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                plugin.getLogger().log(java.util.logging.Level.SEVERE,
                        "Erreur dans " + listener.getClass().getSimpleName() + "." + method.getName(), cause);
            }
        }
    }

    private static volatile Set<String> worlds = Set.of();
    private static volatile BiConsumer<Player, String> ejector;
    private static final List<Handler> JOIN = new CopyOnWriteArrayList<>();
    private static final List<Handler> QUIT = new CopyOnWriteArrayList<>();

    private HcfWorlds() {
    }

    static void configure(Collection<String> names) {
        worlds = Set.copyOf(names);
    }

    /** Vrai si les mondes HCF sont limites a une liste (serveur partage avec d'autres modes). */
    public static boolean isScoped() {
        return !worlds.isEmpty();
    }

    public static Set<String> names() {
        return worlds;
    }

    public static boolean isHcf(World world) {
        return world != null && (worlds.isEmpty() || worlds.contains(world.getName()));
    }

    public static boolean isHcf(Player player) {
        return isHcf(player.getWorld());
    }

    /** Joueurs connectes qui sont actuellement dans un monde HCF. */
    public static List<Player> players() {
        List<Player> list = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isHcf(player)) {
                list.add(player);
            }
        }
        return list;
    }

    // ------------------------------------------------------------------
    // Sortie forcee du mode (deathban)
    // ------------------------------------------------------------------

    /** Definit ce qui arrive a un joueur ejecte du HCF (par defaut : expulsion du serveur). */
    public static void setEjector(BiConsumer<Player, String> handler) {
        ejector = handler;
    }

    /** Sort un joueur du mode HCF avec un message (deathban, /logout). Passe outre le blocage en combat. */
    public static void eject(Player player, String message) {
        BiConsumer<Player, String> handler = ejector;
        if (handler == null) {
            player.kickPlayer(message);
            return;
        }
        EXITING.add(player.getUniqueId());
        try {
            handler.accept(player, message);
        } finally {
            EXITING.remove(player.getUniqueId());
        }
    }

    private static final Set<java.util.UUID> EXITING = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** Vrai pendant une sortie autorisee par {@link #eject} (le blocage en combat ne s'applique pas). */
    public static boolean isExiting(java.util.UUID playerId) {
        return EXITING.contains(playerId);
    }

    /**
     * Raison qui empeche ce joueur de quitter le HCF maintenant (combat tag), ou {@code null}.
     * Le mode qui fait sortir le joueur (/hub du Practice) doit l'appeler AVANT de toucher a l'inventaire.
     */
    public static String exitDenyReason(Player player) {
        if (!isHcf(player) || isExiting(player.getUniqueId()) || player.hasPermission("orbithcf.admin")) {
            return null;
        }
        CombatService combat = OrbitHCF.combat();
        if (combat == null || !combat.isCombatTagged(player.getUniqueId())) {
            return null;
        }
        return "Tu es en combat encore " + DurationFormat.compact(combat.getCombatTagRemaining(player.getUniqueId()))
                + " : impossible de quitter le HCF. /logout en zone sure, ou attends la fin du combat.";
    }

    /** Raison qui interdit a ce joueur d'entrer en HCF maintenant (deathban), ou {@code null}. */
    public static String denyReason(java.util.UUID playerId) {
        CombatService combat = OrbitHCF.combat();
        if (combat == null || !combat.isDeathbanEnabled()) {
            return null;
        }
        Duration remaining = combat.getDeathbanRemaining(playerId);
        return remaining.isZero() ? null
                : "Tu es deathban du HCF encore " + DurationFormat.compact(remaining) + " (/revive par un allie, ou attends).";
    }

    // ------------------------------------------------------------------
    // Enregistrement des ecouteurs
    // ------------------------------------------------------------------

    /** Enregistre un ecouteur dont les evenements sont limites aux mondes HCF. */
    public static void register(Plugin plugin, Listener listener) {
        for (Method method : listener.getClass().getDeclaredMethods()) {
            EventHandler annotation = method.getAnnotation(EventHandler.class);
            if (annotation == null || method.getParameterCount() != 1
                    || !Event.class.isAssignableFrom(method.getParameterTypes()[0])) {
                continue;
            }
            Class<? extends Event> type = method.getParameterTypes()[0].asSubclass(Event.class);
            method.setAccessible(true);
            Handler handler = new Handler(plugin, listener, method, annotation.priority());
            if (type == AsyncPlayerPreLoginEvent.class && isScoped()) {
                // Serveur partage : le deathban ferme le mode HCF (denyReason), pas le serveur entier.
                continue;
            }
            if (type == PlayerJoinEvent.class) {
                JOIN.add(handler);
                JOIN.sort(Comparator.comparing(Handler::priority));
            } else if (type == PlayerQuitEvent.class) {
                QUIT.add(handler);
                QUIT.sort(Comparator.comparing(Handler::priority));
            }
            EventExecutor executor = (l, event) -> {
                if (type.isInstance(event) && concerns(event)) {
                    handler.call(event);
                }
            };
            Bukkit.getPluginManager().registerEvent(type, listener, annotation.priority(), executor, plugin,
                    annotation.ignoreCancelled());
        }
    }

    private static boolean concerns(Event event) {
        if (worlds.isEmpty()) {
            return true;
        }
        if (event instanceof PlayerEvent e) {
            return isHcf(e.getPlayer());
        }
        if (event instanceof EntityEvent e) {
            return isHcf(e.getEntity().getWorld());
        }
        if (event instanceof BlockEvent e) {
            return isHcf(e.getBlock().getWorld());
        }
        if (event instanceof InventoryEvent e) {
            return isHcf(e.getView().getPlayer().getWorld());
        }
        if (event instanceof WorldEvent e) {
            return isHcf(e.getWorld());
        }
        return true;
    }

    /** Le joueur vient d'arriver dans un monde HCF depuis un autre mode : equivaut a une connexion. */
    static void fireEnter(Player player) {
        PlayerJoinEvent event = new PlayerJoinEvent(player, (net.kyori.adventure.text.Component) null);
        for (Handler handler : JOIN) {
            if (handler.plugin().isEnabled()) {
                handler.call(event);
            }
        }
    }

    /** Le joueur vient de quitter les mondes HCF pour un autre mode : equivaut a une deconnexion. */
    static void fireLeave(Player player) {
        PlayerQuitEvent event = new PlayerQuitEvent(player, (net.kyori.adventure.text.Component) null,
                PlayerQuitEvent.QuitReason.DISCONNECTED);
        for (Handler handler : QUIT) {
            if (handler.plugin().isEnabled()) {
                handler.call(event);
            }
        }
    }

    static void reset() {
        JOIN.clear();
        QUIT.clear();
        ejector = null;
    }
}
