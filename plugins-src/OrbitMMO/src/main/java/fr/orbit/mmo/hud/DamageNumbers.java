package fr.orbit.mmo.hud;

import fr.orbit.mmo.OrbitMMO;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Chiffres de dégâts flottants (« -4 ») : visibles du seul joueur qui a frappé, ils jaillissent au-dessus de la cible,
 * montent puis disparaissent. Coup critique : doré, plus gros, avec une étoile.
 */
public final class DamageNumbers {

    public static final String TAG = "orbit_dmg";
    private static final Key FONT = Key.key("orbitmmo", "qtext_b");
    private static final TextColor NORMAL = TextColor.color(0xFFFFFF);
    private static final TextColor CRIT = TextColor.color(0xFFD66E);
    private static final ShadowColor SHADOW = ShadowColor.shadowColor(0xC0000000);

    private final OrbitMMO plugin;

    public DamageNumbers(OrbitMMO plugin) {
        this.plugin = plugin;
    }

    /** Purge des chiffres restés après un arrêt brutal. */
    public void start() {
        for (World w : plugin.getServer().getWorlds()) {
            for (TextDisplay td : w.getEntitiesByClass(TextDisplay.class)) {
                if (td.getScoreboardTags().contains(TAG)) {
                    td.remove();
                }
            }
        }
    }

    /** « 4 », « 0.8 », « 12 » : une décimale sous 10, sans zéro inutile. */
    static String format(double amount) {
        if (amount >= 9.95) {
            return Long.toString(Math.round(amount));
        }
        String s = String.format(Locale.ROOT, "%.1f", Math.max(0.1, amount));
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    public void show(Player viewer, LivingEntity target, double amount, boolean crit) {
        if (viewer == null || !viewer.isOnline() || amount <= 0 || target == null || target == viewer
                || target.getWorld() != viewer.getWorld()) {
            return;
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Location at = target.getLocation().add(r.nextDouble(-0.45, 0.45), target.getHeight() * 0.8 + r.nextDouble(0, 0.3),
                r.nextDouble(-0.45, 0.45));
        at.setYaw(0);
        at.setPitch(0);
        Component text = Component.text((crit ? "✦ -" : "-") + format(amount)).font(FONT)
                .color(crit ? CRIT : NORMAL).shadowColor(SHADOW)
                .decoration(TextDecoration.BOLD, false).decoration(TextDecoration.ITALIC, false);
        float big = crit ? 1.9f : 1.35f;
        TextDisplay td = at.getWorld().spawn(at, TextDisplay.class, d -> {
            d.setPersistent(false);
            d.addScoreboardTag(TAG);
            d.setVisibleByDefault(false);
            d.setBillboard(Display.Billboard.CENTER);
            d.setDefaultBackground(false);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setShadowed(false);
            d.setSeeThrough(true);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setViewRange(0.5f);
            d.text(text);
            d.setTransformation(tr(0, big * 0.55f));
        });
        viewer.showEntity(plugin, td);
        // Apparition (agrandissement) puis montée, puis rétrécissement : interpolations côté client.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> anim(td, 4, 0.35f, big), 1L);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> anim(td, 10, 0.85f, big * 0.9f), 6L);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> anim(td, 5, 1.0f, 0.01f), 16L);
        plugin.getServer().getScheduler().runTaskLater(plugin, td::remove, 22L);
    }

    private static void anim(TextDisplay td, int duration, float up, float scale) {
        if (!td.isValid()) {
            return;
        }
        td.setInterpolationDelay(0);
        td.setInterpolationDuration(duration);
        td.setTransformation(tr(up, scale));
    }

    private static Transformation tr(float up, float scale) {
        return new Transformation(new Vector3f(0, up, 0), new AxisAngle4f(), new Vector3f(scale, scale, scale),
                new AxisAngle4f());
    }
}
