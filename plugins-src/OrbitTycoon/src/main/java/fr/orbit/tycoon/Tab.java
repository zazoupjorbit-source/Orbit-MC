package fr.orbit.tycoon;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Liste des joueurs (touche Tab) : cadre HD du grade, pseudo, prestige total. Triée par grade puis prestige.
 * Les cadres et l'étoile sont des glyphes de la police orbittycoon:tab (tools/gen-tab.ps1), appelés par des clés
 * de langue du pack : sans le pack, le client affiche le repli (« %s » → grade en texte, « ✪ »).
 */
final class Tab implements Listener {
    private static final Key POLICE = Key.key("orbittycoon", "tab");
    private static final Key POLICE_VANILLA = Key.key("minecraft", "default");
    private static final ShadowColor SANS_OMBRE = ShadowColor.none();
    private static final ShadowColor OMBRE = ShadowColor.shadowColor(0xFF1E1E1E);
    private static final TextColor OR = TextColor.color(0xE0B864);
    private static final TextColor PSEUDO = TextColor.color(0xEEF2F7);
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final OrbitTycoon plugin;
    private final Monnaies monnaies;
    private final Hud hud;
    private final Map<UUID, Component> noms = new HashMap<>();
    private BukkitTask tache;

    Tab(OrbitTycoon plugin, Monnaies monnaies, Hud hud) {
        this.plugin = plugin;
        this.monnaies = monnaies;
        this.hud = hud;
    }

    void demarrer() {
        if (!plugin.getConfig().getBoolean("tab.actif", true)) return;
        tache = Bukkit.getScheduler().runTaskTimer(plugin, this::toutMettreAJour, 20L, 40L);
    }

    void arreter() {
        if (tache != null) tache.cancel();
        tache = null;
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playerListName(null);
            p.setPlayerListOrder(0);
            p.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
        }
        noms.clear();
    }

    @EventHandler
    public void connexion(PlayerJoinEvent e) {
        if (tache != null) Bukkit.getScheduler().runTaskLater(plugin, this::toutMettreAJour, 5L);
    }

    @EventHandler
    public void deconnexion(PlayerQuitEvent e) {
        noms.remove(e.getPlayer().getUniqueId());
        if (tache != null) Bukkit.getScheduler().runTask(plugin, this::toutMettreAJour);   // compteur du haut
    }

    private void toutMettreAJour() {
        int enLigne = Bukkit.getOnlinePlayers().size();
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            int prestige = monnaies.prestige(id);
            String groupe = groupe(p);

            Component nom = Component.text()
                    .append(cadre(groupe))
                    .append(Component.text(" " + p.getName(), PSEUDO))
                    .append(Component.text("  "))
                    .append(etoile())
                    .append(Component.text(" " + prestige, OR))
                    .build();
            if (!nom.equals(noms.get(id))) {
                p.playerListName(nom);
                noms.put(id, nom);
            }
            // Ordre : plus grand en haut. Grade d'abord, puis prestige.
            int ordre = poidsGrade(groupe) * 100_000 + Math.min(prestige, 99_999);
            if (p.getPlayerListOrder() != ordre) p.setPlayerListOrder(ordre);

            Integer rang = hud.rang(id);
            var res = new net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[]{
                    Placeholder.unparsed("en_ligne", Integer.toString(enLigne)),
                    Placeholder.unparsed("pluriel", enLigne > 1 ? "s" : ""),
                    Placeholder.unparsed("prestige", Integer.toString(prestige)),
                    Placeholder.unparsed("rang", rang == null ? "…" : "#" + rang)};
            p.sendPlayerListHeaderAndFooter(
                    MM.deserialize(plugin.getConfig().getString("tab.entete", ENTETE), res),
                    MM.deserialize(plugin.getConfig().getString("tab.pied", PIED), res));
        }
    }

    /** Cadre HD (glyphe du pack, blanc pour ne pas le teinter, sans ombre) ; repli : le grade en texte. */
    private Component cadre(String groupe) {
        String mini = groupe == null ? plugin.getConfig().getString("hud.grade-defaut", "<gray>Joueur")
                : plugin.getConfig().getString("hud.grades." + groupe, groupe);
        Component repli = MM.deserialize(mini).font(POLICE_VANILLA).shadowColor(OMBRE);
        return Component.translatable("orbittycoon.tab.grade." + (groupe == null ? "joueur" : groupe), "%s", repli)
                .font(POLICE).color(NamedTextColor.WHITE).shadowColor(SANS_OMBRE);
    }

    private Component etoile() {
        Component repli = Component.text("✪", OR).font(POLICE_VANILLA).shadowColor(OMBRE);
        return Component.translatable("orbittycoon.tab.prestige", "%s", repli)
                .font(POLICE).color(NamedTextColor.WHITE).shadowColor(SANS_OMBRE);
    }

    /** Groupe LuckPerms le plus haut parmi hud.grades (même règle que le HUD), null = joueur. */
    private String groupe(Player p) {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("hud.grades");
        if (s != null) for (String g : s.getKeys(false)) {
            String perm = "group." + g;
            if (p.isPermissionSet(perm) && p.hasPermission(perm)) return g;
        }
        return null;
    }

    private int poidsGrade(String groupe) {
        if (groupe == null) return 0;
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("hud.grades");
        if (s == null) return 0;
        List<String> cles = new ArrayList<>(s.getKeys(false));
        int i = cles.indexOf(groupe);
        return i < 0 ? 0 : cles.size() - i;
    }

    private static final String ENTETE = "\n<b><gradient:#62CFF4:#E0B864>✦  ORBIT MC  ✦</gradient></b>\n"
            + "<#3DDCB0>ᴛʏᴄᴏᴏɴ <dark_gray>• <gray><white><en_ligne></white> explorateur<pluriel> en orbite\n";
    private static final String PIED = "\n<gray>Ton prestige <#E0B864>✪ <prestige></#E0B864> <dark_gray>• <gray>Rang <white><rang>\n"
            + "<dark_gray>/navigateur pour voyager entre les zones\n";
}
