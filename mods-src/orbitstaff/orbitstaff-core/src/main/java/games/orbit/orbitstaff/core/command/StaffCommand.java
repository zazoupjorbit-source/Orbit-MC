package games.orbit.orbitstaff.core.command;

import games.orbit.orbitstaff.core.OrbitStaffServices;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /staff : active ou desactive le mode staff. */
public final class StaffCommand implements CommandExecutor {

    private final OrbitStaffServices services;

    public StaffCommand(OrbitStaffServices services) {
        this.services = services;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            services.messages().send(sender, "general.player-not-found");
            return true;
        }
        if (!player.hasPermission("orbitstaff.staffmode")) {
            services.messages().send(sender, "general.no-permission");
            return true;
        }
        services.staffMode().toggle(player);
        return true;
    }
}
