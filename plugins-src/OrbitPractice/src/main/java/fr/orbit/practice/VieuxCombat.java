package fr.orbit.practice;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.BlocksAttacks;
import io.papermc.paper.datacomponent.item.blocksattacks.DamageReduction;
import io.papermc.paper.datacomponent.item.blocksattacks.ItemDamageFunction;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityKnockbackByEntityEvent;
import org.bukkit.event.entity.EntityKnockbackEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Combat façon 1.7/1.8 sur tout le serveur (hub, Practice et mode HCF), en restant en 26.3 :
 * pas de délai d'attaque, pas de balayage, recul d'avant, haches et Tranchant d'avant, blocage à l'épée
 * (clic droit), régénération lente, pomme de Notch d'avant, pas de main secondaire ni de bouclier,
 * recul de la canne à pêche. Chaque point se coupe dans « vieux-combat » de la config.
 */
public final class VieuxCombat implements Listener {

    /** Vitesse d'attaque qui supprime la recharge (et la barre de recharge à l'écran). */
    public static final double VITESSE_LIBRE = 1024.0;
    private static final int MAIN_SECONDAIRE = 40;

    private static OrbitPractice plugin;

    private final Map<UUID, Long> derniereRegen = new HashMap<>();

    public VieuxCombat(OrbitPractice plugin) {
        VieuxCombat.plugin = plugin;
    }

    /** true si le vieux combat est actif (lu à chaque fois : suit /practice reload). */
    public static boolean actif() {
        return plugin != null && plugin.getConfig().getBoolean("vieux-combat.actif", true);
    }

    private static boolean option(String cle) {
        return actif() && plugin.getConfig().getBoolean("vieux-combat." + cle, true);
    }

    private static double nombre(String cle, double defaut) {
        return plugin.getConfig().getDouble("vieux-combat." + cle, defaut);
    }

    /** Une fois par seconde : vitesse d'attaque, épées qui bloquent, main secondaire vide. */
    public void tick() {
        if (!actif()) {
            return;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            preparer(p);
        }
    }

    private void preparer(Player p) {
        AttributeInstance vitesse = p.getAttribute(Attribute.ATTACK_SPEED);
        if (vitesse != null && vitesse.getBaseValue() != VITESSE_LIBRE) {
            vitesse.setBaseValue(VITESSE_LIBRE);
        }
        if (option("blocage-epee")) {
            epeeQuiBloque(p);
        }
        if (option("sans-main-secondaire")) {
            viderMainSecondaire(p);
        }
    }

    // ------------------------------------------------------------------ Remise en place

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (actif()) {
            Bukkit.getScheduler().runTask(plugin, () -> preparer(event.getPlayer()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorld(PlayerChangedWorldEvent event) {
        if (actif()) {
            Bukkit.getScheduler().runTask(plugin, () -> preparer(event.getPlayer()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        if (actif()) {
            Bukkit.getScheduler().runTask(plugin, () -> preparer(event.getPlayer()));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        derniereRegen.remove(event.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------ Balayage

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSweep(EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK && option("sans-balayage")) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ Dégâts des armes

    /** Haches et Tranchant aux valeurs d'avant. LOWEST : les pouvoirs de classe s'appliquent ensuite. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || !(event.getDamager() instanceof Player attaquant) || !option("degats-anciens")) {
            return;
        }
        ItemStack arme = attaquant.getInventory().getItemInMainHand();
        double ecart = 0;
        if (Tag.ITEMS_AXES.isTagged(arme.getType())) {
            double moderne = hacheModerne(arme.getType());
            double ancien = hacheAncienne(arme.getType());
            if (moderne > 0) {
                ecart += (ancien - moderne) * (event.isCritical() ? 1.5 : 1.0);
            }
        }
        int tranchant = arme.getEnchantmentLevel(Enchantment.SHARPNESS);
        if (tranchant > 0) {
            // 1.8 : +1,25 par niveau. Aujourd'hui : 0,5 × niveau + 0,5.
            ecart += 1.25 * tranchant - (0.5 * tranchant + 0.5);
        }
        if (ecart != 0) {
            event.setDamage(Math.max(0.5, event.getDamage() + ecart));
        }
    }

    private static double hacheModerne(Material m) {
        return switch (m) {
            case WOODEN_AXE, GOLDEN_AXE -> 7;
            case STONE_AXE, IRON_AXE, DIAMOND_AXE -> 9;
            case NETHERITE_AXE -> 10;
            default -> 0;
        };
    }

    private static double hacheAncienne(Material m) {
        return switch (m) {
            case WOODEN_AXE, GOLDEN_AXE -> 3;
            case STONE_AXE -> 4;
            case IRON_AXE -> 5;
            case DIAMOND_AXE -> 6;
            case NETHERITE_AXE -> 7;
            default -> 0;
        };
    }

    // ------------------------------------------------------------------ Recul

    /** Le recul de base de 26.3 est retiré entre joueurs : il est recalculé façon 1.8 dans onKnockback. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVanillaKnockback(EntityKnockbackByEntityEvent event) {
        if (!(event.getEntity() instanceof Player) || !(event.getSourceEntity() instanceof Player)
                || !option("recul-ancien")) {
            return;
        }
        EntityKnockbackEvent.KnockbackCause cause = event.getCause();
        if (cause == EntityKnockbackEvent.KnockbackCause.ENTITY_ATTACK
                || cause == EntityKnockbackEvent.KnockbackCause.SWEEP_ATTACK
                || cause == EntityKnockbackEvent.KnockbackCause.SHIELD_BLOCK) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKnockback(EntityDamageByEntityEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || !(event.getEntity() instanceof Player victime)
                || !(event.getDamager() instanceof Player attaquant)
                || victime.equals(attaquant) || !option("recul-ancien")) {
            return;
        }
        double horizontal = nombre("recul.horizontal", 0.4);
        double vertical = nombre("recul.vertical", 0.4);
        double plafond = nombre("recul.plafond-vertical", 0.4);
        double bonus = nombre("recul.bonus-sprint", 0.5);
        double bonusVertical = nombre("recul.bonus-vertical", 0.1);

        double dx = attaquant.getLocation().getX() - victime.getLocation().getX();
        double dz = attaquant.getLocation().getZ() - victime.getLocation().getZ();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        while (dx * dx + dz * dz < 1.0E-4) {
            dx = (r.nextDouble() - r.nextDouble()) * 0.01;
            dz = (r.nextDouble() - r.nextDouble()) * 0.01;
        }
        double d = Math.sqrt(dx * dx + dz * dz);

        Vector v = victime.getVelocity();
        v.setX(v.getX() / 2.0 - dx / d * horizontal);
        v.setY(Math.min(plafond, v.getY() / 2.0 + vertical));
        v.setZ(v.getZ() / 2.0 - dz / d * horizontal);

        int niveaux = attaquant.getInventory().getItemInMainHand().getEnchantmentLevel(Enchantment.KNOCKBACK)
                + (attaquant.isSprinting() ? 1 : 0);
        if (niveaux > 0) {
            double yaw = Math.toRadians(attaquant.getLocation().getYaw());
            v.setX(v.getX() - Math.sin(yaw) * niveaux * bonus);
            v.setY(v.getY() + bonusVertical);
            v.setZ(v.getZ() + Math.cos(yaw) * niveaux * bonus);
        }
        victime.setVelocity(v);
    }

    /** Canne à pêche : un joueur accroché prend un petit coup, donc le recul d'avant. */
    @EventHandler(ignoreCancelled = true)
    public void onRod(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof FishHook hook) || !(event.getHitEntity() instanceof Player victime)
                || !(hook.getShooter() instanceof Player pecheur) || victime.equals(pecheur)
                || !option("recul-canne")) {
            return;
        }
        if (victime.getNoDamageTicks() > victime.getMaximumNoDamageTicks() / 2) {
            return;
        }
        victime.damage(0.01, pecheur);
    }

    // ------------------------------------------------------------------ Blocage à l'épée

    private void epeeQuiBloque(Player p) {
        PlayerInventory inv = p.getInventory();
        ItemStack epee = inv.getItemInMainHand();
        if (!Tag.ITEMS_SWORDS.isTagged(epee.getType()) || epee.hasData(DataComponentTypes.BLOCKS_ATTACKS)) {
            return;
        }
        float part = (float) Math.max(0, Math.min(1, nombre("blocage-part-bloquee", 0.5)));
        epee.setData(DataComponentTypes.BLOCKS_ATTACKS, BlocksAttacks.blocksAttacks()
                .blockDelaySeconds(0f)
                .disableCooldownScale(0f)
                .addDamageReduction(DamageReduction.damageReduction()
                        .horizontalBlockingAngle(180f).base(0f).factor(part).build())
                .itemDamage(ItemDamageFunction.itemDamageFunction()
                        .threshold(Float.MAX_VALUE).base(0f).factor(0f).build())
                .build());
        inv.setItemInMainHand(epee);
    }

    /** Clic droit avec une épée pas encore préparée : elle bloque dès le clic suivant. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onRightClick(PlayerInteractEvent event) {
        if ((event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)
                && option("blocage-epee")) {
            epeeQuiBloque(event.getPlayer());
        }
    }

    // ------------------------------------------------------------------ Main secondaire et bouclier

    private void viderMainSecondaire(Player p) {
        PlayerInventory inv = p.getInventory();
        ItemStack objet = inv.getItemInOffHand();
        if (objet.getType().isAir()) {
            return;
        }
        inv.setItemInOffHand(null);
        inv.addItem(objet).values().forEach(reste -> p.getWorld().dropItemNaturally(p.getLocation(), reste));
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (option("sans-main-secondaire")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!option("sans-main-secondaire") || !(event.getWhoClicked() instanceof Player)) {
            return;
        }
        boolean caseMain = event.getClickedInventory() instanceof PlayerInventory && event.getSlot() == MAIN_SECONDAIRE;
        boolean toucheF = event.getClick() == org.bukkit.event.inventory.ClickType.SWAP_OFFHAND;
        if (caseMain || toucheF) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!option("sans-main-secondaire")) {
            return;
        }
        // Dans la vue du joueur, la main secondaire est l'emplacement brut 45.
        if (event.getView().getTopInventory().getType() == org.bukkit.event.inventory.InventoryType.CRAFTING
                && event.getRawSlots().contains(45)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onCraft(PrepareItemCraftEvent event) {
        ItemStack resultat = event.getInventory().getResult();
        if (resultat != null && resultat.getType() == Material.SHIELD && option("sans-bouclier")) {
            event.getInventory().setResult(null);
        }
    }

    // ------------------------------------------------------------------ Régénération et pommes

    /** 1.8 : 1 PV toutes les 4 s avec 9 cuisses ou plus, jamais la régénération rapide d'aujourd'hui. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRegen(EntityRegainHealthEvent event) {
        if (event.getRegainReason() != EntityRegainHealthEvent.RegainReason.SATIATED
                || !(event.getEntity() instanceof Player p) || !option("regen-ancienne")) {
            return;
        }
        long maintenant = System.currentTimeMillis();
        long delai = (long) (nombre("regen-secondes", 4.0) * 1000);
        Long avant = derniereRegen.get(p.getUniqueId());
        if (p.getFoodLevel() < 18 || (avant != null && maintenant - avant < delai)) {
            event.setCancelled(true);
            return;
        }
        derniereRegen.put(p.getUniqueId(), maintenant);
        event.setAmount(1.0);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEat(PlayerItemConsumeEvent event) {
        if (event.getItem().getType() != Material.ENCHANTED_GOLDEN_APPLE || !option("pomme-notch-ancienne")) {
            return;
        }
        Player p = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            // 1.8 : Régénération V 30 s, Absorption I 2 min, Résistance I et Résistance au feu 5 min.
            for (PotionEffectType t : new PotionEffectType[]{PotionEffectType.REGENERATION,
                    PotionEffectType.ABSORPTION, PotionEffectType.RESISTANCE, PotionEffectType.FIRE_RESISTANCE}) {
                p.removePotionEffect(t);
            }
            p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 30 * 20, 4));
            p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 120 * 20, 0));
            p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 300 * 20, 0));
            p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 300 * 20, 0));
        });
    }
}
