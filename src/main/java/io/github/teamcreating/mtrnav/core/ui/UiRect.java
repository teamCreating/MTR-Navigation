package io.github.teamcreating.mtrnav.core.ui;

/**
 * Immutable GUI-unit rectangle shared across layout, rendering, scissoring and hit-testing.
 */
public record UiRect(int x, int y, int width, int height) {
    public static final UiRect EMPTY = new UiRect(0, 0, 0, 0);

    public UiRect {
        width = Math.max(0, width);
        height = Math.max(0, height);
    }

    public int right() { return x + width; }
    public int bottom() { return y + height; }
    public int centerX() { return x + width / 2; }
    public int centerY() { return y + height / 2; }
    public boolean isEmpty() { return width <= 0 || height <= 0; }

    public boolean contains(double px, double py) {
        return !isEmpty() && px >= x && px < right() && py >= y && py < bottom();
    }

    public UiRect inset(int padding) {
        return inset(padding, padding, padding, padding);
    }

    public UiRect inset(int horizontal, int vertical) {
        return inset(horizontal, vertical, horizontal, vertical);
    }

    public UiRect inset(int left, int top, int right, int bottom) {
        return new UiRect(x + left, y + top, Math.max(0, width - left - right), Math.max(0, height - top - bottom));
    }

    public UiRect translate(int dx, int dy) {
        return new UiRect(x + dx, y + dy, width, height);
    }

    public UiRect withY(int newY) {
        return new UiRect(x, newY, width, height);
    }

    public UiRect withHeight(int newHeight) {
        return new UiRect(x, y, width, Math.max(0, newHeight));
    }

    public UiRect sliceTop(int h) {
        return new UiRect(x, y, width, Math.min(height, Math.max(0, h)));
    }

    public UiRect trimTop(int h) {
        int used = Math.min(height, Math.max(0, h));
        return new UiRect(x, y + used, width, height - used);
    }

    public UiRect sliceBottom(int h) {
        int used = Math.min(height, Math.max(0, h));
        return new UiRect(x, bottom() - used, width, used);
    }

    public UiRect trimBottom(int h) {
        int used = Math.min(height, Math.max(0, h));
        return new UiRect(x, y, width, height - used);
    }

    public UiRect sliceLeft(int w) {
        return new UiRect(x, y, Math.min(width, Math.max(0, w)), height);
    }

    public UiRect trimLeft(int w) {
        int used = Math.min(width, Math.max(0, w));
        return new UiRect(x + used, y, width - used, height);
    }

    public UiRect sliceRight(int w) {
        int used = Math.min(width, Math.max(0, w));
        return new UiRect(right() - used, y, used, height);
    }

    public UiRect trimRight(int w) {
        int used = Math.min(width, Math.max(0, w));
        return new UiRect(x, y, width - used, height);
    }

    public UiRect intersect(UiRect other) {
        int nx = Math.max(x, other.x);
        int ny = Math.max(y, other.y);
        int nr = Math.min(right(), other.right());
        int nb = Math.min(bottom(), other.bottom());
        return nr > nx && nb > ny ? new UiRect(nx, ny, nr - nx, nb - ny) : EMPTY;
    }

    public boolean intersects(UiRect other) {
        return !intersect(other).isEmpty();
    }
}
