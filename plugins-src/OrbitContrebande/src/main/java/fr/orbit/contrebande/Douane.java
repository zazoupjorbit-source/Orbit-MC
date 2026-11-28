package fr.orbit.contrebande;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Wolf;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * La douane au sol : des agents à pied qui font leur ronde, et des voitures de patrouille qui lâchent deux agents.
 * <p>
 * Rien n'est immédiat, tout se lit avant d'arriver :
 * <ol>
 * <li><b>Soupçon</b> : l'agent qui aperçoit un joueur chargé s'arrête et le fixe ; une jauge monte (1 à 2 s) tant qu'il le
 * voit, et redescend dès qu'il le perd. Sortir de sa vue suffit.</li>
 * <li><b>Sommation</b> : jauge pleine, il crie « Halte ! » et laisse une bonne seconde d'avance.</li>
 * <li><b>Poursuite</b> : il sprinte un peu moins vite qu'un joueur, puis s'essouffle au bout de quelques secondes.</li>
 * <li><b>Combat</b> : arrivé au contact, il arme un coup de matraque (un temps d'élan, qu'on esquive en s'écartant). Un coup
 * reçu coûte une résistance sur trois et repousse le joueur ; à zéro, c'est la saisie. Le joueur peut frapper l'agent :
 * il est sonné, et K.-O. au troisième coup, mais chaque coup fait monter l'avis de recherche.</li>
 * </ol>
 * Un agent, c'est une entité invisible qui porte le déplacement (elle trouve son chemin dans les rues) et un mannequin en
 * uniforme recalé dessus à chaque tick (skin du pack ; sans le pack, un mannequin en tenue bleue).
 * Mêmes règles que les drones : il faut porter de la marchandise, être vu sans mur entre les deux, et la planque est sûre.
 */
final class Douane {

    /**
     * Vitesses en blocs par seconde. Un joueur marche à 4,3 et sprinte à 5,6 : en ligne droite il ne distance un agent frais
     * que de justesse, et un agent essoufflé rattrape encore quelqu'un qui marche. Il faut des virages, le skate ou un coup.
     */
    static final double V_RONDE = 2.2, V_SPRINT = 5.5, V_FOND = 4.8, V_VOITURE = 0.21;
    static final double RAYON_AGENT = 7.0, RAYON_VOITURE = 8.0, DEMI_CONE = 65;
    /** Durées en ticks : sprint avant l'essoufflement, sommation, élan de la matraque, récupération après le coup, chancelle. */
    static final int SOUFFLE = 220, SOMMATION = 22, ELAN = 8, RECUP = 14, SONNE = 16;
    /**
     * Un douanier ne tombe jamais. Un coup le fait chanceler et lui laisse une blessure : {@code RALENTI} de vitesse en moins
     * par blessure, {@code BLESSURES} au plus, chacune guérie {@code SOIN} ticks après le dernier coup. En se reprenant il
     * reste {@code GARDE} ticks sur ses gardes et pare tout : impossible de l'enchaîner.
     */
    static final int BLESSURES = 3, SOIN = 200, GARDE = 50;
    static final double RALENTI = 0.09;
    /** Délai entre deux coups d'un même joueur. */
    static final int CADENCE = 12, CADENCE_BATTE = 14;
    /** Poursuite : distance et temps sans le voir au-delà desquels il abandonne, durée maximale, portée de la matraque. */
    static final double SEME = 28, PORTEE = 2.5;
    static final int PERDU = 120, LASSE = 1100;
    private static final String EQUIPE = "ctb_douane";
    private static final int BLEU = 0x62CFF4, ROUGE = 0xE0484A, GRIS = 0x5A6270, JAUNE = 0xFFE680;

    private enum Mode { RONDE, SOUPCON, SOMMATION, POURSUITE, ELAN, RECUP, SONNE, RETOUR }

    private final Nuit n;
    private final List<Agent> agents = new ArrayList<>();
    private final List<Voiture> voitures = new ArrayList<>();
    private final boolean inverse;

    Douane(Nuit n) {
        this.n = n;
        this.inverse = n.plugin.getConfig().getBoolean("douane.voiture-inversee", false);
    }

    /** Vitesse d'attribut qui donne tant de blocs par seconde à une créature qui marche (la vitesse compte au carré). */
    static double attribut(double blocsParSeconde) {
        return Math.sqrt(blocsParSeconde / 44.05);
    }

    void agent(double[][] route) {
        int i = (int) (Math.random() * route.length);
        Agent a = new Agent(route, null, route[i][0], route[i][1], null);
        a.wp = (i + 1) % route.length;
        agents.add(a);
    }

    void voiture(double[][] route) {
        voitures.add(new Voiture(route));
    }

    int agents() {
        return agents.size();
    }

    void tick() {
        for (Voiture v : voitures) {
            v.tick();
        }
        for (Iterator<Agent> it = agents.iterator(); it.hasNext(); ) {
            Agent a = it.next();
            a.tick();
            if (a.fini) {
                a.retirer();
                it.remove();
            }
        }
    }

    /** Un joueur quitte la nuit : ceux qui le suivaient abandonnent. */
    void lacher(Player p) {
        relacher(p, 20);
    }

    private void relacher(Player p, int pause) {
        for (Agent a : agents) {
            if (p.equals(a.cible)) {
                a.lacher(pause);
            }
        }
    }

    /** Vrai tant qu'un agent a sommé ou poursuit ce joueur (pour la barre de résistance). */
    boolean poursuit(Player p) {
        for (Agent a : agents) {
            if (p.equals(a.cible) && a.mode != Mode.RONDE && a.mode != Mode.SOUPCON && a.mode != Mode.RETOUR) {
                return true;
            }
        }
        return false;
    }

    /** Un joueur frappe une entité de la nuit : si c'est un agent, il encaisse. */
    void coup(Player p, Entity e) {
        Nuit.Joueur j = n.joueurs.get(p.getUniqueId());
        if (j == null) {
            return;
        }
        for (Agent a : agents) {
            if (e.equals(a.corps) || e.equals(a.simple) || e.equals(a.hd)) {
                if (n.tick >= j.coupPret && !a.fini) {
                    // un coup blesse et ralentit, la batte davantage ; la batte à impulsion touche aussi les agents voisins
                    int arme = Produit.niveauBatte(p.getInventory().getItemInMainHand());
                    j.coupPret = n.tick + (arme > 0 ? CADENCE_BATTE : CADENCE);
                    Location centre = a.corps.getLocation();
                    a.encaisse(p, arme);
                    if (arme >= 2) {
                        for (Agent b : agents) {
                            if (b != a && !b.fini && b.corps.getLocation().distanceSquared(centre) <= 9) {
                                b.encaisse(p, arme);
                            }
                        }
                        n.world.playSound(centre, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.8f, 1.4f);
                        for (int i = 0; i < 24; i++) {
                            double an = Math.PI * 2 * i / 24;
                            n.world.spawnParticle(Particle.DUST, centre.getX() + Math.cos(an) * 3, centre.getY() + 0.3, centre.getZ() + Math.sin(an) * 3,
                                    1, 0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(BLEU), 1.4f));
                        }
                    }
                }
                return;
            }
        }
    }

    void retirer() {
        for (Agent a : agents) {
            a.retirer();
        }
        agents.clear();
        for (Voiture v : voitures) {
            v.retirer();
        }
        voitures.clear();
    }

    // ------------------------------------------------------------------ sans collision : personne n'est bousculé

    private Team equipe(Scoreboard sb) {
        Team t = sb.getTeam(EQUIPE);
        if (t == null) {
            t = sb.registerNewTeam(EQUIPE);
            t.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
            t.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        }
        return t;
    }

    private void inscrire(Entity e) {
        String id = e.getUniqueId().toString();
        try {
            Scoreboard principal = Bukkit.getScoreboardManager().getMainScoreboard();
            equipe(principal).addEntry(id);
            for (Nuit.Joueur j : n.joueurs.values()) {
                if (j.p.getScoreboard() != principal) {
                    equipe(j.p.getScoreboard()).addEntry(id);
                }
            }
        } catch (RuntimeException ex) {
            // un tableau d'un autre plugin qui refuse : on garde la collision, sans gravité
        }
    }

    /** Un joueur arrive : son tableau (s'il en a un à lui) doit aussi connaître l'équipe sans collision. */
    void inscrire(Player p) {
        try {
            Scoreboard sb = p.getScoreboard();
            if (sb == Bukkit.getScoreboardManager().getMainScoreboard()) {
                return;
            }
            Team t = equipe(sb);
            for (Agent a : agents) {
                for (Entity e : new Entity[]{a.corps, a.simple, a.hd}) {
                    t.addEntry(e.getUniqueId().toString());
                }
            }
        } catch (RuntimeException ex) {
            // voir plus haut
        }
    }

    private void desinscrire(Entity e) {
        String id = e.getUniqueId().toString();
        try {
            Scoreboard principal = Bukkit.getScoreboardManager().getMainScoreboard();
            Team t = principal.getTeam(EQUIPE);
            if (t != null) {
                t.removeEntry(id);
            }
            for (Nuit.Joueur j : n.joueurs.values()) {
                Team o = j.p.getScoreboard().getTeam(EQUIPE);
                if (o != null && j.p.getScoreboard() != principal) {
                    o.removeEntry(id);
                }
            }
        } catch (RuntimeException ex) {
            // rien à nettoyer
        }
    }

    // ------------------------------------------------------------------ repérage

    private double facteur() {
        int b = n.orga.niveau("brouilleur");
        return b >= 2 ? 0.65 : b == 1 ? 0.8 : 1.0;
    }

    private boolean brouille() {
        return n.tick < n.brouillage;
    }

    private boolean voit(double x, double y, double z, Player p) {
        Location from = n.loc(x, y, z);
        Vector dir = p.getEyeLocation().toVector().subtract(from.toVector());
        double len = dir.length();
        if (len <= 0.4) {
            return true;
        }
        RayTraceResult hit = n.world.rayTraceBlocks(from, dir.normalize(), len - 0.3, FluidCollisionMode.NEVER, true);
        return hit == null || hit.getHitBlock() == null;
    }

    /**
     * Le joueur chargé de marchandise le plus proche que l'on voit depuis (x, y, z), à moins de {@code rayon}.
     * {@code lacet} : direction du regard (NaN = on voit tout autour) ; à moins de 1,8 bloc on est remarqué même de dos.
     */
    private Player repere(double x, double y, double z, double rayon, float lacet) {
        Player best = null;
        double bd = Double.MAX_VALUE;
        for (Nuit.Joueur j : n.joueurs.values()) {
            Location pl = j.p.getLocation();
            double prx = pl.getX() - n.ox, prz = pl.getZ();
            double d = Math.hypot(prx - x, prz - z);
            if (d > rayon || d >= bd || Math.abs(pl.getY() - y) > 3.5 || Ville.safe(prx, prz) || !n.marchandise(j.p)) {
                continue;
            }
            if (!Float.isNaN(lacet) && d > 1.8) {
                double a = Math.toRadians(lacet), fx = -Math.sin(a), fz = Math.cos(a);
                if (((prx - x) * fx + (prz - z) * fz) / d < Math.cos(Math.toRadians(DEMI_CONE))) {
                    continue;
                }
            }
            if (!voit(x, y + 1.6, z, j.p)) {
                continue;
            }
            best = j.p;
            bd = d;
        }
        return best;
    }

    private void alerte(Player p, String sousTitre) {
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 0.6f);
        p.playSound(p.getLocation(), Sound.BLOCK_BELL_USE, 0.8f, 1.6f);
        Txt.title(p, "", sousTitre, 1500);
        n.orga.chaleur = Math.min(100, n.orga.chaleur + 3);
        n.alertes++;
    }

    private void poussiere(double x, double y, double z, int rgb, float taille) {
        n.world.spawnParticle(Particle.DUST, n.ox + x, y, z, 1, 0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(rgb), taille));
    }

    private static void attr(LivingEntity e, Attribute a, double v) {
        AttributeInstance i = e.getAttribute(a);
        if (i != null) {
            i.setBaseValue(v);
        }
    }

    private static ItemStack cuir(Material m, int rgb) {
        ItemStack it = new ItemStack(m);
        if (it.getItemMeta() instanceof LeatherArmorMeta meta) {
            meta.setColor(Color.fromRGB(rgb));
            it.setItemMeta(meta);
        }
        return it;
    }

    /** Jauge en petits carrés : {@code pleins} de la couleur donnée, le reste en gris. */
    private static String carres(int pleins, int total, int rgb) {
        StringBuilder b = new StringBuilder("<#" + Nuit.hex(rgb) + ">");
        for (int i = 0; i < total; i++) {
            if (i == pleins) {
                b.append("<#").append(Nuit.hex(GRIS)).append(">");
            }
            b.append('■');
        }
        return b.toString();
    }

    private static float lacet(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    // ------------------------------------------------------------------ agent à pied

    final class Agent {
        final double[][] route;
        final Voiture voiture;
        final Wolf corps;
        final Mannequin simple, hd;
        final TextDisplay etiquette;
        int wp;
        Mode mode = Mode.RONDE;
        Player cible;
        boolean fini;
        /** Sonné : ce qu'il fera en se reprenant. Rancune : il a été frappé, il poursuit même un joueur sans marchandise. */
        private boolean reprendre, rancune, enChasse, essouffle;
        private int depuis, vu, repos, controle, etat;
        /** Blessures en cours, tick du dernier coup reçu, tick jusqu'auquel il pare. */
        private int blessures, soin, garde;
        private double cx, cz, jauge;
        private int dureeSonne = SONNE;

        Agent(double[][] route, Voiture voiture, double x, double z, Player cible) {
            this.route = route;
            this.voiture = voiture;
            Location l = n.loc(x, Ville.Y + 1, z);
            corps = n.world.spawn(l, Wolf.class, e -> {
                n.tag(e);
                e.setInvisible(true);
                e.setSilent(true);
                e.setInvulnerable(true);
                e.setCollidable(false);
                e.setRemoveWhenFarAway(false);
                e.setCanPickupItems(false);
                e.setAdult();
                Bukkit.getMobGoals().removeAllGoals(e);
                attr(e, Attribute.MOVEMENT_SPEED, attribut(V_SPRINT));
                attr(e, Attribute.FOLLOW_RANGE, 56);
            }, CreatureSpawnEvent.SpawnReason.CUSTOM);
            simple = habit(l, false);
            hd = habit(l, true);
            n.paire(simple, hd);
            etiquette = n.world.spawn(l, TextDisplay.class, e -> {
                n.tag(e);
                e.text(Txt.mm("<#" + Nuit.hex(BLEU) + "><b>DOUANE</b>"));
                e.setBillboard(Display.Billboard.CENTER);
                e.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                e.setShadowed(true);
                e.setBrightness(new Display.Brightness(15, 15));
                e.setTransformation(new Transformation(new Vector3f(0f, 1.45f, 0f), new AxisAngle4f(), new Vector3f(1f, 1f, 1f), new AxisAngle4f()));
            });
            corps.addPassenger(etiquette);
            for (Entity e : new Entity[]{corps, simple, hd}) {
                inscrire(e);
            }
            cx = x;
            cz = z;
            controle = n.tick;
            if (cible != null) {
                // équipage d'une voiture : il descend et somme le joueur avant de courir
                this.cible = cible;
                passer(Mode.SOMMATION);
                afficher("<#" + Nuit.hex(ROUGE) + "><b>HALTE ! DOUANE !</b>");
            }
        }

        /** Le corps visible : skin de douanier du pack, ou mannequin en tenue bleue pour ceux qui n'ont pas le pack. */
        private Mannequin habit(Location l, boolean pack) {
            return n.world.spawn(l, Mannequin.class, b -> {
                n.tag(b);
                if (pack) {
                    b.setProfile(ResolvableProfile.resolvableProfile()
                            .skinPatch(sp -> sp.body(Key.key("contrebande", "entity/douane/agent"))).build());
                } else {
                    EntityEquipment eq = b.getEquipment();
                    if (eq != null) {
                        eq.setHelmet(cuir(Material.LEATHER_HELMET, 0x1B2A4A));
                        eq.setChestplate(cuir(Material.LEATHER_CHESTPLATE, 0x24407A));
                        eq.setLeggings(cuir(Material.LEATHER_LEGGINGS, 0x1B2A4A));
                        eq.setBoots(cuir(Material.LEATHER_BOOTS, 0x0B0D16));
                    }
                }
                EntityEquipment eq = b.getEquipment();
                if (eq != null) {
                    eq.setItemInMainHand(new ItemStack(Material.STICK));   // la matraque
                }
                b.setImmovable(true);
                b.setGravity(false);
                b.setSilent(true);
                b.setInvulnerable(true);
                b.setCollidable(false);
                b.setCanPickupItems(false);
                b.setCustomNameVisible(false);
                try {
                    b.setDescription(null);
                } catch (RuntimeException ex) {
                    b.setDescription(Component.empty());
                }
            }, CreatureSpawnEvent.SpawnReason.CUSTOM);
        }

        private void afficher(String mm) {
            etiquette.text(Txt.mm(mm));
        }

        /** L'étiquette de poursuite : ses blessures et son état se lisent dessus. */
        private void afficherChasse(String suite) {
            String etat = suite;
            if (etat.isEmpty()) {
                etat = n.tick < garde ? "en garde" : blessures > 0 ? "blessé" : essouffle ? "essoufflé" : "";
            }
            afficher("<#" + Nuit.hex(ROUGE) + "><b>DOUANE</b>" + (blessures > 0 ? " " + carres(blessures, BLESSURES, JAUNE) : "")
                    + (etat.isEmpty() ? "" : " <#" + Nuit.hex(ROUGE) + ">" + etat));
        }

        private void passer(Mode m) {
            mode = m;
            etat = n.tick;
        }

        private void arreter() {
            corps.getPathfinder().stopPathfinding();
        }

        private void aller(double x, double y, double z, double vitesse) {
            corps.getPathfinder().moveTo(n.loc(x, y, z), vitesse);
        }

        /** Multiplicateur de déplacement : le fond quand il est essoufflé, moins {@code RALENTI} par blessure. */
        private double allure() {
            double v = (essouffle ? V_FOND : V_SPRINT) * (1 - RALENTI * blessures);
            return Math.sqrt(v / V_SPRINT);
        }

        private void son(Sound s, float volume, float ton) {
            n.world.playSound(corps.getLocation(), s, volume, ton);
        }

        /** Il abandonne : retour à la ronde (ou à la voiture), aveugle pendant {@code pause} ticks. */
        void lacher(int pause) {
            cible = null;
            repos = pause;
            jauge = 0;
            rancune = false;
            enChasse = false;
            essouffle = false;
            reprendre = false;
            afficher("<#" + Nuit.hex(BLEU) + "><b>DOUANE</b>");
            if (voiture != null) {
                passer(Mode.RETOUR);
                depuis = n.tick;
                return;
            }
            passer(Mode.RONDE);
            Location l = corps.getLocation();
            double best = Double.MAX_VALUE;
            for (int i = 0; i < route.length; i++) {
                double d = Math.hypot(route[i][0] - (l.getX() - n.ox), route[i][1] - l.getZ());
                if (d < best) {
                    best = d;
                    wp = i;
                }
            }
            controle = n.tick;
            cx = l.getX() - n.ox;
            cz = l.getZ();
        }

        private void chasser() {
            passer(Mode.POURSUITE);
            vu = n.tick;
            if (!enChasse) {
                enChasse = true;
                essouffle = false;
                depuis = n.tick;
            }
            afficherChasse("");
        }

        /**
         * Un joueur le frappe. Un douanier ne tombe jamais : hors garde, il chancelle un instant et garde une blessure qui le
         * ralentit ; quand il vient de se reprendre, il pare. L'avis de recherche monte à chaque coup.
         */
        void encaisse(Player p, int arme) {
            Location l = corps.getLocation();
            if (cible == null) {
                // frappé pendant sa ronde : c'est à celui-là qu'il en veut
                cible = p;
                rancune = true;
            } else if (p.equals(cible)) {
                rancune = true;
            }
            if (mode != Mode.SONNE && n.tick < garde) {
                n.orga.chaleur = Math.min(100, n.orga.chaleur + 1);
                son(Sound.ITEM_SHIELD_BLOCK, 0.9f, 1.2f);
                n.world.spawnParticle(Particle.ENCHANTED_HIT, l.clone().add(0, 1.2, 0), 8, 0.2, 0.3, 0.2, 0.1);
                Txt.bar(p, "<#FFE680>Paré !</#FFE680> <#9AA4B2>Il est sur ses gardes : esquive sa matraque ou file.");
                if (mode == Mode.RONDE || mode == Mode.SOUPCON) {
                    chasser();
                }
                return;
            }
            n.orga.chaleur = Math.min(100, n.orga.chaleur + 3);
            blessures = Math.min(BLESSURES, blessures + (arme > 0 ? 2 : 1));
            soin = n.tick;
            son(arme > 0 ? Sound.BLOCK_WOOD_HIT : Sound.ENTITY_PLAYER_ATTACK_STRONG, 1f, arme > 0 ? 0.7f : 1f);
            Vector v = l.toVector().subtract(p.getLocation().toVector()).setY(0);
            if (v.lengthSquared() < 0.01) {
                v = p.getLocation().getDirection().setY(0);
            }
            if (v.lengthSquared() < 0.01) {
                v = new Vector(0, 0, 1);
            }
            corps.setVelocity(v.normalize().multiply(arme > 0 ? 0.8 : 0.5).setY(arme > 0 ? 0.34 : 0.26));
            n.world.spawnParticle(Particle.CRIT, l.clone().add(0, 1.2, 0), 14, 0.25, 0.35, 0.25, 0.25);
            n.world.spawnParticle(Particle.DAMAGE_INDICATOR, l.clone().add(0, 1.4, 0), arme > 0 ? 4 : 2, 0.2, 0.2, 0.2, 0.05);
            try {
                simple.playHurtAnimation(0f);
                hd.playHurtAnimation(0f);
            } catch (RuntimeException ex) {
                // simple effet visuel
            }
            if (mode != Mode.SONNE) {
                // un coup pendant qu'il chancelle déjà le blesse, sans prolonger : pas de douanier cloué sur place
                dureeSonne = arme >= 2 ? 40 : arme == 1 ? 30 : SONNE;
                arreter();
                reprendre = true;
                passer(Mode.SONNE);
            }
            afficher("<#" + Nuit.hex(JAUNE) + "><b>touché</b> " + carres(blessures, BLESSURES, JAUNE));
            Txt.bar(p, blessures >= BLESSURES
                    ? "<#FFE680>Douanier blessé</#FFE680> <#9AA4B2>· il traîne la jambe, mais il ne tombera pas : file !"
                    : "<#FFE680>Douanier touché</#FFE680> <#9AA4B2>· ralenti " + carres(blessures, BLESSURES, JAUNE) + " <#9AA4B2>· il se reprend vite : file !");
        }

        void tick() {
            if (!corps.isValid()) {
                fini = true;
                return;
            }
            Location l = corps.getLocation();
            double x = l.getX() - n.ox, y = l.getY(), z = l.getZ();
            if (blessures > 0) {
                if (n.tick - soin >= SOIN) {
                    blessures--;
                    soin = n.tick;
                    if (mode == Mode.POURSUITE || mode == Mode.RECUP) {
                        afficherChasse("");
                    }
                } else if (n.tick % 8 == 0) {
                    poussiere(l.getX() - n.ox, l.getY() + 0.9, l.getZ(), 0x9C1F2A, 0.9f);
                }
            }
            if (garde != 0 && n.tick >= garde) {
                garde = 0;
                if (mode == Mode.POURSUITE || mode == Mode.RECUP) {
                    afficherChasse("");
                }
            }
            boolean brouille = brouille();
            boolean valide = cible != null && cible.isOnline() && n.joueurs.containsKey(cible.getUniqueId());
            Location pl = valide ? cible.getLocation() : null;
            double prx = valide ? pl.getX() - n.ox : 0, prz = valide ? pl.getZ() : 0;
            double d = valide ? Math.hypot(prx - x, prz - z) : 99, dy = valide ? Math.abs(pl.getY() - y) : 99;
            float face = l.getYaw();
            if (valide && d > 0.2 && (mode == Mode.SOUPCON || mode == Mode.SOMMATION || mode == Mode.RECUP || mode == Mode.ELAN)) {
                // à l'arrêt, il fait face à celui qu'il surveille
                face = lacet(prx - x, prz - z);
                corps.setRotation(face, 0f);
            }
            switch (mode) {
                case RONDE -> {
                    double[] t = route[wp];
                    if (Math.hypot(t[0] - x, t[1] - z) < 1.8) {
                        wp = (wp + 1) % route.length;
                        t = route[wp];
                        aller(t[0], Ville.Y + 1, t[1], Math.sqrt(V_RONDE / V_SPRINT));
                    } else if (n.tick % 10 == 0 && (n.tick % 40 == 0 || !corps.getPathfinder().hasPath())) {
                        aller(t[0], Ville.Y + 1, t[1], Math.sqrt(V_RONDE / V_SPRINT));
                    }
                    // coincé (un recoin, une échelle) : on le remet sur sa ronde
                    if (n.tick - controle >= 100) {
                        if (Math.hypot(x - cx, z - cz) < 1.2) {
                            corps.teleport(n.loc(t[0], Ville.Y + 1, t[1]));   // les passagers (étiquette) suivent
                        }
                        controle = n.tick;
                        cx = x;
                        cz = z;
                    }
                    if (repos > 0) {
                        repos--;
                    } else if (!brouille && n.tick % 5 == 0) {
                        Player p = repere(x, y, z, RAYON_AGENT * facteur(), l.getYaw());
                        if (p != null) {
                            cible = p;
                            jauge = 0.12;
                            passer(Mode.SOUPCON);
                            arreter();
                            afficher("<#" + Nuit.hex(JAUNE) + "><b>?</b>");
                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 0.7f);
                        }
                    }
                }
                case SOUPCON -> {
                    if (!valide || brouille) {
                        lacher(30);
                        break;
                    }
                    if (n.tick % 5 == 0) {
                        boolean visible = d <= RAYON_AGENT * facteur() + 2 && dy <= 3.5 && !Ville.safe(prx, prz) && n.marchandise(cible)
                                && voit(x, y + 1.6, z, cible);
                        // de près il comprend en une seconde, de loin il lui en faut deux
                        jauge += visible ? (d < 3 ? 0.25 : d < 5 ? 0.17 : 0.125) : -0.2;
                        if (jauge >= 1) {
                            passer(Mode.SOMMATION);
                            afficher("<#" + Nuit.hex(ROUGE) + "><b>HALTE ! DOUANE !</b>");
                            alerte(cible, "<#E0484A><b>Repéré !</b> <#E6EDF3>Cours… ou bats-toi.");
                        } else if (jauge <= 0) {
                            Txt.bar(cible, "<#7CFF8A>Le douanier t'a perdu de vue.");
                            lacher(30);
                        } else {
                            int k = (int) Math.ceil(jauge * 5);
                            afficher("<#" + Nuit.hex(JAUNE) + "><b>?</b> " + carres(k, 5, JAUNE));
                            if (visible) {
                                Txt.bar(cible, "<#FFE680>Un douanier te regarde</#FFE680> " + carres(k, 5, JAUNE) + " <#9AA4B2>sors de sa vue !");
                                cible.playSound(pl, Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 0.7f + (float) jauge * 0.9f);
                            }
                        }
                    }
                }
                case SOMMATION -> {
                    if (!valide) {
                        lacher(40);
                    } else if (n.tick - etat >= SOMMATION) {
                        son(Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.6f, 0.6f);
                        chasser();
                    }
                }
                case POURSUITE -> {
                    if (!valide) {
                        lacher(40);
                        break;
                    }
                    if (!essouffle && n.tick - depuis >= SOUFFLE) {
                        essouffle = true;
                        afficherChasse("");
                        son(Sound.ENTITY_PLAYER_BREATH, 1f, 0.8f);
                        Txt.bar(cible, "<#7CFF8A>Le douanier s'essouffle :</#7CFF8A> <#9AA4B2>continue de courir.");
                    }
                    if (n.tick % 5 == 0) {
                        corps.getPathfinder().moveTo(pl, allure());
                        if (d <= SEME - 2 && voit(x, y + 1.6, z, cible)) {
                            vu = n.tick;
                        }
                    }
                    if (n.tick % 10 == 0) {
                        cible.playSound(pl, Sound.BLOCK_NOTE_BLOCK_BIT, 0.7f, n.tick % 20 == 0 ? 0.6f : 0.9f);
                    }
                    if (brouille || (!rancune && !n.marchandise(cible))) {
                        lacher(60);
                    } else if (d > SEME || Ville.safe(prx, prz) || n.tick - vu > PERDU || n.tick - depuis > LASSE) {
                        Txt.bar(cible, "<#7CFF8A>Tu as semé la douane.");
                        cible.playSound(pl, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 0.8f);
                        lacher(60);
                    } else if (d < PORTEE + 0.3 && dy < 1.6) {
                        // au contact : il arme sa matraque, le coup part dans une demi-seconde
                        passer(Mode.ELAN);
                        afficherChasse("!");
                        son(Sound.ITEM_ARMOR_EQUIP_IRON, 1f, 1.5f);
                    }
                }
                case ELAN -> {
                    if (!valide) {
                        lacher(40);
                        break;
                    }
                    if (n.tick % 4 == 0) {
                        corps.getPathfinder().moveTo(pl, allure());
                    }
                    if (n.tick - etat >= ELAN) {
                        simple.swingMainHand();
                        hd.swingMainHand();
                        arreter();
                        passer(Mode.RECUP);
                        afficherChasse("");
                        if (d < PORTEE && dy < 1.8 && !Ville.safe(prx, prz)) {
                            Player touche = cible;
                            son(Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 0.9f);
                            if (n.frappe(touche, x, z)) {
                                // saisi : tous ceux qui le suivaient s'arrêtent là
                                relacher(touche, 200);
                            }
                        } else {
                            son(Sound.ENTITY_PLAYER_ATTACK_NODAMAGE, 1f, 1f);
                            Txt.bar(cible, "<#7CFF8A>Esquivé !");
                        }
                    }
                }
                case RECUP -> {
                    if (!valide) {
                        lacher(40);
                    } else if (n.tick - etat >= RECUP) {
                        chasser();
                    }
                }
                case SONNE -> {
                    if (n.tick % 4 == 0) {
                        etoiles(x, y, z);
                    }
                    if (n.tick - etat >= dureeSonne) {
                        garde = n.tick + GARDE;
                        if (reprendre && valide && (rancune || n.marchandise(cible)) && !brouille) {
                            chasser();
                        } else {
                            lacher(20);
                        }
                    }
                }
                case RETOUR -> {
                    // équipage d'une voiture : il revient à pied et remonte
                    if (n.tick % 10 == 0) {
                        aller(voiture.x, Ville.Y + 1, voiture.z, 0.85);
                    }
                    if (Math.hypot(voiture.x - x, voiture.z - z) < 2.4 || n.tick - depuis > 300) {
                        fini = true;
                    }
                }
            }
            l.setYaw(face);
            l.setPitch(0);
            simple.teleport(l);
            hd.teleport(l);
            if (n.tick % 4 == 0) {
                if (mode == Mode.POURSUITE || mode == Mode.ELAN || mode == Mode.RECUP || mode == Mode.SOMMATION) {
                    int rgb = mode == Mode.ELAN ? JAUNE : ROUGE;
                    for (int i = 0; i < 8; i++) {
                        double a = Math.PI * 2 * i / 8 + n.tick * 0.05;
                        poussiere(x + Math.cos(a) * 1.1, y + 0.15, z + Math.sin(a) * 1.1, rgb, 1.1f);
                    }
                } else if ((mode == Mode.RONDE && repos == 0) || mode == Mode.SOUPCON) {
                    // le cône de vue, tracé au sol : bleu en ronde, jaune quand il soupçonne quelqu'un
                    double r = RAYON_AGENT * facteur();
                    int rgb = mode == Mode.SOUPCON ? JAUNE : brouille ? GRIS : BLEU;
                    for (int i = -5; i <= 5; i++) {
                        double a = Math.toRadians(face + i * DEMI_CONE / 5);
                        poussiere(x - Math.sin(a) * r, y + 0.15, z + Math.cos(a) * r, rgb, 1.0f);
                    }
                    for (int s = -1; s <= 1; s += 2) {
                        double a = Math.toRadians(face + s * DEMI_CONE);
                        for (int k = 1; k <= 3; k++) {
                            poussiere(x - Math.sin(a) * r * k / 4, y + 0.15, z + Math.cos(a) * r * k / 4, rgb, 0.8f);
                        }
                    }
                }
            }
        }

        private void etoiles(double x, double y, double z) {
            for (int i = 0; i < 3; i++) {
                double a = Math.PI * 2 * i / 3 + n.tick * 0.35;
                poussiere(x + Math.cos(a) * 0.45, y + 2.15, z + Math.sin(a) * 0.45, JAUNE, 0.9f);
            }
        }

        void retirer() {
            for (Entity e : new Entity[]{corps, simple, hd}) {
                desinscrire(e);
            }
            etiquette.remove();
            corps.remove();
            simple.remove();
            hd.remove();
        }
    }

    // ------------------------------------------------------------------ voiture de patrouille

    final class Voiture {
        final double[][] route;
        final BlockDisplay simple;
        final ItemDisplay modele;
        final TextDisplay etiquette;
        final List<Agent> equipage = new ArrayList<>();
        int wp;
        double x, z;
        private float cap;
        private boolean arret;
        private int repos = 100;
        private double jauge;

        Voiture(double[][] route) {
            this.route = route;
            int i = (int) (Math.random() * route.length);
            x = route[i][0];
            z = route[i][1];
            wp = (i + 1) % route.length;
            cap = lacet(route[wp][0] - x, route[wp][1] - z);
            Location l = pose();
            simple = n.world.spawn(l, BlockDisplay.class, e -> {
                n.tag(e);
                e.setBlock(Material.BLUE_CONCRETE.createBlockData());
                e.setTransformation(new Transformation(new Vector3f(-1.0f, 0.1f, -2.1f), new AxisAngle4f(), new Vector3f(2.0f, 1.3f, 4.2f), new AxisAngle4f()));
                e.setTeleportDuration(3);
                e.setGlowColorOverride(Color.fromRGB(BLEU));
                e.setBrightness(new Display.Brightness(15, 15));
            });
            modele = n.world.spawn(l, ItemDisplay.class, e -> {
                n.tag(e);
                e.setItemStack(Nuit.objetPack("voiture_douane"));
                e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                e.setTransformation(new Transformation(new Vector3f(0f, 2.2f, 0f), new AxisAngle4f(), new Vector3f(4.4f, 4.4f, 4.4f), new AxisAngle4f()));
                e.setTeleportDuration(3);
                e.setGlowColorOverride(Color.fromRGB(BLEU));
                e.setBrightness(new Display.Brightness(15, 15));
                e.setViewRange(1.6f);
            });
            n.paire(simple, modele);
            etiquette = n.world.spawn(l.clone().add(0, 2.3, 0), TextDisplay.class, e -> {
                n.tag(e);
                e.text(Txt.mm("<#" + Nuit.hex(BLEU) + "><b>DOUANE</b>"));
                e.setBillboard(Display.Billboard.CENTER);
                e.setTeleportDuration(3);
                e.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                e.setShadowed(true);
                e.setBrightness(new Display.Brightness(15, 15));
            });
        }

        private Location pose() {
            Location l = n.loc(x, Ville.Y + 1, z);
            l.setYaw(inverse ? cap + 180f : cap);
            return l;
        }

        private void texte(String mm) {
            etiquette.text(Txt.mm(mm));
        }

        void tick() {
            if (arret) {
                // gyrophare : rouge, bleu, rouge…
                boolean rouge = (n.tick / 6) % 2 == 0;
                if (n.tick % 6 == 0) {
                    modele.setGlowColorOverride(Color.fromRGB(rouge ? ROUGE : BLEU));
                    simple.setGlowColorOverride(Color.fromRGB(rouge ? ROUGE : BLEU));
                }
                if (n.tick % 12 == 0) {
                    n.world.playSound(n.loc(x, Ville.Y + 2, z), Sound.BLOCK_NOTE_BLOCK_BIT, 1.4f, rouge ? 0.75f : 1.0f);
                }
                if (n.tick % 2 == 0) {
                    poussiere(x, Ville.Y + 2.75, z, rouge ? ROUGE : BLEU, 1.6f);
                }
                equipage.removeIf(a -> a.fini);
                if (equipage.isEmpty()) {
                    arret = false;
                    repos = 200;
                    jauge = 0;
                    modele.setGlowing(false);
                    simple.setGlowing(false);
                    texte("<#" + Nuit.hex(BLEU) + "><b>DOUANE</b>");
                }
                return;
            }
            // elle ralentit quand elle a un doute : c'est le signal pour s'écarter
            double pas = jauge > 0 ? V_VOITURE * 0.45 : V_VOITURE;
            double[] t = route[wp];
            double dx = t[0] - x, dz = t[1] - z, d = Math.hypot(dx, dz);
            if (d <= pas) {
                x = t[0];
                z = t[1];
                wp = (wp + 1) % route.length;
            } else {
                x += dx / d * pas;
                z += dz / d * pas;
                // le capot tourne vers la route, sans à-coup
                float ecart = ((lacet(dx, dz) - cap) % 360 + 540) % 360 - 180;
                cap += Math.max(-9f, Math.min(9f, ecart));
            }
            Location l = pose();
            simple.teleport(l);
            modele.teleport(l);
            etiquette.teleport(n.loc(x, Ville.Y + 3.3, z));
            boolean brouille = brouille();
            double r = RAYON_VOITURE * facteur();
            if (repos > 0) {
                repos--;
            } else if (n.tick % 5 == 0) {
                Player p = brouille ? null : repere(x, Ville.Y + 1, z, r, Float.NaN);
                if (p == null) {
                    if (jauge > 0) {
                        jauge = Math.max(0, jauge - 0.15);
                        if (jauge == 0) {
                            texte("<#" + Nuit.hex(BLEU) + "><b>DOUANE</b>");
                        }
                    }
                } else {
                    // deux secondes dans son rayon avant qu'elle ne s'arrête
                    jauge += 0.125;
                    int k = (int) Math.ceil(Math.min(1, jauge) * 5);
                    texte("<#" + Nuit.hex(JAUNE) + "><b>?</b> " + carres(k, 5, JAUNE));
                    if (jauge < 1) {
                        Txt.bar(p, "<#FFE680>Une patrouille ralentit</#FFE680> " + carres(k, 5, JAUNE) + " <#9AA4B2>écarte-toi !");
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 0.7f + (float) jauge * 0.9f);
                    } else {
                        arret = true;
                        modele.setGlowing(true);
                        simple.setGlowing(true);
                        texte("<#" + Nuit.hex(ROUGE) + "><b>DOUANE · CONTRÔLE !</b>");
                        alerte(p, "<#E0484A><b>Contrôle !</b> <#E6EDF3>Deux douaniers descendent : cours… ou bats-toi.");
                        n.world.playSound(n.loc(x, Ville.Y + 2, z), Sound.BLOCK_IRON_DOOR_OPEN, 1f, 1.3f);
                        // deux agents descendent, un de chaque côté ; ils somment avant de courir
                        double a = Math.toRadians(cap), cx = Math.cos(a), cz = Math.sin(a);
                        for (int s = -1; s <= 1; s += 2) {
                            Agent ag = new Agent(route, this, x + cx * 1.7 * s, z + cz * 1.7 * s, p);
                            equipage.add(ag);
                            agents.add(ag);
                        }
                    }
                }
            }
            if (n.tick % 4 == 0 && repos == 0) {
                int rgb = brouille ? GRIS : jauge > 0 ? JAUNE : BLEU;
                int nb = (int) Math.max(12, r * 2.4);
                for (int i = 0; i < nb; i++) {
                    double a = Math.PI * 2 * i / nb + n.tick * 0.05;
                    poussiere(x + Math.cos(a) * r, Ville.Y + 1.1, z + Math.sin(a) * r, rgb, 1.1f);
                }
            }
        }

        void retirer() {
            simple.remove();
            modele.remove();
            etiquette.remove();
        }
    }
}
