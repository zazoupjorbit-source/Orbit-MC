package fr.orbit.tele;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.GlowItemFrame;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Un écran : un mur de cartes (128 × 128 points par bloc) dans des cadres lumineux invisibles. Les cartes n'existent
 * pas côté serveur (numéros réservés très hauts) : leur contenu est envoyé directement aux joueurs proches, et
 * seulement la zone qui a changé depuis l'image précédente.
 */
public final class Ecran {

    public enum Mode { VEILLE, DIRECT, CONSOLE }

    final OrbitTele plugin;
    final String nom, mondeNom;
    /** Bloc du cadre en bas à gauche (vu par le spectateur). */
    final int ox, oy, oz;
    final BlockFace face;
    final int l, h, baseId;
    /** Taille de l'image en points. */
    public final int W, H;
    /** Blocs posés par le plugin (retirés avec l'écran). */
    final List<int[]> blocs = new ArrayList<>();
    /** Place assise de la console (null = pas de console). */
    Location console;

    private final byte[][] tuiles;
    private volatile Player[] spectateurs = new Player[0];
    private Set<UUID> connus = new HashSet<>();
    private final List<Entity> entites = new ArrayList<>();
    private TextDisplay etiquette, etiquetteConsole;
    private String etiquetteTexte = "", consoleTexte = "";
    private boolean installe;

    volatile Mode mode = Mode.VEILLE;
    volatile long derniereImage;
    volatile String diffuseur = "";
    volatile Session session;
    volatile long octets;
    /** Son du partage d'écran, relayé aux pages d'écoute des joueurs. */
    final Son son = new Son();
    /** Joueurs déjà prévenus que cette diffusion a du son (remis à zéro à chaque nouvelle diffusion). */
    private final Set<UUID> prevenus = new HashSet<>();

    Ecran(OrbitTele plugin, String nom, String monde, int ox, int oy, int oz, BlockFace face, int l, int h, int baseId) {
        this.plugin = plugin;
        this.nom = nom;
        this.mondeNom = monde;
        this.ox = ox;
        this.oy = oy;
        this.oz = oz;
        this.face = face;
        this.l = l;
        this.h = h;
        this.baseId = baseId;
        this.W = l * 128;
        this.H = h * 128;
        this.tuiles = new byte[l * h][128 * 128];
    }

    World monde() {
        return Bukkit.getWorld(mondeNom);
    }

    /** Vers la droite du spectateur. */
    int rx() {
        return face.getModZ();
    }

    int rz() {
        return -face.getModX();
    }

    Location centre() {
        return new Location(monde(), ox + rx() * (l - 1) / 2.0 + 0.5, oy + h / 2.0, oz + rz() * (l - 1) / 2.0 + 0.5);
    }

    /** Distance du centre de l'écran à un point du même monde (calcul seul, utilisable hors du fil du serveur). */
    double distance(double x, double y, double z) {
        double dx = x - (ox + rx() * (l - 1) / 2.0 + 0.5), dy = y - (oy + h / 2.0), dz = z - (oz + rz() * (l - 1) / 2.0 + 0.5);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    int spectateurs() {
        return spectateurs.length;
    }

    boolean installe() {
        return installe;
    }

    static float lacet(BlockFace vers) {
        return switch (vers) {
            case NORTH -> 180f;
            case WEST -> 90f;
            case EAST -> -90f;
            default -> 0f;
        };
    }

    // ------------------------------------------------------------------ image

    /** Affiche une image (W × H points, RVB) : ne part vers les joueurs que ce qui a changé. */
    public synchronized void pousser(int[] rvb, boolean trame) {
        Palette pal = plugin.palette();
        if (pal == null || rvb.length < W * H) {
            return;
        }
        byte[] table = pal.table, ecart = pal.ecart;
        int[] motif = pal.motif, ampli = pal.ampli;
        Player[] vers = spectateurs;
        for (int ty = 0; ty < h; ty++) {
            for (int tx = 0; tx < l; tx++) {
                byte[] t = tuiles[ty * l + tx];
                int x0 = 128, y0 = 128, x1 = -1, y1 = -1;
                for (int y = 0; y < 128; y++) {
                    int src = (ty * 128 + y) * W + tx * 128, dst = y * 128, ligne = (y & 7) << 3;
                    for (int x = 0; x < 128; x++) {
                        int c = rvb[src + x];
                        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
                        int k = ((r >> 2) << 12) | ((g >> 2) << 6) | (b >> 2);
                        int a = trame ? ampli[ecart[k] & 255] : 0;
                        if (a != 0) {
                            int d = (motif[ligne | (x & 7)] * a) >> 6;
                            r += d;
                            g += d;
                            b += d;
                            r = r < 0 ? 0 : (r > 255 ? 255 : r);
                            g = g < 0 ? 0 : (g > 255 ? 255 : g);
                            b = b < 0 ? 0 : (b > 255 ? 255 : b);
                            k = ((r >> 2) << 12) | ((g >> 2) << 6) | (b >> 2);
                        }
                        byte v = table[k];
                        if (t[dst + x] != v) {
                            t[dst + x] = v;
                            if (x < x0) x0 = x;
                            if (x > x1) x1 = x;
                            if (y < y0) y0 = y;
                            if (y > y1) y1 = y;
                        }
                    }
                }
                if (x1 < 0 || vers.length == 0) {
                    continue;
                }
                int w = x1 - x0 + 1, hh = y1 - y0 + 1;
                byte[] zone = new byte[w * hh];
                for (int y = 0; y < hh; y++) {
                    System.arraycopy(t, (y0 + y) * 128 + x0, zone, y * w, w);
                }
                envoyer(vers, baseId + ty * l + tx, x0, y0, w, hh, zone);
            }
        }
    }

    /** Toute l'image à un joueur (il arrive, ou image de contrôle). */
    synchronized void envoyerTout(Player p) {
        Player[] un = {p};
        for (int i = 0; i < tuiles.length; i++) {
            envoyer(un, baseId + i, 0, 0, 128, 128, tuiles[i].clone());
        }
    }

    private void envoyer(Player[] vers, int id, int x, int y, int w, int hh, byte[] zone) {
        ClientboundMapItemDataPacket paquet = new ClientboundMapItemDataPacket(new MapId(id), (byte) 0, true,
                Optional.empty(), Optional.of(new MapItemSavedData.MapPatch(x, y, w, hh, zone)));
        for (Player p : vers) {
            try {
                ((CraftPlayer) p).getHandle().connection.send(paquet);
                octets += zone.length;
            } catch (RuntimeException ignored) {
                // joueur en train de partir
            }
        }
    }

    /** Ce que les joueurs voient vraiment (couleurs des cartes). */
    public synchronized BufferedImage apercu() {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Palette pal = plugin.palette();
        if (pal == null) {
            return img;
        }
        for (int ty = 0; ty < h; ty++) {
            for (int tx = 0; tx < l; tx++) {
                byte[] t = tuiles[ty * l + tx];
                for (int y = 0; y < 128; y++) {
                    for (int x = 0; x < 128; x++) {
                        img.setRGB(tx * 128 + x, ty * 128 + y, pal.rvb[t[y * 128 + x] & 255]);
                    }
                }
            }
        }
        return img;
    }

    // ------------------------------------------------------------------ dans le monde

    /** Pose cadres, étiquette et console. À appeler sur le fil du serveur, monde chargé. */
    void installer() {
        World w = monde();
        if (w == null || installe) {
            return;
        }
        installe = true;
        for (int c = -1; c <= l; c++) {
            w.addPluginChunkTicket((ox + rx() * c) >> 4, (oz + rz() * c) >> 4, plugin);
        }
        Location centre = centre();
        for (Entity e : w.getNearbyEntities(centre, l + 12, h + 12, l + 12)) {
            if (nom.equals(e.getPersistentDataContainer().get(plugin.cle(), PersistentDataType.STRING))) {
                e.remove();
            }
        }
        for (int r = 0; r < h; r++) {
            for (int c = 0; c < l; c++) {
                int id = baseId + r * l + c;
                Location at = new Location(w, ox + rx() * c, oy + (h - 1 - r), oz + rz() * c);
                try {
                    garder(w.spawn(at, GlowItemFrame.class, false, f -> {
                        f.setFixed(true);
                        f.setVisible(false);
                        f.setInvulnerable(true);
                        f.setSilent(true);
                        f.setFacingDirection(face, true);
                        ItemStack carte = new ItemStack(Material.FILLED_MAP);
                        carte.setData(DataComponentTypes.MAP_ID, io.papermc.paper.datacomponent.item.MapId.mapId(id));
                        f.setItem(carte, false);
                    }));
                } catch (RuntimeException ex) {
                    plugin.getLogger().warning("Cadre impossible à poser en " + at.getBlockX() + " " + at.getBlockY() + " " + at.getBlockZ() + " : " + ex.getMessage());
                }
            }
        }
        float lacet = lacet(face);
        Location haut = centre.clone().add(-face.getModX() * 0.42, h / 2.0 + 0.18, -face.getModZ() * 0.42);
        etiquette = texte(haut, lacet, 1.0f);
        etiquetteTexte = "";
        if (console != null) {
            console.setWorld(w);
            poserConsole(w);
        }
        w.getPlayers().forEach(p -> connus.remove(p.getUniqueId()));
    }

    private void poserConsole(World w) {
        BlockFace vers = face.getOppositeFace();
        boolean axeZ = face.getModZ() != 0;
        Location meuble = console.clone().add(vers.getModX() * 1.3, 0, vers.getModZ() * 1.3);
        bloc(meuble.clone().add(0, 0.25, 0), Material.POLISHED_BLACKSTONE, axeZ ? 1.0f : 0.6f, 0.5f, axeZ ? 0.6f : 1.0f);
        bloc(meuble.clone().add(0, 0.57, 0), Material.BLACK_CONCRETE, axeZ ? 0.62f : 0.38f, 0.14f, axeZ ? 0.38f : 0.62f);
        bloc(meuble.clone().add(-vers.getModX() * 0.2, 0.585, -vers.getModZ() * 0.2), Material.SEA_LANTERN,
                axeZ ? 0.5f : 0.03f, 0.03f, axeZ ? 0.03f : 0.5f);
        bloc(place(0).add(0, 0.2, 0), Material.POLISHED_BLACKSTONE, 0.5f, 0.4f, 0.5f);
        bloc(place(1).add(0, 0.2, 0), Material.POLISHED_BLACKSTONE, 0.5f, 0.4f, 0.5f);
        garder(w.spawn(meuble, Interaction.class, i -> {
            i.setInteractionWidth(1.2f);
            i.setInteractionHeight(1.0f);
            i.setResponsive(true);
        }));
        etiquetteConsole = texte(meuble.clone().add(0, 1.3, 0), lacet(face), 0.55f);
        consoleTexte = "";
    }

    /** Place assise du joueur 1 (à gauche) ou 2 (à droite), de part et d'autre du centre de la console. */
    Location place(int joueur) {
        double d = joueur == 0 ? -0.65 : 0.65;
        return console.clone().add(rx() * d, 0, rz() * d);
    }

    private void bloc(Location centre, Material m, float sx, float sy, float sz) {
        garder(centre.getWorld().spawn(centre, BlockDisplay.class, e -> {
            e.setBlock(m.createBlockData());
            e.setBrightness(new Display.Brightness(15, 15));
            e.setTransformation(new Transformation(new Vector3f(-sx / 2, -sy / 2, -sz / 2), new AxisAngle4f(),
                    new Vector3f(sx, sy, sz), new AxisAngle4f()));
        }));
    }

    private TextDisplay texte(Location at, float lacet, float echelle) {
        return garder(at.getWorld().spawn(at, TextDisplay.class, e -> {
            e.setBillboard(Display.Billboard.FIXED);
            e.setRotation(lacet, 0f);
            e.setAlignment(TextDisplay.TextAlignment.CENTER);
            e.setShadowed(true);
            e.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            e.setBrightness(new Display.Brightness(15, 15));
            e.setLineWidth(400);
            e.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(echelle, echelle, echelle), new AxisAngle4f()));
        }));
    }

    private <T extends Entity> T garder(T e) {
        e.setPersistent(false);
        e.getPersistentDataContainer().set(plugin.cle(), PersistentDataType.STRING, nom);
        entites.add(e);
        return e;
    }

    boolean aPourEntite(Entity e) {
        return nom.equals(e.getPersistentDataContainer().get(plugin.cle(), PersistentDataType.STRING));
    }

    /** Retire les entités ; avec {@code tout}, retire aussi les blocs posés par le plugin. */
    void retirer(boolean tout) {
        entites.forEach(Entity::remove);
        entites.clear();
        etiquette = null;
        etiquetteConsole = null;
        installe = false;
        World w = monde();
        if (w == null) {
            return;
        }
        for (int c = -1; c <= l; c++) {
            w.removePluginChunkTicket((ox + rx() * c) >> 4, (oz + rz() * c) >> 4, plugin);
        }
        if (tout) {
            for (int[] b : blocs) {
                w.getBlockAt(b[0], b[1], b[2]).setType(Material.AIR, false);
            }
            blocs.clear();
        }
    }

    /** Remet la console (après un déplacement). */
    void reposer() {
        retirer(false);
        installer();
    }

    /** Toutes les demi-secondes, sur le fil du serveur : qui regarde, étiquettes, image de contrôle. */
    void tic(int compteur) {
        World w = monde();
        if (w == null) {
            return;
        }
        if (!installe) {
            installer();
        }
        Location c = centre();
        double r2 = plugin.rayon() * plugin.rayon();
        List<Player> vus = new ArrayList<>();
        Set<UUID> ids = new HashSet<>();
        boolean controle = plugin.imageDeControle() > 0 && compteur % plugin.imageDeControle() == 0;
        for (Player p : w.getPlayers()) {
            if (p.getLocation().distanceSquared(c) > r2) {
                continue;
            }
            vus.add(p);
            ids.add(p.getUniqueId());
            if (!connus.contains(p.getUniqueId())) {
                envoyerTout(p);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (p.isOnline() && p.getWorld().equals(w)) {
                        envoyerTout(p);
                    }
                }, 20L);
            } else if (controle) {
                envoyerTout(p);
            }
        }
        connus = ids;
        spectateurs = vus.toArray(new Player[0]);

        long maintenant = System.currentTimeMillis();
        boolean avecSon = mode == Mode.DIRECT && son.actif(maintenant);
        if (mode != Mode.DIRECT) {
            // une annonce par joueur et par diffusion, même si le son s'interrompt puis revient
            prevenus.clear();
        } else if (avecSon) {
            for (Player p : vus) {
                if (prevenus.add(p.getUniqueId()) && !son.ecoute(p.getName(), maintenant) && plugin.sonOuvert(p)) {
                    p.sendMessage(plugin.mm("<#62CFF4><b>ORBIT TV</b></#62CFF4> <dark_gray>»</dark_gray> <white>" + diffuseur + "</white> diffuse avec le son. "
                            + "<click:run_command:'/tele son'><#E0B864><u>♪ Écouter</u></#E0B864></click> <gray>(ouvre une page dans ton navigateur)"));
                }
            }
        }

        Session s = session;
        String t = switch (mode) {
            case DIRECT -> "<red>●</red> <white>En direct</white> <dark_gray>·</dark_gray> <#E0B864>" + diffuseur
                    + (avecSon ? "</#E0B864> <dark_gray>·</dark_gray> <#62CFF4>♪ /tele son" : "");
            case CONSOLE -> s == null ? "" : "<#3A7FFF>▶</#3A7FFF> <white>" + s.noms() + "</white> <dark_gray>·</dark_gray> <#E0B864>" + s.titre();
            default -> "<#62CFF4><b>ORBIT TV</b>";
        };
        if (etiquette != null && !t.equals(etiquetteTexte)) {
            etiquetteTexte = t;
            etiquette.text(plugin.mm(t));
        }
        String tc = mode == Mode.CONSOLE ? (s != null && !s.complet() ? "<#E0B864><b>PLACE DE J2 LIBRE</b>\n<white>Clic droit pour rejoindre" : "<gray>Console complète")
                : mode == Mode.DIRECT ? "<gray>Diffusion en cours"
                : "<#E0B864><b>CONSOLE ORBIT</b>\n<white>Clic droit pour jouer";
        if (etiquetteConsole != null && !tc.equals(consoleTexte)) {
            consoleTexte = tc;
            etiquetteConsole.text(plugin.mm(tc));
        }
        masquerEtiquette();
    }

    /** L'étiquette de la console est pile entre le siège et l'écran : ceux qui sont assis ne la voient pas, les autres si. */
    void masquerEtiquette() {
        TextDisplay e = etiquetteConsole;
        if (e == null || !e.isValid()) {
            return;
        }
        Session s = session;
        for (Player p : e.getWorld().getPlayers()) {
            if (s != null && s.retient(p.getUniqueId())) {
                p.hideEntity(plugin, e);
            } else {
                p.showEntity(plugin, e);
            }
        }
    }
}
