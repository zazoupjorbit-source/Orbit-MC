package fr.orbit.tycoon;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
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
import java.util.concurrent.ConcurrentHashMap;

/**
 * HUD permanent à droite de l'écran (barre latérale du tableau des scores, chiffres masqués) :
 * pseudo, grade, les trois monnaies, l'outil utilisé (niveau, sa ressource propre récoltée, prestige),
 * prestige total, classement global (aux Crédits orbitaux), nom de l'île et générateurs (posés / max).
 * Rafraîchi chaque seconde ; une ligne n'est renvoyée au joueur que si son texte a changé.
 */
final class Hud implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String TITRE = "<gradient:#62CFF4:#E0B864><bold>✦ ORBIT TYCOON ✦</bold></gradient>";
    private static final String TRAIT = "<dark_gray><st>                    </st>";

    private record Ecran(Scoreboard tableau, Objective objectif, List<String> lignes) { }

    private final OrbitTycoon plugin;
    private final Monnaies monnaies;
    private final Map<UUID, Ecran> ecrans = new HashMap<>();
    /** Rang de chaque joueur au classement global, recalculé toutes les minutes hors du thread principal. */
    private volatile Map<UUID, Integer> rangs = Map.of();
    private volatile int classes;
    private BukkitTask rafraichir, classement;

    Hud(OrbitTycoon plugin, Monnaies monnaies) {
        this.plugin = plugin;
        this.monnaies = monnaies;
    }

    void demarrer() {
        for (Player p : Bukkit.getOnlinePlayers()) creer(p);
        rafraichir = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) maj(p);
        }, 20L, 20L);
        long periode = Math.max(10, plugin.getConfig().getInt("hud.classement-secondes", 60)) * 20L;
        classement = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::calculerClassement, 0L, periode);
    }

    void arreter() {
        if (rafraichir != null) rafraichir.cancel();
        if (classement != null) classement.cancel();
        for (Player p : Bukkit.getOnlinePlayers())
            if (ecrans.containsKey(p.getUniqueId())) p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        ecrans.clear();
    }

    // ------------------------------------------------------------------ joueurs

    @EventHandler
    public void connexion(PlayerJoinEvent e) {
        creer(e.getPlayer());
        // Le nouveau venu n'est peut-être pas encore classé : on relance le calcul.
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::calculerClassement);
    }

    @EventHandler
    public void deconnexion(PlayerQuitEvent e) {
        ecrans.remove(e.getPlayer().getUniqueId());
    }

    /** Changement d'emplacement de la barre rapide : l'outil en main a pu changer → HUD tout de suite. */
    @EventHandler
    public void changeMain(PlayerItemHeldEvent e) {
        bientot(e.getPlayer());
    }

    /** Rafraîchit le HUD du joueur au tick suivant (après le changement d'objet), sans attendre la seconde. */
    void bientot(Player p) {
        Bukkit.getScheduler().runTask(plugin, () -> { if (p.isOnline()) maj(p); });
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
        l.add("<#62CFF4>◈ <white><bold>" + echapper(p.getName()) + "</bold> <dark_gray>· " + grade(p));
        l.add("<#E0B864><bold>MONNAIES");
        for (Monnaies.Monnaie m : Monnaies.Monnaie.values()) {
            long v = monnaies.solde(id, m);
            l.add("<" + m.couleur + ">" + m.symbole + " <white>" + nombre(v) + " <gray>" + court(m));
        }
        Outil.Mode outil = outilUtilise(p);
        if (outil == null) {
            l.add("<#E0B864><bold>OUTIL <dark_gray>» <dark_gray><i>aucun</i>");
            l.add("<dark_gray>Ton Outil orbital revient");
            l.add("<dark_gray>à ta prochaine connexion.");
            l.add("  ");
        } else {
            Progression prog = plugin.progression();
            int niveau = Math.min(monnaies.niveau(id, outil), prog.niveauMax());
            l.add("<#E0B864><bold>OUTIL <dark_gray>» <" + outil.couleur + "><bold>" + outil.nom);
            l.add("<gray>Niveau <dark_gray>» <#3DDCB0>" + niveau + " <dark_gray>/ " + prog.niveauMax());
            // Chaque outil a sa propre ressource : la ligne suit l'outil en main.
            l.add("<" + outil.couleur + ">" + outil.symbole + " <white>" + nombre(monnaies.recoltes(id, outil))
                    + " <gray>" + outil.ressource);
            l.add("<gray>Prestige <dark_gray>» <#E0B864>✪ " + monnaies.prestige(id, outil) + " <dark_gray>/ " + prog.prestigeMax());
        }
        l.add(" ");
        Integer rang = rangs.get(id);
        // Rang = classement global aux Crédits orbitaux.
        l.add("<gray>Total <#E0B864>✪ " + monnaies.prestige(id) + " <dark_gray>• <gray>Rang "
                + (rang == null ? "<dark_gray>…" : "<white>#" + rang + "<dark_gray>/" + classes));
        String ile = monnaies.ile(id);
        l.add("<gray>Île <dark_gray>» " + (ile == null ? "<dark_gray><i>aucune</i>" : "<white>" + echapper(ile)));
        int poses = monnaies.generateursPoses(id), max = monnaies.generateursMax(id);
        l.add("<gray>Générateurs <dark_gray>» " + (poses >= max ? "<#FF6B6B>" : "<#3DDCB0>") + poses + " <dark_gray>/ <white>" + max);
        l.add("<#E0B864>/boutique <dark_gray>• <#62CFF4>/vote");
        return l;
    }

    /** Outil orbital tenu en main ; sinon le mode de celui rangé dans l'inventaire ; null s'il n'en a pas. */
    private Outil.Mode outilUtilise(Player p) {
        Outil o = plugin.outil();
        var main = p.getInventory().getItemInMainHand();
        return o.est(main) ? o.mode(main) : o.modeActuel(p);
    }

    private static String court(Monnaies.Monnaie m) {
        return switch (m) {
            case CREDIT -> "Crédits";
            case FLUX -> "Flux";
            case PLASMA -> "Plasma";
        };
    }

    /** Rang au classement global (null = pas encore classé). */
    Integer rang(UUID id) { return rangs.get(id); }

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

    /** 1234567 → « 1 234 567 », puis 12,3 M au-delà du million pour garder le HUD étroit. */
    static String nombre(long v) {
        if (v >= 1_000_000_000L) return decimale(v / 1e9) + " Md";
        if (v >= 1_000_000L) return decimale(v / 1e6) + " M";
        String s = Long.toString(v);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (s.length() - i) % 3 == 0) b.append(' ');
            b.append(s.charAt(i));
        }
        return b.toString();
    }

    private static String decimale(double d) {
        return String.format(java.util.Locale.FRANCE, "%.1f", d).replace(",0", "");
    }

    private static String echapper(String s) {
        return MM.escapeTags(s);
    }

    // ------------------------------------------------------------------ classement

    private void calculerClassement() {
        Map<UUID, Long> credits = monnaies.tousLesCredits();
        List<Map.Entry<UUID, Long>> liste = new ArrayList<>(credits.entrySet());
        liste.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        Map<UUID, Integer> r = new ConcurrentHashMap<>();
        for (int i = 0; i < liste.size(); i++) r.put(liste.get(i).getKey(), i + 1);
        rangs = r;
        classes = liste.size();
    }
}
