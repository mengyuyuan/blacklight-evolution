package com.codex.blacklight.entity;

import com.codex.blacklight.combat.ConsumeSequence;
import com.codex.blacklight.infection.BlacklightInfectionSystem;
import com.codex.blacklight.infection.DevourerFeedingSystem;
import com.codex.blacklight.infection.TissueOrgans;
import com.codex.blacklight.infection.TissueGrowth;
import com.codex.blacklight.infection.TissueEvolution;
import com.codex.blacklight.infection.TissueCore;
import com.codex.blacklight.infection.TissueMetabolism;
import com.codex.blacklight.infection.TissueDigestion;
import com.codex.blacklight.infection.TissueFeeding;
import com.codex.blacklight.infection.TissueVitals;
import com.codex.blacklight.infection.TissueDefense;
import com.codex.blacklight.infection.TissueDefenseSystem;
import com.codex.blacklight.infection.TissueVitalSystem;
import com.codex.blacklight.infection.TissueInheritance;
import com.codex.blacklight.infection.TissueInheritanceCodec;
import com.codex.blacklight.infection.TissueCompetition;
import com.codex.blacklight.infection.TissueCompetitionConfig;
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
    private boolean appearanceSwing,appearanceAirborne;
    private boolean navigationReady;
    private String navigationFamily="";
    private boolean bossDefeated;
    private int attackCooldown;
    private int tissueClock;
    private int tissueRecoveryTicks;
    private int tissueNutritionDebt;
    private int tissueCultivationTicks;
    private int evolvedMorphTicks;
    private static final EntityDataAccessor<CompoundTag> EVOLVED_STATE=SynchedEntityData.defineId(BlacklightInfectedHost.class,EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<CompoundTag> EVOLVED_ACTION=SynchedEntityData.defineId(BlacklightInfectedHost.class,EntityDataSerializers.COMPOUND_TAG);
    private final com.codex.blacklight.infection.EvolvedCombat evolvedCombat=new com.codex.blacklight.infection.EvolvedCombat(this);
    private static final EntityDataAccessor<CompoundTag> NATIVE_ACTION=SynchedEntityData.defineId(BlacklightInfectedHost.class,EntityDataSerializers.COMPOUND_TAG);
    private final com.codex.blacklight.infection.NativeInfectedCombat nativeCombat=new com.codex.blacklight.infection.NativeInfectedCombat(this);
    public com.codex.blacklight.infection.NativeInfectedCombat nativeCombat(){return nativeCombat;}
    public String nativeAction(){return entityData.get(NATIVE_ACTION).getString("Move");}
    public int nativeActionTarget(){return entityData.get(NATIVE_ACTION).getInt("Target");}
    public float nativeActionElapsed(float partial){var t=entityData.get(NATIVE_ACTION);return t.isEmpty()?-1:level().getGameTime()-t.getLong("At")+partial;}
    public void setNativeAction(String move,LivingEntity target){if(level().isClientSide)return;var t=new CompoundTag();if(!move.isEmpty()){t.putString("Move",move);t.putLong("At",level().getGameTime());t.putInt("Target",target==null?-1:target.getId());}entityData.set(NATIVE_ACTION,t);}
    public boolean acceptEvolvedImpact(){return !isEvolved()||evolvedCombat.acceptImpact();}
    public boolean evolvedControlProtected(){return evolvedCombat.controlProtected();}
    public boolean isEvolved(){return hostData().getBoolean("Evolved")||entityData.get(EVOLVED_STATE).getInt("Stage")==2;}
    public boolean isMetamorphosing(){int stage=entityData.get(EVOLVED_STATE).getInt("Stage");return stage==1||stage==3;}
    public java.util.Optional<com.codex.blacklight.infection.EvolvedMapping.Choice> evolvedChoice(){return com.codex.blacklight.infection.EvolvedChoiceCodec.read(hostData().getCompound("EvolvedChoice"));}
    public com.codex.blacklight.infection.EvolvedArchetype evolvedArchetype(){return com.codex.blacklight.infection.EvolvedArchetype.resolve(hostData().getCompound("EvolvedChoice").getString("Type"));}
    public int evolvedSpecialTarget(){return entityData.get(EVOLVED_ACTION).getInt("Target");}
    public int[] evolvedPatients(){return entityData.get(EVOLVED_ACTION).getIntArray("Patients");}
    public int evolvedShield(){var state=entityData.get(EVOLVED_STATE);return state.contains("Shield")?state.getInt("Shield"):20;}
    public void publishEvolvedShield(int shield){if(level().isClientSide)return;var state=entityData.get(EVOLVED_STATE).copy();state.putInt("Shield",Math.clamp(shield,0,20));entityData.set(EVOLVED_STATE,state);}
    public void setEvolvedPatients(int[] ids){if(level().isClientSide)return;var tag=entityData.get(EVOLVED_ACTION).copy();tag.putIntArray("Patients",ids);entityData.set(EVOLVED_ACTION,tag);}
    public float evolvedProgress(float partial){
        var state=entityData.get(EVOLVED_STATE);int stage=state.getInt("Stage");if(stage==2)return com.codex.blacklight.infection.EvolvedForm.DURATION;
        float extra=Math.clamp(level().getGameTime()-state.getLong("SampledAt")+partial,0,10)*(stage==3?-2:1);
        return Math.clamp(state.getInt("Elapsed")+(level().isClientSide?extra:0),0,com.codex.blacklight.infection.EvolvedForm.DURATION);
    }
    public String evolvedAction(){return entityData.get(EVOLVED_ACTION).getString("Move");}
    public boolean evolvedEngaged(){return entityData.get(EVOLVED_STATE).getBoolean("Combat");}
    public float evolvedDeployment(float partial){var state=entityData.get(EVOLVED_STATE);float t=(level().getGameTime()-state.getLong("CombatAt")+partial)/(evolvedEngaged()?6F:8F);return evolvedEngaged()?com.codex.blacklight.infection.EvolvedForm.smooth(t):1-com.codex.blacklight.infection.EvolvedForm.smooth(t);}
    public void setEvolvedEngaged(boolean combat){if(level().isClientSide||evolvedEngaged()==combat)return;var tag=entityData.get(EVOLVED_STATE).copy();tag.putBoolean("Combat",combat);tag.putLong("CombatAt",level().getGameTime());entityData.set(EVOLVED_STATE,tag);}
    public float evolvedActionElapsed(float partial){var action=entityData.get(EVOLVED_ACTION);return action.isEmpty()?-1:level().getGameTime()-action.getLong("Started")+partial;}
    public void setEvolvedAction(String action){if(level().isClientSide)return;var tag=new CompoundTag();if(!action.isEmpty()){tag.putString("Move",action);tag.putLong("Started",level().getGameTime());}entityData.set(EVOLVED_ACTION,tag);}
    public void setEvolvedAction(String action,LivingEntity target){setEvolvedAction(action);if(level().isClientSide||action.isEmpty())return;var tag=entityData.get(EVOLVED_ACTION).copy();tag.putInt("Target",target.getId());entityData.set(EVOLVED_ACTION,tag);}
    private void publishEvolvedState(int stage){var tag=new CompoundTag();tag.putInt("Stage",stage);tag.putInt("Elapsed",evolvedMorphTicks);tag.putLong("SampledAt",level().getGameTime());entityData.set(EVOLVED_STATE,tag);}
    public boolean beginEvolvedMetamorphosis(){
        if(level().isClientSide||!isAlive()||isEvolved()||isMetamorphosing()||hasMultipartBody()||devourerIdentity()
                ||!com.codex.blacklight.infection.EvolvedForm.eligible(tissueInheritance(),tissueAge())||!evolvedSpace())return false;
        if(evolvedChoice().isEmpty()){
            var data=hostData().copy();data.put("EvolvedChoice",com.codex.blacklight.infection.EvolvedChoiceCodec.write(com.codex.blacklight.infection.EvolvedMappingReload.choose(tissueInheritance(),getUUID())));entityData.set(HOST,data);
        }
        evolvedMorphTicks=0;publishEvolvedState(1);nativeCombat.interrupt();clearTissueStrike();organAbilities.interrupt();getNavigation().stop();setTarget(null);return true;
    }
    private boolean evolvedSpace(){
        float scale=com.codex.blacklight.infection.EvolvedForm.scale(tissueInheritance());
        var box=EntityDimensions.scalable(.95F*1.12F*scale,2.16F*1.12F*scale).makeBoundingBox(position());
        return box.maxY<=level().getMaxBuildHeight()&&level().getWorldBorder().isWithinBounds(box)&&level().noCollision(this,box);
    }
    private boolean tickEvolvedMorph(){
        if(isMetamorphosing()){
            int stage=entityData.get(EVOLVED_STATE).getInt("Stage");getNavigation().stop();setTarget(null);setXxa(0);setZza(0);setDeltaMovement(getDeltaMovement().multiply(0,1,0));
            if(stage==3){evolvedMorphTicks=Math.max(0,evolvedMorphTicks-2);if(evolvedMorphTicks==0)publishEvolvedState(0);else if(tickCount%5==0)publishEvolvedState(3);return true;}
            evolvedMorphTicks++;
            if(evolvedMorphTicks==com.codex.blacklight.infection.EvolvedForm.COMMIT){
                if(!evolvedSpace()){publishEvolvedState(3);return true;}
                commitEvolvedBody();
            }
            if(evolvedMorphTicks>=com.codex.blacklight.infection.EvolvedForm.DURATION){evolvedMorphTicks=com.codex.blacklight.infection.EvolvedForm.DURATION;publishEvolvedState(2);return true;}
            if(tickCount%5==0||evolvedMorphTicks==com.codex.blacklight.infection.EvolvedForm.COMMIT)publishEvolvedState(1);return true;
        }
        if(!isEvolved()&&tissueRecoveryTicks==0&&tickCount%20==0&&(getTarget()==null||!getTarget().isAlive()||distanceToSqr(getTarget())>24*24)
                &&getData(ModAttachments.CONSUME_VISUAL).active()==false&&!TissueCompetition.controls(this)&&organActionCore()==null)
            return beginEvolvedMetamorphosis();
        return false;
    }
    private void commitEvolvedBody(){
        if(isEvolved())return;var data=hostData().copy();data.put("EvolvedOrigin",data.copy());data.putString("EvolvedOriginSpecies",speciesId());
        data.putBoolean("Evolved",true);data.putString("Type","minecraft:zombie");data.putString("Family","ground");data.putBoolean("Humanoid",true);data.putBoolean("Quadruped",false);
        data.putFloat("Width",.95F*1.12F);data.putFloat("Height",2.16F*1.12F);data.putFloat("ModelScale",1);data.put("Visual",new CompoundTag());
        entityData.set(HOST,data);entityData.set(TISSUE_BODY_SCALE,com.codex.blacklight.infection.EvolvedForm.scale(tissueInheritance()));
        if(!hasCustomName())setCustomName(Component.translatable(evolvedArchetype().translation()));
        navigationReady=false;setupNavigation();getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(Math.max(48,getAttributeValue(Attributes.FOLLOW_RANGE)));updateTissueCombat();refreshDimensions();
    }
    private static final ResourceLocation TISSUE_HEALTH=ResourceLocation.fromNamespaceAndPath("blacklight","tissue_evolution_health");
    private static final ResourceLocation TISSUE_DAMAGE=ResourceLocation.fromNamespaceAndPath("blacklight","tissue_evolution_damage");
    private static final ResourceLocation TISSUE_ARMOR=ResourceLocation.fromNamespaceAndPath("blacklight","tissue_evolution_armor");
    private static final ResourceLocation TISSUE_KNOCKBACK=ResourceLocation.fromNamespaceAndPath("blacklight","tissue_evolution_knockback");
    private static final String TISSUE_MEAL="BlacklightTissueMealSettled";
    private static final EntityDataAccessor<Long> TISSUE_AGE=SynchedEntityData.defineId(BlacklightInfectedHost.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Float> TISSUE_BODY_SCALE=SynchedEntityData.defineId(BlacklightInfectedHost.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<org.joml.Vector3f> TISSUE_FRAME=SynchedEntityData.defineId(BlacklightInfectedHost.class,EntityDataSerializers.VECTOR3);
    public org.joml.Vector3f tissueFrame(){return new org.joml.Vector3f(entityData.get(TISSUE_FRAME));}
    public com.codex.blacklight.infection.TissueBodyPlan.Frame tissueBodyPlan(){return com.codex.blacklight.infection.TissueBodyPlan.resolve(nativeTissuePlan(),nativeTissueCore(),TissueOrgans.seed(getUUID()),tissueAge(),tissueInheritance());}
    private static final EntityDataAccessor<Float> TISSUE_CORE_FRACTURE=SynchedEntityData.defineId(BlacklightInfectedHost.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> TISSUE_STRIKE_TARGET=SynchedEntityData.defineId(BlacklightInfectedHost.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> TISSUE_STRIKE_STARTED=SynchedEntityData.defineId(BlacklightInfectedHost.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> TISSUE_GRIP_RELEASE=SynchedEntityData.defineId(BlacklightInfectedHost.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<org.joml.Vector3f> TISSUE_STRIKE_POINT=SynchedEntityData.defineId(BlacklightInfectedHost.class,EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Integer> TISSUE_NUTRITION=SynchedEntityData.defineId(BlacklightInfectedHost.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<CompoundTag> TISSUE_DIGESTION=SynchedEntityData.defineId(BlacklightInfectedHost.class,EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<CompoundTag> TISSUE_VITALS=SynchedEntityData.defineId(BlacklightInfectedHost.class,EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<CompoundTag> TISSUE_VASCULAR=SynchedEntityData.defineId(BlacklightInfectedHost.class,EntityDataSerializers.COMPOUND_TAG);
    private com.codex.blacklight.infection.TissueVascular vascular=new com.codex.blacklight.infection.TissueVascular();
    public com.codex.blacklight.infection.TissueVascular vascular(){return vascular;}
    public boolean hasVascularVisual(){return !entityData.get(TISSUE_VASCULAR).isEmpty();}
    public CompoundTag vascularVisual(){return entityData.get(TISSUE_VASCULAR).copy();}
    public void setVascularVisual(CompoundTag visual){if(!level().isClientSide)entityData.set(TISSUE_VASCULAR,visual);}
    private TissueVitals vitals=new TissueVitals();
    private static final EntityDataAccessor<CompoundTag> TISSUE_DEFENSE=SynchedEntityData.defineId(BlacklightInfectedHost.class,EntityDataSerializers.COMPOUND_TAG);
    private TissueDefense defense=new TissueDefense();
    public TissueDefense defense(){return defense;}
    public TissueDefense.Profile defenseProfile(){return tissueMetabolismActive()?new TissueDefense.Profile(organPower(TissueCore.OSTEODERM),organPower(TissueCore.NACRE)):new TissueDefense.Profile(0,0);}
    public CompoundTag defenseVisual(){return entityData.get(TISSUE_DEFENSE).copy();}
    public void publishDefense(){if(!level().isClientSide){var state=defense.visual(defenseProfile());if(!state.equals(entityData.get(TISSUE_DEFENSE)))entityData.set(TISSUE_DEFENSE,state);}}
    private TissueVitals.Profile vitalsProfile;
    private long vitalsAge=Long.MIN_VALUE,vitalsRevision=Long.MIN_VALUE;
    private String vitalsCore="";
    public TissueVitals vitals(){return vitals;}
    public TissueVitals.Profile vitalsProfile(){
        if(vitals==null)return TissueVitals.Profile.EMPTY;
        long age=tissueAge()/20,revision=tissueInheritance().revision();String core=hostData().getString("NativeCore");
        if(vitalsProfile==null||vitalsAge!=age||vitalsRevision!=revision||!vitalsCore.equals(core)){
            vitalsProfile=new TissueVitals.Profile(organPower(TissueCore.GILL_LAMELLA),organPower(TissueCore.LUNG),organPower(TissueCore.AIRFLOW),organPower(TissueCore.BOOK_LUNG),organPower(TissueCore.TRACHEA),organPower(TissueCore.HEART),organPower(TissueCore.BRANCHIAL_HEART),organPower(TissueCore.SPLEEN));
            vitalsAge=age;vitalsRevision=revision;vitalsCore=core;
        }return vitalsProfile;
    }
    public CompoundTag vitalsVisual(){return entityData.get(TISSUE_VITALS).copy();}
    public void publishVitals(){if(!level().isClientSide){var p=vitalsProfile();var state=p.active()?vitals.visual(p):new CompoundTag();if(!state.equals(entityData.get(TISSUE_VITALS)))entityData.set(TISSUE_VITALS,state);}}
    @Override public int getMaxAirSupply(){return vitals==null?300:vitalsProfile().maxAir(hostData().contains("NativeMaxAir")?hostData().getInt("NativeMaxAir"):300);}
    public int recoverTissueAction(int normal){
        if(level().isClientSide||!tissueMetabolismActive())return normal;
        var recovery=vitals.recover(normal,vitalsProfile(),tissueNutrition(),getHealth()/Math.max(.001,getMaxHealth()));
        if(recovery.spent()>0){spendTissueNutrition(recovery.spent());publishVitals();}return recovery.ticks();
    }
    private TissueDigestion digestion=new TissueDigestion();
    private long nextTissueForage;
    public TissueDigestion digestion(){return digestion;}
    public CompoundTag digestiveVisual(){return entityData.get(TISSUE_DIGESTION).copy();}
    public TissueDigestion.Profile digestiveProfile(){return new TissueDigestion.Profile(organPower(TissueCore.GIZZARD),organPower(TissueCore.FERMENT),organPower(TissueCore.ABSORPTIVE),organPower(TissueCore.PANCREAS),organPower(TissueCore.CROP),organPower(TissueCore.FAT),organPower(TissueCore.GILL_RAKER));}
    private void publishDigestion(){if(!level().isClientSide){var visual=digestion.visual(digestiveProfile());if(!visual.equals(entityData.get(TISSUE_DIGESTION)))entityData.set(TISSUE_DIGESTION,visual);}}
    public boolean offerTissueFood(TissueDigestion.Kind kind,int nutrition){
        if(level().isClientSide||!isAlive()||hasMultipartBody()||devourerIdentity()||!digestion.offer(kind,nutrition,digestiveProfile()))return false;
        publishDigestion();return true;
    }
    private void depositTissueNutrition(int nutrition){entityData.set(TISSUE_NUTRITION,digestion.deposit(tissueNutrition(),nutrition,digestiveProfile()));publishDigestion();}
    @Override protected net.minecraft.world.InteractionResult mobInteract(Player player,InteractionHand hand){
        if(TissueFeeding.food(this,player.getItemInHand(hand))!=null){
            if(level().isClientSide)return net.minecraft.world.InteractionResult.SUCCESS;
            if(TissueFeeding.playerFeed(this,player,hand))return net.minecraft.world.InteractionResult.CONSUME;
        }return super.mobInteract(player,hand);
    }
    private static final EntityDataAccessor<CompoundTag> TISSUE_INHERITANCE=SynchedEntityData.defineId(BlacklightInfectedHost.class,EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<Integer> CANNIBAL_TARGET=SynchedEntityData.defineId(BlacklightInfectedHost.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> CANNIBAL_STARTED=SynchedEntityData.defineId(BlacklightInfectedHost.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Boolean> CANNIBAL_PREY=SynchedEntityData.defineId(BlacklightInfectedHost.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> CANNIBAL_DURATION=SynchedEntityData.defineId(BlacklightInfectedHost.class,EntityDataSerializers.INT);
    private TissueInheritance inheritanceCache;
    private static final EntityDataAccessor<CompoundTag> TISSUE_ACTION=SynchedEntityData.defineId(BlacklightInfectedHost.class,EntityDataSerializers.COMPOUND_TAG);
    private final com.codex.blacklight.infection.TissueAbilities organAbilities=new com.codex.blacklight.infection.TissueAbilities(this);
    public com.codex.blacklight.infection.TissueAbilities organAbilities(){return organAbilities;}
    public TissueCore organActionCore(){return TissueCore.byId(entityData.get(TISSUE_ACTION).getString("Core"));}
    public int organActionTarget(){return entityData.get(TISSUE_ACTION).getInt("Target");}
    public Vec3 organActionDirection(){var tag=entityData.get(TISSUE_ACTION);return new Vec3(tag.getDouble("X"),0,tag.getDouble("Z"));}
    public float organActionElapsed(float partial){return organActionCore()==null?-1:level().getGameTime()-entityData.get(TISSUE_ACTION).getLong("Started")+partial;}
    public void setOrganAction(TissueCore core,int target,Vec3 direction){
        if(level().isClientSide)return;var tag=new CompoundTag();
        if(core!=null){clearTissueStrike();tag.putString("Core",core.key());tag.putInt("Target",target);tag.putLong("Started",level().getGameTime());tag.putDouble("X",direction.x);tag.putDouble("Z",direction.z);}
        entityData.set(TISSUE_ACTION,tag);
    }
    /** Keep the same paid action and target, but begin its synchronized closing phase. */
    public void retractOrganAction(){
        var core=organActionCore();if(level().isClientSide||core==null)return;
        int closing=com.codex.blacklight.infection.TissueOrganCycle.cycle(core).retract();if(organActionElapsed(0)>=closing)return;
        var tag=entityData.get(TISSUE_ACTION).copy();tag.putLong("Started",level().getGameTime()-closing);entityData.set(TISSUE_ACTION,tag);
    }
    public boolean spendTissueNutrition(int amount){if(level().isClientSide||amount<0||tissueNutrition()<amount)return false;entityData.set(TISSUE_NUTRITION,tissueNutrition()-amount);return true;}
    public TissueInheritance tissueInheritance(){
        if(inheritanceCache==null)inheritanceCache=TissueInheritanceCodec.read(entityData.get(TISSUE_INHERITANCE));
        return inheritanceCache;
    }
    private void publishInheritance(TissueInheritance value){
        if(value==tissueInheritance())return;
        var tag=TissueInheritanceCodec.write(value);tag.putLong("SampledAt",level().getGameTime());
        entityData.set(TISSUE_INHERITANCE,tag);inheritanceCache=value;
    }
    public double tissueInheritanceVisualWork(float partial){
        if(!level().isClientSide)return 0;
        return Math.clamp(level().getGameTime()+partial-entityData.get(TISSUE_INHERITANCE).getLong("SampledAt"),0,20)*(tissueNutrition()>0?1:.2);
    }
    public void inheritTissue(UUID operation,TissueInheritance donor,long seed,long age){
        inheritTissue(operation,donor,seed,age,-1);
    }
    public void inheritTissue(UUID operation,TissueInheritance donor,long seed,long age,int donorPlan){
        inheritTissue(operation,donor,seed,age,donorPlan,TissueCore.forSeed(seed));
    }
    public void inheritTissue(UUID operation,TissueInheritance donor,long seed,long age,int donorPlan,TissueCore donorCore){
        inheritTissue(operation,donor,seed,age,donorPlan,donorCore,"");
    }
    public void inheritTissue(UUID operation,TissueInheritance donor,long seed,long age,int donorPlan,TissueCore donorCore,String donorSpecies){
        inheritTissue(operation,donor,seed,age,donorPlan,donorCore,donorSpecies,"");
    }
    public void inheritTissue(UUID operation,TissueInheritance donor,long seed,long age,int donorPlan,TissueCore donorCore,String donorSpecies,String donorAppearance){
        if(level().isClientSide||!isAlive())return;
        var before=tissueInheritance();var after=before.inherit(operation,donor,TissueOrgans.seed(getUUID()),tissueAge(),seed,age,TissueCompetitionConfig.rules(),donorPlan,knownNativeTissueCore(),donorCore,donorSpecies,donorAppearance);
        if(before==after)return;
        publishInheritance(after);
        depositTissueNutrition(24);
    }
    /** Bounded source anatomy descriptor. Unknown species use structural proportions, not an ID whitelist. */
    public int nativeTissuePlan(){
        if(humanoid())return 0;if(quadruped()||!family().equals("ground"))return 1;
        float width=hostData().getFloat("Width"),height=hostData().getFloat("Height");
        return width>height*1.3F?2:height>width*1.8F?0:1;
    }
    public TissueEvolution.State tissueEvolution(boolean authored){
        return tissueEvolution(authored,0);
    }
    public TissueEvolution.State tissueEvolution(boolean authored,float partial){
        long seed=TissueOrgans.seed(getUUID());
        return tissueInheritance().appearance(TissueEvolution.nativeState(nativeTissueCore(),seed,tissueAge(),authored),nativeTissueCore(),tissueInheritanceVisualWork(partial));
    }
    public TissueCore nativeTissueCore(){var core=TissueCore.byId(hostData().getString("NativeCore"));return core==null?TissueCore.forSeed(TissueOrgans.seed(getUUID())):core;}
    /** Unknown future IDs keep their save identity but cannot mint a random functional organ. */
    public TissueCore knownNativeTissueCore(){return hostData().getString("NativeCore").isBlank()?nativeTissueCore():TissueCore.byId(hostData().getString("NativeCore"));}
    public double organPower(TissueCore core){
        double strength=tissueInheritance().strength(core,knownNativeTissueCore(),tissueAge());
        return tissueInheritance().maturity(core,knownNativeTissueCore(),tissueAge())<1?0:Math.log1p(strength/100)/Math.log(2);
    }
    public float organExpression(TissueCore core,float partial){
        var organ=tissueInheritance().organ(core);
        float born=core==knownNativeTissueCore()?Math.max(core.legacy()?0:.35F*TissueGrowth.progress(tissueAge()),TissueEvolution.attachmentGrowth(tissueAge())):0;
        return Math.max(born,com.codex.blacklight.infection.TissueGraftLayout.growth(organ,tissueInheritanceVisualWork(partial)));
    }
    public int cannibalTargetId(){return entityData.get(CANNIBAL_TARGET);}
    public long cannibalStarted(){return entityData.get(CANNIBAL_STARTED);}
    public boolean cannibalPrey(){return entityData.get(CANNIBAL_PREY);}
    public int cannibalDuration(){return entityData.get(CANNIBAL_DURATION);}
    public void setCannibalVisual(int target,long started,boolean prey){
        setCannibalVisual(target,started,prey,TissueCompetition.END_TICKS);
    }
    public void setCannibalVisual(int target,long started,boolean prey,int duration){
        if(level().isClientSide)return;
        if(target>=0)clearTissueStrike();
        entityData.set(CANNIBAL_TARGET,target);entityData.set(CANNIBAL_STARTED,started);entityData.set(CANNIBAL_PREY,prey);
        entityData.set(CANNIBAL_DURATION,Math.clamp(duration,40,200));
    }
    public long tissueAge() { return entityData.get(TISSUE_AGE); }
    public int tissueNutrition() { return entityData.get(TISSUE_NUTRITION); }
    public float tissueCoreFracture() { return entityData.get(TISSUE_CORE_FRACTURE); }
    /** Accepted, space-checked development. Multipart encounters keep their separate evolution system. */
    public float tissueBodyScale() { return hasMultipartBody()?1:entityData.get(TISSUE_BODY_SCALE); }
    public float tissueTentacles() {
        // Grip is a forelimb function. Native feet must not disappear when an unrelated donor has grasping wrists.
        return 0;
    }
    /** Folded native feet stay inside the unchanged collider; new limbs support the lowered torso. */
    public float tissueStanceOffset() { return isEvolved()?0:-getBbHeight()*.10F*tissueTentacles(); }
    public int tissueStrikeTargetId() { return entityData.get(TISSUE_STRIKE_TARGET); }
    public long tissueGripReleaseAt() { return entityData.get(TISSUE_GRIP_RELEASE); }
    public Vec3 tissueStrikePoint() { var p=entityData.get(TISSUE_STRIKE_POINT);return new Vec3(p.x,p.y,p.z); }
    public float tissueStrikeElapsed(float partial) {
        long start=entityData.get(TISSUE_STRIKE_STARTED);
        return start<0?-1:level().getGameTime()-start+partial;
    }
    public float tissueOrganReach() {
        return TissueOrgans.reach(TissueOrgans.seed(getUUID()),tissueAge(),getBbWidth(),getBbHeight(),tissueBodyScale(),tissueEvolution(false));
    }
    public void advanceTissue(long ticks) {
        if (!level().isClientSide && isAlive()) {
            entityData.set(TISSUE_AGE,com.codex.blacklight.infection.TissueGrowth.advance(tissueAge(),ticks));
            growTissueBody();
            fitTissueFrame();
            updateTissueCombat();
        }
    }
    private boolean tissueMetabolismActive() {
        return !hasMultipartBody()&&!devourerIdentity()&&!DevourerFeedingSystem.isDevourer(this);
    }
    private void updateTissueCombat() {
        var growth=tissueMetabolismActive()?TissueEvolution.combat(TissueOrgans.seed(getUUID()),tissueAge(),nativeTissueCore()):new TissueEvolution.Combat(0,0,0,0);
        var inherited=tissueMetabolismActive()?tissueInheritance().combat():new TissueEvolution.Combat(0,0,0,0);
        var frame=tissueFrame();double vitality=tissueMetabolismActive()?tissueBodyPlan().vitality(frame.x,frame.y,frame.z):1;
        growth=new TissueEvolution.Combat((1+growth.health()+inherited.health())*vitality-1,growth.damage()+inherited.damage(),growth.armor()+inherited.armor(),growth.knockback()+inherited.knockback());
        float maximum=getMaxHealth(),health=getHealth();
        CombatScale.modifier(getAttribute(Attributes.MAX_HEALTH),TISSUE_HEALTH,growth.health(),AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        CombatScale.modifier(getAttribute(Attributes.ATTACK_DAMAGE),TISSUE_DAMAGE,growth.damage(),AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        CombatScale.modifier(getAttribute(Attributes.ARMOR),TISSUE_ARMOR,growth.armor(),AttributeModifier.Operation.ADD_VALUE);
        CombatScale.modifier(getAttribute(Attributes.KNOCKBACK_RESISTANCE),TISSUE_KNOCKBACK,growth.knockback(),AttributeModifier.Operation.ADD_VALUE);
        com.codex.blacklight.infection.EvolvedBalance.apply(this);
        CombatScale.preserveHealthFraction(this,maximum,health);
    }
    private void digestTissue() {
        int elapsed=tissueClock;tissueClock=0;
        if(tissueMetabolismActive()){
            var processed=digestion.advance(elapsed,tissueNutrition(),digestiveProfile());entityData.set(TISSUE_NUTRITION,processed.reserve());publishDigestion();
        }
        var genes=tissueInheritance();
        boolean integrating=genes.organs().values().stream().anyMatch(o->o.work()>0||!o.mature())||genes.sources().values().stream().anyMatch(s->s.work()>0);
        boolean repairing=tissueRecoveryTicks==0&&getHealth()<getMaxHealth()-.01F&&!getData(ModAttachments.CONSUME_VISUAL).active();
        double worldSpeed=level() instanceof ServerLevel server?TissueMetabolism.worldSpeed(server.getServer().overworld().getDayTime()):1;
        int demand=tissueMetabolismActive()?TissueMetabolism.demand(tissueAge(),integrating,repairing,worldSpeed):0;
        var budget=TissueMetabolism.budget(tissueNutrition(),tissueNutritionDebt,demand);
        boolean supplied=budget.supplied();tissueNutritionDebt=budget.debt();entityData.set(TISSUE_NUTRITION,budget.reserve());
        if(tissueMetabolismActive()){
            var developed=tissueInheritance().digest(TissueMetabolism.scaledTicks(elapsed,worldSpeed*(.2+.8*budget.fraction())),true,TissueOrgans.seed(getUUID()),tissueAge(),TissueCompetitionConfig.rules(),knownNativeTissueCore());
            if(supplied&&developed.organs().values().stream().anyMatch(o->o.mature()&&o.pending()==0&&o.integrated()>0)){
                var cultivation=TissueMetabolism.cultivation(tissueCultivationTicks,TissueMetabolism.scaledTicks(elapsed,worldSpeed*budget.fraction()));
                tissueCultivationTicks=cultivation.remainder();
                developed=developed.cultivate(cultivation.work(),knownNativeTissueCore(),tissueAge(),TissueCompetitionConfig.rules());
            }
            publishInheritance(developed);
        }
        long natural=TissueMetabolism.scaledTicks(elapsed,worldSpeed),bonus=supplied&&TissueMetabolism.growing(tissueAge(),integrating)?TissueMetabolism.scaledTicks(TissueMetabolism.BONUS_TICKS,worldSpeed*budget.fraction()):0;
        advanceTissue(com.codex.blacklight.infection.TissueGrowth.advance(natural,bonus));
        if(supplied&&repairing){
            heal((float)(getMaxHealth()*.005F*budget.fraction()/CombatScale.units(this)));vitals.repaired(budget.fraction());
        }
    }
    /** Vanilla invokes this only after its death event accepts the kill. A
     * rejected death, infection conversion, or live victim cannot pay a meal. */
    @Override public boolean killedEntity(ServerLevel server,LivingEntity victim) {
        boolean accepted=super.killedEntity(server,victim);
        if(!accepted||!isAlive()||!tissueMetabolismActive()||!(victim instanceof Mob)||victim.isAlive()
                ||victim.getKillCredit()!=this||BlacklightInfectionSystem.isInfected(victim)
                ||victim.getPersistentData().getBoolean(TISSUE_MEAL))return accepted;
        int meal=TissueMetabolism.meal(CombatScale.originalMaximum(victim),nativeTissueCore());
        if(meal<=0)return accepted;
        victim.getPersistentData().putBoolean(TISSUE_MEAL,true);
        int previous=tissueNutrition();
        if(digestiveProfile().digestive()||organPower(TissueCore.EXTRAORAL)>0)offerTissueFood(TissueDigestion.Kind.MEAT,meal);else depositTissueNutrition(meal);
        if(tissueNutrition()>previous)server.sendParticles(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(.38F,.025F,.035F),1.1F),
                getX(),getY()+getBbHeight()*.55,getZ(),10,getBbWidth()*.2,getBbHeight()*.15,getBbWidth()*.2,.025);
        return accepted;
    }
    private void growTissueBody() {
        if(hasMultipartBody())return;
        float current=tissueBodyScale(),desired=desiredTissueScale();
        if(desired<=current+.0001F)return;
        var candidate=EntityDimensions.scalable(getBbWidth()*desired/current,getBbHeight()*desired/current).makeBoundingBox(position());
        // Evolution continues in confined spaces, but a bigger body waits until
        // there is room. Never force a host into a wall or above build height.
        if(isAddedToLevel()&&(!level().noCollision(this,candidate)||!level().getWorldBorder().isWithinBounds(candidate)
                ||candidate.maxY>level().getMaxBuildHeight()))return;
        entityData.set(TISSUE_BODY_SCALE,desired);
    }
    private float desiredTissueScale(){return isEvolved()?com.codex.blacklight.infection.EvolvedForm.scale(tissueInheritance()):TissueEvolution.desiredScale(tissueAge())*tissueInheritance().scaleFactor();}
    private void fitTissueFrame(){
        if(!tissueMetabolismActive()||bossProfile()!=null)return;
        var plan=tissueBodyPlan();var old=tissueFrame();var next=new org.joml.Vector3f(plan.width(),plan.height(),plan.depth());
        if(old.distanceSquared(next)<.000001F)return;
        float width=getBbWidth()*Math.max(next.x,next.z)/Math.max(old.x,old.z),height=getBbHeight()*next.y/old.y;
        var box=EntityDimensions.scalable(width,height).makeBoundingBox(position());
        if(isAddedToLevel()&&(!level().noCollision(this,box)||!level().getWorldBorder().isWithinBounds(box)||box.maxY>level().getMaxBuildHeight()))return;
        entityData.set(TISSUE_FRAME,next);
    }

    public BlacklightInfectedHost(EntityType<? extends BlacklightInfectedHost> type,Level level) {
        super(type,level);xpReward=8;
        for(int i=0;i<parts.length;i++)parts[i]=new InfectedHostPart(this);
        setId(ENTITY_COUNTER.getAndAdd(PART_COUNT+1)+1);
        configureParts();
    }
    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,30).add(Attributes.ATTACK_DAMAGE,5)
                .add(Attributes.MOVEMENT_SPEED,.30).add(Attributes.FLYING_SPEED,.35).add(Attributes.FOLLOW_RANGE,24)
                .add(Attributes.STEP_HEIGHT,1).add(Attributes.KNOCKBACK_RESISTANCE,0).add(Attributes.ARMOR_TOUGHNESS,0);
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);b.define(TISSUE_FRAME,new org.joml.Vector3f(1));b.define(TISSUE_AGE,0L);b.define(TISSUE_BODY_SCALE,1F);b.define(TISSUE_CORE_FRACTURE,0F);b.define(TISSUE_STRIKE_TARGET,-1);b.define(TISSUE_STRIKE_STARTED,-1L);b.define(TISSUE_GRIP_RELEASE,-1L);b.define(TISSUE_STRIKE_POINT,new org.joml.Vector3f());b.define(HOST,new CompoundTag());b.define(FEED_TARGET,-1);b.define(FEED_STARTED,-1L);b.define(DEVOURER,false);b.define(NURSERY,new CompoundTag());b.define(FEED_NODE,-1);
        b.define(TISSUE_NUTRITION,0);b.define(TISSUE_DIGESTION,new CompoundTag());b.define(TISSUE_VITALS,new CompoundTag());b.define(TISSUE_VASCULAR,new CompoundTag());b.define(TISSUE_DEFENSE,new CompoundTag());
        b.define(TISSUE_INHERITANCE,new CompoundTag());b.define(CANNIBAL_TARGET,-1);b.define(CANNIBAL_STARTED,-1L);b.define(CANNIBAL_PREY,false);b.define(CANNIBAL_DURATION,TissueCompetition.END_TICKS);b.define(TISSUE_ACTION,new CompoundTag());
        b.define(EVOLVED_STATE,new CompoundTag());b.define(EVOLVED_ACTION,new CompoundTag());b.define(NATIVE_ACTION,new CompoundTag());
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
        CompoundTag data=new CompoundTag();data.putInt("NativeCombatVersion",1);
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
        if(source instanceof net.minecraft.world.entity.monster.RangedAttackMob)data.putString("NativeCapability","ranged");
        data.putBoolean("NativeClimbs",source instanceof net.minecraft.world.entity.monster.Spider);
        data.putBoolean("NativeHops",source instanceof Slime||source instanceof net.minecraft.world.entity.animal.Rabbit);
        TissueVitalSystem.inherit(data,source);
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
        if(source instanceof BlacklightCommonInfected civilian)visual.putInt("BlacklightCivilianAppearance",civilian.appearance());
        data.put("Visual",visual);
        // Summoned draft dragons must never claim or finish the natural End encounter.
        if(source instanceof EnderDragon dragon&&level() instanceof ServerLevel server
                &&server.getDragonFight()!=null&&source.getUUID().equals(server.getDragonFight().getDragonUUID())) {
            data.putUUID("DragonFight",source.getUUID());
            data.putInt("DragonOriginX",dragon.getFightOrigin().getX());data.putInt("DragonOriginZ",dragon.getFightOrigin().getZ());
        }
        entityData.set(HOST,data);
        data=data.copy();data.putString("NativeCore",TissueCore.forNewHost(TissueOrgans.seed(getUUID()),nativeTissuePlan(),family.equals("water")).key());
        entityData.set(HOST,data);setAirSupply(Math.clamp(source.getAirSupply(),-20,getMaxAirSupply()));refreshDimensions();
        float originalHealth=CombatScale.originalMaximum(source);
        if(!level().isClientSide&&!hasMultipartBody()&&tissueAge()==0&&tissueNutrition()==0)
            entityData.set(TISSUE_NUTRITION,TissueMetabolism.initialReserve(originalHealth));
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(colossal?profile.health():Math.max(18,originalHealth*1.4));
        double originalAttack=source.getAttribute(Attributes.ATTACK_DAMAGE)==null?0:source.getAttributeValue(Attributes.ATTACK_DAMAGE);
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(colossal?profile.damage():Math.max(Math.clamp(originalHealth*.14,4,18),originalAttack*1.3));
        if(source.getAttribute(Attributes.MOVEMENT_SPEED)!=null)getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(Math.max(.22,source.getAttributeValue(Attributes.MOVEMENT_SPEED)*1.08));
        if(source.getAttribute(Attributes.FLYING_SPEED)!=null)getAttribute(Attributes.FLYING_SPEED).setBaseValue(Math.max(.35,source.getAttributeValue(Attributes.FLYING_SPEED)*1.08));
        for(var attribute:List.of(Attributes.ARMOR,Attributes.ARMOR_TOUGHNESS,Attributes.KNOCKBACK_RESISTANCE,Attributes.STEP_HEIGHT))
            if(source.getAttribute(attribute)!=null&&getAttribute(attribute)!=null)getAttribute(attribute).setBaseValue(Math.max(getAttribute(attribute).getBaseValue(),source.getAttributeValue(attribute)));
        for(var slot:EquipmentSlot.values()){setItemSlot(slot,source.getItemBySlot(slot).copy());setDropChance(slot,0);}
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
        getAttribute(Attributes.STEP_HEIGHT).setBaseValue(2);
        xpReward=profile==InfectedBossProfile.ENDER_DRAGON?1000:150;noCulling=true;
    }
    private void applyCompactEncounterAttributes(InfectedBossProfile profile) {
        getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(64);
        getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        xpReward=profile==InfectedBossProfile.ENDER_DRAGON?500:50;
    }
    @Override public EntityDimensions getDefaultDimensions(Pose pose) {
        if(isEvolved())return EntityDimensions.scalable(.95F*1.12F*tissueBodyScale(),2.16F*1.12F*tissueBodyScale()).withEyeHeight(1.95F*1.12F*tissueBodyScale());
        var data=hostData();InfectedBossProfile profile=bossProfile();
        if(profile!=null)return EntityDimensions.scalable(profile.width()*bodyScale(),profile.height()*bodyScale())
                .withEyeHeight(profile.visualHeight()*bodyScale()*.77F);
        if(!data.contains("Width"))return super.getDefaultDimensions(pose);
        float width=Mth.clamp(data.getFloat("Width"),.2F,16),height=Mth.clamp(data.getFloat("Height"),.2F,16);
        if(nativeTissuePlan()==2){
            float upper=0;for(var entry:tissueInheritance().sources().entrySet())if(!entry.getKey().equals(speciesId())&&entry.getValue().plan()==0)
                upper=Math.max(upper,com.codex.blacklight.infection.TissueChimera.growth(entry.getValue(),0));
            height+=width*upper;
        }
        var frame=tissueFrame();return EntityDimensions.scalable(width*Math.max(frame.x,frame.z),height*frame.y).scale(tissueBodyScale());
    }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if(TISSUE_INHERITANCE.equals(key)){inheritanceCache=null;refreshDimensions();}
        if(TISSUE_BODY_SCALE.equals(key)||TISSUE_FRAME.equals(key))refreshDimensions();
        if(HOST.equals(key)) {
            appearance=null;appearanceAnimationTick=Integer.MIN_VALUE;appearanceSwing=false;appearanceAirborne=false;
            refreshDimensions();configureParts();
            // The client initially registered reserved IDs before HOST was received.
            // Drop unused parts once species is known; ordinary hordes do not need 20
            // extra entries in NeoForge's global multipart collision queries.
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
    /** Detached render proxy. Never registered in the world or ticked as server AI. */
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
        if(appearance instanceof net.minecraft.world.entity.ambient.Bat bat){
            bat.setResting(false);bat.restAnimationState.stop();bat.flyAnimationState.startIfStopped(tickCount);
        }
        // Native client animation fields include squid tentacles, fish flops, allay wings,
        // golem attack clocks and modern AnimationStates. One client tick, never one per frame.
        if(level().isClientSide){
            boolean airborne=!onGround()&&!isInWaterOrBubble();
            if(appearance instanceof net.minecraft.world.entity.animal.Rabbit rabbit&&airborne&&!appearanceAirborne&&getDeltaMovement().y>.025)rabbit.handleEntityEvent((byte)1);
            appearanceAirborne=airborne;
            if(appearance instanceof net.minecraft.world.entity.animal.frog.Frog frog)
                frog.setPose(airborne&&getDeltaMovement().lengthSqr()>.001?Pose.LONG_JUMPING:Pose.STANDING);
            if(appearance instanceof net.minecraft.world.entity.monster.breeze.Breeze breeze){
                breeze.setPose(nativeAction().equals("WIND")?(nativeActionElapsed(0)<nativeCombat.profile().contact?Pose.INHALING:Pose.SHOOTING)
                        :airborne?Pose.LONG_JUMPING:getDeltaMovement().horizontalDistanceSqr()>.002?Pose.SLIDING:Pose.STANDING);
                breeze.idle.startIfStopped(tickCount);
            }
            if(appearance instanceof net.minecraft.world.entity.monster.Shulker shulker)
                ((com.codex.blacklight.mixin.ShulkerPeekAccess)shulker).blacklight$peek(nativeAction().equals("SHULKER")?100:Math.floorMod(tickCount+getId(),160)<35?30:0);
            if(swinging&&!appearanceSwing&&(appearance instanceof net.minecraft.world.entity.animal.IronGolem||appearance instanceof net.minecraft.world.entity.monster.Ravager))appearance.handleEntityEvent((byte)4);
            appearanceSwing=swinging;appearance.tick();appearance.copyPosition(this);appearance.setDeltaMovement(getDeltaMovement());
            if(appearance instanceof net.minecraft.world.entity.animal.Squid squid){
                long time=level().getGameTime(),seed=getUUID().getLeastSignificantBits();
                squid.oldTentacleMovement=com.codex.blacklight.infection.NativeSpeciesMotion.squidPhase(time-1,seed);squid.tentacleMovement=com.codex.blacklight.infection.NativeSpeciesMotion.squidPhase(time,seed);
                squid.oldTentacleAngle=com.codex.blacklight.infection.NativeSpeciesMotion.squidTentacle(time-1,seed);squid.tentacleAngle=com.codex.blacklight.infection.NativeSpeciesMotion.squidTentacle(time,seed);
            }
        }
        // Air/water anatomy must keep flapping/swimming when a locomotor organ grows.
        appearance.walkAnimation.update(walkAnimation.speed()*(family().equals("ground")?1-.55F*tissueTentacles():1),.4F);
        appearance.setAggressive(getTarget()!=null||!nativeAction().isEmpty());
        if(appearance instanceof net.minecraft.world.entity.monster.warden.Warden warden){
            if(nativeAction().equals("SONIC"))warden.sonicBoomAnimationState.startIfStopped(tickCount-(int)nativeActionElapsed(0));else warden.sonicBoomAnimationState.stop();
            if(swinging)warden.attackAnimationState.startIfStopped(tickCount);else warden.attackAnimationState.stop();
        }
        if(appearance instanceof net.minecraft.world.entity.monster.Ghast ghast)ghast.setCharging(!nativeAction().isEmpty());
        if(appearance instanceof net.minecraft.world.entity.monster.Creeper creeper)creeper.setSwellDir(nativeAction().equals("CREEPER")?1:-1);
        if(appearance instanceof net.minecraft.world.entity.monster.AbstractSkeleton skeleton)skeleton.setAggressive(!nativeAction().isEmpty());
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
        return TissueCompetition.contender(this,target)||!BlacklightInfectionSystem.isInfected(target);
    }
    @Override protected void registerGoals() {
        targetSelector.addGoal(0,new NearestAttackableTargetGoal<>(this,Player.class,5,true,false,target->isEvolved()&&validTarget(target)){{setUnseenMemoryTicks(200);}});
        targetSelector.addGoal(1,new net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal(this){
            @Override public boolean canUse(){return getLastHurtByMob()!=null&&validTarget(getLastHurtByMob())&&super.canUse();}
            @Override public boolean canContinueToUse(){return getTarget()!=null&&validTarget(getTarget())&&super.canContinueToUse();}
        });
        goalSelector.addGoal(0,new FloatGoal(this){
            @Override public boolean canUse(){return !family().equals("water")&&super.canUse();}
        });
        goalSelector.addGoal(1,new Goal() {
            { setFlags(java.util.EnumSet.of(Goal.Flag.MOVE,Goal.Flag.LOOK)); }
            @Override public boolean canUse() {
                boolean hull=com.codex.blacklight.military.vehicle.VehicleHullAttackGoal.handles(BlacklightInfectedHost.this);
                return isMetamorphosing()||!hull&&(!nativeAction().isEmpty()||isEvolved()&&getTarget()!=null||organActionCore()!=null)
                        ||vascular.active()||vascular.seeking()||DevourerFeedingSystem.controls(BlacklightInfectedHost.this)||TissueCompetition.controls(BlacklightInfectedHost.this);
            }
            @Override public boolean canContinueToUse() { return canUse(); }
        });
        goalSelector.addGoal(2,new MeleeAttackGoal(this,1.1,true) {
            @Override public boolean canUse() { return !isEvolved()&&!nativeCombat.ownsGroundCombat()&&!isMetamorphosing()&&!DevourerFeedingSystem.controls(BlacklightInfectedHost.this)&&family().equals("ground")&&super.canUse(); }
            @Override public boolean canContinueToUse() { return !getData(ModAttachments.CONSUME_VISUAL).active()&&super.canContinueToUse(); }
            @Override protected void checkAndPerformAttack(LivingEntity target) {
                if(!nativeAction().isEmpty()||nativeCombat.ownsGroundCombat()||organActionCore()!=null||vascular.active()||TissueCompetition.controls(BlacklightInfectedHost.this))return;
                if(target instanceof BlacklightInfectedHost prey&&TissueCompetition.tryStart(BlacklightInfectedHost.this,prey))return;
                if(organAbilities.tryStart(target))return;
                if(tissueStrikeElapsed(0)<0&&attackCooldown==0&&isWithinMeleeAttackRange(target)&&getSensing().hasLineOfSight(target)) {
                    resetAttackCooldown();swing(InteractionHand.MAIN_HAND);doHurtTarget(target);attackCooldown=recoverTissueAction(giantBoss()?40:20);
                }
            }
        });
        goalSelector.addGoal(6,new WaterAvoidingRandomStrollGoal(this,.8) {
            @Override public boolean canUse() { return !DevourerFeedingSystem.isDevourer(BlacklightInfectedHost.this)&&family().equals("ground")&&super.canUse(); }
        });
        targetSelector.addGoal(2,new NearestAttackableTargetGoal<>(this,LivingEntity.class,10,true,false,target->validTarget(target)&&!(target instanceof BlacklightInfectedHost)));
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
            if(navigation instanceof GroundPathNavigation ground)ground.setCanFloat(true);
        }
    }
    private Vec3 airDestination;
    private int airRoamCooldown;
    /** Idle fliers need actual flight steering; ground stroll goals never supply an air destination. */
    private void roamInAir(){
        if(!(level() instanceof ServerLevel server))return;
        getNavigation().stop();
        if(--airRoamCooldown<=0||airDestination==null||position().distanceToSqr(airDestination)<2||horizontalCollision){
            airRoamCooldown=25+random.nextInt(25);airDestination=null;
            for(int attempt=0;attempt<6;attempt++){
                var goal=position().add(random.nextDouble()*12-6,onGround()?2+random.nextDouble()*2:random.nextDouble()*4-1,random.nextDouble()*12-6);
                var box=getBoundingBox().move(goal.subtract(position()));
                if(!server.hasChunksAt(net.minecraft.core.BlockPos.containing(box.minX,box.minY,box.minZ),net.minecraft.core.BlockPos.containing(box.maxX,box.maxY,box.maxZ))
                        ||!server.noCollision(this,box)||server.containsAnyLiquid(box))continue;
                if(server.clip(new net.minecraft.world.level.ClipContext(getBoundingBox().getCenter(),goal.add(0,getBbHeight()*.5,0),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,this)).getType()!=net.minecraft.world.phys.HitResult.Type.MISS)continue;
                airDestination=goal;break;
            }
        }
        if(airDestination!=null){
            var aim=airDestination.subtract(position());
            setDeltaMovement(getDeltaMovement().scale(.85).add(aim.normalize().scale(.055)));
            setYRot(Mth.rotLerp(.15F,getYRot(),(float)(Mth.atan2(-aim.x,aim.z)*Mth.RAD_TO_DEG)));yBodyRot=getYRot();
        }
    }
    @Override public void travel(Vec3 input){
        setNoGravity(family().equals("air")||family().equals("water")&&isInWater());
        if(family().equals("water")&&isInWater()){
            if(isControlledByLocalInstance()){moveRelative(.025F,input);move(net.minecraft.world.entity.MoverType.SELF,getDeltaMovement());setDeltaMovement(getDeltaMovement().scale(.9));}
            calculateEntityAnimation(false);
        }else if(family().equals("air")&&!isInWater()&&!isInLava()){
            if(isControlledByLocalInstance()){
                moveRelative(.02F,input);move(net.minecraft.world.entity.MoverType.SELF,getDeltaMovement());
                setDeltaMovement(getDeltaMovement().scale(.91));
            }
            calculateEntityAnimation(false);
        }else super.travel(input);
    }
    @Override public void tick() {
        super.tick();updateParts();if(level().isClientSide)return;setupNavigation();
        TissueDefenseSystem.tick(this);
        if(tissueRecoveryTicks>0)tissueRecoveryTicks--;
        if(isAlive() && ++tissueClock>=20)digestTissue();
        if(isAlive()&&tissueMetabolismActive())nextTissueForage=TissueFeeding.scavenge(this,nextTissueForage);
        if(vascular.tick(this)){nativeCombat.interrupt();return;}
        entityData.set(DEVOURER,DevourerFeedingSystem.isDevourer(this));
        DevourerFeedingSystem.tick(this);
        tickBoss();
        if(tickEvolvedMorph())return;
        if(TissueCompetition.tick(this)){nativeCombat.interrupt();if(organActionCore()!=null)organAbilities.interrupt();return;}
        if(hasMultipartBody())setAirSupply(getMaxAirSupply());
        if(getData(ModAttachments.CONSUME_VISUAL).active()||com.codex.blacklight.combat.ImpactControl.stunned(this)) { nativeCombat.interrupt();organAbilities.interrupt();evolvedCombat.interruptForHit();clearTissueStrike();getNavigation().stop();return; }
        // The same lawful living target now occupies a real mechanical hull. Release old
        // living-only actions before their contact callbacks, without stopping hull approach every tick.
        if(com.codex.blacklight.military.vehicle.VehicleHullAttackGoal.handles(this)){
            if(!nativeAction().isEmpty())nativeCombat.interrupt();
            if(organActionCore()!=null)organAbilities.interrupt();
            if(!evolvedAction().isEmpty()||evolvedEngaged())evolvedCombat.interrupt();
            if(attackCooldown>0)attackCooldown--;
            clearTissueStrike();return;
        }
        if(isEvolved()&&evolvedCombat.tick())return;
        if(nativeCombat.tick())return;
        if(organAbilities.tick())return;
        if(attackCooldown>0)attackCooldown--;
        if(tickTissueStrike())return;
        if(!isAlive()||isNoAi()||level().getDifficulty()==net.minecraft.world.Difficulty.PEACEFUL||family().equals("ground"))return;
        var target=getTarget();if(target==null||!validTarget(target)){if(family().equals("air"))roamInAir();else if(family().equals("water"))roamInWater();return;}
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
            Vec3 steering=direction;
            if(family().equals("air")){
                if(direction.horizontalDistanceSqr()>9)steering=steering.add(0,Math.max(1.5,getBbHeight()),0);
                if(onGround())steering=steering.add(0,2,0);
                setYRot(Mth.rotLerp(.15F,getYRot(),(float)(Mth.atan2(-direction.x,direction.z)*Mth.RAD_TO_DEG)));yBodyRot=getYRot();
            }
            if(hasLineOfSight(target))setDeltaMovement(getDeltaMovement().scale(.85).add(steering.normalize().scale(.055)));
            if(direction.lengthSqr()<Math.pow(getBbWidth()+target.getBbWidth()+.6,2)&&hasLineOfSight(target)&&attackCooldown==0) {
                swing(InteractionHand.MAIN_HAND);doHurtTarget(target);attackCooldown=recoverTissueAction(24);
            }
        }
    }
    private void clearTissueStrike() {
        com.codex.blacklight.infection.TissueGrapples.release(this);
        entityData.set(TISSUE_GRIP_RELEASE,-1L);
        entityData.set(TISSUE_STRIKE_STARTED,-1L);entityData.set(TISSUE_STRIKE_TARGET,-1);
    }
    private void roamInWater(){
        if(!isInWater()||!(level() instanceof ServerLevel server))return;
        if(--airRoamCooldown<=0||airDestination==null||position().distanceToSqr(airDestination)<.5||horizontalCollision){
            airRoamCooldown=25+random.nextInt(25);airDestination=null;
            for(int i=0;i<6;i++){var goal=position().add(random.nextDouble()*7-3.5,random.nextDouble()*3-1.5,random.nextDouble()*7-3.5);var pos=net.minecraft.core.BlockPos.containing(goal);
                if(server.hasChunkAt(pos)&&server.getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER)&&server.noCollision(this,getBoundingBox().move(goal.subtract(position())))){airDestination=goal;break;}
            }
        }
        if(airDestination!=null){var aim=airDestination.subtract(position());setDeltaMovement(getDeltaMovement().scale(.85).add(aim.normalize().scale(.045)));setYRot(Mth.rotLerp(.12F,getYRot(),(float)(Mth.atan2(-aim.x,aim.z)*Mth.RAD_TO_DEG)));yBodyRot=getYRot();}
    }
    private void rememberTissueContact(LivingEntity target) {
        Vec3 centre=getBoundingBox().getCenter();AABB box=target.getBoundingBox();
        Vec3 point=new Vec3(Mth.clamp(centre.x,box.minX,box.maxX),Mth.clamp(centre.y,box.minY,box.maxY),Mth.clamp(centre.z,box.minZ,box.maxZ)).subtract(position());
        double limit=getBbWidth()+tissueOrganReach()*2+2;
        if(point.length()>limit)point=point.normalize().scale(limit);
        entityData.set(TISSUE_STRIKE_POINT,new org.joml.Vector3f((float)point.x,(float)point.y,(float)point.z));
    }
    private boolean inTissueRange(LivingEntity target) {
        float reach=tissueOrganReach();
        return reach>0&&getBoundingBox().inflate(reach,reach*.65,reach).intersects(target.getBoundingBox());
    }
    /** Server-owned windup/contact/retraction. Neither render frames nor attached mesh count deal damage. */
    private boolean tickTissueStrike() {
        if(!isAlive()||isNoAi()||hasMultipartBody()||devourerIdentity()
                ||level().getDifficulty()==net.minecraft.world.Difficulty.PEACEFUL) { clearTissueStrike();return false; }
        int elapsed=(int)tissueStrikeElapsed(0);
        if(elapsed>=0) {
            var entity=level().getEntity(tissueStrikeTargetId());
            if(elapsed>=TissueOrgans.DURATION||elapsed<=TissueOrgans.HIT&&(!(entity instanceof LivingEntity target)||!validTarget(target))) {
                clearTissueStrike();return false;
            }
            getNavigation().stop();
            if(entity instanceof LivingEntity target) {
                getLookControl().setLookAt(target,30,30);
                if(elapsed==TissueOrgans.HIT) {
                    if(target instanceof BlacklightInfectedHost prey&&TissueCompetition.tryStart(this,prey))return true;
                    rememberTissueContact(target);
                    if(inTissueRange(target)&&hasLineOfSight(target)){
                        swing(InteractionHand.MAIN_HAND);
                        if(doHurtTarget(target)&&organPower(TissueCore.GRASP)>0&&com.codex.blacklight.infection.TissueGrapples.grab(this,target,TissueOrgans.GRAB_RELEASE-TissueOrgans.HIT))
                            entityData.set(TISSUE_GRIP_RELEASE,level().getGameTime()+TissueOrgans.GRAB_RELEASE-TissueOrgans.HIT);
                    }
                }
                if(elapsed>TissueOrgans.HIT) {
                    if(com.codex.blacklight.infection.TissueGrapples.owner(target)==this)rememberTissueContact(target);
                    else if(tissueGripReleaseAt()>level().getGameTime())entityData.set(TISSUE_GRIP_RELEASE,level().getGameTime());
                }
            }
            return true;
        }
        var target=getTarget();
        if(attackCooldown==0&&target!=null&&validTarget(target)&&inTissueRange(target)&&hasLineOfSight(target)) {
            rememberTissueContact(target);
            entityData.set(TISSUE_STRIKE_TARGET,target.getId());entityData.set(TISSUE_STRIKE_STARTED,level().getGameTime());
            attackCooldown=TissueOrgans.DURATION+recoverTissueAction(18);getNavigation().stop();return true;
        }
        return false;
    }
    private void basicBossProjectile(LivingEntity target) {
        Vec3 origin=position().add(0,bossProfile().visualHeight()*bodyScale()*.8,0);
        for(InfectedHostPart part:parts)if(part.active()&&part.partName().equals("head")){origin=part.getBoundingBox().getCenter();break;}
        Vec3 aim=target.getBoundingBox().getCenter().subtract(origin).normalize();
        origin=origin.add(aim.scale(Math.max(.6,7*bodyScale())));
        Entity projectile=bossProfile()==InfectedBossProfile.WITHER?new WitherSkull(level(),this,aim) {
            @Override protected boolean canHitEntity(Entity target) { return !target.is(BlacklightInfectedHost.this)&&super.canHitEntity(target); }
        }:new DragonFireball(level(),this,aim) {
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
        bossBar.setProgress(Math.clamp(getHealth()/Math.max(1,getMaxHealth()),0,1));
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
    @Override protected void dropCustomDeathLoot(ServerLevel level,DamageSource source,boolean recentlyHit) {
        super.dropCustomDeathLoot(level,source,recentlyHit);
        // This hook already observes doMobLoot, drop capture, and LivingDrops cancellation.
        if(bossProfile()==InfectedBossProfile.WITHER)spawnAtLocation(net.minecraft.world.item.Items.NETHER_STAR);
    }
    @Override public void knockback(double strength,double x,double z){
        if(!evolvedControlProtected()&&!TissueCompetition.controls(this))super.knockback(strength,x,z);
    }
    /** Run after vanilla's same-hit cooldown comparison, on the accepted difference.
     * NeoForge uses its container rather than the method argument as the damage source of truth. */
    @Override protected void actuallyHurt(DamageSource source,float amount){
        float incoming=level().isClientSide?amount:damageContainers.peek().getNewDamage();
        float shielded=level().isClientSide?incoming:evolvedCombat.protect(source,incoming),shieldReduction=Math.max(0,incoming-shielded);
        var protection=level().isClientSide?new TissueDefense.Decision(amount,0,0):TissueDefenseSystem.incoming(this,source,shielded);
        if(!level().isClientSide)damageContainers.peek().setNewDamage(protection.damage());
        float before=getHealth(),absorbed=getAbsorptionAmount(),maximum=getMaxHealth();
        super.actuallyHurt(source,protection.damage());
        if(!level().isClientSide&&(getHealth()<before||getAbsorptionAmount()<absorbed)){defense.accepted(protection,maximum,level().getGameTime());evolvedCombat.acceptedShield(shieldReduction);publishDefense();}
    }
    @Override public boolean hurt(DamageSource source,float amount) {
        if(evolvedCombat.evading()&&!source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY))return false;
        if(encounterBoss()&&source.getEntity()==this)return false;
        float before=getHealth(),maximum=getMaxHealth();boolean accepted=super.hurt(source,amount);
        if(accepted&&!level().isClientSide&&getHealth()<before){
            tissueRecoveryTicks=TissueMetabolism.RECOVERY_DELAY;TissueCompetition.damaged(this,before-getHealth());
            vitals.damaged((before-getHealth())/Math.max(.001,maximum));
            vascular.interrupt(this);
            if(before-getHealth()>=maximum*.025F)tissueRecoveryTicks=recoverTissueAction(tissueRecoveryTicks);
            publishVitals();
            if(before-getHealth()>=maximum*.1F)organAbilities.interrupt();
            if(before-getHealth()>=maximum*.1F&&isMetamorphosing()&&!isEvolved())publishEvolvedState(3);
        }
        if(accepted&&!level().isClientSide&&!hasMultipartBody()&&com.codex.blacklight.infection.TissueCore.quality(tissueAge())>=2&&maximum>0){
            float loss=Math.max(0,before-getHealth());
            float durability=nativeTissueCore()==TissueCore.CARAPACE?.5F:.3F;
            float old=tissueCoreFracture(),next=Math.clamp(old+loss/maximum/durability,0,1);
            entityData.set(TISSUE_CORE_FRACTURE,next);
            if(old<1&&next>=1&&level() instanceof ServerLevel server){
                server.sendParticles(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(.38F,.025F,.035F),1.4F),getX(),getY()+getBbHeight()*.6,getZ(),16,getBbWidth()*.3,getBbHeight()*.2,getBbWidth()*.3,.05);
            }
        }
        if(accepted&&!level().isClientSide&&getHealth()<before)TissueCompetition.captureAfterContact(this,source);
        return accepted;
    }
    @Override public void remove(RemovalReason reason) {
        if(!level().isClientSide){DevourerFeedingSystem.release(this);TissueCompetition.release(this);}
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
        tag.put("BlacklightNativeCombat",nativeCombat.save());
        tag.put("BlacklightEvolvedSpecials",evolvedCombat.saveSpecials());
        var evolution=entityData.get(EVOLVED_STATE).copy();evolution.putInt("Elapsed",evolvedMorphTicks);evolution.remove("SampledAt");tag.put("BlacklightEvolvedState",evolution);
        var frame=tissueFrame();tag.putFloat("BlacklightTissueFrameX",frame.x);tag.putFloat("BlacklightTissueFrameY",frame.y);tag.putFloat("BlacklightTissueFrameZ",frame.z);
        tag.put("BlacklightTissueInheritance",TissueInheritanceCodec.write(tissueInheritance()));
        tag.putInt("BlacklightTissueNutrition",tissueNutrition());tag.putInt("BlacklightTissueNutritionDebt",tissueNutritionDebt);tag.putInt("BlacklightTissueRecovery",tissueRecoveryTicks);
        tag.putInt("BlacklightTissueCultivation",tissueCultivationTicks);
        tag.put("BlacklightTissueDigestion",digestion.write());
        tag.put("BlacklightTissueVitals",vitals.write());
        tag.put("BlacklightTissueDefense",defense.write());
        tag.put("BlacklightTissueVascular",vascular.write());
        super.addAdditionalSaveData(tag);tag.putLong("BlacklightTissueAge",com.codex.blacklight.infection.TissueGrowth.advance(tissueAge(),tissueClock));tag.putFloat("BlacklightTissueScale",tissueBodyScale());tag.putFloat("BlacklightCoreFracture",tissueCoreFracture());tag.put("BlacklightHost",hostData().copy());tag.putBoolean("BlacklightBossDefeated",bossDefeated);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        nativeCombat.load(tag.getCompound("BlacklightNativeCombat"));
        evolvedCombat.loadSpecials(tag.getCompound("BlacklightEvolvedSpecials"));
        var evolution=tag.getCompound("BlacklightEvolvedState").copy();evolvedMorphTicks=Math.clamp(evolution.getInt("Elapsed"),0,com.codex.blacklight.infection.EvolvedForm.DURATION);evolution.putInt("Elapsed",evolvedMorphTicks);evolution.putLong("SampledAt",level().getGameTime());
        if(evolution.getInt("Stage")<0||evolution.getInt("Stage")>3)evolution.putInt("Stage",0);
        evolution.putBoolean("Combat",false);evolution.putLong("CombatAt",level().getGameTime()-20);entityData.set(EVOLVED_STATE,evolution);entityData.set(EVOLVED_ACTION,new CompoundTag());
        float frameX=tag.contains("BlacklightTissueFrameX")?tag.getFloat("BlacklightTissueFrameX"):1,frameY=tag.contains("BlacklightTissueFrameY")?tag.getFloat("BlacklightTissueFrameY"):1,frameZ=tag.contains("BlacklightTissueFrameZ")?tag.getFloat("BlacklightTissueFrameZ"):1;
        entityData.set(TISSUE_FRAME,new org.joml.Vector3f(Float.isFinite(frameX)?Math.clamp(frameX,.75F,2):1,Float.isFinite(frameY)?Math.clamp(frameY,.75F,2):1,Float.isFinite(frameZ)?Math.clamp(frameZ,.75F,2):1));
        if(organActionCore()!=null)organAbilities.interrupt();
        if(!level().isClientSide){com.codex.blacklight.infection.TissueGrapples.release(this);clearTissueStrike();}
        if(!level().isClientSide)TissueCompetition.interrupt(this);
        entityData.set(TISSUE_INHERITANCE,tag.getCompound("BlacklightTissueInheritance").copy());inheritanceCache=null;
        setCannibalVisual(-1,-1,false);
        entityData.set(TISSUE_NUTRITION,TissueMetabolism.clamp(tag.getInt("BlacklightTissueNutrition")));
        tissueNutritionDebt=Math.clamp(tag.getInt("BlacklightTissueNutritionDebt"),0,TissueMetabolism.WORK_PER_UNIT-1);
        tissueCultivationTicks=Math.clamp(tag.getInt("BlacklightTissueCultivation"),0,TissueMetabolism.CULTIVATION_BATCH-1);
        tissueRecoveryTicks=Math.clamp(tag.getInt("BlacklightTissueRecovery"),0,TissueMetabolism.RECOVERY_DELAY);
        float fracture=tag.getFloat("BlacklightCoreFracture");entityData.set(TISSUE_CORE_FRACTURE,Float.isFinite(fracture)?Math.clamp(fracture,0,1):0);
        super.readAdditionalSaveData(tag);entityData.set(TISSUE_AGE,com.codex.blacklight.infection.TissueGrowth.clamp(tag.getLong("BlacklightTissueAge")));tissueClock=0;bossDefeated=tag.getBoolean("BlacklightBossDefeated");CompoundTag data=tag.getCompound("BlacklightHost").copy();
        // /summon without host NBT historically displayed a pig proxy but passed
        // an empty species ID to the new morphology selector. Persist the same
        // species/anatomy metadata that potion conversion supplies, without
        // replacing command-provided attributes, health or visual variants.
        if(data.getString("Type").isBlank()) {
            CompoundTag defaults=new CompoundTag();
            defaults.putString("Family","ground");defaults.putBoolean("Quadruped",true);
            defaults.putBoolean("Humanoid",false);defaults.putBoolean("Colossal",false);
            defaults.putInt("FormVersion",2);
            defaults.putFloat("Width",EntityType.PIG.getWidth()*1.12F);
            defaults.putFloat("Height",EntityType.PIG.getHeight()*1.12F);
            defaults.merge(data);defaults.putString("Type","minecraft:pig");data=defaults;
        }
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
        if(!data.contains("NativeCore"))data.putString("NativeCore",TissueCore.forSeed(TissueOrgans.seed(getUUID())).key());
        TissueVitalSystem.migrate(this,data);
        if(!data.getBoolean("Evolved")&&data.getInt("NativeCombatVersion")<1){
            // Old hosts capped every native bite/punch at 18. Restore known native
            // baselines once, without replacing earned attributes or refilling health.
            double nativeAttack=switch(data.getString("Type")){case "minecraft:warden"->30;case "minecraft:iron_golem"->15;case "minecraft:ravager"->12;default->0;};
            var attack=getAttribute(Attributes.ATTACK_DAMAGE);if(attack!=null&&nativeAttack>0)attack.setBaseValue(Math.max(attack.getBaseValue(),nativeAttack*1.3));
            if(profile==InfectedBossProfile.WARDEN||profile==InfectedBossProfile.IRON_GOLEM)getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
            data.putInt("NativeCombatVersion",1);
        }
        entityData.set(HOST,data);
        float savedScale=tag.getFloat("BlacklightTissueScale");
        entityData.set(TISSUE_BODY_SCALE,hasMultipartBody()||!Float.isFinite(savedScale)?1F
                :Math.clamp(savedScale,1F,desiredTissueScale()));
        if(isEvolved())getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(Math.max(48,getAttributeValue(Attributes.FOLLOW_RANGE)));
        digestion=TissueDigestion.read(tag.getCompound("BlacklightTissueDigestion"));publishDigestion();nextTissueForage=level().getGameTime()+Math.floorMod(getId(),40);
        vitals=TissueVitals.read(tag.getCompound("BlacklightTissueVitals"));vitalsProfile=null;publishVitals();
        defense=TissueDefense.read(tag.getCompound("BlacklightTissueDefense"));publishDefense();
        vascular=com.codex.blacklight.infection.TissueVascular.read(tag.getCompound("BlacklightTissueVascular"));setVascularVisual(new CompoundTag());
        updateTissueCombat();refreshDimensions();setPersistenceRequired();
    }
}
