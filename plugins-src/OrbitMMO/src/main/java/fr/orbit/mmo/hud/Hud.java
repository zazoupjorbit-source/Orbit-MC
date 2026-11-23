package fr.orbit.mmo.hud;

import fr.orbit.mmo.OrbitMMO;
import fr.orbit.mmo.classes.ClassDef;
import fr.orbit.mmo.classes.Faction;
import fr.orbit.mmo.classes.Resource;
import fr.orbit.mmo.party.PartyService.Party;
import fr.orbit.mmo.player.PlayerData;
import fr.orbit.mmo.player.Stamina;
import fr.orbit.mmo.player.Stats;
import fr.orbit.mmo.spell.SpellEngine;
import fr.orbit.mmo.util.Text;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * HUD. Avec le pack : la console du bas de l'écran remplace le HUD du jeu (coeurs, faim, armure, niveau,
 * rendus invisibles par le pack) : aile gauche (nom, classe, niveau, vie, endurance), pont d'expérience,
 * aile droite (raccourcis, faction, ressource, or, zone), barre d'incantation ; les barres de vie des
 * équipiers restent en haut. Sans le pack : barre de vie en haut et ligne de texte au-dessus de la barre d'action.
 */
public final class Hud {

    private static final TextColor WHITE = NamedTextColor.WHITE;
    private static final TextColor GOLD = TextColor.color(0xFFD66E);
    private static final TextColor LEVEL = TextColor.color(0xFFF3B0);
    private static final TextColor XP = TextColor.color(0xB36BFF);
    private static final TextColor STAMINA = TextColor.color(0xFFC857);
    private static final TextColor WINDED = TextColor.color(0xFF5C3B);
    private static final TextColor HINT = TextColor.color(0xB8C0CA);
    private static final TextColor DIM = TextColor.color(0x5A6270);

    private final OrbitMMO plugin;
    private final Map<UUID, BossBar> own = new HashMap<>();
    private final Map<UUID, Map<UUID, BossBar>> partyBars = new HashMap<>();
    private final Map<UUID, Long> holdUntil = new HashMap<>();
    private final Map<UUID, Component> flashText = new HashMap<>();
    /** Boîtes de dialogue des PNJ en cours d'affichage. */
    private final Map<UUID, BossBar> dialogues = new HashMap<>();
    /** Carte du PNJ de ville en cours pour chaque joueur (tant qu'il reste dans ses menus). */
    private final Map<UUID, NpcCard.Card> cards = new HashMap<>();

    public Hud(OrbitMMO plugin) {
        this.plugin = plugin;
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::barsTick, 10L, 4L);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::actionTick, 20L, 3L);
    }

    private void barsTick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            PlayerData d = plugin.players().loaded(id);
            if (d == null || !plugin.progression().valid(d)) {
                hideAll(p);
                continue;
            }
            boolean drawn = plugin.pack().isLoaded(id);
            if (drawn) {
                // La vie du joueur est dans la console du bas.
                BossBar b = own.remove(id);
                if (b != null) {
                    p.hideBossBar(b);
                }
            } else {
                BossBar self = own.computeIfAbsent(id, k -> {
                    BossBar b = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS);
                    p.showBossBar(b);
                    return b;
                });
                update(self, p.getName(), d, true, false);
            }

            Map<UUID, BossBar> bars = partyBars.computeIfAbsent(id, k -> new HashMap<>());
            Set<UUID> keep = new HashSet<>();
            Party party = plugin.parties().party(id);
            if (party != null) {
                for (UUID m : party.members()) {
                    if (m.equals(id)) {
                        continue;
                    }
                    Player mp = Bukkit.getPlayer(m);
                    PlayerData md = mp == null ? null : plugin.players().loaded(m);
                    if (md == null || !plugin.progression().valid(md)) {
                        continue;
                    }
                    keep.add(m);
                    BossBar mb = bars.computeIfAbsent(m, k -> {
                        BossBar b = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.GREEN, BossBar.Overlay.NOTCHED_10);
                        p.showBossBar(b);
                        return b;
                    });
                    update(mb, mp.getName(), md, false, drawn);
                }
            }
            for (Iterator<Map.Entry<UUID, BossBar>> it = bars.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<UUID, BossBar> e = it.next();
                if (!keep.contains(e.getKey())) {
                    p.hideBossBar(e.getValue());
                    it.remove();
                }
            }
        }
    }

    private void update(BossBar bar, String name, PlayerData d, boolean self, boolean drawn) {
        double max = Stats.maxHp(d);
        double hp = Math.max(0, d.hp);
        double f = max <= 0 ? 0 : Math.max(0, Math.min(1, hp / max));
        if (drawn) {
            // Pack chargé : barre dessinée par la police du pack ; la barre de boss blanche est invisible.
            bar.name(HpGlyphs.bar(name, d.level, hp, max, self, plugin.getConfig().getInt("hud.offset-x", 0)));
            bar.progress(1f);
            bar.color(BossBar.Color.WHITE);
            bar.overlay(BossBar.Overlay.PROGRESS);
            return;
        }
        bar.overlay(self ? BossBar.Overlay.PROGRESS : BossBar.Overlay.NOTCHED_10);
        String label = self ? "<white><b>" + Text.esc(name) + "</b></white>" : "<#B8C0CA>◆ " + Text.esc(name) + "</#B8C0CA>";
        bar.name(Text.mm(label + "  <#8C96A3>Niv.</#8C96A3><white>" + d.level + "</white>   <" + Text.hpColor(f) + ">"
                + Text.num((long) Math.ceil(hp)) + "</" + Text.hpColor(f) + "> <#5A6270>/</#5A6270> <#D7DCE2>" + Text.num((long) max)));
        bar.progress((float) f);
        bar.color(f > 0.5 ? BossBar.Color.GREEN : f > 0.2 ? BossBar.Color.YELLOW : BossBar.Color.RED);
    }

    private void actionTick() {
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            PlayerData d = plugin.players().loaded(id);
            if (d == null || !plugin.progression().valid(d)) {
                continue;
            }
            Stamina.tick(plugin, p, d, 0.15);
            Long hold = holdUntil.get(id);
            boolean held = hold != null && hold > now;
            if (plugin.pack().isLoaded(id)) {
                // Le niveau est dans la console : le chiffre vert du jeu reste masqué (niveau 0).
                if (p.getLevel() != 0 || p.getExp() != 0f) {
                    p.setLevel(0);
                    p.setExp(0f);
                }
                p.sendActionBar(console(p, d, held ? flashText.get(id) : null));
            } else if (!held || d.cast != null) {
                p.sendActionBar(Text.mm(line(p, d)));
            }
        }
    }

    // ------------------------------------------------------------------ Console (pack chargé)

    private Component console(Player p, PlayerData d, Component flash) {
        Console c = new Console();
        Location loc = p.getLocation();
        boolean offhand = !p.getInventory().getItemInOffHand().isEmpty();
        c.glyph(-210, offhand ? Console.WING_L_HOLE : Console.WING_L, 118, WHITE);
        c.glyph(93, Console.WING_R, 118, WHITE);
        c.glyph(-93, Console.BRIDGE, 187, WHITE);

        // Pont : expérience du niveau en cours.
        double xf = d.level >= plugin.progression().maxLevel() ? 1 : frac(d.xp, Stats.xpToNext(d.level));
        c.fill(-90, (int) Math.round(xf * 180), Console.F3_1, Console.F3_8, XP);

        // Aile gauche : vie, endurance, niveau, nom et classe.
        double max = Stats.maxHp(d);
        double hp = Math.max(0, d.hp);
        double hf = frac(hp, max);
        c.fill(-184, bar(hf, 64, hp > 0), Console.F8_1, Console.F8_8, color(Text.hpColor(hf), WHITE));
        c.fill(-184, bar(d.stamina / Stamina.MAX, 64, d.stamina > 0), Console.F4_1, Console.F4_8, d.winded ? WINDED : STAMINA);
        c.textCenter(-152, big((long) Math.ceil(hp)) + "/" + big((long) max), Console.T_BAR, WHITE);
        c.textCenter(-197.5, String.valueOf(d.level), Console.T_GEM, LEVEL);
        int nameEnd = c.text(-206, p.getName(), Console.T_HEAD, WHITE);
        ClassDef cls = plugin.progression().cls(d);
        if (cls != null) {
            String label = cls.name(d.faction);
            if (nameEnd + 6 <= -97 - Console.width(Console.norm(label))) {
                c.textRight(-97, label, Console.T_HEAD, color(cls.hex, HINT));
            }
        }

        // Aile droite : raccourcis, faction, ressource, or, zone.
        int x = c.text(97, "F", Console.T_HEAD, GOLD);
        x = c.text(x + 3, "MENU", Console.T_HEAD, HINT);
        x = c.text(x + 4, "·", Console.T_HEAD, DIM);
        x = c.text(x + 4, "MAJ+F", Console.T_HEAD, GOLD);
        c.text(x + 3, "PERSO", Console.T_HEAD, HINT);
        char emblem = d.faction == Faction.LUMIERE ? Console.EMB_LUMIERE : d.faction == Faction.OMBRE ? Console.EMB_OMBRE : Console.EMB_NONE;
        c.glyph(101, emblem, 17, WHITE);
        Resource r = plugin.progression().resource(d);
        TextColor rc = color(r.hex, WHITE);
        if (r == Resource.ORBES) {
            int n = (int) Math.floor(d.resource + 1.0E-6);
            for (int i = 0; i < Math.min(5, (int) r.max); i++) {
                c.glyph(120 + i * 13, i < n ? Console.PIP : Console.PIP_EMPTY, 13, i < n ? rc : WHITE);
            }
        } else {
            c.fill(120, bar(d.resource / r.max, 64, d.resource > 0), Console.F8_1, Console.F8_8, rc);
            c.textCenter(152, (long) Math.floor(d.resource) + "/" + (long) r.max, Console.T_BAR, WHITE);
        }
        c.text(121, "◆ " + gold(d.gold), Console.T_GOLD, GOLD);
        char zone = plugin.floors().inArena(loc) ? Console.ZONE_BOSS
                : plugin.floors().isSafe(loc) ? Console.ZONE_SAFE : Console.ZONE_PVP;
        c.glyph(192, zone, 12, WHITE);

        // Barre d'incantation au-dessus de la ligne de texte (le nom du sort s'affiche dessous).
        Component status = flash;
        if (d.cast instanceof SpellEngine.Cast cast) {
            c.glyph(-62, Console.CAST_FRAME, 125, WHITE);
            c.fill(-60, (int) Math.round(Math.max(0, Math.min(1, cast.progress())) * 120), Console.FC_1, Console.FC_8,
                    color(cast.spell.hex, WHITE));
            double left = Math.max(0, (cast.end - System.currentTimeMillis()) / 1000.0);
            c.textCenter(0, String.format(Locale.ROOT, "%.1f", left).replace('.', ','), Console.T_CAST, WHITE);
            if (status == null) {
                status = Text.mm(cast.spell.tag());
            }
        }
        if (status == null) {
            status = notice(d);
        }
        return c.build(status);
    }

    /** Rappel affiché sur la ligne de texte quand rien d'autre ne l'occupe (null sinon). */
    private Component notice(PlayerData d) {
        if (d.specId == null) {
            return Text.mm("<#FFD66E>Spécialisation à choisir <#5A6270>· <white>[F]</white> <#8C96A3>menu");
        }
        int free = plugin.progression().talentPointsFree(d);
        if (free > 0) {
            return Text.mm("<#FFD66E>" + free + " point" + (free > 1 ? "s" : "") + " de talent à placer <#5A6270>· <white>[F]</white> <#8C96A3>menu");
        }
        return null;
    }

    private static double frac(double v, double max) {
        return max <= 0 ? 0 : Math.max(0, Math.min(1, v / max));
    }

    /** Pixels remplis d'une barre de {@code w} pixels (au moins 1 tant que la valeur n'est pas nulle). */
    private static int bar(double f, int w, boolean nonZero) {
        int px = (int) Math.round(Math.max(0, Math.min(1, f)) * w);
        return nonZero && px == 0 ? 1 : px;
    }

    private static String big(long v) {
        return v >= 100_000 ? (v / 1000) + "K" : String.valueOf(v);
    }

    /** 12.345 : points des milliers (étroits dans la police miniature). */
    private static String gold(long v) {
        String s = String.valueOf(Math.max(0, v));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (s.length() - i) % 3 == 0) {
                sb.append('.');
            }
            sb.append(s.charAt(i));
        }
        return sb.toString();
    }

    private static TextColor color(String hex, TextColor def) {
        TextColor c = hex == null ? null : TextColor.fromHexString(hex);
        return c == null ? def : c;
    }

    // ------------------------------------------------------------------ Ligne de texte (sans le pack)

    private static String gauge(double f, int length, String hex) {
        int full = (int) Math.round(Math.max(0, Math.min(1, f)) * length);
        return "<" + hex + ">" + "▮".repeat(full) + "</" + hex + "><#3A3F47>" + "▮".repeat(length - full) + "</#3A3F47>";
    }

    private String line(Player p, PlayerData d) {
        StringBuilder sb = new StringBuilder();
        if (d.cast instanceof SpellEngine.Cast c) {
            double left = Math.max(0, (c.end - System.currentTimeMillis()) / 1000.0);
            sb.append(c.spell.tag()).append(' ').append(gauge(c.progress(), 20, c.spell.hex))
                    .append(" <#D7DCE2>").append(String.format(Locale.ROOT, "%.1f", left).replace('.', ',')).append(" s");
            return sb.toString();
        }
        Resource r = plugin.progression().resource(d);
        if (r == Resource.ORBES) {
            sb.append("<").append(r.hex).append(">").append(r.label).append(" </").append(r.hex).append(">");
            int n = (int) Math.floor(d.resource + 1.0E-6);
            for (int i = 0; i < (int) r.max; i++) {
                sb.append(i < n ? "<" + r.hex + ">●</" + r.hex + ">" : "<#3A3F47>○</#3A3F47>");
            }
        } else {
            sb.append("<").append(r.hex).append(">").append(r.label).append("</").append(r.hex).append("> ")
                    .append(gauge(d.resource / r.max, 20, r.hex)).append(" <white>").append((long) Math.floor(d.resource)).append("</white>");
        }
        sb.append(" <#3A3F47>│</#3A3F47> <#FFD66E>◆ ").append(Text.num(d.gold)).append(" or</#FFD66E>");
        int free = plugin.progression().talentPointsFree(d);
        if (d.specId == null) {
            sb.append(" <#3A3F47>│</#3A3F47> <#FFD66E>Spécialisation à choisir</#FFD66E>");
        } else if (free > 0) {
            sb.append(" <#3A3F47>│</#3A3F47> <#FFD66E>").append(free).append(" talent(s)</#FFD66E>");
        }
        if (plugin.floors().isSafe(p.getLocation())) {
            sb.append(" <#3A3F47>│</#3A3F47> <#5EE89A>Zone sûre</#5EE89A>");
        } else if (plugin.floors().inArena(p.getLocation())) {
            sb.append(" <#3A3F47>│</#3A3F47> <#FF3B3B>Salle du boss</#FF3B3B>");
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ Messages

    /** Message temporaire sur la ligne de texte de la barre d'action (prioritaire sur la ligne normale). */
    public void flash(Player p, String mini, int ticks) {
        UUID id = p.getUniqueId();
        holdUntil.put(id, System.currentTimeMillis() + ticks * 50L);
        Component msg = Text.mm(mini);
        flashText.put(id, msg);
        PlayerData d = plugin.players().loaded(id);
        if (d != null && plugin.progression().valid(d) && plugin.pack().isLoaded(id)) {
            p.sendActionBar(console(p, d, msg));
        } else {
            p.sendActionBar(msg);
        }
    }

    public void refresh(Player p) {
        UUID id = p.getUniqueId();
        PlayerData d = plugin.players().loaded(id);
        if (d == null || !plugin.progression().valid(d)) {
            return;
        }
        holdUntil.remove(id);
        flashText.remove(id);
        p.sendActionBar(plugin.pack().isLoaded(id) ? console(p, d, null) : Text.mm(line(p, d)));
    }

    // ------------------------------------------------------------------ Boîte de dialogue des PNJ

    /** La boîte de dialogue dessinée est-elle disponible pour ce joueur ? (Il faut le pack ; sinon : chat.) */
    public boolean dialogueBox(Player p) {
        return plugin.pack().isLoaded(p.getUniqueId());
    }

    /** Affiche ou met à jour la boîte de dialogue ({@link DialogueBox#build}). */
    public void dialogue(Player p, Component title) {
        BossBar bar = dialogues.get(p.getUniqueId());
        if (bar == null) {
            // Barre blanche pleine : le pack la rend invisible, il ne reste que le titre (cadre et texte).
            bar = BossBar.bossBar(title, 1f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
            dialogues.put(p.getUniqueId(), bar);
            p.showBossBar(bar);
        } else {
            bar.name(title);
        }
    }

    public void dialogueEnd(Player p) {
        BossBar bar = dialogues.remove(p.getUniqueId());
        if (bar != null) {
            p.hideBossBar(bar);
        }
    }

    // ------------------------------------------------------------------ Carte du PNJ de ville (à droite de son menu)

    /** Carte que les menus ouverts par ce joueur doivent afficher (posée au clic sur un PNJ de ville). */
    public void npcCard(Player p, NpcCard.Card card) {
        cards.put(p.getUniqueId(), card);
    }

    public NpcCard.Card npcCard(Player p) {
        return cards.get(p.getUniqueId());
    }

    public void npcCardEnd(Player p) {
        cards.remove(p.getUniqueId());
    }

    public void quit(Player p) {
        dialogueEnd(p);
        npcCardEnd(p);
        hideAll(p);
        holdUntil.remove(p.getUniqueId());
        flashText.remove(p.getUniqueId());
    }

    private void hideAll(Player p) {
        BossBar b = own.remove(p.getUniqueId());
        if (b != null) {
            p.hideBossBar(b);
        }
        Map<UUID, BossBar> bars = partyBars.remove(p.getUniqueId());
        if (bars != null) {
            for (BossBar mb : bars.values()) {
                p.hideBossBar(mb);
            }
        }
    }

    public void shutdown() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            dialogueEnd(p);
            hideAll(p);
        }
    }
}
