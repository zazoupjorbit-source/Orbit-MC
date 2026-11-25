package fr.orbit.chasseurs;

import fr.orbit.chasseurs.Catalogue.Level;
import fr.orbit.chasseurs.Catalogue.Mode;
import fr.orbit.chasseurs.Catalogue.Paint;
import fr.orbit.chasseurs.Catalogue.ShipType;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import java.util.ArrayList;
import java.util.List;

/** Menu du PNJ (choix du mode, niveau des bots) et hangar (vaisseaux et couleurs débloqués en jouant). */
final class Menu implements Listener {

    private static final class Holder implements InventoryHolder {
        final boolean hangar;
        Inventory inv;

        Holder(boolean hangar) {
            this.hangar = hangar;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private static final int[] SHIP_SLOTS = {2, 3, 4, 5, 6};
    private static final int[] PAINT_SLOTS = {19, 20, 21, 22, 23, 24, 25, 30, 31, 32};
    private static final Material[] LEVEL_ICON = {Material.LIME_DYE, Material.LIGHT_BLUE_DYE, Material.YELLOW_DYE, Material.RED_DYE};

    private final OrbitChasseurs plugin;

    Menu(OrbitChasseurs plugin) {
        this.plugin = plugin;
    }

    private static ItemStack icon(ItemStack it, String name, String... lore) {
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Txt.mm(name));
        List<Component> lines = new ArrayList<>();
        for (String l : lore) {
            lines.add(Txt.mm(l));
        }
        meta.lore(lines);
        meta.addItemFlags(ItemFlag.values());
        it.setItemMeta(meta);
        return it;
    }

    private static ItemStack glint(ItemStack it) {
        ItemMeta meta = it.getItemMeta();
        meta.setEnchantmentGlintOverride(true);
        it.setItemMeta(meta);
        return it;
    }

    void open(Player p) {
        plugin.profiles().load(p, pr -> {
            if (!p.isOnline()) {
                return;
            }
            Holder h = new Holder(false);
            Inventory inv = Bukkit.createInventory(h, 27, Txt.mm("<#1B2A4A><b>CHASSEURS D'ORBITE</b>"));
            h.inv = inv;
            fillMain(inv, pr);
            p.openInventory(inv);
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.5f, 1.6f);
        });
    }

    private void fillMain(Inventory inv, Profiles.Profile pr) {
        Level lv = Level.of(pr.botLevel);
        inv.setItem(10, icon(new ItemStack(Material.NETHER_STAR), "<#62CFF4><b>MÊLÉE GÉNÉRALE</b>",
                "<#9AA4B2>Chacun pour soi dans la " + Mode.MELEE.arene + ".", "<#9AA4B2>6 vaisseaux, les places libres sont tenues par des bots.",
                "<#9AA4B2>Premier à 15 éliminations, ou le meilleur après 4 minutes.", "", "<#7CFF8A>▶ Clic pour décoller"));
        inv.setItem(12, icon(new ItemStack(Material.GOLDEN_SWORD), "<#E0B864><b>DUO 2v2</b>",
                "<#9AA4B2>Bleus contre Ors autour de l'" + Mode.DUO.arene + ".", "<#9AA4B2>Deux amis volent dans la même équipe ;",
                "<#9AA4B2>à quatre, c'est un vrai 2 contre 2.", "<#9AA4B2>Première équipe à 12 éliminations.", "", "<#7CFF8A>▶ Clic pour décoller"));
        inv.setItem(14, icon(new ItemStack(Material.CHEST_MINECART), "<#7CFF8A><b>CONVOI</b> <#9AA4B2>(coopératif)",
                "<#9AA4B2>Escorte l'ORBIT-Cargo sur la " + Mode.CONVOI.arene + ".", "<#9AA4B2>Des vagues d'écumeurs pirates de plus en plus fortes,",
                "<#9AA4B2>une frégate toutes les 4 vagues. 1 à 4 pilotes.", "<#9AA4B2>Seul, un ailier t'accompagne.", "", "<#7CFF8A>▶ Clic pour décoller"));
        inv.setItem(16, icon(plugin.model(pr.ship.id, pr.paint.rgb), "<#C79BFF><b>HANGAR</b>",
                "<#9AA4B2>Vaisseau : <#E6EDF3>" + pr.ship.label, "<#9AA4B2>Couleur : <" + Txt.hex(pr.paint.rgb) + ">" + pr.paint.label,
                "<#9AA4B2>Tout se débloque en jouant, rien ne s'achète.", "", "<#7CFF8A>▶ Clic pour choisir"));
        inv.setItem(22, icon(new ItemStack(LEVEL_ICON[lv.ordinal()]), "<#E6EDF3><b>Niveau des bots :</b> <" + lv.color + "><b>" + lv.label + "</b>",
                "<#9AA4B2>Recrue ×0,75 · Pilote ×1 · As ×1,3 · Légende ×1,6", "<#9AA4B2>(multiplicateur de points)", "", "<#7CFF8A>▶ Clic pour changer"));
        int next = Profiles.xpFor(pr.level() + 1);
        inv.setItem(18, icon(new ItemStack(Material.NAME_TAG), "<#62CFF4><b>Pilote niveau " + pr.level() + "</b>",
                "<#9AA4B2>" + Txt.n(pr.xp) + " XP · encore " + Txt.n(next - pr.xp) + " avant le niveau " + (pr.level() + 1),
                "<#9AA4B2>" + pr.games + " parties · " + pr.wins + " victoires", "<#9AA4B2>" + pr.kills + " éliminations · " + pr.deaths + " pertes",
                "<#9AA4B2>Meilleure vague du Convoi : " + pr.bestWave));
        inv.setItem(20, icon(new ItemStack(Material.GOLD_INGOT), "<#E0B864><b>Classement</b>", "<#9AA4B2>Les 10 meilleurs scores de chaque mode.", "", "<#7CFF8A>▶ Clic"));
        inv.setItem(26, icon(new ItemStack(Material.BOOK), "<#E6EDF3><b>Comment piloter</b>",
                "<#9AA4B2>Ton vaisseau vole là où tu regardes.", "<#E6EDF3>Z</#E6EDF3><#9AA4B2> poussée · </#9AA4B2><#E6EDF3>S</#E6EDF3><#9AA4B2> frein · </#9AA4B2><#E6EDF3>Q/D</#E6EDF3><#9AA4B2> glisser de côté",
                "<#E6EDF3>Espace</#E6EDF3><#9AA4B2> boost · </#9AA4B2><#E6EDF3>Maj</#E6EDF3><#9AA4B2> dérapage (tu gardes ta lancée)",
                "<#E6EDF3>Clic</#E6EDF3><#9AA4B2> laser, clic droit maintenu = rafale", "<#9AA4B2>L'arme chauffe : laisse-la refroidir.",
                "<#E6EDF3>F</#E6EDF3><#9AA4B2> ou </#9AA4B2><#E6EDF3>2</#E6EDF3><#9AA4B2> missile à tête chercheuse (vise d'abord)",
                "<#E6EDF3>3</#E6EDF3><#9AA4B2> nanoréparation (toutes les 45 s)", "<#9AA4B2>Anneaux : boost rechargé. Cubes : réparation,", "<#9AA4B2>missiles, survolteur."));
    }

    void openHangar(Player p) {
        plugin.profiles().load(p, pr -> {
            if (!p.isOnline()) {
                return;
            }
            Holder h = new Holder(true);
            Inventory inv = Bukkit.createInventory(h, 45, Txt.mm("<#1B2A4A><b>HANGAR</b> · pilote niveau " + pr.level()));
            h.inv = inv;
            fillHangar(inv, pr);
            p.openInventory(inv);
        });
    }

    private void fillHangar(Inventory inv, Profiles.Profile pr) {
        inv.clear();
        for (int i = 0; i < ShipType.HANGAR.length; i++) {
            ShipType t = ShipType.HANGAR[i];
            boolean has = pr.has(t), selected = pr.ship == t;
            ItemStack it = icon(plugin.model(t.id, has ? pr.paint.rgb : 0x4A5060), (has ? "<#E6EDF3>" : "<#5A6270>🔒 ") + "<b>" + t.label + "</b>"
                            + (selected ? " <#7CFF8A>✔ choisi" : ""),
                    "<#9AA4B2>" + t.text, "",
                    "<#7CFF8A>Coque " + (int) t.hull + "</#7CFF8A> <#5A6270>·</#5A6270> <#62CFF4>Bouclier " + (int) t.shield,
                    "<#E6EDF3>Vitesse " + (int) Math.round(t.speed * 100) + " %</#E6EDF3> <#5A6270>·</#5A6270> <#E6EDF3>Virage " + (int) Math.round(t.turn * 20) + "°/s",
                    "<#FFB000>Laser " + (int) t.damage + " × " + String.format("%.1f", 20.0 / t.cadence) + "/s</#FFB000> <#5A6270>·</#5A6270> <#FF4FD8>" + t.missiles + " missiles",
                    "", has ? (selected ? "<#7CFF8A>Ton vaisseau actuel" : "<#7CFF8A>▶ Clic pour le prendre") : "<#E0B864>Se débloque au niveau de pilote " + t.level);
            inv.setItem(SHIP_SLOTS[i], selected ? glint(it) : it);
        }
        Paint[] paints = Paint.values();
        for (int i = 0; i < paints.length && i < PAINT_SLOTS.length; i++) {
            Paint c = paints[i];
            boolean has = c.unlocked(pr), selected = pr.paint == c;
            ItemStack it = new ItemStack(Material.LEATHER_HORSE_ARMOR);
            LeatherArmorMeta meta = (LeatherArmorMeta) it.getItemMeta();
            meta.setColor(Color.fromRGB(has ? c.rgb : 0x3A3E46));
            it.setItemMeta(meta);
            icon(it, (has ? "<" + Txt.hex(c.rgb) + ">" : "<#5A6270>🔒 ") + "<b>" + c.label + "</b>" + (selected ? " <#7CFF8A>✔" : ""),
                    has ? (selected ? "<#7CFF8A>Ta couleur actuelle" : "<#7CFF8A>▶ Clic pour la prendre") : "<#E0B864>Se débloque : " + c.how(),
                    "<#5A6270>En duo, l'équipe impose sa couleur.");
            inv.setItem(PAINT_SLOTS[i], selected ? glint(it) : it);
        }
        inv.setItem(40, icon(new ItemStack(Material.ARROW), "<#9AA4B2>↩ Retour"));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder h)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player p) || event.getClickedInventory() != event.getInventory()) {
            return;
        }
        Profiles.Profile pr = plugin.profiles().cached(p);
        int slot = event.getSlot();
        if (!h.hangar) {
            switch (slot) {
                case 10 -> start(p, Mode.MELEE);
                case 12 -> start(p, Mode.DUO);
                case 14 -> start(p, Mode.CONVOI);
                case 16 -> openHangar(p);
                case 20 -> {
                    p.closeInventory();
                    plugin.showTop(p, null);
                }
                case 22 -> {
                    pr.botLevel = pr.botLevel % 4 + 1;
                    plugin.profiles().saveChoices(pr);
                    fillMain(h.inv, pr);
                    p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1f + pr.botLevel * 0.15f);
                }
                default -> {
                }
            }
            return;
        }
        if (slot == 40) {
            open(p);
            return;
        }
        for (int i = 0; i < SHIP_SLOTS.length; i++) {
            if (SHIP_SLOTS[i] == slot) {
                ShipType t = ShipType.HANGAR[i];
                if (!pr.has(t)) {
                    deny(p, "Le " + t.label + " se débloque au niveau de pilote " + t.level + ".");
                    return;
                }
                pr.ship = t;
                chosen(p, h, pr);
                return;
            }
        }
        Paint[] paints = Paint.values();
        for (int i = 0; i < PAINT_SLOTS.length && i < paints.length; i++) {
            if (PAINT_SLOTS[i] == slot) {
                if (!paints[i].unlocked(pr)) {
                    deny(p, "Cette couleur se débloque : " + paints[i].how() + ".");
                    return;
                }
                pr.paint = paints[i];
                chosen(p, h, pr);
                return;
            }
        }
    }

    private void start(Player p, Mode mode) {
        p.closeInventory();
        plugin.games().play(p, mode, null);
    }

    private void chosen(Player p, Holder h, Profiles.Profile pr) {
        plugin.profiles().saveChoices(pr);
        fillHangar(h.inv, pr);
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1.8f);
    }

    private static void deny(Player p, String why) {
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 0.6f, 0.5f);
        Txt.send(p, "<#FFB000>" + why);
    }
}
