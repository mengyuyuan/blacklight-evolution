package com.codex.blacklight.infection;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class TissueMotionTest {
    @Test void motionUsesAbsoluteTimeAndDoesNotDependOnFrameHistory() {
        var expected=TissueMotion.sample(171,4567.125,1,.35F,10);
        for(double time=0;time<300;time+=.137)TissueMotion.sample(171,time,1,0,-1);
        assertEquals(expected,TissueMotion.sample(171,4567.125,1,.35F,10));
        assertNotEquals(expected,TissueMotion.sample(172,4567.125,1,.35F,10));
        assertEquals(new TissueMotion.State(0,0,0,0,0),TissueMotion.sample(171,10,0,1,12));
    }
    @Test void windupContactAndRecoveryHaveNoPoseDiscontinuity() {
        for(int boundary:new int[]{0,TissueOrgans.WINDUP,TissueOrgans.HIT,TissueOrgans.DURATION}) {
            var before=TissueMotion.sample(171,300,1,0,boundary-.0001F);
            var after=TissueMotion.sample(171,300,1,0,boundary+.0001F);
            assertEquals(before.jaw(),after.jaw(),.0001F);
            assertEquals(before.split(),after.split(),.0001F);
            assertEquals(before.hook(),after.hook(),.0001F);
            assertEquals(before.toe(),after.toe(),.0001F);
            assertEquals(before.pulse(),after.pulse(),.0001F);
        }
        var idle=TissueMotion.sample(171,300,1,0,-1);
        var contact=TissueMotion.sample(171,300,1,0,TissueOrgans.HIT);
        assertTrue(contact.jaw()>idle.jaw()+.2F);
        assertEquals(idle,TissueMotion.sample(171,300,1,0,TissueOrgans.DURATION));
    }
    @Test void pulseKeepsBothSkinSocketsAndTheGlandRootFixed() {
        float[] original=new float[4*8*3];
        for(int r=0;r<4;r++)for(int s=0;s<8;s++) {
            int i=(r*8+s)*3;
            original[i]=5+(float)Math.cos(s*Math.PI*.25)*2;
            original[i+1]=r*4;
            original[i+2]=-3+(float)Math.sin(s*Math.PI*.25)*3;
        }
        float[] skin=original.clone();TissueMotion.flex(skin,8,.025F,true);
        assertArrayEquals(Arrays.copyOfRange(original,0,24),Arrays.copyOfRange(skin,0,24));
        assertArrayEquals(Arrays.copyOfRange(original,72,96),Arrays.copyOfRange(skin,72,96));
        assertFalse(Arrays.equals(original,skin));
        for(int ring=0;ring<4;ring++)for(int axis=0;axis<3;axis++) {
            float a=0,b=0;for(int s=0;s<8;s++){a+=original[(ring*8+s)*3+axis];b+=skin[(ring*8+s)*3+axis];}
            assertEquals(a,b,.0001F,"breathing cannot translate a cross-section");
        }
        float[] gland=Arrays.copyOf(original,48),before=gland.clone();TissueMotion.flex(gland,8,.025F,false);
        assertArrayEquals(Arrays.copyOf(before,24),Arrays.copyOf(gland,24));
        assertFalse(Arrays.equals(before,gland),"the gland lip may expand around its fixed outlet centre");
    }
}
