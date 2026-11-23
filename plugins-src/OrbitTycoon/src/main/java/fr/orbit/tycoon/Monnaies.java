package fr.orbit.tycoon;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
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
        y.set("ile.nom", iles.get(id));
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
            if (Bukkit.getPlayer(id) == null) { soldes.remove(id); prestiges.remove(id); iles.remove(id); }
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
    void recompenser(Player p, String cle, int quantite) {
        var cfg = plugin.getConfig();
        StringBuilder texte = new StringBuilder();
        for (Monnaie m : new Monnaie[]{Monnaie.FLUX, Monnaie.CREDIT}) {
            String section = m == Monnaie.FLUX ? "flux-stellaire" : "credit-orbital";
            long gain = (long) cfg.getInt(section + ".ressources." + cle, cfg.getInt(section + ".defaut", 1)) * quantite;
            if (gain <= 0) continue;
            ajouter(p.getUniqueId(), m, gain);
            if (!texte.isEmpty()) texte.append("   ");
            texte.append("<").append(m.couleur).append(">+").append(gain).append(" ").append(m.symbole).append(" ")
                    .append(gain > 1 ? m.pluriel : m.nom).append("</").append(m.couleur).append(">");
        }
        if (!texte.isEmpty()) p.sendActionBar(MiniMessage.miniMessage().deserialize(texte.toString()));
    }
}
