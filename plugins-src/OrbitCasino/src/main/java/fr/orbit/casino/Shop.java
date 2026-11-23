package fr.orbit.casino;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Boutique d'Orbit Coins : les cosmétiques du lobby (catalogue d'OrbitDevCore, prix selon la rareté) et les
 * titres de chat du casino. Seule source des cosmétiques avec les permissions données par le staff.
 */
public final class Shop implements Listener {

    /** Une catégorie du catalogue d'OrbitDevCore : section du cosmetics.yml → type de l'API. */
    private record Category(String section, String type, String label, Material icon) {
    }

    private static final List<Category> CATEGORIES = List.of(
            new Category("trails", "trail", "Traînées", Material.BLAZE_POWDER),
            new Category("auras", "aura", "Auras", Material.AMETHYST_SHARD),
            new Category("hats", "hat", "Chapeaux", Material.LEATHER_HELMET),
            new Category("backs", "back", "Accessoires de dos", Material.ELYTRA),
            new Category("balloons", "balloon", "Ballons", Material.LEAD),
            new Category("pets", "pet", "Compagnons", Material.BONE),
            new Category("mounts", "mount", "Montures", Material.SADDLE));

    public record Item(String type, String id, String name, Material icon, String rarity, long price) {
    }

    public record Title(String id, String text, long price) {
    }

    private final OrbitCasino plugin;
    private final Wallet wallet;
    private final Map<String, List<Item>> catalog = new LinkedHashMap<>();
    private final Map<String, Title> titles = new LinkedHashMap<>();

    public Shop(OrbitCasino plugin, Wallet wallet) {
        this.plugin = plugin;
        this.wallet = wallet;
    }

    // ------------------------------------------------------------------ catalogue

    public void load() {
        catalog.clear();
        titles.clear();
        ConfigurationSection t = plugin.getConfig().getConfigurationSection("boutique.titres");
        if (t != null) {
            for (String id : t.getKeys(false)) {
                titles.put(id, new Title(id, t.getString(id + ".texte", "[" + id + "]"), t.getLong(id + ".prix", 10000)));
            }
        }
        Plugin devCore = Bukkit.getPluginManager().getPlugin("OrbitDevCore");
        if (devCore == null) {
            return;
        }
        File file = new File(devCore.getDataFolder(), "cosmetics.yml");
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (Category cat : CATEGORIES) {
            List<Item> items = new ArrayList<>();
            ConfigurationSection section = yaml.getConfigurationSection(cat.section());
            if (section != null) {
                for (String id : section.getKeys(false)) {
                    ConfigurationSection s = section.getConfigurationSection(id);
                    if (s == null) {
                        continue;
                    }
                    String rarity = rarity(s.getString("rarity"));
                    Material icon = Material.matchMaterial(s.getString("icon", ""));
                    if (icon == null) {
                        icon = Material.matchMaterial(s.getString("item", ""));
                    }
                    if (icon == null || !icon.isItem()) {
                        icon = cat.icon();
                    }
                    long price = plugin.getConfig().getLong("boutique.prix." + rarity, 5000);
                    items.add(new Item(cat.type(), id, s.getString("name", id), icon, rarity, price));
                }
            }
            catalog.put(cat.type(), items);
        }
    }

    private static String rarity(String raw) {
        if (raw == null) {
            return "common";
        }
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "rare" -> "rare";
            case "epic", "epique", "épique" -> "epic";
            case "legendary", "legendaire", "légendaire" -> "legendary";
            default -> "common";
        };
    }

    private static String rarityLabel(String rarity) {
        return switch (rarity) {
            case "rare" -> "<#3A7FFF>Rare";
            case "epic" -> "<#C79BFF>Épique";
            case "legendary" -> "<#E0B864>Légendaire";
            default -> "<#9AA4B2>Commun";
        };
    }

    // ------------------------------------------------------------------ possession

    /** Possédé : acheté en OC, ou donné par le staff (permission posée explicitement, pas l'op par défaut). */
    public boolean owns(Player p, String type, String id) {
        Wallet.Account a = wallet.account(p);
        if (a != null && a.owned.contains(type + ":" + id)) {
            return true;
        }
        String node = "orbitdev.cosmetic." + type + "." + id;
        return p.isPermissionSet(node) && p.hasPermission(node);
    }

    public Title title(Player p) {
        Wallet.Account a = wallet.account(p);
        if (a == null || a.titre == null) {
            return null;
        }
        Title t = titles.get(a.titre);
        return t != null && a.owned.contains("titre:" + t.id()) ? t : null;
    }

    /** Titre devant le pseudo, ajouté autour du format de chat d'OrbitDevCore (ou du format vanilla). */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Title t = title(event.getPlayer());
        if (t == null) {
            return;
        }
        Component head = Txt.mm(t.text());
        ChatRenderer previous = event.renderer();
        event.renderer((source, name, message, viewer) -> Component.text().append(head).append(Component.space())
                .append(previous.render(source, name, message, viewer)).build());
    }

    // ------------------------------------------------------------------ menus

    private String balanceLine(Player p) {
        long b = wallet.balance(p);
        return "<#9AA4B2>Ton solde : " + (b < 0 ? "<#E0484A>indisponible" : Txt.oc(b));
    }

    public void open(Player p) {
        Menu m = new Menu(3, "<#E0B864><b>Boutique</b></#E0B864> <#5A6270>— Orbit Coins");
        m.set(4, Menu.item(Material.SUNFLOWER, Txt.GOLD + "<b>Orbit Coins</b>", balanceLine(p), "",
                "<#5A6270>Gagnés au casino, avec la ration du jour", "<#5A6270>et le temps de jeu. Jamais achetables."));
        m.set(10, Menu.item(Material.NAME_TAG, "<#E0B864><b>Titres de chat</b>", "<#9AA4B2>Visibles sur tous les serveurs.",
                "<#9AA4B2>" + titles.size() + " titres"), e -> openTitles(p));
        int[] slots = {11, 12, 13, 14, 15, 16, 22};
        for (int i = 0; i < CATEGORIES.size() && i < slots.length; i++) {
            Category cat = CATEGORIES.get(i);
            List<Item> items = catalog.getOrDefault(cat.type(), List.of());
            long owned = items.stream().filter(it -> owns(p, it.type(), it.id())).count();
            m.set(slots[i], Menu.item(cat.icon(), "<#62CFF4><b>" + cat.label() + "</b>",
                    "<#9AA4B2>" + owned + " / " + items.size() + " débloqués"), e -> openCategory(p, cat, 0));
        }
        m.set(18, Menu.item(Material.GOLD_INGOT, "<#E0B864><b>Classement</b>", "<#9AA4B2>Les 10 plus riches du réseau"),
                e -> {
                    p.closeInventory();
                    plugin.showTop(p);
                });
        m.set(26, Menu.item(Material.BARRIER, "<#E0484A>Fermer"), e -> p.closeInventory());
        m.fill(Material.BLACK_STAINED_GLASS_PANE);
        m.open(p);
    }

    private void openCategory(Player p, Category cat, int page) {
        List<Item> items = catalog.getOrDefault(cat.type(), List.of());
        int pages = Math.max(1, (items.size() + 44) / 45);
        int current = Math.max(0, Math.min(page, pages - 1));
        Menu m = new Menu(6, "<#62CFF4><b>" + cat.label() + "</b></#62CFF4> <#5A6270>— " + (current + 1) + "/" + pages);
        for (int i = 0; i < 45 && current * 45 + i < items.size(); i++) {
            Item it = items.get(current * 45 + i);
            boolean owned = owns(p, it.type(), it.id());
            long b = wallet.balance(p);
            String status = owned ? "<#7BD88F>✔ Débloqué <#5A6270>— /cosmetiques pour l'équiper"
                    : b >= it.price() ? "<#E0B864>Clique pour l'acheter" : "<#E0484A>Pas assez d'Orbit Coins";
            m.set(i, Menu.item(it.icon(), it.name(), rarityLabel(it.rarity()),
                    "<#9AA4B2>Prix : " + Txt.oc(it.price()), "", status), e -> {
                if (!owned) {
                    confirm(p, it.name(), it.icon(), it.type(), it.id(), it.price(), () -> openCategory(p, cat, current));
                }
            });
        }
        if (current > 0) {
            m.set(45, Menu.item(Material.ARROW, "<#E6EDF3>Page précédente"), e -> openCategory(p, cat, current - 1));
        }
        m.set(48, Menu.item(Material.SUNFLOWER, Txt.GOLD + "<b>Solde</b>", balanceLine(p)));
        m.set(49, Menu.item(Material.OAK_DOOR, "<#E6EDF3>Retour"), e -> open(p));
        if (current < pages - 1) {
            m.set(53, Menu.item(Material.ARROW, "<#E6EDF3>Page suivante"), e -> openCategory(p, cat, current + 1));
        }
        m.fill(Material.BLACK_STAINED_GLASS_PANE);
        m.open(p);
    }

    public void openTitles(Player p) {
        Menu m = new Menu(3, "<#E0B864><b>Titres de chat</b>");
        Title worn = title(p);
        int slot = 9;
        for (Title t : titles.values()) {
            if (slot > 17) {
                break;
            }
            boolean owned = owns(p, "titre", t.id());
            boolean wearing = worn != null && worn.id().equals(t.id());
            String status = wearing ? "<#7BD88F>✔ Porté <#5A6270>— clique pour le retirer"
                    : owned ? "<#62CFF4>Clique pour le porter"
                    : wallet.balance(p) >= t.price() ? "<#E0B864>Clique pour l'acheter" : "<#E0484A>Pas assez d'Orbit Coins";
            m.set(slot++, Menu.item(wearing ? Material.GLOW_ITEM_FRAME : Material.NAME_TAG, t.text() + " <#E6EDF3>" + p.getName(),
                    "<#9AA4B2>Prix : " + Txt.oc(t.price()), "", status), e -> {
                if (wearing) {
                    wallet.setTitle(p, null);
                    Txt.send(p, "Titre retiré.");
                    openTitles(p);
                } else if (owned) {
                    wallet.setTitle(p, t.id());
                    Txt.send(p, "Tu portes maintenant " + t.text() + "<reset>.");
                    Txt.sound(p, Sound.ENTITY_PLAYER_LEVELUP, 1.6f);
                    openTitles(p);
                } else {
                    confirm(p, t.text(), Material.NAME_TAG, "titre", t.id(), t.price(), () -> openTitles(p));
                }
            });
        }
        m.set(4, Menu.item(Material.SUNFLOWER, Txt.GOLD + "<b>Solde</b>", balanceLine(p)));
        m.set(22, Menu.item(Material.OAK_DOOR, "<#E6EDF3>Retour"), e -> open(p));
        m.fill(Material.BLACK_STAINED_GLASS_PANE);
        m.open(p);
    }

    private void confirm(Player p, String name, Material icon, String type, String id, long price, Runnable back) {
        Menu m = new Menu(3, "<#E0B864><b>Confirmer l'achat ?</b>");
        m.set(13, Menu.item(icon, name, "<#9AA4B2>Prix : " + Txt.oc(price), balanceLine(p)));
        m.set(11, Menu.item(Material.LIME_CONCRETE, "<#7BD88F><b>Acheter pour " + Txt.n(price) + " OC</b>"), e -> {
            p.closeInventory();
            wallet.buy(p, type, id, price, ok -> {
                if (ok) {
                    Txt.send(p, "Acheté : " + name + "<reset> pour " + Txt.oc(price) + ". Il te reste " + Txt.oc(wallet.balance(p)) + "."
                            + (type.equals("titre") ? " Porte-le depuis /oc titres." : " Équipe-le avec /cosmetiques."));
                    Txt.sound(p, Sound.ENTITY_PLAYER_LEVELUP, 1.2f);
                    if (type.equals("titre")) {
                        wallet.setTitle(p, id);
                    }
                } else {
                    Txt.send(p, "<#E0484A>Achat impossible : pas assez d'Orbit Coins (ou déjà possédé).");
                    Txt.sound(p, Sound.ENTITY_VILLAGER_NO, 1f);
                }
            });
        });
        m.set(15, Menu.item(Material.RED_CONCRETE, "<#E0484A><b>Annuler</b>"), e -> back.run());
        m.fill(Material.BLACK_STAINED_GLASS_PANE);
        m.open(p);
    }
}
