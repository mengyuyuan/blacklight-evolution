package com.codex.blacklight.city;

import com.codex.blacklight.entity.MilitarySoldier;
import com.codex.blacklight.registry.ModEntities;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Real disposable server fixtures. No production test hook or cross-tick config mutation. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class MilitaryGarrisonGameTest {
    @GameTest(template="blackwatch_arena",batch="garrison4",timeoutTicks=300)
    public static void unrelatedPopulationCannotConsumeFirstGarrisonIssue(GameTestHelper h){
        var level=h.getLevel().getServer().getLevel(CityTravel.CITY);
        var fixture=new Fixture(level,new CityMilitaryPlan.Site(22000,0,80,0,false));
        fixture.posts();
        h.startSequence().thenWaitUntil(()->h.assertTrue(fixture.ticking(),"real garrison post chunks must tick"))
                .thenExecute(()->{
                    fixture.ambient(Math.max(80,CityConfig.MOB_LIMIT.get()));
                    h.runAfterDelay(2,()->{
                    try{
                        h.assertTrue(fixture.entities.stream().filter(e->level.getEntity(e.getUUID())==e).count()>=80,"at least eighty real unrelated cows are loaded");
                        h.assertTrue(mobs(level)>=CityConfig.MOB_LIMIT.get(),"ambient fixture must actually reach the ordinary population budget; actual="+mobs(level));
                        CityMilitaryService.maintain(level,List.of(fixture.observer()));
                        h.assertTrue(issued(fixture.base())==2,"unrelated population must leave the first two finite military slots available; actual="+issued(fixture.base()));
                        h.assertTrue(loaded(level,fixture.base())==2,"the two issued UUIDs must resolve to actual loaded guards");
                        h.succeed();
                    }finally{fixture.close();}
                    });
                });
    }

    @GameTest(template="blackwatch_arena",batch="garrison4",timeoutTicks=350)
    public static void negativeYEmptySavedBlackwatchBaseMustersFromActualPlayerTicks(GameTestHelper h){
        var level=h.getLevel();
        var fixture=new Fixture(level,new CityMilitaryPlan.Site(22400,0,-61,0,true));
        // A fresh flat, negative-Y plot exercises the actual administrator placement path.
        for(int x=fixture.site.x();x<fixture.site.x()+fixture.site.width();x++)for(int z=0;z<fixture.site.depth();z++)
            for(int y=-61;y<=-44;y++)level.setBlock(new BlockPos(x,y,z),(y==-61?Blocks.GRASS_BLOCK:Blocks.AIR).defaultBlockState(),2);
        CityMilitaryPlacement.place(level,fixture.site);fixture.force();
        // Retain an actual SavedData round trip representing an existing issued0 base.
        var restored=CityMilitaryData.load(CityMilitaryData.get(level).save(new CompoundTag()));
        CityMilitaryData.get(level).bases.put(fixture.id,restored.bases.get(fixture.id));
        h.startSequence().thenWaitUntil(()->h.assertTrue(fixture.ticking(),"negative-Y base must be entity ticking"))
                .thenExecute(()->{
                    CityMilitaryService.maintain(level,List.of(fixture.observer()));
                    h.assertTrue(issued(fixture.base())==2&&loaded(level,fixture.base())==2,"negative-Y actual placed template must issue two guards without ambient population");
                    for(var uuid:fixture.base().slots)if(uuid!=null&&level.getEntity(uuid)!=null)level.getEntity(uuid).discard();
                    Arrays.fill(fixture.base().slots,null);fixture.base().losses=0;
                    var emptySaved=CityMilitaryData.load(CityMilitaryData.get(level).save(new CompoundTag()));
                    CityMilitaryData.get(level).bases.put(fixture.id,emptySaved.bases.get(fixture.id));
                    fixture.ambient(Math.max(80,CityConfig.MOB_LIMIT.get()));
                    h.runAfterDelay(2,()->{
                    h.assertTrue(mobs(level)>=CityConfig.MOB_LIMIT.get(),"negative-Y ambient population must already be visible before the player enters");
                    var player=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"garrison-ticks"));
                    player.setGameMode(GameType.CREATIVE);player.setPos(fixture.observer());level.addNewPlayer(player);
                    fixture.entities.add(player);
                    h.assertTrue(level.players().contains(player)&&issued(fixture.base())==0,"actual nearby player starts from the persisted empty base");
                    // No maintain/tick call here: the registered real server event handler must do the work.
                    h.runAfterDelay(31,()->{
                        try{
                            long count=issued(fixture.base());
                            h.assertTrue(mobs(level)>=CityConfig.MOB_LIMIT.get(),"negative-Y fixture must actually contain the configured ordinary population budget");
                            h.assertTrue(count>=2&&count<=8,"negative-Y existing base must muster through ordinary player-driven level events; actual="+count);
                            h.assertTrue(loaded(level,fixture.base())==count,"event-driven issued UUIDs resolve to real negative-Y guards");
                            h.assertTrue(Arrays.stream(fixture.base().slots).filter(Objects::nonNull).map(level::getEntity).allMatch(e->e.getY()<0),"actual garrison remains below Y=0");
                            h.succeed();
                        }finally{fixture.close();}
                    });
                    });
                });
    }

    @GameTest(template="blackwatch_arena",batch="garrison4Contracts",timeoutTicks=300)
    public static void finiteMilitaryAndBlackwatchSlotsSurviveLossUnloadAndSerialization(GameTestHelper h){
        var level=h.getLevel();
        var military=new Fixture(level,new CityMilitaryPlan.Site(22800,0,80,0,false));
        var blackwatch=new Fixture(level,new CityMilitaryPlan.Site(23200,0,80,0,true));
        military.posts();blackwatch.posts();
        h.startSequence().thenWaitUntil(()->h.assertTrue(military.ticking()&&blackwatch.ticking(),"both faction fixture posts must tick"))
                .thenExecute(()->{
                    try{
                        for(var fixture:List.of(military,blackwatch)){
                            CityMilitaryService.maintain(level,List.of(fixture.observer()));
                            h.assertTrue(issued(fixture.base())==2,"one maintenance pass remains limited to two actual guards");
                            for(int pass=0;pass<12;pass++)CityMilitaryService.maintain(level,List.of(fixture.observer()));
                            int expected=fixture.site.blackwatch()?20:16;
                            h.assertTrue(issued(fixture.base())==expected&&loaded(level,fixture.base())==expected,"actual faction base must fill exactly its original finite capacity");
                            var uuids=fixture.base().slots.clone();
                            ((MilitarySoldier)level.getEntity(uuids[0])).discard();
                            level.getEntity(uuids[1]).remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
                            h.assertTrue((fixture.base().losses&1)!=0&&(fixture.base().losses&2)==0,"permanent removal records loss; unloading does not");
                            var saved=CityMilitaryData.load(CityMilitaryData.get(level).save(new CompoundTag()));
                            CityMilitaryData.get(level).bases.put(fixture.id,saved.bases.get(fixture.id));
                            for(int pass=0;pass<4;pass++)CityMilitaryService.maintain(level,List.of(fixture.observer()));
                            h.assertTrue(Arrays.equals(uuids,fixture.base().slots)&&level.getEntity(uuids[0])==null&&level.getEntity(uuids[1])==null,"saved dead and unloaded UUIDs remain issued and never receive duplicate replacements");
                            h.assertTrue(loaded(level,fixture.base())==expected-2,"no guards are minted beyond the finite saved roster");
                        }
                        h.succeed();
                    }finally{military.close();blackwatch.close();}
                });
    }

    @GameTest(template="blackwatch_arena",batch="garrison4Budget",timeoutTicks=300)
    public static void loadedBaseGarrisonsRemainBoundedAndCanReleaseTheirBudget(GameTestHelper h){
        var level=h.getLevel().getServer().getLevel(CityTravel.CITY);
        var target=new Fixture(level,new CityMilitaryPlan.Site(23600,0,80,0,false));target.posts();
        var fillers=new ArrayList<Fixture>();
        h.startSequence().thenWaitUntil(()->h.assertTrue(target.ticking(),"budget target must tick"))
                .thenExecute(()->{
                    try{
                        // 200 is the supported configuration ceiling; every entity below owns a real saved slot.
                        for(int group=0;group<10;group++){
                            var fixture=new Fixture(level,new CityMilitaryPlan.Site(24000+group*128,0,80,0,true));fillers.add(fixture);
                            for(int slot=0;slot<20;slot++){
                                var soldier=ModEntities.BLACKWATCH_SOLDIER.get().create(level);
                                soldier.setNoAi(true);soldier.setNoGravity(true);soldier.setPersistenceRequired();soldier.setAutomaticUnitJoin(false);
                                soldier.moveTo(target.site.x()+15+(slot%10)*1.5,85,15+group*2,0,0);
                                soldier.getPersistentData().putString(CityMilitaryService.BASE_TAG,fixture.id);
                                h.assertTrue(level.addFreshEntity(soldier),"real bounded-budget roster soldier enters level");
                                fixture.base().slots[slot]=soldier.getUUID();fixture.entities.add(soldier);
                            }
                        }
                        h.runAfterDelay(2,()->{
                        try{
                        h.assertTrue(mobs(level)>=200,"all two hundred actual saved-slot budget guards must be visible to level population iteration");
                        CityMilitaryService.maintain(level,List.of(target.observer()));
                        h.assertTrue(issued(target.base())==0,"an exhausted loaded-base budget must prevent extra guards");
                        String targetStatus=CityMilitaryService.status(level,target.observer()).stream()
                                .filter(line->line.startsWith("Military ["+target.site.x()+", "+target.site.floor()+", "+target.site.z()+"] ")).findFirst().orElseThrow();
                        h.assertTrue(targetStatus.contains("GARRISON_LIMIT")&&targetStatus.contains("loaded_garrison"),"the exhausted target base status must explain its actual military budget blocker; actual="+targetStatus);
                        for(var fixture:fillers)fixture.close();
                        CityMilitaryService.maintain(level,List.of(target.observer()));
                        h.assertTrue(issued(target.base())==2,"freeing loaded-base capacity allows the same empty base to resume");
                        h.succeed();
                        }finally{for(var fixture:fillers)fixture.close();target.close();}
                        });
                    }catch(RuntimeException failure){for(var fixture:fillers)fixture.close();target.close();throw failure;}
                });
    }

    private static long issued(CityMilitaryData.Base base){return Arrays.stream(base.slots).filter(Objects::nonNull).count();}
    private static long mobs(ServerLevel level){long count=0;for(var entity:level.getAllEntities())if(entity instanceof net.minecraft.world.entity.Mob)count++;return count;}
    private static long loaded(ServerLevel level,CityMilitaryData.Base base){return Arrays.stream(base.slots).filter(Objects::nonNull).map(level::getEntity).filter(e->e instanceof MilitarySoldier&&e.isAlive()).count();}
    private static final class Fixture {
        final ServerLevel level;final CityMilitaryPlan.Site site;final String id;final List<Entity> entities=new ArrayList<>();
        Fixture(ServerLevel level,CityMilitaryPlan.Site site){this.level=level;this.site=site;id=CityMilitaryService.register(level,site);}
        CityMilitaryData.Base base(){return CityMilitaryData.get(level).bases.get(id);}
        Vec3 observer(){return new Vec3(site.x()+site.width()/2.,site.floor()+1,site.z()+site.depth()/2.);}
        void force(){for(int x=(site.x()>>4)-1;x<=((site.x()+site.width()-1)>>4)+1;x++)for(int z=(site.z()>>4)-1;z<=((site.z()+site.depth()-1)>>4)+1;z++)level.setChunkForced(x,z,true);}
        void posts(){
            force();for(var guard:LargeBaseBlueprint.get(site.blackwatch()).guards()){
                int[] p=site.world(guard.x(),guard.z());var at=new BlockPos(p[0],site.floor()+guard.y(),p[1]);
                for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)for(int y=-1;y<=3;y++)level.setBlock(at.offset(dx,y,dz),(y<0?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);
            }
            // A legal minimal base includes the six actual command-core positions from its NBT.
            for(var local:LargeBaseBlueprint.get(site.blackwatch()).commandCores()){int[] point=site.world(local[0],local[2]);level.setBlock(new BlockPos(point[0],site.floor()+local[1],point[1]),com.codex.blacklight.registry.MilitaryFacilityBlocks.COMMAND_TERMINAL.get().defaultBlockState(),2);}
        }
        boolean ticking(){for(var guard:LargeBaseBlueprint.get(site.blackwatch()).guards()){int[] p=site.world(guard.x(),guard.z());if(!level.isPositionEntityTicking(new BlockPos(p[0],site.floor()+guard.y(),p[1])))return false;}return true;}
        void ambient(int count){for(int i=0;i<count;i++){var cow=EntityType.COW.create(level);cow.setNoAi(true);cow.setNoGravity(true);cow.setInvulnerable(true);cow.setPersistenceRequired();cow.moveTo(site.x()+10+(i%10)*1.5,site.floor()+4,site.z()+10+(i/10)*1.5,0,0);if(!level.addFreshEntity(cow))throw new IllegalStateException("ambient fixture cow rejected");entities.add(cow);}}
        void close(){
            var base=base();if(base!=null){for(var uuid:base.slots)if(uuid!=null&&level.getEntity(uuid)!=null)level.getEntity(uuid).discard();CityMilitaryData.get(level).bases.remove(id);}
            for(var entity:entities)if(!entity.isRemoved())entity.discard();entities.clear();
            for(int x=(site.x()>>4)-1;x<=((site.x()+site.width()-1)>>4)+1;x++)for(int z=(site.z()>>4)-1;z<=((site.z()+site.depth()-1)>>4)+1;z++)level.setChunkForced(x,z,false);
        }
    }
    private MilitaryGarrisonGameTest(){}
}
