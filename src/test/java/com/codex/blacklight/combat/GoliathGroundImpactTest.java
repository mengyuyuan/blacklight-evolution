package com.codex.blacklight.combat;

import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GoliathGroundImpactTest {
    @Test void nativeNestedTracksHaveThreeOneTickPulsesAfterTheBodyHit(){
        assertEquals(-1,GoliathGroundImpact.pulseIndex(38));
        assertEquals(0,GoliathGroundImpact.pulseIndex(39));
        assertEquals(1,GoliathGroundImpact.pulseIndex(40));
        assertEquals(2,GoliathGroundImpact.pulseIndex(41));
        assertEquals(-1,GoliathGroundImpact.pulseIndex(42));
        assertEquals(5,GoliathGroundImpact.pulse(2).startDistance());
        assertEquals(20,GoliathGroundImpact.pulse(2).length());
    }
    @Test void coneRaysUseSourceAnglesLengthsAndTheExtendedFistOrigin(){
        float scale=GoliathGroundImpact.scale();var root=new Vec3(11,7,-9);
        for(int pulse=0;pulse<3;pulse++)for(float yaw:new float[]{0,90,-137}){
            var p=GoliathGroundImpact.pulse(pulse);
            // Independently decoded actor-local position (2.5,0,-13), plus
            // reference Motion_Root Y=6, source X mirror, then render yaw.
            var center=new Vec3(2.5*scale,6*scale,13*scale).yRot((float)Math.toRadians(-yaw)).add(root);
            var rays=GoliathGroundImpact.rays(pulse,p.atTick(),root,yaw,RandomSource.create(913+pulse));
            assertTrue(rays.size()>=5&&rays.size()<=10);
            for(var ray:rays){
                var direction=ray.to().subtract(ray.from()).normalize();
                assertEquals(p.length()*scale,ray.from().distanceTo(ray.to()),.0001,"length is measured after startDistance");
                assertEquals(p.startDistance()*scale,center.distanceTo(ray.from()),.0001);
                assertTrue(ray.from().distanceTo(center.add(direction.scale(p.startDistance()*scale)))<.0001);
                double angle=Math.toDegrees(Math.acos(-direction.y));
                assertTrue(angle>=p.minAngle()-.001&&angle<=p.maxAngle()+.001,"5/15/25/35 are polar degrees, not radial distances");
                double travel=(root.y-ray.from().y)/direction.y;
                assertTrue(travel>=0&&travel<=ray.from().distanceTo(ray.to()),"ray reaches the flat support plane");
                var ground=ray.from().add(direction.scale(travel));
                double radius=ground.subtract(center).horizontalDistance();
                assertTrue(radius>=6*scale*Math.tan(Math.toRadians(p.minAngle()))-.0001);
                assertTrue(radius<=6*scale*Math.tan(Math.toRadians(p.maxAngle()))+.0001);
                assertTrue(ground.subtract(root).horizontalDistance()>6,"the real landing effects are ahead at the fist, not centered on the boss feet");
            }
        }
    }
}
