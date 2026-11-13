package io.github.teamcreating.mtrnav.client;

import io.github.teamcreating.mtrnav.mixin.FontAccessor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;

/** Minecraft 1.21.4 font overloads; retain native paper editing without a text shadow. */
final class PaperFont extends Font {
    private final Font delegate;
    PaperFont(Font font) {
        super(((FontAccessor) font).mtrnav$fonts(), false);
        delegate = font;
    }
    @Override public int width(String text) { return delegate.width(text); }
    @Override public String plainSubstrByWidth(String text,int width) { return delegate.plainSubstrByWidth(text,width); }
    @Override public String plainSubstrByWidth(String text,int width,boolean backwards) { return delegate.plainSubstrByWidth(text,width,backwards); }
    @Override public int drawInBatch(String text, float x, float y, int color, boolean shadow, Matrix4f pose,
                                     MultiBufferSource buffers, DisplayMode mode, int background, int light) {
        return delegate.drawInBatch(text, x, y, color, false, pose, buffers, mode, background, light);
    }
    @Override public int drawInBatch(Component text, float x, float y, int color, boolean shadow, Matrix4f pose,
                                     MultiBufferSource buffers, DisplayMode mode, int background, int light) {
        return delegate.drawInBatch(text, x, y, color, false, pose, buffers, mode, background, light);
    }
    @Override public int drawInBatch(Component text, float x, float y, int color, boolean shadow, Matrix4f pose,
                                     MultiBufferSource buffers, DisplayMode mode, int background, int light, boolean inverseDepth) {
        return delegate.drawInBatch(text, x, y, color, false, pose, buffers, mode, background, light, inverseDepth);
    }
    @Override public int drawInBatch(FormattedCharSequence text, float x, float y, int color, boolean shadow, Matrix4f pose,
                                     MultiBufferSource buffers, DisplayMode mode, int background, int light) {
        return delegate.drawInBatch(text, x, y, color, false, pose, buffers, mode, background, light);
    }
}
