package fr.orbit.tycoon;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import static fr.orbit.tycoon.Navigateur.texte;

/**
 * Enchantements propres à chaque outil de l'Outil orbital (en plus de ceux du Noyau, communs à tous les outils).
 * - 100 niveaux par enchantement, payés uniquement avec la ressource de l'outil (son Flux : Minerai stellaire, Bois sidéral…).
 * - Prix : base × croissance^niveau × saut^(niveau / palier) → de plus en plus cher, avec un saut tous les 25 niveaux.
 * - Certains enchantements demandent un prestige minimum sur l'outil.
 * - Réglages : section « enchants-outils » de config.yml (valeurs par défaut dans le code).
 * Données : enchants-outils.yml (uuid → outil → enchantement → niveau).
 */
final class Enchantements implements Listener {

    static final int NIVEAU_MAX = 100;

    private static final Set<Outil.Mode> TOUS = EnumSet.allOf(Outil.Mode.class);

    /** effet = valeur par niveau (%, sauf Célérité). prestige = prestige de l'outil requis pour l'acheter. */
    enum Ench {
        // Communs (chaque outil a ses propres niveaux)
        ABONDANCE("abondance", "Abondance", Material.CHEST, "#3DDCB0", TOUS, 0, 800, 1.0,
                "+%s %% de ressources récoltées"),
        AVIDITE("avidite", "Avidité", Material.PRISMARINE_CRYSTALS, "#62CFF4", TOUS, 0, 1_000, 1.5,
                "+%s %% de ressource de l'outil"),
        ERUDITION("erudition", "Érudition", Material.EXPERIENCE_BOTTLE, "#3DDCB0", TOUS, 0, 1_000, 1.0,
                "+%s %% d'XP de l'outil"),
        MARCHAND("marchand", "Marchand", Material.GOLD_INGOT, "#E0B864", TOUS, 3, 1_500, 1.0,
                "+%s %% de Crédits orbitaux"),
        CLES("cles", "Chasseur de clés", Material.TRIPWIRE_HOOK, "#C77DFF", TOUS, 10, 3_000, 0.05,
                "+%s %% de chance de Clé Farm"),
        // Pioche + Hache
        CELERITE("celerite", "Célérité", Material.SUGAR, "#B0E0FF", EnumSet.of(Outil.Mode.PIOCHE, Outil.Mode.HACHE), 0, 1_200, 0,
                "Hâte %s quand l'outil est en main"),
        // Pioche
        EXPLOSION("explosion", "Explosion", Material.TNT, "#FF6B3D", EnumSet.of(Outil.Mode.PIOCHE), 5, 2_500, 0.25,
                "%s %% de chance d'exploser en 3×3×3"),
        FOREUSE("foreuse", "Foreuse", Material.END_ROD, "#FFD86B", EnumSet.of(Outil.Mode.PIOCHE), 20, 5_000, 0.1,
                "%s %% de chance de forer toute une ligne"),
        FILON("filon", "Filon", Material.RAW_GOLD, "#E0B864", EnumSet.of(Outil.Mode.PIOCHE), 10, 4_000, 0.1,
                "%s %% de chance de ressources ×3"),
        SEISME("seisme", "Séisme", Material.CRACKED_DEEPSLATE_BRICKS, "#8A6BFF", EnumSet.of(Outil.Mode.PIOCHE), 40, 10_000, 0.05,
                "%s %% de chance de casser toute la couche en 7×7"),
        // Hache
        ABATTAGE("abattage", "Abattage", Material.OAK_LOG, "#C8894B", EnumSet.of(Outil.Mode.HACHE), 5, 2_500, 0.3,
                "%s %% de chance d'abattre tout l'arbre"),
        SEVE("seve", "Sève dorée", Material.HONEY_BOTTLE, "#E0B864", EnumSet.of(Outil.Mode.HACHE), 20, 5_000, 0.1,
                "%s %% de chance de Crédits ×5"),
        ECLATS("eclats", "Éclats", Material.STRIPPED_OAK_LOG, "#D9A066", EnumSet.of(Outil.Mode.HACHE), 10, 4_000, 0.1,
                "%s %% de chance de ressources ×3"),
        TORNADE("tornade", "Tornade", Material.WIND_CHARGE, "#B0E0FF", EnumSet.of(Outil.Mode.HACHE), 40, 10_000, 0.05,
                "%s %% de chance d'abattre tous les arbres autour"),
        // Houe
        ENGRAIS("engrais", "Engrais", Material.BONE_MEAL, "#7CFC9A", EnumSet.of(Outil.Mode.HOUE), 0, 1_500, 0.5,
                "-%s %% de temps de repousse"),
        MOISSON("moisson", "Moisson", Material.HAY_BLOCK, "#F5D442", EnumSet.of(Outil.Mode.HOUE), 5, 2_500, 0.3,
                "%s %% de chance de moissonner autour (3×3, 5×5 dès le niv. 50)"),
        DIVINE("divine", "Récolte divine", Material.GOLDEN_CARROT, "#FFF2A8", EnumSet.of(Outil.Mode.HOUE), 20, 5_000, 0.1,
                "%s %% de chance de gains ×5"),
        FERTILITE("fertilite", "Fertilité", Material.WHEAT_SEEDS, "#9BE564", EnumSet.of(Outil.Mode.HOUE), 10, 4_000, 0.1,
                "%s %% de chance de ressources ×3"),
        TEMPETE("tempete", "Tempête de récolte", Material.LIGHTNING_ROD, "#F5D442", EnumSet.of(Outil.Mode.HOUE), 40, 10_000, 0.05,
                "%s %% de chance de moissonner en 9×9"),
        // Canne
        APPAT("appat", "Appât", Material.SPIDER_EYE, "#3FA9F5", EnumSet.of(Outil.Mode.CANNE), 0, 1_500, 0.6,
                "-%s %% d'attente avant une touche"),
        DOUBLE("double", "Double prise", Material.TROPICAL_FISH, "#62CFF4", EnumSet.of(Outil.Mode.CANNE), 5, 2_500, 0.3,
                "%s %% de chance de prise ×2"),
        TRESOR("tresor", "Trésor abyssal", Material.HEART_OF_THE_SEA, "#C77DFF", EnumSet.of(Outil.Mode.CANNE), 20, 5_000, 0.03,
                "%s %% de chance de pêcher une clé de coffre"),
        BANC("banc", "Banc de poissons", Material.SALMON, "#FF9F7A", EnumSet.of(Outil.Mode.CANNE), 10, 4_000, 0.1,
                "%s %% de chance de prise ×3"),
        LEVIATHAN("leviathan", "Léviathan", Material.NAUTILUS_SHELL, "#2E7BFF", EnumSet.of(Outil.Mode.CANNE), 40, 10_000, 0.05,
                "%s %% de chance de gains ×10"),
        // Épée
        TRANCHANT("tranchant", "Tranchant", Material.FLINT, "#FF4D4D", EnumSet.of(Outil.Mode.EPEE), 0, 1_500, 2.0,
                "+%s %% de dégâts"),
        CARNAGE("carnage", "Carnage", Material.WITHER_SKELETON_SKULL, "#B03030", EnumSet.of(Outil.Mode.EPEE), 5, 2_500, 0.2,
                "%s %% de chance de frapper les monstres autour"),
        BUTIN("butin", "Butin sanglant", Material.REDSTONE, "#FF4D4D", EnumSet.of(Outil.Mode.EPEE), 20, 5_000, 0.1,
                "%s %% de chance de gains ×5"),
        MASSACRE("massacre", "Massacre", Material.BONE, "#E8E0D0", EnumSet.of(Outil.Mode.EPEE), 10, 4_000, 0.1,
                "%s %% de chance de gains ×3"),
        EXECUTION("execution", "Exécution", Material.NETHERITE_SWORD, "#7A0000", EnumSet.of(Outil.Mode.EPEE), 40, 10_000, 0.1,
                "%s %% de chance de tuer en un coup");

        final String id, nom, couleur, description;
        final Material icone;
        final Set<Outil.Mode> outils;
        final int prestigeDefaut;
        final long prixDefaut;
        final double effetDefaut;

        Ench(String id, String nom, Material icone, String couleur, Set<Outil.Mode> outils, int prestige, long prix, double effet,
             String description) {
            this.id = id; this.nom = nom; this.icone = icone; this.couleur = couleur; this.outils = outils;
            this.prestigeDefaut = prestige; this.prixDefaut = prix; this.effetDefaut = effet; this.description = description;
        }

        static List<Ench> de(Outil.Mode m) {
            List<Ench> l = new ArrayList<>();
            for (Ench e : values()) if (e.outils.contains(m)) l.add(e);
            return l;
        }
    }

    /** Bonus d'enchantements pour une récolte (tirages déjà faits). */
    record Gains(double quantitePct, int multQuantite, double multCredit, double multFlux, double multXp, String extra) {
        static final Gains AUCUN = new Gains(0, 1, 1, 1, 1, null);
    }

    // Menu (5 rangées) : fiche en haut, les 5 communs puis les 5 propres à l'outil.
    private static final int SLOT_INFO = 4, SLOT_RETOUR = 36, SLOT_FERMER = 44;
    private static final int[] SLOTS = {11, 12, 13, 14, 15, 20, 21, 22, 23, 24};

    private static final class Menu implements InventoryHolder {
        final Outil.Mode outil;
        Inventory inv;
        Menu(Outil.Mode outil) { this.outil = outil; }
        @Override public Inventory getInventory() { return inv; }
    }

    private final OrbitTycoon plugin;
    private final Map<UUID, EnumMap<Outil.Mode, EnumMap<Ench, Integer>>> niveaux = new ConcurrentHashMap<>();
    /** Enchantements que le joueur a désactivés (gardent leur niveau, n'agissent plus). */
    private final Map<UUID, EnumMap<Outil.Mode, EnumSet<Ench>>> desactives = new ConcurrentHashMap<>();
    /** Joueurs dont on casse/frappe en chaîne (évite qu'une Explosion relance une Explosion). */
    private final Set<UUID> enChaine = new HashSet<>();
    private volatile boolean modifie;
    private BukkitTask tache, sauvegarde;

    Enchantements(OrbitTycoon plugin) {
        this.plugin = plugin;
    }

    void demarrer() {
        charger();
        tache = Bukkit.getScheduler().runTaskTimer(plugin, this::celerite, 20L, 20L);
        sauvegarde = Bukkit.getScheduler().runTaskTimer(plugin, () -> { if (modifie) sauver(true); }, 600L, 600L);
    }

    void arreter() {
        if (tache != null) tache.cancel();
        if (sauvegarde != null) sauvegarde.cancel();
        if (modifie) sauver(false);
    }

    // ------------------------------------------------------------------ réglages

    private ConfigurationSection cfg(Ench e) {
        return plugin.getConfig().getConfigurationSection("enchants-outils." + e.id);
    }

    double effet(Ench e) {
        ConfigurationSection c = cfg(e);
        return c == null ? e.effetDefaut : c.getDouble("effet", e.effetDefaut);
    }

    int prestigeRequis(Ench e) {
        ConfigurationSection c = cfg(e);
        return c == null ? e.prestigeDefaut : Math.max(0, c.getInt("prestige", e.prestigeDefaut));
    }

    /** Prix du niveau {@code niveau + 1}, dans la ressource de l'outil. */
    long prix(Ench e, int niveau) {
        ConfigurationSection c = cfg(e);
        var g = plugin.getConfig();
        long base = c == null ? e.prixDefaut : c.getLong("prix", e.prixDefaut);
        double croissance = Math.max(1, g.getDouble("enchants-outils.croissance", 1.07));
        double saut = Math.max(1, g.getDouble("enchants-outils.saut", 2.0));
        int palier = Math.max(1, g.getInt("enchants-outils.palier", 25));
        double p = base * Math.pow(croissance, niveau) * Math.pow(saut, niveau / palier);
        long arrondi = Math.round(Math.min(p, Long.MAX_VALUE / 2.0));
        return (arrondi + 99) / 100 * 100;
    }

    // ------------------------------------------------------------------ niveaux

    int niveau(UUID id, Outil.Mode m, Ench e) {
        var parOutil = niveaux.get(id);
        if (parOutil == null) return 0;
        var map = parOutil.get(m);
        return map == null ? 0 : map.getOrDefault(e, 0);
    }

    void definir(UUID id, Outil.Mode m, Ench e, int niveau) {
        niveaux.computeIfAbsent(id, k -> new EnumMap<>(Outil.Mode.class))
                .computeIfAbsent(m, k -> new EnumMap<>(Ench.class))
                .put(e, Math.max(0, Math.min(NIVEAU_MAX, niveau)));
        modifie = true;
    }

    /** Valeur de l'effet (%, ou niveau de Hâte pour Célérité). */
    double valeur(Ench e, int niveau) {
        if (e == Ench.CELERITE) return niveau <= 0 ? 0 : Math.min(5, (niveau - 1) / 20 + 1);
        return niveau * effet(e);
    }

    boolean active(UUID id, Outil.Mode m, Ench e) {
        var parOutil = desactives.get(id);
        var set = parOutil == null ? null : parOutil.get(m);
        return set == null || !set.contains(e);
    }

    void basculer(UUID id, Outil.Mode m, Ench e) {
        var set = desactives.computeIfAbsent(id, k -> new EnumMap<>(Outil.Mode.class)).computeIfAbsent(m, k -> EnumSet.noneOf(Ench.class));
        if (!set.remove(e)) set.add(e);
        modifie = true;
    }

    /** Effet réel : 0 si l'enchantement est désactivé par le joueur. */
    private double actif(UUID id, Outil.Mode m, Ench e) {
        return active(id, m, e) ? valeur(e, niveau(id, m, e)) : 0;
    }

    private static boolean tirage(double pct) {
        return pct > 0 && ThreadLocalRandom.current().nextDouble(100) < pct;
    }

    /** Lignes de lore pour l'Outil orbital (enchantements achetés pour ce mode). */
    List<String> lore(UUID id, Outil.Mode m) {
        List<String> l = new ArrayList<>();
        for (Ench e : Ench.de(m)) {
            int n = niveau(id, m, e);
            if (n <= 0) continue;
            if (!active(id, m, e)) l.add("<dark_gray><st>" + e.nom + " " + n + "</st> (désactivé)");
            else l.add("<" + e.couleur + ">" + e.nom + " <white>" + n + (n >= NIVEAU_MAX ? " <#E0B864>✦" : ""));
        }
        return l;
    }

    // ------------------------------------------------------------------ effets

    /**
     * Bonus d'une récolte faite avec cet outil (appelé par Monnaies.recompenser). Trésor abyssal donne la clé tout de suite.
     */
    Gains gains(Player p, Outil.Mode m) {
        UUID id = p.getUniqueId();
        if (!niveaux.containsKey(id)) return Gains.AUCUN;
        double quantite = actif(id, m, Ench.ABONDANCE);
        double credit = 1 + actif(id, m, Ench.MARCHAND) / 100.0;
        double flux = 1 + actif(id, m, Ench.AVIDITE) / 100.0;
        double xp = 1 + actif(id, m, Ench.ERUDITION) / 100.0;
        int multQ = 1;
        List<String> extras = new ArrayList<>();
        switch (m) {
            case PIOCHE -> { if (tirage(actif(id, m, Ench.FILON))) { multQ *= 3; extras.add("<#E0B864>✦ Filon ×3"); } }
            case HACHE -> {
                if (tirage(actif(id, m, Ench.ECLATS))) { multQ *= 3; extras.add("<#D9A066>✦ Éclats ×3"); }
                if (tirage(actif(id, m, Ench.SEVE))) { credit *= 5; extras.add("<#E0B864><bold>✦ Sève dorée ×5</bold>"); }
            }
            case HOUE -> {
                if (tirage(actif(id, m, Ench.FERTILITE))) { multQ *= 3; extras.add("<#9BE564>✦ Fertilité ×3"); }
                if (tirage(actif(id, m, Ench.DIVINE))) { credit *= 5; flux *= 5; extras.add("<#FFF2A8><bold>✦ Récolte divine ×5</bold>"); }
            }
            case EPEE -> {
                if (tirage(actif(id, m, Ench.MASSACRE))) { credit *= 3; flux *= 3; extras.add("<#E8E0D0>✦ Massacre ×3"); }
                if (tirage(actif(id, m, Ench.BUTIN))) { credit *= 5; flux *= 5; extras.add("<#FF4D4D><bold>✦ Butin sanglant ×5</bold>"); }
            }
            case CANNE -> {
                if (tirage(actif(id, m, Ench.BANC))) { multQ *= 3; extras.add("<#FF9F7A>✦ Banc de poissons ×3"); }
                else if (tirage(actif(id, m, Ench.DOUBLE))) { multQ *= 2; extras.add("<#62CFF4>✦ Double prise"); }
                if (tirage(actif(id, m, Ench.LEVIATHAN))) {
                    credit *= 10; flux *= 10; extras.add("<#2E7BFF><bold>🐋 Léviathan ×10</bold>");
                    p.playSound(p.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_AMBIENT, 0.5f, 1.2f);
                }
                if (tirage(actif(id, m, Ench.TRESOR))) tresor(p);
            }
        }
        String extra = extras.isEmpty() ? null : String.join("   ", extras);
        if (extra != null) p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.6f);
        return new Gains(quantite, multQ, credit, flux, xp, extra);
    }

    /** Chance (en %) ajoutée à la Clé Farm par Chasseur de clés. */
    double chanceCle(UUID id, Outil.Mode m) {
        return m == null ? 0 : actif(id, m, Ench.CLES);
    }

    /** Secondes de repousse après Engrais (houe). */
    int repousse(Player p, int base) {
        double pct = Math.min(90, actif(p.getUniqueId(), Outil.Mode.HOUE, Ench.ENGRAIS));
        return Math.max(1, (int) Math.round(base * (1 - pct / 100.0)));
    }

    private void tresor(Player p) {
        Coffres c = plugin.coffres();
        if (c == null) return;
        // Surtout des clés des premiers coffres, rarement des suivants (Pets exclu).
        int nb = Math.max(1, Coffres.NB - 1);
        int n = 0;
        while (n < nb - 1 && ThreadLocalRandom.current().nextDouble() < 0.35) n++;
        for (var reste : p.getInventory().addItem(c.cle(n, 1)).values()) p.getWorld().dropItemNaturally(p.getLocation(), reste);
        OrbitTycoon.msg(p, "<#C77DFF>✦ Trésor abyssal <gray>: tu as pêché une <#E0B864><bold>Clé "
                + net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().escapeTags(c.nom(n)) + "</bold><gray> !");
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.3f);
    }

    /**
     * Après un bloc récolté dans une zone (Zones.recolter) : Explosion / Foreuse (pioche), Abattage (hache), Moisson (houe).
     * Les blocs en plus sont cassés « par le joueur » : ils passent par Zones (repousse, gains) comme un vrai coup.
     */
    void apresCasse(Player p, Outil.Mode m, Block b) {
        if (m == null) return;
        UUID id = p.getUniqueId();
        if (enChaine.contains(id) || !niveaux.containsKey(id)) return;
        List<Block> cibles = new ArrayList<>();
        String nom = null;
        switch (m) {
            case PIOCHE -> {
                if (tirage(actif(id, m, Ench.SEISME))) {
                    nom = "<#8A6BFF><bold>⚠ Séisme</bold>";
                    for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) if (x != 0 || z != 0) cibles.add(b.getRelative(x, 0, z));
                    p.playSound(b.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 0.4f, 1.6f);
                } else if (tirage(actif(id, m, Ench.FOREUSE))) {
                    nom = "<#FFD86B><bold>⚡ Foreuse</bold>";
                    BlockFace f = p.getFacing();
                    for (int s = -1; s <= 1; s += 2)
                        for (int i = 1; i <= 15; i++) cibles.add(b.getRelative(f.getModX() * i * s, 0, f.getModZ() * i * s));
                } else if (tirage(actif(id, m, Ench.EXPLOSION))) {
                    nom = "<#FF6B3D><bold>✹ Explosion</bold>";
                    for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++)
                        if (x != 0 || y != 0 || z != 0) cibles.add(b.getRelative(x, y, z));
                    b.getWorld().spawnParticle(Particle.EXPLOSION, b.getLocation().add(0.5, 0.5, 0.5), 1);
                    p.playSound(b.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.4f, 1.4f);
                }
            }
            case HACHE -> {
                if (Tag.LOGS.isTagged(b.getType()) && tirage(actif(id, m, Ench.TORNADE))) {
                    nom = "<#B0E0FF><bold>🌪 Tornade</bold>";
                    for (int y = -3; y <= 12 && cibles.size() < 150; y++)
                        for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) {
                            Block v = b.getRelative(x, y, z);
                            if ((x != 0 || y != 0 || z != 0) && Tag.LOGS.isTagged(v.getType()) && cibles.size() < 150) cibles.add(v);
                        }
                    p.playSound(b.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 0.6f, 0.8f);
                } else if (Tag.LOGS.isTagged(b.getType()) && tirage(actif(id, m, Ench.ABATTAGE))) {
                    nom = "<#C8894B><bold>🪓 Abattage</bold>";
                    Set<Block> vus = new HashSet<>(Set.of(b));
                    ArrayDeque<Block> file = new ArrayDeque<>(List.of(b));
                    while (!file.isEmpty() && cibles.size() < 48) {
                        Block c = file.poll();
                        for (int x = -1; x <= 1; x++) for (int y = 0; y <= 1; y++) for (int z = -1; z <= 1; z++) {
                            Block v = c.getRelative(x, y, z);
                            if (vus.add(v) && Tag.LOGS.isTagged(v.getType())) { cibles.add(v); file.add(v); }
                        }
                    }
                }
            }
            case HOUE -> {
                boolean tempete = tirage(actif(id, m, Ench.TEMPETE));
                if (tempete || tirage(actif(id, m, Ench.MOISSON))) {
                    nom = tempete ? "<#F5D442><bold>⚡ Tempête de récolte</bold>" : "<#F5D442><bold>✿ Moisson</bold>";
                    int r = tempete ? 4 : niveau(id, m, Ench.MOISSON) >= 50 ? 2 : 1;
                    for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) {
                        if (x == 0 && z == 0) continue;
                        Block v = b.getRelative(x, 0, z);
                        if (v.getBlockData() instanceof Ageable a && a.getAge() >= a.getMaximumAge()) cibles.add(v);
                        else if (v.getType() == Material.MELON || v.getType() == Material.PUMPKIN) cibles.add(v);
                    }
                }
            }
            default -> { }
        }
        if (cibles.isEmpty()) return;
        enChaine.add(id);
        try {
            for (Block c : cibles) if (!c.getType().isAir()) p.breakBlock(c);
        } finally {
            enChaine.remove(id);
        }
        p.sendActionBar(texte(nom));
    }

    /** Célérité : Hâte tant que la pioche ou la hache orbitale est en main (hors îles de joueurs). */
    private void celerite() {
        Outil o = plugin.outil();
        if (o == null) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (IlesJoueurs.estMonde(p.getWorld())) continue;
            ItemStack main = p.getInventory().getItemInMainHand();
            if (!o.est(main)) continue;
            Outil.Mode m = o.mode(main);
            if (m != Outil.Mode.PIOCHE && m != Outil.Mode.HACHE) continue;
            int hate = (int) actif(p.getUniqueId(), m, Ench.CELERITE);
            if (hate <= 0) continue;
            p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 50, hate - 1, true, false, false));
        }
    }

    /** Appât : la touche arrive plus vite. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void lancer(PlayerFishEvent e) {
        if (e.getState() != PlayerFishEvent.State.FISHING || IlesJoueurs.estMonde(e.getPlayer().getWorld())) return;
        double pct = Math.min(90, actif(e.getPlayer().getUniqueId(), Outil.Mode.CANNE, Ench.APPAT));
        if (pct <= 0) return;
        var hook = e.getHook();
        int min = Math.max(1, (int) Math.round(hook.getMinWaitTime() * (1 - pct / 100.0)));
        int max = Math.max(min, (int) Math.round(hook.getMaxWaitTime() * (1 - pct / 100.0)));
        hook.setWaitTime(min, max);
    }

    /** Tranchant (dégâts) et Carnage (frappe les monstres autour), épée orbitale en main. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void frapper(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p) || !(e.getEntity() instanceof Enemy) || !(e.getEntity() instanceof LivingEntity cible)) return;
        if (IlesJoueurs.estMonde(p.getWorld())) return;
        Outil o = plugin.outil();
        ItemStack main = p.getInventory().getItemInMainHand();
        if (o == null || !o.est(main) || o.mode(main) != Outil.Mode.EPEE) return;
        UUID id = p.getUniqueId();
        double tranchant = actif(id, Outil.Mode.EPEE, Ench.TRANCHANT);
        if (tranchant > 0) e.setDamage(e.getDamage() * (1 + tranchant / 100.0));
        double degats = e.getDamage();
        if (!enChaine.contains(id) && tirage(actif(id, Outil.Mode.EPEE, Ench.EXECUTION))) {
            e.setDamage(1_000_000);
            cible.getWorld().spawnParticle(Particle.CRIT, cible.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.2);
            p.playSound(cible.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.8f, 0.7f);
            p.sendActionBar(texte("<#7A0000><bold>☠ Exécution</bold>"));
        }
        if (enChaine.contains(id) || !tirage(actif(id, Outil.Mode.EPEE, Ench.CARNAGE))) return;
        enChaine.add(id);
        try {
            int touches = 0;
            for (Entity n : cible.getNearbyEntities(3.5, 2, 3.5)) {
                if (touches >= 8) break;
                if (n == cible || !(n instanceof Enemy) || !(n instanceof LivingEntity v) || v.isDead()) continue;
                v.damage(degats, p);
                touches++;
            }
        } finally {
            enChaine.remove(id);
        }
        cible.getWorld().spawnParticle(Particle.SWEEP_ATTACK, cible.getLocation().add(0, 1, 0), 6, 1.5, 0.3, 1.5);
        p.sendActionBar(texte("<#B03030><bold>☠ Carnage</bold>"));
    }

    // ------------------------------------------------------------------ menu

    void ouvrir(Player p, Outil.Mode m) {
        Menu h = new Menu(m);
        Inventory inv = Bukkit.createInventory(h, 45, Habillage.titre(plugin, p, 5, "Enchantements"));
        h.inv = inv;
        UUID id = p.getUniqueId();
        long solde = plugin.monnaies().flux(id, m);
        int prestige = plugin.monnaies().prestige(id, m);
        inv.setItem(SLOT_INFO, objet(m.type, "<" + m.couleur + "><bold>" + m.nom + "</bold> <dark_gray>· <gray>enchantements", true,
                "<gray>Ces enchantements n'agissent qu'avec",
                "<gray>cet outil, jusqu'au niveau <white>" + NIVEAU_MAX + "<gray>.",
                "<gray>Ils se paient en <" + m.couleur + ">" + m.ressource + "<gray>.",
                "",
                "<gray>Solde : <" + m.couleur + ">" + m.symbole + " " + Hud.nombre(solde),
                "<gray>Prestige de l'outil : <#E0B864>✪ " + prestige));
        List<Ench> liste = Ench.de(m);
        for (int i = 0; i < liste.size() && i < SLOTS.length; i++) inv.setItem(SLOTS[i], icone(p, m, liste.get(i), solde, prestige));
        inv.setItem(SLOT_RETOUR, objet(Material.ARROW, "<#62CFF4>← Retour", false, "<gray>Revenir à l'Outil orbital."));
        inv.setItem(SLOT_FERMER, objet(Material.BARRIER, "<#FF4D4D>Fermer", false));
        Habillage.fond(plugin, p, inv);
        p.openInventory(inv);
    }

    private ItemStack icone(Player p, Outil.Mode m, Ench e, long solde, int prestige) {
        int niv = niveau(p.getUniqueId(), m, e), requis = prestigeRequis(e);
        boolean verrou = prestige < requis;
        List<String> l = new ArrayList<>();
        l.add("<gray>Niveau : <white>" + niv + "<gray> / " + NIVEAU_MAX + "  " + barre(niv));
        l.add("<gray>Effet : <" + e.couleur + ">" + decrire(e, niv));
        boolean on = active(p.getUniqueId(), m, e);
        if (niv > 0) l.add(on ? "<#7CFC9A>● Activé" : "<#FF4D4D>○ Désactivé <dark_gray>(n'agit plus)");
        l.add("");
        if (verrou) {
            l.add("<#FF4D4D>🔒 Prestige ✪ " + requis + " de l'outil requis");
            l.add("<gray>(tu es à <#E0B864>✪ " + prestige + "<gray>)");
        } else if (niv >= NIVEAU_MAX) {
            l.add("<#7CFC9A>✦ Niveau maximum atteint");
        } else {
            long prix = prix(e, niv);
            l.add("<gray>Suivant : <" + e.couleur + ">" + decrire(e, niv + 1));
            l.add("<gray>Prix : " + (solde >= prix ? "<" + m.couleur + ">" : "<#FF4D4D>") + m.symbole + " " + Hud.nombre(prix) + " " + m.ressource);
            l.add("");
            l.add("<#62CFF4>➜ Clic gauche : +1 niveau");
            l.add("<#62CFF4>➜ Clic droit : +10 niveaux");
            l.add("<#62CFF4>➜ Shift + clic gauche : le plus possible");
        }
        if (niv > 0) l.add("<#E0B864>➜ Shift + clic droit : " + (on ? "désactiver" : "activer"));
        ItemStack it = objet(e.icone, "<" + e.couleur + "><bold>" + e.nom + (niv > 0 ? " " + niv : "") + "</bold>"
                        + (verrou ? " <dark_gray>🔒" : niv > 0 && !on ? " <#FF4D4D>(off)" : ""),
                niv > 0 && on, l.toArray(String[]::new));
        if (niv > 0) it.setAmount(Math.max(1, Math.min(64, niv)));
        return it;
    }

    private String decrire(Ench e, int niveau) {
        double v = valeur(e, niveau);
        String s = e == Ench.CELERITE ? (v <= 0 ? "—" : romain((int) v)) : joli(v);
        return String.format(e.description, s);
    }

    private static String romain(int n) {
        return switch (n) { case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; case 4 -> "IV"; case 5 -> "V"; default -> Integer.toString(n); };
    }

    private static String joli(double v) {
        return v == Math.rint(v) ? Long.toString((long) v) : String.format(java.util.Locale.FRANCE, "%.2f", v).replaceAll("0$", "");
    }

    private static String barre(int n) {
        int plein = n * 10 / NIVEAU_MAX;
        return "<#62CFF4>" + "▮".repeat(plein) + "<dark_gray>" + "▮".repeat(10 - plein);
    }

    private static ItemStack objet(Material m, String nom, boolean brille, String... lignes) {
        ItemStack it = new ItemStack(m);
        it.editMeta(meta -> {
            meta.displayName(texte(nom));
            List<Component> lore = new ArrayList<>();
            for (String l : lignes) lore.add(texte(l));
            if (!lore.isEmpty()) meta.lore(lore);
            if (brille) meta.setEnchantmentGlintOverride(true);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        });
        return it;
    }

    /** Achète jusqu'à {@code combien} niveaux (s'arrête si le solde ne suit plus). */
    private void acheter(Player p, Outil.Mode m, Ench e, int combien) {
        UUID id = p.getUniqueId();
        Monnaies mon = plugin.monnaies();
        int requis = prestigeRequis(e);
        if (mon.prestige(id, m) < requis) {
            OrbitTycoon.msg(p, "<#FF4D4D>Il faut le prestige ✪ " + requis + " sur ton " + m.nom.toLowerCase() + " pour " + e.nom + ".");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1f);
            return;
        }
        int niv = niveau(id, m, e), achetes = 0;
        long total = 0;
        while (achetes < combien && niv < NIVEAU_MAX) {
            long prix = prix(e, niv);
            long solde = mon.flux(id, m);
            if (solde < prix) break;
            mon.definirFlux(id, m, solde - prix);
            total += prix;
            niv++;
            achetes++;
        }
        if (achetes == 0) {
            OrbitTycoon.msg(p, niv >= NIVEAU_MAX ? "<#7CFC9A>" + e.nom + " est déjà au niveau maximum."
                    : "<#FF4D4D>Il te faut " + m.symbole + " " + Hud.nombre(prix(e, niv)) + " " + m.ressource + ".");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1f);
            return;
        }
        definir(id, m, e, niv);
        p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.2f);
        p.getWorld().spawnParticle(Particle.ENCHANT, p.getLocation().add(0, 1.2, 0), 40, 0.4, 0.6, 0.4, 0.8);
        OrbitTycoon.msg(p, "<" + m.couleur + ">" + m.nom + "<gray> : <" + e.couleur + ">" + e.nom + " " + niv + "</" + e.couleur + ">"
                + (achetes > 1 ? " <dark_gray>(+" + achetes + ")" : "") + " <gray>pour " + m.symbole + " " + Hud.nombre(total) + ".");
        plugin.outil().actualiser(p);
        ouvrir(p, m);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void clic(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Menu h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        int slot = e.getRawSlot();
        if (slot == SLOT_FERMER) { p.closeInventory(); return; }
        if (slot == SLOT_RETOUR) { plugin.outil().ouvrir(p); return; }
        List<Ench> liste = Ench.de(h.outil);
        for (int i = 0; i < SLOTS.length && i < liste.size(); i++) {
            if (SLOTS[i] != slot) continue;
            Ench ench = liste.get(i);
            if (e.isShiftClick() && e.isRightClick()) {
                if (niveau(p.getUniqueId(), h.outil, ench) <= 0) {
                    OrbitTycoon.msg(p, "<#FF4D4D>Achète d'abord un niveau de " + ench.nom + ".");
                    return;
                }
                basculer(p.getUniqueId(), h.outil, ench);
                boolean on = active(p.getUniqueId(), h.outil, ench);
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, on ? 1.4f : 0.8f);
                OrbitTycoon.msg(p, "<" + ench.couleur + ">" + ench.nom + "<gray> : " + (on ? "<#7CFC9A>activé" : "<#FF4D4D>désactivé") + "<gray>.");
                plugin.outil().actualiser(p);
                ouvrir(p, h.outil);
                return;
            }
            int combien = e.isShiftClick() ? NIVEAU_MAX : e.isRightClick() ? 10 : 1;
            acheter(p, h.outil, ench, combien);
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void glisser(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ données (enchants-outils.yml)

    private File fichier() { return new File(plugin.getDataFolder(), "enchants-outils.yml"); }

    private void charger() {
        niveaux.clear();
        desactives.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(fichier());
        for (String cle : y.getKeys(false)) {
            UUID id;
            try { id = UUID.fromString(cle); } catch (IllegalArgumentException ex) { continue; }
            for (Outil.Mode m : Outil.Mode.values())
                for (String off : y.getStringList(cle + "." + m.id + ".desactives"))
                    for (Ench e : Ench.de(m))
                        if (e.id.equals(off)) desactives.computeIfAbsent(id, k -> new EnumMap<>(Outil.Mode.class))
                                .computeIfAbsent(m, k -> EnumSet.noneOf(Ench.class)).add(e);
            for (Outil.Mode m : Outil.Mode.values())
                for (Ench e : Ench.de(m)) {
                    int n = y.getInt(cle + "." + m.id + "." + e.id, 0);
                    if (n > 0) niveaux.computeIfAbsent(id, k -> new EnumMap<>(Outil.Mode.class))
                            .computeIfAbsent(m, k -> new EnumMap<>(Ench.class)).put(e, Math.min(NIVEAU_MAX, n));
                }
        }
    }

    /** Le texte est préparé sur le thread principal ; l'écriture peut se faire à côté. */
    private void sauver(boolean async) {
        YamlConfiguration y = new YamlConfiguration();
        for (var j : niveaux.entrySet())
            for (var o : j.getValue().entrySet())
                for (var e : o.getValue().entrySet())
                    if (e.getValue() > 0) y.set(j.getKey() + "." + o.getKey().id + "." + e.getKey().id, e.getValue());
        for (var j : desactives.entrySet())
            for (var o : j.getValue().entrySet())
                if (!o.getValue().isEmpty())
                    y.set(j.getKey() + "." + o.getKey().id + ".desactives", o.getValue().stream().map(e -> e.id).toList());
        String texte = y.saveToString();
        modifie = false;
        Runnable ecrire = () -> {
            try {
                File f = fichier();
                f.getParentFile().mkdirs();
                File tmp = new File(f.getParentFile(), f.getName() + ".tmp");
                Files.writeString(tmp.toPath(), texte, StandardCharsets.UTF_8);
                Files.move(tmp.toPath(), f.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                modifie = true;
                plugin.getLogger().severe("Impossible de sauver enchants-outils.yml : " + ex);
            }
        };
        if (async) Bukkit.getScheduler().runTaskAsynchronously(plugin, ecrire); else ecrire.run();
    }
}
