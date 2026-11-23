package fr.orbit.tycoon;

import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Livre-guide du Tycoon : tout le concept (outils, mondes, commandes, boutique, armures, pets, votes, noyau d'île).
 * Pages en MiniMessage dans plugins/OrbitTycoon/guide.yml (modifiable sans recompiler, puis /tycoon reload).
 * Donné une fois à la première connexion ; /guide le redonne ; le lien de la dernière page (/guide jeter) le supprime.
 */
final class Guide implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final OrbitTycoon plugin;
    private final NamespacedKey marqueLivre, marqueRecu;
    private List<String> pages = List.of();
    private String titre = "Guide d'Orbit Tycoon";

    Guide(OrbitTycoon plugin) {
        this.plugin = plugin;
        this.marqueLivre = new NamespacedKey(plugin, "guide");
        this.marqueRecu = new NamespacedKey(plugin, "guide_recu");
    }

    void charger() {
        File f = new File(plugin.getDataFolder(), "guide.yml");
        if (!f.exists()) plugin.saveResource("guide.yml", false);
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        // Pages par défaut du jar si le fichier du serveur est vide ou abîmé.
        if (y.getStringList("pages").isEmpty()) {
            var in = plugin.getResource("guide.yml");
            if (in != null) y = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        pages = y.getStringList("pages");
        titre = y.getString("titre", titre);
    }

    /** Noms des coffres (<coffre1> … <coffre5>) tirés de config.yml. */
    private TagResolver variables() {
        List<TagResolver> l = new ArrayList<>();
        for (int n = 0; n < 5; n++) l.add(Placeholder.unparsed("coffre" + (n + 1), plugin.coffres().nom(n)));
        return TagResolver.resolver(l);
    }

    ItemStack livre() {
        ItemStack it = new ItemStack(Material.WRITTEN_BOOK);
        TagResolver v = variables();
        List<Component> contenu = pages.stream().map(p -> MM.deserialize(p, v)).toList();
        it.editMeta(BookMeta.class, m -> {
            m.setAuthor("ORBIT");
            m.setTitle("Guide");
            m.itemName(MM.deserialize("<gradient:#62CFF4:#E0B864><bold>" + MM.escapeTags(titre)));
            m.lore(List.of(MM.deserialize("<!italic><gray>Tout savoir sur la station."),
                    MM.deserialize("<!italic><dark_gray>Tu pourras le jeter à la dernière page.")));
            m.pages(contenu);
            m.getPersistentDataContainer().set(marqueLivre, PersistentDataType.BYTE, (byte) 1);
        });
        return it;
    }

    boolean estGuide(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(marqueLivre, PersistentDataType.BYTE);
    }

    private boolean aLeLivre(Player p) {
        for (ItemStack it : p.getInventory().getContents()) if (estGuide(it)) return true;
        return false;
    }

    void donner(Player p) {
        if (aLeLivre(p)) { OrbitTycoon.msg(p, "<gray>Tu as déjà le guide dans ton inventaire."); return; }
        for (ItemStack reste : p.getInventory().addItem(livre()).values()) p.getWorld().dropItemNaturally(p.getLocation(), reste);
        OrbitTycoon.msg(p, "<gray>Tu reçois le <#E0B864>Guide d'Orbit Tycoon</#E0B864><gray>. Ouvre-le (clic droit) pour tout comprendre !");
    }

    boolean commande(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) { OrbitTycoon.msg(s, "Commande réservée aux joueurs."); return true; }
        String sous = args.length > 0 ? args[0].toLowerCase() : "";
        switch (sous) {
            case "jeter", "supprimer" -> jeter(p);
            case "lire" -> p.openBook(Book.book(Component.text(titre), Component.text("ORBIT"),
                    pages.stream().map(x -> MM.deserialize(x, variables())).toList()));
            default -> donner(p);
        }
        return true;
    }

    private void jeter(Player p) {
        int n = 0;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            if (estGuide(inv.getItem(i))) { inv.setItem(i, null); n++; }
        }
        if (estGuide(p.getItemOnCursor())) { p.setItemOnCursor(null); n++; }
        if (n == 0) { OrbitTycoon.msg(p, "<gray>Tu n'as pas de guide sur toi. <dark_gray>(/guide pour le récupérer)"); return; }
        p.closeInventory();
        p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 0.7f);
        OrbitTycoon.msg(p, "<gray>Guide jeté. Tu peux le relire quand tu veux : <#E0B864>/guide</#E0B864><gray> (ou <#E0B864>/guide lire</#E0B864><gray> sans le reprendre).");
    }

    @EventHandler
    public void connexion(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (p.getPersistentDataContainer().has(marqueRecu, PersistentDataType.BYTE)) return;
        p.getPersistentDataContainer().set(marqueRecu, PersistentDataType.BYTE, (byte) 1);
        // Un peu après l'arrivée (équipement et tuto d'abord).
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (p.isOnline()) donner(p); }, 60L);
    }
}
