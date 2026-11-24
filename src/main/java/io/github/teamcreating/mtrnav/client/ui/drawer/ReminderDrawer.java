package io.github.teamcreating.mtrnav.client.ui.drawer;

import io.github.teamcreating.mtrnav.client.UiBridge;
import io.github.teamcreating.mtrnav.client.ui.framework.ConsistBar;
import io.github.teamcreating.mtrnav.client.ui.framework.SlideDrawer;
import io.github.teamcreating.mtrnav.client.ui.framework.UiButton;
import io.github.teamcreating.mtrnav.client.ui.framework.UiContext;
import io.github.teamcreating.mtrnav.client.ui.framework.UiToggle;
import io.github.teamcreating.mtrnav.client.ui.hud.CapsuleReminderHud;
import io.github.teamcreating.mtrnav.client.ui.theme.NavFormat;
import io.github.teamcreating.mtrnav.client.ui.theme.UiDraw;
import io.github.teamcreating.mtrnav.client.ui.theme.UiIcons;
import io.github.teamcreating.mtrnav.client.ui.theme.UiTokens;
import io.github.teamcreating.mtrnav.client.ui.workspace.NewUiFixtures;
import io.github.teamcreating.mtrnav.core.ArrivalReminder;
import io.github.teamcreating.mtrnav.core.Model;
import io.github.teamcreating.mtrnav.core.ui.UiRect;
import net.minecraft.client.gui.GuiGraphics;

import static io.github.teamcreating.mtrnav.client.NavigationClient.*;

/**
 * Right-side slide-over train arrival reminder drawer triggered directly from any PIDS row.
 */
public final class ReminderDrawer extends SlideDrawer {
    private final String stationId;
    private final Model.StopEvent selected;
    private int minutes;
    private Model.StopEvent live;

    private final UiButton minusBtn;
    private final UiButton plusBtn;
    private final UiToggle keepExpandedToggle;
    private final UiButton cancelOrBackBtn;
    private final UiButton armBtn;

    private UiRect cardRect = UiRect.EMPTY;
    private UiRect stepperRect = UiRect.EMPTY;

    public ReminderDrawer(String stationId, Model.StopEvent selected, int initialMinutes) {
        super(tr("mtrnav.reminder_title"), 270);
        this.stationId = stationId;
        this.selected = selected;
        this.minutes = Math.clamp(initialMinutes, 1, 60);

        this.minusBtn = new UiButton("−", UiButton.Style.SECONDARY, () -> minutes = Math.max(1, minutes - 1));
        this.plusBtn = new UiButton("+", UiButton.Style.SECONDARY, () -> minutes = Math.min(60, minutes + 1));
        this.keepExpandedToggle = new UiToggle(tr("mtrnav.ore.keep_expanded"), UiBridge::toggleReminderExpanded)
                .tooltip(tr("mtrnav.reminder_display_hint"));
        this.cancelOrBackBtn = new UiButton(tr("mtrnav.back"), UiButton.Style.SECONDARY, () -> {
            if (isCurrent()) {
                cancelReminder();
                CapsuleReminderHud.reset();
            }
            close();
        });
        this.armBtn = new UiButton(tr("mtrnav.reminder_arm"), UiButton.Style.PRIMARY, () -> {
            if (live != null && armReminder(stationId, live, minutes)) {
                CapsuleReminderHud.armed();
                close();
            }
        }).icon(UiIcons.BELL);
        refreshLive();
    }

    private boolean isCurrent() {
        return reminder != null && reminder.stationId().equals(stationId)
                && ArrivalReminder.sameEvent(selected, reminder.event());
    }

    private void refreshLive() {
        if (NewUiFixtures.enabled()) {
            live = selected;
            return;
        }
        live = boardStation.equals(stationId) && boardFresh()
                ? ArrivalReminder.unique(selected, arrivals).orElse(null)
                : null;
    }

    @Override
    public void tick() {
        refreshLive();
    }

    @Override
    protected void layoutContent(UiRect body, UiRect footer) {
        UiRect inner = body.inset(12, 8, 12, 6);
        boolean compact = inner.height() < 185;
        this.cardRect = inner.sliceTop(compact ? 64 : 78);
        this.stepperRect = new UiRect(inner.x(), cardRect.bottom() + (compact ? 14 : 18), inner.width(), 26);
        minusBtn.bounds(new UiRect(stepperRect.x() + 4, stepperRect.y() + 3, 24, 20));
        plusBtn.bounds(new UiRect(stepperRect.right() - 28, stepperRect.y() + 3, 24, 20));
        keepExpandedToggle.bounds(new UiRect(inner.x(), stepperRect.bottom() + (compact ? 6 : 12), inner.width(), 22));

        UiRect fIn = footer.inset(10, 7, 10, 7);
        int bw = (fIn.width() - 6) / 2;
        cancelOrBackBtn.bounds(new UiRect(fIn.x(), fIn.y(), bw, 22));
        armBtn.bounds(new UiRect(fIn.x() + bw + 6, fIn.y(), fIn.width() - bw - 6, 22));
    }

    @Override
    protected void renderContent(GuiGraphics g, UiContext ctx, UiRect body, UiRect footer) {
        refreshLive();
        var ev = live == null ? selected : live;
        int tint = NavFormat.routeColor(ev.routeId());

        UiDraw.enableScissor(g, body);
        // Train info summary card
        UiDraw.card(g, cardRect, UiTokens.RADIUS_MD, UiTokens.SURFACE, UiTokens.BORDER_SUBTLE);
        UiDraw.roundedRect(g, cardRect.x() + 3, cardRect.y() + 6, 3, cardRect.height() - 12, 1.5f, tint);
        int tx = cardRect.x() + 12;
        int tw = cardRect.width() - 20;
        int bw = UiDraw.lineBadge(g, NavFormat.routeCode(ev.routeId()), tx, cardRect.y() + 6, 90, tint);
        UiDraw.text(g, NavFormat.station(stationId), tx + bw + 6, cardRect.y() + 9, tw - bw - 6, UiTokens.TEXT_PRIMARY);
        UiDraw.text(g, "→ " + NavFormat.primary(ev.destination()) + " · " + tr("mtrnav.platform", NavFormat.platform(ev.platformId())),
                tx, cardRect.y() + 23, tw, UiTokens.TEXT_SECONDARY);
        String times = tr("mtrnav.arrival") + " " + (live == null ? "—" : NavFormat.remaining(ev.arrival()))
                + " · " + tr(ev.realtime() ? "mtrnav.realtime" : "mtrnav.scheduled");
        UiDraw.bold(g, times, tx, cardRect.y() + 36, tw, live == null ? UiTokens.STATUS_WARN : UiTokens.STATUS_LIVE);
        if (cardRect.height() >= 70) {
            ConsistBar.render(g, ev.consist(), tint, tx, cardRect.y() + 54, tw);
        }

        // Stepper
        UiDraw.text(g, tr("mtrnav.reminder_short"), stepperRect.x(), stepperRect.y() - 11, stepperRect.width(), UiTokens.TEXT_MUTED);
        UiDraw.card(g, stepperRect, UiTokens.RADIUS_SM, UiTokens.INSET, UiTokens.BORDER_SUBTLE);
        minusBtn.render(g, ctx);
        plusBtn.render(g, ctx);
        String lead = tr("mtrnav.reminder_lead", minutes);
        UiDraw.bold(g, lead, stepperRect.centerX() - Math.min(stepperRect.width() - 66, UiDraw.font().width(lead)) / 2,
                stepperRect.y() + 9, stepperRect.width() - 66, UiTokens.TEXT_PRIMARY);

        // Keep expanded toggle
        keepExpandedToggle.checked(UiBridge.reminderExpanded()).render(g, ctx);

        // Policy & status notes
        int noteY = stepperRect.bottom() + (body.height() < 195 ? 32 : 40);
        long now = CLOCK.now(ctx.nowNanos);
        String state = live == null ? tr("mtrnav.reminder_unconfirmed")
                : ev.departure() < now ? tr("mtrnav.reminder_stage.passed")
                : ev.arrival() - now <= minutes * 60_000L ? tr("mtrnav.reminder_immediate")
                : tr("mtrnav.reminder_keeps_running");
        noteY = UiDraw.wrapped(g, state, stepperRect.x(), noteY, stepperRect.width(), 12,
                live == null || ev.departure() < now ? UiTokens.STATUS_WARN : UiTokens.STATUS_LIVE) + 4;
        UiDraw.wrapped(g, tr("mtrnav.reminder_policy"), stepperRect.x(), noteY, stepperRect.width(), 11, UiTokens.TEXT_MUTED);
        g.disableScissor();

        // Footer buttons
        boolean cur = isCurrent();
        cancelOrBackBtn.label(tr(cur ? "mtrnav.reminder_cancel" : "mtrnav.back"))
                .style(cur ? UiButton.Style.DANGER : UiButton.Style.SECONDARY).render(g, ctx);
        armBtn.label(tr(reminder == null ? "mtrnav.reminder_arm" : cur ? "mtrnav.reminder_update" : "mtrnav.reminder_replace"))
                .enabled(live != null && ev.departure() >= now).render(g, ctx);
    }

    @Override
    protected boolean onMouseClicked(double x, double y, int button) {
        return minusBtn.mouseClicked(x, y, button)
                || plusBtn.mouseClicked(x, y, button)
                || keepExpandedToggle.mouseClicked(x, y, button)
                || cancelOrBackBtn.mouseClicked(x, y, button)
                || armBtn.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        return minusBtn.mouseReleased(x, y, button)
                | plusBtn.mouseReleased(x, y, button)
                | cancelOrBackBtn.mouseReleased(x, y, button)
                | armBtn.mouseReleased(x, y, button);
    }
}
