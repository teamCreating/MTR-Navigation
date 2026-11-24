package io.github.teamcreating.mtrnav.client.ui.framework;

import io.github.teamcreating.mtrnav.client.ui.theme.UiDraw;
import io.github.teamcreating.mtrnav.client.ui.theme.UiIcons;
import io.github.teamcreating.mtrnav.client.ui.theme.UiTokens;
import io.github.teamcreating.mtrnav.core.ui.UiRect;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Rounded modern text input wrapping Minecraft's native EditBox for full IME, cursor, selection and clipboard support.
 */
public final class UiTextInput {
    private final EditBox editBox;
    private UiRect bounds = UiRect.EMPTY;
    private UiRect clearBounds = UiRect.EMPTY;
    private String placeholder = "";
    private UiIcons leadingIcon;
    private boolean selectAllOnFocus;
    private boolean showClear = true;
    private Consumer<String> responder = ignored -> {};

    public UiTextInput(String label) {
        this.editBox = new EditBox(UiDraw.font(), 0, 0, 100, 18, Component.literal(label));
        this.editBox.setBordered(false);
        this.editBox.setMaxLength(128);
        this.editBox.setTextColor(UiTokens.TEXT_PRIMARY);
        this.editBox.setTextColorUneditable(UiTokens.TEXT_MUTED);
        this.editBox.setResponder(v -> responder.accept(v));
    }

    public UiTextInput placeholder(String text) {
        this.placeholder = text == null ? "" : text;
        return this;
    }

    public UiTextInput icon(UiIcons icon) { this.leadingIcon = icon; return this; }
    public UiTextInput maxLength(int max) { editBox.setMaxLength(max); return this; }
    public UiTextInput filter(Predicate<String> filter) { editBox.setFilter(filter); return this; }
    public UiTextInput selectAllOnFocus(boolean value) { this.selectAllOnFocus = value; return this; }
    public UiTextInput showClear(boolean value) { this.showClear = value; return this; }
    public UiTextInput responder(Consumer<String> responder) { this.responder = responder == null ? ignored -> {} : responder; return this; }

    public String value() { return editBox.getValue(); }
    public void setValue(String value) {
        String safe = value == null ? "" : value;
        if (!safe.equals(editBox.getValue())) editBox.setValue(safe);
    }

    public boolean focused() { return editBox.isFocused(); }
    public void setFocused(boolean focused) {
        boolean was = editBox.isFocused();
        editBox.setFocused(focused);
        if (focused && !was && selectAllOnFocus && !editBox.getValue().isEmpty()) {
            editBox.setCursorPosition(editBox.getValue().length());
            editBox.setHighlightPos(0);
        }
    }

    public void bounds(UiRect rect) {
        this.bounds = rect;
        int leftPad = leadingIcon != null ? 20 : 8;
        int rightPad = showClear ? 20 : 8;
        int innerW = Math.max(10, rect.width() - leftPad - rightPad);
        editBox.setX(rect.x() + leftPad);
        editBox.setY(rect.centerY() - 4);
        editBox.setWidth(innerW);
        editBox.setHeight(12);
        this.clearBounds = new UiRect(rect.right() - 18, rect.centerY() - 7, 14, 14);
    }

    public UiRect bounds() { return bounds; }

    public void render(GuiGraphics g, UiContext ctx) {
        if (bounds.isEmpty()) return;
        boolean focused = editBox.isFocused();
        boolean hovered = bounds.contains(ctx.mouseX, ctx.mouseY);
        UiDraw.roundedRect(g, bounds, UiTokens.RADIUS_SM, UiTokens.INSET);
        UiDraw.roundedOutline(g, bounds, UiTokens.RADIUS_SM, 1f,
                focused ? UiTokens.BORDER_FOCUS : hovered ? UiTokens.BORDER_DEFAULT : UiTokens.BORDER_SUBTLE);

        if (leadingIcon != null) {
            leadingIcon.draw(g, bounds.x() + 6, bounds.centerY() - 5, 10,
                    focused ? UiTokens.ACCENT_SKY : UiTokens.TEXT_MUTED);
        }

        if (editBox.getValue().isEmpty() && !placeholder.isEmpty()) {
            UiDraw.text(g, placeholder, editBox.getX(), editBox.getY(), editBox.getWidth(), UiTokens.TEXT_MUTED);
        }
        editBox.render(g, ctx.mouseX, ctx.mouseY, ctx.delta);

        if (showClear && !editBox.getValue().isEmpty()) {
            boolean ch = clearBounds.contains(ctx.mouseX, ctx.mouseY);
            if (ch) UiDraw.roundedRect(g, clearBounds, 3f, UiTokens.SURFACE_ELEVATED);
            UiIcons.CLOSE.draw(g, clearBounds.centerX() - 4, clearBounds.centerY() - 4, 8,
                    ch ? UiTokens.TEXT_PRIMARY : UiTokens.TEXT_MUTED);
        }
    }

    public boolean mouseClicked(double x, double y, int button) {
        if (button == 0 && showClear && !editBox.getValue().isEmpty() && clearBounds.contains(x, y)) {
            editBox.setValue("");
            setFocused(true);
            return true;
        }
        boolean inside = bounds.contains(x, y);
        if (inside && button == 0) {
            boolean wasFocused = editBox.isFocused();
            editBox.setFocused(true);
            editBox.mouseClicked(x, y, button);
            if (!wasFocused && selectAllOnFocus && !editBox.getValue().isEmpty()) {
                editBox.setCursorPosition(editBox.getValue().length());
                editBox.setHighlightPos(0);
            }
            return true;
        }
        if (editBox.isFocused()) editBox.setFocused(false);
        return false;
    }

    public boolean keyPressed(int key, int scan, int modifiers) {
        return editBox.isFocused() && editBox.keyPressed(key, scan, modifiers);
    }

    public boolean charTyped(char ch, int modifiers) {
        return editBox.isFocused() && editBox.charTyped(ch, modifiers);
    }
}
