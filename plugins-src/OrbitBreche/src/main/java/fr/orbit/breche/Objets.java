package fr.orbit.breche;

import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import java.util.ArrayList;
import java.util.List;

/** Objets de BRÈCHE : armes, lame, pouvoirs, Noyau (modèles du pack « orbitbreche »), tenues d'équipe. */
final class Objets {

    static final String NS = "orbitbreche";

    private final OrbitBreche pl;

    Objets(OrbitBreche pl) {
        this.pl = pl;
    }

    ItemStack modele(Material base, String id) {
        ItemStack it = new ItemStack(base);
        ItemMeta meta = it.getItemMeta();
        meta.setItemModel(new NamespacedKey(NS, id));
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack objet(Material base, String modele, String nom, String... lore) {
        ItemStack it = modele == null ? new ItemStack(base) : modele(base, modele);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Txt.mm(nom));
        List<Component> l = new ArrayList<>();
        for (String s : lore) {
            l.add(Txt.mm("<#9AA4B2>" + s));
        }
        meta.lore(l);
        meta.addItemFlags(ItemFlag.values());
        meta.getPersistentDataContainer().set(pl.cleObjet(), org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        return it;
    }

    ItemStack arme(Arme a) {
        return objet(Material.IRON_HORSE_ARMOR, "arme_" + a.id, "<#E6EDF3><b>" + a.nom + "</b>",
                a.desc, "", "Clic droit : tirer" + (a.auto ? " (maintenu = rafale)" : ""), "F : recharger" + (a == Arme.ZENITH ? " · accroupi : lunette" : ""),
                a.degats + " dégâts · ×" + a.tete + " à la tête · chargeur " + a.chargeur);
    }

    ItemStack lame() {
        return objet(Material.IRON_HORSE_ARMOR, "lame", "<#E6EDF3><b>Lame</b>", "Clic gauche : 50 dégâts, 150 dans le dos.");
    }

    /** Case de pouvoir (0 à 3) : le nombre d'objets = charges ; l'ultime montre sa jauge. */
    ItemStack pouvoir(Combattant c, int i) {
        Agent.Pouvoir pv = c.agent.pouvoirs[i];
        boolean ult = pv.ultime();
        int charges = ult ? (c.ult >= pv.max() ? 1 : 0) : c.charges[i];
        String col = charges > 0 ? c.agent.couleur : "#5A6270";
        String etat = ult ? (c.ult >= pv.max() ? "<#E0B864><b>PRÊT</b>" : "<#9AA4B2>" + c.ult + "/" + pv.max())
                : charges > 0 ? "<#E6EDF3>×" + charges : "<#5A6270>vide";
        ItemStack it = objet(Material.PAPER, "pouvoir_" + pv.id(), "<" + col + "><b>" + pv.touche() + " · " + pv.nom() + "</b></" + col + "> " + etat,
                pv.desc(), "", "Clic droit : utiliser", pv.ultime() ? "Points : élimination, mort, pose, désamorçage."
                        : pv.prix() > 0 ? "Achat : " + pv.prix() + " ¤ (" + pv.max() + " max)" : "Gratuit" + (pv.recharge() > 0 ? " · recharge " + pv.recharge() + " s" : ""));
        it.setAmount(Math.max(1, Math.min(64, charges)));
        if (ult && charges > 0) {
            ItemMeta m = it.getItemMeta();
            m.setEnchantmentGlintOverride(true);
            it.setItemMeta(m);
        }
        return it;
    }

    ItemStack noyau() {
        return objet(Material.PAPER, "noyau", "<#E0B864><b>Noyau</b>", "Sur un site (A ou B) : tiens-le en main", "et reste accroupi 3,5 s pour le poser.");
    }

    ItemStack boutique() {
        return objet(Material.EMERALD, null, "<#7CFF8A><b>Boutique</b> <#9AA4B2>(clic droit)", "Armes, boucliers et pouvoirs.", "Seulement pendant la phase d'achat.");
    }

    ItemStack choixAgent() {
        return objet(Material.NETHER_STAR, null, "<#E0B864><b>Choisir son agent</b> <#9AA4B2>(clic droit)", "Cinq agents, quatre pouvoirs chacun.");
    }

    ItemStack quitter() {
        return objet(Material.RED_DYE, null, "<#E0484A><b>Quitter la partie</b>", "Retour à la salle d'arcade.");
    }

    // ------------------------------------------------------------------ tenues

    private static ItemStack cuir(Material m, int rgb) {
        ItemStack it = new ItemStack(m);
        LeatherArmorMeta meta = (LeatherArmorMeta) it.getItemMeta();
        meta.setColor(Color.fromRGB(rgb & 0xFFFFFF));
        meta.addItemFlags(ItemFlag.values());
        it.setItemMeta(meta);
        return it;
    }

    private static int equipe(int e) {
        return e == 0 ? 0x3A7FFF : 0xE0B864;
    }

    /** Casque et veste à la couleur de l'équipe, jambières et bottes à celle de l'agent. */
    void habiller(Combattant c) {
        if (c.p == null) {
            habillerBot(c);
            return;
        }
        int ag = c.agent == null ? 0x5A6270 : c.agent.rgb();
        c.p.getInventory().setHelmet(cuir(Material.LEATHER_HELMET, equipe(c.equipe)));
        c.p.getInventory().setChestplate(cuir(Material.LEATHER_CHESTPLATE, equipe(c.equipe)));
        c.p.getInventory().setLeggings(cuir(Material.LEATHER_LEGGINGS, ag));
        c.p.getInventory().setBoots(cuir(Material.LEATHER_BOOTS, ag));
    }

    void habillerBot(Combattant b) {
        if (b.m == null || !b.m.isValid()) {
            return;
        }
        EntityEquipment eq = b.m.getEquipment();
        int ag = b.agent.rgb();
        eq.setHelmet(cuir(Material.LEATHER_HELMET, equipe(b.equipe)));
        eq.setChestplate(cuir(Material.LEATHER_CHESTPLATE, equipe(b.equipe)));
        eq.setLeggings(cuir(Material.LEATHER_LEGGINGS, ag));
        eq.setBoots(cuir(Material.LEATHER_BOOTS, ag));
        Arme a = b.arme();
        eq.setItemInMainHand(a == null ? null : modele(Material.IRON_HORSE_ARMOR, "arme_" + a.id));
    }
}
