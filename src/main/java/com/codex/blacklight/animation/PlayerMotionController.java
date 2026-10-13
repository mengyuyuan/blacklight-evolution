package com.codex.blacklight.animation;

import com.codex.blacklight.BlacklightNeoMod;
import com.codex.blacklight.client.ClientMovementEvents;
import com.codex.blacklight.client.ClientWallMotion;
import com.codex.blacklight.client.PrototypeAnimationController;
import com.codex.blacklight.movement.*;
import com.codex.blacklight.network.NativeRootMotionPayload;
import com.codex.blacklight.registry.ModAttachments;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Independent player clocks, action/locomotion arbitration and seekable preview. Never applies combat damage. */
@EventBusSubscriber(modid = BlacklightNeoMod.MOD_ID, value = Dist.CLIENT)
public final class PlayerMotionController {
    private static final Map<LivingEntity, Runtime> STATES = new WeakHashMap<>();
    private static Object level;
    private static long lastRootTick = Long.MIN_VALUE, lastRootRevision = Long.MIN_VALUE;
    static final class Clock {
        String key;
        double at;
        float seconds;
        boolean paused, reverse;
        float time(double now) {
            // Render passes may ask for another partial tick in either order.
            // Sampling must never move the command's time origin or cursor.
            return paused ? seconds : Math.max(0,
                    seconds + (float)Math.max(0, now - at) / 20 * (reverse ? -1 : 1));
        }
        void commit(double now) {
            seconds=time(now);at=now;
        }
        boolean finished(double now,boolean looping,float duration) {
            float time=time(now);
            return !paused&&(reverse?time<=.0001F:!looping&&time>=duration);
        }
    }
    private static final class Runtime {
        Clock override, preview;
        long revision = -1, suppressedRevision = -1;
        String locomotion;
        LocomotionPhase phase;
        long diagnosticsAt=Long.MIN_VALUE;
        long changed;
        boolean contactStrideTransition;
        net.minecraft.world.phys.Vec3 committedPosition;
        final LocomotionClock gait=new LocomotionClock();
        final ChargePoseClock charge=new ChargePoseClock();
        final CombatPoseTransition combat=new CombatPoseTransition();
        final CommittedPoseInterval interval=new CommittedPoseInterval();
        final CombatFacingTransition facing=new CombatFacingTransition();
        BlacklightPose from,last;
    }
    private PlayerMotionController() {}
    public static BlacklightSkeleton skeleton() { return BlacklightSkeleton.get(); }
    private static Runtime state(LivingEntity player) { return STATES.computeIfAbsent(player, ignored -> new Runtime()); }
    private static double now(LivingEntity entity, float partial) { return entity.level().getGameTime() + (double)partial; }
    private static void reconcileAction(LivingEntity entity,Runtime runtime) {
        long revision=entity.getData(ModAttachments.PLAYER_ACTION).revision();
        if(runtime.revision!=revision){runtime.revision=revision;runtime.override=null;}
    }

    public static void request(LivingEntity player, PlayerAction action, float offset) { request(player, MotionLibrary.resolve(action), offset); }
    public static void request(LivingEntity player, String key, float offset) {
        if (player == null) return;
        key = MotionLibrary.canonical(key); MotionLibrary.clip(key);
        var runtime=state(player);reconcileAction(player,runtime);
        var action=player.getData(ModAttachments.PLAYER_ACTION);
        var weapon=player.getData(ModAttachments.WEAPON_VISUAL);
        // A live weapon phase owns its clock even after the initial finite action
        // lease expires during pursuit, an airborne hold or a charged pose.
        if(key.equals(WeaponMotionClock.actionKey(weapon))
                ||key.equals(action.clipId())&&action.active(now(player,0))) {
            runtime.suppressedRevision=-1;
            if(runtime.override!=null&&!key.equals(runtime.override.key))runtime.override=null;
            return;
        }
        var clock = new Clock(); clock.key = key; clock.at = now(player, 0); clock.seconds = Math.max(0, offset);
        runtime.override = clock; runtime.revision = action.revision();
    }
    public static boolean isPlaying(LivingEntity player, PlayerAction action) { return isPlaying(player, MotionLibrary.resolve(action)); }
    public static boolean isPlaying(LivingEntity player, String key) {
        if (player == null) return false;
        var runtime = state(player); var current = clockKey(player, runtime);
        return MotionLibrary.canonical(key).equals(current) && !ended(player);
    }
    private static String clockKey(LivingEntity player,Runtime runtime) {
        reconcileAction(player,runtime);
        if(runtime.preview!=null)return runtime.preview.key;
        if(runtime.override!=null)return runtime.override.key;
        var weapon=WeaponMotionClock.sample(player.getData(ModAttachments.WEAPON_VISUAL),now(player,0));
        if(weapon!=null)return weapon.key();
        var action=player.getData(ModAttachments.PLAYER_ACTION);
        return action.revision()!=runtime.suppressedRevision&&action.active(now(player,0))?action.clipId():runtime.locomotion;
    }
    public static float elapsed(LivingEntity player){return elapsed(player,0);}
    public static float elapsed(LivingEntity player,float partial) {
        if(player==null)return 0;
        var runtime=state(player);reconcileAction(player,runtime);
        var clock=runtime.preview!=null?runtime.preview:runtime.override;
        if(clock!=null)return clock.time(now(player,partial));
        var weapon=WeaponMotionClock.sample(player.getData(ModAttachments.WEAPON_VISUAL),now(player,partial));
        if(weapon!=null)return weapon.seconds();
        var action=player.getData(ModAttachments.PLAYER_ACTION);
        return action.revision()!=runtime.suppressedRevision&&action.active(now(player,partial))
                ?action.seconds(now(player,partial)):(float)runtime.gait.seconds(partial);
    }
    public static float duration(PlayerAction action) { return duration(MotionLibrary.resolve(action)); }
    public static float duration(String key) { return MotionLibrary.duration(key); }
    public static boolean ended(LivingEntity player) {
        if(player==null)return true;
        var runtime=state(player);reconcileAction(player,runtime);
        var clock=runtime.preview!=null?runtime.preview:runtime.override;
        if(clock!=null){
            float time=clock.time(now(player,0));
            if(clock.paused)return false;
            return clock.reverse?time<=.0001F:!MotionLibrary.looping(clock.key)&&time>=duration(clock.key)-.0001F;
        }
        // A travel/landing/charge hold finishes when the authoritative visual is
        // cleared, rather than when its original one-shot animation lease elapsed.
        if(player.getData(ModAttachments.WEAPON_VISUAL).spec()!=null)return false;
        String key=clockKey(player,runtime);
        return key==null||!MotionLibrary.looping(key)&&elapsed(player)>=duration(key)-.0001F;
    }
    private static Clock mutableClock(LivingEntity player) {
        var runtime=state(player);reconcileAction(player,runtime);
        if(runtime.preview!=null)return runtime.preview;
        if(runtime.override==null){
            String key=clockKey(player,runtime);if(key==null)key="basic_idle";
            float seconds=elapsed(player);
            var clock=new Clock();clock.key=key;clock.at=now(player,0);clock.seconds=seconds;
            runtime.override=clock;
        }
        return runtime.override;
    }
    public static void seek(LivingEntity player, float seconds) {
        if (player == null) return;
        if (!Float.isFinite(seconds)) throw new IllegalArgumentException("Non-finite seek time");
        var clock = mutableClock(player); clock.seconds = Math.clamp(seconds, 0, duration(clock.key)); clock.at = now(player, 0);
    }
    public static void pause(LivingEntity player, boolean paused) {
        if (player == null) return;
        var clock = mutableClock(player); clock.commit(now(player, 0)); clock.paused = paused;
    }
    public static void reverse(LivingEntity player, boolean reversed) {
        if (player == null) return;
        var clock = mutableClock(player); clock.commit(now(player, 0)); clock.reverse = reversed;
    }
    public static void clear(LivingEntity player) {
        if (player == null) return;
        var runtime = state(player); runtime.override = null;
        runtime.suppressedRevision = player.getData(ModAttachments.PLAYER_ACTION).revision();
    }
    public static void preview(LivingEntity player, String key, float seconds) {
        if (player == null) return;
        var clock = new Clock(); clock.key = MotionLibrary.canonical(key); clock.seconds = Math.clamp(seconds, 0, duration(key));
        clock.at = now(player, 0); clock.paused = true; state(player).preview = clock;
    }
    /** Selected paused editor/QA clip; real movement has no preview key. */
    public static String previewKey(LivingEntity entity) {
        var runtime=entity==null?null:STATES.get(entity);
        return runtime==null||runtime.preview==null?null:runtime.preview.key;
    }
    public static void seekPreview(LivingEntity player, float seconds) { if (player != null && state(player).preview != null) seek(player, seconds); }
    public static void clearPreview(LivingEntity player) { if (player != null) state(player).preview = null; }

    private static BlacklightPose samplePose(String key, float time, boolean loop) {
        var pose = MotionLibrary.pose(key, time, loop);
        var action = key.equals("legacy_claw_groundspike")
                ? com.codex.blacklight.client.tuning.AnimationPoseSettings.Action.GROUND_SPIKE
                : key.equals("basic_jump_charge")
                ? com.codex.blacklight.client.tuning.AnimationPoseSettings.Action.CHARGED_JUMP : null;
        if (action != null) com.codex.blacklight.client.tuning.AnimationPoseSettings.apply(action, pose, time);
        return pose;
    }

    public static BlacklightPose pose(LivingEntity entity, float partial) {
        var runtime=state(entity);
        if(runtime.preview!=null)return utilityPose(entity,partial);
        var raw=runtime.interval.ready()?runtime.interval.sample(partial):utilityPose(entity,partial);
        return runtime.combat.sample(raw,partial);
    }
    public static float facing(LivingEntity entity,float partial) {
        var facing=state(entity).facing;
        return facing.ready()?facing.sample(partial)
                :com.codex.blacklight.client.animation.BlacklightRenderPose.rawFacing(entity,partial);
    }
    private static BlacklightPose utilityPose(LivingEntity entity,float partial) {
        var pose=rawPose(entity,partial);
        com.codex.blacklight.client.animation.NativeUtilityPose.apply(entity,pose,partial);
        return pose;
    }
    private static BlacklightPose rawPose(LivingEntity entity, float partial) {
        var runtime = state(entity); double now = now(entity, partial);
        reconcileAction(entity,runtime);
        var action=entity.getData(ModAttachments.PLAYER_ACTION);
        BlacklightPose base = locomotion(entity, runtime, now, partial);
        if (runtime.preview != null) return samplePose(runtime.preview.key, runtime.preview.time(now), false);
        if(runtime.phase==LocomotionPhase.CHARGING&&entity.getData(ModAttachments.WEAPON_VISUAL).spec()==null)
            return base;
        var activeWeapon=entity.getData(ModAttachments.WEAPON_VISUAL).spec();
        if(activeWeapon!=null&&(activeWeapon.id.startsWith("dev_")||activeWeapon==com.codex.blacklight.combat.WeaponMove.BULLETDIVE_DROP))
            runtime.override=null; // The synchronized skill owns airborne holds and aim blending.
        if (runtime.override != null) {
            var clock = runtime.override;
            float time = clock.time(now);
            // An end sample does not retire the clock: another pass can ask for
            // an earlier partial in this same interval. Retirement owns a tick.
            if (!clock.finished(now,MotionLibrary.looping(clock.key),duration(clock.key))) {
                var sampled=samplePose(clock.key,time,MotionLibrary.looping(clock.key));
                if(LandingMotionPolicy.isLanding(clock.key)){
                    if(!entity.onGround())return base;
                    NativeLandingPose.stripHorizontalRoot(sampled);
                    if(movingGrounded(entity,runtime)&&LandingMotionPolicy.permitsStride(clock.key,time))
                        return NativeLandingPose.blendUpper(base,sampled,LandingMotionPolicy.movingCushionWeight(time));
                }
                return sampled;
            }
        }
        var weapon=WeaponMotionClock.sample(entity.getData(ModAttachments.WEAPON_VISUAL),now);
        if(weapon!=null){
            var sampled=samplePose(weapon.key(),weapon.seconds(),weapon.looping());
            var dev=entity.getData(ModAttachments.DEVASTATOR_VISUAL);
            if(dev.kind()==2&&!entity.getData(ModAttachments.WEAPON_VISUAL).charging()){
                // The decoded vertical-aim track supplies actual up/down clips.
                // Blend their source poses using the authoritative release ray.
                var direction=dev.direction();
                float weight=(float)Math.clamp(Math.abs(Math.atan2(direction.y,direction.horizontalDistance()))/(Math.PI/2),0,1);
                String variant=weapon.key()+(direction.y>=0?"_aim_up":"_aim_down");
                if(weight>.001F&&MotionLibrary.find(variant).isPresent())
                    sampled=BlacklightPose.interpolatePose(sampled,samplePose(variant,weapon.seconds(),false),weight);
            }
            return SourceClawRecovery.apply(weapon.key(),weapon.seconds(),sampled);
        }
        if (action.revision() != runtime.suppressedRevision && action.active(now)
                && !isLocomotionAction(action.clipId())) {
            float actionSeconds=action.seconds(now);
            var sampled=samplePose(action.clipId(),actionSeconds,action.looping());
            if(LandingMotionPolicy.isLanding(action.clipId())) {
                // An old landing packet cannot plant a character who has jumped again.
                if(!entity.onGround())return base;
                NativeLandingPose.stripHorizontalRoot(sampled);
                if(movingGrounded(entity,runtime)&&LandingMotionPolicy.permitsStride(action.clipId(),actionSeconds))
                    return NativeLandingPose.blendUpper(base,sampled,LandingMotionPolicy.movingCushionWeight(actionSeconds)
                            *Math.clamp((float)((now-action.started())/20/Math.max(.001,action.transition())),0,1));
            }
            boolean legacyClawCombo=action.clipId().equals("legacy_claw_auto1")||action.clipId().equals("legacy_claw_auto2")||action.clipId().equals("legacy_claw_auto3");
            if(legacyClawCombo&&sampled.hasTransform("Root")) {
                // Authoritative entity Coord supplies the horizontal lunge once.
                // The visual root may still carry the authored crouch and rotation.
                sampled.get("Root").translation().x=0;sampled.get("Root").translation().z=0;
            }
            boolean walking=entity.getDeltaMovement().horizontalDistance()>.018
                    ||PrototypeAnimationController.phaseFor(entity).isGroundMovement();
            if((action.clipId().equals("transform_claws")||legacyClawCombo)&&entity.onGround()&&walking) {
                var upper=base.copy();
                if(legacyClawCombo&&sampled.hasTransform("Root"))upper.getJointTransformData().put("Root",sampled.get("Root").copy());
                for(String joint:new String[]{"Torso","Chest","Head","Shoulder_R","Arm_R","Elbow_R","Hand_R","Tool_R","Shoulder_L","Arm_L","Elbow_L","Hand_L","Tool_L"})
                    if(sampled.hasTransform(joint))upper.getJointTransformData().put(joint,sampled.get(joint).copy());
                sampled=upper;
            }
            float blend = Math.clamp((float)((now - action.started()) / 20 / Math.max(.001, action.transition())), 0, 1);
            return action.clipId().startsWith("legacy_claw_") ? sampled : BlacklightPose.interpolatePose(base, sampled, blend);
        }
        return base;
    }
    private static boolean movingGrounded(LivingEntity entity,Runtime runtime) {
        return entity.onGround()&&(runtime.phase!=null&&runtime.phase.isGroundMovement()
                ||entity.getDeltaMovement().horizontalDistance()>.018
                ||LocomotionVisualState.get(entity.getId()).speed()>.018);
    }
    private static boolean isLocomotionAction(String key) {
        return key.startsWith("basic_") && !key.equals("basic_jump_charge") && !key.startsWith("basic_land_") && !key.startsWith("basic_air_dash");
    }
    /** Reduce the double session clock before converting to the float clip API. */
    static float loopSampleTime(double seconds,float duration) {
        return duration<=0?0:(float)(Math.max(0,seconds)%duration);
    }
    private static void commitLocomotion(LivingEntity entity,Runtime runtime) {
        reconcileAction(entity,runtime);
        if(runtime.override!=null) {
            var clock=runtime.override;
            if(clock.finished(now(entity,0),MotionLibrary.looping(clock.key),duration(clock.key)))runtime.override=null;
        }
        // World time can be corrected backwards by a server time packet. Entity
        // ticks own local interpolation; a time correction must not freeze gait.
        long tick=entity.tickCount;
        if(runtime.gait.key()!=null&&tick<=runtime.gait.tick())return;
        runtime.charge.commit(entity==Minecraft.getInstance().player?ClientMovementEvents.visualJumpCharge()
                :entity.getData(ModAttachments.JUMP_CHARGE),MovementTuningSettings.jumpChargeTicks());
        var previous=runtime.last;
        var velocity=entity.getDeltaMovement();
        var travel=runtime.committedPosition==null?net.minecraft.world.phys.Vec3.ZERO:entity.position().subtract(runtime.committedPosition);
        // Teleports and large corrections do not become an authored foot cycle.
        if(travel.length()>Math.max(4,velocity.length()*3+1))travel=net.minecraft.world.phys.Vec3.ZERO;
        runtime.committedPosition=entity.position();
        double ticks=runtime.gait.key()==null?1:Math.max(1,tick-runtime.gait.tick());
        double distance=travel.horizontalDistance();
        String key="basic_idle";boolean ground=false;
        double speed=distance/ticks;
        WallTraversal.Mode wall=WallTraversal.Mode.NONE;
        LocomotionPhase phase=LocomotionPhase.IDLE;
        if(entity instanceof AbstractClientPlayer player) {
            phase=PrototypeAnimationController.phaseFor(player);
            if(!com.codex.blacklight.mode.PrototypeModeSystem.isActive(player))
                phase=entity.onGround()?(speed>.018?player.isSprinting()?LocomotionPhase.RUN:LocomotionPhase.WALK:LocomotionPhase.IDLE)
                        :velocity.y>.035?LocomotionPhase.RISE:LocomotionPhase.FALL;
            var snapshot=LocomotionVisualState.get(entity.getId());
            if(runtime.gait.key()==null)speed=Math.max(velocity.horizontalDistance(),Math.max(0,snapshot.speed()));
            wall=ClientWallMotion.modeFor(player);
            double right=TraversalMotionSelector.rightward(velocity.x,velocity.z,entity.yBodyRot);
            var surface=WallPoseState.get(entity);
            if(surface!=null)right=velocity.x*surface.normalZ()-velocity.z*surface.normalX();
            if(wall!=WallTraversal.Mode.NONE) {
                double traveledRight=surface==null?TraversalMotionSelector.rightward(travel.x,travel.z,entity.yBodyRot)
                        :travel.x*surface.normalZ()-travel.z*surface.normalX();
                distance=Math.hypot(travel.y,traveledRight);
                speed=distance/ticks;
                if(distance>1e-8){right=traveledRight/ticks;velocity=new net.minecraft.world.phys.Vec3(velocity.x,travel.y/ticks,velocity.z);}
                key=switch(wall) {
                    case RUN,CLIMB -> TraversalMotionSelector.wall(wall==WallTraversal.Mode.RUN,velocity.y,right);
                    case CLING -> "basic_wall_cling";case LEAP -> "basic_wall_leap";case MANTLE -> "basic_wall_mantle";
                    case VAULT -> "basic_wall_vault";case TOP -> "basic_wall_top";case PARKOUR -> "basic_parkour_small";
                    default -> "basic_fall";
                };
            } else if(!entity.onGround()&&player.getData(ModAttachments.GLIDE_UNTIL)>=entity.level().getGameTime()) {
                double yaw=Math.toRadians(entity.yBodyRot);
                key=TraversalMotionSelector.glide(-velocity.x*Math.sin(yaw)+velocity.z*Math.cos(yaw),right);
            } else {
                var currentAction=entity.getData(ModAttachments.PLAYER_ACTION);
                if(entity.onGround()&&speed>.018&&phase.isLanding()
                        &&LandingMotionPolicy.permitsStride(currentAction.clipId(),currentAction.seconds(now(entity,0))))
                    phase=snapshot.sprint()||player.isSprinting()||speed>=PrototypeLocomotion.RUN_ANIMATION_SPEED?LocomotionPhase.RUN:LocomotionPhase.WALK;
                ground=phase.isGroundMovement();
                key=ground?TraversalMotionSelector.stableGround(phase,snapshot.sprint(),speed,runtime.locomotion)
                        :switch(phase) {
                            case CHARGING -> "basic_jump_charge";
                            // A rising one-shot chooses its identity once. Decelerating
                            // vertical speed must not restart it as another jump clip.
                            case JUMP_LAUNCH,RISE -> runtime.phase==phase&&runtime.locomotion!=null
                                    &&runtime.locomotion.startsWith("basic_jump_")?runtime.locomotion
                                    :speed>.3?"basic_jump_running":velocity.y<.8?"basic_jump_tactical":"basic_jump_rise";
                            case FALL -> "basic_fall";case LAND_LIGHT -> "basic_land_light";
                            case LAND_HEAVY -> "basic_land_heavy";case LAND_DEVASTATING -> "basic_land_devastating";
                            default -> switch(player.getData(ModAttachments.PLAYER_STATE).getActivePower()) {
                                case CLAWS -> "basic_idle_claw";case BLADE -> "basic_idle_blade";
                                case HAMMERFISTS -> "basic_idle_hammer";case WHIPFIST -> "basic_idle_whip";default -> "basic_idle";
                            };
                        };
            }
        }
        boolean groundContactStride=ground&&runtime.phase!=null
                &&(runtime.phase==LocomotionPhase.FALL||runtime.phase==LocomotionPhase.RISE
                ||runtime.phase==LocomotionPhase.JUMP_LAUNCH||runtime.phase.isLanding());
        runtime.phase=phase;
        boolean sameFamily=ground||runtime.locomotion!=null&&(key.startsWith("basic_wall_run")&&runtime.locomotion.startsWith("basic_wall_run")
                ||key.startsWith("basic_wall_climb")&&runtime.locomotion.startsWith("basic_wall_climb")
                ||key.startsWith("basic_glide")&&runtime.locomotion.startsWith("basic_glide"));
        double nominal=MotionLibrary.nominalSpeed(key)*entity.getScale();
        if(runtime.gait.commit(tick,key,duration(key),MotionLibrary.looping(key),sameFamily,distance,nominal)) {
            runtime.from=previous;runtime.changed=tick;runtime.contactStrideTransition=groundContactStride;
        }
        runtime.locomotion=key;
        runtime.last=renderLocomotion(entity,runtime,1).copy();
        if(Boolean.getBoolean("blacklight.test.motionDiagnostics")&&entity==Minecraft.getInstance().player
                &&(runtime.diagnosticsAt==Long.MIN_VALUE||tick-runtime.diagnosticsAt>=20)) {
            runtime.diagnosticsAt=tick;
            double cadence=nominal>0?Math.min(8,speed/nominal):1;
            BlacklightNeoMod.LOGGER.info("Native committed motion phase={} key={} wall={} time={} distance={} speed={} nominal={} cadence={} action={} weapon={}",
                    phase,key,wall,runtime.gait.current(),distance,speed,nominal,cadence,
                    entity.getData(ModAttachments.PLAYER_ACTION).clipId(),entity.getData(ModAttachments.WEAPON_VISUAL).move());
        }
    }
    private static BlacklightPose locomotion(LivingEntity entity,Runtime runtime,double now,float partial) {
        if(runtime.gait.key()==null) {
            // Initial rendering can precede the first client tick. Establish one
            // idle interval; subsequent render passes cannot advance or reroute it.
            runtime.gait.commit(entity.tickCount,"basic_idle",duration("basic_idle"),true,false,0,0);
            runtime.locomotion="basic_idle";runtime.committedPosition=entity.position();
        }
        return renderLocomotion(entity,runtime,partial);
    }
    private static BlacklightPose renderLocomotion(LivingEntity entity,Runtime runtime,float partial) {
        String key=runtime.gait.key();
        boolean looping=runtime.gait.looping();
        float clipDuration=runtime.gait.duration();
        float time=runtime.gait.sampleTime(partial);
        if (key.equals("basic_jump_charge")) {
            time = duration(key)*runtime.charge.ratio(partial,MovementTuningSettings.jumpChargeTicks());
        }
        var pose=samplePose(key,time,looping);
        if(looping&&clipDuration>0) {
            float cycle=time%clipDuration,window=Math.min(.06F,clipDuration*.25F);
            if(cycle>clipDuration-window)pose=BlacklightPose.interpolatePose(pose,samplePose(key,0,true),(cycle-clipDuration+window)/window);
        } else if((runtime.phase==LocomotionPhase.RISE||runtime.phase==LocomotionPhase.JUMP_LAUNCH)
                &&runtime.gait.seconds(partial)>clipDuration) {
            float age=(float)(runtime.gait.seconds(partial)-clipDuration);
            var airborne=samplePose("basic_fall",age,true);
            pose=BlacklightPose.interpolatePose(pose,airborne,Math.clamp(age/.16F,0,1));
        }
        double blendAge=runtime.gait.tick()-runtime.changed+(double)Math.clamp(partial,0,1);
        if(runtime.from!=null&&blendAge<2.4) {
            float weight=(float)Math.clamp(blendAge/2.4,0,1);
            // Ground contact gives the legs the new stride immediately. Cushioning
            // may blend the upper body, never the previous airborne planted feet.
            pose=runtime.contactStrideTransition?NativeLandingPose.blendUpper(pose,runtime.from,1-weight)
                    :BlacklightPose.interpolatePose(runtime.from,pose,weight);
        }
        return pose;
    }
    private record WeaponPoseIdentity(int move,long started,String key) {}
    private record LegacyPoseIdentity(long revision,String key) {}
    private static void commitCombat(LivingEntity entity,Runtime runtime) {
        var visual=entity.getData(ModAttachments.WEAPON_VISUAL);
        var sample=WeaponMotionClock.sample(visual,now(entity,0));
        Object identity=sample==null?null:new WeaponPoseIdentity(visual.move(),visual.started(),sample.key());
        double transition=sample==null?2.4:Math.max(1,MotionLibrary.transition(sample.key())*20);
        var action=entity.getData(ModAttachments.PLAYER_ACTION);
        if(sample==null&&action.revision()!=runtime.suppressedRevision&&action.active(now(entity,0))
                &&action.clipId().startsWith("legacy_claw_")) {
            identity=new LegacyPoseIdentity(action.revision(),action.clipId());
            transition=Math.max(1,action.transition()*20);
        }
        runtime.facing.commit(identity,entity.tickCount,
                com.codex.blacklight.client.animation.BlacklightRenderPose.rawFacing(entity,0),
                com.codex.blacklight.client.animation.BlacklightRenderPose.rawFacing(entity,1),transition);
        runtime.interval.commit(entity.tickCount,partial->utilityPose(entity,(float)partial));
        runtime.combat.commit(identity,entity.tickCount,renderLocomotion(entity,runtime,0),runtime.interval.sample(1),transition);
    }
    public static void acceptRootMotion(NativeRootMotionPayload packet) {
        var minecraft = Minecraft.getInstance(); var player = minecraft.player;
        if (player == null || packet.gameTime() < lastRootTick || packet.gameTime() == lastRootTick && packet.revision() <= lastRootRevision) return;
        if (packet.revision() < player.getData(ModAttachments.PLAYER_ACTION).revision()) return;
        lastRootTick = packet.gameTime(); lastRootRevision = packet.revision();
        // Keep previous-tick coordinates for render interpolation and preserve the player's freely moving view.
        player.setPos(packet.x(), packet.y(), packet.z());
        com.codex.blacklight.client.ClientDevastatorMovement.acceptRoot(player);
    }
    @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.LOWEST)
    public static void tick(ClientTickEvent.Post event) {
        var current = Minecraft.getInstance().level;
        if (level != current) { STATES.clear(); level = current; lastRootTick = Long.MIN_VALUE; lastRootRevision = Long.MIN_VALUE; }
        STATES.keySet().removeIf(entity -> entity.isRemoved() || entity.level() != current);
        if(current==null)return;
        // Input prediction and phase snapshots run at normal priority. Commit the
        // resulting displacement once, after them, before any renderer queries it.
        for(var player:current.players()){commitLocomotion(player,state(player));commitCombat(player,state(player));}
        for(var entity:java.util.List.copyOf(STATES.keySet()))
            if(!(entity instanceof AbstractClientPlayer)){commitLocomotion(entity,state(entity));commitCombat(entity,state(entity));}
    }
}
