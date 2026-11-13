package io.github.teamcreating.mtrnav.core;

import io.github.teamcreating.mtrnav.core.UiComparison.Variant;
import io.github.teamcreating.mtrnav.core.UiLayout.Rect;

/** Shared GUI-unit bounds for rendering, clipping, controls and hit testing in every review variant. */
public record UiComparisonLayout(Rect panel, Rect header, Rect navigation, Rect rail, Rect content,
                                 Rect footer, boolean compact, int rowHeight, int gap) {
    public static UiComparisonLayout at(int width, int height, Variant variant) {
        int w = Math.max(0, Math.min(660, width - 16)), h = Math.max(0, Math.min(416, height - 16));
        var panel = new Rect((width - w) / 2, (height - h) / 2, w, h);
        var inner = panel.inset(8);
        boolean compact = w < 540 || h < 330;
        int headerH = Math.min(20, inner.height()), navH = Math.min(20, Math.max(0, inner.height() - headerH - 4));
        var header = new Rect(inner.x(), inner.y(), inner.width(), headerH);
        var nav = new Rect(inner.x(), Math.min(inner.bottom(), header.bottom() + 4), inner.width(), navH);
        int footerH = Math.min(20, Math.max(0, inner.bottom() - nav.bottom() - 8));
        var footer = new Rect(inner.x(), inner.bottom() - footerH, inner.width(), footerH);
        int bodyY = Math.min(footer.y(), nav.bottom() + 8), bodyH = Math.max(0, footer.y() - bodyY - 8);
        int railW = compact ? 0 : variant == Variant.BEDROCK ? 88 : variant == Variant.AE2 ? 22 : 0;
        var rail = new Rect(inner.x(), bodyY, railW, bodyH);
        int left = railW == 0 ? inner.x() : rail.right() + 8;
        var content = new Rect(left, bodyY, Math.max(0, inner.right() - left), bodyH);
        return new UiComparisonLayout(panel, header, nav, rail, content, footer, compact, compact ? 28 : 36, 4);
    }
    public Rect query(boolean collapsed) {
        return new Rect(content.x(), content.y(), content.width(), Math.min(content.height(), collapsed ? 24 : 56));
    }
    public Rect body(boolean collapsed) {
        var query = query(collapsed);
        int y = Math.min(content.bottom(), query.bottom() + 4);
        return new Rect(content.x(), y, content.width(), Math.max(0, content.bottom() - y));
    }
    public Rect list(boolean collapsed) {
        var body = body(collapsed);
        int w = compact ? body.width() : (int)(body.width() * .43) / 2 * 2;
        return new Rect(body.x(), body.y(), w, body.height());
    }
    public Rect details(boolean collapsed) {
        var list = list(collapsed);
        return compact ? list : new Rect(list.right() + 8, list.y(), Math.max(0, content.right() - list.right() - 8), list.height());
    }
    public int visibleRows(Rect region) { return Math.max(0, (region.height() + gap) / (rowHeight + gap)); }
    public Rect row(Rect region, int index) { return new Rect(region.x(), region.y() + index * (rowHeight + gap), Math.max(0, region.width() - 8), rowHeight); }
    public Rect hudSettings() {
        if (compact) return new Rect(content.x(), content.y(), content.width(), Math.min(76, content.height() / 2));
        return new Rect(content.x(), content.y(), 184, content.height());
    }
    public Rect hudPreview() {
        var settings = hudSettings();
        return compact ? new Rect(content.x(), settings.bottom() + 8, content.width(), Math.max(0, content.bottom() - settings.bottom() - 8))
                : new Rect(settings.right() + 12, content.y(), Math.max(0, content.right() - settings.right() - 12), content.height());
    }
}
