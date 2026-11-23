import { describe, expect, it } from "vitest";
import { historyEmbed, sanctionCreatedEmbed } from "../src/format/embeds";
import type { Sanction } from "../src/api/schemas";

describe("embeds", () => {
  it("resume une sanction creee", () => {
    const sanction: Sanction = { id: "OS-1", type: "BAN", status: "ACTIVE", reason: "Triche", permanent: true };
    const embed = sanctionCreatedEmbed(sanction);
    expect(embed.title).toBe("Sanction creee");
    const durationField = embed.fields?.find((f) => f.name === "Duree");
    expect(durationField?.value).toBe("permanente");
  });

  it("affiche un dossier vide", () => {
    const embed = historyEmbed("uuid", []);
    expect(embed.description).toContain("Aucune");
    expect(embed.fields).toBeUndefined();
  });

  it("liste les entrees d'un dossier", () => {
    const list: Sanction[] = [
      { type: "WARNING", status: "ACTIVE", reason: "Spam" },
      { type: "MUTE", status: "EXPIRED", reason: "Insultes" },
    ];
    const embed = historyEmbed("uuid", list);
    expect(embed.description).toContain("2");
    expect(embed.fields).toHaveLength(2);
  });
});
