package fr.orbit.survie;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Contrats du jour : chaque jour, une commande par palier (facile, moyen, difficile), la même pour tout le
 * monde, tirée dans la liste de config.yml. On apporte les objets au capitaine du port, il paie en crédits ;
 * les trois contrats du jour livrés = une prime en plus. Chaque contrat se livre une fois par jour et par joueur.
 */
public final class Contrats {

    record Commande(Material objet, int quantite) {}

    record Palier(String id, String nom, double prime, List<Commande> commandes) {}

    /** Un contrat du jour : son numéro (0, 1, 2…), son palier, la commande tirée et ce qu'elle rapporte. */
    record Contrat(int numero, Palier palier, Commande commande, double recompense) {}

    private final OrbitSurvie plugin;
    private final List<Palier> paliers = new ArrayList<>();
    private double multiplicateur;
    private double primeComplet;

    public Contrats(OrbitSurvie plugin) {
        this.plugin = plugin;
        charger();
    }

    public void charger() {
        paliers.clear();
        multiplicateur = plugin.getConfig().getDouble("contrats.multiplicateur", 0.6);
        primeComplet = plugin.getConfig().getDouble("contrats.prime-complet", 250);
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("contrats.paliers");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) {
                continue;
            }
            List<Commande> commandes = new ArrayList<>();
            for (String ligne : s.getStringList("objets")) {
                String[] t = ligne.trim().split("\\s+");
                Material m = Material.matchMaterial(t[0]);
                int q = 1;
                try {
                    q = t.length > 1 ? Integer.parseInt(t[1]) : 1;
                } catch (NumberFormatException ignored) {
                }
                if (m == null || !m.isItem() || q < 1) {
                    plugin.getLogger().warning("Contrats, palier " + id + " : ligne ignorée « " + ligne + " »");
                    continue;
                }
                commandes.add(new Commande(m, q));
            }
            if (!commandes.isEmpty()) {
                paliers.add(new Palier(id, s.getString("nom", id), s.getDouble("prime", 50), commandes));
            }
        }
    }

    /** Les contrats d'un jour donné : le même tirage pour tous les joueurs. */
    List<Contrat> duJour(LocalDate jour) {
        List<Contrat> out = new ArrayList<>();
        for (int i = 0; i < paliers.size(); i++) {
            Palier p = paliers.get(i);
            Commande c = p.commandes().get(new Random(jour.toEpochDay() * 31L + i * 7919L).nextInt(p.commandes().size()));
            Marche.Article a = plugin.marche().article(c.objet());
            double valeur = a == null ? 0 : a.ref() * c.quantite() * multiplicateur;
            out.add(new Contrat(i, p, c, Math.round(p.prime() + valeur)));
        }
        return out;
    }

    // ------------------------------------------------------------------ Menu

    public void ouvrir(Player p) {
        if (!plugin.db().available()) {
            Msg.send(p, "<red>Les contrats sont indisponibles : la base de données est injoignable.");
            return;
        }
        if (paliers.isEmpty()) {
            Msg.send(p, "<red>Aucun contrat n'est configuré.");
            return;
        }
        LocalDate jour = LocalDate.now();
        UUID uuid = p.getUniqueId();
        plugin.db().then(plugin.db().run(c -> {
            Set<Integer> faits = new HashSet<>();
            try (PreparedStatement st = c.prepareStatement("SELECT numero FROM survie_contrats WHERE uuid = ? AND jour = ?")) {
                st.setString(1, uuid.toString());
                st.setString(2, jour.toString());
                try (ResultSet rs = st.executeQuery()) {
                    while (rs.next()) {
                        faits.add(rs.getInt(1));
                    }
                }
            }
            return faits;
        }), faits -> afficher(p, jour, faits), () -> Msg.send(p, "<red>Contrats indisponibles pour le moment."));
    }

    private void afficher(Player p, LocalDate jour, Set<Integer> faits) {
        Menu menu = new Menu(4, Msg.OR + "<bold>Contrats du jour");
        Menu.cadre(menu, 4);
        List<Contrat> contrats = duJour(jour);
        int[] cases = contrats.size() <= 3 ? new int[]{11, 13, 15} : new int[]{10, 11, 12, 13, 14, 15, 16};
        for (int i = 0; i < contrats.size() && i < cases.length; i++) {
            Contrat k = contrats.get(i);
            ItemStack modele = new ItemStack(k.commande().objet());
            int possede = Objets.compter(p, modele);
            boolean fait = faits.contains(k.numero());
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Apporte <white>" + k.commande().quantite() + " × " + Msg.nom(k.commande().objet()));
            lore.add("<gray>Tu en as <white>" + possede);
            lore.add("<gray>Récompense : " + Msg.argent(k.recompense()));
            lore.add("");
            lore.add(fait ? "<#3DDCB0>✔ Livré aujourd'hui" : possede >= k.commande().quantite()
                    ? "<green>Clic : livrer" : "<red>Il t'en manque " + (k.commande().quantite() - possede));
            ItemStack icone = Menu.icone(fait ? Material.LIME_DYE : k.commande().objet(),
                    Msg.OR + "<bold>Contrat " + k.palier().nom(), lore.toArray(new String[0]));
            icone.setAmount(Math.max(1, Math.min(64, fait ? 1 : k.commande().quantite())));
            menu.set(cases[i], icone, fait ? null : (pl, clic) -> livrer(pl, jour, k));
        }
        menu.set(31, Menu.icone(Material.NETHER_STAR, Msg.OR + "<bold>Prime du jour",
                "<gray>Les " + contrats.size() + " contrats livrés : " + Msg.argent(primeComplet) + " <gray>en plus.",
                "<gray>Livrés aujourd'hui : <white>" + faits.size() + " / " + contrats.size(),
                "<dark_gray>Nouveaux contrats chaque jour à minuit."), null);
        menu.open(p);
    }

    // ------------------------------------------------------------------ Livraison

    private void livrer(Player p, LocalDate jour, Contrat k) {
        if (!jour.equals(LocalDate.now())) {
            ouvrir(p); // minuit est passé, le menu date d'hier
            return;
        }
        ItemStack modele = new ItemStack(k.commande().objet());
        int voulu = k.commande().quantite();
        if (Objets.compter(p, modele) < voulu) {
            Msg.send(p, "<red>Il te faut " + voulu + " × " + Msg.nom(k.commande().objet()) + "<red>.");
            return;
        }
        // Les objets sont pris d'abord ; rendus si la livraison ne passe pas en base.
        int pris = Objets.retirer(p, modele, voulu);
        UUID uuid = p.getUniqueId();
        String nom = p.getName();
        int total = duJour(jour).size();
        plugin.db().then(plugin.db().transaction(c -> {
            try (PreparedStatement st = c.prepareStatement("INSERT IGNORE INTO survie_contrats (uuid, jour, numero) VALUES (?, ?, ?)")) {
                st.setString(1, uuid.toString());
                st.setString(2, jour.toString());
                st.setInt(3, k.numero());
                if (st.executeUpdate() == 0) {
                    return -1.0; // déjà livré aujourd'hui
                }
            }
            double gain = k.recompense();
            try (PreparedStatement st = c.prepareStatement("SELECT COUNT(*) FROM survie_contrats WHERE uuid = ? AND jour = ?")) {
                st.setString(1, uuid.toString());
                st.setString(2, jour.toString());
                try (ResultSet rs = st.executeQuery()) {
                    if (rs.next() && rs.getInt(1) >= total) {
                        gain += primeComplet;
                    }
                }
            }
            Economie.crediter(c, uuid, nom, gain);
            return gain;
        }), gain -> {
            if (gain < 0) {
                rendre(p, modele, pris);
                Msg.send(p, "<red>Tu as déjà livré ce contrat aujourd'hui.");
                return;
            }
            plugin.stats().ajouter(uuid, nom, "contrats", 1);
            if (p.isOnline()) {
                Msg.send(p, "Contrat " + k.palier().nom() + " livré : " + Msg.argent(gain)
                        + (gain > k.recompense() ? " <gray>(prime du jour comprise !)" : "."));
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.3f);
                ouvrir(p);
            }
        }, () -> {
            rendre(p, modele, pris);
            Msg.send(p, "<red>Livraison impossible pour le moment (base de données). Tes objets te sont rendus.");
        });
    }

    private static void rendre(Player p, ItemStack modele, int n) {
        if (n > 0 && p.isOnline()) {
            ItemStack it = modele.clone();
            it.setAmount(n);
            Objets.donner(p, it);
        }
    }
}
