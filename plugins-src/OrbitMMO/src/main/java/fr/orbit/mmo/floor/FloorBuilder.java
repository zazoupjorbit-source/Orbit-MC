package fr.orbit.mmo.floor;

import fr.orbit.mmo.Keys;
import fr.orbit.mmo.registry.Defs.FloorDef;
import fr.orbit.mmo.util.Text;
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

        // --- Étiquettes et hôtel des ventes (ex-marchand, étiquette « merchant » gardée) ---
        label(w, new Location(w, 0.5, y + 8.7, 0.5),
                "<gradient:#7FD4FF:#B36BFF><b>Porte de téléportation</b></gradient>\n<#D7DCE2>" + Text.esc(def.town()));
        label(w, new Location(w, ax - arenaR - 3.5, y + 7.5, 0.5),
                "<#FF3B3B><b>Salle du boss</b></#FF3B3B>\n<#D7DCE2>Étage " + def.number());
        if (fr.orbit.mmo.ambient.TownFolk.human("merchant")) {
            fr.orbit.mmo.ambient.TownFolk.spawn(new Location(w, 7.5, y + 1, 7.5, 135, 0), "merchant", Text.mm(MARKET_NAME));
            return;
        }
        w.spawn(new Location(w, 7.5, y + 1, 7.5, 135, 0), Villager.class, v -> {
            v.setAI(false);
            v.setInvulnerable(true);
            v.setSilent(true);
            v.setProfession(Villager.Profession.CARTOGRAPHER);
            v.customName(Text.mm(MARKET_NAME));
            v.setCustomNameVisible(true);
            v.setPersistent(true);
            v.setRemoveWhenFarAway(false);
            v.setCollidable(false);
            v.getPersistentDataContainer().set(Keys.NPC, PersistentDataType.STRING, "merchant");
        });
    }

    /**
     * PNJ de la ville ajoutés après le marchand : forgeron, alchimiste et prêtre, chacun devant son étal,
     * et un chat qui dort près de la forge. Posés une seule fois par étage (voir FloorService).
     */
    static void townNpcs(World w) {
        int y = FloorGenerator.SURFACE;

        // --- Forge (nord-ouest de la place, côté +z) ---
        set(w, -10, y + 1, 8, Material.SMITHING_TABLE);
        set(w, -10, y + 1, 9, Material.BLAST_FURNACE);
        set(w, -9, y + 1, 10, Material.LAVA_CAULDRON);
        place(w, -9, y + 1, 9, Material.ANVIL);
        npc(w, Villager.class, -7.5, y + 1, 7.5, 225, "blacksmith", "<#FF8A3B><b>Torvald</b> <#8C96A3>· Forgeron",
                v -> v.setProfession(Villager.Profession.ARMORER));
        label(w, new Location(w, -7.5, y + 3.05, 7.5), FORGE_LABEL);
        w.spawn(new Location(w, -8.5, y + 1, 10.5, 200, 0), org.bukkit.entity.Cat.class, c -> {
            c.setAI(false);
            c.setSitting(true);
            c.setInvulnerable(true);
            c.setSilent(true);
            c.setPersistent(true);
            c.setRemoveWhenFarAway(false);
            c.setCollidable(false);
            c.getPersistentDataContainer().set(Keys.NPC, PersistentDataType.STRING, "ambient");
        });

        // --- Échoppe de l'alchimiste (côté −z, à l'est) ---
        set(w, 9, y + 1, -9, Material.BREWING_STAND);
        set(w, 10, y + 1, -9, Material.WATER_CAULDRON);
        set(w, 10, y + 1, -8, Material.BARREL);
        set(w, 9, y + 1, -10, Material.POTTED_ALLIUM);
        npc(w, org.bukkit.entity.Witch.class, 7.5, y + 1, -7.5, 45, "alchemist", "<#B36BFF><b>Mirelle</b> <#8C96A3>· Alchimiste", null);
        label(w, new Location(w, 7.5, y + 3.05, -7.5), "<#D7DCE2>Potions · Élixirs");

        // --- Autel du prêtre (côté −z, à l'ouest) ---
        set(w, -9, y + 1, -9, Material.LECTERN);
        set(w, -10, y + 1, -9, Material.CHISELED_QUARTZ_BLOCK);
        set(w, -9, y + 1, -10, Material.CHISELED_QUARTZ_BLOCK);
        org.bukkit.block.data.BlockData candles = org.bukkit.Bukkit.createBlockData("minecraft:candle[candles=3,lit=true]");
        w.getBlockAt(-10, y + 2, -9).setBlockData(candles, false);
        w.getBlockAt(-9, y + 2, -10).setBlockData(candles, false);
        npc(w, Villager.class, -7.5, y + 1, -7.5, 315, "priest", "<#FFF3B0><b>Frère Anselme</b> <#8C96A3>· Prêtre",
                v -> v.setProfession(Villager.Profession.CLERIC));
        label(w, new Location(w, -7.5, y + 3.05, -7.5), "<#D7DCE2>Parchemins · Essences · Bénédiction");
    }

    /** Étiquette au-dessus de Torvald (mise à jour aussi sur les étages déjà construits, voir FloorService). */
    static final String FORGE_LABEL = "<#D7DCE2>Améliorer · Réparer · Rachat du butin";

    /** Nom du PNJ de l'hôtel des ventes (l'ancien marchand). */
    static final String MARKET_NAME = "<#FFD66E><b>Corvin</b> <#8C96A3>· Hôtel des ventes";

    /**
     * Comptoir de l'hôtel des ventes derrière Corvin (ex-marchand) : table de cartographie, étagères,
     * cloche et lanterne, plus l'étiquette. Posé une seule fois par étage (voir FloorService).
     * Le PNJ lui-même est renommé par FloorService (ses entités ne sont pas forcément chargées ici).
     */
    static void marketStall(World w) {
        int y = FloorGenerator.SURFACE;
        set(w, 9, y + 1, 8, Material.CARTOGRAPHY_TABLE);
        set(w, 9, y + 1, 9, Material.CHISELED_BOOKSHELF);
        set(w, 8, y + 1, 9, Material.BOOKSHELF);
        set(w, 10, y + 1, 9, Material.BOOKSHELF);
        set(w, 9, y + 1, 10, Material.FLETCHING_TABLE);
        w.getBlockAt(9, y + 2, 9).setBlockData(org.bukkit.Bukkit.createBlockData("minecraft:bell[attachment=floor,facing=west]"), false);
        w.getBlockAt(8, y + 2, 9).setBlockData(org.bukkit.Bukkit.createBlockData("minecraft:lantern[hanging=false]"), false);
        set(w, 10, y + 2, 9, Material.POTTED_FERN);
        label(w, new Location(w, 7.5, y + 3.05, 7.5), "<#D7DCE2>Achat · Vente entre joueurs");
    }

    /**
     * Établi d'Ysolde, la runiste (côté ouest de la place, juste au sud de l'entrée) : table d'enchantement,
     * magnétite, améthystes, et la PNJ. Posé une seule fois par étage (voir FloorService).
     */
    static void runeStall(World w) {
        int y = FloorGenerator.SURFACE;
        set(w, -14, y + 1, -4, Material.ENCHANTING_TABLE);
        set(w, -14, y + 1, -3, Material.LODESTONE);
        set(w, -14, y + 1, -5, Material.AMETHYST_BLOCK);
        w.getBlockAt(-14, y + 2, -5).setBlockData(org.bukkit.Bukkit.createBlockData("minecraft:amethyst_cluster[facing=up]"), false);
        w.getBlockAt(-14, y + 2, -3).setBlockData(org.bukkit.Bukkit.createBlockData("minecraft:soul_lantern[hanging=false]"), false);
        set(w, -15, y + 1, -4, Material.CHISELED_BOOKSHELF);
        npc(w, Villager.class, -12.5, y + 1, -3.5, 270, "runesmith", RUNE_NAME,
                v -> v.setProfession(Villager.Profession.LIBRARIAN));
        label(w, new Location(w, -12.5, y + 3.05, -3.5), "<#D7DCE2>Runes · Sertissage · Fusion");
    }

    static final String RUNE_NAME = "<#9B59FF><b>Ysolde</b> <#8C96A3>· Runiste";

    /**
     * Pose un PNJ de ville par son étiquette (« blacksmith », « alchemist », « priest », « runesmith », « merchant »)
     * avec son écriteau flottant, n'importe où : sert aux capitales du continent, qui reprennent les mêmes PNJ
     * que les villes des étages. Renvoie les entités créées (PNJ puis écriteau), ou une liste vide si l'étiquette est inconnue.
     */
    static java.util.List<org.bukkit.entity.Entity> townNpc(World w, String tag, double x, double y, double z, float yaw) {
        Class<? extends org.bukkit.entity.LivingEntity> type = Villager.class;
        Villager.Profession job = null;
        String name;
        String sign;
        // Hauteur de l'écriteau au-dessus des pieds : celle d'un villageois, sauf pour les PNJ plus grands.
        double board = 2.05;
        switch (tag) {
            case "tower" -> {
                name = "<#7FD4FF><b>Gardien du Pilier</b> <#8C96A3>· Les cent étages";
                sign = TOWER_LABEL;
                type = org.bukkit.entity.IronGolem.class;
                board = 2.85;
            }
            case "tower_ombre" -> {
                name = "<#9B59FF><b>Veilleur du Pilier</b> <#8C96A3>· Les cent étages";
                sign = TOWER_LABEL;
                type = org.bukkit.entity.WitherSkeleton.class;
                board = 2.55;
            }
            case "blacksmith" -> {
                name = "<#FF8A3B><b>Torvald</b> <#8C96A3>· Forgeron";
                sign = FORGE_LABEL;
                job = Villager.Profession.ARMORER;
            }
            case "alchemist" -> {
                name = "<#B36BFF><b>Mirelle</b> <#8C96A3>· Alchimiste";
                sign = "<#D7DCE2>Potions · Élixirs";
                type = org.bukkit.entity.Witch.class;
            }
            case "priest" -> {
                name = "<#FFF3B0><b>Frère Anselme</b> <#8C96A3>· Prêtre";
                sign = "<#D7DCE2>Parchemins · Essences · Bénédiction";
                job = Villager.Profession.CLERIC;
            }
            case "runesmith" -> {
                name = RUNE_NAME;
                sign = "<#D7DCE2>Runes · Sertissage · Fusion";
                job = Villager.Profession.LIBRARIAN;
            }
            case "merchant" -> {
                name = MARKET_NAME;
                sign = "<#D7DCE2>Achat · Vente entre joueurs";
                job = Villager.Profession.CARTOGRAPHER;
            }
            default -> {
                return java.util.List.of();
            }
        }
        Villager.Profession profession = job;
        // Avec le pack : un mannequin qui porte le skin du PNJ. Sinon (skin absent du jar), le villageois d'avant.
        org.bukkit.entity.Entity npc = fr.orbit.mmo.ambient.TownFolk.human(tag)
                ? fr.orbit.mmo.ambient.TownFolk.spawn(new Location(w, x, y, z, yaw, 0), tag, Text.mm(name))
                : w.spawn(new Location(w, x, y, z, yaw, 0), type, e -> {
            e.setAI(false);
            e.setInvulnerable(true);
            e.setSilent(true);
            e.customName(Text.mm(name));
            e.setCustomNameVisible(true);
            e.setPersistent(true);
            e.setRemoveWhenFarAway(false);
            e.setCollidable(false);
            e.getPersistentDataContainer().set(Keys.NPC, PersistentDataType.STRING, tag);
            if (profession != null && e instanceof Villager v) {
                v.setProfession(profession);
            }
            if (tag.equals("tower_ombre") && fr.orbit.mmo.ambient.Looks.hasOutfit("veilleur")) {
                fr.orbit.mmo.ambient.Looks.dress(e, "veilleur");
            }
        });
        org.bukkit.entity.Entity label = w.spawn(new Location(w, x, y + board, z), TextDisplay.class, t -> {
            t.text(Text.mm(sign));
            t.setBillboard(Display.Billboard.CENTER);
            t.setPersistent(true);
            t.setShadowed(true);
            t.setDefaultBackground(false);
            t.setBackgroundColor(Color.fromARGB(96, 0, 0, 0));
            t.getPersistentDataContainer().set(Keys.NPC, PersistentDataType.STRING, "label");
        });
        return java.util.List.of(npc, label);
    }

    /** Écriteau des gardiens du Pilier des Astres (le donjon des cent étages), devant le donjon de chaque capitale. */
    static final String TOWER_LABEL = "<#D7DCE2>Le Pilier des Astres · Choisir l'étage";

    /** PNJ immobile, invulnérable, qui ne disparaît jamais. */
    private static <T extends org.bukkit.entity.LivingEntity> void npc(World w, Class<T> type, double x, double y, double z, float yaw,
                                                                      String tag, String name, java.util.function.Consumer<T> extra) {
        if (fr.orbit.mmo.ambient.TownFolk.human(tag)) {
            fr.orbit.mmo.ambient.TownFolk.spawn(new Location(w, x, y, z, yaw, 0), tag, Text.mm(name));
            return;
        }
        w.spawn(new Location(w, x, y, z, yaw, 0), type, e -> {
            e.setAI(false);
            e.setInvulnerable(true);
            e.setSilent(true);
            e.customName(Text.mm(name));
            e.setCustomNameVisible(true);
            e.setPersistent(true);
            e.setRemoveWhenFarAway(false);
            e.setCollidable(false);
            e.getPersistentDataContainer().set(Keys.NPC, PersistentDataType.STRING, tag);
            if (extra != null) {
                extra.accept(e);
            }
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
