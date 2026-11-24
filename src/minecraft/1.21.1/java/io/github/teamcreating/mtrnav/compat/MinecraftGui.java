package io.github.teamcreating.mtrnav.compat;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/** Minecraft 1.21.1 rendering adapter; layout, vector math and atlas coordinates remain shared. */
public final class MinecraftGui {
    private MinecraftGui() {}

    public static void sprite(GuiGraphics graphics, ResourceLocation sprite, int x, int y, int width, int height) {
        graphics.blitSprite(sprite, x, y, width, height);
    }

    /** Transparent in-world sign textures are also used as compact transport pictograms. */
    public static void icon(GuiGraphics graphics, ResourceLocation texture, int x, int y, int size) {
        graphics.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        blit(graphics, texture, x, y, size, size, 0, 0, 128, 128, 128, 128);
        RenderSystem.disableBlend();
    }

    public static void blit(GuiGraphics graphics, ResourceLocation texture, int x, int y, int width, int height,
                            float u, float v, int sourceWidth, int sourceHeight, int atlasWidth, int atlasHeight) {
        graphics.blit(texture, x, y, width, height, u, v, sourceWidth, sourceHeight, atlasWidth, atlasHeight);
    }

    public static void blitFull(GuiGraphics graphics, ResourceLocation texture, int x, int y, int width, int height) {
        graphics.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blit(texture, x, y, width, height, 0f, 0f, 1, 1, 1, 1);
        RenderSystem.disableBlend();
    }

    /** Writes an ARGB pixel to NativeImage (1.21.1 stores ABGR internally). */
    public static void setPixel(NativeImage image, int x, int y, int argb) {
        int abgr = (argb & 0xFF00FF00) | ((argb & 0xFF) << 16) | ((argb >>> 16) & 0xFF);
        image.setPixelRGBA(x, y, abgr);
    }

    public static void blitTinted(GuiGraphics graphics, ResourceLocation texture, int x, int y, int width, int height,
                                  float u, float v, int sourceWidth, int sourceHeight, int atlasWidth, int atlasHeight, int argb) {
        graphics.flush();
        float a = ((argb >>> 24) & 0xFF) / 255f;
        float r = ((argb >>> 16) & 0xFF) / 255f;
        float g = ((argb >>> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(r, g, b, a);
        graphics.blit(texture, x, y, width, height, u, v, sourceWidth, sourceHeight, atlasWidth, atlasHeight);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    public static void quad(GuiGraphics graphics, float x1, float y1, float x2, float y2,
                            float x3, float y3, float x4, float y4, int argb) {
        quadGradient(graphics, x1, y1, argb, x2, y2, argb, x3, y3, argb, x4, y4, argb);
    }

    public static void quadGradient(GuiGraphics graphics,
                                    float x1, float y1, int c1,
                                    float x2, float y2, int c2,
                                    float x3, float y3, int c3,
                                    float x4, float y4, int c4) {
        Matrix4f pose = graphics.pose().last().pose();
        VertexConsumer vc = graphics.bufferSource().getBuffer(RenderType.gui());
        // Screen space is Y-down, so negative 2D cross product corresponds to CCW front-face in OpenGL NDC.
        float cross = (x2 - x1) * (y3 - y1) - (y2 - y1) * (x3 - x1);
        if (cross > 0f) {
            vertex(vc, pose, x1, y1, c1);
            vertex(vc, pose, x4, y4, c4);
            vertex(vc, pose, x3, y3, c3);
            vertex(vc, pose, x2, y2, c2);
        } else {
            vertex(vc, pose, x1, y1, c1);
            vertex(vc, pose, x2, y2, c2);
            vertex(vc, pose, x3, y3, c3);
            vertex(vc, pose, x4, y4, c4);
        }
    }

    private static void vertex(VertexConsumer vc, Matrix4f pose, float x, float y, int argb) {
        vc.addVertex(pose, x, y, 0f).setColor((argb >>> 16) & 0xFF, (argb >>> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF);
    }
}
