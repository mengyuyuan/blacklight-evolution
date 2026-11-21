package com.codex.blacklight.client.renderer;

import com.codex.blacklight.entity.QuillOrgan;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuillTendrilsTest {
    @Test void strandsTraverseTheCoreAndBreakTheOppositeSurface(){
        for(double[] size:new double[][]{{.6,1.95},{.9,1.4},{.32,.24},{2.2,5.0}}){
            double w=size[0],h=size[1];
            for(Vec3 wound:new Vec3[]{new Vec3(.48*w,.68*h,-.2*w),new Vec3(0,.95*h,0),new Vec3(0,.53*h,0)}){
                var paths=new QuillTendrils(wound,w,h,170,1.7);
                for(var strand:paths.strands){
                    var center=new Vec3(0,.53*h,0);
                    var normalizer=new Vec3(1/(.43*w),1/(.38*h),1/(.43*w));
                    var entry=strand.puncture().subtract(center).multiply(normalizer);
                    var tip=strand.tip().subtract(center).multiply(normalizer);
                    assertEquals(1,entry.length(),1e-6);
                    assertTrue(strand.buried().subtract(center).multiply(normalizer).length()<.5);
                    assertTrue(tip.dot(entry)<0 && tip.length()>1.2);
                    assertTrue(strand.point(QuillTendrils.CONTACT,.65).distanceTo(strand.puncture())<1e-7);
                    assertTrue(strand.point(QuillTendrils.EXIT,1).distanceTo(strand.exit())<1e-7);
                    assertTrue(strand.point(1,1).distanceTo(strand.tip())<1e-7);
                }
            }
        }
    }
    @Test void tipsClearTheFarSideBeforePullingBackDownTheSameTract(){
        for(int i=0;i<QuillTendrils.COUNT;i++){
            assertEquals(0,QuillTendrils.emergence(0,i));
            assertTrue(QuillTendrils.emergence(2,i)>.2);
            assertTrue(QuillTendrils.emergence(QuillOrgan.PIERCE_TICK,i)>QuillTendrils.CONTACT);
            assertEquals(1,QuillTendrils.emergence(QuillOrgan.FIRST_DRAW_TICK,i));
            assertEquals(1,QuillTendrils.emergence(8,i));
            assertTrue(QuillTendrils.emergence(10,i)<QuillTendrils.EXIT);
            assertEquals(0,QuillTendrils.emergence(12,i));
        }
    }
}
