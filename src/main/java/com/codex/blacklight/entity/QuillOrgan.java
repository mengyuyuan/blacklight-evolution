package com.codex.blacklight.entity;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.power.*;
import com.codex.blacklight.registry.*;
import com.codex.blacklight.infection.InfectedSoundCue;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.*;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;
import java.util.UUID;
import static com.codex.blacklight.config.GameplayBalanceConfig.*;

/** One organism from the wound to the player's shoulder. Only the server advances its resource ledger. */
public final class QuillOrgan extends Entity {
    public static final int ROOT=0, EXTRACT=1, RETURN=2, ORBIT=3, STRIKE=4, REJOIN=5, LIQUID=6;
    public static final int PIERCE_TICK=4, FIRST_DRAW_TICK=6, SECOND_DRAW_TICK=9, RELEASE_TICK=14;
    private static final EntityDataAccessor<Integer> PHASE=SynchedEntityData.defineId(QuillOrgan.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LAYERS=SynchedEntityData.defineId(QuillOrgan.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> START=SynchedEntityData.defineId(QuillOrgan.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Float> ENERGY=SynchedEntityData.defineId(QuillOrgan.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> ACTOR=SynchedEntityData.defineId(QuillOrgan.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> HOST=SynchedEntityData.defineId(QuillOrgan.class,EntityDataSerializers.INT);
    private static final java.util.Map<LivingEntity,java.lang.ref.WeakReference<QuillOrgan>> EXTRACTIONS=new java.util.WeakHashMap<>();
    private static final EntityDataAccessor<Integer> SIDE=SynchedEntityData.defineId(QuillOrgan.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SOCKET=SynchedEntityData.defineId(QuillOrgan.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FIRST_SLOT = SynchedEntityData.defineId(QuillOrgan.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FORMATION = SynchedEntityData.defineId(QuillOrgan.class, EntityDataSerializers.INT);
    // Only admitted, active spines occupy slots; distant attacking and returning spines keep theirs.
    private static final java.util.Map<ServerPlayer, java.util.Set<QuillOrgan>> HELD = new java.util.WeakHashMap<>();
    private ServerPlayer holder;
    private long life, totalLife, durationRank;
    private boolean leadSound=true;
    private UUID owner,host,target;
    private int targetRegion=-1;
    private int supply,initial,deposit;
    private float heal;
    private boolean collected;
    private Vec3 interpolation;
    private Vec3 oldFlight=Vec3.ZERO,flight=Vec3.ZERO;
    private int steps;
    public QuillOrgan(EntityType<? extends QuillOrgan> type, Level level) {
        super(type,level); noPhysics=true; setNoGravity(true);
    }
    public QuillOrgan(ServerLevel level,Mob host,UUID owner,Vec3 hit){
        this(ModEntities.QUILL_ORGAN.get(),level);this.host=host.getUUID();this.owner=owner;
        setPos(hit); entityData.set(HOST,host.getId()); entityData.set(LAYERS,QuillHarvest.mark(host).getInt("layers"));
        entityData.set(SOCKET,QuillHarvest.mark(host).getInt("socket"));
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(PHASE,ROOT);b.define(LAYERS,1);b.define(START,0L);b.define(ENERGY,1F);b.define(ACTOR,-1);b.define(HOST,-1);b.define(SIDE,0);b.define(SOCKET,0);
        b.define(FIRST_SLOT,0); b.define(FORMATION,1);
    }
    public int phase(){return entityData.get(PHASE);}
    public int firstSlot(){ return entityData.get(FIRST_SLOT); }
    public int formation(){ return entityData.get(FORMATION); }
    public void arrange(int slot, int total){
        entityData.set(FIRST_SLOT, slot);
        entityData.set(FORMATION, total);
    }

    public boolean retain(ServerPlayer p){
        if(holder==p)return true;
        releaseSlot();
        var held=HELD.computeIfAbsent(p, ignored -> java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>()));
        long limit=QuillFormation.capacity(InfiniteEvolutionSystem.effectiveRank(p,EvolutionAttribute.QUILL_SWARM));
        if(held.size()>=limit){
            held.removeIf(q -> q.isRemoved() || q.level()!=p.level());
            if(held.size()>=limit)return false;
        }
        held.add(this);
        holder=p;
        extendUntilRank(InfiniteEvolutionSystem.effectiveRank(p,EvolutionAttribute.QUILL_DURATION));
        return true;
    }
    private void releaseSlot(){
        if(holder==null)return;
        var held=HELD.get(holder);
        if(held!=null)held.remove(this);
        holder=null;
    }
    private void extendUntilRank(long rank){
        if(rank<=durationRank)return;
        life=QuillFormation.extendLife(life,rank-durationRank);
        totalLife=QuillFormation.extendLife(totalLife,rank-durationRank);
        durationRank=rank;
    }
    public static void extendHeld(ServerPlayer p,long rank){
        var held=HELD.get(p);
        if(held!=null)for(var q:held)q.extendUntilRank(rank);
    }
    @Override public void remove(RemovalReason reason){
        releaseSlot();
        super.remove(reason);
    }
    @Override public net.minecraft.world.phys.AABB getBoundingBoxForCulling(){
        return phase()==ORBIT ? super.getBoundingBoxForCulling().inflate(2*QUILL_ORBIT_RADIUS.get()+1) : super.getBoundingBoxForCulling().inflate(1);
    }
    public int socket(){return entityData.get(SOCKET);}
    public Vec3 heading(float partial){
        return oldFlight.lerp(flight,partial).normalize();
    }
    public int layers(){return entityData.get(LAYERS);}
    public float energy(){return entityData.get(ENERGY);}
    public int actor(){return entityData.get(ACTOR);}
    public int hostId(){return entityData.get(HOST);}
    public UUID owner(){return owner;}
    public UUID strikeTarget(){return phase()==STRIKE ? target : null;}
    public int supply(){return supply;}
    public long age(){return level().getGameTime()-entityData.get(START);}
    private void phase(int next){
        entityData.set(PHASE,next);entityData.set(START,level().getGameTime());
    }
    private void sound(InfectedSoundCue cue,float volume,float pitch){
        level().playSound(null,getX(),getY(),getZ(),ModInfectedSounds.event(cue),SoundSource.PLAYERS,volume,pitch);
    }
    public boolean arrivesRight(){
        int side=entityData.get(SIDE);
        return side==0 ? Math.cos(offset())>=0 : side>0;
    }
    public void routeShoulder(boolean right,boolean lead){
        entityData.set(SIDE,right?1:-1);
        leadSound=lead;
    }
    public void harvest(boolean fatal){
        if(phase()!=ROOT || fatal!=collected)return;
        phase(EXTRACT);sound(InfectedSoundCue.TENSION,leadSound?.42F:.15F,1.25F);
    }
    public void liquefy(){
        if(phase()!=ORBIT && phase()!=RETURN)return;
        releaseSlot();
        phase(LIQUID);sound(InfectedSoundCue.RETRACT,.44F,1.12F);
    }
    public boolean collect(ServerPlayer p,Mob victim,AssimilationSystem.Yield reward){
        if(collected)return false;
        var identity=com.codex.blacklight.disguise.DisguiseIdentity.capture(victim);
        var progressOnly=new AssimilationSystem.Yield(0,0,reward.ep(),reward.core(),reward.quality(),reward.samples());
        if(!AssimilationSystem.settle(p,victim,progressOnly,false))return false;
        initial=(int)Math.floor(reward.biomass()*QUILL_SPLIT.get());supply=initial;
        deposit=reward.biomass()-initial;heal=(float)(reward.healing()*QUILL_HEAL.get());
        totalLife=QuillHarvest.state(p).hasCombatSkill(CombatSkill.QUILL_ORBIT)?QUILL_LIFE_UP.get():QUILL_LIFE.get();
        life=totalLife;collected=true;
        com.codex.blacklight.city.MilitaryCampaignSystem.consumed(p,victim);
        com.codex.blacklight.disguise.DisguiseSystem.consumed(p,victim);
        com.codex.blacklight.disguise.DisguiseSystem.captured(p,identity);
        victim.getPersistentData().remove("BlacklightQuillParasite");
        return true;
    }
    @Override public boolean isPickable(){return false;}
    @Override public boolean isPushable(){return false;}
    @Override public void lerpTo(double x,double y,double z,float yaw,float pitch,int increments){
        interpolation=new Vec3(x,y,z);steps=2;
        setYRot(yaw);setXRot(pitch);
    }
    @Override public void tick(){
        super.tick();
        if(level().isClientSide){
            oldFlight=flight;
            if(steps>0 && interpolation!=null){
                var next=position().lerp(interpolation,1.0/steps);
                var delta=next.subtract(position());
                if(delta.lengthSqr()>.00001) flight=flight.lengthSqr()<.1?delta.normalize():flight.lerp(delta.normalize(),.74).normalize();
                setPos(next);steps--;
            }
            if(phase()==EXTRACT && level().getEntity(entityData.get(HOST)) instanceof LivingEntity victim)
                EXTRACTIONS.put(victim,new java.lang.ref.WeakReference<>(this));
            return;
        }
        var level=(ServerLevel)level();
        if(owner==null){discard();return;}
        ServerPlayer p=level.getServer().getPlayerList().getPlayer(owner);
        if(phase()<=EXTRACT){
            entityData.set(ACTOR,p!=null && p.level()==level ? p.getId() : -1);
            Mob mob=host!=null && level.getEntity(host) instanceof Mob m?m:null;
            if(mob!=null)entityData.set(HOST,mob.getId());
            if(!collected){
                if(mob==null){if(tickCount>40)discard();return;}
                var mark=QuillHarvest.mark(mob);
                if(!mob.isAlive() || !mark.hasUUID("organ") || !getUUID().equals(mark.getUUID("organ"))){discard();return;}
                setPos(QuillHarvest.anchor(mob,mark));entityData.set(LAYERS,mark.getInt("layers"));
                Vec3 outward=position().subtract(mob.getBoundingBox().getCenter()).normalize();
                setYRot((float)Math.toDegrees(Math.atan2(outward.x,outward.z)));
                setXRot((float)Math.toDegrees(Math.asin(Mth.clamp(outward.y,-1,1))));
            }
            if(phase()==EXTRACT)extract(p,mob);
            return;
        }
        if(!QuillHarvest.active(p) || p.level()!=level){discard();return;}
        entityData.set(ACTOR,p.getId());
        if(phase()==ORBIT || phase()==STRIKE || phase()==REJOIN){
            if(life<=0 || !retain(p) || --life<=0){discard();return;}
            if(tickCount%5==0) entityData.set(ENERGY,Math.min((float)life/Math.max(1,totalLife),supply/(float)Math.max(1,initial)));
        }
        switch(phase()){
            case RETURN,REJOIN -> flyHome(p);
            case ORBIT -> orbit(p);
            case STRIKE -> strike(level,p);
            case LIQUID -> absorb(p);
            default -> discard();
        }
    }
    private void extract(ServerPlayer p,Mob mob){
        if(!collected && (!QuillHarvest.active(p) || p.level()!=level())){phase(ROOT);return;}
        if(p!=null)entityData.set(ACTOR,p.getId());
        long t=age();
        if(t==PIERCE_TICK){
            if(p!=null && p.level()==level())ConsumeSounds.phase(p,"quill_extract",position(),leadSound?1F:.42F,leadSound?1F:1.08F);
            if(!collected && mob!=null && QuillHarvest.available(p,mob))
                ConsumeContactDamage.quillPuncture(p,mob);
        }
        if(t==PIERCE_TICK || t==SECOND_DRAW_TICK || t==RELEASE_TICK-1){
            var red=new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(.52F,.027F,.055F),.72F);
            ((ServerLevel)level()).sendParticles(red,getX(),getY(),getZ(),t==PIERCE_TICK?6:3,.075,.05,.075,.15);
        }
        if(t==FIRST_DRAW_TICK)sound(InfectedSoundCue.TEAR,leadSound?.64F:.18F,1.18F);
        if(t==SECOND_DRAW_TICK)sound(InfectedSoundCue.RETRACT,leadSound?.59F:.18F,.91F);
        if(t<RELEASE_TICK)return;
        if(!collected && (mob==null || !QuillHarvest.finish(p,mob,this))){phase(ROOT);return;}
        if(p!=null && p.level()==level())ConsumeSounds.phase(p,"quill_return",position(),leadSound?.96F:.38F,1F);
        phase(RETURN);
        if(p!=null)entityData.set(ACTOR,p.getId());
    }
    public double offset(){return (getUUID().getLeastSignificantBits() & 65535)*Math.PI*2/65536.0;}
    private static final java.util.Set<LivingEntity> SPENT=java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    public static boolean consumedRemains(LivingEntity host){
        if(!host.isDeadOrDying()){SPENT.remove(host);return false;}
        var ref=EXTRACTIONS.get(host);
        var organ=ref==null?null:ref.get();
        if(organ!=null && organ.phase()>=RETURN)SPENT.add(host);
        return SPENT.contains(host);
    }
    public static QuillOrgan extracting(LivingEntity host){
        var ref=EXTRACTIONS.get(host);var q=ref==null?null:ref.get();
        return q!=null && !q.isRemoved() && q.phase()==EXTRACT?q:null;
    }
    public Vec3 shoulder(ServerPlayer p){
        double side=arrivesRight()?-1:1;
        return p.position().add(new Vec3(side*p.getBbWidth()*.53,p.getBbHeight()*.81,-.13)
                .yRot((float)Math.toRadians(-p.yBodyRot)));
    }
    private boolean toward(Vec3 goal,double speed){
        Vec3 gap=goal.subtract(position());double length=gap.length();
        if(length<=speed){setPos(goal);return true;}
        setPos(position().add(gap.scale(speed/length)));return false;
    }
    private void flyHome(ServerPlayer p){
        Vec3 end=shoulder(p);
        if(phase()==RETURN && p.getHealth()<p.getMaxHealth())
            end=end.add(new Vec3(arrivesRight()?-.34:.34,.18,-.31).yRot((float)Math.toRadians(-p.yBodyRot)));
        if(phase()==REJOIN && age()==1)sound(InfectedSoundCue.LAUNCH,.5F,1.28F);
        double distance=position().distanceTo(end);
        if(distance>2){
            double curve=Math.min(1,distance*.12);
            end=end.add(new Vec3(Math.cos(offset())*curve,curve*.9,Math.sin(offset())*curve));
        }
        if(!toward(end,Math.min(1.65,.48+age()*.12)))return;
        if(phase()==RETURN){
            if(deposit>0){
                QuillHarvest.state(p).addBiomass(deposit);
                QuillHarvest.sync(p);
                deposit=0;
            }
            if(p.getHealth()<p.getMaxHealth()){
                // Let the liquid already at the shoulder settle before spending another shard.
                boolean healing=!level().getEntitiesOfClass(QuillOrgan.class,p.getBoundingBox().inflate(3),
                        q->q.phase()==LIQUID && owner.equals(q.owner)).isEmpty();
                if(!healing)liquefy();
                return;
            }
        }
        if(phase()==RETURN && supply>0 && !retain(p)){
            QuillHarvest.state(p).addBiomass(supply);
            QuillHarvest.sync(p);
            supply=0;
        }
        if(phase()==RETURN)ConsumeSounds.phase(p,"quill_merge",end,leadSound?.9F:.38F,1.06F);
        if(supply<=0){discard();return;}
        if(phase()==REJOIN)sound(InfectedSoundCue.RETRACT,.28F,1.38F);
        phase(ORBIT);
    }
    private void orbit(ServerPlayer p){
        double r=QUILL_ORBIT_RADIUS.get();
        Vec3 center=p.position().add(0,p.getBbHeight()*.80,0);
        Vec3 goal=center.add(QuillFormation.offset(Math.max(0,firstSlot()),formation(),level().getGameTime(),r,p.yBodyRot));
        var wall=level().clip(new net.minecraft.world.level.ClipContext(center,goal,net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE,this));
        if(wall.getType()!=net.minecraft.world.phys.HitResult.Type.MISS)
            goal=wall.getLocation().subtract(goal.subtract(center).normalize().scale(.22));
        setPos(position().lerp(goal,.45));
        if(tickCount%2==0)QuillHarvest.dispatch(p);
    }
    public void launch(TargetSelection prey,boolean audible){
        if(phase()!=ORBIT || holder==null || supply<QUILL_AUTO_COST.get())return;
        target=prey.target().getUUID();
        targetRegion=prey.region();
        supply-=QUILL_AUTO_COST.get();
        phase(STRIKE);
        if(audible)sound(InfectedSoundCue.LAUNCH,.49F,1.32F);
    }
    private static final double STRIKE_SPEED=1.65;
    private void strike(ServerLevel level,ServerPlayer p){
        var prey=target==null?null:new TargetSelection(level.getEntity(target),targetRegion);
        int pursuitTicks=12+Mth.ceil((QUILL_AUTO_RANGE.get()+QUILL_ORBIT_RADIUS.get())/STRIKE_SPEED);
        if(prey!=null && !com.codex.blacklight.combat.ConsumeLastChance.awaitingFinisher(prey.target()) && !QuillHarvest.protectedPet(prey.target()) && prey.selectable(p) && age()<pursuitTicks){
            Vec3 point=prey.point(p);
            var contact=level.clip(new net.minecraft.world.level.ClipContext(position(),point,net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE,this));
            if(contact.getType()==net.minecraft.world.phys.HitResult.Type.MISS){
                Vec3 from=position();
                if(!toward(point,STRIKE_SPEED))return;
                var receiver=prey.target();
                var type=level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getHolderOrThrow(QuillHarvest.VOLLEY_DAMAGE);
                var source=new net.minecraft.world.damagesource.DamageSource(type,this,p);
                float damage=QUILL_AUTO_DAMAGE.get().floatValue();
                var body=AnimatedHurtBody.of(receiver);
                boolean harvestable=receiver instanceof LivingEntity living && QuillHarvest.canHarvestImpact(p,living);
                boolean hit;
                if(body!=null){
                    var end=point.add(point.subtract(from).normalize().scale(.18));
                    var impact=HydraHurtContact.ray(body.hurtRegions(0),from,end,com.codex.blacklight.infection.InfectedAttackStyle.TEAR);
                    hit=impact!=null && body.hurtAt(source,damage,impact);
                }else if(receiver instanceof BlackwatchHelicopter craft){
                    hit=craft.hurtAt(source,damage,point);
                }else hit=receiver.hurt(source,damage);
                if(hit){
                    if(receiver instanceof LivingEntity living){
                        QuillHarvest.impact(p,living,point,socket(),harvestable);
                        var travel=point.distanceToSqr(from)>.0001?point.subtract(from):point.subtract(p.position());
                        QuillImpact.hit(p,living,point,travel.normalize(),false,0);
                    }else sound(InfectedSoundCue.PIERCE,.54F,1.1F);
                }
            }
        }
        target=null;if(supply>0)phase(REJOIN);else discard();
    }
    private void absorb(ServerPlayer p){
        long elapsed=age();
        Vec3 seam=p.position().add(new Vec3(arrivesRight()?-.06:.06,p.getBbHeight()*.63,-p.getBbWidth()*.24)
                .yRot((float)Math.toRadians(-p.yBodyRot)));
        double progress=Mth.clamp((elapsed-3)/7.0,0,1);
        Vec3 end=shoulder(p).lerp(seam,progress*progress*(3-2*progress));
        setPos(position().lerp(end,Math.min(1,.26+elapsed*.15)));
        if(elapsed==3)sound(InfectedSoundCue.PIERCE,.64F,.78F);
        if(elapsed==6)sound(InfectedSoundCue.MEND,.43F,1.14F);
        if(elapsed<11)return;
        float fraction=initial>0?supply/(float)initial:1;
        CriticalMassSystem.healFromConsume(p,heal*fraction);
        ConsumeSounds.phase(p,"quill_merge",seam,.98F,1F);
        discard();
    }
    @Override protected void addAdditionalSaveData(CompoundTag n){
        n.putLong("durationRank",durationRank);
        if(owner!=null)n.putUUID("owner",owner);if(host!=null)n.putUUID("host",host);if(target!=null)n.putUUID("target",target);
        if(target!=null)n.putInt("targetRegion",targetRegion);
        n.putInt("phase",phase());n.putLong("age",age());n.putInt("layers",layers());n.putInt("supply",supply);
        n.putInt("initial",initial);n.putInt("deposit",deposit);n.putLong("life",life);n.putLong("totalLife",totalLife);
        n.putFloat("heal",heal);n.putBoolean("collected",collected);n.putInt("side",entityData.get(SIDE));n.putInt("socket",socket());n.putBoolean("secondaryFoley",!leadSound);
    }
    @Override protected void readAdditionalSaveData(CompoundTag n){
        releaseSlot();
        durationRank=Math.max(0,n.getLong("durationRank"));
        owner=n.hasUUID("owner")?n.getUUID("owner"):null;host=n.hasUUID("host")?n.getUUID("host"):null;
        target=n.hasUUID("target")?n.getUUID("target"):null;targetRegion=n.contains("targetRegion")?n.getInt("targetRegion"):-1;
        entityData.set(PHASE,n.getInt("phase"));
        entityData.set(START,level().getGameTime()-n.getLong("age"));entityData.set(LAYERS,n.getInt("layers"));
        supply=n.getInt("supply");initial=n.getInt("initial");deposit=n.getInt("deposit");life=Math.max(0,n.getLong("life"));
        totalLife=Math.max(0,n.getLong("totalLife"));heal=n.getFloat("heal");collected=n.getBoolean("collected");entityData.set(SIDE,n.getInt("side"));entityData.set(SOCKET,Mth.clamp(n.getInt("socket"),0,5));leadSound=!n.getBoolean("secondaryFoley");
        // Before single-spine harvesting a donor could save extra projectiles in flight.
        // Its original organ is the only one that can still orbit, pay out or hit a target.
        if(n.hasUUID("swarmParent")){
            entityData.set(PHASE,REJOIN);
            life=0;
        }
    }
}
