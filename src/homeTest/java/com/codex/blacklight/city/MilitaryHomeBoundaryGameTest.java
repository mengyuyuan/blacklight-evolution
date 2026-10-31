package com.codex.blacklight.city;

import com.codex.blacklight.entity.*;
import com.codex.blacklight.military.*;
import com.codex.blacklight.registry.ModEntities;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;

/** Boundary contracts use actual entities, real cores and ordinary movement; no arrival teleport. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class MilitaryHomeBoundaryGameTest {
    @GameTest(template="blackwatch_arena",batch="homeTransfer5",timeoutTicks=2600)
    public static void militaryGuardWalksToAnotherBaseAndFiresBeyondItsOldTerritory(GameTestHelper h){relocation(h,60000,false);}
    @GameTest(template="blackwatch_arena",batch="homeTransfer5",timeoutTicks=2600)
    public static void blackwatchGuardWalksToAnotherBaseAndFiresBeyondItsOldTerritory(GameTestHelper h){relocation(h,62000,true);}
    private static void relocation(GameTestHelper h,int x,boolean blackwatch){
        var a=new MilitaryHomeGameTest.Fixture(h,x,blackwatch,true);var b=new MilitaryHomeGameTest.Fixture(h,x+160,blackwatch,true);
        a.forceRect(x+16,48,x+215,108);
        for(int xx=x+16;xx<=x+215;xx++)for(int z=48;z<=108;z++)
            if(!inside(a.site,xx,z)&&!inside(b.site,xx,z))a.level.setBlock(new BlockPos(xx,80,z),Blocks.STONE.defaultBlockState(),2);
        final MilitarySoldier[] guard={null};final UUID[] id={null},unit={null};final int[] ammo={0},magazine={0};
        final MilitaryRank[] rank={null};final MilitaryRole[] role={null};final net.minecraft.world.item.Item[] gun={null};final LivingEntity[] victim={null};final float[] health={0};
        var rally=new Vec3(x+192.5,81,b.site.templateDepth()-8+.5);
        h.startSequence().thenWaitUntil(()->h.assertTrue(a.ticking()&&b.ticking(),"both real base footprints must tick before relocation"))
            .thenExecute(()->{
                CityMilitaryService.maintain(a.level,List.of(a.observer()));h.assertTrue(a.base().slots[0]!=null&&a.base().slots[1]!=null,"source really issues native soldiers");
                guard[0]=(MilitarySoldier)a.level.getEntity(a.base().slots[0]);id[0]=guard[0].getUUID();unit[0]=guard[0].unitId();guard[0].initializeWeapon();
                ((MilitarySoldier)a.level.getEntity(a.base().slots[1])).setNoAi(true);
                ammo[0]=guard[0].reserveAmmo();magazine[0]=guard[0].magazineAmmo();rank[0]=guard[0].rank();role[0]=guard[0].commandRole();gun[0]=guard[0].getMainHandItem().getItem();
            }).thenIdle(45).thenExecute(a::destroyCores)
            .thenWaitUntil(()->h.assertTrue(b.id.equals(guard[0].getPersistentData().getString(MilitaryHomeSystem.HOME_BASE_TAG)),"all six destroyed command terminals eventually move current home to the other same-faction base"))
            .thenExecute(()->{
                var soldier=guard[0];h.assertTrue(id[0].equals(soldier.getUUID())&&unit[0].equals(soldier.unitId())&&rank[0]==soldier.rank()&&role[0]==soldier.commandRole()&&gun[0]==soldier.getMainHandItem().getItem(),"relocation preserves native identity, role, weapon and organization");
                h.assertTrue(ammo[0]==soldier.reserveAmmo()&&magazine[0]==soldier.magazineAmmo(),"changing home itself never supplies rounds");
                h.assertTrue(a.id.equals(soldier.getPersistentData().getString(CityMilitaryService.BASE_TAG))&&id[0].equals(a.base().slots[0])&&b.base().slots[0]==null,"current home cannot rewrite provenance or consume destination issuance slots");
            }).thenWaitUntil(()->h.assertTrue(guard[0].position().distanceToSqr(rally)<12*12,"the original soldier must physically traverse the connecting road and enter the destination gate; actual="+guard[0].position()))
            .thenExecute(()->{
                var soldier=guard[0];h.assertTrue(soldier.position().distanceToSqr(a.observer())>48*48,"the real arrival is outside the old home territory");
                var target=ModEntities.INFECTED_HOST.get().create(a.level);target.inherit(EntityType.COW.create(a.level));target.setNoAi(true);target.setPersistenceRequired();
                target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);target.setHealth(target.getMaxHealth());
                target.setPos(rally.add(0,0,8));h.assertTrue(a.level.addFreshEntity(target),"actual infected target enters the new defense area");a.entities.add(target);victim[0]=target;health[0]=target.getHealth();
                h.assertTrue(BlackwatchSquadSystem.withinTerritory(soldier,target.position()),"current home keeps infected targets legal beyond the obsolete unit origin");
            }).thenWaitUntil(()->h.assertTrue(victim[0].getHealth()<health[0],"relocated soldier must acquire and really damage the infected target with its normal weapon"))
            .thenExecute(()->{
                guard[0].discard();h.assertTrue((a.base().losses&1)!=0&&b.base().losses==0,"a transferred native soldier's permanent loss belongs only to its source base");
                a.close();b.close();
            }).thenSucceed();
    }
    private static boolean inside(CityMilitaryPlan.Site site,int x,int z){return x>=site.x()&&x<site.x()+site.width()&&z>=site.z()&&z<site.z()+site.depth();}

    @GameTest(template="blackwatch_arena",batch="homeFair5",timeoutTicks=800)
    public static void twoHundredSixteenSearchersDoNotStarveTheLastNearbyPatrol(GameTestHelper h){
        var f=new MilitaryHomeGameTest.Fixture(h,64000,false,true);var far=new BlockPos(64632,81,100);var near=new BlockPos(64082,81,70);f.fieldFloor(far,10);f.fieldFloor(near,12);
        var eligible=new ArrayList<MilitarySoldier>();
        h.startSequence().thenWaitUntil(()->h.assertTrue(f.ticking()&&f.level.isPositionEntityTicking(far),"both the 208 no-home soldiers and final eight candidates must actually tick"))
            .thenExecute(()->{
                for(int index=0;index<216;index++){
                    var soldier=ModEntities.MILITARY_SOLDIER.get().create(f.level);soldier.setUUID(new UUID(0,index+1));soldier.setNoAi(true);soldier.setNoGravity(true);soldier.setInvulnerable(true);soldier.setAutomaticUnitJoin(false);
                    var at=index<208?far:near;soldier.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);h.assertTrue(f.level.addFreshEntity(soldier),"actual bounded-search fixture joins level");f.entities.add(soldier);if(index>=208)eligible.add(soldier);
                }
            }).thenWaitUntil(()->{
                h.assertTrue(f.entities.size()==216&&f.entities.stream().allMatch(entity->entity.isAlive()&&!entity.isRemoved()&&f.level.getEntity(entity.getUUID())==entity),"all 216 actual searchers must remain alive and registered for the entire fairness check");
                h.assertTrue(eligible.stream().allMatch(soldier->f.id.equals(soldier.getPersistentData().getString(MilitaryHomeSystem.HOME_BASE_TAG))),"first 208 repeatedly unsuccessful searches must not permanently starve the final eight soldiers with a real nearby base");
            })
            .thenExecute(()->{
                h.assertTrue(f.entities.size()==216&&f.entities.stream().allMatch(entity->entity.isAlive()&&!entity.isRemoved()&&f.level.getEntity(entity.getUUID())==entity&&((MilitarySoldier)entity).isPersistenceRequired()),"all independent soldiers remain persistent under the bounded search budget");
                h.assertTrue(Arrays.stream(f.base().slots).allMatch(Objects::isNull),"216 external assignments never consume native finite slots");f.close();
            }).thenSucceed();
    }

    @GameTest(template="blackwatch_arena",batch="homeUnload5",timeoutTicks=1600)
    public static void unloadedCoreResetsContinuousMissingObservationAndPreservesHome(GameTestHelper h){
        var f=new MilitaryHomeGameTest.Fixture(h,66000,false,true);var field=new BlockPos(66260,81,90);f.fieldFloor(field,10);
        h.startSequence().thenWaitUntil(()->h.assertTrue(f.ticking()&&f.level.isPositionEntityTicking(field),"core and remote home patrol must tick initially"))
            .thenExecute(()->{f.primary=f.soldier(false,field);f.primary.setNoAi(true);f.primary.setNoGravity(true);})
            .thenIdle(45).thenExecute(()->{
                h.assertTrue(f.id.equals(f.primary.getPersistentData().getString(MilitaryHomeSystem.HOME_BASE_TAG)),"the remote patrol is actually assigned before its core unloads");
                f.destroyCores();CityMilitaryService.refreshCores(f.level);
                for(var key:f.forced.keySet()){var chunk=new net.minecraft.world.level.ChunkPos(key);if(chunk.x<((f.site.x()+128)>>4))f.level.setChunkForced(chunk.x,chunk.z,false);}
            }).thenWaitUntil(()->h.assertTrue(f.cores().stream().allMatch(at->f.level.getChunkSource().getChunkNow(at.getX()>>4,at.getZ()>>4)==null),"the six core positions must really unload, rather than merely removing a force flag"))
            .thenIdle(225).thenExecute(()->{
                h.assertTrue(!f.base().health.inactive()&&f.id.equals(f.primary.getPersistentData().getString(MilitaryHomeSystem.HOME_BASE_TAG)),"unknown chunks cannot complete destruction observation or erase current home");
                h.assertTrue(f.cores().stream().allMatch(at->f.level.getChunkSource().getChunkNow(at.getX()>>4,at.getZ()>>4)==null),"home observation does not force the missing core chunks back in");
                for(var key:f.forced.keySet()){var chunk=new net.minecraft.world.level.ChunkPos(key);f.level.setChunkForced(chunk.x,chunk.z,true);}
            }).thenWaitUntil(()->h.assertTrue(f.ticking(),"original core chunks reload normally"))
            .thenIdle(25).thenExecute(()->h.assertTrue(!f.base().health.inactive(),"reloaded zero-core base starts a fresh full observation window"))
            .thenIdle(225).thenExecute(()->{
                h.assertTrue(f.base().health.inactive(),"only a fresh continuous loaded missing interval confirms failure");
                var saved=CityMilitaryData.load(CityMilitaryData.get(f.level).save(new CompoundTag()));
                h.assertTrue(saved.bases.get(f.id).health.inactive(),"confirmed core loss survives SavedData serialization");f.close();
            }).thenSucceed();
    }

    @GameTest(template="blackwatch_arena",batch="homeDimension5",timeoutTicks=180)
    public static void identicalBaseIdsInDifferentDimensionsNeverReuseTheOriginalPostOrLossLedger(GameTestHelper h){dimension(h,68000,false);}
    @GameTest(template="blackwatch_arena",batch="homeDimension5",timeoutTicks=180)
    public static void anOldCrossDimensionSoldierFindsItsUniqueRealIssuanceSlotBeforeRecordingALoss(GameTestHelper h){dimension(h,70000,true);}
    private static void dimension(GameTestHelper h,int x,boolean legacy){
        var f=new MilitaryHomeGameTest.Fixture(h,x,false,true);var other=f.level.getServer().getLevel(CityTravel.CITY);var otherId=CityMilitaryService.register(other,f.site);var otherBase=CityMilitaryData.get(other).bases.get(otherId);
        MilitaryHomeGameTest.cleanupOnFailure(h,()->CityMilitaryData.get(other).bases.remove(otherId));
        h.startSequence().thenWaitUntil(()->h.assertTrue(f.ticking(),"destination dimension base must really tick"))
            .thenExecute(()->{
                var spawn=new BlockPos(x+32,81,61);f.primary=f.soldier(false,spawn);f.primary.setNoAi(true);otherBase.slots[0]=f.primary.getUUID();
                var tag=f.primary.getPersistentData();tag.putString(CityMilitaryService.BASE_TAG,otherId);if(!legacy)tag.putString(MilitaryHomeSystem.ORIGIN_DIMENSION_TAG,other.dimension().location().toString());tag.putLong(CityMilitaryService.POST_TAG,spawn.asLong());
                tag.putString(MilitaryHomeSystem.HOME_BASE_TAG,otherId);tag.putString(MilitaryHomeSystem.HOME_DIMENSION_TAG,other.dimension().location().toString());
                if(legacy)h.assertTrue(!tag.contains(MilitaryHomeSystem.ORIGIN_DIMENSION_TAG),"actual old-save fixture starts without origin dimension metadata");
            }).thenIdle(45).thenExecute(()->{
                var soldier=f.primary;var tag=soldier.getPersistentData();
                h.assertTrue(f.id.equals(tag.getString(MilitaryHomeSystem.HOME_BASE_TAG))&&f.level.dimension().location().toString().equals(tag.getString(MilitaryHomeSystem.HOME_DIMENSION_TAG)),"arrived soldier binds the actual destination dimension's active base");
                h.assertTrue(CityMilitaryService.post(soldier).isEmpty(),"same coordinate/id cannot revive an origin post from another dimension");
                h.assertTrue(other.dimension().location().toString().equals(tag.getString(MilitaryHomeSystem.ORIGIN_DIMENSION_TAG)),"changing current home cannot rewrite origin dimension");
                soldier.discard();h.assertTrue((otherBase.losses&1)!=0&&f.base().losses==0,"actual permanent removal updates only the dimension containing its original UUID slot");CityMilitaryData.get(other).bases.remove(otherId);f.close();
            }).thenSucceed();
    }
    private MilitaryHomeBoundaryGameTest(){}
}
