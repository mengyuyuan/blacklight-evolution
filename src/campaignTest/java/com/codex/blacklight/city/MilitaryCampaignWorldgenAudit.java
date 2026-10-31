package com.codex.blacklight.city;

import java.util.*;
import java.nio.file.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
/** GameTestServer deliberately disables structures; use a real disposable dedicated server for worldgen. */
@EventBusSubscriber(modid="blacklight",value=net.minecraftforge.api.distmarker.Dist.DEDICATED_SERVER)
public final class MilitaryCampaignWorldgenAudit {
    private static boolean done;
    private static final List<Map<String,Object>> results=new ArrayList<>();
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ServerTickEvent event){if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        if(done||!Boolean.getBoolean("blacklight.campaignWorldgenAudit"))return;done=true;
        var server=event.getServer();
        try{
            check(server.isDedicatedServer()&&server.getWorldData().worldGenOptions().generateStructures(),"real dedicated world must have structure generation enabled");
            var city=server.getLevel(CityTravel.WORLD);check(city!=null,"actual Blacklight dimension exists");
            verify(city,true,2);verify(server.overworld(),false,1);
            report("PASS",null);server.halt(false);
        }catch(Throwable failure){report("FAIL",failure.toString());throw new IllegalStateException("Campaign native worldgen audit failed",failure);}
    }
    private static void report(String status,String error){
        try{Files.writeString(Path.of("campaign-worldgen.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(Map.of("status",status,"error",error==null?"":error,"nativeStarts",results)));}
        catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
    private static void check(boolean result,String message){if(!result)throw new AssertionError(message);}
    private static void verify(ServerLevel level,boolean city,int required){
        var structures=level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        var structure=structures.get(new ResourceLocation(city?"blacklight:city_military_base":"blacklight:military_field_base"));
        var set=level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).get(new ResourceLocation(city?"blacklight:city_military_bases":"blacklight:military_field_bases"));
        check(structure instanceof CityMilitaryStructure&&set.placement() instanceof RandomSpreadStructurePlacement,"actual registered native structure and spacing");
        var placement=(RandomSpreadStructurePlacement)set.placement();var factions=new HashSet<Boolean>();
        var generator=level.getChunkSource().getGenerator();int checked=0;
        for(int i=0;i<96&&factions.size()<required;i++){
            int rx=i%12,rz=i/12;
            var candidate=placement.getPotentialStructureChunk(level.getSeed(),rx*placement.spacing(),rz*placement.spacing());
            boolean blackwatch=((level.getSeed()^candidate.toLong())&1)!=0;if(factions.contains(blackwatch))continue;
            var context=new Structure.GenerationContext(level.registryAccess(),generator,generator.getBiomeSource(),level.getChunkSource().randomState(),level.getServer().getStructureManager(),level.getSeed(),candidate,level,b->true);
            if(((CityMilitaryStructure)structure).findGenerationPoint(context).isEmpty())continue;
            checked++;var chunk=level.getChunk(candidate.x,candidate.z);var start=chunk.getStartForStructure(structure);
            if(start==null||!start.isValid())continue;
            var pieces=start.getPieces().stream().filter(p->p instanceof CityMilitaryPiece).map(p->(CityMilitaryPiece)p).toList();
            check(pieces.size()==1,"native pipeline creates exactly one campaign compound");
            var site=pieces.get(0).site();check(site.blackwatch()==blackwatch,"native faction/layout is deterministic");
            int cores=0;
            for(var local:LargeBaseBlueprint.get(site.blackwatch()).commandCores()){
                var p=site.world(local[0],local[2]);var at=new BlockPos(p[0],site.floor()+local[1],p[1]);
                level.getChunk(at.getX()>>4,at.getZ()>>4);
                if(level.getBlockState(at).is(com.codex.blacklight.registry.MilitaryFacilityBlocks.COMMAND_TERMINAL.get()))cores++;
            }
            check(cores==6,"native cross-chunk compound retains all six real command blocks");
            factions.add(blackwatch);results.add(Map.of("dimension",level.dimension().location().toString(),"chunkX",candidate.x,"chunkZ",candidate.z,"blackwatch",blackwatch,"cores",cores,"examined",i,"generated",checked));
            System.out.println("CAMPAIGN_WORLDGEN_PASS "+results.get(results.size()-1));
        }
        check(factions.size()>=required,"native candidates must produce required distinct compounds in "+level.dimension().location()+", found="+factions+" generated="+checked);
    }
}
