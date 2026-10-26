package io.github.teamcreating.mtrnav.core;

import com.google.gson.Gson;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Fixed server-owned filenames; callers never supply a path received from the network. */
public final class WalkingStorage {
    private static final Gson JSON = new Gson();
    private final Path directory;
    public WalkingStorage(Path directory) { this.directory = directory.toAbsolutePath().normalize(); }
    public WalkingLibrary.State load() throws IOException {
        Files.createDirectories(directory);
        var choices = new ArrayList<Path>(); choices.add(directory.resolve("library.json"));
        try (var files = Files.list(directory)) { choices.addAll(files.filter(p -> p.getFileName().toString().matches("backup-[0-9]+\\.json")).sorted(Comparator.comparingLong(WalkingStorage::generation).reversed()).toList()); }
        IOException failure = null;
        for (var path : choices) if (Files.isRegularFile(path)) try {
            var state = read(path);
            if (failure != null) System.getLogger("MTR Navigation").log(System.Logger.Level.WARNING, "Recovered walking library from " + path.getFileName());
            return state;
        } catch (IOException | RuntimeException error) { failure = new IOException("Invalid walking library: " + path.getFileName(), error); }
        if (failure != null) throw failure;
        return WalkingLibrary.State.empty();
    }
    private static long generation(Path p) { try { return Long.parseLong(p.getFileName().toString().replace("backup-", "").replace(".json", "")); } catch (NumberFormatException e) { return -1; } }
    public static WalkingLibrary.State read(Path path) throws IOException {
        if (Files.size(path) > 16 * 1024 * 1024) throw new IOException("Walking library exceeds size limit");
        String text = Files.readString(path); io.github.teamcreating.mtrnav.network.NetworkLimits.structure(text, 16);
        return Objects.requireNonNull(JSON.fromJson(text, WalkingLibrary.State.class));
    }
    /** Background preparation: a failed/revoked operation never overwrites the live library. */
    public Path prepare(WalkingLibrary.State current, WalkingLibrary.State next) throws IOException {
        Files.createDirectories(directory);
        Path old = directory.resolve("backup-" + current.generation() + ".json");
        if (next.revision() != current.revision() && !Files.exists(old)) Files.writeString(old, JSON.toJson(current), StandardOpenOption.CREATE_NEW);
        Path pending = Files.createTempFile(directory, "pending-", ".json");
        Files.writeString(pending, JSON.toJson(next));
        return pending;
    }
    /** Serialize commits on the owning server thread after a final permission/revision check. */
    public void commit(Path prepared) throws IOException {
        if (!prepared.toAbsolutePath().normalize().getParent().equals(directory)) throw new IOException("Invalid prepared file");
        Files.move(prepared, directory.resolve("library.json"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
    public WalkingLibrary.State rollback(long generation, WalkingLibrary.State current, String dimension, UUID actor) throws IOException {
        if (generation < 0) throw new IOException("Invalid backup revision");
        var old = read(directory.resolve("backup-" + generation + ".json"));
        long revision = current.revision() + 1;
        var entries = new HashMap<>(current.published()); entries.values().removeIf(e -> e.connection().dimension().equals(dimension));
        old.published().forEach((id, entry) -> { if (entry.connection().dimension().equals(dimension)) entries.put(id, new WalkingLibrary.Entry(entry.connection(), revision, entry.author(), actor, System.currentTimeMillis())); });
        return new WalkingLibrary.State(1, current.generation() + 1, revision, entries, current.proposals());
    }
    public List<Long> backups() throws IOException { try (var files = Files.list(directory)) { return files.filter(p -> p.getFileName().toString().matches("backup-[0-9]+\\.json")).map(WalkingStorage::generation).sorted(Comparator.reverseOrder()).limit(10).toList(); } }
    public void housekeeping(String audit) throws IOException {
        Path log = directory.resolve("audit.log");
        if (Files.exists(log) && Files.size(log) >= 1024 * 1024) {
            Files.deleteIfExists(directory.resolve("audit.4.log"));
            for (int i = 3; i >= 1; i--) if (Files.exists(directory.resolve("audit." + i + ".log"))) Files.move(directory.resolve("audit." + i + ".log"), directory.resolve("audit." + (i + 1) + ".log"), StandardCopyOption.REPLACE_EXISTING);
            Files.move(log, directory.resolve("audit.1.log"), StandardCopyOption.REPLACE_EXISTING);
        }
        Files.writeString(log, audit + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        try (var files = Files.list(directory)) {
            var backups = files.filter(p -> p.getFileName().toString().matches("backup-[0-9]+\\.json")).sorted(Comparator.comparingLong(WalkingStorage::generation).reversed()).toList();
            for (int i = 10; i < backups.size(); i++) Files.deleteIfExists(backups.get(i));
        }
    }
}
