package com.codex.blacklight.gametest;

import com.codex.blacklight.infection.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class ActualNativeVascularGameTest {
    @GameTest(template="wall_test",timeoutTicks=85,batch="actual_native_born_vascular")
    public static void actualBornBodyTransfersExistingNutritionWithoutLosingSourceControl(GameTestHelper h){run(h,"born_in_chaos_v1:decaying_zombie");}
    @GameTest(template="wall_test",timeoutTicks=85,batch="actual_native_spore_vascular")
    public static void actualSporeBodyTransfersExistingNutritionWithoutLosingSourceControl(GameTestHelper h){run(h,"spore:inf_human");}
    private static void run(GameTestHelper h,String id){
        var f=new NativeVascularArena(h);var key=ResourceLocation.parse(id);h.assertTrue(BuiltInRegistries.ENTITY_TYPE.containsKey(key),"actual optional source type is installed: "+id);
        // The real Born source burns in sunlight; shade is physical, not invulnerability.
        for(int x=2;x<=8;x++)for(int z=1;z<=5;z++)h.setBlock(new net.minecraft.core.BlockPos(x,5,z),net.minecraft.world.level.block.Blocks.STONE);
        var a=f.actor(BuiltInRegistries.ENTITY_TYPE.get(key));var nav=a.getNavigation();var brain=a.getBrain();var move=a.getMoveControl();var uuid=a.getUUID();
        a.getPersistentData().putString("NativeVascularForeign","retained");f.start(a);
        h.runAfterDelay(18,()->{int n=NativeTissueVascular.delivered(a);h.assertTrue(n>0&&f.colony.supply.reserve()==n&&NativeTissue.nutrition(a)+n==398,"actual foreign source pays its real conserved gift: "+id+" delivered="+n+" food="+NativeTissue.nutrition(a)+" health="+a.getHealth()+"/"+a.getMaxHealth()+" fire="+a.getRemainingFireTicks()+" pos="+a.position()+" velocity="+a.getDeltaMovement()+" target="+a.getTarget()+" visual="+NativeTissue.snapshot(a).getCompound("Vascular")+" saved="+NativeTissueVascular.saved(a));h.assertTrue(a.getNavigation()==nav&&a.getBrain()==brain&&a.getMoveControl()==move&&a.getUUID().equals(uuid)&&f.goals.get(a).ticks>0,"actual source controllers/identity and running source goal are retained: "+id);});
        h.runAfterDelay(46,()->{try{h.assertTrue(!NativeTissueVascular.active(a)&&NativeTissue.snapshot(a).getCompound("Vascular").isEmpty()&&a.getPersistentData().getString("NativeVascularForeign").equals("retained"),"actual optional source retracts and retains its own data: "+id);h.succeed();}finally{f.close();}});
    }
}
