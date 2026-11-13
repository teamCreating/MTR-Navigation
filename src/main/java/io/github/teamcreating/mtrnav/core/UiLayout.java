package io.github.teamcreating.mtrnav.core;

/** Geometry shared by the review screens; all dimensions are Minecraft GUI units. */
public final class UiLayout {
    private UiLayout() {}
    public record Rect(int x, int y, int width, int height) {
        public Rect { if (width < 0 || height < 0) throw new IllegalArgumentException("Negative UI size"); }
        public int right() { return x + width; }
        public int bottom() { return y + height; }
        public boolean contains(double px, double py) { return px >= x && px < right() && py >= y && py < bottom(); }
        public boolean contains(Rect r) { return r.x >= x && r.y >= y && r.right() <= right() && r.bottom() <= bottom(); }
        public Rect inset(int p) { return new Rect(x + p, y + p, Math.max(0, width - 2 * p), Math.max(0, height - 2 * p)); }
    }
    public record Workspace(Rect panel, Rect header, Rect query, Rect tabs, Rect list, Rect detail, Rect footer,
                            Rect primary, boolean compact, int rowHeight, int gap) {
        public int visibleRows() { return Math.max(1, (list.height + gap) / (rowHeight + gap)); }
        public Rect row(int index) { return new Rect(list.x, list.y + index * (rowHeight + gap), list.width, rowHeight); }
    }
    public static Workspace workspace(int width, int height, boolean expanded) {
        int w = Math.max(0, Math.min(520, width - 16)), h = Math.max(0, Math.min(360, height - 16));
        var p = new Rect((width - w) / 2, (height - h) / 2, w, h);
        var inner = p.inset(8);
        boolean compact = w < 440 || h < 300;
        var header = new Rect(inner.x, inner.y, inner.width, Math.min(20, inner.height));
        int footerHeight = Math.min(20, Math.max(0, inner.height - header.height));
        var footer = new Rect(inner.x, inner.bottom() - footerHeight, inner.width, footerHeight);
        int primaryWidth = Math.min(96, footer.width);
        var primary = new Rect(footer.right() - primaryWidth, footer.y, primaryWidth, footer.height);
        int queryY = Math.min(footer.y, header.bottom() + 8);
        int queryH = Math.min(expanded ? compact ? 72 : 64 : 24, Math.max(0, footer.y - queryY));
        var query = new Rect(inner.x, queryY, inner.width, queryH);
        int tabsY = Math.min(footer.y, query.bottom() + 4);
        var tabs = new Rect(inner.x, tabsY, inner.width, Math.min(18, Math.max(0, footer.y - tabsY)));
        int bodyY = Math.min(footer.y, tabs.bottom() + 4);
        int bodyH = Math.max(0, footer.y - 4 - bodyY);
        int listW = compact ? inner.width : 152;
        var list = new Rect(inner.x, bodyY, listW, bodyH);
        var detail = compact ? list : new Rect(list.right() + 12, bodyY, Math.max(0, inner.right() - list.right() - 12), bodyH);
        return new Workspace(p, header, query, tabs, list, detail, footer, primary, compact, 32, 4);
    }
    public record HudWorkspace(Rect panel, Rect settings, Rect preview, Rect footer, boolean stacked) {}
    public static HudWorkspace hud(int width, int height) {
        int w = Math.max(0, Math.min(640, width - 16)), h = Math.max(0, Math.min(360, height - 16));
        var p = new Rect((width - w) / 2, (height - h) / 2, w, h);
        var footer = new Rect(p.x + 8, p.bottom() - 28, Math.max(0, w - 16), 20);
        boolean stacked = w < 560 || h < 300;
        int top = p.y + 36, available = Math.max(0, footer.y - 8 - top);
        int settingsW = stacked ? Math.max(0, w - 16) : 232;
        int previewH = stacked ? Math.min(72, Math.max(0, available - 36)) : available;
        var settings = new Rect(p.x + 8, top, settingsW, stacked ? Math.max(0, available - previewH - 8) : available);
        var preview = stacked ? new Rect(settings.x, settings.bottom() + 8, settings.width, previewH)
                : new Rect(settings.right() + 12, top, Math.max(0, p.right() - 8 - settings.right() - 12), available);
        return new HudWorkspace(p, settings, preview, footer, stacked);
    }
}
