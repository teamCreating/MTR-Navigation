package io.github.teamcreating.mtrnav.core;

import java.util.*;

/** Reserve the reminder's expanded bounds, including while its animation is collapsed. */
public record HudArrangement(HudPlacement navigation, HudPlacement reminder) {
    public static HudArrangement arrange(HudPlacement nav, HudPlacement reminder, double viewportWidth, double viewportHeight, double gap) {
        double margin = Math.max(nav.margin(), reminder.margin());
        double width = viewportWidth - margin * 2, height = viewportHeight - margin * 2;
        gap = Math.min(Math.max(0, gap), Math.min(width, height) * .1);
        double factor = Math.min(1, Math.max((width - gap) / (nav.width() + reminder.width()),
                (height - gap) / (nav.height() + reminder.height())));
        nav = resize(nav, factor); reminder = resize(reminder, factor);
        if (!overlaps(nav, reminder, gap)) return new HudArrangement(nav, reminder);
        double wantedX = reminder.x(), wantedY = reminder.y();
        var candidates = new ArrayList<HudPlacement>();
        for (double x : new double[]{wantedX, nav.x() - reminder.width() - gap, nav.x() + nav.width() + gap, margin, viewportWidth - margin - reminder.width()})
            for (double y : new double[]{wantedY, nav.y() - reminder.height() - gap, nav.y() + nav.height() + gap, margin, viewportHeight - margin - reminder.height()}) {
                var candidate = move(reminder, x, y);
                if (!overlaps(nav, candidate, gap)) candidates.add(candidate);
            }
        if (!candidates.isEmpty()) return new HudArrangement(nav, candidates.stream().min(Comparator
                .comparingDouble((HudPlacement p) -> Math.pow(p.x() - wantedX, 2) + Math.pow(p.y() - wantedY, 2))
                .thenComparingDouble(HudPlacement::y).thenComparingDouble(HudPlacement::x)).orElseThrow());
        // A box in the middle may block both sides. Shift it to its nearer edge.
        if (nav.height() + reminder.height() + gap <= height + .000001) {
            boolean upper = nav.y() + nav.height() / 2 <= viewportHeight / 2;
            nav = move(nav, nav.x(), upper ? margin : viewportHeight - margin - nav.height());
            reminder = move(reminder, wantedX, upper ? nav.y() + nav.height() + gap : nav.y() - gap - reminder.height());
        } else {
            boolean left = nav.x() + nav.width() / 2 <= viewportWidth / 2;
            nav = move(nav, left ? margin : viewportWidth - margin - nav.width(), nav.y());
            reminder = move(reminder, left ? nav.x() + nav.width() + gap : nav.x() - gap - reminder.width(), wantedY);
        }
        return new HudArrangement(nav, reminder);
    }
    public static boolean overlaps(HudPlacement a, HudPlacement b, double gap) {
        double epsilon = .000001;
        return a.x() < b.x() + b.width() + gap - epsilon && b.x() < a.x() + a.width() + gap - epsilon
                && a.y() < b.y() + b.height() + gap - epsilon && b.y() < a.y() + a.height() + gap - epsilon;
    }
    private static HudPlacement resize(HudPlacement p, double factor) {
        double width = p.width() * factor, height = p.height() * factor;
        double availableX = p.availableX() + p.width() - width, availableY = p.availableY() + p.height() - height;
        return new HudPlacement(p.margin() + availableX * p.relativeX(p.x()), p.margin() + availableY * p.relativeY(p.y()),
                width, height, p.scale() * factor, p.margin(), availableX, availableY);
    }
    private static HudPlacement move(HudPlacement p, double x, double y) {
        return new HudPlacement(Math.clamp(x, p.margin(), p.margin() + p.availableX()), Math.clamp(y, p.margin(), p.margin() + p.availableY()),
                p.width(), p.height(), p.scale(), p.margin(), p.availableX(), p.availableY());
    }
}
