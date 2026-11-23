import { describe, expect, it } from "vitest";
import { isValidDuration } from "../src/util/duration";

describe("isValidDuration", () => {
  it("accepte les durees valides et permanent", () => {
    for (const valid of ["30m", "2h", "7d", "2w", "1mo", "1y", "permanent", "PERM"]) {
      expect(isValidDuration(valid)).toBe(true);
    }
  });

  it("rejette les durees invalides", () => {
    for (const invalid of ["", "abc", "0m", "5x", "5", "mo", "10.5h"]) {
      expect(isValidDuration(invalid)).toBe(false);
    }
  });
});
