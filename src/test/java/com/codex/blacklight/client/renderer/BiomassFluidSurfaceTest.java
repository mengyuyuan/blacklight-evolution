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
}
