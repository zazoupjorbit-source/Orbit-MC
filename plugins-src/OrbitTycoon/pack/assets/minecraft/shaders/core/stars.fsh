#version 330
#extension GL_ARB_separate_shader_objects : require

// ORBIT Tycoon : etoiles d'origine du jeu plus brillantes et bleutees (en plus de celles du ciel).

#include <minecraft:dynamictransforms.glsl>

layout(location = 0) out vec4 fragColor;

void main() {
    fragColor = vec4(vec3(0.80, 0.92, 1.0) * ColorModulator.rgb * 1.3, min(1.0, ColorModulator.a * 1.8));
}
