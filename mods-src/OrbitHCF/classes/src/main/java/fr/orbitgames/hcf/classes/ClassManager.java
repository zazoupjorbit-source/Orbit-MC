package fr.orbitgames.hcf.classes;

import fr.orbitgames.hcf.api.OrbitHCF;
import fr.orbitgames.hcf.api.TimerKeys;
import fr.orbitgames.hcf.api.event.PlayerClassActivateEvent;
import fr.orbitgames.hcf.api.event.PlayerClassDeactivateEvent;
import fr.orbitgames.hcf.api.model.HcfClass;
import fr.orbitgames.hcf.api.model.Relation;
import fr.orbitgames.hcf.api.service.ClassesService;
import fr.orbitgames.hcf.api.service.CombatService;
import fr.orbitgames.hcf.api.service.FactionService;
import fr.orbitgames.hcf.classes.util.EffectParser;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Detection des classes par armure, effets passifs, invisibilite du Miner.
 */
public class ClassManager implements ClassesService {

    /** Reglages d'une classe issus de classes.yml. */
    public static class ClassSettings {
        final boolean enabled;
        final String armorPrefix;
        final List<EffectParser.ParsedEffect> passives;
        final Set<Material> blockedItems;

        ClassSettings(boolean enabled, String armorPrefix,
                      List<EffectParser.ParsedEffect> passives, Set<Material> blockedItems) {
            this.enabled = enabled;
            this.armorPrefix = armorPrefix;
            this.passives = passives;
            this.blockedItems = blockedItems;
        }

        public Set<Material> blockedItems() {
            return blockedItems;
        }
    }

    private final OrbitHCFClassesPlugin plugin;
    private final Map<HcfClass, ClassSettings> settings = new EnumMap<>(HcfClass.class);
    private final Map<UUID, HcfClass> activeClasses = new ConcurrentHashMap<>();
    /** Joueurs dont l'invisibilite Miner est geree par le plugin. */
    private final Set<UUID> managedInvisibility = ConcurrentHashMap.newKeySet();

    private BukkitTask passiveTask;
    private BukkitTask minerTask;

    public ClassManager(OrbitHCFClassesPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadConfig() {
        settings.clear();
        for (HcfClass hcfClass : HcfClass.values()) {
            String path = "classes." + hcfClass.name().toLowerCase(Locale.ROOT);
            boolean enabled = plugin.getClassesConfig().getBoolean(path + ".enabled", true);
            String armor = plugin.getClassesConfig().getString(path + ".armor", defaultArmor(hcfClass));
            List<EffectParser.ParsedEffect> passives =
                    EffectParser.parseAll(plugin.getClassesConfig().getStringList(path + ".passives"));
            Set<Material> blocked = new HashSet<>();
            for (String name : plugin.getClassesConfig().getStringList(path + ".restrictions.blocked-items")) {
                Material material = Material.matchMaterial(name);
                if (material != null) {
                    blocked.add(material);
                }
            }
            settings.put(hcfClass, new ClassSettings(enabled, armor.toUpperCase(Locale.ROOT), passives, blocked));
        }
    }

    private String defaultArmor(HcfClass hcfClass) {
        return switch (hcfClass) {
            case DIAMOND -> "DIAMOND";
            case BARD -> "GOLDEN";
            case ARCHER -> "LEATHER";
            case ROGUE -> "CHAINMAIL";
            case MINER -> "IRON";
        };
    }

    public void start() {
        // Rafraichissement des passifs toutes les 3 secondes
        passiveTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshPassives, 40L, 60L);
        // Invisibilite du Miner toutes les 2 secondes
        minerTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickMiners, 50L, 40L);
    }

    public void stop() {
        if (passiveTask != null) {
            passiveTask.cancel();
        }
        if (minerTask != null) {
            minerTask.cancel();
        }
        for (UUID uuid : new HashSet<>(activeClasses.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                deactivate(player, false);
            }
        }
    }

    public ClassSettings getSettings(HcfClass hcfClass) {
        return settings.get(hcfClass);
    }

    // ------------------------------------------------------------------
    // Detection
    // ------------------------------------------------------------------

    /** Reevalue la classe du joueur en fonction de son armure. */
    public void recheck(Player player) {
        HcfClass detected = detect(player);
        HcfClass current = activeClasses.get(player.getUniqueId());
        if (detected == current) {
            return;
        }
        if (current != null) {
            deactivate(player, true);
        }
        if (detected != null) {
            activate(player, detected);
        }
    }

    private HcfClass detect(Player player) {
        ItemStack helmet = player.getInventory().getHelmet();
        ItemStack chestplate = player.getInventory().getChestplate();
        ItemStack leggings = player.getInventory().getLeggings();
        ItemStack boots = player.getInventory().getBoots();
        if (helmet == null || chestplate == null || leggings == null || boots == null) {
            return null;
        }
        for (Map.Entry<HcfClass, ClassSettings> entry : settings.entrySet()) {
            ClassSettings s = entry.getValue();
            if (!s.enabled) {
                continue;
            }
            if (matches(helmet, s.armorPrefix, "_HELMET")
                    && matches(chestplate, s.armorPrefix, "_CHESTPLATE")
                    && matches(leggings, s.armorPrefix, "_LEGGINGS")
                    && matches(boots, s.armorPrefix, "_BOOTS")) {
                return entry.getKey();
            }
        }
        return null;
    }

    private boolean matches(ItemStack item, String prefix, String suffix) {
        return item.getType().name().equals(prefix + suffix);
    }

    private void activate(Player player, HcfClass hcfClass) {
        PlayerClassActivateEvent event = new PlayerClassActivateEvent(player, hcfClass);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }
        activeClasses.put(player.getUniqueId(), hcfClass);
        applyPassives(player, hcfClass);
        plugin.getMessages().send(player, "class.activated", "classe", hcfClass.getDisplayName());
    }

    public void deactivate(Player player, boolean notify) {
        HcfClass hcfClass = activeClasses.remove(player.getUniqueId());
        if (hcfClass == null) {
            return;
        }
        removePassives(player, hcfClass);
        removeManagedInvisibility(player);
        plugin.getBardManager().onClassLost(player.getUniqueId());
        Bukkit.getPluginManager().callEvent(new PlayerClassDeactivateEvent(player, hcfClass));
        if (notify) {
            plugin.getMessages().send(player, "class.deactivated", "classe", hcfClass.getDisplayName());
        }
    }

    public void handleQuit(Player player) {
        deactivate(player, false);
    }

    // ------------------------------------------------------------------
    // Passifs
    // ------------------------------------------------------------------

    private void refreshPassives(){
        for (Map.Entry<UUID, HcfClass> entry : activeClasses.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline()) {
                applyPassives(player, entry.getValue());
            }
        }
    }

    private void applyPassives(Player player, HcfClass hcfClass) {
        ClassSettings s = settings.get(hcfClass);
        if (s == null) {
            return;
        }
        for (EffectParser.ParsedEffect passive : s.passives) {
            // Duree superieure a l'intervalle de rafraichissement pour eviter le clignotement
            player.addPotionEffect(passive.toEffect(20 * 6, true));
        }
    }

    private void removePassives(Player player, HcfClass hcfClass) {
        ClassSettings s = settings.get(hcfClass);
        if (s == null) {
            return;
        }
        for (EffectParser.ParsedEffect passive : s.passives) {
            player.removePotionEffect(passive.type());
        }
    }

    // ------------------------------------------------------------------
    // Miner : invisibilite conditionnelle
    // ------------------------------------------------------------------

    private void tickMiners() {
        if (!plugin.getClassesConfig().getBoolean("classes.miner.invisibility.enabled", true)) {
            return;
        }
        int belowY = plugin.getClassesConfig().getInt("classes.miner.invisibility.below-y", 30);
        double radius = plugin.getClassesConfig().getDouble("classes.miner.invisibility.enemy-check-radius", 24.0D);
        boolean revealedInCombat = plugin.getClassesConfig()
                .getBoolean("classes.miner.invisibility.revealed-in-combat", true);

        for (Map.Entry<UUID, HcfClass> entry : activeClasses.entrySet()) {
            if (entry.getValue() != HcfClass.MINER) {
                continue;
            }
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                continue;
            }
            boolean shouldBeInvisible = player.getLocation().getY() < belowY
                    && !(revealedInCombat && isCombatTagged(player.getUniqueId()))
                    && !hasNearbyEnemy(player, radius);
            boolean managed = managedInvisibility.contains(player.getUniqueId());
            if (shouldBeInvisible) {
                player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        PotionEffectType.INVISIBILITY, 20 * 5, 0, true, false, false));
                if (!managed) {
                    managedInvisibility.add(player.getUniqueId());
                    plugin.getMessages().send(player, "miner.invisible");
                }
            } else if (managed) {
                removeManagedInvisibility(player);
                plugin.getMessages().send(player, "miner.revealed");
            }
        }
    }

    private void removeManagedInvisibility(Player player) {
        if (managedInvisibility.remove(player.getUniqueId())) {
            player.removePotionEffect(PotionEffectType.INVISIBILITY);
        }
    }

    private boolean isCombatTagged(UUID uuid) {
        CombatService combat = OrbitHCF.combat();
        return combat != null && combat.isCombatTagged(uuid);
    }

    private boolean hasNearbyEnemy(Player player, double radius) {
        FactionService factions = OrbitHCF.factions();
        for (Player other : player.getWorld().getPlayers()) {
            if (other.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            if (other.getLocation().distanceSquared(player.getLocation()) > radius * radius) {
                continue;
            }
            if (factions == null || !factions.getRelation(player, other).isFriendly()) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Relations d'equipe (utilise par le Bard)
    // ------------------------------------------------------------------

    public boolean isTeammate(Player source, Player target, boolean includeAllies) {
        FactionService factions = OrbitHCF.factions();
        if (factions == null) {
            return false;
        }
        Relation relation = factions.getRelation(source, target);
        return relation == Relation.MEMBER || (includeAllies && relation == Relation.ALLY);
    }

    // ------------------------------------------------------------------
    // ClassesService
    // ------------------------------------------------------------------

    @Override
    public Optional<HcfClass> getActiveClass(UUID playerId) {
        return Optional.ofNullable(activeClasses.get(playerId));
    }

    @Override
    public double getBardEnergy(UUID playerId) {
        return plugin.getBardManager().getEnergy(playerId);
    }

    @Override
    public double getMaxBardEnergy() {
        return plugin.getBardManager().getMaxEnergy();
    }

    @Override
    public boolean isArcherTagged(UUID playerId) {
        return OrbitHCF.timers().isActive(playerId, TimerKeys.ARCHER_TAG);
    }

    @Override
    public Duration getArcherTagRemaining(UUID playerId) {
        return OrbitHCF.timers().getRemaining(playerId, TimerKeys.ARCHER_TAG);
    }

    @Override
    public double getArcherTagDamageMultiplier() {
        return plugin.getClassesConfig().getDouble("classes.archer.tag.damage-multiplier", 1.25D);
    }
}
