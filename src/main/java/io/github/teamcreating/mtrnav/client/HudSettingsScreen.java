package io.github.teamcreating.mtrnav.client;

import io.github.teamcreating.mtrnav.core.HudPlacement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import java.util.*;
import static io.github.teamcreating.mtrnav.client.NavigationClient.tr;
import static io.github.teamcreating.mtrnav.client.NavigationTheme.*;

/** Ore settings sheet with separate display/position groups and actual draggable HUD previews. */
final class HudSettingsScreen extends NavigationPanel {
    private final Screen previous;
    private record Control(AbstractWidget widget,int offset) {}
    private final List<Control> controls=new ArrayList<>();
    private final List<Action> corners=new ArrayList<>();
    private Action fitButton,navigationButton,reminderButton,displayButton,previewButton;
    private PercentSlider sizeSlider;
    private boolean dragging,editingReminder,compact,previewOnly;
    private int panelY,panelX,panelWidth,panelHeight,scaleY,positionY;
    private double dragX,dragY;
    HudSettingsScreen(Screen previous) {super(Component.translatable("mtrnav.hud_settings"));this.previous=previous;}
    private <T extends AbstractWidget> T control(T widget,int offset) {
        controls.add(new Control(addRenderableWidget(widget),offset));return widget;
    }
    private Action button(String key,int x,int offset,int width,int height,Runnable action) {
        return control(new Action(x,offset,width,height,tr(key),action),offset);
    }
    @Override protected void init() {
        super.init();controls.clear();corners.clear();dragging=false;
        panelWidth=Math.min(300,width-16);panelHeight=Math.min(336,height-16);compact=panelHeight<280;
        panelX=width>=640?16:(width-panelWidth)/2;
        int x=panelX+12,inner=panelWidth-24,half=(inner-4)/2;
        navigationButton=button("mtrnav.hud_edit_navigation",x,36,half,22,()->editingReminder=false);navigationButton.tab=true;
        reminderButton=button("mtrnav.hud_edit_reminder",x+half+4,36,inner-half-4,22,()->editingReminder=true);reminderButton.tab=true;
        scaleY=compact?62:84;
        sizeSlider=control(new PercentSlider(x+8,scaleY+14,inner-16,NavigationPreferences.hudPercent(),value->{
            if(editingReminder)NavigationPreferences.setReminderPercent(value);else NavigationPreferences.setHudPercent(value);
        }),scaleY+14);
        fitButton=button("mtrnav.ore.auto_fit",x,compact?104:128,inner,24,NavigationPreferences::toggleAutoFit);fitButton.toggle=true;
        fitButton.setTooltip(Tooltip.create(Component.translatable("mtrnav.hud_both_fit_hint")));
        positionY=compact?136:182;
        String[] names={"top_left","top_right","bottom_left","bottom_right"};
        for(int i=0;i<4;i++) {
            final int corner=i;int start=i*(inner+4)/4,end=(i+1)*(inner+4)/4-4;
            var b=button("mtrnav.corner_"+names[i],x+start,positionY,end-start,20,()->position(corner%2,corner/2,true));
            b.segment=true;corners.add(b);
        }
        displayButton=button("mtrnav.ore.keep_expanded",x,compact?160:218,inner,24,NavigationPreferences::toggleReminderExpanded);displayButton.toggle=true;
        displayButton.setTooltip(Tooltip.create(Component.translatable("mtrnav.reminder_display_hint")));
        var reset=button("mtrnav.hud_reset_selected",x,panelHeight-28,inner-84,20,()->{
            if(editingReminder)NavigationPreferences.resetReminder();else NavigationPreferences.resetHud();
        });
        var done=button("mtrnav.done",x+inner-76,panelHeight-28,76,20,this::onClose);done.primary=true;
        previewButton=button("mtrnav.ore.preview",panelX+panelWidth-72,4,60,20,()->{previewOnly=!previewOnly;layoutControls();});
        layoutControls();updateLabels();setInitialFocus(previewOnly?previewButton:navigationButton);
    }
    private HudPlacement selectedPlacement() {var pair=HudPositions.layout(true);return editingReminder?pair.reminder():pair.navigation();}
    private void changeSize(int direction) {if(editingReminder)NavigationPreferences.changeReminderSize(direction);else NavigationPreferences.changeHudSize(direction);}
    private void position(double x,double y,boolean persist) {if(editingReminder)NavigationPreferences.setReminderPosition(x,y,persist);else NavigationPreferences.setHudPosition(x,y,persist);}
    private void layoutControls() {
        var pair=HudPositions.layout(true);double best=Double.POSITIVE_INFINITY;
        for(int proposed:new int[]{height-panelHeight-4,8,(height-panelHeight)/2}) {
            int y=Math.clamp(proposed,4,Math.max(4,height-panelHeight-4));
            double overlap=overlapArea(panelX,y,panelWidth,panelHeight,pair.navigation())+overlapArea(panelX,y,panelWidth,panelHeight,pair.reminder());
            if(overlap<best){best=overlap;panelY=y;}
        }
        for(var c:controls){c.widget().setY(panelY+c.offset());c.widget().visible=!dragging&&!previewOnly;}
        displayButton.visible=!dragging&&!previewOnly&&editingReminder;
        previewButton.visible=!dragging;previewButton.headerControl=!previewOnly;
        previewButton.setX(previewOnly?(width-104)/2:panelX+panelWidth-72);previewButton.setY(previewOnly?height-28:panelY+4);previewButton.setWidth(previewOnly?104:60);
        previewButton.setMessage(Component.translatable(previewOnly?"mtrnav.ore.back_settings":"mtrnav.ore.preview"));
        if(dragging)setFocused(null);
    }
    private static double overlapArea(int x,int y,int w,int h,HudPlacement p) {
        return Math.max(0,Math.min(x+w,p.x()+p.width())-Math.max(x,p.x()))*Math.max(0,Math.min(y+h,p.y()+p.height())-Math.max(y,p.y()));
    }
    private void updateLabels() {
        sizeSlider.sync(editingReminder?NavigationPreferences.reminderPercent():NavigationPreferences.hudPercent());
        fitButton.selected=NavigationPreferences.autoFit();navigationButton.selected=!editingReminder;reminderButton.selected=editingReminder;
        displayButton.selected=NavigationPreferences.reminderExpanded();
        double px=editingReminder?NavigationPreferences.reminderX():NavigationPreferences.hudX();
        double py=editingReminder?NavigationPreferences.reminderY():NavigationPreferences.hudY();
        for(int i=0;i<4;i++)corners.get(i).selected=Math.abs(px-i%2)<.001&&Math.abs(py-i/2)<.001;
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){if(previewOnly){previewOnly=false;layoutControls();return;}NavigationPreferences.save();minecraft.setScreen(previous);}
    @Override public void render(GuiGraphics g,int mx,int my,float delta) {
        mx=OreReview.mouseX(mx);my=OreReview.mouseY(my);
        g.fill(0,0,width,height,0x70101010);layoutControls();updateLabels();
        NavigationHud.preview(g);ReminderHud.preview(g);
        var selected=selectedPlacement();outline(g,(int)Math.floor(selected.x())-1,(int)Math.floor(selected.y())-1,(int)Math.ceil(selected.width())+2,(int)Math.ceil(selected.height())+2,0xFFFFFFFF);
        if(!dragging&&!previewOnly) {
            int x=panelX,y=panelY,w=panelWidth,inner=w-24;
            g.fill(x,y,x+w,y+panelHeight,OreUi.INSET);outline(g,x,y,w,panelHeight,OreUi.EDGE);
            g.fill(x+1,y+1,x+w-1,y+28,OreUi.HEADER);g.fill(x+1,y+27,x+w-1,y+29,0xFFB1B2B5);
            text(g,title.getString(),x+12,y+10,w-24,OreUi.INK);
            if(!compact)text(g,tr("mtrnav.ore.display"),x+12,y+68,inner,TEXT);
            g.fill(x+12,y+scaleY-2,x+w-12,y+(compact?128:152),OreUi.BODY);
            text(g,tr("mtrnav.ui.scale"),x+20,y+scaleY+2,inner-68,TEXT);
            text(g,(editingReminder?NavigationPreferences.reminderPercent():NavigationPreferences.hudPercent())+"%",x+w-64,y+scaleY+2,44,TEXT);
            if(!compact)text(g,tr("mtrnav.ore.position"),x+12,y+166,inner,TEXT);
            if(!compact) {
                String hint=tr(editingReminder?"mtrnav.ore.reminder_hint":"mtrnav.ore.navigation_hint");
                int hy=y+264;
                for(var line:font.split(Component.literal(hint),inner)){g.drawString(font,line,x+12,hy,MUTED,false);hy+=12;}
            }
            for(var c:controls)if(c.widget().visible)c.widget().render(g,mx,my,delta);
        }else {
            if(dragging)text(g,tr("mtrnav.ui.hud_drag_short"),8,height-18,width-16,TEXT);
            if(previewOnly&&!dragging)previewButton.render(g,mx,my,delta);
        }
        OreReview.audit(this);
    }
    @Override public boolean mouseClicked(double x,double y,int button) {
        layoutControls();
        for(var c:controls)if(c.widget().visible&&c.widget().isMouseOver(x,y))return super.mouseClicked(x,y,button);
        if(!dragging&&!previewOnly&&x>=panelX&&x<panelX+panelWidth&&y>=panelY&&y<panelY+panelHeight)return super.mouseClicked(x,y,button);
        var pair=HudPositions.layout(true);
        if(button==0&&(pair.reminder().contains(x,y)||pair.navigation().contains(x,y))) {
            editingReminder=pair.reminder().contains(x,y);var p=selectedPlacement();dragging=true;dragX=x-p.x();dragY=y-p.y();layoutControls();return true;
        }
        return super.mouseClicked(x,y,button);
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy) {
        if(dragging&&button==0){var p=selectedPlacement();position(p.relativeX(x-dragX),p.relativeY(y-dragY),false);return true;}
        return super.mouseDragged(x,y,button,dx,dy);
    }
    @Override public boolean mouseReleased(double x,double y,int button) {
        if(dragging&&button==0){dragging=false;NavigationPreferences.save();layoutControls();return true;}
        return super.mouseReleased(x,y,button);
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical) {
        if(!dragging&&!previewOnly&&x>=panelX&&x<panelX+panelWidth&&y>=panelY&&y<panelY+panelHeight)return super.mouseScrolled(x,y,horizontal,vertical);
        var pair=HudPositions.layout(true);
        if(vertical!=0&&(pair.reminder().contains(x,y)||pair.navigation().contains(x,y))){editingReminder=pair.reminder().contains(x,y);changeSize(vertical>0?1:-1);return true;}
        return super.mouseScrolled(x,y,horizontal,vertical);
    }
}
