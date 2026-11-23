package games.orbit.orbitstaff.core.command;

import games.orbit.orbitstaff.core.OrbitStaffPlugin;
import games.orbit.orbitstaff.core.OrbitStaffServices;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.Locale;

/** /orbitstaff reload|status|integrations|diagnostics : administration du plugin. */
public final class OrbitStaffCommand implements CommandExecutor {

    private final OrbitStaffPlugin plugin;
    private final OrbitStaffServices services;

    public OrbitStaffCommand(OrbitStaffPlugin plugin, OrbitStaffServices services) {
        this.plugin = plugin;
        this.services = services;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("orbitstaff.admin")) {
            services.messages().send(sender, "general.no-permission");
            return true;
        }
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "status";
        switch (sub) {
            case "reload" -> {
                plugin.reloadSafe();
                sender.sendMessage(Component.text("OrbitStaff : configuration rechargee.", NamedTextColor.GREEN));
            }
            case "integrations" ->
                    sender.sendMessage(Component.text(
                            "Integrations : aucune active (arrivent en Phase 4).", NamedTextColor.GRAY));
            case "diagnostics" -> {
                sender.sendMessage(Component.text("Diagnostics OrbitStaff", NamedTextColor.GOLD));
                sender.sendMessage(Component.text("  Cle de service : "
                        + (services.config().hasServiceKey() ? "presente" : "ABSENTE"), NamedTextColor.GRAY));
                sender.sendMessage(Component.text("  API : " + services.config().apiBaseUrl(), NamedTextColor.GRAY));
            }
            default -> {
                sender.sendMessage(Component.text("OrbitStaff " + plugin.getPluginMeta().getVersion(), NamedTextColor.GOLD));
                sender.sendMessage(Component.text("  Serveur : " + services.config().serverId(), NamedTextColor.GRAY));
                sender.sendMessage(Component.text("  API : " + services.config().apiBaseUrl(), NamedTextColor.GRAY));
            }
        }
        return true;
    }
}
