package io.github.teamcreating.mtrnav;

import io.github.teamcreating.mtrnav.core.*;
import io.github.teamcreating.mtrnav.network.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static io.github.teamcreating.mtrnav.core.Model.*;
import static org.junit.jupiter.api.Assertions.*;

class WalkingSecurityTest {
    private final UUID alice = UUID.randomUUID(), bob = UUID.randomUUID(), admin = UUID.randomUUID();
    private final String dim = "minecraft/overworld";
    private final Catalog catalog = new Catalog(dim, 1,
            List.of(new Station("a", "A", 0, 0, 0, List.of()), new Station("b", "B", 0, 0, 0, List.of())),
            List.of(new Platform("c", "a", "1", 0, 0, 0), new Platform("d", "b", "2", 0, 0, 0)), List.of());
    private WalkingConnection link() { return new WalkingConnection(UUID.randomUUID().toString(), UUID.randomUUID().toString(), dim, "a", "c", "b", "d", 60, "Walk", "manual", true); }
    private WalkingLibrary.Command command(String action, String id, long revision, WalkingConnection value) { return new WalkingLibrary.Command(action, id, revision, value, action.equals("reject") ? "Not passable" : ""); }
    private WalkingLibrary.State submitted(WalkingConnection value) { return WalkingLibrary.change(WalkingLibrary.State.empty(), alice, false, command("submit", "", 0, value), catalog, 1000); }
    @Test void proposalsArePrivateUntilAnAuthenticatedModeratorApproves() {
        var edge = link(); var state = submitted(edge); var p = state.proposals().values().iterator().next();
        assertTrue(state.connections(dim).isEmpty()); assertEquals(alice, p.author());
        assertEquals(1, state.visible(alice, false, dim, 1000).size()); assertTrue(state.visible(bob, false, dim, 1000).isEmpty());
        assertEquals(1, state.visible(admin, true, dim, 1000).size());
        assertThrows(SecurityException.class, () -> WalkingLibrary.change(state, bob, false, command("approve", p.id(), 1, null), catalog, 2000));
        var approved = WalkingLibrary.change(state, admin, true, command("approve", p.id(), 1, null), catalog, 2000);
        assertEquals(1, approved.connections(dim).size()); assertEquals(alice, approved.published().get(edge.id()).author()); assertEquals(admin, approved.published().get(edge.id()).reviewer());
        assertThrows(IllegalStateException.class, () -> WalkingLibrary.change(approved, alice, false, command("withdraw", p.id(), 2, null), catalog, 3000));
    }
    @Test void otherUsersCannotWithdrawOrReadSomeoneElsesProposal() {
        var state = submitted(link()); var p = state.proposals().values().iterator().next();
        assertThrows(SecurityException.class, () -> WalkingLibrary.change(state, bob, false, command("withdraw", p.id(), 1, null), catalog, 2000));
        assertThrows(SecurityException.class, () -> WalkingLibrary.change(state, admin, true, command("withdraw", p.id(), 1, null), catalog, 2000));
        var result = WalkingLibrary.change(state, alice, false, command("withdraw", p.id(), 1, null), catalog, 2000);
        assertEquals(WalkingLibrary.Status.WITHDRAWN, result.proposals().get(p.id()).status());
    }
    @Test void staleRevisionAndDuplicateApprovalCannotOverwritePublishedData() {
        var state = submitted(link()); var p = state.proposals().values().iterator().next();
        assertThrows(IllegalStateException.class, () -> WalkingLibrary.change(state, admin, true, command("approve", p.id(), 0, null), catalog, 2000));
        var approved = WalkingLibrary.change(state, admin, true, command("approve", p.id(), 1, null), catalog, 2000);
        assertThrows(IllegalStateException.class, () -> WalkingLibrary.change(approved, admin, true, command("approve", p.id(), 1, null), catalog, 3000));
        assertThrows(IllegalStateException.class, () -> WalkingLibrary.change(approved, admin, true, command("remove", p.value().id(), 0, null), catalog, 3000));
    }
    @Test void approvalRevalidatesStationsAndExpiredProposalsAreNotApproved() {
        var state = submitted(link()); var p = state.proposals().values().iterator().next();
        var empty = new Catalog(dim, 2, List.of(), List.of(), List.of());
        assertThrows(IllegalArgumentException.class, () -> WalkingLibrary.change(state, admin, true, command("approve", p.id(), 1, null), empty, 2000));
        assertEquals(WalkingLibrary.Status.EXPIRED, state.visible(alice, false, dim, 1000 + WalkingLibrary.WEEK).getFirst().status());
        assertThrows(IllegalStateException.class, () -> WalkingLibrary.change(state, admin, true, command("approve", p.id(), 1, null), catalog, 1000 + WalkingLibrary.WEEK));
    }
    @Test void editsRemainPendingAndLosingModeratorPermissionRejectsCommitCandidate() {
        var state = submitted(link()); var p = state.proposals().values().iterator().next(); var value = p.value().enabled(false);
        var edit = command("amend", p.id(), 1, value);
        assertThrows(SecurityException.class, () -> WalkingLibrary.change(state, admin, false, edit, catalog, 2000));
        var changed = WalkingLibrary.change(state, admin, true, edit, catalog, 2000);
        assertTrue(changed.published().isEmpty()); assertEquals(WalkingLibrary.Status.PENDING, changed.proposals().get(p.id()).status());
        assertThrows(IllegalStateException.class, () -> WalkingLibrary.change(changed, admin, true, command("approve", p.id(), 1, null), catalog, 3000));
        assertFalse(WalkingLibrary.change(changed, admin, true, command("approve", p.id(), 2, null), catalog, 3000).connections(dim).getFirst().enabled());
    }
    @Test void duplicateAndOverQuotaPendingSuggestionsAreRejected() {
        var state = submitted(link()); var duplicate = link();
        var firstState = state;
        assertThrows(IllegalArgumentException.class, () -> WalkingLibrary.change(firstState, alice, false, command("submit", "", 0, duplicate), catalog, 2000));
        // Different endpoint specificity/direction gives at most five pending suggestions per actor.
        var variants = List.of(new String[]{"a", "", "b", ""}, new String[]{"a", "c", "b", ""}, new String[]{"a", "", "b", "d"}, new String[]{"b", "d", "a", "c"});
        for (var v : variants) state = WalkingLibrary.change(state, alice, false, command("submit", "", 0,
                new WalkingConnection(UUID.randomUUID().toString(), UUID.randomUUID().toString(), dim, v[0], v[1], v[2], v[3], 60, "", "", true)), catalog, 2000);
        var full = state;
        assertThrows(IllegalStateException.class, () -> WalkingLibrary.change(full, alice, false, command("submit", "", 0, link()), catalog, 2000));
    }
    @Test void validationRejectsWrongPlatformsPathsTextAndOutOfRangeTimes() {
        var good = link();
        assertThrows(IllegalArgumentException.class, () -> new WalkingConnection(good.id(), good.groupId(), dim, "../a", "", "b", "", 60, "", "", true));
        assertThrows(IllegalArgumentException.class, () -> new WalkingConnection(good.id(), good.groupId(), dim, "a", "d", "b", "c", 60, "", "", true).validate(catalog));
        assertThrows(IllegalArgumentException.class, () -> new WalkingConnection(good.id(), good.groupId(), dim, "a", "", "b", "", 9, "", "", true));
        assertThrows(IllegalArgumentException.class, () -> new WalkingConnection(good.id(), good.groupId(), dim, "a", "", "b", "", 3601, "", "", true));
        assertThrows(IllegalArgumentException.class, () -> WalkingConnection.text("bad\ntext", 256));
        assertThrows(IllegalArgumentException.class, () -> WalkingConnection.text("§ccommand", 256));
        assertThrows(IllegalArgumentException.class, () -> new WalkingLibrary.Command("approve", "", 0, null, "x".repeat(257)));
    }
    @Test void rateBudgetsBoundSustainedAndBurstTraffic() {
        var rate = new WalkingLibrary.Rate(2, 2); long now = 1_000_000_000L;
        assertTrue(rate.allow(now)); assertTrue(rate.allow(now)); assertFalse(rate.allow(now));
        assertFalse(rate.allow(now + 29_000_000_000L)); assertFalse(rate.allow(now + 30_000_000_001L));
        assertFalse(rate.allow(now + 59_999_999_999L)); assertTrue(rate.allow(now + 60_000_000_000L));
        assertTrue(rate.allow(now + 60_000_000_001L)); assertFalse(rate.allow(now + 60_000_000_002L));
    }
    @Test void failedPreparationDoesNotPublishAndStorageRecoversAnOlderValidSnapshot() throws Exception {
        var directory = Files.createTempDirectory(Path.of(System.getProperty("java.io.tmpdir")), "walking-test-");
        var storage = new WalkingStorage(directory); var empty = WalkingLibrary.State.empty(); var proposed = submitted(link());
        var prepared = storage.prepare(empty, proposed);
        assertEquals(0, storage.load().revision(), "prepared files are never visible as live data");
        storage.commit(prepared); assertEquals(proposed, storage.load());
        var p = proposed.proposals().values().iterator().next(); var approved = WalkingLibrary.change(proposed, admin, true, command("approve", p.id(), 1, null), catalog, 2000);
        storage.commit(storage.prepare(proposed, approved)); assertEquals(1, storage.load().connections(dim).size());
        Files.writeString(directory.resolve("library.json"), "broken");
        assertEquals(proposed, storage.load()); assertEquals("broken", Files.readString(directory.resolve("library.json")), "corrupt evidence is retained");
        assertThrows(Exception.class, () -> storage.commit(directory.getParent().resolve("other.json")));
    }
    @Test void boundedIndependentWireRejectsInvalidChunksAndExpiredOrConflictingTransfers() throws Exception {
        var response = new WalkingExchange.Reply("snapshot", dim, 1, 0, 0, false, List.of(), List.of(), List.of(), "", 0, 0, 0);
        byte[] bytes = WalkingWire.encode(response); assertEquals(response, WalkingWire.decode(bytes));
        var assembly = new WalkingWire.Assembly();
        assertThrows(Exception.class, () -> assembly.accept(1, 0, 17, new byte[]{1}, 1));
        assertThrows(Exception.class, () -> assembly.accept(1, 0, 1, new byte[WalkingWire.CHUNK + 1], 1));
        int half = bytes.length / 2;
        assertNull(assembly.accept(2, 0, 2, Arrays.copyOfRange(bytes, 0, half), 1));
        assertNull(assembly.accept(1, 0, 1, bytes, 2), "old transfer cannot replace a newer one");
        assertThrows(Exception.class, () -> assembly.accept(2, 0, 2, new byte[]{7}, 3));
        assembly.clear(); assertNull(assembly.accept(3, 0, 2, Arrays.copyOfRange(bytes, 0, half), 1));
        assertNull(assembly.accept(3, 1, 2, Arrays.copyOfRange(bytes, half, bytes.length), 11_000_000_002L), "expired first half is discarded");
        assertThrows(Exception.class, () -> WalkingWire.decode(new byte[WalkingWire.MAX_COMPRESSED + 1]));
        var bomb = new java.io.ByteArrayOutputStream();
        try (var gzip = new java.util.zip.GZIPOutputStream(bomb)) { gzip.write(new byte[WalkingWire.MAX_JSON + 1]); }
        assertThrows(Exception.class, () -> WalkingWire.decode(bomb.toByteArray()));
    }
    @Test void privateQueryPayloadCannotCarryDescriptionsOrMoreThan32Rules() {
        var request = new Messages.Request("query", dim, 1, 0, "a", "b", Sort.FASTEST, "", "", "");
        assertThrows(IllegalArgumentException.class, () -> new QueryV2.Request(request, QueryOptions.defaults(), 0, List.of(link())));
        var safe = link().privateWire(); assertEquals("", safe.description()); assertEquals("", safe.name());
        assertThrows(IllegalArgumentException.class, () -> new QueryV2.Request(request, QueryOptions.defaults(), 0, Collections.nCopies(33, safe)));
        assertThrows(IllegalArgumentException.class, () -> NetworkLimits.upload("x".repeat(16 * 1024 + 1)));
        assertThrows(IllegalArgumentException.class, () -> NetworkLimits.upload("车".repeat(6000)), "byte limit, not character limit");
        assertThrows(IllegalArgumentException.class, () -> NetworkLimits.upload("[".repeat(1000) + "0" + "]".repeat(1000)), "reject nesting before JSON deserialization");
        assertDoesNotThrow(() -> NetworkLimits.upload("{\"description\":\"[quoted] \\\" { text }\"}"));
    }
    @Test void walkingStepsAndReportsRoundTripAndOldV1ResponsesStillDecode() throws Exception {
        var run = new RunKey("s", 0, 0, 0);
        var ride = new RideLeg(run, "", "1", "a", "b", "c", "d", 0, 1, "B", 100_000, 200_000, false, List.of());
        var walk = new WalkLeg(UUID.randomUUID().toString(), "b", "a", "d", "c", 60, 200_000, 1, "personal");
        var journey = new Journey("j", dim, 1, "a", "a", 0, 260_000, 0, List.of(ride), List.of(walk), 5, 45);
        var tracker = new JourneyTracker(journey); tracker.correct("board"); var walking = tracker.correct("next");
        var response = new Messages.Response("poll", dim, 1, 1, 1, null, List.of(journey), List.of(), walking, "", new QueryV2.Report(260_000, false, 5));
        assertEquals(response, WireCodec.decode(WireCodec.encode(response)));
        var legacy = WireCodec.GSON.toJsonTree(response).getAsJsonObject(); legacy.remove("report");
        var legacyJourney = legacy.getAsJsonArray("journeys").get(0).getAsJsonObject(); legacyJourney.remove("walks"); legacyJourney.remove("walkingRevision"); legacyJourney.remove("transferBufferSeconds");
        legacy.add("tracking", com.google.gson.JsonNull.INSTANCE);
        var decoded = WireCodec.GSON.fromJson(legacy, Messages.Response.class);
        assertTrue(decoded.journeys().getFirst().walks().isEmpty()); assertNull(decoded.report());
    }
}
