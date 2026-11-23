package games.orbit.orbitstaff.api.application.model;

/** Nature d'un compte de service raccorde a l'API. */
public enum ServiceAccountKind {
    /** Plugin Paper (orbitstaff-core). */
    CORE,
    /** Bot Discord (orbitstaff-bot). */
    BOT,
    /** Futur panel web. */
    PANEL
}
