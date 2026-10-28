package com.codex.blacklight.client.renderer;

import com.codex.blacklight.entity.BlacklightInfectedHost;
import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.client.model.InfectedSpeciesMesh;
import com.codex.blacklight.client.model.InfectedModelPose;
import com.codex.blacklight.client.animation.ConsumeVictimPose;
import com.codex.blacklight.client.animation.HitBodyPose;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;

/** Native species skins with approved infection geometry, plus distinct colossal meshes. */
public final class InfectedHostRenderer extends EntityRenderer<BlacklightInfectedHost> {
    public InfectedHostRenderer(EntityRendererProvider.Context context){super(context);shadowRadius=.6F;}
    @Override public ResourceLocation getTextureLocation(BlacklightInfectedHost entity){return ConsumedMobVisuals.WHITE;}
    @Override @SuppressWarnings({"rawtypes","unchecked"})
    public void render(BlacklightInfectedHost entity,float yaw,float partial,PoseStack stack,MultiBufferSource buffers,int light){
        DevourerFeedingFx.render(entity,partial,stack,buffers,light);
        TissueCompetitionFx.render(entity,partial,stack,buffers,light);
        TissueVascularFx.render(entity,partial,stack,buffers,light);
        EvolvedBodyRenderer.cocoon(entity,partial,stack,buffers,light);
        InfectionCombatVisuals.host(entity,partial,stack,buffers,light);
        if(entity.cannibalPrey()&&TissueCompetitionFx.active(entity))buffers=new CannibalCoverBuffers(buffers,entity,stack,partial);
        if(entity.isEvolved()){EvolvedBodyRenderer.render(entity,partial,stack,buffers,light);super.render(entity,yaw,partial,stack,buffers,light);return;}
        var mesh=authoredMesh(entity);
        if(mesh!=null){renderAuthored(entity,mesh,yaw,partial,stack,buffers,light);return;}
        var proxy=entity.appearance();if(proxy==null)return;
        proxy.setId(entity.getId());proxy.copyPosition(entity);
        proxy.xo=entity.xo;proxy.yo=entity.yo;proxy.zo=entity.zo;
        proxy.xOld=entity.xOld;proxy.yOld=entity.yOld;proxy.zOld=entity.zOld;
        proxy.yBodyRot=entity.yBodyRot;proxy.yBodyRotO=entity.yBodyRotO;
        proxy.yHeadRot=entity.yHeadRot;proxy.yHeadRotO=entity.yHeadRotO;
        proxy.xRotO=entity.xRotO;proxy.yRotO=entity.yRotO;
        proxy.tickCount=entity.tickCount;proxy.hurtTime=entity.hurtTime;proxy.deathTime=entity.deathTime;
        proxy.setOnGround(entity.onGround());proxy.setDeltaMovement(entity.getDeltaMovement());
        proxy.attackAnim=entity.attackAnim;proxy.oAttackAnim=entity.oAttackAnim;
        proxy.swinging=entity.swinging;proxy.setInvisible(entity.isInvisible());
        // Do not advance animation per render frame: interpolation is sampled from the host.
        entity.advanceAppearanceAnimation();
        // Native tick advances old positions/rotations too. Restore the host interpolation
        // after that tick so legs and wings cannot snap against a smoothly moving body.
        proxy.xo=entity.xo;proxy.yo=entity.yo;proxy.zo=entity.zo;
        proxy.xOld=entity.xOld;proxy.yOld=entity.yOld;proxy.zOld=entity.zOld;
        proxy.yBodyRot=entity.yBodyRot;proxy.yBodyRotO=entity.yBodyRotO;
        proxy.yHeadRot=entity.yHeadRot;proxy.yHeadRotO=entity.yHeadRotO;
        proxy.xRotO=entity.xRotO;proxy.yRotO=entity.yRotO;
        if(entity.organActionCore()==com.codex.blacklight.infection.TissueCore.HORN){
            float load=com.codex.blacklight.infection.TissueOrganCycle.cycle(entity.organActionCore()).effort(entity.organActionElapsed(partial));
            proxy.setXRot(entity.getXRot()+32*load);proxy.xRotO=entity.xRotO+32*load;
        }
        proxy.setHealth(Math.max(1,proxy.getMaxHealth()*entity.getHealth()/entity.getMaxHealth()));
        proxy.setData(ModAttachments.CONSUME_VISUAL,entity.getData(ModAttachments.CONSUME_VISUAL));
        proxy.setData(ModAttachments.HIT_REACTION,entity.getData(ModAttachments.HIT_REACTION));
        for(var slot:EquipmentSlot.values())proxy.setItemSlot(slot,entity.getItemBySlot(slot));
        proxy.setCustomName(null);
        EntityRenderer renderer=Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(proxy);
        var skin=new InfectedSkinBuffers(buffers);
        var worldRelativePose=new org.joml.Matrix4f(stack.last().pose());
        stack.pushPose();DevourerFeedingFx.applyVictim(entity,partial,stack);
        TissueCompetitionFx.apply(entity,partial,stack);
        stack.translate(0,entity.tissueStanceOffset(),0);
        float developedScale=1.12F*entity.tissueBodyScale();var frame=entity.tissueFrame();stack.scale(developedScale*frame.x,developedScale*frame.y,developedScale*frame.z);
        if(entity.isMetamorphosing()){float reclaim=com.codex.blacklight.infection.EvolvedForm.oldBody(entity.evolvedProgress(partial));stack.scale(reclaim,reclaim,reclaim);}
        try{
            if(renderer instanceof net.minecraft.client.renderer.entity.LivingEntityRenderer living){
                try(var morphology=new InfectedMorphology(entity,proxy,living.getModel(),renderer.getTextureLocation(proxy),stack.last(),partial,worldRelativePose)){
                    renderer.render(proxy,yaw,partial,stack,skin,light);
                }
            }else renderer.render(proxy,yaw,partial,stack,skin,light);
        }finally{stack.popPose();}
        super.render(entity,yaw,partial,stack,buffers,light);
    }

    @Override public boolean shouldRender(BlacklightInfectedHost entity,Frustum camera,double x,double y,double z){
        if(entity.hasVascularVisual()){var vascular=entity.vascularVisual();if(entity.shouldRender(x,y,z)&&camera.isVisible(entity.getBoundingBox().minmax(new net.minecraft.world.phys.AABB(net.minecraft.core.BlockPos.of(vascular.getLong("Target")))).inflate(.3)))return true;}
        if(entity.devourerIdentity()&&!entity.giantBoss()&&entity.nurseryData().contains("Home")&&entity.shouldRender(x,y,z)
                &&camera.isVisible(new net.minecraft.world.phys.AABB(entity.nurseryHome()).inflate(42)))return true;
        if(entity.feedingStarted()>=0&&entity.level().getEntity(entity.feedingTargetId()) instanceof net.minecraft.world.entity.LivingEntity prey
                &&prey.isAlive()&&prey.distanceToSqr(entity)<=48*48
                &&camera.isVisible(entity.getBoundingBox().minmax(prey.getBoundingBox()).inflate(3)))return true;
        var mesh=authoredMesh(entity);
        if(mesh==null)return entity.shouldRender(x,y,z)&&(entity.noCulling||camera.isVisible(entity.getBoundingBox().inflate((entity.tissueInheritance().sources().isEmpty()?.65:3)+entity.tissueOrganReach())));
        double scale=scale(entity,mesh);
        var bounds=mesh.bounds();
        double radius=Math.hypot(Math.max(Math.abs(bounds.minX),Math.abs(bounds.maxX)),
                Math.max(Math.abs(bounds.minZ),Math.abs(bounds.maxZ)))*scale+1;
        double motionMargin=Math.max(1,radius*.25);
        var visualBounds=new net.minecraft.world.phys.AABB(entity.getX()-radius,entity.getY()-motionMargin,entity.getZ()-radius,
                entity.getX()+radius,entity.getY()+bounds.maxY*scale+motionMargin,entity.getZ()+radius);
        return entity.shouldRender(x,y,z)&&(entity.noCulling||camera.isVisible(visualBounds));
    }

    private static InfectedSpeciesMesh authoredMesh(BlacklightInfectedHost host){
        // Ordinary hosts combine native skins with their approved species geometry.
        // Growing/mature colossal encounters keep the distinct boss body.
        String species=host.speciesId();
        return host.hasMultipartBody()&&(host.giantBoss()||host.evolutionTicks()>0||host.devourerIdentity()
                ||species.equals("minecraft:wither")||species.equals("minecraft:ender_dragon"))?InfectedSpeciesMesh.get(species):null;
    }

    private static float scale(BlacklightInfectedHost host,InfectedSpeciesMesh mesh){
        return mesh.boss()?host.bodyScale():1.12F*mesh.referenceScale()*host.visualScale();
    }

    private void renderAuthored(BlacklightInfectedHost entity,InfectedSpeciesMesh mesh,float yaw,float partial,
            PoseStack stack,MultiBufferSource buffers,int light){
        var mc=Minecraft.getInstance();
        boolean hidden=entity.isInvisible();
        boolean translucent=hidden&&mc.player!=null&&!entity.isInvisibleTo(mc.player);
        if(hidden&&!translucent&&!mc.shouldEntityAppearGlowing(entity))return;
        var visual=entity.getData(ModAttachments.CONSUME_VISUAL);
        boolean consuming=visual.active()&&entity.level().getEntity(visual.consumerId())!=null;
        if(consuming&&visual.progress(entity.level().getGameTime(),partial)>=.995F
                &&!com.codex.blacklight.combat.ConsumeSequence.boss(entity))return;
        float scale=scale(entity,mesh);
        float bodyYaw=net.minecraft.util.Mth.rotLerp(partial,entity.yBodyRotO,entity.yBodyRot);
        float headYaw=net.minecraft.util.Mth.wrapDegrees(net.minecraft.util.Mth.rotLerp(partial,entity.yHeadRotO,entity.yHeadRot)-bodyYaw);
        float headPitch=net.minecraft.util.Mth.lerp(partial,entity.xRotO,entity.getXRot());
        float age=entity.tickCount+partial;
        // The host's tick-driven walk clock survives render passes and resource reloads.
        float walk=entity.walkAnimation.position(partial)*.6662F;
        float speed=Math.clamp(entity.walkAnimation.speed(partial)*2,0,1);
        float attack=entity.getAttackAnim(partial);
        var receiver=InfectedModelPose.receiverState(entity,partial);
        stack.pushPose();
        try{
            DevourerFeedingFx.applyVictim(entity,partial,stack);
            net.minecraft.world.phys.Vec3 placement=net.minecraft.world.phys.Vec3.ZERO;
            if(consuming){
                placement=com.codex.blacklight.client.ClientCarryPlacement.consumeOffset(entity,partial);
                stack.translate(placement.x,placement.y,placement.z);
                ConsumedMobVisuals.whip(entity,stack,buffers.getBuffer(RenderType.entityCutoutNoCull(ConsumedMobVisuals.WHITE)),
                        light,entity.level().getEntity(visual.consumerId()).getPosition(partial).subtract(entity.getPosition(partial).add(placement)),
                        ConsumedMobVisuals.tetherProgress(entity,partial));
            }
            ConsumeFracture fracture=consuming&&ConsumeFracture.visible(entity,partial)
                    ?new ConsumeFracture(entity,partial,stack,buffers,ConsumeAbsorptionFx.destination(entity,partial,placement)):null;
            if(consuming)ConsumeVictimPose.body(entity,stack,partial);else HitBodyPose.apply(entity,stack,partial);
            stack.mulPose(Axis.YP.rotationDegrees(180-bodyYaw));
            if(!consuming&&entity.deathTime>0&&entity.getData(ModAttachments.HIT_REACTION).weight(entity.level().getGameTime(),partial)<=0){
                float fall=Math.min(1,(entity.deathTime+partial)/20F);
                stack.translate(0,mesh.bounds().getYsize()*scale*.5,0);
                stack.mulPose(Axis.ZP.rotationDegrees(fall*70));
                stack.translate(0,-mesh.bounds().getYsize()*scale*.5,0);
            }
            stack.scale(scale,scale,scale);
            RenderType type=translucent?RenderType.entityTranslucent(ConsumedMobVisuals.WHITE)
                    :hidden?RenderType.outline(ConsumedMobVisuals.WHITE):RenderType.entityCutoutNoCull(ConsumedMobVisuals.WHITE);
            var out=(fracture==null?buffers:fracture).getBuffer(type);
            int overlay=OverlayTexture.pack(0,entity.hurtTime>0||entity.deathTime>0);
            mesh.render(stack,out,light,overlay,translucent?40:255,(bone,posed)->{
                if(!InfectedModelPose.receiver(mesh.species(),bone.name,bone.parent,receiver,posed)
                        &&!InfectedModelPose.devourerFeeding(entity,bone.name,partial,posed))
                    InfectedModelPose.transform(mesh.species(),bone.name,age,walk,speed,attack,headYaw,headPitch,
                            entity.family().equals("air")&&!entity.onGround()||entity.isInWaterOrBubble(),posed);
                InfectedModelPose.nativeAction(entity.nativeAction(),bone.name,entity.nativeActionElapsed(partial),posed);
            },com.codex.blacklight.infection.TissueLateBody.progress(entity.tissueAge()));
            if(fracture!=null)fracture.finish();
        }finally{stack.popPose();}
        super.render(entity,yaw,partial,stack,buffers,light);
    }
}
