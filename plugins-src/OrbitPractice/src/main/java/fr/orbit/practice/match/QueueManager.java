package fr.orbit.practice.match;

import fr.orbit.practice.EloStore;
import fr.orbit.practice.Ladder;
import fr.orbit.practice.Lobby;
import fr.orbit.practice.Mode;
import fr.orbit.practice.Msg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Files d'attente classées (une par type de combat et par format), équipes de 2 pour le 2v2,
 * et défis directs sans Elo. Tout tourne sur le fil du serveur.
 */
public final class QueueManager {

    private record Challenge(UUID target, Ladder ladder, long expires) {}

    private final MatchManager matches;
    private final EloStore elo;
    private final Lobby lobby;

    private final Map<String, Deque<List<UUID>>> queues = new HashMap<>();
    private final Set<UUID> queued = new HashSet<>();
    private final Map<UUID, UUID> mates = new HashMap<>();          // coéquipier <-> coéquipier
    private final Map<UUID, UUID> invites = new HashMap<>();        // invité -> hôte
    private final Map<UUID, Challenge> challenges = new HashMap<>(); // celui qui défie -> défi

    public QueueManager(MatchManager matches, EloStore elo, Lobby lobby) {
        this.matches = matches;
        this.elo = elo;
        this.lobby = lobby;
    }

    private static String key(Ladder ladder, Mode mode, boolean ranked) {
        return ladder.id + ":" + mode.label + (ranked ? ":classe" : ":casual");
    }

    public boolean isQueued(UUID uuid) {
        return queued.contains(uuid);
    }

    public int waiting(Ladder ladder, Mode mode, boolean ranked) {
        Deque<List<UUID>> queue = queues.get(key(ladder, mode, ranked));
        return queue == null ? 0 : queue.size() * mode.teamSize;
    }

    public UUID mateOf(UUID uuid) {
        return mates.get(uuid);
    }

    private boolean free(Player p, boolean tell) {
        if (matches.isBusy(p.getUniqueId()) || queued.contains(p.getUniqueId())) {
            if (tell) {
                p.sendMessage(Msg.msg("<red>Tu es déjà en combat ou en file d'attente."));
            }
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ Files (classées et casual)

    /** Rejoint la file d'un kit : classée (l'Elo est en jeu) ou casual (sans Elo). */
    public void join(Player p, Ladder ladder, Mode mode, boolean ranked) {
        if (!free(p, true)) {
            return;
        }
        List<UUID> group;
        if (mode == Mode.SOLO) {
            group = List.of(p.getUniqueId());
        } else {
            Player mate = mates.containsKey(p.getUniqueId()) ? Bukkit.getPlayer(mates.get(p.getUniqueId())) : null;
            if (mate == null) {
                p.sendMessage(Msg.msg("Le 2v2 se joue en équipe : <white>/party inviter <pseudo><gray>."));
                return;
            }
            if (!free(mate, false)) {
                p.sendMessage(Msg.msg("<red>" + mate.getName() + " est déjà en combat ou en file."));
                return;
            }
            group = List.of(p.getUniqueId(), mate.getUniqueId());
        }
        queues.computeIfAbsent(key(ladder, mode, ranked), k -> new ArrayDeque<>()).add(group);
        for (UUID u : group) {
            queued.add(u);
            Player member = Bukkit.getPlayer(u);
            if (member != null) {
                lobby.giveItems(member, true);
                member.sendMessage(Msg.msg("Recherche " + Msg.BLEU + ladder.label + " " + mode.label
                        + (ranked ? " <dark_gray>· <gray>classé, ton Elo <white>" + elo.elo(u, ladder)
                                  : " <dark_gray>· <gray>casual, sans Elo")));
            }
        }
    }

    /** Retire le joueur (et son coéquipier en 2v2) de la file. */
    public void leave(Player p, boolean tell) {
        UUID id = p.getUniqueId();
        if (!queued.contains(id)) {
            if (tell) {
                p.sendMessage(Msg.msg("Tu n'es dans aucune file."));
            }
            return;
        }
        for (Deque<List<UUID>> queue : queues.values()) {
            queue.removeIf(group -> {
                if (!group.contains(id)) {
                    return false;
                }
                for (UUID u : group) {
                    queued.remove(u);
                    Player member = Bukkit.getPlayer(u);
                    if (member != null && member.isOnline()) {
                        lobby.giveItems(member, false);
                        member.sendMessage(Msg.msg("Recherche annulée."));
                    }
                }
                return true;
            });
        }
    }

    /** Appelé une fois par seconde : forme les combats dès que deux groupes attendent. */
    public void tick() {
        long now = System.currentTimeMillis();
        challenges.values().removeIf(c -> c.expires() < now);
        for (boolean ranked : new boolean[] {true, false}) {
          for (Ladder ladder : Ladder.values()) {
            for (Mode mode : Mode.values()) {
                Deque<List<UUID>> queue = queues.get(key(ladder, mode, ranked));
                while (queue != null && queue.size() >= 2) {
                    List<UUID> a = queue.poll();
                    List<UUID> b = queue.poll();
                    a.forEach(queued::remove);
                    b.forEach(queued::remove);
                    if (!matches.start(ladder, mode, ranked, a, b)) {
                        // Aucune arène libre : les deux groupes gardent leur place en tête de file.
                        queued.addAll(a);
                        queued.addAll(b);
                        queue.addFirst(b);
                        queue.addFirst(a);
                        break;
                    }
                }
            }
          }
        }
    }

    // ------------------------------------------------------------------ Défis directs (sans Elo)

    public void challenge(Player from, Player to, Ladder ladder) {
        if (from.equals(to)) {
            from.sendMessage(Msg.msg("<red>Tu ne peux pas te défier toi-même."));
            return;
        }
        if (!free(from, true)) {
            return;
        }
        challenges.put(from.getUniqueId(), new Challenge(to.getUniqueId(), ladder, System.currentTimeMillis() + 60_000));
        from.sendMessage(Msg.msg("Défi " + Msg.BLEU + ladder.label + " <gray>envoyé à <white>" + to.getName() + "<gray>."));
        to.sendMessage(Msg.msg("<white>" + from.getName() + " <gray>te défie en " + Msg.BLEU + ladder.label
                + " <gray>(sans Elo). <white>/duel accepter " + from.getName()));
    }

    public void accept(Player target, Player from) {
        Challenge c = challenges.get(from.getUniqueId());
        if (c == null || !c.target().equals(target.getUniqueId())) {
            target.sendMessage(Msg.msg("<red>" + from.getName() + " ne t'a pas défié (ou le défi a expiré)."));
            return;
        }
        if (!free(target, true)) {
            return;
        }
        if (!free(from, false)) {
            target.sendMessage(Msg.msg("<red>" + from.getName() + " est déjà en combat ou en file."));
            return;
        }
        challenges.remove(from.getUniqueId());
        if (!matches.start(c.ladder(), Mode.SOLO, false, List.of(from.getUniqueId()), List.of(target.getUniqueId()))) {
            target.sendMessage(Msg.msg("<red>Aucune arène libre pour le moment."));
            from.sendMessage(Msg.msg("<red>Aucune arène libre pour le moment."));
        }
    }

    // ------------------------------------------------------------------ Équipes de 2

    public void invite(Player host, Player guest) {
        if (host.equals(guest)) {
            host.sendMessage(Msg.msg("<red>Invite quelqu'un d'autre que toi."));
            return;
        }
        if (mates.containsKey(host.getUniqueId()) || mates.containsKey(guest.getUniqueId())) {
            host.sendMessage(Msg.msg("<red>L'un de vous deux a déjà une équipe (<white>/party quitter<red>)."));
            return;
        }
        invites.put(guest.getUniqueId(), host.getUniqueId());
        host.sendMessage(Msg.msg("Invitation envoyée à <white>" + guest.getName() + "<gray>."));
        guest.sendMessage(Msg.msg("<white>" + host.getName() + " <gray>t'invite dans son équipe. <white>/party accepter"));
    }

    public void acceptInvite(Player guest) {
        UUID hostId = invites.remove(guest.getUniqueId());
        Player host = hostId == null ? null : Bukkit.getPlayer(hostId);
        if (host == null) {
            guest.sendMessage(Msg.msg("<red>Aucune invitation en attente."));
            return;
        }
        if (mates.containsKey(hostId) || mates.containsKey(guest.getUniqueId())) {
            guest.sendMessage(Msg.msg("<red>L'un de vous deux a déjà une équipe."));
            return;
        }
        mates.put(hostId, guest.getUniqueId());
        mates.put(guest.getUniqueId(), hostId);
        for (Player p : List.of(host, guest)) {
            p.sendMessage(Msg.msg("Équipe formée : <white>" + host.getName() + " <gray>et <white>" + guest.getName()
                    + "<gray>. Lance un 2v2 depuis le menu."));
        }
    }

    public void leaveParty(Player p, boolean tell) {
        UUID mateId = mates.remove(p.getUniqueId());
        if (mateId == null) {
            if (tell) {
                p.sendMessage(Msg.msg("Tu n'as pas d'équipe."));
            }
            return;
        }
        mates.remove(mateId);
        if (tell) {
            p.sendMessage(Msg.msg("Tu as quitté ton équipe."));
        }
        Player mate = Bukkit.getPlayer(mateId);
        if (mate != null) {
            mate.sendMessage(Msg.msg("<white>" + p.getName() + " <gray>a quitté l'équipe."));
        }
    }

    /** Déconnexion : plus de file, plus d'équipe, plus de défi ni d'invitation. */
    public void purge(Player p) {
        leave(p, false);
        leaveParty(p, false);
        UUID id = p.getUniqueId();
        invites.remove(id);
        invites.values().remove(id);
        challenges.remove(id);
        challenges.values().removeIf(c -> c.target().equals(id));
    }
}
