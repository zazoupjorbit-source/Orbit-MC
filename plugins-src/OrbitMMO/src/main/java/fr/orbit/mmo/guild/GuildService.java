package fr.orbit.mmo.guild;

import fr.orbit.mmo.OrbitMMO;
import fr.orbit.mmo.classes.Faction;
import fr.orbit.mmo.guild.Guild.Member;
import fr.orbit.mmo.guild.Guild.Rank;
import fr.orbit.mmo.player.PlayerData;
import fr.orbit.mmo.util.Text;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.regex.Pattern;

/**
 * Les guildes. Règles choisies par Zazou le 23/11/2026 : une seule faction par guilde, 20 membres, trois grades
 * (chef, officiers, membres), 1 000 or pour en fonder une, une banque en or, et le Pilier des Astres comme terrain de
 * jeu : chaque guilde y ouvre ses propres étages, les guildes sont classées par étage franchi et la première du
 * serveur à franchir un étage touche une prime.
 *
 * <p>Tout est gardé dans {@code plugins/OrbitMMO/guildes.yml}, réécrit après chaque changement.</p>
 */
public final class GuildService implements Listener {

    /** Couleur des guildes dans le chat et les menus. */
    public static final String HEX = "#6FE3C1";
    private static final String TAG = "<" + HEX + ">[Guilde]</" + HEX + "> ";
    private static final String ERR = "<#FF5C5C>";
    /** 3 à 20 caractères : lettres, chiffres, espaces, apostrophes et tirets ; ni au début ni à la fin pour les trois derniers. */
    private static final Pattern NAME = Pattern.compile("[\\p{L}0-9][\\p{L}0-9 '\\-]{1,18}[\\p{L}0-9]");
    private static final long INVITE_MS = 120_000L;

    /** Première guilde du serveur à avoir franchi un étage. */
    public record First(String guildId, String guildName, long at) {
    }

    private record Invite(String guildId, String from, long expires) {
    }

    private final OrbitMMO plugin;
    private final File file;
    private final Map<String, Guild> guilds = new LinkedHashMap<>();
    private final Map<UUID, Guild> byMember = new HashMap<>();
    private final Map<UUID, Invite> invites = new HashMap<>();
    private final Map<Integer, First> firsts = new TreeMap<>();
    /** Or dû à un joueur hors ligne (banque d'une guilde dissoute), versé à sa connexion. */
    private final Map<UUID, Long> owed = new HashMap<>();
    /** Joueurs dont tout ce qu'ils écrivent part dans le canal de guilde (lu aussi depuis le thread du chat). */
    private final Set<UUID> chatMode = ConcurrentHashMap.newKeySet();

    public GuildService(OrbitMMO plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "guildes.yml");
        load();
    }

    // ------------------------------------------------------------------ Réglages

    public long createCost() {
        return Math.max(0, plugin.getConfig().getLong("guildes.cout-creation", 1000));
    }

    public int maxMembers() {
        return Math.max(2, plugin.getConfig().getInt("guildes.max-membres", 20));
    }

    /** Prime de premier passage de l'étage {@code floor}, versée dans la banque de la guilde. */
    public long prime(int floor) {
        return Math.max(0, plugin.getConfig().getLong("guildes.prime-base", 500)
                + plugin.getConfig().getLong("guildes.prime-par-etage", 250) * floor);
    }

    // ------------------------------------------------------------------ Lecture

    public Guild of(UUID player) {
        return byMember.get(player);
    }

    public Guild find(String name) {
        return name == null ? null : guilds.get(key(name));
    }

    public Collection<Guild> all() {
        return Collections.unmodifiableCollection(guilds.values());
    }

    public List<String> names() {
        List<String> out = new ArrayList<>();
        for (Guild g : guilds.values()) {
            out.add(g.name);
        }
        return out;
    }

    /** Classement : l'étage le plus haut d'abord ; à égalité, la guilde arrivée la première. */
    public List<Guild> ranking() {
        List<Guild> out = new ArrayList<>(guilds.values());
        out.sort(Comparator.comparingInt((Guild g) -> -g.towerCleared)
                .thenComparingLong(g -> g.towerClearedAt <= 0 ? Long.MAX_VALUE : g.towerClearedAt)
                .thenComparingLong(g -> g.created));
        return out;
    }

    /** Place de la guilde au classement (1 = première). */
    public int place(Guild g) {
        return ranking().indexOf(g) + 1;
    }

    /** « 1re », « 2e », « 3e »… (sans exposant : la police du pack ne les dessine pas). */
    public static String ordinal(int n) {
        return n == 1 ? "1re" : n + "e";
    }

    public First first(int floor) {
        return firsts.get(floor);
    }

    public Map<Integer, First> firsts() {
        return Collections.unmodifiableMap(firsts);
    }

    /** Guilde qui a invité ce joueur, tant que l'invitation tient. */
    public Guild invitedTo(UUID player) {
        Invite inv = invites.get(player);
        if (inv == null || inv.expires < System.currentTimeMillis()) {
            return null;
        }
        return guilds.get(inv.guildId);
    }

    public boolean chatMode(UUID player) {
        return chatMode.contains(player);
    }

    public int online(Guild g) {
        int n = 0;
        for (UUID id : g.members.keySet()) {
            if (Bukkit.getPlayer(id) != null) {
                n++;
            }
        }
        return n;
    }

    /** Clé d'une guilde : son nom sans accent ni majuscule, les séparateurs réduits à un tiret. */
    public static String key(String name) {
        String flat = Normalizer.normalize(name.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return flat.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}0-9]+", "-").replaceAll("^-+|-+$", "");
    }

    // ------------------------------------------------------------------ Vie de la guilde

    public void create(Player p, String raw) {
        PlayerData d = plugin.players().get(p);
        if (!plugin.progression().valid(d)) {
            Text.msg(p, ERR + "Créez d'abord votre personnage.");
            return;
        }
        Guild current = byMember.get(p.getUniqueId());
        if (current != null) {
            Text.msg(p, ERR + "Vous êtes déjà dans la guilde " + Text.esc(current.name) + ".");
            return;
        }
        String name = raw.trim().replaceAll("\\s+", " ");
        if (!NAME.matcher(name).matches()) {
            Text.msg(p, ERR + "Nom de guilde : 3 à 20 caractères (lettres, chiffres, espaces, apostrophes, tirets).");
            return;
        }
        String id = key(name);
        if (id.length() < 3 || guilds.containsKey(id)) {
            Text.msg(p, ERR + (id.length() < 3 ? "Ce nom est trop court." : "Une guilde porte déjà ce nom."));
            return;
        }
        long cost = createCost();
        if (d.gold < cost) {
            Text.msg(p, ERR + "Fonder une guilde coûte " + Text.num(cost) + " or (vous en avez " + Text.num(d.gold) + ").");
            return;
        }
        d.gold -= cost;
        long now = System.currentTimeMillis();
        Guild g = new Guild(id, name, d.faction, now);
        g.members.put(p.getUniqueId(), new Member(p.getUniqueId(), p.getName(), Rank.CHEF, now));
        guilds.put(id, g);
        byMember.put(p.getUniqueId(), g);
        invites.remove(p.getUniqueId());
        save();
        plugin.players().save(d);
        Bukkit.broadcast(Text.mm(Text.PREFIX + "<#FFD66E>✦</#FFD66E> <white>" + Text.esc(p.getName()) + "</white> fonde la guilde "
                + g.tag() + " <#8C96A3>(" + d.faction.tag() + "<#8C96A3>)."));
        Text.msg(p, "<#8C96A3>Invitez vos compagnons : <white>/guilde inviter <joueur></white>. Menu : <white>/guilde</white>.");
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
    }

    public void invite(Player from, Player target) {
        Guild g = byMember.get(from.getUniqueId());
        if (g == null) {
            Text.msg(from, ERR + "Vous n'êtes dans aucune guilde.");
            return;
        }
        if (g.rank(from.getUniqueId()) == Rank.MEMBRE) {
            Text.msg(from, ERR + "Seuls le chef et les officiers peuvent inviter.");
            return;
        }
        if (from.equals(target)) {
            Text.msg(from, ERR + "Vous ne pouvez pas vous inviter vous-même.");
            return;
        }
        if (g.size() >= maxMembers()) {
            Text.msg(from, ERR + "La guilde est complète (" + maxMembers() + " membres).");
            return;
        }
        String problem = cannotJoin(g, target);
        if (problem != null) {
            Text.msg(from, ERR + problem);
            return;
        }
        invites.put(target.getUniqueId(), new Invite(g.id, from.getName(), System.currentTimeMillis() + INVITE_MS));
        Text.msg(from, "Invitation envoyée à <white>" + Text.esc(target.getName()) + "</white>.");
        Text.msg(target, "<white>" + Text.esc(from.getName()) + "</white> vous invite dans la guilde " + g.tag() + ". "
                + "<click:run_command:'/guilde accepter'><#5EE89A><b>[Accepter]</b></#5EE89A></click> "
                + "<click:run_command:'/guilde refuser'><#FF5C5C><b>[Refuser]</b></#FF5C5C></click> <#8C96A3>(2 min)");
        target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.4f);
    }

    /** Raison pour laquelle ce joueur ne peut pas entrer dans la guilde, ou null. */
    private String cannotJoin(Guild g, Player target) {
        PlayerData td = plugin.players().loaded(target.getUniqueId());
        if (td == null || !plugin.progression().valid(td)) {
            return Text.esc(target.getName()) + " n'a pas encore de personnage.";
        }
        if (td.faction != g.faction) {
            return Text.esc(target.getName()) + " est de la faction " + td.faction.label + " : une guilde ne réunit qu'une faction.";
        }
        Guild other = byMember.get(target.getUniqueId());
        if (other != null) {
            return Text.esc(target.getName()) + " est déjà dans " + (other == g ? "la guilde." : "une autre guilde.");
        }
        return null;
    }

    public void accept(Player p) {
        Invite inv = invites.remove(p.getUniqueId());
        Guild g = inv == null || inv.expires < System.currentTimeMillis() ? null : guilds.get(inv.guildId);
        if (g == null) {
            Text.msg(p, ERR + "Aucune invitation de guilde en attente.");
            return;
        }
        if (byMember.containsKey(p.getUniqueId())) {
            Text.msg(p, ERR + "Quittez d'abord votre guilde actuelle.");
            return;
        }
        if (g.size() >= maxMembers()) {
            Text.msg(p, ERR + "Cette guilde est complète.");
            return;
        }
        PlayerData d = plugin.players().get(p);
        if (!plugin.progression().valid(d) || d.faction != g.faction) {
            Text.msg(p, ERR + "Cette guilde est réservée à la faction " + g.faction.label + ".");
            return;
        }
        g.members.put(p.getUniqueId(), new Member(p.getUniqueId(), p.getName(), Rank.MEMBRE, System.currentTimeMillis()));
        byMember.put(p.getUniqueId(), g);
        save();
        broadcast(g, "<white>" + Text.esc(p.getName()) + "</white> rejoint la guilde.");
        if (g.towerCleared > 0) {
            Text.msg(p, "<#8C96A3>Le Pilier des Astres vous est ouvert jusqu'à l'étage <white>"
                    + Math.min(plugin.floors().maxFloor(), g.towerCleared + 1) + "</white>, comme à toute la guilde.");
        }
    }

    public void decline(Player p) {
        Invite inv = invites.remove(p.getUniqueId());
        Guild g = inv == null ? null : guilds.get(inv.guildId);
        if (g == null || inv.expires < System.currentTimeMillis()) {
            Text.msg(p, ERR + "Aucune invitation de guilde en attente.");
            return;
        }
        Text.msg(p, "Invitation de " + g.tag() + " refusée.");
        Player from = Bukkit.getPlayerExact(inv.from);
        if (from != null) {
            Text.msg(from, "<white>" + Text.esc(p.getName()) + "</white> a refusé l'invitation.");
        }
    }

    public void leave(Player p) {
        Guild g = byMember.get(p.getUniqueId());
        if (g == null) {
            Text.msg(p, ERR + "Vous n'êtes dans aucune guilde.");
            return;
        }
        if (g.rank(p.getUniqueId()) == Rank.CHEF) {
            if (g.size() > 1) {
                Text.msg(p, ERR + "Vous êtes le chef : donnez d'abord votre place (<white>/guilde chef <joueur></white>"
                        + ERR + ") ou dissolvez la guilde (<white>/guilde dissoudre</white>" + ERR + ").");
                return;
            }
            dissolve(g, p.getName());
            return;
        }
        remove(g, p.getUniqueId());
        save();
        Text.msg(p, "Vous avez quitté la guilde " + g.tag() + ".");
        broadcast(g, "<white>" + Text.esc(p.getName()) + "</white> a quitté la guilde.");
    }

    public void kick(Player by, String name) {
        Guild g = byMember.get(by.getUniqueId());
        Member target = target(by, g, name);
        if (target == null) {
            return;
        }
        Rank mine = g.rank(by.getUniqueId());
        if (mine == Rank.MEMBRE || target.rank.ordinal() <= mine.ordinal()) {
            Text.msg(by, ERR + (mine == Rank.MEMBRE ? "Seuls le chef et les officiers peuvent exclure."
                    : "Vous ne pouvez exclure que les grades en dessous du vôtre."));
            return;
        }
        remove(g, target.id);
        save();
        broadcast(g, "<white>" + Text.esc(target.name) + "</white> est exclu de la guilde par <white>" + Text.esc(by.getName()) + "</white>.");
        Player t = Bukkit.getPlayer(target.id);
        if (t != null) {
            Text.msg(t, "<#FF8A3B>Vous avez été exclu de la guilde " + g.tag() + "<#FF8A3B>.");
        }
    }

    public void promote(Player by, String name) {
        Guild g = byMember.get(by.getUniqueId());
        Member target = target(by, g, name);
        if (target == null || !chief(by, g)) {
            return;
        }
        if (target.rank != Rank.MEMBRE) {
            Text.msg(by, ERR + Text.esc(target.name) + " est déjà " + target.rank.label.toLowerCase(Locale.ROOT)
                    + ". Pour lui donner votre place : <white>/guilde chef " + Text.esc(target.name));
            return;
        }
        target.rank = Rank.OFFICIER;
        save();
        broadcast(g, "<white>" + Text.esc(target.name) + "</white> devient " + Rank.OFFICIER.tag() + ".");
    }

    public void demote(Player by, String name) {
        Guild g = byMember.get(by.getUniqueId());
        Member target = target(by, g, name);
        if (target == null || !chief(by, g)) {
            return;
        }
        if (target.rank != Rank.OFFICIER) {
            Text.msg(by, ERR + Text.esc(target.name) + " n'est pas officier.");
            return;
        }
        target.rank = Rank.MEMBRE;
        save();
        broadcast(g, "<white>" + Text.esc(target.name) + "</white> redevient " + Rank.MEMBRE.tag() + ".");
    }

    /** Le chef donne sa place ; il devient officier. */
    public void transfer(Player by, String name) {
        Guild g = byMember.get(by.getUniqueId());
        Member target = target(by, g, name);
        if (target == null || !chief(by, g)) {
            return;
        }
        g.members.get(by.getUniqueId()).rank = Rank.OFFICIER;
        target.rank = Rank.CHEF;
        save();
        broadcast(g, "<white>" + Text.esc(target.name) + "</white> est le nouveau " + Rank.CHEF.tag() + " de la guilde.");
    }

    public void disband(Player by) {
        Guild g = byMember.get(by.getUniqueId());
        if (g == null) {
            Text.msg(by, ERR + "Vous n'êtes dans aucune guilde.");
            return;
        }
        if (!chief(by, g)) {
            return;
        }
        dissolve(g, by.getName());
    }

    /** Membre visé par une action, retrouvé par son nom ; null (avec un message) si l'action n'a pas de sens. */
    private Member target(Player by, Guild g, String name) {
        if (g == null) {
            Text.msg(by, ERR + "Vous n'êtes dans aucune guilde.");
            return null;
        }
        Member m = g.byName(name);
        if (m == null) {
            Text.msg(by, ERR + Text.esc(name) + " n'est pas dans votre guilde.");
            return null;
        }
        if (m.id.equals(by.getUniqueId())) {
            Text.msg(by, ERR + "Cette action ne s'applique pas à vous-même.");
            return null;
        }
        return m;
    }

    private boolean chief(Player by, Guild g) {
        if (g.rank(by.getUniqueId()) == Rank.CHEF) {
            return true;
        }
        Text.msg(by, ERR + "Seul le chef de la guilde peut faire cela.");
        return false;
    }

    private void remove(Guild g, UUID id) {
        g.members.remove(id);
        byMember.remove(id);
        chatMode.remove(id);
    }

    /** Dissout la guilde ; l'or de la banque revient au chef. */
    private void dissolve(Guild g, String by) {
        Member chef = g.leader();
        long bank = g.bank;
        broadcast(g, "<#FF8A3B>La guilde est dissoute par <white>" + Text.esc(by) + "</white>.");
        for (UUID id : new ArrayList<>(g.members.keySet())) {
            byMember.remove(id);
            chatMode.remove(id);
        }
        g.members.clear();
        g.bank = 0;
        guilds.remove(g.id);
        invites.values().removeIf(i -> i.guildId.equals(g.id));
        if (bank > 0 && chef != null) {
            pay(chef.id, bank, "banque de la guilde " + g.name);
        }
        save();
        Bukkit.broadcast(Text.mm(Text.PREFIX + "<#8C96A3>La guilde " + g.tag() + "<#8C96A3> est dissoute."));
    }

    /** Verse de l'or à un joueur, tout de suite s'il est en ligne, sinon à sa prochaine connexion. */
    private void pay(UUID id, long amount, String why) {
        Player p = Bukkit.getPlayer(id);
        PlayerData d = p == null ? null : plugin.players().loaded(id);
        if (d == null) {
            owed.merge(id, amount, Long::sum);
            return;
        }
        d.gold += amount;
        plugin.players().save(d);
        Text.msg(p, "<#FFD66E>+" + Text.num(amount) + " or</#FFD66E> <#8C96A3>(" + Text.esc(why) + ").");
    }

    /** Personnage effacé (nouvelle partie) : il quitte sa guilde ; s'il en était le chef, le plus ancien gradé le remplace. */
    public void forget(UUID id) {
        invites.remove(id);
        Guild g = byMember.get(id);
        if (g == null) {
            return;
        }
        Member gone = g.members.get(id);
        if (g.size() <= 1) {
            dissolve(g, gone.name);
            return;
        }
        remove(g, id);
        if (gone.rank == Rank.CHEF) {
            Member heir = null;
            for (Member m : g.members.values()) {
                if (heir == null || m.rank.ordinal() < heir.rank.ordinal()
                        || (m.rank == heir.rank && m.since < heir.since)) {
                    heir = m;
                }
            }
            heir.rank = Rank.CHEF;
            broadcast(g, "<white>" + Text.esc(heir.name) + "</white> est le nouveau " + Rank.CHEF.tag() + " de la guilde.");
        }
        save();
        broadcast(g, "<white>" + Text.esc(gone.name) + "</white> a quitté la guilde.");
    }

    // ------------------------------------------------------------------ Banque

    /** {@code amount} ≤ 0 : tout l'or du joueur. */
    public void deposit(Player p, long amount) {
        Guild g = byMember.get(p.getUniqueId());
        if (g == null) {
            Text.msg(p, ERR + "Vous n'êtes dans aucune guilde.");
            return;
        }
        PlayerData d = plugin.players().get(p);
        long n = amount <= 0 ? d.gold : amount;
        if (n <= 0 || n > d.gold) {
            Text.msg(p, ERR + "Vous n'avez pas cet or (" + Text.num(d.gold) + " or sur vous).");
            return;
        }
        d.gold -= n;
        g.bank += n;
        save();
        plugin.players().save(d);
        broadcast(g, "<white>" + Text.esc(p.getName()) + "</white> dépose <#FFD66E>" + Text.num(n) + " or</#FFD66E> <#8C96A3>(banque : "
                + Text.num(g.bank) + " or).");
    }

    /** {@code amount} ≤ 0 : toute la banque. Réservé au chef et aux officiers. */
    public void withdraw(Player p, long amount) {
        Guild g = byMember.get(p.getUniqueId());
        if (g == null) {
            Text.msg(p, ERR + "Vous n'êtes dans aucune guilde.");
            return;
        }
        if (g.rank(p.getUniqueId()) == Rank.MEMBRE) {
            Text.msg(p, ERR + "Seuls le chef et les officiers peuvent retirer de l'or.");
            return;
        }
        long n = amount <= 0 ? g.bank : amount;
        if (n <= 0 || n > g.bank) {
            Text.msg(p, ERR + "La banque ne contient que " + Text.num(g.bank) + " or.");
            return;
        }
        PlayerData d = plugin.players().get(p);
        g.bank -= n;
        d.gold += n;
        save();
        plugin.players().save(d);
        broadcast(g, "<white>" + Text.esc(p.getName()) + "</white> retire <#FFD66E>" + Text.num(n) + " or</#FFD66E> <#8C96A3>(banque : "
                + Text.num(g.bank) + " or).");
    }

    // ------------------------------------------------------------------ Canal de discussion

    public void broadcast(Guild g, String mini) {
        for (UUID id : g.members.keySet()) {
            Player m = Bukkit.getPlayer(id);
            if (m != null) {
                Text.msg(m, TAG + mini);
            }
        }
    }

    /** Message d'un membre dans le canal de sa guilde. */
    public void chat(Player p, String message) {
        Guild g = byMember.get(p.getUniqueId());
        if (g == null) {
            chatMode.remove(p.getUniqueId());
            Text.msg(p, ERR + "Vous n'êtes dans aucune guilde.");
            return;
        }
        String text = message.trim();
        if (text.isEmpty()) {
            return;
        }
        Rank rank = g.rank(p.getUniqueId());
        var line = Text.mm(TAG + "<" + rank.hex + ">" + Text.esc(p.getName()) + "</" + rank.hex + "> <#5A6270>»</#5A6270> <white>" + Text.esc(text));
        for (UUID id : g.members.keySet()) {
            Player m = Bukkit.getPlayer(id);
            if (m != null) {
                m.sendMessage(line);
            }
        }
        plugin.getLogger().info("[Guilde " + g.name + "] " + p.getName() + " : " + text);
    }

    /** Bascule : tout ce que le joueur écrit part dans le canal de guilde, ou revient dans le chat général. */
    public void toggleChat(Player p) {
        if (byMember.get(p.getUniqueId()) == null) {
            Text.msg(p, ERR + "Vous n'êtes dans aucune guilde.");
            return;
        }
        if (chatMode.remove(p.getUniqueId())) {
            Text.msg(p, "Vous écrivez de nouveau dans le <white>chat général</white>. <#8C96A3>Un message à la guilde : /gc <message>");
        } else {
            chatMode.add(p.getUniqueId());
            Text.msg(p, "Vous écrivez maintenant dans le <" + HEX + ">canal de guilde</" + HEX + ">. <#8C96A3>/guilde chat pour revenir au chat général.");
        }
    }

    /** Après l'hôtel des ventes (priorité la plus basse), qui lit un prix tapé dans le chat. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        if (!chatMode.contains(p.getUniqueId())) {
            return;
        }
        e.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(e.message());
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline()) {
                chat(p, text);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) {
                return;
            }
            Long due = owed.remove(p.getUniqueId());
            if (due != null && due > 0) {
                PlayerData d = plugin.players().get(p);
                d.gold += due;
                plugin.players().save(d);
                save();
                Text.msg(p, "<#FFD66E>+" + Text.num(due) + " or</#FFD66E> <#8C96A3>(banque de votre ancienne guilde).");
            }
            Guild g = byMember.get(p.getUniqueId());
            if (g == null) {
                return;
            }
            Member m = g.members.get(p.getUniqueId());
            if (!p.getName().equals(m.name)) {
                m.name = p.getName();
                save();
            }
            Text.msg(p, TAG + g.tag() + " <#5A6270>·</#5A6270> " + online(g) + " membre(s) en ligne <#5A6270>·</#5A6270> étage franchi : <white>"
                    + (g.towerCleared > 0 ? g.towerCleared : "aucun") + "</white> <#5A6270>·</#5A6270> /guilde");
        }, 40L);
    }

    /** Déconnexion. */
    public void quit(Player p) {
        invites.remove(p.getUniqueId());
        chatMode.remove(p.getUniqueId());
    }

    // ------------------------------------------------------------------ Le Pilier des Astres

    /**
     * Un boss d'étage vient de tomber. Chaque vainqueur garde l'étage pour lui-même ; sa guilde le franchit si c'est
     * bien son étage suivant (une guilde gravit le Pilier dans l'ordre) ; la première guilde du serveur à franchir cet
     * étage touche la prime de premier passage dans sa banque.
     */
    public void floorCleared(int floor, Collection<Player> victors) {
        int max = plugin.floors().maxFloor();
        int base = plugin.floors().base();
        Map<Player, Integer> before = new LinkedHashMap<>();
        Set<Guild> teams = new LinkedHashSet<>();
        for (Player p : victors) {
            PlayerData d = plugin.players().get(p);
            before.put(p, plugin.floors().cleared(d));
            if (floor > d.towerCleared) {
                d.towerCleared = floor;
            }
            Guild g = byMember.get(p.getUniqueId());
            if (g != null) {
                teams.add(g);
            }
        }
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (Guild g : teams) {
            if (floor <= g.towerCleared) {
                continue;
            }
            int next = Math.max(g.towerCleared, base) + 1;
            if (floor > next) {
                broadcast(g, "<#8C96A3>Étage " + floor + " vaincu par des membres, mais la guilde doit d'abord franchir l'étage <white>"
                        + next + "</white> : le Pilier se gravit dans l'ordre.");
                continue;
            }
            g.towerCleared = floor;
            g.towerClearedAt = now;
            changed = true;
            broadcast(g, "<#FFD66E><b>Étage " + floor + " franchi !</b></#FFD66E> "
                    + (floor < max ? "L'étage " + (floor + 1) + " est ouvert à toute la guilde." : "La guilde a atteint le sommet du Pilier des Astres.")
                    + " <#8C96A3>Classement : " + ordinal(place(g)) + " sur " + guilds.size() + ".");
            if (!firsts.containsKey(floor)) {
                long prime = prime(floor);
                firsts.put(floor, new First(g.id, g.name, now));
                g.firsts++;
                g.bank += prime;
                Bukkit.broadcast(Text.mm(Text.PREFIX + "<#FFD66E>✦ Premier passage !</#FFD66E> La guilde " + g.tag()
                        + " est la première à franchir l'<white>étage " + floor + "</white> du Pilier des Astres."
                        + (prime > 0 ? " <#8C96A3>Prime : <#FFD66E>" + Text.num(prime) + " or</#FFD66E> dans sa banque." : "")));
                for (Player o : Bukkit.getOnlinePlayers()) {
                    o.playSound(o.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.35f, 1.6f);
                }
            }
        }
        for (Map.Entry<Player, Integer> e : before.entrySet()) {
            Player p = e.getKey();
            PlayerData d = plugin.players().get(p);
            int after = plugin.floors().cleared(d);
            if (after > e.getValue() && after < max) {
                Text.msg(p, "<#FFD66E><b>L'étage " + (after + 1) + " vous est ouvert !</b> <#D7DCE2>Passez la porte de téléportation de la ville.");
            }
            if (byMember.get(p.getUniqueId()) == null && after > e.getValue()) {
                Text.msg(p, "<#8C96A3>Sans guilde, vous gravissez le Pilier pour vous seul. Une guilde partage ses étages, son classement et ses primes : <white>/guilde");
            }
            plugin.players().save(d);
        }
        if (changed) {
            save();
        }
    }

    // ------------------------------------------------------------------ Affichage et administration

    public void info(CommandSender to, Guild g) {
        Member chef = g.leader();
        Text.msg(to, g.tag() + " <#8C96A3>· " + g.faction.tag() + " <#8C96A3>· " + g.size() + "/" + maxMembers() + " membres ("
                + online(g) + " en ligne)");
        Text.msg(to, "<#8C96A3>Chef : <white>" + (chef == null ? "—" : Text.esc(chef.name)) + "</white> · Banque : <#FFD66E>" + Text.num(g.bank) + " or");
        Text.msg(to, "<#8C96A3>Pilier des Astres : étage franchi <white>" + (g.towerCleared > 0 ? g.towerCleared : "aucun") + "</white> · "
                + ordinal(place(g)) + " au classement · " + g.firsts + " premier(s) passage(s)");
        StringBuilder sb = new StringBuilder("<#8C96A3>Membres : ");
        boolean firstName = true;
        for (Member m : g.sorted()) {
            if (!firstName) {
                sb.append("<#5A6270>, ");
            }
            firstName = false;
            sb.append("<").append(m.rank.hex).append(">").append(Text.esc(String.valueOf(m.name))).append("</").append(m.rank.hex).append(">");
        }
        Text.msg(to, sb.toString());
    }

    public void adminDisband(CommandSender by, Guild g) {
        dissolve(g, by.getName());
        Text.msg(by, "Guilde " + Text.esc(g.name) + " dissoute.");
    }

    /** Pose l'étage franchi d'une guilde (essais) ; ne touche pas aux premiers passages. */
    public void adminFloor(CommandSender by, Guild g, int floor) {
        g.towerCleared = Math.max(0, Math.min(plugin.floors().maxFloor(), floor));
        g.towerClearedAt = g.towerCleared > 0 ? System.currentTimeMillis() : 0;
        save();
        Text.msg(by, "Guilde " + Text.esc(g.name) + " : étage franchi = " + g.towerCleared + ".");
    }

    public void adminBank(CommandSender by, Guild g, long delta) {
        g.bank = Math.max(0, g.bank + delta);
        save();
        Text.msg(by, "Guilde " + Text.esc(g.name) + " : banque = " + Text.num(g.bank) + " or.");
    }

    public void reload(CommandSender by) {
        load();
        Text.msg(by, guilds.size() + " guilde(s) relue(s) depuis guildes.yml.");
    }

    // ------------------------------------------------------------------ Fichier

    private void load() {
        guilds.clear();
        byMember.clear();
        firsts.clear();
        owed.clear();
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection gs = y.getConfigurationSection("guildes");
        if (gs != null) {
            for (String id : gs.getKeys(false)) {
                ConfigurationSection s = gs.getConfigurationSection(id);
                Faction faction = s == null ? null : Faction.byId(s.getString("faction"));
                ConfigurationSection ms = s == null ? null : s.getConfigurationSection("membres");
                if (faction == null || ms == null) {
                    plugin.getLogger().warning("guildes.yml : guilde « " + id + " » illisible, ignorée.");
                    continue;
                }
                Guild g = new Guild(id, s.getString("nom", id), faction, s.getLong("creee"));
                g.bank = Math.max(0, s.getLong("banque"));
                g.towerCleared = Math.max(0, s.getInt("etage"));
                g.towerClearedAt = s.getLong("etage-le");
                g.firsts = Math.max(0, s.getInt("premiers"));
                for (String uid : ms.getKeys(false)) {
                    try {
                        UUID u = UUID.fromString(uid);
                        if (byMember.containsKey(u)) {
                            continue;
                        }
                        g.members.put(u, new Member(u, ms.getString(uid + ".nom", "?"), Rank.byId(ms.getString(uid + ".grade")),
                                ms.getLong(uid + ".depuis")));
                    } catch (IllegalArgumentException ex) {
                        plugin.getLogger().warning("guildes.yml : membre « " + uid + " » de " + id + " ignoré.");
                    }
                }
                if (g.members.isEmpty()) {
                    continue;
                }
                if (g.leader() == null) {
                    // Fichier retouché à la main sans chef : le premier membre le devient.
                    g.members.values().iterator().next().rank = Rank.CHEF;
                }
                guilds.put(id, g);
                for (UUID u : g.members.keySet()) {
                    byMember.put(u, g);
                }
            }
        }
        ConfigurationSection fs = y.getConfigurationSection("premiers-passages");
        if (fs != null) {
            for (String k : fs.getKeys(false)) {
                try {
                    firsts.put(Integer.parseInt(k), new First(fs.getString(k + ".guilde", ""), fs.getString(k + ".nom", "?"), fs.getLong(k + ".le")));
                } catch (NumberFormatException ignored) {
                    // clé invalide
                }
            }
        }
        ConfigurationSection os = y.getConfigurationSection("a-verser");
        if (os != null) {
            for (String k : os.getKeys(false)) {
                try {
                    owed.put(UUID.fromString(k), os.getLong(k));
                } catch (IllegalArgumentException ignored) {
                    // clé invalide
                }
            }
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Guild g : guilds.values()) {
            String base = "guildes." + g.id + ".";
            y.set(base + "nom", g.name);
            y.set(base + "faction", g.faction.id);
            y.set(base + "creee", g.created);
            y.set(base + "banque", g.bank);
            y.set(base + "etage", g.towerCleared);
            y.set(base + "etage-le", g.towerClearedAt);
            y.set(base + "premiers", g.firsts);
            for (Member m : g.members.values()) {
                String mb = base + "membres." + m.id + ".";
                y.set(mb + "nom", m.name);
                y.set(mb + "grade", m.rank.id);
                y.set(mb + "depuis", m.since);
            }
        }
        for (Map.Entry<Integer, First> e : firsts.entrySet()) {
            String base = "premiers-passages." + e.getKey() + ".";
            y.set(base + "guilde", e.getValue().guildId());
            y.set(base + "nom", e.getValue().guildName());
            y.set(base + "le", e.getValue().at());
        }
        for (Map.Entry<UUID, Long> e : owed.entrySet()) {
            if (e.getValue() > 0) {
                y.set("a-verser." + e.getKey(), e.getValue());
            }
        }
        try {
            Path target = file.toPath();
            Path tmp = target.resolveSibling(file.getName() + ".tmp");
            Files.writeString(tmp, y.saveToString(), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Échec de la sauvegarde de guildes.yml", ex);
        }
    }
}
