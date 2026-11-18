package com.codex.blacklight.combat;

import java.util.Arrays;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Deferred until the user's shared validation session; no client is needed. */
class GreeneMouthTest {
    private static float[][] neutralPose(){
        var pose=new float[GreeneMotion.table().bones.length][9];
        for(var row:pose)Arrays.fill(row,6,9,1);
        return pose;
    }
    private static int mouth(){return Arrays.asList(GreeneMotion.table().bones).indexOf("native_Mouth");}
    private static void near(Vec3 expected,Vec3 actual){assertTrue(expected.distanceTo(actual)<.000002,expected+" != "+actual);}

    @Test void sourceEulerUsesNegatedThirdRowAndCurrentNativeAxes(){
        var pose=neutralPose();
        near(new Vec3(0,0,-1),GreeneMouth.direction(pose,180,new float[]{0,0,0}));
        near(new Vec3(-1,0,0),GreeneMouth.direction(pose,180,new float[]{90,0,90}));
        near(new Vec3(0,1,0),GreeneMouth.direction(pose,180,new float[]{90,0,0}));
        near(new Vec3(0,0,1),GreeneMouth.direction(pose,0,new float[]{0,0,0}));
        near(new Vec3(-1,0,0),GreeneMouth.direction(pose,90,new float[]{0,0,0}));
    }
    @Test void attachedWeaponDiscardsScaleAndShearInSourceAxisOrder(){
        var pose=neutralPose();int mouth=mouth(),parent=GreeneMotion.table().parents[mouth];
        // Parent scale followed by a 45-degree mouth turn gives X=(2,1,0),
        // old Y=(-2,1,0), up to a common sqrt(2). P1 preserves X, rebuilds Y.
        pose[parent][6]=2;pose[mouth][2]=(float)(Math.PI/4);
        near(new Vec3(-2,-1,0).normalize(),GreeneMouth.direction(pose,180,new float[]{90,0,90}));
        near(new Vec3(-1/Math.sqrt(10),2/Math.sqrt(10),-1/Math.sqrt(2)),
            GreeneMouth.direction(pose,180,new float[]{45,0,0}));
    }
    @Test void socketTranslationKeepsFullScaleWithoutChangingDirection(){
        var pose=neutralPose();pose[mouth()][7]=3;
        var origin=new Vec3(10,20,30);
        var a=GreeneMouth.position(pose,origin,180,1);var b=GreeneMouth.position(pose,origin,180,2);
        assertTrue(b.y-a.y>1.8,"original Mouth socket offset inherits nonuniform scale");
        Vec3 before=GreeneMouth.direction(pose,180,new float[]{45,0,0});
        pose[mouth()][3]=300;pose[mouth()][4]=-700;pose[mouth()][5]=200;
        near(before,GreeneMouth.direction(pose,180,new float[]{45,0,0}));
        near(new Vec3(0,1,-1).normalize(),before);
    }
    @Test void malformedShotAnglesAndCollapsedBasisCannotBecomeSilentNanShots(){
        var pose=neutralPose();
        assertThrows(IllegalArgumentException.class,()->GreeneMouth.direction(pose,0,new float[]{Float.NaN,0,0}));
        pose[mouth()][6]=0;
        assertThrows(IllegalStateException.class,()->GreeneMouth.direction(pose,0,new float[]{0,0,0}));
    }
}
