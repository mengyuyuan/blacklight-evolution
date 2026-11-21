package com.codex.blacklight.client.renderer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FirstPersonConsumeTendrilsTest {
    private static Matrix4f projection(double fov){return new Matrix4f().perspective((float)Math.toRadians(fov),16F/9,.05F,128);}
    @Test void rootFollowsHandAndReceiverAtBothFovExtremes(){
        for(double fov:new double[]{45,95})for(int side:new int[]{-1,1}){
            var p=projection(fov);var inv=new Matrix4f(p).invert();
            var root=new Vec3(side*.42,-.41,-.67);var tip=new Vec3(-side*.29,-.32,-1.4);
            var path=FirstPersonConsumeTendrils.path(root,tip,p,inv,side,0,.65,1,true);
            assertEquals(root,path[0]);assertEquals(tip,path[path.length-1]);
            for(var v:path){assertTrue(v.z<-.05);assertTrue(Double.isFinite(v.x+v.y+v.z));}
        }
    }
    @Test void extendingFibreStartsAtActualPalmAndOnlyReachesSkinAfterGrowth(){
        var p=projection(70);var inv=new Matrix4f(p).invert();
        var r=new Vec3(.35,-.32,-.47);var e=new Vec3(.3,-.21,-1.8);
        var emerging=FirstPersonConsumeTendrils.path(r,e,p,inv,1,0,1,.12,true);
        assertEquals(r,emerging[0]);assertTrue(emerging[12].distanceTo(r)<emerging[12].distanceTo(e));
        var finish=FirstPersonConsumeTendrils.path(r,e,p,inv,1,0,1,1,true);
        assertEquals(e,finish[12]);
        assertEquals(0,FirstPersonConsumeTendrils.path(r,new Vec3(0,-.12,.02),p,inv,1,0,1,1,true).length);
    }
    @Test void retainedSleeveFibreHasBothEndsOnHandAfterRemoval(){
        var p=projection(70);var inv=new Matrix4f(p).invert();
        var r=new Vec3(.37,-.31,-.49);var f=new Vec3(.48,-.35,-.31);
        var path=FirstPersonConsumeTendrils.path(r,f,p,inv,1,1,2,1,false);
        assertEquals(r,path[0]);assertEquals(f,path[12]);
        assertTrue(path[6].distanceTo(r.lerp(f,.5))>.05);
    }
    @Test void retainedKnotHasVolumeAtPeakAndSinksInsideTheForearm(){
        var centre=new Vec3(.3,-.4,-.58);
        var axis=new Vec3(0,1,0);var out=new Vec3(0,0,1);var across=new Vec3(1,0,0);
        for(int i=0;i<3;i++){
            var peak=FirstPersonConsumeTendrils.residue(centre,axis,out,across,.27,1,i);
            var end=FirstPersonConsumeTendrils.residue(centre,axis,out,across,.27,.015,i);
            assertTrue(peak[4].z>centre.z+.05);
            assertTrue(FirstPersonConsumeTendrils.residue(centre,axis,out,across,.27,.17,i)[4].z>centre.z);
            assertTrue(java.util.Arrays.stream(end).allMatch(p->p.z<centre.z));
            assertTrue(peak[0].distanceTo(peak[8])>.17);
        }
    }
    @Test void crosshairClippingChecksTheWholeSegment(){
        assertEquals(0,FirstPersonConsumeTendrils.gap(new Vec3(-.7,0,0),new Vec3(.7,0,0)),1e-8);
        assertTrue(FirstPersonConsumeTendrils.gap(new Vec3(-.7,-.28,0),new Vec3(.7,-.28,0))>1.7);
    }
}
