package com.codex.blacklight.infection;

import com.codex.blacklight.config.GameplayBalanceConfig;
import com.codex.blacklight.registry.ModAttachments;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

/** Blacklight-owned development values attached to the actual native mob.
 * Foreign NBT/capabilities are never copied to either persistence or the wire. */
public final class NativeTissue {
    private static final String[] CLOCKS={"Fraction","FedFraction","IntegrationFraction","CultivationFraction"};
    private record CachedGenes(CompoundTag token,long revision,TissueInheritance value) {}
    private static final Map<Mob,CachedGenes> GENES=Collections.synchronizedMap(new WeakHashMap<>());
    private NativeTissue() {}
    public static void forget(Mob mob){if(mob!=null){GENES.remove(mob);NativeTissueDigestion.forget(mob);NativeTissueVitals.forget(mob);NativeTissueDefense.forget(mob);NativeTissueFeeding.forget(mob);NativeTissueVascular.forget(mob);}}
    private static TissueInheritance readGenes(Mob mob,CompoundTag state){
        if(!state.contains("Genes",10))return TissueInheritance.EMPTY;
        var tag=state.getCompound("Genes");var cached=GENES.get(mob);long revision=tag.getLong("Revision");
        if(cached!=null&&cached.token()==tag&&cached.revision()==revision)return cached.value();
        var value=TissueInheritanceCodec.read(tag);GENES.put(mob,new CachedGenes(tag,revision,value));return value;
    }
    private static CompoundTag data(Mob mob){return mob.getPersistentData().getCompound(NativeInfection.DATA);}
    public static CompoundTag snapshot(Mob mob){
        return ModAttachments.peekData(mob,ModAttachments.NATIVE_TISSUE,new CompoundTag());
    }
    public static TissueInheritance inheritance(Mob mob){
        return readGenes(mob,mob.level().isClientSide?snapshot(mob):data(mob));
    }
    public static int nutrition(Mob mob){
        return TissueMetabolism.clamp((mob.level().isClientSide?snapshot(mob):data(mob)).getInt("Nutrition"));
    }
    public static String coreId(Mob mob){
        String id=(mob.level().isClientSide?snapshot(mob):data(mob)).getString("Core");
        return id.isBlank()?TissueCore.forNewHost(NativeInfection.seed(mob),0,
                mob instanceof net.minecraft.world.entity.animal.WaterAnimal).key():id;
    }
    public static TissueCore knownCore(Mob mob){return TissueCore.byId(coreId(mob));}
    public static TissueEvolution.Combat combat(Mob mob){
        var known=knownCore(mob);
        var nativeGrowth=known==null?new TissueEvolution.Combat(0,0,0,0)
                :TissueEvolution.combat(NativeInfection.seed(mob),NativeInfection.age(mob),known);
        var inherited=inheritance(mob).combat();
        return new TissueEvolution.Combat(nativeGrowth.health()+inherited.health(),nativeGrowth.damage()+inherited.damage(),
                nativeGrowth.armor()+inherited.armor(),nativeGrowth.knockback()+inherited.knockback());
    }

    private static void initialize(Mob mob,CompoundTag data){
        if(data.getInt("DevelopmentSchema")>=1)return;
        data.putInt("DevelopmentSchema",1);
        if(!data.contains("Nutrition",99))data.putInt("Nutrition",TissueMetabolism.initialReserve(mob.getMaxHealth()));
        if(data.getString("Core").isBlank())data.putString("Core",coreId(mob));
        // Old Age and Fraction stay intact. No reroll, free organs or offline time.
    }
    static long advance(Mob mob,ServerLevel level,CompoundTag data,int elapsed){
        initialize(mob,data);
        NativeTissueDigestion.advance(mob,data,elapsed);
        double[] fractions=new double[4];for(int i=0;i<4;i++)fractions[i]=data.getDouble(CLOCKS[i]);
        var genes=readGenes(mob,data);
        int recovery=Math.max(0,data.getInt("Recovery")-Math.max(0,elapsed));
        // Native mob damage can bypass ordinary hurt events. Observe the actual
        // post-tick health receipt too; this only delays our own food-paid repair.
        if(mob.hurtTime>0||data.contains("LastHealth",99)&&mob.getHealth()+.01F<data.getFloat("LastHealth"))
            recovery=TissueMetabolism.repairDelay();
        boolean repair=recovery==0&&mob.getHealth()<mob.getMaxHealth()-.01F;
        var step=TissueDevelopment.advance(TissueGrowth.clamp(data.getLong("Age")),genes,NativeInfection.seed(mob),
                knownCore(mob),data.getInt("Nutrition"),data.getInt("Debt"),data.getInt("Cultivation"),fractions,
                elapsed,TissueMetabolism.worldSpeed(level.getServer().overworld().getDayTime()),true,repair,TissueCompetitionConfig.rules());
        data.putLong("Age",step.age());data.putInt("Nutrition",step.nutrition());data.putInt("Debt",step.debt());
        data.putInt("Cultivation",step.cultivation());data.putInt("Recovery",recovery);
        fractions=step.fractions();for(int i=0;i<4;i++)data.putDouble(CLOCKS[i],fractions[i]);
        if(step.inheritance()!=genes)data.put("Genes",TissueInheritanceCodec.write(step.inheritance()));
        if(step.repairFraction()>0)mob.heal((float)(mob.getMaxHealth()
                *GameplayBalanceConfig.value(GameplayBalanceConfig.REPAIR_FRACTION)*step.repairFraction()
                /com.codex.blacklight.power.CombatScale.units(mob)));
        if(step.repairFraction()>0)NativeTissueVitals.repaired(mob,step.repairFraction());
        data.putFloat("LastHealth",mob.getHealth());
        return step.age();
    }
    public static boolean inherit(Mob actor,UUID operation,TissueInheritance donor,long donorSeed,long donorAge,
                                  int donorPlan,TissueCore donorCore,String species){
        return inherit(actor,operation,donor,donorSeed,donorAge,donorPlan,donorCore,species,"");
    }
    public static boolean inherit(Mob actor,UUID operation,TissueInheritance donor,long donorSeed,long donorAge,
                                  int donorPlan,TissueCore donorCore,String species,String appearance){
        if(actor==null||donor==null||actor.level().isClientSide||!actor.isAlive()||!ModAttachments.available(actor)||!NativeInfection.active(actor))return false;
        var data=data(actor);initialize(actor,data);
        var before=readGenes(actor,data);
        var after=before.inherit(operation,donor,NativeInfection.seed(actor),data.getLong("Age"),donorSeed,donorAge,
                TissueCompetitionConfig.rules(),donorPlan,knownCore(actor),donorCore,species,appearance);
        if(before==after)return false;
        data.put("Genes",TissueInheritanceCodec.write(after));data.putInt("Nutrition",TissueMetabolism.add(data.getInt("Nutrition"),24));
        actor.getPersistentData().put(NativeInfection.DATA,data);publish(actor,data);return true;
    }
    /** Ordinary accepted kills pay through the same reserve clamp as development. */
    public static void feed(Mob mob,int amount){
        if(mob==null||amount<=0||mob.level().isClientSide||!ModAttachments.available(mob)||!mob.isAlive()||!NativeInfection.active(mob))return;
        var data=data(mob);initialize(mob,data);data.putInt("Nutrition",NativeTissueDigestion.deposit(mob,data,amount));
        mob.getPersistentData().put(NativeInfection.DATA,data);publish(mob,data);
    }
    public static boolean spend(Mob mob,int amount){
        if(mob==null||amount<0||mob.level().isClientSide||!ModAttachments.available(mob)||!mob.isAlive()||!NativeInfection.active(mob))return false;
        var data=data(mob);initialize(mob,data);int reserve=TissueMetabolism.clamp(data.getInt("Nutrition"));
        if(reserve<amount)return false;
        data.putInt("Nutrition",reserve-amount);mob.getPersistentData().put(NativeInfection.DATA,data);publish(mob,data);return true;
    }
    /** Session presentation is transient: never add target IDs or claims to persistent NBT. */
    public static void scene(Mob mob,int target,long started,boolean prey,int duration){
        if(!ModAttachments.available(mob)||mob.level().isClientSide)return;
        var next=snapshot(mob).copy();next.putInt("CannibalTarget",target);next.putLong("CannibalStarted",started);
        next.putBoolean("CannibalPrey",prey);next.putInt("CannibalDuration",Math.max(1,Math.min(1200,duration)));
        if(!next.equals(snapshot(mob))){ModAttachments.setData(mob,ModAttachments.NATIVE_TISSUE,next);ModAttachments.syncData(mob,ModAttachments.NATIVE_TISSUE);}
    }
    public static void vascular(Mob mob,CompoundTag visual){
        if(!ModAttachments.available(mob)||mob.level().isClientSide)return;
        var next=snapshot(mob).copy();next.put("Vascular",visual.copy());
        if(!next.equals(snapshot(mob))){ModAttachments.setData(mob,ModAttachments.NATIVE_TISSUE,next);ModAttachments.syncData(mob,ModAttachments.NATIVE_TISSUE);}
    }
    public static void action(Mob mob,CompoundTag action){
        if(!ModAttachments.available(mob)||mob.level().isClientSide)return;
        var next=snapshot(mob).copy();next.put("OrganAction",action.copy());
        if(!next.equals(snapshot(mob))){ModAttachments.setData(mob,ModAttachments.NATIVE_TISSUE,next);ModAttachments.syncData(mob,ModAttachments.NATIVE_TISSUE);}
    }
    /** Compact presentation projection. Logical genes and levels remain intact on disk.
     * Visibility/source limits are mesh/network budgets, not evolution rank limits. */
    public static CompoundTag wireGenes(TissueInheritance genes){
        var dormant=new TreeMap<String,TissueInheritance.Organ>();
        genes.dormant().entrySet().stream().filter(e->e.getKey().length()<=256).sorted(Map.Entry.comparingByKey()).limit(64)
                .forEach(e->dormant.put(e.getKey(),e.getValue()));
        var bodies=new TreeMap<String,TissueInheritance.BodySource>();
        genes.sources().entrySet().stream().sorted(Map.Entry.comparingByKey()).limit(8).forEach(e->{
            var b=e.getValue();bodies.put(e.getKey(),new TissueInheritance.BodySource(b.plan(),b.variant(),b.work(),b.portions(),
                    b.age(),b.nativeCore(),b.authored(),b.procedural()));
        });
        return TissueInheritanceCodec.write(new TissueInheritance(genes.organs(),genes.meals(),genes.points(),genes.qualified(),
                genes.lastOperation(),genes.revision(),genes.acquiredOrgans(),dormant,bodies));
    }
    static void publish(Mob mob,CompoundTag data){
        if(!ModAttachments.available(mob))return;
        var next=snapshot(mob).copy();
        next.putInt("Schema",1);next.putLong("Age",data.getLong("Age"));
        String core=data.getString("Core");next.putString("Core",core.length()<=256?core:"blacklight:unavailable_native_core");
        next.putInt("Nutrition",TissueMetabolism.clamp(data.getInt("Nutrition")));
        next.put("Genes",wireGenes(readGenes(mob,data)));
        next.put("Digestion",NativeTissueDigestion.visual(mob,data));
        next.put("Vitals",NativeTissueVitals.visual(mob,data));
        next.put("Defense",NativeTissueDefense.visual(mob,data));
        next.putLong("SampledAt",mob.level().getGameTime());
        if(!next.equals(snapshot(mob))){ModAttachments.setData(mob,ModAttachments.NATIVE_TISSUE,next);ModAttachments.syncData(mob,ModAttachments.NATIVE_TISSUE);}
    }
}
