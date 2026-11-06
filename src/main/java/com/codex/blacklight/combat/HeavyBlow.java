package com.codex.blacklight.combat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import java.util.*;

/**
 * Heavy blows after Prototype's hit reactions: launched bodies trail a motion streak,
 * hit walls (hitreaction .../float/hit_wall) for a second impact, bowl over bystanders,
 * then lie down before getting up. Overkill heavy hits burst the body into flesh.
 */
@EventBusSubscriber(modid="blacklight")
public final class HeavyBlow {
    private static final class Flight {
        final UUID attacker;final Vec3 direction;final float damage;final long started;final boolean full;
        final Set<UUID> bowled=new HashSet<>();Vec3 before=Vec3.ZERO;boolean walled;
        Flight(UUID attacker,Vec3 direction,float damage,long started,boolean full){this.attacker=attacker;this.direction=direction;this.damage=damage;this.started=started;this.full=full;}
    }
    private static final Map<LivingEntity,Flight> FLYING=new WeakHashMap<>();
    private static final Map<LivingEntity,Long> LANDED=new WeakHashMap<>();
    private HeavyBlow(){}

    /** Extra lie-down time for a body that was just thrown by a heavy blow. */
    public static boolean recentlyThrown(LivingEntity v){var at=LANDED.get(v);return FLYING.containsKey(v)||at!=null&&v.level().getGameTime()-at<6;}

    /** Called after the ordinary impact; returns true when the heavy launch took over the knockback. */
    public static boolean apply(ServerPlayer p,LivingEntity v,WeaponMove move,ImpactTier tier,float charge,Vec3 direction,boolean controlled){
        boolean full=charge>=.9F;
        boolean killed=!v.isAlive()||v.getHealth()<=0;
        float dealt=move.damageFor(move.form)*(1+.5F*charge);
        if(killed){if((tier.atLeast(ImpactTier.HEAVY)||full)&&!burst(p,v,direction,tier,full,dealt))dismember(p,v,move,direction);return false;}
        if(!controlled||!(tier.atLeast(ImpactTier.HEAVY)||full)||CombatTargeting.multipart(v)||v instanceof Player)return false;
        double resistance=Math.max(0,Math.min(1,1-v.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE)));
        double volume=v.getBbWidth()*v.getBbWidth()*v.getBbHeight();
        if(volume>=4||resistance<.05)return false;
        double mass=Math.max(.25,Math.min(1,.8/Math.max(.6,volume)));
        var flat=new Vec3(direction.x,0,direction.z);if(flat.lengthSqr()<1e-4)flat=Vec3.directionFromRotation(0,p.getYRot());flat=flat.normalize();
        double speed=(full?1.75:tier==ImpactTier.FINISHER?1.35:.95)*mass*resistance;
        double lift=(full?.42:tier==ImpactTier.FINISHER?.36:.28)*Math.sqrt(mass)*resistance;
        if(move.uncharged()==WeaponMove.COMBO_FLOAT||move.uncharged()==WeaponMove.MUSCLE_UPPERCUT||ImpactProfile.of(move).lift()<0)return false; // launchers/slams keep their own arc
        v.setDeltaMovement(flat.x*speed,Math.max(v.getDeltaMovement().y,lift),flat.z*speed);v.setOnGround(false);v.hurtMarked=true;
        FLYING.put(v,new Flight(p.getUUID(),flat,dealt,v.level().getGameTime(),full));
        // Re-store the launch inside any running contact freeze, so it survives the hold.
        if(HitStop.held(v)||full)HitStop.victim(v,full?8:tier.hitstop);
        if(full){
            // Prototype's slowMotion/small: a short slow window once the contact freeze lets go.
            HeavySlowMotion.trigger(p,8);
            // Full charge: the longest freeze plus the heaviest camera hit (prototype_musclemass/hit_freeze + full_charge_attack).
            com.codex.blacklight.forge.PacketDistributor.sendToPlayer(p,new com.codex.blacklight.network.PhysicalFeedbackPayload(com.codex.blacklight.network.PhysicalFeedbackPayload.Effect.CLAW_HEAVY,1.6F));
            v.level().playSound(null,v.getX(),v.getY(),v.getZ(),SoundEvents.GENERIC_EXPLODE,SoundSource.PLAYERS,.55F,1.35F);
        }
        return true;
    }

    @SubscribeEvent public static void before(ForgeEntityTickEvent.Pre e){
        if(!(e.getEntity() instanceof LivingEntity v)||v.level().isClientSide)return;
        var f=FLYING.get(v);if(f!=null&&!HitStop.held(v))f.before=v.getDeltaMovement();
    }
    @SubscribeEvent public static void after(ForgeEntityTickEvent.Post e){
        if(!(e.getEntity() instanceof LivingEntity v)||!(v.level() instanceof ServerLevel level))return;
        var f=FLYING.get(v);if(f==null)return;
        long age=level.getGameTime()-f.started;
        if(!v.isAlive()||v.isRemoved()||age>45){FLYING.remove(v);return;}
        if(HitStop.held(v))return;
        double speed=Math.sqrt(f.before.x*f.before.x+f.before.z*f.before.z);
        var mid=v.position().add(0,v.getBbHeight()*.5,0);
        // Knockback motion trail.
        if(speed>.25)level.sendParticles(ParticleTypes.CLOUD,mid.x,mid.y,mid.z,1,.08,.08,.08,.005);
        var attacker=level.getServer().getPlayerList().getPlayer(f.attacker);
        if(!f.walled&&speed>.35&&v.horizontalCollision)wall(level,v,f,attacker,speed);
        if(attacker!=null&&speed>.3)bowl(level,v,f,attacker,speed);
        if(age>3&&v.onGround()){
            FLYING.remove(v);LANDED.put(v,level.getGameTime());
            level.sendParticles(ParticleTypes.POOF,v.getX(),v.getY()+.1,v.getZ(),8,v.getBbWidth()*.5,.05,v.getBbWidth()*.5,.03);
            level.playSound(null,v.getX(),v.getY(),v.getZ(),SoundEvents.GENERIC_BIG_FALL,SoundSource.HOSTILE,.9F,.7F);
        }
    }
    private static void wall(ServerLevel level,LivingEntity v,Flight f,ServerPlayer attacker,double speed){
        f.walled=true;
        var at=v.position().add(0,v.getBbHeight()*.5,0).add(f.direction.scale(v.getBbWidth()*.5+.3));
        var pos=BlockPos.containing(at);var state=level.getBlockState(pos);
        if(state.isAir()){pos=pos.below();state=level.getBlockState(pos);}
        if(!state.isAir()){
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,state),at.x,at.y,at.z,26,.25,.35,.25,.18);
            for(int i=0;i<4;i++){
                var out=f.direction.scale(-.18-.06*i).add((level.random.nextDouble()-.5)*.2,.18+.06*i,(level.random.nextDouble()-.5)*.2);
                com.codex.blacklight.movement.LandingDebrisSystem.fragment(level,at.subtract(f.direction.scale(.3)),state,out,.18F+.05F*i,14);
            }
        }
        level.sendParticles(ParticleTypes.EXPLOSION,at.x,at.y,at.z,1,0,0,0,0);
        level.playSound(null,at.x,at.y,at.z,SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR,SoundSource.PLAYERS,1F,.55F);
        level.playSound(null,at.x,at.y,at.z,SoundEvents.PLAYER_ATTACK_KNOCKBACK,SoundSource.PLAYERS,1F,.5F);
        float damage=(float)(f.damage*Math.min(.9,.35+.35*speed));
        v.invulnerableTime=0;
        if(attacker!=null)v.hurt(attacker.damageSources().playerAttack(attacker),damage);else v.hurt(level.damageSources().flyIntoWall(),damage);
        // Slide down the wall, then the normal knockdown / get-up runs on landing.
        v.setDeltaMovement(f.direction.x*-.06,-.12,f.direction.z*-.06);v.hurtMarked=true;
        HitStop.victim(v,3);
        if(attacker!=null)com.codex.blacklight.forge.PacketDistributor.sendToPlayer(attacker,new com.codex.blacklight.network.PhysicalFeedbackPayload(com.codex.blacklight.network.PhysicalFeedbackPayload.Effect.CLAW_HEAVY,1.1F));
    }
    private static void bowl(ServerLevel level,LivingEntity v,Flight f,ServerPlayer attacker,double speed){
        for(var other:level.getEntitiesOfClass(LivingEntity.class,v.getBoundingBox().inflate(.35),o->o!=v&&o!=attacker&&o.isAlive()&&!f.bowled.contains(o.getUUID()))){
            if(!CombatTargeting.selectable(attacker,other)||CombatTargeting.multipart(other))continue;
            f.bowled.add(other.getUUID());
            other.invulnerableTime=0;
            other.hurt(attacker.damageSources().playerAttack(attacker),f.damage*.35F);
            double resist=Math.max(0,1-other.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE));
            if(ImpactControl.begin(other,new ImpactProfile(HitReaction.HEAVY,ImpactProfile.FALL,30,.6,.3,true),attacker.getYRot())){
                var push=f.direction.scale(speed*.7*resist);other.setDeltaMovement(push.x,.32*resist,push.z);other.setOnGround(false);other.hurtMarked=true;
                if(other instanceof Mob mob)mob.getNavigation().stop();
            }
            level.playSound(null,other.getX(),other.getY(),other.getZ(),SoundEvents.PLAYER_ATTACK_KNOCKBACK,SoundSource.PLAYERS,.9F,.75F);
            // The thrown body loses part of its momentum to the one it hit.
            var d=v.getDeltaMovement();v.setDeltaMovement(d.x*.7,d.y,d.z*.7);v.hurtMarked=true;
        }
    }
    /** Overkill: no ordinary collapse, the body bursts into flesh chunks along the blow. */
    private static boolean burst(ServerPlayer p,LivingEntity v,Vec3 direction,ImpactTier tier,boolean full,float dealt){
        if(!(v.level() instanceof ServerLevel level)||v instanceof Player||CombatTargeting.multipart(v))return false;
        // Only blows that clearly outweigh the body: full charge, finishers, or a heavy hit on a small creature.
        if(!full&&tier!=ImpactTier.FINISHER&&v.getMaxHealth()>dealt*1.5F)return false;
        var flat=new Vec3(direction.x,0,direction.z);if(flat.lengthSqr()<1e-4)flat=Vec3.directionFromRotation(0,p.getYRot());
        return shatter(v,flat);
    }
    /** Bursts a dead, ordinary body into flesh chunks flying along {@code direction}. Thrown bodies and blocks share it. */
    public static boolean shatter(LivingEntity v,Vec3 direction){
        if(!(v.level() instanceof ServerLevel level)||v instanceof Player||CombatTargeting.multipart(v)||v.isAlive()&&v.getHealth()>0||v.isInvisible())return false;
        var c=v.position().add(0,v.getBbHeight()*.55,0);double size=Math.max(.6,Math.min(2.2,v.getBbHeight()));
        var flat=new Vec3(direction.x,0,direction.z);if(flat.lengthSqr()<1e-4)flat=Vec3.directionFromRotation(0,v.getYRot());flat=flat.normalize();
        int chunks=(int)Math.round(5+size*3);
        var flesh=new net.minecraft.world.level.block.state.BlockState[]{Blocks.NETHER_WART_BLOCK.defaultBlockState(),Blocks.CRIMSON_HYPHAE.defaultBlockState(),Blocks.RED_MUSHROOM_BLOCK.defaultBlockState(),Blocks.BONE_BLOCK.defaultBlockState()};
        for(int i=0;i<chunks;i++){
            var rnd=new Vec3(level.random.nextDouble()-.5,level.random.nextDouble()*.6+.2,level.random.nextDouble()-.5);
            var vel=flat.scale(.25+level.random.nextDouble()*.35).add(rnd.scale(.45));
            var state=flesh[i%7==6?3:level.random.nextInt(3)];
            com.codex.blacklight.movement.LandingDebrisSystem.fragment(level,c.add(rnd.scale(size*.4)),state,vel,(float)(.12+level.random.nextDouble()*.16*size),26+level.random.nextInt(14));
        }
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,Blocks.REDSTONE_BLOCK.defaultBlockState()),c.x,c.y,c.z,(int)(30*size),size*.3,size*.35,size*.3,.25);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,Blocks.NETHER_WART_BLOCK.defaultBlockState()),c.x,c.y,c.z,(int)(20*size),size*.25,size*.3,size*.25,.2);
        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR,c.x,c.y,c.z,6,.3,.3,.3,.15);
        level.playSound(null,c.x,c.y,c.z,SoundEvents.SLIME_SQUISH,SoundSource.HOSTILE,1.2F,.55F);
        level.playSound(null,c.x,c.y,c.z,SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR,SoundSource.HOSTILE,.7F,.8F);
        // Loot and death events already ran; only the collapsing body is hidden.
        v.setInvisible(true);
        return true;
    }
    /** Prototype's dismember: a heavy killing blow that does not burst the body tears off the struck limb.
     * Purely visual: the client hides the part on the corpse and throws it along the blow. */
    private static void dismember(ServerPlayer p,LivingEntity v,WeaponMove move,Vec3 direction){
        if(!(v.level() instanceof ServerLevel level)||v instanceof Player||CombatTargeting.multipart(v))return;
        if(v.getBbHeight()<1.3F||v.getBbHeight()>2.7F||v.getBbWidth()>1.1F)return; // humanoid-sized rigs only
        var flat=new Vec3(direction.x,0,direction.z);if(flat.lengthSqr()<1e-4)flat=Vec3.directionFromRotation(0,p.getYRot());flat=flat.normalize();
        // Full charge and finishers burst the body before this point, so this is a heavy blow
        // on a sturdier body: launchers and rising blows take the head, others the arm on the struck side.
        int mask;
        var kind=ImpactProfile.of(move).kind();
        if(kind==HitReaction.LAUNCH||kind==HitReaction.SLAM)mask=DISMEMBER_HEAD;
        else{
            var facing=Vec3.directionFromRotation(0,v.getYRot());
            double side=facing.x*flat.z-facing.z*flat.x;
            mask=side>0?DISMEMBER_LEFT_ARM:DISMEMBER_RIGHT_ARM;
        }
        com.codex.blacklight.registry.ModAttachments.setData(v,com.codex.blacklight.registry.ModAttachments.DISMEMBER,mask);com.codex.blacklight.registry.ModAttachments.syncData(v,com.codex.blacklight.registry.ModAttachments.DISMEMBER);
        var at=v.position().add(0,v.getBbHeight()*(mask==DISMEMBER_HEAD?.88:.7),0);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,Blocks.REDSTONE_BLOCK.defaultBlockState()),at.x,at.y,at.z,24,.12,.12,.12,.18);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,Blocks.NETHER_WART_BLOCK.defaultBlockState()),at.x,at.y,at.z,12,.1,.1,.1,.12);
        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR,at.x,at.y,at.z,3,.15,.15,.15,.1);
        for(int i=0;i<3;i++){
            var vel=flat.scale(.2+level.random.nextDouble()*.15).add((level.random.nextDouble()-.5)*.25,.2+level.random.nextDouble()*.2,(level.random.nextDouble()-.5)*.25);
            com.codex.blacklight.movement.LandingDebrisSystem.fragment(level,at,Blocks.NETHER_WART_BLOCK.defaultBlockState(),vel,.1F+.04F*i,22);
        }
        level.playSound(null,at.x,at.y,at.z,SoundEvents.SLIME_SQUISH,SoundSource.HOSTILE,1F,.7F);
        level.playSound(null,at.x,at.y,at.z,SoundEvents.PLAYER_ATTACK_CRIT,SoundSource.HOSTILE,.8F,.6F);
    }
    public static final int DISMEMBER_HEAD=1,DISMEMBER_RIGHT_ARM=2,DISMEMBER_LEFT_ARM=4;
}
