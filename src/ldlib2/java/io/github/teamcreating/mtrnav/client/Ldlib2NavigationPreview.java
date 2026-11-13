package io.github.teamcreating.mtrnav.client;

import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.lowdragmc.lowdraglib2.gui.editor.UIEditor;
import com.lowdragmc.lowdraglib2.gui.editor.view.UIEditorView;
import com.lowdragmc.lowdraglib2.editor.resource.UIResource;
import com.lowdragmc.lowdraglib2.editor.resource.FileResourceProvider;
import com.lowdragmc.lowdraglib2.editor.resource.FilePath;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.UITemplate;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.utils.XmlUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import com.google.gson.GsonBuilder;

/** Optional 1.21.1 NeoForge trial, excluded entirely from the normal four builds. */
public final class Ldlib2NavigationPreview extends ModularUIScreen {
    private static final String[] TITLES={"直达 · 28 分钟","换乘 1 次 · 31 分钟","换乘 1 次 · 36 分钟","换乘 2 次 · 42 分钟","步行换乘 · 45 分钟"};
    private static final ResourceLocation XML=ResourceLocation.fromNamespaceAndPath("mtrnav","ui/ldlib2/navigation.xml");
    private static final String[] THEMES={"native","editor","create"},THEME_NAMES={"原版控件","编辑器","Create"};
    private static final class State {
        UiTypography.Face face="pixel".equals(System.getProperty("mtrnav.uiPreviewFont"))?UiTypography.Face.PIXEL:UiTypography.Face.SMOOTH;
        String origin="南苑 Nanyuan",destination="滨海北 Binhai North";
        int selected,scene,theme;
        boolean showDetails,editQuery,useTemplate;
    }
    private final UI ui;
    private final State state;
    private double previousScale=Double.NaN;
    private int auditDelay=10;
    public static Screen create() { return create(new State()); }
    private static Screen create(State state) {
        if(state.useTemplate&&templatePath(state)!=null&&Files.isRegularFile(templatePath(state))){
            var template=templateProvider(state).getResource(new FilePath(templatePath(state).toFile()));
            if(template==null)throw new IllegalStateException("Cannot decode the saved LDLib2 navigation template");
            return new Ldlib2NavigationPreview(template.createUI(),state);
        }
        org.w3c.dom.Document xml;
        var path=editablePath(state);
        if(path!=null&&Files.isRegularFile(path)){
            try(var in=Files.newInputStream(path)){xml=XmlUtils.loadXml(in);}
            catch(IOException e){throw new IllegalStateException("Cannot read LDLib2 preview XML",e);}
        }else xml=XmlUtils.loadXml(ResourceLocation.fromNamespaceAndPath("mtrnav","ui/ldlib2/navigation-"+THEMES[state.theme]+".xml"));
        if(xml==null)throw new IllegalStateException("Invalid LDLib2 navigation XML; existing formal UI is unaffected");
        var ui=UI.of(xml);
        return new Ldlib2NavigationPreview(ui,state);
    }
    private Ldlib2NavigationPreview(UI ui,State state){
        super(ModularUI.of(ui),Component.literal("站间物语 · LDLib2 试验"));
        this.ui=ui;this.state=state;
        field("origin").setText(state.origin,false).setTextResponder(value->{state.origin=value;updateDetails();});
        field("destination").setText(state.destination,false).setTextResponder(value->{state.destination=value;updateDetails();});
        on("close",this::onClose);
        on("font",()->{state.face=state.face.next();reload();});
        on("theme",this::nextTheme);
        on("editor",this::openEditor);
        on("swap",()->{var temp=state.origin;state.origin=state.destination;state.destination=temp;reload();});
        on("scene",this::nextScene);
        on("search",()->{state.scene=state.origin.isBlank()||state.destination.isBlank()?2:0;reload();});
        on("stations",()->status("测试预览：目录接入将在框架验证后进行"));
        on("walking",()->status("测试预览：步行查询逻辑保持原样"));
        on("navigate",()->status("测试数据，不会开始真实导航"));
        on("show-list",()->{state.showDetails=false;updateCompact();});
        on("show-detail",()->{state.showDetails=true;updateCompact();});
        on("compact-query",()->{state.editQuery=!state.editQuery;updateCompact();});
        for(int i=0;i<5;i++){int index=i;on("journey-"+i,()->{state.selected=index;updateDetails();});}
        ui.rootElement.selfAndAllChildren().forEach(n->{
            var font=ResourceLocation.fromNamespaceAndPath("mtrnav","ui_"+state.face.id);
            if(n instanceof TextElement text)text.getTextStyle().font(font);
            if(n instanceof TextField field)field.getTextFieldStyle().font(font);
        });
        button("font").setText(state.face==UiTypography.Face.PIXEL?"A 像素中文":"B 清晰中文");
        button("theme").setText(THEME_NAMES[state.theme]);
        updateDetails();
    }
    private static Path editablePath(){var value=System.getProperty("mtrnav.ldlib2Xml","");return value.isBlank()?null:Path.of(value);}
    private static Path editablePath(State state){var base=editablePath();return base==null?null:base.resolveSibling("navigation-"+THEMES[state.theme]+".xml");}
    private static Path templatePath(State state){var xml=editablePath(state);return xml==null?null:xml.resolveSibling("navigation-"+THEMES[state.theme]+".ui.nbt");}
    private static FileResourceProvider<UITemplate> templateProvider(State state){return new FileResourceProvider<>(UIResource.INSTANCE.getResourceInstance(),templatePath(state).getParent().toFile());}
    private UIElement node(String id){return ui.selectId(id).findFirst().orElseThrow(()->new IllegalStateException("Missing trial element: "+id));}
    private Button button(String id){return (Button)node(id);}
    private TextField field(String id){return (TextField)node(id);}
    private void text(String id,String value){((TextElement)node(id)).setText(UiTypography.component(state.face,value));}
    private void on(String id,Runnable action){button(id).setOnClick(event->action.run());}
    private void status(String value){text("status",value);}
    private void updateDetails(){
        for(int i=0;i<5;i++){
            var row=node("journey-"+i);row.removeClass("selected");
            row.setDisplay(state.scene!=2&&(state.scene!=3||i==0));
        }
        node("journey-"+state.selected).addClass("selected");
        node("empty-list").setVisible(state.scene==2);
        text("result-count","行程方案 · "+(state.scene==2?0:state.scene==3?1:5));
        text("route-title",state.scene==2?"没有找到可用路线":state.scene==3?"线路参考 · 班次未确认":TITLES[state.selected]);
        text("origin-detail",state.origin);text("destination-detail",state.destination);
        text("summary-label",state.origin+" → "+state.destination);
        text("departure",state.scene==3?"当前暂无可乘班次":state.scene==2?"请更换站点或查询条件":"下一班 08:12 · 1 站台");
        text("arrival",state.scene>=2?"":"08:40 到达 · 2 站台");
        text("state-note",state.scene==3?"仅显示换乘路径，班次未确认，暂不能开始导航。":"测试数据，不会发送查询或开始真实导航。");
        button("navigate").setActive(state.scene<2);
        auditDelay=10;
    }
    private void updateCompact(){
        if(state.showDetails)ui.rootElement.addClass("show-detail");else ui.rootElement.removeClass("show-detail");
        boolean compact=width<456||height<316;
        node("query").setDisplay(!compact||state.editQuery);
        node("toolbar").setDisplay(!compact||state.editQuery);
        button("show-list").removeClass("selected");button("show-detail").removeClass("selected");
        button(state.showDetails?"show-detail":"show-list").addClass("selected");
        auditDelay=10;
    }
    private void nextScene(){
        state.scene=(state.scene+1)%4;state.selected=0;
        if(state.scene==1){state.origin="滨海国际机场交通中心 Binhai International Airport Transport Centre";state.destination="北部新区综合交通枢纽 North District Integrated Transport Hub";}
        else{state.origin="南苑 Nanyuan";state.destination="滨海北 Binhai North";}
        reload();
    }
    private void reload(){Minecraft.getInstance().setScreen(create(state));}
    private void nextTheme(){state.theme=(state.theme+1)%THEMES.length;state.useTemplate=false;reload();}
    private void openEditor(){
        if(templatePath(state)==null){status("可视化编辑器需要开发启动参数指定项目路径");return;}
        var provider=templateProvider(state);
        var path=new FilePath(templatePath(state).toFile());
        if(!Files.isRegularFile(templatePath(state))){
            var template=ui.toTemplate();
            try{
                var xml=XmlUtils.loadXml(Files.readString(editablePath(state)));
                var styles=xml.getDocumentElement().getElementsByTagName("style");
                template.setBuiltinStyles(styles.item(0).getTextContent());
                template.getStylesheets().add(ResourceLocation.fromNamespaceAndPath("ldlib2","lss/mc.lss"));
            }catch(IOException e){throw new IllegalStateException("Cannot export the XML stylesheet",e);}
            if(!provider.addResource(path,template))throw new IllegalStateException("Cannot save LDLib2 template");
        }
        state.useTemplate=true;
        var editorWindow=new EditorWindow(()->{
            var editor=new UIEditor();
            var view=new UIEditorView().loadTemplate(provider.getResource(path),template->{
                if(!provider.addResource(path,template))throw new IllegalStateException("Cannot save edited LDLib2 template");
            });
            editor.centerWindow.getLeftTop().addView(view);
            return editor;
        });
        showEditorScreen(editorWindow);
    }
    private void openXmlEditor(){
        state.useTemplate=false;
        var path=editablePath(state);
        if(path==null){status("编辑器需要开发启动参数指定 XML 路径");return;}
        var editorWindow=new EditorWindow(()->{
            var editor=new UIEditor();
            if(!editor.fileMenu.openProject(path.toFile()))throw new IllegalStateException("LDLib2 editor could not open navigation.xml");
            return editor;
        });
        showEditorScreen(editorWindow);
    }
    private void showEditorScreen(EditorWindow editorWindow){
        var screen=new ModularUIScreen(ModularUI.of(UI.of(editorWindow)),Component.literal("站间物语 · 界面编辑器")){
            @Override public boolean keyPressed(int key,int scan,int modifiers){
                if(key==GLFW.GLFW_KEY_F8){Minecraft.getInstance().setScreen(create(state));return true;}
                return super.keyPressed(key,scan,modifiers);
            }
        };
        Minecraft.getInstance().setScreen(screen);
    }
    @Override public void init(){
        var window=Minecraft.getInstance().getWindow();
        if(Double.isNaN(previousScale))previousScale=window.getGuiScale();
        int normal=window.calculateScale(Minecraft.getInstance().options.guiScale().get(),Minecraft.getInstance().isEnforceUnicode());
        window.setGuiScale(Math.min(normal,window.getHeight()>=1260&&window.getWidth()>=1920?3:2));
        width=window.getGuiScaledWidth();height=window.getGuiScaledHeight();
        ui.rootElement.layout(l->l.width(Math.min(520,width-16)).height(Math.min(360,height-16)));
        if(width<456||height<316)ui.rootElement.addClass("compact");else ui.rootElement.removeClass("compact");
        updateCompact();super.init();auditDelay=10;
    }
    @Override public void removed(){super.removed();if(!Double.isNaN(previousScale))Minecraft.getInstance().getWindow().setGuiScale(previousScale);}
    @Override public void renderBackground(net.minecraft.client.gui.GuiGraphics graphics,int mouseX,int mouseY,float delta){graphics.fill(0,0,width,height,0x99121B16);}
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(key==GLFW.GLFW_KEY_F5){nextTheme();return true;}
        if(key==GLFW.GLFW_KEY_F6){state.face=state.face.next();reload();return true;}
        if(key==GLFW.GLFW_KEY_F7){nextScene();return true;}
        if(key==GLFW.GLFW_KEY_F8){reload();return true;}
        if(key==GLFW.GLFW_KEY_F9){openEditor();return true;}
        if(key==GLFW.GLFW_KEY_F10){openXmlEditor();return true;}
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public void tick(){super.tick();if(auditDelay>0&&--auditDelay==0)writeAudit();}
    private void writeAudit(){
        var data=new LinkedHashMap<String,Object>();data.put("viewport",new int[]{width,height});
        data.put("guiScale",Minecraft.getInstance().getWindow().getGuiScale());data.put("font",state.face.id);data.put("scene",state.scene);data.put("theme",THEMES[state.theme]);
        var bounds=new LinkedHashMap<String,Object>();
        for(String id:new String[]{"workspace","query","body","results","detail","footer","navigate","journey-0","journey-4"}){
            var n=node(id);bounds.put(id,Map.of("x",n.getPositionX(),"y",n.getPositionY(),"w",n.getSizeWidth(),"h",n.getSizeHeight()));
        }
        data.put("bounds",bounds);
        try{Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve("mtrnav-ldlib2-audit.json"),new GsonBuilder().setPrettyPrinting().create().toJson(data));}
        catch(IOException e){com.mojang.logging.LogUtils.getLogger().warn("Cannot write LDLib2 preview audit",e);}
    }
}
