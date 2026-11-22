using System;
using System.Diagnostics;
using System.IO;
using System.Linq;

// Lanceur du serveur Minecraft de dev local (Paper 1.21.1 / Java 21).
// Recompiler : build-launcher.bat
class Launcher
{
    const string Root = @"C:\Users\Zazou\MinecraftDev";
    static string ServerDir { get { return Path.Combine(Root, "server"); } }

    static string FindJava()
    {
        string javaDir = Path.Combine(Root, "java");
        var jdk = Directory.GetDirectories(javaDir, "jdk-21*").OrderByDescending(d => d).FirstOrDefault();
        if (jdk == null) throw new Exception("JDK 21 introuvable dans " + javaDir);
        return Path.Combine(jdk, "bin", "java.exe");
    }

    static int Main()
    {
        Console.Title = "Minecraft Dev Server - Paper 1.21.1";
        while (true)
        {
            Console.Clear();
            Console.ForegroundColor = ConsoleColor.Cyan;
            Console.WriteLine("===========================================");
            Console.WriteLine("   SERVEUR DE DEV MINECRAFT  (Paper 1.21.1)");
            Console.WriteLine("===========================================");
            Console.ResetColor();
            Console.WriteLine("  Adresse en jeu : localhost");
            Console.WriteLine();
            Console.WriteLine("  [1] Demarrer le serveur");
            Console.WriteLine("  [2] Ouvrir le dossier plugins");
            Console.WriteLine("  [3] Ouvrir le dossier du serveur");
            Console.WriteLine("  [4] Remettre la map GameLand d'origine");
            Console.WriteLine("  [Q] Quitter");
            Console.WriteLine();
            Console.Write("  Choix : ");
            var key = Console.ReadKey(true).KeyChar.ToString().ToUpperInvariant();
            Console.WriteLine(key);

            try
            {
                switch (key)
                {
                    case "1": RunServer(); break;
                    case "2": Process.Start("explorer.exe", Path.Combine(ServerDir, "plugins")); break;
                    case "3": Process.Start("explorer.exe", ServerDir); break;
                    case "4": ResetWorld(); break;
                    case "Q": return 0;
                }
            }
            catch (Exception e)
            {
                Console.ForegroundColor = ConsoleColor.Red;
                Console.WriteLine("Erreur : " + e.Message);
                Console.ResetColor();
                Pause();
            }
        }
    }

    static void RunServer()
    {
        string java = FindJava();
        // Flags Aikar (recommandes par PaperMC), 4 Go de RAM.
        string args = "-Xms4G -Xmx4G -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 " +
                      "-XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch " +
                      "-XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M " +
                      "-XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 " +
                      "-XX:InitiatingHeapOccupancyPercent=15 -XX:G1MixedGCLiveThresholdPercent=90 " +
                      "-XX:G1RSetUpdatingPauseIntervalMillis=5000 -XX:SurvivorRatio=32 " +
                      "-XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1 " +
                      // Debug distant : attache IntelliJ sur le port 5005 pour poser des breakpoints dans tes plugins
                      "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=127.0.0.1:5005 " +
                      "-Dpaper.disableChannelLimit=true -Dfile.encoding=UTF-8 " +
                      "-jar paper.jar --nogui";

        while (true)
        {
            Console.Clear();
            Console.ForegroundColor = ConsoleColor.Green;
            Console.WriteLine(">> Demarrage du serveur... (tape 'stop' pour l'arreter proprement)");
            Console.WriteLine(">> Debug Java dispo sur localhost:5005");
            Console.ResetColor();

            var psi = new ProcessStartInfo(java, args)
            {
                WorkingDirectory = ServerDir,
                UseShellExecute = false
            };
            using (var p = Process.Start(psi)) { p.WaitForExit(); }

            Console.WriteLine();
            Console.ForegroundColor = ConsoleColor.Yellow;
            Console.Write(">> Serveur arrete. [R] Redemarrer  /  autre touche : menu ");
            Console.ResetColor();
            if (Console.ReadKey(true).KeyChar.ToString().ToUpperInvariant() != "R") return;
        }
    }

    static void ResetWorld()
    {
        string backup = Path.Combine(Root, @"maps\gameland-spawn");
        Console.Write("  Remettre la map GameLand d'origine (les modifs du monde seront perdues) ? (o/N) ");
        if (Console.ReadKey(true).KeyChar.ToString().ToLowerInvariant() != "o") return;
        if (!Directory.Exists(backup)) throw new Exception("Sauvegarde introuvable : " + backup);
        foreach (var w in new[] { "world", "world_nether", "world_the_end" })
        {
            string d = Path.Combine(ServerDir, w);
            if (Directory.Exists(d)) Directory.Delete(d, true);
        }
        CopyDir(backup, Path.Combine(ServerDir, "world"));
        Console.WriteLine("\n  Map GameLand restauree.");
        Pause();
    }

    static void CopyDir(string src, string dst)
    {
        Directory.CreateDirectory(dst);
        foreach (var f in Directory.GetFiles(src)) File.Copy(f, Path.Combine(dst, Path.GetFileName(f)), true);
        foreach (var d in Directory.GetDirectories(src)) CopyDir(d, Path.Combine(dst, Path.GetFileName(d)));
    }

    static void Pause()
    {
        Console.WriteLine("  (appuie sur une touche)");
        Console.ReadKey(true);
    }
}
