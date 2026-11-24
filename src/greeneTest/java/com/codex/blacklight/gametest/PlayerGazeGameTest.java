package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.PlayerGazeField;
import com.codex.blacklight.power.*;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class PlayerGazeGameTest {
    private static void aim(ServerPlayer p,Vec3 at){
        Vec3 delta=at.subtract(p.getEyePosition());
        p.setYRot((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)));
        p.setXRot((float)-Math.toDegrees(Math.atan2(delta.y,delta.horizontalDistance())));
        p.yHeadRot=p.getYRot();p.yHeadRotO=p.getYRot();p.yRotO=p.getYRot();p.xRotO=p.getXRot();
    }
    @GameTest(templateNamespace="blacklight",template="blackwatch_arena",batch="gaze_boss_resistance",timeoutTicks=80)
    public static void bossCanMoveDuringCompression(GameTestHelper h){
        var arena=new GoddessTerrainGameTest.Arena(h);
        arena.boss.discard();
        var chunk=new net.minecraft.world.level.ChunkPos(h.absolutePos(new BlockPos(2,5,2)));
        var pad=new BlockPos(chunk.getMinBlockX()+6,(int)arena.base.y,chunk.getMinBlockZ()+2);
        for(int x=-2;x<=5;x++)for(int z=-1;z<=12;z++){
            arena.level.setBlockAndUpdate(pad.offset(x,-1,z),Blocks.STONE.defaultBlockState());
            for(int y=0;y<10;y++)arena.level.setBlockAndUpdate(pad.offset(x,y,z),Blocks.AIR.defaultBlockState());
        }
        var player=arena.player("GazeBoss",Vec3.atBottomCenterOf(pad));
        var state=ModAttachments.getData(player,ModAttachments.PLAYER_STATE);
        state.infect();state.setPrototypeMode(true);PlayerGazeField.inherit(player);
        UtilityPowerSystem.toggle(player,PowerForm.GAZE_VACUOLE);
        var boss=EntityType.WITHER.create(arena.level);boss.setNoAi(true);boss.setNoGravity(true);
        var at=player.position().add(0,3,9);boss.moveTo(at.x,at.y,at.z,0,0);arena.level.addFreshEntity(boss);
        aim(player,boss.getBoundingBox().getCenter());var mark=PlayerGazeField.aim(player,1);
        h.assertTrue(PlayerGazeField.release(player),"inherited gaze can be aimed at a boss");
        var field=arena.level.getEntitiesOfClass(PlayerGazeField.class,player.getBoundingBox().inflate(25))
                .stream().min(java.util.Comparator.comparingDouble(f->f.position().distanceToSqr(mark))).orElseThrow();
        float full=boss.getHealth();
        h.runAfterDelay(16,()->{
            h.assertTrue(field.victim()==boss && PlayerGazeField.squeeze(boss,0)==0,"caught boss retains its body control");
            boss.setPos(at.add(5,0,0));
        });
        h.runAfterDelay(38,()->{
            h.assertTrue(boss.distanceToSqr(at.add(5,0,0))<1 && boss.getHealth()==full,"boss outside the fixed field stays free and takes no remote damage");
            System.out.println("PLAYER_GAZE_BOSS resisted=true escaped=true health="+boss.getHealth());
            boss.discard();arena.close();h.succeed();
        });
    }
    @GameTest(templateNamespace="blacklight",template="blackwatch_arena",batch="gaze_inheritance",timeoutTicks=105)
    public static void airborneCaptureUsesSightAndAllowsEscape(GameTestHelper h){
        var arena=new GoddessTerrainGameTest.Arena(h);
        arena.boss.discard();
        var chunk=new net.minecraft.world.level.ChunkPos(h.absolutePos(new BlockPos(2,5,2)));
        var pad=new BlockPos(chunk.getMinBlockX()+7,(int)arena.base.y,chunk.getMinBlockZ()+2);
        for(int x=-2;x<=2;x++)for(int z=-1;z<=12;z++){
            arena.level.setBlockAndUpdate(pad.offset(x,-1,z),Blocks.STONE.defaultBlockState());
            for(int y=0;y<12;y++)arena.level.setBlockAndUpdate(pad.offset(x,y,z),Blocks.AIR.defaultBlockState());
        }
        var p=arena.player("GazeCast",Vec3.atBottomCenterOf(pad));
        var state=ModAttachments.getData(p,ModAttachments.PLAYER_STATE);
        state.infect();state.setPrototypeMode(true);state.unlock(PowerForm.BLADE);state.activate(PowerForm.BLADE);
        h.assertTrue(!state.buy(PowerForm.GAZE_VACUOLE),"EP cannot purchase the head reward");
        PlayerGazeField.inherit(p);
        UtilityPowerSystem.toggle(p,PowerForm.GAZE_VACUOLE);
        var mob=EntityType.HUSK.create(arena.level);
        var feet=p.position().add(0,4,10);
        mob.setNoAi(true);mob.setNoGravity(true);mob.moveTo(feet.x,feet.y,feet.z,0,0);arena.level.addFreshEntity(mob);
        var middle=mob.getBoundingBox().getCenter();aim(p,middle);
        var wall=BlockPos.containing(p.getEyePosition().lerp(middle,.5));
        arena.level.setBlockAndUpdate(wall,Blocks.OBSIDIAN.defaultBlockState());
        h.assertTrue(PlayerGazeField.aim(p,1).distanceTo(middle)>4,"stone intercepts sight before the airborne mob");
        arena.level.setBlockAndUpdate(wall,Blocks.AIR.defaultBlockState());
        Vec3 expected=PlayerGazeField.aim(p,1);
        int biomass=state.getBiomass();float hp=mob.getHealth();
        boolean fired=PlayerGazeField.release(p);
        System.out.println("PLAYER_GAZE_AIM eye="+p.getEyePosition()+" view="+p.getViewVector(1)+" expected="+expected+" target="+middle+" fired="+fired);
        if(expected.distanceTo(middle)>=1.5 || !fired){arena.close();mob.discard();}
        h.assertTrue(expected.distanceTo(middle)<1.5 && fired,"server accepts visible airborne cast");
        var fields=arena.level.getEntitiesOfClass(PlayerGazeField.class,p.getBoundingBox().inflate(45));
        var field=fields.stream().min(java.util.Comparator.comparingDouble(f->f.position().distanceToSqr(expected))).orElseThrow();
        System.out.println("PLAYER_GAZE_PLACED field="+field.position()+" error="+field.position().distanceTo(expected)+" power="+state.getActivePower()+" fields="+fields.size());
        h.assertTrue(field.position().distanceToSqr(expected)<1.e-5 && state.getActivePower()==PowerForm.BLADE,"preview and placed center agree; blade remains active");
        h.assertTrue(!PlayerGazeField.release(p) && biomass-state.getBiomass()==PlayerGazeField.BIOMASS,"same-tick retry cannot spend or cast twice");
        h.runAfterDelay(16,()->{
            System.out.println("PLAYER_GAZE_CLOSE age="+field.age(0)+" ticks="+field.tickCount+" removed="+field.isRemoved()+" mob="+mob.position()+" id="+mob.getId()+" caught="+(field.victim()==null?-1:field.victim().getId())+" owner_active="+(arena.level.getPlayerByUUID(p.getUUID())==p)+" loaded="+arena.level.isPositionEntityTicking(mob.blockPosition())+" squeeze="+PlayerGazeField.squeeze(mob,0));
            h.assertTrue(field.victim()==mob && PlayerGazeField.squeeze(mob,0)>0,"airborne survivor moves into the membrane and compresses");
            h.assertTrue(mob.getHealth()==hp,"charge and capture do not deal premature damage");
        });
        h.runAfterDelay(38,()->{
            h.assertTrue(mob.getHealth()<hp && state.getActivePower()==PowerForm.BLADE,"burst hurt mob and kept the weapon");
            System.out.println("PLAYER_GAZE_AIR health "+hp+" -> "+mob.getHealth()+" biomass cost "+PlayerGazeField.BIOMASS+" marker_error "+field.position().distanceTo(expected));
            mob.discard();arena.close();h.succeed();
        });
    }
    @GameTest(templateNamespace="blacklight",template="blackwatch_arena",batch="gaze_evade",timeoutTicks=100)
    public static void targetCanLeaveCommittedCenter(GameTestHelper h){
        var a=new GoddessTerrainGameTest.Arena(h);
        var p=a.player("GazeEvade",a.base.add(5,0,0));
        var s=ModAttachments.getData(p,ModAttachments.PLAYER_STATE);
        s.infect();s.setPrototypeMode(true);PlayerGazeField.inherit(p);UtilityPowerSystem.toggle(p,PowerForm.GAZE_VACUOLE);
        var m=EntityType.HUSK.create(a.level);m.setNoAi(true);m.setNoGravity(true);var start=p.position().add(0,5,12);
        m.moveTo(start.x,start.y,start.z,0,0);a.level.addFreshEntity(m);
        aim(p,m.getBoundingBox().getCenter());h.assertTrue(PlayerGazeField.release(p),"release accepted");
        float before=m.getHealth();m.setPos(start.add(6,0,0));
        h.runAfterDelay(36,()->{h.assertTrue(m.getHealth()==before && PlayerGazeField.squeeze(m,0)==0,"dodged field never captures or damages");
            System.out.println("PLAYER_GAZE_EVADED health "+m.getHealth());m.discard();a.close();h.succeed();});
    }
}
