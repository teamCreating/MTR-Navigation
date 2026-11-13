package io.github.teamcreating.mtrnav.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import static io.github.teamcreating.mtrnav.core.Model.*;
import static io.github.teamcreating.mtrnav.client.NavigationClient.*;

/** Shared native screen/HUD styling. Coordinates are Minecraft GUI units. */
final class NavigationTheme {
    enum Skin { VANILLA, STEEL, BRASS, ORE }
    static Skin skin = Skin.ORE;
    static int BACKGROUND, PANEL, RAISED, SUNKEN, BORDER, SOFT, TEXT, MUTED, FAINT, ACCENT, WARNING, INPUT_TEXT, INPUT_MUTED;
    static { palette(Skin.ORE); }
    static void palette(Skin value) {
        skin=value;
        int[] c=switch(value) {
            case ORE -> new int[]{0x48494A,0x48494A,0x58585A,0x313233,0x1E1E1F,0x58585A,0xFFFFFF,0xD0D1D4,0xB1B2B5,0x6CC349,0xFFB366};
            case VANILLA -> new int[]{0xC6C6C6,0xBCBCBC,0xD2D2D2,0x8B8B8B,0x2F2F2F,0x6E6E6E,0x2F2F2F,0x3F3F3F,0x5F5F5F,0xAA6600,0x885000};
            case STEEL -> new int[]{0x4B4B46,0x41413D,0x4D4D46,0x2D2D2B,0x8A8981,0x5B5B55,0xF1EEE5,0xC4C1B6,0xB4B2A7,0xDBC18A,0xE7B778};
            case BRASS -> new int[]{0x1A1611,0x221D15,0x2A2419,0x151109,0x4D4230,0x3A3223,0xF5EFE0,0xB0A690,0x9C917B,0xE8C57C,0xE8B077};
        };
        BACKGROUND=0xFF000000|c[0]; PANEL=0xFF000000|c[1]; RAISED=0xFF000000|c[2]; SUNKEN=0xFF000000|c[3];
        BORDER=0xFF000000|c[4]; SOFT=0xFF000000|c[5]; TEXT=0xFF000000|c[6]; MUTED=0xFF000000|c[7]; FAINT=0xFF000000|c[8];
        ACCENT=0xFF000000|c[9]; WARNING=0xFF000000|c[10]; INPUT_TEXT=skin==Skin.ORE?0xFFFFFFFF:0xFFF2EEDC; INPUT_MUTED=skin==Skin.ORE?0xFFB1B2B5:0xFFB8B7AC;
    }
    static Font uiFont() { return Minecraft.getInstance().font; }
    static String skinName() { return tr("mtrnav.skin."+skin.name().toLowerCase(java.util.Locale.ROOT)); }
    static int headerInk() { return skin==Skin.ORE?OreUi.INK:TEXT; }
    static void nextSkin() { palette(Skin.values()[(skin.ordinal()+1)%3]); NavigationPreferences.setSkin(skin.name()); }
    static void large(GuiGraphics g,String value,int x,int y,int w,int color,float scale) {
        if(w<=0)return;
        g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(scale,scale,1);
        text(g,value,0,0,(int)(w/scale),color);g.pose().popPose();
    }
    static int badge(GuiGraphics g,String label,int x,int y,int maxWidth,int color) {
        int w=Math.min(maxWidth,uiFont().width(label)+8); if(w<8)return 0;
        g.fill(x,y,x+w,y+12,color); text(g,label,x+4,y+2,w-8,badgeInk(color));return w;
    }
    /** MTR-owned assets are referenced in place; resource packs can override them. */
    static void transportIcon(GuiGraphics g, RouteType type, int x, int y, int size) {
        String name = switch(type) { case LIGHT_RAIL -> "light_rail_1"; case HIGH_SPEED -> "xrl_2"; default -> "train"; };
        io.github.teamcreating.mtrnav.compat.MinecraftGui.icon(g, ResourceLocation.fromNamespaceAndPath("mtr", "textures/block/sign/" + name + ".png"), x, y, size);
    }
    private static void journeyChain(GuiGraphics g, java.util.List<io.github.teamcreating.mtrnav.core.JourneyChain.Item> source, int x, int y, int width) {
        if (source.isEmpty() || width < 24) return;
        var items = new java.util.ArrayList<>(source);
        int rides = (int)items.stream().filter(item -> !item.walking()).count();
        int walkingWidth = items.stream().filter(io.github.teamcreating.mtrnav.core.JourneyChain.Item::walking).mapToInt(item -> 14 + uiFont().width(Long.toString((item.seconds()+59L)/60))).sum();
        int badgeLimit = Math.clamp((width - walkingWidth - rides*18 - Math.max(0, items.size()-1)*9) / Math.max(1, rides), 18, 70);
        java.util.function.ToIntFunction<io.github.teamcreating.mtrnav.core.JourneyChain.Item> measured = item -> item.walking()
                ? 14 + uiFont().width(Long.toString((item.seconds()+59L)/60)) : 18 + Math.min(badgeLimit, uiFont().width(routeCode(item.routeId()))+8);
        int hidden = 0;
        while (items.size() > 1 && items.stream().mapToInt(measured).sum() + (items.size()-1)*9 + (hidden > 0 ? 24 : 0) > width) {
            items.remove(items.size() > 2 ? items.size()-2 : 1); hidden++;
        }
        int cursor=x;
        for(int i=0;i<items.size();i++) {
            var item=items.get(i);
            if(i>0){
                if(hidden>0&&i==items.size()-1){text(g,"+"+hidden,cursor,y+3,20,FAINT);cursor+=24;}
                text(g,"›",cursor,y+2,6,FAINT);cursor+=9;
            }
            if(item.walking()){
                mark(g,Mark.WALK,cursor,y+2,MUTED);String minutes=Long.toString((item.seconds()+59L)/60);text(g,minutes,cursor+12,y+3,measured.applyAsInt(item)-12,MUTED);
            }else{
                int bw=badge(g,routeCode(item.routeId()),cursor,y+1,Math.min(badgeLimit,Math.max(8,width-(cursor-x)-18)),routeColor(item.routeId()));
                transportIcon(g,catalog==null?RouteType.UNKNOWN:catalog.routeType(item.routeId()),cursor+bw+3,y,14);
            }
            cursor+=measured.applyAsInt(item);
        }
        if(hidden>0&&items.size()==1)text(g,"+"+hidden,cursor+3,y+3,Math.max(0,x+width-cursor-3),FAINT);
    }
    private static int badgeInk(int color){
        double[] rgb={((color>>16)&255)/255.0,((color>>8)&255)/255.0,(color&255)/255.0};
        for(int i=0;i<3;i++)rgb[i]=rgb[i]<=.04045?rgb[i]/12.92:Math.pow((rgb[i]+.055)/1.055,2.4);
        return .2126*rgb[0]+.7152*rgb[1]+.0722*rgb[2]>.179?0xFF141A12:0xFFF7F5E8;
    }
    enum Mark { NONE, SEARCH, STATIONS, WALK, CLOSE, SWAP, CHECK, GEAR, DOWN, BACK, BOX, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }
    private NavigationTheme() {}
    static String station(String id) {
        var station = catalog == null ? null : catalog.station(id);
        return station == null ? tr("mtrnav.station_pending") : primary(station.name());
    }
    static String route(String id) {
        if (catalog == null) return tr("mtrnav.route_pending");
        return catalog.routes().stream().filter(r -> r.id().equals(id)).findFirst().map(r -> primary(r.name())).orElse(tr("mtrnav.route_pending"));
    }
    static String platform(String id) {
        var platform = catalog == null ? null : catalog.platform(id);
        return platform == null ? tr("mtrnav.platform_pending") : displayName(platform.name());
    }
    static String routeCode(String id) {
        String label = route(id);
        var code = java.util.regex.Pattern.compile("[A-Z]{1,3}[0-9]{1,2}[A-Z]?|[0-9]{1,2}号线").matcher(label);
        return code.find() ? code.group() : label;
    }
    static String primary(String name) { return name == null ? "" : name.split("\\|", 2)[0].trim(); }
    static int routeColor(String id) {
        return catalog == null ? ACCENT : catalog.routes().stream().filter(r -> r.id().equals(id))
                .map(r -> 0xFF000000 | r.color() & 0xFFFFFF).findFirst().orElse(ACCENT);
    }
    static void text(GuiGraphics g, String text, int x, int y, int width, int color) {
        var font = uiFont();
        if (width <= 0) return;
        String visible = font.width(text) <= width ? text : font.plainSubstrByWidth(text, Math.max(0, width - font.width("…"))) + "…";
        g.drawString(font, visible, x, y, color, false);
    }
    static void listRow(GuiGraphics g,int x,int y,int w,int h,boolean selected,boolean hovered,boolean stripe) {
        if(selected||hovered||stripe)g.fill(x,y,x+w,y+h,selected?RAISED:stripe?PANEL:SUNKEN);
        if(selected||hovered)outline(g,x,y,w,h,skin==Skin.ORE?(selected?0xFFD0D1D4:0xFFFFFFFF):selected?ACCENT:BORDER);
        if(skin==Skin.ORE&&selected)g.fill(x+2,y+2,x+4,y+h-2,0xFFFFFFFF);
    }
    static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        g.fill(x, y, x + w, y + 1, color); g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color); g.fill(x + w - 1, y, x + w, y + h, color);
    }
    static void icon(GuiGraphics g, int x, int y, int size) {
        if(skin==Skin.ORE){
            // Open route mark: two station nodes, with no filled badge behind the title.
            g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(size/16f,size/16f,1);
            g.fill(3,10,12,12,OreUi.GREEN);g.fill(10,4,12,12,OreUi.GREEN);
            g.fill(1,8,7,14,OreUi.INK);g.fill(2,9,6,13,OreUi.HEADER);
            g.fill(9,2,15,8,OreUi.INK);g.fill(10,3,14,7,OreUi.HEADER);
            g.pose().popPose();return;
        }
        g.pose().pushPose(); g.pose().translate(x, y, 0); g.pose().scale(size / 16f, size / 16f, 1);
        g.renderItem(net.minecraft.world.item.Items.COMPASS.getDefaultInstance(), 0, 0);
        g.pose().popPose();
    }
    static void frame(GuiGraphics g,int x,int y,int w,int h) {
        g.fill(x,y,x+w,y+h,BACKGROUND);outline(g,x,y,w,h,skin==Skin.VANILLA?0xFF000000:BORDER);
        if(skin==Skin.ORE)OreUi.frame(g,x,y,w,h,28);
        if(skin==Skin.VANILLA){
            g.fill(x+1,y+1,x+w-1,y+3,0xFFFFFFFF);g.fill(x+1,y+1,x+3,y+h-1,0xFFFFFFFF);
            g.fill(x+2,y+h-3,x+w-1,y+h-1,0xFF555555);g.fill(x+w-3,y+2,x+w-1,y+h-1,0xFF555555);
        }
    }
    private static final ResourceLocation LINING=ResourceLocation.fromNamespaceAndPath("mtrnav","textures/gui/graphite_lining.png");
    static void lining(GuiGraphics g,int x,int y,int w,int h){
        if(w<=0||h<=0)return;
        if(skin!=Skin.STEEL){g.fill(x,y,x+w,y+h,SUNKEN);return;}
        for(int ty=0;ty<h;ty+=64)for(int tx=0;tx<w;tx+=64){
            int tw=Math.min(64,w-tx),th=Math.min(64,h-ty);
            io.github.teamcreating.mtrnav.compat.MinecraftGui.blit(g,LINING,x+tx,y+ty,tw,th,0,0,tw,th,64,64);
        }
    }
    static void recess(GuiGraphics g,int x,int y,int w,int h) {lining(g,x,y,w,h);outline(g,x,y,w,h,SOFT);}
    /** Original integer-pixel paper trim, inspired by the user's reference; no upstream texture is copied. */
    static void headerTrim(GuiGraphics g,int x,int y,int w){
        if(skin==Skin.ORE)return;
        int ink=0xFF30271D,edge=0xFF987B56,paper=0xFFE5C69D,light=0xFFFFE7C1,shade=0xFFC3A078;
        g.fill(x+3,y-5,x+w-3,y+8,ink);
        g.fill(x+4,y-4,x+w-4,y+7,paper);
        g.fill(x+5,y-4,x+w-5,y-2,light);g.fill(x+5,y+5,x+w-5,y+7,shade);
        g.fill(x+5,y-1,x+w-5,y,0xFFF3D8AF);g.fill(x+8,y+2,x+w-8,y+3,0xFFDDb98D);
        g.fill(x+6,y+4,x+w-6,y+5,0xFFEED2A9);outline(g,x+3,y-5,w-6,13,ink);
        for(int side=0;side<2;side++){
            int sx=side==0?x-3:x+w-4;
            g.fill(sx,y-1,sx+7,y+3,ink);g.fill(sx+1,y,sx+6,y+2,light);
            g.fill(sx+1,y+2,sx+6,y+8,edge);g.fill(sx+2,y+2,sx+5,y+7,paper);
            g.fill(sx,y+8,sx+7,y+10,ink);g.fill(sx+1,y+8,sx+6,y+9,paper);
        }
        g.fill(x+4,y-3,x+5,y+6,edge);g.fill(x+w-5,y-3,x+w-4,y+6,edge);
    }
    static void detailPanel(GuiGraphics g,int x,int y,int w,int h){
        int surface=skin==Skin.VANILLA?0xFFE0E0E0:SUNKEN;
        if(skin==Skin.STEEL)lining(g,x,y,w,h);else g.fill(x,y,x+w,y+h,surface);outline(g,x,y,w,h,skin==Skin.VANILLA?0xFF666666:BORDER);
        if(skin==Skin.VANILLA){g.fill(x+1,y+1,x+w-1,y+2,0xFF969696);g.fill(x+1,y+1,x+2,y+h-1,0xFF969696);g.fill(x+1,y+h-2,x+w-1,y+h-1,0xFFFFFFFF);g.fill(x+w-2,y+1,x+w-1,y+h-1,0xFFFFFFFF);}
    }
    static void hudFrame(GuiGraphics g,int w,int h,int tint) {
        g.fill(0,0,w,h,0xF2313233);outline(g,0,0,w,h,OreUi.EDGE);g.fill(1,1,w-1,19,OreUi.BODY);g.fill(0,0,3,h,tint);
    }
    static void railDivider(GuiGraphics g,int x,int y,int w) {g.fill(x,y,x+w,y+1,SOFT);}
    static void sectionHeading(GuiGraphics g,int x,int y,int w,String title) {text(g,title,x,y,w,MUTED);}
    static void routeSketch(GuiGraphics g,int x,int y) {
        g.fill(x+3,y+19,x+77,y+21,BORDER);
        for(int i=15;i<76;i+=15)g.fill(x+i,y+16,x+i+1,y+19,SOFT);
        outline(g,x,y+16,8,8,MUTED);outline(g,x+72,y+16,8,8,MUTED);
        g.fill(x+92,y,x+102,y+10,ACCENT);g.fill(x+95,y+3,x+99,y+7,BACKGROUND);
    }
    private static void control(GuiGraphics g,int x,int y,int w,int h,boolean active,boolean primary,boolean pressed,boolean highlighted) {
        if(skin==Skin.ORE){OreUi.button(g,x,y,w,h,primary,active,highlighted,pressed);return;}
        if(skin==Skin.VANILLA){
            int[] bands=highlighted?new int[]{0x8F96C6,0x838AC0,0x7A82BA,0x6E76B0}:new int[]{0x8F8F8F,0x838383,0x787878,0x6C6C6C};
            if(!active)bands=new int[]{0x686868,0x626262,0x5C5C5C,0x565656};
            for(int i=0;i<4;i++)g.fill(x+1,y+1+(h-2)*i/4,x+w-1,y+1+(h-2)*(i+1)/4,0xFF000000|bands[i]);
            outline(g,x,y,w,h,0xFF000000);
            int light=pressed?0xFF5F5F5F:0xFFB0B0B0,dark=pressed?0xFFB0B0B0:0xFF4D4D4D;
            g.fill(x+1,y+1,x+w-1,y+2,light);g.fill(x+1,y+1,x+2,y+h-1,light);
            g.fill(x+1,y+h-2,x+w-1,y+h-1,dark);g.fill(x+w-2,y+1,x+w-1,y+h-1,dark);
        }else{
            int fill=!active?SUNKEN:primary?(pressed?0xFF3C4C2A:highlighted?0xFF6A7845:0xFF54643A):pressed?SUNKEN:RAISED;
            g.fill(x,y,x+w,y+h,fill);outline(g,x,y,w,h,primary?0xFF322B1C:SOFT);
            if(primary){
                outline(g,x+1,y+1,w-2,h-2,highlighted?0xFFFFE0A1:0xFFC9A260);
                g.fill(x+2,y+2,x+w-2,y+3,0xFFF0D397);g.fill(x+2,y+2,x+3,y+h-2,0xFFE6C47E);
                g.fill(x+2,y+h-3,x+w-2,y+h-2,0xFF8A6B39);g.fill(x+w-3,y+2,x+w-2,y+h-2,0xFF8A6B39);
            }else{
                g.fill(x+1,y+1,x+w-1,y+2,highlighted?ACCENT:BORDER);g.fill(x+1,y+h-2,x+w-1,y+h-1,0xFF242421);
            }
        }
    }
    private static void ticket(GuiGraphics g,int x,int y,int w,int h,boolean selected,boolean hovered,int tint) {
        g.fill(x,y,x+w,y+h,selected?RAISED:PANEL);outline(g,x,y,w,h,selected&&skin==Skin.VANILLA?0xFFFFFFFF:hovered?BORDER:SOFT);
        g.fill(x+3,y+4,x+5,y+h-4,tint);
        if(skin==Skin.ORE){
            if(selected)outline(g,x,y,w,h,0xFFD0D1D4);
            if(hovered)outline(g,x+1,y+1,w-2,h-2,0xFFFFFFFF);
            return;
        }
        if(selected){int c=skin==Skin.VANILLA?0xFFFFAA00:ACCENT;
            g.fill(x,y,x+6,y+2,c);g.fill(x,y,x+2,y+6,c);
            g.fill(x+w-6,y+h-2,x+w,y+h,c);g.fill(x+w-2,y+h-6,x+w,y+h,c);
        }
    }
    static void mark(GuiGraphics g, Mark mark, int x, int y, int color) {
        switch (mark) {
            case SEARCH -> { outline(g, x, y, 6, 6, color); g.fill(x + 5, y + 5, x + 7, y + 7, color); g.fill(x + 7, y + 7, x + 9, y + 8, color); }
            case STATIONS -> { outline(g, x, y, 9, 8, color); g.fill(x + 2, y + 2, x + 3, y + 6, color); g.fill(x + 5, y + 2, x + 7, y + 3, color); g.fill(x + 5, y + 5, x + 7, y + 6, color); }
            case CLOSE -> { x++;y++;for (int i = 0; i < 7; i++) { g.fill(x + i, y + i, x + i + 1, y + i + 1, color); g.fill(x + 6 - i, y + i, x + 7 - i, y + i + 1, color); } }
            case SWAP -> { g.fill(x, y + 2, x + 9, y + 3, color); g.fill(x, y + 6, x + 9, y + 7, color); g.fill(x + 6, y, x + 7, y + 5, color); g.fill(x + 1, y + 4, x + 2, y + 9, color); }
            case CHECK -> { g.fill(x, y + 4, x + 2, y + 6, color); g.fill(x + 2, y + 6, x + 4, y + 8, color); for (int i = 0; i < 5; i++) g.fill(x + 3 + i, y + 5 - i, x + 5 + i, y + 7 - i, color); }
            case WALK -> { g.fill(x + 4, y, x + 6, y + 2, color); g.fill(x + 3, y + 3, x + 5, y + 6, color); g.fill(x, y + 4, x + 3, y + 5, color); g.fill(x + 5, y + 4, x + 8, y + 5, color); g.fill(x + 2, y + 6, x + 4, y + 9, color); g.fill(x + 5, y + 6, x + 7, y + 9, color); }
            case GEAR -> { outline(g, x + 1, y + 1, 7, 7, color); g.fill(x + 3, y + 3, x + 6, y + 6, color); g.fill(x + 4, y - 1, x + 5, y + 10, color); }
            case BOX -> { outline(g, x + 1, y + 1, 7, 7, color); }
            case BACK -> { g.fill(x + 1, y + 4, x + 9, y + 5, color); for (int i = 0; i < 4; i++) g.fill(x + i, y + 4 - i, x + i + 1, y + 5 + i, color); }
            case TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT -> {
                outline(g, x, y, 9, 9, color);
                int sx = mark == Mark.TOP_RIGHT || mark == Mark.BOTTOM_RIGHT ? x + 5 : x + 1;
                int sy = mark == Mark.BOTTOM_LEFT || mark == Mark.BOTTOM_RIGHT ? y + 5 : y + 1;
                g.fill(sx, sy, sx + 3, sy + 3, color);
            }
            case DOWN -> { g.fill(x, y + 2, x + 7, y + 3, color); g.fill(x + 1, y + 3, x + 6, y + 4, color); g.fill(x + 2, y + 4, x + 5, y + 5, color); g.fill(x + 3, y + 5, x + 4, y + 6, color); }
            default -> { }
        }
    }
    static String duration(long millis) {
        long minutes = Math.max(1, (millis + 59_999) / 60_000);
        return tr("mtrnav.minutes", minutes);
    }
    static String remaining(long timestamp) { return CLOCK.stale(System.nanoTime()) ? "—" : countdown(timestamp); }
    static String guidance() {
        if (tracking == null) return "";
        if (WalkingClient.rulesChanged()) return tr("mtrnav.notice.walking_changed");
        if (tracking.walk() != null) {
            var walk = tracking.walk(); var player = Minecraft.getInstance().player;
            var target = catalog == null ? null : catalog.platform(walk.toPlatformId());
            var stationTarget = catalog == null ? null : catalog.station(walk.toStationId());
            if (!tracking.notice().isEmpty() && !tracking.notice().equals("manual_walk")) return tr("mtrnav.notice." + tracking.notice());
            if (player != null && stationTarget != null) {
                double tx = target == null ? stationTarget.x() : target.x(), tz = target == null ? stationTarget.z() : target.z();
                if (Math.hypot(player.getX() - tx, player.getZ() - tz) <= 16) return tr("mtrnav.walk_nearby");
            }
            return tr("mtrnav.walk_manual_key", openKey.get());
        }
        if (!tracking.notice().isEmpty() && !tracking.notice().equals("manual_check")) return tr("mtrnav.notice." + tracking.notice());
        if (usingNative && !CLOCK.stale(System.nanoTime()) && tracking.legIndex() < tracking.journey().legs().size()) {
            var leg = tracking.journey().legs().get(tracking.legIndex());
            long now = CLOCK.now(System.nanoTime());
            if (tracking.phase() == io.github.teamcreating.mtrnav.core.JourneyTracker.Phase.RIDING && leg.arrival() - now <= 30_000)
                return tr(now < leg.arrival() ? "mtrnav.estimated_alight_soon" : "mtrnav.estimated_alight_check");
            if (tracking.phase() != io.github.teamcreating.mtrnav.core.JourneyTracker.Phase.RIDING && now > leg.departure() + 10_000)
                return tr("mtrnav.notice.missed_train");
        }
        return tracking.notice().isEmpty() ? "" : tr("mtrnav.notice." + tracking.notice());
    }

    /** Uses Minecraft editing, selection and narration, with a measured inset surface. */
    static final class TextField extends EditBox {
        private final Font fieldFont;
        private Component placeholder;
        String caption = "";
        TextField(Font font, int x, int y, int w, int h, Component label) {
            super(new PaperFont(font), x, y, w, h, label);
            fieldFont = font;
            setTextColor(INPUT_TEXT); setTextColorUneditable(INPUT_MUTED);
        }
        @Override public void setHint(Component hint) { placeholder = hint; super.setHint(hint); }
        // Suppress the stock sprite; keep EditBox's native bordered text/cursor padding.
        @Override public boolean isBordered() { return false; }
        @Override public int getInnerWidth() { return Math.max(0, getWidth() - (caption.isEmpty()?8:26)); }
        @Override public void renderWidget(GuiGraphics g, int mx, int my, float delta) {
            if(!caption.isEmpty()&&fieldFont.width(getValue())>getInnerWidth())setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(getValue()).append("\n").append(Component.translatable("mtrnav.station_replace_hint"))));
            else if(!caption.isEmpty())setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("mtrnav.station_replace_hint")));
            if (placeholder != null) {
                String hint = placeholder.getString();
                int available = getInnerWidth();
                if (fieldFont.width(hint) > available) hint = fieldFont.plainSubstrByWidth(hint,
                        Math.max(0, available - fieldFont.width("…"))) + "…";
                super.setHint(Component.literal(hint).withStyle(placeholder.getStyle()));
            }
            if(skin==Skin.ORE)OreUi.field(g,getX(),getY()+(caption.isEmpty()?0:12),width,height-(caption.isEmpty()?0:12),isFocused());
            else g.fill(getX(),getY(),getX()+width,getY()+height,skin==Skin.VANILLA?0xFF151515:SUNKEN);
            if(skin!=Skin.ORE)outline(g,getX(),getY(),width,height,isFocused()?skin==Skin.VANILLA?0xFFFFFFFF:ACCENT:skin==Skin.VANILLA?0xFFA0A0A0:BORDER);
            if(!caption.isEmpty())text(g,caption,getX()+4,getY(),width-8,INPUT_MUTED);
            // Empty fields use a softer ink for the native placeholder.
            setTextColor(getValue().isEmpty() ? INPUT_MUTED : INPUT_TEXT);
            g.pose().pushPose(); if(!caption.isEmpty())g.pose().translate(0,6,0);
            super.renderWidget(g, mx, my, delta); g.pose().popPose();
        }
    }

    /** Native keyboard/drag slider, snapping to the existing 25% preference steps. */
    static final class PercentSlider extends net.minecraft.client.gui.components.AbstractSliderButton {
        private final java.util.function.IntConsumer changed;
        PercentSlider(int x,int y,int width,int percent,java.util.function.IntConsumer changed) {
            super(x,y,width,20,Component.literal(percent+"%"),(percent-75)/125.0);
            this.changed=changed;
        }
        void sync(int percent) { value=(percent-75)/125.0; updateMessage(); }
        private int percent() { return 75+(int)Math.round(value*5)*25; }
        @Override protected void updateMessage() { setMessage(Component.literal(percent()+"%")); }
        @Override protected void applyValue() { value=(percent()-75)/125.0; changed.accept(percent()); }
        @Override public boolean keyPressed(int key,int scan,int modifiers) {
            if(key==263||key==262){value=Math.clamp(value+(key==262?.2:-.2),0,1);applyValue();updateMessage();return true;}
            return super.keyPressed(key,scan,modifiers);
        }
        @Override public void renderWidget(GuiGraphics g,int mx,int my,float delta) {
            int x=getX(),y=getY()+8,w=getWidth();
            g.fill(x,y,x+w,y+5,OreUi.EDGE);g.fill(x+1,y+1,x+w-1,y+4,0xFF58585A);
            int thumb=x+(int)Math.round(value*(w-10));
            g.fill(x+1,y+1,thumb+5,y+4,OreUi.GREEN);
            OreUi.button(g,thumb,getY()+1,10,18,false,active,isHovered(),isFocused(),0);
        }
    }

    /** Measured native controls, ticket rows and segmented tabs share their visual and input bounds. */
    static final class Action extends Button {
        boolean selected,primary,tab,headerTab,headerControl,disclosure,segment,toggle,checkbox,locateIcon,onLight,labelOnly;
        java.util.List<io.github.teamcreating.mtrnav.core.JourneyChain.Item> chain = java.util.List.of();
        String detail,extra,durationLabel,arrivalLabel,bestLabel;
        java.util.List<String> badges=java.util.List.of();
        java.util.List<Integer> badgeColors=java.util.List.of();
        int tint=ACCENT;
        Mark mark=Mark.NONE;
        private final io.github.teamcreating.mtrnav.core.UiPress press=new io.github.teamcreating.mtrnav.core.UiPress();
        private int pressedKey=-1;
        private final io.github.teamcreating.mtrnav.core.UiToggle toggleMotion=new io.github.teamcreating.mtrnav.core.UiToggle();
        double pressDepth() { return press.depth(System.nanoTime()); }
        void cancelPress() { pressedKey=-1;press.cancel(System.nanoTime()); }
        @Override public void onClick(double x,double y) {
            if(skin!=Skin.ORE){super.onClick(x,y);return;}
            press.press(System.nanoTime());
            if(Minecraft.getInstance().screen instanceof NavigationPanel panel)panel.capture(this);
        }
        @Override public boolean mouseReleased(double x,double y,int button) {
            if(skin!=Skin.ORE)return super.mouseReleased(x,y,button);
            if(button!=0||!press.down()||pressedKey!=-1)return false;
            boolean activate=press.release(active&&visible&&isMouseOver(x,y),System.nanoTime());
            if(activate)onPress();return true;
        }
        @Override public boolean keyPressed(int key,int scan,int modifiers) {
            if(skin==Skin.ORE&&active&&visible&&(key==32||key==257||key==335)){
                if(press.press(System.nanoTime())){pressedKey=key;playDownSound(Minecraft.getInstance().getSoundManager());}
                return true;
            }
            return super.keyPressed(key,scan,modifiers);
        }
        @Override public boolean keyReleased(int key,int scan,int modifiers) {
            if(pressedKey==key){pressedKey=-1;if(press.release(active&&visible&&isFocused(),System.nanoTime()))onPress();return true;}
            return super.keyReleased(key,scan,modifiers);
        }
        @Override public void setFocused(boolean focused) { if(!focused&&pressedKey!=-1)cancelPress();super.setFocused(focused); }
        Action(int x,int y,int w,int h,String label,Runnable action){super(x,y,w,Math.max(18,h),Component.literal(label),ignored->action.run(),DEFAULT_NARRATION);}
        @Override protected void renderWidget(GuiGraphics g,int mx,int my,float delta){
            boolean hover=active&&isHoveredOrFocused(),pressed=active&&(skin==Skin.ORE?pressDepth()>.5:isHovered()&&Minecraft.getInstance().mouseHandler.isLeftPressed());
            if(press.down()&&!OreReview.backgroundPointer()&&!Minecraft.getInstance().isWindowActive())cancelPress();
            toggleMotion.update(selected,System.nanoTime());
            int x=getX(),y=getY(),ink=skin==Skin.VANILLA?(!active?0xFFA0A0A0:hover||primary?0xFFFFFFA0:0xFFFFFFFF):!active?FAINT:primary?0xFFFFF5D7:TEXT;
            String label=getMessage().getString();
            if(skin==Skin.ORE)ink=!active?0xFF58585A:primary?0xFFFFFFFF:OreUi.INK;
            if(skin==Skin.ORE&&checkbox){
                OreUi.button(g,x,y,width,height,false,active,isHovered(),isFocused(),pressDepth());
                int shiftY=(int)Math.round(pressDepth()*3),cy=y+8+shiftY;
                g.fill(x+7,cy,x+21,cy+14,OreUi.EDGE);g.fill(x+8,cy+1,x+20,cy+13,selected?OreUi.GREEN:OreUi.INSET);
                if(selected){g.fill(x+10,cy+6,x+13,cy+9,0xFFFFFFFF);g.fill(x+12,cy+8,x+15,cy+11,0xFFFFFFFF);g.fill(x+14,cy+4,x+17,cy+9,0xFFFFFFFF);}
                text(g,label,x+28,y+5+shiftY,width-36,active?OreUi.INK:0xFF58585A);
                if(extra!=null)text(g,extra,x+28,y+17+shiftY,width-36,0xFF48494A);
                return;
            }
            if(detail!=null&&height>=32){
                ticket(g,x,y,width,height,selected,hover,tint);
                text(g,label+(chain.isEmpty()||bestLabel==null?"":" · "+bestLabel),x+10,y+7,width-(durationLabel==null?20:66),MUTED);
                if(durationLabel!=null)large(g,durationLabel,x+width-57,y+(height>=50?6:7),49,TEXT,height>=50?1.5f:1f);
                if(arrivalLabel!=null&&height>=50)text(g,arrivalLabel,x+10,y+19,width-20,selected?MUTED:FAINT);
                if(height>=50||chain.isEmpty())text(g,detail,x+10,y+(height>=50?31:19),width-20,MUTED);
                if(!chain.isEmpty())journeyChain(g,chain,x+10,y+height-16,width-20);
                if(height>=50&&chain.isEmpty()){
                    int bx=x+10;
                    for(int i=0;i<badges.size();i++){
                        if(bx>x+width-25)break;
                        bx+=badge(g,badges.get(i),bx,y+height-16,x+width-8-bx-(bestLabel==null?0:uiFont().width(bestLabel)+12),badgeColors.get(i))+4;
                    }
                    if(bestLabel!=null){int bw=uiFont().width(bestLabel)+6;outline(g,x+width-bw-8,y+height-16,bw,12,ACCENT);text(g,bestLabel,x+width-bw-5,y+height-14,bw-6,ACCENT);}
                }
                return;
            }
            if(disclosure){
                if(hover)g.fill(x,y,x+width,y+height,RAISED);
                if(isFocused())outline(g,x,y,width,height,0xFFFFFFFF);
                if(selected)mark(g,Mark.DOWN,x+5,y+7,TEXT);
                else for(int i=0;i<4;i++)g.fill(x+6+i,y+5+i,x+7+i,y+12-i,TEXT);
                text(g,label,x+18,y+(height-8)/2,width-24,TEXT);return;
            }
            if(labelOnly){text(g,label,x+4,y+(height-8)/2,width-8,TEXT);return;}
            if(skin==Skin.ORE&&headerControl){
                g.fill(x,y,x+width,y+height,hover?0xFFD0D1D4:OreUi.HEADER);if(isFocused())outline(g,x,y,width,height,OreUi.INK);ink=OreUi.INK;
            }else if(skin==Skin.ORE&&(tab||segment||toggle)){
                if(toggle){OreUi.toggle(g,x,y,width,height,isHovered(),isFocused(),active,toggleMotion.frame(System.nanoTime()),pressDepth());text(g,label,x+6,y+(height-8)/2,width-46,active?TEXT:FAINT);return;}
                if(segment)OreUi.segment(g,x,y,width,height,selected,hover);
                else OreUi.tab(g,x,y,width,height,selected,isHovered(),isFocused(),headerTab);
                ink=headerTab||segment&&selected?OreUi.INK:active?TEXT:FAINT;
            }else if(tab&&selected){
                g.fill(x,y,x+width,y+height,RAISED);outline(g,x,y,width,height,SOFT);g.fill(x+1,y+height-2,x+width-1,y+height,skin==Skin.VANILLA?0xFFFFAA00:ACCENT);ink=TEXT;
            }else if(skin==Skin.ORE)OreUi.button(g,x,y,width,height,primary,active,isHovered(),isFocused(),pressDepth());
            else control(g,x,y,width,height,active,primary,pressed,hover);
            int shift=skin==Skin.ORE?(int)Math.round(3*pressDepth()):pressed?1:0;
            Mark symbol=mark;
            if(label.equals("×")||label.equals("✕"))symbol=Mark.CLOSE;
            if(label.equals("⇄"))symbol=Mark.SWAP;
            if(primary&&symbol==Mark.SEARCH){
                int sx=x+(width-15)/2+(skin==Skin.ORE?0:shift),sy=y+1+Math.max(0,(height-5-16)/2)+shift;
                g.fill(sx+3,sy,sx+9,sy+2,ink);g.fill(sx,sy+3,sx+2,sy+9,ink);g.fill(sx+10,sy+3,sx+12,sy+9,ink);g.fill(sx+3,sy+10,sx+9,sy+12,ink);
                g.fill(sx+1,sy+1,sx+4,sy+4,ink);g.fill(sx+8,sy+1,sx+11,sy+4,ink);g.fill(sx+1,sy+8,sx+4,sy+11,ink);g.fill(sx+8,sy+8,sx+11,sy+11,ink);
                for(int i=0;i<5;i++)g.fill(sx+10+i,sy+10+i,sx+12+i,sy+12+i,ink);
            }else if(width<=32&&(symbol!=Mark.NONE||locateIcon)){
                if(locateIcon){outline(g,x+width/2-3,y+height/2-3,7,7,ink);g.fill(x+width/2,y+height/2-5,x+width/2+1,y+height/2+6,ink);}
                else mark(g,symbol,x+(width-9)/2+(skin==Skin.ORE?0:shift),y+(height-9)/2+shift,ink);
            }else{
                String shown=uiFont().width(label)<=width-8?label:uiFont().plainSubstrByWidth(label,Math.max(0,width-8-uiFont().width("…")))+"…";
                int tx=x+Math.max(3,(width-uiFont().width(shown))/2)+(skin==Skin.ORE?0:shift),ty=y+(height-8)/2+shift-(skin==Skin.ORE&&!tab&&!segment&&!headerControl?1:0);
                g.drawString(uiFont(),shown,tx,ty,ink,skin==Skin.VANILLA&&!(tab&&selected));
            }
        }
    }
}
