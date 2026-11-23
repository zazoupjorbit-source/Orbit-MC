package fr.orbit.survie;

import fr.orbit.survie.ile.Canvas;
import fr.orbit.survie.ile.IleService;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * PNJ et panneaux flottants de l'île. Ils ne sont jamais enregistrés avec le monde (non persistants) :
 * toutes les 5 s, ce qui manque dans un tronçon chargé est remis. Pas de doublon possible au redémarrage.
 */
public final class Pnj implements Listener {

    private final OrbitSurvie plugin;
    private final IleService ile;
    private final NamespacedKey tag;
    private final Map<String, UUID> vivants = new HashMap<>();
    private final Map<UUID, Long> clics = new HashMap<>();
    private BiConsumer<org.bukkit.entity.Player, String> action = (p, id) -> {};

    public Pnj(OrbitSurvie plugin, IleService ile) {
        this.plugin = plugin;
        this.ile = ile;
        this.tag = new NamespacedKey(plugin, "pnj");
    }

    public void action(BiConsumer<org.bukkit.entity.Player, String> action) {
        this.action = action;
    }

    public void tick() {
        World w = org.bukkit.Bukkit.getWorld(ile.worldName());
        if (w == null) {
            return;
        }
        for (Canvas.Pnj p : ile.reperes().pnjs()) {
            assurer(w, "pnj:" + p.id(), p.x(), p.y(), p.z(), loc -> w.spawn(loc, Villager.class, v -> {
                v.setAI(false);
                v.setGravity(false);
                v.setInvulnerable(true);
                v.setSilent(true);
                v.setCollidable(false);
                v.setPersistent(false);
                v.setRemoveWhenFarAway(false);
                v.customName(Msg.raw(p.nom()));
                v.setCustomNameVisible(true);
                Villager.Profession metier = Registry.VILLAGER_PROFESSION.get(NamespacedKey.minecraft(p.metier()));
                if (metier != null) {
                    v.setProfession(metier);
                }
                v.setVillagerLevel(5);
                v.getPersistentDataContainer().set(tag, PersistentDataType.STRING, p.id());
            }), p.yaw());
        }
        for (Canvas.Panneau p : ile.reperes().panneaux()) {
            assurer(w, "panneau:" + p.id(), p.x(), p.y(), p.z(), loc -> w.spawn(loc, TextDisplay.class, t -> {
                t.text(Msg.raw(p.texte()));
                t.setBillboard(Display.Billboard.CENTER);
                t.setPersistent(false);
                t.setShadowed(true);
                t.setBackgroundColor(Color.fromARGB(110, 10, 20, 40));
                t.getPersistentDataContainer().set(tag, PersistentDataType.STRING, "panneau");
            }), 0f);
        }
    }

    private void assurer(World w, String cle, double x, double y, double z,
                         java.util.function.Function<Location, Entity> creer, float yaw) {
        if (!w.isChunkLoaded((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4)) {
            return;
        }
        UUID id = vivants.get(cle);
        Entity e = id == null ? null : w.getEntity(id);
        if (e != null && e.isValid()) {
            return;
        }
        Entity neuf = creer.apply(new Location(w, x, y, z, yaw, 0f));
        vivants.put(cle, neuf.getUniqueId());
    }

    private final Map<String, org.bukkit.Particle> particules = new HashMap<>();

    /** Particules d'ambiance (portail, fontaine, tour…), seulement si un joueur est à moins de 48 blocs. */
    public void ambiance() {
        World w = org.bukkit.Bukkit.getWorld(ile.worldName());
        if (w == null || w.getPlayers().isEmpty()) {
            return;
        }
        for (Canvas.Particule p : ile.reperes().particules()) {
            org.bukkit.Particle type = particules.computeIfAbsent(p.type(), n -> {
                org.bukkit.Particle t = Registry.PARTICLE_TYPE.get(NamespacedKey.minecraft(n.toLowerCase()));
                if (t == null) {
                    plugin.getLogger().warning("Particule inconnue : " + n);
                }
                return t;
            });
            if (type == null || type.getDataType() != Void.class) {
                continue;
            }
            Location l = new Location(w, p.x(), p.y(), p.z());
            boolean proche = false;
            for (org.bukkit.entity.Player pl : w.getPlayers()) {
                if (pl.getLocation().distanceSquared(l) < 48 * 48) {
                    proche = true;
                    break;
                }
            }
            if (proche) {
                w.spawnParticle(type, l, p.nombre(), p.ex(), p.ey(), p.ez(), p.vitesse());
            }
        }
    }

    /** Retire tous les PNJ et panneaux (arrêt du plugin, ou avant de les reposer). */
    public void retirer() {
        World w = org.bukkit.Bukkit.getWorld(ile.worldName());
        if (w != null) {
            for (Entity e : w.getEntities()) {
                if (e.getPersistentDataContainer().has(tag, PersistentDataType.STRING)) {
                    e.remove();
                }
            }
        }
        vivants.clear();
    }

    private String id(Entity e) {
        return e.getPersistentDataContainer().get(tag, PersistentDataType.STRING);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClic(PlayerInteractEntityEvent event) {
        String id = id(event.getRightClicked());
        if (id == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = clics.put(event.getPlayer().getUniqueId(), now);
        if (last != null && now - last < 400) {
            return;
        }
        action.accept(event.getPlayer(), id);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDegat(EntityDamageEvent event) {
        if (id(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }
}
