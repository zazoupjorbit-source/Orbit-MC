package fr.orbit.tycoon;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Baguettes de vente (sell wands), vendues au rayon Items de la boutique : clic droit sur un coffre de SON île
 * → toutes les ressources de générateur qu'il contient sont vendues au prix de vente × multiplicateur.
 * Chaque baguette a un nombre d'utilisations (une utilisation = un coffre vendu avec au moins un objet).
 */
final class Baguettes implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    /** Les 3 baguettes de la boutique (réglages boutique.baguettes.&lt;id&gt; : prix, utilisations). */
    enum Modele {
        ORBITALE("orbitale", "Baguette orbitale", 1.5, 100, 400, "baguette_1", "#62CFF4"),
        NEBULAIRE("nebulaire", "Baguette nébulaire", 2.0, 100, 900, "baguette_2", "#C77DFF"),
        STELLAIRE("stellaire", "Baguette stellaire", 3.0, 50, 1500, "baguette_3", "#E0B864");

        final String id, nom, icone, couleur;
        final double mult;
        final int utilisations;
        final long prix;

        Modele(String id, String nom, double mult, int utilisations, long prix, String icone, String couleur) {
            this.id = id; this.nom = nom; this.mult = mult; this.utilisations = utilisations; this.prix = prix;
            this.icone = icone; this.couleur = couleur;
        }
    }

    private final OrbitTycoon plugin;
    private final NamespacedKey cleMult, cleReste, cleModele;

    Baguettes(OrbitTycoon plugin) {
        this.plugin = plugin;
        this.cleMult = new NamespacedKey(plugin, "baguette_mult");
        this.cleReste = new NamespacedKey(plugin, "baguette_reste");
        this.cleModele = new NamespacedKey(plugin, "baguette_modele");
    }

    long prix(Modele m) { return plugin.getConfig().getLong("boutique.baguettes." + m.id + ".prix", m.prix); }
    int utilisations(Modele m) { return plugin.getConfig().getInt("boutique.baguettes." + m.id + ".utilisations", m.utilisations); }

    static String fois(double v) {
        return "×" + (v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.FRANCE, "%.1f", v));
    }

    /** Une baguette neuve (ou avec reste utilisations). */
    ItemStack objet(Modele md, int reste) {
        ItemStack it = new ItemStack(Material.BLAZE_ROD);
        it.editMeta(m -> {
            m.itemName(MM.deserialize("<" + md.couleur + "><bold>" + md.nom + " " + fois(md.mult) + "</bold>"));
            m.lore(lore(md, reste).stream().map(l -> MM.deserialize("<!italic>" + l)).toList());
            m.setItemModel(new NamespacedKey("orbittycoon", "nav_" + md.icone));
            m.setEnchantmentGlintOverride(true);
            m.setMaxStackSize(1);
            var pdc = m.getPersistentDataContainer();
            pdc.set(cleModele, PersistentDataType.STRING, md.id);
            pdc.set(cleMult, PersistentDataType.DOUBLE, md.mult);
            pdc.set(cleReste, PersistentDataType.INTEGER, reste);
        });
        return it;
    }

    List<String> lore(Modele md, int reste) {
        List<String> l = new ArrayList<>();
        l.add("<dark_gray>Baguette de vente");
        l.add("");
        l.add("<gray>Clic droit sur un coffre de ton île :");
        l.add("<gray>vend ses ressources de générateur");
        l.add("<gray>à <" + md.couleur + "><bold>" + fois(md.mult) + "</bold><gray> le prix de vente.");
        l.add("");
        l.add("<gray>Utilisations : <white>" + reste + " <dark_gray>/ " + utilisations(md));
        return l;
    }

    private Modele modele(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        String id = it.getItemMeta().getPersistentDataContainer().get(cleModele, PersistentDataType.STRING);
        if (id == null) return null;
        for (Modele m : Modele.values()) if (m.id.equals(id)) return m;
        return null;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void clic(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        ItemStack main = p.getInventory().getItemInMainHand();
        Modele md = modele(main);
        if (md == null) return;
        e.setCancelled(true);
        Block b = e.getClickedBlock();
        if (b == null || !(b.getState(false) instanceof Container c)) {
            p.sendActionBar(MM.deserialize("<gray>Clique sur un <white>coffre</white> de ton île."));
            return;
        }
        IlesJoueurs iles = plugin.ilesJoueurs();
        IlesJoueurs.Ile ile = iles == null ? null : iles.ileDe(p.getUniqueId());
        if (ile == null || !IlesJoueurs.estMonde(b.getWorld()) || iles.ileA(b.getX(), b.getZ()) != ile || !iles.dedans(ile, b.getX(), b.getZ())) {
            OrbitTycoon.msg(p, "<#FF4D4D>La baguette ne marche que sur les coffres de ton île.");
            return;
        }
        double mult = main.getItemMeta().getPersistentDataContainer().getOrDefault(cleMult, PersistentDataType.DOUBLE, md.mult);
        int reste = main.getItemMeta().getPersistentDataContainer().getOrDefault(cleReste, PersistentDataType.INTEGER, utilisations(md));
        int vendus = plugin.generateurs().vendreConteneur(p, c.getInventory(), mult);
        if (vendus == 0) {
            p.sendActionBar(MM.deserialize("<gray>Aucune ressource de générateur dans ce coffre."));
            return;
        }
        Location l = b.getLocation().add(0.5, 1.1, 0.5);
        b.getWorld().spawnParticle(Particle.END_ROD, l, 12, 0.3, 0.2, 0.3, 0.02);
        reste--;
        if (reste <= 0) {
            p.getInventory().setItemInMainHand(null);
            OrbitTycoon.msg(p, "<gray>Ta <" + md.couleur + ">" + md.nom + "<gray> est épuisée.");
            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.8f, 1f);
        } else {
            p.getInventory().setItemInMainHand(objet(md, reste));
            p.sendActionBar(MM.deserialize("<" + md.couleur + ">" + fois(mult) + " <gray>· utilisations restantes : <white>" + reste));
        }
    }
}
