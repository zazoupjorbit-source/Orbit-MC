package fr.orbitgames.hcf.events;

import fr.orbitgames.hcf.api.model.ConquestInfo;
import fr.orbitgames.hcf.api.model.KothInfo;
import fr.orbitgames.hcf.api.service.EventsService;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Exposition de l'etat des evenements HCF aux autres modules via OrbitHCF-API.
 */
public class EventsServiceImpl implements EventsService {

    private final OrbitHCFEventsPlugin plugin;

    public EventsServiceImpl(OrbitHCFEventsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean isSotwActive() {
        return plugin.getSotwManager().isActive();
    }

    @Override
    public Duration getSotwRemaining() {
        return plugin.getSotwManager().getRemaining();
    }

    @Override
    public boolean isSotwProtected(UUID playerId) {
        return plugin.getSotwManager().isProtected(playerId);
    }

    @Override
    public void removeSotwProtection(UUID playerId) {
        plugin.getSotwManager().removeProtection(playerId);
    }

    @Override
    public boolean isEotwActive() {
        return plugin.getEotwManager().isActive();
    }

    @Override
    public Optional<KothInfo> getActiveKoth() {
        return Optional.ofNullable(plugin.getKothManager().getActive());
    }

    @Override
    public Optional<ConquestInfo> getActiveConquest() {
        return plugin.getConquestManager().isRunning()
                ? Optional.of(plugin.getConquestManager())
                : Optional.empty();
    }
}
