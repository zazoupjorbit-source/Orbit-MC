#ifndef MINECRAFT_FOG_GLSL
#define MINECRAFT_FOG_GLSL

// ORBIT Tycoon : copie du fog.glsl de la 26.3 avec
//  - un brouillard qui monte en douceur (courbe adoucie au lieu d'une ligne droite) ;
//  - un etalonnage des couleurs "cinema" applique a tout ce qui passe par le brouillard
//    (blocs, entites, objets, particules, ciel) : contraste doux, saturation, ombres bleutees, lumieres dorees.

layout(std140) uniform Fog {
    vec4 FogColor;
    float FogEnvironmentalStart;
    float FogEnvironmentalEnd;
    float FogRenderDistanceStart;
    float FogRenderDistanceEnd;
    float FogSkyEnd;
    float FogCloudsEnd;
};

float linear_fog_value(float vertexDistance, float fogStart, float fogEnd) {
    if (vertexDistance <= fogStart) {
        return 0.0;
    } else if (vertexDistance >= fogEnd) {
        return 1.0;
    }

    float v = (vertexDistance - fogStart) / (fogEnd - fogStart);
    return mix(v, v * v * (3.0 - 2.0 * v), 0.5);
}

float total_fog_value(float sphericalVertexDistance, float cylindricalVertexDistance, float environmentalStart, float environmantalEnd, float renderDistanceStart, float renderDistanceEnd) {
    return max(linear_fog_value(sphericalVertexDistance, environmentalStart, environmantalEnd), linear_fog_value(cylindricalVertexDistance, renderDistanceStart, renderDistanceEnd));
}

vec3 orbit_etalonnage(vec3 c) {
    c = clamp(c, 0.0, 1.0);
    float l = dot(c, vec3(0.2126, 0.7152, 0.0722));
    float sat = max(c.r, max(c.g, c.b)) - min(c.r, min(c.g, c.b));
    c = mix(vec3(l), c, 1.45 - 0.30 * sat);                      // vibrance : avive surtout les couleurs ternes
    c = clamp(c, 0.0, 1.0);
    c = mix(c, c * c * (3.0 - 2.0 * c), 0.45);                   // contraste
    c = pow(c, vec3(0.94));                                      // eclaircit un peu les tons moyens
    c += vec3(-0.010, 0.004, 0.035) * (1.0 - l)                  // ombres bleu glace
       + vec3(0.030, 0.016, -0.015) * l;                         // lumieres or
    return clamp(c, 0.0, 1.0);
}

vec4 apply_fog(vec4 inColor, float sphericalVertexDistance, float cylindricalVertexDistance, float environmentalStart, float environmantalEnd, float renderDistanceStart, float renderDistanceEnd, vec4 fogColor) {
    float fogValue = total_fog_value(sphericalVertexDistance, cylindricalVertexDistance, environmentalStart, environmantalEnd, renderDistanceStart, renderDistanceEnd);
    vec3 rgb = mix(inColor.rgb, fogColor.rgb, fogValue * fogColor.a);
    // Pas d'etalonnage sur les surfaces transparentes (couleurs premultipliees dans l'OIT).
    if (inColor.a >= 0.999) rgb = orbit_etalonnage(rgb);
    return vec4(rgb, inColor.a);
}

float fog_spherical_distance(vec3 pos) {
    return length(pos);
}

float fog_cylindrical_distance(vec3 pos) {
    float distXZ = length(pos.xz);
    float distY = abs(pos.y);
    return max(distXZ, distY);
}

#endif
