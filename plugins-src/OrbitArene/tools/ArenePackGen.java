import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Génère le pack de l'arène : icônes 32×32 des objets de la barre (charge, boost, quitter, orbe), dessinées ici
 * (aucune image reprise d'ailleurs), modèles « item/generated » et définitions d'objets orbitarene:*.
 * Usage : java ArenePackGen.java <dossier du pack>
 */
public final class ArenePackGen {

    static File root;

    public static void main(String[] args) throws IOException {
        root = new File(args.length > 0 ? args[0] : "pack");
        write("pack.mcmeta", "{\n  \"pack\": {\n    \"description\": \"§bORBIT §7- arène\",\n    \"min_format\": 97,\n    \"max_format\": 97\n  }\n}\n");
        icon("charge", ArenePackGen::charge);
        icon("boost", ArenePackGen::boost);
        icon("quitter", ArenePackGen::quitter);
        icon("orbe", ArenePackGen::orbe);
        System.out.println("Pack de l'arène généré dans " + root.getPath());
    }

    interface Painter {
        void paint(Graphics2D g);
    }

    static void icon(String name, Painter painter) throws IOException {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        painter.paint(g);
        g.dispose();
        File f = new File(root, "assets/orbitarene/textures/item/" + name + ".png");
        f.getParentFile().mkdirs();
        ImageIO.write(img, "png", f);
        write("assets/orbitarene/models/item/" + name + ".json",
                "{\n  \"parent\": \"minecraft:item/generated\",\n  \"textures\": { \"layer0\": \"orbitarene:item/" + name + "\" }\n}\n");
        write("assets/orbitarene/items/" + name + ".json",
                "{\n  \"model\": { \"type\": \"minecraft:model\", \"model\": \"orbitarene:item/" + name + "\" }\n}\n");
    }

    /** Charge Nova : sphère sombre cerclée de néon orange, étincelle dorée. */
    static void charge(Graphics2D g) {
        g.setPaint(new GradientPaint(8, 8, new Color(0x3A3F4E), 26, 28, new Color(0x0C0E14)));
        g.fill(new Ellipse2D.Double(5, 8, 22, 22));
        g.setStroke(new BasicStroke(2f));
        g.setColor(new Color(0xFF8A3D));
        g.draw(new Ellipse2D.Double(5, 8, 22, 22));
        g.setColor(new Color(0x62CFF4));
        g.fill(new Ellipse2D.Double(10, 12, 5, 5));
        g.setColor(new Color(0xE0B864));
        Path2D s = new Path2D.Double();
        s.moveTo(22, 2);
        s.lineTo(24, 6);
        s.lineTo(28, 7);
        s.lineTo(24, 9);
        s.lineTo(22, 13);
        s.lineTo(20, 9);
        s.lineTo(16, 7);
        s.lineTo(20, 6);
        s.closePath();
        g.fill(s);
    }

    /** Boost : chevrons bleu → glace. */
    static void boost(Graphics2D g) {
        g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Color[] c = {new Color(0x3A7FFF), new Color(0x4FA8FF), new Color(0x62CFF4)};
        for (int i = 0; i < 3; i++) {
            g.setColor(c[i]);
            Path2D p = new Path2D.Double();
            p.moveTo(6 + i * 7, 7);
            p.lineTo(13 + i * 7, 16);
            p.lineTo(6 + i * 7, 25);
            g.draw(p);
        }
    }

    /** Quitter : porte stylisée et flèche. */
    static void quitter(Graphics2D g) {
        g.setColor(new Color(0x1A1E28));
        g.fillRoundRect(4, 3, 16, 26, 4, 4);
        g.setStroke(new BasicStroke(2f));
        g.setColor(new Color(0x9AA4B2));
        g.drawRoundRect(4, 3, 16, 26, 4, 4);
        g.setColor(new Color(0xE0484A));
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(13, 16, 28, 16);
        g.drawLine(23, 11, 28, 16);
        g.drawLine(23, 21, 28, 16);
    }

    /** Orbe : bulle glace avec reflet. */
    static void orbe(Graphics2D g) {
        g.setPaint(new GradientPaint(6, 6, new Color(0xC79BFF), 26, 26, new Color(0x62CFF4)));
        g.fill(new Ellipse2D.Double(4, 4, 24, 24));
        g.setColor(new Color(255, 255, 255, 190));
        g.fill(new Ellipse2D.Double(9, 8, 7, 5));
    }

    static void write(String path, String content) throws IOException {
        File f = new File(root, path);
        f.getParentFile().mkdirs();
        Files.writeString(f.toPath(), content, StandardCharsets.UTF_8);
    }
}
