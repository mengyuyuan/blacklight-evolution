package com.codex.blacklight.gametest;

import com.codex.blacklight.entity.MilitarySoldier;
import com.codex.blacklight.military.MilitaryAimControl;
import com.codex.blacklight.military.MilitaryFaction;
import com.codex.blacklight.military.MilitaryFireControl;
import com.codex.blacklight.military.MilitaryLoadoutProfile;
import com.codex.blacklight.registry.ModEntities;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class MilitaryAimGameTest {
    private static final class Actor extends MilitarySoldier {
        Actor(Level level){super(ModEntities.BLACKWATCH_SOLDIER.get(),level);}
        @Override public MilitaryFaction faction(){return MilitaryFaction.BLACKWATCH;}
        @Override protected double firingPostureSample(){return 0;}
    }
    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h;
        final List<Entity> entities=new ArrayList<>();
        Actor soldier;
        Mob enemy;
        int age,stage,at,ammunition;
        float health,lastYaw;
        boolean started,done;
        Fixture(GameTestHelper h){
            this.h=h;
            for(int x=0;x<16;x++)for(int z=0;z<16;z++){
                h.setBlock(x,1,z,Blocks.STONE);
                for(int y=2;y<7;y++)h.setBlock(x,y,z,Blocks.AIR);
            }
        }
        void start(){
            soldier=new Actor(h.getLevel());soldier.setPos(h.absoluteVec(new Vec3(8.5,2,8.5)));
            soldier.setNoAi(true);soldier.setPersistenceRequired();soldier.setAutomaticUnitJoin(false);
            soldier.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
            h.assertTrue(soldier.initializeLoadout(MilitaryLoadoutProfile.forDuty(MilitaryLoadoutProfile.Duty.RIFLE),"aim-fixture"),"finite rifle issued");
            h.assertTrue(h.getLevel().addFreshEntity(soldier),"real soldier enters level");entities.add(soldier);soldier.adoptFieldPost();
            enemy=ModEntities.COMMON_INFECTED.get().create(h.getLevel());enemy.setPos(h.absoluteVec(new Vec3(8.5,2,14.5)));
            enemy.setNoAi(true);enemy.setPersistenceRequired();enemy.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);enemy.setHealth(1000);
            enemy.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);h.getLevel().addFreshEntity(enemy);entities.add(enemy);
            ammunition=ammunition();health=enemy.getHealth();soldier.setNoAi(false);soldier.setTarget(enemy);started=true;
        }
        int ammunition(){return soldier.magazineAmmo()+soldier.reserveAmmo();}
        boolean aligned(){Vec3 d=enemy.getBoundingBox().getCenter().subtract(soldier.getEyePosition().add(0,-.18,0));return MilitaryAimControl.aligned(soldier.gunAimYaw(1),d.x,d.z);}
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

    @GameTest(template="blackwatch_arena",batch="soldierAimReacquire",timeoutTicks=220)
    public static void movingBehindAnActiveRifleStopsRealBulletsUntilTheGunTurnsAndReaims(GameTestHelper h){
        run(h,f->{
            if(f.stage==0&&f.ammunition()<f.ammunition&&f.enemy.getHealth()<f.health){
                f.lastYaw=f.soldier.gunAimYaw(1);
                h.assertTrue(Float.isFinite(f.lastYaw),"real rifle fire has authoritative visible aim");
                Vec3 rear=Vec3.directionFromRotation(0,f.lastYaw).scale(-6);
                f.enemy.setPos(f.soldier.position().add(rear));f.enemy.setDeltaMovement(Vec3.ZERO);
                f.ammunition=f.ammunition();f.health=f.enemy.getHealth();f.at=f.age;f.stage=1;
                h.assertTrue(!f.aligned(),"target really moved behind the existing gun bearing");return;
            }
            if(f.stage==1){
                float yaw=f.soldier.gunAimYaw(1);
                h.assertTrue(Float.isFinite(yaw)&&Math.abs(MilitaryAimControl.wrap(yaw-f.lastYaw))<=MilitaryAimControl.TURN_DEGREES+.001,"live aiming never snaps beyond its tick limit");
                f.lastYaw=yaw;
                if(f.age-f.at<=3){
                    h.assertTrue(!f.aligned()&&f.ammunition()==f.ammunition&&f.enemy.getHealth()==f.health,"rear target receives no instant redirected round and costs no ammunition");
                }
                if(f.ammunition()<f.ammunition&&f.enemy.getHealth()<f.health){
                    h.assertTrue(f.age-f.at>=6&&f.aligned(),"finite real gunfire resumes only after turning and reacquisition");
                    f.soldier.setEquipped(false);f.ammunition=f.ammunition();f.health=f.enemy.getHealth();f.at=f.age;f.stage=2;
                    h.assertTrue(!Float.isFinite(f.soldier.gunAimYaw(1)),"disarming immediately relinquishes synchronized gun aim");
                }
            }else if(f.stage==2&&f.age-f.at>=12){
                h.assertTrue(f.ammunition()==f.ammunition&&f.enemy.getHealth()==f.health,"canceled aiming cannot leave delayed shots");f.pass();
            }
            h.assertTrue(f.age<180,"live rifle reacquires within the bounded fixture");
        });
    }

    @GameTest(template="blackwatch_arena",batch="soldierAimLostTarget",timeoutTicks=100)
    public static void removingTheTargetDuringAimClearsTheBearingAndSpendsNoRound(GameTestHelper h){
        run(h,f->{
            if(f.stage==0&&Float.isFinite(f.soldier.gunAimYaw(1))&&f.soldier.phase()==MilitaryFireControl.AIM){
                f.enemy.discard();f.ammunition=f.ammunition();f.at=f.age;f.stage=1;
            }else if(f.stage==1&&f.age-f.at>=8){
                h.assertTrue(!Float.isFinite(f.soldier.gunAimYaw(1))&&f.soldier.phase()==MilitaryFireControl.IDLE,"invalid target clears the controller and displayed aim");
                h.assertTrue(f.ammunition()==f.ammunition,"invalid target never completes a deferred shot");f.pass();
            }
            h.assertTrue(f.age<60,"fixture observes and cancels a real aim window");
        });
    }
}
