// Orbit Shaders : lecture de la carte d'ombre (filtrage matériel + 8 échantillons en disque = ombres douces).
// À inclure après commun.glsl.

uniform sampler2DShadow shadowtex0;
const bool shadowHardwareFiltering = true;

// joueur : position relative au joueur ; normale : normale du monde (décale le point pour éviter l'acné d'ombre).
float ombreSoleil(vec3 joueur, vec3 normale, float bruit) {
#ifdef OMBRES
    vec4 c = shadowProjection * (shadowModelView * vec4(joueur, 1.0));
    float k = length(c.xy) * 0.9 + 0.1;
    float texel = (2.0 * shadowDistance / float(shadowMapResolution)) * k * k / 0.1;
    c = shadowProjection * (shadowModelView * vec4(joueur + normale * texel * 1.4, 1.0));
    vec3 s = distordreOmbre(c.xyz) * 0.5 + 0.5;
    if (any(lessThan(s, vec3(0.0))) || any(greaterThan(s, vec3(1.0)))) return 1.0;
    s.z -= 0.0001;

    float rayon = OMBRES_DOUCEUR * 1.2 / float(shadowMapResolution);
    float a = bruit * 2.0 * PI;
    mat2 rot = mat2(cos(a), sin(a), -sin(a), cos(a));
    float somme = 0.0;
    for (int i = 0; i < 8; i++) {
        float r = sqrt((float(i) + 0.5) / 8.0);
        float th = float(i) * 2.39996;
        vec2 o = rot * vec2(cos(th), sin(th)) * r * rayon;
        somme += texture(shadowtex0, vec3(s.xy + o, s.z));
    }
    float bord = smoothstep(0.85, 1.0, length(joueur.xz) / shadowDistance);
    return mix(somme / 8.0, 1.0, bord);
#else
    return 1.0;
#endif
}
