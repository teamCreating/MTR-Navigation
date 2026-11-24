package io.github.teamcreating.mtrnav.client.ui.theme;

import io.github.teamcreating.mtrnav.compat.MinecraftGui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Original 32x32 anti-aliased monochrome icon atlas rendered with arbitrary ARGB tinting.
 */
public enum UiIcons {
    ROUTE(0),
    BOARD(1),
    TRAIN(2),
    WALK(3),
    HUD(4),
    SEARCH(5),
    LOCATE(6),
    SWAP(7),
    STATIONS(8),
    FILTER(9),
    BELL(10),
    MAP(11),
    CHECK(12),
    CLOSE(13),
    CHEVRON_RIGHT(14),
    CHEVRON_DOWN(15);

    private static final ResourceLocation ATLAS = ResourceLocation.fromNamespaceAndPath("mtrnav", "textures/gui/icons.png");
    private static final int CELL = 32;
    private static final int ATLAS_W = 512;
    private static final int ATLAS_H = 32;

    private final int index;

    UiIcons(int index) {
        this.index = index;
    }

    public void draw(GuiGraphics g, int x, int y, int size, int color) {
        MinecraftGui.blitTinted(g, ATLAS, x, y, size, size, index * CELL, 0, CELL, CELL, ATLAS_W, ATLAS_H, color);
    }
}
