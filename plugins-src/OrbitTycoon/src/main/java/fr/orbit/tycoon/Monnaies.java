package fr.orbit.tycoon;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Les trois monnaies du Tycoon, gardées en mémoire et sauvées dans plugins/OrbitTycoon/joueurs/<uuid>.yml.
 * - Crédit orbital : la monnaie principale.
 * - Flux stellaire : gagné en cassant les ressources des zones (les blocs ne lâchent plus rien).
 * - Plasma : monnaie payante de la boutique (cosmétiques seulement, aucun pay-to-win).
 */
final class Monnaies implements Listener {

    enum Monnaie {
        CREDIT("credit", "Crédit orbital", "Crédits orbitaux", "#E0B864", "⛃"),
        FLUX("flux", "Flux stellaire", "Flux stellaire", "#62CFF4", "✦"),
        PLASMA("plasma", "Plasma", "Plasma", "#C77DFF", "◆");

        final String id, nom, pluriel, couleur, symbole;

        Monnaie(String id, String nom, String pluriel, String couleur, String symbole) {
            this.id = id; this.nom = nom; this.pluriel = pluriel; this.couleur = couleur; this.symbole = symbole;
        }

        static Monnaie parse(String s) {
            for (Monnaie m : values()) if (m.id.equalsIgnoreCase(s) || m.name().equalsIgnoreCase(s)) return m;
            return null;
        }

        /** Texte MiniMessage du montant, ex. « 12 ✦ Flux stellaire ». */
        String format(long montant) {
            return "<" + couleur + ">" + montant + " " + symbole + " " + (montant > 1 ? pluriel : nom) + "</" + couleur + ">";
        }
    }

    private final OrbitTycoon plugin;
    private final Map<UUID, EnumMap<Monnaie, Long>> soldes = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> modifies = new ConcurrentHashMap<>();
    /** Prestige et nom d'île, rangés dans le même fichier joueur (affichés par le HUD). */
    private final Map<UUID, Integer> prestiges = new ConcurrentHashMap<>();
    private final Map<UUID, String> iles = new ConcurrentHashMap<>();
    /** Prestige de chaque outil (pioche, hache, houe, canne, épée) : sert aux /classements par outil. */
    private final Map<UUID, EnumMap<Outil.Mode, Integer>> prestigesOutils = new ConcurrentHashMap<>();
    /** Niveau et XP de chaque outil : {niveau, xp dans le niveau}. */
    private final Map<UUID, EnumMap<Outil.Mode, long[]>> niveaux = new ConcurrentHashMap<>();
    /** Ressources récoltées avec chaque outil (blocs, poissons, monstres) : affichées par le HUD. */
    private final Map<UUID, EnumMap<Outil.Mode, Long>> recoltes = new ConcurrentHashMap<>();
    /** Générateurs de l'île : {déjà posés, maximum posable}. */
    private final Map<UUID, int[]> generateurs = new ConcurrentHashMap<>();
    /** Quêtes de prestige récupérées, par outil (paliers 10, 20…). */
    private final Map<UUID, EnumMap<Outil.Mode, java.util.Set<Integer>>> quetes = new ConcurrentHashMap<>();
    /** Ressource choisie par outil (index dans la liste ressources.<outil> de config.yml ; 0 = origine). */
    private final Map<UUID, EnumMap<Outil.Mode, Integer>> ressources = new ConcurrentHashMap<>();
    private BukkitTask sauvegardeAuto;

    Monnaies(OrbitTycoon plugin) {
        this.plugin = plugin;
    }

    void demarrer() {
        for (Player p : Bukkit.getOnlinePlayers()) charger(p.getUniqueId());
        // Sauvegarde toutes les 5 minutes, hors du thread principal.
        sauvegardeAuto = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::sauverModifies, 6000L, 6000L);
    }

    /** À l'arrêt : tout est écrit tout de suite (thread principal, pour ne rien perdre). */
    void arreter() {
        if (sauvegardeAuto != null) sauvegardeAuto.cancel();
        for (UUID id : soldes.keySet()) sauver(id);
    }

    // ------------------------------------------------------------------ API

    long solde(UUID id, Monnaie m) {
        return soldes.computeIfAbsent(id, this::lire).getOrDefault(m, 0L);
    }

    void ajouter(UUID id, Monnaie m, long montant) {
        definir(id, m, solde(id, m) + montant);
    }

    /** Retire si le joueur a assez ; renvoie false sinon (rien n'est retiré). */
    boolean retirer(UUID id, Monnaie m, long montant) {
        long s = solde(id, m);
        if (montant < 0 || s < montant) return false;
        definir(id, m, s - montant);
        return true;
    }

    void definir(UUID id, Monnaie m, long montant) {
        soldes.computeIfAbsent(id, this::lire).put(m, Math.max(0, montant));
        modifies.put(id, true);
    }

    int prestige(UUID id) {
        soldes.computeIfAbsent(id, this::lire);
        return prestiges.getOrDefault(id, 0);
    }

    void definirPrestige(UUID id, int niveau) {
        soldes.computeIfAbsent(id, this::lire);
        prestiges.put(id, Math.max(0, niveau));
        modifies.put(id, true);
    }

    int prestige(UUID id, Outil.Mode outil) {
        soldes.computeIfAbsent(id, this::lire);
        EnumMap<Outil.Mode, Integer> m = prestigesOutils.get(id);
        return m == null ? 0 : m.getOrDefault(outil, 0);
    }

    void definirPrestige(UUID id, Outil.Mode outil, int niveau) {
        soldes.computeIfAbsent(id, this::lire);
        prestigesOutils.computeIfAbsent(id, k -> new EnumMap<>(Outil.Mode.class)).put(outil, Math.max(0, niveau));
        modifies.put(id, true);
    }

    /** Nom de l'île du joueur, ou null s'il n'en a pas encore. */
    String ile(UUID id) {
        soldes.computeIfAbsent(id, this::lire);
        return iles.get(id);
    }

    void definirIle(UUID id, String nom) {
        soldes.computeIfAbsent(id, this::lire);
        if (nom == null || nom.isBlank()) iles.remove(id); else iles.put(id, nom);
        modifies.put(id, true);
    }

    // ------------------------------------------------------------------ niveaux, XP et quêtes des outils

    /** Niveau de l'outil (1 au départ). */
    int niveau(UUID id, Outil.Mode outil) {
        soldes.computeIfAbsent(id, this::lire);
        EnumMap<Outil.Mode, long[]> m = niveaux.get(id);
        long[] v = m == null ? null : m.get(outil);
        return v == null ? 1 : (int) v[0];
    }

    /** XP accumulée dans le niveau actuel de l'outil. */
    long xp(UUID id, Outil.Mode outil) {
        soldes.computeIfAbsent(id, this::lire);
        EnumMap<Outil.Mode, long[]> m = niveaux.get(id);
        long[] v = m == null ? null : m.get(outil);
        return v == null ? 0 : v[1];
    }

    void definirNiveau(UUID id, Outil.Mode outil, int niveau, long xp) {
        soldes.computeIfAbsent(id, this::lire);
        niveaux.computeIfAbsent(id, k -> new EnumMap<>(Outil.Mode.class)).put(outil, new long[]{Math.max(1, niveau), Math.max(0, xp)});
        modifies.put(id, true);
    }

    /** Nombre de ressources récoltées avec cet outil depuis le début. */
    long recoltes(UUID id, Outil.Mode outil) {
        soldes.computeIfAbsent(id, this::lire);
        EnumMap<Outil.Mode, Long> m = recoltes.get(id);
        return m == null ? 0 : m.getOrDefault(outil, 0L);
    }

    void ajouterRecoltes(UUID id, Outil.Mode outil, long quantite) {
        if (quantite <= 0) return;
        soldes.computeIfAbsent(id, this::lire);
        recoltes.computeIfAbsent(id, k -> new EnumMap<>(Outil.Mode.class)).merge(outil, quantite, Long::sum);
        modifies.put(id, true);
    }

    // ------------------------------------------------------------------ générateurs

    private int generateursDepart() {
        return Math.max(0, plugin.getConfig().getInt("generateurs.depart", 10));
    }

    private int[] gen(UUID id) {
        soldes.computeIfAbsent(id, this::lire);
        return generateurs.computeIfAbsent(id, k -> new int[]{0, generateursDepart()});
    }

    /** Générateurs déjà posés sur l'île du joueur. */
    int generateursPoses(UUID id) { return gen(id)[0]; }

    /** Nombre maximum de générateurs que le joueur peut poser (10 au départ, config generateurs.depart). */
    int generateursMax(UUID id) { return gen(id)[1]; }

    void definirGenerateursPoses(UUID id, int poses) {
        gen(id)[0] = Math.max(0, poses);
        modifies.put(id, true);
    }

    void definirGenerateursMax(UUID id, int max) {
        gen(id)[1] = Math.max(0, max);
        modifies.put(id, true);
    }

    /** Paliers de quêtes de prestige déjà récupérés pour cet outil. */
    java.util.Set<Integer> quetes(UUID id, Outil.Mode outil) {
        soldes.computeIfAbsent(id, this::lire);
        EnumMap<Outil.Mode, java.util.Set<Integer>> m = quetes.get(id);
        java.util.Set<Integer> s = m == null ? null : m.get(outil);
        return s == null ? java.util.Set.of() : java.util.Collections.unmodifiableSet(s);
    }

    void marquerQuete(UUID id, Outil.Mode outil, int palier) {
        soldes.computeIfAbsent(id, this::lire);
        quetes.computeIfAbsent(id, k -> new EnumMap<>(Outil.Mode.class))
                .computeIfAbsent(outil, k -> new java.util.TreeSet<>()).add(palier);
        modifies.put(id, true);
    }

    /** Test : remplace les quêtes récupérées de cet outil (sans donner de récompense). */
    void definirQuetes(UUID id, Outil.Mode outil, java.util.Collection<Integer> paliers) {
        soldes.computeIfAbsent(id, this::lire);
        quetes.computeIfAbsent(id, k -> new EnumMap<>(Outil.Mode.class)).put(outil, new java.util.TreeSet<>(paliers));
        modifies.put(id, true);
    }

    /** Ressource choisie pour cet outil (0 = origine). */
    int ressource(UUID id, Outil.Mode outil) {
        soldes.computeIfAbsent(id, this::lire);
        EnumMap<Outil.Mode, Integer> m = ressources.get(id);
        return m == null ? 0 : m.getOrDefault(outil, 0);
    }

    void definirRessource(UUID id, Outil.Mode outil, int index) {
        soldes.computeIfAbsent(id, this::lire);
        ressources.computeIfAbsent(id, k -> new EnumMap<>(Outil.Mode.class)).put(outil, Math.max(0, index));
        modifies.put(id, true);
    }

    /** Crédits orbitaux de tous les joueurs (fichiers + mémoire) : sert au classement global. Hors thread principal. */
    Map<UUID, Long> tousLesCredits() {
        Map<UUID, Long> tout = new java.util.HashMap<>();
        File[] fichiers = new File(plugin.getDataFolder(), "joueurs").listFiles((d, n) -> n.endsWith(".yml"));
        if (fichiers != null) for (File f : fichiers) {
            try {
                UUID id = UUID.fromString(f.getName().substring(0, f.getName().length() - 4));
                tout.put(id, YamlConfiguration.loadConfiguration(f).getLong("monnaies." + Monnaie.CREDIT.id, 0));
            } catch (IllegalArgumentException ignore) { }
        }
        soldes.forEach((id, map) -> tout.put(id, map.getOrDefault(Monnaie.CREDIT, 0L)));
        return tout;
    }

    /** Prestige de tous les joueurs (fichiers + mémoire) : sert au classement prestige. Hors thread principal. */
    Map<UUID, Integer> tousLesPrestiges() {
        Map<UUID, Integer> tout = new java.util.HashMap<>();
        File[] fichiers = new File(plugin.getDataFolder(), "joueurs").listFiles((d, n) -> n.endsWith(".yml"));
        if (fichiers != null) for (File f : fichiers) {
            try {
                UUID id = UUID.fromString(f.getName().substring(0, f.getName().length() - 4));
                tout.put(id, YamlConfiguration.loadConfiguration(f).getInt("prestige", 0));
            } catch (IllegalArgumentException ignore) { }
        }
        tout.putAll(prestiges);
        return tout;
    }

    /** Prestige d'un outil pour tous les joueurs (fichiers + mémoire) : sert à /classements. Hors thread principal. */
    Map<UUID, Integer> tousLesPrestiges(Outil.Mode outil) {
        Map<UUID, Integer> tout = new java.util.HashMap<>();
        File[] fichiers = new File(plugin.getDataFolder(), "joueurs").listFiles((d, n) -> n.endsWith(".yml"));
        if (fichiers != null) for (File f : fichiers) {
            try {
                UUID id = UUID.fromString(f.getName().substring(0, f.getName().length() - 4));
                tout.put(id, YamlConfiguration.loadConfiguration(f).getInt("prestige-outils." + outil.id, 0));
            } catch (IllegalArgumentException ignore) { }
        }
        prestigesOutils.forEach((id, m) -> tout.put(id, m.getOrDefault(outil, 0)));
        return tout;
    }

    // ------------------------------------------------------------------ fichiers

    private File fichier(UUID id) {
        return new File(new File(plugin.getDataFolder(), "joueurs"), id + ".yml");
    }

    private EnumMap<Monnaie, Long> lire(UUID id) {
        EnumMap<Monnaie, Long> map = new EnumMap<>(Monnaie.class);
        File f = fichier(id);
        if (f.exists()) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            for (Monnaie m : Monnaie.values()) map.put(m, y.getLong("monnaies." + m.id, 0));
            prestiges.put(id, y.getInt("prestige", 0));
            EnumMap<Outil.Mode, Integer> po = new EnumMap<>(Outil.Mode.class);
            for (Outil.Mode o : Outil.Mode.values()) po.put(o, y.getInt("prestige-outils." + o.id, 0));
            prestigesOutils.put(id, po);
            EnumMap<Outil.Mode, long[]> nv = new EnumMap<>(Outil.Mode.class);
            EnumMap<Outil.Mode, java.util.Set<Integer>> qu = new EnumMap<>(Outil.Mode.class);
            EnumMap<Outil.Mode, Long> rc = new EnumMap<>(Outil.Mode.class);
            for (Outil.Mode o : Outil.Mode.values()) {
                nv.put(o, new long[]{Math.max(1, y.getInt("outils." + o.id + ".niveau", 1)), Math.max(0, y.getLong("outils." + o.id + ".xp", 0))});
                rc.put(o, Math.max(0, y.getLong("outils." + o.id + ".recoltes", 0)));
                qu.put(o, new java.util.TreeSet<>(y.getIntegerList("quetes." + o.id)));
            }
            EnumMap<Outil.Mode, Integer> rs = new EnumMap<>(Outil.Mode.class);
            for (Outil.Mode o : Outil.Mode.values()) rs.put(o, Math.max(0, y.getInt("ressources." + o.id, 0)));
            ressources.put(id, rs);
            niveaux.put(id, nv);
            quetes.put(id, qu);
            recoltes.put(id, rc);
            generateurs.put(id, new int[]{Math.max(0, y.getInt("generateurs.poses", 0)),
                    Math.max(0, y.getInt("generateurs.max", generateursDepart()))});
            String ile = y.getString("ile.nom");
            if (ile != null && !ile.isBlank()) iles.put(id, ile);
        }
        return map;
    }

    private void charger(UUID id) {
        soldes.computeIfAbsent(id, this::lire);
    }

    private synchronized void sauver(UUID id) {
        EnumMap<Monnaie, Long> map = soldes.get(id);
        if (map == null) return;
        File f = fichier(id);
        f.getParentFile().mkdirs();
        YamlConfiguration y = f.exists() ? YamlConfiguration.loadConfiguration(f) : new YamlConfiguration();
        for (Monnaie m : Monnaie.values()) y.set("monnaies." + m.id, map.getOrDefault(m, 0L));
        y.set("prestige", prestiges.getOrDefault(id, 0));
        EnumMap<Outil.Mode, Integer> po = prestigesOutils.get(id);
        for (Outil.Mode o : Outil.Mode.values()) y.set("prestige-outils." + o.id, po == null ? 0 : po.getOrDefault(o, 0));
        EnumMap<Outil.Mode, long[]> nv = niveaux.get(id);
        EnumMap<Outil.Mode, java.util.Set<Integer>> qu = quetes.get(id);
        EnumMap<Outil.Mode, Long> rc = recoltes.get(id);
        for (Outil.Mode o : Outil.Mode.values()) {
            long[] v = nv == null ? null : nv.get(o);
            y.set("outils." + o.id + ".niveau", v == null ? 1 : v[0]);
            y.set("outils." + o.id + ".xp", v == null ? 0 : v[1]);
            y.set("outils." + o.id + ".recoltes", rc == null ? 0L : rc.getOrDefault(o, 0L));
            java.util.Set<Integer> s = qu == null ? null : qu.get(o);
            y.set("quetes." + o.id, s == null ? java.util.List.of() : new java.util.ArrayList<>(s));
            EnumMap<Outil.Mode, Integer> rs = ressources.get(id);
            y.set("ressources." + o.id, rs == null ? 0 : rs.getOrDefault(o, 0));
        }
        y.set("ile.nom", iles.get(id));
        int[] g = generateurs.get(id);
        y.set("generateurs.poses", g == null ? 0 : g[0]);
        y.set("generateurs.max", g == null ? generateursDepart() : g[1]);
        try {
            y.save(f);
            modifies.remove(id);
        } catch (IOException e) {
            plugin.getLogger().severe("Impossible de sauver les monnaies de " + id + " : " + e);
        }
    }

    private void sauverModifies() {
        for (UUID id : modifies.keySet()) sauver(id);
    }

    @EventHandler
    public void connexion(PlayerJoinEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> charger(id));
    }

    @EventHandler
    public void deconnexion(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            sauver(id);
            if (Bukkit.getPlayer(id) == null) { soldes.remove(id); prestiges.remove(id); prestigesOutils.remove(id); iles.remove(id); niveaux.remove(id); quetes.remove(id); ressources.remove(id); recoltes.remove(id); generateurs.remove(id); }
        });
    }

    // ------------------------------------------------------------------ affichage

    void afficherSolde(org.bukkit.command.CommandSender s, UUID id, String nom) {
        OrbitTycoon.msg(s, "Porte-monnaie" + (nom != null ? " de <#E0B864>" + nom + "</#E0B864>" : "") + " :");
        for (Monnaie m : Monnaie.values())
            s.sendMessage(MiniMessage.miniMessage().deserialize("  <dark_gray>•</dark_gray> " + m.format(solde(id, m))));
    }

    /**
     * Récompense d'une ressource récoltée : Flux stellaire + Crédits orbitaux, lus dans config.yml
     * (sections flux-stellaire / credit-orbital, clé = nom du bloc, du poisson ou du mob), puis affichés en barre d'action.
     */
    void recompenser(Player p, Outil.Mode outil, String cle, int quantite) {
        // Ressource choisie dans le panneau de l'outil (pêche, mobzone) : elle remplace les gains d'origine.
        Ressources.Palier choisie = outil == null ? null : plugin.ressources().choisie(p.getUniqueId(), outil);
        if (choisie != null && (choisie.origine() || !Ressources.convertible(outil, cle))) choisie = null;
        recompenser(p, outil, cle, quantite, choisie);
    }

    /** Idem, avec la ressource dont le joueur touche les gains ({@code choisie} null = barème de {@code cle}). */
    void recompenser(Player p, Outil.Mode outil, String cle, int quantite, Ressources.Palier choisie) {
        var cfg = plugin.getConfig();
        StringBuilder texte = new StringBuilder();
        if (choisie != null) texte.append("<white>").append(choisie.nom()).append(" <dark_gray>· ");
        int debut = texte.length();
        // Enchantements de l'Outil orbital (achetés au Noyau Orbital, plafonnés par le niveau du Noyau de l'île).
        // Pets équipés pour cet outil (Pets.java) : Fortune, XP, Money et Proc, en %.
        UUID uid = p.getUniqueId();
        Pets pets = plugin.pets();
        double petXp = pets == null ? 0 : pets.bonus(uid, outil, Pets.Stat.XP);
        double petMoney = pets == null ? 0 : pets.bonus(uid, outil, Pets.Stat.MONEY);
        double petProc = pets == null ? 0 : pets.bonus(uid, outil, Pets.Stat.PROC);
        if (pets != null && outil != null) quantite = pets.fortune(uid, outil, quantite);
        // Équipements de farm portés (Equipements.java) : même % sur ressources, monnaie et XP.
        double equip = plugin.equipements() == null ? 0 : plugin.equipements().bonus(p, outil);
        quantite = Equipements.appliquer(quantite, equip);
        // Armures premium (boutique) : % de Crédits, Flux et XP avec tous les outils (pas de ressources en plus).
        double premium = outil == null || plugin.equipements() == null ? 0 : plugin.equipements().bonusPremium(p);
        // Grade (Grades.java) : % de Crédits, Flux et XP d'outil.
        double grade = outil == null || plugin.grades() == null ? 0 : plugin.grades().bonus(p);
        double multEquip = (1 + equip / 100.0) * (1 + premium / 100.0) * (1 + grade / 100.0);
        // Boosters orbitaux actifs (lots des coffres, Boosters.java).
        Boosters bst = plugin.boosters();
        double bCredit = bst == null ? 1 : bst.mult(uid, Boosters.Sorte.CREDITS);
        double bFlux = bst == null ? 1 : bst.mult(uid, Boosters.Sorte.FLUX);
        double bXp = bst == null ? 1 : bst.mult(uid, Boosters.Sorte.XP);
        Noyau.Bonus bonus = outil == null ? Noyau.Bonus.AUCUN : plugin.noyau().bonus(p, 1 + petProc / 100.0);
        String extra = null;
        if (bonus.rafale()) { quantite *= 2; extra = "<#62CFF4>✦ Rafale ×2"; }
        boolean meteore = bonus.meteore();
        if (meteore) extra = "<#E0B864><bold>☄ Météore ×" + bonus.multMeteore() + "</bold>";
        for (Monnaie m : new Monnaie[]{Monnaie.FLUX, Monnaie.CREDIT}) {
            String section = m == Monnaie.FLUX ? "flux-stellaire" : "credit-orbital";
            long gain = choisie != null ? (m == Monnaie.FLUX ? choisie.flux() : choisie.credit()) * quantite
                    : (long) cfg.getInt(section + ".ressources." + cle, cfg.getInt(section + ".defaut", 1)) * quantite;
            if (gain <= 0) continue;
            if (m == Monnaie.CREDIT) {
                gain = Math.round(gain * bonus.multCredit() * (1 + petMoney / 100.0) * multEquip * bCredit);
                if (meteore) gain *= bonus.multMeteore();
            } else gain = Math.round(gain * bonus.multFlux() * multEquip * bFlux);
            ajouter(p.getUniqueId(), m, gain);
            if (texte.length() > debut) texte.append("   ");
            texte.append("<").append(m.couleur).append(">+").append(gain).append(" ").append(m.symbole).append(" ")
                    .append(gain > 1 ? m.pluriel : m.nom).append("</").append(m.couleur).append(">");
        }
        if (outil != null) {
            ajouterRecoltes(p.getUniqueId(), outil, quantite);
            if (pets != null) pets.gagnerXp(p, quantite);
            long xpBase = choisie != null ? choisie.xp() * quantite
                    : (long) cfg.getInt("progression.xp.ressources." + cle, cfg.getInt("progression.xp.defaut", 1)) * quantite;
            String xp = plugin.progression().gagner(p, outil, Math.round(xpBase * bonus.multXp() * (1 + petXp / 100.0) * multEquip * bXp));
            if (xp != null) {
                if (texte.length() > debut) texte.append("   ");
                texte.append(xp);
            }
            if (extra != null) texte.append("   ").append(extra);
            if (meteore) p.playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 0.7f, 1.4f);
        }
        if (!texte.isEmpty()) p.sendActionBar(MiniMessage.miniMessage().deserialize(texte.toString()));
        plugin.tuto().recolte(p, quantite);
        cleFarm(p, outil);
    }

    /**
     * Clé Farm : petite chance (coffres.cle-farm.chance, en %) à chaque récolte faite avec l'Outil orbital en main
     * (pioche, hache, houe, canne, épée). Donnée directement dans l'inventaire (au sol seulement s'il est plein).
     */
    private void cleFarm(Player p, Outil.Mode outil) {
        if (outil == null || plugin.coffres() == null || plugin.outil() == null) return;
        if (!plugin.outil().est(p.getInventory().getItemInMainHand())) return;
        double chance = plugin.getConfig().getDouble("coffres.cle-farm.chance", 2.0);
        if (chance <= 0 || java.util.concurrent.ThreadLocalRandom.current().nextDouble(100) >= chance) return;
        int n = Math.max(0, Math.min(Coffres.NB - 1, plugin.getConfig().getInt("coffres.cle-farm.coffre", 2) - 1));
        for (var reste : p.getInventory().addItem(plugin.coffres().cle(n, 1)).values())
            p.getWorld().dropItemNaturally(p.getLocation(), reste);
        OrbitTycoon.msg(p, "<gray>Tu as trouvé une <#E0B864><bold>Clé " + MiniMessage.miniMessage().escapeTags(plugin.coffres().nom(n))
                + "</bold></#E0B864><gray> en farmant !");
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f);
    }
}
