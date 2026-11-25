package fr.orbit.tele;

import fr.orbit.tele.jeux.Dessin;
import fr.orbit.tele.jeux.Jeu;
import fr.orbit.tele.jeux.Manette;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * La console d'un écran, occupée par un ou deux joueurs assis : menu, partie, fin de partie. La boucle (30 images
 * par seconde) tourne hors du fil du serveur ; elle ne touche au serveur que pour envoyer l'image et, par une tâche,
 * pour les sons et les départs.
 */
final class Session {

    private static final Map<String, String[]> SONS = Map.of(
            "clic", new String[]{"minecraft:block.note_block.hat", "1.4"},
            "pose", new String[]{"minecraft:block.note_block.basedrum", "1.2"},
            "ligne", new String[]{"minecraft:block.note_block.bit", "1.2"},
            "bravo", new String[]{"minecraft:block.note_block.bit", "1.8"},
            "piece", new String[]{"minecraft:block.note_block.bell", "1.6"},
            "tir", new String[]{"minecraft:block.note_block.snare", "1.9"},
            "touche", new String[]{"minecraft:block.note_block.bit", "0.9"},
            "boum", new String[]{"minecraft:block.note_block.basedrum", "0.6"},
            "perdu", new String[]{"minecraft:block.note_block.didgeridoo", "0.7"});

    final OrbitTele plugin;
    final Ecran ecran;

    private volatile Player[] joueurs = new Player[2];
    private volatile Manette[] manettes = {new Manette(), new Manette()};
    private final ItemDisplay[] sieges = new ItemDisplay[2];
    private final Location[] retours = new Location[2];
    private final AtomicInteger retourDemande = new AtomicInteger();

    private final BufferedImage image;
    private final int[] points;
    private final Graphics2D g;
    private final AffineTransform cadre;
    private ScheduledFuture<?> boucle;
    private volatile boolean arretee;
    private volatile Jeu jeu;
    private volatile boolean jeuADeux;
    private Jeu.Fiche fiche = Jeu.LISTE.get(0);
    private int choix, finDepuis, rang, publie;
    private long tics;

    Session(OrbitTele plugin, Ecran ecran) {
        this.plugin = plugin;
        this.ecran = ecran;
        image = new BufferedImage(ecran.W, ecran.H, BufferedImage.TYPE_INT_RGB);
        points = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        g = image.createGraphics();
        Dessin.lisse(g);
        double e = Math.min(ecran.W / (double) Jeu.W, ecran.H / (double) Jeu.H);
        cadre = new AffineTransform();
        cadre.translate((ecran.W - Jeu.W * e) / 2, (ecran.H - Jeu.H * e) / 2);
        cadre.scale(e, e);
    }

    // ------------------------------------------------------------------ joueurs (fil du serveur)

    /** « nxm3 » ou « nxm3 + Waneuh ». */
    String noms() {
        Player[] js = joueurs;
        return js[0] == null ? "" : js[1] == null ? js[0].getName() : js[0].getName() + " + " + js[1].getName();
    }

    String titre() {
        return jeu == null ? "menu" : fiche.titre();
    }

    boolean complet() {
        return joueurs[1] != null;
    }

    boolean arretee() {
        return arretee;
    }

    private int indice(UUID id) {
        Player[] js = joueurs;
        return js[0] != null && js[0].getUniqueId().equals(id) ? 0 : js[1] != null && js[1].getUniqueId().equals(id) ? 1 : -1;
    }

    /** Le joueur est-il assis ici (et pas en train de se lever) ? */
    boolean retient(UUID id) {
        return !arretee && indice(id) >= 0;
    }

    Manette manette(UUID id) {
        int i = indice(id);
        return i < 0 ? null : manettes[i];
    }

    /** Touche F. */
    void retour(UUID id) {
        int i = indice(id);
        if (i >= 0) {
            manettes[i].dernier = System.currentTimeMillis();
            retourDemande.getAndUpdate(v -> v | (1 << i));
        }
    }

    /** Premier joueur : il s'assoit et la console s'allume. */
    void demarrer(Player p) {
        asseoir(0, p);
        ecran.session = this;
        ecran.mode = Ecran.Mode.CONSOLE;
        boucle = plugin.horloge().scheduleAtFixedRate(this::image, 100, 33, TimeUnit.MILLISECONDS);
        ecran.masquerEtiquette();
    }

    /** Second joueur. */
    void rejoindre(Player p) {
        asseoir(1, p);
        ecran.masquerEtiquette();
    }

    private void asseoir(int i, Player p) {
        retours[i] = p.getLocation();
        Location place = ecran.place(i);
        place.setYaw(Ecran.lacet(ecran.face.getOppositeFace()));
        place.setPitch(-8f);
        if (p.isInsideVehicle()) {
            p.leaveVehicle();
        }
        Player[] js = joueurs.clone();
        js[i] = p;
        joueurs = js;
        manettes[i].dernier = System.currentTimeMillis();
        plugin.sessions().put(p.getUniqueId(), this);
        p.teleport(place);
        ItemDisplay siege = place.getWorld().spawn(place.clone().add(0, plugin.getConfig().getDouble("console.hauteur-siege", 0.45), 0), ItemDisplay.class, s -> {
            s.setPersistent(false);
            s.getPersistentDataContainer().set(plugin.cle(), PersistentDataType.STRING, ecran.nom);
            s.setRotation(place.getYaw(), 0f);
        });
        sieges[i] = siege;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!arretee && siege.isValid() && p.isOnline() && indice(p.getUniqueId()) >= 0) {
                siege.addPassenger(p);
            }
        }, 2L);
        p.sendActionBar(plugin.mm(i == 0
                ? "<#E0B864>Z S</#E0B864> choisir · <#E0B864>Espace</#E0B864> valider · <#E0B864>F</#E0B864> retour / quitter"
                : "Tu es <#E0B864>J2</#E0B864> · le J1 choisit le jeu · <#E0B864>F</#E0B864> pour te lever"));
    }

    /** Remet assis un joueur que son client a fait descendre. */
    void surveiller() {
        Player[] js = joueurs;
        for (int i = 0; i < 2; i++) {
            if (!arretee && js[i] != null && sieges[i] != null && sieges[i].isValid() && js[i].getVehicle() == null) {
                sieges[i].addPassenger(js[i]);
            }
        }
    }

    /** Un joueur se lève. S'il reste quelqu'un, il devient J1 et la console revient au menu. */
    void partir(Player p, boolean ramener) {
        int i = indice(p.getUniqueId());
        if (i < 0) {
            return;
        }
        Player[] js = joueurs.clone();
        js[i] = null;
        ItemDisplay siege = sieges[i];
        Location retour = retours[i];
        sieges[i] = null;
        retours[i] = null;
        if (i == 0 && js[1] != null) {
            js[0] = js[1];
            js[1] = null;
            sieges[0] = sieges[1];
            retours[0] = retours[1];
            sieges[1] = null;
            retours[1] = null;
            Manette[] ms = manettes;
            manettes = new Manette[]{ms[1], new Manette()};
        } else if (i == 1) {
            manettes = new Manette[]{manettes[0], new Manette()};
        }
        joueurs = js;
        plugin.sessions().remove(p.getUniqueId());
        if (siege != null) {
            siege.eject();
            siege.remove();
        }
        if (ramener && p.isOnline() && retour != null && retour.getWorld() != null) {
            p.teleport(retour);
        }
        if (js[0] == null) {
            arreter(false);
        } else {
            if (jeuADeux) {
                jeu = null;
            }
            js[0].sendActionBar(plugin.mm("<gray>" + p.getName() + " s'est levé. Tu es <#E0B864>J1</#E0B864>."));
        }
    }

    /** Fin de session : tout le monde se lève, l'écran repasse en veille. */
    void arreter(boolean ramener) {
        if (arretee) {
            return;
        }
        arretee = true;
        if (boucle != null) {
            boucle.cancel(false);
        }
        Player[] js = joueurs;
        joueurs = new Player[2];
        for (int i = 0; i < 2; i++) {
            if (sieges[i] != null) {
                sieges[i].eject();
                sieges[i].remove();
            }
            if (js[i] != null) {
                plugin.sessions().remove(js[i].getUniqueId());
                if (ramener && js[i].isOnline() && retours[i] != null && retours[i].getWorld() != null) {
                    js[i].teleport(retours[i]);
                }
            }
        }
        if (ecran.session == this) {
            ecran.session = null;
            ecran.mode = Ecran.Mode.VEILLE;
        }
    }

    private void lever(Player p) {
        if (p != null) {
            Bukkit.getScheduler().runTask(plugin, () -> partir(p, true));
        }
    }

    private void son(String nom) {
        String[] s = SONS.get(nom);
        if (s == null || arretee || !plugin.isEnabled()) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () ->
                ecran.console.getWorld().playSound(ecran.console, s[0], 0.5f, Float.parseFloat(s[1])));
    }

    // ------------------------------------------------------------------ boucle

    private void image() {
        if (arretee) {
            return;
        }
        try {
            tics++;
            Player[] js = joueurs;
            Manette[] ms = manettes;
            if (js[0] == null) {
                return;
            }
            ms[0].lire();
            ms[1].lire();
            int retour = retourDemande.getAndSet(0);
            Jeu j = jeu;
            if (j == null) {
                long inactif = System.currentTimeMillis() - Math.max(ms[0].dernier, js[1] == null ? 0 : ms[1].dernier);
                if (manette(ms, Manette.HAUT)) {
                    choix = (choix + Jeu.LISTE.size() - 1) % Jeu.LISTE.size();
                    son("clic");
                } else if (manette(ms, Manette.BAS)) {
                    choix = (choix + 1) % Jeu.LISTE.size();
                    son("clic");
                } else if (ms[0].appui(Manette.A)) {
                    lancer(js);
                } else if (inactif > 90_000) {
                    Bukkit.getScheduler().runTask(plugin, () -> arreter(true));
                    return;
                } else {
                    if ((retour & 2) != 0) {
                        lever(js[1]);
                    }
                    if ((retour & 1) != 0) {
                        lever(js[0]);
                    }
                }
            } else if (!j.fini) {
                if (retour != 0) {
                    jeu = null;
                } else {
                    j.avancer(ms[0], ms[1]);
                    if (j.recordContinu && j.score > publie) {
                        publie = j.score;
                        plugin.scores().ajouter(fiche.id(), js[0].getName(), j.score);
                    }
                    if (j.fini) {
                        finDepuis = 0;
                        rang = j.record ? plugin.scores().ajouter(fiche.id(), nomRecord(js, j), j.score) : 0;
                    }
                }
            } else {
                finDepuis++;
                if (retour != 0 || finDepuis > 30 * 45) {
                    jeu = null;
                } else if ((ms[0].appui(Manette.A) || ms[1].appui(Manette.A)) && finDepuis > 20) {
                    lancer(js);
                }
            }
            g.setTransform(new AffineTransform());
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, ecran.W, ecran.H);
            g.setTransform(cadre);
            g.setClip(0, 0, Jeu.W, Jeu.H);
            j = jeu;
            if (j == null) {
                menu(js);
            } else {
                Graphics2D gj = (Graphics2D) g.create();
                j.dessiner(gj);
                gj.dispose();
                if (j.fini) {
                    fin(j);
                }
            }
            g.setClip(null);
            ecran.pousser(points, false);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Console de l'écran " + ecran.nom + " arrêtée sur une erreur", t);
            Bukkit.getScheduler().runTask(plugin, () -> arreter(true));
        }
    }

    /** Au menu, seul le J1 choisit. */
    private static boolean manette(Manette[] ms, int touche) {
        return ms[0].appui(touche);
    }

    private static String nomRecord(Player[] js, Jeu j) {
        return js[1] != null && j.record && !j.recordContinu ? js[0].getName() + " & " + js[1].getName() : js[0].getName();
    }

    private void lancer(Player[] js) {
        fiche = Jeu.LISTE.get(choix);
        boolean deux = js[1] != null;
        Jeu.Contexte c = new Jeu.Contexte(deux ? 2 : 1,
                new String[]{js[0].getName(), deux ? js[1].getName() : ""},
                new String[]{js[0].getUniqueId().toString(), deux ? js[1].getUniqueId().toString() : ""},
                plugin.getDataFolder());
        Jeu n = fiche.creer().apply(c);
        n.sons = this::son;
        publie = n.recordContinu ? n.score : 0;
        jeuADeux = deux;
        jeu = n;
        son("bravo");
    }

    private void menu(Player[] js) {
        Dessin.fond(g, tics, 0.2);
        g.setFont(Dessin.titre(28));
        g.setColor(Dessin.OR);
        g.drawString("CONSOLE ORBIT", 28, 40);
        g.setFont(Dessin.texte(12));
        g.setColor(Dessin.BLEU);
        g.drawString("J1  " + js[0].getName(), 30, 60);
        g.setColor(js[1] == null ? Dessin.GRIS : Dessin.OR);
        g.drawString(js[1] == null ? "J2  place libre : clic droit sur la console" : "J2  " + js[1].getName(), 150 + g.getFontMetrics().stringWidth(js[0].getName()) / 2, 60);
        for (int i = 0; i < Jeu.LISTE.size(); i++) {
            Jeu.Fiche f = Jeu.LISTE.get(i);
            boolean ici = i == choix;
            int y = 72 + i * 38;
            Dessin.panneau(g, 28, y, 336, 33, ici ? Dessin.OR : Dessin.alpha(Dessin.BLEU, 90));
            if (ici) {
                g.setColor(Dessin.alpha(Dessin.OR, 28 + (int) (Math.sin(tics * 0.2) * 12)));
                g.fillRoundRect(28, y, 336, 33, 12, 12);
                g.setColor(Dessin.OR);
                g.fillPolygon(new int[]{37, 46, 37}, new int[]{y + 10, y + 17, y + 24}, 3);
            }
            g.setFont(Dessin.titre(15));
            g.setColor(ici ? Dessin.BLANC : Dessin.GLACE);
            g.drawString(f.titre(), 54, y + 16);
            g.setFont(Dessin.texte(10));
            g.setColor(Dessin.GRIS);
            g.drawString(f.resume(), 54, y + 28);
        }
        Jeu.Fiche f = Jeu.LISTE.get(choix);
        Dessin.panneau(g, 384, 72, 228, 261, Dessin.alpha(Dessin.GLACE, 120));
        g.setFont(Dessin.titre(15));
        g.setColor(Dessin.OR);
        g.drawString(f.records() ? "RECORDS" : "DUEL", 398, 96);
        g.setFont(Dessin.texte(11));
        g.setColor(Dessin.GRIS);
        g.drawString(f.titre(), 398, 112);
        List<Scores.Ligne> top = plugin.scores().top(f.id());
        if (!f.records()) {
            g.drawString("Un contre un, pas de classement.", 398, 142);
            g.drawString("Le premier à gagner a raison.", 398, 158);
        } else if (top.isEmpty()) {
            g.drawString("Personne n'a encore joué.", 398, 142);
            g.drawString("Le premier record est pour toi.", 398, 158);
        }
        for (int i = 0; f.records() && i < top.size(); i++) {
            int y = 142 + i * 25;
            g.setFont(Dessin.titre(14));
            g.setColor(i == 0 ? Dessin.OR : Dessin.GLACE);
            g.drawString((i + 1) + ".", 398, y);
            g.setColor(Dessin.BLANC);
            g.setFont(Dessin.texte(12));
            String nom = top.get(i).nom();
            g.drawString(nom.length() > 19 ? nom.substring(0, 18) + "…" : nom, 418, y);
            g.setFont(Dessin.titre(14));
            g.setColor(Dessin.OR);
            Dessin.droite(g, String.valueOf(top.get(i).score()), 600, y);
        }
        g.setFont(Dessin.texte(11));
        g.setColor(js[1] != null ? Dessin.OR : Dessin.GRIS);
        g.drawString("À deux : " + f.aDeux(), 398, 290);
        g.setFont(Dessin.texte(10));
        g.setColor(Dessin.GRIS);
        String t = f.touches();
        int coupe = t.length() > 40 ? t.lastIndexOf(" · ", 40) : -1;
        g.drawString(coupe < 0 ? t : t.substring(0, coupe), 398, 308);
        if (coupe >= 0) {
            g.drawString(t.substring(coupe + 3), 398, 322);
        }
        g.setFont(Dessin.texte(12));
        g.setColor(Dessin.BLANC);
        Dessin.centre(g, "Z S  choisir     Espace  jouer     F  se lever", Jeu.W / 2, 364);
    }

    private void fin(Jeu j) {
        g.setColor(new Color(4, 7, 16, Math.min(190, finDepuis * 12)));
        g.fillRect(0, 0, Jeu.W, Jeu.H);
        if (finDepuis < 8) {
            return;
        }
        Dessin.panneau(g, 150, 96, 340, 192, Dessin.OR);
        g.setColor(Dessin.BLANC);
        if (j.gagnant > 0 || !j.record) {
            g.setFont(Dessin.titre(22));
            Dessin.centre(g, j.gagnant > 0 ? "VICTOIRE" : "ÉGALITÉ", Jeu.W / 2, 136);
            g.setFont(Dessin.titre(34));
            g.setColor(j.gagnant == 2 ? Dessin.OR : Dessin.GLACE);
            Dessin.centre(g, j.gagnant > 0 ? j.noms[j.gagnant - 1] : "personne ne gagne", Jeu.W / 2, 186);
            if (j.bilan != null) {
                g.setFont(Dessin.texte(15));
                g.setColor(Dessin.BLANC);
                Dessin.centre(g, j.bilan, Jeu.W / 2, 220);
            }
        } else {
            g.setFont(Dessin.titre(24));
            Dessin.centre(g, "PARTIE TERMINÉE", Jeu.W / 2, 136);
            g.setFont(Dessin.titre(44));
            g.setColor(Dessin.OR);
            Dessin.centre(g, String.valueOf(j.score), Jeu.W / 2, 194);
            g.setFont(Dessin.texte(13));
            g.setColor(rang == 1 ? Dessin.VERT : Dessin.GLACE);
            Dessin.centre(g, rang == 1 ? "Nouveau record de la console !" : rang > 0 ? "Vous entrez au classement : place " + rang : "Record à battre : " + plugin.scores().meilleur(fiche.id()), Jeu.W / 2, 222);
        }
        g.setFont(Dessin.texte(13));
        g.setColor(Dessin.BLANC);
        Dessin.centre(g, "Espace  rejouer        F  menu", Jeu.W / 2, 262);
    }
}
