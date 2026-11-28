// Orbit Shaders : final = bloom, exposition, courbe filmique, étalonnage ORBIT (ombres bleues, lumières dorées).

/*
const int colortex0Format = RGBA16F;
const int colortex1Format = RGBA8;
const int colortex2Format = RGBA16F;
const int colortex3Format = RGBA16F;
const int colortex4Format = RGBA16F;
*/

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
uniform sampler2D colortex0;
uniform sampler2D colortex2;

layout(location = 0) out vec4 sortie;

vec3 courbeFilmique(vec3 x) {
    return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0);
}

void main() {
    vec3 c = texture(colortex0, uv).rgb;
#ifdef BLOOM
    c = mix(c, texture(colortex2, uv).rgb, 0.12 * BLOOM_FORCE);
#endif
    // L'œil s'habitue : un peu plus clair dans les grottes et la nuit.
    float expo = EXPOSITION * mix(1.8, 1.0, cielOuvert()) * (1.0 + facteurNuit() * 0.5 * cielOuvert());
    c = courbeFilmique(c * expo);

    float l = lum(c);
    vec3 teinte = mix(vec3(0.90, 0.96, 1.10), vec3(1.07, 1.00, 0.88), smoothstep(0.15, 0.75, l));
    c *= mix(vec3(1.0), teinte, 0.5 * TEINTE_ORBIT);
    c = mix(vec3(lum(c)), c, SATURATION);

    vec2 d = uv - 0.5;
    c *= 1.0 - VIGNETTE * dot(d, d) * 1.6;

    c = pow(clamp(c, 0.0, 1.0), vec3(1.0 / 2.2));
    c += (bruitIGN(gl_FragCoord.xy) - 0.5) / 255.0;   // anti-bandes
    sortie = vec4(c, 1.0);
}
#endif
