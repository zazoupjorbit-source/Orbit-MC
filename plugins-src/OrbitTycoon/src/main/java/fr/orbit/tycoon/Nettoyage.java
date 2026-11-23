package fr.orbit.tycoon;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Clear lag : toutes les N minutes (15 par défaut), retire les objets au sol, les flèches plantées
 * et les orbes d'XP de tous les mondes. Avant le passage : messages dans le chat + barre en haut de l'écran
 * (compte à rebours). Les ressources produites par les générateurs sont épargnées (c'est le revenu des joueurs,
 * déjà plafonné par generateurs.max-au-sol).
 */
final class Nettoyage {
    private final OrbitTycoon plugin;
    private BukkitTask tache;
    private int restant;          // secondes avant le prochain nettoyage
    private int intervalle;       // en secondes
    private int barreSecondes;    // la barre du haut apparaît ce nombre de secondes avant le passage
    private BossBar barre;
    private final Set<Integer> avertissements = new HashSet<>();

    Nettoyage(OrbitTycoon plugin) { this.plugin = plugin; }

    void demarrer() {
        arreter();
        var c = plugin.getConfig();
        if (!c.getBoolean("nettoyage.actif", true)) return;
        intervalle = Math.max(1, c.getInt("nettoyage.intervalle-minutes", 15)) * 60;
        avertissements.clear();
        for (int s : c.getIntegerList("nettoyage.avertissements-secondes")) if (s > 0 && s < intervalle) avertissements.add(s);
        barreSecondes = Math.min(intervalle - 1, Math.max(0, c.getInt("nettoyage.barre-secondes", 60)));
        restant = intervalle;
        tache = Bukkit.getScheduler().runTaskTimer(plugin, this::seconde, 20L, 20L);
    }

    void arreter() {
        if (tache != null) { tache.cancel(); tache = null; }
        cacherBarre();
    }

    private void seconde() {
        restant--;
        if (avertissements.contains(restant)) {
            annoncer("<yellow>Nettoyage des objets au sol dans <white>" + duree(restant)
                    + "<yellow>. Les ressources des générateurs ne sont pas touchées.");
        }
        if (restant > 0 && restant <= barreSecondes) afficherBarre();
        if (restant <= 0) {
            cacherBarre();
            int n = nettoyer();
            annoncer("<green>Nettoyage terminé : <white>" + n + "</white> entité" + (n > 1 ? "s" : "") + " retirée" + (n > 1 ? "s" : "") + ".");
            restant = intervalle;
        }
    }

    /** Retire les entités inutiles ; renvoie le nombre retiré. Thread principal uniquement. */
    int nettoyer() {
        boolean garderProduits = plugin.getConfig().getBoolean("nettoyage.garder-ressources-generateurs", true);
        int n = 0;
        for (World w : Bukkit.getWorlds()) {
            List<Entity> liste = w.getEntities();
            for (Entity e : liste) {
                if (!e.isValid()) continue;
                boolean retirer;
                if (e instanceof Item it) {
                    retirer = !(garderProduits && plugin.generateurs() != null
                            && plugin.generateurs().typeProduit(it.getItemStack()) != null);
                } else if (e instanceof AbstractArrow a) {
                    retirer = a.isInBlock();
                } else {
                    retirer = e instanceof ExperienceOrb;
                }
                if (retirer) { e.remove(); n++; }
            }
        }
        plugin.getLogger().info("Nettoyage : " + n + " entités retirées.");
        return n;
    }

    /** Barre en haut de l'écran : compte à rebours qui se vide jusqu'au passage (montrée aussi aux joueurs qui arrivent). */
    private void afficherBarre() {
        var titre = MiniMessage.miniMessage().deserialize("<#E0B864><bold>Clear lag</bold> <gray>dans <white>" + duree(restant)
                + " <dark_gray>• <gray>ramasse tes objets au sol !");
        float p = Math.max(0f, Math.min(1f, restant / (float) barreSecondes));
        BossBar.Color couleur = restant <= 10 ? BossBar.Color.RED : BossBar.Color.YELLOW;
        if (barre == null) barre = BossBar.bossBar(titre, p, couleur, BossBar.Overlay.PROGRESS);
        else { barre.name(titre); barre.progress(p); barre.color(couleur); }
        for (var j : Bukkit.getOnlinePlayers()) j.showBossBar(barre);
    }

    private void cacherBarre() {
        if (barre == null) return;
        for (var j : Bukkit.getOnlinePlayers()) j.hideBossBar(barre);
        barre = null;
    }

    /** Secondes avant le prochain passage (-1 si désactivé). */
    int restant() { return tache == null ? -1 : restant; }

    private static void annoncer(String mm) {
        for (var p : Bukkit.getOnlinePlayers()) OrbitTycoon.msg(p, mm);
    }

    private static String duree(int s) {
        if (s == 60) return "1 minute";
        if (s > 60) return (s / 60) + " min " + (s % 60 < 10 ? "0" : "") + (s % 60) + " s";
        return s + " seconde" + (s > 1 ? "s" : "");
    }
}
