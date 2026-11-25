package fr.orbit.arene.charge;

import fr.orbit.arene.Etat;
import fr.orbit.arene.Jeu;
import fr.orbit.arene.OrbitArene;
import fr.orbit.arene.Partie;
import fr.orbit.arene.Txt;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.function.BiPredicate;

/**
 * Charge Nova (mécanique « poseur de bombes ») : labyrinthe de piliers et de caisses, 4 combattants (humains +
 * bots). On pose des charges (Maj ou clic droit) qui explosent en croix au bout de 2,5 s ; elles cassent les
 * caisses (qui lâchent parfois un bonus : portée, charge en plus, vitesse) et éliminent qui est dans le souffle.
 * Une charge devient solide dès que plus personne n'est sur sa case. Dernier debout = vainqueur.
 */
public final class PartieCharge extends Partie {

    static final int W = 15, H = 13, C = 2;                // cases (impair), taille d'une case en blocs
    static final int MUR = 1, CAISSE = 2;
    static final int[] DI = {1, -1, 0, 0}, DJ = {0, 0, 1, -1};
    static final int INF = Integer.MAX_VALUE;

    enum Bonus {
        PORTEE("+1 portée", "#FF8A3D", Material.BLAZE_POWDER),
        CHARGE("+1 charge", "#E0B864", Material.FIRE_CHARGE),
        VITESSE("+ vitesse", "#62CFF4", Material.SUGAR);

        final String label, color;
        final Material item;

        Bonus(String label, String color, Material item) {
            this.label = label;
            this.color = color;
            this.item = item;
        }
    }

    final class Acteur {
        final Player p;
        Mannequin bot;
        final String nom;
        final Color couleur;
        double x, z;
        boolean vivant = true, parti;
        int max = 1, portee = 2, vitesse, actives, kills, caisses, mortA;
        int[] suivante;      // bot : case visée
        int attente;         // bot : ticks d'attente

        Acteur(Player p, String nom, Color couleur) {
            this.p = p;
            this.nom = nom;
            this.couleur = couleur;
        }

        int ci() {
            return Math.floorDiv((int) Math.floor(x - bx), C);
        }

        int cj() {
            return Math.floorDiv((int) Math.floor(z - bz), C);
        }

        double vitesseMarche() {
            return 0.1 + 0.018 * vitesse;
        }
    }

    final class Charge {
        final int i, j, portee;
        final Acteur poseur;
        int meche;
        boolean solide;
        final BlockDisplay d;

        Charge(int i, int j, Acteur poseur, int meche, BlockDisplay d) {
            this.i = i;
            this.j = j;
            this.poseur = poseur;
            this.portee = poseur.portee;
            this.meche = meche;
            this.d = d;
        }
    }

    static final class Flamme {
        final int i, j;
        final Object source;
        int ticks = 14;
        final BlockDisplay d;

        Flamme(int i, int j, Object source, BlockDisplay d) {
            this.i = i;
            this.j = j;
            this.source = source;
            this.d = d;
        }
    }

    private final SplittableRandom rnd = new SplittableRandom();
    private final int[][] cases = new int[W][H];
    private final Charge[][] charges = new Charge[W][H];
    private final Bonus[][] bonus = new Bonus[W][H];
    private final ItemDisplay[][] bonusD = new ItemDisplay[W][H];
    private final List<Flamme> flammes = new ArrayList<>();
    private final List<Acteur> acteurs = new ArrayList<>();
    private final Map<UUID, Input> dernier = new HashMap<>();
    private final int bx, bz, mecheDefaut;
    private int depart = 80, reste, morts;

    public PartieCharge(OrbitArene pl, Jeu.Mode mode, int slot) {
        super(pl, mode, slot);
        bx = ox + 4;
        bz = oz + 4;
        mecheDefaut = pl.getConfig().getInt("charge.meche", 50);
        reste = pl.getConfig().getInt("charge.duree", 180) * 20;
    }

    @Override
    protected int[] taille() {
        return new int[]{W * C + 8, H * C + 8};
    }

    private Location centre(int i, int j) {
        return new Location(w, bx + i * C + C / 2.0, Y0, bz + j * C + C / 2.0);
    }

    // ------------------------------------------------------------------ arène

    private void construire() {
        double taux = pl.getConfig().getDouble("charge.caisses", 0.72);
        int[][] libres = {{1, 1}, {2, 1}, {1, 2}, {W - 2, 1}, {W - 3, 1}, {W - 2, 2}, {1, H - 2}, {2, H - 2}, {1, H - 3},
                {W - 2, H - 2}, {W - 3, H - 2}, {W - 2, H - 3}};
        for (int i = 0; i < W; i++) {
            for (int j = 0; j < H; j++) {
                boolean bord = i == 0 || j == 0 || i == W - 1 || j == H - 1;
                boolean pilier = i % 2 == 0 && j % 2 == 0;
                int c = bord || pilier ? MUR : rnd.nextDouble() < taux ? CAISSE : 0;
                for (int[] l : libres) {
                    if (l[0] == i && l[1] == j) {
                        c = 0;
                    }
                }
                cases[i][j] = c;
                dessiner(i, j, bord);
            }
        }
    }

    private void dessiner(int i, int j, boolean bord) {
        int c = cases[i][j];
        for (int a = 0; a < C; a++) {
            for (int b = 0; b < C; b++) {
                int x = bx + i * C + a, z = bz + j * C + b;
                w.getBlockAt(x, Y0 - 1, z).setType(c == MUR ? Material.POLISHED_BLACKSTONE
                        : (i + j) % 2 == 0 ? Material.GRAY_CONCRETE : Material.LIGHT_GRAY_CONCRETE, false);
                w.getBlockAt(x, Y0, z).setType(c == MUR ? (bord && (a + b) % 2 == 0 ? Material.SEA_LANTERN : Material.POLISHED_BLACKSTONE_BRICKS)
                        : c == CAISSE ? ((a + b) % 2 == 0 ? Material.ORANGE_TERRACOTTA : Material.CYAN_TERRACOTTA) : Material.AIR, false);
                for (int h = 1; h <= 3; h++) {
                    w.getBlockAt(x, Y0 + h, z).setType(c == MUR || c == CAISSE ? Material.BARRIER : Material.AIR, false);
                }
            }
        }
    }

    // ------------------------------------------------------------------ départ

    @Override
    protected void demarrer(List<Player> joueurs) {
        construire();
        int[][] coins = {{1, 1}, {W - 2, H - 2}, {W - 2, 1}, {1, H - 2}};
        int n = 0;
        for (Player p : joueurs) {
            Acteur a = new Acteur(p, p.getName(), COULEURS[n % COULEURS.length]);
            Location at = centre(coins[n][0], coins[n][1]);
            at.setYaw(yawVersCentre(at));
            a.x = at.getX();
            a.z = at.getZ();
            acteurs.add(a);
            teleporter(p, at);
            Etat.base(p, Attribute.JUMP_STRENGTH, 0.0);
            Etat.base(p, Attribute.MOVEMENT_SPEED, 0.0);
            p.getInventory().setItem(0, objet(p, Material.FIRE_CHARGE, "charge", "<#FF8A3D><b>Charge Nova</b>", "Clic droit ou Maj : poser une charge"));
            p.getInventory().setHeldItemSlot(0);
            boutonQuitter(p);
            n++;
        }
        for (int k = 0; n < 4; n++, k++) {
            String nom = BOTS[(slot * 4 + k + 8) % BOTS.length];
            Acteur a = new Acteur(null, nom, COULEURS[n % COULEURS.length]);
            Location at = centre(coins[n][0], coins[n][1]);
            at.setYaw(yawVersCentre(at));
            a.x = at.getX();
            a.z = at.getZ();
            a.bot = pantin(at, nom, a.couleur, null);
            acteurs.add(a);
        }
        for (Player p : joueurs) {
            Txt.send(p, mode.jeu().grad("<b>CHARGE NOVA</b>") + " · <#E6EDF3>Maj</#E6EDF3> ou <#E6EDF3>clic droit</#E6EDF3> : poser une charge "
                    + "(explosion en croix). Casse les caisses, ramasse les bonus, sois le dernier debout.");
        }
    }

    private float yawVersCentre(Location at) {
        double dx = bx + W * C / 2.0 - at.getX(), dz = bz + H * C / 2.0 - at.getZ();
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    // ------------------------------------------------------------------ commandes

    @Override
    public void touche(Player p, Input in) {
        Input prev = dernier.put(p.getUniqueId(), in);
        if (in.isSneak() && (prev == null || !prev.isSneak())) {
            clic(p);
        }
    }

    @Override
    public void clic(Player p) {
        for (Acteur a : acteurs) {
            if (a.p == p && a.vivant && depart == 0) {
                poser(a);
            }
        }
    }

    private boolean poser(Acteur a) {
        int i = a.ci(), j = a.cj();
        if (i < 0 || j < 0 || i >= W || j >= H || cases[i][j] != 0 || charges[i][j] != null || a.actives >= a.max) {
            if (a.p != null && a.actives >= a.max) {
                a.p.playSound(a.p.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 0.6f, 1.2f);
            }
            return false;
        }
        Location at = centre(i, j).add(0, 0.6, 0);
        BlockDisplay d = bloc(at, Material.COAL_BLOCK.createBlockData(), 1.1f, 1.1f, 1.1f,
                new Quaternionf().rotateY((float) Math.PI / 4), 0);
        d.setGlowing(true);
        d.setGlowColorOverride(a.couleur);
        charges[i][j] = new Charge(i, j, a, mecheDefaut, d);
        a.actives++;
        w.playSound(at, Sound.ENTITY_TNT_PRIMED, 0.8f, 1.3f);
        return true;
    }

    // ------------------------------------------------------------------ tick

    @Override
    public void tick() {
        t++;
        if (depart > 0) {
            depart--;
            if (depart % 20 == 0) {
                int s = depart / 20;
                for (Player p : joueursEnLigne()) {
                    Txt.title(p, s > 0 ? "<#FF8A3D><b>" + s + "</b>" : "<#7CFF8A><b>GO !</b>", s > 0 ? "<#9AA4B2>Maj ou clic droit : poser une charge" : "", 900);
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, s > 0 ? 1f : 2f);
                }
                if (s == 0) {
                    for (Acteur a : acteurs) {
                        if (a.p != null) {
                            Etat.base(a.p, Attribute.MOVEMENT_SPEED, a.vitesseMarche());
                        }
                    }
                }
            }
            return;
        }
        reste--;
        for (Acteur a : acteurs) {
            if (!a.vivant) {
                continue;
            }
            if (a.p != null) {
                Location l = a.p.getLocation();
                a.x = l.getX();
                a.z = l.getZ();
                if (l.getY() < Y0 - 2) {
                    teleporter(a.p, centre(1, 1));
                }
            } else {
                ia(a);
            }
            ramasser(a);
        }
        // charges : solidité, pulsation, mèche
        for (int i = 0; i < W; i++) {
            for (int j = 0; j < H; j++) {
                Charge c = charges[i][j];
                if (c == null) {
                    continue;
                }
                if (!c.solide && personneSur(i, j)) {
                    c.solide = true;
                    for (int a = 0; a < C; a++) {
                        for (int b = 0; b < C; b++) {
                            w.getBlockAt(bx + i * C + a, Y0, bz + j * C + b).setType(Material.BARRIER, false);
                        }
                    }
                }
                if (c.meche % 10 == 0) {
                    float s = c.meche % 20 == 0 ? 1.25f : 1.05f;
                    c.d.setInterpolationDelay(0);
                    c.d.setInterpolationDuration(8);
                    c.d.setTransformation(centre(s, s, s, new Quaternionf().rotateY((float) Math.PI / 4)));
                }
                if (--c.meche <= 0) {
                    exploser(c);
                }
            }
        }
        // flammes
        for (int k = flammes.size() - 1; k >= 0; k--) {
            Flamme f = flammes.get(k);
            for (Acteur a : acteurs) {
                if (a.vivant && a.ci() == f.i && a.cj() == f.j) {
                    eliminer(a, (Acteur) f.source);
                }
            }
            if (--f.ticks <= 0) {
                oublier(f.d);
                flammes.remove(k);
            }
        }
        if (t % 4 == 0) {
            hud();
        }
        long vivants = acteurs.stream().filter(a -> a.vivant).count();
        long humains = acteurs.stream().filter(a -> a.vivant && a.p != null).count();
        if (vivants <= 1 || humains == 0 || reste <= 0) {
            fin();
        }
    }

    /** Vrai si personne n'occupe la case (la charge peut devenir solide). */
    private boolean personneSur(int i, int j) {
        double minX = bx + i * C - 0.35, maxX = bx + (i + 1) * C + 0.35, minZ = bz + j * C - 0.35, maxZ = bz + (j + 1) * C + 0.35;
        for (Acteur a : acteurs) {
            if (a.vivant && a.x > minX && a.x < maxX && a.z > minZ && a.z < maxZ) {
                return false;
            }
        }
        return true;
    }

    private void exploser(Charge c) {
        charges[c.i][c.j] = null;
        c.poseur.actives = Math.max(0, c.poseur.actives - 1);
        oublier(c.d);
        if (c.solide) {
            for (int a = 0; a < C; a++) {
                for (int b = 0; b < C; b++) {
                    w.getBlockAt(bx + c.i * C + a, Y0, bz + c.j * C + b).setType(Material.AIR, false);
                }
            }
        }
        Location at = centre(c.i, c.j).add(0, 0.6, 0);
        w.spawnParticle(Particle.EXPLOSION, at, 1);
        w.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 1.1f);
        flamme(c.i, c.j, c.poseur);
        for (int d = 0; d < 4; d++) {
            for (int r = 1; r <= c.portee; r++) {
                int i = c.i + DI[d] * r, j = c.j + DJ[d] * r;
                if (i < 0 || j < 0 || i >= W || j >= H || cases[i][j] == MUR) {
                    break;
                }
                if (cases[i][j] == CAISSE) {
                    casser(i, j, c.poseur);
                    flamme(i, j, c.poseur);
                    break;
                }
                flamme(i, j, c.poseur);
                if (bonus[i][j] != null) {
                    retirerBonus(i, j);
                    break;
                }
                Charge autre = charges[i][j];
                if (autre != null) {
                    autre.meche = Math.min(autre.meche, 2);
                    break;
                }
            }
        }
    }

    private void flamme(int i, int j, Acteur source) {
        BlockDisplay d = bloc(centre(i, j).add(0, 0.5, 0), Material.SHROOMLIGHT.createBlockData(), 1.7f, 0.9f, 1.7f, new Quaternionf(), 0);
        d.setGlowing(true);
        d.setGlowColorOverride(Color.fromRGB(0xFF8A3D));
        flammes.add(new Flamme(i, j, source, d));
        w.spawnParticle(Particle.FLAME, centre(i, j).add(0, 0.6, 0), 6, 0.5, 0.3, 0.5, 0.02);
    }

    private void casser(int i, int j, Acteur source) {
        cases[i][j] = 0;
        dessiner(i, j, false);
        source.caisses++;
        if (rnd.nextDouble() < pl.getConfig().getDouble("charge.bonus", 0.32)) {
            Bonus[] all = Bonus.values();
            Bonus b = all[rnd.nextInt(all.length)];
            bonus[i][j] = b;
            Location at = centre(i, j).add(0, 0.7, 0);
            // apparaît après les flammes (sinon brûlé aussitôt)
            pl.getServer().getScheduler().runTaskLater(pl, () -> {
                if (finie() || bonus[i][j] != b || bonusD[i][j] != null) {
                    return;
                }
                bonusD[i][j] = garder(w.spawn(at, ItemDisplay.class, e -> {
                    e.setItemStack(new ItemStack(b.item));
                    e.setBillboard(Display.Billboard.VERTICAL);
                    e.setBrightness(new Display.Brightness(15, 15));
                    e.setGlowing(true);
                    e.setGlowColorOverride(Color.fromRGB(Integer.parseInt(b.color.substring(1), 16)));
                    e.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.9f, 0.9f, 0.9f), new Quaternionf()));
                }));
            }, 16L);
        }
    }

    private void retirerBonus(int i, int j) {
        bonus[i][j] = null;
        if (bonusD[i][j] != null) {
            oublier(bonusD[i][j]);
            bonusD[i][j] = null;
        }
    }

    private void ramasser(Acteur a) {
        int i = a.ci(), j = a.cj();
        if (i < 0 || j < 0 || i >= W || j >= H || bonus[i][j] == null || bonusD[i][j] == null) {
            return;
        }
        Bonus b = bonus[i][j];
        retirerBonus(i, j);
        switch (b) {
            case PORTEE -> a.portee = Math.min(8, a.portee + 1);
            case CHARGE -> a.max = Math.min(6, a.max + 1);
            case VITESSE -> {
                a.vitesse = Math.min(5, a.vitesse + 1);
                if (a.p != null) {
                    Etat.base(a.p, Attribute.MOVEMENT_SPEED, a.vitesseMarche());
                }
            }
        }
        if (a.p != null) {
            a.p.playSound(a.p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.4f);
            Txt.bar(a.p, "<" + b.color + "><b>" + b.label + "</b>");
        }
    }

    private void eliminer(Acteur a, Acteur par) {
        if (!a.vivant) {
            return;
        }
        a.vivant = false;
        a.mortA = ++morts;
        if (par != null && par != a) {
            par.kills++;
        }
        Location at = new Location(w, a.x, Y0 + 1, a.z);
        w.spawnParticle(Particle.EXPLOSION, at, 2, 0.3, 0.3, 0.3, 0);
        String qui = par == null || par == a ? "<#9AA4B2>s'est fait sauter" : "<#9AA4B2>soufflé par " + par.nom;
        for (Acteur o : acteurs) {
            if (o.p != null && !o.parti && o != a) {
                Txt.send(o.p, "<#E0484A>" + a.nom + "</#E0484A> " + qui);
            }
        }
        if (a.bot != null) {
            oublier(a.bot);
            a.bot = null;
        }
        if (a.p != null && !a.parti) {
            Txt.title(a.p, "<#E0484A><b>Soufflé !</b>", par == null || par == a ? "<#9AA4B2>par ta propre charge" : "<#9AA4B2>par " + par.nom, 2000);
            a.p.setGameMode(GameMode.SPECTATOR);
            teleporter(a.p, new Location(w, bx + W * C / 2.0, Y0 + 18, bz + H * C / 2.0, 0f, 90f));
        }
    }

    // ------------------------------------------------------------------ bots

    /** Ticks avant qu'une flamme touche chaque case (INF = sûre). */
    private int[][] danger(Charge extra) {
        int[][] dg = new int[W][H];
        for (int[] row : dg) {
            Arrays.fill(row, INF);
        }
        for (Flamme f : flammes) {
            dg[f.i][f.j] = 0;
        }
        List<Charge> all = new ArrayList<>();
        for (Charge[] row : charges) {
            for (Charge c : row) {
                if (c != null) {
                    all.add(c);
                }
            }
        }
        if (extra != null) {
            all.add(extra);
        }
        for (Charge c : all) {
            int m = c.meche;
            dg[c.i][c.j] = Math.min(dg[c.i][c.j], m);
            for (int d = 0; d < 4; d++) {
                for (int r = 1; r <= c.portee; r++) {
                    int i = c.i + DI[d] * r, j = c.j + DJ[d] * r;
                    if (i < 0 || j < 0 || i >= W || j >= H || cases[i][j] == MUR) {
                        break;
                    }
                    dg[i][j] = Math.min(dg[i][j], m);
                    if (cases[i][j] == CAISSE) {
                        break;
                    }
                }
            }
        }
        return dg;
    }

    private boolean passable(int i, int j, Acteur a) {
        return i >= 0 && j >= 0 && i < W && j < H && cases[i][j] == 0 && (charges[i][j] == null || (i == a.ci() && j == a.cj()));
    }

    /**
     * Parcours en largeur depuis la case du bot : première case du chemin vers la plus proche case qui vérifie
     * {@code but}, ou null. {@code prudent} : n'emprunte aucune case menacée ; sinon seulement celles qui ne
     * brûleront pas au moment du passage.
     */
    private int[] chemin(Acteur a, int[][] dg, BiPredicate<Integer, Integer> but, int maxPas, boolean prudent) {
        int si = a.ci(), sj = a.cj();
        if (si < 0 || sj < 0 || si >= W || sj >= H) {
            return null;
        }
        int[][] pere = new int[W * H][];
        boolean[][] vu = new boolean[W][H];
        int[][] dist = new int[W][H];
        ArrayDeque<int[]> q = new ArrayDeque<>();
        q.add(new int[]{si, sj});
        vu[si][sj] = true;
        int pasTicks = (int) Math.ceil(C / (a.vitesseMarche() * 2.16 * 0.9)) + 1;
        while (!q.isEmpty()) {
            int[] c = q.poll();
            if (!(c[0] == si && c[1] == sj) && but.test(c[0], c[1])) {
                int[] cur = c;
                while (pere[cur[0] * H + cur[1]] != null) {
                    int[] up = pere[cur[0] * H + cur[1]];
                    if (up[0] == si && up[1] == sj) {
                        break;
                    }
                    cur = up;
                }
                return cur;
            }
            if (dist[c[0]][c[1]] >= maxPas) {
                continue;
            }
            for (int d = 0; d < 4; d++) {
                int i = c[0] + DI[d], j = c[1] + DJ[d];
                if (!passable(i, j, a) || vu[i][j]) {
                    continue;
                }
                int arrivee = (dist[c[0]][c[1]] + 1) * pasTicks;
                if (dg[i][j] != INF && (prudent || (dg[i][j] <= arrivee + 6 && dg[i][j] + 16 >= arrivee))) {
                    continue;
                }
                vu[i][j] = true;
                dist[i][j] = dist[c[0]][c[1]] + 1;
                pere[i * H + j] = c;
                q.add(new int[]{i, j});
            }
        }
        return null;
    }

    private void ia(Acteur a) {
        // en route vers la case suivante
        if (a.suivante != null) {
            Location cible = centre(a.suivante[0], a.suivante[1]);
            double dx = cible.getX() - a.x, dz = cible.getZ() - a.z, d = Math.hypot(dx, dz);
            double v = a.vitesseMarche() * 2.16 * 0.9;
            if (d <= v) {
                a.x = cible.getX();
                a.z = cible.getZ();
                a.suivante = null;
            } else {
                a.x += dx / d * v;
                a.z += dz / d * v;
            }
            a.bot.teleport(new Location(w, a.x, Y0, a.z, (float) Math.toDegrees(Math.atan2(-dx, dz)), 0f));
            if (a.suivante != null) {
                return;
            }
        }
        if (a.attente > 0) {
            a.attente--;
            return;
        }
        int[][] dg = danger(null);
        int i = a.ci(), j = a.cj();
        if (i < 0 || j < 0 || i >= W || j >= H) {
            return;
        }
        int[] pas;
        if (dg[i][j] != INF) {
            // fuir vers la case sûre la plus proche
            pas = chemin(a, dg, (x, y) -> dg[x][y] == INF, 12, false);
        } else {
            boolean cible = voisinCaisse(i, j);
            for (Acteur o : acteurs) {
                if (o != a && o.vivant && !cible) {
                    int oi = o.ci(), oj = o.cj();
                    if (((oi == i && Math.abs(oj - j) <= a.portee) || (oj == j && Math.abs(oi - i) <= a.portee)) && ligneLibre(i, j, oi, oj)) {
                        cible = rnd.nextInt(3) > 0;
                    }
                }
            }
            if (cible && a.actives < a.max && charges[i][j] == null) {
                Charge essai = new Charge(i, j, a, mecheDefaut, null);
                int[][] dg2 = danger(essai);
                int[] fuite = chemin(a, dg2, (x, y) -> dg2[x][y] == INF, 6, false);
                if (fuite != null && poser(a)) {
                    a.suivante = fuite;
                    return;
                }
            }
            // bonus, puis caisses, puis adversaires ; sans passer par une case menacée
            pas = chemin(a, dg, (x, y) -> bonus[x][y] != null && bonusD[x][y] != null, 14, true);
            if (pas == null) {
                pas = chemin(a, dg, (x, y) -> voisinCaisse(x, y), 20, true);
            }
            if (pas == null) {
                pas = chemin(a, dg, (x, y) -> procheAdversaire(a, x, y), 30, true);
            }
        }
        if (pas != null) {
            a.suivante = pas;
        } else {
            a.attente = 6;
        }
    }

    private boolean ligneLibre(int i, int j, int oi, int oj) {
        int di = Integer.signum(oi - i), dj = Integer.signum(oj - j);
        int x = i, y = j;
        while (x != oi || y != oj) {
            x += di;
            y += dj;
            if (x < 0 || y < 0 || x >= W || y >= H || cases[x][y] != 0) {
                return false;
            }
        }
        return true;
    }

    private boolean voisinCaisse(int i, int j) {
        for (int d = 0; d < 4; d++) {
            int ni = i + DI[d], nj = j + DJ[d];
            if (ni >= 0 && nj >= 0 && ni < W && nj < H && cases[ni][nj] == CAISSE) {
                return true;
            }
        }
        return false;
    }

    private boolean procheAdversaire(Acteur a, int i, int j) {
        for (Acteur o : acteurs) {
            if (o != a && o.vivant && Math.abs(o.ci() - i) + Math.abs(o.cj() - j) <= 1) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ affichage et fin

    private void hud() {
        long vivants = acteurs.stream().filter(a -> a.vivant).count();
        for (Acteur a : acteurs) {
            if (a.p == null || a.parti) {
                continue;
            }
            Txt.bar(a.p, (a.vivant ? "<#E0B864>✸ " + (a.max - a.actives) + "/" + a.max + "</#E0B864>  <#FF8A3D>🔥 " + a.portee
                    + "</#FF8A3D>  <#62CFF4>⚡ " + a.vitesse + "</#62CFF4>" : "<#E0484A>éliminé</#E0484A>")
                    + " <#5A6270>·</#5A6270> <#E6EDF3>" + vivants + " debout</#E6EDF3> <#5A6270>·</#5A6270> <#9AA4B2>" + Txt.chrono(reste));
        }
    }

    private void fin() {
        List<Acteur> l = new ArrayList<>(acteurs);
        l.sort(Comparator.comparingInt((Acteur a) -> a.vivant ? 0 : 1).thenComparingInt(a -> a.vivant ? -a.kills : -a.mortA));
        boolean seul = acteurs.stream().filter(a -> a.vivant).count() == 1;
        for (int k = 0; k < l.size(); k++) {
            Acteur a = l.get(k);
            if (a.p == null || a.parti || !a.p.isOnline()) {
                continue;
            }
            int rang = k + 1;
            boolean gagne = rang == 1 && a.vivant && seul;
            int score = a.kills * 100 + a.caisses * 10 + (gagne ? 300 : a.vivant ? 120 : rang == 2 ? 60 : 0);
            int oc = score / 8;
            resultat(a.p, score, oc, gagne ? "<#E0B864><b>Victoire !</b>" : a.vivant ? "<#62CFF4><b>Temps écoulé</b>" : "<#FF8A3D><b>" + rang + "e</b>",
                    a.kills + " élimination(s) · " + a.caisses + " caisse(s)");
        }
        finir();
    }

    @Override
    protected void retirer(Player p) {
        for (Acteur a : acteurs) {
            if (a.p == p) {
                a.parti = true;
                if (a.vivant) {
                    a.vivant = false;
                    a.mortA = ++morts;
                }
            }
        }
    }

    @Override
    protected void nettoyer() {
        for (int i = 0; i < W; i++) {
            for (int j = 0; j < H; j++) {
                if (charges[i][j] != null && charges[i][j].solide) {
                    for (int a = 0; a < C; a++) {
                        for (int b = 0; b < C; b++) {
                            w.getBlockAt(bx + i * C + a, Y0, bz + j * C + b).setType(Material.AIR, false);
                        }
                    }
                }
                charges[i][j] = null;
            }
        }
        flammes.clear();
        acteurs.clear();
    }
}
