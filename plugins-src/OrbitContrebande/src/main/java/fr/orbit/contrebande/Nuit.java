package fr.orbit.contrebande;

import fr.orbit.contrebande.Produit.Additif;
import fr.orbit.contrebande.Produit.Base;
import fr.orbit.contrebande.Produit.Effet;
import fr.orbit.contrebande.Ville.Quartier;
import fr.orbit.contrebande.Ville.Spot;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Une nuit de CONTREBANDE pour une organisation (1 à 4 joueurs), dans la ville de sa place.
 * Tout ce qui compte (crédits, coffre, réputation, postes en cours, carnet) vient de l'{@link Orga} et y retourne.
 */
final class Nuit {

    // ------------------------------------------------------------------ habitués de la ville

    record Client(String nom, Quartier quartier, Villager.Profession metier, Villager.Type type, Effet prefere, double budget,
                  int rep, String phrase) {
    }

    static final List<Client> CLIENTS = List.of(
            // les Docks
            new Client("Brann", Quartier.DOCKS, Villager.Profession.FISHERMAN, Villager.Type.SNOW, Effet.BRULANT, 0.95, 0, "Il fait froid sur le ponton."),
            new Client("Pip", Quartier.DOCKS, Villager.Profession.NITWIT, Villager.Type.SWAMP, Effet.GLACE, 0.85, 0, "C'est pour une expérience. Chut."),
            new Client("Oskar", Quartier.DOCKS, Villager.Profession.TOOLSMITH, Villager.Type.TAIGA, Effet.MAGNETIQUE, 1.0, 0, "J'ai un moteur à rafistoler."),
            new Client("Vega", Quartier.DOCKS, Villager.Profession.FLETCHER, Villager.Type.PLAINS, Effet.LEGER, 1.15, 0, "Mon vaisseau a besoin d'un coup de pouce."),
            new Client("Kito", Quartier.DOCKS, Villager.Profession.CARTOGRAPHER, Villager.Type.SAVANNA, Effet.LUMINEUX, 0.9, 0, "Vite, je suis en pleine tournée."),
            new Client("Mama Rook", Quartier.DOCKS, Villager.Profession.BUTCHER, Villager.Type.JUNGLE, Effet.APAISANT, 1.0, 1, "Mes dockers dorment mal."),
            new Client("Gus le Soudeur", Quartier.DOCKS, Villager.Profession.ARMORER, Villager.Type.DESERT, Effet.FOUDROYANT, 1.1, 1, "Mon chalumeau fait des siennes."),
            new Client("Capitaine Wren", Quartier.DOCKS, Villager.Profession.FISHERMAN, Villager.Type.TAIGA, Effet.SUPRACONDUCTEUR, 1.25, 2, "Mon navire part à l'aube."),
            // le Marché
            new Client("Sœur Maëlle", Quartier.MARCHE, Villager.Profession.CLERIC, Villager.Type.PLAINS, Effet.APAISANT, 1.05, 1, "Pour les nuits agitées du dispensaire."),
            new Client("Nox", Quartier.MARCHE, Villager.Profession.LIBRARIAN, Villager.Type.JUNGLE, Effet.HARMONIQUE, 1.2, 1, "Ce soir, je fais vibrer tout le quartier."),
            new Client("Zia la Fripière", Quartier.MARCHE, Villager.Profession.LEATHERWORKER, Villager.Type.SAVANNA, Effet.PRISMATIQUE, 1.2, 1, "Mes tissus doivent briller."),
            new Client("Chef Basile", Quartier.MARCHE, Villager.Profession.BUTCHER, Villager.Type.PLAINS, Effet.BRULANT, 1.1, 1, "Une épice qui réveille les morts."),
            new Client("DJ Quasar", Quartier.MARCHE, Villager.Profession.LIBRARIAN, Villager.Type.SNOW, Effet.CHANTANT, 1.15, 1, "Le club ouvre dans une heure."),
            new Client("Tess", Quartier.MARCHE, Villager.Profession.ARMORER, Villager.Type.DESERT, Effet.SUPRACONDUCTEUR, 1.25, 2, "Mes circuits chauffent trop."),
            new Client("Lior", Quartier.MARCHE, Villager.Profession.LEATHERWORKER, Villager.Type.TAIGA, Effet.SPECTRAL, 1.3, 2, "Personne ne doit savoir que je suis passée."),
            new Client("Madame Io", Quartier.MARCHE, Villager.Profession.CLERIC, Villager.Type.SWAMP, Effet.DORE, 1.3, 2, "Pour mes potions… légales, bien sûr."),
            // la Haute-Ville
            new Client("Dame Ilsa", Quartier.HAUTE_VILLE, Villager.Profession.CARTOGRAPHER, Villager.Type.SNOW, Effet.PRISMATIQUE, 1.4, 3, "Une pièce de plus pour ma collection."),
            new Client("Baron Corvin", Quartier.HAUTE_VILLE, Villager.Profession.WEAPONSMITH, Villager.Type.DESERT, Effet.SOLAIRE, 1.5, 3, "Je ne paie que pour l'exceptionnel."),
            new Client("Ambassadrice Kael", Quartier.HAUTE_VILLE, Villager.Profession.CLERIC, Villager.Type.SAVANNA, Effet.HARMONIQUE, 1.5, 3, "Une soirée diplomatique à réussir."),
            new Client("Docteur Vance", Quartier.HAUTE_VILLE, Villager.Profession.LIBRARIAN, Villager.Type.PLAINS, Effet.SPECTRAL, 1.45, 3, "Pour la recherche. Évidemment."),
            new Client("Monsieur Aurum", Quartier.HAUTE_VILLE, Villager.Profession.WEAPONSMITH, Villager.Type.PLAINS, Effet.DORE, 1.55, 3, "L'or attire l'or."),
            new Client("Le vieux Hadrien", Quartier.HAUTE_VILLE, Villager.Profession.MASON, Villager.Type.SWAMP, Effet.STELLAIRE, 1.6, 4, "J'ai vu les étoiles de près, autrefois."),
            new Client("Lady Seren", Quartier.HAUTE_VILLE, Villager.Profession.CARTOGRAPHER, Villager.Type.JUNGLE, Effet.STELLAIRE, 1.7, 4, "Je veux ce que personne d'autre n'a."),
            new Client("L'Inconnu", Quartier.HAUTE_VILLE, Villager.Profession.NITWIT, Villager.Type.SWAMP, Effet.STELLAIRE, 2.0, 5, "…"));

    private static final String[] RADIO = {
            "Ici la Voix des Docks, la seule radio que la douane n'a jamais trouvée.",
            "Paraît que le vieux Hadrien a vu une vraie étoile filante. Moi, je vois surtout des drones.",
            "Rappel amical : un douanier ne voit ni à travers les murs, ni dans son dos. Les ruelles sont vos amies.",
            "Un douanier qui vous fixe n'a encore rien vu. Sortez de sa vue avant qu'il ne crie halte.",
            "Conseil de la maison : un douanier arme sa matraque avant de frapper. Écartez-vous… ou frappez le premier.",
            "Un douanier, ça ne se couche pas. Un coup le fait chanceler et le ralentit : profitez-en pour filer, mes petits.",
            "Après un coup, un douanier se met en garde et pare tout. Frappez une fois, puis courez.",
            "Rix jure qu'une bonne batte règle bien des contrôles. Le terminal en vend, paraît-il.",
            "Au Marché, Chef Basile cherche encore l'épice qui réveille les morts. Bonne chance à lui.",
            "On raconte que mélanger de l'or à du supraconducteur donne quelque chose de… stellaire.",
            "La Haute-Ville paie bien. La Haute-Ville surveille bien aussi.",
            "Météo : nuit claire, vent du large, et une forte probabilité de contrôles.",
            "Petite annonce : Gus le Soudeur vend un chalumeau. Il ne marche pas. Gus non plus, d'ailleurs.",
            "Un conseil de vieux routier : ne garde jamais toute ta marchandise sur toi. Le coffre, c'est fait pour ça.",
            "Les douaniers changent de quart à l'aube. Profitez de la nuit, mes petits.",
            "Quelqu'un a encore mis de la glace de comète dans du soufre. Instable, comme on dit.",
            "Dédicace à l'organisation qui a fait rire tout le quai hier soir. Vous vous reconnaîtrez."};

    // ------------------------------------------------------------------ joueurs

    /** Coups de matraque (ou décharges de drone) qu'un joueur encaisse avant la saisie. */
    static final int RESISTANCE = 3;

    static final class Joueur {
        final Player p;
        final Etat etat;
        final BossBar barre;
        /** Skate : en selle ou non, batterie de 0 à 100, planche sous les pieds [vue sans le pack, vue avec]. */
        boolean skate;
        double batterie = 100;
        int batterieVue = -1;
        ItemDisplay[] planche;
        /** Résistance aux coups de la douane (à zéro : saisie), dernier coup reçu, prochain coup permis, barre de poursuite. */
        int resistance = RESISTANCE, dernierCoup, coupPret;
        BossBar alerte;

        Joueur(Player p, Etat etat, BossBar barre) {
            this.p = p;
            this.etat = etat;
            this.barre = barre;
        }
    }

    // ------------------------------------------------------------------ commandes

    final class Commande {
        final Client client;
        final Spot spot;
        final Base base;
        final int qte;
        final Effet voeu;
        final boolean vip;
        final int fin;
        Villager pnj;
        TextDisplay texte;
        ItemDisplay icone, iconeHd;

        Commande(Client client, Spot spot, Base base, int qte, Effet voeu, boolean vip, int fin) {
            this.client = client;
            this.spot = spot;
            this.base = base;
            this.qte = qte;
            this.voeu = voeu;
            this.vip = vip;
            this.fin = fin;
        }

        String resume() {
            return qte + "× " + base.joli() + (voeu == null ? "" : (vip ? " <#E0B864>(exige " : " <#9AA4B2>(si possible ") + voeu.joli()
                    + (vip ? "<#E0B864>)" : "<#9AA4B2>)"));
        }
    }

    // ------------------------------------------------------------------ drones

    enum Type {
        PATROUILLE("DOUANE", 0x62CFF4, Material.OBSERVER, 6.5, 0.12, 0.245),
        SENTINELLE("SENTINELLE", 0xFF9BE8, Material.BEACON, 9.0, 0.08, 0.235),
        CHASSEUR("CHASSEUR", 0xE0484A, Material.TARGET, 5.5, 0.2, 0.27);

        final String nom;
        final int rgb;
        final Material bloc;
        final double rayon, ronde, poursuite;

        Type(String nom, int rgb, Material bloc, double rayon, double ronde, double poursuite) {
            this.nom = nom;
            this.rgb = rgb;
            this.bloc = bloc;
            this.rayon = rayon;
            this.ronde = ronde;
            this.poursuite = poursuite;
        }
    }

    private enum Mode { RONDE, POURSUITE, RETOUR }

    final class Drone {
        final Type type;
        final double[][] route;
        final int expire;
        int wp;
        double x, y, z;
        Mode mode = Mode.RONDE;
        Player cible;
        int depuis, repos, decharge;
        final BlockDisplay corps;
        final ItemDisplay modele;
        final TextDisplay etiquette;
        private double vx, vz;
        private float cap;

        Drone(Type type, double[][] route, int expire) {
            this.type = type;
            this.route = route;
            this.expire = expire;
            int start = rnd.nextInt(route.length);
            x = route[start][0];
            z = route[start][1];
            y = Ville.DRONE_Y;
            wp = (start + 1) % route.length;
            corps = world.spawn(loc(x, y, z), BlockDisplay.class, e -> {
                tag(e);
                e.setBlock(type.bloc.createBlockData());
                e.setTransformation(new Transformation(new Vector3f(-0.35f, -0.35f, -0.35f), new AxisAngle4f(),
                        new Vector3f(0.7f, 0.7f, 0.7f), new AxisAngle4f()));
                e.setTeleportDuration(1);
                e.setGlowing(true);
                e.setGlowColorOverride(Color.fromRGB(type.rgb));
                e.setBrightness(new Display.Brightness(15, 15));
            });
            // avec le pack : un vrai drone (coque, rotors, œil) à la place du bloc ; chaque joueur ne voit que l'un des deux
            String id = type == Type.SENTINELLE ? "drone_sentinelle" : type == Type.CHASSEUR ? "drone_chasseur" : "drone_douane";
            modele = world.spawn(loc(x, y, z), ItemDisplay.class, e -> {
                tag(e);
                e.setItemStack(objetPack(id));
                e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                e.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(1.9f, 1.9f, 1.9f), new AxisAngle4f()));
                e.setTeleportDuration(2);
                e.setGlowing(true);
                e.setGlowColorOverride(Color.fromRGB(type.rgb));
                e.setBrightness(new Display.Brightness(15, 15));
            });
            vx = x;
            vz = z;
            paire(corps, modele);
            etiquette = world.spawn(loc(x, y + 0.65, z), TextDisplay.class, e -> {
                tag(e);
                e.text(Txt.mm("<#" + hex(type.rgb) + "><b>" + type.nom + "</b>"));
                e.setBillboard(Display.Billboard.CENTER);
                e.setTeleportDuration(1);
                e.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                e.setShadowed(true);
                e.setBrightness(new Display.Brightness(15, 15));
            });
        }

        double rayon() {
            int b = orga.niveau("brouilleur");
            return type.rayon * (b >= 2 ? 0.65 : b == 1 ? 0.8 : 1.0);
        }

        void tick() {
            boolean brouille = tick < brouillage;
            switch (mode) {
                case RONDE -> {
                    double[] t = route[wp];
                    if (avancer(t[0], Ville.DRONE_Y, t[1], type.ronde)) {
                        wp = (wp + 1) % route.length;
                    }
                    if (repos > 0) {
                        repos--;
                    } else if (!brouille && tick % 5 == 0) {
                        Player p = repere();
                        if (p != null) {
                            mode = Mode.POURSUITE;
                            cible = p;
                            depuis = tick;
                            couleur(0xE0484A, "<#E0484A><b>" + type.nom + " · ARRÊTE-TOI !</b>");
                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 0.6f);
                            p.playSound(p.getLocation(), Sound.BLOCK_BELL_USE, 0.8f, 1.6f);
                            Txt.title(p, "", "<#E0484A><b>La douane t'a repéré : cours !", 1500);
                            orga.chaleur = Math.min(100, orga.chaleur + 3);
                            alertes++;
                        }
                    }
                }
                case POURSUITE -> {
                    if (cible == null || !cible.isOnline() || !joueurs.containsKey(cible.getUniqueId())) {
                        lacher(40);
                        break;
                    }
                    Location pl = cible.getLocation();
                    double prx = pl.getX() - ox, prz = pl.getZ();
                    avancer(prx, pl.getY() + 2.2, prz, type.poursuite);
                    double dh = Math.hypot(prx - x, prz - z);
                    if (tick % 10 == 0) {
                        cible.playSound(pl, Sound.BLOCK_NOTE_BLOCK_BIT, 0.7f, tick % 20 == 0 ? 0.6f : 0.9f);
                    }
                    if (dh < 1.5 && Math.abs(pl.getY() + 2.2 - y) < 1.5 && tick >= decharge) {
                        // décharge : une résistance en moins, puis le drone recharge une seconde et demie
                        decharge = tick + 30;
                        world.playSound(loc(x, y, z), Sound.ENTITY_BEE_STING, 1f, 0.7f);
                        if (frappe(cible, x, z)) {
                            lacher(200);
                        }
                    } else if (brouille || !marchandise(cible)) {
                        lacher(60);
                    } else if (dh > 16 || Ville.safe(prx, prz) || tick - depuis > 240) {
                        Txt.bar(cible, "<#7CFF8A>Tu as semé le drone.");
                        cible.playSound(pl, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 0.8f);
                        lacher(60);
                    }
                }
                case RETOUR -> {
                    double[] t = route[wp];
                    if (Math.hypot(t[0] - x, t[1] - z) > 0.6) {
                        avancer(t[0], 84, t[1], type.ronde * 2.5);
                    } else if (avancer(t[0], Ville.DRONE_Y, t[1], type.ronde * 2.5)) {
                        mode = Mode.RONDE;
                    }
                    if (repos > 0) {
                        repos--;
                    }
                }
            }
            corps.teleport(loc(x, y, z));
            if (Math.hypot(x - vx, z - vz) > 0.02) {
                cap = (float) Math.toDegrees(Math.atan2(-(x - vx), z - vz)); // le nez du drone suit sa route
            }
            vx = x;
            vz = z;
            Location pose = loc(x, y, z);
            pose.setYaw(cap);
            modele.teleport(pose);
            etiquette.teleport(loc(x, y + 0.65, z));
            if (tick % 4 == 0) {
                int rgb = brouille ? 0x5A6270 : mode == Mode.POURSUITE ? 0xE0484A : type.rgb;
                Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(rgb), 1.1f);
                double r = mode == Mode.RONDE ? rayon() : 1.2;
                int n = (int) Math.max(10, r * 2.4);
                for (int i = 0; i < n; i++) {
                    double a = Math.PI * 2 * i / n + tick * 0.05;
                    world.spawnParticle(Particle.DUST, ox + x + Math.cos(a) * r, Ville.Y + 1.1, z + Math.sin(a) * r, 1, 0, 0, 0, 0, dust);
                }
            }
        }

        private Player repere() {
            Player best = null;
            double bd = Double.MAX_VALUE;
            for (Joueur j : joueurs.values()) {
                Location pl = j.p.getLocation();
                double prx = pl.getX() - ox, prz = pl.getZ();
                double d = Math.hypot(prx - x, prz - z);
                if (d > rayon() || d >= bd || Ville.safe(prx, prz) || !marchandise(j.p)) {
                    continue;
                }
                Location from = loc(x, y - 0.5, z);
                Vector dir = j.p.getEyeLocation().toVector().subtract(from.toVector());
                double len = dir.length();
                if (len > 0.1) {
                    RayTraceResult hit = world.rayTraceBlocks(from, dir.normalize(), len - 0.3, FluidCollisionMode.NEVER, true);
                    if (hit != null && hit.getHitBlock() != null) {
                        continue;
                    }
                }
                best = j.p;
                bd = d;
            }
            return best;
        }

        private void lacher(int pause) {
            mode = Mode.RETOUR;
            repos = pause;
            cible = null;
            couleur(type.rgb, "<#" + hex(type.rgb) + "><b>" + type.nom + "</b>");
            double best = Double.MAX_VALUE;
            for (int i = 0; i < route.length; i++) {
                double d = Math.hypot(route[i][0] - x, route[i][1] - z);
                if (d < best) {
                    best = d;
                    wp = i;
                }
            }
        }

        private void couleur(int rgb, String tag) {
            corps.setGlowColorOverride(Color.fromRGB(rgb));
            modele.setGlowColorOverride(Color.fromRGB(rgb));
            etiquette.text(Txt.mm(tag));
        }

        private boolean avancer(double tx, double ty, double tz, double v) {
            double dx = tx - x, dy = ty - y, dz = tz - z;
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d <= v) {
                x = tx;
                y = ty;
                z = tz;
                return true;
            }
            x += dx / d * v;
            y += dy / d * v;
            z += dz / d * v;
            return false;
        }

        void retirer() {
            corps.remove();
            modele.remove();
            etiquette.remove();
        }
    }

    static String hex(int rgb) {
        return String.format("%06X", rgb & 0xFFFFFF);
    }

    // ------------------------------------------------------------------ état

    final OrbitContrebande plugin;
    final Orga orga;
    final int slot;
    final World world;
    final int ox;
    final Map<UUID, Joueur> joueurs = new LinkedHashMap<>();
    private final Random rnd = new Random();
    private final List<Entity> entites = new ArrayList<>();
    private final List<Commande> commandes = new ArrayList<>();
    private final List<Drone> drones = new ArrayList<>();
    /** La douane au sol : agents à pied et voitures de patrouille. */
    final Douane douane;
    /** Postes de la planque en 3D (avec le pack), posés par-dessus les blocs. */
    private ItemDisplay tableHd, synthHd;
    private final ItemDisplay[] condHd = new ItemDisplay[Ville.CONDS.length];
    private final NamespacedKey cleSkate;
    private Villager fournisseur;
    private final TextDisplay[] lblBacs = new TextDisplay[Ville.BACS.length];
    private final TextDisplay[] lblConds = new TextDisplay[Ville.CONDS.length];
    private TextDisplay lblSynth;

    boolean ended;
    int tick;
    final int duree;
    /** Ce que la nuit a rapporté (ventes), pour le bilan et les OC. */
    long gains;
    int ventes, saisies, alertes;
    final Set<Effet> decouvertes = EnumSet.noneOf(Effet.class);
    private int prochaineCommande, prochainEvenement, prochaineRadio;
    int brouillage;
    private int penurie, finRaid;
    private Interaction caisse;
    private ItemDisplay caisseVue, caisseHd;
    /** Décors en double : [vue sans le pack, vue avec le pack]. Chaque joueur ne voit que la sienne. */
    private final List<Entity[]> paires = new ArrayList<>();
    private int caisseFin;
    private final int etoilesDebut, titreDebut;

    // postes : tick de départ (-1 = vide)
    private final int[] bacDebut = new int[Ville.BACS.length];
    private final int[] condDebut = new int[Ville.CONDS.length];
    private int synthDebut;

    Nuit(OrbitContrebande plugin, Orga orga, int slot) {
        this.plugin = plugin;
        this.orga = orga;
        this.slot = slot;
        this.world = plugin.world();
        this.ox = Ville.ox(slot);
        duree = Math.max(120, plugin.getConfig().getInt("nuit.duree-secondes", 600)) * 20;
        etoilesDebut = orga.etoiles();
        titreDebut = orga.titreIndex();
        douane = new Douane(this);
        cleSkate = new NamespacedKey(plugin, "skate");
    }

    Location loc(double rx, double y, double rz) {
        return new Location(world, ox + rx, y, rz);
    }

    // ------------------------------------------------------------------ habillage (pack de CONTREBANDE)

    boolean hd(Player p) {
        return plugin.pack().charge(p);
    }

    /** Objet porteur d'un modèle 3D du pack (drones, caisse de cargaison). */
    static ItemStack objetPack(String id) {
        ItemStack it = new ItemStack(Material.PAPER);
        org.bukkit.inventory.meta.ItemMeta m = it.getItemMeta();
        m.setItemModel(new org.bukkit.NamespacedKey("contrebande", id));
        it.setItemMeta(m);
        return it;
    }

    /** Un décor en double : {@code simple} pour ceux qui n'ont pas le pack (null = rien à leur montrer), {@code pack} pour les autres. */
    void paire(Entity simple, Entity pack) {
        paires.removeIf(pr -> (pr[0] == null || !pr[0].isValid()) && !pr[1].isValid());
        Entity[] pr = {simple, pack};
        paires.add(pr);
        for (Joueur j : joueurs.values()) {
            voir(j.p, pr);
        }
    }

    private void voir(Player p, Entity[] pr) {
        boolean hd = hd(p);
        if (pr[0] != null) {
            if (hd) {
                p.hideEntity(plugin, pr[0]);
            } else {
                p.showEntity(plugin, pr[0]);
            }
        }
        if (hd) {
            p.showEntity(plugin, pr[1]);
        } else {
            p.hideEntity(plugin, pr[1]);
        }
    }

    /** Objets portés et décors selon que le joueur a le pack ou non (à l'arrivée, et si le pack change en cours de nuit). */
    void habiller(Player p) {
        for (Entity[] pr : paires) {
            voir(p, pr);
        }
        boolean hd = hd(p);
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (it != null && !it.getType().isAir()) {
                inv.setItem(i, Produit.skin(it, hd));
            }
        }
    }

    void tag(Entity e) {
        e.setPersistent(false);
        e.getPersistentDataContainer().set(plugin.key(), PersistentDataType.BYTE, (byte) 1);
        entites.add(e);
    }

    int croissance() {
        double[] f = {1.0, 0.8, 0.63, 0.5};
        return (int) (plugin.getConfig().getInt("nuit.croissance-secondes", 30) * 20 * f[Math.min(3, orga.niveau("engrais"))]);
    }

    int condensation() {
        return plugin.getConfig().getInt("nuit.condenseur-secondes", 25) * 20;
    }

    int synthese() {
        return plugin.getConfig().getInt("nuit.synthese-secondes", 30) * 20;
    }

    void broadcast(String msg) {
        for (Joueur j : joueurs.values()) {
            Txt.com(j.p, msg);
        }
    }

    void sonAll(Sound s, float vol, float pitch) {
        for (Joueur j : joueurs.values()) {
            j.p.playSound(j.p.getLocation(), s, vol, pitch);
        }
    }

    // ------------------------------------------------------------------ début

    void start() {
        orga.normaliser();
        orga.chaleur = Math.max(0, orga.chaleur - 20);
        for (int cx = (ox - 3) >> 4; cx <= (ox + Ville.W + 3) >> 4; cx++) {
            for (int cz = -1; cz <= (Ville.D + 3) >> 4; cz++) {
                world.getChunkAt(cx, cz).addPluginChunkTicket(plugin);
            }
        }
        for (Entity e : world.getNearbyEntities(loc(Ville.W / 2.0, 75, Ville.D / 2.0), Ville.W / 2.0 + 6, 30, Ville.D / 2.0 + 6)) {
            if (e.getPersistentDataContainer().has(plugin.key(), PersistentDataType.BYTE) || e instanceof Item) {
                e.remove();
            }
        }
        Ville.postes(world, slot, orga.bacs(), orga.conds(), orga.synth());
        Ville.grilles(world, slot, orga.marche(), orga.hauteVille());
        // reprise des postes en cours (la production n'avance que pendant les nuits)
        for (int i = 0; i < bacDebut.length; i++) {
            int prog = orga.data.bacs.get(i);
            bacDebut[i] = prog < 0 || i >= orga.bacs() ? -1 : Math.min(-2, -Math.min(prog, croissance()));
            if (bacDebut[i] != -1) {
                Ville.bac(world, slot, i, true, stage(i));
            }
        }
        for (int i = 0; i < condDebut.length; i++) {
            int prog = orga.data.conds.get(i);
            condDebut[i] = prog < 0 || i >= orga.conds() ? -1 : Math.min(-2, -Math.min(prog, condensation()));
            if (condDebut[i] != -1) {
                Ville.condenseur(world, slot, i, true, tick - condDebut[i] < condensation());
            }
        }
        synthDebut = orga.data.synth < 0 || !orga.synth() ? -1 : Math.min(-2, -Math.min(orga.data.synth, synthese()));
        if (synthDebut != -1) {
            Ville.synth(world, slot, tick - synthDebut < synthese());
        }

        fournisseur = world.spawn(loc(Ville.FOURNISSEUR[0], Ville.Y + 1, Ville.FOURNISSEUR[1]), Villager.class,
                CreatureSpawnEvent.SpawnReason.CUSTOM, e -> {
                    tag(e);
                    e.setAI(false);
                    e.setSilent(true);
                    e.setInvulnerable(true);
                    e.setCollidable(false);
                    e.setProfession(Villager.Profession.CLERIC);
                    e.setVillagerType(Villager.Type.SWAMP);
                    e.setVillagerLevel(5);
                    e.customName(Txt.mm("<#E0B864><b>Rix</b> <#9AA4B2>· fournisseur"));
                    e.setCustomNameVisible(true);
                    e.setRotation((float) Ville.FOURNISSEUR[2], 0f);
                });
        for (int i = 0; i < Ville.BACS.length; i++) {
            // un peu vers l'intérieur de la pièce : jamais dans le mur
            double dx = Ville.BACS[i][0] == Ville.BACS[0][0] && Ville.BACS[i][1] > Ville.BACS[0][1] ? 1.2 : 0.5;
            double dz = Ville.BACS[i][1] == Ville.BACS[0][1] ? 1.2 : 0.5;
            lblBacs[i] = label(Ville.BACS[i][0] + dx, Ville.Y + 3.0, Ville.BACS[i][1] + dz, "");
        }
        for (int i = 0; i < Ville.CONDS.length; i++) {
            lblConds[i] = label(Ville.CONDS[i][0] - 0.2, Ville.Y + 3.3, Ville.CONDS[i][1] + 0.5, "");
        }
        lblSynth = label(Ville.SYNTH[0] - 0.2, Ville.Y + 3.4, Ville.SYNTH[1] + 0.5, "");
        label(Ville.TABLE[0] + 0.5, Ville.Y + 2.7, Ville.TABLE[1] - 0.2, "<#62CFF4><b>Table de mélange</b>\n<#9AA4B2>+ carnet de recettes");
        label(Ville.COFFRE[0] + 0.5, Ville.Y + 2.4, Ville.COFFRE[1] - 0.3, "<#C79BFF><b>Coffre</b>\n<#9AA4B2>la réserve de l'orga");
        label(Ville.TERMINAL[0] + 1.0, Ville.Y + 3.0, Ville.TERMINAL[1] + 0.5, "<#E0B864><b>Terminal</b>\n<#9AA4B2>améliorations");
        label(Ville.ENSEIGNE_PLANQUE[0], Ville.Y + 3.6, Ville.ENSEIGNE_PLANQUE[1],
                "<#E0B864><b>PLANQUE</b> <#9AA4B2>· " + orga.nom + "\n<#7CFF8A>zone sûre <#9AA4B2>· la douane n'entre pas");
        enseignes();
        grillesTextes();
        majEtiquettes();

        postes3d();
        // la douane est au sol : agents à pied et voitures. Les drones ne gardent que la Haute-Ville et les descentes.
        douane.agent(Ville.R_D1);
        if (orga.nuits > 0) {
            douane.voiture(Ville.V_DOCKS);      // la toute première nuit, la voiture n'arrive qu'au quart de la nuit
            douane.agent(Ville.R_D2);           // et le quai n'est pas encore gardé
        }
        if (orga.marche()) {
            douane.agent(Ville.R_M1);
            douane.agent(Ville.R_M2);
            douane.voiture(Ville.V_MARCHE);
        }
        if (orga.hauteVille()) {
            drones.add(new Drone(Type.SENTINELLE, Ville.R_H1, Integer.MAX_VALUE));
            douane.voiture(Ville.V_HV);
        }
        if (orga.chaleur >= 40) {
            drones.add(new Drone(Type.CHASSEUR, Ville.R_D3, Integer.MAX_VALUE));
        }
        prochaineCommande = 8 * 20;
        prochainEvenement = (70 + rnd.nextInt(50)) * 20;
        prochaineRadio = 35 * 20;
    }

    private TextDisplay label(double rx, double y, double rz, String text) {
        return world.spawn(loc(rx, y, rz), TextDisplay.class, e -> {
            tag(e);
            e.text(Txt.mm(text));
            e.setBillboard(Display.Billboard.CENTER);
            e.setAlignment(TextDisplay.TextAlignment.CENTER);
            e.setShadowed(true);
            e.setBackgroundColor(Color.fromARGB(140, 10, 12, 18));
            e.setBrightness(new Display.Brightness(15, 15));
            e.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.55f, 0.55f, 0.55f), new AxisAngle4f()));
        });
    }

    /** Enseignes au néon au-dessus des portes. */
    private void enseignes() {
        for (Object[] s : Ville.ENSEIGNES) {
            double x = (Double) s[0], z = (Double) s[1];
            float yaw = (Float) s[2];
            String nom = (String) s[3], col = (String) s[4];
            world.spawn(loc(x, Ville.Y + 4.2, z), TextDisplay.class, e -> {
                tag(e);
                e.text(Txt.mm("<" + col + "><b>" + nom.toUpperCase(java.util.Locale.ROOT) + "</b>"));
                e.setBillboard(Display.Billboard.FIXED);
                e.setRotation(yaw, 0f);
                e.setAlignment(TextDisplay.TextAlignment.CENTER);
                e.setShadowed(true);
                e.setBackgroundColor(Color.fromARGB(200, 8, 10, 16));
                e.setBrightness(new Display.Brightness(15, 15));
                e.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.9f, 0.9f, 0.9f), new AxisAngle4f()));
            });
        }
    }

    /** Panneaux des grilles fermées. */
    private void grillesTextes() {
        int pm = Menus.amelio("marche").prix()[0], ph = Menus.amelio("hv").prix()[0];
        for (int[] v : Ville.PORTES_MARCHE) {
            double cx = (v[0] + v[1]) / 2.0 + 0.5;
            if (!orga.marche()) {
                for (double dz : new double[]{-0.6, 1.6}) {
                    label(cx, Ville.Y + 4.6, Ville.MUR_MARCHE + dz, "<#E0B864><b>LE MARCHÉ</b>\n<#E0484A>fermé <#9AA4B2>· ★ + " + Txt.n(pm)
                            + " cr\n<#9AA4B2>au terminal de la planque");
                }
            }
        }
        for (int[] v : Ville.PORTES_HV) {
            double cx = (v[0] + v[1]) / 2.0 + 0.5;
            if (!orga.hauteVille()) {
                for (double dz : new double[]{-0.6, 1.6}) {
                    label(cx, Ville.Y + 4.6, Ville.MUR_HV + dz, "<#FF9BE8><b>LA HAUTE-VILLE</b>\n<#E0484A>fermée <#9AA4B2>· ★★★ + " + Txt.n(ph)
                            + " cr\n<#9AA4B2>au terminal de la planque");
                }
            }
        }
    }

    // ------------------------------------------------------------------ joueurs

    void rejoindre(Player p) {
        Etat etat = new Etat(p);
        etat.ecrire(plugin, p);
        Etat.preparer(p);
        BossBar barre = BossBar.bossBar(Txt.mm(""), 1f, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
        Joueur j = new Joueur(p, etat, barre);
        joueurs.put(p.getUniqueId(), j);
        p.getInventory().setItem(8, Produit.communicateur());
        if (orga.niveau("skate") > 0) {
            p.getInventory().setItem(7, Produit.skate(orga.niveau("skate")));
        }
        if (orga.niveau("batte") > 0) {
            p.getInventory().setItem(6, Produit.batte(orga.niveau("batte")));
        }
        habiller(p);
        douane.inscrire(p);
        p.teleport(Ville.at(world, slot, Ville.SPAWN[0] + (joueurs.size() - 1) * 0.6, Ville.Y + 1, Ville.SPAWN[1], (float) Ville.SPAWN[2]));
        p.getInventory().setHeldItemSlot(0);
        if (orga.niveau("bottes") > 0) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 0, false, false, true));
        }
        p.showBossBar(barre);
        majBarre(j);
        Txt.title(p, Txt.TITLE, "<#9AA4B2>" + orga.nom + " · nuit " + (orga.nuits + 1) + " · " + orga.titre(), 2500);
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 0.7f);
        if (joueurs.size() == 1 && orga.nuits == 0) {
            regles(p);
        } else {
            Txt.send(p, "<#9AA4B2>Le coffre de la planque contient la réserve de l'organisation. <#E6EDF3>/contrebande regles</#E6EDF3> pour l'aide.");
        }
        for (Joueur o : joueurs.values()) {
            if (o != j) {
                Txt.com(o.p, "<#7CFF8A>" + p.getName() + " rejoint la nuit.");
            }
        }
    }

    static void regles(Player p) {
        Txt.send(p, "<#E0B864><b>Comment jouer</b> <#9AA4B2>(ton organisation garde tout d'une nuit à l'autre)");
        p.sendMessage(Txt.mm("  <#E0B864>1.</#E0B864> <#E6EDF3>Plante un <#C79BFF>germe</#C79BFF> dans un bac (clic droit : il est pris dans ta main ou dans le <#C79BFF>coffre</#C79BFF>)."));
        p.sendMessage(Txt.mm("  <#E0B864>2.</#E0B864> <#E6EDF3>Achète des additifs chez <#E0B864>Rix</#E0B864>, mélange à la <#62CFF4>table</#62CFF4> : la valeur monte. Le carnet retient tes recettes."));
        p.sendMessage(Txt.mm("  <#E0B864>3.</#E0B864> <#E6EDF3>Les commandes arrivent sur le <#62CFF4>communicateur</#62CFF4> : livre le client (clic droit en tenant la marchandise)."));
        p.sendMessage(Txt.mm("  <#E0B864>4.</#E0B864> <#E6EDF3>La <#E0484A>douane</#E0484A> (à pied et en voiture) ne voit ni à travers les murs ni dans son dos : ruelles, passages, toits… ou la planque."));
        p.sendMessage(Txt.mm("  <#E0B864>5.</#E0B864> <#E6EDF3>Au <#E0B864>terminal</#E0B864> : bacs, engrais, condenseur, quartiers, employés… Ton empire grandit nuit après nuit."));
    }

    /** Un joueur part (fin, abandon, déconnexion) : ses objets vont au coffre, son état lui est rendu. */
    void partir(Player p, boolean teleporter) {
        Joueur j = joueurs.remove(p.getUniqueId());
        if (j == null) {
            return;
        }
        deposerTout(p, false);
        if (p.getOpenInventory().getTopInventory().getHolder() instanceof Menus.Holder) {
            p.closeInventory();
        }
        p.hideBossBar(j.barre);
        if (j.alerte != null) {
            p.hideBossBar(j.alerte);
            j.alerte = null;
        }
        descendre(j, null);
        for (Entity[] pr : paires) {
            if (pr[0] != null) {
                p.showEntity(plugin, pr[0]);
            }
            p.showEntity(plugin, pr[1]);
        }
        j.etat.rendre(plugin, p);
        for (Drone d : drones) {
            if (p.equals(d.cible)) {
                d.lacher(20);
            }
        }
        douane.lacher(p);
        if (teleporter && p.getWorld().equals(world)) {
            Location back = plugin.npc().front();
            p.teleport(back != null ? back : org.bukkit.Bukkit.getWorlds().get(0).getSpawnLocation());
        }
    }

    /** Range au coffre tous les objets du jeu portés par le joueur. Renvoie le nombre d'unités rangées. */
    int deposerTout(Player p, boolean sauf_com) {
        int n = 0;
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            String cle = Produit.cle(it);
            if (cle != null) {
                orga.ajouter(cle, it.getAmount());
                n += it.getAmount();
                inv.setItem(i, null);
            }
        }
        String cur = Produit.cle(p.getItemOnCursor());
        if (cur != null) {
            orga.ajouter(cur, p.getItemOnCursor().getAmount());
            n += p.getItemOnCursor().getAmount();
            p.setItemOnCursor(null);
        }
        return n;
    }

    // ------------------------------------------------------------------ fin

    /** Retire tout ce que la nuit a posé et enregistre l'état des postes dans l'organisation. */
    void cleanup() {
        for (int i = 0; i < bacDebut.length; i++) {
            orga.data.bacs.set(i, bacDebut[i] == -1 ? -1 : Math.min(croissance(), tick - bacDebut[i]));
        }
        for (int i = 0; i < condDebut.length; i++) {
            orga.data.conds.set(i, condDebut[i] == -1 ? -1 : Math.min(condensation(), tick - condDebut[i]));
        }
        orga.data.synth = synthDebut == -1 ? -1 : Math.min(synthese(), tick - synthDebut);
        for (Commande c : commandes) {
            retirerCommande(c);
        }
        commandes.clear();
        for (Drone d : drones) {
            d.retirer();
        }
        drones.clear();
        douane.retirer();
        for (Entity e : entites) {
            e.remove();
        }
        entites.clear();
        for (Entity e : world.getNearbyEntities(loc(Ville.W / 2.0, 75, Ville.D / 2.0), Ville.W / 2.0 + 6, 30, Ville.D / 2.0 + 6)) {
            if (e instanceof Item) {
                e.remove();
            }
        }
        Ville.postes(world, slot, orga.bacs(), orga.conds(), orga.synth());
        Ville.grilles(world, slot, false, false);
        for (int cx = (ox - 3) >> 4; cx <= (ox + Ville.W + 3) >> 4; cx++) {
            for (int cz = -1; cz <= (Ville.D + 3) >> 4; cz++) {
                world.removePluginChunkTicket(cx, cz, plugin);
            }
        }
    }

    /** Fin de nuit pour l'organisation : revendeur, salaires, compteurs. Renvoie les lignes du bilan. */
    List<String> bilanOrga() {
        List<String> l = new ArrayList<>();
        double part = Math.min(1.0, tick / (double) duree);
        // revendeur : écoule une partie du coffre à 65 % de la valeur
        int rv = orga.niveau("revendeur");
        if (rv > 0 && part > 0.25) {
            int cap = (int) Math.round((rv >= 2 ? 30 : 12) * part);
            int vendu = 0;
            long recette = 0;
            List<Map.Entry<String, Integer>> stock = new ArrayList<>(orga.data.coffre.entrySet());
            stock.sort((a, b) -> Integer.compare(valeurCle(b.getKey()), valeurCle(a.getKey())));
            for (Map.Entry<String, Integer> e : stock) {
                if (vendu >= cap || !e.getKey().startsWith("prod:")) {
                    continue;
                }
                int n = Math.min(cap - vendu, e.getValue());
                orga.retirer(e.getKey(), n);
                vendu += n;
                recette += Math.round(valeurCle(e.getKey()) * 0.65 * n);
            }
            if (vendu > 0) {
                if (rnd.nextInt(100) < orga.chaleur / 2) {
                    l.add("<#E0484A>Ton revendeur s'est fait contrôler</#E0484A> : " + vendu + " unité(s) perdue(s). <#9AA4B2>(avis de recherche trop haut)");
                } else {
                    orga.credits += recette;
                    orga.fortune += recette;
                    gains += recette;
                    l.add("<#E0B864>Ton revendeur</#E0B864> a écoulé " + vendu + " unité(s) : <#7CFF8A>+" + Txt.n(recette) + " cr");
                }
            }
        }
        // salaires
        long salaires = (orga.niveau("jardinier") > 0 ? 40 : 0) + (rv > 0 ? 50L * rv : 0);
        if (salaires > 0 && part > 0.25) {
            long paye = Math.min(orga.credits, salaires);
            orga.credits -= paye;
            l.add("<#9AA4B2>Salaires des employés : -" + Txt.n(paye) + " cr" + (paye < salaires ? " <#E0484A>(pas assez : ils grognent)" : ""));
        }
        orga.nuits++;
        orga.data.meilleureNuit = Math.max(orga.data.meilleureNuit, gains);
        orga.data.ventes += ventes;
        orga.data.saisies += saisies;
        orga.data.titre = orga.titreIndex();
        return l;
    }

    static int valeurCle(String cle) {
        if (!cle.startsWith("prod:")) {
            return 0;
        }
        Produit p = Produit.depuisSignature(cle.substring(5));
        return p == null ? 0 : p.valeur();
    }

    // ------------------------------------------------------------------ boucle

    void tick() {
        tick++;
        int reste = duree - tick;
        if (reste <= 0) {
            plugin.sessions().finir(this, "<#E0B864><b>L'aube se lève</b>");
            return;
        }
        if (reste == 60 * 20) {
            broadcast("<#E0B864>L'aube approche :</#E0B864> plus qu'une minute pour écouler la marchandise.");
            sonAll(Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 0.8f);
        }
        // la douane se renforce au fil de la nuit
        if (tick == duree / 4) {
            if (orga.nuits == 0) {
                douane.voiture(Ville.V_DOCKS);
                broadcast("<#E0484A>Radio pirate : une voiture de la douane entre aux Docks.</#E0484A> <#9AA4B2>Si elle te voit chargé trop longtemps, deux agents en sortent.");
            } else {
                douane.agent(Ville.R_D3);
                broadcast("<#E0484A>Radio pirate : une patrouille de plus quadrille les Docks.");
            }
        }
        if (tick == duree / 2) {
            douane.agent(orga.marche() ? Ville.R_M2 : Ville.R_D2);
            broadcast("<#E0484A>Radio pirate : la douane double ses rondes.");
        }
        if (tick == duree * 7 / 10) {
            if (orga.hauteVille()) {
                drones.add(new Drone(Type.SENTINELLE, Ville.R_H2, Integer.MAX_VALUE));
            } else {
                douane.agent(orga.marche() ? Ville.R_D2 : Ville.R_D3);
            }
        }
        douane.tick();
        for (Joueur j : joueurs.values()) {
            skateTick(j);
            gardeTick(j);
        }
        for (Iterator<Drone> it = drones.iterator(); it.hasNext(); ) {
            Drone d = it.next();
            if (tick >= d.expire) {
                d.retirer();
                it.remove();
                continue;
            }
            d.tick();
        }
        if (finRaid > 0 && tick == finRaid) {
            finRaid = 0;
            broadcast("<#7CFF8A>La descente est terminée</#7CFF8A> : les chasseurs rentrent à la base.");
        }
        // commandes
        if (tick >= prochaineCommande) {
            nouvelleCommande(false);
            int lvl = orga.etoiles();
            prochaineCommande = tick + Math.max(6, 16 + rnd.nextInt(10) - lvl - 2 * (joueurs.size() - 1)) * 20;
        }
        for (Iterator<Commande> it = commandes.iterator(); it.hasNext(); ) {
            Commande c = it.next();
            if (tick >= c.fin) {
                it.remove();
                retirerCommande(c);
                orga.rep = Math.max(0, orga.rep - 1);
                broadcast("<#E0484A>" + c.client.nom() + " en a eu assez d'attendre</#E0484A> <#9AA4B2>(réputation -1).");
                sonAll(Sound.ENTITY_VILLAGER_NO, 0.6f, 1f);
            }
        }
        // événements et radio
        if (tick >= prochainEvenement) {
            evenement();
            prochainEvenement = tick + (80 + rnd.nextInt(70)) * 20;
        }
        if (tick >= prochaineRadio) {
            broadcast("<#C79BFF>La Voix des Docks :</#C79BFF> <i><#9AA4B2>" + RADIO[rnd.nextInt(RADIO.length)]);
            prochaineRadio = tick + (75 + rnd.nextInt(60)) * 20;
        }
        if (caisse != null && (tick >= caisseFin || !caisse.isValid())) {
            retirerCaisse();
            broadcast("<#9AA4B2>La cargaison a coulé avant que quelqu'un la récupère.");
        }
        // production
        productionTick();
        if (tick % 10 == 0) {
            majEtiquettes();
            for (Joueur j : joueurs.values()) {
                majBarre(j);
                aide(j.p);
            }
            for (Commande c : commandes) {
                majCommande(c);
            }
            verifierPaliers();
        }
        if (tick % (60 * 20) == 0) {
            plugin.store().sauver(orga);
        }
    }

    private int stage(int i) {
        return Math.min(3, (tick - bacDebut[i]) * 3 / croissance());
    }

    private void productionTick() {
        int cr = croissance();
        for (int i = 0; i < bacDebut.length; i++) {
            if (bacDebut[i] == -1) {
                continue;
            }
            int st = stage(i);
            int before = Math.min(3, (tick - 1 - bacDebut[i]) * 3 / cr);
            if (st != before) {
                Ville.bac(world, slot, i, true, st);
                if (st == 3) {
                    world.playSound(loc(Ville.BACS[i][0] + 0.5, Ville.Y + 2, Ville.BACS[i][1] + 0.5), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f);
                }
            }
        }
        for (int i = 0; i < condDebut.length; i++) {
            if (condDebut[i] != -1 && tick - condDebut[i] == condensation()) {
                Ville.condenseur(world, slot, i, true, false);
                world.playSound(loc(Ville.CONDS[i][0] + 0.5, Ville.Y + 1, Ville.CONDS[i][1] + 0.5), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.4f);
            }
        }
        if (synthDebut != -1 && tick - synthDebut == synthese()) {
            Ville.synth(world, slot, false);
            world.playSound(loc(Ville.SYNTH[0] + 0.5, Ville.Y + 1, Ville.SYNTH[1] + 0.5), Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.6f);
        }
        // le jardinier récolte et replante depuis le coffre
        if (orga.niveau("jardinier") > 0 && tick % 20 == 7) {
            for (int i = 0; i < orga.bacs(); i++) {
                if (bacDebut[i] != -1 && tick - bacDebut[i] >= cr) {
                    bacDebut[i] = -1;
                    Ville.bac(world, slot, i, true, -1);
                    orga.ajouter("prod:" + Base.QUARTZ.name(), 4);
                    world.spawnParticle(Particle.HAPPY_VILLAGER, loc(Ville.BACS[i][0] + 0.5, Ville.Y + 2.3, Ville.BACS[i][1] + 0.5), 6, 0.3, 0.2, 0.3, 0);
                }
                if (bacDebut[i] == -1 && orga.retirer(Produit.CLE_GERME, 1) == 1) {
                    bacDebut[i] = tick;
                    Ville.bac(world, slot, i, true, 0);
                }
            }
        }
    }

    private void majBarre(Joueur j) {
        int reste = (duree - tick) / 20;
        float prog = Math.max(0f, Math.min(1f, (duree - tick) / (float) duree));
        int minutes = (int) ((1f - prog) * 8 * 60);
        int h = (22 + minutes / 60) % 24;
        String heure = (h < 10 ? "0" : "") + h + "h" + (minutes % 60 < 10 ? "0" : "") + minutes % 60;
        String chaleur = orga.chaleur >= 70 ? "<#E0484A>recherché ●●●" : orga.chaleur >= 40 ? "<#E0B864>surveillé ●●○" : orga.chaleur >= 15 ? "<#9AA4B2>repéré ●○○" : "<#7CFF8A>discret ○○○";
        String evt = tick < brouillage ? " <#5A6270>·</#5A6270> <#7CFF8A>douane brouillée" : tick < penurie ? " <#5A6270>·</#5A6270> <#E0B864>prix ×1,3"
                : finRaid > tick ? " <#5A6270>·</#5A6270> <#E0484A><b>DESCENTE</b>" : "";
        j.barre.name(Txt.mm("<#62CFF4>☾ " + heure + "</#62CFF4> <#5A6270>·</#5A6270> " + Txt.cr(orga.credits) + " <#5A6270>·</#5A6270> "
                + Txt.stars(orga.etoiles()) + " <#5A6270>·</#5A6270> " + chaleur + evt));
        j.barre.progress(prog);
        j.barre.color(finRaid > tick ? BossBar.Color.RED : reste <= 60 ? BossBar.Color.YELLOW : BossBar.Color.BLUE);
    }

    private void majEtiquettes() {
        int cr = croissance();
        for (int i = 0; i < lblBacs.length; i++) {
            String t;
            if (i >= orga.bacs()) {
                t = "<#5A6270>Bac " + (i + 1) + "\n<#E0484A>verrouillé\n<#5A6270>terminal";
            } else if (bacDebut[i] == -1) {
                t = "<#C79BFF>Bac " + (i + 1) + "\n<#9AA4B2>vide";
            } else if (tick - bacDebut[i] >= cr) {
                t = "<#C79BFF>Bac " + (i + 1) + "\n<#7CFF8A><b>prêt</b>";
            } else {
                t = "<#C79BFF>Bac " + (i + 1) + "\n<#E6EDF3>" + Txt.clock((cr - (tick - bacDebut[i]) + 19) / 20);
            }
            lblBacs[i].text(Txt.mm(t));
        }
        for (int i = 0; i < lblConds.length; i++) {
            String c;
            if (i >= orga.conds()) {
                c = "<#5A6270>Condenseur\n<#E0484A>verrouillé";
            } else if (condDebut[i] == -1) {
                c = "<#FF4FD8>Condenseur\n<#9AA4B2>vide";
            } else if (tick - condDebut[i] >= condensation()) {
                c = "<#FF4FD8>Condenseur\n<#7CFF8A><b>prêt</b>";
            } else {
                c = "<#FF4FD8>Condenseur\n<#E6EDF3>" + Txt.clock((condensation() - (tick - condDebut[i]) + 19) / 20);
            }
            lblConds[i].text(Txt.mm(c));
        }
        String s;
        if (!orga.synth()) {
            s = "<#5A6270>Synthétiseur\n<#E0484A>verrouillé";
        } else if (synthDebut == -1) {
            s = "<#62CFF4>Synthétiseur\n<#9AA4B2>quartz + plasma";
        } else if (tick - synthDebut >= synthese()) {
            s = "<#62CFF4>Synthétiseur\n<#7CFF8A><b>prêt</b>";
        } else {
            s = "<#62CFF4>Synthétiseur\n<#E6EDF3>" + Txt.clock((synthese() - (tick - synthDebut) + 19) / 20);
        }
        lblSynth.text(Txt.mm(s));
    }

    private void aide(Player p) {
        Produit pr = Produit.lire(p.getInventory().getItemInMainHand());
        if (pr != null) {
            Txt.bar(p, pr.base().joli() + " <#5A6270>·</#5A6270> " + pr.effetsJolis() + " <#5A6270>·</#5A6270> " + Txt.cr(pr.valeur()) + " <#9AA4B2>l'unité");
        }
    }

    /** Annonce des nouvelles étoiles et des nouveaux titres. */
    private int etoilesVues = -1, titreVu = -1;

    private void verifierPaliers() {
        if (etoilesVues < 0) {
            etoilesVues = etoilesDebut;
            titreVu = titreDebut;
        }
        int e = orga.etoiles();
        if (e > etoilesVues) {
            etoilesVues = e;
            for (Joueur j : joueurs.values()) {
                Txt.title(j.p, Txt.stars(e), "<#E0B864>Réputation en hausse · de nouveaux clients te contactent", 2200);
            }
            sonAll(Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.3f);
        }
        int t = orga.titreIndex();
        if (t > titreVu) {
            titreVu = t;
            for (Joueur j : joueurs.values()) {
                Txt.title(j.p, "<gradient:#E0B864:#FFF3C4><b>" + Orga.TITRES[t].toUpperCase(java.util.Locale.ROOT) + "</b></gradient>",
                        "<#E6EDF3>" + orga.nom + " · fortune " + Txt.n(orga.fortune) + " cr", 3500);
            }
            sonAll(Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 0.9f);
            sonAll(Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 0.7f);
            broadcast("<#C79BFF>La Voix des Docks :</#C79BFF> <i><#E6EDF3>Tout le quai ne parle que de « " + orga.nom + " ». Nouveau titre : <#E0B864>"
                    + Orga.TITRES[t] + "</#E0B864>.");
        }
    }

    // ------------------------------------------------------------------ commandes

    private boolean ouvert(Quartier q) {
        return q == Quartier.DOCKS || (q == Quartier.MARCHE && orga.marche()) || (q == Quartier.HAUTE_VILLE && orga.hauteVille());
    }

    private Spot spotDe(Client c) {
        List<Spot> l = new ArrayList<>();
        for (Spot s : Ville.SPOTS) {
            boolean pris = false;
            for (Commande o : commandes) {
                if (o.spot.equals(s)) {
                    pris = true;
                    break;
                }
            }
            if (s.quartier() == c.quartier() && !pris) {
                l.add(s);
            }
        }
        // chaque habitué a ses adresses préférées : il revient souvent au même endroit
        int i = CLIENTS.indexOf(c);
        return l.get(rnd.nextInt(3) > 0 ? Math.floorMod(i * 7 + 3, l.size()) : rnd.nextInt(l.size()));
    }

    private void nouvelleCommande(boolean vip) {
        int lvl = orga.etoiles();
        int max = Math.min(6, 2 + lvl / 2 + (joueurs.size() - 1));
        if (!vip && commandes.size() >= max) {
            return;
        }
        List<Client> libres = new ArrayList<>();
        for (Client c : CLIENTS) {
            if (c.rep() > lvl || !ouvert(c.quartier())) {
                continue;
            }
            boolean pris = false;
            for (Commande o : commandes) {
                if (o.client.equals(c)) {
                    pris = true;
                    break;
                }
            }
            if (!pris) {
                libres.add(c);
            }
        }
        if (libres.isEmpty()) {
            return;
        }
        if (vip) {
            // le client le plus exigeant disponible
            libres.sort((a, b) -> Double.compare(b.budget(), a.budget()));
            libres = libres.subList(0, Math.min(3, libres.size()));
        }
        Client cl = libres.get(rnd.nextInt(libres.size()));
        Base base = Base.QUARTZ;
        int roll = rnd.nextInt(100);
        if (orga.synth() && ((cl.quartier() == Quartier.HAUTE_VILLE && roll < 55) || (cl.quartier() == Quartier.MARCHE && roll < 20))) {
            base = Base.CRISTAL;
        } else if (orga.conds() > 0 && rnd.nextInt(100) < (cl.quartier() == Quartier.DOCKS ? 30 : 50)) {
            base = Base.PLASMA;
        }
        int qte = 1 + rnd.nextInt(Math.min(6, 2 + lvl));
        if (base == Base.CRISTAL) {
            qte = Math.max(1, qte / 2);
        }
        Effet voeu = vip || rnd.nextInt(100) < 65 ? cl.prefere() : null;
        // la ville a doublé (v0.5.0) : 30 s de plus pour traverser
        int fin = tick + (vip ? 110 : 120 + 12 * qte) * 20;
        Spot s = spotDe(cl);
        Commande c = new Commande(cl, s, base, qte, voeu, vip, fin);
        c.pnj = world.spawn(loc(s.x(), Ville.Y + 1, s.z()), Villager.class, CreatureSpawnEvent.SpawnReason.CUSTOM, e -> {
            e.setPersistent(false);
            e.getPersistentDataContainer().set(plugin.key(), PersistentDataType.BYTE, (byte) 1);
            e.setAI(false);
            e.setSilent(true);
            e.setInvulnerable(true);
            e.setCollidable(false);
            e.setProfession(cl.metier());
            e.setVillagerType(cl.type());
            e.setVillagerLevel(vip ? 5 : 2);
            e.setGlowing(true);
            e.customName(Txt.mm((vip ? "<#E0B864><b>★ " : "<#E0B864>") + cl.nom()));
            e.setCustomNameVisible(true);
            e.setRotation(s.yaw(), 0f);
        });
        double yaw = Math.toRadians(s.yaw());
        c.texte = world.spawn(loc(s.x() - Math.sin(yaw) * 0.8, Ville.Y + 3.35, s.z() + Math.cos(yaw) * 0.8), TextDisplay.class, e -> {
            e.setPersistent(false);
            e.getPersistentDataContainer().set(plugin.key(), PersistentDataType.BYTE, (byte) 1);
            e.setBillboard(Display.Billboard.CENTER);
            e.setAlignment(TextDisplay.TextAlignment.CENTER);
            e.setShadowed(true);
            e.setBackgroundColor(vip ? Color.fromARGB(190, 60, 44, 8) : Color.fromARGB(150, 10, 12, 18));
            e.setBrightness(new Display.Brightness(15, 15));
            e.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.6f, 0.6f, 0.6f), new AxisAngle4f()));
        });
        ItemStack voulu = new Produit(base, voeu == null ? List.of() : List.of(voeu)).item(1);
        for (int k = 0; k < 2; k++) {
            boolean pack = k == 1;
            ItemDisplay ic = world.spawn(loc(s.x() - Math.sin(yaw) * 0.8, Ville.Y + 4.3, s.z() + Math.cos(yaw) * 0.8), ItemDisplay.class, e -> {
                e.setPersistent(false);
                e.getPersistentDataContainer().set(plugin.key(), PersistentDataType.BYTE, (byte) 1);
                e.setItemStack(Produit.skin(voulu.clone(), pack));
                e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                e.setBillboard(Display.Billboard.CENTER);
                e.setBrightness(new Display.Brightness(15, 15));
                e.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.7f, 0.7f, 0.7f), new AxisAngle4f()));
            });
            if (pack) {
                c.iconeHd = ic;
            } else {
                c.icone = ic;
            }
        }
        paire(c.icone, c.iconeHd);
        commandes.add(c);
        majCommande(c);
        world.spawnParticle(Particle.PORTAL, c.pnj.getLocation().add(0, 1, 0), 30, 0.3, 0.6, 0.3, 0.2);
        String quoi = qte + " " + base.nom.toLowerCase(java.util.Locale.ROOT) + (voeu == null ? "" : (vip ? ", et je veux du " : ", si possible ")
                + voeu.joli() + "<#E6EDF3>");
        broadcast((vip ? "<#E0B864><b>★ CLIENT VIP</b> " : "") + "<#E0B864>" + cl.nom() + "</#E0B864> <#9AA4B2>(" + s.lieu() + ", "
                + cl.quartier().joli() + "<#9AA4B2>) :</#9AA4B2> « " + quoi + ". " + cl.phrase() + " »");
        sonAll(vip ? Sound.BLOCK_NOTE_BLOCK_BELL : Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, vip ? 1.2f : 1.6f);
    }

    private void majCommande(Commande c) {
        int reste = (c.fin - tick) / 20;
        String color = reste <= 20 ? "#E0484A" : reste <= 45 ? "#E0B864" : "#7CFF8A";
        c.texte.text(Txt.mm((c.vip ? "<#E0B864><b>★ VIP · " : "<#E0B864><b>") + c.client.nom() + "</b>\n<#E6EDF3>" + c.qte + "× " + c.base.joli()
                + (c.voeu == null ? "" : "\n<#9AA4B2>" + (c.vip ? "exige" : "veut") + " : " + c.voeu.joli())
                + "\n<" + color + ">" + Txt.clock(reste)));
    }

    private void retirerCommande(Commande c) {
        if (c.pnj != null) {
            world.spawnParticle(Particle.CLOUD, c.pnj.getLocation().add(0, 1, 0), 12, 0.3, 0.5, 0.3, 0.02);
            c.pnj.remove();
        }
        if (c.texte != null) {
            c.texte.remove();
        }
        if (c.icone != null) {
            c.icone.remove();
        }
        if (c.iconeHd != null) {
            c.iconeHd.remove();
        }
    }

    void communicateur(Player p) {
        if (commandes.isEmpty()) {
            Txt.com(p, "<#9AA4B2>Aucune commande pour l'instant. Profites-en pour produire.");
            return;
        }
        Txt.com(p, "<#E6EDF3>Commandes en cours :");
        Location pl = p.getLocation();
        for (Commande c : commandes) {
            double dx = ox + c.spot.x() - pl.getX(), dz = c.spot.z() - pl.getZ();
            p.sendMessage(Txt.mm("  " + (c.vip ? "<#E0B864>★ " : "") + "<#E0B864>" + c.client.nom() + "</#E0B864> <#5A6270>·</#5A6270> " + c.resume()
                    + " <#5A6270>·</#5A6270> <#9AA4B2>" + c.spot.lieu() + ", " + direction(dx, dz) + " " + Math.round(Math.hypot(dx, dz))
                    + " m <#5A6270>·</#5A6270> " + Txt.clock((c.fin - tick) / 20)));
        }
    }

    private static String direction(double dx, double dz) {
        double a = Math.toDegrees(Math.atan2(dx, -dz));
        String[] n = {"nord", "nord-est", "est", "sud-est", "sud", "sud-ouest", "ouest", "nord-ouest"};
        return n[(int) Math.floorMod(Math.round(a / 45.0), 8)];
    }

    private void livrer(Player pl, Commande c) {
        ItemStack main = pl.getInventory().getItemInMainHand();
        Produit p = Produit.lire(main);
        String qui = "<#E0B864>" + c.client.nom() + "</#E0B864>";
        if (p == null) {
            Txt.com(pl, qui + " : « Tu as ma commande ? " + c.resume() + "<#E6EDF3>. Tiens-la en main. »");
            return;
        }
        if (p.base() != c.base) {
            Txt.com(pl, qui + " : « C'est du " + c.base.joli() + "<#E6EDF3> que je veux, pas du " + p.base().joli() + "<#E6EDF3>. »");
            pl.playSound(pl.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
            return;
        }
        if (main.getAmount() < c.qte) {
            Txt.com(pl, qui + " : « J'en veux " + c.qte + ", tu n'en tiens que " + main.getAmount() + ". »");
            pl.playSound(pl.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
            return;
        }
        boolean souhait = c.voeu != null && p.effets().contains(c.voeu);
        if (c.vip && !souhait) {
            Txt.com(pl, qui + " : « Sans " + c.voeu.joli() + "<#E6EDF3> ? Ce n'est pas digne de moi. »");
            pl.playSound(pl.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 0.8f);
            return;
        }
        double f = c.voeu == null ? 1.0 : souhait ? 1.3 : 0.8;
        if (c.vip) {
            f *= 2.5;
        }
        if (tick < penurie) {
            f *= 1.3;
        }
        long paie = Math.round(p.valeur() * c.qte * c.client.budget() * f * (1 + 0.05 * orga.etoiles()));
        main.setAmount(main.getAmount() - c.qte);
        orga.credits += paie;
        orga.fortune += paie;
        gains += paie;
        ventes++;
        orga.rep += c.vip ? 5 : souhait ? 2 : 1;
        commandes.remove(c);
        String merci = c.vip ? "« Remarquable. Je parlerai de toi en haut lieu. »" : souhait ? "« Exactement ce qu'il me fallait. »"
                : c.voeu != null ? "« Pas tout à fait ce que je voulais… »" : "« Parfait. »";
        broadcast(qui + " : " + merci + " <#7CFF8A>+" + Txt.n(paie) + " cr</#7CFF8A> <#9AA4B2>(livré par " + pl.getName() + ")"
                + (c.vip ? " <#E0B864>réputation +5" : souhait ? " <#9AA4B2>bonus souhait, réputation +2" : c.voeu != null ? " <#9AA4B2>sans le souhait : -20 %" : ""));
        pl.playSound(pl.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
        pl.playSound(pl.getLocation(), Sound.ENTITY_VILLAGER_YES, 0.7f, 1f);
        if (c.vip) {
            sonAll(Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.5f);
        }
        retirerCommande(c);
    }

    // ------------------------------------------------------------------ événements

    private void evenement() {
        int r = rnd.nextInt(100);
        int raidChance = 15 + orga.chaleur / 3;
        if (r < raidChance) {
            raid();
        } else if (r < raidChance + 22) {
            nouvelleCommande(true);
        } else if (r < raidChance + 44) {
            cargaison();
        } else if (r < raidChance + 62) {
            brouillage = tick + 30 * 20;
            broadcast("<#7CFF8A><b>Brouillage pirate !</b></#7CFF8A> La Voix des Docks aveugle la douane pendant 30 s. <#9AA4B2>Fonce !");
            sonAll(Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 0.6f);
        } else {
            penurie = tick + 90 * 20;
            broadcast("<#E0B864><b>Pénurie en ville !</b></#E0B864> Pendant 90 s, les clients paient <#E0B864>30 % de plus</#E0B864>.");
            sonAll(Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.4f);
        }
    }

    private void raid() {
        finRaid = tick + 60 * 20;
        double[][][] routes = orga.marche() ? new double[][][]{Ville.R_D1, Ville.R_M1, Ville.R_D3} : new double[][][]{Ville.R_D1, Ville.R_D3, Ville.R_D2};
        for (int i = 0; i < 2; i++) {
            drones.add(new Drone(Type.CHASSEUR, routes[rnd.nextInt(routes.length)], finRaid));
        }
        for (Joueur j : joueurs.values()) {
            Txt.title(j.p, "<#E0484A><b>DESCENTE DE LA DOUANE</b>", "<#E6EDF3>2 drones chasseurs pendant 60 s · planque-toi !", 2500);
        }
        sonAll(Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.4f, 1.4f);
        sonAll(Sound.BLOCK_BELL_USE, 1f, 0.5f);
        broadcast("<#E0484A><b>Descente !</b></#E0484A> La douane lâche ses chasseurs. Ils sont rapides : évite-les ou reste à la planque.");
    }

    private void cargaison() {
        double[][] places = {{42.5, Ville.D - 1.8}, {122.5, Ville.D - 1.8}, {202.5, Ville.D - 1.8}, {81.5, Ville.QUAI - 2.3}, {177.5, Ville.QUAI - 2.3}};
        double[] p = places[rnd.nextInt(places.length)];
        caisseFin = tick + 60 * 20;
        caisseVue = world.spawn(loc(p[0], Ville.Y + 1.5, p[1]), ItemDisplay.class, e -> {
            tag(e);
            e.setItemStack(new ItemStack(Material.BARREL));
            e.setGlowing(true);
            e.setGlowColorOverride(Color.fromRGB(0xE0B864));
            e.setBrightness(new Display.Brightness(15, 15));
        });
        caisseHd = world.spawn(loc(p[0], Ville.Y + 1.45, p[1]), ItemDisplay.class, e -> {
            tag(e);
            e.setItemStack(objetPack("caisse"));
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            e.setTransformation(new Transformation(new Vector3f(0f, 0.3f, 0f), new AxisAngle4f(), new Vector3f(1.5f, 1.5f, 1.5f), new AxisAngle4f()));
            e.setGlowing(true);
            e.setGlowColorOverride(Color.fromRGB(0xE0B864));
            e.setBrightness(new Display.Brightness(15, 15));
        });
        paire(caisseVue, caisseHd);
        caisse = world.spawn(loc(p[0], Ville.Y + 1, p[1]), Interaction.class, e -> {
            tag(e);
            e.setInteractionWidth(1.2f);
            e.setInteractionHeight(1.2f);
        });
        broadcast("<#E0B864><b>Cargaison à la dérive !</b></#E0B864> Une caisse s'est échouée sur le quai. <#9AA4B2>Le premier qui la touche la garde (60 s).");
        sonAll(Sound.ENTITY_FISHING_BOBBER_SPLASH, 1f, 0.7f);
    }

    private void ouvrirCaisse(Player p) {
        retirerCaisse();
        List<String> lot = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Additif a = Additif.values()[rnd.nextInt(Additif.values().length)];
            int n = 2 + rnd.nextInt(4);
            donner(p, Produit.additif(a, n));
            lot.add(n + " " + a.nom);
        }
        int g = 1 + rnd.nextInt(3);
        donner(p, Produit.germe(g));
        lot.add(g + " germe(s)");
        if (rnd.nextInt(3) == 0) {
            long bonus = 50 + rnd.nextInt(150);
            orga.credits += bonus;
            orga.fortune += bonus;
            gains += bonus;
            lot.add(bonus + " cr cachés au fond");
        }
        broadcast("<#E0B864>" + p.getName() + " a récupéré la cargaison :</#E0B864> " + String.join(", ", lot) + ".");
        p.playSound(p.getLocation(), Sound.BLOCK_BARREL_OPEN, 1f, 1f);
    }

    private void retirerCaisse() {
        if (caisse != null) {
            caisse.remove();
            caisse = null;
        }
        if (caisseVue != null) {
            caisseVue.remove();
            caisseVue = null;
        }
        if (caisseHd != null) {
            caisseHd.remove();
            caisseHd = null;
        }
    }

    // ------------------------------------------------------------------ douane

    boolean marchandise(Player p) {
        for (ItemStack it : p.getInventory().getContents()) {
            if (Produit.lire(it) != null) {
                return true;
            }
        }
        return Produit.lire(p.getItemOnCursor()) != null;
    }

    static String coeurs(int restants) {
        StringBuilder b = new StringBuilder("<#E0484A>");
        for (int i = 0; i < RESISTANCE; i++) {
            if (i == restants) {
                b.append("<#5A6270>");
            }
            b.append('❤');
        }
        return b.toString();
    }

    /**
     * Un coup de la douane (matraque d'un agent, décharge d'un drone) parti de (x, z) : une résistance en moins et le joueur
     * est repoussé, ce qui lui rend un peu d'avance. À zéro, c'est la saisie. Renvoie vrai si le joueur est pris.
     */
    boolean frappe(Player p, double x, double z) {
        Joueur j = joueurs.get(p.getUniqueId());
        if (j == null) {
            return false;
        }
        j.resistance--;
        j.dernierCoup = tick;
        if (j.resistance <= 0) {
            j.resistance = RESISTANCE;
            attrape(p);
            return true;
        }
        Location l = p.getLocation();
        Vector v = new Vector(l.getX() - (ox + x), 0, l.getZ() - z);
        if (v.lengthSquared() < 0.01) {
            v = l.getDirection().setY(0);
        }
        if (v.lengthSquared() < 0.01) {
            v = new Vector(0, 0, 1);
        }
        p.setVelocity(v.normalize().multiply(0.8).setY(0.34));
        try {
            p.sendHurtAnimation(0f);
        } catch (RuntimeException ex) {
            // simple effet visuel
        }
        p.playSound(l, Sound.ENTITY_PLAYER_HURT, 0.9f, 1f);
        world.spawnParticle(Particle.CRIT, l.clone().add(0, 1, 0), 12, 0.3, 0.4, 0.3, 0.2);
        Txt.title(p, "", "<#E0484A><b>Touché !</b> <#E6EDF3>Résistance " + coeurs(j.resistance), 900);
        return false;
    }

    /** Barre de poursuite (la résistance du joueur) et récupération : un cœur toutes les 15 s au calme, tout à la planque. */
    private void gardeTick(Joueur j) {
        boolean chasse = douane.poursuit(j.p) || (j.dernierCoup > 0 && tick - j.dernierCoup < 60);
        if (chasse) {
            if (j.alerte == null) {
                j.alerte = BossBar.bossBar(Txt.mm(""), 1f, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
                j.p.showBossBar(j.alerte);
            }
            if (tick % 5 == 0) {
                j.alerte.name(Txt.mm("<#E0484A><b>DOUANE À TES TROUSSES</b> <#9AA4B2>· résistance " + coeurs(j.resistance)
                        + " <#9AA4B2>· frappe un agent pour le sonner"));
                j.alerte.progress(Math.max(0f, Math.min(1f, j.resistance / (float) RESISTANCE)));
            }
            return;
        }
        if (j.alerte != null) {
            j.p.hideBossBar(j.alerte);
            j.alerte = null;
        }
        if (j.resistance < RESISTANCE) {
            Location l = j.p.getLocation();
            if (Ville.safe(l.getX() - ox, l.getZ())) {
                j.resistance = RESISTANCE;
            } else if (tick - j.dernierCoup >= 300) {
                j.resistance++;
                j.dernierCoup = tick;
                Txt.bar(j.p, "<#7CFF8A>Tu reprends ton souffle :</#7CFF8A> <#9AA4B2>résistance " + coeurs(j.resistance));
            }
        }
    }

    void attrape(Player p) {
        int unites = 0;
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (Produit.lire(it) != null) {
                unites += it.getAmount();
                inv.setItem(i, null);
            }
        }
        if (Produit.lire(p.getItemOnCursor()) != null) {
            unites += p.getItemOnCursor().getAmount();
            p.setItemOnCursor(null);
        }
        if (p.getOpenInventory().getTopInventory().getHolder() instanceof Menus.Holder) {
            p.closeInventory();
        }
        long amende = Math.min(orga.credits, Math.max(10, Math.min(500, orga.credits * plugin.getConfig().getInt("douane.amende-pourcent", 10) / 100)));
        orga.credits -= amende;
        orga.chaleur = Math.min(100, orga.chaleur + 15);
        saisies++;
        Joueur pris = joueurs.get(p.getUniqueId());
        if (pris != null) {
            descendre(pris, null);
        }
        Txt.title(p, "<#E0484A><b>CONTRÔLE DOUANIER</b>", "<#E6EDF3>" + unites + " unité(s) saisie(s) · amende " + Txt.cr(amende), 2500);
        broadcast("<#E0484A>La douane a coincé " + p.getName() + "</#E0484A> : " + unites + " unité(s) saisie(s), " + Txt.cr(amende)
                + " d'amende. <#9AA4B2>L'avis de recherche monte.");
        p.playSound(p.getLocation(), Sound.ENTITY_IRON_GOLEM_HURT, 0.8f, 0.7f);
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.4f, 1.6f);
    }

    // ------------------------------------------------------------------ postes de la planque en 3D (pack)

    /** Avec le pack : table, terminal, coffre, condenseurs et synthétiseur en 3D. Le bloc dessous garde le clic et sert sans le pack. */
    private void postes3d() {
        if (tableHd == null) {
            tableHd = poste3d("table_melange", Ville.TABLE, 0f);
            poste3d("terminal", Ville.TERMINAL, -90f);
            poste3d("coffre_orga", Ville.COFFRE, 180f);
        }
        for (int i = 0; i < orga.conds() && i < condHd.length; i++) {
            if (condHd[i] == null) {
                condHd[i] = poste3d("condenseur", Ville.CONDS[i], 90f);
            }
        }
        if (orga.synth() && synthHd == null) {
            synthHd = poste3d("synthetiseur", Ville.SYNTH, 90f);
        }
    }

    private ItemDisplay poste3d(String id, int[] pos, float lacet) {
        Location l = loc(pos[0] + 0.5, Ville.Y + 1.5, pos[1] + 0.5);
        l.setYaw(lacet);
        ItemDisplay d = world.spawn(l, ItemDisplay.class, e -> {
            tag(e);
            e.setItemStack(objetPack(id));
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            // le poste est posé sur un bloc plein : sans luminosité fixe, il serait éclairé comme l'intérieur du bloc (noir)
            e.setBrightness(new Display.Brightness(14, 14));
        });
        paire(null, d);
        return d;
    }

    // ------------------------------------------------------------------ skate à sustentation

    /** Clic droit en tenant le skate : monter ou descendre. */
    void skate(Player p) {
        Joueur j = joueurs.get(p.getUniqueId());
        if (j == null || orga.niveau("skate") <= 0) {
            return;
        }
        if (j.skate) {
            descendre(j, "<#9AA4B2>Tu descends du skate.");
            return;
        }
        if (j.batterie < 15) {
            Txt.bar(p, "<#E0484A>Batterie du skate trop faible</#E0484A> <#9AA4B2>(" + (int) j.batterie + " %) : elle se recharge quand tu marches.");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.4f);
            return;
        }
        AttributeInstance a = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (a != null) {
            a.removeModifier(cleSkate);
            a.addTransientModifier(new AttributeModifier(cleSkate, 0.45, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        }
        Location l = p.getLocation();
        ItemDisplay simple = world.spawn(l, ItemDisplay.class, e -> {
            tag(e);
            e.setItemStack(new ItemStack(Material.LIGHT_BLUE_CARPET));
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            e.setTransformation(new Transformation(new Vector3f(0f, -1.74f, 0f), new AxisAngle4f(), new Vector3f(1.1f, 1f, 2.6f), new AxisAngle4f()));
            e.setBrightness(new Display.Brightness(15, 15));
        });
        ItemDisplay pack = world.spawn(l, ItemDisplay.class, e -> {
            tag(e);
            e.setItemStack(objetPack("skate"));
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            e.setTransformation(new Transformation(new Vector3f(0f, -1.7f, 0f), new AxisAngle4f(), new Vector3f(1.5f, 1.5f, 1.5f), new AxisAngle4f()));
            e.setBrightness(new Display.Brightness(15, 15));
        });
        // la planche voyage avec le joueur (passagère) : elle le suit sans retard
        p.addPassenger(simple);
        p.addPassenger(pack);
        paire(simple, pack);
        j.planche = new ItemDisplay[]{simple, pack};
        j.skate = true;
        p.playSound(l, Sound.BLOCK_BEACON_POWER_SELECT, 0.5f, 1.9f);
        Txt.bar(p, "<#62CFF4>Skate lancé</#62CFF4> <#9AA4B2>· batterie " + (int) j.batterie + " % · clic droit pour descendre");
    }

    private void descendre(Joueur j, String message) {
        AttributeInstance a = j.p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (a != null) {
            a.removeModifier(cleSkate);
        }
        if (j.planche != null) {
            for (ItemDisplay d : j.planche) {
                d.remove();
            }
            j.planche = null;
        }
        if (j.skate && message != null) {
            Txt.bar(j.p, message);
            j.p.playSound(j.p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.4f, 1.9f);
        }
        j.skate = false;
    }

    private void skateTick(Joueur j) {
        int niveau = orga.niveau("skate");
        if (niveau <= 0) {
            return;
        }
        if (j.skate) {
            j.batterie -= 100.0 / ((niveau >= 2 ? 35 : 20) * 20);
            Location l = j.p.getLocation();
            boolean accrochee = j.planche != null && j.planche[1].isValid() && j.p.getPassengers().contains(j.planche[1]);
            if (j.batterie <= 0) {
                j.batterie = 0;
                descendre(j, "<#E0484A>Batterie du skate à plat</#E0484A> <#9AA4B2>: elle se recharge quand tu marches.");
            } else if (!accrochee) {
                descendre(j, null);
            } else {
                for (ItemDisplay d : j.planche) {
                    d.setRotation(l.getYaw(), 0f);
                }
                if (tick % 2 == 0) {
                    world.spawnParticle(Particle.DUST, l.getX(), l.getY() + 0.06, l.getZ(), 2, 0.16, 0.01, 0.16, 0,
                            new Particle.DustOptions(Color.fromRGB(0x62CFF4), 0.8f));
                }
            }
        } else if (j.batterie < 100) {
            j.batterie = Math.min(100, j.batterie + 100.0 / (30 * 20));
        }
        // la batterie se lit sur la barre d'usure de l'objet
        int vue = (int) Math.round(j.batterie / 5) * 5;
        if (tick % 10 == 0 && vue != j.batterieVue) {
            PlayerInventory inv = j.p.getInventory();
            for (int i = 0; i < inv.getSize(); i++) {
                ItemStack it = inv.getItem(i);
                if (Produit.estSkate(it) && it.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable m) {
                    m.setDamage(Math.min(99, 100 - vue));
                    it.setItemMeta(m);
                    inv.setItem(i, it);
                }
            }
            j.batterieVue = vue;
        }
    }

    // ------------------------------------------------------------------ clics

    void clicBloc(Player p, Block b) {
        int rx = b.getX() - ox, y = b.getY(), rz = b.getZ();
        boolean bas = y == Ville.Y + 1, haut = y == Ville.Y + 2;
        for (int i = 0; i < Ville.BACS.length; i++) {
            if (rx == Ville.BACS[i][0] && rz == Ville.BACS[i][1] && (bas || haut)) {
                bac(p, i);
                return;
            }
        }
        for (int i = 0; i < Ville.CONDS.length; i++) {
            if (rx == Ville.CONDS[i][0] && rz == Ville.CONDS[i][1] && (bas || haut)) {
                condenseur(p, i);
                return;
            }
        }
        if (rx == Ville.SYNTH[0] && rz == Ville.SYNTH[1] && (bas || haut)) {
            synthetiseur(p);
        } else if (rx == Ville.TABLE[0] && rz == Ville.TABLE[1] && bas) {
            Menus.melange(this, p);
        } else if (rx == Ville.COFFRE[0] && rz == Ville.COFFRE[1] && bas) {
            Menus.coffre(this, p);
        } else if (rx == Ville.TERMINAL[0] && rz == Ville.TERMINAL[1] && bas) {
            Menus.terminal(this, p);
        }
    }

    void clicEntite(Player p, Entity e) {
        if (e.equals(fournisseur)) {
            Menus.boutique(this, p);
            return;
        }
        if (caisse != null && e.equals(caisse)) {
            ouvrirCaisse(p);
            return;
        }
        for (Commande c : new ArrayList<>(commandes)) {
            if (e.equals(c.pnj)) {
                livrer(p, c);
                return;
            }
        }
    }

    /** Prend une unité : d'abord dans la main, sinon dans l'inventaire, sinon dans le coffre. */
    private boolean prendre(Player p, String cle) {
        ItemStack main = p.getInventory().getItemInMainHand();
        if (cle.equals(Produit.cle(main))) {
            main.setAmount(main.getAmount() - 1);
            return true;
        }
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (cle.equals(Produit.cle(it))) {
                it.setAmount(it.getAmount() - 1);
                return true;
            }
        }
        return orga.retirer(cle, 1) == 1;
    }

    private void bac(Player p, int i) {
        if (i >= orga.bacs()) {
            Txt.bar(p, "<#9AA4B2>Bac verrouillé : achète-le au <#E0B864>terminal</#E0B864> de la planque.");
            return;
        }
        Location at = loc(Ville.BACS[i][0] + 0.5, Ville.Y + 2, Ville.BACS[i][1] + 0.5);
        if (bacDebut[i] == -1) {
            if (!prendre(p, Produit.CLE_GERME)) {
                Txt.bar(p, "<#9AA4B2>Pas de <#C79BFF>germe</#C79BFF> (ni sur toi ni dans le coffre) : Rix en vend.");
                return;
            }
            bacDebut[i] = tick;
            Ville.bac(world, slot, i, true, 0);
            world.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_PLACE, 1f, 1f);
            majEtiquettes();
            return;
        }
        if (tick - bacDebut[i] < croissance()) {
            Txt.bar(p, "<#C79BFF>Ça pousse…</#C79BFF> <#E6EDF3>" + Txt.clock((croissance() - (tick - bacDebut[i]) + 19) / 20));
            return;
        }
        bacDebut[i] = -1;
        Ville.bac(world, slot, i, true, -1);
        donner(p, new Produit(Base.QUARTZ, List.of()).item(4));
        world.playSound(at, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1f, 1f);
        world.spawnParticle(Particle.END_ROD, at, 10, 0.2, 0.2, 0.2, 0.03);
        Txt.bar(p, "<#7CFF8A>+4 " + Base.QUARTZ.joli());
        majEtiquettes();
    }

    private void condenseur(Player p, int i) {
        if (i >= orga.conds()) {
            Txt.bar(p, "<#9AA4B2>Condenseur verrouillé : achète-le au <#E0B864>terminal</#E0B864>.");
            return;
        }
        Location at = loc(Ville.CONDS[i][0] + 0.5, Ville.Y + 1.5, Ville.CONDS[i][1] + 0.5);
        if (condDebut[i] == -1) {
            if (!prendre(p, Produit.CLE_MINERAI)) {
                Txt.bar(p, "<#9AA4B2>Pas de <#FF4FD8>minerai sombre</#FF4FD8> (ni sur toi ni dans le coffre) : Rix en vend.");
                return;
            }
            condDebut[i] = tick;
            Ville.condenseur(world, slot, i, true, true);
            world.playSound(at, Sound.BLOCK_BLASTFURNACE_FIRE_CRACKLE, 1f, 1f);
            majEtiquettes();
            return;
        }
        if (tick - condDebut[i] < condensation()) {
            Txt.bar(p, "<#FF4FD8>Condensation…</#FF4FD8> <#E6EDF3>" + Txt.clock((condensation() - (tick - condDebut[i]) + 19) / 20));
            return;
        }
        condDebut[i] = -1;
        Ville.condenseur(world, slot, i, true, false);
        donner(p, new Produit(Base.PLASMA, List.of()).item(3));
        world.playSound(at, Sound.BLOCK_FIRE_EXTINGUISH, 0.7f, 1.4f);
        world.spawnParticle(Particle.WITCH, at, 20, 0.3, 0.3, 0.3, 0.05);
        Txt.bar(p, "<#7CFF8A>+3 " + Base.PLASMA.joli());
        majEtiquettes();
    }

    private void synthetiseur(Player p) {
        if (!orga.synth()) {
            Txt.bar(p, "<#9AA4B2>Synthétiseur verrouillé : achète-le au <#E0B864>terminal</#E0B864> (★★).");
            return;
        }
        Location at = loc(Ville.SYNTH[0] + 0.5, Ville.Y + 1.5, Ville.SYNTH[1] + 0.5);
        if (synthDebut == -1) {
            String q = "prod:" + Base.QUARTZ.name(), pl = "prod:" + Base.PLASMA.name();
            if (!a(p, q) || !a(p, pl)) {
                Txt.bar(p, "<#9AA4B2>Il faut 1 " + Base.QUARTZ.joli() + " brut et 1 " + Base.PLASMA.joli() + " brut (sur toi ou au coffre).");
                return;
            }
            prendre(p, q);
            prendre(p, pl);
            synthDebut = tick;
            Ville.synth(world, slot, true);
            world.playSound(at, Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.6f);
            majEtiquettes();
            return;
        }
        if (tick - synthDebut < synthese()) {
            Txt.bar(p, "<#62CFF4>Synthèse…</#62CFF4> <#E6EDF3>" + Txt.clock((synthese() - (tick - synthDebut) + 19) / 20));
            return;
        }
        synthDebut = -1;
        Ville.synth(world, slot, false);
        donner(p, new Produit(Base.CRISTAL, List.of()).item(2));
        world.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1.5f);
        world.spawnParticle(Particle.GLOW, at, 25, 0.3, 0.3, 0.3, 0.05);
        Txt.bar(p, "<#7CFF8A>+2 " + Base.CRISTAL.joli());
        majEtiquettes();
    }

    /** Le joueur (ou le coffre) a-t-il au moins une unité de cette clé ? */
    private boolean a(Player p, String cle) {
        for (ItemStack it : p.getInventory().getContents()) {
            if (cle.equals(Produit.cle(it))) {
                return true;
            }
        }
        return orga.stock(cle) > 0;
    }

    void donner(Player p, ItemStack it) {
        for (ItemStack rest : p.getInventory().addItem(Produit.skin(it, hd(p))).values()) {
            String cle = Produit.cle(rest);
            if (cle != null) {
                orga.ajouter(cle, rest.getAmount());
                Txt.bar(p, "<#9AA4B2>Inventaire plein : rangé au coffre.");
            }
        }
    }

    boolean payer(Player p, long prix) {
        if (orga.credits < prix) {
            Txt.bar(p, "<#E0484A>Pas assez de crédits</#E0484A> <#9AA4B2>(" + Txt.n(orga.credits) + " / " + Txt.n(prix) + " cr)");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1f);
            return false;
        }
        orga.credits -= prix;
        return true;
    }

    /** Améliorations achetées pendant la nuit : postes et grilles mis à jour tout de suite. */
    void appliquer(String cle) {
        switch (cle) {
            case "bacs" -> {
                for (int i = 0; i < orga.bacs(); i++) {
                    if (bacDebut[i] == -1) {
                        Ville.bac(world, slot, i, true, -1);
                    }
                }
            }
            case "conds" -> {
                for (int i = 0; i < orga.conds(); i++) {
                    if (condDebut[i] == -1) {
                        Ville.condenseur(world, slot, i, true, false);
                    }
                }
                postes3d();
            }
            case "synth" -> {
                world.getBlockAt(ox + Ville.SYNTH[0], Ville.Y + 2, Ville.SYNTH[1]).setType(Material.END_ROD, false);
                postes3d();
            }
            case "marche", "hv" -> {
                Ville.grilles(world, slot, orga.marche(), orga.hauteVille());
                for (Entity e : new ArrayList<>(entites)) {
                    if (e instanceof TextDisplay td && Math.abs(td.getLocation().getZ() - (cle.equals("hv") ? Ville.MUR_HV : Ville.MUR_MARCHE)) < 2.5
                            && td.getLocation().getY() > Ville.Y + 4) {
                        td.remove();
                        entites.remove(e);
                    }
                }
                if (cle.equals("marche")) {
                    douane.agent(Ville.R_M1);
                    douane.voiture(Ville.V_MARCHE);
                } else {
                    drones.add(new Drone(Type.SENTINELLE, Ville.R_H1, Integer.MAX_VALUE));
                    douane.voiture(Ville.V_HV);
                }
                broadcast("<#7CFF8A>Les grilles de " + (cle.equals("hv") ? Quartier.HAUTE_VILLE.joli() : Quartier.MARCHE.joli())
                        + " <#7CFF8A>s'ouvrent pour " + orga.nom + "</#7CFF8A> : de nouveaux clients… et de nouvelles patrouilles.");
                sonAll(Sound.BLOCK_IRON_DOOR_OPEN, 1f, 0.7f);
            }
            case "batte" -> {
                for (Joueur j : joueurs.values()) {
                    PlayerInventory inv = j.p.getInventory();
                    ItemStack neuf = Produit.skin(Produit.batte(orga.niveau("batte")), hd(j.p));
                    boolean remplace = false;
                    for (int i = 0; i < inv.getSize(); i++) {
                        if (Produit.niveauBatte(inv.getItem(i)) > 0) {
                            inv.setItem(i, neuf);
                            remplace = true;
                        }
                    }
                    if (!remplace) {
                        if (inv.getItem(6) == null) {
                            inv.setItem(6, neuf);
                        } else {
                            inv.addItem(neuf);
                        }
                    }
                    Txt.bar(j.p, "<#E0B864>Batte reçue</#E0B864> <#9AA4B2>: tiens-la en main, clic gauche sur un douanier.");
                }
            }
            case "skate" -> {
                for (Joueur j : joueurs.values()) {
                    PlayerInventory inv = j.p.getInventory();
                    ItemStack neuf = Produit.skin(Produit.skate(orga.niveau("skate")), hd(j.p));
                    boolean remplace = false;
                    for (int i = 0; i < inv.getSize(); i++) {
                        if (Produit.estSkate(inv.getItem(i))) {
                            inv.setItem(i, neuf);
                            remplace = true;
                        }
                    }
                    if (!remplace) {
                        if (inv.getItem(7) == null) {
                            inv.setItem(7, neuf);
                        } else {
                            inv.addItem(neuf);
                        }
                        Txt.bar(j.p, "<#62CFF4>Skate reçu</#62CFF4> <#9AA4B2>: clic droit en le tenant pour monter.");
                    }
                    j.batterieVue = -1;
                }
            }
            case "bottes" -> {
                for (Joueur j : joueurs.values()) {
                    j.p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 0, false, false, true));
                }
            }
            default -> {
            }
        }
        majEtiquettes();
    }

    boolean allows(Location to) {
        return to.getWorld().equals(world) && Ville.onMap(to.getX() - ox, to.getZ());
    }

    /** Signature -> unités portées par le joueur. */
    Map<String, Integer> stock(Player p) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (ItemStack it : p.getInventory().getStorageContents()) {
            Produit pr = Produit.lire(it);
            if (pr != null) {
                m.merge(pr.signature(), it.getAmount(), Integer::sum);
            }
        }
        return m;
    }

    int additifs(Player p, Additif a) {
        int n = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (Produit.lireAdditif(it) == a) {
                n += it.getAmount();
            }
        }
        return n;
    }

    /** Retire {@code n} unités de ce qui correspond (inventaire du joueur). */
    void retirer(Player p, java.util.function.Predicate<ItemStack> match, int n) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] st = inv.getStorageContents();
        for (int i = 0; i < st.length && n > 0; i++) {
            if (st[i] != null && match.test(st[i])) {
                int take = Math.min(n, st[i].getAmount());
                st[i].setAmount(st[i].getAmount() - take);
                n -= take;
                if (st[i].getAmount() <= 0) {
                    st[i] = null;
                }
            }
        }
        inv.setStorageContents(st);
    }
}
