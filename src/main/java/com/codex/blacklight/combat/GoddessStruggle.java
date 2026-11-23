package com.codex.blacklight.combat;

import com.codex.blacklight.entity.EncounterMob;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** A single encounter owns the capture, displayed prompts and terminal commit. */
public final class GoddessStruggle {
    public enum Step { WARN, LIFT, PUSH, JUMP, LEFT, RIGHT, REPRISE, ESCAPE, SLIP, OPEN }
    public static final int ATTACK=1, JUMP=2, LEFT=4, RIGHT=8;
    private final EncounterMob boss;
    private static final java.util.Map<ServerPlayer,GoddessStruggle> bound=new java.util.WeakHashMap<>();
    private java.lang.ref.WeakReference<ServerPlayer> captive=new java.lang.ref.WeakReference<>(null);
    public static boolean controlling(ServerPlayer p){
        var q=bound.get(p);
        if(q==null)return false;
        if(q.boss.isRemoved() || q.boss.level()!=p.level() || q.captive.get()!=p || !q.holds() || !p.getUUID().equals(q.attacks().victim())){bound.remove(p);return false;}
        return true;
    }
    private Step step=Step.WARN;
    private long stepAt, shown=-1, coolUntil, pressed=-1;
    private int spent, keys, knots, token;
    private Vec3 mark=Vec3.ZERO, source=Vec3.ZERO, caught=Vec3.ZERO, exit=Vec3.ZERO, releaseFrom=Vec3.ZERO;
    private Vec3 gripFrom=Vec3.ZERO, restingPalm=Vec3.ZERO;
    private float facing;
    private boolean hair, rescue, committed;
    private GoddessAttacks attacks(){return boss.goddess.attacks;}
    private boolean client(){return boss.level().isClientSide;}
    private CompoundTag remote(){return boss.goddessData().getCompound("Offense").getCompound("Struggle");}
    GoddessStruggle(EncounterMob boss){this.boss=boss;}
    private final GoddessSceneClock scene = new GoddessSceneClock();
    private long now(){return boss.level().getGameTime();}
    void receiveTimeline(CompoundTag data, long observed) {
        scene.synchronize(data.getInt("Token"),data.getInt("Step"),data.getLong("At"),observed,boss.tickCount);
    }
    public boolean active(){return attacks().move()==GoddessAttacks.Move.STRUGGLE;}
    public boolean hair(){return client()?remote().getBoolean("Hair"):hair;}
    public Step step(){return client()?Step.values()[Mth.clamp(remote().getInt("Step"),0,Step.values().length-1)]:step;}
    public float age(float partial){return client()?scene.age(boss.tickCount,partial):now()-stepAt+partial;}
    public boolean promptDisplayed(){return client()?remote().getBoolean("Displayed"):shown>=0;}
    public boolean prompt(){return switch(step()){case PUSH,JUMP,LEFT,RIGHT,REPRISE->true;default->false;};}
    public boolean holds(){return active() && step()!=Step.WARN && step()!=Step.OPEN;}
    public int remainingKnot(){return (knotMask()&1)==0?LEFT:RIGHT;}
    public int knotMask(){return client()?remote().getInt("Knots"):knots;}
    public float facing(){return client()?remote().getFloat("Facing"):facing;}
    public double effort(float partial){
        long start=client()?remote().getLong("Pressed"):pressed;
        long at=client()?remote().getLong("At"):stepAt;
        return start<0?0:Mth.clamp((age(partial)-(start-at))/12.,0,1);
    }
    private static double smooth(double x){x=Mth.clamp(x,0,1);return x*x*(3-2*x);}
    public Vec3 mark(){return client()?vec(remote(),"Mark"):mark;}
    private Vec3 source(){return client()?vec(remote(),"Source"):source;}
    private Vec3 caught(){return client()?vec(remote(),"Caught"):caught;}
    public float swing(float partial){return attacks().age(partial)*.12F;}
    public Vec3 right(){return Vec3.directionFromRotation(0,facing()+90);}
    public Vec3 front(){return Vec3.directionFromRotation(0,facing());}
    private void change(Step next){
        if(!hair && (next==Step.ESCAPE || next==Step.OPEN))restingPalm=palm(0);
        step=next;stepAt=now();shown=pressed=-1;token++;boss.goddess.publish();
        if(next==Step.LEFT || next==Step.RIGHT || next==Step.REPRISE){
            int side=next==Step.LEFT || next==Step.REPRISE && remainingKnot()==LEFT?0:1;
            SpiderFoley.play(boss,knot(side,0),SpiderFoley.Cue.SCRAPE,1.2F);
        }
    }
    private boolean clear(Vec3 a,Vec3 b){
        return boss.level().clip(new ClipContext(a,b,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,boss)).getType()==HitResult.Type.MISS;
    }
    private Vec3 floor(ServerPlayer p,Vec3 estimate){
        var h=boss.level().clip(new ClipContext(estimate.add(0,2,0),estimate.add(0,-16,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
        if(h.getType()!=HitResult.Type.BLOCK || h.getDirection()!=net.minecraft.core.Direction.UP)return null;
        var at=h.getLocation().add(0,.015,0);var box=p.getBoundingBox().move(at.subtract(p.position()));
        return boss.level().getWorldBorder().isWithinBounds(box) && boss.level().noCollision(p,box)
                && boss.level().getFluidState(net.minecraft.core.BlockPos.containing(at)).isEmpty()?at:null;
    }
    private Vec3 escapeSite(ServerPlayer p){
        for(double side:new double[]{1,-1}){
            var d=right().scale(4.3*side);
            if(!clear(p.getEyePosition(),p.getEyePosition().add(d)))continue;
            var site=floor(p,p.position().add(d));
            if(site!=null && clear(p.getEyePosition(),site.add(0,1,0)))return site;
        }
        return null;
    }
    boolean begin(ServerPlayer p){
        if(com.codex.blacklight.entity.GoddessEmplacement.releaseGrace(p,boss))return false;
        int arms=Integer.bitCount(boss.goddess.removed()&3),bit=1<<arms;
        if(now()<coolUntil || (spent&bit)!=0 || p.isPassenger() || !p.isAlive() || p.isCreative() || p.isSpectator()
                || com.codex.blacklight.power.BiomassSystem.active(p))return false;
        if(controlling(p) || ConsumeSystem.active(p) || com.codex.blacklight.city.HydraTransport.active(p))return false;
        hair=arms==2;
        if(!hair && !attacks().hand.readyForGrip())return false;
        source=hair?boss.hurtRegions(0).get(11).bounds().getCenter():attacks().hand.pose(0).palm();
        mark=p.position();facing=p.getYRot();
        if(!clear(source,p.getEyePosition()) || p.distanceToSqr(boss)>68*68
                || !hair && p.getViewVector(1).dot(source.subtract(p.getEyePosition()).normalize())<-.1)return false;
        var landing=escapeSite(p);
        if(landing==null || !clear(mark.add(0,1,0),mark.add(0,6,0)))return false;
        caught=mark;exit=landing;spent|=bit;coolUntil=now()+800;step=Step.WARN;stepAt=now();token++;knots=keys=0;
        pressed=shown=-1;committed=rescue=false;
        return true;
    }
    public Vec3 palm(float partial){
        double t=attacks().age(partial);
        var approach=mark().add(right().scale(-4)).add(0,1.2,0);
        if(step()==Step.WARN){
            if(t<30)return source().lerp(approach,smooth(t/30));
            return approach.add(right().scale(7.6*smooth((t-30)/10)));
        }
        Vec3 resting=client()?vec(remote(),"RestingPalm"):restingPalm;
        if(step()==Step.OPEN)return resting.lerp(source(),smooth(age(partial)/15));
        if(step()==Step.ESCAPE)return resting;
        Vec3 cradle=seat(partial).subtract(front().scale(1.45)).add(right().scale(-1.42*attacks().hand.gripSide())).add(0,.52,0);
        if(step()!=Step.LIFT)return cradle;
        Vec3 sweepEnd=client()?vec(remote(),"GripFrom"):gripFrom;
        return sweepEnd.lerp(cradle,smooth(age(partial)/15));
    }
    public float palmYaw(float partial){
        double turn=step()==Step.WARN?0:step()==Step.LIFT?smooth(age(partial)/15):1;
        return facing()+180+(float)(turn*180)*attacks().hand.gripSide();
    }
    public Vec3 seat(float partial){
        var base=caught();
        if(step()==Step.ESCAPE){
            var from=client()?vec(remote(),"Release"):releaseFrom;
            var to=client()?vec(remote(),"Exit"):exit;
            double t=smooth(age(partial)/17.);
            return from.lerp(to,t).add(0,.65*Math.sin(t*Math.PI),0);
        }
        double lift=step()==Step.LIFT?smooth(age(partial)/15):1;
        return base.add(0,lift*(hair()?3.4:2.7),0).add(right().scale(hair()?.56*Math.sin(attacks().age(partial)*.12)*lift:0));
    }
    public Vec3 supportTip(float partial){
        if(step()==Step.WARN || step()==Step.OPEN){
            double angle=4*Math.PI/3;
            return mark().add(Math.cos(angle)*1.85,.28,Math.sin(angle)*1.85);
        }
        return seat(partial).add(0,.38,0);
    }
    public Vec3 knot(int side,float partial){
        return seat(partial).add(front().scale(1.17)).add(right().scale(side==0?-.59:.59))
                .add(0,1.42+.18*Math.sin(attacks().age(partial)*.17+side*Math.PI),0);
    }
    public float closure(float partial){
        return switch(step()){
            case WARN -> (float)(.56*(1-smooth(attacks().age(partial)/21)));
            case LIFT -> (float)(.18+.65*smooth(age(partial)/15));
            case PUSH -> .84F-(float)effort(partial)*.36F;
            case JUMP -> .44F;
            case ESCAPE,OPEN -> (float)(.58*(1-smooth(age(partial)/10)));
            case SLIP -> (float)(.65+.77*smooth(age(partial)/19));
            default -> .85F;
        };
    }
    public int promptToken(){return client()?remote().getInt("Token"):token;}
    public static void input(ServerPlayer p,int entity,int sequence,int frame){
        if(!(p.level().getEntity(entity) instanceof EncounterMob b) || !b.goddess.active())return;
        var a=b.goddess.attacks;var q=a.struggle;
        if(!q.active() || q.captive.get()!=p || !p.getUUID().equals(a.victim()) || p.isSpectator() || !p.isAlive()
                || sequence!=a.sequence() || frame>>>4!=q.token || !q.prompt())return;
        int bits=frame&15;
        if(q.shown<0){q.shown=q.now();q.pressed=-1;b.goddess.publish();}
        int edge=bits&~q.keys;q.keys=bits;
        if(q.step==Step.PUSH){
            if((bits&ATTACK)==0)q.pressed=-1;
            else if(q.pressed<0)q.pressed=q.now();
            b.goddess.publish();
        }else if(q.step==Step.JUMP && (edge&JUMP)!=0)q.escape(p);
        else if(q.hair){
            int desired=q.step==Step.LEFT?LEFT:q.step==Step.RIGHT?RIGHT:q.remainingKnot();
            if((edge&(LEFT|RIGHT))!=desired)return;
            q.knots|=desired==LEFT?1:2;
            SpiderFoley.play(b,q.knot(desired==LEFT?0:1,0),SpiderFoley.Cue.TEAR,1.3F);
            if(q.knots==3)q.escape(p);else q.change(q.step==Step.LEFT?Step.RIGHT:Step.REPRISE);
        }
    }
    private void escape(ServerPlayer p){
        releaseFrom=p.position();
        var floor=floor(p,exit);if(floor!=null)exit=floor;else exit=p.position();
        change(Step.ESCAPE);
        SpiderFoley.play(boss,p.position(),SpiderFoley.Cue.SHED,1.5F);
    }
    public boolean rescue(ServerPlayer helper,int slot,Vec3 point,boolean heavy){
        if(!active() || committed || helper.getUUID().equals(attacks().victim()) && step!=Step.WARN)return false;
        boolean possible;
        if(hair){
            if(slot!=11)return false;
            var strand=boss.hurtRegions(0).get(slot).boxes();
            possible=strand.get(strand.size()-1).nearest(point).distanceToSqr(point)<.36;
        }
        else if(step==Step.WARN)possible=slot==GoddessHandPose.WRIST && heavy;
        else possible=slot==GoddessHandPose.THUMB && attacks().hand.pose(0).links().stream()
                .filter(l->l.slot()==GoddessHandPose.THUMB).findFirst().filter(l->l.surface().nearest(point).distanceToSqr(point)<1.5).isPresent();
        if(!possible || step==Step.OPEN || step==Step.ESCAPE)return false;
        if(!(boss.level() instanceof net.minecraft.server.level.ServerLevel l) || !(l.getEntity(attacks().victim()) instanceof ServerPlayer p))return false;
        rescue=true;
        if(step==Step.WARN)change(Step.OPEN);else escape(p);
        return true;
    }
    private boolean place(ServerPlayer p,Vec3 dest){
        var d=dest.subtract(p.position());
        if(d.lengthSqr()>64)return false;
        var motion=Entity.collideBoundingBox(p,d,p.getBoundingBox(),p.level(),List.of());
        if(d.distanceToSqr(motion)>.10 || !p.level().getWorldBorder().isWithinBounds(p.getBoundingBox().move(motion)))return false;
        var at=p.position().add(motion);
        p.setPos(at.x,at.y,at.z);
        if(hair)p.setYRot(facing);
        GoddessHeldMovement.commit(p,boss);
        p.fallDistance=0;p.setDeltaMovement(Vec3.ZERO);return true;
    }
    void cancel(ServerPlayer p){
        if(captive.get()!=null){
            GoddessHeldMovement.release(captive.get(),boss);
            bound.remove(captive.get(),this);
        }
        captive.clear();
        if(p!=null && holds()){p.fallDistance=0;p.setDeltaMovement(Vec3.ZERO);}
        token++;step=Step.OPEN;keys=0;shown=pressed=-1;
    }
    /** 0 continues, 1 returns to normal attacks, 2 opens a feeding break. */
    int tick(ServerPlayer p,int t){
        if(step==Step.OPEN)return age(0)>=15?(rescue?2:1):0;
        if(holds() && captive.get()!=p){cancel(null);return 1;}
        if(p.isCreative() || p.isPassenger() || com.codex.blacklight.power.BiomassSystem.active(p)){cancel(p);return 1;}
        if(step==Step.WARN){
            int lock=hair?22:20, strike=hair?32:30;
            if(t<=lock){
                if(!clear(source,p.getEyePosition()))return 1;
                mark=p.position();
                if(t%3==0 || t==lock)boss.goddess.publish();
            }
            if(t==lock)SpiderFoley.play(boss,mark,SpiderFoley.Cue.LOAD,1.25F);
            if(t<strike)return 0;
            boolean intersects=hair?mark.subtract(p.position()).horizontalDistance()<2.15 && Math.abs(mark.y-p.getY())<2.1:
                    attacks().hand.pose(0).links().stream().filter(l->l.slot()==GoddessHandPose.PALM).anyMatch(l->l.surface().touches(p.getBoundingBox()));
            if(intersects && clear(hair?mark.add(0,2,0):palm(0),p.getEyePosition())){
                if(controlling(p) || ConsumeSystem.active(p) || com.codex.blacklight.city.HydraTransport.active(p))return 1;
                WeaponCombatSystem.cancel(p);
                WeaponCombatSystem.cancelHeldInput(p);
                captive=new java.lang.ref.WeakReference<>(p);bound.put(p,this);
                if(!hair)gripFrom=palm(0);
                caught=p.position();facing=p.getYRot();change(Step.LIFT);
                SpiderFoley.play(boss,caught,SpiderFoley.Cue.CATCH,1.5F);
            }else if(t>=strike+(hair?0:10)){change(Step.OPEN);}
            return 0;
        }
        int age=(int)(now()-stepAt);
        if(!place(p,seat(0))){cancel(p);return 2;}
        if(step==Step.LIFT && age>=16){change(hair?Step.LEFT:Step.PUSH);}
        else if(prompt()){
            // A stalled or disconnected client cannot be killed before its first displayed prompt.
            if(shown<0 && age>=80){escape(p);return 0;}
            if(shown<0)return 0;
            int limit=step==Step.PUSH?32:step==Step.JUMP?20:24;
            if(step==Step.PUSH && pressed>=0 && now()-pressed>=12){
                change(Step.JUMP);SpiderFoley.play(boss,p.position(),SpiderFoley.Cue.STRAIN,1.4F);
            }else if(now()-shown>limit+4){
                if(step==Step.LEFT)change(Step.RIGHT);
                else if(step==Step.RIGHT && knots!=0)change(Step.REPRISE);
                else change(Step.SLIP);
            }
        }else if(step==Step.ESCAPE && age>=17){
            GoddessHeldMovement.release(p,boss);
            p.fallDistance=0;SpiderFoley.ground(boss,p.position(),1,SpiderFoley.Cue.BRACE);return 2;
        }else if(step==Step.SLIP && age>=22){
            GoddessHeldMovement.release(p,boss);
            committed=true;
            change(Step.OPEN);
            SpiderFoley.play(boss,p.position(),hair?SpiderFoley.Cue.RUPTURE:SpiderFoley.Cue.GULP,1.8F);
            p.hurt(boss.damageSources().magic(),(p.getMaxHealth()+p.getAbsorptionAmount()+1)*1000);
        }
        return 0;
    }
    private static void put(CompoundTag t,String k,Vec3 v){t.putDouble(k+"X",v.x);t.putDouble(k+"Y",v.y);t.putDouble(k+"Z",v.z);}
    private static Vec3 vec(CompoundTag t,String k){return new Vec3(t.getDouble(k+"X"),t.getDouble(k+"Y"),t.getDouble(k+"Z"));}
    CompoundTag save(){
        var t=new CompoundTag();t.putInt("Spent",spent);t.putLong("Cooldown",coolUntil);t.putInt("Step",step.ordinal());
        t.putLong("At",stepAt);t.putLong("Pressed",pressed);t.putInt("Knots",knots);t.putInt("Token",token);
        t.putBoolean("Displayed",shown>=0);t.putBoolean("Hair",hair);t.putBoolean("Committed",committed);t.putFloat("Facing",facing);
        put(t,"GripFrom",gripFrom);put(t,"RestingPalm",restingPalm);put(t,"Source",source);put(t,"Mark",mark);put(t,"Caught",caught);put(t,"Exit",exit);put(t,"Release",releaseFrom);return t;
    }
    void load(CompoundTag t){
        var previous=captive.get();
        if(previous!=null)bound.remove(previous,this);
        captive.clear();
        keys=knots=0;shown=pressed=-1;rescue=committed=false;step=Step.OPEN;
        spent=t.getInt("Spent")&7;coolUntil=t.getLong("Cooldown");token=t.getInt("Token")+1;
    }
}
