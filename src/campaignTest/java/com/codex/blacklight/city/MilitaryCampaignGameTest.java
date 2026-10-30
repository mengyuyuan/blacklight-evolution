package com.codex.blacklight.city;

import com.codex.blacklight.disguise.*;
import com.codex.blacklight.entity.*;
import com.codex.blacklight.firearms.*;
import com.codex.blacklight.infection.*;
import com.codex.blacklight.registry.*;
import com.mojang.authlib.GameProfile;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.*;
import static com.codex.blacklight.city.MilitaryCampaignData.*;

/** Actual headless server entities, AI, firearm rays, saved data and event protections. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class MilitaryCampaignGameTest {
    static final class Fixture implements AutoCloseable {
        final GameTestHelper h;final ServerLevel level;final BlockPos at=new BlockPos(40008,100,40008);
        final List<Entity> born=new ArrayList<>();final List<net.minecraft.world.level.ChunkPos> forced=new ArrayList<>();
        final List<String> passed=new ArrayList<>();final Map<net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<?>,Object> config=new LinkedHashMap<>();
        ServerPlayer player;MilitarySoldier soldier;Set<UUID> first;Operation op;String baseId;BlockPos core;int ammo;
        Consumer<BlockEvent.BreakEvent> protection;
        Fixture(GameTestHelper h){
            this.h=h;level=h.getLevel();
            set(CityConfig.SPAWNS,false);set(MilitaryCampaignConfig.ACTIVE,2);set(MilitaryCampaignConfig.INTERVAL,240000);set(MilitaryCampaignConfig.REINFORCEMENT_INTERVAL,240000);
            set(OutbreakConfig.INTERVAL,240000);set(MilitaryCampaignConfig.FIELD_LIMIT,24);
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(true,level.getServer());
            level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true,level.getServer());
            for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++){
                var p=new net.minecraft.world.level.ChunkPos((at.getX()>>4)+x,(at.getZ()>>4)+z);
                if(!level.getForcedChunks().contains(p.toLong())){forced.add(p);level.setChunkForced(p.x,p.z,true);}level.getChunk(p.x,p.z);
            }
            for(int x=-70;x<=70;x++)for(int z=-70;z<=70;z++)level.setBlock(at.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);
            OutbreakData.get(level.getServer()).start(level.getServer().overworld().getGameTime());
            CityEcologyData.get(level).observe(CityEcology.key(level,at),level.getGameTime(),24000,2,24000,new DistrictThreat.Signal(20,3,2,4,0,0));
        }
        <T> void set(net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<T> v,T value){config.putIfAbsent(v,v.get());v.set(value);}
        void check(boolean value,String name){h.assertTrue(value,name);passed.add(name);System.out.println("CAMPAIGN_PASS "+name);}
        MilitarySoldier soldier(BlockPos pos){var s=ModEntities.BLACKWATCH_SOLDIER.get().create(level);s.moveTo(Vec3.atBottomCenterOf(pos));s.initializeNativeLoadout("campaign-qa:"+s.getUUID());s.setAutomaticUnitJoin(false);level.addFreshEntity(s);born.add(s);return s;}
        ServerPlayer player(BlockPos pos){
            var p=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"campaign-qa"));p.setGameMode(GameType.SURVIVAL);p.moveTo(Vec3.atBottomCenterOf(pos));
            p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(10000);p.setHealth(p.getMaxHealth());
            var state=p.getData(ModAttachments.PLAYER_STATE);state.infect();state.setPrototypeMode(true);p.setData(ModAttachments.PLAYER_STATE,state);level.addNewPlayer(p);born.add(p);return p;
        }
        List<MilitarySoldier> members(){var list=new ArrayList<MilitarySoldier>();for(var o:get(level).operations.values())for(var id:o.members)if(level.getEntity(id) instanceof MilitarySoldier s)list.add(s);return list;}
        void clearField(){for(var s:members())s.discard();get(level).operations.clear();}
        @SuppressWarnings({"unchecked","rawtypes"}) public void close(){
            if(protection!=null)NeoForge.EVENT_BUS.unregister(protection);
            clearField();for(var e:born)if(!e.isRemoved())e.discard();
            config.forEach((v,value)->((net.neoforged.neoforge.common.ModConfigSpec.ConfigValue)v).set(value));
            for(var p:forced)level.setChunkForced(p.x,p.z,false);
        }
    }
    @GameTest(template="blackwatch_arena",timeoutTicks=1600,batch="campaign")
    public static void continuousPatrolDisguisePhysicalObjectivesAndIsolation(GameTestHelper h){
        var f=new Fixture(h);var l=f.level;var d=get(l);
        h.startSequence().thenWaitUntil(()->h.assertTrue(MilitaryCampaignSystem.loaded(l,f.at),"waiting for actual entity-ticking ground"))
        .thenExecute(()->{
            f.check(CityEcology.zone(l,f.at)==DistrictThreat.Zone.RED,"actual local pressure selects red district");
            f.check(FieldReinforcementSystem.reinforce(l,List.of(Vec3.atCenterOf(f.at))),"red district receives actual incoming ground patrol");
            f.first=new HashSet<>();for(var s:f.members()){f.first.add(s.getUUID());f.check(s.hasBattleAmmunition()&&!s.isPersistenceRequired(),"new patrol carries finite ammunition and permits ambient despawn");}
            f.check(f.first.size()==MilitaryCampaignConfig.SQUAD.get(),"configured squad size is exact");
            var op=d.operations.values().iterator().next();
            var restored=load(d.save(new CompoundTag(),l.registryAccess()),l.registryAccess());var copy=restored.operations.get(op.id);
            f.check(copy!=null&&copy.expedition&&copy.origin.equals(op.origin)&&copy.members.equals(op.members),"operation membership and entry point survive NBT round trip");
            f.check(!FieldReinforcementSystem.reinforce(l,List.of(Vec3.atCenterOf(f.at),Vec3.atCenterOf(f.at))),"two observers cannot duplicate a district patrol");
            f.op=op;
        }).thenIdle(60).thenExecute(()->{
            f.check(f.members().stream().anyMatch(s->s.distanceToSqr(Vec3.atBottomCenterOf(f.op.origin))>8*8),"incoming patrol actually walks from entry toward the red district");
            f.members().forEach(Entity::discard);
        }).thenIdle(2).thenExecute(()->{
            MilitaryCampaignSystem.step(l,List.of(Vec3.atCenterOf(f.at)));
            f.check(d.operations.isEmpty(),"destroyed first patrol releases operation budget");
            f.check(FieldReinforcementSystem.reinforce(l,List.of(Vec3.atCenterOf(f.at))),"red district receives another patrol after first loss");
            f.check(f.members().stream().noneMatch(s->f.first.contains(s.getUUID())),"reinforcements never resurrect old personnel UUIDs");
            f.clearField();f.set(MilitaryCampaignConfig.FIELD_LIMIT,0);
            f.check(!FieldReinforcementSystem.reinforce(l,List.of(Vec3.atCenterOf(f.at))),"zero population budget blocks reinforcements");f.set(MilitaryCampaignConfig.FIELD_LIMIT,24);
            l.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,l.getServer());
            f.check(!FieldReinforcementSystem.reinforce(l,List.of(Vec3.atCenterOf(f.at))),"doMobSpawning false blocks reinforcements");
            l.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(true,l.getServer());
            f.soldier=f.soldier(f.at);f.player=f.player(f.at.south(8));f.soldier.setYRot(0);
        }).thenIdle(45).thenExecute(()->{
            System.out.println("CAMPAIGN_TARGET_DIAGNOSTIC soldierTick="+f.soldier.tickCount+" playerTick="+f.player.tickCount+" target="+f.soldier.getTarget()+" valid="+f.soldier.validTarget(f.player)+" tactics="+f.soldier.tacticsAvailable()+" canAttack="+f.soldier.canAttack(f.player)+" infected="+BlacklightInfectionSystem.isInfected(f.player)+" sight="+f.soldier.hasLineOfSight(f.player)+" alive="+f.player.isAlive()+" creative="+f.player.isCreative()+" spectator="+f.player.isSpectator()+" invulnerable="+f.player.getAbilities().invulnerable+" distance="+f.soldier.distanceTo(f.player)+" ticking="+l.isPositionEntityTicking(f.soldier.blockPosition())+" position="+f.soldier.position()+" player="+f.player.position());
            f.check(f.soldier.getTarget()==f.player,"real target AI proactively acquires undisguised infected protagonist");
            DisguiseSystem.captured(f.player,DisguiseIdentity.capture(f.soldier));var state=f.player.getData(ModAttachments.PLAYER_STATE);state.setDisguised(true);f.player.setData(ModAttachments.PLAYER_STATE,state);
            f.soldier.setTarget(null);f.soldier.setLastHurtByMob(null);
            f.check(DisguiseSystem.fooled(f.soldier,f.player),"matching unexposed military disguise fools soldier");
        }).thenIdle(35).thenExecute(()->{
            f.check(f.soldier.getTarget()!=f.player,"real AI does not reacquire a valid matching disguise");
            DisguiseSystem.vehicleHostileAct(f.player,f.soldier);
            f.check(!DisguiseSystem.fooled(f.soldier,f.player),"hostile contact exposes military disguise");
        }).thenIdle(35).thenExecute(()->{
            f.check(f.soldier.getTarget()==f.player,"real AI pursues exposed disguised protagonist");
            f.player.discard();f.soldier.discard();
            f.soldier=f.soldier(f.at);f.core=f.at.south(8).above();l.setBlock(f.core,ModBlocks.BUILDING_CORE.get().defaultBlockState(),3);
            var colony=new BuildingInfectionData.Colony(f.core);BuildingInfectionData.get(l).colonies.add(colony);
            f.op=new Operation(UUID.randomUUID(),"cordon:core-test",Kind.COLONY,f.core,null,-1);f.op.expedition=true;f.op.origin=f.at;f.op.members.add(f.soldier.getUUID());d.base(f.op.base);d.operations.put(f.op.id,f.op);
            f.ammo=f.soldier.magazineAmmo();l.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false,l.getServer());
            f.check(!f.soldier.fireAtCampaignCore(f.core)&&f.soldier.magazineAmmo()==f.ammo,"mobGriefing false prevents military structural firing");
            l.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true,l.getServer());
        }).thenWaitUntil(()->{
            f.soldier.fireAtCampaignCore(f.core);h.assertTrue(f.soldier.magazineAmmo()<f.ammo,"waiting for the soldier's real aim/burst/cooldown cycle");
        }).thenExecute(()->{
            f.check(f.soldier.magazineAmmo()<f.ammo&&l.getBlockState(f.core).is(ModBlocks.BUILDING_CORE.get()),"physical core shots debit actual ammunition without timer deletion");
            l.setBlock(f.at.south(3).above(),Blocks.BEDROCK.defaultBlockState(),3);int ammo=f.soldier.magazineAmmo();
            f.check(!f.soldier.fireAtCampaignCore(f.core)&&f.soldier.magazineAmmo()==ammo,"solid wall prevents core shots and ammunition debit");
            f.soldier.discard();d.operations.clear();
            f.player=f.player(f.at.south(2));
            var site=new CityMilitaryPlan.Site(f.at.getX()-32,f.at.getZ()-32,99,0,true);f.baseId=CityMilitaryService.register(l,site);
            for(var local:LargeBaseBlueprint.get(true).commandCores()){var w=site.world(local[0],local[2]);l.setBlock(new BlockPos(w[0],site.floor()+local[1],w[1]),MilitaryFacilityBlocks.COMMAND_TERMINAL.get().defaultBlockState(),3);}
            CityMilitaryService.refreshCores(l);
            f.check(MilitaryBaseAccess.find(l,f.baseId).orElseThrow().operational(),"real command blocks activate registered Blackwatch base");
            f.player.discard();
            for(int i=0;i<12;i++)CityMilitaryService.maintain(l,List.of(Vec3.atCenterOf(f.at)));
            f.check(MilitaryCampaignSystem.dispatch(l,f.baseId),"actual finite native garrison dispatches against a real infection core");
            var nativeOp=d.operations.values().stream().filter(o->o.base.equals(f.baseId)).findFirst().orElseThrow();
            f.check(nativeOp.members.size()>=2&&nativeOp.members.stream().allMatch(id->l.getEntity(id) instanceof MilitarySoldier s&&s.commandRole()==com.codex.blacklight.military.MilitaryRole.TROOPER),"native dispatch keeps command officers at base");
            var nativeBase=CityMilitaryData.get(l).bases.get(f.baseId);
            for(var id:nativeBase.slots)if(id!=null&&l.getEntity(id) instanceof MilitarySoldier s){f.born.add(s);s.discard();}
            d.operations.clear();d.base(f.baseId).cooldown=2400;
            f.check(FieldReinforcementSystem.reinforce(l,List.of(Vec3.atCenterOf(f.at))),"base-garrison rest does not stop independent continuous red-zone reinforcements");
            f.clearField();f.player=f.player(f.at.south(2));
            var b=CityMilitaryData.get(l).bases.get(f.baseId);var local=LargeBaseBlueprint.get(true).commandCores().getFirst();var w=site.world(local[0],local[2]);f.core=new BlockPos(w[0],site.floor()+local[1],w[1]);
            f.player.moveTo(Vec3.atBottomCenterOf(f.core.south(4).below()));
            f.protection=e->{if(e.getPos().equals(f.core))e.setCanceled(true);};NeoForge.EVENT_BUS.addListener(f.protection);
            for(int i=0;i<8;i++)FirearmBlockDamage.blast(l,f.player,WeaponCatalog.ROCKET,Vec3.atCenterOf(f.core));
            f.check(l.getBlockState(f.core).is(MilitaryFacilityBlocks.COMMAND_TERMINAL.get())&&d.base(f.baseId).actor==null,"canceled real firearm break neither destroys core nor claims player credit");
            NeoForge.EVENT_BUS.unregister(f.protection);f.protection=null;
            for(int i=0;i<8;i++)FirearmBlockDamage.blast(l,f.player,WeaponCatalog.ROCKET,Vec3.atCenterOf(f.core));
            f.check(!l.getBlockState(f.core).is(MilitaryFacilityBlocks.COMMAND_TERMINAL.get())&&f.player.getUUID().equals(d.base(f.baseId).actor),"confirmed firearm destruction attributes actual player only");
            // Restore a real command block for the infiltration path.
            l.setBlock(f.core,MilitaryFacilityBlocks.COMMAND_TERMINAL.get().defaultBlockState(),3);
            var officer=ModEntities.BLACKWATCH_OFFICER.get().create(l);officer.moveTo(Vec3.atBottomCenterOf(f.core.south(2)));officer.getPersistentData().putString(CityMilitaryService.BASE_TAG,f.baseId);officer.setNoAi(true);l.addFreshEntity(officer);f.born.add(officer);
            d.infiltration.put(f.player.getUUID(),new Infiltration(f.baseId,12000));int ep=f.player.getData(ModAttachments.PLAYER_STATE).getEvolutionPoints();
            MilitaryCampaignSystem.consumed(f.player,officer);
            f.check(MilitaryCampaignSystem.disabled(l,f.baseId)&&!MilitaryBaseAccess.find(l,f.baseId).orElseThrow().operational(),"settled commander-consume hook disables command, garage and local reinforcement gate");
            MilitaryCampaignSystem.consumed(f.player,officer);
            f.check(f.player.getData(ModAttachments.PLAYER_STATE).getEvolutionPoints()==ep+MilitaryCampaignConfig.INFILTRATE_REWARD.get(),"repeated consume callback cannot duplicate infiltration reward");
            var other=l.getServer().getLevel(CityTravel.WORLD);
            f.check(other!=null&&get(other)!=d&&!MilitaryCampaignSystem.disabled(other,f.baseId),"Blacklight dimension has independent physical campaign saved data");
            var round=load(d.save(new CompoundTag(),l.registryAccess()),l.registryAccess());
            f.check(round.base(f.baseId).sabotage>0&&round.base(f.baseId).infiltrationRewarded,"sabotage and reward deduplication survive NBT round trip");
            for(var core:LargeBaseBlueprint.get(true).commandCores()){var c=site.world(core[0],core[2]);l.setBlock(new BlockPos(c[0],site.floor()+core[1],c[1]),Blocks.AIR.defaultBlockState(),3);}
            CityMilitaryService.refreshCores(l);
        }).thenIdle(220).thenExecute(()->{
            CityMilitaryService.refreshCores(l);f.check(d.base(f.baseId).destroyed,"confirmed destruction requires actual missing command facilities across observation period");
            f.check(!FieldReinforcementSystem.reinforce(l,List.of(Vec3.atCenterOf(f.at))),"destroyed local base prevents new red-zone reinforcement");
            int chunks=l.getChunkSource().getLoadedChunksCount();var remote=f.at.offset(1000000,0,1000000);
            f.check(!MilitaryCampaignSystem.loaded(l,remote),"remote objective starts genuinely unloaded");
            FieldReinforcementSystem.reinforce(l,List.of(Vec3.atCenterOf(remote)));
            f.check(l.getChunkSource().getLoadedChunksCount()==chunks,"campaign inspection never requests remote chunks");
            System.out.println("CAMPAIGN_ASSERTIONS "+f.passed.size());f.close();h.succeed();
        });
    }
}
