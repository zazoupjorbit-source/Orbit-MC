package fr.orbit.mmo.ui;

import fr.orbit.mmo.OrbitMMO;
import fr.orbit.mmo.continent.ContinentService.Town;
import fr.orbit.mmo.inn.InnService;
import fr.orbit.mmo.item.Items;
import fr.orbit.mmo.player.PlayerData;
import fr.orbit.mmo.util.Text;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** Menu de l'aubergiste : lier son cristal de téléportation à l'auberge, en récupérer un s'il manque. */
public final class InnMenus {

    private InnMenus() {
    }

    public static void open(OrbitMMO plugin, Player p, Town t) {
        PlayerData d = plugin.players().get(p);
        if (!plugin.progression().valid(d)) {
            return;
        }
        InnService inns = plugin.inns();
        Menu m = new Menu(3, Text.mm("<#E8C9A0><b>Auberge</b></#E8C9A0> <#5A6270>· <#D7DCE2>" + Text.esc(t.name())))
                .skin("Auberge", t.name(), Menus.gold(d));

        List<String> lore = new ArrayList<>(inns.status(d));
        lore.add("<#5A6270>Lié à vous : il ne se jette pas,");
        lore.add("<#5A6270>ne se vend pas et reste à la mort.");
        m.set(11, Items.icon(Material.PRISMARINE_CRYSTALS, "<#7FD4FF><b>Votre cristal</b>", lore), null);

        if (!inns.welcomes(t, d)) {
            m.set(13, Items.icon(Material.BARRIER, "<#FF5C5C><b>Auberge de l'autre camp</b>",
                    "<#8C96A3>On ne sert pas votre faction ici."), null);
        } else if (t.id().equals(d.inn)) {
            m.set(13, Items.icon(Material.LIME_BED, "<#5EE89A><b>Votre cristal est lié ici</b>",
                    "<#8C96A3>Il vous ramène à cette auberge."), null);
        } else {
            m.set(13, Items.icon(Material.RED_BED, "<#FFD66E><b>Lier mon cristal ici</b>",
                    "<#8C96A3>Le cristal vous ramènera à",
                    "<#8C96A3>l'auberge de <white>" + Text.esc(t.name()) + "</white>.",
                    "<#5A6270>Gratuit · [Clic] lier"), click -> {
                if (inns.bind(p, d, t)) {
                    open(plugin, p, t);
                }
            });
        }

        if (inns.hasCrystal(p)) {
            m.set(15, Items.icon(Material.ENDER_EYE, "<#8C96A3><b>Cristal en poche</b>",
                    "<#5A6270>Vous avez déjà votre cristal."), null);
        } else {
            m.set(15, Items.icon(Material.ENDER_EYE, "<#7FD4FF><b>Recevoir mon cristal</b>",
                    "<#8C96A3>Vous n'avez plus votre cristal ?", "<#5A6270>Gratuit · [Clic] le reprendre"), click -> {
                if (!inns.sync(p, d)) {
                    Text.msg(p, "<#FF5C5C>Faites de la place dans votre sac.");
                    return;
                }
                p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.4f);
                open(plugin, p, t);
            });
        }
        Menus.frame(m, 3);
        m.open(p);
    }
}
