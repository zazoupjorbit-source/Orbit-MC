package fr.orbit.survie;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * HUD permanent à droite de l'écran (barre latérale du tableau des scores, chiffres masqués), même style
 * que celui du Tycoon : pseudo, grade, solde et rang de fortune, monde + position, jour et heure de la
 * survie, base où l'on se trouve, armoires possédées et temps de protection restant, mobs tués et morts.
 * Rafraîchi chaque seconde ; une ligne n'est renvoyée au joueur que si son texte a changé.
 * Les soldes viennent de la base (lus toutes les {@code hud.soldes-secondes} hors du thread principal).
 */
final class Hud implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String TITRE = "<gradient:#62CFF4:#E0B864><bold>✦ ORBIT SURVIE ✦</bold></gradient>";
    private static final String TRAIT = "<dark_gray><st>                    </st>";

    private record Ecran(Scoreboard tableau, Objective objectif, List<String> lignes) { }

    private final OrbitSurvie plugin;
    private final Map<UUID, Ecran> ecrans = new HashMap<>();
    /** Solde et rang de chaque compte, relus périodiquement sur le fil de la base. */
    private volatile Map<UUID, Economie.Rang> fortunes = Map.of();
    private volatile int comptes;
    private BukkitTask rafraichir, soldes;

    Hud(OrbitSurvie plugin) {
        this.plugin = plugin;
    }

    void demarrer() {
        for (Player p : Bukkit.getOnlinePlayers()) creer(p);
        rafraichir = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) maj(p);
        }, 20L, 20L);
        long periode = Math.max(2, plugin.getConfig().getInt("hud.soldes-secondes", 5)) * 20L;
        soldes = Bukkit.getScheduler().runTaskTimer(plugin, this::lireSoldes, 0L, periode);
    }

    void arreter() {
        if (rafraichir != null) rafraichir.cancel();
        if (soldes != null) soldes.cancel();
        for (Player p : Bukkit.getOnlinePlayers())
            if (ecrans.containsKey(p.getUniqueId())) p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        ecrans.clear();
    }

    // ------------------------------------------------------------------ joueurs

    @EventHandler
    public void connexion(PlayerJoinEvent e) {
        creer(e.getPlayer());
        // Le compte vient peut-être d'être ouvert : on relit les soldes un peu après.
        Bukkit.getScheduler().runTaskLater(plugin, this::lireSoldes, 40L);
    }

    @EventHandler
    public void deconnexion(PlayerQuitEvent e) {
        ecrans.remove(e.getPlayer().getUniqueId());
    }

    private void creer(Player p) {
        Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective o = sb.registerNewObjective("orbit_hud", Criteria.DUMMY, MM.deserialize(TITRE));
        o.setDisplaySlot(DisplaySlot.SIDEBAR);
        o.numberFormat(NumberFormat.blank()); // pas de chiffres rouges à droite
        p.setScoreboard(sb);
        ecrans.put(p.getUniqueId(), new Ecran(sb, o, new ArrayList<>()));
        maj(p);
    }

    private void maj(Player p) {
        Ecran ecran = ecrans.get(p.getUniqueId());
        if (ecran == null) return;
        List<String> lignes = lignes(p);
        List<String> avant = ecran.lignes();
        int n = lignes.size();
        for (int i = 0; i < n; i++) {
            String texte = lignes.get(i);
            if (i < avant.size() && avant.get(i).equals(texte)) continue;
            var score = ecran.objectif().getScore("l" + i);
            score.setScore(n - i); // de haut en bas
            score.customName(texte.isEmpty() ? Component.empty() : MM.deserialize(texte));
        }
        for (int i = n; i < avant.size(); i++) ecran.tableau().resetScores("l" + i);
        avant.clear();
        avant.addAll(lignes);
    }

    // ------------------------------------------------------------------ contenu

    private List<String> lignes(Player p) {
        UUID id = p.getUniqueId();
        List<String> l = new ArrayList<>();
        // 15 lignes max dans la barre latérale : chaque ligne compte.
        l.add(TRAIT);
        l.add("<#62CFF4>◈ <white><bold>" + MM.escapeTags(p.getName()) + "</bold> <dark_gray>· " + grade(p));

        Economie.Rang f = fortunes.get(id);
        if (!plugin.eco().active()) {
            l.add("<#E0B864>⛃ <gray>Solde <dark_gray>» <#FF6B6B>banque fermée");
            l.add("<#62CFF4>✦ <gray>Fortune <dark_gray>» <dark_gray>…");
        } else {
            l.add("<#E0B864>⛃ <gray>Solde <dark_gray>» " + (f == null ? "<dark_gray>…" : "<#E0B864>" + Msg.nombre(f.solde())));
            l.add("<#62CFF4>✦ <gray>Fortune <dark_gray>» "
                    + (f == null ? "<dark_gray>…" : "<white>#" + f.rang() + "<dark_gray>/" + comptes));
        }
        l.add(" ");

        Location loc = p.getLocation();
        l.add("<#E0B864><bold>MONDE <dark_gray>» " + monde(p.getWorld()));
        l.add("<#62CFF4>⌖ <white>" + loc.getBlockX() + " <dark_gray>/ <white>" + loc.getBlockY()
                + " <dark_gray>/ <white>" + loc.getBlockZ() + " <dark_gray>" + cap(loc.getYaw()));
        l.add(horloge());
        l.add("  ");

        Bases bases = plugin.bases();
        l.add("<#E0B864><bold>BASE <dark_gray>» " + zone(p, bases));
        List<Bases.Base> miennes = bases.possedees(id);
        l.add("<gray>Armoires <dark_gray>» <white>" + miennes.size() + " <dark_gray>/ <white>" + bases.limite(p));
        l.add("<gray>Protection <dark_gray>» " + protection(miennes, bases));
        l.add("   ");

        l.add("<#3DDCB0>⚔ <white>" + Msg.nombre(p.getStatistic(Statistic.MOB_KILLS)) + " <gray>tués <dark_gray>• <#FF6B6B>☠ <white>"
                + Msg.nombre(p.getStatistic(Statistic.DEATHS)) + " <gray>morts");
        l.add("<#E0B864>/shop <dark_gray>• <#62CFF4>/hdv <dark_gray>• <#E0B864>/base");
        return l;
    }

    private String monde(World w) {
        if (w.getName().equals(plugin.ile().worldName())) return "<#62CFF4>Île du spawn";
        return switch (w.getEnvironment()) {
            case NETHER -> "<#FF6B6B>Nether";
            case THE_END -> "<#C792EA>End";
            default -> "<#3DDCB0>Survie";
        };
    }

    /** Point cardinal regardé (N, NE, E…). */
    private static String cap(float yaw) {
        String[] points = {"S", "SO", "O", "NO", "N", "NE", "E", "SE"};
        int i = Math.floorMod(Math.round(yaw / 45f), 8);
        return points[i];
    }

    /** Jour et heure du monde de survie (même quand on est sur l'île, où il fait toujours jour). */
    private String horloge() {
        World w = Bukkit.getWorld(plugin.getConfig().getString("survie.monde", "world"));
        if (w == null) return "<dark_gray>☀ Survie introuvable";
        long temps = w.getTime();
        long jour = w.getFullTime() / 24000 + 1;
        long t = (temps + 6000) % 24000; // 0 tick = 6 h
        int h = (int) (t / 1000), m = (int) (t % 1000 * 60 / 1000);
        boolean nuit = temps >= 12542 && temps < 23460; // les monstres apparaissent
        String icone = nuit ? "<#9AA7FF>☾" : "<#F5C542>☀";
        return icone + " <gray>Jour <white>" + Msg.nombre(jour) + " <dark_gray>· <white>"
                + String.format("%02dh%02d", h, m) + (nuit ? " <#9AA7FF>nuit" : "");
    }

    private String zone(Player p, Bases bases) {
        if (p.getWorld().getName().equals(plugin.ile().worldName())) return "<#62CFF4>zone protégée";
        Bases.Base b = bases.a(p.getLocation());
        if (b == null) return "<gray>zone libre";
        if (b.proprio.equals(p.getUniqueId())) return "<#3DDCB0>chez toi";
        String nom = MM.escapeTags(b.proprioNom());
        return (bases.peut(p, b) ? "<#3DDCB0>" : "<#FF6B6B>") + "base de " + nom;
    }

    /** Temps de protection restant de l'armoire qui expire le plus tôt. */
    private static String protection(List<Bases.Base> miennes, Bases bases) {
        if (miennes.isEmpty()) return "<dark_gray><i>aucune armoire</i>";
        long reste = Long.MAX_VALUE;
        for (Bases.Base b : miennes) reste = Math.min(reste, b.payeJusqua - System.currentTimeMillis());
        if (reste <= 0) return "<#FF6B6B><bold>COUPÉE</bold> <gray>(/base)";
        return (reste < bases.avertirMs() ? "<#F5C542>⚠ " : "<#3DDCB0>") + Bases.duree(reste);
    }

    /** Grade LuckPerms le plus haut (liste hud.grades de config.yml, du plus haut au plus bas). */
    private String grade(Player p) {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("hud.grades");
        if (s != null) for (String groupe : s.getKeys(false)) {
            String perm = "group." + groupe;
            // isPermissionSet : un op sans grade ne doit pas apparaître « Fondateur ».
            if (p.isPermissionSet(perm) && p.hasPermission(perm)) return s.getString(groupe);
        }
        return plugin.getConfig().getString("hud.grade-defaut", "<gray>Joueur");
    }

    // ------------------------------------------------------------------ soldes

    private void lireSoldes() {
        if (!plugin.eco().active()) return;
        plugin.eco().fortunes().whenComplete((liste, erreur) -> {
            if (erreur != null || liste == null) return;
            Map<UUID, Economie.Rang> m = new HashMap<>();
            for (Economie.Rang r : liste) m.put(r.uuid(), r);
            fortunes = m;
            comptes = liste.size();
        });
    }
}
