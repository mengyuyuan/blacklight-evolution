package com.codex.blacklight.combat;

import com.codex.blacklight.entity.*;
import com.codex.blacklight.registry.ModEntities;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.*;
import java.util.*;

/** Server action clock. Each swing, contact and release has one owner and one saved target. */
public final class SpiderActions {
    public enum Move { REST, STAB, SWEEP, PINCER, SPIKES, SLAM, POUNCE, CATALYZE, SWALLOW, RECOIL, MARCH, ORAL_SCISSORS, POD_CAST, HEAVY_STAMP, FLESH_VOLLEY }
    private static final int[] BYPASS={32,62,88,110,134};
    // A quick stab and the counterable stamp use the same unarmoured damage units as brood melee.
    private static final float MELEE_DAMAGE=44F;
    private static final int[] VOLLEY_RELEASE={15,22,29,46,50};
    private int detourSide;
    private long detourUntil;
    private double closingDistance;
    private UUID observedTarget;
    private long observedTick;
    private Vec3 observedPosition=Vec3.ZERO, walkingVelocity=Vec3.ZERO;

    private final EncounterMob boss;
    public SpiderActions(EncounterMob boss){this.boss=boss;}
    private CompoundTag data(){return boss.spiderData();}
    private long now(){return boss.level().getGameTime();}
    public Move move(){int v=data().getInt("Action");return v>=0&&v<Move.values().length?Move.values()[v]:Move.REST;}
    public float age(float partial){return Math.max(0,now()-data().getLong("Start")+partial);}
    private void set(Move move,Entity target,Vec3 aim,int leg){
        var t=data().copy();
        t.putFloat("IdleOnEntry",SpiderIdle.weight(boss,1));
        float facing=boss.skinYaw(1);
        t.putFloat("ActionYaw",facing);
        t.putFloat("Facing",facing);t.putFloat("FaceFrom",facing);t.putLong("FaceTick",now());
        if(move!=Move.REST && move!=Move.RECOIL)t.putInt("LastAttack",move.ordinal());
        t.putInt("Action",move.ordinal());t.putLong("Start",now());t.putBoolean("Struck",false);
        t.remove("SecondStab");t.remove("FirstStabPoint");
        if(move==Move.STAB && (t.getInt("Turns")&4)==0)t.putBoolean("SecondStab",true);
        t.remove("PryAt");t.remove("Gripped");t.remove("Missed");t.remove("ActionTarget");t.remove("ActionId");
        t.remove("SeedX");t.remove("SeedY");t.remove("SeedZ");t.remove("SeedFlight");t.remove("SeedHit");
        t.remove("SpikeRows");t.remove("RuptureHits");t.remove("Lodged");t.remove("VolleyMask");t.remove("RushStopped");t.remove("RushSecond");
        if(target!=null){t.putUUID("ActionTarget",target.getUUID());t.putInt("ActionId",target.getId());}
        t.putDouble("Ax",aim.x);t.putDouble("Ay",aim.y);t.putDouble("Az",aim.z);t.putInt("Foot",leg);
        if(move==Move.MARCH){
            t.putInt("RushSecond",intact(aim,leg<4?4:0,leg<4?8:4));
            t.putDouble("RushX",boss.getX());t.putDouble("RushZ",boss.getZ());
        }
        boss.spiderData(t);
        if(move==Move.HEAVY_STAMP || move==Move.STAB || move==Move.SWEEP || move==Move.SLAM || move==Move.PINCER)
            boss.spider.footing.brace(move,leg,aim);
    }
    public Vec3 aim(){return new Vec3(data().getDouble("Ax"),data().getDouble("Ay"),data().getDouble("Az"));}
    private boolean clear(Vec3 from,Vec3 to){return boss.level().clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,boss)).getType()==HitResult.Type.MISS;}
    private Entity actor(){return data().hasUUID("ActionTarget") && boss.level() instanceof ServerLevel l?l.getEntity(data().getUUID("ActionTarget")):null;}
    private void observe(ServerPlayer player){
        long tick=now();
        if(player.getUUID().equals(observedTarget) && observedTick==tick)return;
        var delta=player.position().subtract(observedPosition).multiply(1,0,1);
        if(player.getUUID().equals(observedTarget) && observedTick==tick-1 && delta.lengthSqr()<1.5*1.5){
            walkingVelocity=walkingVelocity.lerp(delta,.55);
        }else walkingVelocity=Vec3.ZERO;
        observedPosition=player.position();observedTarget=player.getUUID();observedTick=tick;
    }
    private void commitAim(ServerPlayer player,double leadTicks,double maxLead,boolean ground){
        var lead=walkingVelocity.scale(leadTicks);
        if(lead.lengthSqr()>maxLead*maxLead)lead=lead.normalize().scale(maxLead);
        var point=player.position().add(lead);
        if(ground){
            var floor=SpiderRupture.floor(boss,player.position(),point);
            if(floor==null)return;
            point=floor;
        }
        var next=data().copy();next.putDouble("Ax",point.x);next.putDouble("Ay",point.y);next.putDouble("Az",point.z);
        boss.spiderData(next);
    }
    private void rest(int delay){
        closingDistance=0;
        if(move()==Move.SWALLOW && actor() instanceof ServerPlayer held && data().getBoolean("Gripped")
                && held.getData(ModAttachments.PLAYER_ACTION).clipId().startsWith("greene_mouth_")){
            com.codex.blacklight.animation.NativePlayerActionBackend.clear(held);
        }
        releaseGrip();set(Move.REST,null,boss.position(),-1);
        var t=data().copy();t.putLong("NextMove",now()+delay);boss.spiderData(t);
    }
    public void cancel(){rest(18);}
    public float crouch(float partial){
        if(move()!=Move.SLAM)return 0;
        float t=age(partial);
        // Keep the hand on its world-space target while the thorax is lifting the catch.
        return SpiderGesture.phrase(t,1,17,30,41);
    }
    public Vec3 handTarget(float partial){
        float t=age(partial);
        boolean caught=data().getBoolean("Gripped");
        double lift=caught?SpiderGesture.phrase(t,19,26,27,30):0;
        double side=1.85*SpiderGesture.phrase(t,1,9,10,17)-.95*lift;
        double loaded=data().getBoolean("Gripped")?.28*SpiderGesture.phrase(t,17,19,20,23):0;
        var across=new Vec3(side,0,1.35*lift).yRot((float)Math.toRadians(-boss.skinYaw(partial)));
        double brace=data().getBoolean("Missed")?.80*SpiderGesture.phrase(t,18,23,28,37):0;
        return aim().add(across).add(0,1.20+3.6*lift-loaded-brace,0);
    }
    public void leaveShell(){
        float yaw=boss.skinYaw(1);
        var local=new org.joml.Matrix4f().rotateY((float)Math.toRadians(180-yaw)).scale(1.9824204F).invert().mul(SpiderAnatomy.root(boss,1));
        var joints=SpiderAnatomy.MOLT_JOINTS;int[] endPose=new int[joints.size()*16];
        float[] values=new float[16];
        for(int i=0;i<joints.size();i++){
            boss.spider.jointMatrix(local,joints.get(i),1).get(values);
            for(int j=0;j<16;j++)endPose[i*16+j]=Float.floatToIntBits(values[j]);
        }
        cancel();var t=data().copy();t.putIntArray("LastPose",endPose);
        boss.spiderData(t);
    }
    private void releaseGrip(){
        if(actor() instanceof ServerPlayer p && p.getPersistentData().hasUUID("GreeneGrasp")
            && boss.getUUID().equals(p.getPersistentData().getUUID("GreeneGrasp"))){
            p.getPersistentData().remove("GreeneGrasp");p.fallDistance=0;
        }
    }
    public static void escape(ServerPlayer p){
        if(!p.getPersistentData().hasUUID("GreeneGrasp"))return;
        if(p.serverLevel().getEntity(p.getPersistentData().getUUID("GreeneGrasp")) instanceof EncounterMob owner){
            var action=owner.spider.actions;
            int age=(int)action.age(0);
            if(owner.spider.live() && action.move()==Move.SWALLOW && action.data().getBoolean("Gripped")
                    && action.actor()==p && !action.data().contains("PryAt")
                    && age>=SpiderMouthStruggle.PROMPT && age<=SpiderMouthStruggle.LAST_PRESS){
                var state=action.data().copy();state.putInt("PryAt",age);owner.spiderData(state);
                com.codex.blacklight.animation.PlayerActions.get().playVisual(p,"greene_mouth_kick",.08F);
                p.displayClientMessage(Component.translatable("boss.blacklight.greene_spider.pry"),true);
                SpiderFoley.play(owner,p.position(),SpiderFoley.Cue.STRAIN,1.12F);
            }
        }
    }
    private int intact(Vec3 target){return intact(target,0,8);}
    private int intact(Vec3 target,int start,int end){
        int best=-1;double score=Double.MAX_VALUE;
        for(int leg=start;leg<end;leg++)if(!boss.spider.broken(leg)){
            var tip=SpiderLegPose.worldTip(boss,leg,1);
            double dist=tip.distanceToSqr(target);
            if(dist<score){score=dist;best=leg;}
        }
        return best;
    }
    public void moltBrood(ServerLevel level,int age){
        brood(level,true,age%22==0 && age<=154);
    }
    public void spawnBrood(ServerLevel level){
        brood(level,false,boss.tickCount%75==1);
    }
    private void brood(ServerLevel level,boolean molting,boolean choose){
        var state=data();var anchors=state.getLongArray("DenNests");
        if(anchors.length!=6){
            if(!choose)return;
            var site=com.codex.blacklight.world.GreeneHiveCave.nearest(level.getSeed(),boss.blockPosition());
            if(site.distSqr(boss.blockPosition())>52*52)return;
            anchors=new long[6];
            for(int i=0;i<6;i++)anchors[i]=com.codex.blacklight.world.GreeneHiveCave.sac(site,i).asLong();
            state=state.copy();state.putLongArray("DenNests",anchors);boss.spiderData(state);
        }
        boolean allowed=molting || level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING);
        if(state.contains("HatchDue")){
            long since=now()-state.getLong("HatchDue");
            if(since<0 && allowed)return;
            int socket=state.getInt("HatchSlot"),breed=state.getInt("HatchBreed");
            var occupant=state.getCompound("HatchChild");
            var next=state.copy();next.remove("HatchDue");next.remove("HatchChild");boss.spiderData(next);
            if(allowed && since>=0 && since<=20 && socket>=0 && socket<6)hatch(level,socket,BlockPos.of(anchors[socket]),breed,occupant);
            return;
        }
        if(!choose || !allowed)return;
        var brood=level.getEntitiesOfClass(Mob.class,boss.getBoundingBox().inflate(54),SpiderPhase::edible);
        // A brood made up entirely of evolved hosts still needs one source of natural food.
        boolean needsHunter=!molting && brood.size()>=13 && brood.stream().allMatch(SpiderPhase::reservedForThrow);
        if(brood.size()>=(molting?25:13) && !needsHunter)return;
        int start=boss.getRandom().nextInt(6);
        for(int i=0;i<6;i++){
            int socket=(start+i)%6;var anchor=BlockPos.of(anchors[socket]);
            if(!nestExists(level,anchor))continue;
            var next=state.copy();next.putInt("HatchSlot",socket);next.putInt("HatchBreed",needsHunter?2:boss.getRandom().nextInt(molting?5:12));
            var child=broodType(next.getInt("HatchBreed")).create(level);
            if(child==null)return;
            var occupant=new CompoundTag();child.saveWithoutId(occupant);next.put("HatchChild",occupant);
            next.putLong("HatchDue",now()+18);next.putLong("HatchStart",now());boss.spiderData(next);
            SpiderFoley.play(boss,Vec3.atCenterOf(anchor),SpiderFoley.Cue.STRAIN,.95F);
            return;
        }
    }
    private static boolean nestExists(ServerLevel level,BlockPos p){
        return level.hasChunkAt(p) && level.getBlockState(p).is(com.codex.blacklight.registry.ModBlocks.TISSUE_ATTACHMENT.get());
    }
    public static EntityType<? extends Mob> broodType(int breed){
        return switch(breed){
            case 0->ModEntities.BLACKLIGHT_STALKER.get();
            case 1->ModEntities.BLACKLIGHT_SPITTER.get();
            case 2->ModEntities.BLACKLIGHT_HUNTER.get();
            default->ModEntities.COMMON_INFECTED.get();
        };
    }
    private void hatch(ServerLevel level,int socket,BlockPos anchor,int breed,CompoundTag occupant){
        if(!nestExists(level,anchor))return;
        Mob child=broodType(breed).create(level);if(child==null)return;
        if(!occupant.isEmpty())child.load(occupant);
        var normal=com.codex.blacklight.world.GreeneHiveCave.support(socket).getOpposite();
        Vec3 spawn=Vec3.atBottomCenterOf(anchor.relative(normal,3));
        float yaw=normal.getAxis().isVertical()?0:normal.toYRot();
        child.moveTo(spawn.x,spawn.y,spawn.z,yaw,0);child.yBodyRot=yaw;child.yHeadRot=yaw;
        if(!level.noCollision(child))return;
        child.getPersistentData().putBoolean("GreeneBroodFall",true);
        child.getPersistentData().putUUID("EncounterOwner",boss.getUUID());
        if(level.addFreshEntity(child)){
            var out=boss.spiderData().copy();out.putLong("HatchedAt",now());boss.spiderData(out);
            child.setDeltaMovement(Vec3.atLowerCornerOf(normal.getNormal()).scale(.16));
            level.sendParticles(ParticleTypes.CRIMSON_SPORE,spawn.x,spawn.y+1.1,spawn.z,10,.4,.28,.4,.045);
            SpiderFoley.play(boss,Vec3.atCenterOf(anchor),SpiderFoley.Cue.SHED,1.35F);
            SpiderFoley.strands(boss,Vec3.atCenterOf(anchor),spawn.subtract(Vec3.atCenterOf(anchor)),6);
        }
    }
    public void tick(ServerLevel level){
        spawnBrood(level);
        if(move()!=Move.REST){advance(level);return;}
        if(boss.spider.feeding() || boss.spider.tilt(0)!=0)return;
        var p=level.getNearestPlayer(boss.getX(),boss.getY(),boss.getZ(),112,who->who instanceof ServerPlayer survivor && survivor.isAlive() && !survivor.isCreative() && !survivor.isSpectator());
        if(!(p instanceof ServerPlayer player) || player.isSpectator() || player.isCreative())return;
        observe(player);
        Vec3 diff=player.position().subtract(boss.position());
        float facing=(float)Math.toDegrees(Math.atan2(-diff.x,diff.z));
        float speed=4.8F-.27F*Integer.bitCount(boss.spider.brokenMask());
        float previous=boss.skinYaw(1);
        float yaw=Mth.approachDegrees(previous,facing,speed);
        if(!data().contains("Facing") || Math.abs(Mth.wrapDegrees(yaw-previous))>.001F){
            var st=data().copy();st.putFloat("FaceFrom",previous);st.putFloat("Facing",yaw);
            st.putLong("FaceTick",now());boss.spiderData(st);
        }
        boss.setYRot(yaw);boss.yBodyRot=yaw;boss.yHeadRot=yaw;
        {
            // Reposition between commitments; refuse an unsupported ledge or a solid wall.
            double range=diff.horizontalDistance();
            if(range>10){
                int lost=boss.spider.brokenMask(),limbs=8-Integer.bitCount(lost);
                double pace=(range>24?.54:.37)-.032*Integer.bitCount(lost);
                if(Math.abs(Mth.wrapDegrees(yaw-facing))>75)pace*=.55;
                if(limbs<=2){
                    // On the belly the forearms drag the shell only during the planted half-stroke.
                    double stroke=Math.sin(((now()%36)-6)*Math.PI/18);
                    pace=(.035+.012*limbs)*Math.max(0,stroke);
                }else if((lost&15)==15 || (lost&240)==240)pace*=.52;
                var stride=diff.multiply(1,0,1).normalize().scale(pace);
                var before=boss.position();
                takeStep(stride);
                closingDistance+=boss.position().subtract(before).horizontalDistance();
            }
            if(now()<data().getLong("NextMove"))return;
            // Between commitments follow the live target, even if the previous rush struck a wall.
            if(range>24 && data().getInt("Turns")>0 && closingDistance<9 && age(0)<36)return;
        }
        float angle=Math.abs(Mth.wrapDegrees(yaw-facing));
        // An exposed flank is in reach before the shell has turned to face the intruder.
        boolean flank=angle>27 && angle<105 && diff.horizontalDistanceSqr()<18*18
                && data().getInt("LastAttack")!=Move.SWEEP.ordinal();
        int count=data().getInt("Turns"),foot=intact(player.position());
        Vec3 point=player.position();
        boolean nearFoot=foot>=0 && SpiderLegPose.worldTip(boss,foot,1).distanceToSqr(point)<9*9;
        boolean stamp=nearFoot && data().getInt("LastAttack")!=Move.HEAVY_STAMP.ordinal();
        if(angle>27 && !flank && !stamp)return;
        var prey=level.getEntitiesOfClass(BlacklightCommonInfected.class,boss.getBoundingBox().inflate(31),m->m.isAlive() && !ConsumeControl.protectedEntity(m));
        if(!stamp && !flank && count%4==2 && !prey.isEmpty()){
            Mob chosen=prey.get(boss.getRandom().nextInt(prey.size()));
            set(Move.CATALYZE,chosen,chosen.getBoundingBox().getCenter(),-1);
        }else{
            double d=diff.horizontalDistance();
            Move next;
            Move injured=SpiderWoundedActions.select(boss.spider.brokenMask(),d,count);
            if(stamp)next=Move.HEAVY_STAMP;
            else if(injured!=null)next=injured;
            else if(flank && foot>=0)next=Move.SWEEP;
            else if(d>21){
                // After denying a distant lane she leaves her old position. A sustained retreat changes the fight.
                boolean canMarch=boss.spider.footing.pairedSupport() && foot>=0 && intact(point,foot<4?4:0,foot<4?8:4)>=0;
                next=canMarch && data().getInt("LastAttack")!=Move.MARCH.ordinal()?Move.MARCH:
                        data().getInt("LastAttack")!=Move.FLESH_VOLLEY.ordinal() && (count%3!=2 || d>36)?Move.FLESH_VOLLEY:Move.SPIKES;
            }
            else if(count%6==4 && d<17 && foot>=0 && now()>=data().getLong("SafeFromMouth"))next=Move.SWALLOW;
            else if(d<12 && count%3==1)next=Move.SLAM;
            else if(foot<0)next=Move.SPIKES;
            else if(boss.getHealth()<boss.getMaxHealth()*.6F && count%5==3){
                if(wall())next=Move.POUNCE;
                else next=boss.spider.footing.pairedSupport() && intact(point,foot<4?4:0,foot<4?8:4)>=0?Move.MARCH:Move.STAB;
            }
            else next=switch(count%4){case 0 -> Move.STAB;case 1 -> Move.PINCER;case 2 -> Move.SWEEP;default -> Move.SPIKES;};
            if((next==Move.PINCER || next==Move.SWALLOW) && (foot<0 || boss.spider.broken((foot+4)%8)))
                next=foot<0?Move.ORAL_SCISSORS:Move.STAB;
            if(next==Move.HEAVY_STAMP){
                var floor=level.clip(new ClipContext(point.add(0,1,0),point.add(0,-3,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,boss));
                if(floor.getType()==HitResult.Type.BLOCK && floor.getDirection()==net.minecraft.core.Direction.UP)point=floor.getLocation();
                else next=Move.SWEEP;
            }
            set(next,player,point,foot);
            if(foot>=0 && (next==Move.HEAVY_STAMP||next==Move.STAB||next==Move.PINCER||next==Move.SWEEP))SpiderFoley.play(boss,SpiderLegPose.worldTip(boss,foot,1),SpiderFoley.Cue.LOAD,1.15F);
        }
        var t=data().copy();t.putInt("Turns",count+1);boss.spiderData(t);
    }
    private void takeStep(Vec3 stride){
        if(stride.lengthSqr()<1e-7)return;
        // Keep the chosen shoulder around a pillar until the forward lane opens again.
        boolean bypass=detourSide!=0 && now()<detourUntil;
        if((!bypass || clearance(stride.normalize().scale(2.6))>=0) && advanceShell(stride)){
            detourSide=0;
            return;
        }
        int side=bypass?detourSide:(boss.getId()%2==0?1:-1);
        for(int pass=0;pass<2;pass++,side=-side){
            for(int heading:BYPASS){
                if(advanceShell(stride.yRot((float)Math.toRadians(side*heading)))){
                    detourSide=side;
                    detourUntil=now()+48;
                    return;
                }
            }
        }
    }
    // Highest tread swept by the whole belly, including ridges outside the center ray.
    // A ceiling or a tall wall cannot supply a tread within the allowed rise.
    private double clearance(Vec3 stride){
        var bounds=boss.getBoundingBox().deflate(.008);
        var lane=bounds.expandTowards(stride);
        double rise=0;
        for(var shape:boss.level().getBlockCollisions(boss,lane)){
            rise=Math.max(rise,shape.max(net.minecraft.core.Direction.Axis.Y)-boss.getY());
            if(rise>(Integer.bitCount(boss.spider.brokenMask())<=3?2.01:1.01))return -1;
        }
        if(rise<=0)return 0;
        var crown=new AABB(bounds.minX,bounds.maxY,bounds.minZ,bounds.maxX,bounds.maxY+rise+.01,bounds.maxZ);
        if(boss.level().getBlockCollisions(boss,crown.expandTowards(stride)).iterator().hasNext())return -1;
        return rise;
    }
    private boolean advanceShell(Vec3 stride){
        double rise=clearance(stride);
        if(rise<0)return false;
        var bounds=boss.getBoundingBox();
        var forward=stride.normalize();
        double half=(bounds.maxX-bounds.minX)*.5;
        var front=boss.position().add(stride).add(forward.scale(half*.82));
        var across=new Vec3(forward.z,0,-forward.x).scale(half*.62);
        int supported=0;
        for(int sample=-1;sample<=1;sample++){
            var at=front.add(across.scale(sample));
            var ray=boss.level().clip(new ClipContext(at.add(0,rise+1.51,0),at.add(0,-(half+1.85),0),
                    ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,boss));
            if(ray.getType()==HitResult.Type.BLOCK && ray.getDirection()==net.minecraft.core.Direction.UP)supported++;
        }
        if(supported<2)return false;
        var before=boss.position();
        if(rise>0)boss.move(MoverType.SELF,new Vec3(0,rise,0));
        boss.move(MoverType.SELF,stride);
        boss.move(MoverType.SELF,new Vec3(0,-.52,0));
        return boss.position().subtract(before).dot(forward)>=stride.length()*.6;
    }
    private boolean wall(){
        if(!boss.spider.footing.pairedSupport())return false;
        var from=boss.position().add(0,7,0);
        BlockHitResult best=null;
        for(int i=0;i<12;i++){
            double a=i*Math.PI/6;
            var to=from.add(Math.cos(a)*43,6,Math.sin(a)*43);
            var hit=boss.level().clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,boss));
            if(hit.getType()!=HitResult.Type.BLOCK || hit.getDirection().getAxis().isVertical() || hit.getLocation().distanceToSqr(from)<10*10)continue;
            if(best==null || hit.distanceTo(boss)<best.distanceTo(boss))best=hit;
        }
        if(best==null)return false;
        var state=data().copy();state.putLong("Brace",best.getBlockPos().asLong());
        var point=best.getLocation();state.putDouble("WallX",point.x);state.putDouble("WallY",point.y);state.putDouble("WallZ",point.z);state.remove("Slip");state.putInt("WallLeg",intact(point));
        boss.spiderData(state);return true;
    }
    public Vec3 leapOffset(float partial){
        if(move()!=Move.POUNCE || !data().contains("Brace"))return Vec3.ZERO;
        var toward=new Vec3(data().getDouble("WallX"),0,data().getDouble("WallZ")).subtract(boss.position().multiply(1,0,1));
        double reach=Mth.clamp(toward.length()-14,1,28);
        var perch=toward.normalize().scale(reach).add(0,7,0);
        float time=age(partial);
        if(data().contains("Slip"))time=data().getInt("Slip");
        float climb=SpiderGesture.ease(time,4,15);
        float flight=Mth.clamp((time-18)/11F,0,1);
        double outward=flight*(2-flight);
        double height=perch.y*climb*(1-flight)+17*flight*(1-flight);
        var offset=perch.multiply(1,0,1).scale(climb*(1-outward)).add(0,height,0);
        if(data().contains("Slip"))offset=offset.scale(1-SpiderGesture.ease(age(partial)-time,0,8));
        return offset;
    }
    private void advance(ServerLevel level){
        int t=(int)age(0);Move m=move();
        if(m==Move.POUNCE){
            if(t<18 && !data().contains("Slip") && boss.level().getBlockState(BlockPos.of(data().getLong("Brace"))).getCollisionShape(level,BlockPos.of(data().getLong("Brace"))).isEmpty()){
                var st=data().copy();st.putInt("Slip",t);boss.spiderData(st);boss.playSound(SoundEvents.SPIDER_HURT,2,.58F);
            }
            if(data().contains("Slip")){
                if(t>=data().getInt("Slip")+8){debris(level,boss.position());rest(46);}
                return;
            }
            if(t>=19 && t<=29){
                // Keep the horizontal gain on landing. Entity.move clips the stride against the arena.
                var forward=aim().subtract(boss.position()).multiply(1,0,1);
                if(forward.lengthSqr()>8*8)takeStep(forward.normalize().scale(.32));
            }
            if(t==12)debris(level,Vec3.atCenterOf(BlockPos.of(data().getLong("Brace"))));
        }
        if(m==Move.RECOIL){if(t>=31)rest(14);return;}
        if(m==Move.CATALYZE){
            if(t>=28 && !(actor() instanceof BlacklightCommonInfected)){if(t>=38)rest(24);return;}
            if(!(actor() instanceof BlacklightCommonInfected host) || !host.isAlive()){rest(15);return;}
            var needle=boss.spider.rootPath(-1,1);
            if(t==28 && !needle.isEmpty() && clear(needle.get(0),host.getBoundingBox().getCenter())){
                var evolved=host.promote();
                if(evolved!=null){
                    if(evolved.beginGreeneEvolution()){
                        var st=data().copy();st.putUUID("ActionTarget",evolved.getUUID());st.putInt("ActionId",evolved.getId());boss.spiderData(st);
                        SpiderFoley.play(boss,evolved.getBoundingBox().getCenter(),SpiderFoley.Cue.NEEDLE,1.25F);
                        SpiderFoley.strands(boss,evolved.getBoundingBox().getCenter(),needle.get(0).subtract(evolved.position()),7);
                    }
                }
            }
            if(t>=38)rest(24);
            return;
        }
        if(!(actor() instanceof ServerPlayer p) || !p.isAlive() || p.isSpectator()){rest(18);return;}
        observe(p);
        // Commit early enough for the raised limb or opening fissures to advertise one fixed lane.
        if(t==6 && (m==Move.STAB || m==Move.SWEEP || m==Move.PINCER))
            commitAim(p,m==Move.SWEEP?16:12,4.5,true);
        if(t==6 && m==Move.SPIKES)commitAim(p,28,8,true);
        if(m==Move.HEAVY_STAMP){
            heavyStamp(level,t);return;
        }
        if(m==Move.FLESH_VOLLEY){
            if(t==3 || t==35)SpiderFoley.play(boss,boss.spider.mouth(1),SpiderFoley.Cue.INHALE,t==3?1.65F:1.20F);
            for(int wave=0;wave<VOLLEY_RELEASE.length;wave++){
                int release=VOLLEY_RELEASE[wave];
                // The fast final pair shares one direction so the first fan cannot steal the second fan's tell.
                if(t==release-6 && wave!=4){
                    var mouth=boss.spider.mouth(1);
                    double flight=20*mouth.distanceTo(p.getBoundingBox().getCenter())/GreeneMissileFlight.Ammo.SHOTGUN.initialSpeed;
                    commitAim(p,6+Math.min(26,flight),7,false);
                    SpiderFoley.strands(boss,mouth,aim().add(0,.95,0).subtract(mouth),5);
                }
                int bit=1<<wave;
                if(t!=release || (data().getInt("VolleyMask")&bit)!=0)continue;
                var state=data().copy();state.putInt("VolleyMask",state.getInt("VolleyMask")|bit);boss.spiderData(state);
                var from=boss.spider.mouth(1);
                var dir=aim().add(0,.95,0).subtract(from).normalize();
                int width=wave<3?3:4;
                for(int i=-width;i<=width;i++){
                    if(wave==3 && Math.abs(i)<=1)continue; // Diverging curtains leave a central lane.
                    if(wave==4 && (Math.abs(i)==2 || Math.abs(i)==3))continue;
                    var heading=dir.yRot((float)Math.toRadians(i*(wave<3?10:9)+(wave==1?5:0)));
                    if(heading.lengthSqr()>.001)level.addFreshEntity(new GreeneProjectile(level,boss,GreeneMissileFlight.Ammo.SHOTGUN,
                            GreeneMissileFlight.User.SHOTGUN,from,heading,p).asFlesh());
                }
                SpiderFoley.play(boss,from,SpiderFoley.Cue.SHED,wave>=3?1.65F:1.45F);SpiderFoley.strands(boss,from,dir,6);
            }
            if(t>=59)rest(8);
            return;
        }
        if(m==Move.ORAL_SCISSORS){
            if(SpiderWoundedActions.scissors(boss,t,p))hit(p,.83F,.28);
            if(t>=44)rest(15);
            return;
        }
        if(m==Move.POD_CAST){
            SpiderWoundedActions.cast(boss,t);
            if(data().contains("SeedHit") && t>=data().getInt("SeedHit")+12 || t>=74)rest(27);
            return;
        }
        if(m==Move.SPIKES){
            if(t==12)SpiderFoley.play(boss,boss.spider.palm(1),SpiderFoley.Cue.LOAD,1.2F);
            if(t>=18 && t<=34 && (t-18)%4==0)spike(level,(t-18)/4);
            if(t>=42)rest(8);return;
        }
        if(m==Move.SWALLOW || m==Move.SLAM){
            if(m==Move.SLAM && t>30){if(t>=41)rest(32);return;}
            grapple(p,m,t);return;
        }
        int leg=data().getInt("Foot");
        if(leg<0 || boss.spider.broken(leg)){rest(18);return;}
        int impact=m==Move.POUNCE?29:m==Move.SWEEP?22:18;
        if(m==Move.MARCH){march(level,p,t,leg);return;}
        if(m==Move.PINCER){pincer(p,t,leg);return;}
        if(m==Move.SWEEP && t>=14 && t<=31){
            var before=SpiderLegPose.worldTip(boss,leg,0);var after=SpiderLegPose.worldTip(boss,leg,1);
            if(DevastatorCollision.capsuleDistanceSquared(before,after,p.getBoundingBox())<2.35*2.35 && clear(after.add(0,.35,0),p.getEyePosition()))
                hit(p,.7F,.52);
            if(t==18 || t==26)SpiderFoley.ground(boss,after,1.15F,SpiderFoley.Cue.SCRAPE);
        }
        if(t==impact && m!=Move.SWEEP){
            Vec3 at=SpiderLegPose.worldTip(boss,leg,1);double radius=m==Move.POUNCE?5.1:2.25;
            boolean touched=p.position().distanceToSqr(at)<radius*radius && clear(at.add(0,2,0),p.getEyePosition());
            if(touched)hit(p,m==Move.POUNCE?1F:.73F,.4);
            if(m==Move.STAB)GreeneTendrilPod.plant(boss,at);
            SpiderFoley.ground(boss,at,m==Move.POUNCE?2.3F:1.75F,SpiderFoley.Cue.STRIKE);
        }
        if(m==Move.STAB && data().getBoolean("SecondStab")){
            if(t==18){
                var state=data().copy();var point=SpiderLegPose.worldTip(boss,leg,1);
                var saved=new CompoundTag();saved.putDouble("X",point.x);saved.putDouble("Y",point.y);saved.putDouble("Z",point.z);
                state.put("FirstStabPoint",saved);boss.spiderData(state);
            }
            if(t==32){
                commitAim(p,14,4.8,true);
                SpiderFoley.play(boss,SpiderLegPose.worldTip(boss,leg,1),SpiderFoley.Cue.STRAIN,1.22F);
            }
            if(t==46){
                var tip=SpiderLegPose.worldTip(boss,leg,1);
                ruptureHit(level,tip,3.35,1.34F,.58);
                SpiderFoley.ground(boss,tip,2.2F,SpiderFoley.Cue.STRIKE);
            }
            if(t>=62)rest(15);
            return;
        }
        if(t>=impact+23){
            int recovery=switch(m){
                case SWEEP -> data().getBoolean("Struck")?9:14;
                case STAB -> data().getBoolean("Struck")?11:16;
                case POUNCE -> 19;
                default -> 22;
            };
            rest(recovery);
        }
    }
    private void heavyStamp(ServerLevel level,int t){
        int foot=data().getInt("Foot");
        if(foot<0 || boss.spider.broken(foot)){rest(16);return;}
        if(t==18 && !data().contains("Lodged")){
            var tip=SpiderLegPose.worldTip(boss,foot,1);
            var hit=level.clip(new ClipContext(tip.add(0,1.1,0),tip.add(0,-1.1,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,boss));
            if(hit.getType()!=HitResult.Type.BLOCK || hit.getDirection()!=net.minecraft.core.Direction.UP){rest(15);return;}
            var floor=hit.getLocation();var s=data().copy();s.putLong("Lodged",hit.getBlockPos().asLong());
            s.putDouble("Ax",floor.x);s.putDouble("Ay",floor.y);s.putDouble("Az",floor.z);boss.spiderData(s);
            SpiderFoley.ground(boss,floor,2.15F,SpiderFoley.Cue.STRIKE);
            ruptureHit(level,floor,SpiderRupture.STAMP_RADIUS,1.42F,.62);
        }
        if(t>=18 && t<58 && data().contains("Lodged")){
            var block=BlockPos.of(data().getLong("Lodged"));
            if(level.getBlockState(block).getCollisionShape(level,block).isEmpty()){rest(15);return;}
            if(t==34 || t==51)SpiderFoley.play(boss,aim(),SpiderFoley.Cue.STRAIN,.82F);
        }
        if(t==58){SpiderFoley.ground(boss,aim(),1.4F,SpiderFoley.Cue.SCRAPE);}
        if(t>=70)rest(6);
    }
    public boolean lodged(int foot){
        return move()==Move.HEAVY_STAMP && data().getInt("Foot")==foot && data().contains("Lodged") && age(0)>=18 && age(0)<58;
    }
    /** Both footfalls stay in the lane advertised at takeoff, even if the player sidesteps. */
    public Vec3 marchGoal(boolean second){
        var origin=new Vec3(data().getDouble("RushX"),boss.getY(),data().getDouble("RushZ"));
        var dir=aim().subtract(origin).multiply(1,0,1);
        double distance=Math.min(dir.length(),second?25:17);
        var across=new Vec3(dir.z,0,-dir.x).normalize().scale((data().getInt("Foot")<4?1:-1)*(second?-1.1:1.1));
        var at=origin.add(dir.normalize().scale(distance)).add(across);
        var hit=boss.level().clip(new ClipContext(at.add(0,3,0),at.add(0,-4,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,boss));
        return hit.getType()==HitResult.Type.BLOCK?hit.getLocation().add(0,.08,0):at;
    }
    private void march(ServerLevel level,ServerPlayer p,int t,int first){
        int second=data().getInt("RushSecond");
        if(second<0 || boss.spider.broken(second)){rest(28);return;}
        if(data().contains("RushStopped")){
            if(t-data().getInt("RushStopped")>=14)rest(28);
            return;
        }
        if(t==4 || t==20)SpiderFoley.play(boss,SpiderLegPose.worldTip(boss,t==4?first:second,1),SpiderFoley.Cue.LOAD,1.45F);
        if(t>=10 && t<=39){
            var lane=aim().subtract(new Vec3(data().getDouble("RushX"),boss.getY(),data().getDouble("RushZ"))).multiply(1,0,1).normalize();
            double v=(t<=23?.56:.66)*(1-.065*Integer.bitCount(boss.spider.brokenMask()));
            if(!advanceShell(lane.scale(v))){
                var st=data().copy();st.putInt("RushStopped",t);boss.spiderData(st);
                SpiderFoley.ground(boss,boss.position().add(lane.scale(1.5)),1.45F,SpiderFoley.Cue.SCRAPE);
                return;
            }
        }
        if(t==21 || t==38){
            var at=SpiderLegPose.worldTip(boss,t==21?first:second,1);
            var floor=level.clip(new ClipContext(at.add(0,.55,0),at.add(0,-1.35,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,boss));
            if(floor.getType()==HitResult.Type.BLOCK){
                SpiderFoley.ground(boss,at,2.05F,SpiderFoley.Cue.STRIKE);
                if(p.getBoundingBox().inflate(2.1,.95,2.1).contains(at) && clear(at.add(0,.45,0),p.getEyePosition()))hit(p,.82F,.48);
            }
        }
        if(t>=47)rest(10);
    }
    public Vec3 pincerCarry(float partial){
        float t=age(partial);
        var from=new Vec3(data().getDouble("Px"),data().getDouble("Py"),data().getDouble("Pz"));
        var lip=boss.spider.mouth(partial).add(0,-.9,0);
        return from.lerp(lip,SpiderGesture.ease(t,18,28)).add(0,.48*Math.sin(Math.PI*SpiderGesture.ease(t,18,28)),0);
    }
    private void pincer(ServerPlayer p,int t,int leg){
        int other=(leg+4)%8;
        if(t==18){
            var box=p.getBoundingBox().inflate(1.25);
            boolean contact=false;
            for(int foot:new int[]{leg,other}){
                if(boss.spider.broken(foot))continue;
                var tip=SpiderLegPose.worldTip(boss,foot,1);
                SpiderFoley.ground(boss,tip,1.45F,SpiderFoley.Cue.PLANT);
                contact|=box.contains(tip) && clear(tip,p.getEyePosition());
            }
            var st=data().copy();
            if(contact && !boss.spider.broken(other) && !p.getPersistentData().hasUUID("GreeneGrasp")){
                st.putBoolean("Gripped",true);st.putDouble("Px",p.getX());st.putDouble("Py",p.getY());st.putDouble("Pz",p.getZ());
                p.getPersistentData().putUUID("GreeneGrasp",boss.getUUID());
                SpiderFoley.play(boss,p.position(),SpiderFoley.Cue.CATCH,1.1F);
            }else st.putBoolean("Missed",true);
            boss.spiderData(st);
        }
        if(t>18 && t<=28 && data().getBoolean("Gripped")){
            if(boss.spider.broken(other)){rest(26);return;}
            var next=pincerCarry(1);
            var box=p.getBoundingBox().move(next.subtract(p.position())).deflate(.001);
            if(!clear(p.getEyePosition(),next.add(0,p.getEyeHeight(),0)) || boss.level().getBlockCollisions(p,box).iterator().hasNext()){
                rest(26);return;
            }
            p.teleportTo(next.x,next.y,next.z);p.setDeltaMovement(Vec3.ZERO);p.fallDistance=0;
            if(t==28){
                hit(p,.73F,.18);releaseGrip();
                SpiderFoley.play(boss,boss.spider.mouth(1),SpiderFoley.Cue.CATCH,1.55F);
                SpiderFoley.strands(boss,boss.spider.mouth(1),p.position().subtract(boss.spider.mouth(1)),7);
            }
        }
        if(t>=44)rest(data().getBoolean("Missed")?18:15);
    }
    private void grapple(ServerPlayer p,Move m,int t){
        int grip=m==Move.SWALLOW?SpiderMouthStruggle.CATCH:18;
        if(t==grip){
            int foot=data().getInt("Foot"),opposite=(foot+4)%8;
            Vec3 capture=m==Move.SWALLOW?SpiderLegPose.worldTip(boss,foot,1):boss.spider.palm(1);
            var contact=p.getBoundingBox().inflate(m==Move.SWALLOW?1.5:1.3);
            boolean touched=contact.contains(capture) && clear(capture,p.getEyePosition());
            if(!touched && m==Move.SWALLOW && !boss.spider.broken(opposite)){
                var other=SpiderLegPose.worldTip(boss,opposite,1);
                touched=contact.contains(other) && clear(other,p.getEyePosition());
            }
            if(!touched || p.getPersistentData().hasUUID("GreeneGrasp") || p.isCreative()){
                var missed=data().copy();missed.putBoolean("Missed",true);boss.spiderData(missed);
                return;
            }
            SpiderFoley.play(boss,capture,SpiderFoley.Cue.CATCH,m==Move.SLAM?1.35F:.90F);
            var st=data().copy();st.putBoolean("Gripped",true);st.putDouble("Px",p.getX());st.putDouble("Py",p.getY());st.putDouble("Pz",p.getZ());boss.spiderData(st);
            p.getPersistentData().putUUID("GreeneGrasp",boss.getUUID());
            if(m==Move.SWALLOW){
                var face=boss.spider.mouth(1).subtract(p.getEyePosition());
                float yaw=(float)Math.toDegrees(Math.atan2(-face.x,face.z));
                float pitch=(float)-Math.toDegrees(Math.atan2(face.y,face.horizontalDistance()));
                p.connection.teleport(p.getX(),p.getY(),p.getZ(),yaw,Mth.clamp(pitch,-32,32));
                p.yBodyRot=yaw;p.setYHeadRot(yaw);
            }
            com.codex.blacklight.animation.PlayerActions.get().playVisual(p,m==Move.SWALLOW?"greene_mouth_brace":"greene_hand_catch",.06F);
        }
        if(!data().getBoolean("Gripped")){
            if(data().getBoolean("Missed") && t==grip+6){
                var at=m==Move.SLAM?boss.spider.palm(1):SpiderLegPose.worldTip(boss,data().getInt("Foot"),1);
                SpiderFoley.ground(boss,at,.95F,SpiderFoley.Cue.PLANT);
            }
            if(t>=grip+(m==Move.SLAM?22:20))rest(18);
            return;
        }
        if(p.isCreative() || !p.getPersistentData().hasUUID("GreeneGrasp")
                || !boss.getUUID().equals(p.getPersistentData().getUUID("GreeneGrasp"))){rest(20);return;}
        Vec3 next=m==Move.SWALLOW?SpiderMouthStruggle.feet(boss,t,1):boss.spider.palm(1).add(0,-1.1,0);
        if(m==Move.SLAM){
            // The palm compresses at contact; the receiver's feet must stay above the real floor.
            var ground=p.level().clip(new ClipContext(next.add(0,.85,0),next.add(0,-.45,0),
                    ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
            if(ground.getType()==HitResult.Type.BLOCK && ground.getDirection()==net.minecraft.core.Direction.UP)
                next=new Vec3(next.x,Math.max(next.y,ground.getLocation().y+.025),next.z);
        }
        var body=p.getBoundingBox().move(next.subtract(p.position())).deflate(.001);
        if(!clear(p.getEyePosition(),next.add(0,p.getEyeHeight(),0)) || boss.level().getBlockCollisions(p,body).iterator().hasNext()){rest(20);return;}
        p.teleportTo(next.x,next.y,next.z);p.setDeltaMovement(Vec3.ZERO);p.fallDistance=0;
        if(m==Move.SWALLOW){
            int pressed=data().getInt("PryAt");
            if(pressed>0){
                int effort=t-pressed;
                if(effort==12){
                    SpiderFoley.play(boss,next,SpiderFoley.Cue.SCRAPE,1.18F);
                    SpiderFoley.strands(boss,next.add(0,1.1,0),next.subtract(boss.spider.mouth(1)),5);
                }
                if(effort>=SpiderMouthStruggle.CLEAR){
                    var outward=next.subtract(boss.spider.mouth(1)).multiply(1,0,1).normalize();
                    releaseGrip();p.setDeltaMovement(outward.scale(.52).add(0,.16,0));p.hurtMarked=true;
                    SpiderFoley.play(boss,next,SpiderFoley.Cue.TEAR,1.32F);
                    set(Move.RECOIL,null,boss.position(),-1);
                }
            }else if(t==SpiderMouthStruggle.PROMPT){
                p.displayClientMessage(Component.translatable("boss.blacklight.greene_spider.escape",Component.keybind("key.jump")),true);
                SpiderFoley.play(boss,next,SpiderFoley.Cue.BRACE,1.3F);
            }else if(t==SpiderMouthStruggle.LAST_PRESS+1){
                com.codex.blacklight.animation.PlayerActions.get().playVisual(p,"greene_mouth_slip",.04F);
                p.displayClientMessage(Component.translatable("boss.blacklight.greene_spider.slip"),true);
                SpiderFoley.play(boss,next,SpiderFoley.Cue.SCRAPE,1.06F);
            }else if(t>=SpiderMouthStruggle.SWALLOWED){
                SpiderFoley.play(boss,boss.spider.mouth(1),SpiderFoley.Cue.GULP,1.7F);
                releaseGrip();p.hurt(boss.damageSources().genericKill(),Float.MAX_VALUE);rest(54);
            }
        }else if(t==30){
            var floor=boss.level().clip(new ClipContext(next,next.add(0,-8,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
            if(floor.getType()==HitResult.Type.BLOCK){Vec3 at=floor.getLocation().add(0,.025,0);p.teleportTo(at.x,at.y,at.z);debris(p.serverLevel(),at);}
            hit(p,.88F,.16);releaseGrip();
        }
    }
    private void hit(ServerPlayer p,float scale,double lift){
        if(data().getBoolean("Struck"))return;
        if(p.hurt(boss.damageSources().mobAttack(boss),MELEE_DAMAGE*scale)){
            var dir=p.position().subtract(boss.position()).multiply(1,0,1).normalize();
            p.push(dir.x*.55,lift,dir.z*.55);p.hurtMarked=true;
            var t=data().copy();
            if(move()==Move.PINCER || move()==Move.MARCH || move()==Move.SLAM || move()==Move.SPIKES || move()==Move.POUNCE)
                t.putLong("SafeFromMouth",now()+86);
            t.putBoolean("Struck",true);t.putInt("ContactAge",(int)age(0));boss.spiderData(t);
        }
    }
    private void ruptureHit(ServerLevel level,Vec3 point,double radius,float scale,double lift){
        var box = new AABB(point,point).inflate(radius+1,2.5,radius+1);
        for (var player : level.getEntitiesOfClass(ServerPlayer.class,box,p -> p.isAlive() && !p.isSpectator() && !p.isCreative())) {
            var seen = data().getCompound("RuptureHits");
            if (seen.getBoolean(player.getStringUUID())) continue;
            var delta = player.position().subtract(point);
            if (delta.horizontalDistanceSqr() > radius*radius || delta.y < -1.05 || delta.y > (delta.horizontalDistanceSqr()<4?2.1:.85)) continue;
            if (!clear(point.add(0,.55,0),player.getEyePosition())) continue;
            if (!player.hurt(boss.damageSources().mobAttack(boss),MELEE_DAMAGE*scale)) continue;
            var push=delta.multiply(1,0,1).normalize();
            player.push(push.x*.53,lift,push.z*.53);player.hurtMarked=true;
            var state=data().copy();
            var victims=state.getCompound("RuptureHits");victims.putBoolean(player.getStringUUID(),true);
            state.put("RuptureHits",victims);state.putBoolean("Struck",true);state.putInt("ContactAge",(int)age(0));
            state.putLong("SafeFromMouth",now()+86);boss.spiderData(state);
        }
    }
    private void spike(ServerLevel level,int row){
        int bit=1<<row;
        if ((data().getInt("SpikeRows")&bit)!=0) return;
        var state=data().copy();state.putInt("SpikeRows",state.getInt("SpikeRows")|bit);boss.spiderData(state);
        var sites=SpiderRupture.spikeSites(boss,row);
        for (int lane=-1;lane<=1;lane++) {
            var at=sites[row*3+lane+1];
            if (at==null) continue;
            var shard=ModEntities.GROUND_SPIKE.get().create(level);
            if (shard==null) continue;
            shard.setPos(at);shard.setPlayerShard(lane==0?3:2,6);
            if (!level.addFreshEntity(shard)) continue;
            if (lane==0) debris(level,at);
            ruptureHit(level,at,SpiderRupture.SPIKE_RADIUS,.76F,.86);
        }
    }
    private void debris(ServerLevel level,Vec3 at){
        SpiderFoley.ground(boss,at,1.65F,SpiderFoley.Cue.STRIKE);
    }
    public Vec3 flyingSeed(float partial){
        return move()==Move.POD_CAST?SpiderWoundedActions.seed(boss,age(partial)):null;
    }
    public float flex(String bone,float partial){
        float t=age(partial);Move action=move();
        float stroke=(float)Math.sin(Math.PI*Mth.clamp(t/(action==Move.SPIKES?43:39),0,1));
        if(action==Move.REST || action==Move.RECOIL)return 0;
        if(bone.startsWith("fang_")){
            int index=Integer.parseInt(bone.substring(5));
            float side=index<5?-1:1;
            float lag=(index%4)*.6F;
            if(action==Move.FLESH_VOLLEY){
                float open=SpiderGesture.phrase(t-lag,1,12,29,36)+SpiderGesture.phrase(t-lag,36,44,50,57);
                return side*open*(.75F-.24F*(float)Math.cos((t-15)*Math.PI/3.5));
            }
            if(action==Move.POD_CAST)return side*(.80F*SpiderGesture.phrase(t-lag,1,13,17,25)-.17F*SpiderGesture.phrase(t,17,20,22,29));
            if(action==Move.ORAL_SCISSORS)return side*.68F*SpiderGesture.phrase(t-lag,1,11,30,44);
            if(action==Move.PINCER){
                float open=SpiderGesture.phrase(t-lag,4,16,25,29);
                float snap=SpiderGesture.phrase(t-lag*.28F,26,29,30,39);
                return side*(.60F*open-.23F*snap);
            }
            if(action==Move.SWALLOW)return side*SpiderMouthStruggle.jaw(boss,index,t);
            return 0;
        }
        if(bone.equals("thorax"))return action==Move.SLAM?-1.84F*crouch(partial):(action==Move.SWALLOW?.30F:action==Move.SPIKES?.36F:.15F)*stroke;
        if(bone.startsWith("arm_")||bone.startsWith("elbow_"))return (action==Move.SLAM?-.88F:action==Move.SPIKES?.94F:0)*stroke;
        if(bone.equals("streamer"))return action==Move.CATALYZE?.73F*stroke:0;
        int leg=SpiderAnatomy.leg(bone);
        if(leg<0 || boss.spider.broken(leg))return 0;
        int chosen=data().getInt("Foot");boolean chosenPair=(leg%4)==(chosen%4);
        if(leg==chosen || action==Move.PINCER && chosenPair || action==Move.POUNCE)
            return (bone.startsWith("tibia")?-.92F:.63F)*stroke;
        return 0;
    }
}
