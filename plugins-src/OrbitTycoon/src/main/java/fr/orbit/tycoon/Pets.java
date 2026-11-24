package fr.orbit.tycoon;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Bat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * Pets du Tycoon : 20 espèces (4 par outil, une par bonus) × 4 raretés = 80 pets.
 * - Bonus (seulement avec l'outil du pet) : Fortune (+% de ressources récoltées), XP (+% d'XP d'outil),
 *   Money (+% de Crédits orbitaux), Proc (+% de chance relative de Rafale et Météore).
 *   Valeur selon la rareté : pets.bonus (5 / 10 / 15 / 25 %). Plusieurs pets du même outil s'additionnent.
 * - Obtention : coffre Ménagerie du spawn (rareté tirée selon pets.chances, puis espèce au hasard).
 * - Collection et équipement (3 au plus) : catégorie Pets du Navigateur. Données dans pets.yml.
 * - Les pets équipés tournent autour du joueur : vrais mobs réduits, sans IA, invulnérables, non sauvegardés.
 */
final class Pets implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String TAG = "orbit_pet";
    static final int MAX_EQUIPES = 3;

    // ------------------------------------------------------------------ définitions

    enum Rarete {
        COMMUN("commun", "Commun", "#B0BEC5", 5, 70),
        RARE("rare", "Rare", "#62CFF4", 10, 22),
        EPIQUE("epique", "Épique", "#C77DFF", 15, 7.5),
        LEGENDAIRE("legendaire", "Légendaire", "#E0B864", 25, 0.5);

        final String id, nom, couleur;
        final double bonusDefaut, chanceDefaut;

        Rarete(String id, String nom, String couleur, double bonus, double chance) {
            this.id = id; this.nom = nom; this.couleur = couleur; this.bonusDefaut = bonus; this.chanceDefaut = chance;
        }
    }

    enum Stat {
        FORTUNE("fortune", "Fortune", "de ressources récoltées"),
        XP("xp", "XP", "d'XP d'outil"),
        MONEY("money", "Money", "de Crédits orbitaux"),
        PROC("proc", "Proc de compétence", "de chance de Rafale et Météore");

        final String id, nom, effet;
        Stat(String id, String nom, String effet) { this.id = id; this.nom = nom; this.effet = effet; }
    }

    enum Espece {
        // Pioche (Mine)
        TATOU(Outil.Mode.PIOCHE, Stat.FORTUNE, EntityType.ARMADILLO, "Tatou foreur", 0.55,
                "Il creuse les filons plus vite", "que les foreuses de la station."),
        CHAUVE_SOURIS(Outil.Mode.PIOCHE, Stat.XP, EntityType.BAT, "Chauve-souris écho", 0.7,
                "Son sonar cartographie chaque galerie", "et t'apprend les secrets de la roche."),
        MITE(Outil.Mode.PIOCHE, Stat.MONEY, EntityType.ENDERMITE, "Mite de quartz", 0.8,
                "Elle grignote les débris antiques", "et recrache des Crédits brillants."),
        BRISE(Outil.Mode.PIOCHE, Stat.PROC, EntityType.BREEZE, "Brise minérale", 0.45,
                "Un tourbillon de poussière d'étoile", "qui fait éclater les filons."),
        // Hache (Bûcheron)
        RENARD(Outil.Mode.HACHE, Stat.FORTUNE, EntityType.FOX, "Renard des cimes", 0.5,
                "Il repère les plus beaux chênes", "bien avant toi."),
        ABEILLE(Outil.Mode.HACHE, Stat.XP, EntityType.BEE, "Abeille sylvestre", 0.75,
                "Elle connaît chaque tronc de l'île", "et te souffle ses astuces."),
        PERROQUET(Outil.Mode.HACHE, Stat.MONEY, EntityType.PARROT, "Perroquet marchand", 0.7,
                "Il répète les meilleurs prix", "du marché orbital."),
        OCELOT(Outil.Mode.HACHE, Stat.PROC, EntityType.OCELOT, "Ocelot éclaireur", 0.5,
                "Rapide comme une comète,", "il frappe au bon moment."),
        // Houe (Agriculture)
        POULE(Outil.Mode.HOUE, Stat.FORTUNE, EntityType.CHICKEN, "Poule céleste", 0.7,
                "Elle picore les épis oubliés", "et double tes moissons."),
        LAPIN(Outil.Mode.HOUE, Stat.XP, EntityType.RABBIT, "Lapin lunaire", 0.8,
                "Né sous les lunes de Saturne,", "il sait tout des cultures."),
        GRENOUILLE(Outil.Mode.HOUE, Stat.MONEY, EntityType.FROG, "Grenouille dorée", 0.75,
                "Une légende dit qu'elle porte", "chance aux marchands."),
        RENIFLEUR(Outil.Mode.HOUE, Stat.PROC, EntityType.SNIFFER, "Renifleur ancestral", 0.25,
                "Il flaire les graines les plus rares", "de la galaxie."),
        // Canne à pêche (Pêche)
        AXOLOTL(Outil.Mode.CANNE, Stat.FORTUNE, EntityType.AXOLOTL, "Axolotl stellaire", 0.7,
                "Il rabat les bancs de poissons", "vers ta ligne."),
        TORTUE(Outil.Mode.CANNE, Stat.XP, EntityType.TURTLE, "Tortue des marées", 0.45,
                "Elle a vu passer mille saisons", "de pêche et t'en fait profiter."),
        CALMAR(Outil.Mode.CANNE, Stat.MONEY, EntityType.GLOW_SQUID, "Calmar luisant", 0.45,
                "Sa lumière attire les poissons", "les plus précieux."),
        OURS(Outil.Mode.CANNE, Stat.PROC, EntityType.POLAR_BEAR, "Ours des glaces", 0.3,
                "D'un coup de patte,", "il sort deux poissons d'un coup."),
        // Épée (Mobzone)
        LOUP(Outil.Mode.EPEE, Stat.FORTUNE, EntityType.WOLF, "Loup de garde", 0.5,
                "Il rabat les monstres", "droit sur ta lame."),
        BLAZE(Outil.Mode.EPEE, Stat.XP, EntityType.BLAZE, "Blaze ardent", 0.45,
                "Ses flammes forgent ton expérience", "à chaque combat."),
        VEX(Outil.Mode.EPEE, Stat.MONEY, EntityType.VEX, "Vex spectral", 0.8,
                "Il fouille les poches", "des monstres vaincus."),
        MAGMA(Outil.Mode.EPEE, Stat.PROC, EntityType.MAGMA_CUBE, "Cube de magma", 0.6,
                "Il bondit au cœur de la mêlée", "et déclenche tes pouvoirs.");

        final Outil.Mode outil;
        final Stat stat;
        final EntityType type;
        final String nom;
        final double echelle;
        final String[] description;

        Espece(Outil.Mode outil, Stat stat, EntityType type, String nom, double echelle, String... description) {
            this.outil = outil; this.stat = stat; this.type = type; this.nom = nom; this.echelle = echelle; this.description = description;
        }

        String id() { return name().toLowerCase(Locale.ROOT); }

        Material oeuf() {
            Material m = Material.matchMaterial(type.name() + "_SPAWN_EGG");
            return m != null ? m : Material.EGG;
        }
    }

    /** Un pet = une espèce dans une rareté (80 en tout). */
    record Pet(Espece espece, Rarete rarete) {
        String id() { return espece.id() + "_" + rarete.id; }

        static Pet parse(String id) {
            for (Espece e : Espece.values())
                for (Rarete r : Rarete.values())
                    if (id.equals(e.id() + "_" + r.id)) return new Pet(e, r);
            return null;
        }
    }

    /** Un pet possédé : chaque exemplaire a son propre niveau (0 à 100) et son XP dans le niveau. */
    static final class Exemplaire {
        final String uid;
        final Pet pet;
        int niveau;
        long xp;
        Exemplaire(String uid, Pet pet, int niveau, long xp) { this.uid = uid; this.pet = pet; this.niveau = niveau; this.xp = xp; }
    }

    /** Collection d'un joueur : exemplaires possédés et exemplaires équipés (uids, 3 au plus). */
    private static final class Collection {
        final List<Exemplaire> pets = new ArrayList<>();
        final List<String> equipes = new ArrayList<>();

        Exemplaire get(String uid) {
            for (Exemplaire x : pets) if (x.uid.equals(uid)) return x;
            return null;
        }

        List<Exemplaire> equipes() {
            List<Exemplaire> l = new ArrayList<>();
            for (String u : equipes) { Exemplaire x = get(u); if (x != null) l.add(x); }
            return l;
        }
    }

    private static String nouvelUid() {
        return Long.toHexString(ThreadLocalRandom.current().nextLong() & 0xFFFFFFFFFFL);
    }

    /** Une sauvegarde est à faire (l'XP change souvent : sauvegarde groupée toutes les minutes). */
    private boolean modifie;

    private final OrbitTycoon plugin;
    private final Map<UUID, Collection> collections = new ConcurrentHashMap<>();
    /** Entités visibles des pets équipés, par joueur (même ordre que equipes). */
    private final Map<UUID, List<Entity>> entites = new HashMap<>();
    private BukkitTask tache;
    private long tick;

    Pets(OrbitTycoon plugin) {
        this.plugin = plugin;
    }

    void demarrer() {
        charger();
        for (var w : Bukkit.getWorlds())
            for (Entity e : w.getEntities()) if (e.getScoreboardTags().contains(TAG)) e.remove();
        tache = Bukkit.getScheduler().runTaskTimer(plugin, this::animer, 20L, 2L);
        sauvegarde = Bukkit.getScheduler().runTaskTimer(plugin, () -> { if (modifie) sauver(); }, 1200L, 1200L);
    }

    private BukkitTask sauvegarde;

    void arreter() {
        if (tache != null) tache.cancel();
        if (sauvegarde != null) sauvegarde.cancel();
        for (List<Entity> l : entites.values()) for (Entity e : l) if (e != null) e.remove();
        entites.clear();
        sauver();
    }

    // ------------------------------------------------------------------ réglages

    double bonusRarete(Rarete r) {
        return plugin.getConfig().getDouble("pets.bonus." + r.id, r.bonusDefaut);
    }

    double chanceRarete(Rarete r) {
        return Math.max(0, plugin.getConfig().getDouble("pets.chances." + r.id, r.chanceDefaut));
    }

    /** Chance (en %) de cette rareté à l'ouverture du coffre. */
    double pourcentRarete(Rarete r) {
        double total = 0;
        for (Rarete x : Rarete.values()) total += chanceRarete(x);
        return total <= 0 ? 0 : chanceRarete(r) * 100 / total;
    }

    int niveauMax() { return Math.max(1, plugin.getConfig().getInt("pets.niveau-max", 100)); }

    /** XP pour passer du niveau n au niveau n+1 : base + par-niveau × n (10 + 2n par défaut ≈ 10 900 récoltes de 0 à 100). */
    long xpPour(int niveau) {
        return Math.max(1, plugin.getConfig().getLong("pets.xp.base", 10) + plugin.getConfig().getLong("pets.xp.par-niveau", 2) * niveau);
    }

    /** Bonus (en %) d'un exemplaire : bonus max de sa rareté × niveau / niveau max (0 % au niveau 0). */
    double bonus(Exemplaire x) {
        return bonusRarete(x.pet.rarete) * Math.min(x.niveau, niveauMax()) / niveauMax();
    }

    // ------------------------------------------------------------------ bonus

    /** Bonus total (en %) des pets équipés pour cet outil et ce type de bonus. */
    double bonus(UUID id, Outil.Mode outil, Stat stat) {
        Collection c = collections.get(id);
        if (c == null || outil == null) return 0;
        double total = 0;
        for (Exemplaire x : c.equipes())
            if (x.pet.espece.outil == outil && x.pet.espece.stat == stat) total += bonus(x);
        return total;
    }

    /**
     * XP des pets équipés après une récolte : chaque pet équipé gagne pets.xp.par-recolte × quantité
     * (avec n'importe quel outil : ça monte vite). Annonce les passages de niveau.
     */
    void gagnerXp(Player p, int quantite) {
        Collection c = collections.get(p.getUniqueId());
        if (c == null || c.equipes.isEmpty() || quantite <= 0) return;
        long gain = Math.max(1, Math.round(quantite * plugin.getConfig().getDouble("pets.xp.par-recolte", 1)));
        int max = niveauMax();
        List<Entity> ents = entites.get(p.getUniqueId());
        List<Exemplaire> eq = c.equipes();
        for (int i = 0; i < eq.size(); i++) {
            Exemplaire x = eq.get(i);
            if (x.niveau >= max) continue;
            x.xp += gain;
            modifie = true;
            boolean monte = false;
            while (x.niveau < max && x.xp >= xpPour(x.niveau)) { x.xp -= xpPour(x.niveau); x.niveau++; monte = true; }
            if (x.niveau >= max) x.xp = 0;
            if (!monte) continue;
            OrbitTycoon.msg(p, nomComplet(x.pet) + "<gray> passe <#7CFC9A><bold>niveau " + x.niveau + "</bold></#7CFC9A>"
                    + (x.niveau >= max ? " <#E0B864>(MAX)" : "") + "<gray> : <white>+" + fmt2(bonus(x)) + " % <gray>" + x.pet.espece.stat.effet);
            p.playSound(p.getLocation(), x.niveau >= max ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.6f);
            if (ents != null && i < ents.size() && ents.get(i) != null) {
                Entity en = ents.get(i);
                en.customName(nomEntite(x));
                en.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, en.getLocation().add(0, 0.4, 0), 8, 0.2, 0.2, 0.2, 0);
            }
        }
    }

    /** Bonus avec 2 décimales au plus (6,25 · 0,5 · 25). */
    static String fmt2(double v) {
        String s = String.format(Locale.FRANCE, "%.2f", v);
        return s.replaceAll("0+$", "").replaceAll(",$", "");
    }

    /** Barre de progression de l'XP du niveau (20 traits). */
    private String barre(Exemplaire x) {
        if (x.niveau >= niveauMax()) return "<#E0B864>━━━━━━━━━━━━━━━━━━━━ <bold>MAX";
        long besoin = xpPour(x.niveau);
        int plein = (int) Math.min(20, x.xp * 20 / besoin);
        return "<#7CFC9A>" + "━".repeat(plein) + "<dark_gray>" + "━".repeat(20 - plein)
                + " <gray>" + x.xp + "/" + besoin + " XP";
    }

    /** Quantité récoltée après le bonus Fortune (partie décimale tirée au hasard). */
    int fortune(UUID id, Outil.Mode outil, int quantite) {
        double f = bonus(id, outil, Stat.FORTUNE);
        if (f <= 0 || quantite <= 0) return quantite;
        double extra = quantite * f / 100.0;
        int plus = (int) extra;
        if (ThreadLocalRandom.current().nextDouble() < extra - plus) plus++;
        return quantite + plus;
    }

    // ------------------------------------------------------------------ obtention (coffre Ménagerie)

    Pet tirer() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double total = 0;
        for (Rarete x : Rarete.values()) total += chanceRarete(x);
        Rarete rar = Rarete.COMMUN;
        double t = r.nextDouble(Math.max(total, 1e-9));
        for (Rarete x : Rarete.values()) { t -= chanceRarete(x); if (t < 0) { rar = x; break; } }
        Espece[] es = Espece.values();
        return new Pet(es[r.nextInt(es.length)], rar);
    }

    /** Ajoute un nouvel exemplaire, niveau 0 ; renvoie le nombre d'exemplaires de ce pet possédés. */
    int ajouter(Player p, Pet pet) {
        Collection c = collections.computeIfAbsent(p.getUniqueId(), k -> new Collection());
        c.pets.add(new Exemplaire(nouvelUid(), pet, 0, 0));
        sauver();
        return (int) c.pets.stream().filter(x -> x.pet.equals(pet)).count();
    }

    /** Carte de l'animation d'ouverture : œuf de l'espèce + nom dans la couleur de la rareté. */
    AnimationCoffre.Carte carte(Pet pet) {
        ItemStack it = new ItemStack(pet.espece.oeuf());
        if (pet.rarete.ordinal() >= Rarete.EPIQUE.ordinal()) it.editMeta(m -> m.setEnchantmentGlintOverride(true));
        return new AnimationCoffre.Carte(it, MM.deserialize(nomComplet(pet)));
    }

    /** Cartes du défilement : toutes les espèces dans toutes les raretés (pour le suspense). */
    List<AnimationCoffre.Carte> defilement() {
        List<AnimationCoffre.Carte> l = new ArrayList<>();
        for (Espece e : Espece.values()) for (Rarete r : Rarete.values()) l.add(carte(new Pet(e, r)));
        return l;
    }

    /** Chance (en %) d'obtenir ce pet précis (rareté et espèce) avec une clé. */
    double chancePet(Rarete r) { return pourcentRarete(r) / Espece.values().length; }

    /** Pet gagné à la Ménagerie (à la fin de l'animation) : ajout à la collection et annonces. */
    void recevoir(Player p, Pet pet) {
        int n = ajouter(p, pet);
        OrbitTycoon.msg(p, "<#FF8FC7><bold>Ménagerie</bold></#FF8FC7><gray> : tu obtiens " + nomComplet(pet)
                + (n > 1 ? " <dark_gray>(×" + n + ")" : " <#7CFC9A>(nouveau !)") + "<gray>, niveau 0. "
                + "<dark_gray>Équipe-le (Navigateur → Pets) et récolte pour le faire monter jusqu'au niveau " + niveauMax() + ".");
        if (pet.rarete == Rarete.LEGENDAIRE) {
            Bukkit.broadcast(MM.deserialize("<#E0B864><bold>✦ LÉGENDAIRE ✦</bold> <white>" + p.getName()
                    + "</white><gray> a obtenu " + nomComplet(pet) + "<gray> dans la Ménagerie !"));
            for (Player o : Bukkit.getOnlinePlayers()) o.playSound(o.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.2f);
        }
    }

    /** Aperçu du coffre Ménagerie : les 20 espèces, puis les 4 raretés et leurs chances. */
    void remplirApercu(Inventory inv, int[] cases) {
        Espece[] es = Espece.values();
        for (int i = 0; i < es.length && i < cases.length; i++) {
            Espece e = es[i];
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Outil : <" + e.outil.couleur + ">" + e.outil.nom);
            lore.add("<gray>Bonus : <white>" + e.stat.nom + " <dark_gray>(" + e.stat.effet + ")");
            lore.add("");
            lore.add("<#E0B864>Chances de drop de ce pet :");
            for (Rarete r : Rarete.values())
                lore.add("<dark_gray> • <" + r.couleur + ">" + r.nom + " <gray>+" + fmt(bonusRarete(r)) + " %"
                        + " <dark_gray>→ <white>" + fmtChance(chancePet(r)) + " %");
            lore.add("");
            for (String d : e.description) lore.add("<#8FA3B8><italic>" + d);
            inv.setItem(cases[i], icone(e.oeuf(), "<white><bold>" + e.nom + "</bold>", lore, false));
        }
        Material[] verres = {Material.LIGHT_GRAY_STAINED_GLASS_PANE, Material.LIGHT_BLUE_STAINED_GLASS_PANE,
                Material.PURPLE_STAINED_GLASS_PANE, Material.YELLOW_STAINED_GLASS_PANE};
        for (Rarete r : Rarete.values()) {
            int i = es.length + 1 + r.ordinal();
            if (i >= cases.length) break;
            inv.setItem(cases[i], icone(verres[r.ordinal()], "<" + r.couleur + "><bold>" + r.nom + "</bold>", List.of(
                    "<gray>Chance de la rareté : <#E0B864>" + fmt(pourcentRarete(r)) + " %",
                    "<gray>Chance d'un pet précis : <white>" + fmtChance(chancePet(r)) + " %",
                    "<gray>Bonus du pet : <white>+" + fmt(bonusRarete(r)) + " %"), r == Rarete.LEGENDAIRE));
        }
    }

    // ------------------------------------------------------------------ affichage

    static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.FRANCE, "%.1f", v);
    }

    /** Petits pourcentages lisibles : 3,5 · 0,375 · 0,025 (zéros inutiles retirés). */
    static String fmtChance(double v) {
        String s = String.format(Locale.FRANCE, v >= 1 ? "%.2f" : "%.3f", v);
        return s.contains(",") ? s.replaceAll("0+$", "").replaceAll(",$", "") : s;
    }

    String nomComplet(Pet pet) {
        return "<" + pet.rarete.couleur + "><bold>" + pet.espece.nom + "</bold> <dark_gray>[</dark_gray>"
                + pet.rarete.nom + "<dark_gray>]</dark_gray></" + pet.rarete.couleur + ">";
    }

    private List<String> description(Exemplaire x) {
        Pet pet = x.pet;
        Espece e = pet.espece;
        List<String> l = new ArrayList<>();
        l.add("<gray>Rareté : <" + pet.rarete.couleur + "><bold>" + pet.rarete.nom);
        l.add("<gray>Outil : <" + e.outil.couleur + ">" + e.outil.nom + " <dark_gray>(" + e.outil.zone + ")");
        l.add("<gray>Niveau : <white><bold>" + x.niveau + "</bold><gray>/" + niveauMax());
        l.add(barre(x));
        l.add("<gray>Bonus : <" + pet.rarete.couleur + ">+" + fmt2(bonus(x)) + " % <white>" + e.stat.effet);
        l.add("<dark_gray>(max +" + fmt(bonusRarete(pet.rarete)) + " % au niveau " + niveauMax() + ", seulement avec la "
                + e.outil.nom.toLowerCase(Locale.ROOT) + ")");
        l.add("");
        for (String d : e.description) l.add("<#8FA3B8><italic>" + d);
        return l;
    }

    /** Nom flottant au-dessus du pet : nom, rareté, niveau. */
    private Component nomEntite(Exemplaire x) {
        return MM.deserialize("<" + x.pet.rarete.couleur + ">" + x.pet.espece.nom + " <dark_gray>[</dark_gray>"
                + x.pet.rarete.nom + "<dark_gray>] <white>Nv." + x.niveau);
    }

    private static ItemStack icone(Material mat, String nom, List<String> lore, boolean brille) {
        ItemStack it = new ItemStack(mat);
        it.editMeta(m -> {
            m.displayName(Navigateur.texte(nom));
            m.lore(lore.stream().map(s -> s.isEmpty() ? Component.empty() : Navigateur.texte(s)).toList());
            m.addItemFlags(ItemFlag.values());
            if (brille) m.setEnchantmentGlintOverride(true);
        });
        return it;
    }

    // ------------------------------------------------------------------ menu (Navigateur → Pets)

    private static final class Menu implements InventoryHolder {
        Inventory inv;
        final Map<Integer, Consumer<Player>> actions = new HashMap<>();
        @Override public Inventory getInventory() { return inv; }
    }

    private static final int SLOT_INFO = 4, SLOT_PREC = 45, SLOT_RETOUR = 46, SLOT_SUIV = 53, SLOT_FERMER = 52;
    private static final int[] SLOTS_EQUIPES = {48, 49, 50};

    void ouvrirMenu(Player p, int page) {
        Collection c = collections.computeIfAbsent(p.getUniqueId(), k -> new Collection());
        // Exemplaires possédés, rangés par outil, espèce, rareté (de la plus haute à la plus basse), puis niveau.
        List<Exemplaire> liste = new ArrayList<>(c.pets);
        liste.sort(java.util.Comparator.<Exemplaire>comparingInt(x -> x.pet.espece.ordinal())
                .thenComparing(x -> -x.pet.rarete.ordinal()).thenComparing(x -> -x.niveau));
        long differents = c.pets.stream().map(x -> x.pet).distinct().count();
        int[] cases = Coffres.CASES;
        int pages = Math.max(1, (liste.size() + cases.length - 1) / cases.length);
        int pg = Math.max(0, Math.min(page, pages - 1));

        Menu menu = new Menu();
        menu.inv = Bukkit.createInventory(menu, 54, Habillage.titre(plugin, p, 6, "Pets"));

        // En-tête : collection et bonus actifs.
        List<String> info = new ArrayList<>();
        info.add("<gray>Collection : <white>" + differents + "<gray>/" + (Espece.values().length * Rarete.values().length)
                + " <dark_gray>(" + liste.size() + " pet" + (liste.size() > 1 ? "s" : "") + ")");
        info.add("<gray>Équipés : <white>" + c.equipes.size() + "<gray>/" + MAX_EQUIPES);
        info.add("");
        info.add("<#E0B864>Bonus actifs :");
        boolean aucun = true;
        for (Outil.Mode o : Outil.Mode.values())
            for (Stat s : Stat.values()) {
                double b = bonus(p.getUniqueId(), o, s);
                if (b <= 0) continue;
                aucun = false;
                info.add("<dark_gray> • <" + o.couleur + ">" + o.nom + "<gray> : <white>+" + fmt2(b) + " % <gray>" + s.effet);
            }
        if (aucun) info.add("<dark_gray> • aucun");
        info.add("");
        info.add("<gray>Les pets arrivent <white>niveau 0<gray> : équipe-les,");
        info.add("<gray>chaque récolte leur donne de l'XP.");
        info.add("<gray>Bonus max au <white>niveau " + niveauMax() + "<gray>.");
        info.add("");
        info.add("<gray>Obtiens des pets dans le coffre");
        info.add("<#FF8FC7>Ménagerie<gray> du spawn (/coffres).");
        poser(menu, SLOT_INFO, icone(Material.LEAD, "<#FF8FC7><bold>Mes pets</bold>", info, true), null);

        // Pets de la page.
        for (int i = 0; i < cases.length; i++) {
            int k = pg * cases.length + i;
            if (k >= liste.size()) break;
            Exemplaire x = liste.get(k);
            boolean equipe = c.equipes.contains(x.uid);
            List<String> lore = new ArrayList<>(description(x));
            lore.add("");
            lore.add(equipe ? "<#7CFC9A>✔ Équipé <dark_gray>· <#62CFF4>clic pour retirer" : "<#62CFF4>➜ Clic pour équiper");
            poser(menu, cases[i], icone(x.pet.espece.oeuf(), nomComplet(x.pet) + " <white>Nv." + x.niveau, lore, equipe),
                    j -> { basculer(j, x.uid); ouvrirMenu(j, pg); });
        }
        if (liste.isEmpty())
            poser(menu, 22, icone(Material.BARRIER, "<gray>Aucun pet pour l'instant", List.of(
                    "<gray>Ouvre le coffre <#FF8FC7>Ménagerie<gray> au spawn", "<gray>avec une <#E0B864>Clé Pets<gray> en main."), false), null);

        // Bas : emplacements équipés, pages, retour, fermer.
        for (int i = 0; i < SLOTS_EQUIPES.length; i++) {
            List<Exemplaire> eq = c.equipes();
            if (i < eq.size()) {
                Exemplaire x = eq.get(i);
                List<String> lore = new ArrayList<>(description(x));
                lore.add("");
                lore.add("<#62CFF4>➜ Clic pour retirer");
                poser(menu, SLOTS_EQUIPES[i], icone(x.pet.espece.oeuf(), nomComplet(x.pet) + " <white>Nv." + x.niveau, lore, true),
                        j -> { basculer(j, x.uid); ouvrirMenu(j, pg); });
            } else {
                poser(menu, SLOTS_EQUIPES[i], icone(Material.GRAY_STAINED_GLASS_PANE, "<gray>Emplacement libre " + (i + 1),
                        List.of("<gray>Clique sur un pet pour l'équiper."), false), null);
            }
        }
        if (pg > 0) poser(menu, SLOT_PREC, icone(Material.SPECTRAL_ARROW, "<#E0B864>← Page " + pg, List.of(), false), j -> ouvrirMenu(j, pg - 1));
        if (pg < pages - 1) poser(menu, SLOT_SUIV, icone(Material.SPECTRAL_ARROW, "<#E0B864>Page " + (pg + 2) + " →", List.of(), false), j -> ouvrirMenu(j, pg + 1));
        poser(menu, SLOT_RETOUR, icone(Material.ARROW, "<#E0B864><bold>← Retour</bold>", List.of("<gray>Navigateur orbital"), false),
                j -> plugin.navigateur().ouvrir(j));
        poser(menu, SLOT_FERMER, icone(Material.BARRIER, "<#FF4D4D><bold>Fermer</bold>", List.of(), false), Player::closeInventory);
        Habillage.fond(plugin, p, menu.inv);
        p.openInventory(menu.inv);
    }

    private static void poser(Menu m, int slot, ItemStack it, Consumer<Player> action) {
        m.inv.setItem(slot, it);
        if (action != null) m.actions.put(slot, action);
    }

    /** Équipe le pet s'il ne l'est pas (place libre requise), le retire sinon. */
    private void basculer(Player p, String uid) {
        Collection c = collections.computeIfAbsent(p.getUniqueId(), k -> new Collection());
        Exemplaire x = c.get(uid);
        if (x == null) return;
        Pet pet = x.pet;
        if (c.equipes.remove(uid)) {
            OrbitTycoon.msg(p, "<gray>Pet retiré : " + nomComplet(pet) + "<gray>.");
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 0.9f);
        } else if (c.equipes.size() >= MAX_EQUIPES) {
            OrbitTycoon.msg(p, "<#FF4D4D>Tu as déjà " + MAX_EQUIPES + " pets équipés : retires-en un d'abord.");
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.8f);
            return;
        } else {
            c.equipes.add(uid);
            OrbitTycoon.msg(p, "<gray>Pet équipé : " + nomComplet(pet) + "<gray> !");
            p.playSound(p.getLocation(), Sound.ENTITY_ALLAY_ITEM_GIVEN, 0.7f, 1.2f);
        }
        sauver();
        retirerEntites(p.getUniqueId());   // recréées au prochain passage de l'animation
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void clicMenu(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Menu menu)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getView().getTopInventory()) return;
        Consumer<Player> action = menu.actions.get(e.getRawSlot());
        if (action != null) Bukkit.getScheduler().runTask(plugin, () -> { if (p.isOnline()) action.accept(p); });
    }

    @EventHandler
    public void glisserMenu(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ pets visibles autour du joueur

    private boolean cache(Player p) {
        return p.getGameMode() == GameMode.SPECTATOR || p.hasPotionEffect(PotionEffectType.INVISIBILITY)
                || (plugin.staff() != null && plugin.staff().invisible(p)) || p.isDead();
    }

    private void retirerEntites(UUID id) {
        List<Entity> l = entites.remove(id);
        if (l != null) for (Entity e : l) if (e != null) e.remove();
    }

    /** Toutes les 2 ticks : chaque pet équipé tourne autour de son joueur (cercle de 1,5 bloc, léger flottement). */
    private void animer() {
        tick += 2;
        entites.keySet().removeIf(id -> {
            if (Bukkit.getPlayer(id) != null) return false;
            for (Entity e : entites.get(id)) if (e != null) e.remove();
            return true;
        });
        for (Player p : Bukkit.getOnlinePlayers()) {
            Collection c = collections.get(p.getUniqueId());
            if (c == null || c.equipes.isEmpty() || cache(p)) { retirerEntites(p.getUniqueId()); continue; }
            List<Entity> l = entites.computeIfAbsent(p.getUniqueId(), k -> new ArrayList<>());
            List<Exemplaire> eq = c.equipes();
            int n = eq.size();
            while (l.size() < n) l.add(null);
            while (l.size() > n) { Entity e = l.removeLast(); if (e != null) e.remove(); }
            Location base = p.getLocation();
            for (int i = 0; i < n; i++) {
                Exemplaire x = eq.get(i);
                Pet pet = x.pet;
                double a = tick * (Math.PI * 2 / 160.0) + i * Math.PI * 2 / n;
                double y = 0.9 + Math.sin((tick + i * 13) * 0.08) * 0.15;
                Location loc = base.clone().add(Math.cos(a) * 1.5, y, Math.sin(a) * 1.5);
                loc.setYaw((float) Math.toDegrees(a));   // regarde dans le sens de la rotation
                loc.setPitch(0);
                Entity e = l.get(i);
                if (e == null || !e.isValid() || !e.getWorld().equals(p.getWorld()) || e.getLocation().distanceSquared(loc) > 64) {
                    if (e != null) e.remove();
                    e = creer(x, loc);
                    l.set(i, e);
                } else {
                    e.teleport(loc);
                }
                if (pet.rarete != Rarete.COMMUN && tick % 10 == 0) {
                    Particle part = switch (pet.rarete) {
                        case RARE -> Particle.GLOW;
                        case EPIQUE -> Particle.WITCH;
                        default -> Particle.END_ROD;
                    };
                    p.getWorld().spawnParticle(part, loc.clone().add(0, 0.3, 0), 1, 0.15, 0.15, 0.15, 0);
                }
            }
        }
    }

    private Entity creer(Exemplaire x, Location loc) {
        Pet pet = x.pet;
        Class<? extends Entity> classe = pet.espece.type.getEntityClass();
        if (classe == null) return null;
        return loc.getWorld().spawn(loc, classe, en -> {
            en.addScoreboardTag(TAG);
            en.setPersistent(false);
            en.setInvulnerable(true);
            en.setSilent(true);
            en.setGravity(false);
            en.customName(nomEntite(x));
            en.setCustomNameVisible(true);
            if (en instanceof LivingEntity le) {
                le.setAI(false);
                le.setCollidable(false);
                le.setRemoveWhenFarAway(false);
                le.setCanPickupItems(false);
                AttributeInstance sc = le.getAttribute(Attribute.SCALE);
                if (sc != null) sc.setBaseValue(pet.espece.echelle);
            }
            if (en instanceof Bat b) b.setAwake(true);
            if (en instanceof Slime s) s.setSize(1);
        });
    }

    // Protection : on ne frappe, ne vise, ne brûle, ne renomme, n'attache ni ne pêche un pet.

    private static boolean estPet(Entity e) { return e != null && e.getScoreboardTags().contains(TAG); }

    @EventHandler(priority = EventPriority.LOWEST)
    public void degats(EntityDamageEvent e) { if (estPet(e.getEntity())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.LOWEST)
    public void cible(EntityTargetEvent e) { if (estPet(e.getEntity()) || estPet(e.getTarget())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.LOWEST)
    public void feu(EntityCombustEvent e) { if (estPet(e.getEntity())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.LOWEST)
    public void interaction(PlayerInteractEntityEvent e) { if (estPet(e.getRightClicked())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.LOWEST)
    public void interactionA(PlayerInteractAtEntityEvent e) { if (estPet(e.getRightClicked())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.LOWEST)
    public void peche(PlayerFishEvent e) {
        if (estPet(e.getCaught())) { e.setCancelled(true); }
    }

    @EventHandler
    public void connexion(PlayerJoinEvent e) { retirerEntites(e.getPlayer().getUniqueId()); }

    @EventHandler
    public void deconnexion(PlayerQuitEvent e) { retirerEntites(e.getPlayer().getUniqueId()); }

    // ------------------------------------------------------------------ données (pets.yml)

    private File fichier() { return new File(plugin.getDataFolder(), "pets.yml"); }

    private void charger() {
        collections.clear();
        ancien = false;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(fichier());
        for (String cle : y.getKeys(false)) {
            UUID id;
            try { id = UUID.fromString(cle); } catch (IllegalArgumentException ex) { continue; }
            ConfigurationSection s = y.getConfigurationSection(cle);
            if (s == null) continue;
            Collection c = new Collection();
            ConfigurationSection pets = s.getConfigurationSection("pets");
            if (pets != null)
                for (String uid : pets.getKeys(false)) {
                    Pet pet = Pet.parse(pets.getString(uid + ".pet", ""));
                    if (pet != null)
                        c.pets.add(new Exemplaire(uid, pet, Math.max(0, pets.getInt(uid + ".niveau")), Math.max(0, pets.getLong(uid + ".xp"))));
                }
            // Ancien format (avant les niveaux, 24/11/2026) : nombre par pet → autant d'exemplaires niveau 0.
            ConfigurationSection pos = s.getConfigurationSection("possedes");
            List<String> anciensEquipes = new ArrayList<>();
            if (pos != null) {
                ancien = true;
                for (String k : pos.getKeys(false)) {
                    Pet pet = Pet.parse(k);
                    if (pet == null) continue;
                    for (int i = 0; i < pos.getInt(k); i++) c.pets.add(new Exemplaire(nouvelUid(), pet, 0, 0));
                }
                anciensEquipes.addAll(s.getStringList("equipes"));
            }
            for (String k : pos != null ? anciensEquipes : s.getStringList("equipes")) {
                if (c.equipes.size() >= MAX_EQUIPES) break;
                Exemplaire x = c.get(k);
                if (x == null && pos != null)   // ancien format : l'id était « espece_rarete »
                    x = c.pets.stream().filter(e -> e.pet.id().equals(k) && !c.equipes.contains(e.uid)).findFirst().orElse(null);
                if (x != null && !c.equipes.contains(x.uid)) c.equipes.add(x.uid);
            }
            collections.put(id, c);
        }
        if (ancien) {
            try {
                java.nio.file.Files.copy(fichier().toPath(), new File(plugin.getDataFolder(), "pets.yml.avant-niveaux").toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                plugin.getLogger().warning("pets.yml : copie de l'ancien format impossible : " + ex.getMessage());
            }
            plugin.getLogger().info("pets.yml converti au format à niveaux (copie : pets.yml.avant-niveaux).");
            sauver();
        }
    }

    private boolean ancien;

    private void sauver() {
        modifie = false;
        YamlConfiguration y = new YamlConfiguration();
        for (var en : collections.entrySet()) {
            Collection c = en.getValue();
            if (c.pets.isEmpty()) continue;
            String k = en.getKey().toString();
            for (Exemplaire x : c.pets) {
                y.set(k + ".pets." + x.uid + ".pet", x.pet.id());
                y.set(k + ".pets." + x.uid + ".niveau", x.niveau);
                y.set(k + ".pets." + x.uid + ".xp", x.xp);
            }
            y.set(k + ".equipes", new ArrayList<>(c.equipes));
        }
        try {
            y.save(fichier());
        } catch (IOException ex) {
            plugin.getLogger().severe("pets.yml : sauvegarde impossible : " + ex.getMessage());
        }
    }

    /** Commande admin (tests) : met les pets équipés du joueur au niveau donné. */
    int definirNiveau(Player cible, int niveau) {
        Collection c = collections.get(cible.getUniqueId());
        if (c == null) return 0;
        List<Exemplaire> eq = c.equipes();
        for (Exemplaire x : eq) { x.niveau = Math.max(0, Math.min(niveau, niveauMax())); x.xp = 0; }
        sauver();
        retirerEntites(cible.getUniqueId());
        return eq.size();
    }

    /** Commande admin : donne un pet (id « espece_rarete », ex. tatou_legendaire). */
    boolean donner(Player cible, String id) {
        Pet pet = Pet.parse(id.toLowerCase(Locale.ROOT));
        if (pet == null) return false;
        ajouter(cible, pet);
        OrbitTycoon.msg(cible, "<gray>Tu reçois le pet " + nomComplet(pet) + "<gray> !");
        return true;
    }

    static List<String> ids() {
        List<String> l = new ArrayList<>();
        for (Espece e : Espece.values()) for (Rarete r : Rarete.values()) l.add(new Pet(e, r).id());
        return l;
    }
}
