package io.github.teamcreating.mtrnav.core;

import io.github.teamcreating.mtrnav.network.WalkingExchange;
import java.util.*;

/** Atomic per-dimension published snapshots assembled from bounded, revision-consistent pages. */
public final class WalkingSnapshot {
    private long generation = -1, revision = -1;
    private int count;
    private String dimension = "";
    private final Map<Integer, List<WalkingLibrary.Entry>> pages = new HashMap<>();
    private List<WalkingLibrary.Entry> published = List.of();
    private boolean complete;
    public List<WalkingLibrary.Entry> published() { return published; }
    public boolean hasPage(int page) { return pages.containsKey(page); }
    public boolean complete() { return complete; }
    public void invalidate() { generation = -1; pages.clear(); complete = false; }
    public boolean accept(WalkingExchange.Reply reply) {
        if (reply.generation() < generation || reply.revision() < revision) return false;
        if (reply.entryCount() < 0 || reply.entryCount() > 1024 || reply.page() < 0 || reply.page() > 79 || reply.entries().size() > 128)
            throw new IllegalArgumentException("Invalid walking page bounds");
        int expected = Math.max(0, Math.min(128, reply.entryCount() - reply.page() * 128));
        if (reply.entries().size() != expected) throw new IllegalArgumentException("Truncated walking page");
        for (var entry : reply.entries()) if (entry == null || entry.revision() < 1 || !entry.connection().dimension().equals(reply.dimension())) throw new IllegalArgumentException("Wrong walking entry dimension");
        if (generation != reply.generation() || revision != reply.revision() || !dimension.equals(reply.dimension())) {
            pages.clear(); generation = reply.generation(); revision = reply.revision(); dimension = reply.dimension(); count = reply.entryCount(); complete = false;
        } else if (count != reply.entryCount()) throw new IllegalArgumentException("Inconsistent snapshot size");
        var old = pages.putIfAbsent(reply.page(), reply.entries());
        if (old != null && !old.equals(reply.entries())) throw new IllegalArgumentException("Conflicting walking page");
        int total = Math.max(1, (count + 127) / 128);
        for (int i = 0; i < total; i++) if (!pages.containsKey(i)) return false;
        var entries = new ArrayList<WalkingLibrary.Entry>(); var ids = new HashSet<String>(); var endpoints = new HashSet<String>();
        for (int i = 0; i < total; i++) for (var entry : pages.get(i)) {
            if (!ids.add(entry.connection().id()) || !endpoints.add(entry.connection().endpoints())) throw new IllegalArgumentException("Duplicate walking entry");
            entries.add(entry);
        }
        if (entries.size() != count) throw new IllegalArgumentException("Walking snapshot size mismatch");
        published = List.copyOf(entries); complete = true; return true;
    }
}
