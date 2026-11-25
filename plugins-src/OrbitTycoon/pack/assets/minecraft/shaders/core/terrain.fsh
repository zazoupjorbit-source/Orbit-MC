#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:globals.glsl>
#include <minecraft:texture_sampling.glsl>
#include <minecraft:oit.glsl>
#include <minecraft:terrainglobals.glsl>
#ifndef MULTIDRAW_TERRAIN
    #include <minecraft:chunksection.glsl>
#endif

uniform sampler2D Sampler0;

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec4 vertexColor;
layout(location = 3) in vec2 texCoord0;
layout(location = 4) in float chunkVisibility;
layout(location = 5) in vec3 relPos;
layout(location = 6) in float eau;

// ORBIT Tycoon : petites etoiles qui scintillent dans le reflet de l'eau.
vec3 hashEau(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.xxy + p.yxx) * p.zyx);
}

float refletEtoiles(vec3 r) {
    vec3 p = r * 70.0;
    vec3 c = floor(p);
    vec3 h = hashEau(c);
    if (h.x > 0.35) return 0.0;
    float d = length(fract(p) - (0.25 + 0.5 * hashEau(c + 17.13)));
    float b = max(0.0, 1.0 - d / 0.16);
    return b * b * (0.6 + 0.4 * sin(GameTime * 6.2831853 * floor(500.0 + 800.0 * h.z) + h.y * 6.28));
}

#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif

vec4 calculateFinalColor(vec4 color) {
    #ifdef OIT_ACCUMULATE
    color = sampleColorForAccumulation(color);
    vec4 fogColor = vec4(FogColor.rgb * color.a, FogColor.a);
    #else
    vec4 fogColor = FogColor;
    #endif
    return apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, fogColor);
}

void main() {
    vec4 color = (UseRgss == 1 ? sampleRGSS(Sampler0, texCoord0, 1.0f / TextureSize) : sampleNearest(Sampler0, texCoord0, 1.0f / TextureSize)) * vertexColor;
    #ifndef OIT_ALPHA_ONLY
    color = mix(FogColor * vec4(1, 1, 1, color.a), color, chunkVisibility);
    #endif
    #ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
    #endif

    // ORBIT Tycoon : eau qui reflete le ciel spatial (plus opaque de biais, comme une vraie surface).
    if (eau > 0.5) {
        vec3 dir = normalize(relPos);
        vec3 monde = relPos + vec3(CameraBlockPos) - CameraOffset;
        float t = GameTime * 6.2831853;
        float gx = 0.033 * cos(monde.x * 1.1 + monde.z * 0.4 + t * 380.0) - 0.011 * cos(monde.z * 1.6 - monde.x * 0.5 + t * 530.0)
                 + 0.032 * cos((monde.x + monde.z) * 2.7 + t * 860.0);
        float gz = 0.013 * cos(monde.x * 1.1 + monde.z * 0.4 + t * 380.0) + 0.035 * cos(monde.z * 1.6 - monde.x * 0.5 + t * 530.0)
                 + 0.032 * cos((monde.x + monde.z) * 2.7 + t * 860.0);
        vec3 n = normalize(vec3(-gx * 4.0, 1.0, -gz * 4.0));
        float fres = pow(1.0 - clamp(abs(dir.y), 0.0, 1.0), 3.0);
        color.a = mix(color.a, 1.0, fres * 0.55);
        #ifndef OIT_ALPHA_ONLY
        vec3 r = reflect(dir, n);
        vec3 ciel = mix(vec3(0.02, 0.04, 0.10), vec3(0.10, 0.20, 0.34), fres);
        color.rgb = mix(color.rgb, ciel, fres * 0.65);
        color.rgb += vec3(0.80, 0.92, 1.0) * refletEtoiles(r) * (0.35 + fres);
        #endif
    }

    // ORBIT Tycoon : relief. Normale de la face calculee a l'ecran, lumiere principale venue d'en haut
    // (clair de lune froid sur les faces a l'ombre, touche chaude sur les faces eclairees)
    // + bords de blocs legerement assombris (occlusion douce) pour un rendu plus net et plus "HD".
    #ifndef OIT_ALPHA_ONLY
    {
        vec3 dp1 = dFdx(relPos);
        vec3 dp2 = dFdy(relPos);
        vec2 duv1 = dFdx(texCoord0);
        vec2 duv2 = dFdy(texCoord0);
        vec3 n = normalize(cross(dp1, dp2));
        if (dot(n, relPos) > 0.0) n = -n;

        // Relief par pixel (bump) : la texture sert de carte de hauteur (pixels clairs = en relief).
        // Repere tangent calcule a l'ecran ; on reste dans le sprite 16x16 pour ne pas lire la texture voisine.
        vec3 nb = n;
        // Taille d'un pixel de l'ecran en pixels de texture : au-dela de ~0.5, le relief cree du moire -> on l'efface.
        float empreinte = max(length(duv1), length(duv2)) * float(max(TextureSize.x, TextureSize.y));
        float proche = (1.0 - smoothstep(0.20, 0.55, empreinte)) * (1.0 - smoothstep(10.0, 28.0, sphericalVertexDistance));
        if (eau < 0.5 && proche > 0.0) {
            ivec2 px = ivec2(floor(texCoord0 * vec2(TextureSize)));
            ivec2 loc = px % 16;
            ivec2 dx = ivec2(loc.x < 15 ? 1 : -1, 0);
            ivec2 dy = ivec2(0, loc.y < 15 ? 1 : -1);
            const vec3 L = vec3(0.2126, 0.7152, 0.0722);
            float h0 = dot(texelFetch(Sampler0, px, 0).rgb, L);
            float hx = dot(texelFetch(Sampler0, px + dx, 0).rgb, L);
            float hy = dot(texelFetch(Sampler0, px + dy, 0).rgb, L);
            float dhu = (hx - h0) * float(dx.x);
            float dhv = (hy - h0) * float(dy.y);

            vec3 dp2perp = cross(dp2, n);
            vec3 dp1perp = cross(n, dp1);
            vec3 T = dp2perp * duv1.x + dp1perp * duv2.x;
            vec3 B = dp2perp * duv1.y + dp1perp * duv2.y;
            float inv = inversesqrt(max(max(dot(T, T), dot(B, B)), 1e-12));
            T *= inv;
            B *= inv;
            nb = normalize(n - (T * dhu + B * dhv) * 1.6 * proche);
        }

        vec3 lumiere = normalize(vec3(0.45, 0.80, 0.35));
        float diff = clamp(dot(nb, lumiere), 0.0, 1.0);
        color.rgb *= mix(0.66, 1.14, diff);
        color.rgb += vec3(0.000, 0.025, 0.070) * (1.0 - diff);
        color.rgb += vec3(0.060, 0.040, 0.000) * diff * diff;

        // Reflets brillants : les textures claires (quartz, or, glace, metal) accrochent la lumiere.
        float clair = dot(color.rgb, vec3(0.2126, 0.7152, 0.0722));
        vec3 vue = normalize(-relPos);
        float spec = pow(clamp(dot(nb, normalize(lumiere + vue)), 0.0, 1.0), 28.0);
        color.rgb += vec3(1.0, 0.90, 0.70) * spec * smoothstep(0.35, 0.85, clair) * 0.22 * proche;
        color.rgb = min(color.rgb, vec3(0.97));   // evite le blanc "crame" sur les blocs clairs

        vec3 an = abs(n);
        if (eau < 0.5 && max(an.x, max(an.y, an.z)) > 0.985) {
            vec3 mondeB = relPos + vec3(CameraBlockPos) - CameraOffset;
            vec2 plan = an.y > 0.9 ? mondeB.xz : (an.x > 0.9 ? mondeB.zy : mondeB.xy);
            vec2 f = fract(plan);
            vec2 bord = min(f, 1.0 - f);
            float coin = min(bord.x, bord.y);
            float ao = smoothstep(0.0, 0.14, coin);
            // Contour efface quand un pixel couvre trop de bloc (sinon moire en bandes au loin).
            vec2 fw = fwidth(plan);
            float loin = max(smoothstep(0.025, 0.06, max(fw.x, fw.y)), smoothstep(16.0, 40.0, sphericalVertexDistance));
            color.rgb *= mix(1.0, mix(0.62, 1.0, ao), 1.0 - loin);
        }

        // Perspective aerienne : les blocs lointains perdent un peu de saturation.
        float lum = dot(color.rgb, vec3(0.2126, 0.7152, 0.0722));
        color.rgb = mix(color.rgb, vec3(lum) * vec3(0.85, 0.95, 1.15), smoothstep(30.0, 200.0, sphericalVertexDistance) * 0.35);
    }
    #endif

    // ORBIT Tycoon : leger voile bleu nuit avec la distance (profondeur), avant le brouillard du jeu.
    #if !defined(OIT_ALPHA_ONLY) && !defined(OIT_ACCUMULATE)
    float voile = smoothstep(20.0, max(FogRenderDistanceEnd, 40.0), sphericalVertexDistance) * 0.30;
    color.rgb = mix(color.rgb, vec3(0.05, 0.09, 0.18), voile);
    #endif

    #ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
    #else
    fragColor = calculateFinalColor(color);
    #endif
}
