package fr.orbit.extraction.hud;

import fr.orbit.extraction.OrbitExtraction;
import fr.orbit.extraction.raid.Profile;
import fr.orbit.extraction.raid.RaidMap.Extract;
import fr.orbit.extraction.raid.RaidService;
import fr.orbit.extraction.rank.Kits.Kit;
import fr.orbit.extraction.rank.Ranks.Rank;
import fr.orbit.extraction.util.Text;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Panneau latéral façon lobby (tableau de score à droite de l'écran) : opérateur, raid ou planque, bilan, crédits.
 * Chaque joueur a son propre tableau ; les équipes du tableau principal (corps des infectés : sans collision ni nom)
 * y sont recopiées, sinon le client ne les appliquerait plus. Réglage : {@code hud.panneau} (config.yml).
 */
public final class Panel {

    private static final int LINES = 15;
    private static final String SEP = "<#5A6270> · </#5A6270>";

    private final OrbitExtraction plugin;
    private final Map<UUID, Board> boards = new HashMap<>();

    private static final class Board {
        final Scoreboard sb;
        final Objective obj;
        final Component[] shown = new Component[LINES];
        int count;

        Board(Scoreboard sb, Objective obj) {
            this.sb = sb;
            this.obj = obj;
        }
    }

    Panel(OrbitExtraction plugin) {
        this.plugin = plugin;
    }

    void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 10L);
    }

    void shutdown() {
        Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
        for (UUID id : boards.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.setScoreboard(main);
            }
        }
        boards.clear();
    }

    void quit(Player p) {
        boards.remove(p.getUniqueId());
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("hud.panneau", true);
    }

    private void tick() {
        boolean on = enabled();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!on) {
                if (boards.remove(p.getUniqueId()) != null) {
                    p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                }
                continue;
            }
            Board b = boards.get(p.getUniqueId());
            if (b == null || p.getScoreboard() != b.sb) {
                b = create(p);
            }
            syncTeams(b.sb);
            render(b, lines(p));
        }
    }

    private Board create(Player p) {
        Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective o = sb.registerNewObjective("orbit_ext", Criteria.DUMMY,
                Text.mm("<#C0392B><b>☣</b></#C0392B> <#E8695C><b>ORBIT</b></#E8695C> <#D7DCE2><b>EXTRACTION</b></#D7DCE2>"));
        o.setDisplaySlot(DisplaySlot.SIDEBAR);
        o.numberFormat(NumberFormat.blank());
        Board b = new Board(sb, o);
        boards.put(p.getUniqueId(), b);
        syncTeams(sb);
        p.setScoreboard(sb);
        return b;
    }

    /** Lignes : une entrée invisible par ligne (« §0 »…), dont le nom affiché est remplacé ; envoyées si elles changent. */
    private static void render(Board b, List<String> lines) {
        int n = Math.min(LINES, lines.size());
        for (int i = 0; i < n; i++) {
            Component c = Text.mm(lines.get(i));
            String entry = entry(i);
            if (i >= b.count) {
                Score s = b.obj.getScore(entry);
                s.setScore(LINES - i);
                s.customName(c);
                b.shown[i] = c;
            } else if (!Objects.equals(b.shown[i], c)) {
                b.obj.getScore(entry).customName(c);
                b.shown[i] = c;
            }
        }
        for (int i = n; i < b.count; i++) {
            b.sb.resetScores(entry(i));
            b.shown[i] = null;
        }
        b.count = n;
    }

    private static String entry(int i) {
        return "§" + Integer.toHexString(i) + "§r";
    }

    // ------------------------------------------------------------------ Contenu

    private List<String> lines(Player p) {
        Profile pr = plugin.profiles().get(p);
        boolean raid = plugin.raid().inRaid(p);
        Rank rank = plugin.ranks().of(p);
        List<String> l = new ArrayList<>();
        l.add("<#2A2E35>▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        l.add("<#E8695C><b>▍ OPÉRATEUR</b>");
        l.add(" <#8A919B>Nom <white>" + Text.esc(p.getName()));
        l.add(" <#8A919B>Rang " + rank.tag());
        double max = Hud.maxHealth(p);
        long hp = Math.round(Math.max(0, p.getHealth()) * 100 / Math.max(1, max));
        String hpCol = hp > 50 ? "#2ECC71" : hp > 25 ? "#F1C40F" : "#E74C3C";
        l.add(" <#8A919B>Vie <" + hpCol + ">" + hp + "</" + hpCol + "><#5A6270>/100" + SEP + "<#8A919B>Soins <white>" + plugin.meds().count(p));
        l.add(" ");
        if (raid) {
            long left = Math.max(0, (pr.raidEnds - System.currentTimeMillis()) / 1000);
            l.add("<#E67E22><b>▍ RAID EN COURS</b>");
            l.add(" <#8A919B>Temps <" + (left < 120 ? "#E74C3C" : "#D7DCE2") + ">" + Text.clock(left));
            l.add(" <#8A919B>Sortie " + nearestExtract(p, pr));
            l.add(" <#8A919B>Sorties actives <white>" + pr.extracts.size());
        } else {
            l.add("<#2ECC71><b>▍ PLANQUE</b>");
            l.add(" <#8A919B>Statut <#2ECC71>en sécurité");
            int ready = 0;
            for (Kit k : plugin.kits().all()) {
                if (plugin.kits().unlocked(p, k) && plugin.kits().remaining(p, k) == 0) {
                    ready++;
                }
            }
            l.add(" <#8A919B>Kits prêts " + (ready > 0 ? "<#2ECC71>" + ready : "<#5A6270>aucun"));
            int unread = pr.unread();
            l.add(" <#8A919B>Messages " + (unread > 0 ? "<#6EA2FF>" + unread + " non lu" + (unread > 1 ? "s" : "") : "<#5A6270>aucun"));
        }
        l.add("  ");
        l.add("<#22C7D9><b>▍ BILAN</b>");
        int survival = pr.raids == 0 ? 0 : (int) Math.round(pr.survived * 100.0 / pr.raids);
        l.add(" <#8A919B>Raids <white>" + pr.raids + SEP + "<#8A919B>Survie <#2ECC71>" + survival + " %");
        l.add(" <#8A919B>Joueurs <white>" + pr.pvpKills + SEP + "<#8A919B>Infectés <white>" + pr.infectedKills);
        l.add("<#F5C542>◆ " + spaced(plugin.phone().credits(p)) + " <#8A919B>crédits");
        return l;
    }

    /** Sortie la plus proche : nom, distance et flèche relative au regard. */
    private String nearestExtract(Player p, Profile pr) {
        Extract best = null;
        double bestD = Double.MAX_VALUE;
        Location at = p.getLocation();
        for (String name : pr.extracts) {
            Extract ex = plugin.map().extracts.get(name);
            Location l = ex == null ? null : ex.loc().toLocation();
            if (l == null || l.getWorld() != at.getWorld()) {
                continue;
            }
            double d = l.distanceSquared(at);
            if (d < bestD) {
                bestD = d;
                best = ex;
            }
        }
        if (best == null) {
            return "<#5A6270>inconnue";
        }
        Location l = best.loc().toLocation();
        String name = best.name().length() > 14 ? best.name().substring(0, 13) + "…" : best.name();
        return "<#2ECC71>" + Text.esc(name) + " <#D7DCE2>" + (int) Math.sqrt(bestD) + " m " + RaidService.arrow(p, l);
    }

    /** 12 345 (espace fine des milliers). */
    private static String spaced(long v) {
        return String.format(Locale.FRANCE, "%,d", Math.max(0, v)).replace(' ', ' ').replace(' ', ' ');
    }

    // ------------------------------------------------------------------ Équipes recopiées du tableau principal

    private static void syncTeams(Scoreboard sb) {
        Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
        Set<String> names = new HashSet<>();
        for (Team src : main.getTeams()) {
            names.add(src.getName());
            Team t = sb.getTeam(src.getName());
            if (t == null) {
                t = sb.registerNewTeam(src.getName());
            }
            for (Team.Option o : Team.Option.values()) {
                if (t.getOption(o) != src.getOption(o)) {
                    t.setOption(o, src.getOption(o));
                }
            }
            if (t.allowFriendlyFire() != src.allowFriendlyFire()) {
                t.setAllowFriendlyFire(src.allowFriendlyFire());
            }
            if (t.canSeeFriendlyInvisibles() != src.canSeeFriendlyInvisibles()) {
                t.setCanSeeFriendlyInvisibles(src.canSeeFriendlyInvisibles());
            }
            Set<String> want = src.getEntries();
            Set<String> have = t.getEntries();
            for (String e : want) {
                if (!have.contains(e)) {
                    t.addEntry(e);
                }
            }
            for (String e : have) {
                if (!want.contains(e)) {
                    t.removeEntry(e);
                }
            }
        }
        for (Team t : sb.getTeams()) {
            if (!names.contains(t.getName())) {
                t.unregister();
            }
        }
    }

    /**
     * Entrée ajoutée à une équipe du tableau principal (corps d'infecté qui apparaît) : recopiée tout de suite dans
     * les tableaux des joueurs, sans attendre la synchronisation périodique (sinon son nom s'afficherait un instant).
     */
    public void mirrorEntry(String team, String entry) {
        for (Board b : boards.values()) {
            Team t = b.sb.getTeam(team);
            if (t == null) {
                syncTeams(b.sb);
            } else if (!t.hasEntry(entry)) {
                t.addEntry(entry);
            }
        }
    }
}
