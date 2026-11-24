package fr.orbit.tycoon;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Commandes des grades : chaque grade débloque les commandes des grades en dessous, plus les siennes.
 *   Pilote     : /ec, /craft
 *   Commandant : + /fly (île), /hat, /enclume
 *   Amiral     : + /near, /meule, /tailleur, /ptime, /pv (coffre spatial 27 cases)
 *   Astral     : + /forge, /pv 54 cases, /near plus loin
 * Les admins (orbittycoon.admin) ont tout.
 */
final class CommandesGrade implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    /** Commande → grade minimum (l'ordre sert aussi à l'affichage dans la boutique). */
    enum Cmd {
        EC("ec", "Coffre de l'End partout", Grades.Grade.PILOTE),
        CRAFT("craft", "Établi portable", Grades.Grade.PILOTE),
        FLY("fly", "Vol sur ton île", Grades.Grade.COMMANDANT),
        HAT("hat", "Porter le bloc en main sur la tête", Grades.Grade.COMMANDANT),
        ENCLUME("enclume", "Enclume portable", Grades.Grade.COMMANDANT),
        NEAR("near", "Joueurs proches", Grades.Grade.AMIRAL),
        MEULE("meule", "Meule portable", Grades.Grade.AMIRAL),
        TAILLEUR("tailleur", "Tailleur de pierre portable", Grades.Grade.AMIRAL),
        PTIME("ptime", "Ton heure : jour / nuit", Grades.Grade.AMIRAL),
        PV("pv", "Coffre spatial personnel", Grades.Grade.AMIRAL),
        FORGE("forge", "Table de forge portable", Grades.Grade.ASTRAL);

        final String nom, description;
        final Grades.Grade min;
        Cmd(String nom, String description, Grades.Grade min) { this.nom = nom; this.description = description; this.min = min; }

        static Cmd de(String nom) {
            for (Cmd c : values()) if (c.nom.equalsIgnoreCase(nom)) return c;
            return null;
        }
    }

    private final OrbitTycoon plugin;

    CommandesGrade(OrbitTycoon plugin) { this.plugin = plugin; }

    /** Commandes ajoutées PAR ce grade (pas celles des grades en dessous), pour la boutique. */
    static List<Cmd> nouvelles(Grades.Grade g) {
        List<Cmd> l = new ArrayList<>();
        for (Cmd c : Cmd.values()) if (c.min == g) l.add(c);
        return l;
    }

    /** Taille du coffre spatial : 27 (Amiral), 54 (Astral). */
    private int taillePv(Player p) {
        Grades.Grade g = plugin.grades().grade(p);
        return p.hasPermission("orbittycoon.admin") || g == Grades.Grade.ASTRAL ? 54 : 27;
    }

    private boolean autorise(Player p, Cmd c) {
        if (p.hasPermission("orbittycoon.admin")) return true;
        Grades.Grade g = plugin.grades().grade(p);
        return g != null && g.ordinal() >= c.min.ordinal();
    }

    /** true si la commande est une commande de grade (traitée ici). */
    boolean commande(Player p, String nom, String[] args) {
        Cmd c = Cmd.de(nom);
        if (c == null) return false;
        if (!autorise(p, c)) {
            OrbitTycoon.msg(p, "<gray>/" + c.nom + " est réservée au grade " + c.min.joli + "<gray> et plus. <dark_gray>(/boutique → Grades)");
            p.playSound(p.getLocation(), Sound.BLOCK_CHEST_LOCKED, 0.6f, 1.3f);
            return true;
        }
        switch (c) {
            case EC -> { p.openInventory(p.getEnderChest()); p.playSound(p.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.6f, 1f); }
            case CRAFT -> p.openWorkbench(null, true);
            case FLY -> plugin.grades().basculerVol(p);
            case HAT -> chapeau(p);
            case ENCLUME -> p.openAnvil(null, true);
            case NEAR -> proches(p);
            case MEULE -> p.openGrindstone(null, true);
            case TAILLEUR -> p.openStonecutter(null, true);
            case PTIME -> heure(p, args);
            case PV -> ouvrirPv(p);
            case FORGE -> p.openSmithingTable(null, true);
        }
        return true;
    }

    private void chapeau(Player p) {
        ItemStack main = p.getInventory().getItemInMainHand();
        if (main.getType().isAir()) { OrbitTycoon.msg(p, "<gray>Prends un objet en main."); return; }
        if (plugin.liens() != null && plugin.liens().lie(main)) { OrbitTycoon.msg(p, "<#FF4D4D>Cet objet lié ne se porte pas sur la tête."); return; }
        ItemStack tete = p.getInventory().getHelmet();
        ItemStack un = main.clone();
        un.setAmount(1);
        p.getInventory().setHelmet(un);
        main.setAmount(main.getAmount() - 1);
        p.getInventory().setItemInMainHand(main.getAmount() <= 0 ? null : main);
        if (tete != null && !tete.getType().isAir())
            for (ItemStack reste : p.getInventory().addItem(tete).values()) p.getWorld().dropItemNaturally(p.getLocation(), reste);
        OrbitTycoon.msg(p, "<gray>Joli chapeau !");
    }

    private void proches(Player p) {
        Grades.Grade g = plugin.grades().grade(p);
        double rayon = g == Grades.Grade.ASTRAL || p.hasPermission("orbittycoon.admin") ? 250 : 120;
        List<String> l = new ArrayList<>();
        for (Player o : p.getWorld().getPlayers()) {
            if (o == p || !p.canSee(o) || (plugin.staff() != null && plugin.staff().invisible(o))) continue;
            double d = o.getLocation().distance(p.getLocation());
            if (d <= rayon) l.add("<white>" + o.getName() + " <dark_gray>(" + Math.round(d) + " m)");
        }
        OrbitTycoon.msg(p, "<gray>Joueurs à moins de " + (int) rayon + " m : " + (l.isEmpty() ? "<dark_gray>personne" : String.join("<gray>, ", l)));
    }

    private void heure(Player p, String[] args) {
        String a = args.length > 0 ? args[0].toLowerCase() : "";
        switch (a) {
            case "jour" -> p.setPlayerTime(6000, false);
            case "nuit" -> p.setPlayerTime(18000, false);
            case "reset", "normal" -> p.resetPlayerTime();
            default -> { OrbitTycoon.msg(p, "<gray>Usage : <#E0B864>/ptime jour|nuit|reset"); return; }
        }
        OrbitTycoon.msg(p, "<gray>Ton heure est réglée : <white>" + a);
    }

    // ------------------------------------------------------------------ coffre spatial (/pv)

    private static final class Pv implements InventoryHolder {
        final UUID id;
        Inventory inv;
        Pv(UUID id) { this.id = id; }
        @Override public Inventory getInventory() { return inv; }
    }

    private File fichierPv(UUID id) { return new File(new File(plugin.getDataFolder(), "coffres-spatiaux"), id + ".yml"); }

    private void ouvrirPv(Player p) {
        Pv h = new Pv(p.getUniqueId());
        int taille = taillePv(p);
        h.inv = Bukkit.createInventory(h, taille, Habillage.titre(plugin, p, taille / 9, "Coffre spatial"));
        YamlConfiguration y = YamlConfiguration.loadConfiguration(fichierPv(p.getUniqueId()));
        List<?> l = y.getList("contenu", List.of());
        List<ItemStack> surplus = new ArrayList<>();
        for (int i = 0; i < l.size(); i++) {
            if (!(l.get(i) instanceof ItemStack it)) continue;
            if (i < taille) h.inv.setItem(i, it); else surplus.add(it);
        }
        // Grade perdu (54 → 27 cases) : rien n'est perdu, le surplus revient dans l'inventaire.
        for (ItemStack it : surplus)
            for (ItemStack reste : p.getInventory().addItem(it).values()) p.getWorld().dropItemNaturally(p.getLocation(), reste);
        p.openInventory(h.inv);
        p.playSound(p.getLocation(), Sound.BLOCK_BARREL_OPEN, 0.6f, 1.2f);
    }

    @EventHandler
    public void fermer(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof Pv h)) return;
        YamlConfiguration y = new YamlConfiguration();
        List<ItemStack> l = new ArrayList<>();
        for (ItemStack it : h.inv.getContents()) l.add(it);
        y.set("contenu", l);
        String texte = y.saveToString();
        File f = fichierPv(h.id);
        if (plugin.isEnabled()) Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> ecrire(f, texte));
        else ecrire(f, texte);   // arrêt du serveur : écriture immédiate
    }

    private synchronized void ecrire(File f, String texte) {
        try {
            f.getParentFile().mkdirs();
            File tmp = new File(f.getPath() + ".tmp");
            Files.writeString(tmp.toPath(), texte, StandardCharsets.UTF_8);
            Files.move(tmp.toPath(), f.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            plugin.getLogger().warning("Coffre spatial " + f.getName() + " non sauvé : " + ex.getMessage());
        }
    }
}
