package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.entity.*;
import com.codex.blacklight.military.aircraft.HelicopterArmament;
import com.codex.blacklight.military.vehicle.*;
import com.codex.blacklight.registry.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.*;
import net.minecraftforge.gametest.*;

/** Real pilot intent -> actual gun ray -> multipart receiving surface. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class HelicopterManualBodyGameTest {
    private static Vec3 exposedPoint(BlackwatchHelicopter craft,LivingEntity target,boolean appendage){
        Vec3 from=craft.weaponPoint(HelicopterArmament.GUN);
        for(var region:AnimatedHurtBody.of(target).hurtRegions(0)){
            if(appendage&&!region.bone().startsWith("weak_"))continue;
            for(var box:region.boxes()){
                Vec3 to=box.center;
                var hit=ProjectileUtil.getEntityHitResult(craft.level(),craft,from,to,new AABB(from,to).inflate(.3),
                        e->e.getRootVehicle()!=craft&&e.isAlive()&&e.isPickable());
                if(hit!=null&&CombatTargeting.resolveTarget(hit.getEntity())==target
                        &&hit.getEntity()!=target&&(!appendage||hit.getEntity() instanceof GreenePart part&&part.slot<3))return to;
            }
        }
        return null;
    }
    private static void manualBody(GameTestHelper h,boolean greene,boolean covered){
        var f=new HelicopterPilotGameTest.Fixture(h,covered?299000:greene?298000:297000);
        var craft=f.craft(12);var player=HelicopterPilotGameTest.player(h,f,false);
        h.assertTrue(player.startRiding(craft,true),"real player boards pilot seat");
        LivingEntity target=greene?EncounterEntities.CREATURES.get(EncounterKind.ELIZABETH_GREENE).get().create(f.level)
                :ModEntities.BLACKLIGHT_HYDRA.get().create(f.level);
        // Explicit existing encounter fixture; fresh opening has its own regression.
        if(target instanceof EncounterMob standingMother){var standing=standingMother.greeneBattle().save();standing.putBoolean("OpeningPending",false);standingMother.greeneBattle().load(standing);}

        target.setPos(f.x,81,20);((Mob)target).setNoAi(true);((Mob)target).setPersistenceRequired();
        f.level.addFreshEntity(target);f.entities.add(target);
        if(covered)for(int x=-20;x<=20;x++)for(int y=81;y<=110;y++)
            f.level.setBlock(new net.minecraft.core.BlockPos(f.x+x,y,10),net.minecraft.world.level.block.Blocks.BEDROCK.defaultBlockState(),2);
        float initialHealth=target.getHealth();long[] sequence={0};int[] age={0};boolean[] aimedAtPart={false};
        h.onEachTick(()->{
            int now=++age[0];if(now<90||now>=150||now%2!=0)return;
            Vec3 point=exposedPoint(craft,target,greene);
            if(point==null)return;
            aimedAtPart[0]=true;Vec3 gun=craft.weaponPoint(HelicopterArmament.GUN);
            h.assertTrue(craft.pilot().accept(player,craft.pilot().lease(player.getUUID()),sequence[0]++,0,0,
                    VehicleAim.yaw(gun,point),VehicleAim.pitch(gun,point),VehicleControlAuthority.PRIMARY_FIRE),"pilot firing input accepted");
        });
        h.runAfterDelay(155,()->{
            try(f){
                h.assertTrue(aimedAtPart[0]&&craft.combat().gunShots()>0&&craft.combat().rounds()<240,"actual manual shots aimed at native multipart surfaces");
                if(covered)h.assertTrue(target.getHealth()==initialHealth&&((BlacklightHydra)target).incomingEffects()==0,
                        "first block still prevents player cannon damage to multipart bodies behind it");
                else if(greene){
                    var tag=new CompoundTag();target.saveWithoutId(tag);
                    float full=((EncounterMob)target).greeneSegmentMaximum();
                    h.assertTrue(tag.getFloat("EncounterArmor0")<full||tag.getFloat("EncounterArmor1")<full||tag.getFloat("EncounterArmor2")<full,
                            "player cannon damages an actual Greene appendage, not only a visual hit or core");
                }else h.assertTrue(target.getHealth()<initialHealth&&((BlacklightHydra)target).incomingEffects()>0,
                        "player cannon damages Hydra at its real animated contact");
            }
            h.succeed();
        });
    }
    @GameTest(template="helicopter_arena",batch="airManualBody",timeoutTicks=180)
    public static void playerCannonDamagesHydraThroughItsActualPart(GameTestHelper h){manualBody(h,false,false);}
    @GameTest(template="helicopter_arena",batch="airManualBody",timeoutTicks=180)
    public static void playerCannonDamagesGreenesIndependentAppendages(GameTestHelper h){manualBody(h,true,false);}
    @GameTest(template="helicopter_arena",batch="airManualBody",timeoutTicks=180)
    public static void coverStillBlocksPlayerCannonAgainstMultipartBodies(GameTestHelper h){manualBody(h,false,true);}
    @GameTest(template="helicopter_arena",batch="airManualBody",timeoutTicks=180)
    public static void playerCannonUsesActualTankHullInsteadOfItsOuterPickBox(GameTestHelper h){
        var f=new HelicopterPilotGameTest.Fixture(h,302000);var craft=f.craft(0);
        var player=HelicopterPilotGameTest.player(h,f,false);h.assertTrue(player.startRiding(craft,true),"real pilot boards");
        var tank=ModEntities.MILITARY_TANK.get().create(f.level);tank.setPos(f.x,81,18);f.level.addFreshEntity(tank);f.entities.add(tank);
        float health=tank.health();int[] age={0};long[] sequence={0};
        h.onEachTick(()->{
            int now=++age[0];if(now<100||now>=135||now%2!=0)return;
            Vec3 gun=craft.weaponPoint(HelicopterArmament.GUN),point=tank.position().add(0,tank.definition().height()*.45,0);
            h.assertTrue(craft.pilot().accept(player,craft.pilot().lease(player.getUUID()),sequence[0]++,0,0,
                    VehicleAim.yaw(gun,point),VehicleAim.pitch(gun,point),VehicleControlAuthority.PRIMARY_FIRE),"manual cannon input accepted");
        });
        h.runAfterDelay(145,()->{try(f){
            h.assertTrue(craft.combat().gunShots()>0&&craft.combat().rounds()<240,"actual player cannon fires finite ammunition");
            h.assertTrue(tank.health()<health,"real tank hull receives damage instead of a silent broad-box hit");
        }h.succeed();});
    }
}
