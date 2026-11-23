package fr.orbit.casino;

import fr.orbit.casino.game.Blackjack;
import fr.orbit.casino.game.Poker;
import fr.orbit.casino.game.Roulette;
import fr.orbit.casino.game.Seated;
import fr.orbit.casino.game.Slots;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Switch;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * La salle et ses jeux : monde « casino », entrée secrète dans la station, clics sur les machines et les
 * tables, PNJ, tâche de chaque seconde.
 */
public final class Casino implements Listener {

    private final OrbitCasino plugin;
    private World world;
    private Slots slots;
    private Roulette roulette;
    private final List<Blackjack> blackjacks = new ArrayList<>();
    private final List<Poker> pokers = new ArrayList<>();
    private final List<Seated> tables = new ArrayList<>();
    private Entity cashier;

    public Casino(OrbitCasino plugin) {
        this.plugin = plugin;
    }

    public World world() {
        return world;
    }

    public Slots slots() {
        return slots;
    }

    public void start() {
        String name = plugin.getConfig().getString("casino.monde", "casino");
        world = Bukkit.getWorld(name);
        if (world == null) {
            world = new WorldCreator(name).generator(new VoidGenerator()).generateStructures(false).createWorld();
        }
        if (world == null) {
            plugin.getLogger().severe("Monde du casino impossible à créer.");
            return;
        }
        world.setSpawnLocation(Room.spawn(world));
        world.setTime(18000);
        world.setGameRule(org.bukkit.GameRules.ADVANCE_TIME, false);
        world.setGameRule(org.bukkit.GameRules.ADVANCE_WEATHER, false);
        world.setGameRule(org.bukkit.GameRules.SPAWN_MOBS, false);
        // la salle reste chargée : PNJ et écrans ne disparaissent pas
        for (int cx = -2; cx <= 1; cx++) {
            for (int cz = -2; cz <= 1; cz++) {
                Chunk chunk = world.getChunkAt(cx, cz);
                chunk.addPluginChunkTicket(plugin);
            }
        }
        if (world.getBlockAt(0, Room.FLOOR, 0).getType().isAir()) {
            int n = Room.build(world);
            plugin.getLogger().info("Salle du casino construite (" + n + " blocs).");
        }
        spawnAll();
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private void spawnAll() {
        Holo.clear(world);
        tables.clear();
        blackjacks.clear();
        pokers.clear();
        slots = new Slots(plugin, world);
        slots.spawn();
        roulette = new Roulette(plugin, world);
        roulette.spawn();
        for (int i = 0; i < Room.BJ_Z.length; i++) {
            Blackjack b = new Blackjack(plugin, world, i + 1, Room.BJ_Z[i]);
            b.spawn();
            blackjacks.add(b);
            tables.add(b);
        }
        ConfigurationSection pt = plugin.getConfig().getConfigurationSection("poker.tables");
        List<String> ids = pt == null ? List.of() : new ArrayList<>(pt.getKeys(false));
        for (int i = 0; i < Room.POKER_Z.length && i < ids.size(); i++) {
            String id = ids.get(i);
            Poker p = new Poker(plugin, world, id, pt.getString(id + ".nom", id), pt.getLong(id + ".petite-blinde", 10),
                    pt.getLong(id + ".grosse-blinde", 20), Room.POKER_Z[i]);
            p.spawn();
            pokers.add(p);
            tables.add(p);
        }
        cashier = Holo.npc(Room.cashier(world), "<#E0B864>Caissier · Boutique", org.bukkit.entity.Villager.Profession.LIBRARIAN);
        Holo.text(Room.cashier(world).add(0, 2.9, 0), "<#E0B864><b>CAISSE</b></#E0B864>\n<#9AA4B2>Orbit Coins · boutique · classement", 0.9f, false);
        Holo.npc(Room.barman(world), "<#62CFF4>Barman", org.bukkit.entity.Villager.Profession.BUTCHER);
        Holo.text(Room.barman(world).add(0, 2.9, 0), "<#62CFF4><b>LE BAR</b></#62CFF4>\n<#9AA4B2>Sans alcool, c'est une station.", 0.9f, false);
        Holo.text(new Location(world, Room.EXIT_X + 0.5, Room.Y + 4.6, Room.EXIT_Z + 0.2, 180f, 0f),
                "<#9AA4B2>↩ <b>SORTIE</b></#9AA4B2>\n<#5A6270>bouton à côté de la porte", 0.8f, false);
        Holo.text(new Location(world, Room.BJ_X + 0.5, Room.Y + 5.5, 0.5), "<#E0B864><b>BLACKJACK</b>", 2.0f, false);
        Holo.text(new Location(world, Room.POKER_X + 0.5, Room.Y + 5.5, 0.5), "<#C79BFF><b>POKER</b>", 2.0f, false);
    }

    /** /casino generer : reconstruit la salle (les joueurs présents sont ramenés à l'entrée). */
    public int rebuild() {
        shutdownTables();
        for (Player p : world.getPlayers()) {
            p.teleport(Room.spawn(world));
        }
        int n = Room.build(world);
        spawnAll();
        return n;
    }

    public void stop() {
        shutdownTables();
        Holo.clear(world);
    }

    private void shutdownTables() {
        for (Seated t : tables) {
            t.shutdown();
        }
        if (roulette != null) {
            roulette.shutdown();
        }
    }

    private void tick() {
        for (Seated t : tables) {
            try {
                t.tick();
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Table : " + e);
            }
        }
        roulette.tick();
    }

    public boolean seatedAnywhere(UUID uuid) {
        for (Seated t : tables) {
            if (t.has(uuid)) {
                return true;
            }
        }
        return false;
    }

    public Seated tableOf(UUID uuid) {
        for (Seated t : tables) {
            if (t.has(uuid)) {
                return t;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ entrée secrète

    private Block entrance() {
        ConfigurationSection e = plugin.getConfig().getConfigurationSection("casino.entree");
        World w = e == null ? null : Bukkit.getWorld(e.getString("monde", "station"));
        return w == null ? null : w.getBlockAt(e.getInt("x"), e.getInt("y"), e.getInt("z"));
    }

    private Location exit() {
        ConfigurationSection e = plugin.getConfig().getConfigurationSection("casino.entree");
        World w = e == null ? null : Bukkit.getWorld(e.getString("monde", "station"));
        if (w == null) {
            return Bukkit.getWorlds().get(0).getSpawnLocation();
        }
        List<Double> s = e.getDoubleList("sortie");
        if (s.size() < 3) {
            return w.getSpawnLocation();
        }
        return new Location(w, s.get(0), s.get(1), s.get(2), s.size() > 3 ? s.get(3).floatValue() : 0f, 0f);
    }

    /** Pose un bouton en chêne pâle (presque invisible sur le quartz) à l'emplacement réglé, s'il est libre. */
    public String placeButton() {
        Block b = entrance();
        if (b == null) {
            return "monde de l'entrée introuvable";
        }
        if (Tag.isButton(b.getType())) {
            return "bouton déjà en place en " + b.getX() + " " + b.getY() + " " + b.getZ();
        }
        if (!b.getType().isAir()) {
            return "le bloc " + b.getX() + " " + b.getY() + " " + b.getZ() + " n'est pas libre (" + b.getType().getKey().getKey() + ")";
        }
        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            Block support = b.getRelative(face.getOppositeFace());
            if (support.getType().isSolid()) {
                BlockData data = Material.PALE_OAK_BUTTON.createBlockData();
                if (data instanceof Switch sw) {
                    sw.setAttachedFace(org.bukkit.block.data.FaceAttachable.AttachedFace.WALL);
                    sw.setFacing(face);
                }
                b.setBlockData(data, false);
                return "bouton posé en " + b.getX() + " " + b.getY() + " " + b.getZ() + " (contre " + support.getType().getKey().getKey() + ")";
            }
        }
        return "aucun mur plein à côté de " + b.getX() + " " + b.getY() + " " + b.getZ();
    }

    /** /casino bouton : l'entrée devient le bloc visé (un bouton, un bloc décoratif…). */
    public String setEntrance(Player p) {
        Block b = p.getTargetBlockExact(6);
        if (b == null) {
            return "vise un bloc à moins de 6 blocs";
        }
        plugin.getConfig().set("casino.entree.monde", b.getWorld().getName());
        plugin.getConfig().set("casino.entree.x", b.getX());
        plugin.getConfig().set("casino.entree.y", b.getY());
        plugin.getConfig().set("casino.entree.z", b.getZ());
        Location l = p.getLocation();
        plugin.getConfig().set("casino.entree.sortie", List.of(Math.floor(l.getX()) + 0.5, Math.floor(l.getY()), Math.floor(l.getZ()) + 0.5,
                (double) Math.round(l.getYaw())));
        plugin.saveConfig();
        return "entrée = " + b.getType().getKey().getKey() + " en " + b.getX() + " " + b.getY() + " " + b.getZ()
                + " ; on ressort là où tu te tiens";
    }

    public void enter(Player p) {
        p.teleport(Room.spawn(world));
        p.playSound(p.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.8f, 0.7f);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.2f);
        Txt.send(p, "<#E0B864><b>Bienvenue au casino clandestin d'ORBIT.</b></#E0B864> Ici, tout se joue en Orbit Coins : ils "
                + "s'échangent contre des cosmétiques à la caisse. Solde : " + Txt.oc(Math.max(0, plugin.wallet().balance(p))) + ".");
    }

    public void leaveRoom(Player p) {
        Seated t = tableOf(p.getUniqueId());
        if (t != null) {
            t.leave(p, null);
        }
        p.teleport(exit());
        p.playSound(p.getLocation(), Sound.BLOCK_ENDER_CHEST_CLOSE, 0.8f, 0.8f);
    }

    // ------------------------------------------------------------------ clics

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null) {
            return;
        }
        Player p = event.getPlayer();
        Block b = event.getClickedBlock();
        Block door = entrance();
        if (b.equals(door)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> enter(p), 4L);
            return;
        }
        if (!b.getWorld().equals(world)) {
            return;
        }
        if (b.getX() == Room.EXIT_X && b.getY() == Room.EXIT_Y && b.getZ() == Room.EXIT_Z) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> leaveRoom(p), 4L);
            return;
        }
        Integer machine = slots.machineAt(b);
        if (machine != null) {
            event.setCancelled(true);
            slots.click(p, machine, p.isSneaking());
            return;
        }
        if (Room.isRoulette(b)) {
            event.setCancelled(true);
            roulette.open(p);
            return;
        }
        for (Blackjack t : blackjacks) {
            if (Room.isBlackjack(b, t.cz())) {
                event.setCancelled(true);
                t.sit(p, b.getX() + 0.5);
                return;
            }
        }
        for (Poker t : pokers) {
            if (Room.isPoker(b, t.cz())) {
                event.setCancelled(true);
                t.open(p, b.getLocation().add(0.5, 0, 0.5));
                return;
            }
        }
        if (p.getGameMode() != GameMode.CREATIVE) {
            event.setCancelled(true); // rien d'autre ne s'ouvre ni ne bouge dans la salle
        }
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Entity e = event.getRightClicked();
        if (!Holo.isOurs(e)) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player p = event.getPlayer();
        if (e.equals(cashier)) {
            plugin.shop().open(p);
            return;
        }
        String name = e.customName() == null ? "" : net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(e.customName());
        if (name.startsWith("Barman")) {
            String[] lines = {"Un jus de comète ? C'est la maison qui offre.", "Le gros lot n'est pas tombé depuis un moment…",
                    "Au poker, le casino ne prend rien. Tout ce qui se mise reste entre joueurs.",
                    "Tu cherches la sortie ? Le bouton près de la porte, au sud.",
                    "Si on te demande, ce casino n'existe pas."};
            Txt.send(p, "<#62CFF4>Barman</#62CFF4> <#5A6270>»</#5A6270> " + lines[fr.orbit.casino.game.Cards.RANDOM.nextInt(lines.length)]);
        } else if (name.contains("Roulette")) {
            roulette.open(p);
        } else {
            Txt.send(p, "Clic droit sur le tapis vert pour t'asseoir. <#9AA4B2>/table quitter pour partir.");
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity().getWorld().equals(world)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (event.getBlock().getWorld().equals(world) && !(event.getPlayer().getGameMode() == GameMode.CREATIVE
                && event.getPlayer().hasPermission("orbitcasino.admin"))) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (event.getBlock().getWorld().equals(world) && !(event.getPlayer().getGameMode() == GameMode.CREATIVE
                && event.getPlayer().hasPermission("orbitcasino.admin"))) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onHanging(HangingBreakEvent event) {
        if (event.getEntity().getWorld().equals(world)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Seated t = tableOf(event.getPlayer().getUniqueId());
        if (t != null) {
            t.leave(event.getPlayer(), null);
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player p = event.getPlayer();
        if (event.getFrom().equals(world)) {
            Seated t = tableOf(p.getUniqueId());
            if (t != null) {
                t.leave(p, "tu as quitté la salle");
            }
        } else if (p.getWorld().equals(world)) {
            p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 20, 0.3, 0.6, 0.3, 0.02);
        }
    }

    /** /table … depuis les boutons du chat. */
    public void tableCommand(Player p, String action, String arg) {
        Seated t = tableOf(p.getUniqueId());
        if (t == null) {
            Txt.send(p, "<#E0484A>Tu n'es assis à aucune table. <#9AA4B2>Clic droit sur une table du casino.");
            return;
        }
        t.action(p, action, arg);
    }

    /** Petit utilitaire : boutons en bois/pierre (tags Bukkit). */
    private static final class Tag {
        static boolean isButton(Material m) {
            return org.bukkit.Tag.BUTTONS.isTagged(m);
        }
    }
}
