package io.github.teamcreating.mtrnav.client.ui.theme;

import io.github.teamcreating.mtrnav.client.NavigationClient;
import io.github.teamcreating.mtrnav.client.WalkingClient;
import io.github.teamcreating.mtrnav.core.JourneyTracker;
import io.github.teamcreating.mtrnav.core.Model;
import net.minecraft.client.Minecraft;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;

import static io.github.teamcreating.mtrnav.client.NavigationClient.*;

/**
 * Pure domain-to-display formatting helpers decoupled from any rendering or widget code.
 */
public final class NavFormat {
    private static final Pattern ROUTE_CODE = Pattern.compile("[A-Z]{1,3}[0-9]{1,2}[A-Z]?|[0-9]{1,2}号线");
    private static final DateTimeFormatter CLOCK_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter CLOCK_SEC_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private NavFormat() {}

    public static String primary(String name) {
        return name == null ? "" : name.split("\\|", 2)[0].trim();
    }

    public static String secondary(String name) {
        if (name == null) return "";
        return Arrays.stream(name.split("\\|+")).skip(1).map(String::strip)
                .filter(s -> !s.isEmpty()).reduce((a, b) -> a + " · " + b).orElse("");
    }

    public static String station(String id) {
        var st = catalog == null ? null : catalog.station(id);
        return st == null ? tr("mtrnav.station_pending") : primary(st.name());
    }

    public static String stationFull(String id) {
        var st = catalog == null ? null : catalog.station(id);
        return st == null ? tr("mtrnav.station_pending") : Model.displayName(st.name());
    }

    public static String route(String id) {
        if (catalog == null) return tr("mtrnav.route_pending");
        return catalog.routes().stream().filter(r -> r.id().equals(id))
                .findFirst().map(r -> primary(r.name())).orElse(tr("mtrnav.route_pending"));
    }

    public static String routeCode(String id) {
        String label = route(id);
        var m = ROUTE_CODE.matcher(label);
        return m.find() ? m.group() : label;
    }

    public static int routeColor(String id) {
        if (catalog == null) return UiTokens.ACCENT_SKY;
        return catalog.routes().stream().filter(r -> r.id().equals(id))
                .map(r -> 0xFF000000 | (r.color() & 0xFFFFFF))
                .findFirst().orElse(UiTokens.ACCENT_SKY);
    }

    public static Model.RouteType routeType(String id) {
        return catalog == null ? Model.RouteType.UNKNOWN : catalog.routeType(id);
    }

    public static String platform(String id) {
        var p = catalog == null ? null : catalog.platform(id);
        return p == null ? tr("mtrnav.platform_pending") : Model.displayName(p.name());
    }

    public static String duration(long millis) {
        long minutes = Math.max(1, (millis + 59_999L) / 60_000L);
        return tr("mtrnav.minutes", minutes);
    }

    public static String shortDuration(long millis) {
        long minutes = Math.max(1, (millis + 59_999L) / 60_000L);
        return minutes + tr("mtrnav.ui.minute_unit");
    }

    public static String remaining(long timestamp) {
        return CLOCK.stale(System.nanoTime()) ? "—" : NavigationClient.countdown(timestamp);
    }

    public static String clockTime(long millis) {
        if (CLOCK.stale(System.nanoTime()) || millis <= 0) return "—";
        return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(CLOCK_FMT);
    }

    public static String liveClock() {
        long now = System.nanoTime();
        long millis = CLOCK.stale(now) ? System.currentTimeMillis() : CLOCK.now(now);
        return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(CLOCK_SEC_FMT);
    }

    public static String phaseLabel(JourneyTracker.Phase phase) {
        return tr("mtrnav.phase." + phase.name().toLowerCase(Locale.ROOT));
    }

    public static String guidance() {
        if (tracking == null) return "";
        if (WalkingClient.rulesChanged()) return tr("mtrnav.notice.walking_changed");
        if (tracking.walk() != null) {
            var walk = tracking.walk();
            var player = Minecraft.getInstance().player;
            var target = catalog == null ? null : catalog.platform(walk.toPlatformId());
            var stationTarget = catalog == null ? null : catalog.station(walk.toStationId());
            if (!tracking.notice().isEmpty() && !tracking.notice().equals("manual_walk")) {
                return tr("mtrnav.notice." + tracking.notice());
            }
            if (player != null && stationTarget != null) {
                double tx = target == null ? stationTarget.x() : target.x();
                double tz = target == null ? stationTarget.z() : target.z();
                if (Math.hypot(player.getX() - tx, player.getZ() - tz) <= 16) return tr("mtrnav.walk_nearby");
            }
            return tr("mtrnav.walk_manual_key", openKey.get());
        }
        if (!tracking.notice().isEmpty() && !tracking.notice().equals("manual_check")) {
            return tr("mtrnav.notice." + tracking.notice());
        }
        if (usingNative && !CLOCK.stale(System.nanoTime()) && tracking.legIndex() < tracking.journey().legs().size()) {
            var leg = tracking.journey().legs().get(tracking.legIndex());
            long now = CLOCK.now(System.nanoTime());
            if (tracking.phase() == JourneyTracker.Phase.RIDING && leg.arrival() - now <= 30_000) {
                return tr(now < leg.arrival() ? "mtrnav.estimated_alight_soon" : "mtrnav.estimated_alight_check");
            }
            if (tracking.phase() != JourneyTracker.Phase.RIDING && now > leg.departure() + 10_000) {
                return tr("mtrnav.notice.missed_train");
            }
        }
        return tracking.notice().isEmpty() ? "" : tr("mtrnav.notice." + tracking.notice());
    }
}
