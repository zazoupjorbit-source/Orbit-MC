#version 330
#extension GL_ARB_separate_shader_objects : require

// ORBIT Tycoon : ciel spatial permanent (jour comme nuit).
// Fond bleu nuit, 3 couches d'etoiles qui scintillent, voie lactee, nebuleuse bleu glace + or (DA Orbit).
// Tout est calcule a partir de la direction du regard : le ciel est fixe, quel que soit l'endroit.

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:globals.glsl>

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec3 skyDir;

layout(location = 0) out vec4 fragColor;

const vec3 FOND   = vec3(0.020, 0.028, 0.075);
const vec3 BLEU   = vec3(0.384, 0.812, 0.957);   // #62CFF4
const vec3 OR     = vec3(0.878, 0.722, 0.455);   // #E0B864
const vec3 VIOLET = vec3(0.300, 0.180, 0.560);
const float TAU   = 6.2831853;

vec3 hash33(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.xxy + p.yxx) * p.zyx);
}

float hash31(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

float bruit(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash31(i),                   hash31(i + vec3(1, 0, 0)), f.x),
                   mix(hash31(i + vec3(0, 1, 0)),   hash31(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(hash31(i + vec3(0, 0, 1)),   hash31(i + vec3(1, 0, 1)), f.x),
                   mix(hash31(i + vec3(0, 1, 1)),   hash31(i + vec3(1, 1, 1)), f.x), f.y), f.z);
}

float fbm(vec3 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 5; i++) {
        v += a * bruit(p);
        p = p * 2.03 + vec3(1.7, 9.2, 4.1);
        a *= 0.5;
    }
    return v;
}

// Une couche d'etoiles : une etoile possible par cellule de la grille 3D.
vec3 etoiles(vec3 dir, float echelle, float densite, float rayon, float eclat) {
    vec3 p = dir * echelle;
    vec3 c = floor(p);
    vec3 f = fract(p);
    vec3 h = hash33(c);
    if (h.x > densite) return vec3(0.0);

    vec3 centre = 0.25 + 0.5 * hash33(c + 17.13);
    float d = length(f - centre);
    float coeur = max(0.0, 1.0 - d / rayon);
    coeur *= coeur;
    float halo = exp(-d * d / (rayon * rayon * 6.0)) * 0.25;

    // Scintillement : frequence entiere sur un tour de GameTime (20 min) pour eviter un saut.
    float vitesse = floor(400.0 + 900.0 * h.z);
    float scint = 0.65 + 0.35 * sin(GameTime * TAU * vitesse + h.y * TAU);

    vec3 teinte = h.y < 0.15 ? OR : (h.y < 0.55 ? mix(vec3(1.0), BLEU, 0.45) : vec3(1.0));
    return teinte * (coeur + halo) * scint * eclat * (0.5 + h.z);
}

void main() {
    vec3 dir = normalize(skyDir);

    // Voie lactee : bande autour d'un grand cercle incline.
    vec3 axe = normalize(vec3(0.35, 0.60, 0.72));
    float bande = exp(-pow(dot(dir, axe) / 0.24, 2.0));

    // Nebuleuse
    float n1 = fbm(dir * 2.2);
    float n2 = fbm(dir * 4.5 + vec3(5.2, 1.3, 7.7));
    float nuage = smoothstep(0.38, 0.80, n1);
    vec3 nebuleuse = mix(VIOLET, BLEU, n2) * nuage * 0.32;
    nebuleuse += OR * smoothstep(0.58, 0.90, n2) * nuage * 0.22;
    nebuleuse += mix(BLEU, vec3(0.85, 0.80, 1.0), n2) * bande * (0.10 + 0.16 * fbm(dir * 9.0));

    vec3 col = FOND + nebuleuse;

    // Etoiles : grosses et rares, moyennes, poussiere (plus dense dans la voie lactee).
    col += etoiles(dir,  55.0, 0.30,        0.14, 3.0);
    col += etoiles(dir, 120.0, 0.40,        0.18, 1.8);
    col += etoiles(dir, 240.0, 0.25 + 0.55 * bande, 0.22, 0.9 + 0.6 * bande);

    // Brouillard du jeu reduit sur le ciel pour que les etoiles restent visibles jusque pres de l horizon.
    vec4 avecBrouillard = apply_fog(vec4(col, 1.0), sphericalVertexDistance, cylindricalVertexDistance, 0.0, FogSkyEnd, FogSkyEnd, FogSkyEnd, FogColor);
    fragColor = vec4(mix(col, avecBrouillard.rgb, 0.25), 1.0);
}
