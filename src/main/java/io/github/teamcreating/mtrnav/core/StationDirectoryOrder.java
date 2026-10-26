package io.github.teamcreating.mtrnav.core;

import java.nio.charset.Charset;
import java.text.CollationKey;
import java.text.Collator;
import java.text.Normalizer;
import java.util.*;
import static io.github.teamcreating.mtrnav.core.Model.*;

/** Bilingual alphabetical browsing; unsupported Han initials remain in the other group. */
public final class StationDirectoryOrder {
    public enum Mode { AZ, ZA, DISTANCE }
    public static final List<String> GROUPS;
    private static final Charset CHINESE = Charset.forName("GB2312");
    private static final int[] INITIAL_START = "啊芭擦搭蛾发噶哈击喀垃妈拿哦啪期然撒塌挖昔压匝".chars().map(c -> encoded(Character.toString(c))).toArray();
    private static final String HAN_INITIALS = "ABCDEFGHJKLMNOPQRSTWXYZ";
    static { var groups = new ArrayList<String>(); groups.add("#"); for (char c = 'A'; c <= 'Z'; c++) groups.add(Character.toString(c)); groups.add("…"); GROUPS = List.copyOf(groups); }
    private StationDirectoryOrder() {}
    private static int encoded(String text) { byte[] bytes = text.getBytes(CHINESE); return (bytes[0] & 255) * 256 + (bytes[1] & 255) - 65536; }
    private static String normalized(String value) { return Normalizer.normalize(value.strip(), Normalizer.Form.NFKD).replaceAll("\\p{M}+", "").toUpperCase(Locale.ROOT); }
    private static String key(Station station) {
        var parts = station.name().split("\\|+");
        for (String part : parts) { String text = normalized(part); if (!text.isEmpty() && text.charAt(0) >= 'A' && text.charAt(0) <= 'Z') return text; }
        return normalized(parts.length == 0 ? station.name() : parts[0]);
    }
    public static String group(Station station) { return group(key(station)); }
    private static String group(String key) {
        if (key.isEmpty()) return "…";
        int first = key.codePointAt(0);
        if (first >= 'A' && first <= 'Z') return Character.toString((char)first);
        if (Character.isDigit(first)) return "#";
        if (!Character.isIdeographic(first)) return "…";
        byte[] bytes = new String(Character.toChars(first)).getBytes(CHINESE);
        if (bytes.length != 2) return "…";
        int code = (bytes[0] & 255) * 256 + (bytes[1] & 255) - 65536;
        if (code < INITIAL_START[0] || code > encoded("座")) return "…";
        for (int i = INITIAL_START.length - 1; i >= 0; i--) if (code >= INITIAL_START[i]) return Character.toString(HAN_INITIALS.charAt(i));
        return "…";
    }
    private record Entry(Station station, String group, CollationKey key) {}
    public static List<Station> sort(List<Station> stations, Mode mode, Map<String, Double> distances) {
        var collator = Collator.getInstance(Locale.CHINA);
        var entries = stations.stream().map(s -> { String key = key(s); return new Entry(s, group(key), collator.getCollationKey(key)); }).toList();
        Comparator<Entry> alphabetical = (a, b) -> {
            int ag = GROUPS.indexOf(a.group()), bg = GROUPS.indexOf(b.group());
            if ((ag == 27 || bg == 27 || ag == 0 || bg == 0) && ag != bg) return Integer.compare(ag, bg);
            int comparison = Integer.compare(ag, bg); if (comparison == 0) comparison = a.key().compareTo(b.key());
            return mode == Mode.ZA ? -comparison : comparison;
        };
        Comparator<Entry> order = mode == Mode.DISTANCE ? Comparator.comparingDouble((Entry e) -> distance(distances.get(e.station().id()))).thenComparing(alphabetical) : alphabetical;
        return entries.stream().sorted(order.thenComparing(e -> e.station().id())).map(Entry::station).toList();
    }
    private static double distance(Double value) { return value != null && Double.isFinite(value) && value >= 0 ? value : Double.POSITIVE_INFINITY; }
    public static int first(List<Station> stations, String group) { for (int i = 0; i < stations.size(); i++) if (group(stations.get(i)).equals(group)) return i; return -1; }
}
