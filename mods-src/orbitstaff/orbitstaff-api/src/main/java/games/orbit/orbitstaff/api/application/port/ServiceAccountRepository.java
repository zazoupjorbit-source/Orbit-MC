package games.orbit.orbitstaff.api.application.port;

import games.orbit.orbitstaff.api.application.model.ServiceAccount;

import java.time.Instant;
import java.util.Optional;

/** Acces aux comptes de service (authentification des serveurs et du bot). */
public interface ServiceAccountRepository {

    /**
     * @param keyHash hash SHA-256 de la cle presentee
     * @return le compte s'il existe et n'est pas revoque
     */
    Optional<ServiceAccount> findActiveByKeyHash(String keyHash);

    /** Met a jour la date de derniere utilisation (best-effort). */
    void touch(long serviceAccountId, Instant now);
}
