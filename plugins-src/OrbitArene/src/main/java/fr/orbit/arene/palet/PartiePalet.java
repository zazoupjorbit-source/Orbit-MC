package fr.orbit.arene.palet;

import fr.orbit.arene.Jeu;
import fr.orbit.arene.OrbitArene;
import fr.orbit.arene.Partie;
import fr.orbit.arene.Txt;
import io.papermc.paper.entity.TeleportFlag;
import org.bukkit.Color;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Palet Orbital (mécanique « air hockey ») : table de 9 × 17 blocs, chacun assis au bout de la table pilote son
 * poussoir au clavier (Z/Q/S/D) dans sa moitié. Le palet est simulé par le serveur (rebonds, frottement, chocs
 * avec les poussoirs) ; le premier à N buts gagne. Contre un bot ou contre un autre joueur.
 */
public final class PartiePalet extends Partie {

    static final double HX = 4.5, HZ = 8.5, RP = 0.4, RM = 0.6, BUT = 1.6;
    static final double VMAX_PALET = 1.15, E_MUR = 0.9, E_CHOC = 0.92;

    final class Cote {
        final int sens;              // -1 = côté nord (z négatif), +1 = côté sud
        final Player p;
        Mannequin bot;
        final String nom;
        final Color couleur;
        final String hex;
        double mx, mz, vx, vz;
        Input in;
        int buts;
        boolean parti;
        ItemDisplay siege;
        List<BlockDisplay> maillet;

        Cote(int sens, Player p, String nom, Color couleur) {
            this.sens = sens;
            this.p = p;
            this.nom = nom;
            this.couleur = couleur;
            this.hex = hex(couleur);
            this.mz = sens * (HZ - 1.6);
        }
    }

    private final SplittableRandom rnd = new SplittableRandom();
    private final Cote[] cotes = new Cote[2];
    private double px, pz, pvx, pvz;
    private List<BlockDisplay> palet;
    private TextDisplay tableau;
    private final double tcx, tcz;
    private final int x1, z1;
    private int pause = 80, butsGagne, duree = 20 * 60 * 6;
    private boolean fini;

    public PartiePalet(OrbitArene pl, Jeu.Mode mode, int slot) {
        super(pl, mode, slot);
        x1 = ox + 8;
        z1 = oz + 8;
        tcx = x1 + HX;
        tcz = z1 + HZ;
        butsGagne = Math.max(1, pl.getConfig().getInt("palet.buts-pour-gagner", 7));
    }

    @Override
    protected int[] taille() {
        return new int[]{26, 34};
    }

    @Override
    public boolean assis(Player p) {
        for (Cote c : cotes) {
            if (c != null && c.p == p && !c.parti) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ table

    private void construire() {
        for (int x = x1 - 3; x <= x1 + 11; x++) {
            for (int z = z1 - 6; z <= z1 + 22; z++) {
                for (int y = Y0 - 1; y <= Y0 + 2; y++) {
                    w.getBlockAt(x, y, z).setType(Material.AIR, false);
                }
                w.getBlockAt(x, Y0 - 2, z).setType(Material.POLISHED_BLACKSTONE, false);
            }
        }
        int mid = z1 + 8;
        for (int x = x1 - 1; x <= x1 + 9; x++) {
            for (int z = z1 - 1; z <= z1 + 17; z++) {
                boolean bordX = x == x1 - 1 || x == x1 + 9, bordZ = z == z1 - 1 || z == z1 + 17;
                if (bordX || bordZ) {
                    boolean but = bordZ && !bordX && x >= x1 + 3 && x <= x1 + 5;
                    w.getBlockAt(x, Y0 - 1, z).setType(Material.SEA_LANTERN, false);
                    w.getBlockAt(x, Y0, z).setType(but ? Material.AIR : z == z1 - 1 ? Material.BLUE_STAINED_GLASS
                            : z == z1 + 17 ? Material.YELLOW_STAINED_GLASS : Material.LIGHT_BLUE_STAINED_GLASS, false);
                    if (but) {
                        w.getBlockAt(x, Y0 - 1, z).setType(Material.BLACK_CONCRETE, false);
                    }
                } else {
                    Material m = z == mid ? Material.RED_CONCRETE
                            : (Math.abs(z - mid) == 5 ? Material.LIGHT_BLUE_CONCRETE
                            : (z <= z1 + 1 || z >= z1 + 15) && x >= x1 + 2 && x <= x1 + 6 ? Material.LIGHT_GRAY_CONCRETE : Material.WHITE_CONCRETE);
                    w.getBlockAt(x, Y0 - 1, z).setType(m, false);
                }
            }
        }
        // estrades des deux joueurs
        for (int s = -1; s <= 1; s += 2) {
            int zz = s < 0 ? z1 - 3 : z1 + 19;
            for (int x = x1 + 2; x <= x1 + 6; x++) {
                for (int dz = -1; dz <= 1; dz++) {
                    w.getBlockAt(x, Y0 - 1, zz + dz).setType(s < 0 ? Material.BLUE_CONCRETE : Material.YELLOW_CONCRETE, false);
                }
            }
        }
    }

    private Location monde(double x, double y, double z) {
        return new Location(w, tcx + x, y, tcz + z);
    }

    // ------------------------------------------------------------------ départ

    @Override
    protected void demarrer(List<Player> joueurs) {
        construire();
        Color bleu = Color.fromRGB(0x3A7FFF), or = Color.fromRGB(0xE0B864);
        cotes[0] = new Cote(-1, joueurs.get(0), joueurs.get(0).getName(), bleu);
        cotes[1] = joueurs.size() > 1 ? new Cote(1, joueurs.get(1), joueurs.get(1).getName(), or)
                : new Cote(1, null, BOTS[(slot + 6) % BOTS.length], or);
        for (Cote c : cotes) {
            Location seat = monde(0, Y0 + 0.2, c.sens * (HZ + 2.4));
            seat.setYaw(c.sens < 0 ? 0f : 180f);
            seat.setPitch(35f);
            c.siege = siege(seat);
            if (c.p != null) {
                teleporter(c.p, seat.clone().add(0, 0.3, 0));
                asseoir(c.siege, c.p);
                boutonQuitter(c.p);
            } else {
                c.bot = pantin(seat, c.nom, c.couleur, null);
                asseoir(c.siege, c.bot);
            }
            c.maillet = new ArrayList<>();
            Location m = monde(c.mx, Y0 + 0.15, c.mz);
            c.maillet.add(bloc(m, (c.sens < 0 ? Material.BLUE_CONCRETE : Material.YELLOW_CONCRETE).createBlockData(), 1.0f, 0.3f, 1.0f, new Quaternionf(), 1));
            c.maillet.add(bloc(m, (c.sens < 0 ? Material.BLUE_CONCRETE : Material.YELLOW_CONCRETE).createBlockData(), 1.0f, 0.3f, 1.0f,
                    new Quaternionf().rotateY((float) Math.PI / 4), 1));
            c.maillet.add(bloc(m.clone().add(0, 0.25, 0), (c.sens < 0 ? Material.LIGHT_BLUE_CONCRETE : Material.GOLD_BLOCK).createBlockData(),
                    0.4f, 0.4f, 0.4f, new Quaternionf(), 1));
        }
        Location pp = monde(0, Y0 + 0.08, 0);
        palet = new ArrayList<>();
        palet.add(bloc(pp, Material.BLACK_CONCRETE.createBlockData(), 0.7f, 0.15f, 0.7f, new Quaternionf(), 1));
        palet.add(bloc(pp, Material.BLACK_CONCRETE.createBlockData(), 0.7f, 0.15f, 0.7f, new Quaternionf().rotateY((float) Math.PI / 4), 1));
        palet.add(bloc(pp.clone().add(0, 0.02, 0), Material.CYAN_CONCRETE.createBlockData(), 0.3f, 0.15f, 0.3f, new Quaternionf(), 1));
        tableau = garder(w.spawn(monde(0, Y0 + 4.2, 0), TextDisplay.class, e -> {
            e.setBillboard(Display.Billboard.CENTER);
            e.setAlignment(TextDisplay.TextAlignment.CENTER);
            e.setBackgroundColor(Color.fromARGB(150, 10, 12, 18));
            e.setBrightness(new Display.Brightness(15, 15));
            e.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(1.6f, 1.6f, 1.6f), new Quaternionf()));
        }));
        engager(-1);
        afficher();
        for (Player p : joueurs) {
            Txt.send(p, mode.jeu().grad("<b>PALET ORBITAL</b>") + " · <#E6EDF3>Z/Q/S/D</#E6EDF3> pour déplacer ton poussoir dans ta moitié. "
                    + "Premier à <b>" + butsGagne + "</b> buts.");
        }
    }

    /** Palet posé arrêté dans la moitié de {@code sens} (celui qui vient d'encaisser, ou le bleu au départ). */
    private void engager(int sens) {
        px = rnd.nextDouble(-1.5, 1.5);
        pz = sens * 3.0;
        pvx = 0;
        pvz = 0;
        for (Cote c : cotes) {
            c.mx = 0;
            c.mz = c.sens * (HZ - 1.6);
            c.vx = 0;
            c.vz = 0;
        }
    }

    // ------------------------------------------------------------------ commandes

    @Override
    public void touche(Player p, Input in) {
        for (Cote c : cotes) {
            if (c != null && c.p == p) {
                c.in = in;
            }
        }
    }

    // ------------------------------------------------------------------ tick

    @Override
    public void tick() {
        t++;
        for (Cote c : cotes) {
            if (c.p != null && !c.parti) {
                garderAssis(c.siege, c.p);
            }
        }
        if (fini) {
            return;
        }
        if (--duree <= 0) {
            terminer();
            return;
        }
        if (pause > 0) {
            pause--;
            if (pause % 20 == 0 && pause > 0) {
                for (Player p : joueursEnLigne()) {
                    Txt.title(p, "<#62CFF4><b>" + pause / 20 + "</b>", "<#9AA4B2>Z/Q/S/D : ton poussoir", 900);
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.2f);
                }
            }
            dessiner();
            return;
        }
        double vJoueur = 0.42, vBot = pl.getConfig().getDouble("palet.vitesse-bot", 0.30);
        for (Cote c : cotes) {
            double dx = 0, dz = 0;
            if (c.p != null) {
                if (c.in != null && !c.parti) {
                    // face à la table : le côté nord regarde vers +z (sa gauche = +x), le côté sud vers -z
                    double f = (c.in.isForward() ? 1 : 0) - (c.in.isBackward() ? 1 : 0);
                    double l = (c.in.isLeft() ? 1 : 0) - (c.in.isRight() ? 1 : 0);
                    dz = -c.sens * f;
                    dx = -c.sens * l;
                    double n = Math.hypot(dx, dz);
                    if (n > 0) {
                        dx = dx / n * vJoueur;
                        dz = dz / n * vJoueur;
                    }
                }
            } else {
                double[] v = ia(c, vBot);
                dx = v[0];
                dz = v[1];
            }
            c.vx += (dx - c.vx) * 0.5;
            c.vz += (dz - c.vz) * 0.5;
            c.mx += c.vx;
            c.mz += c.vz;
            double zmin = c.sens < 0 ? -HZ + RM : RM, zmax = c.sens < 0 ? -RM : HZ - RM;
            if (c.mx < -HX + RM || c.mx > HX - RM) {
                c.mx = Math.max(-HX + RM, Math.min(HX - RM, c.mx));
                c.vx = 0;
            }
            if (c.mz < zmin || c.mz > zmax) {
                c.mz = Math.max(zmin, Math.min(zmax, c.mz));
                c.vz = 0;
            }
        }
        // palet : sous-pas pour ne pas traverser les poussoirs
        for (int s = 0; s < 4; s++) {
            px += pvx / 4;
            pz += pvz / 4;
            if (px < -HX + RP) {
                px = -HX + RP;
                pvx = Math.abs(pvx) * E_MUR;
                choc(0.4f);
            } else if (px > HX - RP) {
                px = HX - RP;
                pvx = -Math.abs(pvx) * E_MUR;
                choc(0.4f);
            }
            boolean dansBut = Math.abs(px) < BUT - RP * 0.5;
            if (pz < -HZ + RP && !dansBut) {
                pz = -HZ + RP;
                pvz = Math.abs(pvz) * E_MUR;
                choc(0.4f);
            } else if (pz > HZ - RP && !dansBut) {
                pz = HZ - RP;
                pvz = -Math.abs(pvz) * E_MUR;
                choc(0.4f);
            }
            if (pz < -HZ - RP) {
                but(cotes[1], cotes[0]);
                return;
            }
            if (pz > HZ + RP) {
                but(cotes[0], cotes[1]);
                return;
            }
            for (Cote c : cotes) {
                double dx = px - c.mx, dz = pz - c.mz, d = Math.hypot(dx, dz);
                if (d < RP + RM && d > 1e-6) {
                    double nx = dx / d, nz = dz / d;
                    px = c.mx + nx * (RP + RM);
                    pz = c.mz + nz * (RP + RM);
                    double rvx = pvx - c.vx, rvz = pvz - c.vz;
                    double vn = rvx * nx + rvz * nz;
                    if (vn < 0) {
                        pvx -= (1 + E_CHOC) * vn * nx;
                        pvz -= (1 + E_CHOC) * vn * nz;
                        choc(Math.min(1f, (float) Math.abs(vn) + 0.3f));
                    }
                }
            }
        }
        double v = Math.hypot(pvx, pvz);
        if (v > VMAX_PALET) {
            pvx = pvx / v * VMAX_PALET;
            pvz = pvz / v * VMAX_PALET;
        }
        pvx *= 0.996;
        pvz *= 0.996;
        dessiner();
        if (t % 5 == 0) {
            for (Cote c : cotes) {
                if (c.p != null && !c.parti) {
                    Cote o = c == cotes[0] ? cotes[1] : cotes[0];
                    Txt.bar(c.p, "<" + c.hex + "><b>" + c.buts + "</b> " + c.nom + "</" + c.hex + "> <#5A6270>—</#5A6270> <"
                            + o.hex + ">" + o.nom + " <b>" + o.buts + "</b></" + o.hex + "> <#5A6270>· premier à " + butsGagne);
                }
            }
        }
    }

    /** Bot : défend devant son but, attaque le palet quand il est dans sa moitié en passant derrière lui. */
    private double[] ia(Cote c, double vmax) {
        double tx, tz;
        boolean chezMoi = pz * c.sens > 0;
        double distBut = Math.abs(c.sens * HZ - pz);
        if (chezMoi && (pvz * c.sens <= 0.25 || distBut < 4)) {
            boolean derriere = (c.mz - pz) * c.sens > 0.2;
            if (derriere) {
                tx = px + (px - 0) * 0.15;
                tz = pz - c.sens * 0.3;
            } else {
                tx = px + (px >= c.mx ? -0.9 : 0.9);
                tz = pz + c.sens * 1.3;
            }
        } else {
            tx = Math.max(-BUT, Math.min(BUT, px * 0.55));
            tz = c.sens * (HZ - 1.4);
        }
        double dx = tx - c.mx, dz = tz - c.mz, d = Math.hypot(dx, dz);
        if (d < 0.05) {
            return new double[]{0, 0};
        }
        double k = Math.min(vmax, d) / d;
        return new double[]{dx * k, dz * k};
    }

    private void choc(float force) {
        if (t % 2 == 0) {
            w.playSound(monde(px, Y0, pz), Sound.BLOCK_STONE_HIT, 0.6f * force + 0.2f, 1.6f);
        }
    }

    private void dessiner() {
        Location pp = monde(px, Y0 + 0.08, pz);
        for (int i = 0; i < palet.size(); i++) {
            palet.get(i).teleport(i == 2 ? pp.clone().add(0, 0.02, 0) : pp);
        }
        for (Cote c : cotes) {
            Location m = monde(c.mx, Y0 + 0.15, c.mz);
            for (int i = 0; i < c.maillet.size(); i++) {
                c.maillet.get(i).teleport(i == 2 ? m.clone().add(0, 0.25, 0) : m);
            }
        }
    }

    private void but(Cote marque, Cote encaisse) {
        marque.buts++;
        Location l = monde(px, Y0 + 0.5, pz);
        w.spawnParticle(Particle.FIREWORK, l, 30, 0.6, 0.4, 0.6, 0.08);
        sonTous(Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.9f, 1.1f);
        for (Player p : joueursEnLigne()) {
            Txt.title(p, "<" + marque.hex + "><b>BUT !</b>", "<" + marque.hex + ">" + marque.nom + "</" + marque.hex + "> <#E6EDF3>"
                    + cotes[0].buts + " — " + cotes[1].buts, 1500);
        }
        afficher();
        if (marque.buts >= butsGagne) {
            terminer();
            return;
        }
        engager(encaisse.sens);
        pause = 50;
        dessiner();
    }

    private void afficher() {
        if (tableau != null && tableau.isValid()) {
            tableau.text(Txt.mm(mode.jeu().grad("<b>PALET ORBITAL</b>") + "\n<" + cotes[0].hex + "><b>" + cotes[0].nom + " " + cotes[0].buts
                    + "</b></" + cotes[0].hex + "> <#5A6270>—</#5A6270> <" + cotes[1].hex + "><b>" + cotes[1].buts + " " + cotes[1].nom + "</b>"));
        }
    }

    private void terminer() {
        if (fini) {
            return;
        }
        fini = true;
        for (Cote c : cotes) {
            if (c.p == null || c.parti || !c.p.isOnline()) {
                continue;
            }
            Cote o = c == cotes[0] ? cotes[1] : cotes[0];
            boolean gagne = c.buts > o.buts;
            int score = c.buts * 100 + (gagne ? 300 : 0);
            int oc = c.buts * 15 + (gagne ? 60 : 0);
            resultat(c.p, score, oc, gagne ? "<#7CFF8A><b>Victoire !</b>" : c.buts == o.buts ? "<#E6EDF3><b>Égalité</b>" : "<#E0484A><b>Défaite</b>",
                    c.buts + " — " + o.buts + " contre " + o.nom);
        }
        finir();
    }

    @Override
    protected void retirer(Player p) {
        for (Cote c : cotes) {
            if (c != null && c.p == p) {
                c.parti = true;
                if (c.siege != null) {
                    c.siege.eject();
                }
            }
        }
        // l'autre joueur gagne par forfait
        for (Cote c : cotes) {
            if (c != null && c.p != null && c.p != p && !c.parti && !fini) {
                Cote o = c == cotes[0] ? cotes[1] : cotes[0];
                c.buts = Math.max(c.buts, o.buts + 1);
                Txt.send(c.p, "<#9AA4B2>" + p.getName() + " a quitté la table : victoire par forfait.");
                pl.getServer().getScheduler().runTask(pl, this::terminer);
            }
        }
    }

    @Override
    protected void nettoyer() {
        fini = true;
    }
}
