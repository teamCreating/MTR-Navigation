package io.github.teamcreating.mtrnav.client;

import net.minecraft.client.gui.GuiGraphics;
import static io.github.teamcreating.mtrnav.client.NavigationTheme.outline;

/** Original native pixel drawing based on Ore's neutral surfaces and raised controls. */
final class OreUi {
    static final int HEADER = 0xFFE6E8EB, INK = 0xFF1E1E1F, FACE = 0xFFD0D1D4,
            BODY = 0xFF48494A, INSET = 0xFF313233, EDGE = 0xFF1E1E1F,
            GREEN = 0xFF3C8527, ACCENT = 0xFF6CC349;
    private OreUi() {}
    static void frame(GuiGraphics g,int x,int y,int w,int h,int headerHeight) {
        g.fill(x,y,x+w,y+h,BODY);outline(g,x,y,w,h,EDGE);
        g.fill(x+1,y+1,x+w-1,y+headerHeight-2,HEADER);
        g.fill(x+1,y+headerHeight-2,x+w-1,y+headerHeight,0xFFB1B2B5);
        g.fill(x+1,y+headerHeight,x+w-1,y+headerHeight+1,EDGE);
    }
    static void button(GuiGraphics g, int x, int y, int w, int h, boolean primary,
                       boolean enabled, boolean focused, boolean pressed) {
        button(g,x,y,w,h,primary,enabled,focused,pressed?1:0);
    }
    static void button(GuiGraphics g,int x,int y,int w,int h,boolean primary,boolean enabled,boolean highlighted,double depth) {
        button(g,x,y,w,h,primary,enabled,highlighted,highlighted,depth);
    }
    static void button(GuiGraphics g,int x,int y,int w,int h,boolean primary,boolean enabled,boolean hovered,boolean focused,double depth) {
        boolean pressed=depth>.5,highlighted=hovered||focused;
        int shadow = primary ? 0xFF1D4D13 : 0xFF58585A;
        int face = !enabled ? 0xFFB1B2B5 : primary
                ? pressed ? 0xFF1D4D13 : highlighted ? 0xFF2A641C : GREEN
                : pressed || highlighted ? 0xFFB1B2B5 : FACE;
        int travel=(int)Math.round(3*depth);
        g.fill(x,y+travel,x+w,y+h,EDGE);g.fill(x+1,y+1+travel,x+w-1,y+h-1,!enabled?0xFF8C8D90:shadow);
        int fy=y+1+travel,bottom=y+h-4+travel;
        g.fill(x+1,fy,x+w-1,bottom,face);
        int light=!enabled?0xFFC1C1C4:primary?pressed?0xFF4A7142:highlighted?0xFF7FA277:0xFF639D52:pressed?0xFFC1C1C4:highlighted?0xFFD0D1D3:0xFFE3E3E5;
        int bevel=!enabled?0xFFB9BABC:primary?pressed?0xFF345F2B:highlighted?0xFF6A9260:0xFF50913D:pressed?0xFFB9BABC:highlighted?0xFFC8C9CB:0xFFE3E3E5;
        g.fill(x+1,fy,x+w-1,fy+1,light);g.fill(x+1,fy,x+2,bottom,light);
        g.fill(x+w-2,fy+1,x+w-1,bottom,bevel);g.fill(x+2,bottom-1,x+w-1,bottom,bevel);
        if(enabled&&focused)outline(g,x,y+travel,w,h-travel,0xFFFFFFFF);
    }
    static void tab(GuiGraphics g,int x,int y,int w,int h,boolean selected,boolean hovered,boolean focused,boolean header) {
        g.fill(x,y,x+w,y+h,header?HEADER:selected||hovered?BODY:INSET);
        if(selected)g.fill(x+2,y+h-3,x+w-2,y+h-1,header?INK:FACE);
        if(focused)outline(g,x,y,w,h,header?INK:0xFFFFFFFF);
    }
    static void segment(GuiGraphics g,int x,int y,int w,int h,boolean selected,boolean focused) {
        g.fill(x,y,x+w,y+h,selected?FACE:INSET);outline(g,x,y,w,h,EDGE);
        if(selected)g.fill(x+1,y+h-3,x+w-1,y+h-1,ACCENT);
        if(focused)outline(g,x,y,w,h,0xFFFFFFFF);
    }
    static void toggle(GuiGraphics g,int x,int y,int w,int h,boolean hovered,boolean focused,boolean enabled,
                       io.github.teamcreating.mtrnav.core.UiToggle.Frame frame,double depth) {
        g.fill(x,y,x+w,y+h,BODY);
        int tx=x+w-36,ty=y+(h-14)/2;
        g.fill(tx,ty,tx+30,ty+14,EDGE);
        // Both halves remain underneath the thumb: I on the green half, O on the neutral half.
        g.fill(tx+1,ty+1,tx+15,ty+13,enabled?GREEN:0xFF58585A);
        g.fill(tx+15,ty+1,tx+29,ty+13,enabled?0xFF58585A:0xFF48494A);
        g.fill(tx+1,ty+1,tx+15,ty+2,enabled?0xFF639D52:0xFF6F7072);
        g.fill(tx+15,ty+1,tx+29,ty+2,0xFF6F7072);
        g.fill(tx+7,ty+4,tx+8,ty+10,enabled?0xFFFFFFFF:0xFFB1B2B5);
        outline(g,tx+19,ty+4,6,6,enabled?0xFF242425:0xFF313233);
        int thumb=tx+frame.offset(),top=ty-1,tw=frame.width(),travel=(int)Math.round(depth*2);
        g.fill(thumb,top+travel,thumb+tw,top+16,EDGE);
        g.fill(thumb+1,top+1+travel,thumb+tw-1,top+15,enabled?0xFF58585A:0xFF48494A);
        int face=enabled?hovered||focused?0xFFB1B2B5:FACE:0xFF8C8D90;
        g.fill(thumb+1,top+1+travel,thumb+tw-1,top+13+travel,face);
        g.fill(thumb+1,top+1+travel,thumb+tw-1,top+2+travel,0xFFE3E3E5);
        g.fill(thumb+1,top+1+travel,thumb+2,top+13+travel,0xFFE3E3E5);
        g.fill(thumb+tw-2,top+2+travel,thumb+tw-1,top+13+travel,0xFFB9BABC);
        if(focused&&enabled)outline(g,thumb,top+travel,tw,16-travel,0xFFFFFFFF);
    }
    static void field(GuiGraphics g,int x,int y,int w,int h,boolean focused) {
        g.fill(x,y,x+w,y+h,INSET);outline(g,x,y,w,h,focused?0xFFFFFFFF:EDGE);
        g.fill(x+1,y+1,x+w-1,y+3,0xFF242425);
    }
}
