package fr.orbit.tycoon;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.Location;
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
    private IlesJoueurs ilesJoueurs;
    private Pnjs pnjs;
    private Classement classement;
    private Coffres coffres;
    private Boutique boutique;
    private Decors decors;
    private Tuto tuto;
    private Progression progression;
    private Confirmer confirmer;
    private Quetes quetes;
    private Hdv hdv;
    private Staff staff;
    private Guide guide;
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
        ilesJoueurs = new IlesJoueurs(this);
        classement = new Classement(this);
        coffres = new Coffres(this);
        boutique = new Boutique(this);
        decors = new Decors(this);
        pnjs = new Pnjs(this);
        tuto = new Tuto(this);
        progression = new Progression(this);
        confirmer = new Confirmer(this);
        quetes = new Quetes(this);
        hdv = new Hdv(this);
        staff = new Staff(this);
        guide = new Guide(this);
        navigateur.iles(ilesJoueurs);
        navigateur.pack(pack);
        outil.pack(pack);

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
        w.setTime(18000);   // nuit permanente (minuit) : ambiance spatiale
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
        pm.registerEvents(ilesJoueurs, this);
        pm.registerEvents(classement, this);
        pm.registerEvents(coffres, this);
        pm.registerEvents(boutique, this);
        pm.registerEvents(pnjs, this);
        pm.registerEvents(tuto, this);
        pm.registerEvents(progression, this);
        pm.registerEvents(confirmer, this);
        pm.registerEvents(quetes, this);
        pm.registerEvents(hdv, this);
        pm.registerEvents(staff, this);
        pm.registerEvents(guide, this);
        pm.registerEvents(new Piles(this), this);
        pm.registerEvents(this, this);
        mobzone.demarrer();
        portails.demarrer();
        pack.demarrer();
        monnaies.demarrer();
        hud.demarrer();
        ilesJoueurs.demarrer();
        classement.demarrer();
        coffres.demarrer();
        boutique.demarrer();
        pnjs.demarrer();
        tuto.demarrer();
        hdv.demarrer();
        staff.demarrer();
        guide.charger();

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
        if (classement != null) classement.arreter();
        if (coffres != null) coffres.arreter();
        if (boutique != null) boutique.arreter();
        if (pnjs != null) pnjs.arreter();
        if (tuto != null) tuto.arreter();
        if (hdv != null) hdv.arreter();
        if (staff != null) staff.arreter();
        if (ilesJoueurs != null) ilesJoueurs.arreter();
        if (monnaies != null) monnaies.arreter();
    }

    World monde() { return Bukkit.getWorlds().get(0); }

    Monnaies monnaies() { return monnaies; }

    IlesJoueurs ilesJoueurs() { return ilesJoueurs; }

    Tuto tuto() { return tuto; }

    Coffres coffres() { return coffres; }

    Pack pack() { return pack; }

    Liens liens() { return liens; }

    Outil outil() { return outil; }

    Progression progression() { return progression; }

    Confirmer confirmer() { return confirmer; }

    Quetes quetes() { return quetes; }

    Hdv hdv() { return hdv; }

    /** Renvoie le joueur au spawn (Base Orbit). */
    void versBase(Player p) { navigateur.teleporter(p, Zone.BASE, Zone.BASE.arrivee(monde())); }

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
        msg(p, "<#E0B864>Shift + clic droit<gray> avec ton <#E0B864>Outil orbital</#E0B864><gray> : son interface ; <#E0B864>Shift + F<gray> : outil suivant.");
    }

    @EventHandler
    public void reapparition(PlayerRespawnEvent e) {
        Zone z = Zone.at(e.getPlayer().getLocation());
        Location surIle = IlesJoueurs.estMonde(e.getPlayer().getWorld()) ? ilesJoueurs.reapparition(e.getPlayer()) : null;
        e.setRespawnLocation(surIle != null ? surIle : (z == null ? Zone.BASE : z).arrivee(monde()));
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
        if (cmd.getName().equalsIgnoreCase("is")) return ilesJoueurs.commande(s, args);
        if (cmd.getName().equalsIgnoreCase("tuto")) return tuto.commande(s, args);
        if (cmd.getName().equalsIgnoreCase("hdv")) return hdv.commande(s, args);
        if (cmd.getName().equalsIgnoreCase("staff")) return staff.commande(s, args);
        if (cmd.getName().equalsIgnoreCase("guide")) return guide.commande(s, args);
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
        // /classements <outil> et /classement <outil> : classement de l'outil ; /classement seul : TP au podium.
        if (cmd.getName().equalsIgnoreCase("classements") || (cmd.getName().equalsIgnoreCase("classement") && args.length > 0)) {
            classements(s, args);
            return true;
        }
        // /classement, /coffres : TP au spawn ; /boutique : menu de la boutique.
        if (cmd.getName().equalsIgnoreCase("classement") || cmd.getName().equalsIgnoreCase("coffres")
                || cmd.getName().equalsIgnoreCase("boutique")) {
            if (!(s instanceof Player p)) { msg(s, "Commande réservée aux joueurs."); return true; }
            switch (cmd.getName().toLowerCase()) {
                case "classement" -> navigateur.teleporter(p, Zone.BASE, classement.pointTp());
                case "coffres" -> navigateur.teleporter(p, Zone.BASE, coffres.pointTp());
                default -> boutique.ouvrir(p);
            }
            return true;
        }
        // /observatoire, /marchand, /forgeron, /quetes : TP devant le PNJ du pavillon.
        String lieu = cmd.getName().toLowerCase();
        if (lieu.equals("observatoire") || lieu.equals("marchand") || lieu.equals("forgeron") || lieu.equals("quetes")) {
            if (!(s instanceof Player p)) { msg(s, "Commande réservée aux joueurs."); return true; }
            navigateur.teleporter(p, Zone.BASE, pnjs.pointTp(lieu));
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
                coffres.rafraichir();
                boutique.recharger();
                guide.charger();
                msg(s, "Configuration rechargée.");
            }
            case "monnaie" -> monnaie(s, args);
            case "prestige" -> prestige(s, args);
            case "ile" -> ile(s, args);
            case "dome" -> dome(s, args);
            case "tuto" -> {
                if (args.length < 3 || !args[1].equalsIgnoreCase("reset")) { msg(s, "Usage : /tycoon tuto reset <joueur>"); return true; }
                tuto.reset(s, args[2]);
            }
            case "iles" -> {
                if (args.length > 1 && args[1].equalsIgnoreCase("deco")) msg(s, "Îles : " + ilesJoueurs.decorerTout() + " blocs de décor posés.");
                else msg(s, "Usage : /tycoon iles deco <gray>(pose le décor spatial sur les îles existantes, seulement dans l'air)");
            }
            case "coffres" -> {
                if (args.length > 1 && args[1].equalsIgnoreCase("poser")) msg(s, "Coffres : " + coffres.poser() + " blocs posés.");
                coffres.rafraichir();
                msg(s, "Noms des coffres mis à jour (config.yml, coffres.noms ; /tycoon reload puis /tycoon coffres).");
            }
            case "cle" -> {
                int n = args.length > 1 && args[1].matches("[1-5]") ? Integer.parseInt(args[1]) - 1 : -1;
                Player cible = args.length > 2 ? Bukkit.getPlayerExact(args[2]) : (s instanceof Player p ? p : null);
                int nombre = args.length > 3 && args[3].matches("\\d{1,2}") ? Math.max(1, Integer.parseInt(args[3])) : 1;
                if (n < 0 || cible == null) { msg(s, "Usage : /tycoon cle <1-5> [joueur] [nombre]"); return true; }
                cible.getInventory().addItem(coffres.cle(n, nombre));
                msg(s, nombre + " clé(s) « " + coffres.nom(n) + " » données à " + cible.getName() + ".");
            }
            case "deco" -> {
                if (args.length > 1 && args[1].equalsIgnoreCase("poser")) msg(s, "Décor du spawn : " + decors.poser());
                else if (args.length > 1 && args[1].equalsIgnoreCase("retirer")) msg(s, "Décor du spawn : " + decors.retirer());
                else msg(s, "Usage : /tycoon deco <poser|retirer> <gray>(seulement dans l air ; retirer = enlève ce que poser a mis)");
            }
            case "boutique" -> {
                if (args.length > 1 && args[1].equalsIgnoreCase("poser")) msg(s, "Boutique : " + boutique.poser() + " blocs posés (arche + estrade).");
                else if (args.length > 1 && args[1].equalsIgnoreCase("retirer")) msg(s, "Boutique : " + boutique.retirer() + " blocs retirés (avenues sans boutique).");
                else msg(s, "Usage : /tycoon boutique <poser|retirer>");
            }
            case "classement" -> {
                if (args.length > 1 && args[1].equalsIgnoreCase("poser")) msg(s, "Classement : " + classement.poser() + " blocs posés (socles des panneaux + podium).");
                classement.forcer();
                msg(s, "Classement prestige mis à jour.");
            }
            default -> {
                msg(s, "<#E0B864>/tycoon generer [tout|base|peche|bucheron|agriculture|mobzone|mine] [confirmer]");
                msg(s, "<#E0B864>/tycoon tp <zone><gray>, <#E0B864>/tycoon equiper [joueur]<gray>, <#E0B864>/tycoon reload");
                msg(s, "<#E0B864>/tycoon monnaie <donner|retirer|definir> <joueur> <credit|flux|plasma> <montant>");
                msg(s, "<#E0B864>/tycoon prestige <joueur> <niveau> [outil]<gray>,<#E0B864>/tycoon ile <joueur> <nom|aucune>");
                msg(s, "<#E0B864>/tycoon classement [poser]<gray> (met à jour ; poser = socles + podium au spawn)");
                msg(s, "<#E0B864>/tycoon coffres [poser]<gray> (met à jour les noms ; poser = espace + 5 coffres au spawn)");
                msg(s, "<#E0B864>/tycoon boutique poser<gray> (arche + estrade du PNJ Boutique), <#E0B864>/tycoon dome generer [confirmer]");
                msg(s, "<#E0B864>/tycoon cle <1-5> [joueur] [nombre]<gray> (clé provisoire de test)");
                msg(s, "<#E0B864>/tycoon tuto reset <joueur><gray> (relance le tuto du joueur)");
                msg(s, "<#E0B864>/tycoon deco <poser|retirer><gray> (décor du spawn : pavillons, anneaux, espaces vides)");
            }
        }
        return true;
    }

    /** /tycoon dome generer [confirmer] : grand dôme sur tout le spawn + vaisseau amiral (retire l ancien petit dôme). */
    private void dome(CommandSender s, String[] args) {
        if (args.length < 2 || !args[1].equalsIgnoreCase("generer")) { msg(s, "Usage : /tycoon dome generer [confirmer]"); return; }
        if (chantierEnCours) { msg(s, "Une génération est déjà en cours."); return; }
        YamlConfiguration d = donnees();
        boolean ancien = d.getBoolean("dome", false);          // ancien petit dôme d'observation encore en place
        boolean deja = d.getBoolean("grand-dome", false);
        boolean confirmer = args.length > 2 && args[2].equalsIgnoreCase("confirmer");
        if (deja && !confirmer) { msg(s, "<#FF4D4D>Grand dôme déjà construit.</#FF4D4D> Ajoute <#E0B864>confirmer</#E0B864> pour l'effacer et le reconstruire (retouches perdues)."); return; }
        chantierEnCours = true;
        Chantier c = new Chantier(this, monde());
        new Dome(plans).dessiner(c, ancien, deja);
        long debut = System.currentTimeMillis();
        msg(s, "Dôme + vaisseau : " + c.nbEtapes() + " étapes, ça prend un moment.");
        c.lancer(pct -> getLogger().info("dome : " + pct + " %"), () -> {
            chantierEnCours = false;
            YamlConfiguration d2 = donnees();
            d2.set("dome", false);
            d2.set("grand-dome", true);
            sauver(d2);
            msg(s, "<#7CFC9A>Dôme et vaisseau terminés :</#7CFC9A> " + c.poses() + " blocs en " + (System.currentTimeMillis() - debut) / 1000 + " s.");
        });
    }

    /** /tycoon prestige <joueur> <niveau> [outil] (en attendant le vrai système de prestige). */
    private void prestige(CommandSender s, String[] args) {
        int niveau;
        try { niveau = args.length < 3 ? -1 : Integer.parseInt(args[2]); } catch (NumberFormatException e) { niveau = -1; }
        if (niveau < 0) { msg(s, "Usage : /tycoon prestige <joueur> <niveau> [houe|pioche|peche|epee|hache]"); return; }
        var cible = Bukkit.getOfflinePlayer(args[1]);
        if (args.length > 3) {
            Outil.Mode outil = Outil.Mode.parse(args[3]);
            if (outil == null) { msg(s, "Outil inconnu : houe, pioche, peche, epee ou hache."); return; }
            monnaies.definirPrestige(cible.getUniqueId(), outil, niveau);
            msg(s, "Prestige " + outil.nom + " de " + cible.getName() + " : <#E0B864>✪ " + niveau);
            return;
        }
        monnaies.definirPrestige(cible.getUniqueId(), niveau);
        msg(s, "Prestige de " + cible.getName() + " : <#E0B864>✪ " + niveau);
        classement.forcer();
    }

    /** /classements <houe|pioche|peche|epee|hache> : top 10 du prestige de cet outil, plus ta place. */
    private void classements(CommandSender s, String[] args) {
        Outil.Mode outil = args.length > 0 ? Outil.Mode.parse(args[0]) : null;
        if (outil == null) { msg(s, "Usage : <#E0B864>/classements <houe|pioche|peche|epee|hache>"); return; }
        java.util.UUID moi = s instanceof Player p ? p.getUniqueId() : null;
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            List<Classement.Ligne> liste = new ArrayList<>();
            monnaies.tousLesPrestiges(outil).forEach((id, pr) -> {
                if (pr <= 0) return;
                String nom = Bukkit.getOfflinePlayer(id).getName();
                if (nom != null) liste.add(new Classement.Ligne(id, nom, pr));
            });
            liste.sort(java.util.Comparator.comparingInt(Classement.Ligne::prestige).reversed()
                    .thenComparing(Classement.Ligne::nom, String.CASE_INSENSITIVE_ORDER));
            MiniMessage mm = MiniMessage.miniMessage();
            String[] couleurs = {"#E0B864", "#CFE8F5", "#D08A57"};
            StringBuilder sb = new StringBuilder("<gradient:#62CFF4:#E0B864><bold>✪ CLASSEMENT PRESTIGE</bold></gradient> <dark_gray>— <")
                    .append(outil.couleur).append("><bold>").append(outil.nom).append("</bold></").append(outil.couleur).append(">");
            if (liste.isEmpty()) sb.append("\n<gray>Personne n'a encore de prestige sur cet outil.");
            int maPlace = -1;
            for (int i = 0; i < liste.size(); i++) {
                Classement.Ligne l = liste.get(i);
                if (l.id().equals(moi)) maPlace = i;
                if (i >= 10) continue;
                String c = i < 3 ? couleurs[i] : "#8FA3B5";
                sb.append("\n<").append(c).append("><bold>").append(i + 1).append(".</bold></").append(c).append("> ")
                        .append(l.id().equals(moi) ? "<#62CFF4>" : "<white>").append(mm.escapeTags(l.nom()))
                        .append(" <dark_gray>— <#E0B864>✪ ").append(l.prestige());
            }
            if (moi != null) {
                if (maPlace >= 10) sb.append("\n<dark_gray>…\n<#62CFF4>Ta place : <bold>").append(maPlace + 1)
                        .append("</bold> <dark_gray>— <#E0B864>✪ ").append(liste.get(maPlace).prestige());
                else if (maPlace < 0) sb.append("\n<gray>Tu n'as pas encore de prestige ").append(outil.nom.toLowerCase()).append(".");
            }
            var texte = mm.deserialize(sb.toString());
            Bukkit.getScheduler().runTask(this, () -> s.sendMessage(texte));
        });
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
        if (cmd.getName().equalsIgnoreCase("is")) return ilesJoueurs.completer(args);
        if (cmd.getName().equalsIgnoreCase("tuto")) return args.length == 1 ? List.of("passer", "recommencer") : List.of();
        if (cmd.getName().equalsIgnoreCase("hdv")) return args.length == 1 ? List.of("vendre", "ventes", "aide") : List.of();
        if (cmd.getName().equalsIgnoreCase("staff")) return staff.completer(args);
        if (cmd.getName().equalsIgnoreCase("guide")) return args.length == 1 ? List.of("lire", "jeter") : List.of();
        List<String> res = new ArrayList<>();
        if (cmd.getName().equalsIgnoreCase("classements") || cmd.getName().equalsIgnoreCase("classement")) {
            if (args.length == 1) for (Outil.Mode m : Outil.Mode.values()) if (m.id.startsWith(args[0].toLowerCase())) res.add(m.id);
            return res;
        }
        if (!cmd.getName().equalsIgnoreCase("tycoon")) return List.of();
        if (args.length == 1) res.addAll(List.of("generer", "tp", "equiper", "reload", "monnaie", "prestige", "ile", "classement", "coffres", "cle", "boutique", "dome", "iles", "tuto", "deco"));
        else if (args[0].equalsIgnoreCase("monnaie")) {
            if (args.length == 2) res.addAll(List.of("donner", "retirer", "definir"));
            else if (args.length == 4) res.addAll(List.of("credit", "flux", "plasma"));
            else return null;
        }
        else if (args.length == 2 && args[0].equalsIgnoreCase("generer")) { res.add("tout"); for (Zone z : Zone.values()) res.add(z.id); }
        else if (args.length == 2 && args[0].equalsIgnoreCase("tp")) for (Zone z : Zone.values()) res.add(z.id);
        else if (args.length == 3 && args[0].equalsIgnoreCase("generer")) res.add("confirmer");
        else if (args.length == 4 && args[0].equalsIgnoreCase("prestige")) for (Outil.Mode m : Outil.Mode.values()) res.add(m.id);
        else return null;
        String debut = args[args.length - 1].toLowerCase();
        res.removeIf(x -> !x.startsWith(debut));
        return res;
    }
}
