package io.github.teamcreating.mtrnav.core;

/** Allows normal UI bursts (choose two stations then search), with a bounded sustained rate. */
public final class RequestBudget {
    private double tokens = 6;
    private long updated;
    public boolean allow(long now) {
        if (updated != 0) tokens = Math.min(6, tokens + Math.max(0, now - updated) / 1_000_000_000.0 * 4);
        updated = now;
        if (tokens < 1) return false;
        tokens--; return true;
    }
}
