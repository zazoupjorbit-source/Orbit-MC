package fr.orbit.extraction.loot;

import fr.orbit.extraction.Keys;
import fr.orbit.extraction.OrbitExtraction;
import fr.orbit.extraction.hud.Cast;
import fr.orbit.extraction.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Fouille d'un conteneur (caisse de butin, coffre ou tonneau de la carte) : son contenu est caché derrière des cases
 * « Fouille… » qui se révèlent une à une tant qu'un joueur garde l'inventaire ouvert. Chaque objet a sa durée
 * (une arme se trouve plus lentement qu'une boîte de munitions). La fouille avance pour tout le monde et se met en
 * pause quand plus personne ne regarde ; une case encore cachée ne peut pas être prise.
 */
public final class Search implements Listener {

    private static final class Job {
        final Inventory inv;
        /** Cases encore cachées, dans l'ordre de révélation ; objet null = case vide (rien trouvé). */
        final List<Integer> order = new ArrayList<>();
        final Map<Integer, ItemStack> hidden = new java.util.HashMap<>();
        final Map<Integer, Double> seconds = new java.util.HashMap<>();
        final double total;
        double done;
        /** Avancement de la case en cours (s). */
        double current;

        Job(Inventory inv, double total) {
            this.inv = inv;
            this.total = total;
        }
    }

    private static final int PERIOD = 2;

    private final OrbitExtraction plugin;
    private final Map<Inventory, Job> jobs = new IdentityHashMap<>();
    private boolean enabled = true;
    private double speed = 1;
    private final Map<String, Double> durations = new java.util.HashMap<>();
    private double defaultSeconds = 0.8;

    public Search(OrbitExtraction plugin) {
        this.plugin = plugin;
    }

    public void load(ConfigurationSection c) {
        enabled = c == null || c.getBoolean("active", true);
        speed = c == null ? 1 : Math.max(0.1, c.getDouble("vitesse", 1));
        durations.clear();
        durations.put("arme", 2.2);
        durations.put("armure", 1.6);
        durations.put("grenade", 1.3);
        durations.put("soin", 1.1);
        durations.put("vehicule", 1.2);
        durations.put("munition", 0.8);
        defaultSeconds = 0.7;
        ConfigurationSection d = c == null ? null : c.getConfigurationSection("secondes");
        if (d != null) {
            for (String k : d.getKeys(false)) {
                if (k.equalsIgnoreCase("defaut")) {
                    defaultSeconds = Math.max(0.05, d.getDouble(k));
                } else {
                    durations.put(k.toLowerCase(Locale.ROOT), Math.max(0.05, d.getDouble(k)));
                }
            }
        }
    }

    public boolean enabled() {
        return enabled;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, PERIOD, PERIOD);
    }

    /** Durée de fouille d'un objet tiré d'une spec (« arme:m4 », « munition:9mm:10-20 »...). */
    public double seconds(String spec) {
        int i = spec.indexOf(':');
        String kind = (i < 0 ? spec : spec.substring(0, i)).toLowerCase(Locale.ROOT);
        return durations.getOrDefault(kind, defaultSeconds) / speed;
    }

    /**
     * Remplit l'inventaire de {@code rolls} tirages dans {@code loot} (cases au hasard) et renvoie la durée de
     * fouille de chaque case remplie.
     */
    public Map<Integer, Double> fill(Inventory inv, List<Crates.Entry> loot, int rolls, java.util.Random random) {
        Map<Integer, Double> out = new java.util.HashMap<>();
        int total = 0;
        for (Crates.Entry e : loot) {
            total += e.weight();
        }
        if (total <= 0) {
            return out;
        }
        List<Integer> free = new ArrayList<>();
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) {
                free.add(i);
            }
        }
        for (int i = 0; i < rolls; i++) {
            int r = random.nextInt(total);
            Crates.Entry pick = loot.get(0);
            for (Crates.Entry e : loot) {
                r -= e.weight();
                if (r < 0) {
                    pick = e;
                    break;
                }
            }
            for (ItemStack it : plugin.specs().create(pick.spec(), true)) {
                if (free.isEmpty()) {
                    return out;
                }
                int slot = free.remove(random.nextInt(free.size()));
                inv.setItem(slot, it);
                out.put(slot, seconds(pick.spec()));
            }
        }
        return out;
    }

    /**
     * Cache le contenu de l'inventaire : chaque case remplie devient « Fouille… ». {@code seconds} donne la durée
     * par case (absente : durée par défaut) ; {@code empty} ajoute ce nombre de cases vides à fouiller (rien trouvé).
     */
    public void hide(Inventory inv, Map<Integer, Double> seconds, int empty) {
        if (!enabled) {
            return;
        }
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (it != null && !it.getType().isAir()) {
                slots.add(i);
            }
        }
        List<Integer> free = new ArrayList<>();
        for (int i = 0; i < inv.getSize(); i++) {
            if (!slots.contains(i)) {
                free.add(i);
            }
        }
        Collections.shuffle(free);
        for (int i = 0; i < empty && !free.isEmpty(); i++) {
            slots.add(free.remove(0));
        }
        if (slots.isEmpty()) {
            return;
        }
        // Ordre de lecture (haut-gauche vers bas-droite), comme un joueur qui fouille.
        Collections.sort(slots);
        double total = 0;
        for (int s : slots) {
            total += seconds.getOrDefault(s, defaultSeconds / speed);
        }
        Job job = new Job(inv, total);
        for (int s : slots) {
            job.order.add(s);
            job.hidden.put(s, inv.getItem(s));
            job.seconds.put(s, seconds.getOrDefault(s, defaultSeconds / speed));
            inv.setItem(s, placeholder(false));
        }
        markCurrent(job);
        jobs.put(inv, job);
    }

    /** Vrai si l'inventaire a encore des cases à fouiller. */
    public boolean searching(Inventory inv) {
        return jobs.containsKey(inv);
    }

    /** Fin brutale (conteneur retiré) : le contenu caché est perdu avec l'inventaire. */
    public void forget(Inventory inv) {
        jobs.remove(inv);
    }

    /** Barre de la console : fouille de l'inventaire ouvert par le joueur. */
    public Cast progress(Player p) {
        Job job = jobs.get(p.getOpenInventory().getTopInventory());
        if (job == null) {
            return null;
        }
        return new Cast("<#F5C542>Fouille</#F5C542> <#8A919B>· " + job.order.size() + " case" + (job.order.size() > 1 ? "s" : "") + " à fouiller",
                job.total <= 0 ? 1 : (job.done + job.current) / job.total, Math.max(0, job.total - job.done - job.current), "#F5C542");
    }

    private void tick() {
        Iterator<Job> it = jobs.values().iterator();
        while (it.hasNext()) {
            Job job = it.next();
            List<HumanEntity> viewers = job.inv.getViewers();
            if (viewers.isEmpty()) {
                continue;   // personne ne fouille : en pause
            }
            job.current += PERIOD / 20.0;
            int slot = job.order.get(0);
            double need = job.seconds.get(slot);
            if (job.current < need) {
                continue;
            }
            job.order.remove(0);
            job.done += need;
            job.current = 0;
            ItemStack found = job.hidden.remove(slot);
            job.inv.setItem(slot, found);
            for (HumanEntity v : viewers) {
                if (v instanceof Player p) {
                    if (found == null) {
                        p.playSound(p.getLocation(), Sound.ITEM_BUNDLE_REMOVE_ONE, 0.5f, 0.7f);
                    } else {
                        p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.6f, 1.2f);
                    }
                }
            }
            if (job.order.isEmpty()) {
                it.remove();
                boolean any = false;
                for (ItemStack s : job.inv.getContents()) {
                    if (s != null && !s.getType().isAir()) {
                        any = true;
                        break;
                    }
                }
                for (HumanEntity v : viewers) {
                    if (v instanceof Player p) {
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, any ? 1.6f : 0.8f);
                        if (!any) {
                            Text.bar(p, "<#8A919B>Rien d'utile ici.");
                        }
                    }
                }
            } else {
                markCurrent(job);
            }
        }
    }

    /** La case en cours de fouille est mise en évidence. */
    private static void markCurrent(Job job) {
        job.inv.setItem(job.order.get(0), placeholder(true));
    }

    private static ItemStack placeholder(boolean current) {
        ItemStack it = new ItemStack(current ? Material.YELLOW_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = it.getItemMeta();
        m.displayName(Text.mm(current ? "<#F5C542>Fouille en cours…" : "<#8A919B>Pas encore fouillé"));
        m.getPersistentDataContainer().set(Keys.SEARCH, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    private static boolean isPlaceholder(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(Keys.SEARCH);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent e) {
        if (jobs.isEmpty()) {
            return;
        }
        Inventory top = e.getView().getTopInventory();
        if (!jobs.containsKey(top)) {
            return;
        }
        if (isPlaceholder(e.getCurrentItem()) || isPlaceholder(e.getCursor())) {
            e.setCancelled(true);
            return;
        }
        // Touche de la barre (1-9) ou double-clic qui ramasserait une case cachée : bloqués dans le conteneur.
        if (e.getClickedInventory() == top && (e.getClick().isKeyboardClick() || e.getClick() == org.bukkit.event.inventory.ClickType.DOUBLE_CLICK)) {
            if (isPlaceholder(top.getItem(e.getSlot()))) {
                e.setCancelled(true);
            }
        }
        if (e.getClick() == org.bukkit.event.inventory.ClickType.DOUBLE_CLICK) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!jobs.containsKey(top)) {
            return;
        }
        for (int raw : e.getRawSlots()) {
            if (raw < top.getSize() && isPlaceholder(top.getItem(raw))) {
                e.setCancelled(true);
                return;
            }
        }
    }
}
