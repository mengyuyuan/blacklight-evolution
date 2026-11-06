package com.codex.blacklight.gametest;

import com.codex.blacklight.config.InfectionEcologyConfig;
import com.codex.blacklight.infection.*;
import com.codex.blacklight.registry.ModBlocks;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class NativeVascularGameTest {
    @GameTest(template="wall_test",timeoutTicks=85,batch="native_vascular_transfer")
    public static void oneRealGiftConservesNutritionAndKeepsTheSourceGoalRunning(GameTestHelper h){
        var f=new NativeVascularArena(h);var a=f.actor();var nav=a.getNavigation();var brain=a.getBrain();var move=a.getMoveControl();f.start(a);
        h.runAfterDelay(9,()->h.assertTrue(f.colony.supply.reserve()==0&&NativeTissueVascular.active(a),"extension does not transfer before the actual contact frame"));
        h.runAfterDelay(18,()->{int n=NativeTissueVascular.delivered(a);h.assertTrue(n>0&&f.colony.supply.reserve()==n&&NativeTissue.nutrition(a)+n==398,"recipient income equals actual donor debit plus paid opening; n="+n+" food="+NativeTissue.nutrition(a));h.assertTrue(a.getNavigation()==nav&&a.getBrain()==brain&&a.getMoveControl()==move&&f.goals.get(a).ticks>0,"original source controller and its running goal keep executing during delivery");h.assertTrue(NativeTissue.inheritance(a).meals()==0&&f.colony.supply.queued()==0,"connection cannot invent food portions or capture credits");});
        h.runAfterDelay(46,()->{try{h.assertTrue(!NativeTissueVascular.active(a)&&NativeTissue.snapshot(a).getCompound("Vascular").isEmpty()&&f.colony.supply.reserve()==NativeTissueVascular.delivered(a),"full retraction ends without duplicate credit");h.succeed();}finally{f.close();}});
    }
    @GameTest(template="wall_test",timeoutTicks=35,batch="native_vascular_source_motion")
    public static void originalMovementIsNeverZeroedAndCancelsTheContactOnlyConnection(GameTestHelper h){
        try(var f=new NativeVascularArena(h)){var a=f.actor();f.start(a);var velocity=new Vec3(.3,-.08,.1);a.setDeltaMovement(velocity);NativeTissueVascular.tick(a);h.assertTrue(a.getDeltaMovement().equals(velocity)&&!NativeTissueVascular.active(a)&&f.colony.supply.reserve()==0,"source motion is unchanged, the duct retracts, no remote gift");h.assertTrue(NativeTissue.snapshot(a).getCompound("Vascular").contains("CancelledAt"),"cancel synchronizes a finite closing motion");}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=50,batch="native_vascular_accepted_hit")
    public static void actualHealthDamageBreaksTheDuctWithoutTransportInvulnerability(GameTestHelper h){
        var f=new NativeVascularArena(h);var a=f.actor();f.start(a);h.runAfterDelay(6,()->{float hp=a.getHealth();a.invulnerableTime=0;h.assertTrue(a.hurt(a.damageSources().generic(),4)&&a.getHealth()<hp,"actual native damage hook remains effective");});
        h.runAfterDelay(20,()->{try{h.assertTrue(!NativeTissueVascular.active(a)&&f.colony.supply.reserve()==0&&NativeTissueVascular.saved(a).getInt("Cooldown")>0,"accepted hit interrupts before contact with cooldown and no credited stock");h.succeed();}finally{f.close();}});
    }
    @GameTest(template="wall_test",timeoutTicks=50,batch="native_vascular_wall")
    public static void aNewWallCutsTheActualLoadedPath(GameTestHelper h){
        var f=new NativeVascularArena(h);var a=f.actor();f.start(a);h.runAfterDelay(6,()->{for(int x=2;x<=8;x++)for(int y=2;y<=6;y++)h.setBlock(new BlockPos(x,y,4),Blocks.GLASS);});
        h.runAfterDelay(22,()->{try{h.assertTrue(!NativeTissueVascular.active(a)&&NativeTissue.snapshot(a).getCompound("Vascular").isEmpty()&&f.colony.supply.reserve()==0,"a solid obstruction retracts the duct and cannot credit behind a wall");h.succeed();}finally{f.close();}});
    }
    @GameTest(template="wall_test",timeoutTicks=50,batch="native_vascular_core")
    public static void removingTheCoreCannotCreditAStaleColony(GameTestHelper h){
        var f=new NativeVascularArena(h);var a=f.actor();f.start(a);h.runAfterDelay(6,()->f.level.removeBlock(f.origin,false));
        h.runAfterDelay(22,()->{try{h.assertTrue(!NativeTissueVascular.active(a)&&NativeTissue.snapshot(a).getCompound("Vascular").isEmpty()&&f.colony.supply.reserve()==0,"destroyed actual destination is invalid before contact");h.succeed();}finally{f.close();}});
    }
    @GameTest(template="wall_test",timeoutTicks=80,batch="native_vascular_reload")
    public static void actualNativeReloadKeepsDebitAndCannotResumeACommittedGift(GameTestHelper h){
        var f=new NativeVascularArena(h);var a=f.actor();f.start(a);int[] received={0};
        h.runAfterDelay(18,()->{received[0]=f.colony.supply.reserve();h.assertTrue(received[0]>0,"real gift committed before save");int food=NativeTissue.nutrition(a);var nbt=new CompoundTag();a.saveWithoutId(nbt);a.load(nbt);NativeTissueVascular.tick(a);h.assertTrue(!NativeTissueVascular.active(a)&&NativeTissue.nutrition(a)==food&&NativeTissueVascular.saved(a).getInt("Cooldown")>=150,"source load keeps debit/cooldown while dropping live target state");});
        h.runAfterDelay(46,()->{try{h.assertTrue(f.colony.supply.reserve()==received[0]&&NativeTissue.snapshot(a).getCompound("Vascular").isEmpty(),"loading cannot replay a paid gift");h.succeed();}finally{f.close();}});
    }
    @GameTest(template="wall_test",timeoutTicks=35,batch="native_vascular_permissions")
    public static void noAiNoOutbreakNoGriefFullStoreAndOwnReserveNeverChargeAnOpening(GameTestHelper h){
        try(var f=new NativeVascularArena(h)){var a=f.actor();a.setNoAi(true);h.assertTrue(!NativeTissueVascular.startAt(a,f.origin),"NoAI does not donate");a.setNoAi(false);
            f.level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false,f.level.getServer());h.assertTrue(!NativeTissueVascular.startAt(a,f.origin),"mobGriefing blocks the operation");f.level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true,f.level.getServer());
            f.level.getServer().overworld().getDataStorage().set("blacklight_outbreak",new OutbreakData());h.assertTrue(!NativeTissueVascular.startAt(a,f.origin),"inactive outbreak cannot transport");var active=new OutbreakData();active.start(f.level.getServer().overworld().getGameTime());f.level.getServer().overworld().getDataStorage().set("blacklight_outbreak",active);
            f.colony.supply.receive(TissueMetabolism.MAX_RESERVE);h.assertTrue(!NativeTissueVascular.startAt(a,f.origin)&&NativeTissue.nutrition(a)==400,"full destination cannot charge");f.colony.supply.pay(TissueMetabolism.MAX_RESERVE);NativeTissue.spend(a,304);h.assertTrue(!NativeTissueVascular.startAt(a,f.origin)&&NativeTissue.nutrition(a)==96,"own protected reserve cannot be donated");}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=35,batch="native_vascular_budget")
    public static void nativeCarriersShareTheRealFourSearchBudgetWithoutAddingNavigationGoals(GameTestHelper h){
        try(var f=new NativeVascularArena(h)){int started=0;for(int i=0;i<6;i++){var a=f.actor();a.setPos(a.position().add((i%3)-1,0,i/3));int count=a.goalSelector.getAvailableGoals().size();NativeTissueVascular.tick(a);if(NativeTissueVascular.active(a))started++;h.assertTrue(a.goalSelector.getAvailableGoals().size()==count,"duct does not install a movement goal");}h.assertTrue(started>0&&started<=4,"shared budget bounds native carrier searches: "+started);}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=35,batch="native_vascular_cure")
    public static void cureAndDuplicateCallbacksCannotRetainAConnectionOrAccelerateContact(GameTestHelper h){
        try(var f=new NativeVascularArena(h)){var a=f.actor();f.start(a);for(int i=0;i<20;i++)NativeTissueVascular.tick(a);h.assertTrue(NativeTissueVascular.active(a)&&f.colony.supply.reserve()==0,"same actual entity tick cannot advance twenty contact frames");a.removeEffect(com.codex.blacklight.registry.ModEffects.BLACKLIGHT_INFECTION.get());NativeInfection.clear(a);h.assertTrue(!NativeInfection.active(a)&&!NativeTissueVascular.active(a)&&NativeTissue.snapshot(a).isEmpty(),"cure clears only owned link state and visual");}h.succeed();
    }
}
