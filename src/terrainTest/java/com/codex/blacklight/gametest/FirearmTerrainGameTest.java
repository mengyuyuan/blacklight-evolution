package com.codex.blacklight.gametest;

import com.codex.blacklight.firearms.*;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class FirearmTerrainGameTest {
    private static void arena(GameTestHelper h){for(int x=0;x<16;x++)for(int z=0;z<16;z++){
        h.setBlock(x,1,z,Blocks.STONE);for(int y=2;y<8;y++)h.setBlock(x,y,z,Blocks.AIR);
    }}
    private static ServerPlayer player(GameTestHelper h){
        var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"terrain-test"));
        p.setGameMode(GameType.CREATIVE);p.setPos(h.absoluteVec(new Vec3(3.5,2,3.5)));return p;
    }
    private static void shot(GameTestHelper h,ServerPlayer p,WeaponDefinition definition,BlockPos relative){
        var target=Vec3.atCenterOf(h.absolutePos(relative));FirearmsCombat.fire(p,definition,target.subtract(p.getEyePosition()).normalize(),1);
    }
    @GameTest(template="blackwatch_arena",batch="terrain4",timeoutTicks=60)
    public static void realRifleBulletBreaksGlass(GameTestHelper h){
        arena(h);var p=player(h);var at=new BlockPos(3,3,8);h.setBlock(at,Blocks.GLASS);
        shot(h,p,WeaponCatalog.RIFLE,at);h.assertTrue(h.getBlockState(at).isAir(),"real hitscan rifle must break the hit glass block");h.succeed();
    }
    @GameTest(template="blackwatch_arena",batch="terrain4",timeoutTicks=60)
    public static void realWoodenCoverNeedsAccumulatedHits(GameTestHelper h){
        arena(h);var p=player(h);var at=new BlockPos(3,3,8);h.setBlock(at,Blocks.OAK_PLANKS);
        for(int i=0;i<3;i++)shot(h,p,WeaponCatalog.RIFLE,at);
        h.assertTrue(h.getBlockState(at).is(Blocks.OAK_PLANKS),"three rifle shots do not instantly erase wooden cover");
        shot(h,p,WeaponCatalog.RIFLE,at);h.assertTrue(h.getBlockState(at).isAir(),"four actual impacts accumulate and break wood");h.succeed();
    }
    @GameTest(template="blackwatch_arena",batch="terrain4",timeoutTicks=60)
    public static void aRealPlayerReplacementDoesNotInheritTheOldBlocksDamage(GameTestHelper h){
        arena(h);var p=player(h);var at=new BlockPos(3,3,8);h.setBlock(at.below(),Blocks.STONE);h.setBlock(at,Blocks.OAK_PLANKS);
        for(int i=0;i<3;i++)shot(h,p,WeaponCatalog.RIFLE,at);
        h.assertTrue(p.gameMode.destroyBlock(h.absolutePos(at))&&h.getBlockState(at).isAir(),"actual creative player removes the damaged wood");
        var item=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.OAK_PLANKS);p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,item);
        var against=h.absolutePos(at.below());
        p.gameMode.useItemOn(p,h.getLevel(),item,net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(against).add(0,.5,0),net.minecraft.core.Direction.UP,against,false));
        h.assertTrue(h.getBlockState(at).is(Blocks.OAK_PLANKS),"the real block-item placement installs an identical replacement");
        shot(h,p,WeaponCatalog.RIFLE,at);h.assertTrue(h.getBlockState(at).is(Blocks.OAK_PLANKS),"new wood must not inherit the removed blocks previous three hits");h.succeed();
    }
    @GameTest(template="blackwatch_arena",batch="terrain4",timeoutTicks=60)
    public static void anEntityHitDoesNotAlsoBreakTheWallBehindIt(GameTestHelper h){
        arena(h);var p=player(h);var at=new BlockPos(3,3,9);h.setBlock(at,Blocks.GLASS);
        var target=net.minecraft.world.entity.EntityType.ZOMBIE.create(h.getLevel());target.setNoAi(true);target.setPos(h.absoluteVec(new Vec3(3.5,2,6.5)));h.getLevel().addFreshEntity(target);
        try{float before=target.getHealth();shot(h,p,WeaponCatalog.RIFLE,at);h.assertTrue(target.getHealth()<before,"real bullet hits the living target first");h.assertTrue(h.getBlockState(at).is(Blocks.GLASS),"one bullet does not penetrate a living target into rear cover");h.succeed();}finally{target.discard();}
    }
    @GameTest(template="blackwatch_arena",batch="terrain4",timeoutTicks=60)
    public static void protectedBlocksAndContainerInventorySurvive(GameTestHelper h){
        arena(h);var p=player(h);var at=new BlockPos(3,3,8);h.setBlock(at,Blocks.BEDROCK);
        for(int i=0;i<40;i++)shot(h,p,WeaponCatalog.MACHINE_GUN,at);
        h.assertTrue(h.getBlockState(at).is(Blocks.BEDROCK),"unbreakable blocks retain protection");
        h.setBlock(at,Blocks.CHEST);var chest=(net.minecraft.world.level.block.entity.ChestBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(at));
        chest.setItem(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND,3));
        for(int i=0;i<8;i++)shot(h,p,WeaponCatalog.RIFLE,at);
        FirearmBlockDamage.blast(h.getLevel(),p,WeaponCatalog.ROCKET,Vec3.atCenterOf(h.absolutePos(at)));
        h.assertTrue(h.getBlockState(at).is(Blocks.CHEST)&&chest.getItem(0).getCount()==3,"default protection preserves actual inventory blocks and their items");h.succeed();
    }
    @GameTest(template="blackwatch_arena",batch="terrain4",timeoutTicks=60)
    public static void nonPlayerBulletsHonorMobGriefing(GameTestHelper h){
        arena(h);var at=new BlockPos(3,3,8);h.setBlock(at,Blocks.GLASS);var level=h.getLevel();
        var owner=net.minecraft.world.entity.EntityType.PILLAGER.create(level);owner.setNoAi(true);owner.setPos(h.absoluteVec(new Vec3(3.5,2,3.5)));
        var rule=level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING);boolean before=rule.get();
        try{
            rule.set(false,level.getServer());var direction=Vec3.atCenterOf(h.absolutePos(at)).subtract(owner.getEyePosition()).normalize();FirearmsCombat.fire(owner,WeaponCatalog.RIFLE,direction,1);
            h.assertTrue(h.getBlockState(at).is(Blocks.GLASS),"disabled mobGriefing blocks NPC structural damage");
            rule.set(true,level.getServer());FirearmsCombat.fire(owner,WeaponCatalog.RIFLE,direction,1);
            h.assertTrue(h.getBlockState(at).isAir(),"enabled mobGriefing permits an actual NPC glass impact");h.succeed();
        }finally{rule.set(before,level.getServer());owner.discard();}
    }
    private static final class Protection {
        final UUID player;final BlockPos at;
        Protection(ServerPlayer player,BlockPos at){this.player=player.getUUID();this.at=at;}
        @net.minecraftforge.eventbus.api.SubscribeEvent public void block(net.minecraftforge.event.level.BlockEvent.BreakEvent event){
            if(event.getPlayer().getUUID().equals(player)&&event.getPos().equals(at))event.setCanceled(true);
        }
    }
    @GameTest(template="blackwatch_arena",batch="terrain4",timeoutTicks=60)
    public static void playerBreakCancellationPreventsStructuralDamage(GameTestHelper h){
        arena(h);var p=player(h);var at=new BlockPos(3,3,8);h.setBlock(at,Blocks.GLASS);var protection=new Protection(p,h.absolutePos(at));
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(protection);
        try{shot(h,p,WeaponCatalog.RIFLE,at);h.assertTrue(h.getBlockState(at).is(Blocks.GLASS),"real platform break cancellation protects cover");}
        finally{net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(protection);}
        shot(h,p,WeaponCatalog.RIFLE,at);h.assertTrue(h.getBlockState(at).isAir(),"the same real shot works after protection is removed");h.succeed();
    }
    @GameTest(template="blackwatch_arena",batch="terrain4",timeoutTicks=80)
    public static void anActualRocketImpactDamagesOnlyItsLocalArea(GameTestHelper h){
        arena(h);var p=player(h);var at=new BlockPos(3,3,8);h.setBlock(at,Blocks.OAK_PLANKS);h.setBlock(3,3,14,Blocks.OAK_PLANKS);h.setBlock(5,3,8,Blocks.BEDROCK);
        shot(h,p,WeaponCatalog.ROCKET,at);
        h.runAfterDelay(12,()->{h.assertTrue(h.getBlockState(at).isAir(),"real ordnance impact opens local wooden cover");h.assertTrue(h.getBlockState(new BlockPos(3,3,14)).is(Blocks.OAK_PLANKS),"the blast does not erase distant structures");h.assertTrue(h.getBlockState(new BlockPos(5,3,8)).is(Blocks.BEDROCK),"local protected foundation survives");h.succeed();});
    }
    /** QA-only listener adapter: helper visibility and listener signatures differ across loaders. */
    private static void cleanupOnFailure(GameTestHelper h,Runnable cleanup){
        try{
            var field=GameTestHelper.class.getDeclaredField("testInfo");field.setAccessible(true);
            var info=(GameTestInfo)field.get(h);
            var listener=(GameTestListener)java.lang.reflect.Proxy.newProxyInstance(GameTestListener.class.getClassLoader(),new Class<?>[]{GameTestListener.class},(proxy,method,args)->{
                return switch(method.getName()){
                    case "testFailed" -> {cleanup.run();yield null;}
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy==args[0];
                    case "toString" -> "terrain4-failure-cleanup";
                    default -> null;
                };
            });
            info.addListener(listener);
        }catch(ReflectiveOperationException failure){throw new IllegalStateException("Cannot install QA failure cleanup before chunk tickets",failure);}
    }
    @GameTest(template="blackwatch_arena",batch="terrain4",timeoutTicks=400)
    public static void aShooterChangingDimensionCannotBlastTheOtherWorld(GameTestHelper h){
        arena(h);var source=h.getLevel();var other=source.getServer().getLevel(com.codex.blacklight.city.CityTravel.CITY);
        var nativeSpot=h.absolutePos(new BlockPos(8,5,10));var at=new BlockPos(nativeSpot.getX(),180,nativeSpot.getZ());
        h.assertTrue(other!=null&&source.getWorldBorder().isWithinBounds(at)&&other.getWorldBorder().isWithinBounds(at)
                &&!source.isOutsideBuildHeight(at)&&!other.isOutsideBuildHeight(at),"both actual worlds must permit the impact coordinates");
        var undo=new java.util.ArrayList<Runnable>();var cleaned=new java.util.concurrent.atomic.AtomicBoolean();
        Runnable cleanup=()->{if(cleaned.compareAndSet(false,true))for(int i=undo.size()-1;i>=0;i--)undo.get(i).run();};
        cleanupOnFailure(h,cleanup);
        try{
            for(int x=(at.getX()>>4)-1;x<=(at.getX()>>4)+1;x++)for(int z=(at.getZ()>>4)-1;z<=(at.getZ()>>4)+1;z++){
                var chunk=new net.minecraft.world.level.ChunkPos(x,z);
                boolean wasSource=source.getForcedChunks().contains(chunk.toLong()),wasOther=other.getForcedChunks().contains(chunk.toLong());
                undo.add(()->{source.setChunkForced(chunk.x,chunk.z,wasSource);other.setChunkForced(chunk.x,chunk.z,wasOther);});
                source.setChunkForced(x,z,true);other.setChunkForced(x,z,true);
            }
            source.getChunkAt(at);other.getChunkAt(at);var oldSource=source.getBlockState(at);var oldOther=other.getBlockState(at);
            undo.add(()->{source.setBlock(at,oldSource,3);other.setBlock(at,oldOther,3);});
            var restored=new java.util.HashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
            undo.add(()->restored.forEach((position,state)->source.setBlock(position,state,3)));
            for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-7;z<0;z++){
                var path=at.offset(x,y,z);restored.put(path,source.getBlockState(path));source.setBlock(path,Blocks.AIR.defaultBlockState(),3);
            }
            source.setBlock(at,Blocks.OAK_PLANKS.defaultBlockState(),3);other.setBlock(at,Blocks.OAK_PLANKS.defaultBlockState(),3);
            h.assertTrue(other.hasChunkAt(at)&&other.getBlockState(at).is(Blocks.OAK_PLANKS),"destination is actually loaded and accepts the target block");
            var p=player(h);p.setPos(Vec3.atCenterOf(at).add(0,-1.5,-6));undo.add(()->p.setServerLevel(source));
            var rocket=new OrdnanceProjectile(source,p,WeaponDefinition.Kind.ROCKET);undo.add(rocket::discard);
            var start=BlockPos.containing(p.getEyePosition());
            h.startSequence().thenWaitUntil(()->h.assertTrue(source.isPositionEntityTicking(start)&&source.isPositionEntityTicking(at),
                    "source projectile chunks must tick: start="+source.isPositionEntityTicking(start)+", hit="+source.isPositionEntityTicking(at)+", destinationLoaded="+other.hasChunkAt(at)))
                .thenExecute(()->{
                    var direction=Vec3.atCenterOf(at).subtract(p.getEyePosition()).normalize();
                    rocket.setPos(p.getEyePosition().add(0,-.18,0));rocket.shoot(direction.x,direction.y,direction.z,2.2F,0);
                    h.assertTrue(source.addFreshEntity(rocket),"real source-world rocket is registered");
                    // Actual changeDimension reuses this player; setServerLevel reproduces its cached-owner boundary.
                    p.setServerLevel(other);
                    h.assertTrue(rocket.getOwner()==p&&p.isAlive()&&!p.isRemoved()&&p.level()==other&&rocket.level()==source,"a live owner changes world while its real projectile remains in the source world");
                }).thenIdle(12).thenExecute(()->{
                    try{
                        h.assertTrue(rocket.isRemoved()&&rocket.tickCount>0&&rocket.tickCount<100,"the actual moving rocket must hit before its lifetime expiry");
                        h.assertTrue(other.getBlockState(at).is(Blocks.OAK_PLANKS),"an old-world projectile must not damage the shooters new dimension at matching coordinates");
                        h.assertTrue(source.getBlockState(at).is(Blocks.OAK_PLANKS),"a projectile with no live same-dimension owner cannot damage old-world structures");
                    }finally{cleanup.run();}
                }).thenIdle(5).thenSucceed();
        }catch(Throwable failure){cleanup.run();throw failure;}
    }
    @GameTest(template="blackwatch_arena",batch="terrain4",timeoutTicks=60)
    public static void detonationBudgetAndInfectionRemainBounded(GameTestHelper h){
        arena(h);var p=player(h);int before=0;
        for(int x=6;x<=10;x++)for(int y=2;y<=6;y++)for(int z=6;z<=10;z++){h.setBlock(x,y,z,Blocks.WHITE_WOOL);before++;}
        var center=h.absoluteVec(new Vec3(8.5,4.5,8.5));int changed=FirearmBlockDamage.blast(h.getLevel(),p,WeaponCatalog.ROCKET,center),remaining=0;
        for(int x=6;x<=10;x++)for(int y=2;y<=6;y++)for(int z=6;z<=10;z++)if(h.getBlockState(new BlockPos(x,y,z)).is(Blocks.WHITE_WOOL))remaining++;
        h.assertTrue(changed>0&&before-remaining>0&&before-remaining<=FirearmsWorldConfig.BLAST_BLOCK_LIMIT.get(),"one detonation obeys its actual structural work limit");
        var at=new BlockPos(3,3,8);h.setBlock(at,Blocks.GLASS);
        h.assertTrue(FirearmBlockDamage.blast(h.getLevel(),p,WeaponCatalog.INFECTION,Vec3.atCenterOf(h.absolutePos(at)))==0&&h.getBlockState(at).is(Blocks.GLASS),"infection payload carries no structural blast");
        h.assertTrue(FirearmBlockDamage.blast(h.getLevel(),null,WeaponCatalog.ROCKET,center)==0,"ownerless ordnance cannot mutate terrain");h.succeed();
    }
}
