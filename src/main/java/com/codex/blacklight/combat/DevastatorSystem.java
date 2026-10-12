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
    public static final int CHARGE=0, RELEASE=1, FLIGHT=2, LAND=3;
    private record Held(WeaponButtons buttons,int tick) { }
    private static final Map<ServerPlayer,Held> INPUT=new WeakHashMap<>();
    private static final Map<ServerPlayer,Session> ACTIVE=new WeakHashMap<>();
    private static final class Session {
        final WeaponMove move;final PowerForm form;final long id;final int created;
        final WeaponMove.Input input;final LocalWeaponMotion.Definition definition;
        DevastatorTiming timing;int phase=CHARGE,phaseTick,hits;long released=-1;
        boolean requestedRelease,spent,landed,feedback;float yaw;double highest,peakDown;
        Vec3 origin,direction;LivingEntity target;final Set<UUID> struck=new HashSet<>();
        Session(ServerPlayer p,WeaponMove move,WeaponMove.Input input){
            this.move=move;this.input=input;form=p.getData(ModAttachments.PLAYER_STATE).getActivePower();
            id=p.level().getGameTime();created=phaseTick=p.tickCount;
            definition=LocalWeaponMotion.find(move.id).orElseThrow();timing=DevastatorTiming.of(move);
            yaw=p.getData(ModAttachments.MOVEMENT_STATE).cameraYaw(p.getYRot());
            direction=Vec3.directionFromRotation(p.getXRot(),yaw).normalize();
            origin=p.position();highest=p.getY();target=CombatTargeting.locked(p);
        }
    }
    public record Snapshot(String move,int phase,boolean spent,int hits,boolean landed,long session,long released,double age) { }
    private DevastatorSystem() { }
    public static int kind(WeaponMove move){return move.id.contains("groundspike")?0:move.id.contains("tendril")?1:move.id.contains("critical_pain")?2:3;}
    public static boolean busy(ServerPlayer p){return ACTIVE.containsKey(p);}
    public static Snapshot snapshot(ServerPlayer p){var s=ACTIVE.get(p);return s==null?null:new Snapshot(s.move.id,s.phase,s.spent,s.hits,s.landed,s.id,s.released,s.released<0?0:(p.level().getGameTime()-s.released)/20D);}
    public static CombatSkill skill(WeaponMove.Input input){return switch(input){case GROUND_POUND->CombatSkill.GROUNDSPIKE_GRAVEYARD;case CLAP->CombatSkill.TENDRIL_BARRAGE;case PAIN->CombatSkill.CRITICAL_PAIN;default->null;};}
    public static WeaponMove move(WeaponMove.Input input,boolean air){return switch(input){
        case GROUND_POUND->air?WeaponMove.DEV_AIR_GROUNDSPIKE:WeaponMove.DEV_GROUNDSPIKE;
        case CLAP->air?WeaponMove.DEV_AIR_TENDRIL:WeaponMove.DEV_TENDRIL;
        case PAIN->air?WeaponMove.DEV_AIR_CRITICAL_PAIN:WeaponMove.DEV_CRITICAL_PAIN;
        default->null;
    };}
    private static boolean eligible(ServerPlayer p){
        var state=p.getData(ModAttachments.PLAYER_STATE);var movement=p.getData(ModAttachments.MOVEMENT_STATE);
        return p.isAlive()&&!p.isSpectator()&&!p.isPassenger()&&!p.isFallFlying()&&!p.isInWater()&&!p.isInLava()
                &&state.isInfected()&&state.isPrototypeMode()&&!p.getData(ModAttachments.CONSUMING)
                &&!movement.wallTraversal().attached()&&movement.chargeTicks()==0
                &&WeaponMove.controls(state.getActivePower(),p.getMainHandItem().isEmpty()&&p.getOffhandItem().isEmpty());
    }
    /** True claims only a formal skill or its existing session; ordinary chords
     * retain their old behavior when the upgrade/resource gate is not satisfied. */
    public static boolean acceptInput(ServerPlayer p,boolean attack,boolean special,boolean action,boolean jump){
        var keys=new WeaponButtons(attack,special,action,jump);var prior=INPUT.put(p,new Held(keys,p.tickCount));
        var before=prior==null?new WeaponButtons(false,false,false,false):prior.buttons();
        var running=ACTIVE.get(p);
        if(running!=null){if(!eligible(p)){cancel(p);return true;}
            if(running.phase==CHARGE&&!keys.held(running.input))running.requestedRelease=true;
            return true;
        }
        if(!eligible(p)||!keys.pressedSince(before)||!WeaponCombatSystem.canSkillInterrupt(p))return false;
        var state=p.getData(ModAttachments.PLAYER_STATE);var movement=p.getData(ModAttachments.MOVEMENT_STATE);
        boolean glide=movement.mode()==MovementState.Mode.GLIDE||p.getData(ModAttachments.GLIDE_UNTIL)>p.level().getGameTime();
        WeaponMove move=null;WeaponMove.Input input=keys.intent();
        if(special&&!attack&&!action&&!p.onGround()&&glide&&state.hasCombatSkill(CombatSkill.BULLETDIVE_DROP)){
            move=WeaponMove.BULLETDIVE_DROP;input=WeaponMove.Input.SPECIAL;
        }else if(input!=null&&input.chord()&&state.hasCombatSkill(skill(input))&&CriticalMassSystem.ready(p))move=move(input,!p.onGround());
        if(move==null||LocalWeaponMotion.find(move.id).isEmpty()||LocalWeaponMotion.find(move.id).orElseThrow().charge().isEmpty())return false;
        WeaponCombatSystem.cancel(p);
        // cancel does not retain uncommitted ordinary input or traversal leases.
        INPUT.put(p,new Held(keys,p.tickCount));
        var s=new Session(p,move,input);ACTIVE.put(p,s);
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
                s.origin.x,s.origin.y,s.origin.z,(float)s.direction.x,(float)s.direction.y,(float)s.direction.z,s.timing.radius(),s.timing.effectStart(),s.timing.effectEnd()));
        p.syncData(ModAttachments.DEVASTATOR_VISUAL);
    }
    @SubscribeEvent public static void tick(PlayerTickEvent.Post event){
        if(!(event.getEntity() instanceof ServerPlayer p))return;var s=ACTIVE.get(p);if(s==null)return;
        if(!eligible(p)||p.getData(ModAttachments.PLAYER_STATE).getActivePower()!=s.form||p.tickCount-s.created>600){cancel(p);return;}
        p.fallDistance=0;s.highest=Math.max(s.highest,p.getY());
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
                if(s.move!=WeaponMove.BULLETDIVE_DROP){if(!CriticalMassSystem.spend(p)){cancel(p);return;}s.spent=true;}
                s.phase=RELEASE;s.phaseTick=p.tickCount;s.released=p.level().getGameTime();s.origin=p.position();
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
                float offset=s.definition.charge().orElseThrow().releaseTime()+(float)((p.level().getGameTime()-s.released)/20D);
                play(p,s,false,offset,l.holdTime());publish(p,s);
            }
            if(p.onGround()){
                s.landed=true;s.phase=LAND;s.phaseTick=p.tickCount;s.released=p.level().getGameTime();s.origin=p.position();
                if(s.move==WeaponMove.BULLETDIVE_DROP){
                    var terrain=BulletdiveTerrain.begin(p,s.id,s.origin,Math.max(0,s.highest-s.origin.y));
                    s.timing=new DevastatorTiming(s.timing.effectStart(),s.timing.effectEnd(),s.timing.damageStart(),s.timing.damageEnd(),
                            s.timing.targetStart(),s.timing.targetStop(),Math.max(6,terrain.radius()+2),s.timing.damage(),s.timing.targets());
                }
                float begin=s.timing.effectStart();s.timing=new DevastatorTiming(0,s.timing.effectEnd()-begin,
                        Math.max(0,s.timing.damageStart()-begin),Math.max(.1F,s.timing.damageEnd()-begin),
                        Math.max(0,s.timing.targetStart()-begin),Math.max(0,s.timing.targetStop()-begin),s.timing.radius(),s.timing.damage(),s.timing.targets());
                play(p,s,false,l.impactTime(),-1);publish(p,s);
            }else{
                // Derived .87 source-space conversion is capped for MC collision.
                int falling=p.tickCount-s.phaseTick;double down=Math.min(5.0,.45+falling*.22);
                s.peakDown=Math.max(s.peakDown,down);
                var horizontal=s.move==WeaponMove.BULLETDIVE_DROP?p.getDeltaMovement().multiply(.65,0,.65):Vec3.ZERO;
                p.setDeltaMovement(horizontal.x,-down,horizontal.z);p.hurtMarked=true;
                if(falling>200){cancel(p);return;}return;
            }
        }
        if(s.move.aerial()&&!landing&&!p.onGround()){
            p.setDeltaMovement(0,-.015,0);p.hurtMarked=true;
        }else if(p.onGround()){var v=p.getDeltaMovement();p.setDeltaMovement(0,v.y,0);}
        double age=(p.level().getGameTime()-s.released)/20D;
        if(kind(s.move)==2&&age<=s.timing.effectEnd())aim(p,s);
        if(!s.feedback&&age>=s.timing.effectStart()){
            s.feedback=true;CombatImpactEffects.ground(p,s.move,s.direction);
        }
        if(age>=s.timing.damageStart()&&age<=s.timing.damageEnd())strike(p,s,age);
        float releaseOffset=s.definition.charge().orElseThrow().releaseTime();
        double clip=(p.level().getGameTime()-s.released)/20D+(s.landed?s.definition.landing().orElseThrow().impactTime():releaseOffset);
        if(age>=s.timing.effectEnd()&&clip>=s.definition.durationTicks()/20D){cancel(p);}
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
    private static void strike(ServerPlayer p,Session s,double age){
        int kind=kind(s.move);double radius=s.timing.radius();
        var candidates=CombatTargeting.candidates(p,p.getBoundingBox().inflate(radius),t->CombatTargeting.damageable(p,t));
        candidates.sort(kind==2?Comparator.comparingInt((LivingEntity t)->t==s.target?0:1)
                .thenComparingDouble(t->CombatTargeting.distanceSquared(p.getEyePosition(),t)):CombatTargeting.contactPriority(p));
        for(var target:candidates){
            if(s.struck.size()>=s.timing.targets())break;
            if(s.struck.contains(target.getUUID())||!CombatTargeting.visibleWithinRange(p,target,radius))continue;
            var point=kind==2?(CombatTargeting.multipart(target)?CombatTargeting.closestPoint(p.getEyePosition(),target):target.getBoundingBox().getCenter())
                    :CombatTargeting.positionNear(s.origin,target);
            double distance=point.distanceTo(kind==2?p.getEyePosition():s.origin);
            if(distance>radius)continue;
            if(kind==2){var delta=point.subtract(p.getEyePosition());double along=delta.dot(s.direction);
                if(along<0||delta.subtract(s.direction.scale(along)).length()>Math.max(1.25,target.getBbWidth()*.65))continue;
                // Source target-control duration bounds this MC travel driver.
                // Adapt acceleration for distant targets so the declared reach
                // remains usable within the finite source effect lease.
                double travel=Math.min(distance/(30*DevastatorTiming.SOURCE_WORLD_SCALE),s.move.aerial()?1.666667:1.333333);
                if(age<s.timing.effectStart()+travel)continue;
            }else if(age<s.timing.contactAt(distance))continue;
            float damage=s.timing.damage();
            if(kind==3)damage*=Math.clamp(1+(float)(s.highest-s.origin.y)/30F,1,3);
            if(target.hurt(p.damageSources().playerAttack(p),damage)){
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
    public static void cancel(ServerPlayer p){
        INPUT.remove(p);var s=ACTIVE.remove(p);if(s==null)return;
        p.setData(ModAttachments.DEVASTATOR_VISUAL,DevastatorVisual.EMPTY);p.syncData(ModAttachments.DEVASTATOR_VISUAL);
        if(p.getData(ModAttachments.WEAPON_VISUAL).move()==s.move.ordinal()){
            p.setData(ModAttachments.WEAPON_VISUAL,WeaponVisual.EMPTY);p.syncData(ModAttachments.WEAPON_VISUAL);
            NativePlayerActionBackend.clear(p);
        }
        p.fallDistance=0;
    }
    @SubscribeEvent public static void death(LivingDeathEvent e){if(e.getEntity() instanceof ServerPlayer p)cancel(p);}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p)cancel(p);}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p)cancel(p);}
}
