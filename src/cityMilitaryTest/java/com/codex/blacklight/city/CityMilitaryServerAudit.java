package com.codex.blacklight.city;

import com.codex.blacklight.entity.MilitarySoldier;
import com.codex.blacklight.firearms.FirearmsCombat;
import com.codex.blacklight.firearms.FirearmsRegistry;
import com.codex.blacklight.firearms.WeaponCatalog;
import com.codex.blacklight.military.MilitaryOrganizationSystem;
import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/** Opt-in disposable-server QA. Exclude CityMilitaryServerAudit*.class from every release JAR. */
@EventBusSubscriber(modid="blacklight",value=net.neoforged.api.distmarker.Dist.DEDICATED_SERVER)
public final class CityMilitaryServerAudit {
    private enum Stage { FIND, LOAD, WAIT, WAIT_HOMES, DONE }
    private static final Logger LOG=LoggerFactory.getLogger(CityMilitaryServerAudit.class);
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static final Path RESULT=Path.of("city-military-audit.json").toAbsolutePath().normalize();
    private static final String AMBIENT_TAG="BlacklightMilitary4AuditAmbient";
    private static final int AMBIENT_COUNT=80;
    private static Stage stage=Stage.FIND;
    private static boolean initialized,restarting;
    private static int begun,waitAt,candidateIndex;
    private static String homesHaltCheckpoint;
    private static ServerLevel level;
    private static Structure structure;
    private static RandomSpreadStructurePlacement placement;
    private static CityMilitaryPlan.Site site;
    private static CityMilitaryPlan.Checkpoint checkpoint;
    private static ChunkPos start;
    private static final Deque<ChunkPos> pending=new ArrayDeque<>();
    private static JsonObject result=new JsonObject();

    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        if(!Boolean.getBoolean("blacklight.cityMilitaryAudit")||stage==Stage.DONE)return;
        MinecraftServer server=event.getServer();
        try {
            if(!initialized)initialize(server);
            require(server.getTickCount()-begun<2400,"Audit timed out before completion; stage="+stage);
            switch(stage){
                case FIND -> find();
                case LOAD -> load(server);
                case WAIT -> verifyWhenReady(server);
                case WAIT_HOMES -> continueHomes(server);
                default -> {}
            }
        }catch(Throwable failure){fail(server,failure);}
    }
    private static void initialize(MinecraftServer server)throws IOException{
        initialized=true;begun=server.getTickCount();
        require(server.isDedicatedServer(),"Use a normal dedicated runServer, not an integrated client or GameTestServer");
        require(server.getWorldData().worldGenOptions().generateStructures(),"WorldOptions.generateStructures is false; GameTest auto-generation is not valid evidence");
        String selectedDimension=System.getProperty("blacklight.cityMilitaryAudit.dimension","city");
        require(selectedDimension.equals("city")||selectedDimension.equals("world"),"QA dimension must be city or world");
        level=server.getLevel(selectedDimension.equals("world")?CityTravel.WORLD:CityTravel.CITY);require(level!=null,"Requested actual blacklight dimension is missing: "+selectedDimension);
        require(CityMilitaryTerrain.supported(level.getChunkSource().getGenerator()),"Actual city generator is not supported by CityMilitaryTerrain");
        structure=level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(id("city_military_base"));
        require(structure!=null,"Registered city_military_base structure is missing");
        var set=level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).get(id("city_military_bases"));
        require(set!=null&&set.placement() instanceof RandomSpreadStructurePlacement,"Registered city structure_set is missing or not random_spread");
        require(set.structures().stream().anyMatch(entry->entry.structure().value()==structure),"Actual structure_set does not reference city_military_base");
        placement=(RandomSpreadStructurePlacement)set.placement();
        var encoded=StructurePlacement.CODEC.encodeStart(JsonOps.INSTANCE,placement).result().orElseThrow(()->new IllegalStateException("Cannot encode actual native placement")).getAsJsonObject();
        require(placement.spacing()==24&&placement.separation()==12&&placement.spreadType()==RandomSpreadType.LINEAR&&encoded.get("salt").getAsInt()==26102471,"Actual registered placement differs from 24/12/LINEAR/salt26102471");
        if(Files.exists(RESULT)){
            result=JsonParser.parseString(Files.readString(RESULT,StandardCharsets.UTF_8)).getAsJsonObject();
            String status=result.get("status").getAsString();
            require(status.equals("PHASE1_SAVED")||status.equals("PASS"),"Previous audit was incomplete or failed; preserve its evidence and use a fresh run directory: "+status);
            require(result.get("schema").getAsInt()==1&&result.get("seed").getAsLong()==level.getSeed(),"Restart schema or actual city seed changed");
            require(result.get("dimension").getAsString().equals(level.dimension().location().toString()),"Restart dimension changed");
            restarting=true;site=readSite(result.getAsJsonObject("site"));start=new ChunkPos(result.get("startChunkX").getAsInt(),result.get("startChunkZ").getAsInt());
            require(nativeCandidate(start).equals(start),"Saved start is no longer a native candidate for the actual seed");
            verifyNativeStart();enqueueFootprint();stage=Stage.LOAD;
            progress("RUNNING_RESTART","Loading saved native footprint and entity regions");
        }else{
            require(server.isSpawningAnimals(),"The 80-cow QA fixture requires spawn-animals=true; vanilla otherwise discards even persistent cows");
            result.addProperty("schema",1);result.addProperty("auditRevision",4);result.addProperty("seed",level.getSeed());result.addProperty("dimension",level.dimension().location().toString());
            result.addProperty("generator",level.getChunkSource().getGenerator().getClass().getName());result.add("nativePlacement",encoded);result.addProperty("structuresEnabled",true);
            progress("RUNNING_FIRST","Searching actual registered native structure candidates");
        }
    }
    private static void find()throws IOException{
        int xRegions=(FutianMap.WIDTH+383)/384,zRegions=(FutianMap.DEPTH+383)/384;
        require(candidateIndex<xRegions*zRegions,"No safe native candidate exercising cross-chunk ammunition exists on the actual bounded Futian map for seed "+level.getSeed());
        int rx=candidateIndex/zRegions,rz=candidateIndex%zRegions;candidateIndex++;
        var candidate=placement.getPotentialStructureChunk(level.getSeed(),rx*24,rz*24);
        var expected=new RandomSpreadStructurePlacement(24,12,RandomSpreadType.LINEAR,26102471).getPotentialStructureChunk(level.getSeed(),rx*24,rz*24);
        require(candidate.equals(expected),"Registered candidate does not match the required native placement");
        boolean blackwatch=((level.getSeed()^candidate.toLong())&1)!=0;
        var selected=CityMilitaryPlan.find(level.getSeed(),candidate.x,candidate.z,blackwatch,true,CityMilitaryTerrain.survey(level.getChunkSource().getGenerator(),level.getSeed()));
        result.addProperty("candidatesExamined",candidateIndex);
        if(selected.isEmpty()){progress("RUNNING_FIRST","Rejected unsafe native candidate "+candidate);return;}
        if(!crossChunkAmmunitionCase(selected.get(),candidate)){
            progress("RUNNING_FIRST","Safe candidate does not exercise a cross-start-chunk ammunition chest: "+candidate);return;
        }
        start=candidate;site=selected.get();
        // This ordinary FULL chunk request is the only creator of the native start.
        // Never call createStructures, createStructuresForChunk or piece.postProcess here.
        verifyNativeStart();result.addProperty("naturalStartVerified",true);result.addProperty("startChunkX",start.x);result.addProperty("startChunkZ",start.z);result.add("site",writeSite(site));
        enqueueFootprint();stage=Stage.LOAD;progress("RUNNING_FIRST","Native CityMilitaryPiece start found; loading complete footprint");
    }
    private static boolean crossChunkAmmunitionCase(CityMilitaryPlan.Site selected,ChunkPos candidate){
        for(int[] local:LargeBaseBlueprint.get(selected.blackwatch()).ammunition()){
            int[] point=selected.world(local[0],local[2]);
            if(!new ChunkPos(new BlockPos(point[0],selected.floor()+local[1],point[1])).equals(candidate))return true;
        }
        return false;
    }
    private static ChunkPos nativeCandidate(ChunkPos position){return placement.getPotentialStructureChunk(level.getSeed(),position.x,position.z);}
    private static void verifyNativeStart(){
        var chunk=level.getChunk(start.x,start.z);var generated=chunk.getStartForStructure(structure);
        require(generated!=null&&generated.isValid(),"Normal native getChunk produced no valid city military structure start at "+start);
        var pieces=generated.getPieces().stream().filter(p->p instanceof CityMilitaryPiece).map(p->(CityMilitaryPiece)p).toList();
        require(pieces.size()==1,"Native start must contain exactly one CityMilitaryPiece");
        checkpoint=pieces.get(0).checkpoint();
        require(pieces.get(0).site().equals(site),"Native piece differs from deterministic safe planner or saved start");
    }
    private static void enqueueFootprint(){
        var bounds=new CityMilitaryPiece(site,checkpoint).getBoundingBox();
        for(int x=(bounds.minX()>>4)-1;x<=(bounds.maxX()>>4)+1;x++)
            for(int z=(bounds.minZ()>>4)-1;z<=(bounds.maxZ()>>4)+1;z++)pending.add(new ChunkPos(x,z));
        result.addProperty("forcedFootprintChunks",pending.size());
    }
    private static void load(MinecraftServer server)throws IOException{
        if(!pending.isEmpty()){
            var chunk=pending.removeFirst();level.getChunk(chunk.x,chunk.z);level.setChunkForced(chunk.x,chunk.z,true);return;
        }
        if(!restarting)createAmbientFixture();
        waitAt=server.getTickCount();stage=Stage.WAIT;progress(restarting?"RUNNING_RESTART":"RUNNING_FIRST","Forced full footprint plus one-chunk boundary; waiting at least 40 server ticks");
    }
    private static void createAmbientFixture(){
        var uuids=new JsonArray();
        for(int index=0;index<AMBIENT_COUNT;index++){
            // Keep test mobs away from entry posts, firing lanes and cramming damage.
            int[] point=site.world(24+(index%10)*2,24+(index/10)*2);
            var at=new BlockPos(point[0],site.floor()+24,point[1]);
            require(level.hasChunkAt(at)&&level.getWorldBorder().isWithinBounds(at),"Ambient fixture must remain in the loaded native footprint");
            var cow=EntityType.COW.create(level);require(cow!=null,"Cannot create real ambient fixture cow");
            cow.setNoAi(true);cow.setPersistenceRequired();cow.setNoGravity(true);cow.setInvulnerable(true);cow.addTag(AMBIENT_TAG);
            cow.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);
            require(level.addFreshEntity(cow),"Ambient fixture cow was not registered in the actual server level");
            uuids.add(cow.getUUID().toString());
        }
        result.add("ambientFixtureUuids",uuids);
    }
    private static int ambientFixtureCount(){
        int count=0;for(var entity:level.getAllEntities())if(entity instanceof Mob mob&&mob.isAlive()&&!mob.isRemoved()&&mob.getTags().contains(AMBIENT_TAG))count++;
        return count;
    }
    private static int unrelatedMobCount(){
        int count=0;for(var entity:level.getAllEntities())if(entity instanceof Mob mob&&!(mob instanceof MilitarySoldier)&&mob.isAlive()&&!mob.isRemoved())count++;
        return count;
    }
    private static boolean firstTwoGuardChunksTicking(){
        var guards=LargeBaseBlueprint.get(site.blackwatch()).guards();require(guards.size()>=2,"Blueprint contains fewer than two guards");
        for(int i=0;i<2;i++){
            var guard=guards.get(i);int[] point=checkpoint==null?site.world(guard.x(),guard.z()):checkpoint.world(i==0?3:13,6);
            int y=checkpoint==null?site.floor()+guard.y():checkpoint.floor()+1;
            if(!level.isPositionEntityTicking(new BlockPos(point[0],y,point[1])))return false;
        }
        return true;
    }
    private static void verifyWhenReady(MinecraftServer server)throws IOException{
        if(server.getTickCount()-waitAt<40)return;
        require(server.getTickCount()-waitAt<400,"Forced chunks or saved guard entity regions did not become ready within 400 ticks");
        if(!firstTwoGuardChunksTicking())return;
        if(restarting){
            if(!savedEntitiesPresent())return;
            verifyRestart(server);
        }else verifyFirst(server);
    }
    private static ChestBlockEntity naturalAmmunition(){
        ChestBlockEntity selected=null;
        for(int[] local:LargeBaseBlueprint.get(site.blackwatch()).ammunition()){
            int[] point=site.world(local[0],local[2]);var at=new BlockPos(point[0],site.floor()+local[1],point[1]);
            require(level.getBlockEntity(at) instanceof ChestBlockEntity,"Native footprint is missing ammunition chest "+at);
            if(selected==null&&!new ChunkPos(at).equals(start))selected=(ChestBlockEntity)level.getBlockEntity(at);
        }
        require(selected!=null,"No native ammunition chest exists outside the start chunk");
        return selected;
    }
    private static void verifyFirst(MinecraftServer server)throws IOException{
        var chest=naturalAmmunition();chest.unpackLootTable(null);int initialRounds=rounds(chest);
        require(initialRounds>0&&initialRounds<=1728,"Native chest did not supply bounded real rifle ammunition");
        String baseId=CityMilitaryService.register(level,site,checkpoint);var data=CityMilitaryData.get(level);var base=data.bases.get(baseId);
        require(base!=null&&issued(base)==0,"Fresh native audit base already has issued slots");
        int ambient=ambientFixtureCount(),unrelated=unrelatedMobCount();
        require(ambient==AMBIENT_COUNT&&unrelated>=AMBIENT_COUNT,"Real entity traversal must find all 80 unrelated fixture mobs before the first garrison pass; fixture="+ambient+", unrelated="+unrelated);
        result.addProperty("ambientFixtureMobsAtFirstMuster",ambient);result.addProperty("unrelatedMobsAtFirstMuster",unrelated);
        var observer=new Vec3(site.x()+site.width()/2.,site.floor()+1,site.z()+site.depth()/2.);
        CityMilitaryService.maintain(level,List.of(observer));
        require(issued(base)==2,"One real maintain pass must issue exactly two finite garrison slots; issued="+issued(base));
        result.addProperty("ambientMobsDoNotConsumeGarrisonBudget",true);
        var expected=new LinkedHashSet<UUID>();var guards=new JsonArray();UUID unit=null;
        for(int index=0;index<base.slots.length;index++)if(base.slots[index]!=null){
            UUID uuid=base.slots[index];require(expected.add(uuid),"Duplicate UUID in first-pass slots");
            require(level.getEntity(uuid) instanceof MilitarySoldier,"Issued slot has no real soldier entity");var soldier=(MilitarySoldier)level.getEntity(uuid);
            require(soldier.unitId()!=null,"Real soldier was not assigned to an organization");if(unit==null)unit=soldier.unitId();require(unit.equals(soldier.unitId()),"Base soldiers do not share their actual unit");
            soldier.initializeWeapon();soldier.setNoAi(true);soldier.setPersistenceRequired();
            require(index!=0||soldier.commandRole()==com.codex.blacklight.military.MilitaryRole.TEAM_LEADER,"Native entry slot must spawn a real team leader");
            if(index==1){soldier.setCommandRole(com.codex.blacklight.military.MilitaryRole.OFFICER);soldier.setEquipped(false);}
            var guard=new JsonObject();guard.addProperty("role",soldier.commandRole().id());guard.addProperty("equipped",soldier.equipped());guard.addProperty("slot",index);guard.addProperty("uuid",uuid.toString());guard.addProperty("unit",soldier.unitId().toString());guard.addProperty("magazine",soldier.magazineAmmo());guard.addProperty("reserve",soldier.reserveAmmo());guards.add(guard);
        }
        require(unit!=null&&MilitaryOrganizationSystem.organization(level).unit(unit).orElseThrow(()->new IllegalStateException("Actual or saved garrison organization is missing")).members().keySet().equals(expected),"Actual organization does not contain exactly the two issued UUIDs");
        require(loadedBaseUuids(baseId).equals(expected),"Native garrison contains duplicate or unrelated base-tagged soldiers");
        UUID appointed=base.slots[0];
        try{int appointedResult=server.getCommands().getDispatcher().execute("blacklight military unit commander "+unit+" "+appointed,server.createCommandSourceStack().withLevel(level).withPermission(2).withSuppressedOutput());require(appointedResult>0,"Real commander command failed");}
        catch(com.mojang.brigadier.exceptions.CommandSyntaxException failure){throw new IllegalStateException(failure);}
        require(MilitaryOrganizationSystem.data(level).commanderManual(unit),"Manual appointment marker was not committed");
        result.addProperty("manualCommander",appointed.toString());
        verifyRealStructuralImpacts();
        int[] point=site.world(30,30);var marker=new BlockPos(point[0],site.floor()+1,point[1]);level.setBlock(marker,Blocks.DIAMOND_BLOCK.defaultBlockState(),3);
        chest.clearContent();chest.setItem(0,new ItemStack(FirearmsRegistry.ammo("rifle_round"),7));chest.setChanged();
        require(level.getBlockState(marker).is(Blocks.DIAMOND_BLOCK)&&rounds(chest)==7&&items(chest)==7,"Audit edits were not applied exactly");
        result.addProperty("baseId",baseId);result.addProperty("unit",unit.toString());result.add("guards",guards);result.add("marker",writePos(marker));result.add("ammunitionChest",writePos(chest.getBlockPos()));result.addProperty("initialNaturalRifleRounds",initialRounds);result.addProperty("savedRifleRounds",7);result.addProperty("issuedSlots",2);
        progress("PHASE1_SAVED","Native start, cross-chunk chest, marker and two real organized UUIDs prepared; requesting normal saving shutdown");
        LOG.info("CITY_MILITARY_SERVER_AUDIT PHASE1_SAVED seed={} base={} guards={} result={}; restart this same run directory for durability verification",level.getSeed(),baseId,expected,RESULT);requestSavingHalt(server,"PHASE1_SAVED",base);
    }
    private static void verifyRealStructuralImpacts(){
        int[] point=site.world(32,32);var at=new BlockPos(point[0],site.floor()+30,point[1]);
        require(!level.isOutsideBuildHeight(at)&&level.getWorldBorder().isWithinBounds(at)&&level.hasChunkAt(at)&&level.isPositionEntityTicking(at),"Structural impact fixture must stay in the real loaded, ticking native footprint");
        for(int offset=-6;offset<=0;offset++)for(int y=-1;y<=1;y++)
            require(level.getBlockState(at.offset(0,y,offset)).isAir(),"Structural impact fixture needs an existing unobstructed air lane; it must not clear native structures");
        var player=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"military4-impact"));
        player.setGameMode(GameType.CREATIVE);player.setPos(at.getX()+.5,at.getY()-1,at.getZ()-5.5);
        // Invoke production hitscan impacts; this isolated fixture does not test input or ammo consumption.
        var direction=Vec3.atCenterOf(at).subtract(player.getEyePosition()).normalize();
        try{
            require(level.setBlock(at,Blocks.GLASS.defaultBlockState(),3),"Could not place the real glass impact target");
            FirearmsCombat.fire(player,WeaponCatalog.RIFLE,direction,1);
            require(level.getBlockState(at).isAir(),"One actual rifle impact did not break glass");
            require(level.setBlock(at,Blocks.OAK_PLANKS.defaultBlockState(),3),"Could not place the real wooden impact target");
            for(int shot=0;shot<3;shot++)FirearmsCombat.fire(player,WeaponCatalog.RIFLE,direction,1);
            require(level.getBlockState(at).is(Blocks.OAK_PLANKS),"Three actual rifle impacts must leave wooden cover standing");
            FirearmsCombat.fire(player,WeaponCatalog.RIFLE,direction,1);
            require(level.getBlockState(at).isAir(),"The fourth actual rifle impact did not break wood");
            result.add("structuralImpactMarker",writePos(at));result.addProperty("actualRifleGlassAndWoodVerified",true);
        }finally{player.discard();}
    }
    private static boolean savedEntitiesPresent(){
        for(var entry:result.getAsJsonArray("guards")){var guard=entry.getAsJsonObject();if(!(level.getEntity(UUID.fromString(guard.get("uuid").getAsString())) instanceof MilitarySoldier))return false;}
        return true;
    }
    private static void verifyRestart(MinecraftServer server)throws IOException{
        require(result.get("naturalStartVerified").getAsBoolean(),"Phase one did not prove native auto-generation");verifyNativeStart();naturalAmmunition();
        if(result.has("auditRevision")&&result.get("auditRevision").getAsInt()>=4){
            require(result.has("ambientFixtureMobsAtFirstMuster")&&result.get("ambientFixtureMobsAtFirstMuster").getAsInt()==AMBIENT_COUNT
                    &&result.has("unrelatedMobsAtFirstMuster")&&result.get("unrelatedMobsAtFirstMuster").getAsInt()>=AMBIENT_COUNT
                    &&result.has("ambientMobsDoNotConsumeGarrisonBudget")&&result.get("ambientMobsDoNotConsumeGarrisonBudget").getAsBoolean(),"Fresh military.4 audit lacks actual crowded-world garrison proof");
            require(result.has("actualRifleGlassAndWoodVerified")&&result.get("actualRifleGlassAndWoodVerified").getAsBoolean()&&result.has("structuralImpactMarker"),"Fresh military.4 audit lacks actual production hitscan impacts");
            require(result.has("ambientFixtureUuids")&&result.getAsJsonArray("ambientFixtureUuids").size()==AMBIENT_COUNT&&ambientFixtureCount()==AMBIENT_COUNT,"Persistent unrelated fixture mob count was lost across the actual restart");
            for(var entry:result.getAsJsonArray("ambientFixtureUuids")){
                var entity=level.getEntity(UUID.fromString(entry.getAsString()));
                require(entity instanceof Mob mob&&mob.isAlive()&&mob.getTags().contains(AMBIENT_TAG),"An original persistent unrelated mob UUID was lost across the actual restart");
            }
            require(level.getBlockState(readPos(result.getAsJsonObject("structuralImpactMarker"))).isAir(),"Gun-damaged marker block was regenerated across actual server restart");
            result.addProperty("structuralImpactDiskRestartVerified",true);result.addProperty("ambientFixtureRestartVerified",true);
        }
        var marker=readPos(result.getAsJsonObject("marker"));require(level.getBlockState(marker).is(Blocks.DIAMOND_BLOCK),"DIAMOND_BLOCK edit was lost across actual server restart");
        var at=readPos(result.getAsJsonObject("ammunitionChest"));require(level.getBlockEntity(at) instanceof ChestBlockEntity,"Saved cross-chunk ammunition chest was lost");var chest=(ChestBlockEntity)level.getBlockEntity(at);chest.unpackLootTable(null);
        require(rounds(chest)==7&&items(chest)==7,"Chest ammunition was regenerated or lost across actual restart; rounds="+rounds(chest));
        String baseId=result.get("baseId").getAsString();var base=CityMilitaryData.get(level).bases.get(baseId);require(base!=null&&base.site.equals(site)&&issued(base)==2,"Saved base or its two issued slots were lost or duplicated");
        var expected=new LinkedHashSet<UUID>();UUID unit=UUID.fromString(result.get("unit").getAsString());
        for(var entry:result.getAsJsonArray("guards")){
            var guard=entry.getAsJsonObject();int slot=guard.get("slot").getAsInt();UUID uuid=UUID.fromString(guard.get("uuid").getAsString());require(expected.add(uuid),"Duplicate expected UUID");
            require(slot>=0&&slot<base.slots.length&&uuid.equals(base.slots[slot]),"Saved slot UUID was changed or reissued");var soldier=(MilitarySoldier)level.getEntity(uuid);
            require(soldier!=null&&soldier.isAlive()&&unit.equals(soldier.unitId()),"Soldier UUID or unit did not survive disk save");
            require(soldier.getPersistentData().getString(CityMilitaryService.BASE_TAG).equals(baseId),"Saved soldier lost its base ownership");
            if(guard.has("role"))require(soldier.commandRole().id().equals(guard.get("role").getAsString())&&soldier.equipped()==guard.get("equipped").getAsBoolean(),"Role or uniform was lost across actual server restart");
            else{
                require(soldier.commandRole()==com.codex.blacklight.military.MilitaryRole.migrate(soldier.rank(),com.codex.blacklight.military.MilitaryRole.TROOPER),"Legacy military.2 rank-to-role migration changed the saved identity incorrectly");
                require(MilitaryOrganizationSystem.data(level).commanderManual(unit),"Legacy nonautomatic base lost conservative historical command protection");
                result.addProperty("legacyRoleMigrationVerified",true);
            }
            require(soldier.magazineAmmo()==guard.get("magazine").getAsInt()&&soldier.reserveAmmo()==guard.get("reserve").getAsInt(),"Soldier ammunition reset across server restart");
        }
        require(expected.size()==2&&loadedBaseUuids(baseId).equals(expected),"Restart created duplicate or replacement garrison entities");
        require(MilitaryOrganizationSystem.organization(level).unit(unit).orElseThrow(()->new IllegalStateException("Actual or saved garrison organization is missing")).members().keySet().equals(expected),"Saved organization no longer contains exactly the original two soldiers");
        if(result.has("manualCommander")){
            require(MilitaryOrganizationSystem.data(level).commanderManual(unit)&&MilitaryOrganizationSystem.organization(level).unit(unit).orElseThrow().commander().equals(UUID.fromString(result.get("manualCommander").getAsString())),"Lower-ranked explicit commander or manual marker was lost across disk restart");
            result.addProperty("officerRoleUniformManualAppointmentRestartVerified",true);
        }
        CityMilitaryService.register(level,site,checkpoint);CityMilitaryService.register(level,site,checkpoint);
        require(issued(base)==2&&loadedBaseUuids(baseId).equals(expected)&&rounds(chest)==7&&items(chest)==7&&level.getBlockState(marker).is(Blocks.DIAMOND_BLOCK),"Repeated discovery overwrote saved edits, ammunition or UUIDs");
        if(result.has("structuralImpactMarker"))require(level.getBlockState(readPos(result.getAsJsonObject("structuralImpactMarker"))).isAir(),"Repeated discovery regenerated a gun-damaged marker block");
        result.addProperty("restartVerified",true);result.addProperty("persistedMarker",true);result.addProperty("persistedSevenRounds",true);result.addProperty("uniqueSavedGuardUUIDs",true);result.addProperty("savedOrganizationVerified",true);
        progress("PASS","Two ordinary dedicated-server starts verified native generation, edits, finite ammunition, two UUIDs and organization without duplication");
        LOG.info("CITY_MILITARY_SERVER_AUDIT PASS seed={} base={} guards={} result={}",level.getSeed(),baseId,expected,RESULT);requestSavingHalt(server,"PASS",base);
    }
    private static void requestSavingHalt(MinecraftServer server,String checkpoint,CityMilitaryData.Base base)throws IOException{
        homesHaltCheckpoint=checkpoint;
        if(!CityMilitaryServerAuditHomes.beforeHalt(server,checkpoint,level,base)){stage=Stage.WAIT_HOMES;return;}
        stage=Stage.DONE;server.halt(false);
    }
    private static void continueHomes(MinecraftServer server)throws IOException{
        var base=CityMilitaryData.get(level).bases.get(result.get("baseId").getAsString());
        requestSavingHalt(server,homesHaltCheckpoint,base);
    }
    private static Set<UUID> loadedBaseUuids(String baseId){
        var found=new LinkedHashSet<UUID>();int count=0;
        for(var entity:level.getAllEntities())if(entity instanceof MilitarySoldier soldier&&soldier.getPersistentData().getString(CityMilitaryService.BASE_TAG).equals(baseId)){count++;require(found.add(soldier.getUUID()),"Two loaded soldiers have one UUID");}
        require(count==found.size(),"Loaded soldier count differs from its unique UUID set");return found;
    }
    private static long issued(CityMilitaryData.Base base){return Arrays.stream(base.slots).filter(Objects::nonNull).count();}
    private static int items(ChestBlockEntity chest){int count=0;for(int slot=0;slot<chest.getContainerSize();slot++)count+=chest.getItem(slot).getCount();return count;}
    private static int rounds(ChestBlockEntity chest){int count=0;for(int slot=0;slot<chest.getContainerSize();slot++)if(chest.getItem(slot).is(FirearmsRegistry.ammo("rifle_round")))count+=chest.getItem(slot).getCount();return count;}
    private static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath("blacklight",path);}
    private static JsonObject writePos(BlockPos p){var j=new JsonObject();j.addProperty("x",p.getX());j.addProperty("y",p.getY());j.addProperty("z",p.getZ());return j;}
    private static BlockPos readPos(JsonObject p){return new BlockPos(p.get("x").getAsInt(),p.get("y").getAsInt(),p.get("z").getAsInt());}
    private static JsonObject writeSite(CityMilitaryPlan.Site s){var j=new JsonObject();j.addProperty("x",s.x());j.addProperty("z",s.z());j.addProperty("floor",s.floor());j.addProperty("rotation",s.rotation());j.addProperty("blackwatch",s.blackwatch());return j;}
    private static CityMilitaryPlan.Site readSite(JsonObject j){return new CityMilitaryPlan.Site(j.get("x").getAsInt(),j.get("z").getAsInt(),j.get("floor").getAsInt(),j.get("rotation").getAsInt(),j.get("blackwatch").getAsBoolean());}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
    private static void progress(String status,String detail)throws IOException{result.addProperty("status",status);result.addProperty("detail",detail);result.addProperty("updatedUtc",Instant.now().toString());write();LOG.info("CITY_MILITARY_SERVER_AUDIT {} {}",status,detail);}
    private static void write()throws IOException{
        var temporary=RESULT.resolveSibling(RESULT.getFileName()+".tmp");Files.writeString(temporary,JSON.toJson(result)+"\n",StandardCharsets.UTF_8);
        try{Files.move(temporary,RESULT,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException unsupported){Files.move(temporary,RESULT,StandardCopyOption.REPLACE_EXISTING);}
    }
    private static void fail(MinecraftServer server,Throwable failure){
        CityMilitaryServerAuditHomes.failed(failure);
        stage=Stage.DONE;LOG.error("CITY_MILITARY_SERVER_AUDIT FAIL result="+RESULT,failure);
        try{var trace=new StringWriter();failure.printStackTrace(new PrintWriter(trace));result.addProperty("status","FAIL");result.addProperty("error",failure.toString());result.addProperty("stackTrace",trace.toString());result.addProperty("updatedUtc",Instant.now().toString());write();}catch(Throwable writingFailure){LOG.error("CITY_MILITARY_SERVER_AUDIT FAIL_JSON_WRITE",writingFailure);}
        server.halt(false);
    }
    private CityMilitaryServerAudit(){}
}
