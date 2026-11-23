/**
 * Verification d'autorisation d'une interaction Discord (logique pure, testable).
 *
 * Controle : serveur Discord autorise, salon autorise (optionnel), role staff.
 * La permission metier fine reste verifiee cote API a chaque action.
 */
export interface AuthContext {
  guildId: string | null;
  channelId: string | null;
  memberRoleIds: string[];
}

export interface AuthPolicy {
  allowedGuildId: string;
  /** Vide = tous les salons autorises. */
  allowedChannelIds: string[];
  /** Vide = aucun filtrage par role (a eviter en production). */
  staffRoleIds: string[];
}

export type AuthDecision =
  | { allowed: true }
  | { allowed: false; reason: "guild" | "channel" | "role" };

export function authorize(context: AuthContext, policy: AuthPolicy): AuthDecision {
  if (context.guildId !== policy.allowedGuildId) {
    return { allowed: false, reason: "guild" };
  }
  if (policy.allowedChannelIds.length > 0) {
    if (context.channelId === null || !policy.allowedChannelIds.includes(context.channelId)) {
      return { allowed: false, reason: "channel" };
    }
  }
  if (policy.staffRoleIds.length > 0) {
    const hasStaffRole = context.memberRoleIds.some((id) => policy.staffRoleIds.includes(id));
    if (!hasStaffRole) {
      return { allowed: false, reason: "role" };
    }
  }
  return { allowed: true };
}

/** Message utilisateur associe a un refus. */
export function denialMessage(reason: "guild" | "channel" | "role"): string {
  switch (reason) {
    case "guild":
      return "Ce serveur Discord n'est pas autorise.";
    case "channel":
      return "Cette commande n'est pas autorisee dans ce salon.";
    case "role":
      return "Vous n'avez pas le role staff requis.";
  }
}
