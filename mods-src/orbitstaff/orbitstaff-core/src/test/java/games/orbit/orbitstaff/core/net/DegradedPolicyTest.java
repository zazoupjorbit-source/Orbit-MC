package games.orbit.orbitstaff.core.net;

import games.orbit.orbitstaff.core.net.DegradedPolicy.ActionKind;
import games.orbit.orbitstaff.core.net.DegradedPolicy.Outcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("DegradedPolicy")
class DegradedPolicyTest {

    @Test
    @DisplayName("API disponible : tout passe")
    void apiAvailableProceeds() {
        DegradedPolicy policy = new DegradedPolicy(true);
        assertEquals(Outcome.PROCEED, policy.decide(ActionKind.READ, true));
        assertEquals(Outcome.PROCEED, policy.decide(ActionKind.WRITE, true));
    }

    @Test
    @DisplayName("API indisponible : lecture servie depuis le cache si autorisee")
    void readServedFromCacheWhenAllowed() {
        DegradedPolicy policy = new DegradedPolicy(true);
        assertEquals(Outcome.SERVE_FROM_CACHE, policy.decide(ActionKind.READ, false));
        assertEquals(Outcome.REJECT, policy.decide(ActionKind.WRITE, false));
    }

    @Test
    @DisplayName("API indisponible et lecture locale interdite : rejet")
    void readRejectedWhenNotAllowed() {
        DegradedPolicy policy = new DegradedPolicy(false);
        assertEquals(Outcome.REJECT, policy.decide(ActionKind.READ, false));
    }
}
