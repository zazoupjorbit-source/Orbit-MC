package fr.orbit.mmo.guild;

import fr.orbit.mmo.classes.Faction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Une guilde : des joueurs d'une même faction, trois grades, une banque en or et sa progression dans le Pilier des
 * Astres (les cent étages). Tout passe par {@link GuildService}, qui sauvegarde après chaque changement.
 */
public final class Guild {

    /** Grades, du plus haut au plus bas. */
    public enum Rank {
        CHEF("chef", "Chef", "#FFD66E"),
        OFFICIER("officier", "Officier", "#7FD4FF"),
        MEMBRE("membre", "Membre", "#D7DCE2");

        public final String id;
        public final String label;
        public final String hex;

        Rank(String id, String label, String hex) {
            this.id = id;
            this.label = label;
            this.hex = hex;
        }

        public String tag() {
            return "<" + hex + ">" + label + "</" + hex + ">";
        }

        public static Rank byId(String id) {
            for (Rank r : values()) {
                if (r.id.equalsIgnoreCase(id)) {
                    return r;
                }
            }
            return MEMBRE;
        }
    }

    public static final class Member {
        public final UUID id;
        String name;
        Rank rank;
        long since;

        Member(UUID id, String name, Rank rank, long since) {
            this.id = id;
            this.name = name;
            this.rank = rank;
            this.since = since;
        }

        public String name() {
            return name;
        }

        public Rank rank() {
            return rank;
        }

        public long since() {
            return since;
        }
    }

    /** Clé du fichier : le nom sans accent, en minuscules. */
    final String id;
    final String name;
    final Faction faction;
    final long created;
    final Map<UUID, Member> members = new LinkedHashMap<>();
    long bank;
    /** Plus haut étage du Pilier franchi par la guilde, et quand. */
    int towerCleared;
    long towerClearedAt;
    /** Nombre d'étages franchis avant toutes les autres guildes. */
    int firsts;

    Guild(String id, String name, Faction faction, long created) {
        this.id = id;
        this.name = name;
        this.faction = faction;
        this.created = created;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public Faction faction() {
        return faction;
    }

    public long created() {
        return created;
    }

    public long bank() {
        return bank;
    }

    public int towerCleared() {
        return towerCleared;
    }

    public long towerClearedAt() {
        return towerClearedAt;
    }

    public int firsts() {
        return firsts;
    }

    public int size() {
        return members.size();
    }

    public Member member(UUID id) {
        return members.get(id);
    }

    public Rank rank(UUID id) {
        Member m = members.get(id);
        return m == null ? null : m.rank;
    }

    public Member leader() {
        for (Member m : members.values()) {
            if (m.rank == Rank.CHEF) {
                return m;
            }
        }
        return null;
    }

    public Map<UUID, Member> members() {
        return Collections.unmodifiableMap(members);
    }

    /** Membres du plus haut grade au plus bas, puis par nom. */
    public List<Member> sorted() {
        List<Member> out = new ArrayList<>(members.values());
        out.sort(Comparator.comparing((Member m) -> m.rank).thenComparing(m -> m.name == null ? "" : m.name, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    /** Membre retrouvé par son nom (sans tenir compte des majuscules). */
    public Member byName(String name) {
        for (Member m : members.values()) {
            if (m.name != null && m.name.equalsIgnoreCase(name)) {
                return m;
            }
        }
        return null;
    }

    public String tag() {
        return "<" + GuildService.HEX + ">" + fr.orbit.mmo.util.Text.esc(name) + "</" + GuildService.HEX + ">";
    }
}
