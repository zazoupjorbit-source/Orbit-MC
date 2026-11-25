package fr.orbit.mmo.outfit;

import net.kyori.adventure.key.Key;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Une tenue 3D, nommée comme l'apparence des pièces (clé {@code orbitmmo:outfit} posée par Loot : « plaques_2 » =
 * plaques, palier acier) : couche du corps (equipment du pack {@code orbitmmo:tenue_<id>}), casque en modèle 3D
 * porté sur la tête, et pièces qui dépassent (épaules, cape) posées sur des ItemDisplay qui suivent le joueur.
 * Il y en a 32 : les 4 types d'armure (tissu, cuir, mailles, plaques) × les 8 paliers.
 * Modèles et textures : tools\gen-outfits.ps1 (tools\hd\OutfitHd.cs), dans le pack sous orbitmmo:outfit/&lt;id&gt;/.
 * Les icônes des pièces restent celles de l'armurerie (tools\gen-items.ps1).
 */
public record OutfitDef(String id, String label, Key equipment, Key helmet, Key equipSound, List<Part> parts) {

    /** Façon dont une pièce suit le corps. */
    public enum Kind {
        /** Fixée au haut du torse (épaulières, pèlerine, col de fourrure). */
        TORSE,
        /** Accrochée derrière le cou, se soulève quand on court ou qu'on tombe. */
        CAPE
    }

    public record Part(String name, Key model, Kind kind) {
    }

    /** Les 4 types, dans l'ordre du générateur (OutfitHd.TYPES) ; le nom est celui de ArmorType.id. */
    private static final String[] TYPES = {"tissu", "cuir", "mailles", "plaques"};
    private static final String[] TYPE_LABELS = {"Tissu", "Cuir", "Mailles", "Plaques"};
    private static final String[] SOUNDS = {"item.armor.equip_leather", "item.armor.equip_leather",
            "item.armor.equip_chain", "item.armor.equip_iron"};
    private static final String[] CLOTH = {"lin", "laine", "soie", "soie d'électrum", "tisse-cristal", "soie d'ombre",
            "tisse-astral", "tisse-orbite"};
    private static final String[] HIDE = {"peau", "cuir tanné", "cuir clouté", "cuir d'électrum", "cuir cristallin",
            "cuir d'obsidienne", "cuir astral", "cuir d'orbitium"};
    private static final String[] METAL = {"recrue", "cuivre", "acier", "électrum", "cristal", "obsidienne", "astralite",
            "orbitium"};
    /** Nombre de paliers (le même que Loot.TIERS). */
    public static final int TIERS = 8;

    private static final Map<String, OutfitDef> ALL = new LinkedHashMap<>();

    static {
        for (int type = 0; type < TYPES.length; type++) {
            for (int tier = 0; tier < TIERS; tier++) {
                String id = TYPES[type] + "_" + tier;
                String tierName = type == 0 ? CLOTH[tier] : type == 1 ? HIDE[tier] : METAL[tier];
                List<Part> parts = new ArrayList<>();
                parts.add(new Part("epaules", model(id, "epaules"), Kind.TORSE));
                // Même règle que le générateur (OutfitHd.HasCape) : pas de cape au premier palier.
                if (tier >= 1) {
                    parts.add(new Part("cape", model(id, "cape"), Kind.CAPE));
                }
                ALL.put(id, new OutfitDef(id, TYPE_LABELS[type] + " · " + tierName, Key.key("orbitmmo", "tenue_" + id),
                        model(id, "casque"), Key.key("minecraft", SOUNDS[type]), List.copyOf(parts)));
            }
        }
    }

    private static Key model(String outfit, String part) {
        return Key.key("orbitmmo", "outfit/" + outfit + "/" + part);
    }

    public static OutfitDef get(String id) {
        return id == null ? null : ALL.get(id);
    }

    public static Collection<OutfitDef> all() {
        return Collections.unmodifiableCollection(ALL.values());
    }
}
