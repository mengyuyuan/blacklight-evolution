package com.codex.blacklight.combat;

import com.codex.blacklight.animation.*;
import com.codex.blacklight.entity.BackspineQuill;
import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.power.CombatSkill;
import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.registry.ModWeaponSounds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.WeakHashMap;

/** Native action time is the clock for both the shoulder blade and its detached entity. */
public final class BackspineCombat {
    private static final net.minecraft.core.particles.DustParticleOptions SINEW_SHRED=
            new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(.38F,.025F,.046F),.64F);
    private static final net.minecraft.core.particles.DustParticleOptions DARK_SHRED=
            new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(.09F,.018F,.024F),.48F);
    private enum Phase { READY, LIGHT, DRAW, THROW, LAND }
    private enum Light {
        SWEEP("sweep",5,9,14,ScapulaVolley.OPENING_SHOT_DAMAGE), LIFT("lift",4,8,13,5), DRIVE("drive",5,14,14,6), BURST("burst",4,18,18,3);
        final String key;
        final int shot, link, end, damage;
        Light(String key,int shot,int link,int end,int damage) {
            this.key=key; this.shot=shot; this.link=link; this.end=end; this.damage=damage;
        }
        Light next(boolean held) {
            return switch(this) {
                case SWEEP -> LIFT;
                case LIFT -> held ? BURST : DRIVE;
                case DRIVE, BURST -> SWEEP;
            };
        }
    }
    private enum Heavy { CRUSH, RETURN, SWEEP }
    private static final class Press {
        final int started;
        int released = -1;
        Press(int started) { this.started = started; }
    }

    private static final class State {
        Phase phase=Phase.READY;
        Light light=Light.SWEEP;
        Heavy heavy=Heavy.CRUSH;
        int started, inputAt;
        java.util.UUID volleyId;
        boolean attack, special, pendingLight, air, charged, awaitingLanding;
        // A double shot may still be finishing when two distinct taps arrive.
        final ArrayDeque<Press> queuedHeavy = new ArrayDeque<>(2);
        Press buttonDown, drawing;
        boolean nextRight=true,leadRight,receiveRight,comboHeavy;
        float previousAimTurn;
        int receiveUntil=-1;
        boolean extraLight, releaseReady, heavyPaid;
        int chamberUntil = -1;
        String clip="";
    }
    private static final Map<ServerPlayer,State> STATES=new WeakHashMap<>();
    private BackspineCombat() {}
    private static boolean equipped(ServerPlayer p){return p.getData(ModAttachments.PLAYER_STATE).getActivePower()==PowerForm.BACKSPINES;}
    private static boolean movingOnFoot(ServerPlayer p) {
        var input = p.getData(ModAttachments.MOVEMENT_STATE).wallInput();
        return p.onGround() && !p.isPassenger() && (Math.abs(input.forward()) + Math.abs(input.sideways()) > .05);
    }
    public static boolean busy(ServerPlayer p){var s=STATES.get(p);return s!=null&&s.phase!=Phase.READY;}
    private static boolean eligible(ServerPlayer p){
        return equipped(p)&&WeaponCombatSystem.canRequest(p,true)&&p.getData(ModAttachments.GRAB_STAGE)==0
                &&p.getData(ModAttachments.CLAW_COMBAT).action()==ClawCombatState.Action.IDLE;
    }
    public static void cancel(ServerPlayer p){
        STATES.remove(p);
        syncAim(p,null);
        if(p.getData(ModAttachments.PLAYER_ACTION).clipId().startsWith("spines_"))NativePlayerActionBackend.clear(p);
    }
    public static boolean input(ServerPlayer p,boolean attack,boolean special) {
        if(!equipped(p))return false;
        if(!eligible(p)){cancel(p);return true;}
        State s=STATES.computeIfAbsent(p,ignored->new State());
        if (attack && !s.attack) {
            if (s.pendingLight && s.phase == Phase.LIGHT && movingOnFoot(p) && p.getData(ModAttachments.PLAYER_STATE).hasCombatSkill(CombatSkill.QUILL_RELAY_1))
                s.extraLight = true;
            s.pendingLight = true;
            s.releaseReady = p.onGround() && s.chamberUntil >= p.tickCount;
            s.chamberUntil = -1;
        }
        if (!attack && s.attack && p.onGround() && s.phase == Phase.LIGHT && !s.pendingLight
                && p.tickCount - s.started >= s.light.shot && p.getData(ModAttachments.PLAYER_STATE).hasCombatSkill(CombatSkill.QUILL_RELAY_2))
            s.chamberUntil = p.tickCount + 8;
        if (special && !s.special && s.queuedHeavy.size() < 2) {
            s.buttonDown = new Press(p.tickCount);
            s.queuedHeavy.addLast(s.buttonDown);
        }
        if (!special && s.buttonDown != null) {
            s.buttonDown.released = p.tickCount;
            s.buttonDown = null;
        }
        s.attack=attack;
        s.special=special;
        s.inputAt=p.tickCount;
        if(s.phase==Phase.READY)startReady(p,s);
        else if(s.phase==Phase.DRAW&&s.drawing.released>=0){
            s.air=!p.onGround();
            releaseHeavy(p,s);
        }
        return true;
    }

    private static void play(ServerPlayer p,State s,Phase phase,String key) {
        s.phase=phase;
        s.started=p.tickCount;
        s.volleyId=java.util.UUID.randomUUID();
        s.clip=ScapulaCycle.actionKey("spines_"+key,s.leadRight);
        if(phase!=Phase.LAND)syncAim(p,QuillHarvest.attackTarget(p,64,false));
        int offset = phase == Phase.LIGHT ? (s.releaseReady ? s.light.shot - 1 : s.clip.contains("_flight_") ? 2 : 0) : 0;
        if (offset > 0) {
            s.started -= offset;
            NativePlayerActionBackend.playVisualFromTick(p, s.clip, offset);
        }else PlayerActions.get().playVisual(p,s.clip,0);
    }
    public static void prepareReception(ServerPlayer p,boolean right){
        State s=STATES.computeIfAbsent(p,ignored->new State());
        s.receiveRight=right;
        s.receiveUntil=p.tickCount+24;
    }
    private static void syncAim(ServerPlayer p,TargetSelection selection){
        int id=selection==null?-1:selection.target().getId();
        if(p.getData(ModAttachments.QUILL_AIM_TARGET)!=id){
            p.setData(ModAttachments.QUILL_AIM_TARGET,id);
            p.syncData(ModAttachments.QUILL_AIM_TARGET);
        }
    }
    private static boolean leadShoulder(ServerPlayer p,State s){
        if(p.tickCount>s.receiveUntil)return s.nextRight;
        s.receiveUntil=-1;
        return !s.receiveRight;
    }
    private static void light(ServerPlayer p,State s,Light move) {
        s.pendingLight = s.extraLight;
        s.extraLight = false;
        s.awaitingLanding=false;
        s.light=move;
        s.leadRight=leadShoulder(p,s);
        play(p,s,Phase.LIGHT,(p.onGround()?"light_":"light_flight_")+move.key);
        s.releaseReady = false;
    }
    private static String heavyKey(State s) {
        return (s.air?"air_":"ground_")+s.heavy.name().toLowerCase(java.util.Locale.ROOT);
    }
    private static void draw(ServerPlayer p,State s,Heavy move) {
        s.comboHeavy = s.phase==Phase.LIGHT;
        s.drawing = s.queuedHeavy.removeFirst();
        s.extraLight = false;
        s.releaseReady = false;
        s.chamberUntil = -1;
        s.pendingLight=false;
        s.awaitingLanding=false;
        s.heavy=move;
        s.leadRight=leadShoulder(p,s);
        s.air=!p.onGround();
        if(s.drawing.released>=0){
            releaseHeavy(p,s);
            return;
        }
        play(p,s,Phase.DRAW,heavyKey(s)+"_draw");
        ModWeaponSounds.play(p,PowerForm.BACKSPINES,"draw",p.getEyePosition(),.33F,1F);
    }
    private static void releaseHeavy(ServerPlayer p,State s){
        s.heavyPaid = false;
        int heldTicks = s.drawing.released - s.drawing.started;
        s.drawing = null;
        s.charged=heldTicks>=(s.air?6:10);
        play(p,s,Phase.THROW,heavyKey(s)+(s.charged?"_charged":"_throw"));
    }
    private static void startReady(ServerPlayer p,State s) {
        if(!s.queuedHeavy.isEmpty())draw(p,s,Heavy.CRUSH);
        else if(s.attack||s.pendingLight)light(p,s,Light.SWEEP);
    }
    private static void sampleDraw(ServerPlayer p,State s,int age) {
        boolean air=!p.onGround();
        if(air==s.air&&age!=10)return;
        s.air=air;
        s.clip=ScapulaCycle.actionKey("spines_"+heavyKey(s)+"_draw",s.leadRight);
        var old=p.getData(ModAttachments.PLAYER_ACTION);
        float offset=Math.min(10,age)/20F;
        p.setData(ModAttachments.PLAYER_ACTION,new NativeActionState(s.clip,p.level().getGameTime(),
                offset,0,MotionLibrary.duration(s.clip),p.getYRot(),false,age>=10?.5F:-1,old.revision()+1));
        p.syncData(ModAttachments.PLAYER_ACTION);
    }
    public static void tick(ServerPlayer p) {
        State s=STATES.get(p);
        if(s==null)return;
        if(!eligible(p)){cancel(p);return;}
        if(p.tickCount-s.inputAt>24){
            if(s.phase!=Phase.READY||s.attack||s.special||s.awaitingLanding)cancel(p);
            return;
        }
        int age=p.tickCount-s.started;
        switch(s.phase) {
            case LIGHT -> {
                Light move=s.light;
                if(age==move.shot || move==Light.BURST&&age==10)fire(p,s,move.damage);
                int branch=move==Light.DRIVE||move==Light.BURST?move.end:move.shot+2;
                if(!s.queuedHeavy.isEmpty()&&age>=branch){
                    draw(p,s,switch(move){case SWEEP -> Heavy.RETURN; case LIFT -> Heavy.SWEEP; case DRIVE,BURST -> Heavy.CRUSH;});
                    return;
                }
                int link = move.link;
                if (move != Light.BURST && movingOnFoot(p)
                        && p.getData(ModAttachments.PLAYER_STATE).hasCombatSkill(CombatSkill.QUILL_RELAY_1)) link = Math.max(move.shot + 2, link - 2);
                if(age>=link&&(s.attack||s.pendingLight)){light(p,s,move.next(s.attack&&!s.pendingLight));return;}
                if(age<move.end)return;
            }
            case DRAW -> {
                sampleDraw(p,s,age);
                if(age>=4&&age%3==1)ventShoulders(p,s,age);
                if(age==(s.air?6:10)&&s.special)
                    ModWeaponSounds.play(p,PowerForm.BACKSPINES,"morph",p.getEyePosition(),.34F,1.32F);
                if(age==8&&!s.air){
                    var pos=p.getOnPos();
                    var block=p.level().getBlockState(pos);
                    if(!block.isAir()){
                        var sound=block.getSoundType(p.level(),pos,p);
                        p.level().playSound(null,p.getX(),p.getY(),p.getZ(),sound.getStepSound(),
                                net.minecraft.sounds.SoundSource.PLAYERS,.55F*sound.getVolume(),.87F*sound.getPitch());
                    }
                }
                return;
            }
            case THROW -> {
                if(!s.air&&!p.onGround()&&age<(s.charged?5:4)){
                    s.air=true;
                    play(p,s,Phase.THROW,heavyKey(s)+(s.charged?"_charged":"_throw"));
                    return;
                }
                int shot=ScapulaCycle.shotTick(s.clip,s.leadRight);
                int otherShot=ScapulaCycle.shotTick(s.clip,!s.leadRight);
                if(RooftopRig.four(s.clip)?age>=5&&age<=8:age==shot||age==otherShot){
                    fire(p,s,s.charged?21:s.heavy==Heavy.CRUSH?12:18);
                    s.awaitingLanding=s.air;
                }
                if((!s.queuedHeavy.isEmpty()||s.pendingLight)&&age>=(s.air?5:RooftopRig.four(s.clip)?10:Math.max(shot,otherShot)+2)){
                    startReady(p,s);return;
                }
                if(s.awaitingLanding&&p.onGround()&&age>=shot+(s.charged?8:3)){
                    s.awaitingLanding=false;
                    play(p,s,Phase.LAND,age-shot>=6?"scapula_land_ready":"scapula_land");
                    return;
                }
                if(age<16)return;
            }
            case LAND -> {
                if(age<9&&p.onGround()&&s.queuedHeavy.isEmpty()&&!s.pendingLight)return;
            }
            case READY -> {
                if(s.awaitingLanding&&p.onGround()){
                    s.awaitingLanding=false;
                    play(p,s,Phase.LAND,"scapula_land_ready");
                    return;
                }
            }
        }
        s.phase=Phase.READY;
        startReady(p,s);
        // Keep the next shoulder across pauses between individual presses until the form is cancelled.
    }
    private static final net.minecraft.core.particles.DustParticleOptions BLOOD_MIST=
            new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(.61F,.012F,.046F),.87F);

    private static void ventShoulders(ServerPlayer p,State s,int age){
        var rig=RooftopRig.INSTANCE;
        float poseTick=s.air&&age>=10?10+(age-10)%10:Math.min(age,10);
        var bones=BlacklightSkeleton.get().getPoseMatrices(MotionLibrary.pose(s.clip,poseTick/20,false));
        var source=rig.sample(RooftopRig.time(s.clip,age));
        if(!s.air&&age>=13)rig.projectPlayer(source,RooftopRig.reflect(s.clip),bones);
        if(RooftopRig.groundShot(s.clip)&&p.onGround()&&!p.isCrouching()&&!p.isInWaterOrBubble())RooftopRig.plantGroundShot(bones);
        var chest=ScapulaMount.chest(bones);
        float angle=(float)Math.toRadians(180-p.getYRot());
        for(int side=0;side<2;side++){
            var socket=rig.frame(source,rig.rib[side],s.clip,chest);
            var at=socket.transformPosition(new org.joml.Vector3f(0,.14F,.02F));
            var port=p.position().add(new Vec3(at.x,at.y,at.z).scale(.9375F*p.getScale()).yRot(angle));
            int count=age>=10&&ScapulaVolley.vascularPulse(age,.55F)>.38F?3:1;
            p.serverLevel().sendParticles(BLOOD_MIST,port.x,port.y,port.z,count,.067,.055,.054,.011);
        }
    }
    private static void fire(ServerPlayer p,State s,float damage){
        boolean heavyShot = s.phase == Phase.THROW;
        if (!heavyShot || !s.heavyPaid) {
            if (!QuillHarvest.payShot(p, heavyShot, s.charged, s.comboHeavy)) {
                s.phase = Phase.READY;
                s.pendingLight = s.attack = s.extraLight = false;
                s.queuedHeavy.clear();
                s.buttonDown = null;
                NativePlayerActionBackend.clear(p);
                return;
            }
            if (heavyShot) s.heavyPaid = true;
        }
        float time=(p.tickCount-s.started)/20F;
        var sk=BlacklightSkeleton.get();
        float yaw=p.getYRot();
        var selection=QuillHarvest.attackTarget(p,64,false,p.getData(ModAttachments.QUILL_AIM_TARGET));
        syncAim(p,selection);
        var lock=selection==null?null:selection.target();
        Vec3 aim;
        if(selection!=null){
            aim=selection.point(p);
        }else{
            Vec3 eye=p.getEyePosition(),wall=p.pick(64,1,false).getLocation();
            var sight=net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(p.level(),p,eye,wall,
                    new net.minecraft.world.phys.AABB(eye,wall).inflate(1),candidate->candidate!=p
                    && candidate.isPickable() && !candidate.isSpectator() && !p.isAlliedTo(candidate) && !QuillHarvest.protectedPet(candidate));
            aim=sight==null?wall:sight.getLocation();
        }
        // A quadruped's box midpoint can lie between its legs. When the lock supplies
        // that generic midpoint, raise the quill toward the body. Keep explicit part locks exact.
        if(lock instanceof net.minecraft.world.entity.animal.Animal animal
                &&aim.distanceToSqr(animal.getBoundingBox().getCenter())<1e-8)
            aim=aim.add(0,Math.max(0,animal.getEyeY()-aim.y)*.65,0);
        var body=sk.getPoseMatrices(MotionLibrary.pose(s.clip,time,false));
        if(RooftopRig.fullBody(s.clip)){var rig=RooftopRig.INSTANCE;rig.projectPlayer(rig.sample(RooftopRig.time(s.clip,time*20)),RooftopRig.reflect(s.clip),body); }
        if(RooftopRig.groundShot(s.clip)&&p.onGround()&&!p.isCrouching()&&!p.isInWaterOrBubble())RooftopRig.plantGroundShot(body);
        if(selection!=null && RooftopRig.groundShot(s.clip) && p.onGround()){
            var offset=aim.subtract(p.getEyePosition());
            boolean rearRight=Math.abs(s.previousAimTurn)>8 ? s.previousAimTurn>0 : s.leadRight;
            s.previousAimTurn=RooftopRig.shoulderTurn(offset,yaw,rearRight);
            RooftopRig.aimUpperBody(body,s.previousAimTurn,RooftopRig.shoulderPitch(offset));
        }
        RooftopRig.recoil(body,s.clip,p.tickCount-s.started);
        var chest=ScapulaMount.chest(body);
        float worldAngle=(float)Math.toRadians(180-yaw);
        var bodyTurn=new org.joml.Quaternionf().rotationY(worldAngle);
        var eye=p.getEyePosition();
        boolean heavy=s.phase==Phase.THROW;
        boolean cutting=heavy&&s.heavy==Heavy.SWEEP;
        boolean burst=!heavy&&s.light==Light.BURST;
        boolean follow=burst&&p.tickCount-s.started==10;
        boolean groundHeavy=heavy&&!s.air;
        boolean paired=groundHeavy&&s.charged;
        boolean counter=groundHeavy&&!paired&&p.tickCount-s.started==ScapulaCycle.shotTick(s.clip,!s.leadRight);
        boolean centerRight=(follow||counter)?!s.leadRight:s.leadRight;
        float height=heavy?(cutting?.08F:-.49F):switch(s.light){
            case SWEEP -> .12F; case LIFT -> .64F; case DRIVE -> .02F; case BURST -> follow?.74F:-.28F;
        };
        float lateral=cutting?-1.65F:burst?(follow?1.05F:-.57F):-.82F;
        int pairs=ScapulaVolley.pairs(heavy&&s.air,s.charged);
        Vec3 release=p.position(),centralDirection=p.getLookAngle();
        Vec3 right=new Vec3(1,0,0).yRot((float)Math.toRadians(-p.getYRot()));
        int age=p.tickCount-s.started;
        boolean roofFour=RooftopRig.four(s.clip);
        int lastOutlet=roofFour?0:paired?1:pairs;
        for(int outlet=-pairs;outlet<=lastOutlet;outlet++){
            int slot=paired?0:outlet;
            boolean rightShoulder=paired&&outlet==1?!centerRight:ScapulaCycle.rightForLane(centerRight,slot);
            int index=RooftopRig.index(s.clip,rightShoulder,slot,age);
            if(roofFour)rightShoulder=(index%2==0)!=RooftopRig.reflect(s.clip);
            var socket=RooftopRig.INSTANCE.exit(s.clip,age,index,chest);
            var at=socket.transformPosition(RooftopRig.center(index));
            var start=p.position().add(new Vec3(at.x,at.y,at.z).scale(.9375F*p.getScale()).yRot(worldAngle));
            var wall=p.level().clip(new net.minecraft.world.level.ClipContext(eye,start,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,p));
            if(wall.getType()!=net.minecraft.world.phys.HitResult.Type.MISS)start=eye;
            var direction=aim.subtract(start).normalize();
            if(direction.lengthSqr()<.01)direction=p.getLookAngle();
            if(slot==0){
                release=paired&&outlet==1?release.lerp(start,.5):start;
                centralDirection=direction;
            }
            float share=roofFour?.25F:groundHeavy?(paired?.5F:counter?.60F:.40F):1;
            var shot=new BackspineQuill(p.serverLevel(),p,ScapulaVolley.damage(damage*share,slot,pairs),rightShoulder?0:1);
            shot.setVolleyId(s.volleyId);
            shot.setPos(start.x,start.y,start.z);
            if (slot==0 && selection!=null) shot.setAimTarget(selection.target().getUUID());
            var skills = p.getData(ModAttachments.PLAYER_STATE);
            shot.setWedgeSkills(skills.hasCombatSkill(CombatSkill.QUILL_WEDGE_1),
                    heavy && slot == 0 && s.charged && skills.hasCombatSkill(CombatSkill.QUILL_WEDGE_2));
            shot.setRoofIndex(index);shot.setVolleySlot(slot);shot.setHeavy(heavy);
            if(heavy && s.air && slot==0)shot.armAirImpactFeedback();
            // A quaternion carries the proper rotation. The entity's shoulder bit carries the reflection.
            var proper=socket;
            var orientation=new org.joml.Quaternionf(bodyTurn).mul(proper.getUnnormalizedRotation(new org.joml.Quaternionf()).normalize());
            var heading=ScapulaVolley.aim(direction,right,slot,s.charged);
            shot.launch(heading,aim.distanceTo(start),heavy?2.15F:follow?2.08F:1.82F,height+.09F*Math.abs(slot),
                    paired?(rightShoulder?.58F:-.58F):pairs==0?(rightShoulder?-lateral:lateral):-slot*.26F,orientation);
            if(cutting&&slot==0)shot.enableCleaving();
            p.level().addFreshEntity(shot);
            // A short directional tear at each actual socket, including point-blank casts.
            int filaments=slot==0?(heavy?5:3):2;
            for(int strand=0;strand<filaments;strand++){
                double side=strand/(double)(filaments-1)-.5;
                Vec3 pull=heading.scale(.10+.08*strand).add(right.scale(side*(cutting?.36:.20)))
                        .add(0,side*height*.34,0);
                var tip=start.add(pull.scale(.46));
                p.serverLevel().sendParticles(strand%2==0?SINEW_SHRED:DARK_SHRED,tip.x,tip.y,tip.z,0,pull.x,pull.y,pull.z,.52);
            }
        }
        s.nextRight=!centerRight;
        if(pairs>0){
            var keys=p.getData(ModAttachments.MOVEMENT_STATE).wallInput();
            var wish=Vec3.directionFromRotation(0,yaw).scale(keys.forward()).add(right.scale(-keys.sideways()));
            p.setDeltaMovement(ScapulaVolley.breakAway(p.getDeltaMovement(),centralDirection,wish,s.charged));
            p.resetFallDistance();p.hurtMarked=true;
        }else if(!heavy&&!p.onGround()&&!p.isPassenger()&&!p.isFallFlying()&&!p.getAbilities().flying&&!p.isInWaterOrBubble()&&!p.isInLava()){
            var travel=p.getData(ModAttachments.MOVEMENT_STATE);
            var keys=travel.wallInput();
            if(travel.mode()==com.codex.blacklight.movement.MovementState.Mode.NORMAL&&(keys.forward()!=0||keys.sideways()!=0)){
                var wish=Vec3.directionFromRotation(0,yaw).scale(keys.forward()).add(right.scale(-keys.sideways()));
                p.setDeltaMovement(ScapulaVolley.bankLight(p.getDeltaMovement(),wish));p.hurtMarked=true;
            }
        }else if(p.onGround()&&(!roofFour||age==8)){
            var forward=Vec3.directionFromRotation(0,p.getYRot());
            var keys=p.getData(ModAttachments.MOVEMENT_STATE).wallInput();
            if(keys.sideways()!=0||keys.forward()!=0){
                Vec3 stride=forward.scale(keys.forward()).add(right.scale(-keys.sideways())).normalize();
                double step=heavy?(paired?.33:counter?.22:.095):s.light==Light.DRIVE?.19:s.light==Light.BURST?.045:.08;
                p.setDeltaMovement(p.getDeltaMovement().add(stride.scale(step)));
                p.hurtMarked=true;
            }
        }
        if(groundHeavy&&p.onGround()&&(paired||counter)&&(!roofFour||age==8)){
            var floor=p.getOnPos();
            var material=p.level().getBlockState(floor);
            if(!material.isAir()){
                Vec3 foot=p.position().add(right.scale(centerRight?.19:-.19));
                p.serverLevel().sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,material),
                        foot.x,foot.y+.06,foot.z,paired?12:5,.16,.02,.12,paired?.095:.045);
            }
        }
        float pitch=paired?.68F:counter?.80F:groundHeavy?1.06F:heavy?(cutting?.98F:.76F):burst?(follow?1.35F:.99F):1.18F;
        ModWeaponSounds.play(p,PowerForm.BACKSPINES,heavy?"heavy":"swing",release,heavy?.83F:.48F,pitch);
    }
}
