package com.codex.blacklight.combat;

import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.animation.PlayerAction;
import com.codex.blacklight.animation.PlayerActions;
import com.codex.blacklight.network.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.*;
import net.minecraft.sounds.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent.PlayerTickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import com.codex.blacklight.forge.PacketDistributor;
import java.util.*;

/** Separate weapon actions; server owns each contact and never trusts a client target. */
public final class WeaponCombatSystem {
    private static final int COMBO_GRACE_TICKS=10;
    private record Chain(int tick,WeaponMove move,com.codex.blacklight.power.PowerForm form,LivingEntity target){}
    private static final Map<ServerPlayer,Session> ACTIVE=new HashMap<>();
    private static final Map<ServerPlayer,Chain> CHAINS=new WeakHashMap<>();
    private record Buttons(WeaponButtons keys,int updated){boolean held(WeaponMove.Input input){return keys.held(input);}}
    private static final Map<ServerPlayer,Buttons> BUTTONS=new WeakHashMap<>();
    private static final Map<ServerPlayer,Integer> AIR_HITS=new WeakHashMap<>();
    private record LastContact(UUID owner,long time){}
    private static final Map<LivingEntity,LastContact> LAST_CONTACT=new WeakHashMap<>();
    private static final class Session {
        final WeaponMove move;int started;final int created;float yaw,previousYaw;Vec3 direction;float clipOffset;double pursuitTravel;
        final com.codex.blacklight.power.PowerForm form;
        final Map<UUID,Integer> closeSweepHits=new HashMap<>();
        final Set<UUID> hit=new HashSet<>();boolean effect,charging,released,queuedHeld,jumpQueued,tookOff,lifted,impactEffect,groundEffect,locked;
        WeaponMove.Input input,queued;int landed=-1,contact=-1,flight=-1,travelStarted,travelBudget,stuckTicks;boolean pounceCommitted,traveling;float chargePower;double travelSpeed;Vec3 lastTargetPosition;
        Vec3 previousFeet;LivingEntity target,queuedTarget;final Map<UUID,List<AABB>> previousTargets=new HashMap<>();
        int mobilityStarted=-1;boolean mobilityFinished;Vec3 releaseDirection;double ballisticY;
        final Set<net.minecraft.core.BlockPos> terrainContacts=new HashSet<>();
        Session(ServerPlayer p,WeaponMove m,float offset,float heading){move=m;clipOffset=offset;started=p.tickCount-Math.round(offset*20);created=p.tickCount;form=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE).getActivePower();yaw=heading;previousYaw=yaw;direction=Vec3.directionFromRotation(0,yaw);previousFeet=p.position();}
    }
    private WeaponCombatSystem(){}
    public static boolean groundSpikeGrace(ServerPlayer p){
        var s=ACTIVE.get(p);if(s==null||!s.move.groundSpike()||s.charging||s.traveling)return false;
        int age=p.tickCount-s.started;return age>=0&&age<s.move.start&&p.tickCount-s.created<=s.move.start+2;
    }
    public static boolean busy(ServerPlayer p){return ACTIVE.containsKey(p)||DevastatorSystem.busy(p);}
    /** A form swap cancels only a weapon-owned action, never a grab or devastator. */
    public static boolean canSwitchForm(ServerPlayer p){
        return !DevastatorSystem.busy(p) && (ACTIVE.containsKey(p)
                || ModAttachments.getData(p,ModAttachments.CLAW_COMBAT).action()==ClawCombatState.Action.IDLE);
    }
    /** Remove old contacts/charge/queued combo; airborne combo budget intentionally survives. */
    public static void prepareFormSwitch(ServerPlayer p){
        cancel(p);BUTTONS.remove(p);
    }
    public static boolean canSkillInterrupt(ServerPlayer p){var s=ACTIVE.get(p);return s==null||s.hit.isEmpty()&&(s.charging||p.tickCount-s.created<=2);}
    /** Physical traversal input remains available even while EF suppresses Input.jumping. */
    public static void movementInput(ServerPlayer p,boolean jumpPressed){
        var s=ACTIVE.get(p);
        if(s==null||!jumpPressed||s.move.landingAttack())return;
        s.jumpQueued=true;
        if(s.charging&&p.onGround())jumpOut(p,s);
    }
    private static void jumpOut(ServerPlayer p,Session s){
        var queued=s.queued;var keys=BUTTONS.get(p);
        boolean held=queued!=null&&s.queuedHeld&&keys!=null&&keys.held(queued);
        cancel(p);p.setOnGround(false);p.setDeltaMovement(s.direction.scale(.22).add(0,.75,0));p.hurtMarked=true;
        if(queued!=null)beginInput(p,WeaponMove.choose(s.form,queued,true,false,false),queued,held);
    }
    private static boolean canRequest(ServerPlayer p){
        if(com.codex.blacklight.power.BiomassSystem.active(p)||com.codex.blacklight.city.HydraTransport.active(p))return false;
        if(DevastatorSystem.busy(p))return false;
        var state=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE);var movement=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.MOVEMENT_STATE);
        return p.isAlive()&&!p.isSpectator()&&!p.isPassenger()&&state.isInfected()&&state.isPrototypeMode()&&WeaponMove.controls(state.getActivePower(),p.getMainHandItem().isEmpty()&&p.getOffhandItem().isEmpty())&&!com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.CONSUMING)&&!movement.wallTraversal().attached()&&movement.chargeTicks()==0;

    }
    public static void request(ServerPlayer p,boolean special){
        request(p,special?WeaponMove.Input.SPECIAL:WeaponMove.Input.ATTACK);
    }
    public static void requestSpecial(ServerPlayer p){request(p,WeaponMove.Input.SPECIAL);}
    public static void acceptInput(ServerPlayer p,boolean attack,boolean special){
        acceptInput(p,attack,special,false,false);
    }
    public static void acceptInput(ServerPlayer p,boolean attack,boolean special,boolean action,boolean jump){
        if(com.codex.blacklight.power.BiomassSystem.active(p)||com.codex.blacklight.city.HydraTransport.active(p))return;
        if(DevastatorSystem.acceptInput(p,attack,special,action,jump))return;
        var empty=new WeaponButtons(false,false,false,false);
        var old=BUTTONS.getOrDefault(p,new Buttons(empty,p.tickCount));
        var keys=new WeaponButtons(attack,special,action,jump);
        BUTTONS.put(p,new Buttons(keys,p.tickCount));
        if(!canRequest(p)){if(ACTIVE.containsKey(p)&&ACTIVE.get(p).charging)cancel(p);return;}
        var active=ACTIVE.get(p);
        if(active!=null&&jump&&!old.keys.jump()&&!active.move.landingAttack())active.jumpQueued=true;
        var intent=keys.intent();
        // A chord entered during startup replaces that uncommitted press exactly once.
        if(intent!=null&&intent.chord()&&intent!=old.keys.intent()&&active!=null
                &&!active.input.chord()&&(active.charging||p.tickCount-active.created<=2)
                &&active.hit.isEmpty()){
            cancel(p);requestInternal(p,intent,true);return;
        }
        if(active!=null&&active.charging&&!keys.held(active.input))active.released=true;
        if(intent!=null&&keys.pressedSince(old.keys)&&!(old.keys.intent()!=null&&old.keys.intent().chord()))requestInternal(p,intent,true);
    }
    public static void cancelHeldInput(ServerPlayer p){
        DevastatorSystem.cancel(p);
        BUTTONS.remove(p);
        var active=ACTIVE.get(p);if(active!=null&&active.charging)cancel(p);
    }
    public static void request(ServerPlayer p,WeaponMove.Input input){requestInternal(p,input,false);}
    private static void requestInternal(ServerPlayer p,WeaponMove.Input input,boolean held){
        if(!canRequest(p))return;
        var state=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE);
        var active=ACTIVE.get(p);
        if(active!=null){
            var followUp=active.move.followUp(input,state.getActivePower(),!p.onGround(),p.isSprinting());
            if((!active.charging||active.released)&&followUp!=null&&p.tickCount-active.started>=active.move.comboBufferTick()){
                active.queued=input;active.queuedHeld=held;
                active.queuedTarget=null;active.queuedTarget=acquireTarget(p,followUp);
            }
            return;
        }
        if(com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.CLAW_COMBAT).action()!=ClawCombatState.Action.IDLE)return;
        var last=CHAINS.get(p);
        WeaponMove move=null;
        if(last!=null&&last.form==state.getActivePower()&&p.tickCount-last.tick<COMBO_GRACE_TICKS)
            move=last.move.followUp(input,state.getActivePower(),!p.onGround(),p.isSprinting());
        if(move==null)move=WeaponMove.choose(state.getActivePower(),input,!p.onGround(),p.isSprinting(),false);
        beginInput(p,move,input,held);
    }
    private static void beginInput(ServerPlayer p,WeaponMove move,WeaponMove.Input input,boolean held){
        if(move==null)return;
        if(!move.accepts(com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE).getActivePower()))return;
        if(move.airCombo()){
            if(AIR_HITS.getOrDefault(p,0)>=4)return;
            AIR_HITS.merge(p,1,Integer::sum);
        }
        boolean charge=held&&LocalWeaponMotion.find(move.id).flatMap(LocalWeaponMotion.Definition::charge).isPresent();
        start(p,move,charge,input,0);
        var buttons=BUTTONS.get(p);
        if(charge&&(buttons==null||!buttons.held(input)))ACTIVE.get(p).released=true;
    }
    private static void start(ServerPlayer p,WeaponMove move,boolean charging,WeaponMove.Input input,float clipOffset){
        var target=acquireTarget(p,move);
        float heading=requestedYaw(p,move);
        if(target!=null) {
            var delta=CombatTargeting.positionNear(p.position(),target).subtract(p.position());
            heading=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z));
        }
        if(p.onGround()&&(LocalWeaponMotion.find(move.id).isPresent()||WeaponMotionProfile.forMove(move)!=null)){
            // An attack begins from its own step curve, without inherited sprint inertia.
            p.setDeltaMovement(0,p.getDeltaMovement().y,0);p.xxa=0;p.zza=0;p.hurtMarked=true;
        }
        Session s=new Session(p,move,clipOffset,heading);s.charging=charging;s.input=input;s.releaseDirection=CombatMotion.flightDirection(s.yaw,p.getXRot());ACTIVE.put(p,s);CHAINS.remove(p);com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.MOVEMENT_STATE).cancelLandingRecovery();
        if(CombatMotion.stationaryWhip(move)&&!p.onGround()){p.setDeltaMovement(Vec3.ZERO);p.hurtMarked=true;}
        s.target=target;s.locked=target!=null&&CombatTargeting.locked(p)==target;rememberTargets(p,s);
        s.lastTargetPosition=target==null?null:target.position();
        s.traveling=CombatMotion.canApproach(move)&&!charging&&target!=null&&travelDistance(p,target)>Math.max(AttackReach.reach(p,move)+1.65,CombatMotion.standOff(move)+1.1);
        s.travelStarted=p.tickCount;s.travelBudget=CombatMotion.travelBudget(target==null?0:travelDistance(p,target),CombatMotion.speed(move,0));
        com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.CLAW_COMBAT).begin(ClawCombatState.Action.TRANSFORM);
        com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.WEAPON_VISUAL,new WeaponVisual(move.ordinal(),p.level().getGameTime()-Math.round(clipOffset*20),s.yaw,charging,clipOffset,target==null?-1:target.getId(),s.traveling?CombatMotion.travelPose(move):-1));com.codex.blacklight.registry.ModAttachments.syncData(p, ModAttachments.WEAPON_VISUAL);
        PlayerActions.get().playWeapon(p,move,charging,clipOffset);
        PacketDistributor.sendToPlayer(p,new WeaponAnimationPayload(move.ordinal(),charging,clipOffset));
    }
    private static boolean directionalInput(ServerPlayer p,WeaponMove move){
        return CombatMotion.usesDirectionalInput(move)&&com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.MOVEMENT_STATE).wallInput().moving();
    }
    private static double travelDistance(ServerPlayer player,LivingEntity target){
        return CombatTargeting.multipart(target)?Math.sqrt(CombatTargeting.distanceSquared(player.getEyePosition(),target)):player.distanceTo(target);
    }
    private static float requestedYaw(ServerPlayer p,WeaponMove move){
        var input=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.MOVEMENT_STATE).wallInput();
        return directionalInput(p,move)?CombatPursuit.inputYaw(com.codex.blacklight.movement.TraversalSteering.cameraYaw(p),input.forward(),input.sideways()):p.getYRot();
    }
    private static double inputAimScore(ServerPlayer p,WeaponMove move,LivingEntity target){
        if(!directionalInput(p,move))return CombatTargeting.aimScore(p,target);
        var delta=CombatTargeting.positionNear(p.position(),target).subtract(p.position()).multiply(1,0,1);
        return delta.lengthSqr()<.0001?1:Vec3.directionFromRotation(0,requestedYaw(p,move)).dot(delta.normalize());
    }
    private static LivingEntity acquireTarget(ServerPlayer p,WeaponMove move){
        // Ground swings only assist within their contact reach plus the bounded
        // local step. Locking a distant actor must not turn a punch into autorun.
        double range=CombatMotion.canApproach(move)?CombatTargeting.viewRange(p):AttackReach.reach(p,move)+1.65;
        java.util.function.Predicate<LivingEntity> eligible=v->CombatTargeting.selectable(p,v)
                &&CombatTargeting.inReach(p,v,range)&&CombatTargeting.visible(p,v);
        var locked=CombatTargeting.locked(p);
        if(locked!=null)return eligible.test(locked)?locked:null;
        var active=ACTIVE.get(p);
        // Releasing a held press retains its victim. New presses / combo steps
        // use the user's nearest-in-front policy, not an invented native score.
        if(active!=null&&active.charging&&active.target!=null&&eligible.test(active.target)
                &&(!directionalInput(p,move)||inputAimScore(p,move,active.target)>.5))return active.target;
        // A buffered press chooses when it is entered, before the preceding
        // motion can carry the player past that victim.
        if(active!=null&&active.queuedTarget!=null&&eligible.test(active.queuedTarget)
                &&(!directionalInput(p,move)||inputAimScore(p,move,active.queuedTarget)>.5))return active.queuedTarget;
        return CombatTargeting.candidates(p,p.getBoundingBox().inflate(range),eligible).stream()
                .filter(v->!v.isInvisibleTo(p)&&inputAimScore(p,move,v)>.5)
                .min(Comparator.comparingDouble((LivingEntity v)->CombatTargeting.distanceSquared(p.getEyePosition(),v))
                        .thenComparingDouble(v->-inputAimScore(p,move,v)).thenComparingInt(Entity::getId)).orElse(null);
    }
    /** Both logical sides arrest the whip strike before travel applies gravity.
     * No persistent noGravity flag can leak into the next action/life. */
    @SubscribeEvent public static void beforeTravel(PlayerTickEvent event){
        if (!com.codex.blacklight.registry.ModAttachments.available(event.player)) { if (event.player instanceof net.minecraft.server.level.ServerPlayer removed) { cancel(removed); BUTTONS.remove(removed); AIR_HITS.remove(removed); } return; }
        if(event.phase != net.minecraftforge.event.TickEvent.Phase.START)return;
        holdWhip(event.player);
    }
    @SubscribeEvent public static void afterTravel(PlayerTickEvent event){
        if (!com.codex.blacklight.registry.ModAttachments.available(event.player)) { if (event.player instanceof net.minecraft.server.level.ServerPlayer removed) { cancel(removed); BUTTONS.remove(removed); AIR_HITS.remove(removed); } return; }
        if(event.phase != net.minecraftforge.event.TickEvent.Phase.END)return;
        holdWhip(event.player);
    }
    private static void holdWhip(Player player){
        // Removal can invalidate capabilities during Player.tick before END fires.
        if(!ModAttachments.available(player))return;
        var visual=com.codex.blacklight.registry.ModAttachments.getData(player, ModAttachments.WEAPON_VISUAL);var move=visual.spec();
        if(!CombatMotion.stationaryWhip(move)||player.onGround()||!player.isAlive()
                ||player.isSpectator()||player.isPassenger()||player.getAbilities().flying
                ||player.isInWater()||player.isInLava()||player.isFallFlying())return;
        var state=com.codex.blacklight.registry.ModAttachments.getData(player, ModAttachments.PLAYER_STATE);
        if(!state.isInfected()||!state.isPrototypeMode()||state.getActivePower()!=move.form)return;
        long elapsed=player.level().getGameTime()-visual.started();
        // Bounded MC adaptation: anticipation/charge/strike suspend; recovery falls.
        if(elapsed<0||elapsed>=(visual.charging()?60:move.end))return;
        player.setDeltaMovement(Vec3.ZERO);player.resetFallDistance();
    }
    @SubscribeEvent public static void tick(PlayerTickEvent event){
        if (!com.codex.blacklight.registry.ModAttachments.available(event.player)) { if (event.player instanceof net.minecraft.server.level.ServerPlayer removed) { cancel(removed); BUTTONS.remove(removed); AIR_HITS.remove(removed); } return; }
        if(event.phase != net.minecraftforge.event.TickEvent.Phase.END)return;
        if(!(event.player instanceof ServerPlayer p))return;
        if(com.codex.blacklight.city.HydraTransport.active(p)){cancel(p);cancelHeldInput(p);return;}
        if(p.onGround())AIR_HITS.remove(p);
        var s=ACTIVE.get(p);if(s==null)return;
        var state=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE);
        if(!p.isAlive()||p.isSpectator()||!state.isPrototypeMode()||state.getActivePower()!=s.form||com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.CONSUMING)
                ||!WeaponMove.controls(s.form,p.getMainHandItem().isEmpty()&&p.getOffhandItem().isEmpty())){cancel(p);return;}
        if(CombatMotion.stationaryWhip(s.move)&&(p.isInWater()||p.isInLava()||p.isPassenger()||p.getAbilities().flying)){cancel(p);return;}
        int t=p.tickCount-s.started;
        if(s.target!=null){
            // A teleport is not ordinary target motion; do not chase stale IDs.
            if(s.lastTargetPosition!=null&&s.target.position().distanceToSqr(s.lastTargetPosition)>64){cancel(p);return;}
            s.lastTargetPosition=s.target.position();
        }
        if(s.traveling){
            tickTravel(p,s);
            if(ACTIVE.get(p)!=s)return;
            s.previousFeet=p.position();s.previousYaw=s.yaw;rememberTargets(p,s);
            return;
        }
        steerPreparation(p,s,t);
        pursue(p,s,t);
        if(s.move.aerial()&&!CombatMotion.stationaryWhip(s.move)&&s.move!=WeaponMove.CLAW_POUNCE
                &&!p.onGround()&&!s.move.landingAttack()&&t<s.move.end
                &&(s.charging||airTargetAvailable(p,s))
                &&!(CombatMotion.freeFlight(s.move)&&s.target==null&&!s.charging)){
            var v=p.getDeltaMovement();
            if(airTargetAvailable(p,s)&&!s.charging&&t<s.move.end&&s.hit.isEmpty()){
                var delta=CombatTargeting.positionNear(p.position(),s.target).subtract(p.position());double length=delta.horizontalDistance();
                double speed=Math.min(CombatMotion.speed(s.move,s.chargePower),Math.max(0,length-1.1)*.35);var horizontal=length>.001?delta.multiply(1,0,1).scale(speed/length):Vec3.ZERO;
                p.setDeltaMovement(horizontal.x,com.codex.blacklight.util.MathCompat.clamp(delta.y*.35,-.95,.7),horizontal.z);
            }else p.setDeltaMovement(v.x*.85,Math.max(v.y,-.04),v.z*.85);
            p.hurtMarked=true;
        }
        if(s.charging){
            if(s.move.aerial()&&!s.move.landingAttack()&&p.onGround()){cancel(p);return;}
            if(s.move.aerial()&&!CombatMotion.stationaryWhip(s.move)&&!p.onGround()){
                var velocity=p.getDeltaMovement();
                double cap=CombatMotion.brakeHorizontal(s.move);
                var horizontal=velocity.multiply(1,0,1).scale(.5);
                if(horizontal.length()>cap)horizontal=horizontal.normalize().scale(cap);
                double gravity=CombatMotion.brakeGravity(s.move,velocity.y>0);
                p.setDeltaMovement(horizontal.x,Math.max(velocity.y-gravity,-.02),horizontal.z);p.fallDistance=0;p.hurtMarked=true;
            }
            var buttons=BUTTONS.get(p);
            if(buttons==null||p.tickCount-buttons.updated()>25){cancel(p);return;}
            var definition=LocalWeaponMotion.find(s.move.id).orElseThrow();
            var charge=definition.charge().orElseThrow();
            int minimum=charge.minimumTicks()+(int)Math.ceil(definition.transitionSeconds()*20-.00001);
            boolean full=t>=minimum+charge.holdTicks();
            if(t>=minimum&&(s.released||full)){
                var move=s.move;
                float releaseTime=charge.releaseTime();
                if(full&&charge.chargedMove().isPresent()){
                    String id=charge.chargedMove().orElseThrow();
                    move=Arrays.stream(WeaponMove.values()).filter(candidate->candidate.id.equals(id)).findFirst()
                            .orElseThrow(()->new IllegalStateException("Unknown charged move: "+id));
                }
                if(full&&p.onGround()&&com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.MOVEMENT_STATE).wallInput().forward()<0&&hasLaunchTarget(p)){
                    var launcher=chargedLauncher(s.move);
                    if(launcher!=null){move=launcher;releaseTime=0;}
                }
                start(p,move,false,s.input,releaseTime);
                var released=ACTIVE.get(p);released.jumpQueued=s.jumpQueued;released.queued=s.queued;released.queuedHeld=s.queuedHeld;released.queuedTarget=s.queuedTarget;
                // Some source moves strengthen the same release clip instead of
                // selecting a second animation. Preserve their actual held charge.
                if(move==WeaponMove.AIR_KICK||move==WeaponMove.AIR_PAIN||move==WeaponMove.CLAW_POUNCE||move==WeaponMove.BLADE_DIVE||move==WeaponMove.HAMMER_DROP||move==WeaponMove.HAMMER_RAM||move==WeaponMove.BLADE_RUSH)
                    released.chargePower=(float)com.codex.blacklight.util.MathCompat.clamp((t-minimum)/(double)charge.holdTicks(),0,1);
            }
            return;
        }
        if(CombatMotion.freeFlight(s.move)&&s.target==null&&!s.mobilityFinished&&t>=s.move.start){
            tickFreeFlight(p,s);return;
        }
        if(s.move==WeaponMove.BLADE_RUSH){
            double speed=CombatMotion.bladeRushSpeed(s.chargePower,t);
            p.setDeltaMovement(s.direction.scale(speed).add(0,p.getDeltaMovement().y,0));p.hurtMarked=true;
        }
        if(s.move==WeaponMove.CLAW_POUNCE){
            tickPounce(p,s,t);t=p.tickCount-s.started;
        }
        if(s.move.landingAttack()){
            var landing=LocalWeaponMotion.find(s.move.id).flatMap(LocalWeaponMotion.Definition::landing);
            float impact=landing.map(LocalWeaponMotion.Landing::impactTime).orElse(s.move.start/20f);
            int descent=LocalWeaponMotion.find(s.move.id).flatMap(LocalWeaponMotion.Definition::charge).map(c->Math.round(c.releaseTime()*20)+2).orElse(4);
            if(s.landed<0){
                if(p.isInWater()||p.isInLava()||t>160){cancel(p);return;}
                p.fallDistance=0;
                boolean freeToss=s.move==WeaponMove.HAMMER_RAM&&s.target==null;
                if(freeToss&&!s.tookOff){
                    s.tookOff=true;s.mobilityStarted=p.tickCount;
                    var launch=CombatMotion.tossVelocity(s.chargePower,s.direction);s.ballisticY=launch.y;s.travelSpeed=launch.horizontalDistance();
                    p.setOnGround(false);p.setDeltaMovement(launch);p.hurtMarked=true;
                    s.previousFeet=p.position();return;
                }
                if(freeToss&&!p.onGround()){
                    strike(p,s,s.move.start);
                    s.ballisticY-=CombatMotion.tossGravity();
                    if(p.horizontalCollision)s.travelSpeed=0;
                    p.setDeltaMovement(s.direction.scale(s.travelSpeed).add(0,s.ballisticY,0));p.hurtMarked=true;
                    s.previousFeet=p.position();return;
                }
                if(!freeToss&&(s.move==WeaponMove.HAMMER_RAM||s.move.uncharged()==WeaponMove.HAMMER_COMBO_SPECIAL)&&!s.tookOff){
                    s.tookOff=true;p.setOnGround(false);p.setDeltaMovement(s.direction.scale(s.move==WeaponMove.HAMMER_RAM?.65:.18).add(0,.7,0));p.hurtMarked=true;
                }
                if(t<descent&&!p.onGround()&&airTargetAvailable(p,s)){
                    var delta=CombatTargeting.positionNear(p.position(),s.target).subtract(p.position()).multiply(1,0,1);double distance=delta.length();
                    var horizontal=distance>.001?delta.scale(Math.min(.8,distance*.3)/distance):Vec3.ZERO;
                    // Native blade/hammer anticipation is longer than a five-block fall.
                    // Keep approaching in the air until the authored downward strike starts.
                    p.setDeltaMovement(horizontal.x,Math.max(p.getDeltaMovement().y,-.06),horizontal.z);p.hurtMarked=true;
                }
                if(t>=descent&&!p.onGround()){
                    double down=Math.min(1.6,.35+(t-descent)*CombatMotion.gravity(s.move));var horizontal=Vec3.ZERO;
                    if(CombatMotion.directionalDive(s.move)){
                        // The attack can cover ground without a victim. Preserve the
                        // run/jump speed and accelerate into the selected direction.
                        s.travelSpeed=Math.min(CombatMotion.speed(s.move,s.chargePower),Math.max(s.travelSpeed,p.getDeltaMovement().horizontalDistance())+CombatMotion.acceleration(s.move));
                        horizontal=s.direction.scale(p.horizontalCollision?0:s.travelSpeed);
                    }
                    if(airTargetAvailable(p,s)){
                        var delta=CombatTargeting.positionNear(p.position(),s.target).subtract(p.position());double distance=delta.horizontalDistance();
                        // Travel toward the selected landing point before committing to the
                        // steep dive, while gravity still brings an escaping pursuit down.
                        double speed=Math.min(CombatMotion.speed(s.move,s.chargePower),distance*.32);horizontal=distance>.001?delta.multiply(1,0,1).scale(speed/distance):Vec3.ZERO;
                        if(distance>1.25&&p.getY()>s.target.getY()+1.2)down=Math.min(down,Math.max(.12,(p.getY()-s.target.getY())/Math.max(2,distance/.85+1)));
                    }
                    p.setDeltaMovement(horizontal.x,-down,horizontal.z);p.hurtMarked=true;
                }
                if(t>=descent&&p.onGround()){
                    s.landed=p.tickCount;s.clipOffset=impact;s.started=p.tickCount-Math.round(impact*20);
                    p.setDeltaMovement(0,0,0);p.hurtMarked=true;
                    com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.WEAPON_VISUAL,new WeaponVisual(s.move.ordinal(),p.level().getGameTime()-Math.round(impact*20),s.yaw,false,impact,s.target==null?-1:s.target.getId()));com.codex.blacklight.registry.ModAttachments.syncData(p, ModAttachments.WEAPON_VISUAL);
                    PlayerActions.get().playWeapon(p,s.move,false,impact);
                    PacketDistributor.sendToPlayer(p,new WeaponAnimationPayload(s.move.ordinal(),false,impact));
                }else return;
            }
            t=p.tickCount-s.started;
        }
        if(!s.groundEffect&&s.move.groundImpactTick()>=0&&t>=s.move.groundImpactTick()){
            s.groundEffect=true;CombatImpactEffects.ground(p,s.move,s.direction);
        }
        com.codex.blacklight.animation.NativeRootMotion.weapon(p, s.move, t, s.clipOffset, s.yaw);
        if(s.move!=WeaponMove.BLADE_RUSH&&LocalWeaponMotion.find(s.move.id).isEmpty()&&WeaponMotionProfile.forMove(s.move)==null&&t>=s.move.start&&t<s.move.end&&(s.move.rush()||s.move.frenzy()))p.move(MoverType.SELF,s.direction.scale(s.move.rush()?.22:.07));
        int contact=s.move.contactIndex(t);
        if(contact>=0){
            if(s.contact!=contact){s.contact=contact;s.hit.clear();s.effect=false;s.impactEffect=false;}
            if(!s.effect){s.effect=true;CombatImpactEffects.swing(p,s.move,s.direction);}
            if(s.move.groundSpike()){
                if(s.hit.isEmpty()){ClawCombatSystem.eruptGroundSpike(p);s.hit.add(p.getUUID());}
            }else strike(p,s,t);
        }else s.effect=false; // A later active interval can sound again without clearing the target ledger.
        s.previousFeet=p.position();s.previousYaw=s.yaw;rememberTargets(p,s);
        if(s.jumpQueued&&p.onGround()&&t>=s.move.end&&!s.move.landingAttack()){
            jumpOut(p,s);
            return;
        }
        // Keep all authored contacts, then let movement leave the recovery.
        // Buffered combos have priority; committed aerial/landing travel keeps its owner.
        var travelInput=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.MOVEMENT_STATE).wallInput();
        if(s.queued==null&&(!s.move.aerial()||s.move.airCombo())&&t>=s.move.end+2
                &&(travelInput.forward()!=0||travelInput.sideways()!=0)){
            cancel(p);return;
        }
        if(s.queued!=null&&t>=s.move.comboLinkTick()&&canRequest(p)){
            // Replace the session directly and blend from the current follow-through pose.
            // No idle animation or empty visual packet may interrupt this transition.
            var next=s.move.followUp(s.queued,s.form,!p.onGround(),p.isSprinting());
            var queued=s.queued;s.queued=null;var keys=BUTTONS.get(p);
            if(next!=null)beginInput(p,next,queued,s.queuedHeld&&keys!=null&&keys.held(queued));
        }else if(t>=s.move.duration){
            if(s.move.basicCombo())CHAINS.put(p,new Chain(p.tickCount,s.move,s.form,s.target));
            finish(p);
        }
    }
    private static void rememberTargets(ServerPlayer p,Session s){
        s.previousTargets.clear();
        for(var v:CombatTargeting.candidates(p,p.getBoundingBox().inflate(AttackReach.reach(p,s.move)+3),v->v!=p))
            if(v!=p)s.previousTargets.put(v.getUUID(),CombatTargeting.hitboxes(v));
    }
    private static boolean preparing(Session s,int tick){
        if(s.charging)return true;
        var local=LocalWeaponMotion.find(s.move.id);
        double release=local.flatMap(LocalWeaponMotion.Definition::charge).map(c->(double)c.releaseTime())
                .orElse(s.move.start/20.0);
        double transition=local.map(d->(double)d.transitionSeconds()).orElse(0D);
        return tick/20.0<release+transition-.0001;
    }
    private static void syncHeading(ServerPlayer p,Session s,float yaw){
        s.yaw=yaw;s.direction=Vec3.directionFromRotation(0,yaw);p.yBodyRot=yaw;
        var old=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.WEAPON_VISUAL);
        com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.WEAPON_VISUAL,new WeaponVisual(old.move(),old.started(),yaw,old.charging(),old.clipOffset(),
                s.target==null?-1:s.target.getId(),old.travelPose(),old.attackPose()));
        com.codex.blacklight.registry.ModAttachments.syncData(p, ModAttachments.WEAPON_VISUAL);
    }
    private static void steerPreparation(ServerPlayer p,Session s,int tick){
        if(s.move.aerial()||!preparing(s,tick)||!CombatMotion.usesDirectionalInput(s.move))return;
        // New directional intent may replace auto-assist, but not an explicit lock.
        if(!s.locked&&directionalInput(p,s.move)){
            var target=acquireTarget(p,s.move);
            if(target!=s.target){
                s.target=target;s.lastTargetPosition=target==null?null:target.position();
                syncHeading(p,s,s.yaw);
            }
            s.locked=target!=null&&CombatTargeting.locked(p)==target;
        }
        if(s.target==null){
            float next=CombatPursuit.turnToward(s.yaw,requestedYaw(p,s.move),(float)CombatMotion.turn(s.move,true));
            if(Math.abs(net.minecraft.util.Mth.wrapDegrees(next-s.yaw))>.01)syncHeading(p,s,next);
        }
    }
    private static void pursue(ServerPlayer p,Session s,int tick){
        if(!CombatTargeting.selectable(p,s.target)
                ||!CombatTargeting.inReach(p,s.target,CombatTargeting.viewRange(p))||!CombatTargeting.visible(p,s.target)
                ||tick>=s.move.end&&!s.move.landingAttack())return;
        boolean windup=preparing(s,tick);
        if(!windup&&!s.move.aerial()){
            var local=LocalWeaponMotion.find(s.move.id);
            double release=local.flatMap(LocalWeaponMotion.Definition::charge).map(c->(double)c.releaseTime()).orElse(0D);
            double transition=local.map(d->(double)d.transitionSeconds()).orElse(0D);
            if(!CombatMotion.releaseCanTurn(s.move,tick/20.0-release-transition))return;
        }
        boolean whip=s.move.form==com.codex.blacklight.power.PowerForm.WHIPFIST;
        if(!windup&&!s.move.landingAttack()&&(s.move.arc>=180||!s.hit.isEmpty()))return;
        var aim=CombatTargeting.multipart(s.target)?CombatTargeting.closestPoint(p.getBoundingBox().getCenter(),s.target):s.target.getBoundingBox().getCenter();
        var delta=aim.subtract(p.getBoundingBox().getCenter());
        double distance=delta.horizontalDistance();if(distance<.25)return;
        // Ordinary ground auto-assist cannot reverse a committed attack when
        // its victim escapes behind. Explicit locks and aerial pursuit keep steering.
        if(!s.locked&&!s.move.aerial()&&delta.multiply(1,0,1).dot(s.direction)<0){
            s.target=null;syncHeading(p,s,s.yaw);return;
        }
        float desired=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z));
        boolean aerialPursuit=s.locked&&s.move.aerial()||s.move==WeaponMove.CLAW_POUNCE;
        float next=CombatPursuit.turnToward(s.yaw,desired,(float)CombatMotion.turn(s.move,windup));
        if(Math.abs(next-s.yaw)>.01){
            syncHeading(p,s,next);
        }
        if(s.charging||whip||s.move.landingAttack()||aerialPursuit||s.move.quake()||s.move.rush()||s.pursuitTravel>=1.65||delta.multiply(1,0,1).normalize().dot(s.direction)<.8)return;
        double standOff=Math.max(.9,AttackReach.reach(p,s.move)*.62);
        var bounds=s.move.contactBounds(Math.max(tick,s.move.start));
        if(bounds!=null)standOff=com.codex.blacklight.util.MathCompat.clamp(bounds.maxForward()*.72,.9,standOff);
        double step=Math.min(Math.min(windup?.24:.14,Math.max(0,distance-standOff)),1.65-s.pursuitTravel);
        double dy=s.move.aerial()&&!p.onGround()?com.codex.blacklight.util.MathCompat.clamp(delta.y*.15,-.12,.12):0;
        var before=p.position();p.move(MoverType.SELF,s.direction.scale(step).add(0,dy,0));
        s.pursuitTravel+=p.position().subtract(before).length();if(!p.position().equals(before))p.hurtMarked=true;
    }
    private static WeaponMove chargedLauncher(WeaponMove move){
        return switch(move.uncharged()){
            case MUSCLE_CROSS,MUSCLE_COMBO_C,MUSCLE_SUPERMAN,COMBO_AXE_HANDLE,COMBO_BACKHAND,COMBO_MACH -> WeaponMove.MUSCLE_UPPERCUT;
            case COMBO_KICK1,COMBO_KICK2,COMBO_KNEE,COMBO_SPIN,COMBO_AXE,COMBO_SPIN_LEFT -> WeaponMove.COMBO_FLOAT;
            default -> null;
        };
    }
    private static boolean hasLaunchTarget(ServerPlayer p){
        var locked=CombatTargeting.locked(p);
        if(locked!=null)return CombatTargeting.inReach(p,locked,3)&&CombatTargeting.visible(p,locked);
        var direction=Vec3.directionFromRotation(0,p.getYRot());
        return CombatTargeting.candidates(p,p.getBoundingBox().inflate(3),v->CombatTargeting.selectable(p,v)).stream()
                .anyMatch(v->CombatTargeting.inReach(p,v,3)&&direction.dot(CombatTargeting.positionNear(p.position(),v).subtract(p.position()).normalize())>.5&&CombatTargeting.visible(p,v));
    }
    public static Vec3 strikeDirection(WeaponMove move,float yaw,double tick){
        double progress=move.contactProgress(tick);
        double sweep=move.form==com.codex.blacklight.power.PowerForm.WHIPFIST?(progress-.5)*move.arc:0;
        if(move.frenzy())sweep=(progress-.5)*move.arc*(((int)(tick-move.start)/10)%2==0?1:-1);
        return Vec3.directionFromRotation(0,(float)(yaw+sweep));
    }
    private static void strike(ServerPlayer p,Session s,int tick){
        CombatEnvironmentEffects.contact(p,s.move,tick,s.yaw,s.clipOffset,s.terrainContacts);
        boolean bodyFlight=s.mobilityStarted>=0&&!p.onGround()&&(CombatMotion.freeFlight(s.move)||s.move==WeaponMove.HAMMER_RAM);
        if(s.move.quake()&&!p.onGround()&&!bodyFlight)return;
        var direction=strikeDirection(s.move,s.yaw,tick);
        double arc=s.move.frenzy()?s.move.arc/2+18:s.move.form==com.codex.blacklight.power.PowerForm.WHIPFIST?s.move.arc/Math.max(1,s.move.end-s.move.start-1)+18:s.move.arc;
        var local=LocalWeaponMotion.find(s.move.id);
        WhipCurve whip=local.flatMap(LocalWeaponMotion.Definition::whipCurve).orElse(null);
        double transition=local.map(LocalWeaponMotion.Definition::transitionSeconds).orElse(.05f);
        double seekCorrection=s.clipOffset-Math.round(s.clipOffset*20)/20.0;
        double fromTime=Math.max(s.clipOffset,(tick-1)/20.0-transition+seekCorrection),toTime=Math.max(s.clipOffset,tick/20.0-transition+seekCorrection);
        var bounds=s.move.quake()?null:AttackReach.bounds(p,s.move,tick);
        double moveReach=AttackReach.reach(p,s.move);
        double reach=bounds==null?moveReach:Math.max(moveReach,Math.max(Math.abs(bounds.minForward()),Math.abs(bounds.maxForward()))+Math.max(Math.abs(bounds.minRight()),Math.abs(bounds.maxRight())));
        var previousFeet=safeFeet(p,s);
        var search=whip==null?p.getBoundingBox().minmax(p.getBoundingBox().move(previousFeet.subtract(p.position()))).inflate(reach+1.5,3,reach+1.5)
                :WhipCurveContact.sweptBounds(whip,fromTime,toTime,previousFeet,p.position(),s.yaw,p)
                        .minmax(WhipCurveContact.sweptBounds(whip,fromTime,toTime,previousFeet,p.position(),s.previousYaw,p)).inflate(1.5);
        var candidates=CombatTargeting.candidates(p,search,v->CombatTargeting.damageable(p,v));
        candidates.sort(Comparator.comparingInt((LivingEntity v)->v==s.target?0:1).thenComparingDouble(v->CombatTargeting.distanceSquared(p.getEyePosition(),v)));
        for(var v:candidates){
            if(s.hit.size()>=(s.move.quake()?48:8)&&!s.hit.contains(v.getUUID()))continue;
            int pulse=s.move.sweeperPulse(tick);
            boolean close=pulse>=0&&CombatTargeting.inReach(p,v,1.8)&&CombatTargeting.visible(p,v);
            if(close?s.closeSweepHits.getOrDefault(v.getUUID(),-1)==pulse:s.hit.contains(v.getUUID()))continue;
            // Previous-tick sweeps never authorize a hit after the actor has escaped
            // the victim's actual current reach (teleports, dash or stale movement).
            if(!CombatTargeting.inReach(p,v,whip==null?moveReach+1.0:30.0*AttackReach.scale(p)))continue;
            if(!close&&!contact(p,s,v,bounds,whip,fromTime,toTime,bodyFlight,moveReach,direction,arc))continue;
            int resistance=v.invulnerableTime;
            var last=LAST_CONTACT.get(v);
            boolean ownPrevious=last!=null&&last.owner.equals(p.getUUID())&&p.level().getGameTime()-last.time<=10;
            if(ownPrevious)v.invulnerableTime=0;
            if(v.hurt(p.damageSources().playerAttack(p),s.move.damageFor(s.form)*(1+.5F*s.chargePower))){
                LAST_CONTACT.put(v,new LastContact(p.getUUID(),p.level().getGameTime()));
                s.hit.add(v.getUUID());if(close)s.closeSweepHits.put(v.getUUID(),pulse);
                if(!CombatTargeting.multipart(v)&&(s.move==WeaponMove.COMBO_FLOAT||s.move==WeaponMove.MUSCLE_UPPERCUT)){
                    v.setDeltaMovement(0,.8,0);v.hurtMarked=true;
                    if(!s.lifted){s.lifted=true;p.setOnGround(false);p.setDeltaMovement(s.direction.scale(.12).add(0,.72,0));p.hurtMarked=true;}
                }else if(!CombatTargeting.multipart(v)&&(s.move.quake()||s.move.airCombo())){v.setDeltaMovement(v.getDeltaMovement().add(0,s.move==WeaponMove.AIR_AUTO4?-.6:.3,0));v.hurtMarked=true;}
                var push=s.move.quake()?v.position().subtract(p.position()).multiply(1,0,1).normalize():s.direction;
                WeaponImpact.apply(p,v,s.move,push,s.hit.size()==1);
                if(s.move.form==com.codex.blacklight.power.PowerForm.HAMMERFISTS&&!s.move.quake()&&!s.impactEffect){
                    s.impactEffect=true;hammerSplash(p,s,v);
                }
            }else if(ownPrevious)v.invulnerableTime=resistance;
        }
    }
    private static boolean airTargetAvailable(ServerPlayer p,Session s){
        return CombatTargeting.selectable(p,s.target)
                &&CombatTargeting.inReach(p,s.target,CombatTargeting.viewRange(p))&&CombatTargeting.visible(p,s.target);
    }
    /** A missed attack still travels. Its finite propulsion clock is independent
     * of enemy acquisition and holds the actual striking pose, not anticipation. */
    private static void tickFreeFlight(ServerPlayer p,Session s){
        if(s.mobilityStarted<0){
            s.mobilityStarted=p.tickCount;s.contact=0;
            var old=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.WEAPON_VISUAL);
            float pose=(s.move.start+Math.min(2,s.move.end-s.move.start-1))/20F;
            com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.WEAPON_VISUAL,new WeaponVisual(old.move(),old.started(),s.yaw,false,old.clipOffset(),-1,-1,pose));com.codex.blacklight.registry.ModAttachments.syncData(p, ModAttachments.WEAPON_VISUAL);
            CombatImpactEffects.swing(p,s.move,s.direction);
        }
        // Check the segment actually traversed, even on the final or wall tick.
        strike(p,s,s.move.start+Math.min(2,s.move.end-s.move.start-1));
        boolean stop=p.tickCount-s.mobilityStarted>=CombatMotion.flightTicks(s.move,s.chargePower)
                ||p.onGround()||p.horizontalCollision||p.verticalCollision||p.isInWater()||p.isInLava()||!s.hit.isEmpty();
        if(stop){
            s.mobilityFinished=true;s.clipOffset=s.move.end/20F;s.started=p.tickCount-s.move.end;
            var velocity=p.getDeltaMovement();
            double factor=s.hit.isEmpty()?.65:s.move==WeaponMove.AIR_PAIN?-.22:.15;
            p.setDeltaMovement(velocity.x*factor,Math.min(velocity.y,.1),velocity.z*factor);p.hurtMarked=true;
            com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.WEAPON_VISUAL,new WeaponVisual(s.move.ordinal(),p.level().getGameTime()-s.move.end,s.yaw,false,s.clipOffset,-1));com.codex.blacklight.registry.ModAttachments.syncData(p, ModAttachments.WEAPON_VISUAL);
            PlayerActions.get().playWeapon(p,s.move,false,s.clipOffset);
            PacketDistributor.sendToPlayer(p,new WeaponAnimationPayload(s.move.ordinal(),false,s.clipOffset));
        }else{
            p.setDeltaMovement(s.releaseDirection.scale(CombatMotion.speed(s.move,s.chargePower)));p.fallDistance=0;p.hurtMarked=true;
        }
        s.previousFeet=p.position();s.previousYaw=s.yaw;rememberTargets(p,s);
    }
    /** Travel owns time until arrival. Damage/FX clocks stay stopped, while the
     * same selected entity supplies a fresh destination each server tick. */
    private static void tickTravel(ServerPlayer p,Session s){
        if(!airTargetAvailable(p,s)||p.tickCount-s.travelStarted>s.travelBudget||p.isInWater()||p.isInLava()){
            p.setDeltaMovement(0,Math.min(0,p.getDeltaMovement().y),0);p.hurtMarked=true;cancel(p);return;
        }
        var delta=CombatTargeting.positionNear(p.position(),s.target).subtract(p.position());
        boolean airborne=s.move.aerial();
        double height=s.move.landingAttack()?2.2:0;
        double standOff=CombatMotion.standOff(s.move);
        float desired=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z));
        s.yaw=CombatPursuit.turn(s.yaw,s.yaw,desired,(float)Math.max(CombatMotion.turn(s.move,true),12),180);
        s.direction=Vec3.directionFromRotation(0,s.yaw);p.setYRot(s.yaw);p.setYHeadRot(s.yaw);p.yBodyRot=s.yaw;
        if(delta.horizontalDistance()<=standOff+.3&&(!airborne||Math.abs(delta.y+height)<.75)){
            s.traveling=false;s.clipOffset=LocalWeaponMotion.find(s.move.id).flatMap(LocalWeaponMotion.Definition::charge)
                    .map(LocalWeaponMotion.Charge::releaseTime).orElse(0F);
            if(s.move==WeaponMove.CLAW_POUNCE)s.clipOffset=.4F;
            s.started=p.tickCount-Math.round(s.clipOffset*20);s.previousFeet=p.position();s.previousYaw=s.yaw;
            p.setDeltaMovement(0,0,0);p.hurtMarked=true;
            com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.WEAPON_VISUAL,new WeaponVisual(s.move.ordinal(),p.level().getGameTime()-Math.round(s.clipOffset*20),s.yaw,false,s.clipOffset,s.target.getId()));com.codex.blacklight.registry.ModAttachments.syncData(p, ModAttachments.WEAPON_VISUAL);
            PlayerActions.get().playWeapon(p,s.move,false,s.clipOffset);
            PacketDistributor.sendToPlayer(p,new WeaponAnimationPayload(s.move.ordinal(),false,s.clipOffset));return;
        }
        if(p.horizontalCollision||p.tickCount-s.travelStarted>4&&p.position().distanceToSqr(s.previousFeet)<.0001)s.stuckTicks++;else s.stuckTicks=0;
        if(s.stuckTicks>=10){p.setDeltaMovement(Vec3.ZERO);p.hurtMarked=true;cancel(p);return;}
        s.travelSpeed=Math.min(CombatMotion.speed(s.move,s.chargePower),s.travelSpeed+CombatMotion.acceleration(s.move));
        var horizontal=CombatMotion.chase(delta,standOff,s.travelSpeed);
        if(horizontal.lengthSqr()>.001&&horizontal.normalize().dot(s.direction)<.8)horizontal=horizontal.scale(.25);
        double dy=airborne?com.codex.blacklight.util.MathCompat.clamp((delta.y+height)*.4,-s.travelSpeed,s.travelSpeed):p.getDeltaMovement().y;
        if(!airborne&&delta.y>1.2){cancel(p);return;}
        p.setDeltaMovement(horizontal.x,dy,horizontal.z);p.fallDistance=0;p.hurtMarked=true;
        var old=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.WEAPON_VISUAL);
        if(Math.abs(old.yaw()-s.yaw)>.2){
            com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.WEAPON_VISUAL,new WeaponVisual(old.move(),old.started(),s.yaw,false,old.clipOffset(),old.targetId(),old.travelPose()));com.codex.blacklight.registry.ModAttachments.syncData(p, ModAttachments.WEAPON_VISUAL);
        }
    }
    private static void tickPounce(ServerPlayer p,Session s,int tick){
        if(s.pounceCommitted){
            var v=p.getDeltaMovement();p.setDeltaMovement(v.x*.55,Math.max(v.y,-.12),v.z*.55);p.hurtMarked=true;return;
        }
        if(s.flight<0)s.flight=p.tickCount;
        int elapsed=p.tickCount-s.flight;
        boolean available=airTargetAvailable(p,s);
        var delta=available?CombatTargeting.positionNear(p.position(),s.target).subtract(p.position()):s.direction.scale(3);
        double distance=delta.horizontalDistance();
        boolean reached=available&&distance<1.55&&Math.abs(delta.y)<1.15;
        // The travel pose lasts until physical contact range, a wall, or the
        // bounded flight budget. A failed pursuit still rakes empty air.
        if(tick>=9&&(reached||elapsed>=18||p.horizontalCollision||p.onGround())){
            s.pounceCommitted=true;s.clipOffset=.5F;s.started=p.tickCount-10;
            p.setDeltaMovement(0,0,0);p.hurtMarked=true;
            com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.WEAPON_VISUAL,new WeaponVisual(s.move.ordinal(),p.level().getGameTime()-10,s.yaw,false,.5F,s.target==null?-1:s.target.getId()));com.codex.blacklight.registry.ModAttachments.syncData(p, ModAttachments.WEAPON_VISUAL);
            PlayerActions.get().playWeapon(p,s.move,false,.5F);
            PacketDistributor.sendToPlayer(p,new WeaponAnimationPayload(s.move.ordinal(),false,.5F));return;
        }
        if(tick>=9)s.started=p.tickCount-9;
        double speed=available?Math.min(1.15+.25*s.chargePower,Math.max(0,distance-.9)*.48):.8+.2*s.chargePower;
        var horizontal=distance>.001?delta.multiply(1,0,1).scale(speed/distance):Vec3.ZERO;
        p.setDeltaMovement(horizontal.x,available?com.codex.blacklight.util.MathCompat.clamp(delta.y*.4,-.85,.75):-.045,horizontal.z);
        p.fallDistance=0;p.hurtMarked=true;
    }
    /** Hammer contact disperses a nearby crowd; the main victim is never damaged twice. */
    private static void hammerSplash(ServerPlayer p,Session s,LivingEntity primary){
        var center=CombatTargeting.positionNear(p.getEyePosition(),primary);double radius=(s.move==s.move.uncharged()?1.65:2.2)*AttackReach.scale(p);
        var search=CombatTargeting.multipart(primary)?new AABB(center,center).inflate(radius,1.5,radius):primary.getBoundingBox().inflate(radius,1,radius);
        var splashOrigin=CombatTargeting.multipart(primary)?center:primary.getBoundingBox().getCenter();
        for(var v:CombatTargeting.candidates(p,search,target->CombatTargeting.damageable(p,target))){
            if(v==primary||!CombatTargeting.damageable(p,v)||s.hit.contains(v.getUUID()))continue;
            var delta=CombatTargeting.closestPoint(center,v).subtract(center);
            if(delta.horizontalDistance()>radius||Math.abs(delta.y)>1.5)continue;
            if(CombatTargeting.hitboxes(v).stream().noneMatch(box->box.intersects(search)&&CombatTargeting.visible(p,splashOrigin,box)))continue;
            if(v.hurt(p.damageSources().playerAttack(p),s.move.damage*.65F)){
                s.hit.add(v.getUUID());LAST_CONTACT.put(v,new LastContact(p.getUUID(),p.level().getGameTime()));
                WeaponImpact.apply(p,v,s.move,delta.multiply(1,0,1).normalize(),false);
            }
        }
    }
    private static Vec3 safeFeet(ServerPlayer p,Session s){
        return s.previousFeet.distanceToSqr(p.position())<=64?s.previousFeet:p.position();
    }
    private static AABB previousBox(Session s,LivingEntity target,List<AABB> current,int index){
        var box=current.get(index);var previous=s.previousTargets.get(target.getUUID());
        if(previous==null||previous.size()!=current.size())return box;
        var old=previous.get(index);
        // Entity teleports and sudden giant-body turns do not sweep damage
        // through an entire old-to-new limb corridor.
        return old.getCenter().distanceToSqr(box.getCenter())<=64?old:box;
    }
    private static boolean contact(ServerPlayer p,Session s,LivingEntity target,LocalWeaponMotion.ContactBounds bounds,
            WhipCurve whip,double fromTime,double toTime,boolean bodyFlight,double reach,Vec3 direction,double arc){
        var current=CombatTargeting.hitboxes(target);var previousFeet=safeFeet(p,s);var origin=p.getEyePosition();
        double maxReach=whip==null?reach+1:30*AttackReach.scale(p);
        for(int part=0;part<current.size();part++){
            var box=current.get(part);
            if(CombatTargeting.distanceSquared(origin,box)>maxReach*maxReach)continue;
            if(bodyFlight){
                var expanded=box.inflate(.45,.25,.45);
                var from=previousFeet.add(0,.9,0);var to=p.position().add(s.direction.scale(1)).add(0,.9,0);
                if(!expanded.contains(from)&&!expanded.contains(to)&&expanded.clip(from,to).isEmpty())continue;
            }else if(whip!=null){
                if(!WhipCurveContact.hits(whip,fromTime,toTime,previousFeet,p.position(),s.previousYaw,s.yaw,
                        previousBox(s,target,current,part),box,p.level(),p))continue;
            }else if(bounds!=null){
                if(!meleeContact(p,s,previousBox(s,target,current,part),box,bounds))continue;
            }else{
                var attackOrigin=p.position().add(0,p.getBbHeight()*.5,0);
                var delta=(CombatTargeting.multipart(target)?CombatTargeting.closestPoint(attackOrigin,box):box.getCenter()).subtract(attackOrigin);
                double horizontal=delta.horizontalDistance();
                double halfWidth=CombatTargeting.multipart(target)?0:target.getBbWidth()*.5;
                if(horizontal>reach+halfWidth||Math.abs(delta.y)>2)continue;
                double margin=Math.asin(Math.min(1,halfWidth/Math.max(.1,horizontal)));
                double dot=horizontal<.05?1:direction.dot(delta.multiply(1,0,1).scale(1/horizontal));
                if(arc<360&&dot<Math.cos(Math.toRadians(arc*.5)+margin))continue;
            }
            if(whip!=null||CombatTargeting.visible(p,origin,box))return true;
        }
        return false;
    }
    private static boolean meleeContact(ServerPlayer p,Session s,AABB previous,AABB current,LocalWeaponMotion.ContactBounds bounds){
        for(int i=0;i<=10;i++){
            double alpha=i/10.0;var box=CombatPursuit.interpolate(previous,current,alpha);
            var feet=safeFeet(p,s).lerp(p.position(),alpha);
            float yaw=s.previousYaw+net.minecraft.util.Mth.wrapDegrees(s.yaw-s.previousYaw)*(float)alpha;
            var forward=Vec3.directionFromRotation(0,yaw);var right=new Vec3(-forward.z,0,forward.x);
            var center=box.getCenter().subtract(feet);
            double hx=box.getXsize()*.5,hy=box.getYsize()*.5+.15,hz=box.getZsize()*.5;
            if(bounds.intersects(center.dot(right),center.y,center.dot(forward),Math.abs(right.x)*hx+Math.abs(right.z)*hz,hy,
                    Math.abs(forward.x)*hx+Math.abs(forward.z)*hz))return true;
        }
        return false;
    }
    public static void cancel(ServerPlayer p){
        DevastatorSystem.cancel(p);
        CHAINS.remove(p);
        finish(p);
    }
    private static void finish(ServerPlayer p){
        var old=ACTIVE.remove(p);if(old==null || !ModAttachments.available(p))return;
        com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.CLAW_COMBAT).reset();com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.WEAPON_VISUAL,WeaponVisual.EMPTY);com.codex.blacklight.registry.ModAttachments.syncData(p, ModAttachments.WEAPON_VISUAL);
        PlayerActions.get().resume(p);
        PlayerActions.get().play(p, !p.onGround()&&!p.isInWater()&&!p.isInLava()
                ? (p.getDeltaMovement().y>.035 ? PlayerAction.JUMP_RISE : PlayerAction.JUMP_FALL)
                : PlayerAction.IDLE, .08F);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p){cancel(p);CHAINS.remove(p);BUTTONS.remove(p);AIR_HITS.remove(p);}}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p){cancel(p);BUTTONS.remove(p);AIR_HITS.remove(p);}}
    @SubscribeEvent public static void stop(net.minecraftforge.event.server.ServerStoppingEvent e){for(var p:List.copyOf(ACTIVE.keySet()))cancel(p);CHAINS.clear();BUTTONS.clear();AIR_HITS.clear();}
}
