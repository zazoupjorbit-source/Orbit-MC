package fr.orbit.tycoon;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Animation d'ouverture d'un coffre du spawn (environ 5 s) :
 * le couvercle s'ouvre, un objet monte au-dessus du coffre en tournant et fait défiler les récompenses possibles
 * (de plus en plus lentement, avec un « tic » qui monte dans les aigus), puis s'arrête sur le gain :
 * grossissement, nom affiché et effets selon la rareté (0 = commun … 3 = légendaire).
 * Visible seulement par le joueur qui ouvre (objets cachés aux autres, sons et particules envoyés à lui seul).
 * La récompense est donnée au moment de la révélation (ou tout de suite si le joueur se déconnecte / le plugin s'arrête).
 */
final class AnimationCoffre {

    /** Une case du défilement : l'objet montré et son nom. */
    record Carte(ItemStack objet, Component nom) {}

    static final String TAG = "orbit_coffres";
    /** Délais (ticks) entre deux cartes : de plus en plus lents, la dernière carte est le gain. */
    private static final int[] DELAIS = {2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 5, 6, 7, 9, 12, 15};
    /** Ticks pendant lesquels le gain reste affiché après la révélation. */
    private static final int APRES = 50;

    private final OrbitTycoon plugin;
    private final Map<UUID, Anim> actives = new HashMap<>();

    AnimationCoffre(OrbitTycoon plugin) {
        this.plugin = plugin;
    }

    private static final class Anim {
        BukkitTask tache;
        ItemDisplay objet;
        TextDisplay texte;
        Block coffre;
        Runnable recompense;
        boolean donne;
    }

    boolean enCours(UUID id) { return actives.containsKey(id); }

    /**
     * Lance l'animation. {@code defilement} : cartes tirées au hasard pendant le défilement ;
     * {@code gain} : la carte finale ; {@code niveau} 0 à 3 : intensité des effets ; {@code recompense} : donnée à la révélation.
     */
    void lancer(Player p, Block coffre, Color couleur, List<Carte> defilement, Carte gain, int niveau, Runnable recompense) {
        UUID id = p.getUniqueId();
        terminer(id);
        World w = coffre.getWorld();
        Location base = coffre.getLocation().add(0.5, 1.0, 0.5);
        Anim a = new Anim();
        a.coffre = coffre;
        a.recompense = recompense;
        p.playSound(base, Sound.BLOCK_CHEST_OPEN, 0.9f, 0.9f);
        p.playSound(base, Sound.BLOCK_BEACON_POWER_SELECT, 0.6f, 1.6f);

        a.objet = w.spawn(base, ItemDisplay.class, d -> {
            d.setItemStack(defilement.isEmpty() ? gain.objet() : defilement.getFirst().objet());
            d.setPersistent(false);
            d.setVisibleByDefault(false);
            d.addScoreboardTag(TAG);
            d.setBillboard(Display.Billboard.FIXED);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTeleportDuration(1);
            d.setInterpolationDuration(1);
            d.setGlowing(true);
            d.setGlowColorOverride(couleur);
            d.setTransformation(transfo(0, 0.2f));
        });
        a.texte = w.spawn(base.clone().add(0, 1.0, 0), TextDisplay.class, d -> {
            d.text(defilement.isEmpty() ? gain.nom() : defilement.getFirst().nom());
            d.setPersistent(false);
            d.setVisibleByDefault(false);
            d.addScoreboardTag(TAG);
            d.setBillboard(Display.Billboard.CENTER);
            d.setShadowed(true);
            d.setBackgroundColor(Color.fromARGB(150, 8, 16, 32));
            d.setTeleportDuration(1);
        });
        p.showEntity(plugin, a.objet);
        p.showEntity(plugin, a.texte);
        actives.put(id, a);

        // Moments (ticks) où la carte change ; la dernière = le gain.
        int[] changes = new int[DELAIS.length];
        int t0 = 10;
        for (int i = 0; i < DELAIS.length; i++) { t0 += DELAIS[i]; changes[i] = t0; }
        int revelation = changes[changes.length - 1], fin = revelation + APRES;
        Particle.DustOptions poussiere = new Particle.DustOptions(couleur, 1.1f);

        a.tache = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int t, carte, derniere = -1;
            float angle;

            @Override public void run() {
                t++;
                if (!a.objet.isValid() || !a.texte.isValid()) { terminer(id); return; }
                boolean revele = t >= revelation;
                // Montée (0,8 bloc pendant 20 ticks) puis léger flottement.
                double y = Math.min(1, t / 20.0) * 0.8 + Math.sin(t * 0.15) * 0.05;
                a.objet.teleport(base.clone().add(0, y, 0));
                a.texte.teleport(base.clone().add(0, y + 0.85, 0));
                // Rotation : rapide pendant le défilement, ralentit, lente après la révélation.
                float vitesse = revele ? 0.06f : 0.45f * Math.max(0.25f, 1f - t / (float) revelation);
                angle += vitesse;
                float echelle = revele ? Math.min(0.9f, 0.6f + (t - revelation) * 0.06f) : Math.min(0.6f, 0.2f + t * 0.03f);
                a.objet.setInterpolationDelay(0);
                a.objet.setTransformation(transfo(angle, echelle));

                // Spirale de poussière aux couleurs du coffre.
                if (!revele && t % 2 == 0) {
                    for (int k = 0; k < 2; k++) {
                        double ang = t * 0.35 + k * Math.PI;
                        p.spawnParticle(Particle.DUST, base.clone().add(Math.cos(ang) * 0.75, y * 0.8, Math.sin(ang) * 0.75),
                                1, 0, 0, 0, 0, poussiere);
                    }
                }

                // Changement de carte.
                if (carte < changes.length && t >= changes[carte]) {
                    boolean derniereCarte = carte == changes.length - 1;
                    Carte c = derniereCarte || defilement.isEmpty() ? gain : choisir(defilement, derniere);
                    if (!derniereCarte && !defilement.isEmpty()) derniere = defilement.indexOf(c);
                    a.objet.setItemStack(c.objet());
                    a.texte.text(c.nom());
                    if (!derniereCarte)
                        p.playSound(base, Sound.BLOCK_NOTE_BLOCK_HAT, 0.7f, 0.7f + 1.1f * carte / changes.length);
                    carte++;
                    if (derniereCarte) reveler(p, a, base.clone().add(0, y, 0), niveau, couleur);
                }

                if (t >= fin) terminer(id);
            }
        }, 1L, 1L);
    }

    private static Carte choisir(List<Carte> l, int eviter) {
        if (l.size() == 1) return l.getFirst();
        int i;
        do { i = ThreadLocalRandom.current().nextInt(l.size()); } while (i == eviter);
        return l.get(i);
    }

    private static Transformation transfo(float angle, float echelle) {
        return new Transformation(new Vector3f(), new AxisAngle4f(angle, 0, 1, 0),
                new Vector3f(echelle, echelle, echelle), new AxisAngle4f());
    }

    /** Révélation : effets selon le niveau, puis la récompense. */
    private void reveler(Player p, Anim a, Location loc, int niveau, Color couleur) {
        World w = loc.getWorld();
        p.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
        p.spawnParticle(Particle.FIREWORK, loc, 25, 0.2, 0.2, 0.2, 0.12);
        switch (niveau) {
            case 1 -> {
                p.spawnParticle(Particle.GLOW, loc, 30, 0.4, 0.4, 0.4, 0.05);
                p.playSound(loc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 0.8f);
            }
            case 2 -> {
                p.spawnParticle(Particle.WITCH, loc, 50, 0.5, 0.5, 0.5, 0.1);
                p.spawnParticle(Particle.END_ROD, loc, 20, 0.3, 0.3, 0.3, 0.08);
                p.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1f);
                p.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f);
            }
            case 3 -> {
                p.spawnParticle(Particle.TOTEM_OF_UNDYING, loc, 120, 0.4, 0.6, 0.4, 0.45);
                p.spawnParticle(Particle.END_ROD, loc, 40, 0.5, 0.8, 0.5, 0.12);
                p.spawnParticle(Particle.END_ROD, a.coffre.getLocation().add(0.5, 1, 0.5), 60, 0.1, 2.5, 0.1, 0.02);
                p.playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                p.playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 1f, 1f);
            }
            default -> p.spawnParticle(Particle.HAPPY_VILLAGER, loc, 15, 0.3, 0.3, 0.3, 0);
        }
        if (niveau >= 2) a.objet.setGlowColorOverride(niveau == 3 ? Color.fromRGB(0xE0B864) : Color.fromRGB(0xC77DFF));
        donner(a);
    }

    private static void donner(Anim a) {
        if (a.donne) return;
        a.donne = true;
        a.recompense.run();
    }

    /** Termine l'animation du joueur (s'il en a une) : récompense donnée si ce n'est pas fait, entités retirées. */
    void terminer(UUID id) {
        Anim a = actives.remove(id);
        if (a == null) return;
        if (a.tache != null) a.tache.cancel();
        if (a.objet != null) a.objet.remove();
        if (a.texte != null) a.texte.remove();
        donner(a);
    }

    void toutTerminer() {
        for (UUID id : List.copyOf(actives.keySet())) terminer(id);
    }
}
