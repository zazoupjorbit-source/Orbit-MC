package fr.orbit.reborn.party;

import fr.orbit.reborn.RebornMMO;
import fr.orbit.reborn.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Groupes de joueurs : XP partagée, barres de vie des équipiers, « Switch ». */
public final class PartyService {

    public static final class Party {
        private UUID leader;
        private final LinkedHashSet<UUID> members = new LinkedHashSet<>();

        public UUID leader() {
            return leader;
        }

        public Set<UUID> members() {
            return Collections.unmodifiableSet(members);
        }
    }

    private record Invite(Party party, long expires) {
    }

    private final RebornMMO plugin;
    private final Map<UUID, Party> byMember = new HashMap<>();
    private final Map<UUID, Invite> invites = new HashMap<>();

    public PartyService(RebornMMO plugin) {
        this.plugin = plugin;
    }

    public int maxSize() {
        return plugin.getConfig().getInt("party.max-size", 6);
    }

    public Party party(UUID id) {
        return byMember.get(id);
    }

    public boolean sameParty(UUID a, UUID b) {
        Party p = byMember.get(a);
        return p != null && p.members.contains(b);
    }

    /** Équipiers en ligne à portée (le joueur inclus). */
    public List<Player> nearby(Player p, double radius) {
        Party party = byMember.get(p.getUniqueId());
        List<Player> out = new ArrayList<>();
        if (party == null) {
            out.add(p);
            return out;
        }
        double r2 = radius * radius;
        for (UUID id : party.members) {
            Player m = Bukkit.getPlayer(id);
            if (m != null && m.getWorld().equals(p.getWorld()) && m.getLocation().distanceSquared(p.getLocation()) <= r2) {
                out.add(m);
            }
        }
        return out;
    }

    public void invite(Player leader, Player target) {
        if (leader.equals(target)) {
            Text.msg(leader, "<#FF5C5C>Vous ne pouvez pas vous inviter vous-même.");
            return;
        }
        Party party = byMember.get(leader.getUniqueId());
        if (party == null) {
            party = new Party();
            party.leader = leader.getUniqueId();
            party.members.add(leader.getUniqueId());
            byMember.put(leader.getUniqueId(), party);
        } else if (!party.leader.equals(leader.getUniqueId())) {
            Text.msg(leader, "<#FF5C5C>Seul le chef du groupe peut inviter.");
            return;
        }
        if (party.members.size() >= maxSize()) {
            Text.msg(leader, "<#FF5C5C>Le groupe est complet (" + maxSize() + " joueurs).");
            return;
        }
        if (byMember.containsKey(target.getUniqueId())) {
            Text.msg(leader, "<#FF5C5C>" + Text.esc(target.getName()) + " est déjà dans un groupe.");
            return;
        }
        invites.put(target.getUniqueId(), new Invite(party, System.currentTimeMillis() + 60_000));
        Text.msg(leader, "Invitation envoyée à <white>" + Text.esc(target.getName()) + "</white>.");
        Text.msg(target, "<white>" + Text.esc(leader.getName()) + "</white> vous invite dans son groupe. "
                + "<click:run_command:'/groupe accepter'><#5EE89A><b>[Accepter]</b></#5EE89A></click> <#8C96A3>(60 s)");
    }

    public void accept(Player p) {
        Invite inv = invites.remove(p.getUniqueId());
        if (inv == null || inv.expires < System.currentTimeMillis()) {
            Text.msg(p, "<#FF5C5C>Aucune invitation en attente.");
            return;
        }
        if (byMember.containsKey(p.getUniqueId())) {
            Text.msg(p, "<#FF5C5C>Quittez d'abord votre groupe actuel.");
            return;
        }
        Party party = inv.party;
        if (party.members.isEmpty() || party.members.size() >= maxSize()) {
            Text.msg(p, "<#FF5C5C>Ce groupe n'est plus disponible.");
            return;
        }
        party.members.add(p.getUniqueId());
        byMember.put(p.getUniqueId(), party);
        broadcast(party, "<white>" + Text.esc(p.getName()) + "</white> rejoint le groupe.");
    }

    public void leave(Player p) {
        Party party = byMember.remove(p.getUniqueId());
        if (party == null) {
            Text.msg(p, "<#FF5C5C>Vous n'êtes dans aucun groupe.");
            return;
        }
        party.members.remove(p.getUniqueId());
        Text.msg(p, "Vous avez quitté le groupe.");
        afterLeave(party, p.getName());
    }

    public void kick(Player leader, String name) {
        Party party = byMember.get(leader.getUniqueId());
        if (party == null || !party.leader.equals(leader.getUniqueId())) {
            Text.msg(leader, "<#FF5C5C>Seul le chef du groupe peut expulser.");
            return;
        }
        for (UUID id : party.members) {
            Player m = Bukkit.getPlayer(id);
            String n = m != null ? m.getName() : Bukkit.getOfflinePlayer(id).getName();
            if (n != null && n.equalsIgnoreCase(name) && !id.equals(leader.getUniqueId())) {
                party.members.remove(id);
                byMember.remove(id);
                if (m != null) {
                    Text.msg(m, "<#FF8A3B>Vous avez été retiré du groupe.");
                }
                afterLeave(party, n);
                return;
            }
        }
        Text.msg(leader, "<#FF5C5C>" + Text.esc(name) + " n'est pas dans votre groupe.");
    }

    /** Déconnexion : quitte le groupe sans message d'erreur. */
    public void quit(Player p) {
        invites.remove(p.getUniqueId());
        Party party = byMember.remove(p.getUniqueId());
        if (party != null) {
            party.members.remove(p.getUniqueId());
            afterLeave(party, p.getName());
        }
    }

    private void afterLeave(Party party, String name) {
        if (party.members.size() <= 1) {
            for (UUID id : party.members) {
                byMember.remove(id);
                Player m = Bukkit.getPlayer(id);
                if (m != null) {
                    Text.msg(m, "Le groupe est dissous.");
                }
            }
            party.members.clear();
            return;
        }
        if (!party.members.contains(party.leader)) {
            party.leader = party.members.iterator().next();
        }
        broadcast(party, "<white>" + Text.esc(name) + "</white> a quitté le groupe.");
    }

    public void broadcast(Party party, String mini) {
        for (UUID id : party.members) {
            Player m = Bukkit.getPlayer(id);
            if (m != null) {
                Text.msg(m, "<#7FB2FF>[Groupe]</#7FB2FF> " + mini);
            }
        }
    }
}
