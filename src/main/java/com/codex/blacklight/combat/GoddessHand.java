package com.codex.blacklight.combat;

import com.codex.blacklight.entity.BlacklightCommonInfected;
import com.codex.blacklight.entity.EncounterMob;
import com.codex.blacklight.entity.GreeneProjectile;
import com.codex.blacklight.entity.ThrownBlock;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.*;

/** The airborne colony survives between gestures. Its fingers and cargo belong to this hand only. */
public final class GoddessHand {
    private final EncounterMob boss;
    private final List<UUID> donors=new ArrayList<>();
    private int pattern,next,lost,side=1,skins,flinched=-1;
    private final float[] strain=new float[5];
    private long started,travelAt,kicked=-1000,cooldown;
    private Vec3 anchor=Vec3.ZERO,from=Vec3.ZERO,aim=Vec3.ZERO;
    private float yaw;
    private boolean forming,formed,acting;
    private UUID stone;
    private final GoddessPalmCombo combo=new GoddessPalmCombo();
    private boolean lastWasMelee;
    private BlockPos vein;
    GoddessHand(EncounterMob boss){this.boss=boss;}
    private long now(){return boss.level().isClientSide?boss.goddess.visualServerTick():boss.level().getGameTime();}
    private CompoundTag remote(){return boss.goddessData().getCompound("Offense").getCompound("Hand");}
    private static Vec3 read(CompoundTag t,String k){return new Vec3(t.getDouble(k+"X"),t.getDouble(k+"Y"),t.getDouble(k+"Z"));}
    private static void put(CompoundTag t,String k,Vec3 v){t.putDouble(k+"X",v.x);t.putDouble(k+"Y",v.y);t.putDouble(k+"Z",v.z);}
    int gripSide(){return boss.level().isClientSide?remote().getInt("Side"):side;}
    boolean readyForGrip(){return formed && !acting && !forming && lost==0 && stone==null;}
    public boolean visible(){return boss.level().isClientSide?remote().getBoolean("Visible"):forming||formed;}
    public int skins(){return boss.level().isClientSide?remote().getInt("Skins"):skins;}
    private Vec3 position(Vec3 start,Vec3 goal,long began,float partial){
        double t=Mth.clamp((now()-began+partial)/18.,0,1);return start.lerp(goal,t*t*(3-2*t));
    }
    public GoddessHandPose pose(float partial){
        if(!visible())return null;
        var t=boss.level().isClientSide?remote():null;
        boolean growing=t==null?forming:t.getBoolean("Forming");
        float age=now()-(t==null?started:t.getLong("Start"))+partial;
        double recoil=Mth.clamp(1-(now()-(t==null?kicked:t.getLong("Kick"))+partial)/11.,0,1);
        Vec3 pos=t==null?position(from,anchor,travelAt,partial):position(read(t,"From"),read(t,"At"),t.getLong("Travel"),partial);
        boolean playing=t==null?acting:t.getBoolean("Acting");
        var struggle=boss.goddess.attacks.struggle;
        if(struggle.active() && !struggle.hair())
            return new GoddessHandPose(struggle.palm(partial),struggle.palmYaw(partial),gripSide(),
                    4,struggle.step()==GoddessStruggle.Step.WARN?-1-struggle.age(partial):struggle.closure(partial),0,1,now()+partial,0);
        if(!growing && boss.goddess.attacks.move()==GoddessAttacks.Move.TENDRIL_CRUSH)
            return new GoddessHandPose(pos,t==null?yaw:t.getFloat("Yaw"),gripSide(),6,boss.goddess.attacks.age(partial),
                    t==null?lost:t.getInt("Lost"),1,now()+partial,recoil);
        if(playing && !growing && (t==null?pattern:t.getInt("Move"))==GoddessPalmCombo.MOVE)
            return comboPose(age);
        return new GoddessHandPose(pos,t==null?yaw:t.getFloat("Yaw"),t==null?side:t.getInt("Side"),
                playing&&!growing?(t==null?pattern:t.getInt("Move")):0,playing&&!growing?age:60,
                t==null?lost:t.getInt("Lost"),growing?.05+.95*Mth.clamp(age/35.,0,1):1,now()+partial,recoil);
    }
    int playingPattern(){
        if(boss.level().isClientSide){
            var t=remote();
            return t.getBoolean("Acting") && !t.getBoolean("Forming")?t.getInt("Move"):-1;
        }
        return acting && !forming?pattern:-1;
    }
    float gestureAge(float partial){
        return now()-(boss.level().isClientSide?remote().getLong("Start"):started)+partial;
    }
    public boolean melee(){
        if(boss.level().isClientSide){var t=remote();return t.getBoolean("Acting") && !t.getBoolean("Forming") && t.getInt("Move")==GoddessPalmCombo.MOVE;}
        return acting && !forming && pattern==GoddessPalmCombo.MOVE;
    }
    private float comboTime(float age){return GoddessPalmCombo.clock(age,boss.level().isClientSide?remote().getFloat("Stop"):combo.stopped());}
    public float controlTime(float partial){
        return comboTime(now()-(boss.level().isClientSide?remote().getLong("Start"):started)+partial);
    }
    GoddessHandPose comboPose(float age){
        var data=boss.level().isClientSide?remote():null;
        float t=comboTime(age),heading=data==null?yaw:data.getFloat("Yaw");
        int mirror=data==null?side:data.getInt("Side");
        Vec3 goal=data==null?anchor:read(data,"At");
        Vec3 start=data==null?from:read(data,"From");
        Vec3 home=start.lerp(goal,GoddessPalmCombo.ease(t/18.));
        Vec3 target=data==null?aim:read(data,"Aim"), drop=data==null?combo.drop():read(data,"Drop");
        float stop=data==null?combo.stopped():data.getFloat("Stop");
        Vec3 palm=GoddessPalmCombo.palm(home,target,drop,heading,mirror,t);
        if(stop>=42 && age>stop){
            Vec3 impact=GoddessPalmCombo.palm(goal,target,drop,heading,mirror,stop);
            float pull=GoddessPalmCombo.ease((age-stop-3)/19.);
            palm=impact.lerp(goal,pull).add(0,4*Math.sin(Math.PI*pull),0);
        }
        return new GoddessHandPose(palm,heading,mirror,GoddessPalmCombo.MOVE,t,data==null?lost:data.getInt("Lost"),1,0,0);
    }
    public float gesture(float partial){
        if(!visible())return 0;
        var t=boss.level().isClientSide?remote():null;
        float a=now()-(t==null?started:t.getLong("Start"))+partial;
        boolean playing=t==null?acting:t.getBoolean("Acting");
        boolean formingNow=t==null?forming:t.getBoolean("Forming");
        int digit=t==null?pattern:t.getInt("Move");
        float recoil=(float)Mth.clamp(1-(now()-(t==null?kicked:t.getLong("Kick"))+partial)/10.,0,1);
        return (float)(Mth.clamp(a/12,0,1)*(.32+(playing&&!formingNow?.39*GoddessHandPose.bend(digit,2,a+2):0))-.66*recoil);
    }
    private boolean open(ServerLevel l,Vec3 at){
        var box=new AABB(at.x-3.6,at.y-5.4,at.z-3.6,at.x+3.6,at.y+6.2,at.z+3.6);
        return l.hasChunkAt(BlockPos.containing(at)) && l.getWorldBorder().isWithinBounds(box)
                && !l.getBlockCollisions(boss,box).iterator().hasNext();
    }
    private boolean sight(Vec3 a,Vec3 b){
        return boss.level().clip(new ClipContext(a,b,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,boss)).getType()==HitResult.Type.MISS;
    }
    private boolean available(BlacklightCommonInfected m){
        var d=m.getPersistentData();
        return m.isAlive() && !m.isNoAi() && ConsumeSystem.eligible(m) && !ConsumeControl.protectedEntity(m) && !ConsumeSystem.inFlight(m)
                && (!d.hasUUID("EncounterOwner")||d.getUUID("EncounterOwner").equals(boss.getUUID()));
    }
    boolean begin(ServerLevel l,ServerPlayer p){
        if(!formed && now()<cooldown || (boss.goddess.removed()&3)==3)return false;
        side=(boss.goddess.removed()&1)==0?-1:1;
        var eye=p.getEyePosition();
        var toward=boss.position().subtract(eye).multiply(1,0,1).normalize();
        if(toward.lengthSqr()<.01)toward=new Vec3(0,0,1);
        Vec3 goal=null;
        for(double angle:new double[]{.93,-.93,.50,-.50,1.58,-1.58,0}){
            var at=eye.add(toward.yRot((float)(side*angle)).scale(13)).add(0,7,0);
            if(!open(l,at)||!sight(at,eye))continue;
            if(formed && (!open(l,position(from,anchor,travelAt,0).lerp(at,.5))||!sight(anchor,at)))continue;
            goal=at;break;
        }
        if(goal==null)return false;
        if(!formed){
            final Vec3 gather=goal;
            var bodies=l.getEntitiesOfClass(BlacklightCommonInfected.class,new AABB(goal,goal).inflate(30),this::available);
            bodies.removeIf(m->!sight(m.getEyePosition(),gather));
            bodies.sort(Comparator.comparingDouble(m->m.position().distanceToSqr(gather)));
            if(bodies.size()<3)return false;
            donors.clear();skins=0;
            for(int i=0;i<Math.min(8,bodies.size());i++){
                var m=bodies.get(i);donors.add(m.getUUID());skins|=m.appearance()<<(i*2);
            }
            forming=true;lost=0;Arrays.fill(strain,0);
        }
        // The hand has its own clear staging position beside its target, even across the cavern from Greene.
        pattern=lastWasMelee?Math.floorMod(next,4):GoddessPalmCombo.MOVE;
        if(pattern==3){
            vein=stoneSite(l,p,goal);
            if(vein==null || (lost&1)!=0)pattern=0;
        }
        from=formed?position(from,anchor,travelAt,0):goal;
        anchor=goal;travelAt=started=now();acting=true;aim=eye;flinched=-1;
        yaw=(float)Math.toDegrees(Math.atan2(goal.x-eye.x,eye.z-goal.z));
        if(pattern==GoddessPalmCombo.MOVE)prepareCombo(p);
        return true;
    }
    private void prepareCombo(ServerPlayer p){
        aim=boss.goddess.attacks.lead(p, 16);combo.begin(aim);
        Vec3 forward=aim.subtract(boss.position());
        yaw=(float)Math.toDegrees(Math.atan2(-forward.x,forward.z));
    }
    private BlockPos stoneSite(ServerLevel l,ServerPlayer p,Vec3 at){
        for(int i=0;i<12;i++){
            double a=i*Math.PI/6;
            var h=l.clip(new ClipContext(at,at.add(28*Math.sin(a),-4,28*Math.cos(a)),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,boss));
            if(h.getType()==HitResult.Type.BLOCK && h.getDirection().getAxis().isHorizontal() && canLift(l,p,h.getBlockPos()))return h.getBlockPos();
        }
        return null;
    }
    boolean canLift(ServerLevel l,ServerPlayer p,BlockPos pos){
        return l.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING) && BlockThrowSystem.selectable(p,pos)
                && net.minecraftforge.event.ForgeEventFactory.getMobGriefingEvent(l,boss) && net.minecraftforge.common.ForgeHooks.canEntityDestroy(l,pos,boss);
    }
    private void releaseRock(Vec3 velocity){
        if(stone!=null && boss.level() instanceof ServerLevel l && l.getEntity(stone) instanceof FallingBlockEntity block){
            block.setNoGravity(false);block.setDeltaMovement(velocity);block.hurtMarked=true;
        }
        stone=null;vein=null;
    }
    void cancel(){
        if(visible()){cooldown=Math.max(cooldown,now()+100);SpiderFoley.play(boss,anchor,SpiderFoley.Cue.SHED,1);}
        formed=forming=acting=false;donors.clear();releaseRock(Vec3.ZERO);
    }
    boolean tick(ServerLevel l,ServerPlayer p,GoddessVolley volley){
        if(!acting)return true;
        int t=(int)(now()-started);
        if(forming){
            int close=0;
            for(UUID id:donors){
                if(!(l.getEntity(id) instanceof BlacklightCommonInfected m)||!available(m))continue;
                Vec3 delta=anchor.subtract(m.getBoundingBox().getCenter());
                if(!sight(m.getEyePosition(),anchor))continue;
                m.getNavigation().stop();m.setTarget(null);m.fallDistance=0;
                m.setDeltaMovement(Vec3.ZERO);
                m.move(MoverType.SELF,delta.length()<1?delta:delta.normalize().scale(.95));
                if(delta.length()<2.4)close++;
                if(t%4==0)SpiderFoley.strands(boss,m.getEyePosition(),delta,3);
            }
            if(t<35)return false;
            if(close<3){if(t<49)return false;cancel();return true;}
            for(UUID id:donors)if(l.getEntity(id) instanceof BlacklightCommonInfected m && available(m) && m.getBoundingBox().getCenter().distanceToSqr(anchor)<6.25)m.discard();
            donors.clear();forming=false;formed=true;started=now();cooldown=now()+440;
            if(pattern==GoddessPalmCombo.MOVE){from=anchor;prepareCombo(p);}
            SpiderFoley.play(boss,anchor,SpiderFoley.Cue.CATCH,1.6F);
            boss.goddess.publish();boss.goddess.paid(3000);
            return false;
        }
        if(pattern==GoddessPalmCombo.MOVE){
            if(combo.stopped()<0 && (t==1 || t==23))SpiderFoley.play(boss,boss.hurtRegions(0).get(boss.goddess.supportingWrist()).bounds().getCenter(),
                    t==1?SpiderFoley.Cue.LOAD:SpiderFoley.Cue.STRAIN,1.8F);
            if(combo.stopped()<0 && (t==12 || t==43))SpiderFoley.play(boss,pose(0).palm(),t==12?SpiderFoley.Cue.SHED:SpiderFoley.Cue.STRIKE,1.9F);
            if(t==27 && combo.stopped()<0){
                Vec3 target=boss.goddess.attacks.lead(p, 21),offset=target.subtract(aim);
                if(offset.length()>12)target=aim.add(offset.normalize().scale(12));
                if(sight(aim.add(0,8,0),target.add(0,6,0)))combo.aimDrop(target);
                boss.goddess.publish();
            }
            if(!combo.tick(l,boss,this,t))return false;
            from=comboPose(t).palm();travelAt=now();lastWasMelee=true;acting=false;return true;
        }
        var pose=pose(0);
        int untilShot = switch (pattern) {
            case 0 -> t == 7 ? 6 : t == 29 ? 10 : 0;
            case 1 -> t >= 11 && t <= 39 && (t - 11) % 7 == 0 ? 6 : 0;
            case 2 -> t == 27 ? 11 : 0;
            case 3 -> t == 31 ? 10 : 0;
            default -> 0;
        };
        if (untilShot > 0) {
            double speed = pattern == 1 ? 1.65 : pattern == 3 ? 1.5 : 2.0;
            double range = pose.palm().distanceTo(p.getBoundingBox().getCenter());
            aim = boss.goddess.attacks.lead(p, untilShot + range / speed);
            boss.goddess.publish();
        }
        if(pattern==0 && (t==13||t==39)){
            int finger=t==13?1:2;
            var tip=pose.tip(finger);
            if(tip!=null)flesh(l,p,tip,flinched==finger,false);
        }else if(pattern==1 && t>=17 && t<=45 && (t-17)%7==0){
            int finger=4-(t-17)/7;var tip=pose.tip(finger);
            if(tip!=null){
                var heading=aim.subtract(tip).normalize().yRot((finger-1.8F)*.19F*side);
                for(float lane:new float[]{-.045F,.045F})volley.quill(l,tip,heading.yRot(lane),1.65,14,false);
                SpiderFoley.play(boss,tip,SpiderFoley.Cue.NEEDLE,1);
            }
        }else if(pattern==2 && t==38){flesh(l,p,pose.palm(),false,true);kicked=now();boss.goddess.publish();}
        else if(pattern==3 && t==16 && vein!=null && canLift(l,p,vein) && BlockThrowSystem.mayExtract(p,vein,l.getBlockState(vein))){
            var state=l.getBlockState(vein);
            var block=FallingBlockEntity.fall(l,vein,state);
            if(!block.isRemoved()&&l.getEntity(block.getUUID())==block){
                stone=block.getUUID();block.setNoGravity(true);block.setHurtsEntities(2.5F,35);
                block.getPersistentData().putUUID("EncounterOwner",boss.getUUID());
                block.getPersistentData().putInt("GoddessSequence",boss.goddess.attacks.sequence());
                boss.goddess.publish();
                SpiderFoley.play(boss,block.position(),SpiderFoley.Cue.FRACTURE,1.35F);
            }
        }
        if(stone!=null && l.getEntity(stone) instanceof FallingBlockEntity block){
            var grip=pose.palm().add(0,.55,0);
            if(t<41){var d=grip.subtract(block.position());block.setDeltaMovement(d.scale(Math.min(.32,1.45/Math.max(.001,d.length()))));block.hurtMarked=true;}
            else {releaseRock(aim.subtract(grip).normalize().scale(1.5).add(0,.20,0));kicked=now();boss.goddess.publish();}
        }
        if(t<(pattern==2?51:pattern==1?52:pattern==3?48:47))return false;
        lastWasMelee=false;
        acting=false;next=(pattern+1)%4;releaseRock(Vec3.ZERO);return true;
    }
    private void flesh(ServerLevel l,ServerPlayer p,Vec3 muzzle,boolean deflect,boolean cannon){
        var d=aim.subtract(muzzle).normalize();if(deflect)d=d.yRot(.58F*side);
        var ball=new GreeneProjectile(l,boss,GreeneMissileFlight.Ammo.SHOTGUN,GreeneMissileFlight.User.SHOTGUN,muzzle,d,p).asFlesh();
        ball.getPersistentData().putBoolean("GoddessHandShot",true);
        ball.cannon(cannon);
        ball.getPersistentData().putInt("GoddessSequence",boss.goddess.attacks.sequence());
        l.addFreshEntity(ball);SpiderFoley.play(boss,muzzle,cannon?SpiderFoley.Cue.RUPTURE:SpiderFoley.Cue.SHED,cannon?1.8F:1.2F);
    }
    public void hitController(DamageSource source,HydraHurtContact.Contact contact){
        if(!forming || contact==null || !(source.getEntity() instanceof ServerPlayer)
                || GoddessRig.PARTS.indexOf(contact.bone())!=boss.goddess.supportingWrist())return;
        cancel();
        boss.goddess.publish();
    }
    public boolean hit(int slot,DamageSource source,float damage,Vec3 point){
        if(!formed || !(source.getEntity() instanceof ServerPlayer) || slot<GoddessHandPose.WRIST || slot>18)return false;
        int finger=slot-GoddessHandPose.THUMB;
        if(finger>=0 && (lost&(1<<finger))!=0)return false;
        kicked=now();
        if(finger>=0){
            flinched=finger;strain[finger]+=damage;
            if(strain[finger]>=36)lost|=1<<finger;
            if(finger==0&&stone!=null)releaseRock(Vec3.ZERO);
        }else if(slot==GoddessHandPose.WRIST && acting){
            boolean rock=source.getDirectEntity() instanceof ThrownBlock block && block.chargedImpact();
            boolean charging=pattern==2 && now()-started<38;
            if(charging){
                var palm=pose(0).palm();
                SpiderFoley.play(boss,palm,SpiderFoley.Cue.RUPTURE,1.8F);
                for(var link:pose(0).links())
                    if(link.slot()>=GoddessHandPose.THUMB)SpiderFoley.strands(boss,palm,link.end().subtract(palm),3);
                acting=false;next=3;
            }
            if(rock && stone!=null){
                var sideways=Vec3.directionFromRotation(0,yaw+side*79).scale(1.5).add(0,.28,0);
                releaseRock(sideways);acting=false;next=0;
                boss.goddess.stagger(new HydraHurtContact.Contact(GoddessRig.PARTS.get(slot),point,sideways.normalize(),com.codex.blacklight.infection.InfectedAttackStyle.CRUSH));
            }
        }
        SpiderFoley.play(boss,point,SpiderFoley.Cue.TEAR,1.2F);
        SpiderFoley.strands(boss,point,point.subtract(anchor),8);
        if(lost==31)cancel();
        boss.goddess.publish();return true;
    }
    boolean clipPounce(Vec3 previous, Vec3 current, double radius) {
        var pose=pose(0);
        if(pose==null || pose.palm().distanceToSqr(current)>13*13)return false;
        for(var link:pose.links()){
            var bounds=new net.minecraft.world.phys.AABB(link.base(),link.end()).inflate(Math.max(link.width(),link.depth())+radius);
            if(!bounds.contains(previous) && bounds.clip(previous,current).isEmpty())continue;
            if(ProjectileSphereContact.sweep(previous,current,radius,link.surface())==null)continue;
            kicked=now();
            flinched=Math.max(0,link.slot()-GoddessHandPose.THUMB);
            SpiderFoley.strands(boss,current,current.subtract(previous),9);
            SpiderFoley.play(boss,current,SpiderFoley.Cue.STRIKE,1.55F);
            boss.goddess.publish();
            return true;
        }
        return false;
    }
    CompoundTag save(){
        var t=new CompoundTag();
        t.putBoolean("Visible",forming||formed);t.putBoolean("Forming",forming);t.putBoolean("Formed",formed);t.putBoolean("Acting",acting);
        t.putInt("Move",pattern);t.putInt("Next",next);t.putInt("Lost",lost);t.putInt("Side",side);t.putInt("Skins",skins);t.putInt("Flinch",flinched);
        t.putBoolean("LastMelee",lastWasMelee);if(pattern==GoddessPalmCombo.MOVE)combo.save(t);
        t.putLong("Start",started);t.putLong("Travel",travelAt);t.putLong("Kick",kicked);t.putLong("Cooldown",cooldown);
        t.putFloat("Yaw",yaw);put(t,"Aim",aim);put(t,"At",anchor);put(t,"From",from);
        for(int i=0;i<5;i++)t.putFloat("Strain"+i,strain[i]);
        for(int i=0;i<donors.size();i++)t.putUUID("Donor"+i,donors.get(i));
        if(stone!=null)t.putUUID("Stone",stone);if(vein!=null)t.putLong("Vein",vein.asLong());
        return t;
    }
    void load(CompoundTag t){
        forming=t.getBoolean("Forming");formed=t.getBoolean("Formed");acting=t.getBoolean("Acting");
        lastWasMelee=t.getBoolean("LastMelee");combo.load(t);
        pattern=t.getInt("Move");next=t.getInt("Next");lost=t.getInt("Lost")&31;side=t.getInt("Side");
        skins=t.getInt("Skins");flinched=t.getInt("Flinch");started=t.getLong("Start");travelAt=t.getLong("Travel");
        kicked=t.getLong("Kick");cooldown=t.getLong("Cooldown");yaw=t.getFloat("Yaw");aim=read(t,"Aim");anchor=read(t,"At");from=read(t,"From");
        for(int i=0;i<5;i++)strain[i]=t.getFloat("Strain"+i);
        donors.clear();for(int i=0;i<8;i++)if(t.hasUUID("Donor"+i))donors.add(t.getUUID("Donor"+i));
        stone=t.hasUUID("Stone")?t.getUUID("Stone"):null;vein=t.contains("Vein")?BlockPos.of(t.getLong("Vein")):null;
    }
}
