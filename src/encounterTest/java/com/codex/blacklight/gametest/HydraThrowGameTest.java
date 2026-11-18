package com.codex.blacklight.gametest;
import com.codex.blacklight.entity.*;
import com.codex.blacklight.combat.*;
import com.codex.blacklight.registry.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class HydraThrowGameTest {
    private record Scene(BlacklightHydra h,net.minecraft.world.entity.animal.Cow v){}
    private static Scene scene(GameTestHelper h){
        for(int x=0;x<32;x++)for(int z=0;z<32;z++){h.setBlock(x,1,z,Blocks.STONE);for(int y=2;y<26;y++)h.setBlock(x,y,z,Blocks.AIR);}
        var a=ModEntities.BLACKLIGHT_HYDRA.get().create(h.getLevel());a.setBodySize(2);a.setPos(h.absoluteVec(new Vec3(14,2,8)));a.setNoAi(true);a.setNoGravity(true);h.getLevel().addFreshEntity(a);
        var v=h.spawn(EntityType.COW,new Vec3(14,2,29));v.setNoAi(true);v.setNoGravity(true);v.getAttribute(Attributes.MAX_HEALTH).setBaseValue(2000);v.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);v.setHealth(2000);return new Scene(a,v);
    }
    private static void start(GameTestHelper h,Scene s){s.h.setNoAi(false);h.assertTrue(s.h.beginThrow(s.v),"throw begins");}
    private static void end(GameTestHelper h,Scene s){s.h.discard();s.v.discard();h.succeed();}
    @GameTest(template="blackwatch_facility_arena",batch="hydra_throw_timing",timeoutTicks=180)
    public static void pickupHoldReleaseThenNativeHit(GameTestHelper h){
        var s=scene(h);h.runAfterDelay(5,()->start(h,s));
        h.runAfterDelay(57,()->h.assertTrue(s.h.combatCargo()==null&&s.v.getHealth()==2000,"no early prop or damage"));
        h.runAfterDelay(65,()->{var p=s.h.combatCargo();h.assertTrue(p!=null&&!p.released()&&p.position().distanceTo(s.h.combatGrip(0))<.01,"prop follows articulated grip");h.assertTrue(s.h.groundImpacts()==1,"pickup strikes actual support once");});
        h.runAfterDelay(95,()->h.assertTrue(s.h.action()==HydraThrow.HOLD&&!s.h.combatCargo().released(),"held telegraph"));
        h.runAfterDelay(129,()->h.assertTrue(s.h.combatCargo()!=null&&!s.h.combatCargo().released()&&s.v.getHealth()==2000,"source release not yet reached"));
        h.runAfterDelay(133,()->h.assertTrue(s.h.combatCargo()!=null&&s.h.combatCargo().released(),"prop released on forward swing"));
        h.runAfterDelay(170,()->{h.assertTrue(Math.abs(s.v.getHealth()-1980.4)<.02,"one projectile contact only; health="+s.v.getHealth()+" action="+s.h.action()+" recoil="+s.h.recoilStart());s.h.setNoAi(true);end(h,s);});
    }
    @GameTest(template="blackwatch_facility_arena",batch="hydra_throw_cancel",timeoutTicks=165)
    public static void interruptHeldPropLeavesNoGhostDamageOrItems(GameTestHelper h){
        var s=scene(h);h.runAfterDelay(5,()->start(h,s));
        h.runAfterDelay(90,()->{h.assertTrue(s.h.combatCargo()!=null,"held before cancel");s.h.cancelCombat();s.h.setNoAi(true);});
        h.runAfterDelay(155,()->{h.assertTrue(s.v.getHealth()==2000&&h.getLevel().getEntitiesOfClass(HydraDebris.class,s.h.getBoundingBox().inflate(40)).isEmpty(),"cancel removes held prop");h.assertTrue(h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,s.h.getBoundingBox().inflate(40)).isEmpty(),"no duplicated block items");end(h,s);});
    }
    @GameTest(template="blackwatch_facility_arena",batch="hydra_throw_collision",timeoutTicks=40)
    public static void volumeHitsWallBeforeVictim(GameTestHelper h){
        var s=scene(h);var base=s.h.position();s.v.setPos(base.add(0,2,12));
        for(int x=12;x<=16;x++)for(int y=2;y<=8;y++)h.setBlock(x,y,15,Blocks.STONE);
        var p=new HydraDebris(s.h,Blocks.STONE.defaultBlockState());h.getLevel().addFreshEntity(p);p.release(s.v.getBoundingBox().getCenter());p.setPos(base.add(0,3,5));p.setDeltaMovement(0,0,5);p.tick();
        h.assertTrue(p.isRemoved()&&p.impacts()==1&&s.v.getHealth()==2000,"swept cube stops on wall without damaging target");
        h.assertTrue(com.codex.blacklight.infection.InfectedAttackStyle.of(s.h,s.h.damageSources().thrown(p,s.h))==com.codex.blacklight.infection.InfectedAttackStyle.CRUSH,"heavy concrete uses blunt pressure feedback, not projectile piercing");end(h,s);
    }
    @GameTest(template="blackwatch_facility_arena",batch="hydra_throw_immunity",timeoutTicks=40)
    public static void immuneVictimRejectsProjectileDamage(GameTestHelper h){
        var s=scene(h);s.v.setInvulnerable(true);s.v.setPos(s.h.position().add(0,3,12));
        var p=new HydraDebris(s.h,Blocks.STONE.defaultBlockState());h.getLevel().addFreshEntity(p);p.release(s.v.getBoundingBox().getCenter());p.setPos(s.v.getBoundingBox().getCenter().add(0,0,-4));p.setDeltaMovement(0,0,5);p.tick();
        h.assertTrue(p.isRemoved()&&p.impacts()==1&&s.v.getHealth()==2000,"immune target gets no accepted damage");end(h,s);
    }
    @GameTest(template="blackwatch_facility_arena",batch="hydra_throw_dead",timeoutTicks=100)
    public static void removedCarrierCannotLeaveCargo(GameTestHelper h){
        var s=scene(h);h.runAfterDelay(5,()->start(h,s));h.runAfterDelay(90,()->{var p=s.h.combatCargo();h.assertTrue(p!=null,"has held cargo");s.h.discard();h.assertTrue(p.isRemoved(),"held cargo follows carrier removal");s.v.discard();h.succeed();});
    }
    @GameTest(template="blackwatch_facility_arena",batch="hydra_throw_air",timeoutTicks=100)
    public static void airbornePickupCannotInventGroundOrCargo(GameTestHelper h){
        var s=scene(h);s.h.setPos(s.h.position().add(0,12,0));s.v.setPos(s.v.position().add(0,12,0));
        h.runAfterDelay(5,()->start(h,s));h.runAfterDelay(85,()->{s.h.setNoAi(true);h.assertTrue(s.h.groundImpacts()==0&&s.h.combatCargo()==null&&s.v.getHealth()==2000,"no contacted support, no terrain wave or conjured cargo");end(h,s);});
    }
}
