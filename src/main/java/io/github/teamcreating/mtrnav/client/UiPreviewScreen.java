package io.github.teamcreating.mtrnav.client;

import io.github.teamcreating.mtrnav.core.UiLayout;
import io.github.teamcreating.mtrnav.core.UiLayout.Rect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayList;
import java.util.List;
import static io.github.teamcreating.mtrnav.client.UiPreviewSkin.*;

/** Isolated review surface. No query, tracking, preference or multiplayer state is changed. */
final class UiPreviewScreen extends Screen {
    private enum Scene { JOURNEYS, LONG_NAMES, EMPTY, REFERENCE, HUD, COMPONENTS }
    private record Sample(String title, String route) {}
    private static final List<Sample> SAMPLES = List.of(
            new Sample("直达 · 28 分钟", "1 号线 Local · 无需换乘"),
            new Sample("换乘 1 次 · 31 分钟", "Local → Airport Express"),
            new Sample("换乘 1 次 · 36 分钟", "Local → Riverside"),
            new Sample("换乘 2 次 · 42 分钟", "Local → Central → North"),
            new Sample("步行换乘 · 45 分钟", "Local → 步行 4 分钟 → North"));
    private UiTypography.Face face = "smooth".equals(System.getProperty("mtrnav.uiPreviewFont")) ? UiTypography.Face.SMOOTH : UiTypography.Face.PIXEL;
    private Scene scene;
    private UiLayout.Workspace layout;
    private UiLayout.HudWorkspace hud;
    private final List<ReviewButton> controls = new ArrayList<>();
    private final List<ReviewButton> hudControls = new ArrayList<>();
    private double restoreScale = Double.NaN;
    private Boolean queryExpanded;
    private boolean showDetails, dragging, dragReminder;
    private int selected, scroll, settingsScroll, detailScroll, detailHeight, percent = 150;
    private double navX=.05, navY=.10, reminderX=.60, reminderY=.10, grabX, grabY;
    private Rect navBox, reminderBox;
    private String origin="南苑 Nanyuan", destination="滨海北 Binhai North", notice="";

    UiPreviewScreen() {
        super(Component.literal("站间物语 · UI 设计预览"));
        try { scene=Scene.valueOf(System.getProperty("mtrnav.uiPreviewScene","journeys").toUpperCase(java.util.Locale.ROOT)); }
        catch(IllegalArgumentException e) { scene=Scene.JOURNEYS; }
        setSceneText();
    }
    private void setSceneText() {
        if (scene==Scene.LONG_NAMES) {
            origin="滨海国际机场交通中心 Binhai International Airport Transport Centre";
            destination="北部新区综合交通枢纽 North District Integrated Transport Hub";
        } else { origin="南苑 Nanyuan"; destination="滨海北 Binhai North"; }
    }
    @Override protected void init() {
        var window=minecraft.getWindow();
        if (Double.isNaN(restoreScale)) restoreScale=window.getGuiScale();
        int normal=window.calculateScale(minecraft.options.guiScale().get(),minecraft.isEnforceUnicode());
        int cap=window.getHeight()>=1260 && window.getWidth()>=1920 ? 3 : 2;
        window.setGuiScale(Math.min(normal,cap));
        width=window.getGuiScaledWidth(); height=window.getGuiScaledHeight(); font=UiTypography.font(face);
        if (queryExpanded==null) queryExpanded=!UiLayout.workspace(width,height,false).compact();
        build();
    }
    private ReviewButton button(Rect r,String label,boolean primary,Runnable action) {
        var b=new ReviewButton(r,label,primary,action);controls.add(addRenderableWidget(b));return b;
    }
    private void build() {
        clearWidgets();controls.clear();hudControls.clear();
        layout=UiLayout.workspace(width,height,queryExpanded);
        hud=UiLayout.hud(width,height);
        var p=scene==Scene.HUD ? hud.panel() : layout.panel();
        button(new Rect(p.right()-26,p.y()+10,18,18),"×",false,this::onClose);
        button(new Rect(p.right()-146,p.y()+10,112,18),face.label,false,this::toggleFace);
        if (scene==Scene.HUD) { buildHud();return; }
        var q=layout.query();
        if (queryExpanded) {
            if (layout.compact()) {
                field(new Rect(q.x(),q.y()+10,q.width()-24,20),true);
                field(new Rect(q.x(),q.y()+42,q.width()-24,20),false);
                button(new Rect(q.right()-18,q.y()+10,18,18),"×",false,()->{origin="";build();});
                button(new Rect(q.right()-18,q.y()+42,18,18),"×",false,()->{destination="";build();});
                button(new Rect(layout.primary().x(),layout.primary().y(),layout.primary().width(),20),"收起条件",true,()->{queryExpanded=false;build();});
            } else {
                int fieldWidth=(q.width()-28)/2;
                field(new Rect(q.x(),q.y()+12,fieldWidth,20),true);
                field(new Rect(q.right()-fieldWidth,q.y()+12,fieldWidth,20),false);
                button(new Rect(q.x()+fieldWidth+4,q.y()+13,18,18),"⇄",false,()->{var temp=origin;origin=destination;destination=temp;build();});
                button(new Rect(q.x(),q.y()+40,80,20),"全部车站",false,()->{notice="测试预览：选站交互将在确认设计后接入";});
                button(new Rect(q.x()+86,q.y()+40,80,20),"步行换乘",false,()->{notice="测试预览：保留现有步行查询能力";});
                button(new Rect(q.right()-162,q.y()+40,78,20),"最快到达",false,()->notice="测试预览：当前按到达时间排序");
                button(new Rect(q.right()-78,q.y()+40,78,20),"查找路线",true,()->notice="测试数据：展示 5 个固定方案，未发送查询");
            }
        } else {
            button(new Rect(q.right()-62,q.y(),62,20),"修改站点",false,()->{queryExpanded=true;build();});
        }
        if (layout.compact()) {
            var t=layout.tabs();
            var a=button(new Rect(t.x(),t.y(),76,18),"行程方案",false,()->{showDetails=false;build();});a.selected=!showDetails;
            var b=button(new Rect(t.x()+80,t.y(),76,18),"行程详情",false,()->{showDetails=true;build();});b.selected=showDetails;
        }
        if (!(layout.compact() && queryExpanded)) {
            var b=button(layout.primary(),"开始导航",true,()->notice="这是测试设计稿，不会启动真实导航");
            b.active=scene!=Scene.EMPTY && scene!=Scene.REFERENCE && scene!=Scene.COMPONENTS;
        }
        clampScroll();
    }
    private void field(Rect r,boolean from) {
        var box=new ReviewField(r,from ? "起点" : "终点");
        box.setMaxLength(256);box.setValue(from?origin:destination);
        box.moveCursorToStart(false);
        box.setResponder(value->{if(from)origin=value;else destination=value;});
        addRenderableWidget(box);
    }
    private void toggleFace() { face=face.next();font=UiTypography.font(face);build(); }
    private void nextScene() {
        scene=Scene.values()[(scene.ordinal()+1)%Scene.values().length];selected=scroll=settingsScroll=detailScroll=0;showDetails=false;notice="";
        setSceneText();queryExpanded=!UiLayout.workspace(width,height,false).compact();build();
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { minecraft.setScreen(null); }
    @Override public void removed() {
        super.removed();
        if (!Double.isNaN(restoreScale)) minecraft.getWindow().setGuiScale(restoreScale);
    }
    @Override public void resize(Minecraft mc,int w,int h) { super.resize(mc,w,h); }
    private int count() { return scene==Scene.EMPTY ? 0 : scene==Scene.REFERENCE ? 1 : SAMPLES.size(); }
    private void clampScroll() { scroll=Math.clamp(scroll,0,Math.max(0,count()-layout.visibleRows())); }
    private void label(GuiGraphics g,String text,int x,int y,int w,String color) { UiTypography.text(g,face,text,x,y,w,c(color)); }
    @Override public void render(GuiGraphics g,int mx,int my,float delta) {
        renderBackground(g,mx,my,delta);
        g.fill(0,0,width,height,0xA0121B16);
        if(scene==Scene.HUD){renderHud(g,mx,my,delta);return;}
        var p=layout.panel();panel(g,p);
        g.renderItem(net.minecraft.world.item.Items.COMPASS.getDefaultInstance(),p.x()+10,p.y()+11);
        label(g,"站间物语 · 测试预览",p.x()+30,p.y()+14,Math.max(0,p.width()-184),"paper");
        var q=layout.query();
        if(queryExpanded){
            label(g,"起点",q.x(),q.y(),q.width()/2,"muted");
            label(g,"终点",layout.compact()?q.x():q.right()-(q.width()-28)/2,q.y()+(layout.compact()?32:0),q.width()/2,"muted");
        }else label(g,origin+" → "+destination,q.x(),q.y()+5,Math.max(0,q.width()-70),"text");
        if(!layout.compact()) {
            label(g,"行程方案 · "+count(),layout.list().x(),layout.tabs().y()+4,layout.list().width(),"brass");
            label(g,"行程详情",layout.detail().x(),layout.tabs().y()+4,layout.detail().width(),"brass");
            if(queryExpanded)label(g,"已载入目录",q.x()+174,q.y()+46,Math.max(0,q.width()-340),"muted");
        }
        if(scene==Scene.COMPONENTS)renderComponents(g);
        else if(layout.compact() && showDetails)renderDetails(g,layout.detail());
        else {renderList(g);if(!layout.compact())renderDetails(g,layout.detail());}
        g.fill(p.x()+8,layout.footer().y()-5,p.right()-8,layout.footer().y()-4,c("brass"));
        String footer=notice.isEmpty()?"F6 字体 · F7 场景  "+(scene.ordinal()+1)+"/6":notice;
        label(g,footer,layout.footer().x(),layout.footer().y()+5,Math.max(0,layout.footer().width()-104),"muted");
        renderControls(g,mx,my,delta);
    }
    private void renderList(GuiGraphics g) {
        var r=layout.list();fill(g,r,c("inset"));
        if(count()==0){UiTypography.paragraph(g,face,"没有找到可用方案\n请调整站点或查询条件",r.x()+8,r.y()+12,r.width()-16,5,c("muted"));return;}
        g.enableScissor(r.x(),r.y(),r.right(),r.bottom());
        for(int i=scroll;i<Math.min(count(),scroll+layout.visibleRows());i++){
            var row=layout.row(i-scroll);boolean chosen=i==selected;
            fill(g,row,c(chosen?"selected":"panel"));if(chosen)edge(g,row,c("brass"));
            var sample=SAMPLES.get(i);
            label(g,scene==Scene.REFERENCE?"线路参考 · 换乘 0 次":sample.title,row.x()+6,row.y()+4,row.width()-12,"text");
            label(g,scene==Scene.REFERENCE?"班次未确认":sample.route,row.x()+6,row.y()+18,row.width()-12,"muted");
        }
        g.disableScissor();
        if(count()>layout.visibleRows())g.fill(r.right()-2,r.y(),r.right(),r.y()+Math.max(6,r.height()*layout.visibleRows()/count()),c("brass"));
    }
    private void renderDetails(GuiGraphics g,Rect r) {
        fill(g,r,c("inset"));g.enableScissor(r.x(),r.y(),r.right(),r.bottom());
        int x=r.x()+12,y=r.y()+10-detailScroll,w=r.width()-24;
        if(scene==Scene.EMPTY){UiTypography.paragraph(g,face,"没有可用路线\n请更换站点或调整查询条件。",x,y,w,5,c("text"));}
        else {
            label(g,scene==Scene.REFERENCE?"线路参考 · 班次未确认":SAMPLES.get(selected).title,x,y,w,"text");y+=20;
            if(scene==Scene.REFERENCE)y=UiTypography.paragraph(g,face,"线路可以换乘，但当前没有可乘班次。仅显示路径，暂不能开始导航。",x,y,w,4,c("warning"))+10;
            else {label(g,"下一班 08:12 · 1 站台",x,y,w,"muted");y+=26;}
            int lineX=x+3;
            g.fill(lineX,y+2,lineX+2,Math.min(r.bottom()-8,y+94),c("positive"));
            y=UiTypography.paragraph(g,face,origin,x+14,y,w-14,2,c("text"))+5;
            label(g,scene==Scene.REFERENCE?"上车 · 1 站台":"08:12 上车 · 1 站台",x+14,y,w-14,"muted");y+=24;
            label(g,"途经 6 站 · 点击展开",x+14,y,w-14,"muted");y+=24;
            y=UiTypography.paragraph(g,face,destination,x+14,y,w-14,2,c("text"))+5;
            label(g,scene==Scene.REFERENCE?"下车 · 2 站台":"08:40 到达 · 2 站台",x+14,y,w-14,"muted");
            int noteY=Math.max(r.bottom()-16-detailScroll,y+24);
            label(g,"测试数据 · 非实时班次",x,noteY,w,"muted");
            detailHeight=noteY+detailScroll-r.y()+16;
        }
        g.disableScissor();
        if(detailHeight>r.height())g.fill(r.right()-2,r.y(),r.right(),r.y()+Math.max(6,r.height()*r.height()/detailHeight),c("brass"));
    }
    private void renderComponents(GuiGraphics g) {
        var r=layout.compact()?layout.list():new Rect(layout.list().x(),layout.list().y(),layout.footer().width(),layout.list().height());
        fill(g,r,c("inset"));g.enableScissor(r.x(),r.y(),r.right(),r.bottom());
        int x=r.x()+8,y=r.y()+8;
        for(int i=0;i<4;i++){
            var b=new Rect(x,y+i*28,Math.min(130,r.width()-16),20);UiPreviewSkin.button(g,b,i==0,i==1,i!=3,i==2);
            label(g,new String[]{"主要操作","选中状态","鼠标悬停","暂不可用"}[i],b.x()+8,b.y()+5,b.width()-16,i<2?"ink":"text");
        }
        g.disableScissor();
    }
    private void buildHud() {
        String[] rows={"导航框 / 班次提醒框","HUD 大小："+percent+"%","自动适配：开","左上 / 右上 / 左下 / 右下","提醒条：自动收起","恢复所选框默认值"};
        var r=hud.settings();int visible=Math.max(1,(r.height()-8)/28);settingsScroll=Math.clamp(settingsScroll,0,Math.max(0,rows.length-visible));
        for(int i=settingsScroll;i<Math.min(rows.length,settingsScroll+visible);i++){
            int index=i;var b=button(new Rect(r.x()+4,r.y()+4+(i-settingsScroll)*28,r.width()-8,20),rows[i],false,()->{if(index==1){percent=percent==200?100:percent+50;build();}else notice="HUD 设计预览：设置不会写入你的正式偏好";});hudControls.add(b);
        }
        button(new Rect(hud.footer().right()-96,hud.footer().y(),96,20),"完成预览",true,this::onClose);
    }
    private void renderHud(GuiGraphics g,int mx,int my,float delta) {
        panel(g,hud.panel());var p=hud.panel();label(g,"HUD 设置 · 测试预览",p.x()+12,p.y()+14,p.width()-172,"paper");
        fill(g,hud.settings(),c("inset"));fill(g,hud.preview(),c("inset"));
        var a=hud.preview().inset(6);
        float hudScale=Math.min(percent/100f,Math.min(Math.max(0,(a.width()-8)/2)/104f,a.height()/44f));
        int boxW=Math.max(0,(int)Math.ceil(104*hudScale)),boxH=Math.max(0,(int)Math.ceil(44*hudScale));
        navBox=new Rect(a.x()+(int)Math.round(navX*Math.max(0,a.width()-boxW)),a.y()+(int)Math.round(navY*Math.max(0,a.height()-boxH)),boxW,boxH);
        reminderBox=new Rect(a.x()+(int)Math.round(reminderX*Math.max(0,a.width()-boxW)),a.y()+(int)Math.round(reminderY*Math.max(0,a.height()-boxH)),boxW,boxH);
        if(!dragging && overlaps(navBox,reminderBox)) {
            navBox=new Rect(a.x(),a.y(),boxW,boxH);
            reminderBox=new Rect(a.right()-boxW,a.y(),boxW,boxH);
        }
        g.enableScissor(a.x(),a.y(),a.right(),a.bottom());
        for(int i=0;i<2;i++){
            var r=i==0?navBox:reminderBox;fill(g,r,c("panel"));edge(g,r,c(i==0?"brass":"metal"));
            if(hudScale>0){
                g.pose().pushPose();g.pose().translate(r.x(),r.y(),0);g.pose().scale(hudScale,hudScale,1);
                label(g,i==0?"导航预览 · 上车站":"班次提醒预览",6,5,92,"paper");
                label(g,"Local · 1 站台",6,18,92,"text");
                label(g,"测试预览 · 03:20",6,31,92,"muted");
                g.pose().popPose();
            }
        }
        g.disableScissor();
        label(g,"F6 字体 · F7 场景 · 滚轮查看设置",hud.footer().x(),hud.footer().y()+5,hud.footer().width()-104,"muted");
        for(var b:hudControls)b.visible=!dragging;
        renderControls(g,mx,my,delta);
    }
    private void renderControls(GuiGraphics g,int mx,int my,float delta) {
        for (var control:children()) if (control instanceof net.minecraft.client.gui.components.Renderable drawable) drawable.render(g,mx,my,delta);
    }
    private static boolean overlaps(Rect a,Rect b){return a.x()<b.right()&&a.right()>b.x()&&a.y()<b.bottom()&&a.bottom()>b.y();}
    @Override public boolean mouseClicked(double x,double y,int button) {
        if(super.mouseClicked(x,y,button))return true;
        if(scene==Scene.HUD && button==0 && navBox!=null && hud.preview().contains(x,y)){
            if(navBox.contains(x,y)||reminderBox.contains(x,y)){dragReminder=reminderBox.contains(x,y);var r=dragReminder?reminderBox:navBox;grabX=x-r.x();grabY=y-r.y();dragging=true;return true;}
        }
        if(scene!=Scene.HUD && !(layout.compact()&&showDetails) && button==0 && layout.list().contains(x,y)){
            int row=((int)y-layout.list().y())/(layout.rowHeight()+layout.gap())+scroll;
            if(row<count()){selected=row;detailScroll=0;setFocused(null);return true;}
        }
        return false;
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){
        if(dragging && button==0){var a=hud.preview().inset(6);var r=dragReminder?reminderBox:navBox;double nx=Math.clamp((x-grabX-a.x())/Math.max(1,a.width()-r.width()),0,1),ny=Math.clamp((y-grabY-a.y())/Math.max(1,a.height()-r.height()),0,1);if(dragReminder){reminderX=nx;reminderY=ny;}else{navX=nx;navY=ny;}return true;}
        return super.mouseDragged(x,y,button,dx,dy);
    }
    @Override public boolean mouseReleased(double x,double y,int button){if(dragging){dragging=false;return true;}return super.mouseReleased(x,y,button);}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){
        if(scene==Scene.HUD && hud.settings().contains(x,y)){settingsScroll+=vertical<0?1:-1;build();return true;}
        if(scene!=Scene.HUD && (!layout.compact()||showDetails) && layout.detail().contains(x,y)){detailScroll=Math.clamp(detailScroll+(vertical<0?12:-12),0,Math.max(0,detailHeight-layout.detail().height()));return true;}
        if(scene!=Scene.HUD && layout.list().contains(x,y)){scroll+=vertical<0?1:-1;clampScroll();return true;}
        return super.mouseScrolled(x,y,horizontal,vertical);
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(key==GLFW.GLFW_KEY_F6){toggleFace();return true;}
        if(key==GLFW.GLFW_KEY_F7){nextScene();return true;}
        return super.keyPressed(key,scan,modifiers);
    }
    private final class ReviewButton extends Button {
        private final boolean primary;
        boolean selected;
        ReviewButton(Rect r,String label,boolean primary,Runnable action){super(r.x(),r.y(),r.width(),r.height(),Component.literal(label),b->action.run(),DEFAULT_NARRATION);this.primary=primary;}
        @Override protected void renderWidget(GuiGraphics g,int mx,int my,float delta){
            var r=new Rect(getX(),getY(),width,height);UiPreviewSkin.button(g,r,primary,selected,active,isHoveredOrFocused());
            var f=UiTypography.font(face);String s=UiTypography.fit(face,getMessage().getString(),Math.max(0,width-8));
            g.drawString(f,UiTypography.component(face,s),getX()+Math.max(4,(width-f.width(s))/2),getY()+(height-10)/2,c(primary||selected?"ink":active?"text":"muted"),false);
        }
    }
    private final class ReviewField extends EditBox {
        ReviewField(Rect r,String label){super(new PaperFont(UiTypography.font(face)),r.x(),r.y(),r.width(),r.height(),Component.literal(label));setTextColor(c("ink"));setTextColorUneditable(c("ink"));setFormatter((text,pos)->net.minecraft.util.FormattedCharSequence.forward(text,UiTypography.style(face)));}
        @Override public boolean isBordered(){return false;}
        @Override public int getInnerWidth(){return Math.max(0,getWidth()-8);}
        @Override public void renderWidget(GuiGraphics g,int mx,int my,float delta){UiPreviewSkin.field(g,new Rect(getX(),getY(),width,height));super.renderWidget(g,mx,my,delta);}
    }
}
