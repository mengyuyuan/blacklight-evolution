package com.codex.blacklight.infection;

import com.codex.blacklight.client.model.InfectedModelPose;
import com.mojang.blaze3d.vertex.PoseStack;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InfectedFlightPoseTest{
    private org.joml.Matrix4f wing(String species,float age){var stack=new PoseStack();InfectedModelPose.transform(species,"wing_-1",age,0,0,0,0,0,true,stack);return new org.joml.Matrix4f(stack.last().pose());}
    @Test void batAndPhantomWingsKeepMovingEvenWhenTheirWalkingClockIsZero(){
        for(String species:new String[]{"bat","phantom"}){
            var earlier=wing(species,2);var later=wing(species,7);
            assertFalse(earlier.equals(later,.0001F),species+" must flap while hovering, independently of ground stride");
            wing("bee",14);assertTrue(earlier.equals(wing(species,2),.0001F),"shared model cannot retain another creature's last frame");
        }
    }
}
