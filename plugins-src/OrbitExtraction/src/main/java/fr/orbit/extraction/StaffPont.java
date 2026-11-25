package fr.orbit.extraction;

import fr.orbit.extraction.gun.GunRegistry;
import fr.orbit.extraction.raid.Profile;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pont avec OrbitStaff (appelé par réflexion, aucune dépendance) : objets de l'Extraction pour le catalogue
 * du staff (mêmes « specs » que /extraction give) et lignes de stats pour le livre.
 */
final class StaffPont {

    private StaffPont() {
    }

    static Map<String, List<ItemStack>> catalogue(OrbitExtraction plugin) {
        Map<String, List<ItemStack>> out = new LinkedHashMap<>();
        List<String> armes = new ArrayList<>();
        for (GunRegistry.GunDef g : plugin.guns().guns()) {
            armes.add("arme:" + g.id());
        }
        ajouter(plugin, out, "Armes", armes);
        List<String> mun = new ArrayList<>();
        for (GunRegistry.AmmoDef a : plugin.guns().ammos()) {
            mun.add("munition:" + a.id() + ":" + a.material().getMaxStackSize());
        }
        plugin.guns().grenades().forEach(g -> mun.add("grenade:" + g.id() + ":4"));
        ajouter(plugin, out, "Munitions et grenades", mun);
        List<String> soins = new ArrayList<>();
        plugin.meds().defs().forEach(m -> soins.add("soin:" + m.id() + ":2"));
        ajouter(plugin, out, "Soins", soins);
        List<String> armures = new ArrayList<>();
        plugin.armors().defs().forEach(a -> a.pieces().keySet().forEach(pc -> armures.add("armure:" + a.id() + ":" + pc.id())));
        ajouter(plugin, out, "Armures", armures);
        List<String> veh = new ArrayList<>();
        plugin.vehicles().toolIds().forEach(v -> veh.add("vehicule:" + v + ":1"));
        ajouter(plugin, out, "Véhicules", veh);
        return out;
    }

    private static void ajouter(OrbitExtraction plugin, Map<String, List<ItemStack>> out, String cat, List<String> specs) {
        List<ItemStack> items = new ArrayList<>();
        for (String spec : specs) {
            try {
                items.addAll(plugin.specs().create(spec, true));
            } catch (RuntimeException ignored) {
                // spec cassée : on passe
            }
        }
        if (!items.isEmpty()) {
            out.put(cat, items);
        }
    }

    static List<String> stats(OrbitExtraction plugin, Player p) {
        Profile pr = plugin.profiles().get(p);
        List<String> l = new ArrayList<>();
        if (pr == null) {
            return l;
        }
        var rang = plugin.ranks().of(p);
        l.add("<dark_gray>Rang : <black>" + (rang == null ? "—" : rang.name()));
        l.add("<dark_gray>Crédits : <black>" + plugin.phone().credits(p));
        l.add("<dark_gray>État : " + (pr.inRaid ? "<dark_red>en raid" : "<dark_green>planque"));
        l.add("<dark_gray>Raids : <black>" + pr.raids);
        l.add("<dark_gray>Extraits : <dark_green>" + pr.survived + " <dark_gray>Morts : <dark_red>" + pr.deaths);
        int taux = pr.raids == 0 ? 0 : Math.round(100f * pr.survived / pr.raids);
        l.add("<dark_gray>Survie : <black>" + taux + " %");
        l.add("<dark_gray>Joueurs tués : <black>" + pr.pvpKills);
        l.add("<dark_gray>Infestés tués : <black>" + pr.infectedKills);
        return l;
    }
}
