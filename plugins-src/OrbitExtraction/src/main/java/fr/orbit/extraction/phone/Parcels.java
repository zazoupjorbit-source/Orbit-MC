package fr.orbit.extraction.phone;

import fr.orbit.extraction.Keys;
import fr.orbit.extraction.OrbitExtraction;
import fr.orbit.extraction.phone.PhoneService.Offer;
import fr.orbit.extraction.util.Text;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Colis commandés depuis la tablette en raid : après un délai, une caisse tombe près du joueur. Elle fume
 * (visible de loin), le bruit de l'impact attire les infectés, et n'importe quel joueur peut la piller.
 */
public final class Parcels implements Listener {

    private static final class Parcel implements InventoryHolder {
        final String id = UUID.randomUUID().toString();
        final Location at;
        BlockDisplay crate;
        Interaction hit;
        Inventory inv;
        int landTick;
        int expireTick;

        Parcel(Location at) {
            this.at = at;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private static final int FALL_TICKS = 40;
    private static final double FALL_HEIGHT = 28;

    private final OrbitExtraction plugin;
    private final Map<String, Parcel> parcels = new HashMap<>();
    private final Map<UUID, Integer> nextOrder = new HashMap<>();
    private final Map<String, String> descriptions = new HashMap<>();
    private final Random random = new Random();
    private int delay;
    private int cooldown;
    private int lifetime;
    private double noise;

    public Parcels(OrbitExtraction plugin) {
        this.plugin = plugin;
    }

    public void load(ConfigurationSection c) {
        delay = c == null ? 20 : Math.max(0, c.getInt("delai-secondes", 20));
        cooldown = c == null ? 90 : Math.max(0, c.getInt("recharge-secondes", 90));
        lifetime = c == null ? 180 : Math.max(20, c.getInt("duree-secondes", 180));
        noise = c == null ? 40 : c.getDouble("bruit", 40);
        descriptions.clear();
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 10L);
    }

    public int delaySeconds() {
        return delay;
    }

    /** « munition:9mm:40 » → « Munitions 9 mm x40 ». */
    public String describe(String spec) {
        return descriptions.computeIfAbsent(spec, this::describeNow);   // créer une arme enregistre un chargeur : une seule fois
    }

    private String describeNow(String spec) {
        List<ItemStack> made = plugin.specs().create(spec, false);
        if (made.isEmpty()) {
            return Text.esc(spec);
        }
        int n = 0;
        for (ItemStack it : made) {
            n += it.getAmount();
        }
        ItemStack first = made.get(0);
        String name = first.hasItemMeta() && first.getItemMeta().hasDisplayName()
                ? PlainTextComponentSerializer.plainText().serialize(first.getItemMeta().displayName())
                : pretty(first.getType());
        return Text.esc(name) + (n > 1 ? " x" + n : "");
    }

    private static String pretty(Material m) {
        String s = m.name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Vrai si la commande est prise (crédits débités). */
    public boolean order(Player p, Offer o) {
        if (!plugin.raid().inRaid(p)) {
            Text.msg(p, "<#C0392B>Les colis sont largués en raid.</#C0392B> À la planque, passe par la boutique.");
            return false;
        }
        int now = Bukkit.getCurrentTick();
        Integer next = nextOrder.get(p.getUniqueId());
        if (next != null && now < next) {
            Text.msg(p, "<#C0392B>Ligne occupée.</#C0392B> Prochain colis possible dans <#D7DCE2>" + Text.clock((next - now) / 20) + "</#D7DCE2>.");
            return false;
        }
        if (!plugin.phone().pay(p, o.price())) {
            Text.msg(p, "<#C0392B>Pas assez de crédits</#C0392B> <#8A919B>(il t'en faut " + o.price() + ", tu en as "
                    + plugin.phone().credits(p) + ").");
            return false;
        }
        plugin.profiles().save(plugin.profiles().get(p));
        // Le rang raccourcit l'attente entre deux commandes (pas le délai de largage).
        int wait = (int) Math.round(cooldown * (1 - plugin.ranks().of(p).parcelCut()));
        nextOrder.put(p.getUniqueId(), now + (delay + wait) * 20);
        Text.msg(p, "Colis <#3FC46A>" + Text.esc(o.name()) + "</#3FC46A> commandé : largage dans <#D7DCE2>" + delay
                + " s</#D7DCE2> près de ta position. <#8A919B>Reste à couvert, ça va faire du bruit.");
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.4f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> deliver(p, o), delay * 20L);
        return true;
    }

    private void deliver(Player p, Offer o) {
        if (!p.isOnline() || !plugin.raid().inRaid(p)) {
            // Extrait, mort ou déconnecté avant le largage : la commande est annulée et remboursée.
            if (p.isOnline()) {
                plugin.phone().add(p, o.price());
                plugin.profiles().save(plugin.profiles().get(p));
                Text.msg(p, "Colis annulé (tu n'es plus en raid) : <#2ECC71>" + o.price() + " crédits remboursés</#2ECC71>.");
            }
            nextOrder.remove(p.getUniqueId());
            return;
        }
        if (o.vehicle() != null) {
            // Un véhicule : posé plein près du joueur, s'il y a la place.
            fr.orbit.extraction.vehicle.Vehicles.Def def = plugin.vehicles().def(o.vehicle());
            if (def == null || !plugin.vehicles().deliver(p, def)) {
                plugin.phone().add(p, o.price());
                plugin.profiles().save(plugin.profiles().get(p));
                nextOrder.remove(p.getUniqueId());
                Text.msg(p, "<#C0392B>Pas de terrain dégagé près de toi pour poser le véhicule.</#C0392B> <#2ECC71>" + o.price()
                        + " crédits remboursés</#2ECC71> <#8A919B>— recommande depuis une route ou une place.");
            }
            return;
        }
        Location ground = findGround(p.getLocation());
        Parcel pc = new Parcel(ground);
        pc.inv = Bukkit.createInventory(pc, 27, Text.mm("<#17602F>▼</#17602F> <#2B2F36>Colis — " + Text.esc(o.name())));
        for (String spec : o.contents()) {
            for (ItemStack it : plugin.specs().create(spec, true)) {
                pc.inv.addItem(it);
            }
        }
        World w = ground.getWorld();
        Location sky = ground.clone().add(0, FALL_HEIGHT, 0);
        pc.crate = w.spawn(sky, BlockDisplay.class, d -> {
            d.setBlock(Material.BARREL.createBlockData());
            d.setPersistent(false);
            d.setTransformation(new Transformation(new Vector3f(-0.45f, 0f, -0.45f), new AxisAngle4f(),
                    new Vector3f(0.9f, 0.9f, 0.9f), new AxisAngle4f()));
            d.setTeleportDuration(FALL_TICKS);
            d.setViewRange(3f);
            d.setGlowing(true);
        });
        int now = Bukkit.getCurrentTick();
        pc.landTick = now + FALL_TICKS + 2;
        pc.expireTick = pc.landTick + lifetime * 20;
        parcels.put(pc.id, pc);
        // La descente est interpolée par le client : un seul déplacement, deux ticks après l'apparition.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (pc.crate.isValid()) {
                pc.crate.teleport(ground);
            }
        }, 2L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> land(pc, p), FALL_TICKS + 2L);
        w.playSound(sky, Sound.ENTITY_PHANTOM_FLAP, 2f, 0.5f);
        Text.msg(p, "<#3FC46A>Colis en approche</#3FC46A> à <#D7DCE2>" + (int) Math.round(ground.distance(p.getLocation()))
                + " m</#D7DCE2> — suis la fumée verte.");
    }

    private void land(Parcel pc, Player owner) {
        if (!parcels.containsKey(pc.id) || !pc.crate.isValid()) {
            return;
        }
        World w = pc.at.getWorld();
        pc.hit = w.spawn(pc.at, Interaction.class, i -> {
            i.setInteractionWidth(1.1f);
            i.setInteractionHeight(1.0f);
            i.setResponsive(true);
            i.setPersistent(false);
            i.getPersistentDataContainer().set(Keys.PARCEL, PersistentDataType.STRING, pc.id);
        });
        w.playSound(pc.at, Sound.BLOCK_ANVIL_LAND, 1.6f, 0.6f);
        w.spawnParticle(Particle.CLOUD, pc.at.clone().add(0, 0.3, 0), 30, 0.5, 0.2, 0.5, 0.05);
        plugin.infected().noise(pc.at, noise, null);
        if (owner.isOnline()) {
            Text.bar(owner, "<#3FC46A>Colis au sol</#3FC46A> <#8A919B>— clic droit sur la caisse");
        }
    }

    /** Sol dégagé à quelques blocs du joueur ; à défaut, sa propre position. */
    private Location findGround(Location around) {
        World w = around.getWorld();
        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2, dist = 3 + random.nextDouble() * 5;
            int x = (int) Math.floor(around.getX() + Math.cos(angle) * dist);
            int z = (int) Math.floor(around.getZ() + Math.sin(angle) * dist);
            Block top = w.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            if (!top.getType().isSolid() || top.isLiquid() || Math.abs(top.getY() + 1 - around.getY()) > 4) {
                continue;
            }
            Block feet = top.getRelative(0, 1, 0);
            if (feet.isPassable() && !feet.isLiquid()) {
                return feet.getLocation().add(0.5, 0, 0.5);
            }
        }
        Location l = around.getBlock().getLocation().add(0.5, 0, 0.5);
        l.setYaw(0);
        l.setPitch(0);
        return l;
    }

    private void tick() {
        int now = Bukkit.getCurrentTick();
        Iterator<Parcel> it = parcels.values().iterator();
        while (it.hasNext()) {
            Parcel pc = it.next();
            if (now < pc.landTick) {
                continue;
            }
            boolean empty = pc.inv.isEmpty() && pc.inv.getViewers().isEmpty() && now > pc.landTick + 40;
            if (now >= pc.expireTick || empty || !pc.crate.isValid()) {
                remove(pc);
                it.remove();
                continue;
            }
            World w = pc.at.getWorld();
            w.spawnParticle(Particle.DUST, pc.at.clone().add(0, 1.2, 0), 6, 0.15, 0.4, 0.15, 0,
                    new Particle.DustOptions(org.bukkit.Color.fromRGB(0x3F, 0xC4, 0x6A), 1.6f), true);
            w.spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, pc.at.clone().add(0, 1.0, 0), 0, 0.0, 0.07, 0.0, 1, null, true);
        }
    }

    private void remove(Parcel pc) {
        for (org.bukkit.entity.HumanEntity v : new ArrayList<>(pc.inv.getViewers())) {
            v.closeInventory();
        }
        if (pc.crate != null && pc.crate.isValid()) {
            pc.crate.getWorld().spawnParticle(Particle.CLOUD, pc.at.clone().add(0, 0.4, 0), 12, 0.3, 0.3, 0.3, 0.02);
            pc.crate.remove();
        }
        if (pc.hit != null && pc.hit.isValid()) {
            pc.hit.remove();
        }
    }

    public void shutdown() {
        for (Parcel pc : parcels.values()) {
            remove(pc);
        }
        parcels.clear();
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof Interaction i)) {
            return;
        }
        String id = i.getPersistentDataContainer().get(Keys.PARCEL, PersistentDataType.STRING);
        Parcel pc = id == null ? null : parcels.get(id);
        if (pc == null) {
            return;
        }
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (!plugin.raid().inRaid(p)) {
            return;
        }
        p.openInventory(pc.inv);
        p.playSound(pc.at, Sound.BLOCK_BARREL_OPEN, 0.8f, 1f);
    }
}
