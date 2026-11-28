// Orbit Shaders : bloom en 3 passes.
// composite2 (PASSE_RECOLTE) : moyenne des niveaux de mipmap de l'image -> colortex2
// composite3 (PASSE_H) : flou horizontal colortex2 -> colortex4
// composite4 (PASSE_V) : flou vertical colortex4 -> colortex2 (lu par final)
#include "/lib/commun.glsl"

#ifdef VSH
out vec2 uv;
void main() {
    gl_Position = ftransform();
    uv = gl_MultiTexCoord0.xy;
}
#endif

#ifdef FSH
in vec2 uv;

#if defined PASSE_RECOLTE
uniform sampler2D colortex0;
const bool colortex0MipmapEnabled = true;
/* RENDERTARGETS: 2 */
#elif defined PASSE_H
uniform sampler2D colortex2;
/* RENDERTARGETS: 4 */
#else
uniform sampler2D colortex4;
/* RENDERTARGETS: 2 */
#endif
layout(location = 0) out vec4 sortie;

void main() {
#ifdef BLOOM
    #if defined PASSE_RECOLTE
    vec3 b = vec3(0.0);
    float total = 0.0;
    for (int i = 2; i <= 6; i++) {
        float w = 1.0 / float(i - 1);
        b += textureLod(colortex0, uv, float(i)).rgb * w;
        total += w;
    }
    sortie = vec4(b / total, 1.0);
    #else
    const float poids[5] = float[5](0.227, 0.194, 0.121, 0.054, 0.016);
        #ifdef PASSE_H
    vec2 pas = vec2(3.0 / viewWidth, 0.0);
    vec3 b = texture(colortex2, uv).rgb * poids[0];
    for (int i = 1; i < 5; i++) b += (texture(colortex2, uv + pas * float(i)).rgb + texture(colortex2, uv - pas * float(i)).rgb) * poids[i];
        #else
    vec2 pas = vec2(0.0, 3.0 / viewHeight);
    vec3 b = texture(colortex4, uv).rgb * poids[0];
    for (int i = 1; i < 5; i++) b += (texture(colortex4, uv + pas * float(i)).rgb + texture(colortex4, uv - pas * float(i)).rgb) * poids[i];
        #endif
    sortie = vec4(b, 1.0);
    #endif
#else
    sortie = vec4(0.0, 0.0, 0.0, 1.0);
#endif
}
#endif
