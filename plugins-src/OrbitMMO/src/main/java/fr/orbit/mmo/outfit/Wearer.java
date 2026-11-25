package fr.orbit.mmo.outfit;

import io.papermc.paper.datacomponent.DataComponentTypes;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Les pièces 3D d'un joueur en tenue : un ItemDisplay par pièce (épaulières, cape).
 * Mode « paquets » : le client du joueur et de ceux qui le voient croient les pièces assises sur lui, donc il les
 * place lui-même à chaque image ; le serveur ne fait que tourner les pièces avec le corps (transformation
 * interpolée) et garder leur position serveur près du joueur (suivi des entités).
 * Mode de secours (paquets indisponibles) : pièces téléportées chaque tick, avec 2 ticks d'interpolation.
 * Repère des modèles : pivot = cou (haut du torse), avant +Z, gauche +X, 1 unité = 1 pixel du modèle du joueur.
 */
final class Wearer {

    /** Le jeu tourne le modèle d'un ItemDisplay de 180° autour de Y : on le remet face à l'avant (+Z). */
    private static final Quaternionf ITEM_FLIP = new Quaternionf().rotationY((float) Math.PI);
    /** Le modèle du joueur est dessiné à 15/16 : 1 pixel du modèle = 0,9375/16 bloc. */
    private static final float PX = 0.9375f / 16f;
    /** Hauteur du cou au-dessus des pieds, debout et accroupi (rendu du jeu, en blocs, échelle 1). */
    private static final double NECK = 24 * PX, NECK_SNEAK = 24 * PX - 0.3125;
    /** Penchée du torse accroupi (HumanoidModel : body.xRot = 0,5). */
    private static final float SNEAK_TILT = 0.5f;
    /** Charnière de la cape : derrière le cou, sur le dos du plastron (pixels du modèle). */
    private static final Vector3f CAPE_HINGE = new Vector3f(0, -0.2f, -3.7f);
    /**
     * Hauteur (blocs) de la rehausse des pièces du porteur. La boîte de masquage (1,02 bloc de côté une fois gonflée)
     * commence alors 1,18 bloc au-dessus de ses yeux. À la 1re personne, champ de 70 : hors champ tant qu'il ne lève
     * pas le regard de plus de 24° (regard en diagonale des axes du monde) à 32° (dans l'axe) ; au-delà, les épaules
     * sont sorties de l'écran par le bas. Champ de 90 : 14° à 22° ; champ de 110 : 4° à 12° (les pièces se voient).
     * À la 3e personne : dans le champ tant que la caméra reste à plus de 1,2 bloc (regard à plat), 1,6 bloc (15°
     * vers le bas) ou 2,2 blocs (vue plongeante) ; plus près (mur dans le dos, plafond bas), le porteur les perd.
     * Plus haut = mieux caché à la 1re personne, mais perdu plus tôt à la 3e. Le serveur ne connaît pas le champ.
     */
    private static final float RAISE = 1.5f;
    /** Côté de la boîte de masquage des pièces du porteur (le client la gonfle de 0,5 bloc). */
    private static final float CULL = 0.02f;

    final OutfitService service;
    final Player player;
    final OutfitDef def;
    /** Les pièces de la tenue réellement posées (selon {@link OutfitService#pieces()}), dans l'ordre des listes. */
    final List<OutfitDef.Part> parts;
    /** Pièces vues par les AUTRES joueurs : assises directement sur le porteur, toujours cachées au porteur. */
    final List<ItemDisplay> displays = new ArrayList<>();
    /**
     * Pièces vues par le porteur SEUL. Assises sur sa tête, elles lui barreraient la vue à la 1re personne, et le
     * serveur ne sait pas s'il est en F5. Mais le client ne dessine une pièce « Display » qui a une largeur et une
     * hauteur que si la boîte d'un bloc autour de son point d'attache est dans le champ (EntityRenderer.shouldRender :
     * boîte de masquage gonflée de 0,5). On attache donc ces pièces {@link #RAISE} blocs au-dessus de la tête (sur
     * une Interaction invisible, {@link #seat}) et la transformation les redescend sur les épaules : la boîte est hors
     * champ à la 1re personne (au-dessus de la caméra) et dans le champ à la 3e (la caméra regarde la tête de loin).
     * Sans ça pour les autres joueurs : vus de près, la tête hors de l'écran, les pièces disparaîtraient.
     */
    final List<ItemDisplay> own = new ArrayList<>();
    /** Rehausse des pièces du porteur (mode paquets seulement) : assise sur lui, elle porte {@link #own}. */
    Interaction seat;
    long syncAt;
    private float lastYaw = Float.NaN;
    private float lastCape = Float.NaN;
    private String lastState = "";
    private float cape = 0.1f;
    private double lx, ly, lz;
    private boolean first = true;
    /** Angle du torse recalculé comme le fait le client (voir {@link #body}). */
    private float bodyYaw = Float.NaN;
    /** Ticks restants du geste du bras en cours : pendant ce temps le client tourne le torse vers la tête. */
    int swing;
    /** Lumière imposée aux pièces du porteur (bloc × 16 + ciel), -1 = pas encore posée. Voir {@link #light}. */
    private int lastLight = -1;

    Wearer(OutfitService service, Player player, OutfitDef def, List<OutfitDef.Part> parts) {
        this.service = service;
        this.player = player;
        this.def = def;
        this.parts = parts;
        Location at = anchor();
        boolean packets = service.packets();
        boolean showOwn = !service.hidesSelf(player);
        for (OutfitDef.Part part : parts) {
            ItemDisplay d = spawn(at, part, packets, false);
            displays.add(d);
            player.hideEntity(service.plugin, d);
            // Position serveur = là où le client l'assoit (rehausse comprise) : un recalage ne la déplace pas.
            ItemDisplay mine = spawn(at.clone().add(0, RAISE, 0), part, packets, true);
            own.add(mine);
            if (showOwn) {
                player.showEntity(service.plugin, mine);
            }
        }
        if (packets) {
            seat = at.getWorld().spawn(at, Interaction.class, e -> {
                e.setPersistent(false);
                e.setVisibleByDefault(false);
                e.setInteractionWidth(0f); // pas de volume : rien à viser ni à heurter
                e.setInteractionHeight(RAISE); // ses passagers sont assis à cette hauteur
                e.setResponsive(false);
                e.setRotation(0f, 0f);
                e.addScoreboardTag(OutfitService.TAG);
            });
            if (showOwn) {
                player.showEntity(service.plugin, seat);
            }
        }
    }

    private ItemDisplay spawn(Location at, OutfitDef.Part part, boolean packets, boolean mine) {
        return at.getWorld().spawn(at, ItemDisplay.class, e -> {
            e.setPersistent(false);
            ItemStack it = new ItemStack(Material.PAPER);
            it.setData(DataComponentTypes.ITEM_MODEL, part.model());
            e.setItemStack(it);
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            e.setBillboard(Display.Billboard.FIXED);
            e.setViewRange(0.75f); // 48 blocs : au-delà, le client ne les dessine plus
            e.setShadowRadius(0f);
            e.setInterpolationDelay(0);
            e.setInterpolationDuration(part.kind() == OutfitDef.Kind.CAPE ? 3 : 2);
            // 1 tick en mode paquets : un recalage de la position serveur est alors appliqué par le client dans
            // son tick, juste avant qu'il rasseye la pièce sur le joueur (à 0 il la déplaçait tout de suite et
            // elle pouvait sauter le temps d'une image).
            e.setTeleportDuration(packets ? 1 : 2);
            e.setRotation(0f, 0f);
            e.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0), ITEM_FLIP));
            e.addScoreboardTag(OutfitService.TAG);
            if (mine) {
                e.setVisibleByDefault(false);
                // Largeur et hauteur non nulles : le client masque alors la pièce quand sa boîte sort du champ.
                e.setDisplayWidth(CULL);
                e.setDisplayHeight(CULL);
            }
        });
    }

    /** Point où le client assoit les passagers d'un joueur : le haut de sa boîte (1,8 debout, 1,5 accroupi). */
    Location anchor() {
        Location at = player.getLocation().add(0, player.getHeight(), 0);
        // Surtout pas le regard du joueur : une pièce téléportée avec son lacet et son tangage est tournée une
        // seconde fois par le jeu (elle penche et quitte les épaules). Seule la transformation l'oriente.
        at.setYaw(0f);
        at.setPitch(0f);
        return at;
    }

    /**
     * Angle du torse tel que le CLIENT le dessine. Le serveur ne le connaît pas : pour un joueur il ne fait que
     * traîner le torse derrière la tête (à 50° près), alors que le client l'aligne sur le déplacement. On refait donc
     * le calcul du client (LivingEntity.tick puis tickHeadTurn) avec les déplacements vus par le serveur.
     */
    private float body(float head, double dx, double dz, boolean jumped) {
        Entity vehicle = player.getVehicle();
        if (vehicle instanceof AbstractHorse horse) {
            bodyYaw = horse.getBodyYaw(); // le cheval impose son torse à son cavalier
            return bodyYaw;
        }
        if (vehicle instanceof Boat) {
            bodyYaw = vehicle.getLocation().getYaw();
            return bodyYaw;
        }
        if (Float.isNaN(bodyYaw) || jumped) {
            bodyYaw = player.getBodyYaw();
        }
        float target = bodyYaw;
        if (!jumped && dx * dx + dz * dz > 0.0025) {
            float dir = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
            float diff = Math.abs(wrap(head) - dir);
            target = diff > 95f && diff < 265f ? dir - 180f : dir; // à reculons : le torse reste face à la marche arrière
        }
        if (swing > 0) {
            swing--;
            target = head;
        }
        bodyYaw += wrap(target - bodyYaw) * 0.3f;
        float d = wrap(head - bodyYaw);
        if (Math.abs(d) > 50f) {
            bodyYaw += d - Math.signum(d) * 50f;
        }
        return bodyYaw;
    }

    /** Toutes les entités de la tenue : pièces des autres, pièces du porteur, rehausse. */
    List<Entity> all() {
        List<Entity> out = new ArrayList<>(displays);
        out.addAll(mine());
        return out;
    }

    /** Ce que seul le porteur voit : ses pièces et leur rehausse. */
    List<Entity> mine() {
        List<Entity> out = new ArrayList<>(own);
        if (seat != null) {
            out.add(seat);
        }
        return out;
    }

    boolean valid() {
        for (Entity d : all()) {
            if (!d.isValid() || d.getWorld() != player.getWorld()) {
                return false;
            }
        }
        return true;
    }

    void remove() {
        for (Entity d : all()) {
            d.remove();
        }
        displays.clear();
        own.clear();
        seat = null;
    }

    /** Pièces vues par les autres joueurs (passagers du porteur chez eux). */
    int[] ids() {
        return ids(displays);
    }

    /** Pièces du porteur (passagers de la rehausse chez lui). */
    int[] ownIds() {
        return ids(own);
    }

    private static int[] ids(List<ItemDisplay> list) {
        int[] out = new int[list.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = list.get(i).getEntityId();
        }
        return out;
    }

    /** Garde la position serveur d'une entité près du joueur (suivi) ; sans paquets, la téléporte à chaque tick. */
    private void follow(Entity e, Location to, boolean packets, long tick) {
        if (!packets) {
            e.teleport(to);
            return;
        }
        Location at = e.getLocation();
        if (at.distanceSquared(to) > 64 || at.getYaw() != 0f || at.getPitch() != 0f) {
            // Le client, lui, assoit la pièce sur le joueur : ce recalage ne sert qu'au suivi des entités.
            e.teleport(to);
            syncAt = Math.min(syncAt, tick + 1);
        }
    }

    // ------------------------------------------------------------------ Tick

    void tick(long tick) {
        Location loc = player.getLocation();
        Location anchor = anchor();
        boolean packets = service.packets();
        for (ItemDisplay d : displays) {
            follow(d, anchor, packets, tick);
        }
        // Pièces du porteur : RAISE blocs plus haut, là où le client les assoit (sur la rehausse) ; sans paquets,
        // pas de rehausse à chevaucher, elles y sont téléportées à chaque tick.
        Location high = anchor.clone().add(0, RAISE, 0);
        for (ItemDisplay d : own) {
            follow(d, high, packets, tick);
        }
        if (seat != null) {
            follow(seat, anchor, packets, tick);
        }
        light();

        Pose pose = player.getPose();
        boolean sneak = pose == Pose.SNEAKING;
        boolean posed = (pose == Pose.STANDING || sneak) && !player.isGliding() && !player.isSwimming()
                && !player.isSleeping() && !player.isRiptiding();
        double s = scale();
        double dx = first ? 0 : loc.getX() - lx, dy = first ? 0 : loc.getY() - ly, dz = first ? 0 : loc.getZ() - lz;
        first = false;
        lx = loc.getX();
        ly = loc.getY();
        lz = loc.getZ();
        double horiz = Math.hypot(dx, dz);
        float yaw = body(loc.getYaw(), dx, dz, horiz > 2 || Math.abs(dy) > 2);
        double yr = Math.toRadians(yaw);

        // Cape : se soulève en avançant, un peu de côté, en tombant ; suit le dos penché quand on est accroupi.
        double target;
        if (horiz > 2 || Math.abs(dy) > 2) {
            target = 0.1; // téléportation
            cape = 0.1f;
        } else {
            double fwd = dx * -Math.sin(yr) + dz * Math.cos(yr);
            double lat = dx * Math.cos(yr) + dz * Math.sin(yr);
            target = 0.1 + clamp(fwd * 2.6 / s, 0, 0.95) + clamp(Math.abs(lat) * 1.2 / s, 0, 0.3)
                    + (dy < -0.12 ? clamp(-dy * 1.5, 0, 0.45) : 0);
            if (sneak) {
                target = Math.max(target + 0.1, SNEAK_TILT + 0.12);
            }
        }
        cape += (float) ((target - cape) * 0.3);
        float sway = (float) (Math.sin(tick * 0.3) * 0.025 * Math.min(1, horiz * 4));

        String state = posed + "/" + sneak + "/" + Math.round(s * 1000);
        boolean changed = !state.equals(lastState) || Float.isNaN(lastYaw) || Math.abs(wrap(yaw - lastYaw)) > 0.3f
                || Math.abs(cape - lastCape) > 0.004f || sway != 0f;
        if (!changed && packets) {
            return;
        }
        lastState = state;
        lastYaw = yaw;
        lastCape = cape;

        float px = (float) (PX * s);
        float neck = (float) ((sneak ? NECK_SNEAK : NECK) * s - player.getHeight());
        Quaternionf base = new Quaternionf().rotationY((float) -yr);
        Quaternionf torso = new Quaternionf(base);
        if (sneak) {
            torso.rotateX(SNEAK_TILT);
        }
        Vector3f size = new Vector3f(posed ? px * 16 : 0f);
        for (int i = 0; i < displays.size(); i++) {
            OutfitDef.Part part = parts.get(i);
            Vector3f off;
            Quaternionf rot;
            if (part.kind() == OutfitDef.Kind.CAPE) {
                off = torso.transform(new Vector3f(CAPE_HINGE).mul(px));
                rot = new Quaternionf(base).rotateX(cape).rotateZ(sway);
            } else {
                off = new Vector3f();
                rot = torso;
            }
            off.add(0, neck, 0);
            ItemDisplay d = displays.get(i);
            d.setInterpolationDelay(0);
            d.setTransformation(new Transformation(off, rot, size, ITEM_FLIP));
            // La même pièce pour le porteur : attachée RAISE blocs plus haut, donc redescendue d'autant.
            ItemDisplay mine = own.get(i);
            mine.setInterpolationDelay(0);
            mine.setTransformation(new Transformation(new Vector3f(off).sub(0, RAISE, 0), rot, size, ITEM_FLIP));
        }
    }

    /**
     * Le client éclaire une pièce d'après le bloc où elle est attachée. Celles du porteur sont attachées
     * {@link #RAISE} blocs au-dessus de sa tête, souvent DANS le plafond (lumière 0 : elles seraient noires).
     * On leur impose donc la lumière du bloc où sont ses yeux, celle qui éclaire son propre corps.
     */
    private void light() {
        Block eyes = player.getEyeLocation().getBlock();
        int light = eyes.getLightFromBlocks() << 4 | eyes.getLightFromSky();
        if (light == lastLight) {
            return;
        }
        lastLight = light;
        Display.Brightness b = new Display.Brightness(light >> 4, light & 15);
        for (ItemDisplay d : own) {
            d.setBrightness(b);
        }
    }

    private double scale() {
        AttributeInstance a = player.getAttribute(org.bukkit.attribute.Attribute.SCALE);
        double v = a == null ? 1 : a.getValue();
        return v > 0 ? v : 1;
    }

    float cape() {
        return cape;
    }

    /** Angle du torse recalculé (celui qui oriente les pièces), pour /mmoadmin tenue info. */
    float bodyAngle() {
        return bodyYaw;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static float wrap(float a) {
        a %= 360f;
        if (a >= 180f) {
            a -= 360f;
        } else if (a < -180f) {
            a += 360f;
        }
        return a;
    }
}
