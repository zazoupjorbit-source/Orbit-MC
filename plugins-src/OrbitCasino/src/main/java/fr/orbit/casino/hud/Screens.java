package fr.orbit.casino.hud;

import fr.orbit.casino.OrbitCasino;
import fr.orbit.casino.Txt;
import fr.orbit.casino.game.Cards;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Écran de table HD : un coffre de 6 rangées dont le titre (police du pack) dessine le tapis et les textes, et dont les
 * cases portent les cartes, jetons et boutons (modèles du pack). Les cartes qui arrivent sont distribuées avec une
 * petite animation (la carte grandit, puis se retourne) ; le titre change sans refermer l'écran.
 */
public final class Screens implements Listener {

    private static final String[] SUIT_KEY = {"s", "h", "d", "c"};
    private static final String[] RANK_NAME = {"", "", "2", "3", "4", "5", "6", "7", "8", "9", "10", "Valet", "Dame", "Roi", "As"};
    private static final String[] SUIT_NAME = {"pique", "cœur", "carreau", "trèfle"};

    private final OrbitCasino plugin;
    private final CasinoPack pack;
    private final Map<UUID, Screen> screens = new HashMap<>();

    public Screens(OrbitCasino plugin, CasinoPack pack) {
        this.plugin = plugin;
        this.pack = pack;
    }

    public boolean available(Player p) {
        return pack.isLoaded(p);
    }

    /** L'écran du joueur est-il affiché en ce moment ? */
    public boolean isOpen(Player p) {
        Screen s = screens.get(p.getUniqueId());
        return s != null && p.getOpenInventory().getTopInventory().getHolder() == s;
    }

    /**
     * Met à jour l'écran du joueur avec ce cadre. Ouvert seulement s'il l'est déjà, ou si {@code open} (son tour,
     * nouvelle main, il vient de s'asseoir).
     */
    public void show(Player p, Frame frame, boolean open) {
        if (!available(p)) {
            return;
        }
        Screen s = screens.get(p.getUniqueId());
        boolean shown = s != null && p.getOpenInventory().getTopInventory().getHolder() == s;
        if (!shown && !open) {
            return;
        }
        if (s == null || !s.table.equals(frame.table)) {
            if (s != null) {
                s.cancelAll();
            }
            s = new Screen(p.getUniqueId(), frame.table);
            screens.put(p.getUniqueId(), s);
            shown = false;
        }
        if (!shown) {
            // ouverture : ce que le joueur a déjà vu revient tel quel, seules les nouvelles cartes sont animées
            s.cancelAll();
            s.inventory = Bukkit.createInventory(s, 54, frame.title);
            s.title = frame.title;
            for (int i = 0; i < 54; i++) {
                if (frame.keys[i] != null && frame.keys[i].equals(s.finalKeys[i])) {
                    s.set(i, frame.items[i], frame.keys[i]);
                }
            }
            s.apply(frame, p);
            s.reopening = true;
            p.openInventory(s.inventory);
            s.reopening = false;
            return;
        }
        if (!Objects.equals(s.title, frame.title)) {
            s.title = frame.title;
            if (!Retitle.send(p, frame.title)) {
                Inventory next = Bukkit.createInventory(s, 54, frame.title);
                next.setContents(s.inventory.getContents());
                s.inventory = next;
                s.reopening = true;
                p.openInventory(next);
                s.reopening = false;
            }
        }
        s.apply(frame, p);
    }

    /** Ferme l'écran (le joueur quitte la table). */
    public void close(Player p) {
        Screen s = screens.remove(p.getUniqueId());
        if (s != null) {
            s.cancelAll();
            if (p.getOpenInventory().getTopInventory().getHolder() == s) {
                s.reopening = true;
                p.closeInventory();
            }
        }
    }

    public void shutdown() {
        for (Map.Entry<UUID, Screen> e : new ArrayList<>(screens.entrySet())) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null) {
                close(p);
            }
        }
        screens.clear();
    }

    // ------------------------------------------------------------------ cadre

    /** Contenu voulu de l'écran à un instant donné. */
    public static final class Frame {
        final String table;
        Component title;
        final String[] keys = new String[54];
        final ItemStack[] items = new ItemStack[54];
        final Runnable[] actions = new Runnable[54];
        final boolean[] card = new boolean[54];
        final List<Integer> dealOrder = new ArrayList<>();

        public Frame(String table) {
            this.table = table;
        }

        public Frame title(Component title) {
            this.title = title;
            return this;
        }

        /** Carte visible. */
        public Frame card(int slot, Cards.Card c) {
            return cardKey(slot, "card_" + c.rank() + SUIT_KEY[c.suit()],
                    RANK_NAME[c.rank()] + " de " + SUIT_NAME[c.suit()]);
        }

        /** Carte face cachée. */
        public Frame back(int slot) {
            return cardKey(slot, "card_back", "Carte cachée");
        }

        /** Emplacement vide en pointillés. */
        public Frame placeholder(int slot) {
            keys[slot] = "card_slot";
            items[slot] = model("card_slot", null);
            return this;
        }

        private Frame cardKey(int slot, String key, String name) {
            keys[slot] = key;
            items[slot] = model(key, "<#E6EDF3>" + name);
            card[slot] = true;
            dealOrder.add(slot);
            return this;
        }

        /** Bouton : modèle du pack, nom, lignes d'aide ; {@code wide} occupe aussi la case de droite. */
        public Frame button(int slot, String model, boolean wide, Runnable action, String name, String... lore) {
            ItemStack it = model(model, name, lore);
            keys[slot] = model + "|" + name + "|" + String.join("|", lore);
            items[slot] = it;
            actions[slot] = action;
            if (wide && slot % 9 < 8) {
                ItemStack half = model("invisible", name, lore);
                keys[slot + 1] = "invisible|" + keys[slot];
                items[slot + 1] = half;
                actions[slot + 1] = action;
            }
            return this;
        }

        /** Objet libre (tête de joueur…). */
        public Frame item(int slot, ItemStack it, String key, Runnable action) {
            keys[slot] = key;
            items[slot] = it;
            actions[slot] = action;
            return this;
        }

        /** Sablier avec les secondes restantes. */
        public Frame timer(int slot, int seconds) {
            if (seconds <= 0) {
                return this;
            }
            int n = Math.min(99, seconds);
            ItemStack it = model("timer", "<#E0B864>" + seconds + " s");
            ItemMeta meta = it.getItemMeta();
            meta.setMaxStackSize(99);
            it.setItemMeta(meta);
            it.setAmount(n);
            keys[slot] = "timer|" + n;
            items[slot] = it;
            return this;
        }
    }

    public static ItemStack model(String model, String name, String... lore) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta meta = it.getItemMeta();
        meta.setItemModel(new NamespacedKey("orbitcasino", model));
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

    // ------------------------------------------------------------------ écran ouvert

    private final class Screen implements InventoryHolder {
        final UUID player;
        final String table;
        Inventory inventory;
        Component title;
        boolean reopening;
        final String[] keys = new String[54];
        final String[] finalKeys = new String[54];
        final Runnable[] actions = new Runnable[54];
        final BukkitTask[] anims = new BukkitTask[54];

        Screen(UUID player, String table) {
            this.player = player;
            this.table = table;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        void cancelAll() {
            for (int i = 0; i < 54; i++) {
                cancel(i);
            }
        }

        void cancel(int slot) {
            if (anims[slot] != null) {
                anims[slot].cancel();
                anims[slot] = null;
            }
        }

        void apply(Frame f, Player p) {
            int delay = 0;
            // cartes dans l'ordre de distribution, puis le reste
            List<Integer> order = new ArrayList<>(f.dealOrder);
            for (int i = 0; i < 54; i++) {
                if (!f.card[i]) {
                    order.add(i);
                }
            }
            for (int slot : order) {
                actions[slot] = f.actions[slot];
                String want = f.keys[slot];
                if (Objects.equals(finalKeys[slot], want)) {
                    continue;
                }
                String before = finalKeys[slot];
                finalKeys[slot] = want;
                cancel(slot);
                List<String> steps = f.card[slot] ? animation(before, want) : List.of();
                if (steps.isEmpty()) {
                    set(slot, f.items[slot], want);
                    continue;
                }
                ItemStack target = f.items[slot];
                int start = delay;
                delay += 4;
                anims[slot] = new Anim(slot, steps, target, want, p).start(start);
            }
        }

        void set(int slot, ItemStack it, String key) {
            keys[slot] = key;
            inventory.setItem(slot, it);
        }

        /** Étapes intermédiaires d'une carte (sans l'image finale). */
        List<String> animation(String before, String want) {
            boolean wasEmpty = before == null || before.equals("card_slot");
            boolean wasBack = "card_back".equals(before);
            boolean face = want.startsWith("card_") && !want.equals("card_back");
            List<String> steps = new ArrayList<>();
            if (wasEmpty) {
                steps.add("card_back_g1");
                steps.add("card_back_g2");
                if (face) {
                    steps.add("card_back");
                }
            }
            if (face && (wasEmpty || wasBack)) {
                steps.add("card_back_half");
                steps.add("card_edge");
                steps.add("card_face_half");
            }
            return steps;
        }

        final class Anim implements Runnable {
            final int slot;
            final List<String> steps;
            final ItemStack target;
            final String key;
            final Player p;
            int i;
            BukkitTask task;

            Anim(int slot, List<String> steps, ItemStack target, String key, Player p) {
                this.slot = slot;
                this.steps = steps;
                this.target = target;
                this.key = key;
                this.p = p;
            }

            BukkitTask start(int delay) {
                if (delay == 0) {
                    // première étape tout de suite : pas de case vide le temps d'un tick
                    run();
                    if (i > steps.size()) {
                        return null;
                    }
                }
                task = Bukkit.getScheduler().runTaskTimer(plugin, this, Math.max(1, delay), 2L);
                return task;
            }

            @Override
            public void run() {
                if (i == 0 && p.isOnline()) {
                    p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.6f, 1.1f + Cards.RANDOM.nextFloat() * 0.3f);
                }
                if (i < steps.size()) {
                    if ("card_edge".equals(steps.get(i)) && p.isOnline()) {
                        p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.35f, 1.7f);
                    }
                    set(slot, model(steps.get(i), null), steps.get(i));
                } else {
                    set(slot, target, key);
                    if (task != null) {
                        task.cancel();
                    }
                    if (anims[slot] == task) {
                        anims[slot] = null;
                    }
                }
                i++;
            }
        }
    }

    // ------------------------------------------------------------------ événements

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Screen s)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() != s.inventory || event.getRawSlot() < 0 || event.getRawSlot() >= 54) {
            return;
        }
        Runnable action = s.actions[event.getRawSlot()];
        if (action != null && event.getWhoClicked() instanceof Player p) {
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.4f);
            Bukkit.getScheduler().runTask(plugin, action);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Screen) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof Screen s) || s.reopening) {
            return;
        }
        s.cancelAll();
        if (event.getPlayer() instanceof Player p && plugin.casino() != null && plugin.casino().seatedAnywhere(p.getUniqueId())) {
            Txt.bar(p, "<#9AA4B2>Écran fermé · <#E6EDF3>clic droit sur la table</#E6EDF3> ou <#E6EDF3>/table ecran</#E6EDF3> pour le rouvrir");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Screen s = screens.remove(event.getPlayer().getUniqueId());
        if (s != null) {
            s.cancelAll();
        }
    }
}
