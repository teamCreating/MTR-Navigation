package io.github.teamcreating.mtrnav.client;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import static io.github.teamcreating.mtrnav.client.NavigationClient.*;

/** Opt-in isolated game QA: measured controls and state; never enabled in ordinary play. */
final class OreReview {
    private static String last="";
    private static long commandId;
    private static boolean backgroundReady,pointerHeld;
    private static double pointerX,pointerY;
    private static long recordUntil;
    private static int recordFrame;
    private static String recordPrefix;
    private OreReview() {}
    static int mouseX(int fallback) { return Boolean.getBoolean("mtrnav.oreBackgroundQa")?(int)pointerX:fallback; }
    static int mouseY(int fallback) { return Boolean.getBoolean("mtrnav.oreBackgroundQa")?(int)pointerY:fallback; }
    static boolean backgroundPointer() { return Boolean.getBoolean("mtrnav.oreBackgroundQa")&&pointerHeld; }
    /** Test-only native event dispatch on the game thread, without moving the OS cursor or taking focus. */
    static void tick() {
        if(!Boolean.getBoolean("mtrnav.oreBackgroundQa"))return;
        var mc=Minecraft.getInstance();var root=mc.gameDirectory.toPath();
        try {
            if(recordPrefix!=null){
                if(System.currentTimeMillis()<recordUntil)net.minecraft.client.Screenshot.grab(mc.gameDirectory,recordPrefix+String.format(java.util.Locale.ROOT,"-%04d.png",recordFrame++),mc.getMainRenderTarget(),ignored->{});
                else recordPrefix=null;
            }
            if(!backgroundReady){java.nio.file.Files.writeString(root.resolve("mtrnav-ore-background-ready.json"),"{}");backgroundReady=true;}
            var path=root.resolve("mtrnav-ore-command.json");if(!java.nio.file.Files.isRegularFile(path))return;
            var command=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(path)).getAsJsonObject();
            long id=command.get("id").getAsLong();if(id<=commandId)return;commandId=id;
            String action=command.get("action").getAsString();var args=command.getAsJsonArray("args");
            String error="";
            try {
                var screen=mc.screen;
                switch(action) {
                    case "info","focus" -> { }
                    case "resize" -> mc.getWindow().setWindowed(args.get(0).getAsInt(),args.get(1).getAsInt());
                    case "close" -> org.lwjgl.glfw.GLFW.glfwSetWindowShouldClose(mc.getWindow().getWindow(),true);
                    case "capture" -> net.minecraft.client.Screenshot.grab(mc.gameDirectory,mc.getMainRenderTarget(),ignored->{});
                    case "record" -> {
                        String prefix=args.get(0).getAsString();if(!prefix.matches("[a-z0-9-]+"))throw new IllegalArgumentException("Invalid recording label");
                        recordPrefix=prefix;recordFrame=0;recordUntil=System.currentTimeMillis()+Math.clamp(args.get(1).getAsInt(),500,5000);
                    }
                    case "type" -> {for(char c:args.get(0).getAsString().toCharArray())screen.charTyped(c,0);}
                    case "key" -> {
                        int key=switch(args.get(0).getAsString()) {case "escape"->256;case "enter"->257;case "tab"->258;case "backspace"->259;case "left"->263;case "right"->262;case "home"->268;case "end"->269;case "f6"->295;case "f7"->296;default->throw new IllegalArgumentException("Unsupported QA key");};
                        screen.keyPressed(key,0,0);if(mc.screen==screen)screen.keyReleased(key,0,0);
                    }
                    case "click","hold","release","move","scroll" -> {
                        double scale=mc.getWindow().getGuiScale(),x=args.get(0).getAsDouble()/scale,y=args.get(1).getAsDouble()/scale;
                        if(action.equals("click")){pointerHeld=true;screen.mouseClicked(x,y,0);screen.mouseReleased(x,y,0);pointerHeld=false;}
                        else if(action.equals("hold")){
                            pointerHeld=true;screen.mouseClicked(x,y,0);double dx=args.get(2).getAsDouble()/scale,dy=args.get(3).getAsDouble()/scale;
                            screen.mouseDragged(dx,dy,0,dx-x,dy-y);x=dx;y=dy;
                        }else if(action.equals("release")){screen.mouseReleased(x,y,0);pointerHeld=false;}
                        else if(action.equals("scroll"))screen.mouseScrolled(x,y,0,args.get(2).getAsDouble());
                        else if(pointerHeld)screen.mouseDragged(x,y,0,x-pointerX,y-pointerY);else screen.mouseMoved(x,y);
                        pointerX=x;pointerY=y;
                    }
                    default -> throw new IllegalArgumentException("Unsupported QA action: "+action);
                }
            }catch(RuntimeException e){error=e.toString();pointerHeld=false;}
            java.nio.file.Files.writeString(root.resolve("mtrnav-ore-command-ack.json"),new com.google.gson.Gson().toJson(Map.of("id",id,"error",error,"screen",mc.screen==null?"":mc.screen.getClass().getSimpleName(),"scale",mc.getWindow().getGuiScale())));
        }catch(java.io.IOException|com.google.gson.JsonParseException ignored){/* An unfinished test command is retried next tick. */}
    }
    static void audit(Screen screen) {
        if(!Boolean.getBoolean("mtrnav.oreReview"))return;
        var mc=Minecraft.getInstance();
        var values=new LinkedHashMap<String,Object>();
        values.put("screen",screen.getClass().getSimpleName());values.put("viewport",List.of(screen.width,screen.height));
        values.put("scale",mc.getWindow().getGuiScale());
        if(screen instanceof NavigationPanel panel)values.put("enteredScale",panel.reviewEnteredScale());values.put("fixtures",V2Review.enabled());
        values.put("skin",NavigationTheme.skin.name());values.put("stations",catalog==null?0:catalog.stations().size());
        values.put("journeys",journeys.size());values.put("arrivals",arrivals.size());values.put("boardStation",boardStation);values.put("origin",origin);values.put("destination",destination);
        values.put("status",status);values.put("tracking",tracking==null?"":tracking.phase().name());values.put("reminder",reminder!=null);
        values.put("vehicleSupported",supportsVehicles());values.put("fleet",fleet);values.put("vehicleMode",vehicleCondition.mode().name());values.put("vehicleModels",vehicleCondition.models());
        values.put("journeyStocks",journeys.stream().map(j->j.legs().stream().map(l->l.consist()).toList()).toList());
        values.put("routeTypes",catalog==null?List.of():catalog.routes().stream().map(r->r.routeType().name()).toList());
        values.put("font","minecraft-current-default");values.put("hudPercent",NavigationPreferences.hudPercent());
        values.put("reminderPercent",NavigationPreferences.reminderPercent());values.put("autoFit",NavigationPreferences.autoFit());
        if(screen instanceof NavigationScreen nav){values.put("frame",nav.reviewFrame());values.put("body",nav.reviewBody());values.put("expandedStops",nav.reviewExpandedStops());}
        var controls=new ArrayList<Map<String,Object>>();
        List<? extends AbstractWidget> widgets=screen instanceof NavigationScreen nav?nav.reviewWidgets():screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).toList();
        for(var w:widgets)if(w.visible){
            var c=new LinkedHashMap<String,Object>();c.put("label",w.getMessage().getString());
            c.put("rect",List.of(w.getX(),w.getY(),w.getWidth(),w.getHeight()));c.put("active",w.active);c.put("focused",w.isFocused());
            if(w instanceof EditBox field)c.put("value",field.getValue());
            if(w instanceof NavigationTheme.Action action){c.put("selected",action.selected);c.put("pressDepth",Math.round(action.pressDepth()*100)/100.0);c.put("role",action.detail!=null?"result":action.disclosure?"disclosure":action.checkbox?"checkbox":action.toggle?"switch":action.segment?"segment":action.tab?"tab":"button");}
            if(w instanceof NavigationTheme.Action action && action.checkbox)c.put("resourceId",action.extra);
            if(w instanceof NavigationTheme.Action action && !action.chain.isEmpty())c.put("chain",action.chain);
            controls.add(c);
        }
        if(screen instanceof HudSettingsScreen){var pair=HudPositions.layout(true);values.put("hudBounds",List.of(pair.navigation().x(),pair.navigation().y(),pair.navigation().width(),pair.navigation().height()));values.put("reminderBounds",List.of(pair.reminder().x(),pair.reminder().y(),pair.reminder().width(),pair.reminder().height()));}
        values.put("controls",controls);
        String json=new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(values);
        if(json.equals(last))return;last=json;
        try{java.nio.file.Files.writeString(mc.gameDirectory.toPath().resolve("mtrnav-ore-audit.json"),json);}
        catch(java.io.IOException e){throw new IllegalStateException("Cannot write isolated Ore UI audit",e);}
    }
}
