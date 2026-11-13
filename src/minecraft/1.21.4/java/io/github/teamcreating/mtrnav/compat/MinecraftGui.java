package io.github.teamcreating.mtrnav.compat;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** Minecraft-version-specific texture API; layout and atlas coordinates remain shared. */
public final class MinecraftGui {
    private MinecraftGui() {}

    public static void sprite(GuiGraphics graphics, ResourceLocation sprite, int x, int y, int width, int height) {
        graphics.blitSprite(RenderType::guiTextured, sprite, x, y, width, height);
    }

    /** Transparent in-world sign textures are also used as compact transport pictograms. */
    public static void icon(GuiGraphics graphics, ResourceLocation texture, int x, int y, int size) {
        blit(graphics, texture, x, y, size, size, 0, 0, 128, 128, 128, 128);
    }

    public static void blit(GuiGraphics graphics, ResourceLocation texture, int x, int y, int width, int height,
                            float u, float v, int sourceWidth, int sourceHeight, int atlasWidth, int atlasHeight) {
        graphics.blit(RenderType::guiTextured, texture, x, y, u, v, width, height,
                sourceWidth, sourceHeight, atlasWidth, atlasHeight);
    }
}
