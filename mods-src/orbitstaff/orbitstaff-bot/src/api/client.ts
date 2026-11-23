import { z } from "zod";
import { ApiError, ApiUnavailableError } from "./errors";
import {
  apiErrorSchema,
  healthSchema,
  historySchema,
  linkResultSchema,
  noteSchema,
  sanctionSchema,
  type Health,
  type LinkResult,
  type Note,
  type Sanction,
} from "./schemas";
import type { HttpResponse, HttpTransport } from "./transport";

const HEADER_SERVICE_KEY = "X-OrbitStaff-Service-Key";
const HEADER_SERVER_ID = "X-OrbitStaff-Server-Id";
const HEADER_CORRELATION_ID = "X-OrbitStaff-Correlation-Id";
const HEADER_IDEMPOTENCY_KEY = "X-OrbitStaff-Idempotency-Key";
const JSON_TYPE = "application/json";

export interface ApiClientOptions {
  baseUrl: string;
  serverId: string;
  serviceKey: string;
  transport: HttpTransport;
}

export interface CreateSanctionInput {
  targetUuid: string;
  actorUuid?: string | null;
  type: string;
  reason: string;
  duration?: string | null;
}

export interface CreateNoteInput {
  targetUuid: string;
  authorUuid: string;
  content: string;
  visibility: string;
}

/**
 * Client HTTP du bot vers l'API OrbitStaff.
 *
 * Le bot ne se connecte JAMAIS a MariaDB : il passe exclusivement par l'API,
 * authentifie par cle de service. Les reponses sont validees par Zod.
 */
export class OrbitStaffApiClient {
  private readonly baseUrl: string;

  constructor(private readonly opts: ApiClientOptions) {
    this.baseUrl = opts.baseUrl.replace(/\/+$/, "");
  }

  async history(uuid: string): Promise<Sanction[]> {
    const res = await this.send("GET", `/api/v1/sanctions/history?player=${encodeURIComponent(uuid)}`);
    return this.parse(res, historySchema);
  }

  async createSanction(
    input: CreateSanctionInput,
    idempotencyKey: string,
    correlationId?: string,
  ): Promise<Sanction> {
    const res = await this.send("POST", "/api/v1/sanctions", input, { [HEADER_IDEMPOTENCY_KEY]: idempotencyKey }, correlationId);
    return this.parse(res, sanctionSchema);
  }

  async createNote(input: CreateNoteInput, correlationId?: string): Promise<Note> {
    const res = await this.send("POST", "/api/v1/notes", input, {}, correlationId);
    return this.parse(res, noteSchema);
  }

  async consumeLink(code: string, discordUserId: string, correlationId?: string): Promise<LinkResult> {
    const res = await this.send("POST", "/api/v1/discord/link/consume", { code, discordUserId }, {}, correlationId);
    return this.parse(res, linkResultSchema);
  }

  async health(): Promise<Health> {
    const res = await this.send("GET", "/health");
    return this.parse(res, healthSchema);
  }

  private buildHeaders(extra: Record<string, string>, correlationId?: string): Record<string, string> {
    const headers: Record<string, string> = {
      [HEADER_SERVICE_KEY]: this.opts.serviceKey,
      [HEADER_SERVER_ID]: this.opts.serverId,
      Accept: JSON_TYPE,
      ...extra,
    };
    if (correlationId) {
      headers[HEADER_CORRELATION_ID] = correlationId;
    }
    return headers;
  }

  private async send(
    method: string,
    path: string,
    body?: unknown,
    extraHeaders: Record<string, string> = {},
    correlationId?: string,
  ): Promise<HttpResponse> {
    const headers = this.buildHeaders(extraHeaders, correlationId);
    let bodyStr: string | undefined;
    if (body !== undefined) {
      headers["Content-Type"] = JSON_TYPE;
      bodyStr = JSON.stringify(body);
    }
    try {
      return await this.opts.transport.send({ method, url: this.baseUrl + path, headers, body: bodyStr });
    } catch (cause) {
      throw new ApiUnavailableError(cause);
    }
  }

  private parse<T>(res: HttpResponse, schema: z.ZodType<T>): T {
    if (res.status >= 200 && res.status < 300) {
      return schema.parse(JSON.parse(res.body));
    }
    let code = `HTTP_${res.status}`;
    let message: string | undefined;
    try {
      const parsed = apiErrorSchema.parse(JSON.parse(res.body));
      code = parsed.error;
      message = parsed.message;
    } catch {
      // corps non-JSON : on garde le code generique
    }
    throw new ApiError(res.status, code, message);
  }
}
