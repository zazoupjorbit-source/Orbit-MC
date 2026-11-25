// Icones HD des armures d'OrbitMMO (tools/gen-items.ps1) : 4 types x 4 pieces x 4 variantes, 64 x 64 px.
// Types : tissu, cuir, mailles, plaques. Pieces : head, chest, legs, feet. Variante = palier / 2 (Loot.PACK_TIER).
// Silhouette en polygones/ellipses rasterises a 4x puis reduits (bords lisses), volume par champ de distance,
// matiere propre au type (metal poli + rivets, anneaux de mailles, grain et coutures du cuir, trame et galon du tissu).
// C# 5 (compile par Add-Type de PowerShell 5.1) : pas d'interpolation de chaines ni de "=>".
using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
using System.IO;

public static class ArmorIcons
{
    const int N = 64;
    const int SS = 4;

    class Shape
    {
        public List<PointF[]> Polys = new List<PointF[]>();
        public List<RectangleF> Ellipses = new List<RectangleF>();
        public void P(params float[] xy)
        {
            PointF[] pts = new PointF[xy.Length / 2];
            for (int i = 0; i < pts.Length; i++) pts[i] = new PointF(xy[2 * i], xy[2 * i + 1]);
            Polys.Add(pts);
        }
        public void E(float x, float y, float w, float h) { Ellipses.Add(new RectangleF(x, y, w, h)); }
        public void R(float x, float y, float w, float h) { P(x, y, x + w, y, x + w, y + h, x, y + h); }
    }

    // Palettes : principal, clair, sombre, galon (accent), lueur.
    static readonly int[][][] PAL = new int[][][] {
        // tissu
        new int[][] {
            new int[] { 0xD8CBB0, 0xF2E9D4, 0x8E7F62, 0x4E6FB8, 0x9AB8FF },
            new int[] { 0x4E6FB8, 0x8DA9E6, 0x26386B, 0xC9D2E0, 0xB8D4FF },
            new int[] { 0x5B3A99, 0x9474D6, 0x2A1A4D, 0xE0B84E, 0xD6A8FF },
            new int[] { 0xE8E2F5, 0xFFFFFF, 0x8C82A8, 0xE0B84E, 0xB98CFF } },
        // cuir
        new int[][] {
            new int[] { 0xA8784A, 0xD1A271, 0x5C3B1E, 0x6B4A2B, 0xE8C48A },
            new int[] { 0x7A4E2C, 0xA8744A, 0x3E2410, 0xB8BEC6, 0xE0E6EE },
            new int[] { 0x3E4A2E, 0x66784C, 0x1C2414, 0x4FC9B8, 0x8AFFE8 },
            new int[] { 0x6E2E2E, 0xA04A44, 0x341212, 0xE0B84E, 0xFFD27A } },
        // mailles
        new int[][] {
            new int[] { 0x7E848C, 0xB4BAC2, 0x3C4048, 0x8A5A32, 0xD8DDE4 },
            new int[] { 0xA8B3BF, 0xDCE4EC, 0x4E5866, 0xC99A4A, 0xFFFFFF },
            new int[] { 0x4A4F5C, 0x7C8496, 0x1E2128, 0x4FA3FF, 0x9AD8FF },
            new int[] { 0xD9DEE6, 0xFFFFFF, 0x7A808C, 0xE0B84E, 0xFFF0B0 } },
        // plaques
        new int[][] {
            new int[] { 0xB87333, 0xE8A866, 0x5E3516, 0x7A4A22, 0xFFD2A0 },
            new int[] { 0xA9B4C2, 0xE6EDF5, 0x4C5562, 0xD4A94A, 0xFFFFFF },
            new int[] { 0x2E2A3A, 0x5A5470, 0x110F18, 0x5FD9E8, 0xA8F4FF },
            new int[] { 0xE6DCC0, 0xFFFBEA, 0x8A7A4E, 0x9B6BFF, 0xD6B8FF } }
    };

    static readonly string[] TYPES = { "tissu", "cuir", "mailles", "plaques" };
    static readonly string[] SLOTS = { "head", "chest", "legs", "feet" };

    public static int Run(string outDir)
    {
        int n = 0;
        for (int t = 0; t < 4; t++)
            for (int s = 0; s < 4; s++)
                for (int v = 0; v < 4; v++)
                {
                    string path = Path.Combine(outDir, TYPES[t] + "_" + SLOTS[s] + "_" + v + ".png");
                    Directory.CreateDirectory(Path.GetDirectoryName(path));
                    using (Bitmap b = Paint(t, s, v)) b.Save(path, ImageFormat.Png);
                    n++;
                }
        return n;
    }

    // ------------------------------------------------------------------ Silhouettes

    static void Build(int type, int slot, int v, Shape body, Shape hole, Shape trim)
    {
        if (slot == 0) Head(type, v, body, hole, trim);
        else if (slot == 1) Chest(type, v, body, hole, trim);
        else if (slot == 2) Legs(type, v, body, hole, trim);
        else Feet(type, v, body, hole, trim);
    }

    static void Head(int type, int v, Shape b, Shape h, Shape tr)
    {
        if (type == 0)
        {   // capuche pointue
            b.P(32, 3, 47, 16, 54, 34, 55, 52, 46, 60, 18, 60, 9, 52, 10, 34, 17, 16);
            h.E(20, 24, 24, 28);
            tr.P(18, 58, 46, 58, 46, 61, 18, 61);
            if (v >= 2) tr.P(30, 6, 34, 6, 33, 20, 31, 20);
        }
        else if (type == 1)
        {   // coiffe de cuir avec rabats
            b.E(12, 6, 40, 38);
            b.P(12, 26, 22, 26, 22, 54, 14, 52);
            b.P(42, 26, 52, 26, 50, 52, 42, 54);
            b.R(12, 22, 40, 10);
            h.E(22, 26, 20, 26);
            tr.R(12, 20, 40, 4);
        }
        else if (type == 2)
        {   // camail : calotte + mailles sur les epaules
            b.E(14, 4, 36, 34);
            b.P(10, 22, 54, 22, 58, 58, 6, 58);
            h.E(20, 18, 24, 26);
            tr.R(14, 14, 36, 4);
        }
        else
        {   // heaume : dome, joues, visiere en T
            b.E(11, 4, 42, 42);
            b.R(11, 24, 42, 28);
            b.P(11, 50, 53, 50, 48, 58, 16, 58);
            h.R(19, 28, 26, 5);
            h.R(29, 28, 6, 20);
            tr.R(30, 4, 4, 22);
            if (v >= 2) { tr.P(26, 3, 38, 3, 35, -2, 29, -2); }
            if (v == 3) { tr.P(30, 4, 34, 4, 40, -4, 24, -4); }
        }
    }

    static void Chest(int type, int v, Shape b, Shape h, Shape tr)
    {
        if (type == 0)
        {   // robe longue evasee, manches larges
            b.P(22, 6, 42, 6, 47, 16, 55, 61, 9, 61, 17, 16);
            b.P(17, 12, 6, 34, 4, 44, 14, 44, 20, 26);
            b.P(47, 12, 58, 34, 60, 44, 50, 44, 44, 26);
            h.E(25, 2, 14, 10);
            tr.R(17, 30, 30, 5);
            tr.P(30, 12, 34, 12, 34, 61, 30, 61);
            tr.P(4, 42, 14, 42, 14, 45, 4, 45);
            tr.P(50, 42, 60, 42, 60, 45, 50, 45);
        }
        else if (type == 1)
        {   // veste de cuir, col, sangles
            b.P(20, 8, 44, 8, 50, 16, 50, 56, 14, 56, 14, 16);
            b.P(14, 14, 6, 22, 6, 42, 14, 42);
            b.P(50, 14, 58, 22, 58, 42, 50, 42);
            h.P(26, 4, 38, 4, 32, 18);
            tr.P(14, 20, 18, 20, 48, 50, 44, 50);
            tr.R(14, 40, 36, 5);
        }
        else if (type == 2)
        {   // haubert a manches
            b.P(20, 6, 44, 6, 50, 14, 52, 58, 12, 58, 14, 14);
            b.P(14, 12, 4, 20, 2, 46, 12, 46);
            b.P(50, 12, 60, 20, 62, 46, 52, 46);
            h.E(25, 1, 14, 10);
            tr.R(13, 38, 38, 5);
            tr.R(2, 44, 10, 3);
            tr.R(52, 44, 10, 3);
        }
        else
        {   // cuirasse + epaulieres
            b.P(18, 12, 46, 12, 49, 30, 46, 52, 38, 58, 26, 58, 18, 52, 15, 30);
            b.E(3, 7, 22, 18);
            b.E(39, 7, 22, 18);
            h.E(24, 5, 16, 12);
            tr.R(31, 18, 2, 36);
            tr.E(3, 7, 22, 6);
            tr.E(39, 7, 22, 6);
            if (v >= 2) { tr.P(26, 26, 38, 26, 32, 36); }
        }
    }

    static void Legs(int type, int v, Shape b, Shape h, Shape tr)
    {
        if (type == 0)
        {   // jupe longue a plis
            b.R(14, 4, 36, 8);
            b.P(14, 10, 50, 10, 57, 58, 7, 58);
            tr.R(14, 4, 36, 4);
            tr.P(7, 55, 57, 55, 57, 58, 7, 58);
            tr.P(31, 10, 33, 10, 33, 58, 31, 58);
            return;
        }
        b.R(13, 4, 38, 9);
        b.P(13, 11, 31, 11, 30, 59, 16, 59);
        b.P(33, 11, 51, 11, 48, 59, 34, 59);
        tr.R(13, 4, 38, 4);
        if (type == 1) { tr.R(14, 28, 15, 3); tr.R(35, 28, 15, 3); }
        if (type == 2) { tr.R(16, 54, 14, 3); tr.R(34, 54, 14, 3); }
        if (type == 3)
        {
            b.E(14, 26, 17, 13); b.E(33, 26, 17, 13);
            tr.E(17, 29, 11, 7); tr.E(36, 29, 11, 7);
            tr.R(30, 4, 4, 9);
        }
    }

    static void Feet(int type, int v, Shape b, Shape h, Shape tr)
    {
        if (type == 0)
        {   // chaussons bas
            b.P(6, 26, 24, 26, 26, 44, 32, 50, 32, 58, 4, 58, 3, 44);
            b.P(34, 20, 52, 20, 54, 38, 60, 44, 60, 52, 32, 52, 31, 38);
            tr.R(6, 26, 18, 4);
            tr.R(34, 20, 18, 4);
            return;
        }
        float top = type == 1 ? 12 : 8;
        b.P(6, top + 6, 24, top + 6, 24, 44, 32, 50, 32, 58, 4, 58, 4, 48);
        b.P(34, top, 52, top, 52, 38, 60, 44, 60, 52, 32, 52, 32, 42);
        tr.R(6, top + 6, 18, 4);
        tr.R(34, top, 18, 4);
        if (type == 3) { tr.R(4, 54, 28, 4); tr.R(32, 48, 28, 4); tr.E(10, 34, 10, 8); tr.E(38, 28, 10, 8); }
        if (type == 1) { tr.R(6, 32, 18, 2); tr.R(34, 26, 18, 2); }
    }

    // ------------------------------------------------------------------ Rendu

    static double[,] Raster(Shape s)
    {
        int big = N * SS;
        double[,] cov = new double[N, N];
        using (Bitmap bm = new Bitmap(big, big, PixelFormat.Format32bppArgb))
        {
            using (Graphics g = Graphics.FromImage(bm))
            {
                g.Clear(Color.Transparent);
                g.SmoothingMode = SmoothingMode.None;
                g.ScaleTransform(SS, SS);
                foreach (PointF[] p in s.Polys) g.FillPolygon(Brushes.White, p);
                foreach (RectangleF e in s.Ellipses) g.FillEllipse(Brushes.White, e);
            }
            BitmapData bd = bm.LockBits(new Rectangle(0, 0, big, big), ImageLockMode.ReadOnly, PixelFormat.Format32bppArgb);
            byte[] px = new byte[bd.Stride * big];
            System.Runtime.InteropServices.Marshal.Copy(bd.Scan0, px, 0, px.Length);
            bm.UnlockBits(bd);
            for (int y = 0; y < big; y++)
                for (int x = 0; x < big; x++)
                    if (px[y * bd.Stride + x * 4 + 3] > 127) cov[x / SS, y / SS] += 1.0 / (SS * SS);
        }
        return cov;
    }

    static double[,] Distance(double[,] cov)
    {
        double[,] d = new double[N, N];
        const int R = 7;
        for (int y = 0; y < N; y++)
            for (int x = 0; x < N; x++)
            {
                if (cov[x, y] < 0.5) { d[x, y] = 0; continue; }
                double best = R;
                for (int dy = -R; dy <= R; dy++)
                    for (int dx = -R; dx <= R; dx++)
                    {
                        int nx = x + dx, ny = y + dy;
                        bool outside = nx < 0 || ny < 0 || nx >= N || ny >= N || cov[nx, ny] < 0.5;
                        if (!outside) continue;
                        double dd = Math.Sqrt(dx * dx + dy * dy);
                        if (dd < best) best = dd;
                    }
                d[x, y] = best;
            }
        return d;
    }

    static int seedG;

    static double Hash(int x, int y)
    {
        unchecked
        {
            int h = x * 374761393 + y * 668265263 + seedG * 1274126177;
            h = (h ^ (h >> 13)) * 1274126177;
            h ^= h >> 16;
            return (h & 0x7fffffff) / (double)0x7fffffff;
        }
    }

    static double Noise(double x, double y)
    {
        int xi = (int)Math.Floor(x), yi = (int)Math.Floor(y);
        double xf = x - xi, yf = y - yi;
        xf = xf * xf * (3 - 2 * xf); yf = yf * yf * (3 - 2 * yf);
        double a = Hash(xi, yi), b = Hash(xi + 1, yi), c = Hash(xi, yi + 1), d = Hash(xi + 1, yi + 1);
        return a + (b - a) * xf + (c - a) * yf + (a - b - c + d) * xf * yf;
    }

    static double[] Rgb(int c) { return new double[] { (c >> 16) & 255, (c >> 8) & 255, c & 255 }; }

    static double[] Mix(double[] a, double[] b, double t)
    {
        return new double[] { a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t };
    }

    static double[] Mul(double[] a, double k) { return new double[] { a[0] * k, a[1] * k, a[2] * k }; }

    static Bitmap Paint(int type, int slot, int v)
    {
        Shape body = new Shape(), hole = new Shape(), trim = new Shape();
        Build(type, slot, v, body, hole, trim);
        double[,] cov = Raster(body);
        double[,] hol = Raster(hole);
        double[,] trm = Raster(trim);
        double[,] dist = Distance(cov);
        seedG = type * 97 + slot * 13 + v * 7 + 11;

        int[] p = PAL[type][v];
        double[] main = Rgb(p[0]), light = Rgb(p[1]), dark = Rgb(p[2]), acc = Rgb(p[3]), glow = Rgb(p[4]);
        double[] outline = new double[] { 18, 20, 26 };
        Bitmap bmp = new Bitmap(N, N, PixelFormat.Format32bppArgb);

        for (int y = 0; y < N; y++)
            for (int x = 0; x < N; x++)
            {
                double c = cov[x, y];
                if (c <= 0.02)
                {
                    // contour sombre autour de la silhouette
                    double near = 0;
                    for (int dy = -1; dy <= 1; dy++)
                        for (int dx = -1; dx <= 1; dx++)
                        {
                            int nx = x + dx, ny = y + dy;
                            if (nx >= 0 && ny >= 0 && nx < N && ny < N) near = Math.Max(near, cov[nx, ny]);
                        }
                    if (near > 0.3) bmp.SetPixel(x, y, Color.FromArgb((int)(230 * Math.Min(1, near)), 18, 20, 26));
                    continue;
                }
                double dd = dist[x, y];
                // normale approchee : gradient du champ de distance (lumiere en haut a gauche)
                double gx = Get(dist, x + 1, y) - Get(dist, x - 1, y);
                double gy = Get(dist, x, y + 1) - Get(dist, x, y - 1);
                double lit = (gx * 0.7 + gy * 0.7) * 0.5;      // > 0 : face tournee vers le haut-gauche
                double bevel = Math.Min(1, dd / 5.0);
                double shade = 0.78 + 0.18 * bevel + 0.22 * Math.Max(-1, Math.Min(1, lit)) - 0.10 * (y / (double)N);
                double[] col = main;
                bool isTrim = trm[x, y] > 0.5;

                if (type == 3)
                {   // metal poli : reflet diagonal, plaques, rivets
                    double band = Math.Sin((x - y) * 0.16 + 1.3);
                    col = Mix(main, light, Math.Max(0, band) * 0.55);
                    col = Mix(col, dark, Math.Max(0, -band) * 0.35);
                    if (slot != 0 && ((y + 2) % 13 == 0) && dd > 2) col = Mix(col, dark, 0.55);
                    if (dd >= 3.4 && dd < 4.6 && ((x + y) % 9 == 0)) col = Mix(light, glow, 0.5);
                    col = Mix(col, light, (Noise(x * 0.9, y * 0.9) - 0.5) * 0.12 + 0.06);
                }
                else if (type == 2)
                {   // anneaux de mailles
                    int row = y / 3;
                    double ox = (x + (row % 2) * 1.5) % 3.0 - 1.5;
                    double oy = (y % 3) - 1.0;
                    double r = Math.Sqrt(ox * ox + oy * oy);
                    double ring = r > 0.6 && r < 1.5 ? 1 : 0;
                    col = ring > 0 ? Mix(main, light, 0.35 + 0.35 * (oy < 0 ? 1 : 0)) : Mix(main, dark, 0.65);
                }
                else if (type == 1)
                {   // grain du cuir + coutures
                    double grain = Noise(x * 0.45, y * 0.45) * 0.6 + Noise(x * 1.7, y * 1.7) * 0.4;
                    col = Mix(Mix(dark, main, 0.55 + grain * 0.5), light, Math.Max(0, grain - 0.7) * 0.6);
                    if (dd >= 2.6 && dd < 3.4 && ((x + y) / 2) % 2 == 0) col = Mix(light, new double[] { 240, 225, 190 }, 0.6);
                }
                else
                {   // trame du tissu + plis
                    double weave = ((x + y) % 2 == 0) ? 1.05 : 0.95;
                    double fold = Math.Sin(x * 0.55 + Noise(x * 0.2, y * 0.2) * 3) * 0.08;
                    col = Mul(Mix(main, light, 0.2 + fold), weave);
                }

                if (isTrim)
                {
                    double t2 = Math.Min(1, trm[x, y]);
                    double[] tc = Mix(acc, light, type == 3 ? 0.15 : 0.1);
                    if (v >= 2 && ((x * 7 + y * 3) % 17 == 0)) tc = glow;
                    col = Mix(col, tc, 0.9 * t2);
                }
                col = Mul(col, shade);
                if (dd < 1.2) col = Mix(col, outline, 0.35);
                if (hol[x, y] > 0.3)
                {
                    double[] inside = Mul(dark, 0.35);
                    if (v == 3 && type != 1) inside = Mix(inside, glow, 0.25);
                    col = Mix(col, inside, Math.Min(1, hol[x, y]));
                }
                int a = (int)(255 * Math.Min(1, c));
                bmp.SetPixel(x, y, Color.FromArgb(a, Clamp(col[0]), Clamp(col[1]), Clamp(col[2])));
            }

        // Eclats sur les variantes hautes (astralite / orbitium).
        if (v == 3)
        {
            for (int i = 0; i < 5; i++)
            {
                int sx = 8 + (int)(Hash(i, 3) * 48), sy = 8 + (int)(Hash(3, i) * 48);
                if (cov[sx, sy] < 0.9) continue;
                Spark(bmp, sx, sy, glow);
            }
        }
        return bmp;
    }

    static void Spark(Bitmap b, int x, int y, double[] g)
    {
        Color c = Color.FromArgb(255, Clamp(g[0] + 40), Clamp(g[1] + 40), Clamp(g[2] + 40));
        b.SetPixel(x, y, Color.White);
        if (x > 0) b.SetPixel(x - 1, y, c);
        if (x < N - 1) b.SetPixel(x + 1, y, c);
        if (y > 0) b.SetPixel(x, y - 1, c);
        if (y < N - 1) b.SetPixel(x, y + 1, c);
    }

    static double Get(double[,] a, int x, int y)
    {
        if (x < 0 || y < 0 || x >= N || y >= N) return 0;
        return a[x, y];
    }

    static int Clamp(double v) { return v < 0 ? 0 : v > 255 ? 255 : (int)v; }
}
