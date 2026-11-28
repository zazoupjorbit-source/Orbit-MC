// Orbit Shaders : uniformes, palette ORBIT (bleu + or) et fonctions communes à tous les programmes.
#include "/lib/reglages.glsl"

// Nether (world-1) et End (world1) : pas de soleil, donc ni ombres ni rayons.
#if defined NETHER || defined END
    #define SANS_CIEL
    #undef OMBRES
    #undef RAYONS
#endif

uniform vec3 sunPosition;
uniform vec3 moonPosition;
uniform vec3 shadowLightPosition;
uniform vec3 upPosition;
uniform vec3 cameraPosition;
uniform vec3 fogColor;
uniform float rainStrength;
uniform float frameTimeCounter;
uniform float viewWidth;
uniform float viewHeight;
uniform float far;
uniform float sunAngle;
uniform float nightVision;
uniform float blindness;
uniform float darknessFactor;
uniform int isEyeInWater;
uniform int heldBlockLightValue;
uniform int heldBlockLightValue2;
uniform ivec2 eyeBrightnessSmooth;
uniform mat4 gbufferModelView;
uniform mat4 gbufferModelViewInverse;
uniform mat4 gbufferProjectionInverse;
uniform mat4 shadowModelView;
uniform mat4 shadowModelViewInverse;
uniform mat4 shadowProjection;

const float PI = 3.14159265;

// --- Palette ORBIT : lumière dorée, ombres et nuits bleues ---
const vec3 OR_MIDI       = vec3(1.00, 0.92, 0.80);
const vec3 OR_COUCHANT   = vec3(1.00, 0.56, 0.22);
const vec3 BLEU_LUNE     = vec3(0.55, 0.70, 1.00);
const vec3 AMB_JOUR      = vec3(0.50, 0.66, 1.00);
const vec3 AMB_COUCHANT  = vec3(0.62, 0.52, 0.72);
const vec3 AMB_NUIT      = vec3(0.16, 0.24, 0.52);
const vec3 TORCHE        = vec3(1.00, 0.68, 0.36);

float lum(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }
vec3 versLineaire(vec3 c) { return pow(max(c, vec3(0.0)), vec3(2.2)); }

// Bruit « interleaved gradient » : tramage fixe à l'écran, sans scintillement.
float bruitIGN(vec2 p) { return fract(52.9829189 * fract(dot(p, vec2(0.06711056, 0.00583715)))); }
float hash13(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

vec3 versJoueur(vec3 vue) { return mat3(gbufferModelViewInverse) * vue + gbufferModelViewInverse[3].xyz; }
vec3 ecranVersVue(vec2 uv, float profondeur) {
    vec4 p = gbufferProjectionInverse * vec4(vec3(uv, profondeur) * 2.0 - 1.0, 1.0);
    return p.xyz / p.w;
}

// Carte d'ombre déformée : plus de détails près du joueur.
vec3 distordreOmbre(vec3 p) {
    float k = length(p.xy) * 0.9 + 0.1;
    return vec3(p.xy / k, p.z * 0.2);
}

// --- Moment de la journée ---
float hauteurSoleil() { return dot(normalize(sunPosition), normalize(upPosition)); }
float facteurJour() { return smoothstep(-0.10, 0.22, hauteurSoleil()); }
float facteurNuit() { return 1.0 - smoothstep(-0.22, 0.02, hauteurSoleil()); }
float facteurCouchant() { return 1.0 - smoothstep(0.02, 0.42, abs(hauteurSoleil() - 0.06)); }
// 1 dehors, 0 au fond d'une grotte (lumière du ciel vue par le joueur, lissée par le jeu)
float cielOuvert() { return clamp(float(eyeBrightnessSmooth.y) / 240.0, 0.0, 1.0); }

// Lumière qui porte les ombres : soleil (doré) le jour, lune (bleue) la nuit.
vec3 lumiereDirecte() {
#ifdef SANS_CIEL
    return vec3(0.0);
#else
    float h = hauteurSoleil();
    vec3 soleil = mix(OR_COUCHANT * 0.9, OR_MIDI * 1.15, smoothstep(0.04, 0.5, h));
    vec3 lune = BLEU_LUNE * 0.13;
    bool estSoleil = dot(normalize(shadowLightPosition), normalize(sunPosition)) > 0.5;
    vec3 c = estSoleil ? soleil : lune;
    c *= smoothstep(0.0, 0.07, abs(h));   // fondu quand le soleil passe l'horizon (bascule soleil/lune)
    c *= 1.0 - rainStrength * 0.85;
    return c;
#endif
}

// Lumière du ciel, qui éclaire tout ce qui voit le ciel.
vec3 lumiereAmbiante() {
#if defined NETHER
    return vec3(0.55, 0.34, 0.26) * 0.30 * (1.0 + nightVision * 2.0);
#elif defined END
    return vec3(0.38, 0.32, 0.60) * 0.30 * (1.0 + nightVision * 2.0);
#else
    float h = hauteurSoleil();
    vec3 jour = mix(AMB_COUCHANT * 0.28, AMB_JOUR * 0.42, smoothstep(0.05, 0.45, h));
    vec3 c = mix(AMB_NUIT * 0.15, jour, facteurJour());
    vec3 gris = vec3(lum(c)) * vec3(0.85, 0.90, 1.00);
    c = mix(c, gris * 0.8, rainStrength);
    return c * (1.0 + nightVision * 2.0);
#endif
}
