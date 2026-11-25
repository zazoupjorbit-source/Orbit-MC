package fr.orbit.bornes.astro;

/** Un duel d'ASTROBLOCS : deux joueurs, ou un joueur (a) contre le bot. */
public final class Duel {

    public AstroGame a, b;
    public Bot bot;

    Board other(AstroGame me) {
        if (me == a) {
            return b != null ? b.board : bot.board;
        }
        return a.board;
    }

    String otherName(AstroGame me) {
        if (me == a) {
            return b != null ? b.player().getName() : Bot.NAME;
        }
        return a.player().getName();
    }
}
