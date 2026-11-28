package fr.orbit.survie;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

/**
 * Monde ressources : un monde normal à part, pour miner et récolter sans abîmer le monde de survie (qui, lui,
 * n'est jamais touché). Il est remis à zéro chaque mois (nouvelle graine) : on n'y pose ni base ni point de
 * retour. On y va depuis l'île (/ressources ou le Prospecteur), téléportation aléatoire ; /spawn pour revenir.
 */
public final class Ressources implements Listener, TabExecutor {

    private final OrbitSurvie plugin;
    private final Voyages voyages;
    private String nom = "ressources";

    public Ressources(OrbitSurvie plugin, Voyages voyages) {
        this.plugin = plugin;
        this.voyages = voyages;
    }

    private boolean actif() {
        return plugin.getConfig().getBoolean("ressources.actif", true);
    }

    public World monde() {
        return Bukkit.getWorld(nom);
    }

    private File marque() {
        return new File(plugin.getDataFolder(), "ressources-mois.txt");
    }

    private void marquer() {
        try {
            plugin.getDataFolder().mkdirs();
            Files.writeString(marque().toPath(), YearMonth.now().toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().warning("Marque ressources-mois.txt non écrite : " + e.getMessage());
        }
    }

    /** Au démarrage : charge (ou crée) le monde, et le remet à zéro si le mois a changé. */
    public void demarrer() {
        if (!actif()) {
            return;
        }
        nom = plugin.getConfig().getString("ressources.monde", "ressources");
        boolean existait = marque().isFile();
        String mois = "";
        try {
            mois = existait ? Files.readString(marque().toPath(), StandardCharsets.UTF_8).trim() : "";
        } catch (IOException ignored) {
        }
        World cree = creer();
        if (cree == null) {
            plugin.getLogger().severe("Monde ressources impossible à créer.");
            return;
        }
        File d = dossier(cree);
        plugin.getLogger().info("Monde ressources chargé (dossier : " + (d == null ? "introuvable, remise à zéro impossible" : d.getPath()) + ").");
        if (!existait) {
            marquer();
        } else if (!mois.equals(YearMonth.now().toString()) && plugin.getConfig().getBoolean("ressources.remise-a-zero-mensuelle", true)) {
            plugin.getLogger().info("Nouveau mois : remise à zéro du monde ressources.");
            reinitialiser(Bukkit.getConsoleSender());
        }
    }

    private World creer() {
        World w = Bukkit.getWorld(nom);
        if (w == null) {
            w = new WorldCreator(nom).environment(World.Environment.NORMAL).seed(ThreadLocalRandom.current().nextLong()).createWorld();
        }
        if (w != null) {
            w.getWorldBorder().setCenter(0.5, 0.5);
            w.getWorldBorder().setSize(plugin.getConfig().getInt("ressources.bordure", 4000));
        }
        return w;
    }

    /** Dossier du monde sur le disque, seulement s'il porte bien son nom et n'est pas le monde principal. */
    private File dossier(World w) {
        File principal = Bukkit.getWorlds().get(0).getWorldFolder();
        File d = w.getWorldFolder();
        if (!d.getName().equals(nom)) {
            d = new File(principal, "dimensions/minecraft/" + nom);
        }
        try {
            if (d.isDirectory() && d.getName().equals(nom) && !d.getCanonicalFile().equals(principal.getCanonicalFile())
                    && !principal.getCanonicalPath().startsWith(d.getCanonicalPath())) {
                return d;
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    /** Vide le monde ressources et en refait un neuf (nouvelle graine). Les joueurs qui y sont rentrent sur l'île. */
    public void reinitialiser(CommandSender s) {
        World w = monde();
        if (w == null) {
            creer();
            marquer();
            return;
        }
        Location spawn = plugin.ile().spawn();
        for (Player p : List.copyOf(w.getPlayers())) {
            if (spawn != null) {
                p.teleport(spawn);
            }
            Msg.send(p, "Le monde ressources est remis à zéro : retour au port.");
        }
        File d = dossier(w);
        if (d == null) {
            Msg.send(s, "<red>Dossier du monde ressources introuvable ou douteux (" + w.getWorldFolder().getPath() + ") : rien n'est supprimé.");
            return;
        }
        if (!Bukkit.unloadWorld(w, false)) {
            Msg.send(s, "<red>Le monde ressources ne se décharge pas (un joueur y est encore ?) : rien n'est supprimé.");
            return;
        }
        try (Stream<Path> fichiers = Files.walk(d.toPath())) {
            fichiers.sorted(Comparator.reverseOrder()).forEach(f -> f.toFile().delete());
        } catch (IOException e) {
            plugin.getLogger().warning("Suppression du monde ressources incomplète : " + e.getMessage());
        }
        if (creer() == null) {
            Msg.send(s, "<red>Le monde ressources n'a pas pu être recréé.");
            return;
        }
        marquer();
        Bukkit.broadcast(Msg.msg("Le " + Msg.OR + "monde ressources<gray> est tout neuf ! <white>/ressources<gray> depuis le port."));
        plugin.getLogger().info("Monde ressources remis à zéro (" + d.getPath() + ").");
    }

    // ------------------------------------------------------------------ Y aller

    public void aller(Player p) {
        World w = actif() ? monde() : null;
        if (w == null) {
            Msg.send(p, "<red>Le monde ressources n'est pas disponible.");
            return;
        }
        if (p.getWorld().equals(w)) {
            Msg.send(p, "Tu es déjà dans le monde ressources. <white>/spawn<gray> pour revenir au port.");
            return;
        }
        if (!p.getWorld().getName().equals(plugin.ile().worldName())) {
            Msg.send(p, "On part pour le monde ressources depuis le port : <white>/spawn<gray>, puis <white>/ressources<gray> ou le Prospecteur.");
            return;
        }
        Msg.send(p, Msg.OR + "Monde ressources<gray> : mine et récolte ici, il est remis à zéro chaque début de mois. "
                + "<red>N'y laisse rien<gray> : ni base, ni coffre.");
        voyages.rtp(p, w, plugin.getConfig().getInt("ressources.rtp-min", 50), plugin.getConfig().getInt("ressources.rtp-max", 1800));
    }

    @EventHandler
    public void onPortail(PlayerPortalEvent e) {
        World w = monde();
        if (w != null && e.getFrom().getWorld().equals(w)) {
            e.setCancelled(true);
            e.getPlayer().sendActionBar(Msg.raw("<red>Pas de portail dans le monde ressources : le Nether est dans la survie."));
        }
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (a.length >= 1 && a[0].equalsIgnoreCase("reset") && s.hasPermission("survie.admin")) {
            if (a.length < 2 || !a[1].equalsIgnoreCase("oui")) {
                Msg.send(s, "<red>Tout le monde ressources sera effacé. <white>/ressources reset oui<red> pour confirmer.");
            } else {
                reinitialiser(s);
            }
            return true;
        }
        if (s instanceof Player p) {
            aller(p);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        return a.length == 1 && s.hasPermission("survie.admin") && "reset".startsWith(a[0].toLowerCase()) ? List.of("reset") : List.of();
    }
}
