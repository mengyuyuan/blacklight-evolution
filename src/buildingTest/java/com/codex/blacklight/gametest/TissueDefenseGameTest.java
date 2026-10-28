package com.codex.blacklight.gametest;

import com.codex.blacklight.entity.BlacklightInfectedHost;
import com.codex.blacklight.infection.*;
import com.codex.blacklight.registry.ModEntities;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class TissueDefenseGameTest {
    private static BlacklightInfectedHost host(GameTestHelper h,TissueCore core){
        var host=ModEntities.INFECTED_HOST.get().create(h.getLevel());long id=host.getId();host.setUUID(new UUID(id,id^42));host.inherit(EntityType.COW.create(h.getLevel()));
        var t=new CompoundTag();host.saveWithoutId(t);t.getCompound("BlacklightHost").putString("NativeCore",core.key());t.putInt("BlacklightTissueNutrition",400);host.readAdditionalSaveData(t);
        host.advanceTissue(216000);host.setHealth(host.getMaxHealth());host.setNoAi(true);host.moveTo(h.absolutePos(new BlockPos(5,2,5)).getBottomCenter(),0,0);host.yBodyRot=0;host.setOnGround(true);h.getLevel().addFreshEntity(host);return host;
    }
    private static float shot(ServerLevel l,BlacklightInfectedHost host,boolean front,double height){
        host.invulnerableTime=0;host.setHealth(host.getMaxHealth());var arrow=EntityType.ARROW.create(l);arrow.setPos(host.getX(),host.getY()+host.getBbHeight()*height,host.getZ()+(front?2:-2));arrow.setDeltaMovement(new Vec3(0,0,front?-1:1));
        float old=host.getHealth();if(!host.hurt(l.damageSources().arrow(arrow,null),10))throw new AssertionError("real arrow damage was rejected");return old-host.getHealth();
    }
    @GameTest(template="wall_test",timeoutTicks=25,batch="defense_rear")
    public static void dermalBoneDefendsTheActualRearTorsoButLeavesTheFrontAndJointsOpen(GameTestHelper h){var x=host(h,TissueCore.OSTEODERM);try{
        float front=shot(h.getLevel(),x,true,.55),rear=shot(h.getLevel(),x,false,.55),joint=shot(h.getLevel(),x,false,.12);
        h.assertTrue(rear<front*.8F&&joint>rear*1.2F,"rear puncture reduction has a real physical direction and joint gap: "+front+" / "+rear+" / "+joint);
        h.assertTrue(x.defense().integrity(TissueCore.OSTEODERM)<1&&x.defenseVisual().getFloat("Bone")<1,"a real accepted hit wears and synchronizes its own material");
    }finally{x.discard();}h.succeed();}
    @GameTest(template="wall_test",timeoutTicks=25,batch="defense_front")
    public static void lamellarShieldDefendsTheActualFrontAndDoesNotCoverRearOrHead(GameTestHelper h){var x=host(h,TissueCore.NACRE);try{
        float rear=shot(h.getLevel(),x,false,.55),front=shot(h.getLevel(),x,true,.55),head=shot(h.getLevel(),x,true,.95);
        h.assertTrue(front<rear*.8F&&head>front*1.2F,"front shield leaves real head and rear openings: "+front+" / "+rear+" / "+head);
        h.assertTrue(x.defenseVisual().getString("HitCore").equals(TissueCore.NACRE.key()),"actual shell hit publishes its own recoil rather than a whole-body flash alone");
    }finally{x.discard();}h.succeed();}
    @GameTest(template="wall_test",timeoutTicks=25,batch="defense_rejection")
    public static void rejectedHitsAndMagicCannotConsumeOrActivatePhysicalArmour(GameTestHelper h){var x=host(h,TissueCore.NACRE);try{
        x.setInvulnerable(true);var arrow=EntityType.ARROW.create(h.getLevel());arrow.setPos(x.getX(),x.getY()+x.getBbHeight()*.55,x.getZ()+2);arrow.setDeltaMovement(0,0,-1);
        h.assertTrue(!x.hurt(h.getLevel().damageSources().arrow(arrow,null),10)&&x.defense().integrity(TissueCore.NACRE)==1,"rejected damage has no material commit");
        x.setInvulnerable(false);x.invulnerableTime=0;float old=x.getHealth();h.assertTrue(x.hurt(h.getLevel().damageSources().magic(),6)&&x.getHealth()<old,"magic still causes actual health loss");
        h.assertTrue(x.defense().integrity(TissueCore.NACRE)==1,"a shell cannot absorb a source with no physical collision");
    }finally{x.discard();}h.succeed();}
    @GameTest(template="wall_test",timeoutTicks=240,batch="defense_repair")
    public static void wornMaterialRepairsAfterQuietLoadedWorkAndDebitsActualHostFood(GameTestHelper h){var x=host(h,TissueCore.NACRE);shot(h.getLevel(),x,true,.55);float worn=x.defense().integrity(TissueCore.NACRE);x.setHealth(x.getMaxHealth());int food=x.tissueNutrition();
        h.runAfterDelay(120,()->h.assertTrue(x.defense().integrity(TissueCore.NACRE)==worn,"hit-free recovery delay cannot repair early"));
        h.runAfterDelay(220,()->{try{h.assertTrue(x.defense().integrity(TissueCore.NACRE)>worn&&x.tissueNutrition()<food,"actual host ticks repair material only by consuming real nutrition");}finally{x.discard();}h.succeed();});
    }
    @GameTest(template="wall_test",timeoutTicks=25,batch="defense_save")
    public static void savedWornArmourDoesNotRefillOnReloadAndOldDataStartsWhole(GameTestHelper h){var x=host(h,TissueCore.OSTEODERM);try{shot(h.getLevel(),x,false,.55);float worn=x.defense().integrity(TissueCore.OSTEODERM);int food=x.tissueNutrition();var t=new CompoundTag();x.saveWithoutId(t);x.readAdditionalSaveData(t);h.assertTrue(x.defense().integrity(TissueCore.OSTEODERM)==worn&&x.tissueNutrition()==food,"saving restores finite integrity and real food without offline repair");t.remove("BlacklightTissueDefense");x.readAdditionalSaveData(t);h.assertTrue(x.defense().integrity(TissueCore.OSTEODERM)==1,"old saves have a predictable explicit migration");}finally{x.discard();}h.succeed();}
    @GameTest(template="wall_test",timeoutTicks=25,batch="defense_depleted")
    public static void repeatedAcceptedPuncturesExposeTheUnderlyingHostInsteadOfGrantingPermanentArmour(GameTestHelper h){var x=host(h,TissueCore.NACRE);try{float first=shot(h.getLevel(),x,true,.55);for(int i=0;i<40;i++)shot(h.getLevel(),x,true,.55);float later=shot(h.getLevel(),x,true,.55);h.assertTrue(later>first*1.2F&&x.defense().integrity(TissueCore.NACRE)<.5F,"real repeated accepted damage exhausts the same material: "+first+" / "+later);}finally{x.discard();}h.succeed();}
    @GameTest(template="wall_test",timeoutTicks=40,batch="defense_flying_arrow")
    public static void aRealFlyingArrowHitsTheShieldAtItsActualHeightAndPublishesWear(GameTestHelper h){
        var x=host(h,TissueCore.NACRE);float old=x.getHealth();var arrow=EntityType.ARROW.create(h.getLevel());arrow.setPos(x.getX(),x.getY()+x.getBbHeight()*.55,x.getZ()+3);arrow.setNoGravity(true);arrow.setBaseDamage(8);arrow.setDeltaMovement(0,0,-.6);h.getLevel().addFreshEntity(arrow);
        h.runAfterDelay(25,()->{try{h.assertTrue(x.getHealth()<old&&x.defense().integrity(TissueCore.NACRE)<1&&x.defenseVisual().getString("HitCore").equals(TissueCore.NACRE.key()),"the engine-owned flying projectile reaches the actual shield and commits real damage and material wear");}finally{arrow.discard();x.discard();}h.succeed();});
    }
    @GameTest(template="wall_test",timeoutTicks=25,batch="defense_iframes")
    public static void changingArmourIntegrityCannotBypassTheOriginalSameHitCooldown(GameTestHelper h){var x=host(h,TissueCore.NACRE);try{
        shot(h.getLevel(),x,true,.55);float hp=x.getHealth(),integrity=x.defense().integrity(TissueCore.NACRE);var arrow=EntityType.ARROW.create(h.getLevel());arrow.setPos(x.getX(),x.getY()+x.getBbHeight()*.55,x.getZ()+2);arrow.setDeltaMovement(0,0,-1);
        for(int i=0;i<10;i++)h.assertTrue(!x.hurt(h.getLevel().damageSources().arrow(arrow,null),10),"identical hits inside the original cooldown remain rejected even though the shell just wore");
        h.assertTrue(x.getHealth()==hp&&x.defense().integrity(TissueCore.NACRE)==integrity,"rejected cooldown hits neither chip health nor consume armour");
    }finally{x.discard();}h.succeed();}
    @GameTest(template="wall_test",timeoutTicks=25,batch="defense_absorption")
    public static void acceptedPhysicalHitsStillWearMaterialWhenAbsorptionCatchesTheRemainingDamage(GameTestHelper h){var x=host(h,TissueCore.NACRE);try{
        x.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(100);x.setAbsorptionAmount(40);float health=x.getHealth();shot(h.getLevel(),x,true,.55);
        h.assertTrue(x.getHealth()==health&&x.getAbsorptionAmount()<40&&x.defense().integrity(TissueCore.NACRE)<1,"real absorbed damage still commits the shield's blocked material and cannot make it free while extra hearts remain");
    }finally{x.discard();}h.succeed();}
}
