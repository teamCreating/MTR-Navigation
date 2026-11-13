package io.github.teamcreating.mtrnav.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import io.github.teamcreating.mtrnav.compat.MinecraftGui;

/** Original Create atlas coordinates; retain native borders while extending the content area. */
final class CreateGuiSkin {
    static final ResourceLocation STOCK = texture("stock_keeper"), WIDGETS = texture("widgets"), SCHEDULE = texture("schedule");
    private CreateGuiSkin() {}
    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath("mtrnav", "textures/gui/create/" + name + ".png");
    }
    static void part(GuiGraphics g, ResourceLocation texture, int x, int y, int u, int v, int w, int h) {
        if (w > 0 && h > 0) MinecraftGui.blit(g, texture, x, y, w, h, u, v, w, h, 256, 256);
    }
    static void slice(GuiGraphics g, ResourceLocation texture, int x, int y, int w, int h,
                      int u, int v, int sw, int sh, int left, int right, int top, int bottom) {
        if (w < left + right || h < top + bottom) return;
        int[] sx = {u, u + left, u + sw - right, u + sw};
        int[] sy = {v, v + top, v + sh - bottom, v + sh};
        int[] dx = {x, x + left, x + w - right, x + w};
        int[] dy = {y, y + top, y + h - bottom, y + h};
        for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++) {
            int dw = dx[col + 1] - dx[col], dh = dy[row + 1] - dy[row];
            if (dw > 0 && dh > 0) MinecraftGui.blit(g, texture, dx[col], dy[row], dw, dh, (float)sx[col], (float)sy[row],
                    sx[col + 1] - sx[col], sy[row + 1] - sy[row], 256, 256);
        }
    }
    static void wood(GuiGraphics g, int x, int y, int w, int h) {
        for (int ty = 0; ty < h; ty += 20) for (int tx = 0; tx < w; tx += 182)
            part(g, STOCK, x + tx, y + ty, 37, 48, Math.min(182, w - tx), Math.min(20, h - ty));
    }
    static void lining(GuiGraphics g, int x, int y, int w, int h) {
        // Use the timetable's quiet graphite lining behind text, with native-size pixels.
        for (int ty = 0; ty < h; ty += 32) for (int tx = 0; tx < w; tx += 32)
            part(g, SCHEDULE, x + tx, y + ty, 32, 32, Math.min(32, w - tx), Math.min(32, h - ty));
    }
    private static void band(GuiGraphics g, int x, int y, int w, int v, int h) {
        part(g, STOCK, x, y, 22, v, 15, h);
        for (int tx = 15; tx < w - 15; tx += 182)
            part(g, STOCK, x + tx, y, 37, v, Math.min(182, w - 15 - tx), h);
        part(g, STOCK, x + w - 15, y, 219, v, 15, h);
    }
    static void frame(GuiGraphics g, int x, int y, int w, int h) {
        band(g, x, y, w, 0, 36);
        // The original header contains an item-search icon. Use its matching blank wood behind our title.
        wood(g, x + 15, y + 16, w - 30, 20);
        part(g, STOCK, x - 5, y, 17, 0, 5, 16);
        part(g, STOCK, x + w, y, 234, 0, 5, 16);
        int end = h - 38;
        for (int ty = 36; ty < end; ty += 20) band(g, x, y + ty, w, 48, Math.min(20, end - ty));
        g.fill(x + 10, y + 36, x + w - 10, y + h - 38, 0x25000000);
        slice(g, STOCK, x, y + h - 38, w, 38, 22, 140, 212, 20, 15, 15, 4, 4);
    }
    static void button(GuiGraphics g, int x, int y, int w, int h, boolean active, boolean hovered, boolean down, boolean green) {
        int u = !active ? 90 : down ? 36 : hovered ? 18 : green ? 72 : 0;
        slice(g, WIDGETS, x, y, w, h, u, 0, 18, 18, 3, 3, 3, 3);
        if (active && green) g.fill(x + 3, y + 3, x + w - 3, y + h - 3, 0x50000000);
    }
    static void field(GuiGraphics g, int x, int y, int w, int h) {
        // Plain paper inset; decorative address hooks must not extend into the editable text.
        NavigationTheme.outline(g, x, y, w, h, 0xFF4A2D31);
        MinecraftGui.blit(g, STOCK, x + 1, y + 1, w - 2, h - 2, 80f, 128f, 1, 1, 256, 256);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0xFFB9A587);
        g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, 0xFFFFF5DA);
    }
    static void banner(GuiGraphics g, int x, int y, int w) {
        if (w < 16) return;
        part(g, STOCK, x, y, 64, 228, 8, 16);
        MinecraftGui.blit(g, STOCK, x + 8, y, w - 16, 16, 73f, 228f, 1, 16, 256, 256);
        part(g, STOCK, x + w - 8, y, 75, 228, 8, 16);
    }
    static void card(GuiGraphics g, int x, int y, int w, int h, boolean selected, boolean hovered) {
        if (selected) {
            slice(g, WIDGETS, x, y, w, h, 0, 46, 22, 22, 3, 3, 3, 3);
            g.fill(x + 3, y + 3, x + w - 3, y + h - 3, 0xFF2D352B);
        } else {
            slice(g, STOCK, x, y, w, h, 32, 200, 18, 18, 3, 3, 3, 3);
            g.fill(x + 3, y + 3, x + w - 3, y + h - 3, 0xFF393A33);
        }
        if (hovered && !selected) NavigationTheme.outline(g, x + 1, y + 1, w - 2, h - 2, 0xFFF8F8EC);
    }
}
