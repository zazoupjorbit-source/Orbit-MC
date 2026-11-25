package fr.orbit.staff;

import net.kyori.adventure.text.Component;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Les outils de la barre du mode staff et ce qu'ils font. */
public final class Outils implements Listener {

    public enum Outil {
        JOUEURS(0, Material.COMPASS, "<#4aa3ff><bold>Joueurs", "Clic droit : liste des joueurs (TP, panel)", "Clic gauche : TP sur un joueur au hasard"),
        PANEL(1, Material.BLAZE_ROD, "<#f5c542><bold>Panel joueur", "Clic droit sur un joueur (ou visé de loin)", "Freeze, TP, inventaire, sanctions..."),
        LIVRE(2, Material.WRITTEN_BOOK, "<#f5c542><bold>Livre de stats", "Clic droit sur un joueur (ou visé de loin)", "Infos, combat, modération, stats du mode"),
        FREEZE(3, Material.PACKED_ICE, "<#7fd4ff><bold>Freeze", "Clic droit sur un joueur : geler / dégeler"),
        INVENTAIRE(4, Material.CHEST, "<#c58a4a><bold>Inventaire", "Clic droit sur un joueur : son inventaire", "Accroupi : son coffre de l'End"),
        CATALOGUE(5, Material.NETHER_STAR, "<#d77bff><bold>Catalogue d'objets", "Objets du mode de jeu de ce serveur", "+ tous les objets Minecraft"),
        VITESSE(6, Material.FEATHER, "<white><bold>Vitesse de vol", "Clic : change la vitesse (1 à 4)"),
        VANISH(7, Material.LIME_DYE, "<#55ff88><bold>Invisible", "Clic : te montrer / te cacher"),
        QUITTER(8, Material.RED_DYE, "<#ff5555><bold>Quitter le mode staff", "Rend ton inventaire et ta position");

        final int slot;
        final Material mat;
        final String nom;
        final String[] lore;

        Outil(int slot, Material mat, String nom, String... lore) {
            this.slot = slot;
            this.mat = mat;
            this.nom = nom;
            this.lore = lore;
        }
    }

    private static final float[] VITESSES = {0.1f, 0.2f, 0.35f, 0.6f};

    private final OrbitStaff plugin;
    private final java.util.Map<java.util.UUID, Long> dernierClicEntite = new java.util.concurrent.ConcurrentHashMap<>();

    public Outils(OrbitStaff plugin) {
        this.plugin = plugin;
    }

    private static NamespacedKey cle(OrbitStaff plugin) {
        return new NamespacedKey(plugin, "outil");
    }

    public static ItemStack item(OrbitStaff plugin, Outil o, boolean invisible) {
        Material mat = o == Outil.VANISH && !invisible ? Material.GRAY_DYE : o.mat;
        String nom = o == Outil.VANISH ? (invisible ? "<#55ff88><bold>Invisible <gray>(actif)" : "<gray><bold>Visible <dark_gray>(clic pour te cacher)") : o.nom;
        ItemStack it = Txt.icon(mat, nom, o.lore);
        ItemMeta meta = it.getItemMeta();
        meta.getPersistentDataContainer().set(cle(plugin), PersistentDataType.STRING, o.name());
        if (o == Outil.LIVRE) {
            // Livre écrit sans contenu : juste l'apparence.
            meta.setEnchantmentGlintOverride(true);
        }
        it.setItemMeta(meta);
        return it;
    }

    public static void donner(OrbitStaff plugin, Player p) {
        for (Outil o : Outil.values()) {
            p.getInventory().setItem(o.slot, item(plugin, o, plugin.staffMode().invisible(p)));
        }
        p.getInventory().setHeldItemSlot(0);
    }

    public static Outil de(OrbitStaff plugin, ItemStack it) {
        if (it == null || it.isEmpty() || !it.hasItemMeta()) {
            return null;
        }
        String v = it.getItemMeta().getPersistentDataContainer().get(cle(plugin), PersistentDataType.STRING);
        if (v == null) {
            return null;
        }
        try {
            return Outil.valueOf(v);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Outil enMain(Player p) {
        if (!plugin.staffMode().actif(p)) {
            return null;
        }
        return de(plugin, p.getInventory().getItemInMainHand());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntite(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Outil o = enMain(e.getPlayer());
        if (o == null) {
            return;
        }
        e.setCancelled(true);
        dernierClicEntite.put(e.getPlayer().getUniqueId(), System.currentTimeMillis());
        if (e.getRightClicked() instanceof Player cible && !cible.hasMetadata("NPC")) {
            surJoueur(e.getPlayer(), o, cible);
        } else {
            utiliser(e.getPlayer(), o, false);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClic(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || e.getAction() == Action.PHYSICAL) {
            return;
        }
        Player p = e.getPlayer();
        Outil o = enMain(p);
        if (o == null) {
            return;
        }
        e.setCancelled(true);
        // Le clic sur un joueur déclenche aussi parfois un clic « dans le vide » : une seule action.
        Long dernier = dernierClicEntite.get(p.getUniqueId());
        if (dernier != null && System.currentTimeMillis() - dernier < 150) {
            return;
        }
        boolean gauche =e.getAction() == Action.LEFT_CLICK_AIR || e.getAction() == Action.LEFT_CLICK_BLOCK;
        utiliser(p, o, gauche);
    }

    /** Un outil en main ne fait jamais de dégâts. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFrappe(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p && enMain(p) != null) {
            e.setCancelled(true);
        }
    }

    private void utiliser(Player p, Outil o, boolean gauche) {
        switch (o) {
            case JOUEURS -> {
                if (gauche) {
                    tpHasard(p);
                } else {
                    Menus.joueurs(plugin, p, Menus.Usage.TP, 0);
                }
            }
            case PANEL, LIVRE, FREEZE, INVENTAIRE -> {
                if (gauche) {
                    return;
                }
                Player vise = viser(p);
                if (vise != null) {
                    surJoueur(p, o, vise);
                } else {
                    Menus.joueurs(plugin, p, switch (o) {
                        case LIVRE -> Menus.Usage.LIVRE;
                        case FREEZE -> Menus.Usage.FREEZE;
                        case INVENTAIRE -> Menus.Usage.INVENTAIRE;
                        default -> Menus.Usage.PANEL;
                    }, 0);
                }
            }
            case CATALOGUE -> {
                if (p.hasPermission("orbitstaff.catalogue")) {
                    Menus.catalogue(plugin, p, p);
                } else {
                    Txt.err(p, "Il te faut orbitstaff.catalogue.");
                }
            }
            case VITESSE -> {
                int i = 0;
                for (int k = 0; k < VITESSES.length; k++) {
                    if (Math.abs(p.getFlySpeed() - VITESSES[k]) < 0.01f) {
                        i = k;
                    }
                }
                int n = (i + (gauche ? VITESSES.length - 1 : 1)) % VITESSES.length;
                p.setFlySpeed(VITESSES[n]);
                p.sendActionBar(Txt.mm("<white>Vitesse de vol : <#f5c542>" + (n + 1) + "<gray>/4"));
            }
            case VANISH -> {
                boolean on = !plugin.staffMode().invisible(p);
                plugin.staffMode().setInvisible(p, on);
                p.getInventory().setItem(Outil.VANISH.slot, item(plugin, Outil.VANISH, on));
                p.sendActionBar(Txt.mm(on ? "<#55ff88>Tu es invisible" : "<#ff8c42>Tu es visible des joueurs"));
            }
            case QUITTER -> plugin.staffMode().sortir(p);
        }
    }

    public void surJoueur(Player p, Outil o, Player cible) {
        switch (o) {
            case LIVRE -> Livre.ouvrir(plugin, p, cible.getUniqueId());
            case FREEZE -> {
                if (p.hasPermission("orbitstaff.freeze")) {
                    plugin.freeze().basculer(p, cible, "");
                }
            }
            case INVENTAIRE -> {
                if (p.isSneaking()) {
                    Inspection.ender(plugin, p, cible);
                } else {
                    Inspection.inventaire(plugin, p, cible);
                }
            }
            default -> Menus.panel(plugin, p, cible);
        }
    }

    /** Joueur visé à 60 blocs (hors invisibles). */
    private Player viser(Player p) {
        RayTraceResult r = p.getWorld().rayTrace(p.getEyeLocation(), p.getEyeLocation().getDirection(), 60,
                FluidCollisionMode.NEVER, true, 0.6, ent -> ent instanceof Player o && !o.equals(p) && !o.hasMetadata("NPC"));
        Entity e = r == null ? null : r.getHitEntity();
        return e instanceof Player o ? o : null;
    }

    private void tpHasard(Player p) {
        List<Player> choix = new ArrayList<>();
        for (Player o : p.getServer().getOnlinePlayers()) {
            if (!o.equals(p) && !plugin.staffMode().actif(o) && !o.hasMetadata("NPC")) {
                choix.add(o);
            }
        }
        if (choix.isEmpty()) {
            p.sendActionBar(Component.text("Personne d'autre en ligne."));
            return;
        }
        Collections.shuffle(choix);
        Player o = choix.get(0);
        p.teleportAsync(o.getLocation());
        p.sendActionBar(Txt.mm("<gray>TP sur <#f5c542>" + Txt.esc(o.getName())));
    }
}
