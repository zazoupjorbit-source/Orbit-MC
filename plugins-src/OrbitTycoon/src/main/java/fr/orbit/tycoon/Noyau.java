package fr.orbit.tycoon;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Noyau Orbital : balise posée au nord du pad de chaque île (/is create). Clic droit = menu.
 * - Niveau du Noyau = palier de l'île (taille de la zone, emplacements de générateurs).
 *   Pour monter : déposer les objets des générateurs demandés (noyau.objets) + payer les Crédits (iles.couts).
 *   Dépôts partagés entre les membres ; seul un Officier ou le Chef lance l'amélioration.
 * - Chaque niveau débloque des enchantements de l'Outil orbital (plafonds : noyau.enchants), achetés en Crédits
 *   par chaque joueur. Effet réel = min(niveau acheté, plafond du Noyau de son île).
 * Données : dépôts dans iles.yml, enchantements dans enchants.yml.
 */
final class Noyau implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    /** Position du Noyau par rapport au centre de l'île (au nord du pad d'arrivée). */
    static final int DX = 0, DZ = -7;

    // Menu principal (6 rangées).
    private static final int SLOT_INFO = 4, SLOT_CREDITS = 40, SLOT_TOUT = 47, SLOT_AMELIORER = 49,
            SLOT_ENCHANTS = 51, SLOT_FERMER = 53;
    private static final int[] SLOTS_OBJETS = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
    // Menu des enchantements (4 rangées).
    private static final int SLOT_E_INFO = 4, SLOT_E_RETOUR = 27, SLOT_E_FERMER = 35;
    private static final int[] SLOTS_ENCHANTS = {11, 12, 13, 14, 15};

    /** Enchantements de l'Outil orbital. effet = valeur par niveau (en %), lue dans enchants.<id>.effet. */
    enum Enchant {
        FORTUNE("fortune", "Fortune orbitale", Material.GOLD_INGOT, "#E0B864", 5, 50_000, 1.7, "+%s %% de Crédits orbitaux"),
        ECHO("echo", "Écho stellaire", Material.PRISMARINE_CRYSTALS, "#62CFF4", 5, 40_000, 1.7, "+%s %% de Flux stellaire"),
        SAVOIR("savoir", "Savoir ancien", Material.EXPERIENCE_BOTTLE, "#3DDCB0", 5, 40_000, 1.7, "+%s %% d'XP d'outil"),
        RAFALE("rafale", "Rafale", Material.FEATHER, "#B0E0FF", 2, 150_000, 1.9, "%s %% de chance de récolte ×2"),
        METEORE("meteore", "Météore", Material.FIRE_CHARGE, "#FF9F43", 0.5, 500_000, 2.0, "%s %% de chance de Crédits ×10");

        final String id, nom, couleur, description;
        final Material icone;
        final double effetDefaut, multDefaut;
        final long prixDefaut;

        Enchant(String id, String nom, Material icone, String couleur, double effet, long prix, double mult, String description) {
            this.id = id; this.nom = nom; this.icone = icone; this.couleur = couleur;
            this.effetDefaut = effet; this.prixDefaut = prix; this.multDefaut = mult; this.description = description;
        }
    }

    /** Bonus d'une récolte (tirages de Rafale et Météore déjà faits). */
    record Bonus(double multCredit, double multFlux, double multXp, boolean rafale, boolean meteore, int multMeteore) {
        static final Bonus AUCUN = new Bonus(1, 1, 1, false, false, 1);
    }

    private static final class MenuNoyau implements InventoryHolder {
        final int ile;
        Inventory inv;
        MenuNoyau(int ile) { this.ile = ile; }
        @Override public Inventory getInventory() { return inv; }
    }

    private static final class MenuEnchants implements InventoryHolder {
        final int ile;
        Inventory inv;
        MenuEnchants(int ile) { this.ile = ile; }
        @Override public Inventory getInventory() { return inv; }
    }

    private final OrbitTycoon plugin;
    private final NamespacedKey cleHolo;
    private final Map<UUID, EnumMap<Enchant, Integer>> enchants = new ConcurrentHashMap<>();
    private BukkitTask particules;

    Noyau(OrbitTycoon plugin) {
        this.plugin = plugin;
        this.cleHolo = new NamespacedKey(plugin, "noyau_holo");
    }

    void demarrer() {
        charger();
        particules = Bukkit.getScheduler().runTaskTimer(plugin, this::ambiance, 40L, 40L);
    }

    void arreter() {
        if (particules != null) particules.cancel();
        ecrire(fichier(), texteSauvegarde());
    }

    private IlesJoueurs iles() { return plugin.ilesJoueurs(); }

    private static World monde() { return Bukkit.getWorld(IlesJoueurs.NOM_MONDE); }

    // ------------------------------------------------------------------ bloc

    Location position(IlesJoueurs.Ile ile) {
        return new Location(monde(), ile.cx() + DX, IlesJoueurs.Y + 1, ile.cz() + DZ);
    }

    /** Pose le Noyau : socle 3 × 3 en or (sous la balise, pour allumer son faisceau), balise, hologramme. */
    void poser(IlesJoueurs.Ile ile) {
        World w = monde();
        if (w == null) return;
        int x = ile.cx() + DX, y = IlesJoueurs.Y, z = ile.cz() + DZ;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            w.getBlockAt(x + dx, y, z + dz).setType(dx == 0 && dz == 0 ? Material.DIAMOND_BLOCK : Material.GOLD_BLOCK, false);
            Block dessus = w.getBlockAt(x + dx, y + 1, z + dz);
            if ((dx != 0 || dz != 0) && !dessus.getType().isSolid()) dessus.setType(Material.AIR, false);
        }
        w.getBlockAt(x, y + 1, z).setType(Material.BEACON, false);
        holo(ile);
    }

    /** Île créée avant le Noyau : le pose si sa place est libre. */
    void assurer(IlesJoueurs.Ile ile) {
        World w = monde();
        if (w == null) return;
        Block b = position(ile).getBlock();
        if (b.getType() == Material.BEACON) return;
        if (b.getType().isSolid()) {
            plugin.getLogger().warning("Noyau Orbital de l'île " + ile.index + " : place occupée en " + b.getX() + " " + b.getY() + " " + b.getZ());
            return;
        }
        poser(ile);
    }

    /** (Re)crée l'hologramme au-dessus du Noyau. */
    private void holo(IlesJoueurs.Ile ile) {
        Location l = position(ile).add(0.5, 1.6, 0.5);
        for (Entity e : l.getWorld().getNearbyEntities(l, 1.5, 2, 1.5))
            if (e.getPersistentDataContainer().has(cleHolo, PersistentDataType.BYTE)) e.remove();
        l.getWorld().spawn(l, TextDisplay.class, t -> {
            t.text(texteHolo(ile));
            t.setBillboard(Display.Billboard.CENTER);
            t.setBackgroundColor(Color.fromARGB(90, 5, 15, 40));
            t.setShadowed(true);
            t.setPersistent(true);
            t.getPersistentDataContainer().set(cleHolo, PersistentDataType.BYTE, (byte) 1);
        });
    }

    private net.kyori.adventure.text.Component texteHolo(IlesJoueurs.Ile ile) {
        return MM.deserialize("<gradient:#62CFF4:#E0B864><bold>Noyau Orbital</bold></gradient>\n<gray>Niveau <#E0B864>"
                + (ile.palier + 1) + "</#E0B864> / " + iles().tailles().size() + "\n<dark_gray>Clic droit");
    }

    /** Remet à jour le texte des hologrammes de Noyau quand leur zone se charge (ex. après un changement du nombre de niveaux). */
    @EventHandler
    public void holoCharge(org.bukkit.event.world.EntitiesLoadEvent e) {
        if (!IlesJoueurs.estMonde(e.getWorld())) return;
        for (Entity en : e.getEntities()) {
            if (!(en instanceof TextDisplay t) || !t.getPersistentDataContainer().has(cleHolo, PersistentDataType.BYTE)) continue;
            IlesJoueurs.Ile ile = iles().ileA(t.getLocation().getBlockX(), t.getLocation().getBlockZ());
            if (ile != null) t.text(texteHolo(ile));
        }
    }

    /** L'île de ce bloc, si c'est son Noyau (balise) ou son socle. */
    private IlesJoueurs.Ile ileDuNoyau(Block b, boolean socleAussi) {
        if (!IlesJoueurs.estMonde(b.getWorld())) return null;
        IlesJoueurs.Ile ile = iles().ileA(b.getX(), b.getZ());
        if (ile == null) return null;
        int x = ile.cx() + DX, z = ile.cz() + DZ;
        if (b.getX() == x && b.getZ() == z && b.getY() == IlesJoueurs.Y + 1 && b.getType() == Material.BEACON) return ile;
        if (socleAussi && b.getY() == IlesJoueurs.Y && Math.abs(b.getX() - x) <= 1 && Math.abs(b.getZ() - z) <= 1
                && position(ile).getBlock().getType() == Material.BEACON) return ile;
        return null;
    }

    /** Petites particules autour des Noyaux près des joueurs. */
    private void ambiance() {
        World w = monde();
        if (w == null || w.getPlayers().isEmpty()) return;
        Set<Integer> faits = new HashSet<>();
        double t = (System.currentTimeMillis() / 1000.0) % 360;
        for (Player p : w.getPlayers()) {
            IlesJoueurs.Ile ile = iles().ileA(p.getX(), p.getZ());
            if (ile == null || !faits.add(ile.index)) continue;
            Location c = position(ile).add(0.5, 0.5, 0.5);
            if (c.distanceSquared(p.getLocation()) > 48 * 48 || c.getBlock().getType() != Material.BEACON) continue;
            for (int i = 0; i < 6; i++) {
                double a = t * 2 + i * Math.PI / 3;
                w.spawnParticle(Particle.END_ROD, c.clone().add(Math.cos(a) * 1.3, 0.3 + (i % 3) * 0.4, Math.sin(a) * 1.3), 1, 0, 0, 0, 0);
            }
            w.spawnParticle(Particle.ENCHANT, c.clone().add(0, 1, 0), 12, 0.5, 0.5, 0.5, 0.6);
        }
    }

    // ------------------------------------------------------------------ niveaux et ressources

    /** Objets à déposer pour passer du palier {@code palier} au suivant (id de générateur -> nombre). */
    Map<String, Long> objetsRequis(int palier) {
        Map<String, Long> r = new LinkedHashMap<>();
        List<Map<?, ?>> l = plugin.getConfig().getMapList("noyau.objets");
        if (palier < 0 || palier >= l.size()) return r;
        for (var e : l.get(palier).entrySet()) {
            String id = String.valueOf(e.getKey()).toLowerCase(java.util.Locale.ROOT);
            long n;
            try { n = Long.parseLong(String.valueOf(e.getValue())); } catch (NumberFormatException ex) { continue; }
            if (n <= 0) continue;
            if (plugin.generateurs().type(id) == null) {
                plugin.getLogger().warning("noyau.objets : générateur inconnu « " + id + " » (ignoré).");
                continue;
            }
            r.put(id, n);
        }
        return r;
    }

    private boolean maximum(IlesJoueurs.Ile ile) { return ile.palier + 1 >= iles().tailles().size(); }

    private boolean objetsComplets(IlesJoueurs.Ile ile) {
        for (var e : objetsRequis(ile.palier).entrySet())
            if (ile.depots.getOrDefault(e.getKey(), 0L) < e.getValue()) return false;
        return true;
    }

    /** Dépose les objets de générateur de l'inventaire (un seul id, ou tous si {@code seul} est null). Renvoie le nombre. */
    private long deposer(Player p, IlesJoueurs.Ile ile, String seul) {
        if (maximum(ile)) return 0;
        Map<String, Long> requis = objetsRequis(ile.palier);
        long total = 0;
        ItemStack[] contenu = p.getInventory().getStorageContents();
        for (int i = 0; i < contenu.length; i++) {
            ItemStack it = contenu[i];
            Generateurs.Type t = plugin.generateurs().typeProduit(it);
            if (t == null || (seul != null && !seul.equals(t.id())) || !requis.containsKey(t.id())) continue;
            long manque = requis.get(t.id()) - ile.depots.getOrDefault(t.id(), 0L);
            if (manque <= 0) continue;
            int pris = (int) Math.min(manque, it.getAmount());
            ile.depots.merge(t.id(), (long) pris, Long::sum);
            total += pris;
            if (pris >= it.getAmount()) p.getInventory().setItem(i, null);
            else it.setAmount(it.getAmount() - pris);
        }
        if (total > 0) iles().sauver();
        return total;
    }

    private void ameliorer(Player p, IlesJoueurs.Ile ile) {
        if (iles().ileAvecRang(p, IlesJoueurs.Rang.OFFICIER) != ile) return;
        if (maximum(ile)) { OrbitTycoon.msg(p, "<#7CFC9A>Le Noyau est déjà au niveau maximal."); return; }
        if (!objetsComplets(ile)) { OrbitTycoon.msg(p, "<#FF4D4D>Il manque encore des ressources dans le Noyau."); return; }
        long prix = iles().cout(ile.palier + 1);
        if (prix == Long.MAX_VALUE) { OrbitTycoon.msg(p, "<#FF4D4D>Prix de ce niveau absent de la config (iles.couts)."); return; }
        if (!plugin.monnaies().retirer(p.getUniqueId(), Monnaies.Monnaie.CREDIT, prix)) {
            OrbitTycoon.msg(p, "<#FF4D4D>Il te faut " + Monnaies.Monnaie.CREDIT.format(prix) + "<#FF4D4D>.");
            return;
        }
        ile.palier++;
        ile.depots.clear();
        iles().sauver();
        holo(ile);
        int t = iles().taille(ile);
        Location c = position(ile).add(0.5, 1, 0.5);
        c.getWorld().spawnParticle(Particle.FIREWORK, c, 120, 0.6, 1.2, 0.6, 0.25);
        c.getWorld().spawnParticle(Particle.END_ROD, c, 60, 2, 0.4, 2, 0.05);
        c.getWorld().playSound(c, Sound.BLOCK_BEACON_POWER_SELECT, 1f, 0.8f);
        iles().annoncer(ile, "<#7CFC9A>Le Noyau Orbital passe au niveau " + (ile.palier + 1) + " !</#7CFC9A><gray> Nouvelle zone : <#E0B864>"
                + t + " × " + t + debloques(ile.palier));
        for (UUID id : ile.membres.keySet()) {
            Player m = Bukkit.getPlayer(id);
            if (m == null) continue;
            m.playSound(m.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.2f);
            m.showTitle(net.kyori.adventure.title.Title.title(
                    MM.deserialize("<gradient:#62CFF4:#E0B864><bold>Noyau niveau " + (ile.palier + 1) + "</bold></gradient>"),
                    MM.deserialize("<gray>Zone " + t + " × " + t)));
        }
        ouvrir(p, ile);
    }

    /** « · Nouveaux enchantements : … » pour le palier atteint (vide si rien de neuf). */
    private String debloques(int palier) {
        List<String> l = new ArrayList<>();
        for (Enchant e : Enchant.values()) {
            int avant = plafond(palier - 1, e), apres = plafond(palier, e);
            if (apres > avant) l.add("<" + e.couleur + ">" + e.nom + " " + apres + "</" + e.couleur + ">");
        }
        return l.isEmpty() ? "" : "<gray> · Enchants débloqués : " + String.join("<gray>, ", l);
    }

    // ------------------------------------------------------------------ enchantements

    private ConfigurationSection cfgEnchant(Enchant e) { return plugin.getConfig().getConfigurationSection("enchants." + e.id); }

    double effet(Enchant e) {
        ConfigurationSection c = cfgEnchant(e);
        return c == null ? e.effetDefaut : c.getDouble("effet", e.effetDefaut);
    }

    /**
     * Prix pour passer du niveau {@code niveau} au suivant, arrondi à la centaine.
     * Jusqu'au niveau « seuil », le prix est multiplié par « multiplicateur » à chaque niveau ; au-delà, par
     * « multiplicateur-apres » (plus doux), pour que les hauts niveaux restent atteignables.
     */
    long prix(Enchant e, int niveau) {
        ConfigurationSection c = cfgEnchant(e);
        long base = c == null ? e.prixDefaut : c.getLong("prix", e.prixDefaut);
        double mult = Math.max(1, c == null ? e.multDefaut : c.getDouble("multiplicateur", e.multDefaut));
        int seuil = c == null ? Integer.MAX_VALUE : c.getInt("seuil", Integer.MAX_VALUE);
        double apres = Math.max(1, c == null ? mult : c.getDouble("multiplicateur-apres", mult));
        int avant = Math.min(niveau, Math.max(0, seuil - 1)), enPlus = Math.max(0, niveau - avant);
        long p = Math.round(base * Math.pow(mult, avant) * Math.pow(apres, enPlus));
        return (p + 99) / 100 * 100;
    }

    /** Niveau max de l'enchantement autorisé par un Noyau au palier donné (noyau.enchants). */
    int plafond(int palier, Enchant e) {
        if (palier < 0) return 0;
        List<Map<?, ?>> l = plugin.getConfig().getMapList("noyau.enchants");
        if (l.isEmpty()) return 0;
        Object v = l.get(Math.min(palier, l.size() - 1)).get(e.id);
        try { return v == null ? 0 : Math.max(0, Integer.parseInt(String.valueOf(v))); } catch (NumberFormatException ex) { return 0; }
    }

    int achete(UUID id, Enchant e) {
        EnumMap<Enchant, Integer> m = enchants.get(id);
        return m == null ? 0 : m.getOrDefault(e, 0);
    }

    /** Niveau qui agit vraiment : acheté, plafonné par le Noyau de l'île du joueur. */
    int actif(UUID id, Enchant e) {
        IlesJoueurs.Ile ile = iles().ileDe(id);
        return ile == null ? 0 : Math.min(achete(id, e), plafond(ile.palier, e));
    }

    /** {@code multProc} : multiplicateur des chances de Rafale et Météore (pets « Proc de compétence »). */
    Bonus bonus(Player p, double multProc) {
        UUID id = p.getUniqueId();
        if (!enchants.containsKey(id)) return Bonus.AUCUN;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double credit = 1 + actif(id, Enchant.FORTUNE) * effet(Enchant.FORTUNE) / 100.0;
        double flux = 1 + actif(id, Enchant.ECHO) * effet(Enchant.ECHO) / 100.0;
        double xp = 1 + actif(id, Enchant.SAVOIR) * effet(Enchant.SAVOIR) / 100.0;
        boolean rafale = r.nextDouble() * 100 < actif(id, Enchant.RAFALE) * effet(Enchant.RAFALE) * multProc;
        boolean meteore = r.nextDouble() * 100 < actif(id, Enchant.METEORE) * effet(Enchant.METEORE) * multProc;
        int mult = Math.max(2, plugin.getConfig().getInt("enchants.meteore.multiplicateur-credits", 10));
        return new Bonus(credit, flux, xp, rafale, meteore, mult);
    }

    private void acheter(Player p, IlesJoueurs.Ile ile, Enchant e) {
        UUID id = p.getUniqueId();
        int niv = achete(id, e), cap = plafond(ile.palier, e);
        if (niv >= cap) {
            OrbitTycoon.msg(p, cap == 0 ? "<#FF4D4D>Enchantement pas encore débloqué : améliore le Noyau."
                    : "<#FF4D4D>Niveau max pour ce Noyau : améliore-le pour aller plus haut.");
            return;
        }
        long prix = prix(e, niv);
        if (!plugin.monnaies().retirer(id, Monnaies.Monnaie.CREDIT, prix)) {
            OrbitTycoon.msg(p, "<#FF4D4D>Il te faut " + Monnaies.Monnaie.CREDIT.format(prix) + "<#FF4D4D>.");
            return;
        }
        enchants.computeIfAbsent(id, k -> new EnumMap<>(Enchant.class)).put(e, niv + 1);
        sauver();
        p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.2f);
        p.getWorld().spawnParticle(Particle.ENCHANT, p.getLocation().add(0, 1.2, 0), 40, 0.4, 0.6, 0.4, 0.8);
        OrbitTycoon.msg(p, "Outil orbital : <" + e.couleur + ">" + e.nom + " " + (niv + 1) + "</" + e.couleur + "><gray> !");
        ouvrirEnchants(p, ile);
    }

    // ------------------------------------------------------------------ données (enchants.yml)

    private File fichier() { return new File(plugin.getDataFolder(), "enchants.yml"); }

    private void charger() {
        enchants.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(fichier());
        for (String cle : y.getKeys(false)) {
            ConfigurationSection c = y.getConfigurationSection(cle);
            if (c == null) continue;
            UUID id;
            try { id = UUID.fromString(cle); } catch (IllegalArgumentException ex) { continue; }
            EnumMap<Enchant, Integer> m = new EnumMap<>(Enchant.class);
            for (Enchant e : Enchant.values()) if (c.getInt(e.id) > 0) m.put(e, c.getInt(e.id));
            if (!m.isEmpty()) enchants.put(id, m);
        }
    }

    private String texteSauvegarde() {
        YamlConfiguration y = new YamlConfiguration();
        enchants.forEach((id, m) -> m.forEach((e, n) -> y.set(id + "." + e.id, n)));
        return y.saveToString();
    }

    private void sauver() {
        String t = texteSauvegarde();
        File f = fichier();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> ecrire(f, t));
    }

    private synchronized void ecrire(File f, String texte) {
        try {
            f.getParentFile().mkdirs();
            File tmp = new File(f.getParentFile(), f.getName() + ".tmp");
            Files.writeString(tmp.toPath(), texte, StandardCharsets.UTF_8);
            Files.move(tmp.toPath(), f.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            plugin.getLogger().severe("Impossible d'écrire enchants.yml : " + e);
        }
    }

    // ------------------------------------------------------------------ menus

    void ouvrir(Player p, IlesJoueurs.Ile ile) {
        MenuNoyau h = new MenuNoyau(ile.index);
        Inventory inv = Bukkit.createInventory(h, 54, Habillage.titre(plugin, p, 6, "Noyau Orbital"));
        h.inv = inv;
        int t = iles().taille(ile), max = iles().tailles().size();
        List<Integer> emplacements = plugin.getConfig().getIntegerList("generateurs.emplacements");
        int gens = emplacements.isEmpty() ? 0 : emplacements.get(Math.min(ile.palier, emplacements.size() - 1));
        List<String> info = new ArrayList<>(List.of(
                "<gray>Niveau <#E0B864>" + (ile.palier + 1) + "</#E0B864> / " + max,
                "<gray>Zone de l'île : <#E0B864>" + t + " × " + t,
                "<gray>Emplacements de générateurs : <#E0B864>" + gens,
                "", "<#62CFF4>Enchantements débloqués :"));
        boolean un = false;
        for (Enchant e : Enchant.values()) {
            int cap = plafond(ile.palier, e);
            if (cap <= 0) continue;
            info.add("<dark_gray> • <" + e.couleur + ">" + e.nom + " <gray>jusqu'au niveau <white>" + cap);
            un = true;
        }
        if (!un) info.add("<dark_gray> • aucun");
        inv.setItem(SLOT_INFO, objet(Material.BEACON, "<gradient:#62CFF4:#E0B864><bold>Noyau Orbital</bold></gradient>", true,
                info.toArray(String[]::new)));

        if (maximum(ile)) {
            inv.setItem(22, objet(Material.NETHER_STAR, "<#7CFC9A><bold>Niveau maximal atteint", true,
                    "<gray>Ton Noyau rayonne à pleine puissance."));
        } else {
            Map<String, Long> requis = objetsRequis(ile.palier);
            int i = 0;
            for (var e : requis.entrySet()) {
                if (i >= SLOTS_OBJETS.length) break;
                Generateurs.Type ty = plugin.generateurs().type(e.getKey());
                long d = Math.min(ile.depots.getOrDefault(e.getKey(), 0L), e.getValue());
                boolean ok = d >= e.getValue();
                long dansSac = 0;
                for (ItemStack it : p.getInventory().getStorageContents()) {
                    Generateurs.Type x = plugin.generateurs().typeProduit(it);
                    if (x != null && x.id().equals(e.getKey())) dansSac += it.getAmount();
                }
                inv.setItem(SLOTS_OBJETS[i++], objet(ty.objet(), (ok ? "<#7CFC9A>✔ " : "<white>") + ty.nom(), ok,
                        "<gray>Déposé : " + (ok ? "<#7CFC9A>" : "<#E0B864>") + Hud.nombre(d) + "<gray> / " + Hud.nombre(e.getValue()),
                        barre(d, e.getValue()),
                        "<gray>Dans ton inventaire : <white>" + Hud.nombre(dansSac),
                        "",
                        ok ? "<#7CFC9A>Complet" : "<#62CFF4>➜ Clic : déposer"));
            }
            long prix = iles().cout(ile.palier + 1);
            long solde = plugin.monnaies().solde(p.getUniqueId(), Monnaies.Monnaie.CREDIT);
            inv.setItem(SLOT_CREDITS, objet(Material.GOLD_NUGGET, "<#E0B864><bold>Crédits orbitaux", false,
                    "<gray>Coût : " + (solde >= prix ? "<#E0B864>" : "<#FF4D4D>") + Hud.nombre(prix) + " ⛃",
                    "<gray>Ton solde : <white>" + Hud.nombre(solde) + " ⛃",
                    "<dark_gray>Payés par celui qui lance l'amélioration."));
            inv.setItem(SLOT_TOUT, objet(Material.HOPPER, "<#62CFF4><bold>Tout déposer", false,
                    "<gray>Verse dans le Noyau toutes les ressources",
                    "<gray>de générateur demandées de ton inventaire."));
            boolean pret = objetsComplets(ile);
            int suivante = iles().tailles().get(ile.palier + 1);
            inv.setItem(SLOT_AMELIORER, objet(pret ? Material.NETHER_STAR : Material.GRAY_DYE,
                    (pret ? "<#7CFC9A>" : "<gray>") + "<bold>Améliorer → niveau " + (ile.palier + 2), pret,
                    "<gray>Nouvelle zone : <#E0B864>" + suivante + " × " + suivante,
                    debloques(ile.palier + 1).replace("<gray> · Enchants débloqués : ", "<gray>Débloque : "),
                    "",
                    pret ? (solde >= prix ? "<#7CFC9A>➜ Clic : améliorer (Officier ou Chef)" : "<#FF4D4D>Crédits insuffisants")
                            : "<#FF4D4D>Ressources incomplètes"));
        }
        inv.setItem(SLOT_ENCHANTS, objet(Material.ENCHANTED_BOOK, "<#62CFF4><bold>Enchantements de l'outil", true,
                "<gray>Améliore ton <#E0B864>Outil orbital</#E0B864><gray>.",
                "<gray>Le niveau du Noyau fixe le maximum.", "", "<#62CFF4>➜ Clic : ouvrir"));
        inv.setItem(SLOT_FERMER, objet(Material.BARRIER, "<#FF4D4D>Fermer", false));
        Habillage.fond(plugin, p, inv);
        p.openInventory(inv);
    }

    private void ouvrirEnchants(Player p, IlesJoueurs.Ile ile) {
        MenuEnchants h = new MenuEnchants(ile.index);
        Inventory inv = Bukkit.createInventory(h, 36, Habillage.titre(plugin, p, 4, "Enchantements"));
        h.inv = inv;
        UUID id = p.getUniqueId();
        long solde = plugin.monnaies().solde(id, Monnaies.Monnaie.CREDIT);
        inv.setItem(SLOT_E_INFO, objet(Material.NETHERITE_PICKAXE, "<gradient:#62CFF4:#E0B864><bold>Outil orbital</bold></gradient>", true,
                "<gray>Ces enchantements agissent sur toutes",
                "<gray>tes récoltes, quel que soit l'outil choisi.",
                "<gray>Solde : <#E0B864>" + Hud.nombre(solde) + " ⛃"));
        Enchant[] tous = Enchant.values();
        for (int i = 0; i < tous.length && i < SLOTS_ENCHANTS.length; i++) {
            Enchant e = tous[i];
            int niv = achete(id, e), cap = plafond(ile.palier, e);
            List<String> l = new ArrayList<>();
            l.add("<gray>Niveau : <white>" + niv + "<gray> / " + cap + " <dark_gray>(Noyau niv. " + (ile.palier + 1) + ")");
            l.add("<gray>Effet : <" + e.couleur + ">" + String.format(e.description, joli(niv * effet(e))));
            l.add("");
            if (cap == 0) {
                int palier = premierPalier(e);
                l.add(palier < 0 ? "<#FF4D4D>Pas encore disponible" : "<#FF4D4D>Débloqué au niveau " + (palier + 1) + " du Noyau");
            } else if (niv >= cap) {
                l.add("<#7CFC9A>Maximum de ce Noyau atteint");
            } else {
                long prix = prix(e, niv);
                l.add("<gray>Suivant : <" + e.couleur + ">" + String.format(e.description, joli((niv + 1) * effet(e))));
                l.add("<gray>Prix : " + (solde >= prix ? "<#E0B864>" : "<#FF4D4D>") + Hud.nombre(prix) + " ⛃");
                l.add("<#62CFF4>➜ Clic : acheter le niveau " + (niv + 1));
            }
            ItemStack it = objet(e.icone, "<" + e.couleur + "><bold>" + e.nom + (niv > 0 ? " " + niv : "") + (cap == 0 ? " <dark_gray>🔒" : ""),
                    niv > 0, l.toArray(String[]::new));
            // Icône dessinée du pack (tools/gen-enchants.ps1), en couleur même verrouillé ; sans le pack, l'objet de base reste affiché.
            String modele = "ench_" + e.id;
            it.editMeta(m -> m.setItemModel(new org.bukkit.NamespacedKey("orbittycoon", modele)));
            if (niv > 0) it.setAmount(Math.min(64, niv));
            inv.setItem(SLOTS_ENCHANTS[i], it);
        }
        inv.setItem(SLOT_E_RETOUR, objet(Material.ARROW, "<#62CFF4>← Retour", false, "<gray>Revenir au Noyau."));
        inv.setItem(SLOT_E_FERMER, objet(Material.BARRIER, "<#FF4D4D>Fermer", false));
        Habillage.fond(plugin, p, inv);
        p.openInventory(inv);
    }

    private int premierPalier(Enchant e) {
        int n = plugin.getConfig().getMapList("noyau.enchants").size();
        for (int i = 0; i < n; i++) if (plafond(i, e) > 0) return i;
        return -1;
    }

    private static String joli(double v) {
        return v == Math.rint(v) ? Long.toString((long) v) : String.format(java.util.Locale.FRANCE, "%.1f", v);
    }

    private static String barre(long n, long max) {
        int plein = max <= 0 ? 20 : (int) Math.min(20, n * 20 / max);
        return "<#62CFF4>" + "|".repeat(plein) + "<dark_gray>" + "|".repeat(20 - plein);
    }

    private static ItemStack objet(Material m, String nom, boolean brille, String... lignes) {
        ItemStack it = new ItemStack(m);
        it.editMeta(meta -> {
            meta.displayName(texte(nom));
            List<Component> lore = new ArrayList<>();
            for (String l : lignes) lore.add(texte(l));
            if (!lore.isEmpty()) meta.lore(lore);
            if (brille) meta.setEnchantmentGlintOverride(true);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES, org.bukkit.inventory.ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        });
        return it;
    }

    private static Component texte(String mm) { return MM.deserialize(mm).decoration(TextDecoration.ITALIC, false); }

    // ------------------------------------------------------------------ événements

    @EventHandler(priority = EventPriority.HIGHEST)
    public void clicBloc(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND || e.getClickedBlock() == null) return;
        IlesJoueurs.Ile ile = ileDuNoyau(e.getClickedBlock(), false);
        if (ile == null) return;
        e.setCancelled(true);   // jamais le menu de balise vanilla
        Player p = e.getPlayer();
        if (!ile.membres.containsKey(p.getUniqueId())) {
            OrbitTycoon.msg(p, "Noyau Orbital de <white>" + MM.escapeTags(ile.nom) + "</white><gray> : niveau <#E0B864>" + (ile.palier + 1) + "</#E0B864><gray>.");
            return;
        }
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_AMBIENT, 0.8f, 1.4f);
        ouvrir(p, ile);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void clicMenu(InventoryClickEvent e) {
        InventoryHolder holder = e.getView().getTopInventory().getHolder();
        if (!(holder instanceof MenuNoyau) && !(holder instanceof MenuEnchants)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getView().getTopInventory()) return;
        int index = holder instanceof MenuNoyau m ? m.ile : ((MenuEnchants) holder).ile;
        IlesJoueurs.Ile ile = iles().ileDe(p.getUniqueId());
        if (ile == null || ile.index != index) { p.closeInventory(); return; }
        int slot = e.getSlot();
        if (holder instanceof MenuNoyau) {
            if (slot == SLOT_FERMER) { p.closeInventory(); return; }
            if (slot == SLOT_ENCHANTS) { p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f); ouvrirEnchants(p, ile); return; }
            if (slot == SLOT_AMELIORER) { ameliorer(p, ile); return; }
            String seul = null;
            boolean depot = slot == SLOT_TOUT;
            if (!depot) {
                int i = 0;
                for (String id : objetsRequis(ile.palier).keySet()) {
                    if (i < SLOTS_OBJETS.length && SLOTS_OBJETS[i] == slot) { seul = id; depot = true; break; }
                    i++;
                }
            }
            if (!depot) return;
            long n = deposer(p, ile, seul);
            if (n > 0) {
                p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.3f);
                OrbitTycoon.msg(p, "<#62CFF4>" + Hud.nombre(n) + "</#62CFF4><gray> ressource(s) déposée(s) dans le Noyau.");
                if (objetsComplets(ile)) iles().annoncer(ile, "<#7CFC9A>Toutes les ressources sont dans le Noyau Orbital !</#7CFC9A><gray> Un Officier ou le Chef peut l'améliorer.");
            } else {
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.8f);
                OrbitTycoon.msg(p, "<#FF4D4D>Rien à déposer : il faut les objets produits par tes générateurs.");
            }
            ouvrir(p, ile);
            return;
        }
        if (slot == SLOT_E_FERMER) { p.closeInventory(); return; }
        if (slot == SLOT_E_RETOUR) { ouvrir(p, ile); return; }
        Enchant[] tous = Enchant.values();
        for (int i = 0; i < tous.length && i < SLOTS_ENCHANTS.length; i++)
            if (SLOTS_ENCHANTS[i] == slot) { acheter(p, ile, tous[i]); return; }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void glisser(InventoryDragEvent e) {
        InventoryHolder holder = e.getView().getTopInventory().getHolder();
        if (holder instanceof MenuNoyau || holder instanceof MenuEnchants) e.setCancelled(true);
    }

    /** Le Noyau et son socle sont incassables. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void casser(BlockBreakEvent e) {
        if (ileDuNoyau(e.getBlock(), true) == null) return;
        e.setCancelled(true);
        e.getPlayer().sendActionBar(MM.deserialize("<#FF4D4D>Le Noyau Orbital ne peut pas être détruit."));
    }

    @EventHandler(ignoreCancelled = true)
    public void explosion(EntityExplodeEvent e) {
        if (IlesJoueurs.estMonde(e.getLocation().getWorld())) e.blockList().removeIf(b -> ileDuNoyau(b, true) != null);
    }

    @EventHandler(ignoreCancelled = true)
    public void piston(BlockPistonExtendEvent e) {
        if (IlesJoueurs.estMonde(e.getBlock().getWorld()) && e.getBlocks().stream().anyMatch(b -> ileDuNoyau(b, true) != null)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void pistonRetour(BlockPistonRetractEvent e) {
        if (IlesJoueurs.estMonde(e.getBlock().getWorld()) && e.getBlocks().stream().anyMatch(b -> ileDuNoyau(b, true) != null)) e.setCancelled(true);
    }
}
