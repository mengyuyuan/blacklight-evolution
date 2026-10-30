package com.codex.blacklight.client.renderer;

import com.codex.blacklight.entity.*;
import com.codex.blacklight.infection.*;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3f;
import java.util.*;

/** Short, wet fibres follow posed physical limbs, never a guessed orbit beside the actor. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class InfectedLimbTrails {
    private record Sample(double at,Vec3 point){}
    private static final Map<Mob,Map<Integer,ArrayDeque<Sample>>> STATES=new WeakHashMap<>();
    private InfectedLimbTrails(){}
    private static boolean active(Mob mob){
        if(mob.isInvisible()||!mob.isAlive()||com.codex.blacklight.registry.ModAttachments.getData(mob, com.codex.blacklight.registry.ModAttachments.CONSUME_VISUAL).active())return false;
        if(mob instanceof BlacklightCommonInfected civilian){long t=civilian.level().getGameTime()-civilian.attackStarted();return civilian.attackStarted()>=0&&t>=6&&t<=18;}
        if(mob instanceof BlacklightHunter hunter)return hunter.committedAttack()&&hunter.attackMove()!=com.codex.blacklight.combat.CreatureAttack.DODGE&&hunter.attackAge(0)>6&&hunter.attackAge(0)<34;
        if(mob instanceof BlacklightInfectedHost host){if(host.isEvolved()||!host.nativeAction().isEmpty())return false;float t=host.tissueStrikeElapsed(0);var core=host.organActionCore();boolean organ=false;if(core!=null){var cycle=TissueOrganCycle.cycle(core);float at=host.organActionElapsed(0);organ=at>=cycle.windup()-2&&at<=cycle.retract()+1;}return mob.swinging||t>=7&&t<=19||organ;}
        return mob.swinging;
    }
    public static void capture(Mob mob,int slot,PoseStack.Pose pose,Vec3 local){
        if(!active(mob))return;var mc=Minecraft.getInstance();if(mc.player==null||mc.player.distanceToSqr(mob)>64*64)return;
        float partial=mc.getFrameTime();double now=mob.level().getGameTime()+partial;
        if(STATES.size()>=128&&!STATES.containsKey(mob))return;
        var point=pose.pose().transformPosition(new Vector3f((float)local.x,(float)local.y,(float)local.z));var world=new Vec3(point.x,point.y,point.z).add(mc.gameRenderer.getMainCamera().getPosition());
        var q=STATES.computeIfAbsent(mob,k->new HashMap<>()).computeIfAbsent(slot,k->new ArrayDeque<>());
        if(!q.isEmpty()){var previous=q.getLast();if(now-previous.at<.08)return;if(now<previous.at||now-previous.at>1.5||world.distanceToSqr(previous.point)>9)q.clear();}
        q.add(new Sample(now,world));while(q.size()>24)q.removeFirst();
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent e){
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES)return;var mc=Minecraft.getInstance();if(mc.level==null){STATES.clear();return;}
        double now=mc.level.getGameTime()+e.getPartialTick();STATES.entrySet().removeIf(v->{if(v.getKey().isRemoved()||v.getKey().level()!=mc.level)return true;for(var q:v.getValue().values())while(!q.isEmpty()&&now-q.getFirst().at>2.8)q.removeFirst();return v.getValue().values().stream().allMatch(ArrayDeque::isEmpty);});
        var type=RenderType.entityTranslucent(ConsumedMobVisuals.WHITE);var buffers=mc.renderBuffers().bufferSource();var out=buffers.getBuffer(type);var pose=e.getPoseStack();var camera=e.getCamera().getPosition();pose.pushPose();pose.translate(-camera.x,-camera.y,-camera.z);
        for(var state:STATES.entrySet())for(var q:state.getValue().values()){
            while(!q.isEmpty()&&now-q.getFirst().at>2.8)q.removeFirst();Sample old=null;
            for(var sample:q){if(old!=null&&old.point.distanceToSqr(sample.point)>.0002){float fade=(float)com.codex.blacklight.forge.WingMath.clamp(1-(now-old.at)/2.8,0,1);int a=(int)(fade*150);ConsumedMobVisuals.tube(pose,out,0x00b000b0,old.point,sample.point,.016+fade*.025,(a<<24)|0x7d3340);}old=sample;}
        }
        pose.popPose();buffers.endBatch(type);
    }
}
