package io.github.teamcreating.mtrnav.core;

/** Local wall clocks are deliberately not used for countdowns. */
public final class ServerClock {
    private long serverMillis, receivedNanos;
    private boolean synchronizedClock;
    public void sample(long millis, long nanos) { serverMillis = millis; receivedNanos = nanos; synchronizedClock = true; }
    public boolean stale(long nanos) { return !synchronizedClock || nanos - receivedNanos >= 5_000_000_000L; }
    public long now(long nanos) { return serverMillis + Math.max(0, nanos - receivedNanos) / 1_000_000; }
    public void clear() { synchronizedClock = false; }
}
