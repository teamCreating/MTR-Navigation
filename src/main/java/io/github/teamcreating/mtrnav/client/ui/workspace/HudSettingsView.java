package io.github.teamcreating.mtrnav.client.ui.workspace;

import io.github.teamcreating.mtrnav.client.UiBridge;
import io.github.teamcreating.mtrnav.client.ui.framework.*;
import io.github.teamcreating.mtrnav.client.ui.hud.CapsuleReminderHud;
import io.github.teamcreating.mtrnav.client.ui.hud.LcdNavigationHud;
import io.github.teamcreating.mtrnav.client.ui.theme.UiDraw;
import io.github.teamcreating.mtrnav.client.ui.theme.UiIcons;
import io.github.teamcreating.mtrnav.client.ui.theme.UiTokens;
import io.github.teamcreating.mtrnav.core.HudScale;
import io.github.teamcreating.mtrnav.core.ui.UiRect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import java.util.*;

import static io.github.teamcreating.mtrnav.client.NavigationClient.tr;

/**
 * Integrated HUD settings view with:
 * 1) A true-aspect-ratio interactive screen canvas rendering the actual scaled HUDs (directly draggable & scrollable),
 * 2) A crisp 1:1 close-up live specimen card of the currently selected HUD,
 * 3) One-click full-screen world drag preview mode.
 */
public final class HudSettingsView implements UiView {
    private final WorkspaceHost host;
    private boolean editingReminder;

    private final UiButton navTabBtn;
    private final UiButton remTabBtn;
    private final UiSlider scaleSlider;
    private final UiToggle autoFitToggle;
    private final UiToggle keepExpandedToggle;
    private final List<UiButton> cornerBtns = new ArrayList<>();
    private final UiButton resetBtn;
    private final UiButton liveDragBtn;

    private UiRect bounds = UiRect.EMPTY;
    private UiRect controlsCard = UiRect.EMPTY;
    private UiRect previewCol = UiRect.EMPTY;
    private UiRect monitorCard = UiRect.EMPTY;
    private UiRect monitorStage = UiRect.EMPTY;
    private UiRect closeupCard = UiRect.EMPTY;

    // Interactive dragging inside the miniature true-aspect monitor
    private double stageScale = 1.0;
    private UiRect stageNavRect = UiRect.EMPTY;
    private UiRect stageRemRect = UiRect.EMPTY;
    private boolean draggingInStage;
    private boolean draggingStageReminder;
    private double dragOffsetGuiX, dragOffsetGuiY;

    public HudSettingsView(WorkspaceHost host) {
        this.host = host;
        this.navTabBtn = new UiButton(tr("mtrnav.hud_edit_navigation"), UiButton.Style.CHIP, () -> editingReminder = false);
        this.remTabBtn = new UiButton(tr("mtrnav.hud_edit_reminder"), UiButton.Style.CHIP, () -> editingReminder = true);
        this.scaleSlider = new UiSlider(value -> {
            if (editingReminder) UiBridge.setReminderPercent(value);
            else UiBridge.setHudPercent(value);
        });
        this.autoFitToggle = new UiToggle(tr("mtrnav.ore.auto_fit"), UiBridge::toggleAutoFit)
                .tooltip(tr("mtrnav.hud_both_fit_hint"));
        this.keepExpandedToggle = new UiToggle(tr("mtrnav.ore.keep_expanded"), UiBridge::toggleReminderExpanded)
                .tooltip(tr("mtrnav.reminder_display_hint"));

        String[] keys = {"top_left", "top_right", "bottom_left", "bottom_right"};
        for (int i = 0; i < 4; i++) {
            final int c = i;
            cornerBtns.add(new UiButton(tr("mtrnav.corner_" + keys[i]), UiButton.Style.CHIP, () -> {
                if (editingReminder) UiBridge.setReminderPosition(c % 2, c / 2, true);
                else UiBridge.setHudPosition(c % 2, c / 2, true);
            }));
        }

        this.resetBtn = new UiButton(tr("mtrnav.hud_reset_selected"), UiButton.Style.SECONDARY, () -> {
            if (editingReminder) UiBridge.resetReminder();
            else UiBridge.resetHud();
        });
        this.liveDragBtn = new UiButton(tr("mtrnav.ui.hud_live_drag"), UiButton.Style.PRIMARY, host::enterHudPreviewMode)
                .icon(UiIcons.HUD);
    }

    public boolean editingReminder() { return editingReminder; }
    public void setEditingReminder(boolean value) { this.editingReminder = value; }

    @Override
    public void layout(UiRect bounds) {
        this.bounds = bounds;
        UiRect inner = bounds.inset(12, 10, 12, 10);
        boolean wide = inner.width() >= 460;
        int leftW = wide ? Math.min(242, inner.width() * 44 / 100) : inner.width();
        this.controlsCard = wide ? inner.sliceLeft(leftW) : inner.sliceTop(Math.min(220, inner.height()));
        this.previewCol = wide ? inner.trimLeft(leftW + 10) : inner.trimTop(controlsCard.height() + 8);

        UiRect cIn = controlsCard.inset(10, 10, 10, 10);
        int half = (cIn.width() - 6) / 2;
        navTabBtn.bounds(new UiRect(cIn.x(), cIn.y(), half, 20));
        remTabBtn.bounds(new UiRect(cIn.x() + half + 6, cIn.y(), cIn.width() - half - 6, 20));

        scaleSlider.bounds(new UiRect(cIn.x(), cIn.y() + 44, cIn.width(), 18));
        autoFitToggle.bounds(new UiRect(cIn.x(), cIn.y() + 68, cIn.width(), 22));

        int cornerY = cIn.y() + 108;
        int cw = (cIn.width() - 12) / 4;
        for (int i = 0; i < 4; i++) {
            cornerBtns.get(i).bounds(new UiRect(cIn.x() + i * (cw + 4), cornerY, cw, 20));
        }
        keepExpandedToggle.bounds(new UiRect(cIn.x(), cornerY + 28, cIn.width(), 22));

        int footY = cIn.bottom() - 22;
        int resetW = Math.min(102, cIn.width() / 2 - 3);
        resetBtn.bounds(new UiRect(cIn.x(), footY, resetW, 22));
        liveDragBtn.bounds(new UiRect(cIn.x() + resetW + 6, footY, cIn.width() - resetW - 6, 22));

        // Right column: upper true-aspect interactive monitor + lower 1:1 close-up specimen
        if (previewCol.height() >= 210) {
            int closeupH = Math.min(132, Math.max(106, previewCol.height() * 38 / 100));
            this.monitorCard = previewCol.trimBottom(closeupH + 8);
            this.closeupCard = previewCol.sliceBottom(closeupH);
        } else {
            this.monitorCard = previewCol;
            this.closeupCard = UiRect.EMPTY;
        }
        computeMonitorStage();
    }

    private void computeMonitorStage() {
        if (monitorCard.isEmpty()) {
            monitorStage = UiRect.EMPTY;
            return;
        }
        var win = Minecraft.getInstance().getWindow();
        double screenW = Math.max(1.0, win.getGuiScaledWidth());
        double screenH = Math.max(1.0, win.getGuiScaledHeight());
        UiRect avail = monitorCard.inset(12, 24, 12, 12);
        if (avail.isEmpty()) {
            monitorStage = UiRect.EMPTY;
            return;
        }
        double scale = Math.min(avail.width() / screenW, avail.height() / screenH);
        int sw = Math.max(40, (int) Math.round(screenW * scale));
        int sh = Math.max(24, (int) Math.round(screenH * scale));
        this.stageScale = sw / screenW;
        this.monitorStage = new UiRect(avail.x() + (avail.width() - sw) / 2, avail.y() + (avail.height() - sh) / 2, sw, sh);
    }

    @Override
    public void render(GuiGraphics g, UiContext ctx) {
        renderLeftControls(g, ctx);
        if (!monitorCard.isEmpty()) renderMonitorCard(g, ctx);
        if (!closeupCard.isEmpty()) renderCloseupCard(g);
    }

    private void renderLeftControls(GuiGraphics g, UiContext ctx) {
        UiDraw.card(g, controlsCard, UiTokens.RADIUS_MD, UiTokens.SURFACE, UiTokens.BORDER_SUBTLE);
        UiRect cIn = controlsCard.inset(10, 10, 10, 10);

        navTabBtn.selected(!editingReminder).render(g, ctx);
        remTabBtn.selected(editingReminder).render(g, ctx);

        int pct = editingReminder ? UiBridge.reminderPercent() : UiBridge.hudPercent();
        UiDraw.text(g, tr("mtrnav.ui.scale"), cIn.x(), cIn.y() + 30, cIn.width() - 42, UiTokens.TEXT_SECONDARY);
        UiDraw.bold(g, pct + "%", cIn.right() - 36, cIn.y() + 30, 36, UiTokens.TEXT_PRIMARY);
        scaleSlider.percent(pct).render(g, ctx);

        autoFitToggle.checked(UiBridge.autoFit()).render(g, ctx);

        UiDraw.text(g, tr("mtrnav.ore.position"), cIn.x(), cIn.y() + 96, cIn.width(), UiTokens.TEXT_SECONDARY);
        double px = editingReminder ? UiBridge.reminderX() : UiBridge.hudX();
        double py = editingReminder ? UiBridge.reminderY() : UiBridge.hudY();
        for (int i = 0; i < 4; i++) {
            cornerBtns.get(i).selected(Math.abs(px - (i % 2)) < 0.01 && Math.abs(py - (i / 2)) < 0.01).render(g, ctx);
        }

        if (editingReminder) {
            keepExpandedToggle.checked(UiBridge.reminderExpanded()).render(g, ctx);
        }
        String hint = tr(editingReminder ? "mtrnav.ore.reminder_hint" : "mtrnav.ore.navigation_hint");
        int hintY = editingReminder ? cIn.y() + 166 : cIn.y() + 138;
        if (hintY + 24 < cIn.bottom() - 24) {
            UiDraw.wrapped(g, hint, cIn.x(), hintY, cIn.width(), 11, UiTokens.TEXT_MUTED);
        }

        resetBtn.render(g, ctx);
        liveDragBtn.render(g, ctx);
    }

    private void renderMonitorCard(GuiGraphics g, UiContext ctx) {
        computeMonitorStage();
        UiDraw.card(g, monitorCard, UiTokens.RADIUS_MD, UiTokens.INSET, UiTokens.BORDER_SUBTLE);
        UiDraw.bold(g, tr("mtrnav.ore.preview"), monitorCard.x() + 12, monitorCard.y() + 8, 90, UiTokens.TEXT_PRIMARY);
        String dragTip = tr("mtrnav.ui.hud_drag_short");
        int tipW = Math.min(monitorCard.width() - 110, UiDraw.font().width(dragTip));
        UiDraw.text(g, dragTip, monitorCard.right() - tipW - 12, monitorCard.y() + 9, tipW, UiTokens.TEXT_MUTED);

        if (monitorStage.isEmpty()) return;

        // Simulated scenic 16:9 Minecraft viewport backdrop (sky -> horizon -> dark ground)
        int horizonY = monitorStage.y() + monitorStage.height() * 48 / 100;
        UiDraw.enableScissor(g, monitorStage);
        UiDraw.roundedRect(g, monitorStage, UiTokens.RADIUS_SM, 0xFF1A2534);
        g.fillGradient(monitorStage.x(), monitorStage.y(), monitorStage.right(), horizonY, 0xFF283F5E, 0xFF3E5A78);
        g.fillGradient(monitorStage.x(), horizonY, monitorStage.right(), monitorStage.bottom(), 0xFF1F2B28, 0xFF121820);

        // Subtle thirds alignment grid
        int thirdW = monitorStage.width() / 3;
        int thirdH = monitorStage.height() / 3;
        UiDraw.rect(g, monitorStage.x() + thirdW, monitorStage.y(), 1, monitorStage.height(), 0x16FFFFFF);
        UiDraw.rect(g, monitorStage.x() + thirdW * 2, monitorStage.y(), 1, monitorStage.height(), 0x16FFFFFF);
        UiDraw.rect(g, monitorStage.x(), monitorStage.y() + thirdH, monitorStage.width(), 1, 0x16FFFFFF);
        UiDraw.rect(g, monitorStage.x(), monitorStage.y() + thirdH * 2, monitorStage.width(), 1, 0x16FFFFFF);

        // Center crosshair
        int cx = monitorStage.centerX(), cy = monitorStage.centerY();
        UiDraw.rect(g, cx - 3, cy, 7, 1, 0x55FFFFFF);
        UiDraw.rect(g, cx, cy - 3, 1, 7, 0x55FFFFFF);

        // Miniature bottom 9-slot hotbar for real spatial reference
        int slotSize = Math.max(6, Math.min(11, monitorStage.width() / 32));
        int hotbarW = slotSize * 9 + 10;
        int hx = monitorStage.centerX() - hotbarW / 2;
        int hy = monitorStage.bottom() - slotSize - 5;
        UiDraw.card(g, new UiRect(hx, hy, hotbarW, slotSize + 2), 2f, 0x900A0D12, 0x44FFFFFF);
        for (int i = 0; i < 9; i++) {
            int sx = hx + 1 + i * (slotSize + 1);
            UiDraw.roundedOutline(g, sx, hy + 1, slotSize, slotSize, 1f, 0.8f, i == 2 ? 0xCCFFFFFF : 0x33FFFFFF);
        }

        // Render ACTUAL live-scaled LcdNavigationHud & CapsuleReminderHud at exact HudPositions coordinates!
        var pair = UiBridge.hudLayout(true);
        var nav = pair.navigation();
        var rem = pair.reminder();

        double navX = monitorStage.x() + nav.x() * stageScale;
        double navY = monitorStage.y() + nav.y() * stageScale;
        double navW = nav.width() * stageScale;
        double navH = nav.height() * stageScale;
        stageNavRect = new UiRect((int) Math.floor(navX), (int) Math.floor(navY),
                (int) Math.ceil(navW), (int) Math.ceil(navH));
        LcdNavigationHud.renderPreviewAt(g, navX, navY, nav.scale() * stageScale);

        double remX = monitorStage.x() + rem.x() * stageScale;
        double remY = monitorStage.y() + rem.y() * stageScale;
        double remW = rem.width() * stageScale;
        double remH = rem.height() * stageScale;
        boolean showRemExpanded = UiBridge.reminderExpanded();
        double actualRemW = (showRemExpanded ? 280.0 : 236.0) * rem.scale() * stageScale;
        double actualRemH = (showRemExpanded ? 78.0 : 22.0) * rem.scale() * stageScale;
        double centeredRemX = remX + (remW - actualRemW) * 0.5;
        stageRemRect = new UiRect((int) Math.floor(remX), (int) Math.floor(remY),
                (int) Math.ceil(remW), (int) Math.ceil(remH));

        // Reserved expansion footprint dashed hint when compact
        if (!showRemExpanded) {
            UiDraw.roundedOutline(g, (float) remX, (float) remY, (float) remW, (float) remH, 4f, 0.8f,
                    UiTokens.withAlpha(UiTokens.BORDER_DEFAULT, 120));
        }
        CapsuleReminderHud.renderPreviewAt(g, centeredRemX, remY, rem.scale() * stageScale, showRemExpanded);

        // Selection highlight outline around the active HUD in the monitor
        UiRect selRect = editingReminder ? stageRemRect : stageNavRect;
        UiDraw.roundedOutline(g, selRect.x() - 1, selRect.y() - 1, selRect.width() + 2, selRect.height() + 2,
                4f, 1.4f, UiTokens.ACCENT_SKY);

        g.disableScissor();
        UiDraw.roundedOutline(g, monitorStage, UiTokens.RADIUS_SM, 1f, UiTokens.BORDER_DEFAULT);
    }

    private void renderCloseupCard(GuiGraphics g) {
        UiDraw.card(g, closeupCard, UiTokens.RADIUS_MD, UiTokens.SURFACE, UiTokens.BORDER_SUBTLE);
        String title = editingReminder ? tr("mtrnav.hud_edit_reminder") : tr("mtrnav.hud_edit_navigation");
        int pct = editingReminder ? UiBridge.reminderPercent() : UiBridge.hudPercent();
        UiDraw.bold(g, title + " · 1:1", closeupCard.x() + 12, closeupCard.y() + 8, closeupCard.width() - 90, UiTokens.TEXT_PRIMARY);
        String badge = pct + "% · " + (UiBridge.autoFit() ? tr("mtrnav.on") : tr("mtrnav.off"));
        int bw = UiDraw.font().width(badge) + 12;
        UiDraw.pill(g, badge, closeupCard.right() - bw - 10, closeupCard.y() + 6, bw,
                UiTokens.withAlpha(UiTokens.ACCENT_SKY, 38), UiTokens.ACCENT_SKY);

        UiRect stage = closeupCard.inset(12, 24, 12, 8);
        if (stage.isEmpty()) return;
        UiDraw.enableScissor(g, stage);
        if (editingReminder) {
            boolean exp = UiBridge.reminderExpanded();
            double baseW = exp ? 280.0 : 236.0;
            double baseH = exp ? 78.0 : 22.0;
            double s = Math.min(1.0, Math.min(stage.width() / baseW, stage.height() / baseH));
            double px = stage.x() + (stage.width() - baseW * s) * 0.5;
            double py = stage.y() + (stage.height() - baseH * s) * 0.5;
            CapsuleReminderHud.renderPreviewAt(g, px, py, s, exp);
        } else {
            double baseW = HudScale.WIDTH;
            double baseH = HudScale.HEIGHT;
            double s = Math.min(1.0, Math.min(stage.width() / baseW, stage.height() / baseH));
            double px = stage.x() + (stage.width() - baseW * s) * 0.5;
            double py = stage.y() + (stage.height() - baseH * s) * 0.5;
            LcdNavigationHud.renderPreviewAt(g, px, py, s);
        }
        g.disableScissor();
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (navTabBtn.mouseClicked(x, y, button) || remTabBtn.mouseClicked(x, y, button)
                || scaleSlider.mouseClicked(x, y, button)
                || autoFitToggle.mouseClicked(x, y, button)
                || (editingReminder && keepExpandedToggle.mouseClicked(x, y, button))
                || resetBtn.mouseClicked(x, y, button)
                || liveDragBtn.mouseClicked(x, y, button)) return true;
        for (var b : cornerBtns) if (b.mouseClicked(x, y, button)) return true;

        // Direct click/drag inside the 16:9 monitor preview!
        if (button == 0 && monitorStage.contains(x, y) && stageScale > 0.01) {
            boolean hitRem = stageRemRect.contains(x, y);
            boolean hitNav = stageNavRect.contains(x, y);
            if (hitRem || hitNav) {
                editingReminder = hitRem;
                draggingInStage = true;
                draggingStageReminder = hitRem;
                var pair = UiBridge.hudLayout(true);
                var p = hitRem ? pair.reminder() : pair.navigation();
                double guiX = (x - monitorStage.x()) / stageScale;
                double guiY = (y - monitorStage.y()) / stageScale;
                dragOffsetGuiX = guiX - p.x();
                dragOffsetGuiY = guiY - p.y();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (draggingInStage && button == 0 && stageScale > 0.01) {
            var pair = UiBridge.hudLayout(true);
            var p = draggingStageReminder ? pair.reminder() : pair.navigation();
            double guiX = (x - monitorStage.x()) / stageScale - dragOffsetGuiX;
            double guiY = (y - monitorStage.y()) / stageScale - dragOffsetGuiY;
            if (draggingStageReminder) UiBridge.setReminderPosition(p.relativeX(guiX), p.relativeY(guiY), false);
            else UiBridge.setHudPosition(p.relativeX(guiX), p.relativeY(guiY), false);
            return true;
        }
        return scaleSlider.mouseDragged(x, y, button);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (draggingInStage && button == 0) {
            draggingInStage = false;
            UiBridge.savePreferences();
            return true;
        }
        boolean handled = navTabBtn.mouseReleased(x, y, button)
                | remTabBtn.mouseReleased(x, y, button)
                | scaleSlider.mouseReleased(button)
                | resetBtn.mouseReleased(x, y, button)
                | liveDragBtn.mouseReleased(x, y, button);
        for (var b : cornerBtns) handled |= b.mouseReleased(x, y, button);
        return handled;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (vertical != 0 && monitorStage.contains(x, y)) {
            boolean hitRem = stageRemRect.contains(x, y);
            boolean hitNav = stageNavRect.contains(x, y);
            if (hitRem || hitNav) {
                editingReminder = hitRem;
                if (hitRem) UiBridge.changeReminderSize(vertical > 0 ? 1 : -1);
                else UiBridge.changeHudSize(vertical > 0 ? 1 : -1);
                return true;
            }
        }
        return false;
    }
}
