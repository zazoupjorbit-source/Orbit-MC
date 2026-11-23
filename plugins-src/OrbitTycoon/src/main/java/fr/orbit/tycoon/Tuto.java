package fr.orbit.tycoon;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Tutoriel guidé du Tycoon, lancé à la première connexion.
 * Une bossbar affiche la consigne ; chaque étape se valide quand le joueur fait l'action demandée
 * (les autres classes appellent {@link #fait}, {@link #recolte}, et les zones sont vérifiées chaque seconde).
 * Avancement dans plugins/OrbitTycoon/tuto.yml (joueurs.<uuid>.etape / zones / recoltes).
 */
final class Tuto implements Listener {

    enum Etape {
        BIENVENUE(1, "Bienvenue sur Orbit Tycoon !"),
        SOLDE(2, "Tape <#E0B864>/solde</#E0B864> pour voir tes monnaies"),
        NAVIGATEUR(3, "Ouvre ton <#E0B864>Navigateur orbital</#E0B864> (clic droit ou /zones)"),
        OUTIL(4, "<#E0B864>Shift + clic droit</#E0B864> avec ton Outil orbital pour ouvrir son interface"),
        RECOLTE(5, "Récolte des ressources avec ton outil"),
        ZONES(6, "Découvre les 5 îles de farm"),
        COFFRE_APERCU(7, "Va aux coffres (<#E0B864>/coffres</#E0B864>) et fais un <#E0B864>clic gauche</#E0B864> sur l'un d'eux"),
        COFFRE_OUVRIR(7, "Fais un <#E0B864>clic droit</#E0B864> sur le bon coffre avec ta clé en main"),
        VOTE(8, "Tape <#E0B864>/vote</#E0B864> pour soutenir Orbit MC"),
        FINI(8, "Tuto terminé");

        final int numero;
        final String consigne;
        Etape(int numero, String consigne) { this.numero = numero; this.consigne = consigne; }
        Etape suivante() { return values()[Math.min(ordinal() + 1, values().length - 1)]; }
    }

    private static final int TOTAL = 8;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final OrbitTycoon plugin;
    private final Map<UUID, Etat> etats = new HashMap<>();
    private final Map<UUID, BossBar> barres = new HashMap<>();
    private BukkitTask tache;
    private boolean modifie;

    private static final class Etat {
        Etape etape = Etape.BIENVENUE;
        final Set<Zone> zones = EnumSet.noneOf(Zone.class);
        int recoltes;
        boolean recompense;   // récompense de fin déjà donnée
    }

    Tuto(OrbitTycoon plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ config

    private boolean actif() { return plugin.getConfig().getBoolean("tuto.actif", true); }
    private int recoltesDemandees() { return Math.max(1, plugin.getConfig().getInt("tuto.recoltes", 10)); }
    private int zonesDemandees() { return Math.min(Zone.farms().size(), Math.max(1, plugin.getConfig().getInt("tuto.zones", 5))); }
    private int numeroCle() { return Math.max(1, Math.min(5, plugin.getConfig().getInt("tuto.cle-coffre", 1))) - 1; }
    private long recompenseCredits() { return Math.max(0, plugin.getConfig().getLong("tuto.recompense-credits", 500)); }

    // ------------------------------------------------------------------ cycle de vie

    void demarrer() {
        charger();
        tache = Bukkit.getScheduler().runTaskTimer(plugin, this::verifierZones, 20, 20);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> { if (modifie) sauver(false); }, 1200, 1200);
        for (Player p : Bukkit.getOnlinePlayers()) afficher(p);
    }

    void arreter() {
        if (tache != null) tache.cancel();
        for (Map.Entry<UUID, BossBar> e : barres.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null) p.hideBossBar(e.getValue());
        }
        barres.clear();
        sauver(true);
    }

    private File fichier() { return new File(plugin.getDataFolder(), "tuto.yml"); }

    private void charger() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(fichier());
        ConfigurationSection s = y.getConfigurationSection("joueurs");
        if (s == null) return;
        for (String cle : s.getKeys(false)) {
            try {
                Etat e = new Etat();
                e.etape = Etape.valueOf(s.getString(cle + ".etape", "BIENVENUE"));
                e.recoltes = s.getInt(cle + ".recoltes", 0);
                e.recompense = s.getBoolean(cle + ".recompense", false);
                for (String z : s.getStringList(cle + ".zones")) {
                    try { e.zones.add(Zone.valueOf(z)); } catch (IllegalArgumentException ignore) { }
                }
                etats.put(UUID.fromString(cle), e);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("tuto.yml : entrée ignorée (" + cle + ")");
            }
        }
    }

    /** Sauvegarde : le texte est préparé sur le thread principal, l'écriture se fait à part (sauf à l'arrêt). */
    private void sauver(boolean bloquant) {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, Etat> e : etats.entrySet()) {
            String b = "joueurs." + e.getKey();
            y.set(b + ".etape", e.getValue().etape.name());
            y.set(b + ".recoltes", e.getValue().recoltes);
            y.set(b + ".recompense", e.getValue().recompense);
            y.set(b + ".zones", e.getValue().zones.stream().map(Enum::name).toList());
        }
        String texte = y.saveToString();
        modifie = false;
        Runnable ecrire = () -> {
            synchronized (this) {
                try {
                    plugin.getDataFolder().mkdirs();
                    Files.writeString(fichier().toPath(), texte, StandardCharsets.UTF_8);
                } catch (IOException ex) {
                    plugin.getLogger().severe("Impossible d'écrire tuto.yml : " + ex);
                }
            }
        };
        if (bloquant) ecrire.run();
        else Bukkit.getScheduler().runTaskAsynchronously(plugin, ecrire);
    }

    // ------------------------------------------------------------------ affichage

    private Etat etat(Player p) {
        return etats.computeIfAbsent(p.getUniqueId(), k -> { modifie = true; return new Etat(); });
    }

    private boolean enCours(Player p, Etape etape) {
        if (!actif()) return false;
        Etat e = etats.get(p.getUniqueId());
        return e != null && e.etape == etape;
    }

    private String consigne(Etat e) {
        return switch (e.etape) {
            case RECOLTE -> e.etape.consigne + " <white>(" + Math.min(e.recoltes, recoltesDemandees()) + "/" + recoltesDemandees() + ")";
            case ZONES -> "Découvre les îles de farm <white>(" + e.zones.size() + "/" + zonesDemandees() + ")";
            default -> e.etape.consigne;
        };
    }

    private float progression(Etat e) {
        return switch (e.etape) {
            case RECOLTE -> Math.min(1f, e.recoltes / (float) recoltesDemandees());
            case ZONES -> Math.min(1f, e.zones.size() / (float) zonesDemandees());
            default -> Math.min(1f, (e.etape.numero - 1) / (float) TOTAL);
        };
    }

    /** Met à jour (ou retire) la bossbar du joueur. */
    private void afficher(Player p) {
        Etat e = etats.get(p.getUniqueId());
        BossBar b = barres.get(p.getUniqueId());
        if (!actif() || e == null || e.etape == Etape.FINI) {
            if (b != null) { p.hideBossBar(b); barres.remove(p.getUniqueId()); }
            return;
        }
        Component titre = MM.deserialize("<#E0B864><bold>Tuto " + e.etape.numero + "/" + TOTAL + "</bold> <dark_gray>» <#62CFF4>" + consigne(e));
        if (b == null) {
            b = BossBar.bossBar(titre, progression(e), BossBar.Color.BLUE, BossBar.Overlay.NOTCHED_10);
            barres.put(p.getUniqueId(), b);
            p.showBossBar(b);
        } else {
            b.name(titre);
            b.progress(progression(e));
        }
    }

    /** Explications envoyées dans le chat au début de chaque étape. */
    private void expliquer(Player p, Etape etape) {
        List<String> lignes = switch (etape) {
            case BIENVENUE -> List.of(
                    "Ici, tu farmes des ressources pour gagner des <#E0B864>Crédits orbitaux</#E0B864> et du <#62CFF4>Flux stellaire</#62CFF4>.",
                    "Suis la barre en haut de l'écran : 8 petites étapes. <dark_gray>(/tuto pour revoir la consigne, /tuto passer pour sauter)");
            case SOLDE -> List.of("Tes monnaies : tape <click:suggest_command:'/solde'><#E0B864><u>/solde</u></#E0B864></click><gray>.");
            case NAVIGATEUR -> List.of(
                    "Le <#E0B864>Navigateur orbital</#E0B864> (dans ton inventaire) te téléporte vers les îles, ton île et le spawn.",
                    "Fais un clic droit avec, ou tape <#E0B864>/zones</#E0B864>.");
            case OUTIL -> List.of(
                    "Ton <#E0B864>Outil orbital</#E0B864> est à la fois pioche, hache, houe, canne à pêche et épée.",
                    "Accroupis-toi (<#E0B864>Shift</#E0B864>) et fais un <#E0B864>clic droit</#E0B864> pour ouvrir son interface et choisir un outil.",
                    "Raccourci : <#E0B864>Shift + F</#E0B864> passe directement à l'outil suivant.");
            case RECOLTE -> List.of(
                    "Va sur une île (blé, bois, poissons, minerai ou monstres) et récolte <white>" + recoltesDemandees() + "</white> ressources.",
                    "Pas d'objet à ramasser : chaque ressource te paie directement en monnaies !");
            case ZONES -> List.of(
                    "Visite les îles de farm : <#3FA9F5>Pêche</#3FA9F5>, <#C8894B>Bûcheron</#C8894B>, <#F5D442>Agriculture</#F5D442>, <#FF4D4D>Mobzone</#FF4D4D> et <#B0BEC5>Mine</#B0BEC5>.",
                    "Traverse les anneaux-portails du spawn ou utilise ton Navigateur.");
            case COFFRE_APERCU -> List.of(
                    "Au spawn, 5 coffres orbitaux contiennent des récompenses. Tape <click:suggest_command:'/coffres'><#E0B864><u>/coffres</u></#E0B864></click><gray> pour y aller.",
                    "Un <#E0B864>clic gauche</#E0B864> sur un coffre montre ce qu'il contient.");
            case COFFRE_OUVRIR -> List.of(
                    "Cadeau : voici une <#E0B864>clé</#E0B864> ! Prends-la en main et fais un <#E0B864>clic droit</#E0B864> sur le coffre <#E0B864>"
                            + MM.escapeTags(plugin.coffres().nom(numeroCle())) + "</#E0B864><gray>.");
            case VOTE -> List.of(
                    "Dernière étape : voter fait connaître Orbit MC et rapporte des récompenses.",
                    "Tape <click:suggest_command:'/vote'><#E0B864><u>/vote</u></#E0B864></click><gray> et clique sur les liens.");
            case FINI -> List.of();
        };
        for (String l : lignes) OrbitTycoon.msg(p, l);
    }

    // ------------------------------------------------------------------ avancement

    /** Le joueur vient de faire l'action de l'étape {@code etape} : on passe à la suivante. */
    void fait(Player p, Etape etape) {
        if (!enCours(p, etape)) return;
        avancer(p);
    }

    private void avancer(Player p) {
        Etat e = etat(p);
        if (e.etape == Etape.FINI) return;
        e.etape = e.etape.suivante();
        modifie = true;
        if (e.etape == Etape.FINI) { terminer(p); return; }
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
        p.showTitle(Title.title(MM.deserialize("<#E0B864>Étape " + e.etape.numero + "/" + TOTAL),
                MM.deserialize("<#62CFF4>" + e.etape.consigne),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(500))));
        if (e.etape == Etape.COFFRE_OUVRIR) {
            for (var reste : p.getInventory().addItem(plugin.coffres().cle(numeroCle(), 1)).values())
                p.getWorld().dropItemNaturally(p.getLocation(), reste);
        }
        expliquer(p, e.etape);
        afficher(p);
    }

    private void terminer(Player p) {
        Etat e = etat(p);
        long credits = e.recompense ? 0 : recompenseCredits();
        e.recompense = true;
        if (credits > 0) plugin.monnaies().ajouter(p.getUniqueId(), Monnaies.Monnaie.CREDIT, credits);
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1f);
        p.showTitle(Title.title(MM.deserialize("<gradient:#62CFF4:#E0B864><bold>Tuto terminé !</bold></gradient>"),
                MM.deserialize(credits > 0 ? "<#E0B864>+" + credits + " ⛃ Crédits orbitaux" : "<gray>Bon jeu sur Orbit Tycoon"),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(4), Duration.ofMillis(800))));
        OrbitTycoon.msg(p, "<#E0B864>Bravo, tu connais l'essentiel !<gray> Prochaine étape : crée ton île avec <#E0B864>/is create</#E0B864><gray>.");
        afficher(p);
    }

    /** Appelé à chaque ressource payée (Monnaies.recompenser). */
    void recolte(Player p, int quantite) {
        if (!enCours(p, Etape.RECOLTE)) return;
        Etat e = etats.get(p.getUniqueId());
        e.recoltes += Math.max(1, quantite);
        modifie = true;
        if (e.recoltes >= recoltesDemandees()) avancer(p);
        else afficher(p);
    }

    /** Chaque seconde : îles visitées par les joueurs à l'étape ZONES. */
    private void verifierZones() {
        if (!actif()) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            Etat e = etats.get(p.getUniqueId());
            if (e == null || e.etape != Etape.ZONES || p.getWorld() != plugin.monde()) continue;
            Zone z = Zone.at(p.getLocation());
            if (z == null || z == Zone.BASE || !e.zones.add(z)) continue;
            modifie = true;
            p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f);
            OrbitTycoon.msg(p, "Île découverte : " + z.nom + " <white>(" + e.zones.size() + "/" + zonesDemandees() + ")");
            if (e.zones.size() >= zonesDemandees()) avancer(p);
            else afficher(p);
        }
    }

    // ------------------------------------------------------------------ événements

    @EventHandler
    public void connexion(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        // Laisse passer les messages de bienvenue et la téléportation avant d'afficher le tuto.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline() || !actif()) return;
            boolean nouveau = !etats.containsKey(p.getUniqueId());
            Etat et = etat(p);
            if (et.etape == Etape.FINI) return;
            if (nouveau) {
                p.showTitle(Title.title(MM.deserialize("<gradient:#62CFF4:#E0B864><bold>Orbit Tycoon</bold></gradient>"),
                        MM.deserialize("<gray>Petit tuto pour bien démarrer"),
                        Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(700))));
                expliquer(p, Etape.BIENVENUE);
                afficher(p);
                // L'étape de bienvenue passe toute seule.
                Bukkit.getScheduler().runTaskLater(plugin, () -> { if (p.isOnline()) fait(p, Etape.BIENVENUE); }, 100);
            } else {
                if (et.etape == Etape.BIENVENUE) { fait(p, Etape.BIENVENUE); return; }
                OrbitTycoon.msg(p, "Tuto en cours <dark_gray>(" + et.etape.numero + "/" + TOTAL + ")<gray> :");
                expliquer(p, et.etape);
                afficher(p);
            }
        }, 40);
    }

    @EventHandler
    public void deconnexion(PlayerQuitEvent e) {
        barres.remove(e.getPlayer().getUniqueId());
    }

    /** /solde et /vote (commande d'OrbitDevCore) : on regarde ce que le joueur tape, avec les alias. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void commande(PlayerCommandPreprocessEvent e) {
        String cmd = e.getMessage().substring(1).split(" ", 2)[0].toLowerCase();
        int deuxPoints = cmd.indexOf(':');
        if (deuxPoints >= 0) cmd = cmd.substring(deuxPoints + 1);
        Player p = e.getPlayer();
        switch (cmd) {
            case "solde", "monnaie", "argent", "money", "balance" ->
                    Bukkit.getScheduler().runTaskLater(plugin, () -> fait(p, Etape.SOLDE), 20);
            case "vote", "voter", "votes" ->
                    Bukkit.getScheduler().runTaskLater(plugin, () -> fait(p, Etape.VOTE), 40);
            default -> { }
        }
    }

    // ------------------------------------------------------------------ commandes

    /** /tuto [passer|recommencer] */
    boolean commande(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) { OrbitTycoon.msg(s, "Commande réservée aux joueurs."); return true; }
        if (!actif()) { OrbitTycoon.msg(p, "Le tuto est désactivé."); return true; }
        String sous = args.length > 0 ? args[0].toLowerCase() : "";
        Etat e = etat(p);
        switch (sous) {
            case "passer" -> {
                if (e.etape == Etape.FINI) { OrbitTycoon.msg(p, "Tu as déjà terminé le tuto."); return true; }
                e.etape = Etape.FINI;
                modifie = true;
                afficher(p);
                OrbitTycoon.msg(p, "Tuto passé. <dark_gray>(/tuto recommencer pour le revoir)");
            }
            case "recommencer" -> {
                boolean garde = e.recompense;
                recommencer(p.getUniqueId());
                Etat n = etats.get(p.getUniqueId());
                n.recompense = garde;   // pas de deuxième récompense
                n.etape = Etape.SOLDE;
                OrbitTycoon.msg(p, "Tuto relancé.");
                expliquer(p, n.etape);
                afficher(p);
            }
            default -> {
                if (e.etape == Etape.FINI) {
                    OrbitTycoon.msg(p, "Tu as terminé le tuto. <dark_gray>(/tuto recommencer pour le revoir)");
                    return true;
                }
                if (e.etape == Etape.BIENVENUE) { fait(p, Etape.BIENVENUE); return true; }
                OrbitTycoon.msg(p, "<#E0B864>Tuto " + e.etape.numero + "/" + TOTAL + "<gray> : " + consigne(e));
                expliquer(p, e.etape);
                afficher(p);
            }
        }
        return true;
    }

    private void recommencer(UUID id) {
        etats.put(id, new Etat());
        modifie = true;
    }

    /** /tycoon tuto reset <joueur> : remet le tuto à zéro (la récompense pourra être regagnée). */
    void reset(CommandSender s, String nom) {
        Player cible = Bukkit.getPlayerExact(nom);
        UUID id = cible != null ? cible.getUniqueId() : Bukkit.getOfflinePlayer(nom).getUniqueId();
        recommencer(id);
        OrbitTycoon.msg(s, "Tuto de <white>" + nom + "</white><gray> remis à zéro.");
        if (cible != null) {
            BossBar b = barres.remove(id);
            if (b != null) cible.hideBossBar(b);
            expliquer(cible, Etape.BIENVENUE);
            afficher(cible);
            Bukkit.getScheduler().runTaskLater(plugin, () -> { if (cible.isOnline()) fait(cible, Etape.BIENVENUE); }, 100);
        }
    }
}
