package com.codex.blacklight.entity;

import com.codex.blacklight.infection.BlacklightInfectionSystem;
import com.codex.blacklight.military.*;
import com.codex.blacklight.disguise.DisguiseSystem;
import com.codex.blacklight.combat.ConsumeControl;
import com.codex.blacklight.combat.ConsumeVisual;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import java.util.EnumSet;
import com.codex.blacklight.firearms.*;
import net.minecraft.world.item.*;

/** A visible burst / reload cycle gives the player room to close, grab and consume. */
public abstract class MilitarySoldier extends Monster {
    private static final EntityDataAccessor<Integer> PHASE=SynchedEntityData.defineId(MilitarySoldier.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> RANK=SynchedEntityData.defineId(MilitarySoldier.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> EQUIPPED=SynchedEntityData.defineId(MilitarySoldier.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> COMMAND_ROLE=SynchedEntityData.defineId(MilitarySoldier.class,EntityDataSerializers.INT);
    private final MilitaryRole entityDefaultRole;
    public MilitaryRole commandRole(){int ordinal=entityData.get(COMMAND_ROLE);return ordinal>=0&&ordinal<MilitaryRole.values().length?MilitaryRole.values()[ordinal]:MilitaryRole.TROOPER;}
    /** Identity changes neither reissue equipment nor change finite ammunition. */
    public void setCommandRole(MilitaryRole role){
        if(level().isClientSide)return;
        var chosen=role==null?MilitaryRole.TROOPER:role;entityData.set(COMMAND_ROLE,chosen.ordinal());
        if(rank().ordinal()<chosen.defaultRank().ordinal())setRank(chosen.defaultRank());
        MilitaryOrganizationSystem.roleChanged(this);
    }
    private ItemStack stowedWeapon=ItemStack.EMPTY;
    private boolean weaponInitialized;
    private int ammunitionReserve;
    public boolean equipped(){return entityData.get(EQUIPPED);}
    /** Move the actual stack; toggling never copies a weapon or refills ammunition. */
    public void setEquipped(boolean value){
        if(level().isClientSide||equipped()==value)return;
        entityData.set(EQUIPPED,value);
        if(!value){stowedWeapon=getMainHandItem();setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);suspendTactics();}
        else if(!stowedWeapon.isEmpty()){setItemSlot(EquipmentSlot.MAINHAND,stowedWeapon);stowedWeapon=ItemStack.EMPTY;}
        updateEquipmentProtection();
    }
    private void updateEquipmentProtection(){
        var armour=getAttribute(Attributes.ARMOR);if(armour!=null)armour.setBaseValue(equipped()?profile().armour():0);
        var knockback=getAttribute(Attributes.KNOCKBACK_RESISTANCE);if(knockback!=null)knockback.setBaseValue(equipped()?profile().knockbackResistance():0);
    }
    private ItemStack ammunitionWeapon(){return equipped()?getMainHandItem():stowedWeapon;}
    private WeaponDefinition weaponDefinition(){return ammunitionWeapon().getItem() instanceof FirearmItem gun?gun.definition():defaultWeapon();}
    private WeaponDefinition defaultWeapon(){return commandRole()==MilitaryRole.OFFICER||commandRole()==MilitaryRole.COMMANDER?WeaponCatalog.CARBINE:heavy()?WeaponCatalog.MACHINE_GUN:WeaponCatalog.RIFLE;}
    /** One-time legacy/new spawn loadout. It never runs again when the gun is lost or empty. */
    public void initializeWeapon(){
        if(level().isClientSide||weaponInitialized||!equipped())return;
        weaponInitialized=true;
        if(getMainHandItem().isEmpty()){
            var d=weaponDefinition();ItemStack gun=new ItemStack(FirearmsRegistry.weapon(d.id()));
            FirearmItem.store(gun,new AmmoState(d.capacity(),0,0));setItemSlot(EquipmentSlot.MAINHAND,gun);
            ammunitionReserve=d.capacity()*3;
        }
    }
    public Item requiredAmmoItem(){return FirearmsRegistry.ammo(weaponDefinition().ammoId());}
    public int ammoDeficit(){return Math.max(0,weaponDefinition().capacity()*3-ammunitionReserve);}
    public int reserveAmmo(){return ammunitionReserve;}
    /** Caller must debit precisely the returned amount from its finite supply inventory. */
    public int receiveAmmo(int offered){
        if(level().isClientSide)return 0;
        int accepted=Math.min(Math.max(0,offered),ammoDeficit());ammunitionReserve+=accepted;return accepted;
    }
    public int magazineAmmo(){return FirearmItem.state(ammunitionWeapon(),ammunitionReserve).magazine();}
    private boolean readyWeapon(){
        if(!equipped()||!(getMainHandItem().getItem() instanceof FirearmItem))return false;
        var d=weaponDefinition();long now=level().getGameTime();var state=FirearmItem.state(getMainHandItem(),ammunitionReserve);
        if(state.magazine()==0){var reloaded=state.reload(d,now);FirearmItem.store(getMainHandItem(),reloaded);ammunitionReserve=reloaded.reserve();return false;}
        return state.canFire(now);
    }
    private java.util.UUID unitId;
    private boolean automaticUnitJoin=true;
    private String deployment="";
    private Vec3 deploymentOffset=Vec3.ZERO;
    private int deploymentCapacity=8;
    public String deployment(){return deployment;}
    public int deploymentCapacity(){return deploymentCapacity;}
    public MilitaryOrder.Point deploymentOrigin(){
        double angle=Math.toRadians(getYRot()-180),cos=Math.cos(angle),sin=Math.sin(angle);
        return new MilitaryOrder.Point(Math.rint((getX()+deploymentOffset.x*cos-deploymentOffset.z*sin)*2)/2,
                Math.rint((getY()+deploymentOffset.y)*2)/2,Math.rint((getZ()+deploymentOffset.x*sin+deploymentOffset.z*cos)*2)/2);
    }
    public void finishDeployment(){deployment="";deploymentOffset=Vec3.ZERO;}
    public abstract MilitaryFaction faction();
    /** Entity.getName still gives an explicit CustomName priority over this role-dependent default. */
    @Override protected net.minecraft.network.chat.Component getTypeName(){
        String suffix=commandRole()==MilitaryRole.TROOPER?(heavy()?"heavy":"soldier"):commandRole().id();
        return net.minecraft.network.chat.Component.translatable("entity.blacklight."+faction().id()+"_"+suffix);
    }
    public MilitaryRank rank() { int ordinal=entityData.get(RANK); return ordinal>=0&&ordinal<MilitaryRank.values().length?MilitaryRank.values()[ordinal]:MilitaryRank.PRIVATE; }
    public void setRank(MilitaryRank rank) { entityData.set(RANK,(rank==null?MilitaryRank.PRIVATE:rank).ordinal()); }
    public java.util.UUID unitId() { return unitId; }
    public void setUnitId(java.util.UUID id) { unitId=id; }
    public boolean automaticUnitJoin(){return automaticUnitJoin;}
    public void setAutomaticUnitJoin(boolean value){automaticUnitJoin=value;}
    public MilitaryCombatProfile profile() { return MilitaryCombatProfile.of(faction(),heavy()); }
    private BlockPos home;
    private FireTeamGoal fireTeamGoal;
    private String homeDimension = "";
    public MilitarySoldier(EntityType<? extends MilitarySoldier> type,Level level){this(type,level,MilitaryRole.TROOPER);}
    protected MilitarySoldier(EntityType<? extends MilitarySoldier> type,Level level,MilitaryRole role){
        super(type,level);xpReward=10;entityDefaultRole=role==null?MilitaryRole.TROOPER:role;
        entityData.set(COMMAND_ROLE,entityDefaultRole.ordinal());setRank(entityDefaultRole.defaultRank());
    }
    @Override protected net.minecraft.world.entity.ai.navigation.PathNavigation createNavigation(Level level){return new MilitaryGroundNavigation(this,level);}
    public boolean heavy(){return getType()==com.codex.blacklight.registry.ModEntities.BLACKWATCH_HEAVY.get()||getType()==com.codex.blacklight.registry.ModEntities.MILITARY_HEAVY.get();}
    public int phase(){return entityData.get(PHASE);}
    @Override protected void defineSynchedData(){super.defineSynchedData();entityData.define(PHASE,0);entityData.define(RANK,0);entityData.define(EQUIPPED,true);entityData.define(COMMAND_ROLE,0);}
    public static AttributeSupplier.Builder attributes(boolean heavy){return attributes(MilitaryFaction.BLACKWATCH,heavy);}
    public static AttributeSupplier.Builder attributes(MilitaryFaction faction,boolean heavy){
        var p=MilitaryCombatProfile.of(faction,heavy);
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,p.health()).add(Attributes.MOVEMENT_SPEED,p.speed())
                .add(Attributes.ARMOR,p.armour()).add(Attributes.ATTACK_DAMAGE,heavy?4:2).add(Attributes.FOLLOW_RANGE,28)
                .add(Attributes.KNOCKBACK_RESISTANCE,p.knockbackResistance());
    }
    @Override protected void registerGoals(){
        goalSelector.addGoal(0,new FloatGoal(this));fireTeamGoal=new FireTeamGoal();goalSelector.addGoal(2,fireTeamGoal);
        goalSelector.addGoal(6,new PatrolGoal());
        goalSelector.addGoal(7,new LookAtPlayerGoal(this,Player.class,10));goalSelector.addGoal(8,new RandomLookAroundGoal(this));
        targetSelector.addGoal(1,new HurtByTargetGoal(this,MilitarySoldier.class).setAlertOthers());
        targetSelector.addGoal(2,new NearestAttackableTargetGoal<>(this,LivingEntity.class,10,true,false,this::enemy));
    }
    private boolean enemy(LivingEntity target){
        return validTarget(target) && BlacklightInfectionSystem.isInfected(target);
    }
    public boolean validTarget(LivingEntity target) {
        return target != null && target.isAlive() && !target.isRemoved() && target != this
                && target.level() == level() && !isAlliedTo(target)
                && (!(target instanceof Player p) || !p.isCreative() && !p.isSpectator() && !DisguiseSystem.fooled(this, p));
    }
    public boolean tacticsAvailable() {
        return isAlive() && !isRemoved() && !isNoAi() && !isPassenger()
                && !com.codex.blacklight.combat.ImpactControl.stunned(this)
                && !BlacklightInfectionSystem.isInfected(this)
                && !ModAttachments.peekData(this, ModAttachments.CONSUME_VISUAL).active()
                && !ConsumeControl.ownsMotion(this);
    }
    public BlockPos home() {
        String dimension = level().dimension().location().toString();
        if (home == null || !dimension.equals(homeDimension)) { home = blockPosition().immutable(); homeDimension = dimension; }
        return home;
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("MilitaryEquipped",equipped());tag.putBoolean("MilitaryWeaponInitialized",weaponInitialized);
        tag.putInt("MilitaryAmmoReserve",ammunitionReserve);
        if(!stowedWeapon.isEmpty())tag.put("MilitaryStowedWeapon",stowedWeapon.save(new CompoundTag()));
        tag.putString("MilitaryRole",commandRole().id());
        tag.putString("MilitaryRank",rank().id()); if(unitId!=null)tag.putUUID("MilitaryUnit",unitId);
        tag.putBoolean("MilitaryAutomaticJoin",automaticUnitJoin);
        if(!deployment.isEmpty()){tag.putString("MilitaryDeployment",deployment);tag.putInt("MilitaryDeploymentCapacity",deploymentCapacity);tag.putDouble("MilitaryDeploymentX",deploymentOffset.x);tag.putDouble("MilitaryDeploymentY",deploymentOffset.y);tag.putDouble("MilitaryDeploymentZ",deploymentOffset.z);}
        tag.putLong("BlackwatchHome", home().asLong()); tag.putString("BlackwatchHomeDimension", homeDimension);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(EQUIPPED,!tag.contains("MilitaryEquipped")||tag.getBoolean("MilitaryEquipped"));
        weaponInitialized=tag.getBoolean("MilitaryWeaponInitialized");
        ammunitionReserve=Math.max(0,Math.min(4096,tag.getInt("MilitaryAmmoReserve")));
        stowedWeapon=tag.contains("MilitaryStowedWeapon")?ItemStack.of(tag.getCompound("MilitaryStowedWeapon")):ItemStack.EMPTY;
        if(!equipped()&&!getMainHandItem().isEmpty()){
            if(stowedWeapon.isEmpty())stowedWeapon=getMainHandItem();setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);
        }
        updateEquipmentProtection();
        setRank(tag.contains("MilitaryRank")?MilitaryRank.fromId(tag.getString("MilitaryRank")):entityDefaultRole.defaultRank());
        var savedRole=MilitaryRole.parse(tag.getString("MilitaryRole"));
        var loadedRole=savedRole.orElseGet(()->MilitaryRole.migrate(rank(),entityDefaultRole));
        entityData.set(COMMAND_ROLE,loadedRole.ordinal());
        if(savedRole.isEmpty()&&rank().ordinal()<loadedRole.defaultRank().ordinal())setRank(loadedRole.defaultRank());
        unitId=tag.hasUUID("MilitaryUnit")?tag.getUUID("MilitaryUnit"):null;
        automaticUnitJoin=!tag.contains("MilitaryAutomaticJoin")||tag.getBoolean("MilitaryAutomaticJoin");
        deployment=tag.getString("MilitaryDeployment");deploymentCapacity=Math.max(1,Math.min(32,tag.getInt("MilitaryDeploymentCapacity")));
        deploymentOffset=new Vec3(tag.getDouble("MilitaryDeploymentX"),tag.getDouble("MilitaryDeploymentY"),tag.getDouble("MilitaryDeploymentZ"));
        if(!Double.isFinite(deploymentOffset.lengthSqr())||deploymentOffset.lengthSqr()>64*64)finishDeployment();
        home = tag.contains("BlackwatchHome", 4) ? BlockPos.of(tag.getLong("BlackwatchHome")) : null;
        homeDimension = tag.getString("BlackwatchHomeDimension");
    }
    @Override public void tick() {
        if (!level().isClientSide) {
            initializeWeapon();
            home();
            com.codex.blacklight.military.MilitaryOrganizationSystem.ensureMember(this);
            if (!tacticsAvailable()) suspendTactics();
            else BlackwatchSquadSystem.tick(this);
        }
        super.tick();
    }
    /** Control can begin between entity ticks; clear both the visible and internal gun cycle now. */
    public void suspendTactics() {
        if (level().isClientSide) return;
        if (fireTeamGoal != null) fireTeamGoal.interrupt();
        entityData.set(PHASE, 0); getNavigation().stop(); setTarget(null);
        BlackwatchSquadSystem.release(this); BlackwatchSquadSystem.tick(this);
    }
    @Override public boolean isAlliedTo(Entity other){return other instanceof MilitarySoldier&&!BlacklightInfectionSystem.isInfected((LivingEntity)other)&&!BlacklightInfectionSystem.isInfected(this)||super.isAlliedTo(other);}
    @Override public boolean removeWhenFarAway(double distance){return !isPersistenceRequired();}
    private final class PatrolGoal extends Goal {
        PatrolGoal() { setFlags(EnumSet.of(Flag.MOVE)); }
        @Override public boolean canUse() { return tacticsAvailable() && getTarget() == null; }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void tick() { BlackwatchSquadSystem.patrol(MilitarySoldier.this); }
        @Override public void stop() { getNavigation().stop(); }
    }
    private final class FireTeamGoal extends Goal {
        private long burstAt = -1, readyAt;
        FireTeamGoal(){setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
        @Override public boolean canUse(){return equipped() && tacticsAvailable() && validTarget(getTarget());}
        @Override public void stop(){interrupt();entityData.set(PHASE,0);getNavigation().stop();}
        @Override public boolean requiresUpdateEveryTick(){return true;}
        @Override public void tick(){
            var target=getTarget();if(target==null)return;
            if(!readyWeapon()){entityData.set(PHASE,3);return;}
            if (!tacticsAvailable() || !validTarget(target) || !BlackwatchSquadSystem.withinTerritory(MilitarySoldier.this, position())) {
                setTarget(null); stop(); return;
            }
            long now = level().getGameTime();
            double distance=distanceToSqr(target);boolean sight=hasLineOfSight(target);
            var contact = BlackwatchSquadSystem.contact(MilitarySoldier.this);
            if (!sight || distance > 22 * 22) {
                interrupt(); entityData.set(PHASE, now < readyAt ? 3 : 0);
                if (contact.isPresent()) {
                    var point = BlackwatchSquadSystem.vector(contact.get().position());
                    getLookControl().setLookAt(point.x, point.y + 1, point.z, 30, 30);
                    BlackwatchSquadSystem.navigate(MilitarySoldier.this, point, 1);
                } else { setTarget(null); getNavigation().stop(); }
                return;
            }
            if (!BlackwatchSquadSystem.withinTerritory(MilitarySoldier.this, target.position())) { setTarget(null); stop(); return; }
            getLookControl().setLookAt(target,30,30);
            if (distance < 5 * 5) BlackwatchSquadSystem.reposition(MilitarySoldier.this, target);
            else if (distance > 14 * 14 && contact.isPresent())
                BlackwatchSquadSystem.navigate(MilitarySoldier.this, BlackwatchSquadSystem.vector(contact.get().position()), .8);
            else if (burstAt >= 0 && now - burstAt < profile().burstTicks()) getNavigation().stop();
            var squad = BlackwatchSquadSystem.squad(MilitarySoldier.this);
            if (burstAt >= 0 && now >= readyAt) {
                burstAt = -1; BlackwatchSquadSystem.release(MilitarySoldier.this);
            }
            if (burstAt >= 0) {
                int age = (int) (now - burstAt);
                if (age < profile().burstTicks() && (!squad.hasFirePermit(getUUID(), now) || !clearShot(target))) {
                    interrupt(); entityData.set(PHASE, now < readyAt ? 3 : 0); return;
                }
                if (age >= profile().burstTicks()) { BlackwatchSquadSystem.release(MilitarySoldier.this); entityData.set(PHASE, 3); }
                else if (age < profile().aimTicks()) entityData.set(PHASE, 1);
                else { entityData.set(PHASE, 2); if (age == profile().aimTicks() || age == profile().aimTicks()+8 || age == profile().aimTicks()+16) fire(target); }
                if (age >= profile().burstTicks()) BlackwatchSquadSystem.reposition(MilitarySoldier.this, target);
                return;
            }
            if (now < readyAt) { entityData.set(PHASE, 3); return; }
            if (!clearShot(target)) { BlackwatchSquadSystem.release(MilitarySoldier.this); entityData.set(PHASE, 0); return; }
            if (squad.requestFire(getUUID(), now, profile().burstTicks())) {
                burstAt = now; readyAt = now + profile().cycleTicks(); entityData.set(PHASE, 1);
                if (distance >= 5 * 5) getNavigation().stop();
            } else {
                entityData.set(PHASE, 0);
                BlackwatchSquadSystem.reposition(MilitarySoldier.this, target);
            }
        }
        private void interrupt() {
            long now = level().getGameTime();
            if (burstAt >= 0 && now - burstAt < profile().aimTicks()) readyAt = now;
            burstAt = -1; BlackwatchSquadSystem.release(MilitarySoldier.this);
        }
    }
    private boolean clearShot(LivingEntity target) {
        Vec3 start = getEyePosition().add(0, -.18, 0), end = target.getBoundingBox().getCenter();
        var wall = level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (wall.getType() != HitResult.Type.MISS) return false;
        var hit = ProjectileUtil.getEntityHitResult(level(), this, start, end, new AABB(start, end).inflate(.3),
                entity -> entity != this && entity instanceof LivingEntity living && living.isAlive() && entity.isPickable());
        return hit == null || !isAlliedTo(hit.getEntity());
    }
    private void fire(LivingEntity target){
        if(!(level() instanceof ServerLevel level) || !tacticsAvailable() || !validTarget(target)
                || !hasLineOfSight(target) || !clearShot(target)) return;
        if(!readyWeapon())return;
        var d=weaponDefinition();var state=FirearmItem.state(getMainHandItem(),ammunitionReserve);
        FirearmItem.store(getMainHandItem(),state.fire(d,level.getGameTime()));
        Vec3 start=getEyePosition().add(0,-.18,0);
        Vec3 aim=target.getBoundingBox().getCenter().subtract(start).normalize();
        aim=aim.add((random.nextDouble()-.5)*.035,(random.nextDouble()-.5)*.025,(random.nextDouble()-.5)*.035).normalize();
        FirearmsCombat.fire(this,d,aim,profile().damage()/d.damage());
    }
}
