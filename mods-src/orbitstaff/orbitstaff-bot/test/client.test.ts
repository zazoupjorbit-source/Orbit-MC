import { describe, expect, it } from "vitest";
import { OrbitStaffApiClient, type CreateSanctionInput } from "../src/api/client";
import { ApiError, ApiUnavailableError } from "../src/api/errors";
import type { HttpRequest, HttpResponse, HttpTransport } from "../src/api/transport";

class FakeTransport implements HttpTransport {
  last?: HttpRequest;
  response: HttpResponse = { status: 200, body: "{}" };
  fail = false;

  async send(request: HttpRequest): Promise<HttpResponse> {
    this.last = request;
    if (this.fail) {
      throw new Error("coupure simulee");
    }
    return this.response;
  }
}

function client(transport: FakeTransport): OrbitStaffApiClient {
  return new OrbitStaffApiClient({
    baseUrl: "http://localhost:8085/",
    serverId: "srv-main",
    serviceKey: "oss_secret",
    transport,
  });
}

describe("OrbitStaffApiClient", () => {
  it("construit une requete de sanction authentifiee et idempotente", async () => {
    const transport = new FakeTransport();
    transport.response = {
      status: 201,
      body: JSON.stringify({ id: "case-1", type: "MUTE", status: "ACTIVE", reason: "Spam", permanent: false }),
    };
    const input: CreateSanctionInput = { targetUuid: "target-uuid", type: "MUTE", reason: "Spam", duration: "30m" };

    const sanction = await client(transport).createSanction(input, "idem-key", "corr-id");

    expect(sanction.type).toBe("MUTE");
    expect(transport.last?.method).toBe("POST");
    expect(transport.last?.url.endsWith("/api/v1/sanctions")).toBe(true);
    expect(transport.last?.url.includes("//api")).toBe(false);
    expect(transport.last?.headers["X-OrbitStaff-Service-Key"]).toBe("oss_secret");
    expect(transport.last?.headers["X-OrbitStaff-Server-Id"]).toBe("srv-main");
    expect(transport.last?.headers["X-OrbitStaff-Idempotency-Key"]).toBe("idem-key");
    expect(transport.last?.headers["X-OrbitStaff-Correlation-Id"]).toBe("corr-id");
    expect(transport.last?.body).toContain("30m");
  });

  it("analyse un tableau d'historique", async () => {
    const transport = new FakeTransport();
    transport.response = {
      status: 200,
      body: JSON.stringify([
        { type: "WARNING", status: "ACTIVE" },
        { type: "BAN", status: "REVOKED" },
      ]),
    };

    const history = await client(transport).history("player-uuid");

    expect(history).toHaveLength(2);
    expect(transport.last?.method).toBe("GET");
    expect(transport.last?.url.includes("/api/v1/sanctions/history?player=")).toBe(true);
  });

  it("traduit une erreur metier en ApiError", async () => {
    const transport = new FakeTransport();
    transport.response = { status: 403, body: JSON.stringify({ error: "PERMISSION_DENIED", message: "refuse" }) };

    await expect(client(transport).history("u")).rejects.toBeInstanceOf(ApiError);
    try {
      await client(transport).history("u");
    } catch (error) {
      expect(error).toBeInstanceOf(ApiError);
      expect((error as ApiError).status).toBe(403);
      expect((error as ApiError).code).toBe("PERMISSION_DENIED");
    }
  });

  it("traduit une coupure reseau en ApiUnavailableError", async () => {
    const transport = new FakeTransport();
    transport.fail = true;
    await expect(client(transport).history("u")).rejects.toBeInstanceOf(ApiUnavailableError);
  });
});
