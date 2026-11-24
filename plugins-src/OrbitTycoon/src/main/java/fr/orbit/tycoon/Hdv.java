package fr.orbit.tycoon;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static fr.orbit.tycoon.Navigateur.texte;
import static fr.orbit.tycoon.Progression.objet;

/**
 * Hôtel des ventes (HDV) : tout le monde met en vente un objet (/hdv vendre <prix>) contre des Crédits orbitaux,
 * et tout le monde achète depuis le menu (/hdv ou le Marchand orbital).
 * - Offres sauvées dans plugins/OrbitTycoon/hdv.yml après chaque changement (écriture hors du thread principal).
 * - Taxe sur la vente, nombre d'offres limité, offres qui expirent : l'objet revient dans « Mes ventes ».
 * - Les objets liés (Outil orbital, Navigateur) ne peuvent pas être vendus.
 * Tout se passe sur le thread principal (un seul serveur) : pas de double achat possible.
 */
final class Hdv implements Listener {

    private static final int PAR_PAGE = 45;
    private static final int SLOT_PRECEDENT = 45, SLOT_MES_VENTES = 47, SLOT_INFO = 49, SLOT_ACTUALISER = 51, SLOT_SUIVANT = 53;

    private static final class Offre {
        final UUID id, vendeur;
        final String nomVendeur;
        final ItemStack objet;
        final long prix, depot;

        Offre(UUID id, UUID vendeur, String nomVendeur, ItemStack objet, long prix, long depot) {
            this.id = id; this.vendeur = vendeur; this.nomVendeur = nomVendeur; this.objet = objet; this.prix = prix; this.depot = depot;
        }
    }

    private static final class Marche implements InventoryHolder {
        private Inventory inv;
        private int page;
        private final List<UUID> offres = new ArrayList<>();
        @Override public Inventory getInventory() { return inv; }
    }

    private static final class MesVentes implements InventoryHolder {
        private Inventory inv;
        private final List<UUID> offres = new ArrayList<>();
        @Override public Inventory getInventory() { return inv; }
    }

    private final OrbitTycoon plugin;
    private final Map<UUID, Offre> offres = new LinkedHashMap<>();
    private final ExecutorService ecriture = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "OrbitTycoon-HDV");
        t.setDaemon(true);
        return t;
    });

    Hdv(OrbitTycoon plugin) {
        this.plugin = plugin;
    }

    private FileConfiguration cfg() { return plugin.getConfig(); }

    private File fichier() { return new File(plugin.getDataFolder(), "hdv.yml"); }

    private long dureeMs() { return Math.max(1, cfg().getLong("hdv.duree-heures", 48)) * 3_600_000L; }

    private boolean expiree(Offre o) { return System.currentTimeMillis() - o.depot >= dureeMs(); }

    private long taxe(long prix) { return prix * Math.max(0, Math.min(100, cfg().getInt("hdv.taxe", 5))) / 100; }

    // ------------------------------------------------------------------ fichier

    void demarrer() {
        File f = fichier();
        if (!f.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        ConfigurationSection s = y.getConfigurationSection("offres");
        if (s == null) return;
        int erreurs = 0;
        for (String cle : s.getKeys(false)) {
            try {
                ConfigurationSection o = s.getConfigurationSection(cle);
                ItemStack it = ItemStack.deserializeBytes(Base64.getDecoder().decode(o.getString("objet")));
                offres.put(UUID.fromString(cle), new Offre(UUID.fromString(cle), UUID.fromString(o.getString("vendeur")),
                        o.getString("nom", "?"), it, o.getLong("prix"), o.getLong("depot")));
            } catch (Exception e) {
                erreurs++;
                plugin.getLogger().severe("HDV : offre " + cle + " illisible (gardée dans hdv.yml) : " + e);
            }
        }
        // Une offre illisible ne doit pas disparaître à la prochaine sauvegarde : on garde une copie du fichier.
        if (erreurs > 0) {
            try {
                Files.copy(f.toPath(), new File(plugin.getDataFolder(), "hdv-illisible-" + System.currentTimeMillis() + ".yml").toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                plugin.getLogger().severe("HDV : copie de secours impossible : " + e);
            }
        }
        plugin.getLogger().info("HDV : " + offres.size() + " offre(s) chargée(s).");
    }

    void arreter() {
        ecriture.shutdown();
        try { ecriture.awaitTermination(5, TimeUnit.SECONDS); } catch (InterruptedException ignore) { Thread.currentThread().interrupt(); }
        ecrire(instantane());
    }

    /** Copie texte de toutes les offres, faite sur le thread principal. */
    private String instantane() {
        YamlConfiguration y = new YamlConfiguration();
        for (Offre o : offres.values()) {
            String b = "offres." + o.id + ".";
            y.set(b + "vendeur", o.vendeur.toString());
            y.set(b + "nom", o.nomVendeur);
            y.set(b + "prix", o.prix);
            y.set(b + "depot", o.depot);
            y.set(b + "objet", Base64.getEncoder().encodeToString(o.objet.serializeAsBytes()));
        }
        return y.saveToString();
    }

    private void sauver() {
        String texte = instantane();
        if (ecriture.isShutdown()) { ecrire(texte); return; }
        ecriture.execute(() -> ecrire(texte));
    }

    /** Écrit dans un fichier temporaire puis le renomme : jamais de hdv.yml à moitié écrit. */
    private void ecrire(String texte) {
        File f = fichier(), tmp = new File(plugin.getDataFolder(), "hdv.yml.tmp");
        try {
            f.getParentFile().mkdirs();
            Files.writeString(tmp.toPath(), texte, StandardCharsets.UTF_8);
            Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            plugin.getLogger().severe("HDV : sauvegarde impossible : " + e);
        }
    }

    // ------------------------------------------------------------------ commande

    boolean commande(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) { OrbitTycoon.msg(s, "Commande réservée aux joueurs."); return true; }
        if (args.length == 0) { ouvrir(p, 0); return true; }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "vendre", "sell" -> {
                if (args.length < 2) { OrbitTycoon.msg(p, "Usage : <#E0B864>/hdv vendre <prix></#E0B864> <gray>(objet en main, ex. 2500, 10k, 1.5m)"); return true; }
                vendre(p, args[1]);
            }
            case "ventes", "mesventes" -> mesVentes(p);
            default -> aide(p);
        }
        return true;
    }

    private void aide(Player p) {
        OrbitTycoon.msg(p, "<#E0B864>Hôtel des ventes</#E0B864> :");
        p.sendMessage(texte("  <#E0B864>/hdv <gray>: ouvrir l'hôtel des ventes"));
        p.sendMessage(texte("  <#E0B864>/hdv vendre <prix> <gray>: vendre l'objet en main (ex. 2500, 10k, 1.5m)"));
        p.sendMessage(texte("  <#E0B864>/hdv ventes <gray>: tes offres et objets à récupérer"));
        p.sendMessage(texte("  <dark_gray>Taxe " + cfg().getInt("hdv.taxe", 5) + " % · " + plugin.grades().maxOffres(p)
                + " offres max · expiration " + cfg().getLong("hdv.duree-heures", 48) + " h"));
    }

    /** « 2500 », « 10k », « 1.5m », « 2md » → montant ; -1 si illisible. */
    static long lirePrix(String s) {
        String t = s.toLowerCase(Locale.ROOT).replace(',', '.').replace(" ", "");
        double mult = 1;
        if (t.endsWith("md") || t.endsWith("b")) { mult = 1e9; t = t.substring(0, t.length() - (t.endsWith("md") ? 2 : 1)); }
        else if (t.endsWith("m")) { mult = 1e6; t = t.substring(0, t.length() - 1); }
        else if (t.endsWith("k")) { mult = 1e3; t = t.substring(0, t.length() - 1); }
        try {
            double v = Double.parseDouble(t) * mult;
            return Double.isFinite(v) && v >= 1 && v < 9e15 ? Math.round(v) : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void vendre(Player p, String texte) {
        ItemStack main = p.getInventory().getItemInMainHand();
        if (main.getType().isAir()) { OrbitTycoon.msg(p, "Prends en main l'objet à vendre."); return; }
        if (plugin.liens().lie(main)) { OrbitTycoon.msg(p, "<#FF4D4D>Cet objet est lié à toi : impossible de le vendre."); return; }
        long prix = lirePrix(texte);
        long min = Math.max(1, cfg().getLong("hdv.prix-min", 1)), max = cfg().getLong("hdv.prix-max", 1_000_000_000_000L);
        if (prix < 0) { OrbitTycoon.msg(p, "Prix illisible. Exemples : <#E0B864>2500</#E0B864>, <#E0B864>10k</#E0B864>, <#E0B864>1.5m</#E0B864>."); return; }
        if (prix < min || prix > max) { OrbitTycoon.msg(p, "Le prix doit être entre <#E0B864>" + Hud.nombre(min) + "</#E0B864> et <#E0B864>" + Hud.nombre(max) + "</#E0B864> Crédits."); return; }
        int maxOffres = Math.max(1, plugin.grades().maxOffres(p));
        long miennes = offres.values().stream().filter(o -> o.vendeur.equals(p.getUniqueId())).count();
        if (miennes >= maxOffres) {
            OrbitTycoon.msg(p, "Tu as déjà <#E0B864>" + maxOffres + " offres</#E0B864> (expirées comprises). Retire-en dans <#E0B864>/hdv ventes</#E0B864>.");
            return;
        }
        ItemStack vendu = main.clone();
        p.getInventory().setItemInMainHand(null);
        Offre o = new Offre(UUID.randomUUID(), p.getUniqueId(), p.getName(), vendu, prix, System.currentTimeMillis());
        offres.put(o.id, o);
        sauver();
        OrbitTycoon.msg(p, "Mis en vente : <white>" + vendu.getAmount() + " × </white>" + nomObjet(vendu) + " <gray>pour <#E0B864>"
                + Hud.nombre(prix) + " ⛃</#E0B864> <dark_gray>(tu toucheras " + Hud.nombre(prix - taxe(prix)) + " ⛃ après taxe)");
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_TRADE, 0.8f, 1.2f);
    }

    private static String nomObjet(ItemStack it) {
        Component nom = it.effectiveName();
        return net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().serialize(nom);
    }

    // ------------------------------------------------------------------ menus

    void ouvrir(Player p, int page) {
        List<Offre> liste = offres.values().stream().filter(o -> !expiree(o))
                .sorted(Comparator.comparingLong((Offre o) -> o.depot).reversed()).toList();
        int pages = Math.max(1, (liste.size() + PAR_PAGE - 1) / PAR_PAGE);
        page = Math.max(0, Math.min(page, pages - 1));
        Marche h = new Marche();
        h.page = page;
        Inventory inv = Bukkit.createInventory(h, 54, Habillage.titre(plugin, p, 6, "Hôtel des ventes"));
        h.inv = inv;
        for (int i = 0; i < PAR_PAGE && page * PAR_PAGE + i < liste.size(); i++) {
            Offre o = liste.get(page * PAR_PAGE + i);
            h.offres.add(o.id);
            boolean moi = o.vendeur.equals(p.getUniqueId());
            inv.setItem(i, affichage(o, moi ? "<#62CFF4>● Ton offre <dark_gray>(retire-la dans Mes ventes)" : "<#E0B864>➜ Clic pour acheter"));
        }
        if (page > 0) inv.setItem(SLOT_PRECEDENT, objet(Material.ARROW, "<#62CFF4>← Page précédente", false, "<gray>Page " + page + " / " + pages));
        if (page < pages - 1) inv.setItem(SLOT_SUIVANT, objet(Material.ARROW, "<#62CFF4>Page suivante →", false, "<gray>Page " + (page + 2) + " / " + pages));
        long miennes = offres.values().stream().filter(o -> o.vendeur.equals(p.getUniqueId())).count();
        long aRecuperer = offres.values().stream().filter(o -> o.vendeur.equals(p.getUniqueId()) && expiree(o)).count();
        inv.setItem(SLOT_MES_VENTES, objet(Material.CHEST, "<#E0B864><bold>Mes ventes", aRecuperer > 0,
                "<gray>Offres : <white>" + miennes + " <dark_gray>/ " + plugin.grades().maxOffres(p),
                aRecuperer > 0 ? "<#FF4D4D>" + aRecuperer + " objet(s) expiré(s) à récupérer" : "<dark_gray>Rien à récupérer.",
                "", "<#E0B864>➜ Clic pour gérer tes offres"));
        inv.setItem(SLOT_INFO, objet(Material.EMERALD, "<#E0B864><bold>Hôtel des ventes", false,
                "<gray>Ton solde : <#E0B864>" + Hud.nombre(plugin.monnaies().solde(p.getUniqueId(), Monnaies.Monnaie.CREDIT)) + " ⛃",
                "<gray>Offres en ligne : <white>" + liste.size(),
                "",
                "<gray>Vendre : objet en main, puis",
                "<#E0B864>/hdv vendre <prix>",
                "<dark_gray>Taxe " + cfg().getInt("hdv.taxe", 5) + " % · expiration " + cfg().getLong("hdv.duree-heures", 48) + " h"));
        inv.setItem(SLOT_ACTUALISER, objet(Material.CLOCK, "<#62CFF4>Actualiser", false, "<gray>Recharger la page."));
        Habillage.fond(plugin, p, inv);
        p.openInventory(inv);
    }

    void mesVentes(Player p) {
        List<Offre> liste = offres.values().stream().filter(o -> o.vendeur.equals(p.getUniqueId()))
                .sorted(Comparator.comparingLong(o -> o.depot)).toList();
        MesVentes h = new MesVentes();
        Inventory inv = Bukkit.createInventory(h, 54, Habillage.titre(plugin, p, 6, "Mes ventes"));
        h.inv = inv;
        for (int i = 0; i < PAR_PAGE && i < liste.size(); i++) {
            Offre o = liste.get(i);
            h.offres.add(o.id);
            inv.setItem(i, affichage(o, expiree(o) ? "<#FF4D4D>Expirée <dark_gray>· <#E0B864>➜ Clic pour récupérer" : "<#E0B864>➜ Clic pour retirer l'offre"));
        }
        inv.setItem(SLOT_PRECEDENT, objet(Material.ARROW, "<#62CFF4>← Retour", false, "<gray>Revenir à l'hôtel des ventes."));
        inv.setItem(SLOT_INFO, objet(Material.EMERALD, "<#E0B864><bold>Mes ventes", false,
                "<gray>Clic sur une offre pour la retirer :", "<gray>l'objet revient dans ton inventaire."));
        Habillage.fond(plugin, p, inv);
        p.openInventory(inv);
    }

    private ItemStack affichage(Offre o, String action) {
        ItemStack it = o.objet.clone();
        List<Component> lore = new ArrayList<>(it.lore() == null ? List.of() : it.lore());
        lore.add(texte("<dark_gray><st>                         "));
        lore.add(texte("<gray>Vendeur : <white>" + net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().escapeTags(o.nomVendeur)));
        lore.add(texte("<gray>Prix : <#E0B864>" + Hud.nombre(o.prix) + " ⛃ Crédits orbitaux"));
        lore.add(texte(expiree(o) ? "<#FF4D4D>Expirée" : "<gray>Expire dans : <white>" + duree(o.depot + dureeMs() - System.currentTimeMillis())));
        lore.add(Component.empty());
        lore.add(texte(action));
        it.lore(lore);
        return it;
    }

    private static String duree(long ms) {
        long min = Math.max(0, ms / 60_000);
        long h = min / 60;
        return h > 0 ? h + " h " + (min % 60) + " min" : min + " min";
    }

    // ------------------------------------------------------------------ achat / retrait

    private void demanderAchat(Player p, UUID id, int page) {
        Offre o = offres.get(id);
        if (o == null || expiree(o)) { OrbitTycoon.msg(p, "Cette offre n'est plus disponible."); ouvrir(p, page); return; }
        if (o.vendeur.equals(p.getUniqueId())) { OrbitTycoon.msg(p, "C'est ton offre : retire-la dans <#E0B864>Mes ventes</#E0B864>."); return; }
        plugin.confirmer().ouvrir(p, "Confirmer l'achat ?", affichage(o, "<gray>Achat"),
                List.of("<gray>Payer <#E0B864>" + Hud.nombre(o.prix) + " ⛃ Crédits orbitaux", "<gray>à <white>" + o.nomVendeur),
                () -> acheter(p, id, page), () -> ouvrir(p, page));
    }

    private void acheter(Player p, UUID id, int page) {
        Offre o = offres.get(id);
        if (o == null || expiree(o)) { OrbitTycoon.msg(p, "Trop tard : cette offre n'est plus disponible."); return; }
        if (o.vendeur.equals(p.getUniqueId())) return;
        if (p.getInventory().firstEmpty() < 0) { OrbitTycoon.msg(p, "Ton inventaire est plein."); return; }
        Monnaies mon = plugin.monnaies();
        if (!mon.retirer(p.getUniqueId(), Monnaies.Monnaie.CREDIT, o.prix)) {
            OrbitTycoon.msg(p, "Il te manque des Crédits orbitaux : il en faut <#E0B864>" + Hud.nombre(o.prix) + " ⛃</#E0B864>.");
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.8f);
            return;
        }
        offres.remove(id);
        p.getInventory().addItem(o.objet.clone());
        long net = o.prix - taxe(o.prix);
        mon.ajouter(o.vendeur, Monnaies.Monnaie.CREDIT, net);
        sauver();
        OrbitTycoon.msg(p, "Achat réussi : <white>" + o.objet.getAmount() + " × </white>" + nomObjet(o.objet)
                + " <gray>pour <#E0B864>" + Hud.nombre(o.prix) + " ⛃</#E0B864>.");
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f);
        Player vendeur = Bukkit.getPlayer(o.vendeur);
        if (vendeur != null) {
            OrbitTycoon.msg(vendeur, "<white>" + p.getName() + "</white> a acheté ton offre " + nomObjet(o.objet)
                    + " : <#E0B864>+" + Hud.nombre(net) + " ⛃</#E0B864> <dark_gray>(taxe " + Hud.nombre(o.prix - net) + ")");
            vendeur.playSound(vendeur.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
        }
        ouvrir(p, page);
    }

    private void retirer(Player p, UUID id) {
        Offre o = offres.get(id);
        if (o == null || !o.vendeur.equals(p.getUniqueId())) { mesVentes(p); return; }
        if (p.getInventory().firstEmpty() < 0) { OrbitTycoon.msg(p, "Libère une place dans ton inventaire d'abord."); return; }
        offres.remove(id);
        p.getInventory().addItem(o.objet.clone());
        sauver();
        OrbitTycoon.msg(p, "Offre retirée : " + nomObjet(o.objet) + " <gray>est revenu dans ton inventaire.");
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.0f);
        mesVentes(p);
    }

    // ------------------------------------------------------------------ événements

    @EventHandler(priority = EventPriority.HIGHEST)
    public void clic(InventoryClickEvent e) {
        var holder = e.getView().getTopInventory().getHolder();
        if (!(holder instanceof Marche) && !(holder instanceof MesVentes)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getView().getTopInventory()) return;
        int slot = e.getSlot();
        if (holder instanceof Marche h) {
            if (slot < h.offres.size()) demanderAchat(p, h.offres.get(slot), h.page);
            else if (slot == SLOT_PRECEDENT && h.page > 0) ouvrir(p, h.page - 1);
            else if (slot == SLOT_SUIVANT && e.getCurrentItem() != null) ouvrir(p, h.page + 1);
            else if (slot == SLOT_MES_VENTES) mesVentes(p);
            else if (slot == SLOT_ACTUALISER) ouvrir(p, h.page);
        } else if (holder instanceof MesVentes h) {
            if (slot < h.offres.size()) retirer(p, h.offres.get(slot));
            else if (slot == SLOT_PRECEDENT) ouvrir(p, 0);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void glisser(InventoryDragEvent e) {
        var holder = e.getView().getTopInventory().getHolder();
        if (holder instanceof Marche || holder instanceof MesVentes) e.setCancelled(true);
    }

    @EventHandler
    public void connexion(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            long n = offres.values().stream().filter(o -> o.vendeur.equals(p.getUniqueId()) && expiree(o)).count();
            if (n > 0) OrbitTycoon.msg(p, "<#E0B864>" + n + " objet(s)</#E0B864> de l'hôtel des ventes ont expiré : récupère-les avec <#E0B864>/hdv ventes</#E0B864>.");
        }, 60L);
    }
}
