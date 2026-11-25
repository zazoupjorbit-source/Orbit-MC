package fr.orbit.tycoon;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.block.MoistureChangeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Règles des zones : tout est protégé, sauf ce qui se farme (bûches et feuilles au Bûcheron,
 * cultures mûres / melons / citrouilles à l'Agriculture). Ce qui est cassé repousse tout seul.
 */
final class Zones implements Listener {
    private static final Set<Material> CULTURES = Set.of(Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS);
    /** Blocs qui se minent (et se rechargent) dans la Mine. */
    private static final Set<Material> MINE = Set.of(
            Material.STONE, Material.ANDESITE, Material.TUFF, Material.DEEPSLATE, Material.COBBLESTONE, Material.GRAVEL,
            Material.COAL_ORE, Material.COPPER_ORE, Material.IRON_ORE, Material.GOLD_ORE, Material.REDSTONE_ORE, Material.LAPIS_ORE,
            Material.DEEPSLATE_IRON_ORE, Material.DEEPSLATE_GOLD_ORE, Material.DEEPSLATE_REDSTONE_ORE, Material.DEEPSLATE_LAPIS_ORE,
            Material.DEEPSLATE_DIAMOND_ORE, Material.DEEPSLATE_EMERALD_ORE, Material.AMETHYST_BLOCK,
            Material.NETHER_QUARTZ_ORE, Material.ANCIENT_DEBRIS);

    private final OrbitTycoon plugin;
    /** Blocs en attente de repousse -> leur état d'origine. */
    private final Map<Block, BlockData> enAttente = new HashMap<>();

    Zones(OrbitTycoon plugin) {
        this.plugin = plugin;
    }

    /** Blocs en train de repousser -> état d'origine (lecture seule, thread principal). */
    Map<Block, BlockData> enAttente() { return java.util.Collections.unmodifiableMap(enAttente); }

    static boolean constructeur(Player p) {
        return p.getGameMode() == GameMode.CREATIVE && p.hasPermission("orbittycoon.admin");
    }

    // ------------------------------------------------------------------ farm et repousse

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void casser(BlockBreakEvent e) {
        if (IlesJoueurs.estMonde(e.getBlock().getWorld())) return;
        Player p = e.getPlayer();
        if (constructeur(p)) return;
        Block b = e.getBlock();
        if (enAttente.containsKey(b)) { e.setCancelled(true); return; }
        Zone z = Zone.at(b.getLocation());
        BlockData origine = b.getBlockData().clone();
        plugin.ressources().rafraichir(b, 2); // vue perso : remet la ressource choisie après la récolte ou le refus
        Material t = b.getType();

        if (z == Zone.BUCHERON && (Tag.LOGS.isTagged(t) || Tag.LEAVES.isTagged(t))) {
            recolter(e, t, 1);
            repousse(b, origine, null, plugin.getConfig().getInt("repousse.bucheron", 15));
            return;
        }
        if (z == Zone.AGRICULTURE) {
            if (CULTURES.contains(t) && origine instanceof Ageable a) {
                if (a.getAge() < a.getMaximumAge()) { e.setCancelled(true); return; }
                Ageable jeune = (Ageable) a.clone();
                jeune.setAge(0);
                recolter(e, t, 1);
                repousse(b, origine, jeune, repousseAgri(p));
                return;
            }
            if (t == Material.MELON || t == Material.PUMPKIN) {
                recolter(e, t, 1);
                repousse(b, origine, null, repousseAgri(p));
                return;
            }
            if (t == Material.SUGAR_CANE) {
                // On garde la base : seuls les morceaux du dessus se récoltent, et toute la tige repousse.
                if (b.getRelative(0, -1, 0).getType() != Material.SUGAR_CANE) { e.setCancelled(true); return; }
                int sec = repousseAgri(p);
                int morceaux = 0;
                for (Block h = b; h.getType() == Material.SUGAR_CANE; h = h.getRelative(0, 1, 0)) {
                    if (enAttente.containsKey(h)) continue;
                    repousse(h, h.getBlockData().clone(), null, sec);
                    morceaux++;
                }
                // Les morceaux du dessus sont retirés sans rien lâcher (sinon ils tomberaient en objets).
                for (Block h = b.getRelative(0, 1, 0); h.getType() == Material.SUGAR_CANE; h = h.getRelative(0, 1, 0))
                    h.setType(Material.AIR, false);
                recolter(e, t, morceaux);
                return;
            }
        }
        if (z == Zone.MINE && MINE.contains(t) && z.distance(b.getX() + 0.5, b.getZ() + 0.5) < Iles.FOSSE) {
            recolter(e, t, 1);
            BlockData recharge = Material.LIGHT_BLUE_STAINED_GLASS.createBlockData();
            repousse(b, origine, recharge, plugin.getConfig().getInt("repousse.mine", 15));
            return;
        }
        e.setCancelled(true);
    }

    /** Repousse des cultures, raccourcie par l'enchantement Engrais de la houe. */
    private int repousseAgri(Player p) {
        int base = plugin.getConfig().getInt("repousse.agriculture", 15);
        return plugin.enchantements() == null ? base : plugin.enchantements().repousse(p, base);
    }

    /** La ressource disparaît (aucun objet, aucune XP) et rapporte Flux stellaire + Crédits orbitaux. */
    private void recolter(BlockBreakEvent e, Material t, int quantite) {
        e.setDropItems(false);
        e.setExpToDrop(0);
        Zone z = Zone.at(e.getBlock().getLocation());
        // Ce que le joueur voit : ressource débloquée de cette part de l'île, ou roche si le minerai est verrouillé.
        Ressources.Recolte r = plugin.ressources().recolte(e.getPlayer(), z, e.getBlock(), t);
        plugin.monnaies().recompenser(e.getPlayer(), outilDe(z), r.cle(), quantite, r.palier());
        // Explosion, Foreuse, Abattage, Moisson : blocs voisins cassés par le joueur (même chemin que ce bloc-ci).
        if (plugin.enchantements() != null) plugin.enchantements().apresCasse(e.getPlayer(), outilDe(z), e.getBlock());
    }

    /** Outil qui gagne l'XP d'un bloc cassé, selon la zone. */
    private static Outil.Mode outilDe(Zone z) {
        if (z == null) return null;
        return switch (z) {
            case MINE -> Outil.Mode.PIOCHE;
            case BUCHERON -> Outil.Mode.HACHE;
            case AGRICULTURE -> Outil.Mode.HOUE;
            case PECHE -> Outil.Mode.CANNE;
            case MOBZONE -> Outil.Mode.EPEE;
            default -> null;
        };
    }

    /** Mobs : plus aucun objet ni XP ; le joueur qui tue reçoit directement ses monnaies. */
    @EventHandler(priority = EventPriority.HIGH)
    public void mobTue(org.bukkit.event.entity.EntityDeathEvent e) {
        if (IlesJoueurs.estMonde(e.getEntity().getWorld())) return;
        if (e.getEntity() instanceof Player) return;
        e.getDrops().clear();
        e.setDroppedExp(0);
        Player tueur = e.getEntity().getKiller();
        if (tueur == null) return;
        // Épée : un mob pas encore débloqué ne rapporte rien ; sinon les gains de sa ressource.
        Ressources.Recolte r = plugin.ressources().recolteMob(tueur, e.getEntityType());
        if (r == null) {
            tueur.sendActionBar(Navigateur.texte("<#FF4D4D>🔒 Mob pas encore débloqué pour ton épée : aucun gain."));
            return;
        }
        plugin.monnaies().recompenser(tueur, Outil.Mode.EPEE, r.cle(), 1, r.palier());
    }

    /** Après la casse : pose l'état intermédiaire (pousse) puis remet l'origine après le délai. */
    private void repousse(Block b, BlockData origine, BlockData intermediaire, int secondes) {
        enAttente.put(b, origine);
        if (intermediaire != null) plugin.getServer().getScheduler().runTask(plugin, () -> b.setBlockData(intermediaire, false));
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> restaurer(b), Math.max(1, secondes) * 20L);
    }

    private void restaurer(Block b) {
        BlockData origine = enAttente.remove(b);
        if (origine != null) {
            b.setBlockData(origine, false);
            plugin.ressources().rafraichir(b, 1);
        }
    }

    /** À l'arrêt : tout repousse immédiatement, pour ne jamais laisser de trou. */
    void toutRestaurer() {
        for (Map.Entry<Block, BlockData> en : enAttente.entrySet()) en.getKey().setBlockData(en.getValue(), false);
        enAttente.clear();
    }

    // ------------------------------------------------------------------ protections

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void poser(BlockPlaceEvent e) {
        if (IlesJoueurs.estMonde(e.getBlock().getWorld())) return;
        if (!constructeur(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void seauVider(PlayerBucketEmptyEvent e) {
        if (IlesJoueurs.estMonde(e.getBlock().getWorld())) return;
        if (!constructeur(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void seauRemplir(PlayerBucketFillEvent e) {
        if (IlesJoueurs.estMonde(e.getBlock().getWorld())) return;
        if (!constructeur(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void pietiner(PlayerInteractEvent e) {
        if (e.getAction() == Action.PHYSICAL && e.getClickedBlock() != null && e.getClickedBlock().getType() == Material.FARMLAND)
            e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void mobPietine(EntityChangeBlockEvent e) {
        if (IlesJoueurs.estMonde(e.getBlock().getWorld())) return;
        if (e.getBlock().getType() == Material.FARMLAND || !(e.getEntity() instanceof Player)) e.setCancelled(true);
    }

    /** Creepers, TNT du décor… : les explosions ne cassent aucun bloc du Tycoon (hors îles des joueurs). */
    @EventHandler(ignoreCancelled = true)
    public void explosion(org.bukkit.event.entity.EntityExplodeEvent e) {
        if (!IlesJoueurs.estMonde(e.getLocation().getWorld())) e.blockList().clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void explosionBloc(org.bukkit.event.block.BlockExplodeEvent e) {
        if (!IlesJoueurs.estMonde(e.getBlock().getWorld())) e.blockList().clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void secher(MoistureChangeEvent e) { if (!IlesJoueurs.estMonde(e.getBlock().getWorld())) e.setCancelled(true); }

    @EventHandler(ignoreCancelled = true)
    public void fondre(BlockFadeEvent e) { if (!IlesJoueurs.estMonde(e.getBlock().getWorld())) e.setCancelled(true); }

    @EventHandler(ignoreCancelled = true)
    public void feuilles(LeavesDecayEvent e) { if (!IlesJoueurs.estMonde(e.getBlock().getWorld())) e.setCancelled(true); }

    @EventHandler(ignoreCancelled = true)
    public void ecoulement(BlockFromToEvent e) {
        if (IlesJoueurs.estMonde(e.getBlock().getWorld())) return;
        // L'eau des lagons et canaux ne déborde jamais (sauf vers une case déjà eau).
        if (e.getBlock().getType() == Material.WATER) e.setCancelled(true);
    }

    /**
     * Pêche : seulement des poissons (proportions vanilla : morue 60 %, saumon 25 %, poisson-globe 13 %, tropical 2 %).
     * Le poisson ne remonte pas en objet : il est converti directement en monnaies.
     */
    @EventHandler(ignoreCancelled = true)
    public void peche(org.bukkit.event.player.PlayerFishEvent e) {
        if (IlesJoueurs.estMonde(e.getPlayer().getWorld())) return;
        if (e.getState() != org.bukkit.event.player.PlayerFishEvent.State.CAUGHT_FISH) return;
        if (!(e.getCaught() instanceof org.bukkit.entity.Item item)) return;
        int n = java.util.concurrent.ThreadLocalRandom.current().nextInt(100);
        Material poisson = n < 60 ? Material.COD : n < 85 ? Material.SALMON : n < 98 ? Material.PUFFERFISH : Material.TROPICAL_FISH;
        item.remove();
        e.setExpToDrop(0);
        plugin.monnaies().recompenser(e.getPlayer(), Outil.Mode.CANNE, poisson.name(), 1);
    }

    /** Mobzone : les monstres des spawners ne brûlent pas au soleil. */
    @EventHandler(ignoreCancelled = true)
    public void spawner(org.bukkit.event.entity.CreatureSpawnEvent e) {
        if (e.getSpawnReason() != org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.SPAWNER) return;
        if (e.getEntity() instanceof org.bukkit.entity.Zombie z) z.setShouldBurnInDay(false);
        if (e.getEntity() instanceof org.bukkit.entity.AbstractSkeleton s) s.setShouldBurnInDay(false);
        if (e.getEntity() instanceof org.bukkit.entity.Phantom p) p.setShouldBurnInDay(false);
    }

    @EventHandler(ignoreCancelled = true)
    public void faim(FoodLevelChangeEvent e) {
        e.setCancelled(true);
        e.getEntity().setFoodLevel(20);
    }

    @EventHandler(ignoreCancelled = true)
    public void vide(PlayerMoveEvent e) {
        if (e.getTo().getY() >= plugin.getConfig().getInt("vide-y", 0)) return;
        Player p = e.getPlayer();
        if (IlesJoueurs.estMonde(p.getWorld())) { plugin.ilesJoueurs().vide(p); return; }
        Zone z = Zone.at(e.getTo());
        p.setFallDistance(0);
        p.teleportAsync((z == null ? Zone.BASE : z).arrivee(p.getWorld()));
    }
}
