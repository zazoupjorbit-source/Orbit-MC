package fr.orbit.vol;

import fr.orbit.vol.Arene.Base;
import net.kyori.adventure.title.Title;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.time.Duration;

/**
 * Tutoriel guidé des 2 premières minutes : 5 étapes, une traînée de particules montre où aller,
 * chaque étape réussie rapporte des crédits. Étapes : acheter, encaisser, bouclier, menu, améliorer.
 */
final class Tuto {

    static final String[] ETAPES = {
            "Achète un Orbito sur le <#62CFF4>Tapis stellaire</#62CFF4> (clic dessus)",
            "Marche sur ta <#E0B864>plaque dorée</#E0B864> pour encaisser",
            "Active ton <#62CFF4>bouclier</#62CFF4> : clic droit sur la borne",
            "Ouvre le <#E0B864>menu</#E0B864> : clic droit avec l'étoile",
            "Améliore un de tes Orbitos : clique dessus"
    };
    static final String[] COURTS = {"Achète un Orbito", "Encaisse sur la plaque", "Active ton bouclier", "Ouvre le menu étoile", "Améliore un Orbito"};
    static final long[] PRIMES = {150, 200, 300, 400, 1_000};

    private Tuto() {
    }

    /** Montre l'étape en cours (titre à l'écran). */
    static void annoncer(Player p, Profil pr) {
        if (pr.tuto >= Profil.TUTO_FINI) return;
        p.showTitle(Title.title(Txt.mm("<#E0B864>Étape " + (pr.tuto + 1) + "/" + Profil.TUTO_FINI),
                Txt.mm("<#E6EDF3>" + ETAPES[pr.tuto]),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(3000), Duration.ofMillis(400))));
        Txt.send(p, "<#E0B864>Tuto " + (pr.tuto + 1) + "/" + Profil.TUTO_FINI + " :</#E0B864> " + ETAPES[pr.tuto]
                + " <#5A6270>(+" + Txt.n(PRIMES[pr.tuto]) + " ✦)");
    }

    /** L'action de l'étape « etape » vient d'être faite. */
    static void avancer(Jeu jeu, Player p, Profil pr, int etape) {
        if (pr == null || pr.tuto != etape) return;
        pr.credits += PRIMES[etape];
        pr.tuto++;
        Txt.sound(p, Sound.ENTITY_PLAYER_LEVELUP, 1.8f);
        Txt.bar(p, "<#7CFC9A><b>Étape réussie !</b> <#E6EDF3>+" + Txt.cr(PRIMES[etape]));
        if (pr.tuto >= Profil.TUTO_FINI) {
            pr.titres.add("recrue");
            jeu.hudJeu.banniere(p, "b_quete", "<#7CFC9A><b>TUTORIEL FINI !</b>", "Titre « Recrue d'ORBIT » débloqué — à toi de jouer !");
            Txt.send(p, "<#7CFC9A><b>Tutoriel fini !</b></#7CFC9A> Tu as encore <#62CFF4>" + Math.max(0, (Profil.PROTECTION_S - pr.tempsJeu) / 60)
                    + " min</#62CFF4> de protection de débutant : personne ne peut te voler. Profites-en pour remplir ta base !");
        } else {
            jeu.plugin.getServer().getScheduler().runTaskLater(jeu.plugin, () -> { if (p.isOnline()) annoncer(p, pr); }, 30);
        }
        jeu.hud(p);
    }

    /** Chaque seconde : traînée de particules vers le but de l'étape. */
    static void guider(Jeu jeu, Player p, Profil pr) {
        if (pr.tuto >= Profil.TUTO_FINI) return;
        Base b = jeu.baseDe.get(p.getUniqueId());
        Location but = switch (pr.tuto) {
            case 0 -> new Location(jeu.w, Arene.TAPIS_X0 + 8.5, Arene.Y + 1.2, 0.5);
            case 1 -> b == null ? null : new Location(jeu.w, b.plaque()[0] + 0.5, Arene.Y + 1.2, b.plaque()[2] + 0.5);
            case 2 -> b == null ? null : new Location(jeu.w, b.bouton()[0] + 0.5, Arene.Y + 1.6, b.bouton()[2] + 0.5);
            case 4 -> {
                Jeu.Spec[] tab = b == null ? null : jeu.poses.get(b.idx);
                Location l = null;
                if (tab != null) for (Jeu.Spec s : tab) if (s != null && s.etat == Jeu.Etat.POSE) { l = s.mob.getLocation().add(0, 1, 0); break; }
                yield l;
            }
            default -> null;
        };
        if (but == null) return;
        Location de = p.getLocation().add(0, 1.1, 0);
        Vector v = but.toVector().subtract(de.toVector());
        double d = v.length();
        if (d < 1.5) return;
        v.normalize();
        Particle.DustOptions or = new Particle.DustOptions(Color.fromRGB(0xFFE08A), 1.1f);
        for (double x = 1; x < Math.min(d, 12); x += 0.6) p.spawnParticle(Particle.DUST, de.clone().add(v.clone().multiply(x)), 1, 0, 0, 0, 0, or);
        p.spawnParticle(Particle.END_ROD, but, 4, 0.2, 0.4, 0.2, 0.01);
    }
}
