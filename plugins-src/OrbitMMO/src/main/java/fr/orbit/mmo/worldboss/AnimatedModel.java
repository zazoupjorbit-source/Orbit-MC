package fr.orbit.mmo.worldboss;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Modèle en cubes animé par des ItemDisplay (un par os), sans plugin de modèles.
 *
 * Données : {@code boss/<nom>-anim.json}, écrit par {@code maquettes/boss/<nom>/outils/convertir.py} (Blender).
 * Chaque os est un modèle d'objet du pack ({@code item_model}) dont le point (8,8,8) est le pivot de l'os. Les
 * animations donnent, à chaque tick (20 par seconde), la matrice de chaque os dans l'espace du modèle (1 unité =
 * 1/16 de bloc à l'échelle 1, avant du modèle = -Z, pieds à Y = 0).
 */
final class AnimatedModel {

    /** L'ItemDisplay dessine l'objet retourné d'un demi-tour : on le compense (même chose dans outfit/Wearer). */
    private static final Matrix4f ITEM_FLIP = new Matrix4f().rotationY((float) Math.PI);

    record Bone(String name, Vector3f pivot, float modelScale, Key itemModel) {
    }

    record Animation(String name, int ticks, Matrix4f[][] frames) {
        /** Matrices de l'image à {@code t} ticks (interpolées entre deux images). */
        Matrix4f[] at(double t, boolean loop) {
            if (loop) {
                t = ((t % ticks) + ticks) % ticks;
            } else {
                t = Math.max(0, Math.min(ticks, t));
            }
            int i = (int) Math.floor(t);
            float f = (float) (t - i);
            Matrix4f[] a = frames[Math.min(i, frames.length - 1)];
            if (f < 1e-3 || i + 1 >= frames.length) {
                return a;
            }
            Matrix4f[] b = frames[i + 1];
            Matrix4f[] out = new Matrix4f[a.length];
            Vector3f ta = new Vector3f(), tb = new Vector3f();
            Quaternionf qa = new Quaternionf(), qb = new Quaternionf();
            for (int k = 0; k < a.length; k++) {
                a[k].getTranslation(ta);
                b[k].getTranslation(tb);
                a[k].getUnnormalizedRotation(qa);
                b[k].getUnnormalizedRotation(qb);
                out[k] = new Matrix4f().translation(ta.lerp(tb, f, new Vector3f())).rotate(qa.slerp(qb, f, new Quaternionf()));
            }
            return out;
        }
    }

    /** Données lues une fois (partagées par toutes les apparitions du modèle). */
    static final class Data {
        final String name;
        final int height;
        final List<Bone> bones = new ArrayList<>();
        final Map<String, Animation> animations = new LinkedHashMap<>();

        private Data(String name, int height) {
            this.name = name;
            this.height = height;
        }

        static Data load(InputStream in) throws java.io.IOException {
            JsonObject root;
            try (InputStreamReader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                root = JsonParser.parseReader(r).getAsJsonObject();
            }
            Data d = new Data(root.get("nom").getAsString(), root.get("hauteur").getAsInt());
            for (JsonElement e : root.getAsJsonArray("os")) {
                JsonObject o = e.getAsJsonObject();
                JsonArray p = o.getAsJsonArray("pivot");
                d.bones.add(new Bone(o.get("nom").getAsString(),
                        new Vector3f(p.get(0).getAsFloat(), p.get(1).getAsFloat(), p.get(2).getAsFloat()),
                        o.get("echelle_modele").getAsFloat(), Key.key(o.get("item_model").getAsString())));
            }
            for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("animations").entrySet()) {
                JsonObject a = e.getValue().getAsJsonObject();
                JsonArray images = a.getAsJsonArray("images");
                Matrix4f[][] frames = new Matrix4f[images.size()][];
                for (int i = 0; i < images.size(); i++) {
                    JsonObject img = images.get(i).getAsJsonObject();
                    Matrix4f[] row = new Matrix4f[d.bones.size()];
                    for (int b = 0; b < d.bones.size(); b++) {
                        JsonArray v = img.getAsJsonArray(d.bones.get(b).name());
                        row[b] = new Matrix4f()
                                .translation(v.get(0).getAsFloat(), v.get(1).getAsFloat(), v.get(2).getAsFloat())
                                .rotate(new Quaternionf(v.get(3).getAsFloat(), v.get(4).getAsFloat(), v.get(5).getAsFloat(),
                                        v.get(6).getAsFloat()).normalize());
                    }
                    frames[i] = row;
                }
                d.animations.put(e.getKey(), new Animation(e.getKey(), a.get("ticks").getAsInt(), frames));
            }
            return d;
        }

        Matrix4f[] rest() {
            Matrix4f[] out = new Matrix4f[bones.size()];
            for (int i = 0; i < out.length; i++) {
                out[i] = new Matrix4f();
            }
            return out;
        }
    }

    private final Data data;
    private final float scale;
    private final List<ItemDisplay> displays = new ArrayList<>();

    /**
     * @param scale échelle du modèle : à 1, une unité du modèle vaut 1/16 de bloc (hauteur = {@code data.height / 16})
     */
    AnimatedModel(Data data, float scale) {
        this.data = data;
        this.scale = scale;
    }

    float height() {
        return data.height / 16f * scale;
    }

    boolean spawned() {
        return !displays.isEmpty();
    }

    boolean valid() {
        for (ItemDisplay d : displays) {
            if (!d.isValid()) {
                return false;
            }
        }
        return !displays.isEmpty();
    }

    void spawn(Location at, String tag) {
        remove();
        Location base = at.clone();
        base.setYaw(0f);
        base.setPitch(0f);
        for (Bone b : data.bones) {
            displays.add(at.getWorld().spawn(base, ItemDisplay.class, e -> {
                e.setPersistent(false);
                ItemStack it = new ItemStack(Material.PAPER);
                it.setData(DataComponentTypes.ITEM_MODEL, b.itemModel());
                e.setItemStack(it);
                e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                e.setBillboard(Display.Billboard.FIXED);
                e.setViewRange(2.5f);           // ~160 blocs : un boss se voit de loin
                e.setShadowRadius(0f);
                e.setInterpolationDelay(0);
                e.setInterpolationDuration(1);
                e.setTeleportDuration(2);
                e.addScoreboardTag(tag);
            }));
        }
    }

    /**
     * Place le modèle : pieds en {@code at}, tourné vers {@code yawDeg} (lacet Minecraft, 0 = sud), avec les
     * matrices d'os données (une image d'animation), un décalage vertical et une inclinaison vers l'avant.
     *
     * @param interp ticks d'interpolation côté client (1 en continu, 3 ou 4 pour adoucir un changement d'animation)
     */
    void pose(Location at, float yawDeg, Matrix4f[] bones, float sink, float tiltDeg, int interp) {
        if (displays.isEmpty()) {
            return;
        }
        float yaw = (float) Math.PI - (float) Math.toRadians(yawDeg);
        Matrix4f root = new Matrix4f()
                .translate(0, -sink, 0)
                .rotateY(yaw)
                .rotateX((float) Math.toRadians(-tiltDeg))
                .scale(scale / 16f);
        Location base = at.clone();
        base.setYaw(0f);
        base.setPitch(0f);
        for (int i = 0; i < displays.size(); i++) {
            ItemDisplay d = displays.get(i);
            Bone b = data.bones.get(i);
            Matrix4f m = new Matrix4f(root).mul(bones[i]).translate(b.pivot()).scale(16f / b.modelScale()).mul(ITEM_FLIP);
            if (d.getLocation().distanceSquared(base) > 1.0E-4 || d.getLocation().getYaw() != 0f) {
                d.teleport(base);
            }
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(interp);
            d.setTransformationMatrix(m);
        }
    }

    void brightness(Display.Brightness b) {
        for (ItemDisplay d : displays) {
            d.setBrightness(b);
        }
    }

    void remove() {
        for (ItemDisplay d : displays) {
            d.remove();
        }
        displays.clear();
    }
}
