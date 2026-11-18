package com.codex.blacklight.gametest;

import com.codex.blacklight.entity.MilitarySoldier;
import com.codex.blacklight.military.MilitaryFaction;
import com.codex.blacklight.military.MilitaryFireControl;
import com.codex.blacklight.military.MilitaryLoadoutProfile;
import com.codex.blacklight.registry.ModEntities;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real FireTeamGoal and finite guns; no direct fire invocation or forced firing phase. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class MilitaryRifleRangeGameTest {
    private static final class Actor extends MilitarySoldier {
        Actor(Level level,boolean heavy){super(heavy?ModEntities.BLACKWATCH_HEAVY.get():ModEntities.BLACKWATCH_SOLDIER.get(),level);}
        @Override public MilitaryFaction faction(){return MilitaryFaction.BLACKWATCH;}
        @Override protected double firingPostureSample(){return 0;}
    }
    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h;
        final boolean heavy;
        final List<Entity> entities=new ArrayList<>();
        final List<ChunkPos> forced=new ArrayList<>();
        Actor soldier;
        Mob enemy;
        int age,stage,at,ammunition;
        float health;
        boolean started,done;
        Fixture(GameTestHelper h,boolean heavy){
            this.h=h;this.heavy=heavy;
            Set<Long> seen=new HashSet<>();
            for(int x=3;x<=13;x++)for(int z=1;z<=39;z++){
                var p=h.absolutePos(new BlockPos(x,1,z));var chunk=new ChunkPos(p);
                if(seen.add(chunk.toLong())&&!h.getLevel().getForcedChunks().contains(chunk.toLong())){
                    h.getLevel().setChunkForced(chunk.x,chunk.z,true);forced.add(chunk);
                }
            }
            for(int x=3;x<=13;x++)for(int z=1;z<=39;z++){
                h.setBlock(x,1,z,Blocks.STONE);
                for(int y=2;y<8;y++)h.setBlock(x,y,z,Blocks.AIR);
            }
        }
        void start(){
            soldier=new Actor(h.getLevel(),heavy);soldier.setPos(h.absoluteVec(new Vec3(8.5,2,6.5)));
            soldier.setNoAi(true);soldier.setPersistenceRequired();soldier.setAutomaticUnitJoin(false);
            // Fix the firing mark without bypassing the real goal, its target, aim,
            // LoF, squad lease, finite magazine, or damage. The old gate never fires here.
            soldier.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
            soldier.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
            var duty=heavy?MilitaryLoadoutProfile.Duty.MACHINE_GUN:MilitaryLoadoutProfile.Duty.RIFLE;
            h.assertTrue(soldier.initializeLoadout(MilitaryLoadoutProfile.forDuty(duty),"rifle-range-fixture"),"finite native-duty gun issued");
            h.assertTrue(h.getLevel().addFreshEntity(soldier),"real soldier enters level");entities.add(soldier);soldier.adoptFieldPost();
            soldier.getRandom().setSeed(434911L);
            enemy=ModEntities.COMMON_INFECTED.get().create(h.getLevel());enemy.setPos(h.absoluteVec(new Vec3(8.5,2,31.5)));
            enemy.setNoAi(true);enemy.setPersistenceRequired();enemy.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);enemy.setHealth(1000);
            enemy.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
            h.assertTrue(h.getLevel().addFreshEntity(enemy),"known infected target enters level");entities.add(enemy);
            ammunition=ammunition();health=enemy.getHealth();
            h.assertTrue(Math.abs(soldier.getAttributeValue(Attributes.FOLLOW_RANGE)-28)<.001,"25-block scenario retains ordinary acquisition range");
            // Default target selector must discover the infected target itself.
            soldier.setNoAi(false);started=true;
        }
        int ammunition(){return soldier.magazineAmmo()+soldier.reserveAmmo();}
        void range(double expected){h.assertTrue(Math.abs(soldier.distanceToSqr(enemy)-expected*expected)<.01,"actor really remains at "+expected+" blocks, without walking into the old 22-block gate");}
        void moveTarget(double distance){
            enemy.setPos(h.absoluteVec(new Vec3(8.5,2,6.5+distance)));enemy.setDeltaMovement(Vec3.ZERO);
            soldier.setTarget(enemy);ammunition=ammunition();health=enemy.getHealth();at=age;range(distance);
        }
        void pass(){done=true;close();h.succeed();}
        public void close(){entities.forEach(Entity::discard);for(var chunk:forced)h.getLevel().setChunkForced(chunk.x,chunk.z,false);forced.clear();}
    }
    private static void run(GameTestHelper h,boolean heavy,Consumer<Fixture> tick){
        var f=new Fixture(h,heavy);
        h.onEachTick(()->{
            if(f.done||h.getTick()<20)return;
            try{if(!f.started)f.start();tick.accept(f);f.age++;}
            catch(RuntimeException|AssertionError failure){f.done=true;f.close();throw failure;}
        });
    }

    @GameTest(template="blackwatch_arena",batch="soldierRifleRangeBoundary",timeoutTicks=660)
    public static void rifleFiresAtTwentyFiveCancelsOutsideThirtyAndResumesAtTheInclusiveBoundary(GameTestHelper h){
        run(h,false,f->{
            if(f.stage==0){
                f.range(25);
                if(f.ammunition()<f.ammunition&&f.enemy.getHealth()<f.health){
                    h.assertTrue(f.soldier.getTarget()==f.enemy,"default sensing actually acquired the 25-block target");
                    // Sensing is still the existing MC 28-block adapter. Widen only
                    // this fixture's target retention to isolate the authored shoot gate.
                    f.soldier.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(48);
                    f.moveTarget(31);f.stage=1;
                }
                h.assertTrue(f.age<280,"ordinary rifle fires through its real goal at 25 blocks");
            }else if(f.stage==1){
                f.range(31);
                h.assertTrue(f.ammunition()==f.ammunition&&f.enemy.getHealth()==f.health,"leaving the shoot bank cancels remaining burst bullets without spending rounds");
                if(f.age-f.at>=35){
                    h.assertTrue(f.soldier.phase()==MilitaryFireControl.IDLE&&!Float.isFinite(f.soldier.gunAimYaw(1)),"out-of-range target releases phase and synchronized gun aim");
                    f.moveTarget(30);f.stage=2;
                }
            }else{
                f.range(30);
                if(f.ammunition()<f.ammunition&&f.enemy.getHealth()<f.health)f.pass();
                h.assertTrue(f.age-f.at<280,"exactly 30 blocks re-enters a fresh real aim/fire cycle");
            }
        });
    }

    @GameTest(template="blackwatch_arena",batch="soldierSawRange",timeoutTicks=330)
    public static void heavySawUsesTheSameRifleBankBeyondTheOldTwentyTwoBlockCutoff(GameTestHelper h){
        run(h,true,f->{
            f.range(25);
            h.assertTrue(f.soldier.heavy(),"fixture uses the registered heavy identity and its normal combat profile");
            if(f.ammunition()<f.ammunition&&f.enemy.getHealth()<f.health){h.assertTrue(f.soldier.getTarget()==f.enemy,"heavy default sensing acquired the target");f.pass();}
            h.assertTrue(f.age<280,"heavy SAW fires real finite ammunition and damages the target at 25 blocks");
        });
    }
}
