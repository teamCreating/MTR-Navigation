package io.github.teamcreating.mtrnav.core;

import java.util.*;
import static io.github.teamcreating.mtrnav.core.Model.*;

public final class StationSearch {
    private StationSearch() {}
    public static String exact(List<Station> stations, String text) {
        String needle = text.strip();
        if (needle.isEmpty()) return "";
        var matches = stations.stream().filter(s -> displayName(s.name()).equalsIgnoreCase(needle)
                || Arrays.stream(s.name().split("\\|+")).anyMatch(part -> part.strip().equalsIgnoreCase(needle))).toList();
        return matches.size() == 1 ? matches.getFirst().id() : "";
    }
}
