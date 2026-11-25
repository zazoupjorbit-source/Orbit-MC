package fr.orbit.chasseurs;

import fr.orbit.chasseurs.Catalogue.Level;
import fr.orbit.chasseurs.Catalogue.Mode;
import fr.orbit.chasseurs.Catalogue.Paint;
import fr.orbit.chasseurs.Catalogue.ShipType;
import io.papermc.paper.event.player.PlayerArmSwingEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Parties en cours (une par mode, chacune dans son arène), file d'attente, état des joueurs (inventaire, mode de jeu,
 * vol : sauvegardés à l'entrée — aussi sur disque, au cas où le serveur s'arrête net — et rendus à la sortie) et
 * événements du jeu.
 */
final class Games implements Listener {

    private record Saved(GameMode mode, boolean allowFlight, boolean flying, ItemStack[] inventory, int slot, boolean nightVision, boolean invisible) {
    }

    private final OrbitChasseurs plugin;
    private final World world;
    private final Map<Mode, Arena> arenas = new EnumMap<>(Mode.class);
    private final Map<Mode, Match> matches = new EnumMap<>(Mode.class);
    private final Map<Mode, Deque<UUID>> queues = new EnumMap<>(Mode.class);
    private final Map<UUID, Match> playing = new HashMap<>();
    private final Map<UUID, Saved> saved = new HashMap<>();
    private final Map<UUID, Mode> lastMode = new HashMap<>();
    private final Set<UUID> teleporting = new HashSet<>(), joining = new HashSet<>();
    private final File backups;

    Games(OrbitChasseurs plugin, World world) {
        this.plugin = plugin;
        this.world = world;
        this.backups = new File(plugin.getDataFolder(), "sauvegardes");
        arenas.put(Mode.MELEE, new Arena(0, Arena.Theme.CEINTURE, world));
        arenas.put(Mode.DUO, new Arena(1, Arena.Theme.EPAVE, world));
        arenas.put(Mode.CONVOI, new Arena(2, Arena.Theme.ROUTE, world));
        for (Mode m : Mode.values()) {
            queues.put(m, new ArrayDeque<>());
        }
    }

    /** Pose les arènes dans le monde, l'une après l'autre (seulement si elles ont changé). */
    void build(boolean force) {
        buildNext(new ArrayList<>(List.of(Mode.values())), force);
    }

    private void buildNext(List<Mode> rest, boolean force) {
        if (rest.isEmpty()) {
            return;
        }
        Mode m = rest.remove(0);
        Arena a = arenas.get(m);
        if (!force && a.built(plugin)) {
            a.ready = true;
            buildNext(rest, false);
            return;
        }
        if (matches.containsKey(m)) {
            buildNext(rest, force);
            return;
        }
        boolean clear = world.getPersistentDataContainer().has(new org.bukkit.NamespacedKey(plugin, "arene_" + a.slot));
        long t0 = System.currentTimeMillis();
        plugin.getLogger().info("Arène « " + m.arene + " » : pose de " + Txt.n(a.blocks) + " blocs" + (clear ? " (ancienne version effacée)" : "") + "…");
        a.build(plugin, clear, () -> {
            plugin.getLogger().info("Arène « " + m.arene + " » prête (" + (System.currentTimeMillis() - t0) / 1000 + " s).");
            buildNext(rest, force);
        });
    }

    Arena arena(Mode m) {
        return arenas.get(m);
    }

    Match of(Player p) {
        return playing.get(p.getUniqueId());
    }

    String status() {
        StringBuilder sb = new StringBuilder();
        for (Mode m : Mode.values()) {
            Arena a = arenas.get(m);
            Match x = matches.get(m);
            sb.append("\n  <#9AA4B2>").append(m.label).append(" · ").append(m.arene).append(" (").append(Txt.n(a.blocks)).append(" blocs, ")
                    .append(a.ready ? "prête" : a.building ? "en construction" : "pas posée").append(") : </#9AA4B2><#E6EDF3>")
                    .append(x == null ? "libre" : x.state + ", " + x.byPlayer.size() + " joueur(s), " + x.ships.size() + " vaisseaux")
                    .append(queues.get(m).isEmpty() ? "" : ", file " + queues.get(m).size());
        }
        return sb.toString();
    }

    void tick() {
        for (Match m : new ArrayList<>(matches.values())) {
            try {
                m.tick();
            } catch (RuntimeException e) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "Partie " + m.mode.id + " arrêtée sur une erreur", e);
                m.dispose();
            }
        }
    }

    /**
     * Essai sans joueur (console) : une partie de bots seuls, pour vérifier la simulation de bout en bout.
     * Le résumé part dans le journal à la fin ; le convoi, qui n'a pas de durée, est arrêté après {@code seconds}.
     */
    String test(Mode mode, int seconds) {
        Arena a = arenas.get(mode);
        if (!a.ready) {
            return "arène pas encore prête";
        }
        if (matches.containsKey(mode)) {
            return "une partie est déjà en cours dans ce mode";
        }
        Match m = new Match(plugin, this, mode, a, Level.AS);
        matches.put(mode, m);
        m.testRun(seconds);
        return "essai lancé (" + mode.id + ", " + seconds + " s), résumé dans le journal";
    }

    void stopAll() {
        for (Match m : new ArrayList<>(matches.values())) {
            m.dispose();
        }
    }

    // ------------------------------------------------------------------ entrée et sortie

    void play(Player p, Mode mode, Level wanted) {
        UUID id = p.getUniqueId();
        if (playing.containsKey(id)) {
            Txt.send(p, "Tu es déjà en vol. <#9AA4B2>/chasseurs quitter pour abandonner.");
            return;
        }
        if (!joining.add(id)) {
            return;
        }
        Arena a = arenas.get(mode);
        if (!a.ready) {
            joining.remove(id);
            Txt.send(p, "<#FFB000>L'arène « " + mode.arene + " » est encore en construction, réessaie dans un instant.");
            return;
        }
        plugin.profiles().load(p, pr -> {
            joining.remove(id);
            if (!p.isOnline() || playing.containsKey(id)) {
                return;
            }
            if (wanted != null && wanted.number() != pr.botLevel) {
                pr.botLevel = wanted.number();
                plugin.profiles().saveChoices(pr);
            }
            Match m = matches.get(mode);
            if (m == null) {
                m = new Match(plugin, this, mode, a, Level.of(pr.botLevel));
                matches.put(mode, m);
            }
            if (!m.joinable()) {
                Deque<UUID> q = queues.get(mode);
                if (!q.contains(id)) {
                    q.add(id);
                }
                Txt.send(p, "<#FFB000>Partie complète ou sur le point de finir.</#FFB000> Tu es dans la file d'attente <#9AA4B2>(place " + q.size()
                        + ")</#9AA4B2> : tu décolles dès la prochaine partie.");
                return;
            }
            for (Deque<UUID> q : queues.values()) {
                q.remove(id);
            }
            lastMode.put(id, mode);
            enter(p);
            playing.put(id, m);
            try {
                m.addPlayer(p, pr);
            } catch (RuntimeException e) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "Entrée en partie impossible pour " + p.getName(), e);
                leave(p, false);
            }
        });
    }

    void replay(Player p) {
        Mode m = lastMode.getOrDefault(p.getUniqueId(), Mode.MELEE);
        play(p, m, null);
    }

    void teleport(Player p, Location to) {
        teleporting.add(p.getUniqueId());
        try {
            p.teleport(to);
        } finally {
            teleporting.remove(p.getUniqueId());
        }
    }

    private void enter(Player p) {
        if (p.isInsideVehicle()) {
            p.leaveVehicle();
        }
        p.closeInventory();
        Saved s = new Saved(p.getGameMode(), p.getAllowFlight(), p.isFlying(), p.getInventory().getContents(), p.getInventory().getHeldItemSlot(),
                p.hasPotionEffect(PotionEffectType.NIGHT_VISION), p.hasPotionEffect(PotionEffectType.INVISIBILITY));
        saved.put(p.getUniqueId(), s);
        backup(p, s);
        p.getInventory().clear();
        p.getInventory().setHeldItemSlot(0);
        p.setGameMode(GameMode.ADVENTURE);
        p.setAllowFlight(true);
        p.setFlying(true);
        p.setFallDistance(0);
        if (!s.nightVision) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, PotionEffect.INFINITE_DURATION, 0, false, false, false));
        }
        if (!s.invisible) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false, false, false));
        }
        if (!plugin.pack().isLoaded(p)) {
            plugin.pack().send(p);
            Txt.send(p, "<#FFB000>Accepte le pack de ressources pour voir les vaisseaux en 3D.");
        }
    }

    /** Barre d'objets du pilote : gâchette, missiles (le nombre), réparation. */
    void hotbar(Player p, int missiles, boolean repairReady) {
        if (p == null || !playing.containsKey(p.getUniqueId())) {
            return;
        }
        p.getInventory().setItem(0, item(Material.PAPER, "vide", 1, "<#62CFF4><b>Laser</b></#62CFF4> <#9AA4B2>· clic (droit maintenu = rafale)"));
        p.getInventory().setItem(1, missiles > 0 ? item(Material.PAPER, "missile", Math.min(64, missiles), "<#FF4FD8><b>Missile</b></#FF4FD8> <#9AA4B2>· touche 2 ou F")
                : item(Material.GRAY_DYE, null, 1, "<#5A6270>Plus de missile"));
        p.getInventory().setItem(2, repairReady ? item(Material.PAPER, "bonus_reparation", 1, "<#7CFF8A><b>Nanoréparation</b></#7CFF8A> <#9AA4B2>· touche 3")
                : item(Material.GRAY_DYE, null, 1, "<#5A6270>Nanoréparation en recharge"));
    }

    private static ItemStack item(Material material, String model, int amount, String name) {
        ItemStack it = new ItemStack(material, amount);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Txt.mm(name));
        if (model != null) {
            meta.setItemModel(new org.bukkit.NamespacedKey("orbitchasseurs", model));
        }
        it.setItemMeta(meta);
        return it;
    }

    /** Sortie d'une partie : place rendue à un bot, état du joueur rendu, retour devant le PNJ. */
    void leave(Player p, boolean record) {
        leave(p, record, true);
    }

    private void leave(Player p, boolean record, boolean bringBack) {
        Match m = playing.remove(p.getUniqueId());
        if (m == null) {
            return;
        }
        m.removePlayer(p, record);
        restore(p, bringBack);
    }

    private void restore(Player p, boolean bringBack) {
        Saved s = saved.remove(p.getUniqueId());
        if (p.isInsideVehicle()) {
            p.leaveVehicle();
        }
        p.clearTitle();
        if (s != null) {
            p.getInventory().setContents(s.inventory);
            p.getInventory().setHeldItemSlot(s.slot);
            p.setGameMode(s.mode);
            p.setAllowFlight(s.allowFlight);
            p.setFlying(s.allowFlight && s.flying);
            if (!s.nightVision) {
                p.removePotionEffect(PotionEffectType.NIGHT_VISION);
            }
            if (!s.invisible) {
                p.removePotionEffect(PotionEffectType.INVISIBILITY);
            }
        }
        p.setFallDistance(0);
        if (bringBack) {
            Location back = plugin.returnPoint();
            if (back != null) {
                teleport(p, back);
            } else if (p.getWorld().equals(world)) {
                teleport(p, Bukkit.getWorlds().get(0).getSpawnLocation());
            }
        }
        new File(backups, p.getUniqueId() + ".yml").delete();
    }

    private void backup(Player p, Saved s) {
        try {
            backups.mkdirs();
            YamlConfiguration y = new YamlConfiguration();
            y.set("mode", s.mode.name());
            y.set("vol", s.allowFlight);
            y.set("case", s.slot);
            y.set("inventaire", Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(s.inventory)));
            y.save(new File(backups, p.getUniqueId() + ".yml"));
        } catch (Exception e) {
            plugin.getLogger().warning("Sauvegarde de l'inventaire de " + p.getName() + " impossible : " + e);
        }
    }

    /** Le serveur s'est arrêté net pendant une partie : l'inventaire sauvegardé est rendu à la reconnexion. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        File f = new File(backups, p.getUniqueId() + ".yml");
        if (!f.isFile() || playing.containsKey(p.getUniqueId())) {
            return;
        }
        try {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            ItemStack[] inv = ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(y.getString("inventaire", "")));
            p.getInventory().setContents(inv);
            p.getInventory().setHeldItemSlot(Math.max(0, Math.min(8, y.getInt("case", 0))));
            p.setGameMode(GameMode.valueOf(y.getString("mode", "ADVENTURE")));
            p.setAllowFlight(y.getBoolean("vol", false));
            p.removePotionEffect(PotionEffectType.NIGHT_VISION);
            p.removePotionEffect(PotionEffectType.INVISIBILITY);
            plugin.getLogger().info("Inventaire de " + p.getName() + " rendu après un arrêt en pleine partie.");
        } catch (Exception e) {
            plugin.getLogger().warning("Inventaire sauvegardé de " + p.getName() + " illisible : " + e);
        }
        f.delete();
    }

    /** La partie est finie et vidée : la suivante démarre pour ceux qui attendaient. */
    void closed(Match m) {
        matches.remove(m.mode, m);
        Deque<UUID> q = queues.get(m.mode);
        if (q.isEmpty() || !plugin.isEnabled()) {
            return;
        }
        List<UUID> waiting = new ArrayList<>(q);
        q.clear();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (UUID id : waiting) {
                Player p = Bukkit.getPlayer(id);
                if (p != null && !playing.containsKey(id)) {
                    Txt.send(p, "<#7CFF8A>Une place s'est libérée : décollage !");
                    play(p, m.mode, null);
                }
            }
        }, 20L);
    }

    // ------------------------------------------------------------------ récompenses

    /** Enregistre le résultat d'un joueur : expérience, record de la salle, Orbit Coins (plafond du jour). */
    void reward(Match m, Ship s, boolean win, boolean finished) {
        Player p = s.player;
        UUID id = p.getUniqueId();
        String name = p.getName();
        int score = m.score(s, win), kills = s.kills, deaths = s.deaths, wave = m.mode == Mode.CONVOI ? m.wave : 0;
        int per = Math.max(1, plugin.getConfig().getInt("oc.points-par-oc", 10));
        int oc = score / per + (win ? plugin.getConfig().getInt("oc.bonus-victoire", 50) : 0);
        Mode mode = m.mode;
        plugin.profiles().record(id, name, mode, score, kills, deaths, win, wave, oc, out -> {
            boolean paid = out.oc() > 0 && Coins.credit(plugin, id, out.oc(), mode.id + " " + score + " pts");
            Player online = Bukkit.getPlayer(id);
            if (online == null) {
                return;
            }
            Profiles.Profile pr = out.profile();
            boolean record = score > out.bestBefore() && score > 0;
            int next = Profiles.xpFor(pr.level() + 1);
            online.sendMessage(Txt.mm("  <#62CFF4>+" + Txt.n(score) + " XP</#62CFF4> <#5A6270>·</#5A6270> "
                    + (paid ? "+" + Txt.oc(out.oc()) : out.oc() > 0 ? "<#9AA4B2>OC non versés (casino absent)" : "<#9AA4B2>0 OC")
                    + (out.capped() ? " <#9AA4B2>(plafond de " + Txt.n(plugin.getConfig().getInt("oc.plafond-jour", 1500)) + " OC par jour atteint)</#9AA4B2>" : "")
                    + " <#5A6270>·</#5A6270> <#E6EDF3>pilote niveau <b>" + pr.level() + "</b></#E6EDF3> <#9AA4B2>(" + Txt.n(next - pr.xp) + " XP avant le niveau " + (pr.level() + 1) + ")"
                    + (record ? "  <#7CFF8A><b>NOUVEAU RECORD !</b>" : "")));
            // déblocages gagnés par cette partie
            List<String> unlocked = new ArrayList<>();
            for (ShipType t : ShipType.HANGAR) {
                if (t.level > out.levelBefore() && t.level <= out.levelAfter()) {
                    unlocked.add("vaisseau <b>" + t.label + "</b>");
                }
            }
            Profiles.Profile before = new Profiles.Profile(id, name);
            before.xp = Profiles.xpFor(out.levelBefore());
            before.kills = pr.kills - kills;
            before.wins = pr.wins - (win ? 1 : 0);
            before.bestWave = out.waveBefore();
            for (Paint c : Paint.values()) {
                if (c.unlocked(pr) && !c.unlocked(before)) {
                    unlocked.add("couleur <" + Txt.hex(c.rgb) + "><b>" + c.label + "</b></" + Txt.hex(c.rgb) + ">");
                }
            }
            if (!unlocked.isEmpty()) {
                online.sendMessage(Txt.mm("  <#E0B864><b>✦ Débloqué :</b></#E0B864> <#E6EDF3>" + String.join("<#5A6270>,</#5A6270> ", unlocked)
                        + " <#9AA4B2>· à choisir au hangar"));
                online.playSound(online.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.3f);
            }
            if (finished) {
                buttons(online);
            }
        }, () -> {
            Player online = Bukkit.getPlayer(id);
            if (online != null) {
                Txt.send(online, "Score : <b>" + Txt.n(score) + "</b> <#E0484A>(base injoignable : rien d'enregistré)");
                if (finished) {
                    buttons(online);
                }
            }
        });
    }

    private static void buttons(Player p) {
        p.sendMessage(Txt.mm("  ").append(Txt.button("Rejouer", "#62CFF4", "/chasseurs rejouer", "Une nouvelle partie dans le même mode"))
                .append(Component.text(" ")).append(Txt.button("Classement", "#E0B864", "/chasseurs top", "Les 10 meilleurs scores de chaque mode"))
                .append(Component.text(" ")).append(Txt.button("Hangar", "#C79BFF", "/chasseurs hangar", "Choisir son vaisseau et sa couleur")));
    }

    // ------------------------------------------------------------------ événements

    @EventHandler
    public void onInput(PlayerInputEvent event) {
        Match m = playing.get(event.getPlayer().getUniqueId());
        if (m != null) {
            m.input(event.getPlayer(), event.getInput());
        }
    }

    /** Maj sert au dérapage : on ne descend pas de son vaisseau en vol. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDismount(EntityDismountEvent event) {
        if (event.getEntity() instanceof Player p && playing.containsKey(p.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        Match m = playing.get(event.getPlayer().getUniqueId());
        if (m == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getAction() == Action.PHYSICAL || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        boolean right = event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK;
        m.trigger(event.getPlayer(), right);
    }

    @EventHandler
    public void onSwing(PlayerArmSwingEvent event) {
        Match m = playing.get(event.getPlayer().getUniqueId());
        if (m != null && event.getHand() == EquipmentSlot.HAND) {
            m.trigger(event.getPlayer(), false);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onHeld(PlayerItemHeldEvent event) {
        Match m = playing.get(event.getPlayer().getUniqueId());
        if (m == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getNewSlot() == 1) {
            m.missileKey(event.getPlayer());
        } else if (event.getNewSlot() == 2) {
            m.repairKey(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Match m = playing.get(event.getPlayer().getUniqueId());
        if (m != null) {
            event.setCancelled(true);
            m.missileKey(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDrop(PlayerDropItemEvent event) {
        if (playing.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onClick(InventoryClickEvent event) {
        if (playing.containsKey(event.getWhoClicked().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player p && (playing.containsKey(p.getUniqueId()) || p.getWorld().equals(world))) {
            event.setCancelled(true);
            if (event.getCause() == EntityDamageEvent.DamageCause.VOID && !playing.containsKey(p.getUniqueId())) {
                // tombé dans le vide du monde des arènes sans être en partie : retour à la salle
                Location back = plugin.returnPoint();
                Bukkit.getScheduler().runTask(plugin, () -> p.teleport(back != null ? back : Bukkit.getWorlds().get(0).getSpawnLocation()));
            }
        }
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent event) {
        if (event.getEntity().getWorld().equals(world)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        UUID id = p.getUniqueId();
        leave(p, true, true);
        for (Deque<UUID> q : queues.values()) {
            q.remove(id);
        }
        joining.remove(id);
        lastMode.remove(id);
        plugin.profiles().forget(id);
    }

    /** Téléporté ailleurs par autre chose (boussole, /hub…) : la partie s'arrête pour lui, sans le ramener. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player p = event.getPlayer();
        Match m = playing.get(p.getUniqueId());
        if (m == null || teleporting.contains(p.getUniqueId()) || event.getTo() == null) {
            return;
        }
        Location to = event.getTo();
        boolean away = !to.getWorld().equals(world)
                || m.arena.fromCentre(new org.joml.Vector3d(to.getX(), to.getY(), to.getZ())) > Arena.R + 40;
        if (away) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (playing.get(p.getUniqueId()) == m) {
                    leave(p, true, false);
                }
            });
        }
    }
}
