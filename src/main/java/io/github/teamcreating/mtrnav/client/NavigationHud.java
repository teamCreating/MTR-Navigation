package io.github.teamcreating.mtrnav.client;

import io.github.teamcreating.mtrnav.core.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import static io.github.teamcreating.mtrnav.client.NavigationClient.*;
import static io.github.teamcreating.mtrnav.client.NavigationTheme.*;

/** Signage HUD: step, timetable progress, next action, direction and confirmation. */
final class NavigationHud {
    private static final int WHITE=0xFFFFFFFF,GREY=0xFFD0D1D4,GOLD=0xFF6CC349;
    private NavigationHud(){}
    static HudPlacement placement(){return HudPositions.layout(false).navigation();}
    static void render(GuiGraphics g){render(g,false);}
    static void preview(GuiGraphics g){render(g,true);}
    private static void render(GuiGraphics g,boolean preview){
        var mc=Minecraft.getInstance();boolean example=tracking==null||catalog==null;
        if(!preview&&(example||mc.options.hideGui||mc.screen!=null))return;
        var p=HudPositions.layout(preview).navigation();int w=HudScale.WIDTH,h=HudScale.HEIGHT;
        boolean complete=!example&&(tracking.phase()==JourneyTracker.Phase.COMPLETE||tracking.journey().legs().isEmpty());
        boolean riding=!example&&tracking.phase()==JourneyTracker.Phase.RIDING;
        var leg=example||complete?null:tracking.journey().legs().get(Math.min(tracking.legIndex(),tracking.journey().legs().size()-1));
        int tint=leg==null?0xFFE8A13C:routeColor(leg.routeId());
        g.pose().pushPose();
        try{
            g.pose().translate(p.x(),p.y(),0);g.pose().scale((float)p.scale(),(float)p.scale(),1);hudFrame(g,w,h,tint);
            text(g,example?tr("mtrnav.hud_preview"):tr("mtrnav.phase."+tracking.phase().name().toLowerCase(java.util.Locale.ROOT)),8,5,w-76,GREY);
            text(g,example?"4":leg==null?"":routeCode(leg.routeId()),w-64,5,56,tint);
            int railY=21;g.fill(10,railY,w-10,railY+1,0xFF58585A);g.fill(10,railY+4,w-10,railY+5,0xFF58585A);
            g.fill(10,railY-2,11,railY+7,GREY);g.fill(w-11,railY-2,w-10,railY+7,GREY);
            boolean known=example||leg!=null&&riding&&!CLOCK.stale(System.nanoTime())&&leg.arrival()>leg.departure();
            if(known){double progress=example?.64:Math.clamp((double)(CLOCK.now(System.nanoTime())-leg.departure())/(leg.arrival()-leg.departure()),0,1);int tx=12+(int)((w-28)*progress);g.fill(tx,railY-1,tx+8,railY+7,tint);}
            else text(g,"—",w/2-4,railY-3,10,GREY);
            text(g,example?tr("mtrnav.origin"):leg==null?"":station(leg.fromStationId()),10,29,w/2-15,GREY);
            String end=example?tr("mtrnav.destination"):leg==null?"":station(leg.toStationId());int ew=Math.min(w/2-15,uiFont().width(end));text(g,end,w-10-ew,29,ew,GREY);
            String action=example?tr("mtrnav.hud_preview_line"):complete?tr("mtrnav.complete"):tracking.walk()!=null?tr("mtrnav.walk_to",station(tracking.walk().toStationId())):tr(riding?"mtrnav.hud_alight":"mtrnav.hud_board")+" "+station(riding?leg.toStationId():leg.fromStationId());
            large(g,action,8,41,w-16,WHITE,1.25f);
            String sub=example?tr("mtrnav.hud_reminder_direction"):leg==null?"":tr("mtrnav.direction",primary(leg.direction()))+" · "+platform(riding?leg.toPlatformId():leg.fromPlatformId());
            text(g,sub,8,55,w-16,GREY);
            String hint=example?tr("mtrnav.hud_preview_hint"):guidance().isEmpty()?tr("mtrnav.hud_short_key",openKey.get()):guidance();
            if(!example&&CLOCK.stale(System.nanoTime()))hint=tr("mtrnav.stale");
            text(g,example?tr("mtrnav.vehicle_preview"):leg==null?"":VehicleNames.compact(leg.consist(),w-16),8,66,w-16,GREY);
            text(g,hint,8,78,w-16,GOLD);
        }finally{g.pose().popPose();}
    }
}
