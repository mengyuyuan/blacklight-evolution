package com.codex.blacklight.gametest.vehicle;

import com.codex.blacklight.entity.MilitaryVehicle;
import com.codex.blacklight.firearms.VehicleOrdnanceProjectile;
import com.codex.blacklight.registry.ModEntities;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.gametest.*;

/** Real projectile tick and real impact event; no direct hull-hurt or canHitEntity-only assertions. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false) @EventBusSubscriber(modid="blacklight")
public final class VehicleProjectileHullGameTest {
    private static final Set<UUID> WATCH=new HashSet<>(),CANCEL=new HashSet<>();
    private static final Map<UUID,HitResult> LAST=new HashMap<>();
    private static final Map<UUID,Integer> COUNT=new HashMap<>();
    @SubscribeEvent public static void impact(ProjectileImpactEvent event){
        UUID id=event.getProjectile().getUUID();if(!WATCH.contains(id))return;
        LAST.put(id,event.getRayTraceResult());COUNT.merge(id,1,Integer::sum);if(CANCEL.contains(id))event.setCanceled(true);
    }
    private record Scene(ServerPlayer actor,MilitaryVehicle origin,MilitaryVehicle target){}
    private static Scene scene(GameTestHelper h,MilitaryVehicleGameTest.Fixture f){
        var actor=MilitaryVehicleGameTest.mockPlayer(h);actor.setGameMode(GameType.SURVIVAL);f.entities.add(actor);
        var origin=ModEntities.MILITARY_APC.get().create(f.level);origin.setPos(f.x-7,81,-6);f.level.addFreshEntity(origin);f.entities.add(origin);h.assertTrue(actor.startRiding(origin),"actual actor occupies the origin vehicle");
        var target=ModEntities.MILITARY_TANK.get().create(f.level);target.setPos(f.x+.5,81,5);target.setYRot(45);f.level.addFreshEntity(target);f.entities.add(target);
        h.assertTrue(f.level.getEntitiesOfClass(MilitaryVehicle.class,target.getBoundingBox().inflate(.01)).contains(target),"fixture target is visible in an already ticking loaded entity section");
        return new Scene(actor,origin,target);
    }
    private static VehicleOrdnanceProjectile shot(MilitaryVehicleGameTest.Fixture f,Scene scene,double speed){
        var projectile=new VehicleOrdnanceProjectile(f.level,scene.actor,scene.origin,"tank_ap");projectile.setPos(f.x+3.5,82,0);projectile.setDeltaMovement(0,0,speed);f.level.addFreshEntity(projectile);f.entities.add(projectile);WATCH.add(projectile.getUUID());return projectile;
    }
    private static void clear(VehicleOrdnanceProjectile projectile){UUID id=projectile.getUUID();WATCH.remove(id);CANCEL.remove(id);LAST.remove(id);COUNT.remove(id);}
    @GameTest(template="blackwatch_arena",batch="vehicle6_projectile_hull",timeoutTicks=100)
    public static void actualShellCrossesRotatedEnvelopeEmptyCornerWithoutHullImpact(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,310000);VehicleTestReadiness.run(h,f,()->{
            var scene=scene(h,f);var projectile=shot(f,scene,3.2);try{projectile.tick();
                h.assertTrue(scene.target.health()==scene.target.maxHealth()&&!projectile.isRemoved()&&Math.abs(projectile.getZ()-3.2)<.001,"actual AP shell traverses the empty rotated-envelope corner and keeps its native flight");
                h.assertTrue(COUNT.getOrDefault(projectile.getUUID(),0)==0,"an empty enclosing AABB must not dispatch a fake mechanical impact event");
            }finally{clear(projectile);}
        });
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6_projectile_hull",timeoutTicks=100)
    public static void actualWallBeforeTheRealRotatedHullWinsOverItsEmptyEnvelope(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,311000);VehicleTestReadiness.run(h,f,()->{
            var scene=scene(h,f);for(int y=81;y<=84;y++)f.level.setBlock(new BlockPos(f.x+3,y,2),Blocks.IRON_BLOCK.defaultBlockState(),2);
            var projectile=shot(f,scene,3.2);try{projectile.tick();var impact=LAST.get(projectile.getUUID());
                h.assertTrue(impact instanceof BlockHitResult&&Math.abs(impact.getLocation().z-2)<.001,"real wall before the true OBB receives the only first-impact event despite a nearer empty AABB corner");
                h.assertTrue(scene.target.health()==scene.target.maxHealth(),"AP radius one cannot splash a true hull several blocks beyond the wall from an empty envelope corner");
            }finally{clear(projectile);}
        });
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6_projectile_hull",timeoutTicks=100)
    public static void actualHullSurfacePrecedesLivingBehindItAndReportsItsRealContact(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,312000);VehicleTestReadiness.run(h,f,()->{
            var scene=scene(h,f);var behind=EntityType.COW.create(f.level);behind.setNoAi(true);behind.setPos(f.x+3.5,81,10);f.level.addFreshEntity(behind);f.entities.add(behind);
            var projectile=shot(f,scene,3.2);try{projectile.tick();if(!projectile.isRemoved())projectile.tick();var impact=LAST.get(projectile.getUUID());
                h.assertTrue(impact instanceof EntityHitResult hit&&hit.getEntity()==scene.target&&Math.abs(impact.getLocation().x-(f.x+3.5))<.001&&impact.getLocation().z>4.5&&impact.getLocation().z<7,"actual OBB first hit reports the real point on the projectile trajectory, not parent position or an envelope corner");
                h.assertTrue(Math.abs(scene.target.maxHealth()-scene.target.health()-36)<.001&&behind.getHealth()==behind.getMaxHealth()&&COUNT.getOrDefault(projectile.getUUID(),0)==1,"actual AP damages its first hull once and cannot also hit the living actor behind it");
            }finally{clear(projectile);}
        });
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6_projectile_hull",timeoutTicks=100)
    public static void canceledRealHullImpactPreservesNativeFlightAndOneCorrectEvent(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,313000);VehicleTestReadiness.run(h,f,()->{
            var scene=scene(h,f);for(int y=81;y<=84;y++)f.level.setBlock(new BlockPos(f.x+3,y,6),Blocks.IRON_BLOCK.defaultBlockState(),2);
            var projectile=shot(f,scene,3.2);CANCEL.add(projectile.getUUID());
            try{f.level.tickNonPassenger(projectile);f.level.tickNonPassenger(projectile);var impact=LAST.get(projectile.getUUID());
                h.assertTrue(impact instanceof EntityHitResult&&Math.abs(impact.getLocation().x-(f.x+3.5))<.001&&impact.getLocation().z>4.5,"cancelable native impact hook sees the actual first OBB contact");
                h.assertTrue(!projectile.isRemoved()&&scene.target.health()==scene.target.maxHealth()&&Math.abs(projectile.getZ()-(3.2+3.2*.99))<.001&&Math.abs(projectile.getDeltaMovement().z-3.2*.99*.99)<.001&&projectile.tickCount==2&&COUNT.getOrDefault(projectile.getUUID(),0)==1,
                        "cancellation native-flight diagnostics: removed="+projectile.isRemoved()+" hull="+scene.target.health()+"/"+scene.target.maxHealth()+" pos="+projectile.position()+" expectedZ="+(3.2+3.2*.99)+" velocity="+projectile.getDeltaMovement()+" expectedVz="+(3.2*.99*.99)+" tickCount="+projectile.tickCount+" expectedTickCount=2 impacts="+COUNT.getOrDefault(projectile.getUUID(),0)+" noGravity="+projectile.isNoGravity()+" ray="+impact.getType()+" hitPoint="+impact.getLocation());
            }finally{clear(projectile);}
        });
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6_projectile_hull",timeoutTicks=100)
    public static void actualApUpperAndLowerZombieContactsBothReceiveOneDirectHit(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,314000);VehicleTestReadiness.run(h,f,()->{
            var scene=scene(h,f);double[] damage=new double[2];
            for(int i=0;i<2;i++){
                var zombie=EntityType.ZOMBIE.create(f.level);zombie.setNoAi(true);zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(200);zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR).setBaseValue(0);zombie.setHealth(200);zombie.setPos(f.x-6-i*2,81,6);f.level.addFreshEntity(zombie);f.entities.add(zombie);
                var projectile=new VehicleOrdnanceProjectile(f.level,scene.actor,scene.origin,"tank_ap");projectile.setPos(zombie.getX(),i==0?82.65:81.35,0);projectile.setDeltaMovement(0,0,3.2);f.level.addFreshEntity(projectile);f.entities.add(projectile);WATCH.add(projectile.getUUID());
                try{float before=zombie.getHealth();for(int tick=0;tick<3&&!projectile.isRemoved();tick++)projectile.tick();var impact=LAST.get(projectile.getUUID());
                    h.assertTrue(impact instanceof EntityHitResult hit&&hit.getEntity()==zombie,"actual stationary zombie receives the original projectile impact at both body heights");damage[i]=before-zombie.getHealth();
                }finally{clear(projectile);}
            }
            h.assertTrue(Math.abs(damage[0]-36)<.001&&Math.abs(damage[1]-36)<.001,"actual AP upper and lower direct contacts each receive the same original36 once, independent of foot-point splash radius");
        });
    }
    private VehicleProjectileHullGameTest(){}
}
