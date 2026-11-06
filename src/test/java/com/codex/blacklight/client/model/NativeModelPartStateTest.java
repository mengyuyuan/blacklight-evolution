package com.codex.blacklight.client.model;

import net.minecraft.client.model.geom.ModelPart;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class NativeModelPartStateTest {
    @Test void restoresBiologicalMaskAndAllProjectedEquipmentTransforms() {
        var arm=new ModelPart(List.of(),Map.of());
        var leg=new ModelPart(List.of(),Map.of());
        arm.visible=false;arm.skipDraw=true;
        arm.x=1;arm.y=2;arm.z=3;arm.xRot=.4F;arm.yRot=.5F;arm.zRot=.6F;
        arm.xScale=.7F;arm.yScale=.8F;arm.zScale=.9F;
        leg.visible=true;leg.xRot=1.2F;
        var state=new NativeModelPartState(arm,leg);state.capture();
        arm.visible=true;arm.skipDraw=false;
        arm.x=arm.y=arm.z=arm.xRot=arm.yRot=arm.zRot=10;
        arm.xScale=arm.yScale=arm.zScale=10;
        leg.visible=false;leg.xRot=-2;
        state.restore();
        assertFalse(arm.visible);assertTrue(arm.skipDraw);assertTrue(leg.visible);
        assertArrayEquals(new float[]{1,2,3,.4F,.5F,.6F,.7F,.8F,.9F},
                new float[]{arm.x,arm.y,arm.z,arm.xRot,arm.yRot,arm.zRot,arm.xScale,arm.yScale,arm.zScale});
        assertEquals(1.2F,leg.xRot);
        // Next frame must replace the snapshot, rather than freezing the body.
        arm.visible=true;arm.xRot=-.75F;state.capture();arm.xRot=3;state.restore();
        assertTrue(arm.visible);assertEquals(-.75F,arm.xRot);
    }
    @Test void independentBodiesNeverShareTheirSavedMasks() {
        var a=new ModelPart(List.of(),Map.of());var b=new ModelPart(List.of(),Map.of());
        a.visible=false;b.visible=true;
        var sa=new NativeModelPartState(a);var sb=new NativeModelPartState(b);sa.capture();sb.capture();
        a.visible=true;b.visible=false;sa.restore();
        assertFalse(a.visible);assertFalse(b.visible);sb.restore();assertTrue(b.visible);
    }
}
