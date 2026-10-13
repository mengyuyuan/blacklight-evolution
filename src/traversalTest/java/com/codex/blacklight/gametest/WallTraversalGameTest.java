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
