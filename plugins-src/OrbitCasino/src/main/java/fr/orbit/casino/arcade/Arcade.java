package fr.orbit.casino.arcade;

import fr.orbit.casino.Holo;
import fr.orbit.casino.OrbitCasino;
import fr.orbit.casino.Txt;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.FaceAttachable;
import org.bukkit.block.data.type.Switch;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Salle d'arcade cachée de La Station : on y entre par un bouton secret (par défaut derrière le bambou du
 * casino, d'autres entrées se posent avec /arcade bouton), et on y retrouve les PNJ des modes arcade :
 * ORBIT RUSH (course sans fin, jouée ici même), Vole mon Orbito (serveur vol) et le casino.
 */
public final class Arcade implements Listener {

    private final OrbitCasino plugin;
    private final World world;
    private final Runner runner;
    private UUID rushNpc, volNpc, casinoNpc;
    private Cabinets cabinets;
    private final Map<UUID, Location> comeback = new HashMap<>();
    private final Map<UUID, Long> lastClick = new HashMap<>();

    public Arcade(OrbitCasino plugin, World world) {
        this.plugin = plugin;
        this.world = world;
        this.runner = new Runner(plugin, this, world);
    }

    public Runner runner() {
        return runner;
    }

    public World world() {
        return world;
    }

    public void start() {
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, "BungeeCord");
        for (int cx = Math.floorDiv(-ArcadeRoom.HX - 1, 16); cx <= Math.floorDiv(ArcadeRoom.HX + 1, 16); cx++) {
            for (int cz = Math.floorDiv(ArcadeRoom.NORTH - 1, 16); cz <= Math.floorDiv(ArcadeRoom.SOUTH + 1, 16); cz++) {
                world.getChunkAt(cx, cz).addPluginChunkTicket(plugin);
            }
        }
        if (world.getBlockAt(ArcadeRoom.CX, ArcadeRoom.FLOOR, ArcadeRoom.CZ).getType().isAir()) {
            plugin.getLogger().info("Salle d'arcade construite (" + ArcadeRoom.build(world) + " blocs).");
        }
        placeButtons();
        createSharedTables();
        spawnAll();
        runner.start();
        cabinets = new Cabinets(plugin, world);
        cabinets.start();
        // records des modes arcade : Vole mon Orbito écrit sa base de son côté, on relit chaque minute
        Bukkit.getScheduler().runTaskTimer(plugin, this::refreshRecords, 1200L, 1200L);
    }

    public void stop() {
        runner.stopAll();
        if (cabinets != null) {
            cabinets.stop();
        }
    }

    /** (Re)pose PNJ et textes (après un /casino generer, qui efface tout ce que le casino a posé dans le monde). */
    public void spawnAll() {
        for (Entity e : spawned) {
            e.remove();
        }
        spawned.clear();
        rushNpc = keep(Holo.npc(ArcadeRoom.rushNpc(world), "<#FF4FD8>Pilote · ORBIT RUSH", Villager.Profession.TOOLSMITH)).getUniqueId();
        volNpc = keep(Holo.npc(ArcadeRoom.volNpc(world), "<#E0B864>Vole mon Orbito", Villager.Profession.CARTOGRAPHER)).getUniqueId();
        casinoNpc = keep(Holo.npc(ArcadeRoom.casinoNpc(world), "<#E0484A>Le Casino", Villager.Profession.LIBRARIAN)).getUniqueId();
        keep(Holo.text(ArcadeRoom.rushNpc(world).add(0, 2.9, 0), "<gradient:#FF4FD8:#62CFF4><b>ORBIT RUSH</b></gradient>\n"
                + "<#E6EDF3>Course sans fin dans les couloirs de la station\n<#9AA4B2>Clic droit pour jouer", 0.9f, false));
        keep(Holo.text(ArcadeRoom.volNpc(world).add(0, 2.9, 0), "<gradient:#E0B864:#62CFF4><b>VOLE MON ORBITO</b></gradient>\n"
                + "<#9AA4B2>Clic droit : direction le serveur", 0.9f, false));
        keep(Holo.text(ArcadeRoom.casinoNpc(world).add(0, 2.9, 0), "<#E0484A><b>♠ LE CASINO ♥</b></#E0484A>\n"
                + "<#9AA4B2>Clic droit : retour aux tables", 0.9f, false));
        keep(Holo.text(new Location(world, 0.5, ArcadeRoom.Y + 7.6, ArcadeRoom.NORTH + 1.6),
                "<gradient:#FF4FD8:#62CFF4><b>ORBIT RUSH</b></gradient>", 2.4f, false));
        keep(Holo.text(new Location(world, 0.5, ArcadeRoom.Y + 2.2, ArcadeRoom.CZ + 0.5),
                "<gradient:#C79BFF:#62CFF4><b>⬢ ARCADE ORBIT ⬢</b></gradient>\n<#9AA4B2>Les records sont au-dessus de chaque borne", 1.0f, false));
        keep(Holo.text(new Location(world, ArcadeRoom.EXIT_X + 0.5, ArcadeRoom.Y + 4.6, ArcadeRoom.EXIT_Z - 0.2),
                "<#9AA4B2>↩ <b>SORTIE</b></#9AA4B2>\n<#5A6270>bouton à côté de la porte", 0.8f, false));
        panels.clear();
        refreshRecords();
    }

    /**
     * Où afficher le top 3 de chaque jeu : au-dessus du PNJ de sa borne (x, z dans la salle, monde casino).
     * Les jeux qui partagent un PNJ (variantes) partagent un panneau. Emplacements du contrat docs/arcade-jeux.md.
     */
    private static final Map<String, double[]> PANEL_AT = new java.util.LinkedHashMap<>();

    static {
        PANEL_AT.put("rush", new double[]{0.5, ArcadeRoom.NORTH + 4.5});
        PANEL_AT.put("vol", new double[]{-ArcadeRoom.HX + 3.5, ArcadeRoom.CZ + 0.5});
        PANEL_AT.put("astroblocs", new double[]{-17.5, 411.5});
        PANEL_AT.put("cristaux", new double[]{-13.5, 411.5});
        PANEL_AT.put("defense", new double[]{-9.5, 411.5});
        PANEL_AT.put("pulsar", new double[]{-5.5, 411.5});
        PANEL_AT.put("arene_gravite", new double[]{6.5, 409.9});
        PANEL_AT.put("arene_comete", new double[]{6.5, 409.9});
        PANEL_AT.put("arene_nova", new double[]{10.5, 409.9});
        PANEL_AT.put("arene_palet", new double[]{14.5, 409.9});
        PANEL_AT.put("arene_turbo", new double[]{18.5, 409.9});
        PANEL_AT.put("siege", new double[]{12.5, 389.5});
        PANEL_AT.put("chasseurs", new double[]{-12.5, 389.5});
        PANEL_AT.put("chasseurs_duo", new double[]{-12.5, 389.5});
        PANEL_AT.put("chasseurs_convoi", new double[]{-12.5, 389.5});
    }

    /** Panneaux de records posés, par emplacement « x z ». */
    private final Map<String, org.bukkit.entity.TextDisplay> panels = new HashMap<>();

    private double[] panelAt(String jeu) {
        String cfg = plugin.getConfig().getString("arcade.records-pnj." + jeu, null);
        if (cfg != null) {
            String[] a = cfg.trim().split("\\s+");
            try {
                return new double[]{Double.parseDouble(a[0]), Double.parseDouble(a[1])};
            } catch (RuntimeException ignored) {
            }
        }
        return PANEL_AT.get(jeu);
    }

    private final List<Entity> spawned = new ArrayList<>();

    private <T extends Entity> T keep(T e) {
        spawned.add(e);
        return e;
    }


    /**
     * Tableau des records au centre de la salle : top 3 de chaque mode arcade (pas le casino).
     * ORBIT RUSH = meilleur score (arcade_rush) ; Vole mon Orbito = revenu par seconde (vol_profils, écrite par OrbitVol).
     */
    public void refreshRecords() {
        if (!plugin.db().available()) {
            return;
        }
        plugin.db().then(plugin.db().run(c -> {
            List<List<String[]>> games = new ArrayList<>();
            for (String sql : new String[]{
                    "SELECT nom, meilleur FROM arcade_rush WHERE meilleur > 0 ORDER BY meilleur DESC LIMIT 3",
                    "SELECT pseudo, revenu FROM vol_profils WHERE revenu > 0 ORDER BY revenu DESC LIMIT 3"}) {
                List<String[]> rows = new ArrayList<>();
                try (java.sql.Statement st = c.createStatement(); java.sql.ResultSet rs = st.executeQuery(sql)) {
                    while (rs.next()) {
                        rows.add(new String[]{rs.getString(1), String.valueOf(rs.getDouble(2))});
                    }
                } catch (java.sql.SQLException e) {
                    // table pas encore créée (mode jamais lancé) : colonne vide
                }
                games.add(rows);
            }
            // jeux des autres plugins : ils se déclarent dans arcade_jeux et écrivent leurs meilleurs scores dans arcade_scores
            List<String[]> declared = new ArrayList<>();
            try (java.sql.Statement st = c.createStatement();
                 java.sql.ResultSet rs = st.executeQuery("SELECT jeu, titre, unite FROM arcade_jeux WHERE actif = 1 ORDER BY ordre, jeu")) {
                while (rs.next()) {
                    declared.add(new String[]{rs.getString(1), rs.getString(2), rs.getString(3)});
                }
            } catch (java.sql.SQLException e) {
                // pas encore de table
            }
            for (String[] d : declared) {
                List<String[]> rows = new ArrayList<>();
                rows.add(d); // première ligne = déclaration du jeu
                try (java.sql.PreparedStatement ps = c.prepareStatement(
                        "SELECT nom, meilleur FROM arcade_scores WHERE jeu = ? AND meilleur > 0 ORDER BY meilleur DESC LIMIT 3")) {
                    ps.setString(1, d[0]);
                    try (java.sql.ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            rows.add(new String[]{rs.getString(1), String.valueOf(rs.getDouble(2))});
                        }
                    }
                } catch (java.sql.SQLException e) {
                    // rien
                }
                games.add(rows);
            }
            return games;
        }), games -> {
            // un panneau par borne ; les variantes d'un même PNJ s'empilent dans son panneau
            Map<String, List<String[]>> groups = new java.util.LinkedHashMap<>();
            Map<String, double[]> where = new HashMap<>();
            java.util.function.BiConsumer<String, String[]> add = (jeu, text) -> {
                double[] at = panelAt(jeu);
                if (at == null) {
                    return; // jeu sans borne connue : pas de panneau
                }
                String k = at[0] + " " + at[1];
                where.put(k, at);
                groups.computeIfAbsent(k, x -> new ArrayList<>()).add(text);
            };
            add.accept("rush", section(null, games.get(0), v -> Txt.n((long) v) + " pts"));
            add.accept("vol", section(null, games.get(1), v -> compact(v) + " ✦/s"));
            for (int i = 2; i < games.size(); i++) {
                List<String[]> rows = new ArrayList<>(games.get(i));
                String[] d = rows.remove(0);
                String unit = d[2] == null || d[2].isBlank() ? "" : " " + d[2];
                add.accept(d[0], section(d[1], rows, v -> (v == Math.floor(v) ? Txt.n((long) v) : compact(v)) + unit));
            }
            for (Map.Entry<String, List<String[]>> g : groups.entrySet()) {
                List<String[]> parts = g.getValue();
                StringBuilder sb = new StringBuilder("<#E0B864><b>🏆 RECORDS</b></#E0B864>");
                for (String[] s : parts) {
                    // une seule variante : pas besoin de répéter le nom du jeu, déjà écrit sous le panneau
                    if (parts.size() > 1 && s[0] != null) {
                        sb.append("\n").append(s[0]);
                    }
                    sb.append(s[1]);
                }
                org.bukkit.entity.TextDisplay td = panels.get(g.getKey());
                if (td == null || !td.isValid()) {
                    double[] at = where.get(g.getKey());
                    // au-dessus du nom du PNJ (texte à +2.9) : on reste bien sous le plafond (y 73)
                    td = keep(Holo.text(new Location(world, at[0], ArcadeRoom.Y + 3.9, at[1]), sb.toString(), 0.55f, false));
                    panels.put(g.getKey(), td);
                } else {
                    Holo.set(td, sb.toString());
                }
            }
        }, null);
    }

    /** Tables partagées des records d'arcade (voir docs/arcade-jeux.md). */
    void createSharedTables() {
        plugin.db().run(c -> {
            try (java.sql.Statement st = c.createStatement()) {
                st.executeUpdate("CREATE TABLE IF NOT EXISTS arcade_jeux ("
                        + "jeu VARCHAR(16) NOT NULL PRIMARY KEY, titre VARCHAR(160) NOT NULL, unite VARCHAR(16) NOT NULL DEFAULT 'pts', "
                        + "ordre INT NOT NULL DEFAULT 100, actif TINYINT NOT NULL DEFAULT 1)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS arcade_scores ("
                        + "jeu VARCHAR(16) NOT NULL, uuid CHAR(36) NOT NULL, nom VARCHAR(16) NOT NULL, meilleur DOUBLE NOT NULL DEFAULT 0, "
                        + "parties INT NOT NULL DEFAULT 0, maj TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, "
                        + "PRIMARY KEY (jeu, uuid), KEY idx_jeu_meilleur (jeu, meilleur))");
            }
            return true;
        });
    }

    private static final String[] MEDALS = {"<#FFE08A>🥇", "<#D8E0EA>🥈", "<#E0A070>🥉"};

    /** {titre (peut être null), lignes du top 3 commençant chacune par un saut de ligne}. */
    private static String[] section(String title, List<String[]> rows, java.util.function.DoubleFunction<String> value) {
        StringBuilder sb = new StringBuilder();
        if (rows.isEmpty()) {
            sb.append("\n<#9AA4B2>Personne encore… à toi !");
        }
        for (int i = 0; i < rows.size() && i < MEDALS.length; i++) {
            sb.append("\n").append(MEDALS[i]).append(" ").append(rows.get(i)[0]).append(" <#E6EDF3>")
                    .append(value.apply(Double.parseDouble(rows.get(i)[1]))).append("</#E6EDF3>");
        }
        return new String[]{title, sb.toString()};
    }

    /** 950, 12,5 k, 3,2 M (même écriture que dans Vole mon Orbito). */
    private static String compact(double v) {
        if (v < 1000) {
            return String.valueOf((long) Math.floor(v));
        }
        String[] suffixes = {" k", " M", " Md", " T"};
        int i = -1;
        while (v >= 1000 && i < suffixes.length - 1) {
            v /= 1000;
            i++;
        }
        String s = v >= 100 ? String.valueOf((long) v) : String.format(Locale.FRANCE, "%.1f", v).replace(",0", "");
        return s + suffixes[i];
    }


    public int rebuild() {
        runner.stopAll();
        for (Player p : world.getPlayers()) {
            if (inRoom(p.getLocation())) {
                p.teleport(ArcadeRoom.spawn(world));
            }
        }
        return ArcadeRoom.build(world);
    }

    static boolean inRoom(Location l) {
        return Math.abs(l.getX()) <= ArcadeRoom.HX + 2 && l.getZ() >= ArcadeRoom.NORTH - 2 && l.getZ() <= ArcadeRoom.SOUTH + 2;
    }

    // ------------------------------------------------------------------ entrées secrètes

    private List<Block> entrances() {
        List<Block> list = new ArrayList<>();
        for (String s : plugin.getConfig().getStringList("arcade.entrees")) {
            String[] a = s.trim().split("\\s+");
            if (a.length < 4) {
                continue;
            }
            World w = Bukkit.getWorld(a[0]);
            try {
                if (w != null) {
                    list.add(w.getBlockAt(Integer.parseInt(a[1]), Integer.parseInt(a[2]), Integer.parseInt(a[3])));
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return list;
    }

    /** Pose un bouton discret sur chaque entrée encore vide (contre le premier mur plein trouvé). */
    public List<String> placeButtons() {
        List<String> out = new ArrayList<>();
        for (Block b : entrances()) {
            if (!b.getType().isAir()) {
                out.add(b.getWorld().getName() + " " + b.getX() + " " + b.getY() + " " + b.getZ() + " : " + b.getType().getKey().getKey());
                continue;
            }
            Material m = b.getWorld().equals(world) ? Material.POLISHED_BLACKSTONE_BUTTON : Material.PALE_OAK_BUTTON;
            for (BlockFace face : new BlockFace[]{BlockFace.EAST, BlockFace.WEST, BlockFace.NORTH, BlockFace.SOUTH}) {
                if (b.getRelative(face.getOppositeFace()).getType().isSolid()) {
                    BlockData d = m.createBlockData();
                    if (d instanceof Switch sw) {
                        sw.setAttachedFace(FaceAttachable.AttachedFace.WALL);
                        sw.setFacing(face);
                    }
                    b.setBlockData(d, false);
                    out.add(b.getWorld().getName() + " " + b.getX() + " " + b.getY() + " " + b.getZ() + " : bouton posé");
                    break;
                }
            }
        }
        return out;
    }

    public void enter(Player p) {
        Location from = p.getLocation();
        if (!inRoom(from) || !from.getWorld().equals(world)) {
            comeback.put(p.getUniqueId(), from.clone());
        }
        p.teleport(ArcadeRoom.spawn(world));
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 1.6f);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 0.9f, 1.4f);
        p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 30, 0.4, 0.8, 0.4, 0.03);
        Txt.send(p, "<gradient:#C79BFF:#62CFF4><b>Bienvenue dans l'arcade cachée d'ORBIT.</b></gradient> Ici, les PNJ t'emmènent "
                + "dans les modes arcade : <#FF4FD8>ORBIT RUSH</#FF4FD8>, <#E0B864>Vole mon Orbito</#E0B864> et le <#E0484A>casino</#E0484A>.");
    }

    public void leave(Player p) {
        Location back = comeback.remove(p.getUniqueId());
        if (back == null || back.getWorld() == null) {
            back = plugin.casino() != null ? fr.orbit.casino.Room.spawn(world) : Bukkit.getWorlds().get(0).getSpawnLocation();
        }
        p.teleport(back);
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.7f, 1.4f);
    }

    public Location rushReturn() {
        return ArcadeRoom.rushReturn(world);
    }

    private boolean pass(Player p) {
        long now = System.currentTimeMillis();
        Long last = lastClick.put(p.getUniqueId(), now);
        return last == null || now - last > 800;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null) {
            return;
        }
        Block b = event.getClickedBlock();
        Player p = event.getPlayer();
        if (b.getWorld().equals(world) && b.getX() == ArcadeRoom.EXIT_X && b.getY() == ArcadeRoom.EXIT_Y && b.getZ() == ArcadeRoom.EXIT_Z) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> leave(p), 4L);
            return;
        }
        for (Block e : entrances()) {
            if (e.equals(b)) {
                if (runner.running(p)) {
                    return;
                }
                if (b.getWorld().equals(world)) {
                    event.setCancelled(false); // le casino annule tout clic dans sa salle : le bouton doit s'enfoncer
                }
                Bukkit.getScheduler().runTaskLater(plugin, () -> enter(p), 4L);
                return;
            }
        }
        // les bornes de déco font un petit bip, rien d'autre
        if (b.getWorld().equals(world) && inRoom(b.getLocation()) && org.bukkit.Tag.BUTTONS.isTagged(b.getType()) && pass(p)) {
            p.playSound(b.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 0.8f, 0.5f + (float) Math.random());
            Txt.bar(p, "<#9AA4B2>Cette borne est en panne… <#62CFF4>ORBIT RUSH</#62CFF4> marche, elle.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Entity e = event.getRightClicked();
        UUID id = e.getUniqueId();
        if (!id.equals(rushNpc) && !id.equals(volNpc) && !id.equals(casinoNpc)) {
            return;
        }
        event.setCancelled(true);
        Player p = event.getPlayer();
        if (event.getHand() != EquipmentSlot.HAND || !pass(p)) {
            return;
        }
        if (id.equals(rushNpc)) {
            runner.play(p);
        } else if (id.equals(volNpc)) {
            Txt.send(p, "<#E0B864>Vole mon Orbito</#E0B864> <#5A6270>»</#5A6270> Accroche-toi, direction le Tapis stellaire !");
            connect(p, plugin.getConfig().getString("arcade.serveur-vol", "vol"));
        } else if (plugin.casino() != null) {
            plugin.casino().enter(p);
        }
    }

    private void connect(Player p, String server) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeUTF("Connect");
            out.writeUTF(server);
            p.sendPluginMessage(plugin, "BungeeCord", bytes.toByteArray());
        } catch (IOException ex) {
            Txt.send(p, "<#E0484A>Serveur injoignable pour l'instant.");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        comeback.remove(event.getPlayer().getUniqueId());
        lastClick.remove(event.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------ commande /arcade

    public void command(CommandSender sender, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        boolean admin = sender.hasPermission("orbitcasino.admin");
        Player p = sender instanceof Player pl ? pl : null;
        switch (sub) {
            case "top", "classement" -> runner.showTop(sender);
            case "rejouer", "jouer", "rush" -> {
                if (p != null) {
                    if (!p.getWorld().equals(world) || !inRoom(p.getLocation())) {
                        Txt.send(p, "<#9AA4B2>ORBIT RUSH se lance depuis la salle d'arcade.");
                    } else {
                        runner.play(p);
                    }
                }
            }
            case "quitter" -> {
                if (p != null && runner.running(p)) {
                    runner.abort(p, "abandon");
                }
            }
            case "tp" -> {
                if (admin && p != null) {
                    enter(p);
                }
            }
            case "generer" -> {
                if (admin) {
                    Txt.send(sender, "Salle d'arcade reconstruite : " + Txt.n(rebuild()) + " blocs.");
                    placeButtons();
                    spawnAll();
                }
            }
            case "bouton" -> {
                if (!admin) {
                    return;
                }
                if (p == null) {
                    placeButtons().forEach(s -> Txt.send(sender, s));
                    return;
                }
                Block b = p.getTargetBlockExact(6);
                if (b == null) {
                    Txt.send(p, "<#E0484A>Vise un bloc à moins de 6 blocs.");
                    return;
                }
                String key = b.getWorld().getName() + " " + b.getX() + " " + b.getY() + " " + b.getZ();
                List<String> list = new ArrayList<>(plugin.getConfig().getStringList("arcade.entrees"));
                boolean remove = args.length > 1 && args[1].equalsIgnoreCase("retirer");
                if (remove) {
                    list.removeIf(s -> s.trim().replaceAll("\\s+", " ").equals(key));
                } else if (!list.contains(key)) {
                    list.add(key);
                }
                plugin.getConfig().set("arcade.entrees", list);
                plugin.saveConfig();
                Txt.send(p, (remove ? "Entrée retirée : " : "Entrée ajoutée : ") + b.getType().getKey().getKey() + " en " + key
                        + " <#9AA4B2>(" + list.size() + " entrée(s))");
            }
            case "entrees" -> {
                if (admin) {
                    for (String s : plugin.getConfig().getStringList("arcade.entrees")) {
                        Txt.send(sender, "• " + s);
                    }
                }
            }
            default -> {
                Txt.send(sender, "<#FF4FD8>ORBIT RUSH</#FF4FD8> : <b>/arcade top</b> (classement), <b>/arcade quitter</b> (abandonner une course).");
                if (admin) {
                    Txt.send(sender, "<#9AA4B2>Admin : /arcade tp · generer · bouton [retirer] · entrees");
                }
            }
        }
    }

    public List<String> tab(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> l = new ArrayList<>(List.of("top", "rejouer", "quitter"));
            if (sender.hasPermission("orbitcasino.admin")) {
                l.addAll(List.of("tp", "generer", "bouton", "entrees"));
            }
            return l;
        }
        return args.length == 2 && args[0].equalsIgnoreCase("bouton") ? List.of("retirer") : List.of();
    }
}
