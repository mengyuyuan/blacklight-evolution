package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.entity.*;
import com.codex.blacklight.registry.EncounterEntities;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class GoliathFrontSlamGameTest {
    private static final double START_Z=9;
    private static final double TARGET_Z=START_Z+4*GoliathPresentation.SCALE;
    private static EncounterMob actor(GameTestHelper h){
        for(int x=0;x<16;x++)for(int z=0;z<16;z++){h.setBlock(x,1,z,Blocks.STONE);for(int y=2;y<24;y++)h.setBlock(x,y,z,Blocks.AIR);}
        var mob=EncounterEntities.CREATURES.get(EncounterKind.GOLIATH).get().create(h.getLevel());
        mob.setPos(h.absoluteVec(new Vec3(8,2,START_Z)));mob.goalSelector.removeAllGoals(g->true);mob.targetSelector.removeAllGoals(g->true);mob.setPersistenceRequired();h.getLevel().addFreshEntity(mob);return mob;
    }
    private static net.minecraft.world.entity.animal.Pig target(GameTestHelper h){
        var pig=h.spawn(EntityType.PIG,new Vec3(8,2,TARGET_Z));pig.setNoAi(true);pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);pig.setHealth(100);pig.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);return pig;
    }
    @GameTest(template="blackwatch_arena",batch="goliath_front_slam",timeoutTicks=130)
    public static void sourceFrontSlamWaitsForContactAndCannotDoubleHit(GameTestHelper h){
        var mob=actor(h);var pig=target(h);float[] after={100};
        h.runAfterDelay(25,()->h.assertTrue(mob.begin(EncounterMove.HAMMER,pig),"source front slam begins"));
        h.runAfterDelay(62,()->h.assertTrue(pig.getHealth()==100,"windup before tick 38 cannot damage"));
        h.runAfterDelay(66,()->{after[0]=pig.getHealth();h.assertTrue(after[0]<100&&after[0]>75,"outer Hit deals tuned 400/750 damage once");});
        h.runAfterDelay(110,()->{h.assertTrue(pig.getHealth()==after[0],"recovery and overlapping spheres cannot duplicate the hit");h.assertTrue(mob.move()==EncounterMove.NONE,"source recovery ends");mob.discard();pig.discard();h.succeed();});
    }
    @GameTest(template="blackwatch_arena",batch="goliath_front_slam_cancel",timeoutTicks=110)
    public static void staggerInterruptsFrontSlamBeforeContact(GameTestHelper h){
        var mob=actor(h);var pig=target(h);
        h.runAfterDelay(25,()->h.assertTrue(mob.begin(EncounterMove.HAMMER,pig),"source front slam begins"));
        h.runAfterDelay(35,()->h.assertTrue(mob.hurtPart(0,h.getLevel().damageSources().generic(),100),"first armored hit builds poise"));
        h.runAfterDelay(50,()->{h.assertTrue(mob.hurtPart(1,h.getLevel().damageSources().generic(),100),"second armored hit staggers");h.assertTrue(!mob.broken(0)&&mob.goliathWindow()==GoliathWindow.HEAD&&mob.move()==EncounterMove.NONE,"exposure cancels source action");});
        h.runAfterDelay(100,()->{h.assertTrue(pig.getHealth()==100,"cancelled source impact cannot fire late");mob.discard();pig.discard();h.succeed();});
    }
    @GameTest(template="blackwatch_arena",batch="goliath_incoming_arrow",timeoutTicks=30)
    public static void nativeArrowDamagesReboundHandOutsideRoot(GameTestHelper h){
        var mob=actor(h);var arrow=EntityType.ARROW.create(h.getLevel());
        try{
            mob.setNoAi(true);mob.setNoGravity(true);mob.setPos(h.absoluteVec(new Vec3(8,8,8)));mob.setYRot(0);mob.yBodyRot=mob.yBodyRotO=0;mob.updateParts();
            var hand=mob.hurtRegions(0).stream().filter(r->r.bone().equals("left_hand")).findFirst().orElseThrow();
            var center=hand.boxes().get(0).center;
            var from=center.add(4*GoliathPresentation.SCALE,0,0);var to=center.add(-GoliathPresentation.SCALE,0,0);
            h.assertTrue(mob.getBoundingBox().clip(from,to).isEmpty(),"long hand is outside root column");
            arrow.setPos(from);arrow.setNoGravity(true);arrow.setDeltaMovement(to.subtract(from));arrow.setBaseDamage(2);h.getLevel().addFreshEntity(arrow);
            var hit=ProjectileUtil.getEntityHitResult(h.getLevel(),arrow,from,to,new AABB(from,to).inflate(.1),e->e instanceof GreenePart part&&part.getParent()==mob);
            h.assertTrue(hit!=null,"native projectile query finds the rebound skin proxy beyond root");
            float hp=mob.getHealth();arrow.tick();h.assertTrue(mob.getHealth()<hp,"real arrow tick forwards body damage to the boss");h.succeed();
        }finally{arrow.discard();mob.discard();}
    }
    @GameTest(template="blackwatch_arena",batch="goliath_proxy_lifecycle",timeoutTicks=30)
    public static void reboundProxiesKeepTwoWeakRegionsAndOneSavedOwner(GameTestHelper h){
        var mob=actor(h);EncounterMob copy=null;
        try{
            var state=new net.minecraft.nbt.CompoundTag();mob.saveWithoutId(state);
            state.putInt("GoliathWindow",GoliathWindow.KNEE.ordinal());state.putLong("EncounterExposedStart",h.getLevel().getGameTime());
            state.putLong("EncounterExposed",h.getLevel().getGameTime()+60);mob.load(state);mob.updateParts();
            h.assertTrue(mob.hurtPart(0,h.getLevel().damageSources().generic(),100),"vulnerable right knee breaks");
            var saved=new CompoundTag();mob.saveWithoutId(saved);copy=EncounterEntities.CREATURES.get(EncounterKind.GOLIATH).get().create(h.getLevel());copy.load(saved);copy.updateParts();
            h.assertTrue(copy.getParts().length==GoliathContact.regionBones().size(),"reload recreates deterministic skin regions");
            for(int i=0;i<copy.getParts().length;i++){
                var part=copy.getParts()[i];h.assertTrue(part instanceof GreenePart&&part.getParent()==copy,"every region belongs to one living Goliath");
                h.assertTrue(part.getId()==copy.getId()+i+1&&!part.shouldBeSaved(),"spawn IDs are contiguous and proxies do not persist separately");
            }
            h.assertTrue(!copy.getParts()[0].isPickable()&&copy.getParts()[1].isPickable(),"broken weak plate remains absent after load");
            var regions=copy.hurtRegions(0);
            for(int i=0;i<regions.size();i++){
                boolean present=!GoliathLockPart.severed(regions.get(i).bone(),1);
                h.assertTrue(copy.getParts()[i].isPickable()==present && regions.get(i).boxes().isEmpty()!=present,"detached mesh and all native proxies agree for "+regions.get(i).bone());
            }
            copy.discard();for(var part:copy.getParts())h.assertTrue(!part.isPickable(),"removed owner disables every proxy");h.succeed();
        }finally{if(copy!=null)copy.discard();mob.discard();}
    }
    @GameTest(template="blackwatch_arena",batch="goliath_stationary_hit_pose",timeoutTicks=30)
    public static void stationaryDamageDoesNotSwitchTheBossToAWalkPose(GameTestHelper h){
        var mob=actor(h);
        try{
            mob.setNoAi(true);
            mob.setNoGravity(true);
            var before=mob.hurtRegions(0).stream().filter(r->r.bone().equals("head")).findFirst().orElseThrow().bounds();
            h.assertTrue(mob.hurt(h.getLevel().damageSources().generic(),1),"a real hit is accepted");
            var after=mob.hurtRegions(0).stream().filter(r->r.bone().equals("head")).findFirst().orElseThrow().bounds();
            h.assertTrue(before.getCenter().distanceTo(after.getCenter())<.001,"stationary damage does not select a different gait pose");
            h.succeed();
        }finally{mob.discard();}
    }
}
