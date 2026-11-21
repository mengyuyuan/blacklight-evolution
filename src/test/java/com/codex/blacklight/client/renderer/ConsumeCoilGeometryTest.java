package com.codex.blacklight.client.renderer;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConsumeCoilGeometryTest {
    private static final Vec3 UP=new Vec3(0,1,0), OUT=new Vec3(1,0,0), SIDE=new Vec3(0,0,-1);

    private static Vec3[] route(int index) {
        var frame=new SmallMobConsumeSling.CoilFrame(new Vec3(0,.78,0),new Vec3(0,1.30,0),
                UP,OUT,SIDE,.32,new Vec3(.62,1.05,0),UP,OUT,SIDE,.30,.44,Vec3.ZERO);
        return SmallMobConsumeSling.coilPath(index,frame,0);
    }

    private static double winding(Vec3[] path,double centerX) {
        double total=0;
        for(int i=3;i<path.length-2;i++) {
            Vec3 a=path[i-1],b=path[i];
            double angle=Math.atan2(-b.z,b.x-centerX)-Math.atan2(-a.z,a.x-centerX);
            total+=Math.atan2(Math.sin(angle),Math.cos(angle));
        }
        return total/(2*Math.PI);
    }

    @Test void primaryStrandMakesThreeDescendingTurnsOutsideBothBodies() {
        var path=route(0);
        assertTrue(winding(path,.31)<-2.9);
        int outsidePrey=0,behindPlayer=0;
        for(int i=2;i<path.length-2;i++) {
            if(path[i].x>.89)outsidePrey++;
            if(path[i].x<-.31)behindPlayer++;
            assertTrue(path[i].y>.58 && path[i].y<1.52);
        }
        assertTrue(outsidePrey>=6 && behindPlayer>=6);
        assertTrue(path[2].y-path[path.length-3].y>.75);
    }

    @Test void crossingStrandMakesAnOppositeWrapOnTheVictim() {
        var path=route(1);
        assertTrue(winding(path,.62)>1.8);
        for(int i=2;i<path.length-2;i++)
            assertTrue(Math.hypot(path[i].x-.62,path[i].z)>.33);
    }
}
