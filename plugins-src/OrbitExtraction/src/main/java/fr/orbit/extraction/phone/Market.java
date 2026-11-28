package fr.orbit.extraction.phone;

import fr.orbit.extraction.OrbitExtraction;
import fr.orbit.extraction.gun.GunItems;
import fr.orbit.extraction.raid.Profile;
import fr.orbit.extraction.util.Text;
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
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Hôtel des ventes entre joueurs, ouvert depuis la tablette. Un joueur met en vente un objet de son inventaire au
 * prix qu'il veut ; l'acheteur paie en crédits, le vendeur touche le prix moins la taxe — tout de suite s'il est
 * connecté, à sa prochaine connexion sinon. Une annonce expirée ou retirée revient au vendeur (bouton Récupérer).
 * Tout est enregistré dans marche.yml à chaque changement.
 */
public final class Market implements Listener {

    public record Listing(String id, UUID seller, String sellerName, long price, long created, ItemStack item) {
    }

    private record Prompt(int slot, ItemStack item, long expires) {
    }

    private final OrbitExtraction plugin;
    private final File file;
    private final Map<String, Listing> listings = new LinkedHashMap<>();
    private final Map<UUID, Long> owed = new HashMap<>();
    private final Map<UUID, List<ItemStack>> returns = new HashMap<>();
    private final Map<UUID, Prompt> prompts = new ConcurrentHashMap<>();

    private boolean enabled = true;
    private double tax;
    private long lifetimeMs;
    private int baseSlots, slotsPerRank;
    private long minPrice, maxPrice;

    public Market(OrbitExtraction plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "marche.yml");
    }

    public void load(ConfigurationSection c) {
        enabled = c == null || c.getBoolean("actif", true);
        tax = c == null ? 0.05 : Math.max(0, Math.min(90, c.getDouble("taxe", 5))) / 100.0;
        lifetimeMs = (long) ((c == null ? 48 : Math.max(1, c.getDouble("duree-heures", 48))) * 3_600_000);
        baseSlots = c == null ? 5 : Math.max(1, c.getInt("annonces-max", 5));
        slotsPerRank = c == null ? 2 : Math.max(0, c.getInt("annonces-par-rang", 2));
        minPrice = c == null ? 1 : Math.max(1, c.getLong("prix-min", 1));
        maxPrice = c == null ? 1_000_000 : Math.max(minPrice, c.getLong("prix-max", 1_000_000));
    }

    /** Lit marche.yml (une seule fois, au démarrage). */
    public void loadData() {
        listings.clear();
        owed.clear();
        returns.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection ls = y.getConfigurationSection("annonces");
        if (ls != null) {
            for (String id : ls.getKeys(false)) {
                ConfigurationSection s = ls.getConfigurationSection(id);
                ItemStack it = s == null ? null : decode(s.getString("objet"));
                UUID seller = s == null ? null : uuid(s.getString("vendeur"));
                if (it == null || seller == null) {
                    plugin.getLogger().warning("Hôtel des ventes : annonce « " + id + " » illisible, ignorée.");
                    continue;
                }
                listings.put(id, new Listing(id, seller, s.getString("nom", "?"), s.getLong("prix"), s.getLong("date"), it));
            }
        }
        ConfigurationSection os = y.getConfigurationSection("dus");
        if (os != null) {
            for (String k : os.getKeys(false)) {
                UUID id = uuid(k);
                if (id != null && os.getLong(k) > 0) {
                    owed.put(id, os.getLong(k));
                }
            }
        }
        ConfigurationSection rs = y.getConfigurationSection("retours");
        if (rs != null) {
            for (String k : rs.getKeys(false)) {
                UUID id = uuid(k);
                List<ItemStack> items = new ArrayList<>();
                for (String data : rs.getStringList(k)) {
                    ItemStack it = decode(data);
                    if (it != null) {
                        items.add(it);
                    }
                }
                if (id != null && !items.isEmpty()) {
                    returns.put(id, items);
                }
            }
        }
        plugin.getLogger().info("Hôtel des ventes : " + listings.size() + " annonce(s) en cours.");
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        ConfigurationSection ls = y.createSection("annonces");
        for (Listing l : listings.values()) {
            ConfigurationSection s = ls.createSection(l.id());
            s.set("vendeur", l.seller().toString());
            s.set("nom", l.sellerName());
            s.set("prix", l.price());
            s.set("date", l.created());
            s.set("objet", Base64.getEncoder().encodeToString(l.item().serializeAsBytes()));
        }
        ConfigurationSection os = y.createSection("dus");
        owed.forEach((id, n) -> os.set(id.toString(), n));
        ConfigurationSection rs = y.createSection("retours");
        returns.forEach((id, items) -> {
            List<String> out = new ArrayList<>();
            for (ItemStack it : items) {
                out.add(Base64.getEncoder().encodeToString(it.serializeAsBytes()));
            }
            rs.set(id.toString(), out);
        });
        try {
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Échec de la sauvegarde de marche.yml", e);
        }
    }

    private static UUID uuid(String s) {
        try {
            return s == null ? null : UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private ItemStack decode(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(s));
        } catch (RuntimeException e) {
            return null;
        }
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::expire, 200L, 1200L);
    }

    /** Les annonces trop vieilles reviennent à leur vendeur. */
    private void expire() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        Iterator<Listing> it = listings.values().iterator();
        while (it.hasNext()) {
            Listing l = it.next();
            if (now - l.created() >= lifetimeMs) {
                it.remove();
                returns.computeIfAbsent(l.seller(), k -> new ArrayList<>()).add(l.item());
                changed = true;
                Player seller = Bukkit.getPlayer(l.seller());
                if (seller != null) {
                    Text.msg(seller, "<#E05CB8>Hôtel des ventes</#E05CB8> <#8A919B>— ton annonce <#D7DCE2>" + Text.esc(name(l.item()))
                            + "</#D7DCE2> a expiré. Récupère l'objet depuis la tablette.");
                }
            }
        }
        if (changed) {
            save();
        }
    }

    // ------------------------------------------------------------------ lecture

    public boolean enabled() {
        return enabled;
    }

    /** Annonces, de la plus récente à la plus ancienne ; {@code only} non nul = celles de ce vendeur. */
    public List<Listing> list(UUID only) {
        List<Listing> out = new ArrayList<>();
        for (Listing l : listings.values()) {
            if (only == null || l.seller().equals(only)) {
                out.add(0, l);
            }
        }
        return out;
    }

    public int count(UUID seller) {
        int n = 0;
        for (Listing l : listings.values()) {
            if (l.seller().equals(seller)) {
                n++;
            }
        }
        return n;
    }

    public int maxListings(Player p) {
        return baseSlots + slotsPerRank * Math.max(0, plugin.ranks().of(p).level());
    }

    public int waiting(UUID id) {
        List<ItemStack> r = returns.get(id);
        return r == null ? 0 : r.size();
    }

    public int taxPercent() {
        return (int) Math.round(tax * 100);
    }

    public long hoursLeft(Listing l) {
        return Math.max(0, (l.created() + lifetimeMs - System.currentTimeMillis()) / 3_600_000);
    }

    /** Nom lisible d'un objet : son nom affiché, sinon le matériau. */
    public static String name(ItemStack it) {
        if (it.hasItemMeta() && it.getItemMeta().hasDisplayName()) {
            return PlainTextComponentSerializer.plainText().serialize(it.getItemMeta().displayName());
        }
        return it.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    // ------------------------------------------------------------------ mise en vente

    /** Maj + clic sur un objet de son inventaire, hôtel des ventes ouvert : le prix se tape ensuite dans le chat. */
    public void startSale(Player p, int slot, ItemStack it) {
        if (it == null || it.getType().isAir()) {
            return;
        }
        if (!enabled) {
            deny(p, "<#C0392B>L'hôtel des ventes est fermé.");
            return;
        }
        if (plugin.raid().inRaid(p)) {
            deny(p, "<#C0392B>On ne met en vente qu'à la planque.");
            return;
        }
        if (plugin.phone().isPhone(it) || plugin.miniMap().isMap(it)) {
            deny(p, "<#8A919B>Cet objet ne se vend pas.");
            return;
        }
        if (count(p.getUniqueId()) >= maxListings(p)) {
            deny(p, "<#C0392B>Tu as déjà " + maxListings(p) + " annonces en cours</#C0392B> <#8A919B>(retire-en une, ou attends une vente).");
            return;
        }
        prompts.put(p.getUniqueId(), new Prompt(slot, it.clone(), System.currentTimeMillis() + 60_000));
        p.closeInventory();
        Text.msg(p, "<#E05CB8>Hôtel des ventes</#E05CB8> <#8A919B>— écris dans le chat le <#D7DCE2>prix</#D7DCE2> de <#D7DCE2>"
                + Text.esc(name(it)) + (it.getAmount() > 1 ? " ×" + it.getAmount() : "") + "</#D7DCE2> en crédits "
                + "<#5A6270>(« annuler » pour abandonner, 60 s ; taxe de " + taxPercent() + " % à la vente)");
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        Prompt pr = prompts.remove(p.getUniqueId());
        if (pr == null || System.currentTimeMillis() > pr.expires()) {
            return;
        }
        e.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline()) {
                finishSale(p, pr, text);
            }
        });
    }

    private void finishSale(Player p, Prompt pr, String text) {
        if (text.equalsIgnoreCase("annuler")) {
            Text.msg(p, "<#8A919B>Mise en vente abandonnée.");
            return;
        }
        long price;
        try {
            price = Long.parseLong(text.replace(" ", "").replace(" ", ""));
        } catch (NumberFormatException e) {
            deny(p, "<#C0392B>Ce n'est pas un prix</#C0392B> <#8A919B>(un nombre entier de crédits). Mise en vente abandonnée.");
            return;
        }
        if (price < minPrice || price > maxPrice) {
            deny(p, "<#C0392B>Le prix doit être entre " + minPrice + " et " + maxPrice + " crédits.</#C0392B> Mise en vente abandonnée.");
            return;
        }
        if (plugin.raid().inRaid(p)) {
            deny(p, "<#C0392B>On ne met en vente qu'à la planque.");
            return;
        }
        if (count(p.getUniqueId()) >= maxListings(p)) {
            deny(p, "<#C0392B>Tu as déjà " + maxListings(p) + " annonces en cours.");
            return;
        }
        ItemStack now = p.getInventory().getItem(pr.slot());
        if (now == null || !now.isSimilar(pr.item()) || now.getAmount() != pr.item().getAmount()) {
            deny(p, "<#C0392B>L'objet n'est plus à sa place dans ton inventaire.</#C0392B> Mise en vente abandonnée.");
            return;
        }
        p.getInventory().setItem(pr.slot(), null);
        String id = Long.toString(System.currentTimeMillis(), 36) + Integer.toString((int) (Math.random() * 1296), 36);
        listings.put(id, new Listing(id, p.getUniqueId(), p.getName(), price, System.currentTimeMillis(), now.clone()));
        save();
        Text.msg(p, "<#E05CB8>Hôtel des ventes</#E05CB8> <#8A919B>— <#D7DCE2>" + Text.esc(name(now)) + (now.getAmount() > 1 ? " ×" + now.getAmount() : "")
                + "</#D7DCE2> en vente pour <#F5C542>" + price + " crédits</#F5C542>.");
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
        plugin.phoneMenu().open(p, PhoneMenu.Screen.HDV);
    }

    // ------------------------------------------------------------------ achat, retrait, récupération

    /** Vrai si l'achat a eu lieu. */
    public boolean buy(Player p, String id) {
        Listing l = listings.get(id);
        if (l == null) {
            deny(p, "<#8A919B>Cette annonce n'existe plus.");
            return false;
        }
        if (l.seller().equals(p.getUniqueId())) {
            deny(p, "<#8A919B>C'est ton annonce : <#D7DCE2>Maj + clic</#D7DCE2> pour la retirer.");
            return false;
        }
        if (plugin.raid().inRaid(p)) {
            deny(p, "<#C0392B>L'hôtel des ventes ne livre qu'à la planque.");
            return false;
        }
        if (!plugin.phone().pay(p, l.price())) {
            deny(p, "<#C0392B>Pas assez de crédits</#C0392B> <#8A919B>(il t'en faut " + l.price() + ", tu en as " + plugin.phone().credits(p) + ").");
            return false;
        }
        listings.remove(id);
        GunItems.give(p, l.item().clone());
        plugin.profiles().save(plugin.profiles().get(p));
        long gain = l.price() - (long) Math.floor(l.price() * tax);
        Player seller = Bukkit.getPlayer(l.seller());
        if (seller != null) {
            plugin.phone().add(seller, gain);
            plugin.profiles().save(plugin.profiles().get(seller));
            Text.msg(seller, "<#E05CB8>Hôtel des ventes</#E05CB8> <#8A919B>— <#D7DCE2>" + Text.esc(p.getName()) + "</#D7DCE2> a acheté <#D7DCE2>"
                    + Text.esc(name(l.item())) + "</#D7DCE2> : <#2ECC71>+" + gain + " crédits</#2ECC71>"
                    + (gain < l.price() ? " <#5A6270>(taxe " + (l.price() - gain) + ")" : ""));
            seller.playSound(seller.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 0.9f);
        } else {
            owed.merge(l.seller(), gain, Long::sum);
        }
        save();
        Text.msg(p, "<#E05CB8>Hôtel des ventes</#E05CB8> <#8A919B>— acheté <#D7DCE2>" + Text.esc(name(l.item())) + "</#D7DCE2> à <#D7DCE2>"
                + Text.esc(l.sellerName()) + "</#D7DCE2> pour <#F5C542>" + l.price() + " crédits</#F5C542>.");
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.3f);
        return true;
    }

    /** Le vendeur retire son annonce : l'objet lui revient. */
    public boolean withdraw(Player p, String id) {
        Listing l = listings.get(id);
        if (l == null || !l.seller().equals(p.getUniqueId())) {
            return false;
        }
        if (plugin.raid().inRaid(p)) {
            deny(p, "<#C0392B>On ne retire une annonce qu'à la planque.");
            return false;
        }
        listings.remove(id);
        GunItems.give(p, l.item().clone());
        save();
        Text.msg(p, "<#E05CB8>Hôtel des ventes</#E05CB8> <#8A919B>— annonce retirée, l'objet est revenu dans ton inventaire.");
        return true;
    }

    /** Objets revenus (annonces expirées) : rendus au joueur. */
    public void collect(Player p) {
        List<ItemStack> items = returns.get(p.getUniqueId());
        if (items == null || items.isEmpty()) {
            deny(p, "<#8A919B>Rien à récupérer.");
            return;
        }
        if (plugin.raid().inRaid(p)) {
            deny(p, "<#C0392B>On ne récupère ses objets qu'à la planque.");
            return;
        }
        returns.remove(p.getUniqueId());
        for (ItemStack it : items) {
            GunItems.give(p, it);
        }
        save();
        Text.msg(p, "<#E05CB8>Hôtel des ventes</#E05CB8> <#8A919B>— " + items.size() + " objet" + (items.size() > 1 ? "s" : "") + " récupéré"
                + (items.size() > 1 ? "s" : "") + ".");
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1f);
    }

    private static void deny(Player p, String mini) {
        Text.msg(p, mini);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.7f);
    }

    // ------------------------------------------------------------------ connexion

    /** Ventes conclues pendant l'absence du joueur : créditées à son retour. */
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) {
                return;
            }
            Long due = owed.remove(p.getUniqueId());
            if (due != null && due > 0) {
                Profile pr = plugin.profiles().get(p);
                plugin.phone().add(p, due);
                plugin.profiles().save(pr);
                save();
                Text.msg(p, "<#E05CB8>Hôtel des ventes</#E05CB8> <#8A919B>— tes ventes ont rapporté <#2ECC71>+" + due
                        + " crédits</#2ECC71> pendant ton absence.");
            }
            int back = waiting(p.getUniqueId());
            if (back > 0) {
                Text.msg(p, "<#E05CB8>Hôtel des ventes</#E05CB8> <#8A919B>— " + back + " objet" + (back > 1 ? "s" : "")
                        + " invendu" + (back > 1 ? "s" : "") + " à récupérer depuis la tablette.");
            }
        }, 80L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        prompts.remove(e.getPlayer().getUniqueId());
    }
}
