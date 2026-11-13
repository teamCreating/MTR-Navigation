package io.github.teamcreating.mtrnav;

import io.github.teamcreating.mtrnav.core.Model;
import io.github.teamcreating.mtrnav.server.MtrCoreReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mtr.core.data.*;
import org.mtr.core.serializer.JsonReader;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.Angle;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.com.google.gson.JsonArray;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in operational MTR world, generated only into a fresh explicitly supplied directory. */
@EnabledIfSystemProperty(named = "mtrnav.generateVehicleFixture", matches = "true")
class VehicleRuntimeFixtureTest {
    static Position pos(long x, long z) { return new Position(x, -59, z); }
    static JsonObject position(Position p) { var j = new JsonObject(); j.addProperty("x", p.getX()); j.addProperty("y", p.getY()); j.addProperty("z", p.getZ()); return j; }
    static Rail rail(long from, long to, long z, int kind) {
        var a = pos(from, z); var b = pos(to, z);
        return kind == 1 ? Rail.newPlatformRail(a, Angle.E, b, Angle.W, Rail.Shape.QUADRATIC, 0,0,0,0,0,0,0,0,0,0,0,0,0,new ObjectArrayList<>(),TransportMode.TRAIN)
                : kind == 2 ? Rail.newSidingRail(a, Angle.E, b, Angle.W, Rail.Shape.QUADRATIC, 0,0,0,0,0,0,0,0,0,0,0,0,0,new ObjectArrayList<>(),TransportMode.TRAIN)
                : Rail.newRail(a, Angle.E, b, Angle.W, Rail.Shape.QUADRATIC, 0,0,0,0,0,0,0,0,0,0,0,0,0,new ObjectArrayList<>(),80,80,false,false,true,false,true,TransportMode.TRAIN);
    }
    static Route route(Simulator simulator, String name, RouteType type, List<Platform> platforms, int color) {
        var route = new Route(TransportMode.TRAIN, simulator); var j = new JsonObject(); j.addProperty("name", name); j.addProperty("color", color); j.addProperty("routeType", type.name());
        var stops = new JsonArray(); for (var p : platforms) { var stop = new JsonObject(); stop.addProperty("platformId",p.getId()); stops.add(stop); }
        j.add("routePlatformData", stops); route.updateData(new JsonReader(j));
        for (int i=0;i<platforms.size();i++) route.getRoutePlatforms().get(i).platform=platforms.get(i);
        simulator.routes.add(route); return route;
    }
    @Test void generateOperationalWorld() throws Exception {
        var root = Path.of(System.getProperty("mtrnav.fixtureRoot"));
        assertFalse(Files.exists(root), "Never overwrite an existing fixture or user world");
        var simulator = new Simulator("minecraft/overworld", new String[]{"minecraft/overworld"}, root, false);
        try {
            var stations = new ArrayList<Station>();
            for (int i=0;i<3;i++) {
                var station = new Station(simulator); var j = new JsonObject();
                j.addProperty("name", new String[]{"车型测试起点|Vehicle Origin","车型测试中站|Vehicle Central","车型测试终点|Vehicle Terminal"}[i]);
                j.add("position1",position(pos(i*300-10,-10))); j.add("position2",position(pos(i*300+110,50)));
                station.updateData(new JsonReader(j)); simulator.stations.add(station); stations.add(station);
            }
            var depots = new ObjectArrayList<Depot>();
            for (int line=0;line<3;line++) {
                long z=line*20; var platforms = new ArrayList<Platform>();
                for (int i=0;i<3;i++) {
                    var r=rail(i*300,i*300+100,z,1); simulator.rails.add(r);
                    var p=new Platform(pos(i*300,z),pos(i*300+100,z),TransportMode.TRAIN,simulator);p.rail=r;p.setName(String.valueOf(line+1));
                    simulator.platforms.add(p); platforms.add(p);
                    if(i<2)simulator.rails.add(rail(i*300+100,(i+1)*300,z,0));
                }
                var type=RouteType.values()[line]; String name=new String[]{"普通铁路测试线","轻轨测试线","高速测试线"}[line]; int color=new int[]{0x4FA3D9,0xE8A13C,0xB78BE0}[line];
                var outbound=route(simulator,name,type,platforms,color);
                var reverse=new ArrayList<>(platforms);Collections.reverse(reverse);
                var inbound=route(simulator,name,type,reverse,color);
                simulator.rails.add(rail(700,800,z,0));
                var sidingRail=rail(800,1050,z,2);simulator.rails.add(sidingRail);
                var siding=new Siding(pos(800,z),pos(1050,z),250,TransportMode.TRAIN,simulator);siding.rail=sidingRail;siding.setName("车型测试 "+line);siding.setUnlimitedVehicles(true);siding.setMaxVehicles(20);
                String[] cars=line==0?new String[]{"m_train_cab_1","m_train_trailer","m_train_cab_2"}:line==1?new String[]{"sp1900_cab_1","sp1900_trailer","sp1900_cab_2"}:new String[]{"m_train_cab_1","sp1900_trailer","m_train_cab_2"};
                var stock=new ObjectArrayList<VehicleCar>();for(String id:cars)stock.add(new VehicleCar(id,20,3,100,6,-6,.5,.5));siding.setVehicleCars(stock);simulator.sidings.add(siding);
                var depot=new Depot(TransportMode.TRAIN,simulator);var j=new JsonObject();j.addProperty("name","车型测试车库 "+line);j.addProperty("color",color);
                j.add("position1",position(pos(790,z-4)));j.add("position2",position(pos(1060,z+4)));
                var routes=new JsonArray();routes.add(inbound.getId());routes.add(outbound.getId());j.add("routeIds",routes);depot.updateData(new JsonReader(j));depot.routes.add(inbound);depot.routes.add(outbound);
                depot.setUseRealTime(true);depot.setRepeatInfinitely(false);
                for(long t=0;t<86_400_000;t+=120_000)depot.getRealTimeDepartures().add(t+line*20_000);
                simulator.depots.add(depot);depots.add(depot);
            }
            simulator.sync();Depot.generateDepots(simulator,depots);
            for(int i=0;i<1000;i++)simulator.tick();
            for(var depot:depots) {
                depot.getFailedPlatformIds((start,end)->System.out.println("Failed stretch "+start+" -> "+end),count->System.out.println("Failed sidings "+count));
                System.out.println("Depot "+depot.getName()+" path="+depot.getPath().size()+" sidings="+depot.savedRails.size());
            }
            simulator.platforms.forEach(p->System.out.println("Platform "+p.getId()+" "+p.getName()+" at "+p.getMidPosition().getX()+","+p.getMidPosition().getZ()));
            for(var depot:depots)assertEquals(Depot.GeneratedStatus.SUCCESSFUL,depot.getLastGeneratedStatus(),depot.getName());
            var snapshot=new MtrCoreReader().capture(simulator,Model.Settings.defaults());
            assertFalse(snapshot.events().isEmpty(),"Native MTR must expose usable departures");
            assertEquals(6,snapshot.catalog().routes().size());assertTrue(snapshot.fleet().contains("sp1900_trailer"));
            System.out.println("Vehicle fixture: "+snapshot.events().size()+" native stop events; stocks="+snapshot.fleet());
            simulator.save();
        } finally {simulator.stop();}
    }
}
