package com.codex.blacklight.infection;

import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/** Full stable IDs on disk and wire. Schema 1's short names migrate without rerolling organs. */
public final class TissueInheritanceCodec {
    private TissueInheritanceCodec(){}
    public static CompoundTag write(TissueInheritance value){
        var tag=new CompoundTag();tag.putInt("Schema",TissueInheritance.SCHEMA);tag.putLong("Revision",value.revision());
        tag.putLong("Meals",value.meals());tag.putDouble("Points",value.points());tag.putBoolean("Qualified",value.qualified());
        tag.putLong("AcquiredOrgans",value.acquiredOrgans());
        if(value.lastOperation()!=null)tag.putUUID("LastOperation",value.lastOperation());
        var organs=new CompoundTag();value.dormant().forEach((id,organ)->organs.put(id,slot(organ)));
        value.organs().forEach((core,organ)->organs.put(core.key(),slot(organ)));tag.put("Organs",organs);
        var sources=new CompoundTag();value.sources().forEach((id,body)->{
            var source=new CompoundTag();source.putInt("Plan",body.plan());source.putLong("Variant",body.variant());source.putDouble("Work",body.work());source.putLong("Portions",body.portions());source.putLong("Age",body.age());source.putString("NativeCore",body.nativeCore());source.put("Authored",state(body.authored()));source.put("Procedural",state(body.procedural()));
            source.putString("Appearance",body.appearance());var genes=new CompoundTag();body.genes().forEach((core,organ)->genes.put(core.key(),slot(organ)));source.put("Genes",genes);sources.put(id,source);
        });tag.put("BodySources",sources);return tag;
    }
    private static CompoundTag state(TissueEvolution.State s){
        var t=new CompoundTag();t.putLong("Generation",s.generation());t.putFloat("Muscle",s.muscle());t.putFloat("Carapace",s.carapace());t.putFloat("Jaw",s.jaw());t.putFloat("Grasp",s.grasp());t.putFloat("Crest",s.crest());
        var a=s.aggregate();t.putFloat("Aggregate",a.amount());t.putFloat("Radial",a.radial());t.putFloat("Quadruped",a.quadruped());t.putFloat("Upright",a.upright());t.putFloat("Handedness",a.handedness());return t;
    }
    private static TissueEvolution.State state(CompoundTag t){return new TissueEvolution.State(t.getLong("Generation"),t.getFloat("Muscle"),t.getFloat("Carapace"),t.getFloat("Jaw"),t.getFloat("Grasp"),t.getFloat("Crest"),new TissueAggregate.Form(t.getFloat("Aggregate"),t.getFloat("Radial"),t.getFloat("Quadruped"),t.getFloat("Upright"),t.getFloat("Handedness")));}
    private static CompoundTag slot(TissueInheritance.Organ organ){
        var slot=new CompoundTag();slot.putDouble("Integrated",organ.integrated());slot.putDouble("Pending",organ.pending());
        slot.putDouble("Work",organ.work());slot.putDouble("MaturityWork",organ.maturityWork());
        slot.putInt("Weapons",organ.weapons());slot.putLong("Variant",organ.variant());slot.putInt("DonorPlan",organ.donorPlan());return slot;
    }
    public static TissueInheritance read(CompoundTag tag){
        int schema=tag.getInt("Schema");if(schema<1||schema>TissueInheritance.SCHEMA)return TissueInheritance.EMPTY;
        var slots=new EnumMap<TissueCore,TissueInheritance.Organ>(TissueCore.class);var dormant=new TreeMap<String,TissueInheritance.Organ>();
        var organs=tag.getCompound("Organs");
        for(String id:new TreeSet<>(organs.getAllKeys()))if(organs.contains(id,10)){
            var key=ResourceLocation.tryParse(id.indexOf(':')<0?"blacklight:"+id:id);if(key==null)continue;
            var slot=organs.getCompound(id);var organ=new TissueInheritance.Organ(slot.getDouble("Integrated"),slot.getDouble("Pending"),slot.getDouble("Work"),
                    slot.getDouble("MaturityWork"),slot.getInt("Weapons"),slot.getLong("Variant"),slot.contains("DonorPlan")?slot.getInt("DonorPlan"):-1);
            var core=TissueCore.byId(key.toString());if(core!=null)slots.put(core,organ);else dormant.put(key.toString(),organ);
        }
        var operation=tag.hasUUID("LastOperation")?tag.getUUID("LastOperation"):null;
        long meals=tag.getLong("Meals"),acquired=tag.contains("AcquiredOrgans",99)?tag.getLong("AcquiredOrgans"):TissueInheritance.legacyAcquiredCount(slots,meals);
        var sources=new TreeMap<String,TissueInheritance.BodySource>();var bodies=tag.getCompound("BodySources");
        for(String id:new TreeSet<>(bodies.getAllKeys()))if(TissueInheritance.validSpecies(id)&&bodies.contains(id,10)){
            var source=bodies.getCompound(id);var genes=new EnumMap<TissueCore,TissueInheritance.Organ>(TissueCore.class);var g=source.getCompound("Genes");
            for(var core:TissueCore.values())if(g.contains(core.key(),10)){
                var o=g.getCompound(core.key());genes.put(core,new TissueInheritance.Organ(o.getDouble("Integrated"),o.getDouble("Pending"),o.getDouble("Work"),o.getDouble("MaturityWork"),o.getInt("Weapons"),o.getLong("Variant"),o.getInt("DonorPlan")));
            }
            sources.put(id,new TissueInheritance.BodySource(source.getInt("Plan"),source.getLong("Variant"),source.getDouble("Work"),source.getLong("Portions"),source.getLong("Age"),source.getString("NativeCore"),state(source.getCompound("Authored")),state(source.getCompound("Procedural")),genes,source.getString("Appearance")));
        }
        return new TissueInheritance(slots,meals,tag.getDouble("Points"),tag.getBoolean("Qualified"),operation,tag.getLong("Revision"),acquired,dormant,sources);
    }
}
