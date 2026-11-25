package fr.orbit.mmo.outfit;

import io.papermc.paper.event.player.PlayerTrackEntityEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Tenues 3D (essai du 24/11/2026) : la tenue vient du plastron porté (clé {@code orbitmmo:outfit}, voir {@link Outfits}).
 * Le corps peint et le casque sont dessinés par le jeu. Les pièces posées sur le joueur (épaulières, cape) sont
 * COUPÉES par défaut depuis le 25/11/2026 ({@link Pieces}) ; quand le réglage les rallume :
 * chaque tick : pièces 3D créées, tournées avec le corps et retirées quand il le faut ; toutes les secondes (et dès
 * qu'un joueur se met à voir quelqu'un) : paquet « passagers » renvoyé à ceux qui voient le porteur.
 * Coût : 5 entités par joueur en tenue, une mise à jour de transformation par pièce et par tick seulement s'il bouge.
 * Branché par une seule ligne dans OrbitMMO (start/stop) ; la sous-commande /mmoadmin tenue s'ajoute à la commande
 * existante sans toucher AdminCommand.
 */
public final class OutfitService implements Listener {

    static final String TAG = "orbit_tenue";
    /** Réglage du config.yml (absent = aucune) ; changé en jeu par /mmoadmin tenue pieces. */
    private static final String PIECES_KEY = "tenues.pieces-3d";
    private static OutfitService instance;

    /**
     * Quelles pièces 3D sont posées sur le joueur (ItemDisplay qui le suivent). Le corps peint en HD et le casque,
     * dessinés par le jeu lui-même, ne dépendent pas de ce réglage.
     * AUCUNE par défaut : choix de Zazou du 25/11/2026 après essai en jeu (« les épaulières 3D ça rend vraiment
     * moche et c'est mal géré, garde la tenue simple, pas besoin de la 3D »).
     */
    enum Pieces {
        AUCUNE("aucune", "aucune"),
        CAPE("cape", "la cape seulement"),
        TOUTES("toutes", "épaulières et cape");

        final String id;
        final String label;

        Pieces(String id, String label) {
            this.id = id;
            this.label = label;
        }

        static Pieces of(String id) {
            for (Pieces p : values()) {
                if (p.id.equalsIgnoreCase(id)) {
                    return p;
                }
            }
            return null;
        }

        boolean keeps(OutfitDef.Part part) {
            return this == TOUTES || this == CAPE && part.kind() == OutfitDef.Kind.CAPE;
        }
    }

    final JavaPlugin plugin;
    private Pieces pieces = Pieces.AUCUNE;
    private final Map<OutfitDef, List<OutfitDef.Part>> kept = new HashMap<>();
    private final Map<UUID, Wearer> wearers = new HashMap<>();
    private final Map<UUID, String> worn = new HashMap<>();
    private final Map<UUID, UUID> displayOwner = new HashMap<>();
    private final Set<UUID> hideSelf = new HashSet<>();
    private boolean packets;
    private BukkitTask task;
    private long tick;
    private long packetsSent;

    private OutfitService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Démarre le service (appelé par OrbitMMO.onEnable, après l'enregistrement des commandes). */
    public static void start(JavaPlugin plugin) {
        if (instance != null) {
            stop();
        }
        instance = new OutfitService(plugin);
        instance.enable();
    }

    public static void stop() {
        if (instance != null) {
            instance.disable();
            instance = null;
        }
    }

    static OutfitService get() {
        return instance;
    }

    private void enable() {
        int purged = 0;
        for (World w : Bukkit.getWorlds()) {
            for (ItemDisplay d : w.getEntitiesByClass(ItemDisplay.class)) {
                if (d.getScoreboardTags().contains(TAG)) {
                    d.remove();
                    purged++;
                }
            }
            for (Interaction d : w.getEntitiesByClass(Interaction.class)) {
                if (d.getScoreboardTags().contains(TAG)) {
                    d.remove();
                    purged++;
                }
            }
        }
        packets = PassengerPackets.init();
        String wanted = plugin.getConfig().getString(PIECES_KEY, Pieces.AUCUNE.id);
        Pieces read = Pieces.of(wanted);
        if (read == null) {
            plugin.getLogger().warning("Tenues 3D : " + PIECES_KEY + " « " + wanted + " » inconnu (aucune, cape, toutes) ; aucune pièce 3D posée.");
            read = Pieces.AUCUNE;
        }
        pieces = read;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        hookCommand();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        plugin.getLogger().info("Tenues 3D : " + OutfitDef.all().size() + " tenue(s) (tissu, cuir, mailles, plaques × "
                + OutfitDef.TIERS + " paliers) ; pièces 3D posées sur le joueur : " + pieces.label
                + (pieces == Pieces.AUCUNE ? "" : ", suivies "
                + (packets ? "par le client (paquets passagers)" : "par téléportation (paquets indisponibles : " + PassengerPackets.error() + ")"))
                + (purged > 0 ? " ; " + purged + " ancienne(s) pièce(s) retirée(s)" : "") + ".");
    }

    Pieces pieces() {
        return pieces;
    }

    /** Les pièces 3D de cette tenue que le réglage laisse poser (liste vide = rien à poser). */
    List<OutfitDef.Part> parts(OutfitDef def) {
        return kept.computeIfAbsent(def, d -> d.parts().stream().filter(pieces::keeps).toList());
    }

    /** Change le réglage tout de suite (les pièces en place sont retirées, puis reposées au tick suivant) et le garde dans config.yml. */
    void setPieces(Pieces next) {
        pieces = next;
        kept.clear();
        for (Wearer w : new ArrayList<>(wearers.values())) {
            drop(w);
        }
        plugin.getConfig().set(PIECES_KEY, next.id);
        plugin.saveConfig();
    }

    private void disable() {
        if (task != null) {
            task.cancel();
        }
        HandlerList.unregisterAll(this);
        for (Wearer w : wearers.values()) {
            w.remove();
        }
        wearers.clear();
        displayOwner.clear();
    }

    /** Ajoute « tenue » à /mmoadmin en enveloppant l'exécuteur déjà posé. */
    private void hookCommand() {
        PluginCommand cmd = plugin.getCommand("mmoadmin");
        if (cmd == null) {
            plugin.getLogger().warning("Tenues 3D : commande mmoadmin absente, /mmoadmin tenue indisponible.");
            return;
        }
        OutfitCommand outfit = new OutfitCommand(this);
        CommandExecutor ex = cmd.getExecutor();
        TabCompleter tc = cmd.getTabCompleter();
        cmd.setExecutor((s, c, l, a) -> {
            if (a.length > 0 && a[0].equalsIgnoreCase("tenue")) {
                return outfit.run(s, Arrays.copyOfRange(a, 1, a.length));
            }
            return ex.onCommand(s, c, l, a);
        });
        cmd.setTabCompleter((s, c, l, a) -> {
            if (a.length >= 2 && a[0].equalsIgnoreCase("tenue")) {
                return outfit.complete(Arrays.copyOfRange(a, 1, a.length));
            }
            List<String> base = tc == null ? null : tc.onTabComplete(s, c, l, a);
            if (a.length == 1 && "tenue".startsWith(a[0].toLowerCase(Locale.ROOT))) {
                List<String> out = new ArrayList<>(base == null ? List.of() : base);
                out.add("tenue");
                return out;
            }
            return base;
        });
    }

    boolean packets() {
        return packets;
    }

    long packetsSent() {
        return packetsSent;
    }

    boolean hidesSelf(Player p) {
        return hideSelf.contains(p.getUniqueId());
    }

    Wearer wearer(Player p) {
        return wearers.get(p.getUniqueId());
    }

    /** Montre ou cache à un joueur ses propres pièces 3D (vue à la 1re personne). Renvoie le nouvel état (vrai = cachées). */
    boolean toggleSelf(Player p) {
        boolean hide = hideSelf.add(p.getUniqueId());
        if (!hide) {
            hideSelf.remove(p.getUniqueId());
        }
        Wearer w = wearers.get(p.getUniqueId());
        if (w != null) {
            for (Entity d : w.mine()) {
                if (hide) {
                    p.hideEntity(plugin, d);
                } else {
                    p.showEntity(plugin, d);
                }
            }
            w.syncAt = Math.min(w.syncAt, tick + 3);
        }
        return hide;
    }

    /** Relit la tenue portée tout de suite (après /mmoadmin tenue). */
    void refresh(Player p) {
        readWorn(p);
    }

    // ------------------------------------------------------------------ Tick

    private void tick() {
        tick++;
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID u = p.getUniqueId();
            if (Math.floorMod(tick + u.hashCode(), 10) == 0 || !worn.containsKey(u)) {
                readWorn(p);
            }
            OutfitDef def = OutfitDef.get(worn.get(u));
            List<OutfitDef.Part> parts = def == null ? List.of() : parts(def);
            boolean show = !parts.isEmpty() && !p.isDead() && p.getGameMode() != GameMode.SPECTATOR
                    && !p.isInvisible() && !p.hasPotionEffect(PotionEffectType.INVISIBILITY);
            Wearer w = wearers.get(u);
            if (!show) {
                if (w != null) {
                    drop(w);
                }
                continue;
            }
            if (w == null || w.def != def || !w.valid()) {
                if (w != null) {
                    drop(w);
                }
                w = new Wearer(this, p, def, parts);
                wearers.put(u, w);
                for (Entity d : w.all()) {
                    displayOwner.put(d.getUniqueId(), u);
                }
                w.syncAt = tick + 2;
            }
            w.tick(tick);
            if (packets && (tick >= w.syncAt || Math.floorMod(tick + u.hashCode(), 20) == 0)) {
                sync(w);
                w.syncAt = Long.MAX_VALUE;
            }
        }
        for (Iterator<Map.Entry<UUID, Wearer>> it = wearers.entrySet().iterator(); it.hasNext(); ) {
            Wearer w = it.next().getValue();
            if (!w.player.isOnline()) {
                forget(w);
                it.remove();
            }
        }
    }

    private void drop(Wearer w) {
        forget(w);
        wearers.remove(w.player.getUniqueId());
    }

    private void forget(Wearer w) {
        for (Entity d : w.all()) {
            displayOwner.remove(d.getUniqueId());
        }
        w.remove();
    }

    /** Lit la tenue du plastron et remet l'apparence des pièces marquées qui l'auraient perdue. */
    private void readWorn(Player p) {
        PlayerInventory inv = p.getInventory();
        String id = null;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack it = inv.getItem(slot);
            if (Outfits.redress(it)) {
                inv.setItem(slot, it);
            }
            if (slot == EquipmentSlot.CHEST) {
                OutfitDef def = Outfits.of(it);
                id = def == null ? null : def.id();
            }
        }
        if (id == null) {
            worn.put(p.getUniqueId(), "");
        } else {
            worn.put(p.getUniqueId(), id);
        }
    }

    /**
     * Renvoie le paquet « passagers » du porteur à tous ceux qui le voient (lui compris). Le même paquet sert à tous :
     * un client ignore les entités qu'il ne connaît pas, donc les autres n'en retiennent que leurs pièces et le
     * porteur que sa rehausse, sur laquelle un second paquet, pour lui seul, assoit ses propres pièces.
     */
    private void sync(Wearer w) {
        Player p = w.player;
        int[] mine = w.ids();
        List<Entity> real = p.getPassengers();
        int[] ids = new int[real.size() + mine.length + (w.seat == null ? 0 : 1)];
        for (int i = 0; i < real.size(); i++) {
            ids[i] = real.get(i).getEntityId();
        }
        System.arraycopy(mine, 0, ids, real.size(), mine.length);
        if (w.seat != null) {
            ids[ids.length - 1] = w.seat.getEntityId();
            if (!hidesSelf(p) && PassengerPackets.send(p, w.seat.getEntityId(), w.ownIds())) {
                packetsSent++;
            }
        }
        Set<Player> viewers = new HashSet<>(p.getTrackedBy());
        viewers.add(p);
        for (Player v : viewers) {
            if (v == p && hidesSelf(p)) {
                continue;
            }
            if (v != p && !v.canSee(p)) {
                for (ItemDisplay d : w.displays) {
                    v.hideEntity(plugin, d);
                }
                continue;
            }
            if (v != p) {
                for (ItemDisplay d : w.displays) {
                    if (!v.canSee(d)) {
                        v.showEntity(plugin, d); // il revoit le porteur (fin d'un /vanish)
                    }
                }
            }
            if (PassengerPackets.send(v, p.getEntityId(), ids)) {
                packetsSent++;
            }
        }
        if (!PassengerPackets.ready() && packets) {
            packets = false;
            plugin.getLogger().warning("Tenues 3D : paquets passagers coupés (" + PassengerPackets.error() + ") ; suivi par téléportation.");
        }
    }

    private void resyncSoon(Player p, int delay) {
        Wearer w = wearers.get(p.getUniqueId());
        if (w != null) {
            w.syncAt = Math.min(w.syncAt, tick + delay);
        }
    }

    // ------------------------------------------------------------------ Événements

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTrack(PlayerTrackEntityEvent e) {
        Entity ent = e.getEntity();
        UUID owner = ent instanceof Player ? ent.getUniqueId() : displayOwner.get(ent.getUniqueId());
        if (owner == null) {
            return;
        }
        Wearer w = wearers.get(owner);
        if (w != null) {
            // Les paquets d'apparition partent après l'événement : on attend 2 ticks que les deux entités existent.
            w.syncAt = Math.min(w.syncAt, tick + 2);
        }
    }

    /** Geste du bras : le client tourne alors le torse vers la tête pendant 5 ticks (attackAnim > 0). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwing(PlayerAnimationEvent e) {
        Wearer w = wearers.get(e.getPlayer().getUniqueId());
        if (w != null) {
            w.swing = 5;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        resyncSoon(e.getPlayer(), 3);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        resyncSoon(e.getPlayer(), 3);
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent e) {
        resyncSoon(e.getPlayer(), 3);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID u = e.getPlayer().getUniqueId();
        Wearer w = wearers.remove(u);
        if (w != null) {
            forget(w);
        }
        worn.remove(u);
    }
}
