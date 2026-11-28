package fr.orbitgames.hcf.core.server;

import fr.orbitgames.hcf.api.util.Text;
import fr.orbitgames.hcf.core.OrbitHCFCorePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Locale;

/**
 * Panneaux du HCF : boutique ([Achat] / [Vente]) et ascenseurs ([Ascenseur] Haut / Bas).
 * Les donnees sont gardees dans le panneau lui-meme : le texte affiche n'est que de l'habillage.
 */
public class SignListener implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final OrbitHCFCorePlugin plugin;
    private final NamespacedKey shopKey;
    private final NamespacedKey elevatorKey;

    public SignListener(OrbitHCFCorePlugin plugin) {
        this.plugin = plugin;
        this.shopKey = new NamespacedKey(plugin, "boutique");
        this.elevatorKey = new NamespacedKey(plugin, "ascenseur");
    }

    private static Component text(String legacy) {
        return LEGACY.deserialize(Text.color(legacy));
    }

    private void say(Player player, String message) {
        player.sendMessage(Text.color(plugin.getMessages().getPrefix() + message));
    }

    // ------------------------------------------------------------------ Creation

    /** Pose un panneau mural de boutique (generateur du spawn). */
    public void placeShopSign(Block block, BlockFace facing, boolean buy, Material item, int amount, long price, String label) {
        WallSign data = (WallSign) Material.OAK_WALL_SIGN.createBlockData();
        data.setFacing(facing);
        block.setBlockData(data, false);
        if (block.getState() instanceof Sign sign) {
            writeShop(sign, buy, item, amount, price, label);
        }
    }

    private void writeShop(Sign sign, boolean buy, Material item, int amount, long price, String label) {
        sign.getPersistentDataContainer().set(shopKey, PersistentDataType.STRING,
                (buy ? "achat" : "vente") + "|" + item.name() + "|" + amount + "|" + price);
        var side = sign.getSide(Side.FRONT);
        side.line(0, text(buy ? "&2&l[Achat]" : "&4&l[Vente]"));
        side.line(1, text("&0" + amount + " x"));
        side.line(2, text("&0" + (label == null || label.isEmpty() ? pretty(item) : label)));
        side.line(3, text("&1" + plugin.getEconomy().format(price)));
        sign.setWaxed(true);
        sign.update(true, false);
    }

    private static String pretty(Material item) {
        String name = item.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return name.length() > 15 ? name.substring(0, 15) : name;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        String first = plain(event.line(0)).trim();
        String second = plain(event.line(1)).trim();
        // Ascenseur : tout le monde peut en poser la ou il peut construire.
        if (first.equalsIgnoreCase("[Ascenseur]") || first.equalsIgnoreCase("[Elevator]")) {
            boolean up = second.equalsIgnoreCase("haut") || second.equalsIgnoreCase("up");
            boolean down = second.equalsIgnoreCase("bas") || second.equalsIgnoreCase("down");
            if (!up && !down) {
                say(event.getPlayer(), "&cLigne 2 : &fHaut&c ou &fBas&c.");
                return;
            }
            event.line(0, text("&9&l[Ascenseur]"));
            event.line(1, text(up ? "&0Haut" : "&0Bas"));
            Block block = event.getBlock();
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (block.getState() instanceof Sign sign) {
                    sign.getPersistentDataContainer().set(elevatorKey, PersistentDataType.STRING, up ? "haut" : "bas");
                    sign.setWaxed(true);
                    sign.update(true, false);
                }
            });
            say(event.getPlayer(), "&aAscenseur pose. Clic droit pour " + (up ? "monter" : "descendre") + ".");
            return;
        }
        boolean buy = first.equalsIgnoreCase("[Achat]") || first.equalsIgnoreCase("[Buy]");
        boolean sell = first.equalsIgnoreCase("[Vente]") || first.equalsIgnoreCase("[Sell]");
        if (!buy && !sell) {
            return;
        }
        if (!event.getPlayer().hasPermission("orbithcf.admin")) {
            event.setCancelled(true);
            say(event.getPlayer(), "&cSeul un admin pose un panneau de boutique.");
            return;
        }
        Material item = Material.matchMaterial(plain(event.line(2)).trim().replace(' ', '_'));
        int amount;
        long price;
        try {
            amount = Integer.parseInt(second);
            price = Long.parseLong(plain(event.line(3)).trim().replace("$", ""));
        } catch (NumberFormatException e) {
            item = null;
            amount = 0;
            price = 0;
        }
        if (item == null || !item.isItem() || amount <= 0 || price <= 0) {
            say(event.getPlayer(), "&cPanneau de boutique : ligne 2 quantite, ligne 3 materiau, ligne 4 prix.");
            return;
        }
        Block block = event.getBlock();
        Material material = item;
        int quantity = amount;
        long cost = price;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (block.getState() instanceof Sign sign) {
                writeShop(sign, buy, material, quantity, cost, null);
            }
        });
        say(event.getPlayer(), "&aPanneau de boutique pose.");
    }

    private static String plain(Component component) {
        return component == null ? "" : PlainTextComponentSerializer.plainText().serialize(component);
    }

    // ------------------------------------------------------------------ Utilisation

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND
                || event.getClickedBlock() == null || !(event.getClickedBlock().getState() instanceof Sign sign)) {
            return;
        }
        String shop = sign.getPersistentDataContainer().get(shopKey, PersistentDataType.STRING);
        String elevator = sign.getPersistentDataContainer().get(elevatorKey, PersistentDataType.STRING);
        if (shop == null && elevator == null) {
            return;
        }
        event.setCancelled(true);
        if (elevator != null) {
            ride(event.getPlayer(), sign.getBlock(), elevator.equals("haut"));
        } else {
            trade(event.getPlayer(), shop);
        }
    }

    private void trade(Player player, String data) {
        String[] parts = data.split("\\|");
        Material item = parts.length == 4 ? Material.getMaterial(parts[1]) : null;
        if (item == null) {
            say(player, "&cPanneau de boutique illisible.");
            return;
        }
        int amount = Integer.parseInt(parts[2]);
        long price = Long.parseLong(parts[3]);
        var economy = plugin.getEconomy();
        if (parts[0].equals("achat")) {
            if (!economy.withdraw(player.getUniqueId(), price)) {
                say(player, "&cIl te faut &f" + economy.format(price) + "&c (tu as &f"
                        + economy.format(economy.getBalance(player.getUniqueId())) + "&c).");
                return;
            }
            player.getInventory().addItem(new ItemStack(item, amount)).values()
                    .forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.2f);
            say(player, "&aAchete : &f" + amount + " x " + pretty(item) + " &apour &f" + economy.format(price) + "&a.");
            return;
        }
        ItemStack plainItem = new ItemStack(item);
        int owned = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack != null && stack.isSimilar(plainItem)) {
                owned += stack.getAmount();
            }
        }
        if (owned == 0) {
            say(player, "&cTu n'as pas de &f" + pretty(item) + "&c a vendre.");
            return;
        }
        // Accroupi : tout vendre. Sinon un lot (ou ce qu'il reste, au prorata).
        int sold = player.isSneaking() ? owned : Math.min(owned, amount);
        long gain = price * sold / amount;
        if (gain <= 0) {
            say(player, "&cPas assez pour une vente (lot de &f" + amount + "&c).");
            return;
        }
        player.getInventory().removeItem(new ItemStack(item, sold));
        economy.deposit(player.getUniqueId(), gain);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 0.8f);
        say(player, "&aVendu : &f" + sold + " x " + pretty(item) + " &apour &f" + economy.format(gain)
                + "&a. &7(Accroupi = tout vendre)");
    }

    private void ride(Player player, Block signBlock, boolean up) {
        World world = signBlock.getWorld();
        int x = signBlock.getX();
        int z = signBlock.getZ();
        int found = Integer.MIN_VALUE;
        if (up) {
            for (int y = signBlock.getY() + 2; y < world.getMaxHeight() - 1; y++) {
                if (standable(world, x, y, z)) {
                    found = y;
                    break;
                }
            }
        } else {
            for (int y = signBlock.getY() - 2; y > world.getMinHeight(); y--) {
                if (standable(world, x, y, z)) {
                    found = y;
                    break;
                }
            }
        }
        if (found == Integer.MIN_VALUE) {
            say(player, "&cAucun etage " + (up ? "au-dessus" : "en dessous") + ".");
            return;
        }
        Location to = new Location(world, x + 0.5, found, z + 0.5, player.getLocation().getYaw(), player.getLocation().getPitch());
        player.teleport(to);
        player.playSound(to, Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.4f);
    }

    private static boolean standable(World world, int x, int y, int z) {
        return world.getBlockAt(x, y - 1, z).getType().isSolid()
                && world.getBlockAt(x, y, z).isPassable() && world.getBlockAt(x, y + 1, z).isPassable();
    }
}
