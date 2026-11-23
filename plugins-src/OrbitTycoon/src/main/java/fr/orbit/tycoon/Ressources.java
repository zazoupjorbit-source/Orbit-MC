package fr.orbit.tycoon;

import io.papermc.paper.event.packet.PlayerChunkLoadEvent;
import io.papermc.paper.math.Position;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.Vector;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static fr.orbit.tycoon.Progression.objet;

/**
 * Ressources des outils : chaque outil a une liste de ressources (config.yml, section « ressources »).
 * La première est libre ; chaque quête de prestige récupérée débloque la suivante.
 * Plus la ressource est loin dans la liste, plus elle rapporte (Crédits, Flux, XP).
 * <p>
 * Mine, Bûcheron et Agriculture : <b>cumulatif</b>, vue perso (faux blocs envoyés au joueur seul, le serveur garde les vrais).
 * <ul>
 *   <li>Mine : un étage = un minerai = une ressource de la liste « pioche » (même ordre). Les minerais pas encore
 *       débloqués s'affichent dans la roche de leur étage et se minent comme elle.</li>
 *   <li>Bûcheron / Agriculture : l'île est découpée en parts autour de son centre, réparties entre toutes
 *       les ressources débloquées (au départ : chêne / blé partout).</li>
 * </ul>
 * Pêche et Mobzone : le joueur choisit une ressource dans le menu (gains seulement).
 */
final class Ressources implements Listener {

    /** Une ressource d'un outil. {@code origine} = la zone telle qu'elle est, avec ses gains habituels. */
    record Palier(int index, String nom, Material bloc, Material feuilles, Material mur, Material icone, EntityType mob,
                  boolean origine, long credit, long flux, long xp) { }

    /** Blocs de la Mine qui sont des minerais (le reste — pierre, tuf, gravier… — ne change pas). */
    private static final Set<Material> MINERAIS = Set.of(
            Material.COAL_ORE, Material.COPPER_ORE, Material.IRON_ORE, Material.GOLD_ORE, Material.REDSTONE_ORE, Material.LAPIS_ORE,
            Material.DEEPSLATE_IRON_ORE, Material.DEEPSLATE_GOLD_ORE, Material.DEEPSLATE_REDSTONE_ORE, Material.DEEPSLATE_LAPIS_ORE,
            Material.DEEPSLATE_DIAMOND_ORE, Material.DEEPSLATE_EMERALD_ORE, Material.AMETHYST_BLOCK,
            Material.NETHER_QUARTZ_ORE, Material.ANCIENT_DEBRIS);
    static final Set<Material> CULTURES = Set.of(Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS);

    private static final int[] SLOTS_OUTILS = {2, 3, 4, 5, 6};
    private static final int[] SLOTS_PALIERS = {19, 20, 21, 22, 23, 24, 25, 29, 30, 31, 32, 33, 34, 40};
    private static final int SLOT_RETOUR = 45, SLOT_INFO = 49;

    private static final class Menu implements InventoryHolder {
        private Inventory inv;
        private Outil.Mode mode;
        @Override public Inventory getInventory() { return inv; }
    }

    private final OrbitTycoon plugin;
    private final EnumMap<Outil.Mode, List<Palier>> listes = new EnumMap<>(Outil.Mode.class);
    /** Clé de chunk → positions (clé de bloc) des blocs de farm qui changent d'aspect. Fixe tant que les îles ne sont pas régénérées. */
    private final Map<Long, long[]> cache = new HashMap<>();
    /** Minerai réel de la Mine → ressource « pioche » qui lui correspond (DEEPSLATE_X compté comme X). */
    private final EnumMap<Material, Palier> minerais = new EnumMap<>(Material.class);
    /** Mob de la Mobzone → ressource « épée » qui lui correspond. */
    private final EnumMap<EntityType, Palier> mobs = new EnumMap<>(EntityType.class);
    /** Dernier message « part verrouillée » par joueur (anti-spam). */
    private final Map<UUID, Long> derniersRefus = new HashMap<>();

    /** Ce que le joueur récolte vraiment : clé du barème (bloc) et ressource dont il touche les gains (null = barème). */
    record Recolte(String cle, Palier palier) { }

    Ressources(OrbitTycoon plugin) {
        this.plugin = plugin;
        recharger();
    }

    /** Relit les listes de config.yml et oublie les positions de farm (à appeler après /tycoon reload ou une régénération). */
    void recharger() {
        listes.clear();
        cache.clear();
        minerais.clear();
        for (Outil.Mode m : Outil.Mode.values()) listes.put(m, lire(m));
        for (Palier r : listes.get(Outil.Mode.PIOCHE)) {
            if (r.bloc() == null) continue;
            minerais.putIfAbsent(r.bloc(), r);
            Material profond = Material.getMaterial("DEEPSLATE_" + r.bloc().name());
            if (profond != null) minerais.putIfAbsent(profond, r);
        }
        for (Material ore : MINERAIS)
            if (!minerais.containsKey(ore))
                plugin.getLogger().warning("ressources.pioche : aucun minerai pour " + ore + " (il restera visible pour tous).");
        mobs.clear();
        for (Palier r : listes.get(Outil.Mode.EPEE)) if (r.mob() != null) mobs.putIfAbsent(r.mob(), r);
    }

    /** Mine, Bûcheron, Agriculture, Mobzone : les ressources débloquées s'additionnent (pas de choix). */
    static boolean cumulatif(Outil.Mode m) {
        return m != Outil.Mode.CANNE;
    }

    /** Mobs des parts de la Mobzone, dans l'ordre (part 0 = libre). Null si une ressource n'a pas de mob. */
    List<EntityType> mobsDesParts() {
        List<EntityType> l = new ArrayList<>();
        for (Palier r : liste(Outil.Mode.EPEE)) l.add(r.mob());
        return l;
    }

    /** Mobzone : ce que rapporte ce mob tué (null = mob pas encore débloqué, rien du tout). */
    Recolte recolteMob(Player p, EntityType type) {
        Palier r = mobs.get(type);
        if (r == null) return new Recolte(type.name(), null);
        if (!debloque(p.getUniqueId(), Outil.Mode.EPEE, r.index())) return null;
        return new Recolte(type.name(), r.origine() ? null : r);
    }

    /** Ressource de l'épée qui correspond à ce mob (null si aucune). */
    Palier ressourceDuMob(EntityType type) { return mobs.get(type); }

    /** Ressources débloquées par le joueur pour cet outil, dans l'ordre (la première toujours). */
    List<Palier> debloquees(UUID id, Outil.Mode m) {
        List<Palier> l = liste(m), d = new ArrayList<>();
        for (Palier r : l) if (debloque(id, m, r.index())) d.add(r);
        return d;
    }

    private List<Palier> lire(Outil.Mode m) {
        List<Palier> l = new ArrayList<>();
        for (Map<?, ?> e : plugin.getConfig().getMapList("ressources." + m.id)) {
            boolean origine = Boolean.TRUE.equals(e.get("origine"));
            Material bloc = mat(e.get("bloc"));
            if (bloc == null && !origine) {
                plugin.getLogger().warning("ressources." + m.id + " : bloc inconnu « " + e.get("bloc") + " », ressource ignorée.");
                continue;
            }
            Material icone = mat(e.get("icone"));
            if (icone == null) icone = bloc != null && bloc.isItem() ? bloc : m.type;
            EntityType mob = null;
            if (e.get("mob") != null) {
                try { mob = EntityType.valueOf(e.get("mob").toString().trim().toUpperCase(java.util.Locale.ROOT)); }
                catch (IllegalArgumentException ex) { plugin.getLogger().warning("ressources." + m.id + " : mob inconnu « " + e.get("mob") + " »."); }
            }
            l.add(new Palier(l.size(), String.valueOf(e.get("nom") == null ? "?" : e.get("nom")), bloc, mat(e.get("feuilles")),
                    mat(e.get("mur")), icone, mob, origine, nombre(e.get("credit")), nombre(e.get("flux")), nombre(e.get("xp"))));
        }
        // La première ressource est la ressource libre (pioche : Charbon, épée : Zombies, avec leurs propres gains).
        if (l.isEmpty() || (!l.get(0).origine() && m != Outil.Mode.PIOCHE && m != Outil.Mode.EPEE))
            l.add(0, new Palier(0, "Ressources d'origine", null, null, null, m.type, null, true, 0, 0, 0));
        for (int i = 0; i < l.size(); i++) if (l.get(i).index() != i) { Palier p = l.get(i);
            l.set(i, new Palier(i, p.nom(), p.bloc(), p.feuilles(), p.mur(), p.icone(), p.mob(), p.origine(), p.credit(), p.flux(), p.xp())); }
        return List.copyOf(l);
    }

    private static Material mat(Object o) { return o == null ? null : Material.matchMaterial(o.toString()); }

    private static long nombre(Object o) {
        if (o instanceof Number n) return Math.max(0, n.longValue());
        try { return o == null ? 0 : Math.max(0, Long.parseLong(o.toString().trim())); } catch (NumberFormatException e) { return 0; }
    }

    List<Palier> liste(Outil.Mode m) { return listes.get(m); }

    // ------------------------------------------------------------------ déblocage et choix

    /** Quête (palier de prestige) qui débloque la ressource {@code index} ; -1 si aucune (origine ou trop loin). */
    int quete(int index) {
        List<Integer> paliers = plugin.quetes().paliers();
        return index <= 0 || index > paliers.size() ? -1 : paliers.get(index - 1);
    }

    boolean debloque(UUID id, Outil.Mode m, int index) {
        if (index == 0) return true;
        int q = quete(index);
        return q > 0 && plugin.monnaies().quetes(id, m).contains(q);
    }

    /** Nom de la ressource débloquée par la quête {@code palier} de l'outil (null si aucune). */
    String debloquePar(Outil.Mode m, int palier) {
        List<Integer> paliers = plugin.quetes().paliers();
        int i = paliers.indexOf(palier) + 1;
        List<Palier> l = liste(m);
        return i <= 0 || i >= l.size() ? null : l.get(i).nom();
    }

    /** Ressource choisie par le joueur pour cet outil (l'origine si son choix n'est plus débloqué). */
    Palier choisie(UUID id, Outil.Mode m) {
        List<Palier> l = liste(m);
        int i = plugin.monnaies().ressource(id, m);
        return i > 0 && i < l.size() && debloque(id, m, i) ? l.get(i) : l.get(0);
    }

    /** Une ligne pour les menus : ressource choisie, ou nombre de ressources débloquées (modes cumulatifs). */
    String resume(UUID id, Outil.Mode m) {
        if (!cumulatif(m)) return "<gray>Ressource : <white>" + choisie(id, m).nom();
        return "<gray>Débloquées : <white>" + debloquees(id, m).size() + " / " + liste(m).size();
    }

    /** Après une quête récupérée : renvoie au joueur la vue de la zone de l'outil (nouvelle ressource visible). */
    void actualiser(Player p, Outil.Mode m) {
        Zone z = zone(m);
        if (z != null && cumulatif(m)) renvoyerTout(p, z);
    }

    /**
     * Mine / Bûcheron / Agriculture : ce que rapporte ce bloc de farm pour ce joueur (d'après ce qu'il voit).
     * {@code t} = type réel du bloc avant la casse.
     */
    Recolte recolte(Player p, Zone z, Block b, Material t) {
        Outil.Mode m = outil(z);
        if (m == null) return new Recolte(t.name(), null);
        List<Palier> deb = debloquees(p.getUniqueId(), m);
        if (z == Zone.MINE) {
            if (!MINERAIS.contains(t)) return new Recolte(t.name(), null);
            Palier r = minerais.get(t);
            if (r != null && !deb.contains(r)) // minerai verrouillé : il se mine comme la roche qu'il montre
                return new Recolte(rocheCachee(z, b.getX(), b.getY(), b.getZ()).name(), null);
            return new Recolte(t.name(), r == null || r.origine() ? null : r);
        }
        if (!convertible(m, t.name())) return new Recolte(t.name(), null);
        Palier r = deb.get(secteur(z, b.getX(), b.getZ(), deb.size()));
        return new Recolte(t.name(), r.origine() ? null : r);
    }

    /** La ressource choisie remplace-t-elle les gains de ce bloc / poisson / mob ? (sinon : gains d'origine) */
    static boolean convertible(Outil.Mode m, String cle) {
        Material t = Material.matchMaterial(cle);
        return switch (m) {
            case HACHE -> t != null && Tag.LOGS.isTagged(t);
            case HOUE -> t != null && CULTURES.contains(t);
            case PIOCHE -> t != null && MINERAIS.contains(t);
            case CANNE, EPEE -> true;
        };
    }

    // ------------------------------------------------------------------ menu

    void ouvrir(Player p, Outil.Mode m) {
        UUID id = p.getUniqueId();
        Menu h = new Menu();
        h.mode = m;
        Inventory inv = Bukkit.createInventory(h, 54, Habillage.titre(plugin, p, 6, "Ressources · " + m.nom));
        h.inv = inv;
        Outil.Mode[] modes = Outil.Mode.values();
        for (int i = 0; i < modes.length; i++) {
            Outil.Mode x = modes[i];
            inv.setItem(SLOTS_OUTILS[i], objet(x.type, "<" + x.couleur + "><bold>" + x.nom, x == m,
                    resume(id, x),
                    "", x == m ? "<#E0B864>● Affiché" : "<#62CFF4>➜ Clic pour voir ses ressources"));
        }
        List<Palier> l = liste(m);
        Palier actuelle = choisie(id, m);
        for (int i = 0; i < l.size() && i < SLOTS_PALIERS.length; i++) inv.setItem(SLOTS_PALIERS[i], icone(id, m, l.get(i), actuelle));
        inv.setItem(SLOT_RETOUR, objet(Material.ARROW, "<#62CFF4>← Retour", false, "<gray>Revenir à l'interface de l'outil."));
        String zone = "<" + m.couleur + ">" + m.zone + "</" + m.couleur + ">";
        String[] fin = switch (m) {
            case PIOCHE -> new String[]{"<gray>Zone " + zone + " : un étage = un minerai.",
                    "<gray>Tu ne vois que les minerais débloqués ;",
                    "<gray>les autres restent dans la roche."};
            case HACHE, HOUE -> new String[]{"<gray>Zone " + zone + " : " + (m == Outil.Mode.HACHE ? "les arbres" : "les champs"),
                    "<gray>se partagent entre toutes tes",
                    "<gray>ressources débloquées (toi seul les vois)."};
            case EPEE -> new String[]{"<gray>Zone " + zone + " : une part de l'île par mob.",
                    "<gray>Les parts pas encore débloquées",
                    "<gray>restent fermées."};
            default -> new String[]{"<gray>Zone " + zone + " : chaque prise",
                    "<gray>compte comme la ressource choisie."};
        };
        List<String> info = new ArrayList<>(List.of(
                "<gray>Chaque <#E0B864>quête de prestige</#E0B864> récupérée",
                "<gray>auprès de l'Aventurier (<#E0B864>/quetes</#E0B864>) débloque",
                "<gray>la ressource suivante de l'outil.",
                "<gray>Plus tu vas loin, plus tu gagnes !",
                ""));
        info.addAll(List.of(fin));
        inv.setItem(SLOT_INFO, objet(Material.BOOK, "<#E0B864><bold>Comment ça marche ?", false, info.toArray(String[]::new)));
        Habillage.fond(plugin, p, inv);
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.2f);
    }

    private ItemStack icone(UUID id, Outil.Mode m, Palier r, Palier actuelle) {
        List<String> lore = new ArrayList<>();
        if (r.origine()) {
            lore.add("<gray>" + m.recolte + ".");
            lore.add("<gray>Gains habituels de la zone.");
        } else {
            lore.add("<gray>Par récolte :");
            if (r.credit() > 0) lore.add("<dark_gray> • <#E0B864>+" + Hud.nombre(r.credit()) + " ⛃ Crédits orbitaux");
            if (r.flux() > 0) lore.add("<dark_gray> • <#62CFF4>+" + Hud.nombre(r.flux()) + " ✦ Flux stellaire");
            if (r.xp() > 0) lore.add("<dark_gray> • <#3DDCB0>+" + Hud.nombre(r.xp()) + " XP <" + m.couleur + ">" + m.nom);
        }
        lore.add("");
        String titre = "<bold>" + (r.index() == 0 ? "" : r.index() + ". ") + r.nom();
        if (cumulatif(m) && debloque(id, m, r.index())) {
            if (lieu(m, r) != null) lore.add(0, lieu(m, r));
            lore.add("<#7CFC9A>✔ Débloquée");
            return objet(r.icone(), "<#7CFC9A>" + titre, true, lore.toArray(String[]::new));
        }
        if (cumulatif(m) && lieu(m, r) != null) lore.add(0, lieu(m, r));
        if (r == actuelle && !cumulatif(m)) {
            lore.add("<#7CFC9A>● Ressource choisie");
            return objet(r.icone(), "<#7CFC9A>" + titre, true, lore.toArray(String[]::new));
        }
        if (debloque(id, m, r.index())) {
            lore.add("<#62CFF4>➜ Clic pour la choisir");
            return objet(r.icone(), "<#E0B864>" + titre, false, lore.toArray(String[]::new));
        }
        int q = quete(r.index());
        lore.add(q > 0 ? "<#FF4D4D>🔒 Quête « Prestige " + q + " » du " + m.nom : "<#FF4D4D>🔒 Pas encore disponible");
        if (q > 0) lore.add("<dark_gray>  à récupérer auprès de l'Aventurier");
        return objet(Material.GRAY_DYE, "<gray>" + titre, false, lore.toArray(String[]::new));
    }

    private void choisir(Player p, Outil.Mode m, int index) {
        UUID id = p.getUniqueId();
        List<Palier> l = liste(m);
        if (index < 0 || index >= l.size()) return;
        Palier r = l.get(index);
        if (cumulatif(m) && debloque(id, m, index)) return; // rien à choisir : tout ce qui est débloqué compte
        if (!debloque(id, m, index)) {
            int q = quete(index);
            OrbitTycoon.msg(p, q > 0 ? "Récupère d'abord la quête <#E0B864>" + m.nom + " · Prestige " + q + "</#E0B864> auprès de l'Aventurier."
                    : "Cette ressource n'est pas encore disponible.");
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.8f);
            return;
        }
        if (choisie(id, m) == r) return;
        plugin.monnaies().definirRessource(id, m, index);
        OrbitTycoon.msg(p, "<" + m.couleur + ">" + m.nom + "</" + m.couleur + "> : tu récoltes maintenant <#E0B864>" + r.nom() + "</#E0B864>.");
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.4f);
        Zone z = zone(m);
        if (z != null) renvoyerTout(p, z);
        ouvrir(p, m);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void clic(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Menu h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getView().getTopInventory()) return;
        int slot = e.getSlot();
        if (slot == SLOT_RETOUR) { plugin.outil().ouvrir(p); return; }
        for (int i = 0; i < SLOTS_OUTILS.length; i++) if (slot == SLOTS_OUTILS[i]) { ouvrir(p, Outil.Mode.values()[i]); return; }
        for (int i = 0; i < SLOTS_PALIERS.length; i++) if (slot == SLOTS_PALIERS[i]) { choisir(p, h.mode, i); return; }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void glisser(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ vue perso (faux blocs)

    /** Zone dont les blocs changent d'aspect pour cet outil (null : pêche et mobzone, gains seulement). */
    private static Zone zone(Outil.Mode m) {
        return switch (m) {
            case PIOCHE -> Zone.MINE;
            case HACHE -> Zone.BUCHERON;
            case HOUE -> Zone.AGRICULTURE;
            default -> null;
        };
    }

    private static Outil.Mode outil(Zone z) {
        return switch (z) {
            case MINE -> Outil.Mode.PIOCHE;
            case BUCHERON -> Outil.Mode.HACHE;
            case AGRICULTURE -> Outil.Mode.HOUE;
            default -> null;
        };
    }

    /** Hauteurs à fouiller par zone (les îles sont à Y = 100 ; la mine descend de 10 étages de 5 blocs). */
    private static int yMin(Zone z) { return z == Zone.MINE ? Zone.Y - 70 : Zone.Y - 15; }
    private static int yMax(Zone z) { return z == Zone.BUCHERON ? Zone.Y + 70 : Zone.Y + 25; }

    /** Zone « vue perso » qui touche ce chunk (null sinon). Les îles sont à 1100 blocs : un chunk n'en touche qu'une. */
    private static Zone zoneDuChunk(int cx, int cz) {
        double x = (cx << 4) + 8, z = (cz << 4) + 8;
        for (Zone zo : new Zone[]{Zone.MINE, Zone.BUCHERON, Zone.AGRICULTURE})
            if (zo.distance(x, z) <= zo.rayon + 15 + 12) return zo;
        return null;
    }

    /** Ce bloc (dans son état réel ou d'origine) est-il un bloc de farm qui change d'aspect ? */
    private static boolean suivi(Zone z, Material t, int x, int zc) {
        return switch (z) {
            case BUCHERON -> Tag.LOGS.isTagged(t) || Tag.LEAVES.isTagged(t);
            case AGRICULTURE -> CULTURES.contains(t);
            case MINE -> MINERAIS.contains(t) && z.distance(x + 0.5, zc + 0.5) < Iles.FOSSE;
            default -> false;
        };
    }

    /** Positions suivies d'un chunk chargé (fouillé une seule fois, puis gardé en mémoire). */
    private long[] positions(Chunk c, Zone z) {
        long cle = c.getChunkKey();
        long[] deja = cache.get(cle);
        if (deja != null) return deja;
        World w = c.getWorld();
        int y0 = Math.max(w.getMinHeight(), yMin(z)), y1 = Math.min(w.getMaxHeight() - 1, yMax(z));
        ChunkSnapshot s = c.getChunkSnapshot(false, false, false);
        int bx = c.getX() << 4, bz = c.getZ() << 4;
        List<Long> l = new ArrayList<>();
        for (int x = 0; x < 16; x++)
            for (int zz = 0; zz < 16; zz++)
                for (int y = y0; y <= y1; y++)
                    if (suivi(z, s.getBlockType(x, y, zz), bx + x, bz + zz)) l.add(Block.getBlockKey(bx + x, y, bz + zz));
        // Les blocs en train de repousser (air, verre, jeune pousse) : on les suit d'après leur état d'origine.
        for (Map.Entry<Block, BlockData> en : plugin.zones().enAttente().entrySet()) {
            Block b = en.getKey();
            if (b.getWorld() == w && (b.getX() >> 4) == c.getX() && (b.getZ() >> 4) == c.getZ()
                    && suivi(z, en.getValue().getMaterial(), b.getX(), b.getZ())) l.add(b.getBlockKey());
        }
        long[] t = l.stream().mapToLong(Long::longValue).distinct().toArray();
        cache.put(cle, t);
        return t;
    }

    /** Part de l'île (autour de son centre) d'un bloc : 2 parts par ressource débloquée, en alternance. */
    private static int secteur(Zone z, int x, int zc, int n) {
        if (n <= 1) return 0;
        double a = Math.atan2(zc + 0.5 - z.cz, x + 0.5 - z.cx) + Math.PI; // 0 .. 2π
        return Math.floorMod((int) (a / (2 * Math.PI) * n * 2), n);
    }

    /** Roche affichée (et minée) à la place d'un minerai verrouillé : celle de son étage. */
    private static Material rocheCachee(Zone z, int x, int y, int zc) {
        return Iles.roche(Iles.etage(z.distance(x + 0.5, zc + 0.5)), x, y, zc);
    }

    /** Le joueur voit-il la zone telle qu'elle est construite ? (alors rien à lui envoyer au chargement des chunks) */
    private boolean telleQuelle(Zone z, List<Palier> deb) {
        if (z == Zone.MINE) return deb.size() >= liste(Outil.Mode.PIOCHE).size();
        return deb.size() == 1 && deb.get(0).origine();
    }

    /** Ce que le joueur doit voir à la place du bloc réel (null = le bloc réel, rien à changer). */
    private BlockData vue(Zone z, Block b, BlockData reel, List<Palier> deb) {
        if (z == Zone.MINE) {
            Palier r = minerais.get(reel.getMaterial());
            if (r == null || deb.contains(r)) return null;
            return rocheCachee(z, b.getX(), b.getY(), b.getZ()).createBlockData();
        }
        return vue(z, reel, deb.get(secteur(z, b.getX(), b.getZ(), deb.size())));
    }

    /** Bûcheron / Agriculture : aspect de la ressource {@code r} à la place du bloc réel (null = le bloc réel). */
    private static BlockData vue(Zone z, BlockData reel, Palier r) {
        if (r.origine() || r.bloc() == null) return null;
        Material t = reel.getMaterial();
        switch (z) {
            case BUCHERON -> {
                if (Tag.LOGS.isTagged(t)) {
                    BlockData d = r.bloc().createBlockData();
                    if (d instanceof Orientable o && reel instanceof Orientable ro && o.getAxes().contains(ro.getAxis())) o.setAxis(ro.getAxis());
                    return d;
                }
                if (Tag.LEAVES.isTagged(t) && r.feuilles() != null) {
                    BlockData d = r.feuilles().createBlockData();
                    if (d instanceof Leaves f) f.setPersistent(true);
                    return d;
                }
                return null;
            }
            case AGRICULTURE -> {
                if (!CULTURES.contains(t) || !(reel instanceof Ageable a)) return null;
                boolean mur = a.getAge() >= a.getMaximumAge();
                if (mur && r.mur() != null) return r.mur().createBlockData();
                BlockData d = r.bloc().createBlockData();
                if (d instanceof Ageable fa) {
                    int age = mur ? fa.getMaximumAge()
                            : Math.min(fa.getMaximumAge() - 1, Math.round((float) a.getAge() * fa.getMaximumAge() / a.getMaximumAge()));
                    fa.setAge(Math.max(0, age));
                    return d;
                }
                return mur ? d : Material.AIR.createBlockData(); // ressource sans pousse : invisible tant que pas mûre
            }
            default -> { return null; }
        }
    }

    /** Envoie au joueur l'aspect (faux ou réel) des blocs suivis d'un chunk. */
    private void envoyer(Player p, Chunk c, Zone z, boolean reelAussi) {
        List<Palier> deb = debloquees(p.getUniqueId(), outil(z));
        if (!reelAussi && telleQuelle(z, deb)) return;
        World w = c.getWorld();
        Map<Position, BlockData> envoi = new HashMap<>();
        for (long k : positions(c, z)) {
            Block b = w.getBlockAt(Block.getBlockKeyX(k), Block.getBlockKeyY(k), Block.getBlockKeyZ(k));
            BlockData reel = b.getBlockData();
            BlockData d = vue(z, b, reel, deb);
            if (d != null) envoi.put(Position.block(b.getX(), b.getY(), b.getZ()), d);
            else if (reelAussi) envoi.put(Position.block(b.getX(), b.getY(), b.getZ()), reel);
        }
        if (!envoi.isEmpty()) p.sendMultiBlockChange(envoi);
    }

    /** Après un déblocage : renvoie tous les chunks de la zone déjà reçus par le joueur. */
    private void renvoyerTout(Player p, Zone z) {
        World w = plugin.monde();
        if (p.getWorld() != w) return;
        for (long k : p.getSentChunkKeys()) {
            int cx = (int) k, cz = (int) (k >> 32);
            if (zoneDuChunk(cx, cz) != z || !w.isChunkLoaded(cx, cz)) continue;
            envoyer(p, w.getChunkAt(cx, cz), z, true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void chunkRecu(PlayerChunkLoadEvent e) {
        Chunk c = e.getChunk();
        if (c.getWorld() != plugin.monde()) return;
        Zone z = zoneDuChunk(c.getX(), c.getZ());
        if (z == null) return;
        Player p = e.getPlayer();
        if (Bukkit.isPrimaryThread()) envoyer(p, c, z, false);
        else Bukkit.getScheduler().runTask(plugin, () -> { if (p.isOnline() && c.isLoaded()) envoyer(p, c, z, false); });
    }

    /**
     * Un bloc de farm vient de changer côté serveur (récolte, repousse, pousse) : le serveur a renvoyé le vrai bloc
     * à tout le monde, on renvoie la vue perso aux joueurs proches, {@code delai} ticks plus tard.
     */
    void rafraichir(Block b, long delai) {
        if (b.getWorld() != plugin.monde()) return;
        Zone z = zoneDuChunk(b.getX() >> 4, b.getZ() >> 4);
        if (z == null) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            BlockData reel = b.getBlockData();
            if (!suivi(z, reel.getMaterial(), b.getX(), b.getZ())) return;
            for (Player p : b.getWorld().getPlayers()) {
                if (p.getLocation().distanceSquared(b.getLocation()) > 160 * 160) continue;
                BlockData d = vue(z, b, reel, debloquees(p.getUniqueId(), outil(z)));
                if (d != null) p.sendBlockChange(b.getLocation(), d);
            }
        }, Math.max(1, delai));
    }

    /** Pousse naturelle d'une culture : le serveur renvoie le vrai bloc, on remet la vue perso. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void pousse(BlockGrowEvent e) {
        if (CULTURES.contains(e.getBlock().getType())) rafraichir(e.getBlock(), 1);
    }

    /** Clic droit refusé sur un bloc de farm : le serveur renvoie les vrais blocs autour, on remet la vue perso. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void clicBloc(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        Block b = e.getClickedBlock();
        rafraichir(b, 1);
        rafraichir(b.getRelative(e.getBlockFace()), 1);
    }

    /** Où se trouve la ressource (affiché dans le menu), null si rien à dire. */
    private static String lieu(Outil.Mode m, Palier r) {
        return switch (m) {
            case PIOCHE -> "<gray>Étage <white>" + (r.index() + 1) + "</white> de la Mine.";
            case EPEE -> "<gray>Part <white>" + (r.index() + 1) + "</white> de la Mobzone.";
            default -> null;
        };
    }

    // ------------------------------------------------------------------ Mobzone : parts fermées

    /** Part de la Mobzone sous ce point (-1 : place centrale, ou hors de la Mobzone). */
    int partMobzone(Location l) {
        if (l == null || l.getWorld() != plugin.monde()) return -1;
        Zone z = Zone.MOBZONE;
        if (z.distance(l.getX(), l.getZ()) > z.rayon + 15) return -1;
        return Iles.partMobzone(z, l.getX(), l.getZ(), liste(Outil.Mode.EPEE).size());
    }

    private boolean fermee(Player p, int part) {
        return part > 0 && !Zones.constructeur(p) && !debloque(p.getUniqueId(), Outil.Mode.EPEE, part);
    }

    private void refus(Player p, int part) {
        long t = System.currentTimeMillis();
        Long avant = derniersRefus.get(p.getUniqueId());
        if (avant != null && t - avant < 1500) return;
        derniersRefus.put(p.getUniqueId(), t);
        List<Palier> l = liste(Outil.Mode.EPEE);
        String nom = part < l.size() ? l.get(part).nom() : "Cette part";
        int q = quete(part);
        p.sendActionBar(Navigateur.texte("<#FF4D4D>🔒 " + nom + " : " + (q > 0
                ? "récupère la quête <#E0B864>Épée · Prestige " + q + "</#E0B864> (Aventurier)" : "pas encore disponible")));
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.8f);
    }

    /** Mur invisible : on n'entre pas dans la part d'un mob pas encore débloqué. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void bouger(PlayerMoveEvent e) {
        Location a = e.getFrom(), b = e.getTo();
        if (a.getBlockX() == b.getBlockX() && a.getBlockZ() == b.getBlockZ()) return;
        Player p = e.getPlayer();
        int part = partMobzone(b);
        if (!fermee(p, part)) return;
        refus(p, part);
        if (fermee(p, partMobzone(a))) { e.setTo(Zone.MOBZONE.arrivee(plugin.monde())); return; } // déjà dedans (prestige baissé…)
        Location retour = a.clone();
        retour.setYaw(b.getYaw());
        retour.setPitch(b.getPitch());
        e.setTo(retour);
        Vector recul = a.toVector().subtract(b.toVector()).setY(0);
        if (recul.lengthSquared() > 1.0E-4) p.setVelocity(recul.normalize().multiply(0.45).setY(0.15));
    }

    /** Perle de l'Ender, fruit de chorus… : pas de raccourci vers une part fermée. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void teleporte(PlayerTeleportEvent e) {
        String cause = e.getCause().name();
        if (!cause.equals("ENDER_PEARL") && !cause.contains("CHORUS") && !cause.equals("CONSUMABLE_EFFECT")) return;
        int part = partMobzone(e.getTo());
        if (!fermee(e.getPlayer(), part)) return;
        e.setCancelled(true);
        refus(e.getPlayer(), part);
    }
}
