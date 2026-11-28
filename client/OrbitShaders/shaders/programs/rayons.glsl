// Orbit Shaders : composite = rayons de lumière (on marche le long de chaque pixel dans la carte d'ombre).
// Résultat dans colortex3, ajouté à l'image par composite1.
#include "/lib/commun.glsl"
#include "/lib/ombres.glsl"

#ifdef VSH
out vec2 uv;
void main() {
    gl_Position = ftransform();
    uv = gl_MultiTexCoord0.xy;
}
#endif

#ifdef FSH
in vec2 uv;
uniform sampler2D depthtex0;

/* RENDERTARGETS: 3 */
layout(location = 0) out vec4 sortie;

void main() {
    vec3 vl = vec3(0.0);
#ifdef RAYONS
    float prof = texture(depthtex0, uv).r;
    vec3 vue = ecranVersVue(uv, prof);
    float dist = prof >= 1.0 ? shadowDistance : min(length(vue), shadowDistance);
    vec3 dirV = normalize(vue);
    vec3 dirJ = mat3(gbufferModelViewInverse) * dirV;
    vec3 origine = gbufferModelViewInverse[3].xyz;
    float bruit = bruitIGN(gl_FragCoord.xy);

    float somme = 0.0;
    for (int i = 0; i < RAYONS_PAS; i++) {
        float t = (float(i) + bruit) / float(RAYONS_PAS);
        t *= t;   // plus de pas près du joueur
        vec4 c = shadowProjection * (shadowModelView * vec4(origine + dirJ * dist * t, 1.0));
        somme += texture(shadowtex0, distordreOmbre(c.xyz) * 0.5 + 0.5);
    }
    float eclaire = somme / float(RAYONS_PAS);

    // Diffusion vers l'avant (Henyey-Greenstein) : les rayons se voient surtout face au soleil.
    float cosT = dot(dirV, normalize(shadowLightPosition));
    const float g = 0.72;
    float mie = (1.0 - g * g) / (4.0 * PI * pow(1.0 + g * g - 2.0 * g * cosT, 1.5));
    float phase = mie * 0.6 + 0.025;
    float densite = 1.0 - exp(-dist * 0.010);
    float moment = mix(0.45, 1.0, facteurCouchant());
    vl = lumiereDirecte() * eclaire * phase * densite * moment * cielOuvert() * (1.0 - rainStrength);
    if (isEyeInWater == 1) vl *= vec3(0.2, 0.6, 1.0) * 0.5;
#endif
    sortie = vec4(vl, 1.0);
}
#endif
