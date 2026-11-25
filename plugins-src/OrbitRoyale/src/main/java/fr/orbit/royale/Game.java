package fr.orbit.royale;

import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gestion des parties du Siège Orbital : file d'attente (deux joueurs = duel, sinon un bot après quelques
 * secondes), arènes libres, événements pendant la partie, fin et récompenses, menu du deck.
 */
final class Game implements Listener {

    private final OrbitRoyale plugin;
    private final World world;
    private final NamespacedKey key;
    private final Arena[] arenas;
    private final Match[] slots;
    private final Map<UUID, Match> byPlayer = new HashMap<>();
    private final Map<UUID, Long> queue = new LinkedHashMap<>();
    private final Map<UUID, Long> lastClick = new HashMap<>();
    private final Map<UUID, Integer> quitConfirm = new HashMap<>();
    private int ticks;

    Game(OrbitRoyale plugin, World world) {
        this.plugin = plugin;
        this.world = world;
        this.key = new NamespacedKey(plugin, "siege");
        int n = Math.max(1, plugin.getConfig().getInt("arenes", 8));
        this.arenas = new Arena[n];
        this.slots = new Match[n];
        for (int i = 0; i < n; i++) {
            arenas[i] = new Arena(world, i);
        }
    }

    OrbitRoyale plugin() {
        return plugin;
    }

    RoyalePack pack() {
        return plugin.pack();
    }

    void tag(Entity e) {
        e.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
    }

    boolean isOurs(Entity e) {
        return e.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    void start() {
        for (Entity e : world.getEntities()) {
            if (isOurs(e)) {
                e.remove();
            }
        }
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    void stopAll() {
        for (Match m : new ArrayList<>(byPlayer.values())) {
            if (m.state != Match.State.DONE) {
                m.state = Match.State.DONE;
                release(m, true);
            }
        }
        queue.clear();
    }

    boolean playing(Player p) {
        return byPlayer.containsKey(p.getUniqueId());
    }

    Arena arena(int slot) {
        return arenas[Math.max(0, Math.min(arenas.length - 1, slot))];
    }

    private void tick() {
        ticks++;
        for (Match m : slots) {
            if (m != null && m.state != Match.State.DONE) {
                try {
                    m.tick();
                } catch (RuntimeException e) {
                    plugin.getLogger().warning("Siège Orbital, arène " + m.arena.slot + " : " + e);
                    e.printStackTrace();
                    m.end(-1, "erreur");
                }
            }
        }
        if (ticks % 20 == 0) {
            checkQueue();
        }
    }

    // ------------------------------------------------------------------ file d'attente

    void join(Player p, boolean botNow) {
        if (playing(p)) {
            return;
        }
        if (p.isInsideVehicle()) {
            Txt.send(p, "<#9AA4B2>Descends d'abord de ton siège.");
            return;
        }
        plugin.store().load(p, pr -> {
            if (!p.isOnline() || playing(p)) {
                return;
            }
            if (botNow) {
                queue.remove(p.getUniqueId());
                startBot(p, pr);
                return;
            }
            // un autre commandant attend : duel !
            for (UUID other : new ArrayList<>(queue.keySet())) {
                if (other.equals(p.getUniqueId())) {
                    continue;
                }
                Player o = Bukkit.getPlayer(other);
                if (o == null || !o.isOnline() || playing(o)) {
                    queue.remove(other);
                    continue;
                }
                queue.remove(other);
                queue.remove(p.getUniqueId());
                plugin.store().load(o, po -> startDuel(o, po, p, pr));
                return;
            }
            if (queue.containsKey(p.getUniqueId())) {
                Txt.send(p, "<#9AA4B2>Tu es déjà dans la file.");
                return;
            }
            queue.put(p.getUniqueId(), System.currentTimeMillis());
            int wait = plugin.getConfig().getInt("partie.attente-bot", 8);
            Txt.send(p, "<#E6EDF3>Recherche d'un adversaire…</#E6EDF3> <#9AA4B2>Si personne ne vient d'ici " + wait
                    + " s, un bot du Néant prend la place.</#9AA4B2> ");
            p.sendMessage(Txt.mm("  ").append(Txt.button("Bot tout de suite", "#C79BFF", "/siege bot", "Lancer contre un bot sans attendre"))
                    .append(Txt.mm(" ")).append(Txt.button("Quitter la file", "#9AA4B2", "/siege quitter", "Ne plus attendre")));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.7f, 1.2f);
        });
    }

    private void checkQueue() {
        long wait = plugin.getConfig().getInt("partie.attente-bot", 8) * 1000L;
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Long> e : new ArrayList<>(queue.entrySet())) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null || !p.isOnline() || playing(p)) {
                queue.remove(e.getKey());
                continue;
            }
            if (now - e.getValue() >= wait) {
                queue.remove(e.getKey());
                Profile pr = plugin.store().cached(p.getUniqueId());
                if (pr != null) {
                    startBot(p, pr);
                }
            } else {
                Txt.bar(p, "<#9AA4B2>Recherche d'un adversaire… <#E6EDF3>" + (wait - (now - e.getValue())) / 1000 + " s");
            }
        }
    }

    boolean leaveQueue(Player p) {
        return queue.remove(p.getUniqueId()) != null;
    }

    private int freeSlot() {
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == null) {
                return i;
            }
        }
        return -1;
    }

    private void startBot(Player p, Profile pr) {
        int slot = freeSlot();
        if (slot < 0) {
            Txt.send(p, "<#E0484A>Toutes les arènes sont prises, réessaie dans un instant.");
            return;
        }
        int level = Bot.levelFor(pr.trophies);
        Side a = new Side(0, p, pr, p.getName(), pr.deck);
        Side b = new Side(1, null, null, Bot.name(level), Bot.deck(pr.trophies));
        Match m = new Match(this, arenas[slot], a, b);
        b.bot = new Bot(m, b, level);
        launch(slot, m);
    }

    /** Démo pour l'admin : deux bots s'affrontent (vérification de la simulation sans joueur). */
    String startDemo(int trophies) {
        int slot = freeSlot();
        if (slot < 0) {
            return "aucune arène libre";
        }
        int level = Bot.levelFor(trophies);
        Side a = new Side(0, null, null, Bot.name(level), Bot.deck(trophies));
        Side b = new Side(1, null, null, Bot.name(level), Bot.deck(trophies));
        Match m = new Match(this, arenas[slot], a, b);
        a.bot = new Bot(m, a, level);
        b.bot = new Bot(m, b, level);
        launch(slot, m);
        return "démo lancée sur l'arène " + slot + " (bots niv. " + (level + 1) + ")";
    }

    private void startDuel(Player p1, Profile pr1, Player p2, Profile pr2) {
        if (!p1.isOnline() || !p2.isOnline() || playing(p1) || playing(p2)) {
            if (p2.isOnline() && !playing(p2)) {
                join(p2, false);
            }
            return;
        }
        int slot = freeSlot();
        if (slot < 0) {
            Txt.send(p1, "<#E0484A>Toutes les arènes sont prises, réessaie dans un instant.");
            Txt.send(p2, "<#E0484A>Toutes les arènes sont prises, réessaie dans un instant.");
            return;
        }
        Side a = new Side(0, p1, pr1, p1.getName(), pr1.deck);
        Side b = new Side(1, p2, pr2, p2.getName(), pr2.deck);
        launch(slot, new Match(this, arenas[slot], a, b));
    }

    private void launch(int slot, Match m) {
        slots[slot] = m;
        for (Side s : m.sides) {
            if (s.human()) {
                byPlayer.put(s.player.getUniqueId(), m);
            }
        }
        m.start();
    }

    // ------------------------------------------------------------------ fin

    /** Appelée par Match.end : récompenses, état rendu, retour devant le PNJ. */
    void finish(Match m, int winner, String reason) {
        plugin.getLogger().info("Partie arène " + m.arena.slot + " : " + m.sides[0].name.replaceAll("<[^>]+>", "") + " contre " + m.sides[1].name.replaceAll("<[^>]+>", "")
                + " -> " + (winner < 0 ? "égalité" : "camp " + winner) + " (" + reason + "), étoiles " + m.sides[0].stars + "-" + m.sides[1].stars
                + ", cartes " + m.sides[0].played + "-" + m.sides[1].played + ", " + m.tick / 20 + " s.");
        for (Side s : m.sides) {
            if (!s.human()) {
                continue;
            }
            Player p = s.player;
            int outcome = winner < 0 ? 0 : winner == s.index ? 1 : -1;
            Side o = m.other(s);
            if (p.isOnline()) {
                String head = outcome > 0 ? "<gradient:#E0B864:#FFF0C2><b>VICTOIRE</b></gradient>"
                        : outcome < 0 ? "<#FF5A5A><b>DÉFAITE</b>" : "<#C9D4E0><b>ÉGALITÉ</b>";
                p.showTitle(Title.title(Txt.mm(head), Txt.mm("<#E6EDF3>★ " + s.stars + " – " + o.stars + " <#9AA4B2>· " + reason),
                        Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(2500), Duration.ofMillis(500))));
                p.playSound(p.getLocation(), outcome > 0 ? Sound.UI_TOAST_CHALLENGE_COMPLETE : outcome < 0 ? Sound.ENTITY_WITHER_DEATH : Sound.BLOCK_NOTE_BLOCK_BELL,
                        outcome < 0 ? 0.3f : 0.8f, 1f);
            }
            if (s.profile != null) {
                Profile pr = s.profile;
                int stars = s.stars;
                plugin.store().record(pr, outcome, stars, res -> announce(p, pr, outcome, stars, o, res));
            }
        }
        // on laisse 2 s pour voir la tour tomber, puis retour
        Bukkit.getScheduler().runTaskLater(plugin, () -> release(m, false), 40L);
    }

    private void announce(Player p, Profile pr, int outcome, int stars, Side o, Store.Result r) {
        if (!p.isOnline()) {
            return;
        }
        int diff = r.trophies() - r.trophiesBefore();
        Txt.send(p, (outcome > 0 ? "<#E0B864><b>Victoire</b></#E0B864>" : outcome < 0 ? "<#FF5A5A><b>Défaite</b></#FF5A5A>" : "<b>Égalité</b>")
                + " contre " + o.name + " <#E6EDF3>· ★ " + stars + "</#E6EDF3> · trophées <b>" + Txt.n(r.trophies()) + "</b> "
                + (diff > 0 ? "<#7CFF8A>(+" + diff + ")" : diff < 0 ? "<#FF8A8A>(" + diff + ")" : "<#9AA4B2>(=)")
                + " <#9AA4B2>· " + (r.oc() > 0 ? Txt.oc(r.oc()) : "0 OC")
                + (r.oc() < r.ocWanted() && pr.saved ? " (plafond de " + Txt.n(r.cap()) + " OC par jour)" : "")
                + (!pr.saved ? " <#E0484A>(base injoignable : rien d'enregistré)" : ""));
        for (Card c : Card.values()) {
            if (c.trophies > r.recordBefore() && c.trophies <= r.record()) {
                Txt.send(p, "<#7CFF8A><b>Nouvelle carte débloquée :</b></#7CFF8A> <" + c.color() + "><b>" + c.nom + "</b></" + c.color()
                        + "> <#9AA4B2>(" + c.typeLabel() + ", ⚡" + c.cost + ") — ajoute-la avec /siege deck.");
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.3f);
            }
        }
        Card next = null;
        for (Card c : Card.values()) {
            if (c.trophies > r.record() && (next == null || c.trophies < next.trophies)) {
                next = c;
            }
        }
        if (next != null) {
            p.sendMessage(Txt.mm("  <#5A6270>Prochaine carte : <#9AA4B2>" + next.nom + "</#9AA4B2> à " + next.trophies + " trophées de record."));
        }
        p.sendMessage(Txt.endButtons());
    }

    /** Libère l'arène, rend l'état des joueurs et les ramène devant le PNJ (direct = arrêt du serveur). */
    private void release(Match m, boolean direct) {
        m.cleanup();
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == m) {
                slots[i] = null;
            }
        }
        for (Side s : m.sides) {
            if (!s.human()) {
                continue;
            }
            Player p = s.player;
            byPlayer.remove(p.getUniqueId(), m);
            quitConfirm.remove(p.getUniqueId());
            restore(p, s);
        }
    }

    private void restore(Player p, Side s) {
        if (s.saved != null) {
            s.saved.restore(p);
            java.io.File f = Saved.file(plugin.getDataFolder(), p);
            if (f.isFile()) {
                f.delete();
            }
            s.saved = null;
        }
        Location back = plugin.returnPoint();
        if (back != null && !s.noReturn) {
            p.teleport(back);
        }
    }

    /** Abandon (commande, objet de sortie, déconnexion, téléportation ailleurs) : défaite pour celui qui part. */
    void forfeit(Player p, String reason) {
        Match m = byPlayer.get(p.getUniqueId());
        if (m == null || m.state == Match.State.DONE) {
            return;
        }
        Side s = m.sideOf(p);
        if (s == null) {
            return;
        }
        s.noReturn = reason.equals("parti de l'arène");
        if (m.state == Match.State.COUNTDOWN) {
            // rien n'a commencé : partie annulée sans résultat
            m.state = Match.State.DONE;
            Side o = m.other(s);
            if (o.human() && o.player.isOnline()) {
                Txt.send(o.player, "<#9AA4B2>" + p.getName() + " est parti avant le début : partie annulée.");
            }
            release(m, true);
            return;
        }
        m.end(m.other(s).index, reason);
    }

    // ------------------------------------------------------------------ objets

    ItemStack cardItem(Card c, boolean pack, boolean affordable, int key) {
        ItemStack it = new ItemStack(pack ? Material.PAPER : c.fallback, Math.max(1, c.cost));
        ItemMeta m = it.getItemMeta();
        if (pack) {
            m.setItemModel(new NamespacedKey("orbitroyale", "card_" + c.id + (affordable ? "" : "_off")));
        }
        m.displayName(Txt.mm((affordable ? "<" + c.color() + "><b>" : "<#5A6270><b>") + c.nom + "</b> <#C79BFF>⚡" + c.cost
                + " <#5A6270>[" + key + "]"));
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(Txt.mm("<#9AA4B2>" + c.typeLabel()));
        lore.add(Txt.mm("<#E6EDF3>" + c.description));
        lore.add(Txt.mm(stats(c)));
        lore.add(Txt.mm(affordable ? "<#7CFF8A>Clic pour poser à l'endroit visé" : "<#C79BFF>Plasma insuffisant"));
        m.lore(lore);
        m.addItemFlags(ItemFlag.values());
        m.setMaxStackSize(99);
        it.setItemMeta(m);
        it.setAmount(Math.max(1, c.cost));
        return it;
    }

    static String stats(Card c) {
        if (c.kind == Card.Kind.SPELL) {
            return "<#5A6270>Dégâts " + (int) c.dmg + " · rayon " + c.radius + (c.freeze > 0 ? " · gel " + c.freeze / 20.0 + " s" : "");
        }
        StringBuilder sb = new StringBuilder("<#5A6270>Vie " + (int) c.hp);
        if (c.dmg > 0) {
            sb.append(" · dégâts ").append((int) c.dmg).append(c.count > 1 ? " ×" + c.count : "")
                    .append(" · portée ").append(c.range >= 2 ? String.valueOf(c.range) : "mêlée");
        }
        if (c.air) {
            sb.append(" · anti-volants");
        }
        if (c.towersOnly) {
            sb.append(" · vise les tours");
        }
        return sb.toString();
    }

    ItemStack nextItem(Card c, boolean pack) {
        ItemStack it = new ItemStack(pack ? Material.PAPER : Material.GRAY_DYE);
        ItemMeta m = it.getItemMeta();
        if (pack) {
            m.setItemModel(new NamespacedKey("orbitroyale", "card_" + c.id + "_off"));
        }
        m.displayName(Txt.mm("<#9AA4B2>Carte suivante : <" + c.color() + ">" + c.nom + " <#C79BFF>⚡" + c.cost));
        m.addItemFlags(ItemFlag.values());
        it.setItemMeta(m);
        return it;
    }

    ItemStack quitItem() {
        ItemStack it = new ItemStack(Material.BARRIER);
        ItemMeta m = it.getItemMeta();
        m.displayName(Txt.mm("<#FF5A5A><b>Abandonner</b> <#9AA4B2>(clic deux fois)"));
        m.lore(List.of(Txt.mm("<#9AA4B2>Compte comme une défaite.")));
        it.setItemMeta(m);
        return it;
    }

    // ------------------------------------------------------------------ menu du deck

    static final class DeckHolder implements InventoryHolder {
        final Profile profile;
        Inventory inv;

        DeckHolder(Profile profile) {
            this.profile = profile;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    void openDeck(Player p) {
        if (playing(p)) {
            return;
        }
        plugin.store().load(p, pr -> {
            if (!p.isOnline()) {
                return;
            }
            DeckHolder h = new DeckHolder(pr);
            h.inv = Bukkit.createInventory(h, 36, Txt.mm("<#3A7FFF><b>Deck du Siège</b></#3A7FFF> <#5A6270>· " + pr.deck.size() + "/" + pr.deckSize()));
            drawDeck(h, p);
            p.openInventory(h.inv);
        });
    }

    private void drawDeck(DeckHolder h, Player p) {
        Inventory inv = h.inv;
        inv.clear();
        Profile pr = h.profile;
        boolean pack = pack().isLoaded(p);
        for (int i = 0; i < 8; i++) {
            if (i < pr.deck.size()) {
                ItemStack it = cardItem(pr.deck.get(i), pack, true, i + 1);
                ItemMeta m = it.getItemMeta();
                List<net.kyori.adventure.text.Component> lore = new ArrayList<>(m.lore());
                lore.set(lore.size() - 1, Txt.mm("<#FF8A8A>Clic : retirer du deck"));
                m.lore(lore);
                it.setItemMeta(m);
                inv.setItem(i, it);
            } else {
                inv.setItem(i, simple(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "<#5A6270>Case libre", "<#9AA4B2>Clique une carte en dessous"));
            }
        }
        inv.setItem(8, simple(Material.BOOK, "<#E0B864><b>" + Txt.n(pr.trophies) + " trophées</b>",
                "<#9AA4B2>Record " + pr.record + " · " + pr.wins + " V / " + pr.losses + " D / " + pr.draws + " N",
                "<#9AA4B2>Les cartes se débloquent au record de trophées.", "<#9AA4B2>Jamais en payant."));
        for (int i = 9; i < 18; i++) {
            inv.setItem(i, simple(Material.BLACK_STAINED_GLASS_PANE, " "));
        }
        int slot = 18;
        for (Card c : Card.values()) {
            if (c.trophies <= pr.record) {
                ItemStack it = cardItem(c, pack, !pr.deck.contains(c), 0);
                ItemMeta m = it.getItemMeta();
                List<net.kyori.adventure.text.Component> lore = new ArrayList<>(m.lore());
                lore.set(lore.size() - 1, Txt.mm(pr.deck.contains(c) ? "<#9AA4B2>Déjà dans le deck" : "<#7CFF8A>Clic : ajouter au deck"));
                m.lore(lore);
                m.displayName(Txt.mm("<" + c.color() + "><b>" + c.nom + "</b> <#C79BFF>⚡" + c.cost));
                it.setItemMeta(m);
                inv.setItem(slot, it);
            } else {
                inv.setItem(slot, simple(Material.GRAY_STAINED_GLASS_PANE, "<#5A6270>🔒 " + c.nom,
                        "<#9AA4B2>" + c.typeLabel() + " · ⚡" + c.cost, "<#E0B864>Débloquée à " + c.trophies + " trophées de record"));
            }
            slot++;
        }
    }

    private static ItemStack simple(Material mat, String name, String... lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.displayName(Txt.mm(name));
        List<net.kyori.adventure.text.Component> l = new ArrayList<>();
        for (String s : lore) {
            l.add(Txt.mm(s));
        }
        m.lore(l);
        m.addItemFlags(ItemFlag.values());
        it.setItemMeta(m);
        return it;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeckClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof DeckHolder h)) {
            return;
        }
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getInventory()) {
            return;
        }
        int raw = e.getRawSlot();
        Profile pr = h.profile;
        if (raw < 8) {
            if (raw < pr.deck.size()) {
                if (pr.deck.size() <= 4) {
                    Txt.bar(p, "<#FF8A8A>Il faut au moins 4 cartes.");
                    return;
                }
                pr.deck.remove(raw);
                p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.7f, 0.8f);
            }
        } else if (raw >= 18 && raw < 18 + Card.values().length) {
            Card c = Card.values()[raw - 18];
            if (c.trophies > pr.record || pr.deck.contains(c)) {
                return;
            }
            if (pr.deck.size() >= 8) {
                Txt.bar(p, "<#FF8A8A>Deck plein : retire d'abord une carte (rangée du haut).");
                return;
            }
            pr.deck.add(c);
            p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.7f, 1.3f);
        } else {
            return;
        }
        drawDeck(h, p);
    }

    @EventHandler
    public void onDeckDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof DeckHolder) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeckClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof DeckHolder h)) {
            return;
        }
        Profile pr = h.profile;
        int before = pr.deck.size();
        pr.fixDeck();
        plugin.store().saveDeck(pr);
        if (e.getPlayer() instanceof Player p) {
            Txt.send(p, "Deck enregistré : " + String.join(", ", pr.deck.stream().map(c -> "<" + c.color() + ">" + c.nom + "</" + c.color() + ">").toList())
                    + (pr.deck.size() > before ? " <#9AA4B2>(complété automatiquement)" : ""));
        }
    }

    // ------------------------------------------------------------------ pendant la partie

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        Match m = byPlayer.get(p.getUniqueId());
        if (m == null) {
            return;
        }
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND || e.getAction() == Action.PHYSICAL) {
            return;
        }
        Side s = m.sideOf(p);
        if (s == null) {
            return;
        }
        if (m.tick - s.lastClick < 4 && s.lastClick > 0) {
            return;
        }
        s.lastClick = m.tick;
        int slot = p.getInventory().getHeldItemSlot();
        if (slot == 8) {
            Integer at = quitConfirm.get(p.getUniqueId());
            if (at != null && m.tick - at < 60) {
                forfeit(p, "abandon");
            } else {
                quitConfirm.put(p.getUniqueId(), m.tick);
                Txt.bar(p, "<#FF5A5A>Clique encore pour abandonner (compte comme une défaite).");
            }
            return;
        }
        if (slot > 3) {
            return;
        }
        double[] pt = m.aimPoint(p);
        if (pt == null) {
            Txt.bar(p, "<#FF8A8A>Vise le sol de l'arène.");
            return;
        }
        String err = m.play(s, slot, pt[0], pt[1]);
        if (err != null) {
            Txt.bar(p, "<#FF8A8A>" + err);
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.7f);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        if (playing(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onHeld(PlayerItemHeldEvent e) {
        Match m = byPlayer.get(e.getPlayer().getUniqueId());
        if (m == null) {
            return;
        }
        Side s = m.sideOf(e.getPlayer());
        int slot = e.getNewSlot();
        if (s != null && slot <= 3 && s.hand[slot] != null) {
            Card c = s.hand[slot];
            Txt.bar(e.getPlayer(), "<" + c.color() + "><b>" + c.nom + "</b></" + c.color() + "> <#C79BFF>⚡" + c.cost + "</#C79BFF> <#5A6270>·</#5A6270> <#9AA4B2>" + c.typeLabel());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrop(PlayerDropItemEvent e) {
        if (playing(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (playing(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p && playing(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && playing(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFood(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && playing(p)) {
            e.setCancelled(true);
        }
    }

    /** Tombé de la plateforme (ne devrait pas arriver, barrières tout autour) : on remonte. */
    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Match m = byPlayer.get(e.getPlayer().getUniqueId());
        if (m == null || e.getTo().getY() > Arena.Y + Arena.CAM_H - 1) {
            return;
        }
        Side s = m.sideOf(e.getPlayer());
        if (s != null) {
            e.getPlayer().teleport(m.arena.camera(s.index));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        queue.remove(p.getUniqueId());
        lastClick.remove(p.getUniqueId());
        Match m = byPlayer.get(p.getUniqueId());
        if (m != null) {
            Side s = m.sideOf(p);
            forfeit(p, "déconnexion");
            // l'état doit être rendu avant la sauvegarde du joueur : tout de suite, pas dans 2 s
            if (s != null && s.saved != null) {
                s.saved.restore(p);
                s.saved = null;
                java.io.File f = Saved.file(plugin.getDataFolder(), p);
                if (f.isFile()) {
                    f.delete();
                }
                Location back = plugin.returnPoint();
                if (back != null) {
                    p.teleport(back);
                }
            }
        }
        plugin.store().forget(p.getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        java.io.File f = Saved.file(plugin.getDataFolder(), p);
        if (f.isFile()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline() && Saved.recover(f, p)) {
                    Location back = plugin.returnPoint();
                    if (back != null) {
                        p.teleport(back);
                    }
                    Txt.send(p, "<#9AA4B2>Ta partie du Siège a été coupée par un redémarrage : ton inventaire t'a été rendu.");
                }
            }, 20L);
        }
    }

    /** Téléporté hors de l'arène par autre chose (boussole, /spawn…) : abandon. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        Match m = byPlayer.get(e.getPlayer().getUniqueId());
        if (m == null || m.state == Match.State.DONE || e.getTo() == null) {
            return;
        }
        if (!e.getTo().getWorld().equals(world) || Math.abs(m.arena.localX(e.getTo().getX())) > 40) {
            Player p = e.getPlayer();
            Bukkit.getScheduler().runTask(plugin, () -> forfeit(p, "parti de l'arène"));
        }
    }
}
