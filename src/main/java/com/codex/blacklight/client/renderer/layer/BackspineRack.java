package com.codex.blacklight.client.renderer.layer;
import com.codex.blacklight.animation.*;
import com.codex.blacklight.client.renderer.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;

public final class BackspineRack {
    public static void firstPerson(AbstractClientPlayer player,PoseStack stack,MultiBufferSource buffers,int light,float partial,WeaponMorphTracker.Sample morph){
        if(player.isInvisible()||morph.shoulder()<=.001F)return;
        var clock=RoofPerformance.clock(player,partial);
        for(int side=0;side<2;side++){
            boolean right=(side==0)!=RooftopRig.reflect(clock.key());
            stack.pushPose();
            stack.translate(right?.56:-.56,-.31,-.59);
            stack.mulPose(new org.joml.Quaternionf().rotationY((float)Math.PI));
            stack.scale(.48F,.48F,.48F);
            stack.translate(0,-1.48,0);
            RoofPerformance.rack(clock,stack,buffers,light,null,morph.shoulder(),side);
            stack.popPose();
        }
    }
    public void render(AbstractClientPlayer player,PoseStack stack,MultiBufferSource buffers,int light,Matrix4f[] poses,float partial){
        if(player.isInvisible())return;
        var morph=WeaponMorphTracker.sample(player,partial);
        if(morph.shoulder()<=.001F)return;
        var c=RoofPerformance.clock(player,partial);
        var chest=ScapulaMount.chest(poses);
        for(int side=0;side<2;side++){
            boolean right=(side==0)!=RooftopRig.reflect(c.key());
            float receive=QuillReception.weight(player,partial,right);
            var socket=new Matrix4f(chest).translate(right?.28F:-.28F,.90F,0)
                    .rotateZ((right?1:-1)*receive*.16F).rotateX(-receive*.12F)
                    .translate(right?-.28F:.28F,-.90F,0);
            RoofPerformance.rack(c,stack,buffers,light,socket,morph.shoulder(),side);
        }
    }
}
