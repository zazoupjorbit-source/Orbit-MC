// La Station des agents : petite fenêtre flottante + icône près de l'horloge.
// Compilé par station-agents\fenetre\compiler.bat (csc de .NET Framework 4, aucune dépendance).
//
//  - lance le serveur (node serveur.mjs) s'il ne tourne pas déjà ;
//  - ouvre la page en mode compact dans une fenêtre Edge sans barre d'adresse (profil à part) ;
//  - la garde au premier plan, retient sa place et sa taille, la retire de la barre des tâches ;
//  - clic sur l'icône ou Ctrl+Alt+S : masquer / afficher ; bulle quand un agent t'attend.
using System;
using System.Collections;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.IO;
using System.Net;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;
using System.Web.Script.Serialization;
using System.Windows.Forms;

static class Programme
{
    public const string NomMessager = "OrbitStationAgents.Messager";
    public const int WM_BASCULE = 0x8000 + 0x5A;

    [STAThread]
    static void Main()
    {
        bool premier;
        using (var verrou = new Mutex(true, "OrbitStationAgents", out premier))
        {
            if (!premier)
            {
                // déjà lancé : on demande à l'instance en place d'afficher / masquer
                IntPtr h = W.FindWindow(null, NomMessager);
                if (h != IntPtr.Zero) W.PostMessage(h, WM_BASCULE, IntPtr.Zero, IntPtr.Zero);
                return;
            }
            try { W.SetProcessDPIAware(); } catch { }
            Application.EnableVisualStyles();
            SynchronizationContext.SetSynchronizationContext(new WindowsFormsSynchronizationContext());
            Application.Run(new Station());
        }
    }
}

class Station : ApplicationContext
{
    const int PORT = 8777;
    const string TITRE = "La Station des agents";
    static readonly string Adresse = "http://127.0.0.1:" + PORT + "/";
    static readonly string Ici = AppDomain.CurrentDomain.BaseDirectory;
    static readonly string Donnees = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "OrbitStation");
    static readonly string FichierPlace = Path.Combine(Donnees, "fenetre.txt");
    static readonly string Raccourci = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.Startup), TITRE + ".lnk");

    readonly NotifyIcon icone = new NotifyIcon();
    readonly Messager messager;
    readonly System.Windows.Forms.Timer veille = new System.Windows.Forms.Timer();
    readonly System.Windows.Forms.Timer sondage = new System.Windows.Forms.Timer();
    ToolStripMenuItem miAfficher, miDessus, miDemarrage;

    Process serveur;          // seulement si c'est nous qui l'avons lancé
    Process navigateur;
    IntPtr fenetre = IntPtr.Zero;
    bool dessus = true, enLancement, premierSondage = true, interrogation;
    W.RECT derniere;
    Dictionary<string, string> phases = new Dictionary<string, string>();

    // Barre de titre d'Edge rognée : on ne garde que la page. Déplacement / taille gérés ici.
    const int TITRE_EDGE = 31;     // hauteur de la barre d'Edge à 100 % (mesurée)
    const int POIGNEE = 26;        // bande du haut de la page où l'on attrape la fenêtre (px CSS)
    const int OUTILS = 150;        // à droite de cette bande : les petits boutons de la page
    const int COIN = 18;           // coin bas-droit : redimensionner
    W.LowLevelMouseProc crochet;
    IntPtr hCrochet;
    string regionPosee = "";
    int mode;                      // 0 rien, 1 déplacement, 2 taille, 3 clic sur « masquer »
    W.POINT depart;
    W.RECT departFenetre;
    int bordG = 7, bordD = 7, bordB = 7; // bords invisibles autour de la partie visible

    public Station()
    {
        Directory.CreateDirectory(Donnees);
        LirePlace();

        var menu = new ContextMenuStrip();
        miAfficher = new ToolStripMenuItem("Afficher / masquer", null, delegate { Basculer(); });
        miAfficher.ShortcutKeyDisplayString = "Ctrl+Alt+S";
        miAfficher.Font = new Font(miAfficher.Font, FontStyle.Bold);
        miDessus = new ToolStripMenuItem("Toujours au premier plan", null, delegate { dessus = !dessus; miDessus.Checked = dessus; AppliquerDessus(); EcrirePlace(); });
        miDessus.Checked = dessus;
        miDemarrage = new ToolStripMenuItem("Lancer au démarrage de Windows", null, delegate { BasculerDemarrage(); });
        miDemarrage.Checked = File.Exists(Raccourci);
        menu.Items.Add(miAfficher);
        menu.Items.Add(new ToolStripMenuItem("Remettre en bas à droite", null, delegate { RemettreEnPlace(); }));
        menu.Items.Add(miDessus);
        menu.Items.Add(new ToolStripSeparator());
        menu.Items.Add(new ToolStripMenuItem("Ouvrir en grand dans le navigateur", null, delegate { Ouvrir(Adresse); }));
        menu.Items.Add(new ToolStripMenuItem("Ouvrir le QG ORBIT", null, delegate { Ouvrir(Adresse + "qg"); })); // le serveur le lance s'il est éteint
        menu.Items.Add(miDemarrage);
        menu.Items.Add(new ToolStripSeparator());
        menu.Items.Add(new ToolStripMenuItem("Quitter", null, delegate { Quitter(); }));

        icone.Icon = DessinerIcone();
        icone.Text = TITRE;
        icone.ContextMenuStrip = menu;
        icone.Visible = true;
        icone.MouseClick += delegate(object s, MouseEventArgs e) { if (e.Button == MouseButtons.Left) Basculer(); };
        icone.BalloonTipClicked += delegate { Afficher(); };

        messager = new Messager(this);
        if (!W.RegisterHotKey(messager.Handle, 1, W.MOD_CONTROL | W.MOD_ALT | W.MOD_NOREPEAT, (uint)Keys.S))
            icone.ShowBalloonTip(4000, TITRE, "Le raccourci Ctrl+Alt+S est déjà pris par un autre programme : utilise l'icône près de l'horloge.", ToolTipIcon.Info);

        crochet = Souris;
        hCrochet = W.SetWindowsHookEx(W.WH_MOUSE_LL, crochet, W.GetModuleHandle(null), 0);

        veille.Interval = 200; veille.Tick += delegate { Surveiller(); }; veille.Start();
        sondage.Interval = 3000; sondage.Tick += delegate { Sonder(); }; sondage.Start();

        DemarrerServeur();
        Afficher();
    }

    // ------------------------------------------------------------------ fenêtre
    public void Basculer()
    {
        if (fenetre != IntPtr.Zero && W.IsWindow(fenetre) && W.IsWindowVisible(fenetre)) Masquer();
        else Afficher();
    }

    void Afficher()
    {
        if (fenetre != IntPtr.Zero && W.IsWindow(fenetre))
        {
            W.ShowWindow(fenetre, W.SW_SHOW);
            if (W.IsIconic(fenetre)) W.ShowWindow(fenetre, W.SW_RESTORE);
            AppliquerDessus();
            W.SetForegroundWindow(fenetre);
            return;
        }
        LancerFenetre();
    }

    void Masquer()
    {
        RetenirPlace();
        W.ShowWindow(fenetre, W.SW_HIDE);
    }

    void LancerFenetre()
    {
        if (enLancement) return;
        string exe = TrouverNavigateur();
        if (exe == null)
        {
            MessageBox.Show("Ni Microsoft Edge ni Google Chrome n'ont été trouvés : impossible d'ouvrir la fenêtre.", TITRE, MessageBoxButtons.OK, MessageBoxIcon.Warning);
            return;
        }
        W.RECT r = PlaceValide();
        string profil = Path.Combine(Donnees, "navigateur");
        string args = "--app=\"" + Adresse + "?mini\" --user-data-dir=\"" + profil + "\""
            + " --window-size=" + (r.Right - r.Left) + "," + (r.Bottom - r.Top)
            + " --window-position=" + r.Left + "," + r.Top
            + " --no-first-run --no-default-browser-check --disable-sync --hide-crash-restore-bubble --force-dark-mode"
            + " --disable-direct-composition" // sinon le rognage de la barre de titre ne s'applique pas à tout le contenu
            + " --disable-features=msEdgeSidebarV2,msHubApps,msEdgeTranslate,Translate";
        try { navigateur = Process.Start(new ProcessStartInfo(exe, args) { UseShellExecute = false }); }
        catch (Exception ex) { MessageBox.Show("Ouverture impossible : " + ex.Message, TITRE); return; }

        enLancement = true;
        int pid = navigateur.Id;
        var t = new Thread(delegate()
        {
            IntPtr trouve = IntPtr.Zero;
            for (int i = 0; i < 150 && trouve == IntPtr.Zero; i++) { Thread.Sleep(100); trouve = ChercherFenetre(pid); }
            messager.Executer(delegate { enLancement = false; if (trouve != IntPtr.Zero) Preparer(trouve); });
        });
        t.IsBackground = true; t.Start();
    }

    void Preparer(IntPtr h)
    {
        fenetre = h; regionPosee = "";
        // hors de la barre des tâches : on la retrouve par l'icône près de l'horloge
        W.ShowWindow(h, W.SW_HIDE);
        int ex = W.GetWindowLong(h, W.GWL_EXSTYLE);
        W.SetWindowLong(h, W.GWL_EXSTYLE, (ex | W.WS_EX_TOOLWINDOW) & ~W.WS_EX_APPWINDOW);
        W.RECT r = PlaceValide();
        Placer(h, r);
        W.SetForegroundWindow(h);
        derniere = r;
        // Edge peut agrandir sa fenêtre juste après l'ouverture (profil neuf) : on repasse deux fois.
        int essais = 0;
        var t = new System.Windows.Forms.Timer { Interval = 700 };
        t.Tick += delegate
        {
            if (++essais >= 3 || !W.IsWindow(h)) { t.Stop(); t.Dispose(); return; }
            W.RECT a;
            if (W.IsZoomed(h) || (W.GetWindowRect(h, out a) && (a.Left != r.Left || a.Top != r.Top || a.Right != r.Right || a.Bottom != r.Bottom))) Placer(h, r);
        };
        t.Start();
    }

    void Placer(IntPtr h, W.RECT r)
    {
        if (W.IsZoomed(h) || W.IsIconic(h)) W.ShowWindow(h, W.SW_RESTORE);
        var p = new W.WINDOWPLACEMENT();
        p.length = Marshal.SizeOf(typeof(W.WINDOWPLACEMENT));
        p.showCmd = W.SW_SHOWNORMAL;
        p.ptMin = new W.POINT { X = -1, Y = -1 }; p.ptMax = new W.POINT { X = -1, Y = -1 };
        p.rcNormal = r;
        W.SetWindowPlacement(h, ref p);
        W.SetWindowPos(h, dessus ? W.HWND_TOPMOST : W.HWND_NOTOPMOST, r.Left, r.Top, r.Right - r.Left, r.Bottom - r.Top, W.SWP_SHOWWINDOW);
    }

    void AppliquerDessus()
    {
        if (fenetre == IntPtr.Zero || !W.IsWindow(fenetre)) return;
        W.SetWindowPos(fenetre, dessus ? W.HWND_TOPMOST : W.HWND_NOTOPMOST, 0, 0, 0, 0, W.SWP_NOMOVE | W.SWP_NOSIZE | W.SWP_NOACTIVATE);
    }

    void RemettreEnPlace()
    {
        derniere = PlaceParDefaut(); EcrirePlace();
        if (fenetre != IntPtr.Zero && W.IsWindow(fenetre)) Placer(fenetre, derniere);
        else Afficher();
    }

    void Surveiller()
    {
        if (fenetre == IntPtr.Zero) return;
        if (!W.IsWindow(fenetre)) { fenetre = IntPtr.Zero; regionPosee = ""; return; } // fermée : rouverte au prochain clic
        if (!W.IsWindowVisible(fenetre) || W.IsIconic(fenetre)) return;
        if (W.IsZoomed(fenetre)) W.ShowWindow(fenetre, W.SW_RESTORE); // pas de plein écran sans barre
        Rogner();
        if (mode == 0) { Ramener(); RetenirPlace(); }
    }

    double Echelle(IntPtr h)
    {
        try { uint d = W.GetDpiForWindow(h); if (d > 0) return d / 96.0; } catch { }
        return 1.0;
    }

    // Région = la page seule (sans la barre d'Edge ni les bords invisibles), coins arrondis.
    void Rogner()
    {
        W.RECT r, v, boite;
        // Sans cadre Windows (sinon il dessine un liseré blanc et un voile au-dessus de la page).
        int style = W.GetWindowLong(fenetre, W.GWL_STYLE);
        if ((style & (W.WS_CAPTION | W.WS_THICKFRAME)) != 0)
        {
            // Edge garde ensuite une marge noire de la taille des anciennes bordures : on la mesure avant de retirer le cadre.
            if (W.GetWindowRect(fenetre, out r) && W.DwmGetWindowAttribute(fenetre, W.DWMWA_EXTENDED_FRAME_BOUNDS, out v, Marshal.SizeOf(typeof(W.RECT))) == 0
                && v.Left > r.Left)
            { bordG = v.Left - r.Left; bordD = r.Right - v.Right; bordB = r.Bottom - v.Bottom; }
            W.SetWindowLong(fenetre, W.GWL_STYLE, style & ~(W.WS_CAPTION | W.WS_THICKFRAME));
            W.SetWindowPos(fenetre, IntPtr.Zero, 0, 0, 0, 0, W.SWP_FRAMECHANGED | W.SWP_NOMOVE | W.SWP_NOSIZE | W.SWP_NOZORDER | W.SWP_NOACTIVATE);
            regionPosee = "";
            return; // bords remesurés au prochain passage
        }
        if (!W.GetWindowRect(fenetre, out r)) return;
        // Edge remet parfois sa propre région (changement de thème, de taille…) : on vérifie à chaque passage.
        int type = W.GetWindowRgnBox(fenetre, out boite);
        if (mode == 0 && (type == 0 || boite.Top != (int)Math.Round(TITRE_EDGE * Echelle(fenetre)) || boite.Left != bordG)) regionPosee = "";
        RognerA(r.Right - r.Left, r.Bottom - r.Top);
    }

    void RognerA(int w, int h)
    {
        double e = Echelle(fenetre);
        int haut = (int)Math.Round(TITRE_EDGE * e), rayon = (int)Math.Round(16 * e);
        string cle = w + "x" + h + "@" + haut;
        if (cle == regionPosee) return;
        IntPtr rgn = W.CreateRoundRectRgn(bordG, haut, w - bordD + 1, h - bordB + 1, rayon, rayon);
        if (W.SetWindowRgn(fenetre, rgn, true) != 0) regionPosee = cle; else W.DeleteObject(rgn);
        int aucune = unchecked((int)0xFFFFFFFE); // pas de liseré Windows 11 autour
        W.DwmSetWindowAttribute(fenetre, W.DWMWA_BORDER_COLOR, ref aucune, 4);
    }

    // Partie visible de la page, en coordonnées écran.
    bool Contenu(out W.RECT c)
    {
        W.RECT r; c = new W.RECT();
        if (!W.GetWindowRect(fenetre, out r)) return false;
        c.Left = r.Left + bordG; c.Right = r.Right - bordD; c.Bottom = r.Bottom - bordB;
        c.Top = r.Top + (int)Math.Round(TITRE_EDGE * Echelle(fenetre));
        return true;
    }

    IntPtr Souris(int code, IntPtr wParam, IntPtr lParam)
    {
        if (code >= 0 && fenetre != IntPtr.Zero)
        {
            var s = (W.MSLLHOOKSTRUCT)Marshal.PtrToStructure(lParam, typeof(W.MSLLHOOKSTRUCT));
            int m = wParam.ToInt32();
            if (m == W.WM_LBUTTONDOWN && mode == 0 && W.IsWindowVisible(fenetre)
                && W.GetAncestor(W.WindowFromPoint(s.pt), 2) == fenetre)
            {
                W.RECT c;
                if (Contenu(out c))
                {
                    double e = Echelle(fenetre);
                    int x = s.pt.X, y = s.pt.Y, z = (int)(30 * e);
                    if (x >= c.Right - z && y < c.Top + z) mode = 3;                                          // bouton « masquer »
                    else if (y < c.Top + POIGNEE * e && x < c.Right - OUTILS * e) mode = 1;                   // poignée du haut
                    else if (x >= c.Right - COIN * e && y >= c.Bottom - COIN * e) mode = 2;                   // coin bas-droit
                    if (mode != 0)
                    {
                        depart = s.pt; W.GetWindowRect(fenetre, out departFenetre);
                        return new IntPtr(1); // la page ne reçoit pas ce clic
                    }
                }
            }
            else if (m == W.WM_MOUSEMOVE && (mode == 1 || mode == 2))
            {
                int dx = s.pt.X - depart.X, dy = s.pt.Y - depart.Y;
                int w = departFenetre.Right - departFenetre.Left, h = departFenetre.Bottom - departFenetre.Top;
                if (mode == 1)
                {
                    int nx = departFenetre.Left + dx, ny = departFenetre.Top + dy;
                    Aimanter(ref nx, ref ny, w, h, s.pt);
                    W.SetWindowPos(fenetre, IntPtr.Zero, nx, ny, 0, 0, W.SWP_NOSIZE | W.SWP_NOZORDER | W.SWP_NOACTIVATE | W.SWP_ASYNCWINDOWPOS);
                }
                else
                {
                    double e = Echelle(fenetre);
                    Rectangle z = Screen.FromPoint(new Point(departFenetre.Left + bordG, departFenetre.Top)).WorkingArea;
                    int nw = Math.Max((int)(280 * e), Math.Min(w + dx, z.Right - departFenetre.Left + bordD));
                    int nh = Math.Max((int)(200 * e), Math.Min(h + dy, z.Bottom - departFenetre.Top + bordB));
                    RognerA(nw, nh);
                    W.SetWindowPos(fenetre, IntPtr.Zero, 0, 0, nw, nh, W.SWP_NOMOVE | W.SWP_NOZORDER | W.SWP_NOACTIVATE | W.SWP_ASYNCWINDOWPOS);
                }
            }
            else if (m == W.WM_LBUTTONUP && mode != 0)
            {
                int fini = mode; mode = 0;
                if (fini == 3) messager.Executer(delegate { Masquer(); });
                else messager.Executer(delegate { Ramener(); RetenirPlace(); });
                return new IntPtr(1);
            }
        }
        return W.CallNextHookEx(hCrochet, code, wParam, lParam);
    }

    // Colle aux bords de l'écran à moins de 12 px.
    void Aimanter(ref int x, ref int y, int w, int h, W.POINT curseur)
    {
        Rectangle z = Screen.FromPoint(new Point(curseur.X, curseur.Y)).WorkingArea;
        int haut = (int)Math.Round(TITRE_EDGE * Echelle(fenetre)), a = 12;
        int g = x + bordG, d = x + w - bordD, t = y + haut, b = y + h - bordB;
        if (Math.Abs(g - z.Left) < a) x = z.Left - bordG;
        else if (Math.Abs(d - z.Right) < a) x = z.Right - w + bordD;
        if (Math.Abs(t - z.Top) < a) y = z.Top - haut;
        else if (Math.Abs(b - z.Bottom) < a) y = z.Bottom - h + bordB;
        // butée : la station reste entière dans l'écran (jamais sous la barre des tâches)
        if (x + w - bordD > z.Right) x = z.Right - w + bordD;
        if (x + bordG < z.Left) x = z.Left - bordG;
        if (y + h - bordB > z.Bottom) y = z.Bottom - h + bordB;
        if (y + haut < z.Top) y = z.Top - haut;
    }

    // Ramène la fenêtre entière dans l'écran le plus proche (après un changement d'écran, de résolution…).
    void Ramener()
    {
        W.RECT r;
        if (fenetre == IntPtr.Zero || W.IsZoomed(fenetre) || W.IsIconic(fenetre) || !W.GetWindowRect(fenetre, out r)) return;
        int w = r.Right - r.Left, h = r.Bottom - r.Top, x = r.Left, y = r.Top;
        var c = new W.POINT { X = r.Left + w / 2, Y = r.Top + h / 2 };
        Aimanter(ref x, ref y, w, h, c);
        if (x != r.Left || y != r.Top)
            W.SetWindowPos(fenetre, IntPtr.Zero, x, y, 0, 0, W.SWP_NOSIZE | W.SWP_NOZORDER | W.SWP_NOACTIVATE);
    }

    static IntPtr ChercherFenetre(int pid)
    {
        IntPtr trouve = IntPtr.Zero;
        W.EnumWindows(delegate(IntPtr h, IntPtr l)
        {
            uint p; W.GetWindowThreadProcessId(h, out p);
            if (p != pid || !W.IsWindowVisible(h)) return true;
            var classe = new StringBuilder(64); W.GetClassName(h, classe, 64);
            if (classe.ToString() != "Chrome_WidgetWin_1" || W.GetWindowTextLength(h) == 0) return true;
            trouve = h; return false;
        }, IntPtr.Zero);
        return trouve;
    }

    static string TrouverNavigateur()
    {
        string[] bases = {
            Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86),
            Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles),
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData) };
        string[] chemins = { @"Microsoft\Edge\Application\msedge.exe", @"Google\Chrome\Application\chrome.exe" };
        foreach (string c in chemins) foreach (string b in bases)
        {
            if (string.IsNullOrEmpty(b)) continue;
            string p = Path.Combine(b, c);
            if (File.Exists(p)) return p;
        }
        return null;
    }

    // ------------------------------------------------------------------ place et taille retenues
    void RetenirPlace()
    {
        W.RECT r;
        if (fenetre == IntPtr.Zero || W.IsZoomed(fenetre) || W.IsIconic(fenetre) || !W.GetWindowRect(fenetre, out r)) return;
        if (r.Right - r.Left < 120 || r.Bottom - r.Top < 80) return;
        if (r.Left == derniere.Left && r.Top == derniere.Top && r.Right == derniere.Right && r.Bottom == derniere.Bottom) return;
        derniere = r; EcrirePlace();
    }

    void LirePlace()
    {
        try
        {
            string[] v = File.ReadAllText(FichierPlace).Trim().Split(' ');
            derniere = new W.RECT { Left = int.Parse(v[0]), Top = int.Parse(v[1]), Right = int.Parse(v[2]), Bottom = int.Parse(v[3]) };
            if (v.Length > 4) dessus = v[4] != "0";
        }
        catch { derniere = new W.RECT(); }
    }

    void EcrirePlace()
    {
        try { File.WriteAllText(FichierPlace, derniere.Left + " " + derniere.Top + " " + derniere.Right + " " + derniere.Bottom + " " + (dessus ? 1 : 0)); } catch { }
    }

    W.RECT PlaceValide()
    {
        var r = derniere;
        if (r.Right - r.Left < 120 || r.Bottom - r.Top < 80) return PlaceParDefaut();
        var rect = Rectangle.FromLTRB(r.Left, r.Top, r.Right, r.Bottom);
        foreach (Screen s in Screen.AllScreens)
        {
            var i = Rectangle.Intersect(s.WorkingArea, rect);
            if (i.Width < 100 || i.Height < 60) continue;
            // sur cet écran : on la recale entière dans la zone utile (la marge de bordure est rattrapée par Ramener)
            Rectangle z = s.WorkingArea;
            int w = Math.Min(r.Right - r.Left, z.Width), h = Math.Min(r.Bottom - r.Top, z.Height);
            int x = Math.Max(z.Left, Math.Min(r.Left, z.Right - w)), y = Math.Max(z.Top, Math.Min(r.Top, z.Bottom - h));
            return new W.RECT { Left = x, Top = y, Right = x + w, Bottom = y + h };
        }
        return PlaceParDefaut();
    }

    static W.RECT PlaceParDefaut()
    {
        float echelle = 1f;
        using (var g = Graphics.FromHwnd(IntPtr.Zero)) echelle = g.DpiX / 96f;
        int w = (int)(470 * echelle), h = (int)(350 * echelle), marge = (int)(16 * echelle);
        Rectangle z = Screen.PrimaryScreen.WorkingArea;
        return new W.RECT { Left = z.Right - w - marge, Top = z.Bottom - h - marge, Right = z.Right - marge, Bottom = z.Bottom - marge };
    }

    // ------------------------------------------------------------------ serveur et nouvelles des agents
    static bool ServeurRepond()
    {
        try
        {
            var q = (HttpWebRequest)WebRequest.Create(Adresse + "api/etat");
            q.Timeout = 1500; q.Proxy = null;
            using (var r = (HttpWebResponse)q.GetResponse()) return r.StatusCode == HttpStatusCode.OK;
        }
        catch { return false; }
    }

    void DemarrerServeur()
    {
        if (ServeurRepond()) return;
        string script = Path.Combine(Ici, "serveur.mjs");
        if (!File.Exists(script)) script = Path.Combine(Path.GetDirectoryName(Ici.TrimEnd('\\')), "serveur.mjs");
        try
        {
            serveur = Process.Start(new ProcessStartInfo("node", "\"" + script + "\"")
            { UseShellExecute = false, CreateNoWindow = true, WorkingDirectory = Path.GetDirectoryName(script) });
        }
        catch
        {
            MessageBox.Show("Impossible de lancer « node serveur.mjs ». Node.js est-il installé ?", TITRE, MessageBoxButtons.OK, MessageBoxIcon.Warning);
        }
    }

    void Sonder()
    {
        if (interrogation) return;
        interrogation = true;
        var wc = new WebClient { Encoding = Encoding.UTF8, Proxy = null };
        wc.DownloadStringCompleted += delegate(object s, DownloadStringCompletedEventArgs e)
        {
            interrogation = false; wc.Dispose();
            if (e.Error != null || e.Cancelled) { MettreBulleTexte(TITRE + " — serveur arrêté"); return; }
            try { Lire(e.Result); } catch { }
        };
        try { wc.DownloadStringAsync(new Uri(Adresse + "api/etat")); } catch { interrogation = false; }
    }

    void Lire(string json)
    {
        var racine = (Dictionary<string, object>)new JavaScriptSerializer().DeserializeObject(json);
        var liste = (object[])racine["agents"];
        int mission = 0, attente = 0;
        var nouvelles = new Dictionary<string, string>();
        var appels = new List<string>();
        foreach (object o in liste)
        {
            var a = (Dictionary<string, object>)o;
            string id = (string)a["id"], phase = (string)a["phase"], nom = (string)a["nom"];
            nouvelles[id] = phase;
            if (phase == "travaille") mission++;
            bool attend = phase == "attend" || phase == "question";
            if (attend) attente++;
            string avant;
            if (attend && !premierSondage && (!phases.TryGetValue(id, out avant) || (avant != "attend" && avant != "question"))) appels.Add(nom);
        }
        phases = nouvelles; premierSondage = false;
        MettreBulleTexte("Station : " + mission + " en mission, " + attente + (attente > 1 ? " t'attendent" : " t'attend"));
        bool visible = fenetre != IntPtr.Zero && W.IsWindow(fenetre) && W.IsWindowVisible(fenetre);
        if (appels.Count > 0 && !visible)
            icone.ShowBalloonTip(5000, appels.Count > 1 ? appels.Count + " agents t'attendent" : "Un agent t'attend",
                string.Join("\n", appels.ToArray()) + "\n(clique pour ouvrir la Station)", ToolTipIcon.Info);
    }

    void MettreBulleTexte(string t) { icone.Text = t.Length > 63 ? t.Substring(0, 62) + "…" : t; }

    // ------------------------------------------------------------------ divers
    void BasculerDemarrage()
    {
        try
        {
            if (File.Exists(Raccourci)) File.Delete(Raccourci);
            else
            {
                Type t = Type.GetTypeFromProgID("WScript.Shell");
                object sh = Activator.CreateInstance(t);
                object lnk = t.InvokeMember("CreateShortcut", BindingFlags.InvokeMethod, null, sh, new object[] { Raccourci });
                Type tl = lnk.GetType();
                tl.InvokeMember("TargetPath", BindingFlags.SetProperty, null, lnk, new object[] { Application.ExecutablePath });
                tl.InvokeMember("WorkingDirectory", BindingFlags.SetProperty, null, lnk, new object[] { Ici });
                tl.InvokeMember("Description", BindingFlags.SetProperty, null, lnk, new object[] { TITRE });
                tl.InvokeMember("Save", BindingFlags.InvokeMethod, null, lnk, null);
            }
        }
        catch (Exception ex) { MessageBox.Show("Raccourci impossible : " + ex.Message, TITRE); }
        miDemarrage.Checked = File.Exists(Raccourci);
    }

    static void Ouvrir(string url) { try { Process.Start(url); } catch { } }

    void Quitter()
    {
        RetenirPlace();
        veille.Stop(); sondage.Stop();
        if (fenetre != IntPtr.Zero && W.IsWindow(fenetre)) W.PostMessage(fenetre, W.WM_CLOSE, IntPtr.Zero, IntPtr.Zero);
        try { if (serveur != null && !serveur.HasExited) serveur.Kill(); } catch { }
        W.UnregisterHotKey(messager.Handle, 1);
        if (hCrochet != IntPtr.Zero) W.UnhookWindowsHookEx(hCrochet);
        icone.Visible = false; icone.Dispose();
        messager.DestroyHandle();
        ExitThread();
    }

    // Icône : planète bleue et anneau d'or, dessinée par code.
    static Icon DessinerIcone()
    {
        var b = new Bitmap(32, 32);
        using (var g = Graphics.FromImage(b))
        {
            g.SmoothingMode = SmoothingMode.AntiAlias;
            g.TranslateTransform(16, 16); g.RotateTransform(-22);
            using (var anneau = new Pen(Color.FromArgb(242, 184, 58), 2.6f))
            {
                g.DrawArc(anneau, -15, -5.5f, 30, 11, 180, 180);
                using (var corps = new LinearGradientBrush(new PointF(-8, -8), new PointF(8, 8), Color.FromArgb(111, 177, 255), Color.FromArgb(18, 46, 140)))
                    g.FillEllipse(corps, -8.5f, -8.5f, 17, 17);
                g.DrawArc(anneau, -15, -5.5f, 30, 11, 0, 180);
            }
            g.ResetTransform();
            using (var lune = new SolidBrush(Color.FromArgb(255, 216, 122))) g.FillEllipse(lune, 24, 4, 5, 5);
        }
        return Icon.FromHandle(b.GetHicon());
    }

    // Fenêtre invisible qui reçoit le raccourci clavier et l'appel d'une 2e instance.
    class Messager : NativeWindow
    {
        readonly Station station;
        readonly Queue<MethodInvoker> file = new Queue<MethodInvoker>();
        const int WM_FILE = 0x8000 + 0x5B;
        public Messager(Station s)
        {
            station = s;
            CreateHandle(new CreateParams { Caption = Programme.NomMessager });
        }
        public void Executer(MethodInvoker m)
        {
            lock (file) file.Enqueue(m);
            W.PostMessage(Handle, WM_FILE, IntPtr.Zero, IntPtr.Zero);
        }
        protected override void WndProc(ref Message m)
        {
            if (m.Msg == W.WM_HOTKEY || m.Msg == Programme.WM_BASCULE) { station.Basculer(); return; }
            if (m.Msg == WM_FILE)
            {
                while (true)
                {
                    MethodInvoker a;
                    lock (file) { if (file.Count == 0) break; a = file.Dequeue(); }
                    a();
                }
                return;
            }
            base.WndProc(ref m);
        }
    }
}

static class W
{
    [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left, Top, Right, Bottom; }
    [StructLayout(LayoutKind.Sequential)] public struct POINT { public int X, Y; }
    [StructLayout(LayoutKind.Sequential)] public struct WINDOWPLACEMENT { public int length, flags, showCmd; public POINT ptMin, ptMax; public RECT rcNormal; }
    public const int SW_SHOWNORMAL = 1;
    public const int WH_MOUSE_LL = 14, WM_MOUSEMOVE = 0x200, WM_LBUTTONDOWN = 0x201, WM_LBUTTONUP = 0x202, DWMWA_EXTENDED_FRAME_BOUNDS = 9;
    public const uint SWP_NOZORDER = 4, SWP_ASYNCWINDOWPOS = 0x4000;
    [StructLayout(LayoutKind.Sequential)] public struct MSLLHOOKSTRUCT { public POINT pt; public uint mouseData, flags, time; public IntPtr extra; }
    public delegate IntPtr LowLevelMouseProc(int code, IntPtr w, IntPtr l);
    [DllImport("user32.dll")] public static extern IntPtr SetWindowsHookEx(int id, LowLevelMouseProc p, IntPtr mod, uint thread);
    [DllImport("user32.dll")] public static extern bool UnhookWindowsHookEx(IntPtr h);
    [DllImport("user32.dll")] public static extern IntPtr CallNextHookEx(IntPtr h, int code, IntPtr w, IntPtr l);
    [DllImport("kernel32.dll", CharSet = CharSet.Unicode)] public static extern IntPtr GetModuleHandle(string n);
    [DllImport("user32.dll")] public static extern IntPtr WindowFromPoint(POINT p);
    [DllImport("user32.dll")] public static extern IntPtr GetAncestor(IntPtr h, uint f);
    [DllImport("user32.dll")] public static extern uint GetDpiForWindow(IntPtr h);
    [DllImport("dwmapi.dll")] public static extern int DwmGetWindowAttribute(IntPtr h, int a, out RECT r, int n);
    [DllImport("gdi32.dll")] public static extern IntPtr CreateRoundRectRgn(int l, int t, int r, int b, int w, int h);
    [DllImport("gdi32.dll")] public static extern bool DeleteObject(IntPtr o);
    [DllImport("user32.dll")] public static extern int SetWindowRgn(IntPtr h, IntPtr rgn, bool redessiner);
    [DllImport("user32.dll")] public static extern int GetWindowRgnBox(IntPtr h, out RECT r);
    [DllImport("dwmapi.dll")] public static extern int DwmSetWindowAttribute(IntPtr h, int a, ref int v, int n);
    public const int DWMWA_BORDER_COLOR = 34, GWL_STYLE = -16, WS_CAPTION = 0x00C00000, WS_THICKFRAME = 0x00040000;
    public const uint SWP_FRAMECHANGED = 0x20;
    [DllImport("user32.dll")] public static extern bool IsZoomed(IntPtr h);
    [DllImport("user32.dll")] public static extern bool SetWindowPlacement(IntPtr h, ref WINDOWPLACEMENT p);
    public delegate bool EnumProc(IntPtr h, IntPtr l);
    public const int SW_HIDE = 0, SW_SHOW = 5, SW_RESTORE = 9, GWL_EXSTYLE = -20, WS_EX_TOOLWINDOW = 0x80, WS_EX_APPWINDOW = 0x40000;
    public const int WM_CLOSE = 0x10, WM_HOTKEY = 0x312;
    public const uint MOD_ALT = 1, MOD_CONTROL = 2, MOD_NOREPEAT = 0x4000;
    public const uint SWP_NOSIZE = 1, SWP_NOMOVE = 2, SWP_NOACTIVATE = 0x10, SWP_SHOWWINDOW = 0x40;
    public static readonly IntPtr HWND_TOPMOST = new IntPtr(-1), HWND_NOTOPMOST = new IntPtr(-2);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] public static extern IntPtr FindWindow(string c, string t);
    [DllImport("user32.dll")] public static extern bool PostMessage(IntPtr h, int m, IntPtr w, IntPtr l);
    [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
    [DllImport("user32.dll")] public static extern bool EnumWindows(EnumProc p, IntPtr l);
    [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr h, out uint pid);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] public static extern int GetClassName(IntPtr h, StringBuilder s, int n);
    [DllImport("user32.dll")] public static extern int GetWindowTextLength(IntPtr h);
    [DllImport("user32.dll")] public static extern bool IsWindow(IntPtr h);
    [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr h);
    [DllImport("user32.dll")] public static extern bool IsIconic(IntPtr h);
    [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int n);
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
    [DllImport("user32.dll")] public static extern int GetWindowLong(IntPtr h, int i);
    [DllImport("user32.dll")] public static extern int SetWindowLong(IntPtr h, int i, int v);
    [DllImport("user32.dll")] public static extern bool SetWindowPos(IntPtr h, IntPtr apres, int x, int y, int cx, int cy, uint f);
    [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
    [DllImport("user32.dll")] public static extern bool RegisterHotKey(IntPtr h, int id, uint mods, uint vk);
    [DllImport("user32.dll")] public static extern bool UnregisterHotKey(IntPtr h, int id);
}
