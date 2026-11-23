package games.orbit.orbitstaff.core.command;

import games.orbit.orbitstaff.core.OrbitStaffServices;
import games.orbit.orbitstaff.core.api.dto.GenerateCodePayload;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.UUID;

/** /discord link|unlink|status : liaison du compte Minecraft au compte Discord. */
public final class DiscordLinkCommand implements CommandExecutor {

    private final OrbitStaffServices services;

    public DiscordLinkCommand(OrbitStaffServices services) {
        this.services = services;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            services.messages().send(sender, "general.player-not-found");
            return true;
        }
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "link";
        if (!sub.equals("link")) {
            sender.sendMessage(Component.text(
                    "Utilisez /discord link, puis validez depuis Discord.", NamedTextColor.YELLOW));
            return true;
        }

        String correlationId = UUID.randomUUID().toString();
        services.apiExecutor().<String>call(
                () -> services.apiClient().generateLinkCode(
                        new GenerateCodePayload(player.getUniqueId().toString()), correlationId),
                code -> sender.sendMessage(Component.text(
                        "Code de liaison (a saisir sur Discord) : " + code, NamedTextColor.GREEN)),
                error -> CommandSupport.handleApiError(services.messages(), sender, error));
        return true;
    }
}
