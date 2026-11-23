import { describe, expect, it } from "vitest";
import { authorize, type AuthPolicy } from "../src/auth/authorization";

const policy: AuthPolicy = {
  allowedGuildId: "guild-1",
  allowedChannelIds: ["chan-1"],
  staffRoleIds: ["role-staff"],
};

describe("authorize", () => {
  it("autorise un membre staff dans le bon serveur et salon", () => {
    const decision = authorize(
      { guildId: "guild-1", channelId: "chan-1", memberRoleIds: ["role-staff", "role-x"] },
      policy,
    );
    expect(decision.allowed).toBe(true);
  });

  it("refuse un autre serveur", () => {
    const decision = authorize({ guildId: "autre", channelId: "chan-1", memberRoleIds: ["role-staff"] }, policy);
    expect(decision).toEqual({ allowed: false, reason: "guild" });
  });

  it("refuse un salon non autorise", () => {
    const decision = authorize({ guildId: "guild-1", channelId: "chan-2", memberRoleIds: ["role-staff"] }, policy);
    expect(decision).toEqual({ allowed: false, reason: "channel" });
  });

  it("refuse un membre sans role staff", () => {
    const decision = authorize({ guildId: "guild-1", channelId: "chan-1", memberRoleIds: ["role-x"] }, policy);
    expect(decision).toEqual({ allowed: false, reason: "role" });
  });

  it("sans filtrage de role ni de salon, seul le serveur compte", () => {
    const open: AuthPolicy = { allowedGuildId: "guild-1", allowedChannelIds: [], staffRoleIds: [] };
    expect(authorize({ guildId: "guild-1", channelId: null, memberRoleIds: [] }, open).allowed).toBe(true);
    expect(authorize({ guildId: "autre", channelId: null, memberRoleIds: [] }, open).allowed).toBe(false);
  });
});
