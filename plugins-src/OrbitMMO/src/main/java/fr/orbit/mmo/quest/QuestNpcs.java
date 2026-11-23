package fr.orbit.mmo.quest;

import fr.orbit.mmo.OrbitMMO;
import fr.orbit.mmo.quest.QuestDefs.NpcDef;
import fr.orbit.mmo.util.Text;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * PNJ de quête : posés sur leur point, recréés s'ils disparaissent (chunk déchargé, rechargement).
 * Au-dessus de la tête, « ! » (quête à prendre) ou « ? » (quelque chose à lui dire),
 * visibles seulement par les joueurs concernés.
 */
final class QuestNpcs {

    /** Marqueur à afficher pour un joueur. */
    enum Mark { NONE, OFFER, TALK }

    private static final class Live {
        final NpcDef def;
        final LivingEntity entity;
        final TextDisplay offer;
        final TextDisplay talk;

        Live(NpcDef def, LivingEntity entity, TextDisplay offer, TextDisplay talk) {
            this.def = def;
            this.entity = entity;
            this.offer = offer;
            this.talk = talk;
        }

        boolean valid() {
            return entity.isValid() && offer.isValid() && talk.isValid();
        }

        void remove() {
            entity.remove();
            offer.remove();
            talk.remove();
        }
    }

    private final OrbitMMO plugin;
    private final QuestRegistry registry;
    private final QuestPoints points;
    final NamespacedKey npcKey;
    private final Map<String, Live> live = new HashMap<>();
    /** Marqueur affiché par joueur et par PNJ (évite de renvoyer les mêmes paquets). */
    private final Map<UUID, Map<String, Mark>> shown = new HashMap<>();

    QuestNpcs(OrbitMMO plugin, QuestRegistry registry, QuestPoints points) {
        this.plugin = plugin;
        this.registry = registry;
        this.points = points;
        this.npcKey = new NamespacedKey(plugin, "quest_npc");
    }

    /** Id du PNJ de quête porté par cette entité (nul sinon). */
    String idOf(Entity e) {
        return e == null ? null : e.getPersistentDataContainer().get(npcKey, PersistentDataType.STRING);
    }

    Location location(String npcId) {
        NpcDef def = registry.npc(npcId);
        return def == null ? null : points.location(def.point());
    }

    /** Crée les PNJ manquants, retire ceux dont le point a disparu, tourne la tête vers les joueurs proches. */
    void tick() {
        for (Iterator<Map.Entry<String, Live>> it = live.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Live> e = it.next();
            NpcDef def = registry.npc(e.getKey());
            Location at = def == null ? null : points.location(def.point());
            Live l = e.getValue();
            if (def == null || at == null || !l.valid() || def != l.def
                    || !at.getWorld().equals(l.entity.getWorld()) || at.distanceSquared(l.entity.getLocation()) > 4) {
                l.remove();
                it.remove();
                forget(e.getKey());
            }
        }
        for (NpcDef def : registry.npcs()) {
            if (live.containsKey(def.id())) {
                continue;
            }
            Location at = points.location(def.point());
            if (at == null || !at.getWorld().isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) {
                continue;
            }
            Live l = spawn(def, at);
            if (l != null) {
                live.put(def.id(), l);
            }
        }
        for (Live l : live.values()) {
            lookAtNearest(l);
        }
    }

    private Live spawn(NpcDef def, Location at) {
        Class<? extends Entity> cls = def.type().getEntityClass();
        if (cls == null || !LivingEntity.class.isAssignableFrom(cls)) {
            plugin.getLogger().warning("PNJ " + def.id() + " : type " + def.type() + " impossible.");
            return null;
        }
        Entity raw = at.getWorld().spawn(at, cls, ent -> setup((LivingEntity) ent, def));
        LivingEntity body = (LivingEntity) raw;
        double top = body.getHeight() + 0.75;
        TextDisplay offer = marker(at.clone().add(0, top, 0), "<#FFD66E><b>!</b>");
        TextDisplay talk = marker(at.clone().add(0, top, 0), "<#FFD66E><b>?</b>");
        return new Live(def, body, offer, talk);
    }

    private void setup(LivingEntity e, NpcDef def) {
        e.getPersistentDataContainer().set(npcKey, PersistentDataType.STRING, def.id());
        e.setPersistent(false);
        e.setInvulnerable(true);
        e.setSilent(true);
        e.setCollidable(false);
        e.setRemoveWhenFarAway(false);
        e.customName(Text.mm("<" + def.hex() + "><b>" + Text.esc(def.name()) + "</b>"));
        e.setCustomNameVisible(true);
        if (e instanceof Mob m) {
            m.setAI(false);
        }
        if (e instanceof Mannequin mq) {
            mq.setImmovable(true);
            mq.setDescription(def.title() == null ? Component.empty() : Text.mm("<#A9B1BC>" + Text.esc(def.title())));
            String face = registry.face(def.id());
            if (def.skin() != null && !def.skin().isBlank()) {
                mq.setProfile(ResolvableProfile.resolvableProfile().name(def.skin()).build());
            } else if (face != null) {
                // « visage » : une des apparences fournies avec le jeu (le portrait du dialogue est le même visage).
                try {
                    // Un skin du pack (dessiné pour ce PNJ) ou, à défaut, une des apparences du jeu.
                    mq.setProfile(fr.orbit.mmo.ambient.Looks.hasSkin(face) ? fr.orbit.mmo.ambient.Looks.skin(face)
                            : ResolvableProfile.resolvableProfile().skinPatch(patch -> patch
                            .body(net.kyori.adventure.key.Key.key("minecraft", "entity/player/wide/" + face))
                            .model(org.bukkit.profile.PlayerTextures.SkinModel.CLASSIC)).build());
                    mq.setSkinParts(com.destroystokyo.paper.SkinParts.allParts());
                } catch (RuntimeException ex) {
                    plugin.getLogger().warning("PNJ " + def.id() + " : visage « " + face + " » refusé (" + ex.getMessage() + ").");
                }
            }
        }
        if (e instanceof Villager v && def.profession() != null) {
            Villager.Profession prof = Registry.VILLAGER_PROFESSION.get(NamespacedKey.minecraft(def.profession().toLowerCase(Locale.ROOT)));
            if (prof != null) {
                v.setProfession(prof);
                v.setVillagerLevel(5);
            }
        }
        if (def.hand() != null && e.getEquipment() != null) {
            e.getEquipment().setItemInMainHand(new ItemStack(def.hand()));
        }
    }

    private TextDisplay marker(Location at, String mini) {
        return at.getWorld().spawn(at, TextDisplay.class, d -> {
            d.getPersistentDataContainer().set(npcKey, PersistentDataType.STRING, "#marqueur");
            d.setPersistent(false);
            d.setVisibleByDefault(false);
            d.text(Text.mm(mini));
            d.setBillboard(Display.Billboard.CENTER);
            d.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0));
            d.setShadowed(true);
            d.setSeeThrough(false);
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(2.2f, 2.2f, 2.2f), new AxisAngle4f()));
        });
    }

    /** Affiche au joueur le bon marqueur au-dessus de ce PNJ. */
    void mark(Player p, String npcId, Mark mark) {
        Live l = live.get(npcId);
        if (l == null) {
            return;
        }
        Map<String, Mark> mine = shown.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>());
        if (mine.get(npcId) == mark) {
            return;
        }
        mine.put(npcId, mark);
        if (mark == Mark.OFFER) {
            p.showEntity(plugin, l.offer);
        } else {
            p.hideEntity(plugin, l.offer);
        }
        if (mark == Mark.TALK) {
            p.showEntity(plugin, l.talk);
        } else {
            p.hideEntity(plugin, l.talk);
        }
    }

    Collection<String> liveIds() {
        return live.keySet();
    }

    private void forget(String npcId) {
        for (Map<String, Mark> m : shown.values()) {
            m.remove(npcId);
        }
    }

    void quit(UUID id) {
        shown.remove(id);
    }

    private void lookAtNearest(Live l) {
        LivingEntity npc = l.entity;
        Player near = null;
        double best = 8 * 8;
        for (Player p : npc.getWorld().getPlayers()) {
            double d = p.getLocation().distanceSquared(npc.getLocation());
            if (d < best) {
                best = d;
                near = p;
            }
        }
        if (near == null) {
            return;
        }
        Vector dir = near.getEyeLocation().toVector().subtract(npc.getEyeLocation().toVector());
        if (dir.lengthSquared() < 1.0E-4) {
            return;
        }
        Location look = npc.getLocation().setDirection(dir);
        npc.setRotation(look.getYaw(), Math.max(-30, Math.min(30, look.getPitch())));
    }

    /** Retire les restes (PNJ et marqueurs) d'une ancienne session du serveur. */
    void purge(Iterable<? extends Entity> entities) {
        for (Entity e : entities) {
            if (idOf(e) == null) {
                continue;
            }
            boolean ours = false;
            for (Live l : live.values()) {
                if (l.entity.equals(e) || l.offer.equals(e) || l.talk.equals(e)) {
                    ours = true;
                    break;
                }
            }
            if (!ours) {
                e.remove();
            }
        }
    }

    void removeAll() {
        for (Live l : live.values()) {
            l.remove();
        }
        live.clear();
        shown.clear();
    }
}
