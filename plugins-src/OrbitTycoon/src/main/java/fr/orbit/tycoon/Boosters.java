package fr.orbit.tycoon;

import net.kyori.adventure.bossbar.BossBar;
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
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Boosters orbitaux (lots des coffres) : un objet « Booster » qu'on active d'un clic droit.
 * Trois sortes : Crédits (récoltes + ventes de générateurs), Flux stellaire (récoltes), XP d'outil (récoltes).
 * Un seul booster actif par sorte : un booster du même multiplicateur rallonge la durée, un autre est refusé.
 * Le temps ne s'écoule que connecté (comme sur les serveurs tycoon) ; sauvegardé dans boosters.yml.
 * Affichage : une barre de boss par joueur qui liste les boosters actifs et leur temps restant.
 */
final class Boosters implements Listener {

    enum Sorte {
        CREDITS("credits", "Crédits", "#E0B864", "⛃"),
        FLUX("flux", "Flux stellaire", "#62CFF4", "✦"),
        XP("xp", "XP d'outil", "#7CFC9A", "✧");

        final String id, nom, couleur, symbole;
        Sorte(String id, String nom, String couleur, String symbole) { this.id = id; this.nom = nom; this.couleur = couleur; this.symbole = symbole; }

        static Sorte parse(String s) {
            for (Sorte x : values()) if (x.id.equalsIgnoreCase(s) || x.name().equalsIgnoreCase(s)) return x;
            return null;
        }
    }

    /** Booster actif : multiplicateur et millisecondes restantes (connecté). */
    private static final class Actif {
        final double mult;
        long reste;
        Actif(double mult, long reste) { this.mult = mult; this.reste = reste; }
    }

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private final OrbitTycoon plugin;
    private final NamespacedKey cleSorte, cleMult, cleMinutes;
    private final File fichier;
    private final Map<UUID, EnumMap<Sorte, Actif>> actifs = new HashMap<>();
    private final Map<UUID, BossBar> barres = new HashMap<>();
    private BukkitTask tache;

    Boosters(OrbitTycoon plugin) {
        this.plugin = plugin;
        cleSorte = new NamespacedKey(plugin, "booster_sorte");
        cleMult = new NamespacedKey(plugin, "booster_mult");
        cleMinutes = new NamespacedKey(plugin, "booster_minutes");
        fichier = new File(plugin.getDataFolder(), "boosters.yml");
        for (Player p : Bukkit.getOnlinePlayers()) charger(p.getUniqueId());
        tache = Bukkit.getScheduler().runTaskTimer(plugin, this::seconde, 20L, 20L);
    }

    // ------------------------------------------------------------------ multiplicateurs (Monnaies, Generateurs…)

    /** Multiplicateur actif de cette sorte (1 si aucun, ou si le joueur n'est pas connecté). */
    double mult(UUID id, Sorte s) {
        var m = actifs.get(id);
        Actif a = m == null ? null : m.get(s);
        return a == null || a.reste <= 0 ? 1.0 : a.mult;
    }

    // ------------------------------------------------------------------ objet

    static String fois(double mult) {
        return "×" + (mult == Math.floor(mult) ? Long.toString((long) mult) : String.format(Locale.FRANCE, "%.1f", mult));
    }

    static String duree(int minutes) {
        return minutes >= 60 ? (minutes / 60) + " h" + (minutes % 60 > 0 ? " " + (minutes % 60) + " min" : "") : minutes + " min";
    }

    /** Objet Booster (même sorte + multiplicateur + durée = même objet : ils s'empilent). */
    ItemStack objet(Sorte s, double mult, int minutes, int nombre) {
        ItemStack it = new ItemStack(Material.FIREWORK_STAR, Math.max(1, Math.min(64, nombre)));
        it.editMeta(m -> {
            m.itemName(MM.deserialize("<" + s.couleur + "><bold>Booster " + s.nom + " " + fois(mult) + "</bold>"));
            List<String> l = new ArrayList<>();
            l.add("<dark_gray>Booster orbital");
            l.add("");
            l.add("<gray>Multiplie tes <" + s.couleur + ">" + s.symbole + " " + s.nom + "</" + s.couleur + "> par <white>" + fois(mult));
            l.add(s == Sorte.CREDITS ? "<gray>sur tes récoltes et tes ventes (/vendre, collecteurs)."
                    : "<gray>sur tes récoltes avec l'Outil orbital.");
            l.add("<gray>Durée : <white>" + duree(minutes) + " <dark_gray>(le temps ne file que connecté)");
            l.add("");
            l.add("<#62CFF4>➜ Clic droit pour l'activer");
            l.add("<dark_gray>Même multiplicateur déjà actif : la durée s'ajoute.");
            m.lore(l.stream().map(x -> MM.deserialize("<!italic>" + x)).toList());
            m.setItemModel(new NamespacedKey("orbittycoon", "booster_" + s.id));
            m.setEnchantmentGlintOverride(true);
            var pdc = m.getPersistentDataContainer();
            pdc.set(cleSorte, PersistentDataType.STRING, s.id);
            pdc.set(cleMult, PersistentDataType.DOUBLE, mult);
            pdc.set(cleMinutes, PersistentDataType.INTEGER, minutes);
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
        Sorte s = Sorte.parse(pdc.get(cleSorte, PersistentDataType.STRING));
        if (s == null) return;
        e.setCancelled(true);
        double mult = pdc.getOrDefault(cleMult, PersistentDataType.DOUBLE, 1.0);
        int minutes = pdc.getOrDefault(cleMinutes, PersistentDataType.INTEGER, 0);
        Player p = e.getPlayer();
        var m = actifs.computeIfAbsent(p.getUniqueId(), k -> new EnumMap<>(Sorte.class));
        Actif a = m.get(s);
        if (a != null && a.reste > 0 && a.mult != mult) {
            OrbitTycoon.msg(p, "<gray>Tu as déjà un <" + s.couleur + ">Booster " + s.nom + " " + fois(a.mult) + "</" + s.couleur
                    + "><gray> actif (encore <white>" + horloge(a.reste) + "<gray>). Attends la fin pour en lancer un autre.");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1f);
            return;
        }
        long ajout = minutes * 60_000L;
        if (a != null && a.reste > 0) a.reste += ajout;
        else m.put(s, new Actif(mult, ajout));
        it.setAmount(it.getAmount() - 1);
        OrbitTycoon.msg(p, "<" + s.couleur + "><bold>Booster " + s.nom + " " + fois(mult) + "</bold></" + s.couleur
                + "><gray> activé : <white>" + horloge(m.get(s).reste) + "<gray> de propulsion !");
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.6f);
        p.playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.6f, 1.2f);
        barre(p);
        sauverJoueur(p.getUniqueId());
    }

    // ------------------------------------------------------------------ temps et barre

    private void seconde() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            var m = actifs.get(p.getUniqueId());
            if (m == null || m.isEmpty()) continue;
            var it = m.entrySet().iterator();
            while (it.hasNext()) {
                var en = it.next();
                en.getValue().reste -= 1000;
                if (en.getValue().reste <= 0) {
                    it.remove();
                    Sorte s = en.getKey();
                    OrbitTycoon.msg(p, "<gray>Ton <" + s.couleur + ">Booster " + s.nom + " " + fois(en.getValue().mult)
                            + "</" + s.couleur + "><gray> est épuisé.");
                    p.playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.7f, 1.4f);
                }
            }
            barre(p);
        }
    }

    private static String horloge(long ms) {
        long s = Math.max(0, ms / 1000);
        return s >= 3600 ? String.format("%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) : String.format("%02d:%02d", s / 60, s % 60);
    }

    private void barre(Player p) {
        var m = actifs.get(p.getUniqueId());
        BossBar b = barres.get(p.getUniqueId());
        if (m == null || m.isEmpty()) {
            if (b != null) { p.hideBossBar(b); barres.remove(p.getUniqueId()); }
            return;
        }
        StringBuilder t = new StringBuilder("<#62CFF4>⚡</#62CFF4>");
        long plusLong = 0;
        boolean premier = true;
        for (var en : m.entrySet()) {
            Sorte s = en.getKey();
            if (!premier) t.append("  <dark_gray>|</dark_gray>  ");
            premier = false;
            t.append("<").append(s.couleur).append("><bold>").append(s.nom).append(" ").append(fois(en.getValue().mult))
                    .append("</bold></").append(s.couleur).append("> <white>").append(horloge(en.getValue().reste)).append("</white>");
            plusLong = Math.max(plusLong, en.getValue().reste);
        }
        float prog = (float) Math.max(0, Math.min(1, plusLong / 3_600_000.0));
        if (b == null) {
            b = BossBar.bossBar(MM.deserialize(t.toString()), prog, BossBar.Color.BLUE, BossBar.Overlay.NOTCHED_20);
            barres.put(p.getUniqueId(), b);
            p.showBossBar(b);
        } else { b.name(MM.deserialize(t.toString())); b.progress(prog); }
    }

    // ------------------------------------------------------------------ sauvegarde (boosters.yml)

    private void charger(UUID id) {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(fichier);
        var sec = y.getConfigurationSection(id.toString());
        EnumMap<Sorte, Actif> m = new EnumMap<>(Sorte.class);
        if (sec != null) for (String k : sec.getKeys(false)) {
            Sorte s = Sorte.parse(k);
            long reste = sec.getLong(k + ".reste");
            if (s != null && reste > 0) m.put(s, new Actif(sec.getDouble(k + ".mult", 1), reste));
        }
        actifs.put(id, m);
    }

    private void sauverJoueur(UUID id) {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(fichier);
        y.set(id.toString(), null);
        var m = actifs.get(id);
        if (m != null) for (var en : m.entrySet()) {
            y.set(id + "." + en.getKey().id + ".mult", en.getValue().mult);
            y.set(id + "." + en.getKey().id + ".reste", en.getValue().reste);
        }
        try { y.save(fichier); } catch (IOException ex) { plugin.getLogger().warning("boosters.yml : " + ex.getMessage()); }
    }

    @EventHandler
    public void connexion(PlayerJoinEvent e) {
        charger(e.getPlayer().getUniqueId());
        barre(e.getPlayer());
    }

    @EventHandler
    public void deconnexion(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        sauverJoueur(id);
        actifs.remove(id);
        barres.remove(id);
    }

    void arreter() {
        if (tache != null) tache.cancel();
        for (UUID id : new ArrayList<>(actifs.keySet())) sauverJoueur(id);
        for (var en : barres.entrySet()) {
            Player p = Bukkit.getPlayer(en.getKey());
            if (p != null) p.hideBossBar(en.getValue());
        }
        barres.clear();
    }
}
