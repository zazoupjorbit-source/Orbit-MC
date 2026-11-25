package fr.orbit.tele.jeux;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.Random;

/**
 * ASTROBLOCS : blocs qui tombent dans un puits de 10 × 20. À deux, un puits chacun : vider 2 lignes ou plus en
 * envoie à l'adversaire (1, 2 ou 4), qui montent par le bas de son puits.
 */
final class Astroblocs extends Jeu {

    private static final int[][][] BASES = {
            {{0, 1}, {1, 1}, {2, 1}, {3, 1}}, {{0, 0}, {1, 0}, {0, 1}, {1, 1}}, {{1, 0}, {0, 1}, {1, 1}, {2, 1}},
            {{1, 0}, {2, 0}, {0, 1}, {1, 1}}, {{0, 0}, {1, 0}, {1, 1}, {2, 1}}, {{0, 0}, {0, 1}, {1, 1}, {2, 1}},
            {{2, 0}, {0, 1}, {1, 1}, {2, 1}}};
    private static final int[] BOITES = {4, 2, 3, 3, 3, 3, 3};
    private static final Color[] TEINTES = {Dessin.GLACE, Dessin.OR, Dessin.VIOLET, Dessin.VERT, Dessin.ROUGE, Dessin.BLEU, Dessin.ORANGE, Dessin.GRIS};
    private static final int[][][][] FORMES = new int[7][4][4][2];
    private static final int Y0 = 22, C = 17;

    static {
        for (int t = 0; t < 7; t++) {
            int[][] f = BASES[t];
            for (int r = 0; r < 4; r++) {
                for (int i = 0; i < 4; i++) {
                    FORMES[t][r][i] = f[i].clone();
                }
                int[][] suite = new int[4][2];
                for (int i = 0; i < 4; i++) {
                    suite[i][0] = BOITES[t] - 1 - f[i][1];
                    suite[i][1] = f[i][0];
                }
                f = suite;
            }
        }
    }

    /** Le puits d'un joueur. */
    private final class Puits {
        final int[][] grille = new int[20][10];
        final boolean[] pleines = new boolean[20];
        final Random sac;
        int type, rot, px, py, suivant, chute, lignes, niveau = 1, repetition, eclair, points, recues;
        boolean perdu;

        Puits(long graine) {
            sac = new Random(graine);
            suivant = sac.nextInt(7);
            nouvelle();
        }

        void nouvelle() {
            // lignes envoyées par l'adversaire : elles poussent le puits vers le haut
            for (; recues > 0; recues--) {
                for (int x = 0; x < 10; x++) {
                    perdu |= grille[0][x] != 0;
                }
                System.arraycopy(grille, 1, grille, 0, 19);
                grille[19] = new int[10];
                java.util.Arrays.fill(grille[19], 8);
                grille[19][alea.nextInt(10)] = 0;
            }
            type = suivant;
            suivant = sac.nextInt(7);
            rot = 0;
            px = type == 1 ? 4 : 3;
            py = 0;
            chute = 0;
            if (perdu || bloque(rot, px, py)) {
                perdu = true;
                son("perdu");
            }
        }

        boolean bloque(int r, int x, int y) {
            for (int[] c : FORMES[type][r]) {
                int cx = x + c[0], cy = y + c[1];
                if (cx < 0 || cx >= 10 || cy >= 20 || (cy >= 0 && grille[cy][cx] != 0)) {
                    return true;
                }
            }
            return false;
        }

        /** @return le nombre de lignes vidées à cette image */
        int tic(Manette m) {
            if (perdu) {
                return 0;
            }
            if (eclair > 0) {
                if (--eclair == 0) {
                    effacer();
                    nouvelle();
                }
                return 0;
            }
            int dx = (m.tenu(Manette.GAUCHE) ? -1 : 0) + (m.tenu(Manette.DROITE) ? 1 : 0);
            if (dx == 0) {
                repetition = 0;
            } else if (m.appui(Manette.GAUCHE) || m.appui(Manette.DROITE) || (++repetition > 6 && repetition % 2 == 0)) {
                if (!bloque(rot, px + dx, py)) {
                    px += dx;
                }
            }
            if (m.appui(Manette.HAUT)) {
                int r = (rot + 1) & 3;
                for (int k : new int[]{0, -1, 1, -2, 2}) {
                    if (!bloque(r, px + k, py)) {
                        rot = r;
                        px += k;
                        son("clic");
                        break;
                    }
                }
            }
            if (m.appui(Manette.A)) {
                while (!bloque(rot, px, py + 1)) {
                    py++;
                    points += 2;
                }
                return poser();
            }
            int delai = m.tenu(Manette.BAS) ? 2 : Math.max(3, 28 - niveau * 3);
            if (++chute >= delai) {
                chute = 0;
                if (bloque(rot, px, py + 1)) {
                    return poser();
                }
                py++;
            }
            return 0;
        }

        int poser() {
            for (int[] c : FORMES[type][rot]) {
                if (py + c[1] >= 0) {
                    grille[py + c[1]][px + c[0]] = type + 1;
                }
            }
            int n = 0;
            for (int y = 0; y < 20; y++) {
                pleines[y] = true;
                for (int x = 0; x < 10; x++) {
                    pleines[y] &= grille[y][x] != 0;
                }
                n += pleines[y] ? 1 : 0;
            }
            if (n == 0) {
                son("pose");
                nouvelle();
                return 0;
            }
            points += new int[]{0, 100, 300, 500, 800}[n] * niveau;
            lignes += n;
            niveau = 1 + lignes / 10;
            eclair = 8;
            type = -1;
            son(n == 4 ? "bravo" : "ligne");
            return n;
        }

        void effacer() {
            int vers = 19;
            for (int y = 19; y >= 0; y--) {
                if (!pleines[y]) {
                    if (vers != y) {
                        grille[vers] = grille[y].clone();
                    }
                    vers--;
                }
            }
            for (; vers >= 0; vers--) {
                grille[vers] = new int[10];
            }
        }

        void dessiner(Graphics2D g, int x0, Color bord) {
            Dessin.panneau(g, x0 - 6, Y0 - 6, 10 * C + 12, 20 * C + 12, bord);
            g.setColor(new Color(255, 255, 255, 14));
            for (int x = 1; x < 10; x++) {
                g.drawLine(x0 + x * C, Y0, x0 + x * C, Y0 + 20 * C);
            }
            for (int y = 0; y < 20; y++) {
                for (int x = 0; x < 10; x++) {
                    if (grille[y][x] != 0) {
                        boolean flash = eclair > 0 && pleines[y];
                        cellule(g, x0 + x * C, Y0 + y * C, flash ? Color.WHITE : TEINTES[grille[y][x] - 1], flash ? 120 + eclair * 16 : perdu ? 110 : 255);
                    }
                }
            }
            if (type >= 0 && !perdu) {
                int fy = py;
                while (!bloque(rot, px, fy + 1)) {
                    fy++;
                }
                for (int[] c : FORMES[type][rot]) {
                    g.setColor(Dessin.alpha(TEINTES[type], 70));
                    g.drawRoundRect(x0 + (px + c[0]) * C + 1, Y0 + (fy + c[1]) * C + 1, C - 3, C - 3, 5, 5);
                    cellule(g, x0 + (px + c[0]) * C, Y0 + (py + c[1]) * C, TEINTES[type], 255);
                }
            }
            if (recues > 0) {
                g.setColor(Dessin.ROUGE);
                g.fillRect(x0 - 5, Y0 + 20 * C - recues * C, 3, recues * C);
            }
        }

        void suivante(Graphics2D g, int cx, int y) {
            for (int[] c : FORMES[suivant][0]) {
                cellule(g, cx - BOITES[suivant] * C / 2 + c[0] * C, y + c[1] * C, TEINTES[suivant], 255);
            }
        }
    }

    private final Puits[] puits;

    Astroblocs(Contexte ctx) {
        super(ctx);
        long graine = alea.nextLong();
        puits = ctx.deux() ? new Puits[]{new Puits(graine), new Puits(graine)} : new Puits[]{new Puits(graine)};
        record = !ctx.deux();
    }

    @Override
    protected void tic(Manette a, Manette b) {
        int n = puits[0].tic(a);
        if (puits.length == 1) {
            score = puits[0].points;
            fini = puits[0].perdu;
            return;
        }
        int m = puits[1].tic(b);
        puits[1].recues += n >= 4 ? 4 : Math.max(0, n - 1);
        puits[0].recues += m >= 4 ? 4 : Math.max(0, m - 1);
        if (puits[0].perdu || puits[1].perdu) {
            fini = true;
            gagnant = puits[0].perdu && puits[1].perdu ? 0 : puits[0].perdu ? 2 : 1;
            bilan = puits[0].points + "  –  " + puits[1].points;
        }
    }

    private static void cellule(Graphics2D g, int x, int y, Color c, int a) {
        g.setColor(Dessin.alpha(c, a));
        g.fillRoundRect(x + 1, y + 1, C - 2, C - 2, 5, 5);
        g.setColor(Dessin.alpha(Color.WHITE, a / 3));
        g.fillRoundRect(x + 3, y + 3, C - 6, 4, 3, 3);
    }

    @Override
    public void dessiner(Graphics2D g) {
        Dessin.fond(g, tics, 0.15);
        if (puits.length == 2) {
            puits[0].dessiner(g, 40, Dessin.BLEU);
            puits[1].dessiner(g, 430, Dessin.OR);
            g.setFont(Dessin.titre(18));
            g.setColor(Dessin.GLACE);
            Dessin.centre(g, "ASTROBLOCS", W / 2, 40);
            for (int i = 0; i < 2; i++) {
                Color c = i == 0 ? Dessin.BLEU : Dessin.OR;
                int y = 60 + i * 150;
                g.setFont(Dessin.texte(12));
                g.setColor(c);
                Dessin.centre(g, "J" + (i + 1) + "  " + noms[i], W / 2, y + 8);
                Dessin.compteur(g, 240, y + 16, 160, "SCORE", String.valueOf(puits[i].points), c);
                Dessin.panneau(g, 240, y + 68, 76, 60, Dessin.alpha(c, 120));
                puits[i].suivante(g, 278, y + 80);
                Dessin.compteur(g, 324, y + 68, 76, "LIGNES", String.valueOf(puits[i].lignes), c);
            }
            g.setFont(Dessin.texte(10));
            g.setColor(Dessin.GRIS);
            Dessin.centre(g, "2 lignes ou plus : tu en envoies à l'autre", W / 2, 370);
            return;
        }
        Puits p = puits[0];
        p.dessiner(g, 235, Dessin.BLEU);
        g.setFont(Dessin.titre(24));
        g.setColor(Dessin.GLACE);
        g.drawString("ASTROBLOCS", 28, 48);
        Dessin.compteur(g, 28, 66, 170, "SCORE", String.valueOf(p.points), Dessin.OR);
        Dessin.compteur(g, 28, 120, 80, "NIVEAU", String.valueOf(p.niveau), Dessin.GLACE);
        Dessin.compteur(g, 118, 120, 80, "LIGNES", String.valueOf(p.lignes), Dessin.GLACE);
        Dessin.panneau(g, 440, 22, 120, 104, Dessin.alpha(Dessin.BLEU, 140));
        g.setFont(Dessin.texte(11));
        g.setColor(Dessin.GRIS);
        g.drawString("SUIVANT", 452, 40);
        p.suivante(g, 500, 58);
        String[] aide = {"Q D   déplacer", "Z   tourner", "S   descendre", "Espace   poser"};
        for (int i = 0; i < aide.length; i++) {
            g.drawString(aide[i], 28, 300 + i * 17);
        }
    }
}
