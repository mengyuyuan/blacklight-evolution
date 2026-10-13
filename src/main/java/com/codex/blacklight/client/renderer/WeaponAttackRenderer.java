package com.codex.blacklight.client.renderer;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.power.PowerForm;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Weapon reach is visible: a lash grows out of the rendered hand, quake follows contact. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class WeaponAttackRenderer {
    @SubscribeEvent public static void render(RenderLevelStageEvent e){
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES)return;
        var mc=Minecraft.getInstance();if(mc.level==null)return;
        float partial=e.getPartialTick().getGameTimeDeltaPartialTick(false);var camera=e.getCamera().getPosition();
        var buffers=mc.renderBuffers().bufferSource();var type=RenderType.entityCutoutNoCull(ConsumedMobVisuals.WHITE);var out=buffers.getBuffer(type);var stack=e.getPoseStack();
        for(var entity:mc.level.entitiesForRendering()){
            if(!(entity instanceof Player player)||player.distanceToSqr(camera)>96*96)continue;
            var visual=player.getData(ModAttachments.WEAPON_VISUAL);var move=visual.spec();if(move==null)continue;
            if(visual.traveling())continue;
            double tick=visual.attackPose()>=0?visual.attackPose()*20:mc.level.getGameTime()+partial-visual.started();
            var definition=LocalWeaponMotion.find(move.id);
            WhipCurve sourceCurve=null;
            if(definition.isPresent()){
                var local=definition.get();
                if(visual.charging()&&local.charge().isPresent()){
                    var charge=LocalVisualMotion.find(local.charge().get().animation());
                    if(charge.isPresent())sourceCurve=charge.get().whipCurve().orElse(null);
                }else sourceCurve=local.whipCurve().orElse(null);
            }
            double attackTick=tick;
            if(sourceCurve==null&&(visual.charging()||attackTick<move.start-4||attackTick>move.end+5))continue;
            var pos=player.getPosition(partial);int light=mc.getEntityRenderDispatcher().getPackedLightCoords(player,partial);
            stack.pushPose();stack.translate(-camera.x,-camera.y,-camera.z);
            if(move.form==PowerForm.WHIPFIST){
                if(sourceCurve!=null){
                    // Use the exact skeletal clock, including charge holds and
                    // attackPose. Re-applying transition to a held pose detached it.
                    var clock=com.codex.blacklight.animation.WeaponMotionClock.sample(visual,mc.level.getGameTime()+partial);
                    double time=clock.seconds();
                    var points=new java.util.ArrayList<>(WhipCurveContact.worldPoints(sourceCurve,time,pos,visual.yaw(),player));
                    var hand=WhipTetherRenderer.capturedHand(player.getId());
                    if(hand!=null)points=new java.util.ArrayList<>(WhipCurveContact.attachHand(points,hand));
                    var visible=WhipCurveContact.visiblePoints(points,mc.level,player);
                    WhipArmSurface.render(stack,out,light,visible,Math.max(.105,sourceCurve.radius()));
                    if(visible.size()>1)WeaponTrailRenderer.captureWorld(player,false,visible.get(visible.size()-2),visible.getLast());
                    stack.popPose();continue;
                }
                tick=attackTick;
                var forward=WeaponCombatSystem.strikeDirection(move,visual.yaw(),tick);
                var hand=WhipTetherRenderer.capturedHand(player.getId());if(hand==null)hand=pos.add(0,1.1,0).add(forward.scale(.4));
                double extent=Math.clamp((tick-move.start+4)/4,0,1)*Math.clamp((move.end+5-tick)/5,0,1);
                var end=pos.add(0,1.15,0).add(forward.scale(AttackReach.reach(player,move)*extent));
                // The middle of a flexible lash trails behind its cutting tip.
                // Keep the tip on the same swept ray used by server hit detection.
                var trailing=WeaponCombatSystem.strikeDirection(move,visual.yaw(),Math.max(move.start,tick-2));
                var control=pos.add(0,1.05,0).add(trailing.scale(AttackReach.reach(player,move)*extent*.58));
                var points=new java.util.ArrayList<Vec3>();
                for(int i=0;i<=32;i++)points.add(curve(hand,control,end,i/32.));
                WhipArmSurface.render(stack,out,light,WhipCurveContact.visiblePoints(points,mc.level,player),.105);
            }
            stack.popPose();
        }
        buffers.endBatch(type);
    }
    private static Vec3 curve(Vec3 hand,Vec3 control,Vec3 tip,double t){
        return hand.scale((1-t)*(1-t)).add(control.scale(2*t*(1-t))).add(tip.scale(t*t));
    }
}
