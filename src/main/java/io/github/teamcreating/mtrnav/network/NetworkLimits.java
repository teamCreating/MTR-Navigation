package io.github.teamcreating.mtrnav.network;

import java.nio.charset.StandardCharsets;

/** Shared byte limits used before JSON decoding, independent of the game payload classes. */
public final class NetworkLimits {
    public static final int UPLOAD = 16 * 1024;
    private NetworkLimits() {}
    public static void upload(String json) {
        if (json == null || json.getBytes(StandardCharsets.UTF_8).length > UPLOAD) throw new IllegalArgumentException("Upload exceeds 16 KiB");
        structure(json, 16);
    }
    public static void structure(String json, int maximumDepth) {
        // Reject pathological nesting before Gson can recurse into attacker-controlled JSON.
        int depth = 0; boolean quoted = false, escaped = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (quoted) { if (escaped) escaped = false; else if (c == '\\') escaped = true; else if (c == '"') quoted = false; }
            else if (c == '"') quoted = true;
            else if (c == '{' || c == '[') { if (++depth > maximumDepth) throw new IllegalArgumentException("JSON nesting limit exceeded"); }
            else if (c == '}' || c == ']') { if (--depth < 0) throw new IllegalArgumentException("Invalid JSON structure"); }
        }
        if (quoted || depth != 0) throw new IllegalArgumentException("Invalid JSON structure");
    }
}
