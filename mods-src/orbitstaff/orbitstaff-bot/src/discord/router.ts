import { randomUUID } from "node:crypto";
import { type APIEmbed, type ChatInputCommandInteraction, MessageFlags } from "discord.js";
import type { OrbitStaffApiClient } from "../api/client";
import { ApiError, ApiUnavailableError } from "../api/errors";
import { authorize, denialMessage, type AuthContext } from "../auth/authorization";
import type { BotConfig } from "../config";
import { errorEmbed, healthEmbed, historyEmbed, infoEmbed, linkEmbed, sanctionCreatedEmbed, type Embed } from "../format/embeds";
import { isValidDuration } from "../util/duration";

export interface Deps {
  api: OrbitStaffApiClient;
  config: BotConfig;
}

const SANCTION_TYPES: Record<string, string> = {
  warn: "WARNING",
  mute: "MUTE",
  kick: "KICK",
  tempban: "TEMPBAN",
  ban: "BAN",
};

/** Point d'entree du routage : autorise puis dispatch. Toute reponse est ephemere. */
export async function handleInteraction(interaction: ChatInputCommandInteraction, deps: Deps): Promise<void> {
  const context: AuthContext = {
    guildId: interaction.guildId,
    channelId: interaction.channelId,
    memberRoleIds: roleIdsOf(interaction),
  };

  const guildDecision = authorize(context, {
    allowedGuildId: deps.config.guildId,
    allowedChannelIds: [],
    staffRoleIds: [],
  });
  if (!guildDecision.allowed) {
    await reply(interaction, errorEmbed(denialMessage(guildDecision.reason)));
    return;
  }

  // Les commandes de moderation et de consultation exigent un role staff.
  if (interaction.commandName === "mod" || interaction.commandName === "player") {
    const staffDecision = authorize(context, {
      allowedGuildId: deps.config.guildId,
      allowedChannelIds: [],
      staffRoleIds: deps.config.staffRoleIds,
    });
    if (!staffDecision.allowed) {
      await reply(interaction, errorEmbed(denialMessage(staffDecision.reason)));
      return;
    }
  }

  try {
    switch (interaction.commandName) {
      case "link":
        await handleLink(interaction, deps);
        break;
      case "player":
        await handlePlayer(interaction, deps);
        break;
      case "mod":
        await handleMod(interaction, deps);
        break;
      case "server":
        await handleServer(interaction, deps);
        break;
      case "staff":
        await reply(interaction, infoEmbed("Statistiques staff a venir."));
        break;
      default:
        await reply(interaction, infoEmbed("Commande inconnue."));
    }
  } catch (error) {
    await reply(interaction, errorEmbed(describeError(error)));
  }
}

async function handleLink(interaction: ChatInputCommandInteraction, deps: Deps): Promise<void> {
  const code = interaction.options.getString("code", true);
  const result = await deps.api.consumeLink(code, interaction.user.id, randomUUID());
  await reply(interaction, linkEmbed(result));
}

async function handlePlayer(interaction: ChatInputCommandInteraction, deps: Deps): Promise<void> {
  const sub = interaction.options.getSubcommand();
  if (sub === "sanctions" || sub === "consulter") {
    const uuid = interaction.options.getString("uuid", true);
    const history = await deps.api.history(uuid);
    await reply(interaction, historyEmbed(uuid, history));
    return;
  }
  await reply(interaction, infoEmbed("Cette sous-commande arrivera dans une prochaine version."));
}

async function handleMod(interaction: ChatInputCommandInteraction, deps: Deps): Promise<void> {
  const sub = interaction.options.getSubcommand();

  if (sub === "dossier") {
    const uuid = interaction.options.getString("uuid", true);
    await reply(interaction, historyEmbed(uuid, await deps.api.history(uuid)));
    return;
  }

  if (sub === "note") {
    const uuid = interaction.options.getString("uuid", true);
    const content = interaction.options.getString("contenu", true);
    const actor = interaction.options.getString("acteur", true);
    await deps.api.createNote({ targetUuid: uuid, authorUuid: actor, content, visibility: "MODERATOR" }, randomUUID());
    await reply(interaction, infoEmbed(`Note ajoutee au dossier de ${uuid}.`));
    return;
  }

  if (sub === "unban") {
    await reply(interaction, infoEmbed("La levee de sanction se fait en jeu (/unban) pour l'instant."));
    return;
  }

  const type = SANCTION_TYPES[sub];
  if (type === undefined) {
    await reply(interaction, infoEmbed("Sous-commande non reconnue."));
    return;
  }

  const uuid = interaction.options.getString("uuid", true);
  const reason = interaction.options.getString("motif", true);
  const actor = interaction.options.getString("acteur", false);
  const withDuration = sub === "mute" || sub === "tempban";
  const duration = withDuration ? interaction.options.getString("duree", true) : null;

  if (duration !== null && !isValidDuration(duration)) {
    await reply(interaction, errorEmbed("Duree invalide. Exemples : 30m, 2h, 7d, permanent."));
    return;
  }

  const sanction = await deps.api.createSanction(
    { targetUuid: uuid, actorUuid: actor, type, reason, duration },
    randomUUID(),
    randomUUID(),
  );
  await reply(interaction, sanctionCreatedEmbed(sanction));
}

async function handleServer(interaction: ChatInputCommandInteraction, deps: Deps): Promise<void> {
  const sub = interaction.options.getSubcommand();
  if (sub === "statut") {
    await reply(interaction, healthEmbed(await deps.api.health()));
    return;
  }
  await reply(interaction, infoEmbed("Cette sous-commande arrivera dans une prochaine version."));
}

function roleIdsOf(interaction: ChatInputCommandInteraction): string[] {
  const member = interaction.member;
  if (member === null) {
    return [];
  }
  const roles = (member as { roles?: unknown }).roles;
  if (Array.isArray(roles)) {
    return roles as string[];
  }
  if (roles && typeof roles === "object" && "cache" in roles) {
    const cache = (roles as { cache: Map<string, unknown> }).cache;
    return [...cache.keys()];
  }
  return [];
}

function describeError(error: unknown): string {
  if (error instanceof ApiUnavailableError) {
    return "Service OrbitStaff momentanement indisponible. Reessayez sous peu.";
  }
  if (error instanceof ApiError) {
    if (error.status === 403) {
      return "Action refusee : permission insuffisante.";
    }
    return error.message;
  }
  return "Une erreur inattendue est survenue.";
}

async function reply(interaction: ChatInputCommandInteraction, embed: Embed): Promise<void> {
  await interaction.reply({ embeds: [embed as APIEmbed], flags: MessageFlags.Ephemeral });
}
