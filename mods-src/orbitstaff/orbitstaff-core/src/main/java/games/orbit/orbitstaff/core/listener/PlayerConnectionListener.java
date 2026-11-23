package games.orbit.orbitstaff.core.listener;

import games.orbit.orbitstaff.core.freeze.FreezeManager;
import games.orbit.orbitstaff.core.i18n.Messages;
import games.orbit.orbitstaff.core.staff.StaffModeManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * A la connexion : restaure l'etat du mode staff et rappelle le gel eventuel.
 */
public final class PlayerConnectionListener implements Listener {

    private final StaffModeManager staffModeManager;
    private final FreezeManager freezeManager;
    private final Messages messages;

    public PlayerConnectionListener(StaffModeManager staffModeManager, FreezeManager freezeManager, Messages messages) {
        this.staffModeManager = staffModeManager;
        this.freezeManager = freezeManager;
        this.messages = messages;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        staffModeManager.restoreOnJoin(player);
        if (freezeManager.isFrozen(player.getUniqueId())) {
            messages.send(player, "freeze.frozen-target");
        }
    }
}
