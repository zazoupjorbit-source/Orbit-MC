package fr.orbit.staff;

import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Livre de stats d'un joueur (style Pulsar) : identité, direct, combat, modération, historique, notes,
 * comptes liés, stats du mode, puis une page d'actions cliquables.
 */
public final class Livre {

    private static final String B = "<#1f5fbf>";   // bleu lisible sur le papier
    private static final String O = "<#a87b00>";   // or lisible sur le papier
    private static final String G = "<dark_gray>";
    private static final String N = "<black>";
    private static final String R = "<dark_red>";
    private static final String V = "<dark_green>";

    private Livre() {
    }

    public static void ouvrir(OrbitStaff plugin, Player staff, UUID cible) {
        if (!staff.hasPermission("orbitstaff.stats")) {
            Txt.err(staff, "Permission manquante.");
            return;
        }
        if (!plugin.db().available()) {
            Txt.err(staff, "Base injoignable : livre indisponible.");
            return;
        }
        staff.sendActionBar(Txt.mm("<gray>Ouverture du livre..."));
        CompletableFuture<String> grade;
        try {
            grade = Grade.de(cible).exceptionally(e -> null);
        } catch (Throwable t) {
            grade = CompletableFuture.completedFuture(null);
        }
        CompletableFuture<String> g = grade;
        plugin.db().then(plugin.joueurs().fiche(cible).thenCombine(g, (f, gr) -> new Object[]{f, gr}), r -> {
            if (!staff.isOnline()) {
                return;
            }
            Joueurs.Fiche f = (Joueurs.Fiche) r[0];
            if (f.premiere() == null && Bukkit.getPlayer(cible) == null) {
                Txt.err(staff, "Joueur inconnu du réseau.");
                return;
            }
            staff.openBook(construire(plugin, f, (String) r[1]));
        }, () -> Txt.err(staff, "Lecture du livre impossible (voir la console)."));
    }

    private static Book construire(OrbitStaff plugin, Joueurs.Fiche f, String grade) {
        Player p = Bukkit.getPlayer(f.uuid());
        String nom = p != null ? p.getName() : f.nom();
        List<String> pages = new ArrayList<>();

        // 1. Identité
        long tempsTotal = f.tempsJeu() + (p != null ? plugin.joueurs().sessionSecondes(f.uuid()) : 0);
        String statut = p != null ? V + "● En ligne ici " + G + "(" + plugin.serveur() + ")"
                : f.enLigne() ? V + "● En ligne " + G + "(" + Txt.esc(f.serveur()) + ")"
                : G + "● Hors ligne";
        pages.add("<bold>" + B + "ORBIT " + O + "STAFF</bold>\n" + G + "Livre de stats\n\n"
                + "<bold>" + N + Txt.esc(nom) + "</bold>\n" + statut + "\n\n"
                + G + "Grade : " + N + (grade == null ? "—" : Txt.esc(grade)) + "\n"
                + G + "Temps de jeu : " + N + Txt.duree(Duration.ofSeconds(tempsTotal)) + "\n"
                + G + "1re co : " + N + Txt.date(f.premiere()) + "\n"
                + G + "Vu le : " + N + (p != null ? "maintenant" : Txt.date(f.derniere())) + "\n"
                + G + "Dernier serveur : " + N + (f.serveur() == null ? "—" : Txt.esc(f.serveur())) + "\n\n"
                + "<hover:show_text:'" + f.uuid() + "'>" + B + "[UUID]</hover>");

        // 2. En direct (seulement s'il est sur ce serveur)
        if (p != null) {
            var max = p.getAttribute(Attribute.MAX_HEALTH);
            var gel = plugin.freeze().gel(p.getUniqueId());
            pages.add("<bold>" + B + "En direct</bold>\n\n"
                    + G + "Vie : " + N + Math.round(p.getHealth()) + "/" + Math.round(max == null ? 20 : max.getValue()) + "\n"
                    + G + "Faim : " + N + p.getFoodLevel() + "/20\n"
                    + G + "Ping : " + ping(p.getPing()) + "\n"
                    + G + "Mode : " + N + p.getGameMode().name().toLowerCase(Locale.ROOT) + (p.isFlying() ? " (vol)" : "") + "\n"
                    + G + "Monde : " + N + Txt.esc(p.getWorld().getName()) + "\n"
                    + G + "Pos : " + N + p.getLocation().getBlockX() + " " + p.getLocation().getBlockY() + " " + p.getLocation().getBlockZ() + "\n"
                    + G + "Niveau XP : " + N + p.getLevel() + "\n"
                    + G + "Effets : " + N + p.getActivePotionEffects().size() + "\n"
                    + G + "Gelé : " + (gel == null ? N + "non" : R + "oui " + G + "(" + Txt.esc(gel.staff()) + ")") + "\n"
                    + G + "Mode staff : " + N + (plugin.staffMode().actif(p) ? "oui" : "non") + "\n"
                    + G + "Client : " + N + Txt.esc(client(p)));
        }

        // 3. Combat
        int[] attente = plugin.joueurs().enAttente(f.uuid());
        int k = f.kills() + attente[0], m = f.morts() + attente[1];
        String ratio = m == 0 ? String.valueOf(k) : String.format(Locale.FRANCE, "%.2f", (double) k / m);
        StringBuilder c = new StringBuilder("<bold>" + B + "Combat</bold>\n\n"
                + G + "Kills : " + N + k + "\n" + G + "Morts : " + N + m + "\n" + G + "Ratio : " + N + ratio + "\n");
        int n = 0;
        for (Map.Entry<String, int[]> e : f.combat().entrySet()) {
            if (n++ == 3) {
                break;
            }
            c.append(G).append(" ").append(Txt.esc(e.getKey())).append(" : ").append(N).append(e.getValue()[0]).append("/").append(e.getValue()[1]).append("\n");
        }
        Combat.Mesure me = p == null ? null : plugin.combat().mesure(p.getUniqueId());
        c.append("\n").append(G).append("CPS : ").append(N).append(me == null ? "—" : me.cps + " " + G + "(max " + cps(me.cpsMax) + G + ")").append("\n");
        c.append(G).append("Allonge max : ").append(N).append(me == null || me.allongeMax == 0 ? "—" : allonge(me.allongeMax)).append("\n");
        c.append(G).append("Alertes : ").append(alerte(f.alertes().getOrDefault("cps", 0))).append(G).append(" CPS, ")
                .append(alerte(f.alertes().getOrDefault("allonge", 0))).append(G).append(" allonge");
        pages.add(c.toString());

        // 4. Modération
        Map<Sanctions.Type, Integer> totaux = new EnumMap<>(Sanctions.Type.class);
        Sanctions.Sanction ban = null, mute = null;
        for (Sanctions.Sanction s : f.sanctions()) {
            totaux.merge(s.type(), 1, Integer::sum);
            if (s.enCours() && s.type().estBan() && ban == null) {
                ban = s;
            }
            if (s.enCours() && s.type() == Sanctions.Type.MUTE && mute == null) {
                mute = s;
            }
        }
        pages.add("<bold>" + B + "Modération</bold>\n\n"
                + G + "Ban : " + (ban == null ? V + "non" : R + (ban.fin() == null ? "définitif" : "fin " + Txt.date(ban.fin()))) + "\n"
                + G + "Mute : " + (mute == null ? V + "non" : R + (mute.fin() == null ? "définitif" : "fin " + Txt.date(mute.fin()))) + "\n\n"
                + G + "Warns : " + N + totaux.getOrDefault(Sanctions.Type.WARN, 0) + "\n"
                + G + "Mutes : " + N + totaux.getOrDefault(Sanctions.Type.MUTE, 0) + "\n"
                + G + "Kicks : " + N + totaux.getOrDefault(Sanctions.Type.KICK, 0) + "\n"
                + G + "Bans : " + N + (totaux.getOrDefault(Sanctions.Type.BAN, 0) + totaux.getOrDefault(Sanctions.Type.TEMPBAN, 0)) + "\n\n"
                + G + "Signalements : " + N + f.reports() + (f.reportsOuverts() > 0 ? " " + R + "(" + f.reportsOuverts() + " ouverts)" : "") + "\n"
                + G + "Notes staff : " + N + f.notes().size());

        // 5. Historique (6 dernières, détail au survol)
        StringBuilder h = new StringBuilder("<bold>" + B + "Historique</bold>\n\n");
        if (f.sanctions().isEmpty()) {
            h.append(V).append("Casier vierge.");
        }
        for (int i = 0; i < Math.min(6, f.sanctions().size()); i++) {
            Sanctions.Sanction s = f.sanctions().get(i);
            String survol = "#" + s.id() + " " + s.type().libelle() + "\nPar " + s.staff() + " sur " + s.serveur() + "\nLe " + Txt.date(s.debut())
                    + (s.fin() != null ? "\nFin " + Txt.date(s.fin()) : "") + "\nMotif : " + s.motif()
                    + (s.levePar() != null ? "\nLevée par " + s.levePar() : "");
            h.append("<hover:show_text:'").append(hover(survol)).append("'>")
                    .append(s.enCours() ? R : G).append(court(s.type())).append(" ").append(N)
                    .append(Txt.esc(coupe(s.motif(), 14))).append("</hover>\n");
        }
        pages.add(h.toString());

        // 6. Notes
        StringBuilder no = new StringBuilder("<bold>" + B + "Notes staff</bold>\n\n");
        if (f.notes().isEmpty()) {
            no.append(G).append("Aucune note.");
        }
        for (String[] note : f.notes()) {
            no.append("<hover:show_text:'").append(hover(note[1] + " — " + note[2] + "\n" + note[0])).append("'>")
                    .append(O).append("• ").append(N).append(Txt.esc(coupe(note[0], 34))).append("</hover>\n");
        }
        pages.add(no.toString());

        // 7. Comptes liés
        StringBuilder li = new StringBuilder("<bold>" + B + "Comptes liés</bold>\n" + G + "(même adresse IP)\n\n");
        if (f.comptesLies().isEmpty()) {
            li.append(V).append("Aucun autre compte.");
        }
        for (String a : f.comptesLies()) {
            li.append(a.endsWith("(banni)") ? R : N).append("• ").append(Txt.esc(a)).append("\n");
        }
        pages.add(li.toString());

        // 8+. Stats du mode (plugins du serveur)
        if (p != null) {
            for (Map.Entry<String, List<String>> e : plugin.ponts().stats(p).entrySet()) {
                List<String> lignes = e.getValue();
                for (int i = 0; i < lignes.size(); i += 11) {
                    StringBuilder s = new StringBuilder("<bold>" + B + Txt.esc(e.getKey()) + "</bold>" + (i > 0 ? G + " (suite)" : "") + "\n\n");
                    for (int j = i; j < Math.min(lignes.size(), i + 11); j++) {
                        s.append(N).append(lignes.get(j)).append("<reset>\n");
                    }
                    pages.add(s.toString());
                }
            }
        } else {
            pages.add("<bold>" + B + "Stats du mode</bold>\n\n" + G + "Le joueur n'est pas sur ce serveur : les stats propres au mode "
                    + "se lisent sur le serveur où il joue.");
        }

        // Dernière page : actions
        String a = "<bold>" + B + "Actions</bold>\n\n";
        if (p != null) {
            a += act("/panel " + nom, "▶ Panel") + act("/freeze " + nom, "▶ Geler / dégeler") + act("/invsee " + nom, "▶ Inventaire")
                    + act("/tp " + nom, "▶ Se TP");
        }
        a += act("/sanction " + nom, "▶ Sanctionner") + act("/historique " + nom, "▶ Historique complet")
                + "<click:suggest_command:'/note " + nom + " '>" + O + "▶ Ajouter une note</click>\n"
                + "<click:suggest_command:'/warn " + nom + " '>" + O + "▶ Avertir</click>\n";
        pages.add(a);

        List<Component> comp = new ArrayList<>();
        for (String s : pages) {
            comp.add(Txt.mm(s));
        }
        return Book.book(Component.text("Stats " + nom), Component.text("ORBIT"), comp);
    }

    private static String act(String cmd, String libelle) {
        return "<click:run_command:'" + cmd + "'>" + O + libelle + "</click>\n";
    }

    private static String court(Sanctions.Type t) {
        return switch (t) {
            case WARN -> "WARN";
            case MUTE -> "MUTE";
            case KICK -> "KICK";
            case TEMPBAN -> "TBAN";
            case BAN -> "BAN";
        };
    }

    private static String coupe(String s, int n) {
        return s == null ? "" : s.length() <= n ? s : s.substring(0, n - 1) + "…";
    }

    /** Texte libre dans un survol : pas d'apostrophe droite (elle fermerait l'argument). */
    private static String hover(String s) {
        return Txt.esc(s).replace("'", "’");
    }

    private static String ping(int ms) {
        return (ms < 80 ? V : ms < 180 ? O : R) + ms + " ms";
    }

    private static String cps(int v) {
        return (v >= 16 ? R : N) + v;
    }

    private static String allonge(double d) {
        return (d > 3.3 ? R : N) + String.format(Locale.FRANCE, "%.2f", d);
    }

    private static String alerte(int n) {
        return (n > 0 ? R : N) + n;
    }

    private static String client(Player p) {
        String b = p.getClientBrandName();
        return b == null ? "?" : b.length() > 18 ? b.substring(0, 18) : b;
    }

    static Instant maintenant() {
        return Instant.now();
    }
}
