// Orbit Shaders : ciel (dégradé bleu, horizon doré au lever et au coucher, soleil rond, étoiles, nébuleuse ORBIT).
// À inclure après commun.glsl.

float bruit3(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash13(i);
    float b = hash13(i + vec3(1.0, 0.0, 0.0));
    float c = hash13(i + vec3(0.0, 1.0, 0.0));
    float d = hash13(i + vec3(1.0, 1.0, 0.0));
    float e = hash13(i + vec3(0.0, 0.0, 1.0));
    float g = hash13(i + vec3(1.0, 0.0, 1.0));
    float h = hash13(i + vec3(0.0, 1.0, 1.0));
    float k = hash13(i + vec3(1.0, 1.0, 1.0));
    return mix(mix(mix(a, b, f.x), mix(c, d, f.x), f.y), mix(mix(e, g, f.x), mix(h, k, f.x), f.y), f.z);
}

float fbm(vec3 p) {
    float s = 0.0;
    float a = 0.5;
    for (int i = 0; i < 5; i++) {
        s += a * bruit3(p);
        p = p * 2.03 + vec3(1.7, 9.2, 3.1);
        a *= 0.5;
    }
    return s;
}

// Direction du monde -> repère du ciel, qui tourne avec le soleil (les étoiles défilent dans la nuit).
vec3 versCeleste(vec3 d) {
    float r = radians(sunPathRotation);
    float a = sunAngle * 2.0 * PI;
    d = vec3(d.x, d.y * cos(r) - d.z * sin(r), d.y * sin(r) + d.z * cos(r));
    d = vec3(d.x * cos(a) - d.y * sin(a), d.x * sin(a) + d.y * cos(a), d.z);
    return d;
}

vec3 etoiles(vec3 d) {
    vec3 p = d * 230.0;
    vec3 cel = floor(p);
    float h = hash13(cel);
    if (h < 0.9955) return vec3(0.0);
    vec3 centre = cel + 0.5 + (vec3(hash13(cel + 7.1), hash13(cel + 3.3), hash13(cel + 1.9)) - 0.5) * 0.5;
    float forme = 1.0 - smoothstep(0.0, 0.45, length(p - centre));
    float eclat = (h - 0.9955) / 0.0045;
    float scintille = 0.65 + 0.35 * sin(frameTimeCounter * (2.0 + eclat * 4.0) + h * 300.0);
    vec3 teinte = mix(vec3(0.75, 0.85, 1.00), vec3(1.00, 0.82, 0.50), step(0.75, hash13(cel + 5.0)));
    return teinte * forme * (0.6 + eclat * 2.5) * scintille;
}

// Bande de nébuleuse : nuages bleus et violets, filaments dorés (la DA d'ORBIT dans le ciel de nuit).
vec3 nebuleuse(vec3 d) {
    vec3 axe = normalize(vec3(0.3, 0.2, 1.0));
    float bande = exp(-pow(dot(d, axe) * 2.6, 2.0));
    if (bande < 0.01) return vec3(0.0);
    float n = fbm(d * 3.0);
    float n2 = fbm(d * 6.0 + 11.0);
    float nuage = smoothstep(0.35, 0.85, n) * bande;
    vec3 c = mix(vec3(0.10, 0.22, 0.85), vec3(0.45, 0.25, 0.85), n2) * nuage * 0.10;
    float fil = smoothstep(0.55, 0.95, n2) * bande;
    c += vec3(1.00, 0.72, 0.30) * fil * fil * 0.12;
    return c;
}

// Couleur du ciel dans une direction (repère de la caméra). astres : soleil, étoiles et nébuleuse en plus.
vec3 cielCouleur(vec3 dir, bool astres) {
#ifdef SANS_CIEL
    return versLineaire(fogColor);
#else
    vec3 sunV = normalize(sunPosition);
    vec3 upV = normalize(upPosition);
    float h = hauteurSoleil();
    float jour = facteurJour();
    float nuit = facteurNuit();
    float couchant = facteurCouchant();
    float y = dot(dir, upV);
    float cosS = dot(dir, sunV);

    vec3 zenith = mix(vec3(0.008, 0.013, 0.038), vec3(0.05, 0.17, 0.62), jour);
    vec3 horizon = mix(vec3(0.030, 0.048, 0.11), vec3(0.38, 0.56, 0.95), jour);
    // Lever et coucher : horizon doré côté soleil, mauve de l'autre côté.
    float coteSoleil = pow(cosS * 0.5 + 0.5, 1.6);
    horizon = mix(horizon, vec3(1.00, 0.48, 0.16) * 0.85, couchant * coteSoleil);
    horizon = mix(horizon, vec3(0.42, 0.36, 0.60) * 0.8, couchant * (1.0 - coteSoleil) * 0.6);
    zenith = mix(zenith, vec3(0.10, 0.16, 0.42), couchant * 0.5);

    vec3 c = mix(horizon, zenith, pow(clamp(y, 0.0, 1.0), 0.42));
    c = mix(horizon * 0.35, c, smoothstep(-0.35, 0.0, y));   // sous l'horizon : plus sombre

    // Halo doré autour du soleil, plus large quand il est bas.
    float halo = pow(max(cosS, 0.0), 6.0) * (0.12 + couchant * 0.22) + pow(max(cosS, 0.0), 60.0) * 0.30;
    c += mix(vec3(1.00, 0.70, 0.36), vec3(1.00, 0.46, 0.14), couchant) * halo * smoothstep(-0.15, 0.05, h);
    // Halo bleu de la lune.
    c += BLEU_LUNE * pow(max(dot(dir, normalize(moonPosition)), 0.0), 30.0) * 0.08 * nuit;

    if (astres) {
        float rayon = 0.0085 * SOLEIL_TAILLE;
        float disque = smoothstep(cos(rayon * 1.15), cos(rayon), cosS) * smoothstep(-0.01, 0.01, y);
        c += vec3(1.00, 0.85, 0.60) * disque * 40.0 * (1.0 - rainStrength);

        vec3 dirC = versCeleste(mat3(gbufferModelViewInverse) * dir);
        float visible = nuit * (1.0 - rainStrength) * smoothstep(-0.05, 0.15, y);
        if (visible > 0.0) {
            c += etoiles(dirC) * visible * ETOILES;
#ifdef NEBULEUSE
            c += nebuleuse(dirC) * visible * NEBULEUSE_FORCE;
#endif
        }
    }

    vec3 gris = vec3(lum(c)) * vec3(0.82, 0.88, 1.00);
    return mix(c, gris * 0.7, rainStrength * 0.85);
#endif
}
