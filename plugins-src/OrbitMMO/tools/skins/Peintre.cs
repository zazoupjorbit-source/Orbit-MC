// Peintre des textures d'Orbit MMO : skins des PNJ (64 x 64), tenues completes des monstres humanoides
// (64 x 32, portees comme une armure), variantes d'animaux et betes du jeu recolorees.
// Compile a la volee par tools\gen-skins.ps1 (C# 5 : pas de syntaxe plus recente). Voir docs\textures-ambiance.md.
using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Imaging;
using System.IO;
using System.IO.Compression;
using System.Text;

namespace OrbitMmo
{
    public sealed class Img
    {
        public readonly int W, H;
        public readonly int[] P;

        public Img(int w, int h) { W = w; H = h; P = new int[w * h]; }

        public int Get(int x, int y) { return (x < 0 || y < 0 || x >= W || y >= H) ? 0 : P[y * W + x]; }

        public void Set(int x, int y, int argb) { if (x >= 0 && y >= 0 && x < W && y < H) P[y * W + x] = argb; }

        public static Img Load(Stream s)
        {
            using (Bitmap b = new Bitmap(s))
            {
                Img i = new Img(b.Width, b.Height);
                for (int y = 0; y < b.Height; y++)
                    for (int x = 0; x < b.Width; x++)
                        i.P[y * b.Width + x] = b.GetPixel(x, y).ToArgb();
                return i;
            }
        }

        public Bitmap ToBitmap()
        {
            Bitmap b = new Bitmap(W, H, PixelFormat.Format32bppArgb);
            for (int y = 0; y < H; y++)
                for (int x = 0; x < W; x++)
                    b.SetPixel(x, y, Color.FromArgb(P[y * W + x]));
            return b;
        }

        public void Save(string path)
        {
            Directory.CreateDirectory(Path.GetDirectoryName(path));
            using (Bitmap b = ToBitmap()) b.Save(path, ImageFormat.Png);
        }
    }

    /// <summary>Hasard reproductible : la meme graine donne toujours le meme dessin.</summary>
    public sealed class Rng
    {
        ulong s;

        public Rng(string seed)
        {
            s = 1469598103934665603UL;
            foreach (char ch in seed) { s ^= ch; s *= 1099511628211UL; }
            if (s == 0) s = 1;
            for (int i = 0; i < 4; i++) Next(2);
        }

        public int Next(int n)
        {
            s ^= s << 13; s ^= s >> 7; s ^= s << 17;
            return (int)((s >> 11) % (ulong)n);
        }

        public double Dbl() { return Next(1000000) / 1000000.0; }

        public T Pick<T>(T[] a) { return a[Next(a.Length)]; }
    }

    public static class K
    {
        public static int Rgb(string hex) { return unchecked((int)0xFF000000) | Convert.ToInt32(hex, 16); }

        public static int Mul(int c, double f)
        {
            int r = (int)Math.Round(((c >> 16) & 255) * f), g = (int)Math.Round(((c >> 8) & 255) * f), b = (int)Math.Round((c & 255) * f);
            return (c & unchecked((int)0xFF000000)) | (Clamp(r) << 16) | (Clamp(g) << 8) | Clamp(b);
        }

        public static int Mix(int a, int b, double t)
        {
            if (t < 0) t = 0;
            if (t > 1) t = 1;
            int r = (int)Math.Round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
            int g = (int)Math.Round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
            int bl = (int)Math.Round((a & 255) + ((b & 255) - (a & 255)) * t);
            return unchecked((int)0xFF000000) | (Clamp(r) << 16) | (Clamp(g) << 8) | Clamp(bl);
        }

        public static int Clamp(int v) { return v < 0 ? 0 : (v > 255 ? 255 : v); }

        public static double Hash(int x, int y, int seed)
        {
            unchecked
            {
                uint h = (uint)(x * 374761393 + y * 668265263 + seed * 1274126177);
                h = (h ^ (h >> 13)) * 1274126177u;
                h ^= h >> 16;
                return (h & 0xFFFFFF) / (double)0x1000000;
            }
        }

        public static int Ramp(int[] ramp, double t)
        {
            if (t <= 0) return ramp[0];
            if (t >= 1) return ramp[ramp.Length - 1];
            double p = t * (ramp.Length - 1);
            int i = (int)Math.Floor(p);
            return Mix(ramp[i], ramp[i + 1], p - i);
        }
    }

    /// <summary>Couleur + motif (ecailles, bandelettes, ecorce, braises, metal, fourrure).</summary>
    public struct Brush
    {
        public int Color, Alt, Pat;
        public const int Plat = 0, Ecailles = 1, Bandes = 2, Ecorce = 3, Braises = 4, Metal = 5, Fourrure = 6, Etoiles = 7;

        public static Brush Of(string hex) { Brush b; b.Color = K.Rgb(hex); b.Alt = b.Color; b.Pat = Plat; return b; }

        public static Brush Of(string hex, int pat, string alt) { Brush b; b.Color = K.Rgb(hex); b.Alt = K.Rgb(alt); b.Pat = pat; return b; }

        public static Brush Of(int color) { Brush b; b.Color = color; b.Alt = color; b.Pat = Plat; return b; }
    }

    /// <summary>Boite d'un modele du jeu deroulee dans la texture : dessus, dessous, droite, face, gauche, dos.</summary>
    public struct Box
    {
        public int U, V, W, H, D;

        public Box(int u, int v, int w, int h, int d) { U = u; V = v; W = w; H = h; D = d; }

        public void Face(char f, out int x, out int y, out int w, out int h)
        {
            switch (f)
            {
                case 'T': x = U + D; y = V; w = W; h = D; break;
                case 'B': x = U + D + W; y = V; w = W; h = D; break;
                case 'R': x = U; y = V + D; w = D; h = H; break;
                case 'F': x = U + D; y = V + D; w = W; h = H; break;
                case 'L': x = U + D + W; y = V + D; w = D; h = H; break;
                default: x = U + D + W + D; y = V + D; w = W; h = H; break;
            }
        }
    }

    /// <summary>Une texture de personnage en cours de dessin : skin de joueur (64 x 64) ou tenue portee (64 x 32).</summary>
    public sealed class Skin
    {
        public readonly Img I;
        public readonly bool Armor;
        public readonly int Seed;
        readonly Dictionary<string, Box> parts = new Dictionary<string, Box>();
        readonly HashSet<string> left = new HashSet<string>();
        public static readonly char[] Sides = { 'R', 'F', 'L', 'K' };

        public Skin(bool armor, int seed)
        {
            Armor = armor;
            Seed = seed;
            I = new Img(64, armor ? 32 : 64);
            parts["head"] = new Box(0, 0, 8, 8, 8);
            parts["hat"] = new Box(32, 0, 8, 8, 8);
            parts["body"] = new Box(16, 16, 8, 12, 4);
            parts["rarm"] = new Box(40, 16, 4, 12, 4);
            parts["rleg"] = new Box(0, 16, 4, 12, 4);
            if (armor)
            {
                // Une tenue n'a qu'une couche, et le bras et la jambe gauches sont le miroir des droits.
                parts["jacket"] = parts["body"];
                parts["rsleeve"] = parts["rarm"]; parts["larm"] = parts["rarm"]; parts["lsleeve"] = parts["rarm"];
                parts["rpants"] = parts["rleg"]; parts["lleg"] = parts["rleg"]; parts["lpants"] = parts["rleg"];
            }
            else
            {
                parts["jacket"] = new Box(16, 32, 8, 12, 4);
                parts["rsleeve"] = new Box(40, 32, 4, 12, 4);
                parts["rpants"] = new Box(0, 32, 4, 12, 4);
                parts["lleg"] = new Box(16, 48, 4, 12, 4);
                parts["lpants"] = new Box(0, 48, 4, 12, 4);
                parts["larm"] = new Box(32, 48, 4, 12, 4);
                parts["lsleeve"] = new Box(48, 48, 4, 12, 4);
                left.Add("lleg"); left.Add("lpants"); left.Add("larm"); left.Add("lsleeve");
            }
        }

        public Box Part(string part) { return parts[part]; }

        static double Light(char f)
        {
            switch (f)
            {
                case 'T': return 1.10;
                case 'F': return 1.0;
                case 'K': return 0.84;
                case 'B': return 0.70;
                default: return 0.90;
            }
        }

        int Tone(Brush b, char f, int x, int y, int gx, int gy)
        {
            int c = b.Color;
            double m = 1;
            switch (b.Pat)
            {
                case Brush.Ecailles:
                    m = ((x + y / 2) % 2 == 0) ? 0.86 : 1.05;
                    if (y % 3 == 2) c = K.Mix(c, b.Alt, 0.35);
                    break;
                case Brush.Bandes:
                    int r = (y + ((x / 3 + f) % 2)) % 3;
                    if (r == 2) { c = b.Alt; } else { m = r == 0 ? 1.06 : 0.94; }
                    break;
                case Brush.Ecorce:
                    m = (gx % 2 == 0 ? 0.80 : 1.0) * (0.90 + 0.22 * K.Hash(gx, gy / 3, Seed));
                    if (K.Hash(gx / 2, gy / 2, Seed + 7) < 0.14) c = K.Mix(c, b.Alt, 0.75);
                    break;
                case Brush.Braises:
                    if (K.Hash(gx, gy, Seed + 3) < 0.075) { c = b.Alt; m = 1 / Light(f); }
                    else m = 0.78 + 0.44 * K.Hash(gx / 2, gy / 2, Seed);
                    break;
                case Brush.Metal:
                    m = 1.14 - 0.03 * y;
                    if (x == 1) m *= 1.12;
                    break;
                case Brush.Fourrure:
                    m = 0.86 + 0.28 * K.Hash(gx, gy, Seed);
                    if (K.Hash(gx, gy / 2, Seed + 5) < 0.10) c = K.Mix(c, b.Alt, 0.6);
                    break;
                case Brush.Etoiles:
                    if (K.Hash(gx, gy, Seed + 9) < 0.06) { c = b.Alt; m = 1 / Light(f); }
                    break;
            }
            m *= Light(f) * (0.96 + 0.08 * K.Hash(gx, gy, Seed + 1));
            return K.Mul(c, m);
        }

        /// <summary>Pixel d'une face ; x = 0 est toujours le bord exterieur pour un bras ou une jambe.</summary>
        public void Px(string part, char f, int x, int y, Brush b) { Put(part, f, x, y, b, true); }

        /// <summary>Pixel sans ombrage (yeux, lueurs).</summary>
        public void Raw(string part, char f, int x, int y, int color) { Put(part, f, x, y, Brush.Of(color), false); }

        void Put(string part, char f, int x, int y, Brush b, bool shade)
        {
            int fx, fy, fw, fh;
            parts[part].Face(f, out fx, out fy, out fw, out fh);
            if (x < 0 || y < 0 || x >= fw || y >= fh) return;
            if (left.Contains(part) && (f == 'F' || f == 'K' || f == 'T' || f == 'B')) x = fw - 1 - x;
            int gx = fx + x, gy = fy + y;
            I.Set(gx, gy, shade ? Tone(b, f, x, y, gx, gy) : b.Color);
        }

        public void Rect(string part, char f, int x0, int y0, int x1, int y1, Brush b)
        {
            for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) Px(part, f, x, y, b);
        }

        public void FaceFill(string part, char f, Brush b)
        {
            int fx, fy, fw, fh;
            parts[part].Face(f, out fx, out fy, out fw, out fh);
            Rect(part, f, 0, 0, fw - 1, fh - 1, b);
        }

        public void Fill(string part, Brush b)
        {
            foreach (char f in new char[] { 'T', 'B', 'R', 'F', 'L', 'K' }) FaceFill(part, f, b);
        }

        /// <summary>Bandes horizontales sur les quatre cotes (0 = haut de la piece).</summary>
        public void Rows(string part, int y0, int y1, Brush b)
        {
            foreach (char f in Sides)
            {
                int fx, fy, fw, fh;
                parts[part].Face(f, out fx, out fy, out fw, out fh);
                Rect(part, f, 0, y0, fw - 1, Math.Min(y1, fh - 1), b);
            }
        }

        public void Both(string a, string b2, Action<string> paint) { paint(a); if (!Armor) paint(b2); }
    }

    /// <summary>Description d'un personnage : le peintre en tire un skin ou une tenue.</summary>
    public sealed class Look
    {
        public string Id;
        public string Label;
        public bool Armor;
        public Brush SkinB = Brush.Of("D9A878");
        public int Hair = K.Rgb("5A3A1E"), Eye = K.Rgb("3E2A1A"), Glow = 0;
        public int HairStyle = 1, Beard = 0, Sleeves = 0;
        public bool Female, Child, Pack, Cape, Tabard, Barefoot;
        public string Garb = "tunique", Face = "humain", Head = "none", Emblem = "none";
        public Brush Top = Brush.Of("8C6A46"), Trim = Brush.Of("E8DCC0"), Pants = Brush.Of("5A4630"), Boots = Brush.Of("2E2620"),
            Belt = Brush.Of("3A2A1C"), TabardB = Brush.Of("F0ECE0"), HeadB = Brush.Of("8A93A0"), CapeB = Brush.Of("B8483E");
        public int EmblemColor = K.Rgb("E8C050"), Mark = 0;
    }

    public static class Peintre
    {
        const string L_HEAD = "head", L_HAT = "hat", L_BODY = "body", L_JACKET = "jacket";
        static readonly int White = K.Rgb("F4F4F4"), Dark = K.Rgb("14100C"), Gold = K.Rgb("E0B84A");

        // ------------------------------------------------------------------ Dessin d'un personnage

        public static Skin Paint(Look k)
        {
            Rng seed = new Rng(k.Id);
            Skin s = new Skin(k.Armor, seed.Next(100000));
            string[] limbs = { "rarm", "larm", "rleg", "lleg" };
            s.Fill(L_HEAD, k.SkinB);
            s.Fill(L_BODY, k.SkinB);
            foreach (string l in limbs) s.Fill(l, k.SkinB);
            Legs(s, k);
            Body(s, k);
            Arms(s, k);
            FaceOf(s, k);
            HairOf(s, k);
            HeadGear(s, k);
            Extras(s, k);
            return s;
        }

        static void Legs(Skin s, Look k)
        {
            foreach (string leg in new string[] { "rleg", "lleg" })
            {
                if (k.Garb == "peau")
                {
                    s.Rows(leg, 0, 3, k.Pants);
                }
                else if (k.Garb == "robe")
                {
                    s.Rows(leg, 0, 9, k.Top);
                    s.Rows(leg, 10, 10, k.Trim);
                    s.Rows(leg, 11, 11, k.Boots);
                    s.FaceFill(leg, 'B', k.Boots);
                }
                else if (k.Garb == "haillons")
                {
                    s.Rows(leg, 0, 6, k.Pants);
                    for (int x = 0; x < 4; x++)
                        foreach (char f in Skin.Sides)
                            if (K.Hash(x + f, leg.Length, s.Seed) < 0.5) s.Px(leg, f, x, 7, k.Pants);
                }
                else
                {
                    s.FaceFill(leg, 'T', k.Pants);
                    s.Rows(leg, 0, 8, k.Pants);
                    if (k.Garb == "plates")
                    {
                        s.Rect(leg, 'F', 0, 4, 3, 5, Brush.Of(K.Mul(k.Pants.Color, 1.18)));
                    }
                    if (!k.Barefoot)
                    {
                        s.Rows(leg, 9, 11, k.Boots);
                        s.FaceFill(leg, 'B', k.Boots);
                    }
                }
            }
        }

        static void Body(Skin s, Look k)
        {
            Brush dark = Brush.Of(K.Mul(k.Top.Color, 0.72));
            switch (k.Garb)
            {
                case "peau":
                    s.Rows(L_BODY, 10, 11, k.Belt);
                    break;
                case "haillons":
                    s.Rows(L_BODY, 0, 9, k.Top);
                    for (int x = 0; x < 8; x++)
                    {
                        if (K.Hash(x, 1, s.Seed) < 0.6) { s.Px(L_BODY, 'F', x, 10, k.Top); s.Px(L_BODY, 'K', x, 10, k.Top); }
                        if (K.Hash(x, 2, s.Seed) < 0.3) { s.Px(L_BODY, 'F', x, 11, k.Top); s.Px(L_BODY, 'K', x, 11, k.Top); }
                    }
                    for (int i = 0; i < 5; i++)
                    {
                        s.Px(L_BODY, i % 2 == 0 ? 'F' : 'K', 1 + (int)(K.Hash(i, 3, s.Seed) * 6), 2 + (int)(K.Hash(i, 4, s.Seed) * 6), k.SkinB);
                    }
                    break;
                case "robe":
                    s.Fill(L_BODY, k.Top);
                    s.Rect(L_BODY, 'F', 3, 0, 4, 11, k.Trim);
                    s.Rows(L_BODY, 8, 8, k.Belt);
                    s.Px(L_BODY, 'F', 3, 0, k.SkinB); s.Px(L_BODY, 'F', 4, 0, k.SkinB);
                    break;
                case "manteau":
                    s.Fill(L_BODY, k.Top);
                    s.Rect(L_BODY, 'F', 3, 0, 4, 11, k.Trim);
                    s.Rows(L_BODY, 0, 0, Brush.Of(K.Mul(k.Top.Color, 1.2)));
                    s.Px(L_BODY, 'F', 3, 0, k.SkinB); s.Px(L_BODY, 'F', 4, 0, k.SkinB);
                    s.Px(L_BODY, 'F', 2, 3, Brush.Of(Gold)); s.Px(L_BODY, 'F', 2, 6, Brush.Of(Gold));
                    s.Rows(L_BODY, 9, 9, k.Belt);
                    break;
                case "plates":
                    s.Fill(L_BODY, k.Top);
                    s.Rows(L_BODY, 0, 0, Brush.Of(K.Mul(k.Top.Color, 0.8)));
                    s.Rect(L_BODY, 'F', 1, 1, 6, 1, Brush.Of(K.Mul(k.Top.Color, 1.2)));
                    s.Rows(L_BODY, 6, 6, Brush.Of(K.Mul(k.Top.Color, 0.7)));
                    s.Rows(L_BODY, 9, 9, k.Belt);
                    s.Rect(L_BODY, 'F', 3, 9, 4, 9, Brush.Of(Gold));
                    break;
                case "cuir":
                    s.Fill(L_BODY, k.Top);
                    for (int i = 1; i <= 6; i++) s.Px(L_BODY, 'F', i, i, dark);
                    s.Px(L_BODY, 'F', 3, 0, k.SkinB); s.Px(L_BODY, 'F', 4, 0, k.SkinB);
                    s.Rows(L_BODY, 9, 9, k.Belt);
                    s.Rect(L_BODY, 'F', 3, 9, 4, 9, Brush.Of(Gold));
                    s.Rows(L_BODY, 11, 11, dark);
                    break;
                default: // tunique, tablier
                    s.Fill(L_BODY, k.Top);
                    s.Px(L_BODY, 'F', 3, 0, k.SkinB); s.Px(L_BODY, 'F', 4, 0, k.SkinB);
                    s.Px(L_BODY, 'F', 3, 1, Brush.Of(K.Mul(k.Top.Color, 0.8))); s.Px(L_BODY, 'F', 4, 1, Brush.Of(K.Mul(k.Top.Color, 0.8)));
                    s.Rows(L_BODY, 9, 9, k.Belt);
                    s.Rect(L_BODY, 'F', 3, 9, 4, 9, Brush.Of(Gold));
                    s.Rows(L_BODY, 11, 11, k.Trim);
                    if (k.Garb == "tablier")
                    {
                        Brush ap = k.Trim;
                        s.Rect(L_BODY, 'F', 1, 2, 6, 11, ap);
                        s.Px(L_BODY, 'F', 1, 0, ap); s.Px(L_BODY, 'F', 1, 1, ap); s.Px(L_BODY, 'F', 6, 0, ap); s.Px(L_BODY, 'F', 6, 1, ap);
                        s.Rect(L_BODY, 'F', 3, 6, 4, 7, Brush.Of(K.Mul(ap.Color, 0.75)));
                        s.Rows(L_BODY, 9, 9, k.Belt);
                        foreach (string leg in new string[] { "rpants", "lpants" })
                        {
                            s.Rect(leg, 'F', 0, 0, 3, 5, ap);
                        }
                    }
                    break;
            }
        }

        static void Arms(Skin s, Look k)
        {
            foreach (string arm in new string[] { "rarm", "larm" })
            {
                string sleeve = arm == "rarm" ? "rsleeve" : "lsleeve";
                if (k.Garb == "peau") continue;
                if (k.Garb == "haillons")
                {
                    int len = 3 + (int)(K.Hash(arm.Length, 9, s.Seed) * 4);
                    s.FaceFill(arm, 'T', k.Top);
                    s.Rows(arm, 0, len, k.Top);
                    continue;
                }
                if (k.Garb == "plates")
                {
                    s.FaceFill(arm, 'T', k.Top);
                    s.Rows(arm, 0, 9, k.Trim);
                    s.Rows(arm, 10, 11, k.Belt);
                    s.FaceFill(arm, 'B', k.Belt);
                    s.FaceFill(sleeve, 'T', Brush.Of(K.Mul(k.Top.Color, 1.1)));
                    s.Rows(sleeve, 0, 3, k.Top);
                    s.Rows(sleeve, 3, 3, Brush.Of(K.Mul(k.Top.Color, 0.75)));
                    continue;
                }
                Brush sl = k.Garb == "cuir" ? k.Trim : k.Top;
                int end = k.Sleeves == 0 ? 9 : (k.Sleeves == 1 ? 4 : -1);
                if (k.Garb == "robe") end = 10;
                if (end >= 0)
                {
                    s.FaceFill(arm, 'T', sl);
                    s.Rows(arm, 0, end, sl);
                    if (k.Garb == "robe" || k.Garb == "manteau") s.Rows(arm, end, end, k.Trim);
                    if (k.Garb == "cuir") s.Rows(arm, 7, 9, k.Top);
                }
            }
        }

        static void FaceOf(Skin s, Look k)
        {
            int skin = k.SkinB.Color;
            Brush shade = Brush.Of(K.Mul(skin, 0.86)), deep = Brush.Of(K.Mul(skin, 0.68));
            int eye = k.Glow != 0 ? k.Glow : k.Eye;
            int white = k.Glow != 0 ? K.Mul(k.Glow, 0.6) : White;
            switch (k.Face)
            {
                case "reptile":
                    s.Rect(L_HEAD, 'F', 2, 4, 5, 6, Brush.Of(K.Mul(skin, 1.16)));
                    s.Raw(L_HEAD, 'F', 1, 3, K.Rgb("FFD23E")); s.Raw(L_HEAD, 'F', 2, 3, Dark);
                    s.Raw(L_HEAD, 'F', 6, 3, K.Rgb("FFD23E")); s.Raw(L_HEAD, 'F', 5, 3, Dark);
                    s.Rect(L_HEAD, 'F', 1, 2, 2, 2, deep); s.Rect(L_HEAD, 'F', 5, 2, 6, 2, deep);
                    s.Raw(L_HEAD, 'F', 3, 4, Dark); s.Raw(L_HEAD, 'F', 4, 4, Dark);
                    s.Rect(L_HEAD, 'F', 1, 6, 6, 6, Brush.Of(Dark));
                    s.Raw(L_HEAD, 'F', 2, 6, White); s.Raw(L_HEAD, 'F', 5, 6, White);
                    s.Rect(L_HEAD, 'F', 0, 7, 7, 7, shade);
                    break;
                case "bandes":
                    s.Rect(L_HEAD, 'F', 1, 4, 6, 4, Brush.Of(Dark));
                    s.Raw(L_HEAD, 'F', 2, 4, eye); s.Raw(L_HEAD, 'F', 5, 4, eye);
                    break;
                case "creux":
                    s.Rect(L_HEAD, 'F', 1, 3, 2, 4, Brush.Of(Dark)); s.Rect(L_HEAD, 'F', 5, 3, 6, 4, Brush.Of(Dark));
                    if (k.Glow != 0) { s.Raw(L_HEAD, 'F', 2, 4, k.Glow); s.Raw(L_HEAD, 'F', 5, 4, k.Glow); }
                    s.Px(L_HEAD, 'F', 3, 5, deep); s.Px(L_HEAD, 'F', 4, 5, deep);
                    s.Rect(L_HEAD, 'F', 2, 6, 5, 6, Brush.Of(Dark));
                    s.Raw(L_HEAD, 'F', 3, 6, K.Mul(White, 0.8));
                    break;
                case "or":
                    s.Fill(L_HEAD, k.HeadB);
                    s.Raw(L_HEAD, 'F', 1, 4, K.Rgb("1E2A6A")); s.Raw(L_HEAD, 'F', 2, 4, White);
                    s.Raw(L_HEAD, 'F', 5, 4, White); s.Raw(L_HEAD, 'F', 6, 4, K.Rgb("1E2A6A"));
                    s.Raw(L_HEAD, 'F', 0, 4, K.Rgb("1E2A6A")); s.Raw(L_HEAD, 'F', 7, 4, K.Rgb("1E2A6A"));
                    s.Rect(L_HEAD, 'F', 1, 3, 2, 3, Brush.Of(K.Mul(k.HeadB.Color, 0.7))); s.Rect(L_HEAD, 'F', 5, 3, 6, 3, Brush.Of(K.Mul(k.HeadB.Color, 0.7)));
                    s.Px(L_HEAD, 'F', 3, 5, Brush.Of(K.Mul(k.HeadB.Color, 0.8))); s.Px(L_HEAD, 'F', 4, 5, Brush.Of(K.Mul(k.HeadB.Color, 0.8)));
                    s.Rect(L_HEAD, 'F', 3, 6, 4, 6, Brush.Of(K.Mul(k.HeadB.Color, 0.6)));
                    s.Rect(L_HEAD, 'F', 3, 7, 4, 7, Brush.Of("1E2A6A"));
                    break;
                case "ecorce":
                    s.Rect(L_HEAD, 'F', 1, 3, 2, 4, Brush.Of(Dark)); s.Rect(L_HEAD, 'F', 5, 3, 6, 4, Brush.Of(Dark));
                    s.Raw(L_HEAD, 'F', 2, 4, eye); s.Raw(L_HEAD, 'F', 5, 4, eye); s.Raw(L_HEAD, 'F', 2, 3, K.Mul(eye, 0.7)); s.Raw(L_HEAD, 'F', 5, 3, K.Mul(eye, 0.7));
                    s.Raw(L_HEAD, 'F', 2, 6, Dark); s.Raw(L_HEAD, 'F', 3, 7, Dark); s.Raw(L_HEAD, 'F', 4, 6, Dark); s.Raw(L_HEAD, 'F', 5, 6, Dark);
                    break;
                case "heaume":
                    break;
                default:
                    if (k.Child)
                    {
                        s.Raw(L_HEAD, 'F', 1, 3, White); s.Raw(L_HEAD, 'F', 1, 4, White); s.Raw(L_HEAD, 'F', 2, 3, eye); s.Raw(L_HEAD, 'F', 2, 4, eye);
                        s.Raw(L_HEAD, 'F', 6, 3, White); s.Raw(L_HEAD, 'F', 6, 4, White); s.Raw(L_HEAD, 'F', 5, 3, eye); s.Raw(L_HEAD, 'F', 5, 4, eye);
                        s.Raw(L_HEAD, 'F', 1, 5, K.Mix(skin, K.Rgb("E88A8A"), 0.35)); s.Raw(L_HEAD, 'F', 6, 5, K.Mix(skin, K.Rgb("E88A8A"), 0.35));
                        s.Px(L_HEAD, 'F', 3, 6, shade); s.Px(L_HEAD, 'F', 4, 6, shade);
                        break;
                    }
                    Brush brow = Brush.Of(K.Mix(skin, k.Hair, k.Female ? 0.4 : 0.65));
                    s.Px(L_HEAD, 'F', 1, 3, brow); s.Px(L_HEAD, 'F', 2, 3, brow); s.Px(L_HEAD, 'F', 5, 3, brow); s.Px(L_HEAD, 'F', 6, 3, brow);
                    s.Raw(L_HEAD, 'F', 1, 4, white); s.Raw(L_HEAD, 'F', 2, 4, eye); s.Raw(L_HEAD, 'F', 5, 4, eye); s.Raw(L_HEAD, 'F', 6, 4, white);
                    s.Px(L_HEAD, 'F', 3, 5, shade); s.Px(L_HEAD, 'F', 4, 5, shade);
                    if (k.Female)
                    {
                        s.Raw(L_HEAD, 'F', 3, 6, K.Mix(skin, K.Rgb("C2535F"), 0.6)); s.Raw(L_HEAD, 'F', 4, 6, K.Mix(skin, K.Rgb("C2535F"), 0.6));
                    }
                    else
                    {
                        s.Px(L_HEAD, 'F', 3, 6, deep); s.Px(L_HEAD, 'F', 4, 6, deep);
                    }
                    Brush hb = Brush.Of(k.Hair), stub = Brush.Of(K.Mix(skin, k.Hair, 0.5));
                    if (k.Beard == 1)
                    {
                        s.Rect(L_HEAD, 'F', 0, 6, 7, 7, stub); s.Px(L_HEAD, 'F', 0, 5, stub); s.Px(L_HEAD, 'F', 7, 5, stub);
                        s.Px(L_HEAD, 'F', 3, 6, deep); s.Px(L_HEAD, 'F', 4, 6, deep);
                        s.Rect(L_HEAD, 'R', 5, 5, 7, 7, stub); s.Rect(L_HEAD, 'L', 0, 5, 2, 7, stub);
                    }
                    else if (k.Beard == 2)
                    {
                        s.Rect(L_HEAD, 'F', 0, 6, 7, 7, hb); s.Rect(L_HEAD, 'F', 0, 5, 1, 5, hb); s.Rect(L_HEAD, 'F', 6, 5, 7, 5, hb);
                        s.Px(L_HEAD, 'F', 2, 5, hb); s.Px(L_HEAD, 'F', 5, 5, hb);
                        s.Px(L_HEAD, 'F', 3, 6, deep); s.Px(L_HEAD, 'F', 4, 6, deep);
                        s.Rect(L_HEAD, 'R', 4, 4, 7, 7, hb); s.Rect(L_HEAD, 'L', 0, 4, 3, 7, hb);
                        s.FaceFill(L_HEAD, 'B', hb);
                        s.Rect(L_JACKET, 'F', 2, 0, 5, 1, hb); s.Rect(L_JACKET, 'F', 3, 2, 4, 2, hb);
                    }
                    else if (k.Beard == 3)
                    {
                        s.Rect(L_HEAD, 'F', 2, 6, 5, 6, hb); s.Px(L_HEAD, 'F', 1, 7, hb); s.Px(L_HEAD, 'F', 6, 7, hb);
                    }
                    break;
            }
            if (k.Face == "foulard" || k.Face == "lunettes")
            {
                s.Rows(L_HEAD, k.Face == "foulard" ? 5 : 6, 7, k.Trim);
                s.FaceFill(L_HEAD, 'B', k.Trim);
            }
            if (k.Face == "lunettes")
            {
                Brush strap = Brush.Of("2A1E14");
                s.Rows(L_HEAD, 3, 4, strap);
                int lens = K.Rgb("E8A030");
                s.Raw(L_HEAD, 'F', 1, 3, K.Rgb("FFE0A0")); s.Raw(L_HEAD, 'F', 2, 3, lens); s.Raw(L_HEAD, 'F', 1, 4, lens); s.Raw(L_HEAD, 'F', 2, 4, K.Mul(lens, 0.75));
                s.Raw(L_HEAD, 'F', 5, 3, K.Rgb("FFE0A0")); s.Raw(L_HEAD, 'F', 6, 3, lens); s.Raw(L_HEAD, 'F', 5, 4, lens); s.Raw(L_HEAD, 'F', 6, 4, K.Mul(lens, 0.75));
            }
            if (k.Mark != 0)
            {
                s.Raw(L_HEAD, 'F', 6, 5, k.Mark);
                s.Raw("rarm", 'F', 1, 11, k.Mark); s.Raw("rarm", 'F', 2, 11, K.Mul(k.Mark, 0.8));
            }
        }

        static void HairOf(Skin s, Look k)
        {
            if (k.HairStyle <= 0 || k.Face == "heaume" || k.Face == "or" || k.Face == "reptile" || k.Face == "bandes" || k.Face == "ecorce") return;
            Brush h = Brush.Of(HexOf(k.Hair), Brush.Fourrure, HexOf(K.Mul(k.Hair, 0.7)));
            if (k.HairStyle == 5)
            {
                // Tonsure : une couronne de cheveux.
                s.Rows(L_HEAD, 2, 3, h);
                s.Rect(L_HEAD, 'F', 1, 2, 6, 3, k.SkinB);
                s.Rect(L_HEAD, 'K', 0, 2, 7, 4, h);
                for (int i = 0; i < 8; i++) { s.Px(L_HEAD, 'T', i, 0, h); s.Px(L_HEAD, 'T', i, 7, h); s.Px(L_HEAD, 'T', 0, i, h); s.Px(L_HEAD, 'T', 7, i, h); }
                FaceOfBrows(s, k);
                return;
            }
            int side = k.HairStyle == 1 ? 3 : (k.HairStyle == 2 ? 5 : 7);
            int back = k.HairStyle == 1 ? 5 : 7;
            s.FaceFill(L_HEAD, 'T', h);
            s.Rect(L_HEAD, 'F', 0, 0, 7, 0, h);
            s.Px(L_HEAD, 'F', 0, 1, h); s.Px(L_HEAD, 'F', 7, 1, h);
            if (k.Female || k.Child || k.HairStyle >= 2)
            {
                s.Rect(L_HEAD, 'F', 0, 1, 7, 1, h);
                s.Px(L_HEAD, 'F', 0, 2, h); s.Px(L_HEAD, 'F', 7, 2, h);
            }
            else
            {
                s.Px(L_HEAD, 'F', 1, 1, h); s.Px(L_HEAD, 'F', 6, 1, h); s.Px(L_HEAD, 'F', 3, 1, h);
            }
            s.Rect(L_HEAD, 'R', 0, 0, 7, side, h);
            s.Rect(L_HEAD, 'L', 0, 0, 7, side, h);
            if (side < 7)
            {
                // Dégage l'oreille : les cheveux descendent plus bas vers l'arrière.
                s.Rect(L_HEAD, 'R', 0, side + 1, 3, Math.Min(7, side + 2), h);
                s.Rect(L_HEAD, 'L', 4, side + 1, 7, Math.Min(7, side + 2), h);
            }
            s.Rect(L_HEAD, 'K', 0, 0, 7, back, h);
            // Volume : la couche du dessus reprend le haut de la chevelure.
            s.FaceFill(L_HAT, 'T', h);
            s.Rect(L_HAT, 'F', 0, 0, 7, 0, h);
            s.Rect(L_HAT, 'R', 0, 0, 7, 1, h); s.Rect(L_HAT, 'L', 0, 0, 7, 1, h);
            s.Rect(L_HAT, 'K', 0, 0, 7, Math.Min(back, 3), h);
            if (k.HairStyle == 3)
            {
                s.Rect(L_JACKET, 'K', 1, 0, 6, 4, h);
                s.Rect(L_HAT, 'K', 0, 0, 7, 7, h);
                if (k.Female) { s.Rect(L_JACKET, 'F', 0, 0, 0, 2, h); s.Rect(L_JACKET, 'F', 7, 0, 7, 2, h); }
            }
            if (k.HairStyle == 4)
            {
                // Chignon.
                s.Rect(L_HAT, 'K', 2, 1, 5, 4, h);
                s.Rect(L_HAT, 'T', 2, 5, 5, 7, Brush.Of(K.Mul(k.Hair, 1.15)));
            }
        }

        static void FaceOfBrows(Skin s, Look k) { }

        static void HeadGear(Skin s, Look k)
        {
            Brush c = k.HeadB;
            Brush d = Brush.Of(K.Mul(c.Color, 0.72));
            switch (k.Head)
            {
                case "capuche":
                    s.FaceFill(L_HAT, 'T', c); s.FaceFill(L_HAT, 'K', c); s.FaceFill(L_HAT, 'R', c); s.FaceFill(L_HAT, 'L', c);
                    s.Rect(L_HAT, 'F', 0, 0, 7, 1, c); s.Rect(L_HAT, 'F', 0, 2, 0, 6, c); s.Rect(L_HAT, 'F', 7, 2, 7, 6, c);
                    s.Rect(L_HAT, 'F', 1, 2, 6, 2, d);
                    break;
                case "casque":
                    s.FaceFill(L_HAT, 'T', c); s.Rect(L_HAT, 'K', 0, 0, 7, 6, c);
                    s.Rect(L_HAT, 'R', 0, 0, 7, 5, c); s.Rect(L_HAT, 'L', 0, 0, 7, 5, c);
                    s.Rect(L_HAT, 'F', 0, 0, 7, 2, c); s.Rect(L_HAT, 'F', 3, 3, 4, 4, c);
                    s.Rect(L_HAT, 'F', 0, 3, 0, 6, c); s.Rect(L_HAT, 'F', 7, 3, 7, 6, c);
                    s.Rows(L_HAT, 2, 2, d);
                    s.Rect(L_HAT, 'T', 3, 0, 4, 7, Brush.Of(K.Mul(c.Color, 1.2)));
                    break;
                case "heaume":
                    s.Fill(L_HEAD, c);
                    s.FaceFill(L_HAT, 'T', c);
                    s.Rows(L_HAT, 0, 1, c);
                    s.Rect(L_HAT, 'T', 3, 0, 4, 7, k.Trim);
                    s.Rect(L_HEAD, 'F', 1, 4, 6, 4, Brush.Of(Dark));
                    if (k.Glow != 0) { s.Raw(L_HEAD, 'F', 2, 4, k.Glow); s.Raw(L_HEAD, 'F', 5, 4, k.Glow); s.Raw(L_HEAD, 'F', 3, 4, K.Mul(k.Glow, 0.5)); s.Raw(L_HEAD, 'F', 4, 4, K.Mul(k.Glow, 0.5)); }
                    s.Raw(L_HEAD, 'F', 2, 6, Dark); s.Raw(L_HEAD, 'F', 3, 6, Dark); s.Raw(L_HEAD, 'F', 4, 6, Dark); s.Raw(L_HEAD, 'F', 5, 6, Dark);
                    s.Rect(L_HEAD, 'F', 3, 0, 4, 3, Brush.Of(K.Mul(c.Color, 1.2)));
                    s.Rows(L_HEAD, 7, 7, d);
                    break;
                case "chapeau":
                case "paille":
                    s.FaceFill(L_HAT, 'T', c);
                    s.Rows(L_HAT, 0, 1, c);
                    s.Rows(L_HAT, 2, 2, k.Head == "paille" ? c : d);
                    if (k.Head == "chapeau") s.Rows(L_HAT, 1, 1, k.Trim);
                    break;
                case "bandeau":
                    s.Rows(L_HAT, 2, 2, c);
                    s.Raw(L_HAT, 'F', 3, 2, K.Mul(c.Color, 1.3));
                    break;
                case "couronne":
                    s.Rows(L_HAT, 1, 1, c);
                    foreach (char f in Skin.Sides) for (int x = 0; x < 8; x += 2) s.Px(L_HAT, f, x, 0, c);
                    s.Raw(L_HAT, 'F', 3, 1, K.Rgb("D8343E")); s.Raw(L_HAT, 'F', 4, 1, K.Rgb("D8343E"));
                    break;
                case "turban":
                    s.FaceFill(L_HAT, 'T', c);
                    s.Rows(L_HAT, 0, 2, c);
                    s.Rows(L_HAT, 1, 1, d);
                    s.Rect(L_HAT, 'K', 3, 3, 4, 7, c);
                    break;
                case "nemes":
                    Brush st = Brush.Of(HexOf(c.Color), Brush.Bandes, "1E2A8A");
                    s.FaceFill(L_HAT, 'T', st); s.FaceFill(L_HAT, 'K', st); s.FaceFill(L_HAT, 'R', st); s.FaceFill(L_HAT, 'L', st);
                    s.Rect(L_HAT, 'F', 0, 0, 7, 1, st); s.Rect(L_HAT, 'F', 0, 2, 0, 7, st); s.Rect(L_HAT, 'F', 7, 2, 7, 7, st);
                    s.Raw(L_HAT, 'F', 3, 0, K.Rgb("D8343E")); s.Raw(L_HAT, 'F', 4, 0, K.Rgb("2AA8A0"));
                    break;
                case "cornes":
                    for (int z = 1; z < 8; z += 2) { s.Px(L_HAT, 'T', 3, z, c); s.Px(L_HAT, 'T', 4, z, c); }
                    s.Px(L_HAT, 'F', 1, 0, c); s.Px(L_HAT, 'F', 6, 0, c); s.Px(L_HAT, 'F', 0, 1, c); s.Px(L_HAT, 'F', 7, 1, c);
                    s.Rect(L_HAT, 'R', 3, 0, 4, 1, c); s.Rect(L_HAT, 'L', 3, 0, 4, 1, c);
                    break;
                case "feuillage":
                    Brush lf = Brush.Of(HexOf(c.Color), Brush.Fourrure, HexOf(K.Mul(c.Color, 0.6)));
                    s.FaceFill(L_HAT, 'T', lf);
                    foreach (char f in Skin.Sides)
                        for (int x = 0; x < 8; x++)
                        {
                            s.Px(L_HAT, f, x, 0, lf);
                            if (K.Hash(x + f, 5, s.Seed) < 0.6) s.Px(L_HAT, f, x, 1, lf);
                            if (K.Hash(x + f, 6, s.Seed) < 0.25) s.Px(L_HAT, f, x, 2, lf);
                        }
                    break;
                case "fourrure":
                    Brush fu = Brush.Of(HexOf(c.Color), Brush.Fourrure, HexOf(K.Mul(c.Color, 0.6)));
                    s.FaceFill(L_HAT, 'T', fu);
                    s.Rows(L_HAT, 0, 2, fu);
                    break;
            }
        }

        static readonly string[] Sun = { ".XX.", "XXXX", "XXXX", ".XX." };
        static readonly string[] Moon = { ".XX.", "XX..", "XX..", ".XX." };
        static readonly string[] Gem = { ".XX.", "XXXX", ".XX.", "...." };
        static readonly string[] Star = { ".X..", "XXX.", ".X.X", "...." };

        static void Extras(Skin s, Look k)
        {
            if (k.Tabard)
            {
                s.Rect(L_BODY, 'F', 2, 1, 5, 11, k.TabardB);
                s.Rect(L_BODY, 'K', 2, 1, 5, 11, k.TabardB);
                foreach (string leg in new string[] { "rleg", "lleg" })
                {
                    s.Rect(leg, 'F', 2, 0, 3, 3, k.TabardB);
                    s.Rect(leg, 'K', 0, 0, 1, 3, k.TabardB);
                }
                s.Rect(L_BODY, 'F', 2, 9, 5, 9, k.Belt);
            }
            string[] em = k.Emblem == "soleil" ? Sun : (k.Emblem == "croissant" ? Moon : (k.Emblem == "losange" ? Gem : (k.Emblem == "etoile" ? Star : null)));
            if (em != null)
            {
                for (int y = 0; y < 4; y++)
                    for (int x = 0; x < 4; x++)
                        if (em[y][x] == 'X') s.Raw(L_BODY, 'F', 2 + x, 3 + y, k.EmblemColor);
            }
            if (k.Pack)
            {
                Brush bag = Brush.Of("7A5632");
                s.Rect(L_JACKET, 'K', 1, 1, 6, 8, bag);
                s.Rect(L_JACKET, 'K', 1, 3, 6, 3, Brush.Of("4E3620"));
                s.Rect(L_JACKET, 'K', 3, 4, 4, 4, Brush.Of(Gold));
                s.Rect(L_JACKET, 'F', 1, 0, 1, 7, Brush.Of("4E3620")); s.Rect(L_JACKET, 'F', 6, 0, 6, 7, Brush.Of("4E3620"));
            }
            if (k.Cape)
            {
                s.FaceFill(L_JACKET, 'K', k.CapeB);
                s.Rect(L_JACKET, 'R', 3, 0, 3, 1, k.CapeB); s.Rect(L_JACKET, 'L', 0, 0, 0, 1, k.CapeB);
                s.Rect(L_JACKET, 'F', 0, 0, 1, 0, k.CapeB); s.Rect(L_JACKET, 'F', 6, 0, 7, 0, k.CapeB);
                foreach (string leg in new string[] { "rpants", "lpants" }) s.Rect(leg, 'K', 0, 0, 3, 5, k.CapeB);
            }
        }

        public static string HexOf(int c) { return (c & 0xFFFFFF).ToString("X6"); }

        // ------------------------------------------------------------------ Catalogue des personnages

        static readonly string[] SkinTones = { "F2D3B4", "E8C19A", "D9A878", "C68E5E", "A8703E", "7A4E2A" };
        static readonly string[] HairAll = { "1E1810", "3A2614", "5A3A1E", "8A5A2A", "C89A4A", "9A3E22", "8A8A8E", "D8D8DC" };

        static Look Base(string id, string label, Rng r)
        {
            Look k = new Look();
            k.Id = id;
            k.Label = label;
            k.SkinB = Brush.Of(r.Pick(SkinTones));
            k.Hair = K.Rgb(r.Pick(HairAll));
            k.Eye = K.Rgb(r.Pick(new string[] { "3E2A1A", "2A4A6A", "3A5A2E", "5A4A2A", "2A2A2E" }));
            return k;
        }

        static void Palette(string culture, out string[] tops, out string[] pants, out string[] trims)
        {
            if (culture == "lumiere")
            {
                tops = new string[] { "7A5A9A", "4E78B8", "B8483E", "C9A24A", "5E8C4A", "8AA8C8", "2E7A7A" };
                pants = new string[] { "6B4A2E", "5A5F6B", "2E3A5C", "7A6A4A" };
                trims = new string[] { "F4EEDC", "E8C050", "C8B894" };
            }
            else if (culture == "ombre")
            {
                tops = new string[] { "5B3E8C", "3A3A46", "6E2438", "2E6B6B", "34304A", "4A4A5E", "7A3E5E" };
                pants = new string[] { "22202A", "34343E", "2E2440", "3A2E2E" };
                trims = new string[] { "B8A8D8", "8C96A3", "9B59FF" };
            }
            else
            {
                tops = new string[] { "8C6A46", "6E7A3E", "B8893E", "5E7186", "A85A3A", "8A4E36", "7A8A6A" };
                pants = new string[] { "5A4630", "3E4A2E", "4A4A50", "6A5A46" };
                trims = new string[] { "E8DCC0", "C8A27A", "A8B0A0" };
            }
        }

        static Look Villager(string id, string label, string culture, string role, int n)
        {
            Rng r = new Rng(id + "/v3");
            Look k = Base(id, label, r);
            string[] tops, pants, trims;
            Palette(culture, out tops, out pants, out trims);
            k.Top = Brush.Of(tops[(n + r.Next(2)) % tops.Length]);
            k.Pants = Brush.Of(r.Pick(pants));
            k.Trim = Brush.Of(r.Pick(trims));
            k.Boots = Brush.Of(r.Pick(new string[] { "2E2620", "3A2E22", "1E1C20" }));
            k.Female = n % 2 == 1;
            if (culture == "ombre" && r.Next(3) == 0) k.Hair = K.Rgb(r.Pick(new string[] { "D8D8DC", "1E1810", "8A8A8E" }));
            if (role == "enfant")
            {
                k.Child = true;
                k.HairStyle = k.Female ? 2 : 1;
                k.Sleeves = 1;
                k.Garb = "tunique";
                return k;
            }
            k.HairStyle = k.Female ? r.Pick(new int[] { 2, 3, 3, 4 }) : r.Pick(new int[] { 1, 1, 2, 0 });
            if (!k.Female) k.Beard = r.Pick(new int[] { 0, 0, 1, 2, 3 });
            if (role == "fermier")
            {
                k.Garb = n % 2 == 0 ? "tablier" : "tunique";
                k.Trim = Brush.Of(n % 2 == 0 ? "6A5236" : "C8B894");
                k.Sleeves = 1;
                k.Head = "paille";
                k.HeadB = Brush.Of("D8B85A", Brush.Bandes, "B8963E");
                k.Pants = Brush.Of(r.Pick(new string[] { "5A4630", "4A5A6A", "6A5A46" }));
                return k;
            }
            if (role == "voyageur")
            {
                k.Garb = "manteau";
                k.Top = Brush.Of(r.Pick(new string[] { "5A4A3A", "3E4A3A", "4A4654", "6A4A2E" }));
                k.Trim = Brush.Of("C8B894");
                k.Head = n % 2 == 0 ? "capuche" : "chapeau";
                k.HeadB = Brush.Of(K.Mul(k.Top.Color, n % 2 == 0 ? 0.9 : 0.7));
                k.Pack = true;
                return k;
            }
            string[] garbs = k.Female ? new string[] { "robe", "robe", "tunique", "manteau" } : new string[] { "tunique", "tunique", "cuir", "manteau" };
            k.Garb = garbs[r.Next(garbs.Length)];
            if (k.Garb == "robe") { k.Belt = Brush.Of(HexOf(k.Trim.Color)); }
            if (k.Garb == "cuir") { k.Trim = Brush.Of(tops[(n + 3) % tops.Length]); k.Top = Brush.Of(r.Pick(new string[] { "6A4A2E", "4E3A28", "3A3230" })); }
            k.Sleeves = k.Garb == "tunique" ? r.Next(2) : 0;
            if (r.Next(4) == 0) { k.Head = k.Female ? "bandeau" : "chapeau"; k.HeadB = Brush.Of(tops[(n + 2) % tops.Length]); }
            if (culture == "ombre" && r.Next(3) == 0) { k.Head = "capuche"; k.HeadB = Brush.Of(K.Mul(k.Top.Color, 0.8)); }
            return k;
        }

        static Look Guard(string id, string label, string faction, int n)
        {
            Rng r = new Rng(id + "/g2");
            Look k = Base(id, label, r);
            k.Garb = "plates";
            k.HairStyle = 1;
            k.Beard = r.Pick(new int[] { 0, 1, 1, 2 });
            k.Head = "casque";
            k.Tabard = true;
            if (faction == "lumiere")
            {
                k.Top = Brush.Of("AEB6C2", Brush.Metal, "FFFFFF"); k.Trim = Brush.Of("7E8794"); k.Pants = Brush.Of("8A93A0"); k.Boots = Brush.Of("4A4E58");
                k.HeadB = Brush.Of("B8C0CC", Brush.Metal, "FFFFFF");
                k.TabardB = Brush.Of(n == 2 ? "3E5E9A" : "F0ECE0"); k.Emblem = "soleil"; k.EmblemColor = K.Rgb("E8B83A");
                k.Belt = Brush.Of("5A3A22");
            }
            else if (faction == "ombre")
            {
                k.Top = Brush.Of("3E3C4E", Brush.Metal, "8A88A0"); k.Trim = Brush.Of("26242E"); k.Pants = Brush.Of("302E3C"); k.Boots = Brush.Of("16151C");
                k.HeadB = Brush.Of("34323F", Brush.Metal, "8A88A0");
                k.TabardB = Brush.Of(n == 2 ? "6E2438" : "5B3E8C"); k.Emblem = "croissant"; k.EmblemColor = K.Rgb("D8DCE8");
                k.Belt = Brush.Of("1E1C24");
                if (r.Next(2) == 0) k.Hair = K.Rgb("D8D8DC");
            }
            else
            {
                k.Garb = "cuir";
                k.Top = Brush.Of("6A4A2E"); k.Trim = Brush.Of("8A8E96"); k.Pants = Brush.Of("4A4A50"); k.Boots = Brush.Of("2E2620");
                k.HeadB = Brush.Of("8A8E96", Brush.Metal, "FFFFFF");
                k.TabardB = Brush.Of(n == 2 ? "6E7A3E" : "5E7186"); k.Emblem = "losange"; k.EmblemColor = K.Rgb("E8DCC0");
            }
            return k;
        }

        /// <summary>PNJ nommes (ville et quetes) puis PNJ d'ambiance.</summary>
        public static List<Look> Npcs()
        {
            List<Look> l = new List<Look>();
            Look k;

            k = new Look(); k.Id = "blacksmith"; k.Label = "Torvald";
            k.SkinB = Brush.Of("C8935F"); k.Hair = K.Rgb("5A3A1E"); k.HairStyle = 0; k.Beard = 2; k.Eye = K.Rgb("3E2A1A");
            k.Garb = "tablier"; k.Top = Brush.Of("8C4A2E"); k.Trim = Brush.Of("4A3424"); k.Sleeves = 1; k.Pants = Brush.Of("3A3A40"); k.Boots = Brush.Of("1E1A18");
            l.Add(k);

            k = new Look(); k.Id = "alchemist"; k.Label = "Mirelle"; k.Female = true;
            k.SkinB = Brush.Of("EBC8A4"); k.Hair = K.Rgb("9A3E22"); k.HairStyle = 3; k.Eye = K.Rgb("3A7A4A");
            k.Garb = "robe"; k.Top = Brush.Of("5B3E8C"); k.Trim = Brush.Of("B36BFF"); k.Belt = Brush.Of("3A2A1C"); k.Boots = Brush.Of("2A1E30");
            k.Head = "capuche"; k.HeadB = Brush.Of("4A3076");
            l.Add(k);

            k = new Look(); k.Id = "priest"; k.Label = "Frere Anselme";
            k.SkinB = Brush.Of("E0B088"); k.Hair = K.Rgb("9A9A9E"); k.HairStyle = 5; k.Eye = K.Rgb("2A4A6A");
            k.Garb = "robe"; k.Top = Brush.Of("EDE3C8"); k.Trim = Brush.Of("D8B24A"); k.Belt = Brush.Of("C9A24A"); k.Boots = Brush.Of("5A4630");
            k.Emblem = "soleil"; k.EmblemColor = K.Rgb("E8B83A");
            l.Add(k);

            k = new Look(); k.Id = "runesmith"; k.Label = "Ysolde"; k.Female = true;
            k.SkinB = Brush.Of("F0D2B4"); k.Hair = K.Rgb("D8DCE8"); k.HairStyle = 4; k.Eye = K.Rgb("9B59FF");
            k.Garb = "manteau"; k.Top = Brush.Of("2E2A5C", Brush.Etoiles, "B892FF"); k.Trim = Brush.Of("9B59FF"); k.Belt = Brush.Of("1E1A3A");
            k.Pants = Brush.Of("1E1A3A"); k.Boots = Brush.Of("14122A");
            l.Add(k);

            k = new Look(); k.Id = "merchant"; k.Label = "Corvin";
            k.SkinB = Brush.Of("D9A878"); k.Hair = K.Rgb("1E1810"); k.HairStyle = 1; k.Beard = 3; k.Eye = K.Rgb("3E2A1A");
            k.Garb = "manteau"; k.Top = Brush.Of("2E6B4A"); k.Trim = Brush.Of("E8DCC0"); k.Belt = Brush.Of("C9A24A"); k.Pants = Brush.Of("5A4630"); k.Boots = Brush.Of("2E2620");
            k.Head = "chapeau"; k.HeadB = Brush.Of("2E6B4A");
            l.Add(k);

            k = new Look(); k.Id = "maelis"; k.Label = "Soeur Maelis"; k.Female = true;
            k.SkinB = Brush.Of("E2B48C"); k.Hair = K.Rgb("D8B45A"); k.HairStyle = 3; k.Eye = K.Rgb("2A4A6A");
            k.Garb = "robe"; k.Top = Brush.Of("F4EEDC"); k.Trim = Brush.Of("E8C050"); k.Belt = Brush.Of("C9A24A"); k.Boots = Brush.Of("8A7A5A");
            k.Head = "bandeau"; k.HeadB = Brush.Of("E8C050"); k.Emblem = "soleil"; k.EmblemColor = K.Rgb("E8B83A");
            l.Add(k);

            k = new Look(); k.Id = "teo"; k.Label = "Teo";
            k.SkinB = Brush.Of("C68E5E"); k.Hair = K.Rgb("3A2614"); k.HairStyle = 2; k.Eye = K.Rgb("3E2A1A");
            k.Garb = "tunique"; k.Top = Brush.Of("C8C0B0"); k.Trim = Brush.Of("A89E8A"); k.Sleeves = 1; k.Pants = Brush.Of("7A6E5A"); k.Barefoot = true;
            k.Belt = Brush.Of("6A5A46"); k.Mark = K.Rgb("FFE9A8");
            l.Add(k);

            k = new Look(); k.Id = "brune"; k.Label = "Brune"; k.Female = true;
            k.SkinB = Brush.Of("8A5A3A"); k.Hair = K.Rgb("1E1810"); k.HairStyle = 3; k.Eye = K.Rgb("3A2A1A");
            k.Garb = "tunique"; k.Top = Brush.Of("D0C8B8"); k.Trim = Brush.Of("A89E8A"); k.Sleeves = 1; k.Pants = Brush.Of("8A7E6A"); k.Barefoot = true;
            k.Belt = Brush.Of("6A5A46"); k.Mark = K.Rgb("FFE9A8");
            l.Add(k);

            k = new Look(); k.Id = "mourant"; k.Label = "Marque inconnu";
            k.SkinB = Brush.Of("C8B8A8"); k.Hair = K.Rgb("6A6A6E"); k.HairStyle = 1; k.Beard = 1; k.Eye = K.Rgb("4A4A50");
            k.Garb = "haillons"; k.Top = Brush.Of("9A9488"); k.Pants = Brush.Of("6A6458"); k.Mark = K.Rgb("B8A878");
            l.Add(k);

            k = new Look(); k.Id = "varenne"; k.Label = "Capitaine Varenne";
            k.SkinB = Brush.Of("C8A27A"); k.Hair = K.Rgb("3A2614"); k.HairStyle = 1; k.Beard = 1; k.Eye = K.Rgb("2A4A6A");
            k.Garb = "plates"; k.Top = Brush.Of("AEB6C2", Brush.Metal, "FFFFFF"); k.Trim = Brush.Of("7E8794"); k.Pants = Brush.Of("8A93A0"); k.Boots = Brush.Of("4A4E58");
            k.Belt = Brush.Of("5A3A22"); k.Tabard = true; k.TabardB = Brush.Of("3E5E9A"); k.Emblem = "soleil"; k.EmblemColor = K.Rgb("E8B83A");
            k.Cape = true; k.CapeB = Brush.Of("B8483E");
            l.Add(k);

            k = new Look(); k.Id = "orsan"; k.Label = "Maitre Orsan";
            k.SkinB = Brush.Of("E8C8A8"); k.Hair = K.Rgb("E4E4E8"); k.HairStyle = 3; k.Beard = 2; k.Eye = K.Rgb("4A8AC8");
            k.Garb = "robe"; k.Top = Brush.Of("1E2A54", Brush.Etoiles, "FFE08A"); k.Trim = Brush.Of("9AD8FF"); k.Belt = Brush.Of("C9A24A"); k.Boots = Brush.Of("14182E");
            k.Emblem = "etoile"; k.EmblemColor = K.Rgb("FFE08A");
            l.Add(k);

            // --- Quetes de la Lumiere, actes I et II ---
            k = new Look(); k.Id = "elwen"; k.Label = "Gardienne Elwen"; k.Female = true;
            k.SkinB = Brush.Of("E6C09A"); k.Hair = K.Rgb("C8A24A"); k.HairStyle = 3; k.Eye = K.Rgb("3A7A4A");
            k.Garb = "cuir"; k.Top = Brush.Of("3E6A3A"); k.Trim = Brush.Of("C9A24A"); k.Pants = Brush.Of("4A3E2A"); k.Boots = Brush.Of("2E2620"); k.Belt = Brush.Of("5A3A22");
            k.Cape = true; k.CapeB = Brush.Of("2E5A32"); k.Emblem = "soleil"; k.EmblemColor = K.Rgb("E8C050");
            l.Add(k);

            k = new Look(); k.Id = "agathe"; k.Label = "Agathe"; k.Female = true;
            k.SkinB = Brush.Of("C8935F"); k.Hair = K.Rgb("9A4A22"); k.HairStyle = 4; k.Eye = K.Rgb("3E2A1A");
            k.Garb = "tunique"; k.Top = Brush.Of("9A3E2E", Brush.Bandes, "6A2A20"); k.Trim = Brush.Of("5A3A22"); k.Sleeves = 1; k.Pants = Brush.Of("5A4630"); k.Boots = Brush.Of("2E2620");
            k.Belt = Brush.Of("3A2A1C"); k.Head = "bandeau"; k.HeadB = Brush.Of("C8B88A");
            l.Add(k);

            k = new Look(); k.Id = "gilles"; k.Label = "Frere Gilles";
            k.SkinB = Brush.Of("D8B08A"); k.Hair = K.Rgb("4A3424"); k.HairStyle = 5; k.Beard = 1; k.Eye = K.Rgb("4A4A50");
            k.Garb = "robe"; k.Top = Brush.Of("6A5238"); k.Trim = Brush.Of("4E3C28"); k.Belt = Brush.Of("C8B88A"); k.Barefoot = true;
            k.Head = "capuche"; k.HeadB = Brush.Of("5E4830");
            l.Add(k);

            k = new Look(); k.Id = "seraphine"; k.Label = "Haute-Prelate Seraphine"; k.Female = true;
            k.SkinB = Brush.Of("EBC8A4"); k.Hair = K.Rgb("E4E4E8"); k.HairStyle = 3; k.Eye = K.Rgb("4A8AC8");
            k.Garb = "robe"; k.Top = Brush.Of("F8F4E8"); k.Trim = Brush.Of("E8C050"); k.Belt = Brush.Of("E8C050"); k.Boots = Brush.Of("C9A24A");
            k.Head = "couronne"; k.HeadB = Brush.Of("E8C050"); k.Cape = true; k.CapeB = Brush.Of("D8B24A");
            k.Emblem = "soleil"; k.EmblemColor = K.Rgb("E8A82A");
            l.Add(k);

            k = new Look(); k.Id = "morvain"; k.Label = "Morvain";
            k.SkinB = Brush.Of("C8B49A"); k.Hair = K.Rgb("16141A"); k.HairStyle = 2; k.Beard = 1; k.Eye = K.Rgb("8A8A96");
            k.Garb = "plates"; k.Top = Brush.Of("2A2A32", Brush.Metal, "6A6A78"); k.Trim = Brush.Of("16161C"); k.Pants = Brush.Of("222228"); k.Boots = Brush.Of("121216");
            k.Belt = Brush.Of("5A1E2A"); k.Cape = true; k.CapeB = Brush.Of("5A1E2A");
            l.Add(k);

            k = new Look(); k.Id = "tobias"; k.Label = "Tobias";
            k.SkinB = Brush.Of("B88A5E"); k.Hair = K.Rgb("3A2614"); k.HairStyle = 1; k.Eye = K.Rgb("3A5A3A");
            k.Garb = "cuir"; k.Top = Brush.Of("5E6A36"); k.Trim = Brush.Of("7A5A3A"); k.Pants = Brush.Of("4A3E2A"); k.Boots = Brush.Of("2E2620"); k.Belt = Brush.Of("3A2A1C");
            k.Head = "capuche"; k.HeadB = Brush.Of("4A5A2E"); k.Pack = true;
            l.Add(k);

            k = new Look(); k.Id = "hilde"; k.Label = "Hilde"; k.Female = true;
            k.SkinB = Brush.Of("E8BE98"); k.Hair = K.Rgb("6A4424"); k.HairStyle = 4; k.Eye = K.Rgb("3E2A1A");
            k.Garb = "tablier"; k.Top = Brush.Of("8A3A3A"); k.Trim = Brush.Of("F0ECE0"); k.Sleeves = 1; k.Pants = Brush.Of("6A2E2E"); k.Boots = Brush.Of("3A2A1C");
            l.Add(k);

            k = new Look(); k.Id = "bertin"; k.Label = "Bertin";
            k.SkinB = Brush.Of("D9A878"); k.Hair = K.Rgb("8A5A2A"); k.HairStyle = 1; k.Eye = K.Rgb("2A4A6A");
            k.Garb = "plates"; k.Top = Brush.Of("9AA2AE", Brush.Metal, "E8ECF0"); k.Trim = Brush.Of("6E7784"); k.Pants = Brush.Of("5A4630"); k.Boots = Brush.Of("3A3E48");
            k.Belt = Brush.Of("5A3A22"); k.Head = "casque"; k.HeadB = Brush.Of("9AA2AE", Brush.Metal, "E8ECF0");
            k.Tabard = true; k.TabardB = Brush.Of("3E5E9A"); k.Emblem = "soleil"; k.EmblemColor = K.Rgb("E8B83A");
            l.Add(k);

            k = new Look(); k.Id = "fenouil"; k.Label = "Fenouil";
            k.SkinB = Brush.Of("D0A070"); k.Hair = K.Rgb("A85A22"); k.HairStyle = 2; k.Beard = 3; k.Eye = K.Rgb("3A7A4A");
            k.Garb = "manteau"; k.Top = Brush.Of("8A7A2E"); k.Trim = Brush.Of("B8483E"); k.Belt = Brush.Of("5A3A22"); k.Pants = Brush.Of("5A4630"); k.Boots = Brush.Of("2E2620");
            k.Head = "chapeau"; k.HeadB = Brush.Of("6A5A22"); k.Pack = true;
            l.Add(k);

            k = new Look(); k.Id = "odile"; k.Label = "Dame Odile"; k.Female = true;
            k.SkinB = Brush.Of("E6C4A4"); k.Hair = K.Rgb("8E8E94"); k.HairStyle = 5; k.Eye = K.Rgb("2A4A6A");
            k.Garb = "manteau"; k.Top = Brush.Of("2E4A8A"); k.Trim = Brush.Of("E8C050"); k.Belt = Brush.Of("C9A24A"); k.Pants = Brush.Of("1E2E5A"); k.Boots = Brush.Of("14182E");
            k.Head = "bandeau"; k.HeadB = Brush.Of("E8C050"); k.Emblem = "losange"; k.EmblemColor = K.Rgb("E8C050");
            l.Add(k);

            k = new Look(); k.Id = "gaspard"; k.Label = "Gaspard";
            k.SkinB = Brush.Of("C68E5E"); k.Hair = K.Rgb("6A6A6E"); k.HairStyle = 1; k.Beard = 2; k.Eye = K.Rgb("3E2A1A");
            k.Garb = "tunique"; k.Top = Brush.Of("D8CCB0", Brush.Fourrure, "B8AC90"); k.Trim = Brush.Of("8A7A5A"); k.Pants = Brush.Of("6A5A46"); k.Boots = Brush.Of("3A2A1C");
            k.Belt = Brush.Of("5A3A22"); k.Head = "chapeau"; k.HeadB = Brush.Of("C8A85A"); k.Cape = true; k.CapeB = Brush.Of("6A4A2E");
            l.Add(k);

            k = new Look(); k.Id = "clotaire"; k.Label = "Clotaire";
            k.SkinB = Brush.Of("DDB896"); k.Hair = K.Rgb("C8C8CC"); k.HairStyle = 5; k.Beard = 1; k.Eye = K.Rgb("4A4A50");
            k.Garb = "robe"; k.Top = Brush.Of("7A7468"); k.Trim = Brush.Of("C9A24A"); k.Belt = Brush.Of("4A4438"); k.Boots = Brush.Of("3A342C");
            l.Add(k);

            k = new Look(); k.Id = "bastien"; k.Label = "Heraut Bastien";
            k.SkinB = Brush.Of("E2B48C"); k.Hair = K.Rgb("D8B45A"); k.HairStyle = 2; k.Eye = K.Rgb("2A4A6A");
            k.Garb = "tunique"; k.Top = Brush.Of("F0ECE0"); k.Trim = Brush.Of("E8C050"); k.Pants = Brush.Of("3E5E9A"); k.Boots = Brush.Of("2E2620"); k.Belt = Brush.Of("C9A24A");
            k.Tabard = true; k.TabardB = Brush.Of("B8483E"); k.Emblem = "soleil"; k.EmblemColor = K.Rgb("E8C050");
            k.Head = "chapeau"; k.HeadB = Brush.Of("B8483E");
            l.Add(k);

            k = new Look(); k.Id = "hardouin"; k.Label = "Sergent Hardouin";
            k.SkinB = Brush.Of("B88A5E"); k.Hair = K.Rgb("7A7A80"); k.HairStyle = 1; k.Beard = 2; k.Eye = K.Rgb("3E2A1A");
            k.Garb = "plates"; k.Top = Brush.Of("AEB6C2", Brush.Metal, "FFFFFF"); k.Trim = Brush.Of("D8B24A"); k.Pants = Brush.Of("8A93A0"); k.Boots = Brush.Of("4A4E58");
            k.Belt = Brush.Of("5A3A22"); k.Tabard = true; k.TabardB = Brush.Of("3E5E9A"); k.Emblem = "soleil"; k.EmblemColor = K.Rgb("E8B83A");
            k.Cape = true; k.CapeB = Brush.Of("2E4A8A");
            l.Add(k);

            k = new Look(); k.Id = "lise"; k.Label = "Lise"; k.Female = true;
            k.SkinB = Brush.Of("E8C8A8"); k.Hair = K.Rgb("5A3A1E"); k.HairStyle = 2; k.Eye = K.Rgb("4A8AC8");
            k.Garb = "robe"; k.Top = Brush.Of("2E3E7A", Brush.Etoiles, "FFE08A"); k.Trim = Brush.Of("9AD8FF"); k.Belt = Brush.Of("9AD8FF"); k.Boots = Brush.Of("1E2A54");
            k.Emblem = "etoile"; k.EmblemColor = K.Rgb("FFE08A");
            l.Add(k);

            foreach (string f in new string[] { "lumiere", "ombre", "neutre" })
            {
                for (int n = 1; n <= 3; n++) l.Add(Guard("garde_" + f + "_" + n, "Garde " + f + " " + n, f, n));
                for (int n = 1; n <= 6; n++) l.Add(Villager("villageois_" + f + "_" + n, "Villageois " + f + " " + n, f, "villageois", n));
                for (int n = 1; n <= 2; n++) l.Add(Villager("fermier_" + f + "_" + n, "Fermier " + f + " " + n, f, "fermier", n));
                for (int n = 1; n <= 3; n++) l.Add(Villager("enfant_" + f + "_" + n, "Enfant " + f + " " + n, f, "enfant", n));
            }
            for (int n = 1; n <= 4; n++) l.Add(Villager("voyageur_" + n, "Voyageur " + n, "neutre", "voyageur", n));
            return l;
        }

        static Look Monster(string id, string label)
        {
            Look k = new Look();
            k.Id = id; k.Label = label; k.Armor = true; k.HairStyle = 0;
            return k;
        }

        /// <summary>Tenues completes des monstres humanoides et du Veilleur du Pilier.</summary>
        public static List<Look> Outfits()
        {
            List<Look> l = new List<Look>();
            Look k;
            Brush scales = Brush.Of("7A8A3E", Brush.Ecailles, "5A5A2A");

            k = Monster("kobold", "Kobold eclaireur"); k.SkinB = scales; k.Face = "reptile"; k.Garb = "peau";
            k.Pants = Brush.Of("6A4A2E"); k.Belt = Brush.Of("4A3420"); k.Head = "cornes"; k.HeadB = Brush.Of("D8D0B0");
            l.Add(k);

            k = Monster("kobold_garde", "Sentinelle kobold"); k.SkinB = scales; k.Face = "reptile"; k.Garb = "plates";
            k.Top = Brush.Of("6A6E72", Brush.Metal, "B0B4B8"); k.Trim = scales; k.Pants = Brush.Of("5A4A32"); k.Boots = scales; k.Belt = Brush.Of("4A3420");
            k.Head = "casque"; k.HeadB = Brush.Of("6A6E72", Brush.Metal, "B0B4B8");
            l.Add(k);

            k = Monster("roi_kobold", "Roi Kobold"); k.SkinB = Brush.Of("8A7A2E", Brush.Ecailles, "6A4A1E"); k.Face = "reptile"; k.Garb = "plates";
            k.Top = Brush.Of("D8D0B0", Brush.Bandes, "8A8268"); k.Trim = k.SkinB; k.Pants = Brush.Of("7A1E2A"); k.Boots = k.SkinB; k.Belt = Brush.Of("C9A24A");
            k.Head = "couronne"; k.HeadB = Brush.Of("E8C050"); k.Cape = true; k.CapeB = Brush.Of("8A1E2E");
            l.Add(k);

            k = Monster("elfe_sombre", "Elfe sombre"); k.SkinB = Brush.Of("6E6488"); k.Hair = K.Rgb("E8E8F0"); k.HairStyle = 3; k.Glow = K.Rgb("C86BFF");
            k.Garb = "cuir"; k.Top = Brush.Of("2A2434"); k.Trim = Brush.Of("3E3650"); k.Pants = Brush.Of("1E1A28"); k.Boots = Brush.Of("14121C"); k.Belt = Brush.Of("A8B0C8");
            l.Add(k);

            k = Monster("momie", "Momie"); k.SkinB = Brush.Of("D8CCA8", Brush.Bandes, "6A5E42"); k.Face = "bandes"; k.Garb = "peau"; k.Glow = K.Rgb("7FE8FF");
            k.Pants = k.SkinB; k.Belt = k.SkinB;
            l.Add(k);

            k = Monster("pharaon", "Pharaon des sables"); k.SkinB = Brush.Of("C8BC98", Brush.Bandes, "5A4E36"); k.Face = "or"; k.Garb = "peau";
            k.HeadB = Brush.Of("E8C050"); k.Head = "nemes"; k.Pants = Brush.Of("F0ECE0"); k.Belt = Brush.Of("E8C050");
            k.Tabard = true; k.TabardB = Brush.Of("1E2A8A"); k.Emblem = "soleil"; k.EmblemColor = K.Rgb("E8C050");
            l.Add(k);

            k = Monster("glaneur", "Glaneur d'Eclats"); k.SkinB = Brush.Of("C8A27A"); k.Face = "lunettes"; k.Garb = "manteau";
            k.Top = Brush.Of("5A4A3A"); k.Trim = Brush.Of("8A6A4A"); k.Pants = Brush.Of("3E3830"); k.Boots = Brush.Of("2A221C");
            k.Head = "capuche"; k.HeadB = Brush.Of("4A3C2E"); k.Pack = true; k.Emblem = "losange"; k.EmblemColor = K.Rgb("B892FF");
            l.Add(k);

            k = Monster("glaneur_guetteur", "Guetteur glaneur"); k.SkinB = Brush.Of("B8926A"); k.Face = "lunettes"; k.Garb = "cuir";
            k.Top = Brush.Of("4E4436"); k.Trim = Brush.Of("7A6A52"); k.Pants = Brush.Of("3A342C"); k.Boots = Brush.Of("2A221C");
            k.Head = "chapeau"; k.HeadB = Brush.Of("3E3428");
            l.Add(k);

            k = Monster("revenant", "Revenant"); k.SkinB = Brush.Of("8A9A8A"); k.Face = "creux"; k.Garb = "haillons"; k.Glow = K.Rgb("A8FFC8");
            k.Top = Brush.Of("B8B4A4"); k.Pants = Brush.Of("6A685E"); k.Head = "capuche"; k.HeadB = Brush.Of("A8A494");
            l.Add(k);

            k = Monster("bandit", "Bandit de grand chemin"); k.SkinB = Brush.Of("C8935F"); k.Face = "foulard"; k.Garb = "cuir";
            k.Top = Brush.Of("4A3624"); k.Trim = Brush.Of("9A2E2E"); k.Pants = Brush.Of("3A3430"); k.Boots = Brush.Of("1E1A18");
            k.Head = "capuche"; k.HeadB = Brush.Of("3A2E26"); k.Eye = K.Rgb("2A2A2E");
            l.Add(k);

            k = Monster("braconnier", "Braconnier"); k.SkinB = Brush.Of("D0A070"); k.Hair = K.Rgb("5A3A1E"); k.Beard = 2; k.HairStyle = 1; k.Garb = "cuir";
            k.Top = Brush.Of("5E6A36"); k.Trim = Brush.Of("7A5A3A"); k.Pants = Brush.Of("4A3E2A"); k.Boots = Brush.Of("2E2620");
            k.Head = "fourrure"; k.HeadB = Brush.Of("6A4A2E");
            l.Add(k);

            k = Monster("purificateur", "Purificateur"); k.Face = "heaume"; k.Head = "heaume"; k.Garb = "plates"; k.Glow = K.Rgb("FFE08A");
            k.HeadB = Brush.Of("E4E0D4", Brush.Metal, "FFFFFF"); k.Top = Brush.Of("E4E0D4", Brush.Metal, "FFFFFF"); k.Trim = Brush.Of("D8B24A");
            k.Pants = Brush.Of("C8C4B8"); k.Boots = Brush.Of("9A8A5A"); k.Belt = Brush.Of("C9A24A");
            k.Tabard = true; k.TabardB = Brush.Of("F8F4E8"); k.Emblem = "soleil"; k.EmblemColor = K.Rgb("E8A82A");
            l.Add(k);

            k = Monster("pillard", "Pillard des dunes"); k.SkinB = Brush.Of("A8703E"); k.Face = "foulard"; k.Garb = "tunique";
            k.Top = Brush.Of("D8C08A"); k.Trim = Brush.Of("B8483E"); k.Pants = Brush.Of("8A6A3E"); k.Boots = Brush.Of("5A3E22"); k.Belt = Brush.Of("B8483E");
            k.Head = "turban"; k.HeadB = Brush.Of("D8C08A"); k.Sleeves = 1;
            l.Add(k);

            k = Monster("pillard_archer", "Tireur des dunes"); k.SkinB = Brush.Of("A8703E"); k.Face = "foulard"; k.Garb = "cuir";
            k.Top = Brush.Of("8A6A3E"); k.Trim = Brush.Of("2E6B8A"); k.Pants = Brush.Of("C8B07A"); k.Boots = Brush.Of("5A3E22"); k.Belt = Brush.Of("2E6B8A");
            k.Head = "turban"; k.HeadB = Brush.Of("2E6B8A");
            l.Add(k);

            k = Monster("cendreux", "Cendreux"); k.SkinB = Brush.Of("3A3432", Brush.Braises, "FF7A1E"); k.Face = "creux"; k.Garb = "haillons"; k.Glow = K.Rgb("FFB03A");
            k.Top = Brush.Of("2A2422"); k.Pants = Brush.Of("1E1A18");
            l.Add(k);

            k = Monster("treant", "Treant corrompu"); k.SkinB = Brush.Of("5A4028", Brush.Ecorce, "4A6A2A"); k.Face = "ecorce"; k.Garb = "peau"; k.Glow = K.Rgb("C86BFF");
            k.Pants = k.SkinB; k.Belt = Brush.Of("4A6A2A", Brush.Fourrure, "2E4A1A"); k.Head = "feuillage"; k.HeadB = Brush.Of("3E5A2A");
            l.Add(k);

            k = Monster("veilleur", "Veilleur du Pilier"); k.Face = "heaume"; k.Head = "heaume"; k.Garb = "plates"; k.Glow = K.Rgb("B36BFF");
            k.HeadB = Brush.Of("2A2638", Brush.Metal, "6A6490"); k.Top = Brush.Of("2A2638", Brush.Etoiles, "B36BFF"); k.Trim = Brush.Of("3A3450");
            k.Pants = Brush.Of("221E30"); k.Boots = Brush.Of("14121C"); k.Belt = Brush.Of("9B59FF");
            k.Tabard = true; k.TabardB = Brush.Of("3A2A5C"); k.Emblem = "croissant"; k.EmblemColor = K.Rgb("D8DCE8");
            l.Add(k);

            // --- Monstres des quetes ---
            k = Monster("pretre_revenant", "Pretre revenant"); k.SkinB = Brush.Of("8A9A8A"); k.Face = "creux"; k.Garb = "robe"; k.Glow = K.Rgb("A8FFC8");
            k.Top = Brush.Of("C8C0A8"); k.Trim = Brush.Of("8A7A3A"); k.Belt = Brush.Of("8A7A3A"); k.Boots = Brush.Of("5A5446");
            k.Head = "capuche"; k.HeadB = Brush.Of("B8B098"); k.Emblem = "soleil"; k.EmblemColor = K.Rgb("8A7A3A");
            l.Add(k);

            k = Monster("fidele_petrifie", "Fidele petrifie"); k.SkinB = Brush.Of("8E8E88", Brush.Ecailles, "6A6A66"); k.Face = "creux"; k.Garb = "robe"; k.Glow = K.Rgb("FFE9A8");
            k.Top = Brush.Of("7E7E78", Brush.Ecailles, "5E5E5A"); k.Trim = Brush.Of("5A5A56"); k.Belt = Brush.Of("5A5A56"); k.Boots = Brush.Of("6A6A66");
            k.Head = "capuche"; k.HeadB = Brush.Of("747470", Brush.Ecailles, "585854");
            l.Add(k);
            return l;
        }

        // ------------------------------------------------------------------ Betes : textures du jeu recolorees

        sealed class Tint
        {
            public string Src, Dst;
            public int[] Ramp;
            public double Strength = 0.9;
            public int Eyes = 0;          // couleur donnee aux pixels rouges vifs (yeux), 0 = inchanges
            public bool KeepWhite, KeepRed, KeepGreen;
            public int Specks = 0;        // couleur des eclats (braises, givre, cristaux)
            public double SpeckRate = 0;
            public int[] GreenTo;         // les verts (lierre du golem) passent sur cette rampe
        }

        static int[] R(params string[] hex)
        {
            int[] r = new int[hex.Length];
            for (int i = 0; i < hex.Length; i++) r[i] = K.Rgb(hex[i]);
            return r;
        }

        static Img Recolor(Img src, Tint t, int seed)
        {
            double lo = 1, hi = 0;
            for (int i = 0; i < src.P.Length; i++)
            {
                int c = src.P[i];
                if (((c >> 24) & 255) < 16) continue;
                double l = Lum(c);
                if (l < lo) lo = l;
                if (l > hi) hi = l;
            }
            if (hi - lo < 0.05) { lo = 0; hi = 1; }
            Img o = new Img(src.W, src.H);
            for (int y = 0; y < src.H; y++)
            {
                for (int x = 0; x < src.W; x++)
                {
                    int c = src.Get(x, y);
                    int a = (c >> 24) & 255;
                    if (a == 0) continue;
                    double hue, sat, lum;
                    Hsl(c, out hue, out sat, out lum);
                    bool red = (hue < 18 || hue > 342) && sat > 0.55 && ((c >> 16) & 255) > 140;
                    bool green = hue > 65 && hue < 170 && sat > 0.28;
                    bool white = sat < 0.22 && lum > 0.72;
                    double tt = (Lum(c) - lo) / (hi - lo);
                    int n;
                    if (red && t.Eyes != 0) n = t.Eyes;
                    else if ((red && t.KeepRed) || (white && t.KeepWhite) || (green && t.KeepGreen)) n = c;
                    else if (green && t.GreenTo != null) n = K.Ramp(t.GreenTo, tt);
                    else
                    {
                        n = K.Mix(c | unchecked((int)0xFF000000), K.Ramp(t.Ramp, tt), t.Strength);
                        if (t.Specks != 0 && K.Hash(x, y, seed) < t.SpeckRate) n = t.Specks;
                    }
                    o.Set(x, y, (a << 24) | (n & 0xFFFFFF));
                }
            }
            return o;
        }

        static double Lum(int c) { return (0.299 * ((c >> 16) & 255) + 0.587 * ((c >> 8) & 255) + 0.114 * (c & 255)) / 255.0; }

        static void Hsl(int c, out double h, out double s, out double l)
        {
            double r = ((c >> 16) & 255) / 255.0, g = ((c >> 8) & 255) / 255.0, b = (c & 255) / 255.0;
            double mx = Math.Max(r, Math.Max(g, b)), mn = Math.Min(r, Math.Min(g, b)), d = mx - mn;
            l = (mx + mn) / 2;
            if (d < 1e-6) { h = 0; s = 0; return; }
            s = l > 0.5 ? d / (2 - mx - mn) : d / (mx + mn);
            if (mx == r) h = 60 * (((g - b) / d) % 6);
            else if (mx == g) h = 60 * ((b - r) / d + 2);
            else h = 60 * ((r - g) / d + 4);
            if (h < 0) h += 360;
        }

        sealed class Variant
        {
            public string Kind, Id, Label, Src;
            public Tint T;
        }

        static Variant V(string kind, string id, string label, string src, int[] ramp, double strength)
        {
            Variant v = new Variant();
            v.Kind = kind; v.Id = id; v.Label = label; v.Src = src;
            v.T = new Tint(); v.T.Ramp = ramp; v.T.Strength = strength;
            return v;
        }

        static List<Variant> Variants()
        {
            List<Variant> l = new List<Variant>();
            Variant v;
            v = V("loup", "loup", "Loup des plaines", "wolf/wolf", R("2E261E", "8A7A66", "DCD0BC"), 0.85); l.Add(v);
            v = V("loup", "loup_ombre", "Loup des ombres", "wolf/wolf", R("06060C", "22202E", "5E5A78"), 0.95); v.T.Eyes = K.Rgb("C86BFF"); l.Add(v);
            v = V("loup", "loup_givre", "Loup de givre", "wolf/wolf", R("4E6C8A", "B4D2E8", "FFFFFF"), 0.9); v.T.Eyes = K.Rgb("7FE8FF"); v.T.Specks = K.Rgb("FFFFFF"); v.T.SpeckRate = 0.04; l.Add(v);
            v = V("loup", "limier_aube", "Limier de l'Aube", "wolf/wolf", R("6A4A22", "E0BE7A", "FFF6DC"), 0.9); v.T.Eyes = K.Rgb("FFD23E"); l.Add(v);
            v = V("loup", "chien_cendre", "Chien de cendre", "wolf/wolf", R("0E0A08", "38302E", "6E625C"), 0.95); v.T.Eyes = K.Rgb("FF8A1E"); v.T.Specks = K.Rgb("FF7A1E"); v.T.SpeckRate = 0.05; l.Add(v);
            v = V("loup", "loup_rampant", "Loup tordu", "wolf/wolf", R("1A1030", "5E3E8C", "BCA0EC"), 0.95); v.T.Eyes = K.Rgb("7FE8FF"); v.T.Specks = K.Rgb("9AF0FF"); v.T.SpeckRate = 0.05; l.Add(v);
            v = V("loup", "meneur_rampant", "Meneur de la horde", "wolf/wolf", R("0E0820", "46286E", "A486DC"), 0.95); v.T.Eyes = K.Rgb("FF5CE8"); v.T.Specks = K.Rgb("9AF0FF"); v.T.SpeckRate = 0.08; l.Add(v);
            v = V("chien", "chien_fauve", "Chien fauve", "wolf/wolf", R("4E3018", "B8824A", "F4DCB0"), 0.9); l.Add(v);
            v = V("chien", "chien_noir", "Chien noir", "wolf/wolf", R("0C0C0E", "18181C", "2E2E36", "6E6E78"), 0.95); l.Add(v);
            v = V("chien", "chien_gris", "Chien gris", "wolf/wolf", R("32363C", "8A9098", "E0E4E8"), 0.9); l.Add(v);
            v = V("chat", "chat_perle", "Chat perle", "cat/cat_tabby", R("42464E", "9AA0AC", "ECF0F4"), 0.92); v.T.KeepGreen = true; l.Add(v);
            v = V("chat", "chat_nuit", "Chat de nuit", "cat/cat_tabby", R("08080E", "201E2A", "4A465C"), 0.95); v.T.KeepGreen = true; l.Add(v);
            v = V("chat", "chat_caramel", "Chat caramel", "cat/cat_tabby", R("5A3216", "C48A44", "FAE6BC"), 0.92); v.T.KeepGreen = true; l.Add(v);
            v = V("vache", "vache_ardence", "Vache d'Ardence", "cow/cow_temperate", R("3A2212", "B47A40", "FFF4E0"), 0.9); l.Add(v);
            v = V("vache", "vache_morne", "Vache de la Morne", "cow/cow_temperate", R("0C0C10", "32323A", "ECECF0"), 0.95); l.Add(v);
            v = V("cochon", "cochon_tachete", "Cochon tachete", "pig/pig_temperate", R("3E2A24", "C48C7A", "FADCD0"), 0.8); l.Add(v);
            v = V("cochon", "cochon_noir", "Cochon noir", "pig/pig_temperate", R("141018", "4A3E48", "9E8E94"), 0.95); l.Add(v);
            v = V("poule", "poule_rousse", "Poule rousse", "chicken/chicken_temperate", R("562810", "BA6C34", "F2CA92"), 0.9); v.T.KeepRed = true; l.Add(v);
            v = V("poule", "poule_noire", "Poule noire", "chicken/chicken_temperate", R("0C0C0E", "3A3A40", "8E8E94"), 0.95); v.T.KeepRed = true; l.Add(v);
            return l;
        }

        static Tint G(string path, int[] ramp, double strength)
        {
            Tint t = new Tint();
            t.Src = path; t.Dst = path; t.Ramp = ramp; t.Strength = strength;
            return t;
        }

        /// <summary>Textures du jeu remplacees pour tout le serveur (une seule apparence par type de creature).</summary>
        static List<KeyValuePair<string, Tint>> Globals()
        {
            List<KeyValuePair<string, Tint>> l = new List<KeyValuePair<string, Tint>>();
            Tint t;
            t = G("hoglin/hoglin", R("1A120C", "6A4A30", "CCAC84"), 0.92); t.KeepWhite = true; l.Add(P("Sanglier frenetique", t));
            t = G("illager/ravager", R("160E08", "5A3A24", "BC946C"), 0.92); t.KeepWhite = true; l.Add(P("Taureau des prairies", t));
            t = G("spider/spider", R("10140A", "4A5A22", "ACBC64"), 0.9); l.Add(P("Araignee des herbes", t));
            t = G("spider/cave_spider", R("08040E", "2E1A4A", "7E56BC"), 0.92); l.Add(P("Araignee geante", t));
            t = G("silverfish/silverfish", R("102636", "3E9AB8", "CCF6FF"), 0.92); t.Specks = K.Rgb("FFFFFF"); t.SpeckRate = 0.05; l.Add(P("Rampant cristallin", t));
            t = G("endermite/endermite", R("0A0612", "32204E", "9070C4"), 0.92); t.Specks = K.Rgb("C86BFF"); t.SpeckRate = 0.04; l.Add(P("Scorpion noir, Affame du Neant", t));
            t = G("bear/polarbear", R("4E6880", "C4DCEC", "FFFFFF"), 0.6); l.Add(P("Ours des glaces", t));
            t = G("panda/panda_brown", R("3A2614", "5E4024", "86603A"), 1.0); l.Add(P("Ours des collines", t));
            t = G("skeleton/skeleton", R("141E10", "6A8A5A", "DCF0C4"), 0.7); l.Add(P("Squelette maudit", t));
            t = G("skeleton/stray", R("182E42", "6A9AB8", "E4F6FF"), 0.7); l.Add(P("Errant gele", t));
            t = G("skeleton/stray_overlay", R("182E42", "5A8AA8", "D4EEFF"), 0.7); l.Add(P("Errant gele (habits)", t));
            t = G("skeleton/bogged", R("161E0C", "5A6A30", "BCC48C"), 0.6); t.KeepRed = true; l.Add(P("Archer des tourbes", t));
            t = G("skeleton/bogged_overlay", R("161E0C", "4A5A26", "A4AC74"), 0.5); t.KeepRed = true; l.Add(P("Archer des tourbes (habits)", t));
            t = G("zombie/drowned", R("0A1610", "2E4A3A", "8EAC94"), 0.75); l.Add(P("Noye des tourbieres", t));
            t = G("zombie/drowned_outer_layer", R("0A1610", "3A4A2E", "8E9C74"), 0.75); l.Add(P("Noye des tourbieres (habits)", t));
            t = G("skeleton/wither_skeleton", R("080606", "2A2220", "5E4E48"), 0.9); t.Specks = K.Rgb("FF6A1E"); t.SpeckRate = 0.06; l.Add(P("Brute calcinee", t));
            t = G("iron_golem/iron_golem", R("0A1020", "1E2E54", "5E7EBC"), 0.92); t.GreenTo = R("7A5F22", "C9A24A", "FFE08A"); t.Eyes = K.Rgb("7FD4FF"); l.Add(P("Gardien du Pilier", t));
            return l;
        }

        static KeyValuePair<string, Tint> P(string label, Tint t) { return new KeyValuePair<string, Tint>(label, t); }

        static Img FromJar(ZipArchive zip, string path)
        {
            ZipArchiveEntry e = zip.GetEntry("assets/minecraft/textures/entity/" + path + ".png");
            if (e == null) return null;
            using (Stream s = e.Open())
            using (MemoryStream ms = new MemoryStream())
            {
                s.CopyTo(ms);
                ms.Position = 0;
                return Img.Load(ms);
            }
        }

        // ------------------------------------------------------------------ Apercus

        /// <summary>Vue de face (ou de dos) d'un personnage, 16 x 32.</summary>
        public static Img View(Skin s, bool back)
        {
            Img o = new Img(16, 32);
            char f = back ? 'K' : 'F';
            string[][] layers =
            {
                new string[] { "head", "body", "rarm", "larm", "rleg", "lleg" },
                new string[] { "hat", "jacket", "rsleeve", "lsleeve", "rpants", "lpants" }
            };
            int[][] at = { new int[] { 4, 0 }, new int[] { 4, 8 }, new int[] { 0, 8 }, new int[] { 12, 8 }, new int[] { 4, 20 }, new int[] { 8, 20 } };
            for (int layer = 0; layer < 2; layer++)
            {
                if (layer == 1 && s.Armor)
                {
                    // Une tenue n'a qu'une couche, plus la coiffe.
                    Blit(s, o, "hat", f, 4, 0, back, false);
                    break;
                }
                for (int i = 0; i < 6; i++)
                {
                    bool mirror = s.Armor && (i == 3 || i == 5);
                    int px = at[i][0];
                    if (back && i >= 2) px = i == 2 ? 12 : (i == 3 ? 0 : (i == 4 ? 8 : 4));
                    Blit(s, o, layers[layer][i], f, px, at[i][1], back, mirror);
                }
            }
            return o;
        }

        static void Blit(Skin s, Img o, string part, char f, int dx, int dy, bool back, bool mirror)
        {
            int fx, fy, fw, fh;
            s.Part(part).Face(f, out fx, out fy, out fw, out fh);
            for (int y = 0; y < fh; y++)
                for (int x = 0; x < fw; x++)
                {
                    int c = s.I.Get(fx + (mirror ? fw - 1 - x : x), fy + y);
                    if (((c >> 24) & 255) > 0) o.Set(dx + x, dy + y, c);
                }
        }

        static void Sheet(string path, List<KeyValuePair<string, Img[]>> cells, int scale, int cols)
        {
            if (cells.Count == 0) return;
            int cw = 0, total = 0;
            int rows = (cells.Count + cols - 1) / cols;
            int[] rowH = new int[rows], rowY = new int[rows];
            for (int n = 0; n < cells.Count; n++)
            {
                int w = 0, h = 0;
                foreach (Img i in cells[n].Value) { w += i.W * scale + 6; h = Math.Max(h, i.H * scale); }
                cw = Math.Max(cw, w + 10);
                rowH[n / cols] = Math.Max(rowH[n / cols], h + 26);
            }
            for (int r = 0; r < rows; r++) { rowY[r] = total; total += rowH[r]; }
            cw = Math.Max(cw, 130);
            using (Bitmap b = new Bitmap(cols * cw, total, PixelFormat.Format32bppArgb))
            using (Graphics g = Graphics.FromImage(b))
            using (Font font = new Font("Segoe UI", 8f))
            using (SolidBrush txt = new SolidBrush(Color.FromArgb(255, 232, 224, 200)))
            {
                g.Clear(Color.FromArgb(255, 22, 30, 52));
                g.InterpolationMode = System.Drawing.Drawing2D.InterpolationMode.NearestNeighbor;
                g.PixelOffsetMode = System.Drawing.Drawing2D.PixelOffsetMode.Half;
                for (int n = 0; n < cells.Count; n++)
                {
                    int ox = (n % cols) * cw + 6, oy = rowY[n / cols] + 4, ch = rowH[n / cols];
                    int x = ox;
                    foreach (Img i in cells[n].Value)
                    {
                        using (Bitmap part = i.ToBitmap()) g.DrawImage(part, x, oy, i.W * scale, i.H * scale);
                        x += i.W * scale + 6;
                    }
                    g.DrawString(cells[n].Key, font, txt, ox - 2, oy + ch - 24);
                }
                Directory.CreateDirectory(Path.GetDirectoryName(path));
                b.Save(path, ImageFormat.Png);
            }
        }

        // ------------------------------------------------------------------ Tout ecrire

        static void Text(string path, string text)
        {
            Directory.CreateDirectory(Path.GetDirectoryName(path));
            File.WriteAllText(path, text.Replace("\r\n", "\n"), new UTF8Encoding(false));
        }

        public static string Run(string clientJar, string packDir, string resDir, string docDir)
        {
            StringBuilder looks = new StringBuilder();
            StringBuilder index = new StringBuilder();
            string tex = Path.Combine(packDir, "assets", "orbitmmo", "textures", "entity");
            string dp = Path.Combine(resDir, "datapack");
            if (Directory.Exists(dp)) Directory.Delete(dp, true);

            // PNJ : skins de joueur.
            List<KeyValuePair<string, Img[]>> cells = new List<KeyValuePair<string, Img[]>>();
            List<Look> npcs = Npcs();
            foreach (Look k in npcs)
            {
                Skin s = Paint(k);
                s.I.Save(Path.Combine(tex, "npc", k.Id + ".png"));
                looks.Append("skin ").Append(k.Id).Append('\n');
                cells.Add(new KeyValuePair<string, Img[]>(k.Id, new Img[] { View(s, false), View(s, true) }));
            }
            Sheet(Path.Combine(docDir, "pnj.png"), cells, 4, 8);

            // Monstres humanoides : tenues completes.
            cells = new List<KeyValuePair<string, Img[]>>();
            List<Look> outfits = Outfits();
            foreach (Look k in outfits)
            {
                Skin s = Paint(k);
                s.I.Save(Path.Combine(tex, "equipment", "humanoid", k.Id + ".png"));
                s.I.Save(Path.Combine(tex, "equipment", "humanoid_leggings", k.Id + ".png"));
                Text(Path.Combine(packDir, "assets", "orbitmmo", "equipment", k.Id + ".json"),
                    "{\n  \"layers\": {\n    \"humanoid\": [ { \"texture\": \"orbitmmo:" + k.Id + "\" } ],\n    \"humanoid_leggings\": [ { \"texture\": \"orbitmmo:"
                    + k.Id + "\" } ]\n  }\n}\n");
                looks.Append("tenue ").Append(k.Id).Append('\n');
                cells.Add(new KeyValuePair<string, Img[]>(k.Id, new Img[] { View(s, false), View(s, true) }));
            }
            Sheet(Path.Combine(docDir, "monstres.png"), cells, 4, 6);

            // Betes.
            cells = new List<KeyValuePair<string, Img[]>>();
            int done = 0;
            using (ZipArchive zip = ZipFile.OpenRead(clientJar))
            {
                foreach (Variant v in Variants())
                {
                    string folder = v.Src.Substring(0, v.Src.IndexOf('/'));
                    string[] forms = folder == "wolf" ? new string[] { "", "_angry", "_tame" } : new string[] { "" };
                    Img shown = null;
                    foreach (string form in forms)
                    {
                        foreach (string age in new string[] { "", "_baby" })
                        {
                            Img src = FromJar(zip, v.Src + form + age);
                            if (src == null) throw new Exception("Texture introuvable dans le jar du jeu : " + v.Src + form + age);
                            Tint t = v.T;
                            int eyes = t.Eyes;
                            if (form != "_angry") t.Eyes = 0;
                            Img o = Recolor(src, t, v.Id.Length * 31 + form.Length);
                            t.Eyes = eyes;
                            o.Save(Path.Combine(tex, folder, v.Id + form + age + ".png"));
                            if (form == (folder == "wolf" && v.Kind == "loup" ? "_angry" : "") && age == "") shown = o;
                        }
                    }
                    string a = "orbitmmo:entity/" + folder + "/" + v.Id;
                    string json;
                    string reg;
                    if (folder == "wolf")
                    {
                        reg = "wolf_variant";
                        json = "{\n  \"assets\": { \"angry\": \"" + a + "_angry\", \"tame\": \"" + a + "_tame\", \"wild\": \"" + a + "\" },\n"
                            + "  \"baby_assets\": { \"angry\": \"" + a + "_angry_baby\", \"tame\": \"" + a + "_tame_baby\", \"wild\": \"" + a + "_baby\" },\n"
                            + "  \"spawn_conditions\": []\n}\n";
                    }
                    else
                    {
                        reg = folder + "_variant";
                        json = "{\n  \"asset_id\": \"" + a + "\",\n  \"baby_asset_id\": \"" + a + "_baby\",\n  \"spawn_conditions\": []\n}\n";
                    }
                    string rel = "data/orbitmmo/" + reg + "/" + v.Id + ".json";
                    Text(Path.Combine(dp, rel.Replace('/', Path.DirectorySeparatorChar)), json);
                    index.Append(rel).Append('\n');
                    looks.Append(v.Kind).Append(' ').Append(v.Id).Append('\n');
                    cells.Add(new KeyValuePair<string, Img[]>(v.Id, new Img[] { shown }));
                    done++;
                }
                foreach (KeyValuePair<string, Tint> g in Globals())
                {
                    Img src = FromJar(zip, g.Value.Src);
                    if (src == null) throw new Exception("Texture introuvable dans le jar du jeu : " + g.Value.Src);
                    Img o = Recolor(src, g.Value, g.Value.Src.Length * 17);
                    o.Save(Path.Combine(packDir, "assets", "minecraft", "textures", "entity", g.Value.Dst.Replace('/', Path.DirectorySeparatorChar) + ".png"));
                    cells.Add(new KeyValuePair<string, Img[]>(g.Key, new Img[] { o }));
                    done++;
                }
            }
            Sheet(Path.Combine(docDir, "betes.png"), cells, 2, 5);

            Text(Path.Combine(dp, "pack.mcmeta"),
                "{\n  \"pack\": {\n    \"description\": \"OrbitMMO - variantes d'animaux\",\n    \"min_format\": 121,\n    \"max_format\": 121\n  }\n}\n");
            index.Insert(0, "pack.mcmeta\n");
            Text(Path.Combine(dp, "index.txt"), index.ToString());
            Text(Path.Combine(resDir, "looks.txt"), looks.ToString());
            return npcs.Count + " skins de PNJ, " + outfits.Count + " tenues de monstres, " + done + " textures de betes.";
        }
    }
}
