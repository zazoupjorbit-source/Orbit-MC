package fr.orbit.arene;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Une partie de l'arène, dans sa propre zone du monde {@code arene} (une zone par jeu et par place). La classe
 * de base garde l'état des joueurs humains (rendu à la fin quoi qu'il arrive), les entités posées (retirées à la
 * fin) et les chunks chargés ; chaque jeu fournit sa construction, son tick et ses résultats.
 */
public abstract class Partie {

    public static final int Y0 = 100;

    protected final OrbitArene pl;
    public final Jeu.Mode mode;
    public final int slot;
    protected final World w;
    /** Coin de la zone de la partie (x, z) ; le sol de jeu est en Y0 - 1. */
    protected final int ox, oz;
    protected final Map<UUID, Etat> etats = new LinkedHashMap<>();
    private final List<Entity> entites = new ArrayList<>();
    private final List<Chunk> chunks = new ArrayList<>();
    protected int t;
    private boolean finie;

    protected Partie(OrbitArene pl, Jeu.Mode mode, int slot) {
        this.pl = pl;
        this.mode = mode;
        this.slot = slot;
        this.w = pl.monde();
        this.ox = mode.jeu().ordinal() * 1000;
        this.oz = slot * 400;
    }

    // ------------------------------------------------------------------ à fournir par chaque jeu

    /** Étendue de la zone (blocs, à partir de ox/oz) : {largeur x, longueur z}. */
    protected abstract int[] taille();

    /** Lance la partie (joueurs déjà préparés : aventure, inventaire vide). */
    protected abstract void demarrer(List<Player> joueurs);

    public abstract void tick();

    /** Le joueur s'en va en cours de partie : le sortir du jeu (le reste est fait par la base). */
    protected abstract void retirer(Player p);

    /** Retire tout ce que la partie a posé en plus des entités gardées (blocs temporaires…). */
    protected abstract void nettoyer();

    public void touche(Player p, Input in) {
    }

    public void clic(Player p) {
    }

    /** Vrai si le joueur doit rester assis (descente annulée). */
    public boolean assis(Player p) {
        return false;
    }

    // ------------------------------------------------------------------ cycle de vie

    final void lancer(List<Player> joueurs) {
        int[] s = taille();
        for (int cx = Math.floorDiv(ox - 8, 16); cx <= Math.floorDiv(ox + s[0] + 8, 16); cx++) {
            for (int cz = Math.floorDiv(oz - 8, 16); cz <= Math.floorDiv(oz + s[1] + 8, 16); cz++) {
                Chunk c = w.getChunkAt(cx, cz);
                c.addPluginChunkTicket(pl);
                chunks.add(c);
            }
        }
        for (Player p : joueurs) {
            etats.put(p.getUniqueId(), new Etat(p));
            Etat.preparer(p);
            pl.entre(p, this);
        }
        demarrer(joueurs);
    }

    public final boolean finie() {
        return finie;
    }

    public final boolean contient(Player p) {
        return etats.containsKey(p.getUniqueId());
    }

    public final int humains() {
        return etats.size();
    }

    /** Le joueur quitte (commande, déconnexion, téléporté ailleurs) : pas de récompense. */
    public final void abandon(Player p, boolean ramener) {
        if (!etats.containsKey(p.getUniqueId())) {
            return;
        }
        try {
            retirer(p);
        } catch (RuntimeException e) {
            pl.getLogger().warning(mode.cle() + " : retrait de " + p.getName() + " : " + e);
        }
        rendre(p, ramener);
        if (p.isOnline() && ramener) {
            Txt.send(p, "<#9AA4B2>Partie quittée.");
        }
        if (etats.isEmpty()) {
            finir();
        }
    }

    /** Fin d'un joueur avec résultat : état rendu, retour au PNJ, score enregistré, OC versés (plafond du jour). */
    protected final void resultat(Player p, double score, int oc, String titre, String resume) {
        if (!etats.containsKey(p.getUniqueId())) {
            return;
        }
        rendre(p, true);
        if (p.isOnline()) {
            Txt.title(p, titre, resume, 2600);
        }
        pl.scores().enregistrer(p, mode, score, oc, resume);
    }

    protected final void rendre(Player p, boolean ramener) {
        Etat e = etats.remove(p.getUniqueId());
        pl.sort(p);
        if (e == null || !p.isOnline()) {
            return;
        }
        if (p.isInsideVehicle()) {
            p.leaveVehicle();
        }
        e.rendre(p);
        if (ramener) {
            pl.teleporter(p, pl.salle().retour(mode.jeu()));
        }
    }

    /** Termine la partie : joueurs restants rendus sans résultat, entités retirées, place libérée. */
    protected final void finir() {
        if (finie) {
            return;
        }
        finie = true;
        for (UUID id : new ArrayList<>(etats.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                rendre(p, true);
            } else {
                etats.remove(id);
            }
        }
        try {
            nettoyer();
        } catch (RuntimeException ex) {
            pl.getLogger().warning(mode.cle() + " : nettoyage : " + ex);
        }
        for (Entity e : entites) {
            e.remove();
        }
        entites.clear();
        for (Chunk c : chunks) {
            c.removePluginChunkTicket(pl);
        }
        chunks.clear();
        pl.attente().liberer(this);
    }

    /** Arrêt forcé (plugin arrêté, erreur) : joueurs rendus sans résultat. */
    final void arreter() {
        finir();
    }

    // ------------------------------------------------------------------ aides

    protected final List<Player> joueursEnLigne() {
        List<Player> list = new ArrayList<>();
        for (UUID id : etats.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                list.add(p);
            }
        }
        return list;
    }

    protected final Location loc(double x, double y, double z) {
        return new Location(w, x, y, z);
    }

    protected final <T extends Entity> T garder(T e) {
        e.setPersistent(false);
        e.getPersistentDataContainer().set(pl.cle(), PersistentDataType.BYTE, (byte) 1);
        entites.add(e);
        return e;
    }

    protected final void oublier(Entity e) {
        if (e != null) {
            entites.remove(e);
            e.remove();
        }
    }

    protected final void teleporter(Player p, Location to) {
        pl.teleporter(p, to);
    }

    /** Bloc d'affichage centré sur {@code at} (taille sx × sy × sz, tourné de {@code rot}). */
    protected final BlockDisplay bloc(Location at, BlockData data, float sx, float sy, float sz, Quaternionf rot, int lissage) {
        return garder(w.spawn(at, BlockDisplay.class, d -> {
            d.setBlock(data);
            d.setTransformation(centre(sx, sy, sz, rot));
            d.setTeleportDuration(lissage);
            d.setBrightness(new Display.Brightness(15, 15));
        }));
    }

    protected final BlockDisplay bloc(Location at, Material m, float sx, float sy, float sz) {
        return bloc(at, m.createBlockData(), sx, sy, sz, new Quaternionf(), 1);
    }

    public static Transformation centre(float sx, float sy, float sz, Quaternionf rot) {
        Vector3f c = new Vector3f(sx / 2f, sy / 2f, sz / 2f);
        rot.transform(c);
        return new Transformation(c.negate(), rot, new Vector3f(sx, sy, sz), new Quaternionf());
    }

    /** « Boule » à facettes : trois cubes superposés (droit, tourné sur Y, tourné sur X). */
    protected final List<BlockDisplay> boule(Location at, Material a, Material b, float d, int lissage) {
        List<BlockDisplay> l = new ArrayList<>(3);
        float s = d * 0.8f;
        l.add(bloc(at, a.createBlockData(), s, s, s, new Quaternionf(), lissage));
        l.add(bloc(at, b.createBlockData(), s, s, s, new Quaternionf().rotateY((float) Math.PI / 4), lissage));
        l.add(bloc(at, b.createBlockData(), s, s, s, new Quaternionf().rotateX((float) Math.PI / 4), lissage));
        return l;
    }

    /** Fait tourner une boule (rotation ajoutée à gauche, interpolée par le client). */
    protected static void tourner(List<BlockDisplay> boule, Quaternionf spin, float d) {
        float s = d * 0.8f;
        Quaternionf[] base = {new Quaternionf(), new Quaternionf().rotateY((float) Math.PI / 4), new Quaternionf().rotateX((float) Math.PI / 4)};
        for (int i = 0; i < boule.size(); i++) {
            BlockDisplay bd = boule.get(i);
            bd.setInterpolationDelay(0);
            bd.setInterpolationDuration(2);
            bd.setTransformation(centre(s, s, s, new Quaternionf(spin).mul(base[i])));
        }
    }

    /** Support invisible sur lequel un joueur (ou un pantin) est assis et que l'on déplace chaque tick. */
    protected final ItemDisplay siege(Location at) {
        return garder(w.spawn(at, ItemDisplay.class, e -> e.setTeleportDuration(pl.lissage())));
    }

    protected final void asseoir(ItemDisplay seat, Entity passager) {
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (!finie && seat.isValid() && passager.isValid() && passager.getVehicle() == null) {
                seat.addPassenger(passager);
            }
        }, 2L);
    }

    /** Remet en selle un joueur descendu malgré tout (ex. après un lag). */
    protected final void garderAssis(ItemDisplay seat, Player p) {
        if (t > 6 && seat != null && seat.isValid() && p.isOnline() && p.getVehicle() == null) {
            seat.addPassenger(p);
        }
    }

    /** Bot : mannequin immobile, invulnérable et sans IA, déplacé par la partie ; habillé à sa couleur. */
    protected final Mannequin pantin(Location at, String nom, Color couleur, Consumer<Mannequin> extra) {
        return garder(w.spawn(at, Mannequin.class, m -> {
            m.setAI(false);
            m.setGravity(false);
            m.setInvulnerable(true);
            m.setSilent(true);
            m.setCollidable(false);
            m.setImmovable(true);
            m.setRemoveWhenFarAway(false);
            m.customName(Txt.mm("<#9AA4B2>[bot]</#9AA4B2> <" + hex(couleur) + ">" + nom));
            m.setCustomNameVisible(true);
            try {
                m.setDescription(null); // pas de sous-titre « NPC » sous le nom
            } catch (RuntimeException ignored) {
                m.setDescription(net.kyori.adventure.text.Component.empty());
            }
            m.getEquipment().setHelmet(cuir(Material.LEATHER_HELMET, couleur));
            m.getEquipment().setChestplate(cuir(Material.LEATHER_CHESTPLATE, couleur));
            m.getEquipment().setLeggings(cuir(Material.LEATHER_LEGGINGS, couleur));
            m.getEquipment().setBoots(cuir(Material.LEATHER_BOOTS, couleur));
            if (extra != null) {
                extra.accept(m);
            }
        }));
    }

    public static ItemStack cuir(Material m, Color c) {
        ItemStack it = new ItemStack(m);
        if (it.getItemMeta() instanceof LeatherArmorMeta meta) {
            meta.setColor(c);
            it.setItemMeta(meta);
        }
        return it;
    }

    public static String hex(Color c) {
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }

    /** Noms des bots (originaux, univers ORBIT). */
    protected static final String[] BOTS = {"Nova", "Quasar", "Pulsar", "Orion", "Véga", "Altaïr", "Sirius", "Lyra",
            "Comète", "Zénith", "Nadir", "Céleste", "Astra", "Photon", "Kepler", "Halo"};

    /** Couleurs de joueurs bien distinctes. */
    protected static final Color[] COULEURS = {Color.fromRGB(0x3A7FFF), Color.fromRGB(0xE0B864), Color.fromRGB(0xFF4FD8),
            Color.fromRGB(0x7CFF8A), Color.fromRGB(0xFF8A3D), Color.fromRGB(0x62CFF4), Color.fromRGB(0xC79BFF), Color.fromRGB(0xE0484A)};

    /** Article de la barre d'objets (icône du pack de l'arène si le joueur l'a chargé). */
    protected final ItemStack objet(Player p, Material m, String modele, String nom, String aide) {
        ItemStack it = new ItemStack(m);
        var meta = it.getItemMeta();
        meta.displayName(Txt.mm(nom));
        meta.lore(List.of(Txt.mm("<#9AA4B2>" + aide)));
        if (modele != null && pl.pack() != null && pl.pack().charge(p)) {
            meta.setItemModel(new org.bukkit.NamespacedKey("orbitarene", modele));
        }
        it.setItemMeta(meta);
        return it;
    }

    /** Barre « quitter » (dernière case). */
    protected final void boutonQuitter(Player p) {
        p.getInventory().setItem(8, objet(p, Material.BARRIER, "quitter", "<#E0484A><b>Quitter la partie</b>", "Clic droit : retour à la salle"));
    }

    public static boolean estQuitter(ItemStack it) {
        return it != null && it.getType() == Material.BARRIER;
    }

    protected final void sonTous(org.bukkit.Sound s, float vol, float pitch) {
        for (Player p : joueursEnLigne()) {
            p.playSound(p.getLocation(), s, vol, pitch);
        }
    }
}
