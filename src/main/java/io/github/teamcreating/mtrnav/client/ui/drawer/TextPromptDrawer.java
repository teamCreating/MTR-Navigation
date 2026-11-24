package io.github.teamcreating.mtrnav.client.ui.drawer;

import io.github.teamcreating.mtrnav.client.ui.framework.SlideDrawer;
import io.github.teamcreating.mtrnav.client.ui.framework.UiButton;
import io.github.teamcreating.mtrnav.client.ui.framework.UiContext;
import io.github.teamcreating.mtrnav.client.ui.framework.UiTextInput;
import io.github.teamcreating.mtrnav.client.ui.theme.UiDraw;
import io.github.teamcreating.mtrnav.client.ui.theme.UiIcons;
import io.github.teamcreating.mtrnav.client.ui.theme.UiTokens;
import io.github.teamcreating.mtrnav.core.ui.UiRect;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;
import java.util.function.Consumer;

import static io.github.teamcreating.mtrnav.client.NavigationClient.tr;

/**
 * Clean single-field text input drawer replacing WalkingTextScreen.
 */
public final class TextPromptDrawer extends SlideDrawer {
    private final String fieldLabel;
    private final UiTextInput input;
    private final UiButton submitBtn;
    private final UiButton cancelBtn;
    private final Consumer<String> onSubmit;
    private String error = "";

    public TextPromptDrawer(String title, String fieldLabel, int maxLength, Consumer<String> onSubmit) {
        super(title, 280);
        this.fieldLabel = fieldLabel;
        this.onSubmit = onSubmit;
        this.input = new UiTextInput(fieldLabel).maxLength(maxLength).responder(ignored -> error = "");
        this.input.setFocused(true);
        this.cancelBtn = new UiButton(tr("mtrnav.back"), UiButton.Style.SECONDARY, this::close);
        this.submitBtn = new UiButton(tr("mtrnav.walk_confirm"), UiButton.Style.PRIMARY, this::submit).icon(UiIcons.CHECK);
    }

    private void submit() {
        try {
            if (input.value().isBlank()) throw new IllegalArgumentException();
            onSubmit.accept(input.value());
            close();
        } catch (RuntimeException e) {
            error = tr("mtrnav.walk_message.reason_required");
        }
    }

    @Override
    protected void layoutContent(UiRect body, UiRect footer) {
        UiRect inner = body.inset(12, 12, 12, 12);
        input.bounds(new UiRect(inner.x(), inner.y() + 14, inner.width(), 22));
        UiRect fIn = footer.inset(10, 7, 10, 7);
        int bw = (fIn.width() - 6) / 2;
        cancelBtn.bounds(new UiRect(fIn.x(), fIn.y(), bw, 22));
        submitBtn.bounds(new UiRect(fIn.x() + bw + 6, fIn.y(), fIn.width() - bw - 6, 22));
    }

    @Override
    protected void renderContent(GuiGraphics g, UiContext ctx, UiRect body, UiRect footer) {
        UiRect inner = body.inset(12, 12, 12, 12);
        UiDraw.text(g, fieldLabel, inner.x(), inner.y(), inner.width(), UiTokens.TEXT_SECONDARY);
        input.render(g, ctx);
        if (!error.isEmpty()) UiDraw.text(g, error, inner.x(), inner.y() + 42, inner.width(), UiTokens.STATUS_WARN);
        cancelBtn.render(g, ctx);
        submitBtn.render(g, ctx);
    }

    @Override
    protected boolean onMouseClicked(double x, double y, int button) {
        return input.mouseClicked(x, y, button) || cancelBtn.mouseClicked(x, y, button) || submitBtn.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        return cancelBtn.mouseReleased(x, y, button) | submitBtn.mouseReleased(x, y, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { submit(); return true; }
        return input.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean charTyped(char ch, int modifiers) {
        return input.charTyped(ch, modifiers);
    }

    @Override
    public boolean isTyping() {
        return input.focused();
    }
}
