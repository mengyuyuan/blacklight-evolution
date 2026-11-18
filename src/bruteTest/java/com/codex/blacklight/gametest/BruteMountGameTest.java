package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.registry.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class BruteMountGameTest {
    private static void tick(ServerPlayer p){p.tickCount++;ConsumeSystem.tick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(p));}
    private static void check(GameTestHelper h,PowerForm form,float yaw){
        for(int x=0;x<16;x++)for(int z=0;z<16;z++){h.setBlock(x,1,z,Blocks.STONE);for(int y=2;y<8;y++)h.setBlock(x,y,z,Blocks.AIR);}
        var brute=ModEntities.BLACKLIGHT_BRUTE.get().create(h.getLevel());
        brute.setPos(h.absoluteVec(new Vec3(7.5,2,7.5)));brute.setHealth(1);
        ConsumeSequence.mountFacing(brute,yaw);h.getLevel().addFreshEntity(brute);
        var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(java.util.UUID.randomUUID(),"brute-mount"));
        p.setPos(brute.position().subtract(Vec3.directionFromRotation(0,yaw).scale(2)));p.setYRot(yaw);p.setXRot(0);p.tickCount=100;
        var state=p.getData(ModAttachments.PLAYER_STATE);state.infect();state.setPrototypeMode(true);state.unlock(form);state.activate(form);p.setData(ModAttachments.PLAYER_STATE,state);
        ConsumeSystem.request(p);
        h.assertTrue(p.getData(ModAttachments.GRAB_STAGE)==ConsumeVisual.MOUNT,"real acquisition starts mount");
        for(int i=0;i<40;i++){brute.setYRot(yaw+120);brute.yBodyRot=yaw-70;tick(p);}
        h.assertTrue(p.getData(ModAttachments.GRAB_STAGE)==ConsumeVisual.MOUNT_HOLD,"reaches mounted hold despite stale rotation");
        h.assertTrue(Math.abs(brute.getYRot()-yaw)<.001&&Math.abs(brute.yBodyRot-yaw)<.001,"body and socket use captured heading");
        h.assertTrue(p.position().distanceTo(ConsumeSequence.mount(brute))<.02&&Math.abs(p.getYRot()-yaw)<.001,"actor remains on captured back socket");
        p.setYRot(yaw+80);ConsumeSystem.consume(p);tick(p);
        var visual=brute.getData(ModAttachments.CONSUME_VISUAL);
        h.assertTrue(visual.stage()==ConsumeVisual.MOUNT_CONSUME&&Math.abs(visual.facing()-yaw)<.001,"confirmation cannot rotate the shared scene");
        ConsumeSystem.cancel(p);
        h.assertTrue(!brute.getData(ModAttachments.CONSUME_VISUAL).active()&&!brute.isNoAi(),"cancel releases rotation ownership without persistent NoAI");
        ConsumeSequence.mountFacing(brute,yaw+35);tick(p);
        h.assertTrue(Math.abs(brute.getYRot()-yaw-35)<.001,"released target can turn again");
        brute.discard();h.succeed();
    }
    @GameTest(template="blackwatch_arena",batch="brute_mount_claws",timeoutTicks=40)
    public static void clawsMountSharesCapturedHeading(GameTestHelper h){check(h,PowerForm.CLAWS,37);}
    @GameTest(template="blackwatch_arena",batch="brute_mount_muscle",timeoutTicks=40)
    public static void strengthMountSharesCapturedHeading(GameTestHelper h){check(h,PowerForm.MUSCLE_MASS,143);}
}
