package fr.orbit.tycoon;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Les 4 grades du Tycoon, achetés en Plasma à la boutique : bonus de gains (Crédits, Flux, XP d'outil, production
 * des générateurs : grades.<id>.bonus en %), confort (vol sur l'île, offres HDV, accès au serveur plein) et cosmétiques. Le grade est un groupe LuckPerms donné avec le contexte server=tycoon.
 * Grades achetés dans l ordre, chacun au prix plein. Réglages : grades.&lt;id&gt; dans config.yml.
 */
final class Grades implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    enum Grade {
        PILOTE("pilote", "Pilote", "<b><gradient:#9FB8D0:#D8F6FF>Pilote</gradient></b>", 1000, 5, 5, false, false, null),
        COMMANDANT("commandant", "Commandant", "<b><gradient:#3FA9F5:#62CFF4>Commandant</gradient></b>", 1500, 10, 10, true, false,
                Color.fromRGB(0x62CFF4)),
        AMIRAL("amiral", "Amiral", "<b><gradient:#E0B864:#FFF1A0>Amiral</gradient></b>", 2000, 15, 20, true, true,
                Color.fromRGB(0xE0B864)),
        ASTRAL("astral", "Astral", "<b><gradient:#C77DFF:#62CFF4>Astral</gradient></b>", 4500, 25, 30, true, true,
                Color.fromRGB(0xC77DFF));

        final String id, nom, joli;
        final long prix;
        final double bonus;
        final int offresHdv;
        final boolean vol, annonce;
        final Color particule;

        Grade(String id, String nom, String joli, long prix, double bonus, int offresHdv, boolean vol, boolean annonce, Color particule) {
            this.id = id; this.nom = nom; this.joli = joli; this.prix = prix; this.bonus = bonus; this.offresHdv = offresHdv;
            this.vol = vol; this.annonce = annonce; this.particule = particule;
        }
    }

    private final OrbitTycoon plugin;
    /** Joueurs à qui le vol de grade a été donné (on ne retire que celui-là, pas le vol du staff). */
    private final Set<UUID> volDonne = new HashSet<>();
    /** Joueurs qui ont coupé /fly ou les particules. */
    private final Set<UUID> sansVol = new HashSet<>(), sansParticules = new HashSet<>();
    private BukkitTask tache;
    private int tick;

    Grades(OrbitTycoon plugin) { this.plugin = plugin; }

    long prix(Grade g) { return plugin.getConfig().getLong("grades." + g.id + ".prix", g.prix); }
    int offresHdv(Grade g) { return plugin.getConfig().getInt("grades." + g.id + ".offres-hdv", g.offresHdv); }
    double bonus(Grade g) { return plugin.getConfig().getDouble("grades." + g.id + ".bonus", g.bonus); }

    /** Bonus de gains du joueur en % (Crédits, Flux, XP d'outil, générateurs), 0 sans grade. */
    double bonus(Player p) {
        Grade g = grade(p);
        return g == null ? 0 : bonus(g);
    }

    /** Grade le plus haut du joueur parmi les 4 (null = aucun). */
    Grade grade(Player p) {
        Grade[] t = Grade.values();
        for (int i = t.length - 1; i >= 0; i--) {
            String perm = "group." + t[i].id;
            if (p.isPermissionSet(perm) && p.hasPermission(perm)) return t[i];
        }
        return null;
    }

    /** Offres HDV max : hdv.max-offres + bonus du grade. */
    int maxOffres(Player p) {
        Grade g = grade(p);
        return plugin.getConfig().getInt("hdv.max-offres", 10) + (g == null ? 0 : offresHdv(g));
    }

    /** Prix à payer pour le grade g (prix plein), -1 si déjà au moins ce grade. */
    long prixPour(Player p, Grade g) {
        Grade a = grade(p);
        if (a != null && a.ordinal() >= g.ordinal()) return -1;
        return prix(g);
    }

    /** Grade à acheter avant g (null si g est achetable maintenant) : on monte un grade à la fois. */
    Grade manquant(Player p, Grade g) {
        Grade a = grade(p);
        int suivant = a == null ? 0 : a.ordinal() + 1;
        return g.ordinal() > suivant ? Grade.values()[g.ordinal() - 1] : null;
    }

    /** Avantages du grade g (cumulés avec ceux des grades en dessous), pour la boutique. */
    List<String> avantages(Grade g) {
        String b = "<#7CFC9A>+" + (bonus(g) == Math.rint(bonus(g)) ? String.valueOf((long) bonus(g)) : String.valueOf(bonus(g))) + " %";
        List<String> l = new ArrayList<>();
        l.add("<#E0B864><bold>Bonus de gains :</bold>");
        l.add("<dark_gray> • " + b + " <white>de Crédits");
        l.add("<dark_gray> • " + b + " <white>de Flux");
        l.add("<dark_gray> • " + b + " <white>d'XP de farm");
        l.add("<dark_gray> • " + b + " <white>de production des générateurs");
        l.add("");
        l.add("<#62CFF4><bold>Avantages :</bold>");
        l.add("<dark_gray> • <white>Accès prioritaire <gray>(entre même serveur plein)");
        l.add("<dark_gray> • <white>Cadre " + g.joli + " <white>dans le Tab et le HUD");
        l.add("<dark_gray> • <white>Titre de chat <gray>« " + g.nom + " »");
        l.add("<dark_gray> • <white>+" + offresHdv(g) + " offres <gray>à l'hôtel des ventes");
        // Commandes : celles des grades en dessous, puis les nouvelles de ce grade (en vert).
        StringBuilder avant = new StringBuilder();
        for (CommandesGrade.Cmd c : CommandesGrade.Cmd.values())
            if (c.min.ordinal() < g.ordinal()) avant.append(avant.isEmpty() ? "" : " ").append("/").append(c.nom);
        l.add("");
        l.add("<#62CFF4><bold>Commandes :</bold>");
        if (!avant.isEmpty()) l.add("<dark_gray> • <gray>" + avant + " <dark_gray>(grades d'avant)");
        for (CommandesGrade.Cmd c : CommandesGrade.nouvelles(g))
            l.add("<dark_gray> • <#7CFC9A>/" + c.nom + " <gray>" + c.description);
        if (g == Grade.ASTRAL) l.add("<dark_gray> • <#7CFC9A>/pv <gray>agrandi à 54 cases, <#7CFC9A>/near<gray> à 250 m");
        l.add("");
        l.add("<#62CFF4><bold>Cosmétiques :</bold>");
        if (g.particule != null) l.add("<dark_gray> • <white>Particules " + (g == Grade.ASTRAL ? "en orbite" : "de grade")
                + " <dark_gray>(/grades particules)");
        if (g.annonce) l.add("<dark_gray> • <white>Annonce de ton arrivée <gray>dans la station");
        return l;
    }

    // ------------------------------------------------------------------ achat

    /** Achète le grade g : retire le Plasma, donne le groupe LuckPerms (server=tycoon), débloque le titre. */
    void acheter(Player p, Grade g) {
        long prix = prixPour(p, g);
        if (prix < 0) { OrbitTycoon.msg(p, "<gray>Tu as déjà ce grade (ou mieux)."); return; }
        Grade avant = manquant(p, g);
        if (avant != null) {
            OrbitTycoon.msg(p, "<#FF4D4D>Achète d'abord le grade " + avant.joli + " <#FF4D4D>.");
            p.playSound(p.getLocation(), Sound.BLOCK_CHEST_LOCKED, 0.6f, 1.3f);
            return;
        }
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) {
            OrbitTycoon.msg(p, "<#FF4D4D>Les grades sont indisponibles pour l'instant (LuckPerms absent).");
            return;
        }
        if (!plugin.monnaies().retirer(p.getUniqueId(), Monnaies.Monnaie.PLASMA, prix)) {
            OrbitTycoon.msg(p, "<#FF4D4D>Il te faut " + Monnaies.Monnaie.PLASMA.format(prix) + " <#FF4D4D>pour ce grade.");
            p.playSound(p.getLocation(), Sound.BLOCK_CHEST_LOCKED, 0.6f, 1.3f);
            return;
        }
        Grade ancien = grade(p);
        String uid = p.getUniqueId().toString();
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + uid + " parent add " + g.id + " server=tycoon");
        if (ancien != null)
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + uid + " parent remove " + ancien.id + " server=tycoon");
        plugin.getLogger().info("Grade " + g.id + " acheté par " + p.getName() + " pour " + prix + " Plasma.");
        if (plugin.butin() != null && !plugin.butin().possede(p.getUniqueId(), "grade_" + g.id))
            plugin.butin().debloquer(p, "grade_" + g.id);
        Bukkit.broadcast(MM.deserialize("<#E0B864>✦ <white>" + p.getName() + " <gray>devient " + g.joli + " <gray>de la station !"));
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.1f);
    }

    // ------------------------------------------------------------------ commandes

    /** /grades [particules|fly] ; /fly. */
    void commande(Player p, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("particules")) {
            if (!sansParticules.remove(p.getUniqueId())) sansParticules.add(p.getUniqueId());
            OrbitTycoon.msg(p, "<gray>Particules de grade : " + (sansParticules.contains(p.getUniqueId()) ? "<#FF4D4D>coupées" : "<#7CFC9A>activées"));
            return;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("fly")) { basculerVol(p); return; }
        plugin.boutique().ouvrirGrades(p);
    }

    void basculerVol(Player p) {
        Grade g = grade(p);
        if (g == null || !g.vol) {
            OrbitTycoon.msg(p, "<gray>Le vol sur ton île est un avantage des grades <#62CFF4>Commandant</#62CFF4> et plus. <dark_gray>(/boutique)");
            return;
        }
        if (!sansVol.remove(p.getUniqueId())) sansVol.add(p.getUniqueId());
        OrbitTycoon.msg(p, "<gray>Vol sur ton île : " + (sansVol.contains(p.getUniqueId()) ? "<#FF4D4D>coupé" : "<#7CFC9A>activé <dark_gray>(double saut)"));
        verifierVol(p);
    }

    // ------------------------------------------------------------------ tâches

    void demarrer() {
        tache = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 5L);
    }

    void arreter() {
        if (tache != null) tache.cancel();
        for (UUID id : volDonne) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) { p.setFlying(false); p.setAllowFlight(false); }
        }
        volDonne.clear();
    }

    private void tick() {
        tick++;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (tick % 4 == 0) verifierVol(p);
            particules(p);
        }
    }

    /** Vol autorisé seulement sur sa propre île (monde des îles, dans les limites de l'île). */
    private void verifierVol(Player p) {
        if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) return;
        Grade g = grade(p);
        boolean ok = g != null && g.vol && !sansVol.contains(p.getUniqueId()) && surSonIle(p);
        UUID id = p.getUniqueId();
        if (ok && !p.getAllowFlight()) {
            p.setAllowFlight(true);
            volDonne.add(id);
            p.sendActionBar(MM.deserialize("<#62CFF4>✈ Vol activé sur ton île <dark_gray>(double saut)"));
        } else if (!ok && volDonne.remove(id)) {
            if (p.isFlying()) p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 100, 0, true, false));
            p.setFlying(false);
            p.setAllowFlight(false);
            p.sendActionBar(MM.deserialize("<gray>Vol coupé : tu as quitté ton île."));
        }
    }

    private boolean surSonIle(Player p) {
        IlesJoueurs iles = plugin.ilesJoueurs();
        if (iles == null || !IlesJoueurs.estMonde(p.getWorld())) return false;
        Location l = p.getLocation();
        IlesJoueurs.Ile ile = iles.ileDe(p.getUniqueId());
        return ile != null && iles.ileA(l.getX(), l.getZ()) == ile && iles.dedans(ile, l.getX(), l.getZ());
    }

    private void particules(Player p) {
        Grade g = grade(p);
        if (g == null || g.particule == null || sansParticules.contains(p.getUniqueId())) return;
        if (p.getGameMode() == GameMode.SPECTATOR || p.isInvisible()) return;
        if (plugin.staff() != null && plugin.staff().invisible(p)) return;
        Location l = p.getLocation();
        Particle.DustOptions dust = new Particle.DustOptions(g.particule, 0.8f);
        if (g == Grade.ASTRAL) {
            // Deux petites lunes en orbite autour du joueur.
            double a = tick * 0.35;
            for (int k = 0; k < 2; k++) {
                double b = a + Math.PI * k;
                p.getWorld().spawnParticle(Particle.DUST, l.clone().add(Math.cos(b) * 0.9, 1.0, Math.sin(b) * 0.9), 1, 0, 0, 0, 0,
                        new Particle.DustOptions(k == 0 ? g.particule : Color.fromRGB(0x62CFF4), 0.9f));
            }
        } else if (tick % 2 == 0) {
            p.getWorld().spawnParticle(Particle.DUST, l.clone().add(0, 0.1, 0), 2, 0.25, 0.02, 0.25, 0, dust);
        }
    }

    // ------------------------------------------------------------------ événements

    @EventHandler
    public void connexion(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            Grade g = grade(p);
            if (g == null || !g.annonce) return;
            if (plugin.staff() != null && plugin.staff().invisible(p)) return;
            Bukkit.broadcast(MM.deserialize("<#E0B864>✦ " + g.joli + " <white>" + p.getName() + " <gray>vient d'arriver dans la station !"));
        }, 40L);
    }

    /**
     * Accès prioritaire : un joueur gradé entre même si le serveur est plein (pas de file d'attente sur le réseau
     * aujourd'hui ; la permission orbit.file.bypass est aussi donnée aux groupes pour une future file du proxy).
     */
    @SuppressWarnings("deprecation")
    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void connexionPleine(org.bukkit.event.player.PlayerLoginEvent e) {
        if (e.getResult() != org.bukkit.event.player.PlayerLoginEvent.Result.KICK_FULL) return;
        Player p = e.getPlayer();
        if (grade(p) != null || p.hasPermission("orbit.file.bypass")) e.allow();
    }

    @EventHandler
    public void depart(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        if (volDonne.remove(id)) { e.getPlayer().setFlying(false); e.getPlayer().setAllowFlight(false); }
    }
}
