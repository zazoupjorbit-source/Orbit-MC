package fr.orbit.extraction.hud;

/**
 * Action en cours affichée dans la barre de progression de la console (extraction, soin, rechargement).
 *
 * @param label nom affiché sur la ligne de texte (MiniMessage)
 * @param ratio avancement de 0 à 1
 * @param left  secondes restantes
 * @param hex   couleur du remplissage (« #2ECC71 »)
 */
public record Cast(String label, double ratio, double left, String hex) {
}
