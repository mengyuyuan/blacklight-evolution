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
    private ItemStack stowedWeapon=ItemStack.EMPTY,stowedSecondary=ItemStack.EMPTY;
    private boolean weaponInitialized;
    private MilitaryLoadoutInventory loadoutInventory=new MilitaryLoadoutInventory();
    public boolean equipped(){return entityData.get(EQUIPPED);}
    /** Move the actual stack; toggling never copies a weapon or refills ammunition. */
    public void setEquipped(boolean value){
        if(level().isClientSide||equipped()==value)return;
        entityData.set(EQUIPPED,value);
        if(!value){
            stowedWeapon=getMainHandItem();stowedSecondary=getOffhandItem();
            setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);setItemSlot(EquipmentSlot.OFFHAND,ItemStack.EMPTY);suspendTactics();
        }else{
            if(!stowedWeapon.isEmpty()){setItemSlot(EquipmentSlot.MAINHAND,stowedWeapon);stowedWeapon=ItemStack.EMPTY;}
            if(!stowedSecondary.isEmpty()){setItemSlot(EquipmentSlot.OFFHAND,stowedSecondary);stowedSecondary=ItemStack.EMPTY;}
        }
        updateEquipmentProtection();
    }
    private void updateEquipmentProtection(){
        var armour=getAttribute(Attributes.ARMOR);if(armour!=null)armour.setBaseValue(equipped()?profile().armour():0);
        var knockback=getAttribute(Attributes.KNOCKBACK_RESISTANCE);if(knockback!=null)knockback.setBaseValue(equipped()?profile().knockbackResistance():0);
    }
    private ItemStack ammunitionWeapon(){return equipped()?getMainHandItem():stowedWeapon;}
    private ItemStack secondaryWeapon(){return equipped()?getOffhandItem():stowedSecondary;}
    private static WeaponDefinition definition(ItemStack stack){return stack.getItem() instanceof FirearmItem gun?gun.definition():null;}
    private WeaponDefinition weaponDefinition(){var d=definition(ammunitionWeapon());return d==null?defaultWeapon():d;}
    private WeaponDefinition defaultWeapon(){return commandRole()==MilitaryRole.OFFICER||commandRole()==MilitaryRole.COMMANDER?WeaponCatalog.CARBINE:heavy()?WeaponCatalog.MACHINE_GUN:WeaponCatalog.RIFLE;}
    public boolean initializeNativeLoadout(String assignment){
        if(level().isClientSide||assignment==null||assignment.isBlank()||assignment.length()>256)return false;
        return initializeLoadout(MilitaryLoadoutProfile.forNative(getUUID(),assignment,faction(),heavy(),commandRole()),assignment);
    }
    /** Explicit finite issue for a new native slot or a newly assigned patrol; never a replenishment operation. */
    public boolean initializeLoadout(MilitaryLoadoutProfile loadout,String assignment){
        if(level().isClientSide||weaponInitialized||!equipped()||!getMainHandItem().isEmpty()||!getOffhandItem().isEmpty()
                ||!stowedWeapon.isEmpty()||!stowedSecondary.isEmpty()||!loadoutInventory.claimNative(getUUID(),assignment,loadout))return false;
        weaponInitialized=true;setItemSlot(EquipmentSlot.MAINHAND,issuedGun(loadout.primary()));
        if(loadout.secondary()!=null)setItemSlot(EquipmentSlot.OFFHAND,issuedGun(loadout.secondary()));
        return true;
    }
    private static ItemStack issuedGun(MilitaryLoadoutProfile.Gun issue){
        var stack=new ItemStack(FirearmsRegistry.weapon(issue.weaponId()));FirearmItem.store(stack,new AmmoState(issue.magazine(),0,0));return stack;
    }
    public int reserveAmmo(String ammunition){return loadoutInventory.reserve(ammunition);}
    /** Only a real carried weapon can accept its matching ammunition from a finite external inventory. */
    public int receiveAmmo(Item ammunition,int offered){
        if(level().isClientSide||offered<=0)return 0;
        for(ItemStack stack:java.util.List.of(ammunitionWeapon(),secondaryWeapon())){
            var d=definition(stack);if(d!=null&&FirearmsRegistry.ammo(d.ammoId())==ammunition){loadoutInventory.allow(d);return loadoutInventory.receive(d.ammoId(),offered);}
        }return 0;
    }
    /** The deterministic M5 default remains separate from explicit native weighted assignment. */
    public void initializeWeapon(){
        if(level().isClientSide||weaponInitialized||!equipped())return;
        weaponInitialized=true;
        if(getMainHandItem().isEmpty()&&getOffhandItem().isEmpty()){
            var d=defaultWeapon();ItemStack gun=new ItemStack(FirearmsRegistry.weapon(d.id()));
            FirearmItem.store(gun,new AmmoState(d.capacity(),0,0));setItemSlot(EquipmentSlot.MAINHAND,gun);
            loadoutInventory=MilitaryLoadoutInventory.legacy(d,d.capacity()*3);
        }else loadoutInventory=MilitaryLoadoutInventory.legacy(definition(ammunitionWeapon()),0);
    }
    private String supplyAmmo(){
        String first=null;
        for(ItemStack stack:java.util.List.of(ammunitionWeapon(),secondaryWeapon())){
            var d=definition(stack);if(d==null)continue;loadoutInventory.allow(d);if(first==null)first=d.ammoId();
            if(loadoutInventory.deficit(d.ammoId())>0)return d.ammoId();
        }return first;
    }
    public Item requiredAmmoItem(){String ammo=supplyAmmo();return FirearmsRegistry.ammo(ammo==null?defaultWeapon().ammoId():ammo);}
    public int ammoDeficit(){String ammo=supplyAmmo();return ammo==null?0:loadoutInventory.deficit(ammo);}
    public int reserveAmmo(){var d=definition(ammunitionWeapon());return d==null?0:reserveAmmo(d.ammoId());}
    /** Caller must debit precisely the returned amount from its matching finite supply inventory. */
    public int receiveAmmo(int offered){return receiveAmmo(requiredAmmoItem(),offered);}
    public int magazineAmmo(){return FirearmItem.state(ammunitionWeapon(),reserveAmmo()).magazine();}
    private boolean readyWeapon(){
        if(!equipped()||!(getMainHandItem().getItem() instanceof FirearmItem))return false;
        var d=weaponDefinition();long now=level().getGameTime();var state=FirearmItem.state(getMainHandItem(),reserveAmmo(d.ammoId()));
        if(state.magazine()==0){
            var reloaded=state.reload(d,now);int debit=state.reserve()-reloaded.reserve();
            if(debit>0&&loadoutInventory.consume(d.ammoId(),debit))com.codex.blacklight.firearms.FirearmReloadSystem.begin(getMainHandItem(),reloaded,now);
            return false;
        }return state.canFire(now);
    }
    private boolean hasAmmunition(ItemStack stack){var d=definition(stack);return d!=null&&(FirearmItem.state(stack,0).magazine()>0||reserveAmmo(d.ammoId())>0);}
    public boolean hasBattleAmmunition(){return equipped()&&(hasAmmunition(getMainHandItem())||hasAmmunition(getOffhandItem()));}

    /** A campaign core is a real block hit with a real carried rifle, never a timed objective deletion. */
    public boolean fireAtCampaignCore(BlockPos at){
        if(!(level() instanceof ServerLevel server)||!tacticsAvailable()
                ||!com.codex.blacklight.city.MilitaryCampaignSystem.authorizedCore(this,at))return false;
        ItemStack main=getMainHandItem(),other=getOffhandItem();
        var d=definition(main);
        if(d==null||d.kind()!=WeaponDefinition.Kind.BULLET||!hasAmmunition(main)){
            var second=definition(other);
            if(second==null||second.kind()!=WeaponDefinition.Kind.BULLET||!hasAmmunition(other))return false;
            setItemSlot(EquipmentSlot.MAINHAND,other);setItemSlot(EquipmentSlot.OFFHAND,main);d=second;
        }
        Vec3 start=getEyePosition().add(0,-.18,0),end=Vec3.atCenterOf(at);
        if(start.distanceToSqr(end)>28*28||!com.codex.blacklight.military.vehicle.VehicleDamageBridge.loaded(server,start,end))return false;
        var wall=server.clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));
        if(wall.getType()!=HitResult.Type.BLOCK||!wall.getBlockPos().equals(at))return false;
        var hit=ProjectileUtil.getEntityHitResult(server,this,start,wall.getLocation(),new AABB(start,end).inflate(.3),
                entity->entity!=this&&entity instanceof LivingEntity&&entity.isAlive()&&entity.isPickable());
        if(hit!=null)return false;
        if(com.codex.blacklight.military.vehicle.VehicleDamageBridge.nearest(server,this,null,start,wall.getLocation())!=null)return false;
        long now=server.getGameTime();int age=(int)Math.floorMod(now,profile().cycleTicks());
        if(age<profile().aimTicks()||age>=profile().burstTicks()){entityData.set(PHASE,age<profile().aimTicks()?1:3);return true;}
        entityData.set(PHASE,2);
        if((age-profile().aimTicks())%8!=0||!readyWeapon())return true;
        var state=FirearmItem.state(getMainHandItem(),reserveAmmo(d.ammoId()));
        FirearmItem.store(getMainHandItem(),state.fire(d,now));
        FirearmsCombat.fire(this,d,end.subtract(start).normalize(),profile().damage()/d.damage());return true;
    }
    private void selectWeapon(LivingEntity target){
        if(!equipped())return;
        var main=getMainHandItem();var other=getOffhandItem();var a=definition(main);var b=definition(other);ItemStack selected=main;
        // Prefer the real finite launcher only when its shot and splash are safe; otherwise use the real bullet gun.
        if(b!=null&&b.kind()!=WeaponDefinition.Kind.BULLET&&hasAmmunition(other)&&safeExplosion(target,b))selected=other;
        else if(a!=null&&a.kind()!=WeaponDefinition.Kind.BULLET&&hasAmmunition(main)&&safeExplosion(target,a))selected=main;
        else if(b!=null&&b.kind()==WeaponDefinition.Kind.BULLET&&hasAmmunition(other))selected=other;
        if(selected==other){setItemSlot(EquipmentSlot.MAINHAND,other);setItemSlot(EquipmentSlot.OFFHAND,main);}
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
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder){super.defineSynchedData(builder);builder.define(PHASE,0);builder.define(RANK,0);builder.define(EQUIPPED,true);builder.define(COMMAND_ROLE,0);}
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
    @Override public boolean canBeAffected(net.minecraft.world.effect.MobEffectInstance effect){
        return !(BlacklightInfectionSystem.infectionImmune(this)&&effect.getEffect().value()==com.codex.blacklight.registry.ModEffects.BLACKLIGHT_INFECTION.value())
                &&super.canBeAffected(effect);
    }
    public boolean tacticsAvailable() {
        return isAlive() && !isRemoved() && !isNoAi() && !isPassenger()
                && !com.codex.blacklight.combat.ImpactControl.stunned(this)
                && !BlacklightInfectionSystem.isInfected(this)
                && !ModAttachments.peekData(this, ModAttachments.CONSUME_VISUAL, ConsumeVisual.EMPTY).active()
                && !ConsumeControl.ownsMotion(this);
    }
    public BlockPos home() {
        String dimension = level().dimension().location().toString();
        if (home == null || !dimension.equals(homeDimension)) { home = blockPosition().immutable(); homeDimension = dimension; }
        return home;
    }
    private CompoundTag saveLoadout(){
        var snapshot=loadoutInventory.snapshot();var result=new CompoundTag();
        result.putInt("Schema",snapshot.schemaVersion());result.putBoolean("Initialized",snapshot.initialized());result.putBoolean("NativeIssued",snapshot.nativeIssued());
        if(snapshot.owner()!=null)result.putUUID("Owner",snapshot.owner());result.putString("Assignment",snapshot.assignment());
        var stocks=new CompoundTag();var limits=new CompoundTag();snapshot.reserves().forEach(stocks::putInt);snapshot.capacities().forEach(limits::putInt);
        result.put("Reserves",stocks);result.put("Capacities",limits);return result;
    }
    private static MilitaryLoadoutInventory loadLoadout(CompoundTag tag){
        try{
            if(!tag.contains("Schema",3)||!tag.contains("Initialized",1)||!tag.contains("NativeIssued",1)
                    ||!tag.contains("Assignment",8)||!tag.contains("Reserves",10)||!tag.contains("Capacities",10))throw new IllegalArgumentException("Missing typed ammunition fields");
            var stocks=tag.getCompound("Reserves");var limits=tag.getCompound("Capacities");
            if(stocks.getAllKeys().size()>WeaponCatalog.ALL.size()||limits.getAllKeys().size()>WeaponCatalog.ALL.size())throw new IllegalArgumentException("Too many ammunition kinds");
            var reserves=new java.util.LinkedHashMap<String,Integer>();var capacities=new java.util.LinkedHashMap<String,Integer>();
            for(String key:stocks.getAllKeys()){if(!stocks.contains(key,3))throw new IllegalArgumentException("Invalid stock type");reserves.put(key,stocks.getInt(key));}
            for(String key:limits.getAllKeys()){if(!limits.contains(key,3))throw new IllegalArgumentException("Invalid capacity type");capacities.put(key,limits.getInt(key));}
            return MilitaryLoadoutInventory.restore(new MilitaryLoadoutInventory.Snapshot(tag.getInt("Schema"),tag.getBoolean("Initialized"),tag.getBoolean("NativeIssued"),
                    tag.hasUUID("Owner")?tag.getUUID("Owner"):null,tag.getString("Assignment"),reserves,capacities));
        }catch(IllegalArgumentException failure){return MilitaryLoadoutInventory.legacy(null,0);}
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("MilitaryEquipped",equipped());tag.putBoolean("MilitaryWeaponInitialized",weaponInitialized);
        tag.putInt("MilitaryAmmoReserve",reserveAmmo());
        tag.put("MilitaryLoadout",saveLoadout());
        if(!stowedWeapon.isEmpty())tag.put("MilitaryStowedWeapon",stowedWeapon.save(registryAccess()));
        if(!stowedSecondary.isEmpty())tag.put("MilitaryStowedSecondary",stowedSecondary.save(registryAccess()));
        tag.putString("MilitaryRole",commandRole().id());
        tag.putString("MilitaryRank",rank().id()); if(unitId!=null)tag.putUUID("MilitaryUnit",unitId);
        tag.putBoolean("MilitaryAutomaticJoin",automaticUnitJoin);
        if(!deployment.isEmpty()){tag.putString("MilitaryDeployment",deployment);tag.putInt("MilitaryDeploymentCapacity",deploymentCapacity);tag.putDouble("MilitaryDeploymentX",deploymentOffset.x);tag.putDouble("MilitaryDeploymentY",deploymentOffset.y);tag.putDouble("MilitaryDeploymentZ",deploymentOffset.z);}
        tag.putLong("BlackwatchHome", home().asLong()); tag.putString("BlackwatchHomeDimension", homeDimension);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        // Vanilla bare /summon calls load before finalizeSpawn; only persisted equipment closes new issue.
        if(!tag.contains("HandItems")&&tag.getAllKeys().stream().noneMatch(key->key.startsWith("Military")||key.startsWith("Blackwatch")))return;
        entityData.set(EQUIPPED,!tag.contains("MilitaryEquipped")||tag.getBoolean("MilitaryEquipped"));
        // Loading an old missing/lost gun is not a new equipment issue.
        weaponInitialized=true;
        stowedWeapon=tag.contains("MilitaryStowedWeapon")?ItemStack.parseOptional(registryAccess(),tag.getCompound("MilitaryStowedWeapon")):ItemStack.EMPTY;
        stowedSecondary=tag.contains("MilitaryStowedSecondary")?ItemStack.parseOptional(registryAccess(),tag.getCompound("MilitaryStowedSecondary")):ItemStack.EMPTY;
        if(!equipped()){
            if(stowedWeapon.isEmpty())stowedWeapon=getMainHandItem();
            if(stowedSecondary.isEmpty())stowedSecondary=getOffhandItem();
            setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);setItemSlot(EquipmentSlot.OFFHAND,ItemStack.EMPTY);
        }
        if(tag.contains("MilitaryLoadout",10))loadoutInventory=loadLoadout(tag.getCompound("MilitaryLoadout"));
        else loadoutInventory=MilitaryLoadoutInventory.legacy(definition(ammunitionWeapon()),Math.max(0,Math.min(4096,tag.getInt("MilitaryAmmoReserve"))));
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
            BlacklightInfectionSystem.preserveBlackwatch(this);
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
            selectWeapon(target);
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
        if(!validTarget(target)||!clearLine(target))return false;
        var d=definition(getMainHandItem());return d!=null&&(d.kind()==WeaponDefinition.Kind.BULLET||safeExplosion(target,d));
    }
    private boolean clearLine(LivingEntity target){
        if(!(level() instanceof ServerLevel server)||!validTarget(target))return false;
        Vec3 start=getEyePosition().add(0,-.18,0),end=target.getBoundingBox().getCenter();
        if(!com.codex.blacklight.military.vehicle.VehicleDamageBridge.loaded(server,start,end))return false;
        var wall=level().clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));
        if(wall.getType()!=HitResult.Type.MISS)return false;
        var mechanical=com.codex.blacklight.military.vehicle.VehicleDamageBridge.nearest(server,this,null,start,end);
        if(mechanical!=null&&com.codex.blacklight.military.vehicle.VehicleDamageBridge.protectedHull(this,mechanical.vehicle()))return false;
        var hit=ProjectileUtil.getEntityHitResult(level(),this,start,end,new AABB(start,end).inflate(.3),
                entity->entity!=this&&entity instanceof LivingEntity living&&living.isAlive()&&entity.isPickable());
        return hit==null||hit.getEntity() instanceof LivingEntity living&&validTarget(living);
    }
    private Vec3 aimDirection(LivingEntity target,WeaponDefinition d){
        Vec3 delta=target.getBoundingBox().getCenter().subtract(getEyePosition().add(0,-.18,0));
        if(d.kind()==WeaponDefinition.Kind.BULLET||d.kind()==WeaponDefinition.Kind.ROCKET||d.kind()==WeaponDefinition.Kind.MISSILE)return delta.normalize();
        // ThrowableProjectile: dry drag .99, gravity .03 after each movement. Solve the low arc using that discrete drag sum.
        double horizontal=Math.sqrt(delta.x*delta.x+delta.z*delta.z),time=Math.max(1,horizontal/1.15),vx=0,vy=0;
        if(horizontal<1e-6)return delta.normalize();
        for(int i=0;i<8;i++){
            double sum=(1-Math.pow(.99,time))/.01;
            vx=horizontal/sum;vy=(delta.y+3*(time-sum))/sum;
            time=Math.max(1,Math.min(40,time*Math.sqrt(vx*vx+vy*vy)/1.15));
        }
        return new Vec3(delta.x/horizontal*vx,vy,delta.z/horizontal*vx).normalize();
    }
    private Vec3 explosiveImpact(LivingEntity target,WeaponDefinition d){
        if(!(level() instanceof ServerLevel server))return null;
        boolean straight=d.kind()==WeaponDefinition.Kind.ROCKET||d.kind()==WeaponDefinition.Kind.MISSILE;
        Vec3 from=getEyePosition().add(0,-.18,0),velocity=aimDirection(target,d).scale(straight?2.2F:1.15F);
        for(int step=0;step<40;step++){
            Vec3 to=from.add(velocity);
            if(!com.codex.blacklight.military.vehicle.VehicleDamageBridge.loaded(server,from,to)
                    ||!level().getFluidState(BlockPos.containing(from)).isEmpty()
                    ||!level().getFluidState(BlockPos.containing(to)).isEmpty())return null;
            var wall=level().clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));
            Vec3 end=wall.getType()==HitResult.Type.MISS?to:wall.getLocation();
            var entityHit=ProjectileUtil.getEntityHitResult(level(),this,from,end,new AABB(from,end).inflate(.3),entity->entity!=this&&entity.isAlive()&&entity.isPickable());
            if(entityHit!=null)end=entityHit.getLocation();
            var mechanical=com.codex.blacklight.military.vehicle.VehicleDamageBridge.nearest(server,this,null,from,end);
            if(mechanical!=null)return mechanical.point();
            if(entityHit!=null||wall.getType()!=HitResult.Type.MISS)return end;
            from=to;velocity=velocity.scale(.99F);if(!straight)velocity=velocity.add(0,-.03,0);
        }
        return null;
    }
    private boolean safeExplosion(LivingEntity target,WeaponDefinition d){
        if(d.kind()==WeaponDefinition.Kind.BULLET||!validTarget(target)||distanceToSqr(target)<10*10||!clearLine(target))return false;
        Vec3 center=explosiveImpact(target,d);
        if(center==null||center.distanceToSqr(getEyePosition().add(0,-.18,0))<10*10)return false;
        // Radius margin covers the living body's height and projectile collision inflation.
        double radius=d.radius()+1;
        for(LivingEntity nearby:level().getEntitiesOfClass(LivingEntity.class,new AABB(center,center).inflate(radius),LivingEntity::isAlive)){
            if(nearby.getBoundingBox().getCenter().distanceToSqr(center)<=radius*radius&&!validTarget(nearby))return false;
        }
        return true;
    }
    private void fire(LivingEntity target){
        if(!(level() instanceof ServerLevel level) || !tacticsAvailable() || !validTarget(target)
                || !hasLineOfSight(target) || !clearShot(target)) return;
        if(!readyWeapon())return;
        var d=weaponDefinition();var state=FirearmItem.state(getMainHandItem(),reserveAmmo(d.ammoId()));
        FirearmItem.store(getMainHandItem(),state.fire(d,level.getGameTime()));
        Vec3 aim=aimDirection(target,d);
        if(d.kind()==WeaponDefinition.Kind.BULLET)aim=aim.add((random.nextDouble()-.5)*.035,(random.nextDouble()-.5)*.025,(random.nextDouble()-.5)*.035).normalize();
        FirearmsCombat.fire(this,d,aim,d.kind()==WeaponDefinition.Kind.BULLET?profile().damage()/d.damage():1);
    }
}
