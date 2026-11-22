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
        ANCRAGE
    }

    public record StepDef(int index, StepType type, String text, String npc, String point, double radius,
                          List<String> mobs, int count, double chance, String item, List<Line> dialogue, List<Line> after) {

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
     * startPoint : téléportation au démarrage (facultatif).
     */
    public record QuestDef(String id, String file, String name, Faction faction, int level, List<String> requires,
                           String giver, boolean auto, String startPoint, List<String> summary,
                           List<Line> offer, List<Line> intro, List<StepDef> steps, List<Line> outro, RewardDef reward) {
    }
}
