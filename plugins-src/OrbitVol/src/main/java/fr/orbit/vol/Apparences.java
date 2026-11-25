package fr.orbit.vol;

import org.bukkit.DyeColor;
import org.bukkit.entity.Axolotl;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Fox;
import org.bukkit.entity.Frog;
import org.bukkit.entity.Mob;
import org.bukkit.entity.MushroomCow;
import org.bukkit.entity.Panda;
import org.bukkit.entity.Parrot;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Rabbit;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Wolf;

/**
 * Fixe la variante de chaque bête pour qu'elle porte la texture HD dessinée pour son Orbito
 * (le pack remplace une seule texture par bête : poule tempérée = Dodo Turbo, poule chaude = Dodo Roi, etc.).
 */
final class Apparences {

    private Apparences() {
    }

    static void appliquer(Mob mob, Catalogue.Orbito o) {
        String id = o.id();
        if (mob instanceof Chicken c) c.setVariant(id.equals("dodo_roi") ? Chicken.Variant.WARM : Chicken.Variant.TEMPERATE);
        else if (mob instanceof Frog f) f.setVariant(id.equals("grenouille_supernova") ? Frog.Variant.WARM : Frog.Variant.TEMPERATE);
        else if (mob instanceof Pig p) p.setVariant(Pig.Variant.TEMPERATE);
        else if (mob instanceof Sheep s) s.setColor(DyeColor.WHITE);
        else if (mob instanceof Rabbit r) r.setRabbitType(Rabbit.Type.BROWN);
        else if (mob instanceof Axolotl a) a.setVariant(Axolotl.Variant.LUCY);
        else if (mob instanceof Parrot p) p.setVariant(Parrot.Variant.RED);
        else if (mob instanceof Cat c) c.setCatType(Cat.Type.ALL_BLACK);
        else if (mob instanceof Fox f) f.setFoxType(Fox.Type.RED);
        else if (mob instanceof Wolf w) w.setVariant(Wolf.Variant.PALE);
        else if (mob instanceof Panda p) { p.setMainGene(Panda.Gene.NORMAL); p.setHiddenGene(Panda.Gene.NORMAL); }
        else if (mob instanceof MushroomCow m) m.setVariant(MushroomCow.Variant.RED);
    }
}
