package games.orbit.orbitstaff.common.error;

/**
 * Action sensible rejouee avec la meme cle d'idempotence.
 *
 * <p>Garantit qu'une sanction n'est jamais creee deux fois a cause d'un retry
 * reseau (section 6 du cahier des charges).</p>
 */
public class DuplicateActionException extends OrbitStaffException {

    public DuplicateActionException(String idempotencyKey) {
        super("DUPLICATE_ACTION", "Action deja traitee (cle d'idempotence : " + idempotencyKey + ")");
    }
}
