package com.codex.blacklight.city;

import com.codex.blacklight.entity.MilitarySoldier;
import com.codex.blacklight.firearms.AmmoState;
import com.codex.blacklight.firearms.FirearmItem;
import com.codex.blacklight.military.MilitaryRank;
import com.codex.blacklight.registry.ModEntities;
import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Optional real-disk companion. No production hook; all CityMilitaryServerAudit*.class are excluded from jars. */
@EventBusSubscriber(modid="blacklight",value=net.minecraftforge.api.distmarker.Dist.DEDICATED_SERVER)
public final class CityMilitaryServerAuditHomes {
    private static final Logger LOG=LoggerFactory.getLogger(CityMilitaryServerAuditHomes.class);
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static final Path RESULT=Path.of("military-homes-audit.json").toAbsolutePath().normalize();
    private static final Path BASELINE=Path.of("MILITARY4-BASELINE.json").toAbsolutePath().normalize();
    private static final String RUN=UUID.randomUUID().toString();
    private record Key(String dimension,UUID uuid){}
    private static final Map<Key,JsonObject> INITIAL_LOADS=new LinkedHashMap<>();
    private static final Deque<ChunkPos> PENDING=new ArrayDeque<>();
    private static JsonObject result=new JsonObject();
    private static boolean initialized,restarting,actorsPrepared,complete;
    private static int begun,settleAt=-1;
    private static Throwable joinFailure;

    private static boolean enabled(){return Boolean.getBoolean("blacklight.militaryHomesAudit");}

    /** Observe actual entity NBT before delayed home migration can repair or overwrite missing saved fields. */
    @SubscribeEvent public static void joined(EntityJoinLevelEvent event){
        if(!enabled()||!(event.getLevel() instanceof ServerLevel level)||!(event.getEntity() instanceof MilitarySoldier soldier))return;
        try{
            var key=new Key(level.dimension().location().toString(),soldier.getUUID());
            require(INITIAL_LOADS.size()<512||INITIAL_LOADS.containsKey(key),"Disposable home audit exceeded 512 initial military load observations");
            INITIAL_LOADS.putIfAbsent(key,snapshot(soldier));
        }catch(Throwable failure){joinFailure=failure;}
    }

    /** True permits the ordinary audit's normal saving halt; false requests its WAIT_HOMES continuation. */
    static boolean beforeHalt(MinecraftServer server,String oldStatus,ServerLevel level,CityMilitaryData.Base base)throws IOException{
        if(!enabled())return true;
        if(complete)return true;
        require(server.isDedicatedServer(),"Home durability audit requires an ordinary dedicated server");
        if(!initialized)initialize(server,oldStatus,level,base);
        require(joinFailure==null,"Initial entity-load observation failed: "+joinFailure);
        require(server.getTickCount()-begun<640,"Home audit timed out waiting for actual ticking chunks/entities or home assignments");
        require(result.get("dimension").getAsString().equals(level.dimension().location().toString())
                &&result.get("baseId").getAsString().equals(base.id),"Home audit cannot change its dimension or native base");
        if(!PENDING.isEmpty()){
            var chunk=PENDING.removeFirst();level.getChunk(chunk.x,chunk.z);level.setChunkForced(chunk.x,chunk.z,true);return false;
        }
        if(!actorsPrepared){
            if(!restarting)createActors(level,base);
            actorsPrepared=true;settleAt=server.getTickCount();
        }
        if(server.getTickCount()-settleAt<60)return false;
        var externalAt=readPos(result.getAsJsonObject("externalSpawn"));var fieldAt=readPos(result.getAsJsonObject("fieldSpawn"));
        if(!level.isPositionEntityTicking(externalAt)||!level.isPositionEntityTicking(fieldAt))return false;
        if(restarting&&!expectedEntitiesPresent(level))return false;
        verifyNativeLedger(level,base);
        if(restarting)verifyRestart(level,base,oldStatus);else verifyFirst(level,base,oldStatus);
        complete=true;return true;
    }

    private static void initialize(MinecraftServer server,String oldStatus,ServerLevel level,CityMilitaryData.Base base)throws IOException{
        initialized=true;begun=server.getTickCount();
        require(base!=null,"Native base must exist before preparing home durability evidence");
        require(oldStatus.equals("PASS")||oldStatus.equals("PHASE1_SAVED"),"The original native audit did not reach a saving checkpoint");
        if(Files.exists(RESULT)){
            result=JsonParser.parseString(Files.readString(RESULT,StandardCharsets.UTF_8)).getAsJsonObject();
            String status=result.get("status").getAsString();
            require(status.equals("PHASE1_SAVED")||status.equals("PASS"),"Preserve the incomplete/failed homes result and use a fresh QA copy: "+status);
            require(result.get("schema").getAsInt()==1&&result.get("seed").getAsLong()==level.getSeed(),"Home restart schema or seed changed");
            require(!result.get("firstProcessId").getAsString().equals(RUN),"A second in-memory pass cannot prove an actual process restart");
            require(result.get("dimension").getAsString().equals(level.dimension().location().toString())&&result.get("baseId").getAsString().equals(base.id),"Home restart selected another base or dimension");
            if(result.has("military4BaselineSha256"))require(Files.isRegularFile(BASELINE)&&sha(BASELINE).equals(result.get("military4BaselineSha256").getAsString()),"The immutable military.4 source-copy baseline changed between processes");
            restarting=true;result.addProperty("restartProcessId",RUN);
        }else{
            result.addProperty("schema",1);result.addProperty("firstProcessId",RUN);result.addProperty("seed",level.getSeed());
            result.addProperty("dimension",level.dimension().location().toString());result.addProperty("baseId",base.id);
            result.addProperty("originalAuditCheckpoint",oldStatus);result.addProperty("sourceRunDirectory",Path.of("").toAbsolutePath().normalize().toString());
            result.add("nativeSlots",slots(base));result.addProperty("nativeLosses",base.losses);
            int[] local=base.site.world(32,base.site.templateDepth()-12);
            var externalAt=new BlockPos(local[0],base.site.floor()+24,local[1]);
            var fieldAt=chooseDistantField(level,base);
            require(level.hasChunkAt(externalAt),"External soldier must stay inside the old audit's loaded native footprint");
            result.add("externalSpawn",writePos(externalAt));result.add("fieldSpawn",writePos(fieldAt));
            result.addProperty("movementNotTested",true);result.addProperty("actorsNoAiForFiniteLoadoutPersistence",true);
            if(Files.isRegularFile(BASELINE)){
                result.addProperty("military4BaselineSha256",sha(BASELINE));
                var baseline=JsonParser.parseString(Files.readString(BASELINE,StandardCharsets.UTF_8)).getAsJsonObject();
                if(baseline.has("source"))result.addProperty("military4SourceDirectory",baseline.get("source").getAsString());
                verifyLegacyNativeLoads(level,base);
                result.addProperty("legacyMilitary4NativeLoadsObservedBeforeMigration",true);
            }
        }
        var fieldAt=readPos(result.getAsJsonObject("fieldSpawn"));var forced=new JsonArray();
        for(int x=(fieldAt.getX()>>4)-1;x<=(fieldAt.getX()>>4)+1;x++)for(int z=(fieldAt.getZ()>>4)-1;z<=(fieldAt.getZ()>>4)+1;z++){
            var chunk=new ChunkPos(x,z);PENDING.add(chunk);var entry=new JsonObject();entry.addProperty("x",x);entry.addProperty("z",z);forced.add(entry);
        }
        result.add("qaFieldForcedChunks",forced);progress(restarting?"RUNNING_RESTART":"RUNNING_FIRST","Waiting for loaded real entities; new in-memory fields alone never count as persistence");
    }

    private static BlockPos chooseDistantField(ServerLevel level,CityMilitaryData.Base base){
        for(int radius:new int[]{512,768,1024})for(int[] direction:new int[][]{{1,0},{0,1},{-1,0},{0,-1}}){
            var at=new BlockPos(base.site.x()+32+direction[0]*radius,base.site.floor()+24,base.site.z()+32+direction[1]*radius);
            if(level.isOutsideBuildHeight(at)||!level.getWorldBorder().isWithinBounds(at))continue;
            boolean distant=true;
            for(var candidate:CityMilitaryData.get(level).bases.values())if(candidate.site.blackwatch()==base.site.blackwatch()
                    &&Vec3.atBottomCenterOf(at).distanceToSqr(MilitaryHomeSystem.center(candidate))<=300*300){distant=false;break;}
            if(distant)return at;
        }
        throw new IllegalStateException("No bounded field QA position beyond every registered same-faction base was found");
    }

    private static void createActors(ServerLevel level,CityMilitaryData.Base base)throws IOException{
        var external=actor(level,base,true,readPos(result.getAsJsonObject("externalSpawn")),true,5,11);
        var field=actor(level,base,false,readPos(result.getAsJsonObject("fieldSpawn")),false,3,9);
        result.addProperty("externalUuid",external.getUUID().toString());result.addProperty("fieldUuid",field.getUUID().toString());
        result.add("externalInitialLoadout",snapshot(external));result.add("fieldInitialLoadout",snapshot(field));
        write();
    }
    private static MilitarySoldier actor(ServerLevel level,CityMilitaryData.Base base,boolean officer,BlockPos at,boolean equipped,int magazine,int reserve){
        var type=base.site.blackwatch()?(officer?ModEntities.BLACKWATCH_OFFICER.get():ModEntities.BLACKWATCH_TEAM_LEADER.get())
                :(officer?ModEntities.MILITARY_OFFICER.get():ModEntities.MILITARY_TEAM_LEADER.get());
        var soldier=type.create(level);require(soldier!=null,"Registered home QA role is missing");
        soldier.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);soldier.setNoAi(true);soldier.setNoGravity(true);soldier.setPersistenceRequired();soldier.setAutomaticUnitJoin(false);
        soldier.setRank(officer?MilitaryRank.CAPTAIN:MilitaryRank.SERGEANT);soldier.initializeWeapon();
        require(soldier.getMainHandItem().getItem() instanceof FirearmItem,"Real home QA actor lacks its initialized gun");
        FirearmItem.store(soldier.getMainHandItem(),new AmmoState(magazine,0,0));
        var saved=soldier.saveWithoutId(new CompoundTag());saved.putInt("MilitaryAmmoReserve",reserve);soldier.load(saved);soldier.setEquipped(equipped);
        require(soldier.magazineAmmo()==magazine&&soldier.reserveAmmo()==reserve,"Partly spent QA loadout was not applied exactly");
        require(level.addFreshEntity(soldier),"Actual server refused the standalone home QA soldier");return soldier;
    }

    private static boolean expectedEntitiesPresent(ServerLevel level){
        return level.getEntity(UUID.fromString(result.get("externalUuid").getAsString())) instanceof MilitarySoldier
                &&level.getEntity(UUID.fromString(result.get("fieldUuid").getAsString())) instanceof MilitarySoldier;
    }
    private static MilitarySoldier requiredActor(ServerLevel level,String uuidProperty){
        var entity=level.getEntity(UUID.fromString(result.get(uuidProperty).getAsString()));
        require(entity instanceof MilitarySoldier soldier&&soldier.isAlive(),"Saved actual home QA UUID is absent or dead: "+uuidProperty);return (MilitarySoldier)entity;
    }
    private static void verifyNativeLedger(ServerLevel level,CityMilitaryData.Base base){
        require(slots(base).equals(result.getAsJsonArray("nativeSlots"))&&base.losses==result.get("nativeLosses").getAsInt(),"External/field assignment changed native issuance UUIDs or losses");
        require(base.health.operational(),"The original real command core did not stay operational");
        for(var uuid:base.slots)if(uuid!=null){
            require(level.getEntity(uuid) instanceof MilitarySoldier,"Original native guard was not loaded");
            var soldier=(MilitarySoldier)level.getEntity(uuid);var tag=soldier.getPersistentData();
            require(tag.getString(CityMilitaryService.BASE_TAG).equals(base.id)
                    &&tag.getString(MilitaryHomeSystem.HOME_BASE_TAG).equals(base.id)
                    &&tag.getString(MilitaryHomeSystem.HOME_DIMENSION_TAG).equals(level.dimension().location().toString())
                    &&tag.getString(MilitaryHomeSystem.ORIGIN_DIMENSION_TAG).equals(level.dimension().location().toString()),"Native home migration lost immutable issuance origin or current home/dimension");
        }
    }
    private static void verifyLegacyNativeLoads(ServerLevel level,CityMilitaryData.Base base){
        for(var uuid:base.slots)if(uuid!=null){
            var loaded=initial(level,uuid);
            require(loaded.get("homeBase").getAsString().isEmpty()&&loaded.get("originDimension").getAsString().isEmpty(),"A military.4 migration fixture already had military.5 home fields at initial entity load");
            require(loaded.get("originBase").getAsString().equals(base.id),"The actual pre-migration native origin marker was absent");
        }
    }
    private static void verifyFirst(ServerLevel level,CityMilitaryData.Base base,String oldStatus)throws IOException{
        var external=requiredActor(level,"externalUuid");var field=requiredActor(level,"fieldUuid");
        verifyAssignments(level,base,external,field);
        sameLoadout(result.getAsJsonObject("externalInitialLoadout"),snapshot(external),"external initial finite loadout");
        sameLoadout(result.getAsJsonObject("fieldInitialLoadout"),snapshot(field),"field initial finite loadout");
        var natives=new JsonArray();for(var uuid:base.slots)if(uuid!=null)natives.add(snapshot((MilitarySoldier)level.getEntity(uuid)));
        result.add("nativeSavedSnapshots",natives);result.add("externalSavedSnapshot",snapshot(external));result.add("fieldSavedSnapshot",snapshot(field));
        result.addProperty("phaseOneHomeAssignmentsVerified",true);result.addProperty("oldAuditStatusAtHomeCheckpoint",oldStatus);
        progress("PHASE1_SAVED","Actual home/field tags and finite role loadouts are ready for normal saving shutdown; another process restart is required");
    }
    private static void verifyRestart(ServerLevel level,CityMilitaryData.Base base,String oldStatus)throws IOException{
        var external=requiredActor(level,"externalUuid");var field=requiredActor(level,"fieldUuid");verifyAssignments(level,base,external,field);
        for(var entry:result.getAsJsonArray("nativeSavedSnapshots")){
            var expected=entry.getAsJsonObject();var uuid=UUID.fromString(expected.get("uuid").getAsString());
            sameSnapshot(expected,initial(level,uuid),"native first entity-load snapshot");
            sameSnapshot(expected,snapshot((MilitarySoldier)level.getEntity(uuid)),"native settled restart snapshot");
        }
        var expectedExternal=result.getAsJsonObject("externalSavedSnapshot");var expectedField=result.getAsJsonObject("fieldSavedSnapshot");
        sameSnapshot(expectedExternal,initial(level,external.getUUID()),"external first entity-load snapshot");
        sameSnapshot(expectedField,initial(level,field.getUUID()),"field first entity-load snapshot");
        sameSnapshot(expectedExternal,snapshot(external),"external settled restart snapshot");
        sameSnapshot(expectedField,snapshot(field),"field settled restart snapshot");
        result.addProperty("initialEntityLoadFieldsVerifiedBeforeMigration",true);result.addProperty("actualProcessRestartVerified",true);
        result.addProperty("nativeOriginAndSlotLedgerPreserved",true);result.addProperty("externalDoesNotConsumeNativeSlots",true);
        result.addProperty("fieldAnchorAndDimensionPersisted",true);result.addProperty("finiteWeaponRoleOutfitPersisted",true);
        result.addProperty("oldAuditStatusAtHomeCheckpoint",oldStatus);
        progress("PASS","A different ordinary server process loaded the exact saved home/field/origin and finite role loadouts before delayed migration; native slots remain unchanged");
    }
    private static void verifyAssignments(ServerLevel level,CityMilitaryData.Base base,MilitarySoldier external,MilitarySoldier field){
        var bound=external.getPersistentData();var patrol=field.getPersistentData();String dimension=level.dimension().location().toString();
        require(bound.getString(MilitaryHomeSystem.HOME_BASE_TAG).equals(base.id)&&bound.getString(MilitaryHomeSystem.HOME_DIMENSION_TAG).equals(dimension),"Independent real soldier did not acquire its loaded same-faction home");
        require(!bound.contains(CityMilitaryService.BASE_TAG)&&!patrol.contains(CityMilitaryService.BASE_TAG),"Independent actors forged a native issuance origin");
        require(Arrays.stream(base.slots).noneMatch(id->external.getUUID().equals(id)||field.getUUID().equals(id)),"Independent actors consumed native slots");
        require(patrol.getString(MilitaryHomeSystem.HOME_BASE_TAG).isEmpty()&&patrol.contains(MilitaryHomeSystem.FIELD_ANCHOR_TAG)
                &&patrol.getLong(MilitaryHomeSystem.FIELD_ANCHOR_TAG)==readPos(result.getAsJsonObject("fieldSpawn")).asLong()
                &&patrol.getString(MilitaryHomeSystem.FIELD_DIMENSION_TAG).equals(dimension),"Distant no-base soldier did not preserve its actual original field anchor and dimension");
    }
    private static JsonObject initial(ServerLevel level,UUID uuid){
        var snapshot=INITIAL_LOADS.get(new Key(level.dimension().location().toString(),uuid));
        require(snapshot!=null,"No actual initial entity-load event was observed for "+uuid);return snapshot;
    }
    private static JsonObject snapshot(MilitarySoldier soldier){
        var tag=soldier.getPersistentData();var state=soldier.saveWithoutId(new CompoundTag());var weapon=new CompoundTag();
        for(String key:new String[]{"HandItems","ArmorItems","MilitaryStowedWeapon"})if(state.contains(key))weapon.put(key,state.get(key).copy());
        var j=new JsonObject();j.addProperty("uuid",soldier.getUUID().toString());j.addProperty("type",BuiltInRegistries.ENTITY_TYPE.getKey(soldier.getType()).toString());
        j.addProperty("rank",soldier.rank().id());j.addProperty("role",soldier.commandRole().id());j.addProperty("equipped",soldier.equipped());
        j.addProperty("magazine",soldier.magazineAmmo());j.addProperty("reserve",soldier.reserveAmmo());j.addProperty("weaponNbt",weapon.toString());
        j.addProperty("unit",soldier.unitId()==null?"":soldier.unitId().toString());j.addProperty("automaticJoin",soldier.automaticUnitJoin());
        j.addProperty("noAi",soldier.isNoAi());j.addProperty("noGravity",soldier.isNoGravity());j.addProperty("persistent",soldier.isPersistenceRequired());
        j.addProperty("originBase",tag.getString(CityMilitaryService.BASE_TAG));j.addProperty("originPostPresent",tag.contains(CityMilitaryService.POST_TAG));j.addProperty("originPost",tag.getLong(CityMilitaryService.POST_TAG));
        j.addProperty("originDimension",tag.getString(MilitaryHomeSystem.ORIGIN_DIMENSION_TAG));
        j.addProperty("homeBase",tag.getString(MilitaryHomeSystem.HOME_BASE_TAG));j.addProperty("homeDimension",tag.getString(MilitaryHomeSystem.HOME_DIMENSION_TAG));
        j.addProperty("fieldAnchorPresent",tag.contains(MilitaryHomeSystem.FIELD_ANCHOR_TAG));j.addProperty("fieldAnchor",tag.getLong(MilitaryHomeSystem.FIELD_ANCHOR_TAG));j.addProperty("fieldDimension",tag.getString(MilitaryHomeSystem.FIELD_DIMENSION_TAG));
        j.addProperty("mode",tag.getString(MilitaryHomeSystem.MODE_TAG));return j;
    }
    private static void sameLoadout(JsonObject expected,JsonObject actual,String label){
        for(String key:new String[]{"uuid","type","rank","role","equipped","magazine","reserve","unit","automaticJoin","noAi","noGravity","persistent"})
            require(expected.get(key).equals(actual.get(key)),label+" changed "+key+" expected="+expected.get(key)+" actual="+actual.get(key));
        try{require(TagParser.parseTag(expected.get("weaponNbt").getAsString()).equals(TagParser.parseTag(actual.get("weaponNbt").getAsString())),label+" changed actual carried/stowed weapon NBT");}
        catch(com.mojang.brigadier.exceptions.CommandSyntaxException failure){throw new IllegalStateException("Cannot parse the saved actual QA weapon snapshot",failure);}
    }
    private static void sameSnapshot(JsonObject expected,JsonObject actual,String label){
        sameLoadout(expected,actual,label);
        for(String key:new String[]{"originBase","originPostPresent","originPost","originDimension","homeBase","homeDimension","fieldAnchorPresent","fieldAnchor","fieldDimension","mode"})
            require(expected.get(key).equals(actual.get(key)),label+" changed "+key+" expected="+expected.get(key)+" actual="+actual.get(key));
    }
    private static JsonArray slots(CityMilitaryData.Base base){var list=new JsonArray();for(var id:base.slots)if(id==null)list.add(JsonNull.INSTANCE);else list.add(id.toString());return list;}
    private static JsonObject writePos(BlockPos at){var j=new JsonObject();j.addProperty("x",at.getX());j.addProperty("y",at.getY());j.addProperty("z",at.getZ());return j;}
    private static BlockPos readPos(JsonObject j){return new BlockPos(j.get("x").getAsInt(),j.get("y").getAsInt(),j.get("z").getAsInt());}
    private static String sha(Path path)throws IOException{
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));}
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    private static void progress(String status,String detail)throws IOException{result.addProperty("status",status);result.addProperty("detail",detail);result.addProperty("updatedUtc",Instant.now().toString());write();LOG.info("CITY_MILITARY_HOMES_AUDIT {} {} result={}",status,detail,RESULT);}
    private static void write()throws IOException{
        var temporary=RESULT.resolveSibling(RESULT.getFileName()+".tmp");Files.writeString(temporary,JSON.toJson(result)+"\n",StandardCharsets.UTF_8);
        try{Files.move(temporary,RESULT,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException unsupported){Files.move(temporary,RESULT,StandardCopyOption.REPLACE_EXISTING);}
    }
    static void failed(Throwable failure){
        if(!enabled())return;
        try{result.addProperty("schema",1);result.addProperty("status","FAIL");result.addProperty("error",failure.toString());result.addProperty("updatedUtc",Instant.now().toString());write();}
        catch(Throwable writingFailure){LOG.error("CITY_MILITARY_HOMES_AUDIT FAIL_JSON_WRITE",writingFailure);}
        LOG.error("CITY_MILITARY_HOMES_AUDIT FAIL result="+RESULT,failure);
    }
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
    private CityMilitaryServerAuditHomes(){}
}
