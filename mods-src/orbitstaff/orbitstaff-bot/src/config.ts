import { z } from "zod";

/**
 * Configuration du bot, validee via Zod a partir des variables d'environnement.
 * Aucun secret n'est ecrit en dur : tout provient de l'environnement (.env).
 */
const envSchema = z.object({
  DISCORD_BOT_TOKEN: z.string().min(1),
  DISCORD_APP_ID: z.string().min(1),
  DISCORD_GUILD_ID: z.string().min(1),
  ORBITSTAFF_BOT_API_URL: z.string().min(1),
  ORBITSTAFF_BOT_SERVICE_KEY: z.string().min(1),
  ORBITSTAFF_SERVER_ID: z.string().min(1).default("srv-main"),
  ORBITSTAFF_STAFF_ROLE_IDS: z.string().default(""),
  ORBITSTAFF_LOG_CHANNEL_IDS: z.string().default(""),
});

export interface BotConfig {
  token: string;
  appId: string;
  guildId: string;
  apiUrl: string;
  serviceKey: string;
  serverId: string;
  /** Roles Discord autorises a utiliser les commandes de moderation. */
  staffRoleIds: string[];
  /** Salons ou le bot publie ses journaux (optionnel). */
  logChannelIds: string[];
}

/**
 * Analyse et valide l'environnement. Leve une ZodError si une variable
 * obligatoire manque.
 */
export function loadConfig(env: NodeJS.ProcessEnv): BotConfig {
  const parsed = envSchema.parse(env);
  return {
    token: parsed.DISCORD_BOT_TOKEN,
    appId: parsed.DISCORD_APP_ID,
    guildId: parsed.DISCORD_GUILD_ID,
    apiUrl: parsed.ORBITSTAFF_BOT_API_URL,
    serviceKey: parsed.ORBITSTAFF_BOT_SERVICE_KEY,
    serverId: parsed.ORBITSTAFF_SERVER_ID,
    staffRoleIds: splitCsv(parsed.ORBITSTAFF_STAFF_ROLE_IDS),
    logChannelIds: splitCsv(parsed.ORBITSTAFF_LOG_CHANNEL_IDS),
  };
}

function splitCsv(value: string): string[] {
  return value
    .split(",")
    .map((part) => part.trim())
    .filter((part) => part.length > 0);
}
