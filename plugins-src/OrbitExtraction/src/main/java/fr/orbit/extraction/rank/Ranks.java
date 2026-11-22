package fr.orbit.extraction.rank;

import fr.orbit.extraction.OrbitExtraction;
import fr.orbit.extraction.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Rangs du mode (Joueur, VIP, VIP+, ORBIT) : le rang d'un joueur est le plus haut dont il a la permission —
 * par défaut « group.&lt;rôle&gt; », que LuckPerms donne aux membres du rôle (et à ceux qui en héritent).
 * Un rang ouvre ses kits ({@link Kits}) et quelques avantages légers ; il se voit dans le tab (préfixe et couleur
 * du rôle LuckPerms, les mêmes que dans le chat).
 */
public final class Ranks implements Listener {

    /**
     * @param level          position dans la liste (0 = rang de base)
     * @param creditBonus    part ajoutée aux crédits gagnés (0.10 = +10 %)
     * @param shopDiscount   remise sur la boutique (0.05 = -5 %)
     * @param parcelCut      part retirée de l'attente entre deux colis
     */
    public record Rank(String id, String name, String color, String prefix, String permission, int level,
                       double creditBonus, double shopDiscount, double parcelCut) {

        /** « <#F5C542>VIP</#F5C542> » */
        public String tag() {
            return "<" + color + ">" + Text.esc(name) + "</" + color + ">";
        }
    }

    private static final Rank BASE = new Rank("joueur", "Joueur", "#9AA1AD", "", "", 0, 0, 0, 0);

    private final OrbitExtraction plugin;
    private final List<Rank> ranks = new ArrayList<>();
    private LuckPermsHook hook;
    private boolean hookTried;
    private String header = "";
    private String footer = "";
    private String shopLink = "";

    public Ranks(OrbitExtraction plugin) {
        this.plugin = plugin;
    }

    public void load(ConfigurationSection c) {
        ranks.clear();
        header = c == null ? "" : c.getString("tab.entete", "");
        footer = c == null ? "" : c.getString("tab.pied", "");
        shopLink = c == null ? "" : c.getString("lien-boutique", "");
        ConfigurationSection list = c == null ? null : c.getConfigurationSection("liste");
        if (list != null) {
            for (String id : list.getKeys(false)) {
                ConfigurationSection s = list.getConfigurationSection(id);
                if (s == null) {
                    continue;
                }
                String color = s.getString("couleur", "#9AA1AD");
                if (!color.matches("#[0-9A-Fa-f]{6}")) {
                    color = "#9AA1AD";
                }
                ranks.add(new Rank(id.toLowerCase(Locale.ROOT), s.getString("nom", id), color, s.getString("prefixe", ""),
                        s.getString("permission", ""), ranks.size(), pct(s, "bonus-credits"), pct(s, "remise-boutique"), pct(s, "recharge-colis")));
            }
        }
        if (ranks.isEmpty()) {
            ranks.add(BASE);
        }
        if (!hookTried) {
            hookTried = true;
            if (plugin.getServer().getPluginManager().getPlugin("LuckPerms") != null) {
                try {
                    hook = LuckPermsHook.create(plugin, this::refresh);
                } catch (Throwable t) {
                    plugin.getLogger().log(Level.WARNING, "LuckPerms présent mais inutilisable : tab sans les préfixes des rôles", t);
                }
            }
        }
        plugin.getLogger().info(ranks.size() + " rangs" + (hook != null ? " (préfixes et couleurs lus dans LuckPerms)." : " (LuckPerms absent : préfixes de config.yml)."));
        Bukkit.getOnlinePlayers().forEach(this::refresh);
    }

    private static double pct(ConfigurationSection s, String key) {
        return Math.max(0, Math.min(90, s.getDouble(key, 0))) / 100.0;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::listText, 40L, 100L);
    }

    public List<Rank> all() {
        return ranks;
    }

    public Rank base() {
        return ranks.get(0);
    }

    public Rank byId(String id) {
        for (Rank r : ranks) {
            if (r.id().equalsIgnoreCase(id)) {
                return r;
            }
        }
        return null;
    }

    public String shopLink() {
        return shopLink;
    }

    /** Le plus haut rang dont le joueur a la permission. */
    public Rank of(Player p) {
        for (int i = ranks.size() - 1; i > 0; i--) {
            Rank r = ranks.get(i);
            if (!r.permission().isEmpty() && p.hasPermission(r.permission())) {
                return r;
            }
        }
        return ranks.get(0);
    }

    /** Crédits gagnés, bonus du rang compris. */
    public long boosted(Player p, long amount) {
        return amount <= 0 ? amount : Math.round(amount * (1 + of(p).creditBonus()));
    }

    /** Prix en boutique, remise du rang déduite. */
    public long discounted(Player p, long price) {
        return Math.max(0, Math.round(price * (1 - of(p).shopDiscount())));
    }

    // ------------------------------------------------------------------ tab

    private void refresh(UUID id) {
        Player p = Bukkit.getPlayer(id);
        if (p != null) {
            refresh(p);
        }
    }

    /** Nom dans le tab : préfixe et couleur du rôle, rôles les plus lourds en haut de la liste. */
    public void refresh(Player p) {
        Rank r = of(p);
        String prefix = hook == null ? null : hook.prefix(p);
        String mini = prefix != null ? prefix : (r.prefix().isEmpty() ? "<" + r.color() + ">" : r.prefix());
        try {
            p.playerListName(Text.mm(mini + Text.esc(p.getName())));
        } catch (RuntimeException e) {
            p.playerListName(Text.mm("<" + r.color() + ">" + Text.esc(p.getName())));    // préfixe LuckPerms mal formé
        }
        p.setPlayerListOrder(hook != null ? hook.weight(p) * 10 + r.level() : r.level());
    }

    private void listText() {
        if (header.isEmpty() && footer.isEmpty()) {
            return;
        }
        int online = Bukkit.getOnlinePlayers().size(), raid = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (plugin.raid().inRaid(p)) {
                raid++;
            }
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            Rank r = of(p);
            p.sendPlayerListHeaderAndFooter(Text.mm(fill(header, online, raid, r)), Text.mm(fill(footer, online, raid, r)));
        }
    }

    private static String fill(String s, int online, int raid, Rank r) {
        return s.replace("{joueurs}", String.valueOf(online)).replace("{en-raid}", String.valueOf(raid))
                .replace("{planque}", String.valueOf(online - raid)).replace("{rang}", r.tag());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        refresh(p);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) {
                refresh(p);
                listText();
            }
        }, 20L);
    }
}
