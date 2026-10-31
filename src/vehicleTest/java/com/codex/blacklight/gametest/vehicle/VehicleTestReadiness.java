package com.codex.blacklight.gametest.vehicle;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;

/** Bounded readiness for the fixture's real entity sections; no fixed-delay visibility assumption. */
final class VehicleTestReadiness {
    private static final int MAX_ATTEMPTS=200;
    private VehicleTestReadiness(){}
    static void run(GameTestHelper helper,MilitaryVehicleGameTest.Fixture fixture,Runnable test){
        await(helper,fixture,()->{try(fixture){test.run();}helper.succeed();});
    }
    static void await(GameTestHelper helper,MilitaryVehicleGameTest.Fixture fixture,Runnable test){
        helper.runAfterDelay(1,()->attempt(helper,fixture,test,MAX_ATTEMPTS));
    }
    private static void attempt(GameTestHelper helper,MilitaryVehicleGameTest.Fixture fixture,Runnable test,int remaining){
        // GameTestServer skips normal tick waiting; drain real chunk completion tasks before observing visibility.
        for(int i=0;i<256&&fixture.level.getChunkSource().pollTask();i++){}
        String pending=null;
        for(int x=(fixture.x-10)>>4;x<=(fixture.x+10)>>4&&pending==null;x++)for(int z=-1;z<=1;z++){
            if(!fixture.level.isPositionEntityTicking(new BlockPos(x*16+8,81,z*16+8))){pending=x+","+z+" forced="+fixture.level.getForcedChunks().contains(net.minecraft.world.level.ChunkPos.asLong(x,z))+" loaded="+(fixture.level.getChunkSource().getChunkNow(x,z)!=null)+" naturalTicking="+fixture.level.isNaturalSpawningAllowed(new BlockPos(x*16+8,81,z*16+8))+" spawnTicking="+fixture.level.isPositionEntityTicking(new BlockPos(fixture.x,81,0));break;}
        }
        if(pending==null){test.run();return;}
        if(remaining<=0){
            try(fixture){helper.assertTrue(false,"fixture entity sections did not become ticking within "+MAX_ATTEMPTS+" bounded checks; first pending chunk="+pending+" fixtureX="+fixture.x);}
            return;
        }
        helper.runAfterDelay(1,()->attempt(helper,fixture,test,remaining-1));
    }
}
