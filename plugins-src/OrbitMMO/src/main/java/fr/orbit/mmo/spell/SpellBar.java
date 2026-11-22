package fr.orbit.mmo.spell;

import fr.orbit.mmo.Keys;
import fr.orbit.mmo.OrbitMMO;
import fr.orbit.mmo.classes.Resource;
import fr.orbit.mmo.classes.SpellDef;
import fr.orbit.mmo.classes.SpellDef.Category;
import fr.orbit.mmo.clazz.WeaponType;
import fr.orbit.mmo.item.Items;
import fr.orbit.mmo.player.PlayerData;
import fr.orbit.mmo.player.Stats;
import fr.orbit.mmo.ui.Menus;
import fr.orbit.mmo.util.Fx;
import fr.orbit.mmo.util.Text;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.UseCooldown;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.Material;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.meta.SkullMeta;
import fr.orbit.mmo.ui.ProfileMenu;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Trois objets de sort : Attaque, Déplacement et Buff. Chacun contient les sorts connus de son type ;
 * Accroupi + molette fait défiler le sort choisi, [Clic droit] le lance et la main revient seule sur l'arme.
 * Potions et cristaux s'utilisent aussi au clic droit. [F] ouvre le menu, [Maj + F] la fiche du personnage.
 */
public final class SpellBar implements Listener {

    private final OrbitMMO plugin;
    /** Tick du dernier clic droit traité : un clic sur un bloc envoie deux paquets, on n'en garde qu'un. */
    private final Map<UUID, Integer> lastUse = new HashMap<>();

    public SpellBar(OrbitMMO plugin) {
        this.plugin = plugin;
    }

    private boolean active(Player p, PlayerData d) {
        return d != null && !d.buildMode && plugin.progression().valid(d)
                && p.getGameMode() != GameMode.CREATIVE && p.getGameMode() != GameMode.SPECTATOR;
    }

    // ------------------------------------------------------------------ Objets

    /** Catégorie d'un objet de sort (Attaque, Déplacement, Buff) ; null si ce n'en est pas un. */
    public static Category category(ItemStack it) {
        String t = Items.type(it);
        return t != null && t.startsWith("spells_") ? Category.byId(t) : null;
    }

    public static boolean isSpellItem(ItemStack it) {
        return category(it) != null;
    }

    /** Ancien objet « un sort = un objet » : retiré à la synchronisation. */
    private static boolean isLegacySpell(ItemStack it) {
        return "spell".equals(Items.type(it));
    }

    /** Objet « Mon personnage » (remplace la boussole du lobby). */
    public static boolean isProfile(ItemStack it) {
        return "profile".equals(Items.type(it));
    }

    /** Carquois du pisteur : la munition (jamais consommée) des arcs. */
    public static boolean isQuiver(ItemStack it) {
        return "quiver".equals(Items.type(it));
    }

    /** Objets de sort, « Mon personnage » et carquois : rangés librement dans l'inventaire, jamais jetés ni posés ailleurs. */
    public static boolean isLocked(ItemStack it) {
        String t = Items.type(it);
        return t != null && (t.startsWith("spells_") || "spell".equals(t) || "profile".equals(t) || "quiver".equals(t));
    }

    private static ItemStack quiverItem() {
        ItemStack it = Items.icon(Material.ARROW, "<#C9E89A><b>Carquois du pisteur</b>",
                "<#8C96A3>Munition de l'arc : ne s'épuise jamais.",
                "<#5A6270>Les objets de sort ne sont jamais tirés.");
        ItemMeta meta = it.getItemMeta();
        meta.getPersistentDataContainer().set(Keys.ITEM, PersistentDataType.STRING, "quiver");
        meta.setMaxStackSize(1);
        it.setItemMeta(meta);
        return it;
    }

    private static boolean isBow(ItemStack it) {
        return isWeapon(it) && WeaponType.of(it) == WeaponType.BOW;
    }

    /**
     * Un carquois tant que le joueur a un arc (sinon l'arc prendrait un objet de sort à icône de flèche
     * comme munition) ; retiré s'il n'en a plus. Rangé dans le sac, pas dans la barre.
     */
    private void syncQuiver(Player p) {
        PlayerInventory inv = p.getInventory();
        boolean bow = isBow(inv.getItemInOffHand());
        int found = -1;
        for (int i = 0; i < 36; i++) {
            ItemStack it = inv.getItem(i);
            if (isBow(it)) {
                bow = true;
                if (it.getType() == Material.BOW && !it.containsEnchantment(org.bukkit.enchantments.Enchantment.INFINITY)) {
                    // Arcs obtenus avant l'ajout d'Infinité : la flèche du carquois ne doit jamais être consommée.
                    ItemMeta meta = it.getItemMeta();
                    meta.addEnchant(org.bukkit.enchantments.Enchantment.INFINITY, 1, true);
                    it.setItemMeta(meta);
                    inv.setItem(i, it);
                }
            } else if (isQuiver(it)) {
                if (found >= 0) {
                    inv.setItem(i, null); // un seul carquois
                } else {
                    found = i;
                }
            }
        }
        if (isQuiver(inv.getItemInOffHand())) {
            inv.setItemInOffHand(null);
        }
        if (!bow) {
            if (found >= 0) {
                inv.setItem(found, null);
            }
            return;
        }
        if (found >= 0 || isQuiver(p.getItemOnCursor())) {
            return;
        }
        for (int i = 9; i < 36; i++) {
            ItemStack cur = inv.getItem(i);
            if (cur == null || cur.isEmpty()) {
                inv.setItem(i, quiverItem());
                return;
            }
        }
        for (int i = 0; i <= 8; i++) {
            ItemStack cur = inv.getItem(i);
            if (cur == null || cur.isEmpty()) {
                inv.setItem(i, quiverItem());
                return;
            }
        }
    }

    /** L'arc (ou l'arbalète) ne prend jamais un objet de sort, « Mon personnage » ou un ancien sort comme munition. */
    @EventHandler(ignoreCancelled = true)
    public void onReadyArrow(com.destroystokyo.paper.event.player.PlayerReadyArrowEvent e) {
        ItemStack arrow = e.getArrow();
        if (isLocked(arrow) && !isQuiver(arrow)) {
            e.setCancelled(true); // le serveur passe à la munition suivante (le carquois)
        }
    }

    /** Le carquois n'est jamais consommé et ses flèches ne se ramassent pas. */
    @EventHandler(ignoreCancelled = true)
    public void onShootBow(org.bukkit.event.entity.EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player p) || !isQuiver(e.getConsumable())) {
            return;
        }
        e.setConsumeItem(false);
        if (e.getProjectile() instanceof org.bukkit.entity.AbstractArrow a) {
            a.setPickupStatus(org.bukkit.entity.AbstractArrow.PickupStatus.DISALLOWED);
        }
        Bukkit.getScheduler().runTask(plugin, p::updateInventory);
    }

    /** Boussole d'OrbitDevCore : réservée au lobby. */
    private static final org.bukkit.NamespacedKey LOBBY_COMPASS = new org.bukkit.NamespacedKey("orbitdevcore", "teleport_compass");

    private static boolean isLobbyCompass(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(LOBBY_COMPASS);
    }

    public ItemStack profileItem(Player p) {
        ItemStack it = Items.icon(Material.PLAYER_HEAD, "<#7FB2FF><b>Mon personnage</b>",
                "<#8C96A3>Stats, classe, race, faction,",
                "<#8C96A3>sorts (dégâts, coût, recharge) et talents.",
                "",
                "<#5EE89A>[Clic droit] ouvrir",
                "<#5A6270>[Clic droit sur un joueur] l'inspecter");
        SkullMeta meta = (SkullMeta) it.getItemMeta();
        meta.setOwningPlayer(p);
        meta.getPersistentDataContainer().set(Keys.ITEM, PersistentDataType.STRING, "profile");
        meta.setMaxStackSize(1);
        it.setItemMeta(meta);
        return it;
    }

    /** Donne « Mon personnage » s'il manque : dernière case de la barre, sinon la première libre. */
    private void giveProfile(Player p) {
        PlayerInventory inv = p.getInventory();
        if (isProfile(p.getItemOnCursor())) {
            return;
        }
        for (int i = 0; i < 36; i++) {
            if (isProfile(inv.getItem(i))) {
                return;
            }
        }
        ItemStack it = profileItem(p);
        ItemStack last = inv.getItem(8);
        if (last == null || last.isEmpty()) {
            inv.setItem(8, it);
            return;
        }
        for (int i = 0; i < 36; i++) {
            ItemStack cur = inv.getItem(i);
            if (cur == null || cur.isEmpty()) {
                inv.setItem(i, it);
                return;
            }
        }
    }

    /** Une arme (et pas un objet de sort dont l'icône ressemble à une arme). */
    public static boolean isWeapon(ItemStack it) {
        return it != null && !it.isEmpty() && !isLocked(it) && WeaponType.of(it) != WeaponType.NONE;
    }

    /** Sorts connus rangés dans cet objet, dans l'ordre du fichier de la classe. */
    public List<SpellDef> spells(PlayerData d, Category cat) {
        List<SpellDef> out = new ArrayList<>();
        for (SpellDef s : plugin.progression().known(d)) {
            if (s.category == cat) {
                out.add(s);
            }
        }
        return out;
    }

    /** Sort choisi dans l'objet (le premier connu si le choix n'est plus valable) ; null si l'objet est vide. */
    public SpellDef picked(PlayerData d, Category cat) {
        List<SpellDef> list = spells(d, cat);
        if (list.isEmpty()) {
            return null;
        }
        String id = d.picks.get(cat.id);
        for (SpellDef s : list) {
            if (s.id.equals(id)) {
                return s;
            }
        }
        d.picks.put(cat.id, list.get(0).id);
        return list.get(0);
    }

    /** Choisit le sort lancé par son objet (livre de sorts) et met l'objet à jour. */
    public void pick(Player p, PlayerData d, SpellDef s) {
        d.picks.put(s.category.id, s.id);
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < 36; i++) {
            if (category(inv.getItem(i)) == s.category) {
                inv.setItem(i, item(d, s.category));
                return;
            }
        }
    }

    /**
     * L'objet d'une catégorie : il prend l'icône et la recharge du sort choisi, et sa description
     * liste les sorts qu'il contient. null si le personnage n'a encore aucun sort de ce type.
     */
    public ItemStack item(PlayerData d, Category cat) {
        List<SpellDef> list = spells(d, cat);
        SpellDef cur = picked(d, cat);
        if (cur == null) {
            return null;
        }
        List<String> lore = new ArrayList<>();
        lore.add(cat.tag() + " <#5A6270>· " + list.size() + " sort" + (list.size() > 1 ? "s" : ""));
        for (SpellDef s : list) {
            lore.add(s == cur ? "<white>▶ <" + s.hex + "><b>" + s.name + "</b>" : "<#5A6270>   " + s.name);
        }
        lore.add("");
        lore.addAll(describe(cur, d));
        lore.add("");
        lore.add("<#5EE89A>[Clic droit] lancer <#5A6270>· la main revient sur l'arme");
        if (list.size() > 1) {
            lore.add("<#FFD66E>[Accroupi + molette] changer de sort");
        }
        String count = list.size() > 1 ? " " + (list.indexOf(cur) + 1) + "/" + list.size() : "";
        ItemStack it = Items.icon(cur.icon, "<" + cur.hex + "><b>" + cur.name + "</b> <#5A6270>· " + cat.symbol + " " + cat.label + count, lore);
        ItemMeta meta = it.getItemMeta();
        meta.getPersistentDataContainer().set(Keys.ITEM, PersistentDataType.STRING, cat.itemType());
        meta.setMaxStackSize(1);
        it.setItemMeta(meta);
        // La recharge affichée sur l'objet est celle du sort choisi.
        it.setData(DataComponentTypes.USE_COOLDOWN, UseCooldown.useCooldown(Math.max(0.05f, (float) cur.cooldown))
                .cooldownGroup(Keys.cooldownGroup(cur.id)).build());
        return it;
    }

    /** Range un objet de sort : sa case préférée, sinon une case libre de la barre, sinon de l'inventaire. */
    private static void place(PlayerInventory inv, ItemStack it, int pref) {
        ItemStack at = inv.getItem(pref);
        if (at == null || at.isEmpty()) {
            inv.setItem(pref, it);
            return;
        }
        for (int i = 0; i < 36; i++) {
            ItemStack cur = inv.getItem(i);
            if (cur == null || cur.isEmpty()) {
                inv.setItem(i, it);
                return;
            }
        }
    }

    /**
     * Remet les objets de sort à jour : un seul objet par catégorie (Attaque, Déplacement, Buff), donné s'il
     * manque, retiré s'il n'a plus de sort ; descriptions réécrites (talents) et recharges réaffichées.
     * Les anciens objets « un sort = un objet » sont retirés.
     */
    public void sync(Player p, PlayerData d) {
        if (p == null || !p.isOnline() || d.buildMode || !plugin.progression().valid(d)) {
            return;
        }
        PlayerInventory inv = p.getInventory();
        java.util.EnumSet<Category> seen = java.util.EnumSet.noneOf(Category.class);
        ItemStack cursor = p.getItemOnCursor();
        if (isLegacySpell(cursor)) {
            p.setItemOnCursor(null);
        } else if (category(cursor) != null) {
            seen.add(category(cursor));
        }
        boolean profile = false;
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (isLobbyCompass(it) || isLegacySpell(it)) {
                inv.setItem(i, null);
                continue;
            }
            if (isProfile(it)) {
                if (profile || i >= 36) {
                    inv.setItem(i, null);
                }
                profile = true;
                continue;
            }
            Category cat = category(it);
            if (cat == null) {
                continue;
            }
            inv.setItem(i, i < 36 && seen.add(cat) ? item(d, cat) : null);
        }
        for (Category cat : Category.values()) {
            if (!seen.contains(cat)) {
                ItemStack it = item(d, cat);
                if (it != null) {
                    place(inv, it, cat.slot);
                }
            }
        }
        giveProfile(p);
        plugin.progression().ensureMagicWeapon(p, d);
        syncQuiver(p);
        long now = System.currentTimeMillis();
        for (SpellDef s : plugin.progression().known(d)) {
            long left = d.cooldowns.getOrDefault(s.id, 0L) - now;
            p.setCooldown(Keys.cooldownGroup(s.id), left > 0 ? (int) (left / 50) : 0);
        }
        weaponSlot(p, d);
    }

    /** Case de la barre qui tient l'arme : la dernière tenue, sinon la première trouvée (-1 : aucune). */
    public int weaponSlot(Player p, PlayerData d) {
        PlayerInventory inv = p.getInventory();
        if (d.weaponSlot >= 0 && d.weaponSlot <= 8 && isWeapon(inv.getItem(d.weaponSlot))) {
            return d.weaponSlot;
        }
        for (int i = 0; i <= 8; i++) {
            if (isWeapon(inv.getItem(i))) {
                d.weaponSlot = i;
                return i;
            }
        }
        return -1;
    }

    /** Arme du personnage : celle en main, sinon celle de la barre (pour lancer un sort en tenant son icône). */
    public ItemStack weapon(Player p, PlayerData d) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (isWeapon(hand)) {
            return hand;
        }
        int slot = weaponSlot(p, d);
        return slot < 0 ? null : p.getInventory().getItem(slot);
    }

    /** Lignes de description d'un sort (objet et livre de sorts), avec les bonus de talents. */
    public List<String> describe(SpellDef s, PlayerData d) {
        Resource r = plugin.progression().resource(d);
        List<String> lore = new ArrayList<>();
        lore.add("<#D7DCE2>" + s.desc);
        lore.add("");
        double cost = plugin.spells().cost(d, s);
        if (cost > 0) {
            lore.add("<#8C96A3>Coût : <" + r.hex + ">" + fmt(cost) + " " + r.label.toLowerCase(Locale.ROOT));
        }
        if (s.gain > 0) {
            lore.add("<#8C96A3>Génère : <" + r.hex + ">" + fmt(s.gain) + " " + r.label.toLowerCase(Locale.ROOT));
        }
        lore.add("<#8C96A3>Incantation : <white>" + (s.castTime > 0 ? fmt(s.castTime) + " s" : "instantanée"));
        double cd = plugin.spells().cooldown(d, s);
        if (cd > 0) {
            lore.add("<#8C96A3>Recharge : <white>" + fmt(cd) + " s");
        }
        if (s.type == SpellDef.Type.CHARGE || s.type == SpellDef.Type.PROJECTILE || s.type == SpellDef.Type.SOIN
                || s.type == SpellDef.Type.BOND || s.type == SpellDef.Type.ZONE) {
            lore.add("<#8C96A3>Portée : <white>" + fmt(s.range) + " blocs");
        }
        if (!s.weapons.isEmpty()) {
            lore.add("<#8C96A3>Arme : <white>" + String.join(" ou ", s.weapons.stream().map(w -> w.label).toList()));
        }
        if (s.shield) {
            lore.add("<#8C96A3>Exige un bouclier en main secondaire");
        }
        return lore;
    }

    /**
     * Valeurs chiffrées d'un sort pour ce personnage (arme, niveau, talents), hors effets temporaires :
     * dégâts, soins, boucliers, saignement, contrôle.
     */
    public List<String> numbers(Player p, PlayerData d, SpellDef s) {
        List<String> out = new ArrayList<>();
        double power = plugin.combat().power(p, d);
        double critMult = plugin.combat().critMult(d);
        int crit = (int) Math.round(plugin.combat().critChance(d, s.id) * 100);
        boolean hostile = s.effects.keySet().stream().anyMatch(SpellEngine.ENEMY_KEYS::contains);
        boolean damages = s.power > 0 && switch (s.type) {
            case FRAPPE, TOURBILLON, CHARGE, BOND, PROJECTILE, ZONE -> true;
            case CRI -> hostile;
            default -> false;
        };
        if (damages) {
            double dmg = power * s.power * d.mods.mult("degats", s.id);
            String per = s.type == SpellDef.Type.ZONE ? " par seconde" : s.hits > 1 ? " par coup" : "";
            out.add("<#FFB347>Dégâts : <white>≈ " + fmt(Math.round(dmg)) + per + " <#5A6270>(critique ≈ " + fmt(Math.round(dmg * critMult)) + ")");
            if (s.hits > 1) {
                out.add("<#8C96A3>" + s.hits + " coups : <white>≈ " + fmt(Math.round(dmg * s.hits)) + " au total");
            }
            if (s.type == SpellDef.Type.ZONE) {
                out.add("<#8C96A3>Pendant " + fmt(plugin.spells().duration(d, s)) + " s : <white>≈ "
                        + fmt(Math.round(dmg * Math.max(1, Math.round(plugin.spells().duration(d, s))))) + " au total");
            }
            if (s.radius > 0 || s.type == SpellDef.Type.TOURBILLON || s.type == SpellDef.Type.CRI) {
                out.add("<#8C96A3>Touche tous les ennemis de la zone");
            }
            out.add("<#8C96A3>Critique : <white>" + crit + " %");
        }
        if (s.type == SpellDef.Type.SOIN) {
            double heal = power * s.power * d.mods.mult("soins", s.id);
            out.add("<#5EE89A>Soin : <white>≈ " + fmt(Math.round(heal)) + " PV <#5A6270>(critique ≈ " + fmt(Math.round(heal * 1.5)) + ")");
        }
        double max = Stats.maxHp(d);
        if (s.has("saignement")) {
            double perSec = power * Math.max(0.05, s.effect("dot")) * d.mods.mult("degats", s.id);
            double dur = s.effect("saignement") + d.mods.get("duree", s.id);
            out.add("<#FF5C5C>Saignement : <white>≈ " + fmt(Math.round(perSec)) + "/s pendant " + fmt(dur) + " s <#5A6270>(≈ " + fmt(Math.round(perSec * dur)) + ")");
        }
        if (s.has("vol-de-vie")) {
            out.add("<#FF5C5C>Vol de vie : <white>" + fmt(s.effect("vol-de-vie")) + " % des dégâts");
        }
        if (s.has("soin")) {
            out.add("<#5EE89A>Soin : <white>≈ " + fmt(Math.round(max * s.effect("soin") / 100.0 * d.mods.mult("soins", s.id))) + " PV");
        }
        if (s.has("soin-soi")) {
            out.add("<#5EE89A>Vous soigne de <white>≈ " + fmt(Math.round(max * s.effect("soin-soi") / 100.0)) + " PV");
        }
        if (s.has("soin-par-sec")) {
            out.add("<#5EE89A>Soin : <white>≈ " + fmt(Math.round(max * s.effect("soin-par-sec") / 100.0)) + " PV/s");
        }
        if (s.has("bouclier")) {
            out.add("<#E6C35C>Bouclier : <white>absorbe ≈ " + fmt(Math.round(max * s.effect("bouclier") / 100.0)) + " dégâts");
        }
        if (s.has("reduction")) {
            out.add("<#E6C35C>Dégâts subis : <white>−" + fmt(s.effect("reduction")) + " %");
        }
        if (s.has("bonus-degats")) {
            out.add("<#FFB347>Dégâts : <white>+" + fmt(s.effect("bonus-degats")) + " %");
        }
        if (s.has("bonus-crit")) {
            out.add("<#FFB347>Critique : <white>+" + fmt(s.effect("bonus-crit")) + " %");
        }
        if (s.has("etourdir")) {
            out.add("<#9AD8FF>Étourdit : <white>" + fmt(s.effect("etourdir")) + " s <#5A6270>(pas les boss)");
        }
        if (s.has("peur")) {
            out.add("<#9AD8FF>Terrifie : <white>" + fmt(s.effect("peur")) + " s <#5A6270>(pas les boss)");
        }
        if (s.has("ralentir")) {
            out.add("<#9AD8FF>Ralentit : <white>" + fmt(s.effect("ralentir")) + " s");
        }
        if (s.has("affaiblir")) {
            out.add("<#B36BFF>Affaiblit : <white>−20 % de dégâts pendant " + fmt(s.effect("affaiblir")) + " s");
        }
        double dur = plugin.spells().duration(d, s);
        if (dur > 0 && s.type != SpellDef.Type.ZONE && !s.has("saignement")) {
            out.add("<#8C96A3>Durée : <white>" + fmt(dur) + " s");
        }
        return out;
    }

    static String fmt(double v) {
        if (Math.abs(v - Math.rint(v)) < 1.0E-6) {
            return String.valueOf((long) Math.rint(v));
        }
        return String.format(Locale.ROOT, "%.1f", v).replace('.', ',');
    }

    // ------------------------------------------------------------------ Clic droit

    /** Un seul clic droit traité par tick (un clic sur un bloc arrive en double). */
    private boolean debounce(Player p) {
        int tick = Bukkit.getCurrentTick();
        Integer last = lastUse.put(p.getUniqueId(), tick);
        return last != null && last == tick;
    }

    // Pas d'ignoreCancelled : Paper envoie le clic droit dans le vide déjà « annulé ».
    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent e) {
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player p = e.getPlayer();
        ItemStack main = p.getInventory().getItemInMainHand();
        Category cat = category(main);
        boolean profile = isProfile(main);
        if (cat != null || profile || isLegacySpell(main)) {
            Block b = e.getClickedBlock();
            if (a == Action.RIGHT_CLICK_BLOCK && b != null && b.getType().isInteractable() && !p.isSneaking()) {
                // Porte, coffre, levier : le bloc réagit, l'objet ne sert pas (et ne se pose pas).
                e.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
                return;
            }
            e.setCancelled(true);
            if (e.getHand() != EquipmentSlot.HAND || debounce(p)) {
                return;
            }
            PlayerData d = plugin.players().loaded(p.getUniqueId());
            if (profile) {
                if (d != null && plugin.progression().valid(d)) {
                    ProfileMenu.open(plugin, p, p);
                }
            } else if (active(p, d)) {
                castFromHand(p, d, cat);
            }
            return;
        }
        String type = Items.type(e.getItem());
        if (!"potion".equals(type) && !"mana_potion".equals(type) && !"heal_crystal".equals(type) && !"tp_crystal".equals(type)
                && fr.orbit.mmo.item.Boost.byItemType(type) == null) {
            return;
        }
        e.setCancelled(true);
        if (debounce(p)) {
            return;
        }
        PlayerData d = plugin.players().loaded(p.getUniqueId());
        if (active(p, d) && d.hp > 0) {
            useConsumable(p, d, e.getItem(), type);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        Player p = e.getPlayer();
        ItemStack main = p.getInventory().getItemInMainHand();
        Category cat = category(main);
        boolean profile = isProfile(main);
        if ((cat == null && !profile && !isLegacySpell(main)) || e.getRightClicked().getPersistentDataContainer().has(Keys.NPC)) {
            return;
        }
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND || debounce(p)) {
            return;
        }
        PlayerData d = plugin.players().loaded(p.getUniqueId());
        if (profile) {
            // Clic droit sur un joueur : sa fiche ; sinon la sienne.
            Player target = e.getRightClicked() instanceof Player o && o.isOnline() ? o : p;
            if (d != null && plugin.progression().valid(d)) {
                ProfileMenu.open(plugin, p, target);
            }
        } else if (active(p, d)) {
            castFromHand(p, d, cat);
        }
    }

    /** Lance le sort choisi dans l'objet tenu, puis la main revient sur l'arme. */
    private void castFromHand(Player p, PlayerData d, Category cat) {
        SpellDef s = cat == null ? null : picked(d, cat);
        if (s == null) {
            // Ancien objet de sort ou objet vide : on remet l'inventaire d'aplomb.
            sync(p, d);
            return;
        }
        if (!plugin.spells().cast(p, d, s.id)) {
            return;
        }
        int slot = weaponSlot(p, d);
        if (slot >= 0 && slot != p.getInventory().getHeldItemSlot()) {
            p.getInventory().setHeldItemSlot(slot);
        }
    }

    private void useConsumable(Player p, PlayerData d, ItemStack item, String type) {
        fr.orbit.mmo.item.Boost boost = fr.orbit.mmo.item.Boost.byItemType(type);
        if (boost != null) {
            // Potion de vitesse, élixir, parchemin, essence : effet de quelques minutes.
            plugin.progression().applyBoost(p, d, boost);
            item.setAmount(item.getAmount() - 1);
            return;
        }
        long now = System.currentTimeMillis();
        Location at = p.getLocation().add(0, 1, 0);
        switch (type) {
            case "potion" -> {
                if (d.potionUntil > now) {
                    plugin.hud().flash(p, "<#8C96A3>Une potion fait déjà effet.", 30);
                    return;
                }
                d.potionUntil = now + 5000;
                p.playSound(at, Sound.ENTITY_GENERIC_DRINK, 1f, 1f);
                Fx.dust(at, Fx.color("#FF8AAE"), 1.2f, 15, 0.4);
                plugin.hud().flash(p, "<#FF8AAE>Potion de soin <#8C96A3>(" + (count(p.getInventory(), "potion") - 1) + " restante(s))", 30);
            }
            case "mana_potion" -> {
                fr.orbit.mmo.classes.Resource r = plugin.progression().resource(d);
                if (d.resource >= r.max) {
                    plugin.hud().flash(p, "<#8C96A3>" + r.label + " déjà au maximum.", 30);
                    return;
                }
                d.resource = Math.min(r.max, d.resource + r.max * 0.5);
                p.playSound(at, Sound.ENTITY_GENERIC_DRINK, 1f, 1.2f);
                Fx.dust(at, Fx.color(r.hex), 1.2f, 15, 0.4);
                plugin.hud().flash(p, "<" + r.hex + ">+" + r.label + " <#8C96A3>(" + (count(p.getInventory(), "mana_potion") - 1) + " restante(s))", 30);
            }
            case "heal_crystal" -> {
                plugin.combat().heal(p, d, Stats.maxHp(d));
                p.getWorld().spawnParticle(Particle.HEART, at.clone().add(0, 0.6, 0), 6, 0.4, 0.3, 0.4, 0);
                Fx.dust(at, Fx.color("#FF8AD8"), 1.6f, 30, 0.6);
                p.getWorld().playSound(at, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1.6f);
                plugin.hud().flash(p, "<#FF8AD8><b>Soin !</b>", 30);
            }
            case "tp_crystal" -> {
                if (now - d.lastCombat < 5000) {
                    deny(p, "Impossible en plein combat");
                    return;
                }
                plugin.hud().flash(p, "<#7FD4FF><b>Téléportation !</b> <#D7DCE2>" + Text.esc(plugin.floors().def(d.floor).town()), 40);
                plugin.floors().teleport(p, plugin.floors().unlocked(d.floor) ? d.floor : 1, true);
            }
            default -> {
                return;
            }
        }
        item.setAmount(item.getAmount() - 1);
    }

    private static int count(PlayerInventory inv, String type) {
        int n = 0;
        for (ItemStack it : inv.getContents()) {
            if (type.equals(Items.type(it))) {
                n += it.getAmount();
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ Barre et inventaire

    /**
     * Accroupi + molette sur un objet de sort : la molette fait défiler les sorts de l'objet au lieu de
     * changer de case. Sans s'accroupir (ou avec les touches 1 à 9), la barre change normalement.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onScroll(PlayerItemHeldEvent e) {
        Player p = e.getPlayer();
        if (!p.isSneaking()) {
            return;
        }
        Category cat = category(p.getInventory().getItem(e.getPreviousSlot()));
        if (cat == null) {
            return;
        }
        int delta = Math.floorMod(e.getNewSlot() - e.getPreviousSlot(), 9);
        if (delta != 1 && delta != 8) {
            return; // saut de plusieurs cases : touche numérique, pas la molette
        }
        PlayerData d = plugin.players().loaded(p.getUniqueId());
        if (!active(p, d)) {
            return;
        }
        e.setCancelled(true);
        List<SpellDef> list = spells(d, cat);
        if (list.size() < 2) {
            plugin.hud().flash(p, cat.tag() + " <#8C96A3>· un seul sort pour l'instant", 25);
            return;
        }
        int idx = Math.floorMod(list.indexOf(picked(d, cat)) + (delta == 1 ? 1 : -1), list.size());
        SpellDef next = list.get(idx);
        d.picks.put(cat.id, next.id);
        p.getInventory().setItem(e.getPreviousSlot(), item(d, cat));
        plugin.hud().flash(p, cat.tag() + " <#5A6270>▸ <" + next.hex + "><b>" + next.name + "</b> <#5A6270>(" + (idx + 1) + "/" + list.size()
                + ") · <white>[Clic droit]</white> lancer", 30);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.2f + 0.1f * idx);
    }

    /** Retient la case de l'arme ; rappelle comment utiliser un objet de sort quand on le sélectionne. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent e) {
        Player p = e.getPlayer();
        PlayerData d = plugin.players().loaded(p.getUniqueId());
        if (!active(p, d)) {
            return;
        }
        ItemStack it = p.getInventory().getItem(e.getNewSlot());
        if (isWeapon(it)) {
            d.weaponSlot = e.getNewSlot();
            if (isBow(it)) {
                syncQuiver(p); // arc obtenu après la dernière synchronisation
            }
            return;
        }
        Category cat = category(it);
        SpellDef s = cat == null ? null : picked(d, cat);
        if (s != null && d.cast == null) {
            boolean several = spells(d, cat).size() > 1;
            plugin.hud().flash(p, "<" + s.hex + "><b>" + s.name + "</b> <#8C96A3>· <white>[Clic droit]</white> lancer"
                    + (several ? " <#5A6270>· <white>[Accroupi + molette]</white> changer" : ""), 30);
        }
    }

    /** [F] : menu (livre de sorts, talents, spécialisation…) ; [Maj + F] : fiche « Mon personnage ». */
    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        PlayerData d = plugin.players().loaded(p.getUniqueId());
        if (!active(p, d)) {
            return;
        }
        e.setCancelled(true);
        if (p.isSneaking()) {
            ProfileMenu.open(plugin, p, p);
        } else {
            Menus.main(plugin, p);
        }
    }

    /** Les sorts se rangent librement dans l'inventaire, mais jamais dans un coffre, l'artisanat, l'armure ou la main secondaire. */
    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        PlayerData d = plugin.players().loaded(p.getUniqueId());
        if (!active(p, d)) {
            return;
        }
        ItemStack keyed = e.getClick() == ClickType.NUMBER_KEY ? p.getInventory().getItem(e.getHotbarButton())
                : e.getClick() == ClickType.SWAP_OFFHAND ? p.getInventory().getItemInOffHand() : null;
        boolean incoming = isLocked(e.getCursor()) || isLocked(keyed);
        Inventory top = e.getView().getTopInventory();
        Inventory clicked = e.getClickedInventory();
        if (clicked == null) {
            return;
        }
        boolean intoTop = clicked == top && incoming;
        boolean shiftOut = e.isShiftClick() && clicked != top && isLocked(e.getCurrentItem()) && top.getType() != InventoryType.CRAFTING;
        // Maj + clic sur la tête « Mon personnage » l'enfilerait comme casque.
        boolean shiftHead = e.isShiftClick() && isProfile(e.getCurrentItem());
        boolean special = clicked instanceof PlayerInventory && e.getSlot() >= 36 && incoming;
        boolean toOffhand = e.getClick() == ClickType.SWAP_OFFHAND && isLocked(e.getCurrentItem());
        if (intoTop || shiftOut || shiftHead || special || toOffhand) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (!isLocked(e.getOldCursor())) {
            return;
        }
        int topSize = e.getView().getTopInventory().getSize();
        for (int raw : e.getRawSlots()) {
            boolean special = e.getView().getInventory(raw) instanceof PlayerInventory && e.getView().convertSlot(raw) >= 36;
            if (raw < topSize || special) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        ItemStack it = e.getItemDrop().getItemStack();
        if (isSpellItem(it)) {
            e.setCancelled(true);
            plugin.hud().flash(e.getPlayer(), "<#8C96A3>Les objets de sort ne se jettent pas (rangez-les où vous voulez).", 40);
        } else if (isLocked(it)) {
            e.setCancelled(true);
        }
    }

    /** La boussole d'OrbitDevCore est réservée au lobby. */
    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        String cmd = e.getMessage().split(" ", 2)[0].toLowerCase(Locale.ROOT);
        if (!cmd.equals("/boussole") && !cmd.equals("/compass") && !cmd.equals("/orbitdevcore:boussole") && !cmd.equals("/orbitdevcore:compass")) {
            return;
        }
        PlayerData d = plugin.players().loaded(e.getPlayer().getUniqueId());
        if (d != null && d.buildMode) {
            return;
        }
        e.setCancelled(true);
        Text.msg(e.getPlayer(), "<#8C96A3>La boussole n'existe qu'au lobby. Ici : <white>Mon personnage</white> (clic droit), "
                + "<white>[F]</white> pour le menu, <white>/lobby</white> pour repartir.");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        lastUse.remove(e.getPlayer().getUniqueId());
    }

    /** Message court si un sort est indisponible. */
    void deny(Player p, String why) {
        plugin.hud().flash(p, "<#FF5C5C>" + Text.esc(why), 30);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.8f);
    }
}
