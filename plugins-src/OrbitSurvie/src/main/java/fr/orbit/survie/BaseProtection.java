package fr.orbit.survie;

import fr.orbit.survie.Bases.Base;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Villager;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.block.TNTPrimeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTakeLecternBookEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Protection des bases payées : personne d'autre que le propriétaire et ses membres ne casse, ne pose, n'ouvre,
 * ne vide ni ne brûle quoi que ce soit dans la zone. Explosions, feu, liquides, pistons, entonnoirs et monstres
 * qui arrachent des blocs (endermen, wither…) ne l'entament pas non plus. Base impayée : plus rien de tout ça.
 */
public final class BaseProtection implements Listener {

    /** Objets qu'un visiteur peut utiliser en cliquant un bloc d'une base (rien qui pose, casse ou actionne). */
    private static final Set<Material> LIBRES = EnumSet.of(Material.BOW, Material.CROSSBOW, Material.TRIDENT,
            Material.ENDER_PEARL, Material.SNOWBALL, Material.EGG, Material.SPLASH_POTION, Material.LINGERING_POTION,
            Material.POTION, Material.SHIELD, Material.FISHING_ROD, Material.SPYGLASS, Material.COMPASS, Material.CLOCK,
            Material.RECOVERY_COMPASS, Material.MAP, Material.FILLED_MAP, Material.WRITTEN_BOOK, Material.WRITABLE_BOOK,
            Material.EXPERIENCE_BOTTLE, Material.MILK_BUCKET, Material.GOAT_HORN);

    private final Bases bases;

    public BaseProtection(Bases bases) {
        this.bases = bases;
    }

    /** Vrai (et message) si le joueur n'a pas le droit d'agir à cet endroit. */
    private boolean refuse(Player p, Location l) {
        Base b = bases.a(l);
        if (b == null || bases.peut(p, b)) {
            return false;
        }
        p.sendActionBar(Msg.raw("<red>🔒 Base protégée de <white>" + b.proprioNom()));
        return true;
    }

    private static Player joueur(Entity e) {
        if (e instanceof Player p) {
            return p;
        }
        if (e instanceof Projectile pr) {
            ProjectileSource s = pr.getShooter();
            return s instanceof Player p ? p : null;
        }
        if (e instanceof TNTPrimed t) {
            return t.getSource() instanceof Player p ? p : null;
        }
        return null;
    }

    // ------------------------------------------------------------------ Blocs

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onCasse(BlockBreakEvent e) {
        if (refuse(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPose(BlockPlaceEvent e) {
        if (refuse(e.getPlayer(), e.getBlockPlaced().getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onVider(PlayerBucketEmptyEvent e) {
        if (refuse(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onRemplir(PlayerBucketFillEvent e) {
        if (refuse(e.getPlayer(), e.getBlockClicked().getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onClic(PlayerInteractEvent e) {
        Block bl = e.getClickedBlock();
        if (bl == null) {
            return;
        }
        Player p = e.getPlayer();
        Base b = bases.a(bl.getLocation());
        if (b == null || bases.peut(p, b)) {
            return;
        }
        if (e.getAction() == Action.PHYSICAL) {
            e.setCancelled(true); // plaques de pression, fil, terre labourée
            return;
        }
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || bases.armoire(bl) != null) {
            return;
        }
        if (bl.getType().isInteractable()) {
            e.setUseInteractedBlock(Event.Result.DENY);
            p.sendActionBar(Msg.raw("<red>🔒 Base protégée de <white>" + b.proprioNom()));
        }
        ItemStack it = e.getItem();
        if (it != null && !it.getType().isEdible() && !LIBRES.contains(it.getType())) {
            e.setUseItemInHand(Event.Result.DENY);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPancarte(SignChangeEvent e) {
        if (refuse(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onLutrin(PlayerTakeLecternBookEvent e) {
        if (refuse(e.getPlayer(), e.getLectern().getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onRecolte(PlayerHarvestBlockEvent e) {
        if (refuse(e.getPlayer(), e.getHarvestedBlock().getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEngrais(BlockFertilizeEvent e) {
        if (e.getPlayer() != null && refuse(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ Entités

    private void entite(Player p, Entity cible, Cancellable e) {
        if (cible instanceof Player || cible instanceof Monster) {
            return;
        }
        if (refuse(p, cible.getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntite(PlayerInteractEntityEvent e) {
        entite(e.getPlayer(), e.getRightClicked(), e);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntiteA(PlayerInteractAtEntityEvent e) {
        entite(e.getPlayer(), e.getRightClicked(), e);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onSupport(PlayerArmorStandManipulateEvent e) {
        entite(e.getPlayer(), e.getRightClicked(), e);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDegat(EntityDamageByEntityEvent e) {
        Entity cible = e.getEntity();
        if (cible instanceof Player || cible instanceof Monster) {
            return;
        }
        Player p = joueur(e.getDamager());
        if (p != null) {
            if (refuse(p, cible.getLocation())) {
                e.setCancelled(true);
            }
        } else if ((cible instanceof ArmorStand || cible instanceof Hanging) && bases.a(cible.getLocation()) != null) {
            e.setCancelled(true); // explosion, boule de feu…
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDecroche(HangingBreakEvent e) {
        if (e instanceof HangingBreakByEntityEvent be) {
            Player p = joueur(be.getRemover());
            if (p != null) {
                if (refuse(p, e.getEntity().getLocation())) {
                    e.setCancelled(true);
                }
                return;
            }
        }
        if (e.getCause() != HangingBreakEvent.RemoveCause.PHYSICS && bases.a(e.getEntity().getLocation()) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onAccroche(HangingPlaceEvent e) {
        if (e.getPlayer() != null && refuse(e.getPlayer(), e.getEntity().getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPoseEntite(EntityPlaceEvent e) {
        if (e.getPlayer() != null && refuse(e.getPlayer(), e.getEntity().getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onVehicule(VehicleDestroyEvent e) {
        Player p = joueur(e.getAttacker());
        if (p != null && refuse(p, e.getVehicle().getLocation())) {
            e.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ Le monde autour

    private boolean exposé(Block bl) {
        return bases.a(bl.getLocation()) != null || bases.armoire(bl) != null;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onExplosion(EntityExplodeEvent e) {
        e.blockList().removeIf(this::exposé);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onExplosionBloc(BlockExplodeEvent e) {
        e.blockList().removeIf(this::exposé);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onAllumage(BlockIgniteEvent e) {
        Base b = bases.a(e.getBlock().getLocation());
        if (b == null) {
            return;
        }
        Player p = e.getPlayer();
        if (p == null || !bases.peut(p, b)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBrule(BlockBurnEvent e) {
        if (bases.a(e.getBlock().getLocation()) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onCoule(BlockFromToEvent e) {
        Base vers = bases.a(e.getToBlock().getLocation());
        if (vers != null && vers != bases.a(e.getBlock().getLocation())) {
            e.setCancelled(true);
        }
    }

    private boolean pistonInterdit(Block piston, List<Block> blocs, BlockFace dir) {
        Base pb = bases.a(piston.getLocation());
        for (Block bl : blocs) {
            if (bases.armoire(bl) != null) {
                return true;
            }
            for (Block t : new Block[]{bl, bl.getRelative(dir), bl.getRelative(dir.getOppositeFace())}) {
                Base tb = bases.a(t.getLocation());
                if (tb != null && tb != pb) {
                    return true;
                }
            }
        }
        Base tete = bases.a(piston.getRelative(dir).getLocation());
        return tete != null && tete != pb;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPiston(BlockPistonExtendEvent e) {
        if (pistonInterdit(e.getBlock(), e.getBlocks(), e.getDirection())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPistonRetour(BlockPistonRetractEvent e) {
        if (pistonInterdit(e.getBlock(), e.getBlocks(), e.getDirection())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onArrache(EntityChangeBlockEvent e) {
        Base b = bases.a(e.getBlock().getLocation());
        if (b == null) {
            return;
        }
        Entity en = e.getEntity();
        if (en instanceof Player p) {
            if (!bases.peut(p, b)) {
                e.setCancelled(true);
            }
            return;
        }
        if (en instanceof FallingBlock || en instanceof Sheep || en instanceof Villager) {
            return;
        }
        e.setCancelled(true); // endermen, wither, ravageurs, zombies qui cassent les portes…
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPousse(StructureGrowEvent e) {
        Base source = bases.a(e.getLocation());
        e.getBlocks().removeIf(st -> {
            Base t = bases.a(st.getLocation());
            return t != null && t != source;
        });
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onTnt(TNTPrimeEvent e) {
        Base b = bases.a(e.getBlock().getLocation());
        if (b == null) {
            return;
        }
        Player p = joueur(e.getPrimingEntity());
        if (p != null) {
            if (!bases.peut(p, b)) {
                e.setCancelled(true);
            }
        } else if (e.getCause() != TNTPrimeEvent.PrimeCause.REDSTONE) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDeclenche(EntityInteractEvent e) {
        Base b = bases.a(e.getBlock().getLocation());
        if (b == null) {
            return;
        }
        Player p = joueur(e.getEntity());
        if (p != null) {
            if (!bases.peut(p, b)) {
                e.setCancelled(true);
            }
        } else if (e.getEntity() instanceof Projectile || e.getBlock().getType() == Material.FARMLAND) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntonnoir(InventoryMoveItemEvent e) {
        Location s = e.getSource().getLocation();
        if (s == null) {
            return;
        }
        Base bs = bases.a(s);
        if (bs == null) {
            return;
        }
        Location d = e.getDestination().getLocation();
        if (d == null || bases.a(d) != bs) {
            e.setCancelled(true);
        }
    }
}
