package fr.naruto.core.jutsu;

import fr.naruto.core.ninja.Ninja;
import fr.naruto.core.ninja.NinjaService;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.RayTraceResult;

/** Contexte passe a un jutsu lors du cast (lanceur + utilitaires de visee). */
public final class CastContext {

    public final Player player;
    public final Ninja ninja;
    public final Plugin plugin;
    public final NinjaService service;

    public CastContext(Player player, Ninja ninja, Plugin plugin, NinjaService service) {
        this.player = player;
        this.ninja = ninja;
        this.plugin = plugin;
        this.service = service;
    }

    public LivingEntity targetEntity(double range) {
        RayTraceResult r = player.getWorld().rayTraceEntities(
                player.getEyeLocation(), player.getEyeLocation().getDirection(), range, 0.5,
                e -> e instanceof LivingEntity && !e.equals(player));
        if (r != null && r.getHitEntity() instanceof LivingEntity le) return le;
        return null;
    }

    public Location targetPoint(double range) {
        RayTraceResult r = player.rayTraceBlocks(range);
        if (r != null && r.getHitPosition() != null) {
            return r.getHitPosition().toLocation(player.getWorld());
        }
        return player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(range));
    }
}
