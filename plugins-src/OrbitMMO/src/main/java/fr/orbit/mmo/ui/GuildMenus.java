package fr.orbit.mmo.ui;

import fr.orbit.mmo.OrbitMMO;
import fr.orbit.mmo.classes.Faction;
import fr.orbit.mmo.classes.SpecDef;
import fr.orbit.mmo.guild.Guild;
import fr.orbit.mmo.guild.Guild.Member;
import fr.orbit.mmo.guild.Guild.Rank;
import fr.orbit.mmo.guild.GuildService;
import fr.orbit.mmo.item.Items;
import fr.orbit.mmo.player.PlayerData;
import fr.orbit.mmo.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Menus de guilde : sans guilde (fonder, invitation), la guilde et ses membres, la banque, le classement. */
public final class GuildMenus {

    private static final String HEX = GuildService.HEX;

    private GuildMenus() {
    }

    private static Material banner(Faction f) {
        return f == Faction.OMBRE ? Material.PURPLE_BANNER : Material.YELLOW_BANNER;
    }

    private static String day(long at) {
        return at <= 0 ? "—" : new SimpleDateFormat("dd/MM/yyyy").format(new Date(at));
    }

    // ------------------------------------------------------------------ Entrée

    public static void open(OrbitMMO plugin, Player p) {
        PlayerData d = plugin.players().get(p);
        if (!plugin.progression().valid(d)) {
            Menus.factionSelect(plugin, p);
            return;
        }
        Guild g = plugin.guilds().of(p.getUniqueId());
        if (g == null) {
            none(plugin, p, d);
        } else {
            guild(plugin, p, d, g);
        }
    }

    /** Sans guilde : en fonder une, répondre à une invitation, voir le classement. */
    private static void none(OrbitMMO plugin, Player p, PlayerData d) {
        GuildService guilds = plugin.guilds();
        Menu m = new Menu(3, Text.mm("<" + HEX + "><b>Guilde</b></" + HEX + "> <#8C96A3>· sans guilde"))
                .skin("Guilde", "Sans guilde", Menus.gold(d));
        long cost = guilds.createCost();
        m.set(11, Items.icon(banner(d.faction), "<" + HEX + "><b>Fonder une guilde</b>",
                "<#8C96A3>Coût : <#FFD66E>" + Text.num(cost) + " or" + (d.gold < cost ? " <#FF5C5C>(il vous en manque " + Text.num(cost - d.gold) + ")" : ""),
                "<#8C96A3>Faction : " + d.faction.tag() + " <#5A6270>(une seule par guilde)",
                "<#8C96A3>Jusqu'à <white>" + guilds.maxMembers() + "</white> membres : chef, officiers, membres.",
                "",
                "<#D7DCE2>Une guilde gravit le Pilier des Astres",
                "<#D7DCE2>ensemble : ses étages, son classement,",
                "<#D7DCE2>ses primes et sa banque.",
                "",
                "<#5EE89A>▶ Clic : /guilde creer <nom>"), t -> {
            p.closeInventory();
            p.sendMessage(Text.mm(Text.PREFIX + "<click:suggest_command:'/guilde creer '><" + HEX + "><b>[Cliquez ici]</b></" + HEX
                    + "> puis tapez le nom de votre guilde <#8C96A3>(3 à 20 caractères, " + Text.num(cost) + " or).</click>"));
        });
        Guild inv = guilds.invitedTo(p.getUniqueId());
        if (inv == null) {
            m.set(13, Items.icon(Material.PAPER, "<#8C96A3><b>Aucune invitation</b>",
                    "<#5A6270>Le chef ou un officier d'une guilde", "<#5A6270>de votre faction peut vous inviter."), null);
        } else {
            m.set(13, Items.glow(Items.icon(Material.PAPER, "<#5EE89A><b>Invitation : " + Text.esc(inv.name()) + "</b>",
                    "<#8C96A3>Membres : <white>" + inv.size() + "/" + guilds.maxMembers(),
                    "<#8C96A3>Étage franchi : <white>" + inv.towerCleared(),
                    "",
                    "<#5EE89A>▶ Clic gauche : rejoindre", "<#FF5C5C>▶ Clic droit : refuser")), t -> {
                if (t.isRightClick()) {
                    guilds.decline(p);
                } else {
                    guilds.accept(p);
                }
                open(plugin, p);
            });
        }
        m.set(15, Items.icon(Material.GOLDEN_HELMET, "<#FFD66E><b>Classement des guildes</b>",
                "<#8C96A3>" + guilds.all().size() + " guilde(s), classées par étage", "<#8C96A3>franchi dans le Pilier des Astres."),
                t -> ranking(plugin, p));
        m.set(18, Items.icon(Material.ARROW, "<#8C96A3>Menu"), t -> Menus.main(plugin, p));
        Menus.frame(m, 3);
        m.open(p);
        Menus.click(p);
    }

    // ------------------------------------------------------------------ La guilde

    private static void guild(OrbitMMO plugin, Player p, PlayerData d, Guild g) {
        GuildService guilds = plugin.guilds();
        Rank mine = g.rank(p.getUniqueId());
        int max = plugin.floors().maxFloor();
        Menu m = new Menu(6, Text.mm(g.tag() + " <#8C96A3>· " + g.size() + "/" + guilds.maxMembers()))
                .skin(g.name(), g.size() + "/" + guilds.maxMembers() + " membres · " + mine.label, "◆ " + Text.num(g.bank()) + " or");
        Member chef = g.leader();
        Guild.Member self = g.member(p.getUniqueId());
        m.set(4, Items.icon(banner(g.faction()), "<" + HEX + "><b>" + Text.esc(g.name()) + "</b>",
                "<#8C96A3>Faction : " + g.faction().tag(),
                "<#8C96A3>Chef : <white>" + (chef == null ? "—" : Text.esc(chef.name())),
                "<#8C96A3>Membres : <white>" + g.size() + " / " + guilds.maxMembers() + " <#5A6270>(" + guilds.online(g) + " en ligne)",
                "<#8C96A3>Fondée le : <white>" + day(g.created()),
                "",
                "<#8C96A3>Votre grade : " + mine.tag() + " <#5A6270>(depuis le " + day(self.since()) + ")"), null);

        // --- Membres (rangées 2 à 4), du chef aux membres.
        int slot = 9;
        for (Member mb : g.sorted()) {
            if (slot > 35) {
                break;
            }
            Player mp = Bukkit.getPlayer(mb.id);
            PlayerData md = mp == null ? null : plugin.players().loaded(mb.id);
            List<String> lore = new ArrayList<>();
            lore.add("<#8C96A3>Grade : " + mb.rank().tag());
            if (md != null && plugin.progression().valid(md)) {
                SpecDef ms = plugin.progression().spec(md);
                lore.add("<#8C96A3>" + plugin.progression().cls(md).name(md.faction) + (ms == null ? "" : " · " + ms.name) + " · Niv." + md.level);
                lore.add("<#5EE89A>En ligne");
            } else {
                lore.add("<#5A6270>Hors ligne");
            }
            lore.add("<#5A6270>Dans la guilde depuis le " + day(mb.since()));
            boolean other = !mb.id.equals(p.getUniqueId());
            boolean canKick = other && mine != Rank.MEMBRE && mb.rank().ordinal() > mine.ordinal();
            boolean isChef = mine == Rank.CHEF && other;
            if (isChef || canKick) {
                lore.add("");
            }
            if (isChef) {
                lore.add(mb.rank() == Rank.MEMBRE ? "<#5EE89A>▶ Clic gauche : promouvoir officier" : "<#FF8A3B>▶ Clic gauche : rétrograder membre");
                lore.add("<#FFD66E>▶ Clic droit : lui donner la place de chef");
            }
            if (canKick) {
                lore.add("<#FF5C5C>▶ Maj + clic droit : exclure");
            }
            String title = "<" + mb.rank().hex + "><b>" + Text.esc(String.valueOf(mb.name())) + "</b>";
            ItemStack it = mp == null ? Items.icon(Material.SKELETON_SKULL, title, lore) : Menus.head(mp, title, lore);
            m.set(slot++, it, !isChef && !canKick ? null : t -> {
                if (t.isShiftClick() && t.isRightClick()) {
                    if (canKick) {
                        confirm(plugin, p, "Exclure " + mb.name(), "<#FF5C5C>Exclure " + Text.esc(String.valueOf(mb.name())) + " de la guilde ?",
                                () -> guilds.kick(p, mb.name()));
                    }
                } else if (!isChef) {
                    return;
                } else if (t.isRightClick()) {
                    confirm(plugin, p, "Nouveau chef", "<#FFD66E>Donner votre place de chef à " + Text.esc(String.valueOf(mb.name())) + " ?",
                            () -> guilds.transfer(p, mb.name()));
                } else {
                    if (mb.rank() == Rank.MEMBRE) {
                        guilds.promote(p, mb.name());
                    } else {
                        guilds.demote(p, mb.name());
                    }
                    open(plugin, p);
                }
            });
        }

        // --- Rangée du bas.
        m.set(45, Items.icon(Material.ARROW, "<#8C96A3>Menu"), t -> Menus.main(plugin, p));
        m.set(47, Items.icon(Material.GOLD_INGOT, "<#FFD66E><b>Banque de guilde</b>",
                "<#8C96A3>En banque : <#FFD66E>" + Text.num(g.bank()) + " or",
                "<#8C96A3>Tout membre peut déposer ;", "<#8C96A3>le chef et les officiers retirent.",
                "<#8C96A3>Les primes du Pilier y tombent.",
                "", "<#5EE89A>▶ Clic : ouvrir la banque"), t -> bank(plugin, p));
        int place = guilds.place(g);
        m.set(48, Items.icon(Material.ENDER_EYE, "<#7FD4FF><b>Le Pilier des Astres</b>",
                "<#8C96A3>Étage franchi par la guilde : <white>" + (g.towerCleared() > 0 ? g.towerCleared() + " / " + max : "aucun"),
                "<#8C96A3>Étages ouverts à ses membres : <white>" + Math.min(max, plugin.floors().cleared(d) + 1),
                "<#8C96A3>Classement : <white>" + GuildService.ordinal(place) + " sur " + guilds.all().size(),
                "<#8C96A3>Premiers passages : <white>" + g.firsts(),
                "",
                "<#D7DCE2>La guilde gravit le Pilier dans l'ordre :",
                "<#D7DCE2>battez le boss de l'étage suivant.",
                "<#D7DCE2>Première guilde à franchir un étage :",
                "<#D7DCE2>prime en or dans la banque.",
                "",
                "<#5EE89A>▶ Clic : classement des guildes"), t -> ranking(plugin, p));
        if (mine != Rank.MEMBRE) {
            m.set(49, Items.icon(Material.PAPER, "<#5EE89A><b>Inviter un joueur</b>",
                    "<#8C96A3>Un joueur en ligne de la faction " + g.faction().label + ",", "<#8C96A3>sans guilde.",
                    g.size() >= guilds.maxMembers() ? "<#FF5C5C>La guilde est complète." : "<#5EE89A>▶ Clic : /guilde inviter <joueur>"), t -> {
                p.closeInventory();
                p.sendMessage(Text.mm(Text.PREFIX + "<click:suggest_command:'/guilde inviter '><" + HEX + "><b>[Cliquez ici]</b></" + HEX
                        + "> puis tapez le nom du joueur à inviter.</click>"));
            });
        }
        boolean chat = guilds.chatMode(p.getUniqueId());
        m.set(50, Items.icon(Material.WRITABLE_BOOK, "<" + HEX + "><b>Canal de guilde</b>",
                "<#8C96A3>Vous écrivez dans : <white>" + (chat ? "le canal de guilde" : "le chat général"),
                "<#5A6270>Un seul message : /gc <message>",
                "", "<#5EE89A>▶ Clic : " + (chat ? "revenir au chat général" : "écrire dans le canal de guilde")), t -> {
            guilds.toggleChat(p);
            open(plugin, p);
        });
        if (mine == Rank.CHEF) {
            m.set(53, Items.icon(Material.TNT, "<#FF5C5C><b>Dissoudre la guilde</b>",
                    "<#8C96A3>Définitif. L'or de la banque vous revient.",
                    "<#5A6270>Pour partir sans dissoudre : donnez", "<#5A6270>d'abord votre place de chef."), t ->
                    confirm(plugin, p, "Dissoudre", "<#FF5C5C>Dissoudre " + Text.esc(g.name()) + " pour de bon ?", () -> guilds.disband(p)));
        } else {
            m.set(53, Items.icon(Material.RED_DYE, "<#FF5C5C><b>Quitter la guilde</b>",
                    "<#8C96A3>Vous gardez les étages du Pilier", "<#8C96A3>que vous avez franchis vous-même."), t ->
                    confirm(plugin, p, "Quitter", "<#FF5C5C>Quitter la guilde " + Text.esc(g.name()) + " ?", () -> guilds.leave(p)));
        }
        Menus.frame(m, 6);
        m.open(p);
        Menus.click(p);
    }

    /** Confirmation d'une action sans retour ; dans les deux cas on revient au menu de guilde. */
    private static void confirm(OrbitMMO plugin, Player p, String title, String question, Runnable yes) {
        Menu m = new Menu(3, Text.mm("<#FF8A3B><b>" + Text.esc(title) + "</b>")).skin(title, "Confirmation", null);
        m.set(11, Items.icon(Material.LIME_DYE, "<#5EE89A><b>Confirmer</b>", question), t -> {
            yes.run();
            open(plugin, p);
        });
        m.set(15, Items.icon(Material.RED_DYE, "<#FF5C5C><b>Annuler</b>"), t -> open(plugin, p));
        Menus.frame(m, 3);
        m.open(p);
        Menus.click(p);
    }

    // ------------------------------------------------------------------ Banque

    public static void bank(OrbitMMO plugin, Player p) {
        GuildService guilds = plugin.guilds();
        Guild g = guilds.of(p.getUniqueId());
        if (g == null) {
            Text.msg(p, "<#FF5C5C>Vous n'êtes dans aucune guilde.");
            return;
        }
        PlayerData d = plugin.players().get(p);
        boolean officer = g.rank(p.getUniqueId()) != Rank.MEMBRE;
        Menu m = new Menu(3, Text.mm("<#FFD66E><b>Banque</b></#FFD66E> <#8C96A3>· " + Text.esc(g.name()) + " · " + Text.num(g.bank()) + " or"))
                .skin("Banque de guilde", "En banque : " + Text.num(g.bank()) + " or", Menus.gold(d));
        m.set(4, Items.icon(Material.GOLD_BLOCK, "<#FFD66E><b>" + Text.num(g.bank()) + " or en banque</b>",
                "<#8C96A3>Sur vous : <#FFD66E>" + Text.num(d.gold) + " or",
                "<#5A6270>Les primes de premier passage du", "<#5A6270>Pilier des Astres tombent ici.",
                "<#5A6270>Aussi : /guilde deposer · /guilde retirer"), null);
        long[] sums = {100, 1000, 0};
        for (int i = 0; i < sums.length; i++) {
            long n = sums[i];
            String label = n == 0 ? "tout" : Text.num(n) + " or";
            m.set(10 + i, Items.icon(n == 0 ? Material.GOLD_INGOT : Material.GOLD_NUGGET, "<#5EE89A><b>Déposer " + label + "</b>",
                    n == 0 ? "<#8C96A3>Tout l'or que vous portez." : d.gold < n ? "<#FF5C5C>Vous n'avez pas cet or." : "<#8C96A3>De votre bourse vers la banque."), t -> {
                guilds.deposit(p, n);
                bank(plugin, p);
            });
            if (officer) {
                m.set(14 + i, Items.icon(n == 0 ? Material.GOLD_INGOT : Material.GOLD_NUGGET, "<#FF8A3B><b>Retirer " + label + "</b>",
                        n == 0 ? "<#8C96A3>Toute la banque." : g.bank() < n ? "<#FF5C5C>La banque n'a pas cet or." : "<#8C96A3>De la banque vers votre bourse."), t -> {
                    guilds.withdraw(p, n);
                    bank(plugin, p);
                });
            }
        }
        if (!officer) {
            m.set(15, Items.icon(Material.BARRIER, "<#5A6270><b>Retirer</b>", "<#5A6270>Réservé au chef et aux officiers."), null);
        }
        m.set(18, Items.icon(Material.ARROW, "<#8C96A3>Guilde"), t -> open(plugin, p));
        Menus.frame(m, 3);
        m.open(p);
        Menus.click(p);
    }

    // ------------------------------------------------------------------ Classement

    public static void ranking(OrbitMMO plugin, Player p) {
        GuildService guilds = plugin.guilds();
        Guild mine = guilds.of(p.getUniqueId());
        List<Guild> top = guilds.ranking();
        int max = plugin.floors().maxFloor();
        Menu m = new Menu(6, Text.mm("<#FFD66E><b>Classement des guildes</b></#FFD66E> <#8C96A3>· " + top.size()))
                .skin("Classement des guildes", "Pilier des Astres · étage franchi", top.size() + " guilde(s)");
        if (top.isEmpty()) {
            m.set(22, Items.icon(Material.WHITE_BANNER, "<#8C96A3><b>Aucune guilde</b>",
                    "<#5A6270>Fondez la première : /guilde creer <nom>"), null);
        }
        int n = 0;
        for (Guild g : top) {
            if (n >= 45) {
                break;
            }
            Member chef = g.leader();
            GuildService.First first = g.towerCleared() > 0 ? guilds.first(g.towerCleared()) : null;
            List<String> lore = new ArrayList<>();
            lore.add("<#8C96A3>Étage franchi : <white>" + (g.towerCleared() > 0 ? g.towerCleared() + " / " + max : "aucun")
                    + (g.towerClearedAt() > 0 ? " <#5A6270>(le " + day(g.towerClearedAt()) + ")" : ""));
            lore.add("<#8C96A3>Premiers passages : <white>" + g.firsts()
                    + (first != null && first.guildId().equals(g.id()) ? " <#FFD66E>· première à cet étage" : ""));
            lore.add("<#8C96A3>Faction : " + g.faction().tag());
            lore.add("<#8C96A3>Chef : <white>" + (chef == null ? "—" : Text.esc(chef.name())));
            lore.add("<#8C96A3>Membres : <white>" + g.size() + " / " + guilds.maxMembers());
            if (g == mine) {
                lore.add("");
                lore.add("<" + HEX + ">Votre guilde");
            }
            String medal = n == 0 ? "#FFD66E" : n == 1 ? "#D7DCE2" : n == 2 ? "#D98A4E" : "#8C96A3";
            ItemStack it = Items.icon(banner(g.faction()), "<" + medal + "><b>" + (n + 1) + ".</b></" + medal + "> <" + HEX + "><b>"
                    + Text.esc(g.name()) + "</b>", lore);
            m.set(n, g == mine ? Items.glow(it) : it, null);
            n++;
        }
        m.set(49, Items.icon(Material.ARROW, "<#8C96A3>Guilde"), t -> open(plugin, p));
        Menus.frame(m, 6);
        m.open(p);
        Menus.click(p);
    }
}
