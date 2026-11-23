package fr.naruto.core.jutsu;

import fr.naruto.core.ninja.Element;
import org.bukkit.Material;

import java.util.List;
import java.util.function.Consumer;

/** Jutsu defini par des donnees + un effet (lambda). Permet de creer un gros roster sans une classe par technique. */
public final class SimpleJutsu implements Jutsu {

    private final String id, name;
    private final Element element;
    private final int cost, tier;
    private final long cd;
    private final Material icon;
    private final List<String> lore;
    private final Consumer<CastContext> effect;

    public SimpleJutsu(String id, String name, Element element, int cost, long cd, int tier,
                       Material icon, String lore, Consumer<CastContext> effect) {
        this.id = id;
        this.name = name;
        this.element = element;
        this.cost = cost;
        this.cd = cd;
        this.tier = tier;
        this.icon = icon;
        this.lore = List.of(lore);
        this.effect = effect;
    }

    public String id() { return id; }
    public String displayName() { return name; }
    public Element element() { return element; }
    public int chakraCost() { return cost; }
    public long cooldownMs() { return cd; }
    public int minTier() { return tier; }
    public Material icon() { return icon; }
    public List<String> lore() { return lore; }

    public void cast(CastContext ctx) { effect.accept(ctx); }
}
