package fr.orbit.bornes;

import fr.orbit.bornes.astro.AstroGame;
import fr.orbit.bornes.astro.Duel;
import fr.orbit.bornes.cristaux.CristauxGame;
import fr.orbit.bornes.defense.DefenseGame;
import fr.orbit.bornes.pulsar.PulsarGame;
import fr.orbit.bornes.pulsar.Songs;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Toutes les parties en cours : lancement, tick, touches, fin propre (départ, téléportation), enregistrement du score
 * et versement des Orbit Coins, file d'attente des duels d'ASTROBLOCS (bot si personne ne vient).
 */
public final class Sessions implements Listener {

    /** Bande du monde des bornes (z) de chaque jeu joué assis. */
    public static final int Z_ASTRO = 0, Z_DEFENSE = 1000, Z_PULSAR = 2000;

    private final OrbitBornes plugin;
    private final Map<UUID, Game> games = new HashMap<>();
    private final Map<GameType, Game[]> slots = new EnumMap<>(GameType.class);
    private final Map<UUID, String> last = new HashMap<>();
    private BukkitTask task;
    private Player waiting;
    private long waitingSince;

    Sessions(OrbitBornes plugin) {
        this.plugin = plugin;
        int n = Math.max(1, plugin.getConfig().getInt("places", 12));
        for (GameType t : GameType.values()) {
            slots.put(t, new Game[n]);
        }
    }

    void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    void stopAll() {
        for (Game g : new ArrayList<>(games.values())) {
            finish(g, null);
        }
        if (task != null) {
            task.cancel();
        }
        waiting = null;
    }

    public Game game(Player p) {
        return games.get(p.getUniqueId());
    }

    public boolean playing(Player p) {
        return games.containsKey(p.getUniqueId()) || p.equals(waiting);
    }

    int count() {
        return games.size();
    }

    private int freeSlot(GameType t) {
        Game[] s = slots.get(t);
        for (int i = 0; i < s.length; i++) {
            if (s[i] == null) {
                return i;
            }
        }
        return -1;
    }

    private void register(Game g) {
        games.put(g.player().getUniqueId(), g);
        if (g.slot >= 0) {
            slots.get(g.type())[g.slot] = g;
        }
    }

    // ------------------------------------------------------------------ lancement

    /** /bornes jouer <jeu> [arguments] : vérifie, mémorise pour [Rejouer], lance. */
    void play(Player p, GameType t, String[] args) {
        if (playing(p)) {
            Txt.send(p, "<#E0484A>Tu es déjà en partie (<#E6EDF3>/bornes quitter</#E6EDF3> pour abandonner).");
            return;
        }
        if (!plugin.npcs().near(p)) {
            Txt.send(p, "<#9AA4B2>Les bornes se jouent dans la salle d'arcade cachée de La Station.");
            return;
        }
        String mode = args.length > 0 ? args[0].toLowerCase(java.util.Locale.ROOT) : "";
        switch (t) {
            case ASTROBLOCS -> {
                if (mode.equals("duel")) {
                    last.put(p.getUniqueId(), "astroblocs duel");
                    queueDuel(p);
                } else {
                    last.put(p.getUniqueId(), "astroblocs solo");
                    startSeated(p, t, slot -> new AstroGame(plugin, p, slot, null));
                }
            }
            case CRISTAUX -> {
                last.put(p.getUniqueId(), "cristaux");
                Game g = new CristauxGame(plugin, p);
                register(g);
                g.start();
            }
            case DEFENSE -> {
                last.put(p.getUniqueId(), "defense");
                startSeated(p, t, slot -> new DefenseGame(plugin, p, slot));
            }
            case PULSAR -> {
                Songs.Song song = Songs.byId(mode);
                if (song == null) {
                    plugin.npcs().pulsarMenu(p);
                    return;
                }
                boolean expert = args.length > 1 && args[1].equalsIgnoreCase("expert");
                last.put(p.getUniqueId(), "pulsar " + song.id() + (expert ? " expert" : " normal"));
                startSeated(p, t, slot -> new PulsarGame(plugin, p, slot, song, expert));
            }
        }
    }

    private void startSeated(Player p, GameType t, java.util.function.IntFunction<Game> make) {
        int slot = freeSlot(t);
        if (slot < 0) {
            Txt.send(p, "<#E0484A>Toutes les bornes " + t.name + " sont prises, réessaie dans un instant.");
            return;
        }
        Game g = make.apply(slot);
        register(g);
        try {
            g.start();
        } catch (RuntimeException e) {
            plugin.getLogger().warning(t.name + " : démarrage impossible : " + e);
            finish(g, null);
        }
    }

    void replay(Player p) {
        String cmd = last.get(p.getUniqueId());
        if (cmd == null) {
            Txt.send(p, "<#9AA4B2>Aucune partie récente : parle à un PNJ des bornes.");
            return;
        }
        String[] parts = cmd.split(" ");
        play(p, GameType.of(parts[0]), java.util.Arrays.copyOfRange(parts, 1, parts.length));
    }

    // ------------------------------------------------------------------ duels d'ASTROBLOCS

    private void queueDuel(Player p) {
        if (waiting != null && waiting.isOnline() && !waiting.equals(p) && !games.containsKey(waiting.getUniqueId())) {
            Player other = waiting;
            waiting = null;
            startDuel(other, p);
            return;
        }
        waiting = p;
        waitingSince = System.currentTimeMillis();
        Txt.send(p, "<#62CFF4>En attente d'un adversaire…</#62CFF4> <#9AA4B2>(un bot prend sa place dans "
                + plugin.getConfig().getInt("astroblocs.attente-duel", 10) + " s)");
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (!o.equals(p) && plugin.npcs().near(o) && !playing(o)) {
                o.sendMessage(Txt.mm(Txt.PREFIX + "<#E6EDF3>" + p.getName() + "</#E6EDF3> cherche un duel d'" + GameType.ASTROBLOCS.title() + " ")
                        .append(Txt.button("Accepter", "#62CFF4", "/bornes jouer astroblocs duel", "Rejoindre le duel")));
            }
        }
    }

    private void startDuel(Player a, Player b) {
        int sa = freeSlot(GameType.ASTROBLOCS);
        if (sa < 0) {
            Txt.send(a, "<#E0484A>Toutes les bornes sont prises.");
            Txt.send(b, "<#E0484A>Toutes les bornes sont prises.");
            return;
        }
        Duel duel = new Duel();
        AstroGame ga = new AstroGame(plugin, a, sa, duel);
        register(ga);
        int sb = b == null ? -1 : freeSlot(GameType.ASTROBLOCS);
        if (b != null && sb < 0) {
            games.remove(a.getUniqueId());
            slots.get(GameType.ASTROBLOCS)[sa] = null;
            Txt.send(a, "<#E0484A>Toutes les bornes sont prises.");
            Txt.send(b, "<#E0484A>Toutes les bornes sont prises.");
            return;
        }
        AstroGame gb = b == null ? null : new AstroGame(plugin, b, sb, duel);
        duel.a = ga;
        duel.b = gb;
        if (gb != null) {
            register(gb);
        } else {
            duel.bot = new fr.orbit.bornes.astro.Bot(plugin.getConfig().getInt("astroblocs.bot-vitesse", 6));
        }
        ga.start();
        if (gb != null) {
            gb.start();
        }
    }

    private void tickQueue() {
        if (waiting == null) {
            return;
        }
        if (!waiting.isOnline() || !plugin.npcs().near(waiting)) {
            waiting = null;
            return;
        }
        long waited = System.currentTimeMillis() - waitingSince;
        long max = plugin.getConfig().getInt("astroblocs.attente-duel", 10) * 1000L;
        if (waited >= max) {
            Player p = waiting;
            waiting = null;
            Txt.send(p, "<#9AA4B2>Personne en vue : duel contre <#E0B864>ORBI-7</#E0B864>, le bot de la salle.");
            startDuel(p, null);
        } else if ((waited / 50) % 20 == 0) {
            Txt.bar(waiting, "<#62CFF4>Recherche d'un adversaire… <#E6EDF3>" + (max - waited + 999) / 1000 + " s");
        }
    }

    boolean cancelWait(Player p) {
        if (p.equals(waiting)) {
            waiting = null;
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ tick et fin

    private void tick() {
        tickQueue();
        for (Game g : new ArrayList<>(games.values())) {
            if (g.ended) {
                continue;
            }
            try {
                g.tick();
            } catch (RuntimeException e) {
                plugin.getLogger().warning(g.type().name + " : " + e);
                e.printStackTrace();
                finish(g, null);
            }
        }
    }

    /**
     * Fin de partie. {@code headline} : titre affiché (null = départ ou abandon sans bilan à l'écran).
     * Le joueur revient devant le PNJ, la partie est enregistrée et les OC versés.
     */
    public void finish(Game g, String headline) {
        if (g.ended && !games.containsKey(g.player().getUniqueId())) {
            return;
        }
        Player p = g.player();
        if (games.get(p.getUniqueId()) == g) {
            games.remove(p.getUniqueId());
        }
        if (g.slot >= 0 && slots.get(g.type())[g.slot] == g) {
            slots.get(g.type())[g.slot] = null;
        }
        boolean wasSeated = g.seated();
        g.cleanup();
        g.ended = true;
        if (p.isOnline() && wasSeated) {
            Location back = plugin.npcs().front(g.type());
            if (back != null) {
                p.teleport(back);
            }
        }
        if (!g.played()) {
            return;
        }
        if (p.isOnline() && headline != null) {
            Txt.title(p, headline, "<#E6EDF3>" + Txt.n(Math.round(g.score())) + " " + g.type().unit, 2600);
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.7f, 1.3f);
        }
        reward(p, g.type(), g.score(), g.ocWanted(), g.summary());
    }

    /** Enregistre un score, verse les OC (plafond du jour) et affiche le bilan avec [Rejouer] [Classement]. */
    public void reward(Player p, GameType t, double score, int ocWanted, String summary) {
        int cap = plugin.getConfig().getInt(t.id + ".plafond-oc-jour", 800);
        UUID id = p.getUniqueId();
        String name = p.getName();
        if (plugin.bank() == null) {
            if (p.isOnline()) {
                Txt.send(p, t.title() + " · " + summary + " <#9AA4B2>(OrbitCasino absent : rien d'enregistré)");
                buttons(p, t);
            }
            return;
        }
        plugin.bank().record(id, name, t, score, Math.max(0, ocWanted), cap, res -> {
            if (!p.isOnline()) {
                return;
            }
            if (res == null) {
                Txt.send(p, t.title() + " · " + summary + " <#E0484A>(base injoignable : rien d'enregistré)");
                buttons(p, t);
                return;
            }
            boolean record = score > res.best() && score > 0;
            Txt.send(p, t.title() + " · " + summary + " → " + (res.oc() > 0 ? Txt.oc(res.oc()) : "<#9AA4B2>0 OC")
                    + (res.capped() ? " <#9AA4B2>(plafond de " + Txt.n(cap) + " OC par jour)" : "")
                    + (record ? "  <#7CFF8A><b>NOUVEAU RECORD !</b>" : res.best() > 0 ? " <#5A6270>· record " + Txt.n(Math.round(res.best())) : ""));
            buttons(p, t);
            if (record) {
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.2f);
            }
        });
    }

    private void buttons(Player p, GameType t) {
        p.sendMessage(Txt.mm("  ").append(Txt.button("Rejouer", t.c1, "/bornes rejouer", "Une nouvelle partie"))
                .append(Txt.mm(" ")).append(Txt.button("Classement", "#62CFF4", "/bornes top " + t.id, "Les 10 meilleurs")));
    }

    void showTop(org.bukkit.command.CommandSender to, GameType t) {
        if (plugin.bank() == null) {
            Txt.send(to, "<#9AA4B2>Classement indisponible (OrbitCasino absent).");
            return;
        }
        plugin.bank().top(t, 10, list -> {
            Txt.send(to, "Records de " + t.title());
            if (list.isEmpty()) {
                to.sendMessage(Txt.mm("  <#9AA4B2>Aucune partie encore."));
            }
            int i = 1;
            for (String[] row : list) {
                String color = i == 1 ? "#E0B864" : i <= 3 ? "#C9D4E0" : "#9AA4B2";
                to.sendMessage(Txt.mm("  <" + color + ">" + i++ + ".</" + color + "> <#E6EDF3>" + row[0] + "</#E6EDF3> <#E0B864>"
                        + Txt.n(Long.parseLong(row[1])) + " " + t.unit));
            }
        });
    }

    List<Game> all() {
        return new ArrayList<>(games.values());
    }

    // ------------------------------------------------------------------ événements

    @EventHandler
    public void onInput(PlayerInputEvent event) {
        Game g = games.get(event.getPlayer().getUniqueId());
        if (g != null && !g.ended) {
            g.input(event.getInput());
        }
    }

    /** Maj sert dans les jeux : on ne descend pas du support pendant la partie. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDismount(EntityDismountEvent event) {
        if (event.getEntity() instanceof Player p) {
            Game g = games.get(p.getUniqueId());
            if (g != null && !g.ended && g.seated()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancelWait(event.getPlayer());
        Game g = games.get(event.getPlayer().getUniqueId());
        if (g != null) {
            finish(g, null);
        }
    }

    /** Téléporté ailleurs par autre chose (boussole, /spawn…) : la partie s'arrête proprement. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Game g = games.get(event.getPlayer().getUniqueId());
        if (g == null || g.ended || event.getTo() == null || g.allows(event.getTo())) {
            return;
        }
        if (!g.seated() && event.getTo().getWorld().equals(event.getFrom().getWorld()) && event.getTo().distanceSquared(event.getFrom()) < 64) {
            return; // petit recalage du serveur pendant un jeu sur écran
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (games.get(event.getPlayer().getUniqueId()) == g) {
                finish(g, null);
            }
        });
    }

    /** Pas de dégâts pendant une partie (chute, vide…). */
    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player p && games.containsKey(p.getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
