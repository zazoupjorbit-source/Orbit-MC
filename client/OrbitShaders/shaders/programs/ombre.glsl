// Orbit Shaders : passe d'ombre (rendu du monde vu du soleil, dans une carte déformée).
#include "/lib/commun.glsl"

#ifdef VSH
out vec2 texcoord;
out vec4 glcolor;
flat out int bloc;
in vec4 mc_Entity;
in vec2 mc_midTexCoord;
#include "/lib/vent.glsl"

void main() {
    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    glcolor = gl_Color;
    bloc = int(mc_Entity.x + 0.5);
    vec3 joueur = (shadowModelViewInverse * (gl_ModelViewMatrix * gl_Vertex)).xyz;
#ifdef VEGETATION_VENT
    float k = forceVent(bloc, gl_MultiTexCoord0.y < mc_midTexCoord.y);
    if (k > 0.0) {
        float ciel = clamp((gl_TextureMatrix[1] * gl_MultiTexCoord1).y * (33.05 / 32.0) - (1.05 / 32.0), 0.0, 1.0);
        joueur += vent(joueur + cameraPosition) * k * ciel;
    }
#endif
    gl_Position = gl_ProjectionMatrix * (shadowModelView * vec4(joueur, 1.0));
    gl_Position.xyz = distordreOmbre(gl_Position.xyz);
}
#endif

#ifdef FSH
in vec2 texcoord;
in vec4 glcolor;
flat in int bloc;
uniform sampler2D gtexture;

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 sortie;

void main() {
    if (bloc == 10010) discard;   // l'eau ne fait pas d'ombre (sinon la surface s'ombre elle-même)
    vec4 t = texture(gtexture, texcoord) * glcolor;
    if (t.a < 0.1) discard;
    sortie = t;
}
#endif
