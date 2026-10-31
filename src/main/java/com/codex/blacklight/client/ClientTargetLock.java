package com.codex.blacklight.client;

import com.codex.blacklight.combat.CombatTargeting;
import com.codex.blacklight.network.TargetLockPayload;
import com.codex.blacklight.network.TargetLockAckPayload;
import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.power.PrototypeMetabolism;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import com.codex.blacklight.combat.ExplicitTarget;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent;
import com.codex.blacklight.forge.PacketDistributor;

/** Explicit Blacklight target selection shared by the camera and server combat. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class ClientTargetLock {
    private static net.minecraft.client.player.LocalPlayer previousPlayer;
    private static Entity lockedTarget;
    private static final TargetLockRequestState REQUEST=new TargetLockRequestState();
    private static String notice="";
    private static int noticeUntil;
    public record AckTrace(int requestId,int acceptedId,boolean applied,long gameTick) { }
    private static final java.util.ArrayDeque<AckTrace> ACK_TRACE=new java.util.ArrayDeque<>();
    private record Focus(net.minecraft.client.player.LocalPlayer player,Entity target,long tick,
            Vec3 eye,Vec3 targetPosition,Vec3 point) {}
    private static Focus cachedFocus;
    private ClientTargetLock() {}
    public static boolean active(){return ExplicitTarget.enabled(Minecraft.getInstance().player);}
    public static boolean valid(Entity target){
        var p=Minecraft.getInstance().player;
        return p!=null && ExplicitTarget.selectable(p,target);
    }
    public static double range(){return com.codex.blacklight.forge.WingMath.clamp(Minecraft.getInstance().options.renderDistance().get()*16.0,32,256);}
    public static boolean visibleInRange(Entity target){
        var p=Minecraft.getInstance().player;
        return valid(target)&&ExplicitTarget.visibleWithinRange(p,target,range());
    }
    public static Vec3 focusPoint(Entity target){
        var p=Minecraft.getInstance().player;
        if(p==null||!(target instanceof LivingEntity living)||!CombatTargeting.multipart(living))return target.getBoundingBox().getCenter();
        long tick=p.level().getGameTime();var eye=p.getEyePosition();var position=target.position();
        var cached=cachedFocus;
        if(cached!=null&&cached.player==p&&cached.target==target&&cached.tick==tick
                &&cached.eye.equals(eye)&&cached.targetPosition.equals(position))return cached.point;
        var point=CombatTargeting.lockPoint(p,(LivingEntity)target);
        cachedFocus=new Focus(p,target,tick,eye,position,point);
        return point;
    }
    public static net.minecraft.world.phys.AABB focusBounds(Entity target){
        if(!(target instanceof LivingEntity living)||!CombatTargeting.multipart(living))return target.getBoundingBox();
        var point=focusPoint(target);
        return new net.minecraft.world.phys.AABB(point,point).inflate(.4);
    }
    public static LivingEntity target(){return entityTarget() instanceof LivingEntity living?living:null;}
    public static Entity entityTarget(){return hasValidLock()?lockedTarget:null;}
    public static boolean hasValidLock(){return active()&&valid(lockedTarget)&&REQUEST.desiredId()==lockedTarget.getId();}
    public static boolean pending(){return REQUEST.pending();}
    public static int requestId(){return REQUEST.requestId();}
    public static int requestedId(){return REQUEST.desiredId();}
    public static int acknowledgedId(){return REQUEST.acknowledgedId();}
    public static java.util.List<AckTrace> acknowledgementTrace(){return java.util.List.copyOf(ACK_TRACE);}
    public static String notice(){var p=Minecraft.getInstance().player;return p!=null&&p.tickCount<noticeUntil?notice:"";}
    private static void notice(String message){notice=message;var p=Minecraft.getInstance().player;noticeUntil=p==null?0:p.tickCount+50;}
    private static void request(int id) {
        int sequence=REQUEST.request(id);
        var mc=Minecraft.getInstance();
        if(mc.getConnection()!=null)PacketDistributor.sendToServer(new TargetLockPayload(id,sequence));
    }
    public static void acceptAck(TargetLockAckPayload payload) {
        var mc=Minecraft.getInstance();
        // A reply belonging to the old LocalPlayer cannot affect a respawned owner.
        int requested=REQUEST.desiredId();
        boolean applied=mc.player!=null&&mc.player==previousPlayer&&REQUEST.acknowledge(payload.requestId(),payload.acceptedEntityId());
        ACK_TRACE.addLast(new AckTrace(payload.requestId(),payload.acceptedEntityId(),applied,mc.level==null?-1:mc.level.getGameTime()));
        while(ACK_TRACE.size()>32)ACK_TRACE.removeFirst();
        if(!applied)return;
        if(payload.acceptedEntityId()<0){lockedTarget=null;cachedFocus=null;if(requested>=0)notice("目标不可锁定");}
        else if(mc.level!=null)lockedTarget=ExplicitTarget.resolve(mc.level.getEntity(payload.acceptedEntityId()));
        REQUEST.observeAttachment(com.codex.blacklight.registry.ModAttachments.getData(mc.player, ModAttachments.TARGET_LOCK_ID));
    }
    public static void clear(){
        boolean requested=REQUEST.desiredId()>=0||lockedTarget!=null;
        lockedTarget=null;cachedFocus=null;
        if(requested)request(-1);
    }
    public static void toggle(){
        sync();
        if(REQUEST.desiredId()>=0){clear();return;}
        var mc=Minecraft.getInstance();var player=mc.player;
        if(player==null||mc.level==null||!active())return;
        // Exact limb hits retain their multipart parent. Otherwise choose the actor
        // nearest the viewing direction, considering only exposed damage surfaces.
        if(mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult hit){
            var candidate=ExplicitTarget.resolve(hit.getEntity());
            if(visibleInRange(candidate))lockedTarget=candidate;
        }
        if(lockedTarget==null){
            var camera=mc.gameRenderer.getMainCamera();
            Vec3 origin=camera.getPosition(),view=Vec3.directionFromRotation(camera.getXRot(),camera.getYRot());
            double best=Double.NEGATIVE_INFINITY;
            for(var candidate:ExplicitTarget.candidates(player,range())){
                if(!visibleInRange(candidate))continue;
                Vec3 delta=focusPoint(candidate).subtract(origin);
                double angle=view.dot(delta.normalize());
                if(angle<.5)continue;
                double score=angle*4-Math.sqrt(ExplicitTarget.distanceSquared(player,candidate))/range();
                if(score>best){best=score;lockedTarget=candidate;}
            }
        }
        if(lockedTarget!=null){notice="";request(lockedTarget.getId());}
        else notice("视野内没有可锁定目标");
        sync();
    }
    public static void sync(){
        var mc=Minecraft.getInstance();var p=mc.player;
        if(p==null || !p.isAlive() || !ModAttachments.available(p)){
            if(previousPlayer!=null)REQUEST.resetOwner();
            previousPlayer=null;cachedFocus=null;lockedTarget=null;notice="";return;
        }
        if(p!=previousPlayer){
            previousPlayer=p;REQUEST.resetOwner();cachedFocus=null;lockedTarget=null;notice="";
            // Respawn, reconnect and world handoff explicitly release any copied server attachment.
            request(-1);
        }
        // An observer can stop tracking a distant actor. Keep the explicit
        // request across altitude changes, not a second automatically chosen target.
        // The server still validates life, allies, dimension and loaded ownership.
        if(lockedTarget!=null&&mc.level!=null){
            var tracked=ExplicitTarget.resolve(mc.level.getEntity(lockedTarget.getId()));
            if(tracked!=null)lockedTarget=tracked;
        }
        boolean altitude=lockedTarget instanceof LivingEntity living&&active()&&lockedTarget.level()==p.level()
                &&living.getHealth()>0&&com.codex.blacklight.combat.BulletdiveSteering.retainAltitudeLock(
                        p.position(),p.onGround(),lockedTarget.position(),range());
        if(lockedTarget!=null&&(!active()||!altitude&&!visibleInRange(lockedTarget))){clear();return;}
        if(REQUEST.observeAttachment(com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.TARGET_LOCK_ID))){lockedTarget=null;cachedFocus=null;}
        if(REQUEST.desiredId()<0){lockedTarget=null;cachedFocus=null;}
    }
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent event){if(event.phase != net.minecraftforge.event.TickEvent.Phase.END)return;sync();}
}
