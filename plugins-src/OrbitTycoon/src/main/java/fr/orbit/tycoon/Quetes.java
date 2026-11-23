package fr.orbit.tycoon;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static fr.orbit.tycoon.Progression.objet;

/**
 * Quêtes de prestige de l'Aventurier : pour chaque outil, une quête par palier (prestige 10, 20… 100).
 * Une quête n'est récupérable qu'une fois le palier atteint, et une seule fois.
 * Récompenses dans config.yml (quetes.recompenses) — provisoires en attendant les vraies.
 */
final class Quetes implements Listener {

    private static final int[] SLOTS_OUTILS = {11, 12, 13, 14, 15};
    private static final int[] SLOTS_PALIERS = {20, 21, 22, 23, 24, 29, 30, 31, 32, 33, 38, 39, 40, 41, 42};
    private static final int SLOT_RETOUR = 45;

    private static final class Liste implements InventoryHolder {
        private Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    private static final class Paliers implements InventoryHolder {
        private Inventory inv;
        private Outil.Mode mode;
        @Override public Inventory getInventory() { return inv; }
    }

    private record Recompense(long credit, long flux, List<String> commandes, String texte) { }

    private final OrbitTycoon plugin;

    Quetes(OrbitTycoon plugin) {
        this.plugin = plugin;
    }

    private FileConfiguration cfg() { return plugin.getConfig(); }

    /** Paliers des quêtes : pas, 2 × pas… jusqu'au prestige max (15 au plus). */
    List<Integer> paliers() {
        int pas = Math.max(1, cfg().getInt("quetes.pas", 10));
        int max = plugin.progression().prestigeMax();
        List<Integer> l = new ArrayList<>();
        for (int v = pas; v <= max && l.size() < SLOTS_PALIERS.length; v += pas) l.add(v);
        return l;
    }

    /** Nombre de quêtes prêtes à récupérer (tous outils) : sert au message de l'Aventurier. */
    int pretes(UUID id) {
        int n = 0;
        for (Outil.Mode m : Outil.Mode.values()) n += pretes(id, m);
        return n;
    }

    private int pretes(UUID id, Outil.Mode m) {
        int prestige = plugin.monnaies().prestige(id, m), n = 0;
        Set<Integer> faites = plugin.monnaies().quetes(id, m);
        for (int pal : paliers()) if (prestige >= pal && !faites.contains(pal)) n++;
        return n;
    }

    // ------------------------------------------------------------------ menus

    void ouvrir(Player p) {
        UUID id = p.getUniqueId();
        Liste h = new Liste();
        Inventory inv = Bukkit.createInventory(h, 27, Habillage.titre(plugin, p, 3, "Quêtes de prestige"));
        h.inv = inv;
        int total = paliers().size();
        inv.setItem(4, objet(Material.WRITABLE_BOOK, "<#62CFF4><bold>Quêtes de prestige", false,
                "<gray>Monte le prestige de tes outils pour",
                "<gray>débloquer une quête tous les " + Math.max(1, cfg().getInt("quetes.pas", 10)) + " prestiges.",
                "", "<gray>Quêtes prêtes : <#E0B864>" + pretes(id)));
        Outil.Mode[] modes = Outil.Mode.values();
        for (int i = 0; i < modes.length; i++) {
            Outil.Mode m = modes[i];
            int faites = plugin.monnaies().quetes(id, m).size(), pretes = pretes(id, m);
            inv.setItem(SLOTS_OUTILS[i], objet(m.type, "<" + m.couleur + "><bold>" + m.nom, pretes > 0,
                    "<gray>Prestige : <#E0B864>✪ " + plugin.monnaies().prestige(id, m),
                    "<gray>Quêtes récupérées : <white>" + faites + " <dark_gray>/ " + total,
                    pretes > 0 ? "<#7CFC9A>" + pretes + " quête" + (pretes > 1 ? "s" : "") + " à récupérer !" : "<dark_gray>Aucune quête prête.",
                    "", "<#62CFF4>➜ Clic pour voir les quêtes"));
        }
        Habillage.fond(plugin, p, inv);
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.0f);
    }

    void ouvrir(Player p, Outil.Mode m) {
        UUID id = p.getUniqueId();
        Paliers h = new Paliers();
        h.mode = m;
        Inventory inv = Bukkit.createInventory(h, 54, Habillage.titre(plugin, p, 6, "Quêtes · " + m.nom));
        h.inv = inv;
        int prestige = plugin.monnaies().prestige(id, m);
        Set<Integer> faites = plugin.monnaies().quetes(id, m);
        List<Integer> paliers = paliers();
        inv.setItem(4, objet(m.type, "<" + m.couleur + "><bold>" + m.nom, false,
                "<gray>Ton prestige : <#E0B864>✪ " + prestige,
                "<gray>Quêtes récupérées : <white>" + faites.size() + " <dark_gray>/ " + paliers.size()));
        for (int i = 0; i < paliers.size(); i++) inv.setItem(SLOTS_PALIERS[i], quete(m, paliers.get(i), prestige, faites));
        inv.setItem(SLOT_RETOUR, objet(Material.ARROW, "<#62CFF4>← Retour", false, "<gray>Revenir à la liste des outils."));
        Habillage.fond(plugin, p, inv);
        p.openInventory(inv);
    }

    private ItemStack quete(Outil.Mode m, int palier, int prestige, Set<Integer> faites) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Objectif : passer ton " + m.nom);
        lore.add("<gray>au <#E0B864>prestige ✪ " + palier + "<gray>.");
        lore.add("");
        lore.add("<#E0B864>Récompense :");
        lore.addAll(description(recompense(m, palier)));
        String ressource = plugin.ressources().debloquePar(m, palier);
        if (ressource != null) lore.add("<dark_gray> • <#7CFC9A>Nouvelle ressource : <white>" + ressource);
        lore.add("");
        if (faites.contains(palier)) {
            lore.add("<#7CFC9A>✔ Déjà récupérée");
            return objet(Material.LIME_DYE, "<#7CFC9A><bold>Quête · Prestige " + palier, false, lore.toArray(String[]::new));
        }
        if (prestige >= palier) {
            lore.add("<#E0B864>➜ Clic pour récupérer !");
            return objet(Material.CHEST, "<#E0B864><bold>Quête · Prestige " + palier, true, lore.toArray(String[]::new));
        }
        lore.add("<#FF4D4D>🔒 Prestige " + palier + " requis <dark_gray>(actuel : " + prestige + ")");
        return objet(Material.GRAY_DYE, "<gray><bold>Quête · Prestige " + palier, false, lore.toArray(String[]::new));
    }

    // ------------------------------------------------------------------ récompenses

    /**
     * Récompense d'un palier : quetes.recompenses.<outil>.<palier>, sinon quetes.recompenses.tous.<palier>,
     * sinon palier × quetes.credit-par-palier Crédits orbitaux (provisoire).
     */
    private Recompense recompense(Outil.Mode m, int palier) {
        ConfigurationSection s = cfg().getConfigurationSection("quetes.recompenses." + m.id + "." + palier);
        if (s == null) s = cfg().getConfigurationSection("quetes.recompenses.tous." + palier);
        if (s != null) return new Recompense(s.getLong("credit", 0), s.getLong("flux", 0), s.getStringList("commandes"), s.getString("texte"));
        return new Recompense(palier * cfg().getLong("quetes.credit-par-palier", 500), 0, List.of(), null);
    }

    private static List<String> description(Recompense r) {
        List<String> l = new ArrayList<>();
        if (r.credit() > 0) l.add("<dark_gray> • <#E0B864>" + Hud.nombre(r.credit()) + " ⛃ Crédits orbitaux");
        if (r.flux() > 0) l.add("<dark_gray> • <#62CFF4>" + Hud.nombre(r.flux()) + " ✦ Flux stellaire");
        if (r.texte() != null && !r.texte().isBlank()) l.add("<dark_gray> • <white>" + r.texte());
        if (l.isEmpty()) l.add("<dark_gray> • À définir");
        return l;
    }

    private void recuperer(Player p, Outil.Mode m, int palier) {
        UUID id = p.getUniqueId();
        Monnaies mon = plugin.monnaies();
        if (mon.quetes(id, m).contains(palier)) return;
        if (mon.prestige(id, m) < palier) {
            OrbitTycoon.msg(p, "Il te faut le <#E0B864>prestige ✪ " + palier + "</#E0B864> sur ton " + m.nom + ".");
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.8f);
            return;
        }
        mon.marquerQuete(id, m, palier);
        Recompense r = recompense(m, palier);
        if (r.credit() > 0) mon.ajouter(id, Monnaies.Monnaie.CREDIT, r.credit());
        if (r.flux() > 0) mon.ajouter(id, Monnaies.Monnaie.FLUX, r.flux());
        for (String c : r.commandes())
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c.replace("%joueur%", p.getName()).replace("%uuid%", id.toString()));
        OrbitTycoon.msg(p, "Quête <#E0B864>" + m.nom + " · Prestige " + palier + "</#E0B864> récupérée !");
        for (String l : description(r)) p.sendMessage(Navigateur.texte("  " + l));
        String ressource = plugin.ressources().debloquePar(m, palier);
        if (ressource != null) p.sendMessage(Navigateur.texte("  <dark_gray> • <#7CFC9A>Nouvelle ressource débloquée : <white>" + ressource
                + " <gray>(<#E0B864>/ressources</#E0B864>)"));
        plugin.ressources().actualiser(p, m); // mine / bûcheron / agriculture : la nouvelle ressource apparaît tout de suite
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.2f);
        ouvrir(p, m);
    }

    // ------------------------------------------------------------------ clics

    @EventHandler(priority = EventPriority.HIGHEST)
    public void clic(InventoryClickEvent e) {
        var holder = e.getView().getTopInventory().getHolder();
        if (!(holder instanceof Liste) && !(holder instanceof Paliers)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getView().getTopInventory()) return;
        int slot = e.getSlot();
        if (holder instanceof Liste) {
            for (int i = 0; i < SLOTS_OUTILS.length; i++) if (slot == SLOTS_OUTILS[i]) ouvrir(p, Outil.Mode.values()[i]);
            return;
        }
        Paliers h = (Paliers) holder;
        if (slot == SLOT_RETOUR) { ouvrir(p); return; }
        List<Integer> paliers = paliers();
        for (int i = 0; i < paliers.size(); i++) if (slot == SLOTS_PALIERS[i]) recuperer(p, h.mode, paliers.get(i));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void glisser(InventoryDragEvent e) {
        var holder = e.getView().getTopInventory().getHolder();
        if (holder instanceof Liste || holder instanceof Paliers) e.setCancelled(true);
    }
}
