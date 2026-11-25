package fr.orbit.mmo;

import fr.orbit.mmo.classes.ClassDef;
import fr.orbit.mmo.classes.SpecDef;
import fr.orbit.mmo.item.Boost;
import fr.orbit.mmo.item.Items;
import fr.orbit.mmo.item.Loot;
import fr.orbit.mmo.item.Rarity;
import fr.orbit.mmo.item.Rune;
import fr.orbit.mmo.player.PlayerData;
import fr.orbit.mmo.registry.Defs;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Pont avec OrbitStaff (appelé par réflexion, aucune dépendance) : objets du MMO pour le catalogue du staff
 * et lignes de stats pour le livre.
 */
final class StaffPont {

    private StaffPont() {
    }

    static Map<String, List<ItemStack>> catalogue(OrbitMMO plugin) {
        Map<String, List<ItemStack>> out = new LinkedHashMap<>();
        List<ItemStack> armes = new ArrayList<>();
        for (int t = 0; t < Loot.TIERS; t++) {
            for (Loot.Base b : Loot.Base.values()) {
                armes.add(Loot.shopWeapon(b, t));
            }
        }
        out.put("Armes (8 paliers)", armes);

        List<ItemStack> legendaires = new ArrayList<>();
        Random rnd = new Random(42);
        int top = Loot.TIERS - 1;
        for (Rarity r : new Rarity[]{Rarity.EPIQUE, Rarity.LEGENDAIRE}) {
            for (Loot.Base b : Loot.Base.values()) {
                legendaires.add(Loot.weapon(b, top, r, Loot.TIER_LEVEL[top], Loot.TIER_LEVEL[top], rnd));
            }
            legendaires.add(Loot.shield(top, r, Loot.TIER_LEVEL[top], Loot.TIER_LEVEL[top], rnd));
        }
        out.put("Épiques et légendaires", legendaires);

        List<ItemStack> armures = new ArrayList<>();
        for (int t = 0; t < Loot.TIERS; t++) {
            for (Loot.Slot s : Loot.Slot.values()) {
                armures.add(Loot.shopArmor(s, t, null));
            }
        }
        out.put("Armures", armures);

        List<ItemStack> boucliers = new ArrayList<>();
        for (int t = 0; t < Loot.TIERS; t++) {
            boucliers.add(Loot.shopShield(t));
        }
        out.put("Boucliers", boucliers);

        List<ItemStack> runes = new ArrayList<>();
        for (int rang = 1; rang <= Rune.MAX_RANK; rang++) {
            for (Rune r : Rune.values()) {
                runes.add(r.item(rang));
            }
        }
        out.put("Runes", runes);

        List<ItemStack> conso = new ArrayList<>();
        conso.add(Items.potion(16));
        conso.add(Items.manaPotion(16));
        conso.add(Items.healCrystal());
        for (Boost b : Boost.values()) {
            conso.add(b.item(1));
        }
        out.put("Consommables", conso);

        List<ItemStack> mats = new ArrayList<>();
        Set<String> vus = new HashSet<>();
        for (Defs.MobDef m : plugin.registry().mobs().values()) {
            Defs.DropDef d = m.drop();
            if (d != null && vus.add(d.name())) {
                ItemStack it = Items.material(d);
                it.setAmount(16);
                mats.add(it);
            }
        }
        if (!mats.isEmpty()) {
            out.put("Matériaux de monstres", mats);
        }
        return out;
    }

    static List<String> stats(OrbitMMO plugin, Player p) {
        PlayerData d = plugin.players().get(p);
        List<String> l = new ArrayList<>();
        if (d == null) {
            return l;
        }
        if (!d.hasCharacter()) {
            l.add("<dark_gray>Pas encore de personnage.");
            return l;
        }
        ClassDef c = plugin.classes().get(d.classId);
        SpecDef s = c == null || d.specId == null ? null : c.specs.get(d.specId);
        l.add("<dark_gray>Faction : <black>" + d.faction.label);
        l.add("<dark_gray>Race : <black>" + d.race);
        l.add("<dark_gray>Classe : <black>" + (c == null ? d.classId : c.name(d.faction)));
        if (s != null) {
            l.add("<dark_gray>Spé : <black>" + s.name);
        }
        l.add("<dark_gray>Niveau : <black>" + d.level + " <dark_gray>(" + d.xp + " xp)");
        l.add("<dark_gray>Or : <black>" + d.gold);
        l.add("<dark_gray>Étage : <black>" + d.floor + " <dark_gray>Pilier : <black>" + d.towerCleared);
        l.add("<dark_gray>Monstres tués : <black>" + d.kills);
        l.add("<dark_gray>Joueurs tués : <black>" + d.playerKills + " <dark_gray>Morts : <black>" + d.deaths);
        var g = plugin.guilds().of(p.getUniqueId());
        l.add("<dark_gray>Guilde : <black>" + (g == null ? "aucune" : g.name()));
        l.add("<dark_gray>Montures : <black>" + d.mounts.size() + " <dark_gray>Auberge : <black>" + (d.inn == null ? "—" : d.inn));
        return l;
    }
}
