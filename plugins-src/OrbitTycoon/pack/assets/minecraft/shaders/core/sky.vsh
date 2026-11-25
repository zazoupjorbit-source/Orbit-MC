#version 330
#extension GL_ARB_separate_shader_objects : require

// ORBIT Tycoon : ciel spatial. Copie du sky.vsh de la 26.3 + direction du regard
// envoyee au fragment shader (skyDir) pour dessiner etoiles et nebuleuse.

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

layout(location = 0) in vec3 Position;

layout(location = 0) out float sphericalVertexDistance;
layout(location = 1) out float cylindricalVertexDistance;
layout(location = 2) out vec3 skyDir;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
    skyDir = Position;
}
