package com.codex.blacklight.entity;

import com.codex.blacklight.infection.NativeInfectedCombat;
import com.codex.blacklight.registry.ModEntities;
import com.codex.blacklight.registry.ModParticles;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.*;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import org.joml.Vector3f;
import java.util.*;

/** Shootable flesh projectile -> burst -> extending/contacting/retracting tendrils.
 * No block deletion, no per-frame damage, no invisible sphere of delayed damage. */
public final class InfectedTumor extends ThrowableItemProjectile {
    private static final EntityDataAccessor<CompoundTag> BURST=SynchedEntityData.defineId(InfectedTumor.class,EntityDataSerializers.COMPOUND_TAG);
    private float damage=12,radius=4;
    private boolean pierced,retractSound;
    public InfectedTumor(EntityType<? extends InfectedTumor> type,Level level){super(type,level);}
    public InfectedTumor(Level level,BlacklightInfectedHost owner){super(ModEntities.INFECTED_TUMOR.get(),owner,level);}
    @Override protected void defineSynchedData(){super.defineSynchedData();entityData.define(BURST,new CompoundTag());}
    @Override protected Item getDefaultItem(){return Items.ROTTEN_FLESH;}
    @Override protected float getGravity(){return .003F;}
    public void configure(float damage,float radius){this.damage=Float.isFinite(damage)?com.codex.blacklight.forge.WingMath.clamp(damage,1,1000000):12;this.radius=Float.isFinite(radius)?com.codex.blacklight.forge.WingMath.clamp(radius,2,8):4;}
    public boolean erupted(){return !entityData.get(BURST).isEmpty();}
    public float burstAge(float partial){return erupted()?level().getGameTime()-entityData.get(BURST).getLong("At")+partial:-1;}
    public List<Vec3> tendrils(){var points=new ArrayList<Vec3>();for(var element:entityData.get(BURST).getList("Arms",Tag.TAG_COMPOUND)){var t=(CompoundTag)element;points.add(new Vec3(t.getDouble("X"),t.getDouble("Y"),t.getDouble("Z")));}return points;}
    @Override protected boolean canHitEntity(Entity entity){return !erupted()&&super.canHitEntity(entity)&&!entity.is(getOwner())
            &&!(entity instanceof net.minecraft.world.entity.projectile.Projectile)
            &&(!(entity instanceof LivingEntity living)||owner()!=null&&NativeInfectedCombat.enemy(owner(),living));}
    private BlacklightInfectedHost owner(){return getOwner() instanceof BlacklightInfectedHost h&&h.isAlive()&&h.level()==level()?h:null;}
    @Override protected void onHit(HitResult hit){
        if(erupted())return;
        // Offset outside the contacted surface so its own support block does not occlude every arm.
        if(hit instanceof BlockHitResult b)setPos(b.getLocation().add(Vec3.atLowerCornerOf(b.getDirection().getNormal()).scale(.07)));
        else setPos(hit.getLocation());
        if(!level().isClientSide)burst();
    }
    public void burst(){
        if(level().isClientSide||erupted())return;
        if(owner()==null){discard();return;}
        setDeltaMovement(Vec3.ZERO);setNoGravity(true);var state=new CompoundTag();state.putLong("At",level().getGameTime());var arms=new ListTag();
        var targets=level().getEntitiesOfClass(LivingEntity.class,getBoundingBox().inflate(radius),e->NativeInfectedCombat.enemy(owner(),e)&&e.getBoundingBox().getCenter().distanceToSqr(position())<=radius*radius&&visible(e.getBoundingBox().getCenter()));
        targets.sort(Comparator.comparingDouble(e->e.distanceToSqr(this)));
        for(var target:targets.stream().limit(6).toList())addArm(arms,target.getBoundingBox().getCenter().subtract(position()));
        for(int i=arms.size();i<6;i++){double angle=i*Math.PI/3+getId()*.3;var goal=position().add(Math.cos(angle)*radius,.2+random.nextDouble()*.8,Math.sin(angle)*radius);var trace=level().clip(new ClipContext(position(),goal,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));addArm(arms,trace.getLocation().subtract(position()));}
        state.put("Arms",arms);entityData.set(BURST,state);
        for(var target:targets)if(target.getBoundingBox().getCenter().distanceToSqr(position())<=2.3*2.3)hurt(target,damage*.35F);
        var server=(ServerLevel)level();server.sendParticles(ModParticles.IMPACT_SMOKE.get(),getX(),getY(),getZ(),10,.6,.45,.6,.04);
        server.sendParticles(new DustParticleOptions(new Vector3f(.45F,.018F,.035F),1.4F),getX(),getY(),getZ(),18,.5,.4,.5,.04);
        com.codex.blacklight.registry.ModInfectedSounds.play(this,com.codex.blacklight.infection.InfectedSoundCue.ERUPT);
    }
    private static void addArm(ListTag arms,Vec3 offset){var t=new CompoundTag();t.putDouble("X",offset.x);t.putDouble("Y",offset.y);t.putDouble("Z",offset.z);arms.add(t);}
    private boolean visible(Vec3 end){return level().clip(new ClipContext(position(),end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this)).getType()==HitResult.Type.MISS;}
    private void hurt(LivingEntity target,float amount){
        var owner=owner();if(owner==null||!NativeInfectedCombat.enemy(owner,target))return;
        if(target.hurt(damageSources().thrown(this,owner),amount)){
            ((ServerLevel)level()).sendParticles(ModParticles.BIOMASS_SLASH.get(),target.getX(),target.getY()+target.getBbHeight()*.5,target.getZ(),4,.18,.22,.18,.025);
            target.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WITHER,70,0),owner);
            var d=target.position().subtract(position()).normalize();target.push(d.x*.35,.12,d.z*.35);target.hurtMarked=true;
        }
    }
    @Override public void tick(){
        if(!erupted()){super.tick();if(!level().isClientSide&&(tickCount>100||owner()==null))discard();return;}
        // ThrowableItemProjectile.tick would still run collision and gravity during retraction.
        baseTick();setDeltaMovement(Vec3.ZERO);
        if(level().isClientSide)return;
        if(owner()==null){discard();return;}
        float age=burstAge(0);
        if(age>=12&&!pierced){
            pierced=true;var contacts=new HashSet<Integer>();
            for(var offset:tendrils()){
                var end=position().add(offset);
                for(var target:level().getEntitiesOfClass(LivingEntity.class,new AABB(position(),end).inflate(.4),e->NativeInfectedCombat.enemy(owner(),e))){
                    var box=target.getBoundingBox().inflate(.32);
                    if(!contacts.contains(target.getId())&&(box.contains(position())||box.clip(position(),end).isPresent())&&visible(target.getBoundingBox().getCenter())){contacts.add(target.getId());hurt(target,damage*.85F);}
                }
            }
        }
        if(age>=24&&!retractSound){retractSound=true;com.codex.blacklight.registry.ModInfectedSounds.play(this,com.codex.blacklight.infection.InfectedSoundCue.RETRACT);}
        if(age>=36)discard();
    }
    @Override public boolean isPickable(){return !erupted()&&!isRemoved();}
    @Override public boolean hurt(DamageSource source,float amount){
        if(level().isClientSide||erupted()||source.getEntity()==getOwner()||amount<=0)return false;
        ((ServerLevel)level()).sendParticles(ModParticles.BIOMASS_SLASH.get(),getX(),getY(),getZ(),7,.15,.15,.15,.04);discard();return true;
    }
}
