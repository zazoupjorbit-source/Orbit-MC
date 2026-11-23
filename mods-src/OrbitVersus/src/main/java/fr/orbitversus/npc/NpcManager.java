package fr.orbitversus.npc;

import fr.orbitversus.Msg;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.NamespacedKey;

/**
 * PNJ de matchmaking : un villageois figé et invulnérable, tagué via PDC (persiste avec l'entité,
 * donc survit aux redémarrages). Clic droit dessus → menu des files.
 */
public final class NpcManager {

    private final NamespacedKey key;

    public NpcManager(Plugin plugin) {
        this.key = new NamespacedKey(plugin, "versus_npc");
    }

    /** Fait apparaître le PNJ Versus à un emplacement. */
    public Villager spawn(Location loc) {
        Villager npc = (Villager) loc.getWorld().spawnEntity(loc, EntityType.VILLAGER);
        npc.setAI(false);
        npc.setInvulnerable(true);
        npc.setCollidable(false);
        npc.setSilent(true);
        npc.setPersistent(true);
        npc.customName(MiniMessage.miniMessage().deserialize(Msg.GRAD + "<bold>⚔ Mode Versus</bold></gradient>"));
        npc.setCustomNameVisible(true);
        npc.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        return npc;
    }

    public boolean isNpc(Entity entity) {
        return entity != null
                && entity.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }
}
