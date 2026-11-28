package fr.orbit.bowl;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
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
import org.bukkit.event.entity.EntityDamageByEntityEvent;
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
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * OrbitBowl : la salle ORBIT BOWL (famille F « Loisirs » de l'arcade cachée, contrat MinecraftDev/docs/arcade-jeux.md).
 * Lobby seulement. Monde vide {@code bowl} construit par le code : bowling 6 pistes, fléchettes, tir forain, basket,
 * baby-foot, mini-golf et laser game. Entrée par le portier de la salle d'arcade ; OC versés par OrbitCasino.
 */
public final class OrbitBowl extends JavaPlugin implements Listener, TabCompleter {

    public static NamespacedKey CLE;
    private static NamespacedKey QUITTER;

    private Db db;
    private Scores scores;
    private Pack pack;
    private Postes postes;
    private Entree entree;
    private World monde;
    private final Map<UUID, Activite> enJeu = new HashMap<>();
    private final Map<UUID, String[]> dernier = new HashMap<>();
    private final Set<UUID> tpInternes = new HashSet<>();
    private final Set<UUID> paiements = new HashSet<>();
    private BukkitTask tick, minute;

    public Db db() {
        return db;
    }

    public Scores scores() {
        return scores;
    }

    public Pack pack() {
        return pack;
    }

    public World monde() {
        return monde;
    }

    public Postes postes() {
        return postes;
    }

    // ------------------------------------------------------------------ démarrage

    @Override
    public void onEnable() {
        saveDefaultConfig();
        CLE = new NamespacedKey(this, "bowl");
        QUITTER = new NamespacedKey(this, "quitter");
        db = new Db(this);
        db.connect(Db.locate(getConfig().getString("base.fichier", "auto")));
        scores = new Scores(this);
        scores.declarer();
        pack = new Pack(this);
        pack.activer();
        postes = new Postes(this);
        entree = new Entree(this);
        getServer().getPluginManager().registerEvents(pack, this);
        getServer().getPluginManager().registerEvents(this, this);
        var cmd = getCommand("bowl");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }
        Bukkit.getScheduler().runTask(this, this::ouvrir);
    }

    private void ouvrir() {
        String nom = getConfig().getString("monde", "bowl");
        monde = Bukkit.getWorld(nom);
        if (monde == null) {
            monde = new WorldCreator(nom).generator(new Vide()).generateStructures(false).createWorld();
        }
        if (monde == null) {
            getLogger().severe("Monde d'ORBIT BOWL impossible à créer : salle fermée.");
            return;
        }
        monde.setTime(18000);
        monde.setGameRule(org.bukkit.GameRules.ADVANCE_TIME, false);
        monde.setGameRule(org.bukkit.GameRules.ADVANCE_WEATHER, false);
        monde.setGameRule(org.bukkit.GameRules.SPAWN_MOBS, false);
        monde.setGameRule(org.bukkit.GameRules.FALL_DAMAGE, false);
        monde.setGameRule(org.bukkit.GameRules.MOB_GRIEFING, false);
        monde.setSpawnLocation(Salle.spawn(monde));
        for (int cx = -3; cx <= 2; cx++) {
            for (int cz = -2; cz <= 6; cz++) {
                monde.getChunkAt(cx, cz).addPluginChunkTicket(this);
            }
        }
        for (Entity e : monde.getEntities()) {
            if (e.getPersistentDataContainer().has(CLE, PersistentDataType.BYTE)) {
                e.remove();
            }
        }
        if (!Salle.construite(monde)) {
            long t0 = System.currentTimeMillis();
            int n = Salle.construire(monde);
            getLogger().info("Salle ORBIT BOWL construite (" + n + " blocs, " + (System.currentTimeMillis() - t0) + " ms).");
        }
        poserEntites();
        entree.attendre();
        tick = Bukkit.getScheduler().runTaskTimer(this, this::tick, 1L, 1L);
        minute = Bukkit.getScheduler().runTaskTimer(this, () -> {
            entree.lireRecords();
            postes.rafraichirTout();
        }, 1200L, 1200L);
        getLogger().info("ORBIT BOWL ouvert (monde " + nom + ") : 6 pistes, 3 cibles, 2 stands de tir, 3 paniers, 2 baby-foot, golf 9 trous, laser game.");
    }

    private void poserEntites() {
        Salle.decorer(monde);
        postes.poser(monde);
        entree.poserSalle(monde);
    }

    @Override
    public void onDisable() {
        if (tick != null) {
            tick.cancel();
        }
        if (minute != null) {
            minute.cancel();
        }
        for (Poste p : postes == null ? List.<Poste>of() : postes.tous()) {
            if (p.activite != null) {
                p.activite.arreter();
            }
        }
        if (entree != null) {
            entree.retirer();
        }
        if (monde != null) {
            for (Entity e : monde.getEntities()) {
                if (e.getPersistentDataContainer().has(CLE, PersistentDataType.BYTE)) {
                    e.remove();
                }
            }
        }
        if (pack != null) {
            pack.arreter();
        }
        if (db != null) {
            db.close();
        }
    }

    private void tick() {
        for (Poste p : postes.tous()) {
            Activite a = p.activite;
            if (a == null || a.finie()) {
                continue;
            }
            try {
                a.tickInterne();
            } catch (RuntimeException e) {
                getLogger().severe(p.id + " : erreur, partie arrêtée : " + e);
                e.printStackTrace();
                a.arreter();
            }
        }
    }

    // ------------------------------------------------------------------ outils partagés

    public static void marquer(Entity e) {
        e.setPersistent(false);
        e.getPersistentDataContainer().set(CLE, PersistentDataType.BYTE, (byte) 1);
    }

    public static ItemStack objetQuitter() {
        ItemStack it = Activite.objet(Material.BARRIER, "quitter", "<#E0484A><b>Quitter</b>", "Clic droit : quitter la partie");
        var meta = it.getItemMeta();
        meta.getPersistentDataContainer().set(QUITTER, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        return it;
    }

    static boolean estQuitter(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(QUITTER, PersistentDataType.BYTE);
    }

    public Activite activite(Player p) {
        return enJeu.get(p.getUniqueId());
    }

    void entre(Player p, Activite a) {
        enJeu.put(p.getUniqueId(), a);
        postes.rafraichir(a.poste);
    }

    void sort(Player p) {
        Activite a = enJeu.remove(p.getUniqueId());
        if (a != null) {
            postes.rafraichir(a.poste);
        }
    }

    void libere(Activite a) {
        if (a.poste.activite == a) {
            a.poste.activite = null;
        }
        postes.rafraichir(a.poste);
    }

    /** Téléportation voulue par la salle (pas d'abandon de partie). */
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

    private boolean dansSalle(Player p) {
        return monde != null && p.getWorld().equals(monde);
    }

    public void entrerSalle(Player p) {
        if (monde == null) {
            Txt.send(p, "<#E0484A>ORBIT BOWL n'est pas encore ouvert.");
            return;
        }
        pack.envoyerSiBesoin(p);
        teleporter(p, Salle.spawn(monde));
        Txt.title(p, "<gradient:#3A7FFF:#E0B864><b>ORBIT BOWL</b></gradient>", "<#9AA4B2>bowling · loisirs · sous les étoiles", 2200);
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.4f);
        Txt.send(p, "Bienvenue ! Clique sur la console d'un jeu pour jouer. Sortie : arche dorée au bout de l'allée (est).");
    }

    public void sortirSalle(Player p) {
        Activite a = activite(p);
        if (a != null) {
            a.abandon(p, false);
        }
        teleporter(p, entree.retourArcade());
    }

    // ------------------------------------------------------------------ lancer / rejoindre

    private void jouer(Player p, Poste poste, Mode m, long mise) {
        if (!dansSalle(p)) {
            Txt.send(p, "<#E0484A>Les jeux se lancent depuis la salle ORBIT BOWL.");
            return;
        }
        if (activite(p) != null) {
            Txt.send(p, "<#9AA4B2>Tu es déjà dans une partie.");
            return;
        }
        if (!poste.libre()) {
            postes.menu(p, poste);
            return;
        }
        if (p.getLocation().distanceSquared(poste.console(monde)) > 40 * 40 && !p.hasPermission("orbitbowl.admin")) {
            Txt.send(p, "<#E0484A>Approche-toi de " + poste.nom + ".");
            return;
        }
        if (m.mise()) {
            boolean ok = false;
            for (long s : Mode.MISES) {
                ok |= s == mise;
            }
            if (!ok) {
                Txt.send(p, "<#E0484A>Mise inconnue.");
                return;
            }
            if (!paiements.add(p.getUniqueId())) {
                return;
            }
            Caisse.debiter(p, mise, poste.jeu.id, "mise du défi", fait -> {
                paiements.remove(p.getUniqueId());
                if (!fait) {
                    Txt.send(p, "<#E0484A>Solde insuffisant pour miser " + Txt.oc(mise) + ".");
                    return;
                }
                if (!p.isOnline() || !poste.libre() || activite(p) != null) {
                    Caisse.crediter(p.getUniqueId(), mise, poste.jeu.id, "défi impossible : mise rendue", false);
                    if (p.isOnline()) {
                        Txt.send(p, "<#E0484A>Le poste vient d'être pris : mise rendue.");
                    }
                    return;
                }
                lancer(p, poste, m, mise);
            });
            return;
        }
        lancer(p, poste, m, 0);
    }

    private void lancer(Player p, Poste poste, Mode m, long mise) {
        Activite a = postes.creer(poste, m, mise);
        poste.activite = a;
        dernier.put(p.getUniqueId(), new String[]{poste.id, m.id()});
        a.ouvrir(p);
        postes.rafraichir(poste);
    }

    private void rejoindre(Player p, Poste poste) {
        if (!dansSalle(p)) {
            Txt.send(p, "<#E0484A>Rejoins la salle ORBIT BOWL d'abord.");
            return;
        }
        if (activite(p) != null) {
            Txt.send(p, "<#9AA4B2>Tu es déjà dans une partie.");
            return;
        }
        Activite a = poste.activite;
        if (a == null || !a.ouverte()) {
            Txt.send(p, "<#E0484A>Cette partie n'attend plus de joueurs.");
            return;
        }
        if (a.mise > 0) {
            if (getConfig().getBoolean("defis.meme-ip-interdite", true) && !p.hasPermission("orbitbowl.admin")) {
                for (Player o : a.joueurs()) {
                    if (Activite.ip(o).equals(Activite.ip(p))) {
                        Txt.send(p, "<#E0484A>Défi misé impossible entre deux comptes de la même connexion.");
                        return;
                    }
                }
            }
            if (!paiements.add(p.getUniqueId())) {
                return;
            }
            Caisse.debiter(p, a.mise, poste.jeu.id, "mise du défi", fait -> {
                paiements.remove(p.getUniqueId());
                if (!fait) {
                    Txt.send(p, "<#E0484A>Solde insuffisant pour miser " + Txt.oc(a.mise) + ".");
                    return;
                }
                if (!p.isOnline() || poste.activite != a || !a.ouverte() || activite(p) != null) {
                    Caisse.crediter(p.getUniqueId(), a.mise, poste.jeu.id, "défi déjà pris : mise rendue", false);
                    return;
                }
                a.rejoindre(p);
            });
            return;
        }
        a.rejoindre(p);
    }

    // ------------------------------------------------------------------ commandes

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("admin")) {
            return admin(sender, args);
        }
        if (sub.equals("top") || sub.equals("regles")) {
            Jeu j = args.length > 1 ? Jeu.parse(args[1]) : null;
            if (j == null) {
                Txt.send(sender, "Jeux : bowling, flechettes, tir, basket, golf, baby, laser.");
            } else if (sub.equals("top")) {
                scores.top(sender, j);
            } else {
                Regles.montrer(sender, j);
            }
            return true;
        }
        if (!(sender instanceof Player p)) {
            Txt.send(sender, "/bowl admin <parties|construire|pnj|stop>");
            return true;
        }
        switch (sub) {
            case "jouer" -> {
                Poste poste = args.length > 1 ? postes.get(args[1]) : null;
                Mode m = poste == null || args.length < 3 ? null : Mode.parse(poste.jeu, args[2]);
                if (poste == null || m == null) {
                    Txt.send(p, "<#9AA4B2>Clique sur la console d'un jeu dans la salle.");
                    return true;
                }
                long mise = 0;
                if (args.length > 3) {
                    try {
                        mise = Long.parseLong(args[3]);
                    } catch (NumberFormatException ignored) {
                    }
                }
                jouer(p, poste, m, mise);
            }
            case "rejoindre" -> {
                Poste poste = args.length > 1 ? postes.get(args[1]) : null;
                if (poste != null) {
                    rejoindre(p, poste);
                }
            }
            case "lancer" -> {
                Activite a = activite(p);
                if (a == null || a.phase() != Activite.Phase.ATTENTE) {
                    Txt.send(p, "<#9AA4B2>Aucune partie en attente.");
                } else if (!p.getUniqueId().equals(a.hote)) {
                    Txt.send(p, "<#9AA4B2>Seul le créateur de la partie peut la lancer.");
                } else if (!a.demarrer()) {
                    Txt.send(p, "<#E0484A>Il faut au moins " + a.mode.min() + " joueurs.");
                    a.boutonsHote(p);
                }
            }
            case "quitter" -> {
                Activite a = activite(p);
                if (a == null) {
                    Txt.send(p, "<#9AA4B2>Tu n'es dans aucune partie.");
                } else {
                    a.abandon(p, true);
                }
            }
            case "rejouer" -> {
                String[] d = dernier.get(p.getUniqueId());
                Poste poste = d == null ? null : postes.get(d[0]);
                Mode m = poste == null ? null : Mode.parse(poste.jeu, d[1]);
                if (m == null || m.mise()) {
                    Txt.send(p, "<#9AA4B2>Clique sur la console d'un jeu pour rejouer.");
                } else {
                    jouer(p, poste, m, 0);
                }
            }
            case "salle", "entrer" -> {
                if (dansSalle(p) || p.getWorld().getName().equals(entree.nomArcade()) || p.hasPermission("orbitbowl.admin")) {
                    entrerSalle(p);
                } else {
                    Txt.send(p, "<#E0484A>ORBIT BOWL est dans la salle d'arcade du casino.");
                }
            }
            case "sortir" -> {
                if (dansSalle(p)) {
                    sortirSalle(p);
                }
            }
            default -> entree.aide(p);
        }
        return true;
    }

    private boolean admin(CommandSender s, String[] args) {
        if (!s.hasPermission("orbitbowl.admin")) {
            Txt.send(s, "<#E0484A>Réservé au staff.");
            return true;
        }
        String a = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (a) {
            case "parties" -> {
                int n = 0;
                for (Poste p : postes.tous()) {
                    if (!p.libre()) {
                        n++;
                        s.sendMessage(Txt.mm("  <#9AA4B2>" + p.id + " · " + p.activite.mode.nom() + " · " + p.activite.phase() + " · " + p.activite.etat()));
                    }
                }
                Txt.send(s, n + " partie(s) en cours, " + enJeu.size() + " joueur(s).");
            }
            case "construire" -> {
                if (monde == null) {
                    return true;
                }
                if (args.length < 3 || !args[2].equalsIgnoreCase("confirmer")) {
                    Txt.send(s, "<#E0B864>Reconstruit toute la salle : les retouches faites à la main sont perdues. "
                            + "<#9AA4B2>Confirme avec /bowl admin construire confirmer");
                    return true;
                }
                for (Poste p : postes.tous()) {
                    if (p.activite != null) {
                        p.activite.arreter();
                    }
                }
                for (Entity e : monde.getEntities()) {
                    if (e.getPersistentDataContainer().has(CLE, PersistentDataType.BYTE)) {
                        e.remove();
                    }
                }
                postes.retirer();
                int n = Salle.construire(monde);
                poserEntites();
                Txt.send(s, "Salle reconstruite (" + n + " blocs).");
            }
            case "pnj" -> {
                for (Entity e : monde.getEntities()) {
                    if (e.getPersistentDataContainer().has(CLE, PersistentDataType.BYTE) && !(e instanceof Player)) {
                        boolean enPartie = false;
                        for (Poste p : postes.tous()) {
                            enPartie |= !p.libre();
                        }
                        if (!enPartie) {
                            e.remove();
                        }
                    }
                }
                postes.retirer();
                poserEntites();
                entree.poserPortier();
                Txt.send(s, "PNJ, consoles et décors reposés.");
            }
            case "stop" -> {
                for (Poste p : postes.tous()) {
                    if (p.activite != null) {
                        p.activite.arreter();
                    }
                }
                Txt.send(s, "Toutes les parties sont arrêtées (mises rendues).");
            }
            default -> Txt.send(s, "/bowl admin <parties|construire|pnj|stop>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("quitter", "rejouer", "lancer", "top", "regles", "salle", "sortir"));
            if (sender.hasPermission("orbitbowl.admin")) {
                out.add("admin");
            }
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("top") || args[0].equalsIgnoreCase("regles"))) {
            for (Jeu j : Jeu.values()) {
                out.add(j.id);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            out.addAll(List.of("parties", "construire", "pnj", "stop"));
        }
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }

    // ------------------------------------------------------------------ événements

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntite(PlayerInteractEntityEvent e) {
        Player p = e.getPlayer();
        Entity cible = e.getRightClicked();
        if (entree.estPortier(cible)) {
            e.setCancelled(true);
            if (e.getHand() == EquipmentSlot.HAND) {
                entrerSalle(p);
            }
            return;
        }
        if (!dansSalle(p)) {
            return;
        }
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Activite a = activite(p);
        if (a != null) {
            if (estQuitter(p.getInventory().getItemInMainHand())) {
                a.abandon(p, true);
            } else {
                a.clic(p, false);
            }
            return;
        }
        Poste poste = postes.console(cible);
        if (poste != null) {
            postes.menu(p, poste);
        } else if (entree.estHotesse(cible)) {
            entree.aide(p);
        }
    }

    /** Clic gauche sur une console ou une entité (dégâts annulés dans la salle). */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFrappe(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p) || !dansSalle(p)) {
            return;
        }
        e.setCancelled(true);
        Activite a = activite(p);
        if (a != null) {
            a.clic(p, true);
            return;
        }
        Poste poste = postes.console(e.getEntity());
        if (poste != null) {
            postes.menu(p, poste);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!dansSalle(p)) {
            return;
        }
        Activite a = activite(p);
        if (a == null) {
            Block b = e.getClickedBlock();
            if (b != null && b.getType().name().endsWith("_BUTTON") && b.getX() == Salle.X2 - 1) {
                e.setCancelled(true);
                sortirSalle(p);
                return;
            }
            if (b != null && e.getAction() == Action.RIGHT_CLICK_BLOCK && !p.hasPermission("orbitbowl.admin")) {
                e.setCancelled(true);
            }
            return;
        }
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) {
            return;
        }
        boolean droit = e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK;
        if (droit && estQuitter(e.getItem())) {
            a.abandon(p, true);
            return;
        }
        if (droit || e.getAction() == Action.LEFT_CLICK_AIR || e.getAction() == Action.LEFT_CLICK_BLOCK) {
            a.clic(p, !droit);
        }
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent e) {
        Activite a = activite(e.getPlayer());
        if (a != null) {
            a.accroupi(e.getPlayer(), e.isSneaking());
        }
    }

    @EventHandler
    public void onInput(PlayerInputEvent e) {
        Activite a = activite(e.getPlayer());
        if (a != null) {
            a.touche(e.getPlayer(), e.getInput());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDismount(EntityDismountEvent e) {
        if (e.getEntity() instanceof Player p) {
            Activite a = activite(p);
            if (a != null && a.assis(p)) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        paiements.remove(e.getPlayer().getUniqueId());
        Activite a = activite(e.getPlayer());
        if (a != null) {
            a.abandon(e.getPlayer(), false);
        }
    }

    /** Arrivé dans la salle hors partie (après un arrêt brutal) : retour à l'allée centrale. */
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (monde != null && p.getWorld().equals(monde)) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (p.isOnline() && activite(p) == null && p.getWorld().equals(monde)) {
                    teleporter(p, Salle.spawn(monde));
                    if (p.getGameMode() == GameMode.SURVIVAL) {
                        p.setGameMode(GameMode.ADVENTURE);
                    }
                }
            }, 5L);
        }
    }

    /** Téléporté hors de la salle par autre chose (boussole, /spawn…) : la partie s'arrête pour lui. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        Player p = e.getPlayer();
        if (tpInternes.contains(p.getUniqueId())) {
            return;
        }
        Activite a = activite(p);
        if (a != null && e.getTo() != null && !e.getTo().getWorld().equals(monde)) {
            Bukkit.getScheduler().runTask(this, () -> {
                if (activite(p) == a) {
                    a.abandon(p, false);
                }
            });
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (monde != null && e.getEntity().getWorld().equals(monde)) {
            e.setCancelled(true);
            if (e.getCause() == EntityDamageEvent.DamageCause.VOID && e.getEntity() instanceof Player p) {
                Activite a = activite(p);
                teleporter(p, a != null ? a.retour() : Salle.spawn(monde));
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
        if (dansSalle(e.getPlayer()) && (activite(e.getPlayer()) != null || !e.getPlayer().hasPermission("orbitbowl.admin"))) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (dansSalle(e.getPlayer()) && (activite(e.getPlayer()) != null || !e.getPlayer().hasPermission("orbitbowl.admin"))) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (activite(e.getPlayer()) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (activite(e.getPlayer()) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p && activite(p) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFly(PlayerToggleFlightEvent e) {
        if (activite(e.getPlayer()) != null && e.getPlayer().getGameMode() != GameMode.SPECTATOR) {
            e.setCancelled(true);
            e.getPlayer().setAllowFlight(false);
        }
    }
}
