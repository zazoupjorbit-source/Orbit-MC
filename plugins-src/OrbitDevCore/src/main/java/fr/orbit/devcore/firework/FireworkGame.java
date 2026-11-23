package fr.orbit.devcore.firework;

import fr.orbit.devcore.OrbitDevCore;
import fr.orbit.devcore.Teleports;
import fr.orbit.devcore.message.Messages;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static fr.orbit.devcore.message.Messages.comp;
import static fr.orbit.devcore.message.Messages.ph;

/**
 * Mini-jeu du lobby. Feu d'artifice en main : clic gauche = entrer / sortir du mode feu d'artifice,
 * clic droit = tir en ligne droite vers le viseur. Un tir élimine d'un coup, et seulement un joueur
 * lui aussi en mode feu d'artifice : les autres ne peuvent ni tirer ni être touchés (le tir les traverse).
 * Pas de vrais dégâts : l'élimination est gérée ici (titre, renvoi au spawn, sortie du mode).
 * Enchaîner les éliminations débloque des bonus (voir {@link Bonus}), tous perdus à l'élimination.
 * Pour tester seul : cibles d'entraînement (mannequins) posées par /orbitdev feu, qui comptent comme des
 * joueurs en mode et peuvent riposter.
 */
public final class FireworkGame implements Listener {

    /** Couleurs des joueurs en jeu (traînée et explosion), charte ORBIT d'abord. */
    private static final Color[] COLORS = {
            Color.fromRGB(0x3A7FFF), Color.fromRGB(0xFF4FD8), Color.fromRGB(0xF5C542), Color.fromRGB(0x7CFC9A),
            Color.fromRGB(0xFF4D4D), Color.fromRGB(0x00C6FF), Color.fromRGB(0x9B5CFF), Color.fromRGB(0xFF9A3C)};
    private static final Color ROPE = Color.fromRGB(0xB8C2CC);
    private static final Color DUMMY_COLOR = Color.fromRGB(0xFF4D4D);
    private static final long TOGGLE_DELAY_MS = 400;
    /** Le client envoie parfois un « clic gauche » (mouvement du bras) juste après un clic droit. */
    private static final long USE_GRACE_MS = 250;
    private static final double TRAIL_STEP = 0.3;
    /** Rien n'est dessiné à moins de cette distance du point de départ : la vue du tireur reste dégagée. */
    private static final double TRAIL_SKIP = 0.7;
    /** Distance à laquelle une cible d'entraînement suit du regard (et vise) un joueur en mode. */
    private static final double DUMMY_SIGHT = 48.0;
    private static final Title.Times TITLE_TIMES =
            Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1400), Duration.ofMillis(400));

    private static final Sound ENTER_SOUND = Sound.sound(Key.key("block.beacon.activate"), Sound.Source.PLAYER, 0.8f, 1.6f);
    private static final Sound LEAVE_SOUND = Sound.sound(Key.key("block.beacon.deactivate"), Sound.Source.PLAYER, 0.8f, 1.4f);
    private static final Sound LAUNCH_SOUND = Sound.sound(Key.key("entity.firework_rocket.launch"), Sound.Source.PLAYER, 1.2f, 1.1f);
    private static final Sound FIZZLE_SOUND = Sound.sound(Key.key("entity.firework_rocket.blast"), Sound.Source.PLAYER, 0.5f, 1.7f);
    private static final Sound KILL_SOUND = Sound.sound(Key.key("entity.arrow.hit_player"), Sound.Source.PLAYER, 1f, 1f);
    private static final Sound DEATH_SOUND = Sound.sound(Key.key("entity.player.hurt"), Sound.Source.PLAYER, 1f, 0.8f);
    private static final Sound DENY_SOUND = Sound.sound(Key.key("entity.villager.no"), Sound.Source.PLAYER, 0.6f, 1f);
    private static final Sound BONUS_SOUND = Sound.sound(Key.key("entity.player.levelup"), Sound.Source.PLAYER, 0.8f, 1.3f);
    private static final Sound SHIELD_SOUND = Sound.sound(Key.key("item.shield.break"), Sound.Source.PLAYER, 1f, 1f);
    private static final Sound HOOK_SOUND = Sound.sound(Key.key("entity.fishing_bobber.retrieve"), Sound.Source.PLAYER, 1f, 0.8f);
    private static final Sound BOOST_SOUND = Sound.sound(Key.key("item.trident.riptide_1"), Sound.Source.PLAYER, 0.9f, 1.2f);

    /** Bonus de série (éliminations d'affilée), débloqués une fois par série et perdus à l'élimination. */
    private enum Bonus {
        SPEED("speed", 2),
        GRAPPLE("grapple", 3),
        RAPID_FIRE("rapid-fire", 5),
        SHIELD("shield", 7),
        VOLLEY("volley", 10);

        /** Clé dans firework-game.streak-rewards et dans les messages firework.bonus.<clé>. */
        final String key;
        final int defaultStreak;

        Bonus(String key, int defaultStreak) {
            this.key = key;
            this.defaultStreak = defaultStreak;
        }
    }

    private final OrbitDevCore plugin;
    private final NamespacedKey itemKey;
    private final NamespacedKey grappleKey;
    private final NamespacedKey burstKey;
    private final NamespacedKey glowKey;
    private final NamespacedKey speedKey;
    private final NamespacedKey dummyKey;
    private final Map<UUID, Fighter> fighters = new HashMap<>();
    private final Map<UUID, Integer> kills = new HashMap<>();
    private final Map<UUID, Long> lastToggle = new HashMap<>();
    private final Map<UUID, Long> lastUse = new HashMap<>();
    private final List<Shot> shots = new ArrayList<>();
    private final List<Dummy> dummies = new ArrayList<>();
    private BukkitTask task;
    private long ticks;

    public FireworkGame(OrbitDevCore plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "firework_launcher");
        this.grappleKey = new NamespacedKey(plugin, "firework_grapple");
        this.burstKey = new NamespacedKey(plugin, "firework_burst");
        this.glowKey = new NamespacedKey(plugin, "firework_glow");
        this.speedKey = new NamespacedKey(plugin, "firework_speed");
        this.dummyKey = new NamespacedKey(plugin, "firework_dummy");
    }

    /** Joueur en mode feu d'artifice. */
    private static final class Fighter {
        final Color color;
        final EnumSet<Bonus> bonuses = EnumSet.noneOf(Bonus.class);
        long lastShot;
        int streak;
        /** Jusqu'à ce tick, la barre d'action affiche autre chose que le HUD (élimination, refus). */
        long hudHold;
        /** Jusqu'à ce tick, protégé après une réapparition (les tirs le traversent). */
        long protectedUntil;

        Fighter(Color color) {
            this.color = color;
        }
    }

    /** Projectile en vol : avance en ligne droite, touché calculé par lancer de rayon à chaque tick. */
    private static final class Shot {
        final UUID shooter;
        final Component shooterName;
        final Color color;
        /** Tir d'une cible d'entraînement : ne touche que les joueurs. */
        final boolean fromDummy;
        final World world;
        final Vector dir;
        Location pos;
        double travelled;
        ItemDisplay display;
        int sparks;

        Shot(UUID shooter, Component shooterName, Color color, boolean fromDummy, Location start, Vector dir) {
            this.shooter = shooter;
            this.shooterName = shooterName;
            this.color = color;
            this.fromDummy = fromDummy;
            this.world = start.getWorld();
            this.pos = start;
            this.dir = dir;
        }
    }

    /** Cible d'entraînement : un mannequin à un emplacement fixe, recréé après chaque élimination. */
    private static final class Dummy {
        final Location post;
        final boolean fires;
        Mannequin entity;
        long respawnAt;
        long nextShot;

        Dummy(Location post, boolean fires) {
            this.post = post;
            this.fires = fires;
        }
    }

    // ------------------------------------------------------------------ cycle de vie

    public void start() {
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Shot shot : shots) {
            removeDisplay(shot);
        }
        shots.clear();
        removeDummies();
        for (UUID id : new ArrayList<>(fighters.keySet())) {
            Player player = plugin.getServer().getPlayer(id);
            if (player != null) {
                leave(player, false);
            }
        }
        fighters.clear();
    }

    /** Par défaut, seulement là où la boussole existe (le lobby). */
    public boolean enabled() {
        return plugin.getConfig().getBoolean("firework-game.enabled", plugin.compass().enabled());
    }

    // ------------------------------------------------------------------ objets

    public ItemStack create(boolean armed) {
        ItemStack item = new ItemStack(Material.FIREWORK_ROCKET);
        ItemMeta meta = item.getItemMeta();
        Messages messages = plugin.messages();
        meta.displayName(messages.item(armed ? "firework.item.name-armed" : "firework.item.name"));
        meta.lore(messages.itemLore(armed ? "firework.item.lore-armed" : "firework.item.lore"));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        meta.setEnchantmentGlintOverride(armed);
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createGrapple() {
        ItemStack item = new ItemStack(Material.FISHING_ROD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plugin.messages().item("firework.grapple.name"));
        meta.lore(plugin.messages().itemLore("firework.grapple.lore"));
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(grappleKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isLauncher(ItemStack item) {
        return marked(item, itemKey);
    }

    private boolean isGrapple(ItemStack item) {
        return marked(item, grappleKey);
    }

    private static boolean marked(ItemStack item, NamespacedKey key) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    private boolean has(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isLauncher(item)) {
                return true;
            }
        }
        return isLauncher(player.getItemOnCursor());
    }

    /** Donne le feu d'artifice (case de la config, sinon première case libre) ou remet à jour celui possédé. */
    public void give(Player player) {
        if (has(player)) {
            refresh(player);
            return;
        }
        put(player, create(fighters.containsKey(player.getUniqueId())), plugin.getConfig().getInt("firework-game.slot", 2));
    }

    private void giveGrapple(Player player) {
        removeGrapples(player);
        put(player, createGrapple(), plugin.getConfig().getInt("firework-game.grapple.slot", 3));
    }

    private static void put(Player player, ItemStack item, int slot) {
        PlayerInventory inventory = player.getInventory();
        if (slot >= 0 && slot <= 8) {
            ItemStack current = inventory.getItem(slot);
            if (current == null || current.getType().isAir()) {
                inventory.setItem(slot, item);
                return;
            }
        }
        inventory.addItem(item);
    }

    /** Version de l'objet qui correspond à l'état du joueur (armé = effet enchanté + autre description). */
    private void refresh(Player player) {
        boolean armed = fighters.containsKey(player.getUniqueId());
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isLauncher(contents[i])) {
                inventory.setItem(i, create(armed));
            }
        }
    }

    public void removeAll(Player player) {
        remove(player, itemKey);
    }

    private void removeGrapples(Player player) {
        remove(player, grappleKey);
    }

    private static void remove(Player player, NamespacedKey key) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (marked(contents[i], key)) {
                inventory.setItem(i, null);
            }
        }
        if (marked(player.getItemOnCursor(), key)) {
            player.setItemOnCursor(null);
        }
    }

    // ------------------------------------------------------------------ clics

    // ignoreCancelled = false : un clic droit dans le vide arrive déjà « annulé » par défaut.
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        boolean launcher = isLauncher(event.getItem());
        if (action == Action.PHYSICAL || (!launcher && !isGrapple(event.getItem()))) {
            return;
        }
        // Jamais de vraie fusée, de vraie ligne de pêche, de bloc cassé (créatif) ni de porte ouverte.
        event.setUseItemInHand(Event.Result.DENY);
        event.setUseInteractedBlock(Event.Result.DENY);
        if (event.getHand() != EquipmentSlot.HAND) {
            return; // dans l'autre main : bloqué, sans effet
        }
        Player player = event.getPlayer();
        if (!enabled()) {
            removeAll(player);
            removeGrapples(player);
            plugin.messages().send(player, "firework.lobby-only");
            return;
        }
        boolean left = action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
        if (!launcher) {
            if (!left) {
                grapple(player);
            }
        } else if (left) {
            toggle(player);
        } else {
            shoot(player);
        }
    }

    /** Clic gauche sur un joueur ou une cible : pas de coup, même effet qu'un clic gauche dans le vide. */
    @EventHandler(priority = EventPriority.LOW)
    public void onAttack(PrePlayerAttackEntityEvent event) {
        Player player = event.getPlayer();
        boolean dummy = isDummyEntity(event.getAttacked());
        if (dummy) {
            event.setCancelled(true); // un joueur en créatif tuerait le mannequin d'un coup
        }
        if ((!dummy && !(event.getAttacked() instanceof Player)) || !isLauncher(player.getInventory().getItemInMainHand())) {
            return;
        }
        event.setCancelled(true);
        if (enabled()) {
            toggle(player);
        }
    }

    private void toggle(Player player) {
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        if (now - lastToggle.getOrDefault(id, 0L) < TOGGLE_DELAY_MS || now - lastUse.getOrDefault(id, 0L) < USE_GRACE_MS) {
            return;
        }
        lastToggle.put(id, now);
        Fighter fighter = fighters.get(id);
        if (fighter == null) {
            enter(player);
            return;
        }
        long lockMs = Math.max(0L, plugin.getConfig().getLong("firework-game.leave-lock-seconds", 5L)) * 1000L;
        long left = lockMs - (now - fighter.lastShot);
        if (left > 0) {
            fighter.hudHold = ticks + 30;
            player.sendActionBar(plugin.messages().get("firework.leave-locked", ph("seconds", (left + 999) / 1000)));
            player.playSound(DENY_SOUND);
            return;
        }
        leave(player, true);
    }

    private void enter(Player player) {
        Fighter fighter = new Fighter(freeColor());
        fighters.put(player.getUniqueId(), fighter);
        if (plugin.getConfig().getBoolean("firework-game.glow", true)) {
            player.setGlowing(true);
            player.getPersistentDataContainer().set(glowKey, PersistentDataType.BYTE, (byte) 1);
        }
        refresh(player);
        player.showTitle(Title.title(plugin.messages().get("firework.enter-title"),
                plugin.messages().get("firework.enter-subtitle"), TITLE_TIMES));
        player.playSound(ENTER_SOUND);
        player.sendActionBar(hud(player.getUniqueId(), fighter));
    }

    /** Sortie du mode (volontaire si notify, sinon silencieuse : déconnexion, mort, changement de monde...). */
    private void leave(Player player, boolean notify) {
        if (fighters.remove(player.getUniqueId()) == null) {
            return;
        }
        disarm(player);
        if (notify) {
            player.sendActionBar(plugin.messages().get("firework.left"));
            player.playSound(LEAVE_SOUND);
        }
    }

    /** Retire tout ce que le mode a donné : contour, bonus (vitesse, grappin), objet armé. */
    private void disarm(Player player) {
        clearGlow(player);
        clearSpeed(player);
        removeGrapples(player);
        refresh(player);
    }

    /** Contour lumineux posé par le mini-jeu (marqué sur le joueur pour survivre à un arrêt brutal). */
    private void clearGlow(Player player) {
        PersistentDataContainer data = player.getPersistentDataContainer();
        if (data.has(glowKey, PersistentDataType.BYTE)) {
            data.remove(glowKey);
            player.setGlowing(false);
        }
    }

    /** Vitesse du bonus de série (effet infini, marqué sur le joueur comme le contour). */
    private void clearSpeed(Player player) {
        PersistentDataContainer data = player.getPersistentDataContainer();
        if (data.has(speedKey, PersistentDataType.BYTE)) {
            data.remove(speedKey);
            PotionEffect effect = player.getPotionEffect(PotionEffectType.SPEED);
            if (effect != null && effect.isInfinite()) {
                player.removePotionEffect(PotionEffectType.SPEED);
            }
        }
    }

    /** Couleur pas encore prise par un autre joueur en jeu, si possible. */
    private Color freeColor() {
        List<Color> free = new ArrayList<>(List.of(COLORS));
        for (Fighter other : fighters.values()) {
            free.remove(other.color);
        }
        List<Color> pool = free.isEmpty() ? List.of(COLORS) : free;
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }

    // ------------------------------------------------------------------ tir

    private void shoot(Player player) {
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        lastUse.put(id, now);
        Fighter fighter = fighters.get(id);
        if (fighter == null) {
            player.sendActionBar(plugin.messages().get("firework.not-armed"));
            return;
        }
        FileConfiguration config = plugin.getConfig();
        ItemStack hand = player.getInventory().getItemInMainHand();
        int cooldown = Math.max(1, config.getInt("firework-game.cooldown-ticks", 16));
        if (fighter.bonuses.contains(Bonus.RAPID_FIRE)) {
            cooldown = Math.max(1, cooldown / 2);
        }
        // Clic droit sur un bloc = deux paquets (sur le bloc, puis dans le vide) : un seul tir.
        if (player.hasCooldown(hand) || now - fighter.lastShot < cooldown * 50L - 60L) {
            return;
        }
        player.setCooldown(hand, cooldown);
        fighter.lastShot = now;

        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        List<Vector> directions = new ArrayList<>(List.of(dir));
        if (fighter.bonuses.contains(Bonus.VOLLEY)) {
            double spread = Math.toRadians(config.getDouble("firework-game.volley-spread", 6.0));
            directions.add(dir.clone().rotateAroundY(spread));
            directions.add(dir.clone().rotateAroundY(-spread));
        }
        Component name = Component.text(player.getName(), TextColor.color(fighter.color.asRGB()));
        player.getWorld().playSound(LAUNCH_SOUND, eye.getX(), eye.getY(), eye.getZ());
        for (Vector direction : directions) {
            launch(new Shot(id, name, fighter.color, false, eye.clone().add(direction.clone().multiply(0.5)), direction));
        }
    }

    /** Premier pas tout de suite (pas d'un tick de retard), puis le projectile continue dans tick(). */
    private void launch(Shot shot) {
        if (advance(shot)) {
            shots.add(shot);
        }
    }

    private void tick() {
        ticks++;
        Iterator<Shot> it = shots.iterator();
        while (it.hasNext()) {
            Shot shot = it.next();
            if (!advance(shot)) {
                removeDisplay(shot);
                it.remove();
            }
        }
        tickDummies();
        if (ticks % 20 == 0) {
            for (Map.Entry<UUID, Fighter> entry : fighters.entrySet()) {
                Player player = plugin.getServer().getPlayer(entry.getKey());
                if (player != null && entry.getValue().hudHold <= ticks) {
                    player.sendActionBar(hud(entry.getKey(), entry.getValue()));
                }
            }
        }
    }

    /** Avance d'un tick. false : le tir est terminé (cible touchée, bloc, portée maximale). */
    private boolean advance(Shot shot) {
        FileConfiguration config = plugin.getConfig();
        double speed = Math.max(0.5, config.getDouble("firework-game.speed", 2.5));
        double range = Math.max(4.0, config.getDouble("firework-game.range", 64.0));
        double radius = Math.max(0.0, config.getDouble("firework-game.hit-radius", 0.3));
        double step = Math.min(speed, range - shot.travelled);
        if (step <= 0.01 || !shot.world.isChunkLoaded(shot.pos.getBlockX() >> 4, shot.pos.getBlockZ() >> 4)) {
            fizzle(shot.pos, shot.color);
            return false;
        }
        RayTraceResult hit = shot.world.rayTrace(shot.pos, shot.dir, step, FluidCollisionMode.NEVER, true, radius,
                entity -> isTarget(shot, entity));
        double length = hit != null ? hit.getHitPosition().distance(shot.pos.toVector()) : step;
        Location end = shot.pos.clone().add(shot.dir.clone().multiply(length));
        trail(shot, length);
        shot.travelled += length;
        if (hit != null) {
            Entity entity = hit.getHitEntity();
            Dummy dummy = entity == null ? null : dummyOf(entity);
            if (entity instanceof Player victim) {
                hit(shot, victim);
            } else if (dummy != null) {
                hitDummy(shot, dummy);
            } else {
                fizzle(end, shot.color);
            }
            return false;
        }
        shot.pos = end;
        if (shot.travelled >= range) {
            fizzle(end, shot.color);
            return false;
        }
        moveDisplay(shot);
        return true;
    }

    /** Joueur en mode (sauf le tireur), ou cible d'entraînement pour un tir de joueur. */
    private boolean isTarget(Shot shot, Entity entity) {
        if (entity.getUniqueId().equals(shot.shooter)) {
            return false;
        }
        if (entity instanceof Player player) {
            Fighter fighter = fighters.get(player.getUniqueId());
            return fighter != null && fighter.protectedUntil <= ticks;
        }
        return !shot.fromDummy && dummyOf(entity) != null;
    }

    private void trail(Shot shot, double length) {
        Particle.DustOptions dust = new Particle.DustOptions(shot.color, 1.1f);
        for (double d = 0; d < length; d += TRAIL_STEP) {
            if (shot.travelled + d < TRAIL_SKIP) {
                continue;
            }
            Location point = shot.pos.clone().add(shot.dir.clone().multiply(d));
            shot.world.spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, dust, true);
            if (++shot.sparks % 3 == 0) {
                shot.world.spawnParticle(Particle.FIREWORK, point, 1, 0.02, 0.02, 0.02, 0.01, null, true);
            }
        }
    }

    /** Fusée visible en vol : objet affiché face à la caméra, déplacement interpolé d'un tick à l'autre. */
    private void moveDisplay(Shot shot) {
        if (shot.display == null || !shot.display.isValid()) {
            shot.display = shot.world.spawn(shot.pos, ItemDisplay.class, display -> {
                display.setItemStack(new ItemStack(Material.FIREWORK_ROCKET));
                display.setBillboard(Display.Billboard.CENTER);
                display.setBrightness(new Display.Brightness(15, 15));
                display.setTeleportDuration(1);
                display.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                        new Vector3f(0.6f, 0.6f, 0.6f), new AxisAngle4f()));
                display.setPersistent(false);
            });
            return;
        }
        shot.display.teleport(shot.pos);
    }

    private static void removeDisplay(Shot shot) {
        if (shot.display != null) {
            shot.display.remove();
            shot.display = null;
        }
    }

    /** Tir perdu (bloc, portée maximale) : petite gerbe d'étincelles. */
    private void fizzle(Location at, Color color) {
        World world = at.getWorld();
        world.spawnParticle(Particle.FIREWORK, at, 14, 0.1, 0.1, 0.1, 0.12, null, true);
        world.spawnParticle(Particle.DUST, at, 8, 0.25, 0.25, 0.25, 0, new Particle.DustOptions(color, 1.4f), true);
        world.playSound(FIZZLE_SOUND, at.getX(), at.getY(), at.getZ());
    }

    /** Vraie explosion de feu d'artifice (sans dégâts : voir onBurstDamage). */
    private void burst(Location at, Color color) {
        Firework firework = at.getWorld().spawn(at, Firework.class, fw -> {
            FireworkMeta meta = fw.getFireworkMeta();
            meta.clearEffects();
            meta.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE)
                    .withColor(color, Color.WHITE).withFade(color).flicker(true).trail(true).build());
            fw.setFireworkMeta(meta);
            fw.getPersistentDataContainer().set(burstKey, PersistentDataType.BYTE, (byte) 1);
            fw.setPersistent(false);
        });
        firework.detonate();
    }

    // ------------------------------------------------------------------ touché, bouclier, élimination

    private void hit(Shot shot, Player victim) {
        Fighter target = fighters.get(victim.getUniqueId());
        if (target == null) {
            return; // déjà éliminé par un autre tir dans le même tick
        }
        if (target.bonuses.remove(Bonus.SHIELD)) {
            breakShield(shot, victim, target);
        } else {
            eliminate(shot, victim);
        }
    }

    /** Bonus bouclier : le tir est encaissé, le bouclier disparaît. */
    private void breakShield(Shot shot, Player victim, Fighter target) {
        Location at = victim.getLocation().add(0, 1.0, 0);
        World world = victim.getWorld();
        world.spawnParticle(Particle.ENCHANTED_HIT, at, 30, 0.4, 0.6, 0.4, 0.3, null, true);
        world.spawnParticle(Particle.DUST, at, 12, 0.4, 0.6, 0.4, 0, new Particle.DustOptions(shot.color, 1.4f), true);
        world.playSound(SHIELD_SOUND, at.getX(), at.getY(), at.getZ());
        Messages messages = plugin.messages();
        target.hudHold = ticks + 40;
        victim.sendActionBar(messages.get("firework.shield-broken", comp("killer", shot.shooterName)));
        Player shooter = plugin.getServer().getPlayer(shot.shooter);
        if (shooter != null) {
            Fighter fighter = fighters.get(shot.shooter);
            if (fighter != null) {
                fighter.hudHold = ticks + 40;
            }
            shooter.sendActionBar(messages.get("firework.shield-hit", ph("victim", victim.getName())));
        }
    }

    private void eliminate(Shot shot, Player victim) {
        burst(victim.getLocation().add(0, 1.0, 0), shot.color);
        boolean stay = plugin.getConfig().getBoolean("firework-game.stay-in-mode", true);
        Fighter victimFighter = fighters.get(victim.getUniqueId());
        if (stay && victimFighter != null) {
            resetAfterDeath(victim, victimFighter);
        } else {
            fighters.remove(victim.getUniqueId());
            disarm(victim);
        }
        Messages messages = plugin.messages();
        victim.showTitle(Title.title(messages.get("firework.eliminated-title"),
                messages.get("firework.eliminated-subtitle", comp("killer", shot.shooterName)), TITLE_TIMES));
        victim.playSound(DEATH_SOUND);
        if (plugin.getConfig().getBoolean("firework-game.respawn-at-spawn", true)) {
            Location spot = respawnSpot(victim);
            if (spot != null) {
                Teleports.teleport(plugin, victim, spot, null);
            }
        }
        Component victimName = Component.text(victim.getName());
        Fighter fighter = credit(shot, victimName);

        // Fil des éliminations : joueurs en jeu + la victime.
        Set<Player> audience = new LinkedHashSet<>();
        for (UUID id : fighters.keySet()) {
            Player player = plugin.getServer().getPlayer(id);
            if (player != null) {
                audience.add(player);
            }
        }
        audience.add(victim);
        Component feed = messages.get("firework.feed", comp("killer", shot.shooterName), comp("victim", victimName));
        Component streak = fighter != null && isMilestone(fighter.streak)
                ? messages.get("firework.streak", comp("killer", shot.shooterName), ph("streak", fighter.streak)) : null;
        for (Player player : audience) {
            player.sendMessage(feed);
            if (streak != null) {
                player.sendMessage(streak);
            }
        }
        if (fighter != null) {
            reward(shot.shooter, fighter);
        }
    }

    /** Élimination comptée pour le tireur (s'il est un joueur encore connecté). Renvoie son état en jeu. */
    private Fighter credit(Shot shot, Component victimName) {
        Player shooter = plugin.getServer().getPlayer(shot.shooter);
        if (shooter == null) {
            return null;
        }
        kills.merge(shot.shooter, 1, Integer::sum);
        shooter.playSound(KILL_SOUND);
        shooter.sendActionBar(plugin.messages().get("firework.kill", comp("victim", victimName)));
        Fighter fighter = fighters.get(shot.shooter);
        if (fighter != null) {
            fighter.streak++;
            fighter.hudHold = ticks + 40;
        }
        return fighter;
    }

    private static boolean isMilestone(int streak) {
        return streak == 3 || streak == 5 || (streak >= 10 && streak % 5 == 0);
    }

    // ------------------------------------------------------------------ réapparition dans la station

    /** Élimination ou mort en restant dans le mode : série et bonus perdus, courte protection. */
    private void resetAfterDeath(Player player, Fighter fighter) {
        fighter.streak = 0;
        fighter.bonuses.clear();
        fighter.protectedUntil = ticks + Math.max(0, plugin.getConfig().getInt("firework-game.respawn-protection-ticks", 40));
        clearSpeed(player);
        removeGrapples(player);
        refresh(player);
    }

    /**
     * Point de réapparition : au hasard dans la station (firework-game.random-respawn), loin des autres
     * joueurs en mode si possible ; sinon le spawn du lobby.
     */
    private Location respawnSpot(Player player) {
        if (plugin.getConfig().getBoolean("firework-game.random-respawn", true)) {
            Location spot = randomStationSpot(player);
            if (spot != null) {
                return spot;
            }
        }
        return plugin.locations().spawn();
    }

    private Location randomStationSpot(Player player) {
        World world = plugin.getServer().getWorld(plugin.station().worldName());
        if (world == null) {
            return null;
        }
        FileConfiguration config = plugin.getConfig();
        org.bukkit.configuration.file.YamlConfiguration station = plugin.station().config();
        int cx = station.getInt("origine.x", 0);
        int cy = station.getInt("origine.y", 100);
        int cz = station.getInt("origine.z", 0);
        int radius = Math.max(4, config.getInt("firework-game.random-respawn-radius", 85));
        int maxRise = Math.max(1, config.getInt("firework-game.random-respawn-height", 8));
        double minDist = Math.max(0, config.getDouble("firework-game.random-respawn-min-distance", 12));
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Location fallback = null;
        for (int attempt = 0; attempt < 80; attempt++) {
            int x = cx + random.nextInt(-radius, radius + 1);
            int z = cz + random.nextInt(-radius, radius + 1);
            for (int y = cy + 1; y <= cy + maxRise; y++) {
                if (!isSafeSpot(world, x, y, z)) {
                    continue;
                }
                Location spot = new Location(world, x + 0.5, y, z + 0.5, random.nextFloat() * 360f, 0f);
                if (farFromFighters(player, spot, minDist)) {
                    return spot;
                }
                if (fallback == null) {
                    fallback = spot;
                }
                break;
            }
        }
        return fallback;
    }

    /** Sol plein, deux blocs libres, dégagé autour et un plafond au-dessus (donc à l'intérieur de la station). */
    private static boolean isSafeSpot(World world, int x, int y, int z) {
        org.bukkit.block.Block below = world.getBlockAt(x, y - 1, z);
        Material ground = below.getType();
        if (!ground.isSolid() || ground == Material.MAGMA_BLOCK || ground == Material.CAMPFIRE
                || ground == Material.SOUL_CAMPFIRE || ground == Material.CACTUS || ground.name().endsWith("_FENCE")
                || ground.name().endsWith("_WALL") || ground == Material.BARRIER) {
            return false;
        }
        if (!free(world.getBlockAt(x, y, z)) || !free(world.getBlockAt(x, y + 1, z))) {
            return false;
        }
        int open = 0;
        int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : around) {
            if (free(world.getBlockAt(x + d[0], y, z + d[1])) && free(world.getBlockAt(x + d[0], y + 1, z + d[1]))) {
                open++;
            }
        }
        if (open < 3) {
            return false;
        }
        for (int up = y + 2; up <= y + 48; up++) {
            if (!world.getBlockAt(x, up, z).getType().isAir()) {
                return true;
            }
        }
        return false; // rien au-dessus : dehors, sur la coque
    }

    private static boolean free(org.bukkit.block.Block block) {
        Material type = block.getType();
        return block.isPassable() && !block.isLiquid() && type != Material.FIRE && type != Material.SOUL_FIRE
                && !type.name().endsWith("_PRESSURE_PLATE") && type != Material.NETHER_PORTAL
                && type != Material.END_PORTAL && type != Material.END_GATEWAY;
    }

    private boolean farFromFighters(Player self, Location spot, double minDist) {
        double sq = minDist * minDist;
        for (UUID id : fighters.keySet()) {
            if (id.equals(self.getUniqueId())) {
                continue;
            }
            Player other = plugin.getServer().getPlayer(id);
            if (other != null && other.getWorld() == spot.getWorld() && other.getLocation().distanceSquared(spot) < sq) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ bonus de série

    private int threshold(Bonus bonus) {
        return plugin.getConfig().getInt("firework-game.streak-rewards." + bonus.key, bonus.defaultStreak);
    }

    /** Débloque les bonus dont le palier (firework-game.streak-rewards) vient d'être atteint. */
    private void reward(UUID id, Fighter fighter) {
        Player player = plugin.getServer().getPlayer(id);
        if (player == null) {
            return;
        }
        for (Bonus bonus : Bonus.values()) {
            int at = threshold(bonus);
            if (at > 0 && fighter.streak == at) {
                unlock(player, fighter, bonus);
            }
        }
    }

    private void unlock(Player player, Fighter fighter, Bonus bonus) {
        if (!fighter.bonuses.add(bonus)) {
            return;
        }
        switch (bonus) {
            case SPEED -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION,
                        1, true, false, true));
                player.getPersistentDataContainer().set(speedKey, PersistentDataType.BYTE, (byte) 1);
            }
            case GRAPPLE -> giveGrapple(player);
            default -> {
                // tir rapide, bouclier, salve : lus au moment du tir ou du touché
            }
        }
        Messages messages = plugin.messages();
        player.sendMessage(messages.get("firework.bonus.unlocked", ph("streak", fighter.streak),
                comp("bonus", messages.get("firework.bonus." + bonus.key + ".name")),
                comp("hint", messages.get("firework.bonus." + bonus.key + ".hint"))));
        player.playSound(BONUS_SOUND);
    }

    /** Grappin : propulse vers le bloc visé, ou grand saut vers l'avant si rien n'est à portée. */
    private void grapple(Player player) {
        Fighter fighter = fighters.get(player.getUniqueId());
        if (fighter == null || !fighter.bonuses.contains(Bonus.GRAPPLE)) {
            removeGrapples(player); // reste d'une ancienne série
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (player.hasCooldown(hand)) {
            return;
        }
        FileConfiguration config = plugin.getConfig();
        player.setCooldown(hand, Math.max(1, config.getInt("firework-game.grapple.cooldown-ticks", 50)));
        double range = Math.max(4.0, config.getDouble("firework-game.grapple.range", 32.0));
        double power = Math.max(0.5, config.getDouble("firework-game.grapple.power", 2.6));

        World world = player.getWorld();
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        RayTraceResult hit = world.rayTraceBlocks(eye, dir, range, FluidCollisionMode.NEVER, true);
        Vector velocity;
        if (hit != null) {
            Vector target = hit.getHitPosition().clone();
            rope(world, eye.toVector().add(dir.clone().multiply(0.6)), target);
            world.playSound(HOOK_SOUND, target.getX(), target.getY(), target.getZ());
            Vector pull = target.subtract(player.getLocation().toVector());
            double distance = pull.length();
            velocity = distance < 0.01 ? new Vector(0, 0.8, 0)
                    : pull.multiply(Math.min(power, 1.0 + distance * 0.05) / distance).add(new Vector(0, 0.4, 0));
        } else {
            Vector flat = dir.clone().setY(0);
            velocity = flat.lengthSquared() < 1.0E-4 ? new Vector(0, 1.2, 0) : flat.normalize().multiply(1.3).setY(1.0);
        }
        if (player.isFlying()) {
            player.setFlying(false); // sinon le vol créatif freine la propulsion
        }
        player.setVelocity(velocity);
        Location feet = player.getLocation();
        world.spawnParticle(Particle.CLOUD, feet, 12, 0.3, 0.05, 0.3, 0.05, null, true);
        world.playSound(BOOST_SOUND, feet.getX(), feet.getY(), feet.getZ());
    }

    /** Corde du grappin, de la main jusqu'au point d'accroche. */
    private static void rope(World world, Vector from, Vector to) {
        Vector step = to.clone().subtract(from);
        double length = step.length();
        if (length < 0.01) {
            return;
        }
        step.multiply(0.4 / length);
        Particle.DustOptions dust = new Particle.DustOptions(ROPE, 0.7f);
        Vector point = from.clone();
        for (double d = 0; d < length; d += 0.4) {
            world.spawnParticle(Particle.DUST, point.getX(), point.getY(), point.getZ(), 1, 0, 0, 0, 0, dust, true);
            point.add(step);
        }
        world.spawnParticle(Particle.CRIT, to.getX(), to.getY(), to.getZ(), 8, 0.1, 0.1, 0.1, 0.1, null, true);
    }

    private Component hud(UUID id, Fighter fighter) {
        Messages messages = plugin.messages();
        Component bonuses = Component.empty();
        if (!fighter.bonuses.isEmpty()) {
            List<Component> names = new ArrayList<>();
            for (Bonus bonus : fighter.bonuses) {
                names.add(messages.get("firework.bonus." + bonus.key + ".name"));
            }
            bonuses = messages.get("firework.hud-bonuses",
                    comp("list", Component.join(JoinConfiguration.separator(Component.text(", ")), names)));
        }
        return messages.get("firework.hud",
                comp("dot", Component.text("●", TextColor.color(fighter.color.asRGB()))),
                ph("kills", kills.getOrDefault(id, 0)),
                ph("streak", fighter.streak),
                ph("players", fighters.size()),
                comp("bonuses", bonuses));
    }

    // ------------------------------------------------------------------ cibles d'entraînement

    private Dummy dummyOf(Entity entity) {
        for (Dummy dummy : dummies) {
            if (dummy.entity != null && dummy.entity.getUniqueId().equals(entity.getUniqueId())) {
                return dummy;
            }
        }
        return null;
    }

    private boolean isDummyEntity(Entity entity) {
        return entity != null && entity.getPersistentDataContainer().has(dummyKey, PersistentDataType.BYTE);
    }

    private void spawnDummy(Dummy dummy) {
        Messages messages = plugin.messages();
        dummy.entity = dummy.post.getWorld().spawn(dummy.post, Mannequin.class, mannequin -> {
            mannequin.customName(messages.get("firework.dummy.name"));
            mannequin.setCustomNameVisible(true);
            mannequin.setDescription(messages.get(dummy.fires ? "firework.dummy.description-fires" : "firework.dummy.description"));
            mannequin.setImmovable(true);
            mannequin.setInvulnerable(true);
            mannequin.setPersistent(false);
            mannequin.setGlowing(true);
            if (dummy.fires) {
                mannequin.getEquipment().setItemInMainHand(new ItemStack(Material.FIREWORK_ROCKET));
            }
            mannequin.getPersistentDataContainer().set(dummyKey, PersistentDataType.BYTE, (byte) 1);
        });
        dummy.nextShot = ticks + fireTicks();
        dummy.post.getWorld().spawnParticle(Particle.CLOUD, dummy.post.clone().add(0, 1.0, 0), 10, 0.3, 0.5, 0.3, 0.02, null, true);
    }

    private int fireTicks() {
        return Math.max(10, plugin.getConfig().getInt("firework-game.dummy.fire-ticks", 60));
    }

    private void tickDummies() {
        for (Dummy dummy : dummies) {
            if (dummy.entity != null && !dummy.entity.isValid()) {
                dummy.entity = null; // chunk déchargé (mannequin non sauvegardé) : recréé au retour
                dummy.respawnAt = ticks;
            }
            if (dummy.entity == null) {
                if (ticks >= dummy.respawnAt && dummy.post.isChunkLoaded()) {
                    spawnDummy(dummy);
                }
                continue;
            }
            if (ticks % 5 != 0) {
                continue;
            }
            Player target = nearestFighter(dummy.entity);
            if (target == null) {
                continue;
            }
            Location look = dummy.entity.getLocation();
            look.setDirection(target.getEyeLocation().toVector().subtract(dummy.entity.getEyeLocation().toVector()));
            dummy.entity.setRotation(look.getYaw(), look.getPitch());
            if (dummy.fires && ticks >= dummy.nextShot) {
                dummy.nextShot = ticks + fireTicks();
                fireDummy(dummy, target);
            }
        }
    }

    /** Joueur en mode le plus proche, en vue directe. */
    private Player nearestFighter(Mannequin mannequin) {
        Location at = mannequin.getLocation();
        Player best = null;
        double bestSq = DUMMY_SIGHT * DUMMY_SIGHT;
        for (UUID id : fighters.keySet()) {
            Player player = plugin.getServer().getPlayer(id);
            if (player == null || player.getWorld() != at.getWorld()) {
                continue;
            }
            double sq = player.getLocation().distanceSquared(at);
            if (sq < bestSq && mannequin.hasLineOfSight(player)) {
                best = player;
                bestSq = sq;
            }
        }
        return best;
    }

    /** Riposte : un tir droit vers le torse du joueur (sans anticiper : on peut l'esquiver en bougeant). */
    private void fireDummy(Dummy dummy, Player target) {
        Location eye = dummy.entity.getEyeLocation();
        Vector dir = target.getLocation().add(0, 1.0, 0).toVector().subtract(eye.toVector());
        if (dir.lengthSquared() < 1.0E-4) {
            return;
        }
        dir.normalize();
        eye.getWorld().playSound(LAUNCH_SOUND, eye.getX(), eye.getY(), eye.getZ());
        launch(new Shot(dummy.entity.getUniqueId(), plugin.messages().get("firework.dummy.short-name"), DUMMY_COLOR,
                true, eye.clone().add(dir.clone().multiply(0.6)), dir));
    }

    private void hitDummy(Shot shot, Dummy dummy) {
        burst(dummy.entity.getLocation().add(0, 1.0, 0), shot.color);
        dummy.entity.remove();
        dummy.entity = null;
        dummy.respawnAt = ticks + Math.max(1, plugin.getConfig().getInt("firework-game.dummy.respawn-ticks", 60));
        Fighter fighter = credit(shot, plugin.messages().get("firework.dummy.short-name"));
        if (fighter != null) {
            reward(shot.shooter, fighter);
        }
    }

    private void removeDummies() {
        for (Dummy dummy : dummies) {
            if (dummy.entity != null) {
                dummy.entity.remove();
            }
        }
        dummies.clear();
    }

    /** /orbitdev feu &lt;cible|riposte|retirer|serie &lt;n&gt;&gt; (outils de test, réservés aux admins). */
    public void command(CommandSender sender, String[] args) {
        Messages messages = plugin.messages();
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.players-only");
            return;
        }
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "cible", "riposte" -> {
                if (!enabled()) {
                    messages.send(player, "firework.lobby-only");
                    return;
                }
                Dummy dummy = new Dummy(player.getLocation().clone(), sub.equals("riposte"));
                dummies.add(dummy);
                spawnDummy(dummy);
                messages.send(player, dummy.fires ? "firework.dummy.placed-fires" : "firework.dummy.placed");
            }
            case "retirer" -> {
                int count = dummies.size();
                removeDummies();
                messages.send(player, "firework.dummy.removed", ph("count", count));
            }
            case "serie" -> {
                Fighter fighter = fighters.get(player.getUniqueId());
                if (fighter == null) {
                    messages.send(player, "firework.not-in-mode");
                    return;
                }
                int streak;
                try {
                    streak = args.length < 2 ? -1 : Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    streak = -1;
                }
                if (streak < 0 || streak > 1000) {
                    messages.sendList(player, "firework.admin-help");
                    return;
                }
                fighter.streak = streak;
                for (Bonus bonus : Bonus.values()) {
                    int at = threshold(bonus);
                    if (at > 0 && at <= streak) {
                        unlock(player, fighter, bonus);
                    }
                }
                messages.send(player, "firework.streak-set", ph("streak", streak));
                player.sendActionBar(hud(player.getUniqueId(), fighter));
            }
            default -> messages.sendList(player, "firework.admin-help");
        }
    }

    // ------------------------------------------------------------------ protections et sorties forcées

    @EventHandler(priority = EventPriority.LOW)
    public void onBurstDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Firework firework
                && firework.getPersistentDataContainer().has(burstKey, PersistentDataType.BYTE)) {
            event.setCancelled(true);
        }
    }

    /** Pas de dégâts de chute en mode feu d'artifice (grappin, sauts boostés) ; cibles intouchables. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (isDummyEntity(entity) || (event.getCause() == EntityDamageEvent.DamageCause.FALL
                && fighters.containsKey(entity.getUniqueId()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteractDummy(PlayerInteractEntityEvent event) {
        if (isDummyEntity(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();
        if (isLauncher(item) || isGrapple(item)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (isLauncher(event.getMainHandItem()) || isLauncher(event.getOffHandItem())
                || isGrapple(event.getMainHandItem()) || isGrapple(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(item -> isLauncher(item) || isGrapple(item));
        Player player = event.getPlayer();
        Fighter fighter = fighters.get(player.getUniqueId());
        if (fighter != null && plugin.getConfig().getBoolean("firework-game.stay-in-mode", true)) {
            resetAfterDeath(player, fighter); // reste en mode, réapparaît au hasard (onRespawnSpot)
        } else {
            leave(player, false);
        }
    }

    /** Vraie mort en mode : réapparition au hasard dans la station (après le renvoi au spawn général). */
    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawnSpot(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!fighters.containsKey(player.getUniqueId())
                || !plugin.getConfig().getBoolean("firework-game.respawn-at-spawn", true)) {
            return;
        }
        Location spot = respawnSpot(player);
        if (spot != null) {
            event.setRespawnLocation(spot);
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        leave(event.getPlayer(), true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameMode(PlayerGameModeChangeEvent event) {
        if (event.getNewGameMode() == GameMode.SPECTATOR) {
            leave(event.getPlayer(), true);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Restes d'un arrêt brutal du serveur : le mode ne survit pas à une déconnexion.
        clearGlow(player);
        clearSpeed(player);
        removeGrapples(player);
        if (!enabled()) {
            removeAll(player);
        } else if (plugin.getConfig().getBoolean("firework-game.give-on-join", true)) {
            give(player);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        // L'inventaire n'est modifiable proprement qu'une fois la réapparition terminée.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (!enabled()) {
                removeAll(player);
            } else if (plugin.getConfig().getBoolean("firework-game.give-on-join", true)
                    || fighters.containsKey(player.getUniqueId())) {
                give(player);
            }
            // Toujours en mode après une vraie mort : le contour est perdu avec l'ancien corps.
            if (fighters.containsKey(player.getUniqueId()) && plugin.getConfig().getBoolean("firework-game.glow", true)) {
                player.setGlowing(true);
                player.getPersistentDataContainer().set(glowKey, PersistentDataType.BYTE, (byte) 1);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        leave(player, false);
        UUID id = player.getUniqueId();
        kills.remove(id);
        lastToggle.remove(id);
        lastUse.remove(id);
    }
}
