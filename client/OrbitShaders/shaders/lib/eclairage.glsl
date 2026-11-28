// Orbit Shaders : éclairage d'une surface (soleil ou lune + ciel + torches + main), en couleurs linéaires HDR.
// À inclure après commun.glsl et ombres.glsl.

// albedo : couleur linéaire ; nV : normale (repère caméra) ; lm : lumière bloc (x) et ciel (y) ;
// vue : position (repère caméra) ; sss : 1 pour le feuillage (la lumière traverse) ;
// emissif : 0 à 1.5 (le bloc brille) ; plat : 1 pour les particules (pas de vraie normale).
vec3 eclairer(vec3 albedo, vec3 nV, vec2 lm, vec3 vue, float sss, float emissif, float bruit, float plat) {
    vec3 lightV = normalize(shadowLightPosition);
    vec3 upV = normalize(upPosition);
    float NdotL = dot(nV, lightV);
    float diffus = max(NdotL, 0.0);
    diffus = mix(diffus, 0.55 + 0.45 * abs(NdotL), sss);
    diffus = mix(diffus, 0.6, plat);

    vec3 direct = vec3(0.0);
#ifndef SANS_CIEL
    if (diffus > 0.001) {
        vec3 joueur = versJoueur(vue);
        vec3 nMonde = mat3(gbufferModelViewInverse) * nV;
        vec3 lMonde = mat3(gbufferModelViewInverse) * lightV;
        vec3 nOmbre = (sss > 0.5 || plat > 0.5) ? lMonde : nMonde;
    #ifdef OMBRES
        float ombre = ombreSoleil(joueur, nOmbre, bruit);
    #else
        float ombre = smoothstep(0.80, 0.95, lm.y);
    #endif
        ombre *= clamp(lm.y * 2.5, 0.0, 1.0);   // pas de soleil au fond des grottes, même hors de la carte d'ombre
        direct = lumiereDirecte() * diffus * ombre;
    }
#endif

    float aoCiel = 0.65 + 0.35 * (dot(nV, upV) * 0.5 + 0.5);
#ifdef SANS_CIEL
    vec3 ambiant = lumiereAmbiante() * aoCiel;
#else
    vec3 ambiant = lumiereAmbiante() * lm.y * lm.y * aoCiel;
#endif

    float lumBloc = pow(lm.x, 2.6) * 0.8 + pow(lm.x, 10.0) * 1.1;
#ifdef LUMIERE_EN_MAIN
    float enMain = float(max(heldBlockLightValue, heldBlockLightValue2));
    float m = clamp((enMain - length(vue)) / 15.0, 0.0, 1.0);
    lumBloc = max(lumBloc, pow(m, 2.6) * 0.8 + pow(m, 10.0) * 1.1);
#endif

    vec3 minimum = vec3(0.012, 0.016, 0.028) * (1.0 + nightVision * 8.0);   // le noir des grottes reste bleuté
    vec3 lumiere = direct + ambiant + TORCHE * lumBloc + minimum;
    return albedo * lumiere + albedo * emissif * 2.0;
}
