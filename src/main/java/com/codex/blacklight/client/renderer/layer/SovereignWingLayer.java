package com.codex.blacklight.client.renderer.layer;

import com.codex.blacklight.animation.*;
import com.codex.blacklight.client.model.*;
import com.codex.blacklight.client.renderer.BlacklightBoneRendering;
import com.codex.blacklight.movement.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import java.util.Map;
import java.util.WeakHashMap;

public final class SovereignWingLayer extends RenderLayer<AbstractClientPlayer,PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation COLOR=ResourceLocation.fromNamespaceAndPath("blacklight","textures/sovereign_wings.png"),GLOW=ResourceLocation.fromNamespaceAndPath("blacklight","textures/sovereign_wings_emissive.png");
    private SovereignWingModel model;
    private static final class Blend {
        boolean enabled,airborne;double changed,airChanged;float from,airFrom;
    }
    private final Map<AbstractClientPlayer,Blend> blends=new WeakHashMap<>();
    private final Matrix4f bindInverse=new Matrix4f(BlacklightSkeleton.get().getBoundTransformFor(new BlacklightPose(),BlacklightSkeleton.get().chest)).invert();
    public SovereignWingLayer(RenderLayerParent<AbstractClientPlayer,PlayerModel<AbstractClientPlayer>> parent){super(parent);}
    private static float interpolate(float from,boolean to,double age){return interpolate(from,to,age,10);}
    private static float interpolate(float from,boolean to,double age,float ticks){float t=(float)Math.clamp(age/ticks,0,1);t=t*t*(3-2*t);return from+((to?1:0)-from)*t;}
    @Override public void render(PoseStack stack,MultiBufferSource buffer,int light,AbstractClientPlayer p,float swing,float amount,float partial,float age,float yaw,float pitch){
        if(p.isInvisible()||!(getParentModel() instanceof BlacklightPlayerModel parent)||parent.matrices()==null)return;
        boolean enabled=WingFlightSystem.equipped(p),airborne=WingFlightSystem.flying(p);double now=p.level().getGameTime()+partial;
        var b=blends.computeIfAbsent(p,k->new Blend());
        if(enabled!=b.enabled){b.from=interpolate(b.from,b.enabled,now-b.changed);b.enabled=enabled;b.changed=now;}
        if(airborne!=b.airborne){b.airFrom=interpolate(b.airFrom,b.airborne,now-b.airChanged,24);b.airborne=airborne;b.airChanged=now;}
        float deployment=interpolate(b.from,b.enabled,now-b.changed),air=interpolate(b.airFrom,b.airborne,now-b.airChanged,24);
        var transform=p.getData(com.codex.blacklight.registry.ModAttachments.WING_FLIGHT);
        if(transform.transformedAt()>0&&(enabled==transform.deployed())){
            double elapsed=now-transform.transformedAt();
            deployment=WingMorphTimeline.progress(enabled,elapsed);
            air=Math.max(air,WingMorphTimeline.unfurl(enabled,elapsed));
        }
        if(deployment<.001)return;if(model==null)model=new SovereignWingModel();
        var flight=p.getData(com.codex.blacklight.registry.ModAttachments.WING_FLIGHT);
        var wingPose=com.codex.blacklight.client.animation.WingAnimationSnapshot.sample(p,partial);
        stack.pushPose();
        BlacklightBoneRendering.skeletonFrame(stack);
        if(airborne)com.codex.blacklight.client.renderer.WingFlightEffects.captureHead(p,stack,parent.matrices(),partial);
        var chest=new Matrix4f(parent.matrices()[BlacklightSkeleton.get().chest.getId()]).mul(bindInverse);
        BlacklightBoneRendering.apply(stack,chest);
        // Body capture can pitch almost horizontal. Counter-transform the stroke AXIS,
        // not the attachment/model, so a downstroke still pushes down in that pose.
        var strokeAxis=SovereignWingMotion.strokeAxis(chest.getUnnormalizedRotation(new Quaternionf()));
        model.prepare(wingPose,strokeAxis,deployment,air,flight.foldVariant());
        model.render(stack,buffer.getBuffer(RenderType.entityCutoutNoCull(COLOR)),light);
        model.render(stack,buffer.getBuffer(RenderType.eyes(GLOW)),LightTexture.FULL_BRIGHT);
        if(airborne&&deployment>.95F)com.codex.blacklight.client.renderer.WingFlightEffects.capture(p,stack,model,partial);
        stack.popPose();
    }
}
