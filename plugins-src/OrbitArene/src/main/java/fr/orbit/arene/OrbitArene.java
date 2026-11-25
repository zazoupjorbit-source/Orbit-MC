package fr.orbit.arene;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * OrbitArene : famille C de la salle d'arcade cachée (contrat MinecraftDev/docs/arcade-jeux.md). Lobby seulement.
 * Quatre jeux joués dans le monde vide {@code arene} : Nébuleuse (Gravité / Comète), Charge Nova, Palet Orbital
 * et Turbo-Sphère. PNJ dans la salle d'arcade d'OrbitCasino, OC versés par son portefeuille.
 */
public final class OrbitArene extends JavaPlugin implements Listener, TabCompleter {

    private Db db;
    private Scores scores;
    private Attente attente;
    private Salle salle;
    private Pack pack;
    private World monde;
    private NamespacedKey cle;
    private final Map<UUID, Partie> enPartie = new HashMap<>();
    private final Set<UUID> tpInternes = new HashSet<>();
    private BukkitTask tick, seconde;

    public Db db() {
        return db;
    }

    public Scores scores() {
        return scores;
    }

    public Attente attente() {
        return attente;
    }

    public Salle salle() {
        return salle;
    }

    public Pack pack() {
        return pack;
    }

    public World monde() {
        return monde;
    }

    public NamespacedKey cle() {
        return cle;
    }

    public int lissage() {
        return getConfig().getInt("siege.lissage", 2);
    }

    public double decalageSiege() {
        return getConfig().getDouble("siege.decalage", 0.35);
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        cle = new NamespacedKey(this, "arene");
        db = new Db(this);
        db.connect(Db.locate(getConfig().getString("base.fichier", "auto")));
        scores = new Scores(this);
        scores.declarer();
        salle = new Salle(this);
        attente = new Attente(this);
        pack = new Pack(this);
        pack.activer();
        getServer().getPluginManager().registerEvents(pack, this);
        getServer().getPluginManager().registerEvents(this, this);
        var cmd = getCommand("arene");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }
        Bukkit.getScheduler().runTask(this, () -> {
            String nom = getConfig().getString("monde", "arene");
            monde = Bukkit.getWorld(nom);
            if (monde == null) {
                monde = new WorldCreator(nom).generator(new Vide()).generateStructures(false).createWorld();
            }
            if (monde == null) {
                getLogger().severe("Monde de l'arène impossible à créer : jeux coupés.");
                return;
            }
            monde.setTime(18000);
            monde.setGameRule(org.bukkit.GameRules.ADVANCE_TIME, false);
            monde.setGameRule(org.bukkit.GameRules.ADVANCE_WEATHER, false);
            monde.setGameRule(org.bukkit.GameRules.SPAWN_MOBS, false);
            monde.setGameRule(org.bukkit.GameRules.FALL_DAMAGE, false);
            monde.setGameRule(org.bukkit.GameRules.MOB_GRIEFING, false);
            for (Entity e : monde.getEntities()) {
                if (e.getPersistentDataContainer().has(cle, PersistentDataType.BYTE)) {
                    e.remove();
                }
            }
            salle.attendre();
            tick = Bukkit.getScheduler().runTaskTimer(this, this::tick, 1L, 1L);
            seconde = Bukkit.getScheduler().runTaskTimer(this, attente::seconde, 20L, 20L);
            getLogger().info("Arène ouverte : Nébuleuse, Charge Nova, Palet Orbital, Turbo-Sphère (monde " + nom + ").");
        });
    }

    @Override
    public void onDisable() {
        if (tick != null) {
            tick.cancel();
        }
        if (seconde != null) {
            seconde.cancel();
        }
        if (attente != null) {
            attente.toutArreter();
        }
        if (salle != null) {
            salle.retirer();
        }
        if (pack != null) {
            pack.arreter();
        }
        if (db != null) {
            db.close();
        }
    }

    private void tick() {
        for (Partie p : attente.parties()) {
            if (p.finie()) {
                continue;
            }
            try {
                p.tick();
            } catch (RuntimeException e) {
                getLogger().severe(p.mode.cle() + " : erreur, partie arrêtée : " + e);
                e.printStackTrace();
                p.arreter();
            }
        }
    }

    // ------------------------------------------------------------------ joueurs en partie

    public Partie partie(Player p) {
        return enPartie.get(p.getUniqueId());
    }

    void entre(Player p, Partie partie) {
        attente.quitterFile(p, false);
        enPartie.put(p.getUniqueId(), partie);
    }

    void sort(Player p) {
        enPartie.remove(p.getUniqueId());
    }

    /** Téléportation voulue par l'arène (pas d'arrêt de la partie). */
    public void teleporter(Player p, Location to) {
        if (p.isInsideVehicle()) {
            p.leaveVehicle();
        }
        tpInternes.add(p.getUniqueId());
        try {
            p.teleport(to);
        } finally {
            tpInternes.remove(p.getUniqueId());
        }
    }

    // ------------------------------------------------------------------ commandes

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(java.util.Locale.ROOT);
        if (sub.equals("admin")) {
            return admin(sender, args);
        }
        if (!(sender instanceof Player p)) {
            Txt.send(sender, "/arene admin <parties|pnj|stop>");
            return true;
        }
        switch (sub) {
            case "jouer" -> {
                Jeu j = args.length > 1 ? Jeu.parse(args[1]) : null;
                if (j == null) {
                    Txt.send(p, "Jeux : nebuleuse, charge, palet, turbo.");
                    return true;
                }
                Jeu.Mode m = args.length > 2 ? j.mode(args[2]) : j.modes().get(0);
                if (m == null) {
                    salle.menu(p, j);
                    return true;
                }
                if (!dansSalle(p)) {
                    Txt.send(p, "<#E0484A>Les jeux de l'arène se lancent depuis la salle d'arcade.");
                    return true;
                }
                attente.rejoindre(p, m);
            }
            case "rejouer" -> {
                Jeu.Mode m = attente.dernier(p);
                if (m == null) {
                    Txt.send(p, "Parle à un PNJ de l'arène dans la salle d'arcade.");
                } else if (!dansSalle(p)) {
                    Txt.send(p, "<#E0484A>Retourne dans la salle d'arcade pour rejouer.");
                } else {
                    attente.rejoindre(p, m);
                }
            }
            case "quitter" -> {
                Partie partie = partie(p);
                if (partie != null) {
                    partie.abandon(p, true);
                } else if (!attente.quitterFile(p, true)) {
                    Txt.send(p, "<#9AA4B2>Tu n'es ni en partie ni en file.");
                }
            }
            case "top" -> {
                Jeu j = args.length > 1 ? Jeu.parse(args[1]) : null;
                if (j == null) {
                    for (Jeu x : Jeu.values()) {
                        p.sendMessage(Txt.mm("  ").append(Txt.button(x.nom, x.c1, "/arene top " + x.id, "Records de " + x.nom)));
                    }
                    return true;
                }
                Jeu.Mode m = args.length > 2 ? j.mode(args[2]) : null;
                scores.top(p, m == null ? j.modes().get(0) : m);
            }
            default -> {
                Txt.send(p, "Les jeux de l'arène sont dans la salle d'arcade, à droite en entrant :");
                for (Jeu j : Jeu.values()) {
                    p.sendMessage(Txt.mm("  ").append(Txt.button(j.nom, j.c1, "/arene jouer " + j.id + " ?", j.description)));
                }
            }
        }
        return true;
    }

    private boolean dansSalle(Player p) {
        return p.getWorld().getName().equals(salle.nomMonde()) || p.hasPermission("orbitarene.admin");
    }

    private boolean admin(CommandSender s, String[] args) {
        if (!s.hasPermission("orbitarene.admin")) {
            Txt.send(s, "<#E0484A>Réservé au staff.");
            return true;
        }
        String a = args.length > 1 ? args[1].toLowerCase(java.util.Locale.ROOT) : "";
        switch (a) {
            case "parties" -> {
                List<Partie> l = attente.parties();
                Txt.send(s, l.size() + " partie(s) en cours, " + enPartie.size() + " joueur(s).");
                for (Partie p : l) {
                    s.sendMessage(Txt.mm("  <#9AA4B2>" + p.mode.cle() + " place " + p.slot + " · " + p.humains() + " humain(s)"));
                }
            }
            case "pnj" -> {
                salle.poser();
                Txt.send(s, "PNJ reposés.");
            }
            case "stop" -> {
                attente.toutArreter();
                Txt.send(s, "Toutes les parties sont arrêtées.");
            }
            default -> Txt.send(s, "/arene admin <parties|pnj|stop>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("jouer", "quitter", "rejouer", "top"));
            if (sender.hasPermission("orbitarene.admin")) {
                out.add("admin");
            }
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("jouer") || args[0].equalsIgnoreCase("top"))) {
            for (Jeu j : Jeu.values()) {
                out.add(j.id);
            }
        } else if (args.length == 3 && (args[0].equalsIgnoreCase("jouer") || args[0].equalsIgnoreCase("top"))) {
            Jeu j = Jeu.parse(args[1]);
            if (j != null) {
                for (Jeu.Mode m : j.modes()) {
                    out.add(m.id());
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            out.addAll(List.of("parties", "pnj", "stop"));
        }
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(java.util.Locale.ROOT);
        out.removeIf(x -> !x.toLowerCase(java.util.Locale.ROOT).startsWith(last));
        return out;
    }

    // ------------------------------------------------------------------ événements

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onNpc(PlayerInteractEntityEvent e) {
        Jeu j = salle.jeuDuPnj(e.getRightClicked());
        if (j != null) {
            e.setCancelled(true);
            if (e.getHand() == EquipmentSlot.HAND && partie(e.getPlayer()) == null) {
                salle.menu(e.getPlayer(), j);
            }
            return;
        }
        if (e.getRightClicked().getPersistentDataContainer().has(cle, PersistentDataType.BYTE)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onInput(PlayerInputEvent e) {
        Partie p = partie(e.getPlayer());
        if (p != null) {
            p.touche(e.getPlayer(), e.getInput());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        Partie p = partie(e.getPlayer());
        if (p == null) {
            return;
        }
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND || (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        if (Partie.estQuitter(e.getItem())) {
            p.abandon(e.getPlayer(), true);
        } else {
            p.clic(e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDismount(EntityDismountEvent e) {
        if (e.getEntity() instanceof Player pl) {
            Partie p = partie(pl);
            if (p != null && p.assis(pl)) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        attente.quitterFile(e.getPlayer(), false);
        Partie p = partie(e.getPlayer());
        if (p != null) {
            p.abandon(e.getPlayer(), true);
        }
    }

    /** Arrivé dans l'arène hors partie (après un arrêt brutal) : retour à la salle. */
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (monde != null && p.getWorld().equals(monde)) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (p.isOnline() && partie(p) == null && p.getWorld().equals(monde)) {
                    teleporter(p, salle.retour(Jeu.NEBULEUSE));
                }
            }, 5L);
        }
    }

    /** Téléporté ailleurs par autre chose (boussole, /spawn…) : la partie s'arrête pour lui. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        Player pl = e.getPlayer();
        if (tpInternes.contains(pl.getUniqueId())) {
            return;
        }
        Partie p = partie(pl);
        if (p != null && e.getTo() != null && !e.getTo().getWorld().equals(monde)) {
            Bukkit.getScheduler().runTask(this, () -> {
                if (partie(pl) == p) {
                    p.abandon(pl, false);
                }
            });
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (monde != null && e.getEntity().getWorld().equals(monde)) {
            e.setCancelled(true);
            if (e.getCause() == EntityDamageEvent.DamageCause.VOID && e.getEntity() instanceof Player p) {
                Partie partie = partie(p);
                if (partie == null) {
                    teleporter(p, salle.retour(Jeu.NEBULEUSE));
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent e) {
        if (monde != null && e.getEntity().getWorld().equals(monde)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (monde != null && e.getBlock().getWorld().equals(monde) && !e.getPlayer().hasPermission("orbitarene.admin")) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (monde != null && e.getBlock().getWorld().equals(monde) && !e.getPlayer().hasPermission("orbitarene.admin")) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (partie(e.getPlayer()) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (partie(e.getPlayer()) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p && partie(p) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFly(PlayerToggleFlightEvent e) {
        if (partie(e.getPlayer()) != null && e.getPlayer().getGameMode() != org.bukkit.GameMode.SPECTATOR) {
            e.setCancelled(true);
            e.getPlayer().setAllowFlight(false);
        }
    }
}
