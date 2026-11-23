package fr.orbit.tycoon;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Allay;
import org.bukkit.entity.Axolotl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fox;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Shulker;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.profile.PlayerTextures;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Habitants des pavillons (dômes) du spawn :
 *  - un PNJ vendeur dans le dôme « Marché orbital » ;
 *  - un PNJ aventurier dans le dôme « Quêtes de la station » ;
 *  - un PNJ forgeron dans le dôme « Atelier des outils » ;
 *  - des animaux spatiaux dans le dôme « Observatoire » (ils ne peuvent pas en sortir).
 * Les entités ne sont jamais sauvegardées : elles sont recréées au chargement de leur chunk (comme la Boutique).
 * Les PNJ portent un skin du pack de ressources (orbittycoon:entity/pnj/<nom>, dessiné par tools/PnjSkins.java).
 */
final class Pnjs implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int Y = Zone.Y;
    static final String TAG = "orbit_pnj";
    private static final String TAG_VENDEUR = "orbit_pnj_vendeur", TAG_AVENTURIER = "orbit_pnj_aventurier",
            TAG_FORGERON = "orbit_pnj_forgeron";
    /** Rayon des pavillons autour du centre du spawn (voir Plans.base). */
    private static final double R_PAVILLON = 97;
    /** Index des pavillons (même ordre que Plans.PAVILLONS). */
    private static final int MARCHE = 0, ATELIER = 2, QUETES = 3, OBSERVATOIRE = 4;

    /** Une entité placée à un endroit fixe, recréée si elle disparaît. */
    private static final class Place {
        final Location loc;
        final Function<Location, Entity> creer;
        final boolean enclos;
        Entity entite;
        Place(Location loc, Function<Location, Entity> creer, boolean enclos) { this.loc = loc; this.creer = creer; this.enclos = enclos; }
        boolean vivante() { return entite != null && entite.isValid(); }
    }

    private final OrbitTycoon plugin;
    private final List<Place> places = new ArrayList<>();
    private Location centreObservatoire;
    private BukkitTask tache;

    Pnjs(OrbitTycoon plugin) { this.plugin = plugin; }

    // ------------------------------------------------------------------ outils

    /** Réglages communs d'un PNJ mannequin : immobile, invulnérable, skin du pack, nom visible. */
    static void preparer(Mannequin m, Location l, String skin, String nom) {
        m.setPersistent(false);
        m.setImmovable(true);
        m.setInvulnerable(true);
        m.setAI(false);
        m.setGravity(false);
        m.setSilent(true);
        m.setCustomNameVisible(true);
        m.customName(MM.deserialize(nom));
        m.setDescription(MM.deserialize("<gray>Clic droit"));
        m.setRotation(l.getYaw(), 0);
        m.setProfile(ResolvableProfile.resolvableProfile()
                .skinPatch(sp -> sp.body(Key.key("orbittycoon", "entity/pnj/" + skin)).model(PlayerTextures.SkinModel.CLASSIC))
                .build());
    }

    /** Centre (x, z) du pavillon i. */
    private static double[] pavillon(int i) {
        double a = Math.toRadians(Zone.farms().get(i).angle + 36);
        return new double[]{Math.round(Math.cos(a) * R_PAVILLON), Math.round(Math.sin(a) * R_PAVILLON), a};
    }

    /** Point du pavillon i, à « vers » blocs du pilier central en direction du centre du spawn, décalé de « cote ». */
    private Location dansPavillon(int i, double vers, double cote, double y) {
        double[] p = pavillon(i);
        double ox = -Math.cos(p[2]), oz = -Math.sin(p[2]);
        double x = p[0] + ox * vers - oz * cote, z = p[1] + oz * vers + ox * cote;
        return new Location(plugin.monde(), Math.floor(x) + 0.5, y, Math.floor(z) + 0.5, Zone.yaw(ox, oz), 0f);
    }

    /**
     * Point de téléportation d'un pavillon à PNJ (marchand, forgeron, quetes, observatoire) :
     * à 6 blocs du pilier côté entrée, tourné vers le PNJ. null si le nom est inconnu.
     */
    Location pointTp(String lieu) {
        int i = switch (lieu) {
            case "marchand" -> MARCHE;
            case "forgeron" -> ATELIER;
            case "quetes" -> QUETES;
            case "observatoire" -> OBSERVATOIRE;
            default -> -1;
        };
        if (i < 0) return null;
        Location l = dansPavillon(i, 6, 0, Y + 1);
        l.setYaw(l.getYaw() + 180f);
        return l;
    }

    // ------------------------------------------------------------------ cycle de vie

    void demarrer() {
        World w = plugin.monde();
        for (Entity e : w.getEntities()) if (e.getScoreboardTags().contains(TAG)) e.remove();
        places.clear();

        // Vendeur du marché et aventurier des quêtes, devant le pilier central, face à l'entrée.
        places.add(new Place(dansPavillon(MARCHE, 3, 0, Y + 1), l -> l.getWorld().spawn(l, Mannequin.class, m -> {
            preparer(m, l, "vendeur", "<#E0B864><bold>Marchand orbital</bold>");
            m.addScoreboardTag(TAG);
            m.addScoreboardTag(TAG_VENDEUR);
        }), false));
        places.add(new Place(dansPavillon(QUETES, 3, 0, Y + 1), l -> l.getWorld().spawn(l, Mannequin.class, m -> {
            preparer(m, l, "aventurier", "<#62CFF4><bold>Aventurier</bold>");
            m.addScoreboardTag(TAG);
            m.addScoreboardTag(TAG_AVENTURIER);
        }), false));
        places.add(new Place(dansPavillon(ATELIER, 3, 0, Y + 1), l -> l.getWorld().spawn(l, Mannequin.class, m -> {
            preparer(m, l, "forgeron", "<#E07B39><bold>Forgeron</bold>");
            m.getEquipment().setItemInMainHand(new org.bukkit.inventory.ItemStack(org.bukkit.Material.IRON_PICKAXE));
            m.addScoreboardTag(TAG);
            m.addScoreboardTag(TAG_FORGERON);
        }), false));
        for (Entity e : w.getEntities()) majTitre(e);

        // Animaux spatiaux de l'observatoire.
        centreObservatoire = dansPavillon(OBSERVATOIRE, 0, 0, Y + 1);
        double[][] pos = {{3, 2}, {3, -2}, {-3, 3}, {-3, -3}, {5, 0}, {0, 5}, {0, -5}, {-5, 0}};
        for (int k = 0; k < pos.length; k++) {
            final int type = k;
            boolean volant = type < 2;
            Location l = dansPavillon(OBSERVATOIRE, pos[k][0], pos[k][1], Y + (volant ? 4 : 1));
            places.add(new Place(l, loc -> animal(loc, type), true));
        }

        rafraichir();
        if (tache != null) tache.cancel();
        tache = Bukkit.getScheduler().runTaskTimer(plugin, this::garderDansLeDome, 40L, 40L);
    }

    void arreter() {
        if (tache != null) tache.cancel();
        for (Place p : places) if (p.vivante()) p.entite.remove();
    }

    /** Crée l'animal n° type : 2 lueurs stellaires (allays), 2 axolotls lunaires, 2 renards des comètes, 2 coquilles du vide. */
    private Entity animal(Location l, int type) {
        World w = l.getWorld();
        Entity e;
        if (type <= 1) {
            Allay a = w.spawn(l, Allay.class);
            nommer(a, "<#62CFF4>✦ Lueur stellaire");
            a.setGlowing(true);
            a.setCanDuplicate(false);
            e = a;
        } else if (type <= 3) {
            Axolotl a = w.spawn(l, Axolotl.class);
            nommer(a, "<#9FD8FF>☾ Axolotl lunaire");
            a.setVariant(Axolotl.Variant.BLUE);
            e = a;
        } else if (type <= 5) {
            Fox f = w.spawn(l, Fox.class);
            nommer(f, "<white>☄ Renard des comètes");
            f.setFoxType(Fox.Type.SNOW);
            e = f;
        } else {
            Shulker s = w.spawn(l, Shulker.class);
            nommer(s, "<#C77DFF>◆ Coquille du vide");
            s.setColor(DyeColor.LIGHT_BLUE);
            s.setAI(false);
            e = s;
        }
        e.addScoreboardTag(TAG);
        return e;
    }

    private static void nommer(LivingEntity e, String nom) {
        e.customName(MM.deserialize(nom));
        e.setCustomNameVisible(true);
        e.setPersistent(false);
        e.setInvulnerable(true);
        e.setSilent(false);
        e.setRemoveWhenFarAway(false);
        if (e instanceof Mob m) m.setTarget(null);
    }

    private void rafraichir() {
        for (Place p : places)
            if (!p.vivante() && p.loc.isChunkLoaded()) p.entite = p.creer.apply(p.loc);
    }

    /** Ramène dans le dôme un animal qui s'éloigne du centre de l'observatoire (dôme de rayon 9). */
    private void garderDansLeDome() {
        if (centreObservatoire == null || !centreObservatoire.isChunkLoaded()) return;
        for (Place p : places) {
            if (!p.enclos || !p.vivante()) continue;
            Location e = p.entite.getLocation();
            double dx = e.getX() - centreObservatoire.getX(), dz = e.getZ() - centreObservatoire.getZ();
            if (dx * dx + dz * dz > 6.5 * 6.5 || e.getY() < Y + 0.5 || e.getY() > Y + 8) p.entite.teleport(p.loc);
        }
    }

    /**
     * Les titres des pavillons sont des textes persistants posés par « /tycoon generer » : s'il en reste un avec
     * l'ancien « Bientôt », on le remplace par le titre actuel (Plans.PAVILLONS) sans régénérer le spawn.
     */
    private void majTitre(Entity e) {
        if (!(e instanceof org.bukkit.entity.TextDisplay t)) return;
        String texte = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(t.text());
        if (!texte.contains("Bientôt")) return;
        for (int i = 0; i < Plans.PAVILLONS.length; i++) {
            if (Plans.PAVILLONS[i] == null) continue;
            double[] p = pavillon(i);
            double dx = e.getX() - (p[0] + 0.5), dz = e.getZ() - (p[1] + 0.5);
            if (dx * dx + dz * dz < 4) { t.text(MM.deserialize(Plans.PAVILLONS[i])); return; }
        }
    }

    // ------------------------------------------------------------------ événements

    @EventHandler
    public void entites(org.bukkit.event.world.EntitiesLoadEvent e) {
        if (!e.getWorld().equals(plugin.monde())) return;
        for (Entity en : e.getEntities()) majTitre(en);
    }

    @EventHandler
    public void chunk(ChunkLoadEvent e) {
        if (!e.getWorld().equals(plugin.monde())) return;
        for (Place p : places)
            if (p.loc.getBlockX() >> 4 == e.getChunk().getX() && p.loc.getBlockZ() >> 4 == e.getChunk().getZ()) {
                Bukkit.getScheduler().runTask(plugin, this::rafraichir);
                return;
            }
    }

    @EventHandler
    public void degats(EntityDamageEvent e) {
        if (e.getEntity().getScoreboardTags().contains(TAG)) e.setCancelled(true);
    }

    @EventHandler
    public void cible(EntityTargetEvent e) {
        if (e.getEntity().getScoreboardTags().contains(TAG)) e.setCancelled(true);
    }

    @EventHandler
    public void clic(PlayerInteractEntityEvent e) {
        Entity c = e.getRightClicked();
        if (!c.getScoreboardTags().contains(TAG)) return;
        e.setCancelled(true);   // pas de seau à axolotl, pas d'objet donné aux lueurs…
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        if (c.getScoreboardTags().contains(TAG_VENDEUR)) {
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_TRADE, 0.8f, 1.1f);
            plugin.hdv().ouvrir(p, 0);
        } else if (c.getScoreboardTags().contains(TAG_AVENTURIER)) {
            int pretes = plugin.quetes().pretes(p.getUniqueId());
            if (pretes > 0) OrbitTycoon.msg(p, "<#62CFF4>Aventurier</#62CFF4> <dark_gray>»</dark_gray> <gray>Bravo, pilote ! "
                    + "<#E0B864>" + pretes + " quête" + (pretes > 1 ? "s" : "") + "</#E0B864> t'attend" + (pretes > 1 ? "ent" : "") + ".");
            p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 0.9f);
            plugin.quetes().ouvrir(p);
        } else if (c.getScoreboardTags().contains(TAG_FORGERON)) {
            p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.6f, 1.2f);
            Outil.Mode m = plugin.outil().modeActuel(p);
            if (m != null) plugin.progression().ouvrir(p, m);
            else OrbitTycoon.msg(p, "<#E07B39>Forgeron</#E07B39> <dark_gray>»</dark_gray> <gray>Reviens avec ton Outil orbital !");
        }
    }

    @EventHandler
    public void clicPrecis(PlayerInteractAtEntityEvent e) {
        if (e.getRightClicked().getScoreboardTags().contains(TAG)) e.setCancelled(true);
    }
}
