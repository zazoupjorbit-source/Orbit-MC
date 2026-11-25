package fr.orbit.chasseurs;

import fr.orbit.chasseurs.Catalogue.Level;
import fr.orbit.chasseurs.Catalogue.Mode;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
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
 * CHASSEURS D'ORBITE : combat de vaisseaux en apesanteur, jeu de la salle d'arcade cachée du lobby (famille E du
 * contrat docs/arcade-jeux.md). Monde vide « orbite », PNJ dans la salle (monde du casino), pack servi sur 8799.
 */
public final class OrbitChasseurs extends JavaPlugin implements Listener {

    private Db db;
    private Profiles profiles;
    private Pack pack;
    private Games games;
    private Menu menu;
    private Ship.Tuning tuning;
    private NamespacedKey mark;
    private World world;
    private Villager npc;
    private TextDisplay npcLabel;
    private final Map<String, ItemStack> models = new HashMap<>();
    private final Map<UUID, Long> lastClick = new HashMap<>();

    Db db() {
        return db;
    }

    Profiles profiles() {
        return profiles;
    }

    Pack pack() {
        return pack;
    }

    Games games() {
        return games;
    }

    Ship.Tuning tuning() {
        return tuning;
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        mark = new NamespacedKey(this, "jeu");
        tuning = new Ship.Tuning(getConfig());
        db = new Db(this);
        db.connect(Db.locate(this, getConfig().getString("base.fichier", "auto")));
        profiles = new Profiles(this);
        pack = new Pack(this);
        pack.enable();
        getServer().getPluginManager().registerEvents(pack, this);

        String name = getConfig().getString("monde", "orbite");
        world = Bukkit.getWorld(name);
        if (world == null) {
            world = new WorldCreator(name).generator(new VoidGen()).generateStructures(false).createWorld();
        }
        if (world == null) {
            getLogger().severe("Monde « " + name + " » impossible à créer : jeu désactivé.");
            return;
        }
        world.setTime(18000);
        world.setGameRule(org.bukkit.GameRules.ADVANCE_TIME, false);
        world.setGameRule(org.bukkit.GameRules.ADVANCE_WEATHER, false);
        world.setGameRule(org.bukkit.GameRules.SPAWN_MOBS, false);
        world.setDifficulty(Difficulty.PEACEFUL);
        world.setStorm(false);
        for (Entity e : world.getEntities()) {
            if (e.getPersistentDataContainer().has(mark, PersistentDataType.BYTE)) {
                e.remove();
            }
        }
        games = new Games(this, world);
        menu = new Menu(this);
        getServer().getPluginManager().registerEvents(games, this);
        getServer().getPluginManager().registerEvents(menu, this);
        getServer().getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, games::tick, 1L, 1L);
        Bukkit.getScheduler().runTaskLater(this, () -> games.build(false), 20L);
        // le PNJ se pose quand la salle d'arcade existe (OrbitCasino crée son monde un tick après son démarrage) ; il est reposé s'il disparaît
        Bukkit.getScheduler().runTaskTimer(this, this::keepNpc, 40L, 100L);
        getLogger().info("CHASSEURS D'ORBITE prêt : monde « " + name + " », 3 arènes, 5 vaisseaux à débloquer en jouant.");
    }

    @Override
    public void onDisable() {
        if (games != null) {
            games.stopAll();
        }
        if (npc != null) {
            npc.remove();
        }
        if (npcLabel != null) {
            npcLabel.remove();
        }
        if (pack != null) {
            pack.shutdown();
        }
        if (db != null) {
            db.close();
        }
    }

    // ------------------------------------------------------------------ outils communs

    /** Marque une entité du jeu : jamais enregistrée dans le monde, retirée au démarrage s'il en reste. */
    <T extends Entity> T mark(T e) {
        e.setPersistent(false);
        e.getPersistentDataContainer().set(mark, PersistentDataType.BYTE, (byte) 1);
        return e;
    }

    /** Objet portant un modèle du pack, teinté par la couleur du pilote. */
    ItemStack model(String id, int rgb) {
        return models.computeIfAbsent(id + ":" + rgb, k -> {
            ItemStack it = new ItemStack(Material.LEATHER_HORSE_ARMOR);
            ItemMeta meta = it.getItemMeta();
            meta.setItemModel(new NamespacedKey("orbitchasseurs", id));
            if (meta instanceof LeatherArmorMeta leather) {
                leather.setColor(Color.fromRGB(rgb & 0xFFFFFF));
            }
            it.setItemMeta(meta);
            return it;
        }).clone();
    }

    private Location point(String key, double dx, double dy, double dz, float yaw) {
        World room = Bukkit.getWorld(getConfig().getString("monde-salle", "casino"));
        if (room == null) {
            return null;
        }
        return new Location(room, getConfig().getDouble(key + ".x", dx), getConfig().getDouble(key + ".y", dy), getConfig().getDouble(key + ".z", dz),
                (float) getConfig().getDouble(key + ".yaw", yaw), 0f);
    }

    /** Devant le PNJ, face à lui. Null si la salle d'arcade n'existe pas (OrbitCasino absent). */
    Location returnPoint() {
        return point("retour", -12.5, 64, 392.5, 180);
    }

    // ------------------------------------------------------------------ PNJ

    private void keepNpc() {
        Location at = point("pnj", -12.5, 64, 389.5, 0);
        if (at == null || !at.getWorld().isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) {
            return;
        }
        if (npc != null && npc.isValid() && npcLabel != null && npcLabel.isValid()) {
            return;
        }
        if (npc != null) {
            npc.remove();
        }
        if (npcLabel != null) {
            npcLabel.remove();
        }
        for (Entity e : at.getWorld().getNearbyEntities(at, 3, 4, 3)) {
            if (e.getPersistentDataContainer().has(mark, PersistentDataType.BYTE)) {
                e.remove();
            }
        }
        npc = at.getWorld().spawn(at, Villager.class, CreatureSpawnEvent.SpawnReason.CUSTOM, e -> {
            mark(e);
            e.setAI(false);
            e.setSilent(true);
            e.setInvulnerable(true);
            e.setCollidable(false);
            e.setRemoveWhenFarAway(false);
            e.customName(Txt.mm("<#62CFF4>Cheffe d'escadrille · CHASSEURS D'ORBITE"));
            e.setCustomNameVisible(true);
            e.setProfession(Villager.Profession.WEAPONSMITH);
            e.setVillagerLevel(5);
            e.setRotation(at.getYaw(), 0f);
        });
        npcLabel = at.getWorld().spawn(at.clone().add(0, 2.9, 0), TextDisplay.class, e -> {
            mark(e);
            e.text(Txt.mm(Txt.TITLE + "\n<#E6EDF3>Combat de vaisseaux en apesanteur\n<#9AA4B2>Mêlée · Duo 2v2 · Convoi\n<#9AA4B2>Clic droit pour décoller"));
            e.setBillboard(Display.Billboard.CENTER);
            e.setAlignment(TextDisplay.TextAlignment.CENTER);
            e.setShadowed(true);
            e.setBackgroundColor(Color.fromARGB(150, 10, 12, 18));
            e.setBrightness(new Display.Brightness(15, 15));
            e.setLineWidth(240);
            e.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.9f, 0.9f, 0.9f), new AxisAngle4f()));
        });
        getLogger().info("PNJ de CHASSEURS D'ORBITE posé dans la salle d'arcade (" + at.getX() + " " + at.getY() + " " + at.getZ() + ").");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onNpc(PlayerInteractEntityEvent event) {
        if (npc == null || !event.getRightClicked().getUniqueId().equals(npc.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        Player p = event.getPlayer();
        long now = System.currentTimeMillis();
        Long last = lastClick.put(p.getUniqueId(), now);
        if (event.getHand() != EquipmentSlot.HAND || (last != null && now - last < 400)) {
            return;
        }
        menu.open(p);
    }

    // ------------------------------------------------------------------ commandes

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (games == null) {
            Txt.send(sender, "<#E0484A>Jeu désactivé (monde introuvable).");
            return true;
        }
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        boolean admin = sender.hasPermission("orbitchasseurs.admin");
        switch (sub) {
            case "top", "classement" -> {
                showTop(sender, args.length > 1 ? Mode.of(args[1]) : null);
                return true;
            }
            case "etat", "état" -> {
                if (admin) {
                    Txt.send(sender, "Parties :" + games.status());
                }
                return true;
            }
            case "generer", "générer" -> {
                if (admin) {
                    games.stopAll();
                    games.build(true);
                    Txt.send(sender, "Arènes en reconstruction (voir la console).");
                }
                return true;
            }
            case "essai" -> {
                if (admin) {
                    Mode mode = args.length > 1 ? Mode.of(args[1]) : Mode.MELEE;
                    int seconds = 60;
                    try {
                        seconds = args.length > 2 ? Math.max(10, Math.min(600, Integer.parseInt(args[2]))) : 60;
                    } catch (NumberFormatException ignored) {
                    }
                    Txt.send(sender, mode == null ? "Modes : melee, duo, convoi." : "Essai de bots seuls : " + games.test(mode, seconds) + ".");
                }
                return true;
            }
            case "pnj" -> {
                if (admin) {
                    if (npc != null) {
                        npc.remove();
                    }
                    keepNpc();
                    Txt.send(sender, npc != null && npc.isValid() ? "PNJ reposé." : "<#E0484A>Salle d'arcade introuvable ou pas chargée.");
                }
                return true;
            }
            default -> {
            }
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Console : /chasseurs etat | generer | pnj | top [mode]");
            return true;
        }
        switch (sub) {
            case "" -> menu.open(p);
            case "jouer", "play" -> {
                Mode mode = args.length > 1 ? Mode.of(args[1]) : Mode.MELEE;
                if (mode == null) {
                    Txt.send(p, "Modes : <#62CFF4>melee</#62CFF4>, <#E0B864>duo</#E0B864>, <#7CFF8A>convoi</#7CFF8A>.");
                    return true;
                }
                Level lv = null;
                if (args.length > 2) {
                    try {
                        lv = Level.of(Integer.parseInt(args[2]));
                    } catch (NumberFormatException e) {
                        Txt.send(p, "Niveau des bots : un nombre de 1 (Recrue) à 4 (Légende).");
                        return true;
                    }
                }
                games.play(p, mode, lv);
            }
            case "rejouer" -> games.replay(p);
            case "quitter", "abandon" -> {
                if (games.of(p) == null) {
                    Txt.send(p, "Tu n'es pas en vol.");
                } else {
                    games.leave(p, true);
                    Txt.send(p, "Retour à la salle d'arcade.");
                }
            }
            case "lancer", "go" -> {
                Match m = games.of(p);
                if (m != null) {
                    m.hurry();
                }
            }
            case "hangar" -> {
                if (games.of(p) != null) {
                    Txt.send(p, "Le hangar s'ouvre depuis la salle, pas en vol.");
                } else {
                    menu.openHangar(p);
                }
            }
            case "stats" -> profiles.load(p, pr -> Txt.send(p, "Pilote niveau <b>" + pr.level() + "</b> · " + Txt.n(pr.xp) + " XP · " + pr.games + " parties · "
                    + pr.wins + " victoires · " + pr.kills + " éliminations · meilleure vague " + pr.bestWave));
            case "tp" -> {
                if (admin && args.length > 1 && Mode.of(args[1]) != null) {
                    Arena a = games.arena(Mode.of(args[1]));
                    p.teleport(new Location(world, a.spawns.get(0).x, a.spawns.get(0).y, a.spawns.get(0).z));
                    p.setAllowFlight(true);
                    p.setFlying(true);
                }
            }
            default -> Txt.send(p, "/chasseurs <#9AA4B2>[jouer <melee|duo|convoi> [1-4] · rejouer · quitter · hangar · top · stats]");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : new String[]{"jouer", "rejouer", "quitter", "hangar", "top", "stats", "lancer"}) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(s);
                }
            }
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("jouer") || args[0].equalsIgnoreCase("top"))) {
            for (Mode m : Mode.values()) {
                if (m.id.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add(m.id);
                }
            }
        }
        return out;
    }

    void showTop(CommandSender to, Mode only) {
        Mode[] modes = only != null ? new Mode[]{only} : Mode.values();
        showTop(to, modes, 0);
    }

    private void showTop(CommandSender to, Mode[] modes, int i) {
        if (i >= modes.length) {
            return;
        }
        Mode m = modes[i];
        profiles.top(m, list -> {
            to.sendMessage(Txt.mm(Txt.TITLE + " <#5A6270>·</#5A6270> <" + m.color + "><b>" + m.label + "</b></" + m.color + ">"));
            if (list.isEmpty()) {
                to.sendMessage(Txt.mm("  <#9AA4B2>Personne encore… à toi l'honneur !"));
            }
            int n = 1;
            for (String[] row : list) {
                to.sendMessage(Txt.mm("  <#5A6270>" + n++ + ".</#5A6270> <#E6EDF3>" + row[0] + "</#E6EDF3> <#E0B864>" + Txt.n(Long.parseLong(row[1]))
                        + " pts</#E0B864> <#5A6270>(" + row[2] + " parties)"));
            }
            showTop(to, modes, i + 1);
        });
    }
}
