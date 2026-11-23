package games.orbit.orbitstaff.integrations.unsupported;

import games.orbit.orbitstaff.common.enums.IntegrationAvailability;
import games.orbit.orbitstaff.common.model.BlockLogEntry;
import games.orbit.orbitstaff.common.model.BlockLookupQuery;
import games.orbit.orbitstaff.common.spi.BlockLogProvider;

import java.util.List;

/** Repli lorsqu'aucun historique de blocs n'est disponible (ex: CoreProtect absent). */
public final class UnsupportedBlockLogProvider implements BlockLogProvider {

    @Override
    public IntegrationAvailability availability() {
        return IntegrationAvailability.ABSENT;
    }

    @Override
    public List<BlockLogEntry> lookup(BlockLookupQuery query) {
        return List.of();
    }
}
