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
import com.codex.blacklight.combat.TargetSelection;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Explicit Blacklight target selection shared by the camera and server combat. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class ClientTargetLock {
    private static net.minecraft.client.player.LocalPlayer previousPlayer;
    private static Entity lockedTarget;
    private static int lockedRegion=-1;
    private static final TargetLockRequestState REQUEST=new TargetLockRequestState();
    private static String notice="";
    private static int noticeUntil;
    public record AckTrace(int requestId,int acceptedId,boolean applied,long gameTick) { }
    private static final java.util.ArrayDeque<AckTrace> ACK_TRACE=new java.util.ArrayDeque<>();
    private record Focus(net.minecraft.client.player.LocalPlayer player,Entity target,int region,long tick,float partial,
            Vec3 eye,Vec3 targetPosition,Vec3 point) {}
    private static Focus cachedFocus;
    private ClientTargetLock() {}
    public static boolean active(){return ExplicitTarget.enabled(Minecraft.getInstance().player);}
    public static boolean valid(Entity target){
        var p=Minecraft.getInstance().player;
        return p!=null && selection(target).selectable(p);
    }
    public static double range(){return Math.clamp(Minecraft.getInstance().options.renderDistance().get()*16.0,32,256);}
    public static boolean visibleInRange(Entity target){
        var p=Minecraft.getInstance().player;
        return valid(target)&&selection(target).visibleWithinRange(p,range());
    }
    public static TargetSelection selection(Entity target){return new TargetSelection(target,target==lockedTarget?lockedRegion:-1);}
    public static int region(){return hasValidLock()?lockedRegion:-1;}
    public static Vec3 focusPoint(Entity target){return focusPoint(target,0);}
    public static Vec3 focusPoint(Entity target,float partial){
        var p=Minecraft.getInstance().player;
        if(p==null)return target.getBoundingBox().getCenter();
        var selected=selection(target);
        if(target instanceof LivingEntity living&&!CombatTargeting.multipart(living))return selected.point(p);
        if(selected.goliathPart()==null)partial=0;
        long tick=p.level().getGameTime();var eye=p.getEyePosition();var position=target.position();
        var cached=cachedFocus;
        if(cached!=null&&cached.player==p&&cached.target==target&&cached.region==lockedRegion&&cached.tick==tick&&cached.partial==partial
                &&cached.eye.equals(eye)&&cached.targetPosition.equals(position))return cached.point;
        var point=selected.point(p,partial);
        cachedFocus=new Focus(p,target,lockedRegion,tick,partial,eye,position,point);
        return point;
    }
    public static Vec3 renderedFocus(Entity target,float partial){
        if(selection(target).goliathPart()!=null)return focusPoint(target,partial);
        return focusPoint(target).add(target.getPosition(partial).subtract(target.position()));
    }
    public static net.minecraft.world.phys.AABB focusBounds(Entity target,float partial){
        if(!(target instanceof LivingEntity living)||!CombatTargeting.multipart(living))return target.getBoundingBox();
        var point=renderedFocus(target,partial);
        return new net.minecraft.world.phys.AABB(point,point).inflate(.4);
    }
    public static LivingEntity target(){return entityTarget() instanceof LivingEntity living?living:null;}
    public static Entity entityTarget(){return hasValidLock()?lockedTarget:null;}
    public static boolean hasValidLock(){return active()&&valid(lockedTarget)&&REQUEST.desiredId()==lockedTarget.getId()&&REQUEST.desiredRegion()==lockedRegion;}
    public static boolean pending(){return REQUEST.pending();}
    public static int requestId(){return REQUEST.requestId();}
    public static int requestedId(){return REQUEST.desiredId();}
    public static int acknowledgedId(){return REQUEST.acknowledgedId();}
    public static java.util.List<AckTrace> acknowledgementTrace(){return java.util.List.copyOf(ACK_TRACE);}
    public static String notice(){var p=Minecraft.getInstance().player;return p!=null&&p.tickCount<noticeUntil?notice:"";}
    private static void notice(String message){notice=message;var p=Minecraft.getInstance().player;noticeUntil=p==null?0:p.tickCount+50;}
    private static void request(int id){request(id,-1);}
    private static void request(int id,int region) {
        int sequence=REQUEST.request(id,region);
        var mc=Minecraft.getInstance();
        if(mc.getConnection()!=null)PacketDistributor.sendToServer(new TargetLockPayload(id,sequence,region));
    }
    public static void acceptAck(TargetLockAckPayload payload) {
        var mc=Minecraft.getInstance();
        // A reply belonging to the old LocalPlayer cannot affect a respawned owner.
        int requested=REQUEST.desiredId();
        boolean applied=mc.player!=null&&mc.player==previousPlayer&&REQUEST.acknowledge(payload.requestId(),payload.acceptedEntityId(),payload.acceptedRegion());
        ACK_TRACE.addLast(new AckTrace(payload.requestId(),payload.acceptedEntityId(),applied,mc.level==null?-1:mc.level.getGameTime()));
        while(ACK_TRACE.size()>32)ACK_TRACE.removeFirst();
        if(!applied)return;
        if(payload.acceptedEntityId()<0){lockedTarget=null;lockedRegion=-1;cachedFocus=null;if(requested>=0)notice("目标不可锁定");}
        else if(mc.level!=null){lockedTarget=ExplicitTarget.resolve(mc.level.getEntity(payload.acceptedEntityId()));lockedRegion=payload.acceptedRegion();cachedFocus=null;}
        REQUEST.observeAttachment(mc.player.getData(ModAttachments.TARGET_LOCK_ID),mc.player.getData(ModAttachments.TARGET_LOCK_REGION));
    }
    public static void clear(){
        boolean requested=REQUEST.desiredId()>=0||lockedTarget!=null;
        lockedTarget=null;lockedRegion=-1;cachedFocus=null;
        if(requested)request(-1);
    }
    public static void toggle(){
        sync();
        var mc=Minecraft.getInstance();var player=mc.player;
        if(player==null||mc.level==null||!active())return;
        if(REQUEST.desiredId()>=0){
            // Repeated lock presses cycle visible boss subtargets. Sneak+lock releases.
            if(lockedTarget!=null&&selection(lockedTarget).regional()&&!player.isShiftKeyDown()){
                var next=selection(lockedTarget).nextVisible(player,range());
                if(next!=null){select(next);return;}
            }
            clear();return;
        }
        TargetSelection chosen=null;
        if(mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult hit){
            var candidate=TargetSelection.fromHit(hit.getEntity());
            if(candidate.visibleWithinRange(player,range()))chosen=candidate;
        }
        if(chosen==null){
            var camera=mc.gameRenderer.getMainCamera();
            Vec3 origin=camera.getPosition(),view=Vec3.directionFromRotation(camera.getXRot(),camera.getYRot());
            var candidates=ExplicitTarget.candidates(player,range());
            chosen=TargetSelection.aimedGoliath(player,candidates,origin,origin.add(view.scale(range())),mc.getTimer().getGameTimeDeltaPartialTick(false),range());
            if(chosen!=null){select(chosen);return;}
            double best=Double.NEGATIVE_INFINITY;
            for(var entity:candidates)for(var candidate:TargetSelection.choices(player,entity)){
                Vec3 delta=candidate.point(player).subtract(origin);double angle=view.dot(delta.normalize());
                boolean near=angle>=.5&&candidate.visibleWithinRange(player,range());
                if(!near&&(candidate.regional()||!(entity instanceof LivingEntity living)||!CombatTargeting.altitudeAcquirable(player,living,range())))continue;
                double score;
                if(near)score=angle*4-Math.sqrt(candidate.distanceSquared(player))/range();
                else{
                    Vec3 flat=new Vec3(view.x,0,view.z),side=new Vec3(delta.x,0,delta.z);
                    double h=side.length(),align=h<1e-3||flat.lengthSqr()<1e-6?1:flat.normalize().dot(side.scale(1/h));
                    score=-h/range()*4+align*.5;
                }
                if(score>best){best=score;chosen=candidate;}
            }
        }
        if(chosen!=null)select(chosen);else notice("视野内没有可锁定目标");
        sync();
    }
    private static void select(TargetSelection selected){
        lockedTarget=selected.target();lockedRegion=selected.region();cachedFocus=null;notice="";
        request(lockedTarget.getId(),lockedRegion);
    }
    public static void sync(){
        var mc=Minecraft.getInstance();var p=mc.player;
        if(p==null){
            if(previousPlayer!=null)REQUEST.resetOwner();
            previousPlayer=null;cachedFocus=null;lockedTarget=null;lockedRegion=-1;notice="";return;
        }
        if(p!=previousPlayer){
            previousPlayer=p;REQUEST.resetOwner();cachedFocus=null;lockedTarget=null;lockedRegion=-1;notice="";
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
        boolean altitude=lockedTarget instanceof LivingEntity living&&!selection(lockedTarget).regional()&&active()&&lockedTarget.level()==p.level()
                &&living.getHealth()>0&&com.codex.blacklight.combat.BulletdiveSteering.retainAltitudeLock(
                        p.position(),p.onGround(),lockedTarget.position(),range());
        if (lockedTarget != null && !altitude) {
            if (!active() || !valid(lockedTarget)) { clear(); return; }
            boolean visible = visibleInRange(lockedTarget);
            if (lockedTarget instanceof com.codex.blacklight.entity.BlackwatchHelicopter aircraft) {
                // Destruction, protection and range loss invalidate immediately. Only
                // temporary terrain occlusion of an already acquired hull has a grace period.
                double range = range();
                if (!visible && p.getEyePosition().distanceToSqr(
                        com.codex.blacklight.military.aircraft.HelicopterSkinContact.closestPoint(aircraft, p.getEyePosition())) > range * range) {
                    clear();
                    return;
                }
                visible = REQUEST.retainAircraftSight(visible, p.level().getGameTime());
            }
            if (!visible) { clear(); return; }
        }
        if(REQUEST.observeAttachment(p.getData(ModAttachments.TARGET_LOCK_ID),p.getData(ModAttachments.TARGET_LOCK_REGION))){lockedTarget=null;lockedRegion=-1;cachedFocus=null;}
        if(REQUEST.desiredId()<0){lockedTarget=null;lockedRegion=-1;cachedFocus=null;}
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){sync();}
}
