package com.codex.blacklight.client.renderer;

import com.codex.blacklight.animation.BlacklightPose;
import com.codex.blacklight.animation.PlayerMotionController;
import com.codex.blacklight.client.model.ConsumeBodySurface;
import com.codex.blacklight.client.model.MercerPlayerMesh;
import com.codex.blacklight.client.model.MercerRig;
import com.codex.blacklight.combat.ConsumeGiverEffect;
import com.codex.blacklight.combat.ConsumeVisual;
import com.codex.blacklight.registry.ModAttachments;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;

/** Giver/cyclic -> AlexBody -> EffectConsume (FIG 549912). */
public final class MobileConsumeWrap {
    private MobileConsumeWrap() {}
    private static final class NativeSkin {
        static final Matrix4f[] inverse=PlayerMotionController.skeleton().getPoseMatrices(new BlacklightPose());
        static final Matrix4f[] posed=new Matrix4f[inverse.length];
        static final ConsumeBodySurface regular=ConsumeBodySurface.player(false), slim=ConsumeBodySurface.player(true);
        static { for(int i=0;i<inverse.length;i++){inverse[i].invert();posed[i]=new Matrix4f();} }
    }
    public static void render(AbstractClientPlayer player,PoseStack stack,MultiBufferSource buffers,int light,
                              Matrix4f[] poses,float partial,MercerRig.Frame frame){
        ConsumeExteriorWrap.capture(player,stack,poses,partial);
        draw(player,stack,buffers,light,poses,partial,frame,-2);
    }
    public static void renderArm(AbstractClientPlayer player,PoseStack stack,MultiBufferSource buffers,int light,
                                Matrix4f[] poses,float partial,int side){
        draw(player,stack,buffers,light,poses,partial,null,side);
    }
    private static void draw(AbstractClientPlayer player,PoseStack stack,MultiBufferSource buffers,int light,
                             Matrix4f[] poses,float partial,MercerRig.Frame frame,int side){
        ConsumeVisual scene=ModAttachments.getData(player,ModAttachments.CONSUME_BODY_VISUAL);
        if(!scene.active() || !player.isAlive() || player.isInvisible() || player.isSpectator())return;
        long now=player.level().getGameTime();
        float seconds=scene.elapsed(now,partial)/20F-ConsumeGiverEffect.cue(scene);
        float reach=ConsumeGiverEffect.reach(seconds);
        if(reach<=0)return;
        // The source fragment program scrolls a tiled mesh UV using global time.
        float textureClock=(Math.floorMod(now,10000L)+partial)/20F;
        var out=buffers.getBuffer(ConsumeWeaveMaterial.mesh());
        if(frame!=null){
            var mesh=MercerPlayerMesh.get();
            if(mesh!=null){mesh.consumeSurface().draw(stack,out,light,frame.skin(),side,reach,seconds,textureClock);return;}
        }
        for(int i=0;i<poses.length;i++)NativeSkin.posed[i].set(poses[i]).mul(NativeSkin.inverse[i]);
        boolean slim="slim".equals(player.getModelName());
        (slim?NativeSkin.slim:NativeSkin.regular).draw(stack,out,light,NativeSkin.posed,side,reach,seconds,textureClock);
    }
}
