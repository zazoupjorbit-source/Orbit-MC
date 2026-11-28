import org.lwjgl.opengl.GL;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Banc d'essai d'Orbit Shaders, sans lancer le jeu :
 *  1. compile et relie chaque programme (surface, Nether, End), options par défaut puis options « oui/non » coupées,
 *     sur la vraie carte graphique (contexte OpenGL 4.6 compatibilité, comme ce qu'Iris reçoit) ;
 *  2. avec -apercu <png> : dessine le ciel et un sol éclairé à 6 moments de la journée, passés par la vraie passe finale.
 * Les #include sont résolus comme Iris (chemins depuis shaders/), les macros d'Iris sont ajoutées.
 *
 *   java -cp <lwjgl...> outils/TestShaders.java <dossier shaders> [-apercu fichier.png]
 */
public class TestShaders {

    static Path racine;
    static final String MACROS_IRIS = String.join("\n",
            "#define MC_VERSION 260300", "#define IS_IRIS", "#define MC_GL_VERSION 460", "#define MC_GLSL_VERSION 460",
            "#define MC_RENDER_STAGE_NONE 0", "#define MC_RENDER_STAGE_SKY 1", "#define MC_RENDER_STAGE_SUNSET 2",
            "#define MC_RENDER_STAGE_CUSTOM_SKY 3", "#define MC_RENDER_STAGE_SUN 4", "#define MC_RENDER_STAGE_MOON 5",
            "#define MC_RENDER_STAGE_STARS 6", "#define MC_RENDER_STAGE_VOID 7") + "\n";
    static boolean optionsCoupees;

    /** Source développée + origine de chaque ligne (fichier:ligne) pour traduire les erreurs. */
    record Source(String texte, List<String> origines) {}

    public static void main(String[] args) throws Exception {
        racine = Path.of(args[0]).toAbsolutePath();
        String apercu = args.length > 2 && args[1].equals("-apercu") ? args[2] : null;

        if (!glfwInit()) throw new IllegalStateException("GLFW");
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 6);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_COMPAT_PROFILE);
        long fenetre = glfwCreateWindow(64, 64, "test", 0, 0);
        glfwMakeContextCurrent(fenetre);
        GL.createCapabilities();
        System.out.println("Carte : " + glGetString(GL_RENDERER) + " / OpenGL " + glGetString(GL_VERSION));

        int erreurs = 0, programmes = 0;
        for (boolean coupe : new boolean[]{false, true}) {
            optionsCoupees = coupe;
            for (String monde : new String[]{"", "world-1", "world1"}) {
                Path dossier = monde.isEmpty() ? racine : racine.resolve(monde);
                try (var fichiers = Files.list(dossier)) {
                    for (Path vsh : fichiers.filter(p -> p.toString().endsWith(".vsh")).sorted().toList()) {
                        String nom = vsh.getFileName().toString().replace(".vsh", "");
                        Path fsh = dossier.resolve(nom + ".fsh");
                        String etiquette = (monde.isEmpty() ? "" : monde + "/") + nom + (coupe ? " [options coupées]" : "");
                        programmes++;
                        String err = construire(lire(vsh), lire(fsh));
                        if (err != null) {
                            erreurs++;
                            System.out.println("ÉCHEC " + etiquette + "\n" + err);
                        }
                    }
                }
            }
        }
        System.out.println(programmes + " programmes testés, " + erreurs + " en échec.");

        if (apercu != null && erreurs == 0) apercu(apercu);
        glfwTerminate();
        System.exit(erreurs == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------ préprocesseur (comme Iris)

    static Source lire(Path fichier) throws Exception {
        List<String> lignes = new ArrayList<>(), origines = new ArrayList<>();
        developper(fichier, lignes, origines);
        // macros d'Iris juste après #version
        StringBuilder sb = new StringBuilder();
        List<String> orig = new ArrayList<>();
        sb.append(lignes.get(0)).append('\n');
        orig.add(origines.get(0));
        for (String m : MACROS_IRIS.split("\n")) {
            sb.append(m).append('\n');
            orig.add("(macro Iris)");
        }
        for (int i = 1; i < lignes.size(); i++) {
            sb.append(lignes.get(i)).append('\n');
            orig.add(origines.get(i));
        }
        return new Source(sb.toString(), orig);
    }

    static final Pattern INCLUDE = Pattern.compile("^\\s*#include\\s+\"([^\"]+)\"");
    static final Pattern DEFINE_BOOL = Pattern.compile("^#define ([A-Z_]+)(\\s*//.*)?$");

    static void developper(Path fichier, List<String> lignes, List<String> origines) throws Exception {
        List<String> contenu = Files.readAllLines(fichier);
        String court = racine.relativize(fichier).toString().replace('\\', '/');
        for (int i = 0; i < contenu.size(); i++) {
            String l = contenu.get(i);
            Matcher m = INCLUDE.matcher(l);
            if (m.find()) {
                String chemin = m.group(1);
                Path cible = chemin.startsWith("/") ? racine.resolve(chemin.substring(1)) : fichier.getParent().resolve(chemin);
                developper(cible.normalize(), lignes, origines);
                continue;
            }
            // Iris coupe une option oui/non en commentant son #define : on fait pareil.
            if (optionsCoupees && court.equals("lib/reglages.glsl") && DEFINE_BOOL.matcher(l).matches()) l = "//" + l;
            lignes.add(l);
            origines.add(court + ":" + (i + 1));
        }
    }

    static final Pattern LIGNE_ERREUR = Pattern.compile("0\\((\\d+)\\)");

    static String traduire(String log, Source s) {
        Matcher m = LIGNE_ERREUR.matcher(log);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            int n = Integer.parseInt(m.group(1)) - 1;
            String o = n >= 0 && n < s.origines().size() ? s.origines().get(n) : "?";
            m.appendReplacement(sb, Matcher.quoteReplacement(o));
        }
        m.appendTail(sb);
        return sb.toString().trim();
    }

    // ------------------------------------------------------------ compilation

    static int dernierProgramme;

    static String construire(Source vs, Source fs) {
        StringBuilder err = new StringBuilder();
        int v = compiler(GL_VERTEX_SHADER, vs, "  sommets : ", err);
        int f = compiler(GL_FRAGMENT_SHADER, fs, "  pixels : ", err);
        if (err.length() > 0) return err.toString();
        int p = glCreateProgram();
        glAttachShader(p, v);
        glAttachShader(p, f);
        glLinkProgram(p);
        if (glGetProgrami(p, GL_LINK_STATUS) == 0) return "  liaison : " + glGetProgramInfoLog(p);
        dernierProgramme = p;
        return null;
    }

    static int compiler(int type, Source s, String prefixe, StringBuilder err) {
        int id = glCreateShader(type);
        glShaderSource(id, s.texte());
        glCompileShader(id);
        if (glGetShaderi(id, GL_COMPILE_STATUS) == 0) err.append(prefixe).append(traduire(glGetShaderInfoLog(id), s)).append('\n');
        return id;
    }

    // ------------------------------------------------------------ aperçu du ciel et de la lumière

    static final String APERCU_SOMMETS = """
            #version 330 compatibility
            out vec2 uv;
            void main() { gl_Position = ftransform(); uv = gl_MultiTexCoord0.xy; }
            """;
    static final String APERCU_PIXELS = """
            #version 330 compatibility
            #define FSH
            #include "/lib/commun.glsl"
            #undef OMBRES
            #include "/lib/ombres.glsl"
            #include "/lib/eclairage.glsl"
            #include "/lib/ciel.glsl"
            in vec2 uv;
            uniform mat3 camRot;
            layout(location = 0) out vec4 o;
            void main() {
                vec2 p = uv * 2.0 - 1.0;
                p.x *= viewWidth / viewHeight;
                vec3 dir = normalize(camRot * vec3(p * 0.75, -1.0));
                vec3 c = cielCouleur(dir, true);
                if (dir.y < 0.0) {   // sol d'herbe à 2 blocs sous la caméra, avec des dalles claires
                    float t = -2.0 / dir.y;
                    vec3 pos = dir * t;
                    vec2 cel = floor(pos.xz / 4.0);
                    vec3 albedo = mod(cel.x + cel.y, 2.0) < 1.0 ? vec3(0.09, 0.20, 0.05) : vec3(0.24, 0.24, 0.23);
                    vec3 sol = eclairer(albedo, vec3(0.0, 1.0, 0.0), vec2(0.0, 1.0), pos, 0.0, 0.0, 0.5, 0.0);
                    float brume = 1.0 - exp(-t * 0.0018 * BROUILLARD);
                    float bord = smoothstep(160.0 * 0.55, 160.0, t);
                    c = mix(sol, cielCouleur(dir, false), max(brume * 0.85, bord));
                }
                o = vec4(c, 1.0);
            }
            """;

    record Moment(String nom, float elevation, float azimut, float tangage, float angleSoleil) {}

    static void apercu(String sortie) throws Exception {
        int W = 640, H = 360;
        Moment[] moments = {
                new Moment("Lever du soleil", 4f, 90f, 6f, 0.011f),
                new Moment("Matin", 28f, 90f, 6f, 0.078f),
                new Moment("Midi", 75f, 180f, 14f, 0.21f),
                new Moment("Coucher doré", 3f, 270f, 6f, 0.49f),
                new Moment("Crépuscule", -6f, 270f, 8f, 0.517f),
                new Moment("Nuit (nébuleuse)", -50f, 200f, 38f, 0.64f),
        };
        Source vs = new Source(APERCU_SOMMETS, List.of());
        Path tmp = Files.createTempFile("apercu", ".glsl");
        Files.writeString(tmp, APERCU_PIXELS);
        optionsCoupees = false;
        Source fs = lire(tmp);
        String err = construire(vs, fs);
        if (err != null) { System.out.println("Aperçu : " + err); return; }
        int progCiel = dernierProgramme;
        err = construire(lire(racine.resolve("final.vsh")), lire(racine.resolve("final.fsh")));
        if (err != null) { System.out.println("Aperçu final : " + err); return; }
        int progFinal = dernierProgramme;

        int texHdr = texture(W, H, GL_RGBA16F, GL_FLOAT);
        int texLdr = texture(W, H, GL_RGBA8, GL_UNSIGNED_BYTE);
        int fboHdr = fbo(texHdr), fboLdr = fbo(texLdr);
        glViewport(0, 0, W, H);

        BufferedImage planche = new BufferedImage(W * 3, H * 2, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = planche.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font("Segoe UI", Font.BOLD, 20));
        for (int i = 0; i < moments.length; i++) {
            Moment m = moments[i];
            float e = (float) Math.toRadians(m.elevation()), az = (float) Math.toRadians(m.azimut());
            // azimut 90 = est (+X), 180 = sud (+Z), 270 = ouest (-X) ; le soleil se lève à l'est et se couche à l'ouest
            float sx = (m.angleSoleil() < 0.25f || m.angleSoleil() > 0.75f) ? (float) Math.cos(e) : -(float) Math.cos(e);
            float[] soleil = {sx * 100, (float) Math.sin(e) * 100, 0};
            float[] lune = {-soleil[0], -soleil[1], -soleil[2]};
            float[] lumiere = m.elevation() > 0 ? soleil : lune;
            float t = (float) Math.toRadians(m.tangage());
            float[] f = {(float) (Math.cos(t) * Math.sin(az)), (float) Math.sin(t), (float) (-Math.cos(t) * Math.cos(az))};
            f = new float[]{(float) (Math.cos(t) * Math.sin(az)), (float) Math.sin(t), (float) (Math.cos(t) * -Math.cos(az))};
            float[] r = normaliser(croix(f, new float[]{0, 1, 0}));
            float[] u = croix(r, f);

            glUseProgram(progCiel);
            uniformes(progCiel, W, H, soleil, lune, lumiere, m.angleSoleil());
            glUniformMatrix3fv(glGetUniformLocation(progCiel, "camRot"), false, new float[]{r[0], r[1], r[2], u[0], u[1], u[2], -f[0], -f[1], -f[2]});
            glBindFramebuffer(GL_FRAMEBUFFER, fboHdr);
            quad();

            glUseProgram(progFinal);
            uniformes(progFinal, W, H, soleil, lune, lumiere, m.angleSoleil());
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, texHdr);
            glUniform1i(glGetUniformLocation(progFinal, "colortex0"), 0);
            glUniform1i(glGetUniformLocation(progFinal, "colortex2"), 0);
            glBindFramebuffer(GL_FRAMEBUFFER, fboLdr);
            quad();

            ByteBuffer px = ByteBuffer.allocateDirect(W * H * 4);
            glReadPixels(0, 0, W, H, GL_RGBA, GL_UNSIGNED_BYTE, px);
            int ox = (i % 3) * W, oy = (i / 3) * H;
            for (int y = 0; y < H; y++)
                for (int x = 0; x < W; x++) {
                    int k = ((H - 1 - y) * W + x) * 4;
                    planche.setRGB(ox + x, oy + y, ((px.get(k) & 255) << 16) | ((px.get(k + 1) & 255) << 8) | (px.get(k + 2) & 255));
                }
            g.setColor(new Color(0, 0, 0, 140));
            g.fillRect(ox, oy, W, 34);
            g.setColor(new Color(232, 190, 90));
            g.drawString(m.nom(), ox + 12, oy + 24);
        }
        g.dispose();
        ImageIO.write(planche, "png", new File(sortie));
        System.out.println("Aperçu écrit : " + sortie);
    }

    static void uniformes(int p, int W, int H, float[] soleil, float[] lune, float[] lumiere, float angle) {
        glUniform3f(glGetUniformLocation(p, "sunPosition"), soleil[0], soleil[1], soleil[2]);
        glUniform3f(glGetUniformLocation(p, "moonPosition"), lune[0], lune[1], lune[2]);
        glUniform3f(glGetUniformLocation(p, "shadowLightPosition"), lumiere[0], lumiere[1], lumiere[2]);
        glUniform3f(glGetUniformLocation(p, "upPosition"), 0, 100, 0);
        glUniform1f(glGetUniformLocation(p, "viewWidth"), W);
        glUniform1f(glGetUniformLocation(p, "viewHeight"), H);
        glUniform1f(glGetUniformLocation(p, "far"), 160);
        glUniform1f(glGetUniformLocation(p, "sunAngle"), angle);
        glUniform1f(glGetUniformLocation(p, "frameTimeCounter"), 12.5f);
        glUniform2i(glGetUniformLocation(p, "eyeBrightnessSmooth"), 240, 240);
        float[] id = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
        glUniformMatrix4fv(glGetUniformLocation(p, "gbufferModelView"), false, id);
        glUniformMatrix4fv(glGetUniformLocation(p, "gbufferModelViewInverse"), false, id);
    }

    static void quad() {
        glBegin(GL_QUADS);
        glTexCoord2f(0, 0); glVertex2f(-1, -1);
        glTexCoord2f(1, 0); glVertex2f(1, -1);
        glTexCoord2f(1, 1); glVertex2f(1, 1);
        glTexCoord2f(0, 1); glVertex2f(-1, 1);
        glEnd();
    }

    static int texture(int w, int h, int format, int type) {
        int t = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, t);
        glTexImage2D(GL_TEXTURE_2D, 0, format, w, h, 0, GL_RGBA, type, (ByteBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        return t;
    }

    static int fbo(int tex) {
        int f = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, f);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, tex, 0);
        return f;
    }

    static float[] croix(float[] a, float[] b) {
        return new float[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    static float[] normaliser(float[] v) {
        float l = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        return new float[]{v[0] / l, v[1] / l, v[2] / l};
    }
}
