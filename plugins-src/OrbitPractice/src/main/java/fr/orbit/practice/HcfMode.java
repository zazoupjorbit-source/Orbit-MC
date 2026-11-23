package fr.orbit.practice;

import fr.orbitgames.hcf.api.HcfWorlds;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Le mode PvP HCF du serveur (factions, claims, DTR, KOTH : plugins OrbitHCF), dans son propre monde.
 * <p>
 * Le Practice garde le hub et les arènes ; OrbitHCF ne s'applique qu'au monde {@code hcf.monde}
 * (réglage {@code mondes-hcf} d'OrbitHCF-API). Chaque mode a son inventaire : en sortant du HCF, l'état
 * du joueur (objets, vie, faim, XP, effets, position) est enregistré dans {@code hcf-joueurs/} et rendu
 * à son retour. Tout passage d'un monde à l'autre passe par là, même un /tp d'admin ou /spawn.
 */
public final class HcfMode implements Listener {

    private final OrbitPractice plugin;
    private final String worldName;
    private final File folder;
    /** Joueurs morts dans le monde HCF, en attente de réapparition. */
    private final Set<UUID> diedInHcf = new HashSet<>();

    public HcfMode(OrbitPractice plugin) {
        this.plugin = plugin;
        this.worldName = plugin.getConfig().getString("hcf.monde", "hcf");
        this.folder = new File(plugin.getDataFolder(), "hcf-joueurs");
    }

    /** Charge (ou crée) le monde HCF et branche la sortie forcée d'OrbitHCF (deathban, /logout). */
    public boolean enable() {
        World world = world();
        if (world == null) {
            plugin.getLogger().severe("Monde HCF « " + worldName + " » impossible à créer : mode HCF coupé.");
            return false;
        }
        HcfZone.set(worldName);
        // La Safezone d'OrbitHCF est centrée sur 0,0 : le point d'arrivée doit y être.
        Location s = world.getSpawnLocation();
        if (Math.abs(s.getBlockX()) > 32 || Math.abs(s.getBlockZ()) > 32) {
            int y = world.getHighestBlockYAt(0, 0) + 1;
            world.setSpawnLocation(0, y, 0);
            plugin.getLogger().info("Spawn du monde HCF déplacé en 0 " + y + " 0 (Safezone).");
        }
        if (!HcfWorlds.isHcf(world) || !HcfWorlds.isScoped()) {
            plugin.getLogger().warning("OrbitHCF-API ne limite pas le HCF au monde « " + worldName
                    + " » : mets « mondes-hcf: [" + worldName + "] » dans plugins/OrbitHCF-API/config.yml.");
        }
        HcfWorlds.setEjector(this::eject);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("Mode HCF branché sur le monde « " + worldName + " ».");
        return true;
    }

    public void disable() {
        HcfWorlds.setEjector(null);
    }

    public World world() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            world = new WorldCreator(worldName).environment(World.Environment.NORMAL).createWorld();
        }
        return world;
    }

    private Location spawn() {
        Location s = world().getSpawnLocation();
        return new Location(s.getWorld(), s.getBlockX() + 0.5, s.getY(), s.getBlockZ() + 0.5, s.getYaw(), 0f);
    }

    // ------------------------------------------------------------------ Entrée / sortie

    /** /hcf : du hub vers le monde HCF, à l'endroit où le joueur l'avait quitté. */
    public void enter(Player p) {
        if (HcfZone.is(p)) {
            p.sendMessage(Msg.msg("Tu es déjà en HCF. <white>/hub<gray> pour revenir au Practice."));
            return;
        }
        if (plugin.matches().isBusy(p.getUniqueId())) {
            p.sendMessage(Msg.msg("<red>Termine d'abord ton combat (ou /quitter le mode spectateur)."));
            return;
        }
        String deny = HcfWorlds.denyReason(p.getUniqueId());
        if (deny != null) {
            p.sendMessage(Msg.msg("<red>" + deny));
            return;
        }
        plugin.queue().purge(p);
        Location target = savedLocation(p.getUniqueId());
        if (!p.teleport(target != null ? target : spawn())) {
            p.sendMessage(Msg.msg("<red>Entrée en HCF refusée."));
        }
    }

    /** /hub : du HCF vers le hub du Practice (refusé en combat par OrbitHCF-API). */
    public void leave(Player p) {
        if (!HcfZone.is(p)) {
            if (!plugin.matches().isBusy(p.getUniqueId())) {
                plugin.lobby().send(p);
            }
            return;
        }
        p.teleport(plugin.lobby().hub());
    }

    /** Sortie forcée par OrbitHCF : deathban à la mort, ou /logout terminé. */
    private void eject(Player p, String message) {
        if (p.isDead()) {
            p.spigot().respawn();
        }
        if (HcfZone.is(p)) {
            p.teleport(plugin.lobby().hub());
        }
        if (message != null && !message.isEmpty()) {
            p.sendMessage(message);
        }
    }

    /** Tout changement de monde entre le HCF et le reste échange les inventaires. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null) {
            return;
        }
        boolean from = HcfZone.is(event.getFrom().getWorld());
        boolean to = HcfZone.is(event.getTo().getWorld());
        Player p = event.getPlayer();
        if (from && !to) {
            save(p, event.getFrom());
            // Après la téléportation : état de hub (objets du Practice, mode aventure).
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (p.isOnline() && !HcfZone.is(p) && !plugin.matches().isBusy(p.getUniqueId())) {
                    plugin.lobby().send(p);
                }
            });
        } else if (!from && to) {
            restore(p);
        }
    }

    // ------------------------------------------------------------------ Mort et portails

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        if (HcfZone.is(event.getEntity()) && !event.isCancelled()) {
            diedInHcf.add(event.getEntity().getUniqueId());
        }
    }

    /** Mort en HCF : on réapparaît en HCF (lit si le joueur en a un là-bas, sinon le spawn du monde HCF). */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        if (!diedInHcf.remove(event.getPlayer().getUniqueId())) {
            return;
        }
        if (!HcfZone.is(event.getRespawnLocation().getWorld())) {
            event.setRespawnLocation(spawn());
        }
        event.getPlayer().setGameMode(GameMode.SURVIVAL);
    }

    /** Pas de Nether ni d'End pour le HCF pour l'instant : ils mèneraient hors du mode. */
    @EventHandler(ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        if (HcfZone.is(event.getFrom().getWorld())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Msg.msg("<red>Les portails sont fermés en HCF pour l'instant."));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {
        if (HcfZone.is(event.getFrom().getWorld())) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ Sauvegarde de l'état HCF

    private File file(UUID id) {
        return new File(folder, id + ".yml");
    }

    private void save(Player p, Location at) {
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("pseudo", p.getName());
        yml.set("inventaire", new ArrayList<>(java.util.Arrays.asList(p.getInventory().getContents())));
        yml.set("vie", p.getHealth());
        yml.set("faim", p.getFoodLevel());
        yml.set("saturation", (double) p.getSaturation());
        yml.set("xp.niveau", p.getLevel());
        yml.set("xp.barre", (double) p.getExp());
        yml.set("effets", new ArrayList<>(p.getActivePotionEffects()));
        yml.set("feu", p.getFireTicks());
        if (!p.isDead() && HcfZone.is(at.getWorld())) {
            yml.set("position.x", at.getX());
            yml.set("position.y", at.getY());
            yml.set("position.z", at.getZ());
            yml.set("position.yaw", (double) at.getYaw());
            yml.set("position.pitch", (double) at.getPitch());
        }
        try {
            folder.mkdirs();
            yml.save(file(p.getUniqueId()));
        } catch (IOException e) {
            plugin.getLogger().severe("Inventaire HCF de " + p.getName() + " NON enregistré : " + e.getMessage());
        }
    }

    private Location savedLocation(UUID id) {
        File f = file(id);
        if (!f.exists()) {
            return null;
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
        if (!yml.isSet("position.x")) {
            return null;
        }
        return new Location(world(), yml.getDouble("position.x"), yml.getDouble("position.y"), yml.getDouble("position.z"),
                (float) yml.getDouble("position.yaw"), (float) yml.getDouble("position.pitch"));
    }

    /** Rend l'état HCF enregistré (ou un départ à vide pour une première venue). */
    @SuppressWarnings("unchecked")
    private void restore(Player p) {
        p.getInventory().clear();
        p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType()));
        p.setCooldown(org.bukkit.Material.ENDER_PEARL, 0);
        if (!(p.getGameMode() == GameMode.CREATIVE && p.hasPermission("practice.admin"))) {
            p.setGameMode(GameMode.SURVIVAL);
        }
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = max != null ? max.getValue() : 20.0;
        File f = file(p.getUniqueId());
        if (!f.exists()) {
            p.setHealth(maxHealth);
            p.setFoodLevel(20);
            p.setSaturation(5f);
            p.setLevel(0);
            p.setExp(0f);
            p.sendMessage(Msg.msg("Bienvenue en <white>HCF<gray> : crée ou rejoins une faction avec <white>/f<gray>. "
                    + "<white>/hub<gray> pour revenir au Practice."));
            return;
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
        List<?> items = yml.getList("inventaire", List.of());
        ItemStack[] contents = new ItemStack[p.getInventory().getSize()];
        for (int i = 0; i < Math.min(items.size(), contents.length); i++) {
            if (items.get(i) instanceof ItemStack item) {
                contents[i] = item;
            }
        }
        p.getInventory().setContents(contents);
        p.setHealth(Math.max(1.0, Math.min(maxHealth, yml.getDouble("vie", maxHealth))));
        p.setFoodLevel(yml.getInt("faim", 20));
        p.setSaturation((float) yml.getDouble("saturation", 5.0));
        p.setLevel(yml.getInt("xp.niveau", 0));
        p.setExp((float) yml.getDouble("xp.barre", 0.0));
        p.setFireTicks(yml.getInt("feu", 0));
        for (Object o : yml.getList("effets", List.of())) {
            if (o instanceof PotionEffect effect) {
                p.addPotionEffect(effect);
            }
        }
    }
}
