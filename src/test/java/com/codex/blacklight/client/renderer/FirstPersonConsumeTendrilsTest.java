package com.codex.blacklight.client.renderer;
import com.codex.blacklight.combat.ConsumeGiverEffect;
import com.codex.blacklight.combat.ConsumeSequence;
import com.codex.blacklight.combat.ConsumeVisual;
import com.codex.blacklight.power.PowerForm;
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
    @Test void knotsTravelDownTheActualSurfaceFromHandTowardsElbow(){
        var elbow=new Vec3(.3,-.40,-.58);
        var wrist=elbow.add(0,.27,0);
        var out=new Vec3(0,0,1);
        for(boolean left:new boolean[]{false,true})for(int strand=0;strand<3;strand++){
            var across=new Vec3(left?-1:1,0,0);
            var peak=FirstPersonConsumeTendrils.residue(elbow,wrist,out,across,1,
                    FirstPersonConsumeTendrils.armGrowth(left,ConsumeGiverEffect.REVERSE_SECONDS),strand);
            var falling=FirstPersonConsumeTendrils.residue(elbow,wrist,out,across,1,
                    FirstPersonConsumeTendrils.armGrowth(left,3.06F),strand);
            var end=FirstPersonConsumeTendrils.residue(elbow,wrist,out,across,1,
                    FirstPersonConsumeTendrils.armGrowth(left,4.60F),strand);
            assertTrue(peak.points()[4].z>elbow.z+.05);
            assertTrue(peak.points()[8].distanceTo(peak.points()[0])>.16);
            assertTrue(peak.radii()[8]>.002,"the back of the hand must initially be covered");
            assertEquals(0,falling.radii()[8],.0001,"the far end has already cleared");
            assertTrue(falling.radii()[0]>.004,"a proximal fold survives after the hand clears");
            assertTrue(centreOfVolume(peak)-centreOfVolume(falling)>.045,"visible tissue should actually travel towards the elbow");
            for(var point:falling.points())assertTrue(point.y>=elbow.y&&point.y<=wrist.y);
            assertTrue(java.util.Arrays.stream(end.radii()).allMatch(r->r==0));
            assertTrue(java.util.Arrays.stream(end.points()).allMatch(point->point.z<elbow.z));
        }
    }
    private static double centreOfVolume(FirstPersonConsumeTendrils.Fold fold){
        double moment=0,total=0;
        for(int i=0;i<fold.radii().length;i++){
            double area=fold.radii()[i]*fold.radii()[i];
            total+=area;
            moment+=area*fold.points()[i].y;
        }
        return moment/total;
    }
    @Test void gatheringLiftsTissueBeforeAnyPullTowardsTheElbow(){
        assertEquals(0,FirstPersonConsumeTendrils.armGrowth(true,1.9F).pull());
        assertEquals(0,FirstPersonConsumeTendrils.armGrowth(false,ConsumeGiverEffect.REVERSE_SECONDS).pull());
        assertTrue(FirstPersonConsumeTendrils.armGrowth(false,2.75F).pull()>.4);
    }
    @Test void newCaptureRetainsTheCompletedVictimsWithdrawalClock(){
        int owner=17;
        var prior=new ConsumeVisual(owner,100,60,PowerForm.NONE.ordinal(),ConsumeVisual.MOBILE_CONSUME);
        int complete=ConsumeSequence.completionTick(PowerForm.NONE,false,prior.variant());
        long now=100+complete+36;
        var next=new ConsumeVisual(owner,now-1,60,PowerForm.NONE.ordinal(),ConsumeVisual.CONSUME);
        var scene=FirstPersonConsumeTendrils.scene(owner,next,prior,now,.5F);
        assertNotNull(scene);
        assertEquals(prior,scene.residueSource());
        assertEquals(next,scene.visual());
        assertEquals(0,scene.envelope());
        assertEquals(ConsumeGiverEffect.residueAge(prior,prior.elapsed(now,.5F)/20F),scene.residueAge());
        assertTrue(scene.residueAge()>ConsumeGiverEffect.REVERSE_SECONDS);
        assertNull(FirstPersonConsumeTendrils.scene(owner+1,next,prior,now,.5F));
    }
    @Test void crosshairClippingChecksTheWholeSegment(){
        assertEquals(0,FirstPersonConsumeTendrils.gap(new Vec3(-.7,0,0),new Vec3(.7,0,0)),1e-8);
        assertTrue(FirstPersonConsumeTendrils.gap(new Vec3(-.7,-.28,0),new Vec3(.7,-.28,0))>1.7);
    }
}
