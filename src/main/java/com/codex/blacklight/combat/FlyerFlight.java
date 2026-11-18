package com.codex.blacklight.combat;

import com.codex.blacklight.entity.EncounterMob;
import com.codex.blacklight.registry.ModInfectedSounds;
import com.codex.blacklight.infection.InfectedSoundCue;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.*;
import java.util.*;
import static com.codex.blacklight.combat.FlyerMotion.*;

/** A committed dive, followed by a physical pullout. Speeds are MC adaptations,
 * not inferred from undocumented packed P2 controller fields. */
public final class FlyerFlight {
    private final EncounterMob mob;
    private UUID target;
    private Vec3 aim=Vec3.ZERO,heading=new Vec3(0,0,1),velocity=Vec3.ZERO;
    private boolean contacted;
    private int hits,collisions;
    private Vec3 lastContact;
    private float[][] previousPose;
    public FlyerFlight(EncounterMob mob){this.mob=mob;}
    public int hits(){return hits;}public int collisions(){return collisions;}public Vec3 lastContact(){return lastContact;}
    public void begin(LivingEntity victim){target=victim.getUUID();aim=victim.getBoundingBox().getCenter();velocity=Vec3.ZERO;contacted=false;mob.flyerStage(PREPARE);}
    public void cancel(){
        target=null;velocity=Vec3.ZERO;
        // Goal.stop cancels an attack when a weapon stuns the victim. It must
        // not erase the hurt clip published by that same accepted damage.
        int stage=mob.flyerStage();
        if(stage!=HIT_FRONT&&stage!=HIT_BACK&&stage!=RECOVER)mob.flyerStage(IDLE);
    }
    /** Advance the receiver clock while the victim controller owns movement. */
    public void interruptedTick(){
        mob.finishFlyerAttack();target=null;velocity=Vec3.ZERO;
        int stage=mob.flyerStage();float age=mob.flyerAge(0);
        if((stage==HIT_FRONT||stage==HIT_BACK)&&age>=duration(stage))mob.flyerStage(RECOVER);
        else if(stage==RECOVER&&age>=duration(RECOVER))mob.flyerStage(IDLE);
        else if(stage!=HIT_FRONT&&stage!=HIT_BACK&&stage!=RECOVER)mob.flyerStage(IDLE);
        previousPose=mob.flyerPose(0);
    }
    public void hurt(Vec3 from){
        var incoming=from==null?Vec3.ZERO:from.subtract(mob.position());
        boolean front=CreatureAttack.inArc(incoming.x,incoming.z,mob.getYRot(),0);
        mob.cancelAttack();mob.flyerStage(front?HIT_FRONT:HIT_BACK,true);velocity=incoming.normalize().scale(-.08);
    }
    public void circle(LivingEntity victim){
        if(mob.flyerStage()!=IDLE)return;
        double a=mob.tickCount*.025+mob.getId()*.37;
        Vec3 goal=victim.position().add(Math.sin(a)*8,3.5,Math.cos(a)*8);
        Vec3 wanted=goal.subtract(mob.position()).normalize().scale(.22);
        velocity=velocity.lerp(wanted,.12);var before=previousPose==null?mob.flyerPose(0):previousPose;
        face(velocity,8);move(before,mob.flyerPose(0),velocity,false);previousPose=mob.flyerPose(0);
    }
    private LivingEntity victim(){var e=target==null?null:((ServerLevel)mob.level()).getEntity(target);return e instanceof LivingEntity v&&mob.enemy(v)?v:null;}
    public void tick(){
        try{tickFlight();}finally{previousPose=mob.flyerPose(0);}
    }
    private void tickFlight(){
        int stage=mob.flyerStage();
        if((stage==IDLE||stage==PREPARE)&&mob.tickCount%23==0)mob.playSound(net.minecraft.sounds.SoundEvents.ENDER_DRAGON_FLAP,.3F,1.7F);
        if(stage==IDLE){mob.flightPitch(Mth.approachDegrees(mob.flightPitch(),0,4));return;}
        float age=mob.flyerAge(0);var before=previousPose==null?mob.flyerPose(0):previousPose;
        var v=victim();
        if(stage==PREPARE){
            if(v==null||!mob.hasLineOfSight(v)){mob.cancelAttack();return;}
            // The hooked beak hangs below the thorax. Aim the carrier at the upper
            // body so its actual lower striking surface meets low prey above ground.
            aim=new Vec3(v.getX(),v.getBoundingBox().maxY+.25,v.getZ());face(aim.subtract(mob.position().add(0,.6,0)),12);
            double rise=v.getY()+2.7-mob.getY();
            if(rise>0)move(before,mob.flyerPose(0),new Vec3(0,Math.min(.2,rise),0),false);
            if(age>=40&&rise>.3){mob.cancelAttack();return;}
            if(age>=duration(PREPARE)&&rise<=.3){
                heading=aim.subtract(mob.position().add(0,.6,0)).normalize();
                mob.flyerStage(BOOST);ModInfectedSounds.play(mob,InfectedSoundCue.SWING);
            }return;
        }
        if(stage==BOOST||stage==RAM){
            // Aim is fixed at release. A lateral dodge cannot be homed in on later.
            velocity=heading.scale(stage==BOOST?.25+.5*Math.min(1,age/duration(BOOST)):.85);
            face(velocity,10);
            if(move(before,mob.flyerPose(0),velocity,true))return;
            if(stage==BOOST&&age>=duration(BOOST))mob.flyerStage(RAM);
            else if(stage==RAM&&(age>=duration(RAM)||aim.subtract(mob.position().add(0,.6,0)).dot(heading)<-.6))recover();
            return;
        }
        if(stage==RECOVER){
            velocity=velocity.lerp(new Vec3(heading.x*.24,.15,heading.z*.24),.18);face(velocity,5);
            move(before,mob.flyerPose(0),velocity,false);
            if(age>=duration(RECOVER)){mob.flyerStage(IDLE);target=null;}return;
        }
        if(stage==COLLISION||stage==HIT_FRONT||stage==HIT_BACK){
            velocity=velocity.scale(.8);mob.flightPitch(Mth.approachDegrees(mob.flightPitch(),0,4));
            move(before,mob.flyerPose(0),velocity,false);
            if(age>=duration(stage))recover();
        }
    }
    private void recover(){mob.finishFlyerAttack();mob.flyerStage(RECOVER);velocity=velocity.scale(.4);}
    private void face(Vec3 delta,float degrees){
        if(delta.lengthSqr()<1e-5)return;
        float yaw=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z));
        float pitch=Mth.clamp((float)-Math.toDegrees(Math.atan2(delta.y,delta.horizontalDistance())),-50,50);
        mob.setYRot(Mth.approachDegrees(mob.getYRot(),yaw,degrees));mob.yBodyRot=mob.getYRot();mob.yHeadRot=mob.getYRot();
        mob.flightPitch(Mth.approachDegrees(mob.flightPitch(),pitch,6));
    }
    private boolean move(float[][] before,float[][] after,Vec3 step,boolean attack){
        Vec3 from=mob.position(),to=from.add(step);float yaw=mob.getYRot();
        var broad=new AABB(from,to).inflate(3.5,3,3.5);
        if(!mob.isWithinRestriction(BlockPos.containing(to))||!BlockThrowCollision.loaded(mob.level(),from,to,new Vec3(3.5,3,3.5))){mob.cancelAttack();return true;}
        var terrain=new ArrayList<AABB>();for(var s:mob.level().getBlockCollisions(mob,broad))terrain.addAll(s.toAabbs());
        java.util.function.DoubleFunction<List<HydraContact.Box>> body=t->FlyerContact.body(FlyerContact.blend(before,after,(float)t),from.lerp(to,t),yaw).stream().map(FlyerContact.Surface::box).toList();
        var wall=HydraContact.obstacle(body,0,1,terrain);HydraContact.Obstacle first=wall;LivingEntity touched=null;
        if(attack&&!contacted)for(var v:mob.level().getEntitiesOfClass(LivingEntity.class,broad,mob::enemy)){
            var hit=HydraContact.obstacle(body,0,1,List.of(v.getBoundingBox()));
            if(hit!=null&&(first==null||hit.safeAge()<first.safeAge()-.0001F)){first=hit;touched=v;}
        }
        if(!attack&&wall!=null&&wall.safeAge()==0&&mob.level().noCollision(mob,mob.getBoundingBox().move(0,.2,0))){
            // A ground-spawned flyer can initially have a folded membrane below its
            // root box. Allow vertical separation, never sideways wall traversal.
            mob.move(MoverType.SELF,new Vec3(0,.2,0));mob.setDeltaMovement(Vec3.ZERO);return true;
        }
        Vec3 actual=first==null?step:step.scale(first.safeAge());mob.move(MoverType.SELF,actual);mob.setDeltaMovement(Vec3.ZERO);
        if(first==null)return false;
        if(touched!=null){
            contacted=true;lastContact=first.point();if(mob.flyerStrike(touched,lastContact,heading))hits++;
            recover();return true;
        }
        if(attack){
            collisions++;lastContact=first.point();
            boolean ground=step.y<0&&lastContact.y>=first.shape().maxY-.08;
            if(!ground||!TerrainImpactEffects.emit(mob,new BlockHitResult(new Vec3(lastContact.x,first.shape().maxY,lastContact.z),net.minecraft.core.Direction.UP,BlockPos.containing(first.shape().getCenter()),false),1.3F,false))ContactEffects.obstacle(mob,lastContact,first.shape());
            mob.finishFlyerAttack();mob.flyerStage(COLLISION);velocity=heading.scale(-.12);return true;
        }
        // During approach/recovery, seek free space above the obstruction.
        velocity=new Vec3(0,.18,0);return true;
    }
}
