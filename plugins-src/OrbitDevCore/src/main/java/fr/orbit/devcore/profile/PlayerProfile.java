package fr.orbit.devcore.profile;

import org.bukkit.Statistic;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Statistiques affichées dans le menu de la boussole.
 * Ce sont les statistiques vanilla du serveur où se trouve le joueur (chaque instance a les siennes).
 */
public record PlayerProfile(String playtime, String firstJoin, int deaths, int mobKills, int playerKills,
                            String distance) {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRANCE)
            .withZone(ZoneId.systemDefault());

    public static PlayerProfile of(Player player) {
        long seconds = player.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20L; // la statistique est en ticks
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        String playtime = hours > 0 ? hours + " h " + minutes + " min" : minutes + " min";

        long first = player.getFirstPlayed();
        String firstJoin = first > 0 ? DATE.format(Instant.ofEpochMilli(first)) : "aujourd'hui";

        long cm = (long) player.getStatistic(Statistic.WALK_ONE_CM) + player.getStatistic(Statistic.SPRINT_ONE_CM)
                + player.getStatistic(Statistic.FLY_ONE_CM);
        String distance = cm >= 100_000 ? String.format(Locale.FRANCE, "%.1f km", cm / 100_000.0) : (cm / 100) + " m";

        return new PlayerProfile(playtime, firstJoin, player.getStatistic(Statistic.DEATHS),
                player.getStatistic(Statistic.MOB_KILLS), player.getStatistic(Statistic.PLAYER_KILLS), distance);
    }
}
