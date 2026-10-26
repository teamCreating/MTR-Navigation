package io.github.teamcreating.mtrnav.network;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;

/** Independent small transfer assembly; large navigation catalogs cannot interleave its state. */
public final class WalkingWire {
    public static final int MAX_COMPRESSED = 256 * 1024, MAX_JSON = 1024 * 1024, CHUNK = 16 * 1024;
    public static byte[] encode(WalkingExchange.Reply reply) throws IOException {
        byte[] raw = WireCodec.GSON.toJson(reply).getBytes(StandardCharsets.UTF_8);
        if (raw.length > MAX_JSON) throw new IOException("Walking response too large");
        var out = new ByteArrayOutputStream(); try (var gzip = new GZIPOutputStream(out)) { gzip.write(raw); }
        if (out.size() > MAX_COMPRESSED) throw new IOException("Walking response too large"); return out.toByteArray();
    }
    public static WalkingExchange.Reply decode(byte[] bytes) throws IOException {
        if (bytes.length > MAX_COMPRESSED) throw new IOException("Walking response too large");
        try (var gzip = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            byte[] raw = gzip.readNBytes(MAX_JSON + 1); if (raw.length > MAX_JSON) throw new IOException("Expanded walking response too large");
            String text = new String(raw, StandardCharsets.UTF_8); NetworkLimits.structure(text, 32);
            var reply = WireCodec.GSON.fromJson(text, WalkingExchange.Reply.class);
            if (reply == null || reply.dimension() == null || reply.kind() == null || reply.message() == null || reply.entries().size() > 1024 || reply.proposals().size() > 2000) throw new IOException("Invalid walking response");
            return reply;
        } catch (RuntimeException e) { throw new IOException("Invalid walking response", e); }
    }
    public static final class Assembly {
        private final WireCodec.Assembly delegate = new WireCodec.Assembly();
        public byte[] accept(WalkingChunk chunk, long now) throws IOException {
            return accept(chunk.transfer(), chunk.index(), chunk.total(), chunk.data(), now);
        }
        public byte[] accept(long transfer, int index, int total, byte[] data, long now) throws IOException {
            if (total < 1 || total > MAX_COMPRESSED / CHUNK || data.length > CHUNK) throw new IOException("Walking chunk limit exceeded");
            return delegate.accept(transfer, index, total, data, now);
        }
        public void clear() { delegate.clear(); }
    }
}
