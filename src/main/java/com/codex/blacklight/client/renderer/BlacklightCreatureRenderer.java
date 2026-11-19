package com.codex.blacklight.client.renderer;
import com.codex.blacklight.combat.GoliathLockPart;
import com.codex.blacklight.client.ClientTargetLock;
import com.codex.blacklight.client.model.BlacklightCreatureModel;
import com.codex.blacklight.client.animation.ConsumeVictimPose;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import com.mojang.blaze3d.vertex.PoseStack;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import java.util.function.Function;
public final class BlacklightCreatureRenderer<T extends LivingEntity & GeoEntity> extends AnatomicalGeoRenderer<T> {
    private GoliathLockPart highlightedPart;
    private float greenePartial;
    private float greeneFlash(software.bernie.geckolib.cache.object.GeoBone bone){
        if(!(getAnimatable() instanceof com.codex.blacklight.entity.EncounterMob mob)||mob.kind()!=com.codex.blacklight.entity.EncounterKind.ELIZABETH_GREENE)return 0;
        for(var b=bone;b!=null;b=b.getParent())if(b.getName().startsWith("weak_"))return mob.greeneFlash(b.getName().charAt(5)-'0',greenePartial);
        return mob.greeneFlash(3,greenePartial);
    }
    @Override protected void applyRotations(T entity,PoseStack stack,float age,float yaw,float partial){
        if(entity instanceof com.codex.blacklight.entity.EncounterMob mob
                &&mob.kind()==com.codex.blacklight.entity.EncounterKind.ELIZABETH_GREENE && mob.greeneBattle().exposed())
            yaw=mob.skinYaw(partial);
        super.applyRotations(entity,stack,age,yaw,partial);
    }
    @Override protected float getDeathMaxRotation(T entity){
        if(entity instanceof com.codex.blacklight.entity.EncounterMob mob
                &&mob.kind()==com.codex.blacklight.entity.EncounterKind.GOLIATH)return 0; // The source pose already supplies the fall.
        if(entity instanceof com.codex.blacklight.entity.BlacklightMutant mutant&&mutant.kind()==com.codex.blacklight.entity.BlacklightMutant.Kind.BRUTE&&AdaptiveRagdoll.active(entity))return 0;
        return super.getDeathMaxRotation(entity);
    }
    public BlacklightCreatureRenderer(EntityRendererProvider.Context context,Function<T,String> asset){super(context,new BlacklightCreatureModel<>(asset));shadowRadius=.65F;}
    @Override public void renderCubesOfBone(PoseStack stack,software.bernie.geckolib.cache.object.GeoBone bone,com.mojang.blaze3d.vertex.VertexConsumer out,int light,int overlay,float red,float green,float blue,float alpha){
        if(highlightedPart!=null && GoliathLockPart.forSkin(bone.getName())==highlightedPart){
            light=LightTexture.FULL_BRIGHT;
            overlay=net.minecraft.client.renderer.texture.OverlayTexture.pack(0,net.minecraft.client.renderer.texture.OverlayTexture.v(true));
        }
        float pulse=greeneFlash(bone);
        if(pulse>0){overlay=net.minecraft.client.renderer.texture.OverlayTexture.pack(net.minecraft.client.renderer.texture.OverlayTexture.u(pulse*.55F),net.minecraft.client.renderer.texture.OverlayTexture.v(true));light=LightTexture.FULL_BRIGHT;}
        super.renderCubesOfBone(stack,bone,out,light,overlay,red,green,blue,alpha);
    }
    @Override public void render(T entity,float yaw,float partial,PoseStack stack,MultiBufferSource buffer,int light){
        if(entity instanceof com.codex.blacklight.entity.EncounterMob greene && greene.plazaTruck().isPresent()){
            var path=com.codex.blacklight.combat.PlazaLash.points(greene,partial);
            if(path.length>1){
                var origin=greene.getPosition(partial);
                for(int i=0;i<path.length;i++) path[i]=path[i].subtract(origin);
                BiomassGeometry.strand(stack,buffer.getBuffer(RenderType.entityCutoutNoCull(BiomassGeometry.TEXTURE)),light,path,.38,.09);
            }
        }
        var visual=ModAttachments.getData(entity,ModAttachments.CONSUME_VISUAL);float progress=visual.progress(entity.level().getGameTime(),partial);
        var placement=com.codex.blacklight.client.ClientCarryPlacement.consumeOffset(entity,partial);
        stack.pushPose();if(visual.active())stack.translate(placement.x,placement.y,placement.z);
        if(!visual.active()&&entity instanceof com.codex.blacklight.entity.EncounterMob flyer&&flyer.kind()==com.codex.blacklight.entity.EncounterKind.FLYER&&com.codex.blacklight.combat.FlyerPhysics.hasPose(flyer)){
            var offset=com.codex.blacklight.combat.FlyerPhysics.origin(flyer,partial).subtract(flyer.getPosition(partial));stack.translate(offset.x,offset.y,offset.z);
        }
        if(entity instanceof com.codex.blacklight.entity.BlacklightHive hive){float g=.45F+.55F*hive.growth();stack.scale(g,g,g);}
        if(entity instanceof com.codex.blacklight.entity.EncounterMob greene&&greene.kind()==com.codex.blacklight.entity.EncounterKind.ELIZABETH_GREENE){
            float s=com.codex.blacklight.combat.GreenePresentation.SCALE;stack.translate(0,.01*(1-s),0);stack.scale(s,s,s);
        }else if(entity instanceof com.codex.blacklight.entity.EncounterMob goliath&&goliath.kind()==com.codex.blacklight.entity.EncounterKind.GOLIATH){
            float s=com.codex.blacklight.combat.GoliathPresentation.SCALE;
            stack.translate(0,.01*(1-s),0);stack.scale(s,s,s);
        }
        greenePartial=partial;
        ConsumeFracture fragments=ConsumeFracture.visible(entity,partial)?new ConsumeFracture(entity,partial,stack,buffer,ConsumeAbsorptionFx.destination(entity,partial,placement)):null;
        if(visual.active())ConsumeVictimPose.body(entity,stack,partial);
        else if(!(entity instanceof com.codex.blacklight.entity.BlacklightHive)&&!(entity instanceof com.codex.blacklight.entity.EncounterMob mob&&mob.sourcePoseCommitted())&&!com.codex.blacklight.client.animation.NativeReceiverPose.available(entity,partial))com.codex.blacklight.client.animation.HitBodyPose.apply(entity,stack,partial);
        var previousPart=highlightedPart;
        highlightedPart=entity instanceof com.codex.blacklight.entity.EncounterMob mob
                &&mob.kind()==com.codex.blacklight.entity.EncounterKind.GOLIATH &&ClientTargetLock.entityTarget()==mob
                ? ClientTargetLock.selection(mob).goliathPart() : null;
        try{if(progress<.995F)super.render(entity,yaw,partial,stack,fragments==null?buffer:fragments,light);if(fragments!=null)fragments.finish();}finally{highlightedPart=previousPart;stack.popPose();}
        var owner=entity.level().getEntity(visual.consumerId());
        if(owner!=null&&visual.active()){
            var delta=owner.getPosition(partial).subtract(entity.getPosition(partial));
            var out=buffer.getBuffer(RenderType.entityCutoutNoCull(ConsumedMobVisuals.WHITE));
            ConsumedMobVisuals.whip(entity,stack,out,light,delta,ConsumedMobVisuals.tetherProgress(entity,partial));
        }
    }
}
