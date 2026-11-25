package fr.orbit.arene.nebuleuse;

import fr.orbit.arene.Jeu;
import fr.orbit.arene.OrbitArene;
import fr.orbit.arene.Partie;
import fr.orbit.arene.Txt;
import io.papermc.paper.entity.LookAnchor;
import io.papermc.paper.entity.TeleportFlag;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.joml.Quaternionf;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Nébuleuse · Comète (mécanique serpent / traînée lumineuse). Chacun file tout droit sur une grille et laisse une
 * traînée solide ; toucher une traînée (même la sienne) ou le bord = éliminé. Les orbes allongent la traînée.
 * Q/D tournent (par rapport à la comète), Espace accélère (coûte de la longueur). Le joueur est assis sur un
 * support déplacé chaque tick. Score = plus grande longueur atteinte.
 */
public final class PartieComete extends Partie {

    static final int N = 46;                 // grille N × N cases de 1 bloc
    static final int[] DX = {0, -1, 0, 1};   // 0 = sud (+z), 1 = ouest, 2 = nord, 3 = est (comme le yaw)
    static final int[] DZ = {1, 0, -1, 0};

    private static final Material[] TRACES = {Material.BLUE_STAINED_GLASS, Material.YELLOW_STAINED_GLASS, Material.MAGENTA_STAINED_GLASS,
            Material.LIME_STAINED_GLASS, Material.ORANGE_STAINED_GLASS, Material.LIGHT_BLUE_STAINED_GLASS, Material.PURPLE_STAINED_GLASS,
            Material.RED_STAINED_GLASS};

    final class Comete {
        final Player p;
        Mannequin bot;
        final String nom;
        final Material trace;
        final Color couleur;
        int x, z, dir, longueur = 6, pic = 6, tourne, mortA = -1, rang;
        final Deque<int[]> corps = new ArrayDeque<>();
        boolean vivant = true, boost, parti;
        int id;
        double avance;
        ItemDisplay siege;
        int boostTicks;

        Comete(Player p, Mannequin bot, String nom, Material trace, Color couleur) {
            this.p = p;
            this.bot = bot;
            this.nom = nom;
            this.trace = trace;
            this.couleur = couleur;
        }
    }

    private final SplittableRandom rnd = new SplittableRandom();
    private final List<Comete> cometes = new ArrayList<>();
    private final int[][] grille = new int[N][N];          // 0 = libre, sinon index de comète + 1
    private final BlockDisplay[][] orbes = new BlockDisplay[N][N];
    private int nbOrbes, depart = 80, reste, morts;
    private final int periode;
    private final int bx, bz;                               // coin (0,0) de la grille

    public PartieComete(OrbitArene pl, Jeu.Mode mode, int slot) {
        super(pl, mode, slot);
        periode = Math.max(2, pl.getConfig().getInt("nebuleuse.comete.ticks-par-case", 3));
        reste = pl.getConfig().getInt("nebuleuse.comete.duree", 180) * 20;
        bx = ox + 4;
        bz = oz + 4;
    }

    @Override
    protected int[] taille() {
        return new int[]{N + 8, N + 8};
    }

    @Override
    public boolean assis(Player p) {
        for (Comete c : cometes) {
            if (c.p == p) {
                return c.vivant;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ arène

    private void construire() {
        int y = Y0 - 1;
        for (int gx = -1; gx <= N; gx++) {
            for (int gz = -1; gz <= N; gz++) {
                boolean bord = gx < 0 || gz < 0 || gx >= N || gz >= N;
                w.getBlockAt(bx + gx, y, bz + gz).setType(bord ? Material.SEA_LANTERN
                        : (gx + gz) % 2 == 0 ? Material.BLACK_CONCRETE : Material.GRAY_CONCRETE, false);
                for (int h = 1; h <= 3; h++) {
                    w.getBlockAt(bx + gx, y + h, bz + gz).setType(bord && h == 1 ? Material.LIGHT_BLUE_STAINED_GLASS
                            : bord ? Material.BARRIER : Material.AIR, false);
                }
            }
        }
    }

    private Location centre(int gx, int gz) {
        return new Location(w, bx + gx + 0.5, Y0, bz + gz + 0.5);
    }

    // ------------------------------------------------------------------ départ

    @Override
    protected void demarrer(List<Player> joueurs) {
        construire();
        int total = Math.max(joueurs.size() + 2, pl.getConfig().getInt("nebuleuse.comete.joueurs-max", 6));
        int[][] departs = new int[total][3];
        for (int i = 0; i < total; i++) {
            // en cercle, tournés dans le sens des aiguilles d'une montre
            double a = 2 * Math.PI * i / total;
            int gx = (int) Math.round(N / 2.0 + Math.cos(a) * N * 0.33), gz = (int) Math.round(N / 2.0 + Math.sin(a) * N * 0.33);
            double tx = -Math.sin(a), tz = Math.cos(a);
            int dir = Math.abs(tx) > Math.abs(tz) ? (tx > 0 ? 3 : 1) : (tz > 0 ? 0 : 2);
            departs[i] = new int[]{gx, gz, dir};
        }
        int i = 0;
        for (Player p : joueurs) {
            Comete c = new Comete(p, null, p.getName(), TRACES[i % TRACES.length], COULEURS[i % COULEURS.length]);
            placer(c, departs[i]);
            Location at = centre(c.x, c.z).add(0, 1 + pl.decalageSiege(), 0);
            at.setYaw(c.dir * 90f);
            teleporter(p, at.clone().add(0, 0.2, 0));
            c.siege = siege(at);
            asseoir(c.siege, p);
            boutonQuitter(p);
            i++;
        }
        for (int k = 0; i < total; i++, k++) {
            String nom = BOTS[(slot * 3 + k + 4) % BOTS.length];
            Color col = COULEURS[i % COULEURS.length];
            Comete c = new Comete(null, null, nom, TRACES[i % TRACES.length], col);
            placer(c, departs[i]);
            Location at = centre(c.x, c.z).add(0, 1, 0);
            at.setYaw(c.dir * 90f);
            c.bot = pantin(at, nom, col, mm -> {
                var a = mm.getAttribute(org.bukkit.attribute.Attribute.SCALE);
                if (a != null) {
                    a.setBaseValue(0.8);
                }
            });
        }
        nbOrbes = pl.getConfig().getInt("nebuleuse.comete.orbes", 45);
        for (int k = 0; k < nbOrbes; k++) {
            orbe();
        }
        for (Player p : joueurs) {
            Txt.send(p, mode.jeu().grad("<b>NÉBULEUSE · COMÈTE</b>") + " · tu files tout droit et laisses une traînée. "
                    + "<#E6EDF3>Q/D</#E6EDF3> tourner, <#E6EDF3>Espace</#E6EDF3> accélérer. Touche une traînée ou le bord et c'est fini.");
        }
    }

    private void placer(Comete c, int[] d) {
        c.x = d[0];
        c.z = d[1];
        c.dir = d[2];
        cometes.add(c);
        c.id = cometes.size();
        // traînée de départ derrière la tête
        for (int k = 2; k >= 0; k--) {
            int gx = c.x - DX[c.dir] * k, gz = c.z - DZ[c.dir] * k;
            if (libre(gx, gz)) {
                c.corps.addLast(new int[]{gx, gz});
                poser(c, gx, gz);
            }
        }
        c.longueur = 6;
    }

    private boolean dedans(int gx, int gz) {
        return gx >= 0 && gz >= 0 && gx < N && gz < N;
    }

    private boolean libre(int gx, int gz) {
        return dedans(gx, gz) && grille[gx][gz] == 0;
    }

    private void poser(Comete c, int gx, int gz) {
        grille[gx][gz] = c.id;
        w.getBlockAt(bx + gx, Y0, bz + gz).setType(c.trace, false);
    }

    private void effacer(int gx, int gz) {
        grille[gx][gz] = 0;
        w.getBlockAt(bx + gx, Y0, bz + gz).setType(Material.AIR, false);
    }

    private void orbe() {
        for (int essai = 0; essai < 20; essai++) {
            int gx = rnd.nextInt(N), gz = rnd.nextInt(N);
            if (grille[gx][gz] == 0 && orbes[gx][gz] == null) {
                orbes[gx][gz] = bloc(centre(gx, gz).add(0, 0.4, 0), Material.LIGHT_BLUE_STAINED_GLASS.createBlockData(), 0.35f, 0.35f, 0.35f,
                        new Quaternionf().rotateY((float) Math.PI / 4).rotateX((float) Math.PI / 4), 0);
                orbes[gx][gz].setGlowing(true);
                orbes[gx][gz].setGlowColorOverride(Color.fromRGB(0x62CFF4));
                return;
            }
        }
    }

    // ------------------------------------------------------------------ commandes

    @Override
    public void touche(Player p, Input in) {
        for (Comete c : cometes) {
            if (c.p != p || !c.vivant) {
                continue;
            }
            Input prev = dernier.put(p.getUniqueId(), in);
            if (in.isLeft() && (prev == null || !prev.isLeft())) {
                c.tourne = -1;
            }
            if (in.isRight() && (prev == null || !prev.isRight())) {
                c.tourne = 1;
            }
            c.boost = in.isJump() || in.isForward();
        }
    }

    private final java.util.Map<java.util.UUID, Input> dernier = new java.util.HashMap<>();

    // ------------------------------------------------------------------ tick

    @Override
    public void tick() {
        t++;
        for (Comete c : cometes) {
            if (c.p != null && c.vivant) {
                garderAssis(c.siege, c.p);
            }
        }
        if (depart > 0) {
            depart--;
            if (depart % 20 == 0) {
                int s = depart / 20;
                for (Player p : joueursEnLigne()) {
                    Txt.title(p, s > 0 ? "<#C79BFF><b>" + s + "</b>" : "<#7CFF8A><b>GO !</b>", s > 0 ? "<#9AA4B2>Q/D tourner · Espace accélérer" : "", 900);
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, s > 0 ? 1f : 2f);
                }
            }
            return;
        }
        reste--;
        // qui avance ce tick
        List<Comete> bougent = new ArrayList<>();
        for (Comete c : cometes) {
            if (!c.vivant) {
                continue;
            }
            boolean boost = c.boost && c.longueur > 5;
            c.avance += boost ? 0.5 : 1.0 / periode;
            if (boost && ++c.boostTicks % 12 == 0) {
                c.longueur--;
                retrecir(c);
            }
            if (c.avance >= 1) {
                c.avance -= 1;
                if (c.bot != null) {
                    ia(c);
                }
                if (c.tourne != 0) {
                    c.dir = Math.floorMod(c.dir + c.tourne, 4);
                    c.tourne = 0;
                    if (c.p != null) {
                        Location tete = centre(c.x, c.z);
                        c.p.lookAt(tete.getX() + DX[c.dir] * 10, Y0 + 1.2, tete.getZ() + DZ[c.dir] * 10, LookAnchor.EYES);
                        c.p.playSound(c.p.getLocation(), Sound.ENTITY_BREEZE_JUMP, 0.3f, 1.8f);
                    }
                }
                bougent.add(c);
            }
        }
        // collisions : bord, traînées, têtes sur la même case
        List<Comete> meurent = new ArrayList<>();
        for (Comete c : bougent) {
            int nx = c.x + DX[c.dir], nz = c.z + DZ[c.dir];
            if (!libre(nx, nz)) {
                meurent.add(c);
                continue;
            }
            for (Comete o : bougent) {
                if (o != c && o.x + DX[o.dir] == nx && o.z + DZ[o.dir] == nz) {
                    meurent.add(c);
                    break;
                }
            }
        }
        for (Comete c : bougent) {
            if (meurent.contains(c)) {
                continue;
            }
            c.x += DX[c.dir];
            c.z += DZ[c.dir];
            c.corps.addLast(new int[]{c.x, c.z});
            poser(c, c.x, c.z);
            if (orbes[c.x][c.z] != null) {
                oublier(orbes[c.x][c.z]);
                orbes[c.x][c.z] = null;
                c.longueur += 3;
                c.pic = Math.max(c.pic, c.longueur);
                if (c.p != null) {
                    c.p.playSound(c.p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f, 1.6f);
                }
                orbe();
            }
            retrecir(c);
            Location l = centre(c.x, c.z).add(0, 1, 0);
            l.setYaw(c.dir * 90f);
            if (c.p != null) {
                c.siege.teleport(l.add(0, pl.decalageSiege(), 0), TeleportFlag.EntityState.RETAIN_PASSENGERS);
            } else {
                c.bot.teleport(l);
            }
        }
        for (Comete c : meurent) {
            mort(c);
        }
        if (t % 4 == 0) {
            hud();
        }
        long vivants = cometes.stream().filter(c -> c.vivant).count();
        long humainsVivants = cometes.stream().filter(c -> c.vivant && c.p != null).count();
        if (reste <= 0 || humainsVivants == 0 || vivants <= 1) {
            fin();
        }
    }

    private void retrecir(Comete c) {
        while (c.corps.size() > c.longueur) {
            int[] q = c.corps.pollFirst();
            if (q != null && grille[q[0]][q[1]] == c.id) {
                effacer(q[0], q[1]);
            }
        }
    }

    private void mort(Comete c) {
        c.vivant = false;
        c.mortA = ++morts;
        Location l = centre(c.x, c.z).add(0, 1, 0);
        w.spawnParticle(Particle.EXPLOSION, l, 2, 0.3, 0.3, 0.3, 0);
        sonTous(Sound.ENTITY_GENERIC_EXPLODE, 0.4f, 1.4f);
        // la traînée se disperse en orbes
        int k = 0;
        for (int[] q : c.corps) {
            if (grille[q[0]][q[1]] == c.id) {
                effacer(q[0], q[1]);
                if (k++ % 3 == 0 && orbes[q[0]][q[1]] == null) {
                    orbes[q[0]][q[1]] = bloc(centre(q[0], q[1]).add(0, 0.4, 0), Material.YELLOW_STAINED_GLASS.createBlockData(), 0.35f, 0.35f, 0.35f,
                            new Quaternionf().rotateY((float) Math.PI / 4).rotateX((float) Math.PI / 4), 0);
                }
            }
        }
        c.corps.clear();
        for (Comete o : cometes) {
            if (o.p != null && o.vivant && o != c) {
                Txt.bar(o.p, "<#E0484A>" + c.nom + "</#E0484A> <#9AA4B2>s'est écrasé.");
            }
        }
        if (c.bot != null) {
            oublier(c.bot);
        }
        if (c.p != null) {
            Txt.title(c.p, "<#E0484A><b>Écrasé !</b>", "<#9AA4B2>Longueur max " + c.pic + " · tu regardes la fin de la partie", 2000);
            if (c.siege != null) {
                c.siege.eject();
                oublier(c.siege);
                c.siege = null;
            }
            c.p.setGameMode(GameMode.SPECTATOR);
            teleporter(c.p, new Location(w, bx + N / 2.0, Y0 + 22, bz + N / 2.0, 0f, 90f));
        }
    }

    /** Bot : garde le cap s'il reste de la place, sinon tourne vers le plus d'espace libre ; attiré par les orbes. */
    private void ia(Comete c) {
        int best = c.dir;
        double bestScore = -1e9;
        for (int turn = -1; turn <= 1; turn++) {
            int d = Math.floorMod(c.dir + turn, 4);
            int nx = c.x + DX[d], nz = c.z + DZ[d];
            if (!libre(nx, nz)) {
                continue;
            }
            double s = espace(nx, nz, 70) * 1.0;
            // orbe la plus proche dans cette direction
            double near = 99;
            for (int r = 1; r <= 10; r++) {
                int ax = c.x + DX[d] * r, az = c.z + DZ[d] * r;
                if (!dedans(ax, az) || grille[ax][az] != 0) {
                    break;
                }
                if (orbes[ax][az] != null) {
                    near = r;
                    break;
                }
            }
            s += near < 99 ? 12 - near : 0;
            s += turn == 0 ? 3 : 0;
            s += rnd.nextDouble(0, 2.5);
            if (s > bestScore) {
                bestScore = s;
                best = d;
            }
        }
        if (best != c.dir) {
            c.tourne = Math.floorMod(best - c.dir, 4) == 1 ? 1 : -1;
        }
        c.boost = false;
    }

    /** Cases libres atteignables depuis (gx, gz), plafonné. */
    private int espace(int gx, int gz, int max) {
        boolean[][] vu = new boolean[N][N];
        ArrayDeque<int[]> q = new ArrayDeque<>();
        q.add(new int[]{gx, gz});
        vu[gx][gz] = true;
        int n = 0;
        while (!q.isEmpty() && n < max) {
            int[] c = q.poll();
            n++;
            for (int d = 0; d < 4; d++) {
                int ax = c[0] + DX[d], az = c[1] + DZ[d];
                if (libre(ax, az) && !vu[ax][az]) {
                    vu[ax][az] = true;
                    q.add(new int[]{ax, az});
                }
            }
        }
        return n;
    }

    private void hud() {
        long vivants = cometes.stream().filter(c -> c.vivant).count();
        for (Comete c : cometes) {
            if (c.p == null || c.parti) {
                continue;
            }
            Txt.bar(c.p, (c.vivant ? "<#C79BFF>☄ " + c.longueur + "</#C79BFF>" : "<#E0484A>éliminé</#E0484A>")
                    + " <#5A6270>·</#5A6270> <#E6EDF3>" + vivants + " en lice</#E6EDF3> <#5A6270>·</#5A6270> <#9AA4B2>" + Txt.chrono(reste)
                    + (c.vivant && c.boost && c.longueur > 5 ? "</#9AA4B2>  <#62CFF4><b>BOOST</b>" : ""));
        }
    }

    private void fin() {
        // classement : survivants d'abord (plus longs devant), puis ordre inverse des éliminations
        List<Comete> l = new ArrayList<>(cometes);
        l.sort(Comparator.comparingInt((Comete c) -> c.vivant ? 0 : 1)
                .thenComparingInt(c -> c.vivant ? -c.longueur : -c.mortA));
        for (int i = 0; i < l.size(); i++) {
            Comete c = l.get(i);
            if (c.p == null || c.parti || !c.p.isOnline()) {
                continue;
            }
            int rang = i + 1;
            int oc = c.pic + (rang == 1 ? 50 : rang <= 3 ? 20 : 0);
            resultat(c.p, c.pic, oc, rang == 1 ? "<#E0B864><b>1er !</b>" : "<#C79BFF><b>" + rang + "e</b>",
                    "Longueur max " + c.pic + (c.vivant ? " · survivant" : ""));
        }
        finir();
    }

    @Override
    protected void retirer(Player p) {
        for (Comete c : cometes) {
            if (c.p == p) {
                if (c.vivant) {
                    c.vivant = false;
                    c.mortA = ++morts;
                    for (int[] q : c.corps) {
                        if (grille[q[0]][q[1]] == c.id) {
                            effacer(q[0], q[1]);
                        }
                    }
                    c.corps.clear();
                }
                if (c.siege != null) {
                    oublier(c.siege);
                    c.siege = null;
                }
            }
        }
        for (Comete c : cometes) {
            if (c.p == p) {
                c.parti = true;
            }
        }
    }

    @Override
    protected void nettoyer() {
        for (int gx = 0; gx < N; gx++) {
            for (int gz = 0; gz < N; gz++) {
                if (grille[gx][gz] != 0) {
                    effacer(gx, gz);
                }
            }
        }
        cometes.clear();
    }
}
