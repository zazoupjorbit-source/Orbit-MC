package games.orbit.orbitstaff.core.staff;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

/**
 * Serialise/deserialise un inventaire en base64 via l'API de serialisation
 * versionnee de Bukkit (compatible Paper 1.21.1 et evolutions).
 */
public final class InventorySerializer {

    private InventorySerializer() {
    }

    public static String toBase64(ItemStack[] items) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             BukkitObjectOutputStream out = new BukkitObjectOutputStream(bos)) {
            out.writeInt(items.length);
            for (ItemStack item : items) {
                out.writeObject(item);
            }
            out.flush();
            return Base64.getEncoder().encodeToString(bos.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("Echec de serialisation de l'inventaire", e);
        }
    }

    public static ItemStack[] fromBase64(String data) {
        if (data == null || data.isBlank()) {
            return new ItemStack[0];
        }
        try (ByteArrayInputStream bis = new ByteArrayInputStream(Base64.getDecoder().decode(data));
             BukkitObjectInputStream in = new BukkitObjectInputStream(bis)) {
            int length = in.readInt();
            ItemStack[] items = new ItemStack[length];
            for (int i = 0; i < length; i++) {
                items[i] = (ItemStack) in.readObject();
            }
            return items;
        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalStateException("Echec de deserialisation de l'inventaire", e);
        }
    }
}
