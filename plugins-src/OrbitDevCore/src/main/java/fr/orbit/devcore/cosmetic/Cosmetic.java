package fr.orbit.devcore.cosmetic;

import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Horse;
import org.joml.Vector3f;

/**
 * Un cosmétique du catalogue. Selon le type, seuls certains champs sont remplis :
 * <ul>
 *   <li>TRAIL / AURA : {@code effect} (+ {@code style} pour les auras)</li>
 *   <li>HAT / BALLOON : {@code item} (+ {@code texture} pour une tête personnalisée)</li>
 *   <li>BACK : {@code back}</li>
 *   <li>PET : {@code entity}, {@code baby}, {@code variant}, {@code collar}</li>
 *   <li>MOUNT : {@code mount}</li>
 * </ul>
 */
public record Cosmetic(
        CosmeticType type,
        String id,
        String name,
        String description,
        Material icon,
        Rarity rarity,
        ParticleEffect effect,
        AuraStyle style,
        Material item,
        String texture,
        EntityType entity,
        boolean baby,
        String variant,
        DyeColor collar,
        MountSettings mount,
        BackSettings back) {

    /** Formes des auras (voir {@link AuraRenderer}). */
    public enum AuraStyle {
        /** Anneau qui tourne à hauteur de taille. */
        RING,
        /** Petit cercle au-dessus de la tête. */
        HALO,
        /** Double hélice qui monte autour du joueur. */
        SPIRAL,
        /** Ailes dans le dos, qui battent doucement. */
        WINGS,
        /** Trois orbites inclinées, comme un atome. */
        ATOM,
        /** Onde qui s'élargit au sol. */
        PULSE,
        /** Tornade en cône autour du joueur. */
        TORNADO,
        /** Couronne de pointes autour de la tête. */
        CROWN,
        /** Petit nuage au-dessus de la tête, la particule tombe dessous (pluie, neige, éclairs...). */
        CLOUD
    }

    /**
     * Réglages d'une monture. Valeurs {@code <= 0} : vitesse / saut par défaut de la créature.
     * {@code color} / {@code style} : chevaux uniquement (null sinon). {@code armor} : objet de la
     * case « corps » (armure de cheval ou de nautile, harnais du ghast joyeux), ou null.
     */
    public record MountSettings(EntityType entity, double speed, double jump, double flyingSpeed,
                                Horse.Color color, Horse.Style style, Material armor) {
    }

    /** Formes des accessoires de dos (voir {@link BackModel}). */
    public enum BackStyle {
        /** Un objet au milieu du dos (sac à dos, bouclier, tonneau...). */
        ITEM,
        /** Un objet en travers du dos, la poignée au-dessus de l'épaule (épée, arc, trident...). */
        DIAGONAL,
        /** Deux ailes qui battent : éventail d'objets ({@code item}) ou plaques de blocs ({@code block}). */
        WINGS,
        /** Une cape en bloc fin, qui se soulève quand le joueur court. */
        CAPE,
        /** Deux réservoirs en blocs, avec des particules sous les tuyères. */
        JETPACK
    }

    /**
     * Réglages d'un accessoire de dos. {@code item} : objet affiché (ItemDisplay) ; {@code block} /
     * {@code accent} : blocs affichés (BlockDisplay, {@code accent} = couleur secondaire).
     * {@code offset} (gauche, haut, avant) et {@code rotation} (degrés autour de x, y, z) déplacent
     * le modèle ; {@code flap} = amplitude du battement (ailes) ou du flottement (cape), en degrés.
     */
    public record BackSettings(BackStyle style, Material item, Material block, Material accent,
                               Vector3f offset, Vector3f rotation, float scale, float flap,
                               ParticleEffect particle) {
    }
}
