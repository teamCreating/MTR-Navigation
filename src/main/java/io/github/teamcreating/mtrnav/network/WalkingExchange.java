package io.github.teamcreating.mtrnav.network;

import io.github.teamcreating.mtrnav.core.*;
import java.util.*;

public final class WalkingExchange {
    private WalkingExchange() {}
    public record Request(String action, String dimension, long id, long generation, WalkingLibrary.Command command, long backup, int page, boolean review) {
        public Request {
            if (action == null || !Set.of("read", "change", "rollback").contains(action) || dimension == null || dimension.length() > 256 || id < 0 || generation < 0 || backup < 0
                    || page < 0 || page > 79 || action.equals("change") && command == null) throw new IllegalArgumentException("Invalid walking request");
        }
    }
    public record Reply(String kind, String dimension, long id, long generation, long revision, boolean admin,
                        List<WalkingLibrary.Entry> entries, List<WalkingLibrary.Proposal> proposals, List<Long> backups, String message, int page, int entryCount, int proposalCount) {
        public Reply { entries = List.copyOf(entries); proposals = List.copyOf(proposals); backups = List.copyOf(backups); }
    }
}
