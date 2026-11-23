package fr.orbit.mmo.hud;

import fr.orbit.mmo.OrbitMMO;
import fr.orbit.mmo.classes.ClassDef;
import fr.orbit.mmo.classes.Faction;
import fr.orbit.mmo.classes.SpecDef;
import fr.orbit.mmo.continent.ContinentData;
import fr.orbit.mmo.guild.Guild;
import fr.orbit.mmo.player.PlayerData;
import fr.orbit.mmo.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Liste des joueurs (touche Tab) : emblème et couleur de faction, pseudo, classe et spécialisation, niveau.
 * Tri : Lumière en haut, Ombre en dessous, puis du plus haut niveau au plus bas ; joueurs sans personnage à la fin.
 * En-tête : nombre de joueurs de chaque faction ; pied : zone, région du continent et guilde du joueur qui regarde.
 * Passe par playerListName + setPlayerListOrder (pas d'équipes : le suivi des quêtes donne à chacun son propre tableau).
 */
public final class TabList {

    public static final String SUN = "☀";
    public static final String MOON = "☾";

    private final OrbitMMO plugin;
    /** Dernier nom envoyé par joueur (n'envoie que les changements). */
    private final Map<UUID, String> lastName = new HashMap<>();
    private final Map<UUID, String> lastFrame = new HashMap<>();

    public TabList(OrbitMMO plugin) {
        this.plugin = plugin;
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 40L, 20L);
    }

    public void forget(UUID id) {
        lastName.remove(id);
        lastFrame.remove(id);
    }

    public static String glyph(Faction f) {
        return f == Faction.OMBRE ? MOON : SUN;
    }

    private void tick() {
        int light = 0;
        int dark = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData d = plugin.players().loaded(p.getUniqueId());
            boolean ok = d != null && d.hasCharacter();
            if (ok && d.faction == Faction.LUMIERE) {
                light++;
            } else if (ok) {
                dark++;
            }
            String mini = ok ? line(p, d) : "<#5A6270>✧</#5A6270> <#B8C0CA>" + Text.esc(p.getName()) + "</#B8C0CA> <#5A6270>· sans personnage</#5A6270>";
            if (!mini.equals(lastName.get(p.getUniqueId()))) {
                lastName.put(p.getUniqueId(), mini);
                p.playerListName(Text.mm(mini));
            }
            // Ordre décroissant : Lumière 2000+, Ombre 1000+, sans personnage 0.
            int order = !ok ? 0 : (d.faction == Faction.LUMIERE ? 2000 : 1000) + Math.min(999, d.level);
            if (p.getPlayerListOrder() != order) {
                p.setPlayerListOrder(order);
            }
        }
        String header = "\n<gradient:#FFD66E:#7FD4FF:#B36BFF><bold>ORBIT MMO</bold></gradient>\n"
                + "<#FFD66E>" + SUN + " Lumière " + light + "</#FFD66E>  <#3A3F47>│</#3A3F47>  "
                + "<#B36BFF>" + MOON + " Ombre " + dark + "</#B36BFF>\n";
        for (Player p : Bukkit.getOnlinePlayers()) {
            String frame = header + "\u0000" + footer(p);
            if (frame.equals(lastFrame.get(p.getUniqueId()))) {
                continue;
            }
            lastFrame.put(p.getUniqueId(), frame);
            Component h = Text.mm(header);
            Component f = Text.mm(footer(p));
            p.sendPlayerListHeaderAndFooter(h, f);
        }
    }

    private String line(Player p, PlayerData d) {
        Faction f = d.faction;
        StringBuilder sb = new StringBuilder();
        sb.append('<').append(f.hex).append('>').append(glyph(f)).append("</").append(f.hex).append("> ");
        sb.append("<white>").append(Text.esc(p.getName())).append("</white>");
        ClassDef c = plugin.classes().get(d.classId);
        if (c != null) {
            sb.append(" <#5A6270>·</#5A6270> ").append(c.tag(f));
            SpecDef s = d.specId == null ? null : c.specs.get(d.specId);
            if (s != null) {
                sb.append(" <#8A93A0>").append(Text.esc(s.name)).append("</#8A93A0>");
            }
        }
        sb.append(" <#5A6270>·</#5A6270> <#FFF3B0>Niv. ").append(d.level).append("</#FFF3B0>");
        return sb.toString();
    }

    private String footer(Player p) {
        StringBuilder sb = new StringBuilder("\n");
        Location loc = p.getLocation();
        String zone = plugin.floors().inArena(loc) ? "<#FF5C3B>☠ Combat de boss</#FF5C3B>"
                : plugin.floors().isSafe(loc) ? "<#5EE89A>✔ Zone sûre</#5EE89A>"
                : plugin.inGame(loc.getWorld()) ? "<#FF8A5C>⚔ Zone PvP</#FF8A5C>" : "<#B8C0CA>Hors jeu</#B8C0CA>";
        sb.append(zone);
        if (plugin.continent() != null && plugin.continent().isContinent(loc.getWorld()) && plugin.continent().data() != null) {
            ContinentData.Region r = plugin.continent().data().region(loc.getBlockX(), loc.getBlockZ());
            if (r != null) {
                sb.append("  <#3A3F47>│</#3A3F47>  <#E8E2D0>").append(Text.esc(r.name())).append("</#E8E2D0> <#8A93A0>(niv. ")
                        .append(r.levelMin()).append('-').append(r.levelMax()).append(")</#8A93A0>");
            }
        }
        Guild g = plugin.guilds().of(p.getUniqueId());
        if (g != null) {
            sb.append("  <#3A3F47>│</#3A3F47>  <#7FD4FF>Guilde ").append(Text.esc(g.name())).append("</#7FD4FF>");
        }
        sb.append("\n<#5A6270>[F] Menu  ·  [Maj+F] Mon personnage  ·  /guilde</#5A6270>\n");
        return sb.toString();
    }
}
