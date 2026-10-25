package io.github.teamcreating.mtrnav.core;

import java.util.*;
import static io.github.teamcreating.mtrnav.core.Model.*;

/** One session-local reminder for one stop event. Never substitutes the next train. */
public final class ArrivalReminder {
    public static final int DEFAULT_MINUTES = 9;
    public static final long STALE_NANOS = 5_000_000_000L;
    private final String id = UUID.randomUUID().toString(), dimension, stationId;
    private final StopEvent selected;
    private final int minutes;
    private StopEvent event;
    private long lastSample, lastMatchedSample, lastMatchedNanos;
    private boolean confirmed = true, notified;
    public ArrivalReminder(String dimension, String stationId, StopEvent event, int minutes, long sampleMillis, long receivedNanos) {
        if (dimension == null || dimension.isEmpty() || stationId == null || stationId.isEmpty() || event == null
                || minutes < 1 || minutes > 60 || event.arrival() <= 0 || event.departure() < event.arrival())
            throw new IllegalArgumentException("Invalid arrival reminder");
        this.dimension = dimension; this.stationId = stationId; this.selected = this.event = event;
        this.minutes = minutes; lastSample = lastMatchedSample = sampleMillis; lastMatchedNanos = receivedNanos;
    }
    public String id() { return id; }
    public String dimension() { return dimension; }
    public String stationId() { return stationId; }
    public StopEvent event() { return event; }
    public int minutes() { return minutes; }
    public boolean notified() { return notified; }
    public boolean stale(long nanos) { return !confirmed || nanos - lastMatchedNanos >= STALE_NANOS; }
    public boolean fresh(long serverNow, long nanos) { return !stale(nanos) && serverNow - lastMatchedSample < 5000; }
    public boolean expired(long serverNow) { return serverNow > event.departure() + 600_000L; }
    public void observe(String dimension, String station, Collection<StopEvent> events, long sampleMillis, long receivedNanos) {
        if (!this.dimension.equals(dimension) || !stationId.equals(station) || sampleMillis <= lastSample) return;
        lastSample = sampleMillis;
        var matches = events.stream().filter(e -> sameEvent(selected, e)).toList();
        confirmed = matches.size() == 1;
        if (confirmed) { event = matches.getFirst(); lastMatchedSample = sampleMillis; lastMatchedNanos = receivedNanos; }
    }
    public boolean tick(long serverNow, long nanos, boolean clockFresh) {
        if (notified || !clockFresh || !fresh(serverNow, nanos) || serverNow > event.departure()) return false;
        if (event.arrival() - serverNow <= minutes * 60_000L) { notified = true; return true; }
        return false;
    }
    public static boolean sameEvent(StopEvent a, StopEvent b) {
        if (!a.run().equals(b.run()) || !a.routeId().equals(b.routeId()) || !a.platformId().equals(b.platformId()) || a.stopIndex() != b.stopIndex()) return false;
        // The public board has no siding/loop/vehicle identity. Its departure index
        // is only usable together with the exact scheduled stop times and direction.
        if (!a.run().sidingId().equals("native-board")) return true;
        return a.destination().equals(b.destination()) && a.arrival() - a.deviation() == b.arrival() - b.deviation()
                && a.departure() - a.deviation() == b.departure() - b.deviation();
    }
    public static Optional<StopEvent> unique(StopEvent selected, Collection<StopEvent> events) {
        var matches = events.stream().filter(e -> sameEvent(selected, e)).limit(2).toList();
        return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
    }
}
