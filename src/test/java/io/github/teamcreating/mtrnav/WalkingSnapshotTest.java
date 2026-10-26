package io.github.teamcreating.mtrnav;

import io.github.teamcreating.mtrnav.core.*;
import io.github.teamcreating.mtrnav.network.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WalkingSnapshotTest {
    private final String dimension = "minecraft/overworld";
    private List<WalkingLibrary.Entry> entries(int count, long revision) {
        var result = new ArrayList<WalkingLibrary.Entry>();
        for (int i = 0; i < count; i++) {
            var c = new WalkingConnection(UUID.randomUUID().toString(), UUID.randomUUID().toString(), dimension, "a", "", Integer.toHexString(32 + i), "", 60, "", "", true);
            result.add(new WalkingLibrary.Entry(c, revision, UUID.randomUUID(), UUID.randomUUID(), 1));
        }
        return List.copyOf(result);
    }
    private WalkingExchange.Reply reply(List<WalkingLibrary.Entry> all, long generation, int page) {
        return new WalkingExchange.Reply("snapshot", dimension, generation, generation, generation, false,
                all.stream().skip(page * 128L).limit(128).toList(), List.of(), List.of(), "", page, all.size(), 0);
    }
    @Test void allPagesMustAgreeBeforeThePublishedSnapshotChanges() {
        var cache = new WalkingSnapshot(); var old = entries(1, 1);
        assertTrue(cache.accept(reply(old, 1, 0))); assertEquals(old, cache.published());
        var next = entries(129, 2); assertFalse(cache.accept(reply(next, 2, 1)));
        assertEquals(old, cache.published(), "partial transfer must not replace live data");
        assertFalse(cache.accept(reply(old, 1, 0)), "older generation is ignored");
        assertTrue(cache.accept(reply(next, 2, 0))); assertEquals(next, cache.published());
        assertThrows(UnsupportedOperationException.class, () -> cache.published().clear());
    }
    @Test void refreshDoesNotClearLiveDataAndMixedRevisionsDoNotComplete() {
        var cache = new WalkingSnapshot(); var old = entries(1, 1); cache.accept(reply(old, 1, 0)); cache.invalidate();
        assertEquals(old, cache.published()); assertFalse(cache.complete());
        var next = entries(129, 2); cache.accept(reply(next, 2, 0));
        assertFalse(cache.accept(reply(next, 3, 1))); assertEquals(old, cache.published());
        assertTrue(cache.accept(reply(next, 3, 0))); assertEquals(next, cache.published());
    }
    @Test void duplicateEndpointsTruncatedAndConflictingPagesAreRejected() {
        var cache = new WalkingSnapshot(); var all = entries(129, 1);
        cache.accept(reply(all, 1, 0));
        var changed = new ArrayList<>(all); changed.set(0, entries(1, 1).getFirst());
        assertThrows(IllegalArgumentException.class, () -> cache.accept(reply(changed, 1, 0)));
        var invalid = new WalkingExchange.Reply("snapshot", dimension, 1, 2, 2, false, List.of(), List.of(), List.of(), "", 0, 129, 0);
        assertThrows(IllegalArgumentException.class, () -> cache.accept(invalid));
        var duplicated = List.of(all.getFirst(), all.getFirst());
        assertThrows(IllegalArgumentException.class, () -> new WalkingSnapshot().accept(reply(duplicated, 3, 0)));
    }
}
