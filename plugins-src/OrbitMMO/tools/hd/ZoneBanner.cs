// Bandeau d'entree de zone d'Orbit MMO (hud/ZoneBanner.java), en haute definition.
//
// Le bandeau passe par le titre du jeu (Hud.extractTitle du client 26.3) : le jeu le fait apparaitre puis s'effacer
// en fondu. Le titre est dessine a l'echelle 4 (1 unite de police = 4 px d'interface), haut de sa ligne 40 px
// au-dessus du centre de l'ecran ; le sous-titre a l'echelle 2 (1 unite = 2 px), haut de sa ligne 10 px sous le centre.
//  - Le cadre (police orbitmmo:zone) est un glyphe bitmap du titre : 27 unites de haut, ascent 27, donc de 120 px a
//    12 px au-dessus du centre (le jeu refuse un ascent plus grand que la hauteur : le glyphe descend forcement jusqu'a
//    la ligne de base). La plaque est dans le haut du glyphe, le bas est vide. Il est dessine 2 fois plus finement que
//    l'interface (8 px d'image par unite : 216 px de haut) et decoupe en tuiles de 15 unites (120 px), car le client
//    range les glyphes sur des pages de 256 x 256 px.
//    Deux calques : le fond (blanc, non teinte) et les filets / ornements (blancs, teintes par la couleur de la zone).
//  - Les textes sont dans le sous-titre, en polices lisses (Outfit en TTF, dessinee par le client, sur-echantillonnee
//    8 fois : 4 px d'image par px d'interface), remontees dans le cadre par leur decalage vertical ("shift").
//  - Chaque police contient aussi des espaces fins (1/8 d'unite a 128 unites) pour placer les textes.
// Ecrit aussi src/main/resources/zone-widths.txt : avance de chaque caractere d'Outfit (normal et gras) en 1/10000
// de cadratin ; hud/ZoneBanner.java en deduit la largeur des textes comme le client (arrondi au pixel sur-echantillonne).
//
// Les tailles, decalages et codes ci-dessous doivent rester les memes que dans hud/ZoneBanner.java.
// C# 5 (compile par Add-Type de PowerShell 5.1) : pas d'interpolation de chaines ni de "=>".
using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
using System.Globalization;
using System.IO;
using System.Text;

public static class ZoneBannerHd
{
    /**
     * Px d'image par px d'interface. 2 et non 4 : le glyphe descend forcement jusqu'a la ligne de base du titre (12 px
     * au-dessus du centre) ; pour monter la plaque vers le haut de l'ecran il fait 108 px d'interface de haut, et une
     * page de police ne depasse pas 256 px (25/11 : Zazou voulait le bandeau plus haut, comme le cadre de son capture).
     */
    public const int S = 2;
    /** Px d'interface par unite de police du titre (echelle 4). */
    public const int UNIT = 4;
    /** Hauteur (et ascent) du glyphe du cadre, en unites du titre : de 120 a 12 px au-dessus du centre. */
    public const int GLYPH_H = 27;
    /** Largeur d'une tuile, en unites du titre. */
    public const int TILE = 15;
    /** Haut du glyphe, en px d'interface au-dessus du centre de l'ecran (y du dessin = y ecran + TOP). */
    public const int TOP = 120;
    /** Sur-echantillonnage des polices lisses. */
    public const int OVERSAMPLE = 8;

    static string pack;
    static readonly CultureInfo IC = CultureInfo.InvariantCulture;
    static readonly UTF8Encoding UTF8 = new UTF8Encoding(false);

    static Color H(string hex, int a)
    {
        int v = Convert.ToInt32(hex, 16);
        return Color.FromArgb(a, (v >> 16) & 255, (v >> 8) & 255, v & 255);
    }

    static Color W(int grey, int a) { return Color.FromArgb(a, grey, grey, grey); }

    static void Smooth(Graphics g)
    {
        g.SmoothingMode = SmoothingMode.AntiAlias;
        g.PixelOffsetMode = PixelOffsetMode.HighQuality;
        g.InterpolationMode = InterpolationMode.HighQualityBicubic;
        g.CompositingQuality = CompositingQuality.HighQuality;
    }

    // ------------------------------------------------------------------ Formes

    static void FillRectV(Graphics g, float x, float y, float w, float h, Color top, Color bottom)
    {
        if (w <= 0 || h <= 0) return;
        using (LinearGradientBrush b = new LinearGradientBrush(new RectangleF(x, y - 0.01f, w, h + 0.02f), top, bottom, 90f))
        {
            // Sans miroir, GDI+ repart de la premiere couleur sur la derniere rangee (trait parasite au bas du degrade).
            b.WrapMode = WrapMode.TileFlipXY;
            g.FillRectangle(b, x, y, w, h);
        }
    }

    /** Plaque a bouts biseautes : pointes en (x0, milieu) et (x1, milieu), cotes plats de x0 + slant a x1 - slant. */
    static GraphicsPath Hex(float x0, float y0, float x1, float y1, float slant)
    {
        float ym = (y0 + y1) / 2f;
        GraphicsPath p = new GraphicsPath();
        p.AddPolygon(new PointF[] {
            new PointF(x0, ym), new PointF(x0 + slant, y0), new PointF(x1 - slant, y0),
            new PointF(x1, ym), new PointF(x1 - slant, y1), new PointF(x0 + slant, y1) });
        return p;
    }

    /** Trait qui s'estompe vers l'exterieur (de xIn vers xOut). */
    static void Ray(Graphics g, float xIn, float xOut, float y, float thick, int alpha)
    {
        float x0 = Math.Min(xIn, xOut), x1 = Math.Max(xIn, xOut);
        Color cIn = W(255, alpha), cOut = W(190, 0);
        using (LinearGradientBrush b = new LinearGradientBrush(new RectangleF(x0 - 0.01f, y, x1 - x0 + 0.02f, thick),
            xIn < xOut ? cIn : cOut, xIn < xOut ? cOut : cIn, 0f))
        {
            b.WrapMode = WrapMode.TileFlipXY;
            g.FillRectangle(b, x0, y, x1 - x0, thick);
        }
    }

    static void Diamond(Graphics g, float cx, float cy, float rx, float ry, Color fill, Color edge, float edgeW)
    {
        PointF[] pts = { new PointF(cx, cy - ry), new PointF(cx + rx, cy), new PointF(cx, cy + ry), new PointF(cx - rx, cy) };
        using (GraphicsPath p = new GraphicsPath())
        {
            p.AddPolygon(pts);
            using (SolidBrush b = new SolidBrush(fill)) g.FillPath(b, p);
            if (edge.A > 0) using (Pen pen = new Pen(edge, edgeW)) { pen.LineJoin = LineJoin.Miter; g.DrawPath(pen, p); }
        }
    }

    /** Losange plein a reflet (ornement teinte). */
    static void Gem(Graphics g, float cx, float cy, float r)
    {
        Diamond(g, cx, cy, r, r * 1.05f, W(225, 255), Color.FromArgb(235, 6, 10, 20), Math.Max(0.35f, r * 0.22f));
        Diamond(g, cx - r * 0.18f, cy - r * 0.2f, r * 0.5f, r * 0.5f, W(255, 255), Color.Empty, 0f);
    }

    // ------------------------------------------------------------------ Calques du cadre

    static Bitmap Canvas(int wpx, out Graphics g)
    {
        Bitmap b = new Bitmap(wpx * S, GLYPH_H * UNIT * S, PixelFormat.Format32bppArgb);
        g = Graphics.FromImage(b);
        Smooth(g);
        g.ScaleTransform(S, S);
        return b;
    }

    /**
     * Fond (non teinte) : plaque bleu nuit a bouts biseautes, degrade vertical, reflet froid en haut, ombre en bas,
     * bord sombre pour la detacher d'un ciel clair. Pointes en x = tip et x = wpx - tip ; bandeau de y0 a y1.
     */
    static Bitmap Body(int wpx, float tip, float slant, float y0, float y1)
    {
        Graphics g;
        Bitmap b = Canvas(wpx, out g);
        using (g)
        using (GraphicsPath hex = Hex(tip, y0, wpx - tip, y1, slant))
        {
            float h = y1 - y0;
            using (LinearGradientBrush br = new LinearGradientBrush(new RectangleF(0, y0 - 0.01f, wpx, h + 0.02f), H("17264A", 230), H("060B1A", 236), 90f))
            {
                ColorBlend cb = new ColorBlend();
                cb.Colors = new Color[] { H("1E3060", 226), H("13214A", 232), H("0B1430", 236), H("070C1E", 240) };
                cb.Positions = new float[] { 0f, 0.35f, 0.72f, 1f };
                br.InterpolationColors = cb;
                br.WrapMode = WrapMode.TileFlipXY;
                g.FillPath(br, hex);
            }
            g.SetClip(hex);
            FillRectV(g, 0, y0, wpx, h * 0.45f, Color.FromArgb(46, 150, 185, 255), Color.FromArgb(0, 150, 185, 255));
            FillRectV(g, 0, y1 - h * 0.3f, wpx, h * 0.3f, Color.FromArgb(0, 0, 0, 0), Color.FromArgb(70, 0, 0, 0));
            g.ResetClip();
            using (Pen p = new Pen(H("04070E", 230), 1.1f)) { p.LineJoin = LineJoin.Miter; g.DrawPath(p, hex); }
        }
        return b;
    }

    /**
     * Filets et ornements, dessines en blanc : le jeu les teinte avec la couleur de la zone. Contour de la plaque, lisere
     * interieur, losanges aux pointes et au milieu des cotes, ailettes qui s'estompent au-dela des pointes.
     * {@code sepY} &lt; 0 : pas de separateur entre le nom et la ligne du bas. {@code labelHalf} &gt; 0 : traits de part et
     * d'autre de l'etiquette (largeur fixe : bandeau des lieux-dits), a la hauteur {@code labelY}.
     */
    static Bitmap Trim(int wpx, float tip, float slant, float y0, float y1, float sepY, float labelHalf, float labelY)
    {
        Graphics g;
        Bitmap b = Canvas(wpx, out g);
        float cx = wpx / 2f, ym = (y0 + y1) / 2f;
        using (g)
        {
            using (GraphicsPath hex = Hex(tip, y0, wpx - tip, y1, slant))
            using (Pen p = new Pen(W(255, 255), 0.6f)) { p.LineJoin = LineJoin.Miter; g.DrawPath(p, hex); }
            float k = 1.45f, ks = k * slant / ((y1 - y0) / 2f);
            using (GraphicsPath inner = Hex(tip + ks + 0.4f, y0 + k, wpx - tip - ks - 0.4f, y1 - k, slant - ks * 0.35f))
            using (Pen p = new Pen(W(255, 95), 0.3f)) { p.LineJoin = LineJoin.Miter; g.DrawPath(p, inner); }
            // Ailettes au-dela des pointes, losanges aux pointes et au milieu des cotes plats.
            Ray(g, tip - 2f, 0.5f, ym - 0.3f, 0.6f, 235);
            Ray(g, wpx - tip + 2f, wpx - 0.5f, ym - 0.3f, 0.6f, 235);
            Ray(g, tip - 3f, tip - 14f, ym - 2.4f, 0.35f, 150);
            Ray(g, tip - 3f, tip - 14f, ym + 2.05f, 0.35f, 150);
            Ray(g, wpx - tip + 3f, wpx - tip + 14f, ym - 2.4f, 0.35f, 150);
            Ray(g, wpx - tip + 3f, wpx - tip + 14f, ym + 2.05f, 0.35f, 150);
            Gem(g, tip, ym, 2.9f);
            Gem(g, wpx - tip, ym, 2.9f);
            Gem(g, cx, y0, 2.2f);
            Gem(g, cx, y1, 2.2f);
            if (sepY >= 0)
            {
                // Separateur entre le nom et la ligne du bas : deux rayons et un losange creux.
                Ray(g, cx - 5.5f, cx - 64f, sepY - 0.25f, 0.5f, 235);
                Ray(g, cx + 5.5f, cx + 64f, sepY - 0.25f, 0.5f, 235);
                Diamond(g, cx, sepY, 3.0f, 2.1f, W(255, 0), W(255, 255), 0.55f);
                Diamond(g, cx, sepY, 1.0f, 0.75f, W(255, 255), Color.Empty, 0f);
            }
            if (labelHalf > 0)
            {
                Ray(g, cx - labelHalf, cx - labelHalf - 26f, labelY - 0.25f, 0.5f, 220);
                Ray(g, cx + labelHalf, cx + labelHalf + 26f, labelY - 0.25f, 0.5f, 220);
                Diamond(g, cx - labelHalf + 1.2f, labelY, 1.2f, 0.9f, W(255, 255), Color.Empty, 0f);
                Diamond(g, cx + labelHalf - 1.2f, labelY, 1.2f, 0.9f, W(255, 255), Color.Empty, 0f);
            }
        }
        return b;
    }

    // ------------------------------------------------------------------ Tuiles et police orbitmmo:zone

    static List<string> providers = new List<string>();

    static string U(int code) { return "\\u" + code.ToString("X4"); }

    /** Decoupe une image du cadre en tuiles de TILE unites ; la premiere tuile prend le caractere {@code first}. */
    static int Tiles(Bitmap img, string name, int first)
    {
        int tilePx = TILE * UNIT * S;
        int n = (img.Width + tilePx - 1) / tilePx;
        string dir = Path.Combine(pack, "assets", "orbitmmo", "textures", "font", "zone");
        Directory.CreateDirectory(dir);
        for (int i = 0; i < n; i++)
        {
            int x = i * tilePx;
            int w = Math.Min(tilePx, img.Width - x);
            using (Bitmap t = img.Clone(new Rectangle(x, 0, w, img.Height), PixelFormat.Format32bppArgb))
            {
                // Le jeu mesure l'avance a la derniere colonne non vide : un pixel quasi invisible la garantit.
                bool used = false;
                for (int y = 0; y < t.Height && !used; y++) used = t.GetPixel(w - 1, y).A > 0;
                if (!used) t.SetPixel(w - 1, 0, Color.FromArgb(1, 0, 0, 0));
                t.Save(Path.Combine(dir, name + "_" + i + ".png"), ImageFormat.Png);
            }
            providers.Add("    {\"type\": \"bitmap\", \"file\": \"orbitmmo:font/zone/" + name + "_" + i + ".png\", \"ascent\": " + GLYPH_H
                + ", \"height\": " + GLYPH_H + ", \"chars\": [\"" + U(first + i) + "\"]}");
        }
        img.Dispose();
        return n;
    }

    /** Espaces fins : F900 + k = -2^(k-3) unites, F910 + k = +2^(k-3) unites (k = 0 a 10 : 1/8 a 128). */
    static string Spaces()
    {
        StringBuilder sb = new StringBuilder("    {\"type\": \"space\", \"advances\": {");
        for (int k = 0; k <= 10; k++)
        {
            string v = (Math.Pow(2, k - 3)).ToString("0.###", IC);
            if (k > 0) sb.Append(", ");
            sb.Append("\"" + U(0xF900 + k) + "\": -" + v + ", \"" + U(0xF910 + k) + "\": " + v);
        }
        sb.Append("}}");
        return sb.ToString();
    }

    static void WriteFont(string name, IEnumerable<string> provs)
    {
        string p = Path.Combine(pack, "assets", "orbitmmo", "font", name + ".json");
        Directory.CreateDirectory(Path.GetDirectoryName(p));
        File.WriteAllText(p, "{\n  \"providers\": [\n" + string.Join(",\n", new List<string>(provs).ToArray()) + "\n  ]\n}\n", UTF8);
    }

    static void TtfFont(string name, string file, double size, double shift)
    {
        WriteFont(name, new string[] {
            Spaces(),
            "    {\"type\": \"ttf\", \"file\": \"orbitmmo:" + file + "\", \"size\": " + size.ToString("0.###", IC) + ", \"oversample\": " + OVERSAMPLE
                + ", \"shift\": [0, " + shift.ToString("0.###", IC) + "], \"skip\": \"\"}" });
    }

    // ------------------------------------------------------------------ Entree

    public static void Run(string packDir, string fontsDir, string widthsOut)
    {
        pack = packDir;
        providers.Clear();
        string tex = Path.Combine(pack, "assets", "orbitmmo", "textures", "font", "zone");
        if (Directory.Exists(tex)) Directory.Delete(tex, true);

        // Grand bandeau (regions, villes, sites) : glyphe de 64 unites = 256 px, plaque de y 2 a 46 (118 a 74 px au-dessus
        // du centre), pointes a 22 px des bords. Etiquette (ligne de base y 11), nom (25,5), separateur (31,5), bas (41).
        // Sous le bandeau, le glyphe est vide jusqu'a la ligne de base du titre.
        int big = 64 * UNIT;
        Tiles(Body(big, 22f, 13f, 2f, 46f), "large_body", 0xE900);
        Tiles(Trim(big, 22f, 13f, 2f, 46f, 31.5f, 0f, 0f), "large_trim", 0xE908);
        // Petit bandeau (lieux-dits) : glyphe de 48 unites = 192 px, plaque de y 5 a 31, pointes a 18 px des bords.
        // Etiquette LIEU-DIT (ligne de base y 12, milieu des lettres y 9,9, ~15 px de part et d'autre du centre), nom (25).
        int small = 48 * UNIT;
        Tiles(Body(small, 18f, 8f, 5f, 31f), "small_body", 0xE910);
        Tiles(Trim(small, 18f, 8f, 5f, 31f, -1f, 21f, 9.9f), "small_trim", 0xE918);
        List<string> zone = new List<string>();
        zone.Add(Spaces());
        zone.AddRange(providers);
        WriteFont("zone", zone);

        // Polices lisses des textes (sous-titre, echelle 2 : ligne de base a 24 + 2 x decalage px du centre).
        // RAISE : la plaque est montee de (TOP - 72) px, soit RAISE unites du sous-titre.
        string reg = "qtext.ttf", bold = "qtext_b.ttf";
        CopyIfMissing(Path.Combine(fontsDir, "Outfit-Regular.ttf"), reg);
        CopyIfMissing(Path.Combine(fontsDir, "Outfit-Bold.ttf"), bold);
        double RAISE = (TOP - 72) / 2.0;
        TtfFont("zone_label", bold, 3.5, -42.5 - RAISE);
        TtfFont("zone_name", bold, 6.5, -35.25 - RAISE);
        TtfFont("zone_name_s", bold, 5.25, -35.25 - RAISE);
        TtfFont("zone_info", reg, 4, -27.5 - RAISE);
        TtfFont("zone_mini_label", bold, 3, -42 - RAISE);
        TtfFont("zone_mini_name", bold, 5, -35.5 - RAISE);

        // Avances d'Outfit (normal, gras) en 1/10000 de cadratin.
        Dictionary<int, int> r = Advances(Path.Combine(fontsDir, "Outfit-Regular.ttf"));
        Dictionary<int, int> b = Advances(Path.Combine(fontsDir, "Outfit-Bold.ttf"));
        List<int> keys = new List<int>(r.Keys);
        keys.Sort();
        StringBuilder sb = new StringBuilder();
        sb.Append("# Avances d'Outfit normal / gras en 1/10000 de cadratin (tools/hd/ZoneBanner.cs, lu par hud/ZoneBanner.java)\n");
        foreach (int c in keys) if (b.ContainsKey(c)) sb.Append(c.ToString("X4") + " " + r[c] + " " + b[c] + "\n");
        File.WriteAllText(widthsOut, sb.ToString(), UTF8);
    }

    static void CopyIfMissing(string src, string name)
    {
        string dst = Path.Combine(pack, "assets", "orbitmmo", "font", name);
        if (File.Exists(dst)) return;
        Directory.CreateDirectory(Path.GetDirectoryName(dst));
        File.Copy(src, dst, true);
    }

    static Dictionary<int, int> Advances(string ttf)
    {
        System.Windows.Media.GlyphTypeface gt = new System.Windows.Media.GlyphTypeface(new Uri(Path.GetFullPath(ttf)));
        Dictionary<int, int> adv = new Dictionary<int, int>();
        foreach (KeyValuePair<int, ushort> e in gt.CharacterToGlyphMap)
        {
            if (e.Key > 0xFFFF || e.Key < 0x20) continue;
            adv[e.Key] = (int)Math.Round(gt.AdvanceWidths[e.Value] * 10000);
        }
        return adv;
    }
}
