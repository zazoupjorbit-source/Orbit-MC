// Orbit Shaders : réglages (lus par Iris pour construire le menu, voir shaders.properties et lang/)

// --- Éclairage ---
#define OMBRES                       // Ombres du soleil et de la lune
#define OMBRES_DOUCEUR 1.0           // [0.5 1.0 1.5 2.0 3.0]
const int shadowMapResolution = 2048; // [1024 2048 3072 4096]
const float shadowDistance = 128.0;  // [64.0 96.0 128.0 160.0 192.0 256.0]
const float sunPathRotation = -30.0; // [-45.0 -40.0 -30.0 -20.0 -10.0 0.0]
#define LUMIERE_EN_MAIN              // Une torche tenue en main éclaire autour du joueur
#define RAYONS                       // Rayons de lumière (soleil à travers les arbres, les nuages, les fenêtres)
#define RAYONS_FORCE 1.0             // [0.25 0.5 0.75 1.0 1.25 1.5 2.0]
#define RAYONS_PAS 12                // [6 8 12 16 24]

// --- Ciel ---
#define ETOILES 1.0                  // [0.0 0.5 1.0 1.5 2.0]
#define NEBULEUSE                    // Nébuleuse bleu et or dans le ciel de nuit
#define NEBULEUSE_FORCE 1.0          // [0.5 1.0 1.5 2.0]
#define SOLEIL_TAILLE 1.0            // [0.5 1.0 1.5 2.0]
#define BROUILLARD 1.0               // [0.0 0.5 1.0 1.5 2.0 3.0]

// --- Eau et végétation ---
#define EAU_VAGUES                   // Vagues sur l'eau
#define EAU_OPACITE 0.45             // [0.2 0.3 0.45 0.6 0.8]
#define VEGETATION_VENT              // Herbes, fleurs et feuilles qui bougent au vent
#define VENT_FORCE 1.0               // [0.5 1.0 1.5 2.0]

// --- Image ---
#define BLOOM                        // Halo autour des lumières fortes
#define BLOOM_FORCE 1.0              // [0.5 0.75 1.0 1.5 2.0]
#define EXPOSITION 1.0               // [0.6 0.8 1.0 1.2 1.4 1.6]
#define SATURATION 1.1               // [0.8 0.9 1.0 1.1 1.2 1.3]
#define TEINTE_ORBIT 1.0             // [0.0 0.5 1.0 1.5 2.0]
#define VIGNETTE 0.35                // [0.0 0.2 0.35 0.5 0.7]

// Iris ne propose une option « oui/non » que si elle est testée quelque part : on les teste ici.
#ifdef OMBRES
#endif
#ifdef LUMIERE_EN_MAIN
#endif
#ifdef RAYONS
#endif
#ifdef NEBULEUSE
#endif
#ifdef EAU_VAGUES
#endif
#ifdef VEGETATION_VENT
#endif
#ifdef BLOOM
#endif
