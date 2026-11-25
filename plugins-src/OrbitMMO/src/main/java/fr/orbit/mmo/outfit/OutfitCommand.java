package fr.orbit.mmo.outfit;

import fr.orbit.mmo.item.ArmorType;
import fr.orbit.mmo.item.Loot;
import fr.orbit.mmo.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /mmoadmin tenue … (ajouté à /mmoadmin par OutfitService) :
 * {@code <id> [joueur]} pose l'apparence sur les pièces portées (une case vide reçoit la pièce de l'armurerie),
 * {@code retirer [joueur]}, {@code soi} (montrer/cacher ses propres pièces), {@code info [joueur]}, {@code liste},
 * {@code pieces <aucune|cape|toutes>} (épaulières et cape 3D posées sur les joueurs ; aucune par défaut).
 */
final class OutfitCommand {

    /** Palier « acier » de Loot (recrue, cuivre, acier…). */
    private static final int TIER_ACIER = 2;
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    /** Apparence d'origine de la pièce (la même clé que Keys.OUTFIT_BASE). */
    private static final org.bukkit.NamespacedKey BASE = new org.bukkit.NamespacedKey("orbitmmo", "outfit_base");

    private final OutfitService service;

    OutfitCommand(OutfitService service) {
        this.service = service;
    }

    boolean run(CommandSender s, String[] a) {
        if (a.length == 0) {
            help(s);
            return true;
        }
        String sub = a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "liste" -> {
                Text.msg(s, "<#FFD66E>" + OutfitDef.all().size() + " tenues <#8C96A3>: <type>_<palier>, type tissu | cuir | mailles | plaques, palier 0 à 7."
                        + " Pièces 3D posées sur le joueur : " + service.pieces().label + ".");
                for (OutfitDef d : OutfitDef.all()) {
                    Text.msg(s, "<#FFD66E>" + d.id() + " <#8C96A3>· " + Text.esc(d.label()) + " <#5A6270>(" + service.parts(d).size() + " pièce(s) 3D posée(s))");
                }
            }
            case "pieces" -> {
                if (a.length < 2) {
                    Text.msg(s, "<#8C96A3>Pièces 3D posées sur le joueur : <#FFD66E>" + service.pieces().label
                            + "<#8C96A3>. Changer : /mmoadmin tenue pieces <aucune|cape|toutes>");
                    return true;
                }
                OutfitService.Pieces next = OutfitService.Pieces.of(a[1]);
                if (next == null) {
                    Text.msg(s, "<#FF8A5C>Choix inconnu : " + Text.esc(a[1]) + ". <#8C96A3>aucune, cape ou toutes");
                    return true;
                }
                service.setPieces(next);
                Text.msg(s, "Pièces 3D posées sur le joueur : <#FFD66E>" + next.label + "<white>. "
                        + "<#8C96A3>Tout de suite pour tout le monde, et gardé au redémarrage (config.yml). Le corps et le casque ne changent pas.");
            }
            case "soi" -> {
                if (!(s instanceof Player p)) {
                    Text.msg(s, "<#FF8A5C>Seulement en jeu.");
                    return true;
                }
                if (service.pieces() == OutfitService.Pieces.AUCUNE) {
                    Text.msg(s, "<#8C96A3>Aucune pièce 3D n'est posée sur les joueurs en ce moment : rien à cacher.");
                    return true;
                }
                boolean hidden = service.toggleSelf(p);
                Text.msg(s, hidden ? "Tes épaulières et ta cape sont maintenant <white>cachées pour toi</white> (les autres les voient)."
                        : "Tu vois de nouveau <white>tes épaulières et ta cape</white>.");
            }
            case "retirer" -> {
                Player t = target(s, a, 1);
                if (t == null) {
                    return true;
                }
                int n = 0;
                PlayerInventory inv = t.getInventory();
                for (EquipmentSlot slot : ARMOR) {
                    ItemStack it = inv.getItem(slot);
                    if (Outfits.of(it) == null) {
                        continue;
                    }
                    String base = it.getPersistentDataContainer().get(BASE, PersistentDataType.STRING);
                    if (base != null && !base.equals(Outfits.id(it))) {
                        // Apparence posée par-dessus (comme à la garde-robe) : la pièce reprend la sienne.
                        Outfits.apply(it, base);
                        inv.setItem(slot, it);
                    } else {
                        inv.setItem(slot, null);
                        inv.addItem(it).values().forEach(left -> t.getWorld().dropItemNaturally(t.getLocation(), left));
                    }
                    n++;
                }
                service.refresh(t);
                Text.msg(s, n + " pièce(s) de " + Text.esc(t.getName()) + " sans tenue 3D (apparence d'origine rendue, ou pièce rangée dans son sac).");
            }
            case "info" -> {
                Player t = target(s, a, 1);
                if (t == null) {
                    return true;
                }
                info(s, t);
            }
            default -> {
                OutfitDef def = OutfitDef.get(sub);
                if (def == null) {
                    Text.msg(s, "<#FF8A5C>Tenue inconnue : " + Text.esc(sub) + ". <#8C96A3>/mmoadmin tenue liste");
                    return true;
                }
                Player t = target(s, a, 1);
                if (t == null) {
                    return true;
                }
                give(t, def);
                service.refresh(t);
                Text.msg(s, Text.esc(t.getName()) + " porte <#FFD66E>" + Text.esc(def.label()) + "<white>. "
                        + "<#8C96A3>Apparence posée sur ses pièces portées (stats inchangées). Vue F5 pour la voir.");
            }
        }
        return true;
    }

    /**
     * Pose l'apparence sur les pièces d'armure portées, comme le ferait la garde-robe : les stats et le type de la
     * pièce ne changent pas, donc la règle « une classe, un type d'armure » ne la renvoie pas dans le sac. Une case
     * vide reçoit la pièce de l'armurerie qui a cette apparence (plaques, Commune) ; si la classe ne la porte pas,
     * la règle de classe la range dans le sac.
     */
    private void give(Player t, OutfitDef def) {
        int cut = def.id().lastIndexOf('_');
        int tier = TIER_ACIER;
        try {
            tier = Integer.parseInt(def.id().substring(cut + 1));
        } catch (RuntimeException ignored) {
            // nom sans palier : acier
        }
        ItemStack[] set = null;
        PlayerInventory inv = t.getInventory();
        for (int i = 0; i < ARMOR.length; i++) {
            ItemStack worn = inv.getItem(ARMOR[i]);
            if (worn != null && !worn.isEmpty() && Outfits.slotOf(worn) == ARMOR[i]) {
                Outfits.apply(worn, def.id());
                inv.setItem(ARMOR[i], worn);
                continue;
            }
            if (set == null) {
                ArmorType type = ArmorType.byId(cut < 0 ? def.id() : def.id().substring(0, cut));
                set = Loot.armorSet(type == null ? ArmorType.PLAQUES : type, tier, null);
            }
            Outfits.apply(set[i], def.id());
            inv.setItem(ARMOR[i], set[i]);
            if (worn != null && !worn.isEmpty()) {
                inv.addItem(worn).values().forEach(left -> t.getWorld().dropItemNaturally(t.getLocation(), left));
            }
        }
    }

    private void info(CommandSender s, Player t) {
        Wearer w = service.wearer(t);
        String chest = Outfits.id(t.getInventory().getChestplate());
        Text.msg(s, "<#FFD66E>Tenue de " + Text.esc(t.getName()) + " <#8C96A3>: plastron " + (chest == null ? "sans tenue" : chest)
                + ", pièces 3D " + (w == null ? "aucune" : w.displays.size() + " (" + (w.valid() ? "valides" : "à refaire") + ")")
                + " · réglage du serveur : " + service.pieces().label);
        if (w == null) {
            return; // rien n'est posé sur lui : pas de suivi à décrire
        }
        Text.msg(s, "<#8C96A3>Suivi : " + (service.packets() ? "client (paquets passagers, " + service.packetsSent() + " envoyés)" : "téléportation (" + Text.esc(String.valueOf(PassengerPackets.error())) + ")")
                + " · pose " + t.getPose() + " · hauteur " + String.format(Locale.ROOT, "%.2f", t.getHeight()));
        Text.msg(s, "<#8C96A3>Corps " + String.format(Locale.ROOT, "%.1f", t.getBodyYaw()) + "° · tête " + String.format(Locale.ROOT, "%.1f", t.getLocation().getYaw())
                + "° · cape " + (w == null ? "-" : String.format(Locale.ROOT, "%.2f", w.cape())) + " rad · vus par " + t.getTrackedBy().size() + " joueur(s)"
                + (service.hidesSelf(t) ? " · cachées pour lui" : ""));
        if (w != null && !w.displays.isEmpty()) {
            var d = w.displays.get(0);
            Text.msg(s, "<#8C96A3>Pièce 1 à " + String.format(Locale.ROOT, "%.2f", d.getLocation().distance(w.anchor())) + " bloc(s) du point d'attache ; vue par "
                    + d.getTrackedBy().size() + " joueur(s) ; orientée " + String.format(Locale.ROOT, "%.0f/%.0f", d.getLocation().getYaw(), d.getLocation().getPitch())
                    + " ; torse calculé " + String.format(Locale.ROOT, "%.1f", w.bodyAngle()) + "°.");
        }
        if (w != null && !w.own.isEmpty()) {
            Text.msg(s, "<#8C96A3>Pièces du porteur : " + w.own.size() + ", vues par " + w.own.get(0).getTrackedBy().size() + " joueur(s) ; rehausse "
                    + (w.seat == null ? "absente (suivi par téléportation)" : "vue par " + w.seat.getTrackedBy().size() + " joueur(s)") + ".");
        }
    }

    private Player target(CommandSender s, String[] a, int i) {
        if (a.length > i) {
            Player p = Bukkit.getPlayerExact(a[i]);
            if (p == null) {
                Text.msg(s, "<#FF8A5C>Joueur introuvable : " + Text.esc(a[i]));
            }
            return p;
        }
        if (s instanceof Player p) {
            return p;
        }
        Text.msg(s, "<#FF8A5C>Précise un joueur.");
        return null;
    }

    private void help(CommandSender s) {
        Text.msg(s, "<#FFD66E>/mmoadmin tenue <id> [joueur] <#8C96A3>met la tenue (ex. plaques_2)");
        Text.msg(s, "<#FFD66E>/mmoadmin tenue retirer [joueur] <#8C96A3>· <#FFD66E>soi <#8C96A3>(montrer/cacher ses pièces) · <#FFD66E>info [joueur] <#8C96A3>· <#FFD66E>liste");
        Text.msg(s, "<#FFD66E>/mmoadmin tenue pieces <aucune|cape|toutes> <#8C96A3>épaulières et cape 3D posées sur les joueurs (maintenant : "
                + service.pieces().label + ")");
    }

    List<String> complete(String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            out.addAll(List.of("retirer", "soi", "info", "liste", "pieces"));
            for (OutfitDef d : OutfitDef.all()) {
                out.add(d.id());
            }
        } else if (a.length == 2 && a[0].equalsIgnoreCase("pieces")) {
            out.addAll(List.of("aucune", "cape", "toutes"));
        } else if (a.length == 2 && !a[0].equalsIgnoreCase("soi") && !a[0].equalsIgnoreCase("liste")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                out.add(p.getName());
            }
        }
        String last = a[a.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
