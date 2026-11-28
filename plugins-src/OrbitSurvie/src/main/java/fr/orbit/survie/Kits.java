package fr.orbit.survie;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Kits (config « kits ») : un par grade, avec un délai entre deux prises (table survie_kits).
 * Le grade se lit par la permission LuckPerms « group.<grade> » (héritage compris : ORBIT a aussi VIP+ et VIP).
 * Délai -1 = une seule fois (kit de départ, donné tout seul à la première connexion).
 */
public final class Kits {

    record Kit(String id, String nom, String grade, Material icone, long delaiHeures, List<ItemStack> objets) {}

    private final OrbitSurvie plugin;
    private final Map<String, Kit> kits = new LinkedHashMap<>();

    public Kits(OrbitSurvie plugin) {
        this.plugin = plugin;
        charger();
    }

    public void charger() {
        kits.clear();
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("kits");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection k = root.getConfigurationSection(id);
            if (k == null) {
                continue;
            }
            List<ItemStack> objets = new ArrayList<>();
            for (Map<?, ?> m : k.getMapList("objets")) {
                ItemStack it = objet(id, m);
                if (it != null) {
                    objets.add(it);
                }
            }
            Material icone = Material.matchMaterial(k.getString("icone", "CHEST"));
            kits.put(id, new Kit(id, k.getString("nom", id), k.getString("grade", ""),
                    icone == null ? Material.CHEST : icone, k.getLong("delai-heures", 24), objets));
        }
    }

    private ItemStack objet(String kit, Map<?, ?> m) {
        if ("ARMOIRE_DE_BASE".equalsIgnoreCase(String.valueOf(m.get("objet"))) && plugin.bases() != null) {
            return plugin.bases().objet(m.get("quantite") instanceof Number q ? q.intValue() : 1);
        }
        Material mat = Material.matchMaterial(String.valueOf(m.get("objet")));
        if (mat == null || !mat.isItem()) {
            plugin.getLogger().warning("Kit " + kit + " : objet inconnu " + m.get("objet"));
            return null;
        }
        int n = m.get("quantite") instanceof Number q ? q.intValue() : 1;
        ItemStack it = new ItemStack(mat, Math.max(1, n));
        Object nom = m.get("nom");
        Object ench = m.get("enchant");
        if (nom != null || ench instanceof Map<?, ?>) {
            it.editMeta(meta -> {
                if (nom != null) {
                    meta.displayName(Msg.item(String.valueOf(nom)));
                }
                if (ench instanceof Map<?, ?> em) {
                    em.forEach((cle, niv) -> {
                        Enchantment e = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(String.valueOf(cle).toLowerCase()));
                        if (e != null && niv instanceof Number lv) {
                            meta.addEnchant(e, lv.intValue(), true);
                        } else {
                            plugin.getLogger().warning("Kit " + kit + " : enchantement inconnu " + cle);
                        }
                    });
                }
            });
        }
        return it;
    }

    private static boolean aLeGrade(Player p, Kit k) {
        return k.grade().isEmpty() || p.hasPermission("group." + k.grade()) || p.hasPermission("survie.kit." + k.id());
    }

    /** Millisecondes avant la prochaine prise (0 = disponible, -1 = déjà pris pour toujours). */
    private CompletableFuture<Map<String, Long>> attentes(UUID uuid) {
        return plugin.db().run(c -> {
            Map<String, Long> out = new LinkedHashMap<>();
            try (PreparedStatement st = c.prepareStatement(
                    "SELECT kit, TIMESTAMPDIFF(SECOND, pris_le, NOW()) FROM survie_kits WHERE uuid = ?")) {
                st.setString(1, uuid.toString());
                try (ResultSet rs = st.executeQuery()) {
                    while (rs.next()) {
                        Kit k = kits.get(rs.getString(1));
                        if (k == null) {
                            continue;
                        }
                        long depuis = rs.getLong(2) * 1000L;
                        out.put(k.id(), k.delaiHeures() < 0 ? -1L : Math.max(0, k.delaiHeures() * 3_600_000L - depuis));
                    }
                }
            }
            return out;
        });
    }

    private static String duree(long ms) {
        long min = (ms + 59_999) / 60_000;
        long j = min / 1440, h = (min % 1440) / 60, m = min % 60;
        return j > 0 ? j + " j " + h + " h" : h > 0 ? h + " h " + m + " min" : m + " min";
    }

    // ------------------------------------------------------------------ Menu

    public void ouvrir(Player p) {
        if (!plugin.db().available()) {
            Msg.send(p, "<red>Les kits sont indisponibles : la base de données est injoignable.");
            return;
        }
        plugin.db().then(attentes(p.getUniqueId()), att -> afficher(p, att),
                () -> Msg.send(p, "<red>Kits indisponibles pour le moment."));
    }

    private void afficher(Player p, Map<String, Long> att) {
        Menu menu = new Menu(3, Msg.OR + "<bold>Kits");
        int[] cases = {10, 11, 12, 13, 14, 15, 16};
        int i = 0;
        for (Kit k : kits.values()) {
            if (i >= cases.length) {
                break;
            }
            long reste = att.getOrDefault(k.id(), 0L);
            boolean grade = aLeGrade(p, k);
            List<String> lore = new ArrayList<>();
            for (ItemStack it : k.objets()) {
                lore.add("<dark_gray>• <gray>" + it.getAmount() + " × " + Msg.nom(it.getType()));
            }
            lore.add("");
            lore.add("<gray>Délai : <white>" + (k.delaiHeures() < 0 ? "une seule fois" : duree(k.delaiHeures() * 3_600_000L)));
            if (!grade) {
                lore.add("<red>Réservé au grade " + k.nom());
            } else if (reste < 0) {
                lore.add("<red>Déjà pris");
            } else if (reste > 0) {
                lore.add("<yellow>Disponible dans " + duree(reste));
            } else {
                lore.add("<green>Disponible : clic pour le prendre");
            }
            ItemStack icone = Menu.icone(grade && reste == 0 ? k.icone() : Material.GRAY_DYE, Msg.OR + "<bold>Kit " + k.nom(),
                    lore.toArray(new String[0]));
            if (grade && reste == 0) {
                icone.editMeta(meta -> {
                    meta.setEnchantmentGlintOverride(true);
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                });
            }
            menu.set(cases[i++], icone, (pl, click) -> {
                pl.closeInventory();
                prendre(pl, k.id(), false);
            });
        }
        menu.open(p);
    }

    // ------------------------------------------------------------------ Prise

    /** Donne le kit si le grade et le délai le permettent. {@code auto} : kit de départ à la connexion. */
    public void prendre(Player p, String id, boolean auto) {
        Kit k = kits.get(id);
        if (k == null || !plugin.db().available()) {
            return;
        }
        if (!aLeGrade(p, k)) {
            Msg.send(p, "<red>Ce kit est réservé au grade " + k.nom() + "<red>.");
            return;
        }
        UUID uuid = p.getUniqueId();
        plugin.db().then(plugin.db().transaction(c -> {
            try (PreparedStatement st = c.prepareStatement(
                    "SELECT TIMESTAMPDIFF(SECOND, pris_le, NOW()) FROM survie_kits WHERE uuid = ? AND kit = ? FOR UPDATE")) {
                st.setString(1, uuid.toString());
                st.setString(2, id);
                try (ResultSet rs = st.executeQuery()) {
                    if (rs.next()) {
                        if (k.delaiHeures() < 0) {
                            return -1L;
                        }
                        long reste = k.delaiHeures() * 3_600_000L - rs.getLong(1) * 1000L;
                        if (reste > 0) {
                            return reste;
                        }
                    }
                }
            }
            try (PreparedStatement st = c.prepareStatement("INSERT INTO survie_kits (uuid, kit, pris_le) VALUES (?, ?, NOW()) "
                    + "ON DUPLICATE KEY UPDATE pris_le = NOW()")) {
                st.setString(1, uuid.toString());
                st.setString(2, id);
                st.executeUpdate();
            }
            return 0L;
        }), reste -> {
            if (reste < 0) {
                if (!auto) {
                    Msg.send(p, "<red>Tu as déjà pris ce kit.");
                }
                return;
            }
            if (reste > 0) {
                Msg.send(p, "<red>Kit " + k.nom() + "<red> disponible dans " + duree(reste) + ".");
                return;
            }
            if (!p.isOnline()) {
                return;
            }
            k.objets().forEach(it -> Objets.donner(p, it.clone()));
            Msg.send(p, "Kit " + Msg.OR + k.nom() + "<gray> reçu !");
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f);
        }, () -> Msg.send(p, "<red>Kit indisponible pour le moment (base de données)."));
    }

    /**
     * Rattrapage : ceux qui ont pris le kit Départ avant que l'armoire de base y soit (date « bases.rattrapage-avant »)
     * et qui n'ont aucune base en reçoivent une, une seule fois (ligne « armoire-rattrapage » dans survie_kits).
     */
    public void rattraperArmoire(Player p) {
        String avant = plugin.getConfig().getString("bases.rattrapage-avant", "");
        if (avant.isEmpty() || !plugin.db().available() || !plugin.bases().possedees(p.getUniqueId()).isEmpty()) {
            return;
        }
        UUID uuid = p.getUniqueId();
        plugin.db().then(plugin.db().transaction(c -> {
            try (PreparedStatement st = c.prepareStatement(
                    "SELECT 1 FROM survie_kits WHERE uuid = ? AND kit = 'depart' AND pris_le < ?")) {
                st.setString(1, uuid.toString());
                st.setString(2, avant);
                try (ResultSet rs = st.executeQuery()) {
                    if (!rs.next()) {
                        return false;
                    }
                }
            }
            try (PreparedStatement st = c.prepareStatement(
                    "INSERT IGNORE INTO survie_kits (uuid, kit, pris_le) VALUES (?, 'armoire-rattrapage', NOW())")) {
                st.setString(1, uuid.toString());
                return st.executeUpdate() == 1;
            }
        }), donne -> {
            if (donne && p.isOnline()) {
                Objets.donner(p, plugin.bases().objet(1));
                Msg.send(p, "Cadeau : une " + Msg.OR + "armoire de base<gray> ! Pose-la au cœur de ta base pour la protéger "
                        + "(<white>/base<gray> pour tout savoir).");
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f);
            }
        }, null);
    }

    public List<String> ids() {
        return new ArrayList<>(kits.keySet());
    }
}
