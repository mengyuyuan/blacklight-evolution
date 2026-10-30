package com.codex.blacklight.gametest;

import com.codex.blacklight.entity.MilitarySoldier;
import com.codex.blacklight.military.*;
import com.codex.blacklight.registry.ModEntities;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

/** Opt-in real-entity stress. Accelerated GameTest timings are measurements, not a20TPS promise. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class MilitaryStressGameTest {
    @GameTest(template="military_stress_arena",batch="militaryStress",timeoutTicks=1500)
    public static void twoHundredSixteenRealSoldiersWalkInABattalion(GameTestHelper h){
        var from=h.absolutePos(BlockPos.ZERO);var to=h.absolutePos(new BlockPos(127,0,127));
        for(int x=(from.getX()>>4)-1;x<=(to.getX()>>4)+1;x++)for(int z=(from.getZ()>>4)-1;z<=(to.getZ()>>4)+1;z++)h.getLevel().setChunkForced(x,z,true);
        for(int x=0;x<128;x++)for(int z=0;z<128;z++)h.setBlock(x,1,z,Blocks.STONE);
        var troops=new ArrayList<MilitarySoldier>();var origins=new HashMap<UUID,Vec3>();long[] start={0};
        for(int i=0;i<216;i++){var s=ModEntities.MILITARY_SOLDIER.get().create(h.getLevel());s.setPersistenceRequired();s.setPos(h.absoluteVec(new Vec3(30.5+i%15*2,2,30.5+i/15*2)));s.setRank(i==0?MilitaryRank.MAJOR:MilitaryRank.PRIVATE);h.getLevel().addFreshEntity(s);troops.add(s);origins.put(s.getUUID(),s.position());}
        var destination=h.absoluteVec(new Vec3(96.5,2,90.5));
        h.runAtTickTime(40,()->{
            var p=h.absolutePos(new BlockPos(0,2,0));String selector="@e[type=blacklight:military_soldier,x="+p.getX()+",y="+p.getY()+",z="+p.getZ()+",dx=127,dy=6,dz=127]";
            var source=h.getLevel().getServer().createCommandSourceStack().withLevel(h.getLevel()).withPosition(troops.get(0).position()).withPermission(2).withSuppressedOutput();
            try{h.assertTrue(h.getLevel().getServer().getCommands().getDispatcher().execute("blacklight military organize military battalion "+selector,source)==216,"organize actual216 loaded troops");}catch(Exception e){throw new AssertionError(e);}
            var org=MilitaryOrganizationSystem.organization(h.getLevel());var root=org.rootOf(troops.get(0).unitId()).orElseThrow();
            h.assertTrue(org.allMembers(root).size()==216&&org.unit(root).orElseThrow().children().size()==3,"actual battalion contains three companies");
            org.order(root,new MilitaryOrder(MilitaryOrder.Mode.MOVE,new MilitaryOrder.Point(destination.x,destination.y,destination.z)));start[0]=System.nanoTime();
        });
        h.runAtTickTime(1350,()->{
            var stats=MilitaryFormationSystem.metrics(h.getLevel());long moved=troops.stream().filter(s->s.position().distanceToSqr(origins.get(s.getUUID()))>8*8).count();
            double millis=(System.nanoTime()-start[0])/1_000_000.;
            com.codex.blacklight.BlacklightNeoMod.LOGGER.info("MILITARY_STRESS entities=216 ticks=1310 elapsed_ms={} moved_over8={} metrics={}",millis,moved,stats);
            h.assertTrue(troops.get(0).position().distanceToSqr(destination)<36,"battalion commander reaches the actual destination");
            h.assertTrue(moved>=162,"at least three quarters of actual battalion troops walk over8blocks: "+moved);
            h.assertTrue(stats.peakPlans()<=8&&stats.shortestInterval()>=10&&stats.plannedMembers()>=216,"stress obeys real path budget and services each entity: "+stats);h.succeed();
        });
    }
}
