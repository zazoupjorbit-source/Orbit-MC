package fr.orbit.devcore.cosmetic;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.DyeColor;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fox;
import org.bukkit.entity.Llama;
import org.bukkit.entity.MushroomCow;
import org.bukkit.entity.Panda;
import org.bukkit.entity.Parrot;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Rabbit;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Wolf;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Variantes des familiers (champ {@code variant} du catalogue) :
 * <ul>
 *   <li>mouton : couleur de laine (pink, light_blue...)</li>
 *   <li>renard : red, snow — lapin : brown, white, black, gold, salt_and_pepper, the_killer_bunny...</li>
 *   <li>perroquet : red, blue, green, cyan, gray — lama : creamy, white, brown, gray</li>
 *   <li>panda : normal, lazy, worried, playful, brown, weak, aggressive — champimeuh : red, brown</li>
 *   <li>chat : tabby, black, red, siamese, british_shorthair, calico, persian, ragdoll, white, jellie, all_black</li>
 *   <li>loup : pale, ashen, black, chestnut, rusty, snowy, spotted, striped, woods</li>
 *   <li>cochon, vache, poule : temperate, warm, cold</li>
 * </ul>
 */
final class Variants {

    private static final Pattern KEY = Pattern.compile("[a-z0-9_]{1,64}");

    private Variants() {
    }

    /** @return true si la variante existe pour cette classe d'entité (validation du catalogue). */
    static boolean isValid(Class<?> entityClass, String variant) {
        String v = normalize(variant);
        if (v == null) {
            return false;
        }
        if (Sheep.class.isAssignableFrom(entityClass)) {
            return enumValue(DyeColor.class, v) != null;
        }
        if (Fox.class.isAssignableFrom(entityClass)) {
            return enumValue(Fox.Type.class, v) != null;
        }
        if (Rabbit.class.isAssignableFrom(entityClass)) {
            return enumValue(Rabbit.Type.class, v) != null;
        }
        if (Parrot.class.isAssignableFrom(entityClass)) {
            return enumValue(Parrot.Variant.class, v) != null;
        }
        if (Llama.class.isAssignableFrom(entityClass)) {
            return enumValue(Llama.Color.class, v) != null;
        }
        if (Panda.class.isAssignableFrom(entityClass)) {
            return enumValue(Panda.Gene.class, v) != null;
        }
        if (MushroomCow.class.isAssignableFrom(entityClass)) {
            return enumValue(MushroomCow.Variant.class, v) != null;
        }
        if (Cat.class.isAssignableFrom(entityClass)) {
            return registry(RegistryKey.CAT_VARIANT, v) != null;
        }
        if (Wolf.class.isAssignableFrom(entityClass)) {
            return registry(RegistryKey.WOLF_VARIANT, v) != null;
        }
        if (Pig.class.isAssignableFrom(entityClass)) {
            return registry(RegistryKey.PIG_VARIANT, v) != null;
        }
        if (Cow.class.isAssignableFrom(entityClass)) {
            return registry(RegistryKey.COW_VARIANT, v) != null;
        }
        if (Chicken.class.isAssignableFrom(entityClass)) {
            return registry(RegistryKey.CHICKEN_VARIANT, v) != null;
        }
        return false;
    }

    /** Applique la variante (déjà validée au chargement du catalogue). */
    static void apply(Entity entity, String variant) {
        String v = normalize(variant);
        if (v == null) {
            return;
        }
        switch (entity) {
            case Sheep sheep -> set(enumValue(DyeColor.class, v), sheep::setColor);
            case Fox fox -> set(enumValue(Fox.Type.class, v), fox::setFoxType);
            case Rabbit rabbit -> set(enumValue(Rabbit.Type.class, v), rabbit::setRabbitType);
            case Parrot parrot -> set(enumValue(Parrot.Variant.class, v), parrot::setVariant);
            case Llama llama -> set(enumValue(Llama.Color.class, v), llama::setColor);
            case Panda panda -> {
                Panda.Gene gene = enumValue(Panda.Gene.class, v);
                if (gene != null) {
                    panda.setMainGene(gene);
                    panda.setHiddenGene(gene); // gène en double : l'apparence est garantie
                }
            }
            case MushroomCow cow -> set(enumValue(MushroomCow.Variant.class, v), cow::setVariant);
            case Cat cat -> set(registry(RegistryKey.CAT_VARIANT, v), cat::setCatType);
            case Wolf wolf -> set(registry(RegistryKey.WOLF_VARIANT, v), wolf::setVariant);
            case Pig pig -> set(registry(RegistryKey.PIG_VARIANT, v), pig::setVariant);
            case Cow cow -> set(registry(RegistryKey.COW_VARIANT, v), cow::setVariant);
            case Chicken chicken -> set(registry(RegistryKey.CHICKEN_VARIANT, v), chicken::setVariant);
            default -> {
            }
        }
    }

    private static <T> void set(T value, java.util.function.Consumer<T> setter) {
        if (value != null) {
            setter.accept(value);
        }
    }

    private static String normalize(String variant) {
        if (variant == null) {
            return null;
        }
        String v = variant.trim().toLowerCase(Locale.ROOT);
        return KEY.matcher(v).matches() ? v : null;
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String name) {
        try {
            return Enum.valueOf(type, name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static <T extends Keyed> T registry(RegistryKey<T> key, String name) {
        return RegistryAccess.registryAccess().getRegistry(key).get(NamespacedKey.minecraft(name));
    }
}
