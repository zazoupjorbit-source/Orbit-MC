// Minecraft Dev Panel - backend local (C# 5 / .NET Framework 4).
// Lance un serveur HTTP sur 127.0.0.1, pilote le processus Paper et sert l'interface web (panel\web).
// Recompiler : panel\build.bat
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Net;
using System.Security.Cryptography;
using System.Text;
using System.Threading;
using System.Web.Script.Serialization;
using System.Windows.Forms;

namespace DevPanel
{
    static class Paths
    {
        public const string Root = @"C:\Users\Zazou\MinecraftDev";
        public static string Server { get { return Path.Combine(Root, "server"); } }
        public static string Panel { get { return Path.Combine(Root, "panel"); } }
        public static string Web { get { return Path.Combine(Panel, "web"); } }
        public static string Backups { get { return Path.Combine(Root, "backups"); } }
        public static string MapBackup { get { return Path.Combine(Root, @"maps\gameland-spawn"); } }
        public static string SettingsFile { get { return Path.Combine(Panel, "settings.json"); } }
        public static string RuntimeFile { get { return Path.Combine(Panel, "runtime.json"); } }
        public static string StatsFile { get { return Path.Combine(Server, @"plugins\PanelBridge\stats.json"); } }
    }

    // Base MariaDB du reseau (port 3318, donnees dans db\data) : LuckPerms et les plugins en ont besoin.
    // Demarree a l'ouverture du panel et verifiee avant chaque demarrage de serveur. Jamais arretee par le panel.
    static class Database
    {
        public const int Port = 3318;
        const string Mysqld = @"C:\Program Files\MariaDB 11.8\bin\mysqld.exe";
        static string Ini { get { return Path.Combine(Paths.Root, @"db\data\my.ini"); } }
        static readonly object sync = new object();

        public static bool Running()
        {
            try { return System.Net.NetworkInformation.IPGlobalProperties.GetIPGlobalProperties().GetActiveTcpListeners().Any(e => e.Port == Port); }
            catch { return false; }
        }

        // wait = true : attend (20 s max) que la base reponde. Renvoie false si elle n'a pas pu demarrer.
        public static bool EnsureStarted(bool wait)
        {
            lock (sync)
            {
                if (Running()) return true;
                if (!File.Exists(Ini) || !File.Exists(Mysqld)) return false;
                try
                {
                    Process.Start(new ProcessStartInfo(Mysqld, "--defaults-file=\"" + Ini + "\"")
                    {
                        UseShellExecute = false,
                        CreateNoWindow = true,
                        WorkingDirectory = Path.GetDirectoryName(Ini)
                    });
                }
                catch { return false; }
                if (!wait) return true;
                var deadline = DateTime.Now.AddSeconds(20);
                while (DateTime.Now < deadline)
                {
                    if (Running()) return true;
                    Thread.Sleep(300);
                }
                return Running();
            }
        }
    }

    class Settings
    {
        public int ramMb = 4096;
        public bool autoRestart = true;
        public bool debug = true;
        public int debugPort = 5005;
        public bool hotswap = true; // JetBrains Runtime + redefinition avancee des classes
        public int port = 8765;
        public string watchPath = "";
        public bool autoDeploy = true;
        public bool autoStart = false;
        public string extraArgs = "";
    }

    class LogLine { public long id; public string t; public long ts; }

    class Program
    {
        public static Settings Cfg;
        public static string Token;
        public static ServerManager Mc;
        public static JavaScriptSerializer Json = new JavaScriptSerializer { MaxJsonLength = int.MaxValue };
        static NotifyIcon tray;

        [STAThread]
        static void Main()
        {
            bool created;
            var mutex = new Mutex(true, "MinecraftDevPanel_Zazou", out created);
            if (!created)
            {
                // Deja lance : on rouvre juste la fenetre.
                try
                {
                    var rt = Json.Deserialize<Dictionary<string, object>>(File.ReadAllText(Paths.RuntimeFile));
                    OpenWindow(Convert.ToInt32(rt["port"]), (string)rt["token"]);
                }
                catch { MessageBox.Show("Le panel est deja lance (icone pres de l'horloge)."); }
                return;
            }

            LoadSettings();
            Database.EnsureStarted(false);
            Token = NewToken();
            Mc = new ServerManager();
            DevWatcher.Apply();

            int port = Cfg.port;
            var http = new HttpServer();
            try { http.Start(port); }
            catch (Exception e)
            {
                MessageBox.Show("Impossible d'ouvrir le port " + port + " : " + e.Message, "Minecraft Dev Panel");
                return;
            }
            File.WriteAllText(Paths.RuntimeFile, Json.Serialize(new Dictionary<string, object> { { "port", port }, { "token", Token } }));

            Application.EnableVisualStyles();
            BuildTray(port);
            if (Cfg.autoStart) Mc.Start();
            OpenWindow(port, Token);
            Application.Run();

            tray.Visible = false;
            GC.KeepAlive(mutex);
        }

        static void BuildTray(int port)
        {
            var menu = new ContextMenuStrip();
            menu.Items.Add("Ouvrir le panel", null, delegate { OpenWindow(port, Token); });
            menu.Items.Add("Demarrer le serveur", null, delegate { Mc.Start(); });
            menu.Items.Add("Arreter le serveur", null, delegate { Mc.Stop(); });
            menu.Items.Add(new ToolStripSeparator());
            menu.Items.Add("Demarrer le reseau (Velocity + mapdev)", null, delegate
            {
                foreach (var i in Instances.All) { try { i.Start(); } catch (Exception ex) { tray.ShowBalloonTip(3000, "Minecraft Dev Panel", ex.Message, ToolTipIcon.Warning); } }
            });
            menu.Items.Add("Arreter le reseau", null, delegate { foreach (var i in Instances.All) i.Stop(); });
            menu.Items.Add(new ToolStripSeparator());
            menu.Items.Add("Quitter (arrete tout)", null, delegate { Quit(); });
            Icon ico;
            try { ico = Icon.ExtractAssociatedIcon(Application.ExecutablePath); } catch { ico = SystemIcons.Application; }
            tray = new NotifyIcon { Icon = ico, Text = "Minecraft Dev Panel", ContextMenuStrip = menu, Visible = true };
            tray.DoubleClick += delegate { OpenWindow(port, Token); };
            tray.ShowBalloonTip(3000, "Minecraft Dev Panel", "Le panel tourne ici. Double-clic pour l'ouvrir.", ToolTipIcon.Info);
        }

        public static void Quit()
        {
            new Thread(delegate ()
            {
                var net = new Thread(delegate () { Instances.StopAll(45000); }) { IsBackground = true };
                net.Start();
                Mc.StopAndWait(45000);
                net.Join(60000);
                try { File.Delete(Paths.RuntimeFile); } catch { }
                Application.Exit();
                Environment.Exit(0);
            }) { IsBackground = true }.Start();
        }

        public static void OpenWindow(int port, string token)
        {
            string url = "http://127.0.0.1:" + port + "/?token=" + token;
            string[] edges = {
                @"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
                @"C:\Program Files\Microsoft\Edge\Application\msedge.exe" };
            foreach (var e in edges)
            {
                if (File.Exists(e))
                {
                    try { Process.Start(e, "--app=\"" + url + "\" --window-size=1500,920"); return; } catch { }
                }
            }
            Process.Start(url);
        }

        static string NewToken()
        {
            var b = new byte[24];
            using (var r = RandomNumberGenerator.Create()) r.GetBytes(b);
            return BitConverter.ToString(b).Replace("-", "").ToLowerInvariant();
        }

        public static void LoadSettings()
        {
            try { Cfg = File.Exists(Paths.SettingsFile) ? Json.Deserialize<Settings>(File.ReadAllText(Paths.SettingsFile)) : new Settings(); }
            catch { Cfg = new Settings(); }
            if (Cfg == null) Cfg = new Settings();
        }

        public static void SaveSettings()
        {
            File.WriteAllText(Paths.SettingsFile, Json.Serialize(Cfg));
        }
    }

    // ------------------------------------------------------------------ Serveur Minecraft
    class ServerManager
    {
        readonly object sync = new object();
        Process proc;
        StreamWriter stdin;
        public string State = "offline"; // offline | starting | online | stopping
        public DateTime StartedAt;
        bool stopRequested;
        bool restartAfterStop;
        readonly List<DateTime> crashes = new List<DateTime>();

        // Console
        readonly List<LogLine> lines = new List<LogLine>();
        long nextId = 1;
        public readonly object LogSync = new object();

        // CPU
        TimeSpan lastCpu; DateTime lastCpuAt; double cpu;

        public void Log(string text)
        {
            lock (LogSync)
            {
                lines.Add(new LogLine { id = nextId++, t = text, ts = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds() });
                if (lines.Count > 5000) lines.RemoveRange(0, lines.Count - 5000);
                Monitor.PulseAll(LogSync);
            }
        }

        public void Panel(string text) { Log("[Panel] " + text); }

        public List<LogLine> Since(long id)
        {
            lock (LogSync) { return lines.Where(l => l.id > id).ToList(); }
        }

        public List<LogLine> Tail(int n)
        {
            lock (LogSync) { return lines.Skip(Math.Max(0, lines.Count - n)).ToList(); }
        }

        public bool Running { get { lock (sync) return proc != null && !proc.HasExited; } }

        public static string JbrDir { get { return Path.Combine(Paths.Root, @"java\jbr-25"); } }
        public static bool UseJbr { get { return Program.Cfg.hotswap && Program.Cfg.debug && File.Exists(Path.Combine(JbrDir, @"bin\java.exe")); } }

        public string FindJava()
        {
            if (UseJbr) return Path.Combine(JbrDir, @"bin\java.exe");
            return FindJdk();
        }

        public static string FindJdk()
        {
            // Paper 26.x exige Java 25
            var jdk = Directory.GetDirectories(Path.Combine(Paths.Root, "java"), "jdk-25*").OrderByDescending(d => d).FirstOrDefault();
            if (jdk == null) throw new Exception("JDK 25 introuvable dans " + Path.Combine(Paths.Root, "java"));
            return Path.Combine(jdk, "bin", "java.exe");
        }

        public string BuildArgs()
        {
            var c = Program.Cfg;
            var sb = new StringBuilder();
            sb.Append("-Xms" + c.ramMb + "M -Xmx" + c.ramMb + "M ");
            // Flags recommandes par PaperMC pour 26.3 (Java 25)
            sb.Append("-XX:+UseCompactObjectHeaders -XX:+AlwaysPreTouch -XX:+DisableExplicitGC -XX:+PerfDisableSharedMem " +
                      "-XX:+UnlockExperimentalVMOptions -XX:+UseG1GC -XX:G1HeapRegionSize=8M -XX:G1MaxNewSizePercent=40 " +
                      "-XX:G1MixedGCCountTarget=4 -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1NewSizePercent=30 " +
                      "-XX:G1RSetUpdatingPauseTimePercent=5 -XX:G1ReservePercent=20 -XX:InitiatingHeapOccupancyPercent=15 " +
                      "-XX:MaxTenuringThreshold=1 -XX:SurvivorRatio=32 ");
            if (UseJbr) sb.Append("-XX:+AllowEnhancedClassRedefinition ");
            if (c.debug) sb.Append("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=127.0.0.1:" + c.debugPort + " ");
            sb.Append("-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -Dterminal.jline=false -Dterminal.ansi=true ");
            if (!string.IsNullOrWhiteSpace(c.extraArgs)) sb.Append(c.extraArgs.Trim() + " ");
            sb.Append("-jar paper.jar --nogui");
            return sb.ToString();
        }

        public void Start()
        {
            lock (sync)
            {
                if (proc != null && !proc.HasExited) return;
                Database.EnsureStarted(true);
                stopRequested = false;
                var psi = new ProcessStartInfo(FindJava(), BuildArgs())
                {
                    WorkingDirectory = Paths.Server,
                    UseShellExecute = false,
                    CreateNoWindow = true,
                    RedirectStandardInput = true,
                    RedirectStandardOutput = true,
                    RedirectStandardError = true,
                    StandardOutputEncoding = Encoding.UTF8,
                    StandardErrorEncoding = Encoding.UTF8
                };
                try { File.Delete(Paths.StatsFile); } catch { }
                Panel("Demarrage du serveur (" + Program.Cfg.ramMb + " Mo de RAM)...");
                proc = new Process { StartInfo = psi, EnableRaisingEvents = true };
                proc.OutputDataReceived += OnOut;
                proc.ErrorDataReceived += OnOut;
                proc.Exited += OnExit;
                proc.Start();
                stdin = new StreamWriter(proc.StandardInput.BaseStream, new UTF8Encoding(false)) { AutoFlush = true, NewLine = "\n" };
                proc.BeginOutputReadLine();
                proc.BeginErrorReadLine();
                State = "starting";
                StartedAt = DateTime.Now;
                lastCpu = TimeSpan.Zero; lastCpuAt = DateTime.Now; cpu = 0;
            }
        }

        void OnOut(object s, DataReceivedEventArgs e)
        {
            if (e.Data == null) return;
            Log(e.Data);
            if (State == "starting" && e.Data.Contains("Done (") && e.Data.Contains("For help")) State = "online";
        }

        void OnExit(object s, EventArgs e)
        {
            int code = 0;
            try { code = proc.ExitCode; } catch { }
            bool crashed;
            lock (sync)
            {
                crashed = !stopRequested;
                State = "offline";
            }
            Panel("Serveur arrete (code " + code + ").");
            if (restartAfterStop) { restartAfterStop = false; Thread.Sleep(1000); Start(); return; }
            if (crashed && Program.Cfg.autoRestart)
            {
                crashes.RemoveAll(d => (DateTime.Now - d).TotalMinutes > 5);
                crashes.Add(DateTime.Now);
                if (crashes.Count <= 3)
                {
                    Panel("Arret inattendu : redemarrage automatique dans 3 s...");
                    Thread.Sleep(3000);
                    Start();
                }
                else Panel("Trop de crashs en 5 minutes : redemarrage automatique suspendu.");
            }
        }

        public bool Send(string cmd)
        {
            lock (sync)
            {
                if (proc == null || proc.HasExited || stdin == null) return false;
                if (cmd.Trim().Equals("stop", StringComparison.OrdinalIgnoreCase)) { stopRequested = true; State = "stopping"; }
                stdin.WriteLine(cmd);
                return true;
            }
        }

        public void Stop()
        {
            lock (sync)
            {
                if (proc == null || proc.HasExited) return;
                stopRequested = true;
                State = "stopping";
            }
            Send("stop");
            var p = proc;
            new Thread(delegate ()
            {
                if (!p.WaitForExit(60000)) { Panel("Le serveur ne repond pas : arret force."); Kill(); }
            }) { IsBackground = true }.Start();
        }

        public void Restart()
        {
            if (!Running) { Start(); return; }
            restartAfterStop = true;
            Stop();
        }

        public void Kill()
        {
            lock (sync)
            {
                if (proc == null || proc.HasExited) return;
                stopRequested = true;
                try { proc.Kill(); } catch { }
            }
            Panel("Processus tue.");
        }

        public void StopAndWait(int ms)
        {
            if (!Running) return;
            Stop();
            var p = proc;
            if (p != null && !p.WaitForExit(ms)) Kill();
        }

        public Dictionary<string, object> Status()
        {
            var d = new Dictionary<string, object>();
            d["state"] = State;
            long ram = 0; int pid = 0;
            lock (sync)
            {
                if (proc != null && !proc.HasExited)
                {
                    try
                    {
                        proc.Refresh();
                        pid = proc.Id;
                        ram = proc.WorkingSet64;
                        var now = DateTime.Now;
                        var t = proc.TotalProcessorTime;
                        double el = (now - lastCpuAt).TotalMilliseconds;
                        if (el > 500 && lastCpu != TimeSpan.Zero) cpu = (t - lastCpu).TotalMilliseconds / el / Environment.ProcessorCount * 100.0;
                        if (el > 500 || lastCpu == TimeSpan.Zero) { lastCpu = t; lastCpuAt = now; }
                    }
                    catch { }
                }
                else cpu = 0;
            }
            d["pid"] = pid;
            d["ramMb"] = ram / 1048576;
            d["ramMaxMb"] = Program.Cfg.ramMb;
            d["cpu"] = Math.Round(cpu, 1);
            d["cores"] = Environment.ProcessorCount;
            d["uptime"] = State == "offline" ? 0 : (long)(DateTime.Now - StartedAt).TotalSeconds;
            d["debug"] = Program.Cfg.debug;
            d["debugPort"] = Program.Cfg.debugPort;
            d["hotswap"] = UseJbr;
            d["lastLog"] = nextId - 1;
            return d;
        }
    }

    // ------------------------------------------------------------------ Instances du reseau (proxy Velocity, mapdev...)
    // Processus annexes au serveur principal : console, commandes, demarrage/arret. Pas de redemarrage auto.
    class Instance
    {
        public string Id, Name, Role, Dir, Jar, Memory, StopCmd;
        public int Port;
        readonly object sync = new object();
        Process proc;
        StreamWriter stdin;
        public string State = "offline"; // offline | starting | online | stopping (+ external dans Status)
        DateTime startedAt;
        bool stopRequested, restartAfterStop;
        readonly List<LogLine> lines = new List<LogLine>();
        long nextId = 1;
        TimeSpan lastCpu; DateTime lastCpuAt; double cpu;

        public void Log(string text)
        {
            lock (lines)
            {
                lines.Add(new LogLine { id = nextId++, t = text, ts = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds() });
                if (lines.Count > 3000) lines.RemoveRange(0, lines.Count - 3000);
            }
        }
        void Panel(string text) { Log("[Panel] " + text); }

        public List<LogLine> Since(long id)
        {
            lock (lines) { return id > 0 ? lines.Where(l => l.id > id).ToList() : lines.Skip(Math.Max(0, lines.Count - 1500)).ToList(); }
        }

        public bool Running { get { lock (sync) return proc != null && !proc.HasExited; } }

        // Lance hors du panel (start-network.ps1) : le port ecoute mais ce n'est pas notre processus.
        public bool External { get { return !Running && PortListening(Port); } }

        public static bool PortListening(int port)
        {
            try { return System.Net.NetworkInformation.IPGlobalProperties.GetIPGlobalProperties().GetActiveTcpListeners().Any(e => e.Port == port); }
            catch { return false; }
        }

        public void Start()
        {
            lock (sync)
            {
                if (proc != null && !proc.HasExited) return;
                if (PortListening(Port)) throw new ApiError(409, Name + " tourne deja hors du panel (fenetre start-network). Arrete-le d'abord dans sa fenetre.");
                if (!File.Exists(Path.Combine(Dir, Jar))) throw new ApiError(404, Jar + " introuvable dans " + Dir);
                Database.EnsureStarted(true);
                stopRequested = false;
                string args = Memory + " -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -Dterminal.jline=false -Dterminal.ansi=true -jar " + Jar + (Jar == "paper.jar" ? " --nogui" : "");
                var psi = new ProcessStartInfo(ServerManager.FindJdk(), args)
                {
                    WorkingDirectory = Dir,
                    UseShellExecute = false,
                    CreateNoWindow = true,
                    RedirectStandardInput = true,
                    RedirectStandardOutput = true,
                    RedirectStandardError = true,
                    StandardOutputEncoding = Encoding.UTF8,
                    StandardErrorEncoding = Encoding.UTF8
                };
                Panel("Demarrage de " + Name + "...");
                proc = new Process { StartInfo = psi, EnableRaisingEvents = true };
                proc.OutputDataReceived += OnOut;
                proc.ErrorDataReceived += OnOut;
                proc.Exited += OnExit;
                proc.Start();
                stdin = new StreamWriter(proc.StandardInput.BaseStream, new UTF8Encoding(false)) { AutoFlush = true, NewLine = "\n" };
                proc.BeginOutputReadLine();
                proc.BeginErrorReadLine();
                State = "starting";
                startedAt = DateTime.Now;
                lastCpu = TimeSpan.Zero; lastCpuAt = DateTime.Now; cpu = 0;
            }
        }

        void OnOut(object s, DataReceivedEventArgs e)
        {
            if (e.Data == null) return;
            Log(e.Data);
            if (State == "starting" && e.Data.Contains("Done (")) State = "online";
        }

        void OnExit(object s, EventArgs e)
        {
            int code = 0;
            try { code = ((Process)s).ExitCode; } catch { }
            bool crashed;
            lock (sync) { crashed = !stopRequested; State = "offline"; }
            Panel(Name + " arrete (code " + code + ")" + (crashed ? " : arret inattendu." : "."));
            if (restartAfterStop) { restartAfterStop = false; Thread.Sleep(1000); try { Start(); } catch (Exception ex) { Panel(ex.Message); } }
        }

        public bool Send(string cmd)
        {
            lock (sync)
            {
                if (proc == null || proc.HasExited || stdin == null) return false;
                if (cmd.Trim().Equals(StopCmd, StringComparison.OrdinalIgnoreCase) || cmd.Trim().Equals("stop", StringComparison.OrdinalIgnoreCase)) { stopRequested = true; State = "stopping"; }
                Log("> " + cmd);
                stdin.WriteLine(cmd);
                return true;
            }
        }

        public void Stop()
        {
            Process p;
            lock (sync)
            {
                if (proc == null || proc.HasExited) return;
                stopRequested = true;
                State = "stopping";
                p = proc;
            }
            Send(StopCmd);
            new Thread(delegate ()
            {
                if (!p.WaitForExit(45000)) { Panel(Name + " ne repond pas : arret force."); Kill(); }
            }) { IsBackground = true }.Start();
        }

        public void Restart()
        {
            if (!Running) { Start(); return; }
            restartAfterStop = true;
            Stop();
        }

        public void Kill()
        {
            lock (sync)
            {
                if (proc == null || proc.HasExited) return;
                stopRequested = true;
                try { proc.Kill(); } catch { }
            }
            Panel(Name + " : processus tue.");
        }

        public void StopAndWait(int ms)
        {
            if (!Running) return;
            Process p; lock (sync) p = proc;
            Stop();
            if (p != null && !p.WaitForExit(ms)) Kill();
        }

        public Dictionary<string, object> Status()
        {
            var d = new Dictionary<string, object>();
            long ram = 0; int pid = 0;
            bool running;
            lock (sync)
            {
                running = proc != null && !proc.HasExited;
                if (running)
                {
                    try
                    {
                        proc.Refresh();
                        pid = proc.Id;
                        ram = proc.WorkingSet64;
                        var now = DateTime.Now;
                        var t = proc.TotalProcessorTime;
                        double el = (now - lastCpuAt).TotalMilliseconds;
                        if (el > 500 && lastCpu != TimeSpan.Zero) cpu = (t - lastCpu).TotalMilliseconds / el / Environment.ProcessorCount * 100.0;
                        if (el > 500 || lastCpu == TimeSpan.Zero) { lastCpu = t; lastCpuAt = now; }
                    }
                    catch { }
                }
                else cpu = 0;
            }
            d["id"] = Id; d["name"] = Name; d["role"] = Role; d["port"] = Port; d["kind"] = Jar == "paper.jar" ? "paper" : "velocity"; d["dir"] = Dir; d["memory"] = Memory;
            d["state"] = running ? State : (PortListening(Port) ? "external" : "offline");
            d["pid"] = pid;
            d["ramMb"] = ram / 1048576;
            d["cpu"] = Math.Round(cpu, 1);
            d["uptime"] = running ? (long)(DateTime.Now - startedAt).TotalSeconds : 0;
            lock (lines) d["lastLog"] = nextId - 1;
            return d;
        }
    }

    // Liste construite comme start-network.ps1 : le proxy + chaque dossier de instances\ qui a paper.jar et server.properties.
    static class Instances
    {
        static readonly List<Instance> list = new List<Instance>();

        public static List<Instance> All { get { Refresh(); lock (list) return list.ToList(); } }

        static void Refresh()
        {
            lock (list)
            {
                if (!list.Any(i => i.Id == "velocity"))
                    list.Insert(0, new Instance { Id = "velocity", Name = "Velocity", Role = "Proxy", Dir = Path.Combine(Paths.Root, "proxy"), Jar = "velocity.jar",
                                                  Memory = "-Xms128M -Xmx256M", StopCmd = "end", Port = 25565 });
                var seen = new HashSet<string> { "velocity" };
                var root = Path.Combine(Paths.Root, "instances");
                if (Directory.Exists(root))
                {
                    foreach (var dir in Directory.GetDirectories(root).OrderBy(d => d))
                    {
                        string props = Path.Combine(dir, "server.properties");
                        if (!File.Exists(Path.Combine(dir, "paper.jar")) || !File.Exists(props)) continue;
                        int port = 0; string motd = "";
                        try
                        {
                            foreach (var line in File.ReadAllLines(props))
                            {
                                if (line.StartsWith("server-port=")) int.TryParse(line.Substring(12).Trim(), out port);
                                else if (line.StartsWith("motd=")) motd = line.Substring(5);
                            }
                        }
                        catch { }
                        if (port <= 0) continue;
                        motd = System.Text.RegularExpressions.Regex.Replace(motd.Replace("\\u00A7", "§"), "§.", "").Trim();
                        string id = Path.GetFileName(dir);
                        seen.Add(id);
                        var inst = list.FirstOrDefault(i => i.Id == id);
                        if (inst == null)
                            list.Add(new Instance { Id = id, Name = id, Role = motd.Length > 0 ? motd : "Instance Paper", Dir = dir, Jar = "paper.jar",
                                                    Memory = "-Xms256M -Xmx1G", StopCmd = "stop", Port = port });
                        else if (!inst.Running) { inst.Port = port; inst.Role = motd.Length > 0 ? motd : inst.Role; }
                    }
                }
                list.RemoveAll(i => !seen.Contains(i.Id) && !i.Running);
            }
        }

        public static Instance Get(string id)
        {
            var i = All.FirstOrDefault(x => x.Id == id);
            if (i == null) throw new ApiError(404, "Instance inconnue : " + id);
            return i;
        }

        public static void StopAll(int ms)
        {
            var ts = All.Where(i => i.Running).Select(i => { var t = new Thread(delegate () { i.StopAndWait(ms); }) { IsBackground = true }; t.Start(); return t; }).ToList();
            foreach (var t in ts) t.Join(ms + 5000);
        }
    }

    // ------------------------------------------------------------------ Hot deploy de plugins
    static class DevWatcher
    {
        static FileSystemWatcher watcher;
        static readonly Dictionary<string, DateTime> pending = new Dictionary<string, DateTime>();
        static System.Threading.Timer timer;

        public static void Apply()
        {
            if (watcher != null) { watcher.EnableRaisingEvents = false; watcher.Dispose(); watcher = null; }
            var p = Program.Cfg.watchPath;
            if (string.IsNullOrWhiteSpace(p) || !Directory.Exists(p)) return;
            watcher = new FileSystemWatcher(p, "*.jar") { IncludeSubdirectories = false, NotifyFilter = NotifyFilters.LastWrite | NotifyFilters.FileName | NotifyFilters.Size };
            FileSystemEventHandler h = delegate (object s, FileSystemEventArgs e) { Queue(e.FullPath); };
            watcher.Changed += h; watcher.Created += h;
            watcher.Renamed += delegate (object s, RenamedEventArgs e) { Queue(e.FullPath); };
            watcher.EnableRaisingEvents = true;
            if (timer == null) timer = new System.Threading.Timer(Tick, null, 1000, 1000);
            Program.Mc.Panel("Surveillance du dossier de build : " + p);
        }

        static void Queue(string path)
        {
            var n = Path.GetFileName(path).ToLowerInvariant();
            if (n.StartsWith("original-") || n.EndsWith("-sources.jar") || n.EndsWith("-javadoc.jar")) return;
            lock (pending) pending[path] = DateTime.Now;
        }

        static void Tick(object o)
        {
            List<string> ready;
            lock (pending)
            {
                ready = pending.Where(kv => (DateTime.Now - kv.Value).TotalMilliseconds > 1500).Select(kv => kv.Key).ToList();
                foreach (var r in ready) pending.Remove(r);
            }
            if (ready.Count == 0) return;
            DeployFiles(ready);
        }

        public static List<string> DeployAll()
        {
            var p = Program.Cfg.watchPath;
            if (string.IsNullOrWhiteSpace(p) || !Directory.Exists(p)) throw new Exception("Dossier de build non configure ou introuvable.");
            var jars = Directory.GetFiles(p, "*.jar").Where(f =>
            {
                var n = Path.GetFileName(f).ToLowerInvariant();
                return !n.StartsWith("original-") && !n.EndsWith("-sources.jar") && !n.EndsWith("-javadoc.jar");
            }).ToList();
            DeployFiles(jars);
            return jars.Select(Path.GetFileName).ToList();
        }

        static void DeployFiles(List<string> files)
        {
            int ok = 0;
            foreach (var f in files)
            {
                if (!File.Exists(f)) continue;
                for (int i = 0; i < 5; i++)
                {
                    try { File.Copy(f, Path.Combine(Paths.Server, "plugins", Path.GetFileName(f)), true); ok++; Program.Mc.Panel("Plugin deploye : " + Path.GetFileName(f)); break; }
                    catch { Thread.Sleep(700); }
                }
            }
            if (ok > 0 && Program.Cfg.autoDeploy && Program.Mc.Running)
            {
                Program.Mc.Panel("Redemarrage pour charger le plugin...");
                Program.Mc.Restart();
            }
        }
    }

    // ------------------------------------------------------------------ HTTP
    class HttpServer
    {
        HttpListener listener;

        public void Start(int port)
        {
            listener = new HttpListener();
            listener.Prefixes.Add("http://127.0.0.1:" + port + "/");
            listener.Prefixes.Add("http://localhost:" + port + "/");
            listener.Start();
            new Thread(Loop) { IsBackground = true }.Start();
        }

        void Loop()
        {
            while (listener.IsListening)
            {
                HttpListenerContext ctx;
                try { ctx = listener.GetContext(); } catch { return; }
                ThreadPool.QueueUserWorkItem(delegate { Handle(ctx); });
            }
        }

        static readonly Dictionary<string, string> Mime = new Dictionary<string, string> {
            { ".html", "text/html; charset=utf-8" }, { ".js", "application/javascript; charset=utf-8" },
            { ".css", "text/css; charset=utf-8" }, { ".svg", "image/svg+xml" }, { ".png", "image/png" }, { ".ico", "image/x-icon" }, { ".woff2", "font/woff2" } };

        void Handle(HttpListenerContext ctx)
        {
            var req = ctx.Request; var res = ctx.Response;
            try
            {
                string path = req.Url.AbsolutePath;
                if (!path.StartsWith("/api/"))
                {
                    if (path == "/") path = "/index.html";
                    string f = Path.GetFullPath(Path.Combine(Paths.Web, path.TrimStart('/').Replace('/', '\\')));
                    if (!f.StartsWith(Paths.Web, StringComparison.OrdinalIgnoreCase) || !File.Exists(f)) { Send(res, 404, "text/plain", "404"); return; }
                    string mt; if (!Mime.TryGetValue(Path.GetExtension(f).ToLowerInvariant(), out mt)) mt = "application/octet-stream";
                    res.Headers["Cache-Control"] = "no-store";
                    SendBytes(res, 200, mt, File.ReadAllBytes(f));
                    return;
                }

                string tok = req.Headers["X-Token"] ?? req.QueryString["token"];
                if (tok != Program.Token) { Json(res, 401, new { error = "Token invalide. Rouvre le panel depuis l'icone." }); return; }

                Api.Route(ctx, path.Substring(5), req.HttpMethod);
            }
            catch (ApiError e) { TryJson(res, e.Code, new { error = e.Message }); }
            catch (HttpListenerException) { }
            catch (Exception e) { TryJson(res, 500, new { error = e.Message }); }
            finally { try { res.Close(); } catch { } }
        }

        static void TryJson(HttpListenerResponse res, int code, object o) { try { Json(res, code, o); } catch { } }

        public static void Json(HttpListenerResponse res, int code, object o)
        {
            Send(res, code, "application/json; charset=utf-8", Program.Json.Serialize(o));
        }

        public static void Send(HttpListenerResponse res, int code, string type, string body)
        {
            SendBytes(res, code, type, Encoding.UTF8.GetBytes(body));
        }

        public static void SendBytes(HttpListenerResponse res, int code, string type, byte[] b)
        {
            res.StatusCode = code;
            res.ContentType = type;
            res.ContentLength64 = b.Length;
            res.OutputStream.Write(b, 0, b.Length);
        }
    }

    class ApiError : Exception
    {
        public int Code;
        public ApiError(int code, string msg) : base(msg) { Code = code; }
    }

    static class Api
    {
        static string Body(HttpListenerRequest r)
        {
            using (var sr = new StreamReader(r.InputStream, Encoding.UTF8)) return sr.ReadToEnd();
        }

        static Dictionary<string, object> JBody(HttpListenerRequest r)
        {
            var s = Body(r);
            if (string.IsNullOrWhiteSpace(s)) return new Dictionary<string, object>();
            return Program.Json.Deserialize<Dictionary<string, object>>(s);
        }

        static string Str(Dictionary<string, object> d, string k)
        {
            object v; return d.TryGetValue(k, out v) && v != null ? Convert.ToString(v) : null;
        }

        // Chemin relatif au dossier du serveur, sans possibilite d'en sortir.
        static string Safe(string rel)
        {
            rel = (rel ?? "").Replace('/', '\\').TrimStart('\\');
            string full = Path.GetFullPath(Path.Combine(Paths.Server, rel));
            string root = Path.GetFullPath(Paths.Server);
            if (!full.Equals(root, StringComparison.OrdinalIgnoreCase) && !full.StartsWith(root + "\\", StringComparison.OrdinalIgnoreCase))
                throw new ApiError(400, "Chemin interdit");
            return full;
        }

        static string Rel(string full)
        {
            string root = Path.GetFullPath(Paths.Server);
            return full.Length <= root.Length ? "" : full.Substring(root.Length + 1).Replace('\\', '/');
        }

        static void RequireStopped()
        {
            if (Program.Mc.Running) throw new ApiError(409, "Arrete d'abord le serveur.");
        }

        static object Ok() { return new { ok = true }; }

        public static void Route(HttpListenerContext ctx, string route, string method)
        {
            var req = ctx.Request; var res = ctx.Response; var q = req.QueryString;
            var mc = Program.Mc;
            object result = null;

            switch (method + " " + route)
            {
                case "GET status": result = mc.Status(); break;

                case "GET stats":
                    string stats = null;
                    if (mc.Running && File.Exists(Paths.StatsFile))
                    {
                        try
                        {
                            using (var fs = new FileStream(Paths.StatsFile, FileMode.Open, FileAccess.Read, FileShare.ReadWrite | FileShare.Delete))
                            using (var sr = new StreamReader(fs, Encoding.UTF8)) stats = sr.ReadToEnd();
                        }
                        catch { }
                    }
                    HttpServer.Send(res, 200, "application/json; charset=utf-8", string.IsNullOrEmpty(stats) ? "null" : stats);
                    return;

                case "POST power":
                    {
                        var a = Str(JBody(req), "action");
                        if (a == "start") mc.Start();
                        else if (a == "stop") mc.Stop();
                        else if (a == "restart") mc.Restart();
                        else if (a == "kill") mc.Kill();
                        else throw new ApiError(400, "Action inconnue");
                        result = Ok();
                        break;
                    }

                case "POST command":
                    {
                        var c = Str(JBody(req), "cmd");
                        if (string.IsNullOrWhiteSpace(c)) throw new ApiError(400, "Commande vide");
                        if (!mc.Send(c.TrimStart('/'))) throw new ApiError(409, "Le serveur n'est pas lance.");
                        result = Ok();
                        break;
                    }

                case "GET console":
                    {
                        long since; long.TryParse(q["since"], out since);
                        result = since > 0 ? mc.Since(since) : mc.Tail(1500);
                        break;
                    }

                case "GET console/stream":
                    Stream(ctx);
                    return;

                // ---- Instances du reseau
                case "GET instances":
                    result = Instances.All.Select(i => (object)i.Status()).ToList();
                    break;

                case "POST instances/power":
                    {
                        var inst = Instances.Get(q["id"]);
                        var a = Str(JBody(req), "action");
                        if (a == "start") inst.Start();
                        else if (a == "stop") inst.Stop();
                        else if (a == "restart") inst.Restart();
                        else if (a == "kill") inst.Kill();
                        else throw new ApiError(400, "Action inconnue");
                        result = Ok();
                        break;
                    }

                case "POST instances/command":
                    {
                        var inst = Instances.Get(q["id"]);
                        var c = Str(JBody(req), "cmd");
                        if (string.IsNullOrWhiteSpace(c)) throw new ApiError(400, "Commande vide");
                        if (!inst.Send(c.TrimStart('/'))) throw new ApiError(409, inst.Name + " n'est pas lance depuis le panel.");
                        result = Ok();
                        break;
                    }

                case "GET instances/console":
                    {
                        long since; long.TryParse(q["since"], out since);
                        result = Instances.Get(q["id"]).Since(since);
                        break;
                    }

                // ---- Fichiers
                case "GET files":
                    {
                        string dir = Safe(q["path"]);
                        if (!Directory.Exists(dir)) throw new ApiError(404, "Dossier introuvable");
                        var list = new List<object>();
                        foreach (var d in new DirectoryInfo(dir).GetDirectories().OrderBy(x => x.Name))
                            list.Add(new { name = d.Name, dir = true, size = 0L, mtime = d.LastWriteTime.ToString("yyyy-MM-dd HH:mm"), path = Rel(d.FullName) });
                        foreach (var f in new DirectoryInfo(dir).GetFiles().OrderBy(x => x.Name))
                            list.Add(new { name = f.Name, dir = false, size = f.Length, mtime = f.LastWriteTime.ToString("yyyy-MM-dd HH:mm"), path = Rel(f.FullName) });
                        result = new { path = Rel(dir), items = list };
                        break;
                    }

                case "GET file":
                    {
                        string f = Safe(q["path"]);
                        if (!File.Exists(f)) throw new ApiError(404, "Fichier introuvable");
                        if (new FileInfo(f).Length > 3 * 1024 * 1024) throw new ApiError(413, "Fichier trop gros pour l'editeur (> 3 Mo)");
                        using (var fs = new FileStream(f, FileMode.Open, FileAccess.Read, FileShare.ReadWrite | FileShare.Delete))
                        using (var sr = new StreamReader(fs, Encoding.UTF8, true))
                            result = new { path = Rel(f), content = sr.ReadToEnd() };
                        break;
                    }

                case "PUT file":
                    {
                        string f = Safe(q["path"]);
                        File.WriteAllText(f, Body(req), new UTF8Encoding(false));
                        result = Ok();
                        break;
                    }

                case "POST file/delete":
                    {
                        string f = Safe(q["path"]);
                        if (f.Equals(Path.GetFullPath(Paths.Server), StringComparison.OrdinalIgnoreCase)) throw new ApiError(400, "Non.");
                        if (Directory.Exists(f)) Directory.Delete(f, true); else if (File.Exists(f)) File.Delete(f);
                        result = Ok();
                        break;
                    }

                case "POST file/rename":
                    {
                        var b = JBody(req);
                        string from = Safe(Str(b, "from")), to = Safe(Str(b, "to"));
                        if (Directory.Exists(from)) Directory.Move(from, to); else File.Move(from, to);
                        result = Ok();
                        break;
                    }

                case "POST file/mkdir":
                    Directory.CreateDirectory(Safe(q["path"]));
                    result = Ok();
                    break;

                case "POST upload":
                    {
                        string dir = Safe(q["path"]);
                        string name = Path.GetFileName(q["name"] ?? "");
                        if (string.IsNullOrEmpty(name)) throw new ApiError(400, "Nom manquant");
                        string dest = Safe(Path.Combine(Rel(dir), name));
                        using (var fs = File.Create(dest)) req.InputStream.CopyTo(fs);
                        result = Ok();
                        break;
                    }

                case "GET download":
                    {
                        string f = Safe(q["path"]);
                        if (!File.Exists(f)) throw new ApiError(404, "Fichier introuvable");
                        res.Headers["Content-Disposition"] = "attachment; filename=\"" + Path.GetFileName(f) + "\"";
                        using (var fs = new FileStream(f, FileMode.Open, FileAccess.Read, FileShare.ReadWrite | FileShare.Delete))
                        {
                            res.StatusCode = 200; res.ContentType = "application/octet-stream"; res.ContentLength64 = fs.Length;
                            fs.CopyTo(res.OutputStream);
                        }
                        return;
                    }

                // ---- Plugins
                case "GET plugins":
                    {
                        var dir = Path.Combine(Paths.Server, "plugins");
                        var list = new List<object>();
                        foreach (var f in new DirectoryInfo(dir).GetFiles().Where(x => x.Name.EndsWith(".jar", StringComparison.OrdinalIgnoreCase) || x.Name.EndsWith(".jar.disabled", StringComparison.OrdinalIgnoreCase)).OrderBy(x => x.Name))
                            list.Add(new { file = f.Name, disabled = f.Name.EndsWith(".disabled"), size = f.Length, mtime = f.LastWriteTime.ToString("yyyy-MM-dd HH:mm") });
                        result = list;
                        break;
                    }

                case "POST plugins/toggle":
                    {
                        string f = Safe("plugins/" + Path.GetFileName(q["file"] ?? ""));
                        if (!File.Exists(f)) throw new ApiError(404, "Plugin introuvable");
                        string to = f.EndsWith(".disabled") ? f.Substring(0, f.Length - 9) : f + ".disabled";
                        File.Move(f, to);
                        result = new { ok = true, file = Path.GetFileName(to) };
                        break;
                    }

                case "POST plugins/delete":
                    {
                        string f = Safe("plugins/" + Path.GetFileName(q["file"] ?? ""));
                        if (File.Exists(f)) File.Delete(f);
                        result = Ok();
                        break;
                    }

                // ---- server.properties
                case "GET properties":
                    {
                        var list = new List<object>();
                        foreach (var line in File.ReadAllLines(Path.Combine(Paths.Server, "server.properties")))
                        {
                            if (line.StartsWith("#") || !line.Contains("=")) continue;
                            int i = line.IndexOf('=');
                            list.Add(new { key = line.Substring(0, i), value = Unescape(line.Substring(i + 1)) });
                        }
                        result = list;
                        break;
                    }

                case "PUT properties":
                    {
                        var vals = JBody(req);
                        string file = Path.Combine(Paths.Server, "server.properties");
                        var lines = File.ReadAllLines(file).ToList();
                        var seen = new HashSet<string>();
                        for (int i = 0; i < lines.Count; i++)
                        {
                            if (lines[i].StartsWith("#") || !lines[i].Contains("=")) continue;
                            string k = lines[i].Substring(0, lines[i].IndexOf('='));
                            if (vals.ContainsKey(k)) { lines[i] = k + "=" + Escape(Convert.ToString(vals[k])); seen.Add(k); }
                        }
                        foreach (var kv in vals) if (!seen.Contains(kv.Key)) lines.Add(kv.Key + "=" + Escape(Convert.ToString(kv.Value)));
                        File.WriteAllLines(file, lines, new UTF8Encoding(false));
                        result = Ok();
                        break;
                    }

                // ---- Sauvegardes & monde
                case "GET backups":
                    {
                        Directory.CreateDirectory(Paths.Backups);
                        result = new DirectoryInfo(Paths.Backups).GetFiles("*.zip").OrderByDescending(f => f.LastWriteTime)
                            .Select(f => (object)new { name = f.Name, size = f.Length, mtime = f.LastWriteTime.ToString("yyyy-MM-dd HH:mm") }).ToList();
                        break;
                    }

                case "POST backups/create":
                    {
                        var label = Str(JBody(req), "label") ?? "";
                        label = new string(label.Where(c => char.IsLetterOrDigit(c) || c == '-' || c == '_').ToArray());
                        bool running = mc.Running;
                        if (running) { mc.Send("save-off"); mc.Send("save-all flush"); Thread.Sleep(3000); }
                        try
                        {
                            Directory.CreateDirectory(Paths.Backups);
                            string name = "world_" + DateTime.Now.ToString("yyyy-MM-dd_HH-mm-ss") + (label.Length > 0 ? "_" + label : "") + ".zip";
                            ZipDir(Path.Combine(Paths.Server, "world"), Path.Combine(Paths.Backups, name));
                            mc.Panel("Sauvegarde creee : " + name);
                            result = new { ok = true, name = name };
                        }
                        finally { if (running) mc.Send("save-on"); }
                        break;
                    }

                case "POST backups/restore":
                    {
                        RequireStopped();
                        string zip = Path.Combine(Paths.Backups, Path.GetFileName(q["name"] ?? ""));
                        if (!File.Exists(zip)) throw new ApiError(404, "Sauvegarde introuvable");
                        string w = Path.Combine(Paths.Server, "world");
                        if (Directory.Exists(w)) Directory.Delete(w, true);
                        ZipFile.ExtractToDirectory(zip, w);
                        mc.Panel("Sauvegarde restauree : " + Path.GetFileName(zip));
                        result = Ok();
                        break;
                    }

                case "POST backups/delete":
                    {
                        string zip = Path.Combine(Paths.Backups, Path.GetFileName(q["name"] ?? ""));
                        if (File.Exists(zip)) File.Delete(zip);
                        result = Ok();
                        break;
                    }

                case "GET backups/download":
                    {
                        string zip = Path.Combine(Paths.Backups, Path.GetFileName(q["name"] ?? ""));
                        if (!File.Exists(zip)) throw new ApiError(404, "Sauvegarde introuvable");
                        res.Headers["Content-Disposition"] = "attachment; filename=\"" + Path.GetFileName(zip) + "\"";
                        using (var fs = File.OpenRead(zip)) { res.StatusCode = 200; res.ContentType = "application/zip"; res.ContentLength64 = fs.Length; fs.CopyTo(res.OutputStream); }
                        return;
                    }

                case "POST world/reset":
                    {
                        RequireStopped();
                        if (!Directory.Exists(Paths.MapBackup)) throw new ApiError(404, "Map d'origine introuvable");
                        foreach (var n in new[] { "world", "world_nether", "world_the_end" })
                        {
                            string d = Path.Combine(Paths.Server, n);
                            if (Directory.Exists(d)) Directory.Delete(d, true);
                        }
                        CopyDir(Paths.MapBackup, Path.Combine(Paths.Server, "world"));
                        mc.Panel("Map GameLand d'origine restauree.");
                        result = Ok();
                        break;
                    }

                // ---- Reglages du panel
                case "GET settings": result = Program.Cfg; break;

                case "PUT settings":
                    {
                        var s = Program.Json.Deserialize<Settings>(Body(req));
                        if (s.ramMb < 512 || s.ramMb > 14336) throw new ApiError(400, "RAM entre 512 et 14336 Mo");
                        if (!string.IsNullOrWhiteSpace(s.watchPath) && !Directory.Exists(s.watchPath)) throw new ApiError(400, "Dossier de build introuvable : " + s.watchPath);
                        s.port = Program.Cfg.port;
                        Program.Cfg = s;
                        Program.SaveSettings();
                        DevWatcher.Apply();
                        result = Ok();
                        break;
                    }

                case "POST dev/deploy":
                    result = new { ok = true, files = DevWatcher.DeployAll() };
                    break;

                case "POST open":
                    {
                        string what = Str(JBody(req), "what");
                        string p = what == "server" ? Paths.Server : what == "backups" ? Paths.Backups : what == "plugins" ? Path.Combine(Paths.Server, "plugins")
                                 : what == "watch" ? Program.Cfg.watchPath
                                 : Instances.All.Where(i => i.Id == what).Select(i => i.Dir).FirstOrDefault();
                        if (string.IsNullOrEmpty(p) || !Directory.Exists(p)) throw new ApiError(404, "Dossier introuvable");
                        Process.Start("explorer.exe", "\"" + p + "\"");
                        result = Ok();
                        break;
                    }

                case "GET system":
                    {
                        var ci = new Microsoft.VisualBasic.Devices.ComputerInfo();
                        result = new
                        {
                            totalRamMb = (long)(ci.TotalPhysicalMemory / 1048576),
                            freeRamMb = (long)(ci.AvailablePhysicalMemory / 1048576),
                            cores = Environment.ProcessorCount,
                            os = ci.OSFullName,
                            serverDir = Paths.Server,
                            javaArgs = mc.BuildArgs(),
                            java = mc.FindJava(),
                            jbrInstalled = File.Exists(Path.Combine(ServerManager.JbrDir, @"bin\java.exe"))
                        };
                        break;
                    }

                case "POST quit":
                    HttpServer.Json(res, 200, Ok());
                    res.Close();
                    Program.Quit();
                    return;

                default:
                    throw new ApiError(404, "Route inconnue : " + method + " " + route);
            }
            HttpServer.Json(res, 200, result);
        }

        static void Stream(HttpListenerContext ctx)
        {
            var res = ctx.Response;
            res.StatusCode = 200;
            res.ContentType = "text/event-stream; charset=utf-8";
            res.Headers["Cache-Control"] = "no-cache";
            res.SendChunked = true;
            var mc = Program.Mc;
            long last; long.TryParse(ctx.Request.QueryString["since"], out last);
            var os = res.OutputStream;
            while (true)
            {
                List<LogLine> batch;
                lock (mc.LogSync)
                {
                    batch = mc.Since(last);
                    if (batch.Count == 0) { Monitor.Wait(mc.LogSync, 15000); batch = mc.Since(last); }
                }
                string payload = batch.Count > 0 ? "data: " + Program.Json.Serialize(batch) + "\n\n" : ": ping\n\n";
                if (batch.Count > 0) last = batch[batch.Count - 1].id;
                var b = Encoding.UTF8.GetBytes(payload);
                os.Write(b, 0, b.Length);
                os.Flush();
            }
        }

        static string Unescape(string v) { return v.Replace("\\:", ":").Replace("\\=", "=").Replace("\\\\", "\\"); }
        static string Escape(string v) { return (v ?? "").Replace("\\", "\\\\").Replace(":", "\\:").Replace("\n", ""); }

        static void ZipDir(string dir, string zip)
        {
            using (var fs = File.Create(zip))
            using (var za = new ZipArchive(fs, ZipArchiveMode.Create))
            {
                foreach (var f in Directory.GetFiles(dir, "*", SearchOption.AllDirectories))
                {
                    if (Path.GetFileName(f) == "session.lock") continue;
                    var e = za.CreateEntry(f.Substring(dir.Length + 1).Replace('\\', '/'), CompressionLevel.Fastest);
                    using (var src = new FileStream(f, FileMode.Open, FileAccess.Read, FileShare.ReadWrite | FileShare.Delete))
                    using (var dst = e.Open()) src.CopyTo(dst);
                }
            }
        }

        static void CopyDir(string src, string dst)
        {
            Directory.CreateDirectory(dst);
            foreach (var f in Directory.GetFiles(src)) File.Copy(f, Path.Combine(dst, Path.GetFileName(f)), true);
            foreach (var d in Directory.GetDirectories(src)) CopyDir(d, Path.Combine(dst, Path.GetFileName(d)));
        }
    }
}
