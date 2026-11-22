package fr.orbit.devcore.cosmetic;

import fr.orbit.devcore.OrbitDevCore;
import fr.orbit.devcore.gui.GuiTextures;
import fr.orbit.devcore.gui.Menus;
import fr.orbit.devcore.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static fr.orbit.devcore.gui.Menus.decor;
import static fr.orbit.devcore.gui.Menus.item;

/**
 * Menu des cosmétiques (double coffre) : une page d'accueil avec les catégories, puis une page par catégorie
 * (28 cosmétiques par page, flèches pour changer de page).
 *
 * <p>Deux rendus : texturé (style « station », pack du lobby chargé) et vitres colorées (sans le pack).
 * Les textures sont générées par tools/OrbitGuiGen.java (drawCosmetics / drawCosmeticList) : les noms des
 * catégories y sont dessinés dans l'ordre de {@link CosmeticType#values()}.
 */
public final class CosmeticMenu implements Listener {

    // ------------------------------------------------------------------ disposition « vitres » (sans le pack)
    private static final int[] CATEGORY_SLOTS = {19, 20, 21, 22, 23, 24, 25};
    private static final Material[] ACCENTS = {
            Material.ORANGE_STAINED_GLASS_PANE, Material.MAGENTA_STAINED_GLASS_PANE,
            Material.LIGHT_BLUE_STAINED_GLASS_PANE, Material.PURPLE_STAINED_GLASS_PANE,
            Material.RED_STAINED_GLASS_PANE, Material.LIME_STAINED_GLASS_PANE,
            Material.YELLOW_STAINED_GLASS_PANE};
    private static final int CLOSE_SLOT = 53;

    // ------------------------------------------------------------------ disposition « station » (texture)
    /** Accueil : slot de l'icône de chaque carte (colonne 0 à gauche, colonne 8 à droite), dans l'ordre de CosmeticType. */
    private static final int[] CARD_ICONS = {9, 17, 18, 26, 27, 35, 36};
    private static final int CARD_REMOVE = 44;

    /** Emplacements des boutons d'une page (accueil et catégorie partagent l'en-tête et le retour). */
    private record Slots(int header, int back, int previous, int remove, int next) {}
    private static final Slots GLASS = new Slots(4, 48, 46, 50, 52);
    private static final Slots STATION = new Slots(0, 8, 45, 49, 53);

    private static final int EMPTY_SLOT = 22;
    private static final int PAGE_SIZE = Menus.INNER_SLOTS.length;

    private static final String OPEN = "open:";
    private static final String EQUIP = "equip:";
    private static final String REMOVE = "remove";
    private static final String BACK = "back";
    private static final String PREVIOUS = "previous";
    private static final String NEXT = "next";
    private static final String CLOSE = "close";

    private final OrbitDevCore plugin;

    public CosmeticMenu(OrbitDevCore plugin) {
        this.plugin = plugin;
    }

    /** {@code view} = null pour la page d'accueil, sinon la catégorie affichée. */
    private static final class Holder implements InventoryHolder {
        private final CosmeticType view;
        private final boolean textured;
        private final Map<Integer, String> actions = new HashMap<>();
        private int page;
        private Inventory inventory;

        private Holder(CosmeticType view, boolean textured) {
            this.view = view;
            this.textured = textured;
        }

        private Slots slots() {
            return textured ? STATION : GLASS;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private CosmeticService cosmetics() {
        return plugin.cosmetics();
    }

    private Messages msg() {
        return plugin.messages();
    }

    public void open(Player player) {
        open(player, null);
    }

    public void open(Player player, CosmeticType view) {
        if (!cosmetics().enabled()) {
            msg().send(player, "cosmetics.lobby-only");
            return;
        }
        boolean textured = plugin.lobbyPack().isLoaded(player);
        Holder holder = new Holder(view, textured);
        Component title;
        if (textured) {
            title = GuiTextures.title(view == null ? GuiTextures.Screen.COSMETICS : screen(view));
        } else {
            title = view == null
                    ? msg().get("cosmetics.menu-title")
                    : msg().get("cosmetics.category-title", Messages.ph("category", typeName(view)));
        }
        holder.inventory = Bukkit.createInventory(holder, Menus.SIZE, title);
        render(player, holder);
        player.openInventory(holder.inventory);
        player.playSound(Menus.OPEN_SOUND);
    }

    /** Texture de la page d'une catégorie (switch exhaustif : une nouvelle catégorie oblige à ajouter sa texture). */
    private static GuiTextures.Screen screen(CosmeticType type) {
        return switch (type) {
            case TRAIL -> GuiTextures.Screen.COSMETICS_TRAIL;
            case AURA -> GuiTextures.Screen.COSMETICS_AURA;
            case HAT -> GuiTextures.Screen.COSMETICS_HAT;
            case BACK -> GuiTextures.Screen.COSMETICS_BACK;
            case BALLOON -> GuiTextures.Screen.COSMETICS_BALLOON;
            case PET -> GuiTextures.Screen.COSMETICS_PET;
            case MOUNT -> GuiTextures.Screen.COSMETICS_MOUNT;
        };
    }

    private void render(Player player, Holder holder) {
        holder.actions.clear();
        Inventory inv = holder.inventory;
        if (holder.textured) {
            inv.clear();
        } else {
            Menus.frame(inv);
        }
        if (holder.view != null) {
            renderCategory(player, holder, inv, holder.view);
        } else if (holder.textured) {
            renderHomeStation(player, holder, inv);
        } else {
            renderHome(player, holder, inv);
        }
        // Page d'une catégorie texturée : le slot 53 est « page suivante » (Échap ferme le menu).
        if (!holder.textured || holder.view == null) {
            inv.setItem(CLOSE_SLOT, item(Material.BARRIER, msg().item("menu.close-name"), List.of(), false));
            holder.actions.put(CLOSE_SLOT, CLOSE);
        }
    }

    // ------------------------------------------------------------------ accueil

    private ItemStack headerItem() {
        int total = 0;
        for (CosmeticType type : CosmeticType.values()) {
            total += cosmetics().catalog().all(type).size();
        }
        return item(Material.TOTEM_OF_UNDYING, msg().item("cosmetics.header-name"),
                msg().itemLore("cosmetics.header-lore", Messages.ph("total", total)), true);
    }

    private Component categoryName(CosmeticType type) {
        return msg().item("cosmetics.types." + type.id() + ".name");
    }

    private List<Component> categoryLore(Player player, CosmeticType type) {
        int owned = 0;
        for (Cosmetic cosmetic : cosmetics().catalog().all(type)) {
            if (cosmetics().owns(player, cosmetic)) {
                owned++;
            }
        }
        Cosmetic equipped = cosmetics().equipped(player, type);
        TagResolver[] ph = {
                Messages.ph("owned", owned),
                Messages.ph("total", cosmetics().catalog().all(type).size()),
                Messages.comp("equipped", equippedName(equipped)),
                Messages.comp("description", msg().get("cosmetics.types." + type.id() + ".description"))};
        return msg().itemLore("cosmetics.category-lore", ph);
    }

    private ItemStack categoryItem(Player player, CosmeticType type) {
        return item(type.icon(), categoryName(type), categoryLore(player, type), cosmetics().equipped(player, type) != null);
    }

    private ItemStack backToCompass() {
        return item(Material.COMPASS, msg().item("cosmetics.back-compass-name"), List.of(), false);
    }

    /** Sans le pack : catégories sur une ligne, vitres de couleur au-dessus et au-dessous. */
    private void renderHome(Player player, Holder holder, Inventory inv) {
        Slots s = GLASS;
        inv.setItem(s.header(), headerItem());

        CosmeticType[] types = CosmeticType.values();
        for (int i = 0; i < types.length && i < CATEGORY_SLOTS.length; i++) {
            CosmeticType type = types[i];
            int slot = CATEGORY_SLOTS[i];
            ItemStack accent = decor(ACCENTS[i], null);
            inv.setItem(slot - 9, accent);
            inv.setItem(slot + 9, accent);
            inv.setItem(slot, categoryItem(player, type));
            holder.actions.put(slot, OPEN + type.id());
        }

        inv.setItem(s.back(), backToCompass());
        holder.actions.put(s.back(), BACK);
        if (cosmetics().hasAny(player)) {
            inv.setItem(s.remove(), item(Material.MILK_BUCKET, msg().item("cosmetics.remove-all-name"),
                    msg().itemLore("cosmetics.remove-all-lore"), false));
            holder.actions.put(s.remove(), REMOVE);
        }
    }

    /** Avec le pack : 8 cartes dessinées (7 catégories + « retirer »), icône dans le slot, plaque = zones invisibles. */
    private void renderHomeStation(Player player, Holder holder, Inventory inv) {
        Slots s = STATION;
        inv.setItem(s.header(), headerItem());
        inv.setItem(s.back(), backToCompass());
        holder.actions.put(s.back(), BACK);

        CosmeticType[] types = CosmeticType.values();
        for (int i = 0; i < types.length && i < CARD_ICONS.length; i++) {
            CosmeticType type = types[i];
            card(holder, inv, CARD_ICONS[i], categoryItem(player, type), categoryName(type), categoryLore(player, type),
                    OPEN + type.id());
        }

        Component name = msg().item("cosmetics.remove-all-name");
        if (cosmetics().hasAny(player)) {
            List<Component> lore = msg().itemLore("cosmetics.remove-all-lore");
            card(holder, inv, CARD_REMOVE, item(Material.MILK_BUCKET, name, lore, false), name, lore, REMOVE);
        } else {
            List<Component> lore = msg().itemLore("cosmetics.remove-all-none");
            card(holder, inv, CARD_REMOVE, item(Material.BUCKET, name, lore, false), name, lore, null);
        }
    }

    /** Une carte : l'icône dans son slot, et 3 zones invisibles sur la plaque du nom (à droite, ou à gauche en colonne 8). */
    private static void card(Holder holder, Inventory inv, int iconSlot, ItemStack icon, Component name,
                             List<Component> lore, String action) {
        inv.setItem(iconSlot, icon);
        ItemStack zone = GuiTextures.zone(name, lore);
        int step = iconSlot % 9 == 0 ? 1 : -1;
        for (int k = 0; k <= 3; k++) {
            int slot = iconSlot + k * step;
            if (k > 0) {
                inv.setItem(slot, zone);
            }
            if (action != null) {
                holder.actions.put(slot, action);
            }
        }
    }

    // ------------------------------------------------------------------ catégorie

    private void renderCategory(Player player, Holder holder, Inventory inv, CosmeticType type) {
        Slots s = holder.slots();
        List<Cosmetic> all = new ArrayList<>(cosmetics().catalog().all(type));
        int pages = Math.max(1, (all.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        holder.page = Math.max(0, Math.min(holder.page, pages - 1));
        Cosmetic equipped = cosmetics().equipped(player, type);

        inv.setItem(s.header(), item(type.icon(), categoryName(type),
                msg().itemLore("cosmetics.category-header-lore",
                        Messages.comp("equipped", equippedName(equipped)),
                        Messages.ph("page", holder.page + 1), Messages.ph("pages", pages),
                        Messages.ph("total", all.size())), true));

        String lorePrefix = type == CosmeticType.MOUNT ? "cosmetics.mount-lore-" : "cosmetics.lore-";
        int start = holder.page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && start + i < all.size(); i++) {
            Cosmetic cosmetic = all.get(start + i);
            int slot = Menus.INNER_SLOTS[i];
            boolean isEquipped = equipped != null && equipped.id().equals(cosmetic.id());
            boolean owned = cosmetics().owns(player, cosmetic);
            String lore = isEquipped ? lorePrefix + "equipped" : owned ? lorePrefix + "available" : "cosmetics.lore-locked";
            inv.setItem(slot, cosmeticIcon(cosmetic, owned, isEquipped, lore));
            holder.actions.put(slot, EQUIP + cosmetic.id());
        }
        if (all.isEmpty()) {
            inv.setItem(EMPTY_SLOT, decor(Material.GRAY_STAINED_GLASS_PANE, msg().item("cosmetics.empty-category")));
        }

        if (holder.page > 0) {
            inv.setItem(s.previous(), item(Material.SPECTRAL_ARROW, msg().item("cosmetics.previous-name"),
                    msg().itemLore("cosmetics.page-lore", Messages.ph("page", holder.page), Messages.ph("pages", pages)),
                    false));
            holder.actions.put(s.previous(), PREVIOUS);
        }
        if (holder.page < pages - 1) {
            inv.setItem(s.next(), item(Material.SPECTRAL_ARROW, msg().item("cosmetics.next-name"),
                    msg().itemLore("cosmetics.page-lore", Messages.ph("page", holder.page + 2), Messages.ph("pages", pages)),
                    false));
            holder.actions.put(s.next(), NEXT);
        }
        inv.setItem(s.back(), item(Material.ARROW, msg().item("cosmetics.back-name"), List.of(), false));
        holder.actions.put(s.back(), BACK);
        if (equipped != null) {
            inv.setItem(s.remove(), item(Material.MILK_BUCKET, msg().item("cosmetics.remove-name"),
                    msg().itemLore("cosmetics.remove-lore", Messages.comp("name", Messages.mini(equipped.name()))),
                    false));
            holder.actions.put(s.remove(), REMOVE);
        }
    }

    /** Icône d'un cosmétique : l'objet (tête personnalisée comprise), ou un colorant gris s'il est verrouillé. */
    private ItemStack cosmeticIcon(Cosmetic cosmetic, boolean owned, boolean equipped, String loreKey) {
        ItemStack icon;
        if (!owned) {
            icon = new ItemStack(Material.GRAY_DYE);
        } else if (cosmetic.texture() != null) {
            icon = cosmetics().displayItem(cosmetic);
        } else {
            icon = new ItemStack(cosmetic.icon());
        }
        ItemMeta meta = icon.getItemMeta();
        meta.displayName(Messages.mini(cosmetic.name()).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        meta.lore(msg().itemLore(loreKey,
                Messages.comp("description", Messages.mini(cosmetic.description())),
                Messages.comp("rarity", msg().get("cosmetics.rarity." + cosmetic.rarity().id()))));
        if (equipped) {
            meta.setEnchantmentGlintOverride(true);
        }
        meta.addItemFlags(ItemFlag.values());
        icon.setItemMeta(meta);
        return icon;
    }

    private Component equippedName(Cosmetic equipped) {
        return equipped == null ? msg().get("cosmetics.none") : Messages.mini(equipped.name());
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

        switch (action) {
            case CLOSE -> {
                player.playSound(Menus.CLICK_SOUND);
                player.closeInventory();
            }
            case BACK -> {
                player.playSound(Menus.CLICK_SOUND);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (holder.view == null) {
                        plugin.warpMenu().open(player);
                    } else {
                        open(player, null);
                    }
                });
            }
            case PREVIOUS, NEXT -> {
                player.playSound(Menus.CLICK_SOUND);
                holder.page += action.equals(NEXT) ? 1 : -1;
                render(player, holder);
            }
            case REMOVE -> {
                if (holder.view == null) {
                    cosmetics().unequipAll(player);
                    msg().send(player, "cosmetics.unequipped-all");
                } else {
                    Cosmetic removed = cosmetics().unequip(player, holder.view);
                    if (removed != null) {
                        msg().send(player, "cosmetics.unequipped", Messages.comp("name", Messages.mini(removed.name())));
                    }
                }
                player.playSound(Menus.CLICK_SOUND);
                render(player, holder);
            }
            default -> {
                if (action.startsWith(OPEN)) {
                    player.playSound(Menus.CLICK_SOUND);
                    CosmeticType type = CosmeticType.parse(action.substring(OPEN.length()));
                    Bukkit.getScheduler().runTask(plugin, () -> open(player, type));
                } else if (action.startsWith(EQUIP) && holder.view == CosmeticType.MOUNT) {
                    summon(player, action.substring(EQUIP.length()));
                } else if (action.startsWith(EQUIP) && holder.view != null) {
                    toggle(player, holder.view, action.substring(EQUIP.length()));
                    render(player, holder);
                }
            }
        }
    }

    /** Montures : le clic sélectionne la monture et l'invoque tout de suite (le menu se ferme). */
    private void summon(Player player, String id) {
        Cosmetic mount = cosmetics().catalog().get(CosmeticType.MOUNT, id);
        if (mount == null) {
            return;
        }
        if (cosmetics().equip(player, mount) == CosmeticService.Result.LOCKED) {
            player.playSound(Menus.DENY_SOUND);
            msg().send(player, "cosmetics.locked", Messages.comp("name", Messages.mini(mount.name())));
            return;
        }
        player.closeInventory();
        player.playSound(Menus.EQUIP_SOUND);
        // Au tick suivant : le joueur ne doit plus avoir d'inventaire ouvert quand il monte.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                cosmetics().summonMountWithFeedback(player);
            }
        });
    }

    private void toggle(Player player, CosmeticType type, String id) {
        Cosmetic cosmetic = cosmetics().catalog().get(type, id);
        if (cosmetic == null) {
            return;
        }
        TagResolver name = Messages.comp("name", Messages.mini(cosmetic.name()));
        Cosmetic equipped = cosmetics().equipped(player, type);
        if (equipped != null && equipped.id().equals(cosmetic.id())) {
            cosmetics().unequip(player, type);
            player.playSound(Menus.CLICK_SOUND);
            msg().send(player, "cosmetics.unequipped", name);
            return;
        }
        switch (cosmetics().equip(player, cosmetic)) {
            case EQUIPPED -> {
                player.playSound(Menus.EQUIP_SOUND);
                msg().send(player, "cosmetics.equipped", name);
            }
            case LOCKED -> {
                player.playSound(Menus.DENY_SOUND);
                msg().send(player, "cosmetics.locked", name);
            }
            case HELMET_STUCK -> {
                player.playSound(Menus.DENY_SOUND);
                msg().send(player, "cosmetics.helmet-stuck");
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof Holder) {
            event.setCancelled(true);
        }
    }

    /** Nom de catégorie sans mise en forme (titre du menu, messages). */
    public String typeName(CosmeticType type) {
        return msg().raw("cosmetics.types." + type.id() + ".plain");
    }
}
