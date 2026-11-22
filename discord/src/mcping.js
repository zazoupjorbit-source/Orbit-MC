// Ping "Server List" Minecraft (le même que la liste multijoueur), sans dépendance.
const net = require('net');

function writeVarInt(value) {
  const bytes = [];
  let v = value >>> 0;
  do {
    let b = v & 0x7f;
    v >>>= 7;
    if (v) b |= 0x80;
    bytes.push(b);
  } while (v);
  return Buffer.from(bytes);
}

function readVarInt(buf, offset) {
  let value = 0;
  let shift = 0;
  for (let i = offset; ; i++) {
    if (i >= buf.length) throw new RangeError('incomplete');
    const b = buf[i];
    value |= (b & 0x7f) << shift;
    if (!(b & 0x80)) return [value, i + 1];
    shift += 7;
    if (shift > 35) throw new Error('VarInt trop long');
  }
}

/** Renvoie { online, players, max, version, ms } ou { online: false }. */
function ping(host, port = 25565, timeout = 4000) {
  return new Promise((resolve) => {
    const start = Date.now();
    const sock = net.createConnection({ host, port });
    let buf = Buffer.alloc(0);
    let finished = false;
    const done = (result) => {
      if (finished) return;
      finished = true;
      sock.destroy();
      resolve(result);
    };
    sock.setTimeout(timeout, () => done({ online: false }));
    sock.on('error', () => done({ online: false }));
    sock.on('connect', () => {
      const hostBuf = Buffer.from(host, 'utf8');
      const portBuf = Buffer.alloc(2);
      portBuf.writeUInt16BE(port);
      const handshake = Buffer.concat([writeVarInt(0), writeVarInt(767), writeVarInt(hostBuf.length), hostBuf, portBuf, writeVarInt(1)]);
      sock.write(Buffer.concat([writeVarInt(handshake.length), handshake]));
      sock.write(Buffer.from([0x01, 0x00])); // Status Request
    });
    sock.on('data', (chunk) => {
      buf = Buffer.concat([buf, chunk]);
      try {
        const [len, o1] = readVarInt(buf, 0);
        if (buf.length < o1 + len) return;
        const [, o2] = readVarInt(buf, o1); // packet id
        const [strLen, o3] = readVarInt(buf, o2);
        const json = JSON.parse(buf.subarray(o3, o3 + strLen).toString('utf8'));
        done({
          online: true,
          players: json.players?.online ?? 0,
          max: json.players?.max ?? 0,
          version: json.version?.name ?? '',
          ms: Date.now() - start,
        });
      } catch (e) {
        if (e instanceof RangeError) return; // paquet pas encore complet
        done({ online: false });
      }
    });
  });
}

module.exports = { ping };
