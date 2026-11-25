package fr.orbit.tele;

import fr.orbit.tele.jeux.Manette;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.minecraft.world.level.material.MapColor;
import org.bukkit.Bukkit;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import javax.imageio.ImageIO;
import java.io.File;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * ORBIT TV : des écrans en cartes dans le monde, sans mod ni pack côté joueur. Un écran montre soit un partage
 * d'écran venu de la page web, soit la console de jeux (un joueur assis devant), soit l'image de veille.
 */
public final class OrbitTele extends JavaPlugin implements Listener {

    private static final String PREFIXE = "<#62CFF4><b>ORBIT TV</b></#62CFF4> <dark_gray>»</dark_gray> ";

    private final Map<String, Ecran> ecrans = new LinkedHashMap<>();
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private NamespacedKey cle;
    private volatile Palette palette;
    private ScheduledExecutorService horloge;
    private Scores scores;
    private Web web;
    private Tunnel tunnel;
    private File fichier;
    private int prochain = 1, compteur;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        cle = new NamespacedKey(this, "ecran");
        scores = new Scores(this);
        fichier = new File(getDataFolder(), "ecrans.yml");
        horloge = Executors.newScheduledThreadPool(3, r -> {
            Thread t = new Thread(r, "OrbitTele");
            t.setDaemon(true);
            return t;
        });
        int[] bases = new int[64];
        for (int i = 1; i < 64; i++) {
            try {
                MapColor c = MapColor.byId(i);
                bases[i] = c == null ? 0 : c.col;
            } catch (RuntimeException ignored) {
                // teinte inexistante dans cette version
            }
        }
        int force = getConfig().getInt("ecran.tramage", 22);
        horloge.execute(() -> {
            long t = System.currentTimeMillis();
            palette = new Palette(bases, force);
            getLogger().info("Palette des cartes prête en " + (System.currentTimeMillis() - t) + " ms.");
        });
        charger();
        web = new Web(this);
        try {
            web.demarrer(getConfig().getString("web.adresse", "127.0.0.1"), getConfig().getInt("web.port", 8788), sonPortee(), sonPlein());
            getLogger().info("Page de diffusion sur le port " + web.port() + " (lien personnel : /tele lien).");
            web.diffusionDistante(getConfig().getBoolean("web.diffusion-a-distance", false));
            String fixe = getConfig().getString("web.url-publique", "");
            if (getConfig().getBoolean("web.tunnel", true) && (fixe == null || fixe.isBlank())) {
                tunnel = new Tunnel(getLogger(), getDataFolder().toPath(), web.port());
                tunnel.demarrer();
            }
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Page de diffusion indisponible : " + e.getMessage());
        }
        getServer().getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, this::tic, 60L, 10L);
        horloge.scheduleAtFixedRate(this::veille, 2, 1, TimeUnit.SECONDS);
    }

    @Override
    public void onDisable() {
        new ArrayList<>(sessions.values()).forEach(s -> s.arreter(true));
        ecrans.values().forEach(e -> e.retirer(false));
        if (tunnel != null) {
            tunnel.arreter();
        }
        if (web != null) {
            web.arreter();
        }
        if (horloge != null) {
            horloge.shutdownNow();
        }
    }

    // ------------------------------------------------------------------ accès

    NamespacedKey cle() {
        return cle;
    }

    Palette palette() {
        return palette;
    }

    Scores scores() {
        return scores;
    }

    ScheduledExecutorService horloge() {
        return horloge;
    }

    Map<UUID, Session> sessions() {
        return sessions;
    }

    Collection<Ecran> ecrans() {
        return List.copyOf(ecrans.values());
    }

    Ecran ecran(String nom) {
        return ecrans.get(nom.toLowerCase(Locale.ROOT));
    }

    double rayon() {
        return getConfig().getDouble("ecran.rayon", 48);
    }

    /** Période de l'image complète renvoyée à tous, en demi-secondes (0 = jamais). */
    int imageDeControle() {
        return getConfig().getInt("ecran.image-de-controle-secondes", 30) * 2;
    }

    /** Distance à laquelle le son d'une télé ne s'entend plus, et distance jusqu'où il est à plein volume. */
    double sonPortee() {
        return Math.max(4, getConfig().getDouble("son.portee", 32));
    }

    double sonPlein() {
        return Math.max(0, Math.min(sonPortee() - 1, getConfig().getDouble("son.plein", 6)));
    }

    /** Adresse https de la page web hors de ce PC : celle de la config si elle est réglée, sinon celle du tunnel (null tant qu'il n'est pas prêt). */
    private String adressePublique() {
        String base = getConfig().getString("web.url-publique", "");
        if (base != null && !base.isBlank()) {
            return base.replaceAll("/+$", "");
        }
        return tunnel == null ? null : tunnel.adresse();
    }

    /**
     * Le joueur joue-t-il sur le PC du serveur ? On regarde l'adresse qu'il a tapée pour se connecter (le proxy la
     * transmet) : « localhost » ici, l'adresse du tunnel de jeu pour les autres. Sans cette information, on se rabat sur son IP.
     */
    private static boolean surCePc(Player p) {
        InetSocketAddress tape = p.getVirtualHost();
        if (tape == null) {
            return p.getAddress() != null && p.getAddress().getAddress() != null && p.getAddress().getAddress().isLoopbackAddress();
        }
        String h = tape.getHostString().toLowerCase(Locale.ROOT);
        return h.equals("localhost") || h.equals("localhost.") || h.startsWith("127.") || h.equals("::1") || h.equals("0:0:0:0:0:0:0:1");
    }

    /** La page d'écoute se joint sur ce PC, ou ailleurs dès que l'adresse publique (tunnel) est prête. */
    boolean sonOuvert(Player p) {
        return web != null && web.port() != 0 && (adressePublique() != null || surCePc(p));
    }

    Component mm(String s) {
        return MiniMessage.miniMessage().deserialize(s);
    }

    private void dire(CommandSender a, String s) {
        a.sendMessage(mm(PREFIXE + s));
    }

    // ------------------------------------------------------------------ boucles

    private void tic() {
        compteur++;
        long maintenant = System.currentTimeMillis();
        for (Ecran e : ecrans.values()) {
            if (e.mode == Ecran.Mode.DIRECT && maintenant - e.derniereImage > 4000) {
                e.mode = Ecran.Mode.VEILLE;
            }
            e.tic(compteur);
        }
        sessions.values().forEach(Session::surveiller);
        if (web != null) {
            web.suivre();
        }
    }

    private void veille() {
        try {
            if (palette == null) {
                return;
            }
            int tour = (int) (System.currentTimeMillis() / 8000);
            for (Ecran e : ecrans()) {
                if (e.mode == Ecran.Mode.VEILLE) {
                    int[] img = Veille.image(this, e, tour);
                    if (e.mode == Ecran.Mode.VEILLE) {
                        e.pousser(img, false);
                    }
                }
            }
        } catch (Throwable t) {
            getLogger().log(Level.WARNING, "Image de veille en erreur", t);
        }
    }

    // ------------------------------------------------------------------ fichier des écrans

    private void charger() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(fichier);
        prochain = y.getInt("prochain", 1);
        ConfigurationSection s = y.getConfigurationSection("ecrans");
        if (s == null) {
            return;
        }
        for (String nom : s.getKeys(false)) {
            ConfigurationSection c = s.getConfigurationSection(nom);
            Ecran e = new Ecran(this, nom, c.getString("monde"), c.getInt("x"), c.getInt("y"), c.getInt("z"),
                    BlockFace.valueOf(c.getString("face", "SOUTH")), c.getInt("largeur"), c.getInt("hauteur"), c.getInt("base"));
            for (String b : c.getStringList("blocs")) {
                String[] p = b.split(" ");
                e.blocs.add(new int[]{Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])});
            }
            String[] p = c.getString("console", "").split(" ");
            if (p.length == 3) {
                // le monde peut ne pas être chargé encore : il est retrouvé à la pose
                e.console = new Location(null, Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]));
            }
            ecrans.put(nom, e);
        }
        getLogger().info(ecrans.size() + " écran(s) chargé(s).");
    }

    private void enregistrer() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("prochain", prochain);
        for (Ecran e : ecrans.values()) {
            String k = "ecrans." + e.nom + ".";
            y.set(k + "monde", e.mondeNom);
            y.set(k + "x", e.ox);
            y.set(k + "y", e.oy);
            y.set(k + "z", e.oz);
            y.set(k + "face", e.face.name());
            y.set(k + "largeur", e.l);
            y.set(k + "hauteur", e.h);
            y.set(k + "base", e.baseId);
            y.set(k + "blocs", e.blocs.stream().map(b -> b[0] + " " + b[1] + " " + b[2]).toList());
            if (e.console != null) {
                y.set(k + "console", e.console.getX() + " " + e.console.getY() + " " + e.console.getZ());
            }
        }
        try {
            y.save(fichier);
        } catch (Exception ex) {
            getLogger().warning("ecrans.yml non enregistré : " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------ commande

    @Override
    public boolean onCommand(CommandSender a, Command cmd, String label, String[] args) {
        String sous = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (sous.equals("quitter")) {
            if (a instanceof Player p && sessions.get(p.getUniqueId()) != null) {
                sessions.get(p.getUniqueId()).partir(p, true);
            }
            return true;
        }
        if (sous.equals("son")) {
            if (!(a instanceof Player p)) {
                dire(a, "<gray>En jeu seulement : la page d'écoute suit le joueur qui la demande.");
            } else if (web == null || web.port() == 0) {
                dire(a, "<red>Le son n'est pas disponible (la page web n'a pas démarré).");
            } else if (!sonOuvert(p)) {
                dire(a, "<gray>Le son des télés n'est pas joignable d'ici pour l'instant. Réessaie dans une minute.");
            } else {
                // sur le PC du serveur : lien direct (moins de retard) ; ailleurs : lien du tunnel
                String pub = adressePublique();
                boolean ici = surCePc(p);
                String suite = "/ecoute#" + web.jetonEcoute(p.getName());
                String url = (ici || pub == null ? "http://localhost:" + web.port() : pub) + suite;
                dire(a, "<click:open_url:'" + url + "'><#E0B864><u>♪ Ouvrir ma page d'écoute</u></#E0B864></click> <gray>Garde-la ouverte : tu entends la télé dont tu es proche, plus fort en t'approchant.");
                if (ici && pub != null) {
                    dire(a, "<gray>Tu ne joues pas sur le PC du serveur ? <click:open_url:'" + pub + suite + "'><white><u>Prends ce lien-ci</u></white></click>.");
                }
            }
            return true;
        }
        if (!a.hasPermission("orbittele.admin")) {
            dire(a, "<gray>Assieds-toi à une console (clic droit) pour jouer. <white>/tele quitter</white> pour te lever. <white>/tele son</white> pour entendre une télé en direct.");
            return true;
        }
        Ecran e = args.length > 1 ? ecran(args[1]) : null;
        switch (sous) {
            case "poser" -> poser(a, args);
            case "retirer" -> {
                if (e == null) {
                    dire(a, "<red>Écran inconnu. <gray>/tele liste");
                    break;
                }
                if (e.session != null) {
                    e.session.arreter(true);
                }
                e.retirer(true);
                ecrans.remove(e.nom);
                enregistrer();
                dire(a, "Écran <white>" + e.nom + "</white> retiré, avec les blocs que j'avais posés.");
            }
            case "console" -> {
                String sens = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : "";
                if (e != null && (sens.equals("rapprocher") || sens.equals("reculer"))) {
                    // marche aussi depuis la console du serveur : on glisse la place le long de l'axe de l'écran
                    double blocs;
                    try {
                        blocs = args.length > 3 ? Double.parseDouble(args[3].replace(',', '.')) : 1;
                    } catch (NumberFormatException ex) {
                        blocs = 0;
                    }
                    if (e.console == null || blocs <= 0 || blocs > 32) {
                        dire(a, e.console == null ? "<gray>Cet écran n'a pas de console." : "<gray>Nombre de blocs entre 0,5 et 32.");
                        break;
                    }
                    double pas = sens.equals("rapprocher") ? -blocs : blocs;
                    double avant = Math.abs((e.console.getX() - (e.ox + 0.5)) * e.face.getModX() + (e.console.getZ() - (e.oz + 0.5)) * e.face.getModZ());
                    if (avant + pas < 1.5) {
                        dire(a, "<gray>Trop près : la console doit rester à 1,5 bloc de l'écran au moins (elle est à " + Math.round(avant * 10) / 10.0 + ").");
                        break;
                    }
                    if (e.session != null) {
                        e.session.arreter(true);
                    }
                    e.console.add(e.face.getModX() * pas, 0, e.face.getModZ() * pas);
                    enregistrer();
                    e.reposer();
                    dire(a, "Console " + (pas < 0 ? "rapprochée" : "reculée") + " de " + blocs + " bloc(s) : elle est à " + Math.round((avant + pas) * 10) / 10.0 + " bloc(s) de l'écran.");
                    break;
                }
                if (e == null || !(a instanceof Player p)) {
                    dire(a, "<gray>En jeu : <white>/tele console <écran></white> là où le joueur doit s'asseoir, <white>/tele console <écran> retirer</white>, "
                            + "ou <white>/tele console <écran> rapprocher|reculer <blocs></white>.");
                    break;
                }
                if (e.session != null) {
                    e.session.arreter(true);
                }
                boolean retirer = args.length > 2 && args[2].equalsIgnoreCase("retirer");
                e.console = retirer ? null : place(p);
                enregistrer();
                e.reposer();
                dire(a, retirer ? "Console retirée." : "Console posée ici : le joueur s'assoit à ta place, face à l'écran.");
            }
            case "lien" -> {
                if (web == null || web.port() == 0) {
                    dire(a, "<red>La page de diffusion n'a pas démarré (voir la console du serveur).");
                    break;
                }
                // sur le PC du serveur : toujours le lien direct. Ailleurs : par le tunnel, seulement si la config l'ouvre.
                String base = "http://localhost:" + web.port();
                if (a instanceof Player p && !surCePc(p)) {
                    String pub = adressePublique();
                    if (pub == null || !getConfig().getBoolean("web.diffusion-a-distance", false)) {
                        dire(a, "<gray>Le partage d'écran ne se lance que depuis le PC du serveur"
                                + (pub == null ? "." : " (pour l'ouvrir aux admins ailleurs : <white>web.diffusion-a-distance: true</white> dans la config)."));
                        break;
                    }
                    base = pub;
                }
                String url = base + "/#" + web.jeton(a.getName());
                if (a instanceof Player) {
                    dire(a, "<click:open_url:'" + url + "'><#E0B864><u>Ouvrir ma page de diffusion</u></#E0B864></click> <gray>(lien personnel, ne le donne à personne)");
                } else {
                    a.sendMessage("Page de diffusion : " + url);
                }
            }
            case "stop" -> {
                if (e == null) {
                    dire(a, "<red>Écran inconnu. <gray>/tele liste");
                    break;
                }
                if (e.session != null) {
                    e.session.arreter(true);
                }
                e.derniereImage = 0;
                e.mode = Ecran.Mode.VEILLE;
                e.son.couper();
                dire(a, "Écran <white>" + e.nom + "</white> remis en veille.");
            }
            case "apercu" -> {
                if (e == null) {
                    dire(a, "<red>Écran inconnu. <gray>/tele liste");
                    break;
                }
                File f = new File(getDataFolder(), "apercu-" + e.nom + ".png");
                try {
                    ImageIO.write(e.apercu(), "png", f);
                    dire(a, "Image écrite : <white>" + f.getPath());
                } catch (Exception ex) {
                    dire(a, "<red>Image non écrite : " + ex.getMessage());
                }
            }
            case "liste" -> {
                if (ecrans.isEmpty()) {
                    dire(a, "<gray>Aucun écran. <white>/tele poser <nom> [largeur] [hauteur]");
                }
                for (Ecran x : ecrans.values()) {
                    dire(a, "<white>" + x.nom + "</white> <gray>" + x.l + "×" + x.h + " blocs (" + x.W + "×" + x.H + " points) · " + x.mondeNom + " " + x.ox + " " + x.oy + " " + x.oz
                            + " · " + x.mode.name().toLowerCase(Locale.ROOT) + " · " + x.spectateurs() + " spectateur(s)" + (x.console == null ? " · sans console" : ""));
                }
                String pub = adressePublique();
                dire(a, "<gray>Son hors de ce PC : " + (pub != null ? "<white>" + pub + "</white> (page d'écoute, <white>/tele son</white>)"
                        : tunnel != null ? "tunnel en cours d'ouverture" : "fermé (<white>web.tunnel</white> dans la config)"));
            }
            default -> {
                dire(a, "<white>/tele poser <nom> [largeur] [hauteur]</white> <gray>télé seule, devant toi ou sur le mur que tu regardes");
                dire(a, "<white>/tele poser <nom> [largeur] [hauteur] console</white> <gray>télé + console de jeux à ta place");
                dire(a, "<white>/tele console <nom></white> <gray>ajoute ou déplace la console à ta place · <white>retirer</white> <gray>· <white>rapprocher|reculer <blocs>");
                dire(a, "<white>/tele retirer <nom></white> <gray>enlève la télé et sa console · <white>/tele liste");
                dire(a, "<white>/tele lien</white> <gray>ta page de partage d'écran · <white>/tele stop <nom></white> <gray>remet en veille");
                dire(a, "<white>/tele son</white> <gray>ta page d'écoute : le son du partage d'écran, dans ton navigateur (tous les joueurs)");
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender a, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("poser", "retirer", "console", "lien", "son", "stop", "liste", "apercu", "quitter").stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && !args[0].equalsIgnoreCase("poser")) {
            return ecrans.keySet().stream().filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        String dernier = args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length >= 3 && args.length <= 5 && args[0].equalsIgnoreCase("poser")) {
            return java.util.stream.Stream.of("console").filter(s -> s.startsWith(dernier)).toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("console")) {
            return java.util.stream.Stream.of("retirer", "rapprocher", "reculer").filter(s -> s.startsWith(dernier)).toList();
        }
        return List.of();
    }

    private static Location place(Player p) {
        Location l = p.getLocation();
        return new Location(l.getWorld(), l.getBlockX() + 0.5, l.getY(), l.getBlockZ() + 0.5);
    }

    /**
     * /tele poser nom [l] [h] [console] (en jeu) ou /tele poser nom l h monde x y z face (console du serveur, coordonnées du cadre bas-gauche).
     * La télé est posée seule ; le mot « console » à la fin pose aussi la console de jeux à la place du joueur.
     */
    private void poser(CommandSender a, String[] args) {
        boolean avecConsole = args.length > 2 && args[args.length - 1].equalsIgnoreCase("console");
        if (avecConsole) {
            args = java.util.Arrays.copyOf(args, args.length - 1);
        }
        if (args.length < 2 || !args[1].matches("[A-Za-z0-9_-]{1,16}")) {
            dire(a, "<gray>Donne un nom simple à l'écran : <white>/tele poser salon 5 3</white> (télé seule) ou <white>/tele poser salon 5 3 console</white> (télé + console de jeux).");
            return;
        }
        String nom = args[1].toLowerCase(Locale.ROOT);
        if (ecrans.containsKey(nom)) {
            dire(a, "<red>Un écran porte déjà ce nom. <gray>/tele retirer " + nom);
            return;
        }
        int l, h;
        try {
            l = args.length > 2 ? Integer.parseInt(args[2]) : getConfig().getInt("ecran.largeur", 5);
            h = args.length > 3 ? Integer.parseInt(args[3]) : getConfig().getInt("ecran.hauteur", 3);
        } catch (NumberFormatException ex) {
            dire(a, "<red>Largeur et hauteur en blocs, par exemple 5 3.");
            return;
        }
        if (l < 1 || l > 12 || h < 1 || h > 8) {
            dire(a, "<red>Taille entre 1×1 et 12×8 blocs.");
            return;
        }
        World w;
        int ox, oy, oz;
        BlockFace face;
        boolean pieds = false;
        Location console = null;
        if (args.length >= 9) {
            w = Bukkit.getWorld(args[4]);
            try {
                ox = Integer.parseInt(args[5]);
                oy = Integer.parseInt(args[6]);
                oz = Integer.parseInt(args[7]);
                face = BlockFace.valueOf(args[8].toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                dire(a, "<red>/tele poser <nom> <l> <h> <monde> <x> <y> <z> <NORTH|SOUTH|EAST|WEST>");
                return;
            }
            if (w == null || face.getModY() != 0 || Math.abs(face.getModX()) + Math.abs(face.getModZ()) != 1) {
                dire(a, "<red>Monde inconnu ou face qui n'est pas un des quatre côtés.");
                return;
            }
        } else if (a instanceof Player p) {
            w = p.getWorld();
            Block vise = p.getTargetBlockExact(10);
            BlockFace cote = vise == null ? null : p.getTargetBlockFace(10);
            Block milieu;
            if (cote != null && cote.getModY() == 0) {
                face = cote;
                milieu = vise.getRelative(cote);
                oy = milieu.getY() - (h - 1) / 2;
            } else {
                BlockFace vers = p.getFacing();
                face = vers.getOppositeFace();
                int d = getConfig().getInt("ecran.distance", 6);
                milieu = p.getLocation().getBlock().getRelative(vers, d);
                oy = p.getLocation().getBlockY() + 1;
                pieds = true;
            }
            ox = milieu.getX() - face.getModZ() * ((l - 1) / 2);
            oz = milieu.getZ() + face.getModX() * ((l - 1) / 2);
            console = avecConsole ? place(p) : null;
        } else {
            dire(a, "<red>Depuis la console : /tele poser <nom> <l> <h> <monde> <x> <y> <z> <face>");
            return;
        }
        Ecran e = new Ecran(this, nom, w.getName(), ox, oy, oz, face, l, h, 1_800_000 + prochain * 1000);
        for (int c = 0; c < l; c++) {
            for (int r = 0; r < h; r++) {
                Block b = w.getBlockAt(ox + e.rx() * c, oy + r, oz + e.rz() * c);
                if (!b.getType().isAir()) {
                    dire(a, "<red>Pas la place : il y a un bloc en " + b.getX() + " " + b.getY() + " " + b.getZ() + ". <gray>Recule, ou choisis une taille plus petite.");
                    return;
                }
            }
        }
        for (int c = 0; c < l; c++) {
            for (int r = 0; r < h; r++) {
                poserBloc(e, w.getBlockAt(ox + e.rx() * c - face.getModX(), oy + r, oz + e.rz() * c - face.getModZ()), Material.BLACK_CONCRETE);
            }
            if (pieds && (l < 4 ? c == l / 2 : c == 1 || c == l - 2)) {
                for (int y = oy - 1; y >= oy - 3; y--) {
                    Block b = w.getBlockAt(ox + e.rx() * c - face.getModX(), y, oz + e.rz() * c - face.getModZ());
                    if (!b.getType().isAir()) {
                        break;
                    }
                    poserBloc(e, b, Material.POLISHED_BLACKSTONE_WALL);
                }
            }
        }
        e.console = console;
        prochain++;
        ecrans.put(nom, e);
        enregistrer();
        e.installer();
        dire(a, "Écran <white>" + nom + "</white> posé : " + l + "×" + h + " blocs, " + e.W + "×" + e.H + " points."
                + (console != null ? " La console de jeux est à ta place." : " Télé seule, sans console."));
        dire(a, "<gray>Partage d'écran : <white>/tele lien</white> · " + (console != null ? "déplacer la console : <white>/tele console " + nom + "</white> · l'enlever : <white>/tele console " + nom + " retirer"
                : "ajouter une console de jeux : place-toi où l'on doit s'asseoir puis <white>/tele console " + nom));
    }

    private void poserBloc(Ecran e, Block b, Material m) {
        if (b.getType().isAir()) {
            b.setType(m, false);
            e.blocs.add(new int[]{b.getX(), b.getY(), b.getZ()});
        }
    }

    // ------------------------------------------------------------------ événements

    private Ecran de(Entity ent) {
        String nom = ent.getPersistentDataContainer().get(cle, PersistentDataType.STRING);
        return nom == null ? null : ecrans.get(nom);
    }

    @EventHandler
    public void clic(PlayerInteractEntityEvent ev) {
        Ecran e = de(ev.getRightClicked());
        if (e == null) {
            return;
        }
        ev.setCancelled(true);
        Player p = ev.getPlayer();
        if (!(ev.getRightClicked() instanceof Interaction) || e.console == null || sessions.containsKey(p.getUniqueId())) {
            return;
        }
        Session en = e.session;
        if (e.mode == Ecran.Mode.DIRECT) {
            dire(p, "<gray>L'écran est en direct pour l'instant. La console revient dès que la diffusion s'arrête.");
        } else if (en != null && en.complet()) {
            dire(p, "<gray>" + en.noms() + " sont déjà à la console. Regarde leur partie en attendant ton tour.");
        } else if (en != null && !en.arretee()) {
            en.rejoindre(p);
        } else if (palette == null) {
            dire(p, "<gray>La console démarre encore, réessaie dans un instant.");
        } else {
            new Session(this, e).demarrer(p);
        }
    }

    @EventHandler
    public void touches(PlayerInputEvent ev) {
        Session s = sessions.get(ev.getPlayer().getUniqueId());
        Manette m = s == null ? null : s.manette(ev.getPlayer().getUniqueId());
        if (m == null) {
            return;
        }
        Input in = ev.getInput();
        m.recevoir(Manette.HAUT, in.isForward());
        m.recevoir(Manette.BAS, in.isBackward());
        m.recevoir(Manette.GAUCHE, in.isLeft());
        m.recevoir(Manette.DROITE, in.isRight());
        m.recevoir(Manette.A, in.isJump());
        m.recevoir(Manette.B, in.isSneak() || in.isSprint());
    }

    @EventHandler
    public void descendre(EntityDismountEvent ev) {
        Session s = sessions.get(ev.getEntity().getUniqueId());
        if (s != null && s.retient(ev.getEntity().getUniqueId())) {
            ev.setCancelled(true);
        }
    }

    @EventHandler
    public void toucheF(PlayerSwapHandItemsEvent ev) {
        Session s = sessions.get(ev.getPlayer().getUniqueId());
        if (s != null) {
            ev.setCancelled(true);
            s.retour(ev.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void depart(PlayerQuitEvent ev) {
        Session s = sessions.get(ev.getPlayer().getUniqueId());
        if (s != null) {
            s.partir(ev.getPlayer(), true);
        }
    }

    @EventHandler
    public void monde(PlayerChangedWorldEvent ev) {
        Session s = sessions.get(ev.getPlayer().getUniqueId());
        if (s != null) {
            s.partir(ev.getPlayer(), false);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void teleporte(PlayerTeleportEvent ev) {
        Session s = sessions.get(ev.getPlayer().getUniqueId());
        if (s != null && s.retient(ev.getPlayer().getUniqueId())
                && (!ev.getTo().getWorld().equals(s.ecran.monde()) || ev.getTo().distanceSquared(s.ecran.console) > 9)) {
            s.partir(ev.getPlayer(), false);
        }
    }

    @EventHandler
    public void casse(HangingBreakEvent ev) {
        if (de(ev.getEntity()) != null) {
            ev.setCancelled(true);
        }
    }
}
