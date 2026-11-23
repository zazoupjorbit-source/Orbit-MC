package fr.orbit.tycoon;

import io.papermc.paper.ban.BanListType;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.ban.ProfileBanList;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Modération du Tycoon (/staff), sans dépendance externe (OrbitStaff et son API pourront la remplacer plus tard).
 * - Mode staff : inventaire mis de côté (sauvé dans staff/<uuid>.yml contre les crashs), invisible, vol, invincible,
 *   pas de ramassage ; barre d'outils (joueurs, geler, inspecter, TP au hasard, invisibilité, quitter).
 * - Sanctions : geler, mute (minutes), expulser, bannir (jours ou définitif, sur le Tycoon seulement).
 * - Journal de toutes les actions dans staff-journal.log. Permissions : orbittycoon.staff, orbittycoon.staff.ban.
 */
final class Staff implements Listener {

    static final String PERM = "orbittycoon.staff";
    static final String PERM_BAN = "orbittycoon.staff.ban";
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String MOTIF_DEFAUT = "Décision du staff";

    private final OrbitTycoon plugin;
    private final NamespacedKey cleOutil;
    private final File dossier, fichierMutes, journal;

    /** Joueurs en mode staff → contenu de leur inventaire mis de côté. */
    private final Map<UUID, ItemStack[]> enMode = new HashMap<>();
    private final Map<UUID, GameMode> modes = new HashMap<>();
    private final Set<UUID> invisibles = new HashSet<>();
    private final Set<UUID> geles = new HashSet<>();
    /** Joueurs muets → fin du mute (ms). Lu depuis le fil du chat : table concurrente. */
    private final Map<UUID, Long> mutes = new ConcurrentHashMap<>();

    Staff(OrbitTycoon plugin) {
        this.plugin = plugin;
        this.cleOutil = new NamespacedKey(plugin, "staff_outil");
        this.dossier = new File(plugin.getDataFolder(), "staff");
        this.fichierMutes = new File(plugin.getDataFolder(), "staff-mutes.yml");
        this.journal = new File(plugin.getDataFolder(), "staff-journal.log");
    }

    void demarrer() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(fichierMutes);
        long maintenant = System.currentTimeMillis();
        for (String k : y.getKeys(false)) {
            try {
                long fin = y.getLong(k);
                if (fin > maintenant) mutes.put(UUID.fromString(k), fin);
            } catch (IllegalArgumentException ignore) { }
        }
        // Rechargement à chaud : un staff déjà connecté retrouve son inventaire s'il en reste un de côté.
        for (Player p : Bukkit.getOnlinePlayers()) restaurerApresCrash(p);
    }

    void arreter() {
        for (UUID id : new ArrayList<>(enMode.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) desactiver(p, false);
        }
        sauverMutes();
    }

    // ------------------------------------------------------------------ commande

    boolean commande(CommandSender s, String[] args) {
        if (!s.hasPermission(PERM)) { OrbitTycoon.msg(s, "<red>Commande réservée au staff."); return true; }
        String sous = args.length > 0 ? args[0].toLowerCase() : "";
        String cibleNom = args.length > 1 ? args[1] : null;
        switch (sous) {
            case "" -> {
                if (!(s instanceof Player p)) { aide(s); return true; }
                if (enMode.containsKey(p.getUniqueId())) desactiver(p, true); else activer(p);
            }
            case "joueurs", "liste" -> { if (s instanceof Player p) menuJoueurs(p); }
            case "tp" -> { if (s instanceof Player p && enLigne(s, cibleNom) instanceof Player c) teleporter(p, c); }
            case "geler", "freeze" -> { if (enLigne(s, cibleNom) instanceof Player c) basculerGel(s, c); }
            case "inv" -> { if (s instanceof Player p && enLigne(s, cibleNom) instanceof Player c) inspecter(p, c, false); }
            case "ender" -> { if (s instanceof Player p && enLigne(s, cibleNom) instanceof Player c) inspecter(p, c, true); }
            case "mute" -> {
                if (cibleNom == null || args.length < 3 || !args[2].matches("\\d{1,5}")) {
                    OrbitTycoon.msg(s, "Usage : /staff mute <joueur> <minutes> [motif]"); return true;
                }
                OfflinePlayer c = Bukkit.getOfflinePlayer(cibleNom);
                muter(s, c, Integer.parseInt(args[2]), motif(args, 3));
            }
            case "demute", "unmute" -> {
                if (cibleNom == null) { OrbitTycoon.msg(s, "Usage : /staff demute <joueur>"); return true; }
                demuter(s, Bukkit.getOfflinePlayer(cibleNom));
            }
            case "kick", "expulser" -> { if (enLigne(s, cibleNom) instanceof Player c) expulser(s, c, motif(args, 2)); }
            case "ban", "bannir" -> {
                if (!s.hasPermission(PERM_BAN)) { OrbitTycoon.msg(s, "<red>Il te faut la permission " + PERM_BAN + "."); return true; }
                if (cibleNom == null || args.length < 3 || !(args[2].matches("\\d{1,4}") || args[2].equalsIgnoreCase("perm"))) {
                    OrbitTycoon.msg(s, "Usage : /staff ban <joueur> <jours|perm> [motif]"); return true;
                }
                int jours = args[2].equalsIgnoreCase("perm") ? 0 : Math.max(1, Integer.parseInt(args[2]));
                bannir(s, Bukkit.getOfflinePlayer(cibleNom), jours, motif(args, 3));
            }
            case "deban", "unban" -> {
                if (!s.hasPermission(PERM_BAN)) { OrbitTycoon.msg(s, "<red>Il te faut la permission " + PERM_BAN + "."); return true; }
                if (cibleNom == null) { OrbitTycoon.msg(s, "Usage : /staff deban <joueur>"); return true; }
                debannir(s, Bukkit.getOfflinePlayer(cibleNom));
            }
            case "chat", "c" -> {
                if (args.length < 2) { OrbitTycoon.msg(s, "Usage : /staff chat <message>"); return true; }
                alerte("<#62CFF4>" + s.getName() + "<dark_gray> » <white>" + MM.escapeTags(String.join(" ", Arrays.copyOfRange(args, 1, args.length))));
            }
            case "journal" -> afficherJournal(s);
            default -> aide(s);
        }
        return true;
    }

    List<String> completer(String[] args) {
        if (args.length == 1) return filtre(args[0], List.of("joueurs", "tp", "geler", "inv", "ender", "mute", "demute",
                "kick", "ban", "deban", "chat", "journal", "aide"));
        if (args.length == 2 && !args[0].equalsIgnoreCase("chat")) return null;   // noms des joueurs
        if (args.length == 3 && args[0].equalsIgnoreCase("mute")) return filtre(args[2], List.of("10", "30", "60", "1440"));
        if (args.length == 3 && args[0].equalsIgnoreCase("ban")) return filtre(args[2], List.of("1", "3", "7", "30", "perm"));
        return List.of();
    }

    private static List<String> filtre(String debut, List<String> l) {
        return l.stream().filter(x -> x.startsWith(debut.toLowerCase())).toList();
    }

    private void aide(CommandSender s) {
        OrbitTycoon.msg(s, "<#E0B864><bold>Modération du Tycoon</bold>");
        for (String l : List.of(
                "/staff <gray>: active ou quitte le mode staff",
                "/staff joueurs <gray>: menu des joueurs connectés",
                "/staff tp <joueur> <gray>· geler <joueur> · inv <joueur> · ender <joueur>",
                "/staff mute <joueur> <minutes> [motif] <gray>· demute <joueur>",
                "/staff kick <joueur> [motif]",
                "/staff ban <joueur> <jours|perm> [motif] <gray>· deban <joueur>",
                "/staff chat <message> <gray>: chat privé du staff",
                "/staff journal <gray>: 10 dernières actions"))
            s.sendMessage(MM.deserialize("<#62CFF4> " + l));
    }

    private Player enLigne(CommandSender s, String nom) {
        if (nom == null) { OrbitTycoon.msg(s, "Précise un joueur. <gray>(/staff aide)"); return null; }
        Player c = Bukkit.getPlayerExact(nom);
        if (c == null) OrbitTycoon.msg(s, "<red>" + MM.escapeTags(nom) + " n'est pas connecté.");
        return c;
    }

    private static String motif(String[] args, int debut) {
        return args.length > debut ? String.join(" ", Arrays.copyOfRange(args, debut, args.length)) : MOTIF_DEFAUT;
    }

    // ------------------------------------------------------------------ mode staff

    private void activer(Player p) {
        ItemStack[] contenu = p.getInventory().getContents();
        // Sauvegarde sur disque d'abord : si le serveur plante, l'inventaire revient à la reconnexion.
        YamlConfiguration y = new YamlConfiguration();
        y.set("contenu", Arrays.asList(contenu));
        y.set("mode", p.getGameMode().name());
        try {
            dossier.mkdirs();
            y.save(new File(dossier, p.getUniqueId() + ".yml"));
        } catch (IOException ex) {
            OrbitTycoon.msg(p, "<red>Impossible de mettre ton inventaire de côté : mode staff annulé.");
            plugin.getLogger().warning("Staff : sauvegarde de l'inventaire de " + p.getName() + " impossible : " + ex.getMessage());
            return;
        }
        enMode.put(p.getUniqueId(), contenu);
        modes.put(p.getUniqueId(), p.getGameMode());
        p.getInventory().clear();
        if (p.getGameMode() != GameMode.CREATIVE && p.getGameMode() != GameMode.SPECTATOR) p.setGameMode(GameMode.ADVENTURE);
        p.setAllowFlight(true);
        p.setFlying(true);
        p.setInvulnerable(true);
        p.setCanPickupItems(false);
        donnerOutils(p);
        rendreInvisible(p, true);
        OrbitTycoon.msg(p, "<#7CFC9A>Mode staff activé.<gray> Tu es invisible. <dark_gray>(/staff pour quitter)");
        alerte("<gray>" + p.getName() + " passe en mode staff.");
        noter(p.getName(), "MODE STAFF", "-", "activé");
    }

    private void desactiver(Player p, boolean message) {
        ItemStack[] contenu = enMode.remove(p.getUniqueId());
        GameMode mode = modes.remove(p.getUniqueId());
        rendreInvisible(p, false);
        p.getInventory().clear();
        if (contenu != null) p.getInventory().setContents(contenu);
        if (mode != null) p.setGameMode(mode);
        if (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE) {
            p.setFlying(false);
            p.setAllowFlight(false);
        }
        p.setInvulnerable(false);
        p.setCanPickupItems(true);
        new File(dossier, p.getUniqueId() + ".yml").delete();
        if (message) {
            OrbitTycoon.msg(p, "<gray>Mode staff désactivé, inventaire rendu.");
            alerte("<gray>" + p.getName() + " quitte le mode staff.");
        }
    }

    /** Inventaire resté de côté (crash pendant le mode staff) : on le rend. */
    @SuppressWarnings("unchecked")
    private void restaurerApresCrash(Player p) {
        if (enMode.containsKey(p.getUniqueId())) return;
        File f = new File(dossier, p.getUniqueId() + ".yml");
        if (!f.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        List<ItemStack> l = (List<ItemStack>) y.getList("contenu", List.of());
        p.getInventory().setContents(l.toArray(new ItemStack[0]));
        try { p.setGameMode(GameMode.valueOf(y.getString("mode", "SURVIVAL"))); } catch (IllegalArgumentException ignore) { }
        p.setInvulnerable(false);
        p.setCanPickupItems(true);
        f.delete();
        OrbitTycoon.msg(p, "<gray>Ton inventaire d'avant le mode staff t'a été rendu.");
    }

    boolean enMode(Player p) { return enMode.containsKey(p.getUniqueId()); }

    private void rendreInvisible(Player p, boolean oui) {
        if (oui) invisibles.add(p.getUniqueId()); else invisibles.remove(p.getUniqueId());
        for (Player autre : Bukkit.getOnlinePlayers()) {
            if (autre.equals(p)) continue;
            if (oui && !autre.hasPermission(PERM)) autre.hidePlayer(plugin, p);
            else autre.showPlayer(plugin, p);
        }
        if (enMode(p)) p.getInventory().setItem(7, outil(oui ? Material.LIME_DYE : Material.GRAY_DYE, "invisible",
                oui ? "<#7CFC9A>Invisible" : "<gray>Visible", "<gray>Clic droit : changer"));
    }

    private void donnerOutils(Player p) {
        var inv = p.getInventory();
        inv.setItem(0, outil(Material.COMPASS, "joueurs", "<#62CFF4>Joueurs connectés", "<gray>Clic droit : menu des joueurs"));
        inv.setItem(1, outil(Material.PACKED_ICE, "geler", "<#A8E9FF>Geler", "<gray>Clic droit sur un joueur : geler / dégeler"));
        inv.setItem(2, outil(Material.BOOK, "inspecter", "<#E0B864>Inspecter", "<gray>Clic droit sur un joueur : son inventaire"));
        inv.setItem(4, outil(Material.ENDER_EYE, "hasard", "<#C77DFF>Joueur au hasard", "<gray>Clic droit : TP sur un joueur au hasard"));
        inv.setItem(8, outil(Material.BARRIER, "quitter", "<red>Quitter le mode staff", "<gray>Clic droit : inventaire rendu"));
    }

    private ItemStack outil(Material m, String id, String nom, String lore) {
        ItemStack it = new ItemStack(m);
        it.editMeta(meta -> {
            meta.itemName(MM.deserialize(nom));
            meta.lore(List.of(MM.deserialize("<!italic>" + lore)));
            meta.getPersistentDataContainer().set(cleOutil, PersistentDataType.STRING, id);
        });
        return it;
    }

    private String idOutil(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(cleOutil, PersistentDataType.STRING);
    }

    // ------------------------------------------------------------------ outils en main

    @EventHandler(priority = EventPriority.LOWEST)
    public void utiliser(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !enMode(e.getPlayer())) return;
        String id = idOutil(e.getItem());
        if (id == null) return;
        e.setCancelled(true);
        if (!e.getAction().isRightClick()) return;
        Player p = e.getPlayer();
        switch (id) {
            case "joueurs" -> menuJoueurs(p);
            case "hasard" -> {
                List<Player> l = Bukkit.getOnlinePlayers().stream().filter(x -> !x.equals(p) && !enMode(x)).map(x -> (Player) x).toList();
                if (l.isEmpty()) OrbitTycoon.msg(p, "<gray>Personne d'autre n'est connecté.");
                else teleporter(p, l.get(ThreadLocalRandom.current().nextInt(l.size())));
            }
            case "invisible" -> rendreInvisible(p, !invisibles.contains(p.getUniqueId()));
            case "quitter" -> desactiver(p, true);
            default -> OrbitTycoon.msg(p, "<gray>Fais un clic droit <white>sur un joueur</white> avec cet outil.");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void surJoueur(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !(e.getRightClicked() instanceof Player c) || !enMode(e.getPlayer())) return;
        String id = idOutil(e.getPlayer().getInventory().getItemInMainHand());
        if (id == null) return;
        e.setCancelled(true);
        switch (id) {
            case "geler" -> basculerGel(e.getPlayer(), c);
            case "inspecter" -> inspecter(e.getPlayer(), c, false);
            default -> menuActions(e.getPlayer(), c);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void jeter(PlayerDropItemEvent e) {
        if (enMode(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void poser(BlockPlaceEvent e) {
        if (enMode(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void cible(EntityTargetEvent e) {
        if (e.getTarget() instanceof Player p && enMode(p)) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ menus

    private static final class MenuJoueurs implements InventoryHolder {
        private Inventory inv;
        private final List<UUID> ordre = new ArrayList<>();
        @Override public Inventory getInventory() { return inv; }
    }

    private static final class MenuActions implements InventoryHolder {
        private Inventory inv;
        private final UUID cible;
        MenuActions(UUID cible) { this.cible = cible; }
        @Override public Inventory getInventory() { return inv; }
    }

    /** Vue en lecture seule (inventaire ou coffre de l'Ender d'un joueur). */
    private static final class Inspection implements InventoryHolder {
        private Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    private void menuJoueurs(Player p) {
        MenuJoueurs h = new MenuJoueurs();
        h.inv = Bukkit.createInventory(h, 54, Habillage.titre(plugin, p, 6, "Staff · Joueurs"));
        for (Player c : Bukkit.getOnlinePlayers()) {
            if (h.ordre.size() >= 45) break;
            if (c.equals(p)) continue;
            ItemStack tete = new ItemStack(Material.PLAYER_HEAD);
            tete.editMeta(SkullMeta.class, m -> {
                m.setOwningPlayer(c);
                m.itemName(MM.deserialize("<#E0B864>" + c.getName()));
                List<Component> lore = new ArrayList<>();
                lore.add(MM.deserialize("<!italic><gray>Monde : <white>" + c.getWorld().getName()));
                if (geles.contains(c.getUniqueId())) lore.add(MM.deserialize("<!italic><#A8E9FF>Gelé"));
                if (muet(c.getUniqueId())) lore.add(MM.deserialize("<!italic><red>Muet"));
                if (enMode(c)) lore.add(MM.deserialize("<!italic><#7CFC9A>En mode staff"));
                lore.add(MM.deserialize("<!italic><dark_gray>Clic : actions"));
                m.lore(lore);
            });
            h.inv.setItem(h.ordre.size(), tete);
            h.ordre.add(c.getUniqueId());
        }
        if (h.ordre.isEmpty()) h.inv.setItem(22, info(Material.BARRIER, "<gray>Personne d'autre n'est connecté"));
        p.openInventory(h.inv);
    }

    private void menuActions(Player p, Player c) {
        MenuActions h = new MenuActions(c.getUniqueId());
        h.inv = Bukkit.createInventory(h, 27, Habillage.titre(plugin, p, 3, "Staff · " + c.getName()));
        boolean gele = geles.contains(c.getUniqueId());
        h.inv.setItem(10, info(Material.ENDER_PEARL, "<#62CFF4>Se téléporter", "<gray>Aller sur " + c.getName()));
        h.inv.setItem(11, info(Material.PACKED_ICE, gele ? "<#A8E9FF>Dégeler" : "<#A8E9FF>Geler", "<gray>Bloque ses déplacements"));
        h.inv.setItem(12, info(Material.CHEST, "<#E0B864>Inventaire", "<gray>Lecture seule"));
        h.inv.setItem(13, info(Material.ENDER_CHEST, "<#C77DFF>Coffre de l'Ender", "<gray>Lecture seule"));
        h.inv.setItem(14, info(Material.PAPER, muet(c.getUniqueId()) ? "<red>Muet · clic pour démuter" : "<red>Rendre muet",
                "<gray>Clic gauche : 10 min", "<gray>Clic droit : 1 h", "<dark_gray>Motif précis : /staff mute"));
        h.inv.setItem(15, info(Material.IRON_DOOR, "<#FF9A3B>Expulser", "<gray>Motif : " + MOTIF_DEFAUT, "<dark_gray>Motif précis : /staff kick"));
        h.inv.setItem(16, info(Material.BARRIER, "<#FF4D4D>Bannir du Tycoon",
                "<gray>Clic gauche : 1 jour", "<gray>Shift + clic : définitif", "<dark_gray>Motif précis : /staff ban"));
        h.inv.setItem(22, info(Material.ARROW, "<gray>Retour"));
        Habillage.fond(plugin, p, h.inv);
        p.openInventory(h.inv);
    }

    private void inspecter(Player p, Player c, boolean ender) {
        Inspection h = new Inspection();
        String titre = (ender ? "Ender · " : "Inv · ") + c.getName();
        if (ender) {
            h.inv = Bukkit.createInventory(h, 27, Habillage.titre(plugin, p, 3, titre));
            ItemStack[] contenu = c.getEnderChest().getContents();
            for (int i = 0; i < contenu.length && i < 27; i++) if (contenu[i] != null) h.inv.setItem(i, contenu[i].clone());
        } else {
            h.inv = Bukkit.createInventory(h, 54, Habillage.titre(plugin, p, 6, titre));
            ItemStack[] sac = c.getInventory().getStorageContents();
            for (int i = 0; i < sac.length && i < 36; i++) if (sac[i] != null) h.inv.setItem(i, sac[i].clone());
            ItemStack[] armure = c.getInventory().getArmorContents();
            for (int i = 0; i < armure.length; i++) if (armure[i] != null) h.inv.setItem(45 + i, armure[i].clone());
            h.inv.setItem(49, c.getInventory().getItemInOffHand().clone());
            h.inv.setItem(53, info(Material.EXPERIENCE_BOTTLE, "<#E0B864>" + c.getName(),
                    "<gray>Vie : <white>" + (int) c.getHealth(), "<gray>Mode : <white>" + c.getGameMode().name().toLowerCase(),
                    "<gray>Position : <white>" + c.getLocation().getBlockX() + " " + c.getLocation().getBlockY() + " " + c.getLocation().getBlockZ()));
        }
        p.openInventory(h.inv);
        noter(p.getName(), ender ? "ENDER" : "INVENTAIRE", c.getName(), "consulté");
    }

    private static ItemStack info(Material m, String nom, String... lore) {
        ItemStack it = new ItemStack(m);
        it.editMeta(meta -> {
            meta.itemName(MM.deserialize(nom));
            if (lore.length > 0) meta.lore(Arrays.stream(lore).map(l -> MM.deserialize("<!italic>" + l)).toList());
        });
        return it;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void clicMenu(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        InventoryHolder h = e.getView().getTopInventory().getHolder();
        // Outils du mode staff : on ne les déplace pas.
        if (enMode(p) && (idOutil(e.getCurrentItem()) != null || idOutil(e.getCursor()) != null
                || e.getClick() == ClickType.NUMBER_KEY)) e.setCancelled(true);
        if (!(h instanceof MenuJoueurs) && !(h instanceof MenuActions) && !(h instanceof Inspection)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        int slot = e.getRawSlot();
        if (h instanceof MenuJoueurs m) {
            if (slot < m.ordre.size()) {
                Player c = Bukkit.getPlayer(m.ordre.get(slot));
                if (c != null) menuActions(p, c); else menuJoueurs(p);
            }
        } else if (h instanceof MenuActions m) {
            Player c = Bukkit.getPlayer(m.cible);
            if (slot == 22) { menuJoueurs(p); return; }
            if (c == null) { OrbitTycoon.msg(p, "<gray>Ce joueur s'est déconnecté."); p.closeInventory(); return; }
            switch (slot) {
                case 10 -> { p.closeInventory(); teleporter(p, c); }
                case 11 -> { basculerGel(p, c); menuActions(p, c); }
                case 12 -> inspecter(p, c, false);
                case 13 -> inspecter(p, c, true);
                case 14 -> {
                    if (muet(c.getUniqueId())) demuter(p, c);
                    else muter(p, c, e.isRightClick() ? 60 : 10, MOTIF_DEFAUT);
                    menuActions(p, c);
                }
                case 15 -> { p.closeInventory(); expulser(p, c, MOTIF_DEFAUT); }
                case 16 -> {
                    if (!p.hasPermission(PERM_BAN)) { OrbitTycoon.msg(p, "<red>Il te faut la permission " + PERM_BAN + "."); return; }
                    p.closeInventory();
                    bannir(p, c, e.isShiftClick() ? 0 : 1, MOTIF_DEFAUT);
                }
                default -> { }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void glisserMenu(InventoryDragEvent e) {
        InventoryHolder h = e.getView().getTopInventory().getHolder();
        if (h instanceof MenuJoueurs || h instanceof MenuActions || h instanceof Inspection) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ actions

    private void teleporter(Player p, Player c) {
        p.teleportAsync(c.getLocation());
        OrbitTycoon.msg(p, "<gray>Téléporté sur <white>" + c.getName() + "<gray>.");
    }

    private void basculerGel(CommandSender s, Player c) {
        if (geles.remove(c.getUniqueId())) {
            OrbitTycoon.msg(c, "<#7CFC9A>Tu n'es plus gelé.");
            c.clearTitle();
            alerte("<gray>" + s.getName() + " a dégelé " + c.getName() + ".");
            noter(s.getName(), "DEGEL", c.getName(), "-");
        } else {
            geles.add(c.getUniqueId());
            c.showTitle(Title.title(MM.deserialize("<#A8E9FF><bold>GELÉ"), MM.deserialize("<gray>Un membre du staff va te parler. Ne te déconnecte pas."),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(60), Duration.ofMillis(500))));
            c.playSound(c.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 0.6f);
            OrbitTycoon.msg(c, "<#A8E9FF>Tu es gelé par le staff. <gray>Ne te déconnecte pas, quelqu'un va te parler.");
            alerte("<gray>" + s.getName() + " a gelé " + c.getName() + ".");
            noter(s.getName(), "GEL", c.getName(), "-");
        }
    }

    private boolean muet(UUID id) {
        Long fin = mutes.get(id);
        if (fin == null) return false;
        if (fin > System.currentTimeMillis()) return true;
        mutes.remove(id);
        return false;
    }

    private void muter(CommandSender s, OfflinePlayer c, int minutes, String motif) {
        minutes = Math.max(1, minutes);
        mutes.put(c.getUniqueId(), System.currentTimeMillis() + minutes * 60_000L);
        sauverMutes();
        if (c.getPlayer() != null) OrbitTycoon.msg(c.getPlayer(), "<red>Tu es muet pendant " + duree(minutes) + ". <gray>Motif : " + MM.escapeTags(motif));
        alerte("<gray>" + s.getName() + " a rendu muet " + c.getName() + " (" + duree(minutes) + ") : " + MM.escapeTags(motif));
        noter(s.getName(), "MUTE " + duree(minutes), c.getName(), motif);
    }

    private void demuter(CommandSender s, OfflinePlayer c) {
        if (mutes.remove(c.getUniqueId()) == null) { OrbitTycoon.msg(s, "<gray>" + c.getName() + " n'était pas muet."); return; }
        sauverMutes();
        if (c.getPlayer() != null) OrbitTycoon.msg(c.getPlayer(), "<#7CFC9A>Tu peux de nouveau parler.");
        alerte("<gray>" + s.getName() + " a démuté " + c.getName() + ".");
        noter(s.getName(), "DEMUTE", c.getName(), "-");
    }

    private void expulser(CommandSender s, Player c, String motif) {
        c.kick(MM.deserialize("<#FF9A3B><bold>Expulsé du Tycoon</bold>\n<gray>Motif : <white>" + MM.escapeTags(motif)));
        alerte("<gray>" + s.getName() + " a expulsé " + c.getName() + " : " + MM.escapeTags(motif));
        noter(s.getName(), "KICK", c.getName(), motif);
    }

    /** jours = 0 : définitif. Ban du serveur Tycoon seulement (les autres serveurs du réseau ne sont pas touchés). */
    private void bannir(CommandSender s, OfflinePlayer c, int jours, String motif) {
        Duration d = jours > 0 ? Duration.ofDays(jours) : null;
        String quand = jours > 0 ? jours + " jour(s)" : "définitif";
        c.ban(motif, d, s.getName());
        if (c.getPlayer() != null) c.getPlayer().kick(MM.deserialize("<#FF4D4D><bold>Banni du Tycoon</bold> <gray>(" + quand
                + ")\n<gray>Motif : <white>" + MM.escapeTags(motif)));
        alerte("<gray>" + s.getName() + " a banni " + c.getName() + " (" + quand + ") : " + MM.escapeTags(motif));
        noter(s.getName(), "BAN " + quand, c.getName(), motif);
    }

    private void debannir(CommandSender s, OfflinePlayer c) {
        ProfileBanList liste = Bukkit.getBanList(BanListType.PROFILE);
        if (!c.isBanned()) { OrbitTycoon.msg(s, "<gray>" + c.getName() + " n'est pas banni."); return; }
        liste.pardon(c.getPlayerProfile());
        alerte("<gray>" + s.getName() + " a débanni " + c.getName() + ".");
        noter(s.getName(), "DEBAN", c.getName(), "-");
    }

    private static String duree(int minutes) {
        if (minutes % 1440 == 0) return minutes / 1440 + " j";
        if (minutes % 60 == 0) return minutes / 60 + " h";
        return minutes + " min";
    }

    // ------------------------------------------------------------------ événements joueurs

    @EventHandler(ignoreCancelled = true)
    public void bouger(PlayerMoveEvent e) {
        if (!geles.contains(e.getPlayer().getUniqueId()) || !e.hasChangedPosition()) return;
        Location bloque = e.getFrom().clone();
        bloque.setYaw(e.getTo().getYaw());
        bloque.setPitch(e.getTo().getPitch());
        e.setTo(bloque);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void chat(AsyncChatEvent e) {
        Long fin = mutes.get(e.getPlayer().getUniqueId());
        if (fin == null || fin <= System.currentTimeMillis()) return;
        e.setCancelled(true);
        long min = Math.max(1, (fin - System.currentTimeMillis()) / 60_000);
        OrbitTycoon.msg(e.getPlayer(), "<red>Tu es muet encore " + min + " min.");
    }

    @EventHandler
    public void connexion(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        restaurerApresCrash(p);
        if (!p.hasPermission(PERM)) for (UUID id : invisibles) {
            Player staff = Bukkit.getPlayer(id);
            if (staff != null) p.hidePlayer(plugin, staff);
        }
        if (geles.contains(p.getUniqueId())) {
            OrbitTycoon.msg(p, "<#A8E9FF>Tu es toujours gelé par le staff.");
            alerte("<gray>" + p.getName() + " (gelé) s'est reconnecté.");
        }
    }

    @EventHandler
    public void deconnexion(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (enMode(p)) desactiver(p, false);
        if (geles.contains(p.getUniqueId())) alerte("<red>" + p.getName() + " s'est déconnecté en étant gelé !");
    }

    // ------------------------------------------------------------------ alertes et journal

    private void alerte(String mm) {
        Component c = MM.deserialize("<dark_gray>[<#62CFF4>Staff<dark_gray>] " + mm);
        for (Player p : Bukkit.getOnlinePlayers()) if (p.hasPermission(PERM)) p.sendMessage(c);
        Bukkit.getConsoleSender().sendMessage(c);
    }

    private void noter(String staff, String action, String cible, String motif) {
        String ligne = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) + " | " + staff + " | "
                + action + " | " + cible + " | " + motif.replace('\n', ' ') + System.lineSeparator();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            synchronized (journal) {
                try {
                    Files.writeString(journal.toPath(), ligne, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                } catch (IOException ex) {
                    plugin.getLogger().warning("Staff : journal impossible à écrire : " + ex.getMessage());
                }
            }
        });
    }

    private void afficherJournal(CommandSender s) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<String> lignes;
            synchronized (journal) {
                try { lignes = journal.exists() ? Files.readAllLines(journal.toPath(), StandardCharsets.UTF_8) : List.of(); }
                catch (IOException ex) { lignes = List.of(); }
            }
            List<String> fin = lignes.subList(Math.max(0, lignes.size() - 10), lignes.size());
            Bukkit.getScheduler().runTask(plugin, () -> {
                OrbitTycoon.msg(s, "<#E0B864>Journal du staff <gray>(" + fin.size() + " dernières actions)");
                if (fin.isEmpty()) s.sendMessage(MM.deserialize("<gray> Aucune action pour l'instant."));
                for (String l : fin) s.sendMessage(MM.deserialize("<gray> " + MM.escapeTags(l)));
            });
        });
    }

    private void sauverMutes() {
        YamlConfiguration y = new YamlConfiguration();
        mutes.forEach((id, fin) -> y.set(id.toString(), fin));
        try { y.save(fichierMutes); }
        catch (IOException ex) { plugin.getLogger().warning("Staff : mutes impossibles à sauver : " + ex.getMessage()); }
    }
}
