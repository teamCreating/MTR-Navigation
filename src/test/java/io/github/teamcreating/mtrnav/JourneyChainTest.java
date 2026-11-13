package io.github.teamcreating.mtrnav;

import io.github.teamcreating.mtrnav.core.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.nio.file.*;
import java.util.zip.ZipFile;
import static io.github.teamcreating.mtrnav.core.Model.*;
import static org.junit.jupiter.api.Assertions.*;

class JourneyChainTest {
    static RideLeg ride(String route) { return new RideLeg(new RunKey("s",0,0,0),"v",route,"a","b","a","b",0,1,"b",1000,2000,true,List.of()); }
    @Test void interleavesRealWalkingStepsAndKeepsRideOrder() {
        var walks=List.of(new WalkLeg("start","a","a","","",14*60,0,0,"personal"),new WalkLeg("change","a","b","","",5*60,0,1,"personal"),new WalkLeg("finish","b","b","","",3*60,0,2,"personal"));
        var journey=new Journey("j","d",1,"a","b",0,2000,1,List.of(ride("503"),ride("G7389")),walks,0);
        assertEquals(List.of(new JourneyChain.Item(true,"",840),new JourneyChain.Item(false,"503",0),new JourneyChain.Item(true,"",300),new JourneyChain.Item(false,"G7389",0),new JourneyChain.Item(true,"",180)),JourneyChain.of(journey));
    }
    @Test void directJourneyDoesNotInventWalkOrGenericTransitCategory() {
        var journey=new Journey("j","d",1,"a","b",0,2000,0,List.of(ride("native-route-id")));
        assertEquals(List.of(new JourneyChain.Item(false,"native-route-id",0)),JourneyChain.of(journey));
        assertTrue(JourneyChain.of(new Journey("j","d",1,"a","a",0,0,0,List.of())).isEmpty());
    }
    @Test void referencedMtrTransportIconsExistOnEveryPinnedTarget() throws Exception {
        var root=Path.of(System.getProperty("mtrnav.root"));
        for(String version:List.of("1.21.1","1.21.4"))for(String loader:List.of("neoforge","fabric")) {
            try(var jar=new ZipFile(root.resolve(".cache/downloads/MTR-"+loader+"-4.1.0-beta.2+"+version+".jar").toFile())) {
                for(String icon:List.of("train","light_rail_1","xrl_2"))assertNotNull(jar.getEntry("assets/mtr/textures/block/sign/"+icon+".png"),loader+version+icon);
            }
        }
    }
}
