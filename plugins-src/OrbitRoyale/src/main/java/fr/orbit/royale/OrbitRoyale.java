package fr.orbit.royale;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * OrbitRoyale : SIÈGE ORBITAL, bataille de tours 1v1 à cartes et plasma, lancée depuis un PNJ de l'arcade cachée
 * de La Station (lobby seulement). Voir docs/siege-orbital.md.
 */
public final class OrbitRoyale extends JavaPlugin implements Listener, TabCompleter {

    private Db db;
    private Store store;
    private RoyalePack pack;
    private Game game;
    private World world;
    private NamespacedKey npcKey;
    private UUID npcId;
    private TextDisplay npcText;
    private BukkitTask npcWatch;
    private final Map<UUID, Long> lastClick = new HashMap<>();

    Store store() {
        return store;
    }

    RoyalePack pack() {
        return pack;
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        npcKey = new NamespacedKey(this, "pnj");
        db = new Db(this);
        db.connect(Db.locate(this, getConfig().getString("base.fichier", "auto")));
        store = new Store(this, db);
        store.init();
        pack = new RoyalePack(this);
        getServer().getPluginManager().registerEvents(pack, this);
        pack.enable();
        getServer().getPluginManager().registerEvents(this, this);
        var cmd = getCommand("siege");
        if (cmd != null) {
            cmd.setTabCompleter(this);
        }
        Bukkit.getScheduler().runTask(this, this::setupWorld);
    }

    private void setupWorld() {
        String name = getConfig().getString("monde", "royale");
        world = Bukkit.getWorld(name);
        if (world == null) {
            world = new WorldCreator(name).generator(new VoidGen()).generateStructures(false).createWorld();
        }
        if (world == null) {
            getLogger().severe("Monde « " + name + " » impossible à créer : Siège Orbital coupé.");
            return;
        }
        world.setTime(6000);
        world.setGameRule(org.bukkit.GameRules.ADVANCE_TIME, false);
        world.setGameRule(org.bukkit.GameRules.ADVANCE_WEATHER, false);
        world.setGameRule(org.bukkit.GameRules.SPAWN_MOBS, false);
        world.setStorm(false);
        game = new Game(this, world);
        getServer().getPluginManager().registerEvents(game, this);
        game.start();
        getLogger().info("Siège Orbital prêt : monde « " + name + " », " + getConfig().getInt("arenes", 8) + " arènes.");
        // PNJ dans la salle d'arcade : le monde casino est créé par OrbitCasino un tick après son démarrage
        npcWatch = Bukkit.getScheduler().runTaskTimer(this, this::ensureNpc, 40L, 100L);
    }

    @Override
    public void onDisable() {
        if (game != null) {
            game.stopAll();
        }
        if (npcWatch != null) {
            npcWatch.cancel();
        }
        removeNpc();
        if (pack != null) {
            pack.shutdown();
        }
        if (db != null) {
            db.close();
        }
    }

    // ------------------------------------------------------------------ PNJ

    private Location npcLocation() {
        World w = Bukkit.getWorld(getConfig().getString("pnj.monde", "casino"));
        if (w == null) {
            return null;
        }
        List<Double> pos = getConfig().getDoubleList("pnj.position");
        if (pos.size() < 3) {
            pos = List.of(12.5, 64.0, 389.5, 0.0);
        }
        return new Location(w, pos.get(0), pos.get(1), pos.get(2), pos.size() > 3 ? pos.get(3).floatValue() : 0f, 0f);
    }

    /** Point de retour après une partie : devant le PNJ, tourné vers lui. */
    Location returnPoint() {
        Location l = npcLocation();
        if (l == null) {
            return Bukkit.getWorlds().get(0).getSpawnLocation();
        }
        double yaw = Math.toRadians(l.getYaw());
        Location r = l.clone().add(-Math.sin(yaw) * 2, 0, Math.cos(yaw) * 2);
        r.setYaw(l.getYaw() + 180f);
        r.setPitch(0f);
        return r;
    }

    private void ensureNpc() {
        Location l = npcLocation();
        if (l == null) {
            return;
        }
        Entity e = npcId == null ? null : Bukkit.getEntity(npcId);
        if (e != null && e.isValid() && npcText != null && npcText.isValid()) {
            return;
        }
        removeNpc();
        l.getChunk().addPluginChunkTicket(this);
        for (Entity old : l.getWorld().getNearbyEntities(l, 3, 4, 3)) {
            if (old.getPersistentDataContainer().has(npcKey, PersistentDataType.BYTE)) {
                old.remove();
            }
        }
        Villager v = l.getWorld().spawn(l, Villager.class, CreatureSpawnEvent.SpawnReason.CUSTOM, n -> {
            n.setPersistent(false);
            n.getPersistentDataContainer().set(npcKey, PersistentDataType.BYTE, (byte) 1);
            n.setAI(false);
            n.setSilent(true);
            n.setInvulnerable(true);
            n.setCollidable(false);
            n.setRemoveWhenFarAway(false);
            n.customName(Txt.mm("<#3A7FFF>Commandante · <#E0B864>Siège Orbital"));
            n.setCustomNameVisible(true);
            n.setProfession(Villager.Profession.WEAPONSMITH);
            n.setVillagerType(Villager.Type.SNOW);
            n.setVillagerLevel(5);
            n.setRotation(l.getYaw(), 0f);
        });
        npcId = v.getUniqueId();
        npcText = l.getWorld().spawn(l.clone().add(0, 2.9, 0), TextDisplay.class, t -> {
            t.setPersistent(false);
            t.getPersistentDataContainer().set(npcKey, PersistentDataType.BYTE, (byte) 1);
            t.text(Txt.mm(Txt.TITLE + "\n<#E6EDF3>Bataille de tours 1v1 à cartes et plasma\n<#9AA4B2>Clic droit pour jouer · /siege deck"));
            t.setBillboard(Display.Billboard.CENTER);
            t.setAlignment(TextDisplay.TextAlignment.CENTER);
            t.setShadowed(true);
            t.setBackgroundColor(Color.fromARGB(150, 10, 12, 18));
            t.setBrightness(new Display.Brightness(15, 15));
            t.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.9f, 0.9f, 0.9f), new AxisAngle4f()));
        });
        getLogger().info("PNJ du Siège Orbital posé dans « " + l.getWorld().getName() + " » (" + l.getX() + " " + l.getY() + " " + l.getZ() + ").");
    }

    private void removeNpc() {
        Entity e = npcId == null ? null : Bukkit.getEntity(npcId);
        if (e != null) {
            e.remove();
        }
        if (npcText != null) {
            npcText.remove();
        }
        npcId = null;
        npcText = null;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onNpc(PlayerInteractEntityEvent e) {
        if (npcId == null || !e.getRightClicked().getUniqueId().equals(npcId)) {
            return;
        }
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (e.getHand() != EquipmentSlot.HAND || game == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastClick.put(p.getUniqueId(), now);
        if (last != null && now - last < 800) {
            return;
        }
        if (p.isSneaking()) {
            game.openDeck(p);
        } else {
            game.join(p, false);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onNpcDamage(EntityDamageEvent e) {
        if (npcId != null && e.getEntity().getUniqueId().equals(npcId)) {
            e.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ commande

    private boolean inArcade(Player p) {
        Location n = npcLocation();
        return n != null && p.getWorld().equals(n.getWorld()) && p.getLocation().distanceSquared(n) < 40 * 40;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        Player p = sender instanceof Player pl ? pl : null;
        boolean admin = sender.hasPermission("orbitroyale.admin");
        if (game == null) {
            Txt.send(sender, "<#E0484A>Le Siège Orbital n'est pas prêt sur ce serveur.");
            return true;
        }
        switch (sub) {
            case "jouer", "rejouer", "bot" -> {
                if (p == null) {
                    return true;
                }
                if (game.playing(p)) {
                    Txt.send(p, "<#9AA4B2>Tu es déjà en pleine bataille.");
                } else if (!inArcade(p) && !admin) {
                    Txt.send(p, "<#9AA4B2>Le Siège Orbital se lance depuis la salle d'arcade cachée, auprès de la Commandante.");
                } else {
                    game.join(p, sub.equals("bot"));
                }
            }
            case "deck" -> {
                if (p != null) {
                    game.openDeck(p);
                }
            }
            case "quitter", "abandon" -> {
                if (p == null) {
                    return true;
                }
                if (game.playing(p)) {
                    game.forfeit(p, "abandon");
                } else if (game.leaveQueue(p)) {
                    Txt.send(p, "<#9AA4B2>Tu as quitté la file d'attente.");
                }
            }
            case "top", "classement" -> store.top(list -> {
                Txt.send(sender, Txt.TITLE + " <#9AA4B2>· meilleurs commandants");
                if (list.isEmpty()) {
                    sender.sendMessage(Txt.mm("  <#9AA4B2>Personne encore… à toi l'honneur !"));
                }
                int i = 1;
                for (String[] row : list) {
                    String color = i == 1 ? "#E0B864" : i <= 3 ? "#C9D4E0" : "#9AA4B2";
                    sender.sendMessage(Txt.mm("  <" + color + ">" + i++ + ". " + row[0] + "</" + color + "> <#E6EDF3>" + Txt.n(Long.parseLong(row[1]))
                            + " trophées</#E6EDF3> <#5A6270>· " + row[2] + " victoires"));
                }
            });
            case "cartes" -> {
                Txt.send(sender, Txt.TITLE + " <#9AA4B2>· les 12 cartes");
                for (Card c : Card.values()) {
                    sender.sendMessage(Txt.mm("  <" + c.color() + "><b>" + c.nom + "</b></" + c.color() + "> <#C79BFF>⚡" + c.cost + "</#C79BFF> <#9AA4B2>"
                            + c.typeLabel() + (c.trophies > 0 ? " · à " + c.trophies + " trophées" : " · d'office") + "\n    " + Game.stats(c)));
                }
            }
            case "tp" -> {
                if (admin && p != null) {
                    Location r = returnPoint();
                    p.teleport(r);
                }
            }
            case "pnj" -> {
                if (admin) {
                    removeNpc();
                    ensureNpc();
                    Txt.send(sender, "PNJ reposé.");
                }
            }
            case "demo" -> {
                if (admin) {
                    Txt.send(sender, game.startDemo(args.length > 1 ? parse(args[1]) : 0));
                }
            }
            case "arene" -> {
                if (admin) {
                    int slot = args.length > 1 ? parse(args[1]) : 0;
                    Arena a = game.arena(slot);
                    a.loadChunks(this, true);
                    int n = a.build();
                    a.loadChunks(this, false);
                    Txt.send(sender, "Arène " + a.slot + " reconstruite (" + Txt.n(n) + " blocs).");
                    if (p != null && args.length > 2 && args[2].equalsIgnoreCase("voir")) {
                        p.teleport(a.camera(0));
                    }
                }
            }
            default -> {
                Txt.send(sender, Txt.TITLE + " : <b>/siege jouer</b> (depuis l'arcade) · <b>/siege bot</b> · <b>/siege deck</b> · "
                        + "<b>/siege cartes</b> · <b>/siege top</b> · <b>/siege quitter</b>");
                if (admin) {
                    Txt.send(sender, "<#9AA4B2>Admin : /siege tp · pnj · arene <n> [voir] · demo [trophées]");
                }
            }
        }
        return true;
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> l = new ArrayList<>(List.of("jouer", "bot", "deck", "cartes", "top", "quitter"));
            if (sender.hasPermission("orbitroyale.admin")) {
                l.addAll(List.of("tp", "pnj", "arene", "demo"));
            }
            String pre = args[0].toLowerCase(Locale.ROOT);
            l.removeIf(s -> !s.startsWith(pre));
            return l;
        }
        return List.of();
    }
}
