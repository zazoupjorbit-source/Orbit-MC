package fr.orbit.tycoon;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
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
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static fr.orbit.tycoon.Navigateur.texte;
import static fr.orbit.tycoon.Progression.objet;

/**
 * Générateurs de ressources des îles /is : un bloc (botte de foin = blé, bloc de fer = fer…) qui fait apparaître
 * sa ressource juste au-dessus. Les générateurs s'empilent par 64 dans l'inventaire et jusqu'à « pile-max » sur un
 * même bloc posé (clic droit avec le même générateur en main ; Shift = toute la pile). Achat dans le menu /gen,
 * débloqués dans l'ordre (poser le niveau N débloque N+1 pour toute l'île). Produits vendus par /vendre.
 * Données : plugins/OrbitTycoon/generateurs.yml (écrit hors du thread principal, fichier temporaire puis renommage).
 */
final class Generateurs implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String TAG = "orbit_generateur";
    private static final int SLOT_INFO = 45, SLOT_COFFRE = 46, SLOT_COLLECTEUR = 47, SLOT_VENDRE = 49, SLOT_FERMER = 53;

    /** Un type de générateur (une ligne de generateurs.liste). */
    record Type(int rang, String id, String nom, Material bloc, Material objet, long prix, double vente) { }

    /** Un générateur posé : type, nombre empilé, île. */
    static final class Pose {
        final String type;
        int pile;
        final int ile;
        Pose(String type, int pile, int ile) { this.type = type; this.pile = pile; this.ile = ile; }
    }

    private final OrbitTycoon plugin;
    private final NamespacedKey cleGen, cleProduit;
    private final List<Type> types = new ArrayList<>();
    private final Map<String, Type> parId = new HashMap<>();
    /** Clé « x,y,z » (monde des îles) → générateur. */
    private final Map<String, Pose> poses = new LinkedHashMap<>();
    /** Île → rang du dernier type débloqué (0 = blé seulement). */
    private final Map<Integer, Integer> debloque = new HashMap<>();
    private final Map<String, TextDisplay> titres = new HashMap<>();
    /** Centimes de Crédit pas encore versés (ventes de 1,12 ⛃ l'unité…), gardés en mémoire. */
    private final Map<UUID, Double> restes = new HashMap<>();
    private final ExecutorService ecriture = Executors.newSingleThreadExecutor(r -> new Thread(r, "OrbitTycoon-generateurs"));
    private BukkitTask production;

    Generateurs(OrbitTycoon plugin) {
        this.plugin = plugin;
        cleGen = new NamespacedKey(plugin, "generateur");
        cleProduit = new NamespacedKey(plugin, "produit");
    }

    // ------------------------------------------------------------------ cycle de vie

    void demarrer() {
        charger();
        lire();
        relancer();
        World w = monde();
        if (w != null) for (String k : poses.keySet()) {
            int[] c = coords(k);
            if (w.isChunkLoaded(c[0] >> 4, c[2] >> 4)) titre(k);
        }
    }

    /** (Re)lit la liste des types dans config.yml (aussi au /tycoon reload). */
    void charger() {
        types.clear();
        parId.clear();
        List<Map<?, ?>> liste = plugin.getConfig().getMapList("generateurs.liste");
        for (Map<?, ?> m : liste) {
            try {
                String id = String.valueOf(m.get("id")).toLowerCase();
                Material bloc = Material.matchMaterial(String.valueOf(m.get("bloc")));
                Material obj = Material.matchMaterial(String.valueOf(m.get("objet")));
                if (bloc == null || !bloc.isBlock() || obj == null || !obj.isItem() || parId.containsKey(id)) {
                    plugin.getLogger().warning("Générateur ignoré (bloc/objet invalide ou id en double) : " + m);
                    continue;
                }
                Object nom = m.get("nom");
                Type t = new Type(types.size(), id, nom == null ? id : String.valueOf(nom), bloc, obj,
                        Math.max(0, ((Number) m.get("prix")).longValue()), Math.max(0, ((Number) m.get("vente")).doubleValue()));
                types.add(t);
                parId.put(id, t);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Générateur ignoré (ligne mal écrite) : " + m + " (" + e.getMessage() + ")");
            }
        }
        if (production != null) relancer();
        for (String k : new ArrayList<>(titres.keySet())) titre(k);
    }

    private void relancer() {
        if (production != null) production.cancel();
        long t = Math.max(5, plugin.getConfig().getLong("generateurs.intervalle-secondes", 30)) * 20L;
        production = Bukkit.getScheduler().runTaskTimer(plugin, this::produire, t, t);
    }

    void arreter() {
        if (production != null) production.cancel();
        for (TextDisplay d : titres.values()) if (d.isValid()) d.remove();
        titres.clear();
        String texte = texteSauvegarde();
        ecriture.shutdown();
        ecrire(texte);
    }

    private static World monde() { return Bukkit.getWorld(IlesJoueurs.NOM_MONDE); }

    private int pileMax() { return Math.max(1, Math.min(64, plugin.getConfig().getInt("generateurs.pile-max", 64))); }

    private int intervalle() { return (int) Math.max(5, plugin.getConfig().getLong("generateurs.intervalle-secondes", 30)); }

    // ------------------------------------------------------------------ sauvegarde

    private File fichier() { return new File(plugin.getDataFolder(), "generateurs.yml"); }

    private void lire() {
        poses.clear();
        debloque.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(fichier());
        ConfigurationSection d = y.getConfigurationSection("debloque");
        if (d != null) for (String k : d.getKeys(false)) debloque.put(Integer.parseInt(k), d.getInt(k));
        ConfigurationSection g = y.getConfigurationSection("poses");
        if (g != null) for (String k : g.getKeys(false)) {
            ConfigurationSection s = g.getConfigurationSection(k);
            if (s == null) continue;
            poses.put(k.replace('_', ','), new Pose(s.getString("type", "ble"), Math.max(1, s.getInt("pile", 1)), s.getInt("ile", -1)));
        }
    }

    private String texteSauvegarde() {
        YamlConfiguration y = new YamlConfiguration();
        for (var e : debloque.entrySet()) y.set("debloque." + e.getKey(), e.getValue());
        for (var e : poses.entrySet()) {
            String k = "poses." + e.getKey().replace(',', '_');   // les « . » et « , » ne vont pas dans une clé YAML de Bukkit
            y.set(k + ".type", e.getValue().type);
            y.set(k + ".pile", e.getValue().pile);
            y.set(k + ".ile", e.getValue().ile);
        }
        return y.saveToString();
    }

    private void sauver() {
        String texte = texteSauvegarde();
        if (ecriture.isShutdown()) { ecrire(texte); return; }
        ecriture.execute(() -> ecrire(texte));
    }

    private synchronized void ecrire(String texte) {
        File f = fichier(), tmp = new File(plugin.getDataFolder(), "generateurs.yml.tmp");
        try {
            f.getParentFile().mkdirs();
            Files.writeString(tmp.toPath(), texte, StandardCharsets.UTF_8);
            Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            plugin.getLogger().severe("Générateurs : sauvegarde impossible : " + e);
        }
    }

    // ------------------------------------------------------------------ objets

    private static String cle(Block b) { return b.getX() + "," + b.getY() + "," + b.getZ(); }

    private static int[] coords(String k) {
        String[] p = k.split(",");
        return new int[]{Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])};
    }

    private String gain(Type t, int pile) {
        double parHeure = t.vente() * pile * 3600.0 / intervalle();
        return Hud.nombre(Math.round(parHeure)) + " ⛃/h";
    }

    /** Objet générateur (s'empile par 64 : tous les exemplaires ont exactement les mêmes données). */
    ItemStack generateur(Type t, int nombre) {
        ItemStack it = new ItemStack(t.bloc(), Math.max(1, Math.min(64, nombre)));
        it.editMeta(m -> {
            m.displayName(texte("<gradient:#62CFF4:#E0B864><bold>Générateur de " + t.nom() + "</bold></gradient>"));
            m.lore(List.of(
                    texte("<gray>Produit : <white>1 " + t.nom() + " <gray>toutes les <white>" + intervalle() + " s"),
                    texte("<gray>Vente : <#E0B864>" + vente(t.vente()) + " ⛃ <gray>l'unité <dark_gray>(" + gain(t, 1) + ")"),
                    texte(""),
                    texte("<#62CFF4>➜ Pose-le sur ton île /is"),
                    texte("<#62CFF4>➜ Clic droit sur un générateur pareil : l'empiler <dark_gray>(x" + pileMax() + " max)"),
                    texte("<dark_gray>Shift + clic droit : toute la pile")));
            m.getPersistentDataContainer().set(cleGen, PersistentDataType.STRING, t.id());
        });
        return it;
    }

    /** Ressource produite (marquée : seule une ressource de générateur se vend avec /vendre). */
    ItemStack produit(Type t, int nombre) {
        ItemStack it = new ItemStack(t.objet(), nombre);
        it.editMeta(m -> {
            m.lore(List.of(texte("<dark_gray>Générateur orbital"),
                    texte("<gray>Vente : <#E0B864>" + vente(t.vente()) + " ⛃ <gray>l'unité <dark_gray>(/vendre)")));
            m.getPersistentDataContainer().set(cleProduit, PersistentDataType.STRING, t.id());
        });
        return it;
    }

    static String vente(double v) {
        return v == Math.floor(v) ? Long.toString((long) v) : String.format(java.util.Locale.FRANCE, "%.2f", v);
    }

    private Type typeGen(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        String id = it.getItemMeta().getPersistentDataContainer().get(cleGen, PersistentDataType.STRING);
        return id == null ? null : parId.get(id);
    }

    Type typeProduit(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        String id = it.getItemMeta().getPersistentDataContainer().get(cleProduit, PersistentDataType.STRING);
        return id == null ? null : parId.get(id);
    }

    private static void donner(Player p, ItemStack it) {
        for (ItemStack reste : p.getInventory().addItem(it).values())
            p.getWorld().dropItem(p.getLocation(), reste);
    }

    private void donnerGenerateurs(Player p, Type t, int nombre) {
        while (nombre > 0) {
            int n = Math.min(64, nombre);
            donner(p, generateur(t, n));
            nombre -= n;
        }
    }

    // ------------------------------------------------------------------ îles

    IlesJoueurs iles() { return plugin.ilesJoueurs(); }

    boolean membre(Player p, IlesJoueurs.Ile ile) {
        return ile != null && ile.membres.containsKey(p.getUniqueId());
    }

    private int emplacementsMax(IlesJoueurs.Ile ile) {
        List<Integer> l = plugin.getConfig().getIntegerList("generateurs.emplacements");
        if (l.isEmpty()) return 10;
        return l.get(Math.min(ile.palier, l.size() - 1));
    }

    private int emplacements(int ile) {
        int n = 0;
        for (Pose g : poses.values()) if (g.ile == ile) n++;
        return n;
    }

    private int rangDebloque(UUID joueur) {
        IlesJoueurs.Ile ile = iles().ileDe(joueur);
        return ile == null ? 0 : debloque.getOrDefault(ile.index, 0);
    }

    // ------------------------------------------------------------------ production

    private void produire() {
        World w = monde();
        if (w == null || poses.isEmpty()) return;
        Set<Integer> actives = new HashSet<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            IlesJoueurs.Ile ile = iles().ileDe(p.getUniqueId());
            if (ile != null) actives.add(ile.index);
        }
        if (actives.isEmpty()) return;
        int maxSol = Math.max(64, plugin.getConfig().getInt("generateurs.max-au-sol", 512));
        for (var e : poses.entrySet()) {
            Pose g = e.getValue();
            if (!actives.contains(g.ile)) continue;
            Type t = parId.get(g.type);
            if (t == null) continue;
            int[] c = coords(e.getKey());
            if (!w.isChunkLoaded(c[0] >> 4, c[2] >> 4)) continue;
            // Un collecteur dans le chunk : la production est vendue (Collecteur orbital) ou rangée (Coffre collecteur),
            // sans objet au sol ; seul ce qui ne rentre pas dans un coffre plein tombe au sol.
            int pile = plugin.collecteurs().absorber(c[0] >> 4, c[2] >> 4, g.ile, t, g.pile);
            if (pile <= 0) continue;
            Location l = new Location(w, c[0] + 0.5, c[1] + 1.15, c[2] + 0.5);
            ItemStack modele = produit(t, 1);
            // On complète d'abord les tas déjà au sol (moins d'entités), dans la limite de max-au-sol.
            int aDonner = pile, auSol = 0;
            List<Item> tas = new ArrayList<>();
            for (Entity en : w.getNearbyEntities(l, 0.8, 0.8, 0.8, x -> x instanceof Item)) {
                Item it = (Item) en;
                if (!it.getItemStack().isSimilar(modele)) continue;
                auSol += it.getItemStack().getAmount();
                tas.add(it);
            }
            aDonner = Math.min(aDonner, maxSol - auSol);
            if (aDonner <= 0) continue;
            for (Item it : tas) {
                if (aDonner <= 0) break;
                ItemStack s = it.getItemStack();
                int place = s.getMaxStackSize() - s.getAmount();
                if (place <= 0) continue;
                int n = Math.min(place, aDonner);
                s.setAmount(s.getAmount() + n);
                it.setItemStack(s);
                aDonner -= n;
            }
            while (aDonner > 0) {
                int n = Math.min(t.objet().getMaxStackSize(), aDonner);
                w.dropItem(l, produit(t, n), it -> it.setVelocity(new org.bukkit.util.Vector()));
                aDonner -= n;
            }
        }
    }

    // ------------------------------------------------------------------ titres au-dessus des générateurs

    private void titre(String k) {
        World w = monde();
        Pose g = poses.get(k);
        TextDisplay ancien = titres.remove(k);
        if (g == null || w == null) { if (ancien != null && ancien.isValid()) ancien.remove(); return; }
        Type t = parId.get(g.type);
        Component texte = MM.deserialize("<#E0B864><bold>" + (t == null ? g.type : t.nom()) + "</bold> <#62CFF4>x" + g.pile
                + (t == null ? "" : "\n<gray>" + gain(t, g.pile)));
        if (ancien != null && ancien.isValid()) { ancien.text(texte); titres.put(k, ancien); return; }
        int[] c = coords(k);
        if (!w.isChunkLoaded(c[0] >> 4, c[2] >> 4)) return;
        TextDisplay d = w.spawn(new Location(w, c[0] + 0.5, c[1] + 1.9, c[2] + 0.5), TextDisplay.class, x -> {
            x.setPersistent(false);   // recréé au chargement du chunk : rien ne traîne dans le monde
            x.addScoreboardTag(TAG);
            x.setBillboard(Display.Billboard.CENTER);
            x.setAlignment(TextDisplay.TextAlignment.CENTER);
            x.setShadowed(true);
            x.text(texte);
        });
        titres.put(k, d);
    }

    @EventHandler
    public void chunk(ChunkLoadEvent e) {
        if (!IlesJoueurs.estMonde(e.getWorld())) return;
        int cx = e.getChunk().getX(), cz = e.getChunk().getZ();
        for (String k : poses.keySet()) {
            int[] c = coords(k);
            if (c[0] >> 4 == cx && c[2] >> 4 == cz) titre(k);
        }
    }

    // ------------------------------------------------------------------ poser, empiler, casser

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void poser(BlockPlaceEvent e) {
        Type t = typeGen(e.getItemInHand());
        if (t == null) return;
        Player p = e.getPlayer();
        Block b = e.getBlockPlaced();
        if (!IlesJoueurs.estMonde(b.getWorld())) { e.setCancelled(true); OrbitTycoon.msg(p, "Un générateur se pose seulement sur ton île <#E0B864>/is<gray>."); return; }
        IlesJoueurs.Ile ile = iles().ileA(b.getX(), b.getZ());
        if (ile == null || !membre(p, ile)) { e.setCancelled(true); OrbitTycoon.msg(p, "Un générateur se pose seulement sur <#E0B864>ton<gray> île."); return; }
        if (emplacements(ile.index) >= emplacementsMax(ile)) {
            e.setCancelled(true);
            OrbitTycoon.msg(p, "<#FF4D4D>Ton île a déjà " + emplacementsMax(ile) + " générateurs posés.</#FF4D4D> <gray>Empile-les (clic droit dessus) ou agrandis l'île avec <#E0B864>/is upgrade<gray>.");
            return;
        }
        String k = cle(b);
        poses.put(k, new Pose(t.id(), 1, ile.index));
        int avant = debloque.getOrDefault(ile.index, 0);
        if (t.rang() + 1 > avant && t.rang() + 1 < types.size()) {
            debloque.put(ile.index, t.rang() + 1);
            OrbitTycoon.msg(p, "<#62CFF4>Nouveau générateur débloqué :</#62CFF4> <#E0B864>" + types.get(t.rang() + 1).nom() + "<gray> (menu <#E0B864>/gen<gray>).");
        }
        sauver();
        Bukkit.getScheduler().runTask(plugin, () -> titre(k));
        p.playSound(b.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.6f);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void clic(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND) return;
        Block b = e.getClickedBlock();
        if (b == null || !IlesJoueurs.estMonde(b.getWorld())) return;
        String k = cle(b);
        Pose g = poses.get(k);
        if (g == null) return;
        Player p = e.getPlayer();
        ItemStack main = p.getInventory().getItemInMainHand();
        Type enMain = typeGen(main);
        // Shift avec un bloc (ou un autre type de générateur) : on le laisse se poser contre le générateur.
        if (p.isSneaking() && (enMain == null ? !main.getType().isAir() : !enMain.id().equals(g.type))) { e.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY); return; }
        e.setCancelled(true);
        Type t = parId.get(g.type);
        IlesJoueurs.Ile ile = iles().ileA(b.getX(), b.getZ());
        if (enMain == null) { info(p, g, t); return; }
        if (!membre(p, ile) && !Zones.constructeur(p)) { OrbitTycoon.msg(p, "<#FF4D4D>Ce générateur n'est pas à ton île."); return; }
        if (t == null || !enMain.id().equals(t.id())) { OrbitTycoon.msg(p, "Il faut un <#E0B864>Générateur de " + (t == null ? g.type : t.nom()) + "<gray> pour l'empiler ici."); return; }
        int place = pileMax() - g.pile;
        if (place <= 0) { OrbitTycoon.msg(p, "Ce générateur est déjà au maximum (<#E0B864>x" + pileMax() + "<gray>)."); return; }
        int n = Math.min(place, p.isSneaking() ? main.getAmount() : 1);
        main.setAmount(main.getAmount() - n);
        g.pile += n;
        sauver();
        titre(k);
        p.playSound(b.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.2f);
        b.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, b.getLocation().add(0.5, 1.1, 0.5), 8, 0.3, 0.2, 0.3, 0);
        p.sendActionBar(texte("<#E0B864>Générateur de " + t.nom() + " <#62CFF4>x" + g.pile + " <dark_gray>· <gray>" + gain(t, g.pile)));
    }

    private void info(Player p, Pose g, Type t) {
        if (t == null) { OrbitTycoon.msg(p, "Générateur inconnu (« " + g.type + " » retiré de la config)."); return; }
        p.sendActionBar(texte("<#E0B864>Générateur de " + t.nom() + " <#62CFF4>x" + g.pile + "<dark_gray> · <gray>" + g.pile
                + " toutes les " + intervalle() + " s <dark_gray>· <gray>" + gain(t, g.pile)));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void casser(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (!IlesJoueurs.estMonde(b.getWorld())) return;
        String k = cle(b);
        Pose g = poses.get(k);
        if (g == null) return;
        Player p = e.getPlayer();
        IlesJoueurs.Ile ile = iles().ileA(b.getX(), b.getZ());
        if (!membre(p, ile) && !Zones.constructeur(p)) { e.setCancelled(true); return; }
        e.setDropItems(false);
        e.setExpToDrop(0);
        poses.remove(k);
        sauver();
        titre(k);
        Type t = parId.get(g.type);
        if (t != null) {
            donnerGenerateurs(p, t, g.pile);
            OrbitTycoon.msg(p, "Générateur de <#E0B864>" + t.nom() + " x" + g.pile + "<gray> récupéré.");
        }
    }

    // Le bloc ne doit jamais bouger ni disparaître sans passer par casser() : sinon le générateur resterait « fantôme ».

    private boolean estGen(Block b) { return IlesJoueurs.estMonde(b.getWorld()) && poses.containsKey(cle(b)); }

    @EventHandler(ignoreCancelled = true)
    public void bruler(BlockBurnEvent e) { if (estGen(e.getBlock())) e.setCancelled(true); }

    @EventHandler(ignoreCancelled = true)
    public void piston(BlockPistonExtendEvent e) { if (e.getBlocks().stream().anyMatch(this::estGen)) e.setCancelled(true); }

    @EventHandler(ignoreCancelled = true)
    public void pistonRetour(BlockPistonRetractEvent e) { if (e.getBlocks().stream().anyMatch(this::estGen)) e.setCancelled(true); }

    @EventHandler(ignoreCancelled = true)
    public void explosion(EntityExplodeEvent e) { e.blockList().removeIf(this::estGen); }

    @EventHandler(ignoreCancelled = true)
    public void explosionBloc(BlockExplodeEvent e) { e.blockList().removeIf(this::estGen); }

    @EventHandler(ignoreCancelled = true)
    public void entiteBloc(EntityChangeBlockEvent e) { if (estGen(e.getBlock())) e.setCancelled(true); }

    /** Les visiteurs ne ramassent pas la production d'une île qui n'est pas la leur. */
    @EventHandler(ignoreCancelled = true)
    public void ramasser(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player p) || !IlesJoueurs.estMonde(p.getWorld())) return;
        if (typeProduit(e.getItem().getItemStack()) == null) return;
        IlesJoueurs.Ile ile = iles().ileA(e.getItem().getX(), e.getItem().getZ());
        if (ile != null && !membre(p, ile) && !Zones.constructeur(p)) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ /vendre

    boolean vendre(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) { OrbitTycoon.msg(s, "Commande réservée aux joueurs."); return true; }
        boolean tout = args.length > 0 && (args[0].equalsIgnoreCase("tout") || args[0].equalsIgnoreCase("all"));
        if (tout) vendreTout(p);
        else {
            ItemStack main = p.getInventory().getItemInMainHand();
            Type t = typeProduit(main);
            if (t == null) { OrbitTycoon.msg(p, "Prends en main une ressource de générateur, ou fais <#E0B864>/vendre tout<gray>."); return true; }
            int n = main.getAmount();
            p.getInventory().setItemInMainHand(null);
            payer(p, n * t.vente(), n);
        }
        return true;
    }

    private void vendreTout(Player p) {
        double total = 0;
        int n = 0;
        ItemStack[] contenu = p.getInventory().getStorageContents();
        for (int i = 0; i < contenu.length; i++) {
            Type t = typeProduit(contenu[i]);
            if (t == null) continue;
            total += contenu[i].getAmount() * t.vente();
            n += contenu[i].getAmount();
            p.getInventory().setItem(i, null);
        }
        if (n == 0) { OrbitTycoon.msg(p, "Aucune ressource de générateur dans ton inventaire."); return; }
        payer(p, total, n);
    }

    private void payer(Player p, double montant, int objets) {
        double avecReste = montant + restes.getOrDefault(p.getUniqueId(), 0.0);
        long credits = (long) Math.floor(avecReste);
        restes.put(p.getUniqueId(), avecReste - credits);
        plugin.monnaies().ajouter(p.getUniqueId(), Monnaies.Monnaie.CREDIT, credits);
        OrbitTycoon.msg(p, "Vendu : <white>" + Hud.nombre(objets) + " ressource(s)<gray> → " + Monnaies.Monnaie.CREDIT.format(credits));
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.3f);
    }

    // ------------------------------------------------------------------ menu /gen

    private static final class Menu implements InventoryHolder {
        Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    boolean commande(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) { OrbitTycoon.msg(s, "Commande réservée aux joueurs."); return true; }
        ouvrir(p);
        return true;
    }

    void ouvrir(Player p) {
        Menu h = new Menu();
        Inventory inv = Bukkit.createInventory(h, 54, Habillage.titre(plugin, p, 6, "Générateurs"));
        h.inv = inv;
        int rang = rangDebloque(p.getUniqueId());
        long solde = plugin.monnaies().solde(p.getUniqueId(), Monnaies.Monnaie.CREDIT);
        for (int i = 0; i < types.size() && i < 45; i++) {
            Type t = types.get(i);
            if (i <= rang) {
                ItemStack it = new ItemStack(t.bloc());
                boolean assez = solde >= t.prix();
                it.editMeta(m -> {
                    m.displayName(texte("<#E0B864><bold>Générateur de " + t.nom()));
                    m.lore(List.of(
                            texte("<gray>Produit : <white>1 " + t.nom() + " <gray>/ " + intervalle() + " s"),
                            texte("<gray>Vente : <#E0B864>" + vente(t.vente()) + " ⛃ <gray>l'unité <dark_gray>(" + gain(t, 1) + ")"),
                            texte("<gray>Prix : " + (assez ? "<#E0B864>" : "<#FF4D4D>") + Hud.nombre(t.prix()) + " ⛃"),
                            texte(""),
                            texte("<#62CFF4>Clic : <gray>acheter 1"),
                            texte("<#62CFF4>Clic droit : <gray>acheter 8"),
                            texte("<#62CFF4>Shift + clic : <gray>acheter 64")));
                });
                inv.setItem(i, it);
            } else {
                Type avant = types.get(i - 1);
                inv.setItem(i, objet(Material.GRAY_DYE, "<dark_gray>??? <gray>(verrouillé)", false,
                        "<gray>Pose un <#E0B864>Générateur de " + avant.nom(), "<gray>sur ton île pour le débloquer."));
            }
        }
        IlesJoueurs.Ile ile = iles().ileDe(p.getUniqueId());
        inv.setItem(SLOT_INFO, objet(Material.BEACON, "<#E0B864><bold>Tes générateurs", false,
                "<gray>Solde : <#E0B864>" + Hud.nombre(solde) + " ⛃",
                ile == null ? "<#FF4D4D>Crée ton île avec /is create" : "<gray>Blocs posés : <white>" + emplacements(ile.index) + " <dark_gray>/ " + emplacementsMax(ile),
                "<gray>Empilables jusqu'à <white>x" + pileMax() + " <gray>par bloc",
                "",
                "<dark_gray>Production si un membre de l'île",
                "<dark_gray>est connecté et l'île chargée."));
        inv.setItem(SLOT_VENDRE, objet(Material.EMERALD, "<#E0B864><bold>Tout vendre", false,
                "<gray>Vend toutes les ressources de", "<gray>générateur de ton inventaire.", "", "<dark_gray>(ou /vendre tout)"));
        inv.setItem(SLOT_FERMER, objet(Material.BARRIER, "<#FF4D4D>Fermer", false));
        inv.setItem(SLOT_COFFRE, plugin.collecteurs().vitrineCoffre());
        inv.setItem(SLOT_COLLECTEUR, plugin.collecteurs().vitrine());
        Habillage.fond(plugin, p, inv);
        p.openInventory(inv);
    }

    @EventHandler
    public void clicMenu(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Menu)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getView().getTopInventory()) return;
        int slot = e.getSlot();
        if (slot == SLOT_FERMER) { p.closeInventory(); return; }
        if (slot == SLOT_VENDRE) { vendreTout(p); ouvrir(p); return; }
        if (slot == SLOT_COLLECTEUR) { plugin.collecteurs().acheter(p); return; }
        if (slot == SLOT_COFFRE) { plugin.collecteurs().acheterCoffre(p); return; }
        if (slot >= types.size() || slot >= 45 || slot > rangDebloque(p.getUniqueId())) return;
        Type t = types.get(slot);
        int n = e.isShiftClick() ? 64 : e.isRightClick() ? 8 : 1;
        long cout = t.prix() * n;
        if (!plugin.monnaies().retirer(p.getUniqueId(), Monnaies.Monnaie.CREDIT, cout)) {
            OrbitTycoon.msg(p, "<#FF4D4D>Pas assez de Crédits</#FF4D4D> <gray>: il faut " + Monnaies.Monnaie.CREDIT.format(cout) + "<gray>.");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
            return;
        }
        donnerGenerateurs(p, t, n);
        OrbitTycoon.msg(p, "Acheté : <#E0B864>" + n + " Générateur(s) de " + t.nom() + "<gray> pour " + Monnaies.Monnaie.CREDIT.format(cout) + "<gray>.");
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f);
        ouvrir(p);
    }

    @EventHandler
    public void glisser(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ admin

    /** /tycoon gen donner <joueur> <type> [nombre] · /tycoon gen liste */
    void admin(CommandSender s, String[] args) {
        if (args.length > 1 && args[1].equalsIgnoreCase("liste")) {
            StringBuilder b = new StringBuilder();
            for (Type t : types) b.append(t.id()).append(' ');
            OrbitTycoon.msg(s, types.size() + " générateurs : <white>" + b.toString().trim());
            OrbitTycoon.msg(s, "Posés : <white>" + poses.size() + "<gray> blocs, <white>" + plugin.collecteurs().nombre() + "<gray> collecteur(s).");
            return;
        }
        if (args.length > 2 && (args[1].equalsIgnoreCase("collecteur") || args[1].equalsIgnoreCase("coffre"))) {
            Player c = Bukkit.getPlayerExact(args[2]);
            if (c == null) { OrbitTycoon.msg(s, "Joueur introuvable."); return; }
            plugin.collecteurs().donner(s, c, args[1].equalsIgnoreCase("coffre"), args.length > 3 && args[3].matches("[0-9]{1,2}") ? Math.max(1, Integer.parseInt(args[3])) : 1);
            return;
        }
        if (args.length < 4 || !args[1].equalsIgnoreCase("donner")) { OrbitTycoon.msg(s, "Usage : /tycoon gen donner <joueur> <type> [nombre] · /tycoon gen collecteur|coffre <joueur> [nombre] · /tycoon gen liste"); return; }
        Player cible = Bukkit.getPlayerExact(args[2]);
        Type t = parId.get(args[3].toLowerCase());
        if (cible == null || t == null) { OrbitTycoon.msg(s, "Joueur ou type introuvable (/tycoon gen liste)."); return; }
        int n = args.length > 4 && args[4].matches("\\d{1,4}") ? Math.max(1, Integer.parseInt(args[4])) : 1;
        donnerGenerateurs(cible, t, n);
        OrbitTycoon.msg(s, n + " Générateur(s) de " + t.nom() + " donnés à " + cible.getName() + ".");
    }

    List<String> ids() { return types.stream().map(Type::id).toList(); }

    /** Type de générateur par son id (null si inconnu). */
    Type type(String id) { return id == null ? null : parId.get(id.toLowerCase(java.util.Locale.ROOT)); }
}
