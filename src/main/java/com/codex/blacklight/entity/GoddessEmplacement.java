package com.codex.blacklight.entity;

import com.codex.blacklight.combat.GoddessImpact;
import com.codex.blacklight.combat.AnimatedHurtBody;
import com.codex.blacklight.combat.HydraContact;
import com.codex.blacklight.combat.HydraHurtContact;
import com.codex.blacklight.combat.GoddessAttacks;
import com.codex.blacklight.combat.GoddessEncounter;
import com.codex.blacklight.combat.SpiderFoley;
import com.codex.blacklight.registry.ModEntities;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;

/** An exposed spine or wrist is a real attackable mob. Its root stays on the original collision face. */
public final class GoddessEmplacement extends Mob implements AnimatedHurtBody {
    private static final EntityDataAccessor<CompoundTag> MOUNT=SynchedEntityData.defineId(GoddessEmplacement.class,EntityDataSerializers.COMPOUND_TAG);
    private UUID owner,victim;
    private BlockPos support;
    private int sequence;
    private boolean caught;
    public GoddessEmplacement(EntityType<? extends GoddessEmplacement> type,Level level){
        super(type,level);
        setNoAi(true);setNoGravity(true);setPersistenceRequired();xpReward=0;
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder b){super.defineSynchedData(b);b.define(MOUNT,new CompoundTag());}
    public boolean cannon(){return getType()==ModEntities.GREENE_SPINE_CANNON.get();}
    public float age(float partial){return level().getGameTime()-entityData.get(MOUNT).getLong("Start")+partial;}
    private static Vec3 read(CompoundTag t,String key){return new Vec3(t.getDouble(key+"X"),t.getDouble(key+"Y"),t.getDouble(key+"Z"));}
    private static void write(CompoundTag t,String key,Vec3 v){t.putDouble(key+"X",v.x);t.putDouble(key+"Y",v.y);t.putDouble(key+"Z",v.z);}
    public Vec3 root(){return read(entityData.get(MOUNT),"Root");}
    public Vec3 heading(){return read(entityData.get(MOUNT),"Aim");}
    public Vec3 normal(){return read(entityData.get(MOUNT),"Face");}
    public boolean belongsTo(EncounterMob b){return b.getUUID().equals(owner);}
    @Override public boolean isPushable(){return false;}
    @Override protected void doPush(Entity other){}
    @Override public void knockback(double strength,double x,double z){}
    @Override public void addAdditionalSaveData(CompoundTag t){
        super.addAdditionalSaveData(t);t.put("GoddessMount",entityData.get(MOUNT).copy());
        if(owner!=null)t.putUUID("Mother",owner);
        if(victim!=null)t.putUUID("Victim",victim);
        if(support!=null)t.putLong("Support",support.asLong());
        t.putInt("Order",sequence);
    }
    @Override public void readAdditionalSaveData(CompoundTag t){
        super.readAdditionalSaveData(t);entityData.set(MOUNT,t.getCompound("GoddessMount").copy());
        owner=t.hasUUID("Mother")?t.getUUID("Mother"):null;
        victim=t.hasUUID("Victim")?t.getUUID("Victim"):null;
        support=t.contains("Support")?BlockPos.of(t.getLong("Support")):null;
        sequence=t.getInt("Order");caught=false;
    }
    public boolean fromOrder(EncounterMob b,int order){return belongsTo(b) && sequence==order;}
    private static BlockHitResult trace(Level l,Entity e,Vec3 from,Vec3 to){
        return l.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,e));
    }
    private static BlockHitResult mount(ServerLevel l,EncounterMob b,ServerPlayer p,boolean wall){
        Vec3 at=p.position();
        double main=Math.atan2(at.z-b.getZ(),at.x-b.getX())+Math.PI*.5;
        for(int i=0;i<(wall?24:8);i++){
            double a=main+(i%8)*Math.PI*.25;
            Vec3 offset=new Vec3(Math.cos(a),0,Math.sin(a));
            Vec3 start=wall?at.add(0,1.35+(i/8)*2.3,0):at.add(offset.scale(4)).add(0,2,0);
            var hit=trace(l,b,start,wall?start.add(offset.scale(52)):start.add(0,-6,0));
            if(hit.getType()!=HitResult.Type.BLOCK || !l.hasChunkAt(hit.getBlockPos()))continue;
            if(wall && (hit.getDirection().getAxis().isVertical() || hit.getLocation().distanceToSqr(at)<16))continue;
            if(!wall && (hit.getDirection()!=Direction.UP || Math.abs(at.y-hit.getLocation().y)>1.25))continue;
            if(!l.getFluidState(hit.getBlockPos().relative(hit.getDirection())).isEmpty())continue;
            return hit;
        }
        return null;
    }
    public static void deploy(EncounterMob boss,ServerPlayer target,boolean wall){
        if(!(boss.level() instanceof ServerLevel l))return;
        var existing=l.getEntitiesOfClass(GoddessEmplacement.class,boss.getBoundingBox().inflate(120),
                e->e.belongsTo(boss) && e.cannon()==wall && e.isAlive());
        if(!existing.isEmpty())return;
        var hit=mount(l,boss,target,wall);if(hit==null)return;
        var e=(wall?ModEntities.GREENE_SPINE_CANNON:ModEntities.GREENE_GROUND_WRIST).get().create(l);
        if(e==null)return;
        var face=Vec3.atLowerCornerOf(hit.getDirection().getNormal());
        var root=hit.getLocation().add(face.scale(.04));
        var aim=target.position().add(0,.78,0).subtract(root);
        if(!wall)aim=aim.multiply(1,0,1);
        if(aim.lengthSqr()<.4)return;
        var data=new CompoundTag();write(data,"Root",root);write(data,"Face",face);write(data,"Aim",aim.normalize());
        data.putLong("Start",l.getGameTime());e.entityData.set(MOUNT,data);
        e.owner=boss.getUUID();e.victim=target.getUUID();e.support=hit.getBlockPos().immutable();e.sequence=boss.goddess.attacks.sequence();
        e.positionOrgan(e.tip(0));if(!l.noCollision(e))return;
        if(l.addFreshEntity(e))SpiderFoley.play(e,root,wall?SpiderFoley.Cue.LOAD:SpiderFoley.Cue.SCRAPE,1.15F);
    }
    private static double ease(double age,double from,double to){
        double t=Math.max(0,Math.min(1,(age-from)/(to-from)));return t*t*(3-2*t);
    }
    public Vec3 tip(float t){
        if(cannon())return root().add(normal().scale(.72)).add(0,.12+1.4*ease(t,1,17),0)
                .add(heading().scale(.16+1.05*ease(t,7,20)));
        double reach=ease(t,13,23)*(1-ease(t,25,36));
        return root().add(heading().scale(.16+4.25*reach)).add(0,.45+.11*Math.sin(reach*Math.PI),0);
    }
    public Vec3 bend(float age){
        if(cannon())return root().add(normal().scale(1.08)).add(0,-.05,0);
        var dir=heading();
        double flex=ease(age,7,18)*(1-ease(age,29,39));
        return root().lerp(tip(age),.52).add(new Vec3(dir.z,0,-dir.x).normalize().scale(.47)).add(0,.16+.85*flex,0);
    }
    private static HydraContact.Box segment(Vec3 a,Vec3 b,double radius){
        Vec3 axis=b.subtract(a);double length=axis.length();
        var forward=length<.001?new Vec3(0,0,1):axis.scale(1/length);
        var guide=Math.abs(forward.y)>.95?new Vec3(1,0,0):new Vec3(0,1,0);
        var right=guide.cross(forward).normalize();
        return HydraContact.oriented(a.lerp(b,.5),new Vec3(radius,radius,Math.max(.06,length*.5)),right,forward.cross(right),forward);
    }
    @Override public java.util.List<HydraContact.Region> hurtRegions(float partial){
        float age=age(partial);var tip=tip(age);var mid=bend(age);
        var proximal=segment(root().lerp(mid,.19),mid,cannon()?.26:.19);
        var distal=segment(mid,tip,cannon()?.28:.20);
        var exposed=segment(tip.subtract(heading().scale(.21)),tip.add(heading().scale(.39)),cannon()?.30:.27);
        return java.util.List.of(new HydraContact.Region(cannon()?"spine":"wrist",java.util.List.of(proximal,distal,exposed),
                proximal.bounds.minmax(distal.bounds).minmax(exposed.bounds)));
    }
    private HydraHurtContact.Contact incoming;
    @Override public HydraHurtContact.Contact incomingContact(){return incoming;}
    @Override public Vec3 incomingPoint(){return incoming==null?getBoundingBox().getCenter():incoming.point();}
    @Override public boolean hurtAt(DamageSource source,float amount,HydraHurtContact.Contact contact){
        try{incoming=contact;return contact!=null && hurt(source,amount);}finally{incoming=null;}
    }
    @Override public void confirmedIncoming(DamageSource source,float amount){}
    private void positionOrgan(Vec3 center){setPos(center.add(0,-getBbHeight()*.5,0));}
    public static boolean releaseGrace(ServerPlayer p,EncounterMob boss){
        var state=p.getPersistentData();
        return state.hasUUID("GreeneWristOwner") && boss.getUUID().equals(state.getUUID("GreeneWristOwner"))
                && state.getLong("GreeneWristFreedom")>p.level().getGameTime();
    }
    private void grace(ServerPlayer p){
        var state=p.getPersistentData();state.putUUID("GreeneWristOwner",owner);state.putLong("GreeneWristFreedom",level().getGameTime()+20);
    }
    @Override public boolean hurt(DamageSource source,float damage){
        if(level().isClientSide || isRemoved() || damage<=0 || !(source.getEntity() instanceof Player))return false;
        SpiderFoley.play(this,position(),SpiderFoley.Cue.TEAR,1.15F);discard();return true;
    }
    private void fire(ServerLevel l,EncounterMob b){
        var bolt=new EvolvedQuill(l,b);
        bolt.setPos(tip(age(0)).add(heading().scale(.64)));bolt.damage(12);
        bolt.getPersistentData().putInt("GoddessSequence",b.goddess.attacks.sequence());
        bolt.getPersistentData().putUUID("GoddessSpineCaster",getUUID());
        var dir=heading();bolt.shoot(dir.x,dir.y,dir.z,1.85F,0);l.addFreshEntity(bolt);
        SpiderFoley.play(this,bolt.position(),SpiderFoley.Cue.NEEDLE,1.10F);
    }
    private void wrist(ServerPlayer p,EncounterMob b,int t,Vec3 last,Vec3 point){
        if(t==6 || t==12 || t==17){
            var ripple=root().add(heading().scale((19-t)*.20));
            SpiderFoley.ground(b,ripple,.65F,t==17?SpiderFoley.Cue.STRIKE:SpiderFoley.Cue.SCRAPE);
        }
        var contact=p.getBoundingBox().inflate(.35,.09,.35);
        if(t>=14 && t<=24 && !caught && (contact.contains(last) || contact.clip(last,point).isPresent())
                && trace(level(),this,point,p.position().add(0,.45,0)).getType()==HitResult.Type.MISS){
            if(p.hurt(b.damageSources().mobAttack(b),GoddessImpact.GROUND_WRIST.incoming(p))){
                caught=true;grace(p);SpiderFoley.play(this,point,SpiderFoley.Cue.CATCH,1.15F);
            }
        }
        if(caught && t<=35){
            grace(p);
            if(t<25)return;
            var travel=point.subtract(last);
            if(travel.lengthSqr()>.24)travel=travel.normalize().scale(.49);
            p.move(MoverType.SELF,travel);p.connection.teleport(p.getX(),p.getY(),p.getZ(),p.getYRot(),p.getXRot());
            p.fallDistance=0;
        }
        if(t==36 && caught){grace(p);caught=false;SpiderFoley.play(this,point,SpiderFoley.Cue.SHED,.9F);}
    }
    @Override public void tick(){
        super.tick();
        if(!(level() instanceof ServerLevel l))return;
        if(owner==null || !(l.getEntity(owner) instanceof EncounterMob b) || !b.isAlive() ||
                !b.goddess.active() || b.goddess.action()!=GoddessEncounter.Action.FLYING ||
                victim==null || !(l.getEntity(victim) instanceof ServerPlayer p) || !p.isAlive() || p.isSpectator() || p.isCreative() ||
                b.goddess.attacks.safe(p) || support==null){discard();return;}
        var main=b.goddess.attacks.move();
        if(p.getUUID().equals(b.goddess.attacks.victim()) && (main==GoddessAttacks.Move.STRUGGLE ||
                main==GoddessAttacks.Move.RUSH || main==GoddessAttacks.Move.VACUOLE)){discard();return;}
        var root=root();var n=normal();
        var backing=trace(l,this,root.add(n.scale(.10)),root.subtract(n.scale(.15)));
        if(backing.getType()!=HitResult.Type.BLOCK || !support.equals(backing.getBlockPos())){discard();return;}
        int t=(int)age(0);if(t<0 || t>(cannon()?54:40)){discard();return;}
        var point=tip(t);
        if(trace(l,this,root.add(n.scale(.1)),point).getType()!=HitResult.Type.MISS){discard();return;}
        positionOrgan(point);
        if(cannon()){
            if(t==8 || t==15)SpiderFoley.play(this,point,SpiderFoley.Cue.BRACE,.85F);
            if(t==19 || t==29 || t==39){
                var aim=p.getBoundingBox().getCenter().subtract(point);
                if(aim.lengthSqr()>1){
                    var data=entityData.get(MOUNT).copy();write(data,"Aim",aim.normalize());entityData.set(MOUNT,data);
                }
            }
            if(t==23 || t==33 || t==43)fire(l,b);
        }else wrist(p,b,t,tip(t-1),point);
    }
}
