package fr.orbit.casino;

import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Livre des règles du casino (blackjack et poker), ouvert par /oc regles, en cliquant sur un croupier
 * ou avec le bouton « Règles » donné en s'asseyant. Les règles écrites ici suivent celles du code
 * (Blackjack.java, Poker.java, config.yml) : les changer ensemble.
 */
public final class Guide {

    // Couleurs lisibles sur le papier beige du livre (les couleurs du chat y sont trop pâles).
    private static final String T = "<#8A5A00><b>";   // titre or foncé
    private static final String P = "<#5B2A86><b>";   // titre poker, violet foncé
    private static final String K = "<#1F3A5F>";      // mot important
    private static final String G = "<#555555>";      // note

    public static final int BLACKJACK = 2;
    public static final int POKER = 6;
    public static final int MAINS = 12;
    public static final int ROULETTE = 14, CRASH = 15, MINES = 16, PLINKO = 17, ROUE = 18, BILLARD = 19, JUKEBOX = 21;

    /** Chapitre demandé par /oc regles &lt;nom&gt;. */
    public static int chapter(String what) {
        if (what.startsWith("black") || what.equals("bj")) {
            return BLACKJACK;
        }
        if (what.startsWith("poker")) {
            return POKER;
        }
        if (what.startsWith("main")) {
            return MAINS;
        }
        if (what.startsWith("roulette")) {
            return ROULETTE;
        }
        if (what.startsWith("crash")) {
            return CRASH;
        }
        if (what.startsWith("mine")) {
            return MINES;
        }
        if (what.startsWith("plinko")) {
            return PLINKO;
        }
        if (what.startsWith("roue")) {
            return ROUE;
        }
        if (what.startsWith("billard")) {
            return BILLARD;
        }
        if (what.startsWith("juke") || what.startsWith("musique")) {
            return JUKEBOX;
        }
        return 1;
    }

    private static List<String> pages(OrbitCasino plugin) {
        var c = plugin.getConfig();
        long bjMin = 10, bjMax = c.getLong("blackjack.mise-max", 1000);
        List<?> mises = c.getList("blackjack.mises", List.of());
        if (!mises.isEmpty()) {
            bjMin = Long.parseLong(String.valueOf(mises.get(0)));
        }
        int bjTemps = c.getInt("blackjack.temps-de-jeu", 30);
        int pkTemps = c.getInt("poker.temps-de-jeu", 30);
        StringBuilder tables = new StringBuilder();
        var pt = c.getConfigurationSection("poker.tables");
        if (pt != null) {
            for (String id : pt.getKeys(false)) {
                tables.append("\n").append(K).append(pt.getString(id + ".nom", id).replace("Table ", "")).append("</#1F3A5F> : ")
                        .append(Txt.n(pt.getLong(id + ".petite-blinde"))).append(" / ").append(Txt.n(pt.getLong(id + ".grosse-blinde")));
            }
        }

        List<String> pages = new ArrayList<>();
        // 1 — sommaire
        pages.add(T + "GUIDE DU CASINO</b></#8A5A00>\n"
                + link(BLACKJACK, "♠ Le blackjack") + "\n"
                + link(POKER, "♣ Le poker") + "\n"
                + link(MAINS, "★ Les mains du poker") + "\n"
                + link(ROULETTE, "● La roulette") + "\n\n"
                + T + "SALON DES ÉTOILES</b></#8A5A00>\n"
                + link(CRASH, "▲ Crash") + "   " + link(MINES, "◆ Mines") + "\n"
                + link(PLINKO, "● Plinko") + "   " + link(ROUE, "✦ La roue") + "\n"
                + link(BILLARD, "◎ Billard") + "   " + link(JUKEBOX, "♪ Jukebox") + "\n\n"
                + G + "Tout se joue en Orbit Coins (OC). Ils se gagnent en jouant et ne s'achètent jamais.");

        // 2-5 — blackjack
        pages.add(T + "BLACKJACK</b></#8A5A00>\n" + G + "Le but</#555555>\n\n"
                + "Battre le " + K + "croupier</#1F3A5F> : avoir plus de points que lui " + K + "sans dépasser 21</#1F3A5F>.\n\n"
                + "2 à 10 : leur valeur\nValet, Dame, Roi : 10\nAs : 1 ou 11, au mieux pour toi.");
        pages.add(T + "BLACKJACK</b></#8A5A00>\n" + G + "Le déroulé</#555555>\n\n"
                + "1. Clic droit sur le " + K + "tapis vert</#1F3A5F> pour t'asseoir.\n"
                + "2. Mise de " + Txt.n(bjMin) + " à " + Txt.n(bjMax) + " OC avec les boutons.\n"
                + "3. Chacun reçoit 2 cartes, le croupier en montre une.\n"
                + "4. Chacun joue sa main à son tour (" + bjTemps + " s).");
        pages.add(T + "BLACKJACK</b></#8A5A00>\n" + G + "Tes choix</#555555>\n\n"
                + K + "Tirer</#1F3A5F> : une carte de plus.\n"
                + K + "Rester</#1F3A5F> : tu gardes ta main.\n"
                + K + "Doubler</#1F3A5F> : mise x2, une seule carte (sur tes 2 premières).\n"
                + K + "Séparer</#1F3A5F> : 2 cartes de même valeur font 2 mains (une fois).");
        pages.add(T + "BLACKJACK</b></#8A5A00>\n" + G + "Les gains</#555555>\n\n"
                + "Le croupier tire jusqu'à " + K + "17</#1F3A5F> puis s'arrête.\n\n"
                + "Gagné : mise x2\n"
                + K + "Blackjack</#1F3A5F> (As + 10 d'entrée) : paie 3 pour 2\n"
                + "Égalité : mise rendue\n"
                + "Plus de 21 : perdu, même si le croupier saute aussi.");

        // 6-11 — poker
        pages.add(P + "POKER</b></#5B2A86>\n" + G + "Texas Hold'em</#555555>\n\n"
                + "Entre joueurs, de 2 à 6. " + K + "Le casino ne prend rien</#1F3A5F> : tout ce qui est misé revient aux joueurs.\n\n"
                + "Blindes des tables :" + tables);
        pages.add(P + "POKER</b></#5B2A86>\n" + G + "S'asseoir</#555555>\n\n"
                + "Clic droit sur la table, puis choisis ta " + K + "cave</#1F3A5F> : 20, 50 ou 100 grosses blindes. C'est ton tapis.\n\n"
                + "Assis, tu ne bouges plus. Pour te lever : " + K + "Quitter</#1F3A5F>, et tu repars avec ton tapis. "
                + "En pleine main, partir = " + K + "te coucher</#1F3A5F>.\n\n"
                + G + "Une connexion = une place par table.");
        pages.add(P + "POKER</b></#5B2A86>\n" + G + "Une main</#555555>\n\n"
                + "Deux joueurs paient les " + K + "blindes</#1F3A5F> (petite et grosse).\n\n"
                + "Chacun reçoit " + K + "2 cartes cachées</#1F3A5F>.\n\n"
                + "Puis 5 cartes communes arrivent au milieu, en 3 fois.");
        pages.add(P + "POKER</b></#5B2A86>\n" + G + "Les 4 tours</#555555>\n\n"
                + "1. " + K + "Avant le flop</#1F3A5F> : tes 2 cartes seules\n"
                + "2. " + K + "Flop</#1F3A5F> : 3 cartes au milieu\n"
                + "3. " + K + "Turn</#1F3A5F> : la 4e\n"
                + "4. " + K + "River</#1F3A5F> : la 5e\n\n"
                + "On mise à chaque tour.");
        pages.add(P + "POKER</b></#5B2A86>\n" + G + "Tes actions</#555555>\n\n"
                + K + "Parole</#1F3A5F> : rien à payer, tu passes.\n"
                + K + "Suivre</#1F3A5F> : tu paies la mise.\n"
                + K + "Relancer</#1F3A5F> : tu montes (au moins la grosse blinde).\n"
                + K + "Tapis</#1F3A5F> : tout ton tapis.\n"
                + K + "Se coucher</#1F3A5F> : tu abandonnes la main.");
        pages.add(P + "POKER</b></#5B2A86>\n" + G + "La fin</#555555>\n\n"
                + "Si tous se couchent, le dernier prend le pot.\n\n"
                + "Sinon, la " + K + "meilleure main de 5 cartes</#1F3A5F> (parmi tes 2 et les 5 du milieu) gagne. Égalité : pot partagé.\n\n"
                + G + pkTemps + " s pour jouer, sinon parole ou couché. Deux fois : levé de table.");

        // 12-13 — les mains
        pages.add(P + "LES MAINS</b></#5B2A86>\n" + G + "De la plus forte…</#555555>\n\n"
                + K + "Quinte flush</#1F3A5F>\n" + G + "5 qui se suivent, même couleur</#555555>\n"
                + K + "Carré</#1F3A5F>\n" + G + "4 cartes pareilles</#555555>\n"
                + K + "Full</#1F3A5F>\n" + G + "brelan + paire</#555555>\n"
                + K + "Couleur</#1F3A5F>\n" + G + "5 de la même couleur</#555555>\n"
                + K + "Quinte</#1F3A5F>\n" + G + "5 qui se suivent</#555555>");
        pages.add(P + "LES MAINS</b></#5B2A86>\n" + G + "… à la plus faible</#555555>\n\n"
                + K + "Brelan</#1F3A5F>\n" + G + "3 cartes pareilles</#555555>\n"
                + K + "Double paire</#1F3A5F>\n" + G + "2 paires</#555555>\n"
                + K + "Paire</#1F3A5F>\n" + G + "2 cartes pareilles</#555555>\n"
                + K + "Carte haute</#1F3A5F>\n" + G + "rien : la plus haute compte</#555555>\n\n"
                + link(1, "↩ Sommaire"));

        // 14 — roulette
        pages.add(T + "ROULETTE</b></#8A5A00>\n" + G + "Un seul zéro</#555555>\n\n"
                + "Pose tes jetons sur le tapis, la roue tourne " + c.getInt("roulette.attente-mises", 30) + " s après la 1re mise.\n\n"
                + K + "Numéro plein</#1F3A5F> : 36 fois la mise\n"
                + K + "Douzaine, colonne</#1F3A5F> : 3 fois\n"
                + K + "Rouge, noir, pair…</#1F3A5F> : 2 fois\n\n"
                + G + "Le zéro fait perdre les chances simples. 97,3 % redistribués. Max " + Txt.n(c.getLong("roulette.mise-max", 5000)) + " OC.");
        // 15 — Crash
        int crashPct = (int) Math.round(c.getDouble("crash.redistribution", 0.97) * 100);
        pages.add(T + "CRASH</b></#8A5A00>\n" + G + "La fusée ORBIT</#555555>\n\n"
                + "Mise (10 à 1 000 OC) à la borne. La fusée décolle et le " + K + "multiplicateur</#1F3A5F> monte.\n\n"
                + K + "Encaisse</#1F3A5F> avant qu'elle explose : mise × multiplicateur. Trop tard : tout est perdu.\n\n"
                + G + "Encaissement auto possible. " + crashPct + " % redistribués, plafond ×" + (int) c.getDouble("crash.plafond", 100) + ".");
        // 16 — Mines
        pages.add(T + "MINES</b></#8A5A00>\n" + G + "Cristaux et mines</#555555>\n\n"
                + "Choisis ta mise et le " + K + "nombre de mines</#1F3A5F> (1 à 24) cachées dans 25 cases.\n\n"
                + "Chaque " + K + "cristal</#1F3A5F> trouvé fait monter le gain. Encaisse quand tu veux ; une mine fait tout perdre.\n\n"
                + G + "Plus de mines = plus de gain par case. " + (int) Math.round(c.getDouble("mines.redistribution", 0.97) * 100)
                + " % redistribués.");
        // 17 — Plinko
        pages.add(T + "PLINKO</b></#8A5A00>\n" + G + "La planche à picots</#555555>\n\n"
                + "Clic droit sur la planche : une bille tombe et rebondit au hasard sur 8 rangées.\n\n"
                + "Cases : " + K + "×11 ×3 ×1,3 ×0,7 ×0,4</#1F3A5F> ×0,7 ×1,3 ×3 ×11\n\n"
                + "Accroupi + clic : changer la mise (10 à 500).\n"
                + G + "97,3 % redistribués.");
        // 18 — roue
        pages.add(T + "ROUE DES ÉTOILES</b></#8A5A00>\n\n"
                + "Mise auprès de l'animatrice sur une couleur, la roue tourne " + c.getInt("roue.attente-mises", 20) + " s après.\n\n"
                + K + "×2</#1F3A5F> 12 cases · " + K + "×4</#1F3A5F> 6\n"
                + K + "×8</#1F3A5F> 3 · " + K + "×12</#1F3A5F> 2\n"
                + K + "ORBIT</#1F3A5F> et " + K + "COMÈTE</#1F3A5F> : 1 case, ×24\n\n"
                + G + "Sur 25 cases. 96 % redistribués. Max " + Txt.n(c.getLong("roue.mise-max", 2000)) + " OC par tour.");
        // 19-20 — billard
        pages.add(T + "BILLARD</b></#8A5A00>\n" + G + "Jouer</#555555>\n\n"
                + K + "Viser</#1F3A5F> : regarde le point du tapis où envoyer la blanche (une ligne blanche te guide).\n\n"
                + K + "Tirer</#1F3A5F> : reste accroupi pour charger (plus long = plus fort), relâche.\n\n"
                + G + "Solo, contre le bot, ou défi à 2 avec mise : le gagnant prend tout, le casino ne prend rien.");
        pages.add(T + "BILLARD</b></#8A5A00>\n" + G + "Les règles</#555555>\n\n"
                + "La 1re boule rentrée donne ton groupe : " + K + "pleines</#1F3A5F> (1-7) ou " + K + "rayées</#1F3A5F> (9-15).\n"
                + "Rentre-les toutes, puis la " + K + "8</#1F3A5F>.\n\n"
                + "Tu rejoues si tu rentres une des tiennes. Faute (blanche rentrée, rien touché, mauvaise boule) : la main passe.\n"
                + G + "8 trop tôt = perdu.");
        // 21 — jukebox
        pages.add(T + "JUKEBOX</b></#8A5A00>\n" + G + "Au comptoir du bar</#555555>\n\n"
                + "Choisis un morceau : il rejoint la " + K + "file</#1F3A5F> (2 par joueur).\n\n"
                + K + "Vote</#1F3A5F> pour faire monter un morceau, ou pour passer celui qui joue (la moitié de la salle suffit).\n\n"
                + G + "Disques de Minecraft seulement.\n\n" + link(1, "↩ Sommaire"));
        return pages;
    }

    private static String link(int page, String label) {
        return "<click:change_page:'" + page + "'><hover:show_text:'Aller à la page'><#1F5FA8><u>" + label + "</u></#1F5FA8></hover></click>";
    }

    /** Ouvre le livre, directement à la page voulue si le client le permet (sinon au sommaire). */
    public static void open(OrbitCasino plugin, Player p, int page) {
        List<String> raw = pages(plugin);
        int n = raw.size();
        // Le client ouvre toujours un livre à sa page 1 : pour tomber sur un chapitre, on fait tourner les pages
        // pour qu'il soit en tête, et on recale les liens « aller à la page ».
        int start = Math.max(1, Math.min(page, n)) - 1;
        List<Component> comps = new ArrayList<>();
        for (int k = 0; k < n; k++) {
            String s = raw.get((k + start) % n);
            s = LINK.matcher(s).replaceAll(m -> "change_page:'" + ((Integer.parseInt(m.group(1)) - 1 - start + n) % n + 1) + "'");
            comps.add(Txt.mm(s));
        }
        p.openBook(Book.book(Txt.mm("Guide du casino"), Txt.mm("ORBIT"), comps));
        Txt.sound(p, Sound.ITEM_BOOK_PAGE_TURN, 1f);
    }

    private static final java.util.regex.Pattern LINK = java.util.regex.Pattern.compile("change_page:'(\\d+)'");
}
