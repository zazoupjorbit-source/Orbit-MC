package fr.orbit.tele;

/**
 * Couleurs des cartes Minecraft : passe d'une couleur d'écran (RVB) à l'indice de carte le plus proche à l'œil
 * (distance Oklab), par une table calculée une fois.
 * <p>
 * Tramage « à la demande » : un motif fixe (stable d'une image à l'autre, donc sans scintillement) mélange deux
 * couleurs voisines, mais seulement là où la palette est loin de la couleur voulue, et d'autant plus fort qu'elle
 * en est loin. Un aplat que la palette sait rendre reste net, un dégradé ou une photo est adouci.
 */
public final class Palette {

    private static final int[] NUANCES = {180, 220, 255, 135};
    private static final int[] BAYER = {
            0, 32, 8, 40, 2, 34, 10, 42, 48, 16, 56, 24, 50, 18, 58, 26,
            12, 44, 4, 36, 14, 46, 6, 38, 60, 28, 52, 20, 62, 30, 54, 22,
            3, 35, 11, 43, 1, 33, 9, 41, 51, 19, 59, 27, 49, 17, 57, 25,
            15, 47, 7, 39, 13, 45, 5, 37, 63, 31, 55, 23, 61, 29, 53, 21};

    /** Couleur réelle (RVB) de chaque indice de carte, 0 = transparent. */
    final int[] rvb = new int[256];
    /** 6 bits par composante → indice de carte. */
    final byte[] table = new byte[1 << 18];
    /** 6 bits par composante → écart entre la couleur voulue et celle de la carte (0 = exacte). */
    final byte[] ecart = new byte[1 << 18];
    /** Motif 8 × 8, de -32 à 31. */
    final int[] motif = new int[64];
    /** Amplitude du tramage (en 64es de niveau) pour chaque écart. */
    final int[] ampli = new int[256];

    /**
     * @param bases couleur de base de chaque teinte de carte (indice = id de la teinte, 0 = absente)
     * @param force tramage : 0 = aucun, 22 = normal, 40 = marqué
     */
    public Palette(int[] bases, int force) {
        int n = 0;
        int[] indices = new int[256];
        for (int id = 1; id < bases.length && id < 64; id++) {
            if (bases[id] == 0) {
                continue;
            }
            for (int s = 0; s < 4; s++) {
                int r = ((bases[id] >> 16) & 255) * NUANCES[s] / 255;
                int g = ((bases[id] >> 8) & 255) * NUANCES[s] / 255;
                int b = (bases[id] & 255) * NUANCES[s] / 255;
                int i = id * 4 + s;
                rvb[i] = (r << 16) | (g << 8) | b;
                indices[n++] = i;
            }
        }
        float[] pl = new float[n], pa = new float[n], pb = new float[n];
        float[] lab = new float[3];
        for (int k = 0; k < n; k++) {
            oklab(rvb[indices[k]], lab);
            pl[k] = lab[0];
            pa[k] = lab[1];
            pb[k] = lab[2];
        }
        for (int c = 0; c < table.length; c++) {
            int r = ((c >> 12) & 63) << 2 | 2, g = ((c >> 6) & 63) << 2 | 2, b = (c & 63) << 2 | 2;
            oklab((r << 16) | (g << 8) | b, lab);
            float best = Float.MAX_VALUE;
            int bi = 0;
            for (int k = 0; k < n; k++) {
                float dl = lab[0] - pl[k], da = lab[1] - pa[k], db = lab[2] - pb[k];
                float d = dl * dl + da * da + db * db;
                if (d < best) {
                    best = d;
                    bi = k;
                }
            }
            table[c] = (byte) indices[bi];
            // Écart mesuré en niveaux d'écran (et non en Oklab, qui exagère les différences dans le noir).
            int p = rvb[indices[bi]];
            int dr = r - ((p >> 16) & 255), dg = g - ((p >> 8) & 255), db = b - (p & 255);
            ecart[c] = (byte) Math.min(255, Math.round(Math.sqrt((2 * dr * dr + 4 * dg * dg + 3 * db * db) / 9.0)));
        }
        for (int i = 0; i < 64; i++) {
            motif[i] = BAYER[i] - 32;
        }
        // Sous 6 niveaux d'écart on ne voit rien : pas de tramage. Au-delà, l'amplitude suit l'écart
        // (deux couleurs voisines de la palette sont à peu près à deux écarts l'une de l'autre).
        for (int e = 0; e < 256; e++) {
            ampli[e] = e < 6 ? 0 : Math.min(96, Math.round(e * 2f * force / 22f));
        }
    }

    /** Indice de carte d'un point de l'image. */
    public byte indice(int c, int x, int y, boolean trame) {
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        int k = ((r >> 2) << 12) | ((g >> 2) << 6) | (b >> 2);
        int a = trame ? ampli[ecart[k] & 255] : 0;
        if (a == 0) {
            return table[k];
        }
        int d = (motif[((y & 7) << 3) | (x & 7)] * a) >> 6;
        r += d;
        g += d;
        b += d;
        r = r < 0 ? 0 : Math.min(r, 255);
        g = g < 0 ? 0 : Math.min(g, 255);
        b = b < 0 ? 0 : Math.min(b, 255);
        return table[((r >> 2) << 12) | ((g >> 2) << 6) | (b >> 2)];
    }

    private static float lin(int v) {
        float c = v / 255f;
        return c <= 0.04045f ? c / 12.92f : (float) Math.pow((c + 0.055f) / 1.055f, 2.4f);
    }

    private static void oklab(int c, float[] out) {
        float r = lin((c >> 16) & 255), g = lin((c >> 8) & 255), b = lin(c & 255);
        float l = (float) Math.cbrt(0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * b);
        float m = (float) Math.cbrt(0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * b);
        float s = (float) Math.cbrt(0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * b);
        out[0] = 0.2104542553f * l + 0.7936177850f * m - 0.0040720468f * s;
        out[1] = 1.9779984951f * l - 2.4285922050f * m + 0.4505937099f * s;
        out[2] = 0.0259040371f * l + 0.7827717662f * m - 0.8086757660f * s;
    }
}
