package com.codex.blacklight.client.animation;

import com.codex.blacklight.animation.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HammerGroundContactTest {
    @Test void ramRecoveryClearsBothEnlargedFistsWithoutMovingHipOrFeet(){
        for(float t=1.466667F;t<MotionLibrary.duration("hammer_ram");t+=1/60F){
            var pose=MotionLibrary.pose("hammer_ram",t,false);var original=pose.copy();
            HammerGroundContact.apply(pose);
            for(boolean left:new boolean[]{false,true})assertTrue(HammerGroundContact.minimum(pose,left)>=.011F,"buried fist at "+t+" left="+left+" low="+HammerGroundContact.minimum(pose,left));
            for(String name:new String[]{"Root","Thigh_R","Thigh_L","Leg_R","Leg_L"})
                assertTrue(original.orElseEmpty(name).toMatrix().equals(pose.orElseEmpty(name).toMatrix(),1e-5F),name+" must stay planted");
        }
    }
}
