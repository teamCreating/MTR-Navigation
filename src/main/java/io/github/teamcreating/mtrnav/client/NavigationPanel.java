package io.github.teamcreating.mtrnav.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Keeps the navigation workspace readable without the oversized automatic GUI scale. */
abstract class NavigationPanel extends Screen {
    private NavigationTheme.Action pointerCapture;
    void capture(NavigationTheme.Action action) { if(pointerCapture!=null&&pointerCapture!=action)pointerCapture.cancelPress();pointerCapture=action; }
    @Override public boolean mouseReleased(double x,double y,int button) {
        if(button==0&&pointerCapture!=null){var action=pointerCapture;pointerCapture=null;setDragging(false);return action.mouseReleased(x,y,button);}
        return super.mouseReleased(x,y,button);
    }
    private double enteredScale = Double.NaN;
    double reviewEnteredScale() { return enteredScale; }
    NavigationPanel(Component title) { super(title); }

    private void compactScale() {
        var window = minecraft.getWindow();
        if (Double.isNaN(enteredScale)) enteredScale=window.getGuiScale();
        int normal = window.calculateScale(minecraft.options.guiScale().get(), minecraft.isEnforceUnicode());
        int limit = window.getHeight() >= 1260 && window.getWidth() >= 1920 ? 3 : 2;
        window.setGuiScale(Math.min(limit, normal));
        width = window.getGuiScaledWidth();
        height = window.getGuiScaledHeight();
    }

    @Override protected void init() {
        if(pointerCapture!=null){pointerCapture.cancelPress();pointerCapture=null;}
        compactScale();font=NavigationTheme.uiFont();
        NavigationTheme.palette(NavigationTheme.Skin.ORE);
    }

    @Override public void resize(Minecraft minecraft, int width, int height) {
        compactScale();
        super.resize(minecraft, this.width, this.height);
    }

    @Override public void renderBackground(net.minecraft.client.gui.GuiGraphics g,int mx,int my,float delta) {
        super.renderBackground(g,mx,my,delta);
        OreReview.audit(this);
    }

    @Override public void removed() {
        if(pointerCapture!=null){pointerCapture.cancelPress();pointerCapture=null;}
        super.removed();
        var window = minecraft.getWindow();
        if (!Double.isNaN(enteredScale)) window.setGuiScale(enteredScale);
    }
}
