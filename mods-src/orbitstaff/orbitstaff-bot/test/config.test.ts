import { describe, expect, it } from "vitest";
import { loadConfig } from "../src/config";

const validEnv = {
  DISCORD_BOT_TOKEN: "token",
  DISCORD_APP_ID: "app",
  DISCORD_GUILD_ID: "guild",
  ORBITSTAFF_BOT_API_URL: "http://localhost:8085",
  ORBITSTAFF_BOT_SERVICE_KEY: "oss_key",
  ORBITSTAFF_STAFF_ROLE_IDS: "role-1, role-2",
} as unknown as NodeJS.ProcessEnv;

describe("loadConfig", () => {
  it("valide et decompose la configuration", () => {
    const config = loadConfig(validEnv);
    expect(config.token).toBe("token");
    expect(config.serverId).toBe("srv-main");
    expect(config.staffRoleIds).toEqual(["role-1", "role-2"]);
    expect(config.logChannelIds).toEqual([]);
  });

  it("rejette un environnement incomplet", () => {
    const incomplete = { DISCORD_APP_ID: "app" } as unknown as NodeJS.ProcessEnv;
    expect(() => loadConfig(incomplete)).toThrow();
  });
});
