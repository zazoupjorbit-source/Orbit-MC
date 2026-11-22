package fr.orbit.devcore.npc;

import fr.orbit.devcore.storage.StoredLocation;
import org.bukkit.entity.EntityType;

/**
 * Définition d'un PNJ. Il a une seule cible :
 * soit un serveur du proxy ({@link #server()}), soit un monde de ce serveur ({@link #world()}).
 */
public final class ServerNpc {

    private final String id;
    private String server;
    private String world;
    private String displayName;
    private final EntityType type;
    private String profession;
    private StoredLocation location;

    public ServerNpc(String id, String server, String world, String displayName, EntityType type, String profession,
                     StoredLocation location) {
        this.id = id;
        this.server = server;
        this.world = world;
        this.displayName = displayName;
        this.type = type;
        this.profession = profession;
        this.location = location;
    }

    public String id() {
        return id;
    }

    /** Serveur du proxy ciblé, ou {@code null} si le PNJ cible un monde. */
    public String server() {
        return server;
    }

    /** Monde ciblé, ou {@code null} si le PNJ cible un serveur du proxy. */
    public String world() {
        return world;
    }

    public void targetServer(String server) {
        this.server = server;
        this.world = null;
    }

    public void targetWorld(String world) {
        this.world = world;
        this.server = null;
    }

    /** Cible lisible pour les messages : « hcf » ou « monde:reborn ». */
    public String targetLabel() {
        return world != null ? "monde:" + world : server;
    }

    public String displayName() {
        return displayName;
    }

    public void displayName(String displayName) {
        this.displayName = displayName;
    }

    public EntityType type() {
        return type;
    }

    public String profession() {
        return profession;
    }

    public void profession(String profession) {
        this.profession = profession;
    }

    public StoredLocation location() {
        return location;
    }

    public void location(StoredLocation location) {
        this.location = location;
    }
}
