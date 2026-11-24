package io.github.teamcreating.mtrnav.client.ui.framework;

import io.github.teamcreating.mtrnav.client.ui.theme.UiDraw;
import io.github.teamcreating.mtrnav.client.ui.theme.UiIcons;
import io.github.teamcreating.mtrnav.client.ui.theme.UiTokens;
import io.github.teamcreating.mtrnav.core.ui.UiMotion;
import io.github.teamcreating.mtrnav.core.ui.UiRect;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Right-side slide-over drawer overlay. Absorbs background clicks, animates smoothly, and dismisses on ESC.
 */
public abstract class SlideDrawer {
    private final String title;
    private final int preferredWidth;
    private final UiMotion slide = new UiMotion(0.0, 170);
    private UiRect shellBounds = UiRect.EMPTY;
    private UiRect panelBounds = UiRect.EMPTY;
    private UiRect bodyBounds = UiRect.EMPTY;
    private UiRect footerBounds = UiRect.EMPTY;
    private UiRect closeBounds = UiRect.EMPTY;
    private Runnable onClose = () -> {};

    protected SlideDrawer(String title, int preferredWidth) {
        this.title = title;
        this.preferredWidth = preferredWidth;
        this.slide.snap(0.0);
        this.slide.animateTo(1.0, System.nanoTime(), 170);
    }

    public void setOnClose(Runnable onClose) {
        this.onClose = onClose == null ? () -> {} : onClose;
    }

    public void close() {
        onClosed();
        onClose.run();
    }

    protected void onClosed() {}
    public void tick() {}
    protected int footerHeight() { return 36; }

    public UiRect panelBounds() { return panelBounds; }
    public UiRect bodyBounds() { return bodyBounds; }
    public UiRect footerBounds() { return footerBounds; }

    public final void layout(UiRect shell) {
        this.shellBounds = shell;
        int w = Math.min(preferredWidth, Math.max(200, shell.width() - 28));
        double progress = slide.value(System.nanoTime());
        int offset = (int) Math.round((1.0 - progress) * w);
        this.panelBounds = new UiRect(shell.right() - w + offset, shell.y(), w, shell.height());
        int headerH = 34;
        int footH = footerHeight();
        this.closeBounds = new UiRect(panelBounds.right() - 28, panelBounds.y() + 8, 20, 18);
        this.bodyBounds = new UiRect(panelBounds.x(), panelBounds.y() + headerH, panelBounds.width(), Math.max(0, panelBounds.height() - headerH - footH));
        this.footerBounds = footH > 0
                ? new UiRect(panelBounds.x(), panelBounds.bottom() - footH, panelBounds.width(), footH)
                : UiRect.EMPTY;
        layoutContent(bodyBounds, footerBounds);
    }

    protected abstract void layoutContent(UiRect body, UiRect footer);
    protected abstract void renderContent(GuiGraphics g, UiContext ctx, UiRect body, UiRect footer);

    public final void render(GuiGraphics g, UiContext ctx) {
        layout(shellBounds);
        double progress = slide.value(ctx.nowNanos);
        // Dim scrim over the interior shell behind the drawer
        UiDraw.rect(g, shellBounds.x(), shellBounds.y(), shellBounds.width(), shellBounds.height(),
                UiTokens.withAlpha(0xFF06090F, (int) (135 * progress)));
        // Drawer panel nested cleanly inside outer MC frame
        UiDraw.rect(g, panelBounds.x(), panelBounds.y(), panelBounds.width(), panelBounds.height(), UiTokens.DRAWER_BG);
        UiDraw.rect(g, panelBounds.x(), panelBounds.y(), 1, panelBounds.height(), UiTokens.BORDER_DEFAULT);
        // Header
        UiDraw.bold(g, title, panelBounds.x() + 12, panelBounds.y() + 13, panelBounds.width() - 48, UiTokens.TEXT_PRIMARY);
        boolean closeHover = closeBounds.contains(ctx.mouseX, ctx.mouseY);
        if (closeHover) UiDraw.roundedRect(g, closeBounds, UiTokens.RADIUS_SM, UiTokens.SURFACE_ELEVATED);
        UiIcons.CLOSE.draw(g, closeBounds.centerX() - 5, closeBounds.centerY() - 5, 10,
                closeHover ? UiTokens.TEXT_PRIMARY : UiTokens.TEXT_SECONDARY);
        UiDraw.rect(g, panelBounds.x() + 1, panelBounds.y() + 33, panelBounds.width() - 2, 1, UiTokens.BORDER_SUBTLE);
        // Footer divider
        if (!footerBounds.isEmpty()) {
            UiDraw.rect(g, panelBounds.x() + 1, footerBounds.y(), panelBounds.width() - 2, 1, UiTokens.BORDER_SUBTLE);
        }
        renderContent(g, ctx, bodyBounds, footerBounds);
    }

    public boolean mouseClicked(double x, double y, int button) {
        if (button == 0 && closeBounds.contains(x, y)) {
            close();
            return true;
        }
        if (!panelBounds.contains(x, y)) {
            if (button == 0) close();
            return true;
        }
        return onMouseClicked(x, y, button);
    }

    protected boolean onMouseClicked(double x, double y, int button) { return true; }
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) { return false; }
    public boolean mouseReleased(double x, double y, int button) { return false; }
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) { return panelBounds.contains(x, y); }
    public boolean keyPressed(int key, int scan, int modifiers) { return false; }
    public boolean charTyped(char ch, int modifiers) { return false; }
    public boolean isTyping() { return false; }
}
