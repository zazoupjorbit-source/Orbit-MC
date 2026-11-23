package fr.orbitversus.match;

import fr.orbitversus.Msg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Files d'attente et matchmaking. 1v1 solo, 2v2 par équipe nommée, défis directs, et mises.
 * Le pairing tourne périodiquement (tick) sur le thread principal.
 */
public final class QueueManager {

    private final MatchManager matches;

    private final Deque<Party> queue1v1 = new ArrayDeque<>();
    private final Deque<Party> ready2v2 = new ArrayDeque<>();
    private final Map<String, Party> forming2v2 = new HashMap<>();   // teamName -> équipe en formation
    private final Map<UUID, Long> pendingBet = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> challenges = new ConcurrentHashMap<>(); // challenger -> cible
    private final java.util.Set<UUID> queued = ConcurrentHashMap.newKeySet();

    public QueueManager(MatchManager matches) {
        this.matches = matches;
    }

    public void setBet(UUID uuid, long amount) {
        pendingBet.put(uuid, Math.max(0, amount));
    }

    private long takeBet(UUID uuid) {
        Long b = pendingBet.remove(uuid);
        return b != null ? b : 0L;
    }

    private boolean guard(Player p) {
        if (matches.isBusy(p.getUniqueId()) || queued.contains(p.getUniqueId())) {
            p.sendMessage(Msg.msg("<yellow>Tu es déjà en combat ou en file d'attente."));
            return false;
        }
        return true;
    }

    // ---- 1v1 file ----
    public void queue1v1(Player p) {
        if (!guard(p)) return;
        Party party = new Party(null);
        party.members().add(p.getUniqueId());
        party.setBet(takeBet(p.getUniqueId()));
        queue1v1.add(party);
        queued.add(p.getUniqueId());
        p.sendMessage(Msg.msg("<gray>Recherche d'un adversaire <#E8650A>1v1<gray>… <dark_gray>/1v1 leave pour annuler."));
    }

    // ---- Défi direct 1v1 : deux joueurs se défient mutuellement ----
    public void challenge(Player challenger, Player target) {
        if (challenger.equals(target)) {
            challenger.sendMessage(Msg.msg("<red>Tu ne peux pas te défier toi-même."));
            return;
        }
        if (!guard(challenger)) return;
        // défi mutuel : la cible a-t-elle déjà défié le challenger ?
        if (target.getUniqueId().equals(challenges.get(challenger.getUniqueId()))
                || challenger.getUniqueId().equals(challenges.get(target.getUniqueId()))) {
            challenges.remove(target.getUniqueId());
            challenges.remove(challenger.getUniqueId());
            long stake = Math.min(takeBet(challenger.getUniqueId()), takeBet(target.getUniqueId()));
            matches.start(VersusMode.ONE_V_ONE,
                    java.util.List.of(challenger.getUniqueId()),
                    java.util.List.of(target.getUniqueId()), stake);
            return;
        }
        challenges.put(challenger.getUniqueId(), target.getUniqueId());
        challenger.sendMessage(Msg.msg("<gray>Défi envoyé à <white>" + target.getName() + "<gray>."));
        target.sendMessage(Msg.msg("<white>" + challenger.getName() + " <gray>te défie en 1v1 ! "
                + "<dark_gray>/1v1 " + challenger.getName() + " <gray>pour accepter."));
    }

    // ---- 2v2 : formation d'équipe puis file ----
    public void queue2v2(Player p, String teamName) {
        if (!guard(p)) return;
        String key = teamName.toLowerCase();
        Party team = forming2v2.computeIfAbsent(key, k -> new Party(teamName));
        if (team.size() >= 2) {
            p.sendMessage(Msg.msg("<red>Cette équipe est déjà complète, choisis un autre nom."));
            return;
        }
        team.members().add(p.getUniqueId());
        team.setBet(Math.max(team.bet(), takeBet(p.getUniqueId())));
        queued.add(p.getUniqueId());
        p.sendMessage(Msg.msg("<gray>Équipe <#E8650A>" + teamName + " <gray>(" + team.size() + "/2)"));
        if (team.size() == 2) {
            forming2v2.remove(key);
            ready2v2.add(team);
            for (UUID u : team.members()) {
                Player m = Bukkit.getPlayer(u);
                if (m != null) {
                    m.sendMessage(Msg.msg("<gray>Équipe complète — recherche d'adversaires <#E8650A>2v2<gray>…"));
                }
            }
        }
    }

    public void leave(Player p) {
        UUID id = p.getUniqueId();
        boolean removed = queued.remove(id);
        queue1v1.removeIf(party -> party.contains(id));
        ready2v2.removeIf(party -> party.contains(id));
        forming2v2.values().removeIf(party -> {
            party.members().remove(id);
            return party.size() == 0;
        });
        challenges.remove(id);
        challenges.values().remove(id);
        if (removed) {
            p.sendMessage(Msg.msg("<gray>Tu as quitté la file d'attente."));
        }
    }

    /** Retire un joueur déconnecté de toutes les files. */
    public void purge(UUID id) {
        queued.remove(id);
        queue1v1.removeIf(party -> party.contains(id));
        ready2v2.removeIf(party -> party.contains(id));
        forming2v2.values().removeIf(party -> {
            party.members().remove(id);
            return party.size() == 0;
        });
        challenges.remove(id);
        challenges.values().remove(id);
    }

    /** Tente de former des combats (appelé périodiquement). */
    public void tick() {
        pair(queue1v1, VersusMode.ONE_V_ONE);
        pair(ready2v2, VersusMode.TWO_V_TWO);
    }

    private void pair(Deque<Party> queue, VersusMode mode) {
        while (queue.size() >= 2) {
            Party a = queue.poll();
            Party b = queue.poll();
            if (a == null || b == null) {
                return;
            }
            long stake = a.bet() > 0 && b.bet() > 0 ? Math.min(a.bet(), b.bet()) : 0;
            boolean started = matches.start(mode, a.members(), b.members(), stake);
            if (!started) {
                // pas d'arène libre : on remet les deux groupes en tête et on arrête ce tick
                queue.addFirst(b);
                queue.addFirst(a);
                return;
            }
            a.members().forEach(queued::remove);
            b.members().forEach(queued::remove);
        }
    }
}
