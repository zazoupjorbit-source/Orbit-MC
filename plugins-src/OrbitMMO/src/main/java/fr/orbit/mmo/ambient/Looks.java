package fr.orbit.mmo.ambient;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Equippable;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Bogged;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Cow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Panda;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Wolf;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.profile.PlayerTextures;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/**
 * Apparences d'Orbit MMO, dessinées par tools\gen-skins.ps1 et servies par le pack de ressources :
 * skins des PNJ (mannequins), tenues complètes des monstres humanoïdes (portées comme une armure, elles
 * recouvrent le modèle du jeu) et variantes d'animaux (loups, chats, vaches, cochons, poules).
 * La liste vient de looks.txt, écrit par le générateur. Les variantes d'animaux passent par un pack de
 * données copié dans le monde principal : il n'est lu qu'au démarrage du serveur.
 */
public final class Looks {

    public static final String NAMESPACE = "orbitmmo";

    private static final Map<String, List<String>> BY_KIND = new HashMap<>();
    private static final Set<String> SKINS = new HashSet<>();
    private static final Set<String> OUTFITS = new HashSet<>();
    private static boolean loaded;
    /** Monstres en tenue qui gardaient la voix du modèle du jeu caché dessous (squelette, zombie) : muets. */
    private static final Set<String> SILENT = Set.of("bandit", "braconnier", "purificateur", "elfe_sombre", "pillard",
            "pillard_archer", "glaneur", "glaneur_guetteur", "treant");

    private Looks() {
    }

    /** Lit looks.txt (dans le jar). Fait tout seul au premier besoin : les quêtes se chargent avant le continent. */
    private static synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        try (InputStream in = Looks.class.getResourceAsStream("/looks.txt")) {
            if (in == null) {
                Bukkit.getLogger().warning("[OrbitMMO] Apparences : looks.txt absent du jar (lance tools/gen-skins.ps1 puis build.ps1).");
                return;
            }
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String line = r.readLine(); line != null; line = r.readLine()) {
                String[] p = line.trim().split(" ");
                if (p.length != 2) {
                    continue;
                }
                BY_KIND.computeIfAbsent(p[0], k -> new ArrayList<>()).add(p[1]);
                if (p[0].equals("skin")) {
                    SKINS.add(p[1]);
                } else if (p[0].equals("tenue")) {
                    OUTFITS.add(p[1]);
                }
            }
        } catch (IOException e) {
            Bukkit.getLogger().log(Level.WARNING, "[OrbitMMO] Apparences : lecture de looks.txt impossible", e);
        }
    }

    private static boolean started;

    /** Installe le pack de données des variantes d'animaux et fait le point dans le journal. Une fois, au démarrage. */
    public static void init(Plugin plugin) {
        load();
        if (started) {
            return;
        }
        started = true;
        boolean changed = installDatapack(plugin);
        int variants = 0, missing = 0;
        for (String id : ids("loup", "chien")) {
            variants++;
            missing += wolf(id) == null ? 1 : 0;
        }
        plugin.getLogger().info("Apparences : " + SKINS.size() + " skins de PNJ, " + OUTFITS.size() + " tenues de monstres, "
                + variants + " variantes de loups et chiens" + (missing > 0 ? " (" + missing + " pas encore chargées)" : "") + ".");
        if (changed || missing > 0) {
            plugin.getLogger().warning("Apparences : le pack de données des variantes d'animaux vient d'être "
                    + (changed ? "copié dans le monde" : "trouvé incomplet") + ". Il n'est lu qu'au démarrage : en attendant le prochain "
                    + "redémarrage, les animaux gardent une apparence du jeu.");
        }
    }

    /**
     * Dossier du monde principal, celui de level.dat (les packs de données y sont, dans datapacks\). Depuis
     * Minecraft 26, le dossier que donne l'API est celui de la dimension (world\dimensions\minecraft\overworld).
     */
    private static File levelRoot() {
        File folder = Bukkit.getWorlds().get(0).getWorldFolder().getAbsoluteFile();
        for (File d = folder; d != null; d = d.getParentFile()) {
            if (new File(d, "level.dat").isFile()) {
                return d;
            }
        }
        return folder;
    }

    /** Copie datapack/ (jar) dans world/datapacks/orbitmmo si le contenu a changé. Vrai si quelque chose a été écrit. */
    private static boolean installDatapack(Plugin plugin) {
        if (Bukkit.getWorlds().isEmpty()) {
            return false;
        }
        File dir = new File(new File(levelRoot(), "datapacks"), NAMESPACE);
        boolean changed = false;
        try (InputStream idx = plugin.getResource("datapack/index.txt")) {
            if (idx == null) {
                return false;
            }
            Set<String> wanted = new HashSet<>();
            for (String rel : new String(idx.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                rel = rel.trim();
                if (rel.isEmpty()) {
                    continue;
                }
                wanted.add(rel);
                try (InputStream in = plugin.getResource("datapack/" + rel)) {
                    if (in == null) {
                        continue;
                    }
                    byte[] bytes = in.readAllBytes();
                    File out = new File(dir, rel);
                    if (out.isFile() && Arrays.equals(Files.readAllBytes(out.toPath()), bytes)) {
                        continue;
                    }
                    out.getParentFile().mkdirs();
                    Files.write(out.toPath(), bytes);
                    changed = true;
                }
            }
            // Variantes retirées du générateur : leurs fichiers partent aussi.
            File data = new File(dir, "data/" + NAMESPACE);
            File[] regs = data.listFiles(File::isDirectory);
            if (regs != null) {
                for (File reg : regs) {
                    File[] files = reg.listFiles((d, n) -> n.endsWith(".json"));
                    if (files == null) {
                        continue;
                    }
                    for (File f : files) {
                        if (!wanted.contains("data/" + NAMESPACE + "/" + reg.getName() + "/" + f.getName()) && f.delete()) {
                            changed = true;
                        }
                    }
                }
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Apparences : copie du pack de données impossible dans " + dir, e);
        }
        return changed;
    }

    // ------------------------------------------------------------------ Listes

    /** Identifiants d'une ou plusieurs familles (« skin », « tenue », « loup », « chien », « chat », « vache », « cochon », « poule »). */
    public static List<String> ids(String... kinds) {
        load();
        List<String> out = new ArrayList<>();
        for (String k : kinds) {
            out.addAll(BY_KIND.getOrDefault(k, List.of()));
        }
        return out;
    }

    /** Skins dont l'identifiant commence par ce préfixe (ex. « villageois_lumiere_ »). */
    public static List<String> skins(String prefix) {
        load();
        List<String> out = new ArrayList<>();
        for (String id : BY_KIND.getOrDefault("skin", List.of())) {
            if (id.startsWith(prefix)) {
                out.add(id);
            }
        }
        return out;
    }

    public static boolean hasSkin(String id) {
        load();
        return id != null && SKINS.contains(id);
    }

    public static boolean hasOutfit(String id) {
        load();
        return id != null && OUTFITS.contains(id);
    }

    // ------------------------------------------------------------------ PNJ

    /** Profil d'un mannequin qui porte un skin du pack (textures/entity/npc/&lt;id&gt;.png). */
    public static ResolvableProfile skin(String id) {
        return ResolvableProfile.resolvableProfile().skinPatch(patch -> patch
                .body(Key.key(NAMESPACE, "entity/npc/" + id))
                .model(PlayerTextures.SkinModel.CLASSIC)).build();
    }

    // ------------------------------------------------------------------ Monstres

    /**
     * Donne à un monstre l'apparence qui porte son identifiant : tenue complète (humanoïdes), variante (loups),
     * ours brun (panda). Sans apparence à ce nom, le monstre garde celle de son type, telle que le pack la redessine.
     */
    public static void applyMob(LivingEntity e, String mobId) {
        load();
        if (mobId == null) {
            return;
        }
        if (OUTFITS.contains(mobId)) {
            dress(e, mobId);
            if (SILENT.contains(mobId)) {
                e.setSilent(true);
            }
        }
        if (e instanceof Wolf w) {
            Wolf.Variant v = wolf(mobId);
            if (v != null) {
                w.setVariant(v);
            }
        }
        if (e instanceof Panda p) {
            p.setMainGene(Panda.Gene.BROWN);
            p.setHiddenGene(Panda.Gene.BROWN);
        }
        if (e instanceof Bogged b && OUTFITS.contains(mobId)) {
            b.setSheared(true);
        }
    }

    /** Habille une créature humanoïde d'une tenue du pack : tête, torse et bras, jambes. Rien ne tombe à sa mort. */
    public static void dress(LivingEntity e, String outfit) {
        EntityEquipment eq = e.getEquipment();
        if (eq == null) {
            return;
        }
        eq.setHelmet(piece(outfit, EquipmentSlot.HEAD));
        eq.setChestplate(piece(outfit, EquipmentSlot.CHEST));
        eq.setLeggings(piece(outfit, EquipmentSlot.LEGS));
        eq.setBoots(piece(outfit, EquipmentSlot.FEET));
        if (e instanceof org.bukkit.entity.Mob) {
            eq.setHelmetDropChance(0);
            eq.setChestplateDropChance(0);
            eq.setLeggingsDropChance(0);
            eq.setBootsDropChance(0);
        }
    }

    private static ItemStack piece(String outfit, EquipmentSlot slot) {
        // Du papier : aucun point d'armure, aucune usure. Seul le composant « équipable » compte, pour le rendu.
        ItemStack it = ItemStack.of(Material.PAPER);
        it.setData(DataComponentTypes.EQUIPPABLE, Equippable.equippable(slot).assetId(Key.key(NAMESPACE, outfit))
                .dispensable(false).swappable(false).damageOnHurt(false).build());
        return it;
    }

    // ------------------------------------------------------------------ Variantes d'animaux

    private static NamespacedKey key(String id) {
        return new NamespacedKey(NAMESPACE, id);
    }

    /** Variante de loup du pack de données, ou null si elle n'est pas (encore) chargée. */
    public static Wolf.Variant wolf(String id) {
        try {
            return RegistryAccess.registryAccess().getRegistry(RegistryKey.WOLF_VARIANT).get(key(id));
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static Cat.Type cat(String id) {
        try {
            return RegistryAccess.registryAccess().getRegistry(RegistryKey.CAT_VARIANT).get(key(id));
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static Cow.Variant cow(String id) {
        try {
            return RegistryAccess.registryAccess().getRegistry(RegistryKey.COW_VARIANT).get(key(id));
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static Pig.Variant pig(String id) {
        try {
            return RegistryAccess.registryAccess().getRegistry(RegistryKey.PIG_VARIANT).get(key(id));
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static Chicken.Variant chicken(String id) {
        try {
            return RegistryAccess.registryAccess().getRegistry(RegistryKey.CHICKEN_VARIANT).get(key(id));
        } catch (RuntimeException e) {
            return null;
        }
    }
}
