package com.codex.blacklight.client.renderer;

import com.codex.blacklight.entity.BlacklightInfectedHost;
import com.codex.blacklight.infection.EvolvedSpecialMove;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3f;
import java.util.*;

/** Tendons begin at the final posed wrist, and retract with the synchronized action. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class EvolvedVariantFx {
    private record Anchors(Vec3 right,Vec3 left,double time){}
    private static final Map<BlacklightInfectedHost,Anchors> ANCHORS=new WeakHashMap<>();
    private EvolvedVariantFx(){}
    static void capture(BlacklightInfectedHost host,float partial,PoseStack stack,Vector3f right,Vector3f left){
        var move=EvolvedSpecialMove.resolve(host.evolvedAction());if(move!=EvolvedSpecialMove.LASH&&move!=EvolvedSpecialMove.MEND)return;
        var camera=Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();var r=stack.last().pose().transformPosition(right);var l=stack.last().pose().transformPosition(left);
        ANCHORS.put(host,new Anchors(new Vec3(r.x,r.y,r.z).add(camera),new Vec3(l.x,l.y,l.z).add(camera),host.level().getGameTime()+partial));
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES)return;var mc=Minecraft.getInstance();if(mc.level==null){ANCHORS.clear();return;}
        double now=mc.level.getGameTime()+event.getPartialTick().getGameTimeDeltaPartialTick(false);
        ANCHORS.entrySet().removeIf(e->e.getKey().isRemoved()||e.getKey().level()!=mc.level||now-e.getValue().time>2);
        if(ANCHORS.isEmpty())return;var type=RenderType.entityCutoutNoCull(ConsumedMobVisuals.WHITE);var buffers=mc.renderBuffers().bufferSource();var out=buffers.getBuffer(type);var pose=event.getPoseStack();var camera=event.getCamera().getPosition();pose.pushPose();pose.translate(-camera.x,-camera.y,-camera.z);
        for(var entry:ANCHORS.entrySet()){
            var host=entry.getKey();if(host.isInvisible()||host.isMetamorphosing())continue;var move=EvolvedSpecialMove.resolve(host.evolvedAction());if(move==null)continue;
            float amount=move.extension(host.evolvedActionElapsed(event.getPartialTick().getGameTimeDeltaPartialTick(false)));if(amount<.01)continue;
            int[] targets=move==EvolvedSpecialMove.MEND?host.evolvedPatients():new int[]{host.evolvedSpecialTarget()};
            for(int id:targets){var e=mc.level.getEntity(id);if(e==null||!e.isAlive()||e.distanceToSqr(host)>144)continue;
                var start=move==EvolvedSpecialMove.LASH?entry.getValue().right:entry.getValue().left;var end=start.lerp(e.getBoundingBox().getCenter(),amount);
                for(int strand=0;strand<2;strand++){
                    Vec3 previous=start;for(int i=1;i<=12;i++){
                        double t=i/12D,wave=Math.sin(Math.PI*t)*.09;var p=start.lerp(end,t).add(Math.cos(now*.15+t*8+strand*Math.PI)*wave,Math.sin(now*.13+t*9+strand)*wave,0);
                        ConsumedMobVisuals.tube(pose,out,LightTexture.FULL_BRIGHT,previous,p,(strand==0?.065:.026)*(1-t*.55),strand==0?0xff282329:0xff8d2531);previous=p;
                    }
                }
            }
        }pose.popPose();buffers.endBatch(type);
    }
}
