package com.codex.blacklight.gametest;
import com.codex.blacklight.firearms.*;
import com.codex.blacklight.network.FirearmReloadPayload;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class GunplayGameTest {
    @GameTest(template="blackwatch_arena",timeoutTicks=350,batch="gunplay")
    public static void reloadKeyRequestKeepsFiniteAmmoAndServerDeadline(GameTestHelper h){
        var level=h.getLevel();var at=new BlockPos(48008,100,48008);var forced=new ArrayList<ChunkPos>();
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++){var c=new ChunkPos((at.getX()>>4)+x,(at.getZ()>>4)+z);if(!level.getForcedChunks().contains(c.toLong())){forced.add(c);level.setChunkForced(c.x,c.z,true);}level.getChunk(c.x,c.z);}
        for(int x=-12;x<=12;x++)for(int z=-12;z<=12;z++)level.setBlock(at.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);
        var player=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"GunplayReloadQA"));player.setGameMode(GameType.SURVIVAL);player.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);level.addNewPlayer(player);
        var gun=FirearmsRegistry.weapon(WeaponCatalog.RIFLE.id());var item=gun.getDefaultInstance();FirearmItem.store(item,new AmmoState(10,0,0));player.setItemInHand(InteractionHand.MAIN_HAND,item);
        player.getInventory().setItem(2,new ItemStack(FirearmsRegistry.ammo("rifle_round"),25));
        var target=EntityType.ZOMBIE.create(level);target.moveTo(at.getX()+.5,at.getY(),at.getZ()+7.5,180,0);target.setNoAi(true);target.setPersistenceRequired();target.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,new ItemStack(net.minecraft.world.item.Items.NETHERITE_HELMET));target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);target.setHealth(1000);level.addFreshEntity(target);
        var loader=com.codex.blacklight.registry.ModEntities.BLACKWATCH_SOLDIER.get().create(level);loader.setNoAi(true);loader.setAutomaticUnitJoin(false);loader.moveTo(at.getX()-4,at.getY(),at.getZ());loader.initializeNativeLoadout("gunplay-finite-loader");loader.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,gun.getDefaultInstance());level.addFreshEntity(loader);
        long[] started={0};float[] health={0};int[] mask={0};
        // FakePlayer has no network-driven ServerPlayer.doTick; advance only its
        // vanilla item-cooldown clock so expiry matches the real-client fixture.
        h.onEachTick(()->player.getCooldowns().tick());
        h.startSequence().thenWaitUntil(()->h.assertTrue(level.isPositionEntityTicking(at),"actual player arena must tick"))
        .thenExecute(()->{
            h.assertTrue(!new FirearmReloadPayload(255).handle(player),"invalid hand cannot reload");
            h.assertTrue(new FirearmReloadPayload(0).handle(player),"R payload reloads actual held firearm on server");
            started[0]=level.getGameTime();h.assertTrue(FirearmItem.state(item,0).magazine()==30&&gun.reserveAmmo(player)==5,"exactly twenty real matching rounds transfer once");
            FirearmReloadSystem.begin(loader.getMainHandItem(),new AmmoState(FirearmItem.state(loader.getMainHandItem(),0).magazine(),0,started[0]+40),started[0]);
            h.assertTrue(!new FirearmReloadPayload(0).handle(player)&&gun.reserveAmmo(player)==5,"repeated packet cannot duplicate or debit ammunition twice");
            h.assertTrue(FirearmReloadSystem.started(item)==started[0]&&FirearmItem.state(item,0).readyAt()==started[0]+40,"explicit persisted reload clock uses actual weapon duration");
            health[0]=target.getHealth();player.setShiftKeyDown(false);gun.use(level,player,InteractionHand.MAIN_HAND);
            h.assertTrue(FirearmItem.state(item,0).magazine()==30&&target.getHealth()==health[0],"R cannot shoot early after bullets enter the magazine");
        }).thenIdle(18).thenExecute(()->{
            var data=loader.getMainHandItem().getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
            System.out.println("GUNPLAY_RELOAD_DIAGNOSTIC now="+level.getGameTime()+" born="+started[0]+" tick="+loader.tickCount+" data="+data+" gun="+loader.getMainHandItem());
            h.assertTrue((data.getInt(FirearmReloadSystem.CUES)&1)!=0,"actual server ticks play the extraction stage once");
            h.assertTrue(FirearmItem.state(item,0).magazine()==30,"reload presentation never consumes extra magazine rounds");
        }).thenIdle(25).thenExecute(()->{
            h.assertTrue(level.getGameTime()>=started[0]+40,"test passed the server deadline");
            var data=loader.getMainHandItem().getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
            h.assertTrue(data.getInt(FirearmReloadSystem.CUES)==7,"extraction insertion and bolt stages settle exactly once");
            player.setShiftKeyDown(true);gun.use(level,player,InteractionHand.MAIN_HAND);
            h.assertTrue(FirearmItem.state(item,0).magazine()==29&&gun.reserveAmmo(player)==5,"crouched right click now fires instead of reloading");
            h.assertTrue(target.getHealth()<health[0],"completed reload permits actual accepted rifle damage");health[0]=target.getHealth();
            level.setBlock(at.south(3).above(),Blocks.STONE.defaultBlockState(),3);
        }).thenIdle(6).thenExecute(()->{
            gun.use(level,player,InteractionHand.MAIN_HAND);h.assertTrue(target.getHealth()==health[0],"new gunplay feedback does not shoot damage through a real wall");
            player.getInventory().setItem(2,ItemStack.EMPTY);FirearmItem.store(item,new AmmoState(0,0,0));player.getCooldowns().removeCooldown(gun);
        }).thenIdle(5).thenExecute(()->{
            h.assertTrue(!new FirearmReloadPayload(0).handle(player)&&FirearmItem.state(item,0).magazine()==0,"empty inventory cannot mint ammo on R");
            target.discard();loader.discard();level.removePlayerImmediately(player,Entity.RemovalReason.DISCARDED);for(var c:forced)level.setChunkForced(c.x,c.z,false);
            System.out.println("GUNPLAY_SERVER_PASS finite inventory, R, duplicate requests, staged reload, deadline, crouch fire, wall and empty reserve");h.succeed();
        });
    }
}
