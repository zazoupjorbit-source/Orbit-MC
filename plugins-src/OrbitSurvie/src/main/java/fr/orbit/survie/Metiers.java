package fr.orbit.survie;

import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Métiers : Mineur, Bûcheron, Fermier, Pêcheur, Chasseur. Tout le monde les a tous ; chacun monte en jouant
 * (minerais cassés, bûches, récoltes mûres, prises à la ligne, monstres tués) dans le monde de survie.
 * Chaque action donne de l'XP et une petite paie en crédits, qui grandit avec le niveau ; certains niveaux
 * donnent une prime. L'XP est en base (survie_metiers), gardée en mémoire tant que le joueur est connecté.
 */
public final class Metiers implements Listener {

    public enum Metier {
        MINEUR("Mineur", Material.IRON_PICKAXE, "Casse des minerais."),
        BUCHERON("Bûcheron", Material.IRON_AXE, "Coupe des bûches."),
        FERMIER("Fermier", Material.IRON_HOE, "Récolte des cultures mûres."),
        PECHEUR("Pêcheur", Material.FISHING_ROD, "Pêche à la ligne."),
        CHASSEUR("Chasseur", Material.IRON_SWORD, "Tue des monstres.");

        final String nom;
        final Material icone;
        final String comment;

        Metier(String nom, Material icone, String comment) {
            this.nom = nom;
            this.icone = icone;
            this.comment = comment;
        }

        String cle() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Niveau, XP déjà faite dans ce niveau, XP qu'il faut pour le suivant (0 au niveau maximum). */
    record Etat(int niveau, long dans, long besoin) {}

    private static final class Fiche {
        final EnumMap<Metier, Long> xp = new EnumMap<>(Metier.class);
        String nom;
        boolean chargee;
        boolean modifiee;
        double paie;
    }

    private record Pos(UUID monde, long cle) {}

    private static final Set<Material> POISSONS = EnumSet.of(Material.COD, Material.SALMON, Material.TROPICAL_FISH, Material.PUFFERFISH);
    private static final Set<Material> DECHETS = EnumSet.of(Material.LILY_PAD, Material.BOWL, Material.LEATHER, Material.LEATHER_BOOTS,
            Material.ROTTEN_FLESH, Material.STICK, Material.STRING, Material.POTION, Material.BONE, Material.INK_SAC,
            Material.TRIPWIRE_HOOK, Material.BAMBOO);
    /** Plantes « âgées » par le jeu sans que ça veuille dire mûres : elles comptent à la casse, sauf si on vient de les poser. */
    private static final Set<Material> SANS_MATURITE = EnumSet.of(Material.SUGAR_CANE, Material.CACTUS, Material.BAMBOO,
            Material.KELP, Material.KELP_PLANT);
    private static final int POSES_MAX = 50_000;

    private final OrbitSurvie plugin;
    private final Map<UUID, Fiche> fiches = new HashMap<>();
    private final Map<Metier, Map<String, Integer>> baremes = new EnumMap<>(Metier.class);
    private final Map<Metier, Map<Material, Integer>> cacheBlocs = new EnumMap<>(Metier.class);
    private final TreeMap<Integer, Double> paliers = new TreeMap<>();
    /** Blocs posés par des joueurs (les derniers) : les recasser ne rapporte rien. */
    private final LinkedHashSet<Pos> poses = new LinkedHashSet<>();

    private int niveauMax;
    private double xpBase;
    private double xpExposant;
    private double paieParXp;
    private double bonusNiveau;
    private int paieMinutes;
    private int minutes;

    public Metiers(OrbitSurvie plugin) {
        this.plugin = plugin;
        charger();
    }

    public void charger() {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("metiers");
        niveauMax = s == null ? 50 : Math.max(2, s.getInt("niveau-max", 50));
        xpBase = s == null ? 100 : s.getDouble("xp-base", 100);
        xpExposant = s == null ? 1.5 : s.getDouble("xp-exposant", 1.5);
        paieParXp = s == null ? 0.15 : s.getDouble("paie-par-xp", 0.15);
        bonusNiveau = s == null ? 2 : s.getDouble("bonus-par-niveau", 2);
        paieMinutes = s == null ? 5 : Math.max(1, s.getInt("paie-minutes", 5));
        paliers.clear();
        baremes.clear();
        cacheBlocs.clear();
        ConfigurationSection p = s == null ? null : s.getConfigurationSection("paliers");
        if (p != null) {
            for (String k : p.getKeys(false)) {
                try {
                    paliers.put(Integer.parseInt(k), p.getDouble(k));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        for (Metier m : Metier.values()) {
            Map<String, Integer> b = new LinkedHashMap<>();
            ConfigurationSection bs = s == null ? null : s.getConfigurationSection("baremes." + m.cle());
            if (bs != null) {
                for (String k : bs.getKeys(false)) {
                    b.put(k.toUpperCase(Locale.ROOT), bs.getInt(k));
                }
            }
            baremes.put(m, b);
            cacheBlocs.put(m, new HashMap<>());
        }
    }

    // ------------------------------------------------------------------ Niveaux

    private long besoin(int niveau) {
        return Math.max(1, Math.round(xpBase * Math.pow(niveau, xpExposant)));
    }

    Etat etat(long xp) {
        int n = 1;
        while (n < niveauMax) {
            long b = besoin(n);
            if (xp < b) {
                return new Etat(n, xp, b);
            }
            xp -= b;
            n++;
        }
        return new Etat(niveauMax, 0, 0);
    }

    /** XP d'un bloc pour un métier (0 = ce bloc ne compte pas). Nom exact d'abord, puis les motifs avec *. */
    private int xpBloc(Metier m, Material type) {
        return cacheBlocs.get(m).computeIfAbsent(type, t -> {
            String nom = t.name();
            if (m == Metier.MINEUR && nom.startsWith("DEEPSLATE_") && nom.endsWith("_ORE")) {
                nom = nom.substring("DEEPSLATE_".length());
            }
            Map<String, Integer> b = baremes.get(m);
            Integer exact = b.get(nom);
            if (exact != null) {
                return exact;
            }
            for (Map.Entry<String, Integer> e : b.entrySet()) {
                if (e.getKey().indexOf('*') >= 0 && Marche.glob(nom, e.getKey())) {
                    return e.getValue();
                }
            }
            return 0;
        });
    }

    private boolean compte(Player p) {
        return p.getGameMode() == GameMode.SURVIVAL && !p.getWorld().getName().equals(plugin.ile().worldName());
    }

    /** Donne de l'XP de métier (et la paie qui va avec). */
    public void gagner(Player p, Metier m, int xp) {
        if (xp <= 0) {
            return;
        }
        Fiche f = fiches.computeIfAbsent(p.getUniqueId(), k -> new Fiche());
        f.nom = p.getName();
        long avant = f.xp.getOrDefault(m, 0L);
        f.xp.put(m, avant + xp);
        f.modifiee = true;
        // Tant que la fiche n'est pas lue en base, on ne connaît pas le vrai niveau : paie de base, pas de montée.
        Etat e0 = f.chargee ? etat(avant) : new Etat(1, 0, 0);
        Etat e1 = f.chargee ? etat(avant + xp) : e0;
        f.paie += xp * paieParXp * (1 + bonusNiveau * (e0.niveau() - 1) / 100.0);
        plugin.stats().ajouter(p.getUniqueId(), p.getName(), "xp", xp);
        p.sendActionBar(Msg.raw(Msg.BLEU + m.nom + " <white>+" + xp + " XP <dark_gray>· <gray>niveau <white>" + e1.niveau()
                + (e1.besoin() > 0 ? " <dark_gray>(" + e1.dans() + "/" + e1.besoin() + ")" : " <dark_gray>(max)")));
        for (int n = e0.niveau() + 1; n <= e1.niveau(); n++) {
            monter(p, m, n);
        }
    }

    private void monter(Player p, Metier m, int niveau) {
        p.showTitle(Title.title(Msg.raw(Msg.OR + "<bold>" + m.nom + " niveau " + niveau),
                Msg.raw("<gray>Paie des métiers : <white>+" + Msg.nombre(bonusNiveau * (niveau - 1)) + " %"),
                Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(500))));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1f);
        Double prime = paliers.get(niveau);
        if (prime != null && prime > 0 && plugin.eco().active()) {
            plugin.eco().crediter(p.getUniqueId(), p.getName(), prime);
            Msg.send(p, "Palier de métier : " + Msg.argent(prime) + " de prime !");
            Bukkit.broadcast(Msg.msg("<white>" + p.getName() + "<gray> passe " + Msg.OR + m.nom + " niveau " + niveau + "<gray> !"));
        }
    }

    // ------------------------------------------------------------------ Ce qui rapporte

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPose(BlockPlaceEvent e) {
        Material t = e.getBlockPlaced().getType();
        if (xpBloc(Metier.MINEUR, t) > 0 || xpBloc(Metier.BUCHERON, t) > 0
                || (xpBloc(Metier.FERMIER, t) > 0 && !(e.getBlockPlaced().getBlockData() instanceof Ageable && !SANS_MATURITE.contains(t)))) {
            poses.add(new Pos(e.getBlockPlaced().getWorld().getUID(), e.getBlockPlaced().getBlockKey()));
            if (poses.size() > POSES_MAX) {
                Iterator<Pos> it = poses.iterator();
                it.next();
                it.remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCasse(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Block b = e.getBlock();
        boolean pose = poses.remove(new Pos(b.getWorld().getUID(), b.getBlockKey()));
        if (pose || !compte(p)) {
            return;
        }
        Material t = b.getType();
        int xp = xpBloc(Metier.MINEUR, t);
        if (xp > 0) {
            // Toucher de soie : le minerai repart entier, il sera compté quand il sera vraiment miné.
            if (!p.getInventory().getItemInMainHand().containsEnchantment(Enchantment.SILK_TOUCH)) {
                gagner(p, Metier.MINEUR, xp);
            }
            return;
        }
        xp = xpBloc(Metier.BUCHERON, t);
        if (xp > 0) {
            gagner(p, Metier.BUCHERON, xp);
            return;
        }
        xp = xpBloc(Metier.FERMIER, t);
        if (xp > 0) {
            if (b.getBlockData() instanceof Ageable a && !SANS_MATURITE.contains(t) && a.getAge() < a.getMaximumAge()) {
                return; // pas mûr
            }
            gagner(p, Metier.FERMIER, xp);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPeche(PlayerFishEvent e) {
        if (e.getState() != PlayerFishEvent.State.CAUGHT_FISH || !(e.getCaught() instanceof Item prise) || !compte(e.getPlayer())) {
            return;
        }
        Player p = e.getPlayer();
        Material t = prise.getItemStack().getType();
        Map<String, Integer> b = baremes.get(Metier.PECHEUR);
        boolean poisson = POISSONS.contains(t);
        gagner(p, Metier.PECHEUR, b.getOrDefault(poisson ? "POISSON" : DECHETS.contains(t) ? "DECHET" : "TRESOR", poisson ? 12 : 4));
        if (poisson) {
            plugin.stats().ajouter(p.getUniqueId(), p.getName(), "poissons", 1);
            if (plugin.evenements() != null) {
                plugin.evenements().poisson(p);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMort(EntityDeathEvent e) {
        LivingEntity mort = e.getEntity();
        Player p = mort.getKiller();
        if (p == null || mort instanceof Player || !compte(p) || mort.fromMobSpawner()) {
            return;
        }
        Map<String, Integer> b = baremes.get(Metier.CHASSEUR);
        boolean monstre = mort instanceof Enemy;
        Integer xp = b.get(mort.getType().name());
        if (xp == null) {
            xp = b.getOrDefault(monstre ? "DEFAUT" : "ANIMAL", monstre ? 6 : 0);
        }
        gagner(p, Metier.CHASSEUR, xp);
        if (monstre) {
            plugin.stats().ajouter(p.getUniqueId(), p.getName(), "monstres", 1);
        }
    }

    // ------------------------------------------------------------------ Base de données

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        lire(e.getPlayer());
    }

    public void lire(Player p) {
        if (!plugin.db().available()) {
            return;
        }
        UUID uuid = p.getUniqueId();
        plugin.db().then(plugin.db().run(c -> {
            Map<String, Long> lu = new HashMap<>();
            try (PreparedStatement st = c.prepareStatement("SELECT metier, xp FROM survie_metiers WHERE uuid = ?")) {
                st.setString(1, uuid.toString());
                try (ResultSet rs = st.executeQuery()) {
                    while (rs.next()) {
                        lu.put(rs.getString(1), rs.getLong(2));
                    }
                }
            }
            return lu;
        }), lu -> {
            if (!p.isOnline()) {
                return;
            }
            Fiche f = fiches.computeIfAbsent(uuid, k -> new Fiche());
            if (f.chargee) {
                return;
            }
            f.nom = p.getName();
            for (Metier m : Metier.values()) {
                Long v = lu.get(m.cle());
                if (v != null) {
                    f.xp.merge(m, v, Long::sum); // ce qui a été gagné avant la fin de la lecture s'ajoute
                }
            }
            f.chargee = true;
        }, null);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID uuid = e.getPlayer().getUniqueId();
        Fiche f = fiches.remove(uuid);
        if (f != null) {
            enregistrer(uuid, f);
            payer(uuid, f, null);
        }
    }

    /** Toutes les minutes : XP en base ; toutes les « paie-minutes » : la paie. */
    public void tic() {
        minutes++;
        boolean paie = minutes % paieMinutes == 0;
        for (Map.Entry<UUID, Fiche> e : fiches.entrySet()) {
            enregistrer(e.getKey(), e.getValue());
            if (paie) {
                payer(e.getKey(), e.getValue(), Bukkit.getPlayer(e.getKey()));
            }
        }
    }

    /** À l'arrêt du plugin : tout part en base. */
    public void fermer() {
        for (Map.Entry<UUID, Fiche> e : fiches.entrySet()) {
            enregistrer(e.getKey(), e.getValue());
            payer(e.getKey(), e.getValue(), null);
        }
        fiches.clear();
    }

    private void enregistrer(UUID uuid, Fiche f) {
        if (!f.chargee || !f.modifiee || !plugin.db().available()) {
            return;
        }
        f.modifiee = false;
        Map<Metier, Long> copie = new EnumMap<>(f.xp);
        plugin.db().run(c -> {
            try (PreparedStatement st = c.prepareStatement("INSERT INTO survie_metiers (uuid, metier, xp) VALUES (?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE xp = VALUES(xp)")) {
                for (Map.Entry<Metier, Long> e : copie.entrySet()) {
                    st.setString(1, uuid.toString());
                    st.setString(2, e.getKey().cle());
                    st.setLong(3, e.getValue());
                    st.addBatch();
                }
                st.executeBatch();
            }
            return null;
        });
    }

    private void payer(UUID uuid, Fiche f, Player p) {
        double montant = Math.floor(f.paie * 100) / 100.0;
        if (montant < 0.01 || !plugin.eco().active()) {
            return;
        }
        f.paie -= montant;
        plugin.eco().crediter(uuid, f.nom == null ? "?" : f.nom, montant);
        if (p != null) {
            Msg.send(p, "Paie de tes métiers : " + Msg.argent(montant) + " <dark_gray>(/metiers)");
        }
    }

    // ------------------------------------------------------------------ Menu

    public void ouvrir(Player p) {
        Fiche f = fiches.get(p.getUniqueId());
        if (f == null || !f.chargee) {
            lire(p);
            Msg.send(p, "<red>Tes métiers se chargent, réessaie dans un instant.");
            return;
        }
        Menu menu = new Menu(4, Msg.OR + "<bold>Métiers");
        Menu.cadre(menu, 4);
        int slot = 11;
        for (Metier m : Metier.values()) {
            Etat e = etat(f.xp.getOrDefault(m, 0L));
            Map.Entry<Integer, Double> palier = paliers.higherEntry(e.niveau());
            var icone = Menu.icone(m.icone, Msg.OR + "<bold>" + m.nom,
                    "<gray>" + m.comment,
                    "",
                    "<gray>Niveau <white>" + e.niveau() + " <dark_gray>/ " + niveauMax,
                    e.besoin() > 0 ? Menu.barre(e.dans() / (double) e.besoin()) + " <gray>" + e.dans() + " / " + e.besoin() + " XP"
                            : "<#3DDCB0>Niveau maximum",
                    "<gray>Paie : <white>+" + Msg.nombre(bonusNiveau * (e.niveau() - 1)) + " %",
                    palier == null ? "<dark_gray>Tous les paliers sont passés."
                            : "<gray>Prochain palier : niveau <white>" + palier.getKey() + " <dark_gray>→ " + Msg.argent(palier.getValue()));
            icone.editMeta(meta -> meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES));
            icone.setAmount(Math.max(1, Math.min(64, e.niveau())));
            menu.set(slot++, icone, null);
        }
        menu.set(31, Menu.icone(Material.BOOK, Msg.OR + "<bold>Comment ça marche",
                "<gray>Tu as les cinq métiers : ils montent tout seuls",
                "<gray>quand tu joues dans le monde de survie.",
                "<gray>Chaque action paie quelques crédits, versés",
                "<gray>toutes les <white>" + paieMinutes + " min<gray>. Plus le niveau est haut, plus ça paie.",
                "<dark_gray>Classement du mois : /classement xp"), null);
        menu.open(p);
    }
}
