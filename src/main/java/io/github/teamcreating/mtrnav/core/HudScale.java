package io.github.teamcreating.mtrnav.core;

/** Physical HUD size is independent of Minecraft's GUI scale and bounded by the viewport. */
public final class HudScale {
    public static final int WIDTH = 180, HEIGHT = 43, DEFAULT_PERCENT = 150;
    public static final int MIN_PERCENT = 75, MAX_PERCENT = 200;
    private HudScale() {}
    public static int clamp(int percent) { return Math.max(MIN_PERCENT, Math.min(MAX_PERCENT, percent)); }
    public static int step(int percent, int direction) {
        int next = clamp(percent) + (direction < 0 ? -25 : 25);
        return clamp(next);
    }
    public static double guiScale(double minecraftScale, int pixelWidth, int pixelHeight, int percent) {
        return guiScale(minecraftScale, pixelWidth, pixelHeight, percent, true);
    }
    public static double guiScale(double minecraftScale, int pixelWidth, int pixelHeight, int percent, boolean autoFit) {
        double pixelsPerUnit = Math.min(1.5 * clamp(percent) / 100.0,
                Math.min(Math.max(1, pixelWidth - 24) * (autoFit ? .30 : 1) / WIDTH,
                        Math.max(1, pixelHeight - 24) * (autoFit ? .20 : 1) / HEIGHT));
        return pixelsPerUnit / Math.max(1, minecraftScale);
    }
}
