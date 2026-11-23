package games.orbit.orbitstaff.core.staff;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistance sure de l'etat du mode staff (survit a la deconnexion, au crash et
 * au redemarrage). Stockage YAML dans le dossier du plugin.
 */
public final class StaffStateStore {

    private final File file;
    private final FileConfiguration yaml;

    public StaffStateStore(File dataFolder) {
        this.file = new File(dataFolder, "staff-state.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);
    }

    public void save(UUID uuid, StaffModeState state) {
        yaml.createSection(uuid.toString(), StaffStateCodec.toMap(state));
        persist();
    }

    public Optional<StaffModeState> load(UUID uuid) {
        ConfigurationSection section = yaml.getConfigurationSection(uuid.toString());
        if (section == null) {
            return Optional.empty();
        }
        return Optional.of(StaffStateCodec.fromMap(section.getValues(false)));
    }

    public void clear(UUID uuid) {
        yaml.set(uuid.toString(), null);
        persist();
    }

    private void persist() {
        try {
            yaml.save(file);
        } catch (IOException e) {
            throw new IllegalStateException("Echec d'ecriture de staff-state.yml", e);
        }
    }
}
