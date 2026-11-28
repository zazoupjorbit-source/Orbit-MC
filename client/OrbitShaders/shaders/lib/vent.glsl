// Orbit Shaders : végétation au vent (identifiants de block.properties).
// À inclure après commun.glsl, dans un vertex shader.

// Part du mouvement selon le bloc ; haut : le sommet de la texture (le pied d'une herbe reste planté).
float forceVent(int bloc, bool haut) {
    if (bloc == 10001) return haut ? 1.0 : 0.0;   // plante basse
    if (bloc == 10002) return haut ? 0.6 : 0.0;   // bas d'une grande plante
    if (bloc == 10003) return haut ? 1.4 : 0.6;   // haut d'une grande plante
    if (bloc == 10004) return 0.35;               // feuilles
    if (bloc == 10005) return 0.5;                // lianes
    return 0.0;
}

// Décalage (en blocs) d'un point du monde.
vec3 vent(vec3 monde) {
    float t = frameTimeCounter;
    float w = sin(t * 1.7 + monde.x * 0.6 + monde.z * 0.4) * 0.6 + sin(t * 2.9 + monde.z * 1.1 - monde.x * 0.3) * 0.4;
    float v = sin(t * 2.3 + monde.x * 0.9 + monde.y * 0.5) * 0.5;
    return vec3(w, 0.0, v) * 0.06 * VENT_FORCE * (1.0 + rainStrength);
}
