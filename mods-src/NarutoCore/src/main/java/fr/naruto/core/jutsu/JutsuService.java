package fr.naruto.core.jutsu;

import fr.naruto.core.ninja.Element;
import fr.naruto.core.ninja.Ninja;
import fr.naruto.core.ninja.NinjaService;
import fr.naruto.core.text.Text;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Registre des jutsu + logique de cast (verifie element, rang, cooldown, chakra). */
public final class JutsuService {

    private final Map<String, Jutsu> jutsu = new LinkedHashMap<>();
    private final CooldownManager cooldowns = new CooldownManager();
    private final Plugin plugin;
    private final NinjaService ninjas;

    public JutsuService(Plugin plugin, NinjaService ninjas) {
        this.plugin = plugin;
        this.ninjas = ninjas;
    }

    public void register(Jutsu j) { jutsu.put(j.id(), j); }
    public Jutsu get(String id) { return jutsu.get(id); }
    public Collection<Jutsu> all() { return jutsu.values(); }
    public CooldownManager cooldowns() { return cooldowns; }

    public List<Jutsu> availableFor(Ninja n) {
        List<Jutsu> list = new ArrayList<>();
        for (Jutsu j : jutsu.values()) {
            boolean elementOk = j.element() == Element.NONE || j.element() == n.element();
            if (elementOk && n.rank().tier() >= j.minTier()) list.add(j);
        }
        return list;
    }

    public boolean tryCast(Player player, String id) {
        Ninja n = ninjas.get(player.getUniqueId());
        if (n == null) return false;
        Jutsu j = get(id);
        if (j == null) return false;

        if (j.element() != Element.NONE && j.element() != n.element()) {
            player.sendMessage(Text.msg("<red>Ce jutsu demande l'affinite <white>" + j.element().displayName()));
            return false;
        }
        if (n.rank().tier() < j.minTier()) {
            player.sendMessage(Text.msg("<red>Rang insuffisant pour ce jutsu."));
            return false;
        }
        long cd = cooldowns.remainingMs(player.getUniqueId(), id);
        if (cd > 0) {
            player.sendMessage(Text.msg("<red>Jutsu en recharge (" + Math.ceil(cd / 1000.0) + "s)."));
            return false;
        }
        if (!n.hasChakra(j.chakraCost())) {
            player.sendMessage(Text.msg("<red>Chakra insuffisant (" + j.chakraCost() + " requis)."));
            return false;
        }

        n.spend(j.chakraCost());
        cooldowns.set(player.getUniqueId(), id, j.cooldownMs());
        ninjas.updateBar(player, n);
        try {
            j.cast(new CastContext(player, n, plugin, ninjas));
        } catch (Exception e) {
            plugin.getLogger().warning("Erreur cast jutsu " + id + " : " + e.getMessage());
        }
        return true;
    }
}
