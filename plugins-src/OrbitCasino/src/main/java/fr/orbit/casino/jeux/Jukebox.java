package fr.orbit.casino.jeux;

import fr.orbit.casino.Holo;
import fr.orbit.casino.Menu;
import fr.orbit.casino.OrbitCasino;
import fr.orbit.casino.Txt;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Jukebox du bar : les joueurs ajoutent des morceaux à une file (2 chacun au plus), votent pour faire monter un morceau
 * et votent pour passer celui qui joue (la moitié des joueurs du casino suffit). Seulement des musiques autorisées :
 * les disques de Minecraft (vanilla) ou des morceaux libres de droits ajoutés au pack (config « jukebox.morceaux »).
 */
public final class Jukebox {

    private record Track(String id, String sound, String title, String author, int seconds, Material icon) {
    }

    private static final class Entry {
        final Track track;
        final UUID by;
        final String byName;
        final long added = System.nanoTime();
        final Set<UUID> votes = new HashSet<>();

        Entry(Track track, UUID by, String byName) {
            this.track = track;
            this.by = by;
            this.byName = byName;
        }
    }

    private final OrbitCasino plugin;
    private final World world;
    private final List<Track> tracks = new ArrayList<>();
    private final List<Entry> queue = new ArrayList<>();
    private final Set<UUID> skip = new HashSet<>();
    private Entry playing;
    private int left;
    private TextDisplay sign;

    public Jukebox(OrbitCasino plugin, World world) {
        this.plugin = plugin;
        this.world = world;
        for (Map<?, ?> m : plugin.getConfig().getMapList("jukebox.morceaux")) {
            try {
                String id = String.valueOf(m.get("id"));
                String sound = String.valueOf(m.get("son"));
                Object icon = m.get("icone");
                Material mat = Material.matchMaterial(icon == null ? "music_disc_" + id : String.valueOf(icon));
                tracks.add(new Track(id, sound, String.valueOf(m.get("titre")), String.valueOf(m.get("auteur")),
                        Integer.parseInt(String.valueOf(m.get("duree"))), mat == null ? Material.MUSIC_DISC_CAT : mat));
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Morceau du jukebox ignoré : " + m + " (" + e.getMessage() + ")");
            }
        }
    }

    private Location at() {
        return new Location(world, Salon.JB_X + 0.5, Salon.JB_Y + 0.5, Salon.JB_Z + 0.5);
    }

    public void spawn() {
        sign = Holo.text(new Location(world, Salon.JB_X + 0.5, Salon.JB_Y + 1.6, Salon.JB_Z + 0.5), "", 0.75f, false);
        render();
    }

    private static String time(int s) {
        return s / 60 + ":" + (s % 60 < 10 ? "0" : "") + s % 60;
    }

    private void render() {
        String now = playing == null ? "<#9AA4B2>Clic droit : choisir un morceau"
                : "<#E6EDF3>♪ " + playing.track.title + " <#9AA4B2>— " + playing.track.author + "\n<#5A6270>" + time(Math.max(0, left))
                + " · demandé par " + playing.byName + (queue.isEmpty() ? "" : " · " + queue.size() + " en attente");
        Holo.set(sign, "<#C79BFF><b>JUKEBOX</b></#C79BFF>\n" + now);
    }

    public void open(Player p) {
        UUID u = p.getUniqueId();
        Menu m = new Menu(6, "<#C79BFF><b>Jukebox</b></#C79BFF> <#5A6270>— file : " + queue.size() + " morceau" + (queue.size() > 1 ? "x" : ""));
        for (int i = 0; i < tracks.size() && i < 27; i++) {
            Track t = tracks.get(i);
            m.set(i, Menu.item(t.icon, "<#E6EDF3><b>" + t.title, "<#9AA4B2>" + t.author + " · " + time(t.seconds),
                    "<#62CFF4>Clic : ajouter à la file"), e -> add(p, t));
        }
        List<Entry> ordered = ordered();
        for (int i = 0; i < ordered.size() && i < 9; i++) {
            Entry en = ordered.get(i);
            boolean voted = en.votes.contains(u);
            ItemStack it = Menu.item(en.track.icon, "<#C79BFF>" + (i + 1) + ". <#E6EDF3>" + en.track.title,
                    "<#9AA4B2>" + en.track.author + " · demandé par " + en.byName,
                    "<#E0484A>♥ " + en.votes.size() + " vote" + (en.votes.size() > 1 ? "s" : ""),
                    voted ? "<#7BD88F>✔ Tu as voté · clic : retirer ton vote" : "<#62CFF4>Clic : voter pour le faire monter",
                    en.by.equals(u) ? "<#5A6270>Clic droit : retirer de la file" : "");
            m.set(36 + i, it, e -> {
                if (e.getClick().isRightClick() && en.by.equals(u)) {
                    queue.remove(en);
                } else if (!en.votes.remove(u)) {
                    en.votes.add(u);
                }
                open(p);
            });
        }
        int need = needed();
        m.set(45, Menu.item(playing == null ? Material.JUKEBOX : playing.track.icon,
                playing == null ? "<#9AA4B2>Rien ne joue" : "<#C79BFF><b>♪ " + playing.track.title,
                playing == null ? "<#5A6270>Choisis un morceau ci-dessus" : "<#9AA4B2>" + playing.track.author + " · reste " + time(left),
                playing == null ? "" : "<#9AA4B2>Demandé par " + playing.byName));
        if (playing != null) {
            m.set(49, Menu.item(Material.FEATHER, "<#E0B864><b>Passer ce morceau", "<#9AA4B2>Votes : " + skip.size() + " / " + need,
                    skip.contains(u) ? "<#7BD88F>✔ Tu as voté" : "<#62CFF4>Clic : voter pour passer"), e -> {
                voteSkip(p);
                open(p);
            });
        }
        m.set(53, Menu.item(Material.PAPER, "<#9AA4B2>Musiques autorisées seulement",
                "<#5A6270>Disques de Minecraft ou morceaux libres de droits.", "<#5A6270>2 morceaux par joueur dans la file."));
        m.fill(Material.BLACK_STAINED_GLASS_PANE);
        m.open(p);
    }

    private List<Entry> ordered() {
        List<Entry> l = new ArrayList<>(queue);
        l.sort(Comparator.<Entry>comparingInt(e -> -e.votes.size()).thenComparingLong(e -> e.added));
        return l;
    }

    private int needed() {
        return Math.max(1, (world.getPlayers().size() + 1) / 2);
    }

    private void add(Player p, Track t) {
        UUID u = p.getUniqueId();
        long mine = queue.stream().filter(e -> e.by.equals(u)).count();
        if (mine >= 2) {
            Txt.bar(p, "<#E0484A>Tu as déjà 2 morceaux dans la file.");
            return;
        }
        if (queue.size() >= 20) {
            Txt.bar(p, "<#E0484A>La file est pleine.");
            return;
        }
        queue.add(new Entry(t, u, p.getName()));
        Txt.send(p, "Jukebox : <#C79BFF>" + t.title + "</#C79BFF> ajouté à la file.");
        if (playing == null) {
            next();
        } else {
            render();
            open(p);
        }
    }

    private void voteSkip(Player p) {
        if (playing == null) {
            return;
        }
        UUID u = p.getUniqueId();
        if (playing.by.equals(u)) {
            Txt.send(p, "Jukebox : tu passes ton morceau.");
            next();
            return;
        }
        if (!skip.add(u)) {
            skip.remove(u);
            return;
        }
        if (skip.size() >= needed()) {
            for (Player o : world.getPlayers()) {
                Txt.send(o, "Jukebox : morceau passé par vote.");
            }
            next();
        }
    }

    private void stopAll() {
        if (playing == null) {
            return;
        }
        for (Player o : world.getPlayers()) {
            o.stopSound(playing.track.sound, SoundCategory.RECORDS);
        }
    }

    private void next() {
        stopAll();
        skip.clear();
        List<Entry> l = ordered();
        if (l.isEmpty()) {
            playing = null;
            render();
            return;
        }
        playing = l.get(0);
        queue.remove(playing);
        left = playing.track.seconds;
        world.playSound(at(), playing.track.sound, SoundCategory.RECORDS, 4f, 1f);
        for (Player o : world.getPlayers()) {
            if (o.getLocation().distanceSquared(at()) < 64 * 64) {
                Txt.bar(o, "<#C79BFF>♪ " + playing.track.title + " <#9AA4B2>— " + playing.track.author + " · demandé par " + playing.byName);
            }
        }
        render();
    }

    /** Toutes les secondes. */
    public void tick() {
        if (playing == null) {
            return;
        }
        if (--left <= 0) {
            next();
            return;
        }
        world.spawnParticle(Particle.NOTE, at().add(0, 0.8, 0), 1, 0.2, 0.1, 0.2, 1);
        if (left % 5 == 0) {
            render();
        }
    }

    public void shutdown() {
        stopAll();
        playing = null;
        queue.clear();
    }
}
