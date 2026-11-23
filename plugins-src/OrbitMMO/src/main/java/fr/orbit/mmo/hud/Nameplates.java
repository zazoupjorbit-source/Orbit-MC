package fr.orbit.mmo.hud;

import fr.orbit.mmo.OrbitMMO;
import fr.orbit.mmo.mob.MobState;
import fr.orbit.mmo.player.PlayerData;
import fr.orbit.mmo.player.Stats;
import fr.orbit.mmo.util.Text;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Barres de vie au-dessus des monstres (continent, étages, donjons, boss) et des joueurs : nom et niveau, puis une
 * barre du pack (cadre {@code plate_frame} + remplissage teinté vert/jaune/rouge).
 *
 * <p>La barre est une entité d'affichage de texte à part, déplacée à chaque tick avec une interpolation de 2 ticks
 * (elle suit son porteur en douceur). Elle n'est pas accrochée comme passager : un joueur ou un monstre qui porte un
 * passager ne peut plus être téléporté (warps, étages, quêtes, montures). Un joueur ne voit pas sa propre barre.</p>
 */
public final class Nameplates implements Listener {

    private static final Key FONT = Key.key("orbitmmo", "hud");
    private static final char FRAME = '', FILL_1 = '', FILL_8 = '';
    /** Largeur du cadre et de son intérieur (pixels du pack). */
    private static final int FRAME_W = 42, INNER = 40;
    /** Distance (blocs) au-delà de laquelle un monstre n'a pas de barre. */
    private static final double RANGE = 40;
    private static final ShadowColor SHADOW = ShadowColor.shadowColor(0xB0000000);
    private static final TextColor GREY = TextColor.color(0x9AA4B2);

    private final OrbitMMO plugin;
    public static final String TAG = "orbit_plate";
    private final Map<UUID, Plate> plates = new HashMap<>();

    private static final class Plate {
        TextDisplay display;
        String key = "";
        UUID hiddenFrom;
    }

    public Nameplates(OrbitMMO plugin) {
        this.plugin = plugin;
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        for (World w : Bukkit.getWorlds()) {
            for (TextDisplay td : w.getEntitiesByClass(TextDisplay.class)) {
                if (td.getScoreboardTags().contains(TAG)) {
                    td.remove();
                }
            }
        }
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 1L);
    }

    public void stop() {
        for (Plate p : plates.values()) {
            if (p.display != null) {
                p.display.remove();
            }
        }
        plates.clear();
    }

    /** Entité d'affichage d'une barre (pour l'exclure des ciblages et des purges). */
    public boolean isPlate(Entity e) {
        return e instanceof TextDisplay && e.getScoreboardTags().contains(TAG);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        // Le nouveau venu ne doit pas voir sa propre barre si elle existe encore (reconnexion rapide).
        Plate p = plates.get(e.getPlayer().getUniqueId());
        if (p != null && p.display != null) {
            p.display.remove();
            plates.remove(e.getPlayer().getUniqueId());
        }
    }

    // ------------------------------------------------------------------ Tick

    private int ticks;

    private void tick() {
        ticks++;
        Set<UUID> seen = new HashSet<>();
        boolean refresh = ticks % 4 == 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData d = plugin.players().loaded(p.getUniqueId());
            if (d == null || !plugin.progression().valid(d) || p.isDead() || d.hp <= 0
                    || p.getGameMode() == GameMode.SPECTATOR || p.isInvisible()) {
                continue;
            }
            seen.add(p.getUniqueId());
            follow(p, p.getHeight() + 0.78, refresh ? playerText(d) : null, refresh ? key(d) : null, p.getUniqueId());
        }
        for (MobState ms : plugin.mobs().all()) {
            LivingEntity e = ms.entity;
            if (ms.dead || !e.isValid() || e.isDead() || !nearPlayer(e)) {
                continue;
            }
            seen.add(e.getUniqueId());
            String k = ms.level + ":" + bucket(ms.hp, ms.maxHp) + ":" + ms.def.id();
            follow(e, e.getHeight() + 0.15, refresh ? mobText(ms) : null, refresh ? k : null, null);
        }
        for (Iterator<Map.Entry<UUID, Plate>> it = plates.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Plate> en = it.next();
            if (!seen.contains(en.getKey())) {
                if (en.getValue().display != null) {
                    en.getValue().display.remove();
                }
                it.remove();
            }
        }
    }

    private boolean nearPlayer(LivingEntity e) {
        Location l = e.getLocation();
        for (Player p : e.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(l) < RANGE * RANGE) {
                return true;
            }
        }
        return false;
    }

    /**
     * Place la barre au-dessus de {@code owner} ; {@code text} (null = inchangé) n'est envoyé que si {@code key}
     * a changé.
     */
    private void follow(LivingEntity owner, double dy, Component text, String key, UUID hideFrom) {
        Plate pl = plates.computeIfAbsent(owner.getUniqueId(), u -> new Plate());
        Location at = owner.getLocation().add(0, dy, 0);
        at.setYaw(0);
        at.setPitch(0);
        if (pl.display == null || !pl.display.isValid() || pl.display.getWorld() != at.getWorld()) {
            if (pl.display != null) {
                pl.display.remove();
            }
            if (text == null) {
                return; // créée au prochain rafraîchissement, avec son texte
            }
            pl.display = spawn(at);
            pl.key = "";
            pl.hiddenFrom = null;
        } else if (pl.display.getLocation().distanceSquared(at) > 0.0004) {
            pl.display.teleport(at);
        }
        if (hideFrom != null && !hideFrom.equals(pl.hiddenFrom)) {
            Player self = Bukkit.getPlayer(hideFrom);
            if (self != null) {
                self.hideEntity(plugin, pl.display);
                pl.hiddenFrom = hideFrom;
            }
        }
        if (text != null && !key.equals(pl.key)) {
            pl.display.text(text);
            pl.key = key;
        }
    }

    private TextDisplay spawn(Location at) {
        return at.getWorld().spawn(at, TextDisplay.class, td -> {
            td.setPersistent(false);
            td.addScoreboardTag(TAG);
            td.setBillboard(Display.Billboard.CENTER);
            td.setDefaultBackground(false);
            td.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            td.setShadowed(false);
            td.setSeeThrough(false);
            td.setLineWidth(400);
            td.setViewRange(0.6f);
            td.setBrightness(new Display.Brightness(15, 15));
            td.setTeleportDuration(2);
            td.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                    new Vector3f(0.8f, 0.8f, 0.8f), new AxisAngle4f()));
        });
    }

    // ------------------------------------------------------------------ Contenu

    /** Pas de 2,5 % : la barre ne change qu'au pixel près (40 px), inutile de renvoyer le texte plus souvent. */
    private static int bucket(double hp, double max) {
        return units(hp, max);
    }

    private static int units(double hp, double max) {
        double f = max <= 0 ? 0 : Math.max(0, Math.min(1, hp / max));
        return hp <= 0 ? 0 : Math.max(1, (int) Math.ceil(f * INNER));
    }

    private static String key(PlayerData d) {
        return d.level + ":" + bucket(d.hp, Stats.maxHp(d)) + ":" + (d.faction == null ? "" : d.faction.id);
    }

    private Component mobText(MobState ms) {
        TextComponent.Builder b = Component.text();
        TextColor nameColor = ms.boss != null ? TextColor.color(0xFF3B3B) : TextColor.color(0xFF8A7A);
        b.append(Component.text(ms.def.name(), nameColor).decoration(TextDecoration.BOLD, ms.boss != null).shadowColor(SHADOW));
        b.append(Component.text("  Lv." + ms.level, GREY).decoration(TextDecoration.BOLD, false).shadowColor(SHADOW));
        b.append(Component.newline());
        b.append(bar(ms.hp, ms.maxHp));
        return b.build();
    }

    private Component playerText(PlayerData d) {
        TextColor fc = d.faction == null ? NamedTextColor.WHITE : TextColor.fromHexString(d.faction.hex);
        TextComponent.Builder b = Component.text();
        b.append(Component.text("Lv." + d.level, fc).shadowColor(SHADOW));
        b.append(Component.newline());
        b.append(bar(d.hp, Stats.maxHp(d)));
        return b.build();
    }

    /** Cadre puis remplissage (segments de 8 puis de 1 px), teinté selon la part de vie restante. */
    static Component bar(double hp, double max) {
        int u = units(hp, max);
        double f = max <= 0 ? 0 : hp / max;
        TextColor fill = TextColor.fromHexString(Text.hpColor(f));
        StringBuilder frame = new StringBuilder().append(FRAME).append(HpGlyphs.space(-FRAME_W));
        StringBuilder seg = new StringBuilder();
        for (int i = 0; i < u / 8; i++) {
            seg.append(FILL_8).append(HpGlyphs.space(-1));
        }
        for (int i = 0; i < u % 8; i++) {
            seg.append(FILL_1).append(HpGlyphs.space(-1));
        }
        seg.append(HpGlyphs.space(FRAME_W + 1 - 1 - u));
        return Component.text()
                .append(glyphs(frame.toString(), NamedTextColor.WHITE))
                .append(glyphs(seg.toString(), fill))
                .build();
    }

    private static Component glyphs(String s, TextColor color) {
        return Component.text(s).font(FONT).color(color).shadowColor(ShadowColor.none())
                .decoration(TextDecoration.BOLD, false).decoration(TextDecoration.ITALIC, false);
    }
}
