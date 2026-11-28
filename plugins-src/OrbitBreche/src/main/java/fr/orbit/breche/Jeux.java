package fr.orbit.breche;

import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Les parties en cours : une au plus par carte. Retient la dernière carte de chaque joueur pour « Rejouer ». */
final class Jeux {

    private final OrbitBreche pl;
    final Map<String, Carte> cartes = new LinkedHashMap<>();
    private final Map<String, Partie> parties = new HashMap<>();
    private final Map<UUID, String[]> derniere = new HashMap<>();

    Jeux(OrbitBreche pl, World w) {
        this.pl = pl;
        for (Plan p : Plan.toutes()) {
            cartes.put(p.id, new Carte(pl, p, w));
        }
    }

    void construire(boolean forcer) {
        for (Carte c : cartes.values()) {
            if (forcer || parties.get(c.plan.id) == null) {
                c.construire(forcer);
                c.poserLettres();
            }
        }
    }

    Partie partie(String carte) {
        return parties.get(carte);
    }

    Partie de(Player p) {
        for (Partie pa : parties.values()) {
            if (pa.contient(p)) {
                return pa;
            }
        }
        return null;
    }

    void jouer(Player p, String carte, int niveau, int parEquipe) {
        if (de(p) != null) {
            Txt.send(p, "Tu es déjà dans une partie. <#9AA4B2>/breche quitter</#9AA4B2> pour en sortir.");
            return;
        }
        if (carte == null || carte.equalsIgnoreCase("hasard")) {
            List<String> libres = new ArrayList<>();
            for (String id : cartes.keySet()) {
                if (parties.get(id) == null) {
                    libres.add(id);
                }
            }
            carte = libres.isEmpty() ? new ArrayList<>(cartes.keySet()).get((int) (Math.random() * cartes.size()))
                    : libres.get((int) (Math.random() * libres.size()));
        }
        Carte c = cartes.get(carte.toLowerCase());
        if (c == null) {
            Txt.send(p, "Cartes : <#62CFF4>helios</#62CFF4>, <#E0B864>vesta</#E0B864> ou <#C79BFF>hasard</#C79BFF>.");
            return;
        }
        derniere.put(p.getUniqueId(), new String[]{c.plan.id, String.valueOf(niveau), String.valueOf(parEquipe)});
        Partie pa = parties.get(c.plan.id);
        if (pa == null) {
            pa = new Partie(pl, c, p, parEquipe, niveau);
            parties.put(c.plan.id, pa);
            pl.getLogger().info(p.getName() + " lance une partie sur " + c.plan.nom + " (" + parEquipe + "v" + parEquipe + ", bots " + niveau + ").");
        }
        if (!pa.rejoindre(p)) {
            Txt.send(p, "<#E0484A>Impossible de rejoindre cette partie (complète ou terminée).");
            if (pa.tous.isEmpty() && pa.renforts.isEmpty()) {
                parties.remove(c.plan.id);
            }
        }
    }

    /** Partie d'essai entre bots (admin ou console) : même boucle de jeu, sans joueur, racontée dans la console. */
    String essai(String carte, int niveau, int parEquipe, int vitesse) {
        Carte c = cartes.get(carte == null ? "helios" : carte.toLowerCase());
        if (c == null) {
            return "Cartes : helios, vesta.";
        }
        if (parties.get(c.plan.id) != null) {
            return "Une partie est déjà en cours sur " + c.plan.nom + ".";
        }
        Partie pa = new Partie(pl, c, null, parEquipe, niveau);
        pa.essai = true;
        pa.vitesse = Math.max(1, Math.min(20, vitesse));
        c.garderCharge(true);
        parties.put(c.plan.id, pa);
        pa.lancer();
        return "Partie d'essai entre bots lancée sur " + c.plan.nom + " (" + pa.parEquipe + " contre " + pa.parEquipe + ", bots " + pa.niveau + ", vitesse × " + pa.vitesse + "). Suivi dans la console.";
    }

    void rejouer(Player p) {
        String[] d = derniere.get(p.getUniqueId());
        if (d == null) {
            pl.menus().ouvrirAccueil(p);
            return;
        }
        jouer(p, d[0], Integer.parseInt(d[1]), Integer.parseInt(d[2]));
    }

    void quitter(Player p, boolean teleporter) {
        Partie pa = de(p);
        if (pa != null) {
            pa.quitter(p, teleporter);
        }
    }

    void fini(Partie pa) {
        parties.values().remove(pa);
    }

    void tick() {
        for (Partie pa : new ArrayList<>(parties.values())) {
            try {
                for (int i = 0; i < pa.vitesse && parties.get(pa.plan.id) == pa; i++) {
                    pa.tick();
                }
            } catch (RuntimeException e) {
                pl.getLogger().severe("Erreur dans la partie " + pa.plan.nom + " : " + e);
                e.printStackTrace();
                pa.arreter("erreur interne");
            }
        }
    }

    void arreterTout(String raison) {
        for (Partie pa : new ArrayList<>(parties.values())) {
            pa.arreter(raison);
        }
        parties.clear();
    }

    String etat() {
        if (parties.isEmpty()) {
            return " aucune partie.";
        }
        StringBuilder sb = new StringBuilder();
        for (Partie pa : parties.values()) {
            sb.append("\n  ").append(pa.etat());
        }
        return sb.toString();
    }
}
