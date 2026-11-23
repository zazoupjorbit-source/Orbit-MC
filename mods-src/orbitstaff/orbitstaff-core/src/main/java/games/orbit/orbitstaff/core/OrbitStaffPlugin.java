package games.orbit.orbitstaff.core;

import com.google.gson.Gson;
import games.orbit.orbitstaff.core.api.ApiExecutor;
import games.orbit.orbitstaff.core.api.OrbitStaffApiClient;
import games.orbit.orbitstaff.core.command.DiscordLinkCommand;
import games.orbit.orbitstaff.core.command.FreezeCommand;
import games.orbit.orbitstaff.core.command.HistoryCommand;
import games.orbit.orbitstaff.core.command.NoteCommand;
import games.orbit.orbitstaff.core.command.OrbitStaffCommand;
import games.orbit.orbitstaff.core.command.PlayerInfoCommand;
import games.orbit.orbitstaff.core.command.SanctionCommand;
import games.orbit.orbitstaff.core.command.StaffCommand;
import games.orbit.orbitstaff.core.config.OrbitStaffConfig;
import games.orbit.orbitstaff.core.freeze.FreezeListener;
import games.orbit.orbitstaff.core.freeze.FreezeManager;
import games.orbit.orbitstaff.core.i18n.Messages;
import games.orbit.orbitstaff.core.inspect.InspectionListener;
import games.orbit.orbitstaff.core.inspect.InspectionService;
import games.orbit.orbitstaff.core.listener.PlayerConnectionListener;
import games.orbit.orbitstaff.core.listener.StaffProtectionListener;
import games.orbit.orbitstaff.core.net.JdkHttpTransport;
import games.orbit.orbitstaff.core.staff.HotbarLayout;
import games.orbit.orbitstaff.core.staff.StaffModeManager;
import games.orbit.orbitstaff.core.staff.StaffStateStore;
import games.orbit.orbitstaff.core.staff.StaffToolListener;
import games.orbit.orbitstaff.integrations.BukkitPluginPresence;
import games.orbit.orbitstaff.integrations.IntegrationBootstrap;
import games.orbit.orbitstaff.integrations.IntegrationManager;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/**
 * Point d'entree du plugin OrbitStaff (Paper 1.21.1).
 *
 * <p>Il cable les services, enregistre commandes et listeners, et gere le cycle
 * de vie. Toute la logique metier reste dans l'API ; le plugin ne fait qu'adapter
 * l'entree/sortie en jeu et communiquer en HTTPS avec l'API.</p>
 */
public final class OrbitStaffPlugin extends JavaPlugin {

    private OrbitStaffConfig config;
    private Messages messages;
    private StaffModeManager staffMode;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        for (String resource : new String[]{
                "messages_fr.yml", "messages_en.yml", "sanctions.yml", "staff-items.yml", "retention.yml"}) {
            saveResourceIfAbsent(resource);
        }

        this.config = OrbitStaffConfig.from(getConfig());
        this.messages = new Messages(getDataFolder(), config.language());

        if (!config.hasServiceKey()) {
            getLogger().warning("Cle de service absente : definir la variable d'environnement du plugin. "
                    + "Le plugin demarre en mode limite (les appels API echoueront proprement).");
        }

        OrbitStaffApiClient apiClient = new OrbitStaffApiClient(
                config.apiBaseUrl(), config.serverId(), config.serviceKey(),
                new JdkHttpTransport(config.connectTimeoutMs(), config.readTimeoutMs()), new Gson());
        ApiExecutor apiExecutor = new ApiExecutor(this);

        this.staffMode = new StaffModeManager(this, messages,
                new StaffStateStore(getDataFolder()), HotbarLayout.defaultLayout());
        FreezeManager freeze = new FreezeManager(messages);
        InspectionService inspection = new InspectionService();

        // Integrations optionnelles : detection + cablage des adaptateurs presents.
        IntegrationManager integrations = new IntegrationManager(new BukkitPluginPresence());
        IntegrationBootstrap.wire(integrations, getServer());
        integrations.report().forEach((type, availability) ->
                getLogger().info("Integration " + type + " : " + availability));

        OrbitStaffServices services = new OrbitStaffServices(
                this, config, messages, apiClient, apiExecutor, staffMode, freeze, inspection, integrations);

        registerListeners(new FreezeListener(freeze),
                new PlayerConnectionListener(staffMode, freeze, messages),
                new StaffProtectionListener(staffMode),
                new InspectionListener(),
                new StaffToolListener(services));

        registerCommands(services);

        getLogger().info("OrbitStaff active (serveur=" + config.serverId() + ").");
    }

    @Override
    public void onDisable() {
        if (staffMode != null) {
            // Ramene les membres du staff en ligne a leur etat normal.
            staffMode.restoreAllOnDisable();
        }
    }

    /** Recharge uniquement les elements surs (messages), sans re-cabler le plugin. */
    public void reloadSafe() {
        reloadConfig();
        OrbitStaffConfig fresh = OrbitStaffConfig.from(getConfig());
        messages.reload(getDataFolder(), fresh.language());
    }

    private void registerListeners(Listener... listeners) {
        for (Listener listener : listeners) {
            getServer().getPluginManager().registerEvents(listener, this);
        }
    }

    private void registerCommands(OrbitStaffServices services) {
        SanctionCommand sanctionCommand = new SanctionCommand(services);
        FreezeCommand freezeCommand = new FreezeCommand(services);

        bind("staff", new StaffCommand(services));
        for (String name : new String[]{"warn", "mute", "kick", "tempban", "ban", "unban"}) {
            bind(name, sanctionCommand);
        }
        bind("freeze", freezeCommand);
        bind("unfreeze", freezeCommand);
        bind("note", new NoteCommand(services));
        bind("history", new HistoryCommand(services));
        PlayerInfoCommand playerInfo = new PlayerInfoCommand(services);
        bind("playerinfo", playerInfo);
        bind("inspect", playerInfo);
        bind("discord", new DiscordLinkCommand(services));
        bind("orbitstaff", new OrbitStaffCommand(this, services));
    }

    private void bind(String name, CommandExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().warning("Commande absente de plugin.yml : " + name);
            return;
        }
        command.setExecutor(executor);
    }

    private void saveResourceIfAbsent(String name) {
        if (!new File(getDataFolder(), name).exists()) {
            saveResource(name, false);
        }
    }
}
