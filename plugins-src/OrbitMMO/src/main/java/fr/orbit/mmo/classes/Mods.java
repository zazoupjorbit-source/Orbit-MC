package fr.orbit.mmo.classes;

import java.util.HashMap;
import java.util.Map;

/**
 * Somme des bonus d'un joueur (passifs de spécialisation + talents). Clés (valeurs en %, sauf mention) :
 * <ul>
 *   <li>degats, degats.&lt;sort&gt; · soins, soins.&lt;sort&gt; · crit, crit.&lt;sort&gt; (points de %) · critdeg</li>
 *   <li>pv (PV max) · armure (dégâts subis en moins) · esquive · blocage (chance de bloquer 50 % d'un coup) · epines</li>
 *   <li>vol-de-vie · soin-sur-kill (% des PV max) · vitesse · ressource (génération)</li>
 *   <li>rage-par-coup (points) · recharge.&lt;sort&gt; (secondes) · cout.&lt;sort&gt; (points) · duree.&lt;sort&gt; (secondes)</li>
 * </ul>
 */
public final class Mods {

    public static final Mods EMPTY = new Mods();

    private final Map<String, Double> values = new HashMap<>();

    public void add(Map<String, Double> effects, int times) {
        for (Map.Entry<String, Double> e : effects.entrySet()) {
            values.merge(e.getKey(), e.getValue() * times, Double::sum);
        }
    }

    public double get(String key) {
        Double v = values.get(key);
        return v == null ? 0 : v;
    }

    /** Bonus général + bonus propre au sort (ex. degats + degats.charge). */
    public double get(String key, String spellId) {
        return get(key) + (spellId == null ? 0 : get(key + "." + spellId));
    }

    /** Tous les bonus (lecture seule), pour la fiche du personnage. */
    public Map<String, Double> values() {
        return java.util.Collections.unmodifiableMap(values);
    }

    /** Multiplicateur 1 + x %. */
    public double mult(String key, String spellId) {
        return 1 + get(key, spellId) / 100.0;
    }
}
