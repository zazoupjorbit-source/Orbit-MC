// Orbit Shaders : nuages (gbuffers_clouds). Éclairés par le soleil et le ciel, fondus dans le ciel au loin.
// colortex1 = 0.5 marque leurs pixels : le brouillard de bord de carte ne les touche pas.
#include "/lib/commun.glsl"
#include "/lib/ciel.glsl"

#ifdef VSH
out vec4 glcolor;
out vec3 vue;
out vec3 normaleV;
void main() {
    glcolor = gl_Color;
    vec3 n = gl_NormalMatrix * gl_Normal;
    normaleV = dot(n, n) > 1e-4 ? normalize(n) : normalize(upPosition);
    vue = (gl_ModelViewMatrix * gl_Vertex).xyz;
    gl_Position = gl_ProjectionMatrix * vec4(vue, 1.0);
}
#endif

#ifdef FSH
in vec4 glcolor;
in vec3 vue;
in vec3 normaleV;

/* RENDERTARGETS: 0,1 */
layout(location = 0) out vec4 sortie;
layout(location = 1) out vec4 drapeau;

void main() {
    if (glcolor.a < 0.01) discard;
    vec3 albedo = versLineaire(glcolor.rgb);
    float d = dot(normalize(normaleV), normalize(shadowLightPosition)) * 0.5 + 0.5;
    vec3 c = albedo * (lumiereDirecte() * (0.35 + 0.65 * d) * 0.9 + lumiereAmbiante() * 1.3 + 0.01);
    float loin = (1.0 - exp(-length(vue) / 600.0)) * 0.7;
    c = mix(c, cielCouleur(normalize(vue), false), loin);
    sortie = vec4(c, glcolor.a);
    drapeau = vec4(0.5, 0.0, 0.0, 1.0);
}
#endif
