import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import javax.imageio.ImageIO;

/**
 * Analyse hors ligne du monde d'extraction (fichiers région Anvil) : relief, terre/mer, plus grande île,
 * et points d'apparition possibles au niveau du sol (rues, places, plages) sur cette île.
 *
 * Usage : java MapScan.java <dossier region> <dossier de sortie> [pas=4]
 * Sorties : carte.png (aperçu), apparitions-auto.csv (x,y,z des points retenus), extractions-auto.csv, stats sur la sortie standard.
 * Le plugin relit apparitions-auto.csv et revérifie chaque point en jeu avant d'y déployer un joueur.
 */
public class MapScan {
    static final int AIR = 0, WATER = 1, LEAVES = 2, THIN = 3, SOLID = 4, NONE = -1;
    static final int SEA = 62;
    static int unknown;

    public static void main(String[] a) throws Exception {
        Path regionDir = Path.of(a[0]);
        Path out = Path.of(a[1]);
        int step = a.length > 2 ? Integer.parseInt(a[2]) : 4;
        Files.createDirectories(out);

        List<int[]> regions = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(regionDir, "r.*.mca")) {
            for (Path p : ds) {
                String[] s = p.getFileName().toString().split("\\.");
                int rx = Integer.parseInt(s[1]), rz = Integer.parseInt(s[2]);
                if (Math.abs(rx) <= 9 && Math.abs(rz) <= 9 && Files.size(p) > 8192) regions.add(new int[]{rx, rz});
            }
        }
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int[] r : regions) { minX = Math.min(minX, r[0]); maxX = Math.max(maxX, r[0]); minZ = Math.min(minZ, r[1]); maxZ = Math.max(maxZ, r[1]); }
        int ox = minX * 512, oz = minZ * 512;
        int gw = (maxX - minX + 1) * 512 / step, gh = (maxZ - minZ + 1) * 512 / step;
        short[][] top = new short[gw][gh];
        byte[][] cat = new byte[gw][gh];
        for (byte[] c : cat) Arrays.fill(c, (byte) NONE);

        long t0 = System.currentTimeMillis();
        int chunks = 0, failed = 0;
        for (int[] r : regions) {
            Path f = regionDir.resolve("r." + r[0] + "." + r[1] + ".mca");
            byte[] data = Files.readAllBytes(f);
            for (int i = 0; i < 1024; i++) {
                int off = ((data[i * 4] & 255) << 16) | ((data[i * 4 + 1] & 255) << 8) | (data[i * 4 + 2] & 255);
                if (off == 0 || off * 4096 + 5 > data.length) continue;
                int p = off * 4096;
                int len = ((data[p] & 255) << 24) | ((data[p + 1] & 255) << 16) | ((data[p + 2] & 255) << 8) | (data[p + 3] & 255);
                int comp = data[p + 4];
                if (len <= 1 || p + 4 + len > data.length) continue;
                InputStream raw = new ByteArrayInputStream(data, p + 5, len - 1);
                InputStream in = comp == 1 ? new GZIPInputStream(raw) : comp == 2 ? new InflaterInputStream(raw) : comp == 3 ? raw : null;
                if (in == null) continue;
                Map<String, Object> root;
                try (DataInputStream din = new DataInputStream(new BufferedInputStream(in))) {
                    int t = din.readByte();
                    din.readUTF();
                    root = cast(read(din, t));
                } catch (IOException e) {
                    failed++;
                    if (failed <= 3) System.out.println("chunk illisible " + f.getFileName() + " #" + i + " : " + e);
                    continue;
                }
                int cx = r[0] * 32 + (i & 31), cz = r[1] * 32 + (i >> 5);
                scanChunk(root, cx, cz, ox, oz, step, top, cat);
                chunks++;
            }
        }
        int[] cc = new int[6];
        for (byte[] col : cat) for (byte b : col) cc[b + 1]++;
        System.out.println("illisibles " + failed + " ; cellules : vides " + cc[0] + ", eau " + cc[2] + ", feuilles " + cc[3] + ", solide " + cc[5]);
        System.out.println(chunks + " chunks lus en " + (System.currentTimeMillis() - t0) / 1000 + " s ; grille " + gw + "x" + gh + " (pas " + step + ")");

        // ---- composantes de terre (4-voisins)
        int[][] comp = new int[gw][gh];
        List<Integer> sizes = new ArrayList<>();
        sizes.add(0);
        ArrayDeque<int[]> q = new ArrayDeque<>();
        for (int x = 0; x < gw; x++) for (int z = 0; z < gh; z++) {
            if (!land(cat, top, x, z) || comp[x][z] != 0) continue;
            int id = sizes.size(), n = 0;
            comp[x][z] = id;
            q.add(new int[]{x, z});
            while (!q.isEmpty()) {
                int[] c = q.poll();
                n++;
                for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int nx = c[0] + d[0], nz = c[1] + d[1];
                    if (nx >= 0 && nz >= 0 && nx < gw && nz < gh && comp[nx][nz] == 0 && land(cat, top, nx, nz)) {
                        comp[nx][nz] = id;
                        q.add(new int[]{nx, nz});
                    }
                }
            }
            sizes.add(n);
        }
        Integer[] order = new Integer[sizes.size() - 1];
        for (int i = 0; i < order.length; i++) order[i] = i + 1;
        Arrays.sort(order, (p, s) -> sizes.get(s) - sizes.get(p));
        System.out.println("Iles (cellules de " + step + "x" + step + " blocs) :");
        for (int k = 0; k < Math.min(8, order.length); k++) {
            int id = order[k];
            int x0 = gw, x1 = 0, z0 = gh, z1 = 0;
            for (int x = 0; x < gw; x++) for (int z = 0; z < gh; z++) if (comp[x][z] == id) { x0 = Math.min(x0, x); x1 = Math.max(x1, x); z0 = Math.min(z0, z); z1 = Math.max(z1, z); }
            System.out.printf("  #%d : %d cellules (~%.2f km2), x %d..%d, z %d..%d%n", k + 1, sizes.get(id), sizes.get(id) * step * step / 1e6,
                    ox + x0 * step, ox + x1 * step, oz + z0 * step, oz + z1 * step);
        }
        int big = order[0];

        // ---- relief de la grande ile
        int[] hist = new int[64];
        for (int x = 0; x < gw; x++) for (int z = 0; z < gh; z++) if (comp[x][z] == big) hist[Math.max(0, Math.min(63, (top[x][z] + 64) / 8))]++;
        System.out.println("Hauteur du dessus (grande ile), par tranches de 8 blocs :");
        for (int i = 0; i < 64; i++) if (hist[i] > 0) System.out.printf("  y %4d..%4d : %d%n", i * 8 - 64, i * 8 - 57, hist[i]);

        // ---- points d'apparition : sol degage et plat, pas un toit (pas plus haut que le sol alentour)
        int R = Math.max(1, 24 / step);            // voisinage de 24 blocs pour estimer le niveau du sol
        List<int[]> cands = new ArrayList<>();
        boolean[][] isCand = new boolean[gw][gh];
        for (int x = R; x < gw - R; x++) for (int z = R; z < gh - R; z++) {
            if (comp[x][z] != big || cat[x][z] != SOLID) continue;
            int y = top[x][z];
            if (y <= SEA) continue;
            boolean flat = true;
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int nx = x + d[0], nz = z + d[1];
                if (cat[nx][nz] != SOLID || Math.abs(top[nx][nz] - y) > 1) { flat = false; break; }
            }
            if (!flat) continue;
            // niveau du sol local = 15e centile des hauteurs de terre dans le voisinage
            int[] hs = new int[(2 * R + 1) * (2 * R + 1)];
            int n = 0;
            for (int dx = -R; dx <= R; dx++) for (int dz = -R; dz <= R; dz++) if (land(cat, top, x + dx, z + dz)) hs[n++] = top[x + dx][z + dz];
            if (n < hs.length / 3) continue;       // bord de mer trop etroit
            Arrays.sort(hs, 0, n);
            int ground = hs[n * 15 / 100];
            if (y > ground + 3) continue;          // toit, terrasse, pont
            cands.add(new int[]{ox + x * step, y + 1, oz + z * step});
            isCand[x][z] = true;
        }
        System.out.println(cands.size() + " points d'apparition possibles sur la grande ile");
        Collections.shuffle(cands, new Random(7));
        List<int[]> kept = cands.subList(0, Math.min(6000, cands.size()));
        StringBuilder sb = new StringBuilder("# x,y,z (pieds du joueur) — genere par tools/MapScan.java, verifie en jeu avant usage\n");
        for (int[] c : kept) sb.append(c[0]).append(',').append(c[1]).append(',').append(c[2]).append('\n');
        Files.writeString(out.resolve("apparitions-auto.csv"), sb.toString());

        // ---- extractions proposees : le point retenu le plus eloigne du centre, dans 6 directions
        double mx = 0, mz = 0;
        for (int[] c : cands) { mx += c[0]; mz += c[2]; }
        mx /= Math.max(1, cands.size()); mz /= Math.max(1, cands.size());
        String[] names = {"Est", "Sud-est", "Sud-ouest", "Ouest", "Nord-ouest", "Nord-est"};
        StringBuilder ex = new StringBuilder("# nom,x,y,z — points les plus excentres de la grande ile\n");
        List<int[]> exPts = new ArrayList<>();
        for (int k = 0; k < 6; k++) {
            double ang = Math.toRadians(k * 60), ux = Math.cos(ang), uz = Math.sin(ang);
            int[] best = null;
            double bestP = -1e18;
            for (int[] c : cands) {
                double dx = c[0] - mx, dz = c[2] - mz;
                double along = dx * ux + dz * uz, across = Math.abs(-dx * uz + dz * ux);
                double score = along - across * 0.6;
                if (score > bestP) { bestP = score; best = c; }
            }
            if (best != null) { ex.append(names[k]).append(',').append(best[0]).append(',').append(best[1]).append(',').append(best[2]).append('\n'); exPts.add(best); }
        }
        Files.writeString(out.resolve("extractions-auto.csv"), ex.toString());
        System.out.printf("Centre des points : %.0f, %.0f%n%s", mx, mz, ex);

        // ---- apercu
        BufferedImage img = new BufferedImage(gw, gh, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < gw; x++) for (int z = 0; z < gh; z++) {
            int c;
            if (cat[x][z] == NONE) c = 0x101014;
            else if (!land(cat, top, x, z)) c = 0x1B3A5C;
            else {
                int y = top[x][z];
                double t = Math.max(0, Math.min(1, (y - SEA) / 120.0));
                int g = (int) (70 + 150 * t);
                c = comp[x][z] == big ? (g << 16) | (g << 8) | g : ((g / 2) << 16) | ((g / 2) << 8) | 0x50;
                if (cat[x][z] == LEAVES) c = comp[x][z] == big ? 0x2E7D32 : 0x1B4D20;
                if (isCand[x][z]) c = 0xF2A93B;
            }
            img.setRGB(x, z, c);
        }
        for (int[] e : exPts) for (int dx = -5; dx <= 5; dx++) for (int dz = -5; dz <= 5; dz++) {
            int px = (e[0] - ox) / step + dx, pz = (e[2] - oz) / step + dz;
            if (px >= 0 && pz >= 0 && px < gw && pz < gh && (Math.abs(dx) == 5 || Math.abs(dz) == 5 || Math.abs(dx) + Math.abs(dz) < 3)) img.setRGB(px, pz, 0xFF2020);
        }
        ImageIO.write(img, "png", out.resolve("carte.png").toFile());
        System.out.println("carte.png : origine " + ox + "," + oz + ", 1 px = " + step + " blocs");
    }

    static boolean land(byte[][] cat, short[][] top, int x, int z) {
        if (x < 0 || z < 0 || x >= cat.length || z >= cat[0].length) return false;
        return (cat[x][z] == SOLID || cat[x][z] == LEAVES) && top[x][z] > SEA;
    }

    @SuppressWarnings("unchecked")
    static <T> T cast(Object o) { return (T) o; }

    static void scanChunk(Map<String, Object> root, int cx, int cz, int ox, int oz, int step, short[][] top, byte[][] cat) {
        List<Object> sections = cast(root.get("sections"));
        if (sections == null) return;
        int n = sections.size();
        int[] ys = new int[n];
        byte[][] pcat = new byte[n][];
        long[][] datas = new long[n][];
        int[] bits = new int[n];
        Integer[] idx = new Integer[n];
        for (int i = 0; i < n; i++) {
            Map<String, Object> s = cast(sections.get(i));
            ys[i] = ((Number) s.get("Y")).intValue();
            idx[i] = i;
            Map<String, Object> bs = cast(s.get("block_states"));
            if (bs == null) { pcat[i] = new byte[]{AIR}; continue; }
            List<Object> pal = cast(bs.get("palette"));
            pcat[i] = new byte[pal.size()];
            for (int k = 0; k < pal.size(); k++) {
                // 26.x : une entrée sans propriété est une simple chaîne, sinon un compound {Name, Properties}.
                Object e = pal.get(k);
                String nm = e instanceof String s2 ? s2 : (String) ((Map<?, ?>) e).get("Name");
                // 26.x : {id, properties} ; dans une liste mixte, une simple chaîne est emballée en {"": chaîne}.
                if (nm == null && e instanceof Map<?, ?> em) { nm = (String) em.get("id"); if (nm == null) nm = (String) em.get(""); if (nm == null && unknown++ < 3) System.out.println("palette inconnue : " + em); }
                pcat[i][k] = (byte) category(nm);
            }
            datas[i] = (long[]) bs.get("data");
            bits[i] = Math.max(4, 32 - Integer.numberOfLeadingZeros(Math.max(1, pal.size() - 1)));
        }
        Arrays.sort(idx, (p, s) -> ys[s] - ys[p]);   // du haut vers le bas
        for (int lx = 0; lx < 16; lx++) for (int lz = 0; lz < 16; lz++) {
            int wx = cx * 16 + lx, wz = cz * 16 + lz;
            if (Math.floorMod(wx, step) != 0 || Math.floorMod(wz, step) != 0) continue;
            int gx = (wx - ox) / step, gz = (wz - oz) / step;
            if (gx < 0 || gz < 0 || gx >= top.length || gz >= top[0].length) continue;
            int found = AIR, fy = -64;
            search:
            for (int oi = 0; oi < n; oi++) {
                int i = idx[oi];
                if (pcat[i].length == 1 && (pcat[i][0] == AIR || datas[i] == null && pcat[i][0] == THIN)) continue;
                for (int ly = 15; ly >= 0; ly--) {
                    int c;
                    if (datas[i] == null) c = pcat[i][0];
                    else {
                        int bi = (ly * 16 + lz) * 16 + lx, per = 64 / bits[i];
                        int li = bi / per;
                        if (li >= datas[i].length) { c = AIR; }
                        else c = pcat[i][(int) ((datas[i][li] >>> ((bi % per) * bits[i])) & ((1L << bits[i]) - 1)) % pcat[i].length];
                    }
                    if (c == AIR || c == THIN) continue;
                    found = c;
                    fy = ys[i] * 16 + ly;
                    break search;
                }
            }
            cat[gx][gz] = (byte) (found == AIR ? NONE : found);
            top[gx][gz] = (short) fy;
        }
    }

    static int category(String name) {
        if (name == null) return AIR;
        String n = name.startsWith("minecraft:") ? name.substring(10) : name;
        if (n.equals("air") || n.equals("cave_air") || n.equals("void_air") || n.equals("barrier") || n.equals("light") || n.equals("structure_void")) return AIR;
        if (n.equals("water") || n.equals("seagrass") || n.equals("tall_seagrass") || n.equals("kelp") || n.equals("kelp_plant") || n.equals("bubble_column") || n.equals("lava") || n.equals("ice")) return WATER;
        if (n.endsWith("leaves") || n.equals("vine") || n.contains("bamboo") || n.equals("cactus") || n.equals("cobweb")) return LEAVES;
        if (n.equals("short_grass") || n.equals("tall_grass") || n.equals("grass") || n.contains("fern") || n.endsWith("_sapling") || n.endsWith("_tulip") || n.endsWith("torch")
                || n.equals("poppy") || n.equals("dandelion") || n.equals("blue_orchid") || n.equals("allium") || n.equals("azure_bluet") || n.equals("oxeye_daisy")
                || n.equals("cornflower") || n.equals("lily_of_the_valley") || n.equals("rose_bush") || n.equals("peony") || n.equals("lilac") || n.equals("sunflower")
                || n.equals("dead_bush") || n.equals("snow") || n.endsWith("_carpet") || n.endsWith("rail") || n.equals("lily_pad") || n.endsWith("_button")
                || n.endsWith("pressure_plate") || n.equals("sugar_cane") || n.endsWith("_mushroom") || n.equals("sweet_berry_bush") || n.equals("tripwire")
                || n.equals("string") || n.equals("lever") || n.equals("redstone_wire") || n.endsWith("_sign") || n.endsWith("_banner") || n.equals("ladder")) return THIN;
        return SOLID;
    }

    static Object read(DataInputStream in, int t) throws IOException {
        switch (t) {
            case 1: return in.readByte();
            case 2: return in.readShort();
            case 3: return in.readInt();
            case 4: return in.readLong();
            case 5: return in.readFloat();
            case 6: return in.readDouble();
            case 7: { byte[] b = new byte[in.readInt()]; in.readFully(b); return b; }
            case 8: return in.readUTF();
            case 9: {
                int et = in.readByte(), n = in.readInt();
                List<Object> l = new ArrayList<>(Math.max(0, n));
                for (int i = 0; i < n; i++) l.add(read(in, et));
                return l;
            }
            case 10: {
                Map<String, Object> m = new HashMap<>();
                for (int ct; (ct = in.readByte()) != 0; ) { String k = in.readUTF(); m.put(k, read(in, ct)); }
                return m;
            }
            case 11: { int[] v = new int[in.readInt()]; for (int i = 0; i < v.length; i++) v[i] = in.readInt(); return v; }
            case 12: { long[] v = new long[in.readInt()]; for (int i = 0; i < v.length; i++) v[i] = in.readLong(); return v; }
            default: throw new IOException("tag inconnu " + t);
        }
    }
}
