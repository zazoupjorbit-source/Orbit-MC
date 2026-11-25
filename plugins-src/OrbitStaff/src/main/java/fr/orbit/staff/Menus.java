package fr.orbit.staff;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Tous les menus du staff : liste des joueurs, panel, sanctions, catalogue. */
public final class Menus {

    public enum Usage { TP, PANEL, LIVRE, FREEZE, INVENTAIRE }

    private Menus() {
    }

    // ------------------------------------------------------------------ liste des joueurs

    public static void joueurs(OrbitStaff plugin, Player staff, Usage usage, int page) {
        List<Player> liste = new ArrayList<>(Bukkit.getOnlinePlayers());
        liste.removeIf(o -> o.equals(staff) || o.hasMetadata("NPC"));
        liste.sort(Comparator.comparing((Player o) -> plugin.staffMode().actif(o)).thenComparing(o -> o.getName().toLowerCase(Locale.ROOT)));
        int pages = Math.max(1, (liste.size() + 44) / 45);
        int pg = Math.max(0, Math.min(page, pages - 1));
        String titre = switch (usage) {
            case TP -> "Joueurs : TP";
            case PANEL -> "Joueurs : panel";
            case LIVRE -> "Joueurs : livre de stats";
            case FREEZE -> "Joueurs : freeze";
            case INVENTAIRE -> "Joueurs : inventaire";
        };
        Menu m = new Menu(6, "<#4aa3ff>" + titre + " <dark_gray>(" + liste.size() + ")");
        for (int i = 0; i < 45 && pg * 45 + i < liste.size(); i++) {
            Player o = liste.get(pg * 45 + i);
            List<String> lore = new ArrayList<>(infosCourtes(plugin, o));
            lore.add("");
            lore.add(switch (usage) {
                case TP -> "<#f5c542>Clic gauche <gray>: TP  <#f5c542>Clic droit <gray>: panel";
                case LIVRE -> "<#f5c542>Clic <gray>: livre de stats";
                case FREEZE -> "<#f5c542>Clic <gray>: geler / dégeler";
                case INVENTAIRE -> "<#f5c542>Clic gauche <gray>: inventaire  <#f5c542>Clic droit <gray>: ender chest";
                case PANEL -> "<#f5c542>Clic <gray>: panel";
            });
            String nom = (plugin.staffMode().actif(o) ? "<#f5c542>[STAFF] " : plugin.freeze().gele(o.getUniqueId()) ? "<#7fd4ff>[GELÉ] " : "<white>")
                    + Txt.esc(o.getName());
            m.set(i, Txt.head(o, nom, lore), (p, clic) -> {
                if (!o.isOnline()) {
                    Txt.err(p, "Il n'est plus en ligne.");
                    joueurs(plugin, p, usage, pg);
                    return;
                }
                switch (usage) {
                    case TP -> {
                        if (clic.isRightClick()) {
                            panel(plugin, p, o);
                        } else {
                            p.closeInventory();
                            p.teleportAsync(o.getLocation());
                        }
                    }
                    case PANEL -> panel(plugin, p, o);
                    case LIVRE -> {
                        p.closeInventory();
                        Livre.ouvrir(plugin, p, o.getUniqueId());
                    }
                    case FREEZE -> {
                        plugin.freeze().basculer(p, o, "");
                        joueurs(plugin, p, usage, pg);
                    }
                    case INVENTAIRE -> {
                        if (clic.isRightClick()) {
                            Inspection.ender(plugin, p, o);
                        } else {
                            Inspection.inventaire(plugin, p, o);
                        }
                    }
                }
            });
        }
        m.bas();
        if (pg > 0) {
            m.set(45, Txt.icon(Material.ARROW, "<white>Page précédente"), (p, c) -> joueurs(plugin, p, usage, pg - 1));
        }
        m.set(49, Txt.icon(Material.BARRIER, "<#ff5555>Fermer"), (p, c) -> p.closeInventory());
        m.set(47, Txt.icon(Material.BOOK, "<#f5c542>Page " + (pg + 1) + "/" + pages));
        if (pg < pages - 1) {
            m.set(53, Txt.icon(Material.ARROW, "<white>Page suivante"), (p, c) -> joueurs(plugin, p, usage, pg + 1));
        }
        m.ouvrir(staff);
    }

    static List<String> infosCourtes(OrbitStaff plugin, Player o) {
        var max = o.getAttribute(Attribute.MAX_HEALTH);
        Combat.Mesure me = plugin.combat().mesure(o.getUniqueId());
        return List.of(
                "Vie : <white>" + Math.round(o.getHealth()) + "/" + Math.round(max == null ? 20 : max.getValue())
                        + " <gray>Faim : <white>" + o.getFoodLevel(),
                "Ping : <white>" + o.getPing() + " ms <gray>Mode : <white>" + o.getGameMode().name().toLowerCase(Locale.ROOT),
                "Monde : <white>" + o.getWorld().getName() + " <gray>(" + o.getLocation().getBlockX() + ", "
                        + o.getLocation().getBlockY() + ", " + o.getLocation().getBlockZ() + ")",
                "CPS : <white>" + (me == null ? 0 : me.cps) + " <gray>(max " + (me == null ? 0 : me.cpsMax) + ")"
                        + " <gray>Session : <white>" + Txt.duree(Duration.ofSeconds(plugin.joueurs().sessionSecondes(o.getUniqueId()))));
    }

    // ------------------------------------------------------------------ panel

    public static void panel(OrbitStaff plugin, Player staff, Player cible) {
        if (!staff.hasPermission("orbitstaff.panel")) {
            Txt.err(staff, "Permission manquante.");
            return;
        }
        boolean gele = plugin.freeze().gele(cible.getUniqueId());
        Menu m = new Menu(6, "<#f5c542>Panel <dark_gray>» <white>" + Txt.esc(cible.getName()));
        List<String> tete = new ArrayList<>(infosCourtes(plugin, cible));
        if (plugin.sanctions().mute(cible.getUniqueId()) != null) {
            tete.add("<#ff5555>Muet en ce moment");
        }
        m.set(4, Txt.head(cible, "<white><bold>" + Txt.esc(cible.getName()), tete), (p, c) -> panel(plugin, p, cible));

        m.set(19, Txt.icon(Material.ENDER_PEARL, "<#4aa3ff>Se TP sur lui"), (p, c) -> {
            p.closeInventory();
            p.teleportAsync(cible.getLocation());
        });
        m.set(20, Txt.icon(Material.LEAD, "<#4aa3ff>L'amener à moi", p0(staff, "orbitstaff.tp.ramener")), (p, c) -> {
            if (exige(p, "orbitstaff.tp.ramener")) {
                p.closeInventory();
                cible.teleportAsync(p.getLocation());
                plugin.log(p.getName(), "TPHERE", cible.getName(), null);
            }
        });
        m.set(21, Txt.icon(gele ? Material.BLUE_ICE : Material.PACKED_ICE, gele ? "<#7fd4ff>Dégeler" : "<#7fd4ff>Geler",
                gele ? "Gelé par " + Txt.esc(plugin.freeze().gel(cible.getUniqueId()).staff()) : "Bloque le joueur pour une vérification"), (p, c) -> {
            if (exige(p, "orbitstaff.freeze")) {
                plugin.freeze().basculer(p, cible, "");
                panel(plugin, p, cible);
            }
        });
        m.set(22, Txt.icon(Material.CHEST, "<#c58a4a>Inventaire", "En direct"), (p, c) -> Inspection.inventaire(plugin, p, cible));
        m.set(23, Txt.icon(Material.ENDER_CHEST, "<#c58a4a>Coffre de l'End"), (p, c) -> Inspection.ender(plugin, p, cible));
        m.set(24, Txt.icon(Material.WRITTEN_BOOK, "<#f5c542>Livre de stats", "Infos, combat, modération, mode"), (p, c) -> {
            p.closeInventory();
            Livre.ouvrir(plugin, p, cible.getUniqueId());
        });
        m.set(25, Txt.icon(Material.IRON_AXE, "<#ff5555>Sanctionner", "Sanctions prédéfinies"),
                (p, c) -> sanctions(plugin, p, cible.getUniqueId(), cible.getName()));

        m.set(28, Txt.icon(Material.GOLDEN_APPLE, "<#55ff88>Soigner", p0(staff, "orbitstaff.soins")), (p, c) -> {
            if (exige(p, "orbitstaff.soins")) {
                var max = cible.getAttribute(Attribute.MAX_HEALTH);
                cible.setHealth(max == null ? 20 : max.getValue());
                cible.setFireTicks(0);
                plugin.log(p.getName(), "HEAL", cible.getName(), null);
                panel(plugin, p, cible);
            }
        });
        m.set(29, Txt.icon(Material.COOKED_BEEF, "<#55ff88>Nourrir", p0(staff, "orbitstaff.soins")), (p, c) -> {
            if (exige(p, "orbitstaff.soins")) {
                cible.setFoodLevel(20);
                cible.setSaturation(20);
                plugin.log(p.getName(), "FEED", cible.getName(), null);
                panel(plugin, p, cible);
            }
        });
        m.set(30, Txt.icon(Material.MILK_BUCKET, "<#55ff88>Retirer ses effets", effets(cible)), (p, c) -> {
            if (exige(p, "orbitstaff.soins")) {
                for (PotionEffect e : cible.getActivePotionEffects()) {
                    if (!(plugin.freeze().gele(cible.getUniqueId()) && e.getType().equals(org.bukkit.potion.PotionEffectType.BLINDNESS))) {
                        cible.removePotionEffect(e.getType());
                    }
                }
                plugin.log(p.getName(), "CLEAR_EFFETS", cible.getName(), null);
                panel(plugin, p, cible);
            }
        });
        m.set(31, Txt.icon(Material.NETHER_STAR, "<#d77bff>Lui donner un objet", "Catalogue du mode", p0(staff, "orbitstaff.catalogue")), (p, c) -> {
            if (exige(p, "orbitstaff.catalogue")) {
                catalogue(plugin, p, cible);
            }
        });
        m.set(32, Txt.icon(Material.WRITABLE_BOOK, "<#f5c542>Ajouter une note", "Visible du staff seulement"), (p, c) -> {
            if (exige(p, "orbitstaff.notes")) {
                plugin.saisie().demander(p, "Note sur <white>" + Txt.esc(cible.getName()) + "<gray> :",
                        t -> Commandes.ajouterNote(plugin, p, cible.getUniqueId(), cible.getName(), t));
            }
        });
        m.set(33, Txt.icon(Material.OAK_DOOR, "<#ff8c42>Expulser", "Demande le motif dans le chat"), (p, c) -> {
            if (exige(p, "orbitstaff.kick")) {
                plugin.saisie().demander(p, "Motif de l'expulsion de <white>" + Txt.esc(cible.getName()) + "<gray> :",
                        t -> plugin.sanctions().appliquer(p, cible.getUniqueId(), cible.getName(), Sanctions.Type.KICK, null, t));
            }
        });
        m.set(34, Txt.icon(Material.SPYGLASS, "<#4aa3ff>Observer", "Passe en spectateur sur lui", "/staff pour revenir en mode staff"), (p, c) -> {
            p.closeInventory();
            if (!plugin.staffMode().actif(p)) {
                Txt.err(p, "Passe d'abord en mode staff (/staff) : ton mode de jeu est rendu à la sortie.");
                return;
            }
            p.setGameMode(org.bukkit.GameMode.SPECTATOR);
            p.teleportAsync(cible.getLocation()).thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (p.isOnline() && cible.isOnline()) {
                    p.setSpectatorTarget(cible);
                    Txt.send(p, "Tu observes <white>" + Txt.esc(cible.getName()) + "<gray>. Accroupis-toi pour sortir, puis <white>/staff <gray>pour revenir en mode staff.");
                }
            }));
        });
        m.bas();
        m.set(49, Txt.icon(Material.BARRIER, "<#ff5555>Fermer"), (p, c) -> p.closeInventory());
        m.set(45, Txt.icon(Material.ARROW, "<white>Liste des joueurs"), (p, c) -> joueurs(plugin, p, Usage.TP, 0));
        m.ouvrir(staff);
    }

    private static String p0(Player staff, String perm) {
        return staff.hasPermission(perm) ? "" : "<#ff5555>Il te faut " + perm;
    }

    private static boolean exige(Player p, String perm) {
        if (p.hasPermission(perm)) {
            return true;
        }
        Txt.err(p, "Il te faut " + perm + ".");
        return false;
    }

    private static List<String> effets(Player p) {
        List<String> out = new ArrayList<>();
        for (PotionEffect e : p.getActivePotionEffects()) {
            out.add("<white>" + e.getType().getKey().getKey() + " " + (e.getAmplifier() + 1)
                    + (e.isInfinite() ? "" : " <gray>(" + e.getDuration() / 20 + "s)"));
        }
        if (out.isEmpty()) {
            out.add("Aucun effet");
        }
        return out;
    }

    // ------------------------------------------------------------------ sanctions

    public static void sanctions(OrbitStaff plugin, Player staff, UUID cible, String nom) {
        Map<String, Sanctions.Preset> presets = plugin.sanctions().presets();
        Menu m = new Menu(Math.max(2, Math.min(6, (presets.size() + 8) / 9 + 1)), "<#ff5555>Sanctionner <dark_gray>» <white>" + Txt.esc(nom));
        int i = 0;
        for (Sanctions.Preset s : presets.values()) {
            boolean ok = staff.hasPermission(s.type().permission());
            String duree = s.duree() == null ? (s.type().estBan() || s.type() == Sanctions.Type.MUTE ? "définitif" : "—") : Txt.duree(s.duree());
            m.set(i++, Txt.icon(ok ? s.icone() : Material.GRAY_DYE, (ok ? "<#f5c542>" : "<dark_gray>") + Txt.esc(s.nom()),
                    "Type : <white>" + s.type().libelle(), "Durée : <white>" + duree, "Motif : <white>" + Txt.esc(s.motif()), "",
                    ok ? "<#f5c542>Clic <gray>: appliquer" : "<#ff5555>Il te faut " + s.type().permission()), (p, c) -> {
                if (!exige(p, s.type().permission())) {
                    return;
                }
                if (s.type().estBan()) {
                    confirmer(p, "Confirmer : " + s.nom() + " pour " + nom, () -> {
                        p.closeInventory();
                        plugin.sanctions().appliquer(p, cible, nom, s.type(), s.duree(), s.motif());
                    });
                } else {
                    p.closeInventory();
                    plugin.sanctions().appliquer(p, cible, nom, s.type(), s.duree(), s.motif());
                }
            });
            if (i >= 45) {
                break;
            }
        }
        int last = m.getInventory().getSize() - 1;
        m.set(last, Txt.icon(Material.NAME_TAG, "<white>Sanction libre", "Tape la commande toi-même :",
                "<white>/mute|tempban " + Txt.esc(nom) + " <durée> <motif>"), (p, c) -> {
            p.closeInventory();
            p.sendMessage(Txt.mm(Txt.PREFIXE + "<click:suggest_command:'/tempban " + nom + " 1j '><#f5c542>[/tempban]</click> "
                    + "<click:suggest_command:'/mute " + nom + " 1h '><#f5c542>[/mute]</click> "
                    + "<click:suggest_command:'/warn " + nom + " '><#f5c542>[/warn]</click>"));
        });
        m.ouvrir(staff);
    }

    public static void confirmer(Player p, String question, Runnable oui) {
        Menu m = new Menu(3, "<#ff5555>" + Txt.esc(question));
        for (int i : new int[]{10, 11, 12}) {
            m.set(i, Txt.icon(Material.LIME_CONCRETE, "<#55ff88><bold>Confirmer"), (pp, c) -> oui.run());
        }
        for (int i : new int[]{14, 15, 16}) {
            m.set(i, Txt.icon(Material.RED_CONCRETE, "<#ff5555><bold>Annuler"), (pp, c) -> pp.closeInventory());
        }
        m.ouvrir(p);
    }

    // ------------------------------------------------------------------ catalogue

    private static List<Material> vanilla;

    private static List<Material> vanilla() {
        if (vanilla == null) {
            List<Material> l = new ArrayList<>();
            for (Material mat : Material.values()) {
                if (!mat.isLegacy() && mat.isItem() && !mat.isAir()) {
                    l.add(mat);
                }
            }
            l.sort(Comparator.comparing(Material::name));
            vanilla = l;
        }
        return vanilla;
    }

    /** Écran d'accueil : une icône par catégorie du mode, puis Minecraft et la recherche. */
    public static void catalogue(OrbitStaff plugin, Player staff, Player receveur) {
        Map<String, List<ItemStack>> cats = plugin.ponts().catalogue();
        String pour = receveur.equals(staff) ? "" : " <dark_gray>→ <white>" + Txt.esc(receveur.getName());
        Menu m = new Menu(6, "<#d77bff>Catalogue <dark_gray>(" + plugin.serveur() + ")" + pour);
        int i = 0;
        for (Map.Entry<String, List<ItemStack>> e : cats.entrySet()) {
            if (i >= 36) {
                break;
            }
            ItemStack ic = e.getValue().get(0).clone();
            ic.setAmount(1);
            var meta = ic.getItemMeta();
            meta.displayName(Txt.item("<#f5c542><bold>" + Txt.esc(e.getKey())));
            meta.lore(List.of(Txt.item("<gray>" + e.getValue().size() + " objets"), Txt.item("<#f5c542>Clic <gray>: ouvrir")));
            ic.setItemMeta(meta);
            List<ItemStack> items = e.getValue();
            String titre = e.getKey();
            m.set(i++, ic, (p, c) -> liste(plugin, p, receveur, titre, items, 0));
        }
        if (cats.isEmpty()) {
            m.set(13, Txt.icon(Material.PAPER, "<gray>Aucun objet de mode sur ce serveur",
                    "Le plugin du mode doit exposer orbitCatalogue()."));
        }
        m.bas();
        m.set(48, Txt.icon(Material.GRASS_BLOCK, "<#55ff88><bold>Minecraft", "Tous les objets (" + vanilla().size() + ")"),
                (p, c) -> liste(plugin, p, receveur, "Minecraft", vanilla().stream().map(mat -> new ItemStack(mat)).toList(), 0));
        m.set(50, Txt.icon(Material.SPYGLASS, "<#4aa3ff><bold>Rechercher", "Par nom (anglais ou nom affiché)"), (p, c) ->
                plugin.saisie().demander(p, "Objet à chercher :", t -> rechercher(plugin, p, receveur, t)));
        m.set(49, Txt.icon(Material.BARRIER, "<#ff5555>Fermer"), (p, c) -> p.closeInventory());
        m.ouvrir(staff);
    }

    private static void rechercher(OrbitStaff plugin, Player staff, Player receveur, String texte) {
        String q = texte.toLowerCase(Locale.ROOT).replace(' ', '_');
        List<ItemStack> res = new ArrayList<>();
        for (List<ItemStack> l : plugin.ponts().catalogue().values()) {
            for (ItemStack it : l) {
                String nom = it.hasItemMeta() && it.getItemMeta().hasDisplayName()
                        ? net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                        .serialize(it.getItemMeta().displayName()).toLowerCase(Locale.ROOT) : "";
                if (nom.replace(' ', '_').contains(q) || it.getType().name().toLowerCase(Locale.ROOT).contains(q)) {
                    res.add(it);
                }
            }
        }
        for (Material mat : vanilla()) {
            if (mat.name().toLowerCase(Locale.ROOT).contains(q)) {
                res.add(new ItemStack(mat));
            }
        }
        if (res.isEmpty()) {
            Txt.err(staff, "Rien trouvé pour « " + texte + " ».");
            return;
        }
        liste(plugin, staff, receveur, "« " + texte + " »", res, 0);
    }

    private static void liste(OrbitStaff plugin, Player staff, Player receveur, String titre, List<ItemStack> items, int page) {
        int pages = Math.max(1, (items.size() + 44) / 45);
        int pg = Math.max(0, Math.min(page, pages - 1));
        Menu m = new Menu(6, "<#d77bff>" + Txt.esc(titre) + " <dark_gray>" + (pg + 1) + "/" + pages);
        for (int i = 0; i < 45 && pg * 45 + i < items.size(); i++) {
            ItemStack original = items.get(pg * 45 + i);
            ItemStack vue = original.clone();
            var meta = vue.getItemMeta();
            if (meta != null) {
                List<net.kyori.adventure.text.Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
                lore.add(Txt.item(""));
                lore.add(Txt.item("<#f5c542>Clic gauche <gray>: x1  <#f5c542>Clic droit <gray>: pile  <#f5c542>Maj <gray>: x" + original.getAmount()));
                meta.lore(lore);
                vue.setItemMeta(meta);
            }
            m.set(i, vue, (p, clic) -> {
                if (!p.hasPermission("orbitstaff.catalogue")) {
                    return;
                }
                if (!receveur.isOnline()) {
                    Txt.err(p, "Le joueur n'est plus en ligne.");
                    return;
                }
                ItemStack don = original.clone();
                if (clic == ClickType.SHIFT_LEFT || clic == ClickType.SHIFT_RIGHT) {
                    // quantité d'origine (paquets du mode)
                } else if (clic.isRightClick()) {
                    don.setAmount(don.getMaxStackSize());
                } else {
                    don.setAmount(1);
                }
                for (ItemStack reste : receveur.getInventory().addItem(don).values()) {
                    receveur.getWorld().dropItemNaturally(receveur.getLocation(), reste);
                }
                String n = don.getType().name().toLowerCase(Locale.ROOT);
                if (don.hasItemMeta() && don.getItemMeta().hasDisplayName()) {
                    n = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(don.getItemMeta().displayName());
                }
                p.sendActionBar(Txt.mm("<#55ff88>+" + don.getAmount() + " <white>" + Txt.esc(n)
                        + (receveur.equals(p) ? "" : " <gray>→ " + Txt.esc(receveur.getName()))));
                plugin.log(p.getName(), "GIVE", receveur.getName(), n + " x" + don.getAmount());
            });
        }
        m.bas();
        if (pg > 0) {
            m.set(45, Txt.icon(Material.ARROW, "<white>Page précédente"), (p, c) -> liste(plugin, p, receveur, titre, items, pg - 1));
        }
        m.set(48, Txt.icon(Material.NETHER_STAR, "<#d77bff>Retour au catalogue"), (p, c) -> catalogue(plugin, p, receveur));
        m.set(49, Txt.icon(Material.BARRIER, "<#ff5555>Fermer"), (p, c) -> p.closeInventory());
        m.set(50, Txt.icon(Material.SPYGLASS, "<#4aa3ff>Rechercher"), (p, c) ->
                plugin.saisie().demander(p, "Objet à chercher :", t -> rechercher(plugin, p, receveur, t)));
        if (pg < pages - 1) {
            m.set(53, Txt.icon(Material.ARROW, "<white>Page suivante"), (p, c) -> liste(plugin, p, receveur, titre, items, pg + 1));
        }
        m.ouvrir(staff);
    }

    static List<String> lignes(String... l) {
        return Arrays.asList(l);
    }
}
