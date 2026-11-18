package com.codex.blacklight.gametest;

import com.codex.blacklight.entity.*;
import com.codex.blacklight.military.*;
import com.codex.blacklight.registry.ModEntities;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class MilitaryCoverGameTest {
    static final class Fixture implements AutoCloseable {
        final ServerLevel level;final int x;final List<Entity> entities=new ArrayList<>();final Map<Long,Boolean> forced=new HashMap<>();boolean closed;
        Fixture(GameTestHelper h,int x){this.level=h.getLevel();this.x=x;
            for(int cx=(x-32)>>4;cx<=(x+32)>>4;cx++)for(int cz=-2;cz<=2;cz++){long key=net.minecraft.world.level.ChunkPos.asLong(cx,cz);forced.put(key,level.getForcedChunks().contains(key));level.setChunkForced(cx,cz,true);}
            for(int dx=-24;dx<=24;dx++)for(int z=-24;z<=24;z++){
                level.setBlock(new BlockPos(x+dx,80,z),Blocks.SMOOTH_STONE.defaultBlockState(),2);
                for(int y=81;y<=90;y++){var pos=new BlockPos(x+dx,y,z);if(!level.isEmptyBlock(pos))level.setBlock(pos,Blocks.AIR.defaultBlockState(),2);}
            }
            h.runAfterDelay(20,()->{for(var old:level.getEntitiesOfClass(Entity.class,new AABB(x-28,75,-28,x+28,95,28),e->!(e instanceof net.minecraft.world.entity.player.Player)&&!entities.contains(e)))old.discard();});
        }
        void wall(int dx,int from,int to){for(int z=from;z<=to;z++)for(int y=81;y<=83;y++)level.setBlock(new BlockPos(x+dx,y,z),Blocks.STONE_BRICKS.defaultBlockState(),2);}
        void removeWall(int dx,int from,int to){for(int z=from;z<=to;z++)for(int y=81;y<=83;y++)level.setBlock(new BlockPos(x+dx,y,z),Blocks.AIR.defaultBlockState(),2);}
        MilitarySoldier soldier(double dx,double z){var s=ModEntities.BLACKWATCH_SOLDIER.get().create(level);s.setPos(x+dx,81,z);s.setPersistenceRequired();s.setAutomaticUnitJoin(false);s.setNoAi(true);s.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);s.initializeNativeLoadout("cover-qa:"+entities.size());level.addFreshEntity(s);entities.add(s);s.adoptFieldPost();return s;}
        net.minecraft.world.entity.Mob enemy(double dx,double z){var e=ModEntities.COMMON_INFECTED.get().create(level);e.setPos(x+dx,81,z);e.setNoAi(true);e.setPersistenceRequired();e.getAttribute(Attributes.MAX_HEALTH).setBaseValue(2000);e.setHealth(2000);level.addFreshEntity(e);entities.add(e);return e;}
        void suppress(MilitarySoldier soldier,net.minecraft.world.entity.LivingEntity target){soldier.setNoAi(false);soldier.setTarget(target);if(!soldier.hurt(level.damageSources().mobAttack(target),.1F))throw new IllegalStateException("real hit rejected");}
        public void close(){closed=true;entities.forEach(Entity::discard);forced.forEach((key,value)->{var c=new net.minecraft.world.level.ChunkPos(key);level.setChunkForced(c.x,c.z,value);});}
    }
    @GameTest(template="blackwatch_arena",batch="coverActual",timeoutTicks=130)
    public static void realHitMakesSoldierWalkBehindNearbyCover(GameTestHelper h){
        var f=new Fixture(h,281000);f.wall(2,2,5);var soldier=f.soldier(.5,.5);var enemy=f.enemy(18.5,.5);boolean[] sheltered={false};int[] ammunition={0};Vec3[] previous={null};
        h.runAfterDelay(40,()->{h.assertTrue(soldier.hasLineOfSight(enemy),"initial firing line is exposed");ammunition[0]=soldier.magazineAmmo();f.suppress(soldier,enemy);});
        h.onEachTick(()->{if(f.closed||h.getTick()<41||h.getTick()>=80)return;
            if(previous[0]!=null)h.assertTrue(soldier.position().distanceToSqr(previous[0])<1,"cover movement uses actual walking");previous[0]=soldier.position();
            h.assertTrue(soldier.magazineAmmo()==ammunition[0],"suppression releases fire permit and holds ammunition");
            if(soldier.getZ()>2&&!soldier.hasLineOfSight(enemy))sheltered[0]=true;
        });
        h.runAfterDelay(90,()->{try(f){h.assertTrue(sheltered[0],"real soldier must reach shielding cover after a real hit: pos="+soldier.position()+", navigation="+soldier.getNavigation().isDone());var metrics=MilitaryFormationSystem.metrics(f.level);h.assertTrue(metrics.peakPlans()<=8&&metrics.shortestInterval()>=10,"cover uses shared navigation work limits: "+metrics);}h.succeed();});
    }
    @GameTest(template="blackwatch_arena",batch="coverSquad",timeoutTicks=170)
    public static void fourActualSoldiersReserveDistinctSheltersWithoutOverlapping(GameTestHelper h){
        var f=new Fixture(h,282000);f.wall(2,2,8);f.wall(2,-9,-5);var enemy=f.enemy(18.5,.5);
        var squad=List.of(f.soldier(.5,.5),f.soldier(-2.5,.5),f.soldier(.5,-2.5),f.soldier(-2.5,-2.5));
        for(int time:new int[]{40,65,90})h.runAfterDelay(time,()->squad.forEach(s->f.suppress(s,enemy)));
        h.runAfterDelay(120,()->{try(f){
            var points=new HashSet<BlockPos>();for(var soldier:squad){h.assertTrue(soldier.cover().arrived()&&!soldier.hasLineOfSight(enemy),"every soldier physically reaches shielding cover: "+soldier.position()+" -> "+soldier.cover().destination());h.assertTrue(points.add(soldier.cover().destination()),"cover reservation is not shared by two members");}
            for(int i=0;i<4;i++)for(int j=i+1;j<4;j++)h.assertTrue(!squad.get(i).getBoundingBox().intersects(squad.get(j).getBoundingBox()),"actual bodies do not overlap at reserved destinations");
            var m=MilitaryFormationSystem.metrics(f.level);h.assertTrue(m.peakPlans()<=8&&m.shortestInterval()>=10,"squad shelter routes stay in the global queue: "+m);
        }h.succeed();});
    }
    @GameTest(template="blackwatch_arena",batch="coverDestroyed",timeoutTicks=170)
    public static void destroyingSelectedCoverMakesSoldierChooseAnotherShield(GameTestHelper h){
        var f=new Fixture(h,283000);f.wall(2,2,5);f.wall(2,-5,-2);var soldier=f.soldier(.5,.5);var enemy=f.enemy(18.5,.5);int[] firstSide={0};
        for(int time:new int[]{40,65,90,115})h.runAfterDelay(time,()->f.suppress(soldier,enemy));
        h.runAfterDelay(68,()->{var point=soldier.cover().destination();h.assertTrue(point!=null&&soldier.cover().arrived(),"first shelter reached before destruction");firstSide[0]=point.getZ()>0?1:-1;if(firstSide[0]>0)f.removeWall(2,2,5);else f.removeWall(2,-5,-2);});
        h.runAfterDelay(135,()->{try(f){h.assertTrue(soldier.cover().arrived()&&soldier.cover().destination().getZ()*firstSide[0]<0&&soldier.cover().replans()>0&&!soldier.hasLineOfSight(enemy),"destroyed shelter is released and opposite cover is reached: "+soldier.position()+" -> "+soldier.cover().destination());}h.succeed();});
    }
    @GameTest(template="blackwatch_arena",batch="coverUnreachable",timeoutTicks=180)
    public static void sealedClosestShelterDoesNotHideReachableAlternative(GameTestHelper h){
        var f=new Fixture(h,284000);f.wall(2,2,5);
        for(int[] p:new int[][]{{-1,-3},{1,-3},{0,-4},{0,-2}})for(int y=81;y<=84;y++)f.level.setBlock(new BlockPos(f.x+p[0],y,p[1]),Blocks.STONE_BRICKS.defaultBlockState(),2);
        var soldier=f.soldier(.5,.5);var enemy=f.enemy(18.5,.5);
        for(int time:new int[]{40,65,90,115})h.runAfterDelay(time,()->f.suppress(soldier,enemy));
        h.runAfterDelay(140,()->{try(f){h.assertTrue(soldier.cover().replans()>0,"unreachable nearest cell gets a real failed path result");h.assertTrue(soldier.cover().arrived()&&soldier.cover().destination().getZ()>0&&!soldier.hasLineOfSight(enemy),"reachable alternative is used after the failed queue job: "+soldier.position()+" -> "+soldier.cover().destination());}h.succeed();});
    }
    @GameTest(template="blackwatch_arena",batch="coverKnowledge",timeoutTicks=210)
    public static void hiddenEnemyCannotSteerCoverUsingItsUnobservedNewPosition(GameTestHelper h){
        var f=new Fixture(h,285000);f.wall(2,2,5);var soldier=f.soldier(.5,.5);var enemy=f.enemy(18.5,.5);Vec3[] seen={null};
        for(int time:new int[]{40,65,90,115})h.runAfterDelay(time,()->f.suppress(soldier,enemy));
        h.runAfterDelay(68,()->{h.assertTrue(soldier.cover().destination()!=null&&soldier.cover().lastThreat()!=null,"real observation produces a shelter intent before visibility changes");seen[0]=soldier.cover().lastThreat();f.wall(5,-24,24);enemy.setPos(f.x+18.5,81,18.5);});
        h.runAfterDelay(94,()->{var remembered=soldier.cover().lastThreat();h.assertTrue(remembered!=null&&Math.abs(remembered.x-seen[0].x)<.01&&Math.abs(remembered.z-seen[0].z)<.01,"occluded movement does not update the threat snapshot: "+remembered);});
        h.runAfterDelay(185,()->{try(f){h.assertTrue(soldier.cover().destination()==null&&soldier.cover().lastThreat()==null,"expired contact releases stale shelter intent");}h.succeed();});
    }
    @GameTest(template="blackwatch_arena",batch="coverCancellation",timeoutTicks=130)
    public static void disarmAndReloadCancelTransientShelterAndQueuedPath(GameTestHelper h){
        var f=new Fixture(h,286000);f.wall(2,2,5);var soldier=f.soldier(.5,.5);var enemy=f.enemy(18.5,.5);
        h.runAfterDelay(40,()->f.suppress(soldier,enemy));
        h.runAfterDelay(48,()->{h.assertTrue(soldier.cover().destination()!=null,"real shelter job is active");soldier.setEquipped(false);h.assertTrue(soldier.cover().destination()==null&&soldier.getNavigation().isDone()&&MilitaryFormationSystem.lastPlan(soldier).isEmpty(),"disarm immediately releases destination and navigation job");});
        h.runAfterDelay(70,()->{soldier.setEquipped(true);f.suppress(soldier,enemy);});
        h.runAfterDelay(78,()->{try(f){h.assertTrue(soldier.cover().destination()!=null,"re-equipped soldier makes a new shelter decision");var tag=new net.minecraft.nbt.CompoundTag();soldier.saveWithoutId(tag);int ammunition=soldier.magazineAmmo();soldier.load(tag);h.assertTrue(soldier.cover().destination()==null&&MilitaryFormationSystem.lastPlan(soldier).isEmpty()&&soldier.magazineAmmo()==ammunition,"load retains ammunition but never restores cached shelter geometry or jobs");}h.succeed();});
    }
    @GameTest(template="blackwatch_arena",batch="coverUnknown",timeoutTicks=90)
    public static void exactTacticalDestinationCannotCreateAnUnknownChunk(GameTestHelper h){
        var f=new Fixture(h,287000);var soldier=f.soldier(.5,.5);
        h.runAfterDelay(40,()->{try(f){soldier.setNoAi(false);var destination=new BlockPos(f.x+4000,81,4000);int cx=destination.getX()>>4,cz=destination.getZ()>>4;int forced=f.level.getForcedChunks().size();
            h.assertTrue(f.level.getChunkSource().getChunkNow(cx,cz)==null,"distant tactical destination begins unloaded");MilitaryFormationSystem.navigateExact(soldier,destination,1.15);
            h.assertTrue(f.level.getChunkSource().getChunkNow(cx,cz)==null&&f.level.getForcedChunks().size()==forced,"exact tactical planning never loads or forces the missing destination");h.assertTrue(MilitaryFormationSystem.lastPlan(soldier).filter(p->!p.reachable()).isPresent(),"unavailable destination reports actual failure to its caller");
        }h.succeed();});
    }
    @GameTest(template="blackwatch_arena",batch="coverReturnFire",timeoutTicks=180)
    public static void suppressionExpiryAndOpenSightRestoreActualGunfire(GameTestHelper h){
        var f=new Fixture(h,288000);f.wall(2,2,5);var soldier=f.soldier(.5,.5);var enemy=f.enemy(18.5,.5);int[] ammunition={0};float[] health={0};
        h.runAfterDelay(40,()->{ammunition[0]=soldier.magazineAmmo()+soldier.reserveAmmo();health[0]=enemy.getHealth();f.suppress(soldier,enemy);});
        h.runAfterDelay(75,()->{h.assertTrue(!soldier.hasLineOfSight(enemy)&&soldier.getZ()>2,"real cover blocks the firing line while suppressed");h.assertTrue(enemy.getHealth()==health[0],"no hidden shots occur during suppression");});
        h.runAfterDelay(80,()->f.removeWall(2,2,5));
        h.runAfterDelay(150,()->{try(f){h.assertTrue(soldier.cover().destination()==null,"expired suppression relinquishes shelter control");h.assertTrue(enemy.getHealth()<health[0]&&soldier.magazineAmmo()+soldier.reserveAmmo()<ammunition[0],"actual aimed gunfire resumes and spends issued ammunition");}h.succeed();});
    }
    @GameTest(template="blackwatch_arena",batch="coverAirHome",timeoutTicks=1100)
    public static void deliveredSoldiersReplaceAirborneHomeAnchorWithTheirRealLandingPost(GameTestHelper h){
        var f=new Fixture(h,289000);var craft=ModEntities.BLACKWATCH_HELICOPTER.get().create(f.level);craft.setPos(f.x-16,97,0);f.level.addFreshEntity(craft);f.entities.add(craft);var crew=new ArrayList<MilitarySoldier>();
        for(int i=0;i<6;i++){var s=ModEntities.BLACKWATCH_SOLDIER.get().create(f.level);s.setPos(craft.position());s.setAutomaticUnitJoin(false);s.setPersistenceRequired();s.setNoAi(i>=2);s.getPersistentData().putBoolean("HelicopterTroop",i>=2);s.initializeNativeLoadout("cover-air-home:"+i);f.level.addFreshEntity(s);f.entities.add(s);s.startRiding(craft,true);crew.add(s);}
        h.runAfterDelay(40,()->{h.assertTrue(crew.get(2).getPersistentData().contains(com.codex.blacklight.city.MilitaryHomeSystem.FIELD_ANCHOR_TAG),"delayed home service has captured the airborne spawning anchor");h.assertTrue(craft.deployTo(new Vec3(f.x+8,81,0)),"actual supported delivery");});
        h.succeedWhen(()->{h.assertTrue(crew.subList(2,6).stream().noneMatch(Entity::isPassenger),"four original troops unloaded");
            for(var s:crew.subList(2,6)){var tag=s.getPersistentData();var anchor=BlockPos.of(tag.getLong(com.codex.blacklight.city.MilitaryHomeSystem.FIELD_ANCHOR_TAG));h.assertTrue(anchor.equals(s.blockPosition())&&"field".equals(tag.getString(com.codex.blacklight.city.MilitaryHomeSystem.MODE_TAG)),"real delivery replaces both home systems' airborne origin: "+anchor+" / "+s.position());h.assertTrue(com.codex.blacklight.city.MilitaryHomeSystem.territory(s,()->Vec3.atBottomCenterOf(s.home())).distanceToSqr(s.position())<3,"defensive territory is the real ground landing post");}
            f.close();
        });
    }
}
