package games.orbit.orbitstaff.core.staff;

/**
 * Objet de la hotbar staff (donnee pure, independante de Bukkit).
 *
 * @param slot     emplacement 0-8
 * @param material nom du materiau Bukkit (ex: COMPASS)
 * @param action   action declenchee (ex: TELEPORT, INSPECT, FREEZE)
 * @param nameKey  cle de langue pour le nom affiche
 */
public record HotbarItem(int slot, String material, StaffAction action, String nameKey) {

    public HotbarItem {
        if (slot < 0 || slot > 8) {
            throw new IllegalArgumentException("Slot hotbar hors bornes (0-8) : " + slot);
        }
    }
}
