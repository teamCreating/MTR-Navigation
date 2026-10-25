package io.github.teamcreating.mtrnav.core;

/** Shared scrollbar math for pixel content and paged station candidates. */
public record ScrollBarGeometry(int top, int height, int thumbTop, int thumbHeight, int maxOffset) {
    public static ScrollBarGeometry of(int top, int height, int total, int visible, int offset, int minimumThumb) {
        height = Math.max(1, height);
        int max = Math.max(0, total - visible);
        int thumb = total <= visible || total <= 0 ? height : Math.min(height, Math.max(minimumThumb, (int)((long)height * visible / total)));
        int position = top + (max == 0 ? 0 : (int)((long)(height - thumb) * Math.clamp(offset, 0, max) / max));
        return new ScrollBarGeometry(top, height, position, thumb, max);
    }
    public int offsetAtThumbTop(double position) {
        if (height <= thumbHeight || maxOffset == 0) return 0;
        return (int)Math.clamp(Math.round((position - top) * maxOffset / (height - thumbHeight)), 0, maxOffset);
    }
    public boolean onThumb(double y) { return y >= thumbTop && y < thumbTop + thumbHeight; }
}
