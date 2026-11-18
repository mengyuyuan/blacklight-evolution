package com.codex.blacklight.client.renderer;
import com.codex.blacklight.combat.HydraContact;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class HydraWoundAnchorTest {
    private static List<HydraContact.Region> body(Vec3 center,double yaw){
        var x=new Vec3(Math.cos(yaw),0,Math.sin(yaw));var z=new Vec3(-Math.sin(yaw),0,Math.cos(yaw));
        var box=HydraContact.oriented(center,new Vec3(1,1,1),x,new Vec3(0,1,0),z);
        return List.of(new HydraContact.Region("head",List.of(box),box.bounds));
    }
    @Test void sprayStartsOutsideCurrentEntrySkin(){
        var anchor=HydraWoundAnchor.capture(body(Vec3.ZERO,0),new Vec3(.8,0,0),new Vec3(1,0,0));
        assertNotNull(anchor);assertEquals(1.035,anchor.point().x,1e-6);assertEquals(new Vec3(1,0,0),anchor.normal());
    }
    @Test void followsReceiverRotationThenReleasesToFreeMotion(){
        var anchor=HydraWoundAnchor.capture(body(Vec3.ZERO,0),new Vec3(1,0,0),new Vec3(1,0,0));
        anchor.update(body(new Vec3(3,2,1),Math.PI/2),2);
        assertEquals(0,anchor.point().distanceTo(new Vec3(3,2,2.035)),1e-6);
        assertEquals(0,anchor.normal().distanceTo(new Vec3(0,0,1)),1e-6);
        var detached=anchor.point();anchor.update(body(new Vec3(30,20,10),0),4);assertEquals(detached,anchor.point());
    }
    @Test void absentBodyCannotInventAnAnchor(){
        assertNull(HydraWoundAnchor.capture(body(new Vec3(20,0,0),0),Vec3.ZERO,new Vec3(1,0,0)));
        assertNull(HydraWoundAnchor.capture(List.of(),Vec3.ZERO,new Vec3(1,0,0)));
    }
}
