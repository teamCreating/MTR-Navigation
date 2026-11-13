package io.github.teamcreating.mtrnav.compat;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Minecraft-version-specific texture API; layout and atlas coordinates remain shared. */
public final class MinecraftGui {
    private MinecraftGui() {}

    public static void sprite(GuiGraphics graphics, ResourceLocation sprite, int x, int y, int width, int height) {
        graphics.blitSprite(sprite, x, y, width, height);
    }

    /** Transparent in-world sign textures are also used as compact transport pictograms. */
    public static void icon(GuiGraphics graphics, ResourceLocation texture, int x, int y, int size) {
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        blit(graphics, texture, x, y, size, size, 0, 0, 128, 128, 128, 128);
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
    }

    public static void blit(GuiGraphics graphics, ResourceLocation texture, int x, int y, int width, int height,
                            float u, float v, int sourceWidth, int sourceHeight, int atlasWidth, int atlasHeight) {
        graphics.blit(texture, x, y, width, height, u, v, sourceWidth, sourceHeight, atlasWidth, atlasHeight);
    }
}
