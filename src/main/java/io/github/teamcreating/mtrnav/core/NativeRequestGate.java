package io.github.teamcreating.mtrnav.core;

/** One native request at a time; a failed service requires an explicit, delayed retry. */
public final class NativeRequestGate {
    private boolean enabled, inFlight;
    private long nextStart, retryAfter;
    public boolean resume(long nanos) {
        if (nanos < retryAfter) return false;
        enabled = true; return true;
    }
    public boolean start(long nanos) {
        if (!enabled || inFlight || nanos < nextStart) return false;
        inFlight = true; nextStart = nanos + 500_000_000L; return true;
    }
    public void complete() { inFlight = false; }
    public void fail(long nanos) { enabled = false; inFlight = false; retryAfter = nanos + 15_000_000_000L; }
    public boolean enabled() { return enabled; }
    public void reset() { enabled = inFlight = false; nextStart = retryAfter = 0; }
}
