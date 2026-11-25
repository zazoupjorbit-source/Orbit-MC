package fr.orbit.tele;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tunnel https gratuit (Cloudflare) vers la page web du plugin, pour que les joueurs qui ne sont pas sur le PC du
 * serveur entendent le son des télés. Même principe que le relais des packs (OrbitPackRelay) : le plugin lance
 * {@code tools\cloudflared.exe}, lit l'adresse du moment dans sa sortie, vérifie chaque minute que le tunnel répond
 * et le relance s'il tombe. L'adresse change à chaque lancement : les liens donnés en jeu prennent toujours la bonne.
 */
final class Tunnel {

    private static final Pattern ADRESSE = Pattern.compile("https://[a-z0-9-]+\\.trycloudflare\\.com");

    private final Logger journal;
    private final Path dossier;
    private final int port;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private volatile Process processus;
    private volatile String adresse;
    private volatile boolean marche = true;
    private ScheduledExecutorService veille;
    private int echecs;

    /**
     * @param dossier dossier de données du plugin (on y note le numéro du processus pour le retrouver après un arrêt brutal)
     * @param port    port local de la page web
     */
    Tunnel(Logger journal, Path dossier, int port) {
        this.journal = journal;
        this.dossier = dossier;
        this.port = port;
    }

    void demarrer() {
        veille = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "OrbitTele-tunnel");
            t.setDaemon(true);
            return t;
        });
        veille.execute(this::lancer);
        veille.scheduleWithFixedDelay(this::verifier, 90, 60, TimeUnit.SECONDS);
    }

    void arreter() {
        marche = false;
        if (veille != null) {
            veille.shutdownNow();
        }
        couper();
    }

    /** Adresse publique du moment (https://….trycloudflare.com), ou null tant que le tunnel n'est pas prêt. */
    String adresse() {
        return adresse;
    }

    private static Path executable() {
        Path ici = Paths.get("").toAbsolutePath();
        for (String nom : List.of("cloudflared.exe", "cloudflared")) {
            for (Path p : List.of(ici.resolve("../tools/" + nom), ici.resolve("tools/" + nom))) {
                if (Files.isRegularFile(p)) {
                    return p.normalize();
                }
            }
        }
        return null;
    }

    private Path fichierPid() {
        return dossier.resolve("tunnel.pid");
    }

    /** Un tunnel resté ouvert après un arrêt brutal du serveur est fermé avant d'en lancer un autre. */
    private void fermerAncien() {
        try {
            Path f = fichierPid();
            if (!Files.isRegularFile(f)) {
                return;
            }
            long pid = Long.parseLong(Files.readString(f).trim());
            Files.deleteIfExists(f);
            ProcessHandle.of(pid).ifPresent(h -> {
                String commande = h.info().command().orElse("").toLowerCase();
                if (commande.contains("cloudflared")) {
                    h.descendants().forEach(ProcessHandle::destroyForcibly);
                    h.destroyForcibly();
                }
            });
        } catch (IOException | NumberFormatException ignored) {
            // fichier illisible : rien à fermer
        }
    }

    private synchronized void lancer() {
        if (!marche) {
            return;
        }
        couper();
        fermerAncien();
        Path exe = executable();
        if (exe == null) {
            journal.warning("cloudflared introuvable (dossier tools) : le son des télés reste réservé au PC du serveur.");
            return;
        }
        try {
            Process pr = new ProcessBuilder(exe.toString(), "tunnel", "--no-autoupdate", "--url", "http://127.0.0.1:" + port)
                    .redirectErrorStream(true).start();
            processus = pr;
            echecs = 0;
            Files.createDirectories(dossier);
            Files.writeString(fichierPid(), Long.toString(pr.pid()));
            Path sortie = exe.getParent().resolve("cloudflared-tele.log");
            Thread lecteur = new Thread(() -> {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(pr.getInputStream(), StandardCharsets.UTF_8));
                     PrintWriter out = new PrintWriter(Files.newBufferedWriter(sortie, StandardCharsets.UTF_8,
                             StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING), true)) {
                    String ligne;
                    while ((ligne = br.readLine()) != null) {
                        out.println(ligne);
                        Matcher m = ADRESSE.matcher(ligne);
                        if (m.find() && processus == pr && !m.group().equals(adresse)) {
                            adresse = m.group();
                            journal.info("Tunnel du son ouvert : " + adresse + " (page d'écoute des joueurs hors de ce PC).");
                        }
                    }
                } catch (IOException ignored) {
                    // cloudflared s'est arrêté
                }
            }, "OrbitTele-cloudflared");
            lecteur.setDaemon(true);
            lecteur.start();
        } catch (IOException e) {
            journal.log(Level.WARNING, "Impossible de lancer cloudflared : " + e.getMessage());
        }
    }

    private synchronized void couper() {
        Process pr = processus;
        processus = null;
        adresse = null;
        if (pr != null) {
            pr.descendants().forEach(ProcessHandle::destroyForcibly);
            pr.destroyForcibly();
            try {
                Files.deleteIfExists(fichierPid());
            } catch (IOException ignored) {
                // sans gravité
            }
        }
    }

    private void verifier() {
        if (!marche) {
            return;
        }
        try {
            Process pr = processus;
            if (pr == null || !pr.isAlive()) {
                if (executable() != null) {
                    journal.warning("Tunnel du son arrêté : relance.");
                    lancer();
                }
                return;
            }
            String base = adresse;
            if (base == null) {
                if (++echecs >= 3) {
                    lancer();
                }
                return;
            }
            try {
                HttpResponse<String> r = client.send(HttpRequest.newBuilder(URI.create(base + "/sante"))
                        .timeout(Duration.ofSeconds(15)).GET().build(), HttpResponse.BodyHandlers.ofString());
                if (r.statusCode() == 200) {
                    echecs = 0;
                    return;
                }
            } catch (IOException ignored) {
                // compté comme un échec
            }
            if (++echecs >= 3) {
                journal.warning("Tunnel du son injoignable 3 fois de suite : relance.");
                lancer();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            journal.log(Level.WARNING, "Surveillance du tunnel en erreur", e);
        }
    }
}
