package fr.orbit.survie;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Hôtel des ventes (table survie_hdv). Le vendeur met en vente l'objet qu'il a en main ; l'acheteur paie,
 * le vendeur est crédité (moins la taxe) même s'il n'est pas connecté. Une vente non vendue à temps reste
 * à récupérer dans « Mes ventes ». Toute la vente se fait en une transaction sur le fil de la base.
 */
public final class Hdv {

    record Annonce(long id, UUID vendeur, String vendeurNom, ItemStack objet, double prix, Timestamp expire, String statut) {
        boolean expiree() {
            return expire.getTime() < System.currentTimeMillis();
        }
    }

    enum Resultat { OK, DISPARUE, PAS_ASSEZ, SOI_MEME }

    record Achat(Resultat resultat, Annonce annonce) {}

    private static final int PAR_PAGE = 45;
    private final OrbitSurvie plugin;

    public Hdv(OrbitSurvie plugin) {
        this.plugin = plugin;
    }

    private Db db() {
        return plugin.db();
    }

    private boolean actif(Player p) {
        if (!db().available()) {
            Msg.send(p, "<red>L'hôtel des ventes est fermé : la base de données est injoignable.");
            return false;
        }
        return true;
    }

    static String encoder(ItemStack item) {
        return Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }

    static ItemStack decoder(String data) {
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(data));
        } catch (Exception e) {
            return new ItemStack(Material.BARRIER);
        }
    }

    private static Annonce lire(ResultSet rs) throws java.sql.SQLException {
        return new Annonce(rs.getLong("id"), UUID.fromString(rs.getString("vendeur")), rs.getString("vendeur_nom"),
                decoder(rs.getString("objet")), rs.getDouble("prix"), rs.getTimestamp("expire_le"), rs.getString("statut"));
    }

    // ------------------------------------------------------------------ Vendre

    public void vendre(Player p, String prixTexte) {
        if (!actif(p)) {
            return;
        }
        double prix;
        try {
            prix = Math.floor(Double.parseDouble(prixTexte.replace(',', '.')) * 100) / 100.0;
        } catch (NumberFormatException e) {
            Msg.send(p, "<red>Prix invalide. Exemple : <white>/hdv vendre 250");
            return;
        }
        double max = plugin.getConfig().getDouble("hdv.prix-max", 10_000_000);
        if (prix < 1 || prix > max) {
            Msg.send(p, "<red>Le prix doit être entre 1 et " + Msg.nombre(max) + ".");
            return;
        }
        ItemStack main = p.getInventory().getItemInMainHand();
        if (main.getType().isAir()) {
            Msg.send(p, "<red>Prends en main l'objet à vendre.");
            return;
        }
        ItemStack objet = main.clone();
        p.getInventory().setItemInMainHand(null);
        int maxVentes = plugin.getConfig().getInt("hdv.max-ventes", 10);
        int heures = plugin.getConfig().getInt("hdv.duree-heures", 48);
        UUID uuid = p.getUniqueId();
        String nom = p.getName();
        String data = encoder(objet);
        db().then(db().run(c -> {
            try (PreparedStatement st = c.prepareStatement(
                    "SELECT COUNT(*) FROM survie_hdv WHERE vendeur = ? AND statut = 'en_vente'")) {
                st.setString(1, uuid.toString());
                try (ResultSet rs = st.executeQuery()) {
                    rs.next();
                    if (rs.getInt(1) >= maxVentes) {
                        return false;
                    }
                }
            }
            try (PreparedStatement st = c.prepareStatement("INSERT INTO survie_hdv (vendeur, vendeur_nom, objet, prix, expire_le) "
                    + "VALUES (?, ?, ?, ?, NOW() + INTERVAL ? HOUR)")) {
                st.setString(1, uuid.toString());
                st.setString(2, nom);
                st.setString(3, data);
                st.setDouble(4, prix);
                st.setInt(5, heures);
                st.executeUpdate();
            }
            return true;
        }), ok -> {
            if (!ok) {
                Objets.donner(p, objet);
                Msg.send(p, "<red>Tu as déjà " + maxVentes + " ventes en cours. Retire-en une dans <white>/hdv ventes<red>.");
                return;
            }
            Msg.send(p, "Mis en vente : <white>" + objet.getAmount() + " × " + Msg.nom(objet.getType())
                    + "<gray> pour " + Msg.argent(prix) + " (" + heures + " h).");
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.5f);
        }, () -> {
            Objets.donner(p, objet);
            Msg.send(p, "<red>Mise en vente impossible (base de données) : objet rendu.");
        });
    }

    // ------------------------------------------------------------------ Parcourir

    public void ouvrir(Player p, int page) {
        if (!actif(p)) {
            return;
        }
        int offset = Math.max(0, page) * PAR_PAGE;
        db().then(db().run(c -> {
            List<Annonce> out = new ArrayList<>();
            try (PreparedStatement st = c.prepareStatement("SELECT * FROM survie_hdv WHERE statut = 'en_vente' "
                    + "AND expire_le > NOW() ORDER BY mise_le DESC LIMIT ? OFFSET ?")) {
                st.setInt(1, PAR_PAGE + 1);
                st.setInt(2, offset);
                try (ResultSet rs = st.executeQuery()) {
                    while (rs.next()) {
                        out.add(lire(rs));
                    }
                }
            }
            return out;
        }), annonces -> afficher(p, Math.max(0, page), annonces), () -> Msg.send(p, "<red>Hôtel des ventes injoignable."));
    }

    private void afficher(Player p, int page, List<Annonce> annonces) {
        Menu menu = new Menu(6, Msg.OR + "<bold>Hôtel des ventes</bold> <dark_gray>· page " + (page + 1));
        for (int i = 0; i < annonces.size() && i < PAR_PAGE; i++) {
            Annonce a = annonces.get(i);
            menu.set(i, vitrine(a, "<gray>Vendeur : <white>" + a.vendeurNom(), "<yellow>Clic : acheter"),
                    (pl, click) -> confirmer(pl, a, page));
        }
        if (page > 0) {
            menu.set(45, Menu.icone(Material.ARROW, "<white>Page précédente"), (pl, c) -> ouvrir(pl, page - 1));
        }
        if (annonces.size() > PAR_PAGE) {
            menu.set(53, Menu.icone(Material.ARROW, "<white>Page suivante"), (pl, c) -> ouvrir(pl, page + 1));
        }
        menu.set(47, Menu.icone(Material.CHEST, Msg.BLEU + "<bold>Mes ventes", "<gray>Ventes en cours et invendus à récupérer"),
                (pl, c) -> mesVentes(pl));
        menu.set(49, Menu.icone(Material.GOLD_INGOT, Msg.OR + "<bold>Ton solde", "<gray>Chargement…"), null);
        menu.set(51, Menu.icone(Material.WRITABLE_BOOK, Msg.BLEU + "<bold>Vendre un objet",
                "<gray>Prends l'objet en main puis tape", "<white>/hdv vendre <prix>",
                "<gray>Taxe : " + plugin.getConfig().getInt("hdv.taxe", 5) + " % sur la vente"), null);
        menu.open(p);
        db().then(plugin.eco().solde(p.getUniqueId()), s ->
                menu.set(49, Menu.icone(Material.GOLD_INGOT, Msg.OR + "<bold>Ton solde", Msg.argent(s)), null), null);
    }

    private ItemStack vitrine(Annonce a, String... lignes) {
        ItemStack it = a.objet().clone();
        it.editMeta(meta -> {
            List<net.kyori.adventure.text.Component> lore = meta.hasLore() && meta.lore() != null
                    ? new ArrayList<>(meta.lore()) : new ArrayList<>();
            lore.add(Msg.item("<dark_gray>――――――――――"));
            lore.add(Msg.item("<gray>Prix : " + Msg.argent(a.prix())));
            for (String l : lignes) {
                lore.add(Msg.item(l));
            }
            meta.lore(lore);
        });
        return it;
    }

    private void confirmer(Player p, Annonce a, int page) {
        if (a.vendeur().equals(p.getUniqueId())) {
            Msg.send(p, "<red>C'est ta propre vente : retire-la dans « Mes ventes ».");
            return;
        }
        Menu menu = new Menu(3, Msg.OR + "<bold>Confirmer l'achat");
        menu.set(13, vitrine(a, "<gray>Vendeur : <white>" + a.vendeurNom()), null);
        menu.set(11, Menu.icone(Material.LIME_CONCRETE, "<green><bold>Acheter", "<gray>pour " + Msg.argent(a.prix())),
                (pl, c) -> {
                    pl.closeInventory();
                    acheter(pl, a.id());
                });
        menu.set(15, Menu.icone(Material.RED_CONCRETE, "<red><bold>Annuler"), (pl, c) -> ouvrir(pl, page));
        menu.open(p);
    }

    private void acheter(Player p, long id) {
        UUID acheteur = p.getUniqueId();
        double taxe = plugin.getConfig().getDouble("hdv.taxe", 5) / 100.0;
        db().then(db().transaction(c -> {
            Annonce a;
            try (PreparedStatement st = c.prepareStatement("SELECT * FROM survie_hdv WHERE id = ? AND statut = 'en_vente' "
                    + "AND expire_le > NOW() FOR UPDATE")) {
                st.setLong(1, id);
                try (ResultSet rs = st.executeQuery()) {
                    if (!rs.next()) {
                        return new Achat(Resultat.DISPARUE, null);
                    }
                    a = lire(rs);
                }
            }
            if (a.vendeur().equals(acheteur)) {
                return new Achat(Resultat.SOI_MEME, a);
            }
            if (!Economie.retirer(c, acheteur, a.prix())) {
                return new Achat(Resultat.PAS_ASSEZ, a);
            }
            try (PreparedStatement st = c.prepareStatement(
                    "UPDATE survie_hdv SET statut = 'vendu', acheteur = ?, vendu_le = NOW() WHERE id = ?")) {
                st.setString(1, acheteur.toString());
                st.setLong(2, id);
                st.executeUpdate();
            }
            Economie.crediter(c, a.vendeur(), a.vendeurNom(), Math.floor(a.prix() * (1 - taxe) * 100) / 100.0);
            return new Achat(Resultat.OK, a);
        }), achat -> {
            switch (achat.resultat()) {
                case DISPARUE -> Msg.send(p, "<red>Trop tard : cette vente n'est plus disponible.");
                case SOI_MEME -> Msg.send(p, "<red>C'est ta propre vente.");
                case PAS_ASSEZ -> Msg.send(p, "<red>Pas assez de crédits : il faut " + Msg.argent(achat.annonce().prix()) + "<red>.");
                case OK -> livrer(p, achat.annonce());
            }
        }, () -> Msg.send(p, "<red>Achat impossible pour le moment (base de données)."));
    }

    private void livrer(Player p, Annonce a) {
        if (!p.isOnline()) {
            // Parti entre-temps : l'objet l'attend à sa prochaine connexion.
            db().run(c -> {
                try (PreparedStatement st = c.prepareStatement("UPDATE survie_hdv SET statut = 'a_livrer' WHERE id = ?")) {
                    st.setLong(1, a.id());
                    return st.executeUpdate();
                }
            });
            return;
        }
        Objets.donner(p, a.objet());
        Msg.send(p, "Acheté à <white>" + a.vendeurNom() + "<gray> : " + a.objet().getAmount() + " × "
                + Msg.nom(a.objet().getType()) + " pour " + Msg.argent(a.prix()) + ".");
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f);
        Player vendeur = Bukkit.getPlayer(a.vendeur());
        if (vendeur != null) {
            Msg.send(vendeur, "<white>" + p.getName() + "<gray> a acheté ta vente : " + a.objet().getAmount() + " × "
                    + Msg.nom(a.objet().getType()) + " (" + Msg.argent(a.prix()) + ", taxe déduite).");
        }
    }

    // ------------------------------------------------------------------ Mes ventes

    public void mesVentes(Player p) {
        if (!actif(p)) {
            return;
        }
        UUID uuid = p.getUniqueId();
        db().then(db().run(c -> {
            List<Annonce> out = new ArrayList<>();
            try (PreparedStatement st = c.prepareStatement("SELECT * FROM survie_hdv WHERE vendeur = ? "
                    + "AND statut = 'en_vente' ORDER BY mise_le DESC LIMIT 45")) {
                st.setString(1, uuid.toString());
                try (ResultSet rs = st.executeQuery()) {
                    while (rs.next()) {
                        out.add(lire(rs));
                    }
                }
            }
            return out;
        }), annonces -> {
            Menu menu = new Menu(6, Msg.BLEU + "<bold>Mes ventes");
            for (int i = 0; i < annonces.size(); i++) {
                Annonce a = annonces.get(i);
                menu.set(i, vitrine(a, a.expiree() ? "<red>Expirée : personne ne l'a achetée" : "<green>En vente",
                        "<yellow>Clic : récupérer l'objet"), (pl, c) -> {
                    pl.closeInventory();
                    recuperer(pl, a.id());
                });
            }
            menu.set(49, Menu.icone(Material.ARROW, "<white>Retour à l'hôtel des ventes"), (pl, c) -> ouvrir(pl, 0));
            menu.open(p);
        }, () -> Msg.send(p, "<red>Hôtel des ventes injoignable."));
    }

    private void recuperer(Player p, long id) {
        UUID uuid = p.getUniqueId();
        db().then(db().transaction(c -> {
            String data;
            try (PreparedStatement st = c.prepareStatement("SELECT objet FROM survie_hdv WHERE id = ? AND vendeur = ? "
                    + "AND statut = 'en_vente' FOR UPDATE")) {
                st.setLong(1, id);
                st.setString(2, uuid.toString());
                try (ResultSet rs = st.executeQuery()) {
                    if (!rs.next()) {
                        return null;
                    }
                    data = rs.getString(1);
                }
            }
            try (PreparedStatement st = c.prepareStatement("UPDATE survie_hdv SET statut = 'retire' WHERE id = ?")) {
                st.setLong(1, id);
                st.executeUpdate();
            }
            return data;
        }), data -> {
            if (data == null) {
                Msg.send(p, "<red>Cette vente n'est plus là (déjà vendue ?).");
                return;
            }
            ItemStack it = decoder(data);
            if (!p.isOnline()) {
                return;
            }
            Objets.donner(p, it);
            Msg.send(p, "Récupéré : <white>" + it.getAmount() + " × " + Msg.nom(it.getType()) + "<gray>.");
        }, () -> Msg.send(p, "<red>Récupération impossible pour le moment (base de données)."));
    }

    /** À la connexion : remet les achats payés mais pas reçus, et signale les invendus. */
    public void connexion(Player p) {
        if (!db().available()) {
            return;
        }
        UUID uuid = p.getUniqueId();
        db().then(db().transaction(c -> {
            List<String> objets = new ArrayList<>();
            try (PreparedStatement st = c.prepareStatement("SELECT id, objet FROM survie_hdv WHERE acheteur = ? AND statut = 'a_livrer' FOR UPDATE")) {
                st.setString(1, uuid.toString());
                try (ResultSet rs = st.executeQuery()) {
                    while (rs.next()) {
                        objets.add(rs.getString(2));
                    }
                }
            }
            try (PreparedStatement st = c.prepareStatement("UPDATE survie_hdv SET statut = 'vendu' WHERE acheteur = ? AND statut = 'a_livrer'")) {
                st.setString(1, uuid.toString());
                st.executeUpdate();
            }
            int expirees;
            try (Statement s = c.createStatement(); PreparedStatement st = c.prepareStatement(
                    "SELECT COUNT(*) FROM survie_hdv WHERE vendeur = ? AND statut = 'en_vente' AND expire_le <= NOW()")) {
                st.setString(1, uuid.toString());
                try (ResultSet rs = st.executeQuery()) {
                    rs.next();
                    expirees = rs.getInt(1);
                }
            }
            objets.add(0, String.valueOf(expirees));
            return objets;
        }), liste -> {
            if (!p.isOnline()) {
                return;
            }
            int expirees = Integer.parseInt(liste.remove(0));
            for (String data : liste) {
                ItemStack it = decoder(data);
                Objets.donner(p, it);
                Msg.send(p, "Achat de l'hôtel des ventes reçu : <white>" + it.getAmount() + " × " + Msg.nom(it.getType()));
            }
            if (expirees > 0) {
                Msg.send(p, "<yellow>" + expirees + " de tes ventes n'ont pas trouvé preneur : récupère-les avec <white>/hdv ventes<yellow>.");
            }
        }, null);
    }
}
