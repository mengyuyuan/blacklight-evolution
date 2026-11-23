package com.codex.blacklight.mixin.client;

import com.codex.blacklight.client.renderer.BiomassCoatBuffers;
import com.codex.blacklight.client.renderer.AdaptiveRagdoll;
import com.codex.blacklight.power.BiomassState;
import com.codex.blacklight.power.BiomassSystem;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Includes custom/Gecko renderers which never fire RenderLivingEvent. */
@Mixin(EntityRenderDispatcher.class)
public abstract class BiomassEntityRenderMixin {
    @com.llamalad7.mixinextras.injector.ModifyExpressionValue(method="render",at=@At(value="INVOKE",
            target="Lnet/minecraft/client/renderer/entity/EntityRenderer;getRenderOffset(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/world/phys/Vec3;"))
    private net.minecraft.world.phys.Vec3 blacklight$carryOffset(net.minecraft.world.phys.Vec3 original,Entity entity,
            double x,double y,double z,float yaw,float partial){
        if(!(entity instanceof net.minecraft.world.entity.player.Player p))return original;
        var paired=com.codex.blacklight.combat.GoddessEncounter.paired(p);
        if(paired!=null){
            // Match the exact interpolation used by LevelRenderer.renderEntity, also for spectators and shader passes.
            var anchor=new net.minecraft.world.phys.Vec3(p.xOld,p.yOld,p.zOld).lerp(p.position(),partial);
            return p.getPosition(partial).add(paired.goddess.drawnActorOffset(p,partial)).subtract(anchor);
        }
        return original.add(com.codex.blacklight.client.animation.GoddessPressurePose.carryOffset(p,partial));
    }
    @WrapOperation(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private void blacklight$coat(EntityRenderer<?> renderer,Entity entity,float yaw,float partial,
                                PoseStack stack,MultiBufferSource buffers,int light,Operation<Void> original){
        if(entity instanceof LivingEntity living && com.codex.blacklight.entity.QuillOrgan.consumedRemains(living))return;
        com.codex.blacklight.client.renderer.NativeInfectionVisuals.vascular(entity,stack,buffers,partial,light);
        try(var renderScope=com.codex.blacklight.client.renderer.NativeRenderScope.enter(entity);
            var scene=com.codex.blacklight.client.renderer.NativeInfectionVisuals.competition(entity,stack,buffers,partial,light);
            var tissue=com.codex.blacklight.client.renderer.NativeInfectionVisuals.beginCustom(entity,renderer,stack,partial)){
            var base=scene==null?buffers:scene.buffers();
            var source=tissue==null?base:com.codex.blacklight.client.renderer.NativeInfectionVisuals.buffers(base);
            var owner=entity instanceof LivingEntity?BiomassSystem.claimant(entity):null;
            var extracting=entity instanceof LivingEntity living?com.codex.blacklight.entity.QuillOrgan.extracting(living):null;
            if(owner==null && extracting!=null){
                var mob=(LivingEntity)entity;
                var flow=new BiomassCoatBuffers(mob,stack,source,extracting,partial);
                try(var physics=AdaptiveRagdoll.begin(entity,stack,flow,partial)){
                    var output=physics==null?flow:physics.buffers();
                    original.call(renderer,entity,yaw,partial,stack,output,light);
                    if(tissue!=null)com.codex.blacklight.client.renderer.InfectedMorphology.flush(mob,output);
                    if(physics!=null)physics.finish();
                }
                flow.finish();
                AdaptiveRagdoll.drawQuillTethers(mob,stack,source,light,partial);
                return;
            }
            if(owner==null && entity instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon mob
                    && mob.getData(com.codex.blacklight.registry.ModAttachments.CONSUME_VISUAL).stage()==com.codex.blacklight.combat.ConsumeVisual.MOUNT_CONSUME){
                var skin=new com.codex.blacklight.client.renderer.ConsumeFracture(mob,partial,stack,source,net.minecraft.world.phys.Vec3.ZERO);
                original.call(renderer,entity,yaw,partial,stack,skin,light);
                if(tissue!=null)com.codex.blacklight.client.renderer.InfectedMorphology.flush(mob,skin);
                skin.finishMountedSurface();
                return;
            }
            if(owner==null){
                var receiver=com.codex.blacklight.client.renderer.ChestConsumeReceiver.begin(entity,stack,source,partial);
                var skin=receiver==null?source:receiver;
                try(var physics=com.codex.blacklight.client.renderer.AdaptiveRagdoll.begin(entity,stack,skin,partial)){
                    var output=physics==null?skin:physics.buffers();
                    original.call(renderer,entity,yaw,partial,stack,output,light);
                    if(tissue!=null)com.codex.blacklight.client.renderer.InfectedMorphology.flush((LivingEntity)entity,output);
                    if(physics!=null)physics.finish();
                }
                if(receiver!=null)receiver.finish();
                return;
            }
                float progress=BiomassSystem.state(owner).progress(entity.level().getGameTime()+partial,com.codex.blacklight.power.ConsumeProgression.absorbTicks(owner));
                var coat=new BiomassCoatBuffers((LivingEntity)entity,stack,source,
                        Math.min(1,progress*1.45F),com.codex.blacklight.client.renderer.BiomassSurfaceBounds.ease((progress-.65F)/.35F),entity.tickCount+partial);
                original.call(renderer,entity,yaw,partial,stack,coat,light);
                if(tissue!=null)com.codex.blacklight.client.renderer.InfectedMorphology.flush((LivingEntity)entity,coat);
                coat.finish();
        }
    }
}
