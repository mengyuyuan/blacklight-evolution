package com.codex.blacklight.client.renderer;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Golden points from the original builder, sampler and native segment spacing. */
class SourceTendrilPathTest {
    private static void near(Vec3 actual,double x,double y,double z){
        assertTrue(actual.distanceTo(new Vec3(x,y,z))<0.0001,actual::toString);
    }

    @Test void usesTheSourceLaunchArcAndItsStraightTail(){
        var points=SourceTendrilPath.sample(Vec3.ZERO,new Vec3(0,0,1),.3,60,160,10);
        near(points[1],.10123538,-.05844827,-.14793596);
        near(points[5],.48302349,-.27887368,.22055717);
        near(points[9],.09772074,-.05641907,.84490871);
        near(points[10],0,0,1);
    }

    @Test void feedsOutAuthoredSectionsFromTheRoot(){
        var points=SourceTendrilPath.sample(Vec3.ZERO,new Vec3(0,0,1),.3,60,160,10,.7863644504547118);
        for(int i=0;i<=5;i++)near(points[i],0,0,0);
        near(points[9],.42315716,-.24430986,-.11481092);
        near(points[10],.50014383,-.28875813,.05146338);
        var straight=SourceTendrilPath.sample(Vec3.ZERO,new Vec3(.3,.4,.7),.3,0,0,10,.35269533813);
        near(straight[9],.093,.12400001,.21699999);
        near(straight[10],.12300001,.16400002,.287);
    }

    @Test void supportsVerticalAndCloselySpacedJoints(){
        var vertical=SourceTendrilPath.sample(Vec3.ZERO,new Vec3(0,1,0),.3,0,70,10);
        near(vertical[1],-.09517132,.05633272,0);
        near(vertical[5],-.15607095,.46619982,0);
        var shortArc=SourceTendrilPath.sample(Vec3.ZERO,new Vec3(0,0,.22),.3,60,160,10);
        near(shortArc[1],0,0,0);
        near(shortArc[5],.13642542,-.07876524,-.17039633);
        near(shortArc[9],.49777412,-.28738996,.16761258);
    }

    @Test void samplesShortFiveSectionShoulderTracks(){
        var start=new Vec3(.4,.7,-.9);
        var points=SourceTendrilPath.sample(start,new Vec3(.84,.85,-1.12),.472,-99.5,90,5);
        assertEquals(6,points.length);
        near(points[1],.48708379,.73054683,-.90082860);
        near(points[2],.57531285,.76041013,-.95562142);
        near(points[5],.84,.85,-1.12);
        var collapsed=SourceTendrilPath.sample(start,new Vec3(.425,.712,-.891),.3,-90,90,10);
        for(var point:collapsed)near(point,start.x,start.y,start.z);
    }
}
