package com.codex.blacklight.gametest;

import com.codex.blacklight.entity.BlackwatchHelicopter;
import com.codex.blacklight.military.aircraft.HelicopterArmament;
import com.codex.blacklight.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Deferred real-entity tick regression: no reflection or direct fire/helper calls. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class HelicopterGunEnvelopeGameTest {
    @GameTest(template="helicopter_arena",batch="airGunEnvelope",timeoutTicks=200)
    public static void alignedGunWaitsForOrdinaryBodyEnvelopeThenFiresAndStopsOnTargetLoss(GameTestHelper h){
        var f=new HelicopterPilotGameTest.Fixture(h,432560);var level=f.level;
        // Circle uses a sixty-cell loaded height sample; keep the fixture's
        // terrain independent of neighboring terrain and other GameTests.
        for(int cx=(f.x-64)>>4;cx<=(f.x+64)>>4;cx++)for(int cz=-4;cz<=4;cz++){
            long key=ChunkPos.asLong(cx,cz);f.forced.putIfAbsent(key,level.getForcedChunks().contains(key));level.setChunkForced(cx,cz,true);
        }
        for(int dx=-36;dx<=36;dx++)for(int z=-36;z<=36;z++){
            int top=Math.max(116,level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,f.x+dx,z));
            level.setBlock(new BlockPos(f.x+dx,80,z),Blocks.SMOOTH_STONE.defaultBlockState(),2);
            for(int y=81;y<=top;y++){var p=new BlockPos(f.x+dx,y,z);if(!level.isEmptyBlock(p))level.setBlock(p,Blocks.AIR.defaultBlockState(),2);}
        }
        Vec3 at=new Vec3(f.x,81,0);
        var target=ModEntities.COMMON_INFECTED.get().create(level);h.assertTrue(target!=null,"target type exists");
        target.setPos(at);target.setNoAi(true);target.setNoGravity(true);target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(4000);target.setHealth(4000);
        level.addFreshEntity(target);f.entities.add(target);
        var craft=f.craft(20);craft.setPos(at.add(0,20,-20));craft.setYRot(0);
        var crew=f.crew(craft);for(int i=0;i<crew.size();i++)if(i<2)crew.get(i).setNoAi(false);else crew.get(i).discard();
        var caller=ModEntities.BLACKWATCH_OFFICER.get().create(level);h.assertTrue(caller!=null,"caller type exists");
        caller.setPos(at.add(-12,0,0));caller.setAutomaticUnitJoin(false);level.addFreshEntity(caller);caller.setTarget(target);
        h.assertTrue(craft.deployTo(at),"real radio sortie established");craft.combat().supportCall(caller);caller.discard();
        craft.combat().circle().begin(.5);
        var saved=new CompoundTag();craft.saveWithoutId(saved);saved.putInt("FlightPhase",BlackwatchHelicopter.Phase.STRAFE.ordinal());
        var combat=saved.getCompound("AirCombat");combat.putUUID("Target",target.getUUID());combat.putInt("Age",1);
        var tactic=new CompoundTag();tactic.putInt("Version",1);tactic.putString("Tactic","CIRCLE");tactic.putInt("Age",0);combat.put("Tactics",tactic);
        craft.load(saved);
        for(int i=0;i<40;i++)craft.aimWeapon(target.getBoundingBox().getCenter());
        int[] observed={0},previousTick={craft.tickCount},lostAt={-1},accepted={0};boolean[] done={false};
        h.onEachTick(()->{
            if(done[0])return;
            try{
                if(craft.tickCount==previousTick[0])return;previousTick[0]=craft.tickCount;int age=++observed[0];
                h.assertTrue(age<130,"bounded actual-entity scenario");
                if(lostAt[0]>=0){
                    h.assertTrue(craft.combat().gunShots()==accepted[0],"lost target cannot fire another round");
                    if(age-lostAt[0]>=3){h.assertTrue(craft.combat().tactic().equals("NONE"),"lost observation cancels the actual gun thread");done[0]=true;f.close();h.succeed();}return;
                }
                if(age<=40){
                    h.assertTrue(craft.combat().gunShots()==0&&craft.combat().rocketShots()==0,"aligned gimbal alone cannot authorize steep ordinary fire");
                    h.assertTrue(craft.combat().tactic().equals("CIRCLE")&&craft.rotor()>=.9F,"real circle thread and operational rotor are active");
                    if(age==40){
                        Vec3 aimed=HelicopterArmament.rotate(HelicopterArmament.direction(craft.gunYaw(),craft.gunPitch()),craft.getYRot(),craft.bank());
                        Vec3 to=target.getBoundingBox().getCenter().subtract(craft.weaponPoint(HelicopterArmament.GUN)).normalize();
                        h.assertTrue(aimed.dot(to)>.9995&&craft.gunPitch()>40,"real chin gun is aligned inside the old gimbal limit while source body gate denies");
                    }
                }else if(craft.combat().gunShots()>0){
                    h.assertTrue(target.getHealth()<target.getMaxHealth(),"the actual mounted gun damages the target after entering the permitted envelope");
                    h.assertTrue(craft.combat().rounds()==240&&craft.combat().rockets()==8,"AI does not debit player stores");
                    accepted[0]=craft.combat().gunShots();lostAt[0]=age;target.discard();return;
                }
                // Controlled geometry isolates the fire gate while the entity's
                // own tick still performs targeting, movement, aiming and fire.
                craft.setPos(at.add(0,age<40?20:8,-20));craft.setDeltaMovement(Vec3.ZERO);craft.setYRot(0);
                target.setPos(at);target.setDeltaMovement(Vec3.ZERO);
            }catch(RuntimeException|AssertionError failure){done[0]=true;f.close();throw failure;}
        });
    }
}
