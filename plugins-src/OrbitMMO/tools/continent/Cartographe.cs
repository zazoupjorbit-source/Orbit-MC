// Moteur des cartes 2D du continent d'Orbit MMO. Compile a la volee par tools\gen-continent.ps1
// (PowerShell 5.1 => C# 5 : pas d'interpolation de chaines, pas de membres en =>, pas de « out var »).
// Entree : le dessin tools\continent\continent.txt. Sortie : les PNG de docs\continent + bilan.txt.
using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
using System.Drawing.Text;
using System.Globalization;
using System.IO;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading.Tasks;

namespace OrbitMmo
{
    public class Region
    {
        public string Id, Name, Faction, Biome;
        public int LvMin, LvMax, Index, Style;
        public List<double[]> Seeds = new List<double[]>();
        public double Cx, Cz, AvgH;
        public bool Fixed;
        public long Pixels;
    }

    /** Fleuve ou coulee de lave. S : x, z, largeur, niveau. */
    public class Stream
    {
        public string Name;
        public bool Lava;
        public double W0, W1, Length;
        public List<double[]> Pts = new List<double[]>();
        public List<double[]> S = new List<double[]>();
    }

    public class Lake { public string Name; public double X, Z, Rx, Rz, Level; }

    public class Road
    {
        public string Kind, Name;
        public double Length;
        public List<double[]> Pts = new List<double[]>();
        public List<double[]> S = new List<double[]>();
        public List<double[]> Bridges = new List<double[]>();
    }

    public class Place { public string Kind, Name, Faction, Side, Region = ""; public double X, Z, Safe; }

    public class Blob { public bool Add; public double X, Z, Rx, Rz; }

    /** Bruit de gradient 2D (Perlin) et ses derives. */
    public static class Nz
    {
        static int[] P = new int[512];

        public static void Init(int seed)
        {
            Random rnd = new Random(seed);
            int[] p = new int[256];
            for (int i = 0; i < 256; i++) p[i] = i;
            for (int i = 255; i > 0; i--) { int j = rnd.Next(i + 1); int t = p[i]; p[i] = p[j]; p[j] = t; }
            for (int i = 0; i < 512; i++) P[i] = p[i & 255];
        }

        static double G(int h, double x, double y)
        {
            switch (h & 7)
            {
                case 0: return x + y;
                case 1: return x - y;
                case 2: return -x + y;
                case 3: return -x - y;
                case 4: return 1.4142 * x;
                case 5: return -1.4142 * x;
                case 6: return 1.4142 * y;
                default: return -1.4142 * y;
            }
        }

        public static double N2(double x, double y)
        {
            double fx = Math.Floor(x), fy = Math.Floor(y);
            int X = ((int)fx) & 255, Y = ((int)fy) & 255;
            x -= fx; y -= fy;
            double u = x * x * x * (x * (x * 6 - 15) + 10), v = y * y * y * (y * (y * 6 - 15) + 10);
            int a = P[X] + Y, b = P[X + 1] + Y;
            double n00 = G(P[a], x, y), n10 = G(P[b], x - 1, y), n01 = G(P[a + 1], x, y - 1), n11 = G(P[b + 1], x - 1, y - 1);
            double nx0 = n00 + u * (n10 - n00), nx1 = n01 + u * (n11 - n01);
            return nx0 + v * (nx1 - nx0);
        }

        /** Bruit fractal, valeurs courantes entre -0,7 et 0,7. */
        public static double Fbm(double x, double y, int oct)
        {
            double s = 0, a = 1, n = 0;
            for (int i = 0; i < oct; i++) { s += a * N2(x, y); n += a; a *= 0.5; x = x * 2 + 17.3; y = y * 2 + 9.1; }
            return s / n * 1.5;
        }

        /** Bruit en cretes, entre 0 et 1. */
        public static double Ridged(double x, double y, int oct)
        {
            double s = 0, a = 1, n = 0;
            for (int i = 0; i < oct; i++)
            {
                double r = 1 - Math.Abs(N2(x, y)); r *= r;
                s += a * r; n += a; a *= 0.5; x = x * 2 + 31.7; y = y * 2 + 13.9;
            }
            return s / n;
        }
    }

    public class Carto
    {
        public const double SEA = 63.0;
        const int S_PLAINE = 0, S_LANDE = 1, S_FORET = 2, S_SOMBRE = 3, S_COLLINES = 4, S_MONT = 5, S_DESERT = 6,
            S_MARAIS = 7, S_CENDRES = 8, S_VOLCAN = 9;
        static readonly string[] StyleKey = { "plaine", "lande", "foret", "foret-sombre", "collines", "montagne", "desert", "marais", "cendres", "volcan" };
        static readonly string[] StyleName = { "Plaine", "Lande", "Forêt claire", "Forêt sombre", "Collines", "Montagne", "Désert", "Marais", "Cendres", "Volcan" };

        const int B_OCEAN = 0, B_EAU = 1, B_PLAGE = 2, B_PLAINE = 3, B_LANDE = 4, B_FORET = 5, B_SOMBRE = 6, B_COLLINES = 7,
            B_PINS = 8, B_ALPAGE = 9, B_ROCHE = 10, B_NEIGE = 11, B_SABLE = 12, B_MESA = 13, B_OASIS = 14, B_MARAIS = 15,
            B_CENDRES = 16, B_VOLC = 17, B_LAVE = 18, B_PLAGE_NOIRE = 19;
        static readonly string[] BioName = { "Océan", "Eau douce", "Plage", "Plaine", "Lande", "Forêt claire", "Forêt sombre",
            "Collines", "Forêt de pins", "Alpage", "Roche", "Neige", "Sable", "Mesa et canyons", "Oasis", "Marais", "Cendres",
            "Roche volcanique", "Lave", "Plage noire" };
        static readonly int[] BioCol = { 0x3f77a8, 0x4f93c6, 0xe9dba4, 0x93c35e, 0x8c9b72, 0x4f9a47, 0x2c5c3a,
            0xa9b96a, 0x3f7a56, 0xa3b481, 0x8d8a85, 0xf3f5f8, 0xe4ca7c, 0xc87a44, 0x62b25c, 0x5f7c4c, 0x55504f,
            0x3b3437, 0xff7a1a, 0x4a4648 };

        // 8 tranches de niveau = les 8 paliers d'equipement.
        static readonly int[] LvCol = { 0x7fcf6e, 0xb9db5c, 0xeadf58, 0xf4bd4e, 0xef9142, 0xe35f40, 0xbd3f8c, 0x6f3399 };
        static readonly string[] LvTier = { "recrue", "cuivre", "acier", "électrum", "cristal", "obsidienne", "astralite", "orbitium" };
        static readonly int[] RegCol = { 0xc9e39a, 0x8fcf8a, 0xd9d38a, 0xb9b6cf, 0x7fa893, 0x9fc3b0, 0xf0dfa6, 0xf0c486,
            0xcfd6e0, 0xb7a6a2, 0xe79a7a, 0xd8b0d0 };
        const int C_LUM = 0xe8b93a, C_OMB = 0x7a4fc4, C_NEU = 0xffffff, C_INK = 0x2a1e12, C_PAPER = 0xf3ead3;

        public double Size = 8000, Half, Cell;
        public int N, Seed = 2026;
        public string Title = "Le continent";
        double[] K = new double[] { 0.8 };
        List<Blob> blobs = new List<Blob>();
        List<Region> regions = new List<Region>();
        List<Stream> streams = new List<Stream>();
        List<Lake> lakes = new List<Lake>();
        List<Road> roads = new List<Road>();
        List<Place> places = new List<Place>();
        // Reperes : lieux ajoutes en jeu apres le dessin (hameaux, crateres, camps, ruines, lieux-dits).
        // Ils ne servent qu'aux cartes : jamais ecrits dans continent.dat, sans effet sur le terrain.
        List<Place> marks = new List<Place>();
        string marksDate = "";
        bool hasVolcano; double vx, vz, vR = 620, vC = 125, vTop = 236;

        public float[] H, Land, Wat, RivD, Sh, LavL;
        public byte[] Reg, Bio, Lav, LakeM, Green;
        float[] tD, tL, tW;
        public StringBuilder Log = new StringBuilder();
        string fontName = "Georgia";
        float U = 1; int LEG, W; float ly;
        double topH; int topP;

        // ------------------------------------------------------------------ Lecture du dessin
        static double D(string s) { return double.Parse(s.Trim(), CultureInfo.InvariantCulture); }
        static double[] P2(string s) { string[] a = s.Trim().Split(','); return new double[] { D(a[0]), D(a[1]) }; }
        static string[] Words(string s) { return s.Split(new char[] { ' ', '\t' }, StringSplitOptions.RemoveEmptyEntries); }
        static List<double[]> Pts(string s) { List<double[]> l = new List<double[]>(); foreach (string t in Words(s)) l.Add(P2(t)); return l; }

        void Parse(string design)
        {
            foreach (string raw in design.Split('\n'))
            {
                string line = raw.Trim();
                if (line.Length == 0 || line[0] == '#') continue;
                string[] f = line.Split('|');
                for (int i = 0; i < f.Length; i++) f[i] = f[i].Trim();
                switch (f[0])
                {
                    case "titre": Title = f[1]; break;
                    case "taille": Size = D(f[1]); break;
                    case "graine": Seed = (int)D(f[1]); break;
                    case "cote":
                        {
                            string[] t = Words(f[1]); K = new double[t.Length];
                            for (int i = 0; i < t.Length; i++) K[i] = D(t[i]);
                        }
                        break;
                    case "baie":
                    case "ile":
                        {
                            double[] c = P2(f[1]), r = P2(f[2]);
                            Blob b = new Blob(); b.Add = f[0] == "ile"; b.X = c[0]; b.Z = c[1]; b.Rx = r[0]; b.Rz = r[1];
                            blobs.Add(b);
                        }
                        break;
                    case "volcan":
                        {
                            double[] c = P2(f[1]); hasVolcano = true; vx = c[0]; vz = c[1];
                            if (f.Length > 2) vR = D(f[2]);
                        }
                        break;
                    case "region":
                        {
                            Region r = new Region(); r.Id = f[1]; r.Name = f[2]; r.Faction = f[3]; r.Biome = f[5];
                            string[] lv = f[4].Split('-'); r.LvMin = int.Parse(lv[0]); r.LvMax = int.Parse(lv[1]);
                            r.Style = Array.IndexOf(StyleKey, r.Biome);
                            if (r.Style < 0) throw new Exception("Terrain inconnu : " + r.Biome);
                            r.Seeds = Pts(f[6]); r.Index = regions.Count; regions.Add(r);
                            if (f.Length > 7 && f[7].Length > 0) { double[] c = P2(f[7]); r.Cx = c[0]; r.Cz = c[1]; r.Fixed = true; }
                        }
                        break;
                    case "fleuve":
                    case "coulee":
                        {
                            Stream s = new Stream(); s.Name = f[1]; s.Lava = f[0] == "coulee";
                            string[] w = Words(f[2]); s.W0 = D(w[0]); s.W1 = D(w[1]); s.Pts = Pts(f[3]); streams.Add(s);
                        }
                        break;
                    case "lac":
                        {
                            Lake l = new Lake(); l.Name = f[1]; double[] c = P2(f[2]), r = P2(f[3]);
                            l.X = c[0]; l.Z = c[1]; l.Rx = r[0]; l.Rz = r[1]; lakes.Add(l);
                        }
                        break;
                    case "route":
                        {
                            Road r = new Road(); r.Kind = f[1]; r.Name = f[2]; r.Pts = Pts(f[3]); roads.Add(r);
                        }
                        break;
                    case "lieu":
                        {
                            Place p = new Place(); p.Kind = f[1]; p.Name = f[2]; p.Faction = f[3];
                            double[] c = P2(f[4]); p.X = c[0]; p.Z = c[1];
                            p.Safe = f.Length > 5 && f[5].Length > 0 ? D(f[5]) : 0;
                            p.Side = f.Length > 6 && f[6].Length > 0 ? f[6] : "d";
                            places.Add(p);
                        }
                        break;
                    case "repere":
                        {
                            Place p = new Place(); p.Kind = f[1]; p.Name = f[2]; p.Faction = f[3];
                            double[] c = P2(f[4]); p.X = c[0]; p.Z = c[1];
                            p.Safe = f.Length > 5 && f[5].Length > 0 ? D(f[5]) : 0;
                            p.Side = f.Length > 6 && f[6].Length > 0 ? f[6] : "d";
                            if (f.Length > 7) p.Region = f[7];
                            marks.Add(p);
                        }
                        break;
                    case "maj": marksDate = f[1]; break;
                    default: throw new Exception("Ligne inconnue dans le dessin : " + line);
                }
            }
        }

        // ------------------------------------------------------------------ Outils
        static double Clamp01(double t) { return t < 0 ? 0 : (t > 1 ? 1 : t); }
        static double Smooth(double t) { t = Clamp01(t); return t * t * (3 - 2 * t); }
        static double SMin(double a, double b, double k)
        {
            double h = Clamp01(0.5 + 0.5 * (b - a) / k);
            return b + (a - b) * h - k * h * (1 - h);
        }
        static double SMax(double a, double b, double k) { return -SMin(-a, -b, k); }
        static double CR(double p0, double p1, double p2, double p3, double t)
        {
            double t2 = t * t, t3 = t2 * t;
            return 0.5 * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
        }
        double P2W(int px) { return -Half + (px + 0.5) * Cell; }
        int W2P(double x) { int p = (int)Math.Floor((x + Half) / Cell); return p < 0 ? 0 : (p >= N ? N - 1 : p); }
        int Idx(double x, double z) { return W2P(z) * N + W2P(x); }
        float SX(double x) { return (float)((x + Half) / Cell); }

        // ------------------------------------------------------------------ Terrain
        /** Distance approchee a la cote, en blocs : positive dans les terres. */
        double LandAt(double x, double z)
        {
            double wx = x + 330 * Nz.Fbm(x / 1150 + 11.3, z / 1150 + 4.1, 3) + 105 * Nz.Fbm(x / 270 + 2.2, z / 270 + 9.7, 3);
            double wz = z + 330 * Nz.Fbm(x / 1150 + 71.9, z / 1150 + 33.3, 3) + 105 * Nz.Fbm(x / 270 + 51.2, z / 270 + 19.4, 3);
            double d = Math.Sqrt(wx * wx + wz * wz);
            double th = Math.Atan2(wz, wx); if (th < 0) th += 2 * Math.PI;
            int n = K.Length; double a = th / (2 * Math.PI) * n; int i1 = ((int)Math.Floor(a)) % n; double t = a - Math.Floor(a);
            double k = CR(K[(i1 + n - 1) % n], K[i1], K[(i1 + 1) % n], K[(i1 + 2) % n], t);
            double c = Math.Abs(Math.Cos(th)), s = Math.Abs(Math.Sin(th));
            double rsq = Half / Math.Pow(c * c * c + s * s * s, 1.0 / 3.0);
            double land = rsq * k - d;
            foreach (Blob b in blobs)
            {
                double ex = (wx - b.X) / b.Rx, ez = (wz - b.Z) / b.Rz; double e = Math.Sqrt(ex * ex + ez * ez);
                double m = Math.Min(b.Rx, b.Rz);
                if (b.Add) land = SMax(land, (1 - e) * m, 90); else land = SMin(land, (e - 1) * m, 110);
            }
            double edge = Half - Math.Max(Math.Abs(x), Math.Abs(z)) - 140;
            land = SMin(land, edge, 80);
            return land + 16 * Nz.Fbm(x / 75 + 13.5, z / 75 + 77.5, 2);
        }

        static double StyleH(int st, double x, double z, double n1, double n2, double n3, double rg, double rg2)
        {
            switch (st)
            {
                case S_PLAINE: return 70 + 5.5 * n1 + 2.4 * n2 + 0.8 * n3;
                case S_LANDE: return 72 + 7.5 * n1 + 3.2 * n2 + 1.0 * n3;
                case S_FORET: return 75 + 9 * n1 + 4.2 * n2 + 1.2 * n3;
                case S_SOMBRE: return 77 + 11 * n1 + 5 * n2 + 1.5 * n3;
                case S_COLLINES: return 90 + 20 * n1 + 9 * n2 + 22 * rg2 * rg2 + 2 * n3;
                case S_MONT: return 102 + 108 * Math.Pow(rg, 1.7) + 14 * n1 + 7 * n2 + 2 * n3;
                case S_DESERT:
                    {
                        double dune = 3.4 * (1 - Math.Abs(Nz.N2(x / 95 + n1 * 0.8, z / 46 + 5.5)));
                        double m = Nz.Fbm(x / 540 + 100.5, z / 540 + 100.5, 3);
                        double t = Clamp01((m - 0.10) / 0.5) * 3; double ti = Math.Floor(t); double tf = t - ti;
                        double terr = (ti + Smooth((tf - 0.3) / 0.25)) / 3;
                        double can = Math.Abs(Nz.N2(x / 250 + 31.5, z / 250 + 17.5));
                        double cut = Smooth((can - 0.035) / 0.05);
                        return 71 + 3.5 * n1 + dune * (1 - terr) + 30 * terr * cut + 1.2 * n2 * terr;
                    }
                case S_MARAIS: return 63.55 + 1.5 * n3 + 1.1 * n2 + 0.6 * n1;
                case S_CENDRES: return 84 + 13 * n1 + 26 * rg2 * rg2 + 6 * n2 + 2 * n3;
                default: return 90 + 12 * n1 + 20 * rg2 * rg2 + 5 * n2 + 2 * n3;
            }
        }

        double ConeAt(double r) { return 86 + (vTop - 86) * Math.Pow(Math.Max(0, 1 - r / vR), 1.5); }

        void BaseTerrain()
        {
            int nr = regions.Count;
            double[][] seeds = new double[nr][];
            int[] style = new int[nr];
            for (int r = 0; r < nr; r++)
            {
                style[r] = regions[r].Style;
                seeds[r] = new double[regions[r].Seeds.Count * 2];
                for (int k = 0; k < regions[r].Seeds.Count; k++) { seeds[r][k * 2] = regions[r].Seeds[k][0]; seeds[r][k * 2 + 1] = regions[r].Seeds[k][1]; }
            }
            const double BLEND = 420;
            Parallel.For(0, N, pz =>
            {
                double z = P2W(pz);
                double[] dr = new double[nr];
                for (int px = 0; px < N; px++)
                {
                    double x = P2W(px); int p = pz * N + px;
                    double land = LandAt(x, z);
                    Land[p] = (float)land;
                    double rx = x + 240 * Nz.Fbm(x / 720 + 3.1, z / 720 + 8.4, 3) + 55 * Nz.Fbm(x / 160 + 40.5, z / 160 + 12.5, 2);
                    double rz = z + 240 * Nz.Fbm(x / 720 + 60.7, z / 720 + 21.9, 3) + 55 * Nz.Fbm(x / 160 + 91.5, z / 160 + 66.5, 2);
                    double dmin = 1e18; int best = 0;
                    for (int r = 0; r < nr; r++)
                    {
                        double m = 1e18; double[] sd = seeds[r];
                        for (int k = 0; k < sd.Length; k += 2)
                        {
                            double ax = rx - sd[k], az = rz - sd[k + 1]; double q = ax * ax + az * az;
                            if (q < m) m = q;
                        }
                        dr[r] = Math.Sqrt(m);
                        if (dr[r] < dmin) { dmin = dr[r]; best = r; }
                    }
                    Reg[p] = (byte)best;
                    double n1 = Nz.Fbm(x / 620 + 1.7, z / 620 + 9.2, 4);
                    double n2 = Nz.Fbm(x / 165 + 21.3, z / 165 + 5.9, 4);
                    double n3 = Nz.Fbm(x / 50 + 7.7, z / 50 + 43.1, 3);
                    double rg = Nz.Ridged(x / 760 + 4.4, z / 760 + 2.8, 5);
                    double rg2 = Nz.Ridged(x / 270 + 9.1, z / 270 + 6.6, 4);
                    double hs = 0, ws = 0;
                    for (int r = 0; r < nr; r++)
                    {
                        double w = 1 - (dr[r] - dmin) / BLEND;
                        if (w <= 0) continue;
                        w = w * w * (3 - 2 * w);
                        hs += w * StyleH(style[r], x, z, n1, n2, n3, rg, rg2); ws += w;
                    }
                    double hr = hs / ws;
                    bool lava = false;
                    if (hasVolcano)
                    {
                        double ax = x - vx, az = z - vz;
                        double r = Math.Sqrt(ax * ax + az * az) * (1 + 0.10 * Nz.Fbm(x / 240 + 7.5, z / 240 + 3.5, 2));
                        if (r < vR)
                        {
                            double cone = ConeAt(r) + 8 * rg2 * (1 - r / vR);
                            if (cone > hr) hr = cone;
                            if (r < vC)
                            {
                                double rim = ConeAt(vC), fl = rim - 38;
                                hr = fl + (rim - fl) * Math.Pow(r / vC, 2.2);
                                if (r < vC * 0.72) lava = true;
                            }
                        }
                    }
                    double h;
                    if (land >= 0)
                    {
                        double t = Smooth(land / 230.0);
                        h = SEA + 0.5 + (hr - (SEA + 0.5)) * t;
                        if (land < 22) h = Math.Max(h, SEA + 0.35);
                    }
                    else
                    {
                        double dd = -land;
                        h = SEA + 0.5 - 3.0 * Smooth(dd / 28.0) - Math.Min(27, dd * 0.055) + 1.5 * n2 * Smooth(dd / 100);
                    }
                    H[p] = (float)h;
                    // Lac de lave du cratere : surface plate, a la hauteur du bord de la nappe.
                    if (lava && land > 0) { Lav[p] = 1; LavL[p] = (float)Math.Round(ConeAt(vC) - 38 + 38 * Math.Pow(0.72, 2.2)); }
                }
            });
        }

        void CarveLake(Lake lk)
        {
            lk.Level = Math.Round(Math.Max(SEA + 1, H[Idx(lk.X, lk.Z)] - 1.0));
            double R = Math.Max(lk.Rx, lk.Rz) * 2.3; int r = (int)(R / Cell) + 2; int cx = W2P(lk.X), cz = W2P(lk.Z);
            for (int pz = Math.Max(0, cz - r); pz <= Math.Min(N - 1, cz + r); pz++)
            {
                for (int px = Math.Max(0, cx - r); px <= Math.Min(N - 1, cx + r); px++)
                {
                    double x = P2W(px), z = P2W(pz);
                    double ex = (x - lk.X) / lk.Rx, ez = (z - lk.Z) / lk.Rz;
                    double e = Math.Sqrt(ex * ex + ez * ez) * (1 + 0.22 * Nz.Fbm(x / 130 + 3.3, z / 130 + 8.8, 2));
                    int p = pz * N + px;
                    if (Land[p] < 0) continue;
                    if (e < 1)
                    {
                        H[p] = (float)(lk.Level - 1.5 - 7 * (1 - e * e)); Wat[p] = (float)lk.Level; LakeM[p] = 1;
                    }
                    else if (e < 1.5)
                    {
                        double s = Smooth((e - 1) / 0.5), t = lk.Level + 0.7;
                        H[p] = (float)(t + (H[p] - t) * s);
                    }
                    if (e < 2.1) Green[p] = 1;
                }
            }
        }

        static double Dist(double[] a, double[] b) { double x = a[0] - b[0], z = a[1] - b[1]; return Math.Sqrt(x * x + z * z); }

        List<double[]> Spline(List<double[]> pts, double step)
        {
            List<double[]> res = new List<double[]>();
            int n = pts.Count;
            for (int i = 0; i < n - 1; i++)
            {
                double[] p0 = pts[Math.Max(0, i - 1)], p1 = pts[i], p2 = pts[i + 1], p3 = pts[Math.Min(n - 1, i + 2)];
                int k = Math.Max(2, (int)(Dist(p1, p2) / step));
                for (int j = 0; j < k; j++)
                {
                    double t = (double)j / k;
                    res.Add(new double[] { CR(p0[0], p1[0], p2[0], p3[0], t), CR(p0[1], p1[1], p2[1], p3[1], t) });
                }
            }
            res.Add(new double[] { pts[n - 1][0], pts[n - 1][1] });
            return res;
        }

        /** Meandres : decale chaque point sur sa normale, sauf pres des lieux et aux extremites. */
        void Meander(List<double[]> s, double amp, double wave, double off)
        {
            int n = s.Count; double[] len = new double[n];
            for (int i = 1; i < n; i++) len[i] = len[i - 1] + Dist(s[i - 1], s[i]);
            double total = len[n - 1];
            double[][] o = new double[n][];
            for (int i = 0; i < n; i++)
            {
                double[] a = s[Math.Max(0, i - 3)], b = s[Math.Min(n - 1, i + 3)];
                double tx = b[0] - a[0], tz = b[1] - a[1]; double tl = Math.Sqrt(tx * tx + tz * tz) + 1e-9;
                double k = 1;
                foreach (Place pl in places)
                {
                    double ax = s[i][0] - pl.X, az = s[i][1] - pl.Z;
                    k = Math.Min(k, Smooth((Math.Sqrt(ax * ax + az * az) - 130) / 380));
                }
                k *= Smooth(len[i] / 220) * Smooth((total - len[i]) / 220);
                double d = amp * k * (Nz.N2(len[i] / wave + off, 7.7) + 0.45 * Nz.N2(len[i] / (wave * 0.35) + off, 19.3));
                o[i] = new double[] { s[i][0] - tz / tl * d, s[i][1] + tx / tl * d };
            }
            for (int i = 0; i < n; i++) s[i] = o[i];
        }

        double SurfAt(double[] q)
        {
            int p = Idx(q[0], q[1]);
            if (!float.IsNaN(Wat[p])) return Wat[p];
            return H[p] < SEA ? SEA : H[p];
        }

        void TraceStream(Stream st, int idx)
        {
            List<double[]> raw = Spline(st.Pts, 4);
            Meander(raw, st.Lava ? 22 : 55, 260, idx * 13.7 + 1.1);
            int end = raw.Count - 1;
            for (int i = raw.Count / 4; i < raw.Count; i++)
            {
                if (Land[Idx(raw[i][0], raw[i][1])] < -30) { end = i; break; }
            }
            int n = end + 1;
            // Niveau d'arrivee : la mer, ou l'eau douce deja tracee (affluent).
            double T = st.Lava ? -1e9 : SEA;
            int pe = Idx(raw[end][0], raw[end][1]);
            if (!st.Lava && !float.IsNaN(Wat[pe]) && Land[pe] > 0) T = Wat[pe];
            // Descente : le niveau ne remonte jamais vers l'aval et reste sous le terrain.
            double prev = 1e9, len = 0;
            double[] lvl = new double[n];
            for (int i = 0; i < n; i++)
            {
                int p = Idx(raw[i][0], raw[i][1]);
                bool wet = !float.IsNaN(Wat[p]);
                double lv = Math.Min(prev, SurfAt(raw[i]) - (wet ? 0 : (st.Lava ? 0.8 : 1.2)));
                prev = lv; lvl[i] = lv;
                if (i > 0) len += Dist(raw[i - 1], raw[i]);
            }
            // Remontee : en amont d'un lac traverse, le fleuve ne peut pas etre plus bas que ce lac.
            for (int i = n - 1; i >= 0; i--)
            {
                int p = Idx(raw[i][0], raw[i][1]);
                if (!st.Lava && LakeM[p] == 1 && Wat[p] > T) T = Wat[p];
                if (lvl[i] < T) lvl[i] = T;
                lvl[i] = Math.Round(lvl[i]);    // en jeu, une surface d'eau ou de lave est a une hauteur entiere
            }
            for (int i = 0; i < n; i++)
            {
                double t = n > 1 ? (double)i / (n - 1) : 0;
                st.S.Add(new double[] { raw[i][0], raw[i][1], st.W0 + (st.W1 - st.W0) * Math.Pow(t, 0.75), lvl[i] });
            }
            st.Length = len;
        }

        void StampStream(Stream st)
        {
            for (int i = 0; i < tD.Length; i++) tD[i] = 1e9f;
            foreach (double[] s in st.S)
            {
                double hw = Math.Max(s[2] / 2, Cell * 0.75), bank = st.Lava ? 14 + hw : 26 + hw * 3.6, R = hw + bank;
                int r = (int)Math.Ceiling(R / Cell) + 1; int cx = W2P(s[0]), cz = W2P(s[1]);
                for (int pz = Math.Max(0, cz - r); pz <= Math.Min(N - 1, cz + r); pz++)
                {
                    for (int px = Math.Max(0, cx - r); px <= Math.Min(N - 1, cx + r); px++)
                    {
                        double ax = P2W(px) - s[0], az = P2W(pz) - s[1]; double d = Math.Sqrt(ax * ax + az * az);
                        if (d > R) continue;
                        int p = pz * N + px;
                        if (d < tD[p]) { tD[p] = (float)d; tL[p] = (float)s[3]; tW[p] = (float)hw; }
                    }
                }
            }
            for (int p = 0; p < tD.Length; p++)
            {
                if (tD[p] > 1e8f) continue;
                double d = tD[p], L = tL[p], hw = tW[p], bank = st.Lava ? 14 + hw : 26 + hw * 3.6;
                if (!st.Lava && d < RivD[p]) RivD[p] = (float)d;
                if (Land[p] < -6 || LakeM[p] == 1) continue;
                if (d <= hw)
                {
                    if (st.Lava) { if (H[p] > L - 1.5) H[p] = (float)(L - 1.5); Lav[p] = 1; LavL[p] = (float)L; }
                    else
                    {
                        double bed = L - 1.2 - 2.6 * (1 - (d / hw) * (d / hw));
                        if (H[p] > bed) H[p] = (float)bed;
                        Wat[p] = (float)L;
                    }
                }
                else
                {
                    double s = Smooth((d - hw) / bank), t = L + 0.7, hv = t + (H[p] - t) * s;
                    if (hv < H[p]) H[p] = (float)hv;
                    if (!st.Lava && d < hw + 10 && H[p] < t && float.IsNaN(Wat[p])) H[p] = (float)t;
                    if (!st.Lava && d < hw + 70) Green[p] = 1;
                }
            }
        }

        /**
         * Trace une route et calcule son profil : la chaussee suit le terrain tant que la pente reste praticable,
         * sinon elle passe en tranchee (deblai) ou sur un talus (remblai), a parts egales. S : x, z, hauteur, pont.
         */
        void TraceRoad(Road rd, int idx)
        {
            List<double[]> s = Spline(rd.Pts, 2);
            Meander(s, 14, 300, idx * 7.3 + 50.5);
            int n = s.Count;
            double slope = rd.Kind == "royale" ? 0.25 : 0.4;
            double[] t = new double[n], lo = new double[n], hi = new double[n], y = new double[n], ds = new double[n];
            bool[] wet = new bool[n];
            int ocean = 0, lava = 0; double len = 0;
            for (int i = 0; i < n; i++)
            {
                int p = Idx(s[i][0], s[i][1]);
                if (i > 0) { ds[i] = Dist(s[i - 1], s[i]); len += ds[i]; }
                wet[i] = !float.IsNaN(Wat[p]);
                if (Land[p] < 0 && H[p] < SEA) ocean++;
                if (Lav[p] == 1) lava++;
                t[i] = wet[i] ? Wat[p] + 2.0 : Math.Max(H[p], SEA + 1.0);
                lo[i] = hi[i] = t[i];
            }
            for (int i = 1; i < n; i++) { lo[i] = Math.Min(lo[i], lo[i - 1] + slope * ds[i]); hi[i] = Math.Max(hi[i], hi[i - 1] - slope * ds[i]); }
            for (int i = n - 2; i >= 0; i--) { lo[i] = Math.Min(lo[i], lo[i + 1] + slope * ds[i + 1]); hi[i] = Math.Max(hi[i], hi[i + 1] - slope * ds[i + 1]); }
            double[] pre = new double[n + 1];
            for (int i = 0; i < n; i++) pre[i + 1] = pre[i] + (lo[i] + hi[i]) / 2;
            double cut = 0, fill = 0; int run = -1;
            for (int i = 0; i < n; i++)
            {
                int a = Math.Max(0, i - 10), b = Math.Min(n - 1, i + 10);
                y[i] = (pre[b + 1] - pre[a]) / (b - a + 1);
                if (wet[i]) y[i] = Math.Max(y[i], t[i] - 0.5);
                else { cut = Math.Max(cut, t[i] - y[i]); fill = Math.Max(fill, y[i] - t[i]); }
                rd.S.Add(new double[] { s[i][0], s[i][1], y[i], wet[i] ? 1 : 0 });
                if (wet[i] && run < 0) run = i;
                if ((!wet[i] || i == n - 1) && run >= 0)
                {
                    rd.Bridges.Add(new double[] { s[Math.Max(0, run - 1)][0], s[Math.Max(0, run - 1)][1], s[i][0], s[i][1] });
                    run = -1;
                }
            }
            rd.Length = len;
            Log.AppendLine(string.Format(CultureInfo.InvariantCulture, "  {0,-22} {1,6:0} blocs, {2} pont(s), pente max {3:0.00}, tranchee max {4:0} blocs, talus max {5:0} blocs{6}{7}",
                rd.Name, len, rd.Bridges.Count, slope, cut, fill, ocean > 0 ? "  !! passe par la mer (" + ocean + " points)" : "",
                lava > 0 ? "  !! passe dans la lave (" + lava + " points)" : ""));
        }

        /** Pose la chaussee dans le relief : plat sur la largeur de la route, talus de 0,8 bloc par bloc sur les cotes. */
        void StampRoad(Road rd)
        {
            double hw = rd.Kind == "royale" ? 3.5 : 2.2, R = hw + 50;
            for (int i = 0; i < tD.Length; i++) tD[i] = 1e9f;
            int r = (int)Math.Ceiling(R / Cell) + 1;
            for (int k = 0; k < rd.S.Count; k++)
            {
                double[] s = rd.S[k];
                int cx = W2P(s[0]), cz = W2P(s[1]);
                // Les points sont serres (2 blocs) : on ne balaie le grand rayon qu'un point sur quatre.
                int rr = (k % 4 == 0 || k == rd.S.Count - 1) ? r : (int)Math.Ceiling((hw + 6) / Cell) + 1;
                for (int pz = Math.Max(0, cz - rr); pz <= Math.Min(N - 1, cz + rr); pz++)
                {
                    for (int px = Math.Max(0, cx - rr); px <= Math.Min(N - 1, cx + rr); px++)
                    {
                        double ax = P2W(px) - s[0], az = P2W(pz) - s[1]; double d = Math.Sqrt(ax * ax + az * az);
                        int p = pz * N + px;
                        if (d < tD[p]) { tD[p] = (float)d; tL[p] = (float)s[2]; }
                    }
                }
            }
            for (int p = 0; p < tD.Length; p++)
            {
                if (tD[p] > R) continue;
                if (!float.IsNaN(Wat[p]) || Lav[p] == 1 || (Land[p] < 0 && H[p] < SEA)) continue;
                double e = Math.Max(0, tD[p] - hw) * 0.8, y = tL[p];
                if (H[p] > y + e) H[p] = (float)(y + e); else if (H[p] < y - e) H[p] = (float)(y - e);
            }
        }

        double Slope(int p)
        {
            int px = p % N, pz = p / N;
            int xm = Math.Max(0, px - 1), xp = Math.Min(N - 1, px + 1), zm = Math.Max(0, pz - 1), zp = Math.Min(N - 1, pz + 1);
            double hx = (H[pz * N + xp] - H[pz * N + xm]) / ((xp - xm) * Cell), hz = (H[zp * N + px] - H[zm * N + px]) / ((zp - zm) * Cell);
            return Math.Sqrt(hx * hx + hz * hz);
        }

        void Biomes()
        {
            Parallel.For(0, N, pz =>
            {
                double z = P2W(pz);
                for (int px = 0; px < N; px++)
                {
                    int p = pz * N + px; double x = P2W(px);
                    double h = H[p]; double land = Land[p];
                    if (Lav[p] == 1) { Bio[p] = B_LAVE; continue; }
                    if (!float.IsNaN(Wat[p])) { Bio[p] = B_EAU; continue; }
                    if (h < SEA) { Bio[p] = (byte)(land < 0 ? B_OCEAN : B_EAU); continue; }
                    int st = regions[Reg[p]].Style;
                    bool dark = st == S_CENDRES || st == S_VOLCAN;
                    if (land < 40 && h < 66.2 && st != S_MARAIS) { Bio[p] = (byte)(dark ? B_PLAGE_NOIRE : B_PLAGE); continue; }
                    double g = Nz.Fbm(x / 210 + 5.5, z / 210 + 9.5, 3);
                    double sl = Slope(p);
                    int b;
                    switch (st)
                    {
                        case S_PLAINE: b = g > 0.42 ? B_FORET : B_PLAINE; break;
                        case S_LANDE: b = g > 0.5 ? B_SOMBRE : B_LANDE; break;
                        case S_FORET: b = g < -0.55 ? B_PLAINE : B_FORET; break;
                        case S_SOMBRE: b = g < -0.7 ? B_LANDE : B_SOMBRE; break;
                        case S_COLLINES: b = (h > 132 || sl > 0.8) ? B_ROCHE : (g > 0.3 ? B_PINS : B_COLLINES); break;
                        case S_MONT: b = (h >= 134 || sl > 0.85) ? B_ROCHE : (g > -0.1 ? B_PINS : B_ALPAGE); break;
                        case S_DESERT: b = Green[p] == 1 ? B_OASIS : (h > 84 ? B_MESA : B_SABLE); break;
                        case S_MARAIS: b = B_MARAIS; break;
                        case S_CENDRES: b = (sl > 0.7 || h > 112) ? B_VOLC : B_CENDRES; break;
                        default: b = B_VOLC; break;
                    }
                    if (!dark && st != S_DESERT)
                    {
                        if (h >= 166) b = B_NEIGE; else if (h >= 146 && b != B_ROCHE) b = B_ROCHE;
                    }
                    Bio[p] = (byte)b;
                }
            });
        }

        double CoastDist(double x, double z)
        {
            int cx = W2P(x), cz = W2P(z), r = (int)(1500 / Cell); double best = 1e9;
            for (int pz = Math.Max(0, cz - r); pz <= Math.Min(N - 1, cz + r); pz++)
            {
                for (int px = Math.Max(0, cx - r); px <= Math.Min(N - 1, cx + r); px++)
                {
                    int p = pz * N + px;
                    if (Land[p] >= 0 || H[p] >= SEA) continue;
                    double ax = (px - cx) * Cell, az = (pz - cz) * Cell; double d = Math.Sqrt(ax * ax + az * az);
                    if (d < best) best = d;
                }
            }
            return best;
        }

        void Stats()
        {
            long landPx = 0; double mn = 1e9; topH = -1e9;
            double[] sx = new double[regions.Count], sz = new double[regions.Count], sh = new double[regions.Count];
            for (int p = 0; p < H.Length; p++)
            {
                if (H[p] < mn) mn = H[p];
                if (Land[p] < 0 && H[p] < SEA) continue;
                landPx++;
                if (H[p] > topH) { topH = H[p]; topP = p; }
                Region r = regions[Reg[p]];
                r.Pixels++; sx[r.Index] += p % N; sz[r.Index] += p / N; sh[r.Index] += Math.Max(H[p], SEA);
            }
            double cell2 = Cell * Cell;
            Log.AppendLine(string.Format(CultureInfo.InvariantCulture, "Carte {0:0} x {0:0} blocs, {1} px de cote ({2:0.##} blocs par pixel), graine {3}.", Size, N, Cell, Seed));
            Log.AppendLine(string.Format(CultureInfo.InvariantCulture, "Terres : {0:0.0} millions de blocs carres ({1:0} % de la carte). Fond marin le plus bas Y {2:0}, sommet Y {3:0} en ({4:0}, {5:0}).",
                landPx * cell2 / 1e6, 100.0 * landPx / H.Length, mn, topH, P2W(topP % N), P2W(topP / N)));
            Log.AppendLine();
            Log.AppendLine("Regions (part des terres, altitude moyenne) :");
            foreach (Region r in regions)
            {
                if (r.Pixels > 0) r.AvgH = sh[r.Index] / r.Pixels;
                if (r.Pixels > 0 && !r.Fixed)
                {
                    r.Cx = P2W((int)(sx[r.Index] / r.Pixels)); r.Cz = P2W((int)(sz[r.Index] / r.Pixels));
                    if (Reg[Idx(r.Cx, r.Cz)] != r.Index)
                    {
                        double best = 1e18;
                        double ox = r.Cx, oz = r.Cz;
                        foreach (double[] s in r.Seeds)
                        {
                            double d = (s[0] - ox) * (s[0] - ox) + (s[1] - oz) * (s[1] - oz);
                            if (d < best) { best = d; r.Cx = s[0]; r.Cz = s[1]; }
                        }
                    }
                }
                Log.AppendLine(string.Format(CultureInfo.InvariantCulture, "  {0} {1,-20} {2,-8} niv. {3,2}-{4,-2} {5,-13} {6,5:0.0} M blocs2 ({7,4:0.0} %), Y moyen {8:0}",
                    r.Id, r.Name, r.Faction, r.LvMin, r.LvMax, StyleName[r.Style], r.Pixels * cell2 / 1e6, 100.0 * r.Pixels / landPx, r.AvgH));
            }
            Log.AppendLine();
            Log.AppendLine("Fleuves et coulees :");
            foreach (Stream s in streams)
            {
                Log.AppendLine(string.Format(CultureInfo.InvariantCulture, "  {0,-20} {1,6:0} blocs, de Y {2:0} a Y {3:0}{4}", s.Name, s.Length,
                    s.S[0][3], s.S[s.S.Count - 1][3], s.Lava ? " (lave)" : ""));
            }
            Log.AppendLine();
            Log.AppendLine("Lacs :");
            foreach (Lake l in lakes) Log.AppendLine(string.Format(CultureInfo.InvariantCulture, "  {0,-20} niveau Y {1:0} en ({2:0}, {3:0})", l.Name, l.Level, l.X, l.Z));
            Log.AppendLine();
            Log.AppendLine("Lieux (region, altitude, distance a la mer) :");
            foreach (Place pl in places)
            {
                int p = Idx(pl.X, pl.Z);
                string warn = "";
                if (Land[p] < 0 && H[p] < SEA) warn = "  !! en mer";
                else if (pl.Kind != "capitale" && (!float.IsNaN(Wat[p]) || H[p] < SEA)) warn = "  !! dans l'eau";
                else if (Lav[p] == 1) warn = "  !! dans la lave";
                double cd = CoastDist(pl.X, pl.Z);
                Log.AppendLine(string.Format(CultureInfo.InvariantCulture, "  {0,-8} {1,-32} ({2,5:0}, {3,5:0})  {4,-20} Y {5,3:0}  mer a {6}{7}",
                    pl.Kind, pl.Name, pl.X, pl.Z, regions[Reg[p]].Name, Math.Max(H[p], SEA), cd > 1e8 ? "plus de 1500" : ((int)cd).ToString(), warn));
            }
            if (marks.Count > 0)
            {
                Log.AppendLine();
                Log.AppendLine("Reperes des cartes (ajoutes en jeu, absents de continent.dat)" + (marksDate.Length > 0 ? ", a jour au " + marksDate : "") + " :");
                foreach (Place pl in marks)
                {
                    if (!Named(pl)) continue;
                    int p = Idx(pl.X, pl.Z);
                    Log.AppendLine(string.Format(CultureInfo.InvariantCulture, "  {0,-8} {1,-32} ({2,5:0}, {3,5:0})  {4,-20} Y {5,3:0}",
                        pl.Kind, pl.Name, pl.X, pl.Z, regions[Reg[p]].Name, Math.Max(H[p], SEA)));
                }
                Log.AppendLine("  Par region (camps de monstres, ruines, lieux-dits) :");
                foreach (Region r in regions)
                {
                    int[] n = MarkCount(r.Index);
                    Log.AppendLine(string.Format(CultureInfo.InvariantCulture, "    {0} {1,-20} {2,2} camps, {3,2} ruines, {4,3} lieux-dits", r.Id, r.Name, n[0], n[1], n[2]));
                }
                int[] all = MarkCount(-1);
                Log.AppendLine(string.Format(CultureInfo.InvariantCulture, "    Total                  {0,2} camps, {1,2} ruines, {2,3} lieux-dits", all[0], all[1], all[2]));
            }
        }

        static bool Named(Place m) { return m.Kind == "hameau" || m.Kind == "cratere"; }

        /** Camps, ruines et lieux-dits d'une region (-1 : toute la carte). */
        int[] MarkCount(int region)
        {
            int[] n = new int[3];
            foreach (Place m in marks)
            {
                int k = m.Kind == "camp" ? 0 : (m.Kind == "ruine" ? 1 : (m.Kind == "decor" ? 2 : -1));
                if (k < 0) continue;
                // La region d'un site est celle de son identifiant en jeu ; a defaut, celle du terrain sous lui.
                if (region >= 0 && (m.Region.Length > 0 ? m.Region != regions[region].Id : Reg[Idx(m.X, m.Z)] != region)) continue;
                n[k]++;
            }
            return n;
        }

        void Generate()
        {
            int n2 = N * N;
            H = new float[n2]; Land = new float[n2]; Wat = new float[n2]; RivD = new float[n2]; LavL = new float[n2];
            Reg = new byte[n2]; Bio = new byte[n2]; Lav = new byte[n2]; LakeM = new byte[n2]; Green = new byte[n2];
            tD = new float[n2]; tL = new float[n2]; tW = new float[n2];
            for (int i = 0; i < n2; i++) { Wat[i] = float.NaN; RivD[i] = 1e9f; }
            BaseTerrain();
            foreach (Lake l in lakes) CarveLake(l);
            for (int i = 0; i < streams.Count; i++) { TraceStream(streams[i], i); StampStream(streams[i]); }
            StringBuilder head = Log; Log = new StringBuilder();
            for (int i = 0; i < roads.Count; i++) { TraceRoad(roads[i], i); StampRoad(roads[i]); }
            StringBuilder roadLog = Log; Log = head;
            Biomes();
            Stats();
            Log.AppendLine();
            Log.AppendLine("Routes :");
            Log.Append(roadLog.ToString());
        }

        // ------------------------------------------------------------------ Donnees pour le generateur de terrain
        static void WInt(System.IO.Stream s, int v) { s.WriteByte((byte)(v >> 24)); s.WriteByte((byte)(v >> 16)); s.WriteByte((byte)(v >> 8)); s.WriteByte((byte)v); }
        static void WFloat(System.IO.Stream s, double f) { byte[] b = BitConverter.GetBytes((float)f); if (BitConverter.IsLittleEndian) Array.Reverse(b); s.Write(b, 0, 4); }
        static void WUtf(System.IO.Stream s, string t) { byte[] b = Encoding.UTF8.GetBytes(t); s.WriteByte((byte)(b.Length >> 8)); s.WriteByte((byte)b.Length); s.Write(b, 0, b.Length); }

        /**
         * Ecrit continent.dat (gzip, entiers et flottants gros-boutistes, chaines comme DataOutputStream.writeUTF) :
         * en-tete, regions, lieux, routes, puis quatre couches de N x N valeurs, ligne par ligne du nord au sud :
         * hauteur du sol (Y x 64, 2 octets), niveau du fluide (Y de sa surface, 0 = aucun), biome, region.
         */
        void WriteData(string path)
        {
            int n2 = N * N;
            using (FileStream fs = new FileStream(path, FileMode.Create))
            using (System.IO.Compression.GZipStream gz = new System.IO.Compression.GZipStream(fs, System.IO.Compression.CompressionMode.Compress))
            using (BufferedStream o = new BufferedStream(gz, 1 << 20))
            {
                WInt(o, 0x4F434F4E); WInt(o, 1); WInt(o, (int)Size); WInt(o, N); WFloat(o, SEA);
                WInt(o, regions.Count);
                foreach (Region r in regions) { WUtf(o, r.Id); WUtf(o, r.Name); WUtf(o, r.Faction); WInt(o, r.LvMin); WInt(o, r.LvMax); WUtf(o, r.Biome); }
                WInt(o, places.Count);
                foreach (Place p in places) { WUtf(o, p.Kind); WUtf(o, p.Name); WUtf(o, p.Faction); WInt(o, (int)p.X); WInt(o, (int)p.Z); WInt(o, (int)p.Safe); }
                WInt(o, roads.Count);
                foreach (Road rd in roads)
                {
                    WUtf(o, rd.Kind); WUtf(o, rd.Name); WInt(o, rd.S.Count);
                    foreach (double[] s in rd.S) { WFloat(o, s[0]); WFloat(o, s[1]); WFloat(o, s[2]); o.WriteByte((byte)s[3]); }
                }
                byte[] buf = new byte[n2 * 2];
                for (int p = 0; p < n2; p++)
                {
                    int v = (int)Math.Round(H[p] * 64.0); if (v < 0) v = 0; if (v > 65535) v = 65535;
                    buf[p * 2] = (byte)(v >> 8); buf[p * 2 + 1] = (byte)v;
                }
                o.Write(buf, 0, buf.Length);
                buf = new byte[n2];
                for (int p = 0; p < n2; p++)
                {
                    double f = 0;
                    if (Lav[p] == 1) f = LavL[p];
                    else if (!float.IsNaN(Wat[p])) f = Wat[p];
                    else if (H[p] < SEA) f = SEA;
                    buf[p] = (byte)B8(Math.Round(f));
                }
                o.Write(buf, 0, n2);
                o.Write(Bio, 0, n2);
                o.Write(Reg, 0, n2);
            }
        }

        // ------------------------------------------------------------------ Rendu
        static int Rgb(int hex) { return unchecked((int)0xFF000000) | hex; }
        static int B8(double v) { return v < 0 ? 0 : (v > 255 ? 255 : (int)v); }
        static int Mul(int c, double f)
        {
            return unchecked((int)0xFF000000) | (B8(((c >> 16) & 255) * f) << 16) | (B8(((c >> 8) & 255) * f) << 8) | B8((c & 255) * f);
        }
        static int Mix(int a, int b, double t)
        {
            t = Clamp01(t);
            int r = (int)(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
            int g = (int)(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
            int bl = (int)((a & 255) + ((b & 255) - (a & 255)) * t);
            return unchecked((int)0xFF000000) | (r << 16) | (g << 8) | bl;
        }
        static Color Col(int hex) { return Color.FromArgb(255, (hex >> 16) & 255, (hex >> 8) & 255, hex & 255); }
        static Color Col(int hex, int a) { return Color.FromArgb(a, (hex >> 16) & 255, (hex >> 8) & 255, hex & 255); }

        void Hillshade()
        {
            Sh = new float[N * N];
            const double lx = -0.62, lz = -0.62, lu = 0.48, exag = 4.2;
            Parallel.For(0, N, pz =>
            {
                for (int px = 0; px < N; px++)
                {
                    int p = pz * N + px;
                    int xm = Math.Max(0, px - 1), xp = Math.Min(N - 1, px + 1), zm = Math.Max(0, pz - 1), zp = Math.Min(N - 1, pz + 1);
                    double hx = (Math.Max(H[pz * N + xp], SEA) - Math.Max(H[pz * N + xm], SEA)) / ((xp - xm) * Cell) * exag;
                    double hz = (Math.Max(H[zp * N + px], SEA) - Math.Max(H[zm * N + px], SEA)) / ((zp - zm) * Cell) * exag;
                    double len = Math.Sqrt(hx * hx + hz * hz + 1);
                    double d = (-hx * lx - hz * lz + lu) / len;
                    double s = 1 + (d - lu) * 0.95;
                    Sh[p] = (float)(s < 0.5 ? 0.5 : (s > 1.38 ? 1.38 : s));
                }
            });
        }

        bool IsSea(int p) { return Land[p] < 0 && H[p] < SEA; }
        bool IsWet(int p) { return !float.IsNaN(Wat[p]) || H[p] < SEA; }
        int OceanCol(int p) { return Mix(Rgb(0x66a3cc), Rgb(0x1f3f68), (SEA - H[p]) / 26.0); }
        int PaleSea(int p) { return Mix(Rgb(0xc4dcec), Rgb(0x9fc2dc), (SEA - H[p]) / 26.0); }

        int[] NewCanvas()
        {
            int[] buf = new int[W * N]; int paper = Rgb(C_PAPER);
            for (int y = 0; y < N; y++) for (int x = N; x < W; x++) buf[y * W + x] = paper;
            return buf;
        }

        Bitmap ToBitmap(int[] buf, int w, int h)
        {
            Bitmap bmp = new Bitmap(w, h, PixelFormat.Format32bppArgb);
            BitmapData bd = bmp.LockBits(new Rectangle(0, 0, w, h), ImageLockMode.WriteOnly, PixelFormat.Format32bppArgb);
            Marshal.Copy(buf, 0, bd.Scan0, buf.Length);
            bmp.UnlockBits(bd);
            return bmp;
        }

        Graphics Begin(Bitmap bmp)
        {
            Graphics g = Graphics.FromImage(bmp);
            g.SmoothingMode = SmoothingMode.AntiAlias;
            g.TextRenderingHint = TextRenderingHint.AntiAliasGridFit;
            g.SetClip(new Rectangle(0, 0, N, N));
            return g;
        }

        Font F(float px, FontStyle st) { return new Font(fontName, px * U, st, GraphicsUnit.Pixel); }

        void Label(Graphics g, string text, float x, float y, float size, FontStyle fs, Color fill, Color halo, StringAlignment al)
        {
            using (GraphicsPath path = new GraphicsPath())
            using (FontFamily ff = new FontFamily(fontName))
            using (StringFormat sf = new StringFormat())
            {
                sf.Alignment = al; sf.LineAlignment = StringAlignment.Center;
                path.AddString(text, ff, (int)fs, size * U, new PointF(x, y), sf);
                using (Pen pen = new Pen(halo, Math.Max(2.2f, size * U * 0.22f))) { pen.LineJoin = LineJoin.Round; g.DrawPath(pen, path); }
                using (SolidBrush br = new SolidBrush(fill)) g.FillPath(br, path);
            }
        }

        void DrawStreams(Graphics g, int water, float scale)
        {
            foreach (Stream st in streams)
            {
                Color c = st.Lava ? Col(0xff7a1a) : Col(water);
                int n = st.S.Count; const int chunk = 10;
                for (int i = 0; i < n - 1; i += chunk)
                {
                    int j = Math.Min(n - 1, i + chunk);
                    PointF[] pts = new PointF[j - i + 1];
                    for (int k = 0; k <= j - i; k++) pts[k] = new PointF(SX(st.S[i + k][0]), SX(st.S[i + k][1]));
                    float w = (float)Math.Max(1.5 * U, st.S[(i + j) / 2][2] / Cell * scale);
                    using (Pen pen = new Pen(c, w)) { pen.StartCap = LineCap.Round; pen.EndCap = LineCap.Round; pen.LineJoin = LineJoin.Round; g.DrawLines(pen, pts); }
                }
            }
        }

        void DrawRoads(Graphics g)
        {
            foreach (Road rd in roads)
            {
                int n = (rd.S.Count + 5) / 6 + 1;
                PointF[] pts = new PointF[n];
                for (int i = 0; i < n; i++) { int k = Math.Min(rd.S.Count - 1, i * 6); pts[i] = new PointF(SX(rd.S[k][0]), SX(rd.S[k][1])); }
                pts[n - 1] = new PointF(SX(rd.S[rd.S.Count - 1][0]), SX(rd.S[rd.S.Count - 1][1]));
                bool main = rd.Kind == "royale";
                if (!main) using (Pen pen = new Pen(Col(0xfff3d2, 150), 4.6f * U)) { pen.LineJoin = LineJoin.Round; g.DrawLines(pen, pts); }
                using (Pen pen = new Pen(Col(0x4a2f18), (main ? 4.4f : 2.3f) * U))
                {
                    pen.LineJoin = LineJoin.Round;
                    if (!main) pen.DashPattern = new float[] { 3.2f, 1.8f };
                    g.DrawLines(pen, pts);
                }
                if (main) using (Pen pen = new Pen(Col(0xf2dfae), 1.9f * U)) { pen.LineJoin = LineJoin.Round; g.DrawLines(pen, pts); }
                foreach (double[] b in rd.Bridges)
                {
                    using (Pen pen = new Pen(Col(0x2a1e12), 7.5f * U)) g.DrawLine(pen, SX(b[0]), SX(b[1]), SX(b[2]), SX(b[3]));
                    using (Pen pen = new Pen(Col(0xe8e2d4), 4.2f * U)) g.DrawLine(pen, SX(b[0]), SX(b[1]), SX(b[2]), SX(b[3]));
                }
            }
        }

        static int FacCol(string f) { return f == "lumiere" ? C_LUM : (f == "ombre" ? C_OMB : C_NEU); }

        void Icon(Graphics g, string kind, string faction, float x, float y)
        {
            using (Pen ink = new Pen(Col(C_INK), 1.6f * U))
            using (SolidBrush fill = new SolidBrush(Col(FacCol(faction))))
            using (SolidBrush dark = new SolidBrush(Col(C_INK)))
            using (SolidBrush white = new SolidBrush(Color.White))
            {
                if (kind == "capitale")
                {
                    float r = 10.5f * U;
                    g.FillEllipse(dark, x - r - 2 * U, y - r - 2 * U, 2 * r + 4 * U, 2 * r + 4 * U);
                    g.FillEllipse(fill, x - r, y - r, 2 * r, 2 * r);
                    PointF[] star = new PointF[10];
                    for (int i = 0; i < 10; i++)
                    {
                        double a = -Math.PI / 2 + i * Math.PI / 5; float rr = (i % 2 == 0 ? 7.2f : 3.1f) * U;
                        star[i] = new PointF(x + (float)Math.Cos(a) * rr, y + (float)Math.Sin(a) * rr);
                    }
                    g.FillPolygon(white, star); g.DrawPolygon(ink, star);
                }
                else if (kind == "ville")
                {
                    float r = 7f * U;
                    g.FillRectangle(white, x - r, y - r, 2 * r, 2 * r); g.DrawRectangle(ink, x - r, y - r, 2 * r, 2 * r);
                    g.FillRectangle(dark, x - 2.6f * U, y - 2.6f * U, 5.2f * U, 5.2f * U);
                }
                else if (kind == "village")
                {
                    float r = 4.6f * U;
                    g.FillEllipse(fill, x - r, y - r, 2 * r, 2 * r); g.DrawEllipse(ink, x - r, y - r, 2 * r, 2 * r);
                }
                else if (kind == "hameau")
                {
                    float r = 3.3f * U;
                    g.FillEllipse(fill, x - r, y - r, 2 * r, 2 * r);
                    using (Pen thin = new Pen(Col(C_INK), 1.2f * U)) g.DrawEllipse(thin, x - r, y - r, 2 * r, 2 * r);
                }
                else if (kind == "cratere")
                {
                    // Eclat tombe du ciel : etoile sombre a huit branches, coeur de la couleur de la faction.
                    PointF[] star = new PointF[16];
                    for (int i = 0; i < 16; i++)
                    {
                        double a = -Math.PI / 2 + i * Math.PI / 8; float rr = (i % 2 == 0 ? 7.4f : 3.2f) * U;
                        star[i] = new PointF(x + (float)Math.Cos(a) * rr, y + (float)Math.Sin(a) * rr);
                    }
                    g.FillPolygon(dark, star);
                    float c = 2.7f * U;
                    g.FillEllipse(fill, x - c, y - c, 2 * c, 2 * c);
                }
                else if (kind == "camp")
                {
                    float r = 4.2f * U;
                    PointF[] t = { new PointF(x, y - r), new PointF(x + r * 0.92f, y + r * 0.72f), new PointF(x - r * 0.92f, y + r * 0.72f) };
                    using (SolidBrush red = new SolidBrush(Col(0xd2452f))) g.FillPolygon(red, t);
                    using (Pen thin = new Pen(Col(C_INK), 1f * U)) g.DrawPolygon(thin, t);
                }
                else if (kind == "ruine")
                {
                    float r = 3f * U;
                    using (SolidBrush grey = new SolidBrush(Col(0x9aa0aa))) g.FillRectangle(grey, x - r, y - r, 2 * r, 2 * r);
                    using (Pen thin = new Pen(Col(C_INK), 1f * U)) g.DrawRectangle(thin, x - r, y - r, 2 * r, 2 * r);
                }
                else if (kind == "decor")
                {
                    float r = 1.7f * U;
                    using (SolidBrush brown = new SolidBrush(Col(0x5a3d22, 215))) g.FillEllipse(brown, x - r, y - r, 2 * r, 2 * r);
                }
                else
                {
                    float r = 5.6f * U;
                    PointF[] d = { new PointF(x, y - r), new PointF(x + r, y), new PointF(x, y + r), new PointF(x - r, y) };
                    using (SolidBrush red = new SolidBrush(Col(0x8e1f1f))) g.FillPolygon(red, d);
                    g.DrawPolygon(ink, d);
                }
            }
        }

        void DrawPlaces(Graphics g, bool safe, bool names)
        {
            if (safe)
            {
                foreach (Place pl in places)
                {
                    if (pl.Safe <= 0) continue;
                    float r = (float)(pl.Safe / Cell), x = SX(pl.X), y = SX(pl.Z);
                    using (SolidBrush b = new SolidBrush(Col(0x3ecf6e, 120))) g.FillEllipse(b, x - r, y - r, 2 * r, 2 * r);
                    using (Pen pn = new Pen(Col(0x127a36), 2f * U)) g.DrawEllipse(pn, x - r, y - r, 2 * r, 2 * r);
                }
                foreach (Place m in marks)
                {
                    if (m.Safe <= 0) continue;
                    float r = (float)(m.Safe / Cell), x = SX(m.X), y = SX(m.Z);
                    using (SolidBrush b = new SolidBrush(Col(0x3ecf6e, 120))) g.FillEllipse(b, x - r, y - r, 2 * r, 2 * r);
                    using (Pen pn = new Pen(Col(0x127a36), 2f * U)) g.DrawEllipse(pn, x - r, y - r, 2 * r, 2 * r);
                }
            }
            foreach (Place pl in places) if (pl.Kind == "donjon") Icon(g, pl.Kind, pl.Faction, SX(pl.X), SX(pl.Z));
            foreach (Place m in marks)
            {
                if (!Named(m)) continue;
                Icon(g, m.Kind, m.Faction, SX(m.X), SX(m.Z));
                if (names) PlaceLabel(g, m, 11.5f, (m.Kind == "cratere" ? 9 : 6) * U, FontStyle.Italic);
            }
            foreach (Place pl in places)
            {
                if (pl.Kind == "donjon") continue;
                Icon(g, pl.Kind, pl.Faction, SX(pl.X), SX(pl.Z));
                if (!names) continue;
                bool cap = pl.Kind == "capitale", town = pl.Kind == "ville";
                PlaceLabel(g, pl, cap ? 22 : (town ? 16 : 13), (cap ? 17 : (town ? 12 : 8)) * U, cap || town ? FontStyle.Bold : FontStyle.Italic);
            }
        }

        void PlaceLabel(Graphics g, Place pl, float size, float gap, FontStyle fs)
        {
            float x = SX(pl.X), y = SX(pl.Z), lx = x, lyy = y; StringAlignment al = StringAlignment.Center;
            if (pl.Side == "d") { lx = x + gap; al = StringAlignment.Near; }
            else if (pl.Side == "g") { lx = x - gap; al = StringAlignment.Far; }
            else if (pl.Side == "h") lyy = y - gap - size * U * 0.55f;
            else lyy = y + gap + size * U * 0.55f;
            Label(g, pl.Name, lx, lyy, size, fs, Col(C_INK), Col(0xfff8e6, 235), al);
        }

        /** Camps de monstres, ruines et lieux-dits : seulement sur la carte des sites. */
        void DrawMarks(Graphics g)
        {
            foreach (Place m in marks) if (m.Kind == "decor") Icon(g, m.Kind, m.Faction, SX(m.X), SX(m.Z));
            foreach (Place m in marks) if (m.Kind == "ruine") Icon(g, m.Kind, m.Faction, SX(m.X), SX(m.Z));
            foreach (Place m in marks) if (m.Kind == "camp") Icon(g, m.Kind, m.Faction, SX(m.X), SX(m.Z));
        }

        void DrawRegionLabels(Graphics g, int mode)
        {
            foreach (Region r in regions)
            {
                float x = SX(r.Cx), y = SX(r.Cz);
                string t = r.Name;
                if (mode == 1) t += "\nniv. " + r.LvMin + " – " + r.LvMax;
                else if (mode == 2) t += "\n" + (r.Faction == "lumiere" ? "Lumière" : (r.Faction == "ombre" ? "Ombre" : "contesté"));
                else if (mode == 3) t += "\n" + StyleName[r.Style] + " · niv. " + r.LvMin + " – " + r.LvMax;
                Label(g, t, x, y, mode == 0 ? 19 : 18, FontStyle.Bold | FontStyle.Italic, Col(mode == 0 ? 0x2b1c0c : 0x1d1408),
                    Col(0xfff8e6, mode == 0 ? 170 : 215), StringAlignment.Center);
            }
        }

        /** Noms des fleuves et des lacs. Le nom d'un fleuve se pose la ou il gene le moins les autres etiquettes. */
        void DrawWaterNames(Graphics g)
        {
            List<double[]> busy = new List<double[]>();
            foreach (Region r in regions) { busy.Add(new double[] { r.Cx - 220, r.Cz }); busy.Add(new double[] { r.Cx, r.Cz }); busy.Add(new double[] { r.Cx + 220, r.Cz }); }
            foreach (Place pl in places)
            {
                if (pl.Kind == "donjon") continue;
                busy.Add(new double[] { pl.X, pl.Z });
                double ox = pl.Side == "d" ? 230 : (pl.Side == "g" ? -230 : 0), oz = pl.Side == "h" ? -110 : (pl.Side == "b" ? 110 : 0);
                busy.Add(new double[] { pl.X + ox, pl.Z + oz });
            }
            foreach (Place m in marks)
            {
                if (!Named(m)) continue;
                busy.Add(new double[] { m.X, m.Z });
                busy.Add(new double[] { m.X + (m.Side == "d" ? 230 : (m.Side == "g" ? -230 : 0)), m.Z });
            }
            foreach (Lake l in lakes)
            {
                busy.Add(new double[] { l.X, l.Z }); busy.Add(new double[] { l.X, l.Z + l.Rz + 55 });
                bool twin = false;
                foreach (Place pl in places) if (pl.Name == l.Name) twin = true;
                if (!twin) Label(g, l.Name, SX(l.X), SX(l.Z + l.Rz) + 11 * U, 12.5f, FontStyle.Italic, Col(0x134a7c), Col(0xffffff, 215), StringAlignment.Center);
            }
            foreach (Stream st in streams)
            {
                double[] pick = null; double bestD = -1;
                for (double f = 0.2; f < 0.85; f += 0.05)
                {
                    double[] s = st.S[(int)(st.S.Count * f)];
                    double cx = s[0] + 210, cz = s[1] - 45, dmin = 1e18;
                    foreach (double[] b in busy) { double d = (b[0] - cx) * (b[0] - cx) + (b[1] - cz) * (b[1] - cz) * 4; if (d < dmin) dmin = d; }
                    if (dmin > bestD) { bestD = dmin; pick = s; }
                }
                busy.Add(new double[] { pick[0] + 210, pick[1] - 45 });
                Label(g, st.Name, SX(pick[0]) + 8 * U, SX(pick[1]) - 9 * U, 12.5f, FontStyle.Italic, Col(st.Lava ? 0x8a2a00 : 0x134a7c), Col(0xffffff, 215), StringAlignment.Near);
            }
        }

        void Borders(int[] buf, double f)
        {
            for (int pz = 1; pz < N - 1; pz++)
            {
                for (int px = 1; px < N - 1; px++)
                {
                    int p = pz * N + px;
                    if (IsSea(p)) continue;
                    if (Reg[p] != Reg[p + 1] || Reg[p] != Reg[p + N]) { buf[pz * W + px] = Mul(buf[pz * W + px], f); buf[pz * W + px + 1] = Mul(buf[pz * W + px + 1], f); }
                }
            }
        }

        // ---- Legende (colonne de droite)
        void LegBegin(Graphics g, string mapName)
        {
            g.ResetClip();
            float x = N + 22 * U; ly = 22 * U;
            using (SolidBrush ink = new SolidBrush(Col(C_INK)))
            using (SolidBrush gold = new SolidBrush(Col(0x9a6a12)))
            {
                using (Font f = F(31, FontStyle.Bold)) g.DrawString("ORBIT MMO", f, ink, x - 3 * U, ly);
                ly += 40 * U;
                using (Font f = F(15, FontStyle.Italic)) g.DrawString(Title, f, ink, x, ly);
                ly += 28 * U;
                if (marksDate.Length > 0)
                {
                    using (Font f = F(13, FontStyle.Italic)) g.DrawString("Lieux à jour au " + marksDate, f, ink, x, ly - 6 * U);
                    ly += 18 * U;
                }
                using (Font f = F(21, FontStyle.Bold)) g.DrawString(mapName, f, gold, x, ly);
                ly += 36 * U;
            }
            using (Pen pn = new Pen(Col(0x9a6a12), 1.5f * U)) g.DrawLine(pn, x, ly, W - 22 * U, ly);
            ly += 12 * U;
        }

        void LegHead(Graphics g, string t)
        {
            ly += 6 * U;
            using (SolidBrush ink = new SolidBrush(Col(C_INK))) using (Font f = F(15.5f, FontStyle.Bold)) g.DrawString(t, f, ink, N + 22 * U, ly);
            ly += 25 * U;
        }

        void LegText(Graphics g, string t, float dx)
        {
            using (SolidBrush ink = new SolidBrush(Col(C_INK))) using (Font f = F(13.5f, FontStyle.Regular))
            {
                RectangleF box = new RectangleF(N + 22 * U + dx, ly, W - N - 40 * U - dx, 400 * U);
                SizeF sz = g.MeasureString(t, f, (int)box.Width);
                g.DrawString(t, f, ink, box);
                ly += Math.Max(sz.Height, 19 * U) + 3 * U;
            }
        }

        void LegSwatch(Graphics g, Color c, string t, bool hatch)
        {
            float x = N + 22 * U;
            using (SolidBrush b = new SolidBrush(c)) g.FillRectangle(b, x, ly + 2 * U, 26 * U, 15 * U);
            if (hatch) using (HatchBrush hb = new HatchBrush(HatchStyle.WideUpwardDiagonal, Col(0x5a2320), Color.Transparent)) g.FillRectangle(hb, x, ly + 2 * U, 26 * U, 15 * U);
            using (Pen pn = new Pen(Col(C_INK), 1f * U)) g.DrawRectangle(pn, x, ly + 2 * U, 26 * U, 15 * U);
            LegText(g, t, 36 * U);
        }

        void LegIcon(Graphics g, string kind, string faction, string t)
        {
            Icon(g, kind, faction, N + 35 * U, ly + 10 * U);
            float before = ly; LegText(g, t, 36 * U);
            if (kind == "capitale") ly = Math.Max(ly, before + 27 * U);
        }

        void LegLine(Graphics g, int kind, string t)
        {
            float x = N + 22 * U, y = ly + 10 * U;
            if (kind == 0) { using (Pen p = new Pen(Col(0x4a2f18), 4.4f * U)) g.DrawLine(p, x, y, x + 26 * U, y); using (Pen p = new Pen(Col(0xf2dfae), 1.9f * U)) g.DrawLine(p, x, y, x + 26 * U, y); }
            else if (kind == 1) { using (Pen p = new Pen(Col(0x4a2f18), 2.3f * U)) { p.DashPattern = new float[] { 3.2f, 1.8f }; g.DrawLine(p, x, y, x + 26 * U, y); } }
            else if (kind == 2) { using (Pen p = new Pen(Col(0x2a1e12), 7.5f * U)) g.DrawLine(p, x + 6 * U, y, x + 20 * U, y); using (Pen p = new Pen(Col(0xe8e2d4), 4.2f * U)) g.DrawLine(p, x + 6 * U, y, x + 20 * U, y); }
            else if (kind == 3) { using (Pen p = new Pen(Col(0x2f78c0), 3.5f * U)) g.DrawLine(p, x, y, x + 26 * U, y); }
            else { using (Pen p = new Pen(Col(0xff7a1a), 3.5f * U)) g.DrawLine(p, x, y, x + 26 * U, y); }
            LegText(g, t, 36 * U);
        }

        void LegSymbols(Graphics g)
        {
            LegHead(g, "Lieux et voies");
            LegIcon(g, "capitale", "lumiere", "Capitale de la Lumière");
            LegIcon(g, "capitale", "ombre", "Capitale de l'Ombre");
            LegIcon(g, "ville", "neutre", "Ville neutre");
            LegIcon(g, "village", "neutre", "Village ou relais (couleur = faction)");
            bool hamlet = false, crater = false;
            foreach (Place m in marks) { if (m.Kind == "hameau") hamlet = true; else if (m.Kind == "cratere") crater = true; }
            if (hamlet) LegIcon(g, "hameau", "neutre", "Hameau de départ (couleur = faction)");
            if (crater) LegIcon(g, "cratere", "neutre", "Champ de cratères : réveil des Marqués");
            LegIcon(g, "donjon", "neutre", "Donjon (anciens étages et nouveaux)");
            LegLine(g, 0, "Voie Royale, d'une capitale à l'autre");
            LegLine(g, 1, "Chemin");
            LegLine(g, 2, "Pont");
            LegLine(g, 3, "Fleuve");
            LegLine(g, 4, "Coulée de lave");
        }

        void LegEnd(Graphics g)
        {
            float x = N + 22 * U, y = N - 86 * U, w = (float)(1000 / Cell);
            using (SolidBrush ink = new SolidBrush(Col(C_INK))) using (Pen pn = new Pen(Col(C_INK), 2f * U)) using (Font f = F(13, FontStyle.Regular)) using (Font fb = F(17, FontStyle.Bold))
            {
                g.DrawLine(pn, x, y + 30 * U, x + w, y + 30 * U);
                g.DrawLine(pn, x, y + 23 * U, x, y + 37 * U); g.DrawLine(pn, x + w, y + 23 * U, x + w, y + 37 * U); g.DrawLine(pn, x + w / 2, y + 26 * U, x + w / 2, y + 34 * U);
                g.DrawString("1000 blocs (3 min de course)", f, ink, x, y + 42 * U);
                float cx = W - 50 * U, cy = y + 28 * U;
                PointF[] arrow = { new PointF(cx, cy - 34 * U), new PointF(cx + 10 * U, cy + 6 * U), new PointF(cx, cy - 2 * U), new PointF(cx - 10 * U, cy + 6 * U) };
                g.FillPolygon(ink, arrow);
                g.DrawString("N", fb, ink, cx - 9 * U, cy + 10 * U);
            }
        }

        void Save(Bitmap bmp, string path) { bmp.Save(path, ImageFormat.Png); bmp.Dispose(); }

        void RenderCarte(string path)
        {
            int[] buf = NewCanvas();
            Parallel.For(0, N, pz =>
            {
                for (int px = 0; px < N; px++)
                {
                    int p = pz * N + px, b = Bio[p], c;
                    if (b == B_OCEAN) c = OceanCol(p);
                    else if (b == B_EAU) c = Rgb(BioCol[B_EAU]);
                    else if (b == B_LAVE) c = Mul(Rgb(BioCol[B_LAVE]), 0.9 + 0.2 * Nz.N2(px / 3.0, pz / 3.0));
                    else
                    {
                        double tex = (b == B_FORET || b == B_SOMBRE || b == B_PINS) ? 0.94 + 0.12 * Nz.N2(px / 1.7, pz / 1.7) : 1;
                        c = Mul(Rgb(BioCol[b]), Sh[p] * tex);
                    }
                    buf[pz * W + px] = c;
                }
            });
            Bitmap bmp = ToBitmap(buf, W, N);
            using (Graphics g = Begin(bmp))
            {
                DrawStreams(g, 0x3f86c4, 1.0f);
                DrawRoads(g);
                DrawRegionLabels(g, 0);
                DrawPlaces(g, false, true);
                LegBegin(g, "1 · Carte du monde");
                LegHead(g, "Biomes");
                int[] order = { B_PLAINE, B_LANDE, B_FORET, B_SOMBRE, B_COLLINES, B_PINS, B_ALPAGE, B_ROCHE, B_NEIGE, B_SABLE, B_MESA, B_OASIS, B_MARAIS, B_CENDRES, B_VOLC, B_LAVE, B_PLAGE, B_EAU, B_OCEAN };
                float top = ly; float colW = (W - N - 40 * U) / 2;
                for (int i = 0; i < order.Length; i++)
                {
                    // deux colonnes
                    float x = N + 22 * U + (i % 2) * colW, y = top + (i / 2) * 22 * U;
                    using (SolidBrush b = new SolidBrush(Col(BioCol[order[i]]))) g.FillRectangle(b, x, y + 2 * U, 24 * U, 14 * U);
                    using (Pen pn = new Pen(Col(C_INK), 1f * U)) g.DrawRectangle(pn, x, y + 2 * U, 24 * U, 14 * U);
                    using (SolidBrush ink = new SolidBrush(Col(C_INK))) using (Font f = F(13, FontStyle.Regular)) g.DrawString(BioName[order[i]], f, ink, x + 30 * U, y);
                }
                ly = top + ((order.Length + 1) / 2) * 22 * U + 4 * U;
                LegSymbols(g);
                LegHead(g, "À savoir");
                LegText(g, "Deux capitales : Auréval (Lumière) à l'ouest, Morneval (Ombre) à l'est, chacune sur son fleuve près de la côte. Le niveau monte en s'éloignant d'elles.", 0);
                LegText(g, "Tous les noms sont provisoires.", 0);
                LegEnd(g);
            }
            Save(bmp, path);
        }

        static readonly double[] HypY = { 63, 70, 80, 95, 115, 140, 165, 190, 215 };
        static readonly int[] HypC = { 0x8fbf74, 0xa9cf83, 0xcfdd98, 0xe6d99a, 0xd9b77c, 0xb98f64, 0x9c8474, 0xbdb8b4, 0xffffff };
        static int Hyp(double h)
        {
            if (h <= HypY[0]) return Rgb(HypC[0]);
            for (int i = 1; i < HypY.Length; i++) if (h <= HypY[i]) return Mix(Rgb(HypC[i - 1]), Rgb(HypC[i]), (h - HypY[i - 1]) / (HypY[i] - HypY[i - 1]));
            return Rgb(HypC[HypC.Length - 1]);
        }

        void RenderRelief(string path)
        {
            int[] buf = NewCanvas();
            Parallel.For(0, N, pz =>
            {
                for (int px = 0; px < N; px++)
                {
                    int p = pz * N + px, c;
                    if (IsSea(p)) c = OceanCol(p);
                    else if (Lav[p] == 1) c = Rgb(0xff7a1a);
                    else if (IsWet(p)) c = Rgb(0x4f93c6);
                    else c = Mul(Hyp(H[p]), 0.2 + 0.8 * Sh[p] * Sh[p]);
                    buf[pz * W + px] = c;
                }
            });
            Bitmap bmp = ToBitmap(buf, W, N);
            using (Graphics g = Begin(bmp))
            {
                DrawStreams(g, 0x3f86c4, 0.9f);
                float tx = SX(P2W(topP % N)), ty = SX(P2W(topP / N));
                using (Pen pn = new Pen(Col(C_INK), 1.6f * U)) { g.DrawLine(pn, tx - 6 * U, ty, tx + 6 * U, ty); g.DrawLine(pn, tx, ty - 6 * U, tx, ty + 6 * U); }
                Label(g, "Sommet Y " + ((int)Math.Round(topH)), tx + 9 * U, ty - 10 * U, 13, FontStyle.Bold, Col(C_INK), Col(0xffffff, 220), StringAlignment.Near);
                foreach (Place pl in places) if (pl.Kind == "capitale" || pl.Kind == "ville") Icon(g, pl.Kind, pl.Faction, SX(pl.X), SX(pl.Z));
                LegBegin(g, "2 · Relief");
                LegHead(g, "Altitude (Y en jeu)");
                ly += 8 * U;
                float x = N + 22 * U, hgt = 300 * U, y0 = ly;
                for (int i = 0; i < (int)hgt; i++)
                {
                    double hh = 220 - (220 - 63) * (i / hgt);
                    using (Pen pn = new Pen(Color.FromArgb(Hyp(hh)))) g.DrawLine(pn, x, y0 + i, x + 34 * U, y0 + i);
                }
                for (int i = 0; i < (int)(46 * U); i++)
                {
                    using (Pen pn = new Pen(Color.FromArgb(Mix(Rgb(0x66a3cc), Rgb(0x1f3f68), i / (46.0 * U))))) g.DrawLine(pn, x, y0 + hgt + i, x + 34 * U, y0 + hgt + i);
                }
                using (Pen pn = new Pen(Col(C_INK), 1f * U)) g.DrawRectangle(pn, x, y0, 34 * U, hgt + 46 * U);
                using (SolidBrush ink = new SolidBrush(Col(C_INK))) using (Font f = F(13, FontStyle.Regular))
                {
                    int[] marks = { 220, 190, 160, 130, 100, 80, 63 };
                    string[] notes = { "sommets", "neige au-dessus de 166", "roche nue", "hautes collines", "collines, mesas", "plaines et forêts", "niveau de la mer" };
                    for (int i = 0; i < marks.Length; i++)
                    {
                        float yy = y0 + (float)((220 - marks[i]) / (220.0 - 63) * hgt);
                        g.DrawLine(Pens.Black, x + 34 * U, yy, x + 40 * U, yy);
                        g.DrawString("Y " + marks[i] + " · " + notes[i], f, ink, x + 44 * U, yy - 9 * U);
                    }
                    g.DrawString("fonds marins (jusqu'à Y 35)", f, ink, x + 44 * U, y0 + hgt + 14 * U);
                }
                ly = y0 + hgt + 46 * U + 14 * U;
                LegHead(g, "À savoir");
                LegText(g, "Le fichier hauteurs.png contient la même carte en niveaux de gris : la valeur du gris est directement la hauteur Y du sol. C'est lui que lira le générateur de terrain.", 0);
                LegText(g, "Les fleuves creusent leur vallée ; les lacs sont posés sur des replats. L'ombrage vient du nord-ouest.", 0);
                LegEnd(g);
            }
            Save(bmp, path);
        }

        int Flat(int p, int col)
        {
            if (IsSea(p)) return PaleSea(p);
            if (Lav[p] == 1) return Rgb(0xff8a3a);
            if (IsWet(p)) return Rgb(0x6aa6d6);
            return Mul(Rgb(col), 0.82 + 0.18 * Sh[p]);
        }

        int LvIndex(Region r)
        {
            List<int> mins = new List<int>();
            foreach (Region q in regions) if (!mins.Contains(q.LvMin)) mins.Add(q.LvMin);
            mins.Sort();
            return Math.Min(LvCol.Length - 1, mins.IndexOf(r.LvMin));
        }

        void RenderRegions(string path)
        {
            int[] buf = NewCanvas();
            Parallel.For(0, N, pz => { for (int px = 0; px < N; px++) { int p = pz * N + px; buf[pz * W + px] = Flat(p, RegCol[Reg[p] % RegCol.Length]); } });
            Borders(buf, 0.45);
            Bitmap bmp = ToBitmap(buf, W, N);
            using (Graphics g = Begin(bmp))
            {
                DrawStreams(g, 0x2f78c0, 1.0f);
                DrawRoads(g);
                DrawWaterNames(g);
                DrawRegionLabels(g, 3);
                DrawPlaces(g, false, true);
                LegBegin(g, "3 · Régions, fleuves, routes");
                LegHead(g, "Les " + regions.Count + " régions (et leur part des terres)");
                long land = 0; foreach (Region r in regions) land += r.Pixels;
                foreach (Region r in regions)
                {
                    string fac = r.Faction == "lumiere" ? "Lumière" : (r.Faction == "ombre" ? "Ombre" : "contesté");
                    LegSwatch(g, Col(RegCol[r.Index % RegCol.Length]), r.Name + " : " + StyleName[r.Style].ToLower() + ", " + fac + ", " + r.LvMin + "–" + r.LvMax
                        + " (" + (int)Math.Round(100.0 * r.Pixels / Math.Max(1, land)) + " %)", false);
                }
                LegSymbols(g);
                LegEnd(g);
            }
            Save(bmp, path);
        }

        void RenderNiveaux(string path)
        {
            int[] buf = NewCanvas();
            int[] idx = new int[regions.Count];
            foreach (Region r in regions) idx[r.Index] = LvIndex(r);
            Parallel.For(0, N, pz => { for (int px = 0; px < N; px++) { int p = pz * N + px; buf[pz * W + px] = Flat(p, LvCol[idx[Reg[p]]]); } });
            Borders(buf, 0.5);
            Bitmap bmp = ToBitmap(buf, W, N);
            using (Graphics g = Begin(bmp))
            {
                DrawStreams(g, 0x2f78c0, 0.8f);
                DrawRoads(g);
                DrawRegionLabels(g, 1);
                DrawPlaces(g, false, true);
                LegBegin(g, "4 · Zones par niveau");
                LegHead(g, "Tranches de niveau (1 à 80)");
                for (int t = 0; t < LvCol.Length; t++)
                {
                    string names = ""; int a = 0, b = 0;
                    foreach (Region r in regions) if (idx[r.Index] == t) { names += (names.Length > 0 ? ", " : "") + r.Name; a = r.LvMin; b = r.LvMax; }
                    if (names.Length == 0) continue;
                    LegSwatch(g, Col(LvCol[t]), "Niv. " + a + " à " + b + " (palier " + LvTier[t] + ") : " + names, false);
                }
                LegHead(g, "À savoir");
                LegText(g, "Chaque tranche correspond à un palier d'équipement du jeu. Chaque faction a ses trois premières zones (1 à 26) de son côté ; à partir du niveau 26 tout le monde se retrouve dans les zones du centre, du sud et du nord.", 0);
                LegText(g, "Le chemin d'un joueur : capitale, forêt, puis collines ou marais, La Marche, le désert, les cimes, les terres de cendre et enfin le volcan.", 0);
                LegText(g, "Niveaux 80 à 90 : pas de nouvelle région. Des Brèches s'ouvrent dans les zones contestées (La Marche, le désert, les cimes, les cendres, le volcan).", 0);
                LegSymbols(g);
                LegEnd(g);
            }
            Save(bmp, path);
        }

        void RenderFactions(string path)
        {
            int[] buf = NewCanvas();
            Parallel.For(0, N, pz =>
            {
                for (int px = 0; px < N; px++)
                {
                    int p = pz * N + px; string f = regions[Reg[p]].Faction; int col;
                    if (f == "lumiere") col = 0xf0d27a; else if (f == "ombre") col = 0xa98bd8;
                    else col = ((px + pz) / (int)Math.Max(3, 7 * U)) % 2 == 0 ? 0xd98c80 : 0xc66e62;
                    buf[pz * W + px] = Flat(p, col);
                }
            });
            Borders(buf, 0.5);
            Bitmap bmp = ToBitmap(buf, W, N);
            using (Graphics g = Begin(bmp))
            {
                DrawStreams(g, 0x2f78c0, 0.8f);
                DrawRoads(g);
                DrawPlaces(g, true, true);
                DrawRegionLabels(g, 2);
                LegBegin(g, "5 · Territoires et PvP");
                LegHead(g, "Territoires");
                LegSwatch(g, Col(0xf0d27a), "Terres de la Lumière : Auréval, ses villages, ses gardes", false);
                LegSwatch(g, Col(0xa98bd8), "Terres de l'Ombre : Morneval, ses villages, ses gardes", false);
                LegSwatch(g, Col(0xd98c80), "Zones contestées : aucune faction n'y est chez elle", true);
                LegSwatch(g, Col(0x8fe3a8), "Zone sûre (cercle vert) : aucun combat entre joueurs", false);
                LegHead(g, "Règle du PvP");
                LegText(g, "Le PvP entre Lumière et Ombre est actif partout hors des zones sûres, y compris sur les terres d'une faction : un raid chez l'ennemi est possible, mais ses gardes et ses villages sont contre toi.", 0);
                LegText(g, "Zones sûres : 280 blocs autour de chaque capitale, 120 autour des Quatre-Vents (ville neutre, trêve), 70 autour des villages et relais, 60 autour des hameaux de départ.", 0);
                LegText(g, "En jeu aujourd'hui, toute zone sûre vaut pour les deux factions. Proposé, pas encore décidé : un village de faction ne serait sûr que pour sa faction.", 0);
                LegSymbols(g);
                LegEnd(g);
            }
            Save(bmp, path);
        }

        /** Carte des sites : tout ce qui a ete construit entre les villes (camps, ruines, lieux-dits). */
        void RenderSites(string path)
        {
            int[] buf = NewCanvas();
            Parallel.For(0, N, pz => { for (int px = 0; px < N; px++) { int p = pz * N + px; buf[pz * W + px] = Flat(p, RegCol[Reg[p] % RegCol.Length]); } });
            Borders(buf, 0.45);
            Bitmap bmp = ToBitmap(buf, W, N);
            using (Graphics g = Begin(bmp))
            {
                DrawStreams(g, 0x2f78c0, 0.8f);
                DrawRoads(g);
                DrawMarks(g);
                DrawPlaces(g, false, true);
                LegBegin(g, "6 · Camps, ruines, lieux-dits");
                LegHead(g, "Par région : camps, ruines, lieux-dits");
                foreach (Region r in regions)
                {
                    int[] n = MarkCount(r.Index);
                    LegSwatch(g, Col(RegCol[r.Index % RegCol.Length]), r.Name + " (niv. " + r.LvMin + "–" + r.LvMax + ") : " + n[0] + ", " + n[1] + ", " + n[2], false);
                }
                int[] all = MarkCount(-1);
                LegHead(g, "Sites");
                LegIcon(g, "camp", "neutre", "Camp de monstres (" + all[0] + ") : des gardiens, un thème par région");
                LegIcon(g, "ruine", "neutre", "Ruine hantée (" + all[1] + ")");
                LegIcon(g, "decor", "neutre", "Lieu-dit sans monstre (" + all[2] + ") : tour de guet, moulin, ferme, statue, puits…");
                LegSymbols(g);
                LegHead(g, "À savoir");
                LegText(g, "Le niveau des monstres monte avec la distance au lieu sûr le plus proche. Rien n'apparaît dans les zones sûres ni sur les routes.", 0);
                LegText(g, "Le paysage posé ensuite (petits lieux, lacs, arbres) n'est pas sur cette carte : il est calculé par le plugin.", 0);
                LegEnd(g);
            }
            Save(bmp, path);
        }

        void SaveRaw(string path)
        {
            int[] buf = new int[N * N];
            for (int p = 0; p < buf.Length; p++) { int v = B8(Math.Round(H[p])); buf[p] = unchecked((int)0xFF000000) | (v << 16) | (v << 8) | v; }
            Save(ToBitmap(buf, N, N), path);
        }

        void RenderAll(string dir)
        {
            U = N / 1600f; LEG = (int)(450 * U); W = N + LEG;
            try { using (FontFamily ff = new FontFamily("Palatino Linotype")) fontName = ff.Name; } catch (Exception) { fontName = "Georgia"; }
            Hillshade();
            SaveRaw(Path.Combine(dir, "hauteurs.png"));
            RenderCarte(Path.Combine(dir, "1-carte.png"));
            RenderRelief(Path.Combine(dir, "2-relief.png"));
            RenderRegions(Path.Combine(dir, "3-regions.png"));
            RenderNiveaux(Path.Combine(dir, "4-niveaux.png"));
            RenderFactions(Path.Combine(dir, "5-factions.png"));
            if (marks.Count > 0) RenderSites(Path.Combine(dir, "6-sites.png"));
        }

        /** Cartes PNG + bilan dans outDir ; si dataPath est donne, ecrit aussi les donnees du generateur a la finesse dataPixels. */
        public static string Run(string design, string outDir, int pixels, string dataPath, int dataPixels)
        {
            Carto c = new Carto();
            c.Parse(design);
            c.N = pixels; c.Half = c.Size / 2; c.Cell = c.Size / pixels;
            Nz.Init(c.Seed);
            c.Generate();
            c.RenderAll(outDir);
            string log = c.Log.ToString();
            if (!string.IsNullOrEmpty(dataPath))
            {
                c = null; GC.Collect();
                Carto d = new Carto();
                d.Parse(design);
                d.N = dataPixels; d.Half = d.Size / 2; d.Cell = d.Size / dataPixels;
                Nz.Init(d.Seed);
                d.Generate();
                d.WriteData(dataPath);
                log = d.Log.ToString() + Environment.NewLine + "Donnees du generateur : " + dataPath + " (" + dataPixels + " px de cote, "
                    + (new FileInfo(dataPath).Length / 1048576) + " Mo)." + Environment.NewLine;
            }
            File.WriteAllText(Path.Combine(outDir, "bilan.txt"), log, new UTF8Encoding(false));
            return log;
        }
    }
}
