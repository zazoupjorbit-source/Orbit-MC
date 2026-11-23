package fr.orbit.mmo.ambient;

import fr.orbit.mmo.Keys;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Witch;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * PNJ de ville (forgeron, alchimiste, prêtre, runiste, hôtel des ventes) : des mannequins qui portent leur skin du
 * pack, à la place des villageois et de la sorcière du jeu. Les PNJ posés avant ce changement sont remplacés sur
 * place quand leur chunk se charge (même endroit, même nom, mêmes étiquettes) : aucune ville n'est à régénérer.
 * Le Veilleur du Pilier (squelette noir) reçoit sa tenue.
 */
public final class TownFolk implements Listener {

    /** Ce que chacun tient en main. */
    private static final Map<String, Material> HANDS = Map.of("blacksmith", Material.MACE, "alchemist", Material.GLASS_BOTTLE,
            "priest", Material.BOOK, "runesmith", Material.AMETHYST_SHARD, "merchant", Material.PAPER);
    /** Étiquette posée par l'ancien plugin (RebornMMO) sur les PNJ des premiers étages. */
    private static final NamespacedKey LEGACY = new NamespacedKey("rebornmmo", "npc");
    /** Un peu plus grands qu'un joueur : la taille d'un villageois, pour que l'écriteau reste au-dessus de la tête. */
    private static final double SCALE = 1.08;

    private final Plugin plugin;

    private TownFolk(Plugin plugin) {
        this.plugin = plugin;
    }

    public static void start(Plugin plugin) {
        TownFolk t = new TownFolk(plugin);
        plugin.getServer().getPluginManager().registerEvents(t, plugin);
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (org.bukkit.World w : Bukkit.getWorlds()) {
                migrate(w.getEntities());
            }
        });
    }

    /** Vrai si ce PNJ de ville a un skin dans le pack (il est alors un mannequin). */
    public static boolean human(String tag) {
        return tag != null && HANDS.containsKey(tag) && Looks.hasSkin(tag);
    }

    /** Pose le mannequin d'un PNJ de ville : immobile, invulnérable, enregistré avec le monde. */
    public static Mannequin spawn(Location at, String tag, Component name) {
        return at.getWorld().spawn(at, Mannequin.class, m -> {
            m.getPersistentDataContainer().set(Keys.NPC, PersistentDataType.STRING, tag);
            m.setInvulnerable(true);
            m.setSilent(true);
            m.setCollidable(false);
            m.setImmovable(true);
            m.setPersistent(true);
            m.customName(name);
            m.setCustomNameVisible(true);
            m.setDescription(Component.empty());
            try {
                m.setProfile(Looks.skin(tag));
                m.setSkinParts(com.destroystokyo.paper.SkinParts.allParts());
            } catch (RuntimeException ex) {
                Bukkit.getLogger().warning("[OrbitMMO] PNJ " + tag + " : skin refusé (" + ex.getMessage() + ").");
            }
            AttributeInstance scale = m.getAttribute(Attribute.SCALE);
            if (scale != null) {
                scale.setBaseValue(SCALE);
            }
            Material hand = HANDS.get(tag);
            if (hand != null && m.getEquipment() != null) {
                m.getEquipment().setItemInMainHand(new ItemStack(hand));
            }
        });
    }

    private static String tagOf(Entity e) {
        PersistentDataContainer pdc = e.getPersistentDataContainer();
        String tag = Keys.NPC == null ? null : pdc.get(Keys.NPC, PersistentDataType.STRING);
        return tag != null ? tag : pdc.get(LEGACY, PersistentDataType.STRING);
    }

    /** Remplace les anciens PNJ de ville de cette liste par leur mannequin et habille les Veilleurs. */
    public static void migrate(Collection<? extends Entity> entities) {
        List<Entity> old = new ArrayList<>();
        for (Entity e : entities) {
            if (!e.isValid() || !(e instanceof LivingEntity)) {
                continue;
            }
            String tag = tagOf(e);
            if (tag == null) {
                continue;
            }
            if (e instanceof WitherSkeleton ws && tag.equals("tower_ombre") && Looks.hasOutfit("veilleur")
                    && (ws.getEquipment().getHelmet() == null || ws.getEquipment().getHelmet().getType() != Material.PAPER)) {
                Looks.dress(ws, "veilleur");
                ws.getEquipment().setItemInMainHand(null);
            } else if ((e instanceof Villager || e instanceof Witch) && human(tag)) {
                old.add(e);
            }
        }
        for (Entity e : old) {
            String tag = tagOf(e);
            Component name = e.customName() != null ? e.customName() : Component.text(tag);
            Mannequin m = spawn(e.getLocation(), tag, name);
            for (String s : e.getScoreboardTags()) {
                m.addScoreboardTag(s);
            }
            e.remove();
        }
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent e) {
        List<Entity> list = new ArrayList<>(e.getEntities());
        if (!list.isEmpty()) {
            // Au tic suivant : on ne pose pas d'entité pendant que le chunk charge les siennes.
            Bukkit.getScheduler().runTask(plugin, () -> migrate(list));
        }
    }
}
