package com.codex.blacklight.client.renderer;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BiomassFluidSurfaceTest {
    private static double[] heights(BiomassFluidSurface fluid) throws Exception {
        var field=BiomassFluidSurface.class.getDeclaredField("height");field.setAccessible(true);
        return ((double[])field.get(fluid)).clone();
    }
    @Test void sustainedForwardMovementKeepsChangingLocalShape() throws Exception {
        var fluid=new BiomassFluidSurface();
        for(int t=0;t<=240;t++)fluid.update(new Vec3(0,0,t*.18),t,1);
        var before=heights(fluid);
        for(int t=241;t<=249;t++)fluid.update(new Vec3(0,0,t*.18),t,1);
        var after=heights(fluid);double changed=0,total=0;
        for(int i=0;i<before.length;i++){changed+=Math.abs(after[i]-before[i]);total+=before[i];}
        assertTrue(changed/total>.15,"Constant forward speed must keep redistributing visible liquid");
    }
    @Test void movementTurningAndStoppingPreserveMaterialVolume() throws Exception {
        var fluid=new BiomassFluidSurface();double expected=-1;
        for(int t=0;t<400;t++){
            double x=t<150?0:t<250?(t-150)*.18:18;
            double z=t<150?t*.18:27;
            fluid.update(new Vec3(x,0,z),t,1);
            double volume=0;for(double h:heights(fluid)){assertTrue(Double.isFinite(h)&&h>=0);volume+=h/256;}
            if(expected<0)expected=volume;
            assertEquals(expected,volume,1e-8,"A turn or stop cannot create/delete liquid");
        }
    }
    @Test void teleportDoesNotLeaveTheOldWorldPositionInTheSurface() throws Exception {
        var moved=new BiomassFluidSurface();for(int t=0;t<100;t++)moved.update(new Vec3(t*.18,0,0),t,1);
        moved.update(new Vec3(10000,90,-10000),100,1);
        var fresh=new BiomassFluidSurface();fresh.update(new Vec3(10000,90,-10000),100,1);
        assertArrayEquals(heights(fresh),heights(moved),1e-9);
    }
    private static boolean visibleNear(BiomassFluidSurface fluid,double x,double z,double radius) throws Exception {
        double[] surface=heights(fluid);int n=(int)Math.sqrt(surface.length);
        for(int ix=0;ix<n;ix++)for(int iz=0;iz<n;iz++){
            double dx=(ix-n/2)/16D-x,dz=(iz-n/2)/16D-z;
            if(dx*dx+dz*dz<radius*radius&&surface[ix*n+iz]>=.004)return true;
        }
        return false;
    }
    @Test void steadyTravelLeavesVisibleTailBeyondBodyAndRetractsOnStop() throws Exception {
        var fluid=new BiomassFluidSurface();
        for(int t=0;t<=100;t++)fluid.update(new Vec3(0,0,t*.10),t,1);
        assertTrue(visibleNear(fluid,0,-1.3,.15),"Tail must extend visibly behind the local body");
        assertFalse(visibleNear(fluid,0,1.3,.15),"Tail belongs behind the travelled path");
        for(int t=101;t<=140;t++)fluid.update(new Vec3(0,0,10),t,1);
        assertFalse(visibleNear(fluid,0,-1.3,.15),"Stop must retract the distant tip");
    }
    @Test void cornerRetainsPreviousLegOfActualPath() throws Exception {
        var fluid=new BiomassFluidSurface();
        for(int t=0;t<=40;t++)fluid.update(new Vec3(0,0,t*.10),t,1);
        for(int t=41;t<=46;t++)fluid.update(new Vec3((t-40)*.10,0,4),t,1);
        assertTrue(visibleNear(fluid,-.6,-.65,.15),"Old leg must stay around the corner");
        assertFalse(visibleNear(fluid,-1.3,0,.15),"Tail must not rotate rigidly behind the new heading");
    }
    @Test void tailScalesWithBodyAndDoesNotSpanVerticalLedges() throws Exception {
        var small=new BiomassFluidSurface();var large=new BiomassFluidSurface();
        for(int t=0;t<=80;t++){
            small.update(new Vec3(0,0,t*.10),t,1);
            large.update(new Vec3(0,0,t*.18),t,1.8f);
        }
        assertTrue(visibleNear(large,0,-1.3,.15));
        small.update(new Vec3(0,2,8.1),81,1);
        for(int t=82;t<100;t++)small.update(new Vec3(0,2,8.1),t,1);
        assertFalse(visibleNear(small,0,-1.3,.15),"Old lower path must not float beside the player");
    }
    @Test void highFrameRateSlowTravelStillKeepsTailExtended() throws Exception {
        var fluid=new BiomassFluidSurface();
        for(int frame=0;frame<=1200;frame++){
            double tick=frame/20D;
            fluid.update(new Vec3(0,0,tick*.03),tick,1);
        }
        assertTrue(visibleNear(fluid,0,-.7,.15),"Small per-frame motion must not be treated as stopping");
    }
}
