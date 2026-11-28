package fr.orbit.survie;

import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * L'arène : duels à deux avec mise en crédits (/duel), paris des spectateurs pendant le compte à rebours
 * (/pari) et tournoi à élimination directe (/tournoi), ouvert par un admin ou tout seul chaque semaine.
 * Un seul combat à la fois dans la fosse. Tant qu'un duel est en cours, seuls les deux duellistes peuvent se
 * frapper ; sans duel, la fosse reste en PvP libre comme avant. On garde toujours son inventaire.
 */
public final class Duels implements Listener, TabExecutor {

    private enum Phase { PARIS, COMBAT }

    private record Defi(UUID de, String deNom, double mise, long expire) {}

    private record Pari(String nom, UUID sur, double montant) {}

    private static final class Combat {
        UUID a, b;
        String nomA, nomB;
        double mise;
        boolean tournoi;
        Phase phase = Phase.PARIS;
        long finPhase;
        final Map<UUID, Pari> paris = new LinkedHashMap<>();

        boolean a(UUID u) {
            return u.equals(a) || u.equals(b);
        }

        UUID autre(UUID u) {
            return u.equals(a) ? b : a;
        }

        String nom(UUID u) {
            return u.equals(a) ? nomA : nomB;
        }
    }

    private final OrbitSurvie plugin;
    private final Protection protection;
    private final Map<UUID, Defi> defis = new HashMap<>();
    private Combat combat;

    // Tournoi
    private boolean inscriptions;
    private boolean tournoi;
    private long finInscriptions, prochainMatch;
    private final Map<UUID, String> inscrits = new LinkedHashMap<>();
    private final Deque<UUID> file = new ArrayDeque<>();
    private final List<UUID> qualifies = new ArrayList<>();
    private UUID dernierPerdant;
    private LocalDate dernierOuvert;

    public Duels(OrbitSurvie plugin, Protection protection) {
        this.plugin = plugin;
        this.protection = protection;
    }

    private double taxe() {
        return plugin.getConfig().getDouble("duels.taxe", 5) / 100.0;
    }

    private World ile() {
        return Bukkit.getWorld(plugin.ile().worldName());
    }

    private boolean surIle(Player p) {
        return p != null && p.isOnline() && p.getWorld().getName().equals(plugin.ile().worldName());
    }

    private void annoncer(String mini) {
        World w = ile();
        if (w != null) {
            w.getPlayers().forEach(p -> p.sendMessage(Msg.raw("<red><bold>ARÈNE</bold> <dark_gray>» <gray>" + mini)));
        }
    }

    private static double montant(String t) {
        try {
            double v = Math.floor(Double.parseDouble(t.replace(',', '.')) * 100) / 100.0;
            return Double.isFinite(v) ? v : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Pendant un duel, seuls les deux duellistes se frappent, et seulement une fois le combat lancé. */
    public boolean autorise(Player attaquant, Player cible) {
        if (combat == null) {
            return true;
        }
        return combat.phase == Phase.COMBAT && combat.a(attaquant.getUniqueId()) && combat.a(cible.getUniqueId());
    }

    public boolean occupe() {
        return combat != null;
    }

    // ------------------------------------------------------------------ Défi

    private void defier(Player p, String pseudo, String miseTexte) {
        Player cible = Bukkit.getPlayerExact(pseudo);
        if (cible == null || cible.equals(p)) {
            Msg.send(p, "<red>Joueur introuvable.");
            return;
        }
        if (!surIle(p) || !surIle(cible)) {
            Msg.send(p, "<red>Vous devez être tous les deux au port (<white>/spawn<red>).");
            return;
        }
        double mise = miseTexte == null ? 0 : montant(miseTexte);
        double max = plugin.getConfig().getDouble("duels.mise-max", 50000);
        if (mise < 0 || mise > max) {
            Msg.send(p, "<red>Mise invalide (0 à " + Msg.nombre(max) + ").");
            return;
        }
        if (mise > 0 && !plugin.eco().active()) {
            Msg.send(p, "<red>Les mises sont indisponibles (base de données injoignable).");
            return;
        }
        defis.put(cible.getUniqueId(), new Defi(p.getUniqueId(), p.getName(), mise, System.currentTimeMillis() + 60_000));
        Msg.send(p, "Défi envoyé à <white>" + cible.getName() + "<gray>" + (mise > 0 ? " (mise : " + Msg.argent(mise) + ")" : "") + ".");
        Msg.send(cible, "<white>" + p.getName() + "<gray> te défie en duel" + (mise > 0 ? ", mise de " + Msg.argent(mise) + " chacun" : ", sans mise")
                + ". <click:run_command:'/duel accepter'><green><bold>[Accepter]</bold></click> "
                + "<click:run_command:'/duel refuser'><red><bold>[Refuser]</bold></click> <dark_gray>(60 s)");
        cible.playSound(cible.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.2f);
    }

    private void accepter(Player p) {
        Defi d = defis.remove(p.getUniqueId());
        Player de = d == null ? null : Bukkit.getPlayer(d.de());
        if (d == null || d.expire() < System.currentTimeMillis() || de == null) {
            Msg.send(p, "<red>Aucun défi en attente.");
            return;
        }
        if (combat != null || tournoi) {
            Msg.send(p, "<red>L'arène est occupée, réessaie après le combat.");
            return;
        }
        if (!surIle(p) || !surIle(de)) {
            Msg.send(p, "<red>Vous devez être tous les deux au port.");
            return;
        }
        if (d.mise() <= 0) {
            lancer(de, p, 0, false);
            return;
        }
        UUID a = de.getUniqueId(), b = p.getUniqueId();
        String nomA = de.getName();
        plugin.db().then(plugin.db().transaction(c -> {
            if (!Economie.retirer(c, a, d.mise())) {
                return 1;
            }
            if (!Economie.retirer(c, b, d.mise())) {
                Economie.crediter(c, a, nomA, d.mise());
                return 2;
            }
            return 0;
        }), r -> {
            if (r != 0) {
                Msg.send(p, "<red>" + (r == 1 ? nomA + " n'a" : "Tu n'as") + " pas assez de crédits pour la mise.");
                Msg.send(de, "<red>Duel annulé : " + (r == 1 ? "tu n'as" : p.getName() + " n'a") + " pas assez de crédits.");
                return;
            }
            if (combat != null || tournoi || !surIle(p) || !surIle(de)) {
                rembourser(a, nomA, d.mise());
                rembourser(b, p.getName(), d.mise());
                Msg.send(p, "<red>Duel annulé, mise rendue.");
                Msg.send(de, "<red>Duel annulé, mise rendue.");
                return;
            }
            lancer(de, p, d.mise(), false);
        }, () -> Msg.send(p, "<red>Duel impossible pour le moment (base de données)."));
    }

    private void rembourser(UUID u, String nom, double montant) {
        if (montant > 0 && plugin.eco().active()) {
            plugin.eco().crediter(u, nom, montant);
        }
    }

    // ------------------------------------------------------------------ Combat

    private Location marque(int cote) {
        var d = plugin.ile().dessin();
        return new Location(ile(), d.areneX() + cote * 8 + 0.5, d.sol() - 2, d.areneZ() + 0.5, cote < 0 ? -90f : 90f, 0f);
    }

    private static void soigner(Player p) {
        AttributeInstance pv = p.getAttribute(Attribute.MAX_HEALTH);
        p.setHealth(pv == null ? 20 : pv.getValue());
        p.setFoodLevel(20);
        p.setFireTicks(0);
    }

    private void lancer(Player a, Player b, double mise, boolean enTournoi) {
        Combat c = new Combat();
        c.a = a.getUniqueId();
        c.b = b.getUniqueId();
        c.nomA = a.getName();
        c.nomB = b.getName();
        c.mise = mise;
        c.tournoi = enTournoi;
        c.finPhase = System.currentTimeMillis() + plugin.getConfig().getInt("duels.paris-secondes", 20) * 1000L;
        combat = c;
        a.teleport(marque(-1));
        b.teleport(marque(1));
        soigner(a);
        soigner(b);
        annoncer((enTournoi ? Msg.OR + "Tournoi<gray> : " : "Duel : ") + "<white>" + c.nomA + " <red>⚔ <white>" + c.nomB
                + (mise > 0 ? "<gray>, mise de " + Msg.argent(mise) + " chacun" : "") + "<gray>. Combat dans <white>"
                + plugin.getConfig().getInt("duels.paris-secondes", 20) + " s<gray> — pariez : <white>/pari <pseudo> <montant>");
    }

    /** Chaque seconde. */
    public void tic() {
        long now = System.currentTimeMillis();
        defis.values().removeIf(d -> d.expire() < now);
        ticTournoi(now);
        Combat c = combat;
        if (c == null) {
            return;
        }
        Player a = Bukkit.getPlayer(c.a), b = Bukkit.getPlayer(c.b);
        if (!surIle(a) || !surIle(b)) {
            if (surIle(a) == surIle(b)) {
                egalite("les deux duellistes sont partis");
            } else {
                finir(surIle(a) ? c.a : c.b, "son adversaire est parti");
            }
            return;
        }
        boolean dedansA = protection.dansArene(a.getLocation()), dedansB = protection.dansArene(b.getLocation());
        if (c.phase == Phase.PARIS) {
            if (!dedansA) {
                a.teleport(marque(-1));
            }
            if (!dedansB) {
                b.teleport(marque(1));
            }
            long reste = (c.finPhase - now + 999) / 1000;
            if (reste <= 0) {
                c.phase = Phase.COMBAT;
                c.finPhase = now + plugin.getConfig().getInt("duels.duree-secondes", 180) * 1000L;
                for (Player p : List.of(a, b)) {
                    p.showTitle(Title.title(Msg.raw("<red><bold>Combat !"), Msg.raw(""),
                            Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(300))));
                    p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.4f);
                }
            } else if (reste <= 5) {
                for (Player p : List.of(a, b)) {
                    p.showTitle(Title.title(Msg.raw(Msg.OR + "<bold>" + reste), Msg.raw("<gray>Prépare-toi"),
                            Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(100))));
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
                }
            }
            return;
        }
        if (!dedansA || !dedansB) {
            if (dedansA == dedansB) {
                egalite("les deux duellistes ont quitté la fosse");
            } else {
                finir(dedansA ? c.a : c.b, "son adversaire a quitté la fosse");
            }
            return;
        }
        if (now >= c.finPhase) {
            if (c.tournoi) {
                finir(a.getHealth() >= b.getHealth() ? c.a : c.b, "temps écoulé, il lui restait le plus de vie");
            } else {
                egalite("temps écoulé");
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMort(PlayerDeathEvent e) {
        Combat c = combat;
        if (c != null && c.a(e.getEntity().getUniqueId())) {
            finir(c.autre(e.getEntity().getUniqueId()), null);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID u = e.getPlayer().getUniqueId();
        defis.remove(u);
        Combat c = combat;
        if (c != null && c.a(u)) {
            finir(c.autre(u), "son adversaire s'est déconnecté");
        }
    }

    private void finir(UUID gagnant, String raison) {
        Combat c = combat;
        if (c == null) {
            return;
        }
        combat = null;
        UUID perdant = c.autre(gagnant);
        String nomG = c.nom(gagnant), nomP = c.nom(perdant);
        double gain = Math.floor(2 * c.mise * (1 - taxe()) * 100) / 100.0;
        rembourser(gagnant, nomG, gain);
        annoncer("<white>" + nomG + "<gray> bat <white>" + nomP + (raison == null ? "" : " <dark_gray>(" + raison + ")")
                + (gain > 0 ? "<gray> et gagne " + Msg.argent(gain) : "") + "<gray> !");
        payerParis(c, gagnant);
        if (c.tournoi || c.mise >= plugin.getConfig().getDouble("duels.mise-classement", 100)) {
            plugin.stats().ajouter(gagnant, nomG, "duels", 1);
        }
        for (UUID u : List.of(c.a, c.b)) {
            Player p = Bukkit.getPlayer(u);
            if (p != null && !p.isDead()) {
                soigner(p);
            }
        }
        Player pg = Bukkit.getPlayer(gagnant);
        if (pg != null) {
            pg.playSound(pg.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.2f);
        }
        if (c.tournoi) {
            qualifies.add(gagnant);
            dernierPerdant = perdant;
            prochainMatch = System.currentTimeMillis() + 10_000;
        }
    }

    private void egalite(String raison) {
        Combat c = combat;
        if (c == null) {
            return;
        }
        combat = null;
        rembourser(c.a, c.nomA, c.mise);
        rembourser(c.b, c.nomB, c.mise);
        c.paris.forEach((u, p) -> rembourser(u, p.nom(), p.montant()));
        annoncer("Duel <white>" + c.nomA + " <red>⚔ <white>" + c.nomB + "<gray> : égalité (" + raison + "). Mises et paris rendus.");
        if (c.tournoi) {
            dernierPerdant = null;
            prochainMatch = System.currentTimeMillis() + 10_000;
        }
    }

    // ------------------------------------------------------------------ Paris

    private void parier(Player p, String pseudo, String montantTexte) {
        Combat c = combat;
        if (c == null || c.phase != Phase.PARIS) {
            Msg.send(p, "<red>Les paris ne sont ouverts que pendant le compte à rebours d'un duel.");
            return;
        }
        if (c.a(p.getUniqueId())) {
            Msg.send(p, "<red>Un duelliste ne parie pas sur son propre combat.");
            return;
        }
        if (c.paris.containsKey(p.getUniqueId())) {
            Msg.send(p, "<red>Tu as déjà parié sur ce combat.");
            return;
        }
        UUID sur = pseudo.equalsIgnoreCase(c.nomA) ? c.a : pseudo.equalsIgnoreCase(c.nomB) ? c.b : null;
        double m = montant(montantTexte);
        double max = plugin.getConfig().getDouble("duels.pari-max", 2000);
        if (sur == null || m < 1 || m > max) {
            Msg.send(p, "<red>Usage : <white>/pari <" + c.nomA + "|" + c.nomB + "> <montant><red> (1 à " + Msg.nombre(max) + ").");
            return;
        }
        if (!plugin.eco().active()) {
            Msg.send(p, "<red>Les paris sont indisponibles (base de données injoignable).");
            return;
        }
        plugin.db().then(plugin.eco().retirer(p.getUniqueId(), m), ok -> {
            if (!ok) {
                Msg.send(p, "<red>Pas assez de crédits.");
                return;
            }
            if (combat != c || c.phase != Phase.PARIS || c.paris.containsKey(p.getUniqueId())) {
                rembourser(p.getUniqueId(), p.getName(), m);
                Msg.send(p, "<red>Trop tard, les paris sont fermés. Mise rendue.");
                return;
            }
            c.paris.put(p.getUniqueId(), new Pari(p.getName(), sur, m));
            annoncer("<white>" + p.getName() + "<gray> parie " + Msg.argent(m) + " sur <white>" + c.nom(sur) + "<gray>.");
        }, () -> Msg.send(p, "<red>Pari impossible pour le moment."));
    }

    /** Pari mutuel : les gagnants se partagent le pot au prorata de leur mise. Sans pari en face : remboursé. */
    private void payerParis(Combat c, UUID gagnant) {
        double pot = 0, bon = 0;
        for (Pari p : c.paris.values()) {
            pot += p.montant();
            if (p.sur().equals(gagnant)) {
                bon += p.montant();
            }
        }
        if (pot <= 0) {
            return;
        }
        if (bon <= 0 || bon >= pot) {
            c.paris.forEach((u, p) -> rembourser(u, p.nom(), p.montant()));
            annoncer("Paris rendus : personne n'avait parié " + (bon <= 0 ? "sur le gagnant." : "en face."));
            return;
        }
        for (Map.Entry<UUID, Pari> e : c.paris.entrySet()) {
            Pari p = e.getValue();
            if (!p.sur().equals(gagnant)) {
                continue;
            }
            double gain = Math.floor(p.montant() * pot / bon * (1 - taxe()) * 100) / 100.0;
            rembourser(e.getKey(), p.nom(), gain);
            Player pl = Bukkit.getPlayer(e.getKey());
            if (pl != null) {
                Msg.send(pl, "Pari gagné : " + Msg.argent(gain) + " !");
            }
        }
        annoncer("Paris : pot de " + Msg.argent(pot) + " partagé entre ceux qui ont misé sur <white>" + c.nom(gagnant) + "<gray>.");
    }

    // ------------------------------------------------------------------ Tournoi

    public void ouvrirTournoi(CommandSender s) {
        if (inscriptions || tournoi) {
            if (s != null) {
                Msg.send(s, "<red>Un tournoi est déjà en cours.");
            }
            return;
        }
        int minutes = Math.max(1, plugin.getConfig().getInt("tournoi.inscription-minutes", 5));
        inscriptions = true;
        inscrits.clear();
        finInscriptions = System.currentTimeMillis() + minutes * 60_000L;
        List<Double> lots = plugin.getConfig().getDoubleList("tournoi.lots");
        Bukkit.broadcast(Msg.msg("<red><bold>TOURNOI DE L'ARÈNE</bold><gray> : inscriptions ouvertes pendant <white>" + minutes
                + " min<gray> ! <click:run_command:'/tournoi rejoindre'><green><bold>[Rejoindre]</bold></click> "
                + (lots.isEmpty() ? "" : "<gray>1er : " + Msg.argent(lots.get(0)) + (lots.size() > 1 ? ", 2e : " + Msg.argent(lots.get(1)) : ""))));
        Bukkit.getOnlinePlayers().forEach(p -> p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 0.6f, 1f));
    }

    private void ticTournoi(long now) {
        // Ouverture automatique chaque semaine (tournoi.jour + tournoi.heure), une fois par jour au plus.
        String jour = plugin.getConfig().getString("tournoi.jour", "");
        if (!jour.isEmpty() && !inscriptions && !tournoi) {
            LocalDateTime t = LocalDateTime.now();
            String heure = String.format("%02d:%02d", t.getHour(), t.getMinute());
            try {
                if (t.getDayOfWeek() == DayOfWeek.valueOf(jour.toUpperCase(Locale.ROOT))
                        && heure.equals(plugin.getConfig().getString("tournoi.heure", "21:00"))
                        && !t.toLocalDate().equals(dernierOuvert)) {
                    dernierOuvert = t.toLocalDate();
                    ouvrirTournoi(null);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (inscriptions && now >= finInscriptions) {
            inscriptions = false;
            int min = Math.max(2, plugin.getConfig().getInt("tournoi.joueurs-min", 4));
            if (inscrits.size() < min) {
                Bukkit.broadcast(Msg.msg("<red>Tournoi annulé<gray> : il fallait au moins " + min + " inscrits (" + inscrits.size() + ")."));
                inscrits.clear();
                return;
            }
            List<UUID> ordre = new ArrayList<>(inscrits.keySet());
            Collections.shuffle(ordre);
            file.clear();
            file.addAll(ordre);
            qualifies.clear();
            dernierPerdant = null;
            tournoi = true;
            prochainMatch = now + 10_000;
            Bukkit.broadcast(Msg.msg("<red><bold>Le tournoi commence</bold><gray> avec <white>" + ordre.size()
                    + "<gray> combattants ! Rendez-vous à l'arène du port (<white>/spawn<gray>)."));
        }
        if (tournoi && combat == null && now >= prochainMatch) {
            matchSuivant();
        }
    }

    private void matchSuivant() {
        if (file.size() >= 2) {
            UUID ua = file.poll(), ub = file.poll();
            Player a = Bukkit.getPlayer(ua), b = Bukkit.getPlayer(ub);
            boolean la = surIle(a), lb = surIle(b);
            if (la && lb) {
                lancer(a, b, 0, true);
            } else if (la || lb) {
                qualifies.add(la ? ua : ub);
                dernierPerdant = null;
                annoncer("<white>" + inscrits.get(la ? ua : ub) + "<gray> passe au tour suivant : <white>"
                        + inscrits.get(la ? ub : ua) + "<gray> n'est pas au port (forfait).");
                prochainMatch = System.currentTimeMillis() + 3000;
            } else {
                annoncer("Match annulé : <white>" + inscrits.get(ua) + "<gray> et <white>" + inscrits.get(ub) + "<gray> sont absents.");
                prochainMatch = System.currentTimeMillis() + 3000;
            }
            return;
        }
        if (file.size() == 1) {
            UUID exempt = file.poll();
            qualifies.add(exempt);
            if (qualifies.size() > 1) {
                annoncer("<white>" + inscrits.get(exempt) + "<gray> est exempté de ce tour.");
            }
        }
        if (qualifies.size() > 1) {
            file.addAll(qualifies);
            qualifies.clear();
            annoncer(Msg.OR + (file.size() == 2 ? "Finale !" : "Tour suivant") + "<gray> — encore <white>" + file.size() + "<gray> combattants.");
            prochainMatch = System.currentTimeMillis() + 8000;
            return;
        }
        // Fin : un seul qualifié (ou plus personne).
        tournoi = false;
        List<Double> lots = plugin.getConfig().getDoubleList("tournoi.lots");
        if (qualifies.isEmpty()) {
            Bukkit.broadcast(Msg.msg("<red>Tournoi terminé sans vainqueur."));
        } else {
            UUID champion = qualifies.get(0);
            String nom = inscrits.getOrDefault(champion, "?");
            if (!lots.isEmpty()) {
                rembourser(champion, nom, lots.get(0));
            }
            Bukkit.broadcast(Msg.msg("<red><bold>TOURNOI</bold><gray> : <white>" + nom + "<gray> remporte le tournoi"
                    + (lots.isEmpty() ? "" : " et " + Msg.argent(lots.get(0))) + "<gray> !"));
            if (dernierPerdant != null && lots.size() > 1) {
                String second = inscrits.getOrDefault(dernierPerdant, "?");
                rembourser(dernierPerdant, second, lots.get(1));
                Bukkit.broadcast(Msg.msg("Finaliste : <white>" + second + "<gray>, " + Msg.argent(lots.get(1)) + "."));
            }
        }
        qualifies.clear();
        file.clear();
        inscrits.clear();
    }

    private void annulerTournoi(CommandSender s) {
        if (!inscriptions && !tournoi) {
            Msg.send(s, "<red>Aucun tournoi en cours.");
            return;
        }
        inscriptions = false;
        tournoi = false;
        file.clear();
        qualifies.clear();
        inscrits.clear();
        if (combat != null && combat.tournoi) {
            egalite("tournoi annulé");
        }
        Bukkit.broadcast(Msg.msg("<red>Le tournoi est annulé par un administrateur."));
    }

    // ------------------------------------------------------------------ PNJ et commandes

    /** Le maître d'arène explique et dit ce qui se passe. */
    public void pnj(Player p) {
        Msg.send(p, "<red><bold>Arène</bold><gray> : PvP seulement dans la fosse, inventaire gardé à la mort.");
        Msg.send(p, "<white>/duel <pseudo> [mise]<gray> : défie un joueur ; le gagnant prend les deux mises (taxe "
                + Msg.nombre(taxe() * 100) + " %).");
        Msg.send(p, "<white>/pari <pseudo> <montant><gray> : parie sur un duelliste pendant le compte à rebours.");
        Msg.send(p, "<white>/tournoi<gray> : " + (inscriptions ? "<green>inscriptions ouvertes<gray>, <white>/tournoi rejoindre"
                : tournoi ? "<green>en cours<gray>." : etatTournoi()));
        if (combat != null) {
            Msg.send(p, "En ce moment : <white>" + combat.nomA + " <red>⚔ <white>" + combat.nomB + "<gray>.");
        }
    }

    private String etatTournoi() {
        String jour = plugin.getConfig().getString("tournoi.jour", "");
        if (jour.isEmpty()) {
            return "ouvert par les admins.";
        }
        String fr = switch (jour.toUpperCase(Locale.ROOT)) {
            case "MONDAY" -> "lundi";
            case "TUESDAY" -> "mardi";
            case "WEDNESDAY" -> "mercredi";
            case "THURSDAY" -> "jeudi";
            case "FRIDAY" -> "vendredi";
            case "SATURDAY" -> "samedi";
            case "SUNDAY" -> "dimanche";
            default -> jour;
        };
        return "chaque <white>" + fr + "<gray> à <white>" + plugin.getConfig().getString("tournoi.heure", "21:00") + "<gray>.";
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        String sub = a.length == 0 ? "" : a[0].toLowerCase(Locale.ROOT);
        if (cmd.getName().equals("tournoi")) {
            if (sub.equals("ouvrir") && s.hasPermission("survie.admin")) {
                ouvrirTournoi(s);
            } else if (sub.equals("annuler") && s.hasPermission("survie.admin")) {
                annulerTournoi(s);
            } else if (sub.equals("rejoindre") && s instanceof Player p) {
                if (!inscriptions) {
                    Msg.send(p, "<red>Les inscriptions ne sont pas ouvertes.");
                } else if (inscrits.putIfAbsent(p.getUniqueId(), p.getName()) == null) {
                    Bukkit.broadcast(Msg.msg("<white>" + p.getName() + "<gray> rejoint le tournoi (<white>" + inscrits.size() + "<gray> inscrits)."));
                    Msg.send(p, "Sois au port quand ton combat est appelé, sinon c'est forfait.");
                } else {
                    Msg.send(p, "<red>Tu es déjà inscrit.");
                }
            } else if (sub.equals("quitter") && s instanceof Player p) {
                Msg.send(p, inscriptions && inscrits.remove(p.getUniqueId()) != null ? "Tu n'es plus inscrit au tournoi."
                        : "<red>Tu n'es pas inscrit (ou le tournoi a déjà commencé).");
            } else {
                Msg.send(s, "Tournoi de l'arène : " + (inscriptions ? "<green>inscriptions ouvertes<gray> (<white>" + inscrits.size()
                        + "<gray> inscrits), <white>/tournoi rejoindre" : tournoi ? "<green>en cours<gray>." : etatTournoi()));
            }
            return true;
        }
        if (!(s instanceof Player p)) {
            return true;
        }
        if (cmd.getName().equals("pari")) {
            if (a.length < 2) {
                Msg.send(p, "<red>Usage : <white>/pari <pseudo> <montant>");
            } else {
                parier(p, a[0], a[1]);
            }
            return true;
        }
        switch (sub) {
            case "" -> pnj(p);
            case "accepter" -> accepter(p);
            case "refuser" -> {
                Defi d = defis.remove(p.getUniqueId());
                Player de = d == null ? null : Bukkit.getPlayer(d.de());
                Msg.send(p, d == null ? "<red>Aucun défi en attente." : "Défi refusé.");
                if (de != null) {
                    Msg.send(de, "<white>" + p.getName() + "<gray> refuse ton défi.");
                }
            }
            default -> defier(p, a[0], a.length > 1 ? a[1] : null);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            switch (cmd.getName()) {
                case "tournoi" -> {
                    out.addAll(List.of("rejoindre", "quitter"));
                    if (s.hasPermission("survie.admin")) {
                        out.addAll(List.of("ouvrir", "annuler"));
                    }
                }
                case "pari" -> {
                    if (combat != null) {
                        out.addAll(List.of(combat.nomA, combat.nomB));
                    }
                }
                default -> {
                    out.addAll(List.of("accepter", "refuser"));
                    Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
                }
            }
        }
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(a[a.length - 1].toLowerCase(Locale.ROOT)));
        return out;
    }
}
