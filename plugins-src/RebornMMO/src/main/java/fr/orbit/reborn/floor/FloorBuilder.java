package fr.orbit.reborn.floor;

import fr.orbit.reborn.Keys;
import fr.orbit.reborn.registry.Defs.FloorDef;
import fr.orbit.reborn.util.Text;
import org.bukkit.Color;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataType;

import java.util.Random;

/** Construit une seule fois les éléments fixes d'un étage : ville, porte de téléportation, chemin, salle du boss. */
final class FloorBuilder {

    private FloorBuilder() {
    }

    static void build(World w, FloorDef def, int townR, int ax, int arenaR) {
        int y = FloorGenerator.SURFACE;
        Random r = new Random(def.number() * 31L);

        // --- Place de la ville ---
        for (int x = -townR - 1; x <= townR + 1; x++) {
            for (int z = -townR - 1; z <= townR + 1; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > townR + 0.5) {
                    continue;
                }
                for (int yy = y + 1; yy <= y + 16; yy++) {
                    set(w, x, yy, z, Material.AIR);
                }
                for (int yy = y - 4; yy < y; yy++) {
                    set(w, x, yy, z, Material.STONE);
                }
                Material floor;
                if (d < 5.5) {
                    floor = Material.SMOOTH_QUARTZ;
                } else if (d < 6.5) {
                    floor = Material.QUARTZ_BRICKS;
                } else if (d >= 14.5 && d < 15.5) {
                    floor = Material.POLISHED_ANDESITE;
                } else {
                    int roll = r.nextInt(20);
                    floor = roll == 0 ? Material.MOSSY_STONE_BRICKS : roll == 1 ? Material.CRACKED_STONE_BRICKS : Material.STONE_BRICKS;
                }
                set(w, x, y, z, floor);
            }
        }
        // Muret d'enceinte avec quatre entrées.
        for (int x = -townR - 2; x <= townR + 2; x++) {
            for (int z = -townR - 2; z <= townR + 2; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d < townR + 0.5 || d >= townR + 1.5 || Math.abs(x) <= 2 || Math.abs(z) <= 2) {
                    continue;
                }
                set(w, x, y, z, Material.STONE_BRICKS);
                place(w, x, y + 1, z, Material.STONE_BRICK_WALL);
            }
        }
        // Lampadaires.
        for (int i = 0; i < 8; i++) {
            double a = Math.PI / 4 * i + Math.PI / 8;
            int x = (int) Math.round(Math.cos(a) * 20);
            int z = (int) Math.round(Math.sin(a) * 20);
            for (int yy = y + 1; yy <= y + 3; yy++) {
                place(w, x, yy, z, Material.DARK_OAK_FENCE);
            }
            place(w, x, y + 4, z, Material.LANTERN);
        }

        // --- Porte de téléportation (arche au centre de la place) ---
        for (int yy = y + 1; yy <= y + 5; yy++) {
            set(w, -2, yy, 0, Material.QUARTZ_PILLAR);
            set(w, 2, yy, 0, Material.QUARTZ_PILLAR);
        }
        for (int x = -2; x <= 2; x++) {
            set(w, x, y + 6, 0, Material.CHISELED_QUARTZ_BLOCK);
        }
        set(w, 0, y + 7, 0, Material.SEA_LANTERN);
        set(w, -3, y, 0, Material.SEA_LANTERN);
        set(w, 3, y, 0, Material.SEA_LANTERN);

        // --- Chemin vers la salle du boss ---
        for (int x = townR + 1; x <= ax - arenaR - 2; x++) {
            for (int z = -1; z <= 1; z++) {
                int top = w.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
                Block b = w.getBlockAt(x, top, z);
                if (b.getType() == def.theme().top) {
                    b.setType(def.theme().path, false);
                }
                Block above = b.getRelative(BlockFace.UP);
                if (!above.getType().isAir() && !above.getType().isSolid()) {
                    above.setType(Material.AIR, false);
                }
            }
        }

        // --- Salle du boss : arène circulaire fermée, ouverte vers la ville ---
        for (int dx = -arenaR - 3; dx <= arenaR + 3; dx++) {
            for (int dz = -arenaR - 3; dz <= arenaR + 3; dz++) {
                int x = ax + dx;
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d <= arenaR + 0.5) {
                    for (int yy = y + 1; yy <= y + 22; yy++) {
                        set(w, x, yy, dz, Material.AIR);
                    }
                    for (int yy = y - 4; yy < y; yy++) {
                        set(w, x, yy, dz, Material.STONE);
                    }
                    Material m = (d >= 8.5 && d < 9.5) ? Material.CHISELED_POLISHED_BLACKSTONE
                            : d < 2.5 ? Material.GILDED_BLACKSTONE : Material.POLISHED_BLACKSTONE_BRICKS;
                    set(w, x, y, dz, m);
                } else if (d <= arenaR + 2.5) {
                    boolean door = dx < 0 && Math.abs(dz) <= 2;
                    for (int yy = y - 3; yy <= y + 11; yy++) {
                        if (door && yy >= y + 1 && yy <= y + 5) {
                            set(w, x, yy, dz, Material.AIR);
                        } else if (door && yy == y) {
                            set(w, x, yy, dz, Material.POLISHED_BLACKSTONE_BRICKS);
                        } else {
                            set(w, x, yy, dz, yy == y + 11 ? Material.POLISHED_BLACKSTONE : Material.DEEPSLATE_BRICKS);
                        }
                    }
                }
            }
        }
        for (int i = 0; i < 12; i++) {
            double a = Math.PI / 6 * i;
            int x = ax + (int) Math.round(Math.cos(a) * (arenaR - 1));
            int z = (int) Math.round(Math.sin(a) * (arenaR - 1));
            if (x < ax && Math.abs(z) <= 3) {
                continue;
            }
            place(w, x, y + 1, z, Material.POLISHED_BLACKSTONE_WALL);
            place(w, x, y + 2, z, Material.SOUL_LANTERN);
        }

        // --- Étiquettes et marchand ---
        label(w, new Location(w, 0.5, y + 8.7, 0.5),
                "<gradient:#7FD4FF:#B36BFF><b>Porte de téléportation</b></gradient>\n<#D7DCE2>" + Text.esc(def.town()));
        label(w, new Location(w, ax - arenaR - 3.5, y + 7.5, 0.5),
                "<#FF3B3B><b>Salle du boss</b></#FF3B3B>\n<#D7DCE2>Étage " + def.number());
        w.spawn(new Location(w, 7.5, y + 1, 7.5, 135, 0), Villager.class, v -> {
            v.setAI(false);
            v.setInvulnerable(true);
            v.setSilent(true);
            v.setProfession(Villager.Profession.WEAPONSMITH);
            v.customName(Text.mm("<#FFD66E><b>Marchand</b>"));
            v.setCustomNameVisible(true);
            v.setPersistent(true);
            v.setRemoveWhenFarAway(false);
            v.setCollidable(false);
            v.getPersistentDataContainer().set(Keys.NPC, PersistentDataType.STRING, "merchant");
        });
    }

    private static void label(World w, Location at, String mini) {
        w.spawn(at, TextDisplay.class, t -> {
            t.text(Text.mm(mini));
            t.setBillboard(Display.Billboard.CENTER);
            t.setPersistent(true);
            t.setShadowed(true);
            t.setDefaultBackground(false);
            t.setBackgroundColor(Color.fromARGB(96, 0, 0, 0));
            t.getPersistentDataContainer().set(Keys.NPC, PersistentDataType.STRING, "label");
        });
    }

    private static void set(World w, int x, int y, int z, Material m) {
        Block b = w.getBlockAt(x, y, z);
        if (b.getType() != m) {
            b.setType(m, false);
        }
    }

    /** Pose avec mise à jour des formes (murets, barrières, lanternes). */
    private static void place(World w, int x, int y, int z, Material m) {
        w.getBlockAt(x, y, z).setType(m, true);
    }
}
