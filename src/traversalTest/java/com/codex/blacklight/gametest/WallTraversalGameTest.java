package com.codex.blacklight.gametest;

import com.codex.blacklight.movement.WallContacts;
import com.codex.blacklight.movement.WallTraversal;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Runs production wall probes against Minecraft collision shapes in a disposable world. */
@GameTestHolder("blacklight")
@PrefixGameTestTemplate(false)
public final class WallTraversalGameTest {
    private static final WallTraversal.Input RUN=new WallTraversal.Input(1,0,true,false,false);
    private static final WallTraversal.Input STOP=new WallTraversal.Input(0,0,false,false,false);

    @GameTest(template="wall_test",timeoutTicks=100)
    public static void runStopClimbLeapAndMantle(GameTestHelper helper) {
        for(int x=0;x<10;x++) for(int y=0;y<24;y++) helper.setBlock(x,y,5,Blocks.STONE);
        for(int x=0;x<10;x++) for(int z=5;z<10;z++) helper.setBlock(x,23,z,Blocks.STONE);
        Player player=FakePlayerFactory.get(helper.getLevel(),new GameProfile(UUID.randomUUID(),"wall-test"));
        Vec3 start=helper.absoluteVec(new Vec3(5,3,4.69));
        player.setPos(start);player.setYRot(0);player.setXRot(0);player.setOnGround(false);
        WallTraversal wall=new WallTraversal();
        for(int i=0;i<4;i++) step(helper,player,wall,RUN);
        helper.assertTrue(wall.mode()==WallTraversal.Mode.RUN,"sprinting into wall must run");
        double stoppedY=player.getY();
        for(int i=0;i<200;i++) step(helper,player,wall,STOP);
        helper.assertTrue(Math.abs(player.getY()-stoppedY)<1e-6,"stop must cling without sliding");
        double x=player.getX();
        step(helper,player,wall,new WallTraversal.Input(-1,1,false,false,false));
        helper.assertTrue(player.getY()<stoppedY && player.getX()<x,"S/D must climb down and right on wall");
        helper.assertTrue(wall.mode()==WallTraversal.Mode.CLIMB,"ordinary wall movement must climb");
        double beforeJump=player.getY(),gap=player.getZ();
        step(helper,player,wall,new WallTraversal.Input(1,0,true,true,false));
        for(int i=0;i<8;i++) step(helper,player,wall,RUN);
        helper.assertTrue(player.getY()>beforeJump+5,"wall leap must gain height");
        helper.assertTrue(Math.abs(player.getZ()-gap)<.02,"wall leap must stay against wall");
        step(helper,player,wall,RUN);
        helper.assertTrue(wall.mode()==WallTraversal.Mode.RUN,"upward leap must reconnect to running");
        boolean mantled=false;
        for(int i=0;i<50;i++) {
            WallTraversal.Frame frame=step(helper,player,wall,RUN);
            mantled|=frame.mode()==WallTraversal.Mode.MANTLE || frame.mode()==WallTraversal.Mode.TOP;
            if(mantled && wall.mode()==WallTraversal.Mode.NONE) break;
        }
        helper.assertTrue(mantled,"wall top must trigger mantle");
        helper.assertTrue(player.getY()>=helper.absoluteVec(new Vec3(0,24,0)).y,"mantle must clear top collision");
        helper.assertTrue(player.getY()<=helper.absoluteVec(new Vec3(0,24.5,0)).y,"running mantle must settle onto roof rather than float above it");
        helper.assertTrue(player.getZ()>helper.absoluteVec(new Vec3(0,0,5.3)).z,"mantle must carry player onto roof");
        helper.succeed();
    }

    @GameTest(template="wall_test",timeoutTicks=100)
    public static void sidewaysWallAndRelease(GameTestHelper helper) {
        for(int z=0;z<10;z++) for(int y=0;y<24;y++) helper.setBlock(5,y,z,Blocks.STONE);
        Player player=FakePlayerFactory.get(helper.getLevel(),new GameProfile(UUID.randomUUID(),"wall-test"));
        player.setPos(helper.absoluteVec(new Vec3(4.69,3,5)));player.setYRot(-90);player.setXRot(0);
        WallTraversal wall=new WallTraversal();step(helper,player,wall,RUN);
        double z=player.getZ();
        step(helper,player,wall,new WallTraversal.Input(0,1,false,false,false));
        helper.assertTrue(player.getZ()>z,"D must follow right tangent on east wall");
        double x=player.getX();
        step(helper,player,wall,new WallTraversal.Input(0,0,false,false,true));
        helper.assertTrue(!wall.attached() && player.getX()<x,"release must detach away from wall");
        for(int i=0;i<8;i++) step(helper,player,wall,RUN);
        helper.assertTrue(!wall.attached(),"release cooldown must prevent immediate relatching");
        helper.succeed();
    }

    @GameTest(template="wall_test",timeoutTicks=100)
    public static void leapAcrossTheTopEdge(GameTestHelper helper) {
        for(int x=0;x<10;x++) for(int y=0;y<24;y++) helper.setBlock(x,y,5,Blocks.STONE);
        for(int x=0;x<10;x++) for(int z=5;z<10;z++) helper.setBlock(x,23,z,Blocks.STONE);
        Player player=FakePlayerFactory.get(helper.getLevel(),new GameProfile(UUID.randomUUID(),"wall-test"));
        player.setPos(helper.absoluteVec(new Vec3(5,21.5,4.69)));player.setYRot(0);player.setXRot(0);
        WallTraversal wall=new WallTraversal();step(helper,player,wall,RUN);
        boolean mantled=false;
        for(int i=0;i<20;i++) {
            var frame=step(helper,player,wall,new WallTraversal.Input(1,0,true,i==0,false));
            mantled|=frame.mode()==WallTraversal.Mode.MANTLE || frame.mode()==WallTraversal.Mode.TOP;
            if(mantled && !wall.attached())break;
        }
        helper.assertTrue(mantled,"upward leap crossing the edge must transition onto the roof");
        helper.assertTrue(player.getZ()>helper.absoluteVec(new Vec3(0,0,5.3)).z,"leap mantle must carry forward over the edge");
        helper.succeed();
    }

    @GameTest(template="wall_test",timeoutTicks=100)
    public static void continuousMountainStepsKeepGroundTraversal(GameTestHelper helper) {
        for (int z = 2; z <= 9; z++) {
            int top = Math.min(6, z - 1);
            for (int x = 3; x <= 6; x++) for (int y = 0; y <= 9; y++) {
                helper.setBlock(x,y,z,y <= top ? Blocks.STONE : Blocks.AIR);
            }
        }
        Player player = FakePlayerFactory.get(helper.getLevel(),new GameProfile(UUID.randomUUID(),"slope-test"));
        player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.STEP_HEIGHT).setBaseValue(1.0);
        player.setYRot(0);
        player.setPos(helper.absoluteVec(new Vec3(5,2,2.69)));
        player.setOnGround(true);
        WallTraversal wall = new WallTraversal();
        for (int tick = 0; tick < 12 && player.getZ() < helper.absoluteVec(new Vec3(0,0,7.3)).z; tick++) {
            player.setDeltaMovement(0,-.08,1.45);
            var frame = wall.tick(RUN,WallContacts.sample(player,wall),player.getY(),1.45,.36,1);
            helper.assertTrue(!frame.handled(),"one-block slope became " + frame.mode() + " at tick " + tick);
            player.move(MoverType.SELF,player.getDeltaMovement());
        }
        helper.assertTrue(player.getY() >= helper.absoluteVec(new Vec3(0,7,0)).y - .01,
                "sprint must reach the mountain plateau");
        helper.succeed();
    }

    @GameTest(template="wall_test",timeoutTicks=100)
    public static void leafGapRespectsBodyAndDeliberateWallRun(GameTestHelper helper) {
        for (int x = 3; x <= 7; x++) for (int y = 1; y <= 7; y++) for (int z = 3; z <= 7; z++) {
            helper.setBlock(x,y,z,y == 1 ? Blocks.STONE : Blocks.AIR);
        }
        for (int y = 2; y <= 6; y++) {
            helper.setBlock(4,y,5,Blocks.OAK_LEAVES);
            helper.setBlock(6,y,5,Blocks.OAK_LOG);
        }
        Player player = FakePlayerFactory.get(helper.getLevel(),new GameProfile(UUID.randomUUID(),"tree-gap"));
        player.setYRot(0);
        player.setPos(helper.absoluteVec(new Vec3(5.5,2,4.69)));
        player.setOnGround(true);
        WallTraversal wall = new WallTraversal();
        var gap = wall.tick(RUN,WallContacts.sample(player,wall),player.getY(),.9,.36,1);
        helper.assertTrue(!gap.handled(),"open tree gap must leave steering to the player");
        player.move(MoverType.SELF,new Vec3(0,-.08,1.3));
        helper.assertTrue(player.getZ() > helper.absoluteVec(new Vec3(0,0,5.9)).z,"standing player fits the gap");
        helper.setBlock(5,3,4,Blocks.OAK_LEAVES);
        player.move(MoverType.SELF,new Vec3(0,0,-1.3));
        helper.assertTrue(player.getZ() >= helper.absoluteVec(new Vec3(0,0,5.29)).z,
                "one vertical air block must not fit a standing player");
        player.setPos(helper.absoluteVec(new Vec3(6.5,2,4.69)));
        player.setOnGround(true);
        var trunk = wall.tick(RUN,WallContacts.sample(player,wall),player.getY(),.9,.36,1);
        helper.assertTrue(trunk.mode()==WallTraversal.Mode.RUN && Math.abs(trunk.x())<1.e-8,
                "holding forward into the trunk must wall-run even with an open path beside it");
        helper.succeed();
    }

    @GameTest(template="wall_test",timeoutTicks=100)
    public static void broadCeilingCannotLoopVaultButOpenBalconyCanBeClimbed(GameTestHelper helper) {
        // A one-block balcony in front of a taller facade, initially extended into a broad ceiling.
        for (int x = 3; x <= 7; x++) {
            for (int z = 2; z <= 5; z++) for (int y = 1; y <= 8; y++) {
                boolean solid = z == 5 || y == 4;
                helper.setBlock(x,y,z,solid ? Blocks.STONE : Blocks.AIR);
            }
        }
        Player player=FakePlayerFactory.get(helper.getLevel(),new GameProfile(UUID.randomUUID(),"overhang-test"));
        player.setYRot(0);
        player.setPos(helper.absoluteVec(new Vec3(5.5,2.19,4.69)));
        player.setOnGround(false);
        WallTraversal wall=new WallTraversal();
        for (int tick = 0; tick < 160; tick++) {
            var frame=step(helper,player,wall,RUN);
            helper.assertTrue(!WallTraversal.crossingTop(frame.mode()),"unreachable ceiling triggered " + frame.mode());
        }
        for (int x = 3; x <= 7; x++) for (int z = 2; z <= 3; z++) helper.setBlock(x,4,z,Blocks.AIR);
        int vaults=0;
        for (int tick = 0; tick < 30 && !wall.finishedOnTop(); tick++) {
            var previous=wall.mode();
            var frame=step(helper,player,wall,RUN);
            if (frame.mode()==WallTraversal.Mode.VAULT && previous!=frame.mode()) vaults++;
        }
        helper.assertTrue(vaults==1 && wall.finishedOnTop(),"open balcony must finish exactly one vault, starts=" + vaults);
        helper.assertTrue(Math.abs(player.getY()-helper.absoluteVec(new Vec3(0,5,0)).y)<.05,
                "feet must settle on the balcony");
        helper.succeed();
    }

    @GameTest(template="wall_test",timeoutTicks=100)
    public static void diagonalRunIntoCliffKeepsWallDespiteAdjacentStep(GameTestHelper helper) {
        for (int x=3; x<=7; x++) for (int z=3; z<=6; z++) for (int y=1; y<=7; y++) {
            boolean solid=y==1 || z==5 || (x==4 && y==2 && z<5);
            helper.setBlock(x,y,z,solid ? Blocks.STONE : Blocks.AIR);
        }
        Player player=FakePlayerFactory.get(helper.getLevel(),new GameProfile(UUID.randomUUID(),"cliff-corner"));
        player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.STEP_HEIGHT).setBaseValue(1.0);
        player.setYRot(40);
        player.setPos(helper.absoluteVec(new Vec3(5.39,2,4.69)));
        player.setOnGround(true);
        player.setDeltaMovement(-.93,-.08,1.11);
        WallTraversal wall=new WallTraversal();
        var surface=WallContacts.sample(player,wall);
        var frame=wall.tick(RUN,surface,player.getY(),1.45,.36,1);
        helper.assertTrue(surface.torso() && surface.normalZ()==-1 && frame.mode()==WallTraversal.Mode.RUN,
                "lateral progress over a step must not suppress the cliff ahead");
        helper.succeed();
    }

    @GameTest(template="wall_test",timeoutTicks=100)
    public static void halfStepFitsUnderLowLeaves(GameTestHelper helper) {
        for (int x=4; x<=6; x++) for (int z=3; z<=7; z++) {
            helper.setBlock(x,1,z,Blocks.STONE);
            helper.setBlock(x,2,z,z < 5 ? Blocks.STONE_SLAB : Blocks.STONE);
            helper.setBlock(x,3,z,Blocks.AIR);
            helper.setBlock(x,4,z,Blocks.AIR);
            helper.setBlock(x,5,z,Blocks.OAK_LEAVES);
        }
        Player player=FakePlayerFactory.get(helper.getLevel(),new GameProfile(UUID.randomUUID(),"canopy-step"));
        player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.STEP_HEIGHT).setBaseValue(1.0);
        player.setPos(helper.absoluteVec(new Vec3(5.5,2.5,4.69)));
        player.setOnGround(true);
        helper.assertTrue(com.codex.blacklight.movement.TerrainTraversal.canStepPast(player,new Vec3(0,0,1),.9),
                "half-block rise has enough headroom under the leaves");
        player.move(MoverType.SELF,new Vec3(0,-.08,.9));
        helper.assertTrue(Math.abs(player.getY()-helper.absoluteVec(new Vec3(0,3,0)).y)<.01
                && player.getZ()>helper.absoluteVec(new Vec3(0,0,5.3)).z,"body must step onto the higher block");
        helper.succeed();
    }

    private static WallTraversal.Frame step(GameTestHelper helper,Player player,WallTraversal wall,WallTraversal.Input input) {
        var frame=wall.tick(input,WallContacts.sample(player,wall),player.getY(),
                player.getDeltaMovement().horizontalDistance(),.36,1);
        if(frame.handled()) {
            player.setDeltaMovement(frame.x(),frame.y(),frame.z());
            player.setOnGround(false);
            player.move(MoverType.SELF,player.getDeltaMovement());
            helper.assertTrue(player.level().noCollision(player),"wall traversal must never penetrate blocks");
        }
        return frame;
    }
}
