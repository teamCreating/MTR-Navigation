package io.github.teamcreating.mtrnav.core;

import java.util.*;
import static io.github.teamcreating.mtrnav.core.Model.*;

public final class StationSearch {
    private StationSearch() {}
    public static List<Station> matches(List<Station> stations, String text) {
        String needle = text.strip().toLowerCase(Locale.ROOT);
        return stations.stream().filter(s -> displayName(s.name()).toLowerCase(Locale.ROOT).contains(needle))
                .sorted(Comparator.comparing(Station::name).thenComparing(Station::id)).toList();
    }
    public static String exact(List<Station> stations, String text) {
        String needle = text.strip();
        if (needle.isEmpty()) return "";
        var matches = stations.stream().filter(s -> displayName(s.name()).equalsIgnoreCase(needle)
                || Arrays.stream(s.name().split("\\|+")).anyMatch(part -> part.strip().equalsIgnoreCase(needle))).toList();
        return matches.size() == 1 ? matches.getFirst().id() : "";
    }
}
