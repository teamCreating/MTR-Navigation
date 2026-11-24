package io.github.teamcreating.mtrnav.core.ui;

/**
 * Pure time-based cubic ease-out interpolator for drawers, capsules, toggles and hover transitions.
 */
public final class UiMotion {
    private boolean initialized;
    private double from;
    private double target;
    private long startNanos;
    private long durationNanos;

    public UiMotion(double initialValue, long durationMillis) {
        this.from = initialValue;
        this.target = initialValue;
        this.durationNanos = Math.max(1L, durationMillis) * 1_000_000L;
    }

    public void snap(double value) {
        this.initialized = true;
        this.from = value;
        this.target = value;
        this.startNanos = 0L;
    }

    public void animateTo(double nextTarget, long nowNanos) {
        animateTo(nextTarget, nowNanos, durationNanos / 1_000_000L);
    }

    public void animateTo(double nextTarget, long nowNanos, long durationMillis) {
        if (!initialized) {
            snap(nextTarget);
            return;
        }
        if (Math.abs(nextTarget - target) < 1e-6) return;
        this.from = value(nowNanos);
        this.target = nextTarget;
        this.startNanos = nowNanos;
        this.durationNanos = Math.max(1L, durationMillis) * 1_000_000L;
    }

    public double target() { return target; }

    public double value(long nowNanos) {
        if (!initialized || startNanos == 0L) return target;
        double t = Math.clamp((nowNanos - startNanos) / (double) durationNanos, 0.0, 1.0);
        double eased = 1.0 - Math.pow(1.0 - t, 3.0);
        return from + (target - from) * eased;
    }

    public boolean active(long nowNanos) {
        return initialized && startNanos != 0L && nowNanos - startNanos < durationNanos;
    }
}
