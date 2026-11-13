package io.github.teamcreating.mtrnav.client;

import io.github.teamcreating.mtrnav.core.Model.*;
import org.mtr.client.CustomResourceLoader;
import org.mtr.core.data.TransportMode;
import java.util.*;
import static io.github.teamcreating.mtrnav.client.NavigationClient.tr;

/** Names belong to the client's active resource packs; IDs remain the wire identity. */
final class VehicleNames {
    record Info(String name, String description, String tags) {}
    private static Map<String, Info> resources = Map.of();
    private static long refreshed;
    static void refresh() {
        var found = new HashMap<String, Info>();
        var tags = CustomResourceLoader.getVehicleTags(TransportMode.TRAIN);
        CustomResourceLoader.iterateVehicles(TransportMode.TRAIN, resource -> {
            var labels = new TreeSet<String>();
            tags.forEach((group, values) -> values.forEach((tag, ids) -> { if (ids.contains(resource.getId())) labels.add(group + ": " + tag); }));
            found.put(resource.getId(), new Info(resource.getName().getString(), resource.getDescription().getString(), String.join(" · ", labels)));
        });
        resources = Map.copyOf(found); refreshed = System.nanoTime();
    }
    static Info info(String id) {
        if (System.nanoTime() - refreshed > 2_000_000_000L) refresh();
        return resources.getOrDefault(id, new Info(id, tr("mtrnav.vehicle_missing"), ""));
    }
    static String category(RouteType type) { return tr("mtrnav.route_type." + type.name().toLowerCase(Locale.ROOT)); }
    static String stock(Consist consist) {
        if (consist.cars().isEmpty()) return tr("mtrnav.vehicle_unknown_short");
        var counts = new LinkedHashMap<String, Integer>(); consist.cars().forEach(id -> counts.merge(id, 1, Integer::sum));
        return String.join(" + ", counts.entrySet().stream().map(e -> info(e.getKey()).name() + (e.getValue() > 1 ? " ×" + e.getValue() : "")).toList())
                + " · " + tr("mtrnav.vehicle_cars", consist.count()) + " · " + tr("mtrnav.stock_source." + consist.source().name().toLowerCase(Locale.ROOT));
    }
    static String compact(Consist consist, int width) {
        if (consist.cars().isEmpty()) return tr("mtrnav.vehicle_unknown_short");
        String suffix = " · " + tr("mtrnav.vehicle_cars", consist.count()) + " · " + tr("mtrnav.stock_source." + consist.source().name().toLowerCase(Locale.ROOT));
        String names = String.join(" + ", consist.cars().stream().map(id -> info(id).name()).distinct().toList());
        var font = NavigationTheme.uiFont();
        int available = Math.max(0, width - font.width(suffix));
        if (font.width(names) > available) names = font.plainSubstrByWidth(names, Math.max(0, available - font.width("…"))) + "…";
        return names + suffix;
    }
    static String ordered(Consist consist) { return String.join(" → ", consist.cars().stream().map(id -> info(id).name() + " [" + id + "]").toList()); }
}
