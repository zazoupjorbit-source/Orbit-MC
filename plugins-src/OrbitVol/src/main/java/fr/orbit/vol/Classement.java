package fr.orbit.vol;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Classements (tous les joueurs, connectés ou non) : revenu par seconde, vols réussis, Ascension.
 * Recalculés chaque minute, affichés sur 3 panneaux au bout ouest de l'arène et dans le menu.
 */
public final class Classement {

    public record Ligne(String pseudo, double revenu, long vols, int ascension) {
    }

    final Jeu jeu;
    List<Ligne> parRevenu = List.of(), parVols = List.of(), parAscension = List.of();
    final List<TextDisplay> panneaux = new ArrayList<>();

    Classement(Jeu jeu) {
        this.jeu = jeu;
    }

    void calculer() {
        Map<UUID, Ligne> tous = new HashMap<>();
        File[] fichiers = jeu.dossierJoueurs.listFiles((d, n) -> n.endsWith(".yml"));
        if (fichiers != null) for (File f : fichiers) {
            try {
                YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
                UUID u = UUID.fromString(f.getName().replace(".yml", ""));
                tous.put(u, new Ligne(y.getString("pseudo", "?"), y.getDouble("revenu", 0), y.getLong("vols-reussis", 0), y.getInt("ascension", 0)));
            } catch (RuntimeException ignore) {
            }
        }
        for (Profil p : jeu.profils.values()) tous.put(p.uuid, new Ligne(p.pseudo, p.revenu, p.volsReussis, p.ascension));
        List<Ligne> l = new ArrayList<>(tous.values());
        parRevenu = trier(l, Comparator.comparingDouble(Ligne::revenu));
        parVols = trier(l, Comparator.comparingLong(Ligne::vols));
        parAscension = trier(l, Comparator.<Ligne>comparingInt(Ligne::ascension).thenComparingDouble(Ligne::revenu));
        afficher();
    }

    private static List<Ligne> trier(List<Ligne> l, Comparator<Ligne> c) {
        List<Ligne> r = new ArrayList<>(l);
        r.sort(c.reversed());
        return r.subList(0, Math.min(10, r.size()));
    }

    static final String[] MEDAILLES = {"<#FFE08A>①", "<#D8E0EA>②", "<#E0A070>③"};

    static String rang(int i) {
        return i < 3 ? MEDAILLES[i] : "<#8A96A8>" + (i + 1) + ".";
    }

    private String texte(String titre, List<Ligne> l, java.util.function.Function<Ligne, String> valeur) {
        StringBuilder sb = new StringBuilder(titre).append("\n");
        if (l.isEmpty()) sb.append("<gray>Personne pour l'instant…");
        for (int i = 0; i < l.size(); i++)
            sb.append("\n").append(rang(i)).append(" <#E6EDF3>").append(l.get(i).pseudo()).append("  ").append(valeur.apply(l.get(i)));
        return sb.toString();
    }

    void afficher() {
        World w = jeu.w;
        String[] textes = {
                texte("<#7CFC9A><b>LES PLUS RICHES</b> <gray>(revenu/s)", parRevenu, x -> "<#7CFC9A>+" + Txt.n(x.revenu()) + "/s"),
                texte("<#FF4D6D><b>LES PLUS GRANDS VOLEURS</b>", parVols, x -> "<#FF4D6D>" + x.vols() + " vols"),
                texte("<#E0B864><b>✦ ASCENSION</b>", parAscension, x -> "<#E0B864>niv. " + x.ascension())
        };
        double[][] pos = {{-76.5, Arene.Y + 3.5, -18.5}, {-76.5, Arene.Y + 3.5, 18.5}, {-76.5, Arene.Y + 9.0, 0.5}};
        if (panneaux.isEmpty()) {
            for (double[] p : pos) {
                Location l = new Location(w, p[0], p[1], p[2], -90, 0);
                panneaux.add(w.spawn(l, TextDisplay.class, t -> {
                    t.setBillboard(Display.Billboard.FIXED);
                    t.setRotation(-90, 0);
                    t.setPersistent(false);
                    t.setShadowed(true);
                    t.setLineWidth(260);
                    t.setBackgroundColor(org.bukkit.Color.fromARGB(200, 11, 18, 32));
                    t.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(), new org.joml.Quaternionf(),
                            new org.joml.Vector3f(1.6f, 1.6f, 1.6f), new org.joml.Quaternionf()));
                }));
            }
        }
        for (int i = 0; i < panneaux.size(); i++) panneaux.get(i).text(Txt.mm(textes[i]));
    }

    void retirer() {
        panneaux.forEach(org.bukkit.entity.Entity::remove);
        panneaux.clear();
    }
}
