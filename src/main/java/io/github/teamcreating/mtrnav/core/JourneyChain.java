package io.github.teamcreating.mtrnav.core;

import java.util.*;
import static io.github.teamcreating.mtrnav.core.Model.*;

/** Ordered presentation of real ride and walking steps; no invented station access walks. */
public final class JourneyChain {
    private JourneyChain() {}
    public record Item(boolean walking, String routeId, int seconds) {}
    public static List<Item> of(Journey journey) { return of(journey.legs().stream().map(RideLeg::routeId).toList(), journey.walks()); }
    public static List<Item> of(ConnectionPlan plan) { return of(plan.legs().stream().map(ConnectionPlan.Leg::routeId).toList(), plan.walks()); }
    private static List<Item> of(List<String> rides, List<WalkLeg> walks) {
        var result = new ArrayList<Item>();
        for (int i = 0; i <= rides.size(); i++) {
            final int index = i;
            int seconds = walks.stream().filter(w -> w.beforeRideIndex() == index).mapToInt(WalkLeg::seconds).sum();
            if (seconds > 0) result.add(new Item(true, "", seconds));
            if (i < rides.size()) result.add(new Item(false, rides.get(i), 0));
        }
        return List.copyOf(result);
    }
}
