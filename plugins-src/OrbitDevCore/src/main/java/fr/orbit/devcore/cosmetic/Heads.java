package fr.orbit.devcore.cosmetic;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/**
 * Têtes personnalisées (champ {@code texture} du catalogue) : soit la valeur « Value » complète en
 * base64 (minecraft-heads.com, onglet « For Developers »), soit juste l'identifiant de la texture
 * (la fin de l'URL textures.minecraft.net/texture/...).
 */
final class Heads {

    private Heads() {
    }

    static PlayerProfile profile(String texture) {
        String value = texture.length() > 100
                ? texture
                : Base64.getEncoder().encodeToString(("{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/"
                + texture + "\"}}}").getBytes(StandardCharsets.UTF_8));
        // UUID stable dérivé de la texture : les têtes identiques sont reconnues comme telles par le client.
        PlayerProfile profile = Bukkit.createProfile(UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)), null);
        profile.setProperty(new ProfileProperty("textures", value));
        return profile;
    }
}
