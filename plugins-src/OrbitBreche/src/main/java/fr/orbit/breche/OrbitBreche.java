package fr.orbit.breche;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * BRÈCHE : shooter tactique à agents de la salle d'arcade cachée du lobby (contrat docs/arcade-jeux.md).
 * Bleu contre Or, pose et désamorçage du Noyau, deux cartes construites par le code dans le monde vide « breche »,
 * cinq agents à quatre pouvoirs, bots en renfort. Pack servi sur le port 8802.
 */
public final class OrbitBreche extends JavaPlugin implements Listener {

    private Db db;
    private Profils profils;
    private Pack pack;
    private Jeux jeux;
    private Menus menus;
    private Objets objets;
    private World monde;
    private NamespacedKey cle, cleObjet;
    private Villager pnj;
    private TextDisplay pnjTexte;
    private final Set<UUID> teleportInterne = new HashSet<>();
    private final Map<UUID, Long> dernierClic = new HashMap<>();

    Db db() {
        return db;
    }

    Profils profils() {
        return profils;
    }

    Jeux jeux() {
        return jeux;
    }

    Menus menus() {
        return menus;
    }

    Objets objets() {
        return objets;
    }

    World monde() {
        return monde;
    }

    NamespacedKey cleObjet() {
        return cleObjet;
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        cle = new NamespacedKey(this, "jeu");
        cleObjet = new NamespacedKey(this, "objet");
        db = new Db(this);
        db.connect(Db.locate(this, getConfig().getString("base.fichier", "auto")));
        profils = new Profils(this);
        objets = new Objets(this);
        pack = new Pack(this);
        pack.enable();
        getServer().getPluginManager().registerEvents(pack, this);

        String nom = getConfig().getString("monde", "breche");
        monde = Bukkit.getWorld(nom);
        if (monde == null) {
            monde = new WorldCreator(nom).generator(new Vide()).generateStructures(false).createWorld();
        }
        if (monde == null) {
            getLogger().severe("Monde « " + nom + " » impossible à créer : jeu désactivé.");
            return;
        }
        monde.setTime(getConfig().getLong("heure", 6000));
        monde.setGameRule(org.bukkit.GameRules.ADVANCE_TIME, false);
        monde.setGameRule(org.bukkit.GameRules.ADVANCE_WEATHER, false);
        monde.setGameRule(org.bukkit.GameRules.SPAWN_MOBS, false);
        monde.setDifficulty(Difficulty.PEACEFUL);
        monde.setStorm(false);
        for (Entity e : monde.getEntities()) {
            if (e.getPersistentDataContainer().has(cle, PersistentDataType.BYTE)) {
                e.remove();
            }
        }
        jeux = new Jeux(this, monde);
        menus = new Menus(this);
        getServer().getPluginManager().registerEvents(menus, this);
        getServer().getPluginManager().registerEvents(new Ecoute(this), this);
        getServer().getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, jeux::tick, 1L, 1L);
        Bukkit.getScheduler().runTaskLater(this, () -> jeux.construire(false), 20L);
        Bukkit.getScheduler().runTaskTimer(this, this::garderPnj, 40L, 100L);
        for (Player p : Bukkit.getOnlinePlayers()) {
            profils.charger(p);
        }
        getLogger().info("BRÈCHE prête : monde « " + nom + " », " + Plan.toutes().size() + " cartes, " + Agent.values().length + " agents.");
    }

    @Override
    public void onDisable() {
        if (jeux != null) {
            jeux.arreterTout("redémarrage du serveur");
        }
        if (pnj != null) {
            pnj.remove();
        }
        if (pnjTexte != null) {
            pnjTexte.remove();
        }
        if (pack != null) {
            pack.shutdown();
        }
        if (db != null) {
            db.close();
        }
    }

    // ------------------------------------------------------------------ outils communs

    /** Marque une entité du jeu : jamais enregistrée, retirée au démarrage s'il en reste. */
    <T extends Entity> T mark(T e) {
        e.setPersistent(false);
        e.getPersistentDataContainer().set(cle, PersistentDataType.BYTE, (byte) 1);
        return e;
    }

    void teleporter(Player p, Location l) {
        teleportInterne.add(p.getUniqueId());
        try {
            p.teleport(l);
        } finally {
            teleportInterne.remove(p.getUniqueId());
        }
    }

    boolean teleportInterne(Player p) {
        return teleportInterne.contains(p.getUniqueId());
    }

    void rendre(Player p, Etat etat) {
        if (etat != null) {
            etat.rendre(p);
        }
        p.setGlowing(false);
    }

    private Location point(String key, double dx, double dy, double dz, float yaw) {
        World salle = Bukkit.getWorld(getConfig().getString("monde-salle", "casino"));
        if (salle == null) {
            return null;
        }
        return new Location(salle, getConfig().getDouble(key + ".x", dx), getConfig().getDouble(key + ".y", dy), getConfig().getDouble(key + ".z", dz),
                (float) getConfig().getDouble(key + ".yaw", yaw), 0f);
    }

    /** Devant la borne, face à elle (ou au point d'apparition du lobby si la salle d'arcade n'existe pas). */
    void versSalle(Player p) {
        Location l = point("retour", 17.5, 64, 407.5, -90);
        if (l == null) {
            l = Bukkit.getWorlds().get(0).getSpawnLocation();
        }
        teleporter(p, l);
    }

    // ------------------------------------------------------------------ PNJ (habillé en borne 3D par OrbitCasino)

    private void garderPnj() {
        Location at = point("pnj", 19.5, 64, 407.5, 90);
        if (at == null || !at.getWorld().isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) {
            return;
        }
        if (pnj != null && pnj.isValid() && pnjTexte != null && pnjTexte.isValid()) {
            return;
        }
        if (pnj != null) {
            pnj.remove();
        }
        if (pnjTexte != null) {
            pnjTexte.remove();
        }
        for (Entity e : at.getWorld().getNearbyEntities(at, 3, 4, 3)) {
            if (e.getPersistentDataContainer().has(cle, PersistentDataType.BYTE)) {
                e.remove();
            }
        }
        pnj = at.getWorld().spawn(at, Villager.class, CreatureSpawnEvent.SpawnReason.CUSTOM, e -> {
            mark(e);
            e.setAI(false);
            e.setSilent(true);
            e.setInvulnerable(true);
            e.setCollidable(false);
            e.setRemoveWhenFarAway(false);
            e.customName(Txt.mm("<#E0B864>Instructrice · BRÈCHE"));
            e.setCustomNameVisible(true);
            e.setProfession(Villager.Profession.WEAPONSMITH);
            e.setVillagerLevel(5);
            e.setRotation(at.getYaw(), 0f);
        });
        pnjTexte = at.getWorld().spawn(at.clone().add(0, 2.9, 0), TextDisplay.class, e -> {
            mark(e);
            e.text(Txt.mm(Txt.TITLE + "\n<#E6EDF3>Shooter tactique à agents\n<#9AA4B2>5 agents · 2 cartes · Bleu contre Or\n<#9AA4B2>Clic droit pour jouer"));
            e.setBillboard(Display.Billboard.CENTER);
            e.setAlignment(TextDisplay.TextAlignment.CENTER);
            e.setShadowed(true);
            e.setBackgroundColor(Color.fromARGB(150, 10, 12, 18));
            e.setBrightness(new Display.Brightness(15, 15));
            e.setLineWidth(240);
            e.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.9f, 0.9f, 0.9f), new AxisAngle4f()));
        });
        getLogger().info("PNJ de BRÈCHE posé dans la salle d'arcade (" + at.getX() + " " + at.getY() + " " + at.getZ() + ").");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPnj(PlayerInteractEntityEvent event) {
        if (pnj == null || !event.getRightClicked().getUniqueId().equals(pnj.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        Player p = event.getPlayer();
        long now = System.currentTimeMillis();
        Long last = dernierClic.put(p.getUniqueId(), now);
        if (event.getHand() != EquipmentSlot.HAND || (last != null && now - last < 400)) {
            return;
        }
        if (jeux != null) {
            menus.ouvrirAccueil(p);
        }
    }

    // ------------------------------------------------------------------ commandes

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (jeux == null) {
            Txt.send(sender, "<#E0484A>Jeu désactivé (monde introuvable).");
            return true;
        }
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        boolean admin = sender.hasPermission("orbitbreche.admin");
        switch (sub) {
            case "top", "classement" -> {
                profils.top(sender);
                return true;
            }
            case "etat", "état" -> {
                if (admin) {
                    Txt.send(sender, "Parties :" + jeux.etat());
                }
                return true;
            }
            case "generer", "générer" -> {
                if (admin) {
                    jeux.arreterTout("reconstruction des cartes");
                    jeux.construire(true);
                    Txt.send(sender, "Cartes reconstruites (voir la console).");
                }
                return true;
            }
            case "pnj" -> {
                if (admin) {
                    if (pnj != null) {
                        pnj.remove();
                    }
                    garderPnj();
                    Txt.send(sender, pnj != null && pnj.isValid() ? "PNJ reposé." : "<#E0484A>Salle d'arcade introuvable ou pas chargée.");
                }
                return true;
            }
            case "essai" -> {
                if (admin) {
                    int[] v = {2, 5, 1};   // niveau des bots, joueurs par équipe, vitesse
                    for (int i = 2; i < args.length && i < 5; i++) {
                        try {
                            v[i - 2] = Integer.parseInt(args[i]);
                        } catch (NumberFormatException e) {
                            // valeur par défaut gardée
                        }
                    }
                    Txt.send(sender, jeux.essai(args.length > 1 ? args[1] : "helios", v[0], v[1], v[2]));
                }
                return true;
            }
            case "arreter", "arrêter" -> {
                if (admin) {
                    jeux.arreterTout("arrêtée par un admin");
                    Txt.send(sender, "Parties arrêtées.");
                }
                return true;
            }
            default -> {
            }
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Console : /breche etat | generer | arreter | pnj | top");
            return true;
        }
        Partie pa = jeux.de(p);
        switch (sub) {
            case "" -> {
                if (pa == null) {
                    menus.ouvrirAccueil(p);
                } else {
                    Txt.send(p, "En partie : <#9AA4B2>" + pa.etat());
                }
            }
            case "jouer", "play" -> {
                int[] r = menus.reglages(p);
                int niveau = r[0], taille = r[1];
                try {
                    if (args.length > 2) {
                        niveau = Math.max(1, Math.min(3, Integer.parseInt(args[2])));
                    }
                    if (args.length > 3) {
                        taille = Integer.parseInt(args[3]) <= 3 ? 3 : 5;
                    }
                } catch (NumberFormatException e) {
                    Txt.send(p, "/breche jouer <#9AA4B2>[helios|vesta|hasard] [niveau 1-3] [3|5]");
                    return true;
                }
                jeux.jouer(p, args.length > 1 ? args[1] : "hasard", niveau, taille);
            }
            case "rejouer" -> jeux.rejouer(p);
            case "quitter", "abandon" -> {
                if (pa == null) {
                    Txt.send(p, "Tu n'es pas en partie.");
                } else {
                    jeux.quitter(p, true);
                    Txt.send(p, "Retour à la salle d'arcade.");
                }
            }
            case "lancer", "go" -> {
                if (pa != null && pa.phase == Partie.Phase.ATTENTE && (p.getUniqueId().equals(pa.hote) || admin)) {
                    pa.lancer();
                } else {
                    Txt.send(p, "<#9AA4B2>Seul le créateur de la partie peut la lancer, pendant l'attente.");
                }
            }
            case "agent" -> {
                if (pa != null && pa.phase == Partie.Phase.ATTENTE) {
                    menus.ouvrirAgents(p, pa);
                } else {
                    Txt.send(p, "<#9AA4B2>L'agent se choisit pendant l'attente, avant la première manche.");
                }
            }
            case "boutique", "shop" -> {
                if (pa != null) {
                    menus.ouvrirBoutique(p, pa);
                }
            }
            case "tp" -> {
                if (admin && args.length > 1 && jeux.cartes.get(args[1].toLowerCase(Locale.ROOT)) != null) {
                    Plan plan = jeux.cartes.get(args[1].toLowerCase(Locale.ROOT)).plan;
                    teleporter(p, new Location(monde, plan.cx(), Plan.Y + 8, plan.cz()));
                    p.setAllowFlight(true);
                    p.setFlying(true);
                }
            }
            default -> Txt.send(p, "/breche <#9AA4B2>[jouer [carte] [niveau] [3|5] · rejouer · quitter · lancer · agent · boutique · top]");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("jouer", "rejouer", "quitter", "lancer", "agent", "boutique", "top"));
            if (sender.hasPermission("orbitbreche.admin")) {
                subs.addAll(List.of("etat", "generer", "arreter", "pnj", "tp", "essai"));
            }
            for (String s : subs) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(s);
                }
            }
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("jouer") || args[0].equalsIgnoreCase("tp"))) {
            for (String s : new String[]{"helios", "vesta", "hasard"}) {
                if (s.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add(s);
                }
            }
        }
        return out;
    }
}
