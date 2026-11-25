package fr.orbit.mmo.quest;

import fr.orbit.mmo.OrbitMMO;
import fr.orbit.mmo.hud.QuestPanel;
import fr.orbit.mmo.item.Items;
import fr.orbit.mmo.mob.MobKilledEvent;
import fr.orbit.mmo.mob.MobState;
import fr.orbit.mmo.player.PlayerData;
import fr.orbit.mmo.quest.QuestDefs.Choice;
import fr.orbit.mmo.quest.QuestDefs.Clue;
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
    /** Épreuves en direct des types du 23/11 (escorte, défense, survie…) et objets de quête. */
    private final QuestTrials trials;
    private final QuestTracker tracker = new QuestTracker();
    /** Traînée de guidage vers l'objectif de la quête suivie. */
    private final QuestGuide trail = new QuestGuide(this);
    /** Essais de la traînée par l'administration : joueur → lieu visé (en mémoire seulement). */
    private final Map<UUID, Location> trailTest = new HashMap<>();
    /** Monstres vivants de chaque zone d'apparition. */
    private final Map<String, List<MobState>> spawned = new HashMap<>();
    private final Map<String, Long> idleCooldown = new HashMap<>();
    private int ticks;

    public QuestService(OrbitMMO plugin) {
        this.plugin = plugin;
        this.registry = new QuestRegistry(plugin);
        this.points = new QuestPoints(plugin);
        this.store = new QuestStore(plugin);
        this.dialogue = new Dialogue(plugin, registry, this::lang);
        this.npcs = new QuestNpcs(plugin, registry, points);
        this.trials = new QuestTrials(plugin, this, npcs);
    }

    public void enable() {
        registry.load();
        points.load();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getPluginManager().registerEvents(trials, plugin);
        for (World w : Bukkit.getWorlds()) {
            npcs.purge(w.getEntities());
        }
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 10L);
        plugin.getServer().getScheduler().runTaskTimer(plugin, trail::tick, 40L, 4L);
        plugin.getServer().getScheduler().runTaskTimer(plugin, trials::tick, 30L, QuestTrials.PERIOD);
        plugin.getServer().getScheduler().runTaskTimer(plugin, store::saveDirty, 1200L, 1200L);
    }

    public void shutdown() {
        dialogue.cancelAll();
        trials.shutdown();
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
            boolean skinned = plugin.pack().isLoaded(p.getUniqueId());
            tracker.show(p, trackerLines(p, pr, skinned), skinned, ui(p, "Quêtes", "Quests"));
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
        if (ticks % 4 == 0) {
            trials.checkItems(p, pr);
        }
        for (Map.Entry<String, Active> e : new ArrayList<>(pr.active.entrySet())) {
            QuestDef q = registry.quest(e.getKey());
            StepDef s = step(q, e.getValue());
            if (s == null) {
                continue;
            }
            if (e.getValue().deadline > 0 && System.currentTimeMillis() > e.getValue().deadline) {
                timeUp(p, q, e.getValue(), s);
                return;
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
            if (s != null && s.type() == StepType.ENQUETE) {
                // Indices pas encore examinés : une lueur sur chacun.
                for (Clue c : s.x().clues()) {
                    Location at = e.getValue().marks.contains(c.id()) ? null : points.location(c.point());
                    if (at != null && at.getWorld().equals(p.getWorld()) && at.distanceSquared(p.getLocation()) <= 48 * 48) {
                        Location fx = at.clone().add(0, 1, 0);
                        p.spawnParticle(Particle.END_ROD, fx, 3, 0.2, 0.4, 0.2, 0.01);
                        p.spawnParticle(Particle.DUST, fx, 4, 0.3, 0.4, 0.3, 0, new Particle.DustOptions(Fx.color("#9FD8FF"), 1.1f));
                    }
                }
                continue;
            }
            if (s == null || s.point() == null) {
                continue;
            }
            boolean area = switch (s.type()) {
                case ATTEINDRE, DEGUISEMENT, INFILTRER, TRANSPORT -> true;
                default -> false;
            };
            if (!area && s.type() != StepType.INTERAGIR && s.type() != StepType.ANCRAGE) {
                continue;
            }
            Location at = points.location(s.point());
            if (at == null || !at.getWorld().equals(p.getWorld()) || at.distanceSquared(p.getLocation()) > 64 * 64) {
                continue;
            }
            Location c = at.clone().add(0, 1, 0);
            if (area) {
                p.spawnParticle(Particle.END_ROD, c, 4, s.radius() * 0.3, 0.8, s.radius() * 0.3, 0.01);
            } else {
                p.spawnParticle(Particle.END_ROD, c, 6, 0.25, 0.6, 0.25, 0.02);
                p.spawnParticle(Particle.DUST, c, 6, 0.3, 0.5, 0.3, 0,
                        new Particle.DustOptions(Fx.color(s.type() == StepType.ANCRAGE ? "#FFD66E" : "#FFF3B0"), 1.2f));
            }
        }
    }

    private void markers(Player p, PlayerData d, QuestProgress pr) {
        // « ? » : vert si lui parler termine la quête (dernière étape), sinon doré (principale) ou bleu (annexe).
        Map<String, QuestNpcs.Mark> talk = new java.util.HashMap<>();
        for (Map.Entry<String, Active> e : pr.active.entrySet()) {
            QuestDef q = registry.quest(e.getKey());
            StepDef s = step(q, e.getValue());
            if (s == null || s.npc() == null) {
                continue;
            }
            boolean waiting = switch (s.type()) {
                case PARLER, CHOIX, LIVRER -> true;
                case ENQUETE -> cluesFound(s, e.getValue()) >= s.x().clues().size();
                default -> false;
            };
            if (waiting) {
                QuestNpcs.Mark m = e.getValue().step >= q.steps().size() - 1 ? QuestNpcs.Mark.TURN_IN
                        : registry.isSide(q.id()) ? QuestNpcs.Mark.TALK_SIDE : QuestNpcs.Mark.TALK_MAIN;
                talk.merge(s.npc(), m, (a, b) -> a.ordinal() >= b.ordinal() ? a : b);
            }
        }
        for (String id : npcs.liveIds()) {
            QuestNpcs.Mark m = talk.get(id);
            npcs.mark(p, id, m != null ? m : offerMark(d, pr, id));
        }
    }

    /** « ! » au-dessus d'un donneur : doré s'il a une quête principale à proposer, bleu s'il n'a que des annexes. */
    private QuestNpcs.Mark offerMark(PlayerData d, QuestProgress pr, String npcId) {
        QuestNpcs.Mark best = QuestNpcs.Mark.NONE;
        for (QuestDef q : registry.quests()) {
            if (!q.auto() && npcId.equals(q.giver()) && available(d, pr, q) && ready(q)) {
                if (!registry.isSide(q.id())) {
                    return QuestNpcs.Mark.OFFER_MAIN;
                }
                best = QuestNpcs.Mark.OFFER_SIDE;
            }
        }
        return best;
    }

    /**
     * Un PNJ de quête sur la mini-carte : position et couleur de son marqueur pour ce joueur
     * (« principale », « annexe », « finie » ou « aucun »).
     */
    public record MapNpc(Location at, String kind) {
    }

    /** Les PNJ de quête posés dans le monde du joueur, avec la couleur du marqueur qu'il voit au-dessus d'eux. */
    public java.util.List<MapNpc> mapNpcs(Player p) {
        java.util.List<MapNpc> out = new java.util.ArrayList<>();
        for (String id : npcs.liveIds()) {
            Location at = npcs.liveLocation(id);
            if (at == null || at.getWorld() != p.getWorld()) {
                continue;
            }
            String kind = switch (npcs.shown(p.getUniqueId(), id)) {
                case OFFER_MAIN, TALK_MAIN -> "principale";
                case OFFER_SIDE, TALK_SIDE -> "annexe";
                case TURN_IN -> "finie";
                case NONE -> "aucun";
            };
            out.add(new MapNpc(at, kind));
        }
        return out;
    }

    // ------------------------------------------------------------------ Règles

    static StepDef step(QuestDef q, Active a) {
        if (q == null || a == null || a.step < 0 || a.step >= q.steps().size()) {
            return null;
        }
        return q.steps().get(a.step);
    }

    /** La quête peut être commencée par ce personnage (hors question des points posés). */
    public boolean available(PlayerData d, QuestProgress pr, QuestDef q) {
        if (pr.active.containsKey(q.id())) {
            return false;
        }
        if (pr.done.contains(q.id())) {
            // Contrat répétable : de nouveau proposé une fois son délai passé.
            Long at = pr.doneAt.get(q.id());
            if (q.repeatHours() <= 0 || (at != null && System.currentTimeMillis() < at + (long) (q.repeatHours() * 3_600_000L))) {
                return false;
            }
        }
        if (q.faction() != null && q.faction() != d.faction) {
            return false;
        }
        if (q.admin()) {
            Player p = Bukkit.getPlayer(d.id);
            if (p == null || !p.hasPermission("orbitmmo.admin")) {
                return false;
            }
        }
        if (d.level < q.level()) {
            return false;
        }
        for (String r : q.requires()) {
            if (r.startsWith("choix:") || r.startsWith("!choix:")) {
                if (!conditionMet(pr, r)) {
                    return false;
                }
            } else if (!pr.done.contains(r)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Condition sur une réponse de dialogue à choix : « choix=réponse », « !choix=réponse » (toute autre réponse, ou
     * pas encore répondu), avec ou sans « choix: » devant. Vide = toujours vraie.
     */
    static boolean conditionMet(QuestProgress pr, String cond) {
        if (cond == null || cond.isBlank()) {
            return true;
        }
        String c = cond.trim();
        boolean neg = c.startsWith("!");
        if (neg) {
            c = c.substring(1).trim();
        }
        if (c.startsWith("choix:")) {
            c = c.substring(6).trim();
        }
        int i = c.indexOf('=');
        if (i < 0) {
            // « choix:pacte » seul : il suffit d'avoir répondu.
            return neg != pr.choices.containsKey(c);
        }
        boolean eq = c.substring(i + 1).trim().equals(pr.choices.get(c.substring(0, i).trim()));
        return neg != eq;
    }

    /** Indices de l'enquête déjà examinés (seuls comptent ceux de l'étape). */
    static int cluesFound(StepDef s, Active a) {
        int n = 0;
        for (Clue c : s.x().clues()) {
            if (a.marks.contains(c.id())) {
                n++;
            }
        }
        return n;
    }

    /**
     * Entrée dans l'étape a.step : saute les étapes dont la condition « si » n'est pas remplie, remet à zéro compteur,
     * indices et chrono, remet les objets (livraison, tenue). Faux si la quête est au bout.
     */
    private boolean enter(Player p, QuestDef q, Active a) {
        QuestProgress pr = store.get(p.getUniqueId());
        while (a.step < q.steps().size() && !conditionMet(pr, q.steps().get(a.step).condition())) {
            a.step++;
        }
        a.count = 0;
        a.marks.clear();
        a.deadline = 0;
        pr.dirty = true;
        trials.clear(p);
        if (a.step >= q.steps().size()) {
            return false;
        }
        trials.enter(p, q, q.steps().get(a.step), a.step);
        return true;
    }

    /** Contre la montre perdu : texte d'échec, puis reprise de l'étape (ou de l'étape « reprise »). */
    private void timeUp(Player p, QuestDef q, Active a, StepDef s) {
        QuestProgress pr = store.get(p.getUniqueId());
        a.deadline = 0;
        if (s.retry() > 0) {
            a.step = Math.min(q.steps().size() - 1, s.retry() - 1);
        }
        enter(p, q, a);
        pr.lastGcdSeen = 0;
        Text.title(p, "<#FF5C5C><b>" + ui(p, "Temps écoulé", "Time's up") + "</b>",
                "<#A9B1BC>" + ui(p, "On recommence.", "Try again."), 5, 50, 15);
        p.playSound(p.getLocation(), Sound.BLOCK_BELL_USE, 0.8f, 0.6f);
        dialogue.play(p, s.fail(), () -> announce(p, q));
    }

    /** Le dialogue du joueur est en cours (les épreuves attendent qu'il finisse). */
    boolean talking(Player p) {
        return dialogue.busy(p);
    }

    /**
     * Départ de la quête d'introduction qui attend ce personnage : elle démarrera seule et l'y téléportera.
     * Null s'il n'y en a pas (pas de quête automatique pour lui, points pas posés, ou quête sans lieu de départ).
     */
    public Location introStart(Player p, PlayerData d) {
        QuestProgress pr = store.get(p.getUniqueId());
        for (QuestDef q : registry.quests()) {
            if (q.auto() && available(d, pr, q) && ready(q)) {
                return q.startPoint() == null ? null : points.location(q.startPoint());
            }
        }
        return null;
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
        if (q.repeatHours() <= 0) {
            // Une répétable reste « terminée » pendant qu'on la refait : les quêtes qui la demandent restent ouvertes.
            pr.done.remove(q.id());
        }
        Active first = new Active(0, 0);
        pr.active.put(q.id(), first);
        // La dernière quête prise est celle que la traînée de guidage suit (le journal permet d'en changer).
        pr.tracked = q.id();
        pr.dirty = true;
        pr.lastGcdSeen = 0;
        boolean any = enter(p, q, first);
        Location dep = points.location(q.startPoint());
        if (dep != null) {
            p.teleport(dep);
        }
        Text.title(p, "<#FFD66E><b>" + Text.esc(tr(p, q.name())) + "</b>", "<#A9B1BC>" + ui(p, "Nouvelle quête", "New quest"), 10, 60, 20);
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.4f);
        dialogue.play(p, q.intro(), () -> {
            if (any) {
                announce(p, q);
            } else {
                finish(p, q);
            }
        });
    }

    private void announce(Player p, QuestDef q) {
        QuestProgress pr = store.get(p.getUniqueId());
        Active a = pr.active.get(q.id());
        StepDef s = step(q, a);
        if (s == null) {
            return;
        }
        Text.msg(p, "<#FFD66E>" + ui(p, "Objectif", "Objective") + "</#FFD66E> <#5A6270>·</#5A6270> <white>"
                + Text.esc(objective(p, s, a)));
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6f, 1.6f);
        // Contre la montre : le compte à rebours part quand l'objectif est annoncé (après les dialogues).
        if (s.timer() > 0 && a.deadline == 0) {
            a.deadline = System.currentTimeMillis() + s.timer() * 1000L;
            pr.dirty = true;
            Text.msg(p, "<#FF8A5C>⏱ " + ui(p, "Vous avez ", "You have ") + QuestTrials.clock(s.timer()) + ui(p, " !", "!"));
        }
    }

    /** L'étape en cours est réussie : texte d'après, puis étape suivante ou fin de la quête. */
    void completeStep(Player p, QuestDef q) {
        QuestProgress pr = store.get(p.getUniqueId());
        Active a = pr.active.get(q.id());
        StepDef s = step(q, a);
        if (s == null) {
            return;
        }
        // On passe tout de suite à l'étape suivante (un objectif ne peut pas être validé deux fois),
        // le texte « après » est joué ensuite.
        a.step++;
        boolean more = enter(p, q, a);
        pr.lastGcdSeen = 0;
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
        if (!more) {
            dialogue.play(p, s.after(), () -> finish(p, q));
        } else {
            plugin.hud().flash(p, "<#5EE89A>✔ " + Text.esc(tr(p, s.text())), 50);
            dialogue.play(p, s.after(), () -> announce(p, q));
        }
    }

    private void finish(Player p, QuestDef q) {
        QuestProgress pr = store.get(p.getUniqueId());
        if (pr.active.remove(q.id()) == null) {
            return;
        }
        pr.done.add(q.id());
        if (q.repeatHours() > 0) {
            pr.doneAt.put(q.id(), System.currentTimeMillis());
        }
        pr.dirty = true;
        store.save(pr);
        trials.removeQuestItems(p, q.id());
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
                meta.displayName(Text.item("<#FFE9A8>" + Text.esc(tr(p, it.name()))));
                if (!it.lore().isEmpty()) {
                    List<String> lore = new ArrayList<>();
                    for (String l : it.lore()) {
                        lore.add("<#A9B1BC><i>" + Text.esc(tr(p, l)));
                    }
                    meta.lore(Text.lore(lore));
                }
                stack.setItemMeta(meta);
            }
            Items.give(p, stack);
            sb.append("<#D7DCE2>+").append(it.amount()).append(' ').append(Text.esc(it.name() == null ? it.material().name() : tr(p, it.name()))).append("</#D7DCE2>  ");
        }
        String doneLabel = ui(p, "Quête terminée", "Quest complete");
        Text.title(p, "<gradient:#FFD66E:#FFF3B0><b>" + doneLabel + "</b></gradient>", "<white>" + Text.esc(tr(p, q.name())), 10, 60, 20);
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f);
        Text.msg(p, "<#FFD66E>✦ " + doneLabel + ui(p, " :", ":") + "</#FFD66E> <white>" + Text.esc(tr(p, q.name()))
                + (sb.isEmpty() ? "" : "  " + sb.toString().trim()));
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
        Active a = new Active(Math.max(0, Math.min(q.steps().size() - 1, step)), 0);
        pr.active.put(q.id(), a);
        pr.dirty = true;
        pr.lastGcdSeen = 0;
        if (enter(p, q, a)) {
            announce(p, q);
        } else {
            finish(p, q);
        }
    }

    public void reset(Player p, QuestDef q) {
        QuestProgress pr = store.get(p.getUniqueId());
        dialogue.cancel(p.getUniqueId());
        trials.clear(p);
        trials.removeQuestItems(p, q == null ? null : q.id());
        if (q == null) {
            pr.clear();
        } else {
            pr.active.remove(q.id());
            pr.done.remove(q.id());
            pr.doneAt.remove(q.id());
            pr.dirty = true;
        }
        store.save(pr);
    }

    /**
     * Nouveau personnage (réinitialisation) : retour à zéro de l'histoire, quel que soit le camp choisi ensuite.
     * Quêtes en cours et terminées, Éclat d'ancrage, quête suivie : tout est effacé.
     * Seuls les réglages du joueur (langue, traînée allumée ou non) sont gardés.
     */
    public void wipe(Player p) {
        UUID id = p.getUniqueId();
        dialogue.cancel(id);
        trailTest.remove(id);
        tracker.hide(p);
        trials.clear(p);
        trials.removeQuestItems(p, null);
        QuestProgress pr = store.get(id);
        pr.clear();
        pr.faction = null;
        pr.lastGcdSeen = 0;
        store.save(pr);
    }

    public void skipDialogue(Player p) {
        dialogue.skip(p);
    }

    // ------------------------------------------------------------------ Textes

    /** « Tuer des Rampants (3/8) ». */
    String objective(Player p, StepDef s, Active a) {
        String text = tr(p, s.text());
        if (s.counted()) {
            return text + " (" + Math.min(a == null ? 0 : a.count, s.count()) + "/" + s.count() + ")";
        }
        if (s.type() == StepType.ENQUETE && !s.x().clues().isEmpty()) {
            return text + " (" + (a == null ? 0 : cluesFound(s, a)) + "/" + s.x().clues().size() + ")";
        }
        if (s.type() == StepType.PISTER && s.x().path().size() > 1) {
            return text + " (" + Math.min(a == null ? 0 : a.count, s.x().path().size()) + "/" + s.x().path().size() + ")";
        }
        return text;
    }

    /** Lieu à indiquer pour l'étape de ce joueur : suit ce qui bouge (escorté, cargaison tombée, prochain indice). */
    Location target(Player p, String questId, StepDef s, Active a) {
        Location live = trials.target(p, questId, s, a);
        if (live != null) {
            return live;
        }
        if (s.type() == StepType.ENQUETE && a != null) {
            Location best = null;
            for (Clue c : s.x().clues()) {
                Location at = a.marks.contains(c.id()) ? null : points.location(c.point());
                if (at != null && (best == null || (at.getWorld().equals(p.getWorld())
                        && (!best.getWorld().equals(p.getWorld()) || at.distanceSquared(p.getLocation()) < best.distanceSquared(p.getLocation()))))) {
                    best = at;
                }
            }
            if (best != null || s.npc() == null) {
                return best;
            }
            return npcs.location(s.npc());
        }
        return target(s);
    }

    /** Lieu à indiquer pour l'étape (PNJ, point, zone d'apparition). */
    Location target(StepDef s) {
        if (s.type() == StepType.PARLER || s.type() == StepType.CHOIX || s.type() == StepType.LIVRER) {
            return npcs.location(s.npc());
        }
        if (s.type() == StepType.SURVIE || s.type() == StepType.DEFENSE) {
            return points.location(s.point());
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

    // ------------------------------------------------------------------ Langue

    /** Langue du joueur : son choix, sinon celle de son jeu (français, ou anglais pour toutes les autres). */
    public String lang(Player p) {
        QuestProgress pr = store.get(p.getUniqueId());
        if (pr.lang != null) {
            return pr.lang;
        }
        return "fr".equalsIgnoreCase(p.locale().getLanguage()) ? "fr" : "en";
    }

    /** Choix du joueur : « fr », « en », ou null pour suivre la langue de son jeu. */
    public void setLang(Player p, String lang) {
        QuestProgress pr = store.get(p.getUniqueId());
        pr.lang = "fr".equals(lang) || "en".equals(lang) ? lang : null;
        pr.dirty = true;
    }

    /** Bascule français ↔ anglais (boutons des menus) et le dit au joueur dans sa nouvelle langue. */
    public void cycleLang(Player p) {
        setLang(p, "en".equals(lang(p)) ? "fr" : "en");
        langNotice(p);
    }

    public void langNotice(Player p) {
        boolean chosen = langChosen(p);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6f, 1.6f);
        Text.msg(p, "en".equals(lang(p))
                ? "<#7FB2FF>Language:</#7FB2FF> <white>English</white> <#8C96A3>— dialogues and quests are now in English"
                + (chosen ? "." : " (your game language).") + " <white>/langue fr</white> to switch back."
                : "<#7FB2FF>Langue :</#7FB2FF> <white>français</white> <#8C96A3>— dialogues et quêtes en français"
                + (chosen ? "." : " (la langue de votre jeu).") + " <white>/langue en</white> pour l'anglais.");
    }

    /** Bouton « Langue / Language » pour le menu principal. */
    public org.bukkit.inventory.ItemStack langIcon(Player p) {
        return QuestJournal.langIcon(this, p);
    }

    /** Le joueur a-t-il choisi lui-même sa langue ? (Sinon elle suit celle de son jeu.) */
    public boolean langChosen(Player p) {
        return store.get(p.getUniqueId()).lang != null;
    }

    /** Texte d'un fichier de quêtes (nom, objectif, réplique) dans la langue du joueur. */
    public String tr(Player p, String fr) {
        return registry.tr(fr, lang(p));
    }

    /** Libellé fixe du déroulé des quêtes, dans la langue du joueur. */
    String ui(Player p, String fr, String en) {
        return "en".equals(lang(p)) ? en : fr;
    }

    // ------------------------------------------------------------------ Quête suivie et traînée de guidage

    /** Ouvre le journal de quêtes (bouton « Quêtes » du menu principal). */
    public void openJournal(Player p) {
        QuestJournal.open(this, p);
    }

    /** La traînée de guidage est-elle allumée pour ce joueur ? Son choix, sinon le réglage du serveur. */
    boolean trailOn(QuestProgress pr) {
        return pr.trail != null ? pr.trail : plugin.getConfig().getBoolean("quetes.trainee-par-defaut", true);
    }

    /** Quête suivie : celle que le joueur a choisie si elle est toujours en cours, sinon la première en cours. */
    String trackedId(QuestProgress pr) {
        if (pr.tracked != null && step(registry.quest(pr.tracked), pr.active.get(pr.tracked)) != null) {
            return pr.tracked;
        }
        for (Map.Entry<String, Active> e : pr.active.entrySet()) {
            if (step(registry.quest(e.getKey()), e.getValue()) != null) {
                return e.getKey();
            }
        }
        return null;
    }

    /**
     * Lieu marqué sur la carte du joueur (hud/MiniMap) : objectif de la quête suivie, sinon le personnage le plus proche
     * qui propose une quête. Même règle que la traînée, même quand la traînée est éteinte. Null : rien à marquer.
     */
    public Location mapTarget(Player p) {
        PlayerData d = plugin.players().loaded(p.getUniqueId());
        if (d == null || d.buildMode || !plugin.progression().valid(d)) {
            return null;
        }
        QuestProgress pr = store.get(p.getUniqueId());
        String id = trackedId(pr);
        if (id == null) {
            NpcDef giver = nextGiver(p, d, pr);
            return giver == null ? null : npcs.location(giver.id());
        }
        StepDef s = step(registry.quest(id), pr.active.get(id));
        return s == null ? null : target(p, id, s, pr.active.get(id));
    }

    /** Lieu vers lequel mène la traînée du joueur ; null s'il n'y a rien à montrer. */
    Location trailTarget(Player p) {
        Location test = trailTest.get(p.getUniqueId());
        if (test != null) {
            return test;
        }
        PlayerData d = plugin.players().loaded(p.getUniqueId());
        if (d == null || d.buildMode || !plugin.progression().valid(d)) {
            return null;
        }
        QuestProgress pr = store.get(p.getUniqueId());
        if (!trailOn(pr)) {
            return null;
        }
        String id = trackedId(pr);
        if (id == null) {
            // Aucune quête en cours : la traînée mène au personnage qui propose la prochaine.
            NpcDef giver = nextGiver(p, d, pr);
            return giver == null ? null : npcs.location(giver.id());
        }
        StepDef s = step(registry.quest(id), pr.active.get(id));
        return s == null ? null : target(p, id, s, pr.active.get(id));
    }

    /** Personnage le plus proche qui propose une quête à ce joueur (même monde) ; null s'il n'y en a pas. */
    private NpcDef nextGiver(Player p, PlayerData d, QuestProgress pr) {
        NpcDef best = null;
        double bestDist = Double.MAX_VALUE;
        for (NpcDef n : registry.npcs()) {
            if (offerOf(d, pr, n.id()) == null) {
                continue;
            }
            Location at = npcs.location(n.id());
            if (at == null || !at.getWorld().equals(p.getWorld())) {
                continue;
            }
            double dist = at.distanceSquared(p.getLocation());
            if (dist < bestDist) {
                bestDist = dist;
                best = n;
            }
        }
        return best;
    }

    /** Existe-t-il au moins une quête pour le camp de ce joueur ? (Sinon le journal le dit.) */
    boolean anyQuestFor(Player p) {
        PlayerData d = plugin.players().loaded(p.getUniqueId());
        for (QuestDef q : registry.quests()) {
            if (q.faction() == null || d == null || q.faction() == d.faction) {
                return true;
            }
        }
        return false;
    }

    /** Essai (administration) : la traînée du joueur mène à ce lieu, quêtes ou pas. Null = fin de l'essai. */
    public void trailTest(Player p, Location to) {
        if (to == null) {
            trailTest.remove(p.getUniqueId());
        } else {
            trailTest.put(p.getUniqueId(), to);
        }
    }

    /** Diagnostic (administration) : ce que la traînée de ce joueur est en train de faire, et pourquoi. */
    public String trailDiag(Player p) {
        QuestProgress pr = store.get(p.getUniqueId());
        PlayerData d = plugin.players().loaded(p.getUniqueId());
        StringBuilder sb = new StringBuilder("<white>" + Text.esc(p.getName()) + "</white> <#8C96A3>· ");
        sb.append(d == null || !plugin.progression().valid(d) ? "pas de personnage" : "camp " + d.faction.label);
        sb.append(" · traînée ").append(trailOn(pr) ? "allumée" : "éteinte");
        if (d != null && d.buildMode) {
            sb.append(" · mode construction (pas de traînée)");
        }
        sb.append(" · ").append(pr.active.size()).append(" quête(s) en cours");
        String id = trackedId(pr);
        sb.append(id == null ? "" : " · suivie : " + id);
        if (trailTest.containsKey(p.getUniqueId())) {
            sb.append(" · ESSAI en cours");
        }
        Location t = trailTarget(p);
        if (t == null) {
            sb.append(" · <#FF8A7A>aucun objectif à montrer</#FF8A7A>");
        } else if (!t.getWorld().equals(p.getWorld())) {
            sb.append(" · <#FF8A7A>objectif dans un autre monde (").append(Text.esc(t.getWorld().getName())).append(")</#FF8A7A>");
        } else {
            sb.append(" · objectif ").append(t.getBlockX()).append(' ').append(t.getBlockY()).append(' ').append(t.getBlockZ())
                    .append(" à ").append(Math.round(t.distance(p.getLocation()))).append(" m");
            int n = trail.pathSize(p.getUniqueId());
            sb.append(n < 0 ? " · <#FF8A7A>pas de traînée (arrivé ?)</#FF8A7A>" : n < 2 ? " · <#FFD66E>ligne droite (pas de chemin trouvé)</#FFD66E>"
                    : " · <#5EE89A>chemin de " + n + " cases</#5EE89A>");
        }
        return sb.toString();
    }

    /** Le joueur choisit la quête que la traînée suit. */
    public void track(Player p, QuestDef q) {
        QuestProgress pr = store.get(p.getUniqueId());
        if (q == null || !pr.active.containsKey(q.id())) {
            Text.msg(p, "<#FF5C5C>Cette quête n'est pas en cours.");
            return;
        }
        pr.tracked = q.id();
        pr.dirty = true;
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6f, 1.8f);
        Text.msg(p, "<#FFD66E>Quête suivie</#FFD66E> <#5A6270>·</#5A6270> <white>" + Text.esc(tr(p, q.name())) + "</white>"
                + (trailOn(pr) ? " <#8C96A3>— la traînée dorée au sol mène à l'objectif."
                : " <#8C96A3>— traînée éteinte : <white>/quetes trainee</white> pour l'allumer."));
    }

    /** Allume ou éteint la traînée de guidage du joueur. Renvoie son nouvel état. */
    public boolean toggleTrail(Player p) {
        QuestProgress pr = store.get(p.getUniqueId());
        boolean on = !trailOn(pr);
        pr.trail = on;
        pr.dirty = true;
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6f, on ? 1.8f : 1.0f);
        Text.msg(p, on ? "<#5EE89A>Traînée de guidage allumée</#5EE89A> <#8C96A3>— suivez les particules dorées au sol."
                : "<#8C96A3>Traînée de guidage éteinte. <white>/quetes trainee</white> pour la rallumer.");
        return on;
    }

    private List<QuestPanel.Row> trackerLines(Player p, QuestProgress pr, boolean skinned) {
        List<QuestPanel.Row> lines = new ArrayList<>();
        int shown = 0;
        // La quête suivie passe en tête du suivi à l'écran.
        String followed = trackedId(pr);
        boolean mark = followed != null && pr.active.size() > 1 && trailOn(pr);
        List<Map.Entry<String, Active>> order = new ArrayList<>(pr.active.entrySet());
        order.sort(java.util.Comparator.comparing(e -> !e.getKey().equals(followed)));
        for (Map.Entry<String, Active> e : order) {
            QuestDef q = registry.quest(e.getKey());
            StepDef s = step(q, e.getValue());
            if (s == null) {
                continue;
            }
            if (shown == 2) {
                lines.add(QuestPanel.Row.of(QuestPanel.Kind.MORE, "+" + (pr.active.size() - 2) + ui(p, " autre(s) · /quetes", " more · /quetes")));
                break;
            }
            if (!lines.isEmpty()) {
                lines.add(QuestPanel.Row.of(QuestPanel.Kind.SEPARATOR, ""));
            }
            boolean main = skinned ? shown == 0 : mark && e.getKey().equals(followed);
            lines.add(QuestPanel.Row.of(main ? QuestPanel.Kind.QUEST_MAIN : QuestPanel.Kind.QUEST, tr(p, q.name())));
            addObjective(lines, objective(p, s, e.getValue()), skinned);
            trials.status(p, e.getKey(), s, e.getValue(), lines);
            Location at = target(p, e.getKey(), s, e.getValue());
            if (at != null) {
                if (at.getWorld().equals(p.getWorld())) {
                    lines.add(distanceRow(p, at));
                } else {
                    lines.add(QuestPanel.Row.of(QuestPanel.Kind.ELSEWHERE, ui(p, "ailleurs (", "elsewhere (") + at.getWorld().getName() + ")"));
                }
            }
            shown++;
        }
        if (lines.isEmpty()) {
            // Aucune quête en cours : on indique le personnage qui propose la prochaine.
            PlayerData d = plugin.players().loaded(p.getUniqueId());
            NpcDef giver = d == null ? null : nextGiver(p, d, pr);
            Location at = giver == null ? null : npcs.location(giver.id());
            if (at != null) {
                lines.add(QuestPanel.Row.of(QuestPanel.Kind.QUEST_MAIN, ui(p, "Nouvelle quête", "New quest")));
                addObjective(lines, ui(p, "Parler à ", "Talk to ") + tr(p, giver.name()), skinned);
                lines.add(at.getWorld().equals(p.getWorld()) ? distanceRow(p, at)
                        : QuestPanel.Row.of(QuestPanel.Kind.ELSEWHERE, ui(p, "ailleurs", "elsewhere")));
            }
        }
        return lines;
    }

    /** Objectif : coupé sur deux lignes dans le cadre du pack, une seule ligne sinon. */
    private static void addObjective(List<QuestPanel.Row> lines, String text, boolean skinned) {
        if (!skinned) {
            lines.add(QuestPanel.Row.of(QuestPanel.Kind.OBJECTIVE, text));
            return;
        }
        List<String> parts = QuestPanel.wrapObjective(text);
        for (int i = 0; i < parts.size(); i++) {
            lines.add(QuestPanel.Row.of(i == 0 ? QuestPanel.Kind.OBJECTIVE : QuestPanel.Kind.OBJECTIVE_MORE, parts.get(i)));
        }
    }

    private QuestPanel.Row distanceRow(Player p, Location at) {
        double dist = at.distance(p.getLocation());
        if (dist < 4) {
            return QuestPanel.Row.of(QuestPanel.Kind.HERE, ui(p, "ici", "here"));
        }
        String txt = dist >= 1000 ? String.format(java.util.Locale.FRANCE, "%.1f km", dist / 1000.0) : Math.round(dist) + " m";
        return new QuestPanel.Row(QuestPanel.Kind.DISTANCE, txt, arrow(p, at));
    }

    /** Direction de la cible relative au regard du joueur : 0 = devant, puis par huitièmes de tour dans le sens horaire. */
    private static int arrow(Player p, Location to) {
        double dx = to.getX() - p.getLocation().getX();
        double dz = to.getZ() - p.getLocation().getZ();
        double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
        double rel = targetYaw - p.getLocation().getYaw();
        rel = ((rel % 360) + 360) % 360;
        return (int) Math.round(rel / 45.0) % 8;
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
        trail.quit(id);
        trials.quit(id);
        trailTest.remove(id);
        npcs.quit(id);
        store.unload(id);
    }

    /** Clic gauche pendant un dialogue : fin de l'écriture, puis réplique suivante. */
    @EventHandler
    public void onSwing(org.bukkit.event.player.PlayerAnimationEvent e) {
        if (e.getAnimationType() == org.bukkit.event.player.PlayerAnimationType.ARM_SWING && dialogue.busy(e.getPlayer())) {
            dialogue.skip(e.getPlayer());
        }
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
            if (s != null && npcId.equals(s.npc()) && talkStep(p, q, s, en.getValue())) {
                return;
            }
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

    /** Parler au PNJ d'une étape CHOIX, LIVRER ou ENQUETE. Vrai si le clic est traité. */
    private boolean talkStep(Player p, QuestDef q, StepDef s, Active a) {
        int stepAt = a.step;
        Runnable done = () -> {
            Active now = store.get(p.getUniqueId()).active.get(q.id());
            if (now != null && now.step == stepAt) {
                completeStep(p, q);
            }
        };
        switch (s.type()) {
            case CHOIX -> {
                dialogue.play(p, s.dialogue(), () -> ask(p, q, stepAt));
                return true;
            }
            case LIVRER -> {
                if (!trials.hasQuestItem(p, q.id(), stepAt)) {
                    trials.giveItem(p, q, s, stepAt, true);
                    return true;
                }
                dialogue.play(p, s.dialogue(), () -> {
                    Active now = store.get(p.getUniqueId()).active.get(q.id());
                    if (now != null && now.step == stepAt && trials.takeQuestItem(p, q.id(), stepAt)) {
                        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 0.9f);
                        completeStep(p, q);
                    }
                });
                return true;
            }
            case ENQUETE -> {
                int found = cluesFound(s, a);
                int total = s.x().clues().size();
                if (found < total) {
                    Text.msg(p, "<#9FD8FF>" + ui(p, "Il vous manque des indices", "You are missing clues")
                            + " <white>(" + found + "/" + total + ")</white><#9FD8FF>.");
                    return true;
                }
                dialogue.play(p, s.dialogue(), done);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /** Dialogue à choix : les réponses, cliquables dans le chat. */
    private void ask(Player p, QuestDef q, int stepAt) {
        QuestProgress pr = store.get(p.getUniqueId());
        Active a = pr.active.get(q.id());
        StepDef s = step(q, a);
        if (s == null || a.step != stepAt || s.type() != StepType.CHOIX || !p.isOnline()) {
            return;
        }
        ClickCallback.Options once = ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(10)).build();
        String question = s.x().question() == null ? ui(p, "Que répondez-vous ?", "What do you answer?") : tr(p, s.x().question());
        p.sendMessage(Text.mm(" "));
        Text.msg(p, "<#FFD66E>❖ " + Text.esc(question));
        int i = 1;
        for (Choice c : s.x().choices()) {
            p.sendMessage(Text.mm("  <#7FD4FF><b>[" + i + "]</b></#7FD4FF> <white>" + Text.esc(tr(p, c.text())))
                    .hoverEvent(HoverEvent.showText(Text.mm("<#D7DCE2>" + ui(p, "Répondre ceci", "Answer this"))))
                    .clickEvent(ClickEvent.callback(au -> choose(p, q, stepAt, c), once)));
            i++;
        }
        Text.title(p, "", "<#FFD66E>" + ui(p, "Cliquez une réponse dans le chat", "Click an answer in the chat"), 5, 50, 10);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.2f);
    }

    private void choose(Player p, QuestDef q, int stepAt, Choice c) {
        QuestProgress pr = store.get(p.getUniqueId());
        Active a = pr.active.get(q.id());
        StepDef s = step(q, a);
        if (!p.isOnline() || s == null || a.step != stepAt || s.type() != StepType.CHOIX || dialogue.busy(p)) {
            return;
        }
        pr.choices.put(s.x().choiceId(), c.id());
        pr.dirty = true;
        Text.msg(p, "<#7FD4FF>" + ui(p, "Vous", "You") + "</#7FD4FF> <#5A6270>»</#5A6270> <#D7DCE2>" + Text.esc(tr(p, c.text())));
        PlayerData d = plugin.players().get(p);
        if (c.xp() > 0) {
            plugin.progression().addXp(p, d, c.xp());
        }
        if (c.gold() > 0) {
            d.gold += c.gold();
            Text.msg(p, "<#FFD66E>+" + Text.num(c.gold()) + " or");
        }
        dialogue.play(p, c.reply(), () -> {
            Active now = store.get(p.getUniqueId()).active.get(q.id());
            if (now != null && now.step == stepAt) {
                completeStep(p, q);
            }
        });
    }

    /** Fenêtre d'offre de quête dans le HUD (créée au premier usage). */
    private QuestOffer offers;

    /** Fiche de quête dans le HUD (Maj = accepter) ; sans le pack, boutons « Accepter » / « Plus tard » dans le chat. */
    private void propose(Player p, QuestDef q) {
        if (offers == null) {
            offers = new QuestOffer(plugin, this);
        }
        Runnable acceptNow = () -> {
            PlayerData d = plugin.players().get(p);
            QuestProgress pr = store.get(p.getUniqueId());
            if (p.isOnline() && available(d, pr, q) && ready(q) && !dialogue.busy(p)) {
                start(p, q);
            }
        };
        if (offers.show(p, q, acceptNow)) {
            return;
        }
        ClickCallback.Options once = ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(10)).build();
        String title = Text.esc(tr(p, q.name()));
        Component accept = Text.mm("<#5EE89A><b>[ " + ui(p, "Accepter", "Accept") + " ]</b>")
                .hoverEvent(HoverEvent.showText(Text.mm("<#D7DCE2>" + ui(p, "Commencer « " + title + " »", "Start \"" + title + "\""))))
                .clickEvent(ClickEvent.callback(a -> {
                    PlayerData d = plugin.players().get(p);
                    QuestProgress pr = store.get(p.getUniqueId());
                    if (p.isOnline() && available(d, pr, q) && ready(q) && !dialogue.busy(p)) {
                        start(p, q);
                    }
                }, once));
        Component later = Text.mm("<#8C96A3>[ " + ui(p, "Plus tard", "Later") + " ]")
                .clickEvent(ClickEvent.callback(a -> Text.msg(p, "<#8C96A3>"
                        + ui(p, "Revenez quand vous serez prêt.", "Come back when you are ready.")), once));
        p.sendMessage(Text.mm("<#FFD66E>✦ " + ui(p, "Quête :", "Quest:") + "</#FFD66E> <white>" + title + "</white>  ")
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
            if (s != null && s.type() == StepType.ENQUETE && examine(p, e, pr, q, s, en.getValue())) {
                return;
            }
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

    /** Enquête : clic droit près d'un indice pas encore examiné. Vrai si un indice a été trouvé. */
    private boolean examine(Player p, PlayerInteractEvent e, QuestProgress pr, QuestDef q, StepDef s, Active a) {
        for (Clue c : s.x().clues()) {
            if (a.marks.contains(c.id())) {
                continue;
            }
            Location at = points.location(c.point());
            if (at == null || !near(p, at, c.radius() + 1.5)) {
                continue;
            }
            if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
                e.setCancelled(true);
            }
            a.marks.add(c.id());
            pr.dirty = true;
            Location fx = at.clone().add(0, 1, 0);
            p.spawnParticle(Particle.END_ROD, fx, 30, 0.3, 0.6, 0.3, 0.08);
            p.playSound(fx, Sound.ITEM_BOOK_PAGE_TURN, 1f, 1.1f);
            int found = cluesFound(s, a);
            int total = s.x().clues().size();
            plugin.hud().flash(p, "<#9FD8FF>" + ui(p, "Indice", "Clue") + " <white>" + found + "/" + total
                    + "</white> <#D7DCE2>" + Text.esc(tr(p, c.text())), 60);
            int stepAt = a.step;
            dialogue.play(p, c.after(), () -> {
                Active now = store.get(p.getUniqueId()).active.get(q.id());
                if (now == null || now.step != stepAt || cluesFound(s, now) < total) {
                    return;
                }
                if (s.npc() == null) {
                    completeStep(p, q);
                } else {
                    NpcDef n = registry.npc(s.npc());
                    Text.msg(p, "<#9FD8FF>" + ui(p, "Tous les indices sont réunis.", "All clues found.") + "</#9FD8FF> <white>"
                            + ui(p, "Allez voir ", "Go and see ") + Text.esc(n == null ? s.npc() : tr(p, n.name())) + ".");
                }
            });
            return true;
        }
        return false;
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
                    String what = s.type() == StepType.RAMASSER && s.item() != null ? "+1 " + tr(p, s.item()) : tr(p, s.text());
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
