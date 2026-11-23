package com.codex.blacklight.combat;

import com.codex.blacklight.entity.EncounterMob;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** The real arm carries one target. The release is swept against the world's current collision shapes. */
public final class GoddessRush {
    private final EncounterMob boss;
    private Vec3 origin=Vec3.ZERO, mark=Vec3.ZERO, heading=Vec3.ZERO, previousPalm, flight=Vec3.ZERO, feet, contactOffset=Vec3.ZERO;
    private float yaw,entryYaw;
    private int arm,grip=-1;
    private boolean released,landed;
    private long next;

    GoddessRush(EncounterMob boss){this.boss=boss;}
    private CompoundTag remote(){return boss.goddessData().getCompound("Offense").getCompound("Rush");}
    public int arm(){return boss.level().isClientSide?remote().getInt("Arm"):arm;}
    public int grip(){return boss.level().isClientSide?remote().getInt("Grip"):grip;}
    public boolean released(){return boss.level().isClientSide?remote().getBoolean("Released"):released;}
    public boolean landed(){return boss.level().isClientSide?remote().getBoolean("Landed"):landed;}
    public boolean holding(){return grip()>=0&&!released();}
    public float yaw(){return boss.level().isClientSide?remote().getFloat("Yaw"):yaw;}
    public float bodyYaw(float t){
        float start=boss.level().isClientSide?remote().getFloat("EntryYaw"):entryYaw;
        int side=arm()==1?1:-1;
        float sweep=-side*20*ease(t,12,26)*(1-ease(t,26,41));
        int g=grip();
        float twist=g<0?side*18*ease(t,34,43)*(1-ease(t,48,66)):
                side*(13*ease(t,g+15,g+32)-53*ease(t,g+35,g+47))*(1-ease(t,g+52,g+70));
        return Mth.rotLerp(ease(t,0,22),start,yaw())+sweep+twist;
    }
    static float ease(float t,float a,float b){float s=Mth.clamp((t-a)/(b-a),0,1);return s*s*(3-2*s);}
    public Vec3 aim(){return boss.level().isClientSide?vec(remote(),"Mark"):mark;}
    Vec3 lane(float t){
        int caught=grip();
        float held=caught<0?0:Math.max(0,t-caught);
        if(held>0)t=caught+(float)(2*(1-Math.exp(-held/2)));
        var direction=boss.level().isClientSide?vec(remote(),"Heading"):heading;
        var across=new Vec3(direction.z,0,-direction.x).scale(arm()==1?1:-1);
        double sweep=-6+11*ease(t,26,42);
        return aim().add(direction.scale(sweep)).add(across.scale(-2.9+5*ease(t,26,41)))
                .add(0,.32*(1-ease(t,24,32))+1.45*ease(held,0,6),0);
    }
    public Vec3 heldPosition(HydraContact.Box palm,float height,float held){
        var direction=boss.level().isClientSide?vec(remote(),"Heading"):heading;
        var offset=boss.level().isClientSide?vec(remote(),"ContactOffset"):contactOffset;
        var outward=new Vec3(direction.z,0,-direction.x).scale(arm()==1?-1:1).add(direction.scale(.45)).normalize();
        var skin=palm.clip(palm.center.add(outward.scale(6)),palm.center).orElse(palm.center);
        var tucked=skin.add(outward.scale(.18)).subtract(0,height*.44,0);
        return palm.center.add(offset).lerp(tucked,ease(held,0,8));
    }
    private Vec3 allowed(Entity e,Vec3 delta,AABB box){
        return Entity.collideBoundingBox(e,delta,box,boss.level(),List.of());
    }
    private boolean route(Entity e,Vec3 delta,AABB box){
        return allowed(e,delta,box).distanceToSqr(delta)<.0001 && boss.level().getWorldBorder().isWithinBounds(box.move(delta));
    }
    boolean close(ServerPlayer p){
        if(boss.position().subtract(p.position()).horizontalDistance()<11)return true;
        if(p.getY()<boss.getY()+10)return false;
        int slot=(boss.goddess.removed()&1)==0?5:9;
        return boss.hurtRegions(0).get(slot).boxes().stream().anyMatch(b->b.nearest(p.getEyePosition()).distanceToSqr(p.getEyePosition())<49);
    }
    boolean begin(ServerPlayer p){
        if(boss.level().getGameTime()<next || (boss.goddess.removed()&3)==3 || p.isPassenger() || p.isCreative()
                || p.isSpectator() || p.distanceToSqr(boss)>40*40 || !p.isAlive())return false;
        Vec3 delta=p.position().subtract(boss.position()).multiply(1,0,1);
        if(delta.lengthSqr()<4)return false;
        grip=-1;origin=boss.position();mark=p.getBoundingBox().getCenter();heading=delta.normalize();
        entryYaw=boss.yBodyRot;
        int preferred=p.position().subtract(boss.position()).dot(Vec3.directionFromRotation(0,entryYaw+90))>=0?1:2;
        for(float offset:new float[]{0,.36F,-.36F,.70F,-.70F}){
            heading=delta.normalize().yRot(offset);
            yaw=(float)Math.toDegrees(Math.atan2(-heading.x,heading.z));
            if(!selectArm(p,preferred))continue;
            grip=-1;released=landed=false;previousPalm=null;feet=null;flight=Vec3.ZERO;
            next=boss.level().getGameTime()+260;
            boss.getNavigation().stop();boss.goddess.attacks.hand.cancel();
            return true;
        }
        return false;
    }
    private boolean selectArm(ServerPlayer p,int preferred){
        Vec3 right=new Vec3(heading.z,0,-heading.x);
        for(int candidate:new int[]{preferred,3-preferred}){
            if((boss.goddess.removed()&candidate)!=0)continue;
            arm=candidate;
            Vec3 from=lane(26),to=lane(42);
            var exit=right.scale(candidate==1?-4:4).add(0,3,0);
            if(!route(p,to.subtract(from),AABB.ofSize(from,.9,1,.9)) || !route(p,exit,p.getBoundingBox()))continue;
            var palm=boss.hurtRegions(0).get(arm==1?5:9);
            if(!palm.boxes().isEmpty() && HydraHurtContact.clear(boss,palm.bounds().getCenter(),from))return true;
        }
        return false;
    }
    private void place(ServerPlayer p,Vec3 dest){
        p.connection.teleport(dest.x,dest.y,dest.z,p.getYRot(),p.getXRot());
        p.setDeltaMovement(Vec3.ZERO);p.fallDistance=0;feet=dest;
    }
    private void endHold(ServerPlayer p){
        if(p!=null){p.fallDistance=0;}
        released=landed=true;
        boss.goddess.publish();
    }
    boolean tick(ServerLevel level,ServerPlayer p,int t){
        if((boss.goddess.removed()&arm)!=0 || p.isCreative() || p.isPassenger()){endHold(p);return true;}
        if(t==9)SpiderFoley.play(boss,boss.hurtRegions(0).get(0).bounds().getCenter(),SpiderFoley.Cue.INHALE,1.25F);
        if(t>=5 && t<=65){
            // Give the body clearance over broken flooring, then settle onto the live terrain.
            double altitude=2.4*ease(t,5,25)*(1-ease(t,44,65));
            double reach=Math.min(13,origin.subtract(mark).horizontalDistance()*.64);
            Vec3 step=heading.scale(reach*(ease(t,26,44)-ease(t-1,26,44)));
            step=step.add(0,origin.y+altitude-boss.getY(),0);
            boss.move(MoverType.SELF,step);
        }
        float facing=bodyYaw(t);
        boss.setYRot(facing);boss.yBodyRot=facing;
        var region=boss.hurtRegions(0).get(arm==1?5:9);
        if(region.boxes().isEmpty()){endHold(p);return true;}
        var palm=region.boxes().get(0);
        Vec3 surface=palm.center;
        if(t==26)SpiderFoley.play(boss,surface,SpiderFoley.Cue.STRIKE,1.4F);
        if(grip<0 && t>=26 && t<=42 && previousPalm!=null){
            Vec3 step=surface.subtract(previousPalm);
            var bounds=p.getBoundingBox();var center=bounds.getCenter();
            var half=new Vec3(bounds.getXsize()/2,bounds.getYsize()/2,bounds.getZsize()/2);
            if(!route(p,step,AABB.ofSize(previousPalm,.5,.5,.5))){endHold(p);return true;}
            if((palm.touches(bounds)||palm.sweepBox(center.add(step),center,half)!=null)
                    && HydraHurtContact.clear(boss,palm.nearest(center),center)){
                grip=t;feet=p.position();contactOffset=feet.subtract(surface);boss.goddess.publish();
                SpiderFoley.play(boss,surface,SpiderFoley.Cue.CATCH,1.45F);
            }
        }
        previousPalm=surface;
        if(grip<0)return t>=69;
        if(!released){
            if(feet!=null && p.position().distanceToSqr(feet)>36){endHold(p);return true;}
            float held=t-grip;
            // Stand just inside the curling fingers, in front of the authored palm.
            Vec3 dest=heldPosition(palm,p.getBbHeight(),held);
            Vec3 delta=dest.subtract(p.position());
            Vec3 permitted=allowed(p,delta,p.getBoundingBox());
            if(permitted.distanceToSqr(delta)>2.25 || !level.getWorldBorder().isWithinBounds(p.getBoundingBox().move(permitted))){endHold(p);return true;}
            place(p,p.position().add(permitted));
            if(held==18)SpiderFoley.play(boss,surface,SpiderFoley.Cue.STRAIN,1);
            if(held>=46){
                released=true;
                var turn=heading.yRot(arm==1?.66F:-.66F);
                flight=turn.scale(2.25).add(0,-.87,0);
                p.setDeltaMovement(flight);p.hurtMarked=true;
                SpiderFoley.play(boss,surface,SpiderFoley.Cue.SHED,1.6F);
                boss.goddess.publish();
            }
            return false;
        }
        if(!landed){
            if(feet==null || p.position().distanceToSqr(feet)>36){endHold(p);return true;}
            var box=p.getBoundingBox().move(feet.subtract(p.position()));
            if(!level.getWorldBorder().isWithinBounds(box.expandTowards(flight))){endHold(p);return true;}
            var motion=allowed(p,flight,box);
            place(p,feet.add(motion));
            if(motion.distanceToSqr(flight)>.0001){
                landed=true;boss.goddess.publish();
                var normal=flight.subtract(motion).normalize();
                var start=p.getBoundingBox().getCenter();
                double edge=Math.abs(normal.y)>.7?p.getBbHeight()*.5:p.getBbWidth()*.5;
                var impact=level.clip(new ClipContext(start,start.add(normal.scale(edge+1.5)),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
                if(impact.getType()==HitResult.Type.BLOCK){
                    TerrainImpactEffects.surfaceSound(boss,impact,1.5F,.72F);
                    TerrainImpactEffects.emit(boss,impact,1.8F,false);
                }
                SpiderFoley.play(boss,p.position(),SpiderFoley.Cue.FRACTURE,1.65F);
                p.hurt(boss.damageSources().mobAttack(boss),p.getMaxHealth()*.23F);
            }else{
                flight=flight.add(0,-.08,0).scale(.98);
                if(t-grip>69){p.setDeltaMovement(flight);p.hurtMarked=true;endHold(p);}
            }
        }
        return landed && t>=grip+72;
    }
    private static void put(CompoundTag t,String k,Vec3 v){if(v!=null){t.putDouble(k+"X",v.x);t.putDouble(k+"Y",v.y);t.putDouble(k+"Z",v.z);}}
    private static Vec3 vec(CompoundTag t,String k){return new Vec3(t.getDouble(k+"X"),t.getDouble(k+"Y"),t.getDouble(k+"Z"));}
    CompoundTag save(){
        var t=new CompoundTag();
        put(t,"Origin",origin);put(t,"Mark",mark);put(t,"Heading",heading);put(t,"Palm",previousPalm);put(t,"Feet",feet);put(t,"Flight",flight);put(t,"ContactOffset",contactOffset);
        t.putFloat("Yaw",yaw);t.putFloat("EntryYaw",entryYaw);t.putInt("Arm",arm);t.putInt("Grip",grip);
        t.putBoolean("Released",released);t.putBoolean("Landed",landed);t.putLong("Next",next);return t;
    }
    void load(CompoundTag t){
        origin=vec(t,"Origin");mark=vec(t,"Mark");heading=vec(t,"Heading");flight=vec(t,"Flight");contactOffset=vec(t,"ContactOffset");
        previousPalm=t.contains("PalmX")?vec(t,"Palm"):null;feet=t.contains("FeetX")?vec(t,"Feet"):null;
        yaw=t.getFloat("Yaw");entryYaw=t.getFloat("EntryYaw");arm=t.getInt("Arm");grip=t.contains("Grip")?t.getInt("Grip"):-1;
        released=t.getBoolean("Released");landed=t.getBoolean("Landed");next=t.getLong("Next");
    }
}
