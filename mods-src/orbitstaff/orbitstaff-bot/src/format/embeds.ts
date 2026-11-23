import type { Sanction, Health, LinkResult } from "../api/schemas";

/** Champ d'embed. */
export interface EmbedField {
  name: string;
  value: string;
  inline?: boolean;
}

/**
 * Embed sous forme de donnee pure (compatible APIEmbed de discord.js), afin
 * d'etre testable sans dependance a la construction Discord.
 */
export interface Embed {
  title: string;
  description?: string;
  color?: number;
  fields?: EmbedField[];
}

const COLOR_OK = 0x57f287;
const COLOR_ERROR = 0xed4245;
const COLOR_INFO = 0x5865f2;

export function sanctionCreatedEmbed(sanction: Sanction): Embed {
  return {
    title: "Sanction creee",
    color: COLOR_OK,
    fields: [
      { name: "Type", value: sanction.type, inline: true },
      { name: "Statut", value: sanction.status, inline: true },
      { name: "Duree", value: sanction.permanent ? "permanente" : (sanction.expiresAt ?? "-"), inline: true },
      { name: "Motif", value: sanction.reason ?? "-" },
      { name: "Dossier", value: sanction.id ?? "-" },
    ],
  };
}

export function historyEmbed(uuid: string, sanctions: Sanction[]): Embed {
  if (sanctions.length === 0) {
    return { title: `Dossier de ${uuid}`, color: COLOR_INFO, description: "Aucune entree de moderation." };
  }
  return {
    title: `Dossier de ${uuid}`,
    color: COLOR_INFO,
    description: `${sanctions.length} entree(s)`,
    fields: sanctions.slice(0, 15).map((s, index) => ({
      name: `#${index + 1} - ${s.type} [${s.status}]`,
      value: s.reason ?? "(sans motif)",
    })),
  };
}

export function linkEmbed(result: LinkResult): Embed {
  return {
    title: "Compte lie",
    color: COLOR_OK,
    description: `Votre compte Discord est desormais lie au joueur ${result.playerUuid}.`,
  };
}

export function healthEmbed(health: Health): Embed {
  return {
    title: "Statut du service",
    color: health.status === "UP" ? COLOR_OK : COLOR_ERROR,
    fields: [{ name: "API", value: health.status, inline: true }],
  };
}

export function infoEmbed(message: string): Embed {
  return { title: "OrbitStaff", color: COLOR_INFO, description: message };
}

export function errorEmbed(message: string): Embed {
  return { title: "Erreur", color: COLOR_ERROR, description: message };
}
