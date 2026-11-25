package fr.orbit.mmo.outfit;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Dit au client d'un joueur que des entités sont assises sur un autre joueur, sans les monter côté serveur.
 * Le client place alors lui-même les pièces de la tenue sur le joueur à chaque image : aucun retard en
 * mouvement. Côté serveur le joueur n'a pas de passager, donc les téléportations restent possibles
 * (un porteur de passager ne peut plus être téléporté par Paper).
 * Paquet du jeu (ClientboundSetPassengersPacket) fabriqué par réflexion : Paper 26 tourne avec les noms Mojang.
 * Si quelque chose manque, {@link #ready()} est faux et le service suit les joueurs par téléportation.
 */
final class PassengerPackets {

    private static boolean ready;
    private static String error = "pas initialisé";
    private static Object codec;
    private static Method decode;
    private static Method buffer;
    private static Constructor<?> friendly;
    private static Method writeVarInt;
    private static Method writeVarIntArray;
    private static Method getHandle;
    private static Field connection;
    private static Method send;

    private PassengerPackets() {
    }

    static boolean init() {
        try {
            ClassLoader cl = Bukkit.getServer().getClass().getClassLoader();
            Class<?> packetClass = Class.forName("net.minecraft.network.protocol.Packet", false, cl);
            Class<?> pkt = Class.forName("net.minecraft.network.protocol.game.ClientboundSetPassengersPacket", true, cl);
            codec = pkt.getField("STREAM_CODEC").get(null);
            decode = Class.forName("net.minecraft.network.codec.StreamDecoder", false, cl).getMethod("decode", Object.class);
            Class<?> byteBuf = Class.forName("io.netty.buffer.ByteBuf", false, cl);
            buffer = Class.forName("io.netty.buffer.Unpooled", true, cl).getMethod("buffer");
            Class<?> fbb = Class.forName("net.minecraft.network.FriendlyByteBuf", true, cl);
            friendly = fbb.getConstructor(byteBuf);
            writeVarInt = fbb.getMethod("writeVarInt", int.class);
            writeVarIntArray = fbb.getMethod("writeVarIntArray", int[].class);
            getHandle = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer", true, cl).getMethod("getHandle");
            connection = Class.forName("net.minecraft.server.level.ServerPlayer", true, cl).getField("connection");
            send = Class.forName("net.minecraft.server.network.ServerCommonPacketListenerImpl", true, cl).getMethod("send", packetClass);
            Object test = build(1, new int[]{2, 3});
            if (!pkt.isInstance(test)) {
                throw new IllegalStateException("paquet inattendu : " + test);
            }
            ready = true;
            error = null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            ready = false;
            error = e.getClass().getSimpleName() + " : " + e.getMessage();
        }
        return ready;
    }

    static boolean ready() {
        return ready;
    }

    static String error() {
        return error;
    }

    private static Object build(int vehicle, int[] passengers) throws ReflectiveOperationException {
        Object buf = friendly.newInstance(buffer.invoke(null));
        writeVarInt.invoke(buf, vehicle);
        writeVarIntArray.invoke(buf, (Object) passengers);
        return decode.invoke(codec, buf);
    }

    /** Envoie à {@code viewer} la liste des passagers de l'entité {@code vehicle}. Faux en cas d'échec. */
    static boolean send(Player viewer, int vehicle, int[] passengers) {
        if (!ready) {
            return false;
        }
        try {
            Object handle = getHandle.invoke(viewer);
            Object conn = connection.get(handle);
            if (conn == null) {
                return false;
            }
            send.invoke(conn, build(vehicle, passengers));
            return true;
        } catch (java.lang.reflect.InvocationTargetException e) {
            return false; // connexion qui se ferme, etc. : on réessaiera au prochain envoi
        } catch (ReflectiveOperationException | RuntimeException e) {
            ready = false;
            error = "envoi : " + e.getClass().getSimpleName() + " : " + e.getMessage();
            return false;
        }
    }
}
