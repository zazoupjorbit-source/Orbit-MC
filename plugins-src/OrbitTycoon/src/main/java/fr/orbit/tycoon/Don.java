package fr.orbit.tycoon;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * /donner <joueur|*> <quoi> [quantité] : donne une monnaie ou un objet du Tycoon (admin).
 * quoi = credit | flux | plasma | cle-1..cle-5 | gen-<id> | ressource-<id> | collecteur | coffre-collecteur | outil | navigateur | guide
 */
final class Don {

    /** Plafond par commande pour les objets (l'inventaire déborde au sol au-delà). */
    private static final int MAX_OBJETS = 2304;

    private record Objet(String nom, Supplier<ItemStack> fabrique) { }

    private final OrbitTycoon plugin;

    Don(OrbitTycoon plugin) { this.plugin = plugin; }

    boolean commande(CommandSender s, String[] args) {
        if (args.length < 2) { aide(s); return true; }
        String quoi = args[1].toLowerCase(Locale.ROOT);
        long qte = 1;
        if (args.length > 2) {
            try { qte = Long.parseLong(args[2]); } catch (NumberFormatException e) { qte = -1; }
            if (qte <= 0) { OrbitTycoon.msg(s, "<#FF4D4D>Quantité invalide :</#FF4D4D> <white>" + args[2]); return true; }
        }

        Monnaies.Monnaie m = Monnaies.Monnaie.parse(quoi);
        if (m != null) {
            if (args.length < 3) { OrbitTycoon.msg(s, "Usage : <#E0B864>/donner <joueur|*> " + m.id + " <montant>"); return true; }
            donnerMonnaie(s, args[0], m, qte);
            return true;
        }

        List<Player> cibles = cibles(args[0]);
        if (cibles.isEmpty()) { OrbitTycoon.msg(s, "<#FF4D4D>Joueur introuvable</#FF4D4D> <gray>(il doit être connecté pour recevoir un objet)."); return true; }

        // Objets uniques gérés par leur module (un seul exemplaire par joueur).
        switch (quoi) {
            case "outil", "navigateur", "guide" -> {
                for (Player p : cibles) {
                    switch (quoi) {
                        case "outil" -> plugin.outil().donner(p);
                        case "navigateur" -> plugin.navigateur().donner(p);
                        default -> plugin.guide().donner(p);
                    }
                }
                OrbitTycoon.msg(s, "<white>" + quoi + "<gray> donné à " + noms(cibles) + " <dark_gray>(un seul exemplaire par joueur).");
                return true;
            }
            default -> { }
        }

        Objet o = objet(quoi);
        if (o == null) { OrbitTycoon.msg(s, "<#FF4D4D>Objet inconnu :</#FF4D4D> <white>" + quoi + "<gray>. Liste : <#E0B864>/donner"); return true; }
        if (qte > MAX_OBJETS) { OrbitTycoon.msg(s, "<#FF4D4D>Trop d'objets</#FF4D4D> <gray>(" + MAX_OBJETS + " max par commande)."); return true; }
        int n = (int) qte;
        for (Player p : cibles) {
            donnerObjet(p, o.fabrique().get(), n);
            OrbitTycoon.msg(p, "<gray>Tu as reçu <white>" + n + " × " + o.nom() + "<gray>.");
        }
        OrbitTycoon.msg(s, "<white>" + n + " × " + o.nom() + "<gray> donné(s) à " + noms(cibles) + ".");
        return true;
    }

    // ------------------------------------------------------------------ monnaies

    private void donnerMonnaie(CommandSender s, String qui, Monnaies.Monnaie m, long montant) {
        Monnaies monnaies = plugin.monnaies();
        if (qui.equals("*")) {
            List<Player> tous = cibles("*");
            if (tous.isEmpty()) { OrbitTycoon.msg(s, "Personne n'est connecté."); return; }
            for (Player p : tous) {
                monnaies.ajouter(p.getUniqueId(), m, montant);
                OrbitTycoon.msg(p, "Tu as reçu " + m.format(montant) + "<gray>.");
            }
            OrbitTycoon.msg(s, m.format(montant) + "<gray> donné(s) à " + tous.size() + " joueur(s) connecté(s).");
            return;
        }
        Player enLigne = Bukkit.getPlayerExact(qui);
        OfflinePlayer cible = enLigne != null ? enLigne : Bukkit.getOfflinePlayer(qui);
        if (enLigne == null && !cible.hasPlayedBefore()) {
            OrbitTycoon.msg(s, "<#FF4D4D>Joueur inconnu :</#FF4D4D> <white>" + qui + "<gray> (jamais venu sur le serveur).");
            return;
        }
        monnaies.ajouter(cible.getUniqueId(), m, montant);
        OrbitTycoon.msg(s, m.format(montant) + "<gray> donné(s) à " + cible.getName()
                + ". Solde : " + m.format(monnaies.solde(cible.getUniqueId(), m)));
        if (enLigne != null) OrbitTycoon.msg(enLigne, "Tu as reçu " + m.format(montant) + "<gray>.");
    }

    // ------------------------------------------------------------------ objets

    private Objet objet(String quoi) {
        if (quoi.equals("collecteur")) return new Objet("Collecteur orbital", () -> plugin.collecteurs().objetCollecteur(false));
        if (quoi.equals("coffre-collecteur")) return new Objet("Coffre collecteur", () -> plugin.collecteurs().objetCollecteur(true));
        if (quoi.matches("cle-[1-6]")) {
            int n = quoi.charAt(4) - '1';
            return new Objet("Clé « " + plugin.coffres().nom(n) + " »", () -> plugin.coffres().cle(n, 1));
        }
        if (quoi.startsWith("gen-")) {
            Generateurs.Type t = plugin.generateurs().type(quoi.substring(4));
            return t == null ? null : new Objet("Générateur de " + t.nom(), () -> plugin.generateurs().generateur(t, 1));
        }
        if (quoi.startsWith("ressource-")) {
            Generateurs.Type t = plugin.generateurs().type(quoi.substring(10));
            return t == null ? null : new Objet(t.nom() + " (ressource)", () -> plugin.generateurs().produit(t, 1));
        }
        return null;
    }

    /** Donne n exemplaires par piles ; ce qui ne rentre pas tombe aux pieds du joueur. */
    private static void donnerObjet(Player p, ItemStack modele, int n) {
        int max = Math.max(1, modele.getMaxStackSize());
        while (n > 0) {
            int pile = Math.min(max, n);
            n -= pile;
            for (ItemStack reste : p.getInventory().addItem(modele.asQuantity(pile)).values())
                p.getWorld().dropItemNaturally(p.getLocation(), reste);
        }
    }

    // ------------------------------------------------------------------ outils

    private static List<Player> cibles(String qui) {
        if (qui.equals("*")) return new ArrayList<>(Bukkit.getOnlinePlayers());
        Player p = Bukkit.getPlayerExact(qui);
        return p == null ? List.of() : List.of(p);
    }

    private static String noms(List<Player> cibles) {
        return cibles.size() == 1 ? cibles.get(0).getName() : cibles.size() + " joueurs";
    }

    private List<String> tousLesIds() {
        List<String> ids = new ArrayList<>();
        for (Monnaies.Monnaie m : Monnaies.Monnaie.values()) ids.add(m.id);
        for (int i = 1; i <= Coffres.NB; i++) ids.add("cle-" + i);
        for (String id : plugin.generateurs().ids()) { ids.add("gen-" + id); ids.add("ressource-" + id); }
        ids.addAll(List.of("collecteur", "coffre-collecteur", "outil", "navigateur", "guide"));
        return ids;
    }

    private void aide(CommandSender s) {
        OrbitTycoon.msg(s, "<#E0B864>/donner <joueur|*> <quoi> [quantité]<gray> (* = tous les joueurs connectés)");
        OrbitTycoon.msg(s, "<gray>Monnaies : <#E0B864>credit<gray>, <#62CFF4>flux<gray>, <#C77DFF>plasma<gray> (quantité = montant, marche hors ligne)");
        OrbitTycoon.msg(s, "<gray>Objets : <white>cle-1 … cle-5<gray>, <white>gen-<id><gray>, <white>ressource-<id><gray>, <white>collecteur<gray>, <white>coffre-collecteur");
        OrbitTycoon.msg(s, "<gray>Uniques : <white>outil<gray>, <white>navigateur<gray>, <white>guide <dark_gray>(ids des générateurs : /tycoon gen liste)");
    }

    List<String> completer(String[] args) {
        List<String> res = new ArrayList<>();
        if (args.length == 1) {
            res.add("*");
            for (Player p : Bukkit.getOnlinePlayers()) res.add(p.getName());
        } else if (args.length == 2) res.addAll(tousLesIds());
        else if (args.length == 3) res.addAll(Monnaies.Monnaie.parse(args[1]) != null ? List.of("100", "1000", "10000") : List.of("1", "16", "64"));
        String debut = args[args.length - 1].toLowerCase(Locale.ROOT);
        res.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(debut));
        return res;
    }
}
