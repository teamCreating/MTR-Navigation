package io.github.teamcreating.mtrnav.client;

import io.github.teamcreating.mtrnav.mixin.FontAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import java.util.EnumMap;

/** Local fonts: one measured Font instance is used for drawing, truncation, wrapping and editing. */
final class UiTypography {
    enum Face {
        PIXEL("pixel", "A · 像素中文"), SMOOTH("smooth", "B · 清晰中文");
        final String id, label;
        Face(String id, String label) { this.id = id; this.label = label; }
        Face next() { return this == PIXEL ? SMOOTH : PIXEL; }
    }
    static final int LINE = 12;
    static Style style(Face face) { return Style.EMPTY.withFont(ResourceLocation.fromNamespaceAndPath("mtrnav", "ui_"+face.id)); }
    static Component component(Face face,String text) { return Component.literal(text).withStyle(style(face)); }
    private static Font base;
    private static final EnumMap<Face, Font> fonts = new EnumMap<>(Face.class);
    static Font font(Face face) {
        var current = Minecraft.getInstance().font;
        if (base != current) { base = current; fonts.clear(); }
        return fonts.computeIfAbsent(face, f -> {
            var lookup = ((FontAccessor) current).mtrnav$fonts();
            var id = ResourceLocation.fromNamespaceAndPath("mtrnav", "ui_" + f.id);
            var vanilla = ResourceLocation.withDefaultNamespace("default");
            return new Font(request -> lookup.apply(request.equals(vanilla) ? id : request), false) {
                @Override public int width(String text) { return super.width(component(f,text)); }
                @Override public String plainSubstrByWidth(String text,int width) { return plainSubstrByWidth(text,width,false); }
                @Override public String plainSubstrByWidth(String text,int width,boolean backwards) {
                    int[] cps=text.codePoints().toArray();int lo=0,hi=cps.length;
                    while(lo<hi){int mid=(lo+hi+1)/2;String s=new String(cps,backwards?cps.length-mid:0,mid);if(width(s)<=width)lo=mid;else hi=mid-1;}
                    return new String(cps,backwards?cps.length-lo:0,lo);
                }
            };
        });
    }
    static String fit(Face face, String text, int width) {
        var f = font(face);
        if (width <= 0) return "";
        if (f.width(text) <= width) return text;
        return f.plainSubstrByWidth(text, Math.max(0, width - f.width("…"))) + "…";
    }
    static void text(GuiGraphics g, Face face, String value, int x, int y, int width, int color) {
        g.drawString(font(face), component(face,fit(face,value,width)), x, y, color, false);
    }
    static int paragraph(GuiGraphics g, Face face, String value, int x, int y, int width, int maxLines, int color) {
        if (width <= 0) return y;
        var f = font(face); var lines = f.split(component(face,value), width);
        int shown = Math.min(lines.size(), maxLines);
        for (int i = 0; i < shown; i++) g.drawString(f, lines.get(i), x, y + i * LINE, color, false);
        return y + shown * LINE;
    }
}
