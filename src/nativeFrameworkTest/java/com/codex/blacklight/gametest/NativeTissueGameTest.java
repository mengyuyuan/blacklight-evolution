package com.codex.blacklight.gametest;

import com.codex.blacklight.config.InfectionEcologyConfig;
import com.codex.blacklight.infection.*;
import com.codex.blacklight.registry.ModAttachments;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraftforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class NativeTissueGameTest {
    private static final String DATA="BlacklightNativeInfection";
    @GameTest(template="wall_test",timeoutTicks=60,batch="native_tissue_state")
    public static void legacyAgeAndSixDonationStateSurviveNativeSaveWithoutReplacingAi(GameTestHelper h){
        var old=InfectionEcologyConfig.PRESERVE.get();InfectionEcologyConfig.PRESERVE.set(List.of("minecraft:cow"));
        Cow actor=h.spawn(EntityType.COW,4,2,4);actor.setNoAi(true);float nativeMaximum=actor.getMaxHealth();
        var navigation=actor.getNavigation();var control=actor.getMoveControl();var brain=actor.getBrain();var uuid=actor.getUUID();
        try{
            h.assertTrue(BlacklightInfectionSystem.infect(actor,null),"native fixture infection succeeds");
            var legacy=new CompoundTag();legacy.putLong("Age",48000);legacy.putDouble("Fraction",.375);legacy.putString("Core",TissueCore.MUSCLE.key());
            actor.getPersistentData().put(DATA,legacy);actor.getPersistentData().putString("ExternalFixtureState","retained");
            NativeInfection.tick(actor,h.getLevel());
            h.assertTrue(NativeInfection.age(actor)==48000&&actor.getPersistentData().getCompound(DATA).getDouble("Fraction")==.375,
                    "migration keeps loaded age and fractional work rather than resetting or catching up offline");
            TissueCore[] donated={TissueCore.CARAPACE,TissueCore.JAW,TissueCore.GRASP,TissueCore.CREST,TissueCore.BLADE,TissueCore.CHELA};
            for(int i=0;i<donated.length;i++){
                var operation=new UUID(123,i+1);
                h.assertTrue(NativeTissue.inherit(actor,operation,TissueInheritance.EMPTY,500+i,480000,1,donated[i],"fixture:donor_"+i),"mature component donation is accepted");
                h.assertTrue(!NativeTissue.inherit(actor,operation,TissueInheritance.EMPTY,500+i,480000,1,donated[i],"fixture:donor_"+i),"immediate duplicate operation is not awarded twice");
            }
            h.assertTrue(NativeTissue.inheritance(actor).meals()==6&&NativeTissue.inheritance(actor).acquiredOrgans()==6,
                    "six distinct mature organ donations are retained; this tests state settlement, not autonomous capture AI");
            h.assertTrue(actor.getUUID().equals(uuid)&&actor.getNavigation()==navigation&&actor.getMoveControl()==control&&actor.getBrain()==brain,
                    "no fake native entity or replacement controller is created");
            h.runAfterDelay(21,()->{
                Cow restored=EntityType.COW.create(h.getLevel());
                try{
                    var saved=new CompoundTag();actor.saveWithoutId(saved);restored.load(saved);NativeInfection.tick(restored,h.getLevel());
                    h.assertTrue(NativeTissue.inheritance(restored).meals()==6&&NativeTissue.inheritance(restored).sources().size()==6,"native genetics survives full entity serialization");
                    h.assertTrue(restored.getPersistentData().getString("ExternalFixtureState").equals("retained"),"foreign persistent state survives");
                    h.assertTrue(Math.abs(restored.getMaxHealth()-actor.getMaxHealth())<.01,"genetic/age health modifiers do not compound on reload: before="+actor.getMaxHealth()+" after="+restored.getMaxHealth()+" ages="+NativeInfection.age(actor)+"/"+NativeInfection.age(restored));
                    var wire=NativeTissue.snapshot(actor).getCompound("Genes");
                    h.assertTrue(TissueInheritanceCodec.read(wire).acquiredOrgans()==6,"wire projection retains logical acquired counts");
                    restored.removeEffect(com.codex.blacklight.registry.ModEffects.BLACKLIGHT_INFECTION.get());NativeInfection.clear(restored);
                    h.assertTrue(Math.abs(restored.getMaxHealth()-nativeMaximum)<.001&&NativeTissue.inheritance(restored).organs().isEmpty(),"cure removes only owned development and bonuses");
                    h.succeed();
                }finally{restored.discard();actor.discard();InfectionEcologyConfig.PRESERVE.set(old);}
            });
        }catch(RuntimeException|Error error){actor.discard();InfectionEcologyConfig.PRESERVE.set(old);throw error;}
    }
    @GameTest(template="wall_test",batch="native_tissue_unknown_ids")
    public static void unknownGenesRemainOnDiskAndPresentationHasABoundedSourceBudget(GameTestHelper h){
        var dormant=new TreeMap<String,TissueInheritance.Organ>();
        var bodies=new TreeMap<String,TissueInheritance.BodySource>();
        for(int i=0;i<100;i++){
            dormant.put("future:organ_"+i,new TissueInheritance.Organ(1000+i,0,0,0,0,i));
            bodies.put("future:species_"+i,new TissueInheritance.BodySource(1,i,0,999));
        }
        var genes=new TissueInheritance(Map.of(),100,9000,false,UUID.randomUUID(),3,100,dormant,bodies);
        var disk=TissueInheritanceCodec.read(TissueInheritanceCodec.write(genes));
        h.assertTrue(disk.dormant().size()==100&&disk.sources().size()==100,"unknown identities and all logical ancestry remain on disk");
        var wireTag=NativeTissue.wireGenes(genes);var wire=TissueInheritanceCodec.read(wireTag);
        h.assertTrue(wire.dormant().size()==64&&wire.sources().size()==8&&wire.acquiredOrgans()==100&&wire.meals()==100,
                "network mesh budget does not clamp evolution levels or meal counts");
        h.assertTrue(wire.organs().isEmpty()&&wireTag.toString().length()<40000,"unknown IDs do not mint random functions or unbounded packets");
        var buffer=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try{
            NativeTissueCodec.STREAM_CODEC.encode(buffer,wireTag);
            h.assertTrue(buffer.readableBytes()<=NativeTissueCodec.MAX_BYTES+3,"actual native NBT packet stays within the presentation budget");
            h.assertTrue(NativeTissueCodec.STREAM_CODEC.decode(buffer).equals(wireTag),"real framework codec decodes the bounded projection without quota failure");
        }finally{buffer.release();}
        h.succeed();
    }

    @GameTest(template="wall_test",timeoutTicks=30,batch="native_tissue_cure")
    public static void cureReconcilesOwnedModifiersOnTheNextAvailableEntityTick(GameTestHelper h){
        var old=InfectionEcologyConfig.PRESERVE.get();InfectionEcologyConfig.PRESERVE.set(List.of("minecraft:cow"));
        var cow=h.spawn(EntityType.COW,4,2,4);cow.setNoAi(true);float nativeMaximum=cow.getMaxHealth();
        try{
            BlacklightInfectionSystem.infect(cow,null);NativeInfection.tick(cow,h.getLevel());
            h.assertTrue(cow.getMaxHealth()>nativeMaximum,"fixture owns an infection health bonus");
            cow.removeEffect(com.codex.blacklight.registry.ModEffects.BLACKLIGHT_INFECTION.get());
            cow.invalidateCaps();
            try{
                NativeInfection.clear(cow);
                h.assertTrue(cow.getPersistentData().contains(DATA),"invalid capability does not delete unprocessed owned state");
            }finally{cow.reviveCaps();}
            h.runAfterDelay(2,()->{try{
                h.assertTrue(Math.abs(cow.getMaxHealth()-nativeMaximum)<.001&&!cow.getPersistentData().contains(DATA),"real post-tick cure removes orphaned native bonuses");
                h.assertTrue(NativeTissue.snapshot(cow).isEmpty(),"cured client presentation snapshot is cleared");h.succeed();
            }finally{cow.discard();InfectionEcologyConfig.PRESERVE.set(old);}});
        }catch(RuntimeException|Error error){cow.discard();InfectionEcologyConfig.PRESERVE.set(old);throw error;}
    }

    @GameTest(template="wall_test",batch="native_tissue_unknown_core")
    public static void unknownPrimaryCoreIsNotRerolledIntoAFunctionalKnownOrgan(GameTestHelper h){
        var old=InfectionEcologyConfig.PRESERVE.get();InfectionEcologyConfig.PRESERVE.set(List.of("minecraft:cow"));
        var cow=h.spawn(EntityType.COW,4,2,4);cow.setNoAi(true);
        try{
            BlacklightInfectionSystem.infect(cow,null);
            var state=new CompoundTag();state.putLong("Age",480000);state.putString("Core","future:missing_primary");
            cow.getPersistentData().put(DATA,state);NativeInfection.tick(cow,h.getLevel());
            h.assertTrue(NativeTissue.knownCore(cow)==null&&NativeTissue.coreId(cow).equals("future:missing_primary"),"unknown primary ID survives migration");
            var combat=NativeTissue.combat(cow);
            h.assertTrue(combat.health()==0&&combat.damage()==0&&combat.armor()==0,"unknown organ cannot mint random functional bonuses");
            h.assertTrue(NativeTissue.snapshot(cow).getString("Core").equals("future:missing_primary"),"client receives the same unknown identity");h.succeed();
        }finally{cow.discard();InfectionEcologyConfig.PRESERVE.set(old);}
    }

    @GameTest(template="wall_test",batch="native_tissue_wire_extremes")
    public static void maximumKnownInventoryAndLongUnknownIdsRoundtripAndOversizeFramesAreRejected(GameTestHelper h){
        var known=new EnumMap<TissueCore,TissueInheritance.Organ>(TissueCore.class);
        var unknown=new TreeMap<String,TissueInheritance.Organ>();var bodies=new TreeMap<String,TissueInheritance.BodySource>();
        var organ=new TissueInheritance.Organ(Double.MAX_VALUE,Double.MAX_VALUE,3600,3600,7,Long.MAX_VALUE,2);
        for(var core:TissueCore.values())known.put(core,organ);
        for(int i=0;i<100;i++){
            unknown.put("future:"+"o".repeat(240)+"_"+i,organ);
            bodies.put("future:"+"s".repeat(240)+"_"+i,new TissueInheritance.BodySource(2,i,3600,Long.MAX_VALUE));
        }
        var inventory=new TissueInheritance(known,Long.MAX_VALUE,Double.MAX_VALUE,true,UUID.randomUUID(),Long.MAX_VALUE,Long.MAX_VALUE,unknown,bodies);
        var snapshot=new CompoundTag();snapshot.put("Genes",NativeTissue.wireGenes(inventory));snapshot.putString("Core","future:"+"c".repeat(240));
        snapshot.putLong("Age",Long.MAX_VALUE);snapshot.putInt("Nutrition",2400);snapshot.putLong("SampledAt",Long.MAX_VALUE);
        var buffer=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try{
            NativeTissueCodec.STREAM_CODEC.encode(buffer,snapshot);
            h.assertTrue(buffer.readableBytes()<=NativeTissueCodec.MAX_BYTES+3,"the full known vocabulary fits the encoded budget");
            var decoded=NativeTissueCodec.STREAM_CODEC.decode(buffer);var restored=TissueInheritanceCodec.read(decoded.getCompound("Genes"));
            h.assertTrue(decoded.equals(snapshot)&&restored.organs().size()==TissueCore.values().length
                    &&restored.meals()==Long.MAX_VALUE&&restored.acquiredOrgans()==Long.MAX_VALUE,"no known organ or logical evolution count is pruned to fit the wire");
            buffer.clear();var oversized=new CompoundTag();oversized.putByteArray("Bad",new byte[NativeTissueCodec.MAX_BYTES+1]);
            boolean rejected=false;try{NativeTissueCodec.STREAM_CODEC.encode(buffer,oversized);}catch(io.netty.handler.codec.EncoderException expected){rejected=true;}
            h.assertTrue(rejected&&buffer.writerIndex()==0,"oversize encoding is rejected before publishing partial attachment bytes");
            buffer.writeVarInt(NativeTissueCodec.MAX_BYTES+1);rejected=false;
            try{NativeTissueCodec.STREAM_CODEC.decode(buffer);}catch(io.netty.handler.codec.DecoderException expected){rejected=true;}
            h.assertTrue(rejected,"oversize frame length is rejected before allocating NBT");h.succeed();
        }finally{buffer.release();}
    }
}
