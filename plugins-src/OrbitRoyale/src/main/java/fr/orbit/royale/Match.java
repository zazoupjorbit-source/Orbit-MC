package fr.orbit.royale;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.title.Title;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Une partie de Siège Orbital sur une arène : simulation côté serveur à 20 tours par seconde (unités, tours,
 * sorts, plasma), affichage par entités d'affichage (sprites du pack, ou objets du jeu de base sans le pack).
 */
final class Match {

    enum State { COUNTDOWN, PLAYING, OVERTIME, DONE }

    static final Color BLUE = Color.fromRGB(0x62CFF4), RED = Color.fromRGB(0xFF5A5A);
    static final String[] TEAM = {"#62CFF4", "#FF5A5A"};

    final Game game;
    final Arena arena;
    final World world;
    final Side[] sides;
    final List<Body> bodies = new ArrayList<>();
    private final List<Body> born = new ArrayList<>();
    private final List<Strike> strikes = new ArrayList<>();
    final List<Entity> deco = new ArrayList<>();
    State state = State.COUNTDOWN;
    int tick, countdown = 60, timeLeft, overtimeLeft;
    final boolean packMode;
    final boolean vsBot;

    /** Frappe orbitale en approche. */
    private record Strike(int side, Card card, double x, double z, int[] left) {
    }

    Match(Game game, Arena arena, Side a, Side b) {
        this.game = game;
        this.arena = arena;
        this.world = arena.world;
        this.sides = new Side[]{a, b};
        this.vsBot = !a.human() || !b.human();
        boolean pack = true;
        for (Side s : sides) {
            if (s.human() && !game.pack().isLoaded(s.player)) {
                pack = false;
            }
        }
        this.packMode = pack;
        this.timeLeft = game.plugin().getConfig().getInt("partie.duree", 180) * 20;
        this.overtimeLeft = game.plugin().getConfig().getInt("partie.prolongation", 60) * 20;
    }

    Side other(Side s) {
        return sides[1 - s.index];
    }

    Side sideOf(Player p) {
        for (Side s : sides) {
            if (s.human() && s.player.getUniqueId().equals(p.getUniqueId())) {
                return s;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ démarrage

    void start() {
        arena.loadChunks(game.plugin(), true);
        if (!arena.built()) {
            arena.build();
        } else {
            arena.buildTowers();
        }
        double start = game.plugin().getConfig().getDouble("partie.plasma-depart", 5);
        for (Side s : sides) {
            s.plasma = start;
            s.main = tower(s.index, true, 0);
            s.left = tower(s.index, false, -1);
            s.right = tower(s.index, false, 1);
            if (s.human()) {
                setupHuman(s);
            }
        }
        // bannière au-dessus de la faille
        deco.add(text(arena.at(0, 6.5, 0), Txt.TITLE + "\n<" + TEAM[0] + ">" + sides[0].name + "</" + TEAM[0] + "> <#9AA4B2>contre</#9AA4B2> <"
                + TEAM[1] + ">" + sides[1].name, 2.2f));
    }

    private Body tower(int side, boolean main, int lane) {
        Body t = new Body(side, main, main ? 0 : Arena.towerX(lane), main ? Arena.mainTowerZ(side) : Arena.sideTowerZ(side));
        t.bar = text(arena.at(t.x, main ? 7.6 : 6.4, t.z), "", main ? 1.6f : 1.3f);
        bodies.add(t);
        refreshBar(t);
        return t;
    }

    private void setupHuman(Side s) {
        Player p = s.player;
        s.saved = Saved.of(p);
        s.saved.write(Saved.file(game.plugin().getDataFolder(), p));
        if (p.isInsideVehicle()) {
            p.leaveVehicle();
        }
        p.closeInventory();
        p.getInventory().clear();
        p.setGameMode(GameMode.ADVENTURE);
        p.setAllowFlight(false);
        p.setFlying(false);
        p.setFoodLevel(20);
        p.setWalkSpeed(0.2f);
        p.teleport(arena.camera(s.index));
        p.getInventory().setHeldItemSlot(0);
        s.plasmaBar = BossBar.bossBar(Txt.mm(""), 0f, BossBar.Color.PURPLE, BossBar.Overlay.NOTCHED_10);
        s.infoBar = BossBar.bossBar(Txt.mm(""), 1f, s.index == 0 ? BossBar.Color.BLUE : BossBar.Color.RED, BossBar.Overlay.PROGRESS);
        p.showBossBar(s.infoBar);
        p.showBossBar(s.plasmaBar);
        giveHand(s, true);
        Side o = other(s);
        Txt.send(p, Txt.TITLE + " <#9AA4B2>· tu es</#9AA4B2> <" + TEAM[s.index] + "><b>" + (s.index == 0 ? "BLEU" : "ROUGE")
                + "</b></" + TEAM[s.index] + "> <#9AA4B2>contre</#9AA4B2> <b>" + o.name + "</b>.");
        Txt.send(p, "<#9AA4B2>Tu marches sur un plafond invisible juste au-dessus de l'arène. Choisis une carte (molette ou 1-4), vise le sol "
                + "et <#E6EDF3>clique</#E6EDF3> : un fantôme montre où elle arrive. Unités et bâtiments de ton côté (avant la ligne "
                + (s.index == 0 ? "bleue" : "rouge") + "). Les sorts se lancent partout. Détruis les tours adverses : "
                + "chaque tour = <#E0B864>★</#E0B864>, le Bastion = victoire.");
        if (!packMode) {
            Txt.send(p, "<#9AA4B2>Astuce : accepte le pack du serveur à la connexion pour voir les vraies unités ORBIT.");
        }
    }

    // ------------------------------------------------------------------ boucle

    void tick() {
        tick++;
        if (state == State.COUNTDOWN) {
            if (countdown % 20 == 0) {
                int n = countdown / 20;
                for (Side s : sides) {
                    if (s.human()) {
                        s.player.showTitle(Title.title(Txt.mm(n > 0 ? "<#E0B864><b>" + n + "</b>" : ""), Txt.mm("<#9AA4B2>Molette ou 1-4 : une carte · vise le sol · clic : poser"),
                                Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(100))));
                        s.player.playSound(s.player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 1.2f);
                    }
                }
            }
            if (--countdown <= 0) {
                state = State.PLAYING;
                for (Side s : sides) {
                    if (s.human()) {
                        s.player.showTitle(Title.title(Txt.mm("<gradient:#3A7FFF:#E0B864><b>À L'ASSAUT !</b></gradient>"), Txt.mm(""),
                                Title.Times.times(Duration.ZERO, Duration.ofMillis(800), Duration.ofMillis(300))));
                        s.player.playSound(s.player.getLocation(), Sound.EVENT_RAID_HORN, 0.5f, 1.4f);
                    }
                }
            }
            hud();
            return;
        }
        if (state == State.DONE) {
            return;
        }
        boolean doubled = state == State.OVERTIME || timeLeft <= 60 * 20;
        double regen = 1.0 / Math.max(5, game.plugin().getConfig().getInt("partie.plasma-ticks", 56)) * (doubled ? 2 : 1);
        for (Side s : sides) {
            s.plasma = Math.min(10, s.plasma + regen);
            if (s.bot != null) {
                s.bot.tick();
            }
        }
        // sorts en approche
        for (Strike st : new ArrayList<>(strikes)) {
            if (--st.left()[0] <= 0) {
                strikes.remove(st);
                impact(st.side(), st.card(), st.x(), st.z());
            } else if (st.left()[0] % 2 == 0) {
                ring(st.x(), st.z(), st.card().radius, Color.fromRGB(0xFF8A4A), 18);
                Location top = arena.at(st.x(), 14, st.z());
                world.spawnParticle(Particle.END_ROD, top.clone().subtract(0, 14 * (1 - st.left()[0] / 16.0), 0), 6, 0.15, 0.4, 0.15, 0);
            }
        }
        for (Body b : new ArrayList<>(bodies)) {
            if (!b.dead) {
                update(b);
            }
            if (state == State.DONE) {
                return;
            }
        }
        separate();
        bodies.removeIf(b -> b.dead);
        bodies.addAll(born);
        born.clear();
        for (Body b : bodies) {
            render(b);
        }
        if (tick % 2 == 0) {
            for (Side s : sides) {
                if (s.human()) {
                    aim(s);
                }
            }
        }
        if (tick % 40 == 0) {
            world.spawnParticle(Particle.PORTAL, arena.at(0, 0.2, 0), 40, 7, 0.3, 0.6, 0.4);
        }
        // temps
        if (state == State.PLAYING) {
            if (--timeLeft <= 0) {
                if (sides[0].stars != sides[1].stars) {
                    end(sides[0].stars > sides[1].stars ? 0 : 1, "fin du temps");
                    return;
                }
                state = State.OVERTIME;
                for (Side s : sides) {
                    if (s.human()) {
                        s.player.showTitle(Title.title(Txt.mm("<#FF5A5A><b>PROLONGATION</b>"), Txt.mm("<#E6EDF3>Plasma doublé · la prochaine étoile gagne"),
                                Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1800), Duration.ofMillis(300))));
                        s.player.playSound(s.player.getLocation(), Sound.BLOCK_BELL_USE, 0.8f, 0.8f);
                    }
                }
            } else if (timeLeft == 60 * 20) {
                for (Side s : sides) {
                    if (s.human()) {
                        Txt.bar(s.player, "<#C79BFF><b>⚡ Plasma doublé !</b> <#9AA4B2>Dernière minute.");
                        s.player.playSound(s.player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.6f, 1.6f);
                    }
                }
            }
        } else if (state == State.OVERTIME && --overtimeLeft <= 0) {
            double h0 = sides[0].towerHealth(), h1 = sides[1].towerHealth();
            end(Math.abs(h0 - h1) < 0.01 ? -1 : (h0 > h1 ? 0 : 1), "tours les plus abîmées");
            return;
        }
        if (tick % 4 == 0) {
            hud();
        }
    }

    // ------------------------------------------------------------------ unités

    private void update(Body b) {
        if (b.deploy > 0) {
            b.deploy--;
            if (b.deploy % 5 == 0) {
                world.spawnParticle(Particle.ELECTRIC_SPARK, arena.at(b.x, 0.2, b.z), 6, b.r, 0.1, b.r, 0.05);
            }
            return;
        }
        if (b.lifeMax > 0) {
            b.hp -= b.max / b.lifeMax;
            if (b.hp <= 0) {
                kill(b);
                return;
            }
        }
        if (b.card != null && b.card.spawnEvery > 0 && --b.spawnCd <= 0) {
            b.spawnCd = b.card.spawnEvery;
            Body o = new Body(b.side, Card.ESSAIM, b.x, b.z - sides[b.side].sign() * (b.r + 0.4));
            o.deploy = 4;
            born.add(o);
            world.spawnParticle(Particle.REVERSE_PORTAL, arena.at(b.x, 0.6, b.z), 20, 0.4, 0.4, 0.4, 0.05);
        }
        if (b.frozen > 0) {
            b.frozen--;
            if (b.frozen % 6 == 0) {
                world.spawnParticle(Particle.SNOWFLAKE, arena.at(b.x, 0.6 + (b.fly ? 2 : 0), b.z), 3, b.r, 0.3, b.r, 0);
            }
            return;
        }
        if (b.cd > 0) {
            b.cd--;
        }
        if (b.tower && !b.active) {
            return;
        }
        if (b.dmg <= 0) {
            return; // portail : ne se bat pas
        }
        if (b.target == null || !b.canHit(b.target) || (--b.retarget <= 0 && !b.inRange(b.target))) {
            Body before = b.target;
            b.target = acquire(b);
            b.retarget = 10;
            if (b.target != before && b.target != null && b.cd < b.hit / 2) {
                b.cd = b.hit / 2; // petit temps de visée
            }
        }
        if (b.target != null && b.inRange(b.target)) {
            if (b.cd <= 0) {
                attack(b, b.target);
                b.cd = b.hit;
            }
            return;
        }
        if (b.speed > 0) {
            Body goal = b.target != null ? b.target : defaultTower(b);
            if (goal != null) {
                move(b, goal.x, goal.z);
            }
        }
    }

    /** Cible la plus proche à portée de vue (ou de tir pour les tours et bâtiments). */
    private Body acquire(Body b) {
        double limit = b.mover() ? b.sight : b.range;
        Body best = null;
        double bestD = Double.MAX_VALUE;
        for (Body o : bodies) {
            if (!b.canHit(o)) {
                continue;
            }
            double d = b.edge(o);
            if (d <= limit && d < bestD) {
                best = o;
                bestD = d;
            }
        }
        return best;
    }

    /** Sans rien en vue : la Tourelle adverse de sa voie, sinon le Bastion. */
    private Body defaultTower(Body b) {
        Side enemy = sides[1 - b.side];
        int lane = b.x < -0.01 ? -1 : b.x > 0.01 ? 1 : 0;
        if (lane == 0) {
            Body l = enemy.left, r = enemy.right;
            boolean lAlive = l != null && !l.dead, rAlive = r != null && !r.dead;
            if (lAlive && rAlive) {
                lane = b.dist(l) <= b.dist(r) ? -1 : 1;
            } else {
                lane = lAlive ? -1 : 1;
            }
        }
        Body st = enemy.sideTower(lane);
        if (st != null && !st.dead) {
            return st;
        }
        return enemy.main != null && !enemy.main.dead ? enemy.main : null;
    }

    private static int half(double z) {
        return z < -Arena.RIVER ? -1 : z > Arena.RIVER ? 1 : 0;
    }

    private void move(Body b, double tx, double tz) {
        double gx = tx, gz = tz;
        if (!b.fly) {
            int from = half(b.z), to = half(tz);
            if (from != 0 && to != from) {
                double bx = Math.abs(b.x - Arena.BRIDGE_X) + Math.abs(tx - Arena.BRIDGE_X)
                        <= Math.abs(b.x + Arena.BRIDGE_X) + Math.abs(tx + Arena.BRIDGE_X) ? Arena.BRIDGE_X : -Arena.BRIDGE_X;
                if (Math.abs(b.x - bx) > 0.7) {
                    gx = bx;
                    gz = from * 2.2;
                } else {
                    gx = bx;
                    gz = -from * 2.4;
                }
            } else if (from == 0) {
                double bx = b.x < 0 ? -Arena.BRIDGE_X : Arena.BRIDGE_X;
                int dir = tz >= b.z ? 1 : -1;
                gx = bx;
                gz = dir * 2.4;
                if (Math.abs(tz) <= Arena.RIVER) {
                    gx = tx;
                    gz = tz;
                }
            }
        }
        double dx = gx - b.x, dz = gz - b.z, d = Math.hypot(dx, dz);
        if (d < 1e-4) {
            return;
        }
        double step = Math.min(d, b.speed / 20.0);
        double nx = b.x + dx / d * step, nz = b.z + dz / d * step;
        if (!b.fly && Math.abs(nz) < Arena.RIVER + b.r * 0.5 && Math.abs(Math.abs(nx) - Arena.BRIDGE_X) > 1.3 - b.r * 0.5) {
            // pas dans la faille : on longe jusqu'au pont
            nz = b.z;
            double bx = nx < 0 ? -Arena.BRIDGE_X : Arena.BRIDGE_X;
            nx = b.x + Math.signum(bx - b.x) * step;
        }
        b.x = nx;
        b.z = nz;
    }

    /** Les unités se poussent un peu pour ne pas toutes s'empiler ; on ne traverse pas les tours. */
    private void separate() {
        int n = bodies.size();
        for (int i = 0; i < n; i++) {
            Body a = bodies.get(i);
            if (a.dead) {
                continue;
            }
            for (int j = i + 1; j < n; j++) {
                Body c = bodies.get(j);
                if (c.dead || a.fly != c.fly || (!a.mover() && !c.mover())) {
                    continue;
                }
                double dx = c.x - a.x, dz = c.z - a.z, d = Math.hypot(dx, dz), min = a.r + c.r;
                if (d >= min || min <= 0) {
                    continue;
                }
                if (d < 1e-3) {
                    dx = (i - j) * 0.01;
                    dz = 0.01;
                    d = Math.hypot(dx, dz);
                }
                double push = (min - d) * 0.5;
                double ux = dx / d, uz = dz / d;
                if (a.mover() && c.mover()) {
                    a.x -= ux * push * 0.5;
                    a.z -= uz * push * 0.5;
                    c.x += ux * push * 0.5;
                    c.z += uz * push * 0.5;
                } else if (a.mover()) {
                    a.x -= ux * push;
                    a.z -= uz * push;
                } else {
                    c.x += ux * push;
                    c.z += uz * push;
                }
            }
            if (a.mover()) {
                a.x = Math.max(-Arena.HX - 0.3 + a.r, Math.min(Arena.HX + 0.3 - a.r, a.x));
                a.z = Math.max(-Arena.HZ - 0.3 + a.r, Math.min(Arena.HZ + 0.3 - a.r, a.z));
            }
        }
    }

    private void attack(Body b, Body t) {
        Color color = b.side == 0 ? BLUE : RED;
        double h0 = b.tower ? (b.main ? 6.2 : 5.0) : (b.fly ? 2.4 : 0.6) + (b.card == null ? 0 : b.scale * 0.3);
        Location from = arena.at(b.x, h0, b.z);
        Location to = arena.at(t.x, t.tower ? 2.5 : (t.fly ? 2.4 : 0.5), t.z);
        if (b.range >= 2 || b.tower) {
            line(from, to, color, b.tower ? 1.2f : 0.9f);
            world.playSound(from, b.tower ? Sound.ENTITY_BLAZE_SHOOT : Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.35f, b.tower ? 1.8f : 1.4f);
        } else {
            world.spawnParticle(Particle.CRIT, to, 6, 0.2, 0.2, 0.2, 0.1);
            world.playSound(to, Sound.ENTITY_PLAYER_ATTACK_WEAK, 0.4f, 1.1f);
        }
        if (b.splash > 0) {
            world.spawnParticle(Particle.WITCH, to, 25, b.splash * 0.5, 0.2, b.splash * 0.5, 0.05);
            for (Body o : new ArrayList<>(bodies)) {
                if (b.canHit(o) && Math.hypot(o.x - t.x, o.z - t.z) <= b.splash + o.r) {
                    damage(o, b.dmg);
                }
            }
        } else {
            damage(t, b.dmg);
        }
    }

    void damage(Body t, double amount) {
        if (t.dead || state == State.DONE) {
            return;
        }
        t.hp -= amount;
        if (t.tower && t.main) {
            wake(t);
        }
        if (t.hp <= 0) {
            kill(t);
        } else {
            refreshBar(t);
        }
    }

    private void wake(Body main) {
        if (!main.active) {
            main.active = true;
            world.playSound(arena.at(main.x, 6, main.z), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.2f);
        }
    }

    private void kill(Body b) {
        if (b.dead) {
            return;
        }
        b.dead = true;
        b.hp = 0;
        Location at = arena.at(b.x, b.fly ? 2.4 : 0.5, b.z);
        if (b.view != null) {
            b.view.remove();
        }
        if (b.bar != null) {
            b.bar.remove();
        }
        if (!b.tower) {
            world.spawnParticle(Particle.POOF, at, 8, b.r, 0.3, b.r, 0.02);
            world.playSound(at, Sound.ENTITY_ITEM_BREAK, 0.4f, 0.8f + (float) Math.random() * 0.4f);
            return;
        }
        arena.rubble(b);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, arena.at(b.x, 2, b.z), 2);
        world.spawnParticle(Particle.LARGE_SMOKE, arena.at(b.x, 2, b.z), 40, 1.2, 1.5, 1.2, 0.02);
        world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.8f);
        Side owner = sides[b.side], winner = other(owner);
        if (b.main) {
            winner.stars = 3;
        } else {
            winner.stars = Math.min(3, winner.stars + 1);
            if (owner.main != null) {
                wake(owner.main);
            }
        }
        for (Side s : sides) {
            if (s.human()) {
                boolean mine = s == winner;
                s.player.showTitle(Title.title(Txt.mm(mine ? "<#E0B864><b>★ " + b.label().toUpperCase(Locale.ROOT) + " DÉTRUIT"
                                + (b.main ? "" : "E") + " !</b>" : "<#FF5A5A><b>Tu as perdu " + (b.main ? "ton Bastion" : "une Tourelle") + "</b>"),
                        Txt.mm("<#E6EDF3>★ " + sides[s.index].stars + " – " + other(s).stars),
                        Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1400), Duration.ofMillis(300))));
                s.player.playSound(s.player.getLocation(), mine ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_WITHER_BREAK_BLOCK, 0.6f, 1.1f);
            }
        }
        if (b.main) {
            end(winner.index, "Bastion détruit");
        } else if (state == State.OVERTIME) {
            end(winner.index, "étoile en prolongation");
        }
    }

    // ------------------------------------------------------------------ cartes

    /** Zone où ce camp peut poser une unité ou un bâtiment (son demi-terrain, plus la poche d'une Tourelle tombée). */
    boolean canDeploy(Side s, Card card, double x, double z) {
        if (Math.abs(x) > Arena.HX + 0.4 || Math.abs(z) > Arena.HZ + 0.4) {
            return false;
        }
        if (card.kind == Card.Kind.SPELL) {
            return true;
        }
        double own = z * s.sign();
        boolean ok = own >= 2.0;
        if (!ok && card.kind == Card.Kind.TROOP && own >= -5.5 && own < -Arena.RIVER - 0.2) {
            Body st = other(s).sideTower(x < 0 ? -1 : 1);
            ok = st == null || st.dead;
        }
        if (!ok) {
            return false;
        }
        for (Body t : bodies) {
            if (t.tower && !t.dead && Math.hypot(t.x - x, t.z - z) < t.r + 0.2) {
                return false;
            }
        }
        return true;
    }

    /**
     * Point de pose réel : un clic trop loin (camp adverse, faille, dans une tour) recale la carte sur l'endroit
     * permis le plus proche au lieu de la refuser. null si on vise hors de l'arène.
     */
    double[] snap(Side s, Card c, double x, double z) {
        if (Math.abs(x) > Arena.HX + 3 || Math.abs(z) > Arena.HZ + 3) {
            return null;
        }
        x = Math.max(-Arena.HX + 0.3, Math.min(Arena.HX - 0.3, x));
        z = Math.max(-Arena.HZ + 0.3, Math.min(Arena.HZ - 0.3, z));
        if (c.kind != Card.Kind.SPELL && !canDeploy(s, c, x, z)) {
            if (z * s.sign() < 2.0) {
                z = s.sign() * 2.2;
            }
            for (Body t : bodies) {
                double d = Math.hypot(x - t.x, z - t.z);
                if (t.tower && !t.dead && d < t.r + 0.25) {
                    double ux = d < 1e-3 ? 1 : (x - t.x) / d, uz = d < 1e-3 ? 0 : (z - t.z) / d;
                    x = t.x + ux * (t.r + 0.3);
                    z = t.z + uz * (t.r + 0.3);
                }
            }
        }
        return canDeploy(s, c, x, z) ? new double[]{x, z} : null;
    }

    /** Joue la carte n° slot de la main au point (x, z). Renvoie un message d'erreur, ou null si c'est joué. */
    String play(Side s, int slot, double x, double z) {
        if (state != State.PLAYING && state != State.OVERTIME) {
            return "Attends le signal !";
        }
        Card c = s.hand[slot];
        if (c == null) {
            return "Pas de carte ici.";
        }
        if (s.plasma < c.cost) {
            return "Pas assez de plasma (" + (int) s.plasma + " / " + c.cost + ").";
        }
        double[] q = snap(s, c, x, z);
        if (q == null) {
            return c.kind == Card.Kind.SPELL ? "Vise l'arène." : "Pose-la de ton côté de l'arène.";
        }
        x = q[0];
        z = q[1];
        s.plasma -= c.cost;
        s.cycle(slot);
        s.played++;
        deploy(s.index, c, x, z);
        if (s.human()) {
            giveHand(s, false);
            s.player.playSound(s.player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.6f);
            Txt.bar(s.player, "<#7CFF8A>✔ " + c.nom + (c.kind == Card.Kind.SPELL ? " lancée" : " posée") + "</#7CFF8A> <#9AA4B2>· plasma " + (int) s.plasma);
        }
        return null;
    }

    private void deploy(int side, Card c, double x, double z) {
        switch (c.kind) {
            case SPELL -> {
                if (c == Card.FRAPPE) {
                    strikes.add(new Strike(side, c, x, z, new int[]{16}));
                    world.playSound(arena.at(x, 6, z), Sound.BLOCK_BEACON_POWER_SELECT, 1f, 0.6f);
                } else {
                    impact(side, c, x, z);
                }
            }
            default -> {
                for (int i = 0; i < c.count; i++) {
                    double a = Math.PI * 2 * i / c.count, rr = c.count > 1 ? 0.55 : 0;
                    Body b = new Body(side, c, x + Math.cos(a) * rr, z + Math.sin(a) * rr);
                    born.add(b);
                }
                world.playSound(arena.at(x, 1, z), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.6f, 1.4f);
                world.spawnParticle(Particle.END_ROD, arena.at(x, 0.3, z), 15, 0.5, 0.2, 0.5, 0.03);
            }
        }
    }

    private void impact(int side, Card c, double x, double z) {
        Location at = arena.at(x, 0.5, z);
        if (c == Card.FRAPPE) {
            world.spawnParticle(Particle.EXPLOSION_EMITTER, at, 1);
            world.spawnParticle(Particle.FLAME, at, 60, c.radius * 0.5, 0.3, c.radius * 0.5, 0.08);
            world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.1f);
        } else {
            ring(x, z, c.radius, Color.fromRGB(0xBFEFFF), 40);
            world.spawnParticle(Particle.SNOWFLAKE, at, 80, c.radius * 0.5, 0.5, c.radius * 0.5, 0.05);
            world.playSound(at, Sound.BLOCK_GLASS_BREAK, 1f, 1.4f);
        }
        for (Body o : new ArrayList<>(bodies)) {
            if (o.dead || o.side == side) {
                continue;
            }
            if (Math.hypot(o.x - x, o.z - z) <= c.radius + o.r) {
                if (c.freeze > 0) {
                    o.frozen = Math.max(o.frozen, c.freeze);
                }
                damage(o, o.tower ? c.dmg * c.towerPct : c.dmg);
            }
        }
    }

    // ------------------------------------------------------------------ affichage

    private TextDisplay text(Location loc, String s, float scale) {
        return world.spawn(loc, TextDisplay.class, e -> {
            e.setPersistent(false);
            game.tag(e);
            e.text(Txt.mm(s));
            e.setBillboard(Display.Billboard.CENTER);
            e.setAlignment(TextDisplay.TextAlignment.CENTER);
            e.setShadowed(true);
            e.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            e.setBrightness(new Display.Brightness(15, 15));
            e.setTeleportDuration(2);
            e.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(scale, scale, scale), new AxisAngle4f()));
        });
    }

    ItemStack unitItem(Card c, int side) {
        if (!packMode) {
            return new ItemStack(c.fallback);
        }
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta();
        m.setItemModel(new NamespacedKey("orbitroyale", "unit_" + c.id + (side == 0 ? "_b" : "_r")));
        it.setItemMeta(m);
        return it;
    }

    private double viewY(Body b) {
        return (b.fly ? 2.2 : 0) + b.scale * 0.5;
    }

    private void render(Body b) {
        if (b.tower) {
            if (b.bar != null && tick % 4 == 0) {
                refreshBar(b);
            }
            return;
        }
        if (b.view == null) {
            Location loc = arena.at(b.x, viewY(b), b.z);
            b.view = world.spawn(loc, ItemDisplay.class, e -> {
                e.setPersistent(false);
                game.tag(e);
                e.setItemStack(unitItem(b.card, b.side));
                e.setBillboard(Display.Billboard.CENTER);
                e.setBrightness(new Display.Brightness(15, 15));
                e.setTeleportDuration(2);
                e.setShadowRadius(b.fly ? 0.6f : 0.35f);
                e.setShadowStrength(0.9f);
                e.setViewRange(2f);
                e.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(b.scale, b.scale, b.scale), new AxisAngle4f()));
                if (!packMode) {
                    e.setGlowing(true);
                    e.setGlowColorOverride(b.side == 0 ? BLUE : RED);
                }
            });
            b.bar = text(arena.at(b.x, viewY(b) + b.scale * 0.5 + 0.25, b.z), "", 0.7f);
            b.lastX = b.x;
            b.lastZ = b.z;
        } else if (Math.abs(b.x - b.lastX) > 0.005 || Math.abs(b.z - b.lastZ) > 0.005) {
            b.view.teleport(arena.at(b.x, viewY(b), b.z));
            b.bar.teleport(arena.at(b.x, viewY(b) + b.scale * 0.5 + 0.25, b.z));
            b.lastX = b.x;
            b.lastZ = b.z;
        }
        if (tick % 3 == 0) {
            refreshBar(b);
        }
    }

    private void refreshBar(Body b) {
        if (b.bar == null) {
            return;
        }
        String s;
        String col = TEAM[b.side];
        if (b.tower) {
            int cells = 10, full = (int) Math.ceil(b.hp / b.max * cells);
            s = "<" + col + ">" + "▮".repeat(Math.max(0, full)) + "</" + col + "><#3A3F4A>" + "▮".repeat(cells - Math.max(0, full))
                    + "</#3A3F4A>\n<#E6EDF3>" + Txt.n((long) Math.ceil(b.hp)) + (b.main && !b.active ? " <#9AA4B2>💤" : "");
        } else if (b.deploy > 0) {
            s = "<#9AA4B2>…";
        } else if (b.hp >= b.max - 0.5 && b.lifeMax == 0) {
            s = "";
        } else {
            int cells = 6, full = (int) Math.ceil(b.hp / b.max * cells);
            s = "<" + col + ">" + "▮".repeat(Math.max(0, full)) + "</" + col + "><#3A3F4A>" + "▮".repeat(cells - Math.max(0, full));
        }
        if (b.frozen > 0) {
            s = "<#BFEFFF>❄</#BFEFFF> " + s;
        }
        if (!s.equals(b.lastBar)) {
            b.lastBar = s;
            b.bar.text(Txt.mm(s));
        }
    }

    private void line(Location a, Location b, Color c, float size) {
        Vector d = b.toVector().subtract(a.toVector());
        double len = d.length();
        if (len < 0.01) {
            return;
        }
        d.multiply(1 / len);
        Particle.DustOptions dust = new Particle.DustOptions(c, size);
        for (double t = 0; t <= len; t += 0.45) {
            world.spawnParticle(Particle.DUST, a.clone().add(d.clone().multiply(t)), 1, 0, 0, 0, 0, dust);
        }
    }

    private void ring(double x, double z, double radius, Color c, int points) {
        Particle.DustOptions dust = new Particle.DustOptions(c, 1.1f);
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            world.spawnParticle(Particle.DUST, arena.at(x + Math.cos(a) * radius, 0.15, z + Math.sin(a) * radius), 1, 0, 0, 0, 0, dust);
        }
    }

    /** Point visé par le joueur sur le sol de l'arène (null s'il regarde en l'air). */
    double[] aimPoint(Player p) {
        Location eye = p.getEyeLocation();
        Vector d = eye.getDirection();
        double planeY = Arena.Y + 1;
        if (d.getY() > -0.02) {
            return null;
        }
        double t = (planeY - eye.getY()) / d.getY();
        if (t > 80) {
            return null;
        }
        double wx = eye.getX() + d.getX() * t, wz = eye.getZ() + d.getZ() * t;
        return new double[]{arena.localX(wx), arena.localZ(wz)};
    }

    private void aim(Side s) {
        Player p = s.player;
        int slot = p.getInventory().getHeldItemSlot();
        Card c = slot <= 3 ? s.hand[slot] : null;
        double[] pt = c == null ? null : aimPoint(p);
        double[] q = pt == null ? null : snap(s, c, pt[0], pt[1]);
        if (c == null) {
            hideGhost(s);
            return;
        }
        if (tick % 10 == 0 && c.kind != Card.Kind.SPELL) {
            // limite de pose
            Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(s.index == 0 ? 0x62CFF4 : 0xFF5A5A), 0.8f);
            for (double x = -Arena.HX; x <= Arena.HX; x += 1) {
                p.spawnParticle(Particle.DUST, arena.at(x, 0.15, s.sign() * 2.0 - s.sign() * 0.5), 1, 0, 0, 0, 0, dust);
            }
        }
        if (q == null) {
            hideGhost(s);
            return;
        }
        boolean ok = s.plasma >= c.cost;
        Color col = ok ? Color.fromRGB(0x7CFF8A) : Color.fromRGB(0xC79BFF);
        Particle.DustOptions dust = new Particle.DustOptions(col, 1.3f);
        double radius = c.kind == Card.Kind.SPELL ? c.radius : Math.max(0.5, c.size + (c.count > 1 ? 0.5 : 0));
        int points = c.kind == Card.Kind.SPELL ? 24 : 12;
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points + tick * 0.05;
            p.spawnParticle(Particle.DUST, arena.at(q[0] + Math.cos(a) * radius, 0.15, q[1] + Math.sin(a) * radius), 1, 0, 0, 0, 0, dust);
        }
        // fantôme de la carte à l'endroit exact où elle arrivera (visible seulement par ce joueur)
        if (c.kind == Card.Kind.SPELL) {
            hideGhost(s);
            return;
        }
        Location at = arena.at(q[0], (c.fly ? 2.2 : 0) + c.scale * 0.5, q[1]);
        if (s.ghost == null || !s.ghost.isValid() || s.ghostCard != c) {
            hideGhost(s);
            s.ghostCard = c;
            s.ghost = world.spawn(at, ItemDisplay.class, e -> {
                e.setPersistent(false);
                game.tag(e);
                e.setVisibleByDefault(false);
                e.setItemStack(unitItem(c, s.index));
                e.setBillboard(Display.Billboard.CENTER);
                e.setBrightness(new Display.Brightness(15, 15));
                e.setTeleportDuration(1);
                e.setGlowing(true);
                e.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(c.scale, c.scale, c.scale), new AxisAngle4f()));
            });
            p.showEntity(game.plugin(), s.ghost);
        } else {
            s.ghost.teleport(at);
        }
        s.ghost.setGlowColorOverride(col);
    }

    void hideGhost(Side s) {
        if (s.ghost != null) {
            s.ghost.remove();
            s.ghost = null;
            s.ghostCard = null;
        }
    }

    /** Barre d'objets : les 4 cartes de la main (quantité = coût), la suivante en case 6, quitter en case 9. */
    void giveHand(Side s, boolean force) {
        if (!s.human()) {
            return;
        }
        StringBuilder key = new StringBuilder();
        for (Card c : s.hand) {
            key.append(c == null ? "-" : c.id).append(s.plasma >= (c == null ? 99 : c.cost) ? '+' : '.');
        }
        key.append(s.next() == null ? "" : s.next().id);
        if (!force && key.toString().equals(s.handKey)) {
            return;
        }
        s.handKey = key.toString();
        boolean pack = game.pack().isLoaded(s.player);
        for (int i = 0; i < 4; i++) {
            Card c = s.hand[i];
            s.player.getInventory().setItem(i, c == null ? null : game.cardItem(c, pack, s.plasma >= c.cost, i + 1));
        }
        Card n = s.next();
        s.player.getInventory().setItem(5, n == null ? null : game.nextItem(n, pack));
        s.player.getInventory().setItem(8, game.quitItem());
    }

    private void hud() {
        for (Side s : sides) {
            if (!s.human()) {
                continue;
            }
            Side o = other(s);
            int secs = (state == State.OVERTIME ? overtimeLeft : timeLeft) / 20;
            String clock = (state == State.OVERTIME ? "<#FF5A5A><b>PROLONGATION</b></#FF5A5A> " : "<#E6EDF3>⏱ ")
                    + secs / 60 + ":" + String.format("%02d", secs % 60);
            s.infoBar.name(Txt.mm(clock + "   <" + TEAM[s.index] + ">★ " + s.stars + "</" + TEAM[s.index] + "> <#5A6270>–</#5A6270> <"
                    + TEAM[o.index] + ">★ " + o.stars + "</" + TEAM[o.index] + ">   <#9AA4B2>contre " + o.name));
            int total = state == State.OVERTIME ? game.plugin().getConfig().getInt("partie.prolongation", 60) * 20
                    : game.plugin().getConfig().getInt("partie.duree", 180) * 20;
            s.infoBar.progress(Math.max(0f, Math.min(1f, (state == State.OVERTIME ? overtimeLeft : timeLeft) / (float) total)));
            boolean doubled = state == State.OVERTIME || timeLeft <= 60 * 20;
            Card n = s.next();
            s.plasmaBar.name(Txt.mm("<#C79BFF><b>⚡ PLASMA " + (int) Math.floor(s.plasma) + " / 10</b></#C79BFF>" + (doubled ? " <#FF8A4A>×2</#FF8A4A>" : "")
                    + (n == null ? "" : "   <#5A6270>suivante :</#5A6270> <" + n.color() + ">" + n.nom + "</" + n.color() + "> <#C79BFF>" + n.cost)));
            s.plasmaBar.progress((float) Math.max(0, Math.min(1, s.plasma / 10.0)));
            giveHand(s, false);
            int slot = s.player.getInventory().getHeldItemSlot();
            if (slot <= 3 && s.hand[slot] != null && tick % 8 == 0) {
                Card c = s.hand[slot];
                Txt.bar(s.player, "<" + c.color() + "><b>" + c.nom + "</b></" + c.color() + "> <#C79BFF>⚡" + c.cost + "</#C79BFF> <#5A6270>·</#5A6270> <#9AA4B2>"
                        + c.typeLabel() + (s.plasma >= c.cost ? " · clic pour poser" : " · plasma insuffisant"));
            }
        }
    }

    // ------------------------------------------------------------------ fin

    /** Fin de partie. winner = 0 ou 1, -1 pour une égalité. */
    void end(int winner, String reason) {
        if (state == State.DONE) {
            return;
        }
        state = State.DONE;
        game.finish(this, winner, reason);
    }

    /** Retire tout ce que la partie a posé dans le monde et rend les joueurs. */
    void cleanup() {
        for (Body b : bodies) {
            if (b.view != null) {
                b.view.remove();
            }
            if (b.bar != null) {
                b.bar.remove();
            }
        }
        for (Body b : born) {
            if (b.view != null) {
                b.view.remove();
            }
        }
        bodies.clear();
        born.clear();
        for (Entity e : deco) {
            e.remove();
        }
        deco.clear();
        for (Entity e : world.getNearbyEntities(arena.at(0, 5, 0), 20, 30, Arena.CLEAR_Z)) {
            if (game.isOurs(e)) {
                e.remove();
            }
        }
        for (Side s : sides) {
            hideGhost(s);
        }
        for (Side s : sides) {
            if (s.human()) {
                Player p = s.player;
                if (s.plasmaBar != null) {
                    p.hideBossBar(s.plasmaBar);
                    p.hideBossBar(s.infoBar);
                }
            }
        }
        arena.buildTowers();
        arena.loadChunks(game.plugin(), false);
    }
}
