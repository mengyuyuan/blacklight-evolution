package com.codex.blacklight.gametest;

import com.codex.blacklight.city.*;
import com.codex.blacklight.combat.BlockThrowSystem;
import com.codex.blacklight.entity.ThrownBlock;
import com.codex.blacklight.power.PowerForm;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Real command registration, item use and entity-publication callbacks. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class AuditBoundaryGameTest {
    @GameTest(template="wall_test",timeoutTicks=30,batch="audit_city_commands")
    public static void unprivilegedPlayersCannotParseDebugCityTravel(GameTestHelper h){
        var p=LockedGrabGameTest.player(h,PowerForm.MUSCLE_MASS);
        var dispatcher=h.getLevel().getServer().getCommands().getDispatcher();
        for(var command:new String[]{"blacklightcity travel","blacklightcity shenzhen","blacklightcity futian","blacklightcity losangeles","blacklightcity losangeles lax"}){
            var unprivileged=dispatcher.parse(command,p.createCommandSourceStack().withPermission(0));
            h.assertTrue(unprivileged.getReader().canRead()||!unprivileged.getExceptions().isEmpty(),"survival cannot bypass tissue feeding and hydra maturation: "+command);
            var operator=dispatcher.parse(command,p.createCommandSourceStack().withPermission(2));
            h.assertTrue(!operator.getReader().canRead()&&operator.getExceptions().isEmpty(),"operator debugging remains available: "+command);
        }
        h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=30,batch="audit_city_legacy_item")
    public static void legacyBeaconCannotSkipTheSurvivalHydraRoute(GameTestHelper h){
        var p=LockedGrabGameTest.player(h,PowerForm.MUSCLE_MASS);
        h.assertTrue(!p.hasPermissions(2),"fixture is an ordinary non-operator");
        var item=CityBlocks.TRANSIT.get();p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(item));
        var before=p.level();var at=p.position();
        var result=item.use(p.level(),p,InteractionHand.MAIN_HAND);
        h.assertTrue(result.getResult()==InteractionResult.FAIL&&p.level()==before&&p.position().equals(at),"a legacy registry item cannot initiate survival teleportation");
        h.assertTrue(h.getLevel().getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("blacklight","city_transit")).isEmpty(),"the retired survival bypass has no bundled recipe");
        h.succeed();
    }
    private static void pickup(GameTestHelper h,boolean interrupt){
        var p=LockedGrabGameTest.player(h,PowerForm.MUSCLE_MASS);
        var first=p.blockPosition().below().south();var second=first.east();
        var firstState=h.getLevel().getBlockState(first);var secondState=h.getLevel().getBlockState(second);
        var attempted=new AtomicBoolean();var nested=new AtomicBoolean();
        Consumer<EntityJoinLevelEvent> callback=e->{
            if(e.getLevel()!=p.level()||!(e.getEntity() instanceof ThrownBlock block)||block.getOwner()!=p||!attempted.compareAndSet(false,true))return;
            if(interrupt)p.getData(com.codex.blacklight.registry.ModAttachments.PLAYER_STATE).activate(PowerForm.NONE);
            else nested.set(BlockThrowSystem.request(p,1,0,second));
        };
        NeoForge.EVENT_BUS.addListener(callback);
        try{
            boolean accepted=BlockThrowSystem.request(p,1,0,first);
            h.assertTrue(attempted.get(),"the real projectile join callback was reached");
            var objects=h.getLevel().getEntitiesOfClass(ThrownBlock.class,p.getBoundingBox().inflate(5),b->b.getOwner()==p);
            if(interrupt)h.assertTrue(!accepted&&objects.isEmpty()&&h.getLevel().getBlockState(first).equals(firstState),"a mode change during publication leaves the original terrain intact and no orphan cargo");
            else h.assertTrue(accepted&&!nested.get()&&objects.size()==1&&h.getLevel().getBlockState(first).isAir()&&h.getLevel().getBlockState(second).equals(secondState),"nested publication cannot create two held loads or consume a second source block");
        }finally{
            NeoForge.EVENT_BUS.unregister(callback);
            for(var entity:h.getLevel().getEntitiesOfClass(ThrownBlock.class,p.getBoundingBox().inflate(8),b->b.getOwner()==p))entity.discard();
            BlockThrowSystem.clear(p);h.getLevel().setBlock(first,firstState,3);h.getLevel().setBlock(second,secondState,3);
        }
        h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=30,batch="audit_pickup_reentry")
    public static void entityPublicationCannotReenterTerrainExtraction(GameTestHelper h){pickup(h,false);}
    @GameTest(template="wall_test",timeoutTicks=30,batch="audit_pickup_interrupt")
    public static void entityPublicationCannotExtractAfterPowerWasRemoved(GameTestHelper h){pickup(h,true);}
    @SuppressWarnings("unchecked")
    @GameTest(template="wall_test",timeoutTicks=30,batch="audit_city_replaced_player")
    public static void replacingAPlayerReleasesPendingCityArrivalAndItsTicket(GameTestHelper h)throws Exception{
        var original=LockedGrabGameTest.player(h,PowerForm.MUSCLE_MASS);
        var replacement=new net.neoforged.neoforge.common.util.FakePlayer(h.getLevel(),original.getGameProfile());
        // Stage an asynchronous queue entry without relying on whether some other
        // city fixture has already loaded the public debug destination.
        var type=Class.forName("com.codex.blacklight.city.CityTravel$PendingTravel");
        var constructor=type.getDeclaredConstructor(net.minecraft.server.level.ServerPlayer.class,net.minecraft.server.level.ServerLevel.class,net.minecraft.core.BlockPos.class,float.class,float.class,boolean.class,String.class);
        constructor.setAccessible(true);var pending=constructor.newInstance(original,h.getLevel(),original.blockPosition(),0F,0F,false,"city.blacklight.welcome");
        var field=CityTravel.class.getDeclaredField("PENDING");field.setAccessible(true);
        var queue=(java.util.Map<net.minecraft.server.level.ServerPlayer,Object>)field.get(null);
        var ticketField=type.getDeclaredField("ticket");ticketField.setAccessible(true);var ticket=(HydraDestination)ticketField.get(pending);
        ticket.prepare(h.getLevel(),original.blockPosition());queue.put(original,pending);
        try{
            NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.PlayerEvent.Clone(replacement,original,true));
            h.assertTrue(!queue.containsKey(original)&&ticket.diagnostics().equals("released"),"actual player replacement releases the old strong player reference, travel slot and arrival ticket");
        }finally{
            CityTravel.logout(new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(original));ticket.release();replacement.discard();
        }
        h.succeed();
    }
}
