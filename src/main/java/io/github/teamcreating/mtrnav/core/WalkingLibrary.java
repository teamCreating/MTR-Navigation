package io.github.teamcreating.mtrnav.core;

import java.util.*;
import static io.github.teamcreating.mtrnav.core.Model.*;

/** Immutable server authority. Callers supply the authenticated actor, never a UUID from a packet. */
public final class WalkingLibrary {
    public static final long WEEK = 7 * 86400_000L;
    public enum Status { PENDING, APPROVED, REJECTED, WITHDRAWN, EXPIRED }
    public record Entry(WalkingConnection connection, long revision, UUID author, UUID reviewer, long updated) {
        public Entry { if (connection == null || revision < 1 || author == null || reviewer == null || updated < 0) throw new IllegalArgumentException("Invalid published entry"); }
    }
    public record Proposal(String id, WalkingConnection value, long targetRevision, UUID author, long revision,
                           Status status, long created, UUID reviewer, String reason) {
        public Proposal {
            if (id == null || !UUID.fromString(id).toString().equals(id) || value == null || targetRevision < 0 || author == null || revision < 1 || status == null || created < 0
                    || (status == Status.APPROVED || status == Status.REJECTED) && reviewer == null) throw new IllegalArgumentException("Invalid proposal");
            WalkingConnection.text(reason, 256);
        }
    }
    public record State(int version, long generation, long revision, Map<String, Entry> published, Map<String, Proposal> proposals) {
        public State {
            if (version != 1 || generation < 0 || revision < 0 || published == null || proposals == null || published.size() > 8192 || proposals.size() > 2000)
                throw new IllegalArgumentException("Invalid walking library");
            published = Map.copyOf(published); proposals = Map.copyOf(proposals);
            var counts = new HashMap<String, Integer>(); var endpoints = new HashSet<String>();
            for (var e : published.entrySet()) if (e.getValue() == null || !e.getKey().equals(e.getValue().connection().id()) || e.getValue().revision() < 1 || e.getValue().author() == null || e.getValue().reviewer() == null)
                throw new IllegalArgumentException("Invalid published entry");
            for (var entry : published.values()) if (!endpoints.add(entry.connection().endpoints()) || counts.merge(entry.connection().dimension(), 1, Integer::sum) > 1024)
                throw new IllegalArgumentException("Duplicate or excessive published endpoints");
            for (var e : proposals.entrySet()) if (e.getValue() == null || !e.getKey().equals(e.getValue().id()) || e.getValue().author() == null || e.getValue().status() == null || e.getValue().revision() < 1)
                throw new IllegalArgumentException("Invalid proposal");
        }
        public static State empty() { return new State(1, 0, 0, Map.of(), Map.of()); }
        public List<WalkingConnection> connections(String dimension) { return published.values().stream().map(Entry::connection).filter(c -> c.dimension().equals(dimension)).sorted(Comparator.comparing(WalkingConnection::id)).toList(); }
        public List<Proposal> visible(UUID actor, boolean admin, String dimension, long now) {
            return proposals.values().stream().filter(p -> (admin || p.author().equals(actor)) && p.value().dimension().equals(dimension))
                    .map(p -> expired(p, now)).sorted(Comparator.comparingLong(Proposal::created).reversed().thenComparing(Proposal::id)).toList();
        }
    }
    public record Command(String action, String recordId, long expectedRevision, WalkingConnection value, String reason) {
        public Command {
            if (action == null || !Set.of("submit", "withdraw", "amend", "approve", "reject", "remove").contains(action)
                    || recordId == null || recordId.length() > 36 || expectedRevision < 0) throw new IllegalArgumentException("Invalid walking command");
            WalkingConnection.text(reason, 256);
        }
        public boolean adminOnly() { return Set.of("amend", "approve", "reject", "remove").contains(action); }
    }
    public static State change(State source, UUID actor, boolean admin, Command command, Catalog catalog, long now) {
        Objects.requireNonNull(actor);
        if (command.adminOnly() && !admin) throw new SecurityException("permission_denied");
        var entries = new HashMap<>(source.published()); var proposals = new HashMap<String, Proposal>();
        source.proposals().forEach((id, p) -> proposals.put(id, expired(p, now)));
        long sharedRevision = source.revision();
        if (command.action().equals("submit")) {
            var value = Objects.requireNonNull(command.value()); value.validate(catalog);
            var old = entries.get(value.id());
            if ((old == null ? 0 : old.revision()) != command.expectedRevision()) throw new IllegalStateException("revision_conflict");
            if (proposals.values().stream().filter(p -> p.status() == Status.PENDING).count() >= 1000
                    || proposals.values().stream().filter(p -> p.status() == Status.PENDING && p.author().equals(actor)).count() >= 5)
                throw new IllegalStateException("proposal_limit");
            if (proposals.values().stream().anyMatch(p -> p.status() == Status.PENDING && p.author().equals(actor) && p.value().endpoints().equals(value.endpoints())))
                throw new IllegalArgumentException("duplicate_proposal");
            String id = UUID.randomUUID().toString();
            proposals.put(id, new Proposal(id, value, command.expectedRevision(), actor, 1, Status.PENDING, now, null, ""));
        } else if (command.action().equals("remove")) {
            var old = entries.get(command.recordId());
            if (old == null || old.revision() != command.expectedRevision()) throw new IllegalStateException("revision_conflict");
            if (!old.connection().dimension().equals(catalog.dimension())) throw new IllegalArgumentException("wrong_dimension");
            entries.remove(command.recordId()); sharedRevision++;
        } else {
            var old = proposals.get(command.recordId());
            if (old == null || old.revision() != command.expectedRevision() || old.status() != Status.PENDING) throw new IllegalStateException("revision_conflict");
            if (!old.value().dimension().equals(catalog.dimension())) throw new IllegalArgumentException("wrong_dimension");
            if (!admin && !old.author().equals(actor)) throw new SecurityException("permission_denied");
            WalkingConnection value = old.value(); Status status = old.status(); String reason = command.reason();
            switch (command.action()) {
                case "withdraw" -> { if (!old.author().equals(actor)) throw new SecurityException("permission_denied"); status = Status.WITHDRAWN; }
                case "amend" -> { value = Objects.requireNonNull(command.value()); if (!value.id().equals(old.value().id())) throw new IllegalArgumentException("identity_changed"); value.validate(catalog); }
                case "reject" -> { if (reason.isBlank()) throw new IllegalArgumentException("reason_required"); status = Status.REJECTED; }
                case "approve" -> {
                    value.validate(catalog);
                    var target = entries.get(value.id());
                    if ((target == null ? 0 : target.revision()) != old.targetRevision()) throw new IllegalStateException("revision_conflict");
                    for (var e : entries.values()) if (!e.connection().id().equals(value.id()) && e.connection().endpoints().equals(value.endpoints())) throw new IllegalArgumentException("duplicate_endpoints");
                    if (target == null && (entries.size() >= 8192 || entries.values().stream().filter(e -> e.connection().dimension().equals(catalog.dimension())).count() >= 1024)) throw new IllegalStateException("connection_limit");
                    entries.put(value.id(), new Entry(value, ++sharedRevision, old.author(), actor, now)); status = Status.APPROVED;
                }
                default -> throw new IllegalArgumentException("Invalid command");
            }
            proposals.put(old.id(), new Proposal(old.id(), value, old.targetRevision(), old.author(), old.revision() + 1, status, old.created(), admin ? actor : null, reason));
        }
        // Keep pending work and at most 1000 recent completed records.
        var closed = proposals.values().stream().filter(p -> p.status() != Status.PENDING).sorted(Comparator.comparingLong(Proposal::created).reversed()).toList();
        for (int i = 1000; i < closed.size(); i++) proposals.remove(closed.get(i).id());
        return new State(1, source.generation() + 1, sharedRevision, entries, proposals);
    }
    private static Proposal expired(Proposal p, long now) {
        return p.status() == Status.PENDING && now - p.created() >= WEEK ? new Proposal(p.id(), p.value(), p.targetRevision(), p.author(), p.revision() + 1, Status.EXPIRED, p.created(), null, "expired") : p;
    }
    public static final class Rate {
        private static final long MINUTE = 60_000_000_000L;
        private final int capacity, perMinute; private final double perNanos; private double tokens; private long last;
        private final ArrayDeque<Long> accepted = new ArrayDeque<>();
        public Rate(int perMinute, int burst) { this.perMinute = perMinute; capacity = burst; tokens = burst; perNanos = perMinute / (double) MINUTE; }
        public boolean allow(long now) {
            while (!accepted.isEmpty() && now - accepted.peekFirst() >= MINUTE) accepted.removeFirst();
            if (last != 0) tokens = Math.min(capacity, tokens + Math.max(0, now - last) * perNanos);
            last = now;
            if (tokens < 1 || accepted.size() >= perMinute) return false;
            tokens--; accepted.addLast(now); return true;
        }
    }
}
