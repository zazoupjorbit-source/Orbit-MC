package fr.orbit.tycoon;

import com.destroystokyo.paper.profile.PlayerProfile;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Classement prestige (lié à l'Outil orbital) au spawn :
 * un panneau « Top 10 » à côté de chaque anneau-portail et un podium avec le skin des 3 meilleurs joueurs.
 * Les blocs (socles, podium) sont posés une fois par « /tycoon classement poser » ;
 * les textes et mannequins ne sont jamais sauvegardés : ils sont recréés au chargement de leur chunk.
 */
final class Classement implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int Y = Zone.Y;
    private static final String[] COULEURS = {"#E0B864", "#CFE8F5", "#D08A57"};
    private static final Material[] DESSUS = {Material.GOLD_BLOCK, Material.QUARTZ_BLOCK, Material.WAXED_COPPER_BLOCK};
    private static final int[] HAUTEURS = {3, 2, 1};

    record Ligne(UUID id, String nom, int prestige) { }

    /** Un texte ou un mannequin placé à un endroit fixe ; l'entité est recréée si son chunk est rechargé. */
    private static final class Place {
        final Location loc;
        Entity entite;
        Place(Location loc) { this.loc = loc; }
        boolean vivante() { return entite != null && entite.isValid(); }
    }

    private final OrbitTycoon plugin;
    private final List<Place> panneaux = new ArrayList<>();
    private final Place[] mannequins = new Place[3];
    private final Place[] etiquettes = new Place[3];
    private Place titre;
    private volatile List<Ligne> top = List.of();
    /** Profils complets (avec skin) déjà récupérés. */
    private final Map<UUID, PlayerProfile> profils = new ConcurrentHashMap<>();
    private BukkitTask tache;

    Classement(OrbitTycoon plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ positions

    /** Panneau à droite de chaque anneau (vu depuis le centre), à 12 blocs du centre de l'anneau. */
    private static double[] posPanneau(Zone z) {
        double[] p = z.portailBase();
        double tx = -p[4], tz = p[3];
        return new double[]{p[0] + tx * 12, p[2] + tz * 12};
    }

    /** Angle (degrés) du podium : entre deux avenues, du côté du point d'arrivée. */
    private double anglePodium() { return plugin.getConfig().getDouble("classement.podium.angle", 108); }

    /** 97 = centre du pavillon « Classement » (petit dôme de l'anneau extérieur, voir Plans.base). */
    private double rayonPodium() { return plugin.getConfig().getDouble("classement.podium.rayon", 97); }

    /** Ancienne place du podium (au milieu du spawn), effacée par « /tycoon classement poser ». */
    private static final double ANCIEN_RAYON = 36;

    /** Centre (x, z) de la marche n (0 = 1re place au milieu, 1 = 2e, 2 = 3e). */
    private double[] posMarche(int n) { return posMarche(n, rayonPodium()); }

    private double[] posMarche(int n, double r) {
        double a = Math.toRadians(anglePodium());
        double cx = Math.cos(a) * r, cz = Math.sin(a) * r, tx = -Math.sin(a), tz = Math.cos(a);
        double decal = n == 0 ? 0 : n == 1 ? -3.5 : 3.5;
        return new double[]{Math.floor(cx + tx * decal) + 0.5, Math.floor(cz + tz * decal) + 0.5};
    }

    /** Point de TP (/classement) : devant l'entrée du pavillon, tourné vers le podium. */
    Location pointTp() {
        double a = Math.toRadians(anglePodium()), r = rayonPodium() - 15;
        return new Location(plugin.monde(), Math.floor(Math.cos(a) * r) + 0.5, Y + 1, Math.floor(Math.sin(a) * r) + 0.5,
                Zone.yaw(Math.cos(a), Math.sin(a)), 0f);
    }

    /** Titre du pavillon, juste devant son entrée. */
    private Location posTitre() {
        double a = Math.toRadians(anglePodium()), r = rayonPodium() - 11.5;
        return new Location(plugin.monde(), Math.cos(a) * r + 0.5, Y + 6.2, Math.sin(a) * r + 0.5);
    }

    /** Yaw pour regarder le centre du spawn depuis (x, z). */
    private static float versCentre(double x, double z) {
        return (float) Math.toDegrees(Math.atan2(x, -z));
    }

    // ------------------------------------------------------------------ cycle de vie

    void demarrer() {
        arreter();
        World w = plugin.monde();
        panneaux.clear();
        for (Zone z : Zone.farms()) {
            double[] p = posPanneau(z);
            panneaux.add(new Place(new Location(w, Math.floor(p[0]) + 0.5, Y + 2.2, Math.floor(p[1]) + 0.5)));
        }
        for (int i = 0; i < 3; i++) {
            double[] m = posMarche(i);
            float yaw = versCentre(m[0], m[1]);
            mannequins[i] = new Place(new Location(w, m[0], Y + 1 + HAUTEURS[i], m[1], yaw, 0));
            etiquettes[i] = new Place(new Location(w, m[0], Y + 1 + HAUTEURS[i] + 3.2, m[1]));
        }
        titre = new Place(posTitre());
        // Nettoie les restes d'un ancien démarrage (entités non sauvegardées, mais au cas où).
        for (Entity e : w.getEntities()) if (e.getScoreboardTags().contains("orbit_classement")) e.remove();
        long periode = Math.max(20, plugin.getConfig().getLong("classement.periode-secondes", 60) * 20);
        tache = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::calculer, 20L, periode);
    }

    void arreter() {
        if (tache != null) tache.cancel();
        tache = null;
        for (Place p : panneaux) if (p.vivante()) p.entite.remove();
        for (Place[] t : new Place[][]{mannequins, etiquettes})
            for (Place p : t) if (p != null && p.vivante()) p.entite.remove();
        if (titre != null && titre.vivante()) titre.entite.remove();
    }

    /** Hors thread principal : lit les prestiges, trie, récupère les skins du top 3, puis met à jour au spawn. */
    private void calculer() {
        Map<UUID, Integer> tous = plugin.monnaies().tousLesPrestiges();
        List<Ligne> liste = new ArrayList<>();
        tous.forEach((id, pr) -> {
            String nom = Bukkit.getOfflinePlayer(id).getName();
            if (nom != null) liste.add(new Ligne(id, nom, pr));
        });
        liste.sort(Comparator.comparingInt(Ligne::prestige).reversed().thenComparing(Ligne::nom, String.CASE_INSENSITIVE_ORDER));
        List<Ligne> nouveau = List.copyOf(liste.subList(0, Math.min(10, liste.size())));
        for (int i = 0; i < Math.min(3, nouveau.size()); i++) {
            Ligne l = nouveau.get(i);
            if (profils.containsKey(l.id())) continue;
            try {
                PlayerProfile pp = Bukkit.createProfile(l.id(), l.nom());
                if (pp.complete(true)) profils.put(l.id(), pp);
            } catch (Exception e) {
                plugin.getLogger().fine("Skin introuvable pour " + l.nom() + " : " + e.getMessage());
            }
        }
        top = nouveau;
        Bukkit.getScheduler().runTask(plugin, this::rafraichir);
    }

    void forcer() { Bukkit.getScheduler().runTaskAsynchronously(plugin, this::calculer); }

    // ------------------------------------------------------------------ affichage

    private Component textePanneau() {
        StringBuilder sb = new StringBuilder("<gradient:#62CFF4:#E0B864><bold>✪ CLASSEMENT PRESTIGE</bold></gradient>\n<gray>Outil orbital\n");
        List<Ligne> t = top;
        for (int i = 0; i < 10; i++) {
            String c = i < 3 ? COULEURS[i] : "#8FA3B5";
            sb.append("\n<").append(c).append("><bold>").append(i + 1).append(".</bold></").append(c).append("> ");
            if (i < t.size()) sb.append("<white>").append(MM.escapeTags(t.get(i).nom()))
                    .append(" <dark_gray>— <#E0B864>✪ ").append(t.get(i).prestige());
            else sb.append("<dark_gray>—");
        }
        return MM.deserialize(sb.toString());
    }

    private void rafraichir() {
        Component texte = textePanneau();
        for (Place p : panneaux) {
            if (!p.loc.isChunkLoaded()) continue;
            if (!p.vivante()) p.entite = texteFixe(p.loc, texte, (float) plugin.getConfig().getDouble("classement.panneau-echelle", 1.8));
            else ((TextDisplay) p.entite).text(texte);
        }
        for (int i = 0; i < 3; i++) majPodium(i);
        if (titre != null && titre.loc.isChunkLoaded() && !titre.vivante())
            titre.entite = texteFixe(titre.loc, MM.deserialize("<gradient:#62CFF4:#E0B864><bold>✪ CLASSEMENT PRESTIGE</bold></gradient>\n"
                    + "<gray>Les 3 meilleurs de la station"), 1.6f);
    }

    private void majPodium(int i) {
        Place m = mannequins[i], e = etiquettes[i];
        if (m == null || !m.loc.isChunkLoaded()) return;
        List<Ligne> t = top;
        Ligne l = i < t.size() ? t.get(i) : null;
        if (!m.vivante()) m.entite = m.loc.getWorld().spawn(m.loc, Mannequin.class, mq -> {
            mq.setPersistent(false);
            mq.setImmovable(true);
            mq.setInvulnerable(true);
            mq.setAI(false);
            mq.setGravity(false);
            mq.setSilent(true);
            mq.setCustomNameVisible(true);
            mq.addScoreboardTag("orbit_classement");
        });
        Mannequin mq = (Mannequin) m.entite;
        mq.setRotation(m.loc.getYaw(), 0);
        String c = COULEURS[i];
        if (l == null) {
            mq.setProfile(Mannequin.defaultProfile());
            mq.customName(MM.deserialize("<" + c + "><bold>#" + (i + 1) + "</bold> <gray>Place libre"));
            mq.setDescription(MM.deserialize("<dark_gray>Monte ton prestige !"));
        } else {
            PlayerProfile pp = profils.get(l.id());
            Player en = Bukkit.getPlayer(l.id());
            if (en != null) pp = en.getPlayerProfile();
            mq.setProfile(pp != null ? ResolvableProfile.resolvableProfile(pp)
                    : ResolvableProfile.resolvableProfile().uuid(l.id()).name(l.nom()).build());
            mq.customName(MM.deserialize("<" + c + "><bold>#" + (i + 1) + "</bold> <white>" + MM.escapeTags(l.nom())));
            mq.setDescription(MM.deserialize("<#E0B864>✪ " + l.prestige() + " <gray>prestige"));
        }
        if (e != null && !e.vivante())
            e.entite = texteFixe(e.loc, MM.deserialize("<" + c + "><bold>" + (i == 0 ? "1er" : (i + 1) + "e") + "</bold>"), 2.0f);
    }

    private TextDisplay texteFixe(Location loc, Component texte, float echelle) {
        return loc.getWorld().spawn(loc, TextDisplay.class, d -> {
            d.text(texte);
            d.setPersistent(false);
            d.setBillboard(Display.Billboard.VERTICAL);
            d.setShadowed(true);
            d.setViewRange(2f);
            d.setLineWidth(260);
            d.setBackgroundColor(Color.fromARGB(150, 8, 16, 32));
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                    new Vector3f(echelle, echelle, echelle), new AxisAngle4f()));
            d.addScoreboardTag("orbit_classement");
        });
    }

    @EventHandler
    public void chunk(ChunkLoadEvent e) {
        if (!e.getWorld().equals(plugin.monde())) return;
        int cx = e.getChunk().getX(), cz = e.getChunk().getZ();
        boolean concerne = false;
        for (Place p : panneaux) if (p.loc.getBlockX() >> 4 == cx && p.loc.getBlockZ() >> 4 == cz) concerne = true;
        for (Place p : mannequins) if (p != null && p.loc.getBlockX() >> 4 == cx && p.loc.getBlockZ() >> 4 == cz) concerne = true;
        if (titre != null && titre.loc.getBlockX() >> 4 == cx && titre.loc.getBlockZ() >> 4 == cz) concerne = true;
        if (concerne) Bukkit.getScheduler().runTask(plugin, this::rafraichir);
    }

    @EventHandler
    public void degats(EntityDamageEvent e) {
        if (e.getEntity().getScoreboardTags().contains("orbit_classement")) e.setCancelled(true);
    }

    @EventHandler
    public void clic(PlayerInteractEntityEvent e) {
        if (e.getRightClicked().getScoreboardTags().contains("orbit_classement")) e.setCancelled(true);
    }

    @EventHandler
    public void clicAt(PlayerInteractAtEntityEvent e) {
        if (e.getRightClicked().getScoreboardTags().contains("orbit_classement")) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ blocs (une seule fois)

    /** Pose les socles des panneaux et le podium. Ne touche qu'aux blocs d'air au-dessus du sol. */
    int poser() {
        World w = plugin.monde();
        int poses = 0;
        // Panneaux : deux piliers bleu et or, une lanterne au sommet, une dalle au sol.
        for (Zone z : Zone.farms()) {
            double[] p = posPanneau(z), base = z.portailBase();
            double tx = -base[4], tz = base[3];
            int x0 = (int) Math.floor(p[0]), z0 = (int) Math.floor(p[1]);
            for (int s = -1; s <= 1; s += 2) {
                int px = (int) Math.floor(p[0] + tx * 3.5 * s), pz = (int) Math.floor(p[1] + tz * 3.5 * s);
                for (int y = Y + 1; y <= Y + 6; y++)
                    poses += air(w, px, y, pz, y % 2 == 0 ? Material.GOLD_BLOCK : Material.LIGHT_BLUE_CONCRETE);
                poses += air(w, px, Y + 7, pz, Material.SEA_LANTERN);
                poses += air(w, px, Y + 8, pz, Material.END_ROD);
            }
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
                poses += sol(w, x0 + dx, z0 + dz, dx == 0 && dz == 0 ? Material.SEA_LANTERN : Material.POLISHED_DEEPSLATE);
        }
        // Ancien podium (au milieu du spawn) : on retire ses blocs (le dallage dessous n'avait pas été touché).
        if (Math.abs(rayonPodium() - ANCIEN_RAYON) > 5)
            for (int i = 0; i < 3; i++) {
                double[] m = posMarche(i, ANCIEN_RAYON);
                int mx = (int) Math.floor(m[0]), mz = (int) Math.floor(m[1]);
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                    for (int h = 1; h <= 3; h++) {
                        Block b = w.getBlockAt(mx + dx, Y + h, mz + dz);
                        if (MARCHES.contains(b.getType())) { b.setType(Material.AIR, false); poses++; }
                    }
                }
            }
        // Pavillon : on retire le pilier lumineux du centre et l'ancien texte « Classements / Bientôt ».
        double a = Math.toRadians(anglePodium());
        int pcx = (int) Math.round(Math.cos(a) * rayonPodium()), pcz = (int) Math.round(Math.sin(a) * rayonPodium());
        for (Entity e : w.getNearbyEntities(new Location(w, pcx + 0.5, Y + 4.2, pcz + 0.5), 1.5, 1.5, 1.5))
            if (e instanceof TextDisplay && !e.getScoreboardTags().contains("orbit_classement")) e.remove();
        // Podium : trois marches (or, quartz, cuivre) sur une base de deepslate, face au centre (remplace ce qui gêne).
        for (int i = 0; i < 3; i++) {
            double[] m = posMarche(i);
            int mx = (int) Math.floor(m[0]), mz = (int) Math.floor(m[1]);
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                for (int h = 1; h <= 3; h++) {
                    Material mat = h > HAUTEURS[i] ? Material.AIR : h == HAUTEURS[i] ? DESSUS[i]
                            : (dx == 0 || dz == 0) ? Material.POLISHED_DEEPSLATE : Material.LIGHT_BLUE_CONCRETE;
                    if (h == HAUTEURS[i] && dx == 0 && dz == 0) mat = Material.SEA_LANTERN;
                    Block b = w.getBlockAt(mx + dx, Y + h, mz + dz);
                    if (b.getType() != mat) { b.setType(mat, false); poses++; }
                }
            }
        }
        return poses;
    }

    private static final java.util.Set<Material> MARCHES = java.util.EnumSet.of(Material.POLISHED_DEEPSLATE,
            Material.LIGHT_BLUE_CONCRETE, Material.GOLD_BLOCK, Material.QUARTZ_BLOCK, Material.WAXED_COPPER_BLOCK, Material.SEA_LANTERN);

    private static int air(World w, int x, int y, int z, Material m) {
        if (!w.getBlockAt(x, y, z).getType().isAir()) return 0;
        w.getBlockAt(x, y, z).setType(m, false);
        return 1;
    }

    private static int sol(World w, int x, int z, Material m) {
        if (w.getBlockAt(x, Y, z).getType().isAir() || !w.getBlockAt(x, Y + 1, z).getType().isAir()) return 0;
        w.getBlockAt(x, Y, z).setType(m, false);
        return 1;
    }
}
