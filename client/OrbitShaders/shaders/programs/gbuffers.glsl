// Orbit Shaders : programmes du monde éclairés (terrain, eau, entités, blocs-entités, main, particules, pluie).
// Le fichier d'appel définit VSH ou FSH et un PROG_* (voir outils/generer-programmes.ps1).
#include "/lib/commun.glsl"
#include "/lib/ombres.glsl"
#include "/lib/eclairage.glsl"
#include "/lib/ciel.glsl"

#if defined PROG_TERRAIN || defined PROG_WATER
    #define AVEC_BLOCS
#endif

// ============================================================ sommets
#ifdef VSH

out vec2 texcoord;
out vec2 lmcoord;
out vec4 glcolor;
out vec3 vue;
out vec3 normaleV;
flat out int bloc;

#ifdef AVEC_BLOCS
in vec4 mc_Entity;
in vec2 mc_midTexCoord;
#include "/lib/vent.glsl"
#endif

void main() {
    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    lmcoord = clamp((gl_TextureMatrix[1] * gl_MultiTexCoord1).xy * (33.05 / 32.0) - (1.05 / 32.0), 0.0, 1.0);
    glcolor = gl_Color;
    vec3 n = gl_NormalMatrix * gl_Normal;
    normaleV = dot(n, n) > 1e-4 ? normalize(n) : normalize(upPosition);
    vue = (gl_ModelViewMatrix * gl_Vertex).xyz;
    bloc = 0;
#ifdef AVEC_BLOCS
    bloc = int(mc_Entity.x + 0.5);
    #if defined PROG_TERRAIN && defined VEGETATION_VENT
    float k = forceVent(bloc, gl_MultiTexCoord0.y < mc_midTexCoord.y);
    if (k > 0.0) {
        vec3 joueur = versJoueur(vue);
        joueur += vent(joueur + cameraPosition) * k * lmcoord.y;
        vue = (gbufferModelView * vec4(joueur, 1.0)).xyz;
    }
    #endif
#endif
    gl_Position = gl_ProjectionMatrix * vec4(vue, 1.0);
}

#endif

// ============================================================ pixels
#ifdef FSH

in vec2 texcoord;
in vec2 lmcoord;
in vec4 glcolor;
in vec3 vue;
in vec3 normaleV;
flat in int bloc;

uniform sampler2D gtexture;
#ifdef PROG_ENTITIES
uniform vec4 entityColor;
#endif

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 sortie;

#ifdef PROG_WATER
// Pente des vagues (somme de 4 houles qui se croisent).
vec2 vagues(vec2 p) {
    float t = frameTimeCounter;
    vec2 g = vec2(0.0);
    const vec2 d1 = vec2(0.96, 0.28);
    const vec2 d2 = vec2(-0.37, 0.93);
    const vec2 d3 = vec2(0.66, -0.75);
    const vec2 d4 = vec2(-0.98, -0.20);
    g += d1 * 1.1 * 0.050 * cos(dot(d1, p) * 1.1 + t * 1.3);
    g += d2 * 1.8 * 0.035 * cos(dot(d2, p) * 1.8 + t * 1.6);
    g += d3 * 2.9 * 0.022 * cos(dot(d3, p) * 2.9 + t * 2.1);
    g += d4 * 4.3 * 0.014 * cos(dot(d4, p) * 4.3 + t * 2.7);
    return g;
}

// Eau : reflet du ciel (Fresnel), reflet doré du soleil, corps bleu-vert transparent.
vec4 eau(vec4 tex, vec3 n, float bruit) {
    vec3 upV = normalize(upPosition);
    vec3 joueur = versJoueur(vue);
#ifdef EAU_VAGUES
    if (dot(n, upV) > 0.6) {
        vec2 g = vagues(joueur.xz + cameraPosition.xz);
        n = normalize(mat3(gbufferModelView) * normalize(vec3(-g.x, 1.0, -g.y)));
    }
#endif
    vec3 V = normalize(vue);
    float f = 0.02 + 0.98 * pow(1.0 - clamp(dot(-V, n), 0.0, 1.0), 5.0);
    vec3 R = reflect(V, n);
    vec3 reflet = cielCouleur(R, false) * lmcoord.y * lmcoord.y + vec3(0.010, 0.020, 0.040);

    float ombre = ombreSoleil(joueur, vec3(0.0, 1.0, 0.0), bruit);
    float spec = pow(max(dot(R, normalize(shadowLightPosition)), 0.0), 350.0) * 6.0;
    vec3 speculaire = lumiereDirecte() * spec * ombre * lmcoord.y;

    vec3 teinte = mix(versLineaire(glcolor.rgb), vec3(0.06, 0.30, 0.55), 0.5);
    float detail = 0.8 + 0.4 * lum(tex.rgb);
    vec3 corps = teinte * detail * (lumiereAmbiante() * lmcoord.y * lmcoord.y * 0.6
                                  + lumiereDirecte() * 0.15 * ombre
                                  + TORCHE * pow(lmcoord.x, 2.6) * 0.8 + 0.01);
    if (isEyeInWater == 1) {   // vu d'en dessous
        f *= 0.25;
        reflet = vec3(0.02, 0.07, 0.10);
    }
    float a0 = EAU_OPACITE;
    float alpha = f + (1.0 - f) * a0;
    vec3 c = (reflet * f + corps * (1.0 - f) * a0) / max(alpha, 1e-3) + speculaire;
    return vec4(c, alpha);
}
#endif

void main() {
    vec4 tex = texture(gtexture, texcoord) * glcolor;
#ifdef PROG_WATER
    if (tex.a < 0.004) discard;
#else
    if (tex.a < 0.1) discard;
#endif
#ifdef PROG_ENTITIES
    tex.rgb = mix(tex.rgb, entityColor.rgb, entityColor.a);   // flash rouge des coups
#endif
    vec3 albedo = versLineaire(tex.rgb);
    vec3 n = normalize(normaleV);
    float bruit = bruitIGN(gl_FragCoord.xy);

    float sss = 0.0;
    float emissif = 0.0;
    float plat = 0.0;
#if defined PROG_TEXTURED || defined PROG_WEATHER
    plat = 1.0;
#endif
    if (bloc >= 10001 && bloc <= 10005) sss = 1.0;
    if (bloc == 10020) emissif = 1.0;                                                    // lumineux en entier
    else if (bloc == 10021) emissif = smoothstep(0.6, 0.9, max(tex.r, max(tex.g, tex.b)));   // flamme, ampoule
    else if (bloc == 10022) emissif = 1.5;                                               // lave
    else if (bloc == 10023) emissif = 0.8;                                               // portails

#ifdef PROG_WATER
    if (bloc == 10010) {
        sortie = eau(tex, n, bruit);
        return;
    }
#endif

    vec3 c = eclairer(albedo, n, lmcoord, vue, sss, emissif, bruit, plat);
#ifdef PROG_WEATHER
    sortie = vec4(c, tex.a * 0.55);
#else
    sortie = vec4(c, tex.a);
#endif
}

#endif
