package fr.orbit.tele.jeux;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Random;

/**
 * BASTION ORBITAL : on bâtit sa base (sauvegardée, elle produit de l'énergie même hors ligne), puis on attaque celles
 * des autres joueurs ou des avant-postes pirates en déposant ses troupes autour. Une attaque ne vole rien à la base
 * attaquée : le butin vient de sa valeur. À deux : chacun attaque la base de l'autre, le meilleur assaut gagne.
 */
final class Bastion extends Jeu {

    // ------------------------------------------------------------------ règles

    static final int NOYAU = 0, COLLECTEUR = 1, RESERVE = 2, TOURELLE = 3, MORTIER = 4, MUR = 5, CASERNE = 6;
    static final String[] NOMS = {"Noyau", "Collecteur", "Réserve", "Tourelle", "Mortier", "Mur", "Caserne"};
    static final String[] ROLES = {"Le cœur de la base", "Produit l'énergie", "Stocke l'énergie", "Tire vite, de près", "Frappe en zone, de loin", "Barre le passage", "Agrandit ton armée"};
    static final int[] COUT = {0, 150, 200, 300, 600, 30, 400};
    static final int[] PV = {1500, 300, 450, 420, 380, 600, 400};
    static final int[][] MAXI = {{1, 1, 1, 1, 1}, {2, 3, 4, 5, 6}, {1, 2, 2, 3, 4}, {2, 3, 4, 5, 6}, {0, 1, 2, 2, 3}, {12, 20, 30, 40, 52}, {1, 1, 1, 1, 1}};
    static final int[] COUT_NOYAU = {0, 1500, 4000, 9000, 18000};
    static final int GC = 16, GR = 12, T = 22, MARGE = 2;

    static final int SOLDAT = 0, TIREUR = 1, SAPEUR = 2, COLOSSE = 3;
    static final String[] TROUPES = {"Soldat", "Tireur", "Sapeur", "Colosse"};
    static final double[] T_PV = {80, 46, 30, 560}, T_DEGATS = {9, 7, 0, 22}, T_VITESSE = {0.055, 0.05, 0.09, 0.035}, T_PORTEE = {0.8, 3.0, 0.8, 0.8};
    static final int[] T_CADENCE = {20, 20, 0, 30};
    static final Color[] T_COULEUR = {Dessin.BLEU, Dessin.VERT, Dessin.ORANGE, Dessin.VIOLET};
    static final int[][] ARMEES = {{6, 0, 0, 0}, {10, 5, 0, 0}, {14, 8, 3, 0}, {18, 10, 5, 2}, {22, 12, 6, 4}, {26, 14, 7, 6}};

    static double facteur(int niveau) {
        return 1 + 0.3 * (niveau - 1);
    }

    static int coutAmelioration(int type, int niveau) {
        return type == NOYAU ? COUT_NOYAU[Math.min(4, niveau)] : (int) Math.round(COUT[type] * Math.pow(niveau + 1, 1.7) / 10) * 10;
    }

    // ------------------------------------------------------------------ une base

    static final class Base {
        String nom = "", id = "";
        double energie = 600;
        int trophees;
        long maj = System.currentTimeMillis();
        /** type, x, y, niveau. */
        final List<int[]> bats = new ArrayList<>();

        int niveau(int type) {
            int n = 0;
            for (int[] b : bats) {
                if (b[0] == type) {
                    n = Math.max(n, b[3]);
                }
            }
            return n;
        }

        int nombre(int type) {
            int n = 0;
            for (int[] b : bats) {
                n += b[0] == type ? 1 : 0;
            }
            return n;
        }

        int en(int x, int y) {
            for (int i = 0; i < bats.size(); i++) {
                if (bats.get(i)[1] == x && bats.get(i)[2] == y) {
                    return i;
                }
            }
            return -1;
        }

        int capacite() {
            int c = 2000;
            for (int[] b : bats) {
                c += b[0] == RESERVE ? 2500 * b[3] : 0;
            }
            return c;
        }

        /** Énergie par heure. */
        int production() {
            int p = 0;
            for (int[] b : bats) {
                p += b[0] == COLLECTEUR ? 240 * b[3] : 0;
            }
            return p;
        }

        static Base depart(String nom, String id) {
            Base b = new Base();
            b.nom = nom;
            b.id = id;
            b.bats.add(new int[]{NOYAU, 8, 6, 1});
            b.bats.add(new int[]{COLLECTEUR, 6, 6, 1});
            b.bats.add(new int[]{RESERVE, 10, 6, 1});
            b.bats.add(new int[]{TOURELLE, 8, 4, 1});
            b.bats.add(new int[]{CASERNE, 8, 8, 1});
            return b;
        }

        /** Avant-poste pirate d'un niveau donné, différent à chaque fois. */
        static Base pirate(Random r, int niveau) {
            Base b = new Base();
            String[] noms = {"Avant-poste Kessar", "Repaire des Écumeurs", "Comptoir Noir", "Station Vautour", "Bastion Cendre", "Nid des Corsaires"};
            b.nom = noms[r.nextInt(noms.length)];
            b.bats.add(new int[]{NOYAU, 8, 6, niveau});
            int[][] quoi = {{TOURELLE, MAXI[TOURELLE][niveau - 1]}, {MORTIER, MAXI[MORTIER][niveau - 1]}, {COLLECTEUR, MAXI[COLLECTEUR][niveau - 1]},
                    {RESERVE, MAXI[RESERVE][niveau - 1]}, {CASERNE, 1}};
            for (int[] q : quoi) {
                for (int k = 0; k < q[1]; k++) {
                    for (int essai = 0; essai < 40; essai++) {
                        int x = 8 + r.nextInt(7) - 3, y = 6 + r.nextInt(7) - 3;
                        if (b.en(x, y) < 0) {
                            b.bats.add(new int[]{q[0], x, y, Math.max(1, niveau - r.nextInt(2))});
                            break;
                        }
                    }
                }
            }
            // enceinte : un anneau autour de la base, troué au hasard tant qu'il n'y a pas assez de murs pour le fermer
            int murs = MAXI[MUR][niveau - 1] * 3 / 4;
            for (int tour = 0; tour < 2 && murs > 0; tour++) {
                List<int[]> anneau = new ArrayList<>();
                for (int x = 4 - tour; x <= 12 + tour; x++) {
                    for (int y = 2 - tour; y <= 10 + tour; y++) {
                        if ((x == 4 - tour || x == 12 + tour || y == 2 - tour || y == 10 + tour) && b.en(x, y) < 0) {
                            anneau.add(new int[]{x, y});
                        }
                    }
                }
                java.util.Collections.shuffle(anneau, r);
                for (int k = 0; k < anneau.size() && murs > 0; k++, murs--) {
                    b.bats.add(new int[]{MUR, anneau.get(k)[0], anneau.get(k)[1], niveau});
                }
            }
            return b;
        }

        static Base lire(File f) {
            Properties p = new Properties();
            try (FileInputStream in = new FileInputStream(f)) {
                p.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
                Base b = new Base();
                b.nom = p.getProperty("nom", "?");
                b.id = p.getProperty("id", "");
                b.energie = Double.parseDouble(p.getProperty("energie", "600"));
                b.trophees = Integer.parseInt(p.getProperty("trophees", "0"));
                b.maj = Long.parseLong(p.getProperty("maj", "0"));
                for (String s : p.getProperty("bats", "").split(";")) {
                    String[] v = s.split(",");
                    if (v.length == 4) {
                        b.bats.add(new int[]{Integer.parseInt(v[0]), Integer.parseInt(v[1]), Integer.parseInt(v[2]), Integer.parseInt(v[3])});
                    }
                }
                return b.niveau(NOYAU) == 0 ? null : b;
            } catch (Exception e) {
                return null;
            }
        }

        void ecrire(File f) {
            Properties p = new Properties();
            p.setProperty("nom", nom);
            p.setProperty("id", id);
            p.setProperty("energie", String.valueOf((int) energie));
            p.setProperty("trophees", String.valueOf(trophees));
            p.setProperty("maj", String.valueOf(maj = System.currentTimeMillis()));
            StringBuilder s = new StringBuilder();
            for (int[] b : bats) {
                s.append(b[0]).append(',').append(b[1]).append(',').append(b[2]).append(',').append(b[3]).append(';');
            }
            p.setProperty("bats", s.toString());
            f.getParentFile().mkdirs();
            try (FileOutputStream out = new FileOutputStream(f)) {
                p.store(new java.io.OutputStreamWriter(out, java.nio.charset.StandardCharsets.UTF_8), "Bastion Orbital");
            } catch (Exception ignored) {
                // sauvegarde ratée : la base en mémoire reste bonne, on réessaiera
            }
        }
    }

    // ------------------------------------------------------------------ un assaut

    final class Assaut {
        final Base def;
        final String attaquant;
        final double[] pv, pvMax;
        final int[] recharge, stock;
        final double force;
        /** type, x, y, pv, recharge, cible. */
        final List<double[]> troupes = new ArrayList<>();
        /** x, y, reste, dégâts. */
        final List<double[]> obus = new ArrayList<>();
        /** x1, y1, x2, y2, reste, couleur. */
        final List<double[]> traits = new ArrayList<>();
        int kx = 10, ky = 0, choisi, temps = 90 * 30, pct, etoiles, cadence, repet, total;
        boolean termine;

        Assaut(Base def, String attaquant, int caserne) {
            this.def = def;
            this.attaquant = attaquant;
            stock = ARMEES[Math.max(0, Math.min(5, caserne))].clone();
            force = 1 + 0.2 * Math.max(0, caserne - 1);
            int n = def.bats.size();
            pv = new double[n];
            pvMax = new double[n];
            recharge = new int[n];
            for (int i = 0; i < n; i++) {
                int[] b = def.bats.get(i);
                pv[i] = pvMax[i] = PV[b[0]] * facteur(b[3]);
                total += b[0] == MUR ? 0 : 1;
            }
        }

        double bx(int i) {
            return def.bats.get(i)[1] + MARGE + 0.5;
        }

        double by(int i) {
            return def.bats.get(i)[2] + MARGE + 0.5;
        }

        int batimentEn(double x, double y) {
            int i = def.en((int) Math.floor(x) - MARGE, (int) Math.floor(y) - MARGE);
            return i >= 0 && pv[i] > 0 ? i : -1;
        }

        /** Le bâtiment debout le plus proche parmi ceux qui conviennent (-1 si aucun). */
        int proche(double x, double y, int genre) {
            int mieux = -1;
            double dm = 1e9;
            for (int i = 0; i < pv.length; i++) {
                int t = def.bats.get(i)[0];
                boolean ok = genre == 0 ? t != MUR : genre == 1 ? t == MUR : t == TOURELLE || t == MORTIER;
                double d = Math.hypot(bx(i) - x, by(i) - y);
                if (pv[i] > 0 && ok && d < dm) {
                    dm = d;
                    mieux = i;
                }
            }
            return mieux;
        }

        void frapper(int i, double degats) {
            if (pv[i] > 0 && (pv[i] -= degats) <= 0) {
                son("boum");
            }
        }

        void tic(Manette m) {
            if (termine) {
                return;
            }
            // curseur et dépôt des troupes
            int dx = (m.tenu(Manette.DROITE) ? 1 : 0) - (m.tenu(Manette.GAUCHE) ? 1 : 0), dy = (m.tenu(Manette.BAS) ? 1 : 0) - (m.tenu(Manette.HAUT) ? 1 : 0);
            boolean neuf = m.appui(Manette.DROITE) || m.appui(Manette.GAUCHE) || m.appui(Manette.BAS) || m.appui(Manette.HAUT);
            repet = dx == 0 && dy == 0 ? 0 : repet + 1;
            if (neuf || (repet > 7 && repet % 2 == 0)) {
                kx = Math.max(0, Math.min(GC + 2 * MARGE - 1, kx + dx));
                ky = Math.max(0, Math.min(GR + 2 * MARGE - 1, ky + dy));
            }
            if (m.appui(Manette.B)) {
                for (int k = 1; k <= 4; k++) {
                    if (stock[(choisi + k) % 4] > 0) {
                        choisi = (choisi + k) % 4;
                        break;
                    }
                }
                son("clic");
            }
            cadence = Math.max(0, cadence - 1);
            boolean dehors = kx < MARGE || ky < MARGE || kx >= GC + MARGE || ky >= GR + MARGE;
            if (m.tenu(Manette.A) && cadence == 0 && dehors) {
                if (stock[choisi] == 0) {
                    for (int k = 1; k <= 4 && stock[choisi] == 0; k++) {
                        choisi = (choisi + 1) % 4;
                    }
                }
                if (stock[choisi] > 0) {
                    stock[choisi]--;
                    cadence = 4;
                    troupes.add(new double[]{choisi, kx + 0.2 + alea.nextDouble() * 0.6, ky + 0.2 + alea.nextDouble() * 0.6, T_PV[choisi] * force, 0, -1});
                    son("pose");
                }
            }
            // troupes
            for (double[] t : troupes) {
                int type = (int) t[0];
                int c = (int) t[5];
                if (c < 0 || pv[c] <= 0) {
                    c = type == SAPEUR ? proche(t[1], t[2], 1) : type == COLOSSE ? proche(t[1], t[2], 2) : -1;
                    if (c < 0) {
                        c = proche(t[1], t[2], 0);
                    }
                    t[5] = c;
                }
                if (c < 0) {
                    continue;
                }
                t[4] = Math.max(0, t[4] - 1);
                double ex = bx(c) - t[1], ey = by(c) - t[2], d = Math.hypot(ex, ey);
                int vise = c;
                if (d > T_PORTEE[type]) {
                    double nx = t[1] + ex / d * T_VITESSE[type], ny = t[2] + ey / d * T_VITESSE[type];
                    int gene = batimentEn(nx, ny);
                    if (gene < 0 || gene == c) {
                        t[1] = nx;
                        t[2] = ny;
                        continue;
                    }
                    vise = gene;
                }
                if (type == SAPEUR) {
                    for (int i = 0; i < pv.length; i++) {
                        if (Math.hypot(bx(i) - t[1], by(i) - t[2]) < 1.6) {
                            frapper(i, (def.bats.get(i)[0] == MUR ? 700 : 140) * force);
                        }
                    }
                    obus.add(new double[]{t[1], t[2], -6, 0});
                    t[3] = 0;
                } else if (t[4] == 0) {
                    t[4] = T_CADENCE[type];
                    frapper(vise, T_DEGATS[type] * force);
                    if (type == TIREUR) {
                        traits.add(new double[]{t[1], t[2], bx(vise), by(vise), 3, 1});
                    }
                }
            }
            // défenses
            for (int i = 0; i < pv.length; i++) {
                int[] b = def.bats.get(i);
                if (pv[i] <= 0 || (b[0] != TOURELLE && b[0] != MORTIER) || --recharge[i] > 0) {
                    continue;
                }
                double[] cible = null;
                double dm = b[0] == TOURELLE ? 4.2 : 7;
                for (double[] t : troupes) {
                    double d = Math.hypot(t[1] - bx(i), t[2] - by(i));
                    if (t[3] > 0 && d < dm && (b[0] == TOURELLE || d > 2.2)) {
                        dm = d;
                        cible = t;
                    }
                }
                if (cible == null) {
                    recharge[i] = 5;
                } else if (b[0] == TOURELLE) {
                    recharge[i] = 15;
                    cible[3] -= 6.5 * facteur(b[3]);
                    traits.add(new double[]{bx(i), by(i), cible[1], cible[2], 3, 0});
                } else {
                    recharge[i] = 75;
                    obus.add(new double[]{cible[1], cible[2], 30, 14 * facteur(b[3])});
                }
            }
            obus.removeIf(o -> {
                if (o[2] > 0 && --o[2] == 0) {
                    for (double[] t : troupes) {
                        if (Math.hypot(t[1] - o[0], t[2] - o[1]) < 1.3) {
                            t[3] -= o[3];
                        }
                    }
                    o[2] = -6;
                    son("boum");
                }
                return o[2] < 0 && ++o[2] >= 0;
            });
            traits.removeIf(t -> --t[4] <= 0);
            troupes.removeIf(t -> t[3] <= 0);
            // bilan
            int detruits = 0;
            boolean noyau = false;
            for (int i = 0; i < pv.length; i++) {
                int type = def.bats.get(i)[0];
                detruits += pv[i] <= 0 && type != MUR ? 1 : 0;
                noyau |= pv[i] <= 0 && type == NOYAU;
            }
            pct = total == 0 ? 100 : detruits * 100 / total;
            etoiles = (pct >= 50 ? 1 : 0) + (noyau ? 1 : 0) + (pct == 100 ? 1 : 0);
            int reste = stock[0] + stock[1] + stock[2] + stock[3];
            if (--temps <= 0 || pct == 100 || (reste == 0 && troupes.isEmpty())) {
                termine = true;
            }
        }

        void dessiner(Graphics2D g) {
            int x0 = 12, y0 = 24;
            g.setColor(new Color(0x0B1428));
            g.fillRect(x0, y0, (GC + 2 * MARGE) * T, (GR + 2 * MARGE) * T);
            g.setColor(Dessin.alpha(Dessin.VERT, 22));
            g.fillRect(x0, y0, (GC + 2 * MARGE) * T, (GR + 2 * MARGE) * T);
            g.setColor(new Color(0x12203F));
            g.fillRect(x0 + MARGE * T, y0 + MARGE * T, GC * T, GR * T);
            g.setColor(Dessin.alpha(Dessin.VERT, 26));
            g.setStroke(new BasicStroke(1f));
            g.drawRect(x0, y0, (GC + 2 * MARGE) * T - 1, (GR + 2 * MARGE) * T - 1);
            base(g, def, pv, pvMax, x0 + MARGE * T, y0 + MARGE * T);
            for (double[] o : obus) {
                int px = x0 + (int) (o[0] * T), py = y0 + (int) (o[1] * T);
                if (o[2] > 0) {
                    g.setColor(Dessin.alpha(Dessin.ROUGE, 120));
                    g.drawOval(px - 28, py - 28, 56, 56);
                    g.setColor(Dessin.ORANGE);
                    g.fillOval(px - 3, py - 3 - (int) (o[2] * 2), 6, 6);
                } else {
                    g.setColor(Dessin.alpha(Dessin.ORANGE, 40 + (int) -o[2] * 30));
                    g.fillOval(px - 28, py - 28, 56, 56);
                }
            }
            for (double[] t : traits) {
                g.setColor(t[5] == 0 ? Dessin.ROUGE : Dessin.VERT);
                g.drawLine(x0 + (int) (t[0] * T), y0 + (int) (t[1] * T), x0 + (int) (t[2] * T), y0 + (int) (t[3] * T));
            }
            for (double[] t : troupes) {
                int type = (int) t[0], r = type == COLOSSE ? 7 : type == SAPEUR ? 3 : 4;
                int px = x0 + (int) (t[1] * T), py = y0 + (int) (t[2] * T);
                g.setColor(T_COULEUR[type]);
                g.fillOval(px - r, py - r, r * 2, r * 2);
                g.setColor(Dessin.BLANC);
                g.fillOval(px - 1, py - r + 1, 2, 2);
            }
            boolean dehors = kx < MARGE || ky < MARGE || kx >= GC + MARGE || ky >= GR + MARGE;
            g.setStroke(new BasicStroke(2f));
            g.setColor(dehors ? Dessin.OR : Dessin.ROUGE);
            g.drawRoundRect(x0 + kx * T + 1, y0 + ky * T + 1, T - 2, T - 2, 6, 6);
            g.setStroke(new BasicStroke(1f));
            // panneau
            int px = 462;
            g.setFont(Dessin.texte(10));
            g.setColor(Dessin.GRIS);
            g.drawString("ASSAUT DE", px, 34);
            g.setFont(Dessin.titre(14));
            g.setColor(Dessin.OR);
            g.drawString(attaquant, px, 50);
            g.setFont(Dessin.texte(10));
            g.setColor(Dessin.GRIS);
            g.drawString("contre " + def.nom, px, 64);
            Dessin.compteur(g, px, 74, 80, "TEMPS", String.valueOf(Math.max(0, temps / 30)), temps < 450 ? Dessin.ROUGE : Dessin.GLACE);
            Dessin.compteur(g, px + 86, 74, 80, "DÉTRUIT", pct + " %", Dessin.OR);
            for (int e = 0; e < 3; e++) {
                etoile(g, px + 22 + e * 44, 148, 14, e < etoiles);
            }
            for (int k = 0; k < 4; k++) {
                int y = 176 + k * 36;
                Dessin.panneau(g, px, y, 166, 30, k == choisi ? Dessin.OR : Dessin.alpha(Dessin.BLEU, 80));
                g.setColor(T_COULEUR[k]);
                g.fillOval(px + 9, y + 9, 12, 12);
                g.setFont(Dessin.texte(12));
                g.setColor(stock[k] > 0 ? Dessin.BLANC : Dessin.GRIS);
                g.drawString(TROUPES[k], px + 30, y + 20);
                g.setFont(Dessin.titre(14));
                Dessin.droite(g, "× " + stock[k], px + 156, y + 20);
            }
            g.setFont(Dessin.texte(10));
            g.setColor(Dessin.GRIS);
            g.drawString("Espace : déposer (bord vert)", px, 334);
            g.drawString("Maj : changer de troupe", px, 348);
            g.setColor(dehors ? Dessin.GRIS : Dessin.ROUGE);
            g.drawString(dehors ? "Les troupes visent seules." : "Trop près : sors de la base.", px, 362);
        }
    }

    // ------------------------------------------------------------------ partie

    private static final int BASE = 0, CONSTRUIRE = 1, GERER = 2, DEPLACER = 3, CIBLE = 4, ASSAUT = 5, RESULTAT = 6, ANNONCE = 7;

    private final File dossier;
    private final Base[] bases = new Base[2];
    private int scene = BASE, cx = 8, cy = 6, choix, deplace = -1, repet, messageReste, attente, manche;
    private String message = "";
    private Base cible;
    private boolean pirate;
    private Assaut assaut;
    private final int[][] duel = new int[2][3];
    private int butin;

    Bastion(Contexte ctx) {
        super(ctx);
        dossier = new File(ctx.dossier(), "bastions");
        for (int i = 0; i < ctx.joueurs(); i++) {
            bases[i] = charger(ctx.ids()[i], ctx.noms()[i]);
        }
        if (ctx.deux()) {
            record = false;
            manche = 0;
            scene = ANNONCE;
            attente = 75;
        } else {
            recordContinu = true;
            score = bases[0].trophees;
        }
    }

    private File fichier(String id) {
        return new File(dossier, id + ".properties");
    }

    private Base charger(String id, String nom) {
        Base b = Base.lire(fichier(id));
        if (b == null) {
            b = Base.depart(nom, id);
        } else {
            // production hors ligne, 12 heures au plus
            double heures = Math.min(12, Math.max(0, (System.currentTimeMillis() - b.maj) / 3_600_000.0));
            b.energie = Math.max(b.energie, Math.min(b.capacite(), b.energie + b.production() * heures));
        }
        b.nom = nom;
        b.id = id;
        b.ecrire(fichier(id));
        return b;
    }

    private void dire(String s) {
        message = s;
        messageReste = 90;
    }

    private boolean payer(Base b, int cout) {
        if (b.energie < cout) {
            dire("Pas assez d'énergie : il manque " + (int) (cout - b.energie) + ".");
            son("touche");
            return false;
        }
        b.energie -= cout;
        return true;
    }

    /** Déplacement du curseur avec répétition quand on garde la touche. */
    private boolean curseur(Manette m, int maxX, int maxY) {
        int dx = (m.tenu(Manette.DROITE) ? 1 : 0) - (m.tenu(Manette.GAUCHE) ? 1 : 0), dy = (m.tenu(Manette.BAS) ? 1 : 0) - (m.tenu(Manette.HAUT) ? 1 : 0);
        boolean neuf = m.appui(Manette.DROITE) || m.appui(Manette.GAUCHE) || m.appui(Manette.BAS) || m.appui(Manette.HAUT);
        repet = dx == 0 && dy == 0 ? 0 : repet + 1;
        if (neuf || (repet > 7 && repet % 2 == 0)) {
            cx = Math.max(0, Math.min(maxX, cx + dx));
            cy = Math.max(0, Math.min(maxY, cy + dy));
            return true;
        }
        return false;
    }

    private int liste(Manette m, int n) {
        if (m.appui(Manette.HAUT)) {
            choix = (choix + n - 1) % n;
            son("clic");
        } else if (m.appui(Manette.BAS)) {
            choix = (choix + 1) % n;
            son("clic");
        }
        return choix;
    }

    private void nouvelleCible() {
        Base moi = bases[0];
        List<Base> autres = new ArrayList<>();
        File[] fichiers = dossier.listFiles((d, n) -> n.endsWith(".properties") && !n.startsWith(moi.id));
        if (fichiers != null) {
            for (File f : fichiers) {
                Base b = Base.lire(f);
                if (b != null) {
                    autres.add(b);
                }
            }
        }
        pirate = autres.isEmpty() || alea.nextInt(100) < 50;
        // avant-poste : le plus souvent de son niveau, parfois plus faible, plus rarement plus fort
        int tirage = alea.nextInt(100), ecart = tirage < 25 ? -1 : tirage < 80 ? 0 : 1;
        cible = pirate ? Base.pirate(alea, Math.max(1, Math.min(5, moi.niveau(NOYAU) + ecart))) : autres.get(alea.nextInt(autres.size()));
    }

    /** Une étoile vaut autant de trophées que le niveau du Noyau visé : les cibles faciles rapportent peu. */
    private int trophees(Base b, int etoiles) {
        return etoiles * Math.max(1, b.niveau(NOYAU));
    }

    private int butinMax(Base b) {
        return 300 + 450 * b.niveau(NOYAU);
    }

    @Override
    protected void tic(Manette a, Manette b) {
        messageReste = Math.max(0, messageReste - 1);
        if (ctx.deux()) {
            duel(a, b);
            return;
        }
        Base moi = bases[0];
        moi.energie = Math.min(Math.max(moi.energie, moi.capacite()), moi.energie + moi.production() / 3600.0 / 30);
        if (tics % 300 == 0) {
            moi.ecrire(fichier(moi.id));
        }
        switch (scene) {
            case BASE -> {
                curseur(a, GC - 1, GR - 1);
                int i = moi.en(cx, cy);
                if (a.appui(Manette.A)) {
                    choix = 0;
                    scene = i < 0 ? CONSTRUIRE : GERER;
                    son("clic");
                } else if (a.appui(Manette.B)) {
                    nouvelleCible();
                    scene = CIBLE;
                    son("clic");
                }
            }
            case CONSTRUIRE -> {
                int type = liste(a, 6) + 1;
                if (a.appui(Manette.B)) {
                    scene = BASE;
                } else if (a.appui(Manette.A)) {
                    int max = MAXI[type][moi.niveau(NOYAU) - 1];
                    if (moi.nombre(type) >= max) {
                        dire(max == 0 ? NOMS[type] + " : il faut un Noyau de niveau supérieur." : "Tu as déjà tous tes " + NOMS[type].toLowerCase() + "s : améliore le Noyau.");
                        son("touche");
                    } else if (payer(moi, COUT[type])) {
                        moi.bats.add(new int[]{type, cx, cy, 1});
                        moi.ecrire(fichier(moi.id));
                        son("pose");
                        scene = BASE;
                    }
                }
            }
            case GERER -> {
                int i = moi.en(cx, cy);
                if (i < 0) {
                    scene = BASE;
                    break;
                }
                int[] bat = moi.bats.get(i);
                int n = liste(a, bat[0] == NOYAU ? 2 : 3);
                if (a.appui(Manette.B)) {
                    scene = BASE;
                } else if (a.appui(Manette.A)) {
                    if (n == 0) {
                        int plafond = bat[0] == NOYAU ? 5 : moi.niveau(NOYAU);
                        if (bat[3] >= plafond) {
                            dire(bat[0] == NOYAU ? "Le Noyau est au niveau maximum." : "Améliore d'abord le Noyau (niveau " + moi.niveau(NOYAU) + ").");
                            son("touche");
                        } else if (payer(moi, coutAmelioration(bat[0], bat[3]))) {
                            bat[3]++;
                            moi.ecrire(fichier(moi.id));
                            son("bravo");
                        }
                    } else if (n == 1) {
                        deplace = i;
                        scene = DEPLACER;
                    } else {
                        moi.energie += COUT[bat[0]] / 2.0;
                        moi.bats.remove(i);
                        moi.ecrire(fichier(moi.id));
                        scene = BASE;
                    }
                }
            }
            case DEPLACER -> {
                curseur(a, GC - 1, GR - 1);
                if (a.appui(Manette.B)) {
                    scene = BASE;
                } else if (a.appui(Manette.A)) {
                    if (moi.en(cx, cy) >= 0 && moi.en(cx, cy) != deplace) {
                        dire("La case est déjà prise.");
                    } else {
                        moi.bats.get(deplace)[1] = cx;
                        moi.bats.get(deplace)[2] = cy;
                        moi.ecrire(fichier(moi.id));
                        son("pose");
                        scene = BASE;
                    }
                }
            }
            case CIBLE -> {
                if (a.appui(Manette.B)) {
                    scene = BASE;
                } else if (a.appui(Manette.GAUCHE) || a.appui(Manette.DROITE)) {
                    nouvelleCible();
                    son("clic");
                } else if (a.appui(Manette.A) && payer(moi, 100 * Math.max(1, moi.niveau(CASERNE)))) {
                    assaut = new Assaut(cible, moi.nom, moi.niveau(CASERNE));
                    scene = ASSAUT;
                    son("bravo");
                }
            }
            case ASSAUT -> {
                assaut.tic(a);
                if (assaut.termine) {
                    butin = butinMax(cible) * assaut.pct / 100;
                    moi.energie += butin;
                    moi.trophees += trophees(cible, assaut.etoiles);
                    score = moi.trophees;
                    moi.ecrire(fichier(moi.id));
                    scene = RESULTAT;
                    attente = 20;
                    son(assaut.etoiles > 0 ? "bravo" : "perdu");
                }
            }
            case RESULTAT -> {
                if (--attente < 0 && (a.appui(Manette.A) || a.appui(Manette.B))) {
                    scene = BASE;
                }
            }
            default -> scene = BASE;
        }
    }

    /** À deux : J2 attaque la base de J1, puis J1 celle de J2. */
    private void duel(Manette a, Manette b) {
        if (scene == ANNONCE) {
            if (--attente <= 0) {
                int att = manche == 0 ? 1 : 0;
                assaut = new Assaut(bases[1 - att], noms[att], bases[att].niveau(CASERNE));
                scene = ASSAUT;
            }
            return;
        }
        if (scene == ASSAUT) {
            int att = manche == 0 ? 1 : 0;
            assaut.tic(att == 0 ? a : b);
            if (assaut.termine) {
                duel[att][0] = assaut.etoiles;
                duel[att][1] = assaut.pct;
                duel[att][2] = Math.max(0, assaut.temps);
                scene = RESULTAT;
                attente = 75;
                son("bravo");
            }
            return;
        }
        if (--attente <= 0) {
            if (manche == 0) {
                manche = 1;
                scene = ANNONCE;
                attente = 75;
            } else {
                // étoiles, puis pourcentage, puis l'assaut le plus rapide
                int v0 = duel[0][0] * 10_000_000 + duel[0][1] * 10_000 + duel[0][2], v1 = duel[1][0] * 10_000_000 + duel[1][1] * 10_000 + duel[1][2];
                gagnant = v0 == v1 ? 0 : v0 > v1 ? 1 : 2;
                bilan = duel[0][0] + " ét. " + duel[0][1] + " % en " + (90 - duel[0][2] / 30) + " s   –   " + duel[1][0] + " ét. " + duel[1][1] + " % en " + (90 - duel[1][2] / 30) + " s";
                fini = true;
            }
        }
    }

    // ------------------------------------------------------------------ dessin

    static void etoile(Graphics2D g, int cx, int cy, int r, boolean pleine) {
        int[] xs = new int[10], ys = new int[10];
        for (int i = 0; i < 10; i++) {
            double a = -Math.PI / 2 + i * Math.PI / 5, rr = i % 2 == 0 ? r : r * 0.45;
            xs[i] = cx + (int) Math.round(Math.cos(a) * rr);
            ys[i] = cy + (int) Math.round(Math.sin(a) * rr);
        }
        g.setColor(pleine ? Dessin.OR : new Color(255, 255, 255, 40));
        g.fillPolygon(xs, ys, 10);
    }

    static void batiment(Graphics2D g, int type, int niveau, int x, int y, double vie) {
        int m = T / 2;
        if (vie <= 0) {
            g.setColor(new Color(0x2A2F3D));
            g.fillOval(x + 4, y + 9, 7, 6);
            g.fillOval(x + 10, y + 6, 8, 9);
            return;
        }
        switch (type) {
            case NOYAU -> {
                g.setColor(Dessin.alpha(Dessin.OR, 70));
                g.fillOval(x - 2, y - 2, T + 4, T + 4);
                g.setColor(Dessin.OR);
                g.fillPolygon(new int[]{x + m, x + T - 2, x + m, x + 2}, new int[]{y + 1, y + m, y + T - 1, y + m}, 4);
                g.setColor(Dessin.BLANC);
                g.fillOval(x + m - 3, y + m - 3, 6, 6);
            }
            case COLLECTEUR -> {
                g.setColor(new Color(0x1E4E66));
                g.fillOval(x + 2, y + 2, T - 4, T - 4);
                g.setColor(Dessin.GLACE);
                g.fillPolygon(new int[]{x + m + 2, x + m - 4, x + m, x + m - 2, x + m + 4, x + m}, new int[]{y + 3, y + m + 1, y + m + 1, y + T - 3, y + m - 1, y + m - 1}, 6);
            }
            case RESERVE -> {
                g.setColor(Dessin.BLEU);
                g.fillRoundRect(x + 2, y + 3, T - 4, T - 6, 6, 6);
                g.setColor(Dessin.GLACE);
                g.fillRect(x + 5, y + 7, T - 10, 2);
                g.fillRect(x + 5, y + 12, T - 10, 2);
            }
            case TOURELLE -> {
                g.setColor(new Color(0x56658C));
                g.fillRoundRect(x + 3, y + 3, T - 6, T - 6, 6, 6);
                g.setColor(Dessin.ROUGE);
                g.fillOval(x + m - 4, y + m - 4, 8, 8);
                g.fillRect(x + m - 1, y + 1, 3, m);
            }
            case MORTIER -> {
                g.setColor(new Color(0x3C3048));
                g.fillOval(x + 2, y + 2, T - 4, T - 4);
                g.setColor(Dessin.ORANGE);
                g.fillOval(x + 6, y + 6, T - 12, T - 12);
                g.setColor(Dessin.FOND);
                g.fillOval(x + 8, y + 8, T - 16, T - 16);
            }
            case MUR -> {
                g.setColor(new Color(0x6B7A9A));
                g.fillRoundRect(x + 2, y + 2, T - 4, T - 4, 4, 4);
                g.setColor(new Color(0x8494B5));
                g.fillRect(x + 4, y + 4, T - 8, 4);
            }
            default -> {
                g.setColor(new Color(0x2C6B45));
                g.fillRect(x + 3, y + m, T - 6, m - 3);
                g.setColor(Dessin.VERT);
                g.fillPolygon(new int[]{x + 1, x + m, x + T - 1}, new int[]{y + m, y + 2, y + m}, 3);
            }
        }
        if (type != MUR) {
            g.setColor(Dessin.BLANC);
            for (int k = 0; k < niveau; k++) {
                g.fillRect(x + 2 + k * 4, y + T - 3, 3, 2);
            }
        }
        if (vie < 1) {
            g.setColor(Dessin.FOND);
            g.fillRect(x + 2, y - 1, T - 4, 3);
            g.setColor(vie > 0.5 ? Dessin.VERT : vie > 0.25 ? Dessin.ORANGE : Dessin.ROUGE);
            g.fillRect(x + 2, y - 1, (int) ((T - 4) * vie), 3);
        }
    }

    static void base(Graphics2D g, Base b, double[] pv, double[] pvMax, int x0, int y0) {
        g.setColor(new Color(255, 255, 255, 9));
        for (int x = 0; x <= GC; x++) {
            g.drawLine(x0 + x * T, y0, x0 + x * T, y0 + GR * T);
        }
        for (int y = 0; y <= GR; y++) {
            g.drawLine(x0, y0 + y * T, x0 + GC * T, y0 + y * T);
        }
        for (int i = 0; i < b.bats.size(); i++) {
            int[] bat = b.bats.get(i);
            batiment(g, bat[0], bat[3], x0 + bat[1] * T, y0 + bat[2] * T, pv == null ? 1 : pv[i] / pvMax[i]);
        }
    }

    private void entete(Graphics2D g, Base moi) {
        g.setFont(Dessin.titre(20));
        g.setColor(Dessin.OR);
        g.drawString("BASTION ORBITAL", 20, 32);
        g.setFont(Dessin.texte(12));
        g.setColor(Dessin.GLACE);
        g.drawString("Énergie " + (int) moi.energie + " / " + moi.capacite() + "   (+" + moi.production() + " par heure)", 20, 52);
        etoile(g, 590, 26, 9, true);
        g.setFont(Dessin.titre(18));
        g.setColor(Dessin.OR);
        Dessin.droite(g, String.valueOf(moi.trophees), 576, 33);
        g.setFont(Dessin.texte(10));
        g.setColor(Dessin.GRIS);
        Dessin.droite(g, "Noyau niveau " + moi.niveau(NOYAU), 610, 52);
    }

    @Override
    public void dessiner(Graphics2D g) {
        Dessin.fond(g, tics, 0.05);
        if (scene == ASSAUT || (ctx.deux() && scene == RESULTAT)) {
            assaut.dessiner(g);
            if (scene == RESULTAT) {
                Dessin.panneau(g, 170, 150, 300, 70, Dessin.OR);
                g.setFont(Dessin.titre(18));
                g.setColor(Dessin.BLANC);
                Dessin.centre(g, assaut.attaquant + " : " + assaut.pct + " % détruit", W / 2, 180);
                for (int e = 0; e < 3; e++) {
                    etoile(g, W / 2 - 30 + e * 30, 200, 10, e < assaut.etoiles);
                }
            }
            return;
        }
        if (scene == ANNONCE) {
            int att = manche == 0 ? 1 : 0;
            Dessin.panneau(g, 110, 120, 420, 140, Dessin.OR);
            g.setFont(Dessin.titre(15));
            g.setColor(Dessin.GRIS);
            Dessin.centre(g, "ASSAUT " + (manche + 1) + " SUR 2", W / 2, 152);
            g.setFont(Dessin.titre(26));
            g.setColor(att == 0 ? Dessin.BLEU : Dessin.OR);
            Dessin.centre(g, noms[att] + " attaque", W / 2, 192);
            g.setFont(Dessin.texte(15));
            g.setColor(Dessin.BLANC);
            Dessin.centre(g, "la base de " + noms[1 - att], W / 2, 220);
            g.setFont(Dessin.texte(11));
            g.setColor(Dessin.GRIS);
            Dessin.centre(g, "Bâtis et améliore ta base en jouant seul.", W / 2, 244);
            return;
        }
        Base moi = bases[0];
        entete(g, moi);
        int x0 = 20, y0 = 64, px = 392;
        if (scene == CIBLE || scene == RESULTAT) {
            g.setColor(new Color(0x12203F));
            g.fillRect(x0, y0, GC * T, GR * T);
            base(g, cible, scene == RESULTAT ? assaut.pv : null, scene == RESULTAT ? assaut.pvMax : null, x0, y0);
            Dessin.panneau(g, px, y0, 228, 264, Dessin.alpha(Dessin.OR, 150));
            g.setFont(Dessin.texte(10));
            g.setColor(Dessin.GRIS);
            g.drawString(scene == RESULTAT ? "ASSAUT TERMINÉ" : pirate ? "AVANT-POSTE PIRATE" : "BASE D'UN JOUEUR", px + 14, y0 + 22);
            g.setFont(Dessin.titre(16));
            g.setColor(Dessin.OR);
            g.drawString(cible.nom, px + 14, y0 + 44);
            g.setFont(Dessin.texte(12));
            g.setColor(Dessin.BLANC);
            if (scene == RESULTAT) {
                for (int e = 0; e < 3; e++) {
                    etoile(g, px + 40 + e * 50, y0 + 84, 18, e < assaut.etoiles);
                }
                g.setColor(Dessin.BLANC);
                g.drawString(assaut.pct + " % de la base détruit", px + 14, y0 + 130);
                g.setColor(Dessin.GLACE);
                g.drawString("Butin : +" + butin + " énergie", px + 14, y0 + 152);
                g.setColor(Dessin.OR);
                g.drawString("Trophées : +" + trophees(cible, assaut.etoiles), px + 14, y0 + 174);
                g.setColor(Dessin.GRIS);
                g.drawString("Espace : revenir à ta base", px + 14, y0 + 244);
            } else {
                g.drawString("Noyau niveau " + cible.niveau(NOYAU), px + 14, y0 + 70);
                g.drawString(cible.nombre(TOURELLE) + " tourelles, " + cible.nombre(MORTIER) + " mortiers", px + 14, y0 + 90);
                g.drawString(cible.nombre(MUR) + " murs", px + 14, y0 + 110);
                g.setColor(Dessin.GLACE);
                g.drawString("Jusqu'à " + butinMax(cible) + " énergie, " + trophees(cible, 3) + " trophées", px + 14, y0 + 140);
                int[] armee = ARMEES[Math.min(5, moi.niveau(CASERNE))];
                g.setColor(Dessin.BLANC);
                g.drawString("Ton armée : " + armee[0] + " soldats, " + armee[1] + " tireurs", px + 14, y0 + 170);
                g.drawString(armee[2] + " sapeurs, " + armee[3] + " colosses", px + 14, y0 + 188);
                g.setColor(Dessin.OR);
                g.drawString("Espace : attaquer (" + 100 * Math.max(1, moi.niveau(CASERNE)) + " énergie)", px + 14, y0 + 218);
                g.setColor(Dessin.GRIS);
                g.drawString("Q D : autre cible   ·   Maj : retour", px + 14, y0 + 244);
            }
        } else {
            g.setColor(new Color(0x12203F));
            g.fillRect(x0, y0, GC * T, GR * T);
            base(g, moi, null, null, x0, y0);
            if (scene == DEPLACER) {
                int[] bat = moi.bats.get(deplace);
                g.setColor(Dessin.alpha(Dessin.OR, 60));
                g.fillRect(x0 + bat[1] * T, y0 + bat[2] * T, T, T);
            }
            g.setStroke(new BasicStroke(2f));
            g.setColor(tics % 20 < 14 ? Dessin.OR : Dessin.BLANC);
            g.drawRoundRect(x0 + cx * T + 1, y0 + cy * T + 1, T - 2, T - 2, 6, 6);
            g.setStroke(new BasicStroke(1f));
            Dessin.panneau(g, px, y0, 228, 264, Dessin.alpha(Dessin.GLACE, 120));
            int i = moi.en(cx, cy);
            g.setFont(Dessin.texte(12));
            if (scene == CONSTRUIRE) {
                g.setColor(Dessin.OR);
                g.setFont(Dessin.titre(14));
                g.drawString("CONSTRUIRE ICI", px + 14, y0 + 24);
                for (int k = 0; k < 6; k++) {
                    int type = k + 1, max = MAXI[type][moi.niveau(NOYAU) - 1], y = y0 + 36 + k * 30;
                    boolean possible = moi.nombre(type) < max && moi.energie >= COUT[type];
                    Dessin.panneau(g, px + 10, y, 208, 26, k == choix ? Dessin.OR : Dessin.alpha(Dessin.BLEU, 70));
                    batiment(g, type, 1, px + 14, y + 2, 1);
                    g.setFont(Dessin.texte(12));
                    g.setColor(possible ? Dessin.BLANC : Dessin.GRIS);
                    g.drawString(NOMS[type] + "  " + moi.nombre(type) + "/" + max, px + 42, y + 17);
                    g.setColor(possible ? Dessin.GLACE : Dessin.GRIS);
                    Dessin.droite(g, String.valueOf(COUT[type]), px + 210, y + 17);
                }
                g.setFont(Dessin.texte(10));
                g.setColor(Dessin.GRIS);
                g.drawString(ROLES[choix + 1] + ".  Maj : annuler", px + 14, y0 + 236);
            } else if (scene == GERER && i >= 0) {
                int[] bat = moi.bats.get(i);
                g.setFont(Dessin.titre(15));
                g.setColor(Dessin.OR);
                g.drawString(NOMS[bat[0]] + "  niv. " + bat[3], px + 14, y0 + 26);
                g.setFont(Dessin.texte(11));
                g.setColor(Dessin.GRIS);
                g.drawString(ROLES[bat[0]] + " · " + (int) (PV[bat[0]] * facteur(bat[3])) + " PV", px + 14, y0 + 44);
                int plafond = bat[0] == NOYAU ? 5 : moi.niveau(NOYAU);
                String ameliorer = bat[3] >= 5 ? "Niveau maximum" : bat[3] >= plafond ? "Améliorer : Noyau niv. " + (plafond + 1) + " requis"
                        : "Améliorer   " + coutAmelioration(bat[0], bat[3]);
                String[] options = {ameliorer, "Déplacer", "Retirer   +" + COUT[bat[0]] / 2};
                for (int k = 0; k < (bat[0] == NOYAU ? 2 : 3); k++) {
                    int y = y0 + 60 + k * 32;
                    Dessin.panneau(g, px + 10, y, 208, 26, k == choix ? Dessin.OR : Dessin.alpha(Dessin.BLEU, 70));
                    g.setFont(Dessin.texte(12));
                    g.setColor(Dessin.BLANC);
                    g.drawString(options[k], px + 22, y + 17);
                }
                g.setFont(Dessin.texte(10));
                g.setColor(Dessin.GRIS);
                g.drawString("Espace : valider   ·   Maj : fermer", px + 14, y0 + 236);
            } else {
                g.setColor(Dessin.BLANC);
                if (scene == DEPLACER) {
                    g.drawString("Choisis la nouvelle case.", px + 14, y0 + 26);
                    g.setColor(Dessin.GRIS);
                    g.drawString("Espace : poser   ·   Maj : annuler", px + 14, y0 + 46);
                } else if (i >= 0) {
                    int[] bat = moi.bats.get(i);
                    g.setFont(Dessin.titre(15));
                    g.setColor(Dessin.OR);
                    g.drawString(NOMS[bat[0]] + "  niv. " + bat[3], px + 14, y0 + 26);
                    g.setFont(Dessin.texte(11));
                    g.setColor(Dessin.GRIS);
                    g.drawString(ROLES[bat[0]] + ".", px + 14, y0 + 44);
                    g.setColor(Dessin.BLANC);
                    g.drawString("Espace : améliorer, déplacer…", px + 14, y0 + 66);
                } else {
                    g.drawString("Case libre.", px + 14, y0 + 26);
                    g.drawString("Espace : construire ici", px + 14, y0 + 46);
                }
                g.setFont(Dessin.texte(11));
                g.setColor(Dessin.GRIS);
                for (int k = 1; k <= 6; k++) {
                    batiment(g, k, 1, px + 14, y0 + 78 + k * 24 - 16, 1);
                    g.setColor(Dessin.GRIS);
                    g.setFont(Dessin.texte(11));
                    g.drawString(NOMS[k] + "  " + moi.nombre(k) + "/" + MAXI[k][moi.niveau(NOYAU) - 1], px + 44, y0 + 78 + k * 24);
                }
                g.setColor(Dessin.OR);
                g.setFont(Dessin.texte(12));
                g.drawString("Maj : partir à l'attaque", px + 14, y0 + 248);
            }
        }
        g.setFont(Dessin.texte(12));
        g.setColor(messageReste > 0 ? Dessin.ORANGE : Dessin.GRIS);
        Dessin.centre(g, messageReste > 0 ? message : "Z Q S D : curseur   ·   Espace : valider   ·   Maj : retour ou attaque   ·   F : quitter", W / 2, 356);
    }
}
