package fr.orbit.extraction.phone;

import fr.orbit.extraction.Keys;
import fr.orbit.extraction.OrbitExtraction;
import fr.orbit.extraction.gun.GunItems;
import fr.orbit.extraction.raid.Profile;
import fr.orbit.extraction.util.Text;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Téléphone du mode extraction : l'objet (jamais perdu), les crédits, le catalogue de la boutique et des colis,
 * les messages entre joueurs. Les écrans sont dans {@link PhoneMenu}, les largages dans {@link Parcels}.
 */
public final class PhoneService implements Listener {

    /** Article de la boutique. {@code key} = « type:id » sans quantité, {@code amount} = quantité vendue. */
    public record Article(String spec, String key, int amount, long price, ItemStack icon) {
    }

    /** Colis commandable en raid. {@code vehicle} non nul : le colis est un véhicule (id du type), sans contenu. */
    public record Offer(String id, String name, Material icon, long price, List<String> contents, String vehicle) {
    }

    private record Draft(UUID to, String toName, long expires) {
    }

    private static final int PHONE_SLOT = 8;
    private static final int MESSAGE_MAX = 120;

    private final OrbitExtraction plugin;
    private final List<Article> articles = new ArrayList<>();
    private final Map<String, Long> buyback = new LinkedHashMap<>();
    private final List<Offer> offers = new ArrayList<>();
    private final Map<UUID, Draft> drafts = new ConcurrentHashMap<>();
    private long startCredits;
    private double buybackRate;

    public PhoneService(OrbitExtraction plugin) {
        this.plugin = plugin;
    }

    public void load(FileConfiguration c) {
        startCredits = Math.max(0, c.getLong("economie.depart", 300));
        buybackRate = Math.max(0, Math.min(1, c.getDouble("economie.taux-rachat", 0.25)));
        articles.clear();
        for (Map<?, ?> m : c.getMapList("boutique.articles")) {
            Object spec = m.get("objet");
            if (spec == null || !(m.get("prix") instanceof Number price)) {
                continue;
            }
            List<ItemStack> made = plugin.specs().create(spec.toString(), true);
            if (made.isEmpty()) {
                plugin.getLogger().warning("Boutique : objet inconnu « " + spec + " », ignoré.");
                continue;
            }
            int amount = 0;
            for (ItemStack it : made) {
                amount += it.getAmount();
            }
            ItemStack icon = made.get(0).clone();
            icon.setAmount(Math.min(amount, icon.getMaxStackSize()));
            articles.add(new Article(spec.toString(), keyOf(spec.toString()), amount, Math.max(0, price.longValue()), icon));
        }
        buyback.clear();
        ConfigurationSection bb = c.getConfigurationSection("boutique.rachat");
        if (bb != null) {
            for (String k : bb.getKeys(false)) {
                buyback.put(k.contains(":") ? k.toLowerCase(Locale.ROOT) : "objet:" + k.toLowerCase(Locale.ROOT), bb.getLong(k));
            }
        }
        offers.clear();
        int n = 0;
        for (Map<?, ?> m : c.getMapList("colis.offres")) {
            Object name = m.get("nom");
            String vehicle = m.get("vehicule") == null ? null : m.get("vehicule").toString().toLowerCase(Locale.ROOT);
            if (name == null || !(m.get("prix") instanceof Number price) || (vehicle == null && !(m.get("contenu") instanceof List<?>))) {
                continue;
            }
            if (vehicle != null && plugin.vehicles().def(vehicle) == null) {
                plugin.getLogger().warning("Colis : véhicule inconnu « " + vehicle + " », ignoré.");
                continue;
            }
            Material icon = m.get("icone") == null ? null : Material.matchMaterial(m.get("icone").toString());
            List<String> contents = new ArrayList<>();
            if (m.get("contenu") instanceof List<?> list) {
                for (Object o : list) {
                    contents.add(String.valueOf(o));
                }
            }
            offers.add(new Offer("offre" + (++n), name.toString(), icon == null ? Material.BARREL : icon, Math.max(0, price.longValue()),
                    contents, vehicle));
        }
        plugin.getLogger().info("Téléphone : " + articles.size() + " articles en boutique, " + offers.size() + " colis.");
    }

    public List<Article> articles() {
        return articles;
    }

    public List<Offer> offers() {
        return offers;
    }

    /** « arme:ak12 », « munition:9mm:40 » → « arme:ak12 », « munition:9mm ». */
    private static String keyOf(String spec) {
        String[] p = spec.trim().toLowerCase(Locale.ROOT).split(":");
        String kind = switch (p[0]) {
            case "gun" -> "arme";
            case "ammo" -> "munition";
            case "item" -> "objet";
            case "med" -> "soin";
            case "vehicle" -> "vehicule";
            case "armor", "tenue" -> "armure";
            default -> p[0];
        };
        if (kind.equals("armure") && p.length > 2) {
            fr.orbit.extraction.armor.Armors.Piece piece = fr.orbit.extraction.armor.Armors.Piece.of(p[2]);
            if (piece != null) {
                return kind + ":" + p[1] + ":" + piece.id();       // une pièce de tenue se vend à la pièce
            }
        }
        return p.length > 1 ? kind + ":" + p[1] : kind;
    }

    // ------------------------------------------------------------------ objet

    public ItemStack item() {
        ItemStack it = new ItemStack(Material.ECHO_SHARD);
        it.editMeta(m -> {
            m.displayName(Text.item("<#E8695C><b>Téléphone</b>"));
            m.lore(Text.lore(List.of(
                    "<#8A919B>Raid, boutique, colis, messages, coffre.",
                    "<#5A6270>Clic droit : ouvrir  •  ne se perd jamais")));
            m.getPersistentDataContainer().set(Keys.PHONE, PersistentDataType.BYTE, (byte) 1);
            m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        });
        it.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData().addString("orbitguns:phone").build());
        return it;
    }

    public boolean isPhone(ItemStack it) {
        return it != null && it.getType() == Material.ECHO_SHARD && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(Keys.PHONE, PersistentDataType.BYTE);
    }

    /** S'assure que le joueur a son téléphone (dernière case de la barre si elle est libre) et ses crédits de départ. */
    public void give(Player p) {
        Profile pr = plugin.profiles().get(p);
        if (pr.credits < 0) {
            pr.credits = startCredits;
            plugin.profiles().save(pr);
        }
        PlayerInventory inv = p.getInventory();
        for (ItemStack it : inv.getContents()) {
            if (isPhone(it)) {
                return;
            }
        }
        ItemStack held = inv.getItem(PHONE_SLOT);
        if (held == null || held.getType().isAir()) {
            inv.setItem(PHONE_SLOT, item());
        } else if (!inv.addItem(item()).isEmpty()) {
            inv.setItem(PHONE_SLOT, item());     // inventaire plein : l'objet de la case 9 tombe au sol
            p.getWorld().dropItemNaturally(p.getLocation(), held);
        }
    }

    // ------------------------------------------------------------------ crédits

    public long credits(Player p) {
        return Math.max(0, plugin.profiles().get(p).credits);
    }

    public void reward(Player p, long amount, String reason) {
        if (amount <= 0) {
            return;
        }
        long base = amount;
        amount = plugin.ranks().boosted(p, amount);
        Profile pr = plugin.profiles().get(p);
        pr.credits = Math.max(0, pr.credits) + amount;
        Text.msg(p, "<#2ECC71>+" + amount + " crédits</#2ECC71> <#8A919B>(" + reason
                + (amount > base ? ", bonus " + plugin.ranks().of(p).tag() + "<#8A919B> +" + (amount - base) : "") + ")");
    }

    public void add(Player p, long amount) {
        Profile pr = plugin.profiles().get(p);
        pr.credits = Math.max(0, Math.max(0, pr.credits) + amount);
    }

    public boolean pay(Player p, long price) {
        Profile pr = plugin.profiles().get(p);
        if (Math.max(0, pr.credits) < price) {
            return false;
        }
        pr.credits -= price;
        return true;
    }

    /** Prix de rachat à l'unité d'un objet du joueur (0 = la boutique n'en veut pas). */
    public long sellPrice(ItemStack it) {
        if (it == null || it.getType().isAir() || isPhone(it)) {
            return 0;
        }
        GunItems items = plugin.items();
        String key;
        if (items.gunOf(it) != null) {
            key = "arme:" + items.gunOf(it).id();
        } else if (items.ammoOf(it) != null) {
            key = "munition:" + items.ammoOf(it);
        } else if (items.grenadeOf(it) != null) {
            key = "grenade:" + items.grenadeOf(it).id();
        } else if (plugin.meds().defOf(it) != null) {
            key = "soin:" + plugin.meds().defOf(it).id();
        } else if (plugin.armors().keyOf(it) != null) {
            key = "armure:" + plugin.armors().keyOf(it);
        } else if (plugin.vehicles().toolOf(it) != null) {
            key = "vehicule:" + plugin.vehicles().toolOf(it);
        } else if (plugin.miniMap().isMap(it)) {
            return 0;
        } else if (it.hasItemMeta() && it.getItemMeta().hasDisplayName()) {
            return 0;       // objet renommé ou spécial : pas de rachat
        } else {
            key = "objet:" + it.getType().name().toLowerCase(Locale.ROOT);
        }
        Long fixed = buyback.get(key);
        if (fixed != null) {
            return Math.max(0, fixed);
        }
        for (Article a : articles) {
            if (a.key().equals(key)) {
                return (long) Math.floor(a.price() * buybackRate / Math.max(1, a.amount()));
            }
        }
        return 0;
    }

    // ------------------------------------------------------------------ messages

    /** Le prochain message tapé dans le chat part chez {@code to}. */
    public void compose(Player from, Player to) {
        drafts.put(from.getUniqueId(), new Draft(to.getUniqueId(), to.getName(), System.currentTimeMillis() + 60_000));
        from.closeInventory();
        Text.msg(from, "Écris ton message pour <#6EA2FF>" + Text.esc(to.getName())
                + "</#6EA2FF> dans le chat <#5A6270>(« annuler » pour abandonner, 60 s)");
        from.playSound(from.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
    }

    public void send(Player from, UUID toId, String toName, String raw) {
        String text = raw.replaceAll("\\s+", " ").trim();
        if (text.length() > MESSAGE_MAX) {
            text = text.substring(0, MESSAGE_MAX);
        }
        Player to = Bukkit.getPlayer(toId);
        if (to == null) {
            Text.msg(from, "<#C0392B>" + Text.esc(toName) + " n'est plus en ligne.</#C0392B> Message non envoyé.");
            return;
        }
        if (text.isEmpty()) {
            return;
        }
        Profile pr = plugin.profiles().get(to);
        pr.addMessage(new Profile.Message(System.currentTimeMillis(), from.getName(), text, false));
        plugin.profiles().save(pr);
        to.sendMessage(Text.mm("<#6EA2FF>✉ " + Text.esc(from.getName()) + "</#6EA2FF> <#5A6270>»</#5A6270> <#D7DCE2>" + Text.esc(text)));
        to.playSound(to.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1.6f);
        from.sendMessage(Text.mm("<#6EA2FF>✉ à " + Text.esc(to.getName()) + "</#6EA2FF> <#5A6270>»</#5A6270> <#8A919B>" + Text.esc(text)));
        from.playSound(from.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.8f);
    }

    public void notifyUnread(Player p) {
        int n = plugin.profiles().get(p).unread();
        if (n > 0) {
            Text.msg(p, "<#6EA2FF>✉ " + n + " message" + (n > 1 ? "s" : "") + " non lu" + (n > 1 ? "s" : "")
                    + "</#6EA2FF> <#8A919B>— ouvre ton téléphone.");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        Draft d = drafts.remove(p.getUniqueId());
        if (d == null || System.currentTimeMillis() > d.expires()) {
            return;
        }
        e.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(e.message());
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) {
                return;
            }
            if (text.trim().equalsIgnoreCase("annuler")) {
                Text.msg(p, "<#8A919B>Message abandonné.");
            } else {
                send(p, d.to(), d.toName(), text);
            }
        });
    }

    // ------------------------------------------------------------------ événements de l'objet

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (!isPhone(e.getItem())) {
            return;
        }
        e.setCancelled(true);
        plugin.phoneMenu().open(e.getPlayer(), PhoneMenu.Screen.HOME);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (isPhone(e.getItemDrop().getItemStack())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (isPhone(e.getOffHandItem()) || isPhone(e.getMainHandItem())) {
            e.setCancelled(true);
        }
    }

    /** Le téléphone reste dans l'inventaire du joueur : pas de dépôt dans un coffre, la planque ou un colis. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (e.getView().getTopInventory().getType() == InventoryType.CRAFTING) {
            return;
        }
        ItemStack hotbar = e.getHotbarButton() >= 0 ? e.getWhoClicked().getInventory().getItem(e.getHotbarButton()) : null;
        if (isPhone(e.getCurrentItem()) || isPhone(e.getCursor()) || isPhone(hotbar)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getType() != InventoryType.CRAFTING && isPhone(e.getOldCursor())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        drafts.remove(e.getPlayer().getUniqueId());
    }
}
