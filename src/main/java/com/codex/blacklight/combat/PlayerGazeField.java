package com.codex.blacklight.combat;

import com.codex.blacklight.entity.*;
import com.codex.blacklight.power.*;
import com.codex.blacklight.registry.EncounterEntities;
import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.registry.ModInfectedSounds;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.*;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import java.util.Comparator;
import java.util.UUID;

/** A stationary pressure membrane. The owner commits a sight point and targets can leave before it closes. */
public final class PlayerGazeField extends Entity {
    public static final int REACH=40, CLOSE=10, BURST=32, END=41, BIOMASS=15, COOLDOWN=100;
    public static final double RADIUS=2.6;
    private static final String READY="BlacklightGazeReady", HELD="BlacklightGazeFieldId";
    private static final EntityDataAccessor<Long> BORN=SynchedEntityData.defineId(PlayerGazeField.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> VICTIM=SynchedEntityData.defineId(PlayerGazeField.class,EntityDataSerializers.INT);
    private UUID ownerId;
    public PlayerGazeField(EntityType<? extends PlayerGazeField> type,Level level){
        super(type,level);
        noPhysics=true;
        setNoGravity(true);
    }
    @Override protected void defineSynchedData(){
        entityData.define(BORN,0L);
        entityData.define(VICTIM,-1);
    }
    @Override public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket(){
        return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this);
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag){}
    @Override protected void readAdditionalSaveData(CompoundTag tag){}
    public float age(float partial){return level().getGameTime()-entityData.get(BORN)+partial;}
    public LivingEntity victim(){
        return level().getEntity(entityData.get(VICTIM)) instanceof LivingEntity mob ? mob : null;
    }
    public static float squeeze(LivingEntity entity,float partial){
        if(!entity.getPersistentData().contains(HELD))return 0;
        var field=entity.level().getEntity(entity.getPersistentData().getInt(HELD));
        if(!(field instanceof PlayerGazeField gaze) || gaze.victim()!=entity || !restrainable(entity))return 0;
        float age=gaze.age(partial);
        return (float)Mth.smoothstep(Mth.clamp((age-CLOSE)/14F,0F,1F))*(1-Mth.clamp((age-BURST)/5F,0F,1F));
    }
    private static boolean eligible(Player player,LivingEntity mob){
        return !(mob instanceof Player) && !(mob instanceof net.minecraft.world.entity.decoration.ArmorStand)
                && mob.isAlive() && !mob.isSpectator() && !player.isAlliedTo(mob)
                && !(mob instanceof TamableAnimal pet && pet.isTame());
    }
    private static boolean restrainable(LivingEntity target){
        return !ConsumeSequence.boss(target) && !(target instanceof BlacklightHydra)
                && !(target instanceof BlacklightHive) && !(target instanceof com.codex.blacklight.world.GreeneHost)
                && target.getBbWidth()<=3.4 && target.getBbHeight()<=4.8
                && target.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE)<1;
    }
    private static Vec3 nearest(AABB b,Vec3 p){
        return new Vec3(Mth.clamp(p.x,b.minX,b.maxX),Mth.clamp(p.y,b.minY,b.maxY),Mth.clamp(p.z,b.minZ,b.maxZ));
    }
    private static boolean visible(Entity observer,Vec3 start,Vec3 end){
        return observer.level().clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,observer)).getType()!=HitResult.Type.BLOCK;
    }
    public static Vec3 aim(Player player,float partial){
        Vec3 eye=player.getEyePosition(partial),end=eye.add(player.getViewVector(partial).scale(REACH));
        var wall=player.level().clip(new ClipContext(eye,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
        Vec3 stop=wall.getLocation();
        Vec3 mark=wall.getType()==HitResult.Type.BLOCK ? stop.add(Vec3.atLowerCornerOf(wall.getDirection().getNormal()).scale(.68)) : stop;
        double best=eye.distanceToSqr(stop);
        for(var mob:player.level().getEntitiesOfClass(LivingEntity.class,new AABB(eye,stop).inflate(.35),m->eligible(player,m))){
            var bounds=mob.getBoundingBox();
            var entry=bounds.inflate(.12).clip(eye,stop);
            if(entry.isEmpty() || eye.distanceToSqr(entry.get())>=best)continue;
            Vec3 mid=bounds.getCenter();
            // On huge enemies the actual near surface can be many blocks from the entity origin.
            var focus=entry.get().lerp(mid,Math.min(1,1.3/Math.max(.001,mid.distanceTo(entry.get()))));
            if(!visible(player,eye,focus))continue;
            best=eye.distanceToSqr(entry.get());
            mark=focus;
        }
        return mark;
    }
    public static void inherit(ServerPlayer player){
        var state=ModAttachments.getData(player,ModAttachments.PLAYER_STATE);
        if(state.unlock(PowerForm.GAZE_VACUOLE)){
            ModAttachments.setData(player,ModAttachments.PLAYER_STATE,state);
            ModAttachments.syncData(player,ModAttachments.PLAYER_STATE);
            player.displayClientMessage(Component.translatable("message.blacklight.gaze_inherited"),false);
        }
    }
    public static boolean release(ServerPlayer player){
        var state=ModAttachments.getData(player,ModAttachments.PLAYER_STATE);
        if(!player.isAlive() || player.isSpectator() || player.isPassenger() || !state.hasUnlocked(PowerForm.GAZE_VACUOLE)
                || !UtilityPowerSystem.active(player,PowerForm.GAZE_VACUOLE) || ConsumeSystem.active(player)
                || ModAttachments.getData(player,ModAttachments.CONSUMING) || GoddessHeldMovement.controls(player)
                || BiomassSystem.active(player) || BlockThrowSystem.held(player)!=null)return false;
        var level=player.serverLevel();
        long now=level.getGameTime();
        if(now<player.getPersistentData().getLong(READY) || state.getBiomass()<BIOMASS){
            player.displayClientMessage(Component.translatable(now<player.getPersistentData().getLong(READY)
                    ?"message.blacklight.gaze_cooling":"message.blacklight.gaze_biomass"),true);
            return false;
        }
        var at=aim(player,1);
        if(!level.hasChunkAt(net.minecraft.core.BlockPos.containing(at)) || !level.getWorldBorder().isWithinBounds(net.minecraft.core.BlockPos.containing(at)))return false;
        var field=new PlayerGazeField(EncounterEntities.PLAYER_GAZE_FIELD.get(),level);
        field.ownerId=player.getUUID();
        field.entityData.set(BORN,now);
        field.setPos(at);
        if(!level.addFreshEntity(field))return false;
        state.spendBiomass(BIOMASS);
        ModAttachments.setData(player,ModAttachments.PLAYER_STATE,state);
        ModAttachments.syncData(player,ModAttachments.PLAYER_STATE);
        player.getPersistentData().putLong(READY,now+COOLDOWN);
        SpiderFoley.play(field,at,SpiderFoley.Cue.LOAD,1.1F);
        return true;
    }
    @Override public void tick(){
        super.tick();
        var caught=victim();
        int age=(int)age(0);
        if(level().isClientSide){
            if(caught!=null && age<END)caught.getPersistentData().putInt(HELD,getId());
            return;
        }
        var level=(ServerLevel)level();
        var owner=ownerId==null?null:level.getPlayerByUUID(ownerId);
        if(!(owner instanceof ServerPlayer player) || !player.isAlive() || age>=END){discard();return;}
        if(age==CLOSE && visible(player,player.getEyePosition(),position())){
            var targets=level.getEntitiesOfClass(LivingEntity.class,getBoundingBox().inflate(RADIUS),mob->
                    eligible(player,mob) && !ConsumeSystem.grappleProtected(mob) && !isHeld(mob)
                    && nearest(mob.getBoundingBox(),position()).distanceToSqr(position())<=RADIUS*RADIUS
                    && visible(this,position(),nearest(mob.getBoundingBox(),position())));
            caught=targets.stream().min(Comparator.comparingDouble(mob->nearest(mob.getBoundingBox(),position()).distanceToSqr(position()))).orElse(null);
            if(caught!=null){
                entityData.set(VICTIM,caught.getId());
                caught.getPersistentData().putInt(HELD,getId());
                SpiderFoley.play(this,position(),SpiderFoley.Cue.CATCH,1.35F);
            }
            drawGround(level,player);
        }
        if(caught!=null && (!caught.isAlive() || ConsumeSystem.grappleProtected(caught))){
            if(caught.getPersistentData().getInt(HELD)==getId())caught.getPersistentData().remove(HELD);
            entityData.set(VICTIM,-1);
            caught=null;
        }
        if(caught!=null && age>=CLOSE && age<BURST && restrainable(caught)){
            var pull=position().subtract(caught.getBoundingBox().getCenter()).scale(.26);
            if(pull.lengthSqr()>1)pull=pull.normalize();
            if(caught instanceof Mob mob)mob.getNavigation().stop();
            caught.setDeltaMovement(pull);
            caught.hurtMarked=true;
            caught.fallDistance=0;
        }
        if(age==CLOSE+6)sound(level,ModInfectedSounds.Pressure.STRAIN,.9F);
        if(age==BURST){
            if(caught!=null && nearest(caught.getBoundingBox(),position()).distanceToSqr(position())<=RADIUS*RADIUS && visible(this,position(),nearest(caught.getBoundingBox(),position()))){
                boolean hit=caught.hurt(player.damageSources().indirectMagic(this,player),restrainable(caught)?12F:6F);
                if(hit){
                    var at=nearest(caught.getBoundingBox(),position());
                    var blood=new BlockParticleOption(ParticleTypes.BLOCK,net.minecraft.world.level.block.Blocks.NETHER_WART_BLOCK.defaultBlockState());
                    level.sendParticles(blood,at.x,at.y,at.z,30,.32,.24,.32,.16);
                }
            }
            sound(level,ModInfectedSounds.Pressure.BURST,1.5F);
        }
    }
    private static boolean isHeld(LivingEntity entity){
        if(!entity.getPersistentData().contains(HELD))return false;
        return entity.level().getEntity(entity.getPersistentData().getInt(HELD)) instanceof PlayerGazeField gaze
                && gaze.victim()==entity && gaze.age(0)<BURST;
    }
    @Override public void remove(RemovalReason reason){
        var mob=victim();
        if(mob!=null && mob.getPersistentData().getInt(HELD)==getId())mob.getPersistentData().remove(HELD);
        super.remove(reason);
    }
    private void sound(ServerLevel level,ModInfectedSounds.Pressure cue,float volume){
        level.playSound(null,getX(),getY(),getZ(),ModInfectedSounds.pressure(cue),SoundSource.PLAYERS,volume,1F);
    }
    private void drawGround(ServerLevel level,ServerPlayer player){
        if(!player.mayBuild() || !level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING))return;
        for(int i=0;i<4;i++){
            double angle=i*Math.PI/2+Math.toRadians(player.getYRot());
            var edge=position().add(Math.cos(angle)*2,0,Math.sin(angle)*2);
            var hit=level.clip(new ClipContext(edge.add(0,.35,0),edge.add(0,-1.35,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
            if(hit.getType()!=HitResult.Type.BLOCK || hit.getDirection()!=Direction.UP || !BlockThrowSystem.selectable(player,hit.getBlockPos()))continue;
            var state=level.getBlockState(hit.getBlockPos());
            if(!BlockThrowSystem.mayExtract(player,hit.getBlockPos(),state))continue;
            var rock=FallingBlockEntity.fall(level,hit.getBlockPos(),state);
            rock.setDeltaMovement(position().subtract(rock.position()).multiply(1,0,1).normalize().scale(.24).add(0,.29,0));
            rock.hurtMarked=true;
        }
    }
}
