package com.codex.blacklight.combat;

import com.codex.blacklight.animation.NativePlayerActionBackend;
import com.codex.blacklight.animation.PlayerActions;
import com.codex.blacklight.movement.MovementState;
import com.codex.blacklight.network.WeaponAnimationPayload;
import com.codex.blacklight.power.CombatSkill;
import com.codex.blacklight.power.CriticalMassSystem;
import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.registry.ModAttachments;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Source clips plus authoritative, bounded Minecraft contacts. No client may
 * select a damage target, grant critical charge or invent a landing packet. */
@EventBusSubscriber(modid="blacklight")
public final class DevastatorSystem {
    public static final int CHARGE=0, RELEASE=1, FLIGHT=2, LAND=3, RECOVERY=4;
    private record TargetPlan(LivingEntity target,double contact) { }
    private record Held(WeaponButtons buttons,int tick) { }
    private static final Map<ServerPlayer,Held> INPUT=new WeakHashMap<>();
    private static final Map<ServerPlayer,Session> ACTIVE=new WeakHashMap<>();
    public record HitContext(String lane,int sourceTrack,double scheduled,float damage) { }
    private static final ThreadLocal<HitContext> HIT_CONTEXT=new ThreadLocal<>();
    /** Synchronous observation of an actual hurt callback, never a client request. */
    public static HitContext hitContext(){return HIT_CONTEXT.get();}
    private static final class Session {
        final WeaponMove move;final PowerForm form;final long id;final int created;
        final WeaponMove.Input input;final LocalWeaponMotion.Definition definition;
        DevastatorTiming timing;int phase=CHARGE,phaseTick,hits;long released=-1;
        boolean requestedRelease,spent,landed,feedback,bodyRecovered,targetsSelected;float yaw;double highest,peakDown,landingFallSpeed;
        Vec3 origin,direction;LivingEntity target;final Set<UUID> struck=new HashSet<>();
        final Map<Integer,Set<UUID>> directStruck=new HashMap<>();
        final List<TargetPlan> targets=new ArrayList<>();
        Session(ServerPlayer p,WeaponMove move,WeaponMove.Input input){
            this.move=move;this.input=input;form=p.getData(ModAttachments.PLAYER_STATE).getActivePower();
            id=p.level().getGameTime();created=phaseTick=p.tickCount;
            definition=LocalWeaponMotion.find(move.id).orElseThrow();timing=DevastatorTiming.of(move);
            yaw=p.getData(ModAttachments.MOVEMENT_STATE).cameraYaw(p.getYRot());
            direction=Vec3.directionFromRotation(p.getXRot(),yaw).normalize();
            origin=p.position();highest=p.getY();target=CombatTargeting.locked(p);
        }
    }
    public record Snapshot(String move,int phase,boolean spent,int hits,boolean landed,long session,long released,double age,double fallHeight,double peakDown) { }
    private DevastatorSystem() { }
    public static int kind(WeaponMove move){return move.id.contains("groundspike")?0:move.id.contains("tendril")?1:move.id.contains("critical_pain")?2:3;}
    public static boolean busy(ServerPlayer p){var s=ACTIVE.get(p);return s!=null&&!s.bodyRecovered;}
    /** A late absolute flight packet must not undo a real landing. This ends
     * with source body recovery; observer-only tails own no physical position. */
    public static boolean controlsLandingPosition(ServerPlayer p){
        var s=ACTIVE.get(p);return s!=null&&s.landed&&!s.bodyRecovered&&p.isAlive()&&s.phase==LAND;
    }
    public static Snapshot snapshot(ServerPlayer p){var s=ACTIVE.get(p);return s==null?null:new Snapshot(s.move.id,s.phase,s.spent,s.hits,s.landed,s.id,s.released,s.released<0?0:(p.level().getGameTime()-s.released)/20D,Math.max(0,s.highest-(s.landed?s.origin.y:p.getY())),s.peakDown);}
    public static CombatSkill skill(WeaponMove.Input input){return switch(input){case GROUND_POUND->CombatSkill.GROUNDSPIKE_GRAVEYARD;case CLAP->CombatSkill.TENDRIL_BARRAGE;case PAIN->CombatSkill.CRITICAL_PAIN;default->null;};}
    public static WeaponMove move(WeaponMove.Input input,boolean air){return switch(input){
        case GROUND_POUND->air?WeaponMove.DEV_AIR_GROUNDSPIKE:WeaponMove.DEV_GROUNDSPIKE;
        case CLAP->air?WeaponMove.DEV_AIR_TENDRIL:WeaponMove.DEV_TENDRIL;
        case PAIN->air?WeaponMove.DEV_AIR_CRITICAL_PAIN:WeaponMove.DEV_CRITICAL_PAIN;
        default->null;
    };}
    private static boolean eligible(ServerPlayer p){
        var state=p.getData(ModAttachments.PLAYER_STATE);var movement=p.getData(ModAttachments.MOVEMENT_STATE);
        return !com.codex.blacklight.city.HydraTransport.active(p)&&p.isAlive()&&!p.isSpectator()&&!p.isPassenger()&&!p.isFallFlying()&&!p.isInWater()&&!p.isInLava()
                &&state.isInfected()&&state.isPrototypeMode()&&!p.getData(ModAttachments.CONSUMING)
                &&!movement.wallTraversal().attached()&&movement.chargeTicks()==0
                &&WeaponMove.controls(state.getActivePower(),p.getMainHandItem().isEmpty()&&p.getOffhandItem().isEmpty());
    }
    /** True claims only a formal skill or its existing session; ordinary chords
     * retain their old behavior when the upgrade/resource gate is not satisfied. */
    public static boolean acceptInput(ServerPlayer p,boolean attack,boolean special,boolean action,boolean jump){
        if(com.codex.blacklight.city.HydraTransport.active(p)){cancel(p);return false;}
        var keys=new WeaponButtons(attack,special,action,jump);var prior=INPUT.put(p,new Held(keys,p.tickCount));
        var before=prior==null?new WeaponButtons(false,false,false,false):prior.buttons();
        var running=ACTIVE.get(p);
        if(running!=null&&running.bodyRecovered&&keys.pressedSince(before)){cancel(p);running=null;INPUT.put(p,new Held(keys,p.tickCount));}
        if(running!=null){if(!eligible(p)){cancel(p);return true;}
            if(running.phase==CHARGE&&!keys.held(running.input))running.requestedRelease=true;
            return true;
        }
        if(!eligible(p)||!keys.pressedSince(before)||!WeaponCombatSystem.canSkillInterrupt(p))return false;
        var state=p.getData(ModAttachments.PLAYER_STATE);var movement=p.getData(ModAttachments.MOVEMENT_STATE);
        boolean glide=movement.mode()==MovementState.Mode.GLIDE||p.getData(ModAttachments.GLIDE_UNTIL)>p.level().getGameTime();
        if(special&&!attack&&!action&&!p.onGround()&&before.special()!=special)
            com.codex.blacklight.BlacklightNeoMod.LOGGER.debug("Air special routing for {}: glide={}, mode={}, glideUntil={}, now={}, bulletUnlocked={}, power={}",
                    p.getGameProfile().getName(),glide,movement.mode(),p.getData(ModAttachments.GLIDE_UNTIL),p.level().getGameTime(),
                    state.hasCombatSkill(CombatSkill.BULLETDIVE_DROP),state.getActivePower());
        WeaponMove move=null;WeaponMove.Input input=keys.intent();
        if(special&&!attack&&!action&&!p.onGround()&&glide&&state.hasCombatSkill(CombatSkill.BULLETDIVE_DROP)){
            move=WeaponMove.BULLETDIVE_DROP;input=WeaponMove.Input.SPECIAL;
        }else if(input!=null&&input.chord()&&state.hasCombatSkill(skill(input))&&CriticalMassSystem.ready(p))move=move(input,!p.onGround());
        if(move==null||LocalWeaponMotion.find(move.id).isEmpty()||LocalWeaponMotion.find(move.id).orElseThrow().charge().isEmpty())return false;
        WeaponCombatSystem.cancel(p);
        // cancel does not retain uncommitted ordinary input or traversal leases.
        INPUT.put(p,new Held(keys,p.tickCount));
        var s=new Session(p,move,input);ACTIVE.put(p,s);
        com.codex.blacklight.BlacklightNeoMod.LOGGER.debug("Devastator begin {} session={} actor={} y={} onGround={}",
                move.id,s.id,p.getGameProfile().getName(),p.getY(),p.onGround());
        movement.cancelCharge();movement.consumeDashRequest();movement.clearSprintDrive();
        p.setData(ModAttachments.GLIDE_UNTIL,-1L);p.syncData(ModAttachments.GLIDE_UNTIL);
        play(p,s,true,0,-1);publish(p,s);return true;
    }
    private static void play(ServerPlayer p,Session s,boolean charging,float offset,float heldPose){
        long clock=p.level().getGameTime()-(charging?0:Math.round(offset*20));
        p.setData(ModAttachments.WEAPON_VISUAL,new WeaponVisual(s.move.ordinal(),clock,s.yaw,charging,offset,s.target==null?-1:s.target.getId(),heldPose));
        p.syncData(ModAttachments.WEAPON_VISUAL);PlayerActions.get().playWeapon(p,s.move,charging,offset);
        PacketDistributor.sendToPlayer(p,new WeaponAnimationPayload(s.move.ordinal(),charging,offset));
    }
    private static void publish(ServerPlayer p,Session s){
        p.setData(ModAttachments.DEVASTATOR_VISUAL,new DevastatorVisual(kind(s.move),s.id,s.released,s.phase,s.target==null?-1:s.target.getId(),
                s.origin.x,s.origin.y,s.origin.z,(float)s.direction.x,(float)s.direction.y,(float)s.direction.z,s.timing.radius(),s.timing.effectStart(),s.timing.effectEnd(),
                Math.max(0,s.highest-(s.landed?s.origin.y:p.getY())),s.peakDown,
                s.targets.stream().map(plan->new DevastatorVisual.Contact(plan.target().getId(),(float)plan.contact())).toList()));
        p.syncData(ModAttachments.DEVASTATOR_VISUAL);
    }
    @SubscribeEvent public static void tick(PlayerTickEvent.Post event){
        if(!(event.getEntity() instanceof ServerPlayer p))return;var s=ACTIVE.get(p);if(s==null)return;
        // A fall can legitimately take arbitrarily long. Source landing is
        // SupportingSurface-driven; only leaving the world cancels a void dive.
        if(!eligible(p)||p.getData(ModAttachments.PLAYER_STATE).getActivePower()!=s.form
                ||p.getY()<p.level().getMinBuildHeight()-64){cancel(p);return;}
        if(!s.bodyRecovered)p.fallDistance=0;
        s.highest=Math.max(s.highest,p.getY());
        if(s.phase==CHARGE){
            var input=INPUT.get(p);if(input==null||p.tickCount-input.tick()>25){cancel(p);return;}
            if(s.move!=WeaponMove.BULLETDIVE_DROP&&!CriticalMassSystem.ready(p)){cancel(p);return;}
            var charge=s.definition.charge().orElseThrow();int age=p.tickCount-s.phaseTick;
            var velocity=p.getDeltaMovement();p.setDeltaMovement(velocity.x*.25,p.onGround()?velocity.y:Math.max(-.02,velocity.y*.2),velocity.z*.25);p.hurtMarked=true;
            int minimum=charge.minimumTicks()+(int)Math.ceil(s.definition.transitionSeconds()*20-.00001);
            int full=minimum+charge.holdTicks();
            if(s.requestedRelease&&age<full&&s.move!=WeaponMove.BULLETDIVE_DROP){
                var ordinary=s.input;cancel(p);WeaponCombatSystem.request(p,ordinary);return;
            }
            if(age>=minimum&&(s.move==WeaponMove.BULLETDIVE_DROP?s.requestedRelease||age>=full:age>=full)){
                s.phase=RELEASE;s.phaseTick=p.tickCount;s.released=p.level().getGameTime();s.origin=p.position();
                com.codex.blacklight.BlacklightNeoMod.LOGGER.debug("Devastator release {} session={} chargeTicks={} released={}",
                        s.move.id,s.id,age,s.released);
                if(kind(s.move)==2&&s.target!=null&&s.target.isAlive()&&CombatTargeting.visibleWithinRange(p,s.target,s.timing.radius()))
                    s.direction=CombatTargeting.positionNear(p.getEyePosition(),s.target).subtract(p.getEyePosition()).normalize();
                play(p,s,false,charge.releaseTime(),-1);publish(p,s);
            }
            return;
        }
        boolean landing=s.move==WeaponMove.BULLETDIVE_DROP||s.move==WeaponMove.DEV_AIR_GROUNDSPIKE;
        if(landing&&!s.landed){
            var l=s.definition.landing().orElseThrow(()->new IllegalStateException("Missing source landing "+s.move.id));
            if(s.phase==RELEASE){
                // Play the source release/air antic before committing the fall.
                double preparation=s.move==WeaponMove.BULLETDIVE_DROP?1D/6:1D;
                if((p.level().getGameTime()-s.released)/20D<preparation){
                    p.setDeltaMovement(0,p.onGround()?0:-.015,0);p.hurtMarked=true;return;
                }
                s.phase=FLIGHT;s.phaseTick=p.tickCount;
                com.codex.blacklight.BlacklightNeoMod.LOGGER.debug("Devastator flight {} session={} y={} released={} flightTick={}",
                        s.move.id,s.id,p.getY(),s.released,p.level().getGameTime());
                float offset=s.definition.charge().orElseThrow().releaseTime()+(float)((p.level().getGameTime()-s.released)/20D);
                play(p,s,false,offset,l.holdTime());publish(p,s);
            }
            if(p.onGround()&&com.codex.blacklight.movement.TerrainTraversal.supportBelow(p,.05)){
                s.landed=true;s.phase=LAND;s.phaseTick=p.tickCount;s.released=p.level().getGameTime();s.origin=p.position();
                com.codex.blacklight.BlacklightNeoMod.LOGGER.debug("Devastator land {} session={} y={} onGround={} fallHeight={} peakDown={}",
                        s.move.id,s.id,p.getY(),p.onGround(),Math.max(0,s.highest-s.origin.y),s.peakDown);
                if(s.move==WeaponMove.BULLETDIVE_DROP){
                    var terrain=BulletdiveTerrain.begin(p,s.id,s.origin,Math.max(0,s.highest-s.origin.y));
                    s.timing=new DevastatorTiming(s.timing.effectStart(),s.timing.effectEnd(),s.timing.damageStart(),s.timing.damageEnd(),
                            s.timing.targetStart(),s.timing.targetStop(),Math.max(6F,(float)terrain.radius()+2F),s.timing.damage(),s.timing.targets());
                }
                float begin=s.timing.effectStart();s.timing=new DevastatorTiming(0,s.timing.effectEnd()-begin,
                        Math.max(0,s.timing.damageStart()-begin),Math.max(.1F,s.timing.damageEnd()-begin),
                        Math.max(0,s.timing.targetStart()-begin),Math.max(0,s.timing.targetStop()-begin),s.timing.radius(),s.timing.damage(),s.timing.targets());
                play(p,s,false,l.impactTime(),-1);publish(p,s);
            }else{
                int falling=p.tickCount-s.phaseTick;
                double down=s.move==WeaponMove.BULLETDIVE_DROP?BulletdivePhysics.downSpeed(falling):Math.min(5.0,.45+falling*.22);
                s.peakDown=Math.max(s.peakDown,down);
                var horizontal=s.move==WeaponMove.BULLETDIVE_DROP?p.getDeltaMovement().multiply(.65,0,.65):Vec3.ZERO;
                p.setDeltaMovement(horizontal.x,-down,horizontal.z);p.hurtMarked=true;
                if(p.tickCount%2==0)publish(p,s);
                return;
            }
        }
        if(s.landed&&!s.bodyRecovered)commitLandingPosition(p,s);
        if(!s.bodyRecovered&&s.move.aerial()&&!landing&&!p.onGround()){
            p.setDeltaMovement(0,-.015,0);p.hurtMarked=true;
        }else if(!s.bodyRecovered&&p.onGround()){var v=p.getDeltaMovement();p.setDeltaMovement(0,v.y,0);}
        double age=(p.level().getGameTime()-s.released)/20D;
        // Native DevastatorAction starts by sending UsedMessage, then selecting
        // targets. The preceding cinematic antic does not spend health.
        if(!s.spent&&s.move!=WeaponMove.BULLETDIVE_DROP&&age>=DevastatorCollision.spendAt(s.move,s.timing)){
            if(!CriticalMassSystem.spend(p)||!eligible(p)||ACTIVE.get(p)!=s){cancel(p);return;}
            s.spent=true;
        }
        if(kind(s.move)==2&&!s.bodyRecovered&&age<=s.timing.damageEnd())aim(p,s);
        if(!s.targetsSelected&&(s.spent||s.move==WeaponMove.BULLETDIVE_DROP&&s.landed)){
            selectTargets(p,s);s.targetsSelected=true;publish(p,s);
        }
        if(!s.feedback&&age>=s.timing.effectStart()){
            s.feedback=true;CombatImpactEffects.ground(p,s.move,s.direction);
        }
        if(s.move==WeaponMove.BULLETDIVE_DROP||s.spent){
            if(age>=s.timing.damageStart()&&age<=s.timing.damageEnd())strike(p,s,age);
            directStrike(p,s,age);
        }
        float releaseOffset=s.definition.charge().orElseThrow().releaseTime();
        double clip=(p.level().getGameTime()-s.released)/20D+(s.landed?s.definition.landing().orElseThrow().impactTime():releaseOffset);
        if(!s.bodyRecovered&&clip>=s.definition.durationTicks()/20D){
            // Finished source body recovery releases movement immediately;
            // tissue and scene-distortion fade keep only their observer snapshot.
            s.bodyRecovered=true;s.phase=RECOVERY;
            if(p.getData(ModAttachments.WEAPON_VISUAL).move()==s.move.ordinal()){
                p.setData(ModAttachments.WEAPON_VISUAL,WeaponVisual.EMPTY);p.syncData(ModAttachments.WEAPON_VISUAL);NativePlayerActionBackend.clear(p);
            }
            publish(p,s);
        }
        if(age>=DevastatorEffectTimeline.visibleEnd(kind(s.move),s.timing.effectStart(),s.timing.effectEnd())
                &&clip>=s.definition.durationTicks()/20D){cancel(p);}
    }
    private static void aim(ServerPlayer p,Session s){
        var locked=CombatTargeting.locked(p);
        s.target=locked!=null&&CombatTargeting.visibleWithinRange(p,locked,s.timing.radius())?locked:null;
        Vec3 ray=s.target!=null?(CombatTargeting.multipart(s.target)?CombatTargeting.closestPoint(p.getEyePosition(),s.target):s.target.getBoundingBox().getCenter())
                .subtract(p.getEyePosition()).normalize():Vec3.directionFromRotation(p.getXRot(),p.getData(ModAttachments.MOVEMENT_STATE).cameraYaw(p.getYRot()));
        if(ray.distanceToSqr(s.direction)<.000001&&p.tickCount%10!=0)return;
        s.direction=ray;s.yaw=(float)Math.toDegrees(Math.atan2(-ray.x,ray.z));
        var v=p.getData(ModAttachments.WEAPON_VISUAL);
        p.setData(ModAttachments.WEAPON_VISUAL,new WeaponVisual(v.move(),v.started(),s.yaw,v.charging(),v.clipOffset(),s.target==null?-1:s.target.getId(),v.travelPose(),v.attackPose()));
        p.syncData(ModAttachments.WEAPON_VISUAL);publish(p,s);
    }
    private static void commitLandingPosition(ServerPlayer p,Session s){
        boolean support=com.codex.blacklight.movement.TerrainTraversal.supportBelow(p,.01);
        s.landingFallSpeed=support?.08:(s.landingFallSpeed+.08)*.98;
        // The server now owns the recovery collision result. If the crater
        // removes support, keep real downward gravity instead of hovering at
        // the old impact point. No source Coord Y is added to the actor.
        p.setDeltaMovement(Vec3.ZERO);
        p.move(net.minecraft.world.entity.MoverType.SELF,new Vec3(0,-s.landingFallSpeed,0));
        p.setOnGround(com.codex.blacklight.movement.TerrainTraversal.supportBelow(p,.01));
        p.connection.resetPosition();
        if(p.serverLevel().players().contains(p))p.serverLevel().getChunkSource().move(p);
        var action=p.getData(ModAttachments.PLAYER_ACTION);
        PacketDistributor.sendToPlayer(p,new com.codex.blacklight.network.NativeRootMotionPayload(
                p.level().getGameTime(),action.revision(),p.getX(),p.getY(),p.getZ()));
    }
    private static void strike(ServerPlayer p,Session s,double age){
        int kind=kind(s.move);double radius=s.timing.radius();
        for(var plan:s.targets){
            var target=plan.target();
            if(s.struck.size()>=s.timing.targets())break;
            if(s.struck.contains(target.getUUID())||!CombatTargeting.damageable(p,target)||!CombatTargeting.visibleWithinRange(p,target,radius))continue;
            var point=kind==2?(CombatTargeting.multipart(target)?CombatTargeting.closestPoint(p.getEyePosition(),target):target.getBoundingBox().getCenter())
                    :CombatTargeting.positionNear(s.origin,target);
            double distance=point.distanceTo(kind==2?p.getEyePosition():s.origin);
            if(distance>radius)continue;
            double contact=plan.contact();
            if(kind==2){var delta=point.subtract(p.getEyePosition());double along=delta.dot(s.direction);
                if(along<0||delta.subtract(s.direction.scale(along)).length()>Math.max(1.25,target.getBbWidth()*.65))continue;
                // Source target-control duration bounds this MC travel driver.
                // Adapt acceleration for distant targets so the declared reach
                // remains usable within the finite source effect lease.
            }
            if(age<contact||age>Math.min(s.timing.damageEnd(),contact+DevastatorCollision.targetDuration(s.move)))continue;
            float damage=s.timing.damage();
            if(kind==3)damage=BulletdivePhysics.impactDamage(damage,s.highest-s.origin.y);
            if(hurt(p,target,damage,s.move==WeaponMove.BULLETDIVE_DROP,new HitContext("target_control",0,contact,damage))){
                s.struck.add(target.getUUID());s.hits++;var outward=point.subtract(s.origin).multiply(1,0,1);
                if(outward.lengthSqr()<.0001)outward=s.direction;else outward=outward.normalize();
                ImpactControl.begin(target,new ImpactProfile(kind==0?HitReaction.LAUNCH:HitReaction.HEAVY,kind==0?ImpactProfile.UP:ImpactProfile.FALL,40,.6,kind==0?.8:.15,true),s.yaw);
                if(!(target instanceof com.codex.blacklight.entity.BlacklightInfectedHost host&&host.giantBoss())
                        &&!ConsumeSystem.grappleProtected(target)){
                    double resistance=target.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE);
                    target.setDeltaMovement(outward.scale(.6*(1-resistance)).add(0,(kind==0?.8:.15)*(1-resistance),0));target.hurtMarked=true;
                }
                CombatImpactEffects.target(p,target,s.move);
            }
        }
    }
    private static void selectTargets(ServerPlayer p,Session s){
        int kind=kind(s.move);double radius=s.timing.radius();
        var candidates=kind==2?(s.target==null?new ArrayList<LivingEntity>():new ArrayList<>(List.of(s.target))):
                CombatTargeting.candidates(p,p.getBoundingBox().inflate(radius),t->CombatTargeting.damageable(p,t));
        candidates.sort(CombatTargeting.contactPriority(p));
        for(var target:candidates){
            if(s.targets.size()>=s.timing.targets())break;
            if(!CombatTargeting.damageable(p,target)||!CombatTargeting.visibleWithinRange(p,target,radius))continue;
            var point=kind==2?(CombatTargeting.multipart(target)?CombatTargeting.closestPoint(p.getEyePosition(),target):target.getBoundingBox().getCenter()):CombatTargeting.positionNear(s.origin,target);
            double distance=point.distanceTo(kind==2?p.getEyePosition():s.origin);if(distance>radius)continue;
            if(kind==0&&!graveContactSupported(p,target,point))continue;
            double contact=kind==2?Math.max(s.timing.targetStart(),s.timing.effectStart()+
                    Math.min(distance/(30*DevastatorTiming.SOURCE_WORLD_SCALE),DevastatorCollision.targetDuration(s.move))):s.timing.contactAt(distance);
            s.targets.add(new TargetPlan(target,contact));
        }
    }
    private static boolean graveContactSupported(ServerPlayer p,LivingEntity target,Vec3 point){
        var hit=p.level().clip(new net.minecraft.world.level.ClipContext(point.add(0,3,0),point.add(0,-8,0),
                net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,p));
        // Every authored variant reaches at least 1.8 blocks above support.
        // Do not damage a high airborne actor with a groundspike that cannot
        // physically reach it. Independent native direct volumes remain separate.
        return hit.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK&&hit.getDirection()==net.minecraft.core.Direction.UP
                &&point.y>=hit.getLocation().y-.1&&point.y-hit.getLocation().y<=1.8;
    }
    private static boolean hurt(ServerPlayer p,LivingEntity target,float damage,boolean bulletdive,HitContext context){
        var prior=HIT_CONTEXT.get();HIT_CONTEXT.set(context);
        try{return target.hurt(bulletdive?BulletdiveDamage.source(p):p.damageSources().playerAttack(p),damage);}
        finally{if(prior==null)HIT_CONTEXT.remove();else HIT_CONTEXT.set(prior);}
    }
    private static void directStrike(ServerPlayer p,Session s,double age){
        double active=age-DevastatorCollision.activeStart(s.move,s.timing);
        for(var track:DevastatorCollision.tracks(s.move)){
            if(active+1e-6<track.begin()||active>track.end()+1e-6)continue;
            double scale=DevastatorTiming.SOURCE_WORLD_SCALE,radius=track.radius()*scale;
            var forward=Vec3.directionFromRotation(0,s.yaw);
            var center=p.position().add(0,track.offsetY()*scale,0).add(forward.scale(track.forwardOffset()*scale));
            // Motion_Root uses the source actor frame; MC's flat player uses
            // its torso anchor for the same pitched capsule. This root bridge
            // is explicit adaptation, not a recovered native joint callback.
            if(track.extendingCapsule())center=p.position().add(0,p.getBbHeight()*.58,0);
            final var capsuleOrigin=center;
            var end=track.extendingCapsule()?center.add(s.direction.scale((active-track.begin())*track.travelVelocity())):center;
            var struck=s.directStruck.computeIfAbsent(track.sourceTrack(),ignored->new HashSet<>());
            var candidates=CombatTargeting.candidates(p,new net.minecraft.world.phys.AABB(center,end).inflate(radius),t->CombatTargeting.damageable(p,t));
            candidates.sort(CombatTargeting.contactPriority(p));
            for(var target:candidates){
                if(struck.size()>=64)break;
                double contactDistance=track.extendingCapsule()?CombatTargeting.hitboxes(target).stream()
                        .mapToDouble(box->DevastatorCollision.capsuleDistanceSquared(capsuleOrigin,end,box)).min().orElse(Double.POSITIVE_INFINITY)
                        :CombatTargeting.distanceSquared(center,target);
                if(struck.contains(target.getUUID())||contactDistance>radius*radius
                        ||!CombatTargeting.visibleWithinRange(p,target,radius+center.distanceTo(end)+Math.abs(track.offsetY())*scale))continue;
                var point=CombatTargeting.closestPoint(center,target);
                var delta=point.subtract(center).multiply(1,0,1);
                if(track.arc()>0&&track.arc()<360&&delta.lengthSqr()>.000001
                        &&delta.normalize().dot(forward)<Math.cos(Math.toRadians(track.arc()/2)))continue;
                float damage=track.damage()*.04F;
                if(hurt(p,target,damage,s.move==WeaponMove.BULLETDIVE_DROP,new HitContext("direct_"+track.sourceTrack(),track.sourceTrack(),
                        DevastatorCollision.activeStart(s.move,s.timing)+track.begin(),damage))){
                    struck.add(target.getUUID());s.hits++;CombatImpactEffects.target(p,target,s.move);
                }
            }
        }
    }
    public static void cancel(ServerPlayer p){
        INPUT.remove(p);var s=ACTIVE.remove(p);if(s==null)return;
        p.setData(ModAttachments.DEVASTATOR_VISUAL,DevastatorVisual.EMPTY);p.syncData(ModAttachments.DEVASTATOR_VISUAL);
        if(p.getData(ModAttachments.WEAPON_VISUAL).move()==s.move.ordinal()){
            p.setData(ModAttachments.WEAPON_VISUAL,WeaponVisual.EMPTY);p.syncData(ModAttachments.WEAPON_VISUAL);
            NativePlayerActionBackend.clear(p);
        }
        if(!s.bodyRecovered)p.fallDistance=0;
    }
    @SubscribeEvent public static void death(LivingDeathEvent e){if(e.getEntity() instanceof ServerPlayer p)cancel(p);}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p)cancel(p);}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p)cancel(p);}
}
