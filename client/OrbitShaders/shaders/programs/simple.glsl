// Orbit Shaders : programmes non éclairés.
// PROG_BASIC : lignes (contour du bloc visé, laisses) ; PROG_EMISSIVE : yeux d'araignée, faisceau de balise ;
// PROG_GLINT : reflet des objets enchantés ; PROG_LIGHTNING : éclairs.
#include "/lib/commun.glsl"

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
#if defined PROG_EMISSIVE || defined PROG_GLINT
uniform sampler2D gtexture;
#endif

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 sortie;

void main() {
#if defined PROG_EMISSIVE
    vec4 t = texture(gtexture, texcoord) * glcolor;
    if (t.a < 0.004) discard;
    sortie = vec4(versLineaire(t.rgb) * 2.5, t.a);
#elif defined PROG_GLINT
    vec4 t = texture(gtexture, texcoord) * glcolor;
    sortie = vec4(versLineaire(t.rgb) * 1.2, t.a);
#elif defined PROG_LIGHTNING
    sortie = vec4(versLineaire(glcolor.rgb) * 4.0, glcolor.a);
#else
    if (glcolor.a < 0.004) discard;
    sortie = vec4(versLineaire(glcolor.rgb), glcolor.a);
#endif
}
#endif
