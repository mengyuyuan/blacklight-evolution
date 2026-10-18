package com.codex.blacklight.entity;

import com.codex.blacklight.combat.ConsumeSequence;
import com.codex.blacklight.infection.BlacklightInfectionSystem;
import com.codex.blacklight.infection.DevourerFeedingSystem;
import com.codex.blacklight.power.CombatScale;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.*;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.DragonFireball;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** A saved host species with ordinary anatomy or an authored giant boss body. */
public final class BlacklightInfectedHost extends Monster {
    private static final EntityDataAccessor<CompoundTag> HOST=SynchedEntityData.defineId(BlacklightInfectedHost.class,EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<Integer> FEED_TARGET=SynchedEntityData.defineId(BlacklightInfectedHost.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> FEED_STARTED=SynchedEntityData.defineId(BlacklightInfectedHost.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<CompoundTag> NURSERY=SynchedEntityData.defineId(BlacklightInfectedHost.class,EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<Integer> FEED_NODE=SynchedEntityData.defineId(BlacklightInfectedHost.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DEVOURER=SynchedEntityData.defineId(BlacklightInfectedHost.class,EntityDataSerializers.BOOLEAN);
    // Reserve stable part IDs before the spawn packet. Species data arrives after that packet.
    private static final int PART_COUNT=20;
    private final InfectedHostPart[] parts=new InfectedHostPart[PART_COUNT];
    private InfectedHostPart[] activeParts=new InfectedHostPart[0];
    private final ServerBossEvent bossBar=new ServerBossEvent(Component.empty(),BossEvent.BossBarColor.RED,BossEvent.BossBarOverlay.PROGRESS);
    // Track viewers even before growth starts: changing form does not restart entity tracking.
    private final Set<ServerPlayer> trackingPlayers=new HashSet<>();
    private Mob appearance;
    private EnderDragon dragonFightProxy;
    private int appearanceAnimationTick=Integer.MIN_VALUE;
    private boolean navigationReady;
    private String navigationFamily="";
    private boolean bossDefeated;
    private int attackCooldown;

    public BlacklightInfectedHost(EntityType<? extends BlacklightInfectedHost> type,Level level) {
        super(type,level);xpReward=8;
        for(int i=0;i<parts.length;i++)parts[i]=new InfectedHostPart(this);
        setId(ENTITY_COUNTER.getAndAdd(PART_COUNT+1)+1);
        configureParts();
    }
    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,30).add(Attributes.ATTACK_DAMAGE,5)
                .add(Attributes.MOVEMENT_SPEED,.30).add(Attributes.FLYING_SPEED,.35).add(Attributes.FOLLOW_RANGE,24)
                .add(net.minecraftforge.common.ForgeMod.STEP_HEIGHT_ADDITION.get(),.4).add(Attributes.KNOCKBACK_RESISTANCE,0);
    }
    @Override protected void defineSynchedData() {
        super.defineSynchedData();entityData.define(HOST,new CompoundTag());entityData.define(FEED_TARGET,-1);entityData.define(FEED_STARTED,-1L);entityData.define(DEVOURER,false);entityData.define(NURSERY,new CompoundTag());entityData.define(FEED_NODE,-1);
    }
    public int feedingTargetId() { return entityData.get(FEED_TARGET); }
    public long feedingStarted() { return entityData.get(FEED_STARTED); }
    public boolean devourerIdentity() { return entityData.get(DEVOURER); }
    public void setFeedingVisual(int target,long started) {
        if(!level().isClientSide){entityData.set(FEED_TARGET,target);entityData.set(FEED_STARTED,started);}
    }
    public int feedingNode() { return entityData.get(FEED_NODE); }
    public CompoundTag nurseryData() { return entityData.get(NURSERY); }
    public net.minecraft.core.BlockPos nurseryHome() { return net.minecraft.core.BlockPos.of(nurseryData().getLong("Home")); }
    public void setFeedingNode(int node) { if(!level().isClientSide)entityData.set(FEED_NODE,node); }
    public void syncNursery(net.minecraft.core.BlockPos home,int nodes,boolean supplied) {
        if(level().isClientSide)return;
        CompoundTag data=new CompoundTag();data.putLong("Home",home.asLong());data.putInt("Nodes",nodes);data.putBoolean("Supplied",supplied);
        if(!data.equals(nurseryData()))entityData.set(NURSERY,data);
    }
    public CompoundTag hostData() { return entityData.get(HOST); }
    public String speciesId() { return hostData().getString("Type"); }
    public String family() { return hostData().getString("Family"); }
    public boolean humanoid() { return hostData().getBoolean("Humanoid"); }
    public boolean quadruped() { return hostData().getBoolean("Quadruped"); }
    public boolean creeper() { return speciesId().equals("minecraft:creeper"); }
    /** Authored anatomy is separate from encounter tier: these meshes also have compact variants. */
    public InfectedBossProfile bossProfile() { return InfectedBossProfile.forSpecies(speciesId()); }
    public boolean hasMultipartBody() { return bossProfile()!=null; }
    public boolean giantBoss() {
        if(!hasMultipartBody())return false;
        return hostData().contains("Colossal")?hostData().getBoolean("Colossal"):hostData().getInt("BossProfileVersion")>=1;
    }
    public boolean encounterBoss() {
        return giantBoss()||speciesId().equals("minecraft:wither")||speciesId().equals("minecraft:ender_dragon");
    }
    /** Uniform authored-mesh scale, also used by server damage parts and locomotion dimensions. */
    public float bodyScale() { return bodyScaleAt(evolutionTicks()); }
    private float baseBodyScale() {
        InfectedBossProfile profile=bossProfile();
        if(profile==null)return 1;
        if(hostData().contains("BaseBodyScale"))return Mth.clamp(hostData().getFloat("BaseBodyScale"),.005F,4);
        if(hostData().contains("BodyScale"))return Mth.clamp(hostData().getFloat("BodyScale"),.005F,4);
        float targetHeight=hostData().contains("Height")?hostData().getFloat("Height"):sourceType().getHeight()*1.12F;
        return Mth.clamp(targetHeight/profile.visualHeight(),.005F,4);
    }
    public int evolutionTicks() { return giantBoss()?18000:Mth.clamp(hostData().getInt("EvolutionTicks"),0,18000); }
    private float bodyScaleAt(int ticks) {
        if(!hasMultipartBody()||giantBoss())return 1;
        return growthScale(baseBodyScale(),ticks);
    }
    private static float growthScale(float base,int ticks) {
        float middle=Math.max(base,.35F);
        int clamped=Mth.clamp(ticks,0,18000);
        float phase=clamped<=12000?clamped/12000F:(clamped-12000)/6000F;
        float eased=phase*phase*(3-2*phase);
        return clamped<=12000?Mth.lerp(eased,base,middle):Mth.lerp(eased,middle,Math.max(base,1));
    }
    /** Persisted growth only. The encounter coordinator owns eligibility and space checks. */
    public boolean setEvolutionProgress(int ticks) {
        if(level().isClientSide||!isAlive()||!hasMultipartBody()||giantBoss())return false;
        int next=Mth.clamp(ticks,0,18000);
        if(next==evolutionTicks())return false;
        float base=baseBodyScale();CompoundTag data=hostData().copy();
        data.putFloat("BaseBodyScale",base);data.putFloat("BodyScale",base);data.putInt("EvolutionTicks",next);
        data.putBoolean("EvolutionRegressing",next<evolutionTicks());
        data.putInt("FormVersion",2);data.putBoolean("Colossal",false);
        float scale=bodyScaleAt(next);InfectedBossProfile profile=bossProfile();
        data.putFloat("Width",profile.width()*scale);data.putFloat("Height",profile.height()*scale);
        entityData.set(HOST,data);
        return true;
    }
    /** Predict the locomotion collider without changing pose, health, or synced entity data. */
    public AABB evolutionBounds(int proposedTicks) {
        InfectedBossProfile profile=bossProfile();
        if(profile==null)return getBoundingBox();
        float scale=bodyScaleAt(proposedTicks);
        return EntityDimensions.scalable(profile.width()*scale,profile.height()*scale).scale(getScale()).makeBoundingBox(position());
    }
    /** Full authored body for build-height/world-border checks; never a terrain collision volume. */
    public AABB evolutionVisualBounds(int proposedTicks) {
        InfectedBossProfile profile=bossProfile();if(profile==null)return getBoundingBox();
        AABB local=profile.localVisualBounds();float scale=bodyScaleAt(proposedTicks);
        double angle=yBodyRot*Mth.DEG_TO_RAD,cos=Math.cos(angle),sin=Math.sin(angle);
        double minX=Double.POSITIVE_INFINITY,minZ=Double.POSITIVE_INFINITY,maxX=Double.NEGATIVE_INFINITY,maxZ=Double.NEGATIVE_INFINITY;
        for(double x:new double[]{local.minX,local.maxX})for(double z:new double[]{local.minZ,local.maxZ}) {
            double rotatedX=(-x*cos+z*sin)*scale,rotatedZ=(-x*sin-z*cos)*scale;
            minX=Math.min(minX,rotatedX);maxX=Math.max(maxX,rotatedX);
            minZ=Math.min(minZ,rotatedZ);maxZ=Math.max(maxZ,rotatedZ);
        }
        return new AABB(getX()+minX,getY()+local.minY*scale,getZ()+minZ,getX()+maxX,getY()+local.maxY*scale,getZ()+maxZ);
    }
    /** Pure prospective core + limb boxes, valid immediately after moveTo and before a tick. */
    public List<AABB> evolutionCollisionBoxes(int proposedTicks) {
        InfectedBossProfile profile=bossProfile();if(profile==null)return List.of(getBoundingBox());
        float scale=bodyScaleAt(proposedTicks);List<AABB> boxes=new ArrayList<>();boxes.add(evolutionBounds(proposedTicks));
        double angle=yBodyRot*Mth.DEG_TO_RAD,cos=Math.cos(angle),sin=Math.sin(angle);
        for(var part:profile.parts()) {
            double x=(-part.x()*cos+part.z()*sin)*scale,z=(-part.x()*sin-part.z()*cos)*scale;
            boxes.add(EntityDimensions.scalable(part.width()*scale,part.height()*scale)
                    .makeBoundingBox(getX()+x,getY()+part.y()*scale,getZ()+z));
        }
        return boxes;
    }
    /** A checked encounter promotion; CombatScale's existing modifier is retained exactly once. */
    public boolean promoteToColossus() {
        InfectedBossProfile profile=bossProfile();
        if(level().isClientSide||!isAlive()||profile==null||giantBoss())return false;
        float fraction=Mth.clamp(getHealth()/getMaxHealth(),0,1);
        CompoundTag data=hostData().copy();data.putFloat("BaseBodyScale",baseBodyScale());
        data.putBoolean("Colossal",true);data.putInt("FormVersion",2);data.putInt("BossProfileVersion",2);
        data.putInt("EvolutionTicks",18000);data.putFloat("BodyScale",1);
        data.putBoolean("EvolutionRegressing",false);
        data.putFloat("Width",profile.width());data.putFloat("Height",profile.height());
        entityData.set(HOST,data);
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(profile.health());
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(profile.damage());
        applyBossAttributes(profile);setHealth(getMaxHealth()*fraction);
        setPersistenceRequired();
        updateBossBar(true);
        return true;
    }
    @Override public boolean isBaby() {
        var visual=hostData().getCompound("Visual");
        return !giantBoss()&&(visual.getInt("Age")<0||visual.getBoolean("IsBaby"));
    }
    /** Artist meshes are adult sized; slime size and juvenile scale are saved explicitly. */
    public float visualScale() {
        if(giantBoss())return 1;
        CompoundTag data=hostData();
        if(data.contains("ModelScale"))return Mth.clamp(data.getFloat("ModelScale"),.25F,16);
        var visual=data.getCompound("Visual");
        if(visual.getInt("Age")<0||visual.getBoolean("IsBaby"))return .5F;
        if(speciesId().equals("minecraft:slime")||speciesId().equals("minecraft:magma_cube"))return Mth.clamp(visual.getInt("Size")+1,1,16);
        return 1;
    }
    public void inherit(Mob source) { inherit(source,false); }
    /** Colossal encounters require an explicit caller; infection never implies this tier. */
    public void inherit(Mob source,boolean colossal) {
        CompoundTag data=new CompoundTag();
        String id=BuiltInRegistries.ENTITY_TYPE.getKey(source.getType()).toString();data.putString("Type",id);
        InfectedBossProfile profile=InfectedBossProfile.forSpecies(id);
        colossal=colossal&&profile!=null;
        float bodyScale=profile==null||colossal?1:Mth.clamp(source.getBbHeight()*1.12F/profile.visualHeight(),.005F,4);
        data.putBoolean("Colossal",colossal);data.putInt("FormVersion",2);data.putFloat("BodyScale",bodyScale);
        data.putFloat("BaseBodyScale",bodyScale);data.putInt("EvolutionTicks",colossal?18000:0);
        String family=source instanceof net.minecraft.world.entity.animal.WaterAnimal?"water":
                source instanceof FlyingMob||source instanceof net.minecraft.world.entity.animal.FlyingAnimal
                        ||source instanceof net.minecraft.world.entity.ambient.Bat||source instanceof net.minecraft.world.entity.monster.Vex?"air":"ground";
        if(List.of("minecraft:guardian","minecraft:elder_guardian","minecraft:axolotl","minecraft:tadpole").contains(id))family="water";
        if(List.of("minecraft:blaze","minecraft:allay").contains(id))family="air";
        if(profile!=null)family=profile.family();
        data.putString("Family",family);data.putBoolean("Humanoid",ConsumeSequence.humanoid(source));
        data.putBoolean("Quadruped",ConsumeSequence.quadruped(source));
        data.putFloat("Width",profile!=null?profile.width()*bodyScale:source.getBbWidth()*1.12F);
        data.putFloat("Height",profile!=null?profile.height()*bodyScale:source.getBbHeight()*1.12F);
        data.putFloat("ModelScale",source instanceof Slime slime?slime.getSize():source.isBaby()?.5F:1);
        data.putBoolean("Tamed",source instanceof TamableAnimal tame&&tame.isTame()
                ||source instanceof net.minecraft.world.entity.animal.horse.AbstractHorse horse&&horse.isTamed());
        if(colossal)data.putInt("BossProfileVersion",2);
        // Only visual variants cross into the detached proxy, never inventory/brain/UUID.
        CompoundTag saved=new CompoundTag(),visual=new CompoundTag();source.saveWithoutId(saved);
        for(String key:new String[]{"Age","IsBaby","Variant","variant","Color","Sheared","CollarColor","CatType",
                "RabbitType","Size","Type","Profession","VillagerData","PuffState","MooshroomType","MainGene","HiddenGene","Peek","AttachFace"})
            if(saved.contains(key))visual.put(key,saved.get(key).copy());
        data.put("Visual",visual);
        // Summoned draft dragons must never claim or finish the natural End encounter.
        if(source instanceof EnderDragon dragon&&level() instanceof ServerLevel server
                &&server.getDragonFight()!=null&&source.getUUID().equals(server.getDragonFight().getDragonUUID())) {
            data.putUUID("DragonFight",source.getUUID());
            data.putInt("DragonOriginX",dragon.getFightOrigin().getX());data.putInt("DragonOriginZ",dragon.getFightOrigin().getZ());
        }
        entityData.set(HOST,data);refreshDimensions();
        float originalHealth=CombatScale.originalMaximum(source);
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(colossal?profile.health():Math.max(18,originalHealth*1.4));
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(colossal?profile.damage():com.codex.blacklight.util.MathCompat.clamp(originalHealth*.14,4,18));
        if(colossal)applyBossAttributes(profile);else if(encounterBoss())applyCompactEncounterAttributes(profile);
        setHealth(Math.max(1,getMaxHealth()*source.getHealth()/source.getMaxHealth()));
        setPersistenceRequired();setupNavigation();
        if(!level().isClientSide)updateBossBar(true);
    }
    private void applyBossAttributes(InfectedBossProfile profile) {
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(profile.speed());
        getAttribute(Attributes.FLYING_SPEED).setBaseValue(profile.speed());
        getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(96);
        getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        getAttribute(net.minecraftforge.common.ForgeMod.STEP_HEIGHT_ADDITION.get()).setBaseValue(1.4);
        xpReward=profile==InfectedBossProfile.ENDER_DRAGON?1000:150;noCulling=true;
    }
    private void applyCompactEncounterAttributes(InfectedBossProfile profile) {
        getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(64);
        getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        xpReward=profile==InfectedBossProfile.ENDER_DRAGON?500:50;
    }
    @Override public EntityDimensions getDimensions(Pose pose) {
        var data=hostData();InfectedBossProfile profile=bossProfile();
        if(profile!=null)return EntityDimensions.scalable(profile.width()*bodyScale(),profile.height()*bodyScale());
        if(!data.contains("Width"))return super.getDimensions(pose);
        return EntityDimensions.scalable(Mth.clamp(data.getFloat("Width"),.2F,16),Mth.clamp(data.getFloat("Height"),.2F,16));
    }
    @Override protected float getStandingEyeHeight(Pose pose,EntityDimensions dimensions) {
        InfectedBossProfile profile=bossProfile();
        return profile==null?super.getStandingEyeHeight(pose,dimensions):profile.visualHeight()*bodyScale()*.77F;
    }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if(HOST.equals(key)) {
            appearance=null;appearanceAnimationTick=Integer.MIN_VALUE;
            refreshDimensions();configureParts();
            // The client initially registered reserved IDs before HOST was received.
            // Drop unused parts once species is known; ordinary hordes do not need 20
            // extra entries in Forge's global multipart collision queries.
            if(level().isClientSide&&level().getEntity(getId())==this)
                level().getPartEntities().removeIf(p->p instanceof InfectedHostPart part&&part.getParent()==this&&!part.active());
            if(!level().isClientSide&&bossBar!=null)updateBossBar(true);
        }
    }
    private EntityType<?> sourceType() {
        ResourceLocation id=ResourceLocation.tryParse(speciesId());
        if(id==null)return EntityType.PIG;
        EntityType<?> type=BuiltInRegistries.ENTITY_TYPE.get(id);
        return type==null||type==getType()?EntityType.PIG:type;
    }
    /** Detached, unticked fallback render proxy. */
    public Mob appearance() {
        if(appearance==null) {
            Entity value=sourceType().create(level());
            if(!(value instanceof Mob))value=EntityType.PIG.create(level());
            appearance=(Mob)value;
            if(appearance!=null){appearance.readAdditionalSaveData(hostData().getCompound("Visual"));appearance.setNoAi(true);}
        }
        return appearance;
    }
    public void advanceAppearanceAnimation() {
        if(appearance==null||appearanceAnimationTick==tickCount)return;
        appearanceAnimationTick=tickCount;
        if(level().isClientSide&&appearance instanceof net.minecraft.world.entity.monster.Shulker shulker)shulker.tick();
        appearance.walkAnimation.update(walkAnimation.speed(),.4F);
    }
    @Override protected Component getTypeName() {
        if(devourerIdentity()||DevourerFeedingSystem.isDevourer(this))return Component.translatable("entity.blacklight.devourer");
        return Component.translatable(giantBoss()?"entity.blacklight.infected_boss_name":"entity.blacklight.infected_host_name",sourceType().getDescription());
    }
    private boolean validTarget(LivingEntity target) {
        if(target==this||!target.isAlive()||target instanceof HiveTendril)return false;
        if(DevourerFeedingSystem.isDevourer(this)&&!giantBoss())return false;
        if(DevourerFeedingSystem.isDevourer(this)&&(!(target instanceof Player)
                ||!DevourerFeedingSystem.withinTerritory(this,target)))return false;
        // Player infection grants powers, not allegiance to hostile infected hosts.
        if(target instanceof Player player)return !player.isCreative()&&!player.isSpectator();
        return !BlacklightInfectionSystem.isInfected(target);
    }
    @Override protected void registerGoals() {
        goalSelector.addGoal(0,new FloatGoal(this));
        goalSelector.addGoal(1,new Goal() {
            { setFlags(java.util.EnumSet.of(Goal.Flag.MOVE,Goal.Flag.LOOK)); }
            @Override public boolean canUse() { return DevourerFeedingSystem.controls(BlacklightInfectedHost.this); }
            @Override public boolean canContinueToUse() { return canUse(); }
        });
        goalSelector.addGoal(2,new MeleeAttackGoal(this,1.1,true) {
            @Override public boolean canUse() { return !DevourerFeedingSystem.controls(BlacklightInfectedHost.this)&&family().equals("ground")&&super.canUse(); }
            @Override public boolean canContinueToUse() { return !com.codex.blacklight.registry.ModAttachments.getData(BlacklightInfectedHost.this, ModAttachments.CONSUME_VISUAL).active()&&super.canContinueToUse(); }
            @Override protected void checkAndPerformAttack(LivingEntity target,double distanceSquared) {
                if(attackCooldown==0&&isTimeToAttack()&&BlacklightInfectedHost.this.isWithinMeleeAttackRange(target)&&getSensing().hasLineOfSight(target)) {
                    resetAttackCooldown();swing(InteractionHand.MAIN_HAND);doHurtTarget(target);attackCooldown=giantBoss()?40:20;
                }
            }
        });
        goalSelector.addGoal(6,new WaterAvoidingRandomStrollGoal(this,.8) {
            @Override public boolean canUse() { return !DevourerFeedingSystem.isDevourer(BlacklightInfectedHost.this)&&family().equals("ground")&&super.canUse(); }
        });
        targetSelector.addGoal(2,new NearestAttackableTargetGoal<>(this,LivingEntity.class,10,true,false,this::validTarget));
    }
    @Override public boolean isWithinMeleeAttackRange(LivingEntity target) {
        if(!hasMultipartBody())return super.isWithinMeleeAttackRange(target);
        // Reach from nearby body/limbs, never from the full wingspan as one attack radius.
        for(AABB box:combatHitboxes())if(box.inflate(giantBoss()?1.25:.7).intersects(target.getBoundingBox()))return true;
        return false;
    }
    private void setupNavigation() {
        if(navigationReady&&navigationFamily.equals(family()))return;
        navigationReady=true;navigationFamily=family();setNoGravity(family().equals("air"));
        if(family().equals("air")) {
            moveControl=new FlyingMoveControl(this,20,true);navigation=new FlyingPathNavigation(this,level());
        } else {
            moveControl=new net.minecraft.world.entity.ai.control.MoveControl(this);
            navigation=family().equals("water")?new WaterBoundPathNavigation(this,level()):new GroundPathNavigation(this,level());
        }
    }
    @Override public void tick() {
        super.tick();
        // Death/despawn can finish inside super.tick(); no state access after removal.
        if(isRemoved())return;
        updateParts();if(level().isClientSide)return;setupNavigation();
        entityData.set(DEVOURER,DevourerFeedingSystem.isDevourer(this));
        DevourerFeedingSystem.tick(this);
        tickBoss();
        if(family().equals("water")||hasMultipartBody())setAirSupply(getMaxAirSupply());
        if(com.codex.blacklight.registry.ModAttachments.getData(BlacklightInfectedHost.this, ModAttachments.CONSUME_VISUAL).active()||com.codex.blacklight.combat.ImpactControl.stunned(this)) { getNavigation().stop();return; }
        if(attackCooldown>0)attackCooldown--;
        if(!isAlive()||isNoAi()||level().getDifficulty()==net.minecraft.world.Difficulty.PEACEFUL||family().equals("ground"))return;
        var target=getTarget();if(target==null||!validTarget(target))return;
        if(family().equals("water")&&!isInWater())return;
        getLookControl().setLookAt(target,20,20);
        Vec3 direction=target.getBoundingBox().getCenter().subtract(getBoundingBox().getCenter());
        if(encounterBoss()) {
            // Keep the lower body above terrain; ranged basics can engage ground targets.
            double desiredY=target.getY()+3;
            Vec3 steering=new Vec3(direction.x,desiredY-getY(),direction.z);
            if(dragonFightId()!=null) {
                // The End fight keeps only its arena loaded. Do not pursue a player into
                // unloaded outer chunks and make the fight manager spawn a second dragon.
                Vec3 center=new Vec3(hostData().getInt("DragonOriginX"),desiredY,hostData().getInt("DragonOriginZ"));
                Vec3 offset=new Vec3(target.getX()-center.x,0,target.getZ()-center.z);
                if(offset.horizontalDistanceSqr()>96*96)offset=offset.normalize().scale(96);
                steering=center.add(offset).subtract(position());
            }
            double horizontal=Math.sqrt(steering.horizontalDistanceSqr());
            Vec3 acceleration=steering.normalize().scale(horizontal>(giantBoss()?28:12)?.045:0);
            acceleration=acceleration.add(0,Mth.clamp((desiredY-getY())*.004,-.025,.025),0);
            if(hasLineOfSight(target))setDeltaMovement(getDeltaMovement().scale(.92).add(acceleration));
            setYRot(Mth.rotLerp(.09F,getYRot(),(float)(Mth.atan2(-direction.x,direction.z)*Mth.RAD_TO_DEG)));yBodyRot=getYRot();
            if(attackCooldown==0&&hasLineOfSight(target)&&direction.lengthSqr()<96*96)basicBossProjectile(target);
        } else {
            if(hasLineOfSight(target))setDeltaMovement(getDeltaMovement().scale(.85).add(direction.normalize().scale(.045)));
            if(direction.lengthSqr()<Math.pow(getBbWidth()+target.getBbWidth()+.6,2)&&hasLineOfSight(target)&&attackCooldown==0) {
                swing(InteractionHand.MAIN_HAND);doHurtTarget(target);attackCooldown=24;
            }
        }
    }
    private void basicBossProjectile(LivingEntity target) {
        Vec3 origin=position().add(0,bossProfile().visualHeight()*bodyScale()*.8,0);
        for(InfectedHostPart part:parts)if(part.active()&&part.partName().equals("head")){origin=part.getBoundingBox().getCenter();break;}
        Vec3 aim=target.getBoundingBox().getCenter().subtract(origin).normalize();
        origin=origin.add(aim.scale(Math.max(.6,7*bodyScale())));
        Entity projectile=bossProfile()==InfectedBossProfile.WITHER?new WitherSkull(level(),this,aim.x,aim.y,aim.z) {
            @Override protected boolean canHitEntity(Entity target) { return !target.is(BlacklightInfectedHost.this)&&super.canHitEntity(target); }
        }:new DragonFireball(level(),this,aim.x,aim.y,aim.z) {
            @Override protected boolean canHitEntity(Entity target) { return !target.is(BlacklightInfectedHost.this)&&super.canHitEntity(target); }
        };
        projectile.setPos(origin);level().addFreshEntity(projectile);swing(InteractionHand.MAIN_HAND);
        attackCooldown=bossProfile()==InfectedBossProfile.WITHER?60:100;
    }
    private void tickBoss() {
        if(isAlive()&&dragonFightId()!=null)refreshDragonFight();
        updateBossBar(tickCount%20==0);
    }
    private boolean hasEncounterHealthBar() {
        return encounterBoss()||DevourerFeedingSystem.isDevourer(this)||hasMultipartBody()&&evolutionTicks()>0;
    }
    private Component encounterBarName() {
        if(!giantBoss()&&evolutionTicks()==0&&!DevourerFeedingSystem.isDevourer(this))return getDisplayName();
        String stage=giantBoss()?"colossal":evolutionTicks()>0&&hostData().getBoolean("EvolutionRegressing")?"regressing":evolutionTicks()>=12000?"awakening"
                :evolutionTicks()>=6000?"incubating":evolutionTicks()>0?"malformed":"nursery";
        return Component.translatable("bossbar.blacklight.host_stage",getDisplayName(),
                Component.translatable("bossbar.blacklight.stage_"+stage));
    }
    private boolean eligibleViewer(ServerPlayer player) {
        return player.isAlive()&&!player.isSpectator()&&player.level()==level()&&player.distanceToSqr(this)<160*160;
    }
    private void updateBossBar(boolean refreshViewers) {
        boolean visible=isAlive()&&hasEncounterHealthBar()&&dragonFightId()==null;
        bossBar.setVisible(visible);
        if(!visible){bossBar.removeAllPlayers();return;}
        bossBar.setName(encounterBarName());
        bossBar.setProgress(com.codex.blacklight.util.MathCompat.clamp(getHealth()/Math.max(1,getMaxHealth()),0,1));
        if(refreshViewers) {
            for(var player:List.copyOf(bossBar.getPlayers()))
                if(!trackingPlayers.contains(player)||!eligibleViewer(player))bossBar.removePlayer(player);
            for(var player:trackingPlayers)if(eligibleViewer(player))bossBar.addPlayer(player);
        }
    }
    @Override public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);trackingPlayers.add(player);updateBossBar(true);
    }
    @Override public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);trackingPlayers.remove(player);bossBar.removePlayer(player);
    }
    public UUID dragonFightId() { return hostData().hasUUID("DragonFight")?hostData().getUUID("DragonFight"):null; }
    /** Preserve the vanilla portal/gateway/respawn progression until the replacement dies. */
    public void refreshDragonFight() {
        UUID id=dragonFightId();
        if(id==null||!(level() instanceof ServerLevel server)||server.getDragonFight()==null)return;
        if(dragonFightProxy==null) {
            dragonFightProxy=EntityType.ENDER_DRAGON.create(level());if(dragonFightProxy==null)return;dragonFightProxy.setUUID(id);
        }
        dragonFightProxy.setHealth(dragonFightProxy.getMaxHealth()*getHealth()/getMaxHealth());
        dragonFightProxy.setCustomName(getDisplayName());server.getDragonFight().updateDragon(dragonFightProxy);
    }
    @Override public void die(DamageSource source) {
        super.die(source);
        // NeoForge may cancel LivingDeath (revive/totem mods). Finish the End fight
        // only after vanilla accepted death, never just because die() was invoked.
        if(!level().isClientSide&&dead&&!bossDefeated) {
            DevourerFeedingSystem.release(this);
            bossDefeated=true;
            bossBar.removeAllPlayers();
            if(dragonFightId()!=null&&level() instanceof ServerLevel server&&server.getDragonFight()!=null) {
                refreshDragonFight();if(dragonFightProxy!=null)server.getDragonFight().setDragonKilled(dragonFightProxy);
            }
        }
    }
    @Override protected void dropCustomDeathLoot(DamageSource source,int looting,boolean recentlyHit) {
        super.dropCustomDeathLoot(source,looting,recentlyHit);
        // This hook already observes doMobLoot, drop capture, and LivingDrops cancellation.
        if(bossProfile()==InfectedBossProfile.WITHER)spawnAtLocation(net.minecraft.world.item.Items.NETHER_STAR);
    }
    @Override public boolean hurt(DamageSource source,float amount) {
        if(encounterBoss()&&source.getEntity()==this)return false;
        return super.hurt(source,amount);
    }
    @Override public void remove(RemovalReason reason) {
        if(!level().isClientSide)DevourerFeedingSystem.release(this);
        if(bossBar!=null)bossBar.removeAllPlayers();if(trackingPlayers!=null)trackingPlayers.clear();super.remove(reason);
    }
    @Override protected boolean shouldDespawnInPeaceful() { return !encounterBoss(); }
    @Override public boolean fireImmune() { return giantBoss()||sourceType().fireImmune(); }
    /** The movement column is not an extra damage surface between a giant's legs. */
    @Override public boolean isPickable() { return !hasMultipartBody()&&super.isPickable(); }
    @Override public boolean isPushable() { return !hasMultipartBody()&&super.isPushable(); }
    @Override protected void doPush(Entity entity) {
        if(!hasMultipartBody()||combatHitboxes().stream().anyMatch(box->box.intersects(entity.getBoundingBox())))super.doPush(entity);
    }
    @Override public boolean shouldRenderAtSqrDistance(double distance) { return giantBoss()?distance<256*256:super.shouldRenderAtSqrDistance(distance); }
    @Override public AABB getBoundingBoxForCulling() { return hasMultipartBody()?combatBounds():super.getBoundingBoxForCulling(); }
    @Override public boolean isMultipartEntity() { return true; }
    @Override public InfectedHostPart[] getParts() { return speciesId().isEmpty()?parts:activeParts; }
    @Override public void setId(int id) {
        super.setId(id);if(parts!=null)for(int i=0;i<parts.length;i++)if(parts[i]!=null)parts[i].setId(id+i+1);
    }
    private void configureParts() {
        if(parts==null)return;InfectedBossProfile profile=bossProfile();
        for(int i=0;i<parts.length;i++)if(parts[i]!=null)parts[i].configure(profile!=null&&i<profile.parts().size()?profile.parts().get(i):null,bodyScale());
        activeParts=profile==null?new InfectedHostPart[0]:java.util.Arrays.copyOf(parts,profile.parts().size());
        updateParts();
    }
    private void updateParts() {
        if(parts==null)return;
        InfectedBossProfile profile=bossProfile();double angle=yBodyRot*Mth.DEG_TO_RAD,cos=Math.cos(angle),sin=Math.sin(angle);
        if(profile==null)return;
        for(int i=0;i<parts.length;i++) {
            InfectedHostPart part=parts[i];if(part==null)continue;Vec3 old=part.position();
            if(profile!=null&&i<profile.parts().size()) {
                var local=profile.parts().get(i);
                // Mesh front is -Z; MC yaw zero faces +Z.
                double x=-local.x()*cos+local.z()*sin,z=-local.x()*sin-local.z()*cos;
                float scale=bodyScale();part.setPos(getX()+x*scale,getY()+local.y()*scale,getZ()+z*scale);
            } else part.setPos(position());
            part.xo=old.x;part.yo=old.y;part.zo=old.z;part.xOld=old.x;part.yOld=old.y;part.zOld=old.z;
        }
    }
    public List<AABB> combatHitboxes() {
        List<AABB> boxes=new ArrayList<>();
        if(parts!=null)for(InfectedHostPart part:parts)if(part!=null&&part.active())boxes.add(part.getBoundingBox());
        if(boxes.isEmpty())boxes.add(getBoundingBox());
        return boxes;
    }
    public AABB combatBounds() { AABB result=getBoundingBox();for(AABB box:combatHitboxes())result=result.minmax(box);return result; }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);tag.put("BlacklightHost",hostData().copy());tag.putBoolean("BlacklightBossDefeated",bossDefeated);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);bossDefeated=tag.getBoolean("BlacklightBossDefeated");CompoundTag data=tag.getCompound("BlacklightHost").copy();
        InfectedBossProfile profile=InfectedBossProfile.forSpecies(data.getString("Type"));
        if(profile!=null) {
            // RC51 did not save how a giant was created. Preserve every already-saved
            // RC51 giant; older compact infections must never be promoted by loading.
            boolean colossal=data.contains("Colossal")?data.getBoolean("Colossal"):data.getInt("BossProfileVersion")>=1;
            data.putBoolean("Colossal",colossal);data.putInt("FormVersion",2);
            float baselineHeight=switch(data.getString("Type")) {
                case "minecraft:warden" -> 2.9F;
                case "minecraft:iron_golem" -> 2.7F;
                case "minecraft:wither" -> 3.5F;
                default -> 8;
            };
            float scale=colossal?1:data.contains("BodyScale")?Mth.clamp(data.getFloat("BodyScale"),.005F,4)
                    :Mth.clamp((data.contains("Height")?data.getFloat("Height"):baselineHeight*1.12F)/profile.visualHeight(),.005F,4);
            data.putFloat("BodyScale",scale);data.putString("Family",profile.family());
            if(!data.contains("BaseBodyScale"))data.putFloat("BaseBodyScale",scale);
            data.putInt("EvolutionTicks",colossal?18000:Mth.clamp(data.getInt("EvolutionTicks"),0,18000));
            scale=colossal?1:growthScale(data.getFloat("BaseBodyScale"),data.getInt("EvolutionTicks"));
            data.putFloat("Width",profile.width()*scale);data.putFloat("Height",profile.height()*scale);
            if(colossal)applyBossAttributes(profile);
            else if(profile==InfectedBossProfile.WITHER||profile==InfectedBossProfile.ENDER_DRAGON)applyCompactEncounterAttributes(profile);
        }
        entityData.set(HOST,data);refreshDimensions();setPersistenceRequired();
    }
}
