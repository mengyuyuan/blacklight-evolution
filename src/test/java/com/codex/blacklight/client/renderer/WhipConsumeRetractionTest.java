package com.codex.blacklight.client.renderer;

import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.vertex.*;
import com.codex.blacklight.power.PowerForm;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WhipConsumeRetractionTest {
    @Test void extendedCaptureIsPreservedAndTheReleasedCableReturnsToTheActualWrist() {
        var hand=new Vec3(-.4,1.3,.1);var body=new Vec3(2.1,3.2,-.9);
        assertEquals(body,WhipConsumeRetraction.end(hand,body,1,0));
        double previous=hand.distanceTo(body);
        for(int i=0;i<=100;i++) {
            var point=WhipConsumeRetraction.end(hand,body,1,i/100F);
            double distance=point.distanceTo(hand);
            assertTrue(Double.isFinite(distance)&&distance<=previous+1e-6);
            previous=distance;
        }
        assertEquals(hand,WhipConsumeRetraction.end(hand,body,1,.4F));
        assertEquals(hand,WhipConsumeRetraction.end(hand,body,0,0));
    }
    @Test void theEmptyWrapCannotOutliveTheReceiversTissueDeformation() {
        assertEquals(1,WhipConsumeRetraction.coil(0));
        assertEquals(0,WhipConsumeRetraction.coil(1));
        assertEquals(0,WhipConsumeRetraction.cable(.4F));
        float previous=1;
        for(int i=0;i<=100;i++) {
            float amount=WhipConsumeRetraction.coil(i/100F);
            assertTrue(amount>=0&&amount<=previous);previous=amount;
        }
    }
    @Test void aConnectedBodyUsesOneNarrowingStreamInsteadOfParallelLimbCables() {
        var roots=List.of(new ConsumeTendrils.Attachment(new Vec3(2,.8,0),.2),
                new ConsumeTendrils.Attachment(new Vec3(2,1,0),.2),new ConsumeTendrils.Attachment(new Vec3(2,1.2,0),.2),
                new ConsumeTendrils.Attachment(new Vec3(2,1.4,0),.2));
        var visible=new Capture();var end=new Capture();
        ConsumeTendrils.draw(new PoseStack(),visible,15728880,roots,Vec3.ZERO,
                new ConsumeFracture.Phase(PowerForm.WHIPFIST,.8F,1,.4F,.3F,ConsumeFracture.Style.LASH,true),17);
        ConsumeTendrils.draw(new PoseStack(),end,15728880,roots,Vec3.ZERO,
                new ConsumeFracture.Phase(PowerForm.WHIPFIST,.95F,1,.8F,.3F,ConsumeFracture.Style.LASH,true),17);
        assertTrue(visible.finite&&visible.vertices>0&&visible.vertices<=1200,"duplicated full-body streams");
        assertEquals(0,end.vertices,"empty tissue stream survived absorption");
    }
    private static final class Capture implements VertexConsumer {
        int vertices;boolean finite=true;
        public VertexConsumer addVertex(float x,float y,float z){vertices++;finite&=Float.isFinite(x+y+z);return this;}
        public VertexConsumer setColor(int r,int g,int b,int a){return this;}
        public VertexConsumer setUv(float u,float v){return this;}
        public VertexConsumer setUv1(int u,int v){return this;}
        public VertexConsumer setUv2(int u,int v){return this;}
        public VertexConsumer setNormal(float x,float y,float z){return this;}
    }
}
