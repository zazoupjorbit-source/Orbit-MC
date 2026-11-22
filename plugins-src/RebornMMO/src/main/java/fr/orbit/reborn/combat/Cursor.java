package fr.orbit.reborn.combat;

import fr.orbit.reborn.RebornMMO;
import fr.orbit.reborn.player.PlayerData;
import fr.orbit.reborn.util.Text;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/**
 * Couleur du curseur : vert (normal), orange (a attaqué un joueur vert), rouge (a tué un joueur vert).
 * Affichée sur le pseudo au-dessus de la tête et dans la liste des joueurs.
 */
public final class Cursor {

    private static final String[] HEX = {"#5EE89A", "#FF8A3B", "#FF3B3B"};
    private final RebornMMO plugin;
    private final Team[] teams = new Team[3];

    public Cursor(RebornMMO plugin) {
        this.plugin = plugin;
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        teams[0] = team(sb, "rbmmo_vert", NamedTextColor.GREEN);
        teams[1] = team(sb, "rbmmo_orange", NamedTextColor.GOLD);
        teams[2] = team(sb, "rbmmo_rouge", NamedTextColor.RED);
    }

    private static Team team(Scoreboard sb, String name, NamedTextColor color) {
        Team t = sb.getTeam(name);
        if (t == null) {
            t = sb.registerNewTeam(name);
        }
        t.color(color);
        return t;
    }

    public int state(PlayerData d) {
        long now = System.currentTimeMillis();
        if (now < d.redUntil) {
            return 2;
        }
        if (now < d.orangeUntil) {
            return 1;
        }
        return 0;
    }

    public void update(Player p, PlayerData d) {
        int s = state(d);
        if (!teams[s].hasEntry(p.getName())) {
            teams[s].addEntry(p.getName());
        }
        p.playerListName(Text.mm("<" + HEX[s] + ">" + Text.esc(p.getName())));
    }

    /** Attaquer un joueur vert rend orange. */
    public void onAttack(Player attacker, PlayerData da, PlayerData victim) {
        if (state(victim) != 0) {
            return;
        }
        long until = System.currentTimeMillis() + plugin.getConfig().getLong("pvp.orange-minutes", 10) * 60_000L;
        boolean wasGreen = state(da) == 0;
        da.orangeUntil = Math.max(da.orangeUntil, until);
        update(attacker, da);
        if (wasGreen) {
            Text.msg(attacker, "<#FF8A3B>Votre curseur devient orange : vous avez attaqué un joueur. Les gardes vous refuseront l'entrée des villes.");
        }
    }

    /** Tuer un joueur vert rend rouge. */
    public void onKill(Player killer, PlayerData dk, PlayerData victim) {
        if (state(victim) != 0) {
            return;
        }
        dk.redUntil = System.currentTimeMillis() + plugin.getConfig().getLong("pvp.red-minutes", 30) * 60_000L;
        dk.playerKills++;
        update(killer, dk);
        Text.msg(killer, "<#FF3B3B>Votre curseur devient rouge : vous avez tué un joueur.");
    }
}
