// Tenues 3D d'Orbit MMO (tools/gen-outfits.ps1) : pour chaque tenue,
//  - couche du corps peinte en HD (equipment humanoid + humanoid_leggings, 8x : 512 x 256 au lieu de 64 x 32) ;
//  - casque en vrai modele 3D, porte sur la tete comme une citrouille (contexte "head") ;
//  - pieces qui depassent (epaules, cape) en modeles 3D poses sur des ItemDisplay par le plugin ;
//  - definition d'objet du casque : modele 3D sur la tete, icone HD de l'armurerie (gen-items.ps1) ailleurs ;
//  - apercus PNG (face, dos, profil) du mannequin habille, pour controler l'alignement hors du jeu.
// Toutes les textures sont peintes face par face a PPU px par unite de modele (8 = 8x le jeu).
// Reperes des pieces du corps (epaules, cape) : pivot (8,8,8) = haut du torse au milieu du cou ; avant = +Z ;
// gauche du joueur = +X ; 1 unite = 1 pixel du modele du joueur (le plugin applique l'echelle 0,9375 du joueur).
// Reperes du casque : centre (8,8,8) = centre de la tete ; avant = -Z (nord, comme la citrouille) ;
// +X = droite du joueur ; la tete occupe 4..12 sur les trois axes (affichage "head" a l'echelle 1,6).
// C# 5 (compile par Add-Type de PowerShell 5.1) : pas d'interpolation de chaines ni de "=>".
using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
using System.Globalization;
using System.IO;
using System.Linq;
using System.Text;

public static class OutfitHd
{
    const int PPU = 8;
    static readonly CultureInfo Inv = CultureInfo.InvariantCulture;
    static readonly UTF8Encoding Utf8 = new UTF8Encoding(false);

    // ------------------------------------------------------------------ Image en memoire

    public class Img
    {
        public int W, H;
        public double[] R, G, B, A;
        public Img(int w, int h) { W = w; H = h; R = new double[w * h]; G = new double[w * h]; B = new double[w * h]; A = new double[w * h]; }
        public void Set(int x, int y, double r, double g, double b, double a)
        {
            if (x < 0 || y < 0 || x >= W || y >= H) return;
            int i = y * W + x;
            R[i] = Clamp(r); G[i] = Clamp(g); B[i] = Clamp(b); A[i] = Clamp(a);
        }
        public void Blend(int x, int y, double r, double g, double b, double a)
        {
            if (x < 0 || y < 0 || x >= W || y >= H || a <= 0) return;
            int i = y * W + x;
            double oa = A[i] / 255.0, na = a / 255.0;
            double outA = na + oa * (1 - na);
            if (outA <= 0) return;
            R[i] = Clamp((r * na + R[i] * oa * (1 - na)) / outA);
            G[i] = Clamp((g * na + G[i] * oa * (1 - na)) / outA);
            B[i] = Clamp((b * na + B[i] * oa * (1 - na)) / outA);
            A[i] = Clamp(outA * 255);
        }
        public Bitmap ToBitmap()
        {
            Bitmap bmp = new Bitmap(W, H, PixelFormat.Format32bppArgb);
            BitmapData d = bmp.LockBits(new Rectangle(0, 0, W, H), ImageLockMode.WriteOnly, PixelFormat.Format32bppArgb);
            int[] px = new int[W * H];
            for (int i = 0; i < px.Length; i++)
            {
                px[i] = ((int)Math.Round(A[i]) << 24) | (Q(R[i]) << 16) | (Q(G[i]) << 8) | Q(B[i]);
            }
            System.Runtime.InteropServices.Marshal.Copy(px, 0, d.Scan0, px.Length);
            bmp.UnlockBits(d);
            return bmp;
        }
        public void Save(string path)
        {
            Directory.CreateDirectory(Path.GetDirectoryName(path));
            using (Bitmap b = ToBitmap()) { b.Save(path, ImageFormat.Png); }
        }
    }

    static double Clamp(double v) { return v < 0 ? 0 : v > 255 ? 255 : v; }
    // Couleurs arrondies au multiple de 5 : memes textures a l'oeil, PNG nettement plus legers.
    static int Q(double v) { int q = (int)Math.Round(v / 5.0) * 5; return q > 255 ? 255 : q < 0 ? 0 : q; }

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

    // ------------------------------------------------------------------ Materiaux

    // Palette de la tenue en cours (remplie par SetOutfit) : role -> couleur.
    static readonly Dictionary<string, int[]> Colors = new Dictionary<string, int[]>();
    static bool CapeBorder = true, CapeEmblem = true, VisorGlow;

    static int[] ColorOf(string mat)
    {
        if (mat == "visor") return Colors["steel"];
        if (mat == "cape_back") return Colors["cloth"];
        int[] c;
        return Colors.TryGetValue(mat, out c) ? c : Colors["steel"];
    }

    static bool IsMetal(string mat) { return mat == "steel" || mat == "steel_dark" || mat == "gold" || mat == "visor"; }
    static bool IsHide(string mat) { return mat == "leather" || mat == "sole" || mat == "hide" || mat == "hide_dark"; }
    static bool IsCloth(string mat) { return mat == "cloth" || mat == "cape_back" || mat == "lining" || mat == "fabric" || mat == "fabric_dark"; }

    // Peint une face (w x h px) avec son materiau. face = north/south/east/west/up/down ("side" pour une zone de peau).
    static void PaintFace(Img im, int x0, int y0, int w, int h, string mat, string face, int seed)
    {
        seedG = seed;
        int[] c = ColorOf(mat);
        bool top = face == "up", bottom = face == "down";
        double bw = Math.Max(1.0, PPU * 0.4);
        for (int j = 0; j < h; j++)
        {
            for (int i = 0; i < w; i++)
            {
                double fx = w > 1 ? i / (double)(w - 1) : 0.5, fy = h > 1 ? j / (double)(h - 1) : 0.5;
                double r = c[0], g = c[1], b = c[2], s = 1;
                if (IsMetal(mat))
                {
                    s = top ? 1.1 : bottom ? 0.78 : 1.12 - 0.26 * fy;
                    s += (Hash(j, 17) - 0.5) * 0.07 + (Hash(i / 2 * 7 + j * 131, 3) - 0.5) * 0.03; // acier brosse
                    double d = fx * 0.55 + fy * 0.45;
                    s += Math.Exp(-((d - 0.3) * (d - 0.3)) / 0.005) * (mat == "gold" ? 0.22 : 0.16); // reflet
                    if (mat == "gold" && h >= 3 * PPU && Math.Abs(j - h / 2.0) < 1.0 && (i / 3) % 2 == 0) s *= 0.72; // gravure
                }
                else if (IsHide(mat))
                {
                    s = 1.06 - 0.16 * fy + (Fbm(i / 5.0, j / 5.0) - 0.5) * 0.3;
                }
                else if (IsCloth(mat))
                {
                    s = 1.04 - 0.14 * fy + (((i / 2) + (j / 2)) % 2) * 0.05 + (Hash(i, j) - 0.5) * 0.05;
                    s *= 0.9 + 0.1 * Math.Sin(fx * Math.PI * 5 + seed);
                }
                else if (mat == "plume")
                {
                    s = 0.78 + 0.42 * Hash(i / 2, 5) - 0.18 * fy;
                }
                else if (mat == "mail")
                {
                    // Anneaux d'un pixel du modele (8 texels), rangs decales qui se chevauchent, bords adoucis et fond
                    // peu contraste. L'ancien motif (anneaux de 4 texels sur fond presque noir) moirait en jeu : a
                    // distance normale un texel vaut a peu pres un pixel d'ecran et les textures d'entite n'ont pas
                    // de mipmap.
                    int cw = 8, rh = 6;
                    double best = 0, lightY = 0;
                    int row = (int)Math.Floor(j / (double)rh);
                    for (int rr = row - 1; rr <= row + 1; rr++)
                    {
                        double oy = rr * rh + rh / 2.0, shift = (rr & 1) == 0 ? 0 : cw / 2.0;
                        double ox = Math.Floor((i + 0.5 - shift) / cw) * cw + cw / 2.0 + shift;
                        for (int k = -1; k <= 1; k++)
                        {
                            double ddx = i + 0.5 - (ox + k * cw), ddy = j + 0.5 - oy;
                            double dd = Math.Sqrt(ddx * ddx + ddy * ddy);
                            double cov = Math.Max(0, Math.Min(1, 1.6 - Math.Abs(dd - 2.9)));
                            if (cov > best) { best = cov; lightY = ddy / Math.Max(dd, 0.001); }
                        }
                    }
                    s = 0.52 + (1.04 - 0.2 * lightY - 0.52) * best;
                }
                else if (mat == "black")
                {
                    s = 1 + (Hash(i, j) - 0.5) * 0.2;
                }
                else if (mat == "fur")
                {
                    s = 0.74 + 0.44 * Hash(i / 2, j / 5) + 0.1 * Hash(i, j) - 0.14 * fy;
                }
                else if (mat == "glow")
                {
                    double gx = fx - 0.5, gy = fy - 0.5, gd = Math.Sqrt(gx * gx + gy * gy);
                    s = 1.3 - 0.9 * gd;
                    if (gd < 0.16) { r = 255; g = 255; b = 255; s = 1; }
                }
                r *= s; g *= s; b *= s;

                // Bordure du materiau de la cape : galon d'or sur les cotes et le bas.
                if (mat == "cape_back" && CapeBorder)
                {
                    double edge = Math.Min(Math.Min(i, w - 1 - i), h - 1 - j);
                    if (edge < PPU * 0.75 || (edge >= PPU * 1.1 && edge < PPU * 1.3))
                    {
                        int[] gc = Colors["gold"];
                        double gs = (edge < PPU * 0.75 ? 1.05 : 0.8) - 0.15 * fy + (Hash(i, j * 3) - 0.5) * 0.06;
                        r = gc[0] * gs; g = gc[1] * gs; b = gc[2] * gs;
                    }
                }

                // Biseau (haut et gauche eclaires, bas et droite dans l'ombre) et liseré sombre.
                if (IsMetal(mat) || mat == "leather" || mat == "hide" || mat == "hide_dark" || mat == "glow")
                {
                    double tl = Math.Min(i, j), br = Math.Min(w - 1 - i, h - 1 - j);
                    if (tl < bw && tl <= br) { r += 34; g += 34; b += 32; }
                    else if (br < bw) { r *= 0.74; g *= 0.74; b *= 0.76; }
                    if (i == 0 || j == 0 || i == w - 1 || j == h - 1) { r *= 0.62; g *= 0.62; b *= 0.66; }
                }
                im.Set(x0 + i, y0 + j, r, g, b, 255);
            }
        }
        if (mat == "visor") PaintVisor(im, x0, y0, w, h);
        if (mat == "cape_back" && CapeEmblem && w >= 6 * PPU && h >= 10 * PPU) PaintEmblem(im, x0 + w / 2.0, y0 + h * 0.34, w * 0.27);
        if (IsMetal(mat) && mat != "gold" && w >= 3 * PPU && h >= 3 * PPU && (face == "north" || face == "south" || face == "east" || face == "west"))
        {
            double inset = PPU * 0.9;
            Rivet(im, x0 + inset, y0 + inset, PPU * 0.42);
            Rivet(im, x0 + w - inset, y0 + inset, PPU * 0.42);
        }
    }

    // Visiere : fente des yeux (pont central) et trous d'aeration sur le bas du masque.
    static void PaintVisor(Img im, int x0, int y0, int w, int h)
    {
        int ya = y0 + (int)(h * 0.16), yb = y0 + (int)(h * 0.27);
        for (int y = ya; y <= yb; y++)
        {
            for (int x = x0 + (int)(w * 0.08); x < x0 + w * 0.92; x++)
            {
                double fx = (x - x0) / (double)w;
                if (Math.Abs(fx - 0.5) < 0.05) continue;
                bool lip = y == ya;
                if (lip) im.Set(x, y, 60, 64, 72, 255);
                else if (VisorGlow) { int[] gl = Colors["glow"]; im.Set(x, y, gl[0], gl[1], gl[2], 255); }
                else im.Set(x, y, 10, 10, 14, 255);

            }
            if (y == yb) for (int x = x0 + (int)(w * 0.08); x < x0 + w * 0.92; x++) im.Set(x, y + 1, 210, 218, 228, 255);
        }
        // Arete verticale du masque.
        for (int y = yb + 2; y < y0 + h - 2; y++)
        {
            int cx = x0 + w / 2;
            im.Set(cx - 1, y, 214, 222, 232, 255);
            im.Set(cx, y, 120, 128, 140, 255);
        }
        // Trous d'aeration.
        double r = PPU * 0.22;
        for (int row = 0; row < 3; row++)
        {
            double cy = y0 + h * (0.5 + row * 0.12);
            foreach (double f in new[] { 0.2, 0.3, 0.7, 0.8 })
            {
                double cx = x0 + w * f;
                for (int y = (int)(cy - r - 1); y <= cy + r + 1; y++)
                    for (int x = (int)(cx - r - 1); x <= cx + r + 1; x++)
                    {
                        double d = Math.Sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy));
                        if (d <= r) im.Set(x, y, 12, 12, 16, 255);
                        else if (d <= r + 1 && y > cy) im.Set(x, y, 205, 214, 224, 255);
                    }
            }
        }
    }

    // Embleme d'ORBIT sur le dos de la cape : anneau d'orbite incline autour d'une etoile a 4 branches.
    static void PaintEmblem(Img im, double cx, double cy, double rad)
    {
        int[] gc = Colors["gold"];
        for (int y = (int)(cy - rad * 1.3); y <= cy + rad * 1.3; y++)
        {
            for (int x = (int)(cx - rad * 1.4); x <= cx + rad * 1.4; x++)
            {
                double dx = (x - cx) / rad, dy = (y - cy) / rad;
                // Anneau : ellipse inclinee.
                double ca = Math.Cos(-0.45), sa = Math.Sin(-0.45);
                double ex = dx * ca - dy * sa, ey = dx * sa + dy * ca;
                double e = Math.Sqrt(ex * ex / 1.25 + ey * ey / 0.18);
                bool ring = Math.Abs(e - 1) < 0.11;
                // Etoile : |x|^0.5 + |y|^0.5 < 1 (astroide).
                double st = Math.Pow(Math.Abs(dx) / 0.75, 0.5) + Math.Pow(Math.Abs(dy) / 0.75, 0.5);
                bool star = st < 1;
                if (star || ring)
                {
                    double s = star ? 1.12 - 0.3 * (st) : 0.95 - 0.2 * dy;
                    im.Set(x, y, gc[0] * s, gc[1] * s, gc[2] * s, 255);
                }
                else if (st < 1.12 || Math.Abs(e - 1) < 0.17)
                {
                    int[] cc = Colors["cloth"]; im.Set(x, y, cc[0] * 0.45, cc[1] * 0.45, cc[2] * 0.45, 255); // liseré sombre
                }
            }
        }
    }

    static void Rivet(Img im, double cx, double cy, double r)
    {
        int[] gc = Colors["gold"];
        for (int y = (int)(cy - r - 2); y <= cy + r + 2; y++)
            for (int x = (int)(cx - r - 2); x <= cx + r + 2; x++)
            {
                double dx = x - cx, dy = y - cy, d = Math.Sqrt(dx * dx + dy * dy);
                if (d <= r)
                {
                    double s = 1.15 - 0.35 * ((dx + dy) / (2 * r) + 0.5);
                    if (dx * dx + dy * dy < r * r * 0.15 && dx < 0 && dy < 0) s = 1.5;
                    im.Set(x, y, gc[0] * s, gc[1] * s, gc[2] * s, 255);
                }
                else if (d <= r + 1.2 && dy > 0) im.Blend(x, y, 20, 20, 26, 150);
            }
    }

    // ------------------------------------------------------------------ Modeles en cubes

    public class Cube
    {
        public double X1, Y1, Z1, X2, Y2, Z2;
        public string Mat;
        public Dictionary<string, string> Faces = new Dictionary<string, string>();
        public char Axis = ' ';
        public double Angle, OX, OY, OZ;
        public Cube Rot(char axis, double angle, double ox, double oy, double oz) { Axis = axis; Angle = angle; OX = ox; OY = oy; OZ = oz; return this; }
        public Cube Face(string face, string mat) { Faces[face] = mat; return this; }
        public string MatOf(string face) { string m; return Faces.TryGetValue(face, out m) ? m : Mat; }
        public Cube Mirror()
        {
            Cube c = new Cube { X1 = 16 - X2, X2 = 16 - X1, Y1 = Y1, Y2 = Y2, Z1 = Z1, Z2 = Z2, Mat = Mat, Axis = Axis, Angle = Axis == 'x' ? Angle : -Angle, OX = 16 - OX, OY = OY, OZ = OZ };
            foreach (KeyValuePair<string, string> kv in Faces)
            {
                string f = kv.Key == "east" ? "west" : kv.Key == "west" ? "east" : kv.Key;
                c.Faces[f] = kv.Value;
            }
            return c;
        }
    }

    public class Model
    {
        public string Name;
        public List<Cube> Cubes = new List<Cube>();
        public string Display = "";
        public Cube Add(double x1, double y1, double z1, double x2, double y2, double z2, string mat)
        {
            Cube c = new Cube { X1 = x1, Y1 = y1, Z1 = z1, X2 = x2, Y2 = y2, Z2 = z2, Mat = mat };
            Cubes.Add(c);
            return c;
        }
        // Cube donne par rapport au pivot (8,8,8).
        public Cube Rel(double x1, double y1, double z1, double x2, double y2, double z2, string mat)
        {
            return Add(x1 + 8, y1 + 8, z1 + 8, x2 + 8, y2 + 8, z2 + 8, mat);
        }
    }

    static readonly string[] FaceNames = { "north", "south", "east", "west", "up", "down" };

    static double[] FaceSize(Cube c, string f)
    {
        if (f == "north" || f == "south") return new[] { c.X2 - c.X1, c.Y2 - c.Y1 };
        if (f == "east" || f == "west") return new[] { c.Z2 - c.Z1, c.Y2 - c.Y1 };
        return new[] { c.X2 - c.X1, c.Z2 - c.Z1 };
    }

    class Slot { public Cube C; public string F; public int W, H, X, Y; }

    // Peint toutes les faces dans un atlas (rangees, 2 px de marge recopiee) et ecrit modele + texture.
    // Renvoie l'atlas (pour les apercus) ; uv[cube][face] = rectangle en px.
    static Img BuildModel(string packDir, string outfit, Model m, Dictionary<Cube, Dictionary<string, Rectangle>> uvOut)
    {
        List<Slot> slots = new List<Slot>();
        foreach (Cube c in m.Cubes)
            foreach (string f in FaceNames)
            {
                double[] sz = FaceSize(c, f);
                slots.Add(new Slot { C = c, F = f, W = Math.Max(2, (int)Math.Round(sz[0] * PPU)), H = Math.Max(2, (int)Math.Round(sz[1] * PPU)) });
            }
        int size = 256;
        while (!Pack(slots, size)) size *= 2;
        Img im = new Img(size, size);
        int seed = 1;
        foreach (Slot s in slots)
        {
            PaintFace(im, s.X, s.Y, s.W, s.H, s.C.MatOf(s.F), s.F, seed++);
            Gutter(im, s.X, s.Y, s.W, s.H);
            if (!uvOut.ContainsKey(s.C)) uvOut[s.C] = new Dictionary<string, Rectangle>();
            uvOut[s.C][s.F] = new Rectangle(s.X, s.Y, s.W, s.H);
        }
        string rel = "outfit/" + outfit + "/" + m.Name;
        im.Save(Path.Combine(packDir, "assets", "orbitmmo", "textures", "item", rel.Replace('/', Path.DirectorySeparatorChar) + ".png"));

        StringBuilder sb = new StringBuilder();
        sb.Append("{\n  \"textures\": {\"0\": \"orbitmmo:item/" + rel + "\", \"particle\": \"orbitmmo:item/" + rel + "\"},\n");
        if (m.Display.Length > 0) sb.Append("  \"display\": " + m.Display + ",\n");
        sb.Append("  \"elements\": [\n");
        for (int k = 0; k < m.Cubes.Count; k++)
        {
            Cube c = m.Cubes[k];
            sb.Append("    {\"from\": [" + N(c.X1) + ", " + N(c.Y1) + ", " + N(c.Z1) + "], \"to\": [" + N(c.X2) + ", " + N(c.Y2) + ", " + N(c.Z2) + "]");
            if (c.Axis != ' ') sb.Append(", \"rotation\": {\"angle\": " + N(c.Angle) + ", \"axis\": \"" + c.Axis + "\", \"origin\": [" + N(c.OX) + ", " + N(c.OY) + ", " + N(c.OZ) + "]}");
            sb.Append(", \"faces\": {");
            for (int f = 0; f < 6; f++)
            {
                Rectangle r = uvOut[c][FaceNames[f]];
                double sc = 16.0 / size;
                sb.Append((f > 0 ? ", " : "") + "\"" + FaceNames[f] + "\": {\"uv\": [" + N(r.X * sc) + ", " + N(r.Y * sc) + ", " + N((r.X + r.Width) * sc) + ", " + N((r.Y + r.Height) * sc) + "], \"texture\": \"#0\"}");
            }
            sb.Append("}}" + (k < m.Cubes.Count - 1 ? "," : "") + "\n");
        }
        sb.Append("  ]\n}\n");
        Text(Path.Combine(packDir, "assets", "orbitmmo", "models", "outfit", outfit, m.Name + ".json"), sb.ToString());
        Text(Path.Combine(packDir, "assets", "orbitmmo", "items", "outfit", outfit, m.Name + ".json"),
            "{\n  \"model\": {\"type\": \"minecraft:model\", \"model\": \"orbitmmo:outfit/" + outfit + "/" + m.Name + "\"}\n}\n");
        return im;
    }

    static bool Pack(List<Slot> slots, int size)
    {
        int x = 2, y = 2, rowH = 0;
        foreach (Slot s in slots.OrderByDescending(t => t.H).ThenByDescending(t => t.W))
        {
            if (x + s.W + 2 > size) { x = 2; y += rowH + 4; rowH = 0; }
            if (y + s.H + 2 > size) return false;
            s.X = x; s.Y = y;
            x += s.W + 4;
            rowH = Math.Max(rowH, s.H);
        }
        return true;
    }

    // Recopie le bord d'une face sur 2 px autour (evite les liserés au filtrage et aux niveaux de mipmap).
    static void Gutter(Img im, int x0, int y0, int w, int h)
    {
        for (int k = 1; k <= 2; k++)
        {
            for (int i = -k; i < w + k; i++)
            {
                Copy(im, x0 + Math.Max(0, Math.Min(w - 1, i)), y0, x0 + i, y0 - k);
                Copy(im, x0 + Math.Max(0, Math.Min(w - 1, i)), y0 + h - 1, x0 + i, y0 + h - 1 + k);
            }
            for (int j = -k; j < h + k; j++)
            {
                Copy(im, x0, y0 + Math.Max(0, Math.Min(h - 1, j)), x0 - k, y0 + j);
                Copy(im, x0 + w - 1, y0 + Math.Max(0, Math.Min(h - 1, j)), x0 + w - 1 + k, y0 + j);
            }
        }
    }

    static void Copy(Img im, int sx, int sy, int dx, int dy)
    {
        if (sx < 0 || sy < 0 || sx >= im.W || sy >= im.H || dx < 0 || dy < 0 || dx >= im.W || dy >= im.H) return;
        int s = sy * im.W + sx, d = dy * im.W + dx;
        im.R[d] = im.R[s]; im.G[d] = im.G[s]; im.B[d] = im.B[s]; im.A[d] = im.A[s];
    }

    static string N(double v) { return Math.Round(v, 4).ToString("0.####", Inv); }

    static void Text(string path, string text)
    {
        Directory.CreateDirectory(Path.GetDirectoryName(path));
        File.WriteAllText(path, text, Utf8);
    }

    // ------------------------------------------------------------------ Les 32 tenues : 4 types x 8 paliers
    // Nom d'une tenue = apparence des pieces posee par Loot (cle orbitmmo:outfit) : "<type>_<palier>".
    // Paliers : recrue, cuivre, acier, electrum, cristal, obsidienne, astralite, orbitium (lin ... tisse-orbite pour le
    // tissu, peau ... cuir d'orbitium pour le cuir). Les couleurs suivent les icones de l'armurerie (ArmorIcons.cs,
    // une icone pour deux paliers) ; le palier impair d'une paire est plus orne que le pair.

    static readonly string[] TYPES = { "tissu", "cuir", "mailles", "plaques" };

    // [type][palier] = principal, galon, etoffe (cape, tabard, ceinture), plumet, lueur (0 = pas de lueur).
    static readonly int[][][] PALS = new int[][][] {
        new int[][] { // tissu
            new int[] { 0xD8CBB0, 0x8E7F62, 0x6E5A3C, 0x8E7F62, 0 },
            new int[] { 0xC8B894, 0x4E6FB8, 0x35508F, 0x4E6FB8, 0 },
            new int[] { 0x4E6FB8, 0xC9D2E0, 0x26386B, 0xC9D2E0, 0 },
            new int[] { 0x3E5FA8, 0xE0C870, 0x1F2F60, 0xE0C870, 0 },
            new int[] { 0x5B3A99, 0x9AD8FF, 0x2A1A4D, 0x9AD8FF, 0x9AD8FF },
            new int[] { 0x3A2466, 0xE0B84E, 0x1A1030, 0xE0B84E, 0xD6A8FF },
            new int[] { 0xE8E2F5, 0xE0B84E, 0x5B3A99, 0xE0B84E, 0xB98CFF },
            new int[] { 0xF6F2FF, 0xE0B84E, 0x1B1140, 0xE0B84E, 0xB98CFF } },
        new int[][] { // cuir
            new int[] { 0xA8784A, 0x6B4A2B, 0x8A7A5A, 0xC8B490, 0 },
            new int[] { 0x8E5E36, 0xB89468, 0x6A6A4A, 0xC8B490, 0 },
            new int[] { 0x7A4E2C, 0xB8BEC6, 0x3E4A3A, 0xD8DDE4, 0 },
            new int[] { 0x6B5A3A, 0xE0C870, 0x2A5E4E, 0xE8E0C0, 0 },
            new int[] { 0x3E4A2E, 0x4FC9B8, 0x24403A, 0x8AFFE8, 0x8AFFE8 },
            new int[] { 0x26302A, 0x4FC9B8, 0x121A18, 0x4FC9B8, 0x4FC9B8 },
            new int[] { 0x6E2E2E, 0xE0B84E, 0x2A1418, 0xE0B84E, 0xFFD27A },
            new int[] { 0x5A2030, 0xFFD27A, 0x1B1140, 0xFFD27A, 0xB98CFF } },
        new int[][] { // mailles
            new int[] { 0x7E848C, 0x8A5A32, 0x6A5A46, 0x8A5A32, 0 },
            new int[] { 0xB07A48, 0xD9A860, 0x7A3A2A, 0xA83A26, 0 },
            new int[] { 0xA8B3BF, 0xC99A4A, 0x2C4A7A, 0xA81E26, 0 },
            new int[] { 0xD8D2B0, 0xE0B84E, 0x2A5E4E, 0xF0EDE0, 0 },
            new int[] { 0x9FC4DC, 0xE6F4FF, 0x2A4A8C, 0x7FE4FF, 0x7FE4FF },
            new int[] { 0x4A4F5C, 0x4FA3FF, 0x161A2A, 0x4FA3FF, 0x4FA3FF },
            new int[] { 0xD9DEE6, 0x9B6BFF, 0x3A2466, 0x9B6BFF, 0xD6B8FF },
            new int[] { 0xF4F0FA, 0xE0B84E, 0x1B1140, 0xE0B84E, 0xB98CFF } },
        new int[][] { // plaques
            new int[] { 0x9A6A3C, 0x6B4A2B, 0x5A4632, 0x7A5A3A, 0 },
            new int[] { 0xB87333, 0xE0B070, 0x7A2E22, 0xA83A26, 0 },
            new int[] { 0x9EA8B6, 0xD6A84C, 0x182852, 0xA81E26, 0 },
            new int[] { 0xD8D2B0, 0xE0B84E, 0x1F5A4A, 0xF0EDE0, 0 },
            new int[] { 0x9FC4DC, 0xE6F4FF, 0x2A4A8C, 0x7FE4FF, 0x7FE4FF },
            new int[] { 0x2E2A3A, 0x5FD9E8, 0x15121E, 0x5FD9E8, 0x5FD9E8 },
            new int[] { 0xE6DCC0, 0x9B6BFF, 0x3A2466, 0x9B6BFF, 0xD6B8FF },
            new int[] { 0xF4F0FA, 0xE0B84E, 0x1B1140, 0xE0B84E, 0xB98CFF } }
    };

    // Tenue en cours : type (0 tissu, 1 cuir, 2 mailles, 3 plaques), palier, grade (palier / 2), impair.
    static int Ty, Tier, G;
    static bool Odd, Lit;

    static int[] Rgb3(int c) { return new[] { (c >> 16) & 255, (c >> 8) & 255, c & 255 }; }
    static int[] Mul3(int[] a, double k) { return new[] { (int)Clamp(a[0] * k), (int)Clamp(a[1] * k), (int)Clamp(a[2] * k) }; }
    static int[] Mix3(int[] a, int[] b, double t)
    {
        return new[] { (int)(a[0] + (b[0] - a[0]) * t), (int)(a[1] + (b[1] - a[1]) * t), (int)(a[2] + (b[2] - a[2]) * t) };
    }

    static void SetOutfit(int type, int tier)
    {
        Ty = type; Tier = tier; G = tier / 2; Odd = tier % 2 == 1;
        int[] p = PALS[type][tier];
        int[] main = Rgb3(p[0]), trim = Rgb3(p[1]), cloth = Rgb3(p[2]), plume = Rgb3(p[3]);
        Lit = p[4] != 0;
        bool darkMain = main[0] + main[1] + main[2] < 240;
        Colors.Clear();
        Colors["black"] = new[] { 14, 15, 20 };
        Colors["sole"] = new[] { 52, 36, 28 };
        Colors["gold"] = trim;
        Colors["cloth"] = cloth;
        Colors["plume"] = plume;
        Colors["glow"] = Lit ? Rgb3(p[4]) : trim;
        Colors["lining"] = Mul3(Mix3(plume, cloth, 0.25), 0.55);
        Colors["leather"] = darkMain ? new[] { 62, 46, 42 } : new[] { 104, 66, 42 };
        Colors["steel"] = main;
        Colors["steel_dark"] = Mul3(main, 0.66);
        Colors["mail"] = Mul3(Mix3(main, new[] { 120, 128, 140 }, 0.4), 0.86);
        Colors["hide"] = main;
        Colors["hide_dark"] = Mul3(main, 0.62);
        Colors["fabric"] = main;
        Colors["fabric_dark"] = Mul3(main, 0.68);
        Colors["fur"] = tier < 4 ? new[] { 200, 180, 144 } : tier < 6 ? new[] { 70, 84, 78 } : new[] { 226, 214, 200 };
        if (type == 0 || type == 1)
        {
            // Pas de metal principal : les rares pieces de metal prennent la couleur du galon.
            Colors["steel"] = trim;
            Colors["steel_dark"] = Mul3(trim, 0.66);
        }
        CapeBorder = tier >= 2;
        CapeEmblem = type == 3 ? tier >= 2 : type == 1 ? tier >= 6 : tier >= 4;
        VisorGlow = tier >= 4;
    }

    const string HelmDisplay = "{\"head\": {\"rotation\": [0, 0, 0], \"translation\": [0, 0, 0], \"scale\": [1.6, 1.6, 1.6]},"
        + " \"gui\": {\"rotation\": [25, 225, 0], \"translation\": [0, -1.25, 0], \"scale\": [0.92, 0.92, 0.92]},"
        + " \"ground\": {\"rotation\": [0, 0, 0], \"translation\": [0, 3, 0], \"scale\": [0.4, 0.4, 0.4]},"
        + " \"fixed\": {\"rotation\": [0, 180, 0], \"translation\": [0, 0, 0], \"scale\": [0.85, 0.85, 0.85]},"
        + " \"thirdperson_righthand\": {\"rotation\": [75, 45, 0], \"translation\": [0, 2.5, 0], \"scale\": [0.42, 0.42, 0.42]},"
        + " \"thirdperson_lefthand\": {\"rotation\": [75, 45, 0], \"translation\": [0, 2.5, 0], \"scale\": [0.42, 0.42, 0.42]},"
        + " \"firstperson_righthand\": {\"rotation\": [0, 225, 0], \"translation\": [0, 0, 0], \"scale\": [0.45, 0.45, 0.45]},"
        + " \"firstperson_lefthand\": {\"rotation\": [0, 225, 0], \"translation\": [0, 0, 0], \"scale\": [0.45, 0.45, 0.45]}}";

    // Ajoute un cube et son miroir (gauche / droite). Reperes du casque comme du torse : le plan de symetrie est x = 8.
    static void Both(Model m, Cube c) { m.Cubes.Add(c.Mirror()); }

    // ------------------------------------------------------------------ Casques
    // La tete fait 4..12 ; le calque du chapeau du skin monte a 3,5..12,5 ; avant = -Z.

    static Model Helmet()
    {
        Model m = new Model { Name = "casque" };
        m.Display = HelmDisplay;
        if (Ty == 3) HelmPlate(m);
        else if (Ty == 2) HelmMail(m);
        else if (Ty == 1) HelmHide(m);
        else HelmCloth(m);
        return m;
    }

    // Plaques : chapel de fer (recrue, cuivre), heaume a visiere et cimier (acier, electrum), heaume a cornes
    // (cristal, obsidienne), heaume couronne et aile (astralite, orbitium).
    static void HelmPlate(Model m)
    {
        if (G == 0)
        {
            m.Add(3.25, 7.2, 3.25, 12.75, 12.85, 12.75, "steel");                       // calotte
            m.Add(2.5, 6.7, 2.5, 13.5, 7.3, 13.5, Odd ? "gold" : "steel_dark");         // bord
            m.Add(7.45, 4.4, 2.75, 8.55, 7.2, 3.25, "steel");                           // nasal
            if (Odd)
            {
                Both(m, m.Add(3.15, 3.8, 4.2, 3.7, 7.2, 8.4, "steel_dark"));           // jugulaires
                m.Add(7.35, 12.85, 3.6, 8.65, 13.4, 12.4, "gold");                      // arete
            }
            return;
        }
        m.Add(3.25, 3.6, 3.25, 12.75, 12.85, 12.75, "steel");                           // coque
        m.Add(3.45, 3.25, 2.7, 12.55, 10.2, 3.3, "steel").Face("north", "visor");       // masque et visiere
        m.Add(3.15, 9.95, 2.35, 12.85, 10.75, 3.5, "gold");                             // arcade
        m.Add(3.0, 3.0, 3.0, 13.0, 3.65, 13.0, "gold");                                 // bord inferieur
        m.Add(3.4, 2.4, 12.75, 12.6, 5.0, 13.4, "steel_dark").Rot('x', -22.5, 8, 5, 12.75); // couvre-nuque
        foreach (double z in new[] { 5.0, 10.0 }) Both(m, m.Add(2.85, 7.0, z, 3.25, 7.8, z + 0.8, "gold")); // rivets
        if (G == 1)
        {
            m.Add(7.35, 12.85, 3.2, 8.65, 13.55, 12.95, "gold");                        // crete
            m.Add(7.5, 13.55, 4.4, 8.5, 16.2, 12.6, "plume");                           // cimier de crin
            m.Add(7.55, 8.4, 12.95, 8.45, 13.55, 14.1, "plume");
            if (Odd) Both(m, m.Add(2.55, 9.4, 6.4, 3.25, 13.4, 9.6, "gold"));           // ailerons
        }
        else if (G == 2)
        {
            m.Add(7.35, 12.85, 3.2, 8.65, 13.5, 12.95, "steel_dark");                   // crete
            Both(m, m.Add(1.3, 10.2, 7.0, 3.3, 11.7, 8.6, "gold"));                     // cornes
            Both(m, m.Add(0.7, 11.2, 7.15, 2.0, 14.9, 8.45, "gold"));
            if (Odd) Both(m, m.Add(0.85, 14.9, 7.3, 1.85, 16.4, 8.3, "glow"));
            m.Add(7.2, 10.75, 2.15, 8.8, 12.3, 2.5, "glow");                            // gemme du front
        }
        else
        {
            m.Add(7.3, 12.85, 3.2, 8.7, 13.6, 12.95, "gold");                           // crete
            m.Add(7.45, 13.6, 3.6, 8.55, 15.6, 4.8, "gold");                            // fleurons de la couronne
            m.Add(7.45, 13.6, 7.2, 8.55, 16.4, 8.6, Odd ? "glow" : "gold");
            m.Add(7.45, 13.6, 11.0, 8.55, 15.6, 12.2, "gold");
            Both(m, m.Add(1.9, 8.8, 8.6, 3.25, 15.2, 9.4, "gold").Rot('x', -22.5, 2.6, 9, 9));  // ailes
            Both(m, m.Add(1.9, 8.2, 9.9, 3.25, 13.4, 10.6, "gold").Rot('x', -22.5, 2.6, 9, 10));
            m.Add(7.2, 10.75, 2.15, 8.8, 12.3, 2.5, "glow");
        }
    }

    // Mailles : camail qui degage le visage ; cervelliere a nasal des le cuivre, crete puis ailettes ensuite.
    static void HelmMail(Model m)
    {
        m.Add(3.3, 10.3, 3.3, 12.7, 12.7, 12.7, "mail");                                // dessus
        m.Add(3.3, 3.4, 11.9, 12.7, 10.3, 12.7, "mail");                                // nuque
        Both(m, m.Add(3.3, 3.4, 3.3, 4.0, 10.3, 11.9, "mail"));                         // joues
        m.Add(3.3, 3.1, 3.3, 12.7, 4.8, 3.9, "mail");                                   // mentonniere
        m.Add(2.9, 2.3, 2.9, 13.1, 3.6, 13.1, "mail");                                  // camail sur les epaules
        if (Tier == 0)
        {
            m.Add(3.2, 9.6, 3.2, 12.8, 10.4, 12.8, "leather");                          // bandeau de cuir
            return;
        }
        m.Add(3.1, 10.1, 3.1, 12.9, 13.0, 12.9, "steel");                               // cervelliere
        m.Add(3.0, 9.8, 3.0, 13.0, 10.5, 13.0, "gold");                                 // bandeau
        m.Add(7.5, 6.4, 2.75, 8.5, 10.1, 3.3, "steel");                                 // nasal
        if (G >= 1) m.Add(7.4, 13.0, 3.3, 8.6, 13.7, 12.7, G >= 2 ? "gold" : "steel_dark"); // crete
        if (G == 1 && Odd) m.Add(7.55, 13.7, 5.0, 8.45, 15.6, 12.4, "plume");
        if (G >= 2)
        {
            Both(m, m.Add(3.0, 4.6, 3.6, 3.5, 9.8, 7.4, "steel"));                      // garde-joues
            m.Add(7.25, 10.5, 2.5, 8.75, 12.0, 2.8, "glow");                            // gemme
        }
        if (G == 3)
        {
            Both(m, m.Add(2.0, 9.6, 8.4, 3.1, 14.6, 9.2, "gold").Rot('x', -22.5, 2.6, 9.6, 8.8)); // ailettes
            if (Odd) m.Add(7.5, 13.7, 7.2, 8.5, 15.8, 8.6, "glow");
        }
    }

    // Cuir : coiffe a oreilles ; coutures, clous, foulard, plumes puis ramure.
    static void HelmHide(Model m)
    {
        m.Add(3.3, 9.8, 3.3, 12.7, 12.7, 12.7, "hide");                                 // calotte
        m.Add(3.3, 4.6, 11.9, 12.7, 9.8, 12.7, "hide");                                 // nuque
        Both(m, m.Add(3.3, 5.6, 5.5, 3.95, 9.8, 11.9, "hide"));                         // oreilles
        m.Add(3.2, 9.5, 2.7, 12.8, 10.35, 3.5, "hide_dark");                            // bandeau du front
        if (Tier >= 1) m.Add(7.6, 12.7, 3.5, 8.4, 13.0, 12.5, "hide_dark");             // couture
        if (Tier >= 2)
            foreach (double x in new[] { 4.6, 7.6, 10.6 }) m.Add(x, 9.65, 2.45, x + 0.8, 10.25, 2.7, "gold"); // clous
        if (Tier == 3 || Tier == 5) m.Add(3.4, 4.0, 3.0, 12.6, 6.9, 3.5, "cloth");      // foulard sur le bas du visage
        if (G == 2)
        {
            m.Add(12.7, 9.0, 7.0, 13.0, 15.6, 8.6, "plume").Rot('x', -22.5, 12.85, 9, 7.8); // plume
            if (Odd) m.Add(12.7, 9.0, 8.4, 13.0, 14.4, 9.8, "plume").Rot('x', -22.5, 12.85, 9, 9.1);
        }
        if (G == 3)
        {
            Both(m, m.Add(4.2, 12.7, 6.5, 5.2, 15.8, 7.5, "gold"));                     // ramure
            Both(m, m.Add(2.8, 14.4, 6.6, 4.2, 15.2, 7.4, "gold"));
            Both(m, m.Add(2.8, 15.2, 6.6, 3.6, 16.9, 7.4, Odd ? "glow" : "gold"));
            Both(m, m.Add(4.4, 15.8, 6.7, 5.0, 17.2, 7.3, Odd ? "glow" : "gold"));
        }
    }

    // Tissu : capuche ample a pointe ; galon, gemme du front, diademe puis aureole.
    static void HelmCloth(Model m)
    {
        string rim = Tier == 0 ? "fabric_dark" : "gold";
        m.Add(3.0, 10.6, 3.0, 13.0, 13.2, 13.4, "fabric");                              // dessus
        m.Add(3.0, 3.2, 12.0, 13.0, 10.6, 13.4, "fabric");                              // arriere
        Both(m, m.Add(3.0, 3.2, 3.4, 3.8, 10.6, 12.0, "fabric"));                       // cotes
        m.Add(2.9, 10.2, 2.7, 13.1, 11.0, 3.4, rim);                                    // bord du haut
        Both(m, m.Add(2.9, 3.2, 2.9, 3.6, 10.2, 3.5, rim));                             // bords des cotes
        m.Add(5.0, 9.6, 13.4, 11.0, 12.6, 14.6, "fabric");                              // pointe
        m.Add(6.4, 10.2, 14.6, 9.6, 12.0, 15.7, "fabric_dark");
        if (Tier >= 2) m.Add(7.3, 10.9, 2.4, 8.7, 12.1, 2.75, Lit ? "glow" : "gold");   // gemme du front
        if (Tier >= 5)
        {
            m.Add(7.5, 13.2, 3.2, 8.5, 14.6, 4.0, "gold");                              // diademe
            Both(m, m.Add(4.6, 13.2, 3.2, 5.4, 14.0, 4.0, "gold"));
        }
        if (Tier == 7)
        {
            // Aureole : quatre barres au-dessus de la capuche.
            m.Add(3.6, 15.0, 3.6, 12.4, 15.45, 4.4, "glow");
            m.Add(3.6, 15.0, 11.6, 12.4, 15.45, 12.4, "glow");
            Both(m, m.Add(3.6, 15.0, 4.4, 4.4, 15.45, 11.6, "glow"));
        }
    }

    // ------------------------------------------------------------------ Epaules (pivot (8,8,8) = cou, gauche du joueur = +X)

    static Model Pauldrons()
    {
        Model m = new Model { Name = "epaules" };
        List<Cube> left = new List<Cube>();
        if (Ty == 3)
        {
            if (G == 0)
            {
                left.Add(m.Rel(3.4, -1.2, -3.5, 9.0, 2.2, 3.5, "steel"));                       // dome simple
                left.Add(m.Rel(3.4, -1.4, -3.55, 9.1, -1.1, 3.55, Odd ? "gold" : "steel_dark"));
                if (Odd) left.Add(m.Rel(5.4, -3.2, -3.4, 9.6, -1.1, 3.4, "steel_dark"));        // une lame
            }
            else
            {
                left.Add(m.Rel(3.2, -1.6, -3.8, 9.6, 2.6, 3.8, "steel"));           // dome
                left.Add(m.Rel(3.8, 2.6, -3.2, 8.8, 3.5, 3.2, "steel"));            // calotte
                left.Add(m.Rel(5.8, 3.5, -2.8, 6.8, 4.3, 2.8, "gold"));             // arete
                left.Add(m.Rel(3.2, -1.75, -3.85, 9.7, -1.45, 3.85, "gold"));       // galon sous le dome
                left.Add(m.Rel(5.5, -3.6, -3.9, 10.4, -1.2, 3.9, "steel_dark"));    // lame 1
                left.Add(m.Rel(6.5, -5.6, -3.6, 11.0, -3.3, 3.6, "steel"));         // lame 2
                left.Add(m.Rel(6.45, -6.0, -3.65, 11.05, -5.6, 3.65, "gold"));      // galon bas
                string stud = G >= 2 ? "glow" : "gold";
                left.Add(m.Rel(6.0, 0.2, 3.8, 6.8, 1.0, 4.15, stud));               // rivets ou gemmes
                left.Add(m.Rel(6.0, 0.2, -4.15, 6.8, 1.0, -3.8, stud));
                left.Add(m.Rel(9.4, -4.9, 3.6, 10.1, -4.2, 3.95, "gold"));
                left.Add(m.Rel(9.4, -4.9, -3.95, 10.1, -4.2, -3.6, "gold"));
                if (G == 2) left.Add(m.Rel(5.7, 4.3, -0.7, 6.9, 7.0, 0.7, Odd ? "glow" : "gold"));   // pointe
                if (G == 3)
                {
                    left.Add(m.Rel(4.4, 3.5, -2.4, 8.2, 4.6, 2.4, "steel"));        // second dome
                    left.Add(m.Rel(5.6, 4.6, -2.0, 6.8, 7.4, 2.0, "gold"));         // aileron
                    if (Odd) left.Add(m.Rel(5.8, 7.4, -0.6, 6.6, 8.6, 0.6, "glow"));
                }
            }
        }
        else if (Ty == 2)
        {
            left.Add(m.Rel(3.8, -4.4, -3.4, 9.3, -1.0, 3.4, "mail"));               // pan de mailles
            if (Tier == 0)
            {
                left.Add(m.Rel(3.4, -1.0, -3.5, 9.0, 1.8, 3.5, "leather"));         // coussin de cuir
            }
            else
            {
                left.Add(m.Rel(3.4, -1.0, -3.5, 9.0, 2.2, 3.5, "steel"));           // spaliere
                left.Add(m.Rel(3.4, -1.25, -3.55, 9.1, -0.95, 3.55, "gold"));
                if (G >= 1) left.Add(m.Rel(5.6, 2.2, -2.8, 6.8, 3.0, 2.8, G >= 2 ? "gold" : "steel_dark"));
                if (G >= 2) left.Add(m.Rel(5.6, 0.0, 3.5, 7.0, 1.4, 3.85, "glow"));
                if (G == 3) left.Add(m.Rel(4.2, 2.2, -2.2, 8.2, 3.2, 2.2, "steel"));
                if (G == 3 && Odd) left.Add(m.Rel(5.7, 3.2, -0.6, 6.7, 5.4, 0.6, "glow"));
            }
        }
        else if (Ty == 1)
        {
            left.Add(m.Rel(3.4, -0.8, -3.3, 8.8, 1.8, 3.3, "hide"));                // epaulette de cuir
            left.Add(m.Rel(4.6, -3.0, -3.4, 9.4, -0.8, 3.4, "hide_dark"));          // rabat
            if (Tier >= 2)
            {
                left.Add(m.Rel(5.4, 0.2, 3.3, 6.2, 1.0, 3.6, "gold"));              // clous
                left.Add(m.Rel(5.4, 0.2, -3.6, 6.2, 1.0, -3.3, "gold"));
                left.Add(m.Rel(7.4, -2.4, 3.4, 8.2, -1.6, 3.7, "gold"));
                left.Add(m.Rel(7.4, -2.4, -3.7, 8.2, -1.6, -3.4, "gold"));
            }
            if (G >= 2) left.Add(m.Rel(5.4, 1.8, -2.4, 6.6, 2.5, 2.4, "gold"));     // renfort
            if (G == 3)
            {
                left.Add(m.Rel(5.5, 2.5, -1.8, 6.5, 4.6, -0.8, Odd ? "glow" : "gold")); // pointes d'os
                left.Add(m.Rel(5.5, 2.5, 0.8, 6.5, 4.6, 1.8, Odd ? "glow" : "gold"));
            }
        }
        else
        {
            if (G >= 2)
            {
                left.Add(m.Rel(7.0, 0.9, -2.4, 9.9, 1.7, 2.4, "fabric_dark"));      // pointes d'epaule
                left.Add(m.Rel(8.3, 1.7, -1.4, 10.4, 2.4, 1.4, "gold"));
            }
            if (G == 3) left.Add(m.Rel(9.3, 4.4, -0.6, 10.5, 6.4, 0.6, "glow"));    // cristaux flottants
        }
        if (Ty != 0) foreach (Cube c in left) c.Rot('z', -22.5, 12, 10, 8);
        foreach (Cube c in left) m.Cubes.Add(c.Mirror());
        // Pieces du milieu (pas de miroir).
        if (Ty == 1 && Tier >= 1) m.Rel(-6.0, -1.4, -3.7, 6.0, 1.1, 3.7, "fur");    // col de fourrure
        if (Ty == 0)
        {
            if (Tier == 0)
            {
                m.Rel(-6.5, -1.6, -3.2, 6.5, 0.8, 3.2, "fabric");                   // petit col
            }
            else
            {
                m.Rel(-8.9, -2.4, -3.3, 8.9, 0.9, 3.3, "fabric");                   // pelerine
                m.Rel(-9.0, -2.9, -3.4, 9.0, -2.4, 3.4, "gold");                    // galon
                m.Rel(-0.9, -1.9, 3.3, 0.9, -0.1, 3.7, Lit ? "glow" : "gold");      // fermoir
            }
        }
        return m;
    }

    // ------------------------------------------------------------------ Cape (des le 2e palier ; charniere au pivot)

    static bool HasCape() { return Tier >= 1; }

    static Model Cape()
    {
        Model m = new Model { Name = "cape" };
        double hw, len;
        if (Ty == 3) { hw = 5.0; len = Tier == 1 ? 15 : 20; }
        else if (Ty == 2) { hw = 4.2; len = Tier < 4 ? 13 : 16; }
        else if (Ty == 1) { hw = 4.6; len = Tier < 4 ? 11 : 15; }
        else { hw = 5.0; len = Tier < 4 ? 17 : 21; }
        m.Rel(-hw, -len, -0.6, hw, -0.3, 0.0, "cloth").Face("north", "cape_back").Face("south", "lining");
        // Bas decoupe en trois pans (deux pour les mailles, en pointe pour le tissu).
        double tw = hw * 0.56;
        if (Ty == 2)
        {
            m.Rel(-hw, -len - 1.8, -0.6, -0.5, -len, 0.0, "cloth").Face("north", "cape_back").Face("south", "lining");
            m.Rel(0.5, -len - 1.8, -0.6, hw, -len, 0.0, "cloth").Face("north", "cape_back").Face("south", "lining");
        }
        else if (Ty == 0)
        {
            m.Rel(-hw + 1.2, -len - 1.2, -0.6, hw - 1.2, -len, 0.0, "cloth").Face("north", "cape_back").Face("south", "lining");
            m.Rel(-hw + 2.6, -len - 2.4, -0.6, hw - 2.6, -len - 1.2, 0.0, "cloth").Face("north", "cape_back").Face("south", "lining");
        }
        else
        {
            double drop = Ty == 1 ? 2.2 : 1.6;
            m.Rel(-hw, -len - drop, -0.6, -hw + tw, -len, 0.0, "cloth").Face("north", "cape_back").Face("south", "lining");
            m.Rel(-tw / 2, -len - 1.6, -0.6, tw / 2, -len, 0.0, "cloth").Face("north", "cape_back").Face("south", "lining");
            m.Rel(hw - tw, -len - (Ty == 1 ? 1.2 : drop), -0.6, hw, -len, 0.0, "cloth").Face("north", "cape_back").Face("south", "lining");
        }
        // Attache : barre de metal pour les plaques et les mailles, cordon et fermoirs sinon.
        if (Ty >= 2)
        {
            m.Rel(-hw - 0.4, -0.9, -0.95, hw + 0.4, 0.5, 0.3, "gold");
            m.Rel(-hw + 0.4, -1.5, -1.25, -hw + 1.8, 0.9, -0.95, "gold");
            m.Rel(hw - 1.8, -1.5, -1.25, hw - 0.4, 0.9, -0.95, "gold");
        }
        else
        {
            m.Rel(-hw, -0.7, -0.85, hw, 0.3, 0.2, Ty == 1 ? "hide_dark" : "fabric_dark");
            m.Rel(-hw + 0.3, -1.2, -1.15, -hw + 1.5, 0.6, -0.85, "gold");
            m.Rel(hw - 1.5, -1.2, -1.15, hw - 0.3, 0.6, -0.85, "gold");
        }
        return m;
    }

    // ------------------------------------------------------------------ Couche du corps (equipment)

    // Zone en pixels de peau (64 x 32) -> pixels HD.
    static void Area(Img im, double sx, double sy, double sw, double sh, string mat, int seed)
    {
        int x0 = (int)Math.Round(sx * PPU), y0 = (int)Math.Round(sy * PPU);
        int x1 = (int)Math.Round((sx + sw) * PPU), y1 = (int)Math.Round((sy + sh) * PPU);
        PaintFace(im, x0, y0, x1 - x0, y1 - y0, mat, "side", seed);
    }

    static void Stud(Img im, double sx, double sy, double r) { Rivet(im, sx * PPU, sy * PPU, PPU * r); }

    // Faces laterales d'un cube de la peau (origine u,v ; largeur w, hauteur h, profondeur d) : droite, avant, gauche, arriere.
    static double[][] Sides(double u, double v, double w, double h, double d)
    {
        return new[] { new[] { u, v + d, d }, new[] { u + d, v + d, w }, new[] { u + d + w, v + d, d }, new[] { u + 2 * d + w, v + d, w } };
    }

    // Plastron (corps 8 x 12 x 4 en 16,16), bras (4 x 12 x 4 en 40,16), bottes (jambe 4 x 12 x 4 en 0,16, bas seulement).
    static Img Humanoid()
    {
        Img im = new Img(64 * PPU, 32 * PPU);
        int seed = 100;
        double[][] body = Sides(16, 16, 8, 12, 4), arm = Sides(40, 16, 4, 12, 4), leg = Sides(0, 16, 4, 12, 4);
        if (Ty == 3)
        {
            string trim = Tier == 0 ? "steel_dark" : "gold";
            for (int k = 0; k < 4; k++)
            {
                double u = body[k][0], v = body[k][1], w = body[k][2];
                bool front = k == 1, back = k == 3;
                Area(im, u, v, w, 7.6, "steel", seed++);
                if (front || back) Area(im, u, v, w, 0.8, trim, seed++);
                if (front) Area(im, u + 3.55, v + 0.8, 0.9, 6.6, "steel", seed++);          // arete du plastron
                if (front && G >= 2) Area(im, u + 3.2, v + 2.2, 1.6, 1.6, "glow", seed++);  // gemme
                for (int l = 0; l < 3; l++) Area(im, u, v + 7.6 + l * 1.4, w, 1.4, l == 1 ? "steel_dark" : "steel", seed++);
                Area(im, u, v + 11.8, w, 0.2, trim, seed++);
            }
            Area(im, 20, 16, 8, 4, "steel", seed++);
            Area(im, 28, 16, 8, 4, "steel_dark", seed++);
            foreach (double[] s in arm)
            {
                double u = s[0], v = s[1], w = s[2];
                for (int l = 0; l < 3; l++) Area(im, u, v + l * 1.5, w, 1.5, l == 2 ? "steel_dark" : "steel", seed++);
                Area(im, u, v + 4.5, w, 3.0, "mail", seed++);
                Area(im, u, v + 7.5, w, 3.9, "steel", seed++);
                Area(im, u, v + 11.4, w, 0.6, trim, seed++);
            }
            Area(im, 44, 16, 4, 4, "steel", seed++);
            Area(im, 48, 16, 4, 4, "steel_dark", seed++);
            foreach (double[] s in leg)
            {
                double u = s[0], v = s[1], w = s[2];
                Area(im, u, v + 6.6, w, 0.5, trim, seed++);
                Area(im, u, v + 7.1, w, 1.7, "steel", seed++);
                Area(im, u, v + 8.8, w, 1.5, "steel_dark", seed++);
                Area(im, u, v + 10.3, w, 1.7, "steel", seed++);
            }
            Area(im, 4, 20 + 11.3, 4, 0.7, trim, seed++);
        }
        else if (Ty == 2)
        {
            for (int k = 0; k < 4; k++)
            {
                double u = body[k][0], v = body[k][1], w = body[k][2];
                bool fb = k == 1 || k == 3;
                Area(im, u, v, w, 12, "mail", seed++);
                if (Tier >= 2 && fb) Area(im, u, v + 0.8, w, 2.6, "steel", seed++);         // pectoral
                Area(im, u, v, w, 0.8, Tier == 0 ? "leather" : "gold", seed++);             // col
                if (Tier >= 1 && fb)
                {
                    Area(im, u + 1.6, v + (Tier >= 2 ? 3.4 : 0.8), 4.8, Tier >= 2 ? 8.6 : 11.2, "cloth", seed++); // tabard
                    Area(im, u + 1.6, v + (Tier >= 2 ? 3.4 : 0.8), 0.35, Tier >= 2 ? 8.6 : 11.2, "gold", seed++);
                    Area(im, u + 6.05, v + (Tier >= 2 ? 3.4 : 0.8), 0.35, Tier >= 2 ? 8.6 : 11.2, "gold", seed++);
                    if (k == 1 && Tier >= 3) Area(im, u + 3.3, v + 5.2, 1.4, 1.4, Lit ? "glow" : "gold", seed++);
                }
            }
            Area(im, 20, 16, 8, 4, "mail", seed++);
            Area(im, 28, 16, 8, 4, "mail", seed++);
            foreach (double[] s in arm)
            {
                double u = s[0], v = s[1], w = s[2];
                Area(im, u, v, w, 7.0, "mail", seed++);
                if (Tier >= 2) Area(im, u, v + 4.6, w, 2.2, "steel", seed++);               // cubitiere
                Area(im, u, v + 7.0, w, 0.5, Tier == 0 ? "leather" : "gold", seed++);
                Area(im, u, v + 7.5, w, 3.9, "leather", seed++);                            // brassard
                Area(im, u, v + 11.4, w, 0.6, Tier == 0 ? "leather" : "gold", seed++);
            }
            Area(im, 44, 16, 4, 4, "mail", seed++);
            Area(im, 48, 16, 4, 4, "leather", seed++);
            foreach (double[] s in leg)
            {
                double u = s[0], v = s[1], w = s[2];
                Area(im, u, v + 6.6, w, 0.5, Tier == 0 ? "leather" : "gold", seed++);
                Area(im, u, v + 7.1, w, 4.9, "leather", seed++);
                if (Tier >= 2) Area(im, u, v + 7.1, w, 2.0, "steel", seed++);               // greve
            }
            if (Tier >= 1) Area(im, 4, 20 + 11.2, 4, 0.8, "steel", seed++);
        }
        else if (Ty == 1)
        {
            string strap = Tier >= 1 ? "gold" : "hide_dark";
            for (int k = 0; k < 4; k++)
            {
                double u = body[k][0], v = body[k][1], w = body[k][2];
                Area(im, u, v, w, 12, "hide", seed++);
                Area(im, u, v, w, 0.7, "hide_dark", seed++);                                // col
                if (Tier >= 1) Area(im, u, v + 3.6, w, 0.7, "hide_dark", seed++);           // sangle de poitrine
                if (k == 1)
                {
                    Area(im, u + 3.6, v + 0.7, 0.8, 7.5, "hide_dark", seed++);              // lacage
                    if (Tier >= 2)
                        foreach (double yy in new[] { 2.0, 5.6 })
                        {
                            Stud(im, u + 1.3, v + yy, 0.36); Stud(im, u + 6.7, v + yy, 0.36); // clous
                        }
                    if (G >= 2) Area(im, u + 3.3, v + 1.5, 1.4, 1.4, "glow", seed++);       // rune
                }
            }
            Area(im, 20, 16, 8, 4, "hide", seed++);
            Area(im, 28, 16, 8, 4, "hide_dark", seed++);
            foreach (double[] s in arm)
            {
                double u = s[0], v = s[1], w = s[2];
                Area(im, u, v, w, 5.5, "cloth", seed++);                                    // manche
                Area(im, u, v + 5.5, w, 0.5, "hide_dark", seed++);
                Area(im, u, v + 6.0, w, 5.4, "hide", seed++);                               // brassard
                Area(im, u, v + 7.2, w, 0.5, strap, seed++);
                Area(im, u, v + 9.8, w, 0.5, strap, seed++);
                Area(im, u, v + 11.4, w, 0.6, "hide_dark", seed++);
            }
            Area(im, 44, 16, 4, 4, "cloth", seed++);
            Area(im, 48, 16, 4, 4, "hide_dark", seed++);
            foreach (double[] s in leg)
            {
                double u = s[0], v = s[1], w = s[2];
                Area(im, u, v + 6.4, w, 1.4, "hide_dark", seed++);                          // revers
                Area(im, u, v + 7.8, w, 4.2, "hide", seed++);
                Area(im, u, v + 9.6, w, 0.4, strap, seed++);
            }
        }
        else
        {
            string trim = Tier == 0 ? "fabric_dark" : "gold";
            for (int k = 0; k < 4; k++)
            {
                double u = body[k][0], v = body[k][1], w = body[k][2];
                Area(im, u, v, w, 12, "fabric", seed++);
                if (k == 1)
                {
                    Area(im, u + 2.9, v, 2.2, 12, Tier == 0 ? "fabric_dark" : "cloth", seed++); // bande du devant
                    if (Tier >= 1)
                    {
                        Area(im, u + 2.9, v, 0.3, 12, "gold", seed++);
                        Area(im, u + 4.8, v, 0.3, 12, "gold", seed++);
                    }
                    if (Tier >= 2) Area(im, u + 3.3, v + 1.6, 1.4, 1.4, Lit ? "glow" : "gold", seed++); // broche
                }
                Area(im, u, v, w, 0.8, trim, seed++);                                       // col
            }
            Area(im, 20, 16, 8, 4, "fabric", seed++);
            Area(im, 28, 16, 8, 4, "fabric_dark", seed++);
            foreach (double[] s in arm)
            {
                double u = s[0], v = s[1], w = s[2];
                Area(im, u, v, w, 12, "fabric", seed++);
                if (Tier >= 2) Area(im, u, v + 2.2, w, 0.5, "gold", seed++);                // galon du bras
                Area(im, u, v + 9.6, w, 2.4, Tier == 0 ? "fabric_dark" : "cloth", seed++);  // revers de manche
                if (Tier >= 1)
                {
                    Area(im, u, v + 9.4, w, 0.35, "gold", seed++);
                    Area(im, u, v + 11.65, w, 0.35, "gold", seed++);
                }
            }
            Area(im, 44, 16, 4, 4, "fabric", seed++);
            Area(im, 48, 16, 4, 4, "fabric_dark", seed++);
            foreach (double[] s in leg)
            {
                double u = s[0], v = s[1], w = s[2];
                Area(im, u, v + 9.4, w, 2.6, "fabric_dark", seed++);                        // chaussons
                Area(im, u, v + 9.2, w, 0.35, trim, seed++);
            }
        }
        Area(im, 8, 16, 4, 4, "sole", seed++);
        return im;
    }

    // Jambieres : bas du corps (ceinture, tassettes ou jupe) et jambes.
    static Img Leggings()
    {
        Img im = new Img(64 * PPU, 32 * PPU);
        int seed = 300;
        double[][] body = Sides(16, 16, 8, 12, 4), leg = Sides(0, 16, 4, 12, 4);
        if (Ty == 3)
        {
            for (int k = 0; k < 4; k++)
            {
                double u = body[k][0], v = body[k][1], w = body[k][2];
                Area(im, u, v + 8, w, 1.1, "leather", seed++);
                if (k == 1)
                {
                    Area(im, u, v + 9.1, 3.85, 2.9, "steel", seed++);
                    Area(im, u + 4.15, v + 9.1, 3.85, 2.9, "steel", seed++);
                    Area(im, u + 3.85, v + 9.1, 0.3, 2.9, "mail", seed++);
                    Area(im, u + 3.0, v + 7.85, 2.0, 1.4, "gold", seed++);                  // boucle
                }
                else Area(im, u, v + 9.1, w, 2.9, "steel", seed++);
            }
            Area(im, 28, 16, 8, 4, "steel_dark", seed++);
            foreach (double[] s in leg)
            {
                double u = s[0], v = s[1], w = s[2];
                Area(im, u, v, w, 2.9, "steel", seed++);
                Area(im, u, v + 2.9, w, 2.7, "steel", seed++);
                Area(im, u, v + 5.6, w, 1.5, "steel_dark", seed++);
                Area(im, u, v + 7.1, w, 4.9, "mail", seed++);
            }
            Area(im, 4.6, 20 + 5.5, 2.8, 1.7, "steel", seed++);                            // genouillere
            Stud(im, 4 + 2, 20 + 6.35, 0.55);
            Area(im, 4, 16, 4, 4, "steel", seed++);
            Area(im, 8, 16, 4, 4, "mail", seed++);
        }
        else if (Ty == 2)
        {
            for (int k = 0; k < 4; k++)
            {
                double u = body[k][0], v = body[k][1], w = body[k][2];
                Area(im, u, v + 8, w, 1.1, "leather", seed++);
                Area(im, u, v + 9.1, w, 2.9, "mail", seed++);                               // jupe de mailles
                if (k == 1) Area(im, u + 3.1, v + 7.85, 1.8, 1.4, Tier == 0 ? "steel_dark" : "gold", seed++);
            }
            Area(im, 28, 16, 8, 4, "mail", seed++);
            foreach (double[] s in leg)
            {
                double u = s[0], v = s[1], w = s[2];
                Area(im, u, v, w, 5.6, "mail", seed++);
                Area(im, u, v + 5.6, w, 1.5, Tier >= 2 ? "steel" : "leather", seed++);      // genou
                Area(im, u, v + 7.1, w, 4.9, "leather", seed++);
            }
            Area(im, 4, 16, 4, 4, "mail", seed++);
            Area(im, 8, 16, 4, 4, "leather", seed++);
        }
        else if (Ty == 1)
        {
            for (int k = 0; k < 4; k++)
            {
                double u = body[k][0], v = body[k][1], w = body[k][2];
                Area(im, u, v + 8, w, 1.2, "hide_dark", seed++);
                Area(im, u, v + 9.2, w, 2.8, "hide", seed++);                               // basques
                if (k == 1)
                {
                    Area(im, u + 3.85, v + 9.2, 0.3, 2.8, "hide_dark", seed++);
                    Area(im, u + 3.1, v + 7.9, 1.8, 1.4, "gold", seed++);                   // boucle
                }
            }
            Area(im, 28, 16, 8, 4, "hide_dark", seed++);
            foreach (double[] s in leg)
            {
                double u = s[0], v = s[1], w = s[2];
                Area(im, u, v, w, 12, "cloth", seed++);                                     // braies
                Area(im, u, v + 4.8, w, 2.2, "hide", seed++);                               // genouillere
                if (Tier >= 1) Area(im, u, v + 1.6, w, 0.4, "hide_dark", seed++);
            }
            Area(im, 4, 16, 4, 4, "cloth", seed++);
            Area(im, 8, 16, 4, 4, "cloth", seed++);
        }
        else
        {
            for (int k = 0; k < 4; k++)
            {
                double u = body[k][0], v = body[k][1], w = body[k][2];
                Area(im, u, v + 8, w, 1.3, "cloth", seed++);                                // ceinture d'etoffe
                Area(im, u, v + 9.3, w, 2.7, "fabric", seed++);
                if (k == 1) Area(im, u + 3.1, v + 7.9, 1.8, 1.5, Tier == 0 ? "fabric_dark" : "gold", seed++); // noeud
            }
            Area(im, 28, 16, 8, 4, "fabric_dark", seed++);
            foreach (double[] s in leg)
            {
                double u = s[0], v = s[1], w = s[2];
                Area(im, u, v, w, 12, "fabric", seed++);                                    // jupe de la robe
                if (Tier >= 1)
                {
                    Area(im, u, v + 10.4, w, 0.4, "gold", seed++);                          // ourlet
                    Area(im, u, v + 10.8, w, 1.2, "cloth", seed++);
                }
                else Area(im, u, v + 10.8, w, 1.2, "fabric_dark", seed++);
            }
            Area(im, 4, 16, 4, 4, "fabric", seed++);
            Area(im, 8, 16, 4, 4, "fabric_dark", seed++);
        }
        return im;
    }

    // Definition d'objet du casque : modele 3D sur la tete ; partout ailleurs (inventaire, main, sol), l'icone HD
    // de l'armurerie (custom_model_data "orbitmmo:armure/<type>_head_<v>" pose par Loot, modeles de gen-items.ps1),
    // et le modele 3D si l'icone manque.
    static void HelmetItem(string packDir, string outfit, string armorType)
    {
        string model3d = "{\"type\": \"minecraft:model\", \"model\": \"orbitmmo:outfit/" + outfit + "/casque\"}";
        List<string> cases = new List<string>();
        for (int v = 0; v < 8; v++)
        {
            string id = "armure/" + armorType + "_head_" + v;
            if (File.Exists(Path.Combine(packDir, "assets", "orbitmmo", "models", "item", "armure", armorType + "_head_" + v + ".json")))
                cases.Add("        {\"when\": \"orbitmmo:" + id + "\", \"model\": {\"type\": \"minecraft:model\", \"model\": \"orbitmmo:item/" + id + "\"}}");
        }
        string fallback = cases.Count == 0 ? model3d
            : "{\n      \"type\": \"minecraft:select\", \"property\": \"minecraft:custom_model_data\", \"index\": 0,\n      \"cases\": [\n"
              + string.Join(",\n", cases.ToArray()) + "\n      ],\n      \"fallback\": " + model3d + "\n    }";
        Text(Path.Combine(packDir, "assets", "orbitmmo", "items", "outfit", outfit, "casque.json"),
            "{\n  \"model\": {\n    \"type\": \"minecraft:select\", \"property\": \"minecraft:display_context\",\n"
            + "    \"cases\": [{\"when\": \"head\", \"model\": " + model3d + "}],\n    \"fallback\": " + fallback + "\n  }\n}\n");
    }

    // ------------------------------------------------------------------ Point d'entree

    // Ecrit les 32 tenues dans le pack et, si previewDir est donne, une planche d'apercus par type
    // (<type>.png : les 8 paliers de face et de dos) plus quatre vues en grand de la tenue "detail".
    public static string Run(string packDir, string previewDir) { return Run(packDir, previewDir, "plaques_2"); }

    public static string Run(string packDir, string previewDir, string detail)
    {
        int count = 0;
        long bytes = 0;
        for (int type = 0; type < TYPES.Length; type++)
        {
            Bitmap sheet = previewDir != null && previewDir.Length > 0 ? new Bitmap(8 * CELL, 2 * CELL) : null;
            for (int tier = 0; tier < 8; tier++)
            {
                SetOutfit(type, tier);
                string outfit = TYPES[type] + "_" + tier;
                Dictionary<Cube, Dictionary<string, Rectangle>> uv = new Dictionary<Cube, Dictionary<string, Rectangle>>();
                Model helmet = Helmet(), pauldrons = Pauldrons(), cape = HasCape() ? Cape() : null;
                Img helmetTex = BuildModel(packDir, outfit, helmet, uv);
                HelmetItem(packDir, outfit, TYPES[type]);
                Img pauldronTex = BuildModel(packDir, outfit, pauldrons, uv);
                Img capeTex = cape == null ? null : BuildModel(packDir, outfit, cape, uv);

                string asset = "tenue_" + outfit;
                Img hum = Humanoid(), leg = Leggings();
                hum.Save(Path.Combine(packDir, "assets", "orbitmmo", "textures", "entity", "equipment", "humanoid", asset + ".png"));
                leg.Save(Path.Combine(packDir, "assets", "orbitmmo", "textures", "entity", "equipment", "humanoid_leggings", asset + ".png"));
                Text(Path.Combine(packDir, "assets", "orbitmmo", "equipment", asset + ".json"),
                    "{\n  \"layers\": {\n    \"humanoid\": [{\"texture\": \"orbitmmo:" + asset + "\"}],\n    \"humanoid_leggings\": [{\"texture\": \"orbitmmo:" + asset + "\"}]\n  }\n}\n");
                count++;

                if (sheet != null)
                {
                    Preview prev = new Preview();
                    prev.Mannequin(hum, leg);
                    prev.AddModel(helmet, helmetTex, uv, HelmetToBody);
                    prev.AddModel(pauldrons, pauldronTex, uv, PauldronToBody);
                    if (cape != null) prev.AddModel(cape, capeTex, uv, CapeToBody(0.12));
                    using (Graphics g = Graphics.FromImage(sheet))
                    {
                        using (Bitmap a = prev.RenderBmp(30, 12, CELL, CELL / 42.0)) g.DrawImage(a, tier * CELL, 0);
                        using (Bitmap b = prev.RenderBmp(205, 12, CELL, CELL / 42.0)) g.DrawImage(b, tier * CELL, CELL);
                    }
                    if (outfit == detail)
                    {
                        prev.Render(Path.Combine(previewDir, outfit + "-face.png"), 30, 12);
                        prev.Render(Path.Combine(previewDir, outfit + "-dos.png"), 205, 12);
                        prev.Render(Path.Combine(previewDir, outfit + "-profil.png"), 90, 5);
                        Preview run = new Preview();
                        run.Mannequin(hum, leg);
                        run.AddModel(helmet, helmetTex, uv, HelmetToBody);
                        run.AddModel(pauldrons, pauldronTex, uv, PauldronToBody);
                        if (cape != null) run.AddModel(cape, capeTex, uv, CapeToBody(0.75));
                        run.Render(Path.Combine(previewDir, outfit + "-course.png"), 120, 8);
                    }
                }
            }
            if (sheet != null)
            {
                Directory.CreateDirectory(previewDir);
                sheet.Save(Path.Combine(previewDir, TYPES[type] + ".png"), ImageFormat.Png);
                sheet.Dispose();
            }
        }
        foreach (string dir in new[] { Path.Combine(packDir, "assets", "orbitmmo", "textures", "item", "outfit"),
                     Path.Combine(packDir, "assets", "orbitmmo", "textures", "entity", "equipment") })
            foreach (string f in Directory.GetFiles(dir, "*.png", SearchOption.AllDirectories))
                if (f.Contains("outfit") || Path.GetFileName(f).StartsWith("tenue_")) bytes += new FileInfo(f).Length;
        return count + " tenues, " + (bytes / 1024) + " Ko de textures";
    }

    const int CELL = 300;

    // Controle du moire : les 32 tenues vues de loin (la moitie de la taille des planches, soit environ 3,5 pixels
    // d'ecran par pixel du modele, sans lissage comme en jeu), puis agrandies 2 fois pour etre lisibles.
    // tmpPack recoit des fichiers de travail (jamais le vrai pack).
    public static void Far(string tmpPack, string path)
    {
        int cell = CELL / 2;
        using (Bitmap small = new Bitmap(8 * cell, 4 * cell))
        {
            using (Graphics g = Graphics.FromImage(small))
            {
                for (int type = 0; type < TYPES.Length; type++)
                    for (int tier = 0; tier < 8; tier++)
                    {
                        SetOutfit(type, tier);
                        string outfit = TYPES[type] + "_" + tier;
                        Dictionary<Cube, Dictionary<string, Rectangle>> uv = new Dictionary<Cube, Dictionary<string, Rectangle>>();
                        Model helmet = Helmet(), pauldrons = Pauldrons(), cape = HasCape() ? Cape() : null;
                        Preview prev = new Preview();
                        prev.Mannequin(Humanoid(), Leggings());
                        prev.AddModel(helmet, BuildModel(tmpPack, outfit, helmet, uv), uv, HelmetToBody);
                        prev.AddModel(pauldrons, BuildModel(tmpPack, outfit, pauldrons, uv), uv, PauldronToBody);
                        if (cape != null) prev.AddModel(cape, BuildModel(tmpPack, outfit, cape, uv), uv, CapeToBody(0.12));
                        using (Bitmap a = prev.RenderBmp(tier % 2 == 0 ? 205 : 30, 25, cell, cell / 42.0)) g.DrawImage(a, tier * cell, type * cell);
                    }
            }
            using (Bitmap big = new Bitmap(small.Width * 2, small.Height * 2))
            {
                using (Graphics g = Graphics.FromImage(big))
                {
                    g.InterpolationMode = InterpolationMode.NearestNeighbor;
                    g.PixelOffsetMode = PixelOffsetMode.Half;
                    g.DrawImage(small, 0, 0, big.Width, big.Height);
                }
                Directory.CreateDirectory(Path.GetDirectoryName(path));
                big.Save(path, ImageFormat.Png);
            }
        }
    }

    // ------------------------------------------------------------------ Apercus (mannequin en cubes, projection orthographique)
    // Repere de l'apercu : pixels du modele du joueur, pieds en y = 0, avant = +Z, gauche = +X.

    delegate double[] Xf(double x, double y, double z);

    static double[] HelmetToBody(double x, double y, double z) { return new[] { -(x - 8), y - 8 + 28, -(z - 8) }; }

    // Meme calcul que le plugin : pivot du torse = cou (y 24).
    static double[] PauldronToBody(double x, double y, double z) { return new[] { x - 8, y - 8 + 24, z - 8 }; }

    static Xf CapeToBody(double angle)
    {
        return delegate (double x, double y, double z)
        {
            double lx = x - 8, ly = y - 8, lz = z - 8;
            double c = Math.Cos(angle), s = Math.Sin(angle);
            double ry = ly * c - lz * s, rz = ly * s + lz * c;
            return new[] { lx, ry + 24 - 0.2, rz - 3.7 };
        };
    }

    class Quad { public double[][] P; public Bitmap Tex; public RectangleF Src; }

    class Preview
    {
        List<Quad> quads = new List<Quad>();
        Bitmap skin;

        public void Mannequin(Img hum, Img leg)
        {
            // Peau neutre : tete et mains claires, sous-vetement sombre.
            Img s = new Img(64, 32);
            for (int y = 0; y < 32; y++) for (int x = 0; x < 64; x++)
                {
                    bool headPart = y < 16 && x < 32;
                    if (headPart) s.Set(x, y, 206, 160, 128, 255); else s.Set(x, y, 58, 52, 48, 255);
                }
            skin = s.ToBitmap();
            Bitmap h = hum.ToBitmap(), l = leg.ToBitmap();
            // Corps du joueur.
            Box(-4, 24, -4, 4, 32, 4, skin, 0, 0, 8, 8, 8, 1);
            Box(-4, 12, -2, 4, 24, 2, skin, 16, 16, 8, 12, 4, 1);
            Box(-8, 12, -2, -4, 24, 2, skin, 40, 16, 4, 12, 4, 1);
            Box(4, 12, -2, 8, 24, 2, skin, 40, 16, 4, 12, 4, 1);
            Box(-4, 0, -2, 0, 12, 2, skin, 0, 16, 4, 12, 4, 1);
            Box(0, 0, -2, 4, 12, 2, skin, 0, 16, 4, 12, 4, 1);
            // Jambieres (gonflees de 0,5) puis plastron et bottes (gonfles de 1).
            Inflate(-4, 12, -2, 4, 24, 2, 0.5, l, 16, 16, 8, 12, 4);
            Inflate(-4, 0, -2, 0, 12, 2, 0.5, l, 0, 16, 4, 12, 4);
            Inflate(0, 0, -2, 4, 12, 2, 0.5, l, 0, 16, 4, 12, 4);
            Inflate(-4, 12, -2, 4, 24, 2, 1, h, 16, 16, 8, 12, 4);
            Inflate(-8, 12, -2, -4, 24, 2, 1, h, 40, 16, 4, 12, 4);
            Inflate(4, 12, -2, 8, 24, 2, 1, h, 40, 16, 4, 12, 4);
            Inflate(-4, 0, -2, 0, 12, 2, 1, h, 0, 16, 4, 12, 4);
            Inflate(0, 0, -2, 4, 12, 2, 1, h, 0, 16, 4, 12, 4);
        }

        void Inflate(double x1, double y1, double z1, double x2, double y2, double z2, double d, Bitmap tex, double u, double v, double w, double hh, double dp)
        {
            Box(x1 - d, y1 - d, z1 - d, x2 + d, y2 + d, z2 + d, tex, u, v, w, hh, dp, tex.Width / 64.0);
        }

        // Cube de peau : regions du patron classique (u,v ; largeur w, hauteur h, profondeur d), en px de peau x k.
        void Box(double x1, double y1, double z1, double x2, double y2, double z2, Bitmap tex, double u, double v, double w, double h, double d, double k)
        {
            Func<double, double, double, double, RectangleF> R = (a, b, c, e) => new RectangleF((float)(a * k), (float)(b * k), (float)(c * k), (float)(e * k));
            AddFace(Corners(x1, y1, z1, x2, y2, z2, "south"), tex, R(u + d, v + d, w, h));
            AddFace(Corners(x1, y1, z1, x2, y2, z2, "north"), tex, R(u + 2 * d + w, v + d, w, h));
            AddFace(Corners(x1, y1, z1, x2, y2, z2, "west"), tex, R(u, v + d, d, h));
            AddFace(Corners(x1, y1, z1, x2, y2, z2, "east"), tex, R(u + d + w, v + d, d, h));
            AddFace(Corners(x1, y1, z1, x2, y2, z2, "up"), tex, R(u + d, v, w, d));
            AddFace(Corners(x1, y1, z1, x2, y2, z2, "down"), tex, R(u + d + w, v, w, d));
        }

        public void AddModel(Model m, Img atlas, Dictionary<Cube, Dictionary<string, Rectangle>> uv, Xf xf)
        {
            Bitmap tex = atlas.ToBitmap();
            foreach (Cube c in m.Cubes)
                foreach (string f in FaceNames)
                {
                    double[][] p = Corners(c.X1, c.Y1, c.Z1, c.X2, c.Y2, c.Z2, f);
                    for (int i = 0; i < 4; i++)
                    {
                        double[] q = RotateElem(c, p[i]);
                        p[i] = xf(q[0], q[1], q[2]);
                    }
                    Rectangle r = uv[c][f];
                    AddFace(p, tex, new RectangleF(r.X, r.Y, r.Width, r.Height));
                }
        }

        void AddFace(double[][] p, Bitmap tex, RectangleF src) { quads.Add(new Quad { P = p, Tex = tex, Src = src }); }

        public void Render(string path, double yawDeg, double pitchDeg)
        {
            Directory.CreateDirectory(Path.GetDirectoryName(path));
            using (Bitmap b = RenderBmp(yawDeg, pitchDeg, 720, 17)) b.Save(path, ImageFormat.Png);
        }

        public Bitmap RenderBmp(double yawDeg, double pitchDeg, int size, double scale)
        {
            double yaw = yawDeg * Math.PI / 180, pitch = pitchDeg * Math.PI / 180;
            double[] d = { Math.Sin(yaw) * Math.Cos(pitch), Math.Sin(pitch), Math.Cos(yaw) * Math.Cos(pitch) };
            double[] right = { Math.Cos(yaw), 0, -Math.Sin(yaw) };
            double[] up = { d[1] * right[2] - d[2] * right[1], d[2] * right[0] - d[0] * right[2], d[0] * right[1] - d[1] * right[0] };
            // up = d x right ; on veut un "haut" qui monte : on le retourne s'il descend.
            if (up[1] < 0) { up[0] = -up[0]; up[1] = -up[1]; up[2] = -up[2]; }
            List<KeyValuePair<double, Quad>> order = new List<KeyValuePair<double, Quad>>();
            foreach (Quad q in quads)
            {
                double depth = 0;
                foreach (double[] p in q.P) depth += p[0] * d[0] + p[1] * d[1] + p[2] * d[2];
                order.Add(new KeyValuePair<double, Quad>(depth / 4, q));
            }
            Bitmap bmp = new Bitmap(size, size);
            using (Graphics g = Graphics.FromImage(bmp))
            {
                g.Clear(Color.FromArgb(12, 18, 27));
                g.InterpolationMode = InterpolationMode.NearestNeighbor;
                g.PixelOffsetMode = PixelOffsetMode.Half;
                foreach (KeyValuePair<double, Quad> kv in order.OrderBy(t => t.Key))
                {
                    Quad q = kv.Value;
                    PointF[] s = new PointF[4];
                    for (int i = 0; i < 4; i++)
                    {
                        double[] p = q.P[i];
                        double sx = p[0] * right[0] + p[1] * right[1] + p[2] * right[2];
                        double sy = p[0] * up[0] + p[1] * up[1] + p[2] * up[2];
                        s[i] = new PointF((float)(size / 2 + sx * scale), (float)(size * 0.9 - sy * scale));
                    }
                    double area = (s[1].X - s[0].X) * (s[2].Y - s[1].Y) - (s[1].Y - s[0].Y) * (s[2].X - s[1].X);
                    if (area <= 0.01) continue;
                    g.DrawImage(q.Tex, new[] { s[0], s[1], s[3] }, q.Src, GraphicsUnit.Pixel);
                }
            }
            return bmp;
        }
    }

    static double[] RotateElem(Cube c, double[] p)
    {
        if (c.Axis == ' ') return p;
        double a = c.Angle * Math.PI / 180, cs = Math.Cos(a), sn = Math.Sin(a);
        double x = p[0] - c.OX, y = p[1] - c.OY, z = p[2] - c.OZ;
        double nx = x, ny = y, nz = z;
        if (c.Axis == 'x') { ny = y * cs - z * sn; nz = y * sn + z * cs; }
        else if (c.Axis == 'y') { nx = x * cs + z * sn; nz = -x * sn + z * cs; }
        else { nx = x * cs - y * sn; ny = x * sn + y * cs; }
        return new[] { nx + c.OX, ny + c.OY, nz + c.OZ };
    }

    // Coins d'une face vus de l'exterieur : haut-gauche, haut-droite, bas-droite, bas-gauche (conventions UV du jeu).
    static double[][] Corners(double x1, double y1, double z1, double x2, double y2, double z2, string f)
    {
        switch (f)
        {
            case "north": return new[] { new[] { x2, y2, z1 }, new[] { x1, y2, z1 }, new[] { x1, y1, z1 }, new[] { x2, y1, z1 } };
            case "south": return new[] { new[] { x1, y2, z2 }, new[] { x2, y2, z2 }, new[] { x2, y1, z2 }, new[] { x1, y1, z2 } };
            case "east": return new[] { new[] { x2, y2, z2 }, new[] { x2, y2, z1 }, new[] { x2, y1, z1 }, new[] { x2, y1, z2 } };
            case "west": return new[] { new[] { x1, y2, z1 }, new[] { x1, y2, z2 }, new[] { x1, y1, z2 }, new[] { x1, y1, z1 } };
            case "up": return new[] { new[] { x1, y2, z1 }, new[] { x2, y2, z1 }, new[] { x2, y2, z2 }, new[] { x1, y2, z2 } };
            default: return new[] { new[] { x1, y1, z2 }, new[] { x2, y1, z2 }, new[] { x2, y1, z1 }, new[] { x1, y1, z1 } };
        }
    }
}
