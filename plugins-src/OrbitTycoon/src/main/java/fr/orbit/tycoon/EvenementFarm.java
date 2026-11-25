package fr.orbit.tycoon;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Événement de farm automatique : toutes les « intervalle-minutes », un outil tiré au hasard
 * (jamais deux fois le même de suite) ; pendant « duree-minutes », celui qui récolte le plus avec cet outil gagne.
 * - « annonce-minutes » avant : barre en haut de l'écran (sous la Vote Party) avec le compte à rebours.
 * - Pendant l'événement : HUD à GAUCHE de l'écran (top 3 + son propre total) pour les joueurs qui ont le pack
 *   (titre de la barre décalé avec les polices orbittycoon:evt_0..5 et les espaces négatifs de orbittycoon:nav) ;
 *   sans le pack, le même résumé tient sur une ligne dans la barre.
 * - À la fin : clés dans l'inventaire des 3 premiers (config evenement-farm.recompenses). Joueur parti ou
 *   inventaire plein : les clés attendent dans evenement-attente.yml et sont données à la connexion.
 * Le cycle repart de zéro à chaque démarrage du serveur (un événement en cours est perdu au redémarrage).
 */
final class EvenementFarm implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Key NAV = Key.key("orbittycoon", "nav");
    private static final int LIGNES = 6;

    private enum Etat { ATTENTE, ANNONCE, EN_COURS }

    private final OrbitTycoon plugin;
    private final File fichierAttente;
    private final Map<UUID, BossBar> barres = new HashMap<>();
    /** Score de l'événement en cours (ordre d'arrivée gardé : à égalité, le premier arrivé passe devant). */
    private final Map<UUID, Long> scores = new LinkedHashMap<>();
    private final Map<UUID, String> noms = new HashMap<>();
    /** Clés en attente : uuid → (n° de coffre → nombre). */
    private final Map<UUID, Map<Integer, Integer>> attente = new HashMap<>();

    private BukkitTask tache;
    private Etat etat = Etat.ATTENTE;
    private Outil.Mode outil, precedent;
    private long debut, fin; // millisecondes
    private List<Map.Entry<UUID, Long>> classement = List.of();

    EvenementFarm(OrbitTycoon plugin) {
        this.plugin = plugin;
        this.fichierAttente = new File(plugin.getDataFolder(), "evenement-attente.yml");
    }

    // ------------------------------------------------------------------ cycle

    void demarrer() {
        arreter();
        chargerAttente();
        if (!actif()) return;
        etat = Etat.ATTENTE;
        debut = System.currentTimeMillis() + intervalle();
        tache = Bukkit.getScheduler().runTaskTimer(plugin, this::seconde, 20L, 20L);
    }

    /** /tycoon reload : garde le cycle (et l'événement en cours) ; coupe ou relance seulement si « actif » a changé. */
    void recharger() {
        if (!actif()) arreter();
        else if (tache == null) demarrer();
    }

    void arreter() {
        if (tache != null) { tache.cancel(); tache = null; }
        cacherTout();
        etat = Etat.ATTENTE;
        scores.clear();
        classement = List.of();
    }

    private boolean actif() { return plugin.getConfig().getBoolean("evenement-farm.actif", true); }
    private long intervalle() { return Math.max(2, plugin.getConfig().getInt("evenement-farm.intervalle-minutes", 20)) * 60_000L; }
    private long duree() { return Math.max(1, plugin.getConfig().getInt("evenement-farm.duree-minutes", 5)) * 60_000L; }
    private long annonce() { return Math.max(0, plugin.getConfig().getInt("evenement-farm.annonce-minutes", 5)) * 60_000L; }

    private void seconde() {
        long now = System.currentTimeMillis();
        switch (etat) {
            case ATTENTE -> {
                if (now >= debut - annonce()) {
                    if (outil == null) outil = tirer();
                    etat = Etat.ANNONCE;
                    for (Player p : Bukkit.getOnlinePlayers())
                        OrbitTycoon.msg(p, "<#E0B864><bold>Événement de farm</bold> <gray>dans <white>" + minutes(debut - now)
                                + "<gray> : <" + outil.couleur + "><bold>" + outil.nom + "</bold><gray>. Prépare-toi !");
                }
            }
            case ANNONCE -> { if (now >= debut) commencer(now); }
            case EN_COURS -> { if (now >= fin) { terminer(); return; } }
        }
        afficher(now);
    }

    /** Outil au hasard parmi evenement-farm.outils, différent du précédent quand c'est possible. */
    private Outil.Mode tirer() {
        List<Outil.Mode> l = new ArrayList<>();
        for (String s : plugin.getConfig().getStringList("evenement-farm.outils")) {
            Outil.Mode m = Outil.Mode.parse(s);
            if (m != null && !l.contains(m)) l.add(m);
        }
        if (l.isEmpty()) l.addAll(List.of(Outil.Mode.values()));
        if (l.size() > 1) l.remove(precedent);
        return l.get(ThreadLocalRandom.current().nextInt(l.size()));
    }

    private void commencer(long now) {
        if (outil == null) outil = tirer();
        etat = Etat.EN_COURS;
        fin = now + duree();
        scores.clear();
        noms.clear();
        classement = List.of();
        for (Player p : Bukkit.getOnlinePlayers()) {
            OrbitTycoon.msg(p, "<#E0B864><bold>ÉVÉNEMENT DE FARM !</bold> <gray>Récolte le plus de ressources à la <"
                    + outil.couleur + "><bold>" + outil.nom + "</bold><gray> en <white>" + minutes(duree())
                    + "<gray>. Top 3 : <#F2C46D>1 Clé Saturne<gray>, <#FF8A3D>2 Clés Event<gray>, <#FF8A3D>1 Clé Event<gray>.");
            p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 0.6f, 1.2f);
        }
        plugin.getLogger().info("Événement de farm lancé : " + outil.nom + ".");
    }

    private void terminer() {
        List<Map.Entry<UUID, Long>> top = trier();
        List<String> recompenses = plugin.getConfig().getStringList("evenement-farm.recompenses");
        StringBuilder annonce = new StringBuilder("<#E0B864><bold>Fin de l'événement " + outil.nom + "</bold>");
        if (top.isEmpty()) annonce.append(" <gray>: personne n'a récolté, pas de gagnant.");
        for (int i = 0; i < Math.min(3, top.size()); i++) {
            UUID id = top.get(i).getKey();
            annonce.append("\n <#E0B864>").append(i + 1).append(". <white>").append(MM.escapeTags(noms.getOrDefault(id, "?")))
                    .append(" <gray>— ").append(Hud.nombre(top.get(i).getValue())).append(" ressources");
            if (i < recompenses.size()) {
                String[] r = recompenses.get(i).trim().split("\\s+");
                try {
                    int coffre = Math.max(0, Math.min(5, Integer.parseInt(r[0]) - 1));
                    int nombre = r.length > 1 ? Math.max(1, Integer.parseInt(r[1])) : 1;
                    attente.computeIfAbsent(id, k -> new HashMap<>()).merge(coffre, nombre, Integer::sum);
                    annonce.append(" <dark_gray>→ <#C77DFF>").append(nombre).append(" Clé").append(nombre > 1 ? "s " : " ")
                            .append(MM.escapeTags(plugin.coffres().nom(coffre)));
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("evenement-farm.recompenses : ligne illisible « " + recompenses.get(i) + " » (attendu « <n° coffre> <nombre> »).");
                }
            }
        }
        sauverAttente();
        for (Player p : Bukkit.getOnlinePlayers()) {
            OrbitTycoon.msg(p, annonce.toString());
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1f);
        }
        for (int i = 0; i < Math.min(3, top.size()); i++) {
            Player j = Bukkit.getPlayer(top.get(i).getKey());
            if (j != null) donnerAttente(j);
        }
        plugin.getLogger().info("Événement de farm " + outil.nom + " terminé (" + scores.size() + " participants).");
        precedent = outil;
        outil = null;
        etat = Etat.ATTENTE;
        debut += intervalle();
        if (debut <= System.currentTimeMillis()) debut = System.currentTimeMillis() + intervalle();
        scores.clear();
        classement = List.of();
        cacherTout();
    }

    /** Appelé à chaque récolte (Monnaies) : compte si l'événement tourne et que c'est le bon outil. */
    void compter(Player p, Outil.Mode m, long quantite) {
        if (etat != Etat.EN_COURS || m != outil || quantite <= 0) return;
        scores.merge(p.getUniqueId(), quantite, Long::sum);
        noms.put(p.getUniqueId(), p.getName());
    }

    private List<Map.Entry<UUID, Long>> trier() {
        List<Map.Entry<UUID, Long>> l = new ArrayList<>(scores.entrySet());
        l.sort((a, b) -> Long.compare(b.getValue(), a.getValue())); // tri stable : égalité → premier arrivé
        return l;
    }

    // ------------------------------------------------------------------ commandes admin

    /** /tycoon evenement : état, lancer [outil], arreter. */
    String commande(String[] args) {
        String act = args.length > 1 ? args[1].toLowerCase() : "";
        long now = System.currentTimeMillis();
        switch (act) {
            case "lancer" -> {
                if (etat == Etat.EN_COURS) return "Un événement est déjà en cours.";
                if (tache == null) tache = Bukkit.getScheduler().runTaskTimer(plugin, this::seconde, 20L, 20L);
                Outil.Mode m = args.length > 2 ? Outil.Mode.parse(args[2]) : null;
                outil = m != null ? m : tirer();
                debut = now;
                commencer(now);
                afficher(now);
                return "Événement " + outil.nom + " lancé pour " + minutes(duree()) + ".";
            }
            case "arreter" -> {
                if (etat != Etat.EN_COURS) return "Aucun événement en cours.";
                fin = now;
                seconde();
                return "Événement terminé, récompenses distribuées.";
            }
            default -> {
                if (tache == null) return "Événements de farm désactivés (config : evenement-farm.actif).";
                return switch (etat) {
                    case EN_COURS -> "Événement " + outil.nom + " en cours, fin dans <white>" + minutes(fin - now) + "<gray>.";
                    case ANNONCE -> "Prochain événement (" + outil.nom + ") dans <white>" + minutes(debut - now) + "<gray>.";
                    case ATTENTE -> "Prochain événement dans <white>" + minutes(debut - now) + "<gray>. Tester : /tycoon evenement lancer [outil]";
                };
            }
        }
    }

    // ------------------------------------------------------------------ affichage

    private void afficher(long now) {
        if (etat == Etat.ATTENTE) { cacherTout(); return; }
        if (etat == Etat.EN_COURS) classement = trier();
        for (Player p : Bukkit.getOnlinePlayers()) {
            BossBar b = barres.get(p.getUniqueId());
            Component titre = titre(p, now);
            float prog = progression(now);
            BossBar.Color couleur = etat == Etat.ANNONCE ? BossBar.Color.YELLOW : BossBar.Color.BLUE;
            if (b == null) {
                b = BossBar.bossBar(titre, prog, couleur, BossBar.Overlay.PROGRESS);
                barres.put(p.getUniqueId(), b);
                p.showBossBar(b);
            } else {
                b.name(titre);
                b.progress(prog);
                b.color(couleur);
            }
        }
    }

    private float progression(long now) {
        float f = etat == Etat.ANNONCE ? (debut - now) / (float) Math.max(1, annonce()) : (fin - now) / (float) duree();
        return Math.max(0f, Math.min(1f, f));
    }

    private Component titre(Player p, long now) {
        String nomOutil = "<" + outil.couleur + "><bold>" + outil.nom + "</bold>";
        if (etat == Etat.ANNONCE)
            return MM.deserialize("<#E0B864><bold>Événement de farm</bold> <dark_gray>• " + nomOutil
                    + " <gray>dans <white>" + chrono(debut - now));
        String centre = "<#E0B864><bold>Événement</bold> " + nomOutil + " <dark_gray>• <gray>fin dans <white>" + chrono(fin - now);
        UUID id = p.getUniqueId();
        int rang = -1;
        for (int i = 0; i < classement.size(); i++) if (classement.get(i).getKey().equals(id)) { rang = i + 1; break; }
        long moi = scores.getOrDefault(id, 0L);
        if (!plugin.pack().charge(p)) {
            // Sans pack : pas de décalage possible, résumé sur une ligne.
            String s = centre;
            if (!classement.isEmpty()) s += " <dark_gray>• <#E0B864>1er <white>" + MM.escapeTags(noms.getOrDefault(classement.get(0).getKey(), "?"))
                    + " <gray>(" + Hud.nombre(classement.get(0).getValue()) + ")";
            s += " <dark_gray>• <gray>Toi : <white>" + Hud.nombre(moi) + (rang > 0 ? " <gray>(" + rang + "e)" : "");
            return MM.deserialize(s);
        }
        // Avec pack : le titre centré + un bloc de lignes calées à gauche de l'écran.
        List<String> lignes = new ArrayList<>(); // texte MiniMessage
        List<String> bruts = new ArrayList<>();  // même texte sans balises (pour la largeur)
        List<Boolean> gras = new ArrayList<>();
        ajouter(lignes, bruts, gras, "<#E0B864>TOP 3 - " + outil.nom.toUpperCase(), "TOP 3 - " + outil.nom.toUpperCase(), true);
        String[] couleurs = {"#F2C46D", "#D7E3EA", "#E39B5B"};
        for (int i = 0; i < 3; i++) {
            if (i < classement.size()) {
                String nom = noms.getOrDefault(classement.get(i).getKey(), "?");
                String n = Hud.nombre(classement.get(i).getValue());
                boolean toi = i + 1 == rang;
                ajouter(lignes, bruts, gras, "<" + couleurs[i] + ">" + (i + 1) + ". " + (toi ? "<#62CFF4>" : "<white>") + MM.escapeTags(nom)
                        + " <gray>" + n, (i + 1) + ". " + nom + " " + n, false);
            } else ajouter(lignes, bruts, gras, "<dark_gray>" + (i + 1) + ". ---", (i + 1) + ". ---", false);
        }
        if (rang < 1 || rang > 3) {
            String r = rang > 0 ? " (" + rang + "e)" : "";
            ajouter(lignes, bruts, gras, "<gray>Toi : <#62CFF4>" + Hud.nombre(moi) + "<gray>" + r, "Toi : " + Hud.nombre(moi) + r, false);
        }
        ajouter(lignes, bruts, gras, "<dark_gray>" + outil.ressource, outil.ressource, false);

        Component centreC = MM.deserialize(centre);
        int w = largeur(MM.stripTags(centre), false) + largeurGras(centre);
        int decalage = Math.max(0, plugin.getConfig().getInt("evenement-farm.hud-decalage", 225));
        // Avance totale = 0 : le client centre donc sur le point de départ, ce qui rend les positions fixes.
        Component c = Component.text(espaces(-w / 2)).font(NAV).color(NamedTextColor.WHITE)
                .append(centreC)
                .append(Component.text(espaces(-(w - w / 2) - decalage)).font(NAV));
        for (int i = 0; i < Math.min(LIGNES, lignes.size()); i++) {
            int lw = largeur(bruts.get(i), gras.get(i));
            Component l = MM.deserialize(gras.get(i) ? "<bold>" + lignes.get(i) : lignes.get(i))
                    .font(Key.key("orbittycoon", "evt_" + i));
            c = c.append(l).append(Component.text(espaces(-lw)).font(NAV));
        }
        return c.append(Component.text(espaces(decalage)).font(NAV));
    }

    private static void ajouter(List<String> l, List<String> b, List<Boolean> g, String mm, String brut, boolean gras) {
        l.add(mm); b.add(brut); g.add(gras);
    }

    /** Pixels en plus des parties en gras du titre centré (« <bold>…</bold> »). */
    private static int largeurGras(String mm) {
        int total = 0, i = 0;
        while ((i = mm.indexOf("<bold>", i)) >= 0) {
            int j = mm.indexOf("</bold>", i);
            if (j < 0) break;
            total += MM.stripTags(mm.substring(i, j)).length();
            i = j;
        }
        return total;
    }

    /** Largeur (pixels GUI) d'un texte dans la police vanilla : avance de chaque caractère, +1 en gras. */
    static int largeur(String s, boolean gras) {
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            w += switch (c) {
                case '!', ',', '.', ':', ';', '|', 'i', '\'' -> 2;
                case 'l', '`' -> 3;
                case ' ', 'I', 't', '[', ']', '"' -> 4;
                case 'f', 'k', '<', '>', '(', ')', '{', '}', '*' -> 5;
                case '@', '~' -> 7;
                default -> 6;
            } + (gras ? 1 : 0);
        }
        return w;
    }

    /** Espaces de largeur px (négatif = recul) avec les caractères .. / .. de la police orbittycoon:nav. */
    private static String espaces(int px) {
        StringBuilder b = new StringBuilder();
        int base = px < 0 ? 0xF810 : 0xF820;
        px = Math.abs(px);
        for (int k = 7; k >= 0; k--) while (px >= (1 << k)) { b.append((char) (base + k)); px -= 1 << k; }
        return b.toString();
    }

    private static String chrono(long ms) {
        long s = Math.max(0, (ms + 999) / 1000);
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }

    private static String minutes(long ms) {
        long s = Math.max(0, Math.round(ms / 1000.0));
        return s % 60 == 0 ? (s / 60) + " min" : (s / 60) + " min " + (s % 60) + " s";
    }

    private void cacherTout() {
        for (Map.Entry<UUID, BossBar> e : barres.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null) p.hideBossBar(e.getValue());
        }
        barres.clear();
    }

    @EventHandler
    public void deconnexion(PlayerQuitEvent e) {
        BossBar b = barres.remove(e.getPlayer().getUniqueId());
        if (b != null) e.getPlayer().hideBossBar(b);
    }

    // ------------------------------------------------------------------ clés gagnées

    @EventHandler
    public void connexion(PlayerJoinEvent e) {
        Player j = e.getPlayer();
        if (attente.containsKey(j.getUniqueId()))
            Bukkit.getScheduler().runTaskLater(plugin, () -> { if (j.isOnline()) donnerAttente(j); }, 60L);
    }

    /** Donne les clés gagnées ; ce qui ne rentre pas dans l'inventaire reste en attente. */
    private void donnerAttente(Player j) {
        Map<Integer, Integer> m = attente.get(j.getUniqueId());
        if (m == null) return;
        boolean plein = false;
        for (var it = m.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            int reste = 0;
            for (ItemStack r : j.getInventory().addItem(plugin.coffres().cle(e.getKey(), e.getValue())).values()) reste += r.getAmount();
            int donnees = e.getValue() - reste;
            if (donnees > 0)
                OrbitTycoon.msg(j, "<#C77DFF>+" + donnees + " Clé" + (donnees > 1 ? "s " : " ") + MM.escapeTags(plugin.coffres().nom(e.getKey()))
                        + "<gray> dans ton inventaire (événement de farm).");
            if (reste > 0) { e.setValue(reste); plein = true; }
            else it.remove();
        }
        if (m.isEmpty()) attente.remove(j.getUniqueId());
        if (plein) OrbitTycoon.msg(j, "<red>Inventaire plein : des clés de l'événement t'attendent (rien n'est perdu). "
                + "Fais de la place puis reconnecte-toi.");
        else j.playSound(j.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);
        sauverAttente();
    }

    private void chargerAttente() {
        attente.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(fichierAttente);
        for (String k : y.getKeys(false)) {
            try {
                UUID id = UUID.fromString(k);
                var s = y.getConfigurationSection(k);
                if (s == null) continue;
                Map<Integer, Integer> m = new HashMap<>();
                for (String c : s.getKeys(false)) if (s.getInt(c) > 0) m.put(Integer.parseInt(c), s.getInt(c));
                if (!m.isEmpty()) attente.put(id, m);
            } catch (IllegalArgumentException ignore) { }
        }
    }

    private void sauverAttente() {
        YamlConfiguration y = new YamlConfiguration();
        attente.forEach((id, m) -> m.forEach((c, n) -> y.set(id + "." + c, n)));
        try { y.save(fichierAttente); }
        catch (IOException e) { plugin.getLogger().warning("Événement de farm : impossible d'enregistrer evenement-attente.yml (" + e.getMessage() + ")"); }
    }
}
