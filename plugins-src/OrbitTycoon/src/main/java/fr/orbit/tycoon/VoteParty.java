package fr.orbit.tycoon;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Vote Party : barre permanente en haut de l'écran « Vote Party 0/100 ». Chaque vote ajoute 1 ;
 * à l'objectif, tous les joueurs connectés reçoivent les récompenses (commandes console de la config)
 * et le compteur repart à 0.
 * Les votes arrivent par « tycoon voteparty ajouter <n> » (console ou admin). Quand un plugin de réception
 * des votes (type Votifier) sera installé, il suffira qu'il lance cette commande à chaque vote.
 * Le compteur est gardé dans voteparty.yml (survit aux redémarrages).
 */
final class VoteParty implements Listener {
    private final OrbitTycoon plugin;
    private final File fichier;
    private BossBar barre;
    private int votes;

    VoteParty(OrbitTycoon plugin) {
        this.plugin = plugin;
        this.fichier = new File(plugin.getDataFolder(), "voteparty.yml");
    }

    void demarrer() {
        votes = Math.max(0, YamlConfiguration.loadConfiguration(fichier).getInt("votes", 0));
        if (!actif()) { cacher(); return; }
        if (barre == null) barre = BossBar.bossBar(titre(), progression(), BossBar.Color.PURPLE, BossBar.Overlay.NOTCHED_10);
        else maj();
        for (Player p : Bukkit.getOnlinePlayers()) p.showBossBar(barre);
    }

    void arreter() { cacher(); }

    private void cacher() {
        if (barre == null) return;
        for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(barre);
        barre = null;
    }

    @EventHandler
    public void connexion(PlayerJoinEvent e) {
        if (barre != null) e.getPlayer().showBossBar(barre);
    }

    /** Ajoute des votes ; déclenche la party (une ou plusieurs fois) si l'objectif est atteint. */
    void ajouter(int n) {
        if (n <= 0) return;
        votes += n;
        int objectif = objectif();
        while (votes >= objectif) {
            votes -= objectif;
            party();
        }
        sauver();
        maj();
    }

    void definir(int n) {
        votes = Math.max(0, Math.min(n, objectif() - 1));
        sauver();
        maj();
    }

    int votes() { return votes; }

    int objectif() { return Math.max(1, plugin.getConfig().getInt("voteparty.objectif", 100)); }

    private boolean actif() { return plugin.getConfig().getBoolean("voteparty.actif", true); }

    private void party() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            OrbitTycoon.msg(p, "<#C77DFF><bold>VOTE PARTY !</bold> <gray>" + objectif()
                    + " votes atteints, merci à tous ! Récompenses distribuées aux joueurs connectés.");
            p.playSound(p.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }
        List<String> cmds = plugin.getConfig().getStringList("voteparty.recompenses");
        for (Player p : Bukkit.getOnlinePlayers()) {
            for (String c : cmds) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c.replace("{joueur}", p.getName()));
        }
        plugin.getLogger().info("Vote Party déclenchée (" + Bukkit.getOnlinePlayers().size() + " joueurs récompensés).");
    }

    private void maj() {
        if (barre == null) return;
        barre.name(titre());
        barre.progress(progression());
    }

    private net.kyori.adventure.text.Component titre() {
        return MiniMessage.miniMessage().deserialize("<#C77DFF><bold>Vote Party</bold> <white>" + votes + "<gray>/" + objectif()
                + " <dark_gray>• <#E0B864>/vote");
    }

    private float progression() { return Math.max(0f, Math.min(1f, votes / (float) objectif())); }

    private void sauver() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("votes", votes);
        try { y.save(fichier); }
        catch (IOException e) { plugin.getLogger().warning("Vote Party : impossible d'enregistrer voteparty.yml (" + e.getMessage() + ")"); }
    }
}
