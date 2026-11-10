package com.codex.blacklight.client;

import com.codex.blacklight.combat.ConsumePlacementTrack;
import com.codex.blacklight.combat.ConsumeSequence;
import com.codex.blacklight.combat.ConsumeVisual;
import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent;

/**
 * Local prediction of a server-owned grapple root. The server places the actor every
 * tick and sends a teleport; applied as-is, each teleport clears interpolation and the
 * body and camera move in one step per tick. The client follows the same authored track
 * from the synced scene, so interpolation stays continuous, and teleports that agree
 * with the prediction keep the predicted interpolation instead of snapping.
 */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class ClientConsumeActor {
    private ClientConsumeActor(){}
    /** A server placement within this distance of the prediction is the same pose. */
    public static final double TOLERANCE=.75;
    private static int sceneOwner=-1;private static long sceneStarted=Long.MIN_VALUE;private static int sceneStage=-1;
    private static boolean diverged;private static int resyncs;
    /** A stall or a correction resyncs the track from the server this many times per scene before handing it to the server. */
    public static final int MAX_RESYNCS=3;
    private static Vec3 predicted,previous;

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();var player=mc.player;
        if(player==null||mc.level==null){reset();return;}
        var scene=scene(player);
        if(scene==null){reset();return;}
        var visual=ModAttachments.peekData(scene,ModAttachments.CONSUME_VISUAL,ConsumeVisual.EMPTY);
        if(visual.consumerId()!=sceneOwner||visual.started()!=sceneStarted||visual.stage()!=sceneStage){
            sceneOwner=visual.consumerId();sceneStarted=visual.started();sceneStage=visual.stage();diverged=false;resyncs=0;
        }
        if(diverged){predicted=previous=null;return;}
        var goal=goal(player,scene,visual,mc.level.getGameTime());
        if(goal==null){predicted=previous=null;return;}
        Vec3 from=player.position(),delta=goal.subtract(from);
        double limit=ConsumeSequence.mountedStage(visual.stage())?(visual.stage()==ConsumeVisual.MOUNT?.48:.65):.8;
        if(delta.length()>limit)delta=delta.normalize().scale(limit);
        var allowed=net.minecraft.world.entity.Entity.collideBoundingBox(player,delta,player.getBoundingBox(),mc.level,java.util.List.of());
        Vec3 next=from.add(allowed);
        player.setPos(next.x,next.y,next.z);
        player.setDeltaMovement(0,0,0);player.fallDistance=0;
        previous=predicted;predicted=next;
    }

    /** The receiver whose scene this player is currently driving, or null. */
    private static LivingEntity scene(LocalPlayer player){
        if(!ModAttachments.getData(player,ModAttachments.CONSUMING))return null;
        int stage=ModAttachments.getData(player,ModAttachments.GRAB_STAGE);
        boolean paired=stage==ConsumeVisual.CONSUME;
        if(!paired&&!ConsumeSequence.mountedStage(stage))return null;
        for(var e:player.level().getEntitiesOfClass(LivingEntity.class,player.getBoundingBox().inflate(16),
                e->e!=player&&ModAttachments.peekData(e,ModAttachments.CONSUME_VISUAL,ConsumeVisual.EMPTY).consumerId()==player.getId())){
            if(ModAttachments.peekData(e,ModAttachments.CONSUME_VISUAL,ConsumeVisual.EMPTY).stage()==stage)return e;
        }
        return null;
    }

    /** Same placement law as the server's ConsumeSystem for the synced scene. */
    static Vec3 goal(LocalPlayer player,LivingEntity victim,ConsumeVisual visual,long now){
        var form=PowerForm.values()[com.codex.blacklight.util.MathCompat.clamp(visual.form(),0,PowerForm.values().length-1)];
        int stage=visual.stage();
        if(stage==ConsumeVisual.CONSUME){
            if(!ConsumeSequence.paired(form,false))return null;
            float t=visual.motionProgress(now,0);
            return ConsumePlacementTrack.actorAnchor(visual.origin(),visual.facing(),form,t,false,visual.variant());
        }
        if(!ConsumeSequence.mountedStage(stage))return null;
        Vec3 goal=ConsumeSequence.mount(victim);
        if(ConsumeVisual.consumeStage(stage))
            goal=ConsumePlacementTrack.actorAnchor(goal,visual.facing(),form,visual.motionProgress(now,0),true);
        return goal;
    }

    /** True while the local actor's root follows the predicted scene track. */
    public static boolean predicting(){return predicted!=null&&!diverged;}

    /** Called after a server position packet. Returns true to keep the predicted interpolation. */
    public static boolean accept(Vec3 server){
        if(!predicting())return false;
        double near=Math.min(server.distanceTo(predicted),previous==null?Double.MAX_VALUE:server.distanceTo(previous));
        if(near<=TOLERANCE)return true;
        // A frame stall or a server correction moved the actor off the track: take the server
        // position once and predict from there; repeated disagreement hands the scene to the server.
        predicted=previous=null;if(++resyncs>MAX_RESYNCS)diverged=true;return false;
    }

    private static void reset(){sceneOwner=-1;sceneStarted=Long.MIN_VALUE;sceneStage=-1;diverged=false;resyncs=0;predicted=previous=null;}
}