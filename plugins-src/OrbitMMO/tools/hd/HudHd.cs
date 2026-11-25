// HUD OrbitMMO en haute definition : memes images que gen-hud.ps1 (memes tailles d'affichage, memes positions),
// mais dessinees 4 fois plus finement (S px d'image par px d'interface) avec lissage, degrades et reflets.
// Le jeu deduit l'echelle de "height" (inchange dans hud.json) et de la hauteur de l'image : rien ne bouge cote Java.
// Ecrit aussi les polices lisses orbitmmo:qtext / qtext_b (Outfit) et leurs largeurs pour hud/HdText.java.
// C# 5 (compile par Add-Type de PowerShell 5.1) : pas d'interpolation de chaines ni de "=>".
using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
using System.Drawing.Text;
using System.IO;
using System.Text;

public static class HudHd
{
    public const int S = 4;
    static string pack, fontDir, hudDir;
    static FontFamily tektur;

    // ------------------------------------------------------------------ Palette (DA ORBIT)
    static Color H(string hex) { return H(hex, 255); }
    static Color H(string hex, int a)
    {
        int v = Convert.ToInt32(hex, 16);
        return Color.FromArgb(a, (v >> 16) & 255, (v >> 8) & 255, v & 255);
    }
    static readonly Color OUT = H("04070E"), GOLD = H("C9A24A"), GOLD_L = H("FFE08A"), GOLD_D = H("7A5F22"), GOLD_W = H("FFF6C8");

    // ------------------------------------------------------------------ Toile
    class Canvas { public Bitmap B; public Graphics G; public int W, Hh; }

    static Canvas New(int w, int h)
    {
        Canvas c = new Canvas();
        c.W = w; c.Hh = h;
        c.B = new Bitmap(w * S, h * S, PixelFormat.Format32bppArgb);
        c.G = Graphics.FromImage(c.B);
        Smooth(c.G);
        c.G.ScaleTransform(S, S);
        return c;
    }

    static void Smooth(Graphics g)
    {
        g.SmoothingMode = SmoothingMode.AntiAlias;
        g.PixelOffsetMode = PixelOffsetMode.HighQuality;
        g.InterpolationMode = InterpolationMode.HighQualityBicubic;
        g.CompositingQuality = CompositingQuality.HighQuality;
    }

    /** force : un pixel quasi invisible dans la derniere colonne (le jeu mesure la largeur aux pixels non vides). */
    static void Save(Canvas c, string rel, bool force)
    {
        c.G.Dispose();
        if (force)
        {
            int x = c.B.Width - 1;
            bool used = false;
            for (int y = 0; y < c.B.Height; y++) { if (c.B.GetPixel(x, y).A > 0) { used = true; break; } }
            if (!used) c.B.SetPixel(x, 0, Color.FromArgb(1, 0, 0, 0));
        }
        SaveBmp(c.B, rel);
    }

    /**
     * Sprite du jeu a sa taille d'origine (1 px par px d'interface), reduit depuis le dessin fin : la barre d'action
     * en 728 x 88 s'affichait en texture manquante (damier rose) dans le client 26.3, en 182 x 22 elle passe.
     */
    static void SaveDown(Canvas c, string rel)
    {
        c.G.Dispose();
        Bitmap small = new Bitmap(c.W, c.Hh, PixelFormat.Format32bppArgb);
        using (Graphics g = Graphics.FromImage(small))
        {
            g.CompositingMode = CompositingMode.SourceCopy;
            g.InterpolationMode = InterpolationMode.HighQualityBicubic;
            g.PixelOffsetMode = PixelOffsetMode.HighQuality;
            using (ImageAttributes ia = new ImageAttributes())
            {
                ia.SetWrapMode(WrapMode.TileFlipXY);
                g.DrawImage(c.B, new Rectangle(0, 0, c.W, c.Hh), 0, 0, c.B.Width, c.B.Height, GraphicsUnit.Pixel, ia);
            }
        }
        c.B.Dispose();
        SaveBmp(small, rel);
    }

    static void SaveBmp(Bitmap b, string rel)
    {
        string p = Path.Combine(pack, rel.Replace('/', Path.DirectorySeparatorChar));
        Directory.CreateDirectory(Path.GetDirectoryName(p));
        b.Save(p, ImageFormat.Png);
        b.Dispose();
    }

    // ------------------------------------------------------------------ Formes
    static GraphicsPath RR(float x, float y, float w, float h, float r)
    {
        GraphicsPath p = new GraphicsPath();
        if (r <= 0.01f) { p.AddRectangle(new RectangleF(x, y, w, h)); return p; }
        float d = Math.Min(r * 2, Math.Min(w, h));
        p.AddArc(x, y, d, d, 180, 90);
        p.AddArc(x + w - d, y, d, d, 270, 90);
        p.AddArc(x + w - d, y + h - d, d, d, 0, 90);
        p.AddArc(x, y + h - d, d, d, 90, 90);
        p.CloseFigure();
        return p;
    }

    static void FillV(Graphics g, GraphicsPath path, RectangleF r, Color top, Color bottom)
    {
        using (LinearGradientBrush b = new LinearGradientBrush(new RectangleF(r.X, r.Y - 0.01f, r.Width, r.Height + 0.02f), top, bottom, 90f))
            g.FillPath(b, path);
    }

    static void FillRectV(Graphics g, float x, float y, float w, float h, Color top, Color bottom)
    {
        using (GraphicsPath p = RR(x, y, w, h, 0)) FillV(g, p, new RectangleF(x, y, w, h), top, bottom);
    }

    static void Stroke(Graphics g, GraphicsPath path, Color c, float width)
    {
        using (Pen p = new Pen(c, width)) { p.LineJoin = LineJoin.Round; g.DrawPath(p, path); }
    }

    /** Trait d'or horizontal qui s'estompe aux deux bouts, plus clair au centre. */
    static void GoldLine(Graphics g, float x0, float x1, float y, float thick, int alpha)
    {
        RectangleF r = new RectangleF(x0, y, x1 - x0, thick);
        using (LinearGradientBrush b = new LinearGradientBrush(new RectangleF(x0 - 0.01f, y, x1 - x0 + 0.02f, thick), GOLD, GOLD, 0f))
        {
            ColorBlend cb = new ColorBlend();
            cb.Colors = new Color[] { Color.FromArgb(0, GOLD_D), Color.FromArgb(alpha, GOLD), Color.FromArgb(alpha, GOLD_L), Color.FromArgb(alpha, GOLD), Color.FromArgb(0, GOLD_D) };
            cb.Positions = new float[] { 0f, 0.18f, 0.5f, 0.82f, 1f };
            b.InterpolationColors = cb;
            g.FillRectangle(b, r);
        }
    }

    static void Diamond(Graphics g, float cx, float cy, float r, Color fill, Color edge)
    {
        PointF[] pts = { new PointF(cx, cy - r), new PointF(cx + r, cy), new PointF(cx, cy + r), new PointF(cx - r, cy) };
        using (GraphicsPath p = new GraphicsPath()) { p.AddPolygon(pts); using (SolidBrush b = new SolidBrush(fill)) g.FillPath(b, p); if (edge.A > 0) Stroke(g, p, edge, 0.3f); }
    }

    static void Stud(Graphics g, float cx, float cy)
    {
        using (GraphicsPath p = new GraphicsPath())
        {
            p.AddEllipse(cx - 0.95f, cy - 0.95f, 1.9f, 1.9f);
            using (PathGradientBrush b = new PathGradientBrush(p))
            {
                b.CenterPoint = new PointF(cx - 0.35f, cy - 0.35f);
                b.CenterColor = GOLD_W;
                b.SurroundColors = new Color[] { GOLD_D };
                g.FillPath(b, p);
            }
        }
    }

    /**
     * Plaque du HUD : bord sombre arrondi, fond bleu nuit en degrade, reflet en haut, liseres interieurs
     * (bleu clair puis or tres fin).
     */
    static void Plate(Graphics g, float x, float y, float w, float h, int bodyA, float radius)
    {
        using (GraphicsPath body = RR(x + 0.5f, y + 0.5f, w - 1f, h - 1f, radius))
        {
            FillV(g, body, new RectangleF(x, y, w, h), H("1D2B4E", bodyA), H("0A1124", bodyA));
            float sh = Math.Min(h * 0.5f, 9f);
            using (GraphicsPath top = RR(x + 1f, y + 1f, w - 2f, sh, Math.Max(0.5f, radius - 0.5f)))
            using (LinearGradientBrush b = new LinearGradientBrush(new RectangleF(x, y + 0.99f, w, sh + 0.02f), Color.FromArgb(46, 150, 185, 255), Color.FromArgb(0, 150, 185, 255), 90f))
                g.FillPath(b, top);
            Stroke(g, body, OUT, 1f);
        }
        using (GraphicsPath l1 = RR(x + 1.25f, y + 1.25f, w - 2.5f, h - 2.5f, Math.Max(0.4f, radius - 0.75f)))
            Stroke(g, l1, H("4A68A8", 120), 0.25f);
        using (GraphicsPath l2 = RR(x + 1.75f, y + 1.75f, w - 3.5f, h - 3.5f, Math.Max(0.3f, radius - 1.25f)))
            Stroke(g, l2, Color.FromArgb(70, GOLD), 0.25f);
    }

    /** Glissiere d'une jauge (bornes en pixels inclus, comme gen-hud.ps1) : le remplissage va dans l'interieur. */
    static void Track(Graphics g, int x0, int y0, int x1, int y1)
    {
        float w = x1 - x0 + 1, h = y1 - y0 + 1;
        using (GraphicsPath o = RR(x0, y0, w, h, 1f)) using (SolidBrush b = new SolidBrush(H("02050B"))) g.FillPath(b, o);
        FillRectV(g, x0 + 1, y0 + 1, w - 2, h - 2, H("060C18"), H("101C36"));
        // Ombre interne en haut, reflet en bas.
        FillRectV(g, x0 + 1, y0 + 1, w - 2, Math.Min(1.25f, h - 2), Color.FromArgb(150, 0, 0, 0), Color.FromArgb(0, 0, 0, 0));
        using (Pen p = new Pen(H("3A5590", 90), 0.25f)) g.DrawLine(p, x0 + 1.5f, y1 + 0.85f, x1 - 0.5f, y1 + 0.85f);
    }

    static PointF[] Oct(float x, float y, float n, float cut)
    {
        return new PointF[] {
            new PointF(x + cut, y), new PointF(x + n - cut, y), new PointF(x + n, y + cut), new PointF(x + n, y + n - cut),
            new PointF(x + n - cut, y + n), new PointF(x + cut, y + n), new PointF(x, y + n - cut), new PointF(x, y + cut) };
    }

    /** Gemme octogonale : biseau d'or, anneau sombre, coeur en degrade radial et reflet. */
    static void Gem(Graphics g, float gx, float gy, float n, string inA, string inB)
    {
        using (GraphicsPath o = new GraphicsPath())
        {
            o.AddPolygon(Oct(gx, gy, n, 4.2f));
            using (LinearGradientBrush b = new LinearGradientBrush(new RectangleF(gx, gy, n, n), GOLD_L, GOLD_D, 45f)) g.FillPath(b, o);
            Stroke(g, o, OUT, 0.35f);
        }
        using (GraphicsPath r = new GraphicsPath()) { r.AddPolygon(Oct(gx + 1.1f, gy + 1.1f, n - 2.2f, 3.7f)); using (SolidBrush b = new SolidBrush(OUT)) g.FillPath(b, r); }
        using (GraphicsPath c = new GraphicsPath())
        {
            c.AddPolygon(Oct(gx + 1.9f, gy + 1.9f, n - 3.8f, 3.3f));
            using (PathGradientBrush b = new PathGradientBrush(c))
            {
                b.CenterPoint = new PointF(gx + n * 0.42f, gy + n * 0.38f);
                b.CenterColor = H(inA);
                b.SurroundColors = new Color[] { H(inB) };
                g.FillPath(b, c);
            }
            using (GraphicsPath hl = new GraphicsPath())
            {
                hl.AddEllipse(gx + n * 0.22f, gy + n * 0.16f, n * 0.42f, n * 0.24f);
                using (PathGradientBrush b = new PathGradientBrush(hl)) { b.CenterColor = Color.FromArgb(90, 255, 255, 255); b.SurroundColors = new Color[] { Color.FromArgb(0, 255, 255, 255) }; g.FillPath(b, hl); }
            }
        }
    }

    /** Logement 20x20 : cadre d'or, anneau sombre, creux 16x16 (transparent si hole). */
    static void Socket(Canvas cv, float sx, float sy, bool hole)
    {
        Graphics g = cv.G;
        using (GraphicsPath o = RR(sx, sy, 20, 20, 1.6f))
        {
            using (LinearGradientBrush b = new LinearGradientBrush(new RectangleF(sx, sy, 20, 20), GOLD_L, GOLD_D, 60f)) g.FillPath(b, o);
            Stroke(g, o, Color.FromArgb(200, OUT), 0.3f);
        }
        using (GraphicsPath r = RR(sx + 1, sy + 1, 18, 18, 1.1f)) using (SolidBrush b = new SolidBrush(OUT)) g.FillPath(b, r);
        if (hole)
        {
            CompositingMode m = g.CompositingMode;
            SmoothingMode sm = g.SmoothingMode;
            g.CompositingMode = CompositingMode.SourceCopy;
            g.SmoothingMode = SmoothingMode.None;
            using (SolidBrush b = new SolidBrush(Color.FromArgb(0, 0, 0, 0))) g.FillRectangle(b, sx + 2, sy + 2, 16, 16);
            g.CompositingMode = m;
            g.SmoothingMode = sm;
        }
        else
        {
            Recess(g, sx + 2, sy + 2, 16, 16);
        }
    }

    static void Recess(Graphics g, float x, float y, float w, float h)
    {
        FillRectV(g, x, y, w, h, H("0A1224"), H("16233F"));
        FillRectV(g, x, y, w, 2.2f, Color.FromArgb(170, 2, 5, 12), Color.FromArgb(0, 2, 5, 12));
        using (LinearGradientBrush b = new LinearGradientBrush(new RectangleF(x - 0.01f, y, 2.2f, h), Color.FromArgb(110, 2, 5, 12), Color.FromArgb(0, 2, 5, 12), 0f))
            g.FillRectangle(b, x, y, 2.2f, h);
        using (Pen p = new Pen(H("3A5590", 110), 0.25f)) g.DrawLine(p, x + 0.5f, y + h - 0.15f, x + w - 0.5f, y + h - 0.15f);
    }

    /** Bandeau sombre (zone de texte) avec trait d'or dessous. */
    static void Band(Graphics g, float x, float y, float w, float h)
    {
        FillRectV(g, x, y, w, h, H("0E1830", 235), H("070D1C", 235));
    }

    // ------------------------------------------------------------------ Icones avec contour
    /** Dessine une icone (via draw, en px d'interface) avec un contour sombre lisse et une ombre douce. */
    delegate void Draw(Graphics g);

    static void Iconed(Canvas cv, Draw draw, Color outline, float thick)
    {
        Bitmap tmp = new Bitmap(cv.B.Width, cv.B.Height, PixelFormat.Format32bppArgb);
        using (Graphics t = Graphics.FromImage(tmp)) { Smooth(t); t.ScaleTransform(S, S); draw(t); }
        // Contour : l'icone en silhouette, decalee sur un cercle.
        Bitmap sil = Silhouette(tmp, outline);
        Graphics g = cv.G;
        g.ResetTransform();
        int steps = 16;
        for (int i = 0; i < steps; i++)
        {
            double a = Math.PI * 2 * i / steps;
            g.DrawImage(sil, (float)(Math.Cos(a) * thick * S), (float)(Math.Sin(a) * thick * S));
        }
        g.DrawImage(tmp, 0, 0);
        g.ScaleTransform(S, S);
        sil.Dispose();
        tmp.Dispose();
    }

    static Bitmap Silhouette(Bitmap src, Color c)
    {
        Bitmap b = new Bitmap(src.Width, src.Height, PixelFormat.Format32bppArgb);
        for (int y = 0; y < src.Height; y++)
            for (int x = 0; x < src.Width; x++)
            {
                int a = src.GetPixel(x, y).A;
                if (a > 0) b.SetPixel(x, y, Color.FromArgb(a * c.A / 255, c));
            }
        return b;
    }

    // ------------------------------------------------------------------ Console
    static void Wings()
    {
        for (int k = 0; k < 2; k++)
        {
            bool hole = k == 1;
            Canvas c = New(117, 31);
            Plate(c.G, 0, 0, 117, 31, 245, 2.5f);
            Band(c.G, 3, 2, 111, 7);
            GoldLine(c.G, 2, 115, 9, 0.75f, 255);
            Gem(c.G, 3, 10, 19, "4B82E8", "0E1C3A");
            Track(c.G, 25, 10, 90, 19);
            Track(c.G, 25, 21, 90, 26);
            Socket(c, 91, 10, hole);
            Stud(c.G, 113, 12); Stud(c.G, 113, 27);
            Save(c, fontDir + (hole ? "/hud_wing_l_hole.png" : "/hud_wing_l.png"), true);
        }
        Canvas r = New(117, 31);
        Plate(r.G, 0, 0, 117, 31, 245, 2.5f);
        Band(r.G, 3, 2, 111, 7);
        GoldLine(r.G, 2, 115, 9, 0.75f, 255);
        Socket(r, 6, 10, false);
        Track(r.G, 26, 10, 91, 19);
        Band(r.G, 26, 21, 66, 7);
        GoldLine(r.G, 26, 92, 28, 0.6f, 220);
        Gem(r.G, 95, 10, 19, "3A4A70", "0E1424");
        Stud(r.G, 4, 12); Stud(r.G, 4, 27);
        Save(r, fontDir + "/hud_wing_r.png", true);
    }

    static void Bridge()
    {
        Canvas c = New(186, 7);
        Graphics g = c.G;
        using (GraphicsPath o = RR(0, 0, 186, 7, 1.5f)) { using (SolidBrush b = new SolidBrush(OUT)) g.FillPath(b, o); }
        FillRectV(g, 0.75f, 0.75f, 184.5f, 5.5f, H("1A2747"), H("0C1427"));
        GoldLine(g, 0.5f, 185.5f, 1f, 0.75f, 255);
        GoldLine(g, 1f, 185f, 5.25f, 0.5f, 140);
        // Interieur de la jauge d'experience : x 3 a 182, y 2 a 4.
        FillRectV(g, 3, 2, 180, 3, H("050A16"), H("0F1A34"));
        FillRectV(g, 3, 2, 180, 0.75f, Color.FromArgb(150, 0, 0, 0), Color.FromArgb(0, 0, 0, 0));
        using (Pen p = new Pen(H("2A3D66", 150), 0.25f))
            for (int x = 21; x < 183; x += 18) g.DrawLine(p, x, 2.2f, x, 4.8f);
        Diamond(g, 93, 1.4f, 0.9f, GOLD_W, Color.Empty);
        Save(c, fontDir + "/hud_bridge.png", true);
    }

    static void Cast()
    {
        Canvas c = New(124, 19);
        Graphics g = c.G;
        Plate(g, 0, 0, 124, 9, 245, 1.5f);
        GoldLine(g, 1, 123, 1f, 0.6f, 255);
        // Interieur : x 2 a 121, y 2 a 6.
        FillRectV(g, 2, 2, 120, 5, H("050A16"), H("0F1A34"));
        FillRectV(g, 2, 2, 120, 1f, Color.FromArgb(140, 0, 0, 0), Color.FromArgb(0, 0, 0, 0));
        Diamond(g, 62, 1.3f, 0.9f, GOLD_W, Color.Empty);
        Save(c, fontDir + "/hud_cast.png", true);
    }

    /**
     * Cadre de la barre de vie au-dessus des creatures et des joueurs (hud/Nameplates) : 42 x 6, interieur creuse
     * de 40 x 4 en (1, 1) ou se pose le remplissage teinte, liseré d'or tres fin.
     */
    static void PlateFrame()
    {
        Canvas c = New(42, 6);
        Graphics g = c.G;
        using (GraphicsPath o = RR(0.15f, 0.15f, 41.7f, 5.7f, 1.4f))
        {
            using (SolidBrush b = new SolidBrush(Color.FromArgb(235, OUT))) g.FillPath(b, o);
            Stroke(g, o, Color.FromArgb(210, GOLD_D), 0.35f);
        }
        using (GraphicsPath hl = RR(0.55f, 0.45f, 40.9f, 1.2f, 0.6f))
        using (LinearGradientBrush b = new LinearGradientBrush(new RectangleF(0, 0.44f, 42, 1.22f), Color.FromArgb(90, GOLD_L), Color.FromArgb(0, GOLD_L), 90f))
            g.FillPath(b, hl);
        FillRectV(g, 1, 1, 40, 4, H("0A1226", 235), H("17223F", 235));
        FillRectV(g, 1, 1, 40, 1f, Color.FromArgb(160, 0, 0, 0), Color.FromArgb(0, 0, 0, 0));
        Save(c, fontDir + "/plate_frame.png", true);
    }

    /** Remplissages blancs (teintes par la couleur du texte) : degrade vertical avec reflet en haut. */
    static void Fills()
    {
        int[] hs = { 8, 4, 3, 5 };
        string[] names = { "8", "4", "3", "c" };
        int[] pads = { 0, 0, 0, 12 };
        for (int i = 0; i < hs.Length; i++)
            foreach (int w in new int[] { 1, 8 })
            {
                Canvas c = New(w, hs[i] + pads[i]);
                Graphics g = c.G;
                float h = hs[i];
                using (LinearGradientBrush b = new LinearGradientBrush(new RectangleF(0, -0.01f, w, h + 0.02f), Color.White, Color.White, 90f))
                {
                    ColorBlend cb = new ColorBlend();
                    cb.Colors = new Color[] { G(255), G(248), G(222), G(196), G(150) };
                    cb.Positions = new float[] { 0f, 0.2f, 0.5f, 0.8f, 1f };
                    b.InterpolationColors = cb;
                    g.FillRectangle(b, 0, 0, w, h);
                }
                // Reflet : fine ligne blanche sous le haut, ombre douce en bas.
                using (SolidBrush b = new SolidBrush(Color.FromArgb(120, 255, 255, 255))) g.FillRectangle(b, 0, h * 0.12f, w, Math.Max(0.25f, h * 0.08f));
                FillRectV(g, 0, h - Math.Min(1.25f, h * 0.3f), w, Math.Min(1.25f, h * 0.3f), Color.FromArgb(0, 0, 0, 0), Color.FromArgb(60, 0, 0, 0));
                Save(c, fontDir + "/hud_fill" + names[i] + "_" + w + ".png", pads[i] > 0);
            }
        Canvas p = New(12, 8);
        using (GraphicsPath pill = RR(0.25f, 0.5f, 11.5f, 7f, 3.5f))
        {
            using (LinearGradientBrush b = new LinearGradientBrush(new RectangleF(0, 0.49f, 12, 7.02f), G(255), G(150), 90f)) p.G.FillPath(b, pill);
            using (GraphicsPath hl = RR(2f, 1.3f, 8f, 1.8f, 0.9f)) using (SolidBrush b = new SolidBrush(Color.FromArgb(150, 255, 255, 255))) p.G.FillPath(b, hl);
        }
        Save(p, fontDir + "/hud_pip.png", true);
        Canvas e = New(12, 8);
        using (GraphicsPath pill = RR(0.5f, 0.75f, 11f, 6.5f, 3.25f))
        {
            using (SolidBrush b = new SolidBrush(H("0A1222"))) e.G.FillPath(b, pill);
            Stroke(e.G, pill, H("3A5080"), 0.5f);
        }
        Save(e, fontDir + "/hud_pip_empty.png", true);
    }

    static Color G(int v) { return Color.FromArgb(255, v, v, v); }

    static void Emblems()
    {
        Canvas l = New(16, 16);
        Iconed(l, delegate(Graphics g)
        {
            for (int i = 0; i < 8; i++)
            {
                double a = Math.PI / 4 * i - Math.PI / 2;
                float r0 = 4.4f, r1 = i % 2 == 0 ? 7.3f : 6.1f, hw = i % 2 == 0 ? 1.05f : 0.8f;
                PointF tip = new PointF(7.5f + (float)Math.Cos(a) * r1, 7.5f + (float)Math.Sin(a) * r1);
                PointF b1 = new PointF(7.5f + (float)(Math.Cos(a) * r0 - Math.Sin(a) * hw), 7.5f + (float)(Math.Sin(a) * r0 + Math.Cos(a) * hw));
                PointF b2 = new PointF(7.5f + (float)(Math.Cos(a) * r0 + Math.Sin(a) * hw), 7.5f + (float)(Math.Sin(a) * r0 - Math.Cos(a) * hw));
                using (GraphicsPath ray = new GraphicsPath()) { ray.AddPolygon(new PointF[] { b1, tip, b2 }); using (LinearGradientBrush br = new LinearGradientBrush(new RectangleF(0, 0, 16, 16), H("FFE9A0"), H("D08A20"), 90f)) g.FillPath(br, ray); }
            }
            using (GraphicsPath sun = new GraphicsPath())
            {
                sun.AddEllipse(7.5f - 3.7f, 7.5f - 3.7f, 7.4f, 7.4f);
                using (PathGradientBrush br = new PathGradientBrush(sun)) { br.CenterPoint = new PointF(6.8f, 6.6f); br.CenterColor = GOLD_W; br.SurroundColors = new Color[] { H("E0A030") }; g.FillPath(br, sun); }
            }
        }, H("2A1A04", 230), 0.6f);
        Save(l, fontDir + "/hud_emb_lumiere.png", true);

        Canvas o = New(16, 16);
        Iconed(o, delegate(Graphics g)
        {
            Bitmap moon = new Bitmap(16 * S, 16 * S, PixelFormat.Format32bppArgb);
            using (Graphics t = Graphics.FromImage(moon))
            {
                Smooth(t); t.ScaleTransform(S, S);
                using (GraphicsPath disc = new GraphicsPath())
                {
                    disc.AddEllipse(7f - 6.3f, 7.5f - 6.3f, 12.6f, 12.6f);
                    using (LinearGradientBrush br = new LinearGradientBrush(new RectangleF(0, 0, 16, 16), H("ECDCFF"), H("6A30C0"), 50f)) t.FillPath(br, disc);
                }
                t.CompositingMode = CompositingMode.SourceCopy;
                using (SolidBrush clear = new SolidBrush(Color.FromArgb(0, 0, 0, 0))) t.FillEllipse(clear, 10.4f - 5.2f, 5.4f - 5.2f, 10.4f, 10.4f);
            }
            g.ResetTransform(); g.DrawImage(moon, 0, 0); g.ScaleTransform(S, S);
            moon.Dispose();
            Star(g, 13f, 11.6f, 2.3f, 0.6f, Color.White, H("C8A8FF"));
        }, H("1A0830", 230), 0.6f);
        Save(o, fontDir + "/hud_emb_ombre.png", true);

        Canvas n = New(16, 16);
        Iconed(n, delegate(Graphics g) { Star(g, 7.5f, 7.5f, 7f, 1.7f, Color.White, H("A9B4C2")); }, H("10161F", 230), 0.6f);
        Save(n, fontDir + "/hud_emb_none.png", true);
    }

    static void Star(Graphics g, float cx, float cy, float r, float inner, Color center, Color edge)
    {
        PointF[] pts = new PointF[8];
        for (int i = 0; i < 8; i++)
        {
            double a = Math.PI / 4 * i - Math.PI / 2;
            float rr = i % 2 == 0 ? r : inner;
            pts[i] = new PointF(cx + (float)Math.Cos(a) * rr, cy + (float)Math.Sin(a) * rr);
        }
        using (GraphicsPath p = new GraphicsPath())
        {
            p.AddPolygon(pts);
            using (PathGradientBrush b = new PathGradientBrush(p)) { b.CenterPoint = new PointF(cx, cy); b.CenterColor = center; b.SurroundColors = new Color[] { edge }; g.FillPath(b, p); }
        }
    }

    static void ZoneIcons()
    {
        Canvas s = New(11, 11);
        Iconed(s, delegate(Graphics g)
        {
            using (GraphicsPath sh = new GraphicsPath())
            {
                sh.AddLine(1.2f, 1.2f, 9.8f, 1.2f);
                sh.AddLine(9.8f, 1.2f, 9.8f, 5.2f);
                sh.AddBezier(9.8f, 5.2f, 9.8f, 7.8f, 7.4f, 9.2f, 5.5f, 10.1f);
                sh.AddBezier(5.5f, 10.1f, 3.6f, 9.2f, 1.2f, 7.8f, 1.2f, 5.2f);
                sh.CloseFigure();
                using (LinearGradientBrush b = new LinearGradientBrush(new RectangleF(0, 0, 11, 11), H("B8FFD4"), H("1F9A58"), 70f)) g.FillPath(b, sh);
                Stroke(g, sh, H("0A2A18"), 0.35f);
            }
            using (Pen p = new Pen(H("F0FFF6"), 1.1f)) { p.StartCap = LineCap.Round; p.EndCap = LineCap.Round; g.DrawLine(p, 5.5f, 3.3f, 5.5f, 7.6f); g.DrawLine(p, 3.4f, 5.4f, 7.6f, 5.4f); }
        }, H("06180E", 220), 0.5f);
        Save(s, fontDir + "/hud_zone_safe.png", true);

        Canvas v = New(11, 11);
        Iconed(v, delegate(Graphics g)
        {
            for (int k = 0; k < 2; k++)
            {
                GraphicsState st = g.Save();
                g.TranslateTransform(5.5f, 5.5f);
                g.RotateTransform(k == 0 ? 45 : -45);
                using (GraphicsPath blade = new GraphicsPath())
                {
                    blade.AddPolygon(new PointF[] { new PointF(-0.75f, -1.2f), new PointF(-0.75f, -4.6f), new PointF(0, -5.6f), new PointF(0.75f, -4.6f), new PointF(0.75f, -1.2f) });
                    blade.AddPolygon(new PointF[] { new PointF(-0.75f, -1.2f), new PointF(0.75f, -1.2f), new PointF(0.75f, 3.3f), new PointF(-0.75f, 3.3f) });
                    using (LinearGradientBrush b = new LinearGradientBrush(new RectangleF(-1, -6, 2, 10), H("FFFFFF"), H("98A4B4"), 0f)) g.FillPath(b, blade);
                }
                using (SolidBrush b = new SolidBrush(H("FFD66E"))) { g.FillRectangle(b, -2.1f, 3.1f, 4.2f, 0.9f); }
                using (SolidBrush b = new SolidBrush(H("8B5A2B"))) { g.FillRectangle(b, -0.5f, 4.0f, 1f, 1.4f); }
                g.Restore(st);
            }
        }, H("5A0A0A", 235), 0.5f);
        Save(v, fontDir + "/hud_zone_pvp.png", true);

        Canvas b2 = New(11, 11);
        Iconed(b2, delegate(Graphics g)
        {
            using (GraphicsPath sk = new GraphicsPath())
            {
                sk.AddEllipse(1.2f, 0.8f, 8.6f, 7.4f);
                sk.AddRectangle(new RectangleF(2.9f, 6.2f, 5.2f, 3.4f));
                sk.FillMode = FillMode.Winding;
                using (LinearGradientBrush b = new LinearGradientBrush(new RectangleF(0, 0, 11, 11), H("FFFBEF"), H("B8AE96"), 90f)) g.FillPath(b, sk);
            }
            using (SolidBrush e = new SolidBrush(H("1A0505"))) { g.FillEllipse(e, 2.6f, 3.6f, 2.4f, 2.2f); g.FillEllipse(e, 6.0f, 3.6f, 2.4f, 2.2f); g.FillPolygon(e, new PointF[] { new PointF(5.5f, 5.6f), new PointF(4.9f, 6.9f), new PointF(6.1f, 6.9f) }); }
            using (SolidBrush r = new SolidBrush(H("FF3B3B"))) { g.FillEllipse(r, 3.3f, 4.2f, 1.0f, 1.0f); g.FillEllipse(r, 6.7f, 4.2f, 1.0f, 1.0f); }
            using (Pen t = new Pen(H("6A604C"), 0.35f)) { for (int i = 0; i < 3; i++) g.DrawLine(t, 4.2f + i * 1.3f, 8.0f, 4.2f + i * 1.3f, 9.6f); }
        }, H("1A0505", 235), 0.5f);
        Save(b2, fontDir + "/hud_zone_boss.png", true);
    }

    // ------------------------------------------------------------------ Barre d'action
    static void Hotbar()
    {
        Canvas c = New(182, 22);
        Graphics g = c.G;
        Plate(g, 0, 0, 182, 22, 245, 2f);
        GoldLine(g, 1, 181, 1f, 0.6f, 255);
        for (int i = 0; i < 9; i++)
        {
            float x0 = 20 * i + 2;
            using (GraphicsPath o = RR(x0, 2, 18, 18, 1.2f)) using (SolidBrush b = new SolidBrush(H("02050B"))) g.FillPath(b, o);
            Recess(g, x0 + 1, 3, 16, 16);
        }
        for (int i = 0; i < 8; i++) Diamond(g, 20 * i + 21, 11, 0.8f, GOLD, Color.Empty);
        SaveDown(c, hudDir + "/hotbar.png");

        Canvas s = New(24, 23);
        Graphics h = s.G;
        for (int k = 3; k >= 1; k--)
            using (GraphicsPath glow = RR(1.5f - k * 0.4f, 1.5f - k * 0.4f, 21 + k * 0.8f, 20 + k * 0.8f, 2.5f)) Stroke(h, glow, Color.FromArgb(35, 255, 214, 110), 0.6f);
        using (GraphicsPath f = RR(1.5f, 1.5f, 21, 20, 2f))
        {
            using (LinearGradientBrush b = new LinearGradientBrush(new RectangleF(0, 0, 24, 23), GOLD_W, H("B8862E"), 90f))
            using (Pen p = new Pen(b, 1.1f)) h.DrawPath(p, f);
        }
        using (GraphicsPath f2 = RR(2.6f, 2.6f, 18.8f, 17.8f, 1.2f)) Stroke(h, f2, Color.FromArgb(200, 90, 62, 20), 0.35f);
        // Equerres aux coins.
        using (Pen p = new Pen(GOLD_W, 0.7f))
        {
            p.StartCap = LineCap.Round; p.EndCap = LineCap.Round;
            float[][] cs = { new float[] { 1.2f, 1.2f, 1, 1 }, new float[] { 22.8f, 1.2f, -1, 1 }, new float[] { 1.2f, 21.8f, 1, -1 }, new float[] { 22.8f, 21.8f, -1, -1 } };
            foreach (float[] cc in cs) { h.DrawLine(p, cc[0], cc[1], cc[0] + 3.5f * cc[2], cc[1]); h.DrawLine(p, cc[0], cc[1], cc[0], cc[1] + 3.5f * cc[3]); }
        }
        Diamond(h, 12, 1.3f, 1.1f, GOLD_W, Color.Empty);
        Save(s, hudDir + "/hotbar_selection.png", false);

        foreach (int ix in new int[] { 3, 10 })
        {
            Canvas o = New(29, 24);
            using (GraphicsPath r = RR(ix - 1, 3, 18, 18, 1.2f)) using (SolidBrush b = new SolidBrush(Color.FromArgb(235, 2, 5, 11))) o.G.FillPath(b, r);
            Recess(o.G, ix, 4, 16, 16);
            SaveDown(o, hudDir + (ix == 3 ? "/hotbar_offhand_left.png" : "/hotbar_offhand_right.png"));
        }
    }

    // ------------------------------------------------------------------ Suivi des quetes
    static void QuestPanel(int qw)
    {
        for (int n = 1; n <= 14; n++)
        {
            int w = qw + 8, h = 9 * n + 14;
            Canvas c = New(w, h);
            Graphics g = c.G;
            Plate(g, 0, 0, w, h, 240, 3f);
            // Bandeau du titre (y 0 a 11) puis trait d'or en y 11.
            using (GraphicsPath band = RR(1.5f, 1.5f, w - 3, 9.5f, 2f)) FillV(g, band, new RectangleF(0, 1.5f, w, 9.5f), H("223464", 200), H("0E1830", 200));
            GoldLine(g, 1.5f, w - 1.5f, 11f, 0.7f, 255);
            Diamond(g, w / 2f, 11.35f, 1.1f, GOLD_W, H("5A4010"));
            Stud(g, 3.6f, 3.6f); Stud(g, w - 3.6f, 3.6f);
            // Lueur tres douce sous le bandeau.
            FillRectV(g, 1.5f, 12f, w - 3, 5f, Color.FromArgb(28, 255, 214, 110), Color.FromArgb(0, 255, 214, 110));
            Save(c, fontDir + "/quest_panel_" + n + ".png", true);
        }
        Canvas sep = New(qw - 4, 4);
        GoldLine(sep.G, 4, qw - 8, 0.1f, 0.5f, 200);
        Diamond(sep.G, (qw - 4) / 2f, 0.35f, 0.75f, GOLD_L, Color.Empty);
        Save(sep, fontDir + "/quest_sep.png", true);

        Canvas mm = New(7, 7);
        Iconed(mm, delegate(Graphics g)
        {
            using (GraphicsPath p = new GraphicsPath())
            {
                p.AddPolygon(new PointF[] { new PointF(3.5f, 0.6f), new PointF(6.4f, 3.5f), new PointF(3.5f, 6.4f), new PointF(0.6f, 3.5f) });
                using (PathGradientBrush b = new PathGradientBrush(p)) { b.CenterPoint = new PointF(3.1f, 3.0f); b.CenterColor = GOLD_W; b.SurroundColors = new Color[] { H("B8862E") }; g.FillPath(b, p); }
            }
        }, H("2A1A04", 220), 0.45f);
        Save(mm, fontDir + "/quest_mark_main.png", true);

        Canvas mk = New(7, 7);
        Iconed(mk, delegate(Graphics g)
        {
            using (GraphicsPath p = new GraphicsPath())
            {
                p.AddPolygon(new PointF[] { new PointF(3.5f, 1.2f), new PointF(5.8f, 3.5f), new PointF(3.5f, 5.8f), new PointF(1.2f, 3.5f) });
                using (PathGradientBrush b = new PathGradientBrush(p)) { b.CenterColor = Color.White; b.SurroundColors = new Color[] { G(150) }; g.FillPath(b, p); }
            }
        }, Color.FromArgb(200, 0, 0, 0), 0.4f);
        Save(mk, fontDir + "/quest_mark.png", true);

        Canvas bu = New(5, 7);
        using (GraphicsPath p = new GraphicsPath())
        {
            p.AddPolygon(new PointF[] { new PointF(0.6f, 1.4f), new PointF(4.4f, 3.5f), new PointF(0.6f, 5.6f) });
            using (SolidBrush b = new SolidBrush(Color.White)) bu.G.FillPath(b, p);
        }
        Save(bu, fontDir + "/quest_bullet.png", true);

        Canvas here = New(7, 7);
        Iconed(here, delegate(Graphics g)
        {
            using (Pen p = new Pen(Color.White, 1.15f)) { p.StartCap = LineCap.Round; p.EndCap = LineCap.Round; p.LineJoin = LineJoin.Round; g.DrawLines(p, new PointF[] { new PointF(1.0f, 3.8f), new PointF(2.8f, 5.6f), new PointF(6.1f, 1.4f) }); }
        }, Color.FromArgb(170, 0, 0, 0), 0.35f);
        Save(here, fontDir + "/quest_here.png", true);

        for (int i = 0; i < 8; i++)
        {
            Canvas a = New(7, 7);
            int k = i;
            Iconed(a, delegate(Graphics g)
            {
                GraphicsState st = g.Save();
                g.TranslateTransform(3.5f, 3.5f);
                g.RotateTransform(45 * k);
                using (GraphicsPath p = new GraphicsPath())
                {
                    p.AddPolygon(new PointF[] { new PointF(0, -3.1f), new PointF(2.6f, -0.3f), new PointF(0.85f, -0.3f), new PointF(0.85f, 2.9f), new PointF(-0.85f, 2.9f), new PointF(-0.85f, -0.3f), new PointF(-2.6f, -0.3f) });
                    using (SolidBrush b = new SolidBrush(Color.White)) g.FillPath(b, p);
                }
                g.Restore(st);
            }, Color.FromArgb(170, 0, 0, 0), 0.35f);
            Save(a, fontDir + "/quest_arrow_" + i + ".png", true);
        }
    }

    // ------------------------------------------------------------------ Menus (fonds poses par le titre du coffre)
    // Memes coordonnees que gen-hud.ps1 : image 196 px de large, fenetre du coffre en (MX, MY) ; les cases des objets
    // (18 x 18, creux 16 x 16 a +1) restent exactement sous les cases du jeu.
    const int MX = 10, MY = 26;

    /** Case d'objet 18 x 18 (coordonnees de l'image) : bord sombre, creux, equerres de couleur aux coins si acc. */
    static void MenuSocket(Graphics g, float x, float y, Color acc)
    {
        using (GraphicsPath o = RR(x, y, 18, 18, 1.4f)) using (SolidBrush b = new SolidBrush(H("03070F"))) g.FillPath(b, o);
        Recess(g, x + 1, y + 1, 16, 16);
        using (GraphicsPath o = RR(x + 0.25f, y + 0.25f, 17.5f, 17.5f, 1.3f)) Stroke(g, o, H("2A3D66", 120), 0.25f);
        if (acc.A > 0) Brackets(g, x, y, 18, acc, 0.9f);
    }

    /** Equerres aux quatre coins d'un carre (longueur 3,4 px). */
    static void Brackets(Graphics g, float x, float y, float n, Color c, float thick)
    {
        float h = thick / 2f, l = 3.6f;
        using (Pen p = new Pen(c, thick))
        {
            p.StartCap = LineCap.Square; p.EndCap = LineCap.Square;
            float[][] cs = { new float[] { x + h, y + h, 1, 1 }, new float[] { x + n - h, y + h, -1, 1 }, new float[] { x + h, y + n - h, 1, -1 }, new float[] { x + n - h, y + n - h, -1, -1 } };
            foreach (float[] k in cs) { g.DrawLine(p, k[0], k[1], k[0] + (l - thick) * k[2], k[1]); g.DrawLine(p, k[0], k[1], k[0], k[1] + (l - thick) * k[3]); }
        }
    }

    static void Ornament(Graphics g, int row, int xa, int xb)
    {
        float y = MY + 18 + 18 * row + 7;
        using (SolidBrush b = new SolidBrush(Color.FromArgb(160, 4, 8, 16))) g.FillRectangle(b, xa + MX, y + 0.75f, xb - xa, 0.75f);
        GoldLine(g, xa + MX, xb + MX + 1, y, 0.75f, 255);
        foreach (int xx in new int[] { xa, xb })
        {
            Diamond(g, xx + MX + 0.5f, y + 0.4f, 2.4f, GOLD, H("3A2A0A"));
            Diamond(g, xx + MX + 0.5f, y + 0.4f, 1.0f, GOLD_W, Color.Empty);
        }
    }

    /** Fond de menu : rows rangees de coffre, cases accentuees (slots/colors), filets (ornaments par triplets). */
    public static void MenuBg(int rows, int[] slots, string[] colors, int[] ornaments, string rel)
    {
        int imgH = 114 + 18 * rows;
        Canvas c = New(196, MY + imgH + 4);
        Graphics g = c.G;
        float x0 = MX - 4, y0 = MY - 4, w = 184, h = imgH + 8;
        PanelBody(g, x0, y0, w, h, (uint)(20261122 + rows));
        MenuBgRest(c, g, rows, imgH, slots, colors, ornaments, rel);
    }

    /** Panneau des menus : corps en degrade, voile astral, etoiles, liseres bleu et or, rivets aux coins. */
    static void PanelBody(Graphics g, float x0, float y0, float w, float h, uint seed0)
    {
        using (GraphicsPath o = RR(x0, y0, w, h, 3.5f))
        {
            FillV(g, o, new RectangleF(x0, y0, w, h), H("1A2748"), H("0A1123"));
            using (GraphicsPath glow = new GraphicsPath())
            {
                glow.AddEllipse(x0 + w * 0.15f, y0 - h * 0.15f, w * 0.7f, h * 0.55f);
                using (PathGradientBrush b = new PathGradientBrush(glow)) { b.CenterColor = Color.FromArgb(40, 90, 140, 255); b.SurroundColors = new Color[] { Color.FromArgb(0, 90, 140, 255) }; g.FillPath(b, glow); }
            }
            uint seed = seed0;
            for (int i = 0; i < 90; i++)
            {
                seed = seed * 1103515245 + 12345; float sx = x0 + 5 + (seed >> 8) % (int)(w - 10);
                seed = seed * 1103515245 + 12345; float sy = y0 + 5 + (seed >> 8) % (int)(h - 10);
                seed = seed * 1103515245 + 12345; float fr = ((seed >> 8) % 100) / 100f;
                int k = i % 9;
                if (k == 0) Star(g, sx, sy, 1.6f, 0.35f, Color.FromArgb(220, 255, 255, 255), Color.FromArgb(0, 154, 196, 255));
                float r = k == 0 ? 0.35f : 0.12f + fr * 0.22f;
                using (SolidBrush b = new SolidBrush(Color.FromArgb(60 + (int)(fr * 120), k % 3 == 0 ? 255 : 170, k % 3 == 0 ? 255 : 205, 255))) g.FillEllipse(b, sx - r, sy - r, r * 2, r * 2);
            }
            using (GraphicsPath top = RR(x0 + 1, y0 + 1, w - 2, 10, 3f))
            using (LinearGradientBrush b = new LinearGradientBrush(new RectangleF(x0, y0 + 0.99f, w, 10.02f), Color.FromArgb(40, 150, 185, 255), Color.FromArgb(0, 150, 185, 255), 90f))
                g.FillPath(b, top);
            Stroke(g, o, OUT, 1f);
        }
        using (GraphicsPath l1 = RR(x0 + 1.3f, y0 + 1.3f, w - 2.6f, h - 2.6f, 2.6f)) Stroke(g, l1, H("4A68A8", 130), 0.3f);
        using (GraphicsPath l2 = RR(x0 + 3f, y0 + 3f, w - 6f, h - 6f, 1.8f)) Stroke(g, l2, Color.FromArgb(150, GOLD_D), 0.4f);
        using (GraphicsPath l3 = RR(x0 + 3.5f, y0 + 3.5f, w - 7f, h - 7f, 1.5f)) Stroke(g, l3, Color.FromArgb(60, GOLD_L), 0.25f);
        Stud(g, x0 + 6, y0 + 6); Stud(g, x0 + w - 6, y0 + 6); Stud(g, x0 + 6, y0 + h - 6); Stud(g, x0 + w - 6, y0 + h - 6);
    }

    static void MenuBgRest(Canvas c, Graphics g, int rows, int imgH, int[] slots, string[] colors, int[] ornaments, string rel)
    {
        // Cartouche du titre (x 16 a 179, y 2 a 21) et gemmes de part et d'autre.
        using (GraphicsPath sh = RR(16.5f, 3f, 164, 20, 2.5f)) using (SolidBrush b = new SolidBrush(Color.FromArgb(120, 0, 0, 0))) g.FillPath(b, sh);
        using (GraphicsPath f = RR(16, 2, 164, 20, 2.5f))
        {
            using (LinearGradientBrush b = new LinearGradientBrush(new RectangleF(16, 2, 164, 20), GOLD_L, GOLD_D, 90f)) g.FillPath(b, f);
            Stroke(g, f, OUT, 0.5f);
        }
        using (GraphicsPath inner = RR(18, 4, 160, 16, 1.6f))
        {
            using (SolidBrush b = new SolidBrush(H("070D1C"))) g.FillPath(b, inner);
        }
        using (GraphicsPath inner = RR(18.6f, 4.6f, 158.8f, 14.8f, 1.3f))
        {
            FillV(g, inner, new RectangleF(18, 4.6f, 160, 14.8f), H("3A63B4"), H("0F1E45"));
            using (GraphicsPath hl = RR(19.5f, 5.2f, 157, 5.5f, 1.2f))
            using (LinearGradientBrush b = new LinearGradientBrush(new RectangleF(19, 5.19f, 158, 5.52f), Color.FromArgb(70, 255, 255, 255), Color.FromArgb(0, 255, 255, 255), 90f))
                g.FillPath(b, hl);
        }
        GoldLine(g, 30, 166, 19.2f, 0.4f, 160);
        Gem(g, 5, 2, 19, "4B82E8", "0E1C3A");
        Gem(g, 172, 2, 19, "4B82E8", "0E1C3A");

        // Cases du coffre utilisees par le menu.
        for (int i = 0; i < slots.Length; i++)
        {
            int col = slots[i] % 9, row = slots[i] / 9;
            MenuSocket(g, 7 + 18 * col + MX, 17 + 18 * row + MY, H(colors[i]));
        }
        for (int i = 0; i + 2 < ornaments.Length; i += 3) Ornament(g, ornaments[i], ornaments[i + 1], ornaments[i + 2]);

        // Inventaire du joueur : plaque claire sous le mot « Inventaire », filet, 3 rangees, separation, barre.
        int ly = imgH - 94;
        using (GraphicsPath pl = RR(5 + MX, ly - 2 + MY, 60, 12, 2f))
        {
            FillV(g, pl, new RectangleF(5 + MX, ly - 2 + MY, 60, 12), H("D3DAE4"), H("8A94A3"));
            Stroke(g, pl, H("2A3550"), 0.6f);
            using (GraphicsPath hl = RR(6 + MX, ly - 1.3f + MY, 58, 3.5f, 1.4f)) using (SolidBrush b = new SolidBrush(Color.FromArgb(90, 255, 255, 255))) g.FillPath(b, hl);
        }
        GoldLine(g, 68 + MX, 171 + MX, ly + 3 + MY - 0.1f, 0.7f, 255);
        Diamond(g, 68 + MX + 0.5f, ly + 3.4f + MY, 2.3f, GOLD, H("3A2A0A"));
        Diamond(g, 68 + MX + 0.5f, ly + 3.4f + MY, 1.0f, GOLD_W, Color.Empty);
        int invY = 18 * rows + 31;
        for (int k = 0; k < 3; k++) for (int col = 0; col < 9; col++) MenuSocket(g, 7 + 18 * col + MX, invY - 1 + 18 * k + MY, Color.Empty);
        GoldLine(g, 7 + MX, 169 + MX, invY + 55 + MY + 0.1f, 0.5f, 170);
        for (int col = 0; col < 9; col++) MenuSocket(g, 7 + 18 * col + MX, invY + 57 + MY, Color.Empty);
        Save(c, rel, true);
    }

    // ------------------------------------------------------------------ Inventaire du joueur (touche E)
    /**
     * Fond de l'inventaire du joueur (gui/container/inventory.png) : le jeu en lit 176 x 166 px d'interface dans une
     * texture declaree 256 x 256 ; on la dessine 4 fois plus fine (1024 x 1024), le jeu la remet a l'echelle.
     * Cases aux places du jeu : armure (8, 8 + 18 i), seconde main (77, 62), artisanat (98 + 18 c, 18 + 18 r),
     * resultat (154, 28), sac (8 + 18 c, 84 + 18 r), barre (8 + 18 c, 142). Mot « Artisanat » ecrit par le jeu en
     * gris fonce a (97, 8) : plaque claire dessous. Livre des recettes pose par le jeu en (104, 61).
     */
    static void InventoryBg()
    {
        Canvas c = New(256, 256);
        Graphics g = c.G;
        PanelBody(g, 0, 0, 176, 166, 20261124u);

        // Fenetre du personnage : creux sombre, lueur astrale au pied du joueur, cadre d'or et equerres.
        float px = 25, py = 7, pw = 52, ph = 72;
        using (GraphicsPath o = RR(px, py, pw, ph, 2f))
        {
            using (SolidBrush b = new SolidBrush(H("03070F"))) g.FillPath(b, o);
        }
        using (GraphicsPath inner = RR(px + 1, py + 1, pw - 2, ph - 2, 1.4f))
        {
            FillV(g, inner, new RectangleF(px, py + 1, pw, ph - 2), H("0B1530"), H("060B18"));
            using (GraphicsPath glow = new GraphicsPath())
            {
                glow.AddEllipse(px + 4, py + ph - 26, pw - 8, 30);
                using (PathGradientBrush b = new PathGradientBrush(glow)) { b.CenterColor = Color.FromArgb(70, 120, 170, 255); b.SurroundColors = new Color[] { Color.FromArgb(0, 120, 170, 255) }; g.FillPath(b, glow); }
            }
            // Socle lumineux sous les pieds.
            using (GraphicsPath disc = new GraphicsPath())
            {
                disc.AddEllipse(px + 12, py + ph - 9, pw - 24, 5);
                using (PathGradientBrush b = new PathGradientBrush(disc)) { b.CenterColor = Color.FromArgb(150, 255, 224, 138); b.SurroundColors = new Color[] { Color.FromArgb(0, 201, 162, 74) }; g.FillPath(b, disc); }
            }
            FillRectV(g, px + 1, py + 1, pw - 2, 3f, Color.FromArgb(150, 0, 0, 0), Color.FromArgb(0, 0, 0, 0));
        }
        using (GraphicsPath f = RR(px + 0.3f, py + 0.3f, pw - 0.6f, ph - 0.6f, 1.9f)) Stroke(g, f, Color.FromArgb(200, GOLD_D), 0.5f);
        using (GraphicsPath f = RR(px + 0.9f, py + 0.9f, pw - 1.8f, ph - 1.8f, 1.5f)) Stroke(g, f, H("3A5590", 110), 0.25f);
        BracketsRect(g, px - 0.5f, py - 0.5f, pw + 1, ph + 1, GOLD_L, 0.8f);

        // Armure (liseres bleus) et seconde main (liseres or).
        for (int i = 0; i < 4; i++) MenuSocket(g, 7, 7 + 18 * i, H("7FB2FF", 210));
        MenuSocket(g, 76, 61, H("FFD66E", 230));

        // Plaque claire sous le mot « Artisanat ».
        using (GraphicsPath pl = RR(94, 5, 76, 11, 2f))
        {
            FillV(g, pl, new RectangleF(94, 5, 76, 11), H("D3DAE4"), H("8A94A3"));
            Stroke(g, pl, H("2A3550"), 0.6f);
            using (GraphicsPath hl = RR(95, 5.7f, 74, 3.3f, 1.4f)) using (SolidBrush b = new SolidBrush(Color.FromArgb(90, 255, 255, 255))) g.FillPath(b, hl);
        }

        // Grille d'artisanat 2 x 2, fleche d'or, resultat dans un ecrin.
        for (int r = 0; r < 2; r++) for (int col = 0; col < 2; col++) MenuSocket(g, 97 + 18 * col, 17 + 18 * r, Color.Empty);
        using (GraphicsPath a = new GraphicsPath())
        {
            a.AddPolygon(new PointF[] { new PointF(136, 33), new PointF(144, 33), new PointF(144, 29.5f), new PointF(150.5f, 36), new PointF(144, 42.5f), new PointF(144, 39), new PointF(136, 39) });
            using (LinearGradientBrush b = new LinearGradientBrush(new RectangleF(136, 29, 15, 14), GOLD_L, GOLD_D, 90f)) g.FillPath(b, a);
            Stroke(g, a, OUT, 0.4f);
        }
        using (GraphicsPath o = RR(150, 24, 24, 24, 2f))
        {
            using (LinearGradientBrush b = new LinearGradientBrush(new RectangleF(150, 24, 24, 24), GOLD_L, GOLD_D, 60f)) g.FillPath(b, o);
            Stroke(g, o, OUT, 0.4f);
        }
        using (GraphicsPath o = RR(151.2f, 25.2f, 21.6f, 21.6f, 1.5f)) using (SolidBrush b = new SolidBrush(OUT)) g.FillPath(b, o);
        MenuSocket(g, 153, 27, Color.Empty);
        Stud(g, 152.4f, 26.4f); Stud(g, 171.6f, 26.4f); Stud(g, 152.4f, 45.6f); Stud(g, 171.6f, 45.6f);

        // Filet d'or, sac (3 rangees), filet, barre d'action.
        GoldLine(g, 7, 170, 80.2f, 0.7f, 255);
        Diamond(g, 88.5f, 80.55f, 2.3f, GOLD, H("3A2A0A"));
        Diamond(g, 88.5f, 80.55f, 1.0f, GOLD_W, Color.Empty);
        for (int r = 0; r < 3; r++) for (int col = 0; col < 9; col++) MenuSocket(g, 7 + 18 * col, 83 + 18 * r, Color.Empty);
        GoldLine(g, 7, 169, 138.6f, 0.5f, 170);
        for (int col = 0; col < 9; col++) MenuSocket(g, 7 + 18 * col, 141, H("C9A24A", 150));
        Save(c, "assets/minecraft/textures/gui/container/inventory.png", false);
    }

    /** Equerres aux quatre coins d'un rectangle. */
    static void BracketsRect(Graphics g, float x, float y, float w, float h, Color c, float thick)
    {
        float hh = thick / 2f, l = 5f;
        using (Pen p = new Pen(c, thick))
        {
            p.StartCap = LineCap.Square; p.EndCap = LineCap.Square;
            float[][] cs = { new float[] { x + hh, y + hh, 1, 1 }, new float[] { x + w - hh, y + hh, -1, 1 }, new float[] { x + hh, y + h - hh, 1, -1 }, new float[] { x + w - hh, y + h - hh, -1, -1 } };
            foreach (float[] k in cs) { g.DrawLine(p, k[0], k[1], k[0] + (l - thick) * k[2], k[1]); g.DrawLine(p, k[0], k[1], k[0], k[1] + (l - thick) * k[3]); }
        }
    }

    /** Case de bouton en deux glyphes de 18 x 18 : socle, puis equerres blanches (teintees par le jeu). */
    static void MenuSocketGlyph()
    {
        Canvas c = New(36, 18);
        MenuSocket(c.G, 0, 0, Color.Empty);
        Brackets(c.G, 18, 0, 18, Color.White, 1.1f);
        c.G.Dispose();
        // Chaque case doit marquer sa derniere colonne (avance 19 comme avant).
        foreach (int col in new int[] { 18 * S - 1, 36 * S - 1 })
        {
            bool used = false;
            for (int y = 0; y < c.B.Height && !used; y++) if (c.B.GetPixel(col, y).A > 0) used = true;
            if (!used) c.B.SetPixel(col, 0, Color.FromArgb(1, 0, 0, 0));
        }
        SaveBmp(c.B, fontDir + "/menu_socket.png");
    }

    // ------------------------------------------------------------------ Boite de dialogue et carte du PNJ
    static void Dialogs()
    {
        Canvas d = New(246, 65);
        Plate(d.G, 0, 0, 246, 65, 245, 3f);
        using (GraphicsPath band = RR(2, 2, 242, 16, 2f)) FillV(d.G, band, new RectangleF(2, 2, 242, 16), H("223464", 220), H("0C1630", 220));
        GoldLine(d.G, 6, 240, 18, 0.75f, 255);
        Diamond(d.G, 123, 18.35f, 1.2f, GOLD_W, H("5A4010"));
        Track(d.G, 6, 20, 239, 51);
        Stud(d.G, 5, 5); Stud(d.G, 241, 5); Stud(d.G, 5, 60); Stud(d.G, 241, 60);
        Save(d, fontDir + "/dialog_frame.png", true);

        Canvas p = New(54, 65);
        Plate(p.G, 0, 0, 54, 65, 245, 3f);
        Track(p.G, 2, 1, 51, 63);
        using (GraphicsPath f = RR(2.5f, 1.5f, 49, 62, 1.2f)) Stroke(p.G, f, Color.FromArgb(150, GOLD), 0.4f);
        Save(p, fontDir + "/dialog_portrait.png", true);

        Canvas n = New(112, 160);
        Plate(n.G, 0, 0, 112, 160, 245, 3f);
        Track(n.G, 29, 5, 82, 70);
        using (GraphicsPath f = RR(29.5f, 5.5f, 53, 65, 1.2f)) Stroke(n.G, f, Color.FromArgb(150, GOLD), 0.4f);
        using (GraphicsPath band = RR(2, 73, 108, 11, 1.5f)) FillV(n.G, band, new RectangleF(2, 73, 108, 11), H("223464", 220), H("0C1630", 220));
        GoldLine(n.G, 6, 106, 85, 0.75f, 255);
        Diamond(n.G, 56, 85.35f, 1.2f, GOLD_W, H("5A4010"));
        Track(n.G, 5, 88, 106, 154);
        Stud(n.G, 5, 5); Stud(n.G, 107, 5); Stud(n.G, 5, 155); Stud(n.G, 107, 155);
        Save(n, fontDir + "/npc_card.png", true);
    }

    // ------------------------------------------------------------------ Police miniature (Tektur)
    const string CHARSET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ/%.,:-+_'!?\u25C6\u21E7\u00B7<>|";
    const string NARROW = ".,:'!\u00B7|";
    const string WIDE = "MNW\u21E7";

    static int Core(char c) { return NARROW.IndexOf(c) >= 0 ? 1 : WIDE.IndexOf(c) >= 0 ? 5 : 3; }

    /** Police miniature : coeur de core x 5 px d'interface en haut de chaque case de 7 x cellH, ombre de 0,75 px. */
    static void Tiny(int cellH, string rel)
    {
        Bitmap img = new Bitmap(16 * 7 * S, 4 * cellH * S, PixelFormat.Format32bppArgb);
        FontStyle ts = StyleOf(tektur);
        float em = EmForCap(tektur, ts, 5f * S);
        float asc = em * tektur.GetCellAscent(ts) / tektur.GetEmHeight(ts);
        for (int k = 0; k < CHARSET.Length; k++)
        {
            char ch = CHARSET[k];
            int core = Core(ch);
            float cx = (k % 16) * 7 * S, cy = (k / 16) * cellH * S;
            float boxW = core * S;
            using (Graphics g = Graphics.FromImage(img))
            {
                Smooth(g);
                g.TextRenderingHint = TextRenderingHint.AntiAliasGridFit;
                GraphicsPath p = new GraphicsPath();
                if (ch == '\u25C6')
                {
                    p.AddPolygon(new PointF[] { new PointF(1.5f * S, 0.2f * S), new PointF(2.9f * S, 2.5f * S), new PointF(1.5f * S, 4.8f * S), new PointF(0.1f * S, 2.5f * S) });
                }
                else if (ch == '\u21E7')
                {
                    p.AddPolygon(new PointF[] { new PointF(2.5f * S, 0.1f * S), new PointF(4.9f * S, 2.6f * S), new PointF(3.5f * S, 2.6f * S), new PointF(3.5f * S, 4.9f * S), new PointF(1.5f * S, 4.9f * S), new PointF(1.5f * S, 2.6f * S), new PointF(0.1f * S, 2.6f * S) });
                }
                else if (ch == '\u00B7')
                {
                    p.AddEllipse(0.05f * S, 1.9f * S, 0.9f * S, 0.9f * S);
                }
                else
                {
                    p.AddString(ch.ToString(), tektur, (int)ts, em, new PointF(0, 5f * S - asc), StringFormat.GenericTypographic);
                    RectangleF bd = p.GetBounds();
                    float sx = bd.Width > boxW ? boxW / bd.Width : 1f;
                    using (Matrix m = new Matrix())
                    {
                        m.Translate((boxW - bd.Width * sx) / 2f, 0);
                        m.Scale(sx, 1f);
                        m.Translate(-bd.Left, 0);
                        p.Transform(m);
                    }
                }
                using (Matrix m = new Matrix()) { m.Translate(cx, cy); p.Transform(m); }
                using (GraphicsPath shadow = (GraphicsPath)p.Clone())
                {
                    using (Matrix m = new Matrix()) { m.Translate(0.75f * S, 0.75f * S); shadow.Transform(m); }
                    using (SolidBrush b = new SolidBrush(Color.FromArgb(235, 14, 19, 34))) g.FillPath(b, shadow);
                }
                using (SolidBrush b = new SolidBrush(Color.White)) g.FillPath(b, p);
                p.Dispose();
            }
            // Largeur mesuree par le jeu = coeur + 1 (l'ombre) : derniere colonne marquee, rien au-dela.
            int lastCol = (int)cx + (core + 1) * S - 1;
            for (int y = (int)cy; y < (int)cy + cellH * S; y++)
                for (int x = lastCol + 1; x < (int)cx + 7 * S; x++) img.SetPixel(x, y, Color.FromArgb(0, 0, 0, 0));
            bool used = false;
            for (int y = (int)cy; y < (int)cy + cellH * S && !used; y++) if (img.GetPixel(lastCol, y).A > 0) used = true;
            if (!used) img.SetPixel(lastCol, (int)cy, Color.FromArgb(1, 0, 0, 0));
        }
        SaveBmp(img, rel);
    }

    static float EmForCap(FontFamily f, FontStyle st, float capPx)
    {
        using (GraphicsPath p = new GraphicsPath())
        {
            p.AddString("H", f, (int)st, 100f, new PointF(0, 0), StringFormat.GenericTypographic);
            return 100f * capPx / p.GetBounds().Height;
        }
    }

    // ------------------------------------------------------------------ Police lisse du texte (Outfit, TTF)
    // Le client dessine lui-meme les polices "ttf" (FreeType, ligne de base 7 px sous le haut de la ligne, comme la
    // police du jeu) avec des avances non entieres : l'espacement des lettres est celui de la police. Avance d'un
    // glyphe = avance de FreeType (indices de hinting : arrondie au pixel de la taille sur-echantillonnee) / OVERSAMPLE.
    // La table ecrite pour hud/HdText donne cette avance estimee en 1/OVERSAMPLE de pixel ; HdText y ajoute une
    // marge d'une unite par caractere pour ne jamais sous-estimer une ligne.
    public const float TTF_SIZE = 9f;
    public const int OVERSAMPLE = 8;

    static Dictionary<int, int> TtfFont(string ttf, string name)
    {
        string dst = Path.Combine(pack, "assets", "orbitmmo", "font", name + ".ttf");
        Directory.CreateDirectory(Path.GetDirectoryName(dst));
        File.Copy(ttf, dst, true);
        StringBuilder js = new StringBuilder();
        js.Append("{\n  \"providers\": [\n");
        js.Append("    {\"type\": \"ttf\", \"file\": \"orbitmmo:" + name + ".ttf\", \"size\": " + TTF_SIZE.ToString(System.Globalization.CultureInfo.InvariantCulture)
            + ", \"oversample\": " + OVERSAMPLE + ", \"shift\": [0, 0], \"skip\": \"\"},\n");
        js.Append("    {\"type\": \"reference\", \"id\": \"minecraft:default\"}\n  ]\n}\n");
        File.WriteAllText(Path.Combine(pack, "assets", "orbitmmo", "font", name + ".json"), js.ToString(), new UTF8Encoding(false));

        System.Windows.Media.GlyphTypeface gt = new System.Windows.Media.GlyphTypeface(new Uri(Path.GetFullPath(ttf)));
        double px = Math.Round(TTF_SIZE * OVERSAMPLE);
        Dictionary<int, int> adv = new Dictionary<int, int>();
        foreach (KeyValuePair<int, ushort> e in gt.CharacterToGlyphMap)
        {
            if (e.Key > 0xFFFF || e.Key < 0x20) continue;
            adv[e.Key] = (int)Math.Round(gt.AdvanceWidths[e.Value] * px);
        }
        return adv;
    }

    /** Titre du suivi (« Quetes » / « Quests ») en image : W - 1 px de large, donc une avance d'exactement W. */
    static void TitleGlyph(FontFamily fam, string word, int qw, string rel)
    {
        int w = qw - 1;
        Canvas c = New(w, 9);
        Graphics g = c.G;
        FontStyle fs = StyleOf(fam);
        float em = EmForCap(fam, fs, 6.6f);
        float asc = em * fam.GetCellAscent(fs) / fam.GetEmHeight(fs);
        using (GraphicsPath p = new GraphicsPath())
        {
            // Lettres un peu espacees : une par une.
            float x = 0;
            List<GraphicsPath> letters = new List<GraphicsPath>();
            foreach (char ch in word)
            {
                GraphicsPath lp = new GraphicsPath();
                lp.AddString(ch.ToString(), fam, (int)fs, em, new PointF(0, 8f - asc), StringFormat.GenericTypographic);
                RectangleF bd = lp.GetBounds();
                using (Matrix m = new Matrix()) { m.Translate(x - bd.Left, 0); lp.Transform(m); }
                x += bd.Width + 1.1f;
                letters.Add(lp);
            }
            float tw = x - 1.1f;
            float total = 3 + 4.5f + tw + 4.5f + 3;
            float x0 = (w - total) / 2f;
            foreach (GraphicsPath lp in letters)
            {
                using (Matrix m = new Matrix()) { m.Translate(x0 + 7.5f, 0); lp.Transform(m); }
                p.AddPath(lp, false);
                lp.Dispose();
            }
            using (GraphicsPath sh = (GraphicsPath)p.Clone())
            {
                using (Matrix m = new Matrix()) { m.Translate(0.45f, 0.55f); sh.Transform(m); }
                using (SolidBrush b = new SolidBrush(Color.FromArgb(200, 2, 4, 10))) g.FillPath(b, sh);
            }
            using (Pen pen = new Pen(Color.FromArgb(220, 30, 18, 4), 0.55f)) { pen.LineJoin = LineJoin.Round; g.DrawPath(pen, p); }
            using (LinearGradientBrush b = new LinearGradientBrush(new RectangleF(0, 1f, w, 7.2f), GOLD_W, H("C38A2E"), 90f))
            {
                ColorBlend cb = new ColorBlend();
                cb.Colors = new Color[] { H("FFF6D2"), H("FFE08A"), H("E8B550"), H("C38A2E") };
                cb.Positions = new float[] { 0f, 0.35f, 0.7f, 1f };
                b.InterpolationColors = cb;
                g.FillPath(b, p);
            }
            Diamond(g, x0 + 2.5f, 5.0f, 2.3f, H("FFE08A"), H("5A4010"));
            Diamond(g, x0 + 2.5f, 5.0f, 1.0f, GOLD_W, Color.Empty);
            Diamond(g, x0 + total - 2.5f, 5.0f, 2.3f, H("FFE08A"), H("5A4010"));
            Diamond(g, x0 + total - 2.5f, 5.0f, 1.0f, GOLD_W, Color.Empty);
        }
        Save(c, rel, true);
    }

    static List<PrivateFontCollection> keep = new List<PrivateFontCollection>();

    static FontFamily Load(string file)
    {
        if (!File.Exists(file)) throw new Exception("Police introuvable : " + file);
        PrivateFontCollection c = new PrivateFontCollection();
        c.AddFontFile(file);
        keep.Add(c);
        return c.Families[0];
    }

    static FontStyle StyleOf(FontFamily f) { return f.IsStyleAvailable(FontStyle.Regular) ? FontStyle.Regular : FontStyle.Bold; }

    // ------------------------------------------------------------------ Entree
    public static void Run(string packDir, string fontsDir, string widthsOut, int questWidth)
    {
        pack = packDir;
        fontDir = "assets/orbitmmo/textures/font";
        hudDir = "assets/minecraft/textures/gui/sprites/hud";
        // Une collection par fichier : Outfit Regular et Bold porteraient sinon le meme nom de famille.
        tektur = Load(Path.Combine(fontsDir, "Tektur-Medium.ttf"));
        FontFamily orr = Load(Path.Combine(fontsDir, "Outfit-Regular.ttf"));
        FontFamily ob = Load(Path.Combine(fontsDir, "Outfit-Bold.ttf"));

        Wings();
        Bridge();
        Cast();
        Fills();
        PlateFrame();
        Emblems();
        ZoneIcons();
        Hotbar();
        QuestPanel(questWidth);
        MenuSocketGlyph();
        Dialogs();
        InventoryBg();
        Tiny(7, fontDir + "/hud_tiny.png");
        Tiny(17, fontDir + "/hud_tiny_cast.png");
        Tiny(30, fontDir + "/hud_tiny_title.png");
        TitleGlyph(ob, "Quêtes", questWidth, fontDir + "/quest_title_fr.png");
        TitleGlyph(ob, "Quests", questWidth, fontDir + "/quest_title_en.png");
        // Anciennes polices bitmap du texte (remplacees par les TTF).
        foreach (string old in new string[] { fontDir + "/qtext.png", fontDir + "/qtext_b.png" })
        {
            string op = Path.Combine(pack, old.Replace('/', Path.DirectorySeparatorChar));
            if (File.Exists(op)) File.Delete(op);
        }
        Dictionary<int, int> r = TtfFont(Path.Combine(fontsDir, "Outfit-Regular.ttf"), "qtext");
        Dictionary<int, int> b = TtfFont(Path.Combine(fontsDir, "Outfit-Bold.ttf"), "qtext_b");
        List<int> keys = new List<int>(r.Keys);
        keys.Sort();
        StringBuilder sb = new StringBuilder();
        sb.Append("# Avances estimees des polices orbitmmo:qtext / qtext_b, en 1/" + OVERSAMPLE + " de pixel (tools/hd/HudHd.cs)\n");
        foreach (int c in keys) if (b.ContainsKey(c)) sb.Append(c.ToString("X4") + " " + r[c] + " " + b[c] + "\n");
        File.WriteAllText(widthsOut, sb.ToString(), new UTF8Encoding(false));
    }
}
