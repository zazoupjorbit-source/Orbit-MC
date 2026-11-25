package fr.orbit.casino.jeux;

import fr.orbit.casino.Holo;
import fr.orbit.casino.OrbitCasino;
import fr.orbit.casino.Txt;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;

import java.util.Locale;

/**
 * Jeux du Salon des Étoiles (aile est du casino) et jukebox du bar : construction de l'aile, clics, tâches,
 * commande /cj (boutons du chat). Tout se joue en Orbit Coins de jeu, jamais achetables.
 */
public final class Jeux implements Listener {

    private final OrbitCasino plugin;
    private final World world;
    private final Crash crash;
    private final Mines mines;
    private final Plinko plinko;
    private final Roue roue;
    private final Billard billard;
    private final Jukebox jukebox;

    public Jeux(OrbitCasino plugin, World world) {
        this.plugin = plugin;
        this.world = world;
        crash = new Crash(plugin, world);
        mines = new Mines(plugin, world);
        plinko = new Plinko(plugin, world);
        roue = new Roue(plugin, world);
        billard = new Billard(plugin, world);
        jukebox = new Jukebox(plugin, world);
    }

    public void start() {
        for (int cx = 1; cx <= 3; cx++) {
            for (int cz = -2; cz <= 1; cz++) {
                world.getChunkAt(cx, cz).addPluginChunkTicket(plugin);
            }
        }
        if (!Salon.built(world)) {
            plugin.getLogger().info("Salon des Étoiles construit (" + Salon.build(world) + " blocs).");
        } else if (Salon.jukebox(world) > 0) {
            plugin.getLogger().info("Jukebox posé sur le comptoir du bar.");
        }
        int fixed = Salon.repairBillard(world);
        if (fixed > 0) {
            plugin.getLogger().info("Table de billard réparée (" + fixed + " bloc" + (fixed > 1 ? "s" : "") + ").");
        }
        Bukkit.getScheduler().runTaskTimer(plugin, this::second, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::fast, 1L, 1L);
        plugin.getLogger().info(String.format(Locale.ROOT, "Salon des Étoiles ouvert : Crash %.0f %%, Mines %.0f %%, Plinko %.1f %%, "
                + "roue %.0f %% redistribués ; billard et jukebox.", crash.rtp() * 100, mines.rtp() * 100, Plinko.rtp() * 100, Roue.rtp() * 100));
    }

    /** Après chaque Holo.clear du casino : PNJ, textes, picots, roue. */
    public void spawn() {
        crash.spawn();
        mines.spawn();
        plinko.spawn();
        roue.spawn();
        billard.spawn();
        jukebox.spawn();
        Holo.text(new Location(world, Salon.X1 - 0.6, Salon.Y + 5.6, Salon.CZ, 90f, 0f),
                "<gradient:#62CFF4:#E0B864><b>✦ SALON DES ÉTOILES ✦</b></gradient>\n<#9AA4B2>Crash · Mines · Plinko · Roue · Billard", 1.2f, true);
    }

    /** /casino generer : l'aile est reconstruite après la grande salle (qui referme la porte). */
    public int rebuild() {
        return Salon.build(world);
    }

    private void second() {
        try {
            crash.tick();
            roue.tick();
            billard.tick();
            jukebox.tick();
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Salon : " + e);
        }
    }

    private void fast() {
        try {
            crash.fastTick();
            plinko.fastTick();
            billard.fastTick();
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Salon (tick) : " + e);
        }
    }

    /** Clic droit sur un bloc du casino : vrai si un jeu du salon s'en occupe. */
    public boolean click(Player p, Block b) {
        if (!b.getWorld().equals(world)) {
            return false;
        }
        if (Salon.isJukebox(b)) {
            jukebox.open(p);
        } else if (Salon.isPlinko(b)) {
            plinko.click(p);
        } else if (Salon.isBillard(b)) {
            billard.click(p);
        } else if (Salon.isBorne(b, Salon.CRASH_X)) {
            crash.open(p);
        } else if (Salon.isBorne(b, Salon.MINES_X)) {
            mines.open(p);
        } else if (Salon.isRoue(b)) {
            roue.open(p);
        } else {
            return false;
        }
        return true;
    }

    public boolean clickEntity(Player p, Entity e) {
        if (roue.isNpc(e.getUniqueId())) {
            roue.open(p);
            return true;
        }
        return false;
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        if (event.getPlayer().getWorld().equals(world)) {
            billard.sneak(event.getPlayer(), event.isSneaking());
        }
    }

    public void quit(Player p) {
        mines.quit(p.getUniqueId());
        billard.leave(p.getUniqueId(), "déconnecté");
    }

    public void leftWorld(Player p) {
        billard.leave(p.getUniqueId(), "a quitté la salle");
    }

    public void shutdown() {
        crash.shutdown();
        mines.shutdown();
        plinko.shutdown();
        roue.shutdown();
        billard.shutdown();
        jukebox.shutdown();
    }

    /** /cj &lt;jeu&gt; &lt;action&gt; : boutons du chat. */
    public void command(Player p, String[] args) {
        String game = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        String action = args.length < 2 ? "" : args[1].toLowerCase(Locale.ROOT);
        if (!p.getWorld().equals(world)) {
            Txt.send(p, "<#9AA4B2>Ces jeux se jouent dans le casino de La Station.");
            return;
        }
        switch (game) {
            case "crash" -> crash.command(p, action);
            case "mines" -> mines.open(p);
            case "roue" -> roue.open(p);
            case "jukebox" -> jukebox.open(p);
            case "billard" -> {
                switch (action) {
                    case "rejoindre" -> billard.join(p);
                    case "quitter" -> billard.leave(p.getUniqueId(), "a quitté la partie");
                    default -> billard.click(p);
                }
            }
            default -> Txt.send(p, "/cj <crash|mines|roue|jukebox|billard> …");
        }
    }
}
