package fr.orbit.staff;

import org.bukkit.command.PluginCommand;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.PreparedStatement;

/**
 * OrbitStaff 1.0 : outils du staff ORBIT, sur tous les serveurs du réseau.
 * Remplace l'ancienne version 0.1 (API Spring + bot) : tout passe par la MariaDB du réseau.
 */
public final class OrbitStaff extends JavaPlugin {

    private Db db;
    private String serveur;
    private Joueurs joueurs;
    private Sanctions sanctions;
    private Flux flux;
    private StaffMode staffMode;
    private Freeze freeze;
    private Combat combat;
    private Ponts ponts;
    private Saisie saisie;

    @Override
    public void onEnable() {
        migrerAncienneConfig();
        saveDefaultConfig();
        if (!new File(getDataFolder(), "sanctions.yml").exists()) {
            saveResource("sanctions.yml", false);
        }
        serveur = nomServeur();

        db = new Db(this);
        File acces = new File(getConfig().getString("base.fichier", "../../setup/db.properties"));
        if (!acces.isAbsolute()) {
            // Chemin relatif au dossier du serveur (lobby : server\, instances : instances\<nom>\).
            acces = new File(new File("").getAbsoluteFile(), acces.getPath());
        }
        if (!acces.isFile()) {
            // Lobby (server\) et instances (instances\<nom>\) ne sont pas à la même profondeur : on remonte.
            for (File d = new File("").getAbsoluteFile(); d != null; d = d.getParentFile()) {
                File essai = new File(d, "setup" + File.separator + "db.properties");
                if (essai.isFile()) {
                    acces = essai;
                    break;
                }
            }
        }
        db.connect(acces);

        joueurs = new Joueurs(this);
        sanctions = new Sanctions(this);
        flux = new Flux(this);
        staffMode = new StaffMode(this);
        freeze = new Freeze(this);
        combat = new Combat(this);
        ponts = new Ponts(this);
        saisie = new Saisie(this);

        ecouter(joueurs, sanctions, staffMode, freeze, combat, saisie, new Outils(this), new Inspection(this), new Menu.Ecoute());

        Commandes commandes = new Commandes(this);
        for (String c : new String[]{"staff", "panel", "stats", "catalogue", "freeze", "invsee", "endersee", "vanish", "warn", "mute",
                "unmute", "kick", "tempban", "ban", "unban", "sanction", "historique", "note", "report", "reports", "sc", "alertes", "orbitstaff"}) {
            PluginCommand pc = getCommand(c);
            if (pc != null) {
                pc.setExecutor(commandes);
                pc.setTabCompleter(commandes);
            }
        }
        getLogger().info("OrbitStaff actif sur « " + serveur + " » (base " + (db.available() ? "OK" : "COUPÉE") + ").");
    }

    @Override
    public void onDisable() {
        if (staffMode != null) {
            staffMode.toutRendre();
        }
        if (joueurs != null) {
            joueurs.arreter();
        }
        if (db != null) {
            db.close();
        }
    }

    private void ecouter(Listener... ls) {
        for (Listener l : ls) {
            getServer().getPluginManager().registerEvents(l, this);
        }
    }

    /** L'ancienne config (0.1, API Spring) est mise de côté pour repartir sur la nouvelle. */
    private void migrerAncienneConfig() {
        File f = new File(getDataFolder(), "config.yml");
        if (f.isFile() && getConfig().contains("api.base-url")) {
            File ancien = new File(getDataFolder(), "ancienne-version-0.1");
            ancien.mkdirs();
            for (String n : new String[]{"config.yml", "sanctions.yml", "messages_fr.yml", "messages_en.yml", "staff-items.yml", "retention.yml"}) {
                File x = new File(getDataFolder(), n);
                if (x.isFile()) {
                    x.renameTo(new File(ancien, n));
                }
            }
            reloadConfig();
            getLogger().info("Ancienne configuration 0.1 rangée dans ancienne-version-0.1/.");
        }
    }

    private String nomServeur() {
        String s = getConfig().getString("serveur", "");
        if (s != null && !s.isBlank()) {
            return s.trim();
        }
        String dossier = new File("").getAbsoluteFile().getName();
        return dossier.equalsIgnoreCase("server") ? "lobby" : dossier;
    }

    /** Journal des actions du staff (table staff_logs), sans attendre. */
    public void log(String staff, String action, String cible, String detail) {
        String srv = serveur;
        String d = detail == null ? null : detail.length() > 250 ? detail.substring(0, 250) : detail;
        db.fire(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO staff_logs (staff, action, cible, detail, serveur) VALUES (?,?,?,?,?)")) {
                ps.setString(1, staff);
                ps.setString(2, action);
                ps.setString(3, cible);
                ps.setString(4, d);
                ps.setString(5, srv);
                ps.executeUpdate();
            }
            return null;
        });
    }

    public Db db() {
        return db;
    }

    public String serveur() {
        return serveur;
    }

    public Joueurs joueurs() {
        return joueurs;
    }

    public Sanctions sanctions() {
        return sanctions;
    }

    public Flux flux() {
        return flux;
    }

    public StaffMode staffMode() {
        return staffMode;
    }

    public Freeze freeze() {
        return freeze;
    }

    public Combat combat() {
        return combat;
    }

    public Ponts ponts() {
        return ponts;
    }

    public Saisie saisie() {
        return saisie;
    }
}
