package games.orbit.orbitstaff.api.application.port;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Registre d'idempotence : garantit qu'une action sensible n'est traitee qu'une
 * seule fois, meme en cas de retry reseau.
 */
public interface IdempotencyRepository {

    /**
     * @return l'identifiant public du resultat deja produit pour cette cle, s'il existe
     */
    Optional<UUID> findResultPublicId(long serviceAccountId, String idempotencyKey);

    /**
     * Enregistre le resultat d'une action idempotente.
     *
     * @param serviceAccountId compte de service emetteur
     * @param idempotencyKey   cle fournie par le client
     * @param requestHash      empreinte de la requete (detection de collision de cle)
     * @param resultPublicId   identifiant public produit (ex: sanction)
     * @param now              instant de reference
     */
    void save(long serviceAccountId, String idempotencyKey, String requestHash, UUID resultPublicId, Instant now);
}
