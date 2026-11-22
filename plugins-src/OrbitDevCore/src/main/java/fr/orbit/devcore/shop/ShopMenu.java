package fr.orbit.devcore.shop;

import fr.orbit.devcore.OrbitDevCore;
import fr.orbit.devcore.gui.GuiTextures;
import fr.orbit.devcore.gui.Menus;
import fr.orbit.devcore.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static fr.orbit.devcore.gui.Menus.icon;
import static fr.orbit.devcore.gui.Menus.item;

/**
 * Menu « Boutique » du lobby (double coffre), texturé par le pack du lobby (fond : tools/OrbitGuiGen.java, drawShop) :
 * <pre>
 *  rangée 0    : profil (tête) | « EN VEDETTE » | retour à la Navigation
 *  rangées 1-2 : vitrine de 5 x 2 offres (config.yml shop.featured)
 *  rangée 3    : « BOUTIQUE EN LIGNE » (7 cases)
 *  rangées 4-5 : Grades | Clés / Packs | Orbit+ (4 cases chacun)
 * </pre>
 * Rien n'est vendu en jeu : chaque bouton envoie le lien de la boutique en ligne (shop.url ou le lien de la
 * catégorie). Sans lien configuré, le joueur est prévenu que la boutique ouvre bientôt.
 */
public final class ShopMenu implements Listener {

    private static final int PROFILE_SLOT = 0;
    private static final int BACK_SLOT = 8;
    private static final int[] FEATURED = {11, 12, 13, 14, 15, 20, 21, 22, 23, 24};
    private static final int EMPTY_SHOWCASE_SLOT = 13;

    /** Boutons de la texture : clé de config/messages, cases, vitre sans le pack. */
    private record Button(String key, int[] slots, Material glass) {
    }

    private static final Button[] BUTTONS = {
            new Button("online", new int[]{28, 29, 30, 31, 32, 33, 34}, Material.BLUE_STAINED_GLASS_PANE),
            new Button("grades", new int[]{36, 37, 38, 39}, Material.PURPLE_STAINED_GLASS_PANE),
            new Button("cles", new int[]{41, 42, 43, 44}, Material.ORANGE_STAINED_GLASS_PANE),
            new Button("packs", new int[]{45, 46, 47, 48}, Material.LIME_STAINED_GLASS_PANE),
            new Button("orbit-plus", new int[]{50, 51, 52, 53}, Material.CYAN_STAINED_GLASS_PANE)};

    private static final String BACK = "back";
    private static final String LINK = "link:";

    private final OrbitDevCore plugin;

    public ShopMenu(OrbitDevCore plugin) {
        this.plugin = plugin;
    }

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
        Component title = textured ? GuiTextures.title(GuiTextures.Screen.SHOP) : plugin.messages().get("shop.title");
        Inventory inv = Bukkit.createInventory(holder, Menus.SIZE, title);
        holder.inventory = inv;
        if (!textured) {
            GuiTextures.fallbackBackground(inv);
        }

        // --- Rangée 0 : profil / retour
        inv.setItem(PROFILE_SLOT, plugin.warpMenu().profileItem(player));
        inv.setItem(BACK_SLOT, item(Material.COMPASS, plugin.messages().item("shop.back-name"),
                plugin.messages().itemLore("shop.back-lore"), false));
        holder.actions.put(BACK_SLOT, BACK);

        // --- Vitrine
        List<Map<?, ?>> offers = plugin.getConfig().getMapList("shop.featured");
        if (!textured) {
            ItemStack glass = Menus.decor(Material.LIGHT_BLUE_STAINED_GLASS_PANE, null);
            for (int slot : FEATURED) {
                inv.setItem(slot, glass);
            }
        }
        if (offers.isEmpty()) {
            inv.setItem(EMPTY_SHOWCASE_SLOT, item(Material.CLOCK, plugin.messages().item("shop.showcase-empty-name"),
                    plugin.messages().itemLore("shop.showcase-empty-lore"), false));
        }
        for (int i = 0; i < Math.min(offers.size(), FEATURED.length); i++) {
            Map<?, ?> offer = offers.get(i);
            inv.setItem(FEATURED[i], offerItem(offer));
            holder.actions.put(FEATURED[i], LINK + string(offer, "url"));
        }

        // --- Boutons
        for (Button button : BUTTONS) {
            Component name = plugin.messages().item("shop." + button.key() + "-name");
            List<Component> lore = plugin.messages().itemLore("shop." + button.key() + "-lore");
            String url = plugin.getConfig().getString("shop.categories." + button.key(), "");
            for (int slot : button.slots()) {
                inv.setItem(slot, GuiTextures.zone(textured, button.glass(), name, lore));
                holder.actions.put(slot, LINK + url);
            }
        }

        player.openInventory(inv);
        player.playSound(Menus.OPEN_SOUND);
    }

    private ItemStack offerItem(Map<?, ?> offer) {
        Material material = icon(string(offer, "icon"), Material.NETHER_STAR);
        Component name = Messages.mini(string(offer, "name")).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
        List<Component> lore = new ArrayList<>();
        if (offer.get("lore") instanceof List<?> lines) {
            for (Object line : lines) {
                lore.add(Messages.mini(String.valueOf(line)).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
            }
        }
        lore.addAll(plugin.messages().itemLore("shop.offer-lore"));
        return item(material, name, lore, Boolean.TRUE.equals(offer.get("glint")));
    }

    private static String string(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value == null ? "" : String.valueOf(value);
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
        if (action.equals(BACK)) {
            Bukkit.getScheduler().runTask(plugin, () -> plugin.warpMenu().open(player));
            return;
        }
        player.closeInventory();
        if (action.startsWith(LINK)) {
            sendShopLink(player, action.substring(LINK.length()));
        }
    }

    /** Lien de l'offre/catégorie, sinon lien général de la boutique, sinon « bientôt » + site. */
    private void sendShopLink(Player player, String specific) {
        String url = specific.isBlank() ? plugin.getConfig().getString("shop.url", "") : specific;
        if (!url.isBlank()) {
            player.sendMessage(plugin.messages().get("shop.link-message").append(plugin.warpMenu().link(url)));
            return;
        }
        String website = plugin.getConfig().getString("links.website", "");
        if (website.isBlank()) {
            plugin.messages().send(player, "shop.soon");
        } else {
            player.sendMessage(plugin.messages().get("shop.soon-website").append(plugin.warpMenu().link(website)));
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof Holder) {
            event.setCancelled(true);
        }
    }
}
