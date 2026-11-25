package fr.orbit.bornes.cristaux;

import fr.orbit.bornes.Game;
import fr.orbit.bornes.GameType;
import fr.orbit.bornes.Hud;
import fr.orbit.bornes.OrbitBornes;
import fr.orbit.bornes.Retitle;
import fr.orbit.bornes.Txt;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * CRISTAUX D'ORBIT : alignements de cristaux sur un écran HD (coffre de 6 lignes dont le titre, écrit avec la police
 * du pack, dessine le plateau étoilé et le panneau de droite). 15 niveaux avec un nombre de coups, un objectif et
 * 1 à 3 étoiles ; les étoiles sont gardées en base (bornes_cristaux), leur total va au tableau des records.
 * Sans le pack, l'écran garde des objets ordinaires (pierres précieuses) et un titre en texte simple.
 * <p>Plateau : case (r, c), c = 0 à 6 → case du coffre r × 9 + c ; panneau : colonnes 7 et 8, boutons en 52 et 53.
 * Fermer l'écran met en pause (2 minutes) ; le PNJ ou /bornes reprendre le rouvre.</p>
 */
public final class CristauxGame extends Game {

    /** {coups, couleurs, objectif} ; 2 étoiles à 130 %, 3 étoiles à 160 % (calibré par simulation, voir docs). */
    static final int[][] LEVELS = {
            {20, 5, 2500}, {20, 5, 3000}, {18, 5, 3000}, {18, 5, 3500}, {16, 5, 3500},
            {22, 6, 2200}, {20, 6, 2200}, {20, 6, 2500}, {18, 6, 2300}, {18, 6, 2600},
            {16, 6, 2300}, {16, 6, 2600}, {15, 6, 2500}, {14, 6, 2500}, {14, 6, 2800}};
    static final double STAR2 = 1.3, STAR3 = 1.6;
    static final String[] NAMES = {"rubis", "ambre", "émeraude", "saphir", "améthyste", "glace"};
    static final Material[] PLAIN = {Material.REDSTONE, Material.GOLD_INGOT, Material.EMERALD, Material.LAPIS_LAZULI,
            Material.AMETHYST_SHARD, Material.PRISMARINE_CRYSTALS};

    enum Mode { LOADING, SELECT, PLAY, RESULT }

    enum Phase { IDLE, SWAP, BACK, CLEAR, FALL }

    final class Screen implements InventoryHolder {
        Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }

        CristauxGame game() {
            return CristauxGame.this;
        }
    }

    private final Screen screen = new Screen();
    private boolean hd, reopening, paused;
    private int pausedTicks;
    private int[] stars = new int[LEVELS.length];
    private Mode mode = Mode.LOADING;
    private Phase phase = Phase.IDLE;
    private int level, movesLeft, score, cascade, timer, selected = -1, swapA, swapB;
    private Grid grid;
    private Grid.Resolution pending;
    private Component title, shownTitle;
    private final String[] keys = new String[54];
    private final ItemStack[] items = new ItemStack[54];
    private final Runnable[] actions = new Runnable[54];

    public CristauxGame(OrbitBornes plugin, Player player) {
        super(plugin, player, GameType.CRISTAUX);
    }

    // ------------------------------------------------------------------ cycle de vie

    @Override
    protected void start() {
        hd = plugin.pack().isLoaded(player);
        if (!hd) {
            Txt.send(player, "<#9AA4B2>Sans le pack des bornes, l'écran reste en version simple (reconnecte-toi et accepte le pack pour la version HD).");
        }
        plugin.loadStars(player.getUniqueId(), LEVELS.length, s -> {
            if (isEnded() || !player.isOnline()) {
                return;
            }
            stars = s;
            showSelect();
        });
    }

    /** Rouvre l'écran après l'avoir fermé (pause). */
    public void reopen() {
        if (isEnded()) {
            return;
        }
        paused = false;
        shownTitle = null;
        open(true);
    }

    void closed() {
        if (reopening || isEnded() || mode == Mode.LOADING) {
            return;
        }
        paused = true;
        pausedTicks = 0;
        Txt.send(player, GameType.CRISTAUX.title() + " <#9AA4B2>en pause (2 minutes).");
        player.sendMessage(Txt.mm("  ").append(Txt.button("Reprendre", "#FF4FD8", "/bornes reprendre", "Rouvrir l'écran"))
                .append(Txt.mm(" ")).append(Txt.button("Quitter", "#5A6270", "/bornes quitter", "Arrêter CRISTAUX D'ORBIT")));
    }

    @Override
    protected void tick() {
        if (paused) {
            if (++pausedTicks > 2400) {
                plugin.sessions().finish(this, null);
            }
            return;
        }
        if (mode != Mode.PLAY || timer <= 0 || --timer > 0) {
            return;
        }
        step();
    }

    @Override
    protected void cleanup() {
        if (player.isOnline() && screen.inv != null && player.getOpenInventory().getTopInventory().getHolder() == screen) {
            reopening = true;
            player.closeInventory();
            reopening = false;
        }
    }

    // ------------------------------------------------------------------ choix du niveau

    private boolean unlocked(int i) {
        return i == 0 || stars[i - 1] > 0;
    }

    private int totalStars() {
        int t = 0;
        for (int s : stars) {
            t += s;
        }
        return t;
    }

    private void showSelect() {
        mode = Mode.SELECT;
        Arrays.fill(actions, null);
        for (int i = 0; i < 54; i++) {
            put(i, null, null, null);
        }
        for (int i = 0; i < LEVELS.length; i++) {
            int slot = (i / 7) * 9 + i % 7;
            int lvl = i;
            if (!unlocked(i)) {
                put(slot, icon("lvl_lock", Material.GRAY_DYE, "<#5A6270>Niveau " + (i + 1) + " · verrouillé",
                        "<#9AA4B2>Gagne le niveau " + i + " pour l'ouvrir"), "lock" + i, null);
            } else {
                int[] l = LEVELS[i];
                ItemStack it = icon("lvl_" + stars[i], stars[i] > 0 ? Material.NETHER_STAR : Material.ENDER_EYE,
                        "<#E0B864><b>Niveau " + (i + 1) + "</b> " + starText(stars[i]),
                        "<#E6EDF3>Objectif " + Txt.n(l[2]) + " pts en " + l[0] + " coups",
                        "<#9AA4B2>" + l[1] + " couleurs · ★★ " + Txt.n(Math.round(l[2] * STAR2)) + " · ★★★ " + Txt.n(Math.round(l[2] * STAR3)),
                        "<#62CFF4>Clic : jouer");
                it.setAmount(i + 1);
                put(slot, it, "lvl" + i + "_" + stars[i], () -> startLevel(lvl));
            }
        }
        put(53, icon("btn_quitter", Material.BARRIER, "<#E0484A>Quitter"), "quit", () -> plugin.sessions().finish(this, null));
        title = header("CRISTAUX D'ORBIT · CHOISIS UN NIVEAU", "-", totalStars() + " ★", "", "", "");
        open(true);
    }

    // ------------------------------------------------------------------ partie

    private void startLevel(int i) {
        level = i;
        int[] l = LEVELS[i];
        grid = new Grid(l[1], System.nanoTime() ^ player.getUniqueId().getMostSignificantBits());
        movesLeft = l[0];
        score = 0;
        selected = -1;
        phase = Phase.IDLE;
        mode = Mode.PLAY;
        timer = 0;
        Arrays.fill(actions, null);
        for (int s = 0; s < 54; s++) {
            put(s, null, null, null);
        }
        put(52, icon("btn_rejouer", Material.CLOCK, "<#62CFF4>Recommencer le niveau"), "restart", () -> startLevel(level));
        put(53, icon("btn_niveaux", Material.MAP, "<#C79BFF>Retour aux niveaux"), "levels", this::showSelect);
        drawBoard(null);
        updateTitle();
        open(true);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8f, 1.2f);
    }

    void click(int slot) {
        if (slot < 0 || slot >= 54) {
            return;
        }
        if (mode == Mode.PLAY && slot % 9 < 7) {
            if (phase != Phase.IDLE) {
                return;
            }
            int cell = (slot / 9) * Grid.W + slot % 9;
            if (selected < 0) {
                selected = cell;
                player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.6f, 1.6f);
            } else if (selected == cell) {
                selected = -1;
            } else if (grid.adjacent(selected, cell)) {
                trySwap(selected, cell);
                return;
            } else {
                selected = cell;
            }
            drawBoard(null);
            return;
        }
        Runnable a = actions[slot];
        if (a != null) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.4f);
            a.run();
        }
    }

    private void trySwap(int a, int b) {
        selected = -1;
        swapA = a;
        swapB = b;
        grid.swap(a, b);
        drawBoard(null);
        Grid.Resolution r = grid.comboSwap(a, b);
        if (r == null) {
            r = grid.matches(a, b);
        }
        if (r.empty()) {
            phase = Phase.BACK;
            timer = 5;
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.6f);
            return;
        }
        movesLeft--;
        cascade = 1;
        pending = r;
        phase = Phase.SWAP;
        timer = 4;
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_CLUSTER_PLACE, 0.8f, 1.2f);
    }

    /** Étape suivante de l'animation (minuteur écoulé). */
    private void step() {
        switch (phase) {
            case BACK -> {
                grid.swap(swapA, swapB);
                phase = Phase.IDLE;
                drawBoard(null);
            }
            case SWAP -> {
                drawBoard(pending);
                phase = Phase.CLEAR;
                timer = 5;
                player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_BREAK, 0.9f, Math.min(2f, 0.9f + cascade * 0.15f));
                if (!pending.created.isEmpty()) {
                    player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.7f, 1.6f);
                }
            }
            case CLEAR -> {
                score += grid.apply(pending, cascade);
                pending = null;
                phase = Phase.FALL;
                timer = 2;
                drawBoard(null);
                updateTitle();
            }
            case FALL -> {
                if (grid.fallStep()) {
                    drawBoard(null);
                    timer = 2;
                    player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_STEP, 0.3f, 1.5f);
                    return;
                }
                Grid.Resolution r = grid.matches(-1, -1);
                if (!r.empty()) {
                    cascade++;
                    pending = r;
                    phase = Phase.SWAP;
                    timer = 2;
                    if (cascade == 3) {
                        Txt.title(player, "", "<gradient:#FF4FD8:#E0B864><b>Réaction en chaîne !</b></gradient>", 700);
                    }
                    return;
                }
                if (!grid.hasMove()) {
                    grid.shuffle();
                    Txt.bar(player, "<#C79BFF>Plus de coup possible : les cristaux se mélangent.");
                    drawBoard(null);
                }
                phase = Phase.IDLE;
                updateTitle();
                if (movesLeft <= 0) {
                    endLevel();
                }
            }
            default -> {
            }
        }
    }

    private int starsFor(int s) {
        int t = LEVELS[level][2];
        return s >= t * STAR3 ? 3 : s >= t * STAR2 ? 2 : s >= t ? 1 : 0;
    }

    private void endLevel() {
        mode = Mode.RESULT;
        int got = starsFor(score);
        boolean firstWin = got > 0 && stars[level] == 0;
        if (got > stars[level]) {
            stars[level] = got;
            plugin.saveStars(player, stars);
        }
        Arrays.fill(actions, null);
        for (int s = 0; s < 54; s++) {
            put(s, null, null, null);
        }
        put(12, icon("lvl_" + got, got > 0 ? Material.NETHER_STAR : Material.ENDER_EYE,
                got > 0 ? "<#E0B864><b>Niveau " + (level + 1) + " réussi</b> " + starText(got) : "<#E0484A><b>Objectif manqué</b>",
                "<#E6EDF3>" + Txt.n(score) + " / " + Txt.n(LEVELS[level][2]) + " pts"), "res" + got, null);
        put(28, icon("btn_rejouer", Material.CLOCK, "<#62CFF4>Rejouer ce niveau"), "again", () -> startLevel(level));
        put(30, icon("btn_niveaux", Material.MAP, "<#C79BFF>Niveaux"), "lvls", this::showSelect);
        if (got > 0 && level + 1 < LEVELS.length) {
            put(32, icon("btn_suivant", Material.LIME_DYE, "<#7CFF8A>Niveau suivant"), "next", () -> startLevel(level + 1));
        }
        put(53, icon("btn_quitter", Material.BARRIER, "<#E0484A>Quitter"), "quit", () -> plugin.sessions().finish(this, null));
        title = header(got > 0 ? "NIVEAU " + (level + 1) + " RÉUSSI !" : "OBJECTIF MANQUÉ...", String.valueOf(level + 1), Txt.n(score),
                Txt.n(LEVELS[level][2]), "0", starText(got));
        open(false);
        player.playSound(player.getLocation(), got > 0 ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.BLOCK_BEACON_DEACTIVATE, 0.7f, 1.2f);
        int oc = got * plugin.getConfig().getInt("cristaux.oc-par-etoile", 15);
        plugin.sessions().reward(player, GameType.CRISTAUX, totalStars(), oc,
                "niveau " + (level + 1) + " · <b>" + Txt.n(score) + "</b> pts · " + (got > 0 ? "<#E0B864>" + starText(got) + "</#E0B864>" : "objectif manqué")
                        + (firstWin && level + 1 < LEVELS.length ? " · <#7CFF8A>niveau " + (level + 2) + " ouvert</#7CFF8A>" : "")
                        + " · " + totalStars() + " ★ en tout");
    }

    private static String starText(int n) {
        return "★".repeat(n) + "☆".repeat(3 - n);
    }

    // ------------------------------------------------------------------ dessin

    private void drawBoard(Grid.Resolution burst) {
        for (int i = 0; i < Grid.N; i++) {
            int slot = (i / Grid.W) * 9 + i % Grid.W;
            if (burst != null && burst.cleared.get(i)) {
                put(slot, icon("burst", Material.FIREWORK_STAR, null), "burst", null);
                continue;
            }
            if (grid.empty(i)) {
                put(slot, null, null, null);
                continue;
            }
            boolean sel = i == selected;
            int sp = grid.special[i];
            String model;
            if (sp == Grid.NOVA) {
                model = sel ? "nova_sel" : "nova";
            } else {
                String suf = switch (sp) {
                    case Grid.STRIPE_H -> "_h";
                    case Grid.STRIPE_V -> "_v";
                    case Grid.BOMB -> "_b";
                    default -> "";
                };
                model = "gem_" + grid.color[i] + suf + (sel ? "_sel" : "");
            }
            if (!Objects.equals(keys[slot], model)) {
                put(slot, gemItem(grid.color[i], sp, sel, model), model, null);
            }
        }
    }

    private ItemStack gemItem(int color, int sp, boolean sel, String model) {
        String name = sp == Grid.NOVA ? "<gradient:#FF4FD8:#62CFF4><b>NOVA</b></gradient>"
                : "<#E6EDF3>Cristal " + NAMES[color] + switch (sp) {
                    case Grid.STRIPE_H -> " <#62CFF4>rayé ⇆";
                    case Grid.STRIPE_V -> " <#62CFF4>rayé ⇅";
                    case Grid.BOMB -> " <#FF4FD8>pulsar";
                    default -> "";
                };
        String help = sp == Grid.NOVA ? "<#9AA4B2>Échange-la avec un cristal : toute sa couleur disparaît"
                : sp == Grid.STRIPE_H ? "<#9AA4B2>Efface toute sa ligne" : sp == Grid.STRIPE_V ? "<#9AA4B2>Efface toute sa colonne"
                : sp == Grid.BOMB ? "<#9AA4B2>Explose en 3 × 3" : null;
        ItemStack it = hd ? model(model) : new ItemStack(sp == Grid.NOVA ? Material.NETHER_STAR : PLAIN[color]);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Txt.mm(name));
        if (help != null) {
            meta.lore(List.of(Txt.mm(help)));
        }
        if (!hd && (sel || (sp != Grid.NONE && sp != Grid.NOVA))) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        }
        meta.addItemFlags(ItemFlag.values());
        it.setItemMeta(meta);
        return it;
    }

    private static ItemStack model(String model) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta meta = it.getItemMeta();
        meta.setItemModel(new NamespacedKey("orbitbornes", model));
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack icon(String model, Material plain, String name, String... lore) {
        ItemStack it = hd ? model(model) : new ItemStack(plain);
        ItemMeta meta = it.getItemMeta();
        if (name == null) {
            meta.setHideTooltip(true);
        } else {
            meta.displayName(Txt.mm(name));
            List<Component> lines = new ArrayList<>();
            for (String l : lore) {
                lines.add(Txt.mm(l));
            }
            meta.lore(lines);
        }
        meta.addItemFlags(ItemFlag.values());
        it.setItemMeta(meta);
        return it;
    }

    private void put(int slot, ItemStack it, String key, Runnable action) {
        actions[slot] = action;
        if (key != null && Objects.equals(keys[slot], key)) {
            return;
        }
        keys[slot] = key;
        items[slot] = it;
        if (screen.inv != null) {
            screen.inv.setItem(slot, it);
        }
    }

    private void updateTitle() {
        int[] l = LEVELS[level];
        title = header("CRISTAUX D'ORBIT · NIVEAU " + (level + 1), String.valueOf(level + 1), Txt.n(score), Txt.n(l[2]),
                String.valueOf(Math.max(0, movesLeft)), starText(starsFor(score)));
        open(false);
    }

    private Component header(String top, String niv, String sc, String goal, String moves, String st) {
        if (!hd) {
            return Txt.mm("<#45168A>" + top + (mode == Mode.PLAY ? " · " + sc + "/" + goal + " · " + moves + " coups" : ""));
        }
        int x = 153;
        return Hud.title().background(Hud.BG_CRISTAUX)
                .text(0, 8, Hud.Align.LEFT, top, 0xE0B864)
                .text(1, x, Hud.Align.CENTER, niv, 0xFFFFFF)
                .text(2, x, Hud.Align.CENTER, sc, 0xFFFFFF)
                .text(3, x, Hud.Align.CENTER, goal, 0x62CFF4)
                .text(4, x, Hud.Align.CENTER, moves, movesLeft <= 3 && mode == Mode.PLAY ? 0xFF6A6A : 0xFFFFFF)
                .text(5, x, Hud.Align.CENTER, st, 0xFFE08A)
                .build();
    }

    /** Ouvre l'écran (force) ou met seulement son titre à jour s'il est ouvert. */
    private void open(boolean force) {
        if (!player.isOnline() || isEnded()) {
            return;
        }
        boolean shown = screen.inv != null && player.getOpenInventory().getTopInventory().getHolder() == screen;
        if (!shown && !force) {
            return;
        }
        if (shown && Objects.equals(shownTitle, title)) {
            return;
        }
        if (shown && Retitle.send(player, title)) {
            shownTitle = title;
            return;
        }
        Inventory next = Bukkit.createInventory(screen, 54, title);
        next.setContents(items.clone());
        screen.inv = next;
        shownTitle = title;
        reopening = true;
        player.openInventory(next);
        reopening = false;
    }

    // ------------------------------------------------------------------ fin

    @Override
    protected double score() {
        return totalStars();
    }

    @Override
    protected int ocWanted() {
        return 0;
    }

    @Override
    protected String summary() {
        return "";
    }

    @Override
    protected boolean played() {
        return false; // chaque niveau est enregistré à sa fin
    }

    /** Clics et fermeture de l'écran. */
    public static final class Clicks implements Listener {

        private final OrbitBornes plugin;

        public Clicks(OrbitBornes plugin) {
            this.plugin = plugin;
        }

        @EventHandler
        public void onClick(InventoryClickEvent e) {
            if (!(e.getInventory().getHolder() instanceof Screen s)) {
                return;
            }
            e.setCancelled(true);
            if (e.getClickedInventory() != s.inv || e.getRawSlot() < 0 || e.getRawSlot() >= 54) {
                return;
            }
            int slot = e.getRawSlot();
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!s.game().isEnded()) {
                    s.game().click(slot);
                }
            });
        }

        @EventHandler
        public void onDrag(InventoryDragEvent e) {
            if (e.getInventory().getHolder() instanceof Screen) {
                e.setCancelled(true);
            }
        }

        @EventHandler
        public void onClose(InventoryCloseEvent e) {
            if (e.getInventory().getHolder() instanceof Screen s) {
                s.game().closed();
            }
        }
    }
}
