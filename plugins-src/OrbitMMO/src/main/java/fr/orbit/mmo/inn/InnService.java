package fr.orbit.mmo.inn;

import fr.orbit.mmo.Keys;
import fr.orbit.mmo.OrbitMMO;
import fr.orbit.mmo.ambient.Looks;
import fr.orbit.mmo.ambient.TownFolk;
import fr.orbit.mmo.continent.ContinentService.Town;
import fr.orbit.mmo.item.Items;
import fr.orbit.mmo.player.PlayerData;
import fr.orbit.mmo.util.Fx;
import fr.orbit.mmo.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Allay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Auberges et cristal de téléportation (23/11/2026).
 * <p>
 * Le cristal n'est plus à usage unique : chaque personnage en a un, lié à lui (il ne se jette pas, ne se met pas
 * dans un coffre ni en vente, et reste à la mort), avec une recharge entre deux usages (« cristal.recharge-secondes »).
 * Un aubergiste, dans chaque capitale et chaque village, le lie à son auberge : le cristal y ramène ensuite.
 * Sans auberge liée, il ramène comme avant à la capitale de sa faction (dans le Pilier : à la ville de l'étage).
 * <p>
 * Les aubergistes se posent avec « /continent aubergistes poser » : à côté du point « auberge » des villages et du
 * point d'arrivée (« spawn ») des capitales, sur un bloc où les pieds et la tête sont libres. Aucun bloc n'est touché.
 */
public final class InnService implements Listener {

    public static final String TAG = "innkeeper";
    /** Étiquette (scoreboard) de tous les aubergistes posés. */
    private static final String SCOREBOARD = "orbit_inn";
    private static final String HEX = "#E8C9A0";
    private static final long COMBAT_MS = 5000;

    private static final String[] LINES = {
            "Un lit, une soupe, et ton cristal saura toujours où te ramener.",
            "Ici, on ne demande pas d'où tu viens. Seulement si tu reviens.",
            "Lie ton cristal à mon auberge : la porte te sera toujours ouverte.",
            "Tu repars déjà ? Ton cristal connaît le chemin du retour."};
    private static final String[] LINES_EN = {
            "A bed, a bowl of soup, and your crystal will always know where to bring you back.",
            "Here, nobody asks where you come from. Only whether you'll come back.",
            "Bind your crystal to my inn: the door will always be open to you.",
            "Leaving already? Your crystal knows the way back."};

    private final OrbitMMO plugin;
    /** Joueurs dont le cristal recharge : avertis quand il est prêt. */
    private final Set<UUID> charging = new HashSet<>();
    private final Map<UUID, Long> chatCooldown = new HashMap<>();
    private PlaceJob job;

    public InnService(OrbitMMO plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 10L);
        for (Player p : Bukkit.getOnlinePlayers()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> joined(p), 40L);
        }
    }

    // ------------------------------------------------------------------ Réglages

    /** Recharge du cristal, en secondes (config.yml : cristal.recharge-secondes). */
    public int recharge() {
        return Math.max(0, plugin.getConfig().getInt("cristal.recharge-secondes", 600));
    }

    // ------------------------------------------------------------------ Lieux

    /** Capitale ou village construit d'après son id, ou null. */
    public Town town(String id) {
        if (id == null || plugin.continent() == null) {
            return null;
        }
        for (Town t : plugin.continent().towns()) {
            if (t.id().equals(id)) {
                return t;
            }
        }
        return null;
    }

    /** Point où l'auberge accueille : devant l'auberge d'un village, l'arrivée d'une capitale. */
    public Location anchor(Town t) {
        Location l = t.capital() ? t.points().get("spawn") : t.points().get("auberge");
        if (l == null) {
            l = t.points().get("spawn");
        }
        if (l == null && plugin.continent() != null) {
            l = plugin.continent().arrival(t.x(), t.z());
        }
        return l == null ? null : l.clone();
    }

    /** L'auberge accepte-t-elle ce personnage ? Celles de la faction adverse, non. */
    public boolean welcomes(Town t, PlayerData d) {
        String f = t.faction();
        return f == null || f.isBlank() || f.equals("neutre") || d.faction == null || f.equals(d.faction.id);
    }

    private String innName(Town t) {
        return "Auberge · " + t.name();
    }

    /** Nom de l'auberge liée (null si aucune ou si elle n'existe plus). */
    public String boundName(PlayerData d) {
        Town t = town(d.inn);
        return t == null ? null : innName(t);
    }

    // ------------------------------------------------------------------ Cristal

    public static boolean isCrystal(ItemStack it) {
        return "tp_crystal".equals(Items.type(it));
    }

    /** Un cristal neuf pour ce personnage (lore à jour : destination, recharge). */
    public ItemStack crystal(Player p, PlayerData d) {
        return Items.tpCrystal(p.getUniqueId(), boundName(d), recharge());
    }

    /** Le joueur a-t-il son cristal ? */
    public boolean hasCrystal(Player p) {
        for (ItemStack it : p.getInventory().getContents()) {
            if (isCrystal(it) && p.getUniqueId().equals(Items.owner(it))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Remet l'inventaire d'aplomb : un seul cristal, le sien, à jour. Les anciens cristaux à usage unique
     * en trop sont remboursés au prix de l'alchimiste.
     *
     * @return vrai si le joueur a son cristal ensuite
     */
    public boolean sync(Player p, PlayerData d) {
        if (!plugin.progression().valid(d)) {
            return false;
        }
        PlayerInventory inv = p.getInventory();
        ItemStack[] contents = inv.getContents();
        int first = -1;
        int legacy = 0;
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (!isCrystal(it)) {
                continue;
            }
            if (Items.owner(it) == null) {
                legacy += it.getAmount();
            }
            if (first < 0 || (i < 36 && first >= 36)) {
                first = i;
            }
            inv.setItem(i, null);
        }
        if (legacy > 1) {
            long refund = (long) (legacy - 1) * Items.TP_CRYSTAL_PRICE;
            d.gold += refund;
            Text.msg(p, "<#7FD4FF>Le cristal de téléportation est désormais réutilisable. <#D7DCE2>Vos " + (legacy - 1)
                    + " cristaux en trop vous sont remboursés : <#FFD66E>+" + Text.num(refund) + " or</#FFD66E>.");
        } else if (legacy == 1) {
            Text.msg(p, "<#7FD4FF>Votre cristal de téléportation est désormais réutilisable. "
                    + "<#D7DCE2>Liez-le chez un aubergiste pour qu'il vous ramène à son auberge.");
        }
        ItemStack fresh = crystal(p, d);
        if (first >= 0 && first < 36) {
            inv.setItem(first, fresh);
        } else if (inv.firstEmpty() >= 0) {
            inv.addItem(fresh);
        } else {
            return false;
        }
        long left = d.crystalReadyAt - System.currentTimeMillis();
        if (left > 0) {
            p.setCooldown(Keys.TP_CRYSTAL_COOLDOWN, (int) (left / 50));
            charging.add(p.getUniqueId());
        }
        return true;
    }

    /** Clic droit avec le cristal (ou bouton « Retour en ville » du menu). */
    public void use(Player p, PlayerData d, ItemStack item) {
        UUID owner = Items.owner(item);
        if (owner == null) {
            // Ancien cristal à usage unique : il devient le cristal lié du joueur.
            sync(p, d);
            return;
        }
        if (!owner.equals(p.getUniqueId())) {
            deny(p, "Ce cristal est lié à quelqu'un d'autre");
            return;
        }
        long now = System.currentTimeMillis();
        if (now - d.lastCombat < COMBAT_MS) {
            deny(p, "Impossible en plein combat");
            return;
        }
        long left = d.crystalReadyAt - now;
        if (left > 0) {
            p.setCooldown(Keys.TP_CRYSTAL_COOLDOWN, (int) (left / 50));
            charging.add(p.getUniqueId());
            plugin.hud().flash(p, "<#7FD4FF>Cristal en recharge <#8C96A3>· encore <white>" + Items.duration(left), 40);
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.8f);
            return;
        }
        String where = travel(p, d);
        int cd = recharge();
        d.crystalReadyAt = now + cd * 1000L;
        if (cd > 0) {
            p.setCooldown(Keys.TP_CRYSTAL_COOLDOWN, cd * 20);
            charging.add(p.getUniqueId());
        }
        plugin.hud().flash(p, "<#7FD4FF><b>Téléportation !</b> <#D7DCE2>" + Text.esc(where), 40);
    }

    /** Emmène le joueur à son auberge, sinon comme avant (capitale, ou ville de l'étage du Pilier). */
    private String travel(Player p, PlayerData d) {
        Town t = town(d.inn);
        if (d.inn != null && t == null && plugin.continent() != null && plugin.continent().ready()) {
            Text.msg(p, "<#FF5C5C>L'auberge liée à votre cristal n'existe plus : retour à votre capitale.");
        }
        Location to = t == null || !welcomes(t, d) ? null : anchor(t);
        if (to == null) {
            return plugin.floors().recall(p, d);
        }
        Fx.teleport(p.getLocation());
        p.teleportAsync(to).thenAccept(ok -> {
            if (ok && p.isOnline()) {
                Fx.teleport(to);
                Text.title(p, "<" + HEX + "><b>" + Text.esc(t.name()) + "</b>", "<#D7DCE2>Auberge", 5, 50, 15);
            }
        });
        return innName(t);
    }

    /** Lignes d'état du cristal (menus). */
    public List<String> status(PlayerData d) {
        List<String> out = new ArrayList<>();
        String bound = boundName(d);
        out.add("<#8C96A3>Destination : <white>" + (bound == null ? "votre capitale" : Text.esc(bound)));
        long left = d.crystalReadyAt - System.currentTimeMillis();
        out.add(left > 0 ? "<#8C96A3>État : <#FFB347>en recharge, encore " + Items.duration(left) : "<#8C96A3>État : <#5EE89A>prêt");
        out.add("<#8C96A3>Recharge : <white>" + Items.duration(recharge() * 1000L));
        return out;
    }

    /** Lie le cristal à l'auberge de ce lieu. */
    public boolean bind(Player p, PlayerData d, Town t) {
        if (!welcomes(t, d)) {
            deny(p, "Cette auberge ne sert pas votre faction");
            return false;
        }
        d.inn = t.id();
        plugin.players().save(d);
        sync(p, d);
        p.playSound(p.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 0.8f, 1.3f);
        Fx.dust(p.getLocation().add(0, 1, 0), Fx.color("#7FD4FF"), 1.2f, 25, 0.5);
        Text.msg(p, "<#7FD4FF>Cristal lié à l'auberge de " + Text.esc(t.name()) + ".</#7FD4FF> <#D7DCE2>Il vous y ramènera désormais.");
        return true;
    }

    private void deny(Player p, String msg) {
        plugin.hud().flash(p, "<#FF5C5C>" + msg, 40);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.8f);
    }

    /** Avertit quand le cristal est rechargé ; montre le temps restant à qui le tient en main. */
    private void tick() {
        if (charging.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Iterator<UUID> it = charging.iterator(); it.hasNext(); ) {
            UUID id = it.next();
            Player p = Bukkit.getPlayer(id);
            PlayerData d = p == null ? null : plugin.players().loaded(id);
            if (d == null) {
                it.remove();
                continue;
            }
            long left = d.crystalReadyAt - now;
            if (left <= 0) {
                it.remove();
                plugin.hud().flash(p, "<#7FD4FF>Cristal de téléportation <#5EE89A>rechargé</#5EE89A>.", 50);
                p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.6f);
            } else if (isCrystal(p.getInventory().getItemInMainHand())) {
                plugin.hud().flash(p, "<#7FD4FF>Cristal de téléportation <#8C96A3>· recharge : <white>" + Items.duration(left), 15);
            }
        }
    }

    // ------------------------------------------------------------------ Aubergiste

    /** Clic droit sur un aubergiste. */
    public void talk(Player p, Entity npc) {
        String id = npc.getPersistentDataContainer().get(Keys.INN, PersistentDataType.STRING);
        Town t = town(id);
        PlayerData d = plugin.players().get(p);
        if (t == null || !plugin.progression().valid(d)) {
            return;
        }
        boolean en = plugin.quests() != null && "en".equals(plugin.quests().lang(p));
        int k = ThreadLocalRandom.current().nextInt(LINES.length);
        String line = en ? LINES_EN[k] : LINES[k];
        fr.orbit.mmo.hud.NpcCard.Card card = null;
        if (plugin.pack().isLoaded(p.getUniqueId())) {
            card = fr.orbit.mmo.hud.NpcCard.of("makena", en ? "Innkeeper" : "Aubergiste",
                    net.kyori.adventure.text.format.TextColor.fromHexString(HEX), line);
            plugin.hud().npcCard(p, card);
        }
        fr.orbit.mmo.ui.InnMenus.open(plugin, p, t);
        if (card != null && !card.shown() && plugin.hud().npcCard(p) == card) {
            plugin.hud().npcCardEnd(p);
        }
        long now = System.currentTimeMillis();
        Long next = chatCooldown.get(p.getUniqueId());
        if (next == null || next < now) {
            chatCooldown.put(p.getUniqueId(), now + 30_000);
            if (card == null || !card.shown()) {
                p.sendMessage(Text.mm("<" + HEX + ">" + (en ? "Innkeeper" : "Aubergiste") + " <#5A6270>» <#D7DCE2><i>" + Text.esc(line)));
            }
            p.playSound(npc.getLocation(), Sound.ENTITY_VILLAGER_TRADE, 0.7f, 1.1f);
        }
    }

    // ------------------------------------------------------------------ Pose des aubergistes

    /** Ce que donne la pose, lieu par lieu. */
    public String place(CommandSender who, boolean remove) {
        if (job != null) {
            return "Une pose d'aubergistes est déjà en cours.";
        }
        if (plugin.continent() == null || !plugin.continent().ready()) {
            return "Le continent n'est pas chargé.";
        }
        List<Town> towns = plugin.continent().towns();
        if (towns.isEmpty()) {
            return "Aucune capitale ni aucun village construit.";
        }
        job = new PlaceJob(who, remove, new ArrayDeque<>(towns));
        job.task = Bukkit.getScheduler().runTaskTimer(plugin, job::step, 1L, 2L);
        Text.msg(who, "<#7FD4FF>Aubergistes : <#D7DCE2>" + (remove ? "retrait" : "pose") + " dans " + towns.size() + " lieux…");
        return null;
    }

    private final class PlaceJob {
        final CommandSender who;
        final boolean remove;
        final Deque<Town> queue;
        final List<String> report = new ArrayList<>();
        BukkitTask task;
        Town current;
        List<Chunk> held = new ArrayList<>();
        int waited;
        int posed, removed, failed;

        PlaceJob(CommandSender who, boolean remove, Deque<Town> queue) {
            this.who = who;
            this.remove = remove;
            this.queue = queue;
        }

        void step() {
            World w = plugin.continent().world();
            if (current == null) {
                current = queue.poll();
                if (current == null) {
                    finish();
                    return;
                }
                Location a = anchor(current);
                if (a == null || a.getWorld() == null) {
                    report.add("<#FF5C5C>" + Text.esc(current.name()) + " : aucun point d'accueil");
                    failed++;
                    current = null;
                    return;
                }
                held.clear();
                waited = 0;
                for (int cx = (a.getBlockX() >> 4) - 1; cx <= (a.getBlockX() >> 4) + 1; cx++) {
                    for (int cz = (a.getBlockZ() >> 4) - 1; cz <= (a.getBlockZ() >> 4) + 1; cz++) {
                        Chunk c = w.getChunkAt(cx, cz);
                        c.addPluginChunkTicket(plugin);
                        held.add(c);
                    }
                }
            }
            boolean ready = true;
            for (Chunk c : held) {
                ready &= c.isEntitiesLoaded();
            }
            if (!ready && waited++ < 100) {
                return;
            }
            Town t = current;
            current = null;
            // Retire les aubergistes déjà posés pour ce lieu (chunks voisins et tout ce qui est chargé).
            for (Chunk c : held) {
                for (Entity e : c.getEntities()) {
                    if (isInnkeeperOf(e, t.id())) {
                        e.remove();
                        removed++;
                    }
                }
            }
            for (Entity e : w.getEntitiesByClass(Mannequin.class)) {
                if (e.isValid() && isInnkeeperOf(e, t.id())) {
                    e.remove();
                    removed++;
                }
            }
            if (!remove) {
                Location a = anchor(t);
                Location at = spot(a);
                if (at == null) {
                    report.add("<#FF5C5C>" + Text.esc(t.name()) + " : aucune place libre près de " + coords(a));
                    failed++;
                } else {
                    spawn(t, at);
                    posed++;
                    report.add("<#5EE89A>" + Text.esc(t.name()) + " <#8C96A3>" + coords(at));
                }
            }
            for (Chunk c : held) {
                c.removePluginChunkTicket(plugin);
            }
            held.clear();
        }

        void finish() {
            task.cancel();
            job = null;
            for (String line : report) {
                Text.msg(who, "  " + line);
            }
            Text.msg(who, "<#7FD4FF>Aubergistes : <#D7DCE2>" + posed + " posés, " + removed + " retirés"
                    + (failed > 0 ? ", <#FF5C5C>" + failed + " en échec" : "") + "<#D7DCE2>.");
            plugin.getLogger().info("Aubergistes : " + posed + " posés, " + removed + " retirés, " + failed + " en échec.");
        }
    }

    private static boolean isInnkeeperOf(Entity e, String townId) {
        return TAG.equals(e.getPersistentDataContainer().get(Keys.NPC, PersistentDataType.STRING))
                && townId.equals(e.getPersistentDataContainer().get(Keys.INN, PersistentDataType.STRING));
    }

    private static String coords(Location l) {
        return l == null ? "?" : l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ();
    }

    private void spawn(Town t, Location at) {
        String side = t.faction() == null || t.faction().isBlank() ? "neutre" : t.faction();
        String skin = "villageois_" + side + "_" + (1 + Math.floorMod(t.id().hashCode(), 6));
        if (!Looks.hasSkin(skin)) {
            skin = "villageois_neutre_1";
        }
        Mannequin m = TownFolk.spawn(at, TAG, Text.mm("<" + HEX + "><b>Aubergiste</b> <#8C96A3>· " + Text.esc(t.name())),
                skin, Material.HONEY_BOTTLE);
        m.getPersistentDataContainer().set(Keys.INN, PersistentDataType.STRING, t.id());
        m.addScoreboardTag(SCOREBOARD);
    }

    /**
     * Une place pour l'aubergiste à côté du point d'accueil (jamais dessus : on y arrive par le cristal) :
     * sol plein sous les pieds, pieds et tête dans l'air, ni eau ni autre PNJ.
     */
    Location spot(Location a) {
        World w = a.getWorld();
        double rad = Math.toRadians(a.getYaw());
        int fx = (int) Math.round(-Math.sin(rad)), fz = (int) Math.round(Math.cos(rad));
        int sx = fz, sz = -fx;
        List<int[]> tries = new ArrayList<>();
        // D'abord sur le côté (de part et d'autre de la porte), puis un pas en avant, puis tout autour.
        for (int k = 2; k <= 4; k++) {
            tries.add(new int[]{sx * k, sz * k});
            tries.add(new int[]{-sx * k, -sz * k});
            tries.add(new int[]{sx * k + fx, sz * k + fz});
            tries.add(new int[]{-sx * k + fx, -sz * k + fz});
        }
        for (int r = 2; r <= 6; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == r) {
                        tries.add(new int[]{dx, dz});
                    }
                }
            }
        }
        int ay = a.getBlockY();
        for (int[] t : tries) {
            int x = a.getBlockX() + t[0], z = a.getBlockZ() + t[1];
            for (int dy : new int[]{0, 1, -1, 2, -2}) {
                int y = ay + dy;
                if (standable(w, x, y, z)) {
                    Location at = new Location(w, x + 0.5, y, z + 0.5, a.getYaw(), 0);
                    if (!crowded(at)) {
                        return at;
                    }
                }
            }
        }
        return null;
    }

    private static boolean standable(World w, int x, int y, int z) {
        Block below = w.getBlockAt(x, y - 1, z);
        if (!below.getType().isSolid() || below.isLiquid() || Tag.LEAVES.isTagged(below.getType())) {
            return false;
        }
        double h = below.getBoundingBox().getHeight();
        if (h < 0.9 || h > 1.0) {
            return false; // dalles, clôtures, murets : on n'y pose pas un PNJ
        }
        return free(w.getBlockAt(x, y, z)) && free(w.getBlockAt(x, y + 1, z));
    }

    /** Bloc où un corps tient : de l'air, ou une herbe qui ne gêne pas. Jamais d'eau, de lave ni de feu. */
    private static boolean free(Block b) {
        Material m = b.getType();
        if (m.isAir()) {
            return true;
        }
        if (b.isLiquid() || m == Material.WATER || m == Material.LAVA || m == Material.FIRE || m == Material.SOUL_FIRE
                || m == Material.POWDER_SNOW || m == Material.COBWEB || m == Material.SNOW || m == Material.BUBBLE_COLUMN) {
            return false;
        }
        if (b.getBlockData() instanceof Waterlogged wl && wl.isWaterlogged()) {
            return false;
        }
        return b.isPassable() && Tag.REPLACEABLE.isTagged(m);
    }

    private static boolean crowded(Location at) {
        for (Entity e : at.getWorld().getNearbyEntities(at.clone().add(0, 1, 0), 1.0, 1.5, 1.0)) {
            if (!(e instanceof Player)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ Cristal lié : ni jeté, ni rangé, ni perdu

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> joined(p), 40L);
    }

    private void joined(Player p) {
        if (!p.isOnline()) {
            return;
        }
        PlayerData d = plugin.players().get(p);
        if (plugin.progression().valid(d) && !d.buildMode) {
            sync(p, d);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        charging.remove(e.getPlayer().getUniqueId());
        chatCooldown.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (isCrystal(e.getItemDrop().getItemStack()) && Items.owner(e.getItemDrop().getItemStack()) != null) {
            e.setCancelled(true);
            deny(e.getPlayer(), "Votre cristal est lié à vous : il ne se jette pas");
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        ItemStack cursor = e.getCursor();
        ItemStack current = e.getCurrentItem();
        // Dans un sac (bundle) : il en sortirait par un simple jet.
        if ((isCrystal(cursor) && bundle(current)) || (bundle(cursor) && isCrystal(current))) {
            e.setCancelled(true);
            deny(p, "Votre cristal ne se range pas dans un sac");
            return;
        }
        Inventory top = e.getView().getTopInventory();
        if (top.getType() == InventoryType.CRAFTING || top.getType() == InventoryType.CREATIVE
                || top.getHolder() instanceof fr.orbit.mmo.ui.Menu) {
            return;
        }
        boolean moving;
        if (e.getClickedInventory() == top) {
            ItemStack swapped = e.getClick() == ClickType.NUMBER_KEY && e.getHotbarButton() >= 0
                    ? p.getInventory().getItem(e.getHotbarButton())
                    : e.getClick() == ClickType.SWAP_OFFHAND ? p.getInventory().getItemInOffHand() : null;
            moving = isCrystal(cursor) || isCrystal(swapped);
        } else {
            moving = e.isShiftClick() && isCrystal(current);
        }
        if (moving) {
            e.setCancelled(true);
            deny(p, "Votre cristal est lié à vous : il reste dans votre sac");
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (!isCrystal(e.getOldCursor())) {
            return;
        }
        Inventory top = e.getView().getTopInventory();
        if (top.getType() == InventoryType.CRAFTING || top.getType() == InventoryType.CREATIVE) {
            return;
        }
        for (int raw : e.getRawSlots()) {
            if (raw < top.getSize()) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onGiveToEntity(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof ItemFrame) && !(e.getRightClicked() instanceof Allay)) {
            return;
        }
        ItemStack hand = e.getPlayer().getInventory().getItem(e.getHand());
        if (isCrystal(hand)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent e) {
        if (isCrystal(e.getPlayerItem())) {
            e.setCancelled(true);
        }
    }

    /** Hors des mondes du MMO (inventaire perdu à la mort), le cristal reste quand même. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent e) {
        if (e.getKeepInventory()) {
            return;
        }
        List<ItemStack> kept = new ArrayList<>();
        e.getDrops().removeIf(it -> {
            if (isCrystal(it) && Items.owner(it) != null) {
                kept.add(it);
                return true;
            }
            return false;
        });
        e.getItemsToKeep().addAll(kept);
    }

    private static boolean bundle(ItemStack it) {
        return it != null && it.getType().name().endsWith("BUNDLE");
    }
}
