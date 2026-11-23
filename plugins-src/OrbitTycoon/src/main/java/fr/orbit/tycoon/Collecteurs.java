package fr.orbit.tycoon;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
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
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static fr.orbit.tycoon.Navigateur.texte;
import static fr.orbit.tycoon.Progression.objet;

/**
 * Collecteur orbital : un coffre posé sur son île /is qui ramasse et vend tout seul (autosell) toutes les ressources
 * de générateur de SON chunk (16×16, toute la hauteur). Acheté dans le menu /gen pour « prix-plasma » Plasma.
 * - La production des générateurs du chunk est vendue directement (aucun objet au sol, donc pas de lag).
 * - Toutes les « balayage-secondes », les ressources déjà au sol dans le chunk sont ramassées et vendues.
 * - Les Crédits vont à celui qui a posé le collecteur. Un seul collecteur par chunk ; le coffre ne s'ouvre pas.
 * Données : plugins/OrbitTycoon/collecteurs.yml.
 * ATTENTION (23/11/2026) : vendu en Plasma (monnaie payante) à la demande de Wane, alors que c'est un avantage de jeu
 * (contraire à la règle « aucun pay-to-win » et à l'EULA Mojang) : à valider avec Zazou avant toute mise en ligne.
 */
final class Collecteurs implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String TAG = "orbit_collecteur";

    static final class Col {
        final int ile;
        final UUID proprio;
        /** false = Collecteur orbital (vend) ; true = Coffre collecteur (range dans le coffre, ne vend pas). */
        final boolean stock;
        long total;   // Crédits vendus (ou objets rangés) depuis la pose
        Col(int ile, UUID proprio, boolean stock, long total) { this.ile = ile; this.proprio = proprio; this.stock = stock; this.total = total; }
    }

    private final OrbitTycoon plugin;
    private final NamespacedKey cle;
    /** « x,y,z » → collecteur ; et chunk (clé Bukkit) → « x,y,z ». */
    private final Map<String, Col> cols = new HashMap<>();
    private final Map<Long, String> parChunk = new HashMap<>();
    private final Map<String, TextDisplay> titres = new HashMap<>();
    /** Ventes pas encore annoncées / centimes pas encore versés, par propriétaire. */
    private final Map<UUID, Double> enAttente = new HashMap<>();
    private final Map<UUID, Integer> objetsEnAttente = new HashMap<>();
    private final ExecutorService ecriture = Executors.newSingleThreadExecutor(r -> new Thread(r, "OrbitTycoon-collecteurs"));
    private BukkitTask balayage;
    private boolean modifie;

    Collecteurs(OrbitTycoon plugin) {
        this.plugin = plugin;
        cle = new NamespacedKey(plugin, "collecteur");
    }

    private Generateurs gens() { return plugin.generateurs(); }

    private static World monde() { return Bukkit.getWorld(IlesJoueurs.NOM_MONDE); }

    private long prix() { return Math.max(0, plugin.getConfig().getLong("generateurs.collecteur.prix-plasma", 500)); }

    // ------------------------------------------------------------------ cycle de vie

    void demarrer() {
        lire();
        long t = Math.max(2, plugin.getConfig().getLong("generateurs.collecteur.balayage-secondes", 10)) * 20L;
        balayage = Bukkit.getScheduler().runTaskTimer(plugin, this::balayer, t, t);
        World w = monde();
        if (w != null) for (String k : cols.keySet()) {
            int[] c = coords(k);
            if (w.isChunkLoaded(c[0] >> 4, c[2] >> 4)) titre(k);
        }
    }

    void arreter() {
        if (balayage != null) balayage.cancel();
        verser();
        for (TextDisplay d : titres.values()) if (d.isValid()) d.remove();
        titres.clear();
        String texte = texteSauvegarde();
        ecriture.shutdown();
        ecrire(texte);
    }

    private File fichier() { return new File(plugin.getDataFolder(), "collecteurs.yml"); }

    private void lire() {
        cols.clear();
        parChunk.clear();
        ConfigurationSection s = YamlConfiguration.loadConfiguration(fichier()).getConfigurationSection("collecteurs");
        if (s == null) return;
        for (String k : s.getKeys(false)) {
            ConfigurationSection c = s.getConfigurationSection(k);
            if (c == null) continue;
            try {
                String pos = k.replace('_', ',');
                cols.put(pos, new Col(c.getInt("ile", -1), UUID.fromString(c.getString("proprio", "")), c.getBoolean("stock"), c.getLong("total")));
                int[] xyz = coords(pos);
                parChunk.put(Chunk.getChunkKey(xyz[0] >> 4, xyz[2] >> 4), pos);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Collecteur ignoré dans collecteurs.yml : " + k);
            }
        }
    }

    private String texteSauvegarde() {
        YamlConfiguration y = new YamlConfiguration();
        for (var e : cols.entrySet()) {
            String k = "collecteurs." + e.getKey().replace(',', '_');
            y.set(k + ".ile", e.getValue().ile);
            y.set(k + ".proprio", e.getValue().proprio.toString());
            y.set(k + ".total", e.getValue().total);
            if (e.getValue().stock) y.set(k + ".stock", true);
        }
        return y.saveToString();
    }

    private void sauver() {
        modifie = false;
        String texte = texteSauvegarde();
        if (ecriture.isShutdown()) { ecrire(texte); return; }
        ecriture.execute(() -> ecrire(texte));
    }

    private synchronized void ecrire(String texte) {
        File f = fichier(), tmp = new File(plugin.getDataFolder(), "collecteurs.yml.tmp");
        try {
            f.getParentFile().mkdirs();
            Files.writeString(tmp.toPath(), texte, StandardCharsets.UTF_8);
            Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            plugin.getLogger().severe("Collecteurs : sauvegarde impossible : " + e);
        }
    }

    private static String cle(Block b) { return b.getX() + "," + b.getY() + "," + b.getZ(); }

    private static int[] coords(String k) {
        String[] p = k.split(",");
        return new int[]{Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])};
    }

    // ------------------------------------------------------------------ objet et achat

    private long prixCoffre() { return Math.max(0, plugin.getConfig().getLong("generateurs.coffre-collecteur.prix-credits", 10000)); }

    /** stock = false : Collecteur orbital (PDC 1) ; stock = true : Coffre collecteur (PDC 2). */
    ItemStack objetCollecteur(boolean stock) {
        ItemStack it = new ItemStack(Material.CHEST);
        it.editMeta(m -> {
            if (stock) {
                m.displayName(texte("<gradient:#62CFF4:#E0B864><bold>Coffre collecteur</bold></gradient>"));
                m.lore(List.of(
                        texte("<gray>Ramasse et <#62CFF4>range</#62CFF4> <gray>toutes les ressources"),
                        texte("<gray>de générateur de <white>son chunk<gray> (ne vend pas)."),
                        texte("<gray>Ouvre-le pour récupérer ton stock."),
                        texte(""),
                        texte("<#62CFF4>➜ Pose-le sur ton île /is"),
                        texte("<dark_gray>1 collecteur par chunk (F3 + G : bordures)")));
            } else {
                m.displayName(texte("<gradient:#C77DFF:#62CFF4><bold>Collecteur orbital</bold></gradient>"));
                m.lore(List.of(
                        texte("<gray>Ramasse et <#E0B864>vend tout seul</#E0B864> <gray>toutes les"),
                        texte("<gray>ressources de générateur de <white>son chunk<gray>."),
                        texte(""),
                        texte("<#62CFF4>➜ Pose-le sur ton île /is"),
                        texte("<dark_gray>1 collecteur par chunk (F3 + G : bordures)")));
                m.setEnchantmentGlintOverride(true);
            }
            m.getPersistentDataContainer().set(cle, PersistentDataType.BYTE, (byte) (stock ? 2 : 1));
        });
        return it;
    }

    /** 0 = pas un collecteur, 1 = Collecteur orbital, 2 = Coffre collecteur. */
    private byte sorte(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return 0;
        Byte b = it.getItemMeta().getPersistentDataContainer().get(cle, PersistentDataType.BYTE);
        return b == null ? 0 : b;
    }

    private boolean estObjet(ItemStack it) { return sorte(it) != 0; }

    /** Inventaire du coffre posé (sans copie de l'état du bloc), ou null si le bloc n'est plus un coffre. */
    private org.bukkit.inventory.Inventory inventaire(String k) {
        World w = monde();
        if (w == null) return null;
        int[] xyz = coords(k);
        if (!w.isChunkLoaded(xyz[0] >> 4, xyz[2] >> 4)) return null;
        return w.getBlockAt(xyz[0], xyz[1], xyz[2]).getState(false) instanceof org.bukkit.block.Container c ? c.getInventory() : null;
    }

    /** Case « Coffre collecteur » du menu /gen (payé en Crédits). */
    ItemStack vitrineCoffre() {
        return objet(Material.CHEST, "<gradient:#62CFF4:#E0B864><bold>Coffre collecteur</bold></gradient>", false,
                "<gray>Coffre qui ramasse et <#62CFF4>range</#62CFF4>", "<gray>les ressources de générateur", "<gray>de <white>tout son chunk<gray> (ne vend pas).",
                "", "<gray>Prix : " + Monnaies.Monnaie.CREDIT.format(prixCoffre()), "", "<#E0B864>➜ Clic pour acheter");
    }

    void acheterCoffre(Player p) {
        long prix = prixCoffre();
        if (!plugin.monnaies().retirer(p.getUniqueId(), Monnaies.Monnaie.CREDIT, prix)) {
            OrbitTycoon.msg(p, "<#FF4D4D>Pas assez de Crédits</#FF4D4D> <gray>: il faut " + Monnaies.Monnaie.CREDIT.format(prix) + "<gray>.");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
            return;
        }
        for (ItemStack reste : p.getInventory().addItem(objetCollecteur(true)).values()) p.getWorld().dropItem(p.getLocation(), reste);
        OrbitTycoon.msg(p, "Acheté : <gradient:#62CFF4:#E0B864>Coffre collecteur</gradient><gray> pour " + Monnaies.Monnaie.CREDIT.format(prix) + "<gray>.");
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f);
        p.closeInventory();
    }

    /** Case du menu /gen. */
    ItemStack vitrine() {
        return objet(Material.CHEST, "<gradient:#C77DFF:#62CFF4><bold>Collecteur orbital</bold></gradient>", true,
                "<gray>Coffre qui ramasse et vend tout seul", "<gray>les ressources de générateur", "<gray>de <white>tout son chunk<gray> (16×16).",
                "", "<gray>Prix : " + Monnaies.Monnaie.PLASMA.format(prix()), "", "<#C77DFF>➜ Clic pour acheter");
    }

    void acheter(Player p) {
        long prix = prix();
        if (!plugin.monnaies().retirer(p.getUniqueId(), Monnaies.Monnaie.PLASMA, prix)) {
            OrbitTycoon.msg(p, "<#FF4D4D>Pas assez de Plasma</#FF4D4D> <gray>: il faut " + Monnaies.Monnaie.PLASMA.format(prix) + "<gray>.");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
            return;
        }
        for (ItemStack reste : p.getInventory().addItem(objetCollecteur(false)).values()) p.getWorld().dropItem(p.getLocation(), reste);
        OrbitTycoon.msg(p, "Acheté : <gradient:#C77DFF:#62CFF4>Collecteur orbital</gradient><gray> pour " + Monnaies.Monnaie.PLASMA.format(prix) + "<gray>.");
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f);
        p.closeInventory();
    }

    // ------------------------------------------------------------------ vente

    /**
     * Appelé par la production des générateurs : un collecteur de la même île vend (ou range) la production.
     * Renvoie le nombre d'objets qui restent à poser au sol (0 = tout est pris ; coffre plein = le reste).
     */
    int absorber(int cx, int cz, int ile, Generateurs.Type t, int nombre) {
        String k = parChunk.get(Chunk.getChunkKey(cx, cz));
        if (k == null) return nombre;
        Col c = cols.get(k);
        if (c == null || c.ile != ile) return nombre;
        if (!c.stock) { crediter(c, nombre * t.vente(), nombre); return 0; }
        return ranger(k, c, gens().produit(t, nombre));
    }

    /** Range dans le Coffre collecteur ; renvoie ce qui n'a pas pu rentrer. */
    private int ranger(String k, Col c, ItemStack objets) {
        var inv = inventaire(k);
        if (inv == null) return objets.getAmount();
        int avant = objets.getAmount(), reste = 0;
        for (ItemStack r : inv.addItem(objets).values()) reste += r.getAmount();
        if (avant > reste) { c.total += avant - reste; modifie = true; }
        return reste;
    }

    private void crediter(Col c, double montant, int objets) {
        enAttente.merge(c.proprio, montant, Double::sum);
        objetsEnAttente.merge(c.proprio, objets, Integer::sum);
        c.total += Math.round(montant);
        modifie = true;
    }

    /** Ramasse les ressources au sol des chunks à collecteur, puis verse les Crédits et met à jour les titres. */
    private void balayer() {
        World w = monde();
        if (w != null) for (var e : cols.entrySet()) {
            int[] xyz = coords(e.getKey());
            if (!w.isChunkLoaded(xyz[0] >> 4, xyz[2] >> 4)) continue;
            Chunk ch = w.getChunkAt(xyz[0] >> 4, xyz[2] >> 4);
            for (Entity en : ch.getEntities()) {
                if (!(en instanceof Item it) || !it.isValid()) continue;
                Generateurs.Type t = gens().typeProduit(it.getItemStack());
                if (t == null) continue;
                int n = it.getItemStack().getAmount();
                if (e.getValue().stock) {
                    int reste = ranger(e.getKey(), e.getValue(), it.getItemStack().clone());
                    if (reste <= 0) it.remove();
                    else if (reste < n) { ItemStack s = it.getItemStack(); s.setAmount(reste); it.setItemStack(s); }
                    continue;
                }
                it.remove();
                crediter(e.getValue(), n * t.vente(), n);
            }
        }
        verser();
        for (String k : titres.keySet()) titre(k);
        if (modifie) sauver();
    }

    private void verser() {
        for (var e : enAttente.entrySet()) {
            double montant = e.getValue();
            long credits = (long) Math.floor(montant);
            e.setValue(montant - credits);
            int objets = objetsEnAttente.getOrDefault(e.getKey(), 0);
            if (credits <= 0) continue;
            plugin.monnaies().ajouter(e.getKey(), Monnaies.Monnaie.CREDIT, credits);
            objetsEnAttente.remove(e.getKey());
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null) p.sendActionBar(texte("<gradient:#C77DFF:#62CFF4>Collecteur</gradient> <gray>: " + Hud.nombre(objets)
                    + " ressource(s) vendue(s) <dark_gray>→ <#E0B864>+" + Hud.nombre(credits) + " ⛃"));
        }
    }

    // ------------------------------------------------------------------ titre au-dessus du coffre

    private void titre(String k) {
        World w = monde();
        Col c = cols.get(k);
        TextDisplay ancien = titres.remove(k);
        if (c == null || w == null) { if (ancien != null && ancien.isValid()) ancien.remove(); return; }
        net.kyori.adventure.text.Component texte;
        if (c.stock) {
            var inv = inventaire(k);
            int dedans = 0, max = 0;
            if (inv != null) {
                max = inv.getSize() * 64;
                for (ItemStack s : inv.getContents()) if (s != null) dedans += s.getAmount();
            }
            texte = MM.deserialize("<gradient:#62CFF4:#E0B864><bold>Coffre collecteur</bold></gradient>\n<gray>Stock : "
                    + (max > 0 && dedans >= max ? "<#FF4D4D>PLEIN" : "<white>" + Hud.nombre(dedans) + " <dark_gray>/ " + Hud.nombre(max)));
        } else texte = MM.deserialize("<gradient:#C77DFF:#62CFF4><bold>Collecteur orbital</bold></gradient>\n<gray>Vendu : <#E0B864>"
                + Hud.nombre(c.total) + " ⛃");
        if (ancien != null && ancien.isValid()) { ancien.text(texte); titres.put(k, ancien); return; }
        int[] xyz = coords(k);
        if (!w.isChunkLoaded(xyz[0] >> 4, xyz[2] >> 4)) return;
        TextDisplay d = w.spawn(new Location(w, xyz[0] + 0.5, xyz[1] + 1.5, xyz[2] + 0.5), TextDisplay.class, x -> {
            x.setPersistent(false);
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
        String k = parChunk.get(e.getChunk().getChunkKey());
        if (k != null) titre(k);
    }

    // ------------------------------------------------------------------ poser, cliquer, casser

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void poser(BlockPlaceEvent e) {
        if (!estObjet(e.getItemInHand())) return;
        Player p = e.getPlayer();
        Block b = e.getBlockPlaced();
        IlesJoueurs.Ile ile = IlesJoueurs.estMonde(b.getWorld()) ? gens().iles().ileA(b.getX(), b.getZ()) : null;
        if (ile == null || !gens().membre(p, ile)) { e.setCancelled(true); OrbitTycoon.msg(p, "Le Collecteur se pose seulement sur <#E0B864>ton<gray> île /is."); return; }
        long ch = Chunk.getChunkKey(b.getX() >> 4, b.getZ() >> 4);
        if (parChunk.containsKey(ch)) { e.setCancelled(true); OrbitTycoon.msg(p, "<#FF4D4D>Il y a déjà un Collecteur dans ce chunk.</#FF4D4D> <gray>(F3 + G pour voir les bordures)"); return; }
        // Pas de coffre double : un coffre voisin le fusionnerait avec le collecteur.
        for (var f : List.of(org.bukkit.block.BlockFace.NORTH, org.bukkit.block.BlockFace.SOUTH, org.bukkit.block.BlockFace.EAST, org.bukkit.block.BlockFace.WEST)) {
            Material m = b.getRelative(f).getType();
            if (m == Material.CHEST || m == Material.TRAPPED_CHEST) { e.setCancelled(true); OrbitTycoon.msg(p, "Pas de coffre collé au Collecteur."); return; }
        }
        String k = cle(b);
        boolean stock = sorte(e.getItemInHand()) == 2;
        cols.put(k, new Col(ile.index, p.getUniqueId(), stock, 0));
        parChunk.put(ch, k);
        sauver();
        Bukkit.getScheduler().runTask(plugin, () -> titre(k));
        p.playSound(b.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.7f, 1.4f);
        b.getWorld().spawnParticle(Particle.END_ROD, b.getLocation().add(0.5, 1, 0.5), 20, 0.4, 0.4, 0.4, 0.02);
        OrbitTycoon.msg(p, stock ? "Coffre collecteur posé : il range les ressources de générateur de ce chunk (ouvre-le pour les prendre)."
                : "Collecteur posé : il vend tout seul les ressources de générateur de ce chunk.");
    }

    /** Un coffre à côté d'un collecteur ne doit pas se coller à lui (coffre double). */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void poserVoisin(BlockPlaceEvent e) {
        Block b = e.getBlockPlaced();
        if (estObjet(e.getItemInHand()) || (b.getType() != Material.CHEST && b.getType() != Material.TRAPPED_CHEST) || !IlesJoueurs.estMonde(b.getWorld())) return;
        for (var f : List.of(org.bukkit.block.BlockFace.NORTH, org.bukkit.block.BlockFace.SOUTH, org.bukkit.block.BlockFace.EAST, org.bukkit.block.BlockFace.WEST))
            if (cols.containsKey(cle(b.getRelative(f)))) { e.setCancelled(true); OrbitTycoon.msg(e.getPlayer(), "Pas de coffre collé au Collecteur."); return; }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void clic(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND) return;
        Block b = e.getClickedBlock();
        if (b == null || !IlesJoueurs.estMonde(b.getWorld())) return;
        Col c = cols.get(cle(b));
        if (c == null) return;
        Player p = e.getPlayer();
        // Coffre collecteur : il s'ouvre normalement (les visiteurs sont déjà bloqués par IlesJoueurs).
        if (c.stock) return;
        // Shift avec un bloc en main : on le laisse se poser contre le collecteur ; sinon le coffre ne s'ouvre jamais.
        e.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        if (p.isSneaking() && !p.getInventory().getItemInMainHand().getType().isAir()) return;
        e.setCancelled(true);
        String nom = Bukkit.getOfflinePlayer(c.proprio).getName();
        p.sendActionBar(texte("<gradient:#C77DFF:#62CFF4>Collecteur orbital</gradient> <dark_gray>· <gray>de <white>"
                + (nom == null ? "?" : nom) + " <dark_gray>· <gray>vendu : <#E0B864>" + Hud.nombre(c.total) + " ⛃"));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void casser(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (!IlesJoueurs.estMonde(b.getWorld())) return;
        String k = cle(b);
        Col c = cols.get(k);
        if (c == null) return;
        Player p = e.getPlayer();
        IlesJoueurs.Ile ile = gens().iles().ileA(b.getX(), b.getZ());
        if (!gens().membre(p, ile) && !Zones.constructeur(p)) { e.setCancelled(true); return; }
        e.setDropItems(false);
        if (c.stock) {   // le stock tombe au pied du coffre (on le vide avant que le jeu ne le fasse)
            var inv = inventaire(k);
            if (inv != null) {
                Location l = b.getLocation().add(0.5, 0.5, 0.5);
                for (ItemStack s : inv.getContents()) if (s != null && !s.getType().isAir()) b.getWorld().dropItem(l, s);
                inv.clear();
            }
        }
        cols.remove(k);
        parChunk.remove(Chunk.getChunkKey(b.getX() >> 4, b.getZ() >> 4));
        sauver();
        titre(k);
        for (ItemStack reste : p.getInventory().addItem(objetCollecteur(c.stock)).values()) p.getWorld().dropItem(p.getLocation(), reste);
        OrbitTycoon.msg(p, (c.stock ? "Coffre collecteur" : "Collecteur") + " récupéré.");
    }

    private boolean estCol(Block b) { return IlesJoueurs.estMonde(b.getWorld()) && cols.containsKey(cle(b)); }

    @EventHandler(ignoreCancelled = true)
    public void bruler(BlockBurnEvent e) { if (estCol(e.getBlock())) e.setCancelled(true); }

    @EventHandler(ignoreCancelled = true)
    public void piston(BlockPistonExtendEvent e) { if (e.getBlocks().stream().anyMatch(this::estCol)) e.setCancelled(true); }

    @EventHandler(ignoreCancelled = true)
    public void pistonRetour(BlockPistonRetractEvent e) { if (e.getBlocks().stream().anyMatch(this::estCol)) e.setCancelled(true); }

    @EventHandler(ignoreCancelled = true)
    public void explosion(EntityExplodeEvent e) { e.blockList().removeIf(this::estCol); }

    @EventHandler(ignoreCancelled = true)
    public void explosionBloc(BlockExplodeEvent e) { e.blockList().removeIf(this::estCol); }

    /** Pas d'entonnoir sur le Collecteur orbital (il reste vide) ; le Coffre collecteur, lui, peut en avoir. */
    @EventHandler(ignoreCancelled = true)
    public void entonnoir(InventoryMoveItemEvent e) {
        var src = e.getSource().getLocation();
        var dst = e.getDestination().getLocation();
        if ((src != null && estVente(src.getBlock())) || (dst != null && estVente(dst.getBlock()))) e.setCancelled(true);
    }

    private boolean estVente(Block b) {
        if (!IlesJoueurs.estMonde(b.getWorld())) return false;
        Col c = cols.get(cle(b));
        return c != null && !c.stock;
    }

    // ------------------------------------------------------------------ admin

    void donner(CommandSender s, Player cible, boolean stock, int n) {
        for (int i = 0; i < n; i++)
            for (ItemStack reste : cible.getInventory().addItem(objetCollecteur(stock)).values()) cible.getWorld().dropItem(cible.getLocation(), reste);
        OrbitTycoon.msg(s, n + (stock ? " Coffre(s) collecteur(s)" : " Collecteur(s) orbital(aux)") + " donné(s) à " + cible.getName() + ".");
    }

    int nombre() { return cols.size(); }
}
