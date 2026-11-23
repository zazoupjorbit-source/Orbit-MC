package fr.orbitgames.hcf.classes;

import fr.orbitgames.hcf.api.OrbitHCF;
import fr.orbitgames.hcf.api.TimerKeys;
import fr.orbitgames.hcf.api.model.HcfClass;
import fr.orbitgames.hcf.api.util.DurationFormat;
import fr.orbitgames.hcf.classes.util.EffectParser;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bard : energie, auras passives (objet tenu en main) et capacites actives.
 * Les effets ne touchent que les membres autorises de la faction du Bard
 * (et les allies si configure).
 */
public class BardManager {

    /** Reglage d'un objet du Bard. */
    public record BardItem(Material material, EffectParser.ParsedEffect aura,
                           EffectParser.ParsedEffect active, int activeDurationSeconds,
                           double energyCost, boolean offensive) {
    }

    private final OrbitHCFClassesPlugin plugin;
    private final Map<Material, BardItem> items = new HashMap<>();
    private final Map<UUID, Double> energy = new ConcurrentHashMap<>();

    private double maxEnergy = 100.0D;
    private double regenPerSecond = 1.0D;
    private double range = 20.0D;
    private boolean affectAllies;
    private long globalCooldownSeconds = 10L;

    private BukkitTask energyTask;
    private BukkitTask auraTask;

    public BardManager(OrbitHCFClassesPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadConfig() {
        items.clear();
        maxEnergy = plugin.getClassesConfig().getDouble("classes.bard.energy.max", 100.0D);
        regenPerSecond = plugin.getClassesConfig().getDouble("classes.bard.energy.regen-per-second", 1.0D);
        range = plugin.getClassesConfig().getDouble("classes.bard.range", 20.0D);
        affectAllies = plugin.getClassesConfig().getBoolean("classes.bard.affect-allies", false);
        globalCooldownSeconds = plugin.getClassesConfig().getLong("classes.bard.global-cooldown-seconds", 10L);

        ConfigurationSection section = plugin.getClassesConfig().getConfigurationSection("classes.bard.items");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            Material material = Material.matchMaterial(key.toUpperCase(Locale.ROOT));
            if (material == null) {
                plugin.getLogger().warning("Objet Bard inconnu dans classes.yml : " + key);
                continue;
            }
            EffectParser.ParsedEffect aura = EffectParser.parse(section.getString(key + ".aura"));
            EffectParser.ParsedEffect active = EffectParser.parse(section.getString(key + ".active"));
            items.put(material, new BardItem(material, aura, active,
                    section.getInt(key + ".active-duration-seconds", 8),
                    section.getDouble(key + ".energy-cost", 30.0D),
                    section.getBoolean(key + ".offensive", false)));
        }
    }

    public void start() {
        long auraInterval = 20L * Math.max(1L,
                plugin.getClassesConfig().getLong("classes.bard.aura-interval-seconds", 2L));
        energyTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickEnergy, 20L, 20L);
        auraTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAuras, auraInterval, auraInterval);
    }

    public void stop() {
        if (energyTask != null) {
            energyTask.cancel();
        }
        if (auraTask != null) {
            auraTask.cancel();
        }
        energy.clear();
    }

    public double getEnergy(UUID playerId) {
        return energy.getOrDefault(playerId, 0.0D);
    }

    public double getMaxEnergy() {
        return maxEnergy;
    }

    public void onClassLost(UUID playerId) {
        energy.remove(playerId);
    }

    public BardItem getItem(Material material) {
        return items.get(material);
    }

    private boolean isBard(Player player) {
        return plugin.getClassManager().getActiveClass(player.getUniqueId())
                .filter(c -> c == HcfClass.BARD).isPresent();
    }

    // ------------------------------------------------------------------
    // Taches
    // ------------------------------------------------------------------

    private void tickEnergy() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isBard(player)) {
                energy.merge(player.getUniqueId(), regenPerSecond,
                        (current, regen) -> Math.min(maxEnergy, current + regen));
            }
        }
    }

    private void tickAuras() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isBard(player)) {
                continue;
            }
            BardItem held = items.get(player.getInventory().getItemInMainHand().getType());
            if (held == null || held.aura() == null || held.offensive()) {
                continue;
            }
            // Aura courte, rafraichie a chaque tick d'aura
            applyToTeam(player, held.aura(), 20 * 5, true);
        }
    }

    // ------------------------------------------------------------------
    // Capacite active
    // ------------------------------------------------------------------

    /** Declenche la capacite active de l'objet tenu. Retourne vrai si consommee. */
    public boolean useActive(Player bard) {
        BardItem item = items.get(bard.getInventory().getItemInMainHand().getType());
        if (item == null || item.active() == null || !isBard(bard)) {
            return false;
        }
        Duration cooldown = OrbitHCF.timers().getRemaining(bard.getUniqueId(), TimerKeys.BARD_ABILITY);
        if (!cooldown.isZero()) {
            plugin.getMessages().send(bard, "bard.cooldown", "temps", DurationFormat.compact(cooldown));
            return true;
        }
        double current = getEnergy(bard.getUniqueId());
        if (current < item.energyCost()) {
            plugin.getMessages().send(bard, "bard.no-energy",
                    "cout", (int) item.energyCost(), "energie", (int) current);
            return true;
        }

        energy.put(bard.getUniqueId(), current - item.energyCost());
        OrbitHCF.timers().setTimer(bard.getUniqueId(), TimerKeys.BARD_ABILITY,
                Duration.ofSeconds(globalCooldownSeconds));

        // Consomme un exemplaire de l'objet
        var hand = bard.getInventory().getItemInMainHand();
        hand.setAmount(hand.getAmount() - 1);

        int durationTicks = 20 * item.activeDurationSeconds();
        String effectName = item.active().type().getKey().getKey();
        if (item.offensive()) {
            applyToEnemies(bard, item.active(), durationTicks);
            plugin.getMessages().send(bard, "bard.used-offensive",
                    "effet", effectName, "cout", (int) item.energyCost());
        } else {
            applyToTeam(bard, item.active(), durationTicks, false);
            plugin.getMessages().send(bard, "bard.used",
                    "effet", effectName, "cout", (int) item.energyCost());
        }
        return true;
    }

    private void applyToTeam(Player bard, EffectParser.ParsedEffect effect, int durationTicks, boolean ambient) {
        bard.addPotionEffect(effect.toEffect(durationTicks, ambient));
        for (Player other : bard.getWorld().getPlayers()) {
            if (other.getUniqueId().equals(bard.getUniqueId())) {
                continue;
            }
            if (other.getLocation().distanceSquared(bard.getLocation()) > range * range) {
                continue;
            }
            if (plugin.getClassManager().isTeammate(bard, other, affectAllies)) {
                other.addPotionEffect(effect.toEffect(durationTicks, ambient));
            }
        }
    }

    private void applyToEnemies(Player bard, EffectParser.ParsedEffect effect, int durationTicks) {
        for (Player other : bard.getWorld().getPlayers()) {
            if (other.getUniqueId().equals(bard.getUniqueId())) {
                continue;
            }
            if (other.getLocation().distanceSquared(bard.getLocation()) > range * range) {
                continue;
            }
            if (!plugin.getClassManager().isTeammate(bard, other, true)) {
                other.addPotionEffect(effect.toEffect(durationTicks, false));
            }
        }
    }
}
