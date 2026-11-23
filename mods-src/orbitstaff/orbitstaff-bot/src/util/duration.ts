const PATTERN = /^(\d{1,10})(mo|s|m|h|d|w|y)$/;

/**
 * Valide une duree cote bot avant l'appel API (retour immediat a l'utilisateur).
 * Meme regle que le parseur cote serveur : unites s/m/h/d/w/mo/y ou "permanent".
 */
export function isValidDuration(raw: string): boolean {
  const value = raw.trim().toLowerCase();
  if (value === "permanent" || value === "perm") {
    return true;
  }
  const match = PATTERN.exec(value);
  if (match === null) {
    return false;
  }
  return Number(match[1]) > 0;
}
