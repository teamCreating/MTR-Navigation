package io.github.teamcreating.mtrnav.core;

/** Saved positions describe the available space, so resizing keeps the HUD on screen. */
public record HudPlacement(double x, double y, double width, double height, double scale, double margin, double availableX, double availableY) {
    public static double position(double value, double fallback) {
        return Double.isFinite(value) ? Math.max(0, Math.min(1, value)) : fallback;
    }
    public static HudPlacement at(double guiScale, int pixelWidth, int pixelHeight, int percent, double x, double y, boolean autoFit) {
        double base = Math.max(1, guiScale);
        double scale = HudScale.guiScale(base, pixelWidth, pixelHeight, percent, autoFit);
        double width = HudScale.WIDTH * scale, height = HudScale.HEIGHT * scale;
        double margin = Math.min(12, Math.min(pixelWidth, pixelHeight) * .02) / base;
        double availableX = Math.max(0, pixelWidth / base - width - margin * 2);
        double availableY = Math.max(0, pixelHeight / base - height - margin * 2);
        return new HudPlacement(margin + availableX * position(x, 1), margin + availableY * position(y, 0), width, height, scale, margin, availableX, availableY);
    }
    public static HudPlacement reminder(double guiScale, int pixelWidth, int pixelHeight, int percent, double x, double y, boolean autoFit) {
        double base = Math.max(1, guiScale);
        double pixelsPerUnit = Math.min(1.75 * HudScale.clamp(percent) / 150.0,
                Math.min(Math.max(1, pixelWidth - 24) * (autoFit ? .30 : 1) / 280,
                        Math.max(1, pixelHeight - 24) * (autoFit ? .20 : 1) / 66));
        double scale = pixelsPerUnit / base, width = 280 * scale, height = 66 * scale;
        double margin = Math.min(12, Math.min(pixelWidth, pixelHeight) * .02) / base;
        double availableX = Math.max(0, pixelWidth / base - width - margin * 2);
        double availableY = Math.max(0, pixelHeight / base - height - margin * 2);
        return new HudPlacement(margin + availableX * position(x, .5), margin + availableY * position(y, 0), width, height, scale, margin, availableX, availableY);
    }
    public boolean contains(double px, double py) { return px >= x && px <= x + width && py >= y && py <= y + height; }
    public double relativeX(double left) { return availableX == 0 ? 0 : position((left - margin) / availableX, 1); }
    public double relativeY(double top) { return availableY == 0 ? 0 : position((top - margin) / availableY, 0); }
}
