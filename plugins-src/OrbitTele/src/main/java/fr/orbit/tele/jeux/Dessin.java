package fr.orbit.tele.jeux;

import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.io.InputStream;

/** Couleurs, polices et petits outils de dessin communs (charte ORBIT). */
public final class Dessin {

    public static final Color FOND = new Color(0x070B16), FOND2 = new Color(0x141032), PANNEAU = new Color(0x111A2E),
            BLEU = new Color(0x3A7FFF), OR = new Color(0xE0B864), GLACE = new Color(0x62CFF4), BLANC = new Color(0xF2F5FF),
            GRIS = new Color(0x8A93A8), ROUGE = new Color(0xFF4D5E), VERT = new Color(0x4BE38A), VIOLET = new Color(0xB06BFF),
            ROSE = new Color(0xFF6BD0), ORANGE = new Color(0xFF9A3D);

    private static Font titre = new Font(Font.SANS_SERIF, Font.BOLD, 12), texte = new Font(Font.SANS_SERIF, Font.BOLD, 12);
    private static final int[] ETOILES = new int[90 * 3];

    static {
        titre = charger("/polices/Tektur-Medium.ttf", titre);
        texte = charger("/polices/Outfit-Bold.ttf", texte);
        java.util.Random r = new java.util.Random(7);
        for (int i = 0; i < ETOILES.length; i += 3) {
            ETOILES[i] = r.nextInt(Jeu.W);
            ETOILES[i + 1] = r.nextInt(Jeu.H);
            ETOILES[i + 2] = 1 + r.nextInt(3);
        }
    }

    private Dessin() {
    }

    private static Font charger(String chemin, Font sinon) {
        try (InputStream in = Dessin.class.getResourceAsStream(chemin)) {
            return in == null ? sinon : Font.createFont(Font.TRUETYPE_FONT, in);
        } catch (Exception e) {
            return sinon;
        }
    }

    public static Font titre(float taille) {
        return titre.deriveFont(taille);
    }

    public static Font texte(float taille) {
        return texte.deriveFont(taille);
    }

    public static void lisse(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    }

    /** Fond d'espace : dégradé et étoiles qui dérivent doucement. */
    public static void fond(Graphics2D g, long tics, double vitesse) {
        g.setPaint(new GradientPaint(0, 0, FOND, 0, Jeu.H, FOND2));
        g.fillRect(0, 0, Jeu.W, Jeu.H);
        for (int i = 0; i < ETOILES.length; i += 3) {
            int n = ETOILES[i + 2];
            int y = (int) ((ETOILES[i + 1] + tics * vitesse * n) % Jeu.H);
            g.setColor(new Color(255, 255, 255, 50 + n * 45));
            g.fillRect(ETOILES[i], y, n > 2 ? 2 : 1, n > 2 ? 2 : 1);
        }
    }

    public static void centre(Graphics2D g, String s, int cx, int y) {
        g.drawString(s, cx - g.getFontMetrics().stringWidth(s) / 2f, y);
    }

    public static void droite(Graphics2D g, String s, int x, int y) {
        g.drawString(s, x - g.getFontMetrics().stringWidth(s), y);
    }

    /** Panneau arrondi avec liseré. */
    public static void panneau(Graphics2D g, int x, int y, int w, int h, Color bord) {
        g.setColor(new Color(PANNEAU.getRed(), PANNEAU.getGreen(), PANNEAU.getBlue(), 225));
        g.fillRoundRect(x, y, w, h, 12, 12);
        g.setColor(bord);
        g.drawRoundRect(x, y, w, h, 12, 12);
    }

    /** Étiquette + valeur (score, niveau…) dans un petit panneau. */
    public static void compteur(Graphics2D g, int x, int y, int w, String nom, String valeur, Color teinte) {
        panneau(g, x, y, w, 46, new Color(teinte.getRed(), teinte.getGreen(), teinte.getBlue(), 120));
        g.setFont(texte(11));
        g.setColor(GRIS);
        g.drawString(nom, x + 10, y + 17);
        g.setFont(titre(20));
        g.setColor(teinte);
        g.drawString(valeur, x + 10, y + 38);
    }

    public static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, a)));
    }
}
