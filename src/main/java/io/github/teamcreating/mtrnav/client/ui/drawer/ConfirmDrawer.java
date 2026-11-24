package io.github.teamcreating.mtrnav.client.ui.drawer;

import io.github.teamcreating.mtrnav.client.ui.framework.SlideDrawer;
import io.github.teamcreating.mtrnav.client.ui.framework.UiButton;
import io.github.teamcreating.mtrnav.client.ui.framework.UiContext;
import io.github.teamcreating.mtrnav.client.ui.theme.UiDraw;
import io.github.teamcreating.mtrnav.client.ui.theme.UiIcons;
import io.github.teamcreating.mtrnav.client.ui.theme.UiTokens;
import io.github.teamcreating.mtrnav.core.ui.UiRect;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Clean confirmation / privacy-consent slide-over drawer replacing nested WalkingListScreen.choice dialogs.
 */
public final class ConfirmDrawer extends SlideDrawer {
    private final String detail;
    private final UiButton confirmBtn;
    private final UiButton cancelBtn;

    public ConfirmDrawer(String title, String detail, String confirmLabel, String cancelLabel,
                         Runnable onConfirm, Runnable onCancel) {
        super(title, 280);
        this.detail = detail == null ? "" : detail;
        this.confirmBtn = new UiButton(confirmLabel, UiButton.Style.PRIMARY, () -> {
            close();
            if (onConfirm != null) onConfirm.run();
        }).icon(UiIcons.CHECK);
        this.cancelBtn = new UiButton(cancelLabel, UiButton.Style.SECONDARY, () -> {
            close();
            if (onCancel != null) onCancel.run();
        });
    }

    @Override
    protected void layoutContent(UiRect body, UiRect footer) {
        UiRect fIn = footer.inset(10, 7, 10, 7);
        int bw = (fIn.width() - 6) / 2;
        cancelBtn.bounds(new UiRect(fIn.x(), fIn.y(), bw, 22));
        confirmBtn.bounds(new UiRect(fIn.x() + bw + 6, fIn.y(), fIn.width() - bw - 6, 22));
    }

    @Override
    protected void renderContent(GuiGraphics g, UiContext ctx, UiRect body, UiRect footer) {
        UiRect inner = body.inset(12, 12, 12, 12);
        UiDraw.wrapped(g, detail, inner.x(), inner.y(), inner.width(), 13, UiTokens.TEXT_SECONDARY);
        cancelBtn.render(g, ctx);
        confirmBtn.render(g, ctx);
    }

    @Override
    protected boolean onMouseClicked(double x, double y, int button) {
        return cancelBtn.mouseClicked(x, y, button) || confirmBtn.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        return cancelBtn.mouseReleased(x, y, button) | confirmBtn.mouseReleased(x, y, button);
    }
}
