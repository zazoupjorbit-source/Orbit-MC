/** L'API a repondu par une erreur metier (statut non 2xx). */
export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message?: string,
  ) {
    super(message ?? code);
    this.name = "ApiError";
  }
}

/** L'API est injoignable (coupure reseau, timeout). */
export class ApiUnavailableError extends Error {
  constructor(public readonly cause?: unknown) {
    super("API OrbitStaff injoignable");
    this.name = "ApiUnavailableError";
  }
}
