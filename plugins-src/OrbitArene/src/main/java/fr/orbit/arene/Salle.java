package fr.orbit.arene;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PNJ de l'arène dans la salle d'arcade (monde {@code casino} d'OrbitCasino) : x = 6.5 / 10.5 / 14.5 / 18.5,
 * y 64, z 411.5, regard vers le nord (contrat docs/arcade-jeux.md). Posés dès que le monde existe.
 */
public final class Salle {

    static final double Y = 64, Z = 411.5;

    private final OrbitArene pl;
    private final Map<UUID, Jeu> pnj = new java.util.HashMap<>();
    private final List<Entity> poses = new ArrayList<>();
    private BukkitTask attente;

    Salle(OrbitArene pl) {
        this.pl = pl;
    }

    String nomMonde() {
        return pl.getConfig().getString("salle.monde", "casino");
    }

    World monde() {
        return Bukkit.getWorld(nomMonde());
    }

    /** Devant le PNJ du jeu, tourné vers lui. */
    public Location retour(Jeu j) {
        World w = monde();
        if (w == null) {
            w = Bukkit.getWorlds().get(0);
            return w.getSpawnLocation();
        }
        return new Location(w, j.npcX, Y, Z - 1.6, 0f, 0f);
    }

    /** OrbitCasino crée le monde de la salle un tick après son démarrage : on attend qu'il existe. */
    void attendre() {
        attente = Bukkit.getScheduler().runTaskTimer(pl, () -> {
            if (monde() != null) {
                attente.cancel();
                attente = null;
                poser();
            }
        }, 40L, 20L);
    }

    void poser() {
        World w = monde();
        if (w == null) {
            return;
        }
        retirer();
        for (Entity e : w.getEntities()) {
            if (e.getPersistentDataContainer().has(pl.cle(), PersistentDataType.BYTE)) {
                e.remove();
            }
        }
        for (Jeu j : Jeu.values()) {
            Location at = new Location(w, j.npcX, Y, Z, 180f, 0f);
            at.getChunk().addPluginChunkTicket(pl);
            Villager v = w.spawn(at, Villager.class, CreatureSpawnEvent.SpawnReason.CUSTOM, e -> {
                e.setPersistent(false);
                e.getPersistentDataContainer().set(pl.cle(), PersistentDataType.BYTE, (byte) 1);
                e.setAI(false);
                e.setSilent(true);
                e.setInvulnerable(true);
                e.setCollidable(false);
                e.setRemoveWhenFarAway(false);
                e.customName(Txt.mm(j.grad("<b>" + j.nom + "</b>")));
                e.setCustomNameVisible(true);
                e.setProfession(j.metier);
                e.setVillagerLevel(5);
                e.setRotation(180f, 0f);
            });
            poses.add(v);
            pnj.put(v.getUniqueId(), j);
            StringBuilder sb = new StringBuilder(j.grad("<b>" + j.nom.toUpperCase(java.util.Locale.ROOT) + "</b>"))
                    .append("\n<#E6EDF3>").append(j.description).append("\n<#9AA4B2>Clic droit pour jouer");
            poses.add(w.spawn(at.clone().add(0, 2.9, 0), TextDisplay.class, e -> {
                e.setPersistent(false);
                e.getPersistentDataContainer().set(pl.cle(), PersistentDataType.BYTE, (byte) 1);
                e.text(Txt.mm(sb.toString()));
                e.setBillboard(Display.Billboard.CENTER);
                e.setAlignment(TextDisplay.TextAlignment.CENTER);
                e.setShadowed(true);
                e.setBackgroundColor(Color.fromARGB(150, 10, 12, 18));
                e.setBrightness(new Display.Brightness(15, 15));
                e.setLineWidth(170);
                e.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.75f, 0.75f, 0.75f), new AxisAngle4f()));
            }));
        }
        pl.getLogger().info("PNJ de l'arène posés dans la salle d'arcade (" + w.getName() + ").");
    }

    void retirer() {
        for (Entity e : poses) {
            e.remove();
        }
        poses.clear();
        pnj.clear();
        if (attente != null) {
            attente.cancel();
            attente = null;
        }
    }

    Jeu jeuDuPnj(Entity e) {
        return pnj.get(e.getUniqueId());
    }

    /** Menu du PNJ dans le chat : modes, records. */
    void menu(Player p, Jeu j) {
        p.sendMessage(Txt.mm(" "));
        Txt.send(p, j.grad("<b>" + j.nom.toUpperCase(java.util.Locale.ROOT) + "</b>") + " <#5A6270>·</#5A6270> " + j.description);
        var ligne = Txt.mm("  ");
        for (Jeu.Mode m : j.modes()) {
            ligne = ligne.append(Txt.button(m.nom(), j.c1, "/arene jouer " + j.id + " " + m.id(), m.aide())).append(Txt.mm(" "));
        }
        ligne = ligne.append(Txt.button("Records", "#E0B864", "/arene top " + j.id + " " + j.modes().get(0).id(), "Les 10 meilleurs"));
        p.sendMessage(ligne);
    }
}
