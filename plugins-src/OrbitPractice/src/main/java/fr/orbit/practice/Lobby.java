package fr.orbit.practice;

import fr.orbit.practice.arena.ArenaBuilder;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;

/** Le hub du Practice : état du joueur hors combat et objets de la barre d'action. */
public final class Lobby {

    public static final String ACTION_MENU = "menu";
    public static final String ACTION_CASUAL = "casual";
    public static final String ACTION_TOP = "top";
    public static final String ACTION_LEAVE = "quitter";
    public static final String ACTION_HCF = "hcf";

    private final Plugin plugin;
    private final ArenaBuilder builder;
    private final NamespacedKey actionKey;

    public Lobby(Plugin plugin, ArenaBuilder builder) {
        this.plugin = plugin;
        this.builder = builder;
        this.actionKey = new NamespacedKey(plugin, "action");
    }

    /** Remet le joueur dans son état de hub : soigné, mode aventure, objets du hub, au centre du Vestiaire. */
    public void send(Player p) {
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        p.setHealth(max != null ? max.getValue() : 20.0);
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setFireTicks(0);
        p.setFallDistance(0f);
        p.setAbsorptionAmount(0);
        p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType()));
        p.setCooldown(Material.ENDER_PEARL, 0);
        p.setGameMode(GameMode.ADVENTURE);
        p.setMaximumNoDamageTicks(20);
        giveItems(p, false);
        Location hub = hub();
        if (hub != null) {
            p.teleport(hub);
        }
    }

    /**
     * Point d'arrivée hors combat : celui réglé dans la config ({@code hub}), sinon le spawn du monde
     * principal (la map posée à la main sur l'instance).
     */
    public Location hub() {
        String worldName = plugin.getConfig().getString("hub.monde");
        org.bukkit.World world = worldName == null ? null : org.bukkit.Bukkit.getWorld(worldName);
        if (world != null) {
            return new Location(world, plugin.getConfig().getDouble("hub.x"), plugin.getConfig().getDouble("hub.y"),
                    plugin.getConfig().getDouble("hub.z"), (float) plugin.getConfig().getDouble("hub.yaw"), 0f);
        }
        Location spawn = org.bukkit.Bukkit.getWorlds().get(0).getSpawnLocation();
        return new Location(spawn.getWorld(), spawn.getBlockX() + 0.5, spawn.getY(), spawn.getBlockZ() + 0.5, spawn.getYaw(), 0f);
    }

    /** Enregistre le point de hub dans la config. */
    public void setHub(Location loc) {
        plugin.getConfig().set("hub.monde", loc.getWorld().getName());
        plugin.getConfig().set("hub.x", Math.round(loc.getX() * 100) / 100.0);
        plugin.getConfig().set("hub.y", Math.round(loc.getY() * 100) / 100.0);
        plugin.getConfig().set("hub.z", Math.round(loc.getZ() * 100) / 100.0);
        plugin.getConfig().set("hub.yaw", Math.round(loc.getYaw()));
        plugin.saveConfig();
    }

    /** Le hub construit par code (« le Vestiaire »), pour /practice hub generer. */
    public Location generatedHub() {
        return builder.hubSpawn();
    }

    public void giveItems(Player p, boolean queued) {
        p.getInventory().clear();
        p.getInventory().setItem(0, item(Material.DIAMOND_SWORD, Msg.OR + "<bold>Classé",
                "<gray>Clic droit : combats avec Elo, 10 kits.", ACTION_MENU));
        p.getInventory().setItem(1, item(Material.IRON_SWORD, Msg.BLEU + "<bold>Casual",
                "<gray>Clic droit : combats sans Elo, pour s'entraîner.", ACTION_CASUAL));
        if (HcfZone.active()) {
            p.getInventory().setItem(2, item(Material.IRON_CHESTPLATE, "<red><bold>Mode HCF",
                    "<gray>Clic droit : factions, claims, KOTH (/hcf).", ACTION_HCF));
        }
        p.getInventory().setItem(4, item(Material.BOOK, Msg.BLEU + "<bold>Classement",
                "<gray>Clic droit : ton Elo dans chaque kit.", ACTION_TOP));
        if (queued) {
            p.getInventory().setItem(8, item(Material.RED_DYE, "<red><bold>Quitter la file",
                    "<gray>Clic droit : annuler la recherche.", ACTION_LEAVE));
        }
        p.getInventory().setHeldItemSlot(0);
    }

    private ItemStack item(Material material, String name, String lore, String action) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Msg.item(name));
        meta.lore(List.of(Msg.item(lore)));
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    /** Action portée par un objet du hub, ou null. */
    public String action(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
    }
}
