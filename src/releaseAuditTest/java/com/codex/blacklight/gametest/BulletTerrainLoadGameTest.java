package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.BulletdiveTerrain;
import com.codex.blacklight.combat.BulletdiveBreakEffects;
import com.codex.blacklight.power.*;
import com.codex.blacklight.registry.ModAttachments;
import com.mojang.authlib.GameProfile;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class BulletTerrainLoadGameTest {
    @GameTest(template="wall_test",timeoutTicks=160,batch="bullet_load")
    public static void extremeHeightPausesAtLootPressureThenResumes(GameTestHelper h){
        var level=h.getLevel();
        var p=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"load-check"));
        var impact=h.absoluteVec(new Vec3(3.5,5,3.5));p.setPos(impact);
        var state=p.getData(ModAttachments.PLAYER_STATE);state.infect();state.setPrototypeMode(true);
        state.addEvolutionPoints(10000);state.buyMovementUpgrade(MovementUpgrade.GLIDE_1);state.buyCombatSkill(CombatSkill.BULLETDIVE_DROP);
        var allowed=new HashSet<net.minecraft.core.BlockPos>();
        for(int x=1;x<=5;x++)for(int y=1;y<=4;y++)for(int z=1;z<=5;z++){
            h.setBlock(x,y,z,Blocks.STONE);allowed.add(h.absolutePos(new net.minecraft.core.BlockPos(x,y,z)));
        }
        Consumer<BlockEvent.BreakEvent> protection=event->{if(event.getPlayer()==p&&!allowed.contains(event.getPos()))event.setCanceled(true);};
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH,false,BlockEvent.BreakEvent.class,protection);
        var pressure=new ArrayList<ItemEntity>();
        for(int i=0;i<768;i++){
            var item=new ItemEntity(level,impact.x,impact.y+2,impact.z,new ItemStack(Items.STONE_SWORD));
            item.setNoGravity(true);item.setDeltaMovement(Vec3.ZERO);item.setPickUpDelay(32767);
            level.addFreshEntity(item);pressure.add(item);
        }
        long session=-7654321;
        var plan=BulletdiveTerrain.begin(p,session,impact,66666);
        h.assertTrue(plan.radius()>180&&plan.energy()>500000,"height energy and radius remain uncapped");
        h.runAtTickTime(8,()->{
            var snapshot=BulletdiveTerrain.snapshot(p,session);
            h.assertTrue(!snapshot.complete()&&snapshot.visited()==0&&snapshot.energyRemaining()==plan.energy(),"loot pressure must pause without consuming impact energy");
            pressure.forEach(ItemEntity::discard);
        });
        h.runAtTickTime(60,()->{
            var snapshot=BulletdiveTerrain.snapshot(p,session);
            BulletdiveTerrain.cancel(p,session);NeoForge.EVENT_BUS.unregister(protection);
            h.assertTrue(snapshot.destroyed()>0,"pending terrain must resume after pressure falls");
            h.assertTrue(snapshot.peakBreaksPerTick()<=512,"global destruction slice enforced");
            h.succeed();
        });
    }

    @GameTest(template="wall_test",timeoutTicks=20,batch="bullet_merge")
    public static void distantInterleavedDropsRetainCountsAndNearbyMerge(GameTestHelper h){
        var level=h.getLevel();var p=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"merge-check"));
        var effects=new BulletdiveBreakEffects();
        var a=h.absolutePos(new net.minecraft.core.BlockPos(2,4,2));
        var b=h.absolutePos(new net.minecraft.core.BlockPos(7,4,7));
        var before=new HashSet<UUID>();for(var e:level.getAllEntities())before.add(e.getUUID());
        for(int i=0;i<320;i++){
            var at=(i&1)==0?a:b;level.setBlock(at,Blocks.STONE.defaultBlockState(),3);
            h.assertTrue(effects.destroy(level,at,p),"ordinary block destruction succeeds");
        }
        int entities=0,count=0;
        for(var e:level.getAllEntities())if(e instanceof ItemEntity item&&!before.contains(e.getUUID())&&item.isAlive()){
            if(item.getItem().is(Items.COBBLESTONE)){entities++;count+=item.getItem().getCount();}
        }
        effects.clear();
        h.assertTrue(count==320,"all 320 normal drops must be retained, actual="+count);
        h.assertTrue(entities<=6,"nearby drops must merge into stacks without crossing distant sites, entities="+entities);
        h.succeed();
    }
}
