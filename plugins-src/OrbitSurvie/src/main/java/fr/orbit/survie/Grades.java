package fr.orbit.survie;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Grades VIP / VIP+ / ORBIT achetés avec les crédits du jeu (jamais contre de l'argent réel), pour une durée.
 * Le grade est posé par LuckPerms (« parent addtemp … accumulate ») ; la table survie_grades garde la date de
 * fin pour l'afficher. Racheter un grade qu'on a déjà ajoute la durée à ce qui reste.
 */
public final class Grades {

    record Grade(String id, String nom, Material icone, double prix, int jours, List<String> avantages) {}

    private final OrbitSurvie plugin;
    private final Map<String, Grade> grades = new LinkedHashMap<>();
    private String contexte = "";

    public Grades(OrbitSurvie plugin) {
        this.plugin = plugin;
        charger();
    }

    public void charger() {
        grades.clear();
        contexte = plugin.getConfig().getString("grades.contexte", "").trim();
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("grades.liste");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection g = root.getConfigurationSection(id);
            if (g == null || !id.matches("[a-z0-9_]+")) {
                continue;
            }
            Material icone = Material.matchMaterial(g.getString("icone", "NETHER_STAR"));
            grades.put(id, new Grade(id, g.getString("nom", id), icone == null ? Material.NETHER_STAR : icone,
                    g.getDouble("prix", 10000), Math.max(1, g.getInt("jours", 30)), g.getStringList("avantages")));
        }
    }

    public List<String> ids() {
        return new ArrayList<>(grades.keySet());
    }

    private boolean pret(Player p) {
        if (!plugin.db().available()) {
            Msg.send(p, "<red>Les grades sont indisponibles : la base de données est injoignable.");
            return false;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            Msg.send(p, "<red>Les grades sont indisponibles pour le moment (LuckPerms absent).");
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ Menu

    public void ouvrir(Player p) {
        if (!pret(p)) {
            return;
        }
        UUID uuid = p.getUniqueId();
        plugin.db().then(plugin.db().run(c -> {
            Map<String, Long> restes = new HashMap<>();
            try (PreparedStatement st = c.prepareStatement(
                    "SELECT grade, TIMESTAMPDIFF(SECOND, NOW(), expire_le) FROM survie_grades WHERE uuid = ?")) {
                st.setString(1, uuid.toString());
                try (ResultSet rs = st.executeQuery()) {
                    while (rs.next()) {
                        restes.put(rs.getString(1), rs.getLong(2) * 1000L);
                    }
                }
            }
            return restes;
        }), restes -> afficher(p, restes), () -> Msg.send(p, "<red>Grades indisponibles pour le moment."));
    }

    private void afficher(Player p, Map<String, Long> restes) {
        Menu menu = new Menu(4, Msg.OR + "<bold>Grades");
        Menu.cadre(menu, 4);
        int[] cases = {11, 13, 15, 20, 22, 24};
        int i = 0;
        for (Grade g : grades.values()) {
            if (i >= cases.length) {
                break;
            }
            long reste = restes.getOrDefault(g.id(), 0L);
            List<String> lore = new ArrayList<>();
            for (String a : g.avantages()) {
                lore.add("<dark_gray>• <gray>" + a);
            }
            lore.add("");
            lore.add("<gray>Prix : " + Msg.argent(g.prix()) + " <gray>pour <white>" + g.jours() + " jours");
            if (reste > 0) {
                lore.add("<#3DDCB0>Tu l'as encore " + Bases.duree(reste));
                lore.add("<green>Clic : prolonger de " + g.jours() + " jours");
            } else {
                lore.add("<green>Clic : acheter");
            }
            lore.add("<dark_gray>Payé en crédits du jeu uniquement.");
            ItemStack icone = Menu.icone(g.icone(), "<bold>" + g.nom(), lore.toArray(new String[0]));
            icone.editMeta(meta -> {
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
                if (reste > 0) {
                    meta.setEnchantmentGlintOverride(true);
                }
            });
            menu.set(cases[i++], icone, (pl, clic) -> confirmer(pl, g));
        }
        menu.set(31, Menu.icone(Material.GOLD_INGOT, Msg.OR + "<bold>Ton solde", "<gray>Chargement…"), null);
        plugin.db().then(plugin.eco().solde(p.getUniqueId()), s ->
                menu.set(31, Menu.icone(Material.GOLD_INGOT, Msg.OR + "<bold>Ton solde", Msg.argent(s)), null), null);
        menu.open(p);
    }

    private void confirmer(Player p, Grade g) {
        Menu menu = new Menu(3, "<dark_gray>Acheter " + g.nom() + " <dark_gray>?");
        menu.set(11, Menu.icone(Material.LIME_CONCRETE, "<green><bold>Confirmer",
                "<gray>Grade " + g.nom() + " <gray>pendant <white>" + g.jours() + " jours", "<gray>Prix : " + Msg.argent(g.prix())),
                (pl, clic) -> {
                    pl.closeInventory();
                    acheter(pl, g);
                });
        menu.set(13, Menu.icone(g.icone(), "<bold>" + g.nom()), null);
        menu.set(15, Menu.icone(Material.RED_CONCRETE, "<red><bold>Annuler"), (pl, clic) -> ouvrir(pl));
        menu.open(p);
    }

    // ------------------------------------------------------------------ Achat

    public void acheter(Player p, String id) {
        Grade g = grades.get(id);
        if (g == null) {
            Msg.send(p, "<red>Grade inconnu. <white>/grade<red> pour voir la liste.");
            return;
        }
        confirmer(p, g);
    }

    private void acheter(Player p, Grade g) {
        if (!pret(p)) {
            return;
        }
        UUID uuid = p.getUniqueId();
        String nom = p.getName();
        plugin.db().then(plugin.db().transaction(c -> {
            if (!Economie.retirer(c, uuid, g.prix())) {
                return -1L;
            }
            try (PreparedStatement st = c.prepareStatement("INSERT INTO survie_grades (uuid, grade, expire_le) "
                    + "VALUES (?, ?, NOW() + INTERVAL ? DAY) "
                    + "ON DUPLICATE KEY UPDATE expire_le = GREATEST(expire_le, NOW()) + INTERVAL ? DAY")) {
                st.setString(1, uuid.toString());
                st.setString(2, g.id());
                st.setInt(3, g.jours());
                st.setInt(4, g.jours());
                st.executeUpdate();
            }
            try (PreparedStatement st = c.prepareStatement(
                    "SELECT TIMESTAMPDIFF(SECOND, NOW(), expire_le) FROM survie_grades WHERE uuid = ? AND grade = ?")) {
                st.setString(1, uuid.toString());
                st.setString(2, g.id());
                try (ResultSet rs = st.executeQuery()) {
                    return rs.next() ? rs.getLong(1) * 1000L : 0L;
                }
            }
        }), reste -> {
            if (reste < 0) {
                Msg.send(p, "<red>Pas assez de crédits : il faut " + Msg.argent(g.prix()) + "<red>.");
                return;
            }
            // Le paiement est passé : LuckPerms pose le grade (la durée s'ajoute à ce qui reste).
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + uuid + " parent addtemp " + g.id() + " "
                    + g.jours() + "d accumulate" + (contexte.isEmpty() ? "" : " " + contexte));
            plugin.getLogger().info("Grade " + g.id() + " (" + g.jours() + " j) acheté par " + nom + " pour " + g.prix() + " crédits.");
            Bukkit.broadcast(Msg.msg("<white>" + nom + "<gray> devient " + g.nom() + "<gray> ! <dark_gray>(/grade)"));
            if (p.isOnline()) {
                Msg.send(p, "Grade " + g.nom() + "<gray> actif pour encore <white>" + Bases.duree(reste) + "<gray>. Ton kit t'attend : <white>/kit");
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1f);
            }
        }, () -> Msg.send(p, "<red>Achat impossible pour le moment (base de données)."));
    }
}
