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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Dedicated-only, disposable-world observation of real production C2S handlers.
 * This class deliberately has no client classes, callbacks or client linkage. */
@EventBusSubscriber(modid="blacklight",value=Dist.DEDICATED_SERVER)
public final class StandaloneSyncServerChecks {
    private static final String PROPERTY="blacklight.test.standaloneSync";
    private static final Path SERVER=Path.of(System.getProperty("blacklight.test.qaDirectory","missing-sync-server-directory")).toAbsolutePath().normalize();
    private static final Path OUTPUT=Path.of(System.getProperty(PROPERTY,"missing-sync-output-directory")).toAbsolutePath().normalize();
    private static final Map<BlockPos,BlockState> blocks=new LinkedHashMap<>();
    private static final Set<UUID> createdEntities=new HashSet<>();
    private static final Map<UUID,Boolean> otherPreferences=new HashMap<>();
    private static final JsonArray settings=new JsonArray(),samples=new JsonArray(),impacts=new JsonArray();
    private static final JsonArray acceptedDamage=new JsonArray();
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
    private static double landingX,landingY,landingZ;
    private static BlacklightState oldState;
    private static SkillSettings oldSettings;
    private static GameType oldMode;
    private static Vec3 oldPosition;
    private StandaloneSyncServerChecks(){}

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void damage(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post event){
        if(!initialized||completed||System.getProperty(PROPERTY,"").isBlank()||!victims.contains(event.getEntity()))return;
        var hit=new JsonObject();hit.addProperty("tick",event.getEntity().level().getGameTime());hit.addProperty("case",caseIndex);hit.addProperty("target",event.getEntity().getId());hit.addProperty("damage",event.getNewDamage());hit.addProperty("healthAfter",event.getEntity().getHealth());hit.addProperty("context",String.valueOf(DevastatorSystem.hitContext()));acceptedDamage.add(hit);
    }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void tick(ServerTickEvent.Post event){
        if(System.getProperty(PROPERTY,"").isBlank())return;
        var server=event.getServer();
        try{
            if(!initialized){
                if(!server.isDedicatedServer()||server.getPort()!=25568
                        ||!server.getServerDirectory().toAbsolutePath().normalize().equals(SERVER)
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
                owner=p.getUUID();oldState=p.getData(ModAttachments.PLAYER_STATE);oldSettings=p.getData(ModAttachments.SKILL_SETTINGS);oldMode=p.gameMode.getGameModeForPlayer();oldPosition=p.position();
                defaultOn=oldSettings.bulletdiveBlockBreaking();require(defaultOn,"fresh dedicated player did not default block breaking ON");
                for(var other:server.getPlayerList().getPlayers())if(other!=p)otherPreferences.put(other.getUUID(),other.getData(ModAttachments.SKILL_SETTINGS).bulletdiveBlockBreaking());
                originalDrops=server.overworld().getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS);
                server.overworld().getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS).set(true,server);
                var state=new BlacklightState();state.infect();state.setPrototypeMode(true);state.activate(PowerForm.NONE);state.addEvolutionPoints(20_000);
                state.buyMovementUpgrade(MovementUpgrade.GLIDE_1);for(var skill:CombatSkill.values())state.buyCombatSkill(skill);
                p.setData(ModAttachments.PLAYER_STATE,state);p.syncData(ModAttachments.PLAYER_STATE);
                p.setData(ModAttachments.CRITICAL_MASS,new CriticalMassState(4,true));p.syncData(ModAttachments.CRITICAL_MASS);
                p.setData(ModAttachments.ADRENALINE,AdrenalineState.EMPTY);p.syncData(ModAttachments.ADRENALINE);
                p.setGameMode(GameType.CREATIVE);p.getAbilities().flying=false;p.onUpdateAbilities();p.getInventory().clearContent();
                setStage(server,"WAIT_OFF");
            }
            for(var entry:otherPreferences.entrySet()){
                var other=server.getPlayerList().getPlayer(entry.getKey());if(other!=null)require(other.getData(ModAttachments.SKILL_SETTINGS).bulletdiveBlockBreaking()==entry.getValue(),"C2S changed another real player's preferences");
            }
            boolean enabled=p.getData(ModAttachments.SKILL_SETTINGS).bulletdiveBlockBreaking();
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
                var visual=p.getData(ModAttachments.DEVASTATOR_VISUAL);var probe=DevastatorSystem.snapshot(p);
                if(visual.kind()==3){
                    if(visual.phase()==DevastatorSystem.LAND&&!seenLand){
                        landingX=p.getX();landingY=p.getY();landingZ=p.getZ();
                        var contact=new JsonObject();contact.addProperty("actor",p.position().toString());contact.addProperty("eye",p.getEyePosition().toString());contact.addProperty("box",p.getBoundingBox().toString());contact.addProperty("radius",visual.radius());contact.addProperty("visualOrigin",visual.origin().toString());contact.addProperty("selected",visual.contacts().toString());contact.addProperty("snapshot",String.valueOf(probe));
                        contact.addProperty("timing",com.codex.blacklight.combat.DevastatorTiming.of(com.codex.blacklight.combat.WeaponMove.BULLETDIVE_DROP).toString());
                        contact.addProperty("rawCandidates",CombatTargeting.candidates(p,p.getBoundingBox().inflate(visual.radius()),v->true).size());
                        var checks=new JsonArray();for(var target:victims){var v=new JsonObject();v.addProperty("id",target.getId());v.addProperty("pos",target.position().toString());v.addProperty("alive",target.isAlive());v.addProperty("invulnerable",target.isInvulnerable());v.addProperty("allied",p.isAlliedTo(target));v.addProperty("damageable",CombatTargeting.damageable(p,target));v.addProperty("visible",CombatTargeting.visibleWithinRange(p,target,visual.radius()));v.addProperty("distance",target.position().distanceTo(p.position()));checks.add(v);}contact.add("targets",checks);
                        Files.writeString(OUTPUT.resolve("case"+caseIndex+"-first-land.json"),new GsonBuilder().setPrettyPrinting().create().toJson(contact));
                    }
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
                    row.addProperty("age",probe.age());row.addProperty("pos",p.position().toString());row.addProperty("eye",p.getEyePosition().toString());row.addProperty("contacts",visual.contacts().toString());
                    row.addProperty("pendingCorrection",pendingCorrection(p));row.addProperty("chargeTicks",p.getData(ModAttachments.MOVEMENT_STATE).chargeTicks());row.addProperty("movementMode",p.getData(ModAttachments.MOVEMENT_STATE).mode().name());
                    row.addProperty("rawCandidates",CombatTargeting.candidates(p,p.getBoundingBox().inflate(visual.radius()),v->true).size());
                    row.addProperty("visibleEligibleTargets",victims.stream().filter(v->CombatTargeting.damageable(p,v)&&CombatTargeting.visibleWithinRange(p,v,visual.radius())).count());
                    row.addProperty("damagedTargets",victims.stream().filter(v->v.getHealth()<initialHealth).count());
                }
                if(samples.size()<4000)samples.add(row);
                require(!seenLand||visual.kind()!=3||visual.phase()!=DevastatorSystem.LAND||p.getY()<=landingY+.5,
                        "server-owned landing moved upward: landedY="+landingY+" actual="+p.position()+" pending="+pendingCorrection(p));
                var job=session<0?null:BulletdiveTerrain.snapshot(p,session);
                if(seenLand&&visual.kind()<0&&(caseIndex==0||job!=null&&job.complete())){
                    // Glide produces genuine horizontal drift. Use stationary
                    // targets spread over the prepared pad, never move a target
                    // or the actor into a successful collision after takeoff.
                    victim=victims.stream().filter(v->v.getHealth()<initialHealth).findFirst().orElse(victim);
                    int changed=changed(p),loot=loot(p);var impact=new JsonObject();impact.addProperty("case",caseIndex);impact.addProperty("breakingEnabled",caseIndex==1);impact.addProperty("session",session);impact.addProperty("initialTargetHealth",initialHealth);impact.addProperty("finalTargetHealth",victim.getHealth());impact.addProperty("serverHits",highestHits);impact.addProperty("airBlocksInSubstrate",changed);impact.addProperty("liveItemEntities",loot);impact.addProperty("terrainComplete",job!=null&&job.complete());
                    var targets=new JsonArray();for(var target:victims){var t=new JsonObject();t.addProperty("id",target.getId());t.addProperty("x",target.getX());t.addProperty("y",target.getY());t.addProperty("z",target.getZ());t.addProperty("health",target.getHealth());targets.add(t);}impact.add("stationaryTargets",targets);
                    impact.addProperty("landingX",landingX);impact.addProperty("landingZ",landingZ);
                    impact.addProperty("landingInsidePreparedSubstrate",Math.abs(landingX)<=14&&Math.abs(landingZ-center.getZ())<=14);
                    int dirtInInventory=java.util.stream.IntStream.range(0,p.getInventory().getContainerSize()).map(i->{var item=p.getInventory().getItem(i);return item.is(net.minecraft.world.item.Items.DIRT)?item.getCount():0;}).sum();
                    impact.addProperty("dirtInPlayerInventory",dirtInInventory);
                    if(job!=null){impact.addProperty("terrainSnapshot",job.toString());BlacklightNeoMod.LOGGER.info("Dedicated terrain evidence: changed={} loot={} inventoryDirt={} landing={},{} snapshot={} alive={} mayBuild={} infected={} prototype={} skill={} removed={}",changed,loot,dirtInInventory,landingX,landingZ,job,p.isAlive(),p.mayBuild(),p.getData(ModAttachments.PLAYER_STATE).isInfected(),p.getData(ModAttachments.PLAYER_STATE).isPrototypeMode(),p.getData(ModAttachments.PLAYER_STATE).hasCombatSkill(CombatSkill.BULLETDIVE_DROP),p.isRemoved());}
                    impacts.add(impact);
                    require(seenCharge&&seenFlight,"real server action missed charge or flight");require(victim!=null&&victim.getHealth()<initialHealth&&highestHits>0,"actual impact missed all stationary targets");
                    if(caseIndex==0){require(job==null&&changed==0&&loot==0,"OFF impact broke blocks or spawned terrain loot");}
                    else{require(job!=null&&job.complete()&&job.destroyed()>0&&changed>0&&loot>0,"ON impact lacked actual blocks and live drops");impact.addProperty("destroyed",job.destroyed());impact.addProperty("terrainReason",job.reason());}
                    setStage(server,caseIndex==0?"WAIT_ON":"WAIT_FINAL_OFF");
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
        landingX=landingY=landingZ=Double.NaN;
        DevastatorSystem.cancel(p);WeaponCombatSystem.cancel(p);NativePlayerActionBackend.clear(p);CombatTargeting.requestLock(p,-1);p.getData(ModAttachments.MOVEMENT_STATE).reset();
        p.setData(ModAttachments.GLIDE_UNTIL,0L);p.syncData(ModAttachments.GLIDE_UNTIL);
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
        // This is an elevated four-block test pad. Real drops fall through its
        // crater during the landing recovery, so an eight-block vertical box
        // missed live entities (r4 saved six dirt entities at Y184..194).
        // Keep the original horizontal bounds and inspect the loaded column.
        var area=new AABB(center.getX()-22D,p.level().getMinBuildHeight(),center.getZ()-22D,
                center.getX()+23D,center.getY()+9D,center.getZ()+23D);
        var items=p.serverLevel().getEntitiesOfClass(ItemEntity.class,area);int count=0;
        for(var item:items){if(item.getItem().is(net.minecraft.world.item.Items.DIRT)){count++;createdEntities.add(item.getUUID());}}
        return count;
    }
    private static void recordSetting(ServerPlayer p,boolean expected,String reason)throws Exception{
        var tag=p.saveWithoutId(new CompoundTag());var saved=findSettings(tag,0);
        require(saved!=null&&SkillSettings.CODEC.parse(NbtOps.INSTANCE,saved).getOrThrow().bulletdiveBlockBreaking()==expected,"actual attachment serialization mismatch");
        p.server.getPlayerList().saveAll();var path=p.server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(p.getUUID()+".dat");
        require(Files.exists(path),"settings were not written to a player file");var disk=findSettings(NbtIo.readCompressed(path,NbtAccounter.unlimitedHeap()),0);
        require(disk!=null&&SkillSettings.CODEC.parse(NbtOps.INSTANCE,disk).getOrThrow().bulletdiveBlockBreaking()==expected,"disk player attachment mismatch");
        var row=new JsonObject();row.addProperty("tick",p.level().getGameTime());row.addProperty("callerUuid",p.getUUID().toString());row.addProperty("reason",reason);row.addProperty("serverAttachment",expected);row.addProperty("serializedAttachment",saved.toString());row.addProperty("diskAttachment",disk.toString());row.addProperty("savedPlayerFile",path.toString());settings.add(row);
    }
    private static CompoundTag findSettings(CompoundTag tag,int depth){
        if(depth>8)return null;if(tag.contains("blacklight:skill_settings",Tag.TAG_COMPOUND))return tag.getCompound("blacklight:skill_settings");
        for(String key:tag.getAllKeys())if(tag.contains(key,Tag.TAG_COMPOUND)){var found=findSettings(tag.getCompound(key),depth+1);if(found!=null)return found;}return null;
    }
    private static void cleanup(ServerPlayer p){
        DevastatorSystem.cancel(p);WeaponCombatSystem.cancel(p);NativePlayerActionBackend.clear(p);
        for(var entry:blocks.entrySet())p.serverLevel().setBlockAndUpdate(entry.getKey(),entry.getValue());blocks.clear();
        for(UUID id:createdEntities){var entity=p.serverLevel().getEntity(id);if(entity!=null)entity.discard();}createdEntities.clear();
        p.server.overworld().getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS).set(originalDrops,p.server);
        if(oldState!=null){p.setData(ModAttachments.PLAYER_STATE,oldState);p.syncData(ModAttachments.PLAYER_STATE);p.setGameMode(oldMode);p.connection.teleport(oldPosition.x,oldPosition.y,oldPosition.z,0,0);}
        // Keep final OFF preference as the explicit save/rejoin persistence probe.
    }
    private static void write(MinecraftServer server,ServerPlayer p){
        if(!initialized)return;
        try{var report=new JsonObject();report.addProperty("schema",1);report.addProperty("stage",stage);report.addProperty("complete",completed);report.addProperty("passed",completed&&!failed&&impacts.size()==2&&settings.size()==3);report.addProperty("dedicated",server.isDedicatedServer());report.addProperty("port",server.getPort());report.addProperty("tick",server.overworld().getGameTime());report.addProperty("owner",owner==null?"":owner.toString());report.addProperty("defaultOn",defaultOn);report.addProperty("observedOtherRealPlayers",otherPreferences.size());report.addProperty("ownerScopeEvidence",otherPreferences.isEmpty()?"only one real client; packet has no selectable owner, other-player runtime assertion not exercised":"unchanged preferences checked for other connected UUIDs");report.addProperty("case",caseIndex);report.addProperty("session",session);if(p!=null){report.addProperty("serverSetting",p.getData(ModAttachments.SKILL_SETTINGS).bulletdiveBlockBreaking());report.addProperty("gliding",p.getData(ModAttachments.MOVEMENT_STATE).mode()==MovementState.Mode.GLIDE);report.addProperty("targetId",victim==null?-1:victim.getId());}
            report.add("settings",settings);report.add("serverSamples",samples);report.add("impacts",impacts);report.add("acceptedDamage",acceptedDamage);if(error!=null)report.addProperty("error",error);
            var tmp=OUTPUT.resolve("sync-server.pending.json");Files.writeString(tmp,new GsonBuilder().setPrettyPrinting().create().toJson(report));Files.move(tmp,OUTPUT.resolve("sync-server-report.json"),StandardCopyOption.REPLACE_EXISTING);
        }catch(Exception problem){BlacklightNeoMod.LOGGER.error("Cannot write dedicated sync report",problem);}
    }
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
    private static String pendingCorrection(ServerPlayer p){
        try{var field=net.minecraft.server.network.ServerGamePacketListenerImpl.class.getDeclaredField("awaitingPositionFromClient");field.setAccessible(true);return String.valueOf(field.get(p.connection));}
        catch(ReflectiveOperationException failure){return failure.toString();}
    }
}
