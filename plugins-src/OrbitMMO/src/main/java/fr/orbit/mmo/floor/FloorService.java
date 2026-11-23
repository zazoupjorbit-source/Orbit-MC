package fr.orbit.mmo.floor;

import fr.orbit.mmo.Keys;
import fr.orbit.mmo.OrbitMMO;
import fr.orbit.mmo.player.PlayerData;
import fr.orbit.mmo.registry.Defs.FloorDef;
import fr.orbit.mmo.ui.Menus;
import fr.orbit.mmo.util.Fx;
import fr.orbit.mmo.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/** Les étages : mondes, ville (zone sûre), porte de téléportation, salle du boss, progression du serveur. */
public final class FloorService implements Listener {

    public static final String PREFIX = "reborn_etage_";

    private final OrbitMMO plugin;
    private final File stateFile;
    /** Record du serveur : le plus haut étage dont le boss est tombé, toutes guildes confondues. */
    private int highestCleared;
    /** Socle : dernier étage tenu pour franchi par tout le monde (posé par /mmoadmin ouvrir). */
    private int base;
    private final Set<Integer> built = new HashSet<>();
    /** Étages dont la ville a reçu le forgeron, l'alchimiste et le prêtre. */
    private final Set<Integer> townNpcs = new HashSet<>();
    /** Étages dont la ville a reçu le comptoir de l'hôtel des ventes. */
    private final Set<Integer> marketStall = new HashSet<>();
    /** Étages où l'établi d'Ysolde (runiste) est posé. */
    private final Set<Integer> runeStall = new HashSet<>();
    private final Map<String, Long> greetCooldown = new HashMap<>();
    private int lifeTick;
    private final Map<Integer, Long> bossReady = new HashMap<>();
    private final Map<UUID, Long> gateCooldown = new HashMap<>();
    private final Map<UUID, Long> guardWarn = new HashMap<>();

    public FloorService(OrbitMMO plugin) {
        this.plugin = plugin;
        this.stateFile = new File(plugin.getDataFolder(), "state.yml");
        loadState();
    }

    /** Charge les étages déjà ouverts (et crée l'étage 1 au premier démarrage). */
    public void enable() {
        for (int n = 1; n <= Math.min(maxFloor(), highestCleared + 1); n++) {
            world(n);
        }
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::gateFx, 40L, 10L);
        // Les PNJ de ville deviennent des mannequins à skin (les anciens sont remplacés sur place).
        fr.orbit.mmo.ambient.TownFolk.start(plugin);
        // Fin des effets achetés en ville (potions, parchemins, essences, bénédiction).
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> plugin.progression().tickBoosts(), 20L, 20L);
    }

    public int radius() {
        return Math.max(90, plugin.getConfig().getInt("floors.radius", 160));
    }

    public int townRadius() {
        return 28;
    }

    public int arenaRadius() {
        return 18;
    }

    public int arenaX() {
        return (int) Math.round(radius() * 0.62);
    }

    public int maxFloor() {
        return plugin.registry().maxFloor();
    }

    public FloorDef def(int n) {
        return plugin.registry().floor(n);
    }

    public int highestCleared() {
        return highestCleared;
    }

    public void setHighestCleared(int n) {
        highestCleared = Math.max(0, Math.min(maxFloor(), n));
        saveState();
    }

    /** Dernier étage tenu pour franchi par tout le monde : chacun peut entrer jusqu'à l'étage suivant. */
    public int base() {
        return base;
    }

    public void setBase(int n) {
        base = Math.max(0, Math.min(maxFloor(), n));
        highestCleared = Math.max(highestCleared, base);
        saveState();
    }

    /**
     * Dernier étage franchi qui compte pour ce joueur : le socle du serveur, les boss qu'il a vaincus lui-même, ou
     * ceux de sa guilde (une guilde ouvre ses étages à tous ses membres).
     */
    public int cleared(PlayerData d) {
        int n = base;
        if (d != null) {
            n = Math.max(n, d.towerCleared);
            fr.orbit.mmo.guild.Guild g = plugin.guilds() == null ? null : plugin.guilds().of(d.id);
            if (g != null) {
                n = Math.max(n, g.towerCleared());
            }
        }
        return Math.min(maxFloor(), n);
    }

    /** Étage ouvert à ce joueur : le dernier qu'il a franchi (lui, sa guilde ou le socle), plus un. */
    public boolean unlocked(PlayerData d, int n) {
        return n >= 1 && n <= Math.min(maxFloor(), cleared(d) + 1);
    }

    public long bossReadyAt(int floor) {
        return bossReady.getOrDefault(floor, 0L);
    }

    public void setBossReadyAt(int floor, long at) {
        bossReady.put(floor, at);
        saveState();
    }

    public int floorOf(World w) {
        if (w == null || !w.getName().startsWith(PREFIX)) {
            return 0;
        }
        try {
            return Integer.parseInt(w.getName().substring(PREFIX.length()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Monde de l'étage, chargé ou généré à la demande. */
    public World world(int n) {
        String name = PREFIX + n;
        World w = Bukkit.getWorld(name);
        if (w != null) {
            return w;
        }
        FloorDef def = def(n);
        long seed = 7919L * n + 1337L;
        plugin.getLogger().info("Chargement de l'étage " + n + " (" + def.name() + ")…");
        w = new WorldCreator(name)
                .environment(World.Environment.NORMAL)
                .generator(new FloorGenerator(def.theme(), radius(), arenaX(), seed))
                .generateStructures(false)
                .seed(seed)
                .createWorld();
        if (w == null) {
            plugin.getLogger().severe("Impossible de créer le monde " + name);
            return null;
        }
        setup(w);
        if (built.add(n)) {
            FloorBuilder.build(w, def, townRadius(), arenaX(), arenaRadius());
            saveState();
        }
        if (townNpcs.add(n)) {
            FloorBuilder.townNpcs(w);
            saveState();
        }
        if (marketStall.add(n)) {
            FloorBuilder.marketStall(w);
            saveState();
        }
        if (runeStall.add(n)) {
            FloorBuilder.runeStall(w);
            saveState();
        }
        w.getChunkAt(0, 0).addPluginChunkTicket(plugin);
        return w;
    }

    private void setup(World w) {
        w.setDifficulty(Difficulty.NORMAL);
        w.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        w.setGameRule(GameRule.DO_PATROL_SPAWNING, false);
        w.setGameRule(GameRule.DO_TRADER_SPAWNING, false);
        w.setGameRule(GameRule.DO_WARDEN_SPAWNING, false);
        w.setGameRule(GameRule.DO_INSOMNIA, false);
        w.setGameRule(GameRule.NATURAL_REGENERATION, false);
        w.setGameRule(GameRule.KEEP_INVENTORY, true);
        w.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
        w.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        w.setGameRule(GameRule.SHOW_DEATH_MESSAGES, false);
        w.setGameRule(GameRule.DO_FIRE_TICK, false);
        w.setGameRule(GameRule.MOB_GRIEFING, false);
        w.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        w.setGameRule(GameRule.SPAWN_RADIUS, 0);
        w.setStorm(false);
        w.setThundering(false);
        w.setSpawnLocation(0, FloorGenerator.SURFACE + 1, 5);
    }

    public Location town(World w) {
        return new Location(w, 0.5, FloorGenerator.SURFACE + 1, 5.5, 180f, 0f);
    }

    public Location arenaCenter(World w) {
        return new Location(w, arenaX() + 0.5, FloorGenerator.SURFACE + 1, 0.5);
    }

    /** PNJ de ville (forgeron, alchimiste, prêtre, runiste, hôtel des ventes) posé hors d'un étage : capitales du continent. */
    public static List<org.bukkit.entity.Entity> spawnTownNpc(World w, String tag, Location at) {
        return FloorBuilder.townNpc(w, tag, at.getX(), at.getY(), at.getZ(), at.getYaw());
    }

    public boolean isSafe(Location l) {
        if (l == null) {
            return false;
        }
        // Sur le continent, les zones sûres sont celles des capitales, de la ville et des villages.
        if (plugin.continent() != null && plugin.continent().isContinent(l.getWorld())) {
            return plugin.continent().isSafe(l);
        }
        if (floorOf(l.getWorld()) <= 0) {
            return false;
        }
        double r = townRadius();
        return l.getX() * l.getX() + l.getZ() * l.getZ() <= r * r;
    }

    public boolean inArena(Location l) {
        if (l == null || floorOf(l.getWorld()) <= 0) {
            return false;
        }
        double dx = l.getX() - (arenaX() + 0.5);
        double dz = l.getZ() - 0.5;
        return dx * dx + dz * dz <= arenaRadius() * arenaRadius();
    }

    /** Téléporte à la ville d'un étage, avec la colonne de lumière bleue. */
    public void teleport(Player p, int n, boolean effects) {
        World w = world(n);
        if (w == null) {
            Text.msg(p, "<#FF5C5C>Cet étage est indisponible.");
            return;
        }
        PlayerData d = plugin.players().get(p);
        d.floor = n;
        Location target = town(w);
        if (effects) {
            Fx.teleport(p.getLocation());
        }
        p.teleportAsync(target).thenAccept(ok -> {
            if (!ok || !p.isOnline() || !effects) {
                return;
            }
            Fx.teleport(target);
            FloorDef def = def(n);
            Text.title(p, "<#7FD4FF><b>Étage " + n + "</b>",
                    "<#D7DCE2>" + Text.esc(def.town()) + " <#5A6270>·</#5A6270> " + Text.esc(def.name()), 5, 50, 15);
        });
    }

    /**
     * Cristal de retour : dans le Pilier des Astres (les cent étages), il ramène à la ville de l'étage ; partout ailleurs, à la capitale de sa faction.
     *
     * @return le nom du lieu d'arrivée
     */
    public String recall(Player p, PlayerData d) {
        int here = floorOf(p.getWorld());
        if (here <= 0 && plugin.continent() != null && d.faction != null) {
            String home = plugin.continent().sendHome(p, d.faction.id, true);
            if (home != null) {
                return home;
            }
        }
        int n = here > 0 ? here : d.floor;
        n = unlocked(d, n) ? n : 1;
        teleport(p, n, true);
        return def(n).town();
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Location to = e.getTo();
        Location from = e.getFrom();
        if (to.getBlockX() == from.getBlockX() && to.getBlockY() == from.getBlockY() && to.getBlockZ() == from.getBlockZ()) {
            return;
        }
        if (floorOf(to.getWorld()) <= 0) {
            return;
        }
        Player p = e.getPlayer();
        PlayerData d = plugin.players().loaded(p.getUniqueId());
        if (d == null || d.classId == null) {
            return;
        }
        long now = System.currentTimeMillis();
        // Passage sous l'arche de la porte de téléportation.
        if (Math.abs(to.getX() - 0.5) <= 1.4 && Math.abs(to.getZ() - 0.5) <= 0.8
                && to.getY() >= FloorGenerator.SURFACE + 0.5 && to.getY() <= FloorGenerator.SURFACE + 5) {
            Long cd = gateCooldown.get(p.getUniqueId());
            if (cd == null || cd < now) {
                gateCooldown.put(p.getUniqueId(), now + 4000);
                // Entré par un donjon du continent : l'arche y ramène. Sinon, elle ouvre le choix de l'étage.
                if (plugin.continent() == null || !plugin.continent().leaveFloor(p)) {
                    Menus.floors(plugin, p, true);
                }
            }
        }
    }

    /** Étiquette posée par l'ancien plugin (RebornMMO) sur les PNJ déjà construits. */
    private static final org.bukkit.NamespacedKey LEGACY_NPC = new org.bukkit.NamespacedKey("rebornmmo", "npc");

    @EventHandler
    public void onInteractNpc(PlayerInteractEntityEvent e) {
        var pdc = e.getRightClicked().getPersistentDataContainer();
        String tag = pdc.get(Keys.NPC, PersistentDataType.STRING);
        if (tag == null) {
            tag = pdc.get(LEGACY_NPC, PersistentDataType.STRING);
            if (tag == null) {
                return;
            }
            // Migration : le PNJ prend l'étiquette d'OrbitMMO.
            pdc.set(Keys.NPC, PersistentDataType.STRING, tag);
            pdc.remove(LEGACY_NPC);
        }
        e.setCancelled(true);
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) {
            return;
        }
        Player p = e.getPlayer();
        // Aubergiste (inn.InnService) : sa réplique et son menu (lier le cristal de téléportation).
        if (tag.equals(fr.orbit.mmo.inn.InnService.TAG)) {
            if (plugin.inns() != null) {
                plugin.inns().talk(p, e.getRightClicked());
            }
            return;
        }
        // La phrase du PNJ est préparée avant d'ouvrir son menu : avec le pack, le menu l'affiche dans sa carte.
        Greeting greeting = prepareGreeting(p, tag);
        switch (tag) {
            case "merchant" -> fr.orbit.mmo.ui.MarketMenus.open(plugin, p);
            case "blacksmith" -> fr.orbit.mmo.ui.TownMenus.forge(plugin, p);
            case "alchemist" -> fr.orbit.mmo.ui.TownMenus.alchemist(plugin, p);
            case "priest" -> fr.orbit.mmo.ui.TownMenus.priest(plugin, p);
            case "runesmith" -> fr.orbit.mmo.ui.RuneMenus.open(plugin, p);
            // Gardien du Pilier des Astres (capitales) : il ouvre le choix de l'étage, comme la porte d'une ville du Pilier.
            case "tower", "tower_ombre" -> Menus.floors(plugin, p, true);
            case "ambient" -> {
                p.playSound(e.getRightClicked().getLocation(), org.bukkit.Sound.ENTITY_CAT_PURREOW, 0.6f, 1.1f);
                e.getRightClicked().getWorld().spawnParticle(Particle.HEART, e.getRightClicked().getLocation().add(0, 0.8, 0), 2, 0.2, 0.1, 0.2, 0);
                return;
            }
            default -> {
                return;
            }
        }
        greet(p, e.getRightClicked(), tag, greeting);
    }

    /** Répliques des PNJ : dans la carte à droite du menu (pack), sinon dans le chat, une de temps en temps. */
    private static final Map<String, String[]> LINES = Map.of(
            "merchant", new String[]{
                    "Tout se vend, tout s'achète. Fixe ton prix, je m'occupe du reste.",
                    "Les plus belles pièces partent vite. Repasse souvent !",
                    "Pressé ? Torvald, à la forge, rachète ton butin tout de suite… mais pour moins cher."},
            "blacksmith", new String[]{
                    "Une lame entretenue vaut deux lames neuves.",
                    "Montre-moi ce que tu portes, je vais voir ce que j'en tire.",
                    "Le feu de ma forge ne s'éteint jamais. Ma patience, si.",
                    "Ton butin de là-dehors ? Pose-le sur l'établi, je te le rachète."},
            "alchemist", new String[]{
                    "Une potion pour la route ? On ne sait jamais ce qui rôde dehors.",
                    "Attention, celle-là pétille encore… c'est qu'elle est prête.",
                    "Les herbes d'ici font des merveilles, crois-moi."},
            "priest", new String[]{
                    "Que les étoiles veillent sur toi, voyageur.",
                    "Un parchemin béni vaut mieux qu'un bouclier fendu.",
                    "Passe chercher ma bénédiction avant de partir."},
            "runesmith", new String[]{
                    "Une rune bien sertie vaut mieux qu'une lame neuve.",
                    "Trois runes pareilles ? Donne, j'en fais une seule, bien plus forte.",
                    "Le feu, le givre, la foudre… et le néant. Choisis bien ce que ta lame doit porter."},
            "tower", new String[]{
                    "Cent étages, un gardien à chacun. Personne n'a encore vu le sommet.",
                    "Seul, tu n'iras pas loin là-haut. Reviens avec les tiens.",
                    "Ce qu'on arrache au Pilier ne se trouve nulle part ailleurs sur le continent."},
            "tower_ombre", new String[]{
                    "Cent étages, un gardien à chacun. Personne n'en a encore vu le sommet.",
                    "Le Pilier avale les solitaires. Monte avec les tiens.",
                    "Ce que le Pilier garde, il ne le rend qu'à ceux qui le gravissent."});

    private static final Map<String, String> SPEAKERS = Map.of(
            "tower", "<#7FD4FF>Gardien du Pilier",
            "tower_ombre", "<#9B59FF>Veilleur du Pilier",
            "merchant", "<#FFD66E>Corvin",
            "blacksmith", "<#FF8A3B>Torvald",
            "alchemist", "<#B36BFF>Mirelle",
            "priest", "<#FFF3B0>Frère Anselme",
            "runesmith", "<#9B59FF>Ysolde");

    /** Les mêmes répliques en anglais, dans le même ordre (joueurs dont la langue est l'anglais, voir /langue). */
    private static final Map<String, String[]> LINES_EN = Map.of(
            "merchant", new String[]{
                    "Everything sells, everything can be bought. Name your price, I handle the rest.",
                    "The finest pieces go fast. Come back often!",
                    "In a hurry? Torvald, at the forge, buys your loot right away… but for less."},
            "blacksmith", new String[]{
                    "A well-kept blade is worth two new ones.",
                    "Show me what you're wearing, I'll see what I can make of it.",
                    "The fire of my forge never goes out. My patience does.",
                    "Your loot from out there? Put it on the bench, I'll buy it."},
            "alchemist", new String[]{
                    "A potion for the road? You never know what prowls outside.",
                    "Careful, that one is still fizzing… that means it's ready.",
                    "The herbs around here work wonders, believe me."},
            "priest", new String[]{
                    "May the stars watch over you, traveller.",
                    "A blessed scroll is better than a cracked shield.",
                    "Come by for my blessing before you leave."},
            "runesmith", new String[]{
                    "A well-set rune is better than a new blade.",
                    "Three runes alike? Hand them over, I'll make one, far stronger.",
                    "Fire, frost, lightning… and the void. Choose well what your blade should carry."},
            "tower", new String[]{
                    "A hundred floors, a guardian on each. No one has seen the top yet.",
                    "Alone, you won't get far up there. Come back with your own.",
                    "What is torn from the Pillar is found nowhere else on the continent."},
            "tower_ombre", new String[]{
                    "A hundred floors, a guardian on each. No one has seen the top yet.",
                    "The Pillar swallows the lonely. Climb with your own.",
                    "What the Pillar keeps, it only gives back to those who climb it."});

    private static final Map<String, String> SPEAKERS_EN = Map.of(
            "tower", "Pillar Guardian",
            "tower_ombre", "Pillar Watcher",
            "priest", "Brother Anselme");

    /** Ce qu'un PNJ de ville dit à ce clic : la phrase, et la carte posée pour son menu (null sans le pack). */
    private record Greeting(String line, fr.orbit.mmo.hud.NpcCard.Card card) {
    }

    private Greeting prepareGreeting(Player p, String tag) {
        String[] lines = LINES.get(tag);
        String speaker = SPEAKERS.get(tag);
        if (lines == null || speaker == null) {
            return null;
        }
        boolean en = plugin.quests() != null && "en".equals(plugin.quests().lang(p));
        String[] set = en && LINES_EN.containsKey(tag) && LINES_EN.get(tag).length == lines.length ? LINES_EN.get(tag) : lines;
        String line = set[java.util.concurrent.ThreadLocalRandom.current().nextInt(set.length)];
        if (!plugin.pack().isLoaded(p.getUniqueId())) {
            return new Greeting(line, null);
        }
        // SPEAKERS : « <#RRGGBB>Nom ».
        int cut = speaker.indexOf('>');
        String name = en && SPEAKERS_EN.containsKey(tag) ? SPEAKERS_EN.get(tag) : speaker.substring(cut + 1);
        fr.orbit.mmo.hud.NpcCard.Card card = fr.orbit.mmo.hud.NpcCard.of(tag, name,
                net.kyori.adventure.text.format.TextColor.fromHexString(speaker.substring(1, cut)), line);
        plugin.hud().npcCard(p, card);
        return new Greeting(line, card);
    }

    private void greet(Player p, org.bukkit.entity.Entity npc, String tag, Greeting g) {
        if (g == null) {
            return;
        }
        boolean inCard = g.card() != null && g.card().shown();
        if (g.card() != null && !inCard && plugin.hud().npcCard(p) == g.card()) {
            // Aucun menu n'a pris la carte : elle ne doit pas ressortir sur un autre menu.
            plugin.hud().npcCardEnd(p);
        }
        long now = System.currentTimeMillis();
        String key = p.getUniqueId() + ":" + tag;
        Long next = greetCooldown.get(key);
        if (next != null && next > now) {
            return;
        }
        greetCooldown.put(key, now + 30_000);
        if (!inCard) {
            p.sendMessage(Text.mm(SPEAKERS.get(tag) + " <#5A6270>» <#D7DCE2><i>" + Text.esc(g.line())));
        }
        org.bukkit.Sound s = switch (tag) {
            case "alchemist" -> org.bukkit.Sound.ENTITY_WITCH_CELEBRATE;
            case "blacksmith" -> org.bukkit.Sound.BLOCK_ANVIL_USE;
            case "tower", "tower_ombre" -> org.bukkit.Sound.BLOCK_BEACON_AMBIENT;
            default -> org.bukkit.Sound.ENTITY_VILLAGER_TRADE;
        };
        p.playSound(npc.getLocation(), s, tag.equals("blacksmith") ? 0.3f : 0.7f, 1.1f);
    }

    /**
     * Un peu de vie en ville : les PNJ tournent la tête vers le joueur le plus proche,
     * la forge crache des étincelles, le chaudron de l'alchimiste bouillonne, les bougies de l'autel scintillent.
     */
    private void townLife(World w) {
        double y = FloorGenerator.SURFACE;
        lifeTick++;
        for (org.bukkit.entity.LivingEntity npc : w.getNearbyEntitiesByType(org.bukkit.entity.LivingEntity.class,
                new Location(w, 0, y + 1, 0), 14)) {
            String tag = npc.getPersistentDataContainer().get(Keys.NPC, PersistentDataType.STRING);
            if (tag == null || tag.equals("ambient")) {
                continue;
            }
            // L'ancien marchand devient Corvin, de l'hôtel des ventes.
            if (tag.equals("merchant") && npc instanceof org.bukkit.entity.Villager v
                    && v.getProfession() != org.bukkit.entity.Villager.Profession.CARTOGRAPHER) {
                v.setProfession(org.bukkit.entity.Villager.Profession.CARTOGRAPHER);
                v.customName(Text.mm(FloorBuilder.MARKET_NAME));
            }
            Player near = null;
            double best = 8 * 8;
            for (Player p : w.getPlayers()) {
                double dd = p.getLocation().distanceSquared(npc.getLocation());
                if (dd < best) {
                    best = dd;
                    near = p;
                }
            }
            if (near != null) {
                Vector dir = near.getEyeLocation().toVector().subtract(npc.getEyeLocation().toVector());
                Location look = npc.getLocation().setDirection(dir);
                npc.setRotation(look.getYaw(), Math.max(-30, Math.min(30, look.getPitch())));
            }
        }
        // Étiquette de Torvald posée avant le rachat du butin : on la met à jour.
        for (org.bukkit.entity.TextDisplay t : w.getNearbyEntitiesByType(org.bukkit.entity.TextDisplay.class,
                new Location(w, -7.5, y + 3.05, 7.5), 0.6)) {
            if (!t.getScoreboardTags().contains(fr.orbit.mmo.hud.Nameplates.TAG) && !t.getScoreboardTags().contains(fr.orbit.mmo.hud.DamageNumbers.TAG)
                    && !net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(t.text()).contains("Rachat")) {
                t.text(Text.mm(FloorBuilder.FORGE_LABEL));
            }
        }
        java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
        // Forge : étincelles sur l'enclume, fumée du haut fourneau.
        w.spawnParticle(Particle.SMOKE, new Location(w, -9.5, y + 2.1, 9.5), 2, 0.15, 0.05, 0.15, 0.01);
        if (r.nextInt(3) == 0) {
            w.spawnParticle(Particle.LAVA, new Location(w, -8.5, y + 2, 10.5), 1, 0.1, 0.05, 0.1, 0);
        }
        if (lifeTick % 12 == 0) {
            Location anvil = new Location(w, -8.5, y + 2, 9.5);
            w.spawnParticle(Particle.FLAME, anvil, 6, 0.2, 0.1, 0.2, 0.04);
            w.playSound(anvil, org.bukkit.Sound.BLOCK_ANVIL_USE, 0.25f, 1.4f + r.nextFloat() * 0.3f);
        }
        // Alchimiste : le chaudron bouillonne, l'alambic fume.
        w.spawnParticle(Particle.BUBBLE_POP, new Location(w, 10.5, y + 2, -8.5), 3, 0.2, 0.05, 0.2, 0.01);
        w.spawnParticle(Particle.WITCH, new Location(w, 9.5, y + 2, -8.5), 1, 0.1, 0.1, 0.1, 0);
        if (lifeTick % 16 == 0) {
            w.playSound(new Location(w, 10.5, y + 2, -8.5), org.bukkit.Sound.BLOCK_BREWING_STAND_BREW, 0.3f, 1.2f);
        }
        // Établi d'Ysolde : glyphes autour de la table d'enchantement, éclats d'améthyste.
        if (runeStall.contains(floorOf(w))) {
            w.spawnParticle(Particle.ENCHANT, new Location(w, -13.5, y + 2.4, -3.5), 4, 0.4, 0.3, 0.4, 0.6);
            if (r.nextInt(3) == 0) {
                Fx.dust(new Location(w, -13.5, y + 2.6, -4.5), Fx.color("#9B59FF"), 0.9f, 3, 0.3);
            }
        }
        // Autel : lueurs autour du pupitre.
        w.spawnParticle(Particle.ENCHANT, new Location(w, -8.5, y + 2.2, -8.5), 3, 0.3, 0.3, 0.3, 0.4);
        if (r.nextInt(4) == 0) {
            w.spawnParticle(Particle.END_ROD, new Location(w, -9.5, y + 2.6, -9.5), 1, 0.3, 0.2, 0.3, 0.005);
        }
    }

    private void gateFx() {
        for (World w : Bukkit.getWorlds()) {
            if (floorOf(w) <= 0 || w.getPlayers().isEmpty()) {
                continue;
            }
            Location gate = new Location(w, 0.5, FloorGenerator.SURFACE + 3.5, 0.5);
            boolean near = false;
            for (Player p : w.getPlayers()) {
                if (p.getLocation().distanceSquared(gate) < 48 * 48) {
                    near = true;
                    break;
                }
            }
            if (near) {
                w.spawnParticle(Particle.END_ROD, gate, 4, 1.0, 1.4, 0.15, 0.01);
                Fx.dust(gate, Fx.color("#7FD4FF"), 1.4f, 8, 0.9);
                if (townNpcs.contains(floorOf(w))) {
                    townLife(w);
                }
            }
        }
    }

    private void loadState() {
        if (!stateFile.isFile()) {
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(stateFile);
        highestCleared = y.getInt("highest-cleared");
        // Avant les guildes, « highest-cleared » ouvrait les étages à tout le serveur : il devient le socle de départ.
        base = Math.max(0, Math.min(highestCleared, y.getInt("socle", highestCleared)));
        built.addAll(y.getIntegerList("built"));
        townNpcs.addAll(y.getIntegerList("town-npcs"));
        marketStall.addAll(y.getIntegerList("market-stall"));
        runeStall.addAll(y.getIntegerList("rune-stall"));
        ConfigurationSection br = y.getConfigurationSection("boss-ready");
        if (br != null) {
            for (String k : br.getKeys(false)) {
                try {
                    bossReady.put(Integer.parseInt(k), br.getLong(k));
                } catch (NumberFormatException ignored) {
                    // clé invalide
                }
            }
        }
    }

    public void saveState() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("highest-cleared", highestCleared);
        y.set("socle", base);
        y.set("built", List.copyOf(built));
        y.set("town-npcs", List.copyOf(townNpcs));
        y.set("market-stall", List.copyOf(marketStall));
        y.set("rune-stall", List.copyOf(runeStall));
        for (Map.Entry<Integer, Long> e : bossReady.entrySet()) {
            y.set("boss-ready." + e.getKey(), e.getValue());
        }
        try {
            y.save(stateFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Échec de la sauvegarde de state.yml", e);
        }
    }
}
