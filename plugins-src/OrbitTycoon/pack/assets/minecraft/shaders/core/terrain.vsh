#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:globals.glsl>
#include <minecraft:projection.glsl>
#include <minecraft:sample_lightmap.glsl>
#include <minecraft:terrainglobals.glsl>
#ifndef MULTIDRAW_TERRAIN
    #include <minecraft:chunksection.glsl>
#endif

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
layout(location = 3) in ivec2 UV2;
#ifdef MULTIDRAW_TERRAIN
layout(location = 4) in ivec3 ChunkPosition;
layout(location = 5) in float ChunkVisibility;
#endif

#ifndef OIT_ALPHA_ONLY
uniform sampler2D Sampler2;
#endif

layout(location = 0) out float sphericalVertexDistance;
layout(location = 1) out float cylindricalVertexDistance;
layout(location = 2) out vec4 vertexColor;
layout(location = 3) out vec2 texCoord0;
layout(location = 4) out float chunkVisibility;
// ORBIT Tycoon : position relative a la camera + marqueur "eau" pour les reflets du fragment shader.
layout(location = 5) out vec3 relPos;
layout(location = 6) out float eau;

void main() {
    vec3 pos = Position + (ChunkPosition - CameraBlockPos) + CameraOffset;

    // ORBIT Tycoon : vagues. L'eau est le seul bloc teinte en bleu ; on ne bouge que les sommets
    // de surface (hauteur non entiere), toujours de la meme facon pour une position donnee : pas de trou.
    vec3 monde = Position + vec3(ChunkPosition);
    bool bleu = Color.b > Color.r * 1.4 && Color.b > Color.g;
    float hauteur = fract(monde.y);
    eau = bleu ? 1.0 : 0.0;
    if (bleu && hauteur > 0.05 && hauteur < 0.98) {
        const float TAU = 6.2831853;
        float t = GameTime * TAU;
        float vague = 0.030 * sin(monde.x * 1.1 + monde.z * 0.4 + t * 380.0)
                    + 0.022 * sin(monde.z * 1.6 - monde.x * 0.5 + t * 530.0)
                    + 0.012 * sin((monde.x + monde.z) * 2.7 + t * 860.0);
        pos.y += vague - 0.03;
    }
    relPos = pos;
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    sphericalVertexDistance = fog_spherical_distance(pos);
    cylindricalVertexDistance = fog_cylindrical_distance(pos);
    #ifndef OIT_ALPHA_ONLY
    vertexColor = Color * sample_lightmap(Sampler2, UV2);
    #else
    vertexColor = Color;
    #endif
    texCoord0 = UV0;

    const float chunkFullyVisibleRange = 16.0;
    float dist = length(pos);
    chunkVisibility = mix(1.0, ChunkVisibility, clamp((dist - chunkFullyVisibleRange) / chunkFullyVisibleRange, 0.0, 1.0));
}
