package fr.orbit.devcore.command;

import fr.orbit.devcore.OrbitDevCore;
import fr.orbit.devcore.cosmetic.CosmeticType;
import fr.orbit.devcore.message.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** /cosmetiques [catégorie|retirer] : ouvre le menu des cosmétiques. */
public final class CosmeticCommand implements CommandExecutor, TabCompleter {

    private static final Map<String, CosmeticType> ALIASES = Map.ofEntries(
            Map.entry("trainees", CosmeticType.TRAIL), Map.entry("trainee", CosmeticType.TRAIL),
            Map.entry("trails", CosmeticType.TRAIL), Map.entry("trail", CosmeticType.TRAIL),
            Map.entry("auras", CosmeticType.AURA), Map.entry("aura", CosmeticType.AURA),
            Map.entry("chapeaux", CosmeticType.HAT), Map.entry("chapeau", CosmeticType.HAT),
            Map.entry("hats", CosmeticType.HAT), Map.entry("hat", CosmeticType.HAT),
            Map.entry("dos", CosmeticType.BACK), Map.entry("accessoires", CosmeticType.BACK),
            Map.entry("backs", CosmeticType.BACK), Map.entry("back", CosmeticType.BACK),
            Map.entry("familiers", CosmeticType.PET), Map.entry("familier", CosmeticType.PET),
            Map.entry("pets", CosmeticType.PET), Map.entry("pet", CosmeticType.PET),
            Map.entry("ballons", CosmeticType.BALLOON), Map.entry("ballon", CosmeticType.BALLOON),
            Map.entry("balloons", CosmeticType.BALLOON), Map.entry("balloon", CosmeticType.BALLOON),
            Map.entry("montures", CosmeticType.MOUNT), Map.entry("monture", CosmeticType.MOUNT),
            Map.entry("mounts", CosmeticType.MOUNT), Map.entry("mount", CosmeticType.MOUNT));
    private static final List<String> SUGGESTIONS = List.of("trainees", "auras", "chapeaux", "dos", "ballons", "familiers", "montures", "retirer");

    private final OrbitDevCore plugin;

    public CosmeticCommand(OrbitDevCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "general.players-only");
            return true;
        }
        if (!player.hasPermission("orbitdev.cosmetics")) {
            plugin.messages().send(player, "general.no-permission");
            return true;
        }
        if (!plugin.cosmetics().enabled()) {
            plugin.messages().send(player, "cosmetics.lobby-only");
            return true;
        }
        if (args.length == 0) {
            plugin.cosmeticMenu().open(player);
            return true;
        }
        String arg = normalize(args[0]);
        if (arg.equals("retirer") || arg.equals("off") || arg.equals("clear")) {
            plugin.cosmetics().unequipAll(player);
            plugin.messages().send(player, "cosmetics.unequipped-all");
            return true;
        }
        CosmeticType type = ALIASES.get(arg);
        if (type == null) {
            plugin.messages().send(player, "cosmetics.unknown-category", Messages.ph("category", args[0]));
            return true;
        }
        plugin.cosmeticMenu().open(player, type);
        return true;
    }

    /** Minuscules sans accents : « Traînées » → « trainees ». */
    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return args.length == 1 ? Completions.filter(SUGGESTIONS, args[0]) : List.of();
    }
}
