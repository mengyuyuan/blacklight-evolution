package com.codex.blacklight.combat;

import com.codex.blacklight.entity.*;
import com.codex.blacklight.registry.ModEntities;
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
    public enum Move { REST, STAB, SWEEP, PINCER, SPIKES, SLAM, POUNCE, CATALYZE, SWALLOW, RECOIL }
    private static final int[] STRIDE_HEADINGS={0,32,-32};
    private static final double[] STEP_HEIGHTS={0,.5,1.01};
    private final EncounterMob boss;
    public SpiderActions(EncounterMob boss){this.boss=boss;}
    private CompoundTag data(){return boss.spiderData();}
    private long now(){return boss.level().getGameTime();}
    public Move move(){int v=data().getInt("Action");return v>=0&&v<Move.values().length?Move.values()[v]:Move.REST;}
    public float age(float partial){return Math.max(0,now()-data().getLong("Start")+partial);}
    private void set(Move move,Entity target,Vec3 aim,int leg){
        var t=data().copy();
        float facing=boss.skinYaw(1);
        t.putFloat("ActionYaw",facing);
        t.putFloat("Facing",facing);t.putFloat("FaceFrom",facing);t.putLong("FaceTick",now());
        if(move!=Move.REST && move!=Move.RECOIL)t.putInt("LastAttack",move.ordinal());
        t.putInt("Action",move.ordinal());t.putLong("Start",now());t.putBoolean("Struck",false);
        t.remove("Gripped");t.remove("Missed");t.remove("ActionTarget");
        if(target!=null){t.putUUID("ActionTarget",target.getUUID());t.putInt("ActionId",target.getId());}
        t.putDouble("Ax",aim.x);t.putDouble("Ay",aim.y);t.putDouble("Az",aim.z);t.putInt("Foot",leg);
        boss.spiderData(t);
        if(move==Move.STAB || move==Move.SWEEP || move==Move.SLAM || move==Move.PINCER)
            boss.spider.footing.brace(move,leg,aim);
    }
    private Vec3 aim(){return new Vec3(data().getDouble("Ax"),data().getDouble("Ay"),data().getDouble("Az"));}
    private boolean clear(Vec3 from,Vec3 to){return boss.level().clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,boss)).getType()==HitResult.Type.MISS;}
    private Entity actor(){return data().hasUUID("ActionTarget") && boss.level() instanceof ServerLevel l?l.getEntity(data().getUUID("ActionTarget")):null;}
    private void rest(int delay){
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
        t.putDouble("HuskX",boss.getX());t.putDouble("HuskY",boss.getY());t.putDouble("HuskZ",boss.getZ());
        t.putFloat("HuskYaw",yaw);boss.spiderData(t);
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
            if(owner.spider.live() && action.move()==Move.SWALLOW && action.data().getBoolean("Gripped")
                && action.actor()==p && action.age(0)>=28 && action.age(0)<=50){
                action.releaseGrip();
                var side=new Vec3(1,0,0).yRot((float)Math.toRadians(-owner.getYRot()));
                p.setDeltaMovement(side.scale(.82).add(0,.22,0));p.hurtMarked=true;
                com.codex.blacklight.animation.PlayerActions.get().playVisual(p,"greene_mouth_kick",.04F);
                action.set(Move.RECOIL,null,owner.position(),-1);
                SpiderFoley.play(owner,owner.spider.mouth(1),SpiderFoley.Cue.TEAR,1.35F);
            }
        }
    }
    private int intact(Vec3 target){
        int best=-1;double score=Double.MAX_VALUE;
        for(int leg=0;leg<8;leg++)if(!boss.spider.broken(leg)){
            var tip=SpiderAnatomy.point(boss,1,SpiderAnatomy.TIPS.get(SpiderAnatomy.shin(leg)));
            double dist=tip.distanceToSqr(target);
            if(dist<score){score=dist;best=leg;}
        }
        return best;
    }
    public void moltBrood(ServerLevel level,int age){
        if(age%22!=0 || age>154)return;
        hatch(level,true);
    }
    public void spawnBrood(ServerLevel level){
        if(!level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING) || boss.tickCount%75!=1)return;
        hatch(level,false);
    }
    private void hatch(ServerLevel level,boolean molting){
        var sight=boss.getBoundingBox().inflate(54);
        if(level.getEntitiesOfClass(Mob.class,sight,SpiderPhase::edible).size()>=(molting?25:13))return;
        long seed=level.getSeed();var site=com.codex.blacklight.world.GreeneHiveCave.nearest(seed,boss.blockPosition());
        if(site.distSqr(boss.blockPosition())>50*50)return;
        int start=boss.getRandom().nextInt(6);
        for(int i=0;i<6;i++){
            int socket=(start+i)%6;
            var anchor=com.codex.blacklight.world.GreeneHiveCave.sac(site,socket);
            if(!level.hasChunkAt(anchor) || !level.getBlockState(anchor).is(com.codex.blacklight.registry.ModBlocks.TISSUE_ATTACHMENT.get()))continue;
            int breed=boss.getRandom().nextInt(molting?5:12);
            Mob child=switch(breed){
                case 0->ModEntities.BLACKLIGHT_STALKER.get().create(level);
                case 1->ModEntities.BLACKLIGHT_SPITTER.get().create(level);
                case 2->ModEntities.BLACKLIGHT_HUNTER.get().create(level);
                default->ModEntities.COMMON_INFECTED.get().create(level);
            };
            if(child==null)return;
            var normal=com.codex.blacklight.world.GreeneHiveCave.support(socket).getOpposite();
            child.setPos(Vec3.atBottomCenterOf(anchor.relative(normal,3)));
            if(!level.noCollision(child))continue;
            child.getPersistentData().putBoolean("GreeneBroodFall",true);
            child.getPersistentData().putUUID("EncounterOwner",boss.getUUID());
            if(level.addFreshEntity(child)){
                level.sendParticles(ParticleTypes.CRIMSON_SPORE,child.getX(),child.getY()+2,child.getZ(),14,.5,.3,.5,.02);
                SpiderFoley.play(boss,Vec3.atCenterOf(anchor),SpiderFoley.Cue.SHED,1.25F);
                SpiderFoley.strands(boss,Vec3.atCenterOf(anchor),child.position().subtract(Vec3.atCenterOf(anchor)),6);
            }
            return;
        }
    }
    public void tick(ServerLevel level){
        spawnBrood(level);
        if(move()!=Move.REST){advance(level);return;}
        if(boss.spider.feeding() || boss.spider.tilt(0)!=0)return;
        var p=level.getNearestPlayer(boss.getX(),boss.getY(),boss.getZ(),60,who->who instanceof ServerPlayer survivor && survivor.isAlive() && !survivor.isCreative() && !survivor.isSpectator());
        if(!(p instanceof ServerPlayer player) || player.isSpectator() || player.isCreative())return;
        Vec3 diff=player.position().subtract(boss.position());
        float facing=(float)Math.toDegrees(Math.atan2(-diff.x,diff.z));
        float speed=2.8F-.15F*Integer.bitCount(boss.spider.brokenMask());
        float previous=boss.skinYaw(1);
        float yaw=Mth.approachDegrees(previous,facing,speed);
        if(!data().contains("Facing") || Math.abs(Mth.wrapDegrees(yaw-previous))>.001F){
            var st=data().copy();st.putFloat("FaceFrom",previous);st.putFloat("Facing",yaw);
            st.putLong("FaceTick",now());boss.spiderData(st);
        }
        boss.setYRot(yaw);boss.yBodyRot=yaw;boss.yHeadRot=yaw;
        if(now()<data().getLong("NextMove")){
            // Reposition between commitments; refuse an unsupported ledge or a solid wall.
            double range=diff.horizontalDistance();
            if(range>17 && Math.abs(Mth.wrapDegrees(yaw-facing))<30){
                var stride=diff.multiply(1,0,1).normalize().scale(.14-.013*Integer.bitCount(boss.spider.brokenMask()));
                takeStep(stride);
            }
            return;
        }
        float angle=Math.abs(Mth.wrapDegrees(yaw-facing));
        // An exposed flank is in reach before the shell has turned to face the intruder.
        boolean flank=angle>27 && angle<105 && diff.horizontalDistanceSqr()<18*18
                && data().getInt("LastAttack")!=Move.SWEEP.ordinal();
        if(angle>27 && !flank)return;
        int count=data().getInt("Turns"),foot=intact(player.position());
        Vec3 point=player.position();
        var prey=level.getEntitiesOfClass(BlacklightCommonInfected.class,boss.getBoundingBox().inflate(31),m->m.isAlive() && !ConsumeControl.protectedEntity(m));
        if(!flank && count%4==2 && !prey.isEmpty()){
            Mob chosen=prey.get(boss.getRandom().nextInt(prey.size()));
            set(Move.CATALYZE,chosen,chosen.getBoundingBox().getCenter(),-1);
        }else{
            double d=diff.horizontalDistance();
            Move next;
            if(flank && foot>=0)next=Move.SWEEP;
            else if(d>21)next=Move.SPIKES;
            else if(count%6==4 && d<17 && foot>=0)next=Move.SWALLOW;
            else if(d<12 && count%3==1)next=Move.SLAM;
            else if(foot<0)next=Move.SPIKES;
            else if(boss.getHealth()<boss.getMaxHealth()*.6F && count%5==3 && wall())next=Move.POUNCE;
            else next=switch(count%4){case 0 -> Move.STAB;case 1 -> Move.PINCER;case 2 -> Move.SWEEP;default -> Move.SPIKES;};
            set(next,player,point,foot);
            if(foot>=0 && (next==Move.STAB||next==Move.PINCER||next==Move.SWEEP))SpiderFoley.play(boss,SpiderLegPose.worldTip(boss,foot,1),SpiderFoley.Cue.LOAD,1.15F);
        }
        var t=data().copy();t.putInt("Turns",count+1);boss.spiderData(t);
    }
    private void takeStep(Vec3 stride){
        var level=boss.level();
        for(int angle:STRIDE_HEADINGS){
            var step=stride.yRot((float)Math.toRadians(angle));
            var ahead=boss.position().add(step).add(step.normalize().scale(1.5));
            var floor=level.clip(new ClipContext(ahead.add(0,1.25,0),ahead.add(0,-1.2,0),
                    ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,boss));
            if(floor.getType()!=HitResult.Type.BLOCK || floor.getDirection()!=net.minecraft.core.Direction.UP)continue;
            var bounds=boss.getBoundingBox().deflate(.008);
            // Entity.move performs the final collision. Test the shell against actual blocks;
            // her own multipart contacts and the locked target do not serve as walls.
            for(double rise:STEP_HEIGHTS){
                var raised=bounds.move(0,rise,0);
                if(level.getBlockCollisions(boss,raised.expandTowards(step)).iterator().hasNext())continue;
                if(rise>0 && level.getBlockCollisions(boss,bounds.expandTowards(0,rise,0)).iterator().hasNext())continue;
                if(rise>0)boss.move(MoverType.SELF,new Vec3(0,rise,0));
                boss.move(MoverType.SELF,step);return;
            }
        }
    }
    private boolean wall(){
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
        if(m==Move.SPIKES){
            if(t==12)SpiderFoley.play(boss,boss.spider.palm(1),SpiderFoley.Cue.LOAD,1.2F);
            if(t>=18 && t<=34 && (t-18)%4==0)spike(level,p,(t-18)/4);
            if(t>=49)rest(20);return;
        }
        if(m==Move.SWALLOW || m==Move.SLAM){
            if(m==Move.SLAM && t>30){if(t>=41)rest(32);return;}
            grapple(p,m,t);return;
        }
        int leg=data().getInt("Foot");
        if(leg<0 || boss.spider.broken(leg)){rest(18);return;}
        int impact=m==Move.POUNCE?29:m==Move.SWEEP?22:18;
        if(m==Move.SWEEP && t>=14 && t<=31){
            var before=SpiderLegPose.worldTip(boss,leg,0);var after=SpiderLegPose.worldTip(boss,leg,1);
            if(DevastatorCollision.capsuleDistanceSquared(before,after,p.getBoundingBox())<1.85*1.85 && clear(after.add(0,.35,0),p.getEyePosition()))
                hit(p,.7F,.52);
            if(t==18 || t==26)SpiderFoley.ground(boss,after,1.15F,SpiderFoley.Cue.SCRAPE);
        }
        if(t==impact && m!=Move.SWEEP){
            Vec3 at=SpiderLegPose.worldTip(boss,leg,1);double radius=m==Move.POUNCE?3.8:m==Move.PINCER?2.4:1.85;
            boolean touched=p.position().distanceToSqr(at)<radius*radius && clear(at.add(0,2,0),p.getEyePosition());
            if(!touched && m==Move.PINCER && !boss.spider.broken((leg+4)%8)){
                var other=SpiderLegPose.worldTip(boss,(leg+4)%8,1);
                touched=p.position().distanceToSqr(other)<radius*radius && clear(other.add(0,2,0),p.getEyePosition());
            }
            if(touched)hit(p,m==Move.POUNCE?1F:.73F,.4);
            SpiderFoley.ground(boss,at,m==Move.POUNCE?2.3F:1.75F,SpiderFoley.Cue.STRIKE);
        }
        if(t>=impact+23){
            int recovery=switch(m){
                case SWEEP -> data().getBoolean("Struck")?16:29;
                case STAB -> data().getBoolean("Struck")?23:30;
                case POUNCE -> 33;
                default -> 22;
            };
            rest(recovery);
        }
    }
    private void grapple(ServerPlayer p,Move m,int t){
        int grip=m==Move.SWALLOW?24:18;
        if(t==grip){
            int foot=data().getInt("Foot"),opposite=(foot+4)%8;
            Vec3 capture=m==Move.SWALLOW?SpiderLegPose.worldTip(boss,foot,1):boss.spider.palm(1);
            var contact=p.getBoundingBox().inflate(m==Move.SWALLOW?1.5:1.3);
            boolean touched=contact.contains(capture) && clear(capture,p.getEyePosition());
            if(!touched && m==Move.SWALLOW && !boss.spider.broken(opposite)){
                var other=SpiderLegPose.worldTip(boss,opposite,1);
                touched=contact.contains(other) && clear(other,p.getEyePosition());
            }
            if(!touched){
                var missed=data().copy();missed.putBoolean("Missed",true);boss.spiderData(missed);
                return;
            }
            SpiderFoley.play(boss,capture,SpiderFoley.Cue.CATCH,m==Move.SLAM?1.35F:.90F);
            var st=data().copy();st.putBoolean("Gripped",true);st.putDouble("Px",p.getX());st.putDouble("Py",p.getY());st.putDouble("Pz",p.getZ());boss.spiderData(st);
            p.getPersistentData().putUUID("GreeneGrasp",boss.getUUID());
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
        Vec3 start=new Vec3(data().getDouble("Px"),data().getDouble("Py"),data().getDouble("Pz"));
        Vec3 end=m==Move.SWALLOW?boss.spider.mouth(1).add(0,-.7,0):start.add(0,3.6,0);
        Vec3 next=start.lerp(end,Mth.clamp((t-grip)/8D,0,1));
        if(m==Move.SLAM)next=boss.spider.palm(1).add(0,-1.1,0);
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
            if(t==28){p.displayClientMessage(Component.translatable("boss.blacklight.greene_spider.escape",Component.keybind("key.jump")),true);SpiderFoley.play(boss,boss.spider.mouth(1),SpiderFoley.Cue.BRACE,1.3F);}
            if(t>=51){SpiderFoley.play(boss,boss.spider.mouth(1),SpiderFoley.Cue.GULP,1.7F);releaseGrip();p.hurt(boss.damageSources().genericKill(),Float.MAX_VALUE);rest(54);}
        }else if(t==30){
            var floor=boss.level().clip(new ClipContext(next,next.add(0,-8,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
            if(floor.getType()==HitResult.Type.BLOCK){Vec3 at=floor.getLocation().add(0,.025,0);p.teleportTo(at.x,at.y,at.z);debris(p.serverLevel(),at);}
            hit(p,.88F,.16);releaseGrip();
        }
    }
    private void hit(ServerPlayer p,float scale,double lift){
        if(data().getBoolean("Struck"))return;
        if(p.hurt(boss.damageSources().mobAttack(boss),(float)boss.kind().damage*scale)){
            var dir=p.position().subtract(boss.position()).multiply(1,0,1).normalize();
            p.push(dir.x*.55,lift,dir.z*.55);p.hurtMarked=true;
            var t=data().copy();t.putBoolean("Struck",true);t.putInt("ContactAge",(int)age(0));boss.spiderData(t);
        }
    }
    private void spike(ServerLevel l,ServerPlayer p,int step){
        Vec3 at=boss.position().lerp(aim(),.3+.175*step);
        var ray=l.clip(new ClipContext(at.add(0,3,0),at.add(0,-3,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,boss));
        if(ray.getType()!=HitResult.Type.BLOCK){rest(20);return;}
        at=ray.getLocation();var spike=ModEntities.GROUND_SPIKE.get().create(l);
        if(spike!=null){spike.setPos(at);spike.setPlayerShard(2,6);l.addFreshEntity(spike);}
        debris(l,at);if(p.position().distanceToSqr(at)<2.2*2.2 && clear(at.add(0,.2,0),p.getEyePosition()))hit(p,.76F,.86);
    }
    private void debris(ServerLevel level,Vec3 at){
        SpiderFoley.ground(boss,at,1.65F,SpiderFoley.Cue.STRIKE);
    }
    public float flex(String bone,float partial){
        float t=age(partial);Move action=move();
        float stroke=(float)Math.sin(Math.PI*Mth.clamp(t/(action==Move.SPIKES?43:39),0,1));
        if(action==Move.REST || action==Move.RECOIL)return 0;
        if(bone.startsWith("fang_"))return action==Move.SWALLOW?.38F*stroke:0;
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
