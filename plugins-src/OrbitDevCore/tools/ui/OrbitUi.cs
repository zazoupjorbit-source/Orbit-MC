// Kit d'interface ORBIT en haute definition (4x) : redessine TOUTES les textures d'interface du jeu (26.3)
// aux couleurs d'un mode de jeu : inventaire, coffres et tous les conteneurs, menu Echap et options (boutons,
// curseurs, champs, onglets, ascenseurs, cases a cocher), info-bulles, fonds de menus, barre d'action, icones.
//
// Trois traitements :
//  - Conteneurs (gui/container/*.png, recipe_book.png) : la forme du jeu est relue pixel par pixel (panneau gris,
//    cases enfoncees, dessins) puis redessinee 4x plus fine : panneau en relief a la matiere du theme, cases en creux,
//    dessins (fleches, flammes, bulles) lisses et teintes, bandeaux clairs sous les titres que le jeu ecrit en gris fonce.
//  - Elements dessines a la main (boutons, curseurs, champs, onglets, info-bulles, barre d'action, fonds de menus).
//  - Tout le reste des sprites : agrandi 4x avec lissage des contours (Scale2x deux fois) et gris teintes au theme.
// Le jeu deduit l'echelle de la taille declaree (256 x 256 pour les conteneurs, "width/height" des .mcmeta pour les
// sprites) : une image 4x plus grande est simplement 4x plus fine, rien ne bouge a l'ecran.
// C# 5 (compile par Add-Type de PowerShell 5.1) : pas d'interpolation de chaines ni de "=>".
using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
using System.IO;
using System.Runtime.InteropServices;
using System.Text;

public static class OrbitUi
{
    public const int S = 4;

    // ------------------------------------------------------------------ Themes
    public class Theme
    {
        public string Name;
        public Color Bg1, Bg2, Out, Hi, Lo, In1, In2, Acc, Acc2, PlateA, PlateB, Ink;
        public int Pattern;   // 0 poussiere d'etoiles, 1 feuillage, 2 tole + rayures, 3 grille holo, 4 filigrane, 5 circuit, 6 confettis
        public int Ornament;  // 0 equerres, 1 feuilles, 2 rivets + danger, 3 traits neon, 4 equerres + losange, 5 noeuds, 6 etincelles
    }

    static Color H(string hex) { return H(hex, 255); }
    static Color H(string hex, int a)
    {
        int v = Convert.ToInt32(hex, 16);
        return Color.FromArgb(a, (v >> 16) & 255, (v >> 8) & 255, v & 255);
    }
    static Color A(Color c, int a) { return Color.FromArgb(Math.Max(0, Math.Min(255, a)), c); }

    static Theme Make(string name, string bg1, string bg2, string outc, string hi, string lo, string in1, string in2,
        string acc, string acc2, string pa, string pb, string ink, int pattern, int orn)
    {
        Theme t = new Theme();
        t.Name = name; t.Bg1 = H(bg1); t.Bg2 = H(bg2); t.Out = H(outc); t.Hi = H(hi); t.Lo = H(lo);
        t.In1 = H(in1); t.In2 = H(in2); t.Acc = H(acc); t.Acc2 = H(acc2); t.PlateA = H(pa); t.PlateB = H(pb); t.Ink = H(ink);
        t.Pattern = pattern; t.Ornament = orn;
        return t;
    }

    public static Theme Get(string name)
    {
        switch (name)
        {
            // Station en orbite (lobby, creatif, musee, map de dev) : coque bleu nuit, liseres bleu ciel, touche d'or.
            case "station": return Make(name, "1A2844", "0C1427", "03060D", "4A6CA6", "050A16", "060C1A", "0F1A31", "8DB8FF", "C9A24A", "DCE4F0", "8E9BB1", "1B2A48", 0, 0);
            // Survie : serre / dome vegetal, vert profond, liseres vert tendre et or chaud.
            case "survie": return Make(name, "1F3E2C", "0F2218", "030B06", "4E8A5A", "05110A", "07140D", "112A1B", "9BE07A", "E8C46A", "E7EFDA", "A3B48E", "1E3A24", 1, 1);
            // Extraction : sas militaire, acier canon, rouge alerte et ambre de signalisation.
            case "extraction": return Make(name, "2C2D31", "161719", "040404", "56595F", "09090A", "0C0D0F", "1B1C20", "E0412F", "F2B33D", "ECE5D3", "A9A18C", "2A2622", 2, 2);
            // Practice : arene holographique, violet nuit, neon magenta et cyan.
            case "practice": return Make(name, "201533", "0E0A1B", "05030A", "56408A", "07040F", "0A0716", "171030", "C46BFF", "3FE0FF", "ECE2F8", "A799C2", "2A1F40", 3, 3);
            // MMO : bleu nuit et or (meme DA que l'inventaire et la console d'OrbitMMO).
            case "mmo": return Make(name, "17233F", "0A1124", "04070E", "3E5486", "050A16", "070D1C", "0F1932", "C9A24A", "7FB2FF", "D3DAE4", "8A94A3", "1B2440", 4, 4);
            // Tycoon : usine en orbite, bleu canard, cyan et or.
            case "tycoon": return Make(name, "133B3D", "0A2125", "031012", "2F6A6C", "041315", "06171A", "0E2C30", "62CFF4", "E0B864", "DDEFEF", "93B0B0", "123234", 5, 5);
            // Vole mon Orbito : violet prune, orange et rose bonbon.
            case "vol": return Make(name, "3A2149", "1D1029", "0A0410", "6E4590", "0D0616", "130A1E", "261535", "FFB347", "FF6FB5", "F6E6EE", "BFA2B7", "3A2148", 6, 6);
        }
        throw new ArgumentException("Theme inconnu : " + name);
    }

    static Theme T;
    static string srcRoot, dstRoot;
    static HashSet<string> skip = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
    static HashSet<string> written = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
    static StringBuilder log = new StringBuilder();

    /** vanillaGui : dossier assets/minecraft/textures/gui du jeu ; outGui : le meme dans le pack ; skipFile : chemins (relatifs a gui/) fournis par le pack du mode. */
    public static string Run(string vanillaGui, string outGui, string theme, string skipFile)
    {
        T = Get(theme);
        srcRoot = vanillaGui; dstRoot = outGui;
        skip.Clear(); written.Clear(); log.Length = 0;
        if (!string.IsNullOrEmpty(skipFile) && File.Exists(skipFile))
        {
            foreach (string l in File.ReadAllLines(skipFile)) { string s = l.Trim().Replace('\\', '/'); if (s.Length > 0) skip.Add(s); }
        }

        // 1) Conteneurs.
        foreach (string f in Directory.GetFiles(Path.Combine(srcRoot, "container"), "*.png", SearchOption.AllDirectories))
        {
            string rel = Rel(f);
            if (rel == "container/gamemode_switcher.png") { Generic(rel); continue; }
            Container(rel);
        }
        Container("recipe_book.png");

        // 2) Dessines a la main.
        Widgets();
        Tooltip();
        Hotbar();
        Menus();

        // 3) Tout le reste des sprites (et des textures a plat utiles).
        foreach (string f in Directory.GetFiles(Path.Combine(srcRoot, "sprites"), "*.png", SearchOption.AllDirectories)) Generic(Rel(f));
        foreach (string f in Directory.GetFiles(Path.Combine(srcRoot, "advancements"), "*.png", SearchOption.TopDirectoryOnly)) Generic(Rel(f));
        Generic("book.png");
        log.Append(written.Count).Append(" textures ecrites pour le theme ").Append(theme).Append('\n');
        return log.ToString();
    }

    static string Rel(string full)
    {
        return full.Substring(srcRoot.Length).TrimStart('\\', '/').Replace('\\', '/');
    }

    static bool Want(string rel)
    {
        return !skip.Contains(rel) && !written.Contains(rel);
    }

    // ------------------------------------------------------------------ Pixels
    class Img
    {
        public int W, Hh; public int[] P;
        public Img(int w, int h) { W = w; Hh = h; P = new int[w * h]; }
        public int Get(int x, int y) { return (x < 0 || y < 0 || x >= W || y >= Hh) ? 0 : P[y * W + x]; }
    }

    static Img Load(string path)
    {
        using (Bitmap raw = new Bitmap(path))
        using (Bitmap b = new Bitmap(raw.Width, raw.Height, PixelFormat.Format32bppArgb))
        {
            using (Graphics g = Graphics.FromImage(b)) { g.CompositingMode = CompositingMode.SourceCopy; g.DrawImage(raw, 0, 0, raw.Width, raw.Height); }
            Img i = new Img(b.Width, b.Height);
            BitmapData d = b.LockBits(new Rectangle(0, 0, b.Width, b.Height), ImageLockMode.ReadOnly, PixelFormat.Format32bppArgb);
            Marshal.Copy(d.Scan0, i.P, 0, i.P.Length);
            b.UnlockBits(d);
            return i;
        }
    }

    static Bitmap ToBitmap(Img i)
    {
        Bitmap b = new Bitmap(i.W, i.Hh, PixelFormat.Format32bppArgb);
        BitmapData d = b.LockBits(new Rectangle(0, 0, i.W, i.Hh), ImageLockMode.WriteOnly, PixelFormat.Format32bppArgb);
        Marshal.Copy(i.P, 0, d.Scan0, i.P.Length);
        b.UnlockBits(d);
        return b;
    }

    static Img FromBitmap(Bitmap b)
    {
        Img i = new Img(b.Width, b.Height);
        BitmapData d = b.LockBits(new Rectangle(0, 0, b.Width, b.Height), ImageLockMode.ReadOnly, PixelFormat.Format32bppArgb);
        Marshal.Copy(d.Scan0, i.P, 0, i.P.Length);
        b.UnlockBits(d);
        return i;
    }

    static void SaveBmp(Bitmap b, string rel)
    {
        string p = Path.Combine(dstRoot, rel.Replace('/', Path.DirectorySeparatorChar));
        Directory.CreateDirectory(Path.GetDirectoryName(p));
        b.Save(p, ImageFormat.Png);
        written.Add(rel);
        // Le .mcmeta du jeu (nine_slice, animation) reste valable : tailles en unites d'interface.
        string meta = Path.Combine(srcRoot, rel.Replace('/', Path.DirectorySeparatorChar)) + ".mcmeta";
        if (File.Exists(meta)) File.Copy(meta, p + ".mcmeta", true);
    }

    static int Argb(int a, int r, int g, int b) { return (a << 24) | (r << 16) | (g << 8) | b; }
    static int Al(int c) { return (c >> 24) & 255; }
    static int Rr(int c) { return (c >> 16) & 255; }
    static int Gg(int c) { return (c >> 8) & 255; }
    static int Bb(int c) { return c & 255; }
    static int Rgb(int c) { return c & 0xFFFFFF; }

    static Color Mix(Color a, Color b, double t)
    {
        t = Math.Max(0, Math.Min(1, t));
        return Color.FromArgb((int)Math.Round(a.A + (b.A - a.A) * t), (int)Math.Round(a.R + (b.R - a.R) * t),
            (int)Math.Round(a.G + (b.G - a.G) * t), (int)Math.Round(a.B + (b.B - a.B) * t));
    }

    /** Pose c (alpha a 0..1) par-dessus le pixel. */
    static void Over(Img img, int idx, Color c, double a)
    {
        if (a <= 0) return;
        a = Math.Min(1, a) * c.A / 255.0;
        int d = img.P[idx];
        double da = Al(d) / 255.0;
        double oa = a + da * (1 - a);
        if (oa <= 0) return;
        double r = (c.R * a + Rr(d) * da * (1 - a)) / oa;
        double g = (c.G * a + Gg(d) * da * (1 - a)) / oa;
        double b = (c.B * a + Bb(d) * da * (1 - a)) / oa;
        img.P[idx] = Argb((int)Math.Round(oa * 255), Clamp(r), Clamp(g), Clamp(b));
    }
    static int Clamp(double v) { return (int)Math.Max(0, Math.Min(255, Math.Round(v))); }

    // Bruit deterministe.
    static double Hash(int x, int y, int s)
    {
        unchecked
        {
            uint h = (uint)(x * 374761393 + y * 668265263 + s * 1442695040);
            h = (h ^ (h >> 13)) * 1274126177u;
            h ^= h >> 16;
            return (h & 0xFFFFFF) / (double)0xFFFFFF;
        }
    }
    static double Smooth(double e0, double e1, double x)
    {
        double t = Math.Max(0, Math.Min(1, (x - e0) / (e1 - e0)));
        return t * t * (3 - 2 * t);
    }

    // ------------------------------------------------------------------ Champ de distance (chamfer)
    /** Distance (px de sortie) de chaque pixel dans le masque jusqu'au bord exterieur. */
    static float[] Distance(bool[] m, int w, int h)
    {
        const float INF = 1e9f, D1 = 1f, D2 = 1.4142f;
        float[] d = new float[w * h];
        for (int i = 0; i < d.Length; i++) d[i] = m[i] ? INF : 0;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
            {
                int i = y * w + x; if (d[i] == 0) continue;
                float v = d[i];
                v = Math.Min(v, (x > 0 ? d[i - 1] : 0) + D1);
                v = Math.Min(v, (y > 0 ? d[i - w] : 0) + D1);
                v = Math.Min(v, (x > 0 && y > 0 ? d[i - w - 1] : 0) + D2);
                v = Math.Min(v, (x < w - 1 && y > 0 ? d[i - w + 1] : 0) + D2);
                d[i] = v;
            }
        for (int y = h - 1; y >= 0; y--)
            for (int x = w - 1; x >= 0; x--)
            {
                int i = y * w + x; if (d[i] == 0) continue;
                float v = d[i];
                v = Math.Min(v, (x < w - 1 ? d[i + 1] : 0) + D1);
                v = Math.Min(v, (y < h - 1 ? d[i + w] : 0) + D1);
                v = Math.Min(v, (x < w - 1 && y < h - 1 ? d[i + w + 1] : 0) + D2);
                v = Math.Min(v, (x > 0 && y < h - 1 ? d[i + w - 1] : 0) + D2);
                d[i] = v;
            }
        return d;
    }

    /** Masque source (1x) agrandi S fois avec coins arrondis (flou puis seuil), en couverture 0..1. */
    static float[] SmoothMask(bool[] src, int w, int h, float blur)
    {
        int W = w * S, Hh = h * S;
        float[] a = new float[W * Hh];
        for (int y = 0; y < Hh; y++) for (int x = 0; x < W; x++) a[y * W + x] = src[(y / S) * w + (x / S)] ? 1f : 0f;
        int r = (int)Math.Round(blur);
        if (r > 0) { a = Box(a, W, Hh, r); a = Box(a, W, Hh, r); }
        float[] o = new float[W * Hh];
        for (int i = 0; i < o.Length; i++) o[i] = (float)Smooth(0.42, 0.58, a[i]);
        return o;
    }

    static float[] Box(float[] a, int w, int h, int r)
    {
        float[] t = new float[a.Length], o = new float[a.Length];
        for (int y = 0; y < h; y++)
        {
            float s = 0; int n = 0;
            for (int x = -r; x <= r; x++) if (x >= 0 && x < w) { s += a[y * w + x]; n++; }
            for (int x = 0; x < w; x++)
            {
                t[y * w + x] = s / (2 * r + 1);
                int xo = x - r, xi = x + r + 1;
                if (xo >= 0) s -= a[y * w + xo];
                if (xi < w) s += a[y * w + xi];
            }
        }
        for (int x = 0; x < w; x++)
        {
            float s = 0;
            for (int y = -r; y <= r; y++) if (y >= 0 && y < h) s += t[y * w + x];
            for (int y = 0; y < h; y++)
            {
                o[y * w + x] = s / (2 * r + 1);
                int yo = y - r, yi = y + r + 1;
                if (yo >= 0) s -= t[yo * w + x];
                if (yi < h) s += t[yi * w + x];
            }
        }
        return o;
    }

    // ------------------------------------------------------------------ Matiere du theme
    /** Couleur du panneau en (x, y) de sortie ; v = position verticale 0..1 dans le panneau. */
    static Color Material(int x, int y, double v)
    {
        Color c = Mix(T.Bg1, T.Bg2, 0.15 + v * 0.55);
        double n = (Hash(x / 2, y / 2, 7) - 0.5) * 0.035;
        double add = n;
        double fx = x / (double)S, fy = y / (double)S; // unites d'interface
        switch (T.Pattern)
        {
            case 0: // poussiere d'etoiles
                if (Hash(x / 3, y / 3, 11) > 0.9965) add += 0.10;
                break;
            case 1: // nervures vegetales diagonales
                add += 0.025 * Math.Sin((fx * 0.55 + fy * 0.9) + Math.Sin(fy * 0.21) * 1.7);
                break;
            case 2: // tole brossee
                add += (Hash(0, y, 3) - 0.5) * 0.05;
                break;
            case 3: // grille holographique
                if (((int)Math.Floor(fx) % 8 == 0 && x % S == 0) || ((int)Math.Floor(fy) % 8 == 0 && y % S == 0)) add += 0.05;
                if (y % 3 == 0) add -= 0.015;
                break;
            case 4: // filigrane d'etoiles et de points
                if (Hash(x / 4, y / 4, 19) > 0.997) add += 0.12;
                add += 0.012 * Math.Sin(fx * 0.3) * Math.Sin(fy * 0.3);
                break;
            case 5: // pistes de circuit
                if (((int)fy % 12 == 5 && (int)(fx / 7) % 3 != 0 && y % S < 1) || ((int)fx % 16 == 9 && (int)(fy / 6) % 2 == 0 && x % S < 1)) add += 0.06;
                break;
            case 6: // confettis
                if (Hash(x / 5, y / 5, 29) > 0.994) return Mix(c, Hash(x / 5, y / 5, 31) > 0.5 ? T.Acc : T.Acc2, 0.35);
                break;
        }
        return Lighten(c, add);
    }

    static Color Lighten(Color c, double k)
    {
        if (k >= 0) return Mix(c, Color.FromArgb(c.A, 255, 255, 255), k);
        return Mix(c, Color.FromArgb(c.A, 0, 0, 0), -k);
    }

    // ------------------------------------------------------------------ Conteneurs
    class Plate { public int X, Y, W, Hh; public Plate(int x, int y, int w, int h) { X = x; Y = y; W = w; Hh = h; } }

    static List<Plate> Plates(string rel)
    {
        List<Plate> l = new List<Plate>();
        string n = Path.GetFileNameWithoutExtension(rel);
        if (rel.StartsWith("container/creative_inventory/")) { l.Add(new Plate(5, 3, 76, 12)); return l; }
        switch (n)
        {
            case "recipe_book": case "beacon": return l;
            case "inventory": l.Add(new Plate(95, 5, 76, 11)); return l;
            case "generic_54": l.Add(new Plate(5, 3, 166, 12)); l.Add(new Plate(5, 127, 166, 11)); return l;
            case "hopper": l.Add(new Plate(5, 3, 166, 12)); l.Add(new Plate(5, 37, 166, 11)); return l;
            case "anvil": l.Add(new Plate(57, 3, 114, 12)); l.Add(new Plate(5, 70, 166, 11)); return l;
            case "smithing": l.Add(new Plate(41, 12, 130, 12)); l.Add(new Plate(5, 70, 166, 11)); return l;
            case "enchanting_table": l.Add(new Plate(5, 3, 52, 11)); l.Add(new Plate(5, 70, 166, 11)); return l;
            case "villager": l.Add(new Plate(4, 3, 92, 12)); l.Add(new Plate(101, 3, 170, 12)); l.Add(new Plate(104, 70, 167, 11)); return l;
            default: l.Add(new Plate(5, 3, 166, 12)); l.Add(new Plate(5, 70, 166, 11)); return l;
        }
    }

    static bool IsPanelColor(int rgb) { return rgb == 0xC6C6C6 || rgb == 0xFFFFFF || rgb == 0x555555 || rgb == 0x000000; }
    static bool IsInsetColor(int rgb) { return rgb == 0x8B8B8B || rgb == 0x373737; }

    static void Container(string rel)
    {
        if (!Want(rel)) return;
        string path = Path.Combine(srcRoot, rel.Replace('/', Path.DirectorySeparatorChar));
        if (!File.Exists(path)) return;
        Img src = Load(path);
        int w = src.W, h = src.Hh, n = w * h;
        bool[] opaque = new bool[n];
        for (int i = 0; i < n; i++) opaque[i] = Al(src.P[i]) > 8;
        // Le grand coffre est decoupe par le jeu (haut + bas a la ligne 126) : on recolle les deux morceaux.
        if (rel == "container/generic_54.png")
            for (int x = 0; x < w; x++) if (opaque[124 * w + x] && opaque[126 * w + x]) opaque[125 * w + x] = true;

        // Composantes connexes : panneaux (grandes) et petits dessins isoles.
        int[] comp = Components(opaque, w, h);
        Dictionary<int, int> sizes = new Dictionary<int, int>();
        for (int i = 0; i < n; i++) if (comp[i] > 0) { int c; sizes.TryGetValue(comp[i], out c); sizes[comp[i]] = c + 1; }
        bool[] panel = new bool[n];
        for (int i = 0; i < n; i++) panel[i] = comp[i] > 0 && sizes[comp[i]] >= 300;

        // Cases en creux : composantes de gris 8B8B8B.
        bool[] grey = new bool[n];
        for (int i = 0; i < n; i++) grey[i] = panel[i] && Rgb(src.P[i]) == 0x8B8B8B && Al(src.P[i]) > 200;
        int[] gc = Components(grey, w, h);
        Dictionary<int, int[]> box = new Dictionary<int, int[]>();
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++)
            {
                int c = gc[y * w + x]; if (c == 0) continue;
                int[] b; if (!box.TryGetValue(c, out b)) { b = new int[] { x, y, x, y, 0 }; box[c] = b; }
                b[0] = Math.Min(b[0], x); b[1] = Math.Min(b[1], y); b[2] = Math.Max(b[2], x); b[3] = Math.Max(b[3], y); b[4]++;
            }
        bool[] inset = new bool[n];
        bool[] covered = new bool[n];
        List<int[]> rects = new List<int[]>();
        foreach (KeyValuePair<int, int[]> kv in box)
        {
            int[] b = kv.Value;
            int bw = b[2] - b[0] + 1, bh = b[3] - b[1] + 1;
            if (b[4] < 4) continue;
            bool rect = b[4] >= bw * bh * 0.82;
            if (rect)
            {
                // Le creux du jeu = gris + biseau d'un pixel autour (dessine a part : les cases voisines se touchent).
                int x0 = b[0] - 1, y0 = b[1] - 1, x1 = b[2] + 1, y1 = b[3] + 1;
                rects.Add(new int[] { x0, y0, x1 - x0 + 1, y1 - y0 + 1 });
                for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) if (x >= 0 && y >= 0 && x < w && y < h) covered[y * w + x] = true;
            }
            else
            {
                for (int y = b[1] - 1; y <= b[3] + 1; y++) for (int x = b[0] - 1; x <= b[2] + 1; x++)
                    {
                        if (x < 0 || y < 0 || x >= w || y >= h) continue;
                        bool near = false;
                        for (int dy = -1; dy <= 1 && !near; dy++) for (int dx = -1; dx <= 1 && !near; dx++)
                            {
                                int xx = x + dx, yy = y + dy;
                                if (xx >= 0 && yy >= 0 && xx < w && yy < h && gc[yy * w + xx] == kv.Key) near = true;
                            }
                        if (near && IsInsetColor(Rgb(src.P[y * w + x])) || (gc[y * w + x] == kv.Key)) inset[y * w + x] = true;
                    }
            }
        }

        // Fenetre du personnage de l'inventaire : un grand creux comme les cases (le jeu la peint en noir).
        if (rel == "container/inventory.png")
        {
            rects.Add(new int[] { 25, 7, 52, 72 });
            for (int y = 7; y < 79; y++) for (int x = 25; x < 77; x++) covered[y * w + x] = true;
        }

        // Dessins : pixels qui ne sont ni panneau ni creux (fleches, flammes, bulles, contours internes).
        float[] dsrc = Distance(opaque, w, h);
        Img art = new Img(w, h);
        for (int i = 0; i < n; i++)
        {
            if (!opaque[i] || Al(src.P[i]) == 0) continue;
            int rgb = Rgb(src.P[i]);
            if (!panel[i]) { art.P[i] = src.P[i]; continue; }
            if (inset[i] || covered[i]) { if (!IsInsetColor(rgb) && rgb != 0xFFFFFF) art.P[i] = src.P[i]; continue; }
            if (!IsPanelColor(rgb)) { art.P[i] = src.P[i]; continue; }
            if (rgb != 0xC6C6C6 && dsrc[i] > 3.5f) art.P[i] = src.P[i];
        }

        // ---- Rendu 4x
        int W = w * S, HH = h * S;
        Img o = new Img(W, HH);

        // Panneau en relief.
        float[] cov = SmoothMask(panel, w, h, 3);
        bool[] pm = new bool[W * HH];
        for (int i = 0; i < pm.Length; i++) pm[i] = cov[i] > 0.5f;
        float[] dist = Distance(pm, W, HH);
        int[] pc = Components(panel, w, h);
        Dictionary<int, int[]> pbox = Boxes(pc, w, h);
        for (int y = 0; y < HH; y++)
            for (int x = 0; x < W; x++)
            {
                int i = y * W + x;
                if (cov[i] <= 0.001f) continue;
                int[] b; int c = pc[(y / S) * w + (x / S)];
                double v = 0.5;
                if (c > 0 && pbox.TryGetValue(c, out b)) v = (y / (double)S - b[1]) / Math.Max(1, b[3] - b[1]);
                Color col = Material(x, y, v);
                float d = dist[i];
                // Relief : normale tiree du champ de distance, lumiere en haut a gauche.
                double gx = Df(dist, W, HH, x + 1, y) - Df(dist, W, HH, x - 1, y);
                double gy = Df(dist, W, HH, x, y + 1) - Df(dist, W, HH, x, y - 1);
                double len = Math.Sqrt(gx * gx + gy * gy);
                double lit = len > 0 ? (gx * 0.55 + gy * 0.83) / len : 0;
                if (d < 2.2) col = Mix(T.Out, col, Smooth(0.6, 2.2, d) * 0.25);
                else if (d < 3.6) col = Mix(col, T.Acc, 0.55 * (1 - Math.Abs(d - 2.9) / 0.9));
                else if (d < 11)
                {
                    double k = (1 - Smooth(3.6, 11, d)) * lit;
                    col = k >= 0 ? Mix(col, T.Hi, k * 0.75) : Mix(col, T.Lo, -k * 0.8);
                }
                o.P[i] = 0;
                Over(o, i, col, cov[i]);
            }

        Bitmap bmp = ToBitmap(o);
        using (Graphics g = Graphics.FromImage(bmp))
        {
            HiQ(g);
            g.ScaleTransform(S, S);
            // Ornements aux coins des grands panneaux rectangulaires.
            foreach (KeyValuePair<int, int[]> kv in pbox)
            {
                int[] b = kv.Value;
                int bw = b[2] - b[0] + 1, bh = b[3] - b[1] + 1;
                if (bw < 60 || bh < 40) continue;
                Ornaments(g, b[0], b[1], bw, bh);
            }
            foreach (Plate p in Plates(rel)) DrawPlate(g, p.X, p.Y, p.W, p.Hh);
        }
        o = FromBitmap(bmp);
        bmp.Dispose();

        // Creux (cases et zones enfoncees).
        float[] icov = SmoothMask(inset, w, h, 1);
        bool[] im = new bool[W * HH];
        for (int i = 0; i < im.Length; i++) im[i] = icov[i] > 0.5f;
        float[] idist = Distance(im, W, HH);
        for (int y = 0; y < HH; y++)
            for (int x = 0; x < W; x++)
            {
                int i = y * W + x;
                if (icov[i] <= 0.001f) continue;
                float d = idist[i];
                double gx = Df(idist, W, HH, x + 1, y) - Df(idist, W, HH, x - 1, y);
                double gy = Df(idist, W, HH, x, y + 1) - Df(idist, W, HH, x, y - 1);
                double len = Math.Sqrt(gx * gx + gy * gy);
                double lit = len > 0 ? (gx * 0.55 + gy * 0.83) / len : 0;
                Color col = Mix(T.In1, T.In2, (y % (18 * S)) / (18.0 * S));
                if (d < 1.6) col = T.Out;
                else if (d < 6)
                {
                    double k = (1 - Smooth(1.6, 6, d)) * lit;
                    col = k >= 0 ? Mix(col, Color.Black, k * 0.7) : Mix(col, Mix(T.Acc, T.Hi, 0.4), -k * 0.6);
                }
                if (d >= 1.6 && d < 3) col = Mix(col, T.Acc, 0.22);
                Over(o, i, col, icov[i]);
            }
        foreach (int[] r in rects) Socket(o, r[0], r[1], r[2], r[3]);

        // Dessins lisses et teintes.
        Img art4 = Scale4(art);
        for (int i = 0; i < art4.P.Length; i++)
        {
            int c = art4.P[i];
            if (Al(c) == 0) continue;
            Color tc = TintArt(c);
            Over(o, i, tc, 1);
        }

        Bitmap outB = ToBitmap(o);
        SaveBmp(outB, rel);
        outB.Dispose();
    }

    /** Distance signee a un rectangle arrondi (negatif dedans). */
    static double RSdf(double px, double py, double cx, double cy, double hw, double hh, double r)
    {
        double qx = Math.Abs(px - cx) - (hw - r), qy = Math.Abs(py - cy) - (hh - r);
        double ox = Math.Max(qx, 0), oy = Math.Max(qy, 0);
        return Math.Sqrt(ox * ox + oy * oy) + Math.Min(Math.Max(qx, qy), 0) - r;
    }

    /** Case en creux (unites d'interface x, y, w, h) : biseau inverse, fond degrade, liseré d'accent. */
    static void Socket(Img o, int x, int y, int w, int h)
    {
        double X0 = x * S + 0.6, Y0 = y * S + 0.6, X1 = (x + w) * S - 0.6, Y1 = (y + h) * S - 0.6;
        double cx = (X0 + X1) / 2, cy = (Y0 + Y1) / 2, hw = (X1 - X0) / 2, hh = (Y1 - Y0) / 2, r = 1.6 * S;
        for (int py = (int)Math.Floor(Y0) - 1; py <= (int)Math.Ceiling(Y1); py++)
            for (int px = (int)Math.Floor(X0) - 1; px <= (int)Math.Ceiling(X1); px++)
            {
                if (px < 0 || py < 0 || px >= o.W || py >= o.Hh) continue;
                double fx = px + 0.5, fy = py + 0.5;
                double sd = RSdf(fx, fy, cx, cy, hw, hh, r);
                double cov = Math.Max(0, Math.Min(1, 0.5 - sd));
                if (cov <= 0) continue;
                double d = -sd;
                double gx = RSdf(fx + 1, fy, cx, cy, hw, hh, r) - RSdf(fx - 1, fy, cx, cy, hw, hh, r);
                double gy = RSdf(fx, fy + 1, cx, cy, hw, hh, r) - RSdf(fx, fy - 1, cx, cy, hw, hh, r);
                double len = Math.Sqrt(gx * gx + gy * gy);
                // Normale vers l'exterieur : en haut/gauche l'ombre, en bas/droite le reflet (creux).
                double lit = len > 0 ? -(gx * 0.55 + gy * 0.83) / len : 0;
                Color col = Mix(T.In1, T.In2, (fy - Y0) / Math.Max(1, Y1 - Y0));
                if (d < 1.5) col = T.Out;
                else if (d < 7)
                {
                    double k = (1 - Smooth(1.5, 7, d)) * lit;
                    col = k <= 0 ? Mix(col, Color.Black, -k * 0.75) : Mix(col, Mix(T.Acc, T.Hi, 0.5), k * 0.55);
                }
                if (d >= 1.5 && d < 2.8) col = Mix(col, T.Acc, 0.28);
                Over(o, py * o.W + px, col, cov);
            }
    }

    static double Df(float[] d, int w, int h, int x, int y)
    {
        x = Math.Max(0, Math.Min(w - 1, x)); y = Math.Max(0, Math.Min(h - 1, y));
        return Math.Min(d[y * w + x], 40);
    }

    /** Gris des dessins ramenes a la palette du theme ; couleurs gardees. */
    static Color TintArt(int c)
    {
        int r = Rr(c), g = Gg(c), b = Bb(c), a = Al(c);
        int mx = Math.Max(r, Math.Max(g, b)), mn = Math.Min(r, Math.Min(g, b));
        if (mx - mn > 14) return Color.FromArgb(a, r, g, b);
        double l = mx / 255.0;
        Color t = GreyRamp(l);
        return Color.FromArgb(a * t.A / 255, t.R, t.G, t.B);
    }

    /** Rampe de gris du jeu -> theme (0 = contour, 0x37 creux sombre, 0x55 ombre, 0x8B creux, 0xC6 panneau, 0xFF reflet). */
    static Color GreyRamp(double l)
    {
        double[] k = { 0, 0x37 / 255.0, 0x55 / 255.0, 0x8B / 255.0, 0xC6 / 255.0, 0xE0 / 255.0, 1 };
        Color[] c = { T.Out, T.In1, T.Lo, T.In2, Mix(T.Bg1, T.Hi, 0.35), Mix(T.Hi, T.Acc, 0.5), Mix(T.Acc, Color.White, 0.3) };
        for (int i = 1; i < k.Length; i++) if (l <= k[i]) return Mix(c[i - 1], c[i], (l - k[i - 1]) / (k[i] - k[i - 1]));
        return c[c.Length - 1];
    }

    static int[] Components(bool[] m, int w, int h)
    {
        int[] lab = new int[w * h];
        int next = 0;
        Stack<int> st = new Stack<int>();
        for (int i = 0; i < m.Length; i++)
        {
            if (!m[i] || lab[i] != 0) continue;
            next++; lab[i] = next; st.Push(i);
            while (st.Count > 0)
            {
                int p = st.Pop(); int x = p % w, y = p / w;
                if (x > 0 && m[p - 1] && lab[p - 1] == 0) { lab[p - 1] = next; st.Push(p - 1); }
                if (x < w - 1 && m[p + 1] && lab[p + 1] == 0) { lab[p + 1] = next; st.Push(p + 1); }
                if (y > 0 && m[p - w] && lab[p - w] == 0) { lab[p - w] = next; st.Push(p - w); }
                if (y < h - 1 && m[p + w] && lab[p + w] == 0) { lab[p + w] = next; st.Push(p + w); }
            }
        }
        return lab;
    }

    static Dictionary<int, int[]> Boxes(int[] lab, int w, int h)
    {
        Dictionary<int, int[]> box = new Dictionary<int, int[]>();
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++)
            {
                int c = lab[y * w + x]; if (c == 0) continue;
                int[] b; if (!box.TryGetValue(c, out b)) { b = new int[] { x, y, x, y }; box[c] = b; }
                b[0] = Math.Min(b[0], x); b[1] = Math.Min(b[1], y); b[2] = Math.Max(b[2], x); b[3] = Math.Max(b[3], y);
            }
        return box;
    }

    // ------------------------------------------------------------------ Scale2x x2 (lissage des contours de pixel art)
    static Img Scale2(Img s)
    {
        Img o = new Img(s.W * 2, s.Hh * 2);
        for (int y = 0; y < s.Hh; y++)
            for (int x = 0; x < s.W; x++)
            {
                int P = s.Get(x, y), A = s.Get(x, y - 1), B = s.Get(x + 1, y), C = s.Get(x - 1, y), D = s.Get(x, y + 1);
                if (Al(A) == 0) A = 0; if (Al(B) == 0) B = 0; if (Al(C) == 0) C = 0; if (Al(D) == 0) D = 0;
                int p = Al(P) == 0 ? 0 : P;
                int e0 = p, e1 = p, e2 = p, e3 = p;
                if (C == A && C != D && A != B) e0 = A;
                if (A == B && A != C && B != D) e1 = B;
                if (D == C && D != B && C != A) e2 = C;
                if (B == D && B != A && D != C) e3 = D;
                o.P[(2 * y) * o.W + 2 * x] = e0; o.P[(2 * y) * o.W + 2 * x + 1] = e1;
                o.P[(2 * y + 1) * o.W + 2 * x] = e2; o.P[(2 * y + 1) * o.W + 2 * x + 1] = e3;
            }
        return o;
    }

    static Img Scale4(Img s) { return Soften(Scale2(Scale2(s))); }

    /** Adoucit legerement les marches restantes (moyenne 3x3 ponderee sur les bords seulement). */
    static Img Soften(Img s)
    {
        Img o = new Img(s.W, s.Hh);
        for (int y = 0; y < s.Hh; y++)
            for (int x = 0; x < s.W; x++)
            {
                int c = s.P[y * s.W + x];
                bool edge = false;
                for (int dy = -1; dy <= 1 && !edge; dy++) for (int dx = -1; dx <= 1 && !edge; dx++) if (s.Get(x + dx, y + dy) != c) edge = true;
                if (!edge) { o.P[y * s.W + x] = c; continue; }
                double sa = 0, sr = 0, sg = 0, sb = 0, sw = 0;
                for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++)
                    {
                        int q = s.Get(x + dx, y + dy);
                        double wt = (dx == 0 && dy == 0) ? 4 : (dx == 0 || dy == 0 ? 1 : 0.5);
                        double qa = Al(q) / 255.0;
                        sa += qa * wt; sr += Rr(q) * qa * wt; sg += Gg(q) * qa * wt; sb += Bb(q) * qa * wt; sw += wt;
                    }
                if (sa <= 0) { o.P[y * s.W + x] = 0; continue; }
                o.P[y * s.W + x] = Argb(Clamp(sa / sw * 255), Clamp(sr / sa), Clamp(sg / sa), Clamp(sb / sa));
            }
        return o;
    }

    // ------------------------------------------------------------------ Generique
    static void Generic(string rel)
    {
        if (!Want(rel)) return;
        string path = Path.Combine(srcRoot, rel.Replace('/', Path.DirectorySeparatorChar));
        if (!File.Exists(path)) return;
        Img s = Load(path);
        if (s.W * s.Hh > 512 * 512) return;
        // Quelques sprites doivent rester exacts (viseur en negatif, couleurs de reference).
        string n = rel.ToLowerInvariant();
        if (n.Contains("crosshair")) return;
        bool recolor = !(n.StartsWith("sprites/hud/") || n.StartsWith("sprites/boss_bar/") || n.StartsWith("sprites/icon/")
            || n.StartsWith("sprites/pause_menu/") || n.StartsWith("sprites/server_list/") || n.StartsWith("sprites/world_list/")
            || n.StartsWith("sprites/realm_status/") || n.StartsWith("sprites/friends/") || n.StartsWith("sprites/social_interactions/")
            || n.StartsWith("sprites/statistics/") || n.StartsWith("sprites/notification/") || n.StartsWith("sprites/spectator/")
            || n.StartsWith("sprites/player_list/") || n.StartsWith("sprites/pending_invite/") || n.StartsWith("sprites/transferable_list/")
            || n == "book.png" || n.StartsWith("sprites/container/bundle/") || n.StartsWith("sprites/mob_effect"));
        bool ghost = n.StartsWith("sprites/container/slot/");
        Img o = Scale4(s);
        if (recolor || ghost)
        {
            for (int i = 0; i < o.P.Length; i++)
            {
                int c = o.P[i]; if (Al(c) == 0) continue;
                int r = Rr(c), g = Gg(c), b = Bb(c);
                int mx = Math.Max(r, Math.Max(g, b)), mn = Math.Min(r, Math.Min(g, b));
                if (mx - mn > 14) continue;
                Color t = ghost ? Mix(Mix(T.Acc, T.Bg2, 0.35), Mix(T.Acc, Color.White, 0.4), mx / 255.0) : GreyRamp(mx / 255.0);
                int a = ghost ? Al(c) * 3 / 4 : Al(c) * t.A / 255;
                o.P[i] = Argb(a, t.R, t.G, t.B);
            }
        }
        Bitmap b2 = ToBitmap(o);
        SaveBmp(b2, rel);
        b2.Dispose();
    }

    // ------------------------------------------------------------------ Dessin vectoriel
    static void HiQ(Graphics g)
    {
        g.SmoothingMode = SmoothingMode.AntiAlias;
        g.PixelOffsetMode = PixelOffsetMode.HighQuality;
        g.InterpolationMode = InterpolationMode.HighQualityBicubic;
        g.CompositingQuality = CompositingQuality.HighQuality;
    }

    class Cv { public Bitmap B; public Graphics G; }
    static Cv New(int w, int h)
    {
        Cv c = new Cv();
        c.B = new Bitmap(w * S, h * S, PixelFormat.Format32bppArgb);
        c.G = Graphics.FromImage(c.B);
        HiQ(c.G);
        c.G.ScaleTransform(S, S);
        return c;
    }
    static void Done(Cv c, string rel)
    {
        c.G.Dispose();
        if (Want(rel)) SaveBmp(c.B, rel);
        c.B.Dispose();
    }

    static GraphicsPath RR(float x, float y, float w, float h, float r)
    {
        GraphicsPath p = new GraphicsPath();
        r = Math.Min(r, Math.Min(w, h) / 2f);
        if (r <= 0.01f) { p.AddRectangle(new RectangleF(x, y, w, h)); return p; }
        float d = r * 2;
        p.AddArc(x, y, d, d, 180, 90);
        p.AddArc(x + w - d, y, d, d, 270, 90);
        p.AddArc(x + w - d, y + h - d, d, d, 0, 90);
        p.AddArc(x, y + h - d, d, d, 90, 90);
        p.CloseFigure();
        return p;
    }

    static void FillV(Graphics g, GraphicsPath p, RectangleF r, Color top, Color bottom)
    {
        RectangleF rr = new RectangleF(r.X, r.Y - 0.01f, r.Width, r.Height + 0.02f);
        using (LinearGradientBrush b = new LinearGradientBrush(rr, top, bottom, 90f)) g.FillPath(b, p);
    }
    static void Stroke(Graphics g, GraphicsPath p, Color c, float w)
    {
        using (Pen pen = new Pen(c, w)) { pen.LineJoin = LineJoin.Round; g.DrawPath(pen, p); }
    }
    static void Line(Graphics g, Color c, float w, float x0, float y0, float x1, float y1)
    {
        using (Pen p = new Pen(c, w)) { p.StartCap = LineCap.Round; p.EndCap = LineCap.Round; g.DrawLine(p, x0, y0, x1, y1); }
    }
    static void Diamond(Graphics g, float cx, float cy, float r, Color fill)
    {
        using (GraphicsPath p = new GraphicsPath())
        {
            p.AddPolygon(new PointF[] { new PointF(cx, cy - r), new PointF(cx + r, cy), new PointF(cx, cy + r), new PointF(cx - r, cy) });
            using (SolidBrush b = new SolidBrush(fill)) g.FillPath(b, p);
        }
    }
    static void Dot(Graphics g, float cx, float cy, float r, Color fill)
    {
        using (SolidBrush b = new SolidBrush(fill)) g.FillEllipse(b, cx - r, cy - r, r * 2, r * 2);
    }

    /** Bandeau clair sous un titre ecrit par le jeu en gris fonce. */
    static void DrawPlate(Graphics g, float x, float y, float w, float h)
    {
        using (GraphicsPath p = RR(x, y, w, h, 2f))
        {
            FillV(g, p, new RectangleF(x, y, w, h), T.PlateA, T.PlateB);
            using (GraphicsPath hl = RR(x + 0.8f, y + 0.6f, w - 1.6f, h * 0.38f, 1.4f))
            using (SolidBrush b = new SolidBrush(Color.FromArgb(80, 255, 255, 255))) g.FillPath(b, hl);
            Stroke(g, p, T.Out, 0.6f);
        }
        // Encoche d'accent a droite (le texte commence a gauche).
        using (GraphicsPath tab = RR(x + w - 7, y + 2.2f, 4.5f, h - 4.4f, 1f))
        using (SolidBrush b = new SolidBrush(A(T.Acc, 230))) g.FillPath(b, tab);
        Line(g, A(T.Ink, 120), 0.35f, x + w - 10, y + 2.5f, x + w - 10, y + h - 2.5f);
    }

    static void Ornaments(Graphics g, float x, float y, float w, float h)
    {
        float m = 2.2f;
        switch (T.Ornament)
        {
            case 0: case 4:
                Brackets(g, x + m, y + m, w - 2 * m, h - 2 * m, A(T.Ornament == 4 ? T.Acc : T.Acc, 220), 0.7f, 5f);
                if (T.Ornament == 4) { Diamond(g, x + w / 2f, y + h - 1.6f, 1.6f, T.Acc); Diamond(g, x + w / 2f, y + h - 1.6f, 0.7f, Color.FromArgb(255, 255, 246, 200)); }
                break;
            case 1:
                Leaf(g, x + 3, y + h - 3, 1); Leaf(g, x + w - 3, y + h - 3, -1);
                break;
            case 2:
                Rivet(g, x + 3.2f, y + 3.2f); Rivet(g, x + w - 3.2f, y + 3.2f); Rivet(g, x + 3.2f, y + h - 3.2f); Rivet(g, x + w - 3.2f, y + h - 3.2f);
                Hazard(g, x + w / 2f - 14, y + h - 2.8f, 28, 1.6f);
                break;
            case 3:
                Brackets(g, x + m, y + m, w - 2 * m, h - 2 * m, A(T.Acc, 230), 0.5f, 7f);
                Line(g, A(T.Acc2, 200), 0.5f, x + w / 2f - 10, y + h - 1.6f, x + w / 2f + 10, y + h - 1.6f);
                break;
            case 5:
                Node(g, x + 3, y + 3); Node(g, x + w - 3, y + 3); Node(g, x + 3, y + h - 3); Node(g, x + w - 3, y + h - 3);
                break;
            case 6:
                Spark(g, x + 3.5f, y + 3.5f, 2.2f, T.Acc); Spark(g, x + w - 3.5f, y + h - 3.5f, 2.2f, T.Acc2);
                Spark(g, x + w - 3.5f, y + 3.5f, 1.4f, T.Acc2); Spark(g, x + 3.5f, y + h - 3.5f, 1.4f, T.Acc);
                break;
        }
    }

    static void Brackets(Graphics g, float x, float y, float w, float h, Color c, float t, float l)
    {
        using (Pen p = new Pen(c, t))
        {
            p.StartCap = LineCap.Round; p.EndCap = LineCap.Round; p.LineJoin = LineJoin.Round;
            g.DrawLines(p, new PointF[] { new PointF(x, y + l), new PointF(x, y), new PointF(x + l, y) });
            g.DrawLines(p, new PointF[] { new PointF(x + w - l, y), new PointF(x + w, y), new PointF(x + w, y + l) });
            g.DrawLines(p, new PointF[] { new PointF(x, y + h - l), new PointF(x, y + h), new PointF(x + l, y + h) });
            g.DrawLines(p, new PointF[] { new PointF(x + w - l, y + h), new PointF(x + w, y + h), new PointF(x + w, y + h - l) });
        }
    }
    static void Leaf(Graphics g, float x, float y, int dir)
    {
        using (GraphicsPath p = new GraphicsPath())
        {
            p.AddBezier(x, y, x + dir * 1, y - 4, x + dir * 4, y - 5, x + dir * 6, y - 5.5f);
            p.AddBezier(x + dir * 6, y - 5.5f, x + dir * 5, y - 2, x + dir * 3, y - 0.5f, x, y);
            using (SolidBrush b = new SolidBrush(A(T.Acc, 210))) g.FillPath(b, p);
        }
        Line(g, A(T.Bg2, 200), 0.3f, x, y, x + dir * 5f, y - 4.8f);
    }
    static void Rivet(Graphics g, float cx, float cy)
    {
        Dot(g, cx, cy, 1.25f, T.Out);
        using (GraphicsPath p = new GraphicsPath())
        {
            p.AddEllipse(cx - 1f, cy - 1f, 2f, 2f);
            using (LinearGradientBrush b = new LinearGradientBrush(new RectangleF(cx - 1, cy - 1, 2, 2), H("9A9DA3"), H("3A3C40"), 45f)) g.FillPath(b, p);
        }
    }
    static void Hazard(Graphics g, float x, float y, float w, float h)
    {
        GraphicsState st = g.Save();
        using (GraphicsPath clip = RR(x, y, w, h, 0.6f))
        {
            g.SetClip(clip);
            using (SolidBrush b = new SolidBrush(T.Acc2)) g.FillRectangle(b, x, y, w, h);
            for (float s = x - h; s < x + w + h; s += 2.4f)
                using (GraphicsPath p = new GraphicsPath())
                {
                    p.AddPolygon(new PointF[] { new PointF(s, y + h), new PointF(s + 1.2f, y + h), new PointF(s + 1.2f + h, y), new PointF(s + h, y) });
                    using (SolidBrush b = new SolidBrush(H("141414"))) g.FillPath(b, p);
                }
        }
        g.Restore(st);
    }
    static void Node(Graphics g, float cx, float cy)
    {
        Dot(g, cx, cy, 1.4f, A(T.Acc, 90));
        Dot(g, cx, cy, 0.8f, T.Acc);
        Dot(g, cx, cy, 0.35f, Color.White);
    }
    static void Spark(Graphics g, float cx, float cy, float r, Color c)
    {
        using (GraphicsPath p = new GraphicsPath())
        {
            float q = r * 0.28f;
            p.AddPolygon(new PointF[] { new PointF(cx, cy - r), new PointF(cx + q, cy - q), new PointF(cx + r, cy), new PointF(cx + q, cy + q),
                new PointF(cx, cy + r), new PointF(cx - q, cy + q), new PointF(cx - r, cy), new PointF(cx - q, cy - q) });
            using (SolidBrush b = new SolidBrush(c)) g.FillPath(b, p);
        }
    }

    // ------------------------------------------------------------------ Widgets (menu Echap, options...)
    static void Widgets()
    {
        string W = "sprites/widget/";
        Button(W + "button.png", 0); Button(W + "button_highlighted.png", 1); Button(W + "button_disabled.png", 2);
        Button("sprites/dialog/warning_button.png", 3); Button("sprites/dialog/warning_button_highlighted.png", 4); Button("sprites/dialog/warning_button_disabled.png", 2);
        Slider(W + "slider.png", false); Slider(W + "slider_highlighted.png", true);
        Handle(W + "slider_handle.png", false); Handle(W + "slider_handle_highlighted.png", true);
        Field(W + "text_field.png", false); Field(W + "text_field_highlighted.png", true);
        Checkbox(W + "checkbox.png", false, false); Checkbox(W + "checkbox_highlighted.png", true, false);
        Checkbox(W + "checkbox_selected.png", false, true); Checkbox(W + "checkbox_selected_highlighted.png", true, true);
        Tab(W + "tab.png", false, false); Tab(W + "tab_highlighted.png", true, false);
        Tab(W + "tab_selected.png", false, true); Tab(W + "tab_selected_highlighted.png", true, true);
        Scroller(W + "scroller.png"); ScrollerBg(W + "scroller_background.png");
        SlotHighlight("sprites/container/slot_highlight_back.png", true); SlotHighlight("sprites/container/slot_highlight_front.png", false);
    }

    /** state : 0 normal, 1 survol, 2 desactive, 3 alerte, 4 alerte survolee. */
    static void Button(string rel, int state)
    {
        if (!Want(rel)) return;
        Cv c = New(200, 20); Graphics g = c.G;
        bool warn = state >= 3, hot = state == 1 || state == 4, off = state == 2;
        Color top = warn ? Mix(H("5A1A1A"), T.Bg1, 0.3) : Lighten(T.Bg1, 0.06);
        Color bot = warn ? H("2A0B0B") : T.Bg2;
        Color rim = warn ? H("FF6A5A") : T.Acc;
        if (hot) { top = Lighten(Mix(top, rim, 0.22), 0.04); bot = Mix(bot, rim, 0.12); }
        if (off) { top = Mix(T.Bg2, H("2A2A2A"), 0.6); bot = Mix(T.Bg2, H("141414"), 0.6); rim = H("4A4A4A"); }
        using (GraphicsPath o = RR(0.25f, 0.25f, 199.5f, 19.5f, 2.6f)) { using (SolidBrush b = new SolidBrush(T.Out)) g.FillPath(b, o); }
        using (GraphicsPath p = RR(0.9f, 0.9f, 198.2f, 18.2f, 2f))
        {
            FillV(g, p, new RectangleF(0.9f, 0.9f, 198.2f, 18.2f), top, bot);
            Stroke(g, p, A(rim, off ? 140 : (hot ? 255 : 170)), hot ? 0.7f : 0.45f);
        }
        // Reflet haut et ombre basse (dans la bordure de 3 px : tient a toutes les largeurs).
        Line(g, Color.FromArgb(off ? 25 : 60, 255, 255, 255), 0.4f, 2.5f, 1.9f, 197.5f, 1.9f);
        Line(g, Color.FromArgb(110, 0, 0, 0), 0.5f, 2.5f, 18.2f, 197.5f, 18.2f);
        if (!off)
        {
            // Puces d'accent aux extremites (dans les 3 px de bordure de gauche et de droite).
            using (GraphicsPath l = RR(1.5f, 6.5f, 1.1f, 7f, 0.5f)) using (SolidBrush b = new SolidBrush(A(hot ? Mix(rim, Color.White, 0.3) : rim, hot ? 255 : 200))) g.FillPath(b, l);
            using (GraphicsPath r = RR(197.4f, 6.5f, 1.1f, 7f, 0.5f)) using (SolidBrush b = new SolidBrush(A(hot ? Mix(rim, Color.White, 0.3) : rim, hot ? 255 : 200))) g.FillPath(b, r);
        }
        Done(c, rel);
    }

    static void Slider(string rel, bool hot)
    {
        if (!Want(rel)) return;
        Cv c = New(200, 20); Graphics g = c.G;
        using (SolidBrush b = new SolidBrush(T.Out)) g.FillRectangle(b, 0, 0, 200, 20);
        using (GraphicsPath p = new GraphicsPath())
        {
            p.AddRectangle(new RectangleF(0.6f, 0.6f, 198.8f, 18.8f));
            FillV(g, p, new RectangleF(0.6f, 0.6f, 198.8f, 18.8f), Mix(T.In1, Color.Black, 0.3), T.In2);
        }
        using (Pen p = new Pen(A(T.Acc, hot ? 255 : 110), hot ? 0.6f : 0.4f)) g.DrawRectangle(p, 0.6f, 0.6f, 198.8f, 18.8f);
        Done(c, rel);
    }

    static void Handle(string rel, bool hot)
    {
        if (!Want(rel)) return;
        Cv c = New(8, 20); Graphics g = c.G;
        using (GraphicsPath o = RR(0.2f, 0.2f, 7.6f, 19.6f, 1.6f)) using (SolidBrush b = new SolidBrush(T.Out)) g.FillPath(b, o);
        using (GraphicsPath p = RR(0.8f, 0.8f, 6.4f, 18.4f, 1.2f))
        {
            Color a = hot ? Mix(T.Acc, Color.White, 0.35) : T.Acc, bb = hot ? T.Acc : Mix(T.Acc, T.Bg2, 0.45);
            FillV(g, p, new RectangleF(0.8f, 0.8f, 6.4f, 18.4f), a, bb);
        }
        Line(g, Color.FromArgb(140, 255, 255, 255), 0.35f, 1.6f, 1.5f, 6.4f, 1.5f);
        Line(g, A(T.Out, 160), 0.4f, 4, 7, 4, 13);
        Done(c, rel);
    }

    static void Field(string rel, bool hot)
    {
        if (!Want(rel)) return;
        Cv c = New(200, 20); Graphics g = c.G;
        using (SolidBrush b = new SolidBrush(hot ? T.Acc : Mix(T.Acc, T.Bg2, 0.55))) g.FillRectangle(b, 0, 0, 200, 20);
        using (SolidBrush b = new SolidBrush(Color.FromArgb(250, Mix(T.Out, T.In1, 0.5)))) g.FillRectangle(b, 1, 1, 198, 18);
        Done(c, rel);
    }

    static void Checkbox(string rel, bool hot, bool on)
    {
        if (!Want(rel)) return;
        Cv c = New(20, 20); Graphics g = c.G;
        using (GraphicsPath o = RR(0.3f, 0.3f, 19.4f, 19.4f, 2.4f)) using (SolidBrush b = new SolidBrush(T.Out)) g.FillPath(b, o);
        using (GraphicsPath p = RR(1f, 1f, 18f, 18f, 1.8f))
        {
            FillV(g, p, new RectangleF(1, 1, 18, 18), Mix(T.In1, Color.Black, 0.2), T.In2);
            Stroke(g, p, A(T.Acc, hot ? 255 : 150), hot ? 0.7f : 0.45f);
        }
        if (on)
        {
            using (Pen p = new Pen(hot ? Mix(T.Acc, Color.White, 0.35) : T.Acc, 2.2f))
            {
                p.StartCap = LineCap.Round; p.EndCap = LineCap.Round; p.LineJoin = LineJoin.Round;
                g.DrawLines(p, new PointF[] { new PointF(5, 10.5f), new PointF(8.6f, 14), new PointF(15, 6) });
            }
        }
        Done(c, rel);
    }

    static void Tab(string rel, bool hot, bool sel)
    {
        if (!Want(rel)) return;
        Cv c = New(130, 24); Graphics g = c.G;
        Color top = sel ? Lighten(T.Bg1, 0.08) : Mix(T.Bg2, T.Out, 0.3), bot = sel ? T.Bg1 : T.Bg2;
        if (hot) top = Mix(top, T.Acc, 0.18);
        using (GraphicsPath o = new GraphicsPath())
        {
            o.AddLine(0.3f, 24, 0.3f, 2.3f); o.AddArc(0.3f, 0.3f, 4, 4, 180, 90); o.AddArc(125.7f, 0.3f, 4, 4, 270, 90); o.AddLine(129.7f, 2.3f, 129.7f, 24);
            o.CloseFigure();
            using (SolidBrush b = new SolidBrush(T.Out)) g.FillPath(b, o);
        }
        using (GraphicsPath p = new GraphicsPath())
        {
            p.AddLine(1f, 24, 1f, 2.6f); p.AddArc(1f, 1f, 3.2f, 3.2f, 180, 90); p.AddArc(125.8f, 1f, 3.2f, 3.2f, 270, 90); p.AddLine(129f, 2.6f, 129f, 24);
            p.CloseFigure();
            FillV(g, p, new RectangleF(1, 1, 128, 23), top, bot);
        }
        Line(g, A(sel ? T.Acc : T.Hi, sel ? 255 : (hot ? 220 : 120)), sel ? 0.8f : 0.5f, 2.6f, 1.5f, 127.4f, 1.5f);
        Done(c, rel);
    }

    static void Scroller(string rel)
    {
        if (!Want(rel)) return;
        Cv c = New(6, 32); Graphics g = c.G;
        using (GraphicsPath o = RR(0.2f, 0.2f, 5.6f, 31.6f, 1.4f)) using (SolidBrush b = new SolidBrush(T.Out)) g.FillPath(b, o);
        using (GraphicsPath p = RR(0.8f, 0.8f, 4.4f, 30.4f, 1f)) FillV(g, p, new RectangleF(0.8f, 0.8f, 4.4f, 30.4f), Mix(T.Acc, Color.White, 0.2), Mix(T.Acc, T.Bg2, 0.4));
        Done(c, rel);
    }
    static void ScrollerBg(string rel)
    {
        if (!Want(rel)) return;
        Cv c = New(6, 32); Graphics g = c.G;
        using (SolidBrush b = new SolidBrush(Color.FromArgb(230, T.Out))) g.FillRectangle(b, 0, 0, 6, 32);
        using (SolidBrush b = new SolidBrush(Color.FromArgb(230, T.In1))) g.FillRectangle(b, 1, 1, 4, 30);
        Done(c, rel);
    }

    static void SlotHighlight(string rel, bool back)
    {
        if (!Want(rel)) return;
        Cv c = New(24, 24); Graphics g = c.G;
        if (back)
        {
            using (GraphicsPath p = RR(4, 4, 16, 16, 1.5f)) using (SolidBrush b = new SolidBrush(A(Mix(T.Acc, Color.White, 0.25), 70))) g.FillPath(b, p);
        }
        else
        {
            using (GraphicsPath p = RR(3.3f, 3.3f, 17.4f, 17.4f, 2f)) Stroke(g, p, A(Mix(T.Acc, Color.White, 0.3), 235), 0.8f);
            using (GraphicsPath p = RR(2.5f, 2.5f, 19f, 19f, 2.6f)) Stroke(g, p, A(T.Acc, 80), 0.8f);
        }
        Done(c, rel);
    }

    // ------------------------------------------------------------------ Info-bulles
    static void Tooltip()
    {
        // Le jeu etire background (bord 9) et frame (bord 10) autour du texte : memes zones que l'original.
        string bg = "sprites/tooltip/background.png", fr = "sprites/tooltip/frame.png";
        if (Want(bg))
        {
            Img v = Load(Path.Combine(srcRoot, bg.Replace('/', '\\')));
            int[] bb = OpaqueBox(v);
            Cv c = New(100, 100); Graphics g = c.G;
            using (GraphicsPath p = RR(bb[0], bb[1], bb[2] - bb[0] + 1, bb[3] - bb[1] + 1, 2.2f))
                FillV(g, p, new RectangleF(bb[0], bb[1], bb[2] - bb[0] + 1, bb[3] - bb[1] + 1), Color.FromArgb(244, Lighten(T.Bg2, -0.25)), Color.FromArgb(244, Lighten(T.Bg2, -0.45)));
            Done(c, bg);
        }
        if (Want(fr))
        {
            Img v = Load(Path.Combine(srcRoot, fr.Replace('/', '\\')));
            int[] bb = OpaqueBox(v);
            Cv c = New(100, 100); Graphics g = c.G;
            float x = bb[0] + 0.5f, y = bb[1] + 0.5f, w = bb[2] - bb[0], h = bb[3] - bb[1];
            using (GraphicsPath p = RR(x, y, w, h, 1.6f))
            using (LinearGradientBrush b = new LinearGradientBrush(new RectangleF(x - 1, y - 1, w + 2, h + 2), T.Acc, A(T.Acc2, 200), 90f))
            using (Pen pen = new Pen(b, 0.8f)) g.DrawPath(pen, p);
            Done(c, fr);
        }
    }

    static int[] OpaqueBox(Img v)
    {
        int x0 = v.W, y0 = v.Hh, x1 = -1, y1 = -1;
        for (int y = 0; y < v.Hh; y++) for (int x = 0; x < v.W; x++)
                if (Al(v.P[y * v.W + x]) > 0) { x0 = Math.Min(x0, x); y0 = Math.Min(y0, y); x1 = Math.Max(x1, x); y1 = Math.Max(y1, y); }
        if (x1 < 0) return new int[] { 0, 0, v.W - 1, v.Hh - 1 };
        return new int[] { x0, y0, x1, y1 };
    }

    // ------------------------------------------------------------------ Barre d'action, experience
    static void Hotbar()
    {
        string hb = "sprites/hud/hotbar.png";
        if (Want(hb))
        {
            Cv c = New(182, 22); Graphics g = c.G;
            using (GraphicsPath o = RR(0.3f, 0.3f, 181.4f, 21.4f, 3f))
            {
                FillV(g, o, new RectangleF(0, 0, 182, 22), Color.FromArgb(225, Lighten(T.Bg1, 0.03)), Color.FromArgb(235, T.Bg2));
                Stroke(g, o, T.Out, 0.6f);
            }
            using (GraphicsPath o = RR(0.9f, 0.9f, 180.2f, 20.2f, 2.5f)) Stroke(g, o, A(T.Acc, 120), 0.35f);
            for (int i = 0; i < 9; i++)
            {
                float x = 1 + 20 * i;
                using (GraphicsPath s = RR(x + 1.2f, 2.2f, 17.6f, 17.6f, 1.6f))
                {
                    FillV(g, s, new RectangleF(x + 1, 2, 18, 18), Color.FromArgb(220, Mix(T.In1, Color.Black, 0.25)), Color.FromArgb(220, T.In2));
                    Stroke(g, s, A(T.Hi, 120), 0.3f);
                }
                if (i > 0) Diamond(g, x, 11, 0.8f, A(T.Acc, 170));
            }
            Done(c, hb);
        }
        string sel = "sprites/hud/hotbar_selection.png";
        if (Want(sel))
        {
            Cv c = New(24, 23); Graphics g = c.G;
            using (GraphicsPath p = RR(0.6f, 0.6f, 22.8f, 22.2f, 2.6f)) Stroke(g, p, T.Out, 1.4f);
            using (GraphicsPath p = RR(0.8f, 0.8f, 22.4f, 21.8f, 2.4f)) Stroke(g, p, Mix(T.Acc, Color.White, 0.25), 0.9f);
            using (GraphicsPath p = RR(2f, 2f, 20f, 19.4f, 1.8f)) Stroke(g, p, A(T.Acc, 120), 0.5f);
            Diamond(g, 12, 0.9f, 1.4f, T.Acc2); Diamond(g, 12, 22.1f, 1.4f, T.Acc2);
            Done(c, sel);
        }
        foreach (string side in new string[] { "left", "right" })
        {
            string rel = "sprites/hud/hotbar_offhand_" + side + ".png";
            if (!Want(rel)) continue;
            Img v = Load(Path.Combine(srcRoot, rel.Replace('/', '\\')));
            int[] bb = OpaqueBox(v);
            Cv c = New(v.W, v.Hh); Graphics g = c.G;
            float x = bb[0], y = bb[1], w = bb[2] - bb[0] + 1, h = bb[3] - bb[1] + 1;
            using (GraphicsPath o = RR(x + 0.3f, y + 0.3f, w - 0.6f, h - 0.6f, 3f))
            {
                FillV(g, o, new RectangleF(x, y, w, h), Color.FromArgb(225, T.Bg1), Color.FromArgb(235, T.Bg2));
                Stroke(g, o, T.Out, 0.6f);
            }
            using (GraphicsPath s = RR(x + 2.2f, y + 2.2f, w - 4.4f, h - 4.4f, 1.6f))
            {
                FillV(g, s, new RectangleF(x + 2, y + 2, w - 4, h - 4), Color.FromArgb(220, Mix(T.In1, Color.Black, 0.25)), Color.FromArgb(220, T.In2));
                Stroke(g, s, A(T.Acc2, 170), 0.4f);
            }
            Done(c, rel);
        }
        Bar("sprites/hud/experience_bar_background.png", false);
        Bar("sprites/hud/experience_bar_progress.png", true);
        Bar("sprites/hud/jump_bar_background.png", false);
        Bar("sprites/hud/jump_bar_progress.png", true);
    }

    static void Bar(string rel, bool fill)
    {
        if (!Want(rel)) return;
        Img v = Load(Path.Combine(srcRoot, rel.Replace('/', '\\')));
        Cv c = New(v.W, v.Hh); Graphics g = c.G;
        float w = v.W, h = v.Hh;
        if (!fill)
        {
            using (GraphicsPath o = RR(0.2f, 0.2f, w - 0.4f, h - 0.4f, h / 2f))
            {
                using (SolidBrush b = new SolidBrush(Color.FromArgb(230, T.Out))) g.FillPath(b, o);
            }
            using (GraphicsPath o = RR(0.8f, 0.8f, w - 1.6f, h - 1.6f, (h - 1.6f) / 2f)) FillV(g, o, new RectangleF(0, 0.8f, w, h - 1.6f), Color.FromArgb(230, Mix(T.In1, Color.Black, 0.3)), Color.FromArgb(230, T.In2));
        }
        else
        {
            using (GraphicsPath o = RR(0.8f, 0.8f, w - 1.6f, h - 1.6f, (h - 1.6f) / 2f))
                FillV(g, o, new RectangleF(0, 0.8f, w, h - 1.6f), Mix(T.Acc2, Color.White, 0.45), Mix(T.Acc2, Color.Black, 0.25));
            Line(g, Color.FromArgb(150, 255, 255, 255), 0.3f, 2, 1.3f, w - 2, 1.3f);
        }
        Done(c, rel);
    }

    // ------------------------------------------------------------------ Fonds de menus (Echap, options, listes)
    static void Menus()
    {
        // Le jeu les repete en mosaique a 32 x 32 px d'interface (16 x 16 dans l'image d'origine).
        Tile("menu_background.png", 32, 255, 0.0);
        Tile("menu_list_background.png", 32, 255, -0.18);
        Tile("tab_header_background.png", 32, 255, 0.05);
        TileFromVanillaAlpha("inworld_menu_background.png", 32, 0.0);
        TileFromVanillaAlpha("inworld_menu_list_background.png", 32, -0.2);
        Separator("header_separator.png", true, false);
        Separator("footer_separator.png", false, false);
        Separator("inworld_header_separator.png", true, true);
        Separator("inworld_footer_separator.png", false, true);
    }

    static void Tile(string rel, int size, int alpha, double shade)
    {
        if (!Want(rel)) return;
        Img o = new Img(size * S, size * S);
        for (int y = 0; y < o.Hh; y++)
            for (int x = 0; x < o.W; x++)
            {
                Color c = Lighten(Material(x, y, 0.85), shade - 0.12);
                // Trame de fond discrete, raccordee aux bords de la mosaique.
                double gx = Math.Sin(x * Math.PI * 2 / o.W * 2) * Math.Sin(y * Math.PI * 2 / o.Hh * 2);
                c = Lighten(c, gx * 0.02);
                o.P[y * o.W + x] = Argb(alpha, c.R, c.G, c.B);
            }
        Bitmap b = ToBitmap(o); SaveBmp(b, rel); b.Dispose();
    }

    static void TileFromVanillaAlpha(string rel, int size, double shade)
    {
        if (!Want(rel)) return;
        Img v = Load(Path.Combine(srcRoot, rel));
        long sa = 0; foreach (int p in v.P) sa += Al(p);
        int alpha = (int)(sa / v.P.Length);
        Tile(rel, size, Math.Max(alpha, 110), shade);
    }

    static void Separator(string rel, bool header, bool inworld)
    {
        if (!Want(rel)) return;
        Cv c = New(32, 2); Graphics g = c.G;
        // Mosaique horizontale : traits pleins sur toute la largeur.
        Color line = A(T.Acc, inworld ? 200 : 255);
        if (header)
        {
            using (SolidBrush b = new SolidBrush(line)) g.FillRectangle(b, 0, 0, 32, 0.75f);
            using (SolidBrush b = new SolidBrush(Color.FromArgb(150, T.Out))) g.FillRectangle(b, 0, 0.75f, 32, 1.25f);
        }
        else
        {
            using (SolidBrush b = new SolidBrush(Color.FromArgb(150, T.Out))) g.FillRectangle(b, 0, 0, 32, 1.25f);
            using (SolidBrush b = new SolidBrush(line)) g.FillRectangle(b, 0, 1.25f, 32, 0.75f);
        }
        Done(c, rel);
    }

    // ------------------------------------------------------------------ Planche d'apercu
    /** Assemble quelques textures du theme dans une image pour verification. */
    public static void Preview(string outGui, string pngOut)
    {
        string[] items = { "container/inventory.png", "container/generic_54.png", "container/furnace.png", "container/crafting_table.png",
            "container/villager.png", "container/anvil.png", "container/brewing_stand.png", "container/creative_inventory/tab_items.png" };
        int cell = 600;
        using (Bitmap sheet = new Bitmap(cell * 4, cell * 2 + 260, PixelFormat.Format32bppArgb))
        using (Graphics g = Graphics.FromImage(sheet))
        {
            g.Clear(Color.FromArgb(255, 60, 90, 60));
            g.InterpolationMode = InterpolationMode.HighQualityBicubic;
            for (int i = 0; i < items.Length; i++)
            {
                string p = Path.Combine(outGui, items[i].Replace('/', '\\'));
                if (!File.Exists(p)) continue;
                using (Bitmap b = new Bitmap(p))
                {
                    float sc = items[i].Contains("villager") ? 2.0f : 2.8f;
                    g.DrawImage(b, new RectangleF((i % 4) * cell + 4, (i / 4) * cell + 4, b.Width / 4f * sc, b.Height / 4f * sc));
                }
            }
            string[] w = { "sprites/widget/button.png", "sprites/widget/button_highlighted.png", "sprites/widget/button_disabled.png", "sprites/widget/slider.png" };
            for (int i = 0; i < w.Length; i++)
            {
                string p = Path.Combine(outGui, w[i].Replace('/', '\\'));
                if (!File.Exists(p)) continue;
                using (Bitmap b = new Bitmap(p)) g.DrawImage(b, new RectangleF(10 + (i % 2) * 610, cell * 2 + 10 + (i / 2) * 60, 600, 60));
            }
            string[] h = { "sprites/hud/hotbar.png", "sprites/hud/hotbar_selection.png", "sprites/widget/slider_handle.png", "sprites/widget/checkbox_selected.png", "sprites/tooltip/frame.png" };
            float xx = 1240;
            foreach (string s in h)
            {
                string p = Path.Combine(outGui, s.Replace('/', '\\'));
                if (!File.Exists(p)) continue;
                using (Bitmap b = new Bitmap(p)) { g.DrawImage(b, new RectangleF(xx, cell * 2 + 10, b.Width / 4f * 3, b.Height / 4f * 3)); xx += b.Width / 4f * 3 + 12; }
            }
            sheet.Save(pngOut, ImageFormat.Png);
        }
    }
}
