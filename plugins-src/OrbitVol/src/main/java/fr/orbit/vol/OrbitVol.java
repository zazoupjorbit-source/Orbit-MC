package fr.orbit.vol;

import fr.orbit.vol.Catalogue.Mutation;
import fr.orbit.vol.Catalogue.Orbito;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Vole mon Orbito : mode de jeu de l'instance « vol ». */
public final class OrbitVol extends JavaPlugin {

    public static final String MONDE = "vol";
    private Jeu jeu;
    private Pack pack;
    private Stockage stockage;

    @Override
    public void onEnable() {
        getDataFolder().mkdirs();
        World w = Bukkit.getWorld(MONDE);
        if (w == null) w = new WorldCreator(MONDE).generator(new VoidGen()).generateStructures(false).createWorld();
        if (w == null) {
            getLogger().severe("Monde « vol » impossible à créer.");
            return;
        }
        w.setSpawnLocation(new Location(w, 0.5, Arene.Y + 1, 6.5));
        w.setTime(6000);
        for (int cx = -5; cx <= 4; cx++) for (int cz = -3; cz <= 2; cz++) w.setChunkForceLoaded(cx, cz, true);
        File marque = new File(getDataFolder(), "arene-generee.txt");
        if (!marque.exists()) {
            Arene.generer(w);
            ecrireMarque(marque);
            getLogger().info("Arène générée dans le monde « vol ».");
        }
        stockage = new Stockage(this);
        stockage.connecter(new File(getDataFolder(), getConfig().getString("base.fichier", "../../../../setup/db.properties")));
        pack = new Pack(this);
        pack.demarrer();
        Bukkit.getPluginManager().registerEvents(pack, this);
        jeu = new Jeu(this, w, pack);
        Bukkit.getPluginManager().registerEvents(jeu, this);
        Bukkit.getPluginManager().registerEvents(Objets.init(this, jeu), this);
        Bukkit.getPluginManager().registerEvents(new Gui.Ecoute(), this);
        Bukkit.getPluginManager().registerEvents(jeu.evenements, this);
        // Le jour reste figé.
        final World monde = w;
        Bukkit.getScheduler().runTaskTimer(this, () -> monde.setTime(6000), 200, 200);
        jeu.demarrer();
    }

    @Override
    public void onDisable() {
        if (jeu != null) jeu.arreter();
        if (pack != null) pack.arreter();
        if (stockage != null) stockage.fermer();
    }

    private void ecrireMarque(File f) {
        try {
            java.nio.file.Files.writeString(f.toPath(), "Supprimer ce fichier (ou /vol generer) pour reconstruire l'arène.\n");
        } catch (IOException e) {
            getLogger().warning("Marque d'arène non écrite : " + e.getMessage());
        }
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        String sous = a.length == 0 ? "" : a[0].toLowerCase(Locale.ROOT);
        boolean admin = s.hasPermission("orbitvol.admin");
        switch (sous) {
            case "ascension" -> {
                if (s instanceof Player p) jeu.ascension(p, a.length > 1 && a[1].equalsIgnoreCase("confirmer"));
            }
            case "generer" -> {
                if (!admin) return refus(s);
                Arene.generer(jeu.w);
                ecrireMarque(new File(getDataFolder(), "arene-generee.txt"));
                Txt.send(s, "Arène reconstruite. Les joueurs connectés gardent leur base.");
            }
            case "donner" -> {
                if (!admin) return refus(s);
                if (a.length < 3) { Txt.send(s, "/vol donner <joueur> <montant>"); return true; }
                Player c = Bukkit.getPlayerExact(a[1]);
                Profil pr = c == null ? null : jeu.profils.get(c.getUniqueId());
                if (pr == null) { Txt.send(s, "Joueur introuvable sur le mode."); return true; }
                pr.credits += Double.parseDouble(a[2]);
                Txt.send(s, "Solde de " + c.getName() + " : " + Txt.cr(pr.credits));
            }
            case "meteores" -> {
                if (!admin) return refus(s);
                jeu.lancerMeteores();
            }
            case "orbito" -> {
                if (!admin) return refus(s);
                if (a.length < 2 || Catalogue.get(a[1]) == null) { Txt.send(s, "/vol orbito <id> [OR|DIAMANT|ARCENCIEL|METEORE] — ids : /vol liste"); return true; }
                Orbito o = Catalogue.get(a[1]);
                Mutation m = a.length > 2 ? Mutation.valueOf(a[2].toUpperCase(Locale.ROOT)) : Mutation.AUCUNE;
                Jeu.Spec sp = jeu.creer(o, m, new Location(jeu.w, Arene.TAPIS_X0 + 0.5, Arene.Y + 1, 0.5, -90, 0), Jeu.Etat.TAPIS);
                jeu.nommer(sp);
                Txt.send(s, o.nomComplet(m) + " <#E6EDF3>part sur le tapis.");
            }
            case "echange" -> {
                if (!(s instanceof Player p)) return true;
                if (a.length > 1 && a[1].equalsIgnoreCase("accepter")) jeu.echange.repondre(p, true);
                else if (a.length > 1 && a[1].equalsIgnoreCase("refuser")) jeu.echange.repondre(p, false);
                else if (a.length > 1 && Bukkit.getPlayerExact(a[1]) != null && !a[1].equalsIgnoreCase(p.getName())) jeu.echange.choisirMien(p, Bukkit.getPlayerExact(a[1]));
                else jeu.echange.ouvrirJoueurs(p);
            }
            case "pack" -> {
                Player cible = a.length > 1 && admin ? Bukkit.getPlayerExact(a[1]) : (s instanceof Player p ? p : null);
                if (cible == null) { Txt.send(s, "Joueur introuvable."); return true; }
                pack.envoyer(cible);
                Txt.send(s, "Pack de textures renvoyé à " + cible.getName() + ".");
            }
            case "oeufs" -> { if (s instanceof Player p) Oeufs.ouvrir(jeu, p); }
            case "musique" -> { if (s instanceof Player p) jeu.recompenses.basculerMusique(p); }
            case "tuto" -> {
                if (!(s instanceof Player p)) return true;
                Profil pr = jeu.profils.get(p.getUniqueId());
                if (pr == null) return true;
                if (pr.tuto >= Profil.TUTO_FINI) Txt.send(s, "Tu as déjà fini le tutoriel. Tape /vol pour l'aide.");
                else Tuto.annoncer(p, pr);
            }
            case "evenement" -> {
                if (!admin) return refus(s);
                if (a.length < 2) { Txt.send(s, "/vol evenement <nuit_doree|marchand_noir|geant|stop>"); return true; }
                if (a[1].equalsIgnoreCase("stop")) { if (jeu.evenements.actif != null) jeu.evenements.arreter(); return true; }
                try {
                    jeu.evenements.lancer(Evenements.Type.valueOf(a[1].toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException e) {
                    Txt.send(s, "Événements : nuit_doree, marchand_noir, geant, stop");
                }
            }
            case "liste" -> {
                for (Orbito o : Catalogue.TOUS.values())
                    s.sendMessage(Txt.mm("<#5A6270>" + o.id() + " <#E6EDF3>" + o.nomComplet(Mutation.AUCUNE) + " <#5A6270>[" + o.rarete().etiquette() + "<#5A6270>] " + Txt.cr(o.prix()) + " <#7CFC9A>+" + Txt.n(o.revenu()) + "/s"));
            }
            default -> {
                Txt.send(s, "<b>Vole mon Orbito</b>");
                s.sendMessage(Txt.mm("<#E6EDF3>• Clique un Orbito sur le <#62CFF4>Tapis stellaire</#62CFF4> pour l'acheter."));
                s.sendMessage(Txt.mm("<#E6EDF3>• Marche sur la <#E0B864>plaque dorée</#E0B864> de ta base pour encaisser."));
                s.sendMessage(Txt.mm("<#E6EDF3>• Clique l'Orbito d'un autre joueur pour le voler, ramène-le chez toi."));
                s.sendMessage(Txt.mm("<#E6EDF3>• Tape les voleurs pour qu'ils lâchent tout. Bouton bleu = <#62CFF4>bouclier</#62CFF4> 60 s."));
                s.sendMessage(Txt.mm("<#E6EDF3>• Clique ton Orbito pour l'améliorer (niv. 1 à 10) ou le revendre."));
                s.sendMessage(Txt.mm("<#E6EDF3>• L'étoile : objets, base, quêtes, œufs, Orbidex, titres, Ascension."));
                s.sendMessage(Txt.mm("<#E6EDF3>• /vol echange [joueur] · /vol oeufs · /vol musique · /vol tuto · /vol pack"));
                if (admin) s.sendMessage(Txt.mm("<#5A6270>Staff : /vol generer · donner <joueur> <montant> · meteores · evenement <type> · orbito <id> [mutation] · liste"));
            }
        }
        return true;
    }

    private boolean refus(CommandSender s) {
        Txt.send(s, "Réservé au staff.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String alias, String[] a) {
        List<String> l = new ArrayList<>();
        if (a.length == 1) {
            l.addAll(List.of("ascension", "echange", "oeufs", "musique", "tuto", "pack"));
            if (s.hasPermission("orbitvol.admin")) l.addAll(List.of("generer", "donner", "meteores", "evenement", "orbito", "liste"));
        } else if (a.length == 2 && a[0].equalsIgnoreCase("evenement")) {
            l.addAll(List.of("nuit_doree", "marchand_noir", "geant", "stop"));
        } else if (a.length == 2 && a[0].equalsIgnoreCase("echange")) {
            l.addAll(List.of("accepter", "refuser"));
            for (Player p : Bukkit.getOnlinePlayers()) l.add(p.getName());
        } else if (a.length == 2 && a[0].equalsIgnoreCase("orbito")) {
            l.addAll(Catalogue.TOUS.keySet());
        } else if (a.length == 3 && a[0].equalsIgnoreCase("orbito")) {
            for (Mutation m : Mutation.values()) if (m != Mutation.AUCUNE) l.add(m.name());
        }
        String debut = a[a.length - 1].toLowerCase(Locale.ROOT);
        l.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(debut));
        return l;
    }
}
