package com.codex.blacklight.combat;

import com.codex.blacklight.entity.BlacklightHydra;
import com.codex.blacklight.entity.EncounterMob;
import com.codex.blacklight.registry.ModInfectedSounds;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import static com.codex.blacklight.combat.GoddessAttacks.Move;

/** The actress, renderer and collision sample the same source, frozen aim, ring speed and spike footprint. */
public final class GoddessTerrainCast {
    public static final int PING = 15, HOWL = 44, RELEASE = 26;
    public static final int ROWS = 14, LANES = 7;
    public static final float SPIKE_RADIUS = 1.65F, SPIKE_HEIGHT = 4.2F;
    private final EncounterMob boss;
    private int sequence = -1, voice;
    private boolean ready;
    private Vec3 base = Vec3.ZERO, forward = new Vec3(0,0,1), aim = Vec3.ZERO;
    private final Vec3[] mouths = {Vec3.ZERO,Vec3.ZERO}, axes = {Vec3.ZERO,Vec3.ZERO};
    private final Set<UUID> hit = new HashSet<>();

    public GoddessTerrainCast(EncounterMob boss) { this.boss = boss; }
    public static boolean active(Move m) { return m == Move.SONIC_CRY || m == Move.SPIKE_FIELD || m == Move.INFECTION_WAVE; }
    public boolean ready() { return ready && sequence == boss.goddess.attacks.sequence(); }
    public Vec3 base() { return base; }
    public Vec3 aim() { return aim; }
    public Vec3 mouth(int pulse) { return mouths[pulse]; }
    public Vec3 axis(int pulse) { return axes[pulse]; }
    public static float soundRadius(float age,int pulse) { return (age - (pulse == 0 ? PING : HOWL)) * (pulse == 0 ? 3.9F : 3.0F); }
    public static float jawOpening(float age) {
        float ping=GoddessRush.ease(age,12,15)*(1-GoddessRush.ease(age,18,24));
        float howl=GoddessRush.ease(age,39,44)*(1-GoddessRush.ease(age,56,67));
        return .74F*ping+howl;
    }
    public static float soundReach(int pulse) { return pulse == 0 ? 24 : 48; }
    public static float soundAngle(int pulse) { return (float)Math.toRadians(pulse == 0 ? 32 : 54); }
    public static float waveRadius(float age) { return Math.max(0, age - RELEASE) * 1.72F; }
    public static int eruption(int row) { return 18 + row * 3; }
    public Vec3 spike(int row, int lane) {
        double depth=6+row*(42.0/(ROWS-1));
        double width=(lane-3)*7.5+Math.sin(row*.44)*1.8;
        return base.add(forward.scale(depth)).add(forward.z*width,0,-forward.x*width);
    }
    public Vec3 surface(Vec3 at) {
        if (!boss.level().hasChunkAt(BlockPos.containing(at))) return null;
        var hit = ray(at.add(0,7,0),at.add(0,-17,0));
        return hit.getType()==HitResult.Type.BLOCK && !hit.isInside() && hit.getDirection()==Direction.UP ? hit.getLocation() : null;
    }
    public boolean clear(Vec3 from,Vec3 to) { return ray(from,to).getType()==HitResult.Type.MISS; }
    private net.minecraft.world.phys.BlockHitResult ray(Vec3 from,Vec3 to) {
        return boss.level().clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,boss));
    }
    public boolean tick(ServerLevel level, ServerPlayer target, Move move, int age) {
        if (!active(move)) return true;
        if (sequence != boss.goddess.attacks.sequence() || !ready) {
            sequence=boss.goddess.attacks.sequence(); voice=0; hit.clear(); ready=true;
            Vec3 floor=surface(boss.position());
            base=floor==null?boss.position():floor;
            aim=target.getBoundingBox().getCenter();
            Vec3 heading=aim.subtract(base).multiply(1,0,1);
            forward=heading.lengthSqr()>.001 ? heading.normalize() : new Vec3(0,0,1).yRot((float)-Math.toRadians(boss.yBodyRot));
            mouths[0]=mouths[1]=axes[0]=axes[1]=Vec3.ZERO;
            boss.goddess.publish();
        }
        if (move==Move.SONIC_CRY) return sound(level,target,age);
        if (move==Move.SPIKE_FIELD) return spikes(level,age);
        return infectionWave(level,age);
    }
    private boolean sound(ServerLevel level,ServerPlayer target,int age) {
        if ((age<PING || (age>34 && age<HOWL)) && age%3==0) {
            aim=target.getBoundingBox().getCenter(); boss.goddess.publish();
        }
        if (age==PING || age==HOWL) {
            int pulse=age==PING?0:1;
            aim=target.getBoundingBox().getCenter();
            Vec3 origin=GoddessCastPose.lips(boss,1);
            mouths[pulse]=origin; axes[pulse]=aim.subtract(origin).normalize(); voice=pulse+1; hit.clear();
            level.playSound(null,origin.x,origin.y,origin.z,pulse==0?ModInfectedSounds.sonic(false):ModInfectedSounds.sonic(true),
                    SoundSource.HOSTILE,4.8F,1F);
            boss.goddess.publish();
        }
        if (voice>0) {
            int i=voice-1;
            double r=soundRadius(age,i);
            double step=i==0?3.9:3.0;
            if(r>0 && r<=soundReach(i) && age%3==0) {
                double drop=base.y-mouths[i].y;
                if(r*r>drop*drop){
                    double planar=Math.sqrt(r*r-drop*drop);
                    for(int sector=0;sector<16;sector++){
                        double a=sector*Math.PI/8;
                        Vec3 at=new Vec3(mouths[i].x+planar*Math.cos(a),base.y,mouths[i].z+planar*Math.sin(a));
                        Vec3 dir=at.subtract(mouths[i]).normalize();
                        if(dir.dot(axes[i])<Math.cos(soundAngle(i)))continue;
                        Vec3 floor=surface(at);
                        if(floor==null || !clear(mouths[i],floor.add(0,.22,0)))continue;
                        var pos=BlockPos.containing(floor.add(0,-.04,0));
                        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,level.getBlockState(pos)),
                                floor.x,floor.y+.09,floor.z,4,.38,.11,.38,.08);
                    }
                }
            }
            if (r>=0 && r<soundReach(i)+step) for (ServerPlayer p:level.players()) {
                if (!vulnerable(p) || hit.contains(p.getUUID())) continue;
                Vec3 at=p.getBoundingBox().getCenter(),delta=at.subtract(mouths[i]); double d=delta.length();
                if (d>soundReach(i) || d<r-step-.55 || d>r+.55 || axes[i].dot(delta.normalize())<Math.cos(soundAngle(i)) || !clear(mouths[i],at)) continue;
                strike(p,i==0?GoddessImpact.SONIC_PING:GoddessImpact.SONIC_HOWL);
                p.playNotifySound(ModInfectedSounds.pressure(ModInfectedSounds.Pressure.BURST),SoundSource.HOSTILE,.60F,i==0?1.35F:.82F);
            }
        }
        return age>=77;
    }
    private boolean spikes(ServerLevel level,int age) {
        for (int row=0;row<ROWS;row++) {
            int phase=age-eruption(row);
            if (phase != -10 && phase != 0 && phase != 1 && phase != 2 && phase != 3) continue;
            for(int lane=0;lane<LANES;lane++) {
                Vec3 root=surface(spike(row,lane));
                if(root==null || !clear(base.add(0,1.8,0),root.add(0,.9,0))) continue;
                if(phase==-10 || phase==0) {
                    var pos=BlockPos.containing(root.add(0,-.04,0));
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,level.getBlockState(pos)),root.x,root.y+.12,root.z,
                            phase==0?7:3,.32,.1,.32,phase==0?.19:.06);
                    if(lane==3 && row%3==0)SpiderFoley.play(boss,root,phase==0?SpiderFoley.Cue.FRACTURE:SpiderFoley.Cue.SCRAPE,2.7F);
                }
                if(phase<0) continue;
                double height=spikeHeight(phase+1);
                for(ServerPlayer p:level.players()) {
                    if(!vulnerable(p)||hit.contains(p.getUUID()))continue;
                    var bb=p.getBoundingBox();
                    if(bb.minY>=root.y+height || bb.maxY<root.y)continue;
                    double dx=Math.max(0,Math.max(bb.minX-root.x,root.x-bb.maxX));
                    double dz=Math.max(0,Math.max(bb.minZ-root.z,root.z-bb.maxZ));
                    double width=SPIKE_RADIUS*(1-Math.max(0,bb.minY-root.y)/height);
                    if(dx*dx+dz*dz<=width*width && clear(root.add(0,.1,0),p.getBoundingBox().getCenter()))strike(p,GoddessImpact.SPIKE_FIELD);
                }
            }
        }
        return age>=75;
    }
    public static float spikeHeight(float phase){
        if(phase<=0 || phase>=11)return 0;
        return SPIKE_HEIGHT*Math.min(1,phase/4F)*Math.min(1,(11-phase)/5F);
    }
    private boolean infectionWave(ServerLevel level,int age) {
        if(age==RELEASE) {
            SpiderFoley.play(boss,base.add(0,3,0),SpiderFoley.Cue.RUPTURE,4.3F);
            level.playSound(null,base.x,base.y,base.z,ModInfectedSounds.pressure(ModInfectedSounds.Pressure.BURST),SoundSource.HOSTILE,4.4F,.68F);
        }
        if(age>RELEASE && age<=RELEASE+39) {
            double r=waveRadius(age);
            if((age-RELEASE)%5==0) for(int i=0;i<12;i++) {
                double a=(i+.25*(age%4))*Math.PI/6;
                Vec3 floor=surface(base.add(r*Math.cos(a),0,r*Math.sin(a)));
                if(floor==null || !clear(base.add(0,1.8,0),floor.add(0,.75,0)))continue;
                var block=level.getBlockState(BlockPos.containing(floor.add(0,-.04,0)));
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,block),floor.x,floor.y+.18,floor.z,6,.42,.17,.42,.15);
                level.sendParticles(ParticleTypes.CRIMSON_SPORE,floor.x,floor.y+.7,floor.z,4,.38,.24,.38,.07);
            }
            var box = new AABB(base.add(-r-1,-16,-r-1),base.add(r+1,12,r+1));
            var actors=level.getEntities(boss,box,e->e instanceof ServerPlayer p && vulnerable(p) || e instanceof Mob m
                    && SpiderPhase.edible(m) && !(m instanceof BlacklightHydra) && !m.isNoAi() && !m.noPhysics
                    && !m.isPassenger() && !m.isVehicle() && !ConsumeControl.protectedEntity(m) && !ConsumeControl.ownsMotion(m));
            for(var e:actors){
                if(hit.contains(e.getUUID()))continue;
                Vec3 delta=e.position().subtract(base).multiply(1,0,1);
                if(delta.length()<r-2.35 || delta.length()>r+.52)continue;
                Vec3 floor=surface(new Vec3(e.getX(),base.y,e.getZ()));
                if(floor==null || e.getY()<floor.y-.1 || e.getY()>floor.y+1.05 || !clear(base.add(0,1.8,0),e.position().add(0,.8,0)))continue;
                if(e instanceof ServerPlayer p)strike(p,GoddessImpact.INFECTION_WAVE); else hit.add(e.getUUID());
                Vec3 push=delta.normalize().scale(e instanceof Mob?.88:.38);
                e.push(push.x,e instanceof Mob?.52:.24,push.z);e.hurtMarked=true;
            }
        }
        return age>=69;
    }
    private static boolean vulnerable(ServerPlayer p){return p.isAlive()&&!p.isCreative()&&!p.isSpectator();}
    private void strike(ServerPlayer p,GoddessImpact impact) {
        hit.add(p.getUUID());
        p.hurt(boss.damageSources().mobAttack(boss),impact.incoming(p));
    }
    private static void vec(CompoundTag t,String key,Vec3 v){
        t.putDouble(key+"X",v.x);t.putDouble(key+"Y",v.y);t.putDouble(key+"Z",v.z);
    }
    private static Vec3 vec(CompoundTag t,String key){return new Vec3(t.getDouble(key+"X"),t.getDouble(key+"Y"),t.getDouble(key+"Z"));}
    public CompoundTag save(){
        var t=new CompoundTag(); if(!ready)return t;
        t.putInt("Sequence",sequence);t.putInt("Voice",voice);
        vec(t,"Base",base);vec(t,"Forward",forward);vec(t,"Aim",aim);
        for(int i=0;i<2;i++){vec(t,"M"+i,mouths[i]);vec(t,"A"+i,axes[i]);}
        if(!hit.isEmpty()){
            var ids=new ListTag();for(var id:hit){var entry=new CompoundTag();entry.putUUID("Id",id);ids.add(entry);}t.put("Hit",ids);
        }
        return t;
    }
    public void load(CompoundTag t){
        ready=t.contains("Sequence");sequence=ready?t.getInt("Sequence"):-1;hit.clear();
        if(!ready)return;
        base=vec(t,"Base");forward=vec(t,"Forward");aim=vec(t,"Aim");voice=Math.min(2,Math.max(0,t.getInt("Voice")));
        for(int i=0;i<2;i++){mouths[i]=vec(t,"M"+i);axes[i]=vec(t,"A"+i);}
        var ids=t.getList("Hit",Tag.TAG_COMPOUND);for(int i=0;i<ids.size();i++){var e=ids.getCompound(i);if(e.hasUUID("Id"))hit.add(e.getUUID("Id"));}
    }
}
