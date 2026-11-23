package fr.naruto.core.jutsu;

import fr.naruto.core.ninja.Element;
import org.bukkit.Material;

import java.util.List;

/** Une technique. Definit son cout, son cooldown, son element et son effet. */
public interface Jutsu {

    String id();
    String displayName();
    Element element();
    int chakraCost();
    long cooldownMs();
    int minTier();
    Material icon();
    List<String> lore();

    void cast(CastContext ctx);
}
