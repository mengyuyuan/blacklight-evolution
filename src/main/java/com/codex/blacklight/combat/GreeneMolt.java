package com.codex.blacklight.combat;

import com.codex.blacklight.entity.EncounterMob;
import com.codex.blacklight.registry.ModEntities;
import com.codex.blacklight.registry.ModInfectedSounds;
import java.io.*;
import java.nio.*;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/** The server owns the captive brood and its deadlines. Non-interactive members of the swarm are drawn in a batch. */
public final class GreeneMolt {
    public interface Captive { void setMoltCaptive(boolean value); }
    public static final int DURATION=460;
    private static final String OWNER="GreeneMoltOwner";
    private static final String HOME="GreeneMoltAnchor";
    private static final int SLOTS=16;
    private static final int[] CUES={0,48,102,177,266,333};
    private final UUID[] captives=new UUID[SLOTS];
    private final GreeneMoltShockwave shockwave = new GreeneMoltShockwave();
    private int spawned,absent;
    public int absent(){return absent;}

    private static final class Tracks {
        final int[] spawn=new int[SLOTS], end=new int[SLOTS];
        final float[] pos;
        Tracks(){
            try(var in=new GZIPInputStream(GreeneMolt.class.getResourceAsStream("/data/blacklight/molt/targets.bin.gz"))){
                var b=ByteBuffer.wrap(in.readAllBytes()).order(ByteOrder.LITTLE_ENDIAN);
                int samples=b.getInt();if(samples!=231||b.getInt()!=SLOTS)throw new IOException("Invalid Greene targets");
                b.asIntBuffer().get(spawn);b.position(b.position()+4*SLOTS);
                b.asIntBuffer().get(end);b.position(b.position()+4*SLOTS);
                pos=new float[samples*SLOTS*3];b.asFloatBuffer().get(pos);
            }catch(IOException ex){throw new IllegalStateException(ex);}
        }
    }
    private static final class Source {static final Tracks TABLE=new Tracks();}
    private static final int HYDRA=12, SURFACED=60;
    private Vec3 hydraOffset=Vec3.ZERO;

    void prepare(EncounterMob boss){
        hydraOffset=Vec3.ZERO;
        var level=(ServerLevel)boss.level();
        var authored=position(boss,HYDRA,SURFACED);
        var reach=authored.subtract(boss.position()).multiply(1,0,1);
        for(int angle:new int[]{0,35,-35,75,-75,140,180}){
            for(double radius:new double[]{1,.78,.58}){
                var candidate=boss.position().add(reach.yRot((float)Math.toRadians(angle)).scale(radius));
                var floor=level.clip(new net.minecraft.world.level.ClipContext(candidate.add(0,10,0),candidate.add(0,-8,0),
                        net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,boss));
                if(floor.getType()!=net.minecraft.world.phys.HitResult.Type.BLOCK || floor.getDirection()!=net.minecraft.core.Direction.UP)continue;
                var base=floor.getLocation();
                var space=new net.minecraft.world.phys.AABB(base.add(-2.4,1,-2.4),base.add(2.4,15,2.4));
                if(level.getBlockCollisions(boss,space).iterator().hasNext())continue;
                var line=level.clip(new net.minecraft.world.level.ClipContext(boss.position().add(0,9,0),base.add(0,8,0),
                        net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,boss));
                if(line.getType()!=net.minecraft.world.phys.HitResult.Type.MISS)continue;
                hydraOffset=base.subtract(authored).yRot((float)Math.toRadians(boss.greeneBattle().collapseYaw()-180)).scale(1/GreeneForm.OUTBREAK_SCALE);
                return;
            }
        }
    }
    void writeOffset(CompoundTag tag){
        tag.putFloat("HydraX",(float)hydraOffset.x);tag.putFloat("HydraY",(float)hydraOffset.y);tag.putFloat("HydraZ",(float)hydraOffset.z);
    }
    public static Vec3 readOffset(CompoundTag tag){
        return new Vec3(tag.getFloat("HydraX"),tag.getFloat("HydraY"),tag.getFloat("HydraZ"));
    }
    private Vec3 position(EncounterMob boss,int slot,int tick){
        int a=Math.min(230,tick/2),b=Math.min(230,a+1);
        int k=(a*SLOTS+slot)*3,j=(b*SLOTS+slot)*3;float mix=(tick%2)*.5F;
        var p=Source.TABLE.pos;
        return boss.position().add(new Vec3(p[k]+(p[j]-p[k])*mix,p[k+1]+(p[j+1]-p[k+1])*mix,p[k+2]+(p[j+2]-p[k+2])*mix)
                .add(slot==HYDRA?hydraOffset:Vec3.ZERO).scale(GreeneForm.OUTBREAK_SCALE).yRot((float)Math.toRadians(180-boss.greeneBattle().collapseYaw())));
    }
    public boolean tick(EncounterMob boss, int age){
        if(!(boss.level() instanceof ServerLevel level))return false;
        if (age == 0) shockwave.begin(boss, level);
        shockwave.tick(boss, level, age);
        boss.spider.actions.moltBrood(level,age);
        int prior=absent;
        for(int i=0;i<SLOTS;i++){
            if(age<Source.TABLE.spawn[i] || (absent&(1<<i))!=0)continue;
            Entity actor=captives[i]==null?null:level.getEntity(captives[i]);
            if(age>=Source.TABLE.end[i]){
                if(actor!=null)actor.discard();
                captives[i]=null;continue;
            }
            Vec3 p=position(boss,i,age);
            if((spawned&(1<<i))==0){
                var type=i<12?ModEntities.COMMON_INFECTED.get():i==12?ModEntities.BLACKLIGHT_HYDRA.get():ModEntities.BLACKLIGHT_HUNTER.get();
                var nearby=level.getEntitiesOfClass(Mob.class,boss.getBoundingBox().inflate(52),m->m.getType()==type && m.isAlive()
                    && !m.getPersistentData().hasUUID(OWNER) && !ConsumeControl.protectedEntity(m)
                    && m.getPersistentData().hasUUID("EncounterOwner") && boss.getUUID().equals(m.getPersistentData().getUUID("EncounterOwner")));
                Mob mob=nearby.stream().min(java.util.Comparator.comparingDouble(m->m.distanceToSqr(p))).orElse(null);
                boolean fresh=mob==null;
                if(fresh)mob=type.create(level);
                if(mob==null)continue;
                ((Captive)mob).setMoltCaptive(true);
                mob.moveTo(p.x,p.y,p.z,0,0);mob.setNoAi(true);mob.setNoGravity(true);mob.noPhysics=true;
                mob.setSilent(true);mob.setInvisible(true);mob.setPersistenceRequired();
                mob.getPersistentData().putUUID(OWNER,boss.getUUID());mob.getPersistentData().putLong(HOME,boss.blockPosition().asLong());
                if(fresh && !level.addFreshEntity(mob)) {absent|=1<<i;continue;}
                captives[i]=mob.getUUID();spawned|=1<<i;actor=mob;
            }else if(actor==null){
                // During a chunk reload the owner may start ticking before its neighbouring captive is restored.
                continue;
            }
            if(!actor.isAlive()){absent|=1<<i;captives[i]=null;continue;}
            actor.noPhysics=true;actor.setPos(p.x,p.y,p.z);actor.setDeltaMovement(Vec3.ZERO);actor.fallDistance=0;
        }
        if(captives[HYDRA]!=null && (age==9 || age==38)){
            var vent=position(boss,HYDRA,SURFACED);
            SpiderFoley.ground(boss,vent,age==9?1.7F:2.45F,age==9?SpiderFoley.Cue.STRAIN:SpiderFoley.Cue.RUPTURE);
        }
        for(int i=0;i<CUES.length;i++)if(age==CUES[i]){
            Vec3 p=i==1?position(boss,13,age):i==2?position(boss,12,age):boss.position().add(0,i>=4?22:10,0);
            level.playSound(null,p.x,p.y,p.z,ModInfectedSounds.molt(i),SoundSource.HOSTILE,5F,1F);
        }
        return prior!=absent;
    }
    public static void orphanCheck(Mob mob){
        if(mob.tickCount%40!=0 || !(mob.level() instanceof ServerLevel level))return;
        var tag=mob.getPersistentData();
        if(!tag.hasUUID(OWNER))return;
        if(!level.hasChunkAt(BlockPos.of(tag.getLong(HOME))))return;
        var owner=level.getEntity(tag.getUUID(OWNER));
        if(owner instanceof EncounterMob boss && boss.greeneStage()==GreeneBattleCycle.MOLTING && boss.greeneBattle().molt.owns(mob.getUUID()))return;
        if(mob.tickCount>80)mob.discard();
    }
    private boolean owns(UUID id){for(UUID captive:captives)if(id.equals(captive))return true;return false;}
    public void clear(EncounterMob boss){
        if(boss.level() instanceof ServerLevel level)for(int i=0;i<SLOTS;i++){
            Entity actor=captives[i]==null?null:level.getEntity(captives[i]);
            if(actor!=null)actor.discard();
            captives[i]=null;
        }
    }
    public CompoundTag save(){
        var t=new CompoundTag();t.putInt("Spawned",spawned);t.putInt("Absent",absent);
        for(int i=0;i<SLOTS;i++)if(captives[i]!=null)t.putUUID("Actor"+i,captives[i]);
        writeOffset(t);shockwave.save(t);
        return t;
    }
    public void load(CompoundTag t){
        hydraOffset=readOffset(t);shockwave.load(t);
        spawned=t.getInt("Spawned");absent=t.getInt("Absent");
        for(int i=0;i<SLOTS;i++)captives[i]=t.hasUUID("Actor"+i)?t.getUUID("Actor"+i):null;
    }
}
