package fr.orbit.devcore.compass;

import fr.orbit.devcore.OrbitDevCore;
import fr.orbit.devcore.Teleports;
import fr.orbit.devcore.gui.GuiTextures;
import fr.orbit.devcore.gui.Menus;
import fr.orbit.devcore.message.Messages;
import fr.orbit.devcore.profile.PlayerProfile;
import fr.orbit.devcore.profile.RankProvider;
import fr.orbit.devcore.storage.Warp;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static fr.orbit.devcore.gui.Menus.icon;
import static fr.orbit.devcore.gui.Menus.item;

/**
 * Menu de la boussole « Navigation » (double coffre), texturé par le pack du lobby
 * (fond : tools/OrbitGuiGen.java, drawNav) :
 * <pre>
 *  rangée 0    : profil (tête) | « MODES DE JEU » | Boutique
 *  rangées 1-4 : 4 cartes de mode de 4 x 2 cases (icône en haut à gauche), colonne centrale décorative
 *  rangée 5    : Hall central | Discord | Cosmétiques | Site | Fermer
 * </pre>
 * Les 4 cartes reprennent les 4 premiers modes de config.yml « modes », dans l'ordre ; leurs noms sont
 * dessinés dans la texture : changer de mode demande de regénérer la texture.
 * Sans le pack, même disposition en vitres colorées.
 */
public final class WarpMenu implements Listener {

    private static final int PROFILE_SLOT = 0;
    private static final int SHOP_SLOT = 8;
    private static final int HALL_SLOT = 45;
    private static final int DISCORD_SLOT = 47;
    private static final int COSMETICS_SLOT = 49;
    private static final int WEBSITE_SLOT = 51;
    private static final int CLOSE_SLOT = 53;
    /** Mode du centre (Tycoon) : icône sur le conduit, plaque du nom juste en dessous. */
    private static final int CENTER_ICON_SLOT = 22;
    private static final int CENTER_PLATE_SLOT = 31;

    /** Cases de chaque carte de mode ; la première reçoit l'icône du mode. */
    private static final int[][] CARDS = {
            {9, 10, 11, 12, 18, 19, 20, 21},
            {14, 15, 16, 17, 23, 24, 25, 26},
            {27, 28, 29, 30, 36, 37, 38, 39},
            {32, 33, 34, 35, 41, 42, 43, 44}};
    /** Couleur des cartes sans le pack (mêmes teintes que la texture). */
    private static final Material[] CARD_GLASS = {
            Material.LIME_STAINED_GLASS_PANE, Material.RED_STAINED_GLASS_PANE,
            Material.BLUE_STAINED_GLASS_PANE, Material.YELLOW_STAINED_GLASS_PANE};

    private static final String SPAWN = "spawn";
    private static final String WARP = "warp:";
    private static final String SERVER = "server:";
    private static final String DISCORD = "link:discord";
    private static final String WEBSITE = "link:website";
    private static final String COSMETICS = "cosmetics";
    private static final String SHOP = "shop";
    private static final String CLOSE = "close";

    private final OrbitDevCore plugin;
    private final Supplier<RankProvider> ranks;
    private boolean warnedExtraModes;

    public WarpMenu(OrbitDevCore plugin, Supplier<RankProvider> ranks) {
        this.plugin = plugin;
        this.ranks = ranks;
    }

    /** Holder propre au menu : sert à le reconnaître et garde la correspondance case → action. */
    private static final class Holder implements InventoryHolder {
        private final Map<Integer, String> actions = new HashMap<>();
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public void open(Player player) {
        boolean textured = plugin.lobbyPack().isLoaded(player);
        Holder holder = new Holder();
        Component title = textured ? GuiTextures.title(GuiTextures.Screen.NAVIGATION)
                : plugin.messages().get("menu.navigation-title");
        Inventory inv = Bukkit.createInventory(holder, Menus.SIZE, title);
        holder.inventory = inv;
        if (!textured) {
            GuiTextures.fallbackBackground(inv);
        }

        // --- Rangée 0 : profil / boutique
        inv.setItem(PROFILE_SLOT, profileItem(player));
        if (player.hasPermission("orbitdev.shop")) {
            inv.setItem(SHOP_SLOT, item(Material.GOLD_INGOT, plugin.messages().item("menu.shop-name"),
                    plugin.messages().itemLore("menu.shop-lore"), true));
            holder.actions.put(SHOP_SLOT, SHOP);
        }

        // --- Rangées 1-4 : les 4 premiers modes, en cartes
        ConfigurationSection modes = plugin.getConfig().getConfigurationSection("modes");
        List<String> modeIds = modes == null ? List.of() : new ArrayList<>(modes.getKeys(false));
        if (modeIds.size() > CARDS.length && !warnedExtraModes) {
            warnedExtraModes = true;
            plugin.getLogger().warning("Le menu de la boussole n'affiche que " + CARDS.length + " modes ("
                    + modeIds.size() + " dans config.yml) : les suivants sont ignorés.");
        }
        for (int i = 0; i < Math.min(modeIds.size(), CARDS.length); i++) {
            ConfigurationSection mode = modes.getConfigurationSection(modeIds.get(i));
            String warp = mode == null ? modeIds.get(i) : mode.getString("warp", modeIds.get(i));
            Component name = modeName(mode, modeIds.get(i));
            List<Component> lore = modeLore(mode);
            for (int k = 0; k < CARDS[i].length; k++) {
                int slot = CARDS[i][k];
                inv.setItem(slot, k == 0
                        ? item(icon(mode != null ? mode.getString("icon") : null, Material.ENDER_EYE), name, lore, false)
                        : GuiTextures.zone(textured, CARD_GLASS[i], name, lore));
                holder.actions.put(slot, WARP + warp);
            }
        }

        // --- Centre : mode-centre (Tycoon) : warp devant son PNJ, sinon envoi direct sur son serveur
        ConfigurationSection centre = plugin.getConfig().getConfigurationSection("mode-centre");
        String centreWarp = centre == null ? "" : centre.getString("warp", "");
        String centreServer = centre == null ? "" : centre.getString("server", "");
        String centreAction = !centreWarp.isBlank() ? WARP + centreWarp
                : !centreServer.isBlank() ? SERVER + centreServer : null;
        if (centreAction != null) {
            Component name = modeName(centre, centreWarp.isBlank() ? centreServer : centreWarp);
            List<Component> lore = modeLore(centre);
            inv.setItem(CENTER_ICON_SLOT, item(icon(centre.getString("icon"), Material.GOLDEN_PICKAXE), name, lore, false));
            inv.setItem(CENTER_PLATE_SLOT, GuiTextures.zone(textured, Material.CYAN_STAINED_GLASS_PANE, name, lore));
            holder.actions.put(CENTER_ICON_SLOT, centreAction);
            holder.actions.put(CENTER_PLATE_SLOT, centreAction);
        }

        // --- Rangée 5 : Hall central / Discord / Cosmétiques / Site / Fermer
        inv.setItem(HALL_SLOT, item(icon(plugin.getConfig().getString("menu.spawn-icon"), Material.BEACON),
                plugin.messages().item("menu.hall-name"), plugin.messages().itemLore("menu.hall-lore"), true));
        holder.actions.put(HALL_SLOT, SPAWN);
        inv.setItem(DISCORD_SLOT, linkItem(Material.LIGHT_BLUE_DYE, "discord"));
        holder.actions.put(DISCORD_SLOT, DISCORD);
        if (player.hasPermission("orbitdev.cosmetics")) {
            inv.setItem(COSMETICS_SLOT, item(Material.TOTEM_OF_UNDYING, plugin.messages().item("menu.cosmetics-name"),
                    plugin.messages().itemLore("menu.cosmetics-lore"), true));
            holder.actions.put(COSMETICS_SLOT, COSMETICS);
        }
        inv.setItem(WEBSITE_SLOT, linkItem(Material.WRITABLE_BOOK, "website"));
        holder.actions.put(WEBSITE_SLOT, WEBSITE);
        inv.setItem(CLOSE_SLOT, item(Material.BARRIER, plugin.messages().item("menu.close-name"), List.of(), false));
        holder.actions.put(CLOSE_SLOT, CLOSE);

        player.openInventory(inv);
        player.playSound(Menus.OPEN_SOUND);
    }

    // ------------------------------------------------------------------ objets

    private static Component modeName(ConfigurationSection mode, String id) {
        String name = mode != null ? mode.getString("name", id) : id;
        return Messages.mini(name).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    private List<Component> modeLore(ConfigurationSection mode) {
        List<Component> lore = new ArrayList<>();
        if (mode != null) {
            for (String line : mode.getStringList("description")) {
                lore.add(Messages.mini(line).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
            }
        }
        lore.addAll(plugin.messages().itemLore("menu.mode-lore"));
        return lore;
    }

    /** Tête du joueur avec grade et statistiques (aussi utilisée par la boutique). */
    public ItemStack profileItem(Player player) {
        RankProvider.Rank rank = ranks.get().rank(player);
        PlayerProfile profile = PlayerProfile.of(player);
        Component grade = rank.prefix().isBlank()
                ? Component.text(rank.group(), NamedTextColor.WHITE)
                : Messages.mini(rank.prefix().trim());
        String role = player.isOp() ? plugin.messages().raw("menu.role-op") : plugin.messages().raw("menu.role-player");
        String server = plugin.connector().currentServer();

        TagResolver[] ph = {
                Messages.ph("player", player.getName()),
                Messages.comp("grade", grade),
                Messages.ph("group", rank.group()),
                Messages.ph("role", role),
                Messages.ph("server", server.isBlank() ? "-" : server),
                Messages.ph("playtime", profile.playtime()),
                Messages.ph("first_join", profile.firstJoin()),
                Messages.ph("deaths", profile.deaths()),
                Messages.ph("mob_kills", profile.mobKills()),
                Messages.ph("player_kills", profile.playerKills()),
                Messages.ph("distance", profile.distance())};

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(player);
        meta.displayName(plugin.messages().item("menu.profile-name", ph));
        meta.lore(plugin.messages().itemLore("menu.profile-lore", ph));
        meta.addItemFlags(ItemFlag.values());
        head.setItemMeta(meta);
        return head;
    }

    private ItemStack linkItem(Material material, String type) {
        String url = plugin.getConfig().getString("links." + type, "");
        String lore = url.isBlank() ? "menu.link-missing-lore" : "menu." + type + "-lore";
        return item(material, plugin.messages().item("menu." + type + "-name"),
                plugin.messages().itemLore(lore, Messages.ph("url", url)), false);
    }

    // ------------------------------------------------------------------ clics

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        String action = holder.actions.get(event.getSlot());
        if (action == null) {
            return;
        }
        player.playSound(Menus.CLICK_SOUND);
        // Ouvrir un autre inventaire pendant un clic est déconseillé : on le fait au tick suivant.
        if (action.equals(COSMETICS)) {
            Bukkit.getScheduler().runTask(plugin, () -> plugin.cosmeticMenu().open(player));
            return;
        }
        if (action.equals(SHOP)) {
            Bukkit.getScheduler().runTask(plugin, () -> plugin.shopMenu().open(player));
            return;
        }
        player.closeInventory();

        if (action.startsWith(WARP)) {
            teleportToWarp(player, action.substring(WARP.length()));
        } else if (action.startsWith(SERVER)) {
            plugin.connector().send(player, action.substring(SERVER.length()));
        } else if (action.equals(SPAWN)) {
            Location spawn = plugin.locations().spawn();
            if (spawn == null) {
                plugin.messages().send(player, "spawn.missing");
            } else {
                Teleports.teleport(plugin, player, spawn, "spawn.teleported");
            }
        } else if (action.equals(DISCORD)) {
            sendLink(player, "discord");
        } else if (action.equals(WEBSITE)) {
            sendLink(player, "website");
        }
        // CLOSE : rien de plus, l'inventaire est déjà fermé.
    }

    private void teleportToWarp(Player player, String name) {
        Warp warp = plugin.locations().warp(name);
        if (warp == null) {
            plugin.messages().send(player, "warp.not-found", Messages.ph("name", name));
            return;
        }
        Location target = warp.location().toLocation();
        if (target == null) {
            plugin.messages().send(player, "warp.world-missing", Messages.ph("name", warp.name()));
            return;
        }
        Teleports.teleport(plugin, player, target, "warp.teleported", Messages.ph("name", warp.name()));
    }

    /** Un inventaire ne peut pas ouvrir de lien : on envoie un lien cliquable dans le chat. */
    private void sendLink(Player player, String type) {
        String url = plugin.getConfig().getString("links." + type, "");
        if (url.isBlank()) {
            plugin.messages().send(player, "menu.link-missing");
            return;
        }
        player.sendMessage(plugin.messages().get("menu." + type + "-message").append(link(url)));
    }

    /** Lien cliquable souligné (aussi utilisé par la boutique). */
    public Component link(String url) {
        return Component.text(url, NamedTextColor.AQUA)
                .decorate(TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.openUrl(url))
                .hoverEvent(HoverEvent.showText(plugin.messages().get("menu.link-hover")));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof Holder) {
            event.setCancelled(true);
        }
    }
}
