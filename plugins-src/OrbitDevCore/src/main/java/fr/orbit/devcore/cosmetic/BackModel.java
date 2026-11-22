package fr.orbit.devcore.cosmetic;

import fr.orbit.devcore.cosmetic.Cosmetic.BackSettings;
import org.bukkit.Material;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Géométrie des accessoires de dos. Repère local d'une entité d'affichage attachée au joueur :
 * origine au sommet de la tête (point d'attache des passagers), +X = gauche du joueur, +Y = haut,
 * +Z = devant. Pour un joueur de taille normale, le dos est en z ≈ -0,12, les épaules en
 * y ≈ -0,4 et la taille en y ≈ -1,1. Chaque style a une ancre (milieu du dos, épaules...) ;
 * {@code offset}, {@code rotation} et {@code scale} du catalogue s'appliquent autour de cette ancre.
 */
final class BackModel {

    /** Animation d'une pièce : battement d'aile (autour de l'axe vertical) ou flottement de cape. */
    enum Motion { NONE, WING_LEFT, WING_RIGHT, CAPE }

    /** Une entité d'affichage : objet ({@code block} faux) ou bloc, transformation relative à l'ancre. */
    record Part(Material material, boolean block, Motion motion, Vector3f pivot, Matrix4f local) {
    }

    // Ailes en éventail d'objets (plumes, éclats...) : angle depuis l'horizontale et longueur.
    private static final float[] FEATHER_ANGLES = {70, 56, 42, 28, 14, 0, -14};
    private static final float[] FEATHER_SCALES = {1.0f, 1.15f, 1.25f, 1.3f, 1.25f, 1.1f, 0.95f};
    // Ailes en plaques de blocs.
    private static final float[] PLATE_ANGLES = {52, 32, 12, -8, -28};
    private static final float[] PLATE_LENGTHS = {0.95f, 1.15f, 1.1f, 0.95f, 0.75f};
    private static final float PLATE_HEIGHT = 0.2f;
    private static final float PLATE_THICKNESS = 0.03f;
    private static final float HINGE_X = 0.06f;
    /** Repli des ailes vers l'arrière au repos (degrés) ; le battement s'ajoute par-dessus. */
    static final float WING_REST = 22f;
    /** Inclinaison de la cape à l'arrêt (degrés) ; elle se soulève quand le joueur avance. */
    static final float CAPE_REST = 5f;

    private BackModel() {
    }

    static Vector3f anchor(BackSettings s) {
        return switch (s.style()) {
            case ITEM -> s.item().isBlock() ? new Vector3f(0, -0.74f, -0.31f) : new Vector3f(0, -0.74f, -0.17f);
            case DIAGONAL -> new Vector3f(0, -0.70f, -0.17f);
            case WINGS -> new Vector3f(0, -0.55f, -0.19f);
            case CAPE -> new Vector3f(0, -0.40f, -0.135f);
            case JETPACK -> new Vector3f(0, -0.80f, -0.24f);
        };
    }

    static boolean animated(BackSettings s) {
        return switch (s.style()) {
            case WINGS, CAPE -> true;
            default -> false;
        };
    }

    static float restAngle(BackSettings s) {
        return switch (s.style()) {
            case WINGS -> WING_REST + s.flap() / 2;
            case CAPE -> CAPE_REST;
            default -> 0;
        };
    }

    static List<Part> parts(BackSettings s) {
        List<Part> parts = new ArrayList<>();
        switch (s.style()) {
            case ITEM -> {
                // Bloc (coffre, tonneau...) : affiché à demi-taille, aplati pour coller au dos.
                Matrix4f local = s.item().isBlock() ? new Matrix4f().scale(1f, 1f, 0.75f) : new Matrix4f().scale(0.8f);
                parts.add(new Part(s.item(), false, Motion.NONE, new Vector3f(), local));
            }
            // Objet plat en diagonale : la poignée part au-dessus de l'épaule droite.
            case DIAGONAL -> parts.add(new Part(s.item(), false, Motion.NONE, new Vector3f(),
                    new Matrix4f().rotateZ(rad(-100)).scale(1.1f)));
            case WINGS -> {
                if (s.block() != null) {
                    plates(s, parts);
                } else {
                    feathers(s, parts);
                }
            }
            case CAPE -> {
                parts.add(new Part(s.block(), true, Motion.CAPE, new Vector3f(),
                        new Matrix4f().translate(-0.28f, -1.05f, -0.03f).scale(0.56f, 1.05f, 0.03f)));
                if (s.accent() != null) { // bande centrale de couleur secondaire
                    parts.add(new Part(s.accent(), true, Motion.CAPE, new Vector3f(),
                            new Matrix4f().translate(-0.08f, -1.03f, -0.037f).scale(0.16f, 1.01f, 0.007f)));
                }
            }
            case JETPACK -> {
                Material trim = s.accent() != null ? s.accent() : s.block();
                for (int side = -1; side <= 1; side += 2) {
                    float x = side * 0.12f;
                    parts.add(new Part(s.block(), true, Motion.NONE, new Vector3f(),
                            new Matrix4f().translate(x - 0.095f, -0.24f, -0.095f).scale(0.19f, 0.48f, 0.19f)));
                    parts.add(new Part(trim, true, Motion.NONE, new Vector3f(),
                            new Matrix4f().translate(x - 0.06f, -0.32f, -0.06f).scale(0.12f, 0.08f, 0.12f)));
                }
                parts.add(new Part(trim, true, Motion.NONE, new Vector3f(),
                        new Matrix4f().translate(-0.05f, -0.15f, -0.04f).scale(0.1f, 0.3f, 0.08f)));
            }
        }
        return parts;
    }

    /** Éventail d'objets plats ; la diagonale de l'image (manche en bas à gauche) part de la charnière. */
    private static void feathers(BackSettings s, List<Part> parts) {
        for (int i = 0; i < FEATHER_ANGLES.length; i++) {
            float scale = FEATHER_SCALES[i];
            float reach = 0.6f * scale;
            float z = -0.02f * i;
            for (int side = -1; side <= 1; side += 2) {
                float angle = side > 0 ? FEATHER_ANGLES[i] : 180 - FEATHER_ANGLES[i];
                Vector3f hinge = new Vector3f(side * HINGE_X, 0, 0);
                Matrix4f local = new Matrix4f()
                        .translate(hinge.x + cos(angle) * reach, sin(angle) * reach, z)
                        .rotateZ(rad(angle - 45))
                        .scale(scale);
                parts.add(new Part(s.item(), false, side > 0 ? Motion.WING_LEFT : Motion.WING_RIGHT, hinge, local));
            }
        }
    }

    /** Plaques fines rayonnant depuis la charnière ; une sur deux en couleur secondaire. */
    private static void plates(BackSettings s, List<Part> parts) {
        for (int i = 0; i < PLATE_ANGLES.length; i++) {
            Material material = s.accent() != null && i % 2 == 1 ? s.accent() : s.block();
            float z = -0.012f * i;
            for (int side = -1; side <= 1; side += 2) {
                float angle = side > 0 ? PLATE_ANGLES[i] : 180 - PLATE_ANGLES[i];
                Vector3f hinge = new Vector3f(side * HINGE_X, 0, 0);
                Matrix4f local = new Matrix4f()
                        .translate(hinge.x, 0, z)
                        .rotateZ(rad(angle))
                        .translate(0, -PLATE_HEIGHT / 2, -PLATE_THICKNESS / 2)
                        .scale(PLATE_LENGTHS[i], PLATE_HEIGHT, PLATE_THICKNESS);
                parts.add(new Part(material, true, side > 0 ? Motion.WING_LEFT : Motion.WING_RIGHT, hinge, local));
            }
        }
    }

    /**
     * Transformation complète d'une pièce. {@code angle} : repli des ailes ou inclinaison de la cape
     * (degrés) ; {@code playerScale} : attribut de taille du joueur.
     */
    static Matrix4f matrix(BackSettings s, Part part, float angle, float playerScale) {
        Vector3f anchor = anchor(s).add(s.offset());
        Vector3f r = s.rotation();
        Matrix4f m = new Matrix4f().scale(playerScale)
                .translate(anchor)
                .rotateYXZ(rad(r.y), rad(r.x), rad(r.z))
                .scale(s.scale());
        Vector3f pivot = part.pivot();
        switch (part.motion()) {
            case WING_LEFT, WING_RIGHT -> m.translate(pivot)
                    .rotateY(rad(part.motion() == Motion.WING_LEFT ? angle : -angle))
                    .translate(-pivot.x, -pivot.y, -pivot.z);
            case CAPE -> m.translate(pivot).rotateX(rad(angle)).translate(-pivot.x, -pivot.y, -pivot.z);
            case NONE -> {
            }
        }
        return m.mul(part.local());
    }

    /** Points d'émission des particules, relatifs à l'ancre (bouts d'ailes, tuyères...). */
    static List<Vector3f> emitters(BackSettings s) {
        return switch (s.style()) {
            case WINGS -> List.of(new Vector3f(0.75f, 0.1f, -0.3f), new Vector3f(-0.75f, 0.1f, -0.3f));
            case JETPACK -> List.of(new Vector3f(0.12f, -0.38f, 0), new Vector3f(-0.12f, -0.38f, 0));
            case CAPE -> List.of(new Vector3f(0, -0.95f, -0.25f));
            default -> List.of(new Vector3f(0, 0, -0.12f));
        };
    }

    private static float rad(float degrees) {
        return (float) Math.toRadians(degrees);
    }

    private static float sin(float degrees) {
        return (float) Math.sin(Math.toRadians(degrees));
    }

    private static float cos(float degrees) {
        return (float) Math.cos(Math.toRadians(degrees));
    }
}
