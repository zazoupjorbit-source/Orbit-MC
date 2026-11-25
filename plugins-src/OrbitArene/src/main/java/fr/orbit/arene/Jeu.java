package fr.orbit.arene;

import org.bukkit.entity.Villager;

import java.util.List;

/**
 * Les quatre jeux de l'arène (famille C du contrat docs/arcade-jeux.md) et leurs modes. Mécaniques connues,
 * noms et habillage ORBIT : aucun nom, logo ni look d'un jeu existant.
 */
public enum Jeu {

    NEBULEUSE("nebuleuse", "Nébuleuse", "#62CFF4", "#C79BFF", 6.5, Villager.Profession.CLERIC,
            "Grossis en avalant les orbes et les plus petits que toi… ou deviens une comète."),
    CHARGE("charge", "Charge Nova", "#FF8A3D", "#E0B864", 10.5, Villager.Profession.ARMORER,
            "Labyrinthe, charges explosives et bonus : le dernier debout gagne."),
    PALET("palet", "Palet Orbital", "#62CFF4", "#7CFF8A", 14.5, Villager.Profession.FLETCHER,
            "Duel de palet sur coussin d'air, au clavier."),
    TURBO("turbo", "Turbo-Sphère", "#3A7FFF", "#E0B864", 18.5, Villager.Profession.WEAPONSMITH,
            "Foot en bolides à réaction : boost, sauts et buts.");

    /** Un mode jouable : {@code code} = clé du tableau des records (arcade_jeux / arcade_scores, 16 car. max). */
    public record Mode(Jeu jeu, String id, String nom, String code, String unite, int ordre, String aide) {
        public String titre() {
            return "<gradient:" + jeu.c1 + ":" + jeu.c2 + "><b>" + jeu.nom.toUpperCase(java.util.Locale.ROOT)
                    + (jeu == NEBULEUSE ? " · " + nom.toUpperCase(java.util.Locale.ROOT) : "")
                    + "</b></gradient>";
        }

        public String cle() {
            return jeu.id + ":" + id;
        }
    }

    public final String id, nom, c1, c2, description;
    public final double npcX;
    public final Villager.Profession metier;

    Jeu(String id, String nom, String c1, String c2, double npcX, Villager.Profession metier, String description) {
        this.id = id;
        this.nom = nom;
        this.c1 = c1;
        this.c2 = c2;
        this.npcX = npcX;
        this.metier = metier;
        this.description = description;
    }

    public String grad(String text) {
        return "<gradient:" + c1 + ":" + c2 + ">" + text + "</gradient>";
    }

    public List<Mode> modes() {
        return switch (this) {
            case NEBULEUSE -> List.of(
                    new Mode(this, "gravite", "Gravité", "arene_gravite", "masse", 30,
                            "Avale les orbes et les plus petits que toi : tu grossis (et tu ralentis)."),
                    new Mode(this, "comete", "Comète", "arene_comete", "cases", 31,
                            "Ta traînée tue tout ce qui la touche : Q/D pour tourner, Espace pour accélérer."));
            case CHARGE -> List.of(new Mode(this, "nova", "Mêlée", "arene_nova", "pts", 32,
                    "Pose tes charges (Maj ou clic droit), casse les caisses, ramasse les bonus."));
            case PALET -> List.of(
                    new Mode(this, "bot", "Contre un bot", "arene_palet", "pts", 33, "Partie immédiate contre un bot."),
                    new Mode(this, "duel", "Duel", "arene_palet", "pts", 33, "Contre un autre joueur (un bot s'il n'en vient pas)."));
            case TURBO -> List.of(
                    new Mode(this, "1v1", "1 contre 1", "arene_turbo", "pts", 34, "Un bolide chacun."),
                    new Mode(this, "2v2", "2 contre 2", "arene_turbo", "pts", 34, "Deux équipes de deux, complétées par des bots."));
        };
    }

    public Mode mode(String id) {
        for (Mode m : modes()) {
            if (m.id.equalsIgnoreCase(id)) {
                return m;
            }
        }
        return null;
    }

    public static Jeu parse(String s) {
        for (Jeu j : values()) {
            if (j.id.equalsIgnoreCase(s) || j.name().equalsIgnoreCase(s)) {
                return j;
            }
        }
        return null;
    }
}
