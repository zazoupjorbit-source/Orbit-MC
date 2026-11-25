package fr.orbit.mmo.quest;

import fr.orbit.mmo.classes.Faction;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.List;

/** Définitions lues dans plugins/OrbitMMO/quests/*.yml. */
public final class QuestDefs {

    private QuestDefs() {
    }

    /** Réplique d'un dialogue. speaker : id d'un PNJ, « narrateur » ou « joueur ». */
    public record Line(String speaker, String text) {
    }

    /** PNJ de quête, posé sur un point nommé. */
    public record NpcDef(String id, String name, String hex, String title, EntityType type, String skin,
                         String profession, String point, Material hand, List<String> idle) {
    }

    /** Zone où apparaissent des monstres de quête tant qu'un joueur en a besoin. */
    public record SpawnDef(String id, String point, String mob, int minLevel, int maxLevel, int count, double radius) {
    }

    public enum StepType {
        /** Parler à un PNJ (joue le dialogue de l'étape). */
        PARLER,
        /** Entrer dans le rayon d'un point. */
        ATTEINDRE,
        /** Clic droit près d'un point. */
        INTERAGIR,
        /** Tuer N monstres (ids de monstres). */
        TUER,
        /** Récupérer N objets sur des monstres (chance par mort). */
        RAMASSER,
        /** Lancer un sort de sa classe. */
        SORT,
        /** Clic droit près d'un Éclat d'ancrage : il devient le point de réapparition. */
        ANCRAGE,
        /** Escorter un PNJ qui marche le long d'un chemin ; s'il tombe, on recommence. */
        ESCORTE,
        /** Tenir un point (une cible qui a des PV) pendant X vagues ou X secondes. */
        DEFENSE,
        /** Répondre à un PNJ : la réponse est gardée (branches « si », conditions « choix:… » des quêtes). */
        CHOIX,
        /** Apporter à un PNJ un objet de quête (remis au début de l'étape). */
        LIVRER,
        /** Examiner N indices dans n'importe quel ordre, puis conclure (devant un PNJ si « pnj »). */
        ENQUETE,
        /** Atteindre un point sans être vu par les gardes ; repéré = retour au départ. */
        INFILTRER,
        /** Suivre des traces au sol, une par une, jusqu'à la dernière. */
        PISTER,
        /** Atteindre un point en portant une tenue qui rend certains monstres passifs. */
        DEGUISEMENT,
        /** Tenir sans mourir dans une zone qui se resserre ou qui brûle. */
        SURVIE,
        /** Ramener une cargaison à dos de monture ; un coup ou une descente et elle tombe. */
        TRANSPORT
    }

    /** Réponse proposée par une étape CHOIX. */
    public record Choice(String id, String text, List<Line> reply, long xp, long gold) {
    }

    /** Indice d'une étape ENQUETE. */
    public record Clue(String id, String point, double radius, String text, List<Line> after) {
    }

    /**
     * Réglages des types d'étapes ajoutés le 23/11 (escorte, défense, choix…), dans une seule fiche :
     * chaque type ne lit que ce qui le concerne (voir docs\quetes-types.md).
     */
    public record Extra(List<String> path, List<Clue> clues, String choiceId, String question, List<Choice> choices,
                        String start, String spawnPoint, String targetName, double hp, double dps, int waves,
                        int duration, int interval, double speed, double sight, double endRadius, String mode,
                        Material material, String color, int level) {
    }

    /**
     * Une étape. condition : « choix=réponse » (ou « !choix=réponse ») ; l'étape est sautée si elle n'est pas remplie.
     * timer : contre la montre, en secondes (0 = sans) ; fail : texte joué quand le temps est écoulé ;
     * retry : numéro d'étape (0 = la même) où l'on reprend après un échec.
     */
    public record StepDef(int index, StepType type, String text, String npc, String point, double radius,
                          List<String> mobs, int count, double chance, String item, List<Line> dialogue, List<Line> after,
                          String condition, int timer, List<Line> fail, int retry, Extra x) {

        /** Objectif à compteur (affiché « 3/8 »). */
        public boolean counted() {
            return (type == StepType.TUER || type == StepType.RAMASSER) && count > 1;
        }
    }

    public record ItemReward(Material material, String name, int amount, List<String> lore) {
    }

    public record RewardDef(long xp, long gold, List<ItemReward> items) {
    }

    /**
     * Une quête. giver nul ou auto = démarre seule dès que les conditions sont remplies.
     * startPoint : téléportation au démarrage (facultatif). admin : réservée à l'administration (orbitmmo.admin),
     * pour les quêtes d'essai. repeatHours : quête répétable (contrats), de nouveau proposée ce nombre d'heures après
     * avoir été finie ; 0 = une seule fois.
     */
    public record QuestDef(String id, String file, String name, Faction faction, int level, List<String> requires,
                           String giver, boolean auto, String startPoint, List<String> summary,
                           List<Line> offer, List<Line> intro, List<StepDef> steps, List<Line> outro, RewardDef reward,
                           boolean admin, double repeatHours) {
    }
}
