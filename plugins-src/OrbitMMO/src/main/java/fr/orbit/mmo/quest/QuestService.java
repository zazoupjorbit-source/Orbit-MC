package fr.orbit.mmo.quest;

import fr.orbit.mmo.OrbitMMO;
import fr.orbit.mmo.item.Items;
import fr.orbit.mmo.mob.MobKilledEvent;
import fr.orbit.mmo.mob.MobState;
import fr.orbit.mmo.player.PlayerData;
import fr.orbit.mmo.quest.QuestDefs.ItemReward;
import fr.orbit.mmo.quest.QuestDefs.Line;
import fr.orbit.mmo.quest.QuestDefs.NpcDef;
import fr.orbit.mmo.quest.QuestDefs.QuestDef;
import fr.orbit.mmo.quest.QuestDefs.RewardDef;
import fr.orbit.mmo.quest.QuestDefs.SpawnDef;
import fr.orbit.mmo.quest.QuestDefs.StepDef;
import fr.orbit.mmo.quest.QuestDefs.StepType;
import fr.orbit.mmo.quest.QuestProgress.Active;
import fr.orbit.mmo.registry.Defs.MobDef;
import fr.orbit.mmo.ui.Menu;
import fr.orbit.mmo.util.Fx;
import fr.orbit.mmo.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Les quêtes : démarrage (automatique ou auprès d'un PNJ), étapes, dialogues, monstres de quête,
 * Éclats d'ancrage (réapparition), récompenses, suivi à l'écran.
 *
 * Une quête ne démarre que si tous ses points sont posés (/quetes admin manquants) : tant que la zone
 * n'est pas installée sur le serveur, les joueurs ne voient rien.
 */
public final class QuestService implements Listener {

    private final OrbitMMO plugin;
    private final QuestRegistry registry;
    private final QuestPoints points;
    private final QuestStore store;
    private final Dialogue dialogue;
    private final QuestNpcs npcs;
    private final QuestTracker tracker = new QuestTracker();
    /** Monstres vivants de chaque zone d'apparition. */
    private final Map<String, List<MobState>> spawned = new HashMap<>();
    private final Map<String, Long> idleCooldown = new HashMap<>();
    private int ticks;

    public QuestService(OrbitMMO plugin) {
        this.plugin = plugin;
        this.registry = new QuestRegistry(plugin);
        this.points = new QuestPoints(plugin);
        this.store = new QuestStore(plugin);
        this.dialogue = new Dialogue(plugin, registry);
        this.npcs = new QuestNpcs(plugin, registry, points);
    }

    public void enable() {
        registry.load();
        points.load();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        for (World w : Bukkit.getWorlds()) {
            npcs.purge(w.getEntities());
        }
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 10L);
        plugin.getServer().getScheduler().runTaskTimer(plugin, store::saveDirty, 1200L, 1200L);
    }

    public void shutdown() {
        dialogue.cancelAll();
        tracker.hideAll();
        npcs.removeAll();
        for (List<MobState> list : spawned.values()) {
            for (MobState ms : list) {
                if (!ms.dead) {
                    plugin.mobs().remove(ms);
                }
            }
        }
        spawned.clear();
        store.shutdown();
    }

    /** Relit les fichiers de quêtes et les points, recrée les PNJ. */
    public void reload() {
        dialogue.cancelAll();
        npcs.removeAll();
        registry.load();
        points.load();
        for (Player p : Bukkit.getOnlinePlayers()) {
            QuestProgress pr = store.get(p.getUniqueId());
            for (Map.Entry<String, Active> e : pr.active.entrySet()) {
                QuestDef q = registry.quest(e.getKey());
                if (q != null && e.getValue().step >= q.steps().size()) {
                    e.getValue().step = q.steps().size() - 1;
                }
            }
        }
    }

    public QuestRegistry registry() {
        return registry;
    }

    public QuestPoints points() {
        return points;
    }

    public QuestProgress progress(Player p) {
        return store.get(p.getUniqueId());
    }

    // ------------------------------------------------------------------ Boucle

    private void tick() {
        ticks++;
        npcs.tick();
        if (ticks % 4 == 0) {
            spawnTick();
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData d = plugin.players().loaded(p.getUniqueId());
            if (d == null || !plugin.progression().valid(d)) {
                tracker.hide(p);
                continue;
            }
            QuestProgress pr = store.get(p.getUniqueId());
            if (pr.faction != d.faction) {
                // Nouveau personnage (autre faction) : les quêtes repartent de zéro.
                if (pr.faction != null) {
                    pr.clear();
                }
                pr.faction = d.faction;
                pr.dirty = true;
            }
            if (!dialogue.busy(p)) {
                autoStart(p, d, pr);
                checkSteps(p, d, pr);
            }
            if (ticks % 2 == 0) {
                guide(p, pr);
            }
            tracker.show(p, trackerLines(p, pr));
            markers(p, d, pr);
        }
    }

    private void autoStart(Player p, PlayerData d, QuestProgress pr) {
        if (d.buildMode || plugin.progression().spec(d) == null
                || p.getOpenInventory().getTopInventory().getHolder() instanceof Menu) {
            return;
        }
        for (QuestDef q : registry.quests()) {
            if (q.auto() && available(d, pr, q) && ready(q)) {
                start(p, q);
                return;
            }
        }
    }

    /** Objectifs vérifiés en continu : atteindre un lieu, lancer un sort. */
    private void checkSteps(Player p, PlayerData d, QuestProgress pr) {
        for (Map.Entry<String, Active> e : new ArrayList<>(pr.active.entrySet())) {
            QuestDef q = registry.quest(e.getKey());
            StepDef s = step(q, e.getValue());
            if (s == null) {
                continue;
            }
            if (s.type() == StepType.ATTEINDRE) {
                Location at = points.location(s.point());
                if (at != null && near(p, at, s.radius())) {
                    completeStep(p, q);
                    return;
                }
            } else if (s.type() == StepType.SORT) {
                if (pr.lastGcdSeen == 0) {
                    pr.lastGcdSeen = Math.max(1, d.gcdUntil);
                } else if (d.gcdUntil > pr.lastGcdSeen) {
                    completeStep(p, q);
                    return;
                }
            }
        }
    }

    /** Particules visibles par le joueur seul sur les lieux de ses objectifs. */
    private void guide(Player p, QuestProgress pr) {
        for (Map.Entry<String, Active> e : pr.active.entrySet()) {
            StepDef s = step(registry.quest(e.getKey()), e.getValue());
            if (s == null || s.point() == null) {
                continue;
            }
            if (s.type() != StepType.ATTEINDRE && s.type() != StepType.INTERAGIR && s.type() != StepType.ANCRAGE) {
                continue;
            }
            Location at = points.location(s.point());
            if (at == null || !at.getWorld().equals(p.getWorld()) || at.distanceSquared(p.getLocation()) > 64 * 64) {
                continue;
            }
            Location c = at.clone().add(0, 1, 0);
            if (s.type() == StepType.ATTEINDRE) {
                p.spawnParticle(Particle.END_ROD, c, 4, s.radius() * 0.3, 0.8, s.radius() * 0.3, 0.01);
            } else {
                p.spawnParticle(Particle.END_ROD, c, 6, 0.25, 0.6, 0.25, 0.02);
                p.spawnParticle(Particle.DUST, c, 6, 0.3, 0.5, 0.3, 0,
                        new Particle.DustOptions(Fx.color(s.type() == StepType.ANCRAGE ? "#FFD66E" : "#FFF3B0"), 1.2f));
            }
        }
    }

    private void markers(Player p, PlayerData d, QuestProgress pr) {
        Set<String> talk = new HashSet<>();
        for (Map.Entry<String, Active> e : pr.active.entrySet()) {
            StepDef s = step(registry.quest(e.getKey()), e.getValue());
            if (s != null && s.type() == StepType.PARLER && s.npc() != null) {
                talk.add(s.npc());
            }
        }
        for (String id : npcs.liveIds()) {
            QuestNpcs.Mark m = talk.contains(id) ? QuestNpcs.Mark.TALK
                    : offerOf(d, pr, id) != null ? QuestNpcs.Mark.OFFER : QuestNpcs.Mark.NONE;
            npcs.mark(p, id, m);
        }
    }

    // ------------------------------------------------------------------ Règles

    private static StepDef step(QuestDef q, Active a) {
        if (q == null || a == null || a.step < 0 || a.step >= q.steps().size()) {
            return null;
        }
        return q.steps().get(a.step);
    }

    /** La quête peut être commencée par ce personnage (hors question des points posés). */
    public boolean available(PlayerData d, QuestProgress pr, QuestDef q) {
        if (pr.done.contains(q.id()) || pr.active.containsKey(q.id())) {
            return false;
        }
        if (q.faction() != null && q.faction() != d.faction) {
            return false;
        }
        if (d.level < q.level()) {
            return false;
        }
        for (String r : q.requires()) {
            if (!pr.done.contains(r)) {
                return false;
            }
        }
        return true;
    }

    /** Tous les points dont la quête a besoin sont posés. */
    public boolean ready(QuestDef q) {
        for (String pt : registry.pointsOf(q)) {
            if (points.location(pt) == null) {
                return false;
            }
        }
        return true;
    }

    public List<String> missingPoints(QuestDef q) {
        List<String> out = new ArrayList<>();
        for (String pt : registry.pointsOf(q)) {
            if (!points.has(pt)) {
                out.add(pt);
            }
        }
        return out;
    }

    private QuestDef offerOf(PlayerData d, QuestProgress pr, String npcId) {
        for (QuestDef q : registry.quests()) {
            if (!q.auto() && npcId.equals(q.giver()) && available(d, pr, q) && ready(q)) {
                return q;
            }
        }
        return null;
    }

    private static boolean near(Player p, Location at, double radius) {
        return at.getWorld().equals(p.getWorld()) && at.distanceSquared(p.getLocation()) <= radius * radius;
    }

    // ------------------------------------------------------------------ Déroulement

    /** Démarre une quête (téléportation de départ, introduction, premier objectif). */
    public void start(Player p, QuestDef q) {
        QuestProgress pr = store.get(p.getUniqueId());
        pr.done.remove(q.id());
        pr.active.put(q.id(), new Active(0, 0));
        pr.dirty = true;
        pr.lastGcdSeen = 0;
        Location dep = points.location(q.startPoint());
        if (dep != null) {
            p.teleport(dep);
        }
        Text.title(p, "<#FFD66E><b>" + Text.esc(q.name()) + "</b>", "<#A9B1BC>Nouvelle quête", 10, 60, 20);
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.4f);
        dialogue.play(p, q.intro(), () -> announce(p, q));
    }

    private void announce(Player p, QuestDef q) {
        QuestProgress pr = store.get(p.getUniqueId());
        StepDef s = step(q, pr.active.get(q.id()));
        if (s == null) {
            return;
        }
        Text.msg(p, "<#FFD66E>Objectif</#FFD66E> <#5A6270>·</#5A6270> <white>" + Text.esc(objective(s, pr.active.get(q.id()))));
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6f, 1.6f);
    }

    /** L'étape en cours est réussie : texte d'après, puis étape suivante ou fin de la quête. */
    private void completeStep(Player p, QuestDef q) {
        QuestProgress pr = store.get(p.getUniqueId());
        Active a = pr.active.get(q.id());
        StepDef s = step(q, a);
        if (s == null) {
            return;
        }
        // On passe tout de suite à l'étape suivante (un objectif ne peut pas être validé deux fois),
        // le texte « après » est joué ensuite.
        a.step++;
        a.count = 0;
        pr.dirty = true;
        pr.lastGcdSeen = 0;
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
        if (a.step >= q.steps().size()) {
            dialogue.play(p, s.after(), () -> finish(p, q));
        } else {
            plugin.hud().flash(p, "<#5EE89A>✔ " + Text.esc(s.text()), 50);
            dialogue.play(p, s.after(), () -> announce(p, q));
        }
    }

    private void finish(Player p, QuestDef q) {
        QuestProgress pr = store.get(p.getUniqueId());
        if (pr.active.remove(q.id()) == null) {
            return;
        }
        pr.done.add(q.id());
        pr.dirty = true;
        store.save(pr);
        dialogue.play(p, q.outro(), () -> reward(p, q));
    }

    private void reward(Player p, QuestDef q) {
        PlayerData d = plugin.players().get(p);
        RewardDef r = q.reward();
        StringBuilder sb = new StringBuilder();
        if (r.xp() > 0) {
            plugin.progression().addXp(p, d, r.xp());
            sb.append("<#7FD4FF>+").append(Text.num(r.xp())).append(" XP</#7FD4FF>  ");
        }
        if (r.gold() > 0) {
            d.gold += r.gold();
            sb.append("<#FFD66E>+").append(Text.num(r.gold())).append(" or</#FFD66E>  ");
        }
        for (ItemReward it : r.items()) {
            ItemStack stack = new ItemStack(it.material(), it.amount());
            if (it.name() != null) {
                ItemMeta meta = stack.getItemMeta();
                meta.displayName(Text.item("<#FFE9A8>" + Text.esc(it.name())));
                if (!it.lore().isEmpty()) {
                    List<String> lore = new ArrayList<>();
                    for (String l : it.lore()) {
                        lore.add("<#A9B1BC><i>" + Text.esc(l));
                    }
                    meta.lore(Text.lore(lore));
                }
                stack.setItemMeta(meta);
            }
            Items.give(p, stack);
            sb.append("<#D7DCE2>+").append(it.amount()).append(' ').append(Text.esc(it.name() == null ? it.material().name() : it.name())).append("</#D7DCE2>  ");
        }
        Text.title(p, "<gradient:#FFD66E:#FFF3B0><b>Quête terminée</b></gradient>", "<white>" + Text.esc(q.name()), 10, 60, 20);
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f);
        Text.msg(p, "<#FFD66E>✦ Quête terminée :</#FFD66E> <white>" + Text.esc(q.name()) + (sb.isEmpty() ? "" : "  " + sb.toString().trim()));
    }

    // ------------------------------------------------------------------ Administration

    /** Termine la quête sans jouer les dialogues (récompenses données). */
    public void forceFinish(Player p, QuestDef q) {
        QuestProgress pr = store.get(p.getUniqueId());
        dialogue.cancel(p.getUniqueId());
        pr.active.put(q.id(), new Active(q.steps().size(), 0));
        finish(p, q);
    }

    public void setStep(Player p, QuestDef q, int step) {
        QuestProgress pr = store.get(p.getUniqueId());
        dialogue.cancel(p.getUniqueId());
        pr.done.remove(q.id());
        pr.active.put(q.id(), new Active(Math.max(0, Math.min(q.steps().size() - 1, step)), 0));
        pr.dirty = true;
        pr.lastGcdSeen = 0;
        announce(p, q);
    }

    public void reset(Player p, QuestDef q) {
        QuestProgress pr = store.get(p.getUniqueId());
        dialogue.cancel(p.getUniqueId());
        if (q == null) {
            pr.clear();
        } else {
            pr.active.remove(q.id());
            pr.done.remove(q.id());
            pr.dirty = true;
        }
        store.save(pr);
    }

    public void skipDialogue(Player p) {
        dialogue.skip(p);
    }

    // ------------------------------------------------------------------ Textes

    /** « Tuer des Rampants (3/8) ». */
    String objective(StepDef s, Active a) {
        if (s.counted()) {
            return s.text() + " (" + Math.min(a == null ? 0 : a.count, s.count()) + "/" + s.count() + ")";
        }
        return s.text();
    }

    /** Lieu à indiquer pour l'étape (PNJ, point, zone d'apparition). */
    Location target(StepDef s) {
        if (s.type() == StepType.PARLER) {
            return npcs.location(s.npc());
        }
        if (s.point() != null) {
            return points.location(s.point());
        }
        for (SpawnDef sp : registry.spawns()) {
            if (s.mobs().contains(sp.mob())) {
                Location l = points.location(sp.point());
                if (l != null) {
                    return l;
                }
            }
        }
        return null;
    }

    private List<String> trackerLines(Player p, QuestProgress pr) {
        List<String> lines = new ArrayList<>();
        int shown = 0;
        for (Map.Entry<String, Active> e : pr.active.entrySet()) {
            QuestDef q = registry.quest(e.getKey());
            StepDef s = step(q, e.getValue());
            if (s == null) {
                continue;
            }
            if (shown == 2) {
                lines.add("<#8C96A3>+" + (pr.active.size() - 2) + " autre(s) · /quetes");
                break;
            }
            if (!lines.isEmpty()) {
                lines.add(" ");
            }
            lines.add("<#FFD66E><b>" + Text.esc(q.name()) + "</b>");
            lines.add("<#D7DCE2>▸ " + Text.esc(objective(s, e.getValue())));
            Location at = target(s);
            if (at != null) {
                if (at.getWorld().equals(p.getWorld())) {
                    double dist = at.distance(p.getLocation());
                    lines.add(dist < 4 ? "<#5EE89A>  ici" : "<#8C96A3>  " + arrow(p, at) + " " + Math.round(dist) + " m");
                } else {
                    lines.add("<#8C96A3>  ailleurs (" + Text.esc(at.getWorld().getName()) + ")");
                }
            }
            shown++;
        }
        return lines;
    }

    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    /** Flèche vers la cible, relative au regard du joueur. */
    private static String arrow(Player p, Location to) {
        double dx = to.getX() - p.getLocation().getX();
        double dz = to.getZ() - p.getLocation().getZ();
        double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
        double rel = targetYaw - p.getLocation().getYaw();
        rel = ((rel % 360) + 360) % 360;
        return ARROWS[(int) Math.round(rel / 45.0) % 8];
    }

    // ------------------------------------------------------------------ Monstres de quête

    private void spawnTick() {
        for (SpawnDef sp : registry.spawns()) {
            List<MobState> list = spawned.computeIfAbsent(sp.id(), k -> new ArrayList<>());
            list.removeIf(ms -> ms.dead || !ms.entity.isValid());
            Location at = points.location(sp.point());
            MobDef def = registry.mob(sp.mob());
            if (at == null || def == null || list.size() >= sp.count() || !needed(sp, at)) {
                continue;
            }
            for (int i = 0; i < 2 && list.size() < sp.count(); i++) {
                Location spot = spot(at, sp.radius());
                if (spot == null) {
                    continue;
                }
                int level = sp.minLevel() + ThreadLocalRandom.current().nextInt(sp.maxLevel() - sp.minLevel() + 1);
                MobState ms = plugin.mobs().spawn(def, level, spot, plugin.floors().floorOf(spot.getWorld()), null, 1);
                if (ms != null) {
                    list.add(ms);
                }
            }
        }
    }

    /** Un joueur proche a besoin de ces monstres pour une quête en cours. */
    private boolean needed(SpawnDef sp, Location at) {
        for (Player p : at.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(at) > 56 * 56) {
                continue;
            }
            QuestProgress pr = store.get(p.getUniqueId());
            for (Map.Entry<String, Active> e : pr.active.entrySet()) {
                StepDef s = step(registry.quest(e.getKey()), e.getValue());
                // Toute étape qui cite le monstre (tuer, ramasser, ou lancer un sort sur eux).
                if (s != null && s.mobs().contains(sp.mob())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Location spot(Location center, double radius) {
        World w = center.getWorld();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 10; attempt++) {
            double a = r.nextDouble() * Math.PI * 2;
            double dist = r.nextDouble() * radius;
            int x = (int) Math.floor(center.getX() + Math.cos(a) * dist);
            int z = (int) Math.floor(center.getZ() + Math.sin(a) * dist);
            if (!w.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            for (int y = center.getBlockY() + 4; y >= center.getBlockY() - 6; y--) {
                Block ground = w.getBlockAt(x, y, z);
                Block feet = ground.getRelative(BlockFace.UP);
                if (ground.getType().isSolid() && feet.isPassable() && !feet.isLiquid() && feet.getRelative(BlockFace.UP).isPassable()) {
                    return new Location(w, x + 0.5, y + 1, z + 0.5, r.nextFloat() * 360f, 0f);
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ Événements

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        if (e.getLoginResult() == AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            store.preload(e.getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        dialogue.cancel(id);
        tracker.quit(id);
        npcs.quit(id);
        store.unload(id);
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent e) {
        npcs.purge(e.getEntities());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onNpcDamage(EntityDamageEvent e) {
        if (npcs.idOf(e.getEntity()) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onTalk(PlayerInteractEntityEvent e) {
        String npcId = npcs.idOf(e.getRightClicked());
        if (npcId == null || npcId.startsWith("#")) {
            return;
        }
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player p = e.getPlayer();
        if (dialogue.busy(p)) {
            dialogue.skip(p);
            return;
        }
        PlayerData d = plugin.players().get(p);
        if (!plugin.progression().valid(d)) {
            return;
        }
        QuestProgress pr = store.get(p.getUniqueId());
        // 1. Une étape demande de parler à ce PNJ.
        for (Map.Entry<String, Active> en : new ArrayList<>(pr.active.entrySet())) {
            QuestDef q = registry.quest(en.getKey());
            StepDef s = step(q, en.getValue());
            if (s != null && s.type() == StepType.PARLER && npcId.equals(s.npc())) {
                int stepAt = en.getValue().step;
                dialogue.play(p, s.dialogue(), () -> {
                    Active now = store.get(p.getUniqueId()).active.get(q.id());
                    if (now != null && now.step == stepAt) {
                        completeStep(p, q);
                    }
                });
                return;
            }
        }
        // 2. Ce PNJ propose une quête.
        QuestDef offer = offerOf(d, pr, npcId);
        if (offer != null) {
            dialogue.play(p, offer.offer(), () -> propose(p, offer));
            return;
        }
        // 3. Réplique d'ambiance.
        NpcDef def = registry.npc(npcId);
        if (def != null && !def.idle().isEmpty()) {
            String key = p.getUniqueId() + ":" + npcId;
            long now = System.currentTimeMillis();
            if (idleCooldown.getOrDefault(key, 0L) < now) {
                idleCooldown.put(key, now + 8000);
                String line = def.idle().get(ThreadLocalRandom.current().nextInt(def.idle().size()));
                dialogue.play(p, List.of(new Line(npcId, line)), null);
            }
        }
    }

    /** Boutons « Accepter » / « Plus tard » dans le chat. */
    private void propose(Player p, QuestDef q) {
        ClickCallback.Options once = ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(10)).build();
        Component accept = Text.mm("<#5EE89A><b>[ Accepter ]</b>")
                .hoverEvent(HoverEvent.showText(Text.mm("<#D7DCE2>Commencer « " + Text.esc(q.name()) + " »")))
                .clickEvent(ClickEvent.callback(a -> {
                    PlayerData d = plugin.players().get(p);
                    QuestProgress pr = store.get(p.getUniqueId());
                    if (p.isOnline() && available(d, pr, q) && ready(q) && !dialogue.busy(p)) {
                        start(p, q);
                    }
                }, once));
        Component later = Text.mm("<#8C96A3>[ Plus tard ]")
                .clickEvent(ClickEvent.callback(a -> Text.msg(p, "<#8C96A3>Revenez quand vous serez prêt."), once));
        p.sendMessage(Text.mm("<#FFD66E>✦ Quête :</#FFD66E> <white>" + Text.esc(q.name()) + "</white>  ")
                .append(accept).append(Component.text("  ")).append(later));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND
                || (e.getAction() != Action.RIGHT_CLICK_BLOCK && e.getAction() != Action.RIGHT_CLICK_AIR)) {
            return;
        }
        Player p = e.getPlayer();
        if (dialogue.busy(p)) {
            return;
        }
        QuestProgress pr = store.get(p.getUniqueId());
        for (Map.Entry<String, Active> en : new ArrayList<>(pr.active.entrySet())) {
            QuestDef q = registry.quest(en.getKey());
            StepDef s = step(q, en.getValue());
            if (s == null || (s.type() != StepType.INTERAGIR && s.type() != StepType.ANCRAGE)) {
                continue;
            }
            Location at = points.location(s.point());
            if (at == null || !near(p, at, s.radius() + 1.5)) {
                continue;
            }
            if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
                e.setCancelled(true);
            }
            Location fx = at.clone().add(0, 1, 0);
            p.getWorld().spawnParticle(Particle.END_ROD, fx, 40, 0.3, 0.8, 0.3, 0.12);
            p.playSound(fx, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1.2f);
            if (s.type() == StepType.ANCRAGE) {
                pr.anchor = s.point();
                Fx.ring(at.clone().add(0, 0.2, 0), 1.6, Fx.color("#FFD66E"), 1.4f);
                Text.msg(p, "<#FFD66E>✦ Éclat d'ancrage lié.</#FFD66E> <#A9B1BC>Si vous tombez, vous reviendrez ici.");
            }
            completeStep(p, q);
            return;
        }
    }

    @EventHandler
    public void onMobKilled(MobKilledEvent e) {
        Player killer = e.killer();
        if (killer == null || !killer.isOnline()) {
            return;
        }
        String mobId = e.mob().def.id();
        Set<Player> credited = new LinkedHashSet<>();
        credited.add(killer);
        for (Player mate : plugin.parties().nearby(killer, 40)) {
            credited.add(mate);
        }
        for (Player p : credited) {
            QuestProgress pr = store.get(p.getUniqueId());
            for (Map.Entry<String, Active> en : new ArrayList<>(pr.active.entrySet())) {
                QuestDef q = registry.quest(en.getKey());
                Active a = en.getValue();
                StepDef s = step(q, a);
                if (s == null || !s.mobs().contains(mobId)) {
                    continue;
                }
                if (s.type() == StepType.TUER) {
                    a.count++;
                } else if (s.type() == StepType.RAMASSER) {
                    if (ThreadLocalRandom.current().nextDouble() >= s.chance()) {
                        continue;
                    }
                    a.count++;
                    p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.3f);
                } else {
                    continue;
                }
                pr.dirty = true;
                if (a.count >= s.count()) {
                    completeStep(p, q);
                } else {
                    String what = s.type() == StepType.RAMASSER && s.item() != null ? "+1 " + s.item() : s.text();
                    plugin.hud().flash(p, "<#FFD66E>" + Text.esc(what) + " <white>" + a.count + "/" + s.count(), 40);
                }
            }
        }
    }

    /** Réapparition près de l'Éclat d'ancrage lié (après le choix de la ville par OrbitMMO). */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        QuestProgress pr = store.get(p.getUniqueId());
        Location at = points.location(pr.anchor);
        if (at == null) {
            return;
        }
        e.setRespawnLocation(at.clone().add(1.2, 0, 0));
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) {
                Text.title(p, "<#FF5C5C><b>Vous êtes tombé.</b>", "<#FFE9A8>La lumière de l'Éclat vous ramène.", 5, 50, 15);
                p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 1.4f);
            }
        }, 5L);
    }
}
