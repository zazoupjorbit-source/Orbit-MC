package fr.orbit.contrebande;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Les nuits en cours (une par organisation, une ville par nuit), les organisations (invitations, départs) et la
 * fin de nuit : bilan, sauvegarde, records et Orbit Coins.
 */
final class Sessions implements Listener {

    record Invitation(int orga, String nom, String de, long expire) {
    }

    private final OrbitContrebande plugin;
    private final Map<UUID, Nuit> joueurs = new HashMap<>();
    private final Nuit[] slots;
    private final Map<UUID, Invitation> invitations = new HashMap<>();
    private final Set<UUID> chargement = new HashSet<>();
    private BukkitTask task;

    Sessions(OrbitContrebande plugin) {
        this.plugin = plugin;
        slots = new Nuit[Math.max(1, Math.min(8, plugin.getConfig().getInt("places", 4)))];
    }

    void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    void stopAll() {
        for (Nuit n : all()) {
            finir(n, null, false);
        }
        if (task != null) {
            task.cancel();
        }
    }

    Nuit nuit(Player p) {
        return joueurs.get(p.getUniqueId());
    }

    /** Le pack vient d'arriver (ou de partir) : objets et décors du joueur remis à jour s'il est en pleine nuit. */
    void habiller(Player p) {
        Nuit n = nuit(p);
        if (n != null && !n.ended) {
            n.habiller(p);
        }
    }

    boolean playing(Player p) {
        return joueurs.containsKey(p.getUniqueId());
    }

    List<Nuit> all() {
        List<Nuit> l = new ArrayList<>();
        for (Nuit n : slots) {
            if (n != null) {
                l.add(n);
            }
        }
        return l;
    }

    Nuit nuitDe(Orga o) {
        for (Nuit n : slots) {
            if (n != null && !n.ended && n.orga == o) {
                return n;
            }
        }
        return null;
    }

    boolean slotBusy(int slot) {
        return slot >= 0 && slot < slots.length && slots[slot] != null;
    }

    int places() {
        return slots.length;
    }

    // ------------------------------------------------------------------ organisation du joueur

    /** Charge l'organisation du joueur puis appelle {@code then} (sur le fil du serveur, joueur toujours connecté). */
    void orga(Player p, Consumer<Orga> then) {
        UUID id = p.getUniqueId();
        if (!chargement.add(id)) {
            return;
        }
        plugin.store().charger(id, p.getName(), o -> {
            chargement.remove(id);
            if (!p.isOnline()) {
                return;
            }
            if (o == null) {
                Txt.send(p, "<#E0484A>La base des organisations ne répond pas, réessaie dans un instant.");
                return;
            }
            o.membres.put(id, p.getName());
            then.accept(o);
        });
    }

    // ------------------------------------------------------------------ borne

    /** Clic sur la borne : menu d'accueil. */
    void accueil(Player p) {
        if (playing(p)) {
            return;
        }
        if (!plugin.npc().near(p)) {
            Txt.send(p, "<#9AA4B2>CONTREBANDE se joue depuis la salle d'arcade cachée de La Station.");
            return;
        }
        plugin.pack().envoyerSiBesoin(p);
        orga(p, o -> Menus.accueil(plugin, p, o));
    }

    void play(Player p) {
        if (playing(p)) {
            Txt.send(p, "<#E0484A>Tu es déjà aux Docks (<#E6EDF3>/contrebande quitter</#E6EDF3> pour rentrer).");
            return;
        }
        if (!plugin.npc().near(p)) {
            Txt.send(p, "<#9AA4B2>CONTREBANDE se joue depuis la salle d'arcade cachée de La Station.");
            return;
        }
        orga(p, o -> lancer(p, o));
    }

    private void lancer(Player p, Orga o) {
        if (playing(p) || !plugin.npc().near(p)) {
            return;
        }
        Nuit n = nuitDe(o);
        if (n != null) {
            joindre(p, n);
            return;
        }
        int slot = -1, enChantier = -1;
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == null) {
                if (plugin.villePrete(i)) {
                    slot = i;
                    break;
                }
                enChantier = i;
            }
        }
        if (slot < 0) {
            if (enChantier >= 0) {
                Txt.send(p, "<#E0B864>La ville se construit encore</#E0B864> <#9AA4B2>(" + plugin.pourcent(enChantier) + " %) : réessaie dans un instant.");
            } else {
                Txt.send(p, "<#E0484A>Toutes les villes sont occupées par d'autres organisations, réessaie dans un instant.");
            }
            return;
        }
        n = new Nuit(plugin, o, slot);
        slots[slot] = n;
        try {
            n.start();
        } catch (RuntimeException e) {
            plugin.getLogger().warning("CONTREBANDE : démarrage impossible : " + e);
            e.printStackTrace();
            n.ended = true;
            try {
                n.cleanup();
            } catch (RuntimeException ignored) {
                // tant pis
            }
            slots[slot] = null;
            Txt.send(p, "<#E0484A>La nuit n'a pas pu commencer (erreur notée dans la console).");
            return;
        }
        joindre(p, n);
        for (UUID m : o.membres.keySet()) {
            Player pm = Bukkit.getPlayer(m);
            if (pm != null && pm != p && !playing(pm)) {
                Txt.send(pm, "<#E0B864>" + p.getName() + "</#E0B864> a lancé une nuit pour <#E6EDF3>" + o.nom
                        + "</#E6EDF3>. <#9AA4B2>Va à la borne de la salle d'arcade pour le rejoindre.");
                pm.sendMessage(Txt.mm("  ").append(Txt.button("Rejoindre", "#7CFF8A", "/contrebande jouer", "À lancer devant la borne")));
                pm.playSound(pm.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.4f);
            }
        }
    }

    private void joindre(Player p, Nuit n) {
        joueurs.put(p.getUniqueId(), n);
        try {
            n.rejoindre(p);
        } catch (RuntimeException e) {
            plugin.getLogger().warning("CONTREBANDE : arrivée impossible : " + e);
            e.printStackTrace();
            joueurs.remove(p.getUniqueId());
            n.partir(p, true);
        }
    }

    // ------------------------------------------------------------------ tick et fin

    private void tick() {
        for (Nuit n : all()) {
            if (n.ended) {
                continue;
            }
            try {
                n.tick();
            } catch (RuntimeException e) {
                plugin.getLogger().warning("CONTREBANDE : " + e);
                e.printStackTrace();
                finir(n, null, true);
            }
        }
    }

    /** Un joueur rentre avant l'aube : s'il était seul, la nuit se termine. */
    void quitter(Player p, boolean volontaire) {
        Nuit n = joueurs.get(p.getUniqueId());
        if (n == null) {
            return;
        }
        if (n.joueurs.size() <= 1) {
            finir(n, volontaire ? "<#E0B864><b>Retour à la Station</b>" : null, true);
            return;
        }
        joueurs.remove(p.getUniqueId());
        n.partir(p, true);
        n.broadcast("<#9AA4B2>" + p.getName() + " rentre à la Station.");
        if (p.isOnline()) {
            Txt.send(p, "<#9AA4B2>Tu rentres : ce que tu portais est au coffre, ton organisation continue la nuit sans toi.");
            buttons(p);
        }
    }

    void finir(Nuit n, String headline) {
        finir(n, headline, true);
    }

    /** Fin de la nuit pour toute l'organisation. {@code recompenses} : faux à l'arrêt du serveur. */
    void finir(Nuit n, String headline, boolean recompenses) {
        if (n.ended) {
            return;
        }
        n.ended = true;
        List<Player> presents = new ArrayList<>();
        for (Nuit.Joueur j : new ArrayList<>(n.joueurs.values())) {
            presents.add(j.p);
            joueurs.remove(j.p.getUniqueId(), n);
            try {
                n.partir(j.p, true);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("CONTREBANDE : départ incomplet de " + j.p.getName() + " : " + e);
            }
        }
        List<String> lignes = List.of();
        try {
            lignes = n.bilanOrga();
        } catch (RuntimeException e) {
            plugin.getLogger().warning("CONTREBANDE : bilan incomplet : " + e);
        }
        try {
            n.cleanup();
        } catch (RuntimeException e) {
            plugin.getLogger().warning("CONTREBANDE : nettoyage incomplet : " + e);
            e.printStackTrace();
        }
        if (n.slot >= 0 && n.slot < slots.length && slots[n.slot] == n) {
            slots[n.slot] = null;
        }
        plugin.store().sauver(n.orga);
        if (!recompenses || presents.isEmpty()) {
            return;
        }
        Orga o = n.orga;
        int nb = presents.size();
        int ocChacun = (int) Math.round(n.gains / (double) Math.max(1, plugin.getConfig().getInt("oc-diviseur", 10)) / nb * (nb > 1 ? 1.25 : 1.0));
        for (Player p : presents) {
            if (!p.isOnline()) {
                continue;
            }
            if (headline != null) {
                Txt.title(p, headline, "<#E6EDF3>+" + Txt.n(n.gains) + " cr <#5A6270>·</#5A6270> " + o.titre(), 2600);
                p.playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.7f, 1.3f);
            }
            Txt.send(p, "<#E0B864><b>Bilan de la nuit " + o.nuits + "</b></#E0B864> <#5A6270>·</#5A6270> <#E6EDF3>" + o.nom);
            p.sendMessage(Txt.mm("  <#9AA4B2>Livraisons : <#E6EDF3>" + n.ventes + "</#E6EDF3> · gagné " + Txt.cr(n.gains)
                    + " <#9AA4B2>· saisies <#E6EDF3>" + n.saisies + "</#E6EDF3> · alertes <#E6EDF3>" + n.alertes));
            for (String l : lignes) {
                p.sendMessage(Txt.mm("  " + l));
            }
            p.sendMessage(Txt.mm("  <#9AA4B2>Caisse : " + Txt.cr(o.credits) + " <#9AA4B2>· fortune " + Txt.cr(o.fortune)
                    + " <#9AA4B2>· " + o.titre() + " " + Txt.stars(o.etoiles())));
            recompense(p, o.fortune, ocChacun);
        }
    }

    private void recompense(Player p, long fortune, int ocWanted) {
        int cap = plugin.getConfig().getInt("plafond-oc-jour", 600);
        plugin.store().record(p.getUniqueId(), p.getName(), fortune, Math.max(0, ocWanted), cap, res -> {
            if (!p.isOnline()) {
                return;
            }
            if (res == null) {
                p.sendMessage(Txt.mm("  <#9AA4B2>(base injoignable : ni record ni Orbit Coins cette fois)"));
                buttons(p);
                return;
            }
            boolean record = fortune > res.best() && fortune > 0;
            p.sendMessage(Txt.mm("  <#9AA4B2>Ta part : " + (res.oc() > 0 ? Txt.oc(res.oc()) : "<#9AA4B2>0 OC")
                    + (res.capped() ? " <#9AA4B2>(plafond de " + Txt.n(cap) + " OC par jour)" : "")
                    + (record ? "  <#7CFF8A><b>NOUVEAU RECORD !</b>" : "")));
            buttons(p);
            if (record) {
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.2f);
            }
        });
    }

    void buttons(Player p) {
        p.sendMessage(Txt.mm("  ").append(Txt.button("Nuit suivante", "#E0B864", "/contrebande jouer", "Retourner aux Docks (devant la borne)"))
                .append(Txt.mm(" ")).append(Txt.button("Classement", "#62CFF4", "/contrebande top", "Les plus grosses fortunes"))
                .append(Txt.mm(" ")).append(Txt.button("Organisation", "#C79BFF", "/contrebande orga", "Ton organisation")));
    }

    void showTop(CommandSender to) {
        plugin.store().top(10, list -> {
            Txt.send(to, "Les plus grosses fortunes d'Orbite");
            if (list.isEmpty()) {
                to.sendMessage(Txt.mm("  <#9AA4B2>Aucune organisation n'a encore fait fortune."));
            }
            int i = 1;
            for (String[] row : list) {
                String color = i == 1 ? "#E0B864" : i <= 3 ? "#C9D4E0" : "#9AA4B2";
                to.sendMessage(Txt.mm("  <" + color + ">" + i++ + ".</" + color + "> <#E6EDF3>" + row[0] + "</#E6EDF3> "
                        + Txt.cr(Long.parseLong(row[1])) + " <#5A6270>·</#5A6270> <#9AA4B2>" + row[2] + " · " + row[3] + " membre(s)"));
            }
        });
    }

    // ------------------------------------------------------------------ commandes d'organisation

    void orgaAide(Player p) {
        Txt.send(p, "<#C79BFF><b>Organisation</b></#C79BFF> <#9AA4B2>(jusqu'à " + Orga.MAX_MEMBRES + " membres, tout est partagé)");
        p.sendMessage(Txt.mm("  <#E6EDF3>/contrebande orga</#E6EDF3> <#9AA4B2>: infos et membres"));
        p.sendMessage(Txt.mm("  <#E6EDF3>/contrebande orga inviter <pseudo></#E6EDF3> <#9AA4B2>: inviter un ami (chef)"));
        p.sendMessage(Txt.mm("  <#E6EDF3>/contrebande orga accepter</#E6EDF3> <#9AA4B2>: rejoindre l'organisation qui t'invite"));
        p.sendMessage(Txt.mm("  <#E6EDF3>/contrebande orga renommer <nom></#E6EDF3> <#9AA4B2>: changer le nom (chef)"));
        p.sendMessage(Txt.mm("  <#E6EDF3>/contrebande orga chef <pseudo></#E6EDF3> <#9AA4B2>: passer la main (chef)"));
        p.sendMessage(Txt.mm("  <#E6EDF3>/contrebande orga exclure <pseudo></#E6EDF3> <#9AA4B2>: retirer un membre (chef)"));
        p.sendMessage(Txt.mm("  <#E6EDF3>/contrebande orga quitter</#E6EDF3> <#9AA4B2>: partir et repartir de zéro"));
    }

    void commandeOrga(Player p, String[] args) {
        String sub = args.length < 2 ? "" : args[1].toLowerCase(Locale.ROOT);
        String cible = args.length < 3 ? null : args[2];
        switch (sub) {
            case "", "info" -> orga(p, o -> info(p, o));
            case "inviter" -> orga(p, o -> inviter(p, o, cible));
            case "accepter" -> accepter(p);
            case "refuser" -> {
                Invitation inv = invitations.remove(p.getUniqueId());
                Txt.send(p, inv == null ? "<#9AA4B2>Aucune invitation en attente." : "<#9AA4B2>Invitation de " + inv.nom() + " refusée.");
            }
            case "quitter" -> orga(p, o -> partirOrga(p, o));
            case "exclure" -> orga(p, o -> exclure(p, o, cible));
            case "chef" -> orga(p, o -> nommerChef(p, o, cible));
            case "renommer" -> {
                StringBuilder sb = new StringBuilder();
                for (int i = 2; i < args.length; i++) {
                    sb.append(i > 2 ? " " : "").append(args[i]);
                }
                String nom = sb.toString().trim().replaceAll("\\s+", " ");
                orga(p, o -> renommer(p, o, nom));
            }
            default -> orgaAide(p);
        }
    }

    private void info(Player p, Orga o) {
        Txt.send(p, "<#E0B864><b>" + o.nom + "</b></#E0B864> <#5A6270>·</#5A6270> <#E0B864>" + o.titre() + "</#E0B864> " + Txt.stars(o.etoiles()));
        p.sendMessage(Txt.mm("  <#9AA4B2>Caisse " + Txt.cr(o.credits) + " <#9AA4B2>· fortune " + Txt.cr(o.fortune) + " <#9AA4B2>· " + o.nuits
                + " nuit(s) · réputation " + o.rep + " · avis de recherche " + o.chaleur + " %"));
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<UUID, String> m : o.membres.entrySet()) {
            if (sb.length() > 0) {
                sb.append("<#5A6270>, </#5A6270>");
            }
            boolean en = Bukkit.getPlayer(m.getKey()) != null;
            sb.append(en ? "<#7CFF8A>" : "<#9AA4B2>").append(m.getValue()).append(m.getKey().equals(o.chef) ? " <#E0B864>★" : "");
        }
        p.sendMessage(Txt.mm("  <#9AA4B2>Membres (" + o.membres.size() + "/" + Orga.MAX_MEMBRES + ") : " + sb));
        Nuit n = nuitDe(o);
        if (n != null) {
            p.sendMessage(Txt.mm("  <#7CFF8A>Une nuit est en cours (" + n.joueurs.size() + " membre(s), " + Txt.clock((n.duree - n.tick) / 20)
                    + " avant l'aube).</#7CFF8A>"));
        }
        if (o.chef.equals(p.getUniqueId()) && o.membres.size() < Orga.MAX_MEMBRES) {
            p.sendMessage(Txt.mm("  <#9AA4B2>Invite un ami : <#E6EDF3>/contrebande orga inviter <pseudo>"));
        }
    }

    private boolean chef(Player p, Orga o) {
        if (!o.chef.equals(p.getUniqueId())) {
            Txt.send(p, "<#E0484A>Seul le chef de l'organisation peut faire ça</#E0484A> <#9AA4B2>(" + o.membres.getOrDefault(o.chef, "?") + ").");
            return false;
        }
        return true;
    }

    private void inviter(Player p, Orga o, String cible) {
        if (!chef(p, o)) {
            return;
        }
        if (cible == null) {
            Txt.send(p, "<#9AA4B2>Usage : /contrebande orga inviter <pseudo>");
            return;
        }
        if (o.membres.size() >= Orga.MAX_MEMBRES) {
            Txt.send(p, "<#E0484A>L'organisation est complète (" + Orga.MAX_MEMBRES + " membres).");
            return;
        }
        Player t = Bukkit.getPlayerExact(cible);
        if (t == null) {
            Txt.send(p, "<#E0484A>" + cible + " n'est pas connecté sur ce serveur.");
            return;
        }
        if (t == p || o.membres.containsKey(t.getUniqueId())) {
            Txt.send(p, "<#9AA4B2>" + t.getName() + " fait déjà partie de " + o.nom + ".");
            return;
        }
        invitations.put(t.getUniqueId(), new Invitation(o.id, o.nom, p.getName(), System.currentTimeMillis() + 120_000));
        Txt.send(p, "<#7CFF8A>Invitation envoyée à " + t.getName() + "</#7CFF8A> <#9AA4B2>(valable 2 minutes).");
        Txt.send(t, "<#E0B864>" + p.getName() + "</#E0B864> t'invite dans son organisation <#E6EDF3>" + o.nom + "</#E6EDF3> <#9AA4B2>("
                + o.titre() + ", " + o.membres.size() + " membre(s)). Caisse, coffre et améliorations seront partagés.");
        t.sendMessage(Txt.mm("  ").append(Txt.button("Accepter", "#7CFF8A", "/contrebande orga accepter", "Rejoindre " + o.nom))
                .append(Txt.mm(" ")).append(Txt.button("Refuser", "#E0484A", "/contrebande orga refuser", "Ignorer l'invitation")));
        t.playSound(t.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.7f, 1.2f);
    }

    private void accepter(Player p) {
        Invitation inv = invitations.remove(p.getUniqueId());
        if (inv == null || inv.expire() < System.currentTimeMillis()) {
            Txt.send(p, "<#9AA4B2>Aucune invitation valable en attente.");
            return;
        }
        if (playing(p)) {
            Txt.send(p, "<#E0484A>Rentre d'abord à la Station</#E0484A> <#9AA4B2>(fin de ta nuit), puis accepte à nouveau.");
            invitations.put(p.getUniqueId(), inv);
            return;
        }
        Orga o = plugin.store().parId(inv.orga());
        if (o == null) {
            Txt.send(p, "<#9AA4B2>Cette organisation n'est plus disponible.");
            return;
        }
        orga(p, ancienne -> {
            if (ancienne == o) {
                Txt.send(p, "<#9AA4B2>Tu fais déjà partie de " + o.nom + ".");
                return;
            }
            if (o.membres.size() >= Orga.MAX_MEMBRES) {
                Txt.send(p, "<#E0484A>" + o.nom + " est déjà complète.");
                return;
            }
            if (nuitDe(ancienne) != null) {
                Txt.send(p, "<#E0484A>Ton organisation actuelle est en pleine nuit</#E0484A> <#9AA4B2>: attends l'aube.");
                invitations.put(p.getUniqueId(), inv);
                return;
            }
            boolean seul = ancienne.membres.size() <= 1;
            String ancienNom = ancienne.nom;
            plugin.store().rejoindre(o, p.getUniqueId(), p.getName(), () -> {
                if (p.isOnline()) {
                    Txt.send(p, "<#7CFF8A>Bienvenue dans " + o.nom + " !</#7CFF8A> <#9AA4B2>"
                            + (seul ? "« " + ancienNom + " » est abandonnée (elle reste au classement)." : "Tu as quitté « " + ancienNom + " »."));
                    p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.4f);
                }
                for (UUID m : o.membres.keySet()) {
                    Player pm = Bukkit.getPlayer(m);
                    if (pm != null && pm != p) {
                        Txt.send(pm, "<#7CFF8A>" + p.getName() + " rejoint l'organisation.");
                    }
                }
                Nuit n = nuitDe(o);
                if (n != null) {
                    n.broadcast("<#7CFF8A>" + p.getName() + " rejoint l'organisation</#7CFF8A> <#9AA4B2>et peut vous retrouver depuis la borne.");
                }
            });
        });
    }

    private void partirOrga(Player p, Orga o) {
        if (playing(p)) {
            Txt.send(p, "<#E0484A>Rentre d'abord à la Station.");
            return;
        }
        if (o.membres.size() <= 1) {
            Txt.send(p, "<#9AA4B2>Tu es seul dans « " + o.nom + " » : il n'y a rien à quitter. Invite des amis ou renomme-la !");
            return;
        }
        plugin.store().quitter(o, p.getUniqueId());
        Txt.send(p, "<#9AA4B2>Tu as quitté « " + o.nom + " ». Ta prochaine nuit commencera avec une organisation neuve.");
        for (UUID m : o.membres.keySet()) {
            Player pm = Bukkit.getPlayer(m);
            if (pm != null) {
                Txt.send(pm, "<#9AA4B2>" + p.getName() + " a quitté l'organisation."
                        + (m.equals(o.chef) ? " <#E0B864>Tu en es maintenant le chef." : ""));
            }
        }
    }

    private UUID membre(Orga o, String nom) {
        if (nom == null) {
            return null;
        }
        for (Map.Entry<UUID, String> m : o.membres.entrySet()) {
            if (m.getValue().equalsIgnoreCase(nom)) {
                return m.getKey();
            }
        }
        return null;
    }

    private void exclure(Player p, Orga o, String cible) {
        if (!chef(p, o)) {
            return;
        }
        UUID id = membre(o, cible);
        if (id == null || id.equals(p.getUniqueId())) {
            Txt.send(p, "<#9AA4B2>Usage : /contrebande orga exclure <pseudo d'un membre>");
            return;
        }
        Player t = Bukkit.getPlayer(id);
        if (t != null && playing(t)) {
            Txt.send(p, "<#E0484A>" + t.getName() + " est aux Docks</#E0484A> <#9AA4B2>: attends l'aube.");
            return;
        }
        String nom = o.membres.get(id);
        plugin.store().quitter(o, id);
        Txt.send(p, "<#9AA4B2>" + nom + " ne fait plus partie de « " + o.nom + " ».");
        if (t != null) {
            Txt.send(t, "<#E0484A>Tu as été exclu de « " + o.nom + " ».</#E0484A> <#9AA4B2>Ta prochaine nuit commencera avec une organisation neuve.");
        }
    }

    private void nommerChef(Player p, Orga o, String cible) {
        if (!chef(p, o)) {
            return;
        }
        UUID id = membre(o, cible);
        if (id == null || id.equals(p.getUniqueId())) {
            Txt.send(p, "<#9AA4B2>Usage : /contrebande orga chef <pseudo d'un membre>");
            return;
        }
        o.chef = id;
        plugin.store().sauver(o);
        Txt.send(p, "<#9AA4B2>" + o.membres.get(id) + " est maintenant le chef de « " + o.nom + " ».");
        Player t = Bukkit.getPlayer(id);
        if (t != null) {
            Txt.send(t, "<#E0B864>Tu es maintenant le chef de « " + o.nom + " ».");
        }
    }

    private void renommer(Player p, Orga o, String nom) {
        if (!chef(p, o)) {
            return;
        }
        if (!nom.matches("[\\p{L}\\p{N} '\\-]{3,32}")) {
            Txt.send(p, "<#9AA4B2>Usage : /contrebande orga renommer <nom> <#5A6270>(3 à 32 caractères : lettres, chiffres, espaces, ' et -)");
            return;
        }
        String ancien = o.nom;
        plugin.store().renommer(o, nom, ok -> {
            if (!p.isOnline()) {
                return;
            }
            if (ok) {
                Txt.send(p, "<#7CFF8A>« " + ancien + " » s'appelle désormais « " + nom + " ».");
            } else {
                Txt.send(p, "<#E0484A>Ce nom est déjà pris par une autre organisation.");
            }
        });
    }

    // ------------------------------------------------------------------ événements

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Nuit n = joueurs.get(event.getPlayer().getUniqueId());
        if (n == null || n.ended) {
            return;
        }
        // jamais d'usage normal (alambic, haut fourneau, coffre de l'Ender, pupitre…)
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action a = event.getAction();
        Player p = event.getPlayer();
        if ((a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK) && Produit.estCom(event.getItem())) {
            n.communicateur(p);
        } else if ((a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK) && Produit.estSkate(event.getItem())) {
            n.skate(p);
        } else if (a == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            n.clicBloc(p, event.getClickedBlock());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Nuit n = joueurs.get(event.getPlayer().getUniqueId());
        if (n == null || n.ended) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() == EquipmentSlot.HAND) {
            n.clicEntite(event.getPlayer(), event.getRightClicked());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractAt(PlayerInteractAtEntityEvent event) {
        if (joueurs.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Menus.Holder h)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() == null || !(event.getWhoClicked() instanceof Player p)) {
            return;
        }
        boolean haut = event.getClickedInventory().equals(event.getView().getTopInventory());
        try {
            h.click(p, event.getRawSlot(), event.getClick(), haut, event.getSlot());
        } catch (RuntimeException e) {
            plugin.getLogger().warning("CONTREBANDE : menu : " + e);
            e.printStackTrace();
            p.closeInventory();
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menus.Holder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrop(PlayerDropItemEvent event) {
        if (joueurs.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (joueurs.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /** Clic gauche sur un agent de la douane : le coup est pour lui (les entités de la nuit restent invulnérables). */
    @EventHandler
    public void onAttaque(io.papermc.paper.event.player.PrePlayerAttackEntityEvent event) {
        Nuit n = joueurs.get(event.getPlayer().getUniqueId());
        if (n != null && event.getAttacked().getPersistentDataContainer().has(plugin.key(), PersistentDataType.BYTE)) {
            event.setCancelled(true);
            n.douane.coup(event.getPlayer(), event.getAttacked());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player p && joueurs.containsKey(p.getUniqueId())) {
            event.setCancelled(true);
        } else if (event.getEntity().getPersistentDataContainer().has(plugin.key(), PersistentDataType.BYTE)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player p && joueurs.containsKey(p.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        invitations.remove(event.getPlayer().getUniqueId());
        quitter(event.getPlayer(), false);
    }

    /** Après un arrêt brutal pendant une nuit : inventaire et mode de jeu rendus, retour à l'arcade. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        if (playing(p)) {
            return;
        }
        boolean restored = Etat.restaurerFichier(plugin, p);
        if (restored || p.getWorld().equals(plugin.world())) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline()) {
                    return;
                }
                if (p.getWorld().equals(plugin.world())) {
                    Location back = plugin.npc().front();
                    p.teleport(back != null ? back : Bukkit.getWorlds().get(0).getSpawnLocation());
                }
                if (restored) {
                    Txt.send(p, "<#9AA4B2>Ta dernière nuit aux Docks a été interrompue : ton inventaire t'a été rendu.");
                }
            }, 20L);
        }
    }

    /** Téléporté ailleurs par autre chose (boussole, /spawn…) : le joueur rentre proprement. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Nuit n = joueurs.get(event.getPlayer().getUniqueId());
        if (n == null || n.ended || event.getTo() == null || n.allows(event.getTo())) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (joueurs.get(event.getPlayer().getUniqueId()) == n) {
                quitter(event.getPlayer(), false);
            }
        });
    }
}
