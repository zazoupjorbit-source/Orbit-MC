package fr.orbit.mmo.continent;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Données du continent, écrites par {@code tools\gen-continent.ps1 -Donnees} (format décrit dans
 * {@code tools\continent\Cartographe.cs}, méthode WriteData) : relief, niveaux d'eau et de lave, biomes,
 * régions, lieux et profil des routes. Lecture seule une fois chargées, donc utilisables depuis les fils
 * de génération des chunks.
 */
public final class ContinentData {

    // Biomes du dessin (mêmes numéros que dans Cartographe.cs).
    public static final int OCEAN = 0, WATER = 1, BEACH = 2, PLAIN = 3, MOOR = 4, FOREST = 5, DARK_FOREST = 6,
            HILLS = 7, PINES = 8, ALP = 9, ROCK = 10, SNOW = 11, SAND = 12, MESA = 13, OASIS = 14, MARSH = 15,
            ASH = 16, VOLCANIC = 17, LAVA = 18, BLACK_BEACH = 19;
    public static final String[] BIOME_NAMES = {"Océan", "Eau douce", "Plage", "Plaine", "Lande", "Forêt claire",
            "Forêt sombre", "Collines", "Forêt de pins", "Alpage", "Roche", "Neige", "Sable", "Mesa et canyons",
            "Oasis", "Marais", "Cendres", "Roche volcanique", "Lave", "Plage noire"};

    private static final int MAGIC = 0x4F434F4E;
    /** Hauteur du fond hors de la carte (océan sans fin). */
    private static final double OUTSIDE = 40;

    public record Region(String id, String name, String faction, int levelMin, int levelMax, String terrain) {
    }

    public record Place(String kind, String name, String faction, int x, int z, int safe) {
    }

    public static final class Road {
        public final String kind;
        public final String name;
        public final boolean royal;
        public final float[] x;
        public final float[] z;
        public final float[] y;
        public final boolean[] bridge;

        Road(String kind, String name, int n) {
            this.kind = kind;
            this.name = name;
            this.royal = kind.equals("royale");
            this.x = new float[n];
            this.z = new float[n];
            this.y = new float[n];
            this.bridge = new boolean[n];
        }

        /** Demi-largeur de la chaussée, en blocs. */
        public double halfWidth() {
            return royal ? 3.2 : 1.8;
        }
    }

    /** Résultat d'une recherche de route ; un objet par appelant, réutilisé d'une colonne à l'autre. */
    public static final class RoadHit {
        public Road road;
        public double dist;
        public double y;
        public boolean bridge;
        public int sample;
    }

    public final int size;
    public final int n;
    public final double sea;
    /** Empreinte du relief : change dès que le dessin est régénéré autrement. */
    public final long signature;
    private final double cell;
    private final double half;
    private final short[] height;
    private final byte[] fluid;
    private final byte[] biome;
    private final byte[] region;
    public final List<Region> regions = new ArrayList<>();
    public final List<Place> places = new ArrayList<>();
    public final List<Road> roads = new ArrayList<>();
    /** Par chunk : les points de route proches, codés (route << 20 | point). */
    private final Map<Long, int[]> roadIndex = new HashMap<>();
    /** Marge autour de la chaussée couverte par l'index (pour écarter les arbres). */
    public static final double ROAD_MARGIN = 5;

    private ContinentData(DataInputStream in) throws IOException {
        if (in.readInt() != MAGIC) {
            throw new IOException("ce n'est pas un fichier de continent");
        }
        int version = in.readInt();
        if (version != 1) {
            throw new IOException("version " + version + " inconnue");
        }
        size = in.readInt();
        n = in.readInt();
        sea = in.readFloat();
        cell = (double) size / n;
        half = size / 2.0;
        for (int i = in.readInt(); i > 0; i--) {
            regions.add(new Region(in.readUTF(), in.readUTF(), in.readUTF(), in.readInt(), in.readInt(), in.readUTF()));
        }
        for (int i = in.readInt(); i > 0; i--) {
            places.add(new Place(in.readUTF(), in.readUTF(), in.readUTF(), in.readInt(), in.readInt(), in.readInt()));
        }
        for (int i = in.readInt(); i > 0; i--) {
            Road r = new Road(in.readUTF(), in.readUTF(), in.readInt());
            for (int k = 0; k < r.x.length; k++) {
                r.x[k] = in.readFloat();
                r.z[k] = in.readFloat();
                r.y[k] = in.readFloat();
                r.bridge[k] = in.readByte() != 0;
            }
            roads.add(r);
        }
        int n2 = n * n;
        byte[] raw = new byte[n2 * 2];
        in.readFully(raw);
        height = new short[n2];
        for (int i = 0; i < n2; i++) {
            height[i] = (short) (((raw[i * 2] & 0xFF) << 8) | (raw[i * 2 + 1] & 0xFF));
        }
        fluid = new byte[n2];
        in.readFully(fluid);
        biome = new byte[n2];
        in.readFully(biome);
        region = new byte[n2];
        in.readFully(region);
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(raw);
        crc.update(fluid);
        crc.update(biome);
        for (Road r : roads) {
            crc.update(r.x.length);
            crc.update(Float.floatToIntBits(r.y[r.y.length / 2]));
        }
        signature = crc.getValue();
        indexRoads();
    }

    public static ContinentData load(InputStream stream) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(stream, 1 << 16), 1 << 16))) {
            return new ContinentData(in);
        }
    }

    private void indexRoads() {
        Map<Long, List<Integer>> tmp = new HashMap<>();
        for (int ri = 0; ri < roads.size(); ri++) {
            Road r = roads.get(ri);
            double reach = r.halfWidth() + ROAD_MARGIN + 2;
            for (int k = 0; k < r.x.length; k++) {
                int c0 = (int) Math.floor(r.x[k] - reach) >> 4, c1 = (int) Math.floor(r.x[k] + reach) >> 4;
                int d0 = (int) Math.floor(r.z[k] - reach) >> 4, d1 = (int) Math.floor(r.z[k] + reach) >> 4;
                for (int cx = c0; cx <= c1; cx++) {
                    for (int cz = d0; cz <= d1; cz++) {
                        tmp.computeIfAbsent(key(cx, cz), q -> new ArrayList<>()).add((ri << 20) | k);
                    }
                }
            }
        }
        for (Map.Entry<Long, List<Integer>> e : tmp.entrySet()) {
            int[] a = new int[e.getValue().size()];
            for (int i = 0; i < a.length; i++) {
                a[i] = e.getValue().get(i);
            }
            roadIndex.put(e.getKey(), a);
        }
    }

    private static long key(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    /** Vrai si le bloc est dans la carte (au-delà : océan). */
    public boolean inside(int bx, int bz) {
        return bx >= -half && bx < half && bz >= -half && bz < half;
    }

    private int pixel(int bx, int bz) {
        int px = (int) Math.floor((bx + 0.5 + half) / cell), pz = (int) Math.floor((bz + 0.5 + half) / cell);
        if (px < 0 || pz < 0 || px >= n || pz >= n) {
            return -1;
        }
        return pz * n + px;
    }

    /** Hauteur du sol (le dessus du dernier bloc plein), interpolée entre les points de la carte. */
    public double height(int bx, int bz) {
        double u = (bx + 0.5 + half) / cell - 0.5, v = (bz + 0.5 + half) / cell - 0.5;
        int x0 = (int) Math.floor(u), z0 = (int) Math.floor(v);
        if (x0 < 0 || z0 < 0 || x0 >= n - 1 || z0 >= n - 1) {
            return OUTSIDE;
        }
        double fx = u - x0, fz = v - z0;
        int i = z0 * n + x0;
        double a = (height[i] & 0xFFFF) / 64.0, b = (height[i + 1] & 0xFFFF) / 64.0;
        double c = (height[i + n] & 0xFFFF) / 64.0, d = (height[i + n + 1] & 0xFFFF) / 64.0;
        double top = a + (b - a) * fx, bottom = c + (d - c) * fx;
        return top + (bottom - top) * fz;
    }

    /**
     * Niveau du fluide : Y de sa surface (le dernier bloc de fluide est un cran dessous), 0 s'il n'y en a pas,
     * négatif si c'est de la lave. On prend le plus haut des quatre points voisins pour que la berge suive le relief.
     */
    public int fluid(int bx, int bz) {
        double u = (bx + 0.5 + half) / cell - 0.5, v = (bz + 0.5 + half) / cell - 0.5;
        int x0 = (int) Math.floor(u), z0 = (int) Math.floor(v);
        if (x0 < 0 || z0 < 0 || x0 >= n - 1 || z0 >= n - 1) {
            return (int) sea;
        }
        int i = z0 * n + x0, best = 0;
        boolean lava = false;
        for (int k = 0; k < 4; k++) {
            int j = i + (k & 1) + (k >> 1) * n;
            int f = fluid[j] & 0xFF;
            if (f > best) {
                best = f;
                lava = biome[j] == LAVA;
            }
        }
        return lava ? -best : best;
    }

    public int biome(int bx, int bz) {
        int p = pixel(bx, bz);
        return p < 0 ? OCEAN : biome[p];
    }

    /** Région du bloc, ou null hors de la carte. */
    public Region region(int bx, int bz) {
        int p = pixel(bx, bz);
        if (p < 0) {
            return null;
        }
        int r = region[p] & 0xFF;
        return r < regions.size() ? regions.get(r) : null;
    }

    public boolean chunkHasRoad(int cx, int cz) {
        return roadIndex.containsKey(key(cx, cz));
    }

    /**
     * Cherche la route la plus proche du bloc, à moins de (demi-largeur + marge) blocs. Remplit {@code hit}.
     */
    public boolean road(int bx, int bz, double margin, RoadHit hit) {
        int[] list = roadIndex.get(key(bx >> 4, bz >> 4));
        if (list == null) {
            return false;
        }
        double px = bx + 0.5, pz = bz + 0.5, best = Double.MAX_VALUE;
        boolean bestRoyal = false;
        for (int code : list) {
            Road r = roads.get(code >>> 20);
            int k = code & 0xFFFFF;
            double ax = r.x[k], az = r.z[k], t = 0;
            int k2 = Math.min(k + 1, r.x.length - 1);
            double vx = r.x[k2] - ax, vz = r.z[k2] - az, len2 = vx * vx + vz * vz;
            if (len2 > 1e-9) {
                t = ((px - ax) * vx + (pz - az) * vz) / len2;
                t = t < 0 ? 0 : (t > 1 ? 1 : t);
            }
            double qx = ax + vx * t - px, qz = az + vz * t - pz;
            double d2 = qx * qx + qz * qz;
            double limit = r.halfWidth() + margin;
            if (d2 > limit * limit) {
                continue;
            }
            // Aux carrefours, la chaussée de la route royale l'emporte sur celle d'un chemin.
            boolean onRoyal = r.royal && d2 <= r.halfWidth() * r.halfWidth();
            if (bestRoyal && !onRoyal) {
                continue;
            }
            if (d2 < best || (onRoyal && !bestRoyal)) {
                best = d2;
                bestRoyal = onRoyal;
                hit.road = r;
                hit.dist = Math.sqrt(d2);
                hit.y = r.y[k] + (r.y[k2] - r.y[k]) * t;
                hit.bridge = r.bridge[k] || r.bridge[k2];
                hit.sample = k;
            }
        }
        return best != Double.MAX_VALUE;
    }

    /** Vrai dans l'emprise d'une capitale, d'une ville ou d'un village : terrain laissé libre pour les constructions. */
    public boolean reserved(int bx, int bz) {
        for (Place p : places) {
            if (p.safe() <= 0) {
                continue;
            }
            double r = p.safe() * 0.85, dx = bx - p.x(), dz = bz - p.z();
            if (dx * dx + dz * dz < r * r) {
                return true;
            }
        }
        return false;
    }

    /** Lieu dont le nom commence par le texte donné, ou le contient (sans tenir compte des accents ni de la casse). */
    public Place place(String text) {
        String want = plain(text);
        if (want.isEmpty()) {
            return null;
        }
        Place starts = null, contains = null;
        for (Place p : places) {
            String name = plain(p.name());
            if (name.equals(want)) {
                return p;
            }
            if (starts == null && name.startsWith(want)) {
                starts = p;
            }
            if (contains == null && name.contains(want)) {
                contains = p;
            }
        }
        return starts != null ? starts : contains;
    }

    /** Lieu sûr (capitale, ville, village) qui contient le bloc, ou null. */
    public Place placeAt(int bx, int bz) {
        for (Place p : places) {
            double dx = bx - p.x(), dz = bz - p.z();
            if (p.safe() > 0 && dx * dx + dz * dz < (double) p.safe() * p.safe()) {
                return p;
            }
        }
        return null;
    }

    /** Lieu le plus proche du bloc. */
    public Place nearest(int bx, int bz) {
        Place best = null;
        double bestD = Double.MAX_VALUE;
        for (Place p : places) {
            double dx = bx - p.x(), dz = bz - p.z(), d = dx * dx + dz * dz;
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    public static String plain(String s) {
        String d = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return d.toLowerCase(Locale.ROOT).replace("œ", "oe").replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }
}
