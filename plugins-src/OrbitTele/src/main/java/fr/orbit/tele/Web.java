package fr.orbit.tele;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Page de diffusion : le navigateur capture l'écran, l'envoie image par image (JPEG) et le plugin l'affiche.
 * Tout passe par un jeton personnel donné en jeu par {@code /tele lien} ; sans jeton, seule la page elle-même répond.
 *
 * <p>Le son suit un second chemin (voir {@link Son}) : la page de diffusion le dépose sur {@code /son}, et la page
 * d'écoute de chaque joueur ({@code /ecoute}, jeton de {@code /tele son}) vient chercher celui de la télé dont il est proche.
 */
final class Web {

    private static final Logger JOURNAL = Logger.getLogger("OrbitTele");
    private static final int MAX = 3 * 1024 * 1024;
    private static final int MAX_SON = 256 * 1024;

    /** Ce que la page sert, en dehors des appels : chemin → { ressource du jar, type }. */
    private static final Map<String, String[]> FICHIERS = Map.of(
            "/", new String[]{"/web/page.html", "text/html; charset=utf-8"},
            "/ecoute", new String[]{"/web/ecoute.html", "text/html; charset=utf-8"},
            "/orbit.css", new String[]{"/web/orbit.css", "text/css; charset=utf-8"},
            "/travail.js", new String[]{"/web/travail.js", "text/javascript; charset=utf-8"},
            "/capture.js", new String[]{"/web/capture.js", "text/javascript; charset=utf-8"},
            "/polices/titre.ttf", new String[]{"/polices/Tektur-Medium.ttf", "font/ttf"},
            "/polices/texte.ttf", new String[]{"/polices/Outfit-Bold.ttf", "font/ttf"});

    /** Ce qu'une requête venue du tunnel public peut demander tant que la diffusion à distance est fermée : la page d'écoute, rien d'autre. */
    private static final Set<String> ECOUTE_SEULE = Set.of("/ecoute", "/ecoute/son", "/orbit.css", "/polices/titre.ttf", "/polices/texte.ttf", "/sante");

    /** Où se tient un joueur qui a une page d'écoute (relevé sur le fil du serveur). */
    record Place(String monde, double x, double y, double z) {
    }

    private final OrbitTele plugin;
    private final Map<String, String> jetons = new ConcurrentHashMap<>();
    /** Jetons d'écoute : ils ne donnent que le son, jamais le droit de diffuser. */
    private final Map<String, String> ecoutes = new ConcurrentHashMap<>();
    private final Map<String, Place> places = new ConcurrentHashMap<>();
    private final SecureRandom hasard = new SecureRandom();
    private HttpServer http;
    private ExecutorService fils;
    private int port;
    private double portee = 32, plein = 6;
    private volatile boolean diffusionDistante;

    Web(OrbitTele plugin) {
        this.plugin = plugin;
        ImageIO.setUseCache(false);
    }

    /** true = la page de diffusion répond aussi aux requêtes venues du tunnel (un admin partage son écran depuis un autre PC). */
    void diffusionDistante(boolean ouverte) {
        diffusionDistante = ouverte;
    }

    /** Une requête passée par le tunnel Cloudflare porte toujours ces en-têtes ; une requête faite sur ce PC, jamais. */
    private static boolean distante(HttpExchange x) {
        return x.getRequestHeaders().getFirst("Cf-Connecting-Ip") != null || x.getRequestHeaders().getFirst("Cf-Ray") != null
                || x.getRequestHeaders().getFirst("X-Forwarded-For") != null;
    }

    void demarrer(String adresse, int port, double portee, double plein) throws IOException {
        this.portee = portee;
        this.plein = plein;
        http = HttpServer.create(new InetSocketAddress(adresse, port), 0);
        // Un fil léger par requête : une page d'écoute garde la sienne ouverte en attendant le paquet de son suivant.
        fils = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("OrbitTele-web-", 0).factory());
        http.setExecutor(fils);
        http.createContext("/", x -> servir(x, () -> fichier(x)));
        http.createContext("/ecrans", x -> servir(x, () -> ecrans(x)));
        http.createContext("/image", x -> servir(x, () -> image(x)));
        http.createContext("/son", x -> servir(x, () -> son(x)));
        http.createContext("/ecoute/son", x -> servir(x, () -> ecoute(x)));
        http.createContext("/stop", x -> servir(x, () -> stop(x)));
        http.createContext("/apercu.png", x -> servir(x, () -> apercu(x)));
        http.createContext("/sante", x -> servir(x, () -> repondre(x, 200, "text/plain; charset=utf-8", "ok".getBytes(StandardCharsets.UTF_8))));
        http.start();
        this.port = port;
    }

    void arreter() {
        if (http != null) {
            http.stop(0);
            fils.shutdownNow();
        }
    }

    int port() {
        return port;
    }

    /** Un jeton par personne : en redemander un annule le précédent. */
    String jeton(String nom) {
        jetons.values().removeIf(nom::equals);
        String j = nouveau();
        jetons.put(j, nom);
        return j;
    }

    /** Jeton d'écoute : le même tant que le serveur tourne, pour que la page déjà ouverte continue de marcher. */
    String jetonEcoute(String nom) {
        for (Map.Entry<String, String> e : ecoutes.entrySet()) {
            if (e.getValue().equals(nom)) {
                return e.getKey();
            }
        }
        String j = nouveau();
        ecoutes.put(j, nom);
        return j;
    }

    private String nouveau() {
        byte[] b = new byte[16];
        hasard.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }

    /** Sur le fil du serveur, deux fois par seconde : où sont les joueurs qui ont une page d'écoute. */
    void suivre() {
        if (ecoutes.isEmpty()) {
            return;
        }
        for (String nom : Set.copyOf(ecoutes.values())) {
            Player p = Bukkit.getPlayerExact(nom);
            if (p == null) {
                places.remove(nom);
            } else {
                Location l = p.getLocation();
                places.put(nom, new Place(l.getWorld().getName(), l.getX(), l.getY(), l.getZ()));
            }
        }
    }

    /** Pour les essais hors serveur. */
    void placer(String nom, Place ou) {
        if (ou == null) {
            places.remove(nom);
        } else {
            places.put(nom, ou);
        }
    }

    // ------------------------------------------------------------------ réponses

    private interface Action {
        void faire() throws Exception;
    }

    private void servir(HttpExchange x, Action a) {
        try (x) {
            if (!diffusionDistante && distante(x) && !ECOUTE_SEULE.contains(x.getRequestURI().getPath())) {
                texte(x, 403, "{\"erreur\":\"local\"}");
                return;
            }
            a.faire();
        } catch (InterruptedException e) {
            // arrêt du serveur pendant qu'une page d'écoute attendait
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            JOURNAL.log(Level.FINE, "Requête web en erreur", e);
            try {
                texte(x, 500, "{\"erreur\":\"interne\"}");
            } catch (IOException ignored) {
                // réponse déjà partie
            }
        }
    }

    private void repondre(HttpExchange x, int code, String type, byte[] corps) throws IOException {
        x.getResponseHeaders().set("Content-Type", type);
        x.getResponseHeaders().set("Cache-Control", type.startsWith("font/") ? "max-age=86400" : "no-store");
        x.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        x.sendResponseHeaders(code, corps.length);
        x.getResponseBody().write(corps);
    }

    private void texte(HttpExchange x, int code, String json) throws IOException {
        repondre(x, code, "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8));
    }

    /** Nom du porteur du jeton, ou null (et la réponse 401 est déjà partie). */
    private String qui(HttpExchange x) throws IOException {
        String j = x.getRequestHeaders().getFirst("X-Jeton");
        String nom = j == null ? null : jetons.get(j);
        if (nom == null) {
            texte(x, 401, "{\"erreur\":\"jeton\"}");
        }
        return nom;
    }

    /** Valeur d'un paramètre de l'adresse, ou null. */
    private static String param(HttpExchange x, String nom) {
        String q = x.getRequestURI().getQuery();
        if (q != null) {
            for (String p : q.split("&")) {
                if (p.startsWith(nom + "=")) {
                    return p.substring(nom.length() + 1);
                }
            }
        }
        return null;
    }

    private static long nombre(String s, long sinon) {
        try {
            return s == null ? sinon : Long.parseLong(s);
        } catch (NumberFormatException e) {
            return sinon;
        }
    }

    private Ecran ecran(HttpExchange x) throws IOException {
        String nom = param(x, "ecran");
        Ecran e = nom == null ? null : plugin.ecran(nom);
        if (e == null) {
            texte(x, 404, "{\"erreur\":\"ecran\"}");
        }
        return e;
    }

    private void fichier(HttpExchange x) throws IOException {
        String[] res = FICHIERS.get(x.getRequestURI().getPath());
        if (res == null) {
            texte(x, 404, "{\"erreur\":\"introuvable\"}");
            return;
        }
        try (InputStream in = Web.class.getResourceAsStream(res[0])) {
            if (in == null) {
                texte(x, 404, "{\"erreur\":\"introuvable\"}");
                return;
            }
            repondre(x, 200, res[1], in.readAllBytes());
        }
    }

    private void ecrans(HttpExchange x) throws IOException {
        if (qui(x) == null) {
            return;
        }
        StringBuilder s = new StringBuilder("[");
        long maintenant = System.currentTimeMillis();
        for (Ecran e : plugin.ecrans()) {
            Session ses = e.session;
            String par = e.mode == Ecran.Mode.DIRECT ? e.diffuseur : ses != null ? ses.noms() : "";
            s.append(s.length() > 1 ? "," : "").append("{\"nom\":\"").append(e.nom).append("\",\"w\":").append(e.W).append(",\"h\":").append(e.H)
                    .append(",\"mode\":\"").append(e.mode).append("\",\"par\":\"").append(par)
                    .append("\",\"spectateurs\":").append(e.spectateurs()).append(",\"octets\":").append(e.octets)
                    .append(",\"son\":").append(e.mode == Ecran.Mode.DIRECT && e.son.actif(maintenant))
                    .append(",\"auditeurs\":").append(e.son.auditeurs(maintenant)).append("}");
        }
        texte(x, 200, s.append("]").toString());
    }

    private void image(HttpExchange x) throws IOException {
        String nom = qui(x);
        if (nom == null) {
            return;
        }
        Ecran e = ecran(x);
        if (e == null) {
            return;
        }
        byte[] corps = x.getRequestBody().readNBytes(MAX + 1);
        if (corps.length > MAX) {
            texte(x, 413, "{\"erreur\":\"trop gros\"}");
            return;
        }
        long maintenant = System.currentTimeMillis();
        if (e.mode == Ecran.Mode.CONSOLE) {
            texte(x, 409, "{\"erreur\":\"console\"}");
            return;
        }
        if (e.mode == Ecran.Mode.DIRECT && !nom.equals(e.diffuseur) && maintenant - e.derniereImage < 4000) {
            texte(x, 409, "{\"erreur\":\"occupe\",\"par\":\"" + e.diffuseur + "\"}");
            return;
        }
        BufferedImage recu = ImageIO.read(new ByteArrayInputStream(corps));
        if (recu == null) {
            texte(x, 400, "{\"erreur\":\"image\"}");
            return;
        }
        BufferedImage cible = new BufferedImage(e.W, e.H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = cible.createGraphics();
        if (recu.getWidth() == e.W && recu.getHeight() == e.H) {
            g.drawImage(recu, 0, 0, null);
        } else {
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, e.W, e.H);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            double k = Math.min(e.W / (double) recu.getWidth(), e.H / (double) recu.getHeight());
            int w = (int) Math.round(recu.getWidth() * k), h = (int) Math.round(recu.getHeight() * k);
            g.drawImage(recu, (e.W - w) / 2, (e.H - h) / 2, w, h, null);
        }
        g.dispose();
        e.diffuseur = nom;
        e.derniereImage = maintenant;
        e.mode = Ecran.Mode.DIRECT;
        e.pousser(((DataBufferInt) cible.getRaster().getDataBuffer()).getData(), true);
        texte(x, 200, "{\"spectateurs\":" + e.spectateurs() + "}");
    }

    private void stop(HttpExchange x) throws IOException {
        String nom = qui(x);
        Ecran e = nom == null ? null : ecran(x);
        if (e == null) {
            return;
        }
        if (e.mode == Ecran.Mode.DIRECT && nom.equals(e.diffuseur)) {
            e.mode = Ecran.Mode.VEILLE;
            e.son.couper();
        }
        texte(x, 200, "{}");
    }

    /** Dépôt du son par la page de diffusion : seulement pour celui qui tient l'antenne de cet écran. */
    private void son(HttpExchange x) throws IOException {
        String nom = qui(x);
        Ecran e = nom == null ? null : ecran(x);
        if (e == null) {
            return;
        }
        byte[] corps = x.getRequestBody().readNBytes(MAX_SON + 1);
        if (corps.length > MAX_SON) {
            texte(x, 413, "{\"erreur\":\"trop gros\"}");
            return;
        }
        long maintenant = System.currentTimeMillis();
        if (e.mode != Ecran.Mode.DIRECT || !nom.equals(e.diffuseur)) {
            texte(x, 409, "{\"erreur\":\"hors antenne\"}");
            return;
        }
        e.son.recevoir(corps, maintenant);
        texte(x, 200, "{\"auditeurs\":" + e.son.auditeurs(maintenant) + "}");
    }

    /**
     * Page d'écoute d'un joueur : le son de la télé en direct la plus proche de lui, avec le volume selon la distance.
     * La requête attend le paquet suivant (une seconde au plus), puis la page redemande la suite.
     */
    private void ecoute(HttpExchange x) throws IOException, InterruptedException {
        String j = x.getRequestHeaders().getFirst("X-Jeton");
        String nom = j == null ? null : ecoutes.get(j);
        if (nom == null) {
            texte(x, 401, "{\"erreur\":\"jeton\"}");
            return;
        }
        long maintenant = System.currentTimeMillis();
        Place ou = places.get(nom);
        Ecran choisi = null;
        double proche = Double.MAX_VALUE;
        if (ou != null) {
            for (Ecran e : plugin.ecrans()) {
                if (e.mode != Ecran.Mode.DIRECT || !e.son.actif(maintenant) || !e.mondeNom.equals(ou.monde())) {
                    continue;
                }
                double d = e.distance(ou.x(), ou.y(), ou.z());
                if (d < portee && d < proche) {
                    proche = d;
                    choisi = e;
                }
            }
        }
        byte[] corps;
        if (choisi == null) {
            Thread.sleep(500);
            corps = Son.rien(ou == null ? Son.HORS_JEU : Son.RIEN_A_PORTEE);
        } else {
            corps = choisi.son.lire((int) nombre(param(x, "flux"), 0), nombre(param(x, "suite"), -1), 900,
                    Son.volume(proche, plein, portee), choisi.nom, choisi.diffuseur, nom, maintenant);
        }
        repondre(x, 200, "application/octet-stream", corps);
    }

    private void apercu(HttpExchange x) throws IOException {
        Ecran e = qui(x) == null ? null : ecran(x);
        if (e == null) {
            return;
        }
        ByteArrayOutputStream o = new ByteArrayOutputStream(64 * 1024);
        ImageIO.write(e.apercu(), "png", o);
        repondre(x, 200, "image/png", o.toByteArray());
    }
}
