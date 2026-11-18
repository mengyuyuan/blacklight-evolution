package com.codex.blacklight.combat;

import java.util.Random;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SourceBossMotionBlendTest {
    private static float[] row(float x,float y,float z){return new float[]{x,y,z,0,0,0,1,1,1};}
    private static void sameRotation(float[] a,float[] b,float weight,boolean checkRuntime){
        var q=new Quaternionf().rotationZYX(a[2],a[1],a[0])
                .slerp(new Quaternionf().rotationZYX(b[2],b[1],b[0]),weight).normalize();
        var result=SourceBossMotion.blend(a,b,weight);
        // Independent double matrices avoid JOML's direct-Euler cosFromSin
        // cancellation. Matrix3f.rotationZYX is not used by the game pose path.
        double norm=Math.sqrt((double)q.x*q.x+(double)q.y*q.y+(double)q.z*q.z+(double)q.w*q.w);
        double x=q.x/norm,y=q.y/norm,z=q.z/norm,w=q.w/norm;
        double[][] expected={{1-2*(y*y+z*z),2*(x*y-z*w),2*(x*z+y*w)},
                {2*(x*y+z*w),1-2*(x*x+z*z),2*(y*z-x*w)},
                {2*(x*z-y*w),2*(y*z+x*w),1-2*(x*x+y*y)}};
        double cx=Math.cos(result[0]),sx=Math.sin(result[0]),cy=Math.cos(result[1]),sy=Math.sin(result[1]),cz=Math.cos(result[2]),sz=Math.sin(result[2]);
        double[][] actual={{cz*cy,cz*sy*sx-sz*cx,cz*sy*cx+sz*sx},
                {sz*cy,sz*sy*sx+cz*cx,sz*sy*cx-cz*sx},{-sy,cy*sx,cy*cx}};
        var runtime=new Matrix4f().rotate(new Quaternionf().rotationZYX(result[2],result[1],result[0]));
        // Compare rotations rather than Euler components: the representation
        // switches branches across the pole but must not change the mesh pose.
        for(int r=0;r<3;r++)for(int c=0;c<3;c++){
            assertEquals(expected[r][c],actual[r][c],.000004,
                    "slerp rotation changed at weight="+weight+" matrix="+r+","+c);
            if(checkRuntime)assertEquals(expected[r][c],runtime.get(c,r),.000004,
                    "game quaternion/Matrix4f pose changed at weight="+weight+" matrix="+r+","+c);
        }
    }
    @Test void singularConstantBindKeepsItsActualTwist(){
        var waist=row(-.89605538F,-1.57079633F,0); // Actual exported Spine_1 inverse-bind leaf.
        sameRotation(waist,waist,0,true);sameRotation(waist,waist,.5F,true);sameRotation(waist,waist,1,true);
        for(float sign:new float[]{-1,1}){
            var arbitrary=row(.83F,sign*(float)(Math.PI/2),-1.24F);
            sameRotation(arbitrary,arbitrary,.3F,true);
        }
    }
    @Test void distinctAdjacentFramesCrossBothPolesWithoutChangingSlerp(){
        for(float sign:new float[]{-1,1})for(float width:new float[]{.12F,.000001F}){
            var a=row(-.89605538F,sign*(float)(Math.PI/2)-width,.42F);
            var b=row(-.89605538F,sign*(float)(Math.PI/2)+width,.42F);
            for(float weight:new float[]{0,.01F,.25F,.49F,.5F,.51F,.75F,.99F,1})sameRotation(a,b,weight,true);
            // Unequal X/Z as well as Y rules out an identical-row shortcut.
            b[0]+=.14F;b[2]-=.09F;
            for(float weight:new float[]{.25F,.5F,.75F})sameRotation(a,b,weight,true);
        }
    }
    @Test void ordinaryRotationInterpolationAndTranslationScaleChannelsArePreserved(){
        var random=new Random(0x5A17);
        for(int i=0;i<1000;i++){
            var a=row((random.nextFloat()*2-1)*3.14F,(random.nextFloat()*2-1)*3.14F,(random.nextFloat()*2-1)*3.14F);
            var b=row((random.nextFloat()*2-1)*3.14F,(random.nextFloat()*2-1)*3.14F,(random.nextFloat()*2-1)*3.14F);
            // This broad test checks the Euler extraction independently of
            // the engine's existing float trig approximation; the source-bind
            // singularity tests above additionally cover the actual game path.
            sameRotation(a,b,random.nextFloat(),false);
        }
        var result=SourceBossMotion.blend(new float[]{0,0,0,2,4,6},new float[]{0,0,0,6,8,10,2,3,4},.25F);
        assertArrayEquals(new float[]{0,0,0,3,5,7,1.25F,1.5F,1.75F},result,.000001F);
    }
}
