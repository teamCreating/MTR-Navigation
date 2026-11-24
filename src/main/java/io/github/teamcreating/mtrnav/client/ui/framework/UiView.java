package io.github.teamcreating.mtrnav.client.ui.framework;

import io.github.teamcreating.mtrnav.core.ui.UiRect;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Contract for a full-area view hosted inside WorkspaceScreen.
 */
public interface UiView {
    default void onActivate() {}
    default void onDeactivate() {}
    default void tick() {}
    void layout(UiRect bounds);
    void render(GuiGraphics g, UiContext ctx);
    default boolean mouseClicked(double x, double y, int button) { return false; }
    default boolean mouseDragged(double x, double y, int button, double dx, double dy) { return false; }
    default boolean mouseReleased(double x, double y, int button) { return false; }
    default boolean mouseScrolled(double x, double y, double horizontal, double vertical) { return false; }
    default boolean keyPressed(int key, int scan, int modifiers) { return false; }
    default boolean charTyped(char ch, int modifiers) { return false; }
    default boolean isTyping() { return false; }
}
