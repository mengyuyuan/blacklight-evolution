package com.codex.blacklight.entity;

import com.codex.blacklight.combat.SupremeHunterBalance;
import com.codex.blacklight.combat.SupremeHunterMove;
import com.codex.blacklight.combat.SupremeHunterStagger;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

/** Ground chunk torn up and thrown by the Supreme Hunter. Never places or breaks blocks. */
public final class SupremeHunterDebris extends ThrowableItemProjectile {
    public SupremeHunterDebris(EntityType<? extends SupremeHunterDebris> type,Level level){super(type,level);}
    public SupremeHunterDebris(Level level,LivingEntity owner,BlockState block){
        super(com.codex.blacklight.registry.ModEntities.SUPREME_HUNTER_DEBRIS.get(),owner,level);
        Item item=block==null?Items.COBBLESTONE:block.getBlock().asItem();
        setItem(new ItemStack(item==Items.AIR?Items.COBBLESTONE:item));
    }
    @Override protected Item getDefaultItem(){return Items.COBBLESTONE;}
    @Override protected float getGravity(){return .045F;}
    @Override protected void onHitEntity(EntityHitResult hit){
        super.onHitEntity(hit);
        if(level().isClientSide||!(getOwner() instanceof BlacklightSupremeHunter boss)||hit.getEntity()==boss)return;
        if(hit.getEntity() instanceof LivingEntity target&&!(target instanceof BlacklightSupremeHunter)
                &&SupremeHunterBalance.hit(boss,target,SupremeHunterMove.PICKUP_THROW,damageSources().thrown(this,boss))){
            Vec3 d=getDeltaMovement().multiply(1,0,1).normalize();
            target.setDeltaMovement(target.getDeltaMovement().add(d.scale(.8)).add(0,.3,0));target.hurtMarked=true;
            if(target instanceof ServerPlayer p)SupremeHunterStagger.apply(p,SupremeHunterMove.PICKUP_THROW.stagger,boss.getYRot());
        }
    }
    @Override protected void onHit(HitResult hit){
        super.onHit(hit);
        if(level().isClientSide)return;
        var server=(ServerLevel)level();
        var state=getItem().getItem() instanceof BlockItem b?b.getBlock().defaultBlockState():Blocks.COBBLESTONE.defaultBlockState();
        server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,state),getX(),getY(),getZ(),40,.6,.4,.6,.15);
        server.sendParticles(com.codex.blacklight.registry.ModParticles.IMPACT_SMOKE.get(),getX(),getY(),getZ(),6,.5,.3,.5,.03);
        playSound(SoundEvents.GENERIC_EXPLODE,1.2F,1.1F);
        discard();
    }
    @Override public void tick(){super.tick();if(!level().isClientSide&&tickCount>120)discard();}
}
