package io.github.teamcreating.mtrnav.core.ui;

import io.github.teamcreating.mtrnav.core.ScrollBarGeometry;

/**
 * Reusable pure-Java vertical scroll & scrollbar dragging state shared by all scrollable panels.
 */
public final class ScrollState {
    private int offset;
    private int contentHeight;
    private int viewportHeight;
    private boolean dragging;
    private double grabOffset;
    private ScrollBarGeometry geometry;

    public int offset() { return offset; }
    public int contentHeight() { return contentHeight; }
    public int viewportHeight() { return viewportHeight; }
    public boolean dragging() { return dragging; }
    public boolean scrollable() { return contentHeight > viewportHeight && viewportHeight > 0; }
    public ScrollBarGeometry geometry() { return geometry; }

    public void update(int trackTop, int viewport, int content, int minThumb) {
        this.viewportHeight = Math.max(0, viewport);
        this.contentHeight = Math.max(0, content);
        clamp();
        this.geometry = scrollable()
                ? ScrollBarGeometry.of(trackTop, viewportHeight, contentHeight, viewportHeight, offset, minThumb)
                : null;
    }

    public void setOffset(int value) {
        this.offset = value;
        clamp();
    }

    public void reset() {
        offset = 0;
        dragging = false;
        geometry = null;
    }

    public boolean scrollBy(double wheelDelta, int stepPixels) {
        if (!scrollable()) return false;
        int before = offset;
        offset -= (int) Math.round(wheelDelta * stepPixels);
        clamp();
        return offset != before;
    }

    public void ensureVisible(int itemTop, int itemHeight) {
        if (viewportHeight <= 0) return;
        if (itemTop < offset) offset = itemTop;
        else if (itemTop + itemHeight > offset + viewportHeight) offset = itemTop + itemHeight - viewportHeight;
        clamp();
    }

    public boolean beginDrag(double mouseX, double mouseY, int trackX, int trackWidth) {
        if (geometry == null) return false;
        if (mouseX < trackX - 3 || mouseX > trackX + trackWidth + 3
                || mouseY < geometry.top() || mouseY >= geometry.top() + geometry.height()) {
            return false;
        }
        dragging = true;
        grabOffset = geometry.onThumb(mouseY) ? mouseY - geometry.thumbTop() : geometry.thumbHeight() / 2.0;
        offset = geometry.offsetAtThumbTop(mouseY - grabOffset);
        clamp();
        return true;
    }

    public boolean drag(double mouseY) {
        if (!dragging || geometry == null) return false;
        offset = geometry.offsetAtThumbTop(mouseY - grabOffset);
        clamp();
        return true;
    }

    public boolean endDrag() {
        if (!dragging) return false;
        dragging = false;
        return true;
    }

    private void clamp() {
        offset = Math.clamp(offset, 0, Math.max(0, contentHeight - viewportHeight));
    }
}
