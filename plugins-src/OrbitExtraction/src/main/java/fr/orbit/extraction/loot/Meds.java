package fr.orbit.extraction.loot;

import fr.orbit.extraction.Keys;
import fr.orbit.extraction.OrbitExtraction;
import fr.orbit.extraction.util.Text;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Objets de soin (« soin:bandage », « soin:medkit ») : clic droit, on reste quelques secondes sans changer d'objet
 * ni prendre de coup, et la vie remonte. Un coup reçu interrompt le soin sans consommer l'objet.
 */
public final class Meds implements Listener {

    public record MedDef(String id, String name, Material material, double heal, int useTicks) {
    }

    private static final class Use {
        final MedDef def;
        final int slot;
        int elapsed;

        Use(MedDef def, int slot) {
            this.def = def;
            this.slot = slot;
        }
    }

    private static final int STEP = 2;

    private final OrbitExtraction plugin;
    private final Map<String, MedDef> defs = new LinkedHashMap<>();
    private final Map<UUID, Use> using = new HashMap<>();

    public Meds(OrbitExtraction plugin) {
        this.plugin = plugin;
    }

    public void load(ConfigurationSection c) {
        defs.clear();
        if (c == null) {
            return;
        }
        for (String id : c.getKeys(false)) {
            ConfigurationSection s = c.getConfigurationSection(id);
            if (s == null) {
                continue;
            }
            Material m = Material.matchMaterial(s.getString("materiau", "PAPER"));
            defs.put(id.toLowerCase(Locale.ROOT), new MedDef(id.toLowerCase(Locale.ROOT), s.getString("nom", id),
                    m == null || !m.isItem() ? Material.PAPER : m, Math.max(1, s.getDouble("vie", 6)),
                    Math.max(STEP, (int) Math.round(s.getDouble("secondes", 3) * 20))));
        }
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, STEP);
    }

    public MedDef def(String id) {
        return id == null ? null : defs.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<MedDef> defs() {
        return defs.values();
    }

    public ItemStack item(MedDef d, int amount) {
        ItemStack it = new ItemStack(d.material(), Math.max(1, Math.min(amount, d.material().getMaxStackSize())));
        it.editMeta(m -> {
            m.displayName(Text.item("<#E8695C><b>" + Text.esc(d.name()) + "</b>"));
            m.lore(Text.lore(List.of(
                    "<#8A919B>Rend <#E23B3B>" + fmt(d.heal() / 2) + " ❤</#E23B3B> en <#D7DCE2>" + fmt(d.useTicks() / 20.0) + " s",
                    "<#8A919B>Un coup reçu interrompt le soin.",
                    "<#5A6270>Clic droit : se soigner")));
            m.getPersistentDataContainer().set(Keys.MED, PersistentDataType.STRING, d.id());
            m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        });
        it.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData().addString("orbitguns:" + d.id()).build());
        return it;
    }

    public MedDef defOf(ItemStack it) {
        if (it == null || !it.hasItemMeta()) {
            return null;
        }
        return def(it.getItemMeta().getPersistentDataContainer().get(Keys.MED, PersistentDataType.STRING));
    }

    private static String fmt(double v) {
        return (v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.1f", v)).replace('.', ',');
    }

    private static double maxHealth(Player p) {
        AttributeInstance a = p.getAttribute(Attribute.MAX_HEALTH);
        return a == null ? 20 : a.getValue();
    }

    private void tick() {
        Iterator<Map.Entry<UUID, Use>> it = using.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Use> en = it.next();
            Player p = Bukkit.getPlayer(en.getKey());
            Use u = en.getValue();
            if (p == null || p.isDead()) {
                it.remove();
                continue;
            }
            ItemStack held = p.getInventory().getItemInMainHand();
            MedDef d = defOf(held);
            if (d != u.def || p.getInventory().getHeldItemSlot() != u.slot) {
                it.remove();
                stop(p, "<#C0392B>Soin interrompu");
                continue;
            }
            u.elapsed += STEP;
            if (u.elapsed >= d.useTicks()) {
                it.remove();
                held.setAmount(held.getAmount() - 1);
                p.setHealth(Math.min(maxHealth(p), p.getHealth() + d.heal()));
                p.removePotionEffect(PotionEffectType.SLOWNESS);
                p.getWorld().spawnParticle(Particle.HEART, p.getLocation().add(0, 1.9, 0), 4, 0.3, 0.2, 0.3, 0);
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.8f);
                Text.bar(p, "<#2ECC71>+" + fmt(d.heal() / 2) + " ❤</#2ECC71> <#8A919B>" + Text.esc(d.name()));
                continue;
            }
            Text.bar(p, "<#E8695C>" + Text.esc(d.name()) + "</#E8695C>  " + Text.gauge(u.elapsed / (double) d.useTicks(), 16, "#2ECC71", "#3A3F47")
                    + "  <#8A919B>" + fmt((d.useTicks() - u.elapsed) / 20.0) + " s");
            if (u.elapsed % 10 == 0) {
                p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.5f, 1.3f);
            }
        }
    }

    private static void stop(Player p, String bar) {
        p.removePotionEffect(PotionEffectType.SLOWNESS);
        Text.bar(p, bar);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        MedDef d = defOf(e.getItem());
        if (d == null) {
            return;
        }
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (using.containsKey(p.getUniqueId())) {
            return;
        }
        if (p.getHealth() >= maxHealth(p) - 0.01) {
            Text.bar(p, "<#8A919B>Tu n'es pas blessé.");
            return;
        }
        using.put(p.getUniqueId(), new Use(d, p.getInventory().getHeldItemSlot()));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, d.useTicks() + 10, 2, false, false, false));
        p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.7f, 1f);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && e.getFinalDamage() > 0 && using.remove(p.getUniqueId()) != null) {
            stop(p, "<#C0392B>Soin interrompu par un coup");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        using.remove(e.getPlayer().getUniqueId());
    }
}
