package fr.orbit.mmo.quest;

import fr.orbit.mmo.OrbitMMO;
import fr.orbit.mmo.hud.QuestPanel;
import fr.orbit.mmo.item.Items;
import fr.orbit.mmo.mob.MobState;
import fr.orbit.mmo.player.PlayerData;
import fr.orbit.mmo.player.Stats;
import fr.orbit.mmo.quest.QuestDefs.Extra;
import fr.orbit.mmo.quest.QuestDefs.NpcDef;
import fr.orbit.mmo.quest.QuestDefs.QuestDef;
import fr.orbit.mmo.quest.QuestDefs.StepDef;
import fr.orbit.mmo.quest.QuestDefs.StepType;
import fr.orbit.mmo.quest.QuestProgress.Active;
import fr.orbit.mmo.registry.Defs.MobDef;
import fr.orbit.mmo.util.Fx;
import fr.orbit.mmo.util.Text;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Épreuves « en direct » des types de quêtes du 23/11 : escorte, défense, infiltration, pistage, déguisement, survie,
 * transport ; plus le compte à rebours et les objets de quête (livraison, tenue de déguisement).
 * Une épreuve à la fois par joueur (celle de la quête suivie en priorité). Son état (PNJ qui marche, vague, PV de la
 * cible, zone) vit en mémoire : après une reconnexion ou un redémarrage, l'épreuve en cours repart de son début.
 */
final class QuestTrials implements Listener {

    /** Toutes les combien de ticks tourne {@link #tick()}. */
    static final int PERIOD = 2;

    private static final Particle.DustOptions TRACK = new Particle.DustOptions(Fx.color("#6B4A2B"), 1.1f);
    private static final Particle.DustOptions RING = new Particle.DustOptions(Fx.color("#FF6A3D"), 1.3f);
    private static final Particle.DustOptions RING_SAFE = new Particle.DustOptions(Fx.color("#FFD66E"), 1.0f);

    /** État d'une épreuve en cours. */
    private static final class Run {
        final String quest;
        final int step;
        final StepType type;
        final long startedAt = System.currentTimeMillis();
        /** Lieu où le joueur était quand l'épreuve a commencé (retour d'infiltration sans « depart »). */
        Location origin;
        boolean started;
        // Escorte
        LivingEntity walker;
        List<Location> path = List.of();
        int node;
        double wx, wy, wz;
        int stuck;
        int ambushes;
        int ambushDone;
        // Défense, escorte
        TextDisplay label;
        final List<MobState> mobs = new ArrayList<>();
        double hp;
        double maxHp;
        int wave;
        int nextWave;
        // Survie
        double radius;
        final List<Location> fires = new ArrayList<>();
        int firesAt;
        // Transport
        boolean loaded;
        Location dropped;
        ItemDisplay crate;
        ItemDisplay droppedCrate;
        // Infiltration
        long graceUntil;
        int ticks;

        Run(String quest, int step, StepType type) {
            this.quest = quest;
            this.step = step;
            this.type = type;
        }
    }

    private final OrbitMMO plugin;
    private final QuestService svc;
    private final QuestNpcs npcs;
    private final NamespacedKey itemKey;
    private final Map<UUID, Run> runs = new HashMap<>();
    /** Épreuve ratée : pas de nouvel essai avant cette heure (ms). */
    private final Map<UUID, Long> pause = new HashMap<>();
    private final Map<UUID, BossBar> bars = new HashMap<>();
    /** Déguisement découvert (on a frappé un monstre de la faction) : sans effet jusqu'à cette heure (ms). */
    private final Map<UUID, Long> exposed = new HashMap<>();
    private int counter;

    QuestTrials(OrbitMMO plugin, QuestService svc, QuestNpcs npcs) {
        this.plugin = plugin;
        this.svc = svc;
        this.npcs = npcs;
        this.itemKey = new NamespacedKey(plugin, "quest_item");
    }

    // ------------------------------------------------------------------ Boucle

    void tick() {
        long now = System.currentTimeMillis();
        counter++;
        for (Player p : Bukkit.getOnlinePlayers()) {
            QuestProgress pr = svc.progress(p);
            Pick pick = pick(pr);
            if (counter % 10 == 0) {
                calm(p);
            }
            Run run = runs.get(p.getUniqueId());
            if (run != null && (pick == null || !pick.q.id().equals(run.quest) || pick.a.step != run.step)) {
                clear(p);
                run = null;
            }
            if (pick != null && !svc.talking(p) && pause.getOrDefault(p.getUniqueId(), 0L) <= now && !p.isDead()) {
                if (run == null && pick.s.type().ordinal() > StepType.ANCRAGE.ordinal()) {
                    run = new Run(pick.q.id(), pick.a.step, pick.s.type());
                    run.origin = p.getLocation();
                    runs.put(p.getUniqueId(), run);
                }
                if (run != null) {
                    run.ticks++;
                    step(p, pr, pick, run);
                }
            }
            bar(p, pr, pick, runs.get(p.getUniqueId()), now);
        }
    }

    private record Pick(QuestDef q, Active a, StepDef s) {
    }

    /**
     * Les monstres rendus passifs (tenue portée) ou les gardes d'une infiltration lâchent le joueur, même si le ciblage
     * est venu d'ailleurs que d'un événement (MobService pose ses cibles lui-même).
     */
    private void calm(Player p) {
        List<String> calm = disguisedFor(p);
        Run run = runs.get(p.getUniqueId());
        List<String> guards = null;
        if (run != null && run.type == StepType.INFILTRER) {
            StepDef s = QuestService.step(svc.registry().quest(run.quest), svc.progress(p).active.get(run.quest));
            guards = s == null ? null : s.mobs();
        }
        if (calm == null && guards == null) {
            return;
        }
        for (Entity e : p.getNearbyEntities(32, 12, 32)) {
            if (!(e instanceof Mob m) || !p.equals(m.getTarget())) {
                continue;
            }
            MobState ms = plugin.mobs().state(e);
            if (ms != null && ((calm != null && calm.contains(ms.def.id())) || (guards != null && guards.contains(ms.def.id())))) {
                m.setTarget(null);
            }
        }
    }

    /** L'étape à jouer : celle de la quête suivie si c'est un des nouveaux types, sinon la première qui l'est. */
    private Pick pick(QuestProgress pr) {
        String tracked = svc.trackedId(pr);
        Pick first = null;
        for (Map.Entry<String, Active> e : pr.active.entrySet()) {
            QuestDef q = svc.registry().quest(e.getKey());
            StepDef s = QuestService.step(q, e.getValue());
            if (s == null || !live(s.type())) {
                continue;
            }
            Pick pk = new Pick(q, e.getValue(), s);
            if (e.getKey().equals(tracked)) {
                return pk;
            }
            if (first == null) {
                first = pk;
            }
        }
        return first;
    }

    static boolean live(StepType t) {
        return switch (t) {
            case ESCORTE, DEFENSE, INFILTRER, PISTER, DEGUISEMENT, SURVIE, TRANSPORT -> true;
            default -> false;
        };
    }

    private void step(Player p, QuestProgress pr, Pick pk, Run run) {
        switch (pk.s.type()) {
            case ESCORTE -> escort(p, pk, run);
            case DEFENSE -> defense(p, pk, run);
            case INFILTRER -> infiltrate(p, pk, run);
            case PISTER -> track(p, pr, pk);
            case DEGUISEMENT -> disguise(p, pk);
            case SURVIE -> survive(p, pk, run);
            case TRANSPORT -> transport(p, pk, run);
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ Escorte

    private void escort(Player p, Pick pk, Run run) {
        StepDef s = pk.s;
        Extra x = s.x();
        NpcDef def = svc.registry().npc(s.npc());
        if (def == null || x.path().isEmpty()) {
            return;
        }
        if (!run.started) {
            Location start = svc.points().location(x.start() != null ? x.start() : def.point());
            List<Location> path = new ArrayList<>();
            for (String pt : x.path()) {
                Location l = svc.points().location(pt);
                if (l != null) {
                    path.add(l);
                }
            }
            if (start == null || path.isEmpty() || !start.getWorld().equals(p.getWorld())
                    || start.distanceSquared(p.getLocation()) > 20 * 20 || !loaded(start)) {
                return;
            }
            run.path = path;
            run.wx = start.getX();
            run.wy = start.getY();
            run.wz = start.getZ();
            run.maxHp = x.hp();
            run.hp = x.hp();
            run.ambushes = s.mobs().isEmpty() ? 0 : x.waves() > 0 ? x.waves() : 2;
            run.started = true;
            Text.msg(p, "<#FFD66E>" + Text.esc(svc.tr(p, def.name())) + "</#FFD66E> <#A9B1BC>"
                    + svc.ui(p, "se met en route. Restez près de lui.", "sets off. Stay close."));
        }
        World w = run.path.get(0).getWorld();
        if (run.walker == null || !run.walker.isValid()) {
            if (!w.isChunkLoaded((int) Math.floor(run.wx) >> 4, (int) Math.floor(run.wz) >> 4)) {
                return;
            }
            run.walker = npcs.spawnWalker(def, new Location(w, run.wx, run.wy, run.wz));
            if (run.walker == null) {
                return;
            }
        }
        Location at = run.walker.getLocation();
        if (!at.getWorld().equals(p.getWorld()) || at.distanceSquared(p.getLocation()) > 64 * 64) {
            fail(p, run, svc.ui(p, "Vous l'avez laissé seul.", "You left them alone."),
                    svc.ui(p, svc.tr(p, def.name()) + " retourne au départ.", svc.tr(p, def.name()) + " goes back to the start."));
            return;
        }
        // Embuscades : à intervalles réguliers le long du chemin.
        double progress = (double) run.node / run.path.size();
        if (run.ambushDone < run.ambushes && progress >= (run.ambushDone + 1.0) / (run.ambushes + 1.0) && alive(run) == 0) {
            run.ambushDone++;
            spawnAround(p, s, at, 8, 12, s.count(), run);
            Text.title(p, "", "<#FF8A5C>" + svc.ui(p, "Embuscade !", "Ambush!"), 5, 30, 10);
            p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 0.6f, 1.2f);
        }
        steer(run, at);
        hurt(p, run, at, x.dps());
        if (run.hp <= 0) {
            fail(p, run, svc.ui(p, svc.tr(p, def.name()) + " est tombé.", svc.tr(p, def.name()) + " has fallen."),
                    svc.ui(p, "On recommence depuis le départ.", "Starting over."));
            return;
        }
        // Il avance si quelqu'un l'accompagne et qu'aucun ennemi n'est sur lui.
        boolean escorted = at.distanceSquared(p.getLocation()) <= 10 * 10;
        boolean threatened = false;
        for (MobState ms : run.mobs) {
            if (!ms.dead && ms.entity.isValid() && ms.entity.getLocation().distanceSquared(at) < 6 * 6) {
                threatened = true;
                break;
            }
        }
        if (!escorted || threatened) {
            return;
        }
        Location goal = run.path.get(run.node);
        double dx = goal.getX() - run.wx;
        double dz = goal.getZ() - run.wz;
        double dist = Math.sqrt(dx * dx + dz * dz);
        double stepLen = x.speed() * PERIOD / 20.0;
        if (dist <= Math.max(0.6, stepLen)) {
            run.wx = goal.getX();
            run.wz = goal.getZ();
            double gy = ground(w, run.wx, run.wz, goal.getY());
            run.wy = Double.isNaN(gy) ? goal.getY() : gy;
            run.node++;
            if (run.node >= run.path.size()) {
                removeRun(run);
                runs.remove(p.getUniqueId());
                svc.completeStep(p, pk.q);
                return;
            }
        } else {
            double nx = run.wx + dx / dist * stepLen;
            double nz = run.wz + dz / dist * stepLen;
            double ny = ground(w, nx, nz, run.wy);
            if (Double.isNaN(ny) || Math.abs(ny - run.wy) > 1.3) {
                // Mur ou trou : il cherche un moment, puis rejoint le point suivant (pas de quête bloquée).
                if (++run.stuck > 30) {
                    run.stuck = 0;
                    run.wx = goal.getX();
                    run.wy = goal.getY();
                    run.wz = goal.getZ();
                }
            } else {
                run.stuck = 0;
                run.wx = nx;
                run.wy = ny;
                run.wz = nz;
            }
        }
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        run.walker.teleport(new Location(w, run.wx, run.wy, run.wz, yaw, 0f));
    }

    // ------------------------------------------------------------------ Défense

    private void defense(Player p, Pick pk, Run run) {
        StepDef s = pk.s;
        Extra x = s.x();
        Location at = svc.points().location(s.point());
        if (at == null || !at.getWorld().equals(p.getWorld())) {
            return;
        }
        double d2 = at.distanceSquared(p.getLocation());
        if (!run.started) {
            if (d2 > s.radius() * s.radius() || !loaded(at)) {
                return;
            }
            run.started = true;
            run.maxHp = x.hp();
            run.hp = x.hp();
            run.wave = 0;
            run.nextWave = 0;
            run.label = label(at.clone().add(0, 2.6, 0));
            Text.title(p, "<#FFD66E><b>" + svc.ui(p, "Défendez !", "Defend!") + "</b>",
                    "<#A9B1BC>" + Text.esc(targetName(p, s)), 5, 40, 10);
            p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 0.7f, 1f);
        }
        if (d2 > Math.pow(Math.max(40, s.radius() * 3), 2)) {
            fail(p, run, svc.ui(p, "Vous avez abandonné la défense.", "You abandoned the defence."),
                    svc.ui(p, "Revenez pour recommencer.", "Come back to start again."));
            return;
        }
        boolean timed = x.duration() > 0;
        long elapsed = (System.currentTimeMillis() - run.startedAt) / 1000;
        if (timed && elapsed >= x.duration()) {
            removeRun(run);
            runs.remove(p.getUniqueId());
            svc.completeStep(p, pk.q);
            return;
        }
        run.nextWave -= PERIOD;
        if (timed) {
            if (run.nextWave <= 0) {
                run.wave++;
                pk.a.count = run.wave;
                run.nextWave = x.interval() * 20;
                spawnWave(p, s, at, run);
            }
        } else if (alive(run) == 0) {
            if (run.wave >= x.waves()) {
                removeRun(run);
                runs.remove(p.getUniqueId());
                svc.completeStep(p, pk.q);
                return;
            }
            if (run.nextWave > 60) {
                run.nextWave = 60;
            }
            if (run.nextWave <= 0) {
                run.wave++;
                pk.a.count = run.wave;
                run.nextWave = 60;
                spawnWave(p, s, at, run);
                if (run.wave > 1) {
                    Text.title(p, "", "<#FF8A5C>" + svc.ui(p, "Vague ", "Wave ") + run.wave + "/" + x.waves(), 5, 30, 10);
                }
            }
        }
        steer(run, at);
        hurt(p, run, at, x.dps());
        if (run.label != null && run.ticks % 5 == 0) {
            run.label.text(Text.mm("<#FFE9A8><b>" + Text.esc(targetName(p, s)) + "</b>\n" + hpBar(run.hp / run.maxHp)));
        }
        if (run.hp <= 0) {
            fail(p, run, svc.ui(p, "Ils ont percé !", "They broke through!"),
                    svc.ui(p, "Revenez pour recommencer la défense.", "Come back to start the defence again."));
        }
    }

    private void spawnWave(Player p, StepDef s, Location at, Run run) {
        Location from = s.x().spawnPoint() == null ? null : svc.points().location(s.x().spawnPoint());
        if (from != null) {
            spawnAround(p, s, from, 0, 4, s.count(), run);
        } else {
            spawnAround(p, s, at, 14, 20, s.count(), run);
        }
        p.playSound(p.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 0.4f, 0.7f);
    }

    private String targetName(Player p, StepDef s) {
        String n = s.x().targetName();
        return n == null ? svc.ui(p, "La cible", "The target") : svc.tr(p, n);
    }

    // ------------------------------------------------------------------ Infiltration

    private void infiltrate(Player p, Pick pk, Run run) {
        StepDef s = pk.s;
        Location goal = svc.points().location(s.point());
        if (goal == null || !goal.getWorld().equals(p.getWorld())) {
            return;
        }
        if (goal.distanceSquared(p.getLocation()) <= s.radius() * s.radius()) {
            runs.remove(p.getUniqueId());
            svc.completeStep(p, pk.q);
            return;
        }
        if (run.ticks % 3 != 0 || System.currentTimeMillis() < run.graceUntil) {
            return;
        }
        double sight = s.x().sight() * (p.isSneaking() ? 0.6 : 1.0);
        for (Entity e : p.getNearbyEntities(sight, 6, sight)) {
            MobState ms = plugin.mobs().state(e);
            if (ms == null || ms.dead || !s.mobs().contains(ms.def.id()) || !(e instanceof LivingEntity guard)) {
                continue;
            }
            double dist = e.getLocation().distance(p.getLocation());
            if (dist > sight || !guard.hasLineOfSight(p)) {
                continue;
            }
            Vector look = guard.getLocation().getDirection().setY(0);
            Vector to = p.getLocation().toVector().subtract(guard.getLocation().toVector()).setY(0);
            boolean inFront = look.lengthSquared() < 1.0E-4 || to.lengthSquared() < 1.0E-4
                    || look.normalize().dot(to.normalize()) > 0.35;
            if (dist < 2.5 || inFront) {
                spotted(p, s, run, guard);
                return;
            }
        }
    }

    private void spotted(Player p, StepDef s, Run run, LivingEntity guard) {
        guard.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, guard.getEyeLocation().add(0, 0.6, 0), 4, 0.2, 0.2, 0.2, 0);
        Text.title(p, "<#FF5C5C><b>" + svc.ui(p, "Repéré !", "Spotted!") + "</b>",
                "<#A9B1BC>" + svc.ui(p, "Retour au point de départ.", "Back to the starting point."), 5, 40, 15);
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 0.7f);
        Location back = s.x().start() == null ? run.origin : svc.points().location(s.x().start());
        if (back != null) {
            p.teleport(back);
        }
        for (Entity e : p.getNearbyEntities(32, 12, 32)) {
            if (e instanceof Mob m && p.equals(m.getTarget())) {
                m.setTarget(null);
            }
        }
        run.graceUntil = System.currentTimeMillis() + 3000;
    }

    // ------------------------------------------------------------------ Pistage

    private void track(Player p, QuestProgress pr, Pick pk) {
        List<String> traces = pk.s.x().path();
        int next = Math.max(0, Math.min(traces.size() - 1, pk.a.count));
        Location at = svc.points().location(traces.get(next));
        if (at == null || !at.getWorld().equals(p.getWorld())) {
            return;
        }
        if (at.distanceSquared(p.getLocation()) <= pk.s.radius() * pk.s.radius()) {
            pk.a.count = next + 1;
            pr.dirty = true;
            p.playSound(p.getLocation(), Sound.BLOCK_ROOTED_DIRT_BREAK, 1f, 0.8f);
            if (pk.a.count >= traces.size()) {
                runs.remove(p.getUniqueId());
                svc.completeStep(p, pk.q);
            } else {
                plugin.hud().flash(p, "<#C9A27A>" + svc.ui(p, "Traces", "Tracks") + " <white>" + pk.a.count + "/" + traces.size(), 40);
            }
            return;
        }
        if (counter % 2 != 0) {
            return;
        }
        // Empreintes : du dernier repère trouvé (ou du joueur) jusqu'au suivant, seulement pour ce joueur.
        Location from = next == 0 ? p.getLocation() : svc.points().location(traces.get(next - 1));
        if (from == null || !from.getWorld().equals(at.getWorld())) {
            from = p.getLocation();
        }
        double len = from.distance(at);
        for (double t = 0; t <= len; t += 1.3) {
            double k = len < 0.01 ? 1 : t / len;
            double x = from.getX() + (at.getX() - from.getX()) * k;
            double z = from.getZ() + (at.getZ() - from.getZ()) * k;
            double nearY = from.getY() + (at.getY() - from.getY()) * k;
            if ((x - p.getX()) * (x - p.getX()) + (z - p.getZ()) * (z - p.getZ()) > 22 * 22) {
                continue;
            }
            double y = ground(at.getWorld(), x, z, nearY + 1);
            if (Double.isNaN(y)) {
                continue;
            }
            double side = ((int) (t / 1.3) % 2 == 0 ? 0.18 : -0.18);
            double nx = -(at.getZ() - from.getZ()) / Math.max(0.01, len);
            double nz = (at.getX() - from.getX()) / Math.max(0.01, len);
            p.spawnParticle(Particle.DUST, x + nx * side, y + 0.05, z + nz * side, 1, 0.02, 0, 0.02, 0, TRACK);
        }
        p.spawnParticle(Particle.DUST, at.clone().add(0, 0.3, 0), 6, 0.4, 0.1, 0.4, 0, TRACK);
    }

    // ------------------------------------------------------------------ Déguisement

    private void disguise(Player p, Pick pk) {
        if (counter % 20 == 0 && !hasQuestItem(p, pk.q.id(), pk.a.step)) {
            giveItem(p, pk.q, pk.s, pk.a.step, true);
        }
        Location goal = svc.points().location(pk.s.point());
        if (goal == null || !goal.getWorld().equals(p.getWorld())
                || goal.distanceSquared(p.getLocation()) > pk.s.radius() * pk.s.radius()) {
            return;
        }
        if (disguisedFor(p) == null) {
            if (counter % 20 == 0) {
                plugin.hud().flash(p, "<#FF8A5C>" + svc.ui(p, "Portez la tenue pour entrer.", "Wear the outfit to get in."), 40);
            }
            return;
        }
        runs.remove(p.getUniqueId());
        svc.completeStep(p, pk.q);
    }

    /** Monstres que la tenue portée rend passifs (null : pas de tenue, ou déguisement découvert). */
    private List<String> disguisedFor(Player p) {
        if (exposed.getOrDefault(p.getUniqueId(), 0L) > System.currentTimeMillis()) {
            return null;
        }
        for (ItemStack it : p.getInventory().getArmorContents()) {
            String[] tag = tag(it);
            if (tag == null || !"tenue".equals(tag[3])) {
                continue;
            }
            QuestDef q = svc.registry().quest(tag[1]);
            int idx = parse(tag[2]);
            if (q != null && idx >= 0 && idx < q.steps().size() && svc.progress(p).active.containsKey(q.id())) {
                return q.steps().get(idx).mobs();
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ Survie

    private void survive(Player p, Pick pk, Run run) {
        StepDef s = pk.s;
        Extra x = s.x();
        Location c = svc.points().location(s.point());
        if (c == null || !c.getWorld().equals(p.getWorld())) {
            return;
        }
        double d = Math.sqrt(sq(c.getX() - p.getX()) + sq(c.getZ() - p.getZ()));
        if (!run.started) {
            if (d > s.radius()) {
                if (counter % 2 == 0 && d < s.radius() + 30) {
                    ring(p, c, s.radius(), RING_SAFE);
                }
                return;
            }
            run.started = true;
            run.radius = s.radius();
            run.nextWave = 0;
            Text.title(p, "<#FF8A5C><b>" + svc.ui(p, "Tenez bon !", "Hold on!") + "</b>",
                    "<#A9B1BC>" + x.duration() + svc.ui(p, " secondes", " seconds"), 5, 40, 10);
            p.playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 0.7f, 0.6f);
        }
        double elapsed = (System.currentTimeMillis() - run.startedAt) / 1000.0;
        if (elapsed >= x.duration()) {
            removeRun(run);
            runs.remove(p.getUniqueId());
            svc.completeStep(p, pk.q);
            return;
        }
        boolean shrink = !"brule".equals(x.mode()) && !"brûle".equals(x.mode());
        if (shrink) {
            run.radius = s.radius() - (s.radius() - Math.min(s.radius(), x.endRadius())) * Math.min(1, elapsed / x.duration());
        }
        if (d > Math.max(40, s.radius() * 3)) {
            fail(p, run, svc.ui(p, "Vous avez fui la zone.", "You fled the zone."), svc.ui(p, "Revenez pour recommencer.", "Come back to start again."));
            return;
        }
        if (run.ticks % 5 == 0) {
            ring(p, c, run.radius, RING);
        }
        PlayerData pd = plugin.players().loaded(p.getUniqueId());
        if (run.ticks % 10 == 0 && pd != null && d > run.radius) {
            plugin.combat().damagePlayer(p, Stats.maxHp(pd) * x.dps() / 100.0, null, true);
            p.getWorld().spawnParticle(Particle.FLAME, p.getLocation().add(0, 1, 0), 8, 0.3, 0.5, 0.3, 0.02);
            plugin.hud().flash(p, "<#FF5C5C>" + svc.ui(p, "Hors de la zone !", "Outside the zone!"), 20);
        }
        if (!shrink) {
            // Foyers : marqués au sol, puis ils s'embrasent.
            if (run.fires.isEmpty() && run.ticks % 30 == 0) {
                for (int i = 0; i < 4; i++) {
                    double a = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
                    double r = ThreadLocalRandom.current().nextDouble() * run.radius;
                    double fx = c.getX() + Math.cos(a) * r;
                    double fz = c.getZ() + Math.sin(a) * r;
                    double fy = ground(c.getWorld(), fx, fz, c.getY() + 2);
                    if (!Double.isNaN(fy)) {
                        run.fires.add(new Location(c.getWorld(), fx, fy, fz));
                    }
                }
                // Un foyer vise toujours le joueur : on ne peut pas rester immobile.
                run.fires.add(p.getLocation());
                run.firesAt = run.ticks;
            }
            if (!run.fires.isEmpty()) {
                if (run.ticks - run.firesAt < 15) {
                    for (Location f : run.fires) {
                        p.spawnParticle(Particle.SMOKE, f.clone().add(0, 0.1, 0), 6, 1.0, 0.05, 1.0, 0.01);
                        p.spawnParticle(Particle.DUST, f.clone().add(0, 0.1, 0), 4, 1.0, 0.05, 1.0, 0, RING);
                    }
                } else {
                    for (Location f : run.fires) {
                        f.getWorld().spawnParticle(Particle.FLAME, f.clone().add(0, 0.4, 0), 40, 1.0, 0.4, 1.0, 0.05);
                        if (pd != null && f.distanceSquared(p.getLocation()) < 2.5 * 2.5) {
                            plugin.combat().damagePlayer(p, Stats.maxHp(pd) * x.dps() * 2 / 100.0, null, true);
                        }
                    }
                    p.playSound(p.getLocation(), Sound.ITEM_FIRECHARGE_USE, 0.6f, 0.8f);
                    run.fires.clear();
                }
            }
        }
        // Monstres facultatifs, par vagues dans la zone.
        if (!s.mobs().isEmpty()) {
            run.nextWave -= PERIOD;
            if (run.nextWave <= 0) {
                run.nextWave = x.interval() * 20;
                spawnAround(p, s, c, 0, Math.max(2, run.radius - 1), s.count(), run);
            }
        }
    }

    // ------------------------------------------------------------------ Transport

    private void transport(Player p, Pick pk, Run run) {
        StepDef s = pk.s;
        Location goal = svc.points().location(s.point());
        if (goal == null) {
            return;
        }
        boolean riding = plugin.mounts().riding(p) || p.getVehicle() != null;
        if (!run.started) {
            run.started = true;
            run.dropped = s.x().start() == null ? null : svc.points().location(s.x().start());
        }
        if (!run.loaded) {
            Location pick = run.dropped;
            boolean canLoad = riding && (pick == null || (pick.getWorld().equals(p.getWorld())
                    && pick.distanceSquared(p.getLocation()) <= 3.5 * 3.5));
            if (canLoad) {
                run.loaded = true;
                run.dropped = null;
                if (run.droppedCrate != null) {
                    run.droppedCrate.remove();
                    run.droppedCrate = null;
                }
                p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 0.6f);
                plugin.hud().flash(p, "<#5EE89A>" + svc.ui(p, "Cargaison chargée", "Cargo loaded")
                        + " <white>" + Text.esc(cargoName(p, s)), 50);
            } else if (pick != null && run.droppedCrate == null && loaded(pick)) {
                run.droppedCrate = crate(pick.clone().add(0, 0.5, 0), s.x().material(), 0.8f);
            } else if (pick != null && run.ticks % 10 == 0 && pick.getWorld().equals(p.getWorld())) {
                p.spawnParticle(Particle.END_ROD, pick.clone().add(0, 1.2, 0), 3, 0.2, 0.4, 0.2, 0.01);
            }
            return;
        }
        if (!riding) {
            drop(p, s, run, svc.ui(p, "Vous êtes descendu : la cargaison est tombée.", "You dismounted: the cargo fell."));
            return;
        }
        if (run.crate == null || !run.crate.isValid()) {
            run.crate = crate(p.getLocation().add(0, 2, 0), s.x().material(), 0.5f);
            p.addPassenger(run.crate);
        }
        if (goal.getWorld().equals(p.getWorld()) && goal.distanceSquared(p.getLocation()) <= s.radius() * s.radius()) {
            removeRun(run);
            runs.remove(p.getUniqueId());
            svc.completeStep(p, pk.q);
        }
    }

    private void drop(Player p, StepDef s, Run run, String why) {
        run.loaded = false;
        run.dropped = p.getLocation();
        if (run.crate != null) {
            run.crate.remove();
            run.crate = null;
        }
        Text.title(p, "<#FF8A5C><b>" + svc.ui(p, "Cargaison perdue", "Cargo lost") + "</b>", "<#A9B1BC>" + Text.esc(why), 5, 40, 10);
        p.playSound(p.getLocation(), Sound.BLOCK_WOOD_BREAK, 1f, 0.7f);
        Text.msg(p, "<#FF8A5C>" + Text.esc(why) + "</#FF8A5C> <#A9B1BC>" + svc.ui(p,
                "Retournez la chercher à dos de monture.", "Ride back to pick it up."));
    }

    private String cargoName(Player p, StepDef s) {
        return s.item() == null ? svc.ui(p, "la cargaison", "the cargo") : svc.tr(p, s.item());
    }

    // ------------------------------------------------------------------ Monstres d'épreuve

    private void spawnAround(Player p, StepDef s, Location center, double minR, double maxR, int count, Run run) {
        PlayerData d = plugin.players().loaded(p.getUniqueId());
        int level = s.x().level() > 0 ? s.x().level() : d == null ? 1 : d.level;
        for (String id : s.mobs()) {
            MobDef def = svc.registry().mob(id);
            if (def == null) {
                continue;
            }
            for (int i = 0; i < count; i++) {
                Location spot = ring(center, minR, maxR);
                if (spot == null) {
                    continue;
                }
                MobState ms = plugin.mobs().spawn(def, level, spot, plugin.floors().floorOf(spot.getWorld()), null, 1);
                if (ms != null) {
                    run.mobs.add(ms);
                }
            }
        }
    }

    /** Les monstres sans joueur proche marchent vers la cible. */
    private void steer(Run run, Location target) {
        if (run.ticks % 10 != 0) {
            return;
        }
        for (MobState ms : run.mobs) {
            if (ms.dead || !ms.entity.isValid() || !(ms.entity instanceof Mob m)) {
                continue;
            }
            LivingEntity t = m.getTarget();
            if (t != null && t.getLocation().distanceSquared(m.getLocation()) < 7 * 7) {
                continue;
            }
            if (m.getLocation().distanceSquared(target) > 2 * 2) {
                m.setTarget(null);
                m.getPathfinder().moveTo(target, 1.1);
            }
        }
    }

    /** Chaque monstre au contact de la cible lui retire « degats » PV par seconde. */
    private void hurt(Player p, Run run, Location target, double dps) {
        run.mobs.removeIf(ms -> ms.dead || !ms.entity.isValid());
        if (run.ticks % 10 != 0) {
            return;
        }
        boolean hit = false;
        for (MobState ms : run.mobs) {
            if (ms.entity.getLocation().distanceSquared(target) <= 2.6 * 2.6) {
                run.hp -= dps / 2;
                ms.entity.swingMainHand();
                hit = true;
            }
        }
        if (hit) {
            target.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, target.clone().add(0, 1.2, 0), 3, 0.3, 0.3, 0.3, 0.05);
            if (run.walker != null && run.walker.isValid()) {
                run.walker.playHurtAnimation(0);
            }
            p.playSound(target, Sound.ENTITY_PLAYER_HURT, 0.4f, 1.3f);
        }
    }

    private static int alive(Run run) {
        int n = 0;
        for (MobState ms : run.mobs) {
            if (!ms.dead && ms.entity.isValid()) {
                n++;
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ Échec, nettoyage

    private void fail(Player p, Run run, String title, String sub) {
        removeRun(run);
        runs.remove(p.getUniqueId());
        pause.put(p.getUniqueId(), System.currentTimeMillis() + 4000);
        Text.title(p, "<#FF5C5C><b>" + Text.esc(title) + "</b>", "<#A9B1BC>" + Text.esc(sub), 5, 50, 15);
        Text.msg(p, "<#FF5C5C>✘ " + Text.esc(title) + "</#FF5C5C> <#A9B1BC>" + Text.esc(sub));
        p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.35f, 1.6f);
    }

    private void removeRun(Run run) {
        if (run.walker != null) {
            run.walker.remove();
        }
        if (run.label != null) {
            run.label.remove();
        }
        if (run.crate != null) {
            run.crate.remove();
        }
        if (run.droppedCrate != null) {
            run.droppedCrate.remove();
        }
        for (MobState ms : run.mobs) {
            if (!ms.dead && ms.entity.isValid()) {
                plugin.mobs().remove(ms);
            }
        }
        run.mobs.clear();
    }

    /** Arrête l'épreuve en cours du joueur (étape changée, quête finie ou effacée). */
    void clear(Player p) {
        Run run = runs.remove(p.getUniqueId());
        if (run != null) {
            removeRun(run);
        }
    }

    /** Le temps d'un contre la montre est écoulé : l'épreuve en cours repart de zéro. */
    void restart(Player p) {
        clear(p);
    }

    void quit(UUID id) {
        Run run = runs.remove(id);
        if (run != null) {
            removeRun(run);
        }
        pause.remove(id);
        exposed.remove(id);
        BossBar b = bars.remove(id);
        Player p = Bukkit.getPlayer(id);
        if (b != null && p != null) {
            p.hideBossBar(b);
        }
    }

    void shutdown() {
        for (Run run : runs.values()) {
            removeRun(run);
        }
        runs.clear();
        for (Map.Entry<UUID, BossBar> e : bars.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null) {
                p.hideBossBar(e.getValue());
            }
        }
        bars.clear();
    }

    // ------------------------------------------------------------------ Suivi (panneau, barre, traînée)

    /** Lieu à montrer pour une étape en direct (null : le lieu habituel de l'étape). */
    Location target(Player p, String questId, StepDef s, Active a) {
        Run run = runs.get(p.getUniqueId());
        boolean mine = run != null && run.quest.equals(questId) && a != null && run.step == a.step;
        switch (s.type()) {
            case ESCORTE -> {
                if (mine && run.walker != null && run.walker.isValid()) {
                    return run.walker.getLocation();
                }
                NpcDef def = svc.registry().npc(s.npc());
                String start = s.x().start() != null ? s.x().start() : def == null ? null : def.point();
                return svc.points().location(start);
            }
            case PISTER -> {
                List<String> t = s.x().path();
                return t.isEmpty() ? null : svc.points().location(t.get(Math.max(0, Math.min(t.size() - 1, a == null ? 0 : a.count))));
            }
            case TRANSPORT -> {
                if (mine && !run.loaded && run.dropped != null) {
                    return run.dropped;
                }
                return svc.points().location(s.point());
            }
            default -> {
                return null;
            }
        }
    }

    /** Lignes d'état sous l'objectif (PV, temps, cargaison…). */
    void status(Player p, String questId, StepDef s, Active a, List<QuestPanel.Row> out) {
        Run run = runs.get(p.getUniqueId());
        boolean mine = run != null && run.quest.equals(questId) && run.step == a.step && run.started;
        if (mine && (s.type() == StepType.ESCORTE || s.type() == StepType.DEFENSE)) {
            String who = s.type() == StepType.ESCORTE ? nameOf(p, s.npc()) : targetName(p, s);
            out.add(QuestPanel.Row.of(run.hp / run.maxHp < 0.35 ? QuestPanel.Kind.ALERT : QuestPanel.Kind.STATUS,
                    who + " " + Math.max(0, (int) Math.ceil(run.hp / run.maxHp * 100)) + " %"));
        }
        if (mine && s.type() == StepType.SURVIE) {
            int left = (int) Math.max(0, s.x().duration() - (System.currentTimeMillis() - run.startedAt) / 1000);
            out.add(QuestPanel.Row.of(QuestPanel.Kind.STATUS, svc.ui(p, "Tenir encore ", "Hold for ") + clock(left)));
        }
        if (s.type() == StepType.TRANSPORT) {
            boolean loaded = mine && run.loaded;
            out.add(QuestPanel.Row.of(loaded ? QuestPanel.Kind.STATUS : QuestPanel.Kind.ALERT, loaded
                    ? svc.ui(p, "Cargaison à bord", "Cargo on board")
                    : svc.ui(p, "Chargez la cargaison (monture)", "Load the cargo (mount)")));
        }
        if (s.type() == StepType.DEGUISEMENT) {
            boolean on = disguisedFor(p) != null;
            out.add(QuestPanel.Row.of(on ? QuestPanel.Kind.STATUS : QuestPanel.Kind.ALERT,
                    on ? svc.ui(p, "Déguisé", "Disguised") : svc.ui(p, "Mettez la tenue", "Put on the outfit")));
        }
        if (s.type() == StepType.INFILTRER) {
            out.add(QuestPanel.Row.of(QuestPanel.Kind.STATUS, svc.ui(p, "Accroupi : moins visible", "Sneak: harder to see")));
        }
        if (a.deadline > 0) {
            int left = (int) Math.max(0, (a.deadline - System.currentTimeMillis() + 999) / 1000);
            // Pas de « ⏱ » ici : la police du cadre de quêtes ne l'a pas.
            out.add(QuestPanel.Row.of(left <= 10 ? QuestPanel.Kind.ALERT : QuestPanel.Kind.STATUS,
                    svc.ui(p, "Temps : ", "Time: ") + clock(left)));
        }
    }

    private String nameOf(Player p, String npcId) {
        NpcDef def = svc.registry().npc(npcId);
        return def == null ? "?" : svc.tr(p, def.name());
    }

    /** Barre en haut de l'écran : PV de l'escorté ou de la cible, temps de survie, compte à rebours. */
    private void bar(Player p, QuestProgress pr, Pick pk, Run run, long now) {
        String text = null;
        float progress = 1f;
        BossBar.Color color = BossBar.Color.YELLOW;
        if (run != null && run.started && pk != null && run.quest.equals(pk.q.id())) {
            switch (run.type) {
                case ESCORTE, DEFENSE -> {
                    String who = run.type == StepType.ESCORTE ? nameOf(p, pk.s.npc()) : targetName(p, pk.s);
                    progress = (float) Math.max(0, Math.min(1, run.hp / run.maxHp));
                    text = "<#FFE9A8>" + Text.esc(who) + "</#FFE9A8>  <white>" + Math.max(0, (int) Math.ceil(progress * 100)) + " %";
                    if (run.type == StepType.DEFENSE && pk.s.x().waves() > 0 && pk.s.x().duration() <= 0) {
                        text += "  <#8C96A3>·</#8C96A3>  " + svc.ui(p, "vague ", "wave ") + Math.max(1, run.wave) + "/" + pk.s.x().waves();
                    } else if (run.type == StepType.DEFENSE && pk.s.x().duration() > 0) {
                        long left = Math.max(0, pk.s.x().duration() - (now - run.startedAt) / 1000);
                        text += "  <#8C96A3>·</#8C96A3>  " + clock((int) left);
                    }
                    color = progress < 0.35 ? BossBar.Color.RED : BossBar.Color.GREEN;
                }
                case SURVIE -> {
                    long left = Math.max(0, pk.s.x().duration() - (now - run.startedAt) / 1000);
                    progress = (float) Math.max(0, Math.min(1, left / (double) Math.max(1, pk.s.x().duration())));
                    text = "<#FF8A5C>" + svc.ui(p, "Survivre", "Survive") + "</#FF8A5C>  <white>" + clock((int) left);
                    color = BossBar.Color.RED;
                }
                default -> {
                }
            }
        }
        // Contre la montre de la quête suivie.
        String id = svc.trackedId(pr);
        Active a = id == null ? null : pr.active.get(id);
        StepDef s = a == null ? null : QuestService.step(svc.registry().quest(id), a);
        if (s != null && a.deadline > 0 && s.timer() > 0) {
            int left = (int) Math.max(0, (a.deadline - now + 999) / 1000);
            String chrono = "<white>⏱ " + clock(left);
            if (text == null) {
                text = chrono;
                progress = (float) Math.max(0, Math.min(1, (a.deadline - now) / (s.timer() * 1000.0)));
                color = left <= 10 ? BossBar.Color.RED : BossBar.Color.YELLOW;
            } else {
                text += "  <#8C96A3>·</#8C96A3>  " + chrono;
            }
        }
        BossBar b = bars.get(p.getUniqueId());
        if (text == null) {
            if (b != null) {
                p.hideBossBar(b);
                bars.remove(p.getUniqueId());
            }
            return;
        }
        if (b == null) {
            b = BossBar.bossBar(Text.mm(text), progress, color, BossBar.Overlay.PROGRESS);
            bars.put(p.getUniqueId(), b);
            p.showBossBar(b);
        } else {
            b.name(Text.mm(text));
            b.progress(progress);
            b.color(color);
        }
    }

    static String clock(int seconds) {
        return (seconds / 60) + ":" + (seconds % 60 < 10 ? "0" : "") + (seconds % 60);
    }

    private static String hpBar(double frac) {
        int full = (int) Math.round(Math.max(0, Math.min(1, frac)) * 20);
        String col = frac < 0.35 ? "#FF5C5C" : frac < 0.65 ? "#FFD66E" : "#5EE89A";
        return "<" + col + ">" + "|".repeat(full) + "</" + col + "><#3A3F47>" + "|".repeat(20 - full);
    }

    // ------------------------------------------------------------------ Objets de quête (livraison, tenue)

    /** Début d'une étape : remet l'objet à livrer ou la tenue de déguisement. */
    void enter(Player p, QuestDef q, StepDef s, int step) {
        if ((s.type() == StepType.LIVRER || s.type() == StepType.DEGUISEMENT) && !hasQuestItem(p, q.id(), step)) {
            giveItem(p, q, s, step, false);
        }
    }

    void giveItem(Player p, QuestDef q, StepDef s, int step, boolean again) {
        if (p.getInventory().firstEmpty() < 0) {
            // Jamais au sol (sinon un double tous les 2 s) : on attend une place libre.
            plugin.hud().flash(p, "<#FF8A5C>" + svc.ui(p, "Sac plein : libérez une place pour l'objet de quête.",
                    "Bag full: free a slot for the quest item."), 50);
            return;
        }
        Material mat = s.x() == null ? Material.PAPER : s.x().material();
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        String name = s.item() == null ? svc.ui(p, "Objet de quête", "Quest item") : svc.tr(p, s.item());
        meta.displayName(Text.item("<#FFE9A8>" + Text.esc(name)));
        List<String> lore = new ArrayList<>();
        lore.add("<#A9B1BC><i>" + Text.esc(svc.tr(p, q.name())));
        lore.add(s.type() == StepType.DEGUISEMENT
                ? "<#8C96A3>" + svc.ui(p, "À porter : certains monstres vous laissent passer.", "Wear it: some monsters let you through.")
                : "<#8C96A3>" + svc.ui(p, "Objet de quête : à remettre.", "Quest item: to be delivered."));
        meta.lore(Text.lore(lore));
        if (meta instanceof LeatherArmorMeta lm && s.x() != null && s.x().color() != null) {
            lm.setColor(Fx.color(s.x().color()));
        }
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING,
                p.getUniqueId() + "|" + q.id() + "|" + step + "|" + (s.type() == StepType.DEGUISEMENT ? "tenue" : "objet"));
        it.setItemMeta(meta);
        Items.give(p, it);
        Text.msg(p, "<#FFD66E>+</#FFD66E> <white>" + Text.esc(name) + (again
                ? " <#8C96A3>" + svc.ui(p, "(un double vous est remis)", "(a copy is handed to you)") : ""));
    }

    boolean hasQuestItem(Player p, String questId, int step) {
        for (ItemStack it : p.getInventory().getContents()) {
            String[] t = tag(it);
            if (t != null && t[1].equals(questId) && parse(t[2]) == step) {
                return true;
            }
        }
        return false;
    }

    /** Retire l'objet de cette étape (livraison faite). */
    boolean takeQuestItem(Player p, String questId, int step) {
        ItemStack[] all = p.getInventory().getContents();
        for (int i = 0; i < all.length; i++) {
            String[] t = tag(all[i]);
            if (t != null && t[1].equals(questId) && parse(t[2]) == step) {
                p.getInventory().setItem(i, null);
                return true;
            }
        }
        return false;
    }

    /** Retire tous les objets d'une quête (null = de toutes) : quête finie, effacée, nouveau personnage. */
    void removeQuestItems(Player p, String questId) {
        ItemStack[] all = p.getInventory().getContents();
        for (int i = 0; i < all.length; i++) {
            String[] t = tag(all[i]);
            if (t != null && (questId == null || t[1].equals(questId))) {
                p.getInventory().setItem(i, null);
            }
        }
    }

    /** Toutes les 2 s environ : un objet à livrer perdu est rendu. */
    void checkItems(Player p, QuestProgress pr) {
        for (Map.Entry<String, Active> e : pr.active.entrySet()) {
            QuestDef q = svc.registry().quest(e.getKey());
            StepDef s = QuestService.step(q, e.getValue());
            if (s != null && s.type() == StepType.LIVRER && !hasQuestItem(p, q.id(), e.getValue().step)) {
                giveItem(p, q, s, e.getValue().step, true);
            }
        }
    }

    /** [propriétaire, quête, étape, sorte] ou null. */
    private String[] tag(ItemStack it) {
        if (it == null || !it.hasItemMeta()) {
            return null;
        }
        String v = it.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
        if (v == null) {
            return null;
        }
        String[] t = v.split("\\|");
        return t.length == 4 ? t : null;
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // ------------------------------------------------------------------ Événements

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (tag(e.getItemDrop().getItemStack()) != null) {
            e.setCancelled(true);
            Text.msg(e.getPlayer(), "<#8C96A3>" + svc.ui(e.getPlayer(), "Un objet de quête ne se jette pas.", "Quest items cannot be dropped."));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent e) {
        // Les objets de quête restent dans le sac.
        for (java.util.Iterator<ItemStack> it = e.getDrops().iterator(); it.hasNext(); ) {
            ItemStack s = it.next();
            if (tag(s) != null) {
                it.remove();
                e.getItemsToKeep().add(s);
            }
        }
        Player p = e.getPlayer();
        Run run = runs.get(p.getUniqueId());
        if (run != null && run.started && (run.type == StepType.SURVIE || run.type == StepType.DEFENSE || run.type == StepType.ESCORTE)) {
            removeRun(run);
            runs.remove(p.getUniqueId());
            pause.put(p.getUniqueId(), System.currentTimeMillis() + 4000);
            Text.msg(p, "<#FF5C5C>✘ " + svc.ui(p, "Épreuve ratée : vous êtes tombé. Retentez-la quand vous serez prêt.",
                    "Trial failed: you fell. Try again when you are ready."));
        }
    }

    /** Tenue de déguisement : les monstres de la faction ne vous prennent pas pour cible. Infiltration : les gardes non plus, tant qu'ils ne vous ont pas vu. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent e) {
        if (!(e.getTarget() instanceof Player p)) {
            return;
        }
        MobState ms = plugin.mobs().state(e.getEntity());
        if (ms == null) {
            return;
        }
        List<String> calm = disguisedFor(p);
        if (calm != null && calm.contains(ms.def.id())) {
            e.setCancelled(true);
            return;
        }
        Run run = runs.get(p.getUniqueId());
        if (run != null && run.type == StepType.INFILTRER) {
            StepDef s = QuestService.step(svc.registry().quest(run.quest), svc.progress(p).active.get(run.quest));
            if (s != null && s.mobs().contains(ms.def.id())) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHitMob(EntityDamageByEntityEvent e) {
        Entity dmg = e.getDamager();
        if (dmg instanceof Projectile pr && pr.getShooter() instanceof Entity shooter) {
            dmg = shooter;
        }
        if (!(dmg instanceof Player p)) {
            return;
        }
        MobState ms = plugin.mobs().state(e.getEntity());
        List<String> calm = ms == null ? null : disguisedFor(p);
        if (calm != null && calm.contains(ms.def.id())) {
            exposed.put(p.getUniqueId(), System.currentTimeMillis() + 20000);
            Text.title(p, "", "<#FF5C5C>" + svc.ui(p, "Déguisement découvert ! (20 s)", "Disguise blown! (20 s)"), 5, 40, 10);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p) || e.getFinalDamage() <= 0 && !(e instanceof EntityDamageByEntityEvent)) {
            return;
        }
        Run run = runs.get(p.getUniqueId());
        if (run == null || run.type != StepType.TRANSPORT || !run.loaded) {
            return;
        }
        StepDef s = QuestService.step(svc.registry().quest(run.quest), svc.progress(p).active.get(run.quest));
        if (s != null) {
            drop(p, s, run, svc.ui(p, "Touché : la cargaison est tombée.", "Hit: the cargo fell."));
        }
    }

    // ------------------------------------------------------------------ Outils

    private TextDisplay label(Location at) {
        return at.getWorld().spawn(at, TextDisplay.class, d -> {
            d.getPersistentDataContainer().set(npcs.npcKey, PersistentDataType.STRING, "#cible");
            d.setPersistent(false);
            d.setBillboard(Display.Billboard.CENTER);
            d.setBackgroundColor(Color.fromARGB(110, 0, 0, 0));
            d.setShadowed(true);
            d.setAlignment(TextDisplay.TextAlignment.CENTER);
        });
    }

    private ItemDisplay crate(Location at, Material mat, float size) {
        return at.getWorld().spawn(at, ItemDisplay.class, d -> {
            d.getPersistentDataContainer().set(npcs.npcKey, PersistentDataType.STRING, "#cargaison");
            d.setPersistent(false);
            d.setItemStack(new ItemStack(mat.isItem() ? mat : Material.BARREL));
            d.setTransformation(new Transformation(new Vector3f(0, 0.25f, 0), new AxisAngle4f(),
                    new Vector3f(size, size, size), new AxisAngle4f()));
        });
    }

    /** Cercle de particules vu par ce joueur seul. */
    private static void ring(Player p, Location c, double radius, Particle.DustOptions dust) {
        int n = (int) Math.max(24, Math.min(120, radius * 6));
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            double x = c.getX() + Math.cos(a) * radius;
            double z = c.getZ() + Math.sin(a) * radius;
            if ((x - p.getX()) * (x - p.getX()) + (z - p.getZ()) * (z - p.getZ()) > 40 * 40) {
                continue;
            }
            double y = ground(c.getWorld(), x, z, c.getY() + 3);
            p.spawnParticle(Particle.DUST, x, (Double.isNaN(y) ? c.getY() : y) + 0.3, z, 1, 0, 0.2, 0, 0, dust);
        }
    }

    /** Point au sol entre minR et maxR du centre (null si rien de praticable). */
    private static Location ring(Location center, double minR, double maxR) {
        World w = center.getWorld();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 12; attempt++) {
            double a = r.nextDouble() * Math.PI * 2;
            double dist = minR + r.nextDouble() * Math.max(0.1, maxR - minR);
            double x = center.getX() + Math.cos(a) * dist;
            double z = center.getZ() + Math.sin(a) * dist;
            double y = ground(w, x, z, center.getY() + 4);
            if (Double.isNaN(y)) {
                y = ground(w, x, z, center.getY() + 10);
            }
            if (!Double.isNaN(y) && Math.abs(y - center.getY()) < 8) {
                return new Location(w, Math.floor(x) + 0.5, y, Math.floor(z) + 0.5, r.nextFloat() * 360f, 0f);
            }
        }
        return null;
    }

    /** Hauteur des pieds dans la colonne (x, z), en cherchant de nearY + 1 à nearY − 4 ; NaN si rien. */
    static double ground(World w, double x, double z, double nearY) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        if (!w.isChunkLoaded(bx >> 4, bz >> 4)) {
            return Double.NaN;
        }
        int top = (int) Math.floor(nearY) + 1;
        for (int y = top; y >= top - 5; y--) {
            Block g = w.getBlockAt(bx, y, bz);
            Block feet = g.getRelative(BlockFace.UP);
            if (g.getType().isSolid() && feet.isPassable() && !feet.isLiquid() && feet.getRelative(BlockFace.UP).isPassable()) {
                double h = g.getBoundingBox().getMaxY();
                return h > y ? h : y + 1;
            }
        }
        return Double.NaN;
    }

    private static boolean loaded(Location l) {
        return l.getWorld().isChunkLoaded(l.getBlockX() >> 4, l.getBlockZ() >> 4);
    }

    private static double sq(double v) {
        return v * v;
    }
}
