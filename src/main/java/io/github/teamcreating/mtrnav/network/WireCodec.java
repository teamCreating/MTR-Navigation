package io.github.teamcreating.mtrnav.network;

import com.google.gson.Gson;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** Bounded compressed responses; a transfer is installed atomically after all chunks arrive. */
public final class WireCodec {
    public static final int CHUNK_BYTES = 24 * 1024, MAX_BYTES = 8 * 1024 * 1024, MAX_JSON = 32 * 1024 * 1024;
    public static final Gson GSON = new Gson();
    private WireCodec() {}
    public static byte[] encode(Messages.Response response) throws IOException {
        byte[] json = GSON.toJson(response).getBytes(StandardCharsets.UTF_8);
        if (json.length > MAX_JSON) throw new IOException("Navigation response too large");
        var bytes = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(bytes)) { gzip.write(json); }
        if (bytes.size() > MAX_BYTES) throw new IOException("Navigation response too large");
        return bytes.toByteArray();
    }
    public static Messages.Response decode(byte[] bytes) throws IOException {
        if (bytes.length > MAX_BYTES) throw new IOException("Navigation response too large");
        try (var gzip = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            byte[] json = gzip.readNBytes(MAX_JSON + 1);
            if (json.length > MAX_JSON) throw new IOException("Expanded navigation response too large");
            String text = new String(json, StandardCharsets.UTF_8); NetworkLimits.structure(text, 32);
            var response = GSON.fromJson(text, Messages.Response.class);
            if (response == null || response.dimension() == null || response.kind() == null) throw new IOException("Invalid navigation response");
            return response;
        } catch (RuntimeException e) { throw new IOException("Invalid navigation response", e); }
    }
    public static final class Assembly {
        private long transfer = -1, startedNanos;
        private byte[][] parts;
        private int bytes, received;
        public byte[] accept(long id, int part, int total, byte[] data, long now) throws IOException {
            if (total < 1 || total > (MAX_BYTES + CHUNK_BYTES - 1) / CHUNK_BYTES || part < 0 || part >= total
                    || data.length < 1 || data.length > CHUNK_BYTES) throw new IOException("Invalid navigation chunk");
            if (parts != null && now - startedNanos > 10_000_000_000L) clear();
            if (id < transfer) return null;
            if (id != transfer || parts == null) { clear(); transfer = id; parts = new byte[total][]; startedNanos = now; }
            if (parts.length != total) throw new IOException("Inconsistent navigation chunks");
            if (parts[part] != null) {
                if (!Arrays.equals(parts[part], data)) throw new IOException("Conflicting navigation chunk");
                return null;
            }
            if ((bytes += data.length) > MAX_BYTES) { clear(); throw new IOException("Navigation response too large"); }
            parts[part] = data.clone(); received++;
            if (received != total) return null;
            var out = new ByteArrayOutputStream(bytes);
            for (var entry : parts) out.write(entry);
            byte[] result = out.toByteArray(); parts = null; return result;
        }
        public void clear() { transfer = -1; parts = null; bytes = received = 0; }
    }
}
