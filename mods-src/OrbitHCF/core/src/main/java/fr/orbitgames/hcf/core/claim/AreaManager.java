package fr.orbitgames.hcf.core.claim;

import fr.orbitgames.hcf.api.model.Claim;
import fr.orbitgames.hcf.api.model.Faction;
import fr.orbitgames.hcf.api.model.FactionRole;
import fr.orbitgames.hcf.api.model.FactionType;
import fr.orbitgames.hcf.api.model.Relation;
import fr.orbitgames.hcf.api.util.Text;
import fr.orbitgames.hcf.core.OrbitHCFCorePlugin;
import fr.orbitgames.hcf.core.faction.FactionImpl;
import fr.orbitgames.hcf.core.storage.Database;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Claims a la baguette : des rectangles au bloc pres, choisis en deux clics et payes avec l'argent
 * de la faction. Les claims par chunk du {@link ClaimManager} ne servent plus qu'aux zones d'evenement.
 */
public class AreaManager implements Listener {

    /** Un terrain rectangulaire d'une faction (toute la hauteur du monde). */
    public static final class Area implements Claim {
        public final UUID id;
        public final String world;
        public final int minX;
        public final int minZ;
        public final int maxX;
        public final int maxZ;
        public final UUID factionId;

        Area(UUID id, String world, int x1, int z1, int x2, int z2, UUID factionId) {
            this.id = id;
            this.world = world;
            this.minX = Math.min(x1, x2);
            this.minZ = Math.min(z1, z2);
            this.maxX = Math.max(x1, x2);
            this.maxZ = Math.max(z1, z2);
            this.factionId = factionId;
        }

        boolean contains(String worldName, int x, int z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ && world.equals(worldName);
        }

        public boolean contains(Location location) {
            return contains(location.getWorld().getName(), location.getBlockX(), location.getBlockZ());
        }

        /** Distance entre deux rectangles (0 = ils se touchent ou se chevauchent). */
        int gap(int x1, int z1, int x2, int z2) {
            int dx = Math.max(0, Math.max(x1 - maxX, minX - x2) - 1);
            int dz = Math.max(0, Math.max(z1 - maxZ, minZ - z2) - 1);
            return Math.max(dx, dz);
        }

        boolean overlaps(int x1, int z1, int x2, int z2) {
            return x1 <= maxX && x2 >= minX && z1 <= maxZ && z2 >= minZ;
        }

        public long surface() {
            return (long) (maxX - minX + 1) * (maxZ - minZ + 1);
        }

        @Override
        public String getWorldName() {
            return world;
        }

        @Override
        public int getChunkX() {
            return minX >> 4;
        }

        @Override
        public int getChunkZ() {
            return minZ >> 4;
        }

        @Override
        public UUID getFactionId() {
            return factionId;
        }
    }

    private static final class Selection {
        Location first;
        Location second;
        final List<Location> shown = new ArrayList<>();
    }

    private final OrbitHCFCorePlugin plugin;
    private final Database database;
    private final NamespacedKey wandKey;
    private final List<Area> areas = new CopyOnWriteArrayList<>();
    private final Map<UUID, Selection> selections = new HashMap<>();

    // Reglages (claims.yml, section « baguette »)
    private int minSide;
    private int maxSide;
    private int maxAreas;
    private int buffer;
    private double pricePerBlock;
    private double refund;
    private boolean requireAdjacent;
    private FactionRole minRole;
    private int roadHalf;
    private int warzone;

    public AreaManager(OrbitHCFCorePlugin plugin, Database database) {
        this.plugin = plugin;
        this.database = database;
        this.wandKey = new NamespacedKey(plugin, "baguette-claim");
    }

    public void load(FileConfiguration config) {
        minSide = config.getInt("baguette.cote-min", 5);
        maxSide = config.getInt("baguette.cote-max", 80);
        maxAreas = config.getInt("baguette.terrains-max", 4);
        buffer = config.getInt("baguette.ecart-blocs", 2);
        pricePerBlock = config.getDouble("baguette.prix-par-bloc", 0.5);
        refund = config.getDouble("baguette.remboursement", 0.5);
        requireAdjacent = config.getBoolean("baguette.terrains-colles", true);
        try {
            minRole = FactionRole.valueOf(config.getString("claims.min-role", "OFFICER"));
        } catch (IllegalArgumentException e) {
            minRole = FactionRole.OFFICER;
        }
        roadHalf = config.getInt("road.half-width", 8);
        warzone = config.getInt("warzone.radius", 800);

        String table = database.getTablePrefix() + "areas";
        database.update("CREATE TABLE IF NOT EXISTS " + table + " (id VARCHAR(36) PRIMARY KEY, world VARCHAR(64) NOT NULL, "
                + "x1 INT NOT NULL, z1 INT NOT NULL, x2 INT NOT NULL, z2 INT NOT NULL, faction_id VARCHAR(36) NOT NULL)");
        areas.clear();
        database.query("SELECT id, world, x1, z1, x2, z2, faction_id FROM " + table, rs -> {
            while (rs.next()) {
                areas.add(new Area(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getInt(3), rs.getInt(4),
                        rs.getInt(5), rs.getInt(6), UUID.fromString(rs.getString(7))));
            }
            return null;
        });
        plugin.getLogger().info(areas.size() + " terrains (claims a la baguette) charges.");
    }

    // ------------------------------------------------------------------ Lecture

    public Area areaAt(String world, int x, int z) {
        for (Area area : areas) {
            if (area.contains(world, x, z)) {
                return area;
            }
        }
        return null;
    }

    public Area areaAt(Location location) {
        return areaAt(location.getWorld().getName(), location.getBlockX(), location.getBlockZ());
    }

    public List<Area> of(UUID factionId) {
        List<Area> list = new ArrayList<>();
        for (Area area : areas) {
            if (area.factionId.equals(factionId)) {
                list.add(area);
            }
        }
        return list;
    }

    public Collection<Area> all() {
        return areas;
    }

    public long price(long surface) {
        return Math.max(1L, Math.round(surface * pricePerBlock));
    }

    // ------------------------------------------------------------------ Ecriture

    private void add(Area area) {
        areas.add(area);
        database.updateAsync("REPLACE INTO " + database.getTablePrefix()
                        + "areas (id, world, x1, z1, x2, z2, faction_id) VALUES (?, ?, ?, ?, ?, ?, ?)",
                area.id.toString(), area.world, area.minX, area.minZ, area.maxX, area.maxZ, area.factionId.toString());
    }

    /** Abandonne un terrain. Renvoie l'argent rendu a la banque de la faction. */
    public long remove(Area area) {
        if (!areas.remove(area)) {
            return 0L;
        }
        database.updateAsync("DELETE FROM " + database.getTablePrefix() + "areas WHERE id = ?", area.id.toString());
        long back = Math.round(price(area.surface()) * refund);
        if (back > 0 && plugin.getFactionManager().getFaction(area.factionId) != null) {
            plugin.getEconomy().deposit(area.factionId, back);
        }
        return back;
    }

    /** Retire tous les terrains d'une faction (dissolution, /f unclaimall). */
    public long removeAll(UUID factionId) {
        long back = 0L;
        for (Area area : of(factionId)) {
            back += remove(area);
        }
        return back;
    }

    // ------------------------------------------------------------------ Baguette

    public ItemStack wand() {
        ItemStack item = new ItemStack(Material.GOLDEN_HOE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Text.color("&6&lBaguette de claim"));
        meta.setLore(List.of(
                Text.color("&eClic gauche &7sur un bloc : 1er coin"),
                Text.color("&eClic droit &7sur un bloc : 2e coin"),
                Text.color("&eAccroupi + clic gauche &7dans le vide : acheter"),
                Text.color("&eClic droit &7dans le vide : annuler")));
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isWand(ItemStack item) {
        return item != null && item.getType() == Material.GOLDEN_HOE && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE);
    }

    private void say(Player player, String message) {
        player.sendMessage(Text.color(plugin.getMessages().getPrefix() + message));
    }

    /** /f claim : donne la baguette (ou la reprend si le joueur l'a deja). */
    public void giveWand(Player player, FactionImpl faction) {
        FactionRole role = faction.getRole(player.getUniqueId());
        if (role == null || !role.isAtLeast(minRole)) {
            plugin.getMessages().send(player, "general.insufficient-rank");
            return;
        }
        if (!plugin.getClaimManager().isHcfWorld(player.getWorld())) {
            plugin.getMessages().send(player, "claim.wrong-world");
            return;
        }
        for (ItemStack item : player.getInventory().getContents()) {
            if (isWand(item)) {
                say(player, "&eTu as deja la baguette. &7Clic gauche et clic droit sur deux blocs, puis accroupi + clic gauche dans le vide.");
                return;
            }
        }
        if (!player.getInventory().addItem(wand()).isEmpty()) {
            say(player, "&cInventaire plein : libere une case pour la baguette.");
            return;
        }
        say(player, "&aBaguette de claim donnee. &7Clic gauche = 1er coin, clic droit = 2e coin, "
                + "accroupi + clic gauche dans le vide = acheter. Prix : &f" + pricePerBlock + " $ &7par bloc de surface, "
                + "cote de &f" + minSide + " &7a &f" + maxSide + " &7blocs.");
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onWand(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !isWand(event.getItem())) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        Selection selection = selections.computeIfAbsent(player.getUniqueId(), k -> new Selection());
        Action action = event.getAction();
        if (action == Action.LEFT_CLICK_AIR && player.isSneaking()) {
            confirm(player);
        } else if (action == Action.RIGHT_CLICK_AIR) {
            clear(player);
            say(player, "&eSelection annulee.");
        } else if ((action == Action.LEFT_CLICK_BLOCK || action == Action.RIGHT_CLICK_BLOCK) && event.getClickedBlock() != null) {
            Location corner = event.getClickedBlock().getLocation();
            if (action == Action.LEFT_CLICK_BLOCK) {
                selection.first = corner;
            } else {
                selection.second = corner;
            }
            if (selection.first != null && selection.second != null
                    && !selection.first.getWorld().equals(selection.second.getWorld())) {
                selection.first = action == Action.LEFT_CLICK_BLOCK ? corner : null;
                selection.second = action == Action.RIGHT_CLICK_BLOCK ? corner : null;
            }
            describe(player, selection, action == Action.LEFT_CLICK_BLOCK ? 1 : 2);
        }
    }

    private void describe(Player player, Selection selection, int which) {
        Location corner = which == 1 ? selection.first : selection.second;
        showPillars(player, selection);
        if (selection.first == null || selection.second == null) {
            say(player, "&eCoin " + which + " : &f" + corner.getBlockX() + ", " + corner.getBlockZ()
                    + "&e. Pose l'autre coin avec le clic " + (which == 1 ? "droit" : "gauche") + ".");
            return;
        }
        int width = Math.abs(selection.first.getBlockX() - selection.second.getBlockX()) + 1;
        int depth = Math.abs(selection.first.getBlockZ() - selection.second.getBlockZ()) + 1;
        long cost = price((long) width * depth);
        FactionImpl faction = plugin.getFactionManager().getImplByPlayer(player.getUniqueId());
        String problem = faction == null ? "Tu n'as pas de faction." : check(faction, selection);
        say(player, "&eTerrain de &f" + width + " x " + depth + " &eblocs, prix &a" + plugin.getEconomy().format(cost) + "&e.");
        if (problem != null) {
            say(player, "&c" + problem);
        } else {
            say(player, "&7Accroupi + clic gauche dans le vide (ou &f/f claim valider&7) pour acheter.");
        }
    }

    /** Raison qui empeche ce terrain, ou null s'il est bon. */
    private String check(FactionImpl faction, Selection selection) {
        World world = selection.first.getWorld();
        if (!plugin.getClaimManager().isHcfWorld(world)) {
            return "Les claims sont desactives dans ce monde.";
        }
        int x1 = Math.min(selection.first.getBlockX(), selection.second.getBlockX());
        int x2 = Math.max(selection.first.getBlockX(), selection.second.getBlockX());
        int z1 = Math.min(selection.first.getBlockZ(), selection.second.getBlockZ());
        int z2 = Math.max(selection.first.getBlockZ(), selection.second.getBlockZ());
        int width = x2 - x1 + 1;
        int depth = z2 - z1 + 1;
        if (width < minSide || depth < minSide) {
            return "Trop petit : chaque cote fait au moins " + minSide + " blocs.";
        }
        if (width > maxSide || depth > maxSide) {
            return "Trop grand : chaque cote fait au plus " + maxSide + " blocs.";
        }
        // Zones systeme : routes (le long des axes) et carre de la Warzone
        int nearX = x1 > 0 ? x1 : (x2 < 0 ? -x2 : 0);
        int nearZ = z1 > 0 ? z1 : (z2 < 0 ? -z2 : 0);
        if (nearX <= roadHalf || nearZ <= roadHalf || (nearX <= warzone && nearZ <= warzone)) {
            return "Ce terrain touche la Warzone ou une route : on claim au-dela de " + warzone + " blocs du spawn.";
        }
        if (plugin.getClaimManager().hasChunkClaimIn(world.getName(), x1, z1, x2, z2)) {
            return "Ce terrain touche une zone d'evenement.";
        }
        List<Area> own = of(faction.getId());
        if (own.size() >= maxAreas) {
            return "Ta faction a deja " + maxAreas + " terrains (le maximum). /f unclaim pour en rendre un.";
        }
        boolean touching = own.isEmpty();
        for (Area area : areas) {
            if (!area.world.equals(world.getName())) {
                continue;
            }
            if (area.overlaps(x1, z1, x2, z2)) {
                return "Ce terrain chevauche un claim existant.";
            }
            if (area.factionId.equals(faction.getId())) {
                touching |= area.gap(x1, z1, x2, z2) == 0;
            } else if (area.gap(x1, z1, x2, z2) < buffer) {
                return "Trop pres du claim d'une autre faction (" + buffer + " blocs d'ecart au moins).";
            }
        }
        if (requireAdjacent && !touching) {
            return "Le nouveau terrain doit toucher un terrain de ta faction.";
        }
        return null;
    }

    /** Achat du terrain selectionne (accroupi + clic gauche dans le vide, ou /f claim valider). */
    public void confirm(Player player) {
        Selection selection = selections.get(player.getUniqueId());
        FactionImpl faction = plugin.getFactionManager().getImplByPlayer(player.getUniqueId());
        if (faction == null) {
            plugin.getMessages().send(player, "general.not-in-faction");
            return;
        }
        FactionRole role = faction.getRole(player.getUniqueId());
        if (role == null || !role.isAtLeast(minRole)) {
            plugin.getMessages().send(player, "general.insufficient-rank");
            return;
        }
        if (selection == null || selection.first == null || selection.second == null) {
            say(player, "&cPose d'abord les deux coins avec la baguette (clic gauche puis clic droit sur un bloc).");
            return;
        }
        String problem = check(faction, selection);
        if (problem != null) {
            say(player, "&c" + problem);
            return;
        }
        Area area = new Area(UUID.randomUUID(), selection.first.getWorld().getName(),
                selection.first.getBlockX(), selection.first.getBlockZ(),
                selection.second.getBlockX(), selection.second.getBlockZ(), faction.getId());
        long cost = price(area.surface());
        var economy = plugin.getEconomy();
        long bank = economy.getBalance(faction.getId());
        long pocket = economy.getBalance(player.getUniqueId());
        if (bank + pocket < cost) {
            say(player, "&cIl faut &f" + economy.format(cost) + "&c : la banque de faction a &f" + economy.format(bank)
                    + "&c et toi &f" + economy.format(pocket) + "&c. &7(/f deposit pour alimenter la banque)");
            return;
        }
        // La banque de la faction paie d'abord, le joueur complete.
        long fromBank = Math.min(bank, cost);
        economy.withdraw(faction.getId(), fromBank);
        economy.withdraw(player.getUniqueId(), cost - fromBank);
        add(area);
        clear(player);
        removeWand(player);
        String line = Text.color(plugin.getMessages().getPrefix() + "&e" + player.getName() + " &aa claim un terrain de &f"
                + (area.maxX - area.minX + 1) + " x " + (area.maxZ - area.minZ + 1) + " &apour &f" + economy.format(cost)
                + " &7(" + area.minX + ", " + area.minZ + " -> " + area.maxX + ", " + area.maxZ + ")");
        for (Player online : faction.getOnlinePlayers()) {
            online.sendMessage(line);
        }
        if (faction.getHome() == null) {
            say(player, "&7Pense a poser le home de faction : &f/f sethome&7.");
        }
    }

    // ------------------------------------------------------------------ Piliers (vus du joueur seulement)

    private void showPillars(Player player, Selection selection) {
        hidePillars(player, selection);
        List<int[]> corners = new ArrayList<>();
        if (selection.first != null && selection.second != null) {
            corners.add(new int[]{selection.first.getBlockX(), selection.first.getBlockZ()});
            corners.add(new int[]{selection.first.getBlockX(), selection.second.getBlockZ()});
            corners.add(new int[]{selection.second.getBlockX(), selection.first.getBlockZ()});
            corners.add(new int[]{selection.second.getBlockX(), selection.second.getBlockZ()});
        } else {
            Location only = selection.first != null ? selection.first : selection.second;
            corners.add(new int[]{only.getBlockX(), only.getBlockZ()});
        }
        World world = (selection.first != null ? selection.first : selection.second).getWorld();
        BlockData glass = Material.GLASS.createBlockData();
        BlockData gold = Material.GOLD_BLOCK.createBlockData();
        int base = player.getLocation().getBlockY() - 4;
        Map<Location, BlockData> changes = new HashMap<>();
        for (int[] corner : corners) {
            for (int y = Math.max(world.getMinHeight(), base); y < Math.min(world.getMaxHeight(), base + 40); y++) {
                Location at = new Location(world, corner[0], y, corner[1]);
                changes.put(at, Math.floorMod(y, 4) == 0 ? gold : glass);
                selection.shown.add(at);
            }
        }
        player.sendMultiBlockChange(changes);
    }

    private void hidePillars(Player player, Selection selection) {
        if (selection.shown.isEmpty()) {
            return;
        }
        Map<Location, BlockData> changes = new HashMap<>();
        for (Location at : selection.shown) {
            if (at.getWorld().equals(player.getWorld()) && at.isChunkLoaded()) {
                changes.put(at, at.getBlock().getBlockData());
            }
        }
        selection.shown.clear();
        if (!changes.isEmpty() && player.isOnline()) {
            player.sendMultiBlockChange(changes);
        }
    }

    private void clear(Player player) {
        Selection selection = selections.remove(player.getUniqueId());
        if (selection != null) {
            hidePillars(player, selection);
        }
    }

    private void removeWand(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isWand(contents[i])) {
                player.getInventory().setItem(i, null);
            }
        }
    }

    // ------------------------------------------------------------------ La baguette ne traine pas

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (isWand(event.getItemDrop().getItemStack())) {
            event.getItemDrop().remove();
            clear(event.getPlayer());
            say(event.getPlayer(), "&7Baguette rangee. &f/f claim &7pour la reprendre.");
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(this::isWand);
        clear(event.getEntity());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        selections.remove(event.getPlayer().getUniqueId());
        removeWand(event.getPlayer());
    }

    // ------------------------------------------------------------------ Annonce de territoire

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ() && from.getWorld().equals(to.getWorld())) {
            return;
        }
        Faction before = plugin.getClaimManager().getFactionAt(from);
        Faction after = plugin.getClaimManager().getFactionAt(to);
        if (before.getId().equals(after.getId())) {
            return;
        }
        Player player = event.getPlayer();
        player.sendMessage(Text.color("&eTu quittes " + label(player, before) + "&e, tu entres dans " + label(player, after) + "&e."));
    }

    private String label(Player player, Faction faction) {
        FactionType type = faction.getType();
        if (type == FactionType.PLAYER) {
            Relation relation = plugin.getFactionManager().getRelation(
                    plugin.getFactionManager().getImplByPlayer(player.getUniqueId()), faction);
            String color = relation == Relation.MEMBER ? "&a" : relation == Relation.ALLY ? "&d" : "&c";
            return color + faction.getName() + (faction.isRaidable() ? " &4(raidable)" : "");
        }
        return switch (type) {
            case SAFEZONE -> "&ala Safezone";
            case WARZONE -> "&cla Warzone";
            case ROAD -> "&6la Route";
            case EVENT -> "&9" + faction.getName().replace("KOTH_", "KOTH ");
            default -> "&7la Nature";
        };
    }
}
