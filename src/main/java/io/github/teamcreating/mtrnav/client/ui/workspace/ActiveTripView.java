package io.github.teamcreating.mtrnav.client.ui.workspace;

import io.github.teamcreating.mtrnav.client.UiBridge;
import io.github.teamcreating.mtrnav.client.ui.framework.UiButton;
import io.github.teamcreating.mtrnav.client.ui.framework.UiContext;
import io.github.teamcreating.mtrnav.client.ui.framework.UiView;
import io.github.teamcreating.mtrnav.client.ui.theme.NavFormat;
import io.github.teamcreating.mtrnav.client.ui.theme.UiDraw;
import io.github.teamcreating.mtrnav.client.ui.theme.UiIcons;
import io.github.teamcreating.mtrnav.client.ui.theme.UiTokens;
import io.github.teamcreating.mtrnav.core.ConnectionPlan;
import io.github.teamcreating.mtrnav.core.JourneyTracker;
import io.github.teamcreating.mtrnav.core.ui.UiRect;
import net.minecraft.client.gui.GuiGraphics;

import static io.github.teamcreating.mtrnav.client.NavigationClient.*;

/**
 * Active journey view: prominent stage hero card + highlighted itinerary timeline + clear step controls.
 */
public final class ActiveTripView implements UiView {
    private final WorkspaceHost host;
    private final TimelineDetailPane timeline;
    private UiRect bounds = UiRect.EMPTY;
    private UiRect heroRect = UiRect.EMPTY;
    private UiRect footerRect = UiRect.EMPTY;

    private final UiButton backStepBtn;
    private final UiButton requeryBtn;
    private final UiButton endBtn;
    private final UiButton progressBtn;
    private final UiButton goPlanBtn;

    public ActiveTripView(WorkspaceHost host) {
        this.host = host;
        this.timeline = new TimelineDetailPane(host);
        this.backStepBtn = new UiButton(tr("mtrnav.previous_step"), UiButton.Style.SECONDARY, () -> {
            timeline.resetScroll();
            correct("previous");
        });
        this.requeryBtn = new UiButton(tr("mtrnav.requery"), UiButton.Style.SECONDARY, () -> {
            if (tracking != null && catalog != null) {
                UiBridge.originLocation().edited();
                if (tracking.walk() != null) {
                    origin = tracking.walk().fromStationId();
                    destination = tracking.journey().destinationStationId();
                } else if (tracking.legIndex() < tracking.journey().legs().size()) {
                    var leg = tracking.journey().legs().get(tracking.legIndex());
                    origin = leg.fromStationId();
                    destination = tracking.journey().destinationStationId();
                }
                host.switchTab(WorkspaceHost.Tab.PLAN);
                if (NewUiFixtures.enabled()) NewUiFixtures.query();
                else query();
            }
        });
        this.endBtn = new UiButton(tr("mtrnav.end_trip"), UiButton.Style.DANGER, () -> {
            if (NewUiFixtures.enabled()) tracking = null;
            else cancel();
            host.switchTab(WorkspaceHost.Tab.PLAN);
        });
        this.progressBtn = new UiButton(tr("mtrnav.boarded"), UiButton.Style.PRIMARY, () -> {
            if (tracking != null) {
                timeline.resetScroll();
                var phase = tracking.phase();
                correct(phase == JourneyTracker.Phase.RIDING || phase == JourneyTracker.Phase.WALKING ? "next" : "board");
            }
        }).icon(UiIcons.CHECK);
        this.goPlanBtn = new UiButton(tr("mtrnav.query_tab"), UiButton.Style.PRIMARY,
                () -> host.switchTab(WorkspaceHost.Tab.PLAN)).icon(UiIcons.ROUTE);
    }

    @Override
    public void layout(UiRect bounds) {
        this.bounds = bounds;
        UiRect inner = bounds.inset(12, 10, 12, 10);
        this.heroRect = inner.sliceTop(68);
        this.footerRect = inner.sliceBottom(28);
        UiRect mid = inner.trimTop(74).trimBottom(34);
        timeline.layout(mid);

        int primaryW = Math.min(136, Math.max(96, footerRect.width() / 3));
        int secW = Math.max(54, (footerRect.width() - primaryW - 18) / 3);
        int y = footerRect.y() + 4;
        backStepBtn.bounds(new UiRect(footerRect.x(), y, secW, 22));
        requeryBtn.bounds(new UiRect(footerRect.x() + secW + 6, y, secW, 22));
        endBtn.bounds(new UiRect(footerRect.x() + (secW + 6) * 2, y, secW, 22));
        progressBtn.bounds(new UiRect(footerRect.right() - primaryW, y, primaryW, 22));
        goPlanBtn.bounds(new UiRect(bounds.centerX() - 60, bounds.centerY() + 14, 120, 22));
    }

    @Override
    public void render(GuiGraphics g, UiContext ctx) {
        if (tracking == null || catalog == null) {
            UiDraw.card(g, bounds.inset(12, 10, 12, 10), UiTokens.RADIUS_MD, UiTokens.INSET, UiTokens.BORDER_SUBTLE);
            String title = tr("mtrnav.ui.no_active_trip");
            UiDraw.bold(g, title, bounds.centerX() - UiDraw.font().width(title) / 2, bounds.centerY() - 18, bounds.width() - 40, UiTokens.TEXT_PRIMARY);
            String sub = tr("mtrnav.ui.empty_hint");
            UiDraw.text(g, sub, bounds.centerX() - UiDraw.font().width(sub) / 2, bounds.centerY() - 3, bounds.width() - 40, UiTokens.TEXT_MUTED);
            goPlanBtn.render(g, ctx);
            return;
        }

        var phase = tracking.phase();
        var journey = tracking.journey();
        boolean complete = phase == JourneyTracker.Phase.COMPLETE;
        boolean riding = phase == JourneyTracker.Phase.RIDING;
        var leg = !complete && tracking.legIndex() < journey.legs().size() ? journey.legs().get(tracking.legIndex()) : null;
        int accent = tracking.walk() != null ? UiTokens.STATUS_WARN
                : leg != null ? NavFormat.routeColor(leg.routeId()) : UiTokens.PRIMARY;

        // Hero card
        UiDraw.card(g, heroRect, UiTokens.RADIUS_MD, UiTokens.SURFACE, UiTokens.BORDER_DEFAULT);
        UiDraw.roundedRect(g, heroRect.x() + 4, heroRect.y() + 6, 4, heroRect.height() - 12, 2f, accent);

        int hx = heroRect.x() + 16;
        int hw = heroRect.width() - 26;
        int pw = UiDraw.pill(g, NavFormat.phaseLabel(phase), hx, heroRect.y() + 8, 96,
                accent, UiTokens.contrastInk(accent));
        UiDraw.text(g, NavFormat.station(journey.originStationId()) + " → " + NavFormat.station(journey.destinationStationId()),
                hx + pw + 8, heroRect.y() + 11, hw - pw - 8, UiTokens.TEXT_SECONDARY);

        String headline;
        String subline;
        if (complete) {
            headline = tr("mtrnav.arrived_at", NavFormat.station(journey.destinationStationId()));
            subline = tr("mtrnav.complete");
        } else if (tracking.walk() != null) {
            var walk = tracking.walk();
            headline = tr("mtrnav.walk_to", NavFormat.station(walk.toStationId()));
            subline = tr("mtrnav.walk_estimated", walk.seconds())
                    + (walk.toPlatformId().isEmpty() ? "" : " · " + tr("mtrnav.platform", NavFormat.platform(walk.toPlatformId())));
        } else if (leg != null) {
            boolean untimed = ConnectionPlan.isGuidedJourney(journey);
            headline = tr(riding ? "mtrnav.next_alight" : "mtrnav.next_board",
                    NavFormat.station(riding ? leg.toStationId() : leg.fromStationId()));
            subline = NavFormat.route(leg.routeId()) + " · "
                    + tr("mtrnav.platform", NavFormat.platform(riding ? leg.toPlatformId() : leg.fromPlatformId()))
                    + (untimed
                    ? ""
                    : " · " + tr(riding ? "mtrnav.arrival" : "mtrnav.departure") + " "
                    + NavFormat.remaining(riding ? leg.arrival() : leg.departure()));
        } else {
            headline = tr("mtrnav.complete");
            subline = "";
        }

        UiDraw.scaledText(g, headline, hx, heroRect.y() + 26, hw, UiTokens.TEXT_PRIMARY, 1.25f);
        String guide = NavFormat.guidance();
        UiDraw.text(g, guide.isEmpty() ? subline : subline + " · " + guide,
                hx, heroRect.y() + 48, hw, guide.isEmpty() ? UiTokens.TEXT_SECONDARY : UiTokens.STATUS_WARN);

        // Timeline
        timeline.renderJourney(g, ctx, journey, tracking.legIndex());
        timeline.updateTooltips(ctx);

        // Footer controls
        progressBtn.label(tr(complete ? "mtrnav.complete"
                : riding ? "mtrnav.alighted"
                : phase == JourneyTracker.Phase.WALKING ? "mtrnav.walk_arrived" : "mtrnav.boarded"));
        progressBtn.enabled(!complete);
        backStepBtn.enabled(tracking.stepIndex() > 0 || riding);
        requeryBtn.enabled(!complete);

        backStepBtn.render(g, ctx);
        requeryBtn.render(g, ctx);
        endBtn.render(g, ctx);
        progressBtn.render(g, ctx);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (tracking == null) return goPlanBtn.mouseClicked(x, y, button);
        return backStepBtn.mouseClicked(x, y, button)
                || requeryBtn.mouseClicked(x, y, button)
                || endBtn.mouseClicked(x, y, button)
                || progressBtn.mouseClicked(x, y, button)
                || timeline.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return timeline.mouseDragged(x, y, button);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (tracking == null) return goPlanBtn.mouseReleased(x, y, button);
        return backStepBtn.mouseReleased(x, y, button)
                | requeryBtn.mouseReleased(x, y, button)
                | endBtn.mouseReleased(x, y, button)
                | progressBtn.mouseReleased(x, y, button)
                | timeline.mouseReleased(button);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        return tracking != null && timeline.mouseScrolled(x, y, vertical);
    }
}
