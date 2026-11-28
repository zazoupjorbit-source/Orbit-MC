package fr.orbit.tele;

import fr.orbit.tele.jeux.Dessin;
import fr.orbit.tele.jeux.Jeu;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.font.TextAttribute;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Map;
import java.util.Random;

/**
 * Image de veille (télé éteinte) : habillage Orbit MC (cadres des menus HD du jeu, bleu + or) et le mode d'emploi
 * de la télé, rien d'autre. Deux versions : télé seule (regarder, écouter) ou télé + console (jouer, touches, écouter).
 * <p>
 * Les aplats sont pris tels quels dans la palette des cartes (pas de tramage sur la veille) : un dégradé y ferait
 * des marches, donc fonds et cadres sont en couleurs pleines que la carte rend exactement.
 */
final class Veille {

    // DA Orbit MC ramenée aux couleurs exactes des cartes
    private static final Color NUIT = new Color(0x151515), CONTOUR = new Color(0x0D0D0D), CREUX = new Color(0x111111),
            MARINE = new Color(0x1B285E), MARINE_CLAIR = new Color(0x24357D), TOUCHE = new Color(0x284351), TOUCHE_HAUT = new Color(0x365172),
            LISERE = new Color(0x486C98), OR_FONCE = new Color(0xAEA473), OR = new Color(0xD5C98C), OR_CLAIR = new Color(0xF7E9A3),
            OR_CUIVRE = new Color(0xBA8524), GLACE = new Color(0x5CDBD5), BLEU = new Color(0x4A80FF), ENCRE = new Color(0xFFFCF5),
            GRIS = new Color(0xA4A8B8), ORANGE = new Color(0xD87F33);

    private static final int[] ETOILES = new int[64 * 3];

    static {
        Random r = new Random(11);
        for (int i = 0; i < ETOILES.length; i += 3) {
            ETOILES[i] = 14 + r.nextInt(Jeu.W - 28);
            ETOILES[i + 1] = 14 + r.nextInt(Jeu.H - 28);
            ETOILES[i + 2] = r.nextInt(4);
        }
    }

    private Veille() {
    }

    static int[] image(OrbitTele plugin, Ecran e, int tour) {
        return image(e.W, e.H, e.console != null);
    }

    /** Image à la taille de l'écran (le dessin 640 × 384 est mis à l'échelle et centré). */
    static int[] image(int largeur, int hauteur, boolean console) {
        BufferedImage img = new BufferedImage(largeur, hauteur, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        Dessin.lisse(g);
        g.setColor(NUIT);
        g.fillRect(0, 0, largeur, hauteur);
        double k = Math.min(largeur / (double) Jeu.W, hauteur / (double) Jeu.H);
        AffineTransform t = new AffineTransform();
        t.translate((largeur - Jeu.W * k) / 2, (hauteur - Jeu.H * k) / 2);
        t.scale(k, k);
        g.setTransform(t);
        g.setClip(0, 0, Jeu.W, Jeu.H);
        dessiner(g, console);
        g.dispose();
        return ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
    }

    private static void dessiner(Graphics2D g, boolean console) {
        int W = Jeu.W, H = Jeu.H;

        // fond d'espace + poussière d'étoiles
        g.setColor(NUIT);
        g.fillRect(0, 0, W, H);
        for (int i = 0; i < ETOILES.length; i += 3) {
            int n = ETOILES[i + 2];
            g.setColor(n == 3 ? OR_CLAIR : n == 2 ? GLACE : n == 1 ? GRIS : TOUCHE_HAUT);
            g.fillRect(ETOILES[i], ETOILES[i + 1], n == 3 ? 2 : 1, n == 3 ? 2 : 1);
        }

        // cadre des menus HD : contour sombre, liseré or, liseré bleu, coins or
        g.setColor(CONTOUR);
        g.fillRect(0, 0, W, 6);
        g.fillRect(0, H - 6, W, 6);
        g.fillRect(0, 0, 6, H);
        g.fillRect(W - 6, 0, 6, H);
        g.setColor(OR_FONCE);
        g.drawRect(6, 6, W - 13, H - 13);
        g.drawRect(7, 7, W - 15, H - 15);
        g.setColor(LISERE);
        g.drawRect(10, 10, W - 21, H - 21);
        coins(g, W, H);

        // en-tête : logo + ORBIT TV + état
        logo(g, 46, 50, 16);
        g.setFont(Dessin.titre(34));
        g.setColor(CONTOUR);
        g.drawString("ORBIT", 94, 63);
        g.setColor(ENCRE);
        g.drawString("ORBIT", 92, 61);
        int xTv = 92 + g.getFontMetrics().stringWidth("ORBIT ");
        g.setColor(CONTOUR);
        g.drawString("TV", xTv + 2, 63);
        g.setPaint(new GradientPaint(0, 36, OR_CLAIR, 0, 61, OR_CUIVRE));
        g.drawString("TV", xTv, 61);
        g.setFont(espace(11));
        g.setColor(GLACE);
        g.drawString("MODE D'EMPLOI", 94, 79);

        g.setFont(Dessin.texte(12));
        String etat = "EN VEILLE";
        int we = g.getFontMetrics().stringWidth(etat) + 34;
        int xe = W - 26 - we;
        cadre(g, xe, 36, we, 26, false);
        g.setColor(ORANGE);
        g.fill(new Ellipse2D.Double(xe + 11, 45, 8, 8));
        g.setColor(GRIS);
        g.drawString(etat, xe + 25, 54);

        // séparateur or
        int ys = 92;
        g.setColor(OR_FONCE);
        g.fillRect(40, ys, W / 2 - 50, 1);
        g.fillRect(W / 2 + 10, ys, W / 2 - 50, 1);
        losange(g, W / 2, ys, 4);

        if (console) {
            int w = 188, gap = 10, x0 = (W - 3 * w - 2 * gap) / 2, y = 106, h = 236;
            carteJouer(g, x0, y, w, h);
            carteTouches(g, x0 + w + gap, y, w, h);
            carteEcouter(g, x0 + 2 * (w + gap), y, w, h);
        } else {
            int w = 288, gap = 16, x0 = (W - 2 * w - gap) / 2, y = 106, h = 236;
            carteRegarder(g, x0, y, w, h);
            carteEcouter(g, x0 + w + gap, y, w, h);
        }

        // pied
        g.setFont(espace(10));
        g.setColor(OR_FONCE);
        Dessin.centre(g, "ORBIT MC  ·  LA STATION", W / 2, H - 21);
    }

    // ------------------------------------------------------------------ cartes

    private static void carteJouer(Graphics2D g, int x, int y, int w, int h) {
        int ty = titreCarte(g, x, y, w, h, "JOUER", OR);
        ty = etape(g, x, ty, 1, "Clic droit sur la", "console : tu t'assois");
        ty = etape(g, x, ty, 2, "Z S pour choisir,", "Espace pour lancer");
        etape(g, x, ty, 3, "Un ami fait clic droit", "aussi : partie à deux");
        pied(g, x, y, h, "7 jeux, seul ou à deux");
    }

    private static void carteTouches(Graphics2D g, int x, int y, int w, int h) {
        int ty = titreCarte(g, x, y, w, h, "TOUCHES", GLACE);
        int kx = x + 14, lx = x + 82;
        // Z Q S D groupées comme sur le clavier
        ty -= 12;
        touche(g, kx + 21, ty, "Z");
        touche(g, kx, ty + 22, "Q");
        touche(g, kx + 21, ty + 22, "S");
        touche(g, kx + 42, ty + 22, "D");
        libelle(g, lx, ty + 11, "Bouger");
        ty += 54;
        touche(g, kx, ty, "ESPACE");
        libelle(g, lx, ty, "Valider, agir");
        ty += 34;
        touche(g, kx, ty, "MAJ");
        libelle(g, lx, ty, "Action 2");
        ty += 34;
        touche(g, kx, ty, "F");
        libelle(g, lx, ty, "Menu");
        g.setFont(Dessin.texte(10));
        g.setColor(GRIS);
        g.drawString("Au menu : se lever", lx, ty + 29);
    }

    private static void carteRegarder(Graphics2D g, int x, int y, int w, int h) {
        int ty = titreCarte(g, x, y, w, h, "REGARDER", OR);
        ty = etape(g, x, ty, 1, "Quand le staff diffuse, l'image", "s'affiche ici en direct");
        etape(g, x, ty, 2, "Approche-toi : l'image se voit", "à moins de 48 blocs");
        pied(g, x, y, h, "Staff : /tele lien pour partager ton écran");
    }

    private static void carteEcouter(Graphics2D g, int x, int y, int w, int h) {
        int ty = titreCarte(g, x, y, w, h, "ÉCOUTER", GLACE);
        g.setFont(Dessin.texte(12));
        g.setColor(ENCRE);
        g.drawString("Tape dans le chat :", x + 14, ty + 4);
        ty += 14;
        commande(g, x + 14, ty, w - 28, "/tele son");
        ty += 44;
        String[][] l = w > 200
                ? new String[][]{{"Clique sur le lien : le son de la télé"}, {"joue dans ton navigateur."}, {"Plus tu es près, plus c'est fort.", ""}}
                : new String[][]{{"Clique sur le lien :"}, {"le son de la télé joue"}, {"dans ton navigateur."}, {"Plus tu es près,", ""}, {"plus c'est fort.", ""}};
        for (String[] s : l) {
            g.setFont(Dessin.texte(11));
            g.setColor(s.length > 1 ? GRIS : ENCRE);
            g.drawString(s[0], x + 14, ty);
            ty += 16;
        }
        pied(g, x, y, h, "Seulement pendant une diffusion");
    }

    // ------------------------------------------------------------------ éléments

    /** Carte des menus HD avec son titre ; rend le y du contenu. */
    private static int titreCarte(Graphics2D g, int x, int y, int w, int h, String titre, Color teinte) {
        cadre(g, x, y, w, h, true);
        g.setColor(teinte);
        g.fillRect(x + 3, y + 3, w - 6, 2);
        g.setFont(espace(15));
        g.setColor(CONTOUR);
        g.drawString(titre, x + 15, y + 28);
        g.setColor(teinte);
        g.drawString(titre, x + 14, y + 27);
        g.setColor(LISERE);
        g.fillRect(x + 14, y + 35, w - 28, 1);
        return y + 56;
    }

    private static void pied(Graphics2D g, int x, int y, int h, String s) {
        g.setFont(Dessin.texte(10));
        g.setColor(GRIS);
        g.drawString(s, x + 14, y + h - 13);
    }

    /** Étape numérotée sur deux lignes ; rend le y de l'étape suivante. */
    private static int etape(Graphics2D g, int x, int y, int n, String a, String b) {
        int cx = x + 23, cy = y + 2;
        g.setColor(CONTOUR);
        g.fill(new Ellipse2D.Double(cx - 11, cy - 11, 22, 22));
        g.setPaint(new GradientPaint(0, cy - 9, OR_CLAIR, 0, cy + 9, OR_CUIVRE));
        g.fill(new Ellipse2D.Double(cx - 9, cy - 9, 18, 18));
        g.setFont(Dessin.titre(12));
        g.setColor(CONTOUR);
        Dessin.centre(g, String.valueOf(n), cx, cy + 5);
        g.setFont(Dessin.texte(12));
        g.setColor(ENCRE);
        g.drawString(a, x + 42, y);
        g.setFont(Dessin.texte(11));
        g.setColor(GLACE);
        g.drawString(b, x + 42, y + 15);
        return y + 50;
    }

    /** Touche de clavier façon emplacement d'inventaire HD. */
    private static void touche(Graphics2D g, int x, int y, String s) {
        g.setFont(Dessin.titre(s.length() > 1 ? 10 : 12));
        int w = Math.max(19, g.getFontMetrics().stringWidth(s) + 12);
        g.setColor(CONTOUR);
        g.fillRect(x - 1, y - 1, w + 2, 22);
        g.setColor(TOUCHE);
        g.fillRect(x, y, w, 20);
        g.setColor(TOUCHE_HAUT);
        g.fillRect(x, y, w, 2);
        g.setColor(OR_FONCE);
        g.drawRect(x, y, w - 1, 19);
        g.setColor(ENCRE);
        Dessin.centre(g, s, x + w / 2, y + 15);
    }

    private static void libelle(Graphics2D g, int x, int y, String s) {
        g.setFont(Dessin.texte(12));
        g.setColor(ENCRE);
        g.drawString(s, x, y + 15);
    }

    /** Commande à taper, dans un creux à liseré or. */
    private static void commande(Graphics2D g, int x, int y, int w, String s) {
        g.setColor(CONTOUR);
        g.fillRect(x - 2, y - 2, w + 4, 30);
        g.setColor(CREUX);
        g.fillRect(x, y, w, 26);
        g.setColor(OR_FONCE);
        g.drawRect(x, y, w - 1, 25);
        g.setFont(Dessin.titre(15));
        g.setColor(OR);
        Dessin.centre(g, s, x + w / 2, y + 18);
    }

    /** Cadre des menus HD : contour sombre, fond marine, liseré bleu (or + bleu si `fort`). */
    private static void cadre(Graphics2D g, int x, int y, int w, int h, boolean fort) {
        g.setColor(CONTOUR);
        g.fillRect(x - 2, y - 2, w + 4, h + 4);
        g.setColor(fort ? MARINE : CREUX);
        g.fillRect(x, y, w, h);
        g.setColor(fort ? OR_FONCE : LISERE);
        g.drawRect(x, y, w - 1, h - 1);
        if (fort) {
            g.setColor(MARINE_CLAIR);
            g.drawRect(x + 1, y + 1, w - 3, h - 3);
        }
    }

    private static void coins(Graphics2D g, int W, int H) {
        g.setColor(OR_CLAIR);
        g.fillRect(6, 6, 16, 3);
        g.fillRect(6, 6, 3, 16);
        g.fillRect(W - 22, 6, 16, 3);
        g.fillRect(W - 9, 6, 3, 16);
        g.fillRect(6, H - 9, 16, 3);
        g.fillRect(6, H - 22, 3, 16);
        g.fillRect(W - 22, H - 9, 16, 3);
        g.fillRect(W - 9, H - 22, 3, 16);
    }

    private static void losange(Graphics2D g, int cx, int cy, int r) {
        g.setColor(OR_CLAIR);
        g.fillPolygon(new int[]{cx, cx + r, cx, cx - r}, new int[]{cy - r, cy, cy + r, cy}, 4);
    }

    /** Logo planète + anneau (le même que sur les pages web). */
    private static void logo(Graphics2D g, int cx, int cy, int r) {
        AffineTransform avant = g.getTransform();
        java.awt.Shape clip = g.getClip();
        BasicStroke anneau = new BasicStroke(r / 5f);
        Ellipse2D orbite = new Ellipse2D.Double(cx - r * 2, cy - r * 0.5, r * 4, r);
        // moitié arrière de l'anneau
        g.rotate(-0.38, cx, cy);
        g.setStroke(anneau);
        g.setColor(OR_FONCE);
        g.draw(orbite);
        g.setTransform(avant);
        // planète
        g.setColor(CONTOUR);
        g.fill(new Ellipse2D.Double(cx - r - 1.5, cy - r - 1.5, r * 2 + 3, r * 2 + 3));
        g.setPaint(new GradientPaint(cx - r, cy - r, GLACE, cx + r, cy + r, BLEU));
        g.fill(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
        g.setColor(ENCRE);
        g.fill(new Ellipse2D.Double(cx - r * 0.55, cy - r * 0.62, r * 0.5, r * 0.32));
        // moitié avant de l'anneau, devant la planète
        g.rotate(-0.38, cx, cy);
        g.clip(new Rectangle2D.Double(cx - r * 2.4, cy, r * 4.8, r));
        g.setColor(OR);
        g.draw(orbite);
        g.setTransform(avant);
        g.setClip(clip);
        g.setStroke(new BasicStroke(1f));
    }

    /** Police de titre aux lettres espacées. */
    private static Font espace(float taille) {
        return Dessin.titre(taille).deriveFont(Map.of(TextAttribute.TRACKING, 0.12f));
    }
}
