package fr.orbit.devcore.cosmetic;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Paquet « passagers » envoyé aux seuls clients : les accessoires de dos y apparaissent montés sur
 * le joueur (le client les place lui-même, sans retard), alors que pour le serveur le joueur n'a
 * aucun passager. Un vrai passager bloquerait les téléportations vers un autre monde (Paper les
 * refuse). Classes internes du serveur : si elles changent avec une mise à jour de Paper,
 * {@link #available()} devient faux et les accessoires suivent le joueur par téléportation.
 */
final class FakePassengers {

    private static volatile boolean available = true;

    private FakePassengers() {
    }

    static boolean available() {
        return available;
    }

    /** Envoie à {@code viewer} : l'entité {@code vehicleId} porte {@code passengerIds}. */
    static void send(Logger logger, Player viewer, int vehicleId, int[] passengerIds) {
        if (!available) {
            return;
        }
        try {
            ServerGamePacketListenerImpl connection = ((CraftPlayer) viewer).getHandle().connection;
            if (connection == null) {
                return;
            }
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            try {
                buf.writeVarInt(vehicleId);
                buf.writeVarIntArray(passengerIds);
                connection.send(ClientboundSetPassengersPacket.STREAM_CODEC.decode(buf));
            } finally {
                buf.release();
            }
        } catch (LinkageError | ClassCastException e) {
            available = false;
            logger.log(Level.WARNING, "Accessoires de dos : paquet des passagers indisponible "
                    + "(version de Paper ?) ; ils suivront le joueur par téléportation.", e);
        }
    }
}
