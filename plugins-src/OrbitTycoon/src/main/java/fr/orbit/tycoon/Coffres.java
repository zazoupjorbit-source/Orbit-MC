package fr.orbit.tycoon;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Baie des coffres au spawn : 6 coffres en deux ailes de 3, face à face de part et d'autre d'une allée lumineuse,
 * chacun avec son propre décor (Cryo, Solaire, Nébuleuse | Astéroïde, Réacteur, Ménagerie = coffre des pets).
 * Les noms viennent de config.yml (coffres.noms). Clic gauche = aperçu du contenu (coffres.loots), clic droit avec la clé en main = ouverture.
 * Blocs posés une fois par « /tycoon coffres poser » ; textes recréés au chargement de leur chunk.
 */
final class Coffres implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int Y = Zone.Y;
    private static final String TAG = "orbit_coffres";

    /** Nombre de coffres (les 5 de l'arc + la Ménagerie). */
    static final int NB = 6;
    /** Les 6 styles, dans l'ordre (style, couleur du nom). */
    private static final String[] STYLES = {"Cryo", "Solaire", "Nébuleuse", "Astéroïde", "Réacteur", "Ménagerie"};
    private static final String[] COULEURS = {"#62CFF4", "#E0B864", "#C77DFF", "#7CFC9A", "#FF4D4D", "#FF8FC7"};

    private final OrbitTycoon plugin;
    private final TextDisplay[] textes = new TextDisplay[NB + 1];

    private final AnimationCoffre animation;

    Coffres(OrbitTycoon plugin) {
        this.plugin = plugin;
        this.animation = new AnimationCoffre(plugin);
    }

    // ------------------------------------------------------------------ positions

    private double angle() { return plugin.getConfig().getDouble("coffres.angle", 36); }

    private double rayon() { return plugin.getConfig().getDouble("coffres.rayon", 36); }

    /** Écart (blocs) entre l'axe de l'allée et chaque aile ; pas (blocs) entre deux coffres d'une même aile. */
    private static final int ECART_AILE = 7, PAS_AILE = 6;

    /**
     * Centre (bloc) du socle n : deux ailes de 3 coffres face à face, de part et d'autre de l'allée centrale
     * (axe = coffres.angle). Aile gauche (vue depuis le point de TP) : 0, 1, 2 du fond vers l'entrée ; aile droite : 3, 4, 5.
     */
    private int[] centre(int n) {
        double a = Math.toRadians(angle());
        double along = rayon() + 2 - PAS_AILE * (n % 3);           // 38, 32, 26 avec rayon 36
        double side = (n < 3 ? -1 : 1) * ECART_AILE;               // gauche = côté négatif
        return new int[]{(int) Math.floor(along * Math.cos(a) - side * Math.sin(a)),
                (int) Math.floor(along * Math.sin(a) + side * Math.cos(a))};
    }

    /** Ancienne place du coffre n (0 à 4) : l'arc de 5 coffres d'avant les ailes (pour les retirer une fois). */
    private int[] ancienCentre(int n) {
        double r = rayon(), pas = 7.0 / r;
        double a = Math.toRadians(angle()) + (n - 2) * pas;
        return new int[]{(int) Math.floor(Math.cos(a) * r), (int) Math.floor(Math.sin(a) * r)};
    }

    /** Face (cardinale) du coffre n : tournée vers l'allée centrale. */
    private BlockFace versAllee(int n) {
        double a = Math.toRadians(angle()), s = n < 3 ? 1 : -1;      // vers l'axe = +v pour la gauche, -v pour la droite
        double vx = -Math.sin(a) * s, vz = Math.cos(a) * s;
        if (Math.abs(vx) > Math.abs(vz)) return vx > 0 ? BlockFace.EAST : BlockFace.WEST;
        return vz > 0 ? BlockFace.SOUTH : BlockFace.NORTH;
    }

    private int[] coffre(int n) { int[] c = centre(n); return new int[]{c[0], hauteurCoffre(n), c[1]}; }

    private static int hauteurCoffre(int n) { return n == 1 ? Y + 3 : Y + 2; }

    /** Numéro du coffre Ménagerie : il donne des pets (Pets.java) au lieu des loots de config.yml. */
    static final int MENAGERIE = 5;

    String nom(int n) {
        List<String> noms = plugin.getConfig().getStringList("coffres.noms");
        return n < noms.size() && !noms.get(n).isBlank() ? noms.get(n) : "Coffre " + (n + 1);
    }

    // ------------------------------------------------------------------ textes

    /**
     * Apparence des coffres : un modèle 3D du pack (orbittycoon:coffre_<style>, tools/CoffresModeles.java) posé
     * pile sur le bloc coffre, qu'il recouvre. Visible seulement des joueurs qui ont chargé le pack ;
     * les autres voient le coffre vanilla (le bloc reste là : c'est lui qu'on clique).
     */
    private final ItemDisplay[] modeles = new ItemDisplay[NB];
    private static final String[] MODELES_COFFRE = {"coffre_cryo", "coffre_solaire", "coffre_nebuleuse",
            "coffre_asteroide", "coffre_reacteur", "coffre_menagerie"};

    private ItemStack objetModele(int n) {
        ItemStack it = new ItemStack(Material.PAPER);
        it.editMeta(m -> m.setItemModel(new NamespacedKey("orbittycoon", MODELES_COFFRE[n])));
        return it;
    }

    /** Montre les modèles de coffres à un joueur qui vient de charger le pack. */
    void montrerModeles(Player p) {
        for (ItemDisplay d : modeles) if (d != null && d.isValid()) p.showEntity(plugin, d);
    }

    void demarrer() {
        World w = plugin.monde();
        for (var e : w.getEntities()) if (e.getScoreboardTags().contains(TAG)) e.remove();
        rafraichir();
    }

    void arreter() {
        animation.toutTerminer();
        for (TextDisplay t : textes) if (t != null && t.isValid()) t.remove();
        for (ItemDisplay d : modeles) if (d != null && d.isValid()) d.remove();
    }

    void rafraichir() {
        World w = plugin.monde();
        for (int n = 0; n < NB; n++) {
            int[] c = centre(n);
            Location loc = new Location(w, c[0] + 0.5, Y + (n == 1 ? 6.6 : 5.6), c[1] + 0.5);
            Component t = MM.deserialize("<" + COULEURS[n] + "><bold>" + MM.escapeTags(nom(n)) + "</bold>\n<gray>Clic gauche : contenu · Clic droit : ouvrir");
            textes[n] = majTexte(textes[n], loc, t, 1.3f);
            // Modèle 3D posé au centre du bloc coffre ; le modèle a ses serrures au nord et au sud :
            // tourné d'un quart de tour si le coffre regarde vers l'est ou l'ouest.
            Location lc = new Location(w, c[0] + 0.5, hauteurCoffre(n) + 0.5, c[1] + 0.5);
            if (lc.isChunkLoaded() && (modeles[n] == null || !modeles[n].isValid())) {
                int num = n;
                BlockFace f = versAllee(n);
                float quart = f == BlockFace.EAST || f == BlockFace.WEST ? (float) (Math.PI / 2) : 0f;
                modeles[n] = w.spawn(lc, ItemDisplay.class, d -> {
                    d.setItemStack(objetModele(num));
                    d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                    d.setPersistent(false);
                    d.setVisibleByDefault(false);
                    d.addScoreboardTag(TAG);
                    d.setBrightness(new Display.Brightness(13, 15));
                    d.setViewRange(1.5f);
                    d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(quart, 0, 1, 0),
                            new Vector3f(1, 1, 1), new AxisAngle4f()));
                });
                for (Player pl : Bukkit.getOnlinePlayers())
                    if (plugin.pack() != null && plugin.pack().charge(pl)) pl.showEntity(plugin, modeles[n]);
            }
        }
        // Grand titre au-dessus du mur du fond : visible de loin dans tout le spawn.
        double a = Math.toRadians(angle()), r = rayon() + MUR - 0.5;
        Location titre = new Location(w, Math.cos(a) * r + 0.5, Y + 13.5, Math.sin(a) * r + 0.5);
        textes[NB] = majTexte(textes[NB], titre, MM.deserialize(
                "<gradient:#62CFF4:#E0B864><bold>COFFRES ORBITAUX</bold></gradient>\n<gray>6 coffres, 6 trésors de la station"), 3.0f);
    }

    /** Point de TP (/coffres) : au bord de l'espace des coffres, tourné vers eux. */
    Location pointTp() {
        double a = Math.toRadians(angle()), r = rayon() - 13;
        return new Location(plugin.monde(), Math.floor(Math.cos(a) * r) + 0.5, Y + 1, Math.floor(Math.sin(a) * r) + 0.5,
                Zone.yaw(Math.cos(a), Math.sin(a)), 0f);
    }

    private TextDisplay majTexte(TextDisplay ancien, Location loc, Component texte, float echelle) {
        if (!loc.isChunkLoaded()) return ancien;
        if (ancien != null && ancien.isValid()) { ancien.text(texte); return ancien; }
        return loc.getWorld().spawn(loc, TextDisplay.class, d -> {
            d.text(texte);
            d.setPersistent(false);
            d.setBillboard(Display.Billboard.VERTICAL);
            d.setShadowed(true);
            d.setViewRange(3f);
            d.setBackgroundColor(Color.fromARGB(130, 8, 16, 32));
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                    new Vector3f(echelle, echelle, echelle), new AxisAngle4f()));
            d.addScoreboardTag(TAG);
        });
    }

    @EventHandler
    public void chunk(ChunkLoadEvent e) {
        if (!e.getWorld().equals(plugin.monde())) return;
        for (int n = 0; n < NB; n++) {
            int[] c = centre(n);
            if (c[0] >> 4 == e.getChunk().getX() && c[1] >> 4 == e.getChunk().getZ()) {
                Bukkit.getScheduler().runTask(plugin, this::rafraichir);
                return;
            }
        }
    }

    /** Numéro (0 à 5) du coffre à cet endroit, ou -1. */
    private int numero(Block b) {
        if (!b.getWorld().equals(plugin.monde())) return -1;
        for (int n = 0; n < NB; n++) {
            int[] c = coffre(n);
            if (b.getX() == c[0] && b.getY() == c[1] && b.getZ() == c[2]) return n;
        }
        return -1;
    }

    private String titre(int n) {
        return "<" + COULEURS[n] + "><bold>" + MM.escapeTags(nom(n)) + "</bold></" + COULEURS[n] + ">";
    }

    /** Clic gauche : aperçu du contenu. Clic droit : ouverture avec la clé du coffre en main. */
    @EventHandler(priority = EventPriority.HIGH)
    public void clic(PlayerInteractEvent e) {
        Block b = e.getClickedBlock();
        if (b == null) return;
        boolean gauche = e.getAction() == Action.LEFT_CLICK_BLOCK;
        if (!gauche && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        int n = numero(b);
        if (n < 0) return;
        Player p = e.getPlayer();
        // Un admin en créatif qui s'accroupit peut encore casser le coffre.
        if (gauche && p.getGameMode() == GameMode.CREATIVE && p.isSneaking()) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (gauche) { apercu(p, n); plugin.tuto().fait(p, Tuto.Etape.COFFRE_APERCU); }
        else ouvrir(p, n, b);
    }

    // ------------------------------------------------------------------ loots (à remplir dans config.yml)

    /**
     * Une ligne de coffres.loots.<n>. type : objet (vanilla), capsule (Crédits/Flux = minutes de farm), booster,
     * cle (d'un autre coffre), generateur (relatif au palier débloqué de l'île), titre (plaque cosmétique),
     * equipement (pièce de farm au hasard). rarete : commun → mythique (sinon déduite de la chance).
     */
    private record Loot(String type, Material objet, int nombre, double chance, String nom, Rarete rarete, Map<?, ?> d) {
        String texte(String k, String def) { return d.get(k) instanceof Object o ? String.valueOf(o) : def; }
        double reel(String k, double def) { return d.get(k) instanceof Number x ? x.doubleValue() : def; }
        int entier(String k, int def) { return d.get(k) instanceof Number x ? x.intValue() : def; }
    }

    /** Raretés des lots : couleur, nom, intensité de l'animation ; Légendaire et Mythique sont annoncés à tous. */
    enum Rarete {
        COMMUN("Commun", "<#A8B3C2>", 0), RARE("Rare", "<#62CFF4>", 1), EPIQUE("Épique", "<#C77DFF>", 2),
        LEGENDAIRE("Légendaire", "<#E0B864>", 3), MYTHIQUE("Mythique", "<gradient:#62CFF4:#E0B864>", 3);
        final String nom, couleur; final int niveau;
        Rarete(String nom, String couleur, int niveau) { this.nom = nom; this.couleur = couleur; this.niveau = niveau; }
        String etiquette() { return couleur + "<bold>" + nom.toUpperCase(Locale.FRANCE) + "</bold>" + (this == MYTHIQUE ? "</gradient>" : ""); }
        static Rarete parse(Object o) {
            if (o == null) return null;
            String s = java.text.Normalizer.normalize(String.valueOf(o), java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
            for (Rarete r : values()) if (r.name().equalsIgnoreCase(s)) return r;
            return null;
        }
    }

    /** Le coffre n est-il le coffre Farm (celui de la Clé Farm) ? Il donne les équipements de farm. */
    private boolean estFarm(int n) {
        var cfg = plugin.getConfig();
        return cfg.getBoolean("equipements.coffre-farm", true) && n == cfg.getInt("coffres.cle-farm.coffre", 2) - 1;
    }

    private static final java.util.Set<String> TYPES = java.util.Set.of("objet", "capsule", "booster", "cle", "generateur", "titre", "equipement", "armure");

    /** Loots du coffre n lus dans coffres.loots.<n+1> ; les lignes invalides sont ignorées. */
    private List<Loot> loots(int n) {
        List<Loot> res = new ArrayList<>();
        for (Map<?, ?> m : plugin.getConfig().getMapList("coffres.loots." + (n + 1))) {
            String type = m.get("type") instanceof String s ? s.toLowerCase(Locale.ROOT) : "objet";
            if (!TYPES.contains(type)) continue;
            Material mat = null;
            if (type.equals("objet")) {
                mat = Material.matchMaterial(String.valueOf(m.get("objet")));
                if (mat == null || !mat.isItem() || mat.isAir()) continue;
            }
            int nombre = m.get("nombre") instanceof Number x ? Math.max(1, x.intValue()) : 1;
            double chance = m.get("chance") instanceof Number x ? x.doubleValue() : 1;
            if (chance <= 0) continue;
            String nom = m.get("nom") instanceof String s && !s.isBlank() ? s : null;
            res.add(new Loot(type, mat, nombre, chance, nom, Rarete.parse(m.get("rarete")), m));
        }
        return res;
    }

    private static Rarete rarete(Loot l, double total) {
        if (l.rarete() != null) return l.rarete();
        double part = l.chance() / total;
        return part < 0.01 ? Rarete.LEGENDAIRE : part < 0.05 ? Rarete.EPIQUE : part < 0.15 ? Rarete.RARE : Rarete.COMMUN;
    }

    /** L'objet donné par ce lot à ce joueur (générateur : selon le palier de son île ; équipement : pièce au hasard). */
    private ItemStack objet(Loot l, Player p) {
        ItemStack it = switch (l.type()) {
            case "capsule" -> plugin.butin().capsule(l.texte("monnaie", "credits").startsWith("flux") ? Monnaies.Monnaie.FLUX
                    : Monnaies.Monnaie.CREDIT, l.entier("minutes", 5), l.entier("taille", 1), l.nombre());
            case "booster" -> {
                Boosters.Sorte s = Boosters.Sorte.parse(l.texte("booster", "credits"));
                yield plugin.boosters().objet(s == null ? Boosters.Sorte.CREDITS : s, l.reel("mult", 1.5), l.entier("minutes", 15), l.nombre());
            }
            case "cle" -> cle(Math.max(0, Math.min(NB - 1, l.entier("coffre", 1) - 1)), l.nombre());
            case "generateur" -> l.d().get("id") instanceof String id && plugin.generateurs().type(id) != null
                    ? plugin.generateurs().generateur(plugin.generateurs().type(id), l.nombre())
                    : plugin.generateurs().generateurRelatif(p.getUniqueId(), l.entier("ecart", 0), l.nombre());
            case "armure" -> {
                Equipements.Premium a = Equipements.Premium.parse(l.texte("armure", "saturne"));
                yield plugin.equipements().tirerPremium(a == null ? Equipements.Premium.SATURNE : a, l.texte("piece", "hasard"));
            }
            case "titre" -> plugin.butin().plaque(l.texte("titre", "?"));
            case "equipement" -> plugin.equipements().tirer();
            default -> new ItemStack(l.objet(), Math.min(l.nombre(), l.objet().getMaxStackSize()));
        };
        if (l.nom() != null) it.editMeta(m -> m.itemName(MM.deserialize(l.nom())));
        return it;
    }

    /** Nom affiché d'un lot (« 3× Grande Capsule de Crédits »). */
    private static Component nomLot(Loot l, ItemStack it) {
        Component nom = l.nom() != null ? MM.deserialize(l.nom()) : it.effectiveName();
        return l.nombre() > 1 ? MM.deserialize("<white>" + l.nombre() + "× </white>").append(nom) : nom;
    }

    // ------------------------------------------------------------------ aperçu (clic gauche)

    /** Menu d'aperçu : on regarde, on ne prend rien. */
    private static final class Apercu implements InventoryHolder {
        private Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    /** Cases du contenu dans le grand menu (6 rangées) : colonnes 2 à 8 des rangées 2 à 5, soit 28 cases. */
    static final int[] CASES = cases();

    private static int[] cases() {
        int[] c = new int[28];
        for (int r = 0; r < 4; r++) for (int col = 0; col < 7; col++) c[r * 7 + col] = (r + 1) * 9 + col + 1;
        return c;
    }

    /** Icône du coffre (en-tête du menu), dans la couleur de son style. */
    private static final Material[] ICONES = {Material.BLUE_ICE, Material.GOLD_BLOCK, Material.AMETHYST_BLOCK,
            Material.MOSSY_COBBLESTONE, Material.REDSTONE_BLOCK, Material.LEAD};

    private void apercu(Player p, int n) {
        List<Loot> loots = loots(n);
        Apercu holder = new Apercu();
        Inventory inv = Bukkit.createInventory(holder, 54, Habillage.actif(plugin, p)
                ? Habillage.titre(plugin, p, 6, "Coffre " + nom(n))
                : MM.deserialize(titre(n) + "<dark_gray> · contenu"));
        holder.inv = inv;

        // 28 cases : les loots dans l'ordre de config.yml, puis des cases vides « à venir ».
        double total = loots.stream().mapToDouble(Loot::chance).sum();
        if (n == MENAGERIE) plugin.pets().remplirApercu(inv, CASES);
        else if (estFarm(n) && loots.isEmpty()) {
            List<ItemStack> pieces = plugin.equipements().toutes();
            String pct = String.format(Locale.FRANCE, "%.1f", 100.0 / pieces.size());
            for (int i = 0; i < CASES.length; i++) {
                if (i < pieces.size()) {
                    ItemStack it = pieces.get(i);
                    it.editMeta(m -> {
                        List<Component> l = new ArrayList<>(m.lore() == null ? List.of() : m.lore());
                        l.add(MM.deserialize("<!italic><gray>Chance : <#E0B864>" + pct + " %"));
                        m.lore(l);
                    });
                    inv.setItem(CASES[i], it);
                } else inv.setItem(CASES[i], info(Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                        "<!italic><dark_gray>Case " + (i + 1), "<!italic><gray>Récompense bientôt dévoilée"));
            }
        }
        else for (int i = 0; i < CASES.length; i++) {
            if (i < loots.size()) {
                Loot l = loots.get(i);
                ItemStack it = objet(l, p);
                double v = l.chance() * 100 / total;
                String pct = String.format(Locale.FRANCE, v < 1 ? "%.2f" : "%.1f", v);
                Rarete r = rarete(l, total);
                Component nom = nomLot(l, it);
                it.editMeta(m -> {
                    m.itemName(nom);
                    List<Component> lore = new ArrayList<>();
                    lore.add(MM.deserialize("<!italic>" + r.etiquette()));
                    if (l.type().equals("equipement")) lore.add(MM.deserialize("<!italic><gray>Une des 20 pièces de farm (5 sets × 4)"));
                    else if (l.type().equals("generateur") && l.d().get("id") instanceof String) { if (m.lore() != null) for (Component c : m.lore()) if (!c.equals(Component.empty())) lore.add(c); }
                    else if (l.type().equals("armure") && !l.texte("piece", "hasard").matches("(?i)casque|plastron|jambieres|bottes")) {
                        lore.add(MM.deserialize("<!italic><gray>Une des 4 pièces de l'armure, au hasard"));
                        if (m.lore() != null) for (Component c : m.lore()) if (!c.equals(Component.empty())) lore.add(c);
                    }
                    else if (l.type().equals("generateur")) lore.add(MM.deserialize("<!italic><gray>" + (l.entier("ecart", 0) == 0
                            ? "Ton meilleur générateur débloqué" : l.entier("ecart", 0) > 0 ? l.entier("ecart", 0) + " palier(s) au-dessus de ton meilleur"
                            : (-l.entier("ecart", 0)) + " palier(s) sous ton meilleur")));
                    else if (m.lore() != null) for (Component c : m.lore()) if (!c.equals(Component.empty())) lore.add(c);
                    lore.add(Component.empty());
                    lore.add(MM.deserialize("<!italic><gray>Chance : <#E0B864>" + pct + " %"));
                    m.lore(lore);
                });
                inv.setItem(CASES[i], it);
            } else {
                inv.setItem(CASES[i], info(Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                        "<!italic><dark_gray>Case " + (i + 1), "<!italic><gray>Récompense bientôt dévoilée"));
            }
        }
        // En-tête : le coffre et son nombre de récompenses.
        inv.setItem(4, info(ICONES[n], "<!italic>" + titre(n),
                (n == MENAGERIE ? "<!italic><gray>20 pets, 4 raretés : 80 pets à collectionner"
                        : estFarm(n) && loots.isEmpty() ? "<!italic><gray>5 sets d'équipement de farm, 4 pièces chacun" : "<!italic><gray>" + (loots.isEmpty() ? "Contenu bientôt dévoilé" : loots.size() + " récompense(s) possible(s)")),
                n == MENAGERIE ? "<!italic><gray>Un pet tiré au hasard par clé :" : "<!italic><gray>Une seule récompense tirée au hasard",
                n == MENAGERIE ? "<!italic><gray>d'abord la rareté, puis l'espèce." : "<!italic><gray>par clé utilisée."));
        // Bas : la clé qui ouvre ce coffre (avec son vrai design).
        ItemStack cle = cle(n, 1);
        cle.editMeta(m -> m.lore(List.of(
                MM.deserialize("<!italic><gray>Clic droit sur le coffre avec une"),
                MM.deserialize("<!italic><#E0B864>Clé " + MM.escapeTags(nom(n)) + "<gray> en main pour l'ouvrir."))));
        inv.setItem(49, cle);
        Habillage.fond(plugin, p, inv);
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.5f, 1.4f);
    }

    private static ItemStack info(Material mat, String nom, String... lore) {
        ItemStack it = new ItemStack(mat);
        it.editMeta(m -> {
            m.displayName(MM.deserialize(nom));
            if (lore.length > 0) m.lore(java.util.Arrays.stream(lore).map(MM::deserialize).toList());
        });
        return it;
    }

    @EventHandler
    public void clicMenu(InventoryClickEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Apercu) e.setCancelled(true);
    }

    @EventHandler
    public void glisserMenu(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Apercu) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ clés et ouverture (clic droit)

    private NamespacedKey cleCoffre() { return new NamespacedKey(plugin, "cle_coffre"); }

    /** Modèle du pack (custom_model_data) de la clé de chaque style : tools/gen-cles.ps1. */
    private static final String[] MODELES = {"cle_cryo", "cle_solaire", "cle_nebuleuse", "cle_asteroide", "cle_reacteur", "cle_menagerie"};

    /**
     * Clé du coffre n : un crochet dont le pack change l'apparence (design propre à chaque coffre).
     * Sans le pack, le joueur voit un crochet brillant. Seul le marqueur PDC compte pour ouvrir.
     */
    ItemStack cle(int n, int nombre) {
        ItemStack it = new ItemStack(Material.TRIPWIRE_HOOK, nombre);
        it.editMeta(m -> {
            m.itemName(MM.deserialize("<" + COULEURS[n] + "><bold>Clé " + MM.escapeTags(nom(n))));
            m.lore(List.of(
                    MM.deserialize("<!italic><gray>Style : <" + COULEURS[n] + ">" + STYLES[n]),
                    MM.deserialize("<!italic><gray>Ouvre le coffre <white>" + MM.escapeTags(nom(n)) + "</white> au spawn (/coffres)."),
                    MM.deserialize("<!italic><dark_gray>Clic droit sur le coffre, clé en main.")));
            var cmd = m.getCustomModelDataComponent();
            cmd.setStrings(List.of(MODELES[n]));
            m.setCustomModelDataComponent(cmd);
            m.setEnchantmentGlintOverride(true);
            m.getPersistentDataContainer().set(cleCoffre(), PersistentDataType.INTEGER, n);
        });
        return it;
    }

    /** Numéro du coffre que cette clé ouvre, ou -1. */
    private int numeroCle(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return -1;
        Integer v = it.getItemMeta().getPersistentDataContainer().get(cleCoffre(), PersistentDataType.INTEGER);
        return v == null ? -1 : v;
    }

    private void ouvrir(Player p, int n, Block b) {
        ItemStack main = p.getInventory().getItemInMainHand();
        if (numeroCle(main) != n) {
            OrbitTycoon.msg(p, titre(n) + "<gray> : il te faut une <#E0B864>Clé " + MM.escapeTags(nom(n))
                    + "<gray> en main. <dark_gray>(clic gauche pour voir le contenu)");
            p.playSound(b.getLocation(), Sound.BLOCK_CHEST_LOCKED, 0.8f, 1.2f);
            return;
        }
        if (animation.enCours(p.getUniqueId())) {
            OrbitTycoon.msg(p, "<gray>Attends la fin de l'ouverture en cours !");
            return;
        }
        plugin.tuto().fait(p, Tuto.Etape.COFFRE_OUVRIR);
        Color couleur = Color.fromRGB(Integer.parseInt(COULEURS[n].substring(1), 16));
        if (n == MENAGERIE) {
            main.setAmount(main.getAmount() - 1);
            Pets pets = plugin.pets();
            Pets.Pet pet = pets.tirer();
            animation.lancer(p, b, couleur, pets.defilement(), pets.carte(pet), pet.rarete().ordinal(),
                    () -> pets.recevoir(p, pet));
            return;
        }
        List<Loot> loots = loots(n);
        if (estFarm(n) && loots.isEmpty()) {   // coffre Farm sans loots : une pièce d'équipement de farm au hasard
            main.setAmount(main.getAmount() - 1);
            Equipements eq = plugin.equipements();
            ItemStack gagne = eq.tirer();
            List<AnimationCoffre.Carte> defilement = eq.toutes().stream()
                    .map(it -> new AnimationCoffre.Carte(it, it.getItemMeta().itemName())).toList();
            Component nomGagne = gagne.getItemMeta().itemName();
            animation.lancer(p, b, couleur, defilement, new AnimationCoffre.Carte(gagne, nomGagne), 1, () -> {
                for (ItemStack reste : p.getInventory().addItem(gagne).values()) p.getWorld().dropItemNaturally(p.getLocation(), reste);
                OrbitTycoon.msg(p, titre(n) + "<gray> : tu obtiens <white>" + MM.serialize(nomGagne) + "<gray> !");
            });
            return;
        }
        if (loots.isEmpty()) {   // pas de loots définis : on ne consomme pas la clé
            OrbitTycoon.msg(p, titre(n) + "<gray> : le contenu n'est pas encore prêt, garde ta clé !");
            p.playSound(b.getLocation(), Sound.BLOCK_CHEST_LOCKED, 0.8f, 1.2f);
            return;
        }
        main.setAmount(main.getAmount() - 1);
        double total = loots.stream().mapToDouble(Loot::chance).sum();
        double r = ThreadLocalRandom.current().nextDouble(total);
        Loot tire = loots.getLast();
        for (Loot l : loots) { r -= l.chance(); if (r < 0) { tire = l; break; } }
        Loot gagne = tire;
        Rarete rar = rarete(gagne, total);
        ItemStack lot = objet(gagne, p);   // tiré maintenant : l'animation montre exactement ce qui sera donné
        Component nom = nomLot(gagne, lot);
        List<AnimationCoffre.Carte> defilement = new ArrayList<>();
        for (Loot l : loots) { ItemStack it = objet(l, p); defilement.add(new AnimationCoffre.Carte(it, nomLot(l, it))); }
        animation.lancer(p, b, couleur, defilement, new AnimationCoffre.Carte(lot.clone(), nom), rar.niveau, () -> {
            List<ItemStack> piles = new ArrayList<>();
            int reste = gagne.type().equals("objet") ? gagne.nombre() : lot.getAmount();
            while (reste > 0) {   // au-delà d'une pile : plusieurs piles
                ItemStack it = lot.clone();
                it.setAmount(Math.min(reste, it.getMaxStackSize()));
                reste -= it.getAmount();
                piles.add(it);
            }
            for (ItemStack it : piles)
                for (ItemStack trop : p.getInventory().addItem(it).values()) p.getWorld().dropItemNaturally(p.getLocation(), trop);
            String texte = MM.serialize(nom);
            OrbitTycoon.msg(p, titre(n) + "<gray> : " + rar.etiquette() + " <gray>tu obtiens <white>" + texte + "<gray> !");
            if (rar.ordinal() >= Rarete.LEGENDAIRE.ordinal()) {
                Bukkit.broadcast(MM.deserialize("<#62CFF4>✦ <white>" + MM.escapeTags(p.getName()) + "<gray> a ouvert le coffre "
                        + titre(n) + "<gray> et obtient " + rar.etiquette() + " <white>" + texte + "<gray> !"));
                for (Player pl : Bukkit.getOnlinePlayers())
                    pl.playSound(pl.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.5f, rar == Rarete.MYTHIQUE ? 0.8f : 1.2f);
            }
        });
    }

    @EventHandler
    public void quitter(org.bukkit.event.player.PlayerQuitEvent e) { animation.terminer(e.getPlayer().getUniqueId()); }

    // ------------------------------------------------------------------ construction (une seule fois)

    private World w;
    private int poses;
    /** Mode retrait : air()/poserCoffre() enlèvent le bloc s'il est exactement celui que le décor aurait posé. */
    private boolean retrait;
    /** Face du coffre en cours de pose. */
    private BlockFace face = BlockFace.NORTH;

    /** Fichier témoin : l'ancien arc de 5 coffres a déjà été retiré (passage aux deux ailes). */
    private java.io.File temoinAiles() { return new java.io.File(plugin.getDataFolder(), "coffres-ailes.ok"); }

    /**
     * Pose les 6 socles et leurs coffres. Ne remplace que de l'air (et le dallage du sol sous les socles).
     * La première fois avec les ailes, retire d'abord l'ancien arc de 5 coffres (seulement ses propres blocs).
     * Renvoie un résumé.
     */
    String poser() {
        w = plugin.monde();
        String retires = "";
        if (!temoinAiles().exists()) {
            retires = retirerAncienArc() + " blocs de l'ancien arc retirés, ";
            try { temoinAiles().createNewFile(); } catch (java.io.IOException ignore) { }
        }
        poses = 0;
        espace();
        for (int n = 0; n < NB; n++) {
            int[] c = centre(n);
            face = versAllee(n);
            decor(n, c[0], c[1]);
        }
        return retires + poses + " blocs posés.";
    }

    private void decor(int n, int cx, int cz) {
        switch (n) {
            case 0 -> cryo(cx, cz);
            case 1 -> solaire(cx, cz);
            case 2 -> nebuleuse(cx, cz);
            case 3 -> asteroide(cx, cz);
            case 4 -> reacteur(cx, cz);
            default -> menagerie(cx, cz);
        }
    }

    /**
     * Retire l'ancien arc de 5 coffres : rejoue chaque décor à son ancienne place en mode retrait (un bloc n'est enlevé
     * que s'il est exactement celui que le décor avait posé), puis remet le sol des anciens dallages hors de l'esplanade
     * (le sol de l'esplanade est refait par espace()).
     */
    private int retirerAncienArc() {
        poses = 0;
        retrait = true;
        try {
            for (int n = 0; n < 5; n++) { int[] c = ancienCentre(n); decor(n, c[0], c[1]); }
        } finally { retrait = false; }
        int retires = poses;
        for (int n = 0; n < 5; n++) {
            int[] c = ancienCentre(n);
            for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
                if (Math.sqrt(dx * dx + dz * dz) > 3.2) continue;
                int x = c[0] + dx, z = c[1] + dz;
                if (dansEsplanade(x, z)) continue;
                int[] src = solVoisin(x, z);
                if (src == null) continue;
                Block b = w.getBlockAt(x, Y, z);
                Material m = w.getBlockAt(src[0], Y, src[1]).getType();
                if (!m.isAir() && w.getBlockAt(x, Y + 1, z).getType().isAir() && b.getType() != m) b.setType(m, false);
            }
        }
        return retires;
    }

    /** Vrai si (x, z) est dans le sol de l'esplanade que refait espace(). */
    private boolean dansEsplanade(int x, int z) {
        double d = Math.sqrt((double) x * x + (double) z * z), e = ecart(x + 0.5, z + 0.5);
        return d >= rayon() - 10 && d <= rayon() + MUR && Math.abs(e) <= Math.toRadians(DEMI) && distAvenue(x + 0.5, z + 0.5) >= 8;
    }

    /**
     * Sol d'origine probable près d'une avenue : le bloc 9 ou 10 blocs plus loin dans le sens de l'avenue la plus proche
     * (le motif d'une avenue se répète le long de son axe), hors des anciens dallages.
     */
    private int[] solVoisin(int x, int z) {
        Zone proche = null;
        double best = 99;
        for (Zone zo : Zone.farms()) {
            double al = (x + 0.5) * zo.ux() + (z + 0.5) * zo.uz();
            double pe = Math.abs(-(x + 0.5) * zo.uz() + (z + 0.5) * zo.ux());
            if (al > 0 && pe < best) { best = pe; proche = zo; }
        }
        if (proche == null) return null;
        for (int k : new int[]{9, -9, 10, -10}) {
            int sx = (int) Math.round(x + k * proche.ux()), sz = (int) Math.round(z + k * proche.uz());
            boolean libre = true;
            for (int n = 0; n < 5 && libre; n++) {
                int[] c = ancienCentre(n);
                if (Math.hypot(sx - c[0], sz - c[1]) < 3.6) libre = false;
            }
            if (libre) return new int[]{sx, sz};
        }
        return null;
    }

    // ------------------------------------------------------------------ espace des coffres

    /** Distance (au-delà du rayon des coffres) du mur de verre du fond. */
    private static final int MUR = 6;
    /** Demi-ouverture (degrés) de l'espace, de part et d'autre de l'angle des coffres. */
    private static final double DEMI = 26;

    /** Distance à l'avenue la plus proche (les avenues partent du centre vers chaque zone). */
    private static double distAvenue(double x, double z) {
        double best = 99;
        for (Zone zo : Zone.farms()) {
            double a = Math.toRadians(zo.angle), along = x * Math.cos(a) + z * Math.sin(a);
            if (along > 0) best = Math.min(best, Math.abs(-x * Math.sin(a) + z * Math.cos(a)));
        }
        return best;
    }

    /** Écart d'angle (radians) entre (x, z) et l'axe de l'espace des coffres. */
    private double ecart(double x, double z) {
        double d = Math.atan2(z, x) - Math.toRadians(angle());
        return Math.atan2(Math.sin(d), Math.cos(d));
    }

    /**
     * Esplanade des coffres : sol sombre bordé d'or, mur de verre courbe au fond (piliers entre les coffres,
     * lanterne au-dessus de chaque coffre), deux grands pylônes à l'entrée. Ne touche ni aux avenues ni à leurs lampadaires.
     */
    private void espace() {
        double r0 = rayon() - 10, r1 = rayon() + MUR - 1, pas = 7.0 / rayon(), demi = Math.toRadians(DEMI);
        int R = (int) Math.ceil(rayon() + MUR + 1);
        for (int x = -R; x <= R; x++) for (int z = -R; z <= R; z++) {
            double d = Math.sqrt((double) x * x + (double) z * z), e = ecart(x + 0.5, z + 0.5);
            if (d < r0 || d > r1 + 1 || Math.abs(e) > demi || distAvenue(x + 0.5, z + 0.5) < 8) continue;
            if (d > r1) {
                // Mur du fond : piliers entre les coffres, verre bleu, linteau doré, lanterne derrière chaque coffre.
                double k = e / pas + 2;                                   // 0..4 = coffres, 0.5.. = entre deux
                boolean pilier = Math.abs(k - Math.floor(k) - 0.5) * pas * d < 0.75 && k > -0.6 && k < 4.6;
                boolean derriere = Math.abs(k - Math.round(k)) * pas * d < 0.75 && k > -0.4 && k < 4.4;
                if (k < -0.6 || k > 4.6) continue;
                if (pilier) {
                    for (int y = Y + 1; y <= Y + 10; y++) air(x, y, z, y % 2 == 0 ? Material.GOLD_BLOCK : Material.LIGHT_BLUE_CONCRETE);
                    air(x, Y + 11, z, Material.SEA_LANTERN);
                    air(x, Y + 12, z, Material.END_ROD);
                } else {
                    air(x, Y + 1, z, Material.POLISHED_DEEPSLATE);
                    for (int y = Y + 2; y <= Y + 7; y++) air(x, y, z, Material.LIGHT_BLUE_STAINED_GLASS);
                    air(x, Y + 8, z, derriere ? Material.SEA_LANTERN : Material.GOLD_BLOCK);
                }
                sol(x, z, Material.GOLD_BLOCK);
                continue;
            }
            // Sol : damier sombre, bordure dorée, allée lumineuse entre les deux ailes, arc bleu lumineux en travers.
            double ax = Math.toRadians(angle());
            double al = (x + 0.5) * Math.cos(ax) + (z + 0.5) * Math.sin(ax), sd = -(x + 0.5) * Math.sin(ax) + (z + 0.5) * Math.cos(ax);
            Material m;
            if (d < r0 + 1 || Math.abs(e) > demi - 1.0 / d) m = Material.GOLD_BLOCK;
            else if (Math.abs(sd) < 0.8) m = Math.floorMod((int) Math.floor(al), 3) == 0 ? Material.SEA_LANTERN : Material.LIGHT_BLUE_CONCRETE;
            else if (Math.abs(sd) < 1.8) m = Material.GOLD_BLOCK;
            else if (Math.abs(d - (rayon() - 5)) < 0.5) m = Math.floorMod(x + z, 4) == 0 ? Material.SEA_LANTERN : Material.LIGHT_BLUE_CONCRETE;
            else m = (((x >> 1) + (z >> 1)) & 1) == 0 ? Material.POLISHED_DEEPSLATE : Material.DEEPSLATE_TILES;
            sol(x, z, m);
        }
        // Deux pylônes à l'entrée, de part et d'autre.
        for (int s = -1; s <= 1; s += 2) {
            double a = Math.toRadians(angle()) + s * Math.toRadians(DEMI - 6), r = r0 + 4;
            int px = (int) Math.floor(Math.cos(a) * r), pz = (int) Math.floor(Math.sin(a) * r);
            if (distAvenue(px + 0.5, pz + 0.5) < 8) continue;
            for (int y = Y + 1; y <= Y + 12; y++) air(px, y, pz, y % 3 == 0 ? Material.GOLD_BLOCK : Material.LIGHT_BLUE_CONCRETE);
            air(px, Y + 13, pz, Material.SEA_LANTERN);
            air(px, Y + 14, pz, Material.END_ROD);
        }
    }

    private void air(int x, int y, int z, Material m) {
        Block b = w.getBlockAt(x, y, z);
        if (retrait) {
            if (b.getType() == m) { b.setType(Material.AIR, false); poses++; }
            return;
        }
        if (!b.getType().isAir()) return;
        b.setType(m, false);
        poses++;
    }

    private void sol(int x, int z, Material m) {
        if (retrait) return;   // le sol des anciens dallages est remis à part
        Block b = w.getBlockAt(x, Y, z);
        if (b.getType().isAir() || !w.getBlockAt(x, Y + 1, z).getType().isAir()) return;
        b.setType(m, false);
        poses++;
    }

    private void poserCoffre(int x, int y, int z, Material m) {
        Block b = w.getBlockAt(x, y, z);
        if (retrait) {
            if (b.getType() != m) return;
            if (b.getState() instanceof org.bukkit.block.Container co) co.getInventory().clear();
            b.setType(Material.AIR, false);
            poses++;
            return;
        }
        if (!b.getType().isAir()) return;
        b.setType(m, false);
        if (b.getBlockData() instanceof Directional d) {
            d.setFacing(face);
            b.setBlockData(d, false);
        }
        poses++;
    }

    /** Dallage rond (rayon 2,5) au sol : bord / intérieur. */
    private void dalle(int cx, int cz, Material bord, Material dedans) {
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > 3.2) continue;
            sol(cx + dx, cz + dz, d > 2.4 ? bord : dedans);
        }
    }

    /** 1 — Cryo : glace bleue, piliers de verre givré, coffre sur une lanterne de mer. */
    private void cryo(int cx, int cz) {
        dalle(cx, cz, Material.LIGHT_BLUE_CONCRETE, Material.BLUE_ICE);
        air(cx, Y + 1, cz, Material.SEA_LANTERN);
        poserCoffre(cx, Y + 2, cz, Material.CHEST);
        for (int sx = -2; sx <= 2; sx += 4) for (int sz = -2; sz <= 2; sz += 4) {
            air(cx + sx, Y + 1, cz + sz, Material.PACKED_ICE);
            air(cx + sx, Y + 2, cz + sz, Material.LIGHT_BLUE_STAINED_GLASS);
            air(cx + sx, Y + 3, cz + sz, Material.LIGHT_BLUE_STAINED_GLASS);
            air(cx + sx, Y + 4, cz + sz, Material.END_ROD);
        }
        // Petits pics de glace entre les piliers.
        air(cx + 2, Y + 1, cz, Material.PACKED_ICE);
        air(cx - 2, Y + 1, cz, Material.PACKED_ICE);
        air(cx, Y + 1, cz + 2, Material.PACKED_ICE);
        air(cx, Y + 1, cz - 2, Material.PACKED_ICE);
    }

    /** 2 — Solaire : or et cuivre, coffre en cuivre surélevé, paratonnerres comme des rayons de soleil. */
    private void solaire(int cx, int cz) {
        dalle(cx, cz, Material.GOLD_BLOCK, Material.SMOOTH_SANDSTONE);
        air(cx, Y + 1, cz, Material.GOLD_BLOCK);
        air(cx, Y + 2, cz, Material.SHROOMLIGHT);
        poserCoffre(cx, Y + 3, cz, Material.WAXED_COPPER_CHEST);
        int[][] rayons = {{2, 0}, {-2, 0}, {0, 2}, {0, -2}, {2, 2}, {-2, 2}, {2, -2}, {-2, -2}};
        for (int[] r : rayons) {
            boolean diag = r[0] != 0 && r[1] != 0;
            air(cx + r[0], Y + 1, cz + r[1], diag ? Material.WAXED_CUT_COPPER : Material.GOLD_BLOCK);
            air(cx + r[0], Y + 2, cz + r[1], diag ? Material.LIGHTNING_ROD : Material.GLOWSTONE);
            if (!diag) air(cx + r[0], Y + 3, cz + r[1], Material.LIGHTNING_ROD);
        }
    }

    /** 3 — Nébuleuse : purpur et améthyste, coffre de l'End sur obsidienne pleureuse, arche violette. */
    private void nebuleuse(int cx, int cz) {
        dalle(cx, cz, Material.PURPUR_BLOCK, Material.AMETHYST_BLOCK);
        air(cx, Y + 1, cz, Material.CRYING_OBSIDIAN);
        poserCoffre(cx, Y + 2, cz, Material.ENDER_CHEST);
        int[][] cristaux = {{2, 1}, {-2, -1}, {1, -2}, {-1, 2}};
        for (int[] k : cristaux) {
            air(cx + k[0], Y + 1, cz + k[1], Material.AMETHYST_BLOCK);
            air(cx + k[0], Y + 2, cz + k[1], Material.AMETHYST_CLUSTER);
        }
        // Arche de verre violet au-dessus du coffre.
        for (int s = -2; s <= 2; s += 4) {
            for (int y = Y + 1; y <= Y + 4; y++) air(cx + s, y, cz + s, Material.PURPLE_STAINED_GLASS);
        }
        for (int t = -1; t <= 1; t++) air(cx + t, Y + 5, cz + t, Material.MAGENTA_STAINED_GLASS);
        air(cx, Y + 4, cz, Material.END_ROD);
    }

    /** 4 — Astéroïde : roche brute et minerais, coffre posé sur un bloc de pierre, verre vert. */
    private void asteroide(int cx, int cz) {
        dalle(cx, cz, Material.TUFF_BRICKS, Material.COBBLED_DEEPSLATE);
        air(cx, Y + 1, cz, Material.DEEPSLATE_EMERALD_ORE);
        poserCoffre(cx, Y + 2, cz, Material.CHEST);
        int[][] roches = {{2, 0, 2}, {-2, 1, 1}, {1, -2, 1}, {-1, 2, 2}, {2, -2, 1}, {-2, -1, 1}};
        Material[] minerais = {Material.DEEPSLATE_DIAMOND_ORE, Material.COBBLED_DEEPSLATE, Material.DEEPSLATE_GOLD_ORE,
                Material.MOSSY_COBBLESTONE, Material.DEEPSLATE_LAPIS_ORE, Material.TUFF};
        for (int i = 0; i < roches.length; i++) {
            int[] r = roches[i];
            for (int h = 1; h <= r[2]; h++) air(cx + r[0], Y + h, cz + r[1], h == r[2] ? minerais[i] : Material.COBBLED_DEEPSLATE);
        }
        air(cx + 2, Y + 3, cz, Material.LIME_STAINED_GLASS);
        air(cx - 1, Y + 3, cz + 2, Material.GREEN_STAINED_GLASS);
        air(cx - 2, Y + 2, cz + 1, Material.SEA_PICKLE);
    }

    /** 5 — Réacteur : blackstone, magma, cage de barreaux en cuivre et lampes rouges, coffre piégé. */
    private void reacteur(int cx, int cz) {
        dalle(cx, cz, Material.RED_CONCRETE, Material.POLISHED_BLACKSTONE);
        sol(cx + 1, cz, Material.MAGMA_BLOCK);
        sol(cx - 1, cz, Material.MAGMA_BLOCK);
        sol(cx, cz + 1, Material.MAGMA_BLOCK);
        sol(cx, cz - 1, Material.MAGMA_BLOCK);
        air(cx, Y + 1, cz, Material.POLISHED_BLACKSTONE_BRICKS);
        poserCoffre(cx, Y + 2, cz, Material.TRAPPED_CHEST);
        for (int sx = -2; sx <= 2; sx += 4) for (int sz = -2; sz <= 2; sz += 4) {
            air(cx + sx, Y + 1, cz + sz, Material.POLISHED_BLACKSTONE_BRICKS);
            air(cx + sx, Y + 2, cz + sz, Material.RED_STAINED_GLASS);
            air(cx + sx, Y + 3, cz + sz, Material.REDSTONE_BLOCK);
            air(cx + sx, Y + 4, cz + sz, Material.WAXED_COPPER_LANTERN);
        }
        for (int t = -1; t <= 1; t++) {
            air(cx + t, Y + 1, cz + 2, Material.WAXED_COPPER_BARS);
            air(cx + t, Y + 1, cz - 2, Material.WAXED_COPPER_BARS);
            air(cx + 2, Y + 1, cz + t, Material.WAXED_COPPER_BARS);
            air(cx - 2, Y + 1, cz + t, Material.WAXED_COPPER_BARS);
        }
    }

    /**
     * 6 — Ménagerie (pets) : petit jardin de mousse bordé de rose, coffre sur une botte de foin,
     * azalées en fleurs, pétales roses et 4 lanternes sur piquets.
     */
    private void menagerie(int cx, int cz) {
        dalle(cx, cz, Material.PINK_CONCRETE, Material.MOSS_BLOCK);
        air(cx, Y + 1, cz, Material.HAY_BLOCK);
        poserCoffre(cx, Y + 2, cz, Material.CHEST);
        for (int sx = -2; sx <= 2; sx += 4) for (int sz = -2; sz <= 2; sz += 4) {
            air(cx + sx, Y + 1, cz + sz, Material.OAK_FENCE);
            air(cx + sx, Y + 2, cz + sz, Material.OAK_FENCE);
            air(cx + sx, Y + 3, cz + sz, Material.LANTERN);
        }
        air(cx + 2, Y + 1, cz, Material.FLOWERING_AZALEA);
        air(cx - 2, Y + 1, cz, Material.FLOWERING_AZALEA);
        air(cx, Y + 1, cz + 2, Material.FLOWERING_AZALEA);
        air(cx, Y + 1, cz - 2, Material.FLOWERING_AZALEA);
        int[][] petales = {{1, 1}, {-1, -1}, {1, -1}, {-1, 1}};
        for (int[] p : petales) air(cx + p[0], Y + 1, cz + p[1], Material.PINK_PETALS);
    }
}
