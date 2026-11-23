package fr.naruto.core;

import fr.naruto.core.command.ChakraCommand;
import fr.naruto.core.command.JutsuCommand;
import fr.naruto.core.command.NarutoAdminCommand;
import fr.naruto.core.command.NinjaCommand;
import fr.naruto.core.db.AsyncExecutor;
import fr.naruto.core.db.Storage;
import fr.naruto.core.gui.GuiListener;
import fr.naruto.core.jutsu.CastItemListener;
import fr.naruto.core.jutsu.JutsuLibrary;
import fr.naruto.core.jutsu.JutsuService;
import fr.naruto.core.jutsu.Keys;
import fr.naruto.core.listener.JoinListener;
import fr.naruto.core.ninja.NinjaService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Point d'entree NarutoCore : chakra + jutsu (Naruto RP). */
public final class NarutoCore extends JavaPlugin {

    private AsyncExecutor async;
    private Storage storage;
    private NinjaService ninjas;
    private JutsuService jutsus;

    @Override
    public void onEnable() {
        Keys.init(this);
        this.async = new AsyncExecutor(this);
        this.storage = new Storage(getDataFolder(), getLogger());
        this.ninjas = new NinjaService(this, async, storage);
        this.jutsus = new JutsuService(this, ninjas);

        JutsuLibrary.registerAll(jutsus);

        getServer().getPluginManager().registerEvents(new GuiListener(ninjas, jutsus), this);
        getServer().getPluginManager().registerEvents(new JoinListener(ninjas, this), this);
        getServer().getPluginManager().registerEvents(new CastItemListener(jutsus), this);

        getCommand("ninja").setExecutor(new NinjaCommand(ninjas));
        getCommand("jutsu").setExecutor(new JutsuCommand(ninjas, jutsus));
        getCommand("chakra").setExecutor(new ChakraCommand(ninjas));
        getCommand("narutoadmin").setExecutor(new NarutoAdminCommand(ninjas));

        getServer().getScheduler().runTaskTimer(this, ninjas::regenTick, 20L, 20L);

        for (Player p : Bukkit.getOnlinePlayers()) ninjas.load(p);
        getLogger().info("NarutoCore actif — " + jutsus.all().size() + " jutsu prets. Dattebayo !");
    }

    @Override
    public void onDisable() {
        if (ninjas != null) {
            for (Player p : Bukkit.getOnlinePlayers()) ninjas.unload(p);
        }
        if (async != null) async.shutdown();
        if (storage != null) storage.close();
    }
}
