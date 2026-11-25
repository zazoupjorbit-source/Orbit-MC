package fr.orbit.arene;

import fr.orbit.arene.charge.PartieCharge;
import fr.orbit.arene.nebuleuse.PartieComete;
import fr.orbit.arene.nebuleuse.PartieGravite;
import fr.orbit.arene.palet.PartiePalet;
import fr.orbit.arene.turbo.PartieTurbo;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Files d'attente (une par mode) et places des parties. Le réseau a peu de joueurs : on attend quelques secondes
 * les autres, puis la partie part avec les humains présents et des bots pour compléter.
 */
public final class Attente {

    private static final class File {
        final Jeu.Mode mode;
        final List<UUID> joueurs = new ArrayList<>();
        int reste; // secondes

        File(Jeu.Mode mode) {
            this.mode = mode;
        }
    }

    private final OrbitArene pl;
    private final Map<String, File> files = new LinkedHashMap<>();
    private final Map<Jeu, Partie[]> places = new HashMap<>();
    private final Map<UUID, Jeu.Mode> dernier = new HashMap<>();

    Attente(OrbitArene pl) {
        this.pl = pl;
        for (Jeu j : Jeu.values()) {
            places.put(j, new Partie[Math.max(1, pl.getConfig().getInt(j.id + ".places", 3))]);
        }
    }

    public List<Partie> parties() {
        List<Partie> l = new ArrayList<>();
        for (Partie[] arr : places.values()) {
            for (Partie p : arr) {
                if (p != null) {
                    l.add(p);
                }
            }
        }
        return l;
    }

    /** Humains au plus dans une partie de ce mode. */
    public int maxHumains(Jeu.Mode m) {
        return switch (m.jeu()) {
            case NEBULEUSE -> pl.getConfig().getInt("nebuleuse." + m.id() + ".joueurs-max", m.id().equals("comete") ? 6 : 8);
            case CHARGE -> Math.min(4, pl.getConfig().getInt("charge.joueurs-max", 4));
            case PALET -> m.id().equals("bot") ? 1 : 2;
            case TURBO -> m.id().equals("2v2") ? 4 : 2;
        };
    }

    private int delai(Jeu.Mode m) {
        if (m.jeu() == Jeu.PALET) {
            return m.id().equals("bot") ? 0 : pl.getConfig().getInt("palet.attente-duel", 20);
        }
        return pl.getConfig().getInt("attente.secondes", 12);
    }

    public Jeu.Mode dernier(Player p) {
        return dernier.get(p.getUniqueId());
    }

    public Jeu.Mode enFile(Player p) {
        for (File f : files.values()) {
            if (f.joueurs.contains(p.getUniqueId())) {
                return f.mode;
            }
        }
        return null;
    }

    public void rejoindre(Player p, Jeu.Mode m) {
        if (pl.partie(p) != null) {
            Txt.send(p, "<#E0484A>Tu es déjà en partie.");
            return;
        }
        quitterFile(p, false);
        dernier.put(p.getUniqueId(), m);
        File f = files.computeIfAbsent(m.cle(), k -> new File(m));
        if (f.joueurs.isEmpty()) {
            f.reste = delai(m);
        }
        f.joueurs.add(p.getUniqueId());
        pl.pack().envoyerSiBesoin(p);
        if (f.joueurs.size() >= maxHumains(m) || f.reste <= 0) {
            lancer(f);
            return;
        }
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, 1.4f);
        Txt.send(p, m.jeu().grad("<b>" + m.jeu().nom + "</b>") + " · " + m.nom() + " : en attente d'autres joueurs (<b>" + f.reste
                + " s</b>), des bots complètent ensuite. <#9AA4B2>" + m.aide());
        p.sendMessage(Txt.mm("  ").append(Txt.button("Quitter la file", "#E0484A", "/arene quitter", "Ne plus attendre")));
        for (UUID id : f.joueurs) {
            Player o = Bukkit.getPlayer(id);
            if (o != null && o != p) {
                Txt.send(o, "<#7CFF8A>" + p.getName() + "</#7CFF8A> rejoint la file (" + f.joueurs.size() + "/" + maxHumains(m) + ").");
            }
        }
    }

    public boolean quitterFile(Player p, boolean dire) {
        for (File f : files.values()) {
            if (f.joueurs.remove(p.getUniqueId())) {
                if (dire) {
                    Txt.send(p, "<#9AA4B2>Tu as quitté la file.");
                }
                return true;
            }
        }
        return false;
    }

    /** Chaque seconde : compte à rebours des files, départ des parties. */
    void seconde() {
        for (File f : new ArrayList<>(files.values())) {
            f.joueurs.removeIf(id -> {
                Player p = Bukkit.getPlayer(id);
                return p == null || pl.partie(p) != null || !p.getWorld().getName().equals(pl.salle().nomMonde());
            });
            if (f.joueurs.isEmpty()) {
                continue;
            }
            f.reste--;
            if (f.reste <= 0) {
                lancer(f);
                continue;
            }
            for (UUID id : f.joueurs) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) {
                    Txt.bar(p, f.mode.jeu().grad("<b>" + f.mode.jeu().nom + "</b>") + " <#5A6270>·</#5A6270> <#E6EDF3>"
                            + f.joueurs.size() + "/" + maxHumains(f.mode) + " joueurs</#E6EDF3> <#5A6270>·</#5A6270> départ dans <b>" + f.reste + " s</b>");
                }
            }
        }
    }

    private void lancer(File f) {
        Partie[] arr = places.get(f.mode.jeu());
        int slot = -1;
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] == null) {
                slot = i;
                break;
            }
        }
        if (slot < 0) {
            f.reste = 5;
            for (UUID id : f.joueurs) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) {
                    Txt.bar(p, "<#E0B864>Toutes les arènes de " + f.mode.jeu().nom + " sont occupées : départ dès qu'une se libère.");
                }
            }
            return;
        }
        int max = maxHumains(f.mode);
        List<Player> joueurs = new ArrayList<>();
        for (UUID id : new ArrayList<>(f.joueurs)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && joueurs.size() < max) {
                joueurs.add(p);
                f.joueurs.remove(id);
            }
        }
        if (joueurs.isEmpty()) {
            return;
        }
        if (!f.joueurs.isEmpty()) {
            f.reste = delai(f.mode);
        }
        Partie partie = creer(f.mode, slot);
        arr[slot] = partie;
        try {
            partie.lancer(joueurs);
        } catch (RuntimeException e) {
            pl.getLogger().severe(f.mode.cle() + " : départ impossible : " + e);
            e.printStackTrace();
            partie.arreter();
        }
    }

    private Partie creer(Jeu.Mode m, int slot) {
        return switch (m.jeu()) {
            case NEBULEUSE -> m.id().equals("comete") ? new PartieComete(pl, m, slot) : new PartieGravite(pl, m, slot);
            case CHARGE -> new PartieCharge(pl, m, slot);
            case PALET -> new PartiePalet(pl, m, slot);
            case TURBO -> new PartieTurbo(pl, m, slot);
        };
    }

    void liberer(Partie p) {
        Partie[] arr = places.get(p.mode.jeu());
        if (arr != null && p.slot < arr.length && arr[p.slot] == p) {
            arr[p.slot] = null;
        }
    }

    void toutArreter() {
        for (Partie p : parties()) {
            p.arreter();
        }
        files.clear();
    }
}
