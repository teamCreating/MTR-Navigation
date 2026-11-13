package io.github.teamcreating.mtrnav.client;

import io.github.teamcreating.mtrnav.core.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import java.util.*;
import static io.github.teamcreating.mtrnav.core.Model.*;
import static io.github.teamcreating.mtrnav.client.NavigationClient.*;

/** Explicit isolated review only: exercises the formal screen with labelled fixtures, without network requests. */
final class V2Review {
    private static int scene;
    private static long base;
    private static List<Journey> samples=List.of();
    static boolean enabled(){return Boolean.getBoolean("mtrnav.v2Review");}
    static Screen open(){scene=0;setup();return new NavigationScreen();}
    static void setup(){
        base=System.currentTimeMillis();usingNative=false;origin="a";destination="d";tracking=null;referencePlan=null;vehicleCondition=VehicleCondition.none();fleet=List.of("m_train_cab_1","m_train_trailer","sp1900_cab_1");status="mtrnav.ready";
        String from=scene==1?"苔灯港星瀚航站综合交通枢纽|Mosslight Harbour Aster Terminal Transport Hub":"苔灯港|Mosslight Harbour";
        String to=scene==1?"星沼湾月影新区综合换乘中心|Starfen Bay Moonshadow District Interchange":"星沼湾|Starfen Bay";
        catalog=new Catalog("minecraft/overworld",1,List.of(new Station("a",from,0,64,0,List.of()),new Station("b","风铃渡|Windchime Crossing",60,64,0,List.of()),new Station("c","雾松丘|Mistpine Hill",30,64,0,List.of()),new Station("d",to,120,64,0,List.of())),
            List.of(new Platform("a1","a","1",0,64,0),new Platform("b1","b","星汐快线",60,64,0),new Platform("b4","b","B 站台",60,64,4),new Platform("d4","d","B 站台",120,64,4)),
            List.of(new Route("star-tide","星汐快线",0x4FA3D9,List.of("a1","b1"),false,RouteType.HIGH_SPEED),new Route("firefly","萤环线",0xE8A13C,List.of("b4","d4"),false,RouteType.NORMAL),new Route("cloud-bay","岚湾轻轨",0xB78BE0,List.of("a1","b1"),false,RouteType.LIGHT_RAIL)));
        var all=new ArrayList<Journey>();
        for(int i=0;i<5;i++){
            long dep=base+(4+i)*60000L,arr=base+(26+i*5)*60000L;
            var first=new RideLeg(new RunKey("sample-"+i,0,i,0),"test",""+(i==1?"cloud-bay":"star-tide"),"a","b","a1","b1",0,2,"风铃渡",dep,dep+7*60000,true,List.of("c"),new Consist(List.of(i==1?"sp1900_cab_1":"m_train_cab_1","m_train_trailer","m_train_cab_1"),StockSource.ACTUAL));
            var second=new RideLeg(new RunKey("sample-last-"+i,0,i,0),"test","firefly","b","d","b4","d4",0,1,"星沼湾",dep+14*60000,arr,false,List.of(),new Consist(List.of("m_train_cab_1","m_train_trailer"),StockSource.PLANNED));
            var walk=new WalkLeg("test","b","b","b1","b4",180,first.arrival(),1,"personal");
            all.add(new Journey("sample-"+i,"minecraft/overworld",1,"a","d",base,arr,1,List.of(first,second),List.of(walk),0));
        }
        samples=List.copyOf(all);journeys=samples;fastestArrival=samples.getFirst().arrival();
        if(scene==2){journeys=List.of();origin=destination="";status="mtrnav.choose_stations";}
        if(scene==3){journeys=List.of();referencePlan=ConnectionPlan.find(catalog,"a","d").orElseThrow();referenceReason="mtrnav.reference_no_service";}
        if(scene>=7){journeys=List.of();status=scene==7?"mtrnav.searching":scene==8?"mtrnav.search_failed":"mtrnav.no_route";}
        if(scene==6)tracking=new JourneyTracker.State(samples.getFirst(),0,JourneyTracker.Phase.RIDING,"",false);
        refresh();
    }
    static void refresh(){long now=System.nanoTime();CLOCK.sample(System.currentTimeMillis(),now);if(!boardStation.isEmpty()){boardSampleMillis=System.currentTimeMillis();boardUpdatedNanos=now;}}
    static void query(){journeys=origin.isBlank()||destination.isBlank()?List.of():vehicleCondition.mode()==VehicleMode.REQUIRE?samples.stream().filter(vehicleCondition::matches).toList():vehicleCondition.mode()==VehicleMode.PREFER?samples.stream().sorted(Comparator.comparing(j -> !vehicleCondition.matches(j))).toList():samples;referencePlan=null;status=journeys.isEmpty()?"mtrnav.choose_stations":"mtrnav.ready";}
    static void board(String station){
        boardStation=station;boardLoading=false;
        var list=new ArrayList<StopEvent>();
        for(int i=0;i<6;i++)list.add(new StopEvent(new RunKey("review-"+i,0,i,0),"test",i<2?"star-tide":i<5?"firefly":"cloud-bay","a1",0,i<2?"风铃渡":"星沼湾",base+(3+i*4)*60000L,base+(4+i*4)*60000L,0,i%2==0,new Consist(List.of("m_train_cab_1","m_train_trailer","m_train_cab_1"),i%2==0?StockSource.ACTUAL:StockSource.PLANNED)));
        arrivals=List.copyOf(list);refresh();
    }
    static void cycle(NavigationScreen screen){
        scene=(scene+1)%(Boolean.getBoolean("mtrnav.oreReview")?10:7);boardStation="";arrivals=List.of();setup();
        if(scene==5){Minecraft.getInstance().setScreen(new HudSettingsScreen(screen));return;}
        screen.reviewView(scene==4);
    }
}
