// Orbit Shaders : ciel (gbuffers_skybasic = dégradé, soleil, étoiles ; gbuffers_skytextured = lune, ciel de l'End).
#include "/lib/commun.glsl"
#include "/lib/ciel.glsl"

uniform int renderStage;

#ifdef VSH
out vec4 glcolor;
out vec2 texcoord;
void main() {
    gl_Position = ftransform();
    glcolor = gl_Color;
    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
}
#endif

#ifdef FSH
in vec4 glcolor;
in vec2 texcoord;
#ifdef PROG_SKYTEXTURED
uniform sampler2D gtexture;
#endif

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 sortie;

void main() {
#ifdef PROG_SKYBASIC
    #ifdef SANS_CIEL
    sortie = vec4(versLineaire(glcolor.rgb), glcolor.a);
    #else
    if (renderStage == MC_RENDER_STAGE_STARS) discard;   // nos étoiles sont dans cielCouleur
    vec3 dir = normalize(ecranVersVue(gl_FragCoord.xy / vec2(viewWidth, viewHeight), 1.0));
    sortie = vec4(cielCouleur(dir, true), 1.0);
    #endif
#else
    vec4 t = texture(gtexture, texcoord) * glcolor;
    #ifndef SANS_CIEL
    if (renderStage == MC_RENDER_STAGE_SUN) discard;     // soleil rond dessiné dans cielCouleur
    if (renderStage == MC_RENDER_STAGE_MOON) {
        sortie = vec4(versLineaire(t.rgb) * vec3(0.85, 0.92, 1.10) * 2.2, t.a * (1.0 - rainStrength));
        return;
    }
    #endif
    sortie = vec4(versLineaire(t.rgb), t.a);
#endif
}
#endif
