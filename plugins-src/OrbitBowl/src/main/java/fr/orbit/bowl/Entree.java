package fr.orbit.bowl;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * PNJ et panneaux hors parties : le portier d'ORBIT BOWL dans la salle d'arcade (monde {@code casino}), l'hôtesse
 * de l'accueil, le barman, et le mur des records de la salle (relu chaque minute).
 */
public final class Entree {

    private final OrbitBowl pl;
    private final List<Entity> arcade = new ArrayList<>();
    private final List<TextDisplay> records = new ArrayList<>();
    private UUID portier, hotesse;
    private BukkitTask attente;

    Entree(OrbitBowl pl) {
        this.pl = pl;
    }

    String nomArcade() {
        return pl.getConfig().getString("arcade.monde", "casino");
    }

    /** Place du portier dans la salle d'arcade (réglage arcade.pnj : x y z yaw). */
    Location portierLieu() {
        World w = Bukkit.getWorld(nomArcade());
        if (w == null) {
            return null;
        }
        String[] a = pl.getConfig().getString("arcade.pnj", "-18.5 64 392.5 -90").trim().split("\\s+");
        try {
            return new Location(w, Double.parseDouble(a[0]), Double.parseDouble(a[1]), Double.parseDouble(a[2]), Float.parseFloat(a[3]), 0f);
        } catch (RuntimeException e) {
            return new Location(w, -18.5, 64, 392.5, -90f, 0f);
        }
    }

    /** Devant le portier, tourné vers lui (retour de la salle). */
    public Location retourArcade() {
        Location l = portierLieu();
        if (l == null) {
            return Bukkit.getWorlds().get(0).getSpawnLocation();
        }
        return Salle.devant(l);
    }

    /** OrbitCasino crée le monde de l'arcade un tick après son démarrage : on attend qu'il existe. */
    void attendre() {
        attente = Bukkit.getScheduler().runTaskTimer(pl, () -> {
            if (Bukkit.getWorld(nomArcade()) != null) {
                attente.cancel();
                attente = null;
                poserPortier();
            }
        }, 40L, 20L);
    }

    void poserPortier() {
        for (Entity e : arcade) {
            e.remove();
        }
        arcade.clear();
        Location at = portierLieu();
        if (at == null) {
            return;
        }
        for (Entity e : at.getWorld().getNearbyEntities(at, 3, 4, 3)) {
            if (e.getPersistentDataContainer().has(OrbitBowl.CLE, PersistentDataType.BYTE)) {
                e.remove();
            }
        }
        at.getChunk().addPluginChunkTicket(pl);
        Villager v = at.getWorld().spawn(at, Villager.class, CreatureSpawnEvent.SpawnReason.CUSTOM, e -> {
            OrbitBowl.marquer(e);
            e.setAI(false);
            e.setSilent(true);
            e.setInvulnerable(true);
            e.setCollidable(false);
            e.setRemoveWhenFarAway(false);
            e.customName(Txt.mm("<gradient:#3A7FFF:#E0B864><b>ORBIT BOWL</b></gradient>"));
            e.setCustomNameVisible(true);
            e.setProfession(Villager.Profession.FLETCHER);
            e.setVillagerLevel(5);
            e.setRotation(at.getYaw(), 0f);
        });
        portier = v.getUniqueId();
        arcade.add(v);
        arcade.add(Salle.texte(at.clone().add(0, 2.9, 0), "<gradient:#3A7FFF:#E0B864><b>🎳 ORBIT BOWL</b></gradient>\n"
                + "<#E6EDF3>Bowling 6 pistes · fléchettes · tir forain\n<#E6EDF3>basket · baby-foot · mini-golf · laser game\n"
                + "<#9AA4B2>Clic droit pour entrer", 0.75f, true));
        pl.getLogger().info("Portier d'ORBIT BOWL posé dans la salle d'arcade (" + at.getWorld().getName() + ").");
    }

    boolean estPortier(Entity e) {
        return e.getUniqueId().equals(portier);
    }

    boolean estHotesse(Entity e) {
        return e.getUniqueId().equals(hotesse);
    }

    /** PNJ et mur des records dans la salle. */
    void poserSalle(World w) {
        Mannequin h = pnj(w, new Location(w, 29.5, Salle.Y, -7.5, 0f, 0f), "<#E0B864>Hôtesse · ORBIT BOWL", Color.fromRGB(0x3A7FFF));
        hotesse = h.getUniqueId();
        pnj(w, new Location(w, -28.5, Salle.Y, -7.5, 0f, 0f), "<#62CFF4>Barman · Comptoir des Étoiles", Color.fromRGB(0x1B2235));
        records.clear();
        int i = 0;
        for (Jeu j : Jeu.values()) {
            if (j.record == null) {
                continue;
            }
            double z = -19 + (i % 3) * 10;
            double y = i < 3 ? Salle.Y + 6.1 : Salle.Y + 1.6;
            records.add(Salle.texte(new Location(w, Salle.X2 - 0.06, y, z, 90f, 0f), j.titre(), 0.62f, false));
            i++;
        }
        lireRecords();
    }

    private Mannequin pnj(World w, Location at, String nom, Color tenue) {
        return w.spawn(at, Mannequin.class, m -> {
            OrbitBowl.marquer(m);
            m.setAI(false);
            m.setGravity(false);
            m.setInvulnerable(true);
            m.setSilent(true);
            m.setCollidable(false);
            m.setImmovable(true);
            m.customName(Txt.mm(nom));
            m.setCustomNameVisible(true);
            try {
                m.setDescription(null);
            } catch (RuntimeException ignored) {
                m.setDescription(net.kyori.adventure.text.Component.empty());
            }
            m.getEquipment().setChestplate(cuir(Material.LEATHER_CHESTPLATE, tenue));
            m.getEquipment().setLeggings(cuir(Material.LEATHER_LEGGINGS, Color.fromRGB(0x0C0E14)));
            m.getEquipment().setBoots(cuir(Material.LEATHER_BOOTS, Color.fromRGB(0xE0B864)));
            m.setRotation(at.getYaw(), 0f);
        });
    }

    static ItemStack cuir(Material m, Color c) {
        ItemStack it = new ItemStack(m);
        if (it.getItemMeta() instanceof LeatherArmorMeta meta) {
            meta.setColor(c);
            it.setItemMeta(meta);
        }
        return it;
    }

    /** Relit le top 5 de chaque jeu pour le mur des records. */
    void lireRecords() {
        int i = 0;
        for (Jeu j : Jeu.values()) {
            if (j.record == null) {
                continue;
            }
            int idx = i++;
            pl.scores().lire(j, 5, list -> {
                if (idx >= records.size() || !records.get(idx).isValid()) {
                    return;
                }
                StringBuilder sb = new StringBuilder(j.titre());
                if (list.isEmpty()) {
                    sb.append("\n<#9AA4B2>aucun record… à toi !");
                }
                int r = 1;
                for (String[] row : list) {
                    String col = r == 1 ? "#E0B864" : r <= 3 ? "#C9D4E0" : "#9AA4B2";
                    sb.append("\n<").append(col).append(">").append(r++).append(". ").append(row[0]).append("</").append(col)
                            .append("> <#E6EDF3>").append(Txt.n(Long.parseLong(row[1]))).append(" ").append(j.unite);
                }
                records.get(idx).text(Txt.mm(sb.toString()));
            });
        }
    }

    void retirer() {
        for (Entity e : arcade) {
            e.remove();
        }
        arcade.clear();
        if (attente != null) {
            attente.cancel();
            attente = null;
        }
    }

    /** Message de l'hôtesse : tous les jeux et leurs règles. */
    void aide(Player p) {
        p.sendMessage(Txt.mm(" "));
        Txt.send(p, "Bienvenue à l'<gradient:#3A7FFF:#E0B864><b>ORBIT BOWL</b></gradient> ! Clique sur la console d'un jeu pour jouer. "
                + "Tout se joue en <#E0B864>Orbit Coins de jeu</#E0B864> (jamais achetables).");
        for (Jeu j : Jeu.values()) {
            p.sendMessage(Txt.mm("  ").append(Txt.button(j.nom, j.c1, "/bowl regles " + j.id, j.description + "\n<#9AA4B2>Clic : les règles"))
                    .append(Txt.mm(" <#9AA4B2>" + j.description)));
        }
    }
}
