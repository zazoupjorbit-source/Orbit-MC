package fr.orbit.guns;

import fr.orbit.guns.command.GunCommand;
import fr.orbit.guns.gun.Gun;
import fr.orbit.guns.gun.GunItem;
import fr.orbit.guns.gun.GunRegistry;
import fr.orbit.guns.grenade.Grenades;
import fr.orbit.guns.gun.GunService;
import fr.orbit.guns.listener.GunListener;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** Armes a feu : tir, chargeur, rechargement, munitions. */
public final class OrbitGuns extends JavaPlugin {

    private static GunRegistry sRegistry;
    private static GunItem sGunItem;
    private static Grenades sGrenades;

    private GunService service;

    /**
     * API pour les autres plugins (ex: boutique OrbitWZ) : fabrique l'item d'une arme.
     * @return l'ItemStack de l'arme, ou null si l'id est inconnu / plugin pas pret.
     */
    public static ItemStack buildGun(String id) {
        if (sRegistry == null || sGunItem == null || id == null) return null;
        Gun gun = sRegistry.get(id.toLowerCase());
        return gun == null ? null : sGunItem.build(gun);
    }

    /** API : fabrique un lot de grenades. @return null si id inconnu / plugin pas pret. */
    public static ItemStack buildGrenade(String id, int amount) {
        if (sGrenades == null) return null;
        Grenades.Def def = sGrenades.def(id);
        return def == null ? null : sGrenades.build(def, amount);
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        String prefix = getConfig().getString("messages.prefix", "&8[&cGuns&8] &r");
        GunItem gunItem = new GunItem(new NamespacedKey(this, "gunid"), new NamespacedKey(this, "gunmag"));
        GunRegistry registry = new GunRegistry(this);
        registry.load();
        sRegistry = registry;
        sGunItem = gunItem;
        GunService service = new GunService(this, gunItem, prefix);
        this.service = service;

        Grenades grenades = new Grenades(this, service, new NamespacedKey(this, "grenadeid"));
        grenades.load(getConfig());
        sGrenades = grenades;

        getServer().getPluginManager().registerEvents(new GunListener(registry, gunItem, service), this);
        getServer().getPluginManager().registerEvents(grenades, this);

        Runnable reload = () -> { reloadConfig(); registry.load(); grenades.load(getConfig()); };
        PluginCommand cmd = getCommand("gun");
        if (cmd != null) cmd.setExecutor(new GunCommand(registry, gunItem, grenades, reload, prefix));

        getLogger().info("OrbitGuns actif (" + registry.size() + " armes, " + grenades.size() + " grenades).");
    }

    @Override
    public void onDisable() {
        // rend leur vitesse aux joueurs en pleine visee (le walk speed persiste dans le playerdata)
        if (service != null) service.unaimAll();
    }
}
