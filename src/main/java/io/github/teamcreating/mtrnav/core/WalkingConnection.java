package io.github.teamcreating.mtrnav.core;

import java.util.*;
import static io.github.teamcreating.mtrnav.core.Model.*;

/** One directed, explicitly declared walking link. Text is plain data, never a command or path. */
public record WalkingConnection(String id, String groupId, String dimension, String fromStation, String fromPlatform,
        String toStation, String toPlatform, int seconds, String name, String description, boolean enabled) {
    public WalkingConnection {
        uuid(id); uuid(groupId);
        if (dimension == null || !dimension.matches("[a-z0-9_.-]+/[a-z0-9_./-]+") || dimension.length() > 256) throw new IllegalArgumentException("Invalid dimension");
        nativeId(fromStation, false); nativeId(toStation, false); nativeId(fromPlatform, true); nativeId(toPlatform, true);
        if (seconds < 10 || seconds > 3600) throw new IllegalArgumentException("Walking time must be 10..3600 seconds");
        text(name, 48); text(description, 256);
        if (fromStation.equals(toStation) && fromPlatform.equals(toPlatform)) throw new IllegalArgumentException("Identical walking endpoints");
    }
    private static void uuid(String id) { if (id == null || !UUID.fromString(id).toString().equals(id)) throw new IllegalArgumentException("Invalid connection identity"); }
    private static void nativeId(String id, boolean optional) {
        if (id == null || !(optional && id.isEmpty()) && !id.matches("[0-9a-f]{1,16}")) throw new IllegalArgumentException("Invalid native ID");
    }
    public static void text(String text, int max) {
        if (text == null || text.length() > max || text.codePoints().anyMatch(c -> Character.isISOControl(c) || c == 0xA7)) throw new IllegalArgumentException("Invalid plain text");
    }
    public String endpoints() { return dimension + ":" + fromStation + ":" + fromPlatform + ">" + toStation + ":" + toPlatform; }
    public int specificity() { return (fromPlatform.isEmpty() ? 0 : 1) + (toPlatform.isEmpty() ? 0 : 1); }
    public void validate(Catalog catalog) {
        if (catalog == null || !dimension.equals(catalog.dimension()) || catalog.station(fromStation) == null || catalog.station(toStation) == null)
            throw new IllegalArgumentException("Unknown walking station or dimension");
        for (var pair : List.of(Map.entry(fromPlatform, fromStation), Map.entry(toPlatform, toStation))) if (!pair.getKey().isEmpty()) {
            var p = catalog.platform(pair.getKey());
            if (p == null || !p.stationId().equals(pair.getValue())) throw new IllegalArgumentException("Platform does not belong to station");
        }
    }
    public WalkingConnection reverse(String newId) { return new WalkingConnection(newId, groupId, dimension, toStation, toPlatform, fromStation, fromPlatform, seconds, name, description, enabled); }
    public WalkingConnection enabled(boolean value) { return new WalkingConnection(id, groupId, dimension, fromStation, fromPlatform, toStation, toPlatform, seconds, name, description, value); }
    public WalkingConnection privateWire() { return new WalkingConnection(id, id, dimension, fromStation, fromPlatform, toStation, toPlatform, seconds, "", "", enabled); }
}
