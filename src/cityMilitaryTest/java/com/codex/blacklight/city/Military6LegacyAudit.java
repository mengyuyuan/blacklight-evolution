package com.codex.blacklight.city;

import com.codex.blacklight.entity.MilitaryVehicle;
import com.codex.blacklight.military.vehicle.VehicleFleetSavedData;
import com.codex.blacklight.military.vehicle.VehicleGarageService;
import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Optional M6-on-M5 QA; the old audit owns chunk loading, restart assertions and saving halt. */
@EventBusSubscriber(modid="blacklight",value=net.minecraftforge.api.distmarker.Dist.DEDICATED_SERVER)
public final class Military6LegacyAudit {
    private static final Logger LOG=LoggerFactory.getLogger(Military6LegacyAudit.class);
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static final Path DIRECTORY=Path.of("").toAbsolutePath().normalize();
    private static final Path MANIFEST=DIRECTORY.resolve("military6-legacy-copy.json");
    private static final Path RESULT=DIRECTORY.resolve("military6-legacy-audit.json");
    private static JsonObject result,process,manifest;
    private static Instant started;

    @SubscribeEvent public static void starting(ServerAboutToStartEvent event){
        if(!Boolean.getBoolean("blacklight.military6LegacyAudit"))return;
        try{
            require(event.getServer() instanceof DedicatedServer,"Use normal runServer, never GameTestServer or a client");
            require(Boolean.getBoolean("blacklight.cityMilitaryAudit")&&Boolean.getBoolean("blacklight.militaryHomesAudit"),"Also load military5-homes-server.init.gradle");
            started=Instant.now();manifest=read(MANIFEST);
            require(manifest.get("schema").getAsInt()==1&&manifest.get("status").getAsString().equals("COPY_VERIFIED")
                    &&manifest.get("sourceUnchangedAfterCopy").getAsBoolean()&&manifest.get("copyHashesMatch").getAsBoolean(),"M5 copy was not hash-verified");
            require(Path.of(manifest.get("destination").getAsString()).toRealPath().equals(DIRECTORY.toRealPath()),"Wrong copied run directory");
            String phase=System.getProperty("blacklight.military6LegacyAudit.phase","");
            require(phase.equals("first")||phase.equals("second"),"Explicit first/second M6 process phase required");
            long pid=ProcessHandle.current().pid();
            String version=ModList.get().getModContainerById("blacklight").orElseThrow().getModInfo().getVersion().toString();
            require(version.contains("military.6"),"This must load the actual M6 mod: "+version);
            String manifestSha=sha(MANIFEST);
            if(phase.equals("first")){
                require(!Files.exists(RESULT),"Preserve existing M6 evidence; first requires a new M5 copy");
                result=new JsonObject();result.addProperty("schema",1);result.addProperty("copyId",manifest.get("copyId").getAsString());result.add("processes",new JsonArray());
            }else{
                var previous=read(RESULT);
                require(previous.get("schema").getAsInt()==1&&previous.get("copyId").equals(manifest.get("copyId"))
                        &&previous.get("status").getAsString().equals("FIRST_PROCESS_PASS")&&previous.getAsJsonArray("processes").size()==1,"Second requires exactly one successful M6 first process");
                var first=previous.getAsJsonArray("processes").get(0).getAsJsonObject();
                require(first.get("status").getAsString().equals("PASS")&&first.get("pid").getAsLong()!=pid,"Second requires a different OS PID");
                require(first.get("copyManifestSha256").getAsString().equals(manifestSha)&&first.get("modVersion").getAsString().equals(version),"Copy manifest or M6 version changed between processes");
                result=previous;
            }
            process=new JsonObject();process.addProperty("phase",phase);process.addProperty("pid",pid);process.addProperty("processToken",UUID.randomUUID().toString());
            process.addProperty("modVersion",version);process.addProperty("copyManifestSha256",manifestSha);process.addProperty("startedUtc",started.toString());process.addProperty("status","RUNNING");
            result.getAsJsonArray("processes").add(process);result.addProperty("status","RUNNING_"+phase.toUpperCase(java.util.Locale.ROOT));
            var before=audits();process.add("oldAuditsBefore",before);
            for(String key:new String[]{"city","homes"}){
                var observed=before.getAsJsonObject(key);require(observed.get("status").getAsString().equals("PASS"),"M5/previous M6 audit is incomplete: "+key);
                String expected=phase.equals("first")?manifest.getAsJsonObject("snapshot").getAsJsonObject("files").getAsJsonObject(filename(key)).get("sha256").getAsString()
                        :result.getAsJsonArray("processes").get(0).getAsJsonObject().getAsJsonObject("oldAuditsAfter").getAsJsonObject(key).get("sha256").getAsString();
                require(observed.get("sha256").getAsString().equals(expected),"Audit changed outside the recorded M6 process chain: "+key);
            }
            write(phase.equals("first"));
            LOG.info("MILITARY6_LEGACY_AUDIT RUNNING phase={} pid={} result={}",phase,pid,RESULT);
        }catch(Throwable failure){
            failed(failure);throw new IllegalStateException("M6 legacy audit initialization failed; preserve this directory",failure);
        }
    }

    @SubscribeEvent public static void stopping(ServerStoppingEvent event){
        if(!Boolean.getBoolean("blacklight.military6LegacyAudit")||process==null||!process.get("status").getAsString().equals("RUNNING"))return;
        try{
            MinecraftServer server=event.getServer();process.addProperty("stoppingEvent",true);process.addProperty("serverTicks",server.getTickCount());
            require(server instanceof DedicatedServer&&server.getTickCount()>=60,"No completed normal dedicated-server audit window");
            var dimensions=new JsonArray();process.add("loadedDimensions",dimensions);boolean selected=false,empty=true;
            for(var level:server.getAllLevels()){
                int vehicles=0;for(var entity:level.getAllEntities())if(entity instanceof MilitaryVehicle)vehicles++;
                int garages=VehicleGarageService.data(level).garages().size(),fleets=VehicleFleetSavedData.get(level).fleets().size();
                String dimension=level.dimension().location().toString();selected|=dimension.equals(manifest.get("dimension").getAsString());
                var observed=new JsonObject();observed.addProperty("dimension",dimension);observed.addProperty("vehicles",vehicles);observed.addProperty("garages",garages);observed.addProperty("fleets",fleets);dimensions.add(observed);
                empty&=vehicles==0&&garages==0&&fleets==0;
            }
            require(selected&&dimensions.size()>0,"The copied audit dimension is not loaded");
            var after=audits();process.add("oldAuditsAfter",after);
            for(String key:new String[]{"city","homes"}){
                var current=after.getAsJsonObject(key);var before=process.getAsJsonObject("oldAuditsBefore").getAsJsonObject(key);
                require(current.get("status").getAsString().equals("PASS")&&!current.get("sha256").equals(before.get("sha256"))
                        &&!Instant.parse(current.get("updatedUtc").getAsString()).isBefore(started),"Old "+key+" PASS was not freshly completed by this M6 process");
            }
            var home=after.getAsJsonObject("homes");
            require(!home.get("restartProcessId").equals(process.getAsJsonObject("oldAuditsBefore").getAsJsonObject("homes").get("restartProcessId"))
                    &&!home.get("restartProcessId").equals(home.get("firstProcessId")),"Home audit did not record a new restart process");
            require(empty,"M6 automatically created loaded vehicles, VehicleGarageData or VehicleFleetSavedData entries; see loadedDimensions");
            process.addProperty("status","PASS");process.addProperty("completedUtc",Instant.now().toString());
            result.addProperty("status",process.get("phase").getAsString().equals("first")?"FIRST_PROCESS_PASS":"PASS");write(false);
            LOG.info("MILITARY6_LEGACY_AUDIT {} phase={} pid={} dimensions={} result={}",result.get("status").getAsString(),process.get("phase").getAsString(),process.get("pid").getAsLong(),dimensions,RESULT);
        }catch(Throwable failure){failed(failure);}
    }

    private static String filename(String key){return key.equals("city")?"city-military-audit.json":"military-homes-audit.json";}
    private static JsonObject audits()throws Exception{
        var observed=new JsonObject();
        for(String key:new String[]{"city","homes"}){
            Path path=DIRECTORY.resolve(filename(key));var audit=read(path);var summary=new JsonObject();
            for(String field:new String[]{"status","updatedUtc","dimension"})summary.add(field,audit.get(field));
            require(audit.get("dimension").equals(manifest.get("dimension")),"Original audit dimension changed: "+key);
            if(key.equals("homes"))for(String field:new String[]{"firstProcessId","restartProcessId"})summary.add(field,audit.get(field));
            summary.addProperty("sha256",sha(path));observed.add(key,summary);
        }
        return observed;
    }
    private static JsonObject read(Path path)throws IOException{return JsonParser.parseString(Files.readString(path,StandardCharsets.UTF_8)).getAsJsonObject();}
    private static String sha(Path path)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));}
    private static void write(boolean create)throws IOException{
        if(create){Files.writeString(RESULT,JSON.toJson(result)+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);return;}
        Path temporary=RESULT.resolveSibling(RESULT.getFileName()+".tmp");Files.writeString(temporary,JSON.toJson(result)+"\n",StandardCharsets.UTF_8);
        try{Files.move(temporary,RESULT,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
        catch(AtomicMoveNotSupportedException unsupported){Files.move(temporary,RESULT,StandardCopyOption.REPLACE_EXISTING);}
    }
    private static void failed(Throwable failure){
        LOG.error("MILITARY6_LEGACY_AUDIT FAIL result="+RESULT,failure);
        if(process==null||result==null)return;
        process.addProperty("status","FAIL");process.addProperty("error",failure.toString());process.addProperty("completedUtc",Instant.now().toString());result.addProperty("status","FAIL");
        try{write(!Files.exists(RESULT));}catch(Throwable writingFailure){LOG.error("MILITARY6_LEGACY_AUDIT FAIL_JSON_WRITE",writingFailure);}
    }
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
    private Military6LegacyAudit(){}
}
