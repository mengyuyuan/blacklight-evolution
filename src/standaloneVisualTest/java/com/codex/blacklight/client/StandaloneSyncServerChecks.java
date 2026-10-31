package com.codex.blacklight.client;

import com.codex.blacklight.BlacklightNeoMod;
import com.codex.blacklight.animation.NativePlayerActionBackend;
import com.codex.blacklight.combat.*;
import com.codex.blacklight.movement.MovementState;
import com.codex.blacklight.power.*;
import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.state.BlacklightState;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
/** Dedicated-only, disposable-world observation of real production C2S handlers.
 * This class deliberately has no client classes, callbacks or client linkage. */
@EventBusSubscriber(modid="blacklight",value=Dist.DEDICATED_SERVER)
public final class StandaloneSyncServerChecks {
    private static final String PROPERTY="blacklight.test.standaloneSync";
    private static final Path SERVER=Path.of(System.getProperty("blacklight.test.qaDirectory","D:/Blacklight-Standalone/run-dev10-dedicated")).toAbsolutePath().normalize();
    private static final Path OUTPUT=Path.of(System.getProperty(PROPERTY,"D:/Blacklight-Standalone/sync-dev10-native")).toAbsolutePath().normalize();
    private static final Map<BlockPos,BlockState> blocks=new LinkedHashMap<>();
    private static final Set<UUID> createdEntities=new HashSet<>();
    private static final Map<UUID,Boolean> otherPreferences=new HashMap<>();
    private static final JsonArray settings=new JsonArray(),samples=new JsonArray(),impacts=new JsonArray();
    private static boolean initialized,completed,failed,defaultOn,originalDrops;
    private static long began,stageAt,session=-1,finishedAt;
    private static String stage="WAIT_PLAYER",error;
    private static UUID owner;
    private static int caseIndex,highestHits;
    private static boolean seenCharge,seenFlight,seenLand;
    private static Cow victim;
    private static final List<Cow> victims=new ArrayList<>();
    private static BlockPos center;
    private static float initialHealth;
    private static double landingY;
    private static BlacklightState oldState;
    private static SkillSettings oldSettings;
    private static GameType oldMode;
    private static Vec3 oldPosition;
    private StandaloneSyncServerChecks(){}

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void tick(net.minecraftforge.event.TickEvent.ServerTickEvent event){if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        if(System.getProperty(PROPERTY,"").isBlank())return;
        var server=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if(server==null)return;
        try{
            if(!initialized){
                if(!server.isDedicatedServer()||server.getPort()!=25568
                        ||!server.getServerDirectory().toPath().toAbsolutePath().normalize().equals(SERVER)
                        ||!server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().equals(SERVER.resolve("QA-Sync-Dev10"))
                        ||!Path.of(System.getProperty(PROPERTY)).toAbsolutePath().normalize().equals(OUTPUT))
                    throw new IllegalStateException("Dedicated synchronization fixture refused directory/world/port/output");
                Files.createDirectories(OUTPUT);began=server.overworld().getGameTime();stageAt=began;initialized=true;write(server,null);
            }
            if(completed){
                if(server.overworld().getGameTime()-finishedAt>=100)server.halt(false);
                return;
            }
            require(server.overworld().getGameTime()-began<(owner==null?12000:6000),"dedicated synchronization fixture exceeded its connection/action deadline");
            var p=owner==null?server.getPlayerList().getPlayers().stream().filter(x->x.getGameProfile().getName().equals("Dev")).findFirst().orElse(null):server.getPlayerList().getPlayer(owner);
            if(p==null){if(owner!=null)throw new IllegalStateException("QA owner disconnected before completion");if(server.overworld().getGameTime()%20==0)write(server,null);return;}
            if(owner==null){
                began=server.overworld().getGameTime();
                owner=p.getUUID();oldState=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE);oldSettings=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.SKILL_SETTINGS);oldMode=p.gameMode.getGameModeForPlayer();oldPosition=p.position();
                defaultOn=oldSettings.bulletdiveBlockBreaking();require(defaultOn,"fresh dedicated player did not default block breaking ON");
                for(var other:server.getPlayerList().getPlayers())if(other!=p)otherPreferences.put(other.getUUID(),com.codex.blacklight.registry.ModAttachments.getData(other, ModAttachments.SKILL_SETTINGS).bulletdiveBlockBreaking());
                originalDrops=server.overworld().getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS);
                server.overworld().getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS).set(true,server);
                var state=new BlacklightState();state.infect();state.setPrototypeMode(true);state.activate(PowerForm.NONE);state.addEvolutionPoints(20_000);
                state.buyMovementUpgrade(MovementUpgrade.GLIDE_1);for(var skill:CombatSkill.values())state.buyCombatSkill(skill);
                com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.PLAYER_STATE,state);com.codex.blacklight.registry.ModAttachments.syncData(p, ModAttachments.PLAYER_STATE);
                com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.CRITICAL_MASS,new CriticalMassState(4,true));com.codex.blacklight.registry.ModAttachments.syncData(p, ModAttachments.CRITICAL_MASS);
                com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.ADRENALINE,AdrenalineState.EMPTY);com.codex.blacklight.registry.ModAttachments.syncData(p, ModAttachments.ADRENALINE);
                p.setGameMode(GameType.CREATIVE);p.getAbilities().flying=false;p.onUpdateAbilities();p.getInventory().clearContent();
                setStage(server,"WAIT_OFF");
            }
            for(var entry:otherPreferences.entrySet()){
                var other=server.getPlayerList().getPlayer(entry.getKey());if(other!=null)require(com.codex.blacklight.registry.ModAttachments.getData(other, ModAttachments.SKILL_SETTINGS).bulletdiveBlockBreaking()==entry.getValue(),"C2S changed another real player's preferences");
            }
            boolean enabled=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.SKILL_SETTINGS).bulletdiveBlockBreaking();
            if(stage.equals("WAIT_OFF")&&!enabled){recordSetting(p,false,"off_received");prepare(p,0);setStage(server,"OFF_READY");}
            else if(stage.equals("WAIT_ON")&&enabled){recordSetting(p,true,"on_received");prepare(p,1);setStage(server,"ON_READY");}
            else if(stage.equals("WAIT_FINAL_OFF")&&!enabled){recordSetting(p,false,"final_off_saved");cleanup(p);setStage(server,"WAIT_RETURN_ACK");write(server,p);return;}
            if(stage.equals("WAIT_RETURN_ACK")){
                require(p.level().getGameTime()-stageAt<200,"ordinary return teleport was not acknowledged");
                if(pendingCorrection(p).equals("null")&&p.position().distanceToSqr(oldPosition)<1){
                    completed=true;finishedAt=server.overworld().getGameTime();stage="COMPLETE";write(server,p);return;
                }
            }
            if(stage.equals("OFF_READY")||stage.equals("ON_READY")){
                var visual=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.DEVASTATOR_VISUAL);var probe=DevastatorSystem.snapshot(p);
                if(visual.kind()==3){
                    if(visual.phase()==DevastatorSystem.LAND&&!seenLand)landingY=p.getY();
                    session=visual.session();seenCharge|=visual.phase()==DevastatorSystem.CHARGE;seenFlight|=visual.phase()==DevastatorSystem.FLIGHT;seenLand|=visual.phase()==DevastatorSystem.LAND;
                    if(probe!=null)highestHits=Math.max(highestHits,probe.hits());
                    if(visual.phase()==DevastatorSystem.FLIGHT&&p.getY()>251){
                        // A real immutable support follows glide drift before collision;
                        // no post-takeoff position/velocity/onGround assignment occurs.
                        var support=BlockPos.containing(p.getX(),250,p.getZ());remember(p,support);p.serverLevel().setBlockAndUpdate(support,Blocks.BEDROCK.defaultBlockState());
                    }
                }
                var row=new JsonObject();row.addProperty("tick",p.level().getGameTime());row.addProperty("case",caseIndex);row.addProperty("kind",visual.kind());row.addProperty("phase",visual.phase());row.addProperty("session",visual.session());row.addProperty("released",visual.released());row.addProperty("y",p.getY());row.addProperty("onGround",p.onGround());row.addProperty("fallHeight",visual.fallHeight());row.addProperty("downSpeed",visual.downSpeed());row.addProperty("serverHits",probe==null?highestHits:probe.hits());
                if(seenLand&&probe!=null&&probe.age()<=.3){
                    row.addProperty("age",probe.age());row.addProperty("pendingCorrection",pendingCorrection(p));
                    row.addProperty("damagedTargets",victims.stream().filter(v->v.getHealth()<initialHealth).count());
                }
                if(samples.size()<4000)samples.add(row);
                require(!seenLand||visual.kind()!=3||visual.phase()!=DevastatorSystem.LAND||p.getY()<=landingY+.5,
                        "server-owned landing moved upward: landedY="+landingY+" actual="+p.position()+" pending="+pendingCorrection(p));
                var job=session<0?null:BulletdiveTerrain.snapshot(p,session);
                if(seenLand&&visual.kind()<0&&(caseIndex==0||job!=null&&job.complete())){
                    // Static targets cover real glide drift; no target/actor is
                    // moved into a successful collision after takeoff.
                    victim=victims.stream().filter(v->v.getHealth()<initialHealth).findFirst().orElse(victim);
                    int changed=changed(p),loot=loot(p);var impact=new JsonObject();impact.addProperty("case",caseIndex);impact.addProperty("breakingEnabled",caseIndex==1);impact.addProperty("session",session);impact.addProperty("initialTargetHealth",initialHealth);impact.addProperty("finalTargetHealth",victim.getHealth());impact.addProperty("serverHits",highestHits);impact.addProperty("airBlocksInSubstrate",changed);impact.addProperty("liveItemEntities",loot);impact.addProperty("terrainComplete",job!=null&&job.complete());
                    require(seenCharge&&seenFlight,"real server action missed charge or flight");require(victim!=null&&victim.getHealth()<initialHealth&&highestHits>0,"actual impact missed all stationary targets");
                    if(caseIndex==0){require(job==null&&changed==0&&loot==0,"OFF impact broke blocks or spawned terrain loot");}
                    else{require(job!=null&&job.complete()&&job.destroyed()>0&&changed>0&&loot>0,"ON impact lacked actual blocks and live drops");impact.addProperty("destroyed",job.destroyed());impact.addProperty("terrainReason",job.reason());}
                    impacts.add(impact);setStage(server,caseIndex==0?"WAIT_ON":"WAIT_FINAL_OFF");
                }
                require(p.level().getGameTime()-stageAt<1800,"real Bullet action/terrain failed to finish within 90 seconds");
            }
            if(server.overworld().getGameTime()%3==0)write(server,p);
        }catch(Throwable problem){
            if(failed)return;failed=true;error=problem.toString();BlacklightNeoMod.LOGGER.error("Dedicated sync QA failed",problem);
            if(initialized){var p=owner==null?null:server.getPlayerList().getPlayer(owner);if(p!=null)cleanup(p);completed=true;finishedAt=server.overworld().getGameTime();stage="FAILED";write(server,p);}
            // A rejected directory is never stopped or mutated.
        }
    }
    private static void setStage(MinecraftServer server,String next){stage=next;stageAt=server.overworld().getGameTime();}
    private static void prepare(ServerPlayer p,int which){
        caseIndex=which;session=-1;highestHits=0;seenCharge=seenFlight=seenLand=false;
        DevastatorSystem.cancel(p);WeaponCombatSystem.cancel(p);NativePlayerActionBackend.clear(p);CombatTargeting.requestLock(p,-1);com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.MOVEMENT_STATE).reset();
        com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.GLIDE_UNTIL,0L);com.codex.blacklight.registry.ModAttachments.syncData(p, ModAttachments.GLIDE_UNTIL);
        center=new BlockPos(0,250,which*48);var level=p.serverLevel();
        for(int x=-14;x<=14;x++)for(int z=-14;z<=14;z++)for(int y=247;y<=252;y++){
            var pos=new BlockPos(x,y,center.getZ()+z);level.getChunk(pos.getX()>>4,pos.getZ()>>4);remember(p,pos);
            level.setBlockAndUpdate(pos,(y<=250?Blocks.DIRT:Blocks.AIR).defaultBlockState());
        }
        level.setBlockAndUpdate(center,Blocks.BEDROCK.defaultBlockState());
        victims.clear();
        for(int x=-12;x<=12;x+=6)for(int z=-12;z<=12;z+=6){
            var target=new Cow(EntityType.COW,level);target.setNoAi(true);target.setNoGravity(true);target.setPersistenceRequired();target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20_000);target.setHealth(20_000);target.moveTo(x+.5,251,center.getZ()+z+.5,180,0);level.addFreshEntity(target);createdEntities.add(target.getUUID());victims.add(target);
        }
        victim=victims.get(12);initialHealth=victim.getHealth();
        p.setHealth(p.getMaxHealth());p.connection.teleport(.5,331,center.getZ()+.5,0,0);p.setDeltaMovement(Vec3.ZERO);p.setOnGround(false);p.fallDistance=0;
    }
    private static void remember(ServerPlayer p,BlockPos pos){
        if(blocks.containsKey(pos))return;require(p.serverLevel().getBlockEntity(pos)==null,"QA fixture refuses a pre-existing block entity");blocks.put(pos.immutable(),p.level().getBlockState(pos));
    }
    private static int changed(ServerPlayer p){int count=0;for(int x=-14;x<=14;x++)for(int z=-14;z<=14;z++)for(int y=247;y<=250;y++)if(p.level().getBlockState(new BlockPos(x,y,center.getZ()+z)).isAir())count++;return count;}
    private static int loot(ServerPlayer p){
        // The elevated test pad opens into air; drops can leave an eight-block
        // vertical observation box before the landing recovery finishes.
        var area=new AABB(center.getX()-22D,p.level().getMinBuildHeight(),center.getZ()-22D,
                center.getX()+23D,center.getY()+9D,center.getZ()+23D);
        var items=p.serverLevel().getEntitiesOfClass(ItemEntity.class,area);int count=0;
        for(var item:items){if(item.getItem().is(net.minecraft.world.item.Items.DIRT)){count++;createdEntities.add(item.getUUID());}}
        return count;
    }
    private static void recordSetting(ServerPlayer p,boolean expected,String reason)throws Exception{
        var tag=p.saveWithoutId(new CompoundTag());var saved=findSettings(tag,0);
        require(saved!=null&&SkillSettings.CODEC.parse(NbtOps.INSTANCE,saved).getOrThrow(false, message -> { throw new AssertionError(message); }).bulletdiveBlockBreaking()==expected,"actual attachment serialization mismatch");
        p.server.getPlayerList().saveAll();var path=p.server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(p.getUUID()+".dat");
        require(Files.exists(path),"settings were not written to a player file");var disk=findSettings(NbtIo.readCompressed(path.toFile()),0);
        require(disk!=null&&SkillSettings.CODEC.parse(NbtOps.INSTANCE,disk).getOrThrow(false, message -> { throw new AssertionError(message); }).bulletdiveBlockBreaking()==expected,"disk player attachment mismatch");
        var row=new JsonObject();row.addProperty("tick",p.level().getGameTime());row.addProperty("callerUuid",p.getUUID().toString());row.addProperty("reason",reason);row.addProperty("serverAttachment",expected);row.addProperty("serializedAttachment",saved.toString());row.addProperty("diskAttachment",disk.toString());row.addProperty("savedPlayerFile",path.toString());settings.add(row);
    }
    private static CompoundTag findSettings(CompoundTag tag,int depth){
        var fields=tag.getCompound("ForgeCaps").getCompound("blacklight:entity_state");
        return fields.contains("skill_settings",Tag.TAG_COMPOUND)?fields.getCompound("skill_settings"):null;
    }
    private static void cleanup(ServerPlayer p){
        DevastatorSystem.cancel(p);WeaponCombatSystem.cancel(p);NativePlayerActionBackend.clear(p);
        for(var entry:blocks.entrySet())p.serverLevel().setBlockAndUpdate(entry.getKey(),entry.getValue());blocks.clear();
        for(UUID id:createdEntities){var entity=p.serverLevel().getEntity(id);if(entity!=null)entity.discard();}createdEntities.clear();
        p.server.overworld().getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS).set(originalDrops,p.server);
        if(oldState!=null){com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.PLAYER_STATE,oldState);com.codex.blacklight.registry.ModAttachments.syncData(p, ModAttachments.PLAYER_STATE);p.setGameMode(oldMode);p.connection.teleport(oldPosition.x,oldPosition.y,oldPosition.z,0,0);}
        // Keep final OFF preference as the explicit save/rejoin persistence probe.
    }
    private static void write(MinecraftServer server,ServerPlayer p){
        if(!initialized)return;
        try{var report=new JsonObject();report.addProperty("schema",1);report.addProperty("stage",stage);report.addProperty("complete",completed);report.addProperty("passed",completed&&!failed&&impacts.size()==2&&settings.size()==3);report.addProperty("dedicated",server.isDedicatedServer());report.addProperty("port",server.getPort());report.addProperty("tick",server.overworld().getGameTime());report.addProperty("owner",owner==null?"":owner.toString());report.addProperty("defaultOn",defaultOn);report.addProperty("observedOtherRealPlayers",otherPreferences.size());report.addProperty("ownerScopeEvidence",otherPreferences.isEmpty()?"only one real client; packet has no selectable owner, other-player runtime assertion not exercised":"unchanged preferences checked for other connected UUIDs");report.addProperty("case",caseIndex);report.addProperty("session",session);if(p!=null){report.addProperty("serverSetting",com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.SKILL_SETTINGS).bulletdiveBlockBreaking());report.addProperty("gliding",com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.MOVEMENT_STATE).mode()==MovementState.Mode.GLIDE);report.addProperty("targetId",victim==null?-1:victim.getId());}
            report.add("settings",settings);report.add("serverSamples",samples);report.add("impacts",impacts);if(error!=null)report.addProperty("error",error);
            var tmp=OUTPUT.resolve("sync-server.pending.json");Files.writeString(tmp,new GsonBuilder().setPrettyPrinting().create().toJson(report));Files.move(tmp,OUTPUT.resolve("sync-server-report.json"),StandardCopyOption.REPLACE_EXISTING);
        }catch(Exception problem){BlacklightNeoMod.LOGGER.error("Cannot write dedicated sync report",problem);}
    }
    private static String pendingCorrection(ServerPlayer p){
        for(String name:new String[]{"awaitingPositionFromClient","f_9766_"}){
            try{var field=net.minecraft.server.network.ServerGamePacketListenerImpl.class.getDeclaredField(name);field.setAccessible(true);return String.valueOf(field.get(p.connection));}
            catch(NoSuchFieldException ignored){}catch(IllegalAccessException failure){throw new IllegalStateException(failure);}
        }
        throw new IllegalStateException("No mapped pending teleport field for QA observation");
    }
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
}
