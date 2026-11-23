package fr.orbit.extraction.hud;

import fr.orbit.extraction.OrbitExtraction;
import fr.orbit.extraction.armor.Armors;
import fr.orbit.extraction.gun.GunService.GunStatus;
import fr.orbit.extraction.phone.PhoneMenu;
import fr.orbit.extraction.raid.Profile;
import fr.orbit.extraction.rank.Ranks.Rank;
import fr.orbit.extraction.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * HUD d'Orbit Extraction. Avec le pack : la console du bas de l'écran (police orbitextraction:hud, textures
 * tools/HudGen.java) remplace les coeurs, la faim, l'armure et l'expérience du jeu, masqués par le pack :
 * <ul>
 *   <li>aile gauche : nom, tenue portée, protection (écrou), vie (sur 100), vivres, carte tactique en main gauche ;</li>
 *   <li>pont : temps de raid restant (plein et vert à la planque) ;</li>
 *   <li>aile droite : raccourcis, insigne du rang, chargeur de l'arme en main, réserve (ou crédits), zone ;</li>
 *   <li>barre de progression : extraction ou soin en cours ; ligne de texte : messages ({@link Text#bar}).</li>
 * </ul>
 * Sans le pack, rien ne change : barre d'action en texte comme avant. Le panneau latéral ({@link Panel}) s'affiche
 * dans les deux cas. Maj + F ouvre la fiche de l'opérateur ({@link PhoneMenu.Screen#PROFIL}).
 */
public final class Hud implements Listener {

    private static final TextColor WHITE = NamedTextColor.WHITE;
    private static final TextColor RED = TextColor.color(0xE8695C);
    private static final TextColor HINT = TextColor.color(0xB8C0CA);
    private static final TextColor DIM = TextColor.color(0x5A6270);
    private static final TextColor ARMOR = TextColor.color(0xD7DCE2);
    private static final TextColor BRASS = TextColor.color(0xD9C27A);
    private static final TextColor CREDITS = TextColor.color(0xF5C542);

    private final OrbitExtraction plugin;
    private final Panel panel;
    private final Map<UUID, Long> holdUntil = new HashMap<>();
    private final Map<UUID, Component> flashText = new HashMap<>();
    private int ticks;

    public Hud(OrbitExtraction plugin) {
        this.plugin = plugin;
        this.panel = new Panel(plugin);
    }

    public Panel panel() {
        return panel;
    }

    public void start() {
        Text.barSink((p, c) -> {
            if (!drawn(p)) {
                return false;
            }
            flash(p, c, 50);
            return true;
        });
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 3L);
        panel.start();
    }

    public void shutdown() {
        Text.barSink(null);
        panel.shutdown();
    }

    /** La console est-elle dessinée pour ce joueur ? (Il faut le pack.) */
    public boolean drawn(Player p) {
        return plugin.pack().isLoaded(p.getUniqueId());
    }

    private void tick() {
        ticks++;
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!drawn(p) || p.isDead()) {
                continue;
            }
            // L'expérience est masquée par le pack : le chiffre vert du niveau reste à 0 (invisible).
            if (p.getLevel() != 0 || p.getExp() != 0f) {
                p.setLevel(0);
                p.setExp(0f);
            }
            Long hold = holdUntil.get(p.getUniqueId());
            p.sendActionBar(console(p, hold != null && hold > now ? flashText.get(p.getUniqueId()) : null));
        }
    }

    // ------------------------------------------------------------------ Console

    private Component console(Player p, Component flash) {
        Console c = new Console();
        Profile pr = plugin.profiles().get(p);
        boolean raid = plugin.raid().inRaid(p);
        boolean offhand = !p.getInventory().getItemInOffHand().isEmpty();
        c.tiles(-210, offhand ? Console.WING_L_HOLE : Console.WING_L, Console.WING_TILES, WHITE);
        c.tiles(93, Console.WING_R, Console.WING_TILES, WHITE);
        c.tiles(-93, Console.BRIDGE, Console.BRIDGE_TILES, WHITE);

        // Pont : temps de raid restant (plein et vert à la planque).
        long now = System.currentTimeMillis();
        if (raid) {
            long left = Math.max(0, pr.raidEnds - now);
            double f = frac(left, plugin.raid().durationMs());
            TextColor col = left < 120_000 ? (ticks % 4 < 2 ? TextColor.color(0xE74C3C) : TextColor.color(0x7A2018))
                    : f < 0.25 ? TextColor.color(0xE67E22) : TextColor.color(0xD7DCE2);
            c.fill(-90, (int) Math.round(f * 180), Console.F3_1, Console.F3_8, col);
        } else {
            c.fill(-90, 180, Console.F3_1, Console.F3_8, TextColor.color(0x2ECC71));
        }

        // Aile gauche : vie (sur 100), vivres, protection, nom et tenue.
        double max = maxHealth(p);
        double hp = Math.max(0, p.getHealth());
        double hf = frac(hp, max);
        TextColor hpCol = hf > 0.5 ? TextColor.color(0x2ECC71) : hf > 0.25 ? TextColor.color(0xF1C40F)
                : ticks % 4 < 2 ? TextColor.color(0xE74C3C) : TextColor.color(0x9E2D21);
        c.fill(-184, bar(hf, 64, hp > 0), Console.F8_1, Console.F8_8, hpCol);
        int food = p.getFoodLevel();
        c.fill(-184, bar(food / 20.0, 64, food > 0), Console.F4_1, Console.F4_8,
                food <= 6 ? TextColor.color(0xC0392B) : TextColor.color(0xE6A23C));
        c.textCenter(-152, Math.round(hp * 100 / Math.max(1, max)) + "/100", Console.T_BAR, WHITE);
        AttributeInstance armorAttr = p.getAttribute(Attribute.ARMOR);
        long armor = armorAttr == null ? 0 : Math.round(armorAttr.getValue());
        c.textCenter(-197.5, String.valueOf(armor), Console.T_GEM, armor > 0 ? ARMOR : DIM);
        int nameEnd = c.text(-206, p.getName(), Console.T_HEAD, WHITE);
        Armors.SetDef set = outfit(p);
        String outfit = set == null ? "Sans tenue" : set.name();
        if (nameEnd + 6 <= -97 - Console.width(Console.norm(outfit))) {
            c.textRight(-97, outfit, Console.T_HEAD, set == null ? DIM : color(set.color(), HINT));
        }

        // Aile droite : raccourcis, rang, arme en main, réserve ou crédits, zone.
        GunStatus g = plugin.gunService().status(p);
        int x = c.text(97, "F", Console.T_HEAD, RED);
        x = c.text(x + 3, g != null ? "RECHARGER" : "CARTE", Console.T_HEAD, HINT);
        x = c.text(x + 4, "·", Console.T_HEAD, DIM);
        x = c.text(x + 4, "MAJ+F", Console.T_HEAD, RED);
        c.text(x + 3, "FICHE", Console.T_HEAD, HINT);
        Rank rank = plugin.ranks().of(p);
        c.glyph(101, Console.RANK, 17, color(rank.color(), HINT));
        if (g == null) {
            c.textCenter(152, "MAINS LIBRES", Console.T_BAR, DIM);
            c.text(121, "◆ " + thousands(plugin.phone().credits(p)) + " CR", Console.T_LOW, CREDITS);
        } else if (g.reload() >= 0) {
            c.fill(120, bar(g.reload(), 64, true), Console.F8_1, Console.F8_8, BRASS);
            c.textCenter(152, "RECHARGE", Console.T_BAR, WHITE);
            c.text(121, "RESERVE " + g.reserve(), Console.T_LOW, BRASS);
        } else {
            TextColor magCol = g.mag() == 0 ? TextColor.color(0xC0392B) : g.mag() <= g.magMax() / 4 ? TextColor.color(0xE67E22) : BRASS;
            c.fill(120, bar(frac(g.mag(), g.magMax()), 64, g.mag() > 0), Console.F8_1, Console.F8_8, magCol);
            c.textCenter(152, g.mag() + "/" + g.magMax(), Console.T_BAR, WHITE);
            c.text(121, "RESERVE " + g.reserve(), Console.T_LOW, g.reserve() == 0 ? TextColor.color(0xC0392B) : BRASS);
        }
        Cast cast = plugin.raid().extraction(p);
        char zone = cast != null ? Console.ZONE_EXTRACT : raid ? Console.ZONE_RAID : Console.ZONE_SAFE;
        c.glyph(192, zone, 12, WHITE);

        // Barre de progression : extraction, sinon soin, sinon fouille en cours ; son nom sur la ligne de texte.
        if (cast == null) {
            cast = plugin.meds().progress(p);
        }
        if (cast == null) {
            cast = plugin.search().progress(p);
        }
        Component status = flash;
        if (cast != null) {
            c.tiles(-62, Console.CAST_FRAME, Console.CAST_TILES, WHITE);
            c.fill(-60, (int) Math.round(Math.max(0, Math.min(1, cast.ratio())) * 120), Console.FC_1, Console.FC_8,
                    color(cast.hex(), WHITE));
            c.textCenter(0, String.format(Locale.ROOT, "%.1f", cast.left()).replace('.', ','), Console.T_CAST, WHITE);
            if (status == null) {
                status = Text.mm(cast.label());
            }
        }
        if (status == null) {
            status = notice(p, hf);
        }
        return c.build(status);
    }

    /** Rappel affiché sur la ligne de texte quand rien d'autre ne l'occupe (null sinon). */
    private Component notice(Player p, double hf) {
        if (hf <= 0.3) {
            int meds = plugin.meds().count(p);
            return Text.mm(meds > 0 ? "<#E74C3C>Blessé</#E74C3C> <#5A6270>· <#8A919B>" + meds + " soin" + (meds > 1 ? "s" : "") + " dans le sac"
                    : "<#E74C3C>Blessé</#E74C3C> <#5A6270>· <#8A919B>aucun soin dans le sac");
        }
        if (p.getFoodLevel() <= 6) {
            return Text.mm("<#E6A23C>Affamé</#E6A23C> <#5A6270>· <#8A919B>mange pour garder ta vitesse");
        }
        return null;
    }

    /** Tenue portée : celle du plastron, sinon de n'importe quelle pièce. */
    private Armors.SetDef outfit(Player p) {
        ItemStack[] order = {p.getInventory().getChestplate(), p.getInventory().getHelmet(), p.getInventory().getLeggings(),
                p.getInventory().getBoots()};
        for (ItemStack it : order) {
            String key = plugin.armors().keyOf(it);
            if (key != null) {
                int sep = key.indexOf(':');
                Armors.SetDef d = plugin.armors().def(sep < 0 ? key : key.substring(0, sep));
                if (d != null) {
                    return d;
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ Messages

    /** Message temporaire sur la ligne de texte de la console (prioritaire sur les rappels). */
    public void flash(Player p, Component msg, int ticks) {
        UUID id = p.getUniqueId();
        holdUntil.put(id, System.currentTimeMillis() + ticks * 50L);
        flashText.put(id, msg);
        p.sendActionBar(console(p, msg));
    }

    // ------------------------------------------------------------------ Maj + F : fiche de l'opérateur

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        if (!p.isSneaking()) {
            return;
        }
        // Annulé ici : ni rechargement (GunListener) ni changement de zoom de la carte (MiniMap).
        e.setCancelled(true);
        plugin.phoneMenu().open(p, PhoneMenu.Screen.PROFIL);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        holdUntil.remove(e.getPlayer().getUniqueId());
        flashText.remove(e.getPlayer().getUniqueId());
        panel.quit(e.getPlayer());
    }

    // ------------------------------------------------------------------ Formats

    static double maxHealth(Player p) {
        AttributeInstance a = p.getAttribute(Attribute.MAX_HEALTH);
        return a == null ? 20 : a.getValue();
    }

    private static double frac(double v, double max) {
        return max <= 0 ? 0 : Math.max(0, Math.min(1, v / max));
    }

    /** Pixels remplis d'une barre de {@code w} pixels (au moins 1 tant que la valeur n'est pas nulle). */
    private static int bar(double f, int w, boolean nonZero) {
        int px = (int) Math.round(Math.max(0, Math.min(1, f)) * w);
        return nonZero && px == 0 ? 1 : px;
    }

    /** 12.345 : points des milliers (étroits dans la police miniature). */
    static String thousands(long v) {
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

    static TextColor color(String hex, TextColor def) {
        TextColor c = hex == null ? null : TextColor.fromHexString(hex);
        return c == null ? def : c;
    }
}
