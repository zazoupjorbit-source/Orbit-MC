package fr.orbit.tele;

import fr.orbit.tele.jeux.Dessin;
import fr.orbit.tele.jeux.Jeu;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.List;

/** Image de veille : logo ORBIT TV, mode d'emploi, et les records de la console qui tournent. */
final class Veille {

    private Veille() {
    }

    /** @param tour change toutes les quelques secondes (records affichés à tour de rôle) */
    static int[] image(OrbitTele plugin, Ecran e, int tour) {
        BufferedImage img = new BufferedImage(e.W, e.H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        Dessin.lisse(g);
        double k = Math.min(e.W / (double) Jeu.W, e.H / (double) Jeu.H);
        AffineTransform t = new AffineTransform();
        t.translate((e.W - Jeu.W * k) / 2, (e.H - Jeu.H * k) / 2);
        t.scale(k, k);
        g.setTransform(t);
        g.setClip(0, 0, Jeu.W, Jeu.H);
        Dessin.fond(g, 0, 0);

        // planète et anneau
        int cx = 150, cy = 150;
        g.setColor(Dessin.alpha(Dessin.BLEU, 40));
        g.fillOval(cx - 78, cy - 78, 156, 156);
        g.setColor(Dessin.BLEU);
        g.fillOval(cx - 52, cy - 52, 104, 104);
        g.setColor(Dessin.alpha(Dessin.GLACE, 120));
        g.fillOval(cx - 34, cy - 40, 44, 30);
        g.setStroke(new BasicStroke(5f));
        g.setColor(Dessin.OR);
        g.rotate(-0.38, cx, cy);
        g.drawOval(cx - 104, cy - 26, 208, 52);
        g.rotate(0.38, cx, cy);
        g.setStroke(new BasicStroke(1f));
        g.setColor(Dessin.BLEU);
        g.fillArc(cx - 52, cy - 52, 104, 104, 0, 180);
        g.setColor(Dessin.alpha(Dessin.GLACE, 120));
        g.fillOval(cx - 34, cy - 40, 44, 30);

        g.setFont(Dessin.titre(58));
        g.setColor(Dessin.BLANC);
        g.drawString("ORBIT", 276, 132);
        g.setColor(Dessin.OR);
        g.drawString("TV", 478, 132);
        g.setFont(Dessin.texte(16));
        g.setColor(Dessin.GLACE);
        g.drawString("Aucune diffusion pour le moment", 280, 164);
        if (e.console != null) {
            g.setFont(Dessin.texte(14));
            g.setColor(Dessin.BLANC);
            g.drawString("La console est libre : clic droit dessus pour jouer", 280, 190);
        }

        // records d'un jeu, à tour de rôle
        List<Jeu.Fiche> classes = Jeu.LISTE.stream().filter(Jeu.Fiche::records).toList();
        Jeu.Fiche f = classes.get(Math.floorMod(tour, classes.size()));
        List<Scores.Ligne> top = plugin.scores().top(f.id());
        Dessin.panneau(g, 40, 262, 560, 96, Dessin.alpha(Dessin.OR, 150));
        g.setFont(Dessin.titre(17));
        g.setColor(Dessin.OR);
        g.drawString("RECORDS · " + f.titre(), 58, 290);
        g.setFont(Dessin.texte(13));
        if (top.isEmpty()) {
            g.setColor(Dessin.GRIS);
            g.drawString("Aucun score pour l'instant. Le premier record est à prendre.", 58, 322);
        }
        for (int i = 0; i < Math.min(3, top.size()); i++) {
            int x = 58 + i * 180;
            g.setFont(Dessin.titre(15));
            g.setColor(i == 0 ? Dessin.OR : Dessin.GLACE);
            g.drawString((i + 1) + ".", x, 322);
            g.setFont(Dessin.texte(14));
            g.setColor(Dessin.BLANC);
            g.drawString(top.get(i).nom(), x + 22, 322);
            g.setFont(Dessin.titre(14));
            g.setColor(Dessin.OR);
            g.drawString(String.valueOf(top.get(i).score()), x + 22, 342);
        }
        g.setColor(new Color(255, 255, 255, 40));
        for (int i = 0; i < classes.size(); i++) {
            g.setColor(i == Math.floorMod(tour, classes.size()) ? Dessin.OR : new Color(255, 255, 255, 50));
            g.fillOval(580 + i * 9 - classes.size() * 9, 280, 5, 5);
        }
        g.dispose();
        return ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
    }
}
