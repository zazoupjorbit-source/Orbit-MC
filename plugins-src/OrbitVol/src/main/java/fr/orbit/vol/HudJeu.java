package fr.orbit.vol;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * HUD HD (pack chargé) : panneau d'argent en haut de l'écran (barre de boss blanche rendue invisible par le pack),
 * barre de vol au-dessus de la barre d'objets, bannières de titre, icônes de la barre de droite.
 * Sans pack, tout retombe sur du texte simple.
 * Les largeurs des glyphes viennent de glyphes.txt, écrit par tools/Atelier.cs.
 */
public final class HudJeu {

    static final Key HUD = Key.key("orbitvol", "hud");
    static final Key TITRE = Key.key("orbitvol", "titre");
    static final Key C_HAUT = Key.key("orbitvol", "c_haut");
    static final Key C_REVENU = Key.key("orbitvol", "c_revenu");
    static final Key C_VOL = Key.key("orbitvol", "c_vol");
    static final int LARGEUR_PANNEAU = 200;

    final Pack pack;
    final Map<String, Integer> largeurs = new HashMap<>();
    final Map<UUID, BossBar> panneaux = new HashMap<>();
    /** Glyphes de plus de 256 px découpés en bandes : caractère → suite de bandes. */
    static final Map<String, String> tuiles = new HashMap<>();

    HudJeu(OrbitVol plugin, Pack pack) {
        this.pack = pack;
        try (InputStream in = plugin.getResource("glyphes.txt")) {
            if (in != null) {
                BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                String l;
                while ((l = r.readLine()) != null) {
                    String[] t = l.trim().split(" ");
                    if (t.length == 3) largeurs.put(t[0] + ":" + Integer.parseInt(t[1], 16), Integer.parseInt(t[2]));
                    else if (t.length == 4 && t[0].equals("tuile")) {
                        StringBuilder sb = new StringBuilder();
                        for (String h : t[3].split(",")) sb.append((char) Integer.parseInt(h, 16));
                        tuiles.put(t[1] + ":" + (char) Integer.parseInt(t[2], 16), sb.toString());
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("glyphes.txt illisible : " + e.getMessage());
        }
    }

    boolean hd(Player p) {
        return pack.charge(p);
    }

    int largeur(String police, String texte) {
        int w = 0;
        for (int i = 0; i < texte.length(); i++) w += largeurs.getOrDefault(police + ":" + (int) texte.charAt(i), 6);
        return w;
    }

    /** Déplace le curseur de n pixels (négatif = vers la gauche), en caractères d'espace de la police hud. */
    static String espace(int n) {
        StringBuilder sb = new StringBuilder();
        int base = n < 0 ? 0xF801 : 0xF821;
        int reste = Math.abs(n);
        for (int bit = 7; bit >= 0; bit--) {
            int v = 1 << bit;
            while (reste >= v) {
                sb.append((char) (base + bit));
                reste -= v;
            }
        }
        return sb.toString();
    }

    static Component glyphe(Key police, String s) {
        return Component.text(deplier(police, s)).font(police).color(TextColor.color(0xFFFFFF)).shadowColor(ShadowColor.none());
    }

    static Component glyphe(Key police, String s, int couleur) {
        return Component.text(deplier(police, s)).font(police).color(TextColor.color(couleur)).shadowColor(ShadowColor.none());
    }

    static String deplier(Key police, String s) {
        if (tuiles.isEmpty()) return s;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            String t = tuiles.get(police.value() + ":" + s.charAt(i));
            sb.append(t != null ? t : String.valueOf(s.charAt(i)));
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ panneau d'argent

    void panneau(Player p, Profil pr, double revenu) {
        BossBar b = panneaux.get(p.getUniqueId());
        if (!hd(p)) {
            if (b != null) { p.hideBossBar(b); panneaux.remove(p.getUniqueId()); }
            return;
        }
        String credits = Txt.n(pr.credits) + " ✦";
        String rev = "+" + Txt.n(revenu) + "/s";
        int a = largeur("hud", "");
        int t1 = largeur("c_haut", credits);
        int t2 = largeur("c_revenu", rev);
        int xRev = 161 - t2 / 2;   // centré dans la pastille verte (128..194)
        Component titre = Component.empty()
                .append(glyphe(HUD, ""))
                .append(glyphe(HUD, espace(26 - a)))
                .append(glyphe(C_HAUT, credits, 0xFFE08A))
                .append(glyphe(HUD, espace(xRev - 26 - t1)))
                .append(glyphe(C_REVENU, rev, 0x7CFC9A))
                .append(glyphe(HUD, espace(a - xRev - t2)));
        if (b == null) {
            b = BossBar.bossBar(titre, 0f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
            panneaux.put(p.getUniqueId(), b);
            p.showBossBar(b);
        } else b.name(titre);
    }

    void oublier(Player p) {
        BossBar b = panneaux.remove(p.getUniqueId());
        if (b != null) p.hideBossBar(b);
    }

    // ------------------------------------------------------------------ barre de vol

    void barreVol(Player p, double fraction, long secondes, String texteSimple) {
        if (!hd(p)) { Txt.bar(p, texteSimple); return; }
        int k = (int) Math.max(0, Math.min(10, Math.round(fraction * 10)));
        String g = String.valueOf((char) (0xE020 + k));
        int a = largeur("hud", g);
        String s = secondes + " s";
        int ts = largeur("c_vol", s);
        int x = (a - ts) / 2;
        Component c = Component.empty()
                .append(glyphe(HUD, g))
                .append(glyphe(HUD, espace(x - a)))
                .append(glyphe(C_VOL, s, 0xFFFFFF))
                .append(glyphe(HUD, espace(a - x - ts)));
        p.sendActionBar(c);
    }

    // ------------------------------------------------------------------ bannières

    static final Map<String, String> BANNIERES = Map.of(
            "b_vole", "", "b_voleur", "", "b_secret", "", "b_meteores", "",
            "b_ascension", "", "b_quete", "", "b_bienvenue", "");

    void banniere(Player p, String id, String titreSimple, String sousTitre) {
        Component titre = hd(p) ? glyphe(TITRE, BANNIERES.get(id)) : Txt.mm(titreSimple);
        p.showTitle(Title.title(titre, Txt.mm("<#E6EDF3>" + sousTitre),
                Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(2200), Duration.ofMillis(500))));
    }

    // ------------------------------------------------------------------ icônes de la barre de droite

    /** Icône (police hud) suivie d'un petit espace, ou rien sans pack. */
    Component icone(Player p, char c) {
        if (!hd(p)) return Component.empty();
        return Component.empty().append(glyphe(HUD, String.valueOf(c))).append(glyphe(HUD, espace(2)));
    }
}
