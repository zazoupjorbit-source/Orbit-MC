// Orbit Shaders : composite1 = brume du ciel, bord de carte, sous l'eau, grottes ; ajout des rayons de lumière.
#include "/lib/commun.glsl"
#include "/lib/ciel.glsl"

#ifdef VSH
out vec2 uv;
void main() {
    gl_Position = ftransform();
    uv = gl_MultiTexCoord0.xy;
}
#endif

#ifdef FSH
in vec2 uv;
uniform sampler2D colortex0;
uniform sampler2D colortex1;
uniform sampler2D colortex3;
uniform sampler2D depthtex0;

const vec4 colortex1ClearColor = vec4(0.0, 0.0, 0.0, 0.0);

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 sortie;

void main() {
    vec3 c = texture(colortex0, uv).rgb;
    float prof = texture(depthtex0, uv).r;
    vec3 vue = ecranVersVue(uv, prof);
    vec3 dirV = normalize(vue);
    float dist = length(vue);
    bool ciel = prof >= 1.0;
    bool nuage = abs(texture(colortex1, uv).r - 0.5) < 0.2;

    if (isEyeInWater == 1) {
        vec3 eauBrume = vec3(0.015, 0.07, 0.11) * (lum(lumiereAmbiante()) * 1.5 + 0.08);
        c = mix(c * vec3(0.55, 0.85, 1.0), eauBrume, 1.0 - exp(-dist * 0.07));
    } else if (isEyeInWater == 2) {
        c = mix(c, vec3(1.0, 0.30, 0.05) * 2.0, 1.0 - exp(-dist * 1.0));
    } else if (isEyeInWater == 3) {
        c = mix(c, vec3(0.70, 0.80, 0.90), 1.0 - exp(-dist * 0.8));
    } else if (!ciel && !nuage) {
        vec3 joueur = versJoueur(vue);
        float bord = smoothstep(far * 0.55, far, length(joueur.xz));
#ifdef SANS_CIEL
        vec3 fc = versLineaire(fogColor) * 0.8;
        float brume = 1.0 - exp(-dist * 0.012 * BROUILLARD);
        c = mix(c, fc, max(brume * 0.8, bord));
#else
        vec3 fc = mix(vec3(0.004, 0.006, 0.012), cielCouleur(dirV, false), cielOuvert());
        float brume = 1.0 - exp(-dist * 0.0018 * BROUILLARD * (1.0 + rainStrength * 4.0));
        c = mix(c, fc, max(brume * 0.85, bord));
#endif
    }

#ifdef RAYONS
    // petit flou en croix pour fondre le tramage des rayons
    vec2 px = 1.0 / vec2(viewWidth, viewHeight);
    vec3 vl = texture(colortex3, uv + vec2(1.5, 0.5) * px).rgb
            + texture(colortex3, uv + vec2(-0.5, 1.5) * px).rgb
            + texture(colortex3, uv + vec2(-1.5, -0.5) * px).rgb
            + texture(colortex3, uv + vec2(0.5, -1.5) * px).rgb;
    c += vl * 0.25 * RAYONS_FORCE;
#endif

    float noir = max(blindness, darknessFactor);
    c = mix(c, vec3(0.0), noir * (1.0 - exp(-dist * 0.25)));
    sortie = vec4(c, 1.0);
}
#endif
