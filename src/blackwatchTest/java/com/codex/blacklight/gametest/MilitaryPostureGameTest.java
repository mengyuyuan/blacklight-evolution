package com.codex.blacklight.gametest;

import com.codex.blacklight.entity.MilitarySoldier;
import com.codex.blacklight.firearms.AmmoState;
import com.codex.blacklight.firearms.FirearmItem;
import com.codex.blacklight.military.MilitaryFaction;
import com.codex.blacklight.military.MilitaryFireControl;
import com.codex.blacklight.military.MilitaryLoadoutProfile;
import com.codex.blacklight.registry.ModEntities;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class MilitaryPostureGameTest {
    /** Only the bank draw is deterministic. Entity goals, dimensions, navigation,
     * muzzle/ally tests, finite ammunition and actual bullet damage remain live. */
    private static final class Actor extends MilitarySoldier {
        Actor(Level level){super(ModEntities.BLACKWATCH_SOLDIER.get(),level);}
        @Override public MilitaryFaction faction(){return MilitaryFaction.BLACKWATCH;}
        @Override protected double firingPostureSample(){return .9;}
    }
    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h;
        final List<Entity> entities=new ArrayList<>();
        Actor soldier;
        Mob enemy;
        boolean done,started,reloading;
        int age,stage,at,ammunition,reserve;
        float health;
        BlockPos beam;
        Fixture(GameTestHelper h){
            this.h=h;
            for(int x=0;x<16;x++)for(int z=0;z<16;z++){
                h.setBlock(x,1,z,Blocks.STONE);
                for(int y=2;y<7;y++)h.setBlock(x,y,z,Blocks.AIR);
            }
        }
        void start(){
            soldier=new Actor(h.getLevel());soldier.setPos(h.absoluteVec(new Vec3(3.5,2,3.5)));
            soldier.setNoAi(true);soldier.setPersistenceRequired();soldier.setAutomaticUnitJoin(false);
            soldier.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
            h.assertTrue(soldier.initializeLoadout(MilitaryLoadoutProfile.forDuty(MilitaryLoadoutProfile.Duty.RIFLE),"posture-fixture"),"real finite rifle issue");
            h.assertTrue(h.getLevel().addFreshEntity(soldier),"registered military actor enters real level");entities.add(soldier);soldier.adoptFieldPost();
            enemy=ModEntities.COMMON_INFECTED.get().create(h.getLevel());enemy.setPos(h.absoluteVec(new Vec3(3.5,2,13.5)));
            enemy.setNoAi(true);enemy.setPersistenceRequired();enemy.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);enemy.setHealth(1000);
            enemy.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);h.getLevel().addFreshEntity(enemy);entities.add(enemy);
            ammunition=soldier.magazineAmmo()+soldier.reserveAmmo();health=enemy.getHealth();
            soldier.setNoAi(false);soldier.setTarget(enemy);started=true;
        }
        int ammunition(){return soldier.magazineAmmo()+soldier.reserveAmmo();}
        void pass(){done=true;close();h.succeed();}
        public void close(){entities.forEach(Entity::discard);}
    }
    private static void run(GameTestHelper h,Consumer<Fixture> tick){
        var f=new Fixture(h);
        h.onEachTick(()->{
            if(f.done||h.getTick()<20)return;
            try{if(!f.started)f.start();tick.accept(f);f.age++;}
            catch(RuntimeException|AssertionError failure){f.done=true;f.close();throw failure;}
        });
    }
    @GameTest(template="blackwatch_arena",batch="soldierPostureFire",timeoutTicks=240)
    public static void sourceKneelingFiresRealBulletsAndHandsItsPoseToFiniteReload(GameTestHelper h){
        run(h,f->{
            if(f.stage==0&&f.soldier.getPose()==Pose.CROUCHING&&f.ammunition()<f.ammunition&&f.enemy.getHealth()<f.health){
                h.assertTrue(f.enemy.getHealth()<f.health,"actual crouched shot reaches its real enemy");
                h.assertTrue(f.soldier.getBoundingBox().getYsize()<1.6&&f.soldier.getEyeHeight()<1.5,"kneel changes real collision and muzzle height");
                // A low wall supports the existing reload-cover rule while the
                // rifle's actual crouched muzzle still clears its top.
                h.setBlock(3,2,4,Blocks.STONE);f.reserve=f.soldier.reserveAmmo();
                FirearmItem.store(f.soldier.getMainHandItem(),new AmmoState(0,f.reserve,h.getLevel().getGameTime()));
                f.stage=1;f.at=f.age;return;
            }
            if(f.stage==1){
                if(f.soldier.phase()==MilitaryFireControl.RELOAD){
                    f.reloading=true;h.assertTrue(f.soldier.getPose()==Pose.CROUCHING,"reload owns the same crouch without alternating poses");
                    h.assertTrue(f.ammunition()==f.reserve,"reload transfers only finite reserve rounds into the magazine");
                }
                if(f.reloading&&f.ammunition()<f.reserve){
                    h.assertTrue(f.soldier.getPose()==Pose.CROUCHING,"fresh real gunfire resumes in a valid firing posture");
                    f.soldier.setEquipped(false);f.ammunition=f.ammunition();f.health=f.enemy.getHealth();f.stage=2;f.at=f.age;
                    h.assertTrue(f.soldier.getPose()==Pose.STANDING,"disarming immediately relinquishes the firing pose in open space");
                }
            }
            if(f.stage==2&&f.age-f.at>=12){h.assertTrue(f.ammunition()==f.ammunition&&f.enemy.getHealth()==f.health,"disarmed interruption leaves no delayed volley");f.pass();}
            h.assertTrue(f.age<200,"actual kneel/reload/fire cycle completes within its bounded window");
        });
    }
    @GameTest(template="blackwatch_arena",batch="soldierPostureMuzzle",timeoutTicks=200)
    public static void standingOnlyMuzzleClearanceRejectsTheChosenKneel(GameTestHelper h){
        // A real low firing slit: standing muzzle clears the 1.1875-block front;
        // crouched muzzle cannot. Side walls/roof keep navigation inside the cell.
        run(h,f->{
            if(f.age==0){
                for(int x=2;x<=4;x++)for(int z=2;z<=4;z++){
                    h.setBlock(x,4,z,Blocks.BEDROCK);
                    if(x!=3||z==2)for(int y=2;y<=3;y++)h.setBlock(x,y,z,Blocks.BEDROCK);
                }
                h.setBlock(3,2,4,Blocks.STONE);
                h.setBlock(3,3,4,Blocks.IRON_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.HALF,Half.BOTTOM));
            }
            h.assertTrue(f.soldier.getPose()!=Pose.CROUCHING,"low real muzzle obstruction defeats the deterministic crouch choice");
            if(f.age==100){h.assertTrue(f.ammunition()<f.ammunition&&f.enemy.getHealth()<f.health,"standing fallback still fires actual finite rounds through the slit");f.pass();}
        });
    }
    @GameTest(template="blackwatch_arena",batch="soldierPostureCeiling",timeoutTicks=150)
    public static void interruptedKneelCannotStandThroughALowBeamAndRecoversAfterItsRemoval(GameTestHelper h){
        run(h,f->{
            if(f.stage==0&&f.soldier.getPose()==Pose.CROUCHING){
                f.beam=BlockPos.containing(f.soldier.position()).above();
                h.getLevel().setBlock(f.beam,Blocks.IRON_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.HALF,Half.TOP),2);
                h.assertTrue(h.getLevel().noCollision(f.soldier),"low beam does not intersect the current kneeling body");
                h.assertTrue(!h.getLevel().noCollision(f.soldier,f.soldier.getDimensions(Pose.STANDING).makeBoundingBox(f.soldier.position())),"standing body really intersects the beam");
                f.soldier.setNoAi(true);f.soldier.setEquipped(false);f.ammunition=f.ammunition();f.stage=1;f.at=f.age;
            }
            if(f.stage==1){
                h.assertTrue(f.soldier.getPose()==Pose.CROUCHING&&h.getLevel().noCollision(f.soldier),"canceled action never expands through solid headroom");
                if(f.age-f.at==8){h.getLevel().setBlock(f.beam,Blocks.AIR.defaultBlockState(),2);f.stage=2;f.at=f.age;}
            }else if(f.stage==2&&f.age-f.at>=3){
                h.assertTrue(f.soldier.getPose()==Pose.STANDING&&h.getLevel().noCollision(f.soldier),"safe stand recovery continues even after the combat goal stops");
                h.assertTrue(f.ammunition()==f.ammunition&&f.soldier.phase()==MilitaryFireControl.IDLE,"NoAI/disarm cannot leave a pending firing action");f.pass();
            }
            h.assertTrue(f.age<100,"real pose interruption finishes without retrying combat");
        });
    }
}
