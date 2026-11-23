package fr.orbit.survie;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Armoires de base (principe de la TC de Rust) : poser l'armoire protège un cube autour d'elle, sur toute la
 * hauteur, contre tous ceux qui ne sont pas membres. La protection se paie en crédits, par jour, d'avance
 * (coût qui monte avec le niveau). Impayée, la protection saute : n'importe qui peut alors casser la base,
 * armoire comprise. Avertissement 2 jours avant. Nombre d'armoires : 1 / 2 / 3 / 4 selon le grade.
 */
public final class Bases implements Listener, CommandExecutor, TabCompleter {

    private static final long JOUR = 86_400_000L;

    /** Une base : armoire en (x, y, z), cube de rayon selon le niveau. id = -1 tant que la base n'a pas répondu. */
    public static final class Base {
        long id;
        final UUID proprio;
        final String proprioNom;
        final String monde;
        final int x, y, z;
        int niveau;
        long payeJusqua;
        final Map<UUID, String> membres = new LinkedHashMap<>();

        Base(long id, UUID proprio, String proprioNom, String monde, int x, int y, int z, int niveau, long payeJusqua) {
            this.id = id;
            this.proprio = proprio;
            this.proprioNom = proprioNom;
            this.monde = monde;
            this.x = x;
            this.y = y;
            this.z = z;
            this.niveau = niveau;
            this.payeJusqua = payeJusqua;
        }

        public boolean active() {
            return System.currentTimeMillis() < payeJusqua;
        }

        public String proprioNom() {
            return proprioNom;
        }

        String coord() {
            return x + " " + y + " " + z + (monde.endsWith("_nether") ? " (Nether)" : "");
        }
    }

    record Niveau(int rayon, double parJour, double prix) {}

    private final OrbitSurvie plugin;
    private final NamespacedKey cle;
    private final List<Base> toutes = new ArrayList<>();
    private final Map<String, Map<Long, List<Base>>> index = new HashMap<>();
    private final Set<UUID> bypass = new HashSet<>();
    private final Map<Base, Long> averti = new IdentityHashMap<>();

    private Set<String> mondes = Set.of();
    private final List<Niveau> niveaux = new ArrayList<>();
    private final Map<String, Integer> limites = new LinkedHashMap<>();
    private double prixArmoire;
    private int joursOfferts;
    private int joursMax;
    private long avertirMs;
    private int membresMax;

    public Bases(OrbitSurvie plugin) {
        this.plugin = plugin;
        this.cle = new NamespacedKey(plugin, "armoire");
        charger();
    }

    // ------------------------------------------------------------------ Configuration et chargement

    public void charger() {
        ConfigurationSection c = plugin.getConfig().getConfigurationSection("bases");
        if (c == null) {
            c = plugin.getConfig().createSection("bases");
        }
        mondes = new HashSet<>(c.getStringList("mondes").isEmpty() ? List.of("world", "world_nether") : c.getStringList("mondes"));
        prixArmoire = c.getDouble("prix-armoire", 500);
        joursOfferts = c.getInt("jours-offerts", 3);
        joursMax = c.getInt("jours-max", 30);
        avertirMs = c.getLong("avertir-heures", 48) * 3_600_000L;
        membresMax = c.getInt("membres-max", 8);
        limites.clear();
        ConfigurationSection l = c.getConfigurationSection("limites");
        if (l != null) {
            for (String g : l.getKeys(false)) {
                limites.put(g, l.getInt(g));
            }
        }
        if (limites.isEmpty()) {
            limites.putAll(Map.of("joueur", 1, "vip", 2, "vipplus", 3, "orbit", 4));
        }
        niveaux.clear();
        for (Map<?, ?> m : c.getMapList("niveaux")) {
            niveaux.add(new Niveau(nb(m.get("rayon"), 15), nb(m.get("par-jour"), 20), nb(m.get("prix"), 0)));
        }
        if (niveaux.isEmpty()) {
            niveaux.addAll(List.of(new Niveau(15, 20, 0), new Niveau(25, 50, 2000), new Niveau(40, 120, 6000)));
        }
        indexer();
    }

    private static int nb(Object o, int def) {
        return o instanceof Number n ? n.intValue() : def;
    }

    private static double nb(Object o, double def) {
        return o instanceof Number n ? n.doubleValue() : def;
    }

    /** Lit toutes les bases au démarrage (une fois, avant l'arrivée des joueurs). */
    public void lireBase() {
        if (!plugin.db().available()) {
            plugin.getLogger().warning("Bases : base de données injoignable, aucune armoire chargée (pose refusée).");
            return;
        }
        try {
            List<Base> lues = plugin.db().run(c -> {
                Map<Long, Base> parId = new LinkedHashMap<>();
                try (Statement st = c.createStatement();
                     ResultSet rs = st.executeQuery("SELECT id, proprio, proprio_nom, monde, x, y, z, niveau, paye_jusqu_a FROM survie_bases")) {
                    while (rs.next()) {
                        Base b = new Base(rs.getLong(1), UUID.fromString(rs.getString(2)), rs.getString(3), rs.getString(4),
                                rs.getInt(5), rs.getInt(6), rs.getInt(7), rs.getInt(8), rs.getTimestamp(9).getTime());
                        parId.put(b.id, b);
                    }
                }
                try (Statement st = c.createStatement();
                     ResultSet rs = st.executeQuery("SELECT base_id, uuid, nom FROM survie_base_membres")) {
                    while (rs.next()) {
                        Base b = parId.get(rs.getLong(1));
                        if (b != null) {
                            b.membres.put(UUID.fromString(rs.getString(2)), rs.getString(3));
                        }
                    }
                }
                return new ArrayList<>(parId.values());
            }).join();
            toutes.clear();
            toutes.addAll(lues);
            indexer();
            plugin.getLogger().info("Bases : " + toutes.size() + " armoire(s) chargée(s).");
        } catch (Exception e) {
            plugin.getLogger().severe("Bases : lecture impossible (" + e.getMessage() + ").");
        }
    }

    private static long cleChunk(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xffffffffL);
    }

    private void indexer() {
        index.clear();
        for (Base b : toutes) {
            int r = rayon(b);
            Map<Long, List<Base>> m = index.computeIfAbsent(b.monde, k -> new HashMap<>());
            for (int cx = (b.x - r) >> 4; cx <= (b.x + r) >> 4; cx++) {
                for (int cz = (b.z - r) >> 4; cz <= (b.z + r) >> 4; cz++) {
                    m.computeIfAbsent(cleChunk(cx, cz), k -> new ArrayList<>(1)).add(b);
                }
            }
        }
    }

    // ------------------------------------------------------------------ Recherche

    Niveau niveau(Base b) {
        return niveaux.get(Math.max(0, Math.min(niveaux.size() - 1, b.niveau - 1)));
    }

    int rayon(Base b) {
        return niveau(b).rayon();
    }

    /** Base protégée (payée) qui couvre ce point, sinon null. */
    public Base a(Location l) {
        if (l == null || l.getWorld() == null) {
            return null;
        }
        Map<Long, List<Base>> m = index.get(l.getWorld().getName());
        if (m == null) {
            return null;
        }
        List<Base> liste = m.get(cleChunk(l.getBlockX() >> 4, l.getBlockZ() >> 4));
        if (liste == null) {
            return null;
        }
        for (Base b : liste) {
            int r = rayon(b);
            if (Math.abs(l.getBlockX() - b.x) <= r && Math.abs(l.getBlockZ() - b.z) <= r && b.active()) {
                return b;
            }
        }
        return null;
    }

    /** Base dont l'armoire est ce bloc, sinon null. */
    public Base armoire(Block bl) {
        for (Base b : toutes) {
            if (b.x == bl.getX() && b.y == bl.getY() && b.z == bl.getZ() && b.monde.equals(bl.getWorld().getName())) {
                return b;
            }
        }
        return null;
    }

    public boolean peut(Player p, Base b) {
        return b.proprio.equals(p.getUniqueId()) || b.membres.containsKey(p.getUniqueId()) || bypass.contains(p.getUniqueId());
    }

    private Base chevauche(String monde, int x, int z, int r, Base sauf) {
        for (Base b : toutes) {
            if (b != sauf && b.monde.equals(monde)
                    && Math.abs(b.x - x) <= r + rayon(b) && Math.abs(b.z - z) <= r + rayon(b)) {
                return b;
            }
        }
        return null;
    }

    int limite(Player p) {
        int max = limites.getOrDefault("joueur", 1);
        for (Map.Entry<String, Integer> e : limites.entrySet()) {
            if (p.hasPermission("group." + e.getKey())) {
                max = Math.max(max, e.getValue());
            }
        }
        return max;
    }

    List<Base> possedees(UUID u) {
        return toutes.stream().filter(b -> b.proprio.equals(u)).toList();
    }

    /** Seuil de l'avertissement « protection bientôt coupée », en ms. */
    long avertirMs() {
        return avertirMs;
    }

    static String duree(long ms) {
        long min = Math.max(0, (ms + 59_999) / 60_000);
        long j = min / 1440, h = (min % 1440) / 60, m = min % 60;
        return j > 0 ? j + " j " + h + " h" : h > 0 ? h + " h " + m + " min" : m + " min";
    }

    private String etat(Base b) {
        long reste = b.payeJusqua - System.currentTimeMillis();
        if (reste <= 0) {
            return "<red>COUPÉE (impayée)";
        }
        return (reste < avertirMs ? "<yellow>" : "<green>") + "active encore " + duree(reste);
    }

    // ------------------------------------------------------------------ L'objet « Armoire de base »

    public ItemStack objet(int n) {
        ItemStack it = new ItemStack(Material.LODESTONE, Math.max(1, n));
        it.editMeta(meta -> {
            meta.displayName(Msg.item(Msg.OR + "<bold>Armoire de base"));
            meta.lore(List.of(
                    Msg.item("<gray>Pose-la au cœur de ta base : elle protège"),
                    Msg.item("<gray>un carré de <white>" + (2 * niveaux.get(0).rayon() + 1) + " blocs<gray> de côté, sur toute la hauteur."),
                    Msg.item("<gray>Protection payée en crédits, <white>" + joursOfferts + " jours offerts<gray>."),
                    Msg.item("<dark_gray>Clic droit sur l'armoire posée : menu.")));
            meta.setEnchantmentGlintOverride(true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            meta.getPersistentDataContainer().set(cle, PersistentDataType.BYTE, (byte) 1);
        });
        return it;
    }

    public boolean estArmoire(ItemStack it) {
        return it != null && it.getType() == Material.LODESTONE && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(cle, PersistentDataType.BYTE);
    }

    // ------------------------------------------------------------------ Pose, casse, clic

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPose(BlockPlaceEvent e) {
        if (!estArmoire(e.getItemInHand())) {
            return;
        }
        Player p = e.getPlayer();
        Block bl = e.getBlockPlaced();
        String monde = bl.getWorld().getName();
        if (!mondes.contains(monde)) {
            e.setCancelled(true);
            Msg.send(p, "<red>L'armoire de base se pose seulement dans le monde de survie (et le Nether).");
            return;
        }
        if (!plugin.db().available()) {
            e.setCancelled(true);
            Msg.send(p, "<red>Armoires indisponibles : la base de données est injoignable.");
            return;
        }
        int max = limite(p);
        int n = possedees(p.getUniqueId()).size();
        if (n >= max && !p.hasPermission("survie.admin")) {
            e.setCancelled(true);
            Msg.send(p, "<red>Tu as déjà " + n + " armoire(s) sur " + max + " autorisée(s) par ton grade.");
            Msg.send(p, "<gray>Joueur 1 · VIP 2 · VIP+ 3 · ORBIT 4. Casse une de tes armoires pour en poser une autre.");
            return;
        }
        Base autre = chevauche(monde, bl.getX(), bl.getZ(), niveaux.get(0).rayon(), null);
        if (autre != null) {
            e.setCancelled(true);
            Msg.send(p, "<red>Trop près de la base de <white>" + autre.proprioNom
                    + "<red> : les zones protégées ne peuvent pas se toucher.");
            return;
        }
        Base b = new Base(-1, p.getUniqueId(), p.getName(), monde, bl.getX(), bl.getY(), bl.getZ(), 1,
                System.currentTimeMillis() + joursOfferts * JOUR);
        toutes.add(b);
        indexer();
        plugin.db().then(plugin.db().run(c -> {
            try (PreparedStatement st = c.prepareStatement("INSERT INTO survie_bases (proprio, proprio_nom, monde, x, y, z, niveau, paye_jusqu_a) "
                    + "VALUES (?, ?, ?, ?, ?, ?, 1, ?)", Statement.RETURN_GENERATED_KEYS)) {
                st.setString(1, b.proprio.toString());
                st.setString(2, b.proprioNom);
                st.setString(3, monde);
                st.setInt(4, b.x);
                st.setInt(5, b.y);
                st.setInt(6, b.z);
                st.setTimestamp(7, new Timestamp(b.payeJusqua));
                st.executeUpdate();
                try (ResultSet rs = st.getGeneratedKeys()) {
                    rs.next();
                    return rs.getLong(1);
                }
            }
        }), id -> {
            b.id = id;
            if (!toutes.contains(b)) {
                // cassée avant la réponse de la base : on efface aussi la ligne
                supprimer(b);
            }
        }, () -> {
            toutes.remove(b);
            indexer();
            Block ici = Bukkit.getWorld(monde) == null ? null : Bukkit.getWorld(monde).getBlockAt(b.x, b.y, b.z);
            if (ici != null && ici.getType() == Material.LODESTONE) {
                ici.setType(Material.AIR);
            }
            if (p.isOnline()) {
                Objets.donner(p, objet(1));
                Msg.send(p, "<red>Armoire non enregistrée (base de données) : elle t'est rendue.");
            }
        });
        int r = niveaux.get(0).rayon();
        Msg.send(p, Msg.OR + "<bold>Armoire posée !<gray> Ta base est protégée sur <white>" + (2 * r + 1) + " × " + (2 * r + 1)
                + "<gray> blocs, toute la hauteur.");
        Msg.send(p, "<white>" + joursOfferts + " jours offerts<gray>, ensuite <white>" + Msg.argent(niveaux.get(0).parJour())
                + "<gray> par jour. Clic droit sur l'armoire : payer, améliorer, membres.");
        Msg.send(p, "<gray>Ajoute tes coéquipiers : <white>/base ajouter <pseudo>");
        p.playSound(bl.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.8f, 1.2f);
        bordure(p, b);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCasse(BlockBreakEvent e) {
        Base b = armoire(e.getBlock());
        if (b == null) {
            return;
        }
        Player p = e.getPlayer();
        boolean proprio = b.proprio.equals(p.getUniqueId()) || bypass.contains(p.getUniqueId());
        if (b.active() && !proprio) {
            e.setCancelled(true);
            Msg.send(p, "<red>Seul " + b.proprioNom + " peut retirer cette armoire.");
            return;
        }
        e.setDropItems(false);
        e.setExpToDrop(0);
        if (proprio) {
            Objets.donner(p, objet(1));
            Msg.send(p, "Armoire retirée : la base n'est plus protégée. L'armoire est dans ton inventaire.");
        } else {
            e.getBlock().getWorld().dropItemNaturally(e.getBlock().getLocation().add(0.5, 0.5, 0.5), objet(1));
            Msg.send(p, "Tu as détruit l'armoire abandonnée de <white>" + b.proprioNom + "<gray>.");
            Player proprioEnLigne = Bukkit.getPlayer(b.proprio);
            if (proprioEnLigne != null) {
                Msg.send(proprioEnLigne, "<red><bold>Ton armoire en " + b.coord() + " a été détruite</bold><red> (protection impayée).");
            }
        }
        supprimer(b);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClic(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND || e.getClickedBlock() == null) {
            return;
        }
        Base b = armoire(e.getClickedBlock());
        if (b == null) {
            return;
        }
        Player p = e.getPlayer();
        if (p.isSneaking() && e.getItem() != null && e.getItem().getType().isBlock()) {
            return; // pose de bloc contre l'armoire
        }
        e.setCancelled(true);
        if (peut(p, b)) {
            menu(p, b);
        } else {
            Msg.send(p, "Armoire de <white>" + b.proprioNom + "<gray> — protection " + (b.active() ? "<green>active" : "<red>coupée<gray> : la base peut être cassée") + "<gray>.");
        }
    }

    private void supprimer(Base b) {
        toutes.remove(b);
        averti.remove(b);
        indexer();
        if (b.id < 0) {
            return; // la ligne sera effacée à la réponse de l'INSERT
        }
        long id = b.id;
        plugin.db().run(c -> {
            try (PreparedStatement st = c.prepareStatement("DELETE FROM survie_base_membres WHERE base_id = ?")) {
                st.setLong(1, id);
                st.executeUpdate();
            }
            try (PreparedStatement st = c.prepareStatement("DELETE FROM survie_bases WHERE id = ?")) {
                st.setLong(1, id);
                st.executeUpdate();
            }
            return null;
        });
    }

    // ------------------------------------------------------------------ Menu de l'armoire

    private void menu(Player p, Base b) {
        Niveau nv = niveau(b);
        int r = nv.rayon();
        Menu m = new Menu(3, Msg.OR + "<bold>Armoire de base");
        List<String> noms = new ArrayList<>(b.membres.values());
        m.set(4, Menu.icone(Material.LODESTONE, Msg.OR + "<bold>Base de " + b.proprioNom,
                "<gray>Position : <white>" + b.coord(),
                "<gray>Niveau <white>" + b.niveau + "<gray> · zone <white>" + (2 * r + 1) + " × " + (2 * r + 1) + "<gray> blocs",
                "<gray>Protection : " + etat(b),
                "<gray>Coût : <white>" + Msg.argent(nv.parJour()) + "<gray> par jour",
                "<gray>Membres : <white>" + (noms.isEmpty() ? "aucun" : String.join(", ", noms))), null);
        m.set(10, Menu.icone(Material.GOLD_NUGGET, "<green><bold>Payer 1 jour",
                "<gray>Prix : <white>" + Msg.argent(nv.parJour()),
                "<dark_gray>N'importe quel membre peut payer."), (pl, c) -> {
            pl.closeInventory();
            payer(pl, b, 1);
        });
        m.set(11, Menu.icone(Material.GOLD_INGOT, "<green><bold>Payer 7 jours",
                "<gray>Prix : <white>" + Msg.argent(nv.parJour() * 7),
                "<dark_gray>Maximum " + joursMax + " jours d'avance."), (pl, c) -> {
            pl.closeInventory();
            payer(pl, b, 7);
        });
        if (b.niveau < niveaux.size()) {
            Niveau suiv = niveaux.get(b.niveau);
            m.set(13, Menu.icone(Material.NETHER_STAR, Msg.OR + "<bold>Améliorer au niveau " + (b.niveau + 1),
                    "<gray>Zone : <white>" + (2 * suiv.rayon() + 1) + " × " + (2 * suiv.rayon() + 1) + "<gray> blocs",
                    "<gray>Prix : <white>" + Msg.argent(suiv.prix()),
                    "<gray>Ensuite : <white>" + Msg.argent(suiv.parJour()) + "<gray> par jour",
                    "<dark_gray>Le temps payé est converti au nouveau prix.",
                    b.proprio.equals(p.getUniqueId()) ? "<green>Clic pour améliorer" : "<red>Réservé au propriétaire"), (pl, c) -> {
                pl.closeInventory();
                ameliorer(pl, b);
            });
        } else {
            m.set(13, Menu.icone(Material.NETHER_STAR, Msg.OR + "<bold>Niveau maximum", "<gray>Ta base est au plus haut niveau."), null);
        }
        boolean proprio = b.proprio.equals(p.getUniqueId()) || bypass.contains(p.getUniqueId());
        m.set(15, Menu.icone(Material.PLAYER_HEAD, Msg.BLEU + "<bold>Membres (" + b.membres.size() + "/" + membresMax + ")",
                "<gray>Ajouter : <white>/base ajouter <pseudo>",
                proprio ? "<green>Clic pour gérer" : "<dark_gray>Géré par " + b.proprioNom), (pl, c) -> {
            if (proprio) {
                membres(pl, b);
            }
        });
        m.set(16, Menu.icone(Material.ENDER_EYE, Msg.BLEU + "<bold>Voir la zone", "<gray>Montre les bords pendant 10 s."), (pl, c) -> {
            pl.closeInventory();
            bordure(pl, b);
        });
        m.open(p);
    }

    private void membres(Player p, Base b) {
        Menu m = new Menu(3, Msg.BLEU + "<bold>Membres de la base");
        int slot = 0;
        for (Map.Entry<UUID, String> e : b.membres.entrySet()) {
            if (slot >= 18) {
                break;
            }
            m.set(slot++, Menu.icone(Material.PLAYER_HEAD, "<white>" + e.getValue(), "<red>Clic : retirer de la base"), (pl, c) -> {
                retirerMembre(pl, b, e.getValue());
                membres(pl, b);
            });
        }
        m.set(22, Menu.icone(Material.ARROW, "<gray>Retour"), (pl, c) -> menu(pl, b));
        m.open(p);
    }

    private void bordure(Player p, Base b) {
        World w = Bukkit.getWorld(b.monde);
        if (w == null || !p.getWorld().equals(w)) {
            return;
        }
        int r = rayon(b);
        double x0 = b.x - r, x1 = b.x + r + 1, z0 = b.z - r, z1 = b.z + r + 1;
        Particle.DustOptions or = new Particle.DustOptions(Color.fromRGB(245, 197, 66), 1.6f);
        new BukkitRunnable() {
            int n;

            @Override
            public void run() {
                if (n++ >= 20 || !p.isOnline() || !p.getWorld().equals(w)) {
                    cancel();
                    return;
                }
                double y = p.getLocation().getY() + 1;
                for (double t = 0; t <= x1 - x0; t += 1.5) {
                    for (double dy : new double[]{-1, 0.5, 2}) {
                        p.spawnParticle(Particle.DUST, x0 + t, y + dy, z0, 1, 0, 0, 0, 0, or);
                        p.spawnParticle(Particle.DUST, x0 + t, y + dy, z1, 1, 0, 0, 0, 0, or);
                        p.spawnParticle(Particle.DUST, x0, y + dy, z0 + t, 1, 0, 0, 0, 0, or);
                        p.spawnParticle(Particle.DUST, x1, y + dy, z0 + t, 1, 0, 0, 0, 0, or);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    // ------------------------------------------------------------------ Paiement, amélioration, membres

    private void payer(Player p, Base b, int jours) {
        if (b.id < 0) {
            Msg.send(p, "<red>Armoire en cours d'enregistrement, réessaie dans un instant.");
            return;
        }
        double cout = niveau(b).parJour() * jours;
        int niveau = b.niveau;
        UUID u = p.getUniqueId();
        long id = b.id;
        long max = joursMax * JOUR;
        plugin.db().then(plugin.db().transaction(c -> {
            long actuel;
            try (PreparedStatement st = c.prepareStatement("SELECT paye_jusqu_a, niveau FROM survie_bases WHERE id = ? FOR UPDATE")) {
                st.setLong(1, id);
                try (ResultSet rs = st.executeQuery()) {
                    if (!rs.next() || rs.getInt(2) != niveau) {
                        return -2L;
                    }
                    actuel = rs.getTimestamp(1).getTime();
                }
            }
            long now = System.currentTimeMillis();
            long nouveau = Math.max(now, actuel) + jours * JOUR;
            if (nouveau - now > max + 60_000L) {
                return -3L;
            }
            if (!Economie.retirer(c, u, cout)) {
                return -1L;
            }
            try (PreparedStatement st = c.prepareStatement("UPDATE survie_bases SET paye_jusqu_a = ? WHERE id = ?")) {
                st.setTimestamp(1, new Timestamp(nouveau));
                st.setLong(2, id);
                st.executeUpdate();
            }
            return nouveau;
        }), r -> {
            if (r == -1L) {
                Msg.send(p, "<red>Pas assez de crédits : il faut " + Msg.argent(cout) + "<red>.");
            } else if (r == -3L) {
                Msg.send(p, "<red>On ne peut pas payer plus de " + joursMax + " jours d'avance.");
            } else if (r < 0) {
                Msg.send(p, "<red>Cette armoire a changé entre-temps, rouvre le menu.");
            } else {
                b.payeJusqua = r;
                averti.remove(b);
                Msg.send(p, "Payé " + Msg.argent(cout) + " : protection " + etat(b) + "<gray>.");
                p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f);
            }
        }, () -> Msg.send(p, "<red>Paiement impossible pour le moment (base de données)."));
    }

    private void ameliorer(Player p, Base b) {
        if (!b.proprio.equals(p.getUniqueId()) && !bypass.contains(p.getUniqueId())) {
            Msg.send(p, "<red>Seul " + b.proprioNom + " peut améliorer cette armoire.");
            return;
        }
        if (b.id < 0 || b.niveau >= niveaux.size()) {
            return;
        }
        Niveau avant = niveau(b);
        Niveau apres = niveaux.get(b.niveau);
        Base gene = chevauche(b.monde, b.x, b.z, apres.rayon(), b);
        if (gene != null) {
            Msg.send(p, "<red>Impossible : la zone agrandie toucherait la base de <white>" + gene.proprioNom + "<red>.");
            return;
        }
        int niveau = b.niveau;
        UUID u = p.getUniqueId();
        long id = b.id;
        double ratio = avant.parJour() / apres.parJour();
        plugin.db().then(plugin.db().transaction(c -> {
            long actuel;
            try (PreparedStatement st = c.prepareStatement("SELECT paye_jusqu_a, niveau FROM survie_bases WHERE id = ? FOR UPDATE")) {
                st.setLong(1, id);
                try (ResultSet rs = st.executeQuery()) {
                    if (!rs.next() || rs.getInt(2) != niveau) {
                        return -2L;
                    }
                    actuel = rs.getTimestamp(1).getTime();
                }
            }
            if (!Economie.retirer(c, u, apres.prix())) {
                return -1L;
            }
            long now = System.currentTimeMillis();
            long nouveau = now + (long) (Math.max(0, actuel - now) * ratio);
            try (PreparedStatement st = c.prepareStatement("UPDATE survie_bases SET niveau = ?, paye_jusqu_a = ? WHERE id = ?")) {
                st.setInt(1, niveau + 1);
                st.setTimestamp(2, new Timestamp(nouveau));
                st.setLong(3, id);
                st.executeUpdate();
            }
            return nouveau;
        }), r -> {
            if (r == -1L) {
                Msg.send(p, "<red>Pas assez de crédits : il faut " + Msg.argent(apres.prix()) + "<red>.");
            } else if (r < 0) {
                Msg.send(p, "<red>Cette armoire a changé entre-temps, rouvre le menu.");
            } else {
                b.niveau = niveau + 1;
                b.payeJusqua = r;
                averti.remove(b);
                indexer();
                Msg.send(p, Msg.OR + "<bold>Base améliorée au niveau " + b.niveau + "<gray> : zone de <white>"
                        + (2 * apres.rayon() + 1) + " × " + (2 * apres.rayon() + 1) + "<gray> blocs. Protection " + etat(b) + "<gray>.");
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.2f);
                bordure(p, b);
            }
        }, () -> Msg.send(p, "<red>Amélioration impossible pour le moment (base de données)."));
    }

    private void ajouterMembre(Player p, Base b, String pseudo) {
        Player cible = Bukkit.getPlayerExact(pseudo);
        if (cible == null) {
            Msg.send(p, "<red>" + pseudo + " doit être connecté pour être ajouté.");
            return;
        }
        if (cible.getUniqueId().equals(b.proprio) || b.membres.containsKey(cible.getUniqueId())) {
            Msg.send(p, "<red>" + cible.getName() + " a déjà accès à cette base.");
            return;
        }
        if (b.membres.size() >= membresMax) {
            Msg.send(p, "<red>Maximum " + membresMax + " membres par base.");
            return;
        }
        if (b.id < 0) {
            Msg.send(p, "<red>Armoire en cours d'enregistrement, réessaie dans un instant.");
            return;
        }
        long id = b.id;
        UUID cu = cible.getUniqueId();
        String nom = cible.getName();
        plugin.db().then(plugin.db().run(c -> {
            try (PreparedStatement st = c.prepareStatement("INSERT IGNORE INTO survie_base_membres (base_id, uuid, nom) VALUES (?, ?, ?)")) {
                st.setLong(1, id);
                st.setString(2, cu.toString());
                st.setString(3, nom);
                st.executeUpdate();
            }
            return null;
        }), v -> {
            b.membres.put(cu, nom);
            Msg.send(p, "<white>" + nom + "<gray> est maintenant membre de ta base (" + b.coord() + ").");
            Player c = Bukkit.getPlayer(cu);
            if (c != null) {
                Msg.send(c, "<white>" + b.proprioNom + "<gray> t'a ajouté à sa base en <white>" + b.coord() + "<gray>.");
            }
        }, () -> Msg.send(p, "<red>Ajout impossible pour le moment (base de données)."));
    }

    private void retirerMembre(Player p, Base b, String pseudo) {
        UUID cu = null;
        for (Map.Entry<UUID, String> e : b.membres.entrySet()) {
            if (e.getValue().equalsIgnoreCase(pseudo)) {
                cu = e.getKey();
            }
        }
        if (cu == null) {
            Msg.send(p, "<red>" + pseudo + " n'est pas membre de cette base.");
            return;
        }
        String nom = b.membres.remove(cu);
        long id = b.id;
        UUID retire = cu;
        plugin.db().run(c -> {
            try (PreparedStatement st = c.prepareStatement("DELETE FROM survie_base_membres WHERE base_id = ? AND uuid = ?")) {
                st.setLong(1, id);
                st.setString(2, retire.toString());
                st.executeUpdate();
            }
            return null;
        });
        Msg.send(p, "<white>" + nom + "<gray> n'a plus accès à ta base.");
    }

    // ------------------------------------------------------------------ Avertissements

    /** Toutes les 10 min : prévient les membres en ligne des bases qui arrivent à échéance ou viennent de sauter. */
    public void surveiller() {
        long now = System.currentTimeMillis();
        for (Base b : toutes) {
            long reste = b.payeJusqua - now;
            Long dernier = averti.get(b);
            if (reste > 0 && reste < avertirMs && (dernier == null || now - dernier > 3 * 3_600_000L)) {
                if (prevenir(b, "<yellow>⚠ La protection de la base de " + b.proprioNom + " (" + b.coord() + ") saute dans <white>"
                        + duree(reste) + "<yellow>. Paie à l'armoire !")) {
                    averti.put(b, now);
                }
            } else if (reste <= 0 && reste > -15 * 60_000L && (dernier == null || dernier < b.payeJusqua)) {
                if (prevenir(b, "<red><bold>⚠ La protection de la base de " + b.proprioNom + " (" + b.coord()
                        + ") a sauté</bold><red> : n'importe qui peut la casser. Paie à l'armoire pour la remettre.")) {
                    averti.put(b, now);
                }
            }
        }
    }

    private boolean prevenir(Base b, String msg) {
        boolean quelquun = false;
        Set<UUID> qui = new HashSet<>(b.membres.keySet());
        qui.add(b.proprio);
        for (UUID u : qui) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) {
                Msg.send(p, msg);
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 0.8f);
                quelquun = true;
            }
        }
        return quelquun;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) {
                return;
            }
            long now = System.currentTimeMillis();
            for (Base b : toutes) {
                if (!b.proprio.equals(p.getUniqueId()) && !b.membres.containsKey(p.getUniqueId())) {
                    continue;
                }
                long reste = b.payeJusqua - now;
                if (reste <= 0) {
                    Msg.send(p, "<red>⚠ La base de " + b.proprioNom + " (" + b.coord() + ") n'est plus protégée : paie à l'armoire !");
                } else if (reste < avertirMs) {
                    Msg.send(p, "<yellow>⚠ La protection de la base de " + b.proprioNom + " (" + b.coord() + ") saute dans <white>"
                            + duree(reste) + "<yellow>.");
                }
            }
        }, 80L);
    }

    // ------------------------------------------------------------------ /base

    /** Base visée par une commande du propriétaire : celle où il se trouve, sinon sa seule base. */
    private Base cible(Player p) {
        Location l = p.getLocation();
        List<Base> miennes = possedees(p.getUniqueId());
        for (Base b : miennes) {
            int r = rayon(b);
            if (b.monde.equals(l.getWorld().getName()) && Math.abs(l.getBlockX() - b.x) <= r && Math.abs(l.getBlockZ() - b.z) <= r) {
                return b;
            }
        }
        if (miennes.size() == 1) {
            return miennes.get(0);
        }
        Msg.send(p, miennes.isEmpty() ? "<red>Tu n'as pas d'armoire de base." : "<red>Tu as plusieurs bases : va dans celle à modifier.");
        return null;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        String sous = a.length == 0 ? "" : a[0].toLowerCase(Locale.ROOT);
        if (sous.equals("donner") && s.hasPermission("survie.admin")) {
            Player cible = a.length >= 2 ? Bukkit.getPlayerExact(a[1]) : (s instanceof Player pl ? pl : null);
            if (cible == null) {
                Msg.send(s, "<red>Usage : /base donner <pseudo> [nombre]");
                return true;
            }
            int n = a.length >= 3 ? Math.max(1, Math.min(64, parse(a[2]))) : 1;
            Objets.donner(cible, objet(n));
            Msg.send(s, n + " armoire(s) donnée(s) à <white>" + cible.getName() + "<gray>.");
            return true;
        }
        if (!(s instanceof Player p)) {
            return true;
        }
        switch (sous) {
            case "acheter" -> {
                double prix = prixArmoire;
                plugin.db().then(plugin.eco().retirer(p.getUniqueId(), prix), ok -> {
                    if (!ok) {
                        Msg.send(p, "<red>Il faut " + Msg.argent(prix) + "<red> pour acheter une armoire.");
                        return;
                    }
                    Objets.donner(p, objet(1));
                    Msg.send(p, "Armoire de base achetée pour " + Msg.argent(prix) + ". Pose-la au cœur de ta base !");
                }, () -> Msg.send(p, "<red>Achat impossible pour le moment (base de données)."));
            }
            case "ajouter" -> {
                if (a.length < 2) {
                    Msg.send(p, "<red>Usage : /base ajouter <pseudo>");
                    return true;
                }
                Base b = cible(p);
                if (b != null) {
                    ajouterMembre(p, b, a[1]);
                }
            }
            case "retirer" -> {
                if (a.length < 2) {
                    Msg.send(p, "<red>Usage : /base retirer <pseudo>");
                    return true;
                }
                Base b = cible(p);
                if (b != null) {
                    retirerMembre(p, b, a[1]);
                }
            }
            case "bypass" -> {
                if (!p.hasPermission("survie.admin")) {
                    return true;
                }
                if (bypass.remove(p.getUniqueId())) {
                    Msg.send(p, "Contournement des bases <red>désactivé<gray>.");
                } else {
                    bypass.add(p.getUniqueId());
                    Msg.send(p, "Contournement des bases <green>activé<gray> : tu peux tout casser et ouvrir.");
                }
            }
            case "zone" -> {
                Base b = a(p.getLocation());
                if (b == null) {
                    b = cible(p);
                }
                if (b != null) {
                    bordure(p, b);
                }
            }
            default -> liste(p);
        }
        return true;
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private void liste(Player p) {
        Msg.send(p, Msg.OR + "<bold>Armoires de base<gray> — " + possedees(p.getUniqueId()).size() + " / " + limite(p)
                + " (Joueur 1 · VIP 2 · VIP+ 3 · ORBIT 4)");
        boolean rien = true;
        for (Base b : toutes) {
            boolean proprio = b.proprio.equals(p.getUniqueId());
            if (!proprio && !b.membres.containsKey(p.getUniqueId())) {
                continue;
            }
            rien = false;
            Msg.send(p, "<dark_gray>• <white>" + b.coord() + "<gray> — " + (proprio ? "à toi" : "base de " + b.proprioNom)
                    + ", niveau " + b.niveau + ", " + etat(b));
        }
        if (rien) {
            Msg.send(p, "<gray>Aucune base. Une armoire est dans le kit de départ ; sinon <white>/base acheter<gray> ("
                    + Msg.argent(prixArmoire) + ").");
        }
        Msg.send(p, "<white>/base acheter<gray> · <white>/base ajouter <pseudo><gray> · <white>/base retirer <pseudo><gray> · <white>/base zone");
        Msg.send(p, "<gray>Paiement et amélioration : clic droit sur l'armoire. Impayée, la protection saute (avertissement 2 j avant).");
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            out.addAll(List.of("acheter", "ajouter", "retirer", "zone"));
            if (s.hasPermission("survie.admin")) {
                out.addAll(List.of("bypass", "donner"));
            }
        } else if (a.length == 2 && (a[0].equalsIgnoreCase("ajouter") || a[0].equalsIgnoreCase("donner"))) {
            Bukkit.getOnlinePlayers().forEach(pl -> out.add(pl.getName()));
        } else if (a.length == 2 && a[0].equalsIgnoreCase("retirer") && s instanceof Player p) {
            possedees(p.getUniqueId()).forEach(b -> out.addAll(b.membres.values()));
        }
        String debut = a.length == 0 ? "" : a[a.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(debut));
        return out;
    }
}
