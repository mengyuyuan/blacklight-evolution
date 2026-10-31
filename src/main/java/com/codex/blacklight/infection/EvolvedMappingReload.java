package com.codex.blacklight.infection;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.AddReloadListenerEvent;

/** Server datapack table; unknown future runtime types stay dormant instead of producing broken bodies. */
@EventBusSubscriber(modid="blacklight")
public final class EvolvedMappingReload {
    private static final String BUILTIN="/data/blacklight/blacklight/evolved_mapping/default.json";
    private static volatile EvolvedMapping.Table current;
    private static final Set<String> TYPES=EvolvedArchetype.types();
    private EvolvedMappingReload(){}
    public static EvolvedMapping.Table current(){
        var table=current;if(table!=null)return table;
        synchronized(EvolvedMappingReload.class){
            if(current==null)try(var input=EvolvedMappingReload.class.getResourceAsStream(BUILTIN)){
                if(input==null)throw new IllegalStateException("Missing built-in evolved mapping");
                try(var reader=new InputStreamReader(input,StandardCharsets.UTF_8)){current=EvolvedMapping.read(reader);}
            }catch(java.io.IOException error){throw new IllegalStateException(error);}
            return current;
        }
    }
    public static EvolvedMapping.Choice choose(TissueInheritance genes,UUID identity){
        return current().choose(genes,TissueGrowth.mix(identity.getMostSignificantBits()^identity.getLeastSignificantBits()),TYPES);
    }
    @SubscribeEvent public static void register(AddReloadListenerEvent event){
        event.addListener((ResourceManagerReloadListener)manager->{
            var labels=new TreeMap<String,String>();var organs=new TreeMap<String,Map<String,Double>>();var forms=new TreeMap<String,EvolvedMapping.Candidate>();String fallback=null;
            var resources=new TreeMap<>(manager.listResources("blacklight/evolved_mapping",id->id.getPath().endsWith(".json")));
            // Read the built-in layer first, then deterministic extension layers.
            var primary=new ResourceLocation("blacklight","blacklight/evolved_mapping/default.json");
            var ordered=new ArrayList<ResourceLocation>();if(resources.containsKey(primary))ordered.add(primary);
            for(var id:resources.keySet())if(!id.equals(primary))ordered.add(id);
            for(var id:ordered)try(var reader=resources.get(id).openAsReader()){
                var table=EvolvedMapping.read(reader);labels.putAll(table.biases());organs.putAll(table.organs());forms.putAll(table.forms());if(fallback==null)fallback=table.fallback();
            }catch(Exception error){throw new IllegalArgumentException("Invalid evolved mapping "+id,error);}
            if(fallback==null)throw new IllegalStateException("No evolved mapping supplied");
            var replacement=new EvolvedMapping.Table(labels,organs,forms,fallback);
            if(!TYPES.contains(replacement.forms().get(replacement.fallback()).type()))throw new IllegalArgumentException("Unavailable evolved fallback type");
            current=replacement;
        });
    }
}
