package com.codex.blacklight.infection;

import com.codex.blacklight.entity.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.entity.projectile.windcharge.BreezeWindCharge;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import org.joml.Vector3f;

/** Real server abilities, finite recovery, physical projectiles and synchronized action clocks.
 * Detached appearance mobs are never ticked as server AI and cannot spawn unowned attacks. */
public final class NativeInfectedCombat {
    private final BlacklightInfectedHost host;
    private int cooldown=12,movementCooldown,sequence;
    private boolean contacted;
    public NativeInfectedCombat(BlacklightInfectedHost host){this.host=host;}
    public NativeInfectedProfile profile(){return NativeInfectedProfile.saved(host.speciesId(),host.hostData().getString("NativeCapability"));}
    public CompoundTag save(){var tag=new CompoundTag();tag.putInt("Cooldown",Math.max(cooldown,host.nativeAction().isEmpty()?0:profile().cooldown));tag.putInt("Sequence",sequence);return tag;}
    public void load(CompoundTag tag){cooldown=Math.clamp(tag.getInt("Cooldown"),12,200);sequence=Math.max(0,tag.getInt("Sequence"));contacted=false;host.setNativeAction("",null);}
    public void interrupt(){if(!host.nativeAction().isEmpty()){host.setNativeAction("",null);cooldown=Math.max(cooldown,20);}contacted=false;}
    public static boolean enemy(BlacklightInfectedHost host,LivingEntity e){return e!=host&&e.isAlive()&&!e.isRemoved()&&e.level()==host.level()&&!host.isAlliedTo(e)
            &&!(e instanceof Player p&&(p.isCreative()||p.isSpectator()))
            &&(!(e instanceof Mob m)||!BlacklightInfectionSystem.isInfected(m)||host.getTarget()==e||TissueCompetition.contender(host,e));}
    public boolean ownsGroundCombat(){return !host.isEvolved()&&profile().ranged()&&profile()!=NativeInfectedProfile.SONIC;}
    public boolean tick(){
        if(host.level().isClientSide)return false;
        if(cooldown>0)cooldown--;if(movementCooldown>0)movementCooldown--;
        if(host.isEvolved()||host.isMetamorphosing()||!host.isAlive()||host.isNoAi()||host.isPassenger()
                ||host.level().getDifficulty()==net.minecraft.world.Difficulty.PEACEFUL){interrupt();return false;}
        var target=host.getTarget();var kind=profile();
        if(target==null||!enemy(host,target)){interrupt();return false;}
        if(kind==NativeInfectedProfile.SONIC&&host.tickCount%80==0)net.minecraft.world.entity.monster.warden.Warden.applyDarknessAround((ServerLevel)host.level(),host.position(),host,16);
        if(kind==NativeInfectedProfile.WITHER&&host.tickCount%40==0)host.heal(1);
        if(!host.nativeAction().isEmpty()){
            float age=host.nativeActionElapsed(0);
            if(target.getId()!=host.nativeActionTarget()||age>=kind.duration||host.distanceToSqr(target)>Math.pow(kind.range+12,2)){
                interrupt();cooldown=kind.cooldown;return true;
            }
            host.getNavigation().stop();host.setXxa(0);host.setZza(0);face(target);
            brace(target);
            // Sonic waves intentionally travel through walls, like the native Warden.
            if(kind!=NativeInfectedProfile.SONIC&&!host.hasLineOfSight(target)){interrupt();return true;}
            if(age>=kind.contact&&!contacted){contacted=true;release(kind,target);}
            return true;
        }
        movement(target);
        if(kind==NativeInfectedProfile.MELEE)return false;
        // A restored ranged weapon must not starve acquired close-combat organs.
        // Native recovery is still counted while an organ owns the action.
        if(kind.ranged()&&cooldown>0&&host.distanceToSqr(target)<36&&host.organActionCore()==null&&host.tissueStrikeElapsed(0)<0&&host.organAbilities().tryStart(target))return false;
        if(host.organActionCore()!=null||host.tissueStrikeElapsed(0)>=0)return false;
        double distance=host.distanceTo(target),minimum=kind==NativeInfectedProfile.SONIC?4:0;
        if(cooldown==0&&distance<=kind.range+Math.min(8,host.getBbWidth())&&distance>=minimum
                &&(kind==NativeInfectedProfile.SONIC||host.hasLineOfSight(target))){
            contacted=false;host.setNativeAction(kind.name(),target);host.getNavigation().stop();face(target);
            sound(kind==NativeInfectedProfile.SONIC?SoundEvents.WARDEN_SONIC_CHARGE:kind==NativeInfectedProfile.WITHER?SoundEvents.WITHER_AMBIENT:SoundEvents.EVOKER_PREPARE_ATTACK,.75F,.7F);
            return true;
        }
        if(kind.ranged()){
            face(target);
            if(!host.hasLineOfSight(target)||distance>kind.range*.82){
                if(host.family().equals("ground"))host.getNavigation().moveTo(target,1.15);
                else steer(target,false);
            }else{
                host.getNavigation().stop();
                if(!host.family().equals("ground"))steer(target,true);
                else if(distance<3&&kind!=NativeInfectedProfile.SONIC)host.setDeltaMovement(host.getDeltaMovement().add(horizontal(target).scale(-.035)));
            }
            return true;
        }return false;
    }
    private void face(LivingEntity target){host.getLookControl().setLookAt(target,25,25);var d=target.position().subtract(host.position());float yaw=(float)(Math.atan2(-d.x,d.z)*Mth.RAD_TO_DEG);host.setYRot(Mth.approachDegrees(host.getYRot(),yaw,14));host.yBodyRot=host.getYRot();}
    private Vec3 horizontal(LivingEntity target){return target.position().subtract(host.position()).multiply(1,0,1).normalize();}
    private void brace(LivingEntity target){
        host.setDeltaMovement(host.getDeltaMovement().multiply(.6,host.family().equals("ground")?1:.6,.6));
        if(host.family().equals("air"))steer(target,true);
    }
    private void steer(LivingEntity target,boolean orbit){
        var d=target.getBoundingBox().getCenter().subtract(host.getBoundingBox().getCenter());
        if(host.family().equals("air")){
            double preferred=host.speciesId().equals("minecraft:ender_dragon")?10:4;
            var h=horizontal(target);double sign=(host.getUUID().getLeastSignificantBits()&1)==0?1:-1;
            var acceleration=orbit?new Vec3(h.z*sign*.025,Mth.clamp((target.getY()+preferred-host.getY())*.008,-.04,.04),-h.x*sign*.025):d.normalize().scale(.06).add(0,.015,0);
            var next=host.getDeltaMovement().scale(.90).add(acceleration);
            if(host.horizontalCollision)next=next.add(0,.1,0);
            host.setDeltaMovement(limit(next,.55));
        }else if(host.isInWater())host.setDeltaMovement(limit(host.getDeltaMovement().scale(.8).add(d.normalize().scale(orbit?.015:.065)),.4));
    }
    private static Vec3 limit(Vec3 v,double max){return v.lengthSqr()>max*max?v.normalize().scale(max):v;}
    private void movement(LivingEntity target){
        var id=host.speciesId();
        if((NativeInfectedProfile.climbs(id)||host.hostData().getBoolean("NativeClimbs"))&&host.horizontalCollision){host.setDeltaMovement(host.getDeltaMovement().x,.20,host.getDeltaMovement().z);host.fallDistance=0;}
        if((NativeInfectedProfile.hops(id)||host.hostData().getBoolean("NativeHops"))&&host.onGround()&&movementCooldown==0&&host.hasLineOfSight(target)){
            host.setDeltaMovement(horizontal(target).scale(id.equals("minecraft:breeze")?.55:.28).add(0,id.equals("minecraft:breeze")?.65:.45,0));host.hurtMarked=true;movementCooldown=18;
        }
        if(id.equals("minecraft:enderman")&&movementCooldown==0&&host.distanceToSqr(target)>100){
            var goal=target.position().subtract(horizontal(target).scale(3));
            for(int y=2;y>=-2;y--)if(host.randomTeleport(goal.x,goal.y+y,goal.z,true)){movementCooldown=90;sound(SoundEvents.ENDERMAN_TELEPORT,.7F,.8F);break;}
            movementCooldown=Math.max(movementCooldown,25);
        }
        if(NativeInfectedProfile.swoops(id)&&host.family().equals("air")){
            host.getNavigation().stop();var d=target.getBoundingBox().getCenter().subtract(host.getBoundingBox().getCenter());
            int phase=Math.floorMod(host.tickCount+host.getId()*13,100);boolean dive=phase>=52&&phase<82;
            var h=horizontal(target);var wish=dive?d.normalize().scale(.14):new Vec3(h.z*.045,Mth.clamp((target.getY()+5-host.getY())*.012,-.06,.08),-h.x*.045);
            host.setDeltaMovement(limit(host.getDeltaMovement().scale(.88).add(wish),dive?.9:.42));face(target);
        }
    }
    private Vec3 origin(){
        if(host.bossProfile()!=null)return host.position().add(0,host.bossProfile().visualHeight()*host.bodyScale()*.77,0);
        return host.position().add(0,host.getBbHeight()*.7,0);
    }
    private float damage(float factor){return (float)Math.clamp(host.getAttributeValue(Attributes.ATTACK_DAMAGE)*factor,1,1000000);}
    private void release(NativeInfectedProfile kind,LivingEntity target){
        if(!(host.level() instanceof ServerLevel level))return;
        var from=origin();var aim=target.getBoundingBox().getCenter().subtract(from);
        switch(kind){
            case BOW,CROSSBOW->{
                var arrow=new Arrow(level,host,new ItemStack(Items.ARROW),null);arrow.setPos(from);arrow.pickup=AbstractArrow.Pickup.DISALLOWED;arrow.setBaseDamage(damage(.55F));
                if(host.speciesId().equals("minecraft:stray"))arrow.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN,100));
                if(host.speciesId().equals("minecraft:bogged"))arrow.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON,70));
                arrow.shoot(aim.x,aim.y+Math.sqrt(aim.horizontalDistanceSqr())*.12,aim.z,1.65F,1);level.addFreshEntity(arrow);sound(SoundEvents.SKELETON_SHOOT,1,.75F);
            }
            case BLAZE,GHAST,WITHER,DRAGON->{
                // Keep recognisable native weapons; flesh is an additional infection layer.
                Vec3 direction=aim.normalize();
                Projectile nativeShot=switch(kind){
                    case BLAZE->new SmallFireball(level,host,direction);
                    case GHAST->new LargeFireball(level,host,direction,2);
                    case WITHER->new WitherSkull(level,host,direction);
                    default->new DragonFireball(level,host,direction);
                };
                nativeShot.setPos(from.add(direction.scale(Math.max(.8,host.getBbWidth()*.6))));level.addFreshEntity(nativeShot);
                int count=kind==NativeInfectedProfile.WITHER?(host.getHealth()<host.getMaxHealth()*.5?3:1):kind==NativeInfectedProfile.DRAGON?2:sequence%2==0?1:0;
                for(int i=0;i<count;i++){
                    var right=horizontal(target).cross(new Vec3(0,1,0));
                    var muzzle=from.add(right.scale((i-(count-1)/2D)*(kind==NativeInfectedProfile.WITHER?Math.max(.6,host.getBbWidth()*.6):.3)));
                    var tumor=new InfectedTumor(level,host);tumor.configure(damage(kind==NativeInfectedProfile.WITHER?1.35F:kind==NativeInfectedProfile.DRAGON?1.8F:.9F),kind==NativeInfectedProfile.WITHER||kind==NativeInfectedProfile.DRAGON?5.5F:3.3F);
                    muzzle=muzzle.add(aim.normalize().scale(Math.max(.8,host.getBbWidth()*.6)));
                    tumor.setPos(muzzle);var a=target.getBoundingBox().getCenter().add(target.getDeltaMovement().scale(5)).subtract(muzzle);tumor.shoot(a.x,a.y,a.z,kind==NativeInfectedProfile.WITHER?1.0F:.8F,kind==NativeInfectedProfile.BLAZE?2:0);level.addFreshEntity(tumor);
                }sound(kind==NativeInfectedProfile.WITHER?SoundEvents.WITHER_SHOOT:SoundEvents.GHAST_SHOOT,1.3F,.65F);
            }
            case SONIC,GUARDIAN->{
                var to=target.getBoundingBox().getCenter();var delta=to.subtract(from);int count=Math.min(48,(int)delta.length()+2);
                for(int i=0;i<count;i++){var p=from.lerp(to,i/(double)Math.max(1,count-1));level.sendParticles(kind==NativeInfectedProfile.SONIC?ParticleTypes.SONIC_BOOM:ParticleTypes.BUBBLE,p.x,p.y,p.z,1,0,0,0,0);}
                if(target.hurt(kind==NativeInfectedProfile.SONIC?host.damageSources().sonicBoom(host):host.damageSources().indirectMagic(host,host),damage(kind==NativeInfectedProfile.SONIC?1.6F:1.4F))){
                    EvolvedCombatEffects.impact(host,target);var d=horizontal(target);target.push(d.x*.65,.2,d.z*.65);target.hurtMarked=true;
                }
                sound(kind==NativeInfectedProfile.SONIC?SoundEvents.WARDEN_SONIC_BOOM:SoundEvents.GUARDIAN_ATTACK,1.4F,.7F);
            }
            case FANGS->{
                var h=horizontal(target);for(int i=1;i<=8;i++){var p=host.position().add(h.scale(i*1.5));spawnFang(level,p,i*2);}
                for(int i=0;i<6;i++){double a=i*Math.PI/3;spawnFang(level,target.position().add(Math.cos(a)*1.7,0,Math.sin(a)*1.7),14+i);}
                sound(SoundEvents.EVOKER_CAST_SPELL,1,.6F);
            }
            case POTION->{var potion=new ThrownPotion(level,host);potion.setPos(from);var item=new ItemStack(Items.SPLASH_POTION);item.set(DataComponents.POTION_CONTENTS,new PotionContents(sequence%2==0?Potions.HARMING:Potions.SLOWNESS));potion.setItem(item);potion.shoot(aim.x,aim.y+Math.sqrt(aim.horizontalDistanceSqr())*.2,aim.z,.8F,1);level.addFreshEntity(potion);sound(SoundEvents.WITCH_THROW,.8F,.7F);}
            case SHULKER->{var bullet=new ShulkerBullet(level,host,target,Direction.Axis.Y);bullet.setPos(from);level.addFreshEntity(bullet);sound(SoundEvents.SHULKER_SHOOT,1,.8F);}
            case TRIDENT->{var trident=new ThrownTrident(level,host,new ItemStack(Items.TRIDENT));trident.setPos(from);trident.pickup=AbstractArrow.Pickup.DISALLOWED;trident.shoot(aim.x,aim.y+Math.sqrt(aim.horizontalDistanceSqr())*.1,aim.z,1.5F,1);level.addFreshEntity(trident);sound(SoundEvents.DROWNED_SHOOT,1,.8F);}
            case WIND->{var charge=new BreezeWindCharge(EntityType.BREEZE_WIND_CHARGE,level);charge.setOwner(host);charge.setPos(from);charge.shoot(aim.x,aim.y,aim.z,1.2F,0);level.addFreshEntity(charge);sound(SoundEvents.BREEZE_SHOOT,1,.8F);}
            case CREEPER->{var tumor=new InfectedTumor(level,host);tumor.configure(damage(1.8F),4);tumor.setPos(host.position().add(0,.2,0));level.addFreshEntity(tumor);tumor.burst();sound(SoundEvents.CREEPER_PRIMED,.9F,.65F);}
            default->{}
        }sequence++;
    }
    private void spawnFang(ServerLevel level,Vec3 at,int delay){
        for(int y=2;y>=-4;y--){var pos=BlockPos.containing(at).offset(0,y,0);if(!level.hasChunkAt(pos))return;
            var below=pos.below();if(level.getBlockState(below).isFaceSturdy(level,below,Direction.UP)&&level.getBlockState(pos).getCollisionShape(level,pos).isEmpty()){
                level.addFreshEntity(new EvokerFangs(level,at.x,pos.getY(),at.z,host.getYRot()*Mth.DEG_TO_RAD,delay,host));break;
            }
        }
    }
    private void sound(SoundEvent sound,float volume,float pitch){host.level().playSound(null,host.blockPosition(),sound,SoundSource.HOSTILE,volume,pitch);}
}
