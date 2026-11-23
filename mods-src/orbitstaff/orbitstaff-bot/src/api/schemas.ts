import { z } from "zod";

/** Schemas Zod des reponses de l'API OrbitStaff (validation a la reception). */

export const sanctionSchema = z.object({
  id: z.string().optional(),
  serverId: z.string().optional(),
  targetUuid: z.string().optional(),
  actorUuid: z.string().nullable().optional(),
  type: z.string(),
  status: z.string(),
  reason: z.string().optional(),
  severity: z.string().optional(),
  createdAt: z.string().optional(),
  startsAt: z.string().optional(),
  expiresAt: z.string().nullable().optional(),
  permanent: z.boolean().optional(),
});
export type Sanction = z.infer<typeof sanctionSchema>;

export const historySchema = z.array(sanctionSchema);

export const noteSchema = z.object({
  id: z.string().optional(),
  targetUuid: z.string().optional(),
  authorUuid: z.string().optional(),
  content: z.string(),
  visibility: z.string().optional(),
  createdAt: z.string().optional(),
});
export type Note = z.infer<typeof noteSchema>;

export const linkResultSchema = z.object({
  playerUuid: z.string(),
  linked: z.boolean(),
});
export type LinkResult = z.infer<typeof linkResultSchema>;

export const healthSchema = z.object({
  status: z.string(),
  service: z.string().optional(),
});
export type Health = z.infer<typeof healthSchema>;

export const apiErrorSchema = z.object({
  error: z.string(),
  message: z.string().optional(),
});
