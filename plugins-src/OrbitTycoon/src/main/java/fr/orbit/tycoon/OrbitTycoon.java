package fr.orbit.tycoon;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class OrbitTycoon extends JavaPlugin implements Listener {
    static final String PREFIXE = "<#62CFF4><bold>Orbit</bold> <#E0B864>» <gray>";
    /** Version du dessin : 2 = grand spawn + 5 îles à 1100 blocs + anneaux-portails. */
    static final int VERSION_DESSIN = 2;

    private Liens liens;
    private Navigateur navigateur;
    private Outil outil;
    private Zones zones;
    private Mobzone mobzone;
    private Plans plans;
    private Portails portails;
    private Pack pack;
    private Monnaies monnaies;
    private Hud hud;
    private boolean chantierEnCours;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true); // ajoute les nouvelles clés (ex. flux-stellaire) à une config existante
        saveConfig();
        liens = new Liens(this);
        navigateur = new Navigateur(this, liens);
        outil = new Outil(this, liens);
        zones = new Zones(this);
        mobzone = new Mobzone(this);
        plans = new Plans(this);
        portails = new Portails(this, navigateur);
        pack = new Pack(this);
        monnaies = new Monnaies(this);
        hud = new Hud(this, monnaies);
        navigateur.pack(pack);

        World w = monde();
        w.setGameRule(GameRules.ADVANCE_TIME, false);
        w.setGameRule(GameRules.ADVANCE_WEATHER, false);
        w.setGameRule(GameRules.SPAWN_MOBS, false);
        w.setGameRule(GameRules.SPAWN_PATROLS, false);
        w.setGameRule(GameRules.SPAWN_PHANTOMS, false);
        w.setGameRule(GameRules.SPAWN_WANDERING_TRADERS, false);
        w.setGameRule(GameRules.FALL_DAMAGE, false);
        w.setGameRule(GameRules.KEEP_INVENTORY, true);
        w.setGameRule(GameRules.IMMEDIATE_RESPAWN, true);
        w.setGameRule(GameRules.PVP, false);
        w.setGameRule(GameRules.MOB_GRIEFING, false);
        w.setTime(6000);
        w.setStorm(false);
        w.setSpawnLocation(Zone.BASE.arrivee(w));

        var pm = getServer().getPluginManager();
        pm.registerEvents(liens, this);
        pm.registerEvents(navigateur, this);
        pm.registerEvents(outil, this);
        pm.registerEvents(zones, this);
        pm.registerEvents(portails, this);
        pm.registerEvents(pack, this);
        pm.registerEvents(monnaies, this);
        pm.registerEvents(hud, this);
        pm.registerEvents(new Piles(this), this);
        pm.registerEvents(this, this);
        mobzone.demarrer();
        portails.demarrer();
        pack.demarrer();
        monnaies.demarrer();
        hud.demarrer();

        if (donnees().getInt("version", donnees().getBoolean("genere") ? 1 : 0) < VERSION_DESSIN)
            getLogger().warning("Le dessin du Tycoon n'est pas à jour : lance « tycoon generer tout confirmer » dans la console.");
        for (Player p : Bukkit.getOnlinePlayers()) equiper(p);
    }

    @Override
    public void onDisable() {
        if (zones != null) zones.toutRestaurer();
        if (mobzone != null) { mobzone.arreter(); mobzone.vider(monde()); }
        if (portails != null) portails.arreter();
        if (pack != null) pack.arreter();
        if (hud != null) hud.arreter();
        if (monnaies != null) monnaies.arreter();
    }

    World monde() { return Bukkit.getWorlds().get(0); }

    Monnaies monnaies() { return monnaies; }

    // ------------------------------------------------------------------ données

    private File fichierDonnees() { return new File(getDataFolder(), "donnees.yml"); }

    private YamlConfiguration donnees() { return YamlConfiguration.loadConfiguration(fichierDonnees()); }

    private void sauver(YamlConfiguration y) {
        try { y.save(fichierDonnees()); } catch (IOException e) { getLogger().severe("Impossible d'écrire donnees.yml : " + e); }
    }

    // ------------------------------------------------------------------ joueurs

    private void equiper(Player p) {
        navigateur.donner(p);
        outil.donner(p);
    }

    @EventHandler
    public void connexion(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        p.teleportAsync(Zone.BASE.arrivee(monde()));
        equiper(p);
        pack.envoyer(p);
        msg(p, "Bienvenue sur <gradient:#62CFF4:#E0B864><bold>Orbit Tycoon</bold></gradient><gray> ! Traverse un anneau-portail ou utilise ton <#E0B864>Navigateur orbital</#E0B864><gray>.");
        msg(p, "<#E0B864>Shift + clic droit<gray> avec ton <#E0B864>Outil orbital</#E0B864><gray> pour changer d'outil.");
    }

    @EventHandler
    public void reapparition(PlayerRespawnEvent e) {
        Zone z = Zone.at(e.getPlayer().getLocation());
        e.setRespawnLocation((z == null ? Zone.BASE : z).arrivee(monde()));
        Bukkit.getScheduler().runTask(this, () -> equiper(e.getPlayer()));
    }

    @EventHandler
    public void portailVanilla(PlayerPortalEvent e) {
        e.setCancelled(true);
    }

    static void msg(CommandSender s, String mm) {
        s.sendMessage(MiniMessage.miniMessage().deserialize(PREFIXE + mm));
    }

    // ------------------------------------------------------------------ commandes

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("zones")) {
            if (s instanceof Player p) navigateur.ouvrir(p);
            else msg(s, "Commande réservée aux joueurs.");
            return true;
        }
        if (cmd.getName().equalsIgnoreCase("solde")) {
            if (args.length > 0 && s.hasPermission("orbittycoon.admin")) {
                var cible = Bukkit.getOfflinePlayer(args[0]);
                monnaies.afficherSolde(s, cible.getUniqueId(), cible.getName());
            } else if (s instanceof Player p) monnaies.afficherSolde(s, p.getUniqueId(), null);
            else msg(s, "Usage : /solde <joueur>");
            return true;
        }
        String sous = args.length > 0 ? args[0].toLowerCase() : "";
        switch (sous) {
            case "generer" -> generer(s, args);
            case "tp" -> {
                Zone z = args.length > 1 ? Zone.parse(args[1]) : null;
                if (!(s instanceof Player p)) { msg(s, "Commande réservée aux joueurs."); return true; }
                if (z == null) { msg(s, "Usage : /tycoon tp <base|peche|bucheron|agriculture|mobzone|mine>"); return true; }
                navigateur.teleporter(p, z);
            }
            case "navigateur", "equiper" -> {
                Player cible = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : (s instanceof Player p ? p : null);
                if (cible == null) { msg(s, "Joueur introuvable."); return true; }
                equiper(cible);
                msg(s, "Navigateur et Outil orbital donnés à " + cible.getName() + ".");
            }
            case "reload" -> {
                reloadConfig();
                mobzone.demarrer();
                msg(s, "Configuration rechargée.");
            }
            case "monnaie" -> monnaie(s, args);
            case "prestige" -> prestige(s, args);
            case "ile" -> ile(s, args);
            default -> {
                msg(s, "<#E0B864>/tycoon generer [tout|base|peche|bucheron|agriculture|mobzone|mine] [confirmer]");
                msg(s, "<#E0B864>/tycoon tp <zone><gray>, <#E0B864>/tycoon equiper [joueur]<gray>, <#E0B864>/tycoon reload");
                msg(s, "<#E0B864>/tycoon monnaie <donner|retirer|definir> <joueur> <credit|flux|plasma> <montant>");
                msg(s, "<#E0B864>/tycoon prestige <joueur> <niveau><gray>, <#E0B864>/tycoon ile <joueur> <nom|aucune>");
            }
        }
        return true;
    }

    /** /tycoon prestige <joueur> <niveau> (en attendant le vrai système de prestige). */
    private void prestige(CommandSender s, String[] args) {
        int niveau;
        try { niveau = args.length < 3 ? -1 : Integer.parseInt(args[2]); } catch (NumberFormatException e) { niveau = -1; }
        if (niveau < 0) { msg(s, "Usage : /tycoon prestige <joueur> <niveau>"); return; }
        var cible = Bukkit.getOfflinePlayer(args[1]);
        monnaies.definirPrestige(cible.getUniqueId(), niveau);
        msg(s, "Prestige de " + cible.getName() + " : <#E0B864>✪ " + niveau);
    }

    /** /tycoon ile <joueur> <nom|aucune> (en attendant les îles des joueurs). */
    private void ile(CommandSender s, String[] args) {
        if (args.length < 3) { msg(s, "Usage : /tycoon ile <joueur> <nom|aucune>"); return; }
        var cible = Bukkit.getOfflinePlayer(args[1]);
        String nom = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
        if (nom.equalsIgnoreCase("aucune")) nom = null;
        else if (nom.length() > 20) { msg(s, "Nom trop long (20 caractères max)."); return; }
        monnaies.definirIle(cible.getUniqueId(), nom);
        msg(s, "Île de " + cible.getName() + " : " + (nom == null ? "<gray>aucune" : "<white>" + MiniMessage.miniMessage().escapeTags(nom)));
    }

    /** /tycoon monnaie <donner|retirer|definir> <joueur> <credit|flux|plasma> <montant> (aussi pour la future boutique). */
    private void monnaie(CommandSender s, String[] args) {
        String usage = "Usage : /tycoon monnaie <donner|retirer|definir> <joueur> <credit|flux|plasma> <montant>";
        if (args.length < 5) { msg(s, usage); return; }
        var cible = Bukkit.getOfflinePlayer(args[2]);
        Monnaies.Monnaie m = Monnaies.Monnaie.parse(args[3]);
        long montant;
        try { montant = Long.parseLong(args[4]); } catch (NumberFormatException e) { montant = -1; }
        if (m == null || montant < 0) { msg(s, usage); return; }
        var id = cible.getUniqueId();
        switch (args[1].toLowerCase()) {
            case "donner" -> monnaies.ajouter(id, m, montant);
            case "definir" -> monnaies.definir(id, m, montant);
            case "retirer" -> {
                if (!monnaies.retirer(id, m, montant)) { msg(s, "<#FF4D4D>Solde insuffisant</#FF4D4D> (" + m.format(monnaies.solde(id, m)) + "<gray>)."); return; }
            }
            default -> { msg(s, usage); return; }
        }
        msg(s, "Solde de " + cible.getName() + " : " + m.format(monnaies.solde(id, m)));
        if (cible.getPlayer() != null && args[1].equalsIgnoreCase("donner"))
            msg(cible.getPlayer(), "Tu as reçu " + m.format(montant) + "<gray>.");
    }

    private void generer(CommandSender s, String[] args) {
        if (chantierEnCours) { msg(s, "Une génération est déjà en cours."); return; }
        YamlConfiguration d = donnees();
        int version = d.getInt("version", d.getBoolean("genere") ? 1 : 0);
        boolean confirmer = args.length > 2 && args[2].equalsIgnoreCase("confirmer")
                || args.length == 2 && args[1].equalsIgnoreCase("confirmer");
        String cible = args.length > 1 && !args[1].equalsIgnoreCase("confirmer") ? args[1] : "tout";
        List<Zone> liste = new ArrayList<>();
        if (cible.equalsIgnoreCase("tout")) liste.addAll(List.of(Zone.values()));
        else for (String morceau : cible.split(",")) {
            Zone z = Zone.parse(morceau.trim());
            if (z == null) { msg(s, "Zone inconnue : " + morceau); return; }
            liste.add(z);
        }
        if (version > 0 && !confirmer) {
            msg(s, "<#FF4D4D>Déjà généré.</#FF4D4D> Régénérer efface les retouches faites à la main. Ajoute <#E0B864>confirmer</#E0B864>.");
            return;
        }
        chantierEnCours = true;
        Deque<Zone> file = new ArrayDeque<>(liste);
        msg(s, "Génération lancée (" + liste.size() + " zone(s)). Le serveur va ralentir pendant quelques minutes.");
        if (version == 1) {
            Chantier c = new Chantier(this, monde());
            plans.effacerAncienneVersion(c);
            msg(s, "→ Effacement de l'ancienne version (petites îles).");
            c.lancer(pct -> getLogger().info("ancienne version : " + pct + " %"), () -> suivante(s, file));
        } else suivante(s, file);
    }

    /** Une zone à la fois. Une zone déjà dessinée en version actuelle est d'abord vidée. */
    private void suivante(CommandSender s, Deque<Zone> file) {
        Zone z = file.poll();
        YamlConfiguration d = donnees();
        if (z == null) {
            chantierEnCours = false;
            d.set("version", VERSION_DESSIN);
            d.set("genere", true);
            sauver(d);
            msg(s, "<#7CFC9A>Génération terminée.");
            return;
        }
        boolean nettoyer = d.getInt("zones." + z.id, 0) == VERSION_DESSIN;
        Chantier c = new Chantier(this, monde());
        plans.dessiner(c, z, nettoyer);
        long debut = System.currentTimeMillis();
        msg(s, "→ " + z.nom + "<gray> : " + c.nbEtapes() + " étapes" + (nettoyer ? " (avec nettoyage)" : "") + ".");
        c.lancer(pct -> getLogger().info(z.id + " : " + pct + " %"), () -> {
            YamlConfiguration d2 = donnees();
            d2.set("zones." + z.id, VERSION_DESSIN);
            sauver(d2);
            msg(s, "✔ " + z.nom + "<gray> terminée : " + c.poses() + " blocs en " + (System.currentTimeMillis() - debut) / 1000 + " s.");
            suivante(s, file);
        });
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String alias, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("tycoon")) return List.of();
        List<String> res = new ArrayList<>();
        if (args.length == 1) res.addAll(List.of("generer", "tp", "equiper", "reload", "monnaie"));
        else if (args[0].equalsIgnoreCase("monnaie")) {
            if (args.length == 2) res.addAll(List.of("donner", "retirer", "definir"));
            else if (args.length == 4) res.addAll(List.of("credit", "flux", "plasma"));
            else return null;
        }
        else if (args.length == 2 && args[0].equalsIgnoreCase("generer")) { res.add("tout"); for (Zone z : Zone.values()) res.add(z.id); }
        else if (args.length == 2 && args[0].equalsIgnoreCase("tp")) for (Zone z : Zone.values()) res.add(z.id);
        else if (args.length == 3 && args[0].equalsIgnoreCase("generer")) res.add("confirmer");
        else return null;
        String debut = args[args.length - 1].toLowerCase();
        res.removeIf(x -> !x.startsWith(debut));
        return res;
    }
}
