package fr.orbit.survie;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * Événements automatiques de la survie, un à la fois, lancés tout seuls à intervalle régulier quand il y a
 * assez de joueurs : concours de pêche (le plus de poissons), largage (un tonneau de butin tombe à des
 * coordonnées annoncées, le premier qui l'ouvre gagne tout) et boss (un monstre renforcé, la prime est partagée
 * selon les dégâts). Le monde de survie n'est pas abîmé : le tonneau du largage est retiré à la fin.
 */
public final class Evenements implements Listener, TabExecutor {

    enum Type { PECHE, LARGAGE, BOSS }

    record Butin(Material objet, int quantite, double chance) {}

    private final OrbitSurvie plugin;
    private final NamespacedKey cleBoss;
    private Type enCours;
    private boolean recherche;
    private long debut, fin, prochain;
    private BossBar barre;
    private String titre = "";

    private final Map<UUID, Integer> prises = new HashMap<>();
    private final Map<UUID, String> noms = new HashMap<>();
    private Location coffre;
    private BlockData avant;
    private List<ItemStack> contenu = List.of();
    private UUID boss;
    private Chunk bossChunk;
    private final Map<UUID, Double> degats = new HashMap<>();

    public Evenements(OrbitSurvie plugin) {
        this.plugin = plugin;
        this.cleBoss = new NamespacedKey(plugin, "boss");
        prochain = System.currentTimeMillis() + Math.min(15, intervalle()) * 60_000L;
    }

    private int intervalle() {
        return Math.max(1, plugin.getConfig().getInt("evenements.intervalle-minutes", 60));
    }

    private World survie() {
        return Bukkit.getWorld(plugin.getConfig().getString("survie.monde", "world"));
    }

    private static void annoncer(String mini) {
        Bukkit.broadcast(Msg.raw(Msg.OR + "<bold>ÉVÉNEMENT</bold> <dark_gray>» <gray>" + mini));
    }

    private List<Butin> butin(String chemin) {
        List<Butin> out = new ArrayList<>();
        for (String ligne : plugin.getConfig().getStringList(chemin)) {
            String[] t = ligne.trim().split("\\s+");
            Material m = Material.matchMaterial(t[0]);
            try {
                if (m != null && m.isItem()) {
                    out.add(new Butin(m, t.length > 1 ? Integer.parseInt(t[1]) : 1, t.length > 2 ? Double.parseDouble(t[2]) : 100));
                    continue;
                }
            } catch (NumberFormatException ignored) {
            }
            plugin.getLogger().warning("Événements : ligne de butin ignorée « " + ligne + " »");
        }
        return out;
    }

    private static List<ItemStack> tirer(List<Butin> butin) {
        List<ItemStack> out = new ArrayList<>();
        for (Butin b : butin) {
            if (ThreadLocalRandom.current().nextDouble(100) < b.chance()) {
                out.add(new ItemStack(b.objet(), Math.max(1, b.quantite())));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ Horloge

    /** Chaque seconde. */
    public void tic() {
        long now = System.currentTimeMillis();
        if (enCours == null) {
            if (recherche || now < prochain || !plugin.getConfig().getBoolean("evenements.actif", true)) {
                return;
            }
            if (Bukkit.getOnlinePlayers().size() < plugin.getConfig().getInt("evenements.joueurs-min", 2)) {
                prochain = now + 5 * 60_000L;
                return;
            }
            Type[] types = Type.values();
            lancer(types[ThreadLocalRandom.current().nextInt(types.length)], null);
            return;
        }
        if (now >= fin) {
            switch (enCours) {
                case PECHE -> finPeche();
                case LARGAGE -> {
                    annoncer("Personne n'a trouvé le largage : il a été emporté.");
                    terminer();
                }
                case BOSS -> {
                    annoncer("Le boss s'est retiré, invaincu.");
                    terminer();
                }
            }
            return;
        }
        if (enCours == Type.BOSS) {
            Entity e = boss == null ? null : Bukkit.getEntity(boss);
            if (e == null || !e.isValid()) {
                annoncer("Le boss a disparu dans la nature.");
                terminer();
                return;
            }
            titre = Msg.OR + "<bold>BOSS</bold> <gray>en <white>" + e.getLocation().getBlockX() + " " + e.getLocation().getBlockZ();
        } else if (enCours == Type.LARGAGE && coffre != null && coffre.isChunkLoaded()) {
            coffre.getWorld().spawnParticle(Particle.END_ROD, coffre.clone().add(0.5, 6, 0.5), 30, 0.15, 6, 0.15, 0.01);
        }
        if (barre != null) {
            long reste = Math.max(0, fin - now);
            barre.name(Msg.raw(titre + " <dark_gray>· <white>" + String.format("%d:%02d", reste / 60_000, reste / 1000 % 60)));
            barre.progress(Math.max(0f, Math.min(1f, reste / (float) Math.max(1, fin - debut))));
            Bukkit.getOnlinePlayers().forEach(p -> p.showBossBar(barre));
        }
    }

    public void lancer(Type type, CommandSender demandeur) {
        if (enCours != null || recherche) {
            if (demandeur != null) {
                Msg.send(demandeur, "<red>Un événement est déjà en cours.");
            }
            return;
        }
        switch (type) {
            case PECHE -> {
                prises.clear();
                noms.clear();
                demarrer(Type.PECHE, plugin.getConfig().getInt("evenements.peche.minutes", 10),
                        Msg.BLEU + "<bold>CONCOURS DE PÊCHE</bold>", BossBar.Color.BLUE);
                annoncer("Concours de pêche ! Pêche le plus de " + Msg.BLEU + "poissons<gray> en <white>"
                        + plugin.getConfig().getInt("evenements.peche.minutes", 10) + " min<gray>, n'importe où dans la survie.");
            }
            case LARGAGE -> chercher(loc -> {
                Block b = loc.getBlock();
                coffre = b.getLocation();
                avant = b.getBlockData();
                contenu = tirer(butin("evenements.largage.butin"));
                b.setType(Material.BARREL, false);
                demarrer(Type.LARGAGE, plugin.getConfig().getInt("evenements.largage.minutes", 15),
                        Msg.OR + "<bold>LARGAGE</bold> <gray>en <white>" + b.getX() + " " + b.getY() + " " + b.getZ(), BossBar.Color.YELLOW);
                annoncer("Un largage est tombé en <white>" + b.getX() + " " + b.getY() + " " + b.getZ()
                        + "<gray> (monde de survie). Le premier qui ouvre le tonneau prend tout !");
            }, demandeur);
            case BOSS -> chercher(loc -> {
                List<Map<?, ?>> types = plugin.getConfig().getMapList("evenements.boss.types");
                Map<?, ?> choix = types.isEmpty() ? Map.of() : types.get(ThreadLocalRandom.current().nextInt(types.size()));
                EntityType et;
                try {
                    et = EntityType.valueOf(String.valueOf(choix.get("type")).toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    et = EntityType.RAVAGER;
                }
                String nom = choix.get("nom") == null ? "Colosse" : String.valueOf(choix.get("nom"));
                double vie = choix.get("vie") instanceof Number n ? n.doubleValue() : 300;
                if (!(loc.getWorld().spawnEntity(loc, et) instanceof LivingEntity e)) {
                    if (demandeur != null) {
                        Msg.send(demandeur, "<red>Type de boss invalide : " + et);
                    }
                    return;
                }
                AttributeInstance pv = e.getAttribute(Attribute.MAX_HEALTH);
                if (pv != null) {
                    pv.setBaseValue(vie);
                    e.setHealth(pv.getValue());
                }
                e.customName(Msg.raw("<red><bold>" + nom));
                e.setCustomNameVisible(true);
                e.setGlowing(true);
                e.setPersistent(false);
                e.setRemoveWhenFarAway(false);
                e.getPersistentDataContainer().set(cleBoss, PersistentDataType.BYTE, (byte) 1);
                boss = e.getUniqueId();
                bossChunk = loc.getChunk();
                bossChunk.addPluginChunkTicket(plugin);
                degats.clear();
                demarrer(Type.BOSS, plugin.getConfig().getInt("evenements.boss.minutes", 20),
                        Msg.OR + "<bold>BOSS</bold> <gray>en <white>" + loc.getBlockX() + " " + loc.getBlockZ(), BossBar.Color.RED);
                annoncer("<red><bold>" + nom + "</bold><gray> est apparu en <white>" + loc.getBlockX() + " " + loc.getBlockY() + " "
                        + loc.getBlockZ() + "<gray> (monde de survie). Prime de " + Msg.argent(plugin.getConfig().getDouble("evenements.boss.prime", 3000))
                        + " partagée selon les dégâts !");
            }, demandeur);
        }
    }

    private void demarrer(Type type, int minutes, String titre, BossBar.Color couleur) {
        enCours = type;
        debut = System.currentTimeMillis();
        fin = debut + Math.max(1, minutes) * 60_000L;
        this.titre = titre;
        barre = BossBar.bossBar(Msg.raw(titre), 1f, couleur, BossBar.Overlay.PROGRESS);
        Bukkit.getOnlinePlayers().forEach(p -> p.playSound(p.getLocation(), Sound.BLOCK_BELL_USE, 0.8f, 1f));
    }

    /** Remet tout en ordre (tonneau retiré, boss retiré, barre cachée) et programme le prochain événement. */
    public void terminer() {
        if (barre != null) {
            BossBar b = barre;
            Bukkit.getOnlinePlayers().forEach(p -> p.hideBossBar(b));
            barre = null;
        }
        if (coffre != null) {
            if (coffre.getBlock().getType() == Material.BARREL) {
                coffre.getBlock().setBlockData(avant == null ? Material.AIR.createBlockData() : avant, false);
            }
            coffre = null;
            avant = null;
            contenu = List.of();
        }
        if (boss != null) {
            Entity e = Bukkit.getEntity(boss);
            if (e != null) {
                e.remove();
            }
            boss = null;
        }
        if (bossChunk != null) {
            bossChunk.removePluginChunkTicket(plugin);
            bossChunk = null;
        }
        degats.clear();
        prises.clear();
        enCours = null;
        prochain = System.currentTimeMillis() + intervalle() * 60_000L;
    }

    /** Cherche un point au sol, hors de toute base, dans le monde de survie. */
    private void chercher(Consumer<Location> trouve, CommandSender demandeur) {
        World w = survie();
        if (w == null) {
            if (demandeur != null) {
                Msg.send(demandeur, "<red>Monde de survie introuvable.");
            }
            return;
        }
        recherche = true;
        essai(w, 0, trouve, demandeur);
    }

    private void essai(World w, int n, Consumer<Location> trouve, CommandSender demandeur) {
        if (n >= 20) {
            recherche = false;
            prochain = System.currentTimeMillis() + 5 * 60_000L;
            if (demandeur != null) {
                Msg.send(demandeur, "<red>Aucun endroit trouvé pour l'événement, réessaie.");
            }
            return;
        }
        int min = plugin.getConfig().getInt("survie.rtp-min", 300), max = plugin.getConfig().getInt("survie.rtp-max", 3000);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double a = r.nextDouble(Math.PI * 2), d = min + r.nextDouble() * (max - min);
        Location centre = w.getSpawnLocation();
        int x = (int) Math.round(centre.getX() + Math.cos(a) * d), z = (int) Math.round(centre.getZ() + Math.sin(a) * d);
        if (!w.getWorldBorder().isInside(new Location(w, x, 64, z))) {
            essai(w, n + 1, trouve, demandeur);
            return;
        }
        w.getChunkAtAsync(x >> 4, z >> 4, true).thenAccept(chunk -> {
            Block sol = w.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            Block dessus = sol.getRelative(0, 1, 0);
            if (!sol.getType().isSolid() || sol.isLiquid() || !dessus.getType().isAir() || !dessus.getRelative(0, 1, 0).getType().isAir()
                    || plugin.bases().a(dessus.getLocation()) != null) {
                essai(w, n + 1, trouve, demandeur);
                return;
            }
            recherche = false;
            if (enCours == null) {
                trouve.accept(dessus.getLocation().add(0.5, 0, 0.5));
            }
        }).exceptionally(err -> {
            Bukkit.getScheduler().runTask(plugin, () -> essai(w, n + 1, trouve, demandeur));
            return null;
        });
    }

    // ------------------------------------------------------------------ Concours de pêche

    /** Appelé par les métiers à chaque poisson pêché. */
    public void poisson(Player p) {
        if (enCours != Type.PECHE) {
            return;
        }
        int n = prises.merge(p.getUniqueId(), 1, Integer::sum);
        noms.put(p.getUniqueId(), p.getName());
        p.sendActionBar(Msg.raw(Msg.BLEU + "Concours de pêche <dark_gray>· <white>" + n + " <gray>poisson" + (n > 1 ? "s" : "")));
    }

    private void finPeche() {
        List<Map.Entry<UUID, Integer>> ordre = new ArrayList<>(prises.entrySet());
        ordre.sort((a, b) -> b.getValue() - a.getValue());
        if (ordre.isEmpty()) {
            annoncer("Concours de pêche terminé : personne n'a rien pris.");
        } else {
            annoncer("Concours de pêche terminé !");
            List<Double> lots = plugin.getConfig().getDoubleList("evenements.peche.lots");
            for (int i = 0; i < ordre.size() && i < lots.size(); i++) {
                UUID u = ordre.get(i).getKey();
                String nom = noms.getOrDefault(u, "?");
                if (plugin.eco().active()) {
                    plugin.eco().crediter(u, nom, lots.get(i));
                }
                Bukkit.broadcast(Msg.raw("  " + (i == 0 ? Msg.OR : "<gray>") + (i + 1) + ". <white>" + nom + " <dark_gray>— <white>"
                        + ordre.get(i).getValue() + " <gray>poissons <dark_gray>→ " + Msg.argent(lots.get(i))));
            }
        }
        terminer();
    }

    // ------------------------------------------------------------------ Largage

    private boolean estCoffre(Block b) {
        return coffre != null && b.getWorld().equals(coffre.getWorld()) && b.getX() == coffre.getBlockX()
                && b.getY() == coffre.getBlockY() && b.getZ() == coffre.getBlockZ();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onOuvre(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null || !estCoffre(e.getClickedBlock())) {
            return;
        }
        e.setCancelled(true);
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) {
            return;
        }
        Player p = e.getPlayer();
        List<ItemStack> lot = contenu;
        contenu = List.of();
        lot.forEach(it -> Objets.donner(p, it));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.8f);
        annoncer("<white>" + p.getName() + "<gray> a trouvé le largage et repart avec " + Msg.OR + lot.size() + " lots<gray> !");
        terminer();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCasse(BlockBreakEvent e) {
        if (estCoffre(e.getBlock())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onExplosion(EntityExplodeEvent e) {
        e.blockList().removeIf(this::estCoffre);
    }

    // ------------------------------------------------------------------ Boss

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDegat(EntityDamageByEntityEvent e) {
        if (boss == null || !e.getEntity().getUniqueId().equals(boss)) {
            return;
        }
        Player p = e.getDamager() instanceof Player pl ? pl
                : e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player pl ? pl : null;
        if (p != null) {
            degats.merge(p.getUniqueId(), e.getFinalDamage(), Double::sum);
            noms.put(p.getUniqueId(), p.getName());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMort(EntityDeathEvent e) {
        if (boss == null || !e.getEntity().getUniqueId().equals(boss)) {
            return;
        }
        e.getDrops().addAll(tirer(butin("evenements.boss.butin")));
        double total = degats.values().stream().mapToDouble(Double::doubleValue).sum();
        double prime = plugin.getConfig().getDouble("evenements.boss.prime", 3000);
        annoncer("Le boss est vaincu !");
        if (total > 0) {
            List<Map.Entry<UUID, Double>> ordre = new ArrayList<>(degats.entrySet());
            ordre.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
            for (Map.Entry<UUID, Double> en : ordre) {
                double part = Math.floor(prime * en.getValue() / total);
                String nom = noms.getOrDefault(en.getKey(), "?");
                if (part >= 1 && plugin.eco().active()) {
                    plugin.eco().crediter(en.getKey(), nom, part);
                    Bukkit.broadcast(Msg.raw("  <white>" + nom + " <dark_gray>— <gray>" + Math.round(100 * en.getValue() / total)
                            + " % des dégâts <dark_gray>→ " + Msg.argent(part)));
                }
            }
        }
        boss = null; // déjà mort : rien à retirer
        terminer();
    }

    // ------------------------------------------------------------------ /evenement

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        String sub = a.length == 0 ? "" : a[0].toLowerCase(Locale.ROOT);
        if (sub.equals("lancer") && s.hasPermission("survie.admin")) {
            if (a.length < 2) {
                Msg.send(s, "<red>Usage : /evenement lancer <peche|largage|boss>");
                return true;
            }
            try {
                lancer(Type.valueOf(a[1].toUpperCase(Locale.ROOT)), s);
            } catch (IllegalArgumentException e) {
                Msg.send(s, "<red>Type inconnu : peche, largage ou boss.");
            }
            return true;
        }
        if (sub.equals("stop") && s.hasPermission("survie.admin")) {
            if (enCours == null) {
                Msg.send(s, "<red>Aucun événement en cours.");
            } else {
                annoncer("L'événement est arrêté par un administrateur.");
                terminer();
            }
            return true;
        }
        if (enCours == null) {
            long reste = prochain - System.currentTimeMillis();
            Msg.send(s, "Aucun événement en cours. " + (!plugin.getConfig().getBoolean("evenements.actif", true)
                    ? "Les événements automatiques sont coupés."
                    : "Prochain dans environ <white>" + Bases.duree(Math.max(60_000, reste)) + "<gray> (s'il y a au moins <white>"
                    + plugin.getConfig().getInt("evenements.joueurs-min", 2) + "<gray> joueurs)."));
            Msg.send(s, "Concours de pêche, largage de butin, boss : annoncés dans le chat.");
        } else {
            Msg.send(s, "En cours : " + titre + "<gray>, encore <white>" + Bases.duree(fin - System.currentTimeMillis()) + "<gray>.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (s.hasPermission("survie.admin")) {
            if (a.length == 1) {
                out.addAll(List.of("lancer", "stop"));
            } else if (a.length == 2 && a[0].equalsIgnoreCase("lancer")) {
                out.addAll(List.of("peche", "largage", "boss"));
            }
        }
        out.removeIf(x -> !x.startsWith(a[a.length - 1].toLowerCase(Locale.ROOT)));
        return out;
    }
}
