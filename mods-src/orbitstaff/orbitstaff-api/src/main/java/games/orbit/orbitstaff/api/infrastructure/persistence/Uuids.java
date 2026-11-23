package games.orbit.orbitstaff.api.infrastructure.persistence;

import java.nio.ByteBuffer;
import java.util.UUID;

/** Conversion UUID &lt;-&gt; BINARY(16) pour le stockage MariaDB. */
public final class Uuids {

    private Uuids() {
    }

    public static byte[] toBytes(UUID uuid) {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        return buffer.array();
    }

    public static UUID toUuid(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        long high = buffer.getLong();
        long low = buffer.getLong();
        return new UUID(high, low);
    }
}
