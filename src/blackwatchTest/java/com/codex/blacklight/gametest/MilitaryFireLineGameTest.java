package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.CombatTargeting;
import com.codex.blacklight.combat.HydraHurtContact;
import com.codex.blacklight.entity.BlacklightHydra;
import com.codex.blacklight.entity.BlackwatchHelicopter;
import com.codex.blacklight.entity.MilitarySoldier;
import com.codex.blacklight.firearms.FirearmsCombat;
import com.codex.blacklight.infection.InfectedAttackStyle;
import com.codex.blacklight.military.*;
import com.codex.blacklight.military.vehicle.VehicleDamageBridge;
import com.codex.blacklight.registry.ModEntities;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Real rifle goal, finite bullets and current picking mixins. Only posture draw
 * and marks are fixed; no forced firing phase or direct FirearmsCombat.fire. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class MilitaryFireLineGameTest {
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
        BlackwatchHelicopter craft;
        BlacklightHydra hydra;
        PlayerTeam team;
        boolean done,started;
        int age,stage,at,ammunition;
        float health,blockerHealth;
        Fixture(GameTestHelper h){
            this.h=h;
            for(int x=0;x<16;x++)for(int z=0;z<16;z++){
                h.setBlock(x,1,z,Blocks.STONE);
                for(int y=2;y<20;y++)h.setBlock(x,y,z,Blocks.AIR);
            }
        }
        void start(){
            soldier=new Actor(h.getLevel());soldier.setPos(h.absoluteVec(new Vec3(8.5,2,3.5)));
            soldier.setNoAi(true);soldier.setPersistenceRequired();soldier.setAutomaticUnitJoin(false);
            soldier.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
            soldier.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
            h.assertTrue(soldier.initializeLoadout(MilitaryLoadoutProfile.forDuty(MilitaryLoadoutProfile.Duty.RIFLE),"fire-line-fixture"),"real finite rifle issued");
            add(soldier);soldier.adoptFieldPost();soldier.getRandom().setSeed(133177L);
            enemy=ModEntities.COMMON_INFECTED.get().create(h.getLevel());enemy.setPos(h.absoluteVec(new Vec3(8.5,2,13.5)));
            enemy.setNoAi(true);enemy.setPersistenceRequired();enemy.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);enemy.setHealth(1000);
            enemy.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);add(enemy);
            ammunition=ammunition();health=enemy.getHealth();started=true;
        }
        void add(Entity entity){h.assertTrue(h.getLevel().addFreshEntity(entity),"fixture actor joins level");entities.add(entity);}
        int ammunition(){return soldier.magazineAmmo()+soldier.reserveAmmo();}
        Vec3 muzzle(){return soldier.getEyePosition().add(0,-.18,0);}
        EntityHitResult pick(LivingEntity target){
            Vec3 from=muzzle(),to=target.getBoundingBox().getCenter();
            return ProjectileUtil.getEntityHitResult(h.getLevel(),soldier,from,to,new AABB(from,to).inflate(.3),e->FirearmsCombat.bulletCandidate(soldier,e));
        }
        void startGoal(){soldier.setNoAi(false);soldier.setTarget(enemy);}
        void capture(){at=age;ammunition=ammunition();health=enemy.getHealth();}
        void pass(){done=true;close();h.succeed();}
        public void close(){entities.forEach(Entity::discard);if(team!=null){h.getLevel().getScoreboard().removePlayerTeam(team);team=null;}}
    }
    private static void run(GameTestHelper h,Consumer<Fixture> tick){
        var f=new Fixture(h);
        h.onEachTick(()->{
            if(f.done||h.getTick()<20)return;
            try{if(!f.started)f.start();tick.accept(f);f.age++;}
            catch(RuntimeException|AssertionError failure){f.done=true;f.close();throw failure;}
        });
    }

    @GameTest(template="blackwatch_arena",batch="soldierProtectedAircraftLoF",timeoutTicks=440)
    public static void protectedEmptyTransportStopsAnActiveBurstWithoutWastingAmmoAndRemovalReaims(GameTestHelper h){
        run(h,f->{
            if(f.age==0)f.startGoal();
            if(f.stage==0&&f.ammunition()<f.ammunition&&f.enemy.getHealth()<f.health){
                f.craft=ModEntities.BLACKWATCH_HELICOPTER.get().create(h.getLevel());
                f.craft.setPos(h.absoluteVec(new Vec3(8.5,2,8.5)));f.add(f.craft);
                h.assertTrue(VehicleDamageBridge.protectedAircraft(f.soldier,f.craft),"empty native transport is protected by the actual aircraft allegiance policy");
                var hit=f.pick(f.enemy);h.assertTrue(hit!=null&&hit.getEntity()==f.craft,"real bullet ray hits transport before infected target");
                f.blockerHealth=f.craft.health();f.capture();f.stage=1;
            }else if(f.stage==1){
                h.assertTrue(f.ammunition()==f.ammunition&&f.enemy.getHealth()==f.health&&f.craft.health()==f.blockerHealth,"protected nonliving blocker stops remaining rounds, not merely their damage");
                if(f.age-f.at>=35){
                    h.assertTrue(f.soldier.phase()==MilitaryFireControl.IDLE,"blocked fire permit has been relinquished");
                    f.craft.discard();f.capture();f.stage=2;
                }
            }else if(f.stage==2&&f.ammunition()<f.ammunition&&f.enemy.getHealth()<f.health){
                h.assertTrue(f.age-f.at>=f.soldier.profile().aimTicks(),"cleared lane earns a fresh complete aim interval");f.pass();
            }
            h.assertTrue(f.age<400,"real gun completes initial fire, protected obstruction and reacquisition");
        });
    }

    @GameTest(template="blackwatch_arena",batch="soldierMultipartAllegianceLoF",timeoutTicks=340)
    public static void alliedHydraSkinBlocksTheGoalAndItsOwnHostilePartsRemainValidTargets(GameTestHelper h){
        run(h,f->{
            if(f.age==0){
                f.hydra=ModEntities.BLACKLIGHT_HYDRA.get().create(h.getLevel());
                f.hydra.setPos(h.absoluteVec(new Vec3(8,5,8)));f.hydra.setNoAi(true);f.hydra.setNoGravity(true);f.add(f.hydra);f.hydra.updateParts();
                // Source idle root/base channels are constant. Use an actual base
                // cube for a stable receiving lane, not the nonpickable parent box.
                var base=f.hydra.hurtRegions(0).stream().filter(r->r.bone().equals("base")).findFirst().orElseThrow();
                Vec3 center=base.boxes().get(0).center,from=center.add(0,0,-6),to=center.add(0,0,6);
                f.soldier.setNoGravity(true);f.soldier.setPos(from.add(0,-f.soldier.getEyeHeight()+.18,0));f.soldier.adoptFieldPost();
                f.enemy.setNoGravity(true);f.enemy.setPos(to.add(0,-f.enemy.getBbHeight()/2,0));
                f.team=h.getLevel().getScoreboard().addPlayerTeam("lof"+UUID.randomUUID().toString().substring(0,8));
                h.getLevel().getScoreboard().addPlayerToTeam(f.soldier.getScoreboardName(),f.team);
                h.getLevel().getScoreboard().addPlayerToTeam(f.hydra.getScoreboardName(),f.team);
                h.assertTrue(!f.hydra.isPickable()&&!f.soldier.validTarget(f.hydra),"root is nonpickable and friendship belongs to parent");
                f.blockerHealth=f.hydra.getHealth();f.capture();f.startGoal();
            }
            if(f.stage==0){
                var hit=f.pick(f.enemy);
                h.assertTrue(hit!=null&&hit.getEntity()!=f.hydra&&CombatTargeting.resolveTarget(hit.getEntity())==f.hydra,"real posed part, not coarse parent, is first receiver");
                var contact=HydraHurtContact.ray(f.hydra.hurtRegions(0),f.muzzle(),f.enemy.getBoundingBox().getCenter(),InfectedAttackStyle.PIERCE);
                h.assertTrue(contact!=null&&contact.point().distanceTo(hit.getLocation())<.002,"LoF picker retains the exact visible skin contact");
                h.assertTrue(f.ammunition()==f.ammunition&&f.hydra.getHealth()==f.blockerHealth&&f.enemy.getHealth()==f.health,"protected parent prevents real AI ammo debit through its part");
                if(f.age>=35){
                    h.getLevel().getScoreboard().removePlayerFromTeam(f.hydra.getScoreboardName(),f.team);
                    f.enemy.discard();f.soldier.setTarget(f.hydra);
                    // An enemy target's own part must normalize back to that
                    // target. Choose a real horizontal surface toward its center.
                    boolean placed=false;Vec3 end=f.hydra.getBoundingBox().getCenter();
                    for(Vec3 direction:new Vec3[]{new Vec3(0,0,1),new Vec3(0,0,-1),new Vec3(1,0,0),new Vec3(-1,0,0)}){
                        Vec3 from=end.subtract(direction.scale(8));
                        if(HydraHurtContact.ray(f.hydra.hurtRegions(0),from,end,InfectedAttackStyle.PIERCE)==null)continue;
                        f.soldier.setPos(from.add(0,-f.soldier.getEyeHeight()+.18,0));placed=true;break;
                    }
                    h.assertTrue(placed,"own target fixture has real skin on its aiming ray");
                    f.soldier.adoptFieldPost();f.ammunition=f.ammunition();f.at=f.age;f.stage=1;
                }
            }else if(f.ammunition()<f.ammunition&&f.hydra.getHealth()<f.blockerHealth){
                h.assertTrue(f.soldier.getTarget()==f.hydra,"own hostile multipart target is not rejected as an unrelated blocker");f.pass();
            }
            h.assertTrue(f.age<300,"real goal changes from protected part to lawful parent-target fire");
        });
    }
}
