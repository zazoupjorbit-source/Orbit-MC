package fr.orbit.tycoon;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static fr.orbit.tycoon.Navigateur.texte;

/**
 * Niveaux, XP et prestige de chaque outil, plus la fiche d'outil (Shift + clic droit → clic droit sur un outil).
 * - Chaque ressource récoltée donne de l'XP à l'outil de sa zone (barème progression.xp dans config.yml).
 * - Niveaux 1 → niveau-max ; au niveau max, l'XP ne monte plus : il faut passer prestige.
 * - Passer prestige coûte des Crédits orbitaux (cout-base × (prestige + 1)), remet l'outil au niveau 1
 *   et ajoute 1 au prestige de l'outil et au prestige total.
 */
final class Progression implements Listener {

    private static final int SLOT_OUTIL = 4, SLOT_NIVEAU = 20, SLOT_PRESTIGE = 22, SLOT_PASSER = 24,
            SLOT_TITRE_AMELIO = 31, SLOT_RETOUR = 45, SLOT_EQUIPER = 49;
    private static final int PREMIERE_AMELIO = 36, NB_AMELIO = 9;

    private static final class Fiche implements InventoryHolder {
        private Inventory inv;
        private Outil.Mode mode;
        @Override public Inventory getInventory() { return inv; }
    }

    private final OrbitTycoon plugin;

    Progression(OrbitTycoon plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ réglages

    private FileConfiguration cfg() { return plugin.getConfig(); }

    int niveauMax() { return Math.max(1, cfg().getInt("progression.niveau-max", 100)); }

    int prestigeMax() { return Math.max(1, cfg().getInt("progression.prestige-max", 100)); }

    /** XP à accumuler pour passer du niveau {@code niveau} au suivant, selon le prestige de l'outil. */
    long xpRequis(int niveau, int prestige) {
        double base = cfg().getDouble("progression.xp-base", 25);
        double parNiveau = cfg().getDouble("progression.xp-par-niveau", 5);
        double bonus = cfg().getDouble("progression.xp-bonus-prestige", 0.10);
        return Math.max(1, Math.round((base + parNiveau * (niveau - 1)) * (1 + bonus * prestige)));
    }

    /** Coût en Crédits orbitaux pour passer du prestige {@code prestige} au suivant. */
    long coutPrestige(int prestige) {
        return Math.max(0, cfg().getLong("progression.prestige-cout-base", 10_000)) * (prestige + 1L);
    }

    // ------------------------------------------------------------------ XP

    /**
     * Donne l'XP d'une récolte à l'outil ; renvoie le texte à ajouter à la barre d'action (null si rien).
     */
    String gagner(Player p, Outil.Mode outil, String cle, int quantite) {
        return gagner(p, outil, (long) cfg().getInt("progression.xp.ressources." + cle, cfg().getInt("progression.xp.defaut", 1)) * quantite);
    }

    /** Donne {@code gain} XP à l'outil (ressource choisie dans le panneau Ressources). */
    String gagner(Player p, Outil.Mode outil, long gain) {
        UUID id = p.getUniqueId();
        Monnaies mon = plugin.monnaies();
        int max = niveauMax();
        int niveau = Math.min(mon.niveau(id, outil), max);
        if (niveau >= max) return "<#E0B864>✪ Niveau max <dark_gray>(<gray>prestige !<dark_gray>)";
        if (gain <= 0) return null;
        int prestige = mon.prestige(id, outil);
        long xp = mon.xp(id, outil) + gain;
        int avant = niveau;
        while (niveau < max && xp >= xpRequis(niveau, prestige)) {
            xp -= xpRequis(niveau, prestige);
            niveau++;
        }
        if (niveau >= max) xp = 0;
        mon.definirNiveau(id, outil, niveau, xp);
        if (niveau > avant) niveauGagne(p, outil, niveau, niveau >= max);
        return "<#3DDCB0>+" + gain + " XP <" + outil.couleur + ">" + outil.nom;
    }

    private void niveauGagne(Player p, Outil.Mode outil, int niveau, boolean max) {
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);
        p.showTitle(Title.title(texte("<" + outil.couleur + "><bold>" + outil.nom + "</bold>"),
                texte(max ? "<#E0B864>Niveau max ! <gray>Passe prestige" : "<gray>Niveau <#3DDCB0><bold>" + niveau),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1500), Duration.ofMillis(400))));
        if (max) OrbitTycoon.msg(p, "Ton outil <" + outil.couleur + ">" + outil.nom + "</" + outil.couleur + "> est au <#E0B864>niveau max</#E0B864> ! "
                + "<gray>Ouvre sa fiche (<#E0B864>Shift + clic droit</#E0B864>, puis clic droit sur l'outil) pour passer prestige.");
    }

    // ------------------------------------------------------------------ fiche

    void ouvrir(Player p, Outil.Mode m) {
        UUID id = p.getUniqueId();
        Monnaies mon = plugin.monnaies();
        Fiche h = new Fiche();
        h.mode = m;
        Inventory inv = Bukkit.createInventory(h, 54, Habillage.titre(plugin, p, 6, "Fiche · " + m.nom));
        h.inv = inv;

        int niveau = Math.min(mon.niveau(id, m), niveauMax());
        int prestige = mon.prestige(id, m);
        boolean max = niveau >= niveauMax();
        long xp = mon.xp(id, m), requis = xpRequis(niveau, prestige);

        inv.setItem(SLOT_OUTIL, objet(m.type, "<" + m.couleur + "><bold>" + m.nom, true,
                "<gray>Zone : <" + m.couleur + ">" + m.zone,
                "<gray>Récolte : <white>" + m.recolte));
        inv.setItem(SLOT_NIVEAU, objet(Material.EXPERIENCE_BOTTLE, "<#3DDCB0><bold>Niveau " + niveau + " <dark_gray>/ " + niveauMax(), false,
                max ? "<#E0B864>✪ Niveau maximum atteint" : barre(xp, requis),
                max ? "<gray>Passe prestige pour continuer." : "<gray>XP : <white>" + Hud.nombre(xp) + " <dark_gray>/ <white>" + Hud.nombre(requis),
                "",
                "<dark_gray>Chaque ressource récoltée dans la zone",
                "<dark_gray>" + m.zone + " donne de l'XP à cet outil."));
        inv.setItem(SLOT_PRESTIGE, objet(Material.NETHER_STAR, "<#E0B864><bold>Prestige ✪ " + prestige + " <dark_gray>/ " + prestigeMax(), prestige > 0,
                "<gray>Prestige de l'outil : <#E0B864>✪ " + prestige,
                "<gray>Prestige total : <#E0B864>✪ " + mon.prestige(id),
                "",
                "<dark_gray>Chaque prestige rend l'outil un peu",
                "<dark_gray>plus long à monter (+" + Math.round(cfg().getDouble("progression.xp-bonus-prestige", 0.10) * 100) + " % d'XP)."));
        inv.setItem(SLOT_PASSER, boutonPrestige(id, m, niveau, prestige));
        inv.setItem(SLOT_TITRE_AMELIO, objet(Material.ANVIL, "<#E0B864><bold>Améliorations", false,
                "<gray>Cases d'amélioration de l'outil.", "<dark_gray>Bientôt disponibles."));
        List<Map<?, ?>> cases = ameliorations(m);
        for (int i = 0; i < NB_AMELIO; i++) inv.setItem(PREMIERE_AMELIO + i, caseAmelioration(i, i < cases.size() ? cases.get(i) : null));
        inv.setItem(SLOT_RETOUR, objet(Material.ARROW, "<#62CFF4>← Retour", false, "<gray>Revenir à l'Outil orbital."));
        boolean enMain = m == plugin.outil().modeActuel(p);
        inv.setItem(SLOT_EQUIPER, objet(m.type, enMain ? "<#E0B864>● En main" : "<#62CFF4>➜ Équiper " + m.nom, enMain,
                enMain ? "<gray>C'est l'outil que tu tiens." : "<gray>Clic pour passer ton Outil orbital", enMain ? "" : "<gray>en " + m.nom + "."));
        Habillage.fond(plugin, p, inv);
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.5f, 1.4f);
    }

    private ItemStack boutonPrestige(UUID id, Outil.Mode m, int niveau, int prestige) {
        if (prestige >= prestigeMax())
            return objet(Material.BEACON, "<#E0B864><bold>Prestige maximum", true, "<gray>Ton " + m.nom + " est au sommet : ✪ " + prestige + " !");
        long cout = coutPrestige(prestige);
        boolean niveauOk = niveau >= niveauMax();
        boolean argentOk = plugin.monnaies().solde(id, Monnaies.Monnaie.CREDIT) >= cout;
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Passe ton " + m.nom + " au prestige <#E0B864>✪ " + (prestige + 1) + "<gray>.");
        lore.add("");
        lore.add((niveauOk ? "<#7CFC9A>✔" : "<#FF4D4D>✘") + " <gray>Niveau " + niveauMax() + " <dark_gray>(actuel : " + niveau + ")");
        lore.add((argentOk ? "<#7CFC9A>✔" : "<#FF4D4D>✘") + " <gray>Coût : <#E0B864>" + Hud.nombre(cout) + " ⛃ Crédits orbitaux");
        lore.add("");
        lore.add("<dark_gray>L'outil repart au niveau 1.");
        lore.add(niveauOk && argentOk ? "<#E0B864>➜ Clic pour passer prestige" : "<#FF4D4D>Conditions pas encore remplies");
        return objet(niveauOk && argentOk ? Material.BEACON : Material.GRAY_DYE, "<#E0B864><bold>Passer prestige", niveauOk && argentOk,
                lore.toArray(String[]::new));
    }

    /** Cases d'amélioration de l'outil (progression.ameliorations.<outil>, sinon .defaut). */
    private List<Map<?, ?>> ameliorations(Outil.Mode m) {
        List<Map<?, ?>> l = cfg().getMapList("progression.ameliorations." + m.id);
        return l.isEmpty() ? cfg().getMapList("progression.ameliorations.defaut") : l;
    }

    private ItemStack caseAmelioration(int i, Map<?, ?> c) {
        String nom = c != null && c.get("nom") != null ? String.valueOf(c.get("nom")) : "Amélioration " + (i + 1);
        String description = c != null && c.get("description") != null ? String.valueOf(c.get("description")) : "À définir";
        String prerequis = c != null && c.get("prerequis") != null ? String.valueOf(c.get("prerequis")) : "À définir";
        Material mat = c != null && c.get("objet") != null ? Material.matchMaterial(String.valueOf(c.get("objet"))) : null;
        return objet(mat == null ? Material.GRAY_DYE : mat, "<#62CFF4><bold>" + nom, false,
                "<gray>" + description,
                "",
                "<gray>Prérequis : <white>" + prerequis,
                "<dark_gray>🔒 Bientôt disponible");
    }

    private static String barre(long xp, long requis) {
        int plein = (int) Math.min(20, xp * 20 / Math.max(1, requis));
        int pct = (int) Math.min(100, xp * 100 / Math.max(1, requis));
        return "<#3DDCB0>" + "|".repeat(plein) + "<dark_gray>" + "|".repeat(20 - plein) + " <white>" + pct + " %";
    }

    static ItemStack objet(Material mat, String nom, boolean brille, String... lignes) {
        ItemStack it = new ItemStack(mat);
        it.editMeta(meta -> {
            meta.displayName(texte(nom));
            List<Component> lore = new ArrayList<>();
            for (String l : lignes) lore.add(l.isEmpty() ? Component.empty() : texte(l));
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            if (brille) meta.setEnchantmentGlintOverride(true);
        });
        return it;
    }

    // ------------------------------------------------------------------ prestige

    private void demanderPrestige(Player p, Outil.Mode m) {
        UUID id = p.getUniqueId();
        Monnaies mon = plugin.monnaies();
        int prestige = mon.prestige(id, m);
        String refus = refus(id, m);
        if (refus != null) {
            OrbitTycoon.msg(p, refus);
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.8f);
            return;
        }
        long cout = coutPrestige(prestige);
        plugin.confirmer().ouvrir(p, "Passer prestige ?",
                objet(Material.NETHER_STAR, "<" + m.couleur + "><bold>" + m.nom + " <#E0B864>✪ " + prestige + " → ✪ " + (prestige + 1), true),
                List.of("<gray>Coût : <#E0B864>" + Hud.nombre(cout) + " ⛃ Crédits orbitaux", "<gray>L'outil repart au niveau 1."),
                () -> passerPrestige(p, m), () -> ouvrir(p, m));
    }

    /** Raison pour laquelle le prestige est impossible, ou null s'il est possible. */
    private String refus(UUID id, Outil.Mode m) {
        Monnaies mon = plugin.monnaies();
        int prestige = mon.prestige(id, m);
        if (prestige >= prestigeMax()) return "Ton " + m.nom + " est déjà au prestige maximum.";
        if (mon.niveau(id, m) < niveauMax()) return "Ton " + m.nom + " doit d'abord atteindre le <#3DDCB0>niveau " + niveauMax() + "</#3DDCB0>.";
        if (mon.solde(id, Monnaies.Monnaie.CREDIT) < coutPrestige(prestige))
            return "Il te faut <#E0B864>" + Hud.nombre(coutPrestige(prestige)) + " ⛃ Crédits orbitaux</#E0B864> pour passer prestige.";
        return null;
    }

    private void passerPrestige(Player p, Outil.Mode m) {
        UUID id = p.getUniqueId();
        Monnaies mon = plugin.monnaies();
        // On revérifie tout : le solde a pu changer pendant la confirmation.
        String refus = refus(id, m);
        int prestige = mon.prestige(id, m);
        if (refus != null || !mon.retirer(id, Monnaies.Monnaie.CREDIT, coutPrestige(prestige))) {
            OrbitTycoon.msg(p, refus != null ? refus : "Pas assez de Crédits orbitaux.");
            return;
        }
        int nouveau = prestige + 1;
        mon.definirPrestige(id, m, nouveau);
        mon.definirPrestige(id, mon.prestige(id) + 1);
        mon.definirNiveau(id, m, 1, 0);
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f);
        p.showTitle(Title.title(texte("<#E0B864><bold>✪ PRESTIGE " + nouveau + " ✪"),
                texte("<" + m.couleur + ">" + m.nom + " <gray>repart au niveau 1"),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2500), Duration.ofMillis(500))));
        OrbitTycoon.msg(p, "Ton <" + m.couleur + ">" + m.nom + "</" + m.couleur + "> passe au <#E0B864>prestige ✪ " + nouveau + "</#E0B864> !");
        int pas = Math.max(1, cfg().getInt("quetes.pas", 10));
        if (nouveau % pas == 0) {
            for (Player o : Bukkit.getOnlinePlayers()) if (o != p)
                OrbitTycoon.msg(o, "<white>" + p.getName() + "</white> a passé son <" + m.couleur + ">" + m.nom + "</" + m.couleur
                        + "> au <#E0B864>prestige ✪ " + nouveau + "</#E0B864> !");
            OrbitTycoon.msg(p, "Une <#62CFF4>quête de prestige</#62CFF4> t'attend chez l'Aventurier (/quetes).");
        }
        ouvrir(p, m);
    }

    // ------------------------------------------------------------------ clics

    @EventHandler(priority = EventPriority.HIGHEST)
    public void clic(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Fiche h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getView().getTopInventory()) return;
        switch (e.getSlot()) {
            case SLOT_PASSER -> demanderPrestige(p, h.mode);
            case SLOT_RETOUR -> plugin.outil().ouvrir(p);
            case SLOT_EQUIPER -> {
                if (h.mode != plugin.outil().modeActuel(p)) plugin.outil().equiper(p, h.mode);
                ouvrir(p, h.mode);
            }
            default -> {
                if (e.getSlot() >= PREMIERE_AMELIO && e.getSlot() < PREMIERE_AMELIO + NB_AMELIO)
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.8f);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void glisser(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Fiche) e.setCancelled(true);
    }
}
