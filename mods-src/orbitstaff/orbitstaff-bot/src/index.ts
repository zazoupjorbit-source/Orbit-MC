import { Client, Events, GatewayIntentBits, REST, Routes } from "discord.js";
import { OrbitStaffApiClient } from "./api/client";
import { FetchTransport } from "./api/transport";
import { loadConfig } from "./config";
import { commandDefinitions } from "./discord/commands";
import { handleInteraction } from "./discord/router";

/**
 * Demarrage du bot OrbitStaff. Le bot ne parle qu'a l'API (jamais a MariaDB) et
 * n'expose que des commandes slash (aucune commande console arbitraire).
 */
async function main(): Promise<void> {
  const config = loadConfig(process.env);

  const api = new OrbitStaffApiClient({
    baseUrl: config.apiUrl,
    serverId: config.serverId,
    serviceKey: config.serviceKey,
    transport: new FetchTransport(),
  });

  const client = new Client({ intents: [GatewayIntentBits.Guilds] });

  client.once(Events.ClientReady, async (ready) => {
    const rest = new REST().setToken(config.token);
    await rest.put(Routes.applicationGuildCommands(config.appId, config.guildId), { body: commandDefinitions });
    console.log(`OrbitStaff bot connecte : ${ready.user.tag}`);
  });

  client.on(Events.InteractionCreate, async (interaction) => {
    if (!interaction.isChatInputCommand()) {
      return;
    }
    await handleInteraction(interaction, { api, config });
  });

  await client.login(config.token);
}

main().catch((error) => {
  console.error("Echec du demarrage du bot OrbitStaff", error);
  process.exitCode = 1;
});
