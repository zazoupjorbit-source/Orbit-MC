package fr.orbit.mmo.market;

import fr.orbit.mmo.OrbitMMO;
import fr.orbit.mmo.item.Items;
import fr.orbit.mmo.player.PlayerData;
import fr.orbit.mmo.spell.SpellBar;
import fr.orbit.mmo.util.Text;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Hôtel des ventes : annonces à prix fixe entre joueurs, communes à tous les étages.
 * L'objet est retiré de l'inventaire du vendeur à la mise en vente ; l'or de la vente (moins la taxe)
 * lui est versé tout de suite s'il est connecté, sinon à sa prochaine connexion.
 * Une annonce expirée ou retirée revient dans « Mes ventes », à récupérer.
 */
public final class Market implements Listener {

    public static final class Listing {
        public final UUID id;
        public final UUID seller;
        public final String sellerName;
        public final ItemStack item;
        public final long price;
        public final long created;
        public final long expires;

        Listing(UUID id, UUID seller, String sellerName, ItemStack item, long price, long created, long expires) {
            this.id = id;
            this.seller = seller;
            this.sellerName = sellerName;
            this.item = item;
            this.price = price;
            this.created = created;
            this.expires = expires;
        }
    }

    /** Mise en vente en attente du prix tapé dans le chat. */
    private record Pending(int slot, ItemStack snapshot, long until) {
    }

    public static final long MAX_PRICE = 10_000_000L;

    private final OrbitMMO plugin;
    private final File file;
    private final Map<UUID, Listing> listings = new LinkedHashMap<>();
    private final Map<UUID, Long> payouts = new HashMap<>();
    private final Map<UUID, List<ItemStack>> returns = new HashMap<>();
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public Market(OrbitMMO plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "hdv.yml");
        load();
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::expire, 20L * 30, 20L * 60);
    }

    // ------------------------------------------------------------------ Réglages

    public int taxPercent() {
        return Math.max(0, Math.min(50, plugin.getConfig().getInt("hdv.taxe", 5)));
    }

    public int durationHours() {
        return Math.max(1, plugin.getConfig().getInt("hdv.duree-heures", 48));
    }

    public int maxListings() {
        return Math.max(1, plugin.getConfig().getInt("hdv.max-annonces", 10));
    }

    public long net(long price) {
        return price - tax(price);
    }

    public long tax(long price) {
        return (long) Math.floor(price * taxPercent() / 100.0);
    }

    // ------------------------------------------------------------------ Lecture

    /** Annonces en cours, les plus récentes d'abord. */
    public List<Listing> active() {
        long now = System.currentTimeMillis();
        List<Listing> out = new ArrayList<>();
        for (Listing l : listings.values()) {
            if (l.expires > now) {
                out.add(l);
            }
        }
        out.sort(Comparator.comparingLong((Listing l) -> l.created).reversed());
        return out;
    }

    public List<Listing> of(UUID seller) {
        List<Listing> out = new ArrayList<>();
        for (Listing l : active()) {
            if (l.seller.equals(seller)) {
                out.add(l);
            }
        }
        return out;
    }

    public Listing get(UUID id) {
        Listing l = listings.get(id);
        return l != null && l.expires > System.currentTimeMillis() ? l : null;
    }

    public List<ItemStack> returns(UUID seller) {
        return returns.getOrDefault(seller, List.of());
    }

    public long pendingGold(UUID seller) {
        return payouts.getOrDefault(seller, 0L);
    }

    /** Peut-on mettre cet objet en vente ? null si oui, sinon la raison. */
    public String cannotSell(Player p, ItemStack it) {
        if (it == null || it.isEmpty()) {
            return "Case vide.";
        }
        if (SpellBar.isLocked(it)) {
            return "Les objets de sort et « Mon personnage » ne se vendent pas.";
        }
        if (fr.orbit.mmo.inn.InnService.isCrystal(it)) {
            return "Votre cristal de téléportation est lié à vous : il ne se vend pas.";
        }
        String bound = fr.orbit.mmo.item.Binding.tradeBlock(it);
        if (bound != null) {
            return bound;
        }
        if ("weapon".equals(Items.type(it))) {
            int weapons = 0;
            for (ItemStack o : p.getInventory().getStorageContents()) {
                if ("weapon".equals(Items.type(o))) {
                    weapons++;
                }
            }
            if (weapons <= 1) {
                return "Gardez au moins une arme.";
            }
        }
        if (of(p.getUniqueId()).size() >= maxListings()) {
            return "Vous avez déjà " + maxListings() + " annonces en cours (maximum).";
        }
        return null;
    }

    // ------------------------------------------------------------------ Mise en vente

    /** Retient l'objet de la case et demande le prix dans le chat. */
    public void askPrice(Player p, int slot) {
        ItemStack it = p.getInventory().getItem(slot);
        String why = cannotSell(p, it);
        if (why != null) {
            Text.msg(p, "<#FF5C5C>" + why);
            return;
        }
        pending.put(p.getUniqueId(), new Pending(slot, it.clone(), System.currentTimeMillis() + 60_000));
        p.closeInventory();
        long counter = Items.unitSellPrice(it) * it.getAmount();
        p.sendMessage(Text.mm(Text.PREFIX + "<#FFD66E>Mise en vente : </#FFD66E>" + it.getAmount() + "× ").append(it.effectiveName()));
        Text.msg(p, "<#D7DCE2>Tapez le <b>prix total</b> dans le chat (ex. <white>250</white>), ou <white>annuler</white>."
                + (counter > 0 ? " <#5A6270>(le forgeron en donne" + Text.num(counter) + " or)" : ""));
        Text.msg(p, "<#5A6270>Taxe de " + taxPercent() + " % prélevée à la vente · annonce de " + durationHours() + " h.");
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.4f);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        if (!pending.containsKey(p.getUniqueId())) {
            return;
        }
        e.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> answer(p, text));
    }

    private void answer(Player p, String text) {
        Pending pd = pending.get(p.getUniqueId());
        if (pd == null || !p.isOnline()) {
            return;
        }
        if (System.currentTimeMillis() > pd.until()) {
            pending.remove(p.getUniqueId());
            Text.msg(p, "<#8C96A3>Mise en vente annulée (délai dépassé).");
            return;
        }
        String t = text.toLowerCase(Locale.ROOT);
        if (t.equals("annuler") || t.equals("cancel") || t.equals("non")) {
            pending.remove(p.getUniqueId());
            Text.msg(p, "<#8C96A3>Mise en vente annulée.");
            return;
        }
        long price;
        try {
            price = Long.parseLong(t.replaceAll("[\\s.  ]", "").replace("or", ""));
        } catch (NumberFormatException ex) {
            Text.msg(p, "<#FF5C5C>Prix invalide : tapez un nombre (ex. 250), ou « annuler ».");
            return;
        }
        if (price < 1 || price > MAX_PRICE) {
            Text.msg(p, "<#FF5C5C>Le prix doit être entre 1 et " + Text.num(MAX_PRICE) + " or.");
            return;
        }
        pending.remove(p.getUniqueId());
        ItemStack now = p.getInventory().getItem(pd.slot());
        if (now == null || !now.equals(pd.snapshot())) {
            Text.msg(p, "<#FF5C5C>L'objet a bougé de son emplacement : recommencez la mise en vente.");
            return;
        }
        String why = cannotSell(p, now);
        if (why != null) {
            Text.msg(p, "<#FF5C5C>" + why);
            return;
        }
        p.getInventory().setItem(pd.slot(), null);
        long created = System.currentTimeMillis();
        Listing l = new Listing(UUID.randomUUID(), p.getUniqueId(), p.getName(), pd.snapshot(), price, created,
                created + durationHours() * 3_600_000L);
        listings.put(l.id, l);
        save();
        p.sendMessage(Text.mm(Text.PREFIX + "<#5EE89A>Annonce publiée : </#5EE89A>" + l.item.getAmount() + "× ")
                .append(l.item.effectiveName())
                .append(Text.mm(" <#8C96A3>pour <#FFD66E>" + Text.num(price) + " or <#5A6270>(vous toucherez " + Text.num(net(price))
                        + " or, " + durationHours() + " h)")));
        p.playSound(p.getLocation(), Sound.BLOCK_BELL_USE, 0.6f, 1.4f);
    }

    // ------------------------------------------------------------------ Achat, retrait, récupération

    /** Achat d'une annonce. Rend un message d'erreur, ou null si c'est fait. */
    public String buy(Player buyer, PlayerData d, UUID id) {
        Listing l = get(id);
        if (l == null) {
            return "Cette annonce n'est plus disponible.";
        }
        if (l.seller.equals(buyer.getUniqueId())) {
            return "C'est votre propre annonce.";
        }
        if (d.gold < l.price) {
            return "Il vous manque " + Text.num(l.price - d.gold) + " or.";
        }
        d.gold -= l.price;
        listings.remove(l.id);
        Items.give(buyer, l.item.clone());
        long gain = net(l.price);
        Player seller = Bukkit.getPlayer(l.seller);
        PlayerData sd = seller == null ? null : plugin.players().loaded(l.seller);
        if (seller != null && sd != null) {
            sd.gold += gain;
            seller.sendMessage(Text.mm(Text.PREFIX + "<#5EE89A>Vendu à l'hôtel des ventes : </#5EE89A>" + l.item.getAmount() + "× ")
                    .append(l.item.effectiveName())
                    .append(Text.mm(" <#FFD66E>+" + Text.num(gain) + " or <#5A6270>(acheteur : " + Text.esc(buyer.getName()) + ")")));
            seller.playSound(seller.getLocation(), Sound.BLOCK_BELL_USE, 0.8f, 1.6f);
        } else {
            payouts.merge(l.seller, gain, Long::sum);
        }
        save();
        buyer.playSound(buyer.getLocation(), Sound.BLOCK_BELL_USE, 0.7f, 1.2f);
        buyer.playSound(buyer.getLocation(), Sound.ENTITY_VILLAGER_YES, 0.6f, 1.1f);
        buyer.sendMessage(Text.mm(Text.PREFIX + "<#5EE89A>Acheté : </#5EE89A>" + l.item.getAmount() + "× ").append(l.item.effectiveName())
                .append(Text.mm(" <#FFD66E>−" + Text.num(l.price) + " or <#5A6270>(vendeur : " + Text.esc(l.sellerName) + ")")));
        return null;
    }

    /** Retire une de ses annonces : l'objet revient directement dans l'inventaire. */
    public boolean cancel(Player p, UUID id) {
        Listing l = listings.get(id);
        if (l == null || !l.seller.equals(p.getUniqueId())) {
            return false;
        }
        listings.remove(id);
        Items.give(p, l.item.clone());
        save();
        p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, 0.6f, 1.2f);
        return true;
    }

    /** Récupère un objet revenu (annonce expirée). false s'il n'y a plus de place. */
    public boolean collect(Player p, int index) {
        List<ItemStack> list = returns.get(p.getUniqueId());
        if (list == null || index < 0 || index >= list.size()) {
            return false;
        }
        if (p.getInventory().firstEmpty() < 0) {
            Text.msg(p, "<#FF5C5C>Inventaire plein.");
            return false;
        }
        ItemStack it = list.remove(index);
        if (list.isEmpty()) {
            returns.remove(p.getUniqueId());
        }
        Items.give(p, it);
        save();
        return true;
    }

    /** Verse l'or des ventes faites hors ligne (si la connexion ne l'a pas déjà fait). */
    public void claimGold(Player p, PlayerData d) {
        Long gold = payouts.remove(p.getUniqueId());
        if (gold == null || gold <= 0) {
            return;
        }
        d.gold += gold;
        save();
        Text.msg(p, "<#5EE89A>Hôtel des ventes : <#FFD66E>+" + Text.num(gold) + " or");
        p.playSound(p.getLocation(), Sound.BLOCK_BELL_USE, 0.8f, 1.6f);
    }

    // ------------------------------------------------------------------ Expiration, connexion

    private void expire() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (var it = listings.values().iterator(); it.hasNext(); ) {
            Listing l = it.next();
            if (l.expires > now) {
                continue;
            }
            it.remove();
            returns.computeIfAbsent(l.seller, k -> new ArrayList<>()).add(l.item);
            changed = true;
            Player s = Bukkit.getPlayer(l.seller);
            if (s != null) {
                s.sendMessage(Text.mm(Text.PREFIX + "<#8C96A3>Annonce expirée : </#8C96A3>").append(l.item.effectiveName())
                        .append(Text.mm(" <#8C96A3>— à récupérer dans « Mes ventes » à l'hôtel des ventes.")));
            }
        }
        if (changed) {
            save();
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) {
                return;
            }
            PlayerData d = plugin.players().loaded(p.getUniqueId());
            Long gold = payouts.get(p.getUniqueId());
            if (gold != null && gold > 0 && d != null && plugin.progression().valid(d)) {
                payouts.remove(p.getUniqueId());
                d.gold += gold;
                save();
                Text.msg(p, "<#5EE89A>Hôtel des ventes : vos objets se sont vendus pendant votre absence. <#FFD66E>+" + Text.num(gold) + " or");
                p.playSound(p.getLocation(), Sound.BLOCK_BELL_USE, 0.8f, 1.6f);
            }
            int back = returns(p.getUniqueId()).size();
            if (back > 0) {
                Text.msg(p, "<#8C96A3>Hôtel des ventes : " + back + " objet(s) invendu(s) à récupérer dans « Mes ventes ».");
            }
        }, 60L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        pending.remove(e.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------ Fichier

    private static String encode(ItemStack it) {
        return Base64.getEncoder().encodeToString(it.serializeAsBytes());
    }

    private static ItemStack decode(String s) {
        return ItemStack.deserializeBytes(Base64.getDecoder().decode(s));
    }

    private void load() {
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection ls = y.getConfigurationSection("listings");
        if (ls != null) {
            for (String k : ls.getKeys(false)) {
                ConfigurationSection s = ls.getConfigurationSection(k);
                try {
                    Listing l = new Listing(UUID.fromString(k), UUID.fromString(s.getString("seller")), s.getString("seller-name", "?"),
                            decode(s.getString("item")), s.getLong("price"), s.getLong("created"), s.getLong("expires"));
                    listings.put(l.id, l);
                } catch (RuntimeException ex) {
                    plugin.getLogger().log(Level.WARNING, "Annonce illisible dans hdv.yml : " + k, ex);
                }
            }
        }
        ConfigurationSection po = y.getConfigurationSection("payouts");
        if (po != null) {
            for (String k : po.getKeys(false)) {
                payouts.put(UUID.fromString(k), po.getLong(k));
            }
        }
        ConfigurationSection rs = y.getConfigurationSection("returns");
        if (rs != null) {
            for (String k : rs.getKeys(false)) {
                List<ItemStack> items = new ArrayList<>();
                for (String s : rs.getStringList(k)) {
                    try {
                        items.add(decode(s));
                    } catch (RuntimeException ex) {
                        plugin.getLogger().log(Level.WARNING, "Objet illisible dans hdv.yml (" + k + ")", ex);
                    }
                }
                if (!items.isEmpty()) {
                    returns.put(UUID.fromString(k), items);
                }
            }
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Listing l : listings.values()) {
            String base = "listings." + l.id + ".";
            y.set(base + "seller", l.seller.toString());
            y.set(base + "seller-name", l.sellerName);
            y.set(base + "item", encode(l.item));
            y.set(base + "price", l.price);
            y.set(base + "created", l.created);
            y.set(base + "expires", l.expires);
        }
        for (Map.Entry<UUID, Long> e : payouts.entrySet()) {
            y.set("payouts." + e.getKey(), e.getValue());
        }
        for (Map.Entry<UUID, List<ItemStack>> e : returns.entrySet()) {
            List<String> out = new ArrayList<>();
            for (ItemStack it : e.getValue()) {
                out.add(encode(it));
            }
            y.set("returns." + e.getKey(), out);
        }
        try {
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Échec de la sauvegarde de hdv.yml", ex);
        }
    }
}
