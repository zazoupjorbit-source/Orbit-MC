package fr.orbit.survie;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Échoppes de joueurs : un coffre ou un tonneau de sa base devient une boutique (/echoppe creer <prix> [lot],
 * objet à vendre en main). Les visiteurs cliquent le coffre : un menu d'achat s'ouvre à la place du coffre,
 * les objets sortent du coffre et les crédits vont au vendeur, même absent. Le vendeur ouvre son coffre
 * normalement pour le remplir. Une étiquette flotte au-dessus ; /echoppes liste toutes les boutiques.
 */
public final class Echoppes implements Listener, TabExecutor {

    static final class Echoppe {
        long id;
        UUID proprio;
        String proprioNom;
        String monde;
        int x, y, z;
        ItemStack objet;
        int lot;
        double prix;

        String cle() {
            return monde + ":" + x + ":" + y + ":" + z;
        }
    }

    private final OrbitSurvie plugin;
    private final NamespacedKey tag;
    private final Map<String, Echoppe> parBloc = new LinkedHashMap<>();
    private final Map<String, UUID> affiches = new HashMap<>();

    public Echoppes(OrbitSurvie plugin) {
        this.plugin = plugin;
        this.tag = new NamespacedKey(plugin, "echoppe");
    }

    private static String cle(Block b) {
        return b.getWorld().getName() + ":" + b.getX() + ":" + b.getY() + ":" + b.getZ();
    }

    /** Ce bloc est-il une échoppe ? (la protection des bases laisse les visiteurs le cliquer) */
    public boolean est(Block b) {
        return parBloc.containsKey(cle(b));
    }

    public void lireBase() {
        if (!plugin.db().available()) {
            return;
        }
        plugin.db().then(plugin.db().run(c -> {
            List<Echoppe> out = new ArrayList<>();
            try (PreparedStatement st = c.prepareStatement("SELECT * FROM survie_echoppes ORDER BY id"); ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    Echoppe e = new Echoppe();
                    e.id = rs.getLong("id");
                    e.proprio = UUID.fromString(rs.getString("proprio"));
                    e.proprioNom = rs.getString("proprio_nom");
                    e.monde = rs.getString("monde");
                    e.x = rs.getInt("x");
                    e.y = rs.getInt("y");
                    e.z = rs.getInt("z");
                    e.objet = Hdv.decoder(rs.getString("objet"));
                    e.lot = rs.getInt("lot");
                    e.prix = rs.getDouble("prix");
                    out.add(e);
                }
            }
            return out;
        }), liste -> {
            parBloc.clear();
            liste.forEach(e -> parBloc.put(e.cle(), e));
            plugin.getLogger().info(liste.size() + " échoppe(s) de joueurs chargée(s).");
        }, null);
    }

    private int limite(Player p) {
        var s = plugin.getConfig().getConfigurationSection("echoppes.limites");
        int max = 3;
        if (s != null) {
            max = s.getInt("joueur", 3);
            for (String g : s.getKeys(false)) {
                if (p.hasPermission("group." + g)) {
                    max = Math.max(max, s.getInt(g));
                }
            }
        }
        return max;
    }

    private static boolean coffre(Block b) {
        Material t = b.getType();
        return (t == Material.CHEST || t == Material.TRAPPED_CHEST || t == Material.BARREL) && b.getState(false) instanceof Container;
    }

    private static int stock(Inventory inv, ItemStack modele) {
        int n = 0;
        for (ItemStack it : inv.getStorageContents()) {
            if (it != null && it.isSimilar(modele)) {
                n += it.getAmount();
            }
        }
        return n;
    }

    private static void retirer(Inventory inv, ItemStack modele, int combien) {
        ItemStack[] contenu = inv.getStorageContents();
        for (int i = 0; i < contenu.length && combien > 0; i++) {
            ItemStack it = contenu[i];
            if (it == null || !it.isSimilar(modele)) {
                continue;
            }
            int pris = Math.min(it.getAmount(), combien);
            combien -= pris;
            if (pris == it.getAmount()) {
                contenu[i] = null;
            } else {
                it.setAmount(it.getAmount() - pris);
            }
        }
        inv.setStorageContents(contenu);
    }

    // ------------------------------------------------------------------ Créer, supprimer

    private void creer(Player p, String prixTexte, String lotTexte) {
        if (!plugin.db().available()) {
            Msg.send(p, "<red>Les échoppes sont indisponibles : la base de données est injoignable.");
            return;
        }
        Block b = p.getTargetBlockExact(5);
        if (b == null || !coffre(b)) {
            Msg.send(p, "<red>Regarde un coffre ou un tonneau (à moins de 5 blocs).");
            return;
        }
        Bases.Base base = plugin.bases().a(b.getLocation());
        if (base == null || !plugin.bases().peut(p, base)) {
            Msg.send(p, "<red>Une échoppe se pose dans ta base protégée (armoire de base payée) : sinon on te la viderait.");
            return;
        }
        if (est(b)) {
            Msg.send(p, "<red>Ce coffre est déjà une échoppe. <white>/echoppe supprimer<red> pour la retirer.");
            return;
        }
        ItemStack main = p.getInventory().getItemInMainHand();
        if (main.getType().isAir() || plugin.bases().estArmoire(main)) {
            Msg.send(p, "<red>Tiens en main l'objet à vendre.");
            return;
        }
        double prix;
        int lot;
        try {
            prix = Math.floor(Double.parseDouble(prixTexte.replace(',', '.')) * 100) / 100.0;
            lot = lotTexte == null ? main.getAmount() : Integer.parseInt(lotTexte);
        } catch (NumberFormatException e) {
            Msg.send(p, "<red>Usage : <white>/echoppe creer <prix> [quantité par achat]");
            return;
        }
        if (!(prix >= 0.01) || prix > plugin.getConfig().getDouble("hdv.prix-max", 10_000_000) || lot < 1 || lot > main.getMaxStackSize()) {
            Msg.send(p, "<red>Prix ou quantité invalide (quantité de 1 à " + main.getMaxStackSize() + " pour cet objet).");
            return;
        }
        long miennes = parBloc.values().stream().filter(e -> e.proprio.equals(p.getUniqueId())).count();
        if (miennes >= limite(p)) {
            Msg.send(p, "<red>Tu as déjà " + miennes + " échoppes, le maximum de ton grade. <white>/grade<red> pour en avoir plus.");
            return;
        }
        Echoppe e = new Echoppe();
        e.proprio = p.getUniqueId();
        e.proprioNom = p.getName();
        e.monde = b.getWorld().getName();
        e.x = b.getX();
        e.y = b.getY();
        e.z = b.getZ();
        e.objet = main.asOne();
        e.lot = lot;
        e.prix = prix;
        String code = Hdv.encoder(e.objet);
        plugin.db().then(plugin.db().run(c -> {
            try (PreparedStatement st = c.prepareStatement("INSERT INTO survie_echoppes (proprio, proprio_nom, monde, x, y, z, objet, lot, prix) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                st.setString(1, e.proprio.toString());
                st.setString(2, e.proprioNom);
                st.setString(3, e.monde);
                st.setInt(4, e.x);
                st.setInt(5, e.y);
                st.setInt(6, e.z);
                st.setString(7, code);
                st.setInt(8, e.lot);
                st.setDouble(9, e.prix);
                st.executeUpdate();
                try (ResultSet rs = st.getGeneratedKeys()) {
                    return rs.next() ? rs.getLong(1) : -1L;
                }
            }
        }), id -> {
            e.id = id;
            parBloc.put(e.cle(), e);
            Msg.send(p, "Échoppe ouverte : <white>" + e.lot + " × " + Msg.nom(e.objet.getType()) + "<gray> pour " + Msg.argent(e.prix)
                    + ". Remplis le coffre ; les crédits arrivent sur ton compte à chaque vente.");
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1.2f);
            affiches();
        }, () -> Msg.send(p, "<red>Échoppe non créée (base de données)."));
    }

    private void supprimer(Echoppe e) {
        parBloc.remove(e.cle());
        UUID aff = affiches.remove(e.cle());
        Entity ent = aff == null ? null : Bukkit.getEntity(aff);
        if (ent != null) {
            ent.remove();
        }
        if (plugin.db().available()) {
            plugin.db().run(c -> {
                try (PreparedStatement st = c.prepareStatement("DELETE FROM survie_echoppes WHERE id = ?")) {
                    st.setLong(1, e.id);
                    return st.executeUpdate();
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCasse(BlockBreakEvent ev) {
        Echoppe e = parBloc.get(cle(ev.getBlock()));
        if (e != null) {
            supprimer(e);
            Msg.send(ev.getPlayer(), "Échoppe de <white>" + e.proprioNom + "<gray> fermée (coffre cassé).");
        }
    }

    // ------------------------------------------------------------------ Étiquettes

    /** Toutes les 5 s : pose l'étiquette des échoppes dont le tronçon est chargé ; ferme celles dont le coffre a disparu. */
    public void affiches() {
        for (Echoppe e : new ArrayList<>(parBloc.values())) {
            World w = Bukkit.getWorld(e.monde);
            if (w == null || !w.isChunkLoaded(e.x >> 4, e.z >> 4)) {
                continue;
            }
            if (!coffre(w.getBlockAt(e.x, e.y, e.z))) {
                supprimer(e);
                continue;
            }
            UUID id = affiches.get(e.cle());
            Entity ent = id == null ? null : w.getEntity(id);
            if (ent != null && ent.isValid()) {
                continue;
            }
            TextDisplay t = w.spawn(new Location(w, e.x + 0.5, e.y + 1.35, e.z + 0.5), TextDisplay.class, d -> {
                d.text(Msg.raw(Msg.OR + "<bold>ÉCHOPPE</bold><newline><white>" + e.lot + " × " + Msg.nom(e.objet.getType())
                        + "<newline>" + Msg.argent(e.prix) + "<newline><dark_gray>" + e.proprioNom));
                d.setBillboard(Display.Billboard.CENTER);
                d.setPersistent(false);
                d.setShadowed(true);
                d.setBackgroundColor(Color.fromARGB(110, 10, 20, 40));
                d.getPersistentDataContainer().set(tag, PersistentDataType.BYTE, (byte) 1);
            });
            affiches.put(e.cle(), t.getUniqueId());
        }
    }

    public void retirerAffiches() {
        for (UUID id : affiches.values()) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) {
                e.remove();
            }
        }
        affiches.clear();
    }

    // ------------------------------------------------------------------ Achat

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClic(PlayerInteractEvent ev) {
        if (ev.getAction() != Action.RIGHT_CLICK_BLOCK || ev.getClickedBlock() == null) {
            return;
        }
        Echoppe e = parBloc.get(cle(ev.getClickedBlock()));
        if (e == null || e.proprio.equals(ev.getPlayer().getUniqueId())) {
            return; // le vendeur ouvre son coffre normalement
        }
        ev.setCancelled(true);
        if (ev.getHand() == EquipmentSlot.HAND) {
            ouvrir(ev.getPlayer(), e);
        }
    }

    private void ouvrir(Player p, Echoppe e) {
        World w = Bukkit.getWorld(e.monde);
        if (w == null || !(w.getBlockAt(e.x, e.y, e.z).getState(false) instanceof Container cont)) {
            return;
        }
        int lots = stock(cont.getInventory(), e.objet) / e.lot;
        Menu menu = new Menu(3, "<dark_gray>Échoppe de " + e.proprioNom);
        ItemStack vitrine = e.objet.clone();
        vitrine.setAmount(Math.min(e.lot, vitrine.getMaxStackSize()));
        menu.set(13, vitrine, null);
        menu.set(4, Menu.icone(Material.OAK_SIGN, Msg.OR + "<bold>" + e.lot + " × " + Msg.nom(e.objet.getType()),
                "<gray>Prix : " + Msg.argent(e.prix), "<gray>Vendeur : <white>" + e.proprioNom,
                lots > 0 ? "<gray>En stock : <white>" + lots + " lot" + (lots > 1 ? "s" : "") : "<red>Rupture de stock"), null);
        if (lots > 0) {
            menu.set(11, Menu.icone(Material.LIME_CONCRETE, "<green><bold>Acheter 1 lot", "<gray>" + Msg.argent(e.prix)),
                    (pl, clic) -> acheter(pl, e, 1));
            int gros = Math.min(lots, 8);
            if (gros > 1) {
                menu.set(15, Menu.icone(Material.EMERALD_BLOCK, "<green><bold>Acheter " + gros + " lots", "<gray>" + Msg.argent(e.prix * gros)),
                        (pl, clic) -> acheter(pl, e, gros));
            }
        }
        menu.set(22, Menu.icone(Material.GOLD_INGOT, Msg.OR + "<bold>Ton solde", "<gray>Chargement…"), null);
        plugin.db().then(plugin.eco().solde(p.getUniqueId()), s ->
                menu.set(22, Menu.icone(Material.GOLD_INGOT, Msg.OR + "<bold>Ton solde", Msg.argent(s)), null), null);
        menu.open(p);
    }

    private void acheter(Player p, Echoppe e, int voulu) {
        World w = Bukkit.getWorld(e.monde);
        if (!parBloc.containsKey(e.cle()) || w == null || !(w.getBlockAt(e.x, e.y, e.z).getState(false) instanceof Container cont)) {
            p.closeInventory();
            Msg.send(p, "<red>Cette échoppe n'existe plus.");
            return;
        }
        Inventory inv = cont.getInventory();
        int lots = Math.min(voulu, Math.min(stock(inv, e.objet) / e.lot, Objets.place(p, e.objet) / e.lot));
        if (lots < 1) {
            Msg.send(p, "<red>Rupture de stock, ou plus de place dans ton inventaire.");
            ouvrir(p, e);
            return;
        }
        int quantite = lots * e.lot;
        double total = Math.round(e.prix * lots * 100) / 100.0;
        double taxe = plugin.getConfig().getDouble("echoppes.taxe", 0) / 100.0;
        // Les objets sortent du coffre d'abord ; ils y retournent si le paiement ne passe pas.
        retirer(inv, e.objet, quantite);
        UUID acheteur = p.getUniqueId();
        ItemStack lotObjets = e.objet.clone();
        Runnable remettre = () -> {
            int reste = quantite;
            while (reste > 0) {
                ItemStack part = lotObjets.clone();
                part.setAmount(Math.min(reste, part.getMaxStackSize()));
                reste -= part.getAmount();
                Location l = new Location(w, e.x + 0.5, e.y + 1, e.z + 0.5);
                (w.getBlockAt(e.x, e.y, e.z).getState(false) instanceof Container c2 ? c2.getInventory().addItem(part) : Map.of(0, part))
                        .values().forEach(r -> w.dropItemNaturally(l, r));
            }
        };
        plugin.db().then(plugin.db().transaction(c -> {
            if (!Economie.retirer(c, acheteur, total)) {
                return false;
            }
            Economie.crediter(c, e.proprio, e.proprioNom, Math.floor(total * (1 - taxe) * 100) / 100.0);
            return true;
        }), ok -> {
            if (!ok) {
                remettre.run();
                Msg.send(p, "<red>Pas assez de crédits : il faut " + Msg.argent(total) + "<red>.");
                return;
            }
            int reste = quantite;
            while (reste > 0) {
                ItemStack part = lotObjets.clone();
                part.setAmount(Math.min(reste, part.getMaxStackSize()));
                reste -= part.getAmount();
                if (p.isOnline()) {
                    Objets.donner(p, part);
                } else {
                    w.dropItemNaturally(new Location(w, e.x + 0.5, e.y + 1, e.z + 0.5), part);
                }
            }
            plugin.stats().ajouter(e.proprio, e.proprioNom, "ventes", total);
            Msg.send(p, "Acheté à <white>" + e.proprioNom + "<gray> : " + quantite + " × " + Msg.nom(e.objet.getType()) + " pour " + Msg.argent(total) + ".");
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.1f);
            Player vendeur = Bukkit.getPlayer(e.proprio);
            if (vendeur != null) {
                Msg.send(vendeur, "<white>" + p.getName() + "<gray> a acheté à ton échoppe : " + quantite + " × " + Msg.nom(e.objet.getType())
                        + " (" + Msg.argent(total) + ").");
            }
            if (p.isOnline()) {
                ouvrir(p, e);
            }
        }, () -> {
            remettre.run();
            Msg.send(p, "<red>Achat impossible pour le moment (base de données).");
        });
    }

    // ------------------------------------------------------------------ Liste

    private void liste(Player p, int page, boolean miennes) {
        List<Echoppe> toutes = new ArrayList<>(parBloc.values());
        if (miennes) {
            toutes.removeIf(e -> !e.proprio.equals(p.getUniqueId()));
        }
        if (toutes.isEmpty()) {
            Msg.send(p, miennes ? "Tu n'as aucune échoppe. <white>/echoppe creer <prix> [quantité]<gray> en regardant un coffre de ta base, objet en main."
                    : "Aucune échoppe pour l'instant. Ouvre la première : <white>/echoppe");
            return;
        }
        int pages = (toutes.size() + 44) / 45;
        int pg = Math.max(0, Math.min(pages - 1, page));
        Menu menu = new Menu(6, Msg.OR + "<bold>" + (miennes ? "Mes échoppes" : "Échoppes des joueurs") + " <dark_gray>" + (pg + 1) + "/" + pages);
        for (int i = 0; i < 45 && pg * 45 + i < toutes.size(); i++) {
            Echoppe e = toutes.get(pg * 45 + i);
            ItemStack icone = e.objet.clone();
            icone.setAmount(Math.min(e.lot, icone.getMaxStackSize()));
            icone.editMeta(meta -> meta.lore(List.of(
                    Msg.item("<gray>Prix : " + Msg.argent(e.prix) + " <gray>les <white>" + e.lot),
                    Msg.item("<gray>Vendeur : <white>" + e.proprioNom),
                    Msg.item("<gray>Où : <white>" + e.x + " " + e.y + " " + e.z + (e.monde.endsWith("_nether") ? " (Nether)" : "")),
                    Msg.item("<dark_gray>Clic : coordonnées dans le chat"))));
            menu.set(i, icone, (pl, clic) -> Msg.send(pl, "Échoppe de <white>" + e.proprioNom + "<gray> : <white>" + e.x + " " + e.y + " " + e.z
                    + (e.monde.endsWith("_nether") ? " (Nether)" : "") + "<gray> — " + e.lot + " × " + Msg.nom(e.objet.getType()) + " pour " + Msg.argent(e.prix) + "."));
        }
        if (pg > 0) {
            menu.set(45, Menu.icone(Material.ARROW, "<white>Page précédente"), (pl, clic) -> liste(pl, pg - 1, miennes));
        }
        if (pg < pages - 1) {
            menu.set(53, Menu.icone(Material.ARROW, "<white>Page suivante"), (pl, clic) -> liste(pl, pg + 1, miennes));
        }
        menu.open(p);
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (!(s instanceof Player p)) {
            return true;
        }
        if (cmd.getName().equals("echoppes")) {
            liste(p, 0, false);
            return true;
        }
        String sub = a.length == 0 ? "" : a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "creer" -> {
                if (a.length < 2) {
                    Msg.send(p, "<red>Usage : <white>/echoppe creer <prix> [quantité par achat]<red>, objet en main, en regardant un coffre de ta base.");
                } else {
                    creer(p, a[1], a.length > 2 ? a[2] : null);
                }
            }
            case "supprimer" -> {
                Block b = p.getTargetBlockExact(5);
                Echoppe e = b == null ? null : parBloc.get(cle(b));
                if (e == null) {
                    Msg.send(p, "<red>Regarde l'échoppe à supprimer.");
                } else if (!e.proprio.equals(p.getUniqueId()) && !p.hasPermission("survie.admin")) {
                    Msg.send(p, "<red>Cette échoppe est à " + e.proprioNom + ".");
                } else {
                    supprimer(e);
                    Msg.send(p, "Échoppe fermée. Le coffre et son contenu restent en place.");
                }
            }
            case "liste" -> liste(p, 0, true);
            default -> {
                Msg.send(p, Msg.OR + "<bold>Échoppes<gray> : vends tes objets depuis un coffre de ta base, même absent.");
                Msg.send(p, "<white>/echoppe creer <prix> [quantité]<gray> : objet en main, en regardant le coffre.");
                Msg.send(p, "<white>/echoppe supprimer<gray>, <white>/echoppe liste<gray> (les tiennes), <white>/echoppes<gray> (toutes).");
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (cmd.getName().equals("echoppe") && a.length == 1) {
            out.addAll(List.of("creer", "supprimer", "liste"));
        }
        out.removeIf(x -> !x.startsWith(a[a.length - 1].toLowerCase(Locale.ROOT)));
        return out;
    }
}
