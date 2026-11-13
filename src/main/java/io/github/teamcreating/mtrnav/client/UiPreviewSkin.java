package io.github.teamcreating.mtrnav.client;

import com.google.gson.JsonParser;
import io.github.teamcreating.mtrnav.core.UiLayout.Rect;
import net.minecraft.client.gui.GuiGraphics;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Original pixel primitives for the review prototype. No Create texture assets are sampled. */
final class UiPreviewSkin {
    private static final Map<String,Integer> colors = new HashMap<>();
    static {
        try (var stream = UiPreviewSkin.class.getResourceAsStream("/assets/mtrnav/ui/design.json")) {
            if (stream == null) throw new IllegalStateException("Missing UI design tokens");
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("colors");
            json.entrySet().forEach(e -> colors.put(e.getKey(), 0xFF000000 | Integer.parseInt(e.getValue().getAsString().substring(1),16)));
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }
    static int c(String key) { return colors.get(key); }
    static void fill(GuiGraphics g, Rect r, int color) { if (r.width() > 0 && r.height() > 0) g.fill(r.x(),r.y(),r.right(),r.bottom(),color); }
    static void edge(GuiGraphics g, Rect r, int color) {
        if (r.width() < 2 || r.height() < 2) return;
        g.fill(r.x(),r.y(),r.right(),r.y()+1,color); g.fill(r.x(),r.bottom()-1,r.right(),r.bottom(),color);
        g.fill(r.x(),r.y(),r.x()+1,r.bottom(),color); g.fill(r.right()-1,r.y(),r.right(),r.bottom(),color);
    }
    static void panel(GuiGraphics g, Rect p) {
        fill(g,p,c("metal")); edge(g,p,c("edge"));
        fill(g,p.inset(3),c("panel"));
        fill(g,new Rect(p.x()+6,p.y()+6,Math.max(0,p.width()-12),24),c("wood"));
        g.fill(p.x()+8,p.y()+30,p.right()-8,p.y()+31,c("brass"));
        for (int x : new int[]{p.x()+2,p.right()-4}) for(int y : new int[]{p.y()+2,p.bottom()-4}) {
            g.fill(x,y,x+2,y+2,c("edge")); g.fill(x,y,x+1,y+1,c("metalLight"));
        }
    }
    static void button(GuiGraphics g, Rect r, boolean primary, boolean selected, boolean active, boolean hovered) {
        fill(g,r,c(primary || selected ? "paper" : "raised")); edge(g,r,c("edge"));
        if (r.width()>2 && r.height()>2) {
            g.fill(r.x()+1,r.y()+1,r.right()-1,r.y()+2,c(primary||selected?"paperLight":"metal"));
            g.fill(r.x()+1,r.bottom()-2,r.right()-1,r.bottom()-1,c("edge"));
        }
        if (hovered && active) edge(g,r,c("brass"));
        if (!active) fill(g,r.inset(1),0x38303834);
    }
    static void field(GuiGraphics g, Rect r) {
        fill(g,r,c("paperLight")); edge(g,r,c("ink"));
        g.fill(r.x()+1,r.y()+1,r.right()-1,r.y()+2,0xFFAA9D82);
    }
}
