package fr.orbit.tycoon;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lots spéciaux des coffres orbitaux (en plus des boosters, Boosters.java) :
 * - Capsules de Crédits / Cellules de Flux : clic droit = N minutes de farm de ta meilleure ressource débloquée
 *   (tous outils confondus), donc toujours utiles, du début à la fin de la progression.
 * - Titres cosmétiques : plaque holographique à activer (clic droit), titre affiché devant le pseudo dans le chat,
 *   choisi avec /titre. Purement cosmétique. Titres définis dans config.yml (titres.liste), possédés dans titres.yml.
 */
final class Butin implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    /** Tailles de capsule : nom et modèle du pack (1 = petite … 3 = colossale). */
    static final String[] TAILLES = {"", "Petite", "Grande", "Colossale"};

    private final OrbitTycoon plugin;
    private final NamespacedKey cleCapsule, cleMinutes, cleTaille, cleTitre;
    private final File fichierTitres;
    /** Joueur → titres possédés (ids) et titre affiché. Lu par le thread du chat. */
    private final Map<UUID, List<String>> possedes = new ConcurrentHashMap<>();
    private final Map<UUID, String> affiches = new ConcurrentHashMap<>();

    Butin(OrbitTycoon plugin) {
        this.plugin = plugin;
        cleCapsule = new NamespacedKey(plugin, "capsule_monnaie");
        cleMinutes = new NamespacedKey(plugin, "capsule_minutes");
        cleTaille = new NamespacedKey(plugin, "capsule_taille");
        cleTitre = new NamespacedKey(plugin, "plaque_titre");
        fichierTitres = new File(plugin.getDataFolder(), "titres.yml");
        chargerTitres();
    }

    // ------------------------------------------------------------------ capsules de monnaie

    /** Gain par récolte de la meilleure ressource débloquée, tous outils (monnaie CREDIT ou FLUX). */
    long meilleurGain(UUID id, Monnaies.Monnaie mon) {
        var cfg = plugin.getConfig();
        String section = mon == Monnaies.Monnaie.FLUX ? "flux-stellaire" : "credit-orbital";
        long meilleur = cfg.getLong(section + ".defaut", 1);
        // Flux : chaque outil a le sien → seulement l'outil qui le recevra (celui en main).
        Outil.Mode[] outils = mon == Monnaies.Monnaie.FLUX
                ? new Outil.Mode[]{plugin.monnaies().outilCible(id)} : Outil.Mode.values();
        for (Outil.Mode m : outils) {
            List<Ressources.Palier> liste = plugin.ressources().liste(m);
            if (liste == null) continue;
            for (int i = liste.size() - 1; i >= 0; i--) {
                if (!plugin.ressources().debloque(id, m, i)) continue;
                Ressources.Palier r = liste.get(i);
                long v = !r.origine() ? (mon == Monnaies.Monnaie.FLUX ? r.flux() : r.credit())
                        : r.icone() != null ? cfg.getLong(section + ".ressources." + r.icone().name(), meilleur) : meilleur;
                meilleur = Math.max(meilleur, v);
                break;
            }
        }
        return meilleur;
    }

    /** Montant d'une capsule de « minutes » minutes de farm (à recoltes-par-seconde), arrondi à la dizaine. */
    long montant(UUID id, Monnaies.Monnaie mon, int minutes) {
        double rps = plugin.getConfig().getDouble("progression.recoltes-par-seconde", 1);
        long v = Math.round(meilleurGain(id, mon) * minutes * 60 * rps);
        return Math.max(10, (v + 9) / 10 * 10);
    }

    ItemStack capsule(Monnaies.Monnaie mon, int minutes, int taille, int nombre) {
        boolean flux = mon == Monnaies.Monnaie.FLUX;
        int t = Math.max(1, Math.min(3, taille));
        ItemStack it = new ItemStack(flux ? Material.PRISMARINE_CRYSTALS : Material.GOLD_NUGGET, Math.max(1, Math.min(64, nombre)));
        it.editMeta(m -> {
            String nom = flux ? TAILLES[t] + " Cellule de Flux" : TAILLES[t] + " Capsule de Crédits";
            m.itemName(MM.deserialize("<" + mon.couleur + "><bold>" + nom + "</bold>"));
            m.lore(List.of(
                    MM.deserialize("<!italic><dark_gray>" + (flux ? "Énergie stellaire condensée" : "Réserve de la Banque orbitale")),
                    Component.empty(),
                    MM.deserialize("<!italic><gray>Contient <white>" + minutes + " min</white> de farm de ta"),
                    MM.deserialize(flux ? "<!italic><gray>meilleure ressource de <" + mon.couleur + ">l'outil en main</" + mon.couleur + ">."
                            : "<!italic><gray>meilleure ressource en <" + mon.couleur + ">" + mon.symbole + " " + mon.pluriel + "</" + mon.couleur + ">."),
                    MM.deserialize("<!italic><dark_gray>Plus tu progresses, plus elle rapporte."),
                    Component.empty(),
                    MM.deserialize("<!italic><#62CFF4>➜ Clic droit pour l'ouvrir <dark_gray>(Shift : toute la pile)")));
            m.setItemModel(new NamespacedKey("orbittycoon", (flux ? "cellule_flux_" : "capsule_credits_") + t));
            m.setEnchantmentGlintOverride(t >= 3);
            var pdc = m.getPersistentDataContainer();
            pdc.set(cleCapsule, PersistentDataType.STRING, mon.id);
            pdc.set(cleMinutes, PersistentDataType.INTEGER, minutes);
            pdc.set(cleTaille, PersistentDataType.INTEGER, t);
        });
        return it;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void clic(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack it = e.getItem();
        if (it == null || !it.hasItemMeta()) return;
        var pdc = it.getItemMeta().getPersistentDataContainer();
        Player p = e.getPlayer();
        String titre = pdc.get(cleTitre, PersistentDataType.STRING);
        if (titre != null) {
            e.setCancelled(true);
            if (debloquer(p, titre)) it.setAmount(it.getAmount() - 1);
            return;
        }
        String mid = pdc.get(cleCapsule, PersistentDataType.STRING);
        if (mid == null) return;
        e.setCancelled(true);
        Monnaies.Monnaie mon = mid.equals("flux") ? Monnaies.Monnaie.FLUX : Monnaies.Monnaie.CREDIT;
        int minutes = pdc.getOrDefault(cleMinutes, PersistentDataType.INTEGER, 1);
        int n = p.isSneaking() ? it.getAmount() : 1;
        long total = montant(p.getUniqueId(), mon, minutes) * n;
        it.setAmount(it.getAmount() - n);
        plugin.monnaies().ajouter(p.getUniqueId(), mon, total);
        Outil.Mode cible = mon == Monnaies.Monnaie.FLUX ? plugin.monnaies().outilCible(p.getUniqueId()) : null;
        String gain = cible != null
                ? "<" + cible.couleur + "><bold>+" + Hud.nombre(total) + " " + cible.symbole + " " + cible.ressource + "</bold>"
                : "<" + mon.couleur + "><bold>+" + Hud.nombre(total) + " " + mon.symbole + " " + (total > 1 ? mon.pluriel : mon.nom) + "</bold>";
        OrbitTycoon.msg(p, "<gray>" + (n > 1 ? n + " capsules ouvertes" : "Capsule ouverte") + " : " + gain);
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.9f, 1.5f);
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 0.8f);
    }

    // ------------------------------------------------------------------ titres cosmétiques

    /** Titres définis dans config.yml (titres.liste : id → texte MiniMessage), dans l'ordre. */
    Map<String, String> titres() {
        Map<String, String> res = new LinkedHashMap<>();
        var sec = plugin.getConfig().getConfigurationSection("titres.liste");
        if (sec != null) for (String k : sec.getKeys(false)) res.put(k, sec.getString(k, k));
        return res;
    }

    String texteTitre(String id) { return titres().getOrDefault(id, id); }

    ItemStack plaque(String id) {
        ItemStack it = new ItemStack(Material.NAME_TAG);
        it.editMeta(m -> {
            m.itemName(MM.deserialize("<#E0B864><bold>Plaque de titre</bold>"));
            m.lore(List.of(
                    MM.deserialize("<!italic><dark_gray>Holo-badge de la Station"),
                    Component.empty(),
                    MM.deserialize("<!italic><gray>Titre : ").append(MM.deserialize("<!italic>" + texteTitre(id))),
                    MM.deserialize("<!italic><gray>Affiché devant ton pseudo dans le chat."),
                    MM.deserialize("<!italic><dark_gray>Purement cosmétique."),
                    Component.empty(),
                    MM.deserialize("<!italic><#62CFF4>➜ Clic droit pour le débloquer, puis /titre")));
            m.setItemModel(new NamespacedKey("orbittycoon", "plaque_titre"));
            m.setEnchantmentGlintOverride(true);
            m.getPersistentDataContainer().set(cleTitre, PersistentDataType.STRING, id);
        });
        return it;
    }

    boolean possede(UUID id, String titre) { return possedes.getOrDefault(id, List.of()).contains(titre); }

    /** Débloque un titre (false s'il était déjà possédé : la plaque n'est pas consommée). */
    boolean debloquer(Player p, String titre) {
        if (possede(p.getUniqueId(), titre)) {
            OrbitTycoon.msg(p, "<gray>Tu possèdes déjà le titre " + texteTitre(titre) + "<gray>. Revends la plaque à l'HDV !");
            return false;
        }
        possedes.computeIfAbsent(p.getUniqueId(), k -> new ArrayList<>()).add(titre);
        affiches.put(p.getUniqueId(), titre);
        sauverTitres();
        OrbitTycoon.msg(p, "<gray>Nouveau titre débloqué et affiché : " + texteTitre(titre) + "<gray> ! <dark_gray>(/titre pour changer)");
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.3f);
        return true;
    }

    /** Titre affiché devant le pseudo (null = aucun). */
    String affiche(UUID id) { return affiches.get(id); }

    /** Affiche un titre déjà possédé (boutique, rayon Titres). */
    void afficher(Player p, String titre) {
        if (!possede(p.getUniqueId(), titre)) return;
        affiches.put(p.getUniqueId(), titre);
        sauverTitres();
        OrbitTycoon.msg(p, "<gray>Titre affiché : " + texteTitre(titre));
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.4f);
    }

    private static final class MenuTitres implements InventoryHolder {
        Inventory inv;
        final List<String> ids = new ArrayList<>();
        @Override public Inventory getInventory() { return inv; }
    }

    boolean commande(org.bukkit.command.CommandSender s, String[] args) {
        if (!(s instanceof Player p)) { OrbitTycoon.msg(s, "Commande réservée aux joueurs."); return true; }
        MenuTitres h = new MenuTitres();
        Inventory inv = Bukkit.createInventory(h, 54, Habillage.titre(plugin, p, 6, "Titres"));
        h.inv = inv;
        String actuel = affiches.get(p.getUniqueId());
        int i = 0;
        for (var en : titres().entrySet()) {
            if (i >= Coffres.CASES.length) break;
            boolean a = possede(p.getUniqueId(), en.getKey());
            boolean porte = en.getKey().equals(actuel);
            ItemStack it = new ItemStack(a ? Material.NAME_TAG : Material.GRAY_DYE);
            it.editMeta(m -> {
                m.itemName(MM.deserialize(a ? en.getValue() : "<dark_gray>??? <gray>(verrouillé)"));
                m.lore(List.of(MM.deserialize("<!italic>" + (porte ? "<#7CFC9A>✔ Affiché" : a ? "<#62CFF4>➜ Clic pour l'afficher"
                        : "<gray>À trouver dans les coffres orbitaux."))));
                if (a) m.setItemModel(new NamespacedKey("orbittycoon", "plaque_titre"));
                m.setEnchantmentGlintOverride(porte);
            });
            inv.setItem(Coffres.CASES[i], it);
            h.ids.add(en.getKey());
            i++;
        }
        ItemStack retirer = new ItemStack(Material.BARRIER);
        retirer.editMeta(m -> m.itemName(MM.deserialize("<#FF4D4D>Ne plus afficher de titre")));
        inv.setItem(49, retirer);
        Habillage.fond(plugin, p, inv);
        p.openInventory(inv);
        return true;
    }

    @EventHandler
    public void clicMenu(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof MenuTitres h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != h.inv) return;
        if (e.getSlot() == 49) {
            affiches.remove(p.getUniqueId());
            sauverTitres();
            OrbitTycoon.msg(p, "<gray>Plus de titre affiché.");
            p.closeInventory();
            return;
        }
        for (int i = 0; i < h.ids.size(); i++) {
            if (Coffres.CASES[i] != e.getSlot()) continue;
            String id = h.ids.get(i);
            if (!possede(p.getUniqueId(), id)) { p.playSound(p.getLocation(), Sound.BLOCK_CHEST_LOCKED, 0.6f, 1.2f); return; }
            affiches.put(p.getUniqueId(), id);
            sauverTitres();
            OrbitTycoon.msg(p, "<gray>Titre affiché : " + texteTitre(id));
            p.closeInventory();
            return;
        }
    }

    @EventHandler
    public void glisserMenu(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof MenuTitres) e.setCancelled(true);
    }

    /** Ajoute le titre devant la ligne de chat déjà mise en forme (OrbitDevCore met son format en HIGHEST). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void chat(AsyncChatEvent e) {
        String id = affiches.get(e.getPlayer().getUniqueId());
        if (id == null) return;
        Component titre = MM.deserialize("<dark_gray>[</dark_gray>" + texteTitre(id) + "<dark_gray>]</dark_gray> ");
        ChatRenderer avant = e.renderer();
        e.renderer((source, nom, message, lecteur) -> titre.append(avant.render(source, nom, message, lecteur)));
    }

    private void chargerTitres() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(fichierTitres);
        for (String k : y.getKeys(false)) {
            try {
                UUID id = UUID.fromString(k);
                possedes.put(id, new ArrayList<>(y.getStringList(k + ".possedes")));
                String a = y.getString(k + ".affiche");
                if (a != null && !a.isBlank()) affiches.put(id, a);
            } catch (IllegalArgumentException ignore) { }
        }
    }

    private void sauverTitres() {
        YamlConfiguration y = new YamlConfiguration();
        for (var en : possedes.entrySet()) {
            y.set(en.getKey() + ".possedes", en.getValue());
            y.set(en.getKey() + ".affiche", affiches.get(en.getKey()));
        }
        try { y.save(fichierTitres); } catch (IOException ex) { plugin.getLogger().warning("titres.yml : " + ex.getMessage()); }
    }
}
