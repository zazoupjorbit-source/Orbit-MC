package fr.orbitgames.hcf.display.board;

import fr.orbitgames.hcf.api.OrbitHCF;
import fr.orbitgames.hcf.api.model.Faction;
import fr.orbitgames.hcf.api.model.Relation;
import fr.orbitgames.hcf.api.service.FactionService;
import fr.orbitgames.hcf.api.util.Text;
import fr.orbitgames.hcf.display.OrbitHCFDisplayPlugin;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Scoreboard lateral et nametags par relation, par joueur.
 * Chaque joueur possede son propre scoreboard : les couleurs de nametags
 * refletent SA relation avec les autres joueurs.
 */
public class BoardManager {

    private static final String OBJECTIVE_NAME = "orbithcf";
    private static final String[] LINE_ENTRIES = buildEntries();

    private final OrbitHCFDisplayPlugin plugin;
    private final SidebarBuilder sidebarBuilder;
    private final Map<UUID, Scoreboard> boards = new ConcurrentHashMap<>();

    public BoardManager(OrbitHCFDisplayPlugin plugin) {
        this.plugin = plugin;
        this.sidebarBuilder = new SidebarBuilder(plugin);
    }

    private static String[] buildEntries() {
        String[] entries = new String[15];
        ChatColor[] colors = ChatColor.values();
        for (int i = 0; i < 15; i++) {
            entries[i] = colors[i].toString() + ChatColor.RESET;
        }
        return entries;
    }

    // ------------------------------------------------------------------
    // Cycle de vie
    // ------------------------------------------------------------------

    public void setup(Player player) {
        if (!plugin.getScoreboardConfig().getBoolean("enabled", true)) {
            return;
        }
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = board.registerNewObjective(OBJECTIVE_NAME, Criteria.DUMMY,
                Text.color(plugin.getScoreboardConfig().getString("title", "&6OrbitHCF")));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.numberFormat(NumberFormat.blank());
        boards.put(player.getUniqueId(), board);
        player.setScoreboard(board);
        updateSidebar(player);
        updateNametags(player);
    }

    public void remove(Player player) {
        boards.remove(player.getUniqueId());
    }

    public void shutdown() {
        for (UUID uuid : boards.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
        }
        boards.clear();
    }

    // ------------------------------------------------------------------
    // Sidebar
    // ------------------------------------------------------------------

    public void updateAllSidebars() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            updateSidebar(player);
        }
    }

    public void updateSidebar(Player player) {
        Scoreboard board = boards.get(player.getUniqueId());
        if (board == null) {
            return;
        }
        Objective objective = board.getObjective(OBJECTIVE_NAME);
        if (objective == null) {
            return;
        }
        List<String> lines = sidebarBuilder.build(player);
        for (int i = 0; i < LINE_ENTRIES.length; i++) {
            String entry = LINE_ENTRIES[i];
            if (i < lines.size()) {
                Team team = board.getTeam("line_" + i);
                if (team == null) {
                    team = board.registerNewTeam("line_" + i);
                    team.addEntry(entry);
                }
                team.setPrefix(lines.get(i));
                objective.getScore(entry).setScore(lines.size() - i);
            } else if (board.getEntries().contains(entry)) {
                board.resetScores(entry);
            }
        }
    }

    // ------------------------------------------------------------------
    // Nametags par relation
    // ------------------------------------------------------------------

    public void updateAllNametags() {
        if (!plugin.getNametagsConfig().getBoolean("enabled", true)) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            updateNametags(player);
        }
    }

    public void updateNametags(Player viewer) {
        Scoreboard board = boards.get(viewer.getUniqueId());
        if (board == null || !plugin.getNametagsConfig().getBoolean("enabled", true)) {
            return;
        }
        FactionService factions = OrbitHCF.factions();
        for (Player target : Bukkit.getOnlinePlayers()) {
            String group = resolveGroup(viewer, target, factions);
            Team team = getRelationTeam(board, group);
            if (!team.hasEntry(target.getName())) {
                removeFromRelationTeams(board, target.getName());
                team.addEntry(target.getName());
            }
        }
    }

    private String resolveGroup(Player viewer, Player target, FactionService factions) {
        if (viewer.getUniqueId().equals(target.getUniqueId())) {
            return "member";
        }
        if (factions == null) {
            return "neutral";
        }
        // Focus prioritaire : la faction cible est celle que la faction du viewer focus
        Faction viewerFaction = factions.getFactionByPlayer(viewer.getUniqueId());
        Faction targetFaction = factions.getFactionByPlayer(target.getUniqueId());
        if (viewerFaction != null && targetFaction != null
                && targetFaction.getId().equals(viewerFaction.getFocusTarget())) {
            return "focus";
        }
        Relation relation = factions.getRelation(viewer, target);
        return switch (relation) {
            case MEMBER -> "member";
            case ALLY -> "ally";
            case NEUTRAL -> "neutral";
            case ENEMY -> "enemy";
        };
    }

    private Team getRelationTeam(Scoreboard board, String group) {
        String name = "rel_" + group;
        Team team = board.getTeam(name);
        if (team == null) {
            team = board.registerNewTeam(name);
            String colorName = plugin.getNametagsConfig()
                    .getString("colors." + group, "WHITE").toUpperCase(Locale.ROOT);
            try {
                team.setColor(ChatColor.valueOf(colorName));
            } catch (IllegalArgumentException e) {
                team.setColor(ChatColor.WHITE);
            }
            if (group.equals("member")) {
                team.setCanSeeFriendlyInvisibles(true);
            }
        }
        return team;
    }

    private void removeFromRelationTeams(Scoreboard board, String entry) {
        for (String group : new String[]{"member", "ally", "enemy", "neutral", "focus"}) {
            Team team = board.getTeam("rel_" + group);
            if (team != null && team.hasEntry(entry)) {
                team.removeEntry(entry);
            }
        }
    }
}
