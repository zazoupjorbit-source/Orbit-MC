// Pack de ressources HD de « Vole mon Orbito » : créatures, objets, icônes de menu, HUD, bannières de titre.
// Tout est dessiné par code (aucun mod côté joueur). C# 5 (compilé par Add-Type de PowerShell 5.1) :
// pas d'interpolation de chaînes ni de "=>".
//
//  - Créatures : textures du jeu (jar client 26.3) recolorées aux couleurs de chaque Orbito, agrandies ×4
//    avec relief, grain et motifs (étoiles, circuits, ondes...). Remplacent les textures du jeu, mais le pack
//    n'est envoyé que sur le serveur « vol ».
//  - Objets et icônes : 128 × 128, modèles orbitvol:<id> (composant item_model).
//  - HUD : police orbitvol:hud (panneau d'argent, icônes, barre de vol), chiffres orbitvol:c_*, bannières orbitvol:titre.
//    Largeurs des glyphes écrites dans src/main/resources/glyphes.txt pour Hud.java.
using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
using System.Drawing.Text;
using System.IO;
using System.IO.Compression;
using System.Text;

namespace OrbitVol
{
    public static class Atelier
    {
        static string pack, docs, res;
        static FontFamily tektur;
        static PrivateFontCollection polices = new PrivateFontCollection();
        static StringBuilder glyphes = new StringBuilder();
        static List<KeyValuePair<string, Bitmap>> apercuObjets = new List<KeyValuePair<string, Bitmap>>();
        static List<KeyValuePair<string, Bitmap>> apercuBetes = new List<KeyValuePair<string, Bitmap>>();
        static List<KeyValuePair<string, Bitmap>> apercuHud = new List<KeyValuePair<string, Bitmap>>();

        // ------------------------------------------------------------------ couleurs
        static Color H(string hex) { return H(hex, 255); }
        static Color H(string hex, int a)
        {
            int v = Convert.ToInt32(hex, 16);
            return Color.FromArgb(a, (v >> 16) & 255, (v >> 8) & 255, v & 255);
        }
        static Color Mix(Color a, Color b, double t)
        {
            t = Math.Max(0, Math.Min(1, t));
            return Color.FromArgb((int)(a.A + (b.A - a.A) * t), (int)(a.R + (b.R - a.R) * t), (int)(a.G + (b.G - a.G) * t), (int)(a.B + (b.B - a.B) * t));
        }
        static Color Ramp(Color[] r, double t)
        {
            t = Math.Max(0, Math.Min(1, t)) * (r.Length - 1);
            int i = Math.Min(r.Length - 2, (int)t);
            return Mix(r[i], r[i + 1], t - i);
        }
        static Color[] R(params string[] hex)
        {
            Color[] c = new Color[hex.Length];
            for (int i = 0; i < hex.Length; i++) c[i] = H(hex[i]);
            return c;
        }
        static readonly Color CONTOUR = H("070A14"), OR = H("E0B864"), OR_C = H("FFE08A"), OR_F = H("7A5F22"),
            BLEU = H("3A7FFF"), GLACE = H("62CFF4"), NUIT = H("0B1220"), NUIT_C = H("1A2740"), BLANC = H("F4F8FF");

        static double Hash(int x, int y, int seed)
        {
            unchecked
            {
                int h = x * 374761393 + y * 668265263 + seed * 982451653;
                h = (h ^ (h >> 13)) * 1274126177;
                h ^= h >> 16;
                return (h & 0x7FFFFFFF) / (double)0x7FFFFFFF;
            }
        }

        // ------------------------------------------------------------------ entrée

        public static string Run(string jar, string packDir, string docDir, string resDir, string fontDir)
        {
            pack = packDir; docs = docDir; res = resDir;
            polices.AddFontFile(Path.Combine(fontDir, "Tektur-Medium.ttf"));
            tektur = polices.Families[0];
            if (Directory.Exists(Path.Combine(pack, "assets"))) Directory.Delete(Path.Combine(pack, "assets"), true);
            Directory.CreateDirectory(pack);
            Directory.CreateDirectory(docs);
            File.WriteAllText(Path.Combine(pack, "pack.mcmeta"),
                "{\n  \"pack\": {\n    \"description\": \"\\u00a76ORBIT \\u00a77- Vole mon Orbito (HD)\",\n    \"min_format\": 97,\n    \"max_format\": 97\n  }\n}\n", new UTF8Encoding(false));

            int betes = 0;
            using (ZipArchive zip = ZipFile.OpenRead(jar)) betes = Creatures(zip);
            int objets = Objets();
            int hud = Hud();
            int decor = Decor();
            Ecrire("assets/orbitvol/sounds.json", "{\n  \"musique\": { \"category\": \"music\", \"sounds\": [\n"
                + "    { \"name\": \"minecraft:music/game/creative/aria_math\", \"stream\": true },\n"
                + "    { \"name\": \"minecraft:music/game/creative/biome_fest\", \"stream\": true },\n"
                + "    { \"name\": \"minecraft:music/game/creative/blind_spots\", \"stream\": true },\n"
                + "    { \"name\": \"minecraft:music/game/creative/dreiton\", \"stream\": true },\n"
                + "    { \"name\": \"minecraft:music/game/creative/taswell\", \"stream\": true },\n"
                + "    { \"name\": \"minecraft:music/game/mice_on_venus\", \"stream\": true },\n"
                + "    { \"name\": \"minecraft:music/game/floating_dream\", \"stream\": true }\n  ] }\n}\n");
            File.WriteAllText(Path.Combine(res, "glyphes.txt"), glyphes.ToString(), new UTF8Encoding(false));
            Planche(Path.Combine(docs, "objets.png"), apercuObjets, 8, 128);
            Planche(Path.Combine(docs, "creatures.png"), apercuBetes, 4, 256);
            Planche(Path.Combine(docs, "hud.png"), apercuHud, 1, 0);
            return betes + " textures de créatures, " + objets + " objets/icônes, " + hud + " images de HUD, " + decor + " modèles 3D de décor (" + nbDecor + " textures).";
        }

        // ------------------------------------------------------------------ outils d'image

        static Bitmap Charger(ZipArchive zip, string chemin)
        {
            ZipArchiveEntry e = zip.GetEntry("assets/minecraft/textures/entity/" + chemin + ".png");
            if (e == null) throw new Exception("Texture introuvable dans le jar : " + chemin);
            using (Stream s = e.Open())
            using (MemoryStream ms = new MemoryStream())
            {
                s.CopyTo(ms);
                ms.Position = 0;
                using (Bitmap b = new Bitmap(ms)) return new Bitmap(b);
            }
        }

        static void Sauver(Bitmap b, string rel)
        {
            string p = Path.Combine(pack, rel.Replace('/', Path.DirectorySeparatorChar));
            Directory.CreateDirectory(Path.GetDirectoryName(p));
            b.Save(p, ImageFormat.Png);
        }

        static void Lisse(Graphics g)
        {
            g.SmoothingMode = SmoothingMode.AntiAlias;
            g.PixelOffsetMode = PixelOffsetMode.HighQuality;
            g.InterpolationMode = InterpolationMode.HighQualityBicubic;
            g.CompositingQuality = CompositingQuality.HighQuality;
            g.TextRenderingHint = TextRenderingHint.AntiAliasGridFit;
        }

        static void Planche(string chemin, List<KeyValuePair<string, Bitmap>> l, int cols, int cell)
        {
            if (l.Count == 0) return;
            int w = 0, h = 0;
            if (cell == 0)
            {
                foreach (KeyValuePair<string, Bitmap> kv in l) { w = Math.Max(w, kv.Value.Width); h += kv.Value.Height + 30; }
                w += 20;
            }
            else
            {
                w = cols * (cell + 10) + 10;
                h = ((l.Count + cols - 1) / cols) * (cell + 34) + 10;
            }
            using (Bitmap b = new Bitmap(w, h))
            using (Graphics g = Graphics.FromImage(b))
            using (Font f = new Font("Segoe UI", 10f))
            {
                g.Clear(H("2B3445"));
                g.InterpolationMode = InterpolationMode.NearestNeighbor;
                g.PixelOffsetMode = PixelOffsetMode.Half;
                int y = 10;
                for (int i = 0; i < l.Count; i++)
                {
                    Bitmap img = l[i].Value;
                    if (cell == 0)
                    {
                        g.DrawString(l[i].Key, f, Brushes.White, 10, y);
                        g.DrawImage(img, 10, y + 18, img.Width, img.Height);
                        y += img.Height + 30;
                    }
                    else
                    {
                        int x = 10 + (i % cols) * (cell + 10), yy = 10 + (i / cols) * (cell + 34);
                        double sc = Math.Min(cell / (double)img.Width, cell / (double)img.Height);
                        g.DrawImage(img, x, yy, (int)(img.Width * sc), (int)(img.Height * sc));
                        g.DrawString(l[i].Key, f, Brushes.White, x, yy + cell + 4);
                    }
                }
                b.Save(chemin, ImageFormat.Png);
            }
        }

        // ================================================================== CRÉATURES

        sealed class Bete
        {
            public string Nom;
            public string[] Sources;   // chemins sous textures/entity, sans .png
            public Color[] Rampe;
            public Color Accent;
            public string Motif;       // etoiles, circuit, ondes, rayures, taches, lueur, eclairs
            public double Force = 0.88;
            public bool GardeYeux = true;
        }

        static Bete B(string nom, string motif, string accent, string[] rampe, params string[] sources)
        {
            Bete b = new Bete();
            b.Nom = nom; b.Motif = motif; b.Accent = H(accent); b.Rampe = R(rampe); b.Sources = sources;
            return b;
        }

        static List<Bete> Betes()
        {
            List<Bete> l = new List<Bete>();
            l.Add(B("Dodo Turbo", "eclairs", "FFE45C", new string[] { "0B2A66", "2F7BFF", "9CD8FF", "FFFFFF" }, "chicken/chicken_temperate"));
            l.Add(B("Dodo Roi", "etoiles", "FFF2B0", new string[] { "3A1060", "8A3FD0", "E0B864", "FFF2B0" }, "chicken/chicken_warm"));
            l.Add(B("Lapin Satellite", "circuit", "62CFF4", new string[] { "1C2433", "6B7A90", "C9D4E3", "FFFFFF" }, "rabbit/rabbit_brown"));
            l.Add(B("Grenouille Wifi", "ondes", "E8FFF0", new string[] { "053A2A", "13B07A", "5CFFB8", "D8FFF0" }, "frog/frog_temperate"));
            l.Add(B("Grenouille Supernova", "lueur", "FFF07A", new string[] { "3A0630", "D0287A", "FF8A2A", "FFE45C" }, "frog/frog_warm"));
            l.Add(B("Tatou Patate", "taches", "6E4A22", new string[] { "3A2410", "9A6A34", "D8B070", "F6E2B0" }, "armadillo/armadillo"));
            l.Add(B("Cochon Fusée", "rayures", "FFFFFF", new string[] { "4A0A12", "D02A3A", "FF7A6A", "FFE0D8" }, "pig/pig_temperate"));
            l.Add(B("Mouton Nébuleuse", "etoiles", "FFFFFF", new string[] { "1A0A40", "6A2AC0", "E05AD0", "9CE8FF" }, "sheep/sheep_wool", "sheep/sheep_wool_undercoat", "sheep/sheep"));
            l.Add(B("Axolotl Astronaute", "circuit", "FF8A2A", new string[] { "2A3346", "9AA8C0", "F0F4FA", "FFFFFF" }, "axolotl/axolotl_lucy"));
            l.Add(B("Perroquet Radar", "ondes", "FFE45C", new string[] { "06323A", "0FA3A8", "6AF0E0", "FFF6B0" }, "parrot/parrot_red_blue"));
            l.Add(B("Renard Orbital", "etoiles", "FF9A3A", new string[] { "0A1030", "2A3A9A", "FF8A2A", "FFE0B0" }, "fox/fox", "fox/fox_sleep"));
            l.Add(B("Tortue Hyperdrive", "rayures", "62CFF4", new string[] { "141C26", "4A6478", "B8D8E8", "FFFFFF" }, "turtle/turtle"));
            l.Add(B("Chat Quantique", "circuit", "62F4E8", new string[] { "120A2A", "4A2A9A", "9A6AF0", "D8C8FF" }, "cat/cat_all_black"));
            l.Add(B("Bonhomme Cryo", "etoiles", "FFFFFF", new string[] { "0A3050", "3A9AD8", "B8ECFF", "FFFFFF" }, "snow_golem/snow_golem"));
            l.Add(B("Abeille Réacteur", "rayures", "62CFF4", new string[] { "140C06", "6A3008", "FF7A10", "FFD060" }, "bee/bee", "bee/bee_angry", "bee/bee_nectar", "bee/bee_angry_nectar"));
            l.Add(B("Loup Comète", "etoiles", "9CE8FF", new string[] { "06102A", "1A3A8A", "62CFF4", "F0FAFF" }, "wolf/wolf", "wolf/wolf_tame", "wolf/wolf_angry"));
            l.Add(B("Vache Lunaire", "taches", "5A6474", new string[] { "2A2E38", "8A92A0", "D0D6E0", "FFFFFF" }, "cow/mooshroom_red"));
            l.Add(B("Panda Gravité", "ondes", "C86BFF", new string[] { "0E0818", "3A1A6A", "B0A0E0", "FFFFFF" }, "panda/panda"));
            l.Add(B("Fée Antenne", "lueur", "FFF2B0", new string[] { "5A1040", "E05AA0", "FFB0D8", "FFF2B0" }, "allay/allay"));
            l.Add(B("Ours Polaris", "etoiles", "62CFF4", new string[] { "3A5470", "A8D0EC", "E8F6FF", "FFFFFF" }, "bear/polarbear"));
            l.Add(B("Chameau Stellaire", "etoiles", "FFFFFF", new string[] { "0B1640", "2A4AA0", "E0B864", "FFF2C0" }, "camel/camel"));
            l.Add(B("Renifleur d'Étoiles", "etoiles", "FFE45C", new string[] { "081C24", "1A6A70", "8A4AD0", "E8D0FF" }, "sniffer/sniffer"));
            l.Add(B("Titan Boulon", "circuit", "FFE08A", new string[] { "10141C", "3A4454", "8A96A8", "E0B864" }, "iron_golem/iron_golem"));
            l.Add(B("Méduse Wifi", "ondes", "FFFFFF", new string[] { "081A3A", "1A8AD0", "62F4E8", "FFB0F0" }, "squid/glow_squid"));
            l.Add(B("Rex Rikiki", "taches", "1A4A10", new string[] { "0E2A08", "3A8A1A", "9AE04A", "F0FFB0" }, "illager/ravager"));
            l.Add(B("Gardien du Néant", "circuit", "C86BFF", new string[] { "05030C", "1A0E30", "5A2AA0", "B070FF" }, "warden/warden", "warden/warden_bioluminescent_layer", "warden/warden_heart", "warden/warden_pulsating_spots_1", "warden/warden_pulsating_spots_2"));
            l.Add(B("Astraeos de Poche", "lueur", "FFFFFF", new string[] { "6A4A10", "E0B864", "FFF2B0", "9CE8FF" }, "breeze/breeze", "breeze/breeze_wind"));
            l.Add(B("Le Grand Bidule Galactique", "etoiles", "FFE45C", new string[] { "200A40", "C02AA0", "3AC8F0", "FFF0B0" }, "ghast/ghast", "ghast/ghast_shooting"));
            return l;
        }

        static int Creatures(ZipArchive zip)
        {
            int n = 0, seed = 7;
            foreach (Bete b in Betes())
            {
                bool premiere = true;
                foreach (string src in b.Sources)
                {
                    Bitmap s = Charger(zip, src);
                    Bitmap hd = Agrandir(s, b, seed++);
                    Sauver(hd, "assets/minecraft/textures/entity/" + src + ".png");
                    if (premiere) apercuBetes.Add(new KeyValuePair<string, Bitmap>(b.Nom, hd));
                    premiere = false;
                    s.Dispose();
                    n++;
                }
            }
            return n;
        }

        static double Lum(Color c) { return (0.299 * c.R + 0.587 * c.G + 0.114 * c.B) / 255.0; }

        /** Recolore puis agrandit ×4 : chaque pixel du jeu devient un carreau 4 × 4 avec relief, grain et motif. */
        static Bitmap Agrandir(Bitmap s, Bete b, int seed)
        {
            const int K = 4;
            int w = s.Width, h = s.Height;
            Color[] src = new Color[w * h];
            double lo = 1, hi = 0;
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++)
                {
                    Color c = s.GetPixel(x, y);
                    src[y * w + x] = c;
                    if (c.A < 16) continue;
                    double l = Lum(c);
                    lo = Math.Min(lo, l); hi = Math.Max(hi, l);
                }
            if (hi - lo < 0.05) { lo = 0; hi = 1; }
            Bitmap o = new Bitmap(w * K, h * K, PixelFormat.Format32bppArgb);
            BitmapData d = o.LockBits(new Rectangle(0, 0, o.Width, o.Height), ImageLockMode.WriteOnly, PixelFormat.Format32bppArgb);
            int[] px = new int[o.Width * o.Height];
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++)
                {
                    Color c = src[y * w + x];
                    if (c.A == 0) continue;
                    double t = (Lum(c) - lo) / (hi - lo);
                    // Yeux : pixels presque noirs ou très saturés gardés (lisibilité du visage).
                    float sat = c.GetSaturation(), br = c.GetBrightness();
                    bool oeil = b.GardeYeux && br < 0.12;
                    Color nc = oeil ? H("05060A") : Mix(c, Ramp(b.Rampe, t), b.Force);
                    if (!oeil && sat > 0.85 && br > 0.45 && t > 0.75) nc = Mix(nc, b.Accent, 0.5);
                    bool bordG = x == 0 || src[y * w + x - 1].A == 0;
                    bool bordH = y == 0 || src[(y - 1) * w + x].A == 0;
                    bool bordD = x == w - 1 || src[y * w + x + 1].A == 0;
                    bool bordB = y == h - 1 || src[(y + 1) * w + x].A == 0;
                    for (int v = 0; v < K; v++)
                        for (int u = 0; u < K; u++)
                        {
                            int X = x * K + u, Y = y * K + v;
                            double f = 1.0 + 0.09 * (1.5 - (u + v) / 2.0) / 1.5;      // biseau : clair en haut à gauche
                            f += (Hash(X, Y, seed) - 0.5) * 0.03;                       // grain fin
                            if ((u == 0 && bordG) || (v == 0 && bordH)) f += 0.06;
                            if ((u == K - 1 && bordD) || (v == K - 1 && bordB)) f -= 0.10;
                            Color p = Eclairer(nc, f);
                            if (!oeil) p = Motif(p, b, X, Y, w * K, h * K, seed, t);
                            else if (u == 1 && v == 1) p = H("FFFFFF");                 // reflet dans l'œil
                            px[Y * o.Width + X] = Color.FromArgb(c.A, p.R, p.G, p.B).ToArgb();
                        }
                }
            System.Runtime.InteropServices.Marshal.Copy(px, 0, d.Scan0, px.Length);
            o.UnlockBits(d);
            return o;
        }

        /** Bruit lisse (valeurs alÃ©atoires interpolÃ©es) : variations douces de la matiÃ¨re. */
        static double Bruit(double x, double y, int seed)
        {
            int x0 = (int)Math.Floor(x), y0 = (int)Math.Floor(y);
            double fx = x - x0, fy = y - y0;
            fx = fx * fx * (3 - 2 * fx); fy = fy * fy * (3 - 2 * fy);
            double a = Hash(x0, y0, seed), b = Hash(x0 + 1, y0, seed), c = Hash(x0, y0 + 1, seed), d = Hash(x0 + 1, y0 + 1, seed);
            return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy;
        }

        static Color Eclairer(Color c, double f)
        {
            if (f >= 1) return Mix(c, Color.White, (f - 1) * 1.4);
            return Mix(c, Color.Black, (1 - f) * 1.4);
        }

        static Color Motif(Color p, Bete b, int X, int Y, int W, int Hh, int seed, double t)
        {
            switch (b.Motif)
            {
                case "etoiles":
                    {
                        double r = Hash(X / 2, Y / 2, seed + 11);
                        if (r < 0.010) return Mix(p, b.Accent, 0.9);
                        if (r < 0.018) return Mix(p, Color.White, 0.55);
                        return p;
                    }
                case "circuit":
                    {
                        int gx = X % 16, gy = Y % 16;
                        bool ligne = (gy == 7 && Hash(X / 16, Y / 16, seed) < 0.5) || (gx == 7 && Hash(X / 16, Y / 16, seed + 3) < 0.4);
                        bool noeud = gx >= 6 && gx <= 8 && gy >= 6 && gy <= 8 && Hash(X / 16, Y / 16, seed + 5) < 0.35;
                        if (noeud) return Mix(p, b.Accent, 0.85);
                        if (ligne) return Mix(p, b.Accent, 0.45);
                        return p;
                    }
                case "ondes":
                    {
                        double cx = W * 0.5, cy = Hh * 0.5;
                        double dd = Math.Sqrt((X - cx) * (X - cx) + (Y - cy) * (Y - cy));
                        if (((int)dd) % 12 == 0) return Mix(p, b.Accent, 0.35);
                        return p;
                    }
                case "rayures":
                    {
                        if (((X + Y) / 6) % 4 == 0 && t > 0.3) return Mix(p, b.Accent, 0.30);
                        return p;
                    }
                case "taches":
                    {
                        double r = Hash(X / 10, Y / 10, seed + 21);
                        if (r < 0.18) return Mix(p, b.Accent, 0.35);
                        return p;
                    }
                case "lueur":
                    {
                        double r = Hash(X / 3, Y / 3, seed + 31);
                        Color q = Mix(p, b.Accent, 0.12 + 0.18 * t);
                        if (r < 0.012) return Mix(q, Color.White, 0.8);
                        return q;
                    }
                case "eclairs":
                    {
                        int k = (X * 3 + (Y / 4) * 7) % 40;
                        if (k < 2 && ((Y / 8) % 3 == 0)) return Mix(p, b.Accent, 0.75);
                        double r = Hash(X / 2, Y / 2, seed + 41);
                        if (r < 0.006) return Mix(p, Color.White, 0.7);
                        return p;
                    }
            }
            return p;
        }

        // ================================================================== OBJETS ET ICÔNES

        delegate void Dessin(Graphics g);

        static int nbObjets = 0;

        /** Icône 128 × 128 dessinée dans un repère 0..128, contour sombre et reflet ajoutés par le dessin lui-même. */
        static void Icone(string id, bool enMain, Dessin dessin)
        {
            Bitmap b = new Bitmap(128, 128, PixelFormat.Format32bppArgb);
            using (Graphics g = Graphics.FromImage(b))
            {
                Lisse(g);
                dessin(g);
            }
            Sauver(b, "assets/orbitvol/textures/item/" + id + ".png");
            string modele = "{\n  \"parent\": \"minecraft:item/" + (enMain ? "handheld" : "generated") + "\",\n  \"textures\": { \"layer0\": \"orbitvol:item/" + id + "\" }\n}\n";
            Ecrire("assets/orbitvol/models/item/" + id + ".json", modele);
            Ecrire("assets/orbitvol/items/" + id + ".json", "{\n  \"model\": { \"type\": \"minecraft:model\", \"model\": \"orbitvol:item/" + id + "\" }\n}\n");
            apercuObjets.Add(new KeyValuePair<string, Bitmap>(id, b));
            nbObjets++;
        }

        static void Ecrire(string rel, string texte)
        {
            string p = Path.Combine(pack, rel.Replace('/', Path.DirectorySeparatorChar));
            Directory.CreateDirectory(Path.GetDirectoryName(p));
            File.WriteAllText(p, texte, new UTF8Encoding(false));
        }

        static GraphicsPath Arrondi(float x, float y, float w, float h, float r)
        {
            GraphicsPath p = new GraphicsPath();
            float d = r * 2;
            p.AddArc(x, y, d, d, 180, 90);
            p.AddArc(x + w - d, y, d, d, 270, 90);
            p.AddArc(x + w - d, y + h - d, d, d, 0, 90);
            p.AddArc(x, y + h - d, d, d, 90, 90);
            p.CloseFigure();
            return p;
        }

        static GraphicsPath Etoile(float cx, float cy, float r1, float r2, int branches, float rot)
        {
            GraphicsPath p = new GraphicsPath();
            PointF[] pts = new PointF[branches * 2];
            for (int i = 0; i < pts.Length; i++)
            {
                double a = rot + Math.PI * i / branches - Math.PI / 2;
                float r = i % 2 == 0 ? r1 : r2;
                pts[i] = new PointF(cx + (float)(Math.Cos(a) * r), cy + (float)(Math.Sin(a) * r));
            }
            p.AddPolygon(pts);
            return p;
        }

        /** Remplit un chemin : dégradé vertical, contour sombre épais, reflet clair en haut. */
        static void Volume(Graphics g, GraphicsPath p, Color haut, Color bas, float contour)
        {
            RectangleF r = p.GetBounds();
            using (Pen pc = new Pen(CONTOUR, contour * 2) { LineJoin = LineJoin.Round })
                g.DrawPath(pc, p);
            using (LinearGradientBrush br = new LinearGradientBrush(new RectangleF(r.X, r.Y - 1, r.Width + 1, r.Height + 2), haut, bas, 90f))
                g.FillPath(br, p);
            // reflet : la moitié haute, en blanc transparent, découpée par le chemin
            Region ancien = g.Clip;
            g.SetClip(p, CombineMode.Intersect);
            using (GraphicsPath refl = new GraphicsPath())
            {
                refl.AddEllipse(r.X - r.Width * 0.1f, r.Y - r.Height * 0.55f, r.Width * 1.2f, r.Height * 0.95f);
                using (SolidBrush sb = new SolidBrush(Color.FromArgb(55, 255, 255, 255))) g.FillPath(sb, refl);
            }
            g.Clip = ancien;
        }

        static void Eclat(Graphics g, float x, float y, float r, Color c)
        {
            using (GraphicsPath p = Etoile(x, y, r, r * 0.25f, 4, 0))
            using (SolidBrush b = new SolidBrush(c)) g.FillPath(b, p);
        }

        static void Halo(Graphics g, float cx, float cy, float r, Color c)
        {
            using (GraphicsPath p = new GraphicsPath())
            {
                p.AddEllipse(cx - r, cy - r, r * 2, r * 2);
                using (PathGradientBrush b = new PathGradientBrush(p))
                {
                    b.CenterColor = c;
                    b.SurroundColors = new Color[] { Color.FromArgb(0, c) };
                    g.FillPath(b, p);
                }
            }
        }

        static GraphicsPath Cercle(float cx, float cy, float r)
        {
            GraphicsPath p = new GraphicsPath();
            p.AddEllipse(cx - r, cy - r, r * 2, r * 2);
            return p;
        }

        static void Texte(Graphics g, string s, float cx, float cy, float taille, Color fond, Color contour, float ep)
        {
            using (GraphicsPath p = new GraphicsPath())
            using (StringFormat sf = new StringFormat())
            {
                sf.Alignment = StringAlignment.Center;
                sf.LineAlignment = StringAlignment.Center;
                p.AddString(s, tektur, (int)FontStyle.Regular, taille, new PointF(cx, cy), sf);
                using (Pen pe = new Pen(contour, ep) { LineJoin = LineJoin.Round }) g.DrawPath(pe, p);
                using (SolidBrush b = new SolidBrush(fond)) g.FillPath(b, p);
            }
        }

        static int Objets()
        {
            // --- objets du jeu
            Icone("batte", true, delegate (Graphics g)
            {
                using (GraphicsPath p = new GraphicsPath())
                {
                    p.AddPolygon(new PointF[] { new PointF(14, 104), new PointF(24, 114), new PointF(108, 34), new PointF(96, 18), new PointF(84, 22) });
                    p.CloseFigure();
                    Volume(g, p, H("8FB8FF"), H("1F4FB0"), 5);
                }
                using (Pen pn = new Pen(OR, 7)) { g.DrawLine(pn, 40, 82, 50, 92); g.DrawLine(pn, 64, 60, 74, 70); }
                using (GraphicsPath poignee = Arrondi(8, 98, 22, 22, 6)) Volume(g, poignee, H("3A2A1A"), H("120C06"), 4);
                using (GraphicsPath e = Etoile(98, 30, 12, 5, 5, 0.3f)) Volume(g, e, OR_C, OR, 3);
                Eclat(g, 112, 14, 7, Color.White);
            });
            Icone("filet", false, delegate (Graphics g)
            {
                using (GraphicsPath p = Cercle(64, 64, 46)) Volume(g, p, H("BFF4FF"), H("2A8AC8"), 5);
                g.SetClip(new RectangleF(18, 18, 92, 92));
                using (GraphicsPath c = Cercle(64, 64, 46))
                {
                    g.SetClip(c);
                    using (Pen pn = new Pen(Color.FromArgb(200, 255, 255, 255), 3))
                        for (int i = -60; i < 140; i += 16) { g.DrawLine(pn, i, 0, i + 128, 128); g.DrawLine(pn, i, 128, i + 128, 0); }
                }
                g.ResetClip();
                using (Pen pc = new Pen(CONTOUR, 4)) g.DrawEllipse(pc, 18, 18, 92, 92);
                Eclat(g, 44, 40, 9, Color.White);
            });
            Icone("cape", false, delegate (Graphics g)
            {
                using (GraphicsPath p = new GraphicsPath())
                {
                    p.AddBezier(40, 22, 30, 60, 18, 90, 14, 112);
                    p.AddBezier(14, 112, 40, 104, 88, 104, 114, 112);
                    p.AddBezier(114, 112, 110, 90, 98, 60, 88, 22);
                    p.CloseFigure();
                    Volume(g, p, H("A66BFF"), H("2A0E5A"), 5);
                }
                using (Pen pn = new Pen(Color.FromArgb(90, 255, 255, 255), 3)) { g.DrawBezier(pn, 56, 30, 50, 60, 44, 90, 40, 106); g.DrawBezier(pn, 74, 30, 78, 60, 84, 90, 88, 106); }
                using (GraphicsPath f = Cercle(64, 24, 11)) Volume(g, f, OR_C, OR_F, 3);
                Eclat(g, 100, 40, 8, H("E8D0FF"));
            });
            Icone("propulseur", false, delegate (Graphics g)
            {
                Halo(g, 40, 92, 34, Color.FromArgb(170, 255, 122, 26));
                using (GraphicsPath f = new GraphicsPath())
                {
                    f.AddPolygon(new PointF[] { new PointF(14, 112), new PointF(30, 78), new PointF(46, 96) });
                    Volume(g, f, H("FFE08A"), H("FF4D1A"), 3);
                }
                using (GraphicsPath p = new GraphicsPath())
                {
                    p.AddBezier(36, 90, 50, 60, 80, 26, 112, 14);
                    p.AddBezier(112, 14, 104, 46, 70, 78, 40, 94);
                    p.CloseFigure();
                    Volume(g, p, H("F4F8FF"), H("7A8CB0"), 5);
                }
                using (Pen pn = new Pen(H("FF7A1A"), 4)) g.DrawBezier(pn, 46, 84, 64, 64, 84, 42, 104, 24);
                using (GraphicsPath a = Arrondi(26, 82, 26, 18, 6)) Volume(g, a, OR_C, OR_F, 3);
                Eclat(g, 104, 46, 8, Color.White);
            });
            Icone("comete", false, delegate (Graphics g)
            {
                using (GraphicsPath q = new GraphicsPath())
                {
                    q.AddPolygon(new PointF[] { new PointF(10, 118), new PointF(64, 56), new PointF(80, 72) });
                    using (LinearGradientBrush b = new LinearGradientBrush(new PointF(10, 118), new PointF(72, 64), Color.FromArgb(0, 255, 224, 138), H("FFE08A"))) g.FillPath(b, q);
                }
                using (Pen pn = new Pen(Color.FromArgb(160, 98, 207, 244), 5)) g.DrawLine(pn, 24, 120, 70, 70);
                Halo(g, 82, 46, 40, Color.FromArgb(200, 255, 224, 138));
                using (GraphicsPath e = Etoile(82, 46, 30, 12, 5, 0.2f)) Volume(g, e, H("FFFFFF"), OR, 4);
                Eclat(g, 110, 18, 8, Color.White);
                Eclat(g, 40, 30, 5, Color.White);
            });
            Icone("teleporteur", false, delegate (Graphics g)
            {
                Halo(g, 64, 64, 58, Color.FromArgb(170, 199, 125, 255));
                using (GraphicsPath p = new GraphicsPath())
                {
                    p.AddPolygon(new PointF[] { new PointF(64, 8), new PointF(100, 44), new PointF(64, 120), new PointF(28, 44) });
                    Volume(g, p, H("F0D6FF"), H("5A1E9E"), 5);
                }
                using (Pen pn = new Pen(Color.FromArgb(150, 255, 255, 255), 3)) { g.DrawLine(pn, 28, 44, 100, 44); g.DrawLine(pn, 64, 8, 52, 44); g.DrawLine(pn, 64, 8, 76, 44); g.DrawLine(pn, 52, 44, 64, 120); g.DrawLine(pn, 76, 44, 64, 120); }
                using (Pen pn = new Pen(GLACE, 4)) g.DrawEllipse(pn, 14, 94, 100, 26);
                Eclat(g, 52, 28, 10, Color.White);
            });
            Icone("mur", false, delegate (Graphics g)
            {
                using (GraphicsPath p = Arrondi(14, 18, 100, 92, 10))
                {
                    using (LinearGradientBrush b = new LinearGradientBrush(new Rectangle(14, 18, 100, 92), Color.FromArgb(200, 191, 243, 255), Color.FromArgb(200, 58, 127, 255), 90f)) g.FillPath(b, p);
                    g.SetClip(p);
                    for (int j = 0; j < 6; j++)
                        for (int i = 0; i < 6; i++)
                            using (GraphicsPath h = Hexa(14 + i * 22 + (j % 2) * 11, 18 + j * 19, 11, 11))
                            using (Pen pn = new Pen(Color.FromArgb(220, 255, 255, 255), 2.5f)) g.DrawPath(pn, h);
                    g.ResetClip();
                    using (Pen pn = new Pen(CONTOUR, 5)) g.DrawPath(pn, p);
                }
                using (Pen pn = new Pen(OR, 6)) { g.DrawLine(pn, 14, 112, 114, 112); }
                Eclat(g, 96, 34, 10, Color.White);
            });
            Icone("bombe", false, delegate (Graphics g)
            {
                Halo(g, 60, 70, 56, Color.FromArgb(150, 166, 77, 255));
                using (GraphicsPath p = Cercle(60, 72, 40)) Volume(g, p, H("C79BFF"), H("2A0E5A"), 5);
                using (Pen pn = new Pen(Color.FromArgb(200, 98, 207, 244), 3))
                {
                    g.DrawEllipse(pn, 26, 58, 68, 28);
                    g.DrawEllipse(pn, 46, 38, 28, 68);
                }
                using (GraphicsPath c = Arrondi(76, 22, 22, 18, 4)) Volume(g, c, OR_C, OR_F, 3);
                using (Pen pn = new Pen(H("3A2A1A"), 4)) g.DrawBezier(pn, 90, 22, 96, 10, 106, 14, 110, 8);
                Eclat(g, 112, 8, 11, H("FFE08A"));
                Eclat(g, 44, 54, 8, Color.White);
            });
            Icone("grappin", true, delegate (Graphics g)
            {
                using (Pen pn = new Pen(H("C9A35A"), 5)) g.DrawBezier(pn, 12, 116, 30, 90, 40, 90, 60, 66);
                using (GraphicsPath tige = new GraphicsPath())
                {
                    tige.AddPolygon(new PointF[] { new PointF(56, 70), new PointF(62, 76), new PointF(98, 40), new PointF(92, 34) });
                    Volume(g, tige, H("DDE6F5"), H("4A5A78"), 4);
                }
                using (GraphicsPath c = new GraphicsPath())
                {
                    c.AddBezier(92, 34, 84, 18, 96, 8, 110, 14);
                    c.AddBezier(110, 14, 100, 16, 96, 24, 100, 32);
                    c.CloseFigure();
                    c.AddBezier(98, 40, 114, 48, 124, 36, 118, 22);
                    c.AddBezier(118, 22, 116, 32, 108, 36, 100, 34);
                    c.CloseFigure();
                    Volume(g, c, OR_C, OR_F, 4);
                }
                using (GraphicsPath a = Cercle(59, 73, 7)) Volume(g, a, OR_C, OR_F, 3);
                Eclat(g, 112, 50, 7, Color.White);
            });
            Icone("cryo", true, delegate (Graphics g)
            {
                // Tenu en main, le haut-droit de l'image pointe vers l'avant : le canon va en diagonale vers le haut-droit.
                g.TranslateTransform(64, 64); g.RotateTransform(-40); g.ScaleTransform(0.92f, 0.92f); g.TranslateTransform(-64, -64);
                Halo(g, 100, 30, 30, Color.FromArgb(200, 159, 232, 255));
                using (GraphicsPath corps = Arrondi(26, 40, 70, 26, 8)) Volume(g, corps, H("E8F6FF"), H("3A6A9E"), 5);
                using (GraphicsPath canon = Arrondi(90, 44, 24, 16, 5)) Volume(g, canon, H("BFF3FF"), H("2A8AC8"), 4);
                using (GraphicsPath crosse = new GraphicsPath())
                {
                    crosse.AddPolygon(new PointF[] { new PointF(36, 62), new PointF(58, 62), new PointF(48, 108), new PointF(24, 104) });
                    Volume(g, crosse, H("5A6A88"), H("1A2236"), 5);
                }
                using (GraphicsPath r = Arrondi(42, 46, 36, 10, 4)) Volume(g, r, H("9FE8FF"), H("3A7FFF"), 2);
                for (int i = 0; i < 6; i++)
                {
                    double a = Math.PI / 3 * i;
                    using (Pen pn = new Pen(Color.White, 3)) g.DrawLine(pn, 112, 30, 112 + (float)Math.Cos(a) * 13, 30 + (float)Math.Sin(a) * 13);
                }
                Eclat(g, 50, 30, 7, Color.White);
            });
            Icone("marteau", true, delegate (Graphics g)
            {
                Halo(g, 84, 40, 46, Color.FromArgb(170, 255, 224, 138));
                using (GraphicsPath manche = new GraphicsPath())
                {
                    manche.AddPolygon(new PointF[] { new PointF(12, 108), new PointF(22, 118), new PointF(80, 60), new PointF(70, 50) });
                    Volume(g, manche, H("6A4A2A"), H("1E1208"), 4);
                }
                using (Pen pn = new Pen(OR, 6)) { g.DrawLine(pn, 30, 92, 38, 100); g.DrawLine(pn, 46, 76, 54, 84); }
                using (GraphicsPath tete = new GraphicsPath())
                {
                    tete.AddPolygon(new PointF[] { new PointF(56, 30), new PointF(90, 4), new PointF(124, 38), new PointF(98, 72) });
                    Volume(g, tete, H("FFF1C4"), H("8C6A2A"), 5);
                }
                using (GraphicsPath e = Etoile(90, 38, 16, 6, 4, 0.78f)) Volume(g, e, H("BFF3FF"), BLEU, 3);
                Eclat(g, 112, 12, 9, Color.White);
            });
            // --- œufs mystères
            string[][] oeufs = { new string[] { "oeuf_stellaire", "BFF3FF", "2A8AC8", "62CFF4" }, new string[] { "oeuf_nebuleuse", "E0C2FF", "5A1E9E", "A64DFF" }, new string[] { "oeuf_cosmique", "FFF1C4", "B8862A", "62CFF4" } };
            foreach (string[] oe in oeufs)
            {
                string[] c = oe;
                Icone(c[0], false, delegate (Graphics g)
                {
                    Halo(g, 64, 70, 56, H(c[3], 150));
                    using (GraphicsPath p = new GraphicsPath())
                    {
                        p.AddBezier(64, 10, 98, 10, 112, 70, 104, 92);
                        p.AddBezier(104, 92, 96, 116, 32, 116, 24, 92);
                        p.AddBezier(24, 92, 16, 70, 30, 10, 64, 10);
                        Volume(g, p, H(c[1]), H(c[2]), 5);
                        g.SetClip(p);
                        using (Pen pn = new Pen(Color.FromArgb(200, 255, 255, 255), 4)) g.DrawLines(pn, new PointF[] { new PointF(16, 66), new PointF(34, 56), new PointF(50, 70), new PointF(66, 54), new PointF(82, 70), new PointF(98, 56), new PointF(114, 66) });
                        g.ResetClip();
                    }
                    using (GraphicsPath e = Etoile(64, 86, 13, 5, 4, 0)) Volume(g, e, Color.White, H(c[3]), 2);
                    Eclat(g, 44, 34, 9, Color.White);
                    if (c[0] == "oeuf_cosmique") { Eclat(g, 104, 24, 8, H("FFE08A")); Eclat(g, 20, 40, 6, H("62CFF4")); }
                });
            }
            Icone("m_oeufs", false, delegate (Graphics g)
            {
                Halo(g, 64, 64, 58, Color.FromArgb(140, 224, 184, 100));
                using (GraphicsPath p = new GraphicsPath())
                {
                    p.AddBezier(64, 14, 94, 14, 106, 66, 100, 86);
                    p.AddBezier(100, 86, 92, 110, 36, 110, 28, 86);
                    p.AddBezier(28, 86, 22, 66, 34, 14, 64, 14);
                    Volume(g, p, H("FFF1C4"), H("B8862A"), 5);
                }
                Texte(g, "?", 64, 66, 54, Color.White, CONTOUR, 7);
                Eclat(g, 102, 22, 9, Color.White);
            });
            Icone("fusion", false, delegate (Graphics g)
            {
                Halo(g, 64, 70, 50, Color.FromArgb(170, 255, 122, 26));
                using (GraphicsPath a = Cercle(34, 40, 18)) Volume(g, a, H("BFF3FF"), H("2A8AC8"), 4);
                using (GraphicsPath a = Cercle(94, 40, 18)) Volume(g, a, H("BFF3FF"), H("2A8AC8"), 4);
                using (GraphicsPath a = Cercle(64, 22, 16)) Volume(g, a, H("BFF3FF"), H("2A8AC8"), 4);
                using (Pen pn = new Pen(H("FF7A1A"), 6)) { g.DrawLine(pn, 40, 58, 58, 80); g.DrawLine(pn, 88, 58, 70, 80); g.DrawLine(pn, 64, 40, 64, 76); }
                using (GraphicsPath e = Etoile(64, 94, 26, 10, 5, 0)) Volume(g, e, H("FFE08A"), H("FF4D1A"), 4);
            });
            Icone("m_collection", false, delegate (Graphics g)
            {
                Halo(g, 64, 50, 50, Color.FromArgb(150, 255, 224, 138));
                using (GraphicsPath p = new GraphicsPath())
                {
                    p.AddPolygon(new PointF[] { new PointF(30, 18), new PointF(98, 18), new PointF(92, 60), new PointF(64, 78), new PointF(36, 60) });
                    Volume(g, p, H("FFF1C4"), H("B8862A"), 5);
                }
                using (Pen pn = new Pen(OR_F, 6)) { g.DrawArc(pn, 10, 22, 30, 30, 90, 180); g.DrawArc(pn, 88, 22, 30, 30, 270, 180); }
                using (GraphicsPath s = Arrondi(52, 76, 24, 20, 3)) Volume(g, s, OR, OR_F, 3);
                using (GraphicsPath s = Arrondi(36, 96, 56, 16, 5)) Volume(g, s, H("3A4C6E"), H("141B2B"), 4);
                using (GraphicsPath e = Etoile(64, 42, 15, 6, 5, 0)) Volume(g, e, Color.White, H("62CFF4"), 2);
            });
            Icone("m_perso", false, delegate (Graphics g)
            {
                using (GraphicsPath p = Arrondi(12, 30, 70, 70, 10)) Volume(g, p, H("E8F0FA"), H("8A96A8"), 5);
                using (SolidBrush b = new SolidBrush(H("FF4D6D"))) g.FillEllipse(b, 22, 44, 18, 18);
                using (SolidBrush b = new SolidBrush(H("62CFF4"))) g.FillEllipse(b, 48, 40, 18, 18);
                using (SolidBrush b = new SolidBrush(H("7CFC9A"))) g.FillEllipse(b, 26, 70, 18, 18);
                using (SolidBrush b = new SolidBrush(H("FFE08A"))) g.FillEllipse(b, 52, 68, 16, 16);
                using (GraphicsPath m = new GraphicsPath())
                {
                    m.AddPolygon(new PointF[] { new PointF(70, 96), new PointF(78, 104), new PointF(120, 30), new PointF(112, 24) });
                    Volume(g, m, H("8A5A2A"), H("2A1A0A"), 4);
                }
                using (GraphicsPath t = new GraphicsPath())
                {
                    t.AddPolygon(new PointF[] { new PointF(62, 100), new PointF(72, 92), new PointF(82, 104), new PointF(66, 118) });
                    Volume(g, t, H("A66BFF"), H("3A0E7A"), 3);
                }
                Eclat(g, 100, 16, 8, Color.White);
            });
            Icone("m_echange", false, delegate (Graphics g)
            {
                Halo(g, 64, 64, 56, Color.FromArgb(120, 124, 252, 154));
                using (GraphicsPath a = new GraphicsPath())
                {
                    a.AddPolygon(new PointF[] { new PointF(14, 44), new PointF(84, 44), new PointF(84, 28), new PointF(114, 54), new PointF(84, 80), new PointF(84, 64), new PointF(14, 64) });
                    Volume(g, a, H("C8FFD8"), H("1E8A4A"), 5);
                }
                using (GraphicsPath a = new GraphicsPath())
                {
                    a.AddPolygon(new PointF[] { new PointF(114, 84), new PointF(44, 84), new PointF(44, 68), new PointF(14, 94), new PointF(44, 120), new PointF(44, 104), new PointF(114, 104) });
                    Volume(g, a, H("FFF1C4"), H("B8862A"), 5);
                }
                Eclat(g, 100, 22, 8, Color.White);
            });
            Icone("m_musique", false, delegate (Graphics g)
            {
                Halo(g, 64, 64, 56, Color.FromArgb(130, 166, 77, 255));
                using (GraphicsPath p = new GraphicsPath())
                {
                    p.AddPolygon(new PointF[] { new PointF(46, 30), new PointF(104, 16), new PointF(104, 30), new PointF(54, 42) });
                    Volume(g, p, H("E0C2FF"), H("5A1E9E"), 4);
                }
                using (Pen pn = new Pen(CONTOUR, 9)) { g.DrawLine(pn, 50, 34, 50, 92); g.DrawLine(pn, 100, 22, 100, 80); }
                using (Pen pn = new Pen(H("C79BFF"), 5)) { g.DrawLine(pn, 50, 34, 50, 92); g.DrawLine(pn, 100, 22, 100, 80); }
                using (GraphicsPath a = Cercle(38, 94, 15)) Volume(g, a, H("E0C2FF"), H("5A1E9E"), 4);
                using (GraphicsPath a = Cercle(88, 82, 15)) Volume(g, a, H("E0C2FF"), H("5A1E9E"), 4);
                Eclat(g, 22, 30, 8, Color.White);
            });
            Icone("menu", false, delegate (Graphics g)
            {
                Halo(g, 64, 64, 60, Color.FromArgb(140, 98, 207, 244));
                using (Pen pn = new Pen(GLACE, 6)) g.DrawEllipse(pn, 10, 44, 108, 40);
                using (GraphicsPath e = Etoile(64, 62, 44, 19, 5, 0)) Volume(g, e, OR_C, H("B8862A"), 5);
                using (Pen pn = new Pen(GLACE, 6)) g.DrawArc(pn, 10, 44, 108, 40, 0, 180);
                Eclat(g, 104, 22, 10, Color.White);
            });

            // --- icônes de menu
            Icone("m_objets", false, delegate (Graphics g)
            {
                using (GraphicsPath p = Arrondi(18, 46, 92, 64, 12)) Volume(g, p, H("FF6A6A"), H("8A1424"), 5);
                using (Pen pn = new Pen(CONTOUR, 10)) g.DrawArc(pn, 42, 22, 44, 44, 180, 180);
                using (Pen pn = new Pen(OR, 6)) g.DrawArc(pn, 42, 22, 44, 44, 180, 180);
                using (GraphicsPath f = Arrondi(54, 66, 20, 18, 4)) Volume(g, f, OR_C, OR_F, 3);
            });
            Icone("m_base", false, delegate (Graphics g)
            {
                using (GraphicsPath sol = Arrondi(8, 88, 112, 22, 8)) Volume(g, sol, H("6B7A90"), H("232C3A"), 5);
                using (GraphicsPath d = new GraphicsPath()) { d.AddArc(20, 30, 88, 116, 180, 180); d.CloseFigure(); Volume(g, d, Color.FromArgb(230, 156, 232, 255), Color.FromArgb(230, 42, 120, 200), 5); }
                using (Pen pn = new Pen(Color.FromArgb(150, 255, 255, 255), 3)) { g.DrawLine(pn, 64, 32, 64, 88); g.DrawArc(pn, 34, 44, 60, 90, 180, 180); }
                Eclat(g, 44, 50, 9, Color.White);
            });
            Icone("m_quetes", false, delegate (Graphics g)
            {
                using (GraphicsPath p = Arrondi(24, 14, 80, 100, 10)) Volume(g, p, H("FFF2D0"), H("C8A060"), 5);
                using (Pen pn = new Pen(H("8A6A3A"), 5)) { g.DrawLine(pn, 40, 40, 88, 40); g.DrawLine(pn, 40, 58, 80, 58); }
                using (GraphicsPath c = Cercle(80, 88, 24)) Volume(g, c, H("8AFF9A"), H("1A8A3A"), 4);
                using (Pen pn = new Pen(Color.White, 8) { StartCap = LineCap.Round, EndCap = LineCap.Round, LineJoin = LineJoin.Round })
                    g.DrawLines(pn, new PointF[] { new PointF(68, 88), new PointF(77, 97), new PointF(93, 78) });
            });
            Icone("m_orbidex", false, delegate (Graphics g)
            {
                using (GraphicsPath p = Arrondi(22, 12, 84, 104, 10)) Volume(g, p, H("B07AFF"), H("3A1480"), 5);
                using (GraphicsPath dos = Arrondi(22, 12, 16, 104, 6)) Volume(g, dos, H("6A3AC0"), H("200850"), 3);
                using (GraphicsPath e = Etoile(70, 62, 26, 11, 5, 0)) Volume(g, e, OR_C, OR, 4);
                Eclat(g, 96, 26, 8, Color.White);
            });
            Icone("m_classement", false, delegate (Graphics g)
            {
                using (GraphicsPath p = new GraphicsPath())
                {
                    p.AddBezier(28, 18, 28, 70, 50, 80, 64, 80);
                    p.AddBezier(64, 80, 78, 80, 100, 70, 100, 18);
                    p.CloseFigure();
                    Volume(g, p, OR_C, H("B8862A"), 5);
                }
                using (Pen pn = new Pen(CONTOUR, 10)) { g.DrawArc(pn, 8, 26, 30, 30, 90, 180); g.DrawArc(pn, 90, 26, 30, 30, 270, 180); }
                using (Pen pn = new Pen(OR, 5)) { g.DrawArc(pn, 8, 26, 30, 30, 90, 180); g.DrawArc(pn, 90, 26, 30, 30, 270, 180); }
                using (GraphicsPath pied = Arrondi(54, 78, 20, 18, 3)) Volume(g, pied, OR, OR_F, 3);
                using (GraphicsPath socle = Arrondi(34, 94, 60, 20, 6)) Volume(g, socle, H("3A4454"), H("10141C"), 4);
                Eclat(g, 46, 36, 9, Color.White);
            });
            Icone("m_ascension", false, delegate (Graphics g)
            {
                Halo(g, 64, 64, 60, Color.FromArgb(150, 224, 184, 100));
                using (GraphicsPath p = new GraphicsPath())
                {
                    p.AddPolygon(new PointF[] { new PointF(64, 8), new PointF(108, 56), new PointF(80, 56), new PointF(80, 116), new PointF(48, 116), new PointF(48, 56), new PointF(20, 56) });
                    Volume(g, p, OR_C, H("B8862A"), 5);
                }
                Eclat(g, 26, 92, 9, Color.White);
                Eclat(g, 104, 86, 7, GLACE);
            });
            Icone("m_astuces", false, delegate (Graphics g)
            {
                Halo(g, 64, 50, 54, Color.FromArgb(140, 255, 230, 120));
                using (GraphicsPath p = Cercle(64, 50, 34)) Volume(g, p, H("FFF6B0"), H("E0A020"), 5);
                using (GraphicsPath c = Arrondi(46, 80, 36, 30, 6)) Volume(g, c, H("A8B4C8"), H("4A5468"), 4);
                using (Pen pn = new Pen(H("4A5468"), 3)) { g.DrawLine(pn, 48, 92, 80, 92); g.DrawLine(pn, 48, 100, 80, 100); }
            });
            Icone("m_retour", false, delegate (Graphics g)
            {
                using (GraphicsPath p = new GraphicsPath())
                {
                    p.AddPolygon(new PointF[] { new PointF(12, 64), new PointF(56, 22), new PointF(56, 46), new PointF(116, 46), new PointF(116, 82), new PointF(56, 82), new PointF(56, 106) });
                    Volume(g, p, H("BFE8FF"), H("2A6AC8"), 5);
                }
            });
            Icone("ameliorer", false, delegate (Graphics g)
            {
                Halo(g, 64, 64, 60, Color.FromArgb(120, 124, 252, 154));
                using (GraphicsPath c = Cercle(64, 64, 50)) Volume(g, c, H("8AFF9A"), H("12803A"), 5);
                using (GraphicsPath p = new GraphicsPath())
                {
                    p.AddPolygon(new PointF[] { new PointF(64, 24), new PointF(96, 60), new PointF(76, 60), new PointF(76, 100), new PointF(52, 100), new PointF(52, 60), new PointF(32, 60) });
                    Volume(g, p, Color.White, H("D8FFE0"), 3);
                }
            });
            Icone("revendre", false, delegate (Graphics g)
            {
                for (int i = 3; i >= 0; i--)
                    using (GraphicsPath p = new GraphicsPath())
                    {
                        p.AddEllipse(24, 70 - i * 14, 80, 30);
                        Volume(g, p, OR_C, H("B8862A"), 4);
                    }
                Texte(g, "✦", 64, 30, 26, OR_F, Color.FromArgb(0, 0, 0, 0), 1);
                Eclat(g, 100, 20, 9, Color.White);
            });
            Icone("oui", false, delegate (Graphics g)
            {
                using (GraphicsPath c = Cercle(64, 64, 50)) Volume(g, c, H("8AFF9A"), H("12803A"), 5);
                using (Pen pn = new Pen(Color.White, 16) { StartCap = LineCap.Round, EndCap = LineCap.Round, LineJoin = LineJoin.Round })
                    g.DrawLines(pn, new PointF[] { new PointF(38, 66), new PointF(56, 84), new PointF(92, 44) });
            });
            Icone("non", false, delegate (Graphics g)
            {
                using (GraphicsPath c = Cercle(64, 64, 50)) Volume(g, c, H("FF8A8A"), H("A01424"), 5);
                using (Pen pn = new Pen(Color.White, 16) { StartCap = LineCap.Round, EndCap = LineCap.Round })
                { g.DrawLine(pn, 42, 42, 86, 86); g.DrawLine(pn, 86, 42, 42, 86); }
            });
            Icone("max", false, delegate (Graphics g)
            {
                Halo(g, 64, 64, 62, Color.FromArgb(150, 255, 224, 138));
                using (GraphicsPath p = new GraphicsPath())
                {
                    p.AddPolygon(new PointF[] { new PointF(14, 40), new PointF(40, 66), new PointF(64, 22), new PointF(88, 66), new PointF(114, 40), new PointF(104, 104), new PointF(24, 104) });
                    Volume(g, p, OR_C, H("B8862A"), 5);
                }
                using (GraphicsPath c = Cercle(64, 84, 9)) Volume(g, c, H("FF6A8A"), H("A01440"), 3);
                Eclat(g, 30, 22, 8, Color.White);
            });

            // --- améliorations de base
            Icone("a_places", false, delegate (Graphics g)
            {
                Halo(g, 64, 40, 50, Color.FromArgb(160, 98, 207, 244));
                using (GraphicsPath p = Arrondi(22, 62, 84, 48, 8)) Volume(g, p, H("8A96A8"), H("2A3446"), 5);
                using (GraphicsPath t = Arrondi(14, 52, 100, 18, 6)) Volume(g, t, H("C8F4FF"), H("3AA0D8"), 4);
                Texte(g, "+2", 64, 88, 30, Color.White, CONTOUR, 6);
            });
            Icone("a_bouclier", false, delegate (Graphics g)
            {
                using (GraphicsPath p = new GraphicsPath())
                {
                    p.AddBezier(64, 10, 44, 22, 26, 24, 16, 24);
                    p.AddBezier(16, 24, 14, 74, 34, 104, 64, 120);
                    p.AddBezier(64, 120, 94, 104, 114, 74, 112, 24);
                    p.AddBezier(112, 24, 102, 24, 84, 22, 64, 10);
                    p.CloseFigure();
                    using (Pen pn = new Pen(OR, 16)) g.DrawPath(pn, p);
                    Volume(g, p, H("BFF4FF"), H("1A6AB8"), 4);
                }
                Eclat(g, 52, 46, 14, Color.White);
            });
            Icone("a_tourelle", false, delegate (Graphics g)
            {
                using (GraphicsPath s = Arrondi(26, 84, 76, 30, 8)) Volume(g, s, H("6B7A90"), H("1C2433"), 5);
                using (GraphicsPath t = Cercle(64, 72, 28)) Volume(g, t, H("A8B4C8"), H("3A4454"), 5);
                using (GraphicsPath c = Arrondi(64, 36, 54, 16, 6)) Volume(g, c, H("D0D8E4"), H("4A5468"), 4);
                using (Pen pn = new Pen(H("FF4D6D"), 5)) g.DrawLine(pn, 116, 44, 126, 44);
                Halo(g, 120, 44, 12, Color.FromArgb(220, 255, 77, 109));
                using (GraphicsPath o = Cercle(64, 72, 9)) Volume(g, o, H("FF8A9A"), H("C0143A"), 3);
            });
            Icone("a_alarme", false, delegate (Graphics g)
            {
                using (Pen pn = new Pen(H("FFD447"), 6) { StartCap = LineCap.Round, EndCap = LineCap.Round })
                { g.DrawArc(pn, 6, 24, 40, 56, 120, 120); g.DrawArc(pn, 82, 24, 40, 56, -60, 120); }
                using (GraphicsPath p = new GraphicsPath())
                {
                    p.AddBezier(64, 14, 36, 14, 34, 50, 32, 84);
                    p.AddLine(32, 84, 22, 98);
                    p.AddLine(22, 98, 106, 98);
                    p.AddLine(106, 98, 96, 84);
                    p.AddBezier(96, 84, 94, 50, 92, 14, 64, 14);
                    p.CloseFigure();
                    Volume(g, p, OR_C, H("B8862A"), 5);
                }
                using (GraphicsPath c = Cercle(64, 106, 10)) Volume(g, c, OR, OR_F, 3);
            });
            Icone("a_generateur", false, delegate (Graphics g)
            {
                Halo(g, 64, 64, 62, Color.FromArgb(150, 124, 252, 154));
                using (GraphicsPath c = Cercle(64, 64, 46)) Volume(g, c, H("2A3446"), H("0B1220"), 5);
                using (Pen pn = new Pen(H("7CFC9A"), 5)) g.DrawEllipse(pn, 26, 26, 76, 76);
                using (GraphicsPath p = new GraphicsPath())
                {
                    p.AddPolygon(new PointF[] { new PointF(70, 22), new PointF(40, 70), new PointF(62, 70), new PointF(54, 106), new PointF(90, 54), new PointF(68, 54) });
                    Volume(g, p, H("EFFFB0"), H("7CFC9A"), 3);
                }
            });
            Icone("a_banque", false, delegate (Graphics g)
            {
                using (GraphicsPath p = Arrondi(12, 16, 104, 96, 14)) Volume(g, p, H("A8B4C8"), H("3A4454"), 5);
                using (GraphicsPath c = Cercle(64, 64, 34)) Volume(g, c, OR_C, H("B8862A"), 4);
                using (Pen pn = new Pen(OR_F, 5))
                    for (int i = 0; i < 6; i++)
                    {
                        double a = i * Math.PI / 3;
                        g.DrawLine(pn, 64, 64, 64 + (float)(Math.Cos(a) * 26), 64 + (float)(Math.Sin(a) * 26));
                    }
                using (GraphicsPath c = Cercle(64, 64, 9)) Volume(g, c, H("3A4454"), H("10141C"), 3);
            });
            Icone("inconnu", false, delegate (Graphics g)
            {
                using (GraphicsPath p = Arrondi(14, 10, 100, 108, 18)) Volume(g, p, H("4A5468"), H("1C2433"), 5);
                Texte(g, "?", 64, 66, 64, H("8A96A8"), CONTOUR, 6);
            });

            // --- cartes des Orbitos (Orbidex, fiches)
            int i2 = 0;
            foreach (Bete b in Betes())
            {
                string id = IdDe(b.Nom);
                int rar = RareteDe(i2++);
                Bete bb = b;
                int graine = i2;
                Icone("o_" + id, false, delegate (Graphics g) { Carte(g, bb, rar, graine); });
            }
            return nbObjets;
        }

        static readonly string[] IDS = {
            "dodo_turbo", "dodo_roi", "lapin_satellite", "grenouille_wifi", "grenouille_supernova", "tatou_patate", "cochon_fusee",
            "mouton_nebuleuse", "axolotl_astronaute", "perroquet_radar", "renard_orbital", "tortue_hyperdrive", "chat_quantique",
            "bonhomme_cryo", "abeille_reacteur", "loup_comete", "vache_lunaire", "panda_gravite", "fee_antenne", "ours_polaris",
            "chameau_stellaire", "renifleur_etoiles", "titan_boulon", "meduse_wifi", "rex_rikiki", "gardien_neant", "astraeos_poche", "grand_bidule" };
        // Rareté de chaque carte, dans l'ordre de Betes() : 0 commun … 6 secret.
        static readonly int[] RARETES = { 0, 4, 0, 0, 4, 0, 0, 0, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 3, 3, 3, 3, 4, 5, 5, 5, 6, 6 };
        static readonly string[][] COULEURS_RARETE = {
            new string[] { "B8C2CC", "4A5468" }, new string[] { "6AA8FF", "1A3A9A" }, new string[] { "C88AFF", "4A1A8A" },
            new string[] { "FFE08A", "9A6A1A" }, new string[] { "FF8AA0", "8A1430" }, new string[] { "9CF0FF", "1A6A9A" }, new string[] { "FFFFFF", "C02AA0" } };

        static string IdDe(string nom)
        {
            List<Bete> l = Betes();
            for (int i = 0; i < l.Count; i++) if (l[i].Nom == nom) return IDS[i];
            return "inconnu";
        }
        static int RareteDe(int i) { return RARETES[i]; }

        /** Carte d'Orbidex : cadre de rareté, fond étoilé, petite bestiole ronde aux couleurs de l'Orbito. */
        static void Carte(Graphics g, Bete b, int rar, int graine)
        {
            string[] cr = COULEURS_RARETE[rar];
            using (GraphicsPath cadre = Arrondi(8, 4, 112, 120, 16))
            {
                Volume(g, cadre, H(cr[0]), H(cr[1]), 4);
                if (rar == 6)
                {
                    using (LinearGradientBrush lg = new LinearGradientBrush(new RectangleF(8, 4, 112, 120), Color.Red, Color.Blue, 45f))
                    {
                        ColorBlend cb = new ColorBlend();
                        cb.Colors = new Color[] { H("FF4D6D"), H("E0B864"), H("7CFC9A"), H("62CFF4"), H("A64DFF") };
                        cb.Positions = new float[] { 0, 0.25f, 0.5f, 0.75f, 1 };
                        lg.InterpolationColors = cb;
                        g.FillPath(lg, cadre);
                    }
                }
            }
            using (GraphicsPath fond = Arrondi(16, 12, 96, 104, 10))
            {
                using (LinearGradientBrush lg = new LinearGradientBrush(new RectangleF(16, 12, 96, 104), NUIT_C, NUIT, 90f)) g.FillPath(lg, fond);
                for (int k = 0; k < 14; k++)
                {
                    float x = 20 + (float)(Hash(k, graine, 3) * 88), y = 16 + (float)(Hash(graine, k, 5) * 96);
                    using (SolidBrush sb = new SolidBrush(Color.FromArgb(160, 255, 255, 255))) g.FillEllipse(sb, x, y, 2.5f, 2.5f);
                }
            }
            Color corps = Ramp(b.Rampe, 0.45), clair = Ramp(b.Rampe, 0.85), fonce = Ramp(b.Rampe, 0.15);
            Halo(g, 64, 70, 44, Color.FromArgb(110, b.Accent));
            int forme = (int)(Hash(graine, 1, 9) * 3);
            // oreilles / antennes
            if (forme == 0)
            {
                using (GraphicsPath o = Etoile(42, 38, 14, 6, 3, 0)) Volume(g, o, clair, corps, 3);
                using (GraphicsPath o = Etoile(86, 38, 14, 6, 3, 0)) Volume(g, o, clair, corps, 3);
            }
            else if (forme == 1)
            {
                using (Pen pn = new Pen(CONTOUR, 7)) { g.DrawLine(pn, 50, 46, 40, 24); g.DrawLine(pn, 78, 46, 88, 24); }
                using (Pen pn = new Pen(fonce, 3)) { g.DrawLine(pn, 50, 46, 40, 24); g.DrawLine(pn, 78, 46, 88, 24); }
                using (GraphicsPath a = Cercle(40, 22, 7)) Volume(g, a, b.Accent, corps, 3);
                using (GraphicsPath a = Cercle(88, 22, 7)) Volume(g, a, b.Accent, corps, 3);
            }
            else
            {
                using (GraphicsPath o = Cercle(40, 44, 12)) Volume(g, o, clair, corps, 3);
                using (GraphicsPath o = Cercle(88, 44, 12)) Volume(g, o, clair, corps, 3);
            }
            using (GraphicsPath c = new GraphicsPath())
            {
                c.AddEllipse(26, 38, 76, 72);
                Volume(g, c, clair, fonce, 4);
            }
            using (GraphicsPath ventre = new GraphicsPath())
            {
                ventre.AddEllipse(44, 72, 40, 32);
                using (SolidBrush sb = new SolidBrush(Color.FromArgb(120, 255, 255, 255))) g.FillPath(sb, ventre);
            }
            foreach (int ex in new int[] { 50, 78 })
            {
                using (GraphicsPath oe = Cercle(ex, 66, 10)) Volume(g, oe, Color.White, H("D8E0EA"), 2);
                using (SolidBrush sb = new SolidBrush(CONTOUR)) g.FillEllipse(sb, ex - 4, 64, 9, 10);
                using (SolidBrush sb = new SolidBrush(Color.White)) g.FillEllipse(sb, ex - 2, 65, 3.5f, 3.5f);
            }
            using (Pen pn = new Pen(CONTOUR, 3) { StartCap = LineCap.Round, EndCap = LineCap.Round }) g.DrawArc(pn, 56, 78, 16, 10, 20, 140);
            using (SolidBrush sb = new SolidBrush(Color.FromArgb(120, 255, 120, 160))) { g.FillEllipse(sb, 34, 76, 12, 7); g.FillEllipse(sb, 82, 76, 12, 7); }
            Eclat(g, 102, 22, 7, Color.White);
        }

        // ================================================================== HUD

        static int nbHud = 0;
        const int S = 4;                    // pixels d'image par pixel d'interface
        static StringBuilder police = new StringBuilder();

        static Bitmap Toile(int w, int h, out Graphics g)
        {
            Bitmap b = new Bitmap(w * S, h * S, PixelFormat.Format32bppArgb);
            g = Graphics.FromImage(b);
            Lisse(g);
            g.ScaleTransform(S, S);
            return b;
        }

        static void Forcer(Bitmap b)
        {
            int x = b.Width - 1;
            for (int y = 0; y < b.Height; y++) if (b.GetPixel(x, y).A > 0) return;
            b.SetPixel(x, 0, Color.FromArgb(1, 0, 0, 0));
        }

        static int prochaineTuile = 0xE400;

        static void Glyphe(string fichier, Bitmap b, string caractere, int ascent, int hauteur, string police_)
        {
            Forcer(b);
            double sc = hauteur / (double)b.Height;
            glyphes.Append(police_ + " " + ((int)caractere[0]).ToString("X4") + " " + ((int)(0.5 + b.Width * sc) + 1) + "\n");
            // Le client refuse les glyphes de plus de 256 px : on découpe en bandes posées bout à bout.
            if (b.Width <= 256)
            {
                Sauver(b, "assets/orbitvol/textures/font/" + fichier + ".png");
                nbHud++;
                AjouterPolice(police_, "{\"type\": \"bitmap\", \"file\": \"orbitvol:font/" + fichier + ".png\", \"ascent\": " + ascent + ", \"height\": " + hauteur + ", \"chars\": [\"" + Echap(caractere) + "\"]}");
                return;
            }
            int s = Math.Max(1, b.Height / hauteur);
            int n = (b.Width + 255) / 256;
            int sw = (b.Width + n - 1) / n;
            sw = (sw + s - 1) / s * s;
            if (sw > 256) sw = 256 / s * s;
            StringBuilder seq = new StringBuilder();
            int k = 0;
            for (int x0 = 0; x0 < b.Width; x0 += sw, k++)
            {
                int w = Math.Min(sw, b.Width - x0);
                Bitmap t = b.Clone(new Rectangle(x0, 0, w, b.Height), PixelFormat.Format32bppArgb);
                Forcer(t);
                string nomT = fichier + "_t" + k;
                Sauver(t, "assets/orbitvol/textures/font/" + nomT + ".png");
                nbHud++;
                char c = (char)prochaineTuile++;
                AjouterPolice(police_, "{\"type\": \"bitmap\", \"file\": \"orbitvol:font/" + nomT + ".png\", \"ascent\": " + ascent + ", \"height\": " + hauteur + ", \"chars\": [\"" + Echap(c.ToString()) + "\"]}");
                if (seq.Length > 0) seq.Append(",F801,");
                seq.Append(((int)c).ToString("X4"));
                t.Dispose();
            }
            glyphes.Append("tuile " + police_ + " " + ((int)caractere[0]).ToString("X4") + " " + seq + "\n");
        }

        static Dictionary<string, List<string>> polices_ = new Dictionary<string, List<string>>();

        static void AjouterPolice(string nom, string fournisseur)
        {
            if (!polices_.ContainsKey(nom)) polices_[nom] = new List<string>();
            polices_[nom].Add(fournisseur);
        }

        static string Echap(string s)
        {
            StringBuilder sb = new StringBuilder();
            foreach (char c in s) sb.Append(c < 128 && c != '"' && c != '\\' ? c.ToString() : "\\u" + ((int)c).ToString("X4"));
            return sb.ToString();
        }

        static void EcrirePolices()
        {
            string espaces = "{\"type\": \"space\", \"advances\": {\"\\uF801\": -1, \"\\uF802\": -2, \"\\uF803\": -4, \"\\uF804\": -8, \"\\uF805\": -16, \"\\uF806\": -32, \"\\uF807\": -64, \"\\uF808\": -128, \"\\uF821\": 1, \"\\uF822\": 2, \"\\uF823\": 4, \"\\uF824\": 8, \"\\uF825\": 16, \"\\uF826\": 32, \"\\uF827\": 64, \"\\uF828\": 128}}";
            foreach (KeyValuePair<string, List<string>> kv in polices_)
            {
                StringBuilder sb = new StringBuilder("{\n  \"providers\": [\n    " + espaces);
                foreach (string f in kv.Value) sb.Append(",\n    " + f);
                sb.Append("\n  ]\n}\n");
                Ecrire("assets/orbitvol/font/" + kv.Key + ".json", sb.ToString());
            }
        }

        static void Apercu(string nom, Bitmap b) { apercuHud.Add(new KeyValuePair<string, Bitmap>(nom, b)); }

        // ================================================================== DÉCOR 3D DE L'ARÈNE

        static int nbDecor = 0;

        static Bitmap Tex(int w, int h, out Graphics g)
        {
            Bitmap b = new Bitmap(w, h, PixelFormat.Format32bppArgb);
            g = Graphics.FromImage(b);
            Lisse(g);
            return b;
        }

        static void SauverTex(string id, Bitmap b)
        {
            Sauver(b, "assets/orbitvol/textures/item/" + id + ".png");
            nbDecor++;
        }

        /** Élément cubique : toutes les faces avec la texture t (ou la face « face » avec la texture tf). */
        static string Boite(double[] de, double[] a, string t, string face, string tf, string rotation)
        {
            string[] noms = { "north", "south", "east", "west", "up", "down" };
            StringBuilder sb = new StringBuilder();
            sb.Append("    {\"from\": [" + N(de[0]) + ", " + N(de[1]) + ", " + N(de[2]) + "], \"to\": [" + N(a[0]) + ", " + N(a[1]) + ", " + N(a[2]) + "], ");
            if (rotation != null) sb.Append("\"rotation\": " + rotation + ", ");
            sb.Append("\"faces\": {");
            for (int i = 0; i < noms.Length; i++)
            {
                string tx = noms[i] == face ? tf : t;
                sb.Append((i > 0 ? ", " : "") + "\"" + noms[i] + "\": {\"uv\": [0, 0, 16, 16], \"texture\": \"#" + tx + "\"}");
            }
            sb.Append("}}");
            return sb.ToString();
        }

        static string N(double v) { return v.ToString("0.###", System.Globalization.CultureInfo.InvariantCulture); }
        static double[] V(double x, double y, double z) { return new double[] { x, y, z }; }

        static void Modele3D(string id, string[] textures, List<string> elements)
        {
            StringBuilder sb = new StringBuilder();
            sb.Append("{\n  \"textures\": {\n    \"particle\": \"orbitvol:item/" + textures[0] + "\"");
            foreach (string t in textures) sb.Append(",\n    \"" + t + "\": \"orbitvol:item/" + t + "\"");
            sb.Append("\n  },\n  \"elements\": [\n" + string.Join(",\n", elements.ToArray()) + "\n  ],\n");
            sb.Append("  \"display\": {\"gui\": {\"rotation\": [30, 225, 0], \"scale\": [0.6, 0.6, 0.6]}}\n}\n");
            Ecrire("assets/orbitvol/models/item/" + id + ".json", sb.ToString());
            Ecrire("assets/orbitvol/items/" + id + ".json", "{\n  \"model\": { \"type\": \"minecraft:model\", \"model\": \"orbitvol:item/" + id + "\" }\n}\n");
        }

        static void Biseau(Graphics g, RectangleF r, Color clair, Color sombre, float e)
        {
            using (Pen p = new Pen(clair, e)) { g.DrawLine(p, r.Left, r.Top, r.Right, r.Top); g.DrawLine(p, r.Left, r.Top, r.Left, r.Bottom); }
            using (Pen p = new Pen(sombre, e)) { g.DrawLine(p, r.Left, r.Bottom, r.Right, r.Bottom); g.DrawLine(p, r.Right, r.Top, r.Right, r.Bottom); }
        }

        static void Rivet(Graphics g, float x, float y, float r)
        {
            using (SolidBrush b = new SolidBrush(H("0A0F1A"))) g.FillEllipse(b, x - r, y - r, 2 * r, 2 * r);
            using (SolidBrush b = new SolidBrush(H("8FA3C0"))) g.FillEllipse(b, x - r * 0.7f, y - r * 0.8f, r * 1.3f, r * 1.3f);
        }

        static GraphicsPath Hexa(float cx, float cy, float rx, float ry)
        {
            GraphicsPath p = new GraphicsPath();
            PointF[] pts = new PointF[6];
            for (int i = 0; i < 6; i++)
            {
                double a = Math.PI / 3 * i + Math.PI / 6;
                pts[i] = new PointF(cx + (float)(Math.Cos(a) * rx), cy + (float)(Math.Sin(a) * ry));
            }
            p.AddPolygon(pts);
            return p;
        }

        static int Decor()
        {
            Graphics g;
            // --- métal sombre à circuits
            Bitmap m = Tex(128, 128, out g);
            using (LinearGradientBrush lb = new LinearGradientBrush(new Rectangle(0, 0, 128, 128), H("2A3550"), H("141B2B"), 90f)) g.FillRectangle(lb, 0, 0, 128, 128);
            for (int i = 0; i < 4; i++)
            {
                RectangleF r = new RectangleF(6 + (i % 2) * 60, 6 + (i / 2) * 60, 56, 56);
                using (SolidBrush b = new SolidBrush(H("1C2438"))) g.FillRectangle(b, r);
                Biseau(g, r, H("3D4C6E"), H("0A0F1A"), 2);
            }
            using (Pen p = new Pen(H("62CFF4", 200), 2))
            {
                g.DrawLines(p, new PointF[] { new PointF(14, 34), new PointF(30, 34), new PointF(38, 26), new PointF(54, 26) });
                g.DrawLines(p, new PointF[] { new PointF(74, 98), new PointF(90, 98), new PointF(98, 106), new PointF(114, 106) });
                g.DrawLines(p, new PointF[] { new PointF(80, 14), new PointF(80, 30), new PointF(104, 30) });
                g.DrawLines(p, new PointF[] { new PointF(20, 80), new PointF(20, 110), new PointF(44, 110) });
            }
            using (SolidBrush b = new SolidBrush(H("BFF3FF"))) { g.FillEllipse(b, 52, 23, 6, 6); g.FillEllipse(b, 112, 103, 6, 6); g.FillEllipse(b, 102, 27, 6, 6); g.FillEllipse(b, 42, 107, 6, 6); }
            Rivet(g, 4, 4, 3); Rivet(g, 124, 4, 3); Rivet(g, 4, 124, 3); Rivet(g, 124, 124, 3); Rivet(g, 64, 64, 3);
            using (Pen p = new Pen(CONTOUR, 3)) g.DrawRectangle(p, 1, 1, 126, 126);
            g.Dispose(); SauverTex("d_metal", m);

            // --- or brossé
            Bitmap o = Tex(64, 64, out g);
            using (LinearGradientBrush lb = new LinearGradientBrush(new Rectangle(0, 0, 64, 64), OR_C, OR_F, 70f)) g.FillRectangle(lb, 0, 0, 64, 64);
            for (int y = 2; y < 64; y += 3)
                using (Pen p = new Pen(Color.FromArgb(28 + (int)(Hash(1, y, 7) * 30), 255, 240, 200), 1)) g.DrawLine(p, 0, y, 64, y);
            Biseau(g, new RectangleF(2, 2, 60, 60), H("FFF1C4"), H("5C4515"), 3);
            Eclat(g, 48, 14, 6, Color.FromArgb(230, 255, 255, 255));
            g.Dispose(); SauverTex("d_or", o);

            // --- lentille / lueur cyan
            Bitmap l = Tex(64, 64, out g);
            using (SolidBrush b = new SolidBrush(H("0E2A3A"))) g.FillRectangle(b, 0, 0, 64, 64);
            Halo(g, 32, 32, 30, H("62CFF4"));
            Halo(g, 32, 32, 14, BLANC);
            Eclat(g, 32, 32, 12, Color.White);
            g.Dispose(); SauverTex("d_lentille", l);

            // --- rayon laser (cœur blanc, bord rouge)
            Bitmap la = Tex(32, 32, out g);
            for (int x = 0; x < 32; x++)
            {
                double d = Math.Abs(x - 15.5) / 15.5;
                Color c = d < 0.25 ? Mix(Color.White, H("FFB3C1"), d / 0.25) : Mix(H("FF4D6D"), H("B0103A"), (d - 0.25) / 0.75);
                using (Pen p = new Pen(c, 1)) g.DrawLine(p, x, 0, x, 32);
            }
            g.Dispose(); SauverTex("d_laser", la);

            // --- bouclier hexagonal animé (la face est étirée en 7 × 3 : hexagones dessinés comprimés)
            int frames = 8;
            Bitmap bo = new Bitmap(128, 128 * frames, PixelFormat.Format32bppArgb);
            for (int f = 0; f < frames; f++)
            {
                Bitmap fr = Tex(128, 128, out g);
                float sx = 128f / 7f, sy = 128f / 3f;
                using (SolidBrush b = new SolidBrush(Color.FromArgb(70, 98, 207, 244))) g.FillRectangle(b, 0, 0, 128, 128);
                double vague = (f / (double)frames) * 9.0 - 1.0;
                for (int j = -1; j < 8; j++)
                    for (int i = -1; i < 10; i++)
                    {
                        float hx = (i + (j % 2 == 0 ? 0 : 0.5f)) * 0.78f, hy = j * 0.45f;
                        double d = Math.Abs(hx - vague);
                        int a = (int)(110 + Math.Max(0, 1 - d / 1.2) * 140);
                        using (GraphicsPath p = Hexa(hx * sx, hy * sy, 0.4f * sx, 0.26f * sy))
                        {
                            using (SolidBrush b = new SolidBrush(Color.FromArgb(Math.Max(0, a - 90), 140, 230, 255))) g.FillPath(b, p);
                            using (Pen pe = new Pen(Color.FromArgb(Math.Min(255, a), 190, 245, 255), 1.6f)) g.DrawPath(pe, p);
                        }
                    }
                using (Pen p = new Pen(Color.FromArgb(240, 220, 250, 255), 4)) g.DrawRectangle(p, 2, 2, 124, 124);
                g.Dispose();
                using (Graphics gg = Graphics.FromImage(bo)) gg.DrawImage(fr, 0, 128 * f, 128, 128);
                if (f == 0) apercuObjets.Add(new KeyValuePair<string, Bitmap>("d_bouclier", fr));
            }
            SauverTex("d_bouclier", bo);
            Ecrire("assets/orbitvol/textures/item/d_bouclier.png.mcmeta", "{\n  \"animation\": { \"frametime\": 3, \"interpolate\": true }\n}\n");

            // --- écran de la borne du bouclier
            Bitmap e = Tex(128, 128, out g);
            using (SolidBrush b = new SolidBrush(H("06121C"))) g.FillRectangle(b, 0, 0, 128, 128);
            Halo(g, 64, 58, 50, H("62CFF4", 90));
            using (GraphicsPath p = new GraphicsPath())
            {
                p.AddBezier(64, 20, 80, 30, 92, 28, 98, 28);
                p.AddBezier(98, 28, 100, 70, 84, 90, 64, 102);
                p.AddBezier(64, 102, 44, 90, 28, 70, 30, 28);
                p.AddBezier(30, 28, 36, 28, 48, 30, 64, 20);
                using (LinearGradientBrush lb = new LinearGradientBrush(new Rectangle(28, 18, 72, 86), H("BFF3FF"), H("3A7FFF"), 90f)) g.FillPath(lb, p);
                using (Pen pe = new Pen(BLANC, 3)) g.DrawPath(pe, p);
            }
            Eclat(g, 64, 58, 16, Color.White);
            using (Font ft = new Font(tektur, 13, FontStyle.Regular, GraphicsUnit.Pixel))
            using (StringFormat sf = new StringFormat { Alignment = StringAlignment.Center })
            using (SolidBrush b = new SolidBrush(H("62CFF4"))) g.DrawString("BOUCLIER", ft, b, new RectangleF(0, 106, 128, 20), sf);
            using (Pen p = new Pen(OR, 5)) g.DrawRectangle(p, 2, 2, 124, 124);
            g.Dispose(); SauverTex("d_ecran", e);

            // --- plaque d'encaissement (disque doré + étoile)
            Bitmap pa = Tex(128, 128, out g);
            using (GraphicsPath p = new GraphicsPath())
            {
                p.AddEllipse(4, 4, 120, 120);
                using (PathGradientBrush b = new PathGradientBrush(p)) { b.CenterColor = OR_C; b.SurroundColors = new Color[] { OR_F }; g.FillPath(b, p); }
            }
            using (Pen p = new Pen(CONTOUR, 4)) g.DrawEllipse(p, 4, 4, 120, 120);
            using (Pen p = new Pen(H("FFF1C4"), 3)) g.DrawEllipse(p, 16, 16, 96, 96);
            using (Pen p = new Pen(OR_F, 2)) g.DrawEllipse(p, 22, 22, 84, 84);
            for (int i = 0; i < 16; i++)
            {
                double a = Math.PI * 2 * i / 16;
                using (SolidBrush b = new SolidBrush(H("7A5F22"))) g.FillEllipse(b, 64 + (float)Math.Cos(a) * 53 - 2.5f, 64 + (float)Math.Sin(a) * 53 - 2.5f, 5, 5);
            }
            using (GraphicsPath s = Etoile(64, 64, 34, 9, 4, 0))
            {
                using (SolidBrush b = new SolidBrush(BLANC)) g.FillPath(b, s);
                using (Pen pe = new Pen(OR_F, 3)) g.DrawPath(pe, s);
            }
            g.Dispose(); SauverTex("d_pad", pa);

            // --- socle des places (anneau lumineux)
            Bitmap so = Tex(128, 128, out g);
            using (GraphicsPath p = new GraphicsPath())
            {
                p.AddEllipse(6, 6, 116, 116);
                p.AddEllipse(30, 30, 68, 68);
                using (LinearGradientBrush lb = new LinearGradientBrush(new Rectangle(0, 0, 128, 128), H("BFF3FF"), H("3A7FFF"), 45f)) g.FillPath(lb, p);
                using (Pen pe = new Pen(BLANC, 3)) g.DrawPath(pe, p);
            }
            for (int i = 0; i < 4; i++)
            {
                double a = Math.PI / 2 * i + Math.PI / 4;
                Eclat(g, 64 + (float)Math.Cos(a) * 46, 64 + (float)Math.Sin(a) * 46, 9, Color.White);
            }
            g.Dispose(); SauverTex("d_socle", so);

            // ------------------------------------------------ modèles (centrés sur 8, 8, 8)
            List<string> el = new List<string>();
            el.Add(Boite(V(1, 0, 1), V(15, 3, 15), "d_metal", null, null, null));
            el.Add(Boite(V(0.5, 3, 0.5), V(15.5, 4, 15.5), "d_or", null, null, null));
            el.Add(Boite(V(4, 4, 4), V(12, 14, 12), "d_metal", null, null, null));
            el.Add(Boite(V(3, 14, 3), V(13, 16, 13), "d_or", null, null, null));
            Modele3D("tourelle_socle", new string[] { "d_metal", "d_or" }, el);

            el = new List<string>();
            el.Add(Boite(V(3, 4, 3), V(13, 12, 13), "d_metal", null, null, null));
            el.Add(Boite(V(4, 12, 4), V(12, 13, 12), "d_or", null, null, null));
            el.Add(Boite(V(1, 5, 5), V(3, 11, 11), "d_or", null, null, null));
            el.Add(Boite(V(13, 5, 5), V(15, 11, 11), "d_or", null, null, null));
            el.Add(Boite(V(5, 7, 12.9), V(11, 9, 13.2), "d_lentille", null, null, null));
            el.Add(Boite(V(6.5, 6.5, 13), V(9.5, 9.5, 24), "d_metal", null, null, null));
            el.Add(Boite(V(6, 6, 19), V(10, 10, 21), "d_or", null, null, null));
            el.Add(Boite(V(7, 7, 24), V(9, 9, 25), "d_lentille", null, null, null));
            Modele3D("tourelle_tete", new string[] { "d_metal", "d_or", "d_lentille" }, el);

            el = new List<string>();
            el.Add(Boite(V(7, 7, 8), V(9, 9, 24), "d_laser", null, null, null));
            Modele3D("laser", new string[] { "d_laser" }, el);

            el = new List<string>();
            el.Add(Boite(V(0, 0, 7.75), V(16, 16, 8.25), "d_bouclier", null, null, null));
            Modele3D("bouclier", new string[] { "d_bouclier" }, el);

            el = new List<string>();
            el.Add(Boite(V(3, 0, 3), V(13, 2, 13), "d_or", null, null, null));
            el.Add(Boite(V(5, 2, 5), V(11, 11, 11), "d_metal", null, null, null));
            el.Add(Boite(V(2, 10, 4), V(14, 18, 10), "d_metal", "south", "d_ecran", "{\"angle\": -22.5, \"axis\": \"x\", \"origin\": [8, 10, 7]}"));
            el.Add(Boite(V(1.5, 9.5, 3.5), V(14.5, 10.5, 10.5), "d_or", null, null, "{\"angle\": -22.5, \"axis\": \"x\", \"origin\": [8, 10, 7]}"));
            Modele3D("borne", new string[] { "d_metal", "d_or", "d_ecran" }, el);

            el = new List<string>();
            el.Add(Boite(V(0, 7.75, 0), V(16, 8.25, 16), "d_pad", null, null, null));
            Modele3D("pad", new string[] { "d_pad" }, el);

            el = new List<string>();
            el.Add(Boite(V(0, 7.9, 0), V(16, 8.1, 16), "d_socle", null, null, null));
            Modele3D("socle", new string[] { "d_socle" }, el);

            foreach (string id in new string[] { "d_metal", "d_or", "d_lentille", "d_laser", "d_ecran", "d_pad", "d_socle" })
                apercuObjets.Add(new KeyValuePair<string, Bitmap>(id, new Bitmap(Path.Combine(pack, "assets/orbitvol/textures/item/" + id + ".png"))));
            return 7;
        }

        static int Hud()
        {
            Graphics g;

            // Panneau d'argent (haut de l'écran) : 200 × 26 px d'interface.
            Bitmap pan = Toile(200, 26, out g);
            using (GraphicsPath p = Arrondi(1, 1, 198, 24, 9))
            {
                using (LinearGradientBrush lg = new LinearGradientBrush(new RectangleF(0, 0, 200, 26), Color.FromArgb(235, 26, 39, 64), Color.FromArgb(235, 7, 10, 20), 90f)) g.FillPath(lg, p);
                using (Pen pn = new Pen(OR, 1.6f)) g.DrawPath(pn, p);
            }
            using (Pen pn = new Pen(Color.FromArgb(120, 255, 255, 255), 0.6f)) g.DrawLine(pn, 12, 3, 188, 3);
            using (GraphicsPath sep = Arrondi(128, 5, 66, 16, 6))
            {
                using (SolidBrush sb = new SolidBrush(Color.FromArgb(200, 10, 40, 24))) g.FillPath(sb, sep);
                using (Pen pn = new Pen(H("7CFC9A"), 0.8f)) g.DrawPath(pn, sep);
            }
            Piece(g, 13, 13, 9);
            g.Dispose();
            Glyphe("panneau", pan, "\uE000", 8, 26, "hud");
            Apercu("Panneau d'argent (haut de l'écran)", pan);

            // Icônes 8 × 8 (barre de droite, panneaux).
            IconeHud("i_piece", "\uE010", delegate (Graphics gg) { Piece(gg, 4, 4, 3.6f); });
            IconeHud("i_revenu", "\uE011", delegate (Graphics gg)
            {
                using (GraphicsPath p = new GraphicsPath()) { p.AddPolygon(new PointF[] { new PointF(4.6f, 0.4f), new PointF(1.2f, 4.6f), new PointF(3.8f, 4.6f), new PointF(3.2f, 7.6f), new PointF(6.8f, 3.2f), new PointF(4.2f, 3.2f) }); Volume(gg, p, H("EFFFB0"), H("2AC85A"), 0.5f); }
            });
            IconeHud("i_coffre", "\uE012", delegate (Graphics gg)
            {
                using (GraphicsPath p = Arrondi(0.6f, 2.4f, 6.8f, 5, 1)) Volume(gg, p, H("C8884A"), H("5A3014"), 0.5f);
                using (GraphicsPath p = Arrondi(0.6f, 1, 6.8f, 2.6f, 1)) Volume(gg, p, H("E8A860"), H("7A4A20"), 0.5f);
                using (SolidBrush sb = new SolidBrush(OR_C)) gg.FillRectangle(sb, 3.3f, 3, 1.4f, 2);
            });
            IconeHud("i_orbito", "\uE013", delegate (Graphics gg)
            {
                using (GraphicsPath p = Cercle(4, 4.4f, 3.4f)) Volume(gg, p, H("9CE8FF"), H("2A6AC8"), 0.5f);
                using (SolidBrush sb = new SolidBrush(CONTOUR)) { gg.FillEllipse(sb, 2.3f, 3.4f, 1.2f, 1.4f); gg.FillEllipse(sb, 4.5f, 3.4f, 1.2f, 1.4f); }
            });
            IconeHud("i_bonus", "\uE014", delegate (Graphics gg)
            {
                using (GraphicsPath p = new GraphicsPath()) { p.AddPolygon(new PointF[] { new PointF(4, 0.5f), new PointF(7.4f, 4), new PointF(5.4f, 4), new PointF(5.4f, 7.5f), new PointF(2.6f, 7.5f), new PointF(2.6f, 4), new PointF(0.6f, 4) }); Volume(gg, p, H("D8FFE0"), H("12A03A"), 0.5f); }
            });
            IconeHud("i_dex", "\uE015", delegate (Graphics gg)
            {
                using (GraphicsPath p = Arrondi(1, 0.6f, 6, 6.8f, 0.8f)) Volume(gg, p, H("B07AFF"), H("3A1480"), 0.5f);
                using (GraphicsPath e = Etoile(4.4f, 4, 2, 0.9f, 5, 0)) { using (SolidBrush sb = new SolidBrush(OR_C)) gg.FillPath(sb, e); }
            });
            IconeHud("i_couronne", "\uE016", delegate (Graphics gg)
            {
                using (GraphicsPath p = new GraphicsPath()) { p.AddPolygon(new PointF[] { new PointF(0.6f, 2), new PointF(2.4f, 4), new PointF(4, 0.8f), new PointF(5.6f, 4), new PointF(7.4f, 2), new PointF(6.6f, 7), new PointF(1.4f, 7) }); Volume(gg, p, OR_C, H("B8862A"), 0.5f); }
            });
            IconeHud("i_quete", "\uE017", delegate (Graphics gg)
            {
                using (GraphicsPath p = Arrondi(1, 0.6f, 6, 6.8f, 0.8f)) Volume(gg, p, H("FFF2D0"), H("C8A060"), 0.5f);
                using (Pen pn = new Pen(H("12A03A"), 1.1f) { StartCap = LineCap.Round, EndCap = LineCap.Round }) gg.DrawLines(pn, new PointF[] { new PointF(2.4f, 4.2f), new PointF(3.6f, 5.4f), new PointF(5.8f, 2.6f) });
            });

            // Barre de vol (au-dessus de la barre d'objets) : 11 états, 160 × 18.
            for (int k = 0; k <= 10; k++)
            {
                Bitmap bar = Toile(160, 18, out g);
                using (GraphicsPath p = Arrondi(1, 1, 158, 16, 6))
                {
                    using (LinearGradientBrush lg = new LinearGradientBrush(new RectangleF(0, 0, 160, 18), Color.FromArgb(235, 70, 10, 22), Color.FromArgb(235, 24, 4, 10), 90f)) g.FillPath(lg, p);
                    using (Pen pn = new Pen(H("FF4D6D"), 1.2f)) g.DrawPath(pn, p);
                }
                float lw = 150 * k / 10f;
                if (k > 0)
                    using (GraphicsPath f = Arrondi(5, 11, Math.Max(3, lw), 4, 2))
                    using (LinearGradientBrush lg = new LinearGradientBrush(new RectangleF(5, 11, 151, 4), H("FFE45C"), H("FF4D6D"), 0f)) g.FillPath(lg, f);
                using (Pen pn = new Pen(Color.FromArgb(90, 255, 255, 255), 0.5f)) g.DrawLine(pn, 8, 3, 152, 3);
                g.Dispose();
                Glyphe("vol_" + k, bar, ((char)(0xE020 + k)).ToString(), 7, 18, "hud");
                if (k == 6) Apercu("Barre de vol (60 %)", bar);
            }

            // Chiffres HD : blancs bordés de noir, teintés par la couleur du texte.
            Chiffres("c_haut", 12, 1);       // gros chiffres du panneau
            Chiffres("c_revenu", 8, -1);     // revenu du panneau
            Chiffres("c_vol", 7, 3);         // compte à rebours de la barre de vol

            // Bannières de titre (affichées ×4 par le jeu) : 224 × 56 px d'écran.
            Banniere("b_vole", "\uE100", "VOLÉ !", H("7CFC9A"), H("0E5A24"));
            Banniere("b_voleur", "\uE101", "AU VOLEUR !", H("FF4D6D"), H("6A0A1A"));
            Banniere("b_secret", "\uE102", "SECRET !", H("FFFFFF"), H("6A1A8A"));
            Banniere("b_meteores", "\uE103", "MÉTÉORES !", H("FF9A3A"), H("6A2006"));
            Banniere("b_ascension", "\uE104", "ASCENSION !", H("FFE08A"), H("6A4A10"));
            Banniere("b_quete", "\uE105", "QUÊTE FINIE !", H("62CFF4"), H("0A3A6A"));
            Banniere("b_bienvenue", "\uE106", "VOLE MON ORBITO", H("FFE08A"), H("1A2A6A"));

            // Barre d'objets (sprites du jeu, gardés à leur taille d'origine : dessin fin puis réduit).
            BarreObjets();

            // Barre de boss blanche invisible : le panneau d'argent s'affiche sans la barre dessous.
            foreach (string f in new string[] { "white_background", "white_progress" })
            {
                Bitmap vide = new Bitmap(182, 5, PixelFormat.Format32bppArgb);
                Sauver(vide, "assets/minecraft/textures/gui/sprites/boss_bar/" + f + ".png");
            }

            EcrirePolices();
            return nbHud;
        }

        static void Piece(Graphics g, float cx, float cy, float r)
        {
            Halo(g, cx, cy, r * 1.5f, Color.FromArgb(120, 255, 224, 138));
            using (GraphicsPath c = Cercle(cx, cy, r)) Volume(g, c, OR_C, H("B8862A"), r * 0.12f);
            using (GraphicsPath e = Etoile(cx, cy, r * 0.62f, r * 0.2f, 4, 0)) using (SolidBrush sb = new SolidBrush(H("FFF8D8"))) g.FillPath(sb, e);
        }

        static void IconeHud(string fichier, string car, Dessin d)
        {
            Graphics g;
            Bitmap b = Toile(8, 8, out g);
            d(g);
            g.Dispose();
            Glyphe(fichier, b, car, 7, 8, "hud");
            Apercu("Icône " + fichier, b);
        }

        static readonly string CHIFFRES = "0123456789,.+-/ kMdTs✦%×";

        static void Chiffres(string nom, int hauteur, int ascent)
        {
            int ch = 48;               // hauteur de cellule
            Bitmap planche = null;
            for (int i = 0; i < CHIFFRES.Length; i++)
            {
                char c = CHIFFRES[i];
                int w = c == ' ' ? 14 : (c == ',' || c == '.') ? 14 : c == '/' ? 22 : (c == 'M' || c == '✦' || c == '%' || c == 'T') ? 36 : (c == 'k' || c == 'd' || c == 's') ? 28 : 30;
                Bitmap b = new Bitmap(w, ch, PixelFormat.Format32bppArgb);
                using (Graphics g = Graphics.FromImage(b))
                {
                    Lisse(g);
                    if (c == '✦')
                    {
                        using (GraphicsPath p = Etoile(w / 2f, ch / 2f + 1, 15, 5, 4, 0))
                        {
                            using (Pen pe = new Pen(Color.FromArgb(255, 8, 8, 12), 7) { LineJoin = LineJoin.Round }) g.DrawPath(pe, p);
                            using (SolidBrush sb = new SolidBrush(Color.White)) g.FillPath(sb, p);
                        }
                    }
                    else if (c != ' ')
                        using (GraphicsPath p = new GraphicsPath())
                        using (StringFormat sf = new StringFormat())
                        {
                            sf.Alignment = StringAlignment.Center;
                            sf.LineAlignment = StringAlignment.Center;
                            p.AddString(c.ToString(), tektur, (int)FontStyle.Regular, c == '✦' ? 36 : 44, new PointF(w / 2f, ch / 2f + 1), sf);
                            using (Pen pe = new Pen(Color.FromArgb(255, 8, 8, 12), 7) { LineJoin = LineJoin.Round }) g.DrawPath(pe, p);
                            using (SolidBrush sb = new SolidBrush(Color.White)) g.FillPath(sb, p);
                        }
                }
                string fichier = nom + "_" + ((int)c).ToString("x4");
                Glyphe(fichier, b, c.ToString(), ascent, hauteur, nom);
                if (planche == null && nom == "c_haut") planche = b;
            }
        }

        static void Banniere(string fichier, string car, string texte, Color fond, Color sombre)
        {
            Graphics g;
            Bitmap b = Toile(224, 56, out g);
            using (GraphicsPath hp = new GraphicsPath())
            {
                hp.AddEllipse(6, 4, 212, 48);
                using (PathGradientBrush pg = new PathGradientBrush(hp)) { pg.CenterColor = Color.FromArgb(110, fond); pg.SurroundColors = new Color[] { Color.FromArgb(0, fond) }; g.FillPath(pg, hp); }
            }
            using (GraphicsPath p = new GraphicsPath())
            using (StringFormat sf = new StringFormat())
            {
                sf.Alignment = StringAlignment.Center;
                sf.LineAlignment = StringAlignment.Center;
                float taille = texte.Length > 13 ? 21 : texte.Length > 10 ? 25 : 30;
                p.AddString(texte, tektur, (int)FontStyle.Regular, taille, new PointF(112, 29), sf);
                using (Pen pe = new Pen(CONTOUR, 7) { LineJoin = LineJoin.Round }) g.DrawPath(pe, p);
                using (Pen pe = new Pen(sombre, 4) { LineJoin = LineJoin.Round }) g.DrawPath(pe, p);
                RectangleF r = p.GetBounds();
                using (LinearGradientBrush lg = new LinearGradientBrush(new RectangleF(r.X, r.Y - 1, r.Width, r.Height + 2), Color.White, fond, 90f)) g.FillPath(lg, p);
                Eclat(g, r.Right + 2, r.Top + 2, 6, Color.White);
                Eclat(g, r.Left - 2, r.Bottom - 4, 4, fond);
            }
            g.Dispose();
            Glyphe(fichier, b, car, 14, 14, "titre");
            Apercu("Bannière " + texte, b);
        }

        static void BarreObjets()
        {
            Graphics g;
            Bitmap fin = Toile(182, 22, out g);
            using (GraphicsPath p = Arrondi(0.5f, 0.5f, 181, 21, 4))
            {
                using (LinearGradientBrush lg = new LinearGradientBrush(new RectangleF(0, 0, 182, 22), Color.FromArgb(225, 26, 39, 64), Color.FromArgb(225, 7, 10, 20), 90f)) g.FillPath(lg, p);
                using (Pen pn = new Pen(OR, 1f)) g.DrawPath(pn, p);
            }
            for (int i = 0; i < 9; i++)
                using (GraphicsPath c = Arrondi(3 + i * 20, 3, 16, 16, 3))
                {
                    using (SolidBrush sb = new SolidBrush(Color.FromArgb(160, 4, 6, 12))) g.FillPath(sb, c);
                    using (Pen pn = new Pen(Color.FromArgb(150, 98, 207, 244), 0.5f)) g.DrawPath(pn, c);
                }
            g.Dispose();
            Bitmap barre = Reduire(fin, 182, 22);
            Sauver(barre, "assets/minecraft/textures/gui/sprites/hud/hotbar.png");
            Apercu("Barre d'objets", fin);

            Bitmap fin2 = Toile(24, 24, out g);
            using (GraphicsPath p = Arrondi(1, 1, 22, 22, 4))
            {
                using (Pen pn = new Pen(CONTOUR, 3f)) g.DrawPath(pn, p);
                using (Pen pn = new Pen(OR_C, 1.6f)) g.DrawPath(pn, p);
            }
            Eclat(g, 20, 3, 2.6f, Color.White);
            g.Dispose();
            Bitmap sel = Reduire(fin2, 24, 23);
            Sauver(sel, "assets/minecraft/textures/gui/sprites/hud/hotbar_selection.png");
        }

        static Bitmap Reduire(Bitmap fin, int w, int h)
        {
            Bitmap small = new Bitmap(w, h, PixelFormat.Format32bppArgb);
            using (Graphics g = Graphics.FromImage(small))
            {
                g.CompositingMode = CompositingMode.SourceCopy;
                g.InterpolationMode = InterpolationMode.HighQualityBicubic;
                g.PixelOffsetMode = PixelOffsetMode.HighQuality;
                g.DrawImage(fin, new Rectangle(0, 0, w, h), 0, 0, fin.Width, Math.Min(fin.Height, h * S), GraphicsUnit.Pixel);
            }
            return small;
        }
    }
}
