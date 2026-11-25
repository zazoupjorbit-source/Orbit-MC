// Tuiles HD des montures (tools/gen-mounts.ps1) : une tuile de T x T px par couleur de la palette.
// Motifs : fur, feather, scale, leather, membrane, horn, metal, eye, plain.
// Chaque face d'un cube lit une fenetre centree dans sa tuile : les bords de la tuile sont assombris (volume).
// C# 5 (compile par Add-Type de PowerShell 5.1) : pas d'interpolation de chaines ni de "=>".
using System;
using System.Drawing;
using System.Drawing.Imaging;

public static class MountTiles
{
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

    static double Smooth(double t) { return t * t * (3 - 2 * t); }

    static double Noise(double x, double y)
    {
        int xi = (int)Math.Floor(x), yi = (int)Math.Floor(y);
        double xf = Smooth(x - xi), yf = Smooth(y - yi);
        double a = Hash(xi, yi), b = Hash(xi + 1, yi), c = Hash(xi, yi + 1), d = Hash(xi + 1, yi + 1);
        return a + (b - a) * xf + (c - a) * yf + (a - b - c + d) * xf * yf;
    }

    static double Fbm(double x, double y)
    {
        double s = 0, amp = 0.5, f = 1;
        for (int i = 0; i < 4; i++) { s += amp * Noise(x * f, y * f); f *= 2; amp *= 0.5; }
        return s;
    }

    static double Clamp(double v, double a, double b) { return v < a ? a : (v > b ? b : v); }

    /** Couleur : base * lumiere, puis melange vers un ton (clair chaud ou sombre froid). */
    static Color Shade(int r, int g, int b, double light, double warm)
    {
        double rr = r * light, gg = g * light, bb = b * light;
        if (warm > 0) { rr += (255 - rr) * warm * 0.5; gg += (240 - gg) * warm * 0.45; bb += (200 - bb) * warm * 0.35; }
        else if (warm < 0) { double k = -warm; rr *= 1 - k * 0.35; gg *= 1 - k * 0.3; bb *= 1 - k * 0.15; }
        return Color.FromArgb(255, (int)Clamp(rr, 0, 255), (int)Clamp(gg, 0, 255), (int)Clamp(bb, 0, 255));
    }

    public static void Paint(Bitmap bmp, int ox, int oy, int T, int r, int g, int b, string pattern, int seed)
    {
        seedG = seed;
        double s = T / 16.0;
        for (int y = 0; y < T; y++)
        {
            for (int x = 0; x < T; x++)
            {
                double u = x / s, v = y / s; // 0..16, comme une tuile d'origine
                double light = 1, warm = 0;
                switch (pattern)
                {
                    case "fur": Fur(u, v, ref light, ref warm); break;
                    case "feather": Feather(u, v, ref light, ref warm); break;
                    case "scale": Scale(u, v, ref light, ref warm); break;
                    case "leather": Leather(u, v, ref light, ref warm); break;
                    case "membrane": Membrane(u, v, ref light, ref warm); break;
                    case "horn": Horn(u, v, ref light, ref warm); break;
                    case "metal": Metal(u, v, ref light, ref warm); break;
                    case "eye":
                        bmp.SetPixel(ox + x, oy + y, Eye(u, v, r, g, b));
                        continue;
                    default: light = 0.95 + 0.1 * Fbm(u * 0.5, v * 0.5); break;
                }
                // Volume : bords de la tuile plus sombres, haut un peu plus clair.
                double ex = Math.Min(u, 16 - u), ey = Math.Min(v, 16 - v);
                double edge = Clamp(Math.Min(ex, ey) / 2.2, 0, 1);
                light *= 0.8 + 0.2 * Smooth(edge);
                light *= 1.05 - 0.1 * (v / 16.0);
                bmp.SetPixel(ox + x, oy + y, Shade(r, g, b, light, warm));
            }
        }
    }

    static void Fur(double u, double v, ref double light, ref double warm)
    {
        // Meches : bruit etire dans le sens du poil (vers le bas), fines lignes, pointes claires.
        // Touffes en rangees decalees : chaque touffe est claire en haut et s'assombrit vers sa pointe.
        double th = 3.2;
        int row = (int)Math.Floor(v / th);
        double off = Hash(row, 91) * 3;
        double lv = (v - row * th) / th;
        double tuft = Noise((u + off) * 0.9, row * 1.7);
        double hair = Noise((u + off) * 6.5, v * 1.1);
        double fine = Noise((u + off) * 13, v * 2.2);
        light = 0.9 + 0.22 * tuft + 0.14 * (hair - 0.5) + 0.08 * (fine - 0.5);
        light *= 1.08 - 0.28 * lv * lv;
        if (lv < 0.12) light *= 0.8 + lv * 1.6; // ombre sous la rangee du dessus
        if (hair > 0.72 && lv < 0.7) warm = (hair - 0.72) * 1.1;
        light *= 0.93 + 0.14 * Fbm(u * 0.35, v * 0.35);
    }

    static void Feather(double u, double v, ref double light, ref double warm)
    {
        // Plumes longues (4 x 9) en rangees decalees, pointe en bas : rachis clair, barbes obliques, bord sombre.
        double cw = 4, ch = 9;
        int row = (int)Math.Floor(v / ch);
        double lu = u + (row % 2) * cw / 2;
        double cx = (Math.Floor(lu / cw) + 0.5) * cw;
        double lx = (lu - cx) / (cw / 2);          // -1..1
        double ly = (v - row * ch) / ch;            // 0..1
        double half = 1 - Math.Pow(Math.Max(0, ly - 0.55) / 0.45, 1.6); // la plume s'affine vers sa pointe
        double ax = Math.Abs(lx) / Math.Max(0.05, half);
        double barb = Math.Sin((Math.Abs(lx) * 3.2 + ly * 7) * 3.1);
        light = 1.06 - 0.18 * ly + 0.07 * barb - 0.12 * ax;
        if (Math.Abs(lx) < 0.08) { light = 1.22 - 0.1 * ly; warm = 0.3; }
        if (ax > 1) light = 0.5;                 // entre deux pointes : la rangee du dessous, dans l'ombre
        else if (ax > 0.86) light *= 0.75;
        if (ly < 0.12) light *= 0.7 + ly * 2.5;   // ombre de la rangee du dessus
        light *= 0.94 + 0.12 * Noise(u * 1.2, v * 0.6);
    }

    static void Scale(double u, double v, ref double light, ref double warm)
    {
        // Ecailles en demi-lunes decalees (5 x 4) : reflet en haut, liseré sombre.
        double cw = 5, ch = 4;
        int row = (int)Math.Floor(v / ch);
        double lu = u + (row % 2) * cw / 2;
        double cx = (Math.Floor(lu / cw) + 0.5) * cw;
        double lx = (lu - cx) / (cw / 2);
        double ly = (v - row * ch) / ch;
        double d = Math.Sqrt(lx * lx + Math.Pow(ly - 0.1, 2) * 0.8);
        light = 1.12 - 0.35 * ly;
        if (d < 0.45 && ly < 0.5) { light += 0.22 * (1 - d / 0.45); warm = 0.18 * (1 - d / 0.45); }
        if (d > 0.92) light *= 0.55;
        else if (d > 0.8) light *= 0.8;
        light *= 0.95 + 0.1 * Noise(u * 2, v * 2);
    }

    static void Leather(double u, double v, ref double light, ref double warm)
    {
        double grain = Fbm(u * 2.2, v * 2.2);
        double wrinkle = Math.Abs(Math.Sin(u * 0.9 + Fbm(u * 0.3, v * 0.3) * 6 + v * 0.35));
        light = 0.86 + 0.22 * grain + (wrinkle < 0.08 ? -0.12 : 0);
        // Surpiqures a 1,6 px du bord : points clairs.
        double ex = Math.Min(u, 16 - u), ey = Math.Min(v, 16 - v);
        bool line = (Math.Abs(ex - 1.6) < 0.28 && ey > 1.2) || (Math.Abs(ey - 1.6) < 0.28 && ex > 1.2);
        double along = Math.Abs(ex - 1.6) < 0.28 ? v : u;
        if (line && (along % 1.6) < 0.9) { light = 1.35; warm = 0.35; }
        if (Math.Min(ex, ey) < 0.6) light *= 0.7;
    }

    static void Membrane(double u, double v, ref double light, ref double warm)
    {
        light = 0.85 + 0.25 * (1 - v / 16.0) + 0.1 * Fbm(u * 0.6, v * 0.6);
        // Nervures en eventail depuis le coin haut gauche, plus quelques branches.
        double ang = Math.Atan2(v + 1, u + 1);
        double vein = Math.Abs(Math.Sin(ang * 9 + Fbm(u * 0.4, v * 0.4) * 2));
        double dist = Math.Sqrt(u * u + v * v);
        if (vein < 0.06 + 0.02 * (dist / 22)) { light = 0.6; }
        else if (vein < 0.14) { light *= 1.12; warm = 0.12; }
        double branch = Math.Abs(Math.Sin(u * 1.1 - v * 0.7 + Fbm(u, v) * 4));
        if (branch < 0.035) light *= 0.75;
    }

    static void Horn(double u, double v, ref double light, ref double warm)
    {
        double ring = Math.Sin(v * 1.6 + Fbm(u * 0.5, v * 0.3) * 3);
        light = 0.95 + 0.1 * ring + 0.12 * Fbm(u * 0.6, v * 3);
        // Reflet vertical brillant.
        double gloss = Math.Exp(-Math.Pow((u - 5.5) / 1.4, 2));
        light += 0.28 * gloss;
        warm = 0.25 * gloss + (v < 3 ? 0.15 : 0);
        if (ring < -0.85) light *= 0.82;
    }

    static void Metal(double u, double v, ref double light, ref double warm)
    {
        double brushed = Fbm(u * 0.15, v * 3.5);
        double band = Math.Exp(-Math.Pow((v - 5) / 2.6, 2));
        light = 0.78 + 0.18 * brushed + 0.45 * band;
        warm = 0.3 * band;
        double ex = Math.Min(u, 16 - u), ey = Math.Min(v, 16 - v);
        // Filet grave et volutes.
        if (Math.Abs(Math.Min(ex, ey) - 2.2) < 0.22) light *= 0.62;
        else if (Math.Abs(Math.Min(ex, ey) - 2.6) < 0.2) { light *= 1.15; warm += 0.2; }
        // Losange grave au centre.
        double dm = Math.Abs(u - 8) + Math.Abs(v - 8);
        if (Math.Abs(dm - 3.2) < 0.25) light *= 0.65;
        else if (Math.Abs(dm - 3.6) < 0.2) { light *= 1.15; warm += 0.2; }
        // Rivets aux coins.
        double[] cs = { 1.1, 14.9 };
        foreach (double cxr in cs) foreach (double cyr in cs)
        {
            double d = Math.Sqrt((u - cxr) * (u - cxr) + (v - cyr) * (v - cyr));
            if (d < 0.75) { light = 1.25 - d * 0.6; warm = 0.5; if (u - cxr + v - cyr < -0.3) light = 1.6; }
        }
    }

    static Color Eye(double u, double v, int r, int g, int b)
    {
        double dx = (u - 8) / 7.2, dy = (v - 8) / 7.2;
        double d = Math.Sqrt(dx * dx + dy * dy);
        if (d > 1) return Color.FromArgb(255, 18, 16, 20);
        // Iris : clair au centre, cerne sombre ; stries radiales.
        double ang = Math.Atan2(dy, dx);
        double fib = 0.92 + 0.12 * Math.Sin(ang * 22 + d * 4);
        double light = (1.25 - 0.55 * d) * fib;
        double warm = d < 0.5 ? 0.3 * (0.5 - d) : 0;
        if (d > 0.86) light *= 0.45;
        Color c = Shade(r, g, b, light, warm);
        // Pupille fendue (ou ronde si l'oeil est sombre).
        bool dark = r + g + b < 150;
        bool pupil = dark ? d < 0.42 : (Math.Abs(dx) < 0.16 * (1 - dy * dy) && Math.Abs(dy) < 0.8);
        if (pupil) c = dark ? Color.FromArgb(255, 6, 6, 8) : Color.FromArgb(255, 10, 8, 12);
        // Reflets.
        double g1 = Math.Sqrt(Math.Pow(dx + 0.35, 2) + Math.Pow(dy + 0.35, 2));
        double g2 = Math.Sqrt(Math.Pow(dx - 0.3, 2) + Math.Pow(dy - 0.32, 2));
        if (g1 < 0.17) c = Color.FromArgb(255, 255, 255, 255);
        else if (g2 < 0.08) c = Color.FromArgb(255, 230, 235, 240);
        return c;
    }
}
